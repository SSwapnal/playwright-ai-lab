package com.swapnal.migrator.runner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs {@code npx playwright test <spec> --reporter=json} in the Playwright project and parses the result. */
public class PlaywrightRunner implements TestRunner {

    private final Path projectDir;
    private final Duration timeout;
    private final PlaywrightJsonParser parser = new PlaywrightJsonParser();

    public PlaywrightRunner(Path projectDir, Duration timeout) {
        this.projectDir = projectDir;
        this.timeout = timeout;
    }

    @Override
    public RunResult run(String specPath) {
        List<String> command = new ArrayList<>();
        command.add(isWindows() ? "npx.cmd" : "npx");
        // No retries: the fix loop should see the real first-run outcome, not a retried pass.
        command.addAll(List.of("playwright", "test", specPath, "--reporter=json", "--retries=0"));

        Path stdout = null;
        Path stderr = null;
        try {
            stdout = Files.createTempFile("playwright-", ".json");
            stderr = Files.createTempFile("playwright-", ".err");
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(projectDir.toFile())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());
            pb.environment().put("FORCE_COLOR", "0");
            Process process = pb.start();
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return RunResult.error("Playwright run timed out",
                        "No result after " + timeout.toSeconds() + "s for " + specPath);
            }
            String json = Files.readString(stdout, StandardCharsets.UTF_8);
            RunResult result = parser.parse(json);
            if (result.total() == 0 && result.failures().isEmpty()) {
                String err = Files.readString(stderr, StandardCharsets.UTF_8).strip();
                return RunResult.error("No tests found in " + specPath, err.isEmpty() ? json.strip() : err);
            }
            return relativizePaths(result);
        } catch (IOException e) {
            return RunResult.error("Could not start Playwright",
                    e.getMessage() + " (is Node installed and `npm install` done in " + projectDir + "?)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RunResult.error("Interrupted", "Playwright run was interrupted");
        } finally {
            deleteQuietly(stdout);
            deleteQuietly(stderr);
        }
    }

    /** Shortens absolute paths in error output to project-relative ones: less noise for the model. */
    private RunResult relativizePaths(RunResult result) {
        String prefix = projectDir.toAbsolutePath().normalize() + File.separator;
        List<TestFailure> failures = result.failures().stream()
                .map(f -> new TestFailure(f.title(), f.message().replace(prefix, "")))
                .toList();
        return new RunResult(result.total(), result.passed(), failures);
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // temp file, nothing useful to do
            }
        }
    }
}
