# Slice 계약 — M5 / 5E-3 · M5 종결 전 후속 둘 — 정책 로더 fail-closed 를 뿌리에서 · holdout 창 루프 취소 정밀도 — **착수 계약 2026-09-16(사용자 지시 「전체 병합 하고 M5 남은 작업 계속 진행」)**

> **결정이 필요 없는 미결 둘을 닫는다.** (a) `OPEN-5E-YAML-LOADER-INFERENCE` = `OPEN-5C-YAML-ERROR-5D` — 정책 YAML 문법 오류가 예외로 새는 구멍이 **세 번 재발**(5C-1 PR #13, 5C-2, 5E-1)했고 매번 로더 하나에 `except yaml.YAMLError` 를 더하는 사본으로 닫았다. 네 로더(training·evaluation·inference·serving)가 전부 `registry/policy.py::load_policy` 하나를 통해 `yaml.safe_load` 를 부르므로 **그 뿌리에서** `yaml.YAMLError → PolicyError` 로 바꾸면 사본이 더 필요 없다. (b) `OPEN-5E-CANCEL-GRANULARITY` — 5C-2 `run_holdout` 의 창 루프가 취소를 확인하지 않아 `CancelTrainingJob` 뒤에도 남은 창 전부를 학습한다(창 수 × LightGBM 학습). 5E-1 이 「5C-2 파일 편집 필요」로 미뤘고, 5C-2·5E-1·5E-2 가 전부 main 에 있어 지금 닫을 수 있다.
> 레인: worktree `bid-vector-v2-m5e`, 브랜치 `m5-5e3/2026-09-16`, base `8799e05`(PR #20 머지 = origin/main). 병행 레인: M4/4D-3 종결 등 다른 세션 — `ml-engine/**` 무접촉으로 확인(`git log 4b9fa21..origin/main -- ml-engine` 이 5E-2 뿐).

```yaml
milestone: M5
slice: 5e3-policy-loader-root-and-holdout-cancel
base_sha: 8799e0594496593521691a24a29551ee51b6f147
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - ml-engine/src/ml_engine/registry/policy.py               # (a) load_policy — yaml.safe_load 를 감싸 yaml.YAMLError → PolicyError(사유에 path). 반환 타입·시그니처 무변경
  - ml-engine/src/ml_engine/inference/policy.py              # (a) 로더 문면 갱신만(뿌리가 잡으므로 별도 except 불필요 — 5D 파일, 코드 변경은 docstring 한 줄 또는 0)
  - ml-engine/src/ml_engine/app/server.py                    # (a) `_load_inference_policy_safe` 래퍼 제거(호출부 정규화가 뿌리로 이동 — 5E-1 verifier r3 「다른 호출자가 생기면 재발」 조건 소멸). 제거 뒤 5E-1 test 가 여전히 초록이어야 한다
  - ml-engine/src/ml_engine/training/holdout.py              # (b) run_holdout(..., should_stop: Callable[[], bool] = _never) — _evaluate_windows 가 창마다 학습 전에 확인, 멈추면 HoldoutCancelled(결과 타입, 완료 창 수 동반) 반환. 나머지 시그니처·산식 무변경
  - ml-engine/src/ml_engine/training/__init__.py             # HoldoutCancelled 재수출
  - ml-engine/src/ml_engine/app/pipeline.py                  # (b) _run_holdout 이 취소 신호의 확인 함수(`is_cancelled`)를 should_stop 으로 넘기고 HoldoutCancelled → PipelineCancelled. 기존 경계 넷 유지
  - ml-engine/tests/registry/test_policy.py                  # (a) 문법 오류 세 형태 → PolicyError(예외 타입 고정), 정상 경로 무변경
  - ml-engine/tests/gates/test_policy_loaders_fail_closed.py # (a) 신규 게이트 — 네 로더 각각에 문법 깨진 YAML → 결과 타입(PolicyRejected 류)만, 예외 0. 로더가 늘면 이 표에 더한다(열거는 ml_engine.*.policy 의 load_* 를 기계 수집)
  - ml-engine/tests/app/test_server.py                       # (a) 래퍼 제거 뒤 malformed 4 case parametrize 가 그대로 초록(수정 0 목표)
  - ml-engine/tests/training/test_holdout.py                 # (b) should_stop 이 N 창 뒤 참 → HoldoutCancelled, trainer 호출 수 == N(변이: 확인 지점 제거 시 붉어짐) · 기본 인자면 기존 test 무변경
  - ml-engine/tests/app/test_pipeline.py                     # (b) 창 중간 취소 → PipelineCancelled, artifact 파일 0, 기존 경계 test 무변경
  - ml-engine/pyproject.toml                                 # (a) import-linter forbidden 계약 한 블록 — `yaml` 은 `ml_engine.registry.policy` 만 import(ignore_imports 한 줄), 나머지 ml_engine 전부 금지. D-5E3-6, 계약 갱신 (3). 다른 블록·의존성 무편집
  - ml-engine/tests/registry/__init__.py                     # (a) 빈 패키지 마커 — tests/registry/test_policy.py 가 tests/inference/test_policy.py 와 basename 충돌(rootless test 트리, 5C-1 rebase 때와 같은 함정)해 collection 오류. 계약 갱신 (2)
  - milestone-5.md                                           # 5E 절 5E-3 착수 문단
  - reports/evidence/m5/5e3/**
out_of_scope:
  - 정책 값(`OPEN-5D2-POLICY-VALUES`·`OPEN-5E2-CANDIDATE-RATE-UPPER`) · Kotlin(`OPEN-5E2-FEATURE-SCHEMA-PARITY`) · `contracts/**` · serving/evaluation/training 로더의 기존 `except yaml.YAMLError` 절(뿌리가 잡아도 남겨 둔다 — 제거는 값 없음)
  - job 영속·큐 상한·임베딩 실물·GBM 서빙 경로(각 OPEN 유지)
acceptance_commands:
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                                  # S-0
  - "(cd ml-engine && set -euo pipefail && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do if uv run --no-sync python -c \"import $m\" >/dev/null 2>&1; then echo \"금지 패키지 $m 이 serving extras 에 설치됐다\" >&2; exit 1; fi; done && uv sync --frozen --all-extras)"   # S-1b — CI step 「serving extras 분리 확인」 run 블록과 동일(계약 갱신 (3))
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                                           # S-2
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                                             # S-3
  - "(cd ml-engine && uv run lint-imports)"                                                                            # S-4
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                                               # S-5
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                                  # S-6
  - "(cd ml-engine && set -euo pipefail && uv run python tools/reuse_provenance_check.py && if uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md; then echo \"양성 대조(어긋난 evidence)가 실패해야 하는데 통과했다\" >&2; exit 1; fi)"   # S-7 — CI step 「재활용 출처 두 자리 대조」 run 블록과 동일(양성 대조 포함, 계약 갱신 (3))
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python'], (v, p['project']['requires-python'])\")"   # S-9 — CI step 「Python 버전 두 자리 대조」 그대로(계약 갱신 (3))
  - "./gradlew --no-daemon check"                                                                                      # S-10 — evidence 커밋마다 그 HEAD 에서
  - "(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)"   # S-11 승계
  - "./tools/contract-crosslang-smoke.sh"                                                                              # S-12 승계
rollback: |
  **정본 `reports/evidence/m5/5e3/rollback.md`**(구현 레인). 목록은 `git diff --name-status 8799e05..HEAD` 기계 산출 · in_scope 경로 `git restore --source=8799e05 --staged --worktree --`(개별 인자), 신규 test 파일 삭제 · `milestone-5.md` 는 커밋 해시 hunk 격리 · scope.md 자기 이력은 착수 커밋 단일 역적용 + 하네스 절 재등재 · 임시 clone ①~⑥(⑥ Kotlin check). 되돌린 트리 = 5E-2 종결(test 938).
```

작성: 2026-09-16, 세션 모델 단독. 근거: 5E-1 checklist 알려진 제한 4(`OPEN-5E-CANCEL-GRANULARITY`)·verifier r3 「`OPEN-5E-YAML-LOADER-INFERENCE` — 다른 호출자가 생기면 H-1 재발」 · 5C-1 PR #13 HIGH-2·5C-2 동일 결함·5E-1 H-1(세 번 재발 → 뿌리 처방) · `milestone-5.md` 5C 「`OPEN-5C-YAML-ERROR-5D`」 · ADR 0010 D-2(취소 즉시 반영) · 2C ④(CANCELLED 전이) · `v2-지침서.md` §5(중복 최소화·회귀 구조적 방지).

## 계약 고정 결정

| ID | 결정 | 근거 | 지위 |
| --- | --- | --- | --- |
| **D-5E3-1** | YAML 문법 오류의 정규화 지점은 **`registry/policy.py::load_policy` 하나**(`yaml.YAMLError` → `PolicyError`). 네 로더의 기존 `except yaml.YAMLError` 절은 남기되(무해) 새 로더는 절 없이도 닫힌다 — 게이트 test 가 네 로더를 기계 수집해 예외 0 을 고정 | 세 번 재발의 뿌리 = `safe_load` 호출 한 곳 | 계약 고정 |
| **D-5E3-2** | `app/server.py::_load_inference_policy_safe` 래퍼 **제거**(정규화 경로 하나) — 5E-1 test(malformed 4 case)가 수정 없이 초록이어야 한다 | 「호출부 한 곳 정규화」는 임시 방편이었다(5E-1 r3 등재) | 계약 고정 |
| **D-5E3-3** | `run_holdout` 은 `should_stop: Callable[[], bool]` 을 **키워드 기본 인자**로 받는다(기본 항상 거짓). 창마다 학습 **전**에 확인, 참이면 `HoldoutCancelled(completed_windows: int)` 반환(결과 타입 — 예외 아님, 부분 보고서 조립 없음). `training.holdout` 은 `training.jobs` 를 import 하지 않는다(콜러블 주입) | 층 결합 회피 · 5C-2 시그니처 하위 호환(기존 호출자·test 무변경) | 계약 고정 |
| **D-5E3-4** | `app/pipeline.py` 는 `HoldoutCancelled` 를 `PipelineCancelled` 로, 부분 결과·artifact 파일 0. 기존 경계 넷 유지(다섯째가 창 루프 안) — 경계별 변이 test 관례(5E-1 M-2) 그대로 | 5E-1 D-2D-7 | 계약 고정 |
| **D-5E3-5** | 5C-2 파일(`holdout.py`) 편집은 **추가만**(새 인자·새 결과 타입·루프 앞 한 줄) — 산식·창 계획·보고서 조립 무변경, 5C-2 golden·mutation test 전부 무편집 통과 | 5C-2 종결 산출물 보호 | 계약 고정 |
| **D-5E3-6** (계약 갱신 (3), verifier r1 HIGH-1) | 「yaml 을 부르는 곳은 `registry/policy.py` 하나」를 **텍스트 스캔이 아니라 구조로** 닫는다: ① import-linter `forbidden` 계약 — `ml_engine` 전부에 `yaml` 금지, `ignore_imports = ["ml_engine.registry.policy -> yaml"]` 한 줄(S-4 가 CI 에서 강제, 별칭 import·from-import·`full_load`·`safe_load_all` 전부 import 단계에서 잡힘) ② 게이트 test 의 로더 수집은 반환 주석 문자열이 아니라 **모듈 규약**으로 — `ml_engine.*.policy` 서브모듈 전부를 열거하고 각 모듈의 `load_` 접두 public 함수 전부를 대상으로 삼되, **정책 모듈마다 로더 ≥1** 과 **수집 집합 == 열거 집합** 을 단언(하한 `>= 4` 폐지) ③ 기존 텍스트 스캔 test 는 AST 기반(`yaml` 이름을 참조하는 모듈 집합 == {`registry.policy`})으로 바꾸거나 ① 이 대체하면 삭제 — 둘 중 하나, 두 겹 유지는 값 없음 | verifier r1 이 심은 다섯째 로더(타입 별칭 주석 + yaml 별칭 import)가 게이트 넷을 초록인 채 통과해 raw 파서 예외를 누출 — 우회 (1)·(5) 미폐쇄. 문자열에 기대는 술어는 스타일 하나로 열린다 | 계약 고정 — 수정 라운드 1 |

## 위협 모델 — 5E-3 고유 경계
**방어한다**: (a) 어떤 정책 로더도 문법 오류를 예외로 새지 않는다(뿌리 + 게이트) (b) 취소된 job 이 남은 창을 학습하지 않는다(창 단위) (c) 취소가 부분 보고서를 「성공」으로 위장하지 않는다(`HoldoutCancelled` 는 결과 타입, artifact 0).
**방어하지 않는다**: 창 하나 안의 LightGBM 학습 중단(라이브러리 경계 — 창 단위가 최소 단위) · 정책 값 결정 · 로더 밖의 다른 `yaml.safe_load` 호출(grep 으로 0 임을 실측해 등재).
**우회 후보**: (1) 새 로더가 `load_policy` 를 안 쓰고 `yaml.safe_load` 직접 호출 → 게이트 test 의 기계 수집 대상이 아니면 못 잡는다 → 「`ml_engine` 안 `yaml.safe_load` 호출은 `registry.policy` 하나」를 grep 게이트로 고정 (2) `should_stop` 을 안 넘기는 호출자 → 기본 거짓이라 기존 동작(취소 불가) — 조립 근이 넘기는지 test (3) 창 루프 확인을 학습 **뒤**로 옮기는 변이 → trainer 호출 수 test 가 붉어져야 함 (4) `HoldoutCancelled` 를 pipeline 이 `PipelineFailed` 로 매핑 → 상태 FAILED 위장 → test 가 CANCELLED 고정 (5) 게이트 test 의 로더 열거가 손 목록 → 새 로더 누락 → 기계 수집(`pkgutil`·`load_*` 이름 규약) + 수집 수 ≥ 4 단언.

## (2b) 값 획득 축
| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `run_holdout(should_stop=)` 키워드 인자 | 호출자가 중단 시점을 정한다 — 결과를 바꾸지 못하고 멈출 뿐(부분 결과 없음) | 닫는다 |
| `training.HoldoutCancelled` | 읽기 | 닫는다 |
| `registry.load_policy` 예외 타입 변경 | 호출자가 잡을 예외가 하나로 준다 — 권한 축소 | 닫는다 |

## 하네스 레인 변경(리뷰 요청 시점마다 갱신 — `git log --oneline 8799e05..HEAD -- CLAUDE.md .claude/`)
없음(착수 시점 · 리뷰 요청 시점 `80629cd` 재확인 — 출력 없음).

---

## OPEN — 수령·신설
| OPEN | 처리 |
| --- | --- |
| `OPEN-5E-YAML-LOADER-INFERENCE` · `OPEN-5C-YAML-ERROR-5D` | **이 slice 로 종결**(D-5E3-1) |
| `OPEN-5E-CANCEL-GRANULARITY` | **이 slice 로 종결**(창 단위, D-5E3-3·4) — 창 안 중단은 알려진 제한 |
| `OPEN-5D2-POLICY-VALUES` · `OPEN-5E2-CANDIDATE-RATE-UPPER` · `OPEN-5E2-FEATURE-SCHEMA-PARITY` | 운영자 결정 대기(M5 종결 판정 문서에서 처분) |

## 계약 갱신 이력
| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 착수 | 초판 — D-5E3-1~5 | 사용자 「M5 남은 작업 계속 진행」 · 결정 불요 미결 둘 |
| 2026-09-16 리뷰 요청(2) | in_scope 에 `ml-engine/tests/registry/__init__.py` 추가(구현 레인 보고 「새 파일 ↔ in_scope 대조 1건 미등재」) · **D-5E3-5 해석 등재**: 「추가만」은 산식·창 계획·보고서 조립의 불변을 뜻하고, design ratchet(함수 50줄)을 지키기 위한 `_evaluate_windows` → `_record_window_outcome` 순수 이동은 그 안에서 허용한다(5C-2 test 22 case 무편집 통과가 불변의 증거 — verifier 표적) | 팀장 — 구현 완료 보고 `80629cd` 대조. 하네스 레인 절 재확인(없음) |
| 2026-09-16 수정 라운드 1(3) | **D-5E3-6 신설**(게이트 술어를 구조로 — import-linter forbidden `yaml` + 모듈 규약 수집 + 텍스트 스캔 대체) · in_scope 에 `ml-engine/pyproject.toml`(import-linter 블록 한 개) 추가 · acceptance 에 CI `ml-engine` job 의 **S-1b·S-7 양성 대조·S-9** 를 run 블록 그대로 추가(verifier r1 LOW ② — 초판이 CI job 을 통째로 복사하지 않았다, 5E-2 와 같은 팀장 오류) | verifier r1 not-ready(HIGH-1 게이트 술어 우회 — 표적 재검증 대상) |
