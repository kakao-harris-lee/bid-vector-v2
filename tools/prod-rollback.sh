#!/usr/bin/env bash
# M7/7F 본편 — **되돌림**(D-7F2-2). 기본은 dry-run 이다.
#
#     ./tools/prod-rollback.sh --env-file <운영 env> --state-dir <디렉터리> [--project <이름>] [--apply]
#
# 되돌리는 것은 **이미지**다 — 직전 release(`<state>/previous`)로 `tools/prod-deploy.sh` 를 다시 부른다
# (반입 경로가 하나다: 같은 preflight·백업·헬스).
#
# **DB 는 이미지 되돌림으로 돌아오지 않는다.** 스키마 변경은 forward-fix 가 정본이고(runbook
# `m6-6b2-backup-restore.md` §1), 현재 release 가 직전 release 에 없는 마이그레이션을 실었다면 옛 이미지는
# 새 스키마 위에서 뜨게 된다. 그 경우 이 스크립트는 **멈춘다(exit 3)** — 되돌리려면 반입 직전 백업의 복원이
# 필요하고 그 사이의 쓰기를 잃는다. 그 판단은 운영자 몫이라 자동으로 하지 않는다(runbook 「되돌림」).
#
# 종료 코드: 0 · 1 반입 실패 · 2 사용법·판정 불가 · 3 마이그레이션을 넘는 되돌림(멈춤).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

_die() { echo "prod-rollback: $1" >&2; exit "${2:-1}"; }

ENVF="" STATE="" PROJECT="" APPLY=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --apply) APPLY=1; shift ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
[ -n "$ENVF" ] && [ -n "$STATE" ] || _die "사용법: $0 --env-file <운영 env> --state-dir <dir> [--project <이름>] [--apply]" 2

# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

# 반입과 **같은 잠금 둘**을 같은 순서로 쓴다 — 되돌림이 반입과 겹치면 상태 파일이 엉킨다(v V-4).
# 되돌림은 결국 `prod-deploy.sh` 를 부르므로 그 쪽이 잡을 수 있도록 **exec 하기 전에 전부 놓는다**.
# **잠금 둘을 정해진 순서로 잡는다** — 프로젝트 먼저, 그 다음 상태 디렉터리.
# 상태 디렉터리 하나만 잡던 앞 판은 **같은 프로젝트를 다른 state-dir 로** 동시에 반입할 수 있었다
# (v V3-4). 순서를 고정했으므로 교착은 없다. 잡지 못하면 **바로 거절**한다 — 기다리지 않는다.
_prod_lock() { # <잠금 파일> <무엇>
  local f="$1" what="$2" fd
  exec {fd}>>"$f" || _die "${what} 잠금 파일을 열지 못했다: $f" 2
  flock -n "$fd" || _die "다른 반입·되돌림이 ${what}에서 돌고 있다($f) — 끝난 뒤 다시" 2
  BIDVECTOR_LOCK_FDS="${BIDVECTOR_LOCK_FDS:-} $fd"
}
_prod_unlock_all() {
  local fd
  for fd in ${BIDVECTOR_LOCK_FDS:-}; do flock -u "$fd" 2>/dev/null || true; eval "exec ${fd}>&-" 2>/dev/null || true; done
  BIDVECTOR_LOCK_FDS=""
}
PROJ_FOR_LOCK="${PROJECT:-$BIDVECTOR_PROD_PROJECT}"
mkdir -p "$STATE"
_prod_lock "${TMPDIR:-/tmp}/bidvector-v2-prod.${PROJ_FOR_LOCK}.lock" "프로젝트 ${PROJ_FOR_LOCK}"
_prod_lock "$STATE/.lock" "이 상태 디렉터리"

current="$(cat "$STATE/current" 2>/dev/null || true)"
previous="$(cat "$STATE/previous" 2>/dev/null || true)"
pending="$(cat "$STATE/pending" 2>/dev/null || true)"

# **끝나지 않은 반입이 있으면 그것이 떠 있는 것**이고, 돌아갈 자리는 마지막으로 성공한 `current` 다.
# 그 경우가 가장 급한 경우인데 앞 판은 `current`/`previous` 만 보고 「현재와 직전이 같다」로
# 거부했다(v H-2 실측 — 떠 있는 것은 새 release 인데 되돌림이 막혔다).
if [ -n "$pending" ] && [ "$pending" != "$current" ]; then
  from="$pending"; to="$current"
  echo "== 되돌림 ${from} → ${to}  (끝나지 않은 반입 — 떠 있는 것은 ${from}, 마지막으로 성공한 것은 ${to})"
  [ -n "$to" ] || _die "마지막으로 성공한 release 기록이 없다 — 첫 반입이 실패했다(runbook 「되돌림 — 반입이 실패했을 때」)" 2
else
  from="$current"; to="$previous"
  [ -n "$from" ] || _die "현재 release 기록이 없다 — 되돌릴 대상이 없다" 2
  [ -n "$to" ] || _die "직전 release 기록이 없다 — 첫 반입은 되돌릴 이전이 없다" 2
  [ "$from" != "$to" ] || _die "현재와 직전이 같다" 2
  echo "== 되돌림 ${from} → ${to}"
fi

added="$(_prod_migrations_lost "$STATE" "$from" "$to")" \
  || _die "manifest 를 읽지 못했다(떠 있는 것 ${from} · 가려는 것 ${to})" 2
if [ -n "$added" ]; then
  echo "  떠 있는 release 가 ${to} 에 없는 마이그레이션을 실었다: $(printf '%s' "$added" | tr '\n' ' ')" >&2
  backup="$(grep -E "^${from} " "$STATE/backups/index" 2>/dev/null | tail -n1 | cut -d' ' -f2- || true)"
  echo "  반입 직전 백업: ${backup:-기록 없음}" >&2
  echo "prod-rollback: 멈춤 — 이미지 되돌림만으로는 스키마가 돌아오지 않는다(runbook 「되돌림 — DB 를 넘는 경우」)" >&2
  exit 3
fi
echo "  마이그레이션 차이 0 — 이미지 되돌림으로 충분하다"

args=(--env-file "$ENVF" --state-dir "$STATE" --release "$to")
[ -z "$PROJECT" ] || args+=(--project "$PROJECT")
[ "$APPLY" -eq 0 ] || args+=(--apply)
# 잠금을 놓고 넘긴다 — 같은 잠금을 `prod-deploy.sh` 가 다시 잡는다(겹치면 자기 자신에게 막힌다).
_prod_unlock_all
exec ./tools/prod-deploy.sh "${args[@]}"
