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
| effective date 값의 지위 | 알려진 제한 7 — **사용자 확인 항목** | — | 앞 라운드 유지 |

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

## §5 되돌림

`rollback.md` — 복원 원천은 **이 브랜치가 실제로 병합한 main** `3ad4f3f6`(라운드 3 에서도
유지 — 측정 시 `origin/main` == `3ad4f3f6` 확인, 목록은 `M` 37 · `A` 11 로 재산출)(수정 라운드 2 에서 병합 —
착수 base `df4976cc` 는 더 이상 원천이 아니다: 그 사이 main 이 89 커밋 움직여 `capability-map.md` 에
다른 레인 넷이 114 줄을 더했고, 옛 원천에서 복원하면 **남의 줄을 떨어뜨린다**). 목록은 기계 산출
(`M` 36 · `A` 11, 이 slice 의 evidence 제외), 결과는 **트리 동일성**으로 쟀다(복원 뒤 `write-tree`
`810cacbd…` == 원천 트리 해시). 표지 계수는 양성 대조와 함께 쟀고, 그 과정에서 **표지로 쓸 수 없는
상류 어휘 둘**(`sample_scope`·`effective_date` — 6G 백테스트가 이미 쓴다)을 걸러냈다.

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
