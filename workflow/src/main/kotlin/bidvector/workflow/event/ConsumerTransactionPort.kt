package bidvector.workflow.event

/**
 * 소비자가 **커밋 경계를 요구하는** port(D-6F10-3) — 구현은 `adapters`
 * (`TransactionBoundary`, persistence 어댑터).
 *
 * 왜 소비자가 경계를 쥐어야 하는가: at-most-once 는 **claim 커밋과 종단 전이 커밋이 다른
 * 트랜잭션**이라는 사실에서 나온다(T1/T2). 한 트랜잭션으로 묶으면 발송 뒤 크래시의 롤백이
 * 행을 `PENDING` 으로 되돌려 다음 run 이 **다시 발송**한다. `JdbcOutboxPort` 는 트랜잭션을
 * 열지 않으므로(그 KDoc) 그 경계 선택은 호출부의 것이고, 호출부는 `workflow` 안의 relay 다
 * — 그래서 이 port 가 있다.
 *
 * `fun interface` 가 아니다 — SAM 변환이 메서드 자신의 타입 매개변수를 지원하지 않는다
 * (`ConnectionSource` 와 같은 이유).
 *
 * `inTransaction` 하나를 여러 번 부르는 것이 설계다(T1 한 번 + 행마다 T2 한 번 + 고아마다
 * 한 번). 「run 전체를 하나로」는 at-most-once 를 깨고, 「port 호출마다 자동으로 하나」는
 * inbox 기록과 종단 전이를 **갈라** 발송은 됐는데 키만 소진된 상태를 만든다 — 그 둘을 한
 * 커밋에 묶는 자리가 이 블록이다.
 */
interface ConsumerTransactionPort {
    fun <T> inTransaction(block: () -> T): T
}
