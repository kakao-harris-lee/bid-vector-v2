# M5/5E-1 — commands.md

정본 = CI `ml-engine` job 전건(S-1~S-9, S-11) + Kotlin `check`(S-10) + 교차 언어
스모크(S-12) + 실 서버 socket 스모크(S-12b, 로컬 1회). 실패 이력은 결함이 아니라 검증이
실제로 작동했다는 증거다 — 개별 모듈 RED→GREEN 이력은 커밋 로그가 이미 갖는다
(evidence-pack 「라운드 이력 절 금지」).

## S-1 ~ S-12 (fix round 2 종결 — 2026-09-16T00:04Z, 마지막 evidence 커밋 뒤 재실행,
이 결과가 정본이고 그 이전 판(fix round 1 의 803 passed·169 files 등)은 대체됐다)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: Audited 34 packages(grpc-stubs 포함, D-5E-9)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c "import $m" || exit 1; done && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: 5개 DB/HTTP 모듈 전부 ImportError → all-extras 복원

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv sync --frozen --extra serving --no-dev && uv run --no-sync python -c "import ml_engine.serving, ml_engine.serving.grpc" && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: serving extras 만으로 `ml_engine.serving`·`ml_engine.serving.grpc` import 성립(S-1c)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 169 files already formatted

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 70 source files

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: 7 kept, 0 broken(ignored import 2: `serving.grpc -> grpc`, `training.jobs.servicer -> grpc`)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 808 passed(fix round 2 착수 시점 803 → +5 — H-1 잔존 parametrize 4 case ·
  R2-1 재현 test 1건, R2-3·R2-4 는 기존 test 를 재작성했을 뿐 순증 없음)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0(5E-1 은 이식 없음, reuse.md 참고) / 양성 대조(어긋난 evidence) 는 29건 불일치를 내며 의도대로 실패

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']")`
- exit: 0

## 2026-09-16T00:04Z (Kotlin, `./gradlew --no-daemon check --rerun-tasks`)
- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(337 executed — 캐시 없이 전건 강제
  재실행, `leakPatternGate` 포함)

## 2026-09-16T00:04Z
- cmd: `(cd ml-engine && uv build --wheel -o <임시 디렉터리> && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: wheel 빌드 성공 + 저장소 밖 임시 venv 설치본에서 `ml_engine.contracts`·
  `bidvector.ml.v1.prediction_pb2_grpc`·`ml_engine.serving.prediction`·
  `ml_engine.training.jobs.servicer` import 성립(`sys.path` 에 저장소 경로 없음 확인) —
  `OPEN-5A-WHEEL-BUILD-HOOK` 종결(S-11)

## 2026-09-16T00:04Z
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(D-2D-3 (a), fake 서버 — D-5E-10 무편집)

## 2026-09-16T00:04Z (S-12b, 로컬 2회 재실행 — H-1 잔존·R2-1 이 preload·StartTraining
경로를 바꿔 재검증 대상, 커밋 대상 스크립트 아님)
- cmd(케이스 1, 문법 깨진 inference 정책): `python -m ml_engine.app.server`(env 7 —
  `inference-v1.yaml`만 문법 오류로 교체, 나머지 셋은 정상 사본) 로 실 서버 기동 →
  실 gRPC client 로 `StartTraining` 호출
- exit: 0
- 핵심 결과: 서버가 `NOT_READY`(reasons 에 inference 문법 오류 원문 포함)로 정상
  기동(크래시 없음, H-1 잔존 수정 확인) · `StartTraining` → `failure.detail_code ==
  SERVER_NOT_READY`(예외 아니라 정직한 실패 응답)
- cmd(케이스 2, 정상 정책 넷): `python -m ml_engine.app.server` → 실 gRPC client 로
  `StartTraining`(ACCEPTED) → 곧바로 `CancelTrainingJob` → `SIGTERM`
- exit: 0
- 핵심 결과: `StartTraining` → `handle.state == JOB_STATE_ACCEPTED` · `CancelTrainingJob`
  → `job.state == JOB_STATE_CANCELLED`(정상 경로, R2-1 수정이 정상 취소를 깨지 않음
  확인) · `SIGTERM` → 프로세스 정상 종료, M-3 순서(서버 정지 완료→진행 중 job 취소
  요청) 로그 그대로.
- 알려진 제한: 출하 `policy/inference-v1.yaml` 은 `assessment.agency_sample_threshold`
  미공시로 그 자체는 로드 실패가 의도된 상태다(5D-2 운영자 결정) — 이 스모크는 배선
  확인을 위해 그 키만 채운 임시 사본을 썼다(실물 정책값 결정 아님, `OPEN-5D2-POLICY-VALUES`
  유지). 그 사본 없이 실행하면 정상 정책 케이스(2)는 `StartTraining` 이 `MODEL_NOT_READY`
  가 되어 「ACCEPTED」 경로를 이 시점 출하 정책만으로는 보일 수 없다(변화 없음).

## S-13 (verifier r1 M-4 — source 경로 비밀값 스캔, evidence 문서가 아니라 코드 자체)

## 2026-09-16T00:04Z (fix round 2 종결 재실행 — 이 결과가 정본)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 만지거나 새로 낸
  ml-engine/src·ml-engine/tests 파일 목록(`git diff --name-only d78e162..HEAD` 로 산출)>`
- exit: 0(매치 있음 — grep 규약상 매치 존재 시 0, 없으면 1)
- 핵심 결과: 65줄 매치(fix round 1 종결 시점과 동일 — 이번 라운드 편집이 늘리거나
  줄인 매치 없음), 전부 그 취소 토큰 클래스/매개변수 이름 하나의 어휘에서만
  나왔다(참조형 스캔 — 목록의 다른 다섯 어휘 항목은 매치 0줄, 어휘 자체나 매치
  목록은 여기 옮기지 않는다, 2026-09-16 하네스 규율). 실유출 없음.

## evidence 편집 라운드 — leakPatternGate(장부층)

## 2026-09-15T21:44Z
- cmd: `./gradlew --no-daemon check`(evidence 커밋 전 재실측)
- exit: 1(최초) → `leakPatternGate FAILED` — checklist.md 가 그 취소 토큰 클래스의
  **식별자 이름**을 그대로 적어 `leak-patterns.txt` 어휘 하나와 매치(오탐, 실유출
  아님) — 코드에 실제로 있는 이름을 가리키는 것이라 baseline 등재 대상(내용 해시
  키, 게이트가 보고한 값 그대로)
- 조치: `config/quality/leak-pattern-baseline.txt`에 그 키 1건 등재(2026-09-12
  leak-baseline-coord 관례)
- cmd: `./gradlew --no-daemon check`(재실측)
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, `leakPatternGate` 포함 337 task 전부 통과

## 2026-09-15T21:44Z (rollback.md·golden-manifest.json 커밋 전 재실측 — verifier r1 M-7 로
사후 정정)
- cmd: `./gradlew --no-daemon check`
- exit: 1(최초) → `leakPatternGate FAILED` — 이 evidence 문서 **자기 자신의 판독
  서술**이 그 식별자 이름을 다시 축어로 인용해 새 매치 2건을 냈다(코드 자체를
  가리키는 게 아니라 이전 판독을 설명하는 산문이 스캔 어휘를 그대로 옮긴 것 —
  하네스 규율 2026-09-16 「비밀값 스캔 어휘를 evidence 문서에 축어로 적지 않는다」
  위반). baseline 등재 대상이 아니다 — 어휘 인용 자체를 없애야 한다.
- 조치: 두 문단을 간접 표현(「그 취소 토큰 클래스」)으로 다시 쓰고, baseline 에
  잘못 등재했던 두 키를 제거했다(위 한 건만 남는다)
- cmd: `./gradlew --no-daemon check`(재실측)
- exit: 0 — `BUILD SUCCESSFUL`, 337 task

## 2026-09-16T00:30Z (LOW 일괄 편집 뒤 재실측)
- cmd: `./gradlew --no-daemon check`
- exit: 1(최초) → `leakPatternGate FAILED` — 위 한 건이 가리키는 `checklist.md`
  줄의 **내용**을 이번 라운드 편집(M-8·M-5 checklist 매핑 정정)이 바꿔, 같은 자리를
  가리키는 새 내용 해시가 필요해졌다(자기참조 오탐이 아니라 정상적인 내용-해시
  갱신, 2026-09-12 leak-baseline-coord 관례 그대로).
- 조치: baseline 의 그 한 줄을 새 해시로 교체(항목 수 1 유지)
- cmd: `./gradlew --no-daemon check --rerun-tasks`(재실측, 강제 전건)
- exit: 0 — `BUILD SUCCESSFUL`, 337/337 task 실행

## 실패 이력
없음 — mypy strict subclass-of-generated-base 오류(초판, `contracts/__init__.py`와 같은
구조적 사정) 는 `type: ignore[misc]` 로 해소했고, `tests/training/conftest.py`(5C-1,
`sys.modules` 정리 hook) 와의 상호작용으로 `tests/app/test_pipeline.py` 의 지역 import
가 다른 클래스 객체를 낳은 것을 발견해 top-level import 로 고쳤다(구현 결함이 아니라
test 작성 결함, 수정 커밋에 반영).
