package com.swapnal.migrator.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads a JUnit test class and the project classes it depends on.
 *
 * <p>This is deliberately regex-based rather than a full Java parser: legacy UI suites follow a small
 * number of conventions (explicit imports for page objects, a shared base class in the same package),
 * and a light scanner keeps the tool dependency-free. Same-package references are found by type name,
 * which covers {@code extends BaseTest}. Dependencies are followed transitively so that,
 * for example, a page object that returns another page object is collected too.
 */
public class SourceCollector {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE_REFERENCE = Pattern.compile("\\b([A-Z]\\w*)\\b");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@(Test|ParameterizedTest)\\b");

    private final Path sourceRoot;

    /** @param sourceRoot root of the test sources, e.g. {@code src/test/java} */
    public SourceCollector(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /** Finds concrete test classes (files containing {@code @Test}) under the source root, sorted by name. */
    public List<String> discoverTestClasses() {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> TEST_ANNOTATION.matcher(read(p)).find())
                    .map(SourceCollector::simpleName)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan " + sourceRoot, e);
        }
    }

    /** Collects a test class by simple name (e.g. {@code LoginTest}) together with its dependencies. */
    public TestSource collect(String testClassName) {
        Path testFile = findBySimpleName(testClassName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Test class " + testClassName + " not found under " + sourceRoot));
        JavaSourceFile test = load(testFile);

        Map<String, JavaSourceFile> dependencies = new LinkedHashMap<>();
        collectDependencies(test, dependencies);

        List<JavaSourceFile> pageObjects = new ArrayList<>();
        List<JavaSourceFile> support = new ArrayList<>();
        for (JavaSourceFile dep : dependencies.values()) {
            (isPageObject(dep) ? pageObjects : support).add(dep);
        }
        return new TestSource(test, pageObjects, support);
    }

    private void collectDependencies(JavaSourceFile file, Map<String, JavaSourceFile> seen) {
        for (Path dep : directDependencies(file)) {
            String name = simpleName(dep);
            if (!seen.containsKey(name) && !name.equals(file.className())) {
                JavaSourceFile loaded = load(dep);
                seen.put(name, loaded);
                collectDependencies(loaded, seen);
            }
        }
    }

    /** Project-local classes referenced by explicit imports or by type name within the same package. */
    List<Path> directDependencies(JavaSourceFile file) {
        String content = file.content();
        List<Path> result = new ArrayList<>();

        Matcher imports = IMPORT.matcher(content);
        while (imports.find()) {
            Path candidate = sourceRoot.resolve(imports.group(1).replace('.', '/') + ".java");
            if (Files.isRegularFile(candidate)) {
                result.add(candidate);
            }
        }

        Path packageDir = packageDirectory(content);
        Matcher types = TYPE_REFERENCE.matcher(stripComments(content));
        while (types.find()) {
            Path candidate = packageDir.resolve(types.group(1) + ".java");
            if (Files.isRegularFile(candidate) && !result.contains(candidate)
                    && !types.group(1).equals(file.className())) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** A page object is identified by convention: it lives in a {@code pages} package or its name ends with {@code Page}. */
    static boolean isPageObject(JavaSourceFile file) {
        Matcher pkg = PACKAGE.matcher(file.content());
        boolean inPagesPackage = pkg.find() && pkg.group(1).matches(".*\\.pages?(\\..*)?");
        return inPagesPackage || file.className().endsWith("Page");
    }

    private Path packageDirectory(String content) {
        Matcher m = PACKAGE.matcher(content);
        return m.find() ? sourceRoot.resolve(m.group(1).replace('.', '/')) : sourceRoot;
    }

    private Optional<Path> findBySimpleName(String className) {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(p -> p.getFileName().toString().equals(className + ".java")).findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan " + sourceRoot, e);
        }
    }

    private static JavaSourceFile load(Path path) {
        return new JavaSourceFile(simpleName(path), path, read(path));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private static String simpleName(Path path) {
        String file = path.getFileName().toString();
        return file.substring(0, file.length() - ".java".length());
    }

    private static String stripComments(String content) {
        return content.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\\n]*", "")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
