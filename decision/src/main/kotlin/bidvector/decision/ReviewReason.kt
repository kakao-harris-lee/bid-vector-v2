package bidvector.decision

import java.math.BigDecimal

/**
 * ML 미가용 세부 사유(D-M4-6, ADR 0010 D-6) — 4B-1 은 사다리에 필요한 점수가 없다는
 * 사실 하나만 알았다. M4/4D-1 착수로 이 sealed 가 넓어진다(scope.md ④, D-4D-6 — 값은
 * 전부 `data object`, payload 없음. transport status 코드·detail_code 같은 자유 문자열을
 * 싣지 않는다 — 진단은 어댑터 로그의 몫이다).
 *
 * **층 표기(ADR 0010 D-3)** — 이름 옆 괄호가 그 사유가 어느 층에서 나는지를 진술한다.
 * transport 층은 gRPC status(재시도 소진 뒤의 최종 상태), application 층은 status OK +
 * `ApplicationFailure`, 그 밖은 client 쪽 계약 집행이다.
 */
sealed interface MlUnavailableReason {
    /** 사다리가 요구하는 점수 입력([LadderInput])이 결측이었다(4B-1, ML gateway 도입 전). */
    data object ScoreNotProvided : MlUnavailableReason

    /** transport 층 — `DEADLINE_EXCEEDED`, 남은 예산 안 재시도 뒤에도 소진(ADR 0010 D-4). */
    data object DeadlineExceeded : MlUnavailableReason

    /** 소비자 정책 층 — resilience4j circuit breaker 가 open 이라 호출 자체를 하지 않음(D-5). */
    data object CircuitOpen : MlUnavailableReason

    /** transport 층 — `UNAVAILABLE`·`RESOURCE_EXHAUSTED`가 재시도 상한 안에서도 지속(D-4·D-6). */
    data object RetryBudgetExhausted : MlUnavailableReason

    /** transport 층 — 재시도 불가 status(`INVALID_ARGUMENT` 등)로 첫 시도에 끝남(D-4). */
    data object TransportFailed : MlUnavailableReason

    /** application 층 — `ApplicationFailure{MODEL_NOT_READY}`가 재시도 뒤에도 지속(D-4·D-6). */
    data object ModelNotReady : MlUnavailableReason

    /** client 집행 층 — 응답 release 가 요청 selector 와 어긋남(ADR 0010 D-3 제3 변환 금지). */
    data object ReleaseMismatch : MlUnavailableReason

    /** client 집행 층 — 정의 밖 enum·후보 개수/순서·정규형 위반 등 계약 불변식 위반(2A ⑥). */
    data object ContractViolation : MlUnavailableReason

    /** application 층 — `ApplicationFailure{UNSUPPORTED_SCHEMA}`(ADR 0010 D-7, 다른 축). */
    data object UnsupportedSchema : MlUnavailableReason

    /** application 층 — `ApplicationFailure{UNSUPPORTED_RELEASE}`(server 가 명시 거부). */
    data object UnsupportedRelease : MlUnavailableReason

    /** application 층 — `ApplicationFailure{INVALID_REQUEST}`. */
    data object InvalidRequest : MlUnavailableReason
}

/**
 * `Review`의 사유(scope.md ④) — 생성자는 전부 `internal`(설계 검토 (2), [BidNowReason]과
 * 같은 이유).
 */
sealed interface ReviewReason {
    /** priority 가 `[reviewThreshold, bidNowThreshold)` 구간이다(조사 §2.2 분기 3). */
    @ConsistentCopyVisibility
    data class PriorityInReviewBand internal constructor(
        val priority: BigDecimal,
        val reviewThreshold: BigDecimal,
        val bidNowThreshold: BigDecimal,
    ) : ReviewReason

    /**
     * ML 점수 부재(D-M4-6 (a)) — 「추천 없음」이지 「낮은 추천」이 아니다. legacy 는 이
     * 상태 자체가 없어 fail-open 이었다(조사 §7.2) — 이 타입이 그 상태에 이름을 준다.
     * **부재에서 `BidNow`로 가는 경로가 타입에 없다**([VerdictLadder.judge] 구현·
     * `VerdictLadderPropertyTest` negative property 참고).
     */
    @ConsistentCopyVisibility
    data class MlUnavailable internal constructor(
        val reason: MlUnavailableReason,
    ) : ReviewReason
}
