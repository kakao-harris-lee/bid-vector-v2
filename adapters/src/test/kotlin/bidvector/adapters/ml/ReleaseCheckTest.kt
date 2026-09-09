package bidvector.adapters.ml

import contract.bidvector.ml.v1.ExactRelease
import contract.bidvector.ml.v1.LatestPromoted
import contract.bidvector.ml.v1.ModelReleaseSelector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `OPEN-2A-RELEASE-CHECK-4D` 종결(D-4D-3·D-4D-4) — 제3 변환 금지의 client 집행. 우회 (4)(5).
 */
class ReleaseCheckTest {
    @Test
    fun `exact_release 요청은 응답 release 가 정확히 같아야 통과한다`() {
        val selector =
            ModelReleaseSelector
                .newBuilder()
                .setExactRelease(
                    ExactRelease
                        .newBuilder()
                        .setReleaseId("r1")
                        .setArtifactChecksum("c1")
                        .build(),
                ).build()
        val matching = testModelRelease(releaseId = "r1", artifactChecksum = "c1")
        val mismatched = testModelRelease(releaseId = "other", artifactChecksum = "c1")

        releaseSatisfiesSelector(selector, matching, promoted = null) shouldBe true
        releaseSatisfiesSelector(selector, mismatched, promoted = null) shouldBe false
    }

    @Test
    fun `latest_promoted 요청은 GetModelMetadata promoted 와 같아야 통과한다`() {
        val selector = ModelReleaseSelector.newBuilder().setLatestPromoted(LatestPromoted.getDefaultInstance()).build()
        val promoted = testModelRelease(releaseId = "r1", artifactChecksum = "c1")
        val matching = testModelRelease(releaseId = "r1", artifactChecksum = "c1")
        val mismatched = testModelRelease(releaseId = "other", artifactChecksum = "c1")

        releaseSatisfiesSelector(selector, matching, promoted) shouldBe true
        releaseSatisfiesSelector(selector, mismatched, promoted) shouldBe false
    }

    @Test
    fun `latest_promoted 인데 promoted 조회가 없으면 불일치다(대조 불가는 통과가 아니다)`() {
        val selector = ModelReleaseSelector.newBuilder().setLatestPromoted(LatestPromoted.getDefaultInstance()).build()
        val response = testModelRelease()

        releaseSatisfiesSelector(selector, response, promoted = null) shouldBe false
    }

    @Test
    fun `selector 미설정은 통과하지 않는다`() {
        val selector = ModelReleaseSelector.getDefaultInstance()

        releaseSatisfiesSelector(selector, testModelRelease(), promoted = testModelRelease()) shouldBe false
    }
}
