package com.swapnal.migrator.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parses the output of Playwright's {@code --reporter=json}. */
public class PlaywrightJsonParser {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*m");

    private final ObjectMapper mapper = new ObjectMapper();

    public RunResult parse(String json) {
        int start = json.indexOf('{');
        if (start < 0) {
            return RunResult.error("Playwright produced no JSON report", json.strip());
        }
        JsonNode root;
        try {
            root = mapper.readTree(json.substring(start));
        } catch (IOException e) {
            return RunResult.error("Unparseable Playwright JSON report", e.getMessage());
        }

        List<TestFailure> failures = new ArrayList<>();
        int[] counts = new int[2]; // total, passed
        for (JsonNode suite : root.path("suites")) {
            walkSuite(suite, new ArrayList<>(), counts, failures);
        }
        // File-level problems (syntax errors, bad imports) appear here and no tests run.
        for (JsonNode error : root.path("errors")) {
            failures.add(new TestFailure("Error loading spec", clean(error.path("message").asText())));
        }
        return new RunResult(counts[0], counts[1], failures);
    }

    private void walkSuite(JsonNode suite, List<String> path, int[] counts, List<TestFailure> failures) {
        List<String> titles = new ArrayList<>(path);
        String title = suite.path("title").asText();
        // The top-level suite is the file name; skip it in test titles.
        if (!title.isEmpty() && !title.endsWith(".ts")) {
            titles.add(title);
        }
        for (JsonNode spec : suite.path("specs")) {
            for (JsonNode test : spec.path("tests")) {
                if ("skipped".equals(test.path("status").asText())) {
                    continue;
                }
                counts[0]++;
                JsonNode results = test.path("results");
                JsonNode last = results.isEmpty() ? null : results.get(results.size() - 1);
                String status = last == null ? "unknown" : last.path("status").asText();
                if ("passed".equals(status)) {
                    counts[1]++;
                } else {
                    List<String> fullTitle = new ArrayList<>(titles);
                    fullTitle.add(spec.path("title").asText());
                    failures.add(new TestFailure(String.join(" > ", fullTitle),
                            "[" + status + "] " + errorMessages(last)));
                }
            }
        }
        for (JsonNode child : suite.path("suites")) {
            walkSuite(child, titles, counts, failures);
        }
    }

    private static String errorMessages(JsonNode result) {
        if (result == null) {
            return "no result recorded";
        }
        List<String> messages = new ArrayList<>();
        for (JsonNode error : result.path("errors")) {
            messages.add(clean(error.path("message").asText()));
        }
        if (messages.isEmpty() && result.has("error")) {
            messages.add(clean(result.path("error").path("message").asText()));
        }
        return String.join("\n\n", messages);
    }

    static String clean(String text) {
        return ANSI.matcher(text).replaceAll("").strip();
    }
}
