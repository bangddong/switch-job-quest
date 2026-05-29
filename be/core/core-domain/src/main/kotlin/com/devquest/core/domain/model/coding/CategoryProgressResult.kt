package com.devquest.core.domain.model.coding

data class CategoryProgressResult(
    val category: String = "",
    val displayName: String = "",
    val order: Int = 0,
    val solvedCount: Int = 0,
    val locked: Boolean = true
)
