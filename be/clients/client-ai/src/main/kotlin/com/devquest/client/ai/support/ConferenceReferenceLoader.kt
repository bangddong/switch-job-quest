package com.devquest.client.ai.support

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConferenceReference(
    val title: String = "",
    val url: String = "",
    val source: String = "",
    val year: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConferenceCategory(
    val name: String = "",
    val label: String = "",
    val keywords: List<String> = emptyList(),
    val references: List<ConferenceReference> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ConferenceReferencesJson(
    val categories: List<ConferenceCategory> = emptyList(),
)

@Component
class ConferenceReferenceLoader {

    private val log = LoggerFactory.getLogger(javaClass)

    private val categories: List<ConferenceCategory> = run {
        val objectMapper = jacksonObjectMapper()
        val resource = ClassPathResource("references/conference-references.json")
        val parsed = objectMapper.readValue<ConferenceReferencesJson>(resource.inputStream)
        parsed.categories
    }

    fun findByQuestion(questionText: String): String {
        val lowerQuestion = questionText.lowercase()

        val matched = categories.filter { category ->
            category.keywords.any { keyword -> lowerQuestion.contains(keyword.lowercase()) }
        }.filter { it.references.isNotEmpty() }

        if (matched.isEmpty()) {
            return ""
        }

        log.debug("컨퍼런스 참고자료 매칭 카테고리: {}", matched.map { it.label })

        return matched.joinToString("\n\n") { category ->
            val links = category.references.joinToString("\n") { ref ->
                "- [${ref.title}](${ref.url})"
            }
            "### ${category.label}\n$links"
        }
    }
}
