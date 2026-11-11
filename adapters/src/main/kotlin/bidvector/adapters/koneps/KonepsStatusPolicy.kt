package bidvector.adapters.koneps

/**
 * 받은 HTTP 상태 코드의 **처분**(OPS-09) — 봉투를 읽기 **전에** 갈린다.
 *
 * 앞 판은 이 축이 없었다: 429 가 아닌 수신 응답은 상태 코드와 무관하게 봉투 해석으로 갔고
 * 재시도 여부를 봉투 분류가 정했다. 그래서 차단(403) 응답이 재시도 가능 코드 봉투를 싣고
 * 오면 재시도됐다 — 차단당한 상대에게 재시도는 상황을 악화시키는 방향이다.
 *
 * 세 값이 **서로 다른 다음 걸음**을 뜻한다. 하나로 접으면 운영자가 할 일이 사라진다.
 */
enum class KonepsStatusDisposition {
    /** 봉투를 읽는다 — 성공·데이터 없음·에러 봉투가 그 안에서 갈린다(HTTP 200 + 에러 코드 검출이 이 축이다). */
    INTERPRET_ENVELOPE,

    /** 속도 한도 — 답은 재시도(백오프)다. 조사 a-4 실측 「~2분 안에 회복, 원인은 동시성」. */
    RATE_LIMITED,

    /** 차단 — 답은 재시도가 **아니다**. 같은 자격으로 다시 불러도 같은 답이 오고, 호출만 늘어난다. */
    ACCESS_BLOCKED,
}

/**
 * 처분 표의 한 행 — **선언된 정책 값**이다(OPS-09 「각 버킷의 재시도 가능성이 선언된 정책 값으로
 * 조회 가능하다 — 코드 분기에 숨지 않는다」). 상태 코드가 `when` 의 분기 조건으로 흩어지면
 * 「무엇이 왜 재시도되는가」를 읽으려면 호출 그래프를 따라가야 한다.
 */
data class KonepsStatusRule(
    val status: Int,
    val disposition: KonepsStatusDisposition,
) {
    init {
        require(status in HTTP_STATUS_RANGE) { "HTTP 상태 코드가 아니다: $status" }
    }
}

private val HTTP_STATUS_RANGE = 100..599

/**
 * 상태 코드 축의 재시도 처분 표(OPS-09) — **열거하지 않은 상태는 봉투로 간다.**
 *
 * 기본 방향이 [KonepsStatusDisposition.INTERPRET_ENVELOPE] 인 근거: OPS-09 의 첫 확정 결함은
 * 「HTTP 200 으로 오는 quota 초과」이고, 그것을 잡는 유일한 자리가 봉투의 `resultCode` 검증이다.
 * 상태 코드를 모른다고 봉투를 건너뛰면 그 검증이 사라진다. 재시도 쪽 fail-safe 는 **봉투 분류가
 * 이미 진다** — 미지·부재 `resultCode` 는 `Unclassified` 로 비재시도다(P-4 ③).
 *
 * 그래서 이 표에 적는 것은 **봉투를 읽어도 답이 달라지지 않는 상태**뿐이다. 401·403 은 본문이
 * 무엇이든 그 호출이 나갈 자격이 없다는 뜻이고, 429 는 본문이 아니라 속도가 원인이다.
 */
val KONEPS_STATUS_RULES: List<KonepsStatusRule> =
    listOf(
        KonepsStatusRule(HTTP_UNAUTHORIZED, KonepsStatusDisposition.ACCESS_BLOCKED),
        KonepsStatusRule(HTTP_FORBIDDEN, KonepsStatusDisposition.ACCESS_BLOCKED),
        KonepsStatusRule(HTTP_TOO_MANY_REQUESTS, KonepsStatusDisposition.RATE_LIMITED),
    )

internal const val HTTP_UNAUTHORIZED = 401
internal const val HTTP_FORBIDDEN = 403
internal const val HTTP_TOO_MANY_REQUESTS = 429
