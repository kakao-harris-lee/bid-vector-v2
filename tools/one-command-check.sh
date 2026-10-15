#!/usr/bin/env bash
# 완료 조건 1(milestone-6.md 「새 checkout/clean database 에서 one-command
# build/test 가능」). Kotlin 전건 + Python 전건 + web 전건을 **한 명령**으로 순차 실행한다.
#
# 내부 명령은 `.github/workflows/ci.yml` 의 세 job(`check`·`ml-engine`·`web`)이 돌리는 명령
# **그대로**다(하네스 2026-09-12 「부분 게이트를 전건 대신 돌리지 않는다 — 정본은 CI
# 워크플로 job 이 돌리는 명령 그대로이고, 줄일 수 있는 단위는 게이트가 아니라 job」) —
# 새 축약·새 플래그를 만들지 않는다. CI 전용 step(체크아웃·툴체인 설치·**브라우저 시스템
# 패키지 설치(apt)**·아티팩트 업로드)만 뺀다 — 그 나머지 게이트 명령은 순서까지 ci.yml 과 같다
# (PR #75 리뷰 F — apt step 이 열거에서 빠져 있었다).
#
# 각 단계 실패를 삼키지 않는다(우회 (5)) — `set -euo pipefail` + 파이프 미사용(각 명령을
# 독립 스텝으로 실행, `PIPESTATUS` 확인이 필요한 파이프가 없다).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

_step() {
  echo ""
  echo "== one-command-check: $1 =="
}

# ---- Kotlin(check job) ----
_step "Kotlin — ./gradlew --no-daemon check"
./gradlew --no-daemon check

# F-6(D-6C-9, MEDIUM) — ci.yml 의 `check` job 은 이 step 도 돈다(OPEN-ADR-06
# 측정 입력). 머리 주석의 "CI 전용 step 만 뺀다"는 체크아웃·툴체인 설치·아티팩트 업로드
# 셋을 가리키고 이 step 은 그 셋이 아니다 — 뺐던 것이 축어 불일치였다.
_step "Kotlin — ./gradlew --no-daemon qualityBaseline"
./gradlew --no-daemon qualityBaseline

# ---- Python(ml-engine job, working-directory: ml-engine) ----
cd "$REPO_ROOT/ml-engine"

_step "Python — install (S-1)"
uv sync --frozen --all-extras

_step "Python — serving extras 분리 확인 (S-1b)"
uv sync --frozen --extra serving --no-dev
for m in sqlalchemy psycopg requests httpx celery; do
  if uv run --no-sync python -c "import $m" >/dev/null 2>&1; then
    echo "금지 패키지 $m 이 serving extras 에 설치됐다" >&2
    exit 1
  fi
done
uv sync --frozen --all-extras

_step "Python — ruff (S-2)"
uv run ruff check .
uv run ruff format --check .

_step "Python — mypy --strict (S-3)"
uv run mypy --strict src/ml_engine

_step "Python — import-linter (S-4)"
uv run lint-imports

_step "Python — pytest (S-5)"
uv run python -m pytest tests -q

_step "Python — 설계 래칫 (S-6)"
uv run python tools/design_ratchet.py --check

_step "Python — 재활용 출처 두 자리 대조 (S-7)"
uv run python tools/reuse_provenance_check.py
if uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md; then
  echo "양성 대조(어긋난 evidence)가 실패해야 하는데 통과했다" >&2
  exit 1
fi

_step "Python — 버전 두 자리 대조 (S-9)"
uv run python -c "import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python'], (v, p['project']['requires-python'])"

_step "Python — wheel 빌드 + 설치본 재수출 확인 (S-11)"
# reviewer LOW — 산출물이 스크립트 종료 후 `/tmp` 에 남아 로컬 반복 실행마다 쌓였다.
# 이 스크립트 안에서만 쓰는 임시 디렉터리라 종료 시(성공·실패 무관) 지운다 — CI
# `ml-engine` job 의 `/tmp/ml-engine-wheel`(이 diff 밖, 러너가 매 실행 폐기)과는 다른 자리.
ONE_COMMAND_WHEEL_DIR="$(mktemp -d /tmp/ml-engine-wheel-one-command-check.XXXXXX)"
trap 'rm -rf "$ONE_COMMAND_WHEEL_DIR"' EXIT
uv build --wheel -o "$ONE_COMMAND_WHEEL_DIR"
uv run python -m pytest tests/gates/test_wheel_reexport.py -q

# ---- web(web job, working-directory: web) ----
# M7/7A — ci.yml `web` job 의 명령을 순서까지 그대로 둔다. 뺀 CI 전용 step 은 둘이다:
# 체크아웃·setup-node(툴체인 설치)와 `npx playwright install-deps chromium`(브라우저가 쓰는
# 시스템 패키지 설치 — apt 를 건드리므로 로컬에서 돌릴 것이 아니다). 브라우저 자체 설치
# (W-9)는 **여기 있다** — 새 checkout 에서 이 스크립트 하나로 e2e 까지 돌 수 있어야 완료
# 조건 1 이고, 그래서 job 쪽도 그 두 명령을 나눠 두었다(축약·새 플래그를 만들지 않는다).
# **그 「e2e 까지」는 브라우저의 시스템 패키지가 이미 있는 장비를 전제한다**(PR #75 리뷰 V) —
# 없는 장비에서는 이 스크립트가 아니라 `npx playwright install-deps chromium`(apt·sudo)이
# 먼저 필요하다. 그 전제는 evidence 의 알려진 제한에 등재돼 있다.
#
# CI 에서 이 스크립트를 돌리는 `check` job 에는 그 두 CI 전용 step(setup-node · install-deps)이
# **그 job 안에** 있다(code-review r1 F-3) — 여기서 빼는 것은 「로컬에서 하지 않는다」는 뜻이고
# 「CI 에서 없다」는 뜻이 아니다.
cd "$REPO_ROOT/web"

_step "web — install (W-1)"
npm ci

_step "web — eslint (W-2)"
npm run lint

_step "web — typecheck (W-3)"
npm run typecheck

_step "web — 컴파일 경계 전제 (W-4)"
npm run gate:src-tree

_step "web — OpenAPI 생성 타입 바이트 등식 (W-5)"
npm run api:types:check

_step "web — vitest (W-6)"
npm run test

_step "web — build (W-7)"
npm run build

_step "web — 번들 경계 카나리 (W-8)"
npm run gate:boundary-canary

_step "web — Playwright chromium 설치 (W-9)"
npx playwright install chromium

_step "web — Playwright 스모크 (W-10)"
npm run test:e2e

_step "완료 — Kotlin 전건 + Python 전건 + web 전건 통과"
