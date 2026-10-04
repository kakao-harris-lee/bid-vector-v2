package bidvector.app.wiring

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.adapters.snapshot.RunStateLock
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetOutcome
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor

/**
 * M6/6G-2c — 수집 배선이 기대는 **원장 표면의 구조 잠금**. 세 가지가 여기서 닫힌다: 잠금을 놓는
 * 길이 하나뿐인가(D-6G2c-4) · 상한 원장의 「이미 쓴 몫」을 빠뜨릴 수 있는가(D-6G2c-19 저위 변이) ·
 * 상한의 하루가 **KST** 인가(같은 저위 변이의 다른 축).
 *
 * 앞 둘은 **거동이 없는 변이**다 — 가시성을 올리거나 기본값을 되살리는 편집은 모든 호출부가
 * 지금처럼 값을 넘기는 동안 아무 test 도 붉히지 않는다(vr r5-t: 둘 다 GREEN). 그 변이가 여는 것은
 * 「다음 호출부가 빠뜨릴 수 있다」이므로, 재는 것도 거동이 아니라 **선언의 형태**여야 한다.
 */
class CollectionLedgerSurfaceTest {
    /**
     * **D-6G2c-4 — 잠금을 놓는 길은 [RunStateDirectory.close] 하나다.** `release()` 가 인터페이스에
     * 공개돼 있던 동안 밖에서 잠금만 풀고 원장은 쓰기 가능한 채로 둘 수 있었고, 그 상태에는 이름이
     * 없었다(어느 사유 어휘에도 들지 않는다).
     */
    @Test
    fun `잠금에는 공개된 놓기가 없다 — 놓는 길은 디렉터리의 close 하나다`() {
        RunStateLock::class.members.map { it.name } shouldNotContain "release"

        val release = RunStateLock.Held::class.members.single { it.name == "release" }

        release.visibility shouldBe KVisibility.INTERNAL
        RunStateDirectory::class.members.map { it.name }.contains("close") shouldBe true
    }

    /**
     * **D-6G2c-19 (vr r5-t 저위 변이 ②) — 「이미 쓴 몫」에 기본값이 없다.** 있던 동안 seed 를 빼먹은
     * 원장이 한 줄로 지어졌고, 그 배선은 승인 상한을 매 기동 0 에서 다시 시작한다(D-6G-72). 기본값을
     * 되살리는 변이는 **거동을 바꾸지 않는다** — 오늘의 호출부가 전부 값을 넘기므로. 그래서 선언을
     * 잰다: 이 인자를 **생략할 수 있는가**.
     */
    @Test
    fun `상한 원장은 이미 쓴 몫을 생략할 수 없다`() {
        val constructor = requireNotNull(CallBudgetLedger::class.primaryConstructor)

        constructor.parameters
            .filter { it.name == "alreadySpent" }
            .map { it.isOptional } shouldBe listOf(false)
    }

    /**
     * **D-6G2c-19 (vr r5-t 저위 변이 ①) — 상한의 하루 경계는 KST 다.** 앞 판의 E2E 는 「오늘치를 다
     * 쓴 상태로 KST 00:30 기동 → 요청 0」만 쟀고, 그 단언은 구역을 UTC 로 바꿔도 참이다(UTC 로 보면
     * 그 시각은 어제 15:30 이고 seed 한 시도가 **어제의 하루**에 그대로 들어온다). 갈리는 자리는
     * 반대쪽이다: **어제 저녁의 호출이 오늘치에 섞이는가**.
     *
     * KST 로 세면 어제 20:00 는 어제치다 — 오늘치는 0 이고 오늘 상한이 그대로 남는다. UTC 로 세면
     * 「오늘」이 어제가 되고 그 하루는 어제 09:00 KST 부터라, 어제 저녁의 호출이 오늘치로 계상돼
     * 아무것도 부르지 못한다.
     */
    @Test
    fun `어제 저녁의 호출은 오늘치에 들지 않는다 — 하루 경계가 KST 다`() {
        val directory = Files.createTempDirectory("6g2c-budget-zone")
        val today = LocalDate.of(2026, 9, 24)
        val yesterdayEvening = at(today.minusDays(1), LocalTime.of(20, 0))
        val bootAt = at(today, LocalTime.of(0, 30))
        val seeded = RunStateDirectory(directory)
        repeat(DAILY_CAP) { seeded.attempts.append(pendingAt(yesterdayEvening)) }
        seeded.close()

        // 바로 위 형제와 같이 **닫는다**(PR #59 R) — 열어 둔 인스턴스가 잠금을 쥐면 같은 JVM 의 뒤
        // 기동이 조용히 `Busy` 가 되고, 그 어긋남은 이 test 가 아니라 다른 test 에서 드러난다.
        val reopened = RunStateDirectory(directory)
        val budget =
            try {
                seededBudget(reopened, DAILY_CAP, TOTAL_CAP, Clock { bootAt })
            } finally {
                reopened.close()
            }

        budget.spentToday shouldBe 0
        // 어제치는 총 상한에는 그대로 남는다 — 되감기는 것은 하루 몫뿐이다.
        budget.spentTotal shouldBe DAILY_CAP
        budget.consume(today, 1) shouldBe BudgetOutcome.Allowed
    }

    private fun at(
        day: LocalDate,
        time: LocalTime,
    ): Instant = ZonedDateTime.of(day, time, COLLECTION_BUDGET_ZONE).toInstant()

    private fun pendingAt(moment: Instant) =
        CollectionAttempt(
            noticeKey = null,
            axis = SourceEndpoint.OPENING_RESULT_LIST,
            outcome = AttemptOutcome.Succeeded,
            at = moment,
            kind = AttemptKind.PENDING,
            walk = null,
        )
}

/** 일 상한 — seed 한 어제치와 같은 수로 둔다(오늘치로 섞이면 한 호출도 남지 않는다). */
private const val DAILY_CAP = 4

/** 총 상한은 넉넉히 — 이 test 가 재는 것은 하루 경계뿐이다. */
private const val TOTAL_CAP = 1_000
