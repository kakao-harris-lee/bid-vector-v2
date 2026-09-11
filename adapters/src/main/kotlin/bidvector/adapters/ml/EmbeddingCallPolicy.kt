package bidvector.adapters.ml

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.math.BigDecimal

/**
 * 임베딩 전용 호출 정책 인스턴스(D-4D2-2) — `MlCallPolicyData`(4D-1) 타입을 재사용하되
 * **다른 값 슬롯**을 둔다. 예측과 임베딩은 호출 성격이 다르다(후보마다 텍스트 하나,
 * 순차) — deadline·재시도·backoff·breaker 는 착수 값을 4D-1 과 같게 두고
 * `OPEN-4D2-POLICY-VALUES`로 실측 갱신을 연다. 그 넷의 리터럴은
 * `placeholderMlCallPolicy`(`MlCallPolicyPlaceholder.kt`, cpd 블록 2)를 공유한다 — 정본은
 * 이 코드가 아니라 `reports/evidence/m4/4d2/policy-values.md`가 될 것이나 사용자 승인
 * 전까지는 이 팩토리 호출이 유일한 근거다.
 *
 * **`featureSchemaVersion`은 위 넷과 다른 축이다(리뷰 F-B(medium) 정정)** — 이 필드는
 * 「텍스트 합성 규약의 version」이라 `embedding.proto`가 뜻을 정하고 주인은
 * `OPEN-2E-TEXT-SYNTHESIS`(4B-6)다. 착수 시 이 slice가 `"bidvector.ml.v1-embedding"`을
 * 스스로 지어냈는데, 2E 승인 testdata(`contracts/testdata/embedding/embed_text_response_success.binpb`)와 provider
 * fixture servicer(`ml-engine/tests/test_embedding_contract.py`)는 이미
 * `"text-synthesis-v1"`을 정본으로 쓰고 있었다 — 지어낸 값을 실 provider에 붙이면 양방향
 * `UNSUPPORTED_SCHEMA`였다(교차 test 부재로 안 보였다). 이 slice는 그 축의 **의미를
 * 정하지 않는다** — 이미 2E가 승인한 오늘의 값을 그대로 채용할 뿐이다(`OPEN-2E-TEXT-SYNTHESIS`
 * 는 열린 채로 둔다 — 4B-6이 합성 규약을 바꾸면 이 값도 그 slice가 함께 옮긴다).
 * `EmbeddingCallPolicyTest`가 이 값을 2E testdata와 직접 대조해 어긋남을 게이트로 바꾼다.
 */
val EMBEDDING_CALL_POLICY: EffectiveDatedPolicy<MlCallPolicyData> =
    EffectiveDatedPolicy(
        source =
            "OPEN-4D2-POLICY-VALUES — deadline·재시도·backoff·breaker는 착수 placeholder" +
                "(4D-1 ML_CALL_POLICY 와 같은 값). featureSchemaVersion 은 별도 축" +
                "(OPEN-2E-TEXT-SYNTHESIS, 2E 승인 testdata 값 채용)",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    placeholderMlCallPolicy(featureSchemaVersion = "text-synthesis-v1"),
            ),
    )

/**
 * `config/quality/contract-policy.properties`의 `embedding.norm.epsilon`(2E 정본, D-2E-1)을
 * 미러한다 — 어댑터의 구조 검증층(`isAcceptableEmbeddingShape`)이 이 값으로 응답 벡터의
 * L2 norm 을 대조한다. 값을 바꾸려면 그 정책 파일을 먼저 갱신한다(정본이 코드가 아니라
 * 그 파일이다, `MlCallPolicyData` KDoc과 같은 관례). `EmbeddingVector.init`의 거친
 * 안전판(`COARSE_NORM_EPSILON`)과 다른 axis다 — 이쪽이 정밀 임계다.
 *
 * **verifier F-3(medium)** — 이 리터럴이 정책 파일을 「미러」한다는 진술을 이전엔 아무
 * test 도 대조하지 않았다. `EmbeddingCallPolicyTest`의 값 고정 test(`ContractPolicySupport`
 * 의 `contractPolicyValue` 경유)가 이제 두 값을 직접 비교한다 — 어느 한쪽만 바뀌면 그
 * test 가 실패한다.
 */
internal val EMBEDDING_NORM_EPSILON: BigDecimal = BigDecimal("0.0005")
