package bidvector.workflow.collection

import bidvector.procurement.CallSpend
import bidvector.procurement.SourceEndpoint
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
) {
    init {
        require(httpAttempts >= 0) { "HTTP 시도 수는 음수일 수 없다: $httpAttempts" }
    }
}

/** 시도의 결말 — 빈 응답은 **오류가 아니다**(정상 응답이고 항목이 없었다). 둘을 가른다. */
sealed interface AttemptOutcome {
    data object Succeeded : AttemptOutcome

    data object Empty : AttemptOutcome

    data class Failed(
        val code: String,
    ) : AttemptOutcome
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

    /** 시도한 (공고, 축) — 결말과 무관하다. 빈 응답도 「불렀다」이므로 다시 부르지 않는다. */
    fun attemptedAxes(): Map<NoticeKeyHash, Set<SourceEndpoint>> =
        attempts
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
