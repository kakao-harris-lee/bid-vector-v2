package bidvector.workflow.collection

import bidvector.procurement.CallSpend
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import java.time.Instant
import java.time.LocalDate

/**
 * 한 번의 조회 시도(D-6G-45) — **받은 것이 아니라 시도한 것**을 적는다.
 *
 * 두 구멍을 같은 기록으로 막는다. ① 이어 돌기가 원문 행의 존재로 판정하면 **빈 응답을 받은 축**은
 * 행이 없어 다음 실행이 영원히 다시 부른다. ② 호출 원장이 받은 페이지만 세면 **재시도·5xx·429·
 * 타임아웃**이 승인 상한 밖에서 나간다. 둘 다 「무엇을 받았나」가 아니라 「무엇을 시도했나」를
 * 물어야 답이 나온다.
 */
data class CollectionAttempt(
    /** 목록 축은 공고 단위가 아니다 — 슬롯 하나가 여러 공고를 낸다. 그 경우 `null`. */
    val noticeKey: NoticeKeyHash?,
    val axis: SourceEndpoint,
    val outcome: AttemptOutcome,
    val at: Instant,
    val httpAttempts: Int,
    val kind: AttemptKind = AttemptKind.HTTP,
) {
    init {
        require(httpAttempts >= 0) { "HTTP 시도 수는 음수일 수 없다: $httpAttempts" }
        require(kind == AttemptKind.HTTP || httpAttempts == 0) {
            "축 결말 줄은 호출이 아니다 — 상한에 계상되지 않는다"
        }
    }
}

/**
 * 원장의 두 줄 갈래 — **상한과 이어 돌기는 서로 다른 것을 묻는다.**
 *
 * [HTTP] 는 실제로 나간 호출 하나다(재시도마다 한 줄). 상한이 세는 것은 이것뿐이다.
 * [AXIS] 는 한 축의 조회가 **끝난 방식**이다 — 항목이 0 이었는지(빈 응답)는 봉투를 편 뒤에야
 * 알 수 있어 transport 관문이 답할 수 없다. 이어 돌기가 보는 것은 이것뿐이고, 호출이 아니므로
 * `http_attempts` 는 0 이다(한 파일 안에서 두 셈이 섞이지 않는다).
 */
enum class AttemptKind {
    HTTP,
    AXIS,
}

/** 시도의 결말 — 빈 응답은 **오류가 아니다**(정상 응답이고 항목이 없었다). 둘을 가른다. */
sealed interface AttemptOutcome {
    /** 끝난 답을 받았는가 — 다시 부를 이유가 없는 상태다. */
    val isSettled: Boolean

    data object Succeeded : AttemptOutcome {
        override val isSettled: Boolean = true
    }

    data object Empty : AttemptOutcome {
        override val isSettled: Boolean = true
    }

    data class Failed(
        val code: String,
    ) : AttemptOutcome {
        override val isSettled: Boolean = false
    }
}

/**
 * 읽어 온 시도 이력 — 두 물음에만 답한다. 파일 판독은 어댑터가 하고 이 타입은 값만 센다.
 */
class AttemptHistory(
    private val attempts: List<CollectionAttempt>,
) {
    /**
     * 승인 상한에 계상할 몫 — [since] 이후 전부와 [dayStart] 이후 오늘치. 오늘치는 총계의
     * 부분집합이라 [dayStart] 가 [since] 보다 이르면 같은 창을 두 번 세지 않도록 좁힌다.
     */
    fun spend(
        since: Instant,
        dayStart: Instant,
    ): CallSpend {
        val counted = attempts.filter { !it.at.isBefore(since) }
        val total = counted.sumOf { it.httpAttempts }
        val today = counted.filter { !it.at.isBefore(dayStart) }.sumOf { it.httpAttempts }
        return CallSpend(total = total, today = minOf(today, total))
    }

    /**
     * **다시 부르지 않을** (공고, 축)(D-6G-49) — 끝난 방식이 성공이거나 빈 응답인 것만이다.
     *
     * 실패·타임아웃·5xx·쿼터 거절은 **다시 부른다.** 한 번 실패한 축을 영구히 포기하면 그 결측이
     * 무작위가 아니게 된다 — 느린 응답·과부하 시간대에 몰린 공고만 빠지고, 그 행은 값 결측 제외로
     * 계수되어 사유 귀속까지 틀린다.
     */
    fun settledAxes(): Map<NoticeKeyHash, Set<SourceEndpoint>> =
        attempts
            .filter { it.kind == AttemptKind.AXIS && it.outcome.isSettled }
            .mapNotNull { attempt -> attempt.noticeKey?.let { it to attempt.axis } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, axes) -> axes.toSet() }

    val size: Int get() = attempts.size
}

/**
 * 시도 원장 — 저장소 **밖** 실행 상태다(마이그레이션 없음, D-6G-1). append-only 이고, 읽기는 한
 * 실행에 한 번이다.
 */
interface AttemptLedger {
    fun append(attempt: CollectionAttempt)

    fun read(): AttemptHistory
}

/** KST 하루의 시작 — 상한의 「오늘」은 실행 구역의 하루다(UTC 자정이 아니다). */
fun dayStartOf(
    day: LocalDate,
    zone: java.time.ZoneId,
): Instant = day.atStartOfDay(zone).toInstant()

/**
 * 절단 사유의 **원장 어휘** — `::class.simpleName` 을 쓰지 않는다. 리플렉션이라 모듈 경계 게이트가
 * 막기도 하지만, 더 나쁜 것은 클래스 이름이 영속 파일의 값이 되는 것이다: 타입 이름을 바꾸면 앞
 * 실행이 남긴 원장을 읽지 못한다. 소진 `when` 이라 새 사유를 더하면 컴파일이 여기를 가리킨다.
 */
fun truncationCodeOf(cause: TruncationCause): String =
    when (cause) {
        TruncationCause.MaxPages -> "MAX_PAGES"
        TruncationCause.RepeatedPage -> "REPEATED_PAGE"
        TruncationCause.QuotaExhausted -> "QUOTA_EXHAUSTED"
        TruncationCause.Timeout -> "TIMEOUT"
        TruncationCause.TransportFailure -> "TRANSPORT_FAILURE"
        TruncationCause.ServerError -> "SERVER_ERROR"
        TruncationCause.NotRetryable -> "NOT_RETRYABLE"
        TruncationCause.InputError -> "INPUT_ERROR"
        TruncationCause.Unclassified -> "UNCLASSIFIED"
        TruncationCause.StructureFailure -> "STRUCTURE_FAILURE"
        TruncationCause.SelfThrottled -> "SELF_THROTTLED"
        is TruncationCause.BudgetExhausted -> "BUDGET_EXHAUSTED_${cause.limit}"
    }

/** 시도의 결말 — 절단은 오류, 항목 0 은 빈 응답, 그 밖은 성공. 빈 응답은 오류가 아니다. */
fun attemptOutcomeOf(accounting: CollectionAccounting): AttemptOutcome =
    when {
        accounting.truncationCause != null -> {
            AttemptOutcome.Failed(truncationCodeOf(accounting.truncationCause!!))
        }

        accounting.received == 0 -> {
            AttemptOutcome.Empty
        }

        else -> {
            AttemptOutcome.Succeeded
        }
    }
