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
 * `award_verification.py:121-123` 의 사유) 금액 근접은 낙찰의 근거가 될 수 없다.
 *
 * **두 값의 생성자는 `internal` 이다** — 확정 판정을 만드는 길은 [judgeAwardOutcome] 하나다.
 * 모듈 밖에서 금액 비교 따위로 [Won] 을 직접 만들어 [recordAwardOutcome] 에 넣는 길이
 * 컴파일되지 않는다(`CompileFailureHarnessTest` fixture 6 · `Lost` 는 fixture 7 — 모듈 밖에서 만든 패찰이 먼저
 * 기록되면 뒤의 참 낙찰이 막힌다). 값은 상태가 없어 같은 종류끼리
 * 같다 — 동등성은 종류로만 선다.
 */
sealed interface SettledAwardOutcome : AwardOutcome {
    /** 정규화된 운영자 상호 == 정규화된 낙찰자 상호. */
    class Won internal constructor() : SettledAwardOutcome {
        override fun equals(other: Any?): Boolean = other is Won

        override fun hashCode(): Int = WON_HASH

        override fun toString(): String = "Won"
    }

    /** 낙찰자 상호가 확정됐고 운영자 상호와 다르다. */
    class Lost internal constructor() : SettledAwardOutcome {
        override fun equals(other: Any?): Boolean = other is Lost

        override fun hashCode(): Int = LOST_HASH

        override fun toString(): String = "Lost"
    }
}

private const val WON_HASH = 1

private const val LOST_HASH = 2

/**
 * 외부 조회·판정의 **관문**. [Lookup] 만이 정규화된 운영자 상호를 나르므로, 운영자 상호가
 * 미설정인 상태에서는 조회 호출에 넘길 값 자체가 존재하지 않는다.
 *
 * **[Lookup] 의 생성자는 `internal` 이고 `copy()` 도 같은 가시성이다** — 유일한 생성 경로는
 * [decideAwardLookup] 이다. 모듈 밖에서 `normalizeCompanyName` 으로 아무 문자열을 정규화해
 * [Lookup] 을 직접 만들거나 `copy` 로 운영자 상호를 바꿔치우는 길이 컴파일되지 않는다
 * (`CompileFailureHarnessTest` fixture 4·5). 그래서 「판정에 닿으려면 이 술어를 지나야 한다」가
 * 타입으로 선다.
 *
 * [Lookup] 은 운영자 상호를 정규화한 **정책도 함께 나른다** — [judgeAwardOutcome] 은 결과 쪽
 * 상호를 그 정책으로 정규화하므로, 서로 다른 정책으로 접힌 두 상호가 한 판정에서 비교될 길이
 * 없다.
 */
sealed interface AwardLookupDecision {
    @ConsistentCopyVisibility
    data class Lookup internal constructor(
        val operator: NormalizedCompanyName,
        val policy: AwardMatchingPolicyData,
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
 *
 * 기록과 새 판정이 어긋나도(기록 `Won`, 오늘 판정 `Lost`) 이 결말은 그 사실을 따로 알리지
 * 않는다. 새 판정은 호출부가 넘긴 값이라 호출부가 이미 쥐고 있고, [AlreadySettled.kept] 와
 * 비교하면 어긋남이 드러난다 — 어긋남을 세고 경보를 낼지는 영속화 소비 slice 몫이다.
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
