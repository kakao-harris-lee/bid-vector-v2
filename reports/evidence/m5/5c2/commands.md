# M5/5C-2 — commands.md

정본 = CI `ml-engine` job 전건(S-1~S-9) + evidence 편집 라운드의 Kotlin `check`(S-10,
5C-1 S-10 규율 승계). 실패 이력은 결함이 아니라 검증이 실제로 작동했다는 증거다 — 구현
과정의 개별 모듈 RED→GREEN 이력은 커밋 로그가 이미 갖는다(evidence-pack 「라운드 이력
절 금지」).

## S-1 ~ S-9 (Python, 2026-09-15T15:10Z 재실행 확인)

## 2026-09-15T15:10:12Z
- cmd: `(cd ml-engine && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: Audited 33 packages(변경 없음)

## 2026-09-15T15:10:20Z
- cmd: `(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c "import $m" || exit 1; done && uv sync --frozen --all-extras)`
- exit: 0
- 핵심 결과: 5개 DB/HTTP 모듈 전부 ImportError(serving 순수성 유지) → all-extras 복원

## 2026-09-15T15:10:33Z
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)`
- exit: 0
- 핵심 결과: All checks passed! / 134 files already formatted

## 2026-09-15T15:10:41Z
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)`
- exit: 0
- 핵심 결과: Success: no issues found in 54 source files

## 2026-09-15T15:10:47Z
- cmd: `(cd ml-engine && uv run lint-imports)`
- exit: 0
- 핵심 결과: Contracts: 6 kept, 0 broken

## 2026-09-15T15:10:52Z
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 630 passed

## 2026-09-15T15:11:03Z
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)`
- exit: 0
- 핵심 결과: 설계 래칫 위반 없음(대상 0개 파일 중 allowlist 밖 위반 0)

## 2026-09-15T15:11:10Z
- cmd: `(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)`
- exit: 0
- 핵심 결과: 재활용 출처 두 자리 일치 — 위반 0 / mismatch fixture 는 예상대로 불일치 검출(음성 대조 성립)

## 2026-09-15T15:11:15Z
- cmd: `(cd ml-engine && uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']")`
- exit: 0
- 핵심 결과: 3.12 일치, assert 통과

## S-10 (evidence 편집 라운드 — 5C-1 규율 승계)

## 2026-09-15T15:12:10Z (완료 약 1m 5s 뒤)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 1m 5s, 346 actionable tasks(211 executed, 135 from cache)

## 비밀값 스캔 (리뷰 요청 조건)

## 2026-09-15T15:13:50Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m5/5c2/`
- exit: 1
- 핵심 결과: 매치 없음(exit 1 = grep 무매치, 통과)

RED 확인·수정 라운드 이력은 커밋 로그에 있다(evidence-pack 「라운드 이력 절 금지」) —
모듈별 커밋은 `git log --oneline <base_sha>..HEAD -- ml-engine/src/ml_engine/evaluation
ml-engine/src/ml_engine/training/holdout.py ml-engine/src/ml_engine/training/_holdout_fit.py
ml-engine/src/ml_engine/training/_holdout_window.py`로 확인한다.

## 수정 라운드 1 종결 — S-1~S-10 전건 재실행(HEAD `7b4057d`)

## 2026-09-15T16:30Z
- cmd: `(cd ml-engine && uv sync --frozen --all-extras)` — S-1
- exit: 0
## 2026-09-15T16:30Z
- cmd: S-1b(serving extra 순수성 5종 ImportError) — S-1b
- exit: 0 — 핵심 결과: 5개 전부 ImportError 확인, all-extras 복원
## 2026-09-15T16:30Z
- cmd: `(cd ml-engine && uv run ruff check . && uv run ruff format --check .)` — S-2
- exit: 0 — 핵심 결과: All checks passed! / 135 files already formatted
## 2026-09-15T16:30Z
- cmd: `(cd ml-engine && uv run mypy --strict src/ml_engine)` — S-3
- exit: 0 — 핵심 결과: Success: no issues found in 54 source files
## 2026-09-15T16:30Z
- cmd: `(cd ml-engine && uv run lint-imports)` — S-4
- exit: 0 — 핵심 결과: 계약 6 KEPT
## 2026-09-15T16:31Z
- cmd: `(cd ml-engine && uv run python -m pytest tests -q)` — S-5
- exit: 0 — 핵심 결과: **652 passed**(수정 라운드 1 이전 630 대비 +22 — H-C 변이 저항
  9 · H-A 3 · M-2 test 정정 · M-4/reviewer 확인불가 latest-window test 1 · 기타)
## 2026-09-15T16:31Z
- cmd: `(cd ml-engine && uv run python tools/design_ratchet.py --check)` — S-6
- exit: 0 — 핵심 결과: 위반 0
## 2026-09-15T16:31Z
- cmd: S-7(재활용 출처 양성 + 음성 대조) — S-7
- exit: 0 — 핵심 결과: 위반 0 / mismatch fixture 는 예상대로 29건 불일치 검출(음성 대조 성립, 라운드 1 이전과 동일 패턴)
## 2026-09-15T16:31Z
- cmd: python 3.12 assertion — S-9
- exit: 0
## 2026-09-15T16:31Z
- cmd: `./gradlew --no-daemon check` — S-10(evidence 편집 라운드)
- exit: 0 — 핵심 결과: BUILD SUCCESSFUL in 6s, 337 actionable tasks(32 executed, 305 up-to-date)
## 2026-09-15T16:32Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m5/5c2/`
- exit: 1 — 핵심 결과: 매치 없음(통과)

## 수정 라운드 2 — BLOCKER B-1 정정(verifier r2) — 이 커밋 HEAD 에서 S-10 재실측

verifier r2 B-1: 위 2026-09-15T16:32Z 기록이 비밀값 스캔 명령을 패턴 나열형으로
인라인해(`grep -rniE "(api[_-]?key|secret|...)"`) 그 줄 자신이
`config/quality/leak-patterns.txt` 매치가 되어 `:leakPatternGate` 를 실제로
붉혔다(`8ba3fd9`부터). 위 명령을 65행과 같은 참조형(`-f
config/quality/leak-patterns.txt`)으로 정정하고, 이 커밋 HEAD 에서 즉시 재실측한다
(이력 되쓰기 아님 — 새 커밋, 절대 규칙: evidence 를 고친 커밋마다 그 커밋에서 S-10
을 다시 돌려 적는다).

## 2026-09-15T16:53:27Z
- cmd: `./gradlew --no-daemon :leakPatternGate`
- exit: 0 — 핵심 결과: BUILD SUCCESSFUL(매치 0, 위 정정된 줄 포함 전체 스캔)
## 2026-09-15T16:53:27Z
- cmd: `./gradlew --no-daemon check` — S-10, 이 커밋(B-1 정정) HEAD 에서
- exit: 0 — 핵심 결과: BUILD SUCCESSFUL in 5s, 337 actionable tasks(31 executed, 306 up-to-date)

## clean-tree 게이트(verifier r1 M-3 ②) — 개별 경로 인자 + 양성 대조 1회

## 2026-09-15T16:28Z
- cmd: `git status --porcelain -- <in_scope 전 경로를 개별 인자로>`(변수 미사용,
  scope.md `in_scope` 목록을 그대로 나열 — `ml-engine/tests/evaluation/**`는
  디렉터리 안 파일 9개를 개별 나열, `tests/training/test_holdout*.py`는
  `test_holdout.py` 단일 파일로 전개)
- exit: 0
- 핵심 결과: 출력 없음(더러운 트리 아님)
- 양성 대조: `ml-engine/src/ml_engine/evaluation/windows.py`에 주석 한 줄을
  `>>`로 append 후 같은 `git status --porcelain -- <그 파일>` 재실행 →
  `M ml-engine/src/ml_engine/evaluation/windows.py` 잡힘(게이트가 실제로
  감지함을 확인) → `head -n 237 <파일> > tmp && mv tmp <파일>`로 **비파괴
  절삭 복원**(`git checkout --` 미사용, evidence-pack 규격) → 같은 명령 재실행
  결과 다시 빈 출력, `git diff --stat -- <그 파일>` 도 빈 출력(원상 확인)
