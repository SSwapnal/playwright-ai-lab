package com.swapnal.migrator.llm;

/** One turn in a conversation with the model. {@code role} is {@code user} or {@code assistant}. */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
