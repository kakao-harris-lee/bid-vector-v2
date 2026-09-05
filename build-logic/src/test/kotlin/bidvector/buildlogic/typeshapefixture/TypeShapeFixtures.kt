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
