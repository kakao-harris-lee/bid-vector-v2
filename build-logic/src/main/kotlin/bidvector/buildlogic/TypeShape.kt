package bidvector.buildlogic

import com.tngtech.archunit.core.domain.JavaClass
import java.io.File

/** 상속 깊이·구현 인터페이스 수 하나의 스냅샷. 정책 비교는 이 값들만으로 이뤄진다(순수 함수). */
internal data class TypeShape(
    val name: String,
    val inheritanceDepth: Int,
    val interfaceCount: Int,
)

/**
 * 상속 깊이·구현 인터페이스 수는 **바이트코드**에서 잰다 — PSI로는 상위 타입 해석이 안 된다
 * (컴파일된 클래스 정보 없이는 `Base`가 무엇을 상속하는지 소스만 보고 알 수 없다).
 *
 * `QualityBaselineTask`(측정)와 `TypeShapeGateTask`(래칫)가 **같은 함수**로 잰다 — 중복 금지
 * (v2-지침서.md §5). 두 task 가 각자 계수식을 새로 쓰면 baseline 이 보고하는 최댓값과 게이트가
 * 실제로 잡는 임계가 어긋날 수 있다.
 */
internal fun JavaClass.inheritanceDepth(): Int = generateSequence(this) { it.rawSuperclass.orElse(null) }.count() - 1

internal fun JavaClass.interfaceCount(): Int = rawInterfaces.size

internal fun JavaClass.toTypeShape(): TypeShape = TypeShape(name, inheritanceDepth(), interfaceCount())

/**
 * 래칫의 의미(D-3, scope.md) — **증가 금지**. baseline 값(현재 최대)을 그대로 상한으로 두고
 * 넘는 타입이 있으면 실패한다. 순수 함수라 Gradle·바이트코드 없이 테스트가 붙는다 —
 * [TypeShape] 값만 있으면 된다.
 */
internal class TypeShapeRatchetPolicy(
    private val values: Map<String, String>,
) {
    val maxInheritanceDepth: Int get() = values.requireInt("ratchet.type.inheritance-depth.max")
    val maxInterfaces: Int get() = values.requireInt("ratchet.type.interfaces.max")

    fun violations(shapes: List<TypeShape>): List<String> =
        shapes
            .filter { it.inheritanceDepth > maxInheritanceDepth }
            .map { "상속 깊이 ${it.inheritanceDepth} > $maxInheritanceDepth — ${it.name}" } +
            shapes
                .filter { it.interfaceCount > maxInterfaces }
                .map { "구현 인터페이스 ${it.interfaceCount} > $maxInterfaces — ${it.name}" }

    companion object {
        fun load(file: File): TypeShapeRatchetPolicy = TypeShapeRatchetPolicy(readPolicy(file))
    }
}
