package com.swapnal.migrator.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swapnal.migrator.analysis.PatternFinding;
import com.swapnal.migrator.analysis.SeleniumPattern;
import com.swapnal.migrator.core.AttemptRecord;
import com.swapnal.migrator.core.MigrationResult;
import com.swapnal.migrator.core.MigrationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    private static final MigrationResult FIRST_TRY = new MigrationResult("DynamicLoadingTest",
            "tests/dynamic-loading.spec.ts", List.of(), MigrationStatus.PASSED,
            List.of(new AttemptRecord(1, true, 1, 1, List.of(), 1200)),
            List.of(PatternFinding.removed(SeleniumPattern.EXPLICIT_WAIT)),
            List.of("Attempt 1: WebDriverWait removed"));

    private static final MigrationResult AFTER_FIX = new MigrationResult("LoginTest",
            "tests/login.spec.ts", List.of("pages/LoginPage.ts"), MigrationStatus.PASSED,
            List.of(new AttemptRecord(1, false, 2, 0, List.of("valid login: toHaveText | failed"), 3000),
                    new AttemptRecord(2, true, 2, 2, List.of(), 2500)),
            List.of(PatternFinding.retained(SeleniumPattern.THREAD_SLEEP, "await page.waitForTimeout(500);")),
            List.of());

    private static final MigrationResult STILL_FAILING = new MigrationResult("FormControlsTest",
            "tests/form-controls.spec.ts", List.of(), MigrationStatus.FAILED,
            List.of(new AttemptRecord(1, false, 3, 2, List.of("dropdown: timeout"), 1000)),
            List.of(), List.of());

    private final MigrationReport report = MigrationReport.of("2026-01-01T00:00:00Z", "mock", 3,
            List.of(FIRST_TRY, AFTER_FIX, STILL_FAILING));

    @Test
    void summaryCountsOutcomes() {
        MigrationReport.Summary s = report.summary();

        assertEquals(3, s.testClasses());
        assertEquals(2, s.passed());
        assertEquals(1, s.passedFirstAttempt());
        assertEquals(1, s.passedAfterFix());
        assertEquals(1, s.failed());
    }

    @Test
    void markdownHasSummaryTableAndPerClassDetail() {
        String md = new ReportWriter().toMarkdown(report);

        assertTrue(md.contains("2 of 3 test classes passing (1 on the first attempt, 1 after self-correction), 1 still failing."));
        assertTrue(md.contains("| LoginTest | `tests/login.spec.ts` | PASSED | 2 | 2/2 |"));
        assertTrue(md.contains("| FormControlsTest | `tests/form-controls.spec.ts` | FAILED | 1 | 2/3 |"));
        assertTrue(md.contains("- Status: PASSED after 2 attempts"));
        assertTrue(md.contains("- Page objects: `pages/LoginPage.ts`"));
        assertTrue(md.contains("| 1 | fail | 0/2 | valid login: toHaveText \\| failed |"), "pipes are escaped");
        assertTrue(md.contains("| WebDriverWait / ExpectedConditions |"));
        assertTrue(md.contains("still present: `await page.waitForTimeout(500);`"));
        assertTrue(md.contains("- Attempt 1: WebDriverWait removed"));
    }

    @Test
    void writesMarkdownAndJson(@TempDir Path dir) throws IOException {
        new ReportWriter().write(report, dir);

        JsonNode json = new ObjectMapper().readTree(dir.resolve(ReportWriter.JSON_FILE).toFile());
        assertEquals(2, json.path("summary").path("passed").asInt());
        JsonNode login = json.path("results").get(1);
        assertEquals("LoginTest", login.path("testClass").asText());
        assertEquals(2, login.path("attempts").size());
        assertEquals(false, login.path("seleniumPatterns").get(0).path("removed").asBoolean());
        assertTrue(Files.readString(dir.resolve(ReportWriter.MARKDOWN_FILE)).startsWith("# Selenium to Playwright migration report"));
    }
}
