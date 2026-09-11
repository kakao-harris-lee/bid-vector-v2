package bidvector.workflow.embedding

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 임베딩 벡터(scope.md ①, 위협 모델 방어 (a)) — 차원과 L2 정규화를 자기 불변식으로 갖는다.
 * `dimension`은 별도 필드가 아니라 `values.size`에서 **파생**한다 — 「dimension 필드와
 * values 크기가 어긋난 상태」자체를 표현할 수 없게 한다(불가능한 상태를 타입으로 차단).
 *
 * **생성자는 공개다(설계 검토 대비 결정 변경 — 알려진 제한)** — 설계 검토는 `internal`
 * 생성자를 제안했으나, `internal`은 Kotlin **컴파일 모듈**(Gradle 프로젝트) 단위라 이 값을
 * 실제로 짓는 어댑터(`adapters` 모듈, 다른 Gradle 프로젝트)에서 `internal` 생성자를 애초에
 * 호출할 수 없다(4D-1 `BidRateCandidates`·`ModelReleaseRef`·`Uncertainty` KDoc과 같은
 * 실측 — 이미 그 타입들이 같은 이유로 public이다). 위조 방어는 이 `init`(형태 하한)과
 * 어댑터 쪽 매핑 함수(`mapEmbedSuccess`)의 유일 생성 경로 관례·code review·test 커버리지가
 * 진다 — 이 `init`의 epsilon은 정책이 아니라 부동소수점 계산 오차를 흡수하는 **거친
 * 마지막 안전판**이다(정밀 임계는 `contract-policy.properties`의 `embedding.norm.epsilon`,
 * 어댑터 쪽 구조 검증층 `isAcceptableEmbeddingShape`가 먼저 건다, 설계 검토 (3)).
 */
data class EmbeddingVector(
    val values: List<Float>,
) {
    val dimension: Int get() = values.size

    init {
        require(values.isNotEmpty()) { "EmbeddingVector.values는 비어 있을 수 없다" }
        val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() })
        require(abs(norm - 1.0) <= COARSE_NORM_EPSILON) {
            "EmbeddingVector는 L2 정규화(norm≈1)여야 한다: norm=$norm"
        }
    }

    companion object {
        /**
         * 리뷰 F-C(medium) — 「검증층(정밀 임계, `EMBEDDING_NORM_EPSILON`)이 이 거친 안전판
         * 보다 먼저 걸린다」는 불변식이 실제로 서는 유일한 이유는 두 epsilon 의 대소
         * 관계(정밀 < 거침)뿐인데, 그 관계를 강제하는 장치가 없었다 — 정책 파일의
         * `embedding.norm.epsilon` 이 이 값보다 커지면 검증층을 통과한 벡터가 여기서
         * `IllegalArgumentException` 을 던져 F-1 과 같은 클래스로 `embed` 밖으로 샌다.
         * `internal` 은 Kotlin 컴파일 모듈(Gradle 프로젝트) 단위라 대소를 재는 `adapters`
         * 모듈 test 에서 보이지 않는다(`EmbeddingVector` 생성자가 public 인 것과 같은
         * 이유) — 가시성을 넓히는 목적이 아니라 **잴 수 있게** 하려고 `public` 으로 둔다.
         * 대소 관계 자체는 `EmbeddingCallPolicyTest`(adapters, `EMBEDDING_NORM_EPSILON` 소유
         * 모듈)가 test 로 강제한다.
         */
        const val COARSE_NORM_EPSILON = 0.01
    }
}
