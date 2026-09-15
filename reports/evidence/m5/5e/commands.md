# M5/5E-1 — commands.md

정본 = CI `ml-engine` job 전건(S-1~S-9, S-11) + Kotlin `check`(S-10) + 교차 언어
스모크(S-12) + 실 서버 socket 스모크(S-12b, 로컬 1회). 실패 이력은 결함이 아니라 검증이
실제로 작동했다는 증거다 — 개별 모듈 RED→GREEN 이력은 커밋 로그가 이미 갖는다
(evidence-pack 「라운드 이력 절 금지」).

## S-1 ~ S-12 (2026-09-15T21:44Z 재실행 확인)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: Audited 34 packages(grpc-stubs 포함, D-5E-9)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c "import $m" || exit 1; done && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: 5개 DB/HTTP 모듈 전부 ImportError → all-extras 복원

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv sync --frozen --extra serving --no-dev && uv run --no-sync python -c "import ml_engine.serving, ml_engine.serving.grpc" && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: serving extras 만으로 `ml_engine.serving`·`ml_engine.serving.grpc` import 성립(S-1c)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 166 files already formatted

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 70 source files

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: 7 kept, 0 broken(ignored import 2: `serving.grpc -> grpc`, `training.jobs.servicer -> grpc`)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 782 passed(base `d78e162` 임시 worktree 직접 계수 658 → +124, evidence-pack 규율)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0(5E-1 은 이식 없음, reuse.md 참고) / 양성 대조(어긋난 evidence) 는 29건 불일치를 내며 의도대로 실패

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']")`
- exit: 0

## 2026-09-15T21:44Z (Kotlin, `./gradlew --no-daemon check`)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 36s, 346 actionable tasks(211 executed, 135 from cache)

## 2026-09-15T21:44Z
- cmd: `(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)`
- exit: 0
- 핵심 결과: wheel 빌드 성공 + 저장소 밖 임시 venv 설치본에서 `ml_engine.contracts`·
  `bidvector.ml.v1.prediction_pb2_grpc`·`ml_engine.serving.prediction`·
  `ml_engine.training.jobs.servicer` import 성립(`sys.path` 에 저장소 경로 없음 확인) —
  `OPEN-5A-WHEEL-BUILD-HOOK` 종결(S-11)

## 2026-09-15T21:44Z
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(D-2D-3 (a), fake 서버 — D-5E-10 무편집)

## 2026-09-15T21:44Z (S-12b, 로컬 1회 — 커밋 대상 스크립트 아님)
- cmd: `python -m ml_engine.app.server`(env 6 — `serving-v1.yaml`·`training-v1.yaml`·
  `evaluation-v1.yaml`·`inference-v1.yaml`+`assessment.agency_sample_threshold` placeholder
  사본, 별도 프로세스) 로 실 서버 기동 → 실 gRPC client 로 `GetModelMetadata`·
  `GetEmbeddingMetadata`·`StartTraining` 호출
- exit: 0
- 핵심 결과: 세 서비스 등록 확인(소켓 응답) · `GetModelMetadata.readiness == READINESS_NOT_READY`(D-5E-3) ·
  `GetEmbeddingMetadata.readiness == READINESS_NOT_READY`(D-5E-2) ·
  `StartTraining` → `handle.state == JOB_STATE_ACCEPTED`(정책 넷 READY 일 때) ·
  `SIGTERM` → graceful shutdown 로그 확인(readiness NOT_READY 선행 순서)
- 알려진 제한: 출하 `policy/inference-v1.yaml` 은 `assessment.agency_sample_threshold`
  미공시로 그 자체는 로드 실패가 의도된 상태다(5D-2 운영자 결정) — 이 스모크는 배선
  확인을 위해 그 키만 채운 임시 사본을 썼다(실물 정책값 결정 아님, `OPEN-5D2-POLICY-VALUES`
  유지). 그 사본 없이 실행하면 `GetModelMetadata`/`GetEmbeddingMetadata` 는 이미 항상
  `NOT_READY`(D-5E-2·D-5E-3, 변화 없음)이지만 `StartTraining` 은 `MODEL_NOT_READY`(gate 가
  `NOT_READY`)가 되어 「ACCEPTED」 경로를 이 시점 출하 정책만으로는 보일 수 없다.

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

## 실패 이력
없음 — mypy strict subclass-of-generated-base 오류(초판, `contracts/__init__.py`와 같은
구조적 사정) 는 `type: ignore[misc]` 로 해소했고, `tests/training/conftest.py`(5C-1,
`sys.modules` 정리 hook) 와의 상호작용으로 `tests/app/test_pipeline.py` 의 지역 import
가 다른 클래스 객체를 낳은 것을 발견해 top-level import 로 고쳤다(구현 결함이 아니라
test 작성 결함, 수정 커밋에 반영).
