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
 *
 * **상속 깊이의 정의(D-7, 2026-09-05 정제 · verifier r2 H-1, 2026-09-06 확장) — 프로젝트가
 * 소유한 타입 안에서의 상위 체인 길이.** 「프로젝트가 소유한」= **이번 스캔이 찾은 타입**
 * (`ownedTypeNames`, 같은 배선이 컴파일한 산출물) **이거나 루트 패키지(`rootPackagePrefix`,
 * 예: `bidvector.`) 아래**다. 상위 클래스를 따라 올라가다 둘 다 아닌 클래스를 만나면 그 자리에서
 * 멈춘다 — `java.lang.Object`·`java.lang.Enum`·Gradle `DefaultTask`·Spring 기반 클래스처럼
 * 우리가 늘리거나 줄일 수 없는 프레임워크 기저 깊이는 세지 않는다. ADR 0007 §1.3 의 대상은
 * **우리 코드의 합성 팽창**이지 프레임워크가 강제하는 최소 상속이 아니다.
 *
 * **왜 스캔 집합만으로는 부족한가(verifier r2 H-1)** — `ownedTypeNames`는 **한 배선(보통 한
 * 모듈)이 스캔한 집합**이다. 상위 클래스가 **다른 모듈**(예: `shared-kernel`의 `open class A`를
 * `workflow`의 `class B : A()`가 상속)에 있으면 소유 코드인데도 그 스캔 밖이라 계수가 즉시
 * 멈춰 모듈 경계를 하나 넘을 때마다 깊이가 1씩 줄었다(실측: 2모듈짜리 상속 한 단계가 아예
 * 게이트에 안 걸림). 루트 패키지 접두 판정이 그 사각을 닫는다 — 프레임워크 클래스는
 * `bidvector.` 가 아니므로 D-7 의 원 목적(프레임워크 기저 깊이 제외)은 그대로 유지된다.
 * 접두 문자열은 하드코딩하지 않는다 — `architecture-policy.properties` 의 `package.root`
 * (ADR 0006 D-3)를 호출부가 읽어 넘긴다.
 *
 * verifier r1 M-2 실측 — build-logic 자신의 `abstract class X : DefaultTask()` 형태 17개가
 * 이전 정의(외부 포함)로는 depth 3(`DefaultTask`가 이미 2단 상속이라 항상 최소 3)이었는데,
 * 이 정의로는 `DefaultTask`가 소유 집합 밖(스캔 밖이자 `bidvector.` 도 아님)이라 즉시 멈춰
 * depth 0이다.
 */
internal fun JavaClass.inheritanceDepth(
    ownedTypeNames: Set<String>,
    rootPackagePrefix: String,
): Int {
    val prefix = "$rootPackagePrefix."
    return generateSequence(this) { it.rawSuperclass.orElse(null) }
        .drop(1)
        .takeWhile { it.name in ownedTypeNames || it.name.startsWith(prefix) }
        .count()
}

internal fun JavaClass.interfaceCount(): Int = rawInterfaces.size

internal fun JavaClass.toTypeShape(
    ownedTypeNames: Set<String>,
    rootPackagePrefix: String,
): TypeShape = TypeShape(name, inheritanceDepth(ownedTypeNames, rootPackagePrefix), interfaceCount())

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
