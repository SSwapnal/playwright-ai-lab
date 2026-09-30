package com.swapnal.migrator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Calls the Anthropic Messages API ({@code POST /v1/messages}) with {@code java.net.http}.
 *
 * <p>Retries rate limits (429), overload (529) and server errors (5xx) as well as connection failures,
 * with exponential backoff that honours the {@code retry-after} header. Other 4xx responses are not
 * retried because resending the same request will not change the outcome.
 */
public class AnthropicLlmClient implements LlmClient {

    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    static final String API_VERSION = "2023-06-01";
    static final int MAX_TOKENS = 16_000;

    /**
     * Models that accept server-side refusal fallbacks ({@code fallbacks: "default"}). If a safety
     * classifier declines a request, the API re-runs it on a recommended fallback model instead of
     * returning a refusal.
     */
    static final Set<String> FALLBACK_MODELS =
            Set.of("claude-sonnet-5-5", "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1");
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504, 529);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    /** Abstraction over {@link Thread#sleep} so tests do not have to wait for real backoff. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final int maxRetries;
    private final Sleeper sleeper;

    public AnthropicLlmClient(String apiKey, String model) {
        this(apiKey, model, DEFAULT_BASE_URL, 4, d -> Thread.sleep(d.toMillis()));
    }

    public AnthropicLlmClient(String apiKey, String model, String baseUrl, int maxRetries, Sleeper sleeper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY is not set");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/messages");
        this.maxRetries = maxRetries;
        this.sleeper = sleeper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override
    public String describe() {
        return "anthropic/" + model;
    }

    @Override
    public String complete(LlmRequest request) throws LlmException {
        HttpRequest httpRequest = buildHttpRequest(request);
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    throw new LlmException("Could not reach the Anthropic API: " + e.getMessage(), e);
                }
                backoff(attempt, null);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Interrupted while calling the Anthropic API", e);
            }

            int status = response.statusCode();
            if (status == 200) {
                return extractText(response.body());
            }
            if (RETRYABLE_STATUS.contains(status) && attempt < maxRetries) {
                backoff(attempt, response.headers().firstValue("retry-after").orElse(null));
                continue;
            }
            throw new LlmException("Anthropic API returned HTTP " + status + ": " + errorMessage(response.body()));
        }
    }

    HttpRequest buildHttpRequest(LlmRequest request) throws LlmException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(5))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION);
        if (FALLBACK_MODELS.contains(model)) {
            builder.header("anthropic-beta", FALLBACK_BETA);
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(requestBody(request))).build();
    }

    String requestBody(LlmRequest request) throws LlmException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", request.system());
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage m : request.messages()) {
            messages.addObject().put("role", m.role()).put("content", m.content());
        }
        if (FALLBACK_MODELS.contains(model)) {
            body.put("fallbacks", "default");
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new LlmException("Could not serialise request", e);
        }
    }

    String extractText(String responseBody) throws LlmException {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (IOException e) {
            throw new LlmException("Unparseable response from the Anthropic API", e);
        }
        String stopReason = root.path("stop_reason").asText();
        if ("refusal".equals(stopReason)) {
            String category = root.path("stop_details").path("category").asText("unspecified");
            throw new LlmException("The model declined the request (refusal category: " + category + ")");
        }
        if ("max_tokens".equals(stopReason)) {
            throw new LlmException("Response was truncated at max_tokens=" + MAX_TOKENS
                    + "; the generated files would be incomplete");
        }
        // Read content blocks by type: with thinking enabled a response can start with a thinking block.
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new LlmException("Response contained no text (stop_reason=" + stopReason + ")");
        }
        return text.toString();
    }

    private String errorMessage(String body) {
        try {
            JsonNode error = mapper.readTree(body).path("error");
            if (error.hasNonNull("message")) {
                return error.path("type").asText() + ": " + error.path("message").asText();
            }
        } catch (IOException ignored) {
            // fall through to the raw body
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }

    private void backoff(int attempt, String retryAfterHeader) throws LlmException {
        Duration delay = retryAfter(retryAfterHeader);
        if (delay == null) {
            long exponential = BASE_BACKOFF.toMillis() * (1L << attempt);
            long jitter = ThreadLocalRandom.current().nextLong(250);
            delay = Duration.ofMillis(Math.min(exponential, MAX_BACKOFF.toMillis()) + jitter);
        }
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted during retry backoff", e);
        }
    }

    static Duration retryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.toSeconds()));
        } catch (NumberFormatException e) {
            return null; // HTTP-date form is not used by this API; fall back to exponential backoff
        }
    }
}
