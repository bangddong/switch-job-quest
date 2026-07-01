package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.TechQuestionBank
import com.devquest.core.domain.port.TechQuestionBankPort
import com.devquest.storage.db.core.TechQuestionBankEntity
import com.devquest.storage.db.core.TechQuestionBankRepository
import org.springframework.stereotype.Component

@Component
class TechQuestionBankAdapter(
    private val repository: TechQuestionBankRepository,
) : TechQuestionBankPort {

    override fun findUnused(excludeQuestions: List<String>, category: String?): TechQuestionBank? {
        val candidates = when {
            category != null && excludeQuestions.isNotEmpty() ->
                repository.findAllByCategoryAndQuestionNotIn(category, excludeQuestions)
            category != null ->
                repository.findAllByCategory(category)
            excludeQuestions.isNotEmpty() ->
                repository.findAllByQuestionNotIn(excludeQuestions)
            else ->
                repository.findAll()
        }
        return candidates.randomOrNull()?.toDomain()
    }
}

private fun TechQuestionBankEntity.toDomain() = TechQuestionBank(
    id = this.id,
    category = this.category,
    question = this.question,
    referenceUrl = this.referenceUrl,
    source = this.source,
    createdAt = this.createdAt,
)
