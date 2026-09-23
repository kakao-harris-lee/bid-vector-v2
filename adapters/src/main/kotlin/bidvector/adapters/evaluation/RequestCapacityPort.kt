package bidvector.adapters.evaluation

import bidvector.workflow.evaluation.CapacityPort
import bidvector.workflow.evaluation.CapacitySnapshot

/**
 * [CapacityPort]의 첫 production 구현(M6/6A-3+6F-3, D-6A3-5) — **요청 스코프**다. `evaluate()`
 * 진입에서 정확히 한 번 불리는 [snapshot]이 이 생성자 값을 그대로 옮긴다 — 새 계산값을
 * 만들지 않는다((2b) 「닫는다」). `currentActiveBids`는 요청 본문이 싣는 값 그대로이고
 * (결정 ③, 그 값의 정직성은 이 slice 경계 밖), `maxActiveBids`는 같은 요청에서 한 번 읽은
 * 전략의 `maxActiveBids`에서 온다(D-6A3-5 — 호출부가 그 축을 진다, 이 클래스는 두 Int를
 * 옮길 뿐이다).
 */
class RequestCapacityPort(
    private val currentActiveBids: Int,
    private val maxActiveBids: Int,
) : CapacityPort {
    init {
        require(currentActiveBids >= 0) { "currentActiveBids는 음수일 수 없다: $currentActiveBids" }
    }

    override fun snapshot(): CapacitySnapshot = CapacitySnapshot(currentActiveBids, maxActiveBids)
}
