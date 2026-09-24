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
        if (currentActiveBids < 0) {
            throw InvalidEvaluationRequestException("currentActiveBids는 음수일 수 없다: $currentActiveBids")
        }
    }

    override fun snapshot(): CapacitySnapshot = CapacitySnapshot(currentActiveBids, maxActiveBids)
}

/**
 * dry-run 요청 본문 거부(D-6A3-5, D-6A3-11 400 INVALID_REQUEST) — `IllegalArgumentException`의
 * 구체 하위형이다: `app.http.ErrorMapping`이 **이 타입만** 400으로 매핑한다(바이트코드가 다른
 * `IllegalArgumentException` 출처를 조용히 같은 상태 코드로 흡수하지 않는다, D-6A1-7과 같은
 * 정밀도).
 */
class InvalidEvaluationRequestException(
    message: String,
) : IllegalArgumentException(message)
