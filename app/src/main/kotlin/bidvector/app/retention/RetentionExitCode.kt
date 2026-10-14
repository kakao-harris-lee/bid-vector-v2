package bidvector.app.retention

// 0~2 밖의 값은 이름을 붙인다 — relay·수집 러너와 같은 관례(값 자체에 뜻이 없다).
private const val LEASE_BUSY_EXIT_CODE = 3
private const val REFUSED_EXIT_CODE = 4

/**
 * 보존 러너의 프로세스 종료 코드 — **0 은 기한이 지난 행이 전부 처분되고 판독 실패가 없을
 * 때만**이다.
 *
 * 넷을 한 값으로 접지 않는 이유는 **처방이 다르다**는 것뿐이다(relay 러너가 `LEASE_BUSY` 를
 * 0 으로 접지 않은 것과 같은 축):
 *
 *   [LEASE_BUSY]  — 다른 보존 러너가 돌고 있다. 기다리면 풀린다(cron 주기가 겹친 것).
 *   [REFUSED]     — 분류 레지스트리와 스키마가 어긋났거나 FK 에 순환이 있다. **아무것도
 *                   지우지 않았다**. 마이그레이션·분류를 고쳐야 풀린다.
 *   [INCOMPLETE]  — 돌긴 돌았는데 **끝까지 처분하지 못한 행이 있다**. 사유 둘이 같은 코드로 온다:
 *                   ⓐ 판독할 수 없는 원문이 있어 값 전체를 버렸다(상류 형태 변화의 신호) ·
 *                   ⓑ 후보로 골랐는데 **건너뛴** 행이 있다(그 run 이 백로그를 비우지 못했다).
 *   [FAILED]      — 실행이 예외로 끝났다. 앞서 커밋된 일괄은 남고 다음 run 이 이어받는다.
 */
enum class RetentionExitCode(
    val value: Int,
) {
    COMPLETE(0),

    /** 예외로 끝났다 — 정제된 원인 코드만 로그에 남는다(메시지에 행 값이 실릴 수 있다). */
    FAILED(1),

    /** fail-closed 로 버린 원문이나 **건너뛴 후보**가 있다 — 파기·마스킹 자체는 돌았다. */
    INCOMPLETE(2),

    LEASE_BUSY(LEASE_BUSY_EXIT_CODE),

    REFUSED(REFUSED_EXIT_CODE),
}
