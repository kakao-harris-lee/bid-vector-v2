package bidvector.archfixture.violating.qualification

/**
 * 실행 스택 읽기. 값을 밖으로 내보내지 않아도 **실행 환경을 읽는** 자리이고, `StackWalker` 를
 * 허용 목록에서 뺀 것과 같은 이유로 막는다. 프로퍼티 표기(`.stackTrace`)라 호출처럼 보이지
 * 않지만 바이트코드에서는 `Throwable#getStackTrace` 접근이다.
 */
class StackTraceLeak {
    fun depth(): Int = IllegalStateException("x").stackTrace.size
}
