package com.swapnal.migrator.source;

import com.swapnal.migrator.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceCollectorTest {

    private final SourceCollector collector = new SourceCollector(TestFixtures.fixtureSuite());

    @Test
    void discoversOnlyClassesWithTestMethods() {
        assertEquals(List.of("CheckoutTest"), collector.discoverTestClasses());
    }

    @Test
    void collectsImportedPageObjectsTransitively() {
        TestSource source = collector.collect("CheckoutTest");

        assertEquals("CheckoutTest", source.className());
        assertEquals(List.of("CartPage", "SummaryPage"), names(source.pageObjects()));
    }

    @Test
    void collectsSamePackageBaseClassAsSupport() {
        TestSource source = collector.collect("CheckoutTest");

        assertEquals(List.of("BaseTest"), names(source.supportClasses()));
        assertTrue(source.supportClasses().get(0).content().contains("implicitlyWait"));
    }

    @Test
    void ignoresClassNamesInCommentsAndStrings() {
        TestSource source = collector.collect("CheckoutTest");

        assertTrue(source.allFiles().stream().noneMatch(f -> f.className().equals("DashboardPage")));
    }

    @Test
    void allFilesListsTestClassFirst() {
        TestSource source = collector.collect("CheckoutTest");

        assertEquals(List.of("CheckoutTest", "BaseTest", "CartPage", "SummaryPage"), names(source.allFiles()));
    }

    @Test
    void unknownClassIsReported() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> collector.collect("MissingTest"));
        assertTrue(e.getMessage().contains("MissingTest"));
    }

    private static List<String> names(List<JavaSourceFile> files) {
        return files.stream().map(JavaSourceFile::className).toList();
    }
}
