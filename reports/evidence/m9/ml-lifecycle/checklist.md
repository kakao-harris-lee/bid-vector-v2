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
| ② 환경변수 | 환경을 실제로 읽는 모듈(`os.environ`·`getenv`)의 환경 이름 꼴 상수 전수 + `evaluation/**` 은 환경을 **아예** 읽지 않음 | 같음 |
| ③ 요청 파라미터 | **생성 descriptor 순회** — proto 전 메시지(중첩 포함)의 필드 이름 전수. 텍스트 grep 이 아니라 descriptor 라, 파일을 나누거나 메시지를 중첩해도 범위가 줄지 않는다 | 같음 |
| ④ 로더 우회 | `src` 안에서 정책 객체를 짓는 자리가 **하나**이고 그것이 `load_evaluation_policy` 안 | — |

**정책 키를 더하면 세 경로의 감시 범위가 같은 커밋에서 자란다**(열거 장부가 아니다). 모집단이 비거나 출하
파일과 어긋나면 네 축이 전부 헛도므로, 그것을 **먼저 재는** test 를 같은 파일에 뒀다.

②는 **술어 범위를 좁혀서** 성립한다: 전 소스의 대문자 상수를 환경 이름으로 보면 enum 값
(`MEASURED_BEFORE_EFFECTIVE_DATE` 등)까지 후보가 되어 술어가 자기 어휘를 고발한다 — 「술어가 모든 것을
잡으면 아무것도 잡지 못한다」(이 저장소의 숫자 리터럴 게이트가 base 36 을 거부한 것과 같은 자리).

## D-3 (SET-06) — 시간축 provenance 와 `Unmeasurable`

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | 관측이 `datetime` 하나를 들었다 — 「이 시각이 어디서 왔는가」와 「시각을 모른다」를 적을 자리가 없었다. legacy 는 개찰 시각 결측 시 마감 시각으로 **조용히 대체**했고 대체 사실이 결과에 남지 않았다. |
| 구조로 닫은 것 | `TimeAxis = KnownTimeAxis(값·출처·정책 version) \| UnmeasurableTimeAxis(사유)`. 측정 불가 쪽에는 **시각 필드가 없다** — 「출처를 모르는데 값은 있다」를 적을 자리가 없으므로 그 행을 어느 주에 배정하려면 값을 지어내야 하고, 지어내면 아래 항등식이 어긋난다. |
| 항등식 | **모집단 = 창의 개찰 계수 합 + 사유별 측정 불가 합**. `MaturityTable` 의 **생성 불변식**이라, 사유 없이 분모에서 사라지는 행이 있으면 표를 **만들 수 없다**. |
| 대체 승인 자리 | versioned 정책 파일의 한 칸(`maturity.time_axis_substitute_approved`, 출하 **false**) — 코드에 승인 목록이 없다. `OPEN-SET-10` 미결의 기계적 표현이고, 결정되면 그 한 칸이 바뀐다. bool 만 받는다(`"true"`·`1` 같은 느슨한 참값을 승인으로 읽지 않는다). |
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
| 3 로더를 우회해 정책 객체를 손으로 조립 | 「src 안 생성 자리 하나」(별칭·재대입까지) | **M8a·M8b RED** |
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
3. **단일 생성 자리 게이트의 잔여**: 재대입을 **한 단계**만 따라간다(`_A = T; _B = _A` 연쇄는 못 본다). 그
   경로도 src 편집이 필요하고, 새 임계 **값**은 숫자 리터럴 게이트가, 새 입력 **경로**는 D-2 의 세 축이
   막는다. 문면을 그 게이트 docstring 에 뒀다.
4. **effective date 를 과거로 적는 길은 막지 않는다** — 그것이 정책 승인의 뜻이다. 막는 것은 **조용히**
   그러는 길이고, 값이 정책 checksum·report 에 들어가므로 산출물이 달라진다.
5. **출하 effective date(2026-09-16)는 지금 코퍼스의 창들보다 뒤다.** 즉 그 창들로는 승격이 인용되지 않는다 —
   이 capability 가 이미 적은 사실(「승격 여부를 판정할 수 있는 창이 아직 없다」)과 같은 방향이고, 통과를
   만들려면 **정책을 새 effective date 로 다시 승인**하거나 그 뒤의 창이 성숙해야 한다.
6. **ML-08 서명은 코드 축이 없다**(위 D-5). 등재만이다.
7. **`policy-values.md` 문서를 갱신하지 않았다.** 값 갱신 절차(문서 → YAML → test)의 대상은 **임계**이고,
   effective date 는 그 표의 **승인 날짜를 기계가 읽게 적은 것**이라 새 임계가 아니다. 그 표의 값 11 개는
   무변경이다(대조 test 가 그대로 돈다).
