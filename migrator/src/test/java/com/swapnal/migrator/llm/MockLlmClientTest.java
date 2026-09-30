package com.swapnal.migrator.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmClientTest {

    private final MockLlmClient mock = new MockLlmClient("test-mock-responses");

    @Test
    void returnsResponseForCurrentAttempt() throws Exception {
        String first = mock.complete(new LlmRequest("s", List.of(ChatMessage.user("convert")), "CheckoutTest"));
        String second = mock.complete(new LlmRequest("s", List.of(
                ChatMessage.user("convert"), ChatMessage.assistant(first), ChatMessage.user("fix")), "CheckoutTest"));

        assertTrue(first.contains("Total: 3"));
        assertTrue(second.contains("Total: 2"));
        assertEquals(2, mock.calls());
    }

    @Test
    void fallsBackToLastAvailableAttempt() throws Exception {
        List<ChatMessage> fiveTurns = List.of(ChatMessage.user("1"), ChatMessage.assistant("a"),
                ChatMessage.user("2"), ChatMessage.assistant("b"), ChatMessage.user("3"));

        String response = mock.complete(new LlmRequest("s", fiveTurns, "CheckoutTest"));

        assertTrue(response.contains("Total: 2"));
    }

    @Test
    void unknownTestClassIsAnError() {
        assertThrows(LlmException.class,
                () -> mock.complete(new LlmRequest("s", List.of(ChatMessage.user("x")), "UnknownTest")));
    }

    @Test
    void bundledResponsesCoverTheLegacySuite() throws Exception {
        MockLlmClient bundled = new MockLlmClient();
        for (String testClass : List.of("LoginTest", "DynamicLoadingTest", "FormControlsTest")) {
            String response = bundled.complete(new LlmRequest("s", List.of(ChatMessage.user("x")), testClass));
            assertTrue(response.contains("// file: tests/"), testClass);
        }
    }

    @Test
    void factoryDefaultsToMockWithoutKey() {
        assertInstanceOf(MockLlmClient.class, LlmClientFactory.fromEnvironment(Map.of()));
        assertInstanceOf(MockLlmClient.class,
                LlmClientFactory.fromEnvironment(Map.of("ANTHROPIC_API_KEY", "k", "LLM_PROVIDER", "mock")));
    }

    @Test
    void factoryUsesAnthropicWhenKeyPresent() {
        LlmClient client = LlmClientFactory.fromEnvironment(Map.of("ANTHROPIC_API_KEY", "k"));

        assertEquals("anthropic/" + LlmClientFactory.DEFAULT_MODEL, client.describe());
        assertEquals("anthropic/claude-opus-5-5", LlmClientFactory.fromEnvironment(
                Map.of("ANTHROPIC_API_KEY", "k", "LLM_MODEL", "claude-opus-5-5")).describe());
    }

    @Test
    void factoryRejectsAnthropicWithoutKeyAndUnknownProviders() {
        assertThrows(IllegalStateException.class,
                () -> LlmClientFactory.fromEnvironment(Map.of("LLM_PROVIDER", "anthropic")));
        assertThrows(IllegalStateException.class,
                () -> LlmClientFactory.fromEnvironment(Map.of("LLM_PROVIDER", "other")));
    }
}
