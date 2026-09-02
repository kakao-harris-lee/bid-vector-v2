package bidvector.archfixture.violating.sharedkernel

/**
 * 허용된 예외 계층이 **상속으로 들고 다니는 I/O**. `printStackTrace` 는 `System.err` 로 쓰는데
 * 바이트코드의 owner 는 구체 예외 타입이라, 멤버 규칙을 owner 로 재면 잡히지 않는다(Codex 6차 #1).
 *
 * `Throwable` 을 허용 목록에서 빼는 것으로는 닫지 못한다 — `require`/`check`/`toInt()` 가
 * 컴파일러 산출로 내는 `IllegalArgumentException`·`IllegalStateException`·`NumberFormatException`
 * 이 같은 멤버를 상속으로 갖고, 그 셋은 뺄 수 없다. 그래서 **선언 클래스**로 잰다.
 */
class ExceptionIoLeak {
    fun report(cause: RuntimeException) {
        cause.printStackTrace()
    }

    /** 구체 예외 타입을 거치는 같은 접근 — owner 가 `IllegalStateException` 이다. */
    fun reportConcrete() {
        IllegalStateException("domain side effect").printStackTrace()
    }
}
