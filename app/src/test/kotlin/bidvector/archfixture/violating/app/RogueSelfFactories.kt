package bidvector.archfixture.violating.app

/**
 * M6/G1-C03 r3 위반 표본(CR M6 · verifier R2-1) — **타입 자신이 짓는 길** 셋. 생성 축 규칙이 타입 자신을 통째로 면제하면
 * 이 셋이 게이트 밖으로 나간다: 호출자는 메서드를 부를 뿐이고 `<init>` 은 타입(또는 그 동반 객체) 안에서 불린다.
 * production classpath 에는 오르지 않는다(test 소스).
 */
data class FixtureDataCandidate(
    val anchor: String = "declared-by-frame",
)

/** ① data class `copy` 로 다른 선언을 싣는다. */
class RogueCopyCaller {
    fun rebuild(candidate: FixtureDataCandidate): FixtureDataCandidate = candidate.copy(anchor = "not-carried")
}

class FixtureMemberFactoryCandidate(
    val anchor: String,
) {
    fun withAnchor(anchor: String): FixtureMemberFactoryCandidate = FixtureMemberFactoryCandidate(anchor)
}

/** ② 타입의 멤버 팩토리로 다시 짓는다. */
class RogueMemberFactoryCaller {
    fun rebuild(candidate: FixtureMemberFactoryCandidate): FixtureMemberFactoryCandidate =
        candidate.withAnchor("not-carried")
}

class FixtureCompanionCandidate(
    val anchor: String,
) {
    companion object {
        fun of(anchor: String): FixtureCompanionCandidate = FixtureCompanionCandidate(anchor)
    }
}

/** ③ 동반 객체 팩토리로 다시 짓는다. */
class RogueCompanionCaller {
    fun rebuild(): FixtureCompanionCandidate = FixtureCompanionCandidate.of("not-carried")
}

/** 비과잉 — 생성자 위임(기본 인자 합성 생성자 · 보조 생성자)만 있는 타입은 짓는 자리가 없다. */
class FixtureDelegatingCandidate(
    val anchor: String = "declared-by-frame",
) {
    constructor(code: Int) : this(code.toString())
}
