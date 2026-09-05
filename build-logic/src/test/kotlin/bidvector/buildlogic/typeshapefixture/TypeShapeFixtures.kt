package bidvector.buildlogic.typeshapefixture

/**
 * [TypeShapeFixtureTest]가 재는 실제 컴파일된 fixture — 이 파일은 production 이 아니라 이
 * 모듈의 test 소스라 어떤 게이트의 대상도 아니다(`sourceSetKotlinFiles`가 main만 본다).
 */
internal interface Greeter {
    fun greet(): String
}

internal interface Farewell {
    fun bye(): String
}

/** 상속 체인의 뿌리 — 사용자가 쓴 supertype 이 없다. */
internal open class ChainRoot

/** 체인 2단계. */
internal open class ChainMiddle : ChainRoot()

/** 체인 3단계 — 래칫이 잡아야 할 「깊이 증가」모양. */
internal class ChainLeaf : ChainMiddle()

/** 인터페이스 둘을 구현 — 래칫이 잡아야 할 「인터페이스 수 증가」모양. */
internal class TwoInterfaces :
    Greeter,
    Farewell {
    override fun greet() = "hi"

    override fun bye() = "bye"
}

/** preflight §3 — `enum class`는 `java.lang.Enum` 상속이 체인에 들어가 항상 depth 2. */
internal enum class Status {
    ACTIVE,
    INACTIVE,
}

/**
 * preflight §3 — `by` 위임이 상속 체인이 아니라 인터페이스 수에 잡히는지 미실측이었다.
 * 이 fixture 로 고정한다: 위임 대상 인터페이스가 `implements`로 바이트코드에 남는다.
 */
internal class DelegatingGreeter(
    delegate: Greeter,
) : Greeter by delegate

/**
 * D-7(verifier r1 M-2) — build-logic 자신의 실제 형태(`abstract class X : DefaultTask()`)를
 * 그대로 고정한다. `DefaultTask`는 이 fixture 컴파일 단위 밖(Gradle API)이라 import 집합에
 * 없다 — depth 는 0 이어야 한다.
 */
internal abstract class FakeGradleTask : org.gradle.api.DefaultTask()

/**
 * verifier r2 H-1 — **다른 모듈의 소유 타입**을 흉내낸다. `TypeShapeFixtureTest`가 일부러
 * 이 클래스 자체를 `ClassFileImporter.importClasses`에 넣지 않는다 — 「이번 스캔 집합 밖이지만
 * 루트 패키지(`bidvector.`) 아래」인 사례를 만들기 위해서다(예: `shared-kernel`의 `open class`를
 * `workflow`가 상속하는 실제 형태). `CrossModuleDerived`는 스캔 집합에 있고 상위가
 * `CrossModuleBase`다 — 소유 판정이 스캔 집합뿐이면 이 상속을 놓치고, 루트 패키지 접두까지
 * 보면 잡는다.
 */
internal open class CrossModuleBase

internal class CrossModuleDerived : CrossModuleBase()
