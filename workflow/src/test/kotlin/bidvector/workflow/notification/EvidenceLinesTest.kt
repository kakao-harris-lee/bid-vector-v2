package bidvector.workflow.notification

import bidvector.decision.LadderInput
import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.decision.VerdictLadder
import bidvector.decision.VerdictLadderPolicyData
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.evaluation.SampleExclusionReason
import bidvector.workflow.prediction.ModelReleaseRef
import bidvector.workflow.prediction.PredictionDiagnostics
import bidvector.workflow.prediction.SegmentSupport
import bidvector.workflow.prediction.Weight
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * D-4D4-4 — `evidenceLinesFor`의 골든 두 줄(ML-04 ②)·전수(exhaustive, `MlUnavailableReason`
 * 11·`SampleExclusionReason` 8)·줄 순서·생략 규칙을 잰다. 순수 함수라 ML·DB·시계
 * 무접촉 — 값만으로 잰다. `Verdict.BidNow`는 생성자가 `internal`(`decision` 모듈)이라
 * 이 모듈에서 직접 지을 수 없다 — [bidNowVia]가 [VerdictLadder.judge]를 통해 실제
 * 경로로 얻는다.
 */
class EvidenceLinesTest {
    private val release =
        ModelReleaseRef(
            releaseId = "release-1",
            artifactChecksum = "checksum-1",
            featureSchemaVersion = "schema-1",
            codeVersion = "code-1",
            datasetId = "dataset-1",
        )

    private fun diagnostics(
        agencySampleCount: Int = 20,
        agencySampleBelowThreshold: Boolean = false,
        shrinkageWeight: BigDecimal = BigDecimal("0.1500"),
        trainingRowCount: Int = 100,
        excludedObservations: Int = 0,
        segmentSupport: SegmentSupport = SegmentSupport.Direct,
    ): PredictionDiagnostics =
        PredictionDiagnostics(
            trainingRowCount = trainingRowCount,
            segmentSupport = segmentSupport,
            shrinkageWeight = Weight(shrinkageWeight),
            excludedObservations = excludedObservations,
            agencySampleCount = agencySampleCount,
            agencySampleBelowThreshold = agencySampleBelowThreshold,
        )

    private fun bidNowVia(priorityScore: BigDecimal = BigDecimal("0.9")): Verdict.BidNow {
        val policy =
            Resolution.Resolved(
                VerdictLadderPolicyData(
                    capacityHoldPriorityThreshold = BigDecimal("0.8"),
                    bidNowThreshold = BigDecimal("0.7"),
                    reviewThreshold = BigDecimal("0.45"),
                    forceBidProbabilityThreshold = BigDecimal("0.9"),
                    forceBidMatchedThreshold = BigDecimal("0.9"),
                ),
                PolicyVersion(EffectiveFrom.Initial, "test"),
            )
        val input =
            LadderInput(
                priorityScore = UnitScore(priorityScore),
                probabilityScore = null,
                matchedScore = null,
                currentActiveBids = 0,
                maxActiveBids = 10,
            )
        return VerdictLadder.judge(input, policy) as Verdict.BidNow
    }

    // ---- 골든 두 줄(ML-04 ②) ----

    @Test
    fun `기관 표본이 임계 미만이면 골든 줄에 임계 미만 표기가 실린다`() {
        val evidence =
            PredictionEvidence.Diagnosed(
                diagnostics =
                    diagnostics(
                        agencySampleCount = 2,
                        agencySampleBelowThreshold = true,
                        shrinkageWeight = BigDecimal("0.9500"),
                    ),
                release = release,
                excludedSamples = emptyMap(),
            )

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        lines shouldContain "기관 표본 2건(임계 미만) · 수축 가중치 0.9500"
    }

    @Test
    fun `기관 표본이 임계 이상이면 골든 줄에 임계 미만 표기가 없다`() {
        val evidence =
            PredictionEvidence.Diagnosed(
                diagnostics =
                    diagnostics(
                        agencySampleCount = 40,
                        agencySampleBelowThreshold = false,
                        shrinkageWeight = BigDecimal("0.0500"),
                    ),
                release = release,
                excludedSamples = emptyMap(),
            )

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        lines shouldContain "기관 표본 40건 · 수축 가중치 0.0500"
        lines.none { it.contains("임계 미만") } shouldBe true
    }

    // ---- NotPredicted 11 사유 전수 ----

    @Test
    fun `NotPredicted 11 사유 전부 근거 없음 줄을 낸다`() {
        val reasons =
            listOf(
                MlUnavailableReason.ScoreNotProvided to "점수 미제공",
                MlUnavailableReason.DeadlineExceeded to "응답 시한 초과",
                MlUnavailableReason.CircuitOpen to "회로 차단 열림",
                MlUnavailableReason.RetryBudgetExhausted to "재시도 예산 소진",
                MlUnavailableReason.TransportFailed to "전송 실패",
                MlUnavailableReason.ModelNotReady to "모델 준비 안 됨",
                MlUnavailableReason.ReleaseMismatch to "release 불일치",
                MlUnavailableReason.ContractViolation to "계약 위반",
                MlUnavailableReason.UnsupportedSchema to "지원하지 않는 스키마",
                MlUnavailableReason.UnsupportedRelease to "지원하지 않는 release",
                MlUnavailableReason.InvalidRequest to "잘못된 요청",
            )
        reasons.size shouldBe 11

        reasons.forEach { (reason, label) ->
            val lines = evidenceLinesFor(bidNowVia(), PredictionEvidence.NotPredicted(reason))

            lines shouldContain "예정가 분포 근거 없음: $label"
        }
    }

    // ---- SampleExclusionReason 8 전수 ----

    @Test
    fun `SampleExclusionReason 8 전부 표본 제외 줄을 낸다`() {
        val reasons =
            listOf(
                SampleExclusionReason.RANK_ONE_RATE_MISSING to "1순위 사정율 결측",
                SampleExclusionReason.RESERVE_PRICE_COUNT_MISMATCH to "예비가격 개수 불일치",
                SampleExclusionReason.RESERVE_PRICE_SEQUENCE_INVALID to "예비가격 순번 불일치",
                SampleExclusionReason.RESERVE_PRICE_MISSING to "예비가격 결측",
                SampleExclusionReason.DRAW_NUMBERS_OUT_OF_RANGE to "추첨번호 범위 이탈",
                SampleExclusionReason.BASE_AMOUNT_MISSING to "기초금액 결측",
                SampleExclusionReason.OPENING_DATE_MISSING to "개찰일자 결측",
                SampleExclusionReason.CANDIDATE_VANISHED to "후보 소실",
            )
        reasons.size shouldBe 8
        val evidence =
            PredictionEvidence.Diagnosed(
                diagnostics = diagnostics(),
                release = release,
                excludedSamples = reasons.associate { (reason, _) -> reason to 1 },
            )

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        reasons.forEach { (_, label) ->
            lines shouldContain "표본 제외 1건: $label"
        }
    }

    // ---- 줄 순서(D-4D4-4 고정 순서 ①②③) ----

    @Test
    fun `줄 순서는 판정 근거 예측 근거 표본 제외 순이다`() {
        val evidence =
            PredictionEvidence.Diagnosed(
                diagnostics = diagnostics(),
                release = release,
                excludedSamples = mapOf(SampleExclusionReason.BASE_AMOUNT_MISSING to 2),
            )

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        val reasonLineIndex = lines.indexOfFirst { it.startsWith("우선순위") }
        val evidenceLineIndex = lines.indexOfFirst { it.startsWith("구간 지지 근거") }
        val exclusionLineIndex = lines.indexOfFirst { it.startsWith("표본 제외") }

        (reasonLineIndex >= 0) shouldBe true
        (evidenceLineIndex >= 0) shouldBe true
        (exclusionLineIndex >= 0) shouldBe true
        (reasonLineIndex < evidenceLineIndex) shouldBe true
        (evidenceLineIndex < exclusionLineIndex) shouldBe true
    }

    // ---- 생략 규칙 ----

    @Test
    fun `excludedSamples 가 비면 표본 제외 줄이 없다`() {
        val evidence =
            PredictionEvidence.Diagnosed(diagnostics = diagnostics(), release = release, excludedSamples = emptyMap())

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        lines.none { it.startsWith("표본 제외") } shouldBe true
    }

    @Test
    fun `excludedObservations 가 0 이면 엔진 제외 관측 줄이 없다`() {
        val evidence =
            PredictionEvidence.Diagnosed(
                diagnostics = diagnostics(excludedObservations = 0),
                release = release,
                excludedSamples = emptyMap(),
            )

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        lines.none { it.contains("엔진 제외 관측") } shouldBe true
    }

    @Test
    fun `excludedObservations 가 양수면 엔진 제외 관측 줄이 실린다`() {
        val evidence =
            PredictionEvidence.Diagnosed(
                diagnostics = diagnostics(excludedObservations = 3),
                release = release,
                excludedSamples = emptyMap(),
            )

        val lines = evidenceLinesFor(bidNowVia(), evidence)

        lines shouldContain "엔진 제외 관측 3건"
    }

    // ---- 판정 근거 줄 — BidNowReason 마다 한 줄 ----

    @Test
    fun `판정 근거 줄은 BidNowReason 마다 하나 실리고 총 줄 수는 그 합이다`() {
        val verdict = bidNowVia(priorityScore = BigDecimal("0.9"))
        val evidence =
            PredictionEvidence.Diagnosed(diagnostics = diagnostics(), release = release, excludedSamples = emptyMap())

        val lines = evidenceLinesFor(verdict, evidence)

        // diagnostics() 기본값은 구간·학습·골든 3줄, excludedObservations=0·excludedSamples 빈
        // 맵이라 그 이상은 없다.
        lines.size shouldBe verdict.reasons.size + 3
    }
}
