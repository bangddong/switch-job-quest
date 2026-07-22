package com.devquest.ai.controller.request

data class ResumeEvaluateRequest(
    val targetCompany: String,
    val targetJd: String,
    val resumeContent: String,
)
