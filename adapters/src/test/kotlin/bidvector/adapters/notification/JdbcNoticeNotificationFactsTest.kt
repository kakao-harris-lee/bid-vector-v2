package bidvector.adapters.notification

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 공고 표 읽기의 **행 부재** 축 — 키가 없으면 `null` 이다(던지지 않는다). 그 `null` 이 렌더러의
 * 「저장된 공고 정보가 없습니다」 갈래로 이어지고, 그쪽 문면은 [NoticeMessageRendererTest] 가 든다.
 *
 * **행이 있는 축은 E2E 가 든다**(`PipelineTelegramDeliveryE2ETest`) — 공고 행을 손으로 심으려면
 * `raw_observation` 부모와 provenance CHECK 를 모두 만족시켜야 하고, 그 사본을 또 짓는 대신
 * **production 수집 경로가 심은 행**을 읽는 쪽이 더 센 증거다(네 칸 전부가 그 경로에서 온다).
 */
internal class JdbcNoticeNotificationFactsTest : PersistenceTestSupport() {
    @Test
    fun `공고 행이 없으면 null 이다`() {
        val facts = JdbcNoticeNotificationFacts(dataSource())

        facts.factsFor(NoticeReference("NO-SUCH-NOTICE", "000")) shouldBe null
    }

    /** 차수가 키의 일부다 — 같은 번호의 다른 차수는 다른 공고다(`NoticeId` KDoc). */
    @Test
    fun `번호가 같고 차수가 다르면 다른 공고다`() {
        val facts = JdbcNoticeNotificationFacts(dataSource())

        facts.factsFor(NoticeReference("NO-SUCH-NOTICE", "001")) shouldBe null
    }
}
