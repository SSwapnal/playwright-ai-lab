package com.swapnal.migrator.output;

import java.util.Locale;

/** Naming conventions for generated files, relative to the Playwright project root. */
public final class TargetPaths {

    private TargetPaths() {
    }

    /** {@code DynamicLoadingTest} becomes {@code tests/dynamic-loading.spec.ts}. */
    public static String specFor(String testClassName) {
        String base = testClassName.replaceAll("(Test|Tests|IT)$", "");
        String kebab = base.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        return "tests/" + kebab + ".spec.ts";
    }

    /** Page classes keep their Java name: {@code LoginPage} becomes {@code pages/LoginPage.ts}. */
    public static String pageFor(String pageClassName) {
        return "pages/" + pageClassName + ".ts";
    }
}
