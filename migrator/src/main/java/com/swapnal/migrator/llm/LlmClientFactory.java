package com.swapnal.migrator.llm;

import java.util.Locale;
import java.util.Map;

/**
 * Chooses the LLM client from environment variables:
 * <ul>
 *   <li>{@code LLM_PROVIDER} - {@code anthropic} or {@code mock}. Defaults to {@code anthropic} when
 *       {@code ANTHROPIC_API_KEY} is set, otherwise {@code mock}.</li>
 *   <li>{@code LLM_MODEL} - model id, default {@value #DEFAULT_MODEL}.</li>
 *   <li>{@code ANTHROPIC_API_KEY} - required for the anthropic provider.</li>
 *   <li>{@code ANTHROPIC_BASE_URL} - optional, e.g. for a proxy.</li>
 * </ul>
 */
public final class LlmClientFactory {

    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    private LlmClientFactory() {
    }

    public static LlmClient fromEnvironment(Map<String, String> env) {
        String apiKey = env.get("ANTHROPIC_API_KEY");
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        String provider = env.getOrDefault("LLM_PROVIDER", hasKey ? "anthropic" : "mock")
                .trim().toLowerCase(Locale.ROOT);

        switch (provider) {
            case "mock":
                return new MockLlmClient();
            case "anthropic":
                if (!hasKey) {
                    throw new IllegalStateException(
                            "LLM_PROVIDER=anthropic but ANTHROPIC_API_KEY is not set. "
                                    + "Export the key or use LLM_PROVIDER=mock.");
                }
                String model = env.getOrDefault("LLM_MODEL", DEFAULT_MODEL);
                String baseUrl = env.getOrDefault("ANTHROPIC_BASE_URL", AnthropicLlmClient.DEFAULT_BASE_URL);
                return new AnthropicLlmClient(apiKey, model, baseUrl, 4, d -> Thread.sleep(d.toMillis()));
            default:
                throw new IllegalStateException("Unknown LLM_PROVIDER '" + provider + "' (expected anthropic or mock)");
        }
    }
}
