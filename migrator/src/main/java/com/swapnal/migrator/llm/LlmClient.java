package com.swapnal.migrator.llm;

/** Minimal text-in/text-out interface so the migration logic does not depend on a specific provider. */
public interface LlmClient {

    /** Returns the model's text response. */
    String complete(LlmRequest request) throws LlmException;

    /** Human-readable description for logs and reports, e.g. {@code anthropic/claude-sonnet-5-5}. */
    String describe();
}
