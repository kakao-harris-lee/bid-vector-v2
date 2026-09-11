package bidvector.decision

/**
 * 사다리 안에서 `skip`으로 접히는 사유(scope.md ③, `OPEN-DIC-03` 종결 — 운영자 결정
 * 2026-09-09). legacy 의 두 `skip`은 대입이 아니라 부작위(초기값 유지)였고 문장으로만
 * 구분됐다(조사 §2.3·§3.1, N-2·N-3) — sealed + 값만으로 구분해 그 형태를 뒤집는다.
 *
 * **둘로 닫는다** — 사다리 유래(확정적)는 정확히 둘뿐이다(조사 §3.1 실측, §3.3).
 * 사다리 **밖**에서 공고가 조용히 사라지는 지점 열셋(조사 §3.2)은 이 어휘가 아니다 —
 * 운영자 결정으로 신설 `OPEN`(4B-2 배정, `capability-map.md` §14).
 */
sealed interface SkipReason {
    /** 진행 중 입찰 수가 한도에 닿았고 priority 가 임계 미만이다(조사 §2.2 분기 1). */
    data object CapacityHold : SkipReason

    /** 위 분기 전부가 거짓이다 — 소진 `when`의 마지막 갈래(조사 §2.2 분기 4). */
    data object LowPriority : SkipReason
}
