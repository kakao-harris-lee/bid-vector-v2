# M5/5A — commands.md

base `d281329bed7f095a3d98eaaa5eecb06afd0d5969` · 계약(착수+r1 뒤 갱신) 최신 `18ddc1d`
· 수정 라운드 1 최종 코드 커밋 `fdb1d52e4b6b510ee34e75f4e5e95c59abbb3814`. 로컬:
`uv 0.9.22`(Homebrew), pyenv `3.12.2`(`.python-version` 요구는 `3.12` 두 자리 일치면
충분 — S-9). 아래는 **2026-09-11 verifier r1 수정 라운드 뒤 실측 재실행**(모든 명령
`cd ml-engine &&` 생략, 실제로는 그 안에서 실행). 이전 판(F-6·F-7)의 부정확한 서술은
아래 「r1 에서 정정된 것」에서 사실만 남긴다 — 옛 raw 출력을 다시 베끼지 않는다.

## S-1 ~ S-9 (scope.md acceptance_commands 순서)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음, 재실행) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + S-1 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 `ModuleNotFoundError`(부재 확인) |
| S-2a | `uv run ruff check .` | 0 | All checks passed!(`tests/gates/**` 포함 34개 파일 스캔 대상 — F-4 수정 뒤 `--show-files` 로 확인) |
| S-2b | `uv run ruff format --check .` | 0 | `34 files already formatted` |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | Success: no issues found in 10 source files |
| S-4 | `uv run lint-imports` | 0 | Contracts: 5 kept, 0 broken(「bidvector 직접 import 금지 — contracts 재수출(간접)은 허용」 계약 포함) |
| S-5 | `uv run python -m pytest tests -q` | 0 | **156 passed**(2A~2E 125 + 게이트 31, 회귀 0 — `--collect-only`로 156 재확인) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음 (대상 0개 파일 중 allowlist 밖 위반 0) |
| S-7 | `reuse_provenance_check.py` + 양성 대조 둘(`reuse-mismatch.md`·`reuse-claims-fake-pointer.md`) | 0 | 정상: 위반 0 · 값 어긋남 양성: exit 1(`docstring(...) != evidence(...)`) · 포인터 없음 양성(F-3 신규): exit 1(`evidence 에는 있으나 모듈 docstring 에 Reuse 포인터가 없다`) |
| S-8 | `./gradlew --no-build-cache --no-daemon clean check`(저장소 루트) | 0 | BUILD SUCCESSFUL in 52s, 345 actionable tasks — Kotlin 무영향 |
| S-9 | python 버전 두 자리 대조(`.python-version` vs `requires-python`) | 0 | assert 통과(출력 없음) |

## verifier r1 finding 별 재현 명령·결과

- **F-1(high)**: `printf 'from ml_engine.contracts import common_pb2\n' >
  src/ml_engine/serving/_probe.py && uv run lint-imports` → **수정 전 exit 1**(BROKEN,
  간접 연쇄까지 닫힘) → **수정 후 exit 0**(`Contracts: 5 kept, 0 broken`,
  `allow_indirect_imports = true`). 양성 대조: `tests/gates/fixtures/good_serving/`
  (KEPT) · `tests/gates/fixtures/bad_contracts_bypass/`(직접 import 는 계속 BROKEN —
  F-1 수정이 F-2 방어를 안 되돌림을 확인).
- **F-2(high)**: `printf 'from ml_engine.contracts._generated.bidvector.ml.v1 import
  common_pb2\n' > src/ml_engine/serving/_probe.py && uv run python -c "import
  ml_engine.serving._probe"` → **수정 전**: 성공(런타임 import 도 됨) → **수정 후**:
  `ModuleNotFoundError: No module named 'ml_engine.contracts._generated'`(`.contracts-
  generated/`가 패키지 트리 밖으로 옮겨져 그 경로 자체가 없다). `lint-imports` 도 5
  kept 0 broken 불변(문제였던 정적 우회 자체가 구조적으로 사라짐).
- **F-3(medium)**: `uv run python tools/reuse_provenance_check.py --evidence
  tests/gates/fixtures/reuse-claims-fake-pointer.md` → **수정 전 exit 0**(포인터 없는
  이식본을 못 봄) → **수정 후 exit 1**(`evidence 에는 있으나 모듈 docstring 에 Reuse
  포인터가 없다` — 양방향 대조).
- **F-4(medium)**: `uv run ruff check --show-files . | grep tests/gates` → **수정
  전**: 0줄(전부 제외) → **수정 후**: 19줄(게이트 test·fixtures 전부 스캔 대상).
- **F-5(medium)**: `actions/setup-python@v6`(v5=node20→v6=node24)·
  `astral-sh/setup-uv@v7`(v6=node20→v7=node24) — GitHub API 로 각 액션의
  `action.yml`(`using:`) 실측, Node24 를 만족하는 가장 낮은 major로 고정.
- **F-8(low)**: `[[tool.design-ratchet.allowlist]]` 블록을 `allowlist = []`(inline)와
  같이 두고 `uv run python tools/design_ratchet.py --check` → **수정 전**: `TOML parse
  error ... duplicate key`, exit 2(`uv` 자체가 `pyproject.toml` 파싱에 실패 —
  `ruff`·`mypy`·`pytest` 등 `uv run` 전체가 깨짐) → **수정**: `allowlist = []` 제거,
  `[[tool.design-ratchet.allowlist]]` array-of-tables 만 쓰도록 주석 정정(항목 0 은
  키 부재로 표현, `test_allowlist_is_empty_at_5a` 불변으로 재확인).

## r1 에서 정정된 것 (F-6·F-7 — 이전 판 evidence 자체의 오류)

- **F-6 — 「`_generated/` 없으면 세그폴트」 서술은 틀렸다.** 그 관찰은 **옛 상대 import
  코드**(`from ._generated.bidvector.ml.v1 import …`, D-5A-0 (b) 원문 그대로 첫 시도)
  기준이었다. F-1/F-2 수정으로 `contracts/__init__.py`가 `sys.path` 삽입 + 절대
  import(`from bidvector.ml.v1 import …`)로 바뀌었고, 생성 위치도 패키지 트리 밖으로
  옮겨졌다 — 현재 코드는 `.contracts-generated/`가 **아예 없어도** `lint-imports`가
  정상 exit(0 또는 1, 세그폴트 없음). `.gitignore`·`checklist.md`·이 파일의 해당
  서술을 삭제했다(`.gitkeep`도 필요 없어져 제거).
- **F-7 — 이전 판의 수치 둘이 실측과 달랐다.** 「15 files」(당시 게이트 test 가
  `extend-exclude`에 가려 스캔 밖이라 더 적게 잡혔다) → 이번 판은 34(F-4 수정 뒤)
  · 「32개 패키지」→ 실측은 처음부터 `uv sync` 출력이 `Audited 33 packages`(설치
  33개, 텍스트 오기). 이번 판은 재실행 원문을 그대로 옮겼다.

## rollback 실측

`reports/evidence/m5/5a/rollback.md` 정본(이번 라운드 코드 커밋 4개 목록으로 재산출).
