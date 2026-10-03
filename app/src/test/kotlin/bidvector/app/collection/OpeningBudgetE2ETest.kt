package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * M6/6G E2E — **얼마나·언제 부르는가**. 승인 호출 상한(D-6G-11·45·47·56), 동시 실행 잠금
 * (D-6G-57), 이어 돌기와 쪽 중간 절단(D-6G-58)을 출하 조립에서 잰다.
 *
 * 이 축의 기준은 **원장 밖**에 있다(D-6G-56) — mock 서버가 실제로 받은 요청 수다. 네 라운드 동안
 * 원장이 센 것을 원장의 합으로 재어 덜 센 것을 한 번도 보지 못했다.
 *
 * 무엇을 부르는가(표본·원문·조립 타입)는 [OpeningCollectionE2ETest] 가 진다.
 */
class OpeningBudgetE2ETest {
    companion object {
        /** 원장 합과 **다른 값** — 상한과 같으면 「상한만큼만 셌다」는 구현도 통과한다(D-6G-56). */
        private const val CAP_UNRELATED_TO_LEDGER = 97

        /** 오늘치 경계를 재는 자리의 일 상한 — 이만큼 써 둔 실행 상태로 KST 00:30 에 기동한다. */
        private const val DAILY_CAP = 4

        /** 목록 둘 · 예비가격 하나 · 개찰완료 1쪽 — 여기까지가 넷이고 2쪽이 거부된다(D-6G-58). */
        private const val CAP_CUTS_SECOND_PAGE = 4

        /** 미리 깔아 두는 시도의 시각 — 기동 시각보다 조금 앞이면 같은 KST 하루에 든다. */
        private const val SEED_BACKDATE_SECONDS = 60L

        private val today: LocalDate = LocalDate.now(OPENING_DATE_ZONE)

        private val e2e = OpeningCollectionE2EHarness()

        @AfterAll
        @JvmStatic
        fun stop() {
            e2e.stop()
        }
    }

    /**
     * **D-6G-58 — 한 쪽만 받고 끊긴 축은 완료가 아니다.** 앞 판은 이어 돌기를 **원문 행의 존재**로
     * 판정해, 잘린 1쪽의 행이 남아 있으면 그 축을 영영 다시 부르지 않았다 — 그 공고는 참가자 일부만
     * 실린 채 스냅숏에 들어간다(조용한 결측). 지금은 원장이 정하고, `SHORT_WALK` 은 미완이다.
     */
    @Test
    fun `한 쪽만 받고 끊긴 축은 다음 기동이 다시 걷는다 — 최종 행 수가 전 참가자와 같다`() {
        val nonce = "SHORTWALK"

        val (_, first) =
            e2e.bootAndRun(
                emptyMap(),
                nonce = nonce,
                openingCompletePageSize = OPENING_COMPLETE_PAGE_SIZE,
                failOpeningCompleteSecondPageOnce = true,
            )
        val calledFirst = first.openingCompleteNotices.toSet()

        // 끊긴 축은 원장에 **미완**으로 적힌다 — 그 줄이 다음 기동의 입력이다.
        attemptLinesOf(e2e.runStateDir).any {
            it.contains("\"axis\":\"OPENING_COMPLETE\"") && it.contains("FAILED:")
        } shouldBe true

        val (_, second) =
            e2e.bootAndRun(
                emptyMap(),
                nonce = nonce,
                openingCompletePageSize = OPENING_COMPLETE_PAGE_SIZE,
                reuseRunState = true,
            )

        // 끊긴 그 공고를 다시 부른다(끝난 축은 다시 부르지 않는다).
        second.openingCompleteNotices.toSet().shouldNotBeEmpty()
        // 원문은 append-only 라 잘린 1쪽의 행도 **남는다** — 그것을 지우는 것이 답이 아니다.
        e2e.query(allOpeningRowsSql(nonce)) { it.getInt(1) } shouldBeGreaterThan calledFirst.size * BIDDERS_PER_NOTICE
        // 추출이 쓰는 것은 (공고, 축)마다 **마지막 걷기**다 — 그 수가 전 참가자와 같다.
        e2e.query(latestWalkOpeningRowsSql(nonce)) { it.getInt(1) } shouldBe calledFirst.size * BIDDERS_PER_NOTICE
    }

    /**
     * D-6G-58 — 끊는 것이 5xx 가 아니라 **상한 거부**여도 같다. 쪽 단위로 결정적으로 짓는다:
     * 목록 둘 → 표본 첫 공고의 예비가격 하나 → 개찰완료 1쪽까지가 넷이고, 상한을 넷으로 두면
     * **2쪽이 거부된다**. 기준은 여기서도 mock 이 받은 요청 수다 — 정확히 상한만큼이어야 한다.
     */
    @Test
    fun `상한이 쪽 중간에서 끊어도 그 축은 미완이고 다음 기동이 다시 걷는다`() {
        val nonce = "CAPCUT"
        val capped =
            mapOf(
                "bidvector.opening-collection.calls-per-day" to CAP_CUTS_SECOND_PAGE.toString(),
                "bidvector.opening-collection.calls-total" to CAP_CUTS_SECOND_PAGE.toString(),
            )

        val (_, first) = e2e.bootAndRun(capped, nonce = nonce, openingCompletePageSize = OPENING_COMPLETE_PAGE_SIZE)

        first.requestCount() shouldBe CAP_CUTS_SECOND_PAGE
        val cut = first.openingCompleteNotices.single()
        // **1쪽까지만 적재됐다.** 이 값이 전 참가자와 같으면 상한이 쪽 **사이**에서 끊지 않은 것이라
        // 이 test 가 재려던 자리가 사라진다 — 그때는 초록이 아니라 붉어야 한다.
        e2e.query(allOpeningRowsForNoticeSql(cut)) { it.getInt(1) } shouldBe OPENING_COMPLETE_PAGE_SIZE
        attemptLinesOf(e2e.runStateDir).any {
            it.contains("\"axis\":\"OPENING_COMPLETE\"") && it.contains("BUDGET_EXHAUSTED")
        } shouldBe true

        val (_, second) =
            e2e.bootAndRun(
                mapOf("bidvector.opening-collection.calls-total" to "1000"),
                nonce = nonce,
                openingCompletePageSize = OPENING_COMPLETE_PAGE_SIZE,
                reuseRunState = true,
            )

        second.openingCompleteNotices shouldContain cut
        // 잘린 앞 걷기의 쪽은 원문에 **남고**(append-only), 추출이 쓰는 마지막 걷기만 전 참가자다.
        e2e.query(allOpeningRowsForNoticeSql(cut)) { it.getInt(1) } shouldBe
            OPENING_COMPLETE_PAGE_SIZE + BIDDERS_PER_NOTICE
        e2e.query(latestWalkOpeningRowsForNoticeSql(cut)) { it.getInt(1) } shouldBe BIDDERS_PER_NOTICE
    }

    /**
     * **D-6G-56 — 진실의 출처를 원장 밖에 둔다.** 네 라운드 동안 test 는 원장이 센 것을 **원장의
     * 합**으로 쟀고, 그래서 덜 센 것을 한 번도 보지 못했다. 여기서 기준은 **mock 서버가 실제로 받은
     * 요청 수**다 — 429 로 끊긴 것도, 재시도도, 상세·A값·기초금액도 전부 들어간다.
     *
     * 상한은 원장 합과 **다른 값**으로 둔다. 상한과 같으면 「상한만큼만 셌다」는 구현도 통과한다.
     */
    @Test
    fun `출하 조립이 낸 요청 수가 원장의 HTTP 줄 수와 같다 — 두 번 기동해도`() {
        val cap =
            mapOf(
                "bidvector.opening-collection.calls-per-day" to CAP_UNRELATED_TO_LEDGER.toString(),
                "bidvector.opening-collection.calls-total" to CAP_UNRELATED_TO_LEDGER.toString(),
            )

        val (_, first) =
            e2e.bootAndRun(
                e2e.censusSample() + cap,
                nonce = "GROUND",
                throttleOnce = setOf("getOpengResultListInfoCnstwk"),
            )
        val firstRequests = first.requestCount()

        firstRequests shouldBeGreaterThan 0
        firstRequests shouldBe e2e.httpLines()
        // 의도 줄과 결말 줄은 한 벌이다 — 한쪽만 적히면 상한과 원장이 갈린다.
        e2e.pendingLines() shouldBe e2e.httpLines()

        val (_, second) = e2e.bootAndRun(e2e.censusSample() + cap, nonce = "GROUND", reuseRunState = true)

        second.requestCount() shouldBe e2e.httpLines() - firstRequests
        e2e.pendingLines() shouldBe e2e.httpLines()
    }

    /**
     * D-6G-56 — 상한의 「오늘」은 **KST 하루**다. 오늘치를 다 쓴 실행 상태로 KST 00:30 에 기동하면
     * 한 요청도 나가지 않아야 한다. 하루 경계를 UTC 로 잡으면 이 시각은 아직 「어제」라 오늘치가
     * 0 으로 되살아나고, 그 아홉 시간 동안 일 상한이 아무것도 막지 못한다.
     */
    @Test
    fun `오늘치를 다 쓴 실행 상태로 KST 00시 30분에 기동하면 한 요청도 나가지 않는다`() {
        val bootAt = ZonedDateTime.of(today, LocalTime.of(0, 30), COLLECTION_BUDGET_ZONE).toInstant()
        e2e.freshRunStateDir()
        seedSpentCallsAt(e2e.runStateDir, bootAt.minusSeconds(SEED_BACKDATE_SECONDS), DAILY_CAP)

        val (exitCodes, mock) =
            e2e.bootAndRun(
                mapOf(
                    "bidvector.opening-collection.calls-per-day" to DAILY_CAP.toString(),
                    "bidvector.opening-collection.calls-total" to "1000",
                ),
                reuseRunState = true,
                now = bootAt,
            )

        mock.requestCount() shouldBe 0
        exitCodes shouldContainExactly listOf(CollectionExitCode.INCOMPLETE.value)
    }

    /**
     * D-6G-45 — 상한은 **시도 원장에서 이어진다.** 매 기동 0 에서 시작하면 승인 총 상한이 3~4일에
     * 걸친 여러 실행을 덮지 못한다. 첫 실행이 쓴 만큼을 총 상한으로 걸고 **같은 실행 상태**로 다시
     * 기동해, 두 번째가 한 호출도 내지 않는지 본다.
     */
    @Test
    fun `두 번 기동하면 시도 원장의 합이 상한에 누적된다`() {
        // **예산 시작 시점을 두 기동에 같게 못 박는다** — 기본값(`now()`)이면 두 번째 기동의
        // 시작 시점이 첫 기동의 시도보다 뒤라 그 시도들이 계상에서 빠진다.
        e2e.bootAndRun(e2e.censusSample(), nonce = "ACCUM")
        val spent = e2e.pendingLines()

        val (exitCodes, second) =
            e2e.bootAndRun(
                e2e.censusSample() +
                    mapOf(
                        "bidvector.opening-collection.calls-per-day" to spent.toString(),
                        "bidvector.opening-collection.calls-total" to spent.toString(),
                    ),
                nonce = "ACCUM",
                reuseRunState = true,
            )

        spent shouldBeGreaterThan 0
        exitCodes shouldContainExactly listOf(CollectionExitCode.INCOMPLETE.value)
        second.listCalls.shouldBeEmpty()
        second.detailCallCount() shouldBe 0
    }

    /**
     * D-6G-45 — 원장이 세는 것은 **나간 호출**이다. 429 는 재시도 대상이라(`isRetryableStep`) 한 번
     * 더 나가는데, 받은 페이지만 세면 그 호출이 승인 상한 밖에 남는다.
     */
    @Test
    fun `재시도로 나간 호출도 시도 원장에 실린다`() {
        val (_, mock) =
            e2e.bootAndRun(emptyMap(), nonce = "RETRY", throttleOnce = setOf("getOpengResultListInfoCnstwk"))

        val listAttempts =
            attemptLinesOf(e2e.runStateDir).count {
                it.contains("\"axis\":\"OPENING_RESULT_LIST\"") && it.contains("\"kind\":\"HTTP\"")
            }
        // mock 은 429 로 끊은 요청을 `listCalls` 에 세지 않는다 — 원장에는 그것까지 한 줄로 남는다.
        // 나간 호출을 세는 자리와 받은 페이지를 세는 자리가 **다르다**는 것이 이 값의 뜻이다.
        listAttempts shouldBe mock.listCalls.size + 1
    }

    /**
     * D-6G-45 — **빈 응답도 시도다.** 원문 행의 존재로만 이어 돌기를 판정하면 항목이 하나도 오지
     * 않은 축은 다음 실행이 영원히 다시 부른다 — 그 공고만큼 상한이 매 실행 새로 탄다.
     */
    @Test
    fun `빈 응답을 받은 축은 다음 기동에서 다시 부르지 않는다`() {
        e2e.bootAndRun(e2e.censusSample(), nonce = "EMPTY")
        val firstDetailCalls = attemptLinesOf(e2e.runStateDir).count { it.contains("\"outcome\":\"EMPTY\"") }

        val (_, second) = e2e.bootAndRun(e2e.censusSample(), nonce = "EMPTY", reuseRunState = true)

        // 첫 실행에 빈 응답이 있었고(공사 마지막 순번), 두 번째는 그 축을 한 번도 부르지 않는다.
        firstDetailCalls shouldBeGreaterThan 0
        second.detailCallCount() shouldBe 0
    }

    /**
     * D-6G-42 M-3 · D-6G-57 · **D-6G2c-1** — 겹쳐 도는 두 실행은 같은 표본을 두 번 부르고 두 상한
     * 회계가 서로의 호출을 보지 못한다. 잠금은 **실행 상태 디렉터리**에 걸린다(갈래별 DB advisory
     * lock 은 범위가 달라 같은 것을 지키지 못했다 — vr r4 H-2). 출하 조립이 실제로 아무것도 부르지
     * 않고 끝나는지 **mock 이 받은 요청 수**로 잰다.
     *
     * 보유자는 **별 프로세스**다(cr r5 M-3). 같은 JVM 에서 두 번째로 잠그면 잡히는 것은
     * `OverlappingFileLockException` 이고 그것은 JVM 안의 사실이라, 재려던 것(두 **프로세스**)을
     * 재지 못했다 — `tryAcquire` 를 JVM 안 맵 가드로 바꾸는 변이가 그 판을 통과했다.
     */
    @Test
    fun `다른 프로세스가 잠금을 들고 있으면 아무것도 부르지 않고 끝난다`() {
        val directory = e2e.freshRunStateDir()

        RunStateLockHolder.hold(directory).use {
            val (exitCodes, mock) = e2e.bootAndRun(emptyMap(), reuseRunState = true)

            exitCodes shouldContainExactly listOf(CollectionExitCode.ALREADY_RUNNING.value)
            mock.requestCount() shouldBe 0
            e2e.capturedLog() shouldContain "opening-collection skipped reason=ALREADY_RUNNING"
        }
    }

    /**
     * 출하 조립이 표본을 **파일로** 확정한다(D-6G-39) — 다음 실행이 다시 뽑지 못하게 하는 것은 이
     * 파일이다. 파일이 없으면 「결과를 보기 전에 확정했다」는 실행 로그의 주장일 뿐이다.
     */
    @Test
    fun `표본 목록이 저장소 밖 파일로 확정된다 — 층마다 목표만큼`() {
        e2e.bootAndRun(emptyMap())

        val lines = Files.readString(e2e.runStateDir.resolve("sample-list.tsv")).trimEnd('\n').lines()
        lines shouldHaveSize TARGET_PER_STRATUM * DIVISIONS
        val hashes = lines.map { it.substringBefore('\t') }
        hashes shouldContainExactly hashes.sorted()
        lines.map { it.split('\t')[1] }.toSet() shouldBe setOf("CONSTRUCTION", "SERVICE")
        val captured = e2e.capturedLog()
        captured shouldContain "sampled=${lines.size} "
    }

    @Test
    fun `호출 상한에 닿으면 멈추고 종료 코드가 미완이며 남은 축은 다음 실행이 받는다`() {
        val (exitCodes, mock) =
            e2e.bootAndRun(
                mapOf(
                    // 일 상한만 낮춘다 — 총 상한을 같이 낮추면 TOTAL 이 먼저 물어 사유가 바뀐다.
                    "bidvector.opening-collection.calls-per-day" to "6",
                    "bidvector.opening-collection.calls-total" to "1000",
                ),
                nonce = "HALFWAY",
            )

        exitCodes shouldContainExactly listOf(CollectionExitCode.INCOMPLETE.value)
        val captured = e2e.capturedLog()
        captured shouldContain "opening-collection halted budgetLimit=DAILY"
        // 상한을 넘겨 쓰지 않는다. **정확한 수를 고정하지 않는다** — 표본 순서가 공고 키 해시로
        // 정해지고 mock 의 번호에 실행마다 다른 표식이 들어가, 먼저 뽑히는 층(공사 4축·용역 3축)이
        // 실행마다 달라진다. 계약은 「넘겨 쓰지 않는다」이지 「정확히 6 이다」가 아니다.
        (mock.listCalls.size + mock.detailCallCount()) shouldBeLessThanOrEqual 6

        // **반쪽 공고가 영구히 남지 않는다**(D-6G-29 ④의 취지). 관문이 호출 하나씩 세므로 상한
        // 경계가 공고 한가운데에 떨어질 수 있다 — 옛 걸음 단위 예산은 그 자리를 공고 단위로
        // 막았지만, 그 대가로 재시도가 상한 밖에 있었다. 지금은 거부된 축이 **끝나지 않은 축**으로
        // 남아(D-6G-49) 다음 실행이 그것부터 받는다. 잰다: 상한을 풀고 같은 실행 상태로 다시 돌면
        // 받다 만 공고의 남은 축이 채워진다.
        val touched = (mock.reservePriceNotices + mock.openingCompleteNotices + mock.baseAmountNotices).toSet()
        // 같은 nonce·같은 실행 상태로 다시 돈다 — 번호가 같아야 「그 공고의 남은 축」을 잴 수 있다.
        val (_, second) = e2e.bootAndRun(emptyMap(), nonce = "HALFWAY", reuseRunState = true)
        touched.forEach { notice ->
            (mock.reservePriceNotices + second.reservePriceNotices) shouldContain notice
            (mock.openingCompleteNotices + second.openingCompleteNotices) shouldContain notice
            (mock.baseAmountNotices + second.baseAmountNotices) shouldContain notice
        }
    }

    /**
     * **D-6G2d-41 비용 공시** — append 하나가 fsync **셋**을 부른다(원장 · staged 장부 · 디렉터리 항목).
     * 그것이 이 slice 가 택한 값이다: 장부가 원장보다 먼저 굳으면 정전 뒤 그 디렉터리는 영구 거부되고,
     * 출구는 폐기(= 승인 상한을 0 에서 다시 시작)다.
     *
     * **문턱을 걸지 않는다** — 기계·파일시스템마다 한 자리 수가 다르고, 문턱은 그 차이에서 붉어지기만
     * 한다. 재는 것은 크기의 자리수이고, 한 줄로 **공시**한다. 묶는 길(결말 단위)은
     * `OPEN-6G2D-FSYNC-BATCHING` 이다 — 묶으면 크래시 창이 그만큼 넓어지므로 값을 보고 정한다.
     */
    @Test
    fun `원장 append 의 내구 비용을 공시한다`() {
        val root = Files.createTempDirectory("6g2d-append-cost")
        val runState = RunStateDirectory(root)
        val started = System.nanoTime()
        repeat(APPEND_COST_LINES) { runState.attempts.append(costProbeLine()) }
        val elapsedMicros = (System.nanoTime() - started) / 1_000
        runState.close()

        println(
            "run-state append durable lines=$APPEND_COST_LINES " +
                "totalMicros=$elapsedMicros perAppendMicros=${elapsedMicros / APPEND_COST_LINES}",
        )
        attemptLinesOf(root) shouldHaveSize APPEND_COST_LINES
    }

    /** 비용 공시용 한 줄 — 의도 줄이라 걷기를 갖지 않는다(D-6G2d-4 ⓒ). */
    private fun costProbeLine() =
        CollectionAttempt(
            noticeKey = null,
            axis = SourceEndpoint.OPENING_RESULT_LIST,
            outcome = AttemptOutcome.Succeeded,
            at = COST_PROBE_AT,
            kind = AttemptKind.PENDING,
            walk = null,
        )
}

/** 공시에 쓰는 줄 수 — 자리수를 보는 데 충분하고 test 시간을 늘리지 않는 값이다. */
private const val APPEND_COST_LINES = 50

private val COST_PROBE_AT: Instant = Instant.parse("2026-06-17T02:00:00Z")
