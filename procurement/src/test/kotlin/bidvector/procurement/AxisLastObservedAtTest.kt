package bidvector.procurement

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val AXIS: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL

private fun settled(
    at: String,
    key: String?,
    axis: SourceEndpoint,
    outcome: AttemptOutcome,
    walk: String = at,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), AttemptKind.AXIS, walk = Instant.parse(walk))

/**
 * **재조회 게이트가 읽는 「그 축을 마지막으로 관측한 때」**(M6/G1-C03) — [AttemptHistory.axisLastObservedAt].
 * 결말 줄만 관측이고, 관문 거부와 끊긴 라운드를 닫는 줄은 관측이 아니다.
 */
class AxisLastObservedAtTest {
    /**
     * **M6/G1-C03 — 재조회 창을 여는 것은 관측뿐이다.** 관문 거부(속도 보호·승인 상한·원천 쿼터)는
     * 그 축에 대한 관측이 아니므로(D-6G2d-16) 「마지막으로 본 때」를 옮기지 않는다. 옮기면 쿼터로
     * 멈춘 실행 뒤에 다음 실행이 미완 축을 이어받지 못하고 창이 닫힐 때까지 기다린다 — 재호출 상한이
     * 같은 이유로 거부를 세지 않는 것과 한 규칙이다.
     */
    @Test
    fun `관문 거부는 마지막 관측 시각을 옮기지 않는다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(
                listOf(
                    settled("2026-09-24T01:00:00Z", key, AXIS, AttemptOutcome.Failed("SHORT_WALK")),
                    settled("2026-09-24T05:00:00Z", key, AXIS, AttemptOutcome.Refused("QUOTA_EXHAUSTED")),
                ),
            )

        history.axisLastObservedAt().getValue(key) shouldBe
            mapOf(AXIS to Instant.parse("2026-09-24T01:00:00Z"))
    }

    /** 관측이 하나도 없는 (공고, 축)은 **항목 자체가 없다** — 창이 열린 적 없는 것과 같은 값이다. */
    @Test
    fun `거부만 있는 축은 마지막 관측 시각을 갖지 않는다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(listOf(settled("2026-09-24T05:00:00Z", key, AXIS, AttemptOutcome.Refused("DAILY"))))

        history.axisLastObservedAt() shouldBe emptyMap()
    }

    /**
     * **끊긴 라운드를 닫는 줄은 관측이 아니다**(M6/G1-C03) — 시각이 「닫은 때」다. 앞 실행이 닫고 그 축에
     * 닿기 전에 멈췄어도 다음 실행은 그 줄로 창을 열지 않는다(닫는 행위가 창을 열면 끊긴 축이 재개되지
     * 않는다). 앞의 관측은 그대로 남는다.
     */
    @Test
    fun `끊긴 라운드를 닫는 줄은 마지막 관측 시각을 옮기지 않는다`() {
        val key = "0".repeat(64)
        val observed = settled("2026-09-24T01:00:00Z", key, AXIS, AttemptOutcome.Failed("SHORT_WALK"))
        val closing = settled("2026-09-24T05:00:00Z", key, AXIS, INTERRUPTED_ROUND, walk = "2026-09-24T04:00:00Z")

        AttemptHistory(listOf(observed, closing)).axisLastObservedAt().getValue(key) shouldBe
            mapOf(AXIS to Instant.parse("2026-09-24T01:00:00Z"))
        AttemptHistory(listOf(closing)).axisLastObservedAt() shouldBe emptyMap()
    }

    /**
     * **결말 줄만 관측이다** — 다음 라운드의 의도·호출 줄이 결말 **뒤에** 덧붙어도 관측 시각을 옮기지
     * 않는다. 그 줄까지 세면 창이 쪽 수(= 참가자 수)에 따라 흔들린다(비랜덤 결측의 같은 함정).
     */
    @Test
    fun `다음 라운드의 의도와 호출 줄은 마지막 관측 시각을 옮기지 않는다`() {
        val key = "0".repeat(64)
        val later = Instant.parse("2026-09-24T05:00:00Z")
        val history =
            AttemptHistory(
                listOf(
                    settled("2026-09-24T01:00:00Z", key, AXIS, AttemptOutcome.Failed("SHORT_WALK")),
                    CollectionAttempt(key, AXIS, AttemptOutcome.Succeeded, later, AttemptKind.PENDING, walk = null),
                    CollectionAttempt(key, AXIS, AttemptOutcome.Succeeded, later, AttemptKind.HTTP, walk = null),
                ),
            )

        history.axisLastObservedAt().getValue(key) shouldBe mapOf(AXIS to Instant.parse("2026-09-24T01:00:00Z"))
    }

    /**
     * **절단 사유가 닫는 줄의 값을 내지 않는다**(M6/G1-C03 r2) — 관측 질의는 [INTERRUPTED_ROUND] 와 같은 결말을 뺀다.
     * 절단 코드가 그 값과 겹치면 **진짜 관측**(일시 실패로 끝난 걷기)이 조용히 관측에서 빠진다. 절단 사유 전수를
     * 결말로 옮겨 그 값이 나오지 않음을 잰다. 「전수」는 손 목록이 아니라 **컴파일러가 적은 sealed 하위 타입**
     * (`PermittedSubclasses`)과 대조한다 — 사유를 더하고 목록에 안 더하면 이 test 가 붉어진다(CR r3 L13).
     */
    @Test
    fun `절단 사유의 결말은 끊긴 라운드를 닫는 값과 겹치지 않는다`() {
        val permitted = TruncationCause::class.java.permittedSubclasses.toSet()
        ALL_TRUNCATION_CAUSES shouldHaveSize permitted.size - 1 + BudgetLimit.entries.size
        ALL_TRUNCATION_CAUSES.map { it.javaClass }.toSet() shouldBe permitted
        val outcomes = ALL_TRUNCATION_CAUSES.map { attemptOutcomeOf(truncatedBy(it)) }

        ALL_TRUNCATION_CAUSES.map(::truncationCodeOf) shouldNotContain INTERRUPTED_ROUND.code
        outcomes shouldNotContain INTERRUPTED_ROUND
    }
}

/** 절단 사유 전수 — sealed 하위 타입과의 등식을 위 test 가 잰다(매개변수 변종은 `BudgetLimit` 마다 하나). */
private val ALL_TRUNCATION_CAUSES: List<TruncationCause> =
    listOf(
        TruncationCause.MaxPages,
        TruncationCause.RepeatedPage,
        TruncationCause.QuotaExhausted,
        TruncationCause.Timeout,
        TruncationCause.TransportFailure,
        TruncationCause.ServerError,
        TruncationCause.NotRetryable,
        TruncationCause.InputError,
        TruncationCause.Unclassified,
        TruncationCause.StructureFailure,
        TruncationCause.SelfThrottled,
    ) + BudgetLimit.entries.map(TruncationCause::BudgetExhausted)

private fun truncatedBy(cause: TruncationCause) =
    CollectionAccounting(
        received = 0,
        normalized = 0,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = null,
        pagesFetched = 1,
        truncated = true,
        unknownFields = 0,
        truncationCause = cause,
    )
