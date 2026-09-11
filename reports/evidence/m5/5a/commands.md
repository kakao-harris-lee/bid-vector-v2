# M5/5A — commands.md

base `d281329bed7f095a3d98eaaa5eecb06afd0d5969` · 계약 커밋 `b00e9299bb9dec48ea9604ac8b53ef1084c1c701`
(scope.md 착수판). 로컬: `uv 0.9.22`(Homebrew), pyenv `3.12.2`(`uv python pin 3.12`가 선택,
`.python-version` 요구는 `3.12` 두 자리 일치면 충분 — S-9). 아래는 2026-09-11 실측 재실행
(모든 명령 `cd ml-engine &&` 생략, 실제로는 그 안에서 실행).

## S-1 ~ S-9 (scope.md acceptance_commands 순서)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | 32개 패키지 설치(재실행 시 `Audited` — 변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + S-1 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 `ModuleNotFoundError`(부재 확인) |
| S-2a | `uv run ruff check .` | 0 | All checks passed! |
| S-2b | `uv run ruff format --check .` | 0 | 15 files already formatted |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | Success: no issues found in 10 source files |
| S-4 | `uv run lint-imports` | 0 | Contracts: 5 kept, 0 broken |
| S-5 | `uv run python -m pytest tests -q` | 0 | 152 passed in 3.00s(2A~2E 125 + 게이트 27, 회귀 0) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음 (대상 0개 파일 중 allowlist 밖 위반 0) |
| S-7 | `reuse_provenance_check.py` (+ `--evidence tests/gates/fixtures/reuse-mismatch.md` 양성 대조) | 0 | 1차: 「재활용 출처 두 자리 일치 — 위반 0」 · 2차(양성 대조): 「불일치 1건」 exit 1 → `!`로 성공 |
| S-8 | `./gradlew --no-build-cache --no-daemon clean check`(저장소 루트) | 0 | BUILD SUCCESSFUL in 46s, 345 actionable tasks — Kotlin 무영향 |
| S-9 | python 버전 두 자리 대조(`.python-version` vs `requires-python`) | 0 | assert 통과(출력 없음) |

## 알려진 함정 실측 — 설계 문면과 어긋난 지점 (구현 중 발견, 코드로 흡수함)

- **grimp 세그폴트**: `ml_engine/contracts/_generated/`가 디스크에 **전혀 없으면**(fresh
  checkout) `uv run lint-imports`가 exit 139(SIGSEGV)로 죽는다. 빈 디렉터리만 있으면
  정상 동작(exit 0/1). `.gitkeep`으로 디렉터리 자체를 항상 존재시켜 해소
  (`.gitignore`·`ml-engine/src/ml_engine/contracts/_generated/.gitkeep`).
- **protoc 절대 import**: D-5A-0(b) 원문의 `from ._generated.bidvector.ml.v1 import …`(상대
  import)는 `ModuleNotFoundError: No module named 'bidvector'`로 깨진다 — protoc 생성
  코드가 형제 proto 를 항상 절대 import(`from bidvector.ml.v1 import …`)로 참조하기
  때문(예: `embedding_pb2.py`가 `common_pb2`를 그렇게 부른다). `contracts/__init__.py`는
  2A~2E 와 같은 관례(생성 디렉터리를 `sys.path`에 얹고 절대 import)로 바꿨다 — 생성
  위치·gitignore 경계·재수출 하나만 허용이라는 결정의 **의도**는 그대로다(파일 상단
  구현 노트에 근거 기록).
- **import-linter 옵션 둘 신규 발견**: `include_external_packages = true`(external
  forbidden module 이 있으면 필수) · `ignore_imports`는 실제 import edge 가 있어야
  한다(`ml_engine.serving.grpc`가 5A 시점엔 없어 넣으면 exit 1 — 5E 가 만들 때 추가하도록
  `pyproject.toml` 주석·`checklist.md` OPEN 에 남김).
- **ruff select 확장의 부작용**: `N`(pep8-naming) 없이는 2A~2E 의 `# noqa: N802`가
  "미사용"(RUF100)이 되어 편집 금지 파일이 위반한다 — `N`을 select 에 추가해 해소.
  반대로 `PLC0415`를 추가하면 2A~2E 의 다른 지연 import 17곳이 새로 위반해 **추가하지
  않았다**(대신 `ruff format`이 그 파일들에 요구하는 폭이 기존 서식과 달라 `tests/*.py`
  직계만 `extend-exclude`로 뺐다 — `ruff check`는 이미 0 위반이었다).
- **mypy 캐시 staleness**: `.mypy_cache`가 남아 있으면 `_generated` 존재 여부가 바뀐
  뒤에도 이전 결과가 재사용돼 혼란스러운 에러가 난다 — evidence 재실행은 항상
  `rm -rf .mypy_cache` 뒤에 했다(`.gitignore`로 이미 제외됨, CI 는 매번 새 러너라 해당 없음).

## rollback 실측

`reports/evidence/m5/5a/rollback.md` 정본.
