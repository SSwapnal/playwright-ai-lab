package com.swapnal.migrator.llm;

import java.util.List;

/**
 * A request to the model.
 *
 * @param system   system prompt (conversion rules)
 * @param messages conversation so far; the fix loop appends to it rather than rewriting it
 * @param unitName the test class being migrated. Not sent to the API; used for logging and by the mock client.
 */
public record LlmRequest(String system, List<ChatMessage> messages, String unitName) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }

    /** 1 for the initial conversion, 2 for the first fix request, and so on. */
    public int attempt() {
        return (int) messages.stream().filter(m -> m.role().equals("user")).count();
    }
}
