# M5/5E-2 — commands.md

정본 = 위 acceptance_commands 전건(S-0~S-12, `dc90f18` 갱신본 — S-3·S-6·S-7·S-9 는
CI `ml-engine` job 명령 그대로) + S-10(Kotlin `check`, evidence 커밋마다 재실측) +
(2b) 호출자 실측. 명령·exit·핵심 결과 한 줄만 — 출력 전문·라운드 이력 절은 남기지
않는다(evidence-pack 규율, verifier r1 M-3 갱신 반영 — 결과만 최신화).

## S-0 ~ S-12 (fix round 1 종결, HEAD `361c957`)

## S-0
- cmd: `(cd ml-engine && uv sync --extra serving --extra dev)`
- exit: 0
- 핵심 결과: Audited 34 packages(변경 없음, 새 의존 없음)

## S-1c
- cmd: `(cd ml-engine && uv run python -c 'import ml_engine.serving.prediction, ml_engine.serving.wire, ml_engine.serving.runtime')`
- exit: 0

## S-2
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 175 files already formatted

## S-3(verifier r1 M-3 — `dc90f18`로 CI 명령과 일치하게 정정)
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 72 source files

## S-4
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: 7 kept, 0 broken(ignored import 2 — `serving.grpc -> grpc`,
  `training.jobs.servicer -> grpc`)

## S-5
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 930 passed(base `4b9fa21` 837 → 구현 종결 890(+53) → fix round 1
  종결 930(+40) — H-1 4·V-M1 1·V-M2 22·V-M4 2·R-M1 5·R-M2 3·LOW 3(에코 test 1·
  CopyFrom 회귀 1·나머지는 기존 test 문면만 강화))

## S-6(verifier r1 M-3 — `dc90f18`로 CI 명령 `--check`와 일치하게 정정)
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0) —
  `prediction.py::CalculateOptimalBid`이 R-M2 로그 추가로 50줄을 넘어 `_compute_
  and_map`으로 분리했다(순수 리팩터)

## S-7(verifier r1 M-3 — `dc90f18`로 CI 명령과 일치하게 정정)
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0(이식 없음, reuse.md 참고)

## S-9(팀장 계약 갱신 (2), `cfbb859` — 초판이 존재하지 않는 `tools/check_python_
version.py`를 가리켰다)
- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']")`
- exit: 0

## S-10 (Kotlin `check`, evidence 커밋마다 그 HEAD 에서 재실측)
- cmd: `./gradlew --no-daemon check`(HEAD `361c957`, fix round 1 evidence 종결)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, `leakPatternGate` 포함 337 task(32 executed, 305
  up-to-date)

## S-11
- cmd: `(cd ml-engine && uv build --wheel -o <임시 디렉터리> && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: wheel 빌드 성공 + 저장소 밖 임시 venv 설치본에서 재수출 import
  성립(1 passed) — fix round 1 재실행 확인(verifier 가 별도 재실행 없이 S-5 에
  포함으로 처리했던 것과 달리 명시 재실행)

## S-12(verifier 가 이번 라운드에 미실행이라고 명시 — 재실행)
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(fake 서버 무편집, 계약 표면 불변)

## (2b) 값 획득 축 — 호출자 실측(fix round 1 뒤 재실측 — 새 public 표면 0)

- cmd: `grep -rn "PredictionRuntime(" ml-engine/src`
- 핵심 결과: `app/server.py:212`(`_prediction_runtime`, R-H1 로 줄 번호만 이동) 하나
- cmd: `grep -rn "BidPredictionServicer(" ml-engine/src`
- 핵심 결과: 클래스 정의(`prediction.py:192`) 제외 인스턴스화는
  `app/server.py:235`(`_build_servicers`) 하나
- cmd: `grep -rn "serve_bid_rates(" ml-engine/src`
- 핵심 결과: 함수 정의(`inference/engine.py:23`) 제외 production 호출자는
  `serving/prediction.py:160`(`_compute_and_map`, R-M2 리팩터로 `CalculateOptimalBid`
  에서 분리됐지만 여전히 유일 호출자) 하나
- **이번 라운드가 새로 만든 public 표면**: 없다. `_validate`(3번째 인자 `gate`
  추가)·`_compute_and_map`(신설)·`_first_candidate_rate_out_of_range`(신설)은
  전부 밑줄 접두 모듈 내부 함수이고 `__all__`·`serving/__init__.py` 재수출 대상이
  아니다(diff 로 확인 — `serving/__init__.py` 는 이번 라운드에 변경 없음).

## 비밀값 스캔 — 참조형(어휘 축어 인용 없음)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m5/5e2/`
- exit: 1(매치 없음 — grep 규약상 무매치는 1)
- 핵심 결과: 매치 0줄

## 실패 이력
없음(fix round 1 커밋은 전부 finding 별로 확인 뒤 커밋) — verifier·code-reviewer
가 재현한 RED 는 각 finding 의 실측으로 기록돼 있다(H-1: verifier 재현 스크립트
exit 1 → 시정 후 test 로 고정. R-H1: 수정 전 코드로 새 test 를 돌려 `AttributeError`
로 계산이 실제 진행됐음을 확인 → 시정 후 통과. V-M2: `derived_release_checksum`
에서 `maturity_window_days` 를 열거에서 뺀 변이로 새 test 가 그 case 만 실패함을
확인).
