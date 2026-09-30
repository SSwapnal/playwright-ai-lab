package com.swapnal.migrator.source;

import java.nio.file.Path;

/**
 * A Java source file that is part of a migration unit.
 *
 * @param className simple class name, e.g. {@code LoginPage}
 * @param path      location on disk
 * @param content   full source text
 */
public record JavaSourceFile(String className, Path path, String content) {
}
