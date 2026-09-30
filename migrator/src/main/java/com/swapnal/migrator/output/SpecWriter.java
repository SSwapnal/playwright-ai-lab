package com.swapnal.migrator.output;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes generated files into the Playwright project. Only {@code tests/*.spec.ts} and {@code pages/*.ts}
 * are accepted, so a model response cannot overwrite configuration or escape the project directory.
 */
public class SpecWriter {

    private static final Pattern ALLOWED = Pattern.compile("(tests/[\\w.-]+\\.spec\\.ts|pages/[\\w.-]+\\.ts)");

    private final Path projectDir;

    public SpecWriter(Path projectDir) {
        this.projectDir = projectDir;
    }

    /** @return paths written, relative to the project root */
    public List<String> write(List<GeneratedFile> files) {
        List<String> written = new ArrayList<>();
        for (GeneratedFile file : files) {
            if (!isAllowed(file.path())) {
                throw new IllegalArgumentException("Refusing to write outside tests/ or pages/: " + file.path());
            }
            Path target = projectDir.resolve(file.path()).normalize();
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + target, e);
            }
            written.add(file.path());
        }
        return written;
    }

    public static boolean isAllowed(String relativePath) {
        return ALLOWED.matcher(relativePath).matches() && !relativePath.contains("..");
    }
}
