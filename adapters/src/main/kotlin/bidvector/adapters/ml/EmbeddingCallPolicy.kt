package bidvector.adapters.ml

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.math.BigDecimal

/**
 * 임베딩 전용 호출 정책 인스턴스(D-4D2-2) — `MlCallPolicyData`(4D-1) 타입을 재사용하되
 * **다른 값 슬롯**을 둔다. 예측과 임베딩은 호출 성격이 다르다(후보마다 텍스트 하나,
 * 순차) — 착수 값은 4D-1 과 같게 두고 `OPEN-4D2-POLICY-VALUES`로 실측 갱신을 연다.
 * 리터럴 자체는 `placeholderMlCallPolicy`(`MlCallPolicyPlaceholder.kt`, cpd 블록 2)를
 * 공유한다 — 정본은 이 코드가 아니라 `reports/evidence/m4/4d2/policy-values.md`가 될
 * 것이나 사용자 승인 전까지는 이 팩토리 호출이 유일한 근거다.
 */
val EMBEDDING_CALL_POLICY: EffectiveDatedPolicy<MlCallPolicyData> =
    EffectiveDatedPolicy(
        source = "OPEN-4D2-POLICY-VALUES — 착수 placeholder, 4D-1 ML_CALL_POLICY 와 같은 값",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    placeholderMlCallPolicy(featureSchemaVersion = "bidvector.ml.v1-embedding"),
            ),
    )

/**
 * `config/quality/contract-policy.properties`의 `embedding.norm.epsilon`(2E 정본, D-2E-1)을
 * 미러한다 — 어댑터의 구조 검증층(`isAcceptableEmbeddingShape`)이 이 값으로 응답 벡터의
 * L2 norm 을 대조한다. 값을 바꾸려면 그 정책 파일을 먼저 갱신한다(정본이 코드가 아니라
 * 그 파일이다, `MlCallPolicyData` KDoc과 같은 관례). `EmbeddingVector.init`의 거친
 * 안전판(`COARSE_NORM_EPSILON`)과 다른 axis다 — 이쪽이 정밀 임계다.
 */
internal val EMBEDDING_NORM_EPSILON: BigDecimal = BigDecimal("0.0005")
