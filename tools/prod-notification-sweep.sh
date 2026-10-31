#!/usr/bin/env bash
# M7/7F 본편 — 운영 스택에서 **알림 훑기 일회 러너**를 한 번 돌린다(D-7F2-8).
#
#     ./tools/prod-notification-sweep.sh --env-file <운영 env> --state-dir <디렉터리> [--project <이름>]
#
# 러너 자체(무엇을 훑는가 · 종료 코드 · 로그 문형 · 끄는 법)의 정본은
# `docs/runbook/m7-7c3b-notification-sweep.md` 다. 이 스크립트가 더하는 것은 **운영 스택에서 그것을
# 거는 법**뿐이다 — 반입과 같은 compose 파일 셋·같은 프로젝트 이름·같은 env 파일을 쓴다.
#
# 왜 저장소 파일인가: 앞 판은 runbook 의 붙여넣기용 heredoc 이었고 두 자리가 그대로는 돌지 않았다
# (저장소 자리를 문서가 정하지 않았고, `BIDVECTOR_RELEASE_TAG` 를 주지 않아 매 run 이 보간에서 멈췄다 —
# v V3-3 · CR r3 M-4). 스크립트는 자기 위치에서 저장소를 알고, 태그는 상태 파일에서 읽는다.
#
# **반입된 release 태그는 `<state>/current` 에서 읽는다** — env 파일에 넣지 않는다(§1.3 이 그 키를
# 비워 두라고 적는다). 그래서 반입 뒤 갱신을 잊을 수 없다.
#
# 종료 코드: 러너의 것을 그대로 돌려준다(0 완료 · 1 예외 · 2 적지 못한 알림) · 3 이 스크립트의 전제 실패.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

_die() { echo "prod-notification-sweep: $1" >&2; exit "${2:-3}"; }

ENVF="" STATE="" PROJECT=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 3 ;;
  esac
done
[ -n "$ENVF" ] && [ -n "$STATE" ] || _die "사용법: $0 --env-file <운영 env> --state-dir <dir> [--project <이름>]" 3
[ -r "$ENVF" ] || _die "env 파일을 읽지 못했다: $ENVF" 3

rel="$(cat "$STATE/current" 2>/dev/null || true)"
[ -n "$rel" ] || _die "반입 기록이 없다($STATE/current) — 반입 전에는 훑을 스택이 없다" 3

# **끝나지 않은 반입이 있으면 돌리지 않는다.** 그때 떠 있는 것은 `current` 가 아니라 `pending` 이고,
# 여기서 `current` 로 일회 컨테이너를 띄우면 **서비스와 다른 이미지**가 같은 DB 를 쓴다.
pending="$(cat "$STATE/pending" 2>/dev/null || true)"
if [ -n "$pending" ] && [ "$pending" != "$rel" ]; then
  _die "끝나지 않은 반입이 있다(pending=${pending} · current=${rel}) — 먼저 되돌리거나 반입을 마친다(runbook 「되돌림 — 반입이 실패했을 때」)" 3
fi

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")
export BIDVECTOR_RELEASE_TAG="$rel"

echo "notification-sweep: release ${rel} · 프로젝트 $("${COMPOSE[@]}" config --format json 2>/dev/null | jq -r .name)"
# `--no-deps` — postgres 는 이미 떠 있다. 이 러너가 스택을 건드리지 않는다.
# `--rm` — 일회 컨테이너는 남기지 않는다. 포트는 publish 하지 않아 떠 있는 app 과 다투지 않는다.
exec "${COMPOSE[@]}" run --rm --no-deps app --bidvector.notification-sweep.mode=once
