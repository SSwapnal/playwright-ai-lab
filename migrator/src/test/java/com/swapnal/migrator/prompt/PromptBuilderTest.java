package com.swapnal.migrator.prompt;

import com.swapnal.migrator.TestFixtures;
import com.swapnal.migrator.runner.RunResult;
import com.swapnal.migrator.runner.TestFailure;
import com.swapnal.migrator.source.SourceCollector;
import com.swapnal.migrator.source.TestSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();
    private final TestSource source = new SourceCollector(TestFixtures.fixtureSuite()).collect("CheckoutTest");

    @Test
    void systemPromptStatesTheKeyConversionRules() {
        String system = builder.systemPrompt();

        assertTrue(system.contains("Never use page.waitForTimeout()"), "no fixed waits");
        assertTrue(system.contains("Preserve every assertion"), "assertions preserved");
        assertTrue(system.contains("selectOption"), "Select mapping");
        assertTrue(system.contains("toHaveCount"), "findElements mapping");
        assertTrue(system.contains("pages/<ClassName>.ts"), "page object mapping");
        assertTrue(system.contains("// file: "), "output format");
    }

    @Test
    void conversionPromptListsTargetsAndIncludesAllSources() {
        String prompt = builder.conversionPrompt(source, Map.of());

        assertTrue(prompt.contains("- tests/checkout.spec.ts (spec)"));
        assertTrue(prompt.contains("- pages/CartPage.ts (page object CartPage)"));
        assertTrue(prompt.contains("- pages/SummaryPage.ts (page object SummaryPage)"));
        assertTrue(prompt.contains("## Test class: CheckoutTest.java"));
        assertTrue(prompt.contains("## Supporting class (context only, do not emit a file for it): BaseTest.java"));
        assertTrue(prompt.contains("implicitlyWait"), "base class content is included");
        assertTrue(prompt.contains("## Page object: SummaryPage.java"));
        assertFalse(prompt.contains("Existing TypeScript page"));
    }

    @Test
    void conversionPromptIncludesPagesGeneratedEarlier() {
        String prompt = builder.conversionPrompt(source,
                Map.of("pages/CartPage.ts", "export class CartPage {}"));

        assertTrue(prompt.contains("## Existing TypeScript page: pages/CartPage.ts"));
        assertTrue(prompt.contains("export class CartPage {}"));
    }

    @Test
    void fixPromptContainsEachFailureAndGuardsAssertions() {
        RunResult result = new RunResult(3, 1, List.of(
                new TestFailure("Checkout > shows total", "Expected: \"Total: 3\"\nReceived: \"Total: 2\""),
                new TestFailure("Checkout > shows tax", "Timeout 10000ms exceeded")));

        String prompt = builder.fixPrompt("tests/checkout.spec.ts", result);

        assertTrue(prompt.startsWith("I ran `npx playwright test tests/checkout.spec.ts` and 2 of 3 test(s) failed."));
        assertTrue(prompt.contains("### Checkout > shows total"));
        assertTrue(prompt.contains("Received: \"Total: 2\""));
        assertTrue(prompt.contains("### Checkout > shows tax"));
        assertTrue(prompt.contains("do not delete or loosen an assertion"));
    }

    @Test
    void fixPromptExplainsWhenNoTestsRan() {
        RunResult result = RunResult.error("Error loading spec", "SyntaxError: Unexpected token");

        String prompt = builder.fixPrompt("tests/checkout.spec.ts", result);

        assertTrue(prompt.contains("no tests ran"));
        assertTrue(prompt.contains("SyntaxError: Unexpected token"));
    }

    @Test
    void longErrorsAreTruncated() {
        String longMessage = "x".repeat(PromptBuilder.MAX_ERROR_CHARS + 100);

        String truncated = PromptBuilder.truncate(longMessage);

        assertEquals(PromptBuilder.MAX_ERROR_CHARS + "\n... (truncated)".length(), truncated.length());
    }
}
