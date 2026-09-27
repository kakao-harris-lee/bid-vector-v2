package bidvector.archfixture.violating.app.tiers

import org.springframework.jdbc.core.simple.JdbcClient

/**
 * D-6A2b-32·38(M-r3-3) 위반 표본 — ② 층·③ 층 규칙의 **영구 음성 대조**다. 두 규칙은 production
 * 이 오늘 그것을 어기지 않아 양성만으로는 항진식이 되어도 조용하다.
 *
 * 층은 test 가 배정한다(`LayerAssignment`) — 규칙 값은 production 과 같고 배정만 바뀐다.
 */
class RogueTier2JdbcHolder(
    private val jdbc: JdbcClient,
) {
    fun bump(): Int = jdbc.sql("UPDATE operator_strategy SET revision = revision + 1 WHERE id = 1").update()
}

/** ③ 층으로 배정할 표본 — 수집 레인 자리를 흉내낸다. */
class RogueCollectionLane {
    fun run(): Int = hashCode()
}

/** 제한 층에서 ③ 층을 참조한다 — HTTP 로 닿는 층이 수집 레인을 쥐는 형태. */
class RogueCollectionReferencer(
    private val lane: RogueCollectionLane,
) {
    fun run(): Int = lane.run()
}
