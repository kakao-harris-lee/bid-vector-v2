# M5/5E-3 — commands.md

정본 = scope.md acceptance_commands 전건(S-0~S-12) + S-10(Kotlin `check`, evidence
커밋마다 그 HEAD 에서 재실측) + (2b) 호출자 실측. 명령·exit·핵심 결과 한 줄만 —
출력 전문·라운드 이력 절은 남기지 않는다(evidence-pack 규율).

각 명령의 최신 값은 가장 아래(최신) 절을 정본으로 삼는다 — 「구현 종결」→「수정
라운드 1」→「승인 전 일괄」 순으로 갱신됐다.

## 구현 종결 시점(HEAD `b78082d`) 전건 실측 — 2026-09-16T05:06Z

## S-0
- cmd: `(cd ml-engine && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: Audited 34 packages(변경 없음)

## S-2
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 178 files already formatted

## S-3
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 72 source files

## S-4
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과(구현 종결 시점, D-5E3-6 이전): Contracts: 7 kept, 0 broken(ignored
  import 2). **수정 라운드 1 뒤 최종값은 아래 「S-4 재실측」 절 참고**(8 kept,
  ignored import 4)

## S-5
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 951 passed(base 938 → +13 — registry/test_policy.py 4·gates/
  test_policy_loaders_fail_closed.py 4·training/test_holdout.py 3·
  app/test_pipeline.py 2)

## S-6
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0) — `holdout.py`
  에 `should_stop` 확인을 추가하며 `_evaluate_windows`가 50줄을 넘겨(59줄) 한 번
  위반했고, `_record_window_outcome` 헬퍼로 분리해 재통과시켰다. **verifier r1
  LOW-5 정정(수정 라운드 1)** — 이 분리는 래칫 통과에 필수가 아니었다: 헬퍼를
  인라인으로 되돌린 사본을 재실측하면 정확히 50줄이고 판정식이 `> 50`이라 그
  사본도 `design_ratchet.py --check` exit 0(위반 0)이다 — 원래 긴 docstring 을
  짧게 줄인 것만으로 이미 50줄에 닿아 있었다. 분리는 산식·분기·순서·호출 인자가
  원본과 동일한 예방적 순수 이동으로 재분류(checklist.md D-5E3-5)

## S-7
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0(이 slice 는 이식 없음, reuse.md)

## 수정 라운드 1(verifier r1 HIGH-1 + LOW 5, code-reviewer LOW 1) — 최종 HEAD 재실측

verifier r1 LOW-2 — 초판 acceptance_commands 가 CI `ml-engine` job 의 세 step
(S-1b·S-7 양성 대조·S-9)을 빠뜨렸다. 팀장 계약 갱신 (3)이 세 step 을 scope.md 에
추가했다 — 아래는 그 셋을 이 worktree 에서 직접 실행한 결과다.

## S-1b (CI step 「serving extras 분리 확인」)
- cmd: `(cd ml-engine && set -euo pipefail && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do if uv run --no-sync python -c "import $m" >/dev/null 2>&1; then echo "금지 패키지 $m 이 serving extras 에 설치됐다" >&2; exit 1; fi; done && uv sync --frozen --all-extras)`
- exit: 0 — 금지 패키지 5종 미설치. 명령 끝의 `uv sync --frozen --all-extras`가
  venv 를 원상 복구한다(뒤이은 S-5 재실행으로 951 passed 확인, venv 손상 없음)

## S-7 양성 대조(CI step 「재활용 출처 두 자리 대조」 후반)
- cmd: `(cd ml-engine && set -euo pipefail && uv run python tools/reuse_provenance_check.py && if uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md; then echo "양성 대조(어긋난 evidence)가 실패해야 하는데 통과했다" >&2; exit 1; fi)`
- exit: 0 — 어긋난 evidence(`reuse-mismatch.md`)를 두 번째 호출이 정상적으로
  거부해(그 자체는 비0 종료) 바깥 `if` 조건이 거짓이 되고 스크립트 전체는 exit 0
  으로 끝난다(양성 대조가 기대대로 실패함을 확인하는 것이 성공 기준)

## S-9 (CI step 「Python 버전 두 자리 대조」)
- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python'], (v, p['project']['requires-python'])")`
- exit: 0

## S-4 재실측(D-5E3-6 반영 뒤)
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: **8** kept, 0 broken(신설 계약 `yaml 을 직접 import 하는
  곳은 registry.policy 하나` — ignored import 4: `registry.policy`(뿌리) +
  `training`·`evaluation`·`serving`의 `policy.py`(out_of_scope 유지 죽은 except
  절의 무해 참조, checklist.md 알려진 제한 7))

## HIGH-1 수정 검증 — 양성 대조(구조 계약이 실제로 우회를 잡는지)
- 절차: verifier r1 이 심었던 정확한 재현 probe(반환 타입은 `ProbeResult =
  ProbePolicy | PolicyRejected` **타입 별칭**, `import yaml as _y`, `load_probe_
  policy` 함수)를 `ml_engine.probe_pkg.policy`에 임시로 심고 두 방어를 각각 확인
  한 뒤 probe 삭제(작업 트리 클린 확인)
- cmd: `(cd ml-engine && uv run python -m pytest tests/gates/test_policy_loaders_fail_closed.py -q)`(probe 심은 상태)
- exit: 1 — `test_every_collected_loader_rejects_malformed_yaml_without_raising`
  1 failed(`ml_engine.probe_pkg.policy.load_probe_policy 가 예외를 던졌다:
  ParserError(...)`) — **재작성된 게이트가 이제 이 로더를 수집해 누출을 잡는다**
  (수정 전에는 반환 주석이 타입 별칭이라 수집에서 빠져 0 failed 였다, verifier r1
  재현 그대로)
- cmd: `(cd ml-engine && uv run lint-imports)`(probe 심은 상태)
- exit: 1 — `ml_engine is not allowed to import yaml: ml_engine.probe_pkg.policy
  -> yaml (l.7)` — **import-linter 가 별칭 import 를 독립적으로 잡는다**
- probe 삭제 뒤: `git status --short src/ml_engine/`에 probe 잔재 없음(이 라운드의
  다른 pending 편집 — `training/holdout.py` LOW-1 정정 — 만 남음), `uv run python
  -m pytest tests/gates/test_policy_loaders_fail_closed.py -q` exit 0(4 passed),
  `uv run lint-imports` exit 0(8 kept)

## S-10 (Kotlin `check`, evidence 커밋마다 그 HEAD 에서 재실측)

cmd 는 매번 `./gradlew --no-daemon check` — 전부 exit 0(BUILD SUCCESSFUL).

| HEAD | 시점 |
| --- | --- |
| `b78082d` | 구현 종결 |
| `4d2c1d8` | evidence 패키지 커밋 뒤 |
| `f671fe9`·`80629cd` | 첫 리뷰 요청 시점 마지막 재실측 |
| `014752d` | 수정 라운드 1 HIGH-1 코드 커밋 뒤 |
| `fe76d50` | 수정 라운드 1 LOW 일괄 커밋 뒤 |
| `7df311e`·`1c410d0` | 수정 라운드 1 종결 시점 |
| `930891d` | 승인 전 일괄 커밋 뒤 — 이 줄을 적기 직전의 최종 재실측 |

## 승인 전 일괄(verifier r2 MEDIUM-1 + LOW 1·2) — 최종 HEAD 재실측

MEDIUM-1 신설 test `test_exception_modules_reference_only_yaml_yamlerror`
(D-5E3-6 ④) — `pyproject.toml`을 직접 읽어 뽑은 예외 세 모듈을 AST 로 전수 스캔.

- cmd: `(cd ml-engine && uv run python -m pytest tests/gates/test_policy_loaders_fail_closed.py -v)`
- exit: 0 — 5 passed(신설 1 포함)
- 변이 1(`training/policy.py`에 `yaml.safe_load("a: 1")` 호출 추가) — exit 1,
  1 failed(`YAMLError 외 yaml 속성을 참조한다: ['YAMLError', 'safe_load']`). 원복
  뒤 재실행 exit 0(5 passed)
- 변이 2(verifier r2 MEDIUM-1 재현 probe — `training/policy.py`에 `load_` 접두가
  아닌 `read_training_policy_v2`로 `yaml.safe_load` 직접 호출) — exit 1, 1
  failed(같은 assertion, 이름 규약과 무관하게 잡음). `lint-imports`는 이 변이에서
  여전히 exit 0(import 계약은 이름을 보지 않으므로 무관 — 새 test 가 이 자리를
  독립적으로 닫는다는 것이 핵심). 원복 뒤 재실행 exit 0(5 passed), `git diff
  --stat src/ml_engine/training/policy.py` 빈 diff
- S-4(재확인, 이번 라운드는 계약 자체를 안 바꿨다) — exit 0, Contracts: 8 kept,
  0 broken(변동 없음)
- S-5(전체) — exit 0, **952 passed**(951 → +1, 신설 test 하나)

## S-11
- cmd: `(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel-5e3 && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: Successfully built ml_engine-0.1.0-py3-none-any.whl / 1 passed

## S-12
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`

## (2b) 값 획득 축 — 실측

- `run_holdout(..., should_stop=)` 호출 — production 호출자 `app/pipeline.py::
  _run_holdout` 하나(`cmd: grep -rn "run_holdout(" ml-engine/src/ml_engine/`).
- `training.HoldoutCancelled` 읽기 — `app/pipeline.py`(isinstance 분기)·
  `training/__init__.py`(재수출) 둘뿐, 판정 로직을 추가로 소비하는 다른 모듈 없음
  (`cmd: grep -rln "HoldoutCancelled" ml-engine/src/ml_engine/`).
- `registry.load_policy` 예외 타입 변경의 영향 반경 — `PolicyError`를 잡는 소비
  로더 넷(`serving`·`training`·`inference`·`evaluation`의 `policy.py`), 손 목록이
  아니라 `tests/gates/test_policy_loaders_fail_closed.py`가 기계 수집으로 같은
  집합을 매 실행마다 재확인한다.

## RED 확인(구현 전 실측, mutation 대조 포함)

- `tests/registry/test_policy.py` 4 case — root 수정 전 4 failed(malformed YAML
  이 raw `yaml.YAMLError`로 새 나감).
- `tests/gates/test_policy_loaders_fail_closed.py` — 구현 전 코드(`registry/
  policy.py`를 base `HEAD~1`로 되돌린 사본)에 대해 `test_every_collected_loader_
  rejects_malformed_yaml_without_raising` 1 failed(`ml_engine.inference.policy.
  load_inference_policy 가 예외를 던졌다: ParserError(...)`) — 3 passed(나머지
  게이트는 무관).
- `tests/training/test_holdout.py`(should_stop 신설 3 case) — `HoldoutCancelled`
  import 자체가 `ImportError`로 collection 실패(RED).
- mutation 대조 — `_evaluate_windows`의 `should_stop()` 확인을 학습 **뒤**로 옮긴
  사본에서 `test_run_holdout_should_stop_halts_before_training_next_window`·
  `test_run_holdout_should_stop_checked_before_first_window_too` 2 failed
  (`completed_windows` 값이 기대보다 1 많음 — 창 하나가 더 학습됨).
- `tests/app/test_pipeline.py`(pipeline 배선 2 case) — 구현 전 `_run_holdout`이
  `should_stop=`을 넘기지 않아 `test_holdout_cancelled_mid_windows_maps_to_
  pipeline_cancelled_with_no_artifact_files`의 내부 assertion(`"should_stop" in
  kwargs`)에서 2 failed.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 파일들 개별 인자>
  reports/evidence/m5/5e3/`(in_scope 목록은 scope.md 그대로)
- exit: 0(매치 있음) — 매치는 전부 `ml-engine/src/ml_engine/app/pipeline.py`·
  `ml-engine/tests/app/test_pipeline.py` 두 파일에 국한되고, `reports/evidence/
  m5/5e3/`에는 매치가 0 이다(이 명령을 evidence 디렉터리만으로 좁혀 재실행해
  exit 1 확인). 두 파일의 매치는 패턴 파일 넷째 항목(인증·비밀값 통칭어)과
  대소문자 무관 일치하는 **이 slice 이전부터 있던 job 취소 신호 타입**(`training.
  jobs.pipeline` 소유, 5E-1 도입)의 클래스·변수 이름이다 — 육안 확인: 전부 job
  취소 여부를 나르는 용도이고 인증·자격증명·API 키류 값이 아니다(leakPatternGate
  의 scanRoot 는 `reports/evidence` 뿐이라 이 두 소스 파일은 CI 게이트 대상이
  아니다 — S-10 이 이미 그 사실을 실측했다).
