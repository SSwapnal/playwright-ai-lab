package com.swapnal.migrator.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecWriterTest {

    @TempDir
    Path project;

    @Test
    void writesSpecsAndPages() throws IOException {
        List<String> written = new SpecWriter(project).write(List.of(
                new GeneratedFile("tests/login.spec.ts", "spec\n"),
                new GeneratedFile("pages/LoginPage.ts", "page\n")));

        assertEquals(List.of("tests/login.spec.ts", "pages/LoginPage.ts"), written);
        assertEquals("spec\n", Files.readString(project.resolve("tests/login.spec.ts")));
        assertEquals("page\n", Files.readString(project.resolve("pages/LoginPage.ts")));
    }

    @Test
    void refusesPathsOutsideTestsAndPages() {
        SpecWriter writer = new SpecWriter(project);

        assertThrows(IllegalArgumentException.class,
                () -> writer.write(List.of(new GeneratedFile("playwright.config.ts", "x"))));
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(List.of(new GeneratedFile("tests/../../evil.spec.ts", "x"))));
        assertFalse(Files.exists(project.resolve("playwright.config.ts")));
    }

    @Test
    void allowedPathRules() {
        assertTrue(SpecWriter.isAllowed("tests/dynamic-loading.spec.ts"));
        assertTrue(SpecWriter.isAllowed("pages/LoginPage.ts"));
        assertFalse(SpecWriter.isAllowed("tests/helper.ts"));
        assertFalse(SpecWriter.isAllowed("/etc/passwd"));
        assertFalse(SpecWriter.isAllowed("pages/sub/Page.ts"));
    }

    @Test
    void targetPathNaming() {
        assertEquals("tests/login.spec.ts", TargetPaths.specFor("LoginTest"));
        assertEquals("tests/dynamic-loading.spec.ts", TargetPaths.specFor("DynamicLoadingTest"));
        assertEquals("tests/checkout-flow.spec.ts", TargetPaths.specFor("CheckoutFlowIT"));
        assertEquals("pages/LoginPage.ts", TargetPaths.pageFor("LoginPage"));
    }
}
