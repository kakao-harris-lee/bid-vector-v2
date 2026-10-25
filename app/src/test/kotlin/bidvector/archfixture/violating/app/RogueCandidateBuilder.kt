package bidvector.archfixture.violating.app

/**
 * M6/G1-C03 위반 표본(V2) — 표본틀 밖에서 후보를 **다른 선언으로 다시 짓는다**. 실제 후보 타입은 workflow 의 `internal`
 * 이라 이 모듈에서 지을 수 없으므로 같은 모양의 표본 타입으로 규칙의 생성 축을 잰다. production classpath 에는 오르지
 * 않는다(test 소스).
 */
class FixtureOpeningCandidate(
    val anchor: String,
)

/** 허용된 생성 자리(표본틀의 자리) — 규칙이 이것까지 막으면 과잉이다. */
class FixtureCandidateFramer {
    fun frame(): FixtureOpeningCandidate = FixtureOpeningCandidate("declared-by-frame")
}

/** 생성자 호출로 다시 짓는다. */
class RogueCandidateBuilder {
    fun rebuild(): FixtureOpeningCandidate = FixtureOpeningCandidate("not-carried")
}

/** 생성자 참조(`::T`)로 다시 짓는다 — 호출이 아니어도 짓는 자리다. */
class RogueCandidateReference {
    val build: (String) -> FixtureOpeningCandidate = ::FixtureOpeningCandidate
}
