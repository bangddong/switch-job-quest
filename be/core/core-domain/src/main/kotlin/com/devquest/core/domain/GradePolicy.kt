package com.devquest.core.domain

object GradePolicy {

    fun from(score: Int): String = when {
        score >= 90 -> "S"
        score >= 80 -> "A"
        score >= 70 -> "B"
        score >= 60 -> "C"
        else -> "D"
    }
}
