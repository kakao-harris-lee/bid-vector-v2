package bidvector.decision.priority

import bidvector.decision.MlUnavailableReason

/**
 * 성분·penalty 입력 여덟(scope.md ①)의 존재 여부. `null`이나 `0`으로 부재를 접지
 * 않는다(§6.3 sentinel 금지 — 1E `ActionThresholds`·1D `LadderInput` 관례와 같은
 * 축) — [Absent]가 「모르면 그 항이 없다」는 사실을 값으로 남긴다([Present]로 들어온
 * `0`은 정직한 값이다).
 *
 * 사유 어휘는 [MlUnavailableReason] 을 재사용한다(바퀴 재발명 금지, 같은 모듈이 이미
 * 가진 「왜 이 점수가 없는가」 어휘). `match`(scope.md ①)의 [Absent] 는
 * [bidvector.decision.priority.composePriority] 가 그대로
 * [bidvector.decision.priority.PriorityOutcome.Unavailable] 로 옮긴다 — 나머지
 * 일곱(재정규화·penalty 대상)도 같은 어휘를 쓴다. 성분마다 부재 사유를 더 세분화할
 * 필요가 생기면(성분 산출원이 저마다 다를 4B-5) 그 slice 가 이 sealed 를 넓힌다.
 */
sealed interface ScoreFact<out T> {
    data class Present<T>(
        val value: T,
    ) : ScoreFact<T>

    data class Absent(
        val reason: MlUnavailableReason,
    ) : ScoreFact<Nothing>
}
