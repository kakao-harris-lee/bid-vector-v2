package bidvector.procurement

import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import java.time.Instant

/**
 * `canonicalize`가 원문에서 실제로 낸 canonical 값(②) — 원문(`raw`)은 감사를 위해 그대로
 * 함께 나른다(버리지 않는다). 판정 결과(`BaseAmountProvenance` 라벨)는 여기 없다 — 3A는
 * `Provenance` 해석까지만 낸다(⑤ 경계).
 */
data class NoticeCollected(
    val id: NoticeId,
    val businessCategory: BusinessCategory?,
    val baseAmount: ResolvedBaseAmount?,
    val estimatedAmount: ResolvedEstimatedAmount?,
    val allocatedBudget: AllocatedBudget?,
    val floorRate: FloorRate?,
    val deadlineAt: Instant?,
    val openingScheduledAt: Instant?,
    val raw: RawNoticeObservation,
    /**
     * 수요기관(D-3H-3, M3/3H-1) — 기본값 `null`이라 이 slice 밖 호출부(workflow·ml 어댑터
     * test 등)는 수정 없이 그대로 컴파일된다(scope.md 우회 (6) — `agencyId = null` 두 자리
     * 불변).
     */
    val demandAgency: Agency? = null,
    /** 공고기관(D-3H-3, M3/3H-1) — [demandAgency]와 다른 축, 기본값 `null`(위와 같은 이유). */
    val noticeAgency: Agency? = null,
    /**
     * 공고명(D-6F4-9, M6/6F-4) — 기본값 `null`(위 발주기관 둘과 같은 이유, 이 slice 밖
     * 호출부는 수정 없이 그대로 컴파일된다). 값은 `canonicalize` 가 `NOTICE_TITLE` 필드
     * 계약에서 채운다(D-6F8-2, M6/6F-8).
     */
    val title: NoticeTitle? = null,
    /**
     * 업무 대분류(D-6F9-1, M6/6F-9) — 응답 필드가 아니라 관측이 나르는 **수집 오퍼레이션 값**을 그대로 옮긴다
     * ([RawNoticeObservation.sourceDivision]). 응답의 `bsnsDivNm` 은 소비하지 않는다(P-7 — 축을 접지 않는다).
     * 기본값 `null`(위 슬롯들과 같은 이유).
     */
    val businessDivision: BusinessDivision? = null,
    /** 용역구분(D-6F9-2, `SERVICE_DIVISION` 계약) — [businessCategory]의 라벨 칸에 섞지 않는 자기 칸. */
    val serviceDivision: ServiceDivision? = null,
    /** 주공종(D-6F9-2, `MAIN_CONSTRUCTION_TYPE` 계약) — 이름만이다. [businessCategory]의 코드를 낳지 않는다. */
    val mainConstructionType: MainConstructionType? = null,
)

/** 추정가격도 기초금액과 같은 형태 규율(파생이 원본을 덮지 않는다)을 받는다 — `Published`만 직접값이다. */
data class ResolvedEstimatedAmount(
    val sourceKey: RawKey,
    val amount: EstimatedAmount,
)

/** 항목 하나의 canonicalize 결과 — 성공(정규화됨) 또는 탈락(사유 있음), 미지 필드 수는 둘 다 싣는다. */
sealed interface CanonicalizationOutcome {
    val unknownFieldCount: Int

    data class Normalized(
        val command: NoticeCollected,
        override val unknownFieldCount: Int,
    ) : CanonicalizationOutcome

    data class Dropped(
        val reason: CollectionDropReason,
        override val unknownFieldCount: Int,
    ) : CanonicalizationOutcome
}

private fun currencyFor(unit: FieldUnit): Currency =
    when (unit) {
        FieldUnit.WON -> Currency.KRW
        else -> error("금액 축 계약의 unit은 WON이어야 한다: $unit")
    }

/** 식별자를 세우는 단계의 결과 — 원문 값 유래 실패는 예외가 아니라 탈락 사유다(D-6F8-7). */
private sealed interface IdentityOutcome {
    data class Resolved(
        val id: NoticeId,
    ) : IdentityOutcome

    data class Unresolvable(
        val reason: CollectionDropReason,
    ) : IdentityOutcome
}

/**
 * 공백뿐인 번호·차수는 없는 것으로 본다(어댑터 `mapRawItem` 과 같은 판단). 비어 있지 않은데 차수가 형식
 * (`NoticeRound`: 제로패딩 세 자리)을 어기면 [ParseFailureKind.IDENTIFIER] 탈락이다 — 형식 규칙의 정본은
 * `NoticeRound` 이고 여기서 다시 쓰지 않는다. 그 값 객체는 위반을 `IllegalArgumentException` 으로만 알리므로
 * 이 자리에서 결과로 접는다. 번호는 공백만 아니면 `NoticeNumber.of` 가 던지지 않는다.
 */
private fun resolvedNoticeId(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
): IdentityOutcome {
    val numberRaw = registry.valueIn(observation, FieldConcept.NOTICE_NUMBER)?.takeUnless(String::isBlank)
    val roundRaw = registry.valueIn(observation, FieldConcept.NOTICE_ROUND)?.takeUnless(String::isBlank)
    if (numberRaw == null || roundRaw == null) {
        return IdentityOutcome.Unresolvable(CollectionDropReason.CollectionMissingNoticeNumber)
    }
    val round =
        try {
            NoticeRound.of(roundRaw)
        } catch (_: IllegalArgumentException) {
            null
        }
    return if (round == null) {
        IdentityOutcome.Unresolvable(CollectionDropReason.CollectionParseFailure(ParseFailureKind.IDENTIFIER))
    } else {
        IdentityOutcome.Resolved(NoticeId(NoticeNumber.of(numberRaw), round))
    }
}

/** 통화·과세는 계약에서 읽는다(verifier r1 F-2) — 리터럴로 짓지 않는다. */
private fun baseAmountAsResolved(outcome: AmountResolutionOutcome): ResolvedBaseAmount? {
    if (outcome !is AmountResolutionOutcome.Resolved) return null
    val currency = currencyFor(outcome.unit)
    return when (val provenance = outcome.provenance) {
        is Provenance.Published -> {
            ResolvedBaseAmount.Direct.of(outcome.won, currency, outcome.vatTreatment, provenance)
        }

        is Provenance.FilledFromBudgetKey -> {
            ResolvedBaseAmount.FallbackFromBudget(
                outcome.sourceKey,
                BaseAmount(outcome.won, currency, outcome.vatTreatment, provenance),
            )
        }

        else -> {
            null
        }
    }
}

private fun estimatedAmountAsResolved(outcome: AmountResolutionOutcome): ResolvedEstimatedAmount? {
    if (outcome !is AmountResolutionOutcome.Resolved) return null
    return ResolvedEstimatedAmount(
        outcome.sourceKey,
        EstimatedAmount(outcome.won, currencyFor(outcome.unit), outcome.vatTreatment, outcome.provenance),
    )
}

/**
 * 배정예산(F-5) — `FilledFromBudgetKey` 폴백과는 **다른 자리**다. 자기 개념(`ALLOCATED_BUDGET`)
 * 필드에 값이 있으면 그 자체로 게시값이라 `Provenance.Published`를 받는다(폴백에 쓰였는지
 * 여부와 무관 — 축이 다르다, §5.2).
 */
private fun allocatedBudgetFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    noticeRound: NoticeRound,
): AllocatedBudget? =
    registry
        .contractsFor(FieldConcept.ALLOCATED_BUDGET)
        .firstOrNull()
        ?.takeIf { it.scale == FieldScale.WON_INTEGER && !basisMismatch(it) }
        ?.let { contract ->
            observation
                .valueOf(contract)
                ?.replace(",", "")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { won -> AllocatedBudget(won, currencyFor(contract.unit), Provenance.Published(noticeRound)) }
        }

/**
 * 게시 낙찰하한율(F-5) — 원문 percent → canonical fraction 은 계약 `scale` 지시로만 연다(ADR 0002 D-4).
 * 음수(`Rate` 불변식)나 표현할 수 없는 지수(`BigDecimal.divide` 의 `ArithmeticException`)는 수치가 아닌 원문과 같이
 * 필드 부재로 접는다 — 항목을 살리고 던지지 않는다(D-6F8-7).
 */
private fun floorRateFrom(
    observation: RawNoticeObservation,
    registry: KonepsFieldContractRegistry,
    noticeRound: NoticeRound,
): FloorRate? =
    registry
        .contractsFor(FieldConcept.FLOOR_RATE)
        .firstOrNull()
        ?.takeIf { it.scale == FieldScale.PERCENT }
        ?.let(observation::valueOf)
        ?.toBigDecimalOrNull()
        ?.let { numeric ->
            try {
                FloorRate(Rate.ofPercent(numeric), FloorRateOrigin.NoticeValue(noticeRound))
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: ArithmeticException) {
                null
            }
        }

/**
 * 계약 값이 없거나 공백뿐이면 부재다(D-6F8-11) — KONEPS 는 옵션 일시를 키 부재·`null` 이 아니라 **빈 문자열**로 낼 때가
 * 많다(실수집 실측). 값이 있는데 정책의 어느 패턴으로도 해석되지 않을 때만 [instantFrom] 이
 * [InstantResolutionOutcome.ParseFailed] 를 낸다.
 */
private fun instantResolutionOf(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
    concept: FieldConcept,
): InstantResolutionOutcome =
    if (policy.fieldContracts.valueIn(observation, concept).isNullOrBlank()) {
        InstantResolutionOutcome.Absent
    } else {
        instantFrom(observation, policy, concept)
    }

/**
 * [InstantResolutionOutcome.Resolved]는 값으로, [InstantResolutionOutcome.Absent]는 `null`로
 * 접는다 — [InstantResolutionOutcome.ParseFailed]는 이 함수가 다루지 않는다(호출부가 먼저
 * 걸러야 한다, `normalizedCommand`의 `when` 참고).
 */
private fun instantOrNull(outcome: InstantResolutionOutcome): Instant? =
    when (outcome) {
        is InstantResolutionOutcome.Resolved -> outcome.instant
        InstantResolutionOutcome.Absent -> null
        InstantResolutionOutcome.ParseFailed -> null
    }

/**
 * 금액·일시 해석 중 하나라도 계약 위반이면 항목 전체가 탈락한다(④·⑦) — 먼저 걸린 사유 하나. 없으면 `null`(정규화 진행).
 * 해석 결과 네 개 전부에서 [AmountResolutionOutcome.Rejected]·[InstantResolutionOutcome.ParseFailed] 가 아닌 것만 통과한다.
 */
private fun dropReasonOf(
    baseAmount: AmountResolutionOutcome,
    estimated: AmountResolutionOutcome,
    deadline: InstantResolutionOutcome,
    opening: InstantResolutionOutcome,
): CollectionDropReason? =
    when {
        baseAmount is AmountResolutionOutcome.Rejected -> {
            baseAmount.reason
        }

        estimated is AmountResolutionOutcome.Rejected -> {
            estimated.reason
        }

        deadline is InstantResolutionOutcome.ParseFailed || opening is InstantResolutionOutcome.ParseFailed -> {
            CollectionDropReason.CollectionParseFailure(ParseFailureKind.DATE_TIME)
        }

        else -> {
            null
        }
    }

/** 식별자가 선 뒤의 나머지 canonicalize — 금액 해석·일시 해석 중 하나라도 계약 위반이면 항목 전체가 탈락한다(④·⑦). */
private fun normalizedCommand(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
    noticeId: NoticeId,
    unknownFieldCount: Int,
): CanonicalizationOutcome {
    val baseAmountResolution = resolveAmount(observation, noticeId.round, AmountAxis.BASE, policy)
    val estimatedResolution = resolveAmount(observation, noticeId.round, AmountAxis.ESTIMATED, policy)
    val deadlineResolution = instantResolutionOf(observation, policy, FieldConcept.DEADLINE_AT)
    val openingResolution = instantResolutionOf(observation, policy, FieldConcept.OPENING_SCHEDULED_AT)
    val dropReason = dropReasonOf(baseAmountResolution, estimatedResolution, deadlineResolution, openingResolution)
    if (dropReason != null) return CanonicalizationOutcome.Dropped(dropReason, unknownFieldCount)
    val contracts = policy.fieldContracts
    return CanonicalizationOutcome.Normalized(
        NoticeCollected(
            id = noticeId,
            businessCategory = businessCategoryFrom(observation, contracts),
            baseAmount = baseAmountAsResolved(baseAmountResolution),
            estimatedAmount = estimatedAmountAsResolved(estimatedResolution),
            allocatedBudget = allocatedBudgetFrom(observation, contracts, noticeId.round),
            floorRate = floorRateFrom(observation, contracts, noticeId.round),
            deadlineAt = instantOrNull(deadlineResolution),
            openingScheduledAt = instantOrNull(openingResolution),
            raw = observation,
            demandAgency = demandAgencyFrom(observation, contracts),
            noticeAgency = noticeAgencyFrom(observation, contracts),
            title = contracts.valueIn(observation, FieldConcept.NOTICE_TITLE)?.let(NoticeTitle::of),
            businessDivision = observation.sourceDivision,
            serviceDivision = serviceDivisionFrom(observation, contracts),
            mainConstructionType = mainConstructionTypeFrom(observation, contracts),
        ),
        unknownFieldCount,
    )
}

/**
 * 원문 raw 관측 ↔ canonical command 변환(②) — 유일한 변환 지점. 원문은 [NoticeCollected.raw]
 * 로 보존된다(감사). 공고번호·차수 중 하나라도 없거나 차수가 형식을 어기면 이 항목은 탈락한다(④·⑦, D-6F8-7).
 * **원문 값이 무엇이든 던지지 않는다** — 값 유래 실패는 탈락 사유이거나 필드 부재다(`CanonicalizeNeverThrowsTest`).
 */
fun canonicalize(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
): CanonicalizationOutcome {
    val unknownFieldCount = policy.fieldContracts.unknownKeysIn(observation).size
    return when (val identity = resolvedNoticeId(observation, policy.fieldContracts)) {
        is IdentityOutcome.Unresolvable -> CanonicalizationOutcome.Dropped(identity.reason, unknownFieldCount)
        is IdentityOutcome.Resolved -> normalizedCommand(observation, policy, identity.id, unknownFieldCount)
    }
}
