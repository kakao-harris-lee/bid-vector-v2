package bidvector.decision

/**
 * 투찰 판정(scope.md ①, `data-dictionary.md` §3.6, `OPEN-DEC-05` 해소) —
 * `BidNow(reasons)` · `Review(reasons)` · `Skip(reason)`. 사유는 구조화 코드이고
 * 문장이 아니다(§3.1 「사람이 읽는 문장은 렌더링 시점에 생성하고 영속하지 않는다」).
 *
 * 세 하위 타입 생성자는 전부 `internal` + `@ConsistentCopyVisibility`(설계 검토 (2),
 * 1D `FloorShortfall.Measured`·4A `AppliedStrategy` 관례) — 유일한 생성 경로는
 * [VerdictLadder.judge]다. **「`Verdict`를 얻으면 무엇을 할 수 있는가」**: 판정 결과는
 * 읽기 값이고 부작용 권한을 주지 않는다(설계 검토 (2) — `decision`은 도메인 모듈이라
 * `app` conformance 실행자가 이 타입을 직접 소비하는 것이 1C·1D 관례이며 그 자체는
 * 위협이 아니다). 위협은 **사다리를 지나지 않고 이 타입을 지어내는 것**뿐이고 그것을
 * 생성자 폐쇄가 막는다.
 */
sealed interface Verdict {
    @ConsistentCopyVisibility
    data class BidNow internal constructor(
        val reasons: List<BidNowReason>,
    ) : Verdict

    @ConsistentCopyVisibility
    data class Review internal constructor(
        val reasons: List<ReviewReason>,
    ) : Verdict

    @ConsistentCopyVisibility
    data class Skip internal constructor(
        val reason: SkipReason,
    ) : Verdict
}
