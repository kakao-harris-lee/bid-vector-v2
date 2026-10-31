package bidvector.app.notificationinbox

/**
 * 훑기 러너의 프로세스 종료 코드 — **0 은 창에 든 건을 전부 기록했을 때만**이다.
 *
 * 셋을 한 값으로 접지 않는 이유는 **처방이 다르다**는 것뿐이다(보존·relay 러너와 같은 축):
 *
 *   [INCOMPLETE] — 돌긴 돌았는데 **기록하지 못한 알림이 있다**. 그 건의 운영자는 그 사실을
 *                  영영 보지 못한다(훑기는 다음 run 에 같은 창을 다시 보므로 마감 창이
 *                  지나기 전이면 회수된다 — 그래서 실패가 아니라 미완이다).
 *   [FAILED]     — 실행이 예외로 끝났다. 앞서 기록된 항목은 남고 다음 run 이 이어받는다.
 */
enum class NotificationSweepExitCode(
    val value: Int,
) {
    COMPLETE(0),

    /** 예외로 끝났다 — 정제된 원인 코드만 로그에 남는다. */
    FAILED(1),

    /** 기록에 실패한 알림이 있다 — 훑기 자체는 끝까지 돌았다. */
    INCOMPLETE(2),
}
