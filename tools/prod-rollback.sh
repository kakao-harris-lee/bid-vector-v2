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

current="$(cat "$STATE/current" 2>/dev/null || true)"
previous="$(cat "$STATE/previous" 2>/dev/null || true)"
[ -n "$current" ] || _die "현재 release 기록이 없다 — 되돌릴 대상이 없다" 2
[ -n "$previous" ] || _die "직전 release 기록이 없다 — 첫 반입은 되돌릴 이전이 없다" 2
[ "$current" != "$previous" ] || _die "현재와 직전이 같다" 2

_migrations() { grep -E '^migration=' "$STATE/releases/$1.manifest" | cut -d= -f2- | sort; }
[ -r "$STATE/releases/${current}.manifest" ] && [ -r "$STATE/releases/${previous}.manifest" ] \
  || _die "manifest 를 읽지 못했다(현재 ${current} · 직전 ${previous})" 2
added="$(comm -23 <(_migrations "$current") <(_migrations "$previous"))"
echo "== 되돌림 ${current} → ${previous}"
if [ -n "$added" ]; then
  echo "  현재 release 가 직전에 없는 마이그레이션을 실었다: $(printf '%s' "$added" | tr '\n' ' ')" >&2
  backup="$(grep -E "^${current} " "$STATE/backups/index" 2>/dev/null | tail -n1 | cut -d' ' -f2- || true)"
  echo "  반입 직전 백업: ${backup:-기록 없음}" >&2
  echo "prod-rollback: 멈춤 — 이미지 되돌림만으로는 스키마가 돌아오지 않는다(runbook 「되돌림 — DB 를 넘는 경우」)" >&2
  exit 3
fi
echo "  마이그레이션 차이 0 — 이미지 되돌림으로 충분하다"

args=(--env-file "$ENVF" --state-dir "$STATE" --release "$previous")
[ -z "$PROJECT" ] || args+=(--project "$PROJECT")
[ "$APPLY" -eq 0 ] || args+=(--apply)
exec ./tools/prod-deploy.sh "${args[@]}"
