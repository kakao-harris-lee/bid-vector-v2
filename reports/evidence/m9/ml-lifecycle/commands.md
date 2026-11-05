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
| S-5 | `pytest tests -q` | 0 | **1679 passed**(착수 1628 → +51) |
| S-6 | `python tools/design_ratchet.py --check` | 0 | allowlist 밖 위반 0 |
| S-7 | 재활용 출처 두 자리 대조 + 양성 대조 | 0 | 어긋난 evidence 가 실제로 실패 |
| S-9 | Python 버전 두 자리 대조 | 0 | — |
| S-11 | wheel 빌드 + 설치본 재수출 | 0 | 1 passed |

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
