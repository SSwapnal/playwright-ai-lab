package com.swapnal.migrator.runner;

import com.swapnal.migrator.TestFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightJsonParserTest {

    private final PlaywrightJsonParser parser = new PlaywrightJsonParser();

    @Test
    void parsesRealReportWithOneFailure() throws IOException {
        String json = Files.readString(TestFixtures.resource("playwright/login-one-failure.json"));

        RunResult result = parser.parse(json);

        assertEquals(2, result.total());
        assertEquals(1, result.passed());
        assertFalse(result.success());
        assertEquals(1, result.failures().size());
        TestFailure failure = result.failures().get(0);
        assertEquals("LoginTest > valid login shows secure area", failure.title());
        assertTrue(failure.message().contains("toHaveText"));
        assertTrue(failure.message().contains("You logged into a secure area!"));
        assertFalse(failure.message().contains("\u001B["), "ANSI codes are stripped");
    }

    @Test
    void fileLevelErrorsBecomeFailures() throws IOException {
        String json = Files.readString(TestFixtures.resource("playwright/load-error.json"));

        RunResult result = parser.parse(json);

        assertEquals(0, result.total());
        assertFalse(result.success());
        assertTrue(result.failures().get(0).message().startsWith("SyntaxError"));
    }

    @Test
    void allPassing() {
        String json = """
                {"suites":[{"title":"a.spec.ts","specs":[],"suites":[{"title":"A","specs":[
                  {"title":"one","tests":[{"status":"expected","results":[{"status":"passed","errors":[]}]}]},
                  {"title":"two","tests":[{"status":"expected","results":[
                     {"status":"failed","errors":[{"message":"flaky"}]},{"status":"passed","errors":[]}]}]}
                ]}]}],"errors":[]}
                """;

        RunResult result = parser.parse(json);

        assertEquals(2, result.total());
        assertTrue(result.success(), "the last retry decides the outcome");
    }

    @Test
    void nonJsonOutputIsAFailure() {
        RunResult result = parser.parse("npx: command not found");

        assertFalse(result.success());
        assertEquals("Playwright produced no JSON report", result.failures().get(0).title());
    }
}
