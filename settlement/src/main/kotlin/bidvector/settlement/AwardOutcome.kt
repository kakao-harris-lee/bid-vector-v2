package bidvector.settlement

/** 운영자 상호를 판정 근거로 쓸 수 없는 사유. */
sealed interface OperatorNameAbsentReason {
    /** 운영자가 상호를 설정하지 않았다. */
    data object NotDeclared : OperatorNameAbsentReason

    /** 설정은 됐으나 정규화 뒤 남는 글자가 없다(예: 법인 표기 토큰만). */
    data object NormalizesToEmpty : OperatorNameAbsentReason
}

/** 결과 쪽 상호를 판정 근거로 쓸 수 없는 사유. */
sealed interface WinnerNameAbsentReason {
    /** 낙찰자 상호가 공개되지 않았다 — 「낙찰자가 아니다」가 아니다. */
    data object NotPublished : WinnerNameAbsentReason

    /** 공개는 됐으나 정규화 뒤 남는 글자가 없다. */
    data object NormalizesToEmpty : WinnerNameAbsentReason
}

/**
 * 실투찰 개찰 대사의 결말 — **4-state 다. `Boolean` 으로 접지 않는다**(SET-01 acceptance:
 * *"낙찰가만 있고 낙찰자 상호가 없으면 판정이 `false` 가 아니라 미기록(3-state)"*).
 *
 * [SettledAwardOutcome] 이 중간 층으로 있는 이유는 **「확정」을 주석이 아니라 타입으로** 두기
 * 위해서다 — [recordAwardOutcome] 의 기록 대상이 그 타입이라, 미기록·판정 안 함이 기록으로
 * 승격되는 경로가 컴파일 수준에서 없다.
 *
 * `isWon(): Boolean`·`orElse` 류의 **접는 API 를 두지 않는다**(`LicenseVerdict` 규율) —
 * 소비자는 소진 `when` 만 쓴다.
 */
sealed interface AwardOutcome {
    /** 결과 쪽 상호가 없다 — 미기록. 패찰이 아니고 「아직 알 수 없다」다. */
    data class NotRecorded(
        val reason: WinnerNameAbsentReason,
    ) : AwardOutcome

    /** 운영자 상호가 없다 — 판정 자체를 하지 않는다(외부 조회도 하지 않는다). */
    data class NotJudged(
        val reason: OperatorNameAbsentReason,
    ) : AwardOutcome
}

/**
 * 확정 판정 둘 — 상호 정규화 **정확매치**만으로 갈린다. 금액은 어느 쪽에도 실리지 않는다:
 * 같은 개찰에 같은 투찰가를 낸 서로 다른 업체가 실존하는 도메인이라(legacy
 * `award_verification.py:127-128` 의 사유) 금액 근접은 낙찰의 근거가 될 수 없다.
 */
sealed interface SettledAwardOutcome : AwardOutcome {
    /** 정규화된 운영자 상호 == 정규화된 낙찰자 상호. */
    data object Won : SettledAwardOutcome

    /** 낙찰자 상호가 확정됐고 운영자 상호와 다르다. */
    data object Lost : SettledAwardOutcome
}

/**
 * 외부 조회·판정의 **관문**. [Lookup] 만이 정규화된 운영자 상호를 나르므로, 운영자 상호가
 * 미설정인 상태에서는 조회 호출에 넘길 값 자체가 존재하지 않는다 — 「미설정이면 외부 조회도
 * 하지 않는다」가 호출부의 성실성이 아니라 타입으로 선다.
 *
 * [judgeAwardOutcome] 의 첫 인자 타입이 이 타입이라 **판정에 닿으려면 이 술어를 지나야 한다.**
 */
sealed interface AwardLookupDecision {
    data class Lookup(
        val operator: NormalizedCompanyName,
    ) : AwardLookupDecision

    data class Skip(
        val reason: OperatorNameAbsentReason,
    ) : AwardLookupDecision
}

/** 이미 기록된 확정 판정의 유무. `null` 로 접지 않는다. */
sealed interface RecordedAwardOutcome {
    data class Settled(
        val outcome: SettledAwardOutcome,
    ) : RecordedAwardOutcome

    data object Absent : RecordedAwardOutcome
}

/**
 * 기록 시도의 결말 — [AlreadySettled] 는 **기존 값을 그대로 돌려준다**. 새 값을 나르는 자리가
 * 없으므로 호출부가 덮어쓸 값을 얻지 못한다(legacy 의 멱등 조건
 * `record.award_outcome is None`, `award_notifications.py:186-191`).
 */
sealed interface AwardOutcomeRecording {
    data class Recorded(
        val outcome: SettledAwardOutcome,
    ) : AwardOutcomeRecording

    data class AlreadySettled(
        val kept: SettledAwardOutcome,
    ) : AwardOutcomeRecording

    /** 확정이 아닌 결말은 기록하지 않는다 — 미기록·판정 안 함은 다음 관측에서 다시 판정된다. */
    data class NotSettled(
        val outcome: AwardOutcome,
    ) : AwardOutcomeRecording
}
