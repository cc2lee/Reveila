package com.reveila.ai.util;

import com.reveila.ai.ReveilaMessage;
import java.util.List;

/**
 * Formats a conversation into the Llama 3 Instruct template.
 * * Template:
 * <|begin_of_text|><|start_header_id|>system<|end_header_id|>
 * {system_prompt}<|eot_id|>
 * <|start_header_id|>user<|end_header_id|>
 * {user_message}<|eot_id|>
 * <|start_header_id|>assistant<|end_header_id|>
 */
public class Llama3PromptFormatter {

    public static String format(List<ReveilaMessage> messages) {
        StringBuilder sb = new StringBuilder();

        // Start the sequence
        sb.append("<|begin_of_text|>");

        for (ReveilaMessage msg : messages) {
            String role = msg.role().name().toLowerCase();

            // Map role names if necessary
            if (role.equals("model"))
                role = "assistant";

            sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                    .append(msg.content().trim())
                    .append("<|eot_id|>");
        }

        // Final header to trigger the assistant's generation
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n");

        return sb.toString();
    }
}