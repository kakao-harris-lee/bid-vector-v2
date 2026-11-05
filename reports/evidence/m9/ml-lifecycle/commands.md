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

## §5 되돌림

`rollback.md` — 복원 원천 `df4976cc`(이 브랜치는 main 을 병합한 적이 없다), 목록은 기계 산출
(`M` 29 · `A` 6, evidence 제외), 결과는 **트리 동일성**으로 쟀다(복원 뒤 `write-tree` == base 트리 해시).

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
