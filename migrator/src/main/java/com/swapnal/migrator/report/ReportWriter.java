package com.swapnal.migrator.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.swapnal.migrator.analysis.PatternFinding;
import com.swapnal.migrator.core.AttemptRecord;
import com.swapnal.migrator.core.MigrationResult;
import com.swapnal.migrator.core.MigrationStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes {@code migration-report.md} and {@code migration-report.json}. */
public class ReportWriter {

    public static final String MARKDOWN_FILE = "migration-report.md";
    public static final String JSON_FILE = "migration-report.json";

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(MigrationReport report, Path directory) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(MARKDOWN_FILE), toMarkdown(report));
            Files.writeString(directory.resolve(JSON_FILE), toJson(report));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write report to " + directory, e);
        }
    }

    public String toJson(MigrationReport report) {
        try {
            return mapper.writeValueAsString(report) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String toMarkdown(MigrationReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Selenium to Playwright migration report\n\n");
        md.append("- Generated: ").append(report.generatedAt()).append('\n');
        md.append("- LLM: ").append(report.llm()).append('\n');
        md.append("- Max attempts per test class: ").append(report.maxAttempts()).append("\n\n");

        MigrationReport.Summary s = report.summary();
        md.append("## Summary\n\n");
        md.append(s.passed()).append(" of ").append(s.testClasses()).append(" test classes passing")
                .append(" (").append(s.passedFirstAttempt()).append(" on the first attempt, ")
                .append(s.passedAfterFix()).append(" after self-correction)");
        if (s.failed() > 0) {
            md.append(", ").append(s.failed()).append(" still failing");
        }
        if (s.notRun() > 0) {
            md.append(", ").append(s.notRun()).append(" converted but not run");
        }
        if (s.errors() > 0) {
            md.append(", ").append(s.errors()).append(" with errors");
        }
        md.append(".\n\n");

        md.append("| Test class | Spec | Status | Attempts | Tests passed |\n");
        md.append("|---|---|---|---|---|\n");
        for (MigrationResult r : report.results()) {
            md.append("| ").append(r.testClass())
                    .append(" | `").append(r.specFile()).append('`')
                    .append(" | ").append(r.status())
                    .append(" | ").append(r.attemptCount())
                    .append(" | ").append(testsPassed(r))
                    .append(" |\n");
        }
        md.append('\n');

        for (MigrationResult r : report.results()) {
            appendDetail(md, r);
        }
        return md.toString();
    }

    private static void appendDetail(StringBuilder md, MigrationResult r) {
        md.append("## ").append(r.testClass()).append("\n\n");
        md.append("- Spec: `").append(r.specFile()).append("`\n");
        if (!r.pageFiles().isEmpty()) {
            md.append("- Page objects: ");
            md.append(String.join(", ", r.pageFiles().stream().map(p -> "`" + p + "`").toList()));
            md.append('\n');
        }
        md.append("- Status: ").append(r.status());
        if (r.status() == MigrationStatus.PASSED) {
            md.append(r.attemptCount() == 1 ? " on the first attempt" : " after " + r.attemptCount() + " attempts");
        }
        md.append("\n\n");

        md.append("### Attempts\n\n");
        md.append("| # | Result | Tests passed | Failures |\n|---|---|---|---|\n");
        for (AttemptRecord a : r.attempts()) {
            md.append("| ").append(a.number())
                    .append(" | ").append(r.status() == MigrationStatus.CONVERTED_NOT_RUN ? "not run"
                            : a.passed() ? "pass" : "fail")
                    .append(" | ").append(a.testsPassed()).append('/').append(a.testsRun())
                    .append(" | ").append(a.failures().isEmpty() ? "-" : cell(String.join("<br>", a.failures())))
                    .append(" |\n");
        }
        md.append('\n');

        List<PatternFinding> patterns = r.seleniumPatterns();
        if (!patterns.isEmpty()) {
            md.append("### Selenium patterns\n\n");
            md.append("| Pattern in Java source | Playwright replacement | Result |\n|---|---|---|\n");
            for (PatternFinding p : patterns) {
                md.append("| ").append(p.pattern())
                        .append(" | ").append(cell(p.replacement()))
                        .append(" | ").append(p.removed() ? "removed" : "still present: `" + cell(p.leftover()) + "`")
                        .append(" |\n");
            }
            md.append('\n');
        }

        if (!r.notes().isEmpty()) {
            md.append("### Notes\n\n");
            for (String note : r.notes()) {
                md.append("- ").append(note).append('\n');
            }
            md.append('\n');
        }
    }

    private static String testsPassed(MigrationResult r) {
        if (r.attempts().isEmpty() || r.status() == MigrationStatus.CONVERTED_NOT_RUN) {
            return "-";
        }
        AttemptRecord last = r.attempts().get(r.attempts().size() - 1);
        return last.testsPassed() + "/" + last.testsRun();
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
