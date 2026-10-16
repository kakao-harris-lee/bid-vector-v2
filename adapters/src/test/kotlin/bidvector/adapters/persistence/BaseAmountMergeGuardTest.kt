package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.ResolvedEstimatedAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * COL-02 첫째 acceptance — **실 기초금액이 없는 개찰 pass 를 같은 공고번호로 흘려도 저장된
 * 기초금액이 덮이지 않는다.**
 *
 * 이 축에 이미 있는 측정은 **값이 있는 유입**의 권위 비교다(`ResolvedBaseAmount` 의 점유 가드
 * 판정과 그 DB 가드 parity). 결측 재관측이 지우지 않음을 persist 경로로 재는 test 는 발주기관·
 * 공고명 두 축에만 있었고(`NoticeFindRoundTripTest`), **기초금액 축에는 없었다** — 그래서
 * 그 가드를 기초금액 자리에서만 지우는 변이가 전건 초록이었다.
 *
 * V2 에서 개찰 축의 파생 기초금액은 `opening_result` 자기 fact 로 가고 공고 행의 기초금액 열을
 * 쓰지 않는다(그 분리가 COL-02 경계의 구조적 답이다). 그래서 이 test 가 지키는 자리는 **공고 행의
 * write 가드**다: 기초금액을 싣지 않은 재관측이 그 열을 건드리지 못한다.
 *
 * 세 걸음으로 잠근다 — ① 기초금액이 실린 insert ② 아무 값도 싣지 않은 개찰 pass: 가드가 기존을
 * 지켜 다른 열도 안 바뀌므로 `Unchanged`(가드가 없으면 그 열이 null 로 덮여 merged != existing
 * 이 되어 write 를 시도한다 — 이 단언 자체가 가드의 증거다) ③ **예정가는 싣고 기초금액은 싣지
 * 않은** 개찰 pass: 행은 실제로 갱신되는데(예정가가 빈 자리를 채운다) 기초금액은 그대로다.
 * ③ 이 있어야 ② 의 초록이 「아무것도 쓰지 못하는 구현」에서 온 것이 아님을 말할 수 있다.
 */
class BaseAmountMergeGuardTest : PersistenceTestSupport() {
    private val repository get() = JdbcNoticeRepository(dataSource())

    private var observedSeconds = 0L

    private fun idOf(suffix: String) = NoticeId(NoticeNumber.of("COL02-GUARD-$suffix"), NoticeRound.of("000"))

    private fun observationOf(
        id: NoticeId,
        marker: String,
        endpoint: SourceEndpoint,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                RawKey("bidNtceNo") to id.number.value,
                RawKey("bidNtceOrd") to id.round.value,
                RawKey("marker") to marker,
            ),
            endpoint,
            Instant.parse("2026-10-16T00:00:00Z").plusSeconds(observedSeconds++),
        )

    private fun commandOf(
        id: NoticeId,
        raw: RawNoticeObservation,
        baseAmount: ResolvedBaseAmount? = null,
        estimatedAmount: ResolvedEstimatedAmount? = null,
    ): NoticeCollected =
        NoticeCollected(
            id = id,
            businessCategory = null,
            baseAmount = baseAmount,
            estimatedAmount = estimatedAmount,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = null,
            openingScheduledAt = null,
            raw = raw,
        )

    private fun persist(command: NoticeCollected): PersistOutcome =
        repository.persist(command, appendRawObservation(command.raw))

    private fun publishedBaseAmount(
        id: NoticeId,
        won: Long,
    ): ResolvedBaseAmount =
        ResolvedBaseAmount.Direct.of(won, Currency.KRW, VatTreatment.EXCLUSIVE, Provenance.Published(id.round))

    /** 개찰 pass 가 나르는 예정가 — 파생값이라 권위가 없다(빈 자리만 채운다). */
    private fun derivedEstimatedAmount(won: Long): ResolvedEstimatedAmount =
        ResolvedEstimatedAmount(
            RawKey("plnprc"),
            EstimatedAmount(won, Currency.KRW, VatTreatment.EXCLUSIVE, Provenance.DerivedFromOpening),
        )

    @Test
    fun `기초금액을 싣지 않은 개찰 pass 는 저장된 기초금액을 덮지 않는다`() {
        val id = idOf("MISSING")
        val published = publishedBaseAmount(id, 1_234_000L)

        val first = observationOf(id, "notice-list", SourceEndpoint.NOTICE_LIST)
        persist(commandOf(id, first, baseAmount = published)) shouldBe PersistOutcome.Inserted

        // ② 아무 값도 싣지 않은 개찰 pass — write 자체가 없다.
        val emptyPass = observationOf(id, "opening-empty", SourceEndpoint.OPENING_RESULT)
        persist(commandOf(id, emptyPass)) shouldBe PersistOutcome.Unchanged
        requireNotNull(repository.find(id)).baseAmount shouldBe published

        // ③ 예정가만 실은 개찰 pass — 행은 갱신되고 기초금액 열은 그대로다.
        val derived = derivedEstimatedAmount(2_345_000L)
        val yegaPass = observationOf(id, "opening-yega", SourceEndpoint.OPENING_RESULT)
        persist(commandOf(id, yegaPass, estimatedAmount = derived))
            .shouldBeInstanceOf<PersistOutcome.Updated>()
        val afterPass = requireNotNull(repository.find(id))
        afterPass.baseAmount shouldBe published
        afterPass.estimatedAmount shouldBe derived
    }

    /**
     * 같은 열이 **권위 있는** 재관측에는 교체된다 — 위 test 의 불변이 「이 경로로는 기초금액을
     * 아예 못 쓴다」에서 온 것이 아님을 잠근다(점유 가드의 판정 축 자체는
     * `ResolvedBaseAmountTest` 와 DB parity test 가 든다).
     */
    @Test
    fun `기초금액은 권위 있는 재관측에는 교체된다`() {
        val id = idOf("REPLACED")
        val published = publishedBaseAmount(id, 1_234_000L)
        val corrected = publishedBaseAmount(id, 9_876_000L)

        val first = observationOf(id, "first", SourceEndpoint.NOTICE_LIST)
        persist(commandOf(id, first, baseAmount = published)) shouldBe PersistOutcome.Inserted

        val second = observationOf(id, "corrected", SourceEndpoint.NOTICE_LIST)
        persist(commandOf(id, second, baseAmount = corrected))
            .shouldBeInstanceOf<PersistOutcome.Updated>()

        requireNotNull(repository.find(id)).baseAmount shouldBe corrected
    }
}
