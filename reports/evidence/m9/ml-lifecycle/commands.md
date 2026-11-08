# M9/ml-lifecycle 실행 기록

base `df4976cc` · 브랜치 `feature/m9-ml-lifecycle` · 계약 `scope.md` · 설계 검토 `design-review.md`.

재현은 `ml-engine/` 에서. 이 레인은 **Gradle 을 돌리지 않는다**(M9 공통 규율 — ml-engine job 명령은 로컬
실행 허용). 실 외부 API·LLM·운영 host 접근 0.

## §1 착수 실측 — 네 capability 의 현 상태

| capability | 착수 시점의 사실 | 어디서 |
| --- | --- | --- |
| ML-07 ③ | 출하 정책 파일에 **effective date 칸이 없다**. 로더의 `known_keys` 전수라 그 키를 더하면 거부된다 | `policy/evaluation-v1.yaml` · `evaluation/policy.py` |
| ML-07 ④ | 완화 경로 확인이 **로더 시그니처 한 줄**뿐(`path` 하나). CLI·env·요청·로더 우회를 재는 층 0 | `tests/evaluation/test_public_signatures.py` 의 마지막 test |
| SET-06 | 관측이 `datetime` 하나를 든다 — 「어디서 온 시각인가」·「시각을 모른다」를 적을 자리가 없다. 측정 불가 어휘 0 | `inference/maturity.py` |
| ML-05 | `derive_release_id` 의 선언 입력 **다섯** — `sample_scope` 가 없다. 범위만 다른 두 아티팩트가 같은 id 를 가질 수 있다 | `training/release.py` · `training/artifact_writer.py` |
| ML-08 | **서명 코드 0**(`registry`·`training` grep). checksum 축만 M5 가 세움 | — |

착수 base 에서 전건 pytest **1628 passed**(exit 0)로 기준선을 잡았다.

## §2 RED 실측

| 라운드 | 명령(요지) | 무엇이 붉었나 |
| --- | --- | --- |
| ML-07 | `pytest tests/evaluation/test_{epoch,gate_relaxation_paths,evaluation_policy}.py` | **collection error 1**(`ml_engine.evaluation.epoch` 없음) + **8 failed / 33 passed** |
| SET-06 | `pytest tests/inference/test_{maturity,time_axis}.py` | **collection error 2**(`ml_engine.inference.time_axis` 없음 · `time_axis_policy_of` 없음) |
| ML-05 | `pytest tests/training/test_release_identity.py` | **4 failed / 1 passed**(`derive_release_id` 가 `sample_scope` 를 받지 않는다) |

**완화 경로 게이트(D-2)의 다섯 test 는 RED 가 아니다** — 그것들은 지금 참인 성질을 잠그는 회귀 게이트다
(모집단 test 하나만 RED 였다: `effective_date` 가 아직 키가 아니었다). 그 다섯이 **실제로 무는지**는 RED 가
아니라 변이로 쟀다(§4 의 M8a·M8b·M9·M10a·M11).

## §3 acceptance — CI `ml-engine` job 명령 그대로

CI 워크플로의 그 job 단계를 **같은 명령으로** 로컬에서 돌렸다(`uv sync --frozen --all-extras` 부터
wheel 재수출까지). 판정 SHA 에서의 결과:

| 단계 | 명령(요지) | exit | 핵심 결과 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | — |
| S-1b | serving extras 분리(금지 패키지 다섯 import 실패 확인 → dev 복구) | 0 | 금지 패키지 설치 0 |
| S-2 | `ruff check .` · `ruff format --check .` | 0 · 0 | 236 files already formatted |
| S-3 | `mypy --strict src/ml_engine` | 0 | 101 source files, no issues |
| S-4 | `lint-imports` | 0 | **8 kept, 0 broken** |
| S-5 | `pytest tests -q` | 0 | 착수 라운드 **1679 passed** · 수정 라운드 **1700 passed**(착수 base 1628 → +72) |
| S-6 | `python tools/design_ratchet.py --check` | 0 | allowlist 밖 위반 0 |
| S-7 | 재활용 출처 두 자리 대조 + 양성 대조 | 0 | 어긋난 evidence 가 실제로 실패 |
| S-9 | Python 버전 두 자리 대조 | 0 | — |
| S-11 | wheel 빌드 + 설치본 재수출 | 0 | 1 passed |

위 표는 **착수 라운드(`8284e602`)** 측정이고, 수정 라운드 HEAD(`9ddfc542`)에서 **같은 열한
단계를 다시 돌려 전부 exit 0** 이다(S-5 1700 passed · S-3 102 files · S-4 8 kept/0 broken ·
S-6 위반 0 · S-2b 239 files formatted).

**S-6 가 한 번 붉었다.** ML-05 가 더한 `sample_scope` 한 줄이 `_assemble_window_result` 를 함수 길이 소프트
한도 위로 밀었고, 그 라운드에서는 ML-05 뒤에 래칫을 다시 돌리지 않아 **전건 acceptance 에서 처음** 드러났다
(S-5 도 같은 게이트 test 로 1 failed). 파생을 `_window_release_id` 로 빼고 전건을 다시 돌려 위 표를 얻었다 —
**이 표는 그 수정 뒤의 HEAD(`8284e602`) 측정이다.**

`check`·`web`·`container` job 은 이 slice 의 대상이 아니다 — `git diff --name-only df4976cc..HEAD` 에
Kotlin·web·docker 경로가 **0 건**이다(변경은 `ml-engine/**` · `docs/discovery/capability-map.md` · evidence).

## §4 변이 — 각각 바꿔치우기, 전부 커밋 뒤

변이는 **더하기가 아니라 바꿔치우기**다. 산출물을 전부 커밋한 뒤 돌리고, 원복은 변이 파일 하나만
(`git checkout -- <그 파일>`). 각 행의 표적은 그 방어가 걸린 test 파일들이다.

| 변이 | 무엇을 바꿨나 | 결과 |
| --- | --- | --- |
| M1 | 승격에서 인용 분기 제거(effective date 를 칸만 두기) | **4 failed / 47 passed** |
| M2 | 정책 checksum payload 에서 `effective_date` 제거 | **2 failed / 41 passed** |
| M3 | effective date 를 KST 가 아니라 UTC 로 해석 | **3 failed / 5 passed** |
| M4 | 승인 칸 무시(legacy 의 조용한 대체 복귀) | **2 failed / 22 passed** |
| M5 | 모집단 항등식 제거 | **1 failed / 15 passed** |
| M6 | 측정 불가 행을 사유 없이 버리기 | **3 failed / 13 passed** |
| M7 | `sample_scope` 를 model_version 해시에서 빼기 | **3 failed / 27 passed** |
| M8a | 로더 밖에서 정책 객체를 **실제로 조립** | **1 failed / 5 passed** |
| M8b | 같은 조립을 **별칭**으로(`import … as _P` 뒤 `_P(...)`) | 처음 **6 passed(초록)** → 게이트 보강 뒤 **1 failed / 5 passed** |
| M9 | CLI 플래그 `--maturity-threshold` 신설 | **1 failed / 5 passed** |
| M10a | 환경 변수 `ML_ENGINE_MATURITY_THRESHOLD` 를 실제로 읽기 | **1 failed / 5 passed** |
| M11 | wire 필드 이름과 겹치는 정책 키 등재 | **2 failed / 4 passed** |
| M12 | 조립 근에서 시간축 정책을 직접 조립 | **1 failed / 7 passed** |
| **C** | **음성 대조** — 주석 한 줄만 바꿔치움(거동 0) | **14 passed(초록)** |

### 계측을 두 번 틀렸고 그것이 한 건의 보강을 낳았다

첫 판의 **M8 은 호출이 아니라 이름 참조**(`return EvaluationPolicy`)였고 **M10 은 주석**이었다. 둘 다 초록이
나왔는데, 그것은 게이트가 헐거워서가 아니라 **게이트가 지키는 것을 심지 않은 변이**였기 때문이다 — 변이가
「바꿔치우기」가 아니면 초록은 아무것도 말하지 않는다. 실제 조립·실제 환경 읽기로 다시 심어 둘 다 RED 를 받았다.

그 과정에서 **M8b 가 진짜 우회를 찾았다**: 호출 이름만 보던 「생성 자리 하나」 게이트가 `as` 별칭을 지나갔다.
게이트를 **그 모듈 안에서 그 타입을 가리키는 이름 전수**(원래 이름 + 별칭 + 한 단계 재대입)로 보강하고 같은
변이를 다시 돌려 RED 를 실측했다. 남은 한계(재대입 두 단계 이상)는 그 게이트 docstring 과
`checklist.md` 제한 3 에 적었다.

## §4b 수정 라운드 (v not-ready @`8284e602` · cr approve)

finding 별 별도 커밋이고 각각 RED 선행이다.

| finding | 처방 | RED/변이 실측 | 커밋 |
| --- | --- | --- | --- |
| **v H-1**(차단) 완화 불가 게이트가 이름 문자열 술어 | 겹 둘 — `InitVar` 생성 토큰 + 출처 재검증(제품 경로) | V1·V2·V3 형태가 각각 `TypeError`/`ValueError` · 위조 토큰 객체가 재검증에서 `PolicySourceMismatch` · 제품 경로에서 job 실패(통제군: 정직한 정책은 끝까지 돈다) | `8f6ae6ac` |
| cr M-1 측정 불가 행의 정책 version | 검사를 두 갈래 공통 자리로 + `UnmeasurableTimeAxis.policy_version` | 새 test 둘이 앞 판에서 `AttributeError`·「예외 없음」 | `352e6c59` + `75d2cc96` |
| cr M-2 표 항등식이 provenance 를 안 묶는다 | `__post_init__` 등식 둘(창별 출처 합 = 분모 · 대체 계수 = 창들의 합) | 새 test 셋이 「DID NOT RAISE」로 붉다(**3 failed** 실측) | `85a68e64` |
| v L-2 `yes` 가 승인으로 읽힌다 | bool → 닫힌 어휘 `TimeAxisSubstitute` | 느슨한 표기 여덟 전부 거부 + 어휘 두 값 양성 대조 | `da405f6c` + `847f7784` |
| v M-1 창 경로의 scope 축 미잠금 | `sample_scope` 만 다른 두 manifest 로 창 id 가 갈리는 test | v 변이 **R4**(그 자리 상수화) 재현 → 이 test 만 RED(27 passed) | `a0e9f1cc` |
| cr M-6 게이트 AST 보조 복제 | `tests/ast_sites.py` 한 자리 | — (구조) | `6cc07e06` |
| cr M-3 금지 인자 열거가 스키마를 안 따라온다 | 정책 키에서 **생성** + 비정책 이름 명시 예외 | — (모집단 등식 test) | `9b010c79` |
| cr M-5 · cr LOW 넷 | 문면 정정 + env 축 별칭 구멍·`os` 뿌리 비교 | — (문면) · 별칭 구멍은 ④ 축의 M8b 와 같은 갈래 | `954e62c1` |
| cr M-4 장부 미갱신 | `m5/5c2`·`m5/5d` 에 새 키 행(in_scope 확장 승인) | — (장부) | `9ddfc542` |
| 설계 래칫 | `policy_source.py` 분리(파일 500) · `_maturities` 분리 · docstring 축약 | 라운드 중 **2건 붉음 → 0** | `8f6ae6ac` 안 |

**계측을 한 번 더 틀렸다**: cr M-1 의 회귀 test 를 **붉은 채로 커밋했고**(`352e6c59`) 그 test
자체도 축을 재지 못했다 — 두 fixture 가 승인 칸만 다르고 version 이 같아 정책 version 검사를
트리지 않았다. 다음 커밋(`75d2cc96`)에서 다른 version 의 정책을 따로 만들고 양성 대조를 붙였다.
같은 라운드에 `da405f6c` 는 serving 두 test 를 함께 고치지 않아 collection error 를 남겼고
`847f7784` 가 받았다 — **표적을 좁혀 돌린 대가**이고, 그래서 라운드 끝에 전건을 다시 돌린다.

### v H-1 의 세 변이 재현 — **게이트가 아니라 형태가 막는다**

버릴 clone 에 verifier 의 V1 을 그대로 심었다(`server.py::_preload` 의 평가 줄을
`_tuned(load_…)` 로 바꿔치우고 그 함수가 `os.environ["ML_ENGINE_GATE_T"]` → `replace`).

| 축 | 결과 |
| --- | --- |
| 완화 경로 게이트 + 숫자 리터럴 게이트 | **16 passed(초록)** — 이 층은 `replace` 를 세지 않는다. 앞 판과 같다 |
| 그 코드가 **실제로 도는가** | `ML_ENGINE_GATE_T=0.01` 로 호출 → **`ValueError: InitVar 'parsed' must be specified with replace()`**. 파일 임계 2.58 은 그대로다 |
| V2 `type(p)(**vars(p))` · V3 `getattr(…, "Evaluation"+"Policy")(…)` | 각각 **`TypeError`**(토큰 누락) |
| V4 토큰 절취 위조 | 객체는 만들어진다 → 출처 재검증이 `PolicySourceMismatch`, 제품 경로가 job 실패. 통제군: 정직한 정책은 끝까지 돈다 |

**문면을 정확히 적는다**: V1·V2·V3 는 「게이트가 붉어져서」 막히는 것이 아니라 **그 코드가
자기 일을 할 수 없어서** 막힌다 — 완화가 탐지되는 것이 아니라 **불가능**하다. 탐지가 필요한
잔여(동적 절취)는 V4 축이 받고, 그쪽은 제품 경로에서 job 실패로 드러난다. 이름 대조 층
(`src` 생성 자리 하나)은 그대로 보조로 남겨 **정적으로** 가져가는 길을 계속 센다
(M8a·M8b RED).

**거부한 지적 하나**: cr LOW `_matched_keys` 부분 일치 → **등재**(`checklist.md` 제한 3b).
좁히면 접두·접미를 붙인 완화 이름이 조용히 통과하고, 지금 형태의 대가는 조용한 구멍이 아니라
사람이 볼 신호다.

## §4c 수정 라운드 2 (v 표적 재검증 `9ddfc542` **not-ready** — high H-2)

**이 라운드는 세션이 끊긴 뒤 다른 에이전트가 이어받았다.** 앞 세션이 H-2 구현을 절반 써 둔 상태
(미커밋: 등록부 모듈 신규 + 로더·재검증·L-1 문면)에서 멈췄고, 이어받아 **그 반쯤 된 편집을 읽고
기록과 대조한 뒤** 계속했다. 그래서 H-2 의 RED 선행은 **라운드 전체에 대해 성립하지 않는다** —
아래 표가 어느 축이 참 RED 이고 어느 축이 변이-RED 인지 구분한다.

| finding | 처방 | RED/변이 실측 | 커밋 |
| --- | --- | --- | --- |
| **v H-2**(차단) 완화 정책 **파일** 경로가 전 게이트 초록 | 승인 등록부(승인 version → 파일 sha256) + **조립 근 하나**가 승인을 묻는다 | **참 RED**: 조립 근 축은 구현 전이었다 — 이름을 바꿔 단 완화 사본이 `_preload` 를 통과(1 failed) · **변이-RED**: 등록부·로더 축은 앞 세션이 이미 써 둬서 변이로 쟀다(MH2·MH2b·MH2c·MH3·MH4) | `a50a0270` → `ae2b7e3e` |
| **v M-1** H-1 의 네 검사가 잠겨 있지 않다 | 각 검사를 **단독으로** 겨누는 test 넷 | 변이 **M1a·M1b·M1c·M1d 전부 RED**(각 1 failed) | `f2ce1997` |
| **v M-2** `yes` 가 파일에 bool 로 들어가지 않는다 | 출하 원문의 한 줄만 비인용으로 치환 + 「bool 로 들어갔는가」 동반 단언 | 변이 **M2a RED**(로더가 bool `True` 를 승인으로 받게 함) | `f2ce1997` |
| **v M-3** 재검증의 제품 경로 단일성 미잠금 | `run_holdout` 호출 자리 집합 등식 + `build_training_pipeline` 조립 자리 등식 | 변이 **MH6·MH7 RED** | `a50a0270` · `ae2b7e3e` |
| **v L-1** 사라진 키 이름·bool 문면 잔존 | 네 자리 + docstring 정리 | — (문면) · 옛 키 이름 한 자리는 **인용으로 남겼다**(문면에 명시) | `6cf5451a` |
| **v L-2** in_scope 확장의 승인 주체 미기록 | 팀장이 scope.md 「계약 갱신 2026-11-06」에 커밋 | — (계약) | `e9d24484`(팀장) |
| effective date 값의 지위 | 알려진 제한 7 — 사용자 확인 항목으로 올렸다 | — | **2026-11-06 사용자 승인으로 종결**(§4h) |

### 계약 문면에서 **두 자리를 옮겼다** — 둘 다 걸어 보고 실측으로 되돌렸다

계약(scope.md H-2 처방)은 「**로더와 재검증**이 등록부에 없는 version·지문이 다른 파일을 거부」로
적었다. 두 자리 모두 실제로 걸고 전건을 돌렸다.

| 걸었던 자리 | 실측 | 판단 |
| --- | --- | --- |
| 재검증(`verify_policy_source`) | **조립 근 test 일곱 붉음** | 그 test 들은 작은 데이터셋으로 돌려고 승인 밖 임계를 파일로 만들어 쓴다 — 승인을 거기서 물으면 재던 것을 잃는다 |
| 뿌리 로더(`registry.policy.load_policy`) | **32 failed · 79 errors**(11 파일) | 값 불변식 test 들이 파생 정책에 현실적인 version 이름을 쓴다. 출하 추론 정책이 일부러 불완전해(`agency_sample_threshold` 미선언) **네 helper 가 placeholder 사본으로만** 로드하고, `test_epoch` 하나는 그 이름이 승격 인용 문면에 나오는 것을 단언한다 |

그래서 승인을 묻는 자리는 **운영자의 선택(env)이 들어오는 조립 근 하나**다. 닫는 집합은 같다 —
이름을 유지한 편집 사본(지문 불일치)과 이름을 바꿔 단 사본(미등록)을 **그 한 자리가 함께** 거부하고,
세 갈래(둘의 거부 + 무편집 사본의 통과)를 env 축 test 가 잰다. 한 자리로 충분한 근거는 **사슬의
단일성**이고 집합 등식 넷이 그것을 진다(설계 검토 §6).

**runbook 은 새로 만들지 않았다** — 「세 자리를 같은 커밋에서」 절차의 자리는 이미 승인 장부 둘
(`m5/5c2`·`m5/5d` `policy-values.md`)이고, 두 문서가 머리말에서 그 절차를 정본으로 들고 있다. 그
문서에 **승인된 산출물 지문 표**를 더해 절차를 그 자리에서 완성했다. `docs/runbook/**` 는 이 slice 의
in_scope 밖이라 손대지 않았다.

### acceptance — 판정 SHA `0e75f29e`(main `3ad4f3f6` 병합 커밋), CI `ml-engine` job 명령 그대로

열한 단계 전부 **exit 0**: S-1 · S-1b(금지 패키지 0) · S-2a/2b · S-3(103 files) · S-4(**8 kept,
0 broken**) · S-5 **1719 passed**(앞 라운드 1700 → +19) · S-6(위반 0) · S-7(양성 대조가 실제로 실패) ·
S-9 · S-11(1 passed).

### 변이 — 바꿔치우기 **17 전부 RED** + 통제군 GREEN + **열린 잔여 하나를 측정으로 등재**

| 변이 | 무엇을 바꿨나 | 결과 |
| --- | --- | --- |
| MH2 | 조립 근의 승인 대조 전체 제거 | **RED** |
| MH2b | 지문 불일치만 거부(미등록은 통과) | **RED** |
| MH2c | 미등록만 거부(이름 유지 편집 사본은 통과) | **RED** |
| MH3 | 등록부 지문 한 글자 변경 | **RED** |
| MH4 | 등록부에서 `inference-v1` 제거 | **RED** |
| MH5b | 조립 근이 승인 미요구 로더를 **정적으로** 호출 | **RED** |
| MH5c | 같은 호출을 동적 import 의 **속성**으로 | **RED** |
| **MH5** | 같은 호출을 이름을 **쪼개** 만들어(`"load_" + …`) | **GREEN — 열린 잔여**(제한 3e) |
| MH6 | src 다른 함수에 둘째 `run_holdout` 자리 | **RED** |
| MH7 | src 다른 함수에 둘째 pipeline 조립 자리 | **RED** |
| M1a·M1b | 생성 토큰의 타입 검사 · 지문 등식 무력화 | **RED** · **RED** |
| M1c·MV4 | 재검증의 바이트 축 · 값 등식 무력화 | **RED** · **RED** |
| M1d | report 의 정책 파일 지문을 상수로 | **RED** |
| M2a | 로더가 YAML bool `True` 를 승인으로 받게 함 | **RED** |
| MW | 제품 경로의 재검증 호출 제거 | **RED** |
| **C** | **음성 대조** — 주석 한 줄만(거동 0) | **GREEN** |

변이는 전부 **커밋 뒤**에 돌리고 원복은 **원문 문자열로** 되돌렸다(`git checkout --` 는 미커밋
evidence 를 함께 지운다). 하네스가 매 변이 뒤 원문 일치를 단언한다.

**MH5 를 숨기지 않는다**: 집합 등식 층은 이름이 실행 시점에 만들어지면 보지 못한다. H-1 라운드의 같은
잔여는 **값 대조**가 받았지만 여기서는 받지 못한다 — 이름을 바꿔 단 사본은 자기 파일과 값이 같아
재검증을 지나기 때문이다. 성립하려면 `app/server.py` 를 편집해야 하고, 이 저장소는 같은 종류의 경계
(동적 import)를 app 의 HTTP 금지 계약에서 이미 알려진 제한으로 적고 있다.

## §4d 수정 라운드 3 (v not-ready @`0e75f29e` H-3 · cr request_changes @`0e75f29e` H-1)

차단 둘이 **같은 뿌리**다: 라운드 2 가 승인을 「조립 근 한 자리」로 옮기며 「게이트에 닿는
정책은 그 자리를 거친 하나」를 근거로 삼았는데, 그 단일성은 **호출 자리**만 말하고 그 자리를
지나는 **값**은 말하지 않는다.

| finding | 처방 | RED/변이 실측 | 커밋 |
| --- | --- | --- | --- |
| **v H-3a**(차단) 위조 객체 + 내용이 일치하는 미승인 사본(MX1b) | 재검증이 **객체의 자기 주장**으로 승인을 묻는다 | **참 RED 2**(재현물 둘이 `PolicySourceMismatch` 없음) · 변이 N1·N2 RED | `c57d7fd2` |
| **v H-3b = cr H-1**(차단) 등록된 `inference-v1` 사본이 env 만으로 부팅 통과 | `load_approved_inference_policy` ← `_preload`(한 번 읽기) | **참 RED 3**(편집 사본 둘·이름 바꾼 사본 전부 ACCEPTED) · 변이 N3·N3b RED | `7b31661e` |
| cr M-3 `windows_excluding_substitute` 무검사 | 등식 셋(제외 표에 대체 행 없음 · 창별 개찰 계수 등식 · 대체 0 이면 두 표 동일) | **참 RED 3** · 변이 N4a·N4b·N4c RED · 생산자 양성 대조 | `8c6d5830` |
| cr M-4 새 조립 근 로더에 시그니처 게이트 없음 | 정책 파일 입구 **전수를 구조로** 생성 + 집합 등식 + `parameters == ["path"]` | 변이 N5(주입 매개변수)·N5b(다른 이름의 새 입구) RED | `cb60e7fc` |
| cr M-5 죽은 허용 항목 | `_NON_POLICY_PARAMETER_NAMES` 와 도달 불가 가드 둘 삭제 | 삭제 뒤 전건 초록 | `cb60e7fc` |
| cr M-1·M-2 뿌리 로더 오귀속 | 조립 근 docstring · 승인 장부 5c2 §4 를 「어느 층이 무엇을 막는가」로 | — (문면) | `c57d7fd2` · `cb60e7fc` |
| cr LOW 넷 · v L-1 | 지문은 줄끝 정규화 텍스트 해시 · `enclosing_functions` 는 「가장 바깥」 · `atexit` 정리 · 사유 어휘 접힘 등재 · 미등록 version 상수 문면 | — (문면·등재) | `cb60e7fc` |

### 계약 문면으로 **되돌아왔다**

라운드 2 는 계약(「로더와 **재검증**이 거부」)에서 재검증을 뺐고, 이 라운드가 **재검증에
승인을 넣어** 그 문면으로 돌아왔다. 뿌리 로더 쪽은 여전히 빼 둔다(사유는 라운드 2 의 실측 —
32 failed·79 errors). 라운드 2 에 재검증이 붉혔던 조립 근 test 일곱은 **test 쪽에서** 해결했다:
그 test 들이 작은 데이터셋에 맞춘 **파생(미승인) 정책**을 쓴 것이 원인이었고, **출하 승인 정책
그대로** 바꿔도 재던 것을 잃지 않는다 — 그 데이터셋은 `settlements.jsonl` 이 비어 성숙한 주가
0 이라 평가 창이 선택되지 않고 임계가 애초에 물리지 않는다. **test 전용 승인 주입 경로는
만들지 않았다**(팀장 지시) — src 가 같은 주입을 쓸 수 있는 우회 표면이다.

### MX1b 재현물을 수정 뒤 HEAD 에 다시 적용 — **거동은 닫혔고 게이트는 여전히 조용하다**

버릴 clone 에 `policy_tuning.adapters.py` + `mx1b-server.diff` 를 그대로 적용하고 쟀다.

| 축 | 라운드 2 판정 SHA | 이 라운드 |
| --- | --- | --- |
| `verify_policy_source` | **PASS** | **MISMATCH(UNREGISTERED_VERSION)** — t=0.1 이 게이트에 닿지 못한다 |
| 표적 게이트 76 · ruff·mypy·lint-imports·래칫 | 전부 초록 | **전부 초록(그대로)** |

**정확히 적는다**: MX1b 는 수정 뒤에도 **어떤 게이트도 붉히지 않는다** — 그 코드는
`ML_ENGINE_GATE_T` 가 설정될 때만 발화하고 test 는 그것을 설정하지 않는다. 바뀐 것은
**런타임 fail-closed** 다(job 이 `POLICY_SOURCE_MISMATCH`). 「그런 코드가 저장소에 들어오는
것 자체」를 막는 층은 없고 src 리뷰가 그 자리다(알려진 제한). 방어 **메커니즘** 제거는
붉다(N1·N2).

### acceptance · 변이

- **CI `ml-engine` job 열한 단계** 명령 그대로, 판정 SHA 에서 전부 **exit 0**(S-5 **1732
  passed** — 앞 라운드 1719 → +13).
- **변이 10 전부 RED + 통제군 GREEN.** 자리별: 재검증 승인 축(N1) · 제품 경로 재검증 호출(N2) ·
  추론 승인 대조(N3)와 그 호출(N3b) · 대체 제외 등식 셋(N4a·N4b·N4c) · 정책 입구 시그니처
  (N5)와 새 입구(N5b).
- **예측이 한 칸 틀렸다(정직하게 등재)**: 「승인을 재로드한 파일에 걸면 바이트 축 제거 변이가
  조용히 초록이 된다」를 근거로 **역할 접힘 변이(N1b)**를 GREEN 으로 기대했는데 **RED** 였다.
  잡은 것은 바이트 축 test 의 **문면 단언**(`"파일 바이트" in detail`)이다 — 즉 역할 분리를
  지키는 것은 「거부가 나는가」가 아니라 「어느 축이 거부했는가를 test 가 읽는가」다. 그
  단언이 없었다면 예측대로 조용히 초록이었다.
- 낙진 **셋**을 고쳤다: `test_server_prediction` 의 추론 정책 helper 가 출하 값을 `safe_dump`
  로 다시 써 **같은 값의 다른 서식** 사본을 만들었고(그 `setdefault` 는 5F-1 이후 무동작),
  그 사본이 미승인으로 떨어져 「넷 중 하나만 깨졌다」 단언이 붉었다 — 출하 경로를 그대로 쓴다.

## §4e 수정 라운드 4 (v not-ready @`cb60e7fc` H-4 · cr request_changes @`cb60e7fc` H-1)

| finding | 처방 | RED/변이 실측 |
| --- | --- | --- |
| **v H-4**(차단) 값 축이 **직렬화**를 비교하고 게이트는 **거동**을 소비한다(F6·F7·F8) | 축을 더하지 않고 **값의 출처를 바꾼다** — 재검증이 **승인 파일에서 재구성한** 정책을 돌려주고 `_run_holdout` 이 그 반환값을 게이트에 넘긴다 | **참 RED 4**(재구성 반환 · 원시 타입 · F8 격리 · 파이프라인 배선) · 변이 O1·O2 RED |
| **cr H-1**(차단) = v L-1 모듈 docstring 이 「왜 승인을 넣지 않는가」를 측정과 함께 반대 논증 | `policy_source`·`policy_approvals` 모듈 docstring 을 현행 설계로 다시 썼다(묻는 자리 셋 · 축 셋 · 반환값 · 해소된 사유) | — (문면) |
| cr M-1 제외 표 등식이 대체 행 있을 때 무발화 | `_check_exclusion_denominators` — 창별 출처 등식을 **제외 표에도** | **참 RED** · 변이 O3 RED · 생산자 양성 대조 |
| cr L-3 `Observed` 가 비율 > 1 을 받는다 | `settled <= opened`·음수 금지를 **`Observed.__post_init__`** 로 | **참 RED** — cr 가 요청한 「제외 표 `settled<=opened`」는 **더 강한 자리**로 올렸다(두 표·모든 창에 걸린다). 제외 표 쪽에 같은 검사를 두면 **도달 불가 분기**라 두지 않았다 |
| cr M-2 · v M-7 입구 전수가 모듈 열거 + 문자열 술어 | 모집단을 **src 전체**(`walk_packages`) × **해석된 반환 타입**(`get_type_hints` + `issubclass`)으로. 파일 입구 7 + 명시 예외 4 의 **집합 등식** | 변이 O4(별칭)·O5(인자 개명)·O6(넷째 모듈) RED |
| cr M-3 우회 표가 한 라운드 뒤처져 제한 3d 와 반대 | 행 14 갱신 + 행 15·16·17 신설 | — (장부) |
| cr M-4 `_run_holdout` docstring 이 승인 축을 말하지 않는다 | 세 축 + 반환값 문면으로 다시 썼다 | — (문면) |
| v M-6 추론 축 부팅 뒤 바꿔치우기 | **등재**(제한 3i) + §0 경계 — verifier 가 「등재로 충분」이라 적은 쪽 | 실측: 창 값 1 이 파이프라인에 들어간다 |
| cr L-1 「등재」가 가리키는 자리가 비어 있다 | 제한 3h 신설 | — (장부) |
| cr L-2 조립 근 test 전제가 단언으로 안 잠겼다 | 전제 test 하나(창 0 · 승격 인용 불가 · `min_evaluation_rows==100`) | — (잠금) |
| cr L-4 `_opened_at_counts` 가 같은 시작 창을 접는다 | dict → 정렬 tuple(다중집합) | — (구조) |
| cr L-5 추론 축 게이트가 `tests/evaluation/` 에 있다 | 머리 주석 — 모집단이 **축 전부**를 센다(이제 src 전체라 사실이 됐다) | — (문면) |

### H-4 의 처방을 MXI 재현물로 실측

조립 근의 정책 인자를 F7 헬퍼로 감싸는 **한 줄**을 그대로 심고 출하 경로를 따라갔다.

| 축 | 라운드 3 판정 SHA | 이 라운드 |
| --- | --- | --- |
| 조립 근이 주입한 객체 | `-t = -0.1`(`_Lenient`) | `-t = -0.1`(그대로 — 주입은 막지 않는다) |
| **게이트가 쓰는 객체** | `-t = -0.1` | **`-t = -2.58`, 정확히 `float`** |
| `verify_policy_source` | PASS(받은 객체 반환) | PASS(**재구성 객체** 반환) |
| checksum == 승인본 | True | True(같다 — 그래서 축이 아니었다) |
| 추론 창(MI1) | [1] | **[1] — 여전히 열려 있다**(제한 3i) |

**§0 에 위협 모델 경계를 명문화했다**: 이 게이트는 「승인 밖 값이 **판정에 닿지 못한다**」를
지고 「악의 코드가 **들어오지 못한다**」는 지지 않는다. 유입은 리뷰, 승인 파일 변경은 CI,
동적 절취·추론 부팅 뒤는 등재(3e·3h·3i). **그 문장은 라운드 5 가 좁혔다** — 「코드가 **건넨
객체의 거동으로는** 판정에 닿지 못한다」가 측정 크기이고, 건네지 않은 객체에 손을 뻗는 축
(`gc`)은 경계 밖이다(제한 3j).

### acceptance · 변이

- **CI `ml-engine` job 열한 단계** 전부 **exit 0** @`027b3fb7` · S-5 **1743 passed**
  (앞 라운드 1732 → +11).
- **변이 10 전부 RED + 통제군 둘 GREEN**: O1(재구성 반환 되돌림, 3 failed) · O2(배선이 반환값
  무시) · O2b(재검증 호출 제거) · O3(제외 표 창별 등식) · O3b(그 검사 호출) · O3c(`Observed`
  비율 가드) · O4(타입 별칭) · O5(인자 개명) · O6(넷째 모듈) · O7(승인 끄기 키워드).

### 라운드 중 **내 게이트가 두 번 조용했고 둘 다 측정이 잡았다**

정직하게 적는다 — 둘 다 「게이트를 넣었는데 비어 있었다」 계열이고, 이 라운드가 닫으려던 바로
그 우회였다.

| 자리 | 어떻게 드러났나 | 뿌리 |
| --- | --- | --- |
| 입구 전수의 모집단이 **전건에서만** 줄었다 | **전건 S-5 1 failed**(표적은 초록) — `training.load_training_policy` 가 모집단에서 빠졌다 | `tests/training/conftest.py` 가 `ml_engine.training.*` 를 `sys.modules` 에서 지우므로, 모듈 수준에서 묶은 class 객체와 재-import 된 class 가 **다른 객체**이고 `issubclass` 가 조용히 거짓이 된다(이 저장소가 5C-1 에서 만난 함정). 타입을 **순회와 같은 import 패스**에서 꺼내 닫았다 |
| 입구 전수가 **타입 별칭을 풀지 않았다** | **변이 O4 가 GREEN** — 그 게이트가 닫으려던 바로 그 우회 | `typing.get_type_hints` 는 `type X = …`(PEP 695) 별칭을 풀지 않는다(반환이 `TypeAliasType`, `get_args` 빈 tuple — 실측). 주석을 끝까지 펴는 재귀 + 자기참조 별칭 `id` 가드로 닫았고, 펴고 나니 입구 하나(`backtest_job._load_policies`)가 더 드러나 **사유와 함께** 명시 예외로 등재했다 |

## §4f 수정 라운드 5 (v not-ready @`027b3fb7` H-5 · cr request_changes @`027b3fb7` H-1) — 마지막 라운드

사용자가 상한(5회) 밖 **한 라운드**를 예외로 승인했다. 두 심판의 진단이 같았다:
**재검증이 호출자가 건넨 객체의 거동을 믿는다.** 네 라운드의 지적이 그 뿌리의 다른 자리였다 —
r3 주장 축 · r4 임계 값 · r5 재구성의 입력과 비교 피연산자.

| finding | 처방 | RED/변이 실측 |
| --- | --- | --- |
| **v H-5 = cr H-1**(차단) 재구성의 입력과 비교 피연산자가 호출자 통제하 | ⑴ 입력을 `Path(str(...))` 로 정규화 ⑵ 세 축 뒤 **재구성 객체 자신**에게 승인을 묻는다(피연산자 둘이 로더 산출) | **참 RED 3**(N1·N2·N6) · 변이 P1·P2 RED · forge5 재실측 |
| cr M-1 입구 전수가 **조용히** 줄는 자리 셋 | ⑴ 해석 실패를 **등식으로** 잠근다(선언 여섯) ⑵ 순회에 **클래스 멤버** 포함 ⑶ 타입 판정을 동일성 + `(모듈, 이름)` 좌표로 | 변이 P3(TYPE_CHECKING)·P4(staticmethod) RED |
| cr M-2 분자가 묶이지 않는데 「묶는다」고 적는다 | **문면을 좁히고 등재**(제한 3k) — 묶을 데이터가 표에 없다(출처별 정산 계수 없음), 닫으려면 `WindowTimeAxis` 확장이고 slice 밖 | — (장부) |
| v M-8 | §0 에 경계선 한 문장 — **건넨 객체가 하는 일**(안쪽) / **건네지 않은 객체에 손 뻗기**(바깥, `gc`·프레임·`ctypes`) + 제한 3j | 실측 N3 통과 |
| v L-1 전수 주장이 넓다 | 모집단이 이제 클래스 멤버까지 센다 + 「이 전수가 지는 것은 **유입 층**」을 머리에 적었다 | — |
| v L-2 3g 「어긋날 자리가 없다」가 N2 에서 거짓 | 「**stdlib 읽기에 대해** 참」으로 좁히고 N2 사실을 적었다 | — |
| v L-3 `## 0.` 제목 둘 | 유일 제목으로(「라운드 4 명문화 · 라운드 5 정정」) | — |
| cr L-1·L-2 예외 사유·등식 docstring 이 r4 뒤 거짓 | 「경로를 **인자로** 받지 않는다」 · 「이 등식이 지는 것은 **배선의 유일성**」 | — |
| cr L-4·L-5·L-6 | `resolve_maturity` 결과 셋 · src 전 모듈 import 사실 · 음수 가드를 먼저 | test 하나 추가 |

### 장부에서 **범주 주장을 전부 측정 크기로 좁혔다**

cr 의 상한 진단이 이것이었다 — 「인스턴스는 닫혔고 장부가 범주 주장을 쓴다 → 다음 라운드가
그 주장의 반례를 만든다」가 네 라운드 연속이었다. 「위조가 어떤 모양이든」·「어떻게 만들거나
비틀든」을 지우고 **측정한 다섯 모양과 그것들이 공유하는 성질**(건넨 객체의 거동)로 바꿨다.
자리: design-review §0a·§0b·§8·§9 · `policy_source` 모듈 docstring · checklist D-2c·3g·3h.
라운드 끝에 `command grep` 으로 확인했다 — **찾은 어휘**는 「어떤 모양이든」·「어떻게 만들거나
비틀든」·「어떤 경로로 왔든」이다.

**그 sweep 의 설계 결함을 기록한다**(cr r6 L-6): 옛 어휘를 찾는 grep 은 **같은 라운드가 새로
쓴** 단언을 찾지 못한다 — 라운드 5 가 처방을 설명하며 들여놓은 「`str()` 과 `Path()` 가 각각
정확 타입을 낸다」가 그 공백으로 빠져나가 라운드 6 의 지적이 됐다. 다음에 같은 정리를 할 때는
「지운 문장 목록」이 아니라 **「이 라운드가 새로 쓴 단언 목록」**을 대상으로 돌린다(같은 비용으로
더 넓다).

### forge5 재실측 (verifier 재현물 그대로)

| 위조 | 027b3fb7 | 이 라운드 |
| --- | --- | --- |
| F0 승인 그대로 | PASS · gate False | 같다 |
| F6 프레임 property · F7 float 하위 클래스 · F8 통과 뒤 입력 변경 | PASS · t=2.58 · gate False | **같다**(무변화) |
| **N1** 거짓 `__eq__` 지문 + 완화 파일 | **PASS · gate True(0.1)** | **MISMATCH**(재구성 승인) |
| **N2** `read_text` 가 가로채는 경로 | **PASS · gate True(0.1)** | **MISMATCH**(바이트 축) |
| **N6** 읽을 때마다 다른 `source` | **PASS · gate True(0.1)** | **MISMATCH**(재구성 승인) |
| N4 동일 바이트 사본 + t=0.1 · N5 version 별칭 | MISMATCH | 같다 |
| **N3** `gc` 로 반환 객체에 손대기 | PASS · gate True | **PASS · gate True — 경계 밖**(제한 3j) |

### acceptance · 변이

- **CI `ml-engine` job 열한 단계** 전부 **exit 0** @`0389b728`(main `97ed49c4` 병합 커밋) ·
  S-5 **1750 passed**(앞 라운드 1743 → +7).
- **변이 7 전부 RED + 통제군 둘 GREEN**: P1(재구성 객체 승인 질문 제거, 2 failed) ·
  P2(입력 정규화 제거) · P3(TYPE_CHECKING 전용 반환 주석 입구) · P4(클래스 `@staticmethod`
  입구) · P6(배선이 반환값 무시) · P7(`Observed` 비율 가드) · P8(제외 표 분모 등식).
- **P5 는 자기 검사라 GREEN 이다**(기대대로, 결함 아님): 해석 실패 **등식 자체**를 공허하게
  바꾸는 변이는 같은 test 가 잡을 수 없다 — 어떤 test 든 자기 단언을 약화하면 조용하다. 그
  자리를 지키는 것은 리뷰이고, 등식이 **실제로 지고 있다**는 것은 P3·P4 가 RED 로 보인다.

### main 병합 (원천 교체)

`97ed49c4`(PR #134) 병합. **겹침 0 을 계수로 확인했다** — `3ad4f3f6..97ed49c4` 가 만진 9 파일에
`ml-engine/**` 과 이 slice 의 in_scope 가 하나도 없다(`ml-engine` diff **0 파일**). 충돌 해소
줄 0, 병합이 in_scope 에 가져온 변경 **빈 출력**. rollback 원천을 그 SHA 로 옮겼다.

## §4g 승인 전 일괄 (v ready-for-review @`0389b728` · cr approve @`0389b728`)

두 심판이 **차단 0** 으로 통과했고, 장부층 지적만 남아 승인 전 한 커밋 묶음으로 처리했다.

| finding | 처방 | 실측 |
| --- | --- | --- |
| **cr M-1 = v L-1** 「`str()` 과 `Path()` 가 각각 정확 타입을 낸다」가 거짓 | **`str()` 을 지웠다**. N2 를 닫는 것은 `Path()` 다(`PurePath.__init__` 이 `PurePath` 인자에서 `_raw_paths` 만 가져와 그 객체의 `read_text` 가 벗겨진다). `str()` 은 정확 타입을 보장하지 않고 오히려 **파일 선택권을 호출자의 `__str__` 에 넘긴다** | 그 줄 제거 변이 **GREEN**(잠긴 test 없음) → 지웠다 · `Path()` 제거는 **Q3 RED** |
| **cr M-2** 입구 전수가 `@classmethod` 를 못 본다 | `inspect.isfunction` → **`inspect.isroutine`**(bound method 를 함께 받는다) | **Q1 RED**(classmethod) · **Q2 RED**(staticmethod, 통제군) |
| **v L-2** 집합 등식 docstring 의 범주 주장 | 측정한 모양으로 좁히고 **반례**(구식 `TypeVar` + `noqa: UP047`)를 적었다 — 그 전수가 지는 것은 **유입 층** | — |
| **cr M-3** §5 가 옛 원천·옛 계수를 들고 rollback.md 와 어긋난다 | §5 를 **포인터 한 줄**로(수를 두 자리에 적지 않는다) | — |
| **v L-3 = cr L-1** `## 0.` 제목 둘 | `## 0a.`·`## 0b.` 로 갈라 **유일 제목**으로 + 인용 셋을 그 번호로 | — |
| cr L-2 | `_run_holdout` docstring 에 **넷째 검사**(재구성 승인, 안전 성질을 지는 자리) | — |
| cr L-3 | 승인 계수에 「역할 기준 — 호출은 `verify_policy_source` 안에서 둘」 한 구절 | `policy_approval(` 호출 넷 ↔ 역할 셋 |
| cr L-4 | 제한 항목을 **번호 순**으로 재배치(본문 무변경) | 3b~3k 순 |
| cr L-5 | 좌표를 점으로 쪼개 해석하는 `_resolve` — 클래스 멤버가 입구·예외에 들어오는 날 `AttributeError` 로 죽지 않게 | 지금은 도달 불가(열하나 전부 모듈 수준) |
| cr L-6 | sweep 의 **대상 어휘**를 적고, 「새로 쓴 단언은 그 sweep 밖이라 리뷰가 자리」를 기록 | 아래 |

### 이 라운드가 배운 것 — sweep 의 설계 결함

cr M-1 은 **거동 결함이 아니라** 라운드 5 가 처방을 설명하며 들여놓은 **새 범주 주장**이었다.
그런데 라운드 5 의 sweep 은 「지운 문장」의 어휘(「어떤 모양이든」 등)를 찾았기 때문에 새 어휘를
못 찾았다. 그 공백을 §4f 에 기록했다 — 다음에는 **「이 라운드가 새로 쓴 단언 목록」**을 대상으로
돌린다.

### acceptance · 변이

- **CI `ml-engine` job 열한 단계** 전부 **exit 0** @`9631517e`(main `56bd33ea` 병합 커밋) ·
  S-5 **1750 passed**(앞 라운드와 같다 — 이 라운드는 문면과 두 줄이고 test 수가 그대로다).
- **변이 4 전부 RED + 통제군 둘 GREEN**: Q1(classmethod 입구) · Q2(staticmethod 입구, 통제군) ·
  Q3(`Path()` 정규화 제거) · Q4(재구성 승인 질문 제거).
- **`str()` 제거 변이는 GREEN 이었다** — 그것이 그 줄을 **남길 이유가 없다는 근거**다(잠긴
  test 가 없고, 주장은 거짓이고, 거동이 갈리는 모양에서는 호출자에게 파일 선택권을 준다).

### main 병합 (원천 재교체) — 겹침 하나를 계수로 검증

`56bd33ea`(PR #137) 병합. `97ed49c4..56bd33ea` 가 만진 **54 파일** 중 `ml-engine/**` **0**,
겹치는 in_scope 는 `docs/discovery/capability-map.md` **하나**다. 그 하나를 **합집합 계수**로
검증했다(이 저장소가 겪은 「충돌 0 자동 병합이 같은 본문을 접는다」 함정 때문):

| 확인 | 결과 |
| --- | --- |
| 양쪽이 더한 줄(lane **51** · main **5**)이 병합본에 전부 있나 | **유실 0** |
| 양쪽이 지운 줄(lane 0 · main **4**)이 병합본에서 전부 빠졌나 | **남음 0** |
| 비빈줄 등식 | 3492 + 51 − 0 + 5 − 4 = **3544** == 실측 **3544** |
| 중복 비빈줄 **종류** | base 24 == 병합본 **24**(병합이 만든 중복 0) |
| 내 축 식별자 언급(`ML-05/07/08`·`SET-06`) | lane 31 == 병합본 **31** |
| 충돌 표지 | **0** |

줄 수 단순 합(4027)과 실측(4023)이 **달랐고**, 그 차이가 **main 이 고친 4 줄**임을 위 등식으로
갈랐다 — 접힘이 아니다. rollback 원천을 그 SHA 로 옮겼다.

## §4h effective date 사용자 승인 (2026-11-06, 문면만)

v M-2 가 올린 **사용자 확인 항목**이 종결됐다 — 사용자가 `evaluation-v1` 의
`effective_date: "2026-09-16"` 을 **승인**했다(팀장 전달).

**값도 파일 지문도 바뀌지 않는다.** 그래서 이 변경은 장부 문면뿐이고, 승인 등록부의 지문
(`fba628ef…`)·정책 checksum·test 의 하드코딩 값 전부 **무편집**이다. 그 사실을 계수로 확인했다
(아래 acceptance 는 이 커밋 뒤 HEAD 에서 돌린 것이다).

| 자리 | 바뀐 것 |
| --- | --- |
| `reports/evidence/m5/5c2/policy-values.md` §1 `effective_date` 행 | 지위 한 구절 **덧붙임**(앞 문면 무편집 — 어떻게 정해졌는지의 기록을 덮지 않는다) |
| 같은 문서 change_history | **2026-11-06 승인** 행 추가(값·지문 무변경 명시 · 확인된 거동 명시) |
| checklist 알려진 제한 7 | 「사용자 확인 항목」 → **「승인됨」**(v M-2 종결) |
| commands 라운드 표 | 그 행의 결과 칸을 종결로 |

`reports/evidence/m5/5d/policy-values.md` 는 **손대지 않았다** — 그 문서는 추론 축 값 장부이고
effective date 를 담지 않는다(`command grep "effective"` **0건**). 팀장 지시가 두 장부를 함께
가리켰지만, 없는 문면을 만들지 않고 이 사실을 보고했다.

## §4i CI 전용 실패 — 해석 실패 등식의 **파이썬 패치 의존** (PR #139)

승인 전 일괄 뒤 PR 을 열었더니 CI `ml-engine`(과 `check` 의 S-20 pytest)이 **test 하나**로
붉었다 — `test_annotation_resolution_failures_are_exactly_the_declared_ones`
(1 failed, 1749 passed). **로컬은 초록이었다.**

### 뿌리 — 「환경이 다르다」가 아니라 **게이트가 환경에 의존했다**

선언 목록의 둘이 CI 에서는 **해석돼** 실제 집합에서 빠졌다. 그 둘은 **PEP 695 제네릭 함수**다
(`features.schema.require_declared[T]` · `features.encoding._accumulate[KeyT]`). 이 저장소는
전 모듈이 `from __future__ import annotations` 라 주석이 문자열이고, `get_type_hints` 는 그것을
모듈 전역에서 찾는데 `T` 는 **함수의 타입 파라미터 스코프**에 있다.

| 인터프리터 | `get_type_hints(f)` | `localns=__type_params__` 로 넘기면 |
| --- | --- | --- |
| **3.12.3**(로컬) | **`NameError: name 'T' is not defined`** | 해석됨 |
| **3.12.13**(CI 의 `setup-python` 이 `3.12` 를 푸는 최신 패치) | **해석됨** | 해석됨 |

CPython 3.12 가 그 스코프를 보는 시점이 **패치 버전마다 다르다**(실측). `.python-version` 이
`3.12` 라 CI 는 최신 패치를 쓰고, 그 차이가 그대로 등식에 들어왔다.

### 처방 — 환경 분기를 넣지 않고 **의존 자체를 없앤다**

`_resolved_hints` 가 `func.__type_params__` 를 `localns` 로 넘긴다. 그러면 **어느 파이썬에서도**
해석되고(두 인터프리터에서 같은 결과 실측), 선언 목록은 **환경과 무관한 넷**만 남는다 — 전부
`AttributeError` 이고, 그것은 파이썬이 아니라 **생성 stub 의 모양**이 정한다(주석이 생성 stub 의
없는 모듈 속성을 이름으로 가리킨다).

**그 「같은 사유」를 test 로 잠갔다**(새 test 하나): 선언된 넷의 실패가 전부 `AttributeError`
여야 한다. 다른 종류(예: PEP 695 `NameError`)가 섞이면 그 순간 붉다 — **등식만 있으면 그런
실패가 「선언했으니 괜찮다」로 조용히 묻힌다**. 이번에 묻혔던 모양이 정확히 그것이다.

### CI 와 같은 환경에서 수정 전/후를 쟀다

버릴 clone + `uv sync --frozen --all-extras --python 3.12.13`(CI 의 lockfile 설치와 같은 명령,
같은 패치 버전) + 생성 stub 복사.

| 측정 | 결과 |
| --- | --- |
| **수정 전** @`1b89cba3` · 3.12.13 | **1 failed** — CI 실패를 그대로 재현(빠진 둘도 같다) |
| **수정 후** · 3.12.13 | 표적 **12 passed** · 전건 **1751 passed** |
| **수정 후** · 로컬 3.12.3 | 전건 **1751 passed** — 양방향으로 환경 무관 |

(1750 → 1751 은 위의 **새 test 하나**다.)

**건드린 것은 그 test 파일 하나뿐**이다 — src·정책·장부 무편집(`git diff` 계수로 확인).

## §4j 병합 전 일괄 — v r7 M-1 (제네릭 반환 입구) · main `bd2f450e`

재검증이 **ready-for-review @`fe46bd2f`** 로 통과했고(두 인터프리터에서 집합·hints 동일),
비차단 M-1 하나를 병합 전에 닫았다.

### M-1 — **앞 커밋이 우연한 방어 하나를 없앴다**

`localns` 처방 뒤 `get_type_hints` 는 `-> P` 를 **`TypeVar`** 로 해석한다. 그런데
`_flatten_with_guard` 는 `TypeVar` 를 잎으로 두고 `__bound__` 를 펴지 않아,
`def f[P: EvaluationPolicy](path, kind: type[P]) -> P` 같은 입구가 `_is_policy_type` 에서
거짓이 되어 **모집단에서 조용히 빠졌다**.

앞 판에서는 이 모양이 3.12.3 의 `NameError` 로 **해석 실패 등식**에 걸려 RED 였다 — r6 가
「PEP 695 입구는 RED」로 잰 근거가 그 **우연**이었다. CI(3.12.13) 기준으로는 **원래 열려 있던
구멍**이고, 처방이 그 우연을 걷어내며 로컬에서도 드러났다.

처방: `_flatten_with_guard` 에 `TypeVar` 가지(`__bound__`·`__constraints__` 를 편다). 같은
가지로 **구식 `TypeVar("_P", bound=…)` + `# noqa: UP047`**(r6 L-2 가 반례로 등재했던 모양)도
함께 닫힌다.

| 변이 | 수정 전 | 수정 후 |
| --- | --- | --- |
| R1 PEP 695 제네릭 반환 입구 | 3.12.3 **GREEN** · 3.12.13 **GREEN** | 둘 다 **RED** |
| R2 구식 `TypeVar(bound=…)` + noqa | 3.12.3 **GREEN** · 3.12.13 **GREEN** | 둘 다 **RED** |
| C 통제군(주석 한 줄) | — | 둘 다 **GREEN** |

집합도 두 버전에서 같다: 해석 실패 **4** · 정책 입구 전수 **12**(양쪽).

### 변이 하네스가 거짓말을 했고 그것을 잡았다

처음 측정은 `uv run --python X --no-sync` 로 두 버전을 돈다고 **적었지만** 그렇지 않았다 —
`--no-sync` 가 `--python` 을 무시하고 기존 `.venv` 를 쓴다(실측: 3.12.3 을 달라고 해도
**3.12.13** 이 나왔다). 「두 버전 GREEN」이 사실은 한 버전이었다.
`UV_PROJECT_ENVIRONMENT` 로 venv 를 갈라 두고 **매 실행 전에 실제 버전을 단언**하게 고쳤다.
이 저장소의 「초록인데 조용하다」 계열이고, **하네스 자신도 측정 대상**이라는 자리다.

### v r7 참고(비 finding)도 닫았다

사유를 재는 test 가 `get_type_hints` 를 **localns 없이** 불렀다 — 등식이 쓰는
`_resolved_hints` 와 다른 namespace 다. 「PEP 695 제네릭이면서 stub 속성 부재」인 함수가
선언 목록에 들어오는 날 **먼저 나는 예외가 버전마다 달라** 그 test 가 다시 갈린다. 두 자리가
`_resolution_localns` 하나를 공유하게 했다(한 줄).

### main 병합 · acceptance

`bd2f450e`(PR #142 CVE) 병합 — `56bd33ea..bd2f450e` 8 파일에 `ml-engine/**` **0**,
내 in_scope 겹침 **0**(계수 확인). 병합이 in_scope 에 가져온 변경 **빈 출력**.
rollback 원천을 그 SHA 로 옮기고 다시 쟀다(목록 `M` 37 · `A` 11 불변, 복원 트리 해시
`b520aebc…` 원천과 동일).

**CI `ml-engine` job 열한 단계를 3.12.13 에서** 돌렸다(`uv sync --frozen --all-extras
--python 3.12.13` — CI 의 `setup-python` 이 `3.12` 를 푸는 패치와 같다): **전부 exit 0**,
S-5 **1751 passed**. 실행 뒤 인터프리터를 다시 물어 **3.12.13** 임을 확인했다(위 하네스
거짓말 뒤라 버전은 매번 단언한다).

## §4k 병합 전 일괄 — v r8 M-1' (문자열 경계) · main `3101035f`

재검증이 **ready-for-review @`830e21a6`** 로 통과했고, 비차단 M-1' 하나와 low 둘을 병합 전에
처분했다. 판정 SHA **`c916b51e`**.

### M-1' — **따옴표 두 개가 같은 구멍을 다시 연다**

§4j 가 닫은 것은 경계가 **타입 객체일 때**다. 같은 경계를 문자열로 적으면
(`def f[P: "EvaluationPolicy"](…) -> P` · `TypeVar("_P", bound="EvaluationPolicy")`)
`__bound__` 가 각각 `str`·`ForwardRef` 로 남아 `_is_policy_type` 이 거짓이고, 그 입구가
**모집단에서 조용히 빠진다** — 두 인터프리터 모두 GREEN 으로 재현했다.

처방은 열거 추가가 아니라 **fail-closed 가지**다: 경계·제약 **자체**가 `str`/`ForwardRef` 면
`get_type_hints` 가 쓰는 것과 같은 namespace(함수 `__globals__` + PEP 695 타입 파라미터)로
풀고 — 풀리면 정책 타입이 잎으로 나와 **모집단 등식**이 받는다 — 풀리지 않으면 표지를 내서
`_resolved_hints` 가 **해석 실패**를 선언한다(**해석 실패 등식**이 붉다). 선언 목록에 적어
피하는 길도 막혀 있다: 그런 함수는 `get_type_hints` 자체가 성공하므로 **사유를 재는 test** 가
「이제 해석된다 — 목록에서 빼라」로 붉다.

| 변이(src 변이 파일 전부 ruff·`ruff format`·`mypy --strict` 통과 — B17 의 test 편집도 ruff 통과) | 수정 전 | 수정 후 | 붉어지는 자리 |
| --- | --- | --- | --- |
| B3c PEP 695 **문자열** 경계 | 3.12.3 **GREEN** · 3.12.13 **GREEN** | 둘 다 **RED** | 모집단 등식 |
| B4c 구식 `TypeVar(bound="…")` + noqa | 3.12.3 **GREEN** · 3.12.13 **GREEN** | 둘 다 **RED** | 모집단 등식 |
| B16c 문자열 경계 + `TYPE_CHECKING` 전용 import | 둘 다 **GREEN** | 둘 다 **RED** | 해석 실패 등식 |
| B17 그 함수를 **선언 목록에 등재**해 피하기 | — | 둘 다 **RED** | 사유 test |
| M1 r7 변이(회귀) | — | 둘 다 **RED** | 모집단 등식 |
| C0 통제군(`-> int`) · C1 통제군(문자열 경계 `"int"`) | 둘 다 **GREEN** | 둘 다 **GREEN** | — |

C1 이 통제군의 핵심이다: **문자열 경계라고 다 붉어지는 것이 아니라**, 풀어 본 결과가 정책
타입일 때만 붉다. 집합도 불변이다 — 해석 실패 **4** · 정책 입구 전수 **12**, 부모(`830e21a6`)·
수정(`c0ceb7e9`)·head(`c916b51e`) × 3.12.3·3.12.13 **여섯 칸의 정렬 목록 sha256 이 같다**
(`c72cc096…`).

### 잔여 — 닫지 않았다(등재)

`@classmethod … -> Self` · `NewType("Pol", EvaluationPolicy)` 반환 · **무경계** 제네릭
`def f[P](…) -> P` 는 셋 다 린트·타입을 통과하는 모양으로 **두 버전 GREEN**(이번 라운드 실측).
무경계 제네릭은 반환 주석에 정책 타입이 한 번도 나오지 않아 **반환 타입 술어로는 원리상** 못
닫는다. 이 전수는 **유입 층**이고 방어선이 아니다(방어선은 `policy_source` 재구성 승인 축).
**문자열이 한 겹 더 깊은 모양은 이 커밋이 닫지 못했다** — §4l 이 그것을 닫는다(v r9 M-1''·
cr r9 M-2).

### low 둘

- 등식 docstring 과 checklist 3a2 의 「받는다/닫았다」를 **측정한 범위**로 좁히고, r6 L-2 의
  **잔여 반례 문단을 다시 등재**했다(위 셋).
- `rollback.md` 의 「`fe46bd2f..HEAD` 로 확인해도 빈 출력」은 거짓이었다 — 유효성 술어는
  **실측 HEAD 기준**이라는 문장으로 고쳤다(그 문서 「유효성 확인」 절).

### main 병합 · acceptance

`3101035f`(#127 Telegram · #136 pitest) 병합 — `bd2f450e..3101035f` **76** 파일에
`ml-engine/**` **0**, in_scope 겹침은 `capability-map.md` **하나**. 합집합 보존을 계수로
확인했다(lane 51 · main 12 전부 있고, main 이 지운 4 전부 없고, 중복 비빈줄 종류 24 로 base 와
같고, 충돌 표지 0, 비빈줄 등식 3552). **손 해소 줄 0** — 겹침 파일을 뺀 양쪽 증분 diff 가
병합 전후로 바이트 동일이다. rollback 은 원천을 `3101035f` 로 옮겨 **다시 쟀다**(rollback.md).

**CI `ml-engine` job 열한 단계를 3.12.13 에서** 돌렸다(`uv sync --frozen --all-extras
--python 3.12.13`): **전부 exit 0**, S-5 **1751 passed**. 시작·끝에 인터프리터를 물어 둘 다
**3.12.13** 임을 확인했다.

## §4l 마지막 수정 라운드 — cr r9 request_changes (v r9 **ready-for-review** @`c916b51e`)

사용자가 상한 너머 **한 라운드**를 승인했다(2026-11-08). 판정 SHA **`5a4c3098`**.

### M-2·M-4 — 푸는 자리를 **경계에서 잎으로** 옮겼다

§4k 는 경계·제약 **자체**가 문자열일 때만 풀었다. 한 겹만 깊으면 그 문자열은 그대로 잎이 되어
`_is_policy_type` 이 거짓이다 — 다섯 모양이 두 인터프리터 모두 GREEN 이었다(아래 표). 처방은
열거가 아니라 **자리 이동**이다: 펼치기 머리에서 `str`/`ForwardRef` 를 만나면 **어느 깊이에서든**
같은 namespace 로 풀고, 못 풀면 해석 실패 표지를 낸다. `TypeVar` 가지의 해석 호출 둘은 그만큼
사라졌다(순 단순화). `Literal` 가드가 함께 필요하다 — 그 인자는 타입이 아니라 **값**이다.

지연 평가 읽기는 **크래시가 아니라 해석 실패**로 보낸다(M-4): `TYPE_CHECKING` 전용 이름을 가리키는
PEP 695 경계는 `__bound__` 를 읽는 순간 `NameError` 가 포획 없이 전파돼 test 둘이 **죽었다**.
별칭의 `__value__` 도 같은 지연 평가다 — 같은 기제를 실측하고 함께 감쌌다.

| 변이(ruff·`ruff format`·`mypy --strict` 통과 — 예외는 표 아래) | 수정 전 `c916b51e` | 수정 후 `5a4c3098` | 붉어지는 자리 |
| --- | --- | --- | --- |
| B19 `[P: list["EvaluationPolicy"]]` | 둘 다 **GREEN** | 둘 다 **RED** | 모집단 등식 |
| B24 `[P: tuple["EvaluationPolicy", int]]` | 둘 다 **GREEN** | 둘 다 **RED** | 모집단 등식 |
| B18 구식 `TypeVar(bound=Optional["…"])` | 둘 다 **GREEN** | 둘 다 **RED** | 모집단 등식 |
| B20 별칭 `type Pol = "EvaluationPolicy"` | 둘 다 **GREEN** | 둘 다 **RED** | 모집단 등식 |
| B23 `[P: Annotated["EvaluationPolicy", "x"]]` | 둘 다 **GREEN** | 둘 다 **RED** | 해석 실패 등식 — **사유는 메타데이터**(표 주) |
| B28 `TYPE_CHECKING` 전용 경계(따옴표 **없음**) | 둘 다 **크래시**(`NameError`, test 둘) | 둘 다 **RED** | 해석 실패 등식 |
| B29 `TYPE_CHECKING` 전용 **별칭**(`__value__`) | 둘 다 **크래시**(위와 같음) | 둘 다 **RED** | 해석 실패 등식 |
| B30 B28 을 **선언 목록에 등재**해 피하기 | — | 둘 다 **RED** | 사유 test |
| B3c·B4c·B16c·B17·M1 (앞 라운드가 닫은 것) | 둘 다 **RED**(v r9 재현) | 둘 다 **RED 유지** | 모집단·해석 실패·사유 |
| 통제군 C0 `-> int` · C1 `[P: "int"]` · C2 자기참조 문자열 경계 · C5 `-> Literal["approved"]` · C6 `-> None` · C7 자기참조 별칭 | 둘 다 **GREEN** | 둘 다 **GREEN** | — |

**음성 대조** — `Literal` 가드를 걷어내면 C5 가 **해석 실패 등식으로 붉다**(두 버전). 같은 트리에서
변이가 없으면 조용하다(src 에 `Literal` **0**) — 이 가드의 거동은 변이로만 보인다.
**L-1 실측** — `_resolved_bound("[][1]")` 는 수정 전 `IndexError` **크래시**, 수정 후 해석 실패
표지다(두 버전). 좁은 열거가 받지 못하는 종류가 실제로 있었다.
표 주: C2 만 `mypy --strict` 가 거부한다(unbound TypeVar) — 재귀 가드 회귀를 보는 runtime
통제군이라 그대로 둔다. C7 은 src 에 실재하는 모양(`str | int | list["Rec"]`)으로 적었다.
**B23 이 「해석 실패 등식」으로 붉은 사유를 정정한다**(v r10, §4m M-A): 정책 문자열
`"EvaluationPolicy"` 가 안 풀려서가 아니라 **메타데이터 `"x"`** 가 잎 자리로 가서 안 풀리기
때문이다 — `get_args(Annotated[X, "x"])` 가 `(X, "x")` 를 내므로 메타데이터도 잎이 된다. 즉
B23 은 「문자열 경계를 닫았다」의 증거가 아니라 **메타데이터 경로의 증거**이고, 같은 기제가
비정책 코드를 붉히는 쪽이 §4m 의 M-A 다. 문자열 경계 쪽 증거는 B19·B24·B18·B20 넷이다.

**집합 불변** — 해석 실패 **4** · 정책 입구 전수 **12** · 정렬 목록 내용 해시 **`60f92e88f7ee`**,
3.12.3·3.12.13 두 버전 모두 수정 전후 같다(v r9 가 여섯 칸에서 잰 해시와 같은 값).

### M-3 — 문면을 **잰 모양**에만 붙였다

`_UNRESOLVED_BOUND` docstring 의 fail-closed 주장에 **범위**를 달았다: ⑴ 어느 깊이의 문자열 잎
⑵ 경계·제약·`__value__` 의 지연 평가 실패. 잔여는 그대로 등재다 — `-> Self` · `NewType` ·
무경계 `[P]` 는 **풀 문자열이 없어** 이 가지에 오지 않는다. **오탐 쪽 대가**도 등재했다(v r9 C4):
클래스 스코프 이름을 문자열 경계로 쓴 비정책 함수는 통과도 등재도 아닌 **해석 실패**로 붉다
(탈출구는 따옴표 제거이고, src 에 그 모양이 0 이라는 근거가 위 집합 불변이다). §4k 의 기제
문장도 그 커밋의 범위(「경계·제약 자체」)로 좁혔다(v r9 L-1).
**이 절의 「오탐 쪽 대가」는 하나가 아니었다** — §4m M-A 가 셋으로 정정한다(그 하나만 적은
문면이 이 라운드의 커밋에서 잰 범위를 넘었다).

### low 둘

- L-2 `TypeVar` 가지 주석에서 라운드 이력을 걷어내고 **불변 둘**(①②) + 라운드 꼬리표로 줄였다.
- L-3 `_annotation_namespace` 를 함수당 두 번 만들던 자리를 호출부 한 번으로 모았다.

### acceptance · rollback

**CI `ml-engine` job 열한 단계를 3.12.13 에서** 그대로 돌렸다(`uv sync --frozen --all-extras
--python 3.12.13`, HEAD `5a4c3098`): **전부 exit 0**, S-5 **1751 passed**. 시작·끝에 인터프리터를
물어 둘 다 **3.12.13**. 변이·집합은 **커밋 뒤** 버릴 clone 에서 쟀고, venv 를 버전별로 나눠 venv
python 을 직접 불렀으며 매 실행 전 버전과 `ml_engine` import 경로를 단언했다.

rollback 은 복원 원천 `3101035f` **그대로** 다시 쟀다(rollback.md — 실측 HEAD `5a4c3098`,
목록 `M` 37 · `A` 11 불변, 트리 동일성 `c0913842…`). 이 slice 가 만진 파일은 in_scope
(`ml-engine/**` · evidence) 안이고 **새 파일 0** 이다.

## §4m 마지막 병합 전 일괄 — v r10 medium 둘 **등재** · main `70406318`

v r10 은 **ready-for-review @`5a4c3098`** 이고 blocker/high 0 이다. 남은 medium 둘은 사용자 결정에
따라 **고치지 않고 등재만** 한다. 이 라운드는 **문면과 장부만** 바꾼다 — 편집한 test 파일에서
docstring 을 제거한 AST 가 앞 커밋과 **동일**하다(실측). 게이트 술어는 한 글자도 움직이지 않았다.

### M-A — 「오탐 쪽 대가는 하나」가 잰 범위를 넘었다 (이 계열이 만든 회귀, 등재)

`_UNRESOLVED_BOUND` docstring 은 fail-closed 의 대가를 **클래스 스코프 이름 하나**로 적었다.
실측된 대가는 **최소 셋**이다 — 둘은 §4l 커밋이 「문자열을 잎 자리에서 푼다」로 옮기며 생긴
**회귀**이고(앞 커밋 `c916b51e` 에서 GREEN), 비정책 코드를 해석 실패로 붉힌다.

| 모양 | 기제 | `c916b51e` | `5a4c3098` |
| --- | --- | --- | --- |
| `type Meters = Annotated[int, "meters"]` → `-> Meters` | `get_args` 가 메타데이터를 **잎**으로 낸다 | GREEN | **RED** |
| `def f[T: Annotated[int, "positive"]](…) -> T` | 같음(경계 안) | GREEN | **RED** |
| `type Box[T] = list["T"]` → `-> Box` | 푸는 namespace 가 함수 쪽이라 `"T"` 가 없다 | GREEN | **RED** |

터지는 자리는 `get_type_hints` 가 **들어가지 않는 구멍**(별칭 `__value__` · `TypeVar` 경계)뿐이다 —
매개변수·반환에 **직접** 적은 `Annotated[int, "설명"]` 은 최상위가 벗겨져 통과한다(v r10 F1·F2
GREEN). 셋 다 `mypy --strict` 를 통과하는 모양이고, **src 에 0** 이라 오늘 붉지 않는다(집합 불변).
탈출구는 따옴표 제거 · 메타데이터를 비문자열로 · 첨자 적기 셋이다. **후속 처방**(이 slice 밖):
`get_origin` 이 `Annotated` 면 첫 인자만 펴고 `__metadata__` 는 버린다.

**같은 기제가 B23 의 사유다** — §4l 변이표의 표 주에 정정을 달았다. B23 이 붉은 것은 정책 문자열이
아니라 메타데이터 `"x"` 때문이고, 그래서 B23 은 문자열 **경계**를 닫았다는 증거가 아니다
(그 증거는 B19·B24·B18·B20 넷이다).

### M-B — 별칭 주장은 **무첨자** 별칭에만 잰 것이었다 (기존 잔여, 등재)

모집단 등식 docstring 의 「PEP 695 **별칭**으로 가려도」는 첨자 없는 별칭만 쟀다. 두 모양이
**두 버전 GREEN** 이고 `c916b51e` 에서도 GREEN 이라 이 라운드의 회귀가 아니다 — 처음부터 있던
틈이고 잔여 목록(`-> Self`·`NewType`·무경계)에 빠져 있었다.

| 모양 | 기제 |
| --- | --- |
| `type Pol[T] = EvaluationPolicy` → `-> Pol[int]` · `type Pol[T] = T \| EvaluationPolicy` | `get_type_hints` 가 내는 것이 `TypeAliasType` 이 아니라 그것을 origin 으로 한 **첨자 꼴**이라 별칭 가지가 받지 못하고 `get_args` 가 첨자 인자만 낸다 |
| `typing_extensions.TypeAliasType("Pol", EvaluationPolicy)` | 3.12 에서 이 타입은 `typing.TypeAliasType` 이 **아니다** |

둘 다 `paired_t_threshold` 를 받는 정책 반환 함수를 모집단 **밖**에 둔다(v r10 X8·X9·X2 실측).
문면을 두 자리에 고쳤다 — 주장 쪽은 「**무첨자** 별칭」으로 좁히고, 잔여 목록에 두 모양을 더했다.
이 전수가 지는 것은 **유입 층**이고 방어선이 아니다(미승인 값이 판정에 닿는 것은
`evaluation.policy_source` 의 재구성 승인 축이 막는다) — 등급이 medium 인 근거가 그것이다.

### main 병합 (원천 교체) — 겹침 하나를 계수로 검증

`70406318`(PR #128 award-ingest)을 병합했다. `ml-engine/**` 겹침 **0** 이고, main 측 27 파일 중
in_scope 와 겹치는 것은 `docs/discovery/capability-map.md` **하나**다.

| 확인 | 결과 |
| --- | --- |
| 충돌 표지(추적 파일 전체) | **0** |
| lane 추가 51 줄 중 병합본에 없는 것 | **0** |
| main 추가 3 줄 중 없는 것 · main 삭제 2 줄 중 남은 것 | **0** · **0** |
| 비빈줄 등식 | 3501 + 51 + 3 − 2 = **3553** == 실측 |
| 중복 비빈줄 **종류** (병합이 만든 중복) | **24** — 원천 base 와 같다(증가 0) |
| **손 해소 줄** — 양쪽 증분 diff 본문(겹침 파일 제외)이 병합 전후 바이트 동일 | **0** (네 방향: lane 7875 줄 · main 3832 줄, 양쪽 `cmp` 동일) |

### acceptance

**CI `ml-engine` job 열한 단계를 3.12.13 에서** 판정 SHA `b776ccee`(병합 커밋)에 대해 그대로 돌렸다 —
**전부 exit 0**. 시작·끝에 인터프리터를 물어 둘 다 **3.12.13**. 핵심 결과: ruff check 통과 ·
`ruff format --check` **241 파일** 무변경 · `mypy --strict` **103 소스 파일** 이슈 0 · import-linter
**계약 8 kept, 0 broken** · pytest **1751 passed**(앞 판정 SHA 와 같은 수 — 이 라운드는 test 를 더하지도
지우지도 않았다) · 설계 래칫·재활용 두 자리 대조(양성 대조 포함)·버전 두 자리 대조 통과 ·
wheel 빌드 + 설치본 재수출 **1 passed**. 변이는 다시 돌리지 않았다 — 이 라운드의 유일한 코드 트리
편집이 **docstring** 이고, 그것을 제거한 AST 가 앞 커밋과 동일함을 실측했으므로 변이 결과가 바뀔
입력이 없다.

### rollback

복원 **원천을 `70406318` 로 옮기고 다시 쟀다** — 이 라운드가 복원 목록 안 파일 둘을 움직였기
때문이다(등재 커밋이 `tests/evaluation/test_public_signatures.py` 를, 병합이
`docs/discovery/capability-map.md` 를). 수·트리 해시·표지 계수는 `rollback.md` 가 정본이다.

## §5 되돌림

`rollback.md` **가 정본**이다 — 복원 원천·복원 목록·실측 HEAD·트리 동일성·표지 계수가 전부
거기 있다. 이 절은 라운드마다 그 문서를 **가리키기만** 한다: 수를 두 자리에 적지 않으면
어긋날 자리가 없다(cr r6 M-3 — 앞 판은 이 절이 옛 원천 `3ad4f3f6` 과 라운드 2 의 계수를
들고 있어 rollback.md 와 정면으로 어긋났다).

## §6 재활용 조사

- **재활용**: 성숙도 커널(`inference/maturity.py`)은 legacy `app/domain/settlement_maturity.py@ed4b06c`의 이식
  판을 **그대로 이었다** — KST 주 경계·반개구간·`0/0 → NoObservation`·관측 없는 주 제외가 전부 무변경이고,
  이 slice 는 그 위에 provenance 축과 계수를 **더했다**(집계 산식 재작성 0). `Reuse:` 포인터와 S-7 두 자리
  대조는 그대로 통과한다.
- **신규로 쓴 것 둘과 그 사유**: `evaluation/epoch.py`(legacy 에는 effective date 가 없다 — 임계가 코드
  상수였으므로 「언제부터의 규칙인가」를 적을 자리 자체가 없었다) · `inference/time_axis.py`(legacy 의 시간축
  대체는 **조용한 폴백**이고 provenance 를 남기지 않는다 — capability map 이 「이 형태는 채택하지 않는다」고
  이미 판정한 자리라 이식 대상이 아니다).
- **어휘 충돌 하나를 찾았다**: `inference.availability` 가 이미 `UnmeasurableReason` 을 쓴다(예측 응답의 도메인
  결과 축, wire enum 으로 사상). 같은 이름을 성숙도 시간축에 쓰면 패키지 재수출에서 충돌하고, 사상 표에 wire
  값 없는 사유가 생긴다 — `TimeAxisUnmeasurableReason` 으로 따로 뒀다(`checklist.md` D-3).
