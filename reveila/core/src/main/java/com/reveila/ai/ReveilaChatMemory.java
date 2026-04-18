package com.reveila.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Custom ChatMemory implementation for Reveila LLM interactions.
 */
public class ReveilaChatMemory {
    private final int maxMessages;
    private final List<ReveilaMessage> messages = Collections.synchronizedList(new ArrayList<>());

    public ReveilaChatMemory(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public void add(ReveilaMessage message) {
        messages.add(message);
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    public List<ReveilaMessage> messages() {
        return new ArrayList<>(messages);
    }

    public void clear() {
        messages.clear();
    }
}
