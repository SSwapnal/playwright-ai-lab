package com.swapnal.migrator.core;

import com.swapnal.migrator.analysis.PatternFinding;
import com.swapnal.migrator.analysis.PatternScanner;
import com.swapnal.migrator.analysis.SeleniumPattern;
import com.swapnal.migrator.llm.ChatMessage;
import com.swapnal.migrator.llm.LlmClient;
import com.swapnal.migrator.llm.LlmException;
import com.swapnal.migrator.llm.LlmRequest;
import com.swapnal.migrator.output.GeneratedFile;
import com.swapnal.migrator.output.ParsedResponse;
import com.swapnal.migrator.output.ResponseParser;
import com.swapnal.migrator.output.SpecWriter;
import com.swapnal.migrator.output.TargetPaths;
import com.swapnal.migrator.prompt.PromptBuilder;
import com.swapnal.migrator.runner.RunResult;
import com.swapnal.migrator.runner.TestFailure;
import com.swapnal.migrator.runner.TestRunner;
import com.swapnal.migrator.source.JavaSourceFile;
import com.swapnal.migrator.source.SourceCollector;
import com.swapnal.migrator.source.TestSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Converts test classes one at a time: prompt the model, write the files, run the spec, and on failure
 * send the errors back in the same conversation, up to {@code maxAttempts} times.
 */
public class MigrationEngine {

    private final SourceCollector collector;
    private final LlmClient llm;
    private final SpecWriter writer;
    private final TestRunner runner;
    private final int maxAttempts;
    private final Consumer<String> log;

    private final PromptBuilder prompts = new PromptBuilder();
    private final ResponseParser parser = new ResponseParser();
    private final PatternScanner scanner = new PatternScanner();

    /** If set, the full conversation for each test class is saved here for review. */
    private Path transcriptDir;

    /** Page classes generated earlier in this run, so later test classes reuse rather than redefine them. */
    private final Map<String, String> generatedPages = new LinkedHashMap<>();

    /**
     * @param runner may be {@code null} to convert without executing (no fix loop)
     */
    public MigrationEngine(SourceCollector collector, LlmClient llm, SpecWriter writer, TestRunner runner,
                           int maxAttempts, Consumer<String> log) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.collector = collector;
        this.llm = llm;
        this.writer = writer;
        this.runner = runner;
        this.maxAttempts = maxAttempts;
        this.log = log;
    }

    public MigrationEngine withTranscripts(Path directory) {
        this.transcriptDir = directory;
        return this;
    }

    public List<MigrationResult> migrateAll(List<String> testClasses) {
        List<MigrationResult> results = new ArrayList<>();
        for (String testClass : testClasses) {
            results.add(migrate(testClass));
        }
        return results;
    }

    public MigrationResult migrate(String testClass) {
        TestSource source = collector.collect(testClass);
        String specPath = TargetPaths.specFor(testClass);
        List<String> pagePaths = source.pageObjects().stream()
                .map(p -> TargetPaths.pageFor(p.className())).toList();
        Set<SeleniumPattern> detected = scanner.detect(source.allFiles().stream()
                .map(JavaSourceFile::content).collect(Collectors.joining("\n")));

        log.accept("== " + testClass + " -> " + specPath
                + (pagePaths.isEmpty() ? "" : " + " + String.join(", ", pagePaths)));

        Map<String, String> existingPages = new LinkedHashMap<>();
        pagePaths.stream().filter(generatedPages::containsKey)
                .forEach(p -> existingPages.put(p, generatedPages.get(p)));

        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.user(prompts.conversionPrompt(source, existingPages)));
        List<AttemptRecord> attempts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<String, String> latestFiles = new LinkedHashMap<>();
        MigrationStatus status = MigrationStatus.FAILED;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.currentTimeMillis();
            String response;
            try {
                response = llm.complete(new LlmRequest(prompts.systemPrompt(), conversation, testClass));
            } catch (LlmException e) {
                log.accept("   attempt " + attempt + ": LLM error - " + e.getMessage());
                notes.add("LLM error on attempt " + attempt + ": " + e.getMessage());
                status = MigrationStatus.ERROR;
                break;
            }

            conversation.add(ChatMessage.assistant(response));
            ParsedResponse parsed = parser.parse(response);
            List<GeneratedFile> accepted = new ArrayList<>();
            for (GeneratedFile file : parsed.files()) {
                if (file.path().equals(specPath) || pagePaths.contains(file.path())) {
                    accepted.add(file);
                } else {
                    notes.add("Ignored unexpected file in response: " + file.path());
                }
            }
            for (String note : parsed.notes()) {
                notes.add("Attempt " + attempt + ": " + note);
            }

            if (parsed.file(specPath).isEmpty()) {
                String problem = "Response did not contain " + specPath + " in the required format";
                log.accept("   attempt " + attempt + ": " + problem);
                attempts.add(new AttemptRecord(attempt, false, 0, 0, List.of(problem), elapsed(started)));
                status = MigrationStatus.ERROR;
                if (attempt < maxAttempts) {
                    conversation.add(ChatMessage.user(problem + ". Return every target file as a fenced code "
                            + "block whose first line is `// file: <path>`."));
                }
                continue;
            }

            writer.write(accepted);
            accepted.forEach(f -> latestFiles.put(f.path(), f.content()));

            if (runner == null) {
                attempts.add(new AttemptRecord(attempt, false, 0, 0, List.of(), elapsed(started)));
                status = MigrationStatus.CONVERTED_NOT_RUN;
                log.accept("   attempt " + attempt + ": converted (not run)");
                break;
            }

            RunResult result = runner.run(specPath);
            attempts.add(new AttemptRecord(attempt, result.success(), result.total(), result.passed(),
                    summarise(result.failures()), elapsed(started)));
            log.accept("   attempt " + attempt + ": " + result.passed() + "/" + result.total() + " passed"
                    + (result.success() ? "" : " - " + result.failures().size() + " failure(s)"));

            if (result.success()) {
                status = MigrationStatus.PASSED;
                break;
            }
            status = MigrationStatus.FAILED;
            if (attempt < maxAttempts) {
                conversation.add(ChatMessage.user(prompts.fixPrompt(specPath, result)));
            }
        }

        latestFiles.forEach((path, content) -> {
            if (path.startsWith("pages/")) {
                generatedPages.put(path, content);
            }
        });
        saveTranscript(testClass, conversation);
        List<PatternFinding> patterns = latestFiles.isEmpty()
                ? List.of()
                : scanner.verify(detected, String.join("\n", latestFiles.values()));
        log.accept("   result: " + status + " after " + attempts.size() + " attempt(s)");
        return new MigrationResult(testClass, specPath, pagePaths, status, attempts, patterns, notes);
    }

    private void saveTranscript(String testClass, List<ChatMessage> conversation) {
        if (transcriptDir == null) {
            return;
        }
        StringBuilder md = new StringBuilder("# Conversation: " + testClass + "\n\n");
        md.append("## system\n\n").append(prompts.systemPrompt()).append('\n');
        for (ChatMessage m : conversation) {
            md.append("## ").append(m.role()).append("\n\n").append(m.content().strip()).append("\n\n");
        }
        try {
            Files.createDirectories(transcriptDir);
            Files.writeString(transcriptDir.resolve(testClass + ".md"), md.toString());
        } catch (IOException e) {
            log.accept("   could not save transcript: " + e.getMessage());
        }
    }

    private static List<String> summarise(List<TestFailure> failures) {
        return failures.stream()
                .map(f -> f.title() + ": " + firstMeaningfulLine(f.message()))
                .toList();
    }

    /** Skips the generic "Test timeout" line when a more specific assertion error follows. */
    static String firstMeaningfulLine(String message) {
        String[] lines = message.split("\\R");
        for (String line : lines) {
            String trimmed = line.replaceFirst("^\\[\\w+]\\s*", "").strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("Test timeout of")) {
                return trimmed;
            }
        }
        return lines.length == 0 ? "" : lines[0].strip();
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
