package com.devquest.client.ai.support

import org.springframework.ai.chat.client.ChatClient

abstract class BaseAiEvaluator(
    protected val chatClient: ChatClient,
    protected val aiCallExecutor: AiCallExecutor
)
