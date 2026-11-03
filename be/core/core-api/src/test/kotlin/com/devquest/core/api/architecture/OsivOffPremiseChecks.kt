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

    /** KDoc/주석에 적힌 이름(예: "getReferenceById 는 쓰지 않는다")이 위반으로 오탐되지 않게 주석을 지운다. */
    fun stripComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex("""(?m)(^|\s)//.*$"""), " ")

    fun dbCorePackage(): String = DB_CORE_PACKAGE
}
