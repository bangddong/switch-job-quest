package com.devquest.core.domain

object PassCriteriaPolicy {

    private const val DEFAULT_PASS_SCORE = 70

    fun evaluate(score: Int, passScore: Int = DEFAULT_PASS_SCORE): Boolean =
        score >= passScore

    fun evaluateMax(scores: List<Int>, passScore: Int = DEFAULT_PASS_SCORE): Boolean =
        scores.isNotEmpty() && scores.max() >= passScore
}
