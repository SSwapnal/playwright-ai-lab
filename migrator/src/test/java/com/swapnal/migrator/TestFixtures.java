package com.swapnal.migrator;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public final class TestFixtures {

    private TestFixtures() {
    }

    /** A small Selenium suite under src/test/resources used as migrator input. */
    public static Path fixtureSuite() {
        return resource("fixture-suite");
    }

    public static Path resource(String name) {
        try {
            return Path.of(Objects.requireNonNull(TestFixtures.class.getClassLoader().getResource(name)).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
