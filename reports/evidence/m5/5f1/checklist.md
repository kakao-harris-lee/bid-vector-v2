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
| D-5F1-5(계약 갱신 (2), 팀장 확인 `e4ba6a4`) | `center >= clamp_max`에서 `base`·`aggressive`가 같은 값으로 접히는 것은 결함이 아니다. **실측**(팀장 지시 — 강제하면 멈추고 보고): Kotlin `ParsedSuccessFields.kt`(`hasOrderedCandidateRates` 호출)·`CandidateShapeValidation.kt`(`rates[0] <= rates[1] && rates[1] <= rates[2]`)·`ResponseMapping.kt`(순서·유일성 검사 없음, grep 확인)와 도메인 타입 `BidPredictionOutcome.kt::BidRateCandidates.init`(`require(conservative <= base)`·`require(base <= aggressive)`) 전부 비엄격 `<=`만 두고 엄격 순서·유일성을 강제하지 않음을 확인 — 재결정 불필요. `tests/inference/test_scenario.py::test_center_at_or_above_clamp_max_folds_base_and_aggressive_but_keeps_three_candidates`(center `1.0`·`1.05` 파라미터화)가 예외 0·후보 3·라벨 순서·`conservative < base`(엄격)·`base == aggressive == clamp_max`를 고정 |

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
   `center`를 `0.9`로 옮기고(경계 회피, 원 의도 유지) `test_center_at_or_above_clamp_max_folds_
   base_and_aggressive_but_keeps_three_candidates`(D-5F1-5, `center` `1.0`·`1.05` 파라미터화)를
   신설했다 — 값 변경의 실제 결과(접힘)를 피하지 않고 test 로 고정했다.

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
2. **`center ≥ clamp_max` 에서 후보 둘 접힘 — 계약·소비자 무위반, 정책 (a) 의 귀결**
   (D-5F1-5, 계약 갱신 (2)). `scenario.clamp_max` 를 `1.0`으로 내린 값 변경(`OPEN-5E2-
   CANDIDATE-RATE-UPPER` (a) 채택)의 실제 결과로, `center`(대상 공고 축)가 `1.0` 이상인
   입력에서는 `base`와 `aggressive` 후보가 항상 같은 값(`1.0`)으로 접힌다 — 세 후보가
   서로 다른 값이라는 보장은 없다. 실측 확인(위 D-5F1-5 행): Kotlin 소비자
   (`ParsedSuccessFields.kt`·`CandidateShapeValidation.kt`·`ResponseMapping.kt`)도
   도메인 타입(`BidRateCandidates.init`)도 엄격 순서나 유일성을 요구하지 않아 이 접힘이
   계약 위반이나 서빙 장애로 이어지지 않는다. `tests/inference/test_scenario.py::
   test_center_at_or_above_clamp_max_folds_base_and_aggressive_but_keeps_three_candidates`
   가 이 사실을 test 로 고정한다 — 값을 지어내거나 감추지 않는다.
3. **`OPEN-5E2-FEATURE-SCHEMA-PARITY`(5E-2 알려진 제한 3, 미해결 승계)** — 이 slice 는 건드리지
   않는다. Kotlin `ML_CALL_POLICY.featureSchemaVersion`과 Python `SUPPORTED_FEATURE_SCHEMAS`
   불일치는 그대로 남아 있다.
4. **로더 불변식 `clamp_max ≤ 1` 부재**(verifier r1 MEDIUM-3, 계약 갱신 (3) — scope.md 위협
   모델 문면 그대로 인용): 「로더 불변식 `clamp_max ≤ 1`(계약 갱신 (3), verifier r1
   MEDIUM-3): 5D 로더는 `clamp_min < clamp_max` 만 검사해 `1.05` 도 정상 로드·READY 이고
   계약 위반은 요청마다 5E-2 런타임 fail-closed 로만 걸린다. (a) 의 방어는 값 고정 test +
   런타임 fail-closed 두 층이며 **구성상 닫히지 않는다** — D-5F1-1(값만, `src/**` 무편집)이
   친 울타리라 이 slice 밖. 구조적 폐쇄(로더 불변식 한 줄 + test)는 후속 소폭(5D 로더 파일을
   다음에 만지는 slice), M5 종결 판정 §2.5 에 등재.」
5. **Python 미러 `_is_acceptable_success_shape`(참고 부채, 이 slice 소유 아님)** —
   `tests/serving/test_kotlin_rules_parity.py`의 이 함수가 Kotlin
   `ParsedSuccessFields.kt::isAcceptableSuccessShape`의 여섯 검사 중 `hasValidDiagnosticsShape`
   대응을 옮기지 않았다. base `845e29b`에도 같은 상태라 5E-2 소유 부채이고 5F-1 이 만든
   것이 아니다(verifier r1 「참고」). 이 slice 의 D-5F1-5 접힘 판정에는 영향 없다(접힘 probe 는
   Kotlin 원문을 직접 읽어 교차 확인).
6. **`tests/inference/golden/_adapter.py`의 낡은 docstring·placeholder(reviewer LOW)** —
   `shipped_policy()` docstring 이 D-5F1-3 로 개명된 test(`test_shipped_policy_file_is_
   rejected_missing_agency_sample_threshold` → `test_shipped_policy_file_loads_with_
   lowered_clamp_and_agency_sample_threshold`)를 옛 이름으로 인용하고, "5F-1 이후 출하
   파일이 그 키를 갖고 로드된다"는 사실과 어긋난 서술을 유지한다. `_SHIPPED_POLICY_TEST_
   PLACEHOLDER`도 `.update()`(무조건 덮어쓰기)로 주입돼 출하 값이 `10`이 된 지금도 golden
   case(011 제외)는 placeholder `1`을 받는다. 이 파일은 scope.md 의 out_of_scope(「golden」)
   라 이 slice 의 blocker 가 아니다 — golden 953개 통과에 영향 없음(이 필드는 5F-1 이전에도
   항상 없어 golden 이 보는 실제 값이 바뀌지 않았다). 다음에 이 어댑터를 만지는 slice 가
   정리한다.

## 리뷰 finding 처분표(verifier r1 넷 + code-reviewer LOW 하나)

| # | 출처 | 심각도 | 자리 | 처분 |
| --- | --- | --- | --- | --- |
| V-r1 #1 | verifier r1 | MEDIUM(장부층) | `reports/evidence/m5/5d/policy-values.md` 헤더 | **해소** — 헤더 전칭에 `scenario.clamp_max` 예외절 추가(YAML 과 같은 형태) |
| V-r1 #2 | verifier r1 | LOW(낡는 좌표) | `test_scenario.py` docstring·`checklist.md` 판단 4 | **해소** — 개명된 test 이름(`test_center_at_or_above_clamp_max_folds_...`) 참조 정정 둘. `commands.md` 953 줄은 이력이라 그대로(변경 시점 정확한 이름) |
| V-r1 #3 | verifier r1 | MEDIUM(경계 문장) | `scope.md` 위협 모델 「방어하지 않는다」 | **해소(범위는 후속)** — 위 알려진 제한 4 로 문면 그대로 등재, 구조적 폐쇄는 후속 slice(M5 종결 판정 §2.5) |
| V-r1 #4 | verifier r1 | LOW(하네스 관측) | evidence 규격(크기 게이트·자기급식 재실측 루프) | **판정 불필요**(팀장 지시) — slice 결함이 아니라 규격 관측, 이 slice 는 이미 종결 |
| R-r1 #1 | code-reviewer | LOW | `tests/inference/golden/_adapter.py` | **등재(범위 밖)** — 위 알려진 제한 6, 다음에 이 파일을 만지는 slice 가 처리 |
