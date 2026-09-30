package com.swapnal.migrator.source;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the model needs to convert one test class: the class itself, the page objects it uses,
 * and supporting classes such as its base class (where driver setup and implicit waits usually live).
 */
public record TestSource(JavaSourceFile testClass,
                         List<JavaSourceFile> pageObjects,
                         List<JavaSourceFile> supportClasses) {

    public TestSource {
        pageObjects = List.copyOf(pageObjects);
        supportClasses = List.copyOf(supportClasses);
    }

    public String className() {
        return testClass.className();
    }

    /** Test class first, then support classes, then page objects. */
    public List<JavaSourceFile> allFiles() {
        List<JavaSourceFile> all = new ArrayList<>();
        all.add(testClass);
        all.addAll(supportClasses);
        all.addAll(pageObjects);
        return all;
    }
}
