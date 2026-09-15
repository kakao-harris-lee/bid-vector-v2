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
