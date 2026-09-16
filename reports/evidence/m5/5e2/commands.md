# M5/5E-2 — commands.md

정본 = 위 acceptance_commands 전건(S-0~S-12) + S-10(Kotlin `check`, evidence 커밋마다
재실측) + (2b) 호출자 실측. 명령·exit·핵심 결과 한 줄만 — 출력 전문·라운드 이력 절은
남기지 않는다(evidence-pack 규율).

## S-0 ~ S-12 (구현 종결 시점, 2026-09-16, HEAD `3ddb36f`)

## S-0
- cmd: `(cd ml-engine && uv sync --extra serving --extra dev)`
- exit: 0
- 핵심 결과: Audited 34 packages(변경 없음, 5E-2 는 새 의존을 더하지 않았다)

## S-1c
- cmd: `(cd ml-engine && uv run python -c 'import ml_engine.serving.prediction, ml_engine.serving.wire, ml_engine.serving.runtime')`
- exit: 0

## S-2
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 173 files already formatted

## S-3
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 72 source files(70 → 72, `wire.py`·`runtime.py` 신설)

## S-4
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: 7 kept, 0 broken(ignored import 2 — `serving.grpc -> grpc`,
  `training.jobs.servicer -> grpc`, 5E-2 가 새 예외를 추가하지 않았다)

## S-5
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 890 passed(base `4b9fa21` 837 → +53 — wire 14·runtime 16·
  kotlin_rules_parity 5·server_prediction 2·prediction 순증 16(7→23), test_grpc.py
  는 시그니처 변경(`runtime=None`) 한 줄만 — 순증 0)

## S-6
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0) — `wire.py`의
  `_check_invariants` 후보-수 검사는 `Sequence`로 국소 타입을 넓혀 mypy
  `warn_unreachable`을 우회하지 않고 실행 시점 방어를 유지했다(구현 노트 참고)

## S-7
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0(5E-2 는 이식 없음, reuse.md 참고)

## S-9 — 계약 불일치(구현 판단 아님)
- cmd: `(cd ml-engine && uv run python tools/check_python_version.py)`
- exit: N/A — `tools/check_python_version.py`가 이 저장소에 존재하지 않는다(전건
  `find`·git 이력 확인, 5A~5E-1 어느 slice 도 만들지 않았다). scope.md 의
  acceptance_commands 항목이 존재하지 않는 도구를 가리키는 계약 불일치다 — 계약을
  임의로 고치거나 도구를 새로 짓지 않고(범위 밖 신설 금지) 팀장에게 보고한다.
  Python 버전 자체는 `pyproject.toml`(`requires-python = ">=3.12,<3.13"`)·
  `.python-version`(둘 다 5E-2 무편집)로 이미 고정돼 있다.

## S-10 (Kotlin `check`, evidence 커밋마다 그 HEAD 에서 재실측 — 아래는 최종 evidence
커밋 뒤 재실행 결과)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, `leakPatternGate` 포함 337 task(35 executed, 302
  up-to-date) — 이 slice 는 Kotlin 소스를 만지지 않았다(교차 언어 실서버 통합은
  `OPEN-5E2-CROSSLANG-REAL-SERVER`)

## S-11
- cmd: `(cd ml-engine && uv build --wheel -o <임시 디렉터리> && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: wheel 빌드 성공(`serving/wire.py`·`serving/runtime.py` 포함) + 저장소
  밖 임시 venv 설치본에서 재수출 import 성립(1 passed)

## S-12
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(fake 서버 무편집, 계약 표면
  불변 확인)

## (2b) 값 획득 축 — 호출자 실측(수정 라운드마다 재실측 대상)

- cmd: `grep -rn "PredictionRuntime(" ml-engine/src`
- 핵심 결과: `app/server.py:201`(`_prediction_runtime`) 하나 — 조립 근 밖 생성자 없음
- cmd: `grep -rn "BidPredictionServicer(" ml-engine/src`
- 핵심 결과: 클래스 정의(`prediction.py:143`) 제외 인스턴스화는 `app/server.py:224`
  (`_build_servicers`) 하나
- cmd: `grep -rn "serve_bid_rates(" ml-engine/src`
- 핵심 결과: 함수 정의(`inference/engine.py:23`) 제외 production 호출자는
  `serving/prediction.py:227`(`CalculateOptimalBid`) 하나 — test 는 `monkeypatch`로
  대체해 계수한다(공개 표면 증가 0, 착수 조사 「object 커널」 절)

## 비밀값 스캔 — 참조형(어휘 축어 인용 없음)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m5/5e2/`
- exit: 1(매치 없음 — 이 문서 집합에 어휘 매치가 없다는 뜻, grep 규약상 무매치는 1)
- 핵심 결과: 매치 0줄

## 실패 이력
없음 — wire.py `_check_invariants`의 후보-수 재확인이 초판에서 mypy
`warn_unreachable`에 걸렸고(정적 타입이 이미 길이 3을 보장), `Sequence` 로컬 타입
확장으로 실행 시점 방어를 유지한 채 해소했다(구현 노트에 근거 기록, 삭제하지
않았다 — frozen dataclass 도 `object.__setattr__`로 사후 변조될 수 있어 방어 가치가
있다). 그 밖에는 RED 단계 test 가 처음부터 GREEN 이었던 항목이 대다수다(엔진·계약이
이미 성숙해 매핑·검증 로직이 첫 구현에서 맞았다) — 이는 결함 이력 부재이지 검증
누락이 아니다(각 파일의 test 는 커밋 전 개별 실행으로 실패 가능한 경계를 확인했다:
예 — `test_sample_size_zero_success_is_mapping_rejected`류는 구현 이전 `MappingRejected`
분기가 없었다면 `AttributeError`로 실패했을 것이다).
