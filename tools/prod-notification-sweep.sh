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
# **반입과 같은 이미지 불변식**을 거친다 — `<state>/releases/<current>.manifest` 의 이미지 ID 와
# 지금 태그가 가리키는 ID 가 같아야 하고, 일회 컨테이너는 `--pull never` 로 띄운다. 누가 태그를
# 옮겼거나 태그가 로컬에 없으면 **훑지 않는다**(v V4-2).
#
# 종료 코드: 러너의 것을 그대로 돌려준다(0 완료 · 1 예외 · 2 적지 못한 알림) · 3 이 스크립트의 전제
# 실패(반입 기록 없음·손상 · 끝나지 않은 반입 · manifest 부재 · 태그 ID 불일치).
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

# **반입·되돌림과 같은 잠금 둘을 같은 순서로 잡는다**(`tools/prod-env.sh`). 잠금 없이 상태를 읽던
# 앞 판은, 반입이 백업 중이라 아직 `pending` 을 쓰기 전이면 **옛 `current` 로 일회 컨테이너를 띄웠고**
# 그 뒤 새 release 가 같은 DB 를 썼다(v V4-7). 훑기는 멱등이고 주기 실행이라, 겹치면 **돌지 않고**
# 다음 주기로 미루는 것이 맞다 — 기다리지 않는다.
PROJ_FOR_LOCK="${PROJECT:-$BIDVECTOR_PROD_PROJECT}"
_prod_project_lock "$PROJ_FOR_LOCK" || _die "$_PROD_LOCK_ERR" 3
_prod_lock "$STATE/.lock" "이 상태 디렉터리" || _die "$_PROD_LOCK_ERR" 3

rel="$(cat "$STATE/current" 2>/dev/null || true)"
[ -n "$rel" ] || _die "반입 기록이 없다($STATE/current) — 반입 전에는 훑을 스택이 없다" 3
# 손상된 `current` 는 **여기서** 끊는다. 앞 판은 그 값을 그대로 이미지 참조로 넘겨 compose 가
# exit 1 로 끝냈고, 그 1 은 러너 자신의 「예외」와 같은 값이라 journal 에서 앱 장애로 보였다(v V4-4).
_prod_valid_tag "$rel" \
  || _die "반입 기록이 release 태그 모양이 아니다($STATE/current) — 손상됐다. 반입 기록을 먼저 본다" 3

# **끝나지 않은 반입이 있으면 돌리지 않는다.** 그때 떠 있는 것은 `current` 가 아니라 `pending` 이고,
# 여기서 `current` 로 일회 컨테이너를 띄우면 **서비스와 다른 이미지**가 같은 DB 를 쓴다.
pending="$(cat "$STATE/pending" 2>/dev/null || true)"
if [ -n "$pending" ] && [ "$pending" != "$rel" ]; then
  _die "끝나지 않은 반입이 있다(pending=${pending} · current=${rel}) — 먼저 되돌리거나 반입을 마친다(runbook 「되돌림 — 반입이 실패했을 때」)" 3
fi

# **반입과 같은 이미지 불변식을 거친다.** 앞 판은 태그 ID 를 manifest 와 대조하지 않아, 누가 태그를
# 옮기면 반입은 멈추는데 러너는 **옮겨진 이미지를 운영 DB 자격과 함께** 띄웠다(v V4-2 실측 — 컨테이너가
# 실제로 만들어졌다). 술어는 `tools/prod-env.sh` 에 하나로 둔다.
MAN="$STATE/releases/${rel}.manifest"
[ -r "$MAN" ] || _die "release manifest 가 없다: ${rel}(${MAN}) — 반입 기록과 release 기록이 어긋났다" 3
_prod_images_match "$MAN" "$rel" || _die "${_PROD_IMAGE_ERR} — 훑지 않는다" 3

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")
export BIDVECTOR_RELEASE_TAG="$rel"

echo "notification-sweep: release ${rel} · 프로젝트 $("${COMPOSE[@]}" config --format json 2>/dev/null | jq -r .name)"
# `--no-deps` — postgres 는 이미 떠 있다. 이 러너가 스택을 건드리지 않는다.
# `--rm` — 일회 컨테이너는 남기지 않는다. 포트는 publish 하지 않아 떠 있는 app 과 다투지 않는다.
# `--pull never` — 기본값(missing)이면 태그가 로컬에 없을 때 compose 가 **레지스트리에서 당겨와**
#   운영 자격으로 실행하려 한다. 위 ID 등식이 먼저 막지만, 당기는 길 자체를 닫아 둔다(겹 둘).
#
# **exec 하지 않고 자식으로 돌린다** — 잠금을 이 셸이 쥐고 있어야 훑는 동안 반입이 끼어들지 않는다.
# 종료 코드는 러너의 것을 그대로 돌려준다.
rc=0
"${COMPOSE[@]}" run --rm --no-deps --pull never app --bidvector.notification-sweep.mode=once || rc=$?
exit "$rc"
