package bidvector.app.conformance

import bidvector.decision.AssessmentBand
import bidvector.decision.FloorShortfall
import bidvector.decision.FloorShortfallPolicyData
import bidvector.decision.FloorUnmeasurableReason
import bidvector.decision.Frequency
import bidvector.decision.ProvenancePolicyData
import bidvector.decision.ProvenanceRuleId
import bidvector.decision.ProvenanceRules
import bidvector.decision.ShortfallComparison
import bidvector.decision.ShortfallTally
import bidvector.decision.criticalAssessmentRateFor
import bidvector.decision.isShortfall
import bidvector.decision.measureFloorShortfall
import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Derived
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.RoundingPolicy
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.criticalAssessmentRate
import bidvector.sharedkernel.export
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * M1/1D — base-amount-provenance·floor-threshold·floor-shortfall case → executor dispatch.
 * `CorpusExecutors.kt`(1B-c·1C)와 같은 관심사 분리(크기 한도 회피가 아니라 「fixture 를
 * 읽고 비교하는 법」과 「계약과 무엇을 대조하는가」를 나눈다) — `SharedKernelCorpusConformanceTest.kt`
 * 의 dispatch 표(`VALUE_EXECUTORS`)가 이 파일의 [PROVENANCE_FLOOR_EXECUTORS] 를 합친다.
 */

// ---- provenance/floor value executor 보조 — M1/1D, decision 공개 API 호출 → projection ----

/** legacy 술어 어휘(조사 §5.2 ②, kebab-case) ↔ `ProvenanceRuleId`. 결속은 이 runner 소유다(D-12). */
private fun provenanceRuleIdFromToken(token: String): ProvenanceRuleId =
    when (token) {
        "suspect-ratio" -> ProvenanceRuleId.SuspectRatio
        "clean-integer" -> ProvenanceRuleId.CleanInteger
        "derived-yega" -> ProvenanceRuleId.DerivedYega
        "derived-vat" -> ProvenanceRuleId.DerivedVat
        else -> error("이 corpus 가 다루지 않는 provenance rule 토큰: $token")
    }

private fun provenanceRuleIdName(ruleId: ProvenanceRuleId): String =
    when (ruleId) {
        ProvenanceRuleId.SuspectRatio -> "suspect-ratio"
        ProvenanceRuleId.CleanInteger -> "clean-integer"
        ProvenanceRuleId.DerivedYega -> "derived-yega"
        ProvenanceRuleId.DerivedVat -> "derived-vat"
    }

/** `BaseAmountProvenance` variant 이름 — 리플렉션 대신 소진 `when`(1C `licenseVerdictName` 관례). */
private fun baseAmountProvenanceName(classification: BaseAmountProvenance): String =
    when (classification) {
        BaseAmountProvenance.Clean -> "Clean"
        BaseAmountProvenance.DerivedYega -> "DerivedYega"
        BaseAmountProvenance.DerivedVat -> "DerivedVat"
        BaseAmountProvenance.SuspectRatio -> "SuspectRatio"
        BaseAmountProvenance.Unknown -> "Unknown"
    }

/**
 * 이 corpus 의 어느 base-amount-provenance case 도 `vatTreatment`·`provenance`를 값으로
 * 주장하지 않는다(fixture 는 `row.rawBaseAmount`·`currency`만 준다) — 1B-c
 * `UNASSERTED_NOTICE_REVISION` 관례와 같은 자리표시자. `original`은 값 왕복
 * (`originalBaseAmount`)만 잰다.
 */
private fun unassertedBaseAmount(
    won: Long,
    currency: Currency,
): BaseAmount = BaseAmount(won, currency, VatTreatment.UNKNOWN, Provenance.Undeclared)

/**
 * `ProvenancePolicyData.init`(D-5)은 `SuspectRatio`가 `ruleOrder`에 있으면 `trustRatioMax`가
 * 있어야 정책 구성을 허용한다. authoritative 3 case 는 `ruleHits`로 매치를 이미 선언해
 * 술어를 한 번도 평가하지 않는다(orderer 층, D-4) — 그래서 이 값은 어떤 계산에도 쓰이지
 * 않는다. 값 자체가 아니라 "정책이 그 규칙을 활성화하려면 임계값이 있어야 한다"는
 * 구성 불변식만 만족시키는 자리표시자다(임계값 재유도는 `OPEN-DEC-07`).
 */
private val UNASSERTED_TRUST_RATIO_MAX_PLACEHOLDER = Rate.ofFraction(BigDecimal.ONE)

/** `input.policy.ruleOrder`+`policyVersion` → `Resolution.Resolved<ProvenancePolicyData>`(임계값은 자리표시자, D-5). */
private fun provenancePolicyFrom(input: JsonNode): Resolution.Resolved<ProvenancePolicyData> {
    val ruleOrder = input.atDollarPath("$.policy.ruleOrder").values().map { provenanceRuleIdFromToken(it.asString()) }
    val policyVersionSource = input.atDollarPath("$.policy.policyVersion").asString()
    val policyData =
        ProvenancePolicyData(
            ruleOrder = ruleOrder,
            trustRatioMax = UNASSERTED_TRUST_RATIO_MAX_PLACEHOLDER,
            cleanIntegerTolerance = BigDecimal.ZERO,
            vatMultiplier = BigDecimal.ONE,
            vatTolerance = BigDecimal.ZERO,
            yegaTolerance = BigDecimal.ZERO,
        )
    return Resolution.Resolved(policyData, PolicyVersion(EffectiveFrom.Initial, policyVersionSource))
}

/** `recoveryCandidate` 부재는 `Fact.Absent`(계산하지 않았다는 뜻이지 `0`이 아니다) — D-6. */
private fun recoveryEstimateFrom(
    input: JsonNode,
    currency: Currency,
): Fact<Money> {
    val recoveryCandidateNode = input.path("recoveryCandidate")
    return if (recoveryCandidateNode.isMissingNode || recoveryCandidateNode.isNull) {
        Fact.Absent(ReasonCode.EMPTY_INPUT)
    } else {
        Fact.Known(unassertedBaseAmount(recoveryCandidateNode.asLong(), currency))
    }
}

/**
 * base-amount-provenance 실행자(①②③, D-13) — authoritative 3 case 는 `ruleHits` 로
 * 「어느 규칙이 걸렸는지」를 추상 선언한다(조사 §5.3) — 이 executor 는 그 추상을 그대로
 * 받아 orderer(`ProvenanceRules.judge`)로 넘긴다.
 */
private fun baseAmountProvenanceExecutor(input: JsonNode): Map<String, Any?> {
    val row = input.atDollarPath("$.row")
    val rawBaseAmount = row.path("rawBaseAmount").asLong()
    val currency = Currency.valueOf(row.path("currency").asString())
    val original = unassertedBaseAmount(rawBaseAmount, currency)

    val hits =
        input
            .path("ruleHits")
            .values()
            .map { provenanceRuleIdFromToken(it.asString()) }
            .toSet()
    val policy = provenancePolicyFrom(input)
    val recoveryEstimate = recoveryEstimateFrom(input, currency)

    val judgement = ProvenanceRules.judge(original, hits, recoveryEstimate, policy)
    val firstMatchedRuleName = judgement.evidence.firstMatchedRule?.let(::provenanceRuleIdName)
    val projection =
        mutableMapOf<String, Any?>(
            "classification" to baseAmountProvenanceName(judgement.classification),
            "firstMatchedRule" to firstMatchedRuleName,
            "evidence" to mapOf("firstMatchedRule" to firstMatchedRuleName),
            "policyVersion" to judgement.evidence.policyVersion.source,
            // 001(3 규칙)·002·003(3 규칙) 전부 ruleOrder.size > 1 이라 이 corpus 는 이
            // 감사 필드가 항상 참인 case 만 다룬다 — 순서가 하나뿐이면 애초에 순서가
            // load-bearing 일 수 없다는 사실을 그대로 낸다.
            "orderIsLoadBearing" to (judgement.evidence.ruleOrder.size > 1),
            // Money 는 불변이라 판정을 거쳐도 원본 참조가 바뀌지 않는다 — 구조적으로 항상
            // false 다(D-13 감사 boolean).
            "originalBaseAmount" to judgement.original.export().won,
            "originalFieldMutated" to false,
            "recoveryWrittenToOriginalField" to false,
        )
    val recovery = judgement.recoveryEstimate
    if (recovery is Fact.Known) {
        val recoveredWon = recovery.value.export().won
        projection["recoveryEstimate"] = mapOf("field" to "recoveredBaseAmount", "amount" to recoveredWon)
    }
    return projection
}

/**
 * 입력 `{fraction, unit?: "fraction", ...}` → `Rate`(floor-threshold·floor-shortfall 공용).
 * `unit` 이 선언되면 `fraction` 이어야 한다(1B-c `rate-unit` 관례) — 이 corpus 는
 * percent 선언을 다루지 않는다. `realizedAssessmentRate` 는 이미 계산된 사정률이라
 * `unit` 을 선언하지 않는다(입력 fixture 실측) — 그 경우 `unit` 검사를 건너뛴다.
 */
private fun rateFromFractionNode(node: JsonNode): Rate {
    val unitNode = node.path("unit")
    require(unitNode.isMissingNode || unitNode.asString() == "fraction") { "이 corpus 는 unit=fraction 만 다룬다" }
    return Rate.ofFraction(node.path("fraction").decimalValue())
}

/** 이 corpus 의 어느 case 도 `noticeRevision`을 값으로 주장하지 않는다(1B-c 관례와 같다). */
private const val UNASSERTED_FLOOR_RATE_NOTICE_REVISION = 0

/** legacy 6자리(조사 §2.1) — main 정책이 아니라 이 runner 의 test 정책 인스턴스에만 쓴다. */
private const val LEGACY_CRITICAL_RATE_SCALE_DIGITS = 6

private fun bidRateFrom(input: JsonNode): BidRate =
    BidRate.recommended(rateFromFractionNode(input.atDollarPath("$.recommendedBidRate")))

private fun floorRateFrom(input: JsonNode): FloorRate =
    FloorRate(
        rateFromFractionNode(input.atDollarPath("$.floorRate")),
        FloorRateOrigin.NoticeValue(UNASSERTED_FLOOR_RATE_NOTICE_REVISION),
    )

/**
 * `floor-threshold` 축 전용 — `FloorShortfallPolicyData`가 없는 입력(ft-001·003)에서도
 * 임계을 낸다. `Derived<AssessmentRate>`를 그대로 돌려준다(verifier r1 F-1) — 벗겨서
 * bare rate 로 두지 않는다.
 */
private fun criticalAssessmentRateFrom(input: JsonNode): Derived<AssessmentRate> {
    val scalePolicy =
        Resolution.Resolved(
            RoundingPolicy(LEGACY_CRITICAL_RATE_SCALE_DIGITS, RoundingMode.HALF_UP),
            PolicyVersion(EffectiveFrom.Initial, "test-critical-rate-scale"),
        )
    val measurement = criticalAssessmentRate(bidRateFrom(input), floorRateFrom(input), scalePolicy)
    check(measurement is Measurement.Measured<Derived<AssessmentRate>>) {
        "이 corpus 는 floorRate=0 을 다루지 않는다 — criticalAssessmentRate 가 Unmeasurable 을 냈다"
    }
    return measurement.value
}

/** 입력 `policy.shortfallComparison` 문자열 → 계약 값(ft-002 승격분, decision 28). */
private fun shortfallComparisonFromToken(token: String): ShortfallComparison =
    when (token) {
        "strictly-greater" -> ShortfallComparison.StrictlyGreater
        "greater-or-equal" -> ShortfallComparison.GreaterOrEqual
        else -> error("이 corpus 가 다루지 않는 shortfallComparison 토큰: $token")
    }

/**
 * `floor-threshold-001`·`003`의 입력에는 `policy` 블록이 없다 — 그 case 들의 `verifies`는
 * 방향(미만/초과)만 겨냥하고 경계 등가를 겨냥하지 않으므로, 어느 `ShortfallComparison`으로
 * 판정해도 같은 결과가 나와야 한다(D-3 정책 독립성 — curator `32b1b39` 논의). runner 가
 * 정책값을 지어내는 대신 **두 값 모두로 판정해 결과가 같음을 단언**하고 그 공유값을 낸다 —
 * 입력이 갖지 않은 정보를 만들어 넣지 않는다.
 */
private fun shortfallWithoutPolicy(
    realized: AssessmentRate,
    critical: AssessmentRate,
): Boolean {
    val strictly = isShortfall(realized, critical, ShortfallComparison.StrictlyGreater)
    val orEqual = isShortfall(realized, critical, ShortfallComparison.GreaterOrEqual)
    check(strictly == orEqual) {
        "정책 없는 입력은 방향만 겨냥해야 한다 — StrictlyGreater=$strictly, GreaterOrEqual=$orEqual 로 갈렸다"
    }
    return strictly
}

/**
 * `$.comparison` 문자열(verifier r1 F-4) — 정책 토큰에서 낸다. 001·003(정책 부재)은
 * decision 28 초기값(strictly-greater)의 문면을 쓴다 — [shortfallWithoutPolicy]가 실제로는
 * 두 비교값이 같은 결과임을 이미 확인했으므로 어느 쪽 문구를 써도 값 자체는 어긋나지
 * 않는다. 이 필드는 계약 타입이 아니다(`does_not_carry` ①) — 사람이 읽는 설명일 뿐이다.
 */
private fun comparisonStringFor(policyNode: JsonNode): String {
    val token = if (policyNode.isMissingNode) "strictly-greater" else policyNode.path("shortfallComparison").asString()
    return when (token) {
        "strictly-greater" -> "realizedAssessmentRate > criticalAssessmentRate"
        "greater-or-equal" -> "realizedAssessmentRate >= criticalAssessmentRate"
        else -> error("이 corpus 가 다루지 않는 shortfallComparison 토큰: $token")
    }
}

/**
 * floor-threshold 실행자(②, D-4 — 표본 하나의 미달 술어). 입력이 정책을 실으면 그것을
 * 읽고(`policy.shortfallComparison`, ft-002), 없으면 정책 독립적 판정으로 대신한다
 * (ft-001·003, [shortfallWithoutPolicy]) — 어느 쪽도 runner 가 값을 지어내지 않는다.
 * `critical`은 `Derived<AssessmentRate>`다(verifier r1 F-1) — `isShortfall`(bare 값을
 * 받는 순수 술어)을 부를 때만 `.value`로 벗기고, 봉투에는 담지 않는다(floor-threshold
 * 축은 그 자체가 봉투를 만들지 않는다 — D-4 표본 단건 술어).
 */
private fun floorThresholdExecutor(input: JsonNode): Map<String, Any?> {
    val critical = criticalAssessmentRateFrom(input)
    val realized = AssessmentRate.observed(rateFromFractionNode(input.atDollarPath("$.sample.realizedAssessmentRate")))
    val policyNode = input.path("policy")
    val sampleIsShortfall =
        if (policyNode.isMissingNode) {
            shortfallWithoutPolicy(realized, critical.value)
        } else {
            val comparison = shortfallComparisonFromToken(policyNode.path("shortfallComparison").asString())
            isShortfall(realized, critical.value, comparison)
        }
    return mapOf(
        "criticalAssessmentRate" to mapOf("fraction" to critical.value.rate.fraction),
        "sampleIsShortfall" to sampleIsShortfall,
        "comparison" to comparisonStringFor(policyNode),
    )
}

/** 이 corpus 의 어느 case 도 밴드 값을 주장하지 않는다(fs-005 note — synthetic) — 미사용 자리표시자. */
private val UNASSERTED_FULL_RANGE_BAND = AssessmentBand(BigDecimal("-1000000"), BigDecimal("1000000"))

private fun floorUnmeasurableReasonProjection(reason: FloorUnmeasurableReason): Map<String, Any?> =
    when (reason) {
        FloorUnmeasurableReason.FloorRateUnresolved -> {
            mapOf("code" to "FloorRateUnresolved")
        }

        FloorUnmeasurableReason.FloorModelNotApplicable -> {
            mapOf("code" to "FloorModelNotApplicable")
        }

        FloorUnmeasurableReason.BidRateUnavailable -> {
            mapOf("code" to "BidRateUnavailable")
        }

        is FloorUnmeasurableReason.SampleInsufficient -> {
            mapOf("code" to "SampleInsufficient", "required" to reason.required, "actual" to reason.actual)
        }
    }

private fun frequencyProjection(frequency: Frequency): Map<String, Any?> =
    mapOf("numerator" to frequency.numerator, "denominator" to frequency.denominator)

/** raw ≥ required && qualified < required → "Measured-candidate"(D-13, fs-005 전이) — 그 밖은 `null`. */
private fun transitionedFromProjection(
    tally: ShortfallTally,
    minAssessmentSamples: Int,
): String? =
    if (tally.rawCount >= minAssessmentSamples && tally.qualifiedDenominator < minAssessmentSamples) {
        "Measured-candidate"
    } else {
        null
    }

private fun shortfallTallyFrom(input: JsonNode): ShortfallTally =
    if (input.path("rawSampleCount").isMissingNode) {
        ShortfallTally(
            rawCount = input.atDollarPath("$.qualifiedSampleCount").asInt(),
            outsideBand = 0,
            shortfallNumerator = input.atDollarPath("$.shortfallSampleCount").asInt(),
        )
    } else {
        ShortfallTally(
            rawCount = input.atDollarPath("$.rawSampleCount").asInt(),
            outsideBand = input.atDollarPath("$.samplesOutsideBand").asInt(),
            shortfallNumerator = input.atDollarPath("$.shortfallSampleCountAmongQualified").asInt(),
        )
    }

/** `input.policy`+`band` → `Resolution.Resolved<FloorShortfallPolicyData>`(밴드 값은 자리표시자, D-3). */
private fun floorShortfallPolicyFrom(input: JsonNode): Resolution.Resolved<FloorShortfallPolicyData> {
    val minAssessmentSamples = input.atDollarPath("$.policy.minAssessmentSamples").asInt()
    val rationale = input.atDollarPath("$.policy.minAssessmentSamplesRationale").asString()
    val policyVersionSource = input.atDollarPath("$.policy.policyVersion").asString()
    val bandNode = input.path("band")
    val denominatorBand =
        if (bandNode.isMissingNode) {
            UNASSERTED_FULL_RANGE_BAND
        } else {
            AssessmentBand(bandNode.path("min").decimalValue(), bandNode.path("max").decimalValue())
        }
    val policyData =
        FloorShortfallPolicyData(
            minAssessmentSamples = minAssessmentSamples,
            minAssessmentSamplesRationale = rationale,
            denominatorBand = denominatorBand,
            shortfallComparison = ShortfallComparison.StrictlyGreater,
            biasIndeterminateBand = UNASSERTED_FULL_RANGE_BAND,
            criticalRateScale = LEGACY_CRITICAL_RATE_SCALE_DIGITS,
        )
    return Resolution.Resolved(policyData, PolicyVersion(EffectiveFrom.Initial, policyVersionSource))
}

/** [FloorShortfallJudgement.result]·[ShortfallTally]에서 낸 output projection(D-13 감사 필드 포함). */
private fun floorShortfallResultProjection(
    result: FloorShortfall,
    tally: ShortfallTally,
    minAssessmentSamples: Int,
): MutableMap<String, Any?> {
    val projection =
        mutableMapOf<String, Any?>(
            "result" to
                when (result) {
                    is FloorShortfall.Measured -> "Measured"
                    is FloorShortfall.Unmeasurable -> "Unmeasurable"
                },
            "qualifiedDenominator" to tally.qualifiedDenominator,
            "transitionedFrom" to transitionedFromProjection(tally, minAssessmentSamples),
        )
    when (result) {
        is FloorShortfall.Unmeasurable -> {
            projection["reason"] = floorUnmeasurableReasonProjection(result.reason)
            projection["frequency"] = null
            // Unmeasurable 은 값을 낸 적이 없다 — "0%"로 렌더링되는 공개 경로가 없으므로
            // 구조적으로 항상 false 다(위협 모델 (a), D-13 감사 boolean).
            projection["renderedAsZeroPercent"] = false
        }

        is FloorShortfall.Measured -> {
            projection["frequency"] = frequencyProjection(result.frequency)
        }
    }
    return projection
}

/**
 * floor-shortfall 실행자(⑤⑥, D-4 — 집계에서 판정). 임계는
 * `bidvector.decision.criticalAssessmentRateFor`로 낸다(verifier r1 F-3) — 이 경로가
 * `FloorShortfallPolicyData.criticalRateScale`을 실제로 소비하는 유일한 자리다. 별도
 * `RoundingPolicy`를 runner 가 조립하지 않는다(F-1과 F-3을 한 호출로 함께 만족한다).
 */
private fun floorShortfallExecutor(input: JsonNode): Map<String, Any?> {
    val policy = floorShortfallPolicyFrom(input)
    val measurement = criticalAssessmentRateFor(bidRateFrom(input), floorRateFrom(input), policy)
    check(measurement is Measurement.Measured<Derived<AssessmentRate>>) {
        "이 corpus 는 floorRate=0 을 다루지 않는다 — criticalAssessmentRate 가 Unmeasurable 을 냈다"
    }
    val tally = shortfallTallyFrom(input)

    val judgement = measureFloorShortfall(tally, measurement.value, policy)
    val projection = floorShortfallResultProjection(judgement.result, tally, policy.value.minAssessmentSamples)
    projection["policyVersion"] = policy.version.source
    return projection
}

internal val PROVENANCE_FLOOR_EXECUTORS: Map<String, (JsonNode) -> Map<String, Any?>> =
    mapOf(
        "base-amount-provenance-001" to ::baseAmountProvenanceExecutor,
        "base-amount-provenance-002" to ::baseAmountProvenanceExecutor,
        "base-amount-provenance-003" to ::baseAmountProvenanceExecutor,
        "floor-threshold-001" to ::floorThresholdExecutor,
        "floor-threshold-002" to ::floorThresholdExecutor,
        "floor-threshold-003" to ::floorThresholdExecutor,
        "floor-shortfall-001" to ::floorShortfallExecutor,
        "floor-shortfall-005" to ::floorShortfallExecutor,
    )
