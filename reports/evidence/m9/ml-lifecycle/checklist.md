# M9 / ml-lifecycle — checklist (ML-07 effective date · SET-06 시간축 · ML-05 model_version · ML-08 등재)

계열: `OPEN-M6-RESIDUAL-ML-LIFECYCLE` 의 ML 수명주기 넷. 이 slice 는 **ml-engine 오프라인 축만** 세운다 —
ML 앱 배선과 수집 연장은 사용자 결정으로 보류 상태이고, 운영 반입·실 API·LLM 호출은 범위 밖이다.
**6G 판정(`reports/evidence/m6/6g/verdict.md`)은 읽지도 바꾸지도 않았다** — 재판정 0 · 새 수집 0.

## D-1 (ML-07) — effective date 를 칸이 아니라 거동으로

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | acceptance ③ 은 「판정식과 임계가 version·**effective date**·승인 근거를 가진 정책 산출물에 존재」인데, 출하 정책 파일에 effective date **칸이 없었다**. 칸만 더하면 장식이다 — 값이 wire 에 닿지도, 거동을 바꾸지도 않는다. |
| 값의 출처 | **그 정책 표의 승인 날짜**다 — `reports/evidence/m5/5c2/policy-values.md` 머리말 「승인 2026-09-16」(운영자 「둘 다 승인」, M5 종결 판정). 지어낸 날짜가 아니다. |
| 술어 하나 | `window_start >= effective date 의 KST 일 시작`. 효력 시작 **전에 시작한** 창의 판정은 승격 근거로 인용되지 않는다 — 그 창은 정책이 참고할 수 있었던 창이고, 이 capability 가 문면으로 남긴 함정(「관측에서 역산한 상수가 그 데이터의 사후 산물이 된다」, legacy commit `5d38ac1`)의 기계적 방어다. |
| 규칙의 모양 | 특례가 아니라 **하나**다: 점수가 난 판정(`Passed`·`Failed`)이 인용 가능한 창을 요구하고, `NotEvaluable` 은 그보다 앞선다(인용할 판정 자체가 없고, 「못 쟀다」는 데이터의 사실이라 정책 효력과 무관하다). `Failed` 를 예외로 두지 않은 이유: 「이 게이트를 통과 못했다」도 **그 정책에 대한 주장**이다. 숫자는 사라지지 않는다 — `windows[*].outcome` 이 그대로 싣는다. |
| ⑥ 이 측정되는 자리 | 데이터·임계·창 판정이 전부 같고 effective date 만 다른 두 run 이 `Promotable` 과 `PromotionSuperseded` 로 갈린다(홀드아웃 전건 경로에서 실측 — 두 run 의 `windows[0].outcome` **등식**을 함께 단언해 「바뀐 것이 게이트 판정이 아니라 승격 인용」임을 고정했다). |
| 승격 어휘 | `Promotion` 이 네 값이 된다. `PromotionSuperseded` 는 「못 이겼다」·「못 쟀다」와 **다른 사실**이라 접지 않는다. `_promotion_json` 의 암묵적 최종 `else` 는 `match` 전수로 바꿨다 — 그 `else` 가 네 번째 변형을 조용히 `not_evaluable` 로 적던 자리다. |
| wire | `policy_effective_date` 가 report 의 canonical JSON 과 checksum 에 들어간다. 정책 checksum 에도 들어가므로 같은 임계·다른 효력 시작이 **같은 값이 아니다**. |
| KST 로 재는 이유 | effective date 는 운영자 승인 **날짜**(KST)다. UTC 자정으로 재면 월요일 오전 KST 에 시작한 창이 전날로 밀린다 — SET-06 이 실측한 그 함정이다. 평가 층은 추론 층(성숙도 커널)을 import 할 수 없어(forbidden 계약) 상수를 **공유하지 못하므로**, 두 층의 KST 경계가 같음을 **등식 test** 로 닫았다. |

## D-2 (ML-07 ④) — 임계 완화 입력 경로 넷

앞 판의 확인은 **로더 시그니처 한 줄**(`load_evaluation_policy` 의 매개변수가 `path` 하나)뿐이었다. 그 한 줄은
「다른 자리에 완화 표면이 생기는 것」을 보지 못한다.

| 경로 | 술어 | 모집단 |
| --- | --- | --- |
| ① CLI | `add_argument` 의 옵션 문자열·`dest` 전수(AST) | 정책 키(`_SCALAR_KEYS` + 목록 접두)에서 **생성** |
| ② 환경변수 | 환경을 실제로 읽는 모듈(`os.environ`·`getenv`, **별칭 포함**)의 환경 이름 꼴 상수 전수 + `evaluation/**` 은 환경을 **아예** 읽지 않음(import 뿌리 비교) | 같음 |
| ③ 요청 파라미터 | **생성 descriptor 순회** — proto 전 메시지(중첩 포함)의 필드 이름 전수. 텍스트 grep 이 아니라 descriptor 라, 파일을 나누거나 메시지를 중첩해도 범위가 줄지 않는다 | 같음 |
| ④ 생성 자체 | **형태로 닫는다**(수정 라운드 v H-1) — 아래 D-2b. 이름 대조(`src` 생성 자리 하나)는 보조로 남긴다 | — |

**정책 키를 더하면 세 경로의 감시 범위가 같은 커밋에서 자란다**(열거 장부가 아니다). 모집단이 비거나 출하
파일과 어긋나면 네 축이 전부 헛도므로, 그것을 **먼저 재는** test 를 같은 파일에 뒀다.

## D-2b (v H-1) — 완화된 값을 **만드는 형태**를 닫는다

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | ④ 축이 「생성 **호출 이름**이 로더 안에 하나」였고, verifier 가 세 경로로 실제 임계 완화를 **전 게이트 초록**으로 통과시켰다: `dataclasses.replace`(복사라 호출이 아니다) · `type(p)(**vars(p))`(이름이 `type(p)`) · `getattr(importlib.import_module(…), "Evaluation"+"Policy")(…)`(이름이 실행 시점에 만들어진다). 주입원도 이름 대조를 비켜 갔다 — 환경 변수 이름이 정책 키를 담지 않았다. |
| 겹 1 — 생성 토큰 | 첫 매개변수가 기본값 없는 `InitVar[_ParsedPolicyFile]`(모듈 private). `InitVar` 는 인스턴스에 저장되지 않아 `vars()`·`fields()`·`asdict()` 에 없고, 그래서 **기존 객체에서 베껴 올 자리가 없다** — 세 형태가 전부 `TypeError`/`ValueError`(각각 test). |
| 겹 2 — 출처 재검증 | 정책이 자기 파일(`PolicySource`: 경로 + 바이트 지문)을 들고, 제품 경로가 **파일을 다시 읽어** 값(`policy_checksum` 등식)과 바이트(지문 등식)를 대조한다. 객체가 **어떻게 만들어졌든** 무관한 겹이라 토큰 이름을 동적으로 훔쳐 위조한 객체도 드러난다(AST 가 볼 수 없는 마지막 경로). |
| 어긋나면 | 조용히 파일 값으로 갈아끼우지 **않는다** — `DetailCode.POLICY_SOURCE_MISMATCH` 로 job 을 실패시킨다. 갈아끼우면 「무엇으로 판정했는가」가 다시 흐려진다. |
| 지문은 한 번 읽기에서 | `registry.policy.Policy.digest` 가 **파싱에 쓴 그 텍스트**에서 나온다 — 호출부가 파일을 다시 읽어 지문을 내면 그 사이에 파일이 바뀔 수 있는 창이 생긴다. |
| 왜 제품 경로인가 | `run_holdout` 은 라이브러리 진입점이고 커널 test 가 승인 밖 조합(seed 재배열·단일 seed)으로도 불러야 한다 — 그런 정책은 로더가 거부하므로 거기서 재검증하면 그 test 들이 **재는 것을 잃는다**. 제품 경로는 하나다(`app.pipeline._run_holdout`). |
| test 정책도 같은 길로 | 조립 자리를 `tests/policy_fixture.py` 하나로 모았다 — `evaluation_policy`(파일 경유, 제품 경로 test) / `kernel_policy`(토큰 직접, 승인 밖 조합이 필요한 커널 test). 앞 판은 여섯 파일이 각자 조립해 칸이 늘 때마다 여섯 자리를 고쳤다(이 라운드에 실제로 두 번). |
| report 가 공시하는 것 | `policy_checksum`(값의 지문)과 `policy_source_digest`(**파일의** 지문)를 함께. |

②는 **술어 범위를 좁혀서** 성립한다: 전 소스의 대문자 상수를 환경 이름으로 보면 enum 값
(`MEASURED_BEFORE_EFFECTIVE_DATE` 등)까지 후보가 되어 술어가 자기 어휘를 고발한다 — 「술어가 모든 것을
잡으면 아무것도 잡지 못한다」(이 저장소의 숫자 리터럴 게이트가 base 36 을 거부한 것과 같은 자리).

## D-3 (SET-06) — 시간축 provenance 와 `Unmeasurable`

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | 관측이 `datetime` 하나를 들었다 — 「이 시각이 어디서 왔는가」와 「시각을 모른다」를 적을 자리가 없었다. legacy 는 개찰 시각 결측 시 마감 시각으로 **조용히 대체**했고 대체 사실이 결과에 남지 않았다. |
| 구조로 닫은 것 | `TimeAxis = KnownTimeAxis(값·출처·정책 version) \| UnmeasurableTimeAxis(사유)`. 측정 불가 쪽에는 **시각 필드가 없다** — 「출처를 모르는데 값은 있다」를 적을 자리가 없으므로 그 행을 어느 주에 배정하려면 값을 지어내야 하고, 지어내면 아래 항등식이 어긋난다. |
| 항등식 | **모집단 = 창의 개찰 계수 합 + 사유별 측정 불가 합**. `MaturityTable` 의 **생성 불변식**이라, 사유 없이 분모에서 사라지는 행이 있으면 표를 **만들 수 없다**. |
| 대체 승인 자리 | versioned 정책 파일의 한 칸(`maturity.time_axis_substitute`, 출하 **`NOT_APPROVED`**) — 코드에 승인 목록이 없다. `OPEN-SET-10` 미결의 기계적 표현이고, 결정되면 그 한 칸이 `APPROVED` 가 된다. **닫힌 어휘 두 값만** 받는다 — bool 칸이었던 앞 판은 YAML 1.1 의 `yes`·`on`·`y` 를 승인으로 읽었다(v L-2 실측). |
| 대체의 영향 | 대체 행 계수와 **대체 제외 주별 표**를 함께 낸다 — 승인되는 날 측정이 **그 자리에 이미 있다**(그때 새로 만드는 것이 아니다). 지금은 대체 행이 0 이라 두 표가 같다. |
| 섞임 방지 | 한 표의 모든 행은 같은 정책 version 으로 해석됐어야 한다(provenance 를 손으로 지어 다른 version 을 섞으면 `ValueError`). |
| 어휘 충돌 | `inference.availability.UnmeasurableReason`(예측 응답의 도메인 결과 축, wire enum 으로 사상)과 **다른 어휘**다. 섞으면 응답 어휘에 wire 값 없는 사유가 생겨 `serving.wire` 의 사상 불변식이 깨지고 두 축의 「측정 불가」가 한 이름이 된다 — 그래서 `TimeAxisUnmeasurableReason` 으로 따로 둔다. |

## D-4 (ML-05) — `release_id` 를 model_version 축으로

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | acceptance 「학습 표본 정의가 바뀌면 `model_version` 이 올라간다」가 **성립하지 않았다**. 선언 입력이 다섯(dataset·spec version·spec checksum·seed·code version)이고 **학습 표본 범위가 빠져** 있어, 같은 데이터셋·spec·seed·code 로 범위만 다른 두 아티팩트가 같은 식별자를 가질 수 있었다(manifest 에는 `sample_scope` 가 있으니 내용은 달랐다). |
| 왜 그 축인가 | `sample_scope` 는 **피처 공간과 직교하는 축**이고, 기본값을 주면 미학습 공종 가드가 조용히 열린다(legacy `artifact_contracts.py` 의 사유). 범위만 다르게 학습한 둘은 다른 모델이다. |
| 열거하지 않는다 | 전수 민감도 sweep 의 입력 목록이 **`inspect.signature` 에서 나온다** — 입력을 더하고 해시 payload 에 넣지 않으면 그 test 가 붉어진다. 기존 손 열거 단언(`test_residual_release.py`)은 한 줄만 더해 남기고, 전수 축은 새 파일이 진다. |
| 문면 보정 | V2 의 식별자는 순서 있는 번호가 아니라 **결정적 해시**다(같은 입력의 두 학습이 같은 id 를 가져야 재현 대조가 성립, D-5C-10) — acceptance 의 「올라간다」는 「달라진다」로 읽는다. capability map 에 그 보정을 적었다. |
| 식별자 ↔ 공시 | `write_artifact` 의 재파생 단언이 **manifest 가 공시한 값**(`sample_scope` 포함)만 읽는다 — 식별자와 공시가 갈리면 그 단언이 붉어진다. |

## D-5 (ML-08) — 등재만, 코드 변경 0

첫째 acceptance(「검증 안 함」이 「통과」와 같은 값이 될 수 없다)는 M5 가 구조로 세웠다 — checksum 대조를
통과한 증거 타입 없이는 manifest 파싱 함수에 도달할 수 없다. **서명 축 자체는 ml-engine 에 아직 없다**
(`registry`·`training` 에 서명 코드 0 — grep 실측). 남은 결정은 코드 설계가 아니라 **키 보관 매체와 회전
주기의 승인**이고 운영자 소관이다. capability map 의 ML-08 행에 그 사실과 정본 포인터를 적었다.

## 설계 검토의 우회 ↔ 무엇이 막는가

| 우회 | 막는 것 | 변이 |
| --- | --- | --- |
| 1 effective date 를 칸만 두고 거동에서 뺀다 | 인용 술어 + report/checksum 공시 | **M1 RED** |
| 1b effective date 를 checksum 에서 뺀다 | 정책 checksum payload 단언 | **M2 RED** |
| 2 임계를 CLI·env·요청으로 받는다 | 세 축 AST·descriptor 스윕 | **M9·M10a·M11 RED** |
| 3 로더를 우회해 정책 객체를 손으로 조립 | 「src 안 생성 자리 하나」(별칭·재대입까지, 보조) | **M8a·M8b RED** |
| 3b `replace`·`type()`·동적 생성으로 비튼다 | `InitVar` 생성 토큰(형태) | **V1·V2·V3 전용 test RED** |
| 3c 토큰 이름을 동적으로 훔쳐 위조한다 | 출처 재검증(값·바이트 등식) + 제품 경로 거부 | **V4 전용 test RED** |
| 4 승격을 인용 없이 만든다 | `citation` 이 기본값 없는 키워드 + `Citable` 생성 경로 하나 | 타입(mypy)·`ValueError` test |
| 5 측정 불가 행을 어느 주에 배정해 분모를 채운다 | 측정 불가 타입에 시각 필드 없음 + 항등식 | **M5·M6 RED** |
| 6 대체 출처를 코드에서 승인한다 | 승인은 정책 파일 한 칸 + 출하값 `false` 단언 | **M4 RED** |
| 7 대체가 주 경계를 밀었는데 안 보인다 | 대체 행 계수 + 대체 제외 주별 표 | 두 표 대조 test |
| 8 학습 표본 정의를 바꾸고 같은 id 를 쓴다 | 시그니처에서 생성한 전수 민감도 | **M7 RED** |
| 9 KST 경계로 하루를 벌어 통과 창을 만든다 | 월요일 오전 KST test + cross-layer 경계 등식 | **M3 RED** |
| — | 음성 대조(주석만 바꿔치움, 거동 0) | **C GREEN** |

## 알려진 제한

1. **평가 report 로 provenance 가 흐르지 않는다.** `WeekMaturity` 는 평가 층 타입이고 provenance 를 담으려면
   그 층이 추론 층 어휘를 알아야 한다(forbidden 계약이 막는다). 지금 경계는 「SET-06 의 리포트는 성숙도 표
   자신」이고, 평가 report 로의 전달은 앱 배선 축과 함께 미룬다. 조립 근 코드에 그 문면을 주석으로 남겼다.
2. **조립 근 경로의 측정 불가 행은 구조적으로 0 이다.** `settlements.jsonl` 의 개찰 시각이 필수라 결측을 들고
   올 경로가 없다 — 커널의 측정 불가 갈래는 커널 test 로만 실측된다. 데이터셋 schema 확장은 이 slice 밖이다.
3. **단일 생성 자리 게이트의 잔여**: 재대입을 **한 단계**만 따라간다(`_A = T; _B = _A` 연쇄는 못 본다).
   이 층은 이제 **보조**다 — 생성 자체는 토큰이, 비튼 값은 출처 재검증이 막는다(D-2b). 문면은 공용 보조
   `tests/ast_sites.py` docstring 에 있다.
3b. **완화 경로 술어의 부분 일치**(cr LOW, **등재**): `_matched_keys` 는 정책 키가 후보 이름에 들어 있으면
   잡는다. `effective_date` 는 일반명이고 저장소에 동명 다른 어휘가 있다(`evaluation/backtest/rules.py` 의
   날짜 함수 · `strategy-backtest-v1` 의 `effective.*` 키) — 지금 교집합은 0 이다. 좁히지 **않은** 이유:
   접두·접미를 붙인 완화 이름(`--relax-maturity-threshold`)이 조용히 통과하는 쪽이 더 나쁘고, 지금 형태의
   대가는 「무관한 이름이 정책 키를 담는 날 붉어진다」 — 조용한 구멍이 아니라 사람이 볼 신호다.
3c. **운영자가 고르는 정책 파일**: 어느 파일을 쓸지는 환경이 정한다 — 그것이 설계다(D-2b 의 경계). 완화는
   승인 근거와 version·effective date 를 가진 **정책 산출물**로만 가능하고, 그 파일의 지문이 report 에 남는다.
4. **effective date 를 과거로 적는 길은 막지 않는다** — 그것이 정책 승인의 뜻이다. 막는 것은 **조용히**
   그러는 길이고, 값이 정책 checksum·report 에 들어가므로 산출물이 달라진다.
5. **출하 effective date(2026-09-16)는 지금 코퍼스의 창들보다 뒤다.** 즉 그 창들로는 승격이 인용되지 않는다 —
   이 capability 가 이미 적은 사실(「승격 여부를 판정할 수 있는 창이 아직 없다」)과 같은 방향이고, 통과를
   만들려면 **정책을 새 effective date 로 다시 승인**하거나 그 뒤의 창이 성숙해야 한다.
6. **ML-08 서명은 코드 축이 없다**(위 D-5). 등재만이다.
7. **effective date 값의 지위**(v M-2 · **사용자 확인 항목**): `2026-09-16` 은
   `reports/evidence/m5/5c2/policy-values.md` 머리말·change_history 의 **그 표 승인 날짜**(운영자 「둘 다
   승인」, M5 종결 판정 `reports/evidence/m5/closure/checklist.md` §3 ④)에서 읽었다. 그 기록은 **정책 표의
   승인**이고 **effective date 로서의 별도 승인은 아니다** — 구현자의 해석이다. 장부 두 곳에 그 사실을
   그대로 적었다(cr M-4). 같은 `evaluation-v1` version 을 유지한 채 칸이 늘어 **같은 version 의 checksum 과
   승격 거동이 바뀐다**(이전 `Passed` 창 → `Superseded`) — 승인 시 함께 확인할 자리다.
8. **정책 파일의 YAML 중복 키**(범위 밖 기존 부채, v 참고): `yaml.safe_load` 가 중복 키를 마지막 값으로
   조용히 접는다 — 한 줄 추가로 `effective_date` 를 덮어쓸 수 있다. 값이 정책 checksum·report 에 들어가므로
   **산출물에는 드러난다**. 뿌리 처방(중복 감지 로더)은 네 로더 공통이라 이 slice 밖이다.
