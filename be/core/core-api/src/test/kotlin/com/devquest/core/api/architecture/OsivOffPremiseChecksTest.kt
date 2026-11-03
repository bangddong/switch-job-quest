package com.devquest.core.api.architecture

import jakarta.persistence.Basic
import jakarta.persistence.ElementCollection
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [OsivOffPremiseChecks] 의 **실패 주입** 테스트 - 위반 샘플을 먹여 검사기가 실제로 잡는지 영구히 확인한다.
 * (실제 코드베이스를 스캔하는 [OsivOffPremiseGuardTest] 는 지금 깨끗해서 초록이다. 초록만으로는 검사기가 일을 하는지 알 수 없다.)
 *
 * 🔴 픽스처에는 `@Entity` 를 붙이지 않는다: core-api 는 `@EntityScan` 없이 `com.devquest` 아래를 스캔하므로 테스트 클래스에
 * `@Entity` 를 달면 실제 JPA 메타모델에 끼어들어 다른 테스트를 깬다. 검사기는 클래스 목록을 인자로 받으므로 어노테이션 없이도 시험된다.
 */
class OsivOffPremiseChecksTest {

    // ---- 픽스처: 연관관계/지연 필드 ----

    @Suppress("unused")
    class WithOneToMany { @OneToMany val items: List<String> = emptyList() }

    @Suppress("unused")
    class WithManyToOne { @ManyToOne val owner: String = "" }

    @Suppress("unused")
    class WithOneToOne { @OneToOne val other: String = "" }

    @Suppress("unused")
    class WithManyToMany { @ManyToMany val peers: List<String> = emptyList() }

    @Suppress("unused")
    class WithElementCollection { @ElementCollection val tags: List<String> = emptyList() }

    @Suppress("unused")
    class WithLazyBasic { @Basic(fetch = FetchType.LAZY) val body: String = "" }

    @Suppress("unused")
    class WithEagerBasic { @Basic(fetch = FetchType.EAGER) val body: String = "" }

    @Suppress("unused")
    class Clean { val plain: String = "" }

    @Test
    fun `연관관계 컬렉션 지연 Basic 은 각각 잡는다`() {
        val cases = mapOf(
            WithOneToMany::class.java to "@OneToMany",
            WithManyToOne::class.java to "@ManyToOne",
            WithOneToOne::class.java to "@OneToOne",
            WithManyToMany::class.java to "@ManyToMany",
            WithElementCollection::class.java to "@ElementCollection",
            WithLazyBasic::class.java to "@Basic(fetch = LAZY)",
        )
        cases.forEach { (clazz, expected) ->
            val violations = OsivOffPremiseChecks.lazyAssociationViolations(listOf(clazz))
            assertThat(violations).describedAs(clazz.simpleName).hasSize(1)
            assertThat(violations.single()).contains(expected).contains(clazz.simpleName)
        }
    }

    @Test
    fun `일반 필드와 즉시 Basic 은 위반이 아니다`() {
        assertThat(OsivOffPremiseChecks.lazyAssociationViolations(listOf(Clean::class.java, WithEagerBasic::class.java))).isEmpty()
    }

    // ---- 픽스처: 어댑터 반환형 ----

    @Suppress("unused")
    class FakeEntity(val id: Long = 0)

    @Suppress("unused")
    class LeakyAdapter {
        fun direct(): FakeEntity = FakeEntity()
        fun nullable(): FakeEntity? = null
        fun inList(): List<FakeEntity> = emptyList()
        fun inMap(): Map<String, List<FakeEntity>> = emptyMap()
        fun inArray(): Array<FakeEntity> = emptyArray()
        fun inWildcard(): List<out FakeEntity> = emptyList()
    }

    @Suppress("unused")
    class SafeAdapter {
        fun domain(): String = ""
        fun listOfDomain(): List<String> = emptyList()
        fun count(): Long = 0
        private fun hidden(): FakeEntity = FakeEntity()
    }

    @Test
    fun `어댑터 반환형 노출은 제네릭 인자 배열 와일드카드까지 잡는다`() {
        val violations = OsivOffPremiseChecks.entityLeakingReturnTypeViolations(listOf(LeakyAdapter::class.java))

        assertThat(violations).hasSize(6)
        listOf("direct", "nullable", "inList", "inMap", "inArray", "inWildcard").forEach { name ->
            assertThat(violations).describedAs(name).anyMatch { it.contains("$name()") }
        }
    }

    @Test
    fun `도메인 반환형과 private 메서드는 위반이 아니다`() {
        assertThat(OsivOffPremiseChecks.entityLeakingReturnTypeViolations(listOf(SafeAdapter::class.java))).isEmpty()
    }

    // ---- 소스 스캔 ----

    @Test
    fun `getReferenceById 류 호출을 잡는다`() {
        val sources = mapOf(
            "a/A.kt" to "val e = repo.getReferenceById(id)",
            "a/B.kt" to "val e = repo.getOne(id)",
            "a/C.kt" to "val e = repo.getById(id)",
            "a/D.kt" to "val e = entityManager.getReference(Foo::class.java, id)",
            "a/E.kt" to "val e = repo\n    .getReferenceById (id)",
        )

        val violations = OsivOffPremiseChecks.lazyLoadingApiViolations(sources)

        assertThat(violations).hasSize(5)
    }

    @Test
    fun `주석에 적힌 이름과 비슷한 이름의 메서드는 오탐하지 않는다`() {
        val sources = mapOf(
            "a/A.kt" to "// repo.getReferenceById(id) 는 쓰지 않는다\nval x = 1",
            "a/B.kt" to "/** getOne(id) 도 마찬가지 */\nval y = 2",
            "a/C.kt" to "val z = service.getByIdentifier(id)\nval w = findById(id)",
        )

        assertThat(OsivOffPremiseChecks.lazyLoadingApiViolations(sources)).isEmpty()
    }

    @Test
    fun `문자열 속 슬래시별표 뒤에 오는 getReferenceById 도 잡는다 - 문자열이 주석 시작으로 오인되지 않는다`() {
        // SecurityConfig("/api/**")·WebMvcConfig 가 실제로 이 모양이다. 예전 정규식은 문자열의 `/*` 부터 다음 `*/` 까지를 주석으로 지웠다.
        val sources = mapOf(
            "a/Glob.kt" to "val patterns = arrayOf(\"/api/**\")\nval e = repo.getReferenceById(id)\n/** 아래 KDoc */\nfun f() {}",
            "a/Slashes.kt" to "val s = \"a // b\"; val e = repo.getReferenceById(id)",
            "a/Raw.kt" to "val s = \"\"\"/* raw\"\"\"\nval e = repo.getOne(id)\n/* 진짜 주석 */",
            "a/Template.kt" to "val s = \"x \${f(\"q\")} /*\"\nval e = repo.getById(id)\n/* 끝 */",
        )

        val violations = OsivOffPremiseChecks.lazyLoadingApiViolations(sources)

        assertThat(violations).hasSize(4)
        listOf("a/Glob.kt", "a/Slashes.kt", "a/Raw.kt", "a/Template.kt").forEach { path ->
            assertThat(violations).describedAs(path).anyMatch { it.startsWith(path) }
        }
    }

    @Test
    fun `문자열 처리 후에도 진짜 주석은 계속 지운다 - 중첩 블록 주석과 줄 주석`() {
        val sources = mapOf(
            "a/Nested.kt" to "/* 바깥 /* repo.getReferenceById(id) */ 아직 주석 repo.getOne(id) */\nval ok = 1",
            "a/Line.kt" to "val s = \"문자열\" // repo.getById(id)\nval ok = 2",
            "a/Char.kt" to "val q = '\"'\nval w = '/'\n// repo.getReferenceById(id)",
        )

        assertThat(OsivOffPremiseChecks.lazyLoadingApiViolations(sources)).isEmpty()
    }

    @Test
    fun `엔티티 스캔 패키지는 앱이 실제로 JPA 엔티티를 스캔하는 범위와 같다`() {
        // core-api 는 @EntityScan 없이 @SpringBootApplication 이 속한 패키지 아래를 전부 스캔한다 - db-core 로만 좁히면 다른 패키지의 새 @Entity 를 놓친다.
        assertThat(OsivOffPremiseChecks.ENTITY_SCAN_PACKAGE)
            .isEqualTo(com.devquest.DevQuestApplication::class.java.packageName)
    }

    @Test
    fun `core 코드가 db-core 의 Entity 와 Repository 를 참조하면 잡는다`() {
        val sources = mapOf(
            "core/X.kt" to "import com.devquest.storage.db.core.UserResumeEntity",
            "core/Y.kt" to "import com.devquest.storage.db.core.CodingProblemRepository",
            "core/Z.kt" to "import com.devquest.storage.db.core.*",
            "core/W.kt" to "val r: com.devquest.storage.db.core.QuestProgressEntity? = null",
        )

        val violations = OsivOffPremiseChecks.persistenceLeakViolations(sources)

        assertThat(violations).hasSize(4)
    }

    @Test
    fun `db-core 설정 클래스 임포트와 주석 속 언급은 위반이 아니다`() {
        val sources = mapOf(
            "core/M.kt" to "import com.devquest.storage.db.core.config.CoreDataSourceConfig",
            "core/N.kt" to "// com.devquest.storage.db.core.UserResumeEntity 는 어댑터 안에서만 쓴다\nval a = 1",
            "core/O.kt" to "@EntityScan(\"com.devquest.storage.db.core\")\nclass App",
        )

        assertThat(OsivOffPremiseChecks.persistenceLeakViolations(sources)).isEmpty()
    }
}
