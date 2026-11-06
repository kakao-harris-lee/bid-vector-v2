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
- **구성**: 승인된 시간축 출처는 **정책 파일의 한 칸**(`maturity.time_axis_substitute`, 닫힌 어휘
  두 값)이 정하고, 코드에는 승인 목록 리터럴이 없다. `OPEN-SET-10` 이 결정되면 **그 한 칸**이
  바뀐다. bool 이 아닌 이유는 수정 라운드 v L-2 — YAML 1.1 참값 표기가 승인을 만들었다.
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
| `InferencePolicy.maturity_time_axis_substitute` | `policy/inference-v1.yaml` 한 자리 | known_keys 전수 로더 · 미선언은 `PolicyRejected` · 닫힌 어휘 밖 값은 거부(v L-2) |
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
   지어낸다. 막는 것: `citation` 이 기본값 없는 키워드라 안 넘기면 mypy 가 거부하고, 지어낸
   `Citable` 은 **거동 test** 가 잡는다(홀드아웃 전건에서 승격이 갈리는 등식). **정정**
   (verifier L-1): 앞 판 문면은 「src 생성 자리 전수 test 가 그 자리를 센다」고 적었는데
   `Citable` 을 세는 test 는 **없다** — 세는 것은 `EvaluationPolicy`·`TimeAxisPolicy` 둘이다.
5. **측정 불가 행을 어느 주에 배정해 분모를 채운다**(legacy 의 조용한 대체) — `UnknownTimeAxis`
   에는 시각 값이 **없다**(타입에 필드가 없다). 주를 고르려면 값을 지어내야 하고, 지어내면
   항등식의 모집단 계수가 어긋난다.
6. **대체 출처를 코드에서 승인한다** — 승인은 정책 파일의 **닫힌 어휘 한 칸**이고 코드에는
   목록이 없다. 출하값이 `NOT_APPROVED` 임을 test 가 잠근다(= `OPEN-SET-10` 미결 상태의
   기계적 표현). 어휘가 bool 이 아닌 이유는 v L-2 — YAML 1.1 참값 표기가 승인을 만들었다.
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


## 5. 수정 라운드 — v H-1 이 뒤집은 설계 (2026-11-06)

위 §2 표의 `EvaluationPolicy.effective_date` 행은 값 획득 자리를 「로더가 `path` 하나만
받는다」로만 적었고, §3 우회 3 은 그 방어를 「src 안 생성 **자리** 하나」로 적었다. **둘 다
모자랐다** — verifier 가 세 경로로 실제 임계 완화를 전 게이트 초록으로 통과시켰다:

| 경로 | 앞 판이 지나간 이유 |
| --- | --- |
| `dataclasses.replace(p, paired_t_threshold=…)` | 생성 **호출**이 아니라 복사라 AST 가 세지 않는다 |
| `type(p)(**{**vars(p), …})` | 호출 이름이 `type(p)` 라 이름 대조 밖이다 |
| `getattr(importlib.import_module(…), "Evaluation"+"Policy")(…)` | 이름이 **실행 시점**에 만들어져 어떤 AST 도 보지 못한다 |

주입원도 이름 대조를 비켜 갔다 — 환경 변수 이름(`ML_ENGINE_GATE_T`)이 정책 키를 담지 않았다.

**처방은 열거 확대가 아니라 형태다**(겹 둘, 둘 다 실측):

1. **생성 토큰** — `EvaluationPolicy` 의 첫 매개변수가 기본값 없는 `InitVar[_ParsedPolicyFile]`
   (모듈 private). `InitVar` 는 인스턴스에 저장되지 않아 `vars()`·`fields()`·`asdict()` 에
   없으므로 **기존 객체에서 베껴 올 자리가 없다** — 위 세 형태가 전부 `TypeError`/`ValueError`.
2. **출처 재검증** — 정책이 자기 파일과 그 지문을 들고, 제품 경로가 파일을 다시 읽어 값과
   바이트를 대조한다. 이 겹은 객체가 **어떻게 만들어졌든** 무관하므로 토큰 이름을 동적으로
   훔쳐 위조한 객체도 드러난다(AST 가 볼 수 없는 마지막 경로).

**경계를 새로 적는다**: 어느 **파일**을 정책으로 쓸지는 운영자가 고른다
(`ML_ENGINE_EVALUATION_POLICY`). 이 두 겹이 막는 것은 「코드가 값을 지어내거나 비트는 것」이고,
완화된 임계는 **승인 근거와 version·effective date 를 가진 정책 산출물**로만 존재할 수 있다 —
그리고 그 파일의 지문이 report 에 남는다(`policy_source_digest`). 그것이 ML-07 ③④ 가 요구한
모양이고, 앞 판은 그 모양을 **이름으로 근사**하고 있었다.

**재검증을 제품 경로에 둔 이유**(라이브러리 진입점이 아니라): `run_holdout` 은 커널 test 가
승인 밖 조합(seed 재배열·단일 seed)으로도 불러야 하고 그런 정책은 로더가 거부한다 — 거기서
재검증하면 그 test 들이 **재는 것을 잃는다**. 제품 경로는 하나다(`app.pipeline._run_holdout`).


## 6. 수정 라운드 2 — v H-2 가 뒤집은 **경계 문면** (2026-11-06)

§5 는 경계를 「어느 **파일**을 정책으로 쓸지는 운영자가 고른다」로 적었고, 그 두 겹이 막는
것을 「코드가 값을 지어내거나 비트는 것」으로 적었다. **그 경계 문면이 ML-07 수용과
모순이었다** — verifier 가 실측했다:

| 경로 | 결과 |
| --- | --- |
| `adapters` 에 헬퍼를 두고 임계만 바꾼 사본을 tempdir 에 써서 부팅이 그것을 로드 | ruff·mypy·lint-imports·래칫 0, 전건 1700 passed, 실제 t=0.1, version 은 그대로 `evaluation-v1`, 출처 재검증도 **통과** |
| 코드 변경 없이 `ML_ENGINE_EVALUATION_POLICY` 를 편집 사본으로 돌림 | 같다 |

같은 `evaluation-v1` 에서 판정이 달랐다. 그러면 「version 이 바뀌면 전후 판정이 갈린다」와
「env 로 값을 주입해도 판정이 달라지지 않는다」가 **둘 다** 거짓이다. §5 의 「완화된 임계는
승인 근거를 가진 정책 산출물로만 존재할 수 있다」도 이 경로에서 거짓이었다 — 사본에는 승인
근거가 없는데 산출물로서 통했다.

**근원**: version 문자열이 **내용에 묶여 있지 않다**. 두 겹은 「객체 ↔ 그 파일」을 묻고,
「그 파일 ↔ 승인」을 묻지 않았다.

**처방은 열거의 방향을 뒤집는 것이다** — 「무엇을 거부하는가」(무한)가 아니라 **「무엇이
승인됐는가」**(유한)를 코드 상수로 적는다. `registry.policy_approvals` 가 승인 version →
그 파일 내용 sha256 을 들고, **묻는 자리는 하나**다:
`load_approved_evaluation_policy` ← `app.server._preload` — 곧 **운영자의 선택(env)이
들어오는 자리**. 그 한 자리가 두 갈래를 함께 거부한다: 이름을 유지한 편집 사본(지문 불일치)과
이름을 바꿔 단 사본(미등록). 세 갈래를 env 축 test 가 잰다(둘의 거부 + 무편집 사본의 통과).

**경계를 다시 적는다**: 운영자는 **승인된 산출물 사이에서** 고른다. 승인은 세 자리를 같은
커밋에서 바꾸는 것이고(yaml · 등록부 한 줄 · 승인 장부의 지문 표), 어긋나면 test 가 붉다.

**한 자리로 충분한 근거는 사슬의 단일성이다**: 게이트에 닿는 정책은 그 자리를 거친 하나다.
env → 승인 요구 로더 → `_preload` → `pipeline_factory` → `_run_holdout`. 그 사슬을 호출 자리
집합 등식 **넷**이 진다 — 승인 미요구 로더(policy_source 안 둘) · `build_training_pipeline`
(하나) · `run_holdout`(하나) · `EvaluationPolicy` 생성 자리(로더 안 하나).

### 계약과 갈린 자리 둘 — **둘 다 실측 뒤 되돌렸다**

계약은 「**로더와 재검증**이 둘 다 거부」로 적었다. 둘 다 실제로 걸어 보고 되돌렸다.

| 어디에 걸었나 | 실측 | 왜 되돌렸나 |
| --- | --- | --- |
| 재검증(`verify_policy_source`) | 조립 근 test **일곱** 붉음 | 그 test 들은 작은 데이터셋으로 돌려고 승인 밖 임계(`min_evaluation_rows` 등)를 파일로 만들어 쓴다 — §5 가 「재검증을 제품 경로에 둔 이유」로 적은 함정이 한 겹 위에서 재발한다 |
| 뿌리 로더(`registry.policy.load_policy`) | **32 failed · 79 errors**(11 파일) | 값 불변식 test 들이 파생 정책에 현실적인 version 이름을 쓴다. 출하 추론 정책은 일부러 불완전해서(`agency_sample_threshold` 미선언) **네 helper 가 placeholder 를 얹은 사본으로만** 로드할 수 있고, `test_epoch` 하나는 그 이름이 승격 인용 문면에 나오는 것을 단언한다 |

**뿌리 검사가 더 막던 것**은 「제품 게이트에 닿을 수 없는 경로의 편집 사본」뿐이다. 출하 파일
자체를 고치는 길은 런타임이 아니라 **CI** 가 막는다(등록부 ↔ 출하 지문 등식 test). 닫는
집합은 같고, 잃지 않은 것은 그 11 파일이 재던 값 불변식이다.

### 우회 — 이 겹이 열어 둔 것과 막는 것

10. **등록부 상수를 코드가 고친다** — 모듈 소스를 편집해야 하고, 그러면 세 자리 등식 test
    셋이 붉다(지문 등식 **MH3 RED** · 출하 version 전수 분할 **MH4 RED** · 장부 문서 대조).
    사상은 `MappingProxyType` 이라 **실행 중 항목 삽입**은 `TypeError` 다(test).
11. **승인을 묻지 않는 로더를 조립 근이 직접 부른다** — 호출 자리 집합 등식이
    `policy_source` 안 둘로 닫혀 있다. 정적 호출 **MH5b RED**, 동적 import 의 속성 호출
    **MH5c RED**. **열린 잔여**: 이름을 실행 시점에 **쪼개** 만드는 `getattr` — **MH5 GREEN**
    (알려진 제한 3e; H-1 라운드의 같은 잔여는 값 대조가 받았지만 여기서는 받지 못한다 —
    이름을 바꾼 사본은 자기 파일과 값이 같아 재검증을 지난다).
12. **재검증·승인을 거치지 않는 두 번째 게이트 경로를 만든다** — 집합 등식 둘:
    `run_holdout` 호출 자리(**MH6 RED**) · `build_training_pipeline` 조립 자리(**MH7 RED**).
13. **승인된 두 version 밖의 정책으로 같은 완화를 한다** — `serving-v1`·`training-v1`·
    `strategy-backtest-*` 는 일부러 미등록이고 **그 목록이 test 에 있다**. 평가 게이트 임계는
    그 파일들에 없으므로 이 slice 의 축이 아니다; 축을 넓히는 것은 각자의 승인 장부와 소비
    경로를 함께 봐야 하는 별건이다(등재).
14. **추론 축에서 사본으로 대체 승인을 켠다** — 런타임에는 열려 있다(알려진 제한 3d).
    `InferencePolicy` 가 자기 파일 지문을 들고 있지 않아 조립 근이 승인을 물을 수 없다.
    출하 파일을 고치는 길은 CI 의 지문 등식 test 가 막는다.
