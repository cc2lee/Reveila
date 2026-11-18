package com.reveila.ai.util

import com.reveila.ai.LlmRole
import com.reveila.ai.ReveilaMessage
import kotlin.jvm.JvmStatic

/**
 * Formats a conversation into the Gemma 2 / Gemma 3 Instruct chat template.
 * Gemma has no native 'system' role and requires strictly alternating user/model turns,
 * so system and tool messages are folded into the user turn buffer.
 */
class GemmaPromptFormatter private constructor() {

    companion object {
        @JvmStatic
        fun format(messages: List<ReveilaMessage>): String {
            val sb = StringBuilder()
            val pendingUserParts = mutableListOf<String>()

            fun flushUserTurn() {
                if (pendingUserParts.isNotEmpty()) {
                    sb.append("<start_of_turn>user\n")
                        .append(pendingUserParts.joinToString("\n\n"))
                        .append("<end_of_turn>\n")
                    pendingUserParts.clear()
                }
            }

            for (msg in messages) {
                val content = msg.content.trim()
                if (content.isEmpty()) continue

                when (msg.role) {
                    LlmRole.ASSISTANT -> {
                        flushUserTurn()
                        sb.append("<start_of_turn>model\n")
                            .append(content)
                            .append("<end_of_turn>\n")
                    }
                    LlmRole.SYSTEM, LlmRole.USER -> {
                        pendingUserParts.add(content)
                    }
                    LlmRole.TOOL -> {
                        pendingUserParts.add("[Tool Result]\n$content")
                    }
                }
            }

            flushUserTurn()
            sb.append("<start_of_turn>model\n")
            return sb.toString()
        }
    }
}
