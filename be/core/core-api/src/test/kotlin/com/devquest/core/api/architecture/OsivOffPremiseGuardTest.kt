package com.devquest.core.api.architecture

import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.RegexPatternTypeFilter
import java.io.File
import java.util.regex.Pattern

/**
 * OSIV OFF(B7-21) 구조 가드 - 실제 코드베이스를 스캔한다. 위반 시 [OsivOffPremiseChecks.GUIDANCE] 가 메시지에 붙는다.
 * 검사 로직은 [OsivOffPremiseChecks] 에 있고, "검사기가 실제로 잡는가" 는 [OsivOffPremiseChecksTest] 가 맡는다.
 *
 * 스캔이 비어 있어서 초록이 되는 것을 막으려고 각 테스트에 "스캔 대상이 실제로 있다" 단언을 먼저 둔다.
 */
class OsivOffPremiseGuardTest {

    private fun scanClasses(vararg filters: org.springframework.core.type.filter.TypeFilter, basePackage: String): List<Class<*>> {
        // 기본 구현은 추상 클래스(BaseEntity 등)를 후보에서 뺀다 - 연관관계는 슈퍼클래스에도 있을 수 있으므로 전부 본다.
        val provider = object : ClassPathScanningCandidateComponentProvider(false) {
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean = true
        }
        filters.forEach { provider.addIncludeFilter(it) }
        return provider.findCandidateComponents(basePackage)
            .mapNotNull(BeanDefinition::getBeanClassName)
            .map { Class.forName(it) }
    }

    /** 앱의 실제 JPA 엔티티 스캔 범위(`com.devquest` 전체)로 훑는다 - db-core 밖에 새로 생긴 `@Entity` 도 걸린다. */
    private fun persistenceClasses(): List<Class<*>> = scanClasses(
        AnnotationTypeFilter(Entity::class.java),
        AnnotationTypeFilter(MappedSuperclass::class.java),
        AnnotationTypeFilter(Embeddable::class.java),
        basePackage = OsivOffPremiseChecks.ENTITY_SCAN_PACKAGE,
    )

    /** `be/` 루트(= settings.gradle.kts 가 있는 디렉터리). Gradle 테스트의 작업 디렉터리는 모듈 디렉터리이므로 위로 올라가며 찾는다. */
    private fun beRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("settings.gradle.kts 를 찾지 못했다 - 소스 스캔 기준 디렉터리를 정할 수 없다 (user.dir=${System.getProperty("user.dir")})")
    }

    /** db-core 를 제외할지 여부와 무관하게 `be/` 아래 모든 모듈의 `src/main` Kotlin 소스. 경로는 be 루트 기준 상대 경로. */
    private fun mainSources(): Map<String, String> {
        val root = beRoot()
        return root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "node_modules" }
            .filter { it.isFile && it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/main/") }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
    }

    @Test
    fun `스캔 대상이 실제로 있다 - 빈 입력으로 가드가 거짓 통과하지 않는다`() {
        assertThat(persistenceClasses().map { it.simpleName })
            .describedAs("db-core 영속성 클래스 스캔").contains("CodingProblemEntity", "QuestProgressEntity", "BaseEntity")
        val sources = mainSources()
        assertThat(sources.keys).describedAs("main 소스 스캔").anyMatch { it.endsWith("core/core-api/src/main/kotlin/com/devquest/DevQuestApplication.kt") }
        assertThat(sources.keys).anyMatch { it.startsWith("storage/db-core/src/main/") }
        assertThat(sources.keys).anyMatch { it.startsWith("core/daily-api/src/main/") }
    }

    @Test
    fun `db-core 엔티티에 연관관계 컬렉션 지연 Basic 이 없다`() {
        val violations = OsivOffPremiseChecks.lazyAssociationViolations(persistenceClasses())

        assertThat(violations).describedAs(OsivOffPremiseChecks.GUIDANCE).isEmpty()
    }

    @Test
    fun `main 소스에 getReferenceById 같은 지연 프록시 API 호출이 없다`() {
        val violations = OsivOffPremiseChecks.lazyLoadingApiViolations(mainSources())

        assertThat(violations).describedAs(OsivOffPremiseChecks.GUIDANCE).isEmpty()
    }

    @Test
    fun `db-core 밖의 main 코드는 Entity 와 Repository 를 직접 참조하지 않는다`() {
        val outsideDbCore = mainSources().filterKeys { !it.startsWith("storage/db-core/") }

        val violations = OsivOffPremiseChecks.persistenceLeakViolations(outsideDbCore)

        assertThat(violations).describedAs(OsivOffPremiseChecks.GUIDANCE).isEmpty()
    }

    @Test
    fun `db-core 어댑터의 public 메서드 반환형에 Entity 가 없다`() {
        val adapters = scanClasses(
            RegexPatternTypeFilter(Pattern.compile(".*Adapter")),
            basePackage = "${OsivOffPremiseChecks.dbCorePackage()}.adapter",
        )
        assertThat(adapters.map { it.simpleName }).describedAs("어댑터 스캔").contains("CodingProblemAdapter", "RateLimitCounterAdapter")

        val violations = OsivOffPremiseChecks.entityLeakingReturnTypeViolations(adapters)

        assertThat(violations).describedAs(OsivOffPremiseChecks.GUIDANCE).isEmpty()
    }
}
