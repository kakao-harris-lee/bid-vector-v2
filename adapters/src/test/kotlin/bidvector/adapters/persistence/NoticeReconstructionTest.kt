package bidvector.adapters.persistence

import bidvector.procurement.BusinessDivision
import bidvector.procurement.MainConstructionType
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeStatus
import bidvector.procurement.ServiceDivision
import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
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
            demandAgencyCode = null,
            demandAgencyName = null,
            noticeAgencyCode = null,
            noticeAgencyName = null,
            title = null,
            businessDivision = null,
            serviceDivision = null,
            mainConstructionType = null,
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

    /** D-6F9-3 — 업무구분 새 칸 셋의 복원. 대분류는 문서 열거 라벨 넷 전부가 왕복하고, 나머지 둘은 저장된 이름 그대로다. */
    @Test
    fun `업무구분 새 칸 셋은 저장 표현에서 그대로 복원된다 — 대분류 넷 전부`() {
        BusinessDivision.entries.forEach { division ->
            val notice = id.reconstructNotice(minimalRow(NoticeStatus.Open).copy(businessDivision = division.label))
            notice.businessDivision shouldBe division
        }
        val notice =
            id.reconstructNotice(
                minimalRow(NoticeStatus.Open).copy(serviceDivision = "기술용역", mainConstructionType = "전기공사업"),
            )
        notice.serviceDivision shouldBe ServiceDivision.of("기술용역")
        notice.mainConstructionType shouldBe MainConstructionType.of("전기공사업")
        id.reconstructNotice(minimalRow(NoticeStatus.Open)).businessDivision shouldBe null
    }

    @Test
    fun `어휘 밖 대분류 라벨은 손상이라 조용히 null 로 접지 않고 실패한다 — DB CHECK 가 이 값을 막는다`() {
        shouldThrow<IllegalStateException> {
            id.reconstructNotice(minimalRow(NoticeStatus.Open).copy(businessDivision = "basket"))
        }
    }

    /**
     * code-review r2 LOW — 세부 분류 이름 두 열의 손상 정책(D-6F9-3, r1 의 L7 고침)을 잠근다. 세 열이 **같은** 정책을
     * 갖는 것이 요점이라 위 대분류 test 와 짝이다: 공백뿐인 값은 V17 CHECK 가 막으므로 도달했다면 CHECK 없이 만든
     * 열이라는 뜻이고, 조용히 `null` 로 접으면 감시 「관심 업종」이 아무것과도 맞지 않는 채 성립한다(D-6F4-8).
     * 이 잠금 없이는 다음 리팩터가 `?.let(ServiceDivision::of)` 로 되돌려도 전 test 가 초록이다.
     */
    @Test
    fun `공백뿐인 세부 분류 이름 두 열도 조용히 null 로 접지 않고 실패한다 — 대분류와 같은 정책`() {
        shouldThrow<IllegalStateException> {
            id.reconstructNotice(minimalRow(NoticeStatus.Open).copy(serviceDivision = "   "))
        }
        shouldThrow<IllegalStateException> {
            id.reconstructNotice(minimalRow(NoticeStatus.Open).copy(mainConstructionType = " "))
        }
    }
}
