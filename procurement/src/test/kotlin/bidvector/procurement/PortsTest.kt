package bidvector.procurement

import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private val NOTICE_ID = NoticeId(NoticeNumber.of("20260101002"), NoticeRound.of("000"))

private fun emptyBatch(): SourceBatch<RawNoticeObservation> =
    SourceBatch(
        items = emptyList(),
        accounting =
            CollectionAccounting(
                received = 0,
                normalized = 0,
                duplicate = 0,
                dropped = 0,
                dropReasons = emptyMap(),
                sourceTotal = 0,
                pagesFetched = 1,
                truncated = false,
                unknownFields = 0,
            ),
        next = null,
    )

/**
 * `DocumentSourcePort`의 서명이 [QualificationFetchDecision.Fetch]를 요구한다는 것을 실제
 * fake 구현으로 증명한다(D-3B2-5 (a)) — 술어([decideQualificationFetch])를 거치지 않고는
 * `Fetch`를 만들 수 없으므로 이 fake 는 `Skip` 경로에서 port 를 호출조차 하지 못한다(우회
 * 후보 (4), 위협 모델 방어 (a)).
 */
class PortsTest {
    @Test
    fun `Skip 이면 DocumentSourcePort 를 호출하지 않는다 — COL-04 업종제한 없음`() {
        var callCount = 0
        val fake =
            object : DocumentSourcePort {
                override fun fetchQualificationText(
                    evidence: QualificationFetchDecision.Fetch,
                ): SourceBatch<RawNoticeObservation> {
                    callCount++
                    return emptyBatch()
                }
            }

        val decision = decideQualificationFetch(NOTICE_ID, industryRestricted = false)
        if (decision is QualificationFetchDecision.Fetch) fake.fetchQualificationText(decision)

        callCount shouldBe 0
    }

    @Test
    fun `Fetch 이면 DocumentSourcePort 를 정확히 1회 호출한다 — COL-04 업종제한 있음`() {
        var callCount = 0
        val fake =
            object : DocumentSourcePort {
                override fun fetchQualificationText(
                    evidence: QualificationFetchDecision.Fetch,
                ): SourceBatch<RawNoticeObservation> {
                    callCount++
                    evidence.noticeId shouldBe NOTICE_ID
                    return emptyBatch()
                }
            }

        val decision = decideQualificationFetch(NOTICE_ID, industryRestricted = true)
        if (decision is QualificationFetchDecision.Fetch) fake.fetchQualificationText(decision)

        callCount shouldBe 1
    }
}
