package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.TestCase
import com.devquest.core.domain.port.CodingProblemPort
import com.devquest.storage.db.core.CodingProblemEntity
import com.devquest.storage.db.core.CodingProblemRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Component

@Component
class CodingProblemAdapter(
    private val repository: CodingProblemRepository
) : CodingProblemPort {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    override fun save(problem: CodingProblem): CodingProblem {
        val entity = CodingProblemEntity(
            title = problem.title,
            description = problem.description,
            difficulty = problem.difficulty,
            language = problem.language,
            category = problem.category.ifBlank { null },
            solutionCode = problem.solutionCode,
            testCases = objectMapper.writeValueAsString(problem.testCases)
        )
        return repository.save(entity).toDomain()
    }

    override fun findById(id: Long): CodingProblem? {
        return repository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByDifficultyAndLanguage(difficulty: String, language: String): List<CodingProblem> {
        return repository.findByDifficultyAndLanguage(difficulty, language).map { it.toDomain() }
    }

    override fun findByCategoryAndLanguage(category: String, language: String): List<CodingProblem> {
        return repository.findByCategoryAndLanguage(category, language).map { it.toDomain() }
    }

    private fun CodingProblemEntity.toDomain(): CodingProblem {
        val testCaseList: List<TestCase> = runCatching {
            objectMapper.readValue(this.testCases, object : TypeReference<List<TestCase>>() {})
        }.getOrElse { emptyList() }

        return CodingProblem(
            id = this.id,
            title = this.title,
            description = this.description,
            difficulty = this.difficulty,
            language = this.language,
            category = this.category ?: "",
            solutionCode = this.solutionCode,
            testCases = testCaseList
        )
    }
}
