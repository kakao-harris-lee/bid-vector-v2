# M5/5F-1 — checklist.md

## M5 완료 조건 담당 근거

| 완료 조건(milestone-5.md) | 5F-1 근거 |
| --- | --- |
| 후보율 계약 축 `Candidate.bid_rate` ≤ 1(D-2B-8·D-2F-4)이 출하 경로에서도 성립 | `policy/inference-v1.yaml` `scenario.clamp_max: 1.0` — `tests/serving/test_kotlin_rules_parity.py::test_shipped_clamp_max_keeps_extreme_observation_within_contract_rate`(실 엔진, 극단 관측값에서도 Success + 후보 전부 (0,1]), `tests/app/test_server_prediction.py::test_shipped_policy_serves_success_matching_promoted`(실 socket, 같은 확인) |
| `OPEN-5D2-POLICY-VALUES`(기관 표본 임계 값 확정) | `assessment.agency_sample_threshold: 10` — `tests/inference/test_policy.py::test_shipped_policy_file_loads_with_lowered_clamp_and_agency_sample_threshold` |
| 출하 정책 그대로 gate READY(5E-2 알려진 제한 2 해소) | 위 두 값의 결합 결과 — `test_shipped_policy_serves_success_matching_promoted`가 READY·promoted·Success 를 실 socket 으로 고정 |

## D-5F1-1~4 이행

| ID | 이행 |
| --- | --- |
| D-5F1-1 | `src/**` 무편집 — `git diff --name-status <base> -- ml-engine/src` 무출력(commands.md 「경계 확인」) |
| D-5F1-2 | `assessment.agency_sample_threshold: 10`의 근거는 golden `ml-kernel-011`(승인 corpus) — `reuse.md`·`reports/evidence/m5/5d2/policy-values.md` 재승인 조건 문면 |
| D-5F1-3 | `test_shipped_policy_file_is_rejected_missing_agency_sample_threshold`(5E-2 소유) 반전 — `test_shipped_policy_file_loads_with_lowered_clamp_and_agency_sample_threshold`가 값이 빠지면 다시 붉어짐을 보장(로더가 `PolicyRejected(MissingKey)`를 내는 동작 자체는 무변경 — commands.md 「RED 확인」) |
| D-5F1-4 | `reports/evidence/m5/5d/policy-values.md`·`reports/evidence/m5/5d2/policy-values.md` 갱신은 이 slice 가 함 — `reports/evidence/m5/5e2/**`는 무편집(`git diff --name-status <base> -- reports/evidence/m5/5e2` 무출력) |
| D-5F1-5(계약 갱신 (2), 팀장 확인 `e4ba6a4`) | `center >= clamp_max`에서 `base`·`aggressive`가 같은 값으로 접히는 것은 결함이 아니다 — `src/**`가 엄격 순서를 강제하지 않고 Kotlin `CandidateShapeValidation.kt::hasOrderedCandidateRates`도 `<=`(비엄격)을 씀을 확인. `tests/inference/test_scenario.py::test_center_at_clamp_max_folds_base_and_aggressive_but_keeps_three_candidates`가 고정 |

## 위협 모델 우회 후보 ↔ 대응표(scope.md 「우회 후보」)

| # | 우회 후보 | 대응 |
| --- | --- | --- |
| (1) | 값을 바꾸고 test 를 안 뒤집으면 S-5 가 붉어야 한다 | 실측 — YAML 갱신 전 RED 커밋(`test_shipped_policy_file_loads_with_lowered_clamp_and_agency_sample_threshold`가 `PolicyRejected` 로 실패)으로 test 가 실제로 값 부재에 반응함을 확인. `test_shipped_values_with_agency_sample_threshold_declared_load_successfully`의 `clamp_max` 단언(1.4→1.0)도 YAML 갱신 전엔 붉었다(commands.md) |
| (2) | `clamp_min < clamp_max` 불변식(0.7 < 1.0) 유지 | `test_policy.py::test_clamp_band_inverted_is_rejected`(기존, 무편집) + `test_shipped_clamp_min_survives_quantize_check`(0.7 유지 확인) — 로더가 새 값에서도 이 불변식을 계속 검사 |
| (3) | `assessment.plausible_max 1.2`(입력 표본 밴드)와 `scenario.clamp_max`(출력 후보율 상한) 혼동 | 둘은 서로 다른 정책 키·다른 test(`test_policy.py`의 `assessment_band_inverted` vs `scenario_clamp_band` 계열)로 각자 검증됨 — 이 slice 는 `scenario.clamp_max`만 바꿨고 `assessment.plausible_max`는 무편집(YAML diff 로 확인) |

## (2b) 값 획득 축

새 production public 표면 0(scope.md 선언대로 — `ml_engine` 패키지 export·top-level 함수·타입
무변경, `git diff --name-status <base> -- ml-engine/src` 무출력). test 파일에 추가한 것들은
production 표면이 아니다: `tests/serving/test_kotlin_rules_parity.py::_inference_policy_with_clamp_max`
(모듈 내부 helper, 이 파일만 호출)와 `_servicer_and_runtime(policy: InferencePolicy | None = None)`
(선택 인자 — 기존 무인자 호출부 전부 무변경, 새 인자를 쓰는 호출자는 같은 파일의 변이 test
하나뿐).

## 판단이 필요했던 자리(등재)

1. **`test_server_prediction.py` (A)/(B) 병합** — (A) 완성 case 정책 test 와 반전된 (B) 출하
   정책 test 가 값 변경 이후 결과가 동일해져(둘 다 READY·Success·promoted 일치) `test_shipped_
   policy_serves_success_matching_promoted` 하나로 합쳤다. `_completed_case_inference_policy_
   path` 헬퍼 자체는 제거하지 않고 N-2 test(정책 넷 중 하나만 깨졌을 때의 "정상 정책 하나" 자리)
   가 계속 쓴다.
2. **`_policy_support.py`의 placeholder** — `setdefault`가 이제 no-op(출하 파일이 이미 값을
   가짐)이지만 코드는 남기고 docstring만 갱신했다(제거·단순화 대신 유지 선택). 근거: 이 helper
   가 "출하 YAML 을 편집하지 않고 읽는다"는 불변식을 지키는 자리이고, 만약 출하 파일이 이 키를
   다시 잃는 회귀가 나도(예: 수동 편집 실수) 이 helper 를 쓰는 다른 5D 모듈 test(예:
   `test_scenario.py`·`test_predict.py`)가 계속 로드 가능한 상태를 유지하게 하는 방어적 fallback
   이라 판단했다.
3. **`test_kotlin_rules_parity.py`의 placeholder** — 위와 달리 **단순화**(직접 로드로 교체)를
   택했다. 이 파일 안에서는 `_completed_case_inference_policy()`가 유일한 소비자라 fallback
   가치가 없고, 대신 그 임시 사본 메커니즘을 재사용해 `_inference_policy_with_clamp_max`(변이
   helper)를 새로 만들었다 — 같은 패턴이 다른 목적으로 남아 코드 중복이 아니다.
4. **`ml-engine/tests/inference/test_scenario.py`(scope 밖) 편집 필요 — 해소됨.** `center=1.0`
   fixture 상수가 새 `clamp_max=1.0` 경계와 우연히 겹쳐 `test_candidates_have_fixed_order_and_
   labels`·`test_matches_legacy_scenario_bid_rates_formula` 둘이 RED 였다. `src/**`나 산식
   자체는 무관 — 순수 fixture 값 충돌이었다. 팀장에게 scope 확장을 요청(2026-09-16, 메시지
   「5F-1 scope gap: test_scenario.py needs edit」)했고, 팀장이 계약 갱신 (2)(커밋 `e4ba6a4`,
   `reports/evidence/m5/5f1/scope.md` in_scope 편입 + D-5F1-5 신설)로 확인했다. 두 test 의
   `center`를 `0.9`로 옮기고(경계 회피, 원 의도 유지) `test_center_at_clamp_max_folds_base_and_
   aggressive_but_keeps_three_candidates`(D-5F1-5)를 신설했다 — 값 변경의 실제 결과(접힘)를
   피하지 않고 test 로 고정했다.

## 새 파일 ↔ in_scope 대조

이 slice 는 신규 파일을 만들지 않는다(`_inference_policy_with_clamp_max`는 기존 파일
`test_kotlin_rules_parity.py` 안의 신규 함수이지 신규 파일이 아니다). `git diff --name-status
<base>..HEAD -- <in_scope 경로>`(commands.md)의 상태 열이 전부 `M`(수정)임을 확인 — `A`(추가)
없음.

## OPEN 처분

| OPEN | 처분 |
| --- | --- |
| `OPEN-5E2-CANDIDATE-RATE-UPPER` | **종결** — (a) 채택, `scenario.clamp_max: 1.0` |
| `OPEN-5D2-POLICY-VALUES` | **종결(잠정)** — 값 `10`(후보 (b)), 재승인 조건은 `reports/evidence/m5/5d2/policy-values.md` |

## 알려진 제한

1. **`assessment.agency_sample_threshold = 10`은 잠정값이다** — legacy 대응 상수가 없어 golden
   synthetic 값을 옮긴 것이고, 5C 재학습 지표가 나오면 재승인 대상이다(`reports/evidence/m5/
   5d2/policy-values.md`). 재학습 지표가 이 값을 다르게 가리키면 이 slice 의 값 갱신 절차(정책
   문서 → YAML → test)를 다시 밟아야 한다.
2. **`OPEN-5E2-FEATURE-SCHEMA-PARITY`(5E-2 알려진 제한 3, 미해결 승계)** — 이 slice 는 건드리지
   않는다. Kotlin `ML_CALL_POLICY.featureSchemaVersion`과 Python `SUPPORTED_FEATURE_SCHEMAS`
   불일치는 그대로 남아 있다.
