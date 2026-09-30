package com.swapnal.migrator.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Offline stand-in for a real model. Returns canned responses from the classpath:
 * {@code <root>/<TestClass>/attempt-<n>.md}. If there is no file for the requested attempt, the
 * highest-numbered earlier attempt is returned.
 *
 * <p>The bundled responses for {@code LoginTest} deliberately start with a flawed conversion so the
 * run-and-fix loop can be demonstrated without an API key.
 */
public class MockLlmClient implements LlmClient {

    public static final String DEFAULT_ROOT = "mock-responses";

    private final String root;
    private final AtomicInteger calls = new AtomicInteger();

    public MockLlmClient() {
        this(DEFAULT_ROOT);
    }

    public MockLlmClient(String classpathRoot) {
        this.root = classpathRoot;
    }

    @Override
    public String complete(LlmRequest request) throws LlmException {
        calls.incrementAndGet();
        for (int attempt = request.attempt(); attempt >= 1; attempt--) {
            String resource = root + "/" + request.unitName() + "/attempt-" + attempt + ".md";
            String content = load(resource);
            if (content != null) {
                return content;
            }
        }
        throw new LlmException("No mock response for " + request.unitName() + " under classpath:" + root);
    }

    @Override
    public String describe() {
        return "mock (canned responses)";
    }

    public int calls() {
        return calls.get();
    }

    private static String load(String resource) throws LlmException {
        try (InputStream in = MockLlmClient.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Cannot read mock response " + resource, e);
        }
    }
}
