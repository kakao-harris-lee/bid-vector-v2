# M5/5E-3 — commands.md

정본 = scope.md acceptance_commands 전건(S-0~S-12) + S-10(Kotlin `check`, evidence
커밋마다 그 HEAD 에서 재실측) + (2b) 호출자 실측. 명령·exit·핵심 결과 한 줄만 —
출력 전문·라운드 이력 절은 남기지 않는다(evidence-pack 규율).

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
- 핵심 결과: Contracts: 7 kept, 0 broken(ignored import 2)

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
  위반했고, `_record_window_outcome` 헬퍼로 분리해 재통과시켰다(창 처리 결과를
  results/excluded 로 나누는 기존 로직을 그대로 옮긴 것 — 5C-2 golden·mutation
  test 무편집 통과가 동작 불변의 증거)

## S-7
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0(이 slice 는 이식 없음, reuse.md)

## S-10 (Kotlin `check`, evidence 커밋마다 그 HEAD 에서 재실측)
- cmd: `./gradlew --no-daemon check`(HEAD `b78082d`, 구현 종결)
- exit: 0 — BUILD SUCCESSFUL, 337 task(35 executed, 302 up-to-date)

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
