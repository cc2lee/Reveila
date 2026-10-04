package com.reveila.ai

interface ReveilaTool {
    fun getName(): String
    fun getDescription(): String
    fun execute(args: Map<String, Any?>): String
}
