# M9/ml-lifecycle 설계 검토 (구현 전)

## 0. 위협 모델 경계

승격 판정이 **자기를 만든 데이터로 자기를 승인하지 못하고**, 성숙도 분모에서 사라지는 행이
**사유 없이** 사라지지 못하고, 학습 표본 정의가 바뀐 모델이 **같은 식별자**를 쓰지 못한다.
경계 **밖**: 임계 값 자체의 옳음(승인 문서 소관) · 서명 키 보관·회전(운영자 결정, 등재만) ·
마감 시각 대체의 채택 여부(`OPEN-SET-10`, 등재만) · 앱 배선과 수집 연장(사용자 보류) ·
최종 bid/review/skip 판단(Kotlin 소유).

## 1. 열거인가 구성인가

- **구성**: 승격 인용(citation)은 「창 시작 ≥ 정책 effective date」 **한 술어**이고, 두 날짜는
  각각 자기 원천(정책 파일 · 창 구간)에서만 온다. 통과 목록도 예외 목록도 없다 — 정책 version 을
  올리지 않고 통과를 만들려면 **창을 미래로 옮기는 수밖에** 없고 창은 코퍼스가 정한다.
- **구성**: 시간축은 `KnownTimeAxis(value, source, policy_version) | UnknownTimeAxis(reason)`
  **합 타입**이다. 「출처를 모르는데 값은 있다」가 타입으로 표현되지 않는다(그 상태를 적을 자리가
  없다) — legacy 의 조용한 대체가 형태로 불가능해진다.
- **구성**: 승인된 시간축 출처는 **정책 파일의 bool 한 칸**(`maturity.time_axis_substitute_approved`)
  이 정하고, 코드에는 승인 목록 리터럴이 없다. `OPEN-SET-10` 이 결정되면 **그 한 칸**이 바뀐다.
- **열거**: `UnmeasurableReason` 둘·`TimeAxisSource` 둘은 어휘라 열거다. 그 열거가 「분모에서
  사라진 행 전수」와 같은 집합이라는 **항등식**(모집단 = 성숙 분모 + 측정 불가)이 `MaturityTable`
  생성 자리에서 돈다 — 새 사유를 더하고 계수에 넣지 않으면 항등식이 깨진다.
- **열거 아님(구조)**: 「임계 완화 입력 경로 0」 게이트의 모집단은 **정책 키 집합에서 생성**한다
  (`_SCALAR_KEYS` + 목록 접두). 키를 더하면 감시 범위가 같은 커밋에서 자란다.

## 2. 새 public 표면 전수 (값 획득 축)

| 표면 | 값이 들어오는 자리 | 무엇이 막는가 |
| --- | --- | --- |
| `EvaluationPolicy.effective_date` | `policy/evaluation-v1.yaml` 한 자리 | 로더가 `path` 하나만 받는다 · 비문자열(비인용 ISO → `date` 객체)은 `INVALID_VALUE` · checksum 에 들어가 report 가 공시 |
| `PolicyEpoch` · `policy_epoch(policy)` | `EvaluationPolicy` 하나뿐(유일 생성 경로) | 생성자 인자가 정책 객체 하나 — 낱개 값으로 epoch 을 짓는 자리가 없다 |
| `cite_promotion_window(policy, window_start)` | 정책 + 창 시작 시각 | 둘 다 호출자가 지어낼 수 없는 값(정책은 파일, 창은 성숙도 표) · KST 경계 동치 test |
| `PromotionSuperseded(reason, detail)` | `derive_promotion` 의 `citation` 인자 | `derive_promotion` 이 `citation` 을 **필수 키워드**로 받는다(안 넘기면 mypy 가 거부) · `Promotion` 네 변형 `match` 전수 |
| `EvaluationReportV1.policy_effective_date` | `_assemble_report` 하나 | canonical JSON · report checksum(값이 바뀌면 checksum 이 바뀐다) |
| `TimeAxisSource` · `UnmeasurableReason` · `TimeAxis` · `resolve_time_axis` | 관측 행의 두 시각 후보 + 정책 | 승인 밖 출처는 값이 있어도 `UnknownTimeAxis` · 조용한 주 배정 경로 없음 |
| `MaturityTable`(+`UnmeasurableCount`) | `build_weekly_maturity` 하나 | `__post_init__` 항등식 — 모집단 ≠ 분모 + 측정 불가면 생성 자체가 실패 |
| `MaturityWindow.time_axis` | 창을 만드는 집계 한 자리 | 창마다 출처별 계수 + 정책 version 이 함께 산다(provenance 소실 자리 없음) |
| `InferencePolicy.maturity_time_axis_substitute_approved` | `policy/inference-v1.yaml` 한 자리 | known_keys 전수 로더 · 미선언은 `PolicyRejected` |
| `derive_release_id(… sample_scope)` | `TrainedArtifact` 하나(호출자 인자 경로 없음) | 시그니처 전수 민감도 test(파라미터를 더하고 해시에 넣지 않으면 붉다) |

**「경계로 처리」 행 없음** — 위 전부 실측 대상이다.

## 3. 우회 (≥5)

1. **effective date 를 과거로 적어 전부 통과시킨다** — 막지 않는다(그것이 정책 승인의 뜻이다).
   막는 것은 **조용히** 그러는 길이다: 값이 정책 checksum 과 report 에 들어가므로 report 가
   달라지고, 「어느 정책으로 판정했는가」가 산출물에 남는다. 코드에서 바꾸는 길은 숫자 리터럴
   게이트(`evaluation/**`)와 「정책 객체 생성 자리 1」 게이트가 막는다.
2. **임계를 CLI 플래그·환경변수·요청 필드로 주입한다** — 세 경로 각각을 구조로 잰다:
   `add_argument` 의 옵션·dest 이름 ∩ 정책 키 = ∅(AST) · 환경 변수 이름 ∩ 정책 키 = ∅ 이고
   `evaluation/**` 은 환경을 아예 읽지 않는다(AST) · proto 전 메시지 필드 이름 ∩ 정책 키 = ∅
   (descriptor 순회 — 실제 wire).
3. **`EvaluationPolicy` 를 손으로 조립해 임계를 비튼다**(로더를 우회) — `src` 전체 AST 에서
   생성 자리가 **하나**이고 그것이 `load_evaluation_policy` 안임을 잰다. 테스트 코드는 대상 밖
   (그 자유가 있어야 거부 test 를 쓸 수 있다) — 그래서 「src 안」으로 범위를 적는다.
4. **승격을 창 없이 만든다** — `derive_promotion` 에 `citation` 을 안 넘기거나 `Citable` 을
   지어낸다. `citation` 은 기본값 없는 키워드이고 `Citable` 은 `cite_promotion_window` 만
   만든다(모듈 밖에서 그 타입을 지으려면 import 해 생성해야 하고, src 생성 자리 전수 test 가
   그 자리를 센다).
5. **측정 불가 행을 어느 주에 배정해 분모를 채운다**(legacy 의 조용한 대체) — `UnknownTimeAxis`
   에는 시각 값이 **없다**(타입에 필드가 없다). 주를 고르려면 값을 지어내야 하고, 지어내면
   항등식의 모집단 계수가 어긋난다.
6. **대체 출처를 코드에서 승인한다** — 승인은 정책 파일 bool 한 칸이고 코드에는 목록이 없다.
   출하값이 `false` 임을 test 가 잠근다(= `OPEN-SET-10` 미결 상태의 기계적 표현).
7. **대체가 주 경계를 밀었는데 안 보인다** — 대체 행 계수와 **대체 제외 주별 성숙도**를 함께
   산출한다(둘이 갈리면 표에서 보인다). 지금은 승인 밖이라 대체 행이 0 이고, 승인되는 날
   같은 측정이 그대로 돈다.
8. **학습 표본 정의를 바꾸고 같은 `release_id` 를 쓴다** — `sample_scope` 가 해시 입력에
   들어간다. 전수 민감도 test 의 모집단은 **시그니처에서 생성**되므로, 입력을 더하고 payload 에
   넣지 않으면 그 test 가 붉다.
9. **KST 경계로 하루를 벌어 통과 창을 만든다**(effective date 를 UTC 날짜로 재면 월요일 오전
   KST 창이 전날로 밀린다 — SET-06 이 실측한 함정) — epoch 의 KST 일 시작과 성숙도 커널의
   KST 주 시작이 **같은 경계**임을 cross-layer test 가 잰다(두 모듈이 상수를 공유하지 않으므로
   공유가 아니라 **등식**으로 닫는다).

## 4. 과잉·미달

- **과잉 아님(effective date 를 거동에 묶는 것)**: 칸만 더하면 ML-07 acceptance ③ 은 장식이
  된다(값이 wire 에 닿지도, 거동을 바꾸지도 않는다). 술어는 **하나**이고, ML-07 이 문면으로 적은
  함정(「관측에서 역산한 상수가 그 데이터의 사후 산물이 된다」)을 기계가 막는 가장 작은 형태다.
- **과잉 아님(대체 제외 표)**: SET-06 acceptance 가 요구하는 항목이고, 같은 커널을 걸러 한 번 더
  돌리는 것이라 산식 중복이 없다.
- **미달 자리 둘(알려진 제한으로 등재)**:
  ① `settlements.jsonl` 행은 `opened_at` 이 **필수**라 조립 근 경로에서는 측정 불가 행이
  구조적으로 0 이다 — 커널의 측정 불가 갈래는 커널 test 로만 실측된다(데이터셋 schema 확장은
  이 slice 밖, 앱 배선 보류).
  ② 성숙도 표의 provenance 는 **평가 report 로 흐르지 않는다** — `WeekMaturity` 는 평가 층
  타입이고 provenance 를 담으려면 그 층이 추론 층 어휘를 알아야 한다(import 계약이 막는다).
  지금 경계는 「SET-06 의 리포트는 `MaturityTable` 자신」이고, 평가 report 로의 전달은 앱 배선
  축과 함께 미룬다.
- **미달 아님(ML-08)**: 서명 키 보관·회전은 운영자 결정이라 코드로 만들 축이 없다 — 등재만.
