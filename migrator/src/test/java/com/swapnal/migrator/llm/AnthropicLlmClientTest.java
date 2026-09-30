package com.swapnal.migrator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the HTTP client against a local stub server; no network access or API key needed. */
class AnthropicLlmClientTest {

    private static final String OK_BODY = """
            {"id":"msg_1","type":"message","role":"assistant","stop_reason":"end_turn",
             "content":[{"type":"thinking","thinking":""},{"type":"text","text":"converted"}]}
            """;

    private record Stub(int status, String body, String retryAfter) {
    }

    private HttpServer server;
    private final Deque<Stub> responses = new ArrayDeque<>();
    private final List<String> requestBodies = new ArrayList<>();
    private final List<String> apiKeys = new ArrayList<>();
    private final List<String> betas = new ArrayList<>();
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        apiKeys.add(exchange.getRequestHeaders().getFirst("x-api-key"));
        betas.add(exchange.getRequestHeaders().getFirst("anthropic-beta"));
        Stub stub = responses.isEmpty() ? new Stub(200, OK_BODY, null) : responses.poll();
        if (stub.retryAfter() != null) {
            exchange.getResponseHeaders().add("retry-after", stub.retryAfter());
        }
        byte[] bytes = stub.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(stub.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private AnthropicLlmClient client(String model) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AnthropicLlmClient("test-key", model, baseUrl, 3, sleeps::add);
    }

    private static LlmRequest request() {
        return new LlmRequest("system rules", List.of(ChatMessage.user("convert this")), "LoginTest");
    }

    @Test
    void sendsMessagesRequestAndReturnsTextBlocks() throws Exception {
        String text = client("claude-sonnet-5-5").complete(request());

        assertEquals("converted", text);
        JsonNode body = new ObjectMapper().readTree(requestBodies.get(0));
        assertEquals("claude-sonnet-5-5", body.path("model").asText());
        assertEquals("system rules", body.path("system").asText());
        assertEquals("user", body.path("messages").get(0).path("role").asText());
        assertEquals("convert this", body.path("messages").get(0).path("content").asText());
        assertTrue(body.path("max_tokens").asInt() > 0);
        assertFalse(body.has("unitName"), "internal metadata is not sent");
        assertEquals("test-key", apiKeys.get(0));
    }

    @Test
    void enablesServerSideFallbackOnlyForSupportedModels() throws Exception {
        client("claude-sonnet-5-5").complete(request());
        client("claude-haiku-4-5").complete(request());

        assertEquals("default", new ObjectMapper().readTree(requestBodies.get(0)).path("fallbacks").asText());
        assertEquals(AnthropicLlmClient.FALLBACK_BETA, betas.get(0));
        assertFalse(new ObjectMapper().readTree(requestBodies.get(1)).has("fallbacks"));
        assertNull(betas.get(1));
    }

    @Test
    void retriesRateLimitAndServerErrorsThenSucceeds() throws Exception {
        responses.add(new Stub(429, "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}", "7"));
        responses.add(new Stub(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}", null));

        String text = client("claude-sonnet-5-5").complete(request());

        assertEquals("converted", text);
        assertEquals(3, requestBodies.size());
        assertEquals(Duration.ofSeconds(7), sleeps.get(0), "retry-after header is honoured");
        assertTrue(sleeps.get(1).toMillis() >= 4000, "exponential backoff on the second retry");
    }

    @Test
    void givesUpAfterMaxRetries() {
        for (int i = 0; i < 4; i++) {
            responses.add(new Stub(503, "unavailable", null));
        }

        LlmException e = assertThrows(LlmException.class, () -> client("claude-sonnet-5-5").complete(request()));

        assertTrue(e.getMessage().contains("HTTP 503"));
        assertEquals(4, requestBodies.size(), "1 call + 3 retries");
    }

    @Test
    void doesNotRetryClientErrors() {
        responses.add(new Stub(400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad field\"}}", null));

        LlmException e = assertThrows(LlmException.class, () -> client("claude-sonnet-5-5").complete(request()));

        assertEquals(1, requestBodies.size());
        assertTrue(e.getMessage().contains("invalid_request_error: bad field"));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void refusalIsReportedNotReturnedAsText() {
        responses.add(new Stub(200, """
                {"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber"},"content":[]}
                """, null));

        LlmException e = assertThrows(LlmException.class, () -> client("claude-sonnet-5-5").complete(request()));

        assertTrue(e.getMessage().contains("cyber"));
    }

    @Test
    void truncatedResponseIsAnError() {
        responses.add(new Stub(200, """
                {"stop_reason":"max_tokens","content":[{"type":"text","text":"```ts\\n// file: tests/a"}]}
                """, null));

        LlmException e = assertThrows(LlmException.class, () -> client("claude-sonnet-5-5").complete(request()));

        assertTrue(e.getMessage().contains("truncated"));
    }

    @Test
    void retryAfterParsing() {
        assertEquals(Duration.ofSeconds(3), AnthropicLlmClient.retryAfter("3"));
        assertEquals(Duration.ofSeconds(30), AnthropicLlmClient.retryAfter("600"), "capped");
        assertNull(AnthropicLlmClient.retryAfter("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertNull(AnthropicLlmClient.retryAfter(null));
    }

    @Test
    void missingKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AnthropicLlmClient(" ", "claude-sonnet-5-5"));
    }
}
