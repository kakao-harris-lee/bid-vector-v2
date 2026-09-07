package bidvector.sharedkernel

/**
 * 금액이 어디서 왔는가 — [Money]의 다섯째 성분. `data-dictionary.md` §5.1 `FactProvenance`의
 * 승인 명세 표기다(운영자 결정 2026-09-04 — A2와 같은 갈래로 승인 명세 이름을 채택한다).
 */
sealed interface Provenance {
    /**
     * `noticeRevision`은 [NoticeRound]다(M3/3A D-3A-0 (a), 운영자 결정 2026-09-07) — 표적조회
     * 필수 입력이라 `Int`로 나르면 1차 공고(`"000"`) 전부의 자격을 잃는 회귀를 재현한다
     * (R-QUAL-05).
     */
    data class Published(
        val noticeRevision: NoticeRound,
    ) : Provenance

    data object DerivedFromOpening : Provenance

    data class FilledFromBudgetKey(
        val key: String,
    ) : Provenance

    data object CopiedFromBaseAmount : Provenance

    data object OperatorDeclared : Provenance

    data object Undeclared : Provenance
}

/**
 * 기초금액 provenance 판정 라벨 — 승인 명세(`v2-지침서.md` §4.3)의 다섯 값 그대로다
 * (운영자 결정 2026-09-04, B10 — 여섯째 variant를 더하지 않는다). first-match rule 자체는
 * 1D 소유이고, 1B는 이 라벨 타입만 선언한다.
 */
sealed interface BaseAmountProvenance {
    data object Clean : BaseAmountProvenance

    data object DerivedYega : BaseAmountProvenance

    data object DerivedVat : BaseAmountProvenance

    data object SuspectRatio : BaseAmountProvenance

    data object Unknown : BaseAmountProvenance
}
