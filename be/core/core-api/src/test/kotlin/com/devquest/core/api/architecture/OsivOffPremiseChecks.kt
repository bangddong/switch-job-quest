package com.devquest.core.api.architecture

import jakarta.persistence.Basic
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * OSIV OFF(B7-21) 전제를 CI 가 지키게 하는 검사 함수 모음. 검사 로직만 담고 "무엇을 스캔할지"는 호출자가 정한다.
 * 그래서 [OsivOffPremiseChecksTest] 가 위반 샘플을 먹여 **검사기가 실제로 잡는지** 영구히 확인한다
 * (이 레포는 "검사기가 거짓 통과" 사고를 겪었다 - 빈 입력·잘못된 경로에서 조용히 초록이 되는 형태).
 *
 * 왜 이 전제가 필요한가: OSIV 를 끄면 컨트롤러·뷰 계층에서는 영속성 컨텍스트가 이미 닫혀 있다. 지연 로딩 프록시를 트랜잭션 밖에서
 * 건드리면 `LazyInitializationException` 이다. 지금은 (1) 엔티티에 연관관계/지연 필드가 없고 (2) 엔티티가 어댑터 밖으로 나가지 않아서
 * 안전하다 - 이 둘이 깨지면 OSIV OFF 가 운영에서 터진다.
 */
object OsivOffPremiseChecks {

    const val GUIDANCE =
        "OSIV OFF(B7-21) 전제 — 지연 로딩을 도입하려면 트랜잭션 경계/fetch join 을 먼저 설계하라 " +
            "(엔티티를 어댑터 밖으로 내보내거나 연관관계를 추가하기 전에, 서비스 @Transactional 범위와 조회 쿼리를 정하고 " +
            "이 가드를 그 설계에 맞게 고쳐라)."

    private const val DB_CORE_PACKAGE = "com.devquest.storage.db.core"

    /**
     * 엔티티 스캔 범위 - 앱의 실제 범위와 같아야 한다. core-api 의 `DevQuestApplication` 은 `@EntityScan` 이 없어서 Boot 기본값
     * (= `@SpringBootApplication` 이 속한 패키지 `com.devquest` 전체)을 쓴다. db-core 패키지로만 좁히면 다른 패키지에 새로 생긴
     * `@Entity` 가 이 가드를 피한다. (daily-api 는 `@EntityScan("com.devquest.storage.db.core")` 로 더 좁게 쓰지만 core-api 의 부분집합이다.)
     */
    const val ENTITY_SCAN_PACKAGE = "com.devquest"

    private val LAZY_CAPABLE_ASSOCIATIONS: List<Class<out Annotation>> = listOf(
        OneToMany::class.java,
        ManyToOne::class.java,
        OneToOne::class.java,
        ManyToMany::class.java,
        ElementCollection::class.java,
    )

    /** 영속성 클래스 판정: JPA 어노테이션이 붙었거나 이름이 `Entity` 로 끝난다(어노테이션 누락에도 걸리게). */
    fun isPersistenceType(type: Class<*>): Boolean =
        type.isAnnotationPresent(Entity::class.java) ||
            type.isAnnotationPresent(MappedSuperclass::class.java) ||
            type.isAnnotationPresent(Embeddable::class.java) ||
            type.simpleName.endsWith("Entity")

    /** 엔티티/매핑 슈퍼클래스/임베더블에 연관관계·컬렉션·지연 `@Basic` 이 하나라도 있으면 위반. */
    fun lazyAssociationViolations(persistenceClasses: Collection<Class<*>>): List<String> {
        val violations = mutableListOf<String>()
        for (clazz in persistenceClasses) {
            val members: List<AnnotatedElement> = clazz.declaredFields.toList() + clazz.declaredMethods.toList()
            for (member in members) {
                val name = (member as? java.lang.reflect.Member)?.name ?: member.toString()
                for (annotation in LAZY_CAPABLE_ASSOCIATIONS) {
                    if (member.isAnnotationPresent(annotation)) {
                        violations += "${clazz.name}.$name 에 @${annotation.simpleName}"
                    }
                }
                val basic = member.getAnnotation(Basic::class.java)
                if (basic != null && basic.fetch == FetchType.LAZY) {
                    violations += "${clazz.name}.$name 에 @Basic(fetch = LAZY)"
                }
            }
        }
        return violations
    }

    /** 어댑터의 public 메서드 반환형(제네릭 인자 포함)에 영속성 클래스가 있으면 위반. */
    fun entityLeakingReturnTypeViolations(adapterClasses: Collection<Class<*>>): List<String> {
        val violations = mutableListOf<String>()
        for (clazz in adapterClasses) {
            for (method in clazz.declaredMethods) {
                if (!Modifier.isPublic(method.modifiers) || method.isSynthetic) continue
                val leaked = referencedClasses(method.genericReturnType).filter { isPersistenceType(it) }
                for (type in leaked) {
                    violations += "${clazz.name}.${method.name}() 가 ${type.name} 을 반환형에 노출"
                }
            }
        }
        return violations
    }

    private fun referencedClasses(type: Type): Set<Class<*>> = when (type) {
        is Class<*> -> if (type.isArray) referencedClasses(type.componentType) else setOf(type)
        is ParameterizedType -> referencedClasses(type.rawType) + type.actualTypeArguments.flatMap { referencedClasses(it) }
        is GenericArrayType -> referencedClasses(type.genericComponentType)
        is WildcardType -> (type.upperBounds + type.lowerBounds).flatMap { referencedClasses(it) }.toSet()
        else -> emptySet()
    }

    /**
     * 🔴 `getOne`·`getById` 는 이름이 넓어서 도메인 서비스의 같은 이름 메서드도 잡는다(오탐). 그대로 둔다: 오탐은 CI 가 시끄럽게 실패해서
     * 바로 드러나지만, 수신자 이름(`*Repository`)으로 좁히면 `val problems: CodingProblemRepository` 처럼 다른 이름의 변수로 호출한 진짜
     * 지연 프록시 호출이 조용히 빠진다(미탐). `JpaRepository` 에는 `getOne`·`getById` 가 deprecated 로 아직 남아 있다(Spring Data JPA 4.0.3 실측).
     * 오탐이 실제로 나면 그 호출의 이름을 바꾸거나 이 정규식을 타입 정보 기반으로 고친다.
     */
    private val LAZY_LOADING_API = Regex("""\.(getReferenceById|getReference|getOne|getById)\s*\(""")

    /** 지연 프록시를 만드는 API(`getReferenceById` 등) 호출이 main 소스에 있으면 위반. [sources] = 경로 -> 소스 텍스트. */
    fun lazyLoadingApiViolations(sources: Map<String, String>): List<String> =
        sources.flatMap { (path, text) ->
            LAZY_LOADING_API.findAll(stripComments(text)).map { "$path 에서 ${it.value.trim()} 호출" }.toList()
        }

    private val PERSISTENCE_REFERENCE = Regex("""com\.devquest\.storage\.db\.core\.(\w*Entity|\w*Repository|\*)(?![\w])""")

    /** db-core 밖의 main 소스가 db-core 의 `*Entity`·`*Repository`(또는 와일드카드 import)를 직접 참조하면 위반. */
    fun persistenceLeakViolations(sources: Map<String, String>): List<String> =
        sources.flatMap { (path, text) ->
            PERSISTENCE_REFERENCE.findAll(stripComments(text)).map { "$path 가 ${it.value} 를 직접 참조" }.toList()
        }

    /**
     * KDoc/주석에 적힌 이름(예: "getReferenceById 는 쓰지 않는다")이 위반으로 오탐되지 않게 주석을 지운다.
     *
     * 🔴 정규식이 아니라 **문자열 리터럴을 아는 스캐너**다. 문자열 속 슬래시-별표(예: ant 패턴 문자열 `/api/` 뒤에 별표 둘)나 슬래시 둘(예: `"a // b"`)을 주석 시작으로 오인하면
     * 다음 블록 주석 끝·줄 끝까지의 진짜 코드(`getReferenceById` 호출)가 통째로 지워져 위반을 놓친다(미탐). 그래서
     * - `"..."`(이스케이프 포함)·`"""..."""`·`'x'` 는 **그대로 둔다**(문자열 속 `${...}` 템플릿은 코드라서 다시 스캔한다)
     * - 코드 위치의 `//` 줄 주석과 블록 주석(Kotlin 은 중첩된다)만 공백으로 바꾼다
     */
    fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        CommentStripper(text, out).scanCode(untilClosingBrace = false)
        return out.toString()
    }

    private class CommentStripper(private val text: String, private val out: StringBuilder) {
        private var i = 0

        private fun startsWith(token: String) = text.startsWith(token, i)

        /** 코드 영역을 [text] 끝(또는 `${` 템플릿이면 짝이 맞는 `}`)까지 스캔한다. */
        fun scanCode(untilClosingBrace: Boolean) {
            var depth = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    startsWith("//") -> skipLineComment()
                    startsWith("/*") -> skipBlockComment()
                    startsWith("\"\"\"") -> copyRawString()
                    c == '"' -> copyString()
                    c == '\'' -> copyCharLiteral()
                    c == '{' -> { depth++; out.append(c); i++ }
                    c == '}' -> {
                        if (untilClosingBrace && depth == 0) return
                        depth--
                        out.append(c); i++
                    }
                    else -> { out.append(c); i++ }
                }
            }
        }

        private fun skipLineComment() {
            while (i < text.length && text[i] != '\n') i++
            out.append(' ')
        }

        private fun skipBlockComment() {
            var depth = 0
            while (i < text.length) {
                if (startsWith("/*")) { depth++; i += 2 }
                else if (startsWith("*/")) { depth--; i += 2; if (depth == 0) break }
                else i++
            }
            out.append(' ')
        }

        private fun copyRawString() {
            out.append("\"\"\""); i += 3
            while (i < text.length) {
                when {
                    startsWith("\${") -> copyTemplate()
                    startsWith("\"\"\"") -> {
                        // 닫는 따옴표 앞에 따옴표가 더 붙어 있으면 마지막 세 개가 닫는 따옴표다(`""""` 같은 경우).
                        while (startsWith("\"\"\"\"")) { out.append('"'); i++ }
                        out.append("\"\"\""); i += 3
                        return
                    }
                    else -> { out.append(text[i]); i++ }
                }
            }
        }

        private fun copyString() {
            out.append('"'); i++
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '\\' -> { out.append(c); i++; if (i < text.length) { out.append(text[i]); i++ } }
                    startsWith("\${") -> copyTemplate()
                    c == '"' -> { out.append(c); i++; return }
                    else -> { out.append(c); i++ }
                }
            }
        }

        /** `${ ... }` - 안은 코드다(중첩 문자열·주석 포함). */
        private fun copyTemplate() {
            out.append("\${"); i += 2
            scanCode(untilClosingBrace = true)
            if (i < text.length) { out.append('}'); i++ }
        }

        private fun copyCharLiteral() {
            // `'x'`·`'\n'`·`'\u0041'`·`'"'`. 모양이 아니면 따옴표 하나만 그대로 복사한다(주석 밖 코드에서 `'` 단독은 드물다).
            val end = if (i + 1 < text.length && text[i + 1] == '\\') text.indexOf('\'', i + 2) else if (i + 2 < text.length && text[i + 2] == '\'') i + 2 else -1
            if (end == -1 || end - i > 8) { out.append('\''); i++; return }
            out.append(text, i, end + 1)
            i = end + 1
        }
    }

    fun dbCorePackage(): String = DB_CORE_PACKAGE
}
