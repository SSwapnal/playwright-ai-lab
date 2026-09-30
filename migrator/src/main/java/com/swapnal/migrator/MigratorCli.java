package com.swapnal.migrator;

import com.swapnal.migrator.core.MigrationEngine;
import com.swapnal.migrator.core.MigrationResult;
import com.swapnal.migrator.core.MigrationStatus;
import com.swapnal.migrator.llm.LlmClient;
import com.swapnal.migrator.llm.LlmClientFactory;
import com.swapnal.migrator.output.SpecWriter;
import com.swapnal.migrator.report.MigrationReport;
import com.swapnal.migrator.report.ReportWriter;
import com.swapnal.migrator.runner.PlaywrightRunner;
import com.swapnal.migrator.source.SourceCollector;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "migrator", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Converts Selenium/JUnit 5 tests to Playwright TypeScript with an LLM, runs them, "
                + "and feeds failures back to the model until they pass.",
        footer = {"",
                "Environment:",
                "  LLM_PROVIDER        anthropic | mock",
                "                      (default: anthropic if a key is set, else mock)",
                "  LLM_MODEL           model id (default: " + LlmClientFactory.DEFAULT_MODEL + ")",
                "  ANTHROPIC_API_KEY   API key for the anthropic provider"})
public class MigratorCli implements Callable<Integer> {

    @Option(names = "--source", description = "Root of the Selenium test sources (default: ${DEFAULT-VALUE})")
    Path source = Path.of("../legacy-selenium-suite/src/test/java");

    @Option(names = "--output", description = "Playwright project directory (default: ${DEFAULT-VALUE})")
    Path output = Path.of("../playwright-output");

    @Option(names = "--report-dir", description = "Where to write the migration report (default: ${DEFAULT-VALUE})")
    Path reportDir = Path.of("target/migration-report");

    @Option(names = {"-t", "--test"}, description = "Test class to migrate (repeatable). Default: all classes containing @Test")
    List<String> tests;

    @Option(names = "--max-attempts", description = "Generate/run attempts per test class (default: ${DEFAULT-VALUE})")
    int maxAttempts = 3;

    @Option(names = "--no-run", description = "Only convert; do not run Playwright or attempt fixes")
    boolean noRun;

    @Option(names = "--run-timeout", description = "Seconds allowed per Playwright run (default: ${DEFAULT-VALUE})")
    long runTimeoutSeconds = 180;

    public static void main(String[] args) {
        System.exit(new CommandLine(new MigratorCli()).execute(args));
    }

    @Override
    public Integer call() {
        if (!Files.isDirectory(source)) {
            System.err.println("Source directory not found: " + source.toAbsolutePath().normalize());
            return 2;
        }
        if (!Files.isRegularFile(output.resolve("playwright.config.ts"))) {
            System.err.println("Not a Playwright project (no playwright.config.ts): " + output.toAbsolutePath().normalize());
            return 2;
        }

        LlmClient llm;
        try {
            llm = LlmClientFactory.fromEnvironment(System.getenv());
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        SourceCollector collector = new SourceCollector(source);
        List<String> classes = tests == null || tests.isEmpty() ? collector.discoverTestClasses() : tests;
        System.out.println("LLM: " + llm.describe());
        System.out.println("Migrating " + classes.size() + " test class(es): " + String.join(", ", classes));

        PlaywrightRunner runner = noRun ? null : new PlaywrightRunner(output, Duration.ofSeconds(runTimeoutSeconds));
        MigrationEngine engine = new MigrationEngine(collector, llm, new SpecWriter(output), runner,
                maxAttempts, System.out::println).withTranscripts(reportDir.resolve("conversations"));
        List<MigrationResult> results = engine.migrateAll(classes);

        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
        MigrationReport report = MigrationReport.of(timestamp, llm.describe(), maxAttempts, results);
        new ReportWriter().write(report, reportDir);

        MigrationReport.Summary s = report.summary();
        System.out.println();
        System.out.printf("Done: %d/%d passing (%d first attempt, %d after fixes), %d failed, %d not run, %d errors%n",
                s.passed(), s.testClasses(), s.passedFirstAttempt(), s.passedAfterFix(), s.failed(), s.notRun(), s.errors());
        System.out.println("Report: " + reportDir.resolve(ReportWriter.MARKDOWN_FILE).toAbsolutePath().normalize());

        boolean ok = results.stream().allMatch(r -> r.status() == MigrationStatus.PASSED
                || r.status() == MigrationStatus.CONVERTED_NOT_RUN);
        return ok ? 0 : 1;
    }
}
