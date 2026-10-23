package bidvector.archfixture.violating.app

import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.NoticeId
import bidvector.procurement.decideDetailFetch
import java.time.Instant

/**
 * M6/G1-C03 위반 표본(V1) — 기준 시각 경계 밖에서 조회 가치 술어를 직접 부르고 기준 시각에 상수를 넘긴다
 * (G1-T 가 COL-03 을 거짓으로 판정한 결함의 모양). production classpath 에는 오르지 않는다(test 소스).
 */
class RogueDetailFetchCaller {
    fun decide(
        id: NoticeId,
        now: Instant,
        gates: DetailFetchGates,
    ): DetailFetchDecision =
        decideDetailFetch(
            noticeId = id,
            noticeKeyHash = "0".repeat(NOTICE_KEY_LENGTH),
            alreadyHeld = false,
            openingObservedAt = null,
            lastCheckedAt = null,
            now = now,
            gates = gates,
        )
}

private const val NOTICE_KEY_LENGTH = 64
