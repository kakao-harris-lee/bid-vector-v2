package bidvector.adapters.archfixture

/**
 * D-6A2b-42·45 음성 fixture의 대상 — 계약 목록 **밖**의 어댑터 예외 타입이다.
 *
 * `isAssignableTo(Throwable)`(계층 해석)만으로 판정하면 이런 새 타입도 그대로 통과한다.
 * 목록을 정본으로 삼은 뒤에도 그 성질이 남아 있는지 보려면 **목록 밖의
 * 어댑터 예외**가 실재해야 하는데 production 의 어댑터 예외는 셋 다 목록 안이라, 그 대상만
 * test 자리에 둔다. 이 클래스는 test 클래스패스에만 있다.
 */
class UnlistedAdapterException(
    private val reason: String,
) : RuntimeException(reason) {
    fun rejectionReason(): String = reason
}
