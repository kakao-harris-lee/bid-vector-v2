# M5/5B — commands.md

base `cc90f7060c3df2a002450420d1fc3de952d9b612`(PR #9 머지 = origin/main) · 계약 `8bc7a35`
(verifier r1 뒤 갱신 `d7b1ab6`) · 코드 마지막 커밋 `2685fe5`(verifier r1 M-1/M-2/L-1 수정,
최초 코드는 `e564d90` + reuse `abf5951`). 로컬: `uv 0.9.22`(Homebrew), pyenv `3.12.2`
(`.python-version` 요구는 `3.12` 두 자리 일치면 충분 — S-9). 전건 재실행(부분 게이트 없음).

## S-1 ~ S-9 (scope.md acceptance_commands 순서)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + S-1 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 `ModuleNotFoundError`(부재 확인), 복구 후 33 패키지 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · `50 files already formatted` |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found in 18 source files` |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 5 kept, 0 broken` |
| S-5 | `uv run python -m pytest tests -q` | 0 | **226 passed**(5A 156 + 5B 70, r1 수정으로 66→70 — manifest 순서 불변식 test 3 신설(M-1) + encoding `NoObservations`/`Built` 결과 타입 test 2 신설·구 test 1 대체(M-2), 회귀 0) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0) |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 / 1 | 정상: 위반 0 · 양성(값 어긋남): exit 1(`docstring(...) != evidence(...)`) — scope.md 는 `reuse-mismatch.md` 하나만 요구, `reuse-claims-fake-pointer.md`도 별도 확인(비고 참조) |
| S-9 | python 버전 두 자리 대조(`.python-version` vs `requires-python`) | 0 | assert 통과(출력 없음) |

## 비고

- S-7 두 번째 양성(`reuse-claims-fake-pointer.md`, 5A 도입)도 exit 1로 재확인했다 —
  `ml-engine/tools/generate_contracts.py: evidence 에는 있으나 모듈 docstring 에 Reuse
  포인터가 없다`. scope.md acceptance_commands는 `reuse-mismatch.md` 하나만 요구하므로
  이건 회귀 확인용 부가 명령이다.
- S-8(Kotlin `./gradlew check`)은 이 slice 의 정본에 없다 — `ml-engine/**`만 닿고
  워크플로가 `ml-engine` job 과 Kotlin `check` job 의 소스 독립을 선언한다
  (evidence-pack 2026-09-12 규율, digest §0 인용).
- leak 스캔: `grep -rniE -f config/quality/leak-patterns.txt ml-engine/src/ml_engine/features
  ml-engine/tests/features reports/evidence/m5/5b` → exit 1(매치 0).
- 하네스 레인 변경: `git log --oneline cc90f7060c3df2a002450420d1fc3de952d9b612..HEAD --
  CLAUDE.md .claude/` → 빈 목록(착수 이후 하네스 레인 변경 없음, r1 수정 라운드 뒤 재확인).
- verifier r1(ready-for-review, `_workspace/m5-5b/04_verifier_report.md`) medium 3·low 3 을
  한 라운드로 처리 — 코드 커밋 `2685fe5`(M-1·M-2·L-1, 게이트 술어 변경 포함 → 위 표 전건
  재실행), evidence 커밋(M-3 rollback.md 정정·L-2 reuse.md 정정·본 문서·checklist.md
  갱신)은 별도. L-3 은 5B 범위 밖(`OPEN-5B-FEATURES-FORBIDDEN`, checklist.md 등재).
