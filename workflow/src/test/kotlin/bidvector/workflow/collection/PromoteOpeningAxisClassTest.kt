package bidvector.workflow.collection

import bidvector.procurement.OPENING_PER_NOTICE_AXES
import bidvector.procurement.OPENING_PROMOTION_AXES
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C⑤-0 — **승격의 상세 축 거울이 수집 팬아웃의 `DetailAxis` 와 같은가**(D-7C5P-11).
 *
 * 승격은 「결말이 없으면 **목록 축만** 행 시각으로 물러선다」로 걷기를 고른다. 그 판정의 입력이
 * 상세 축 집합이고, 정본은 수집이 공고마다 부르는 축의 열거다. 둘이 갈리면 승격이 상세 축을 목록
 * 축으로 오인해 **결말 없는 원문을 올린다**(verifier r2 R2-1 이 잡은 결함류) — 그래서 이 등식이
 * 거울의 유일한 존재 근거다. 한 선언으로 합치는 길은 **있지만**(`SourceEndpoint` 쪽 속성) 수집·추출
 * production 을 건드려 이 slice 의 범위 밖이라 합치지 않았다(제한 20, 후속 slice) — 그 사이의 틈을
 * 이 test 가 기계로 닫는다.
 */
class PromoteOpeningAxisClassTest {
    @Test
    fun `승격의 상세 축 집합은 수집 팬아웃의 DetailAxis 와 같다`() {
        val collectionDetailAxes = DetailAxis.entries.map { it.endpoint }.toSet()

        OPENING_PER_NOTICE_AXES shouldBe collectionDetailAxes
    }

    @Test
    fun `목록 축은 상세 축 집합 밖이다 — 물러설 수 있는 축이 그것뿐이다`() {
        val listAxes = OPENING_PROMOTION_AXES - OPENING_PER_NOTICE_AXES

        listAxes shouldBe setOf(SourceEndpoint.OPENING_RESULT_LIST, SourceEndpoint.OPENING_AWARD_LIST)
    }
}
