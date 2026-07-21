package com.devquest.core.domain.port

import com.devquest.core.domain.port.ai.AiEvaluatorPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * 서비스 분해 에픽 Phase 0 Task 0.1 — 무행동 구조 정리 검증.
 *
 * `port` 패키지(최상위, 하위 패키지 제외)에 선언된 모든 인터페이스/클래스를 스캔하여,
 * `AiEvaluatorPort`(AI/LLM 컴퓨트 포트 마커)를 상속한 타입이 정확히 아래 17개인지 검증한다.
 * DB 영속성 포트, Judge0Port(비-LLM 외부 코드채점 어댑터)는 절대 포함되면 안 된다.
 */
class ArchAiPortConventionTest {

    private val expectedAiEvaluatorPortSimpleNames = setOf(
        // *EvaluatorPort 접미사 10개
        "BlogEvaluatorPort",
        "ResumeEvaluatorPort",
        "EssayEvaluatorPort",
        "InterviewEvaluatorPort",
        "PersonalityEvaluatorPort",
        "SystemDesignEvaluatorPort",
        "CompanyFitEvaluatorPort",
        "JdAnalysisEvaluatorPort",
        "BossPackageEvaluatorPort",
        "DeveloperClassEvaluatorPort",
        // *Port (접미사 다름) 7개
        "CodingProblemGeneratorPort",
        "CodingHintPort",
        "SkillAssessmentPort",
        "JourneyReportPort",
        "ActClearReportPort",
        "InterviewCoachPort",
        "TechInterviewPort",
    )

    @Test
    fun `port 패키지를 스캔하면 AiEvaluatorPort 하위 타입이 정확히 17개다`() {
        val aiPorts = scanAiEvaluatorPortsInTopLevelPortPackage()

        assertThat(aiPorts).hasSize(17)
        assertThat(aiPorts.mapNotNull { it.simpleName }.toSet())
            .isEqualTo(expectedAiEvaluatorPortSimpleNames)
    }

    @Test
    fun `Judge0Port는 비-LLM 외부 코드채점 어댑터이므로 AiEvaluatorPort를 상속하지 않는다`() {
        assertThat(Judge0Port::class.isSubclassOf(AiEvaluatorPort::class)).isFalse()
    }

    @Test
    fun `DB 영속성 포트는 AiEvaluatorPort를 상속하지 않는다`() {
        val dbPersistencePorts = listOf(
            CompanyPort::class,
            QuestProgressPort::class,
            CodingSubmissionPort::class,
            UserResumePort::class,
            DailyMailLogPort::class,
            CompanyActivityPort::class,
            AiCallLogPort::class,
            CodingProblemPort::class,
            CodingRankPort::class,
            CodingRoadmapProgressPort::class,
            QuestHistoryPort::class,
            TechQuestionBankPort::class,
            UserCodingLevelPort::class,
            UserEmailPort::class,
        )

        dbPersistencePorts.forEach { port ->
            assertThat(port.isSubclassOf(AiEvaluatorPort::class))
                .withFailMessage("${port.simpleName}은 DB 영속성 포트이므로 AiEvaluatorPort를 상속하면 안 된다")
                .isFalse()
        }
    }

    /**
     * `com.devquest.core.domain.port` 패키지의 최상위(직속) 클래스 파일만 스캔한다.
     * 하위 패키지(`port.ai` 등)는 제외 — 마커 인터페이스 자신이 스캔 대상에 섞이는 것을 방지.
     *
     * 테스트 클래스패스에는 `testClasses`와 main `classes` 디렉터리가 모두 올라가므로,
     * 동일 패키지 경로에 대해 `getResources`(복수형)로 모든 디렉터리를 모아 병합한다
     * (`getResource` 단수형은 첫 매치만 반환해 main 클래스 디렉터리를 놓칠 수 있다).
     */
    private fun scanAiEvaluatorPortsInTopLevelPortPackage(): List<KClass<*>> {
        val packageName = "com.devquest.core.domain.port"
        val packagePath = packageName.replace('.', '/')
        val classLoader = Thread.currentThread().contextClassLoader

        val classDirs = classLoader.getResources(packagePath).toList()
            .map { File(it.toURI()) }
            .filter { it.isDirectory }

        check(classDirs.isNotEmpty()) { "클래스패스에서 패키지를 찾을 수 없다: $packageName" }

        return classDirs
            .flatMap { dir -> dir.listFiles { file -> file.isFile && file.extension == "class" }.orEmpty().toList() }
            .distinctBy { it.name }
            .map { classLoader.loadClass("$packageName.${it.nameWithoutExtension}").kotlin }
            .filter { it != AiEvaluatorPort::class && it.isSubclassOf(AiEvaluatorPort::class) }
    }
}
