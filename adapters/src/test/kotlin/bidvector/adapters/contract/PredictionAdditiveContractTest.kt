package bidvector.adapters.contract

import bidvector.adapters.ml.hasValidReleaseShape
import contract.bidvector.ml.v1.AgencyIdFact
import contract.bidvector.ml.v1.CalculateOptimalBidRequest
import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import contract.bidvector.ml.v1.CategoryCodeFact
import contract.bidvector.ml.v1.IntervalSource
import contract.bidvector.ml.v1.MissingReason
import contract.bidvector.ml.v1.ReleaseKind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * M2/2F(scope.md ⑦, D-2F-1~4) — v1 additive 묶음(표본 축 fact 둘·`Diagnostics` 넷·
 * `IntervalSource.POSTERIOR_PREDICTIVE`·`ModelRelease.release_kind`)의 계약 단언.
 * `PredictionContractTest.kt`(같은 패키지, M2/2B)에서 size ratchet(v2-지침서 §5, 파일당
 * 500줄)으로 갈라낸 파일이다 — testdata·헬퍼(`contractTestdataRoot`·`canonicalBytes`)는
 * `ContractTestdataSupport.kt`(같은 패키지)를 공유한다.
 */
class PredictionAdditiveContractTest {
    private val testdataRoot: Path = contractTestdataRoot("prediction")

    private fun bytes(name: String): ByteArray = readTestdataBytes(testdataRoot, name)

    private val requestBytes = bytes("calculate_optimal_bid_request.binpb")
    private val successBytes = bytes("calculate_optimal_bid_response_success.binpb")

    // `release_kind = DERIVED`·`interval_source = POSTERIOR_PREDICTIVE`를 함께 싣는 표본
    // (분포 엔진 — 아티팩트 없는 release, D-2F-2). 기존 `successBytes`(ARTIFACT)와 짝이다.
    private val derivedPosteriorPredictiveBytes =
        bytes("calculate_optimal_bid_response_success_posterior_predictive.binpb")

    // ---- diagnostics 넷 보존(proto 레벨, 도메인 미소비 — `OPEN-2F-DIAGNOSTICS-DOMAIN`) ----

    @Test
    fun `diagnostics 넷은 파싱 뒤에도 값이 보존된다`() {
        val success = CalculateOptimalBidResponse.parseFrom(successBytes).success
        success.diagnostics.shrinkageWeight.fraction shouldBe "0.1500"
        success.diagnostics.excludedObservations shouldBe 12
        success.diagnostics.agencySampleCount shouldBe 38
        success.diagnostics.agencySampleBelowThreshold shouldBe false
    }

    // ---- IntervalSource.POSTERIOR_PREDICTIVE(D-2F-3) ----

    @Test
    fun `POSTERIOR_PREDICTIVE 표본은 그 값 그대로 파싱된다`() {
        val success = CalculateOptimalBidResponse.parseFrom(derivedPosteriorPredictiveBytes).success
        success.uncertainty.intervalSource shouldBe IntervalSource.INTERVAL_SOURCE_POSTERIOR_PREDICTIVE
    }

    @Test
    fun `CalculateOptimalBidResponse Success DERIVED POSTERIOR_PREDICTIVE 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(CalculateOptimalBidResponse.parseFrom(derivedPosteriorPredictiveBytes)).toList() shouldBe
            derivedPosteriorPredictiveBytes.toList()
    }

    // ---- 표본 축(agency_id/category_code, D-2F-1) 왕복 — 값 표본 1·missing 표본 1 ----

    @Test
    fun `요청 표본의 agency_id category_code fact 는 값 표본과 missing 표본이 각 하나다`() {
        val request = CalculateOptimalBidRequest.parseFrom(requestBytes)
        val samples = request.competitionSamplesList
        samples[0].agencyId.factCase shouldBe AgencyIdFact.FactCase.VALUE
        samples[0].agencyId.value shouldBe "agency-opaque-771"
        samples[0].categoryCode.factCase shouldBe CategoryCodeFact.FactCase.VALUE
        samples[0].categoryCode.value shouldBe "CAT-0821"
        samples[1].agencyId.factCase shouldBe AgencyIdFact.FactCase.MISSING
        samples[1].agencyId.missing shouldBe MissingReason.MISSING_REASON_NOT_COLLECTED_YET
        samples[1].categoryCode.factCase shouldBe CategoryCodeFact.FactCase.MISSING
        samples[1].categoryCode.missing shouldBe MissingReason.MISSING_REASON_NOT_COLLECTED_YET
    }

    // ---- release_kind(D-2F-2) — ARTIFACT/DERIVED 통과, UNSPECIFIED 거부, kind ↔
    // release_id 접두 `distribution/` 불일치 거부(설계 검토 우회 (2)(9)) ----

    @Test
    fun `ARTIFACT release 는 hasValidReleaseShape 를 통과한다`() {
        val release = CalculateOptimalBidResponse.parseFrom(successBytes).success.release
        release.releaseKind shouldBe ReleaseKind.RELEASE_KIND_ARTIFACT
        hasValidReleaseShape(release) shouldBe true
    }

    @Test
    fun `DERIVED release 는 dataset_id 가 비어 있어도 hasValidReleaseShape 를 통과한다`() {
        val release = CalculateOptimalBidResponse.parseFrom(derivedPosteriorPredictiveBytes).success.release
        release.releaseKind shouldBe ReleaseKind.RELEASE_KIND_DERIVED
        release.datasetId shouldBe ""
        hasValidReleaseShape(release) shouldBe true
    }

    @Test
    fun `DERIVED release 는 dataset_id 가 비공백이어도 hasValidReleaseShape 를 통과한다`() {
        val release =
            CalculateOptimalBidResponse
                .parseFrom(derivedPosteriorPredictiveBytes)
                .success
                .release
                .toBuilder()
                .setDatasetId("dataset-2026-09-15")
                .build()
        release.releaseKind shouldBe ReleaseKind.RELEASE_KIND_DERIVED
        hasValidReleaseShape(release) shouldBe true
    }

    @Test
    fun `release_kind UNSPECIFIED 는 hasValidReleaseShape 가 거부한다`() {
        val release =
            CalculateOptimalBidResponse
                .parseFrom(successBytes)
                .success
                .release
                .toBuilder()
                .clearReleaseKind()
                .build()
        release.releaseKind shouldBe ReleaseKind.RELEASE_KIND_UNSPECIFIED
        hasValidReleaseShape(release) shouldBe false
    }

    @Test
    fun `DERIVED 인데 release_id 접두가 distribution 이 아니면 거부된다`() {
        val release =
            CalculateOptimalBidResponse
                .parseFrom(derivedPosteriorPredictiveBytes)
                .success
                .release
                .toBuilder()
                .setReleaseId("release-2026-09-01")
                .build()
        hasValidReleaseShape(release) shouldBe false
    }

    @Test
    fun `ARTIFACT 인데 release_id 접두가 distribution 이면 거부된다`() {
        val release =
            CalculateOptimalBidResponse
                .parseFrom(successBytes)
                .success
                .release
                .toBuilder()
                .setReleaseId("distribution/reserve-draw-distribution-v1")
                .build()
        hasValidReleaseShape(release) shouldBe false
    }
}
