package com.devquest.ai.controller.request

data class BossPackageEvaluateRequest(
    val resumeContent: String,
    val githubUrl: String,
    val blogUrl: String,
    val targetPosition: String,
)
