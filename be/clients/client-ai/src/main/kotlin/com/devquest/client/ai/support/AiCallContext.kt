package com.devquest.client.ai.support

object AiCallContext {
    private val threadLocal = ThreadLocal<String>()

    fun set(evaluatorName: String) {
        threadLocal.set(evaluatorName)
    }

    fun get(): String = threadLocal.get() ?: "Unknown"

    fun clear() {
        threadLocal.remove()
    }
}
