package com.reveila.ai.util

import com.reveila.ai.ReveilaMessage

/**
 * Formats a conversation into the Gemma 2 2b-it chat template.
 */
class GemmaPromptFormatter private constructor() {

    companion object {
        @JvmStatic
        fun format(messages: List<ReveilaMessage>): String {
            val sb = StringBuilder()

            for (msg in messages) {
                var role = msg.role.name.lowercase()

                // Map 'assistant' to 'model' for Gemma's expected role tokens
                if (role == "assistant") {
                    role = "model"
                } else if (role == "system") {
                    // Gemma doesn't have a native 'system' role,
                    // so we treat it as a user instruction or prepend to user.
                    role = "user"
                }

                sb.append("<start_of_turn>").append(role).append("\n")
                    .append(msg.content.trim())
                    .append("<end_of_turn>\n")
            }

            // Add the final trigger to prompt the model to begin its response
            sb.append("<start_of_turn>model\n")

            return sb.toString()
        }
    }
}
