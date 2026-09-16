package bidvector.workflow.evaluation

import bidvector.decision.BidNowReason
import bidvector.decision.MlUnavailableReason
import bidvector.decision.Verdict
import bidvector.workflow.prediction.SegmentSupport

/**
 * D-4D4-4 — `BidNow` 판정의 근거를 사람이 읽는 한국어 줄로 합성하는 순수 함수. 채널
 * 무관·부작용 없음·영속하지 않는다(`docs/discovery/data-dictionary.md` §3.1 「문장은
 * 렌더링 시점에 생성하고 영속하지 않는다」) — 6A `ContentRenderer` 구현이 이 함수를
 * 호출해 알림 본문에 싣는다(`OPEN-4D4-CONTENT-REF`). 반환값은 `List<String>`뿐이고
 * 어떤 결과 타입도 짓지 않는다 — 문구가 판정 결과가 되는 것을 막는다(scope.md 우회 (4)).
 *
 * 줄 순서 고정(D-4D4-4): ① 판정 근거([BidNowReason] 각 1줄) ② 예측 근거([PredictionEvidence.Diagnosed]는
 * 구간·학습 행·기관 표본·수축 가중치·엔진 제외 관측, [PredictionEvidence.NotPredicted]는 사유
 * 1줄) ③ 표본 제외 요약(`excludedSamples`가 비어 있으면 생략).
 *
 * 숫자는 [java.math.BigDecimal.toPlainString]·[Int.toString]만 쓴다 — printf 계열 서식
 * 함수·locale 종속 API는 이 파일에 없다. **갱신 2(verifier r1 V-1)** — `ArchitectureGateTest`
 * 의 locale 누출 축은 `layer.domain` 모듈에만 걸리고 `workflow`는 `layer.application`이라
 * 대상 밖이다. 잠금은 `EvidenceLinesBoundaryTest`(소스 텍스트 경계 test, S-5
 * `NotificationBoundaryTest` 관례)가 진다 — 그 test 의 금지 어휘 목록은 여기 적지 않는다
 * (이 KDoc 자신이 스캔 대상이라 어휘를 그대로 옮기면 자기매치가 난다).
 */
fun evidenceLinesFor(
    verdict: Verdict.BidNow,
    evidence: PredictionEvidence,
): List<String> {
    val excludedSamples = if (evidence is PredictionEvidence.Diagnosed) evidence.excludedSamples else emptyMap()
    return verdict.reasons.map(::bidNowReasonLine) + predictionLines(evidence) + excludedSampleLines(excludedSamples)
}

private fun bidNowReasonLine(reason: BidNowReason): String =
    when (reason) {
        is BidNowReason.PriorityAboveBidNowThreshold -> {
            "우선순위 ${reason.priority.toPlainString()} 이 기준 ${reason.threshold.toPlainString()} 이상"
        }

        is BidNowReason.ForceBidOverride -> {
            "확률 ${reason.probability.toPlainString()}(기준 ${reason.probabilityThreshold.toPlainString()}) · " +
                "적합도 ${reason.matched.toPlainString()}(기준 ${reason.matchedThreshold.toPlainString()}) 강제 승격"
        }
    }

private fun predictionLines(evidence: PredictionEvidence): List<String> =
    when (evidence) {
        is PredictionEvidence.Diagnosed -> diagnosedLines(evidence)
        is PredictionEvidence.NotPredicted -> listOf(notPredictedLine(evidence))
    }

/** ML-04 ② 골든 줄(D-4D4-4) — 기관 표본이 임계 미만이면 그 사실을 근거 문구에 싣는다. */
private fun diagnosedLines(diagnosed: PredictionEvidence.Diagnosed): List<String> {
    val diagnostics = diagnosed.diagnostics
    val agencyCount = diagnostics.agencySampleCount.toString()
    val shrinkageWeight = diagnostics.shrinkageWeight.value.toPlainString()
    val golden =
        if (diagnostics.agencySampleBelowThreshold) {
            "기관 표본 ${agencyCount}건(임계 미만) · 수축 가중치 $shrinkageWeight"
        } else {
            "기관 표본 ${agencyCount}건 · 수축 가중치 $shrinkageWeight"
        }
    val base =
        listOf(
            "구간 지지 근거: ${segmentSupportLabel(diagnostics.segmentSupport)}",
            "학습 표본 ${diagnostics.trainingRowCount}건",
            golden,
        )
    return if (diagnostics.excludedObservations > 0) {
        base + "엔진 제외 관측 ${diagnostics.excludedObservations}건"
    } else {
        base
    }
}

private fun notPredictedLine(notPredicted: PredictionEvidence.NotPredicted): String =
    "예정가 분포 근거 없음: ${mlUnavailableReasonLabel(notPredicted.reason)}"

private fun segmentSupportLabel(support: SegmentSupport): String =
    when (support) {
        SegmentSupport.Direct -> "직접"
        SegmentSupport.ParentCategory -> "상위 카테고리"
        SegmentSupport.Global -> "전역"
    }

private fun mlUnavailableReasonLabel(reason: MlUnavailableReason): String =
    when (reason) {
        MlUnavailableReason.ScoreNotProvided -> "점수 미제공"
        MlUnavailableReason.DeadlineExceeded -> "응답 시한 초과"
        MlUnavailableReason.CircuitOpen -> "회로 차단 열림"
        MlUnavailableReason.RetryBudgetExhausted -> "재시도 예산 소진"
        MlUnavailableReason.TransportFailed -> "전송 실패"
        MlUnavailableReason.ModelNotReady -> "모델 준비 안 됨"
        MlUnavailableReason.ReleaseMismatch -> "release 불일치"
        MlUnavailableReason.ContractViolation -> "계약 위반"
        MlUnavailableReason.UnsupportedSchema -> "지원하지 않는 스키마"
        MlUnavailableReason.UnsupportedRelease -> "지원하지 않는 release"
        MlUnavailableReason.InvalidRequest -> "잘못된 요청"
    }

/** enum 선언 순서로 고정 — map 순회 순서(구현 정의)에 기대지 않는다. */
private fun excludedSampleLines(excludedSamples: Map<SampleExclusionReason, Int>): List<String> =
    SampleExclusionReason.entries
        .mapNotNull { reason ->
            excludedSamples[reason]?.takeIf { count -> count > 0 }?.let { count -> reason to count }
        }.map { (reason, count) -> "표본 제외 ${count}건: ${sampleExclusionReasonLabel(reason)}" }

private fun sampleExclusionReasonLabel(reason: SampleExclusionReason): String =
    when (reason) {
        SampleExclusionReason.RANK_ONE_RATE_MISSING -> "1순위 사정율 결측"
        SampleExclusionReason.RESERVE_PRICE_COUNT_MISMATCH -> "예비가격 개수 불일치"
        SampleExclusionReason.RESERVE_PRICE_SEQUENCE_INVALID -> "예비가격 순번 불일치"
        SampleExclusionReason.RESERVE_PRICE_MISSING -> "예비가격 결측"
        SampleExclusionReason.DRAW_NUMBERS_OUT_OF_RANGE -> "추첨번호 범위 이탈"
        SampleExclusionReason.BASE_AMOUNT_MISSING -> "기초금액 결측"
        SampleExclusionReason.OPENING_DATE_MISSING -> "개찰일자 결측"
        SampleExclusionReason.CANDIDATE_VANISHED -> "후보 소실"
    }
