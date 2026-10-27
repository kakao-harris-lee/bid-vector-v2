package bidvector.adapters.opening

import bidvector.adapters.notice.ReadOnlyWitness
import bidvector.adapters.notice.witnessedDataSource
import bidvector.procurement.NoticeId
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **「승격 전」과 「수집 전」을 가르는 축** — canonical 행이 없을 때 그 공고의 개찰 결과 **원문이
 * 있는가**를 조회가 어떻게 판정하는가(실제 PostgreSQL).
 *
 * 이 축만 따로 세운 이유: 판정의 입력이 다른 모든 test 와 다르다 — 여기 쓰이는 것은 상류가 보낸
 * **원문 표기**와 보존 규율의 **마스킹 표지**이고, 결과 투영(열·칸·금액)은 한 줄도 관여하지 않는다.
 * 결과 투영과 읽기 전용은 [JdbcOpeningResultViewTest] 가 잰다.
 *
 * 실측되는 우회: ⑩ 개찰 축 아닌 원문을 끌어오지 않는다 · ⑬ 다른 공고·다른 차수의 원문이 이 공고의
 * 답을 바꾸지 않는다 · ⑭ 원문 표기 차이가 상태를 바꾸지 않는다 · ⑮ 마스킹된 원문을 「승격 전」으로
 * 세지 않는다.
 */
class JdbcOpeningAxisObservationTest : OpeningViewDbFixture() {
    @Test
    fun `canonical 이 없고 개찰 결과 축 원문이 있으면 관측됐다고 답한다`() {
        seedNotice()
        rawKey(SourceEndpoint.OPENING_COMPLETE, T1)

        val facts = present(view().lookup(id))
        facts.canonical shouldBe null
        facts.openingAxisObserved shouldBe true
    }

    @Test
    fun `우회 ⑩ — 예비가격·기초금액 축 원문만 있으면 개찰 결과 관측이 아니다`() {
        seedNotice()
        rawKey(SourceEndpoint.RESERVE_PRICE_DETAIL, T1)
        rawKey(SourceEndpoint.BASE_AMOUNT_DETAIL, T2)

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    @Test
    fun `우회 ⑬ — 다른 공고의 개찰 원문은 이 공고의 답을 바꾸지 않는다`() {
        seedNotice()
        seedNotice(other)
        rawKey(SourceEndpoint.OPENING_RESULT_LIST, T1, other)

        present(view().lookup(id)).openingAxisObserved shouldBe false
        present(view().lookup(other)).openingAxisObserved shouldBe true
    }

    @Test
    fun `차수가 다른 개찰 원문은 이 공고의 답을 바꾸지 않는다`() {
        seedNotice()
        val otherRound = NoticeId(id.number, NoticeRound.of("002"))
        rawKey(SourceEndpoint.OPENING_RESULT_LIST, T1, otherRound)

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    /**
     * 원문의 공고번호는 **상류가 보낸 그대로**이고 물어보는 쪽은 canonical 이다 — 둘을 SQL 에서
     * 그대로 맞대면 대소문자·공백이 든 번호에서 조용히 빗나가 「원문도 없다」고 답한다
     * (`JdbcCollectedAxisStore` 가 COL-05 로 세운 규율과 같은 자리).
     *
     * **표기를 이렇게 고른 이유**(vr r2 M-1): 소문자 + 한 칸 공백만 쓰면 `upper(replace(btrim(…),
     * ' ', '-'))` 같은 **부분 사본**도 통과한다 — 그 사본을 SQL 에 다시 넣는 「최적화」가 초록으로
     * 지나가면 탭·연속 공백·앞뒤 공백에서 결함이 되살아난다. 이 표기는 `NoticeNumber.of` 의 규칙
     * 전부(trim · ASCII 대문자화 · **연속** 공백류 → `-`)를 만족해야만 맞는다.
     */
    @Test
    fun `원문 번호가 대소문자·탭·연속 공백이어도 같은 공고의 관측으로 센다`() {
        seedNotice()
        appendRawObservation(rawObservationOf("  openview\t20261022  001 ", id.round.value))

        present(view().lookup(id)).openingAxisObserved shouldBe true
    }

    /** 접은 뒤에도 공고 축은 선다 — 표기만 다른 것과 **다른 공고**인 것을 구별한다. */
    @Test
    fun `원문 표기가 달라도 다른 공고의 관측은 이 공고로 세지 않는다`() {
        seedNotice()
        appendRawObservation(rawObservationOf(" OpenView\t20261022  999 ", id.round.value))

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    /**
     * 차수는 정규화가 아니라 **거부**다(`\d{3}`) — 형식을 어긴 원문 차수는 어느 공고의 키도 되지
     * 못한다. 차수 등식을 SQL 에 두는 것(cr r2 M-2)과 값 객체로 접는 것이 같은 답을 낸다는 사실이
     * 이 자리에서 선다.
     */
    @Test
    fun `원문 차수가 형식을 어기면 이 공고의 관측이 아니다`() {
        seedNotice()
        appendRawObservation(rawObservationOf(id.number.value, "1", observedAt = T1))
        appendRawObservation(rawObservationOf(id.number.value, " 001", observedAt = T2))

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    /**
     * 마스킹된 원문은 **개찰 결과로 승격될 수 없다**(업체 식별 개념이 지워진 뒤다) — 세어 주면 그
     * 공고는 영영 「승격 전」으로 남아, 일시적이라던 상태가 영구 오답이 된다. 이 조건이 승격 적격
     * **전부**와 같다는 뜻은 아니다(cr r2 M-1 — 보존창·확정 표본은 이 조회가 볼 수 없는 축이다).
     */
    @Test
    fun `마스킹된 원문은 관측으로 세지 않는다`() {
        seedNotice()
        markMasked(rawKey(SourceEndpoint.OPENING_RESULT_LIST, T1))

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    /** 원문 표를 훑는 질의는 **canonical 이 없을 때만** 돈다(그 비용이 알려진 제한인 이유). */
    @Test
    fun `canonical 이 있으면 원문 존재 질의는 돌지 않는다`() {
        seedNotice()
        seedCanonical(canonicalOf())

        val witness = ReadOnlyWitness()
        JdbcOpeningResultView(witnessedDataSource(dataSource(), witness)).lookup(id)

        witness.queries().map { it.sql }.none { it.contains("raw_observation") } shouldBe true
    }
}
