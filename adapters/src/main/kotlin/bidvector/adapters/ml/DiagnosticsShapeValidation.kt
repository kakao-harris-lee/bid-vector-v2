package bidvector.adapters.ml

import contract.bidvector.ml.v1.ReleaseKind
import contract.bidvector.ml.v1.Success
import java.math.BigDecimal
import bidvector.workflow.prediction.PredictionDiagnostics as DomainPredictionDiagnostics
import bidvector.workflow.prediction.SegmentSupport as DomainSegmentSupport
import bidvector.workflow.prediction.Weight as DomainWeight
import contract.bidvector.ml.v1.SegmentSupport as ProtoSegmentSupport

/**
 * M4/4D-3(scope.md D-4D3-2) — `Success.diagnostics` 형태 검증·파싱. `ReleaseShapeValidation.kt`
 * ·`CandidateShapeValidation.kt`와 같은 꼴(`ParsedSuccessFields.kt`에서 갈라낸 파일 —
 * detekt `TooManyFunctions`, 술어를 한 파일에 모아 table test 가 가리키게 한다).
 * `isAcceptableSuccessShape`(구조 검증층, `ParsedSuccessFields.kt`)가
 * `PredictionDiagnostics.init`·`Weight.init`이 던지기 전에 먼저 잰다
 * ([bidvector.workflow.prediction.BidPredictionOutcome] 파일 규율).
 *
 * [hasValidDiagnosticsShape]는 넷을 잰다:
 * (a) `shrinkage_weight.fraction` 이 정규형 decimal string이고 [0,1] — `Weight.init`과 짝
 * (b) `segment_support` 가 `UNSPECIFIED`/`UNRECOGNIZED` 아님
 * (c) `uint32` 셋(`training_row_count`·`excluded_observations`·`agency_sample_count`)을
 *     Kotlin `Int` 로 읽어 음수가 아님(2^31 이상 = 오버플로 = 계약 위반) —
 *     `PredictionDiagnostics.init`과 짝
 * (d) `release_kind = DERIVED` ⇒ `training_row_count == 0`(2F 우회 (10) 의 Kotlin
 *     집행, D-2B-6 「아티팩트 학습 행 수」) — `Diagnostics`는 `release_kind`를 모르므로
 *     이 불변식은 도메인 `init`으로 표현할 수 없다. 이 함수가 유일한 집행 지점이다.
 */
internal fun hasValidDiagnosticsShape(success: Success): Boolean {
    val diagnostics = success.diagnostics
    val checks =
        listOf(
            diagnostics.shrinkageWeight.fraction.toWeightOrNull() != null,
            diagnostics.segmentSupport.toDomainOrNull() != null,
            diagnostics.trainingRowCount >= 0,
            diagnostics.excludedObservations >= 0,
            diagnostics.agencySampleCount >= 0,
            success.release.releaseKind != ReleaseKind.RELEASE_KIND_DERIVED || diagnostics.trainingRowCount == 0,
        )
    return checks.all { it }
}

/** `ParsedSuccessFields.kt`가 `mapSuccess`에서 쓰는 최종 조립(값만, 판정 무변경). */
internal fun ParsedSuccessFields.toDiagnostics(): DomainPredictionDiagnostics =
    DomainPredictionDiagnostics(
        trainingRowCount = trainingRowCount,
        segmentSupport = segmentSupport,
        shrinkageWeight = shrinkageWeight,
        excludedObservations = excludedObservations,
        agencySampleCount = agencySampleCount,
        agencySampleBelowThreshold = agencySampleBelowThreshold,
    )

/**
 * `toRateOrNull()`(`ParsedSuccessFields.kt`)과 같은 꼴 — 범위 사전 검사 뒤 생성해 값 타입
 * `init`이 실제로 던지지 않게 한다. `Weight`는 `Rate`와 달리 상한 [0,1]을 값 타입 자신이
 * 강제하지만, 이 사전 검사가 없으면 `Weight(BigDecimal(fraction))`이 그 `init`을 던지는
 * 경로로 남는다 — 검증층이 먼저 걸러야 한다는 규율과 같다.
 */
internal fun String.toWeightOrNull(): DomainWeight? {
    val value = toValidatedBigDecimalOrNull() ?: return null
    return if (value.signum() < 0 || value > BigDecimal.ONE) null else DomainWeight(value)
}

internal fun ProtoSegmentSupport.toDomainOrNull(): DomainSegmentSupport? =
    when (this) {
        ProtoSegmentSupport.SEGMENT_SUPPORT_DIRECT -> DomainSegmentSupport.Direct
        ProtoSegmentSupport.SEGMENT_SUPPORT_PARENT_CATEGORY -> DomainSegmentSupport.ParentCategory
        ProtoSegmentSupport.SEGMENT_SUPPORT_GLOBAL -> DomainSegmentSupport.Global
        ProtoSegmentSupport.SEGMENT_SUPPORT_UNSPECIFIED, ProtoSegmentSupport.UNRECOGNIZED -> null
    }
