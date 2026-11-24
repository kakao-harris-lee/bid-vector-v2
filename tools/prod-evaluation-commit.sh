#!/usr/bin/env bash
# M15/15Q — 운영 스택에서 **평가 커밋 일회 러너**를 한 번 돌린다(runbook §13.3).
#
#     ./tools/prod-evaluation-commit.sh --env-file <운영 env> --state-dir <디렉터리> \
#         --current-active-bids N [--project <이름>]
#
# 러너 자체(무엇을 평가하는가 · 종료 코드 셋 · 마침 줄의 계수 넷 · 멈춤 줄)의 정본은
# `docs/runbook/m6-6e-operations.md` §3.4 다. 이 스크립트가 더하는 것은 **운영 스택에서 그것을 거는
# 법**뿐이고, 모양은 `tools/prod-notification-sweep.sh`(§9.1)와 같다 — 같은 compose 파일 셋 · 같은
# 프로젝트 이름 · 같은 env 파일 · release 태그는 `<state>/current` 에서 · `--pull never` · 잠금 둘 ·
# manifest 이미지 ID · 반입 트리 == 그 release 의 커밋 · 설정 통로 · 일회 컨테이너 잔존·이름 충돌.
#
# **순서가 있다**(§13.3): 평가 커밋이 `outbox` 에 행을 남기고, relay 가 그 행을 집어 보낸다. 그래서
# 타이머도 평가 커밋이 **먼저**다(07:35·13:35 ↔ relay 07:40·13:40).
#
# **`--current-active-bids` 는 기본값을 지어내지 않는다.** `bidvector.evaluation.commit.
# current-active-bids` 는 기본값이 없고 미설정이 기동 실패다 — 이 저장소에 실 진행 입찰 계수기가 없어
# (`OPEN-6F10-CAPACITY-SOURCE`) 그 값을 호출자가 댄다는 것이 6A-3 운영자 결정 ③ 이고, 일회 러너에서
# 그 호출자는 **설정을 주는 운영자**다. 여력을 0 으로 지어내면 용량 보류(`CapacityHold`) 판정이 조용히
# 사라지므로, 여기서도 짓지 않고 **인자가 없으면 거절**한다.
#
# **run 당 계정 상한과 멈춤 줄은 출하 기본값 그대로다**(M15/15B·15H). `max-accounts-per-run`(50)·
# `max-consecutive-account-failures`(3)에 플래그를 열지 않는다 — 상한 밖은 **KST 달력일마다 한 칸씩**
# 회전하고(보폭 1), 그 덮개는 **run 사이의 최대 간격**에 달려 있다. 플래그를 열면 운영자가 상한을
# 흔드는 동안 한 바퀴가 말없이 길어진다. 같은 이유로 처방된 타이머는 **매 달력일**(`OnCalendar=*-*-*`)
# 이다 — 평일만 거는 timer 는 간격이 3 이라 한 바퀴가 그만큼 는다(m6-6e §3.4 의 인용 블록).
#
# 종료 코드: 러너의 것을 그대로 돌려준다(`EvaluationCommitExitCode` — 0 완료 · 1 계정 전수를 못 읽음 ·
# 2 요청 실패·계정 실패) · **3** 이 스크립트의 전제 실패(훑기와 같은 목록 + `--current-active-bids`
# 결손) · **4 미뤘다**(잠금 경합 — 설계된 정상. `SuccessExitStatus=4` 가 그 코드를 실패로 보지 않게
# 한다, runbook §13.3). 러너의 코드가 셋뿐이라 래퍼의 둘과 겹치지 않는다 — relay 쪽은 다섯이라
# 겹침을 옮겨야 했다(`tools/prod-notification-relay.sh` 머리말).
# 신호(TERM·INT·HUP)로 끝나면 그 신호로 죽는다(128+n) — 그 경로에서 이 run 의 일회 컨테이너는 trap 이
# 치운다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

_die() { echo "prod-evaluation-commit: $1" >&2; exit "${2:-3}"; }

ENVF="" STATE="" PROJECT="" ACTIVE_BIDS=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --current-active-bids) ACTIVE_BIDS="${2:-}"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 3 ;;
  esac
done
[ -n "$ENVF" ] && [ -n "$STATE" ] && [ -n "$ACTIVE_BIDS" ] \
  || _die "사용법: $0 --env-file <운영 env> --state-dir <dir> --current-active-bids <현재 진행 입찰 수> [--project <이름>]" 3
[[ "$ACTIVE_BIDS" =~ ^(0|[1-9][0-9]{0,4})$ ]] \
  || _die "--current-active-bids 는 0 이상의 정수다(지어내지 않는다 — 운영자가 댄다, OPEN-6F10-CAPACITY-SOURCE): ${ACTIVE_BIDS}" 3
[ -r "$ENVF" ] || _die "env 파일을 읽지 못했다: $ENVF" 3
# **금지되는 JVM 옵션**(runbook §13.3) — 술어는 relay 래퍼와 **같은 것**이다(`tools/prod-env.sh`).
# 이 러너는 봇 자격을 쥐지 않지만 바깥 호출을 하고, 이 저장소의 바깥 호출은 자격을 URL 에 싣는다 —
# 러너마다 「이쪽은 봐도 되나」를 다시 묻지 않도록 세 래퍼가 같은 금지를 진다. compose 축은
# `_prod_config_surface_ok` 가 이미 묶었다.
_prod_jvm_env_log_free || _die "${_PROD_HTTPCLIENT_LOG_ERR}(runbook §13.3)" 3
_prod_compose_version_ok || _die "$_PROD_COMPOSE_VER_ERR" 3

# **반입·되돌림·훑기와 같은 잠금 둘을 같은 순서로 잡는다**(`tools/prod-env.sh`). 평가 커밋은 relay 보다
# 먼저 돌아야 하므로(위 「순서가 있다」) 겹치면 **돌지 않고 다음 주기로 미룬다** — 같은 주기 안에서
# relay 가 먼저 잠금을 쥐었다면 이 run 의 행은 다음 relay run 이 집는다(행은 `PENDING` 으로 남는다).
#
# **경합은 `4`, 전제 실패는 `3`**(M15/15M, cr H-1). 가르는 값은 문면이 아니라 `_PROD_LOCK_BUSY` 다.
_deferred_or_precondition() { [ -n "${_PROD_LOCK_BUSY:-}" ] && echo 4 || echo 3; }
PROJ_FOR_LOCK="${PROJECT:-$BIDVECTOR_PROD_PROJECT}"
_prod_project_lock "$PROJ_FOR_LOCK" || _die "$_PROD_LOCK_ERR" "$(_deferred_or_precondition)"
_prod_lock "$STATE/.lock" "이 상태 디렉터리" || _die "$_PROD_LOCK_ERR" "$(_deferred_or_precondition)"

rel="$(cat "$STATE/current" 2>/dev/null || true)"
[ -n "$rel" ] || _die "반입 기록이 없다($STATE/current) — 반입 전에는 평가할 스택이 없다" 3
_prod_valid_tag "$rel" \
  || _die "반입 기록이 release 태그 모양이 아니다($STATE/current) — 손상됐다. 반입 기록을 먼저 본다" 3

pending="$(cat "$STATE/pending" 2>/dev/null || true)"
if [ -n "$pending" ] && [ "$pending" != "$rel" ]; then
  _die "끝나지 않은 반입이 있다(pending=${pending} · current=${rel}) — 먼저 되돌리거나 반입을 마친다(runbook 「되돌림 — 반입이 실패했을 때」)" 3
fi

MAN="$STATE/releases/${rel}.manifest"
[ -r "$MAN" ] || _die "release manifest 가 없다: ${rel}(${MAN}) — 반입 기록과 release 기록이 어긋났다" 3
_prod_images_match "$MAN" "$rel" || _die "${_PROD_IMAGE_ERR} — 평가하지 않는다" 3
_prod_git_sha_match "$MAN" || _die "${_PROD_GIT_SHA_ERR} — 평가하지 않는다" 3

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")
export BIDVECTOR_RELEASE_TAG="$rel"

cfg="$("${COMPOSE[@]}" config --format json 2>/dev/null)" || _die "운영 compose 를 해석하지 못했다" 3
_prod_config_surface_ok "$cfg" || _die "${_PROD_SURFACE_ERR} — 평가하지 않는다" 3
PROJ="$(printf '%s' "$cfg" | jq -r .name)"
[ -n "$PROJ" ] && [ "$PROJ" != null ] || _die "해석된 compose 프로젝트 이름을 읽지 못했다" 3
[ "$PROJ" = "$PROJ_FOR_LOCK" ] \
  || _die "해석된 프로젝트 이름이 잠금을 잡은 이름과 다르다: ${PROJ} ≠ ${PROJ_FOR_LOCK} — 잠금과 잔존·일회 컨테이너 이름이 다른 자리를 본다" 3

# 술어는 훑기·relay 와 **같은 것**이라 남은 그쪽 컨테이너도 여기서 걸린다(runbook §11 제한 13).
_prod_oneoff_residue "$PROJ" || _die "${_PROD_ONEOFF_ERR}" 3

echo "evaluation-commit: release ${rel} · 프로젝트 ${PROJ} · current-active-bids ${ACTIVE_BIDS}"
ONEOFF_NAME="${PROJ}-app-run-evaluation"
clash="$(docker ps -aq --filter "name=^${ONEOFF_NAME}$" 2>/dev/null)" \
  || _die "일회 컨테이너 이름 충돌을 세지 못했다(docker ps 실패) — 판정 불가" 3
[ -z "$clash" ] \
  || _die "일회 컨테이너 이름 ${ONEOFF_NAME} 를 이미 다른 컨테이너가 쓰고 있다(이 프로젝트 밖일 수 있다) — 'docker ps -a --filter name=^${ONEOFF_NAME}\$' 로 보고 치운 뒤 다시" 3

_evaluation_on_signal() {
  local sig="$1"
  trap - TERM INT HUP
  docker rm -f "$ONEOFF_NAME" >/dev/null 2>&1 || true
  echo "evaluation-commit: ${sig} 로 끝났다 — 이 run 의 일회 컨테이너 ${ONEOFF_NAME} 를 치웠다" >&2
  kill -s "$sig" $$
}
trap '_evaluation_on_signal TERM' TERM
trap '_evaluation_on_signal INT' INT
trap '_evaluation_on_signal HUP' HUP
# `SPRING_MAIN_WEB_APPLICATION_TYPE=none` — 러너를 켤 때 HTTP 표면을 끈다(m6-6e §1.3).
# `bidvector.evaluation.candidate-cap` 은 **compose 가 준다**(`BIDVECTOR_EVALUATION_CANDIDATECAP` —
# `tools/prod-env.sh` 의 고정 키 목록에 있다). 여기서 다시 주지 않는다 — 두 자리가 생기면 운영 env 와
# 인자가 갈리는 날이 온다.
rc=0
"${COMPOSE[@]}" run --rm --no-deps --pull never --name "$ONEOFF_NAME" \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  app \
  --bidvector.evaluation.mode=once \
  --bidvector.evaluation.commit.current-active-bids="$ACTIVE_BIDS" || rc=$?
exit "$rc"
