package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeStatus
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * verifier r2 N-5 — F-8이 [NoticeReconstruction]에 넣은 「표 밖 상태 → 예외」 술어를 지키는
 * test가 없었다(변이로 `NoticeStatus.Cancelled` 항목을 표에서 지워도 persistence suite
 * 전건이 초록이었다). DB 없이 순수 Kotlin으로 [NoticeRow] → `reconstructNotice`만 잰다
 * (`PersistenceAdapterDependencyTest`와 같은 관례 — Testcontainers가 필요 없는 test는
 * [PersistenceTestSupport]를 상속하지 않는다).
 *
 * 이 test 자체는 [NoticeStatus]가 여섯 값뿐이라 "표 밖 상태"를 직접 구성할 수 없다 —
 * 그 결손은 이제 `NoticeReconstruction.kt`의 `eventPathFor`(private, exhaustive `when`)가
 * 컴파일 시점에 막는다(구조적 방지, 그 파일 KDoc). 그래서 이 test는 **여섯 상태 전부가
 * 예외 없이 왕복 복원된다**는 양성 경로를 고정한다 — 누군가 [NoticeStatus]에 값을
 * 추가하고 그 `when` 분기를 빠뜨리면 이 파일이 아니라 컴파일 자체가 즉시 깨진다(이
 * test는 현재 여섯 분기가 실제로 동작함을 증명한다).
 */
class NoticeReconstructionTest {
    private val id = NoticeId(NoticeNumber.of("RECON-20260907-001"), NoticeRound.of("000"))

    private fun minimalRow(status: NoticeStatus): NoticeRow =
        NoticeRow(
            status = status.name,
            businessCategoryCode = null,
            businessCategoryLabel = null,
            baseAmountWon = null,
            baseAmountCurrency = null,
            baseAmountVat = null,
            baseAmountProvenance = null,
            baseAmountProvenanceDetail = null,
            estimatedAmountWon = null,
            estimatedAmountCurrency = null,
            estimatedAmountVat = null,
            estimatedAmountProvenance = null,
            estimatedAmountProvenanceDetail = null,
            estimatedAmountSourceKey = null,
            allocatedBudgetWon = null,
            allocatedBudgetProvenance = null,
            allocatedBudgetProvenanceDetail = null,
            floorRateFraction = null,
            floorRateOriginKind = null,
            floorRateOriginDetail = null,
            deadlineAt = null,
            revision = 1L,
        )

    @Test
    fun `NoticeStatus 여섯 값 전부가 예외 없이 왕복 복원되고 status 가 정확히 일치한다`() {
        for (status in NoticeStatus.entries) {
            val notice = id.reconstructNotice(minimalRow(status))
            notice.status shouldBe status
        }
    }
}
