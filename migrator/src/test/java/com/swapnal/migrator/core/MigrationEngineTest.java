package com.swapnal.migrator.core;

import com.swapnal.migrator.TestFixtures;
import com.swapnal.migrator.analysis.PatternFinding;
import com.swapnal.migrator.llm.LlmClient;
import com.swapnal.migrator.llm.LlmException;
import com.swapnal.migrator.llm.LlmRequest;
import com.swapnal.migrator.llm.MockLlmClient;
import com.swapnal.migrator.output.SpecWriter;
import com.swapnal.migrator.runner.RunResult;
import com.swapnal.migrator.runner.TestFailure;
import com.swapnal.migrator.runner.TestRunner;
import com.swapnal.migrator.source.SourceCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationEngineTest {

    private static final RunResult WRONG_TOTAL = new RunResult(1, 0, List.of(new TestFailure(
            "checkout shows total", "[failed] Error: expect(locator).toHaveText(expected) failed\nExpected: \"Total: 3\"\nReceived: \"Total: 2\"")));
    private static final RunResult PASS = new RunResult(1, 1, List.of());

    @TempDir
    Path project;

    private final SourceCollector collector = new SourceCollector(TestFixtures.fixtureSuite());
    private final List<LlmRequest> requests = new ArrayList<>();
    private final MockLlmClient mock = new MockLlmClient("test-mock-responses");

    /** Records every request, then delegates to the canned responses. */
    private final LlmClient recordingLlm = new LlmClient() {
        @Override
        public String complete(LlmRequest request) throws LlmException {
            requests.add(request);
            return mock.complete(request);
        }

        @Override
        public String describe() {
            return "recording mock";
        }
    };

    /** Returns queued results in order, repeating the last one. */
    private static TestRunner scripted(RunResult... results) {
        Deque<RunResult> queue = new ArrayDeque<>(List.of(results));
        return spec -> queue.size() > 1 ? queue.poll() : queue.peek();
    }

    private MigrationEngine engine(TestRunner runner, int maxAttempts) {
        return new MigrationEngine(collector, recordingLlm, new SpecWriter(project), runner, maxAttempts, line -> { });
    }

    @Test
    void failingSpecIsSentBackAndFixed() throws IOException {
        MigrationResult result = engine(scripted(WRONG_TOTAL, PASS), 3).migrate("CheckoutTest");

        assertEquals(MigrationStatus.PASSED, result.status());
        assertEquals(2, result.attemptCount());
        assertFalse(result.attempts().get(0).passed());
        assertEquals(List.of("checkout shows total: Error: expect(locator).toHaveText(expected) failed"),
                result.attempts().get(0).failures());
        assertTrue(result.attempts().get(1).passed());

        // Second request continues the same conversation: prompt, first answer, error feedback.
        LlmRequest fixRequest = requests.get(1);
        assertEquals(3, fixRequest.messages().size());
        assertEquals("assistant", fixRequest.messages().get(1).role());
        assertTrue(fixRequest.messages().get(2).content().contains("Received: \"Total: 2\""));

        assertTrue(Files.readString(project.resolve("tests/checkout.spec.ts")).contains("Total: 2"));
        assertTrue(Files.exists(project.resolve("pages/CartPage.ts")));
    }

    @Test
    void filesOutsideTheTargetsAreIgnored() {
        MigrationResult result = engine(scripted(PASS), 3).migrate("CheckoutTest");

        assertFalse(Files.exists(project.resolve("playwright.config.ts")));
        assertTrue(result.notes().contains("Ignored unexpected file in response: playwright.config.ts"));
        assertTrue(result.notes().contains("Attempt 1: First draft."));
    }

    @Test
    void leftoverHardWaitIsReportedEvenWhenTestsPass() {
        MigrationResult result = engine(scripted(PASS), 3).migrate("CheckoutTest");

        PatternFinding implicitWait = result.seleniumPatterns().stream()
                .filter(p -> p.pattern().equals("Implicit wait")).findFirst().orElseThrow();
        assertFalse(implicitWait.removed());
        assertTrue(implicitWait.leftover().contains("waitForTimeout"));
    }

    @Test
    void patternsAreCleanAfterFix() {
        MigrationResult result = engine(scripted(WRONG_TOTAL, PASS), 3).migrate("CheckoutTest");

        assertFalse(result.seleniumPatterns().isEmpty());
        assertTrue(result.seleniumPatterns().stream().allMatch(PatternFinding::removed));
    }

    @Test
    void stopsAfterMaxAttempts(@TempDir Path transcripts) throws IOException {
        MigrationResult result = engine(scripted(WRONG_TOTAL), 3).withTranscripts(transcripts).migrate("CheckoutTest");

        assertEquals(MigrationStatus.FAILED, result.status());
        assertEquals(3, result.attemptCount());
        assertEquals(3, requests.size());

        String transcript = Files.readString(transcripts.resolve("CheckoutTest.md"));
        assertTrue(transcript.strip().endsWith("- Fixed the expected total."),
                "transcript ends with the last model answer, not an unsent fix request");
    }

    @Test
    void responseWithoutFilesIsRetriedWithFormatReminder() {
        List<LlmRequest> seen = new ArrayList<>();
        LlmClient chatty = new LlmClient() {
            @Override
            public String complete(LlmRequest request) {
                seen.add(request);
                return "I think the test looks fine.";
            }

            @Override
            public String describe() {
                return "chatty";
            }
        };
        MigrationEngine engine = new MigrationEngine(collector, chatty, new SpecWriter(project), scripted(PASS), 2, l -> { });

        MigrationResult result = engine.migrate("CheckoutTest");

        assertEquals(MigrationStatus.ERROR, result.status());
        assertEquals(2, seen.size());
        assertTrue(seen.get(1).messages().get(2).content().contains("// file: <path>"));
        assertTrue(result.seleniumPatterns().isEmpty(), "nothing was converted, so nothing to verify");
    }

    @Test
    void llmFailureIsRecordedAsError() {
        LlmClient failing = new LlmClient() {
            @Override
            public String complete(LlmRequest request) throws LlmException {
                throw new LlmException("HTTP 401");
            }

            @Override
            public String describe() {
                return "failing";
            }
        };
        MigrationEngine engine = new MigrationEngine(collector, failing, new SpecWriter(project), scripted(PASS), 3, l -> { });

        MigrationResult result = engine.migrate("CheckoutTest");

        assertEquals(MigrationStatus.ERROR, result.status());
        assertTrue(result.notes().get(0).contains("HTTP 401"));
    }

    @Test
    void noRunModeConvertsOnce() {
        MigrationResult result = engine(null, 3).migrate("CheckoutTest");

        assertEquals(MigrationStatus.CONVERTED_NOT_RUN, result.status());
        assertEquals(1, requests.size());
        assertTrue(Files.exists(project.resolve("tests/checkout.spec.ts")));
    }

    @Test
    void firstMeaningfulLineSkipsGenericTimeout() {
        assertEquals("Error: expect(locator).toContainText(expected) failed", MigrationEngine.firstMeaningfulLine(
                "[timedOut] Test timeout of 30000ms exceeded.\n\nError: expect(locator).toContainText(expected) failed"));
    }
}
