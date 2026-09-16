# M5/5E-2 — commands.md

정본 = 위 acceptance_commands 전건(S-0~S-12, `dc90f18` 갱신본 — S-3·S-6·S-7·S-9 는
CI `ml-engine` job 명령 그대로) + S-10(Kotlin `check`, evidence 커밋마다 재실측) +
(2b) 호출자 실측. 명령·exit·핵심 결과 한 줄만 — 출력 전문·라운드 이력 절은 남기지
않는다(evidence-pack 규율).

## S-0 ~ S-12 (fix round 2(verifier r2·code-reviewer r2 닫힘 확인) 종결, HEAD `4a46c85`)

## S-0
- cmd: `(cd ml-engine && uv sync --extra serving --extra dev)`
- exit: 0
- 핵심 결과: Resolved 35 packages / Audited 34 packages(변경 없음)

## S-1c
- cmd: `(cd ml-engine && uv run python -c 'import ml_engine.serving.prediction, ml_engine.serving.wire, ml_engine.serving.runtime')`
- exit: 0

## S-2
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 177 files already formatted

## S-3(CI 명령)
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
- 핵심 결과: 938 passed(fix round 1 종결 930 → fix round 2 종결 938(+8) — N-1
  wire 2·prediction 1 · N-2 app 4 case · N-3 parity 1, N-6 은 기존 test 문면
  강화라 순증 0)

## S-6(CI 명령)
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0)

## S-7(CI 명령)
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0

## S-9
- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']")`
- exit: 0

## S-10 (Kotlin `check`, evidence 커밋마다 그 HEAD 에서 재실측 — verifier r2 N-5
가 지목한 최종 HEAD 행 공백을 이 표가 닫는다)
- cmd: `./gradlew --no-daemon check`(HEAD `361c957`, fix round 1 코드 종결)
- exit: 0 — BUILD SUCCESSFUL, 337 task(32 executed, 305 up-to-date)
- cmd: `./gradlew --no-daemon check`(HEAD `a18340e`, fix round 1 evidence 종결)
- exit: 0 — BUILD SUCCESSFUL, 337 task(36 executed, 301 up-to-date)
- cmd: `./gradlew --no-daemon check`(HEAD `4a46c85`, fix round 2 종결 — 정본)
- exit: 0 — BUILD SUCCESSFUL, 337 task(36 executed, 301 up-to-date)

## S-11
- cmd: `(cd ml-engine && uv build --wheel -o <임시 디렉터리> && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: wheel 빌드 성공 + 저장소 밖 임시 venv 설치본에서 재수출 import
  성립(1 passed)

## S-12
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(fake 서버 무편집, 계약 표면 불변)

## (2b) 값 획득 축 — 호출자 실측(fix round 2 뒤 재실측 — 새 public 표면 0)

- cmd: `grep -rn "PredictionRuntime(" ml-engine/src`
- 핵심 결과: `app/server.py:212`(`_prediction_runtime`) 하나
- cmd: `grep -rn "BidPredictionServicer(" ml-engine/src`
- 핵심 결과: 클래스 정의 제외 인스턴스화는 `app/server.py:235`(`_build_servicers`) 하나
- cmd: `grep -rn "serve_bid_rates(" ml-engine/src`
- 핵심 결과: 함수 정의 제외 production 호출자는 `serving/prediction.py:160`
  (`_compute_and_map`) 하나
- **이번 라운드가 새로 만든 public 표면**: 없다. `serving/__init__.py`는
  fix round 2 에서도 무변경이다(diff 확인). fix round 2 가 손댄 이름
  (`_first_non_finite_decimal`·`_first_candidate_rate_out_of_range`의 호출
  순서, `_is_recognized_interval_source`(test 전용 신설), `_prediction_runtime`
  가드 그대로)은 전부 밑줄 접두이거나 test 모듈 내부다.

## 비밀값 스캔 — 참조형(어휘 축어 인용 없음)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m5/5e2/`
- exit: 1(매치 없음 — grep 규약상 무매치는 1)
- 핵심 결과: 매치 0줄

## 실패 이력
없음(fix round 2 커밋도 전부 finding 별 확인 뒤 커밋) — N-1 은 시정 전 코드로
NaN(`decimal.InvalidOperation` 노출)·Infinity(사유 오보고) 둘 다 재현 확인 후
시정. N-2 는 `_prediction_runtime`의 `_preload_outcomes` 가드를 지운 변이로
4 case 중 3 case(`training`·`evaluation`·`serving`)가 실패함을 확인(`inference`
는 별도 `isinstance` 확인이 남아 있어 그 case 만 가드 제거에도 통과 — 별개
방어선이 있다는 뜻이지 test 결함이 아니다).
