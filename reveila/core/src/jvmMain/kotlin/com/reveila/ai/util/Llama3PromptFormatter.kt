package com.reveila.ai.util

import com.reveila.ai.ReveilaMessage

/**
 * Formats a conversation into the Llama 3 Instruct template.
 */
class Llama3PromptFormatter private constructor() {

    companion object {
        @JvmStatic
        fun format(messages: List<ReveilaMessage>): String {
            val sb = StringBuilder()

            // Start the sequence
            sb.append("<|begin_of_text|>")

            for (msg in messages) {
                var role = msg.role.name.lowercase()

                // Map role names if necessary
                if (role == "model") {
                    role = "assistant"
                }

                sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                    .append(msg.content.trim())
                    .append("<|eot_id|>")
            }

            // Final header to trigger the assistant's generation
            sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")

            return sb.toString()
        }
    }
}
