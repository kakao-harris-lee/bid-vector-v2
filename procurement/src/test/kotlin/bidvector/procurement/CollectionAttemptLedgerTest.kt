package bidvector.procurement

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** 나간 호출의 결말 줄 — 상한이 세지 않는다(의도 줄이 이미 세었다). */
private fun settledHttp(at: String) =
    CollectionAttempt(null, AXIS, AttemptOutcome.Succeeded, Instant.parse(at), AttemptKind.HTTP, walk = null)

private val AXIS: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL

/** 재호출 상한 — 이 test 가 재는 것은 셈의 규칙이지 운영 판이 아니다(D-6G2d-8 ⓒ). */
private const val RETRY_LIMIT = 3

private val KEY = "0".repeat(64)

private val WALK: Instant = Instant.parse("2026-09-24T01:00:00Z")

/** 한 줄 — 갈래와 어휘만 다르다. 시각은 셈에 쓰이지 않으므로(덧붙인 순서로 읽는다) 한 값으로 둔다. */
private fun line(
    kind: AttemptKind,
    outcome: AttemptOutcome,
    walk: Instant? = null,
) = CollectionAttempt(KEY, AXIS, outcome, WALK, kind, walk = walk)

/** 결말을 남기지 못한 호출 라운드 — 의도 줄과 호출 줄만 남는다(적재와 결말 사이에서 던졌다). */
private fun callRounds(count: Int): List<CollectionAttempt> =
    (1..count).flatMap {
        listOf(line(AttemptKind.PENDING, AttemptOutcome.Succeeded), line(AttemptKind.HTTP, AttemptOutcome.Succeeded))
    }

/** 나가려는 호출 한 줄 — 한 줄이 한 호출이다(D-6G-61 ①). */
private fun attempt(
    at: String,
    key: String? = null,
    axis: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL,
    outcome: AttemptOutcome = AttemptOutcome.Succeeded,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), AttemptKind.PENDING, walk = null)

/**
 * 축의 결말 줄 — 호출이 아니므로 상한에 계상되지 않는다(D-6G-49). 걷기의 이름은 **언제나** 실린다
 * (D-6G2d-4 ⓒ) — 결말 시각과 걷기 시각은 다를 수 있으므로 기본값을 결말 시각으로 접지 않는다.
 */
private fun settled(
    at: String,
    key: String?,
    axis: SourceEndpoint,
    outcome: AttemptOutcome,
    walk: String = at,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), AttemptKind.AXIS, walk = Instant.parse(walk))

private fun walked(
    received: Int,
    sourceTotal: Int,
) = CollectionAccounting(
    received = received,
    normalized = received,
    duplicate = 0,
    dropped = 0,
    dropReasons = emptyMap(),
    sourceTotal = sourceTotal,
    pagesFetched = 1,
    truncated = false,
    unknownFields = 0,
)

/** 절단으로 끝난 걷기 — 사유만 다르다. */
private fun truncated(cause: TruncationCause) =
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

/**
 * D-6G-45 — 상한이 세는 것은 **나간 호출**이다. 받은 페이지만 세면 재시도·5xx·429·타임아웃이
 * 승인 범위 밖에서 나가고, 원문 행의 존재로 이어 돌기를 판정하면 빈 응답 축이 영원히 다시 불린다.
 */
class CollectionAttemptLedgerTest {
    @Test
    fun `상한은 받은 페이지가 아니라 나간 호출을 센다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-24T01:00:00Z"),
                    attempt("2026-09-24T01:30:00Z"),
                    attempt("2026-09-24T02:00:00Z"),
                    // 결말 줄은 호출이 아니다 — 같은 호출을 두 번 세지 않는다.
                    CollectionAttempt(
                        null,
                        AXIS,
                        AttemptOutcome.Succeeded,
                        Instant.parse("2026-09-24T02:00:01Z"),
                        AttemptKind.HTTP,
                        walk = null,
                    ),
                ),
            )

        history.spend(Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 3
    }

    /**
     * **D-6G-69 (vr r5 M-1 probe P4) — 되감을 시작 시점이 없다.** 앞 판은 설정이 매 기동 주는
     * `budget-since` 이후만 셌고, 그 값을 1 초 뒤로 옮기면 총계와 오늘치가 **둘 다 0 으로** 되감겨
     * 승인 상한을 다 쓴 디렉터리에서 상한만큼 다시 나갈 수 있었다. 범위는 디렉터리 자신이다 —
     * 오래된 줄도 센다.
     */
    @Test
    fun `원장의 모든 의도 줄을 계상한다 — 되감을 시작 시점이 없다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-19T23:00:00Z"),
                    attempt("2026-09-24T01:00:00Z"),
                    attempt("2026-09-24T01:10:00Z"),
                ),
            )

        history.spend(Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 3
    }

    /** KST 자정과 UTC 자정 사이 아홉 시간 — UTC 로 세면 이 시도가 오늘치에서 빠진다. */
    @Test
    fun `오늘치의 경계는 KST 자정이다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-23T16:00:00Z"),
                    attempt("2026-09-23T17:00:00Z"),
                    attempt("2026-09-23T14:00:00Z"),
                ),
            )

        val spend = history.spend(dayStartOf(java.time.LocalDate.of(2026, 9, 24), KST))

        spend.total shouldBe 3
        spend.today shouldBe 2
    }

    /**
     * D-6G-49 — **성공과 빈 응답만** 다시 부르지 않는다. 실패·타임아웃·5xx·쿼터 거절은 다시
     * 부른다: 한 번 실패한 축을 영구히 포기하면 결측이 무작위가 아니게 되고(느린 시간대에 몰린
     * 공고만 빠진다) 그 행은 값 결측 제외로 계수되어 사유 귀속까지 틀린다.
     */
    @Test
    fun `다시 부르지 않는 축은 성공과 빈 응답뿐이다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(
                listOf(
                    settled("2026-09-24T01:00:00Z", key, SourceEndpoint.RESERVE_PRICE_DETAIL, AttemptOutcome.Empty),
                    settled("2026-09-24T01:00:01Z", key, SourceEndpoint.BASE_AMOUNT_DETAIL, AttemptOutcome.Succeeded),
                    settled(
                        "2026-09-24T01:00:02Z",
                        key,
                        SourceEndpoint.OPENING_COMPLETE,
                        AttemptOutcome.Failed("TIMEOUT"),
                    ),
                ),
            )

        val axes = history.axisResumptions(RETRY_LIMIT).getValue(key)

        axes
            .filterValues { it }
            .keys
            .map { it.name }
            .sorted() shouldContainExactly
            listOf(SourceEndpoint.BASE_AMOUNT_DETAIL.name, SourceEndpoint.RESERVE_PRICE_DETAIL.name)
    }

    /**
     * D-6G-58 — (공고, 축)에 줄이 여럿이면 **마지막**이 이긴다. 앞 실행이 실패로 닫은 축을 이번
     * 실행이 받으면 완료이고, 앞 실행이 받은 축을 이번 실행이 짧게 걸었으면 다시 불려야 한다.
     */
    @Test
    fun `축의 결말은 마지막 줄이 이긴다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(
                listOf(
                    settled("2026-09-24T01:00:00Z", key, SourceEndpoint.OPENING_COMPLETE, AttemptOutcome.Failed("X")),
                    settled("2026-09-24T02:00:00Z", key, SourceEndpoint.OPENING_COMPLETE, AttemptOutcome.Succeeded),
                    settled("2026-09-24T01:00:00Z", key, SourceEndpoint.BASE_AMOUNT_DETAIL, AttemptOutcome.Succeeded),
                    settled(
                        "2026-09-24T02:00:00Z",
                        key,
                        SourceEndpoint.BASE_AMOUNT_DETAIL,
                        AttemptOutcome.Failed("SHORT_WALK"),
                    ),
                ),
            )

        history.axisConclusions().getValue(key) shouldBe
            mapOf(
                SourceEndpoint.OPENING_COMPLETE to
                    AxisConclusion(AttemptOutcome.Succeeded, Instant.parse("2026-09-24T02:00:00Z")),
                SourceEndpoint.BASE_AMOUNT_DETAIL to
                    AxisConclusion(AttemptOutcome.Failed("SHORT_WALK"), Instant.parse("2026-09-24T02:00:00Z")),
            )
    }

    /**
     * D-6G-58 ⓒ — 원천이 총수를 말했는데 그만큼 받지 못하고 끝난 걷기는 **짧은 걷기**다. 빈
     * 페이지나 `NoData` 로 곱게 멈추면 절단 사유가 없어 앞 판은 이것을 성공·빈 응답으로 적었고,
     * 그 축은 다시 불리지 않았다(조용한 결측).
     */
    @Test
    fun `받은 수가 원천 총수보다 적으면 짧은 걷기다`() {
        attemptOutcomeOf(walked(received = 0, sourceTotal = 250)) shouldBe AttemptOutcome.Failed("SHORT_WALK")
        attemptOutcomeOf(walked(received = 100, sourceTotal = 250)) shouldBe AttemptOutcome.Failed("SHORT_WALK")
        attemptOutcomeOf(walked(received = 250, sourceTotal = 250)) shouldBe AttemptOutcome.Succeeded
        // 빈 응답이 끝난 답인 것은 원천 총수가 0 일 때뿐이다.
        attemptOutcomeOf(walked(received = 0, sourceTotal = 0)) shouldBe AttemptOutcome.Empty
    }

    /**
     * **D-6G2d-8 ⓒ — 절단 사유가 확정 실패와 일시 실패를 가른다.** 이어 돌기는 cursor 를 쓰지 않고 그
     * 축을 1쪽부터 다시 걷는다 — 그래서 백스톱(최대 페이지·같은 쪽 반복)은 다시 걸어도 같은 자리에서
     * 멈추는 **확정**이고, 입력 오류·비재시도 코드·미지 코드도 확정이다. 서버가 그 순간 무너뜨린 응답
     * (구조 붕괴)은 **일시**다: 이 저장소에서 HTTP 5xx 가 봉투 없이 와 이 사유가 된다(6G-2d 실측).
     * 확정으로 두면 일시적 5xx 한 번이 그 축을 영구히 버려 느린 시간대에 몰린 공고만 빠진다.
     */
    @Test
    fun `절단 사유가 확정 실패와 일시 실패를 가른다`() {
        attemptOutcomeOf(truncated(TruncationCause.StructureFailure)) shouldBe
            AttemptOutcome.Failed("STRUCTURE_FAILURE")
        attemptOutcomeOf(truncated(TruncationCause.ServerError)) shouldBe AttemptOutcome.Failed("SERVER_ERROR")
        attemptOutcomeOf(truncated(TruncationCause.Timeout)) shouldBe AttemptOutcome.Failed("TIMEOUT")
        attemptOutcomeOf(truncated(TruncationCause.RepeatedPage)) shouldBe AttemptOutcome.Failed("REPEATED_PAGE")
        attemptOutcomeOf(truncated(TruncationCause.Unclassified)) shouldBe AttemptOutcome.Failed("UNCLASSIFIED")
        attemptOutcomeOf(truncated(TruncationCause.MaxPages)) shouldBe AttemptOutcome.FinalFailure("MAX_PAGES")
        attemptOutcomeOf(truncated(TruncationCause.InputError)) shouldBe AttemptOutcome.FinalFailure("INPUT_ERROR")
        attemptOutcomeOf(truncated(TruncationCause.NotRetryable)) shouldBe AttemptOutcome.FinalFailure("NOT_RETRYABLE")
    }

    /**
     * **D-6G2d-16 — 관문 거부는 실패가 아니다.** 우리 속도 보호·우리 승인 상한·원천의 쿼터 거절은
     * 전송 앞에서 접히므로 그 공고·축에 대한 **관측이 아니다**. 어휘로 갈라 두면 재호출 상한이 그것을
     * 세는 길이 구조적으로 닫힌다(코드 문자열로 되읽어 분류하지 않는다).
     */
    @Test
    fun `관문 거부는 넷째 어휘로 나가고 정착이 아니다`() {
        attemptOutcomeOf(truncated(TruncationCause.SelfThrottled)) shouldBe AttemptOutcome.Refused("SELF_THROTTLED")
        attemptOutcomeOf(truncated(TruncationCause.QuotaExhausted)) shouldBe AttemptOutcome.Refused("QUOTA_EXHAUSTED")
        attemptOutcomeOf(truncated(TruncationCause.BudgetExhausted(BudgetLimit.DAILY))) shouldBe
            AttemptOutcome.Refused("BUDGET_EXHAUSTED_DAILY")
        AttemptOutcome.Refused("QUOTA_EXHAUSTED").isSettled shouldBe false
    }

    /**
     * **cr r4 ② — 결말 없이 끝난 호출도 상한에 센다.** 걷기는 받은 뒤 원문을 적재하고 그다음 결말을
     * 적는다(D-6G-58 ⓑ). 그 사이에서 던지면 일시 실패 결말이 **하나도 없어**, 앞 판은 그 축을 매 실행
     * 상한 없이 다시 불렀다. 세는 단위는 나갔고 결말을 남기지 못한 **호출 하나**([AttemptKind.HTTP]).
     *
     * 등식으로 잠근다 — 상한 미만에서는 다시 부르고, 닿으면 접는다.
     */
    @Test
    fun `결말 없는 호출이 상한만큼 쌓이면 그 축을 접는다`() {
        val twoCalls = callRounds(2)
        val threeCalls = callRounds(3)

        AttemptHistory(twoCalls).axisResumptions(RETRY_LIMIT)[KEY]?.get(AXIS) shouldBe false
        AttemptHistory(threeCalls).axisResumptions(RETRY_LIMIT)[KEY]?.get(AXIS) shouldBe true
    }

    /**
     * 의도 줄만 있는 꼬리는 세지 않는다 — **나가지 않은 호출은 관측이 아니다**. 그 축은 상한을 쓰지
     * 않고 다시 불린다(보수적인 방향: 호출이 실제로 나갔는지가 기준이다).
     */
    @Test
    fun `보낸 적 없는 의도 줄은 상한에 세지 않는다`() {
        val intentsOnly = List(RETRY_LIMIT * 2) { line(AttemptKind.PENDING, AttemptOutcome.Succeeded) }

        AttemptHistory(intentsOnly).axisResumptions(RETRY_LIMIT)[KEY]?.get(AXIS) shouldBe false
    }

    /**
     * 같은 라운드를 두 번 세지 않는다 — 일시 실패로 **끝난** 라운드의 호출 줄은 그 결말이 이미 세었다.
     * 상한 3 에서 「호출+실패 결말」 두 라운드는 2 를 쓰므로 아직 접히지 않는다(호출까지 같이 세면 4 가
     * 되어 접힌다).
     */
    @Test
    fun `일시 실패로 끝난 라운드의 호출 줄은 다시 세지 않는다`() {
        val concluded =
            (1..2).flatMap {
                listOf(
                    line(AttemptKind.PENDING, AttemptOutcome.Succeeded),
                    line(AttemptKind.HTTP, AttemptOutcome.Succeeded),
                    line(AttemptKind.AXIS, AttemptOutcome.Failed("SHORT_WALK"), walk = WALK),
                )
            }

        AttemptHistory(concluded).axisResumptions(RETRY_LIMIT)[KEY]?.get(AXIS) shouldBe false
    }

    /**
     * **D-6G2d-19 (vr r1 M-2) — 상한은 마지막 정착 뒤부터 센다.** 원장은 append-only 라 한 번 끝난
     * 축의 앞 실패 줄이 그대로 남는다. 그것까지 세면 「성공했다가 일시적으로 실패한」 축이 새 예산
     * 없이 곧바로 확정된다 — 정착이 앞의 증거를 무효로 만든다. 등식으로 잠근다.
     */
    @Test
    fun `재호출 상한은 마지막 정착 뒤의 일시 실패만 센다`() {
        val key = "0".repeat(64)
        val axis = SourceEndpoint.OPENING_COMPLETE
        val failed = settled("2026-09-24T01:00:00Z", key, axis, AttemptOutcome.Failed("TIMEOUT"))
        val succeeded = settled("2026-09-24T02:00:00Z", key, axis, AttemptOutcome.Succeeded)

        // 정착 앞의 실패가 상한을 채웠어도, 정착 뒤의 실패 하나면 다시 부른다.
        val revived = List(RETRY_LIMIT) { failed } + succeeded + failed
        AttemptHistory(revived).axisResumptions(RETRY_LIMIT).getValue(key) shouldBe mapOf(axis to false)

        // 정착 **뒤**의 실패가 상한에 닿으면 접는다.
        val exhausted = List(RETRY_LIMIT) { failed } + succeeded + List(RETRY_LIMIT) { failed }
        AttemptHistory(exhausted).axisResumptions(RETRY_LIMIT).getValue(key) shouldBe mapOf(axis to true)
    }

    /**
     * **D-6G2d-22 ② · 30 — 순서는 `at` 값이 아니라 원장의 덧붙인 순서다.** 시계가 뒤로 간 실행이 있으면
     * 두 기준이 갈린다: 아래 원장은 정착 줄의 `at` 이 **가장 늦고** 실패 줄이 그보다 이르다. `at` 으로
     * 정렬하면 정착이 마지막이 되어 「다시 부르지 않는다」가 되지만, 실제로 마지막에 일어난 일은 실패다.
     * 덧붙인 순서가 사건 순서이고, 추출 쪽의 「마지막 줄이 이긴다」(D-6G-58)와도 같은 기준이다.
     */
    @Test
    fun `순서는 at 값이 아니라 덧붙인 순서다`() {
        val key = "0".repeat(64)
        val axis = SourceEndpoint.OPENING_COMPLETE
        val settledLate = settled("2026-09-24T09:00:00Z", key, axis, AttemptOutcome.Succeeded)
        val failedEarly = settled("2026-09-24T01:00:00Z", key, axis, AttemptOutcome.Failed("TIMEOUT"))
        val failedEarlier = settled("2026-09-24T02:00:00Z", key, axis, AttemptOutcome.Failed("TIMEOUT"))

        // 덧붙인 순서: 정착 → 실패 → 실패. `at` 정렬이면 정착이 마지막이 되어 `true` 가 된다.
        val history = AttemptHistory(listOf(settledLate, failedEarly, failedEarlier))

        history.axisResumptions(RETRY_LIMIT).getValue(key) shouldBe mapOf(axis to false)
    }

    /** 거부가 상한만큼 쌓여도 그 축은 미정착이다 — 세는 것은 실제로 나간 호출의 일시 실패뿐이다. */
    @Test
    fun `관문 거부는 재호출 상한에 세지 않는다`() {
        val key = "0".repeat(64)
        val axis = SourceEndpoint.OPENING_COMPLETE
        val refused = settled("2026-09-24T01:00:00Z", key, axis, AttemptOutcome.Refused("BUDGET_EXHAUSTED_DAILY"))

        AttemptHistory(List(RETRY_LIMIT + 1) { refused }).axisResumptions(RETRY_LIMIT).getValue(key) shouldBe
            mapOf(axis to false)
    }

    /** 확정 실패는 다시 부르지 않고, 일시 실패는 상한까지 다시 부른다 — 결말 하나가 그 답을 정한다. */
    @Test
    fun `확정 실패는 한 줄로도 이어 돌기에서 빠진다`() {
        val key = "0".repeat(64)
        val axis = SourceEndpoint.OPENING_COMPLETE
        val final = settled("2026-09-24T01:00:00Z", key, axis, AttemptOutcome.FinalFailure("MAX_PAGES"))
        val transient = settled("2026-09-24T01:00:00Z", key, axis, AttemptOutcome.Failed("STRUCTURE_FAILURE"))

        AttemptHistory(listOf(final)).axisResumptions(RETRY_LIMIT).getValue(key) shouldBe mapOf(axis to true)
        AttemptHistory(listOf(transient)).axisResumptions(RETRY_LIMIT).getValue(key) shouldBe mapOf(axis to false)
        // 상한에 닿으면 일시 실패도 확정으로 접는다 — 같은 축을 매 실행 다시 걸지 않는다.
        AttemptHistory(List(RETRY_LIMIT) { transient }).axisResumptions(RETRY_LIMIT).getValue(key) shouldBe
            mapOf(axis to true)
    }

    /** HTTP 줄은 이어 돌기가 보지 않는다 — 나간 호출이지 축의 결말이 아니다. */
    @Test
    fun `HTTP 시도 줄은 이어 돌기에 들지 않는다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(listOf(attempt("2026-09-24T01:00:00Z", key, SourceEndpoint.OPENING_COMPLETE)))

        // 줄은 있으나 결말이 없다 — 다시 부른다(D-6G2d-8 ⓑ). 「없음」이 아니라 `false` 다.
        history.axisResumptions(RETRY_LIMIT).getValue(key) shouldBe
            mapOf(SourceEndpoint.OPENING_COMPLETE to false)
        history.spend(Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 1
    }

    /** 목록 축은 공고 단위가 아니다 — 이어 돌기 대상이 아니지만 상한은 센다. */
    @Test
    fun `공고 키 없는 시도는 이어 돌기에 들지 않는다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-24T01:00:00Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                    attempt("2026-09-24T01:00:01Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                    attempt("2026-09-24T01:00:02Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                    attempt("2026-09-24T01:00:03Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                ),
            )

        history.axisResumptions(RETRY_LIMIT).keys.shouldBeEmpty()
        history.spend(Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 4
    }
}
