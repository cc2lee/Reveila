package com.reveila.ai.util;

import com.reveila.ai.ReveilaMessage;
import java.util.List;

/**
 * Formats a conversation into the Gemma 2 2b-it chat template.
 * * Template:
 * <start_of_turn>user
 * {content}<end_of_turn>
 * <start_of_turn>model
 * {content}<end_of_turn>
 */
public class GemmaPromptFormatter {

    public static String format(List<ReveilaMessage> messages) {
        StringBuilder sb = new StringBuilder();

        for (ReveilaMessage msg : messages) {
            String role = msg.role().name().toLowerCase();

            // Map 'assistant' to 'model' for Gemma's expected role tokens
            if (role.equals("assistant")) {
                role = "model";
            } else if (role.equals("system")) {
                // Gemma doesn't have a native 'system' role,
                // so we treat it as a user instruction or prepend to user.
                role = "user";
            }

            sb.append("<start_of_turn>").append(role).append("\n")
                    .append(msg.content().trim())
                    .append("<end_of_turn>\n");
        }

        // Add the final trigger to prompt the model to begin its response
        sb.append("<start_of_turn>model\n");

        return sb.toString();
    }
}