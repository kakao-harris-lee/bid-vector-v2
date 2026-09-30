package bidvector.procurement

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
    /**
     * 공고 키 해시의 **hex 문자열** — 목록 축은 공고 단위가 아니라 `null` 이다(슬롯 하나가 여러
     * 공고를 낸다). 값 타입이 아니라 문자열인 이유: 그 타입을 만드는 일은 sha256 계산이고 도메인
     * 모듈은 `java.security` 를 보지 않는다(architecture 게이트). 해시를 **짓는** 자리는 workflow
     * 하나이고, 도메인은 이미 지어진 값을 나르기만 한다.
     */
    val noticeKey: String?,
    val axis: SourceEndpoint,
    val outcome: AttemptOutcome,
    val at: Instant,
    val kind: AttemptKind,
    /**
     * **이 결말이 가리키는 걷기**(D-6G-68) — 그 걷기의 관측 시각이고, 한 걷기의 모든 쪽이 같은 값을
     * 단다. [AttemptKind.AXIS] 줄만 갖고, AXIS 줄은 **언제나** 갖는다(D-6G2d-4 ⓒ — `init` 이 양방향으로
     * 요구한다). 빈 응답도 걷기의 이름은 있다: 행을 남기지 않았다는 것은 결말 어휘([AttemptOutcome.Empty])
     * 가 말한다. 앞 판은 그 둘을 `null` 하나로 접었고, 그래서 **걷기를 모르는 옛 줄**과 **빈 응답**이
     * 같은 값이 되어 축이 통째로 빠진 완료 행이 나왔다(vr r5-t probe W7).
     *
     * **기본값이 없다**(cr r5-t L-1). AXIS 줄을 쓰는 새 자리가 인자를 잊으면 그 축이 조용히 「0 행」이
     * 되는 것이 아니라 컴파일이 깨진다.
     *
     * 이 칸이 없던 동안 추출은 「원문 행 중 가장 늦은 시각」으로 걷기를 **짐작**했다. 그러면 셋이
     * 조용히 틀린다: 빈 응답으로 끝난 재걷기는 행을 남기지 않아 앞의 잘린 걷기가 마지막으로 보이고,
     * 추출의 관측 창은 원장에 걸리지 않아 창 밖 재걷기가 보이지 않으며, 벽시계가 뒤로 가면 걷기의
     * 순서가 뒤집힌다. 셋 다 정직한 운영자에게 일어나고(예비 추출·일시적 `NODATA`·시계 보정),
     * 결과는 **잘린 투찰 행이 완료 행으로 실리는 것**이다 — 계수도 오류도 없이.
     */
    val walk: Instant?,
) {
    init {
        // 형태를 여기서 닫는다 — 원장은 영속 파일이고, 키가 아닌 문자열이 한 줄 들어가면 그 줄은
        // 어떤 공고와도 맞지 않아 그 축이 영영 다시 불린다(조용히 상한만 태운다).
        require(noticeKey == null || NOTICE_KEY_HEX.matches(noticeKey)) { NOTICE_KEY_HEX_MESSAGE }
        // **양방향**이다(D-6G2d-4 ⓒ) — AXIS 아닌 줄이 걷기를 갖는 것도, AXIS 줄이 빠뜨리는 것도 막는다.
        // 한쪽만 막으면 빠뜨림이 「빈 응답」으로 조용히 읽힌다.
        require((kind == AttemptKind.AXIS) == (walk != null)) { "걷기 식별자는 AXIS 줄만, 그리고 AXIS 줄은 반드시 갖는다" }
    }
}

/**
 * 공고 키 해시의 형태(vr r4 L-6) — 이 형태를 도메인이 검사한다. 원장은 영속 파일이고, 키가 아닌
 * 문자열이 한 줄 들어가면 그 줄은 어떤 공고와도 맞지 않아 그 축이 영영 다시 불린다(조용히 상한만
 * 태운다). 해시를 **짓는** 것은 workflow 의 몫이고(도메인은 `java.security` 를 보지 않는다),
 * 도메인은 나르는 값의 형태만 닫는다.
 */
internal val NOTICE_KEY_HEX = Regex("[0-9a-f]{64}")

internal const val NOTICE_KEY_HEX_MESSAGE = "공고 키 해시는 소문자 hex 64 자다"

/** AXIS 줄의 걷기 부재 — 형태 위반이다(D-6G2d-4 ⓑ). 「모름」을 「빈 응답」으로 접지 않는다. */
internal const val AXIS_WALK_REQUIRED = "AXIS 줄은 걷기 식별자를 반드시 싣는다"

/**
 * 원장의 두 줄 갈래 — **상한과 이어 돌기는 서로 다른 것을 묻는다.**
 *
 * [PENDING] 은 **나가려는 호출 하나**다(재시도마다 한 줄). 상한이 세는 것은 이것뿐이고, **줄 수를
 * 센다** — 한 줄이 「몇 회」를 들고 있으면 그 수를 0 으로 적는 길이 생기고 네 라운드 동안 실제로
 * 그렇게 새어 나갔다. 한 줄 = 한 호출이면 덜 세려면 줄을 지워야 하고, 지운 줄은 무결성 장부가 본다.
 * [HTTP] 는 그 호출의 **결말**이고 [AXIS] 는 한 축의 조회가 **끝난 방식**이다 — 항목이 0 이었는지는
 * 봉투를 편 뒤에야 알 수 있어 transport 관문이 답할 수 없다. 이어 돌기가 보는 것은 [AXIS] 뿐이다.
 */
enum class AttemptKind {
    /**
     * **나가려는** 호출 하나(D-6G-61) — 호출 **전에** 적는다. 호출 뒤에 적으면 그 사이에 죽었을 때
     * 나간 호출이 원장에 없고, 다음 기동의 seed 가 그만큼 덜 세어 **상한이 되감긴다**. 선기록이면
     * 최악이 「덜 쓴 것으로 세지 않고 더 쓴 것으로 센다」다 — 상한은 그 방향으로 틀려야 한다.
     */
    PENDING,

    /** 나간 호출 하나의 결말 — 선기록한 [PENDING] 줄에 이어 붙는다. */
    HTTP,

    /** 한 축의 조회가 끝난 방식 — 이어 돌기가 보는 줄이고 호출이 아니다. */
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
 * (공고, 축)의 **마지막 AXIS 결말**(D-6G-68) — 끝났는가와, 그 결말이 **어느 걷기의 것인가**.
 *
 * 추출이 이 둘을 함께 읽어야 짐작이 사라진다. [settled] 만 있으면 「끝났다」는 알아도 **어느 행이
 * 그 끝난 걷기의 것인지**는 모르고, 그 자리를 원문 행의 시각으로 메우는 순간 잘린 걷기가 완료 행이
 * 된다. [walk] 가 `null` 인 settled 는 빈 응답이고 **0 행**이다.
 */
data class AxisConclusion(
    /**
     * 그 걷기가 **어떻게 끝났는가**. 「끝났다」만으로는 부족하다: 빈 응답으로 정착한 축은 0 행이고,
     * 실패로 끝난 축은 걷기가 있어도 그 행을 쓸 수 없다 — 셋을 한 `Boolean` 으로 접으면 판독이
     * 그 차이를 잃는다(vr r5-t probe W7 · M-2).
     */
    val outcome: AttemptOutcome,
    /** 이 결말이 가리키는 걷기 — AXIS 줄은 언제나 싣는다(D-6G2d-4 ⓑ). */
    val walk: Instant,
) {
    /** 다시 부르지 않는가 — 이어 돌기가 보는 값이다. */
    val settled: Boolean get() = outcome.isSettled

    /** 그 걷기의 **행을 쓰는가** — 빈 응답과 실패는 행을 쓰지 않는다. */
    val usesRows: Boolean get() = outcome == AttemptOutcome.Succeeded
}

/**
 * 읽어 온 시도 이력 — 두 물음에만 답한다. 파일 판독은 어댑터가 하고 이 타입은 값만 센다.
 */
class AttemptHistory(
    private val attempts: List<CollectionAttempt>,
    /**
     * 형태가 서지 않아 읽지 못한 **끝 줄**의 수(D-6G-70) — 개행 없이 끝난 원장의 마지막 줄은
     * 크래시 흔적이다(append 도중에 죽었다). 그 호출이 실제로 나갔는지는 알 수 없고, 상한은
     * **덜 세는 쪽이 아니라 더 세는 쪽으로** 틀려야 하므로 호출 하나로 센다.
     */
    private val tornLines: Int = 0,
) {
    /**
     * 승인 상한에 계상할 몫 — **이 원장 전부**와 [dayStart] 이후 오늘치(총계의 부분집합이라 좁힌다).
     * **의도 줄([AttemptKind.PENDING])의 개수**다(D-6G-61). 결말 줄까지 세면 한 호출이 두 번
     * 계상되고, 죽어서 결말이 없는 의도 줄은 세는 것이 맞다 — 그 호출은 실제로 나갔을 수 있고
     * 상한은 덜 세는 쪽이 아니라 더 세는 쪽으로 틀려야 한다.
     *
     * **시작 시점 인자를 받지 않는다**(D-6G-69). 받던 시절에는 그 값이 기동마다 설정에서 왔고, 1 초
     * 뒤로 옮기기만 하면 총계와 오늘치가 **둘 다 0 으로 되감겼다** — 승인 상한을 다 쓴 디렉터리에서
     * 다시 상한만큼 나갈 수 있었다. 원장이 실행 상태 디렉터리 전용이 된 뒤로 그 값이 막는 것은 없고
     * 되감는 길만 남았다. **범위는 디렉터리 자신이다.**
     */
    fun spend(dayStart: Instant): CallSpend {
        val counted = attempts.filter { it.kind == AttemptKind.PENDING }
        val today = counted.count { !it.at.isBefore(dayStart) }
        // 찢어진 끝 줄은 **오늘치에도** 넣는다 — 시각을 모르므로 상한이 줄지 않는 쪽을 고른다.
        return CallSpend(total = counted.size + tornLines, today = today + tornLines)
    }

    /**
     * (공고, 축)마다 **마지막** 결말이 끝난 것인가(D-6G-58) — 원장에 줄이 하나라도 있으면 **원장이
     * 이긴다.** 원문 행의 존재는 원장 이전 실행의 흔적에만 쓴다: 잘린 걷기도 행을 남기므로, 행이
     * 있다고 다 받은 것이 아니다(그 축은 영영 다시 불리지 않고 결측이 조용해진다).
     *
     * 값이 `false` 인 것(실패·타임아웃·5xx·쿼터 거절·짧은 걷기)은 **다시 부른다.** 한 번 실패한 축을
     * 영구히 포기하면 그 결측이 무작위가 아니게 된다 — 느린 응답·과부하 시간대에 몰린 공고만 빠지고,
     * 그 행은 값 결측 제외로 계수되어 사유 귀속까지 틀린다.
     */
    fun axisConclusions(): Map<String, Map<SourceEndpoint, AxisConclusion>> =
        attempts
            .filter { it.kind == AttemptKind.AXIS }
            .mapNotNull { attempt -> attempt.noticeKey?.let { it to attempt } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, lines) ->
                lines.groupBy { it.axis }.mapValues { (_, byAxis) ->
                    // AXIS 줄은 걷기를 반드시 갖는다(`CollectionAttempt.init`) — 판독이 그 불변식을
                    // 다시 요구해, 형태를 어긴 줄이 여기까지 왔으면 조용히 지나가지 않는다.
                    byAxis.last().let { AxisConclusion(it.outcome, requireNotNull(it.walk) { AXIS_WALK_REQUIRED }) }
                }
            }

    /** **다시 부르지 않을** (공고, 축) — [axisConclusions] 중 끝난 것만. 이어 돌기가 쓴다. */
    fun settledAxes(): Map<String, Set<SourceEndpoint>> =
        axisConclusions().mapValues { (_, byAxis) -> byAxis.filterValues { it.settled }.keys }

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

/**
 * 시도의 결말 — 절단은 오류, **짧은 걷기**도 오류, 항목 0 은 빈 응답, 그 밖은 성공.
 *
 * 짧은 걷기(D-6G-58 ⓒ): 원천이 총수를 말했는데 그만큼 받지 못한 채 끝났다. 빈 페이지나 `NoData` 로
 * 「곱게」 멈춘 걷기는 절단 사유를 남기지 않아 앞 판에서 **성공으로** 적혔고, 그 축은 다시 불리지
 * 않았다. 받은 것이 총수보다 적으면 그것이 어떤 모양으로 끝났든 아직 다 받은 것이 아니다.
 * 빈 응답이 끝난 답인 것은 **원천 총수가 0 일 때뿐**이다.
 */
fun attemptOutcomeOf(accounting: CollectionAccounting): AttemptOutcome =
    when {
        accounting.truncationCause != null -> {
            AttemptOutcome.Failed(truncationCodeOf(accounting.truncationCause))
        }

        accounting.sourceTotal != null && accounting.received < accounting.sourceTotal -> {
            AttemptOutcome.Failed(SHORT_WALK_CODE)
        }

        accounting.received == 0 -> {
            AttemptOutcome.Empty
        }

        else -> {
            AttemptOutcome.Succeeded
        }
    }

private const val SHORT_WALK_CODE = "SHORT_WALK"
