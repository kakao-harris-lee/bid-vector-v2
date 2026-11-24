#!/usr/bin/env bash
# M15/15Q — 운영 스택에서 **알림 relay 일회 러너**를 한 번 돌린다(runbook §13.3).
#
#     ./tools/prod-notification-relay.sh --env-file <운영 env> --relay-env-file <채널 env> \
#         --state-dir <디렉터리> --owner <운영자 id> [--environment production|staging] \
#         [--claim-limit N] [--project <이름>]
#
# 러너 자체(무엇을 집는가 · 종료 코드 · 억제 판정 · 기동 거부의 뜻)의 정본은
# `docs/runbook/m6-6e-operations.md` §3.2·§5 다. 이 스크립트가 더하는 것은 **운영 스택에서 그것을 거는
# 법**뿐이고, 모양은 `tools/prod-notification-sweep.sh`(§9.1)와 같다 — 같은 compose 파일 셋 · 같은
# 프로젝트 이름 · 같은 env 파일 · release 태그는 `<state>/current` 에서 · `--pull never` · 잠금 둘 ·
# manifest 이미지 ID · 반입 트리 == 그 release 의 커밋 · 설정 통로 · 일회 컨테이너 잔존·이름 충돌.
#
# **훑기와 다른 것은 셋이다.**
#
# ① **env 파일이 둘이다**(§13.3). `--env-file` 은 스택 설정(`prod.env` — compose 가 요구하는 키 집합)
#    이고, `--relay-env-file` 은 알림 채널 설정(`relay.env` — compose 가 모르는 키라 템플릿에 넣지
#    않는다)이다. 둘째 파일은 **일회 컨테이너까지** 가야 한다. unit 의 `EnvironmentFile=` 은 그 값을
#    **이 스크립트 프로세스**의 환경에 올릴 뿐이고, compose 의 최상위 `--env-file` 은 **보간에만** 쓰여
#    컨테이너에 닿지 않는다(실측 — evidence commands). 컨테이너까지 가는 통로는 `run --env-from-file`
#    이다. 이 한 줄이 빠지면 세 값이 JVM 에 닿지 않고 `production` 러너가 §13.2 를 채웠는데도
#    `RefusedMissingChannelConfig` 로 기동 실패한다.
#
# ② **그 둘째 파일이 새 설정 통로다 — 키 집합으로 닫는다.** `--env-from-file` 은 서비스가 선언한 env 에
#    **더하고, 같은 이름이면 이긴다**(실측 — evidence commands). 그래서 `relay.env` 에 들어온
#    `JAVA_TOOL_OPTIONS` 나 `BIDVECTOR_PERSISTENCE_JDBCURL` 한 줄이 **반입 가드를 거치지 않은 채**
#    compose 가 준 값을 덮는다(`_prod_config_surface_ok` 는 해석된 compose 만 본다 — 이 파일은 그 밖이다).
#    통로 이름을 열거해 거부하면 새 이름 하나로 다시 열리므로 **표면 자체를 등식으로** 닫는다:
#    `relay.env` 의 키는 §13.2 의 일곱(필수 셋 + 선택 넷) **밖이면 거절**이다. 허용 이름은 손으로
#    열거하지 않고 **Spring 의 두 사상**(dash 제거 · dash→`_`)으로 속성 이름에서 만든다.
#
# ③ **금지되는 JVM 옵션**(§13.3). JDK HttpClient 의 내부 로그 속성 둘이 지정되면 요청 URL 이 stderr 로
#    나가고 그 URL 은 **경로에 봇 자격을 싣는다**. 러너 자신도 `RefusedHttpClientLogging` 으로 기동을
#    거부하지만(§13.6), 그 거부는 종료 1 이라 러너의 「예외」와 섞인다 — 여기서 **전제 실패(3)** 로
#    먼저 끊는다. 보는 자리는 **해석된 compose 가 못 보는 둘**이다: 이 스크립트 프로세스의 JVM 옵션
#    통로(`JAVA_TOOL_OPTIONS`·`JDK_JAVA_OPTIONS`·`_JAVA_OPTIONS`)와 `relay.env`.
#    compose 축은 `_prod_config_surface_ok` 가 이미 허용 형태 하나로 묶어 두었다(겹치지 않게 둔다).
#    판정은 **존재만** 본다 — `=false`·빈 값도 거절이다(§13.3 「켜지 않는다가 아니라 지정하지 않는다」).
#
# 종료 코드 — **러너의 다섯과 래퍼의 둘이 겹치지 않게 옮겼다.**
# `RelayExitCode` 는 0·1·2·3(`LEASE_BUSY`)·4(`ENV_SUPPRESSED`) 다섯인데(m6-6e §3.2) 훑기 선례의 래퍼는
# 3 을 전제 실패에, 4 를 미룸에 쓴다. 그대로 두면 **뜻이 정확히 반대인 둘**이 같은 수로 만난다:
# 래퍼 3(고쳐야 한다) ↔ 러너 3(기다리면 풀린다) · 래퍼 4(기다리면 풀린다) ↔ 러너 4(고쳐야 한다).
# §9.5 의 경보는 종료 코드로만 갈라지므로 그 겹침은 **경보를 정확히 거꾸로** 울린다. 그래서 래퍼가
# 러너의 3·4 를 **5·6 으로 옮기고** 옮겼다는 사실을 줄로 남긴다.
#
#   0 `COMPLETE` · 1 `FAILED` · 2 `INCOMPLETE`     — 러너의 것 그대로
#   3 이 스크립트의 전제 실패                        — 훑기와 같은 목록 + relay.env 축 셋
#   4 **미뤘다**(잠금 경합 — 설계된 정상, §9.1)
#   5 러너 `LEASE_BUSY`(옛 3)                        — 기다리면 풀린다. 경보를 울리지 않는다
#   6 러너 `ENV_SUPPRESSED`(옛 4)                    — 기다려도 안 풀린다. 경보를 울린다
#
# 신호(TERM·INT·HUP)로 끝나면 그 신호로 죽는다(128+n) — 그 경로에서 이 run 의 일회 컨테이너는 trap 이
# 치운다. 호출 모양·unit·타이머·활성화 절차의 정본은 runbook §13.3·§13.4 다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

_die() { echo "prod-notification-relay: $1" >&2; exit "${2:-3}"; }

ENVF="" RELAYENVF="" STATE="" PROJECT="" OWNER="" ENVIRONMENT=production CLAIM_LIMIT=50
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --relay-env-file) RELAYENVF="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --owner) OWNER="${2:-}"; shift 2 ;;
    --environment) ENVIRONMENT="${2:-}"; shift 2 ;;
    --claim-limit) CLAIM_LIMIT="${2:-}"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 3 ;;
  esac
done
[ -n "$ENVF" ] && [ -n "$RELAYENVF" ] && [ -n "$STATE" ] && [ -n "$OWNER" ] \
  || _die "사용법: $0 --env-file <운영 env> --relay-env-file <채널 env> --state-dir <dir> --owner <운영자 id> [--environment production|staging] [--claim-limit N] [--project <이름>]" 3
# **환경은 둘만 받는다.** `RuntimeEnvironment` 는 넷이지만 운영 스택에서 뜻이 있는 것은 `production`
# (발송)과 `staging`(§13.4 ① 의 억제 확인)뿐이다. 나머지를 받으면 relaxed binding 이 조용히 억제 환경을
# 세워 **보내지 않는 run 이 성공으로 끝난다** — 좁히는 쪽이 fail-closed 다.
case "$ENVIRONMENT" in
  production|staging) ;;
  *) _die "--environment 는 production 또는 staging 이다: ${ENVIRONMENT}" 3 ;;
esac
[[ "$CLAIM_LIMIT" =~ ^[1-9][0-9]{0,4}$ ]] \
  || _die "--claim-limit 는 1 이상의 정수다(러너가 1 미만을 기동 실패로 거부한다): ${CLAIM_LIMIT}" 3
[ -r "$ENVF" ] || _die "운영 env 파일을 읽지 못했다: $ENVF" 3
[ -r "$RELAYENVF" ] || _die "채널 env 파일을 읽지 못했다: $RELAYENVF(§13.2)" 3

# ── relay.env — 키 집합과 JVM 옵션 축 ────────────────────────────────────────────
# **값은 어느 출력에도 옮기지 않는다** — 이 파일은 봇 자격과 chat id 를 담는다(§13.1). 아래 문면이
# 나르는 것은 **키 이름**과 **속성 이름**뿐이다.
#
# 허용 이름을 손으로 적지 않고 속성 이름에서 만든다 — Spring 의 `SystemEnvironmentPropertyMapper` 가
# 환경 변수 후보를 **둘** 만든다(dash 제거 · dash→`_`). 두 사상을 여기서 같은 규칙으로 되풀이하면
# 「문서에 적힌 철자」와 「바인딩되는 철자」가 갈리지 않는다.
_RELAY_REQUIRED_PROPS=(
  bidvector.notification.telegram.bot-credential
  bidvector.notification.telegram.chat-id
  bidvector.notification.site-base-url
)
_RELAY_OPTIONAL_PROPS=(
  bidvector.notification.telegram.base-url
  bidvector.notification.telegram.route-key
  bidvector.notification.telegram.connect-timeout
  bidvector.notification.telegram.response-timeout
)
_relay_env_names() { # <속성 이름> → 그 속성이 받는 환경 변수 이름 둘
  local p="$1" dots
  dots="$(printf '%s' "$p" | tr '.' '_' | tr '[:lower:]' '[:upper:]')"
  printf '%s\n' "${dots//-/}"   # dash 제거   — bidvector.…bot-credential → …BOTCREDENTIAL
  printf '%s\n' "${dots//-/_}"  # dash → `_`  — bidvector.…bot-credential → …BOT_CREDENTIAL
}
_relay_allowed_names() {
  local p
  for p in "${_RELAY_REQUIRED_PROPS[@]}" "${_RELAY_OPTIONAL_PROPS[@]}"; do _relay_env_names "$p"; done
}
# 파일에서 **키 이름만** 뽑는다. 주석·빈 줄은 건너뛰고, 그 밖에 `KEY=` 모양이 아닌 줄은 **거절**한다
# (docker 의 env 파일 해석기가 그 줄에서 끊으면 그 rc 가 러너의 「1 예외」와 섞인다).
_relay_env_keys() { sed -n 's/^[[:space:]]*\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' "$1"; }
_relay_env_junk() { sed -n '/^[[:space:]]*$/d; /^[[:space:]]*#/d; /^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*=/d; p' "$1"; }

junk_n="$(_relay_env_junk "$RELAYENVF" | grep -c '' || true)"
[ "$junk_n" = 0 ] \
  || _die "채널 env 에 'KEY=값' 도 주석도 아닌 줄이 ${junk_n}개 있다($RELAYENVF) — docker 의 env 파일 해석기가 거기서 끊는다. 그 줄을 지운다(문면은 줄 내용을 옮기지 않는다 — 이 파일은 자격을 담는다)" 3
stray="$(comm -23 <(_relay_env_keys "$RELAYENVF" | sort -u) <(_relay_allowed_names | sort -u) | tr '\n' ' ')"
[ -z "${stray// /}" ] \
  || _die "채널 env 에 §13.2 밖의 키가 있다: ${stray}— 이 파일은 일회 컨테이너의 env 를 **덮으므로**, 여기 들어온 이름 하나가 반입 가드를 거치지 않고 compose 가 준 값을 이긴다. §13.2 의 일곱 키만 둔다" 3
_relay_env_has() { _relay_env_keys "$RELAYENVF" | grep -qxF -f <(_relay_env_names "$1"); }
for _p in "${_RELAY_REQUIRED_PROPS[@]}"; do
  _relay_env_has "$_p" \
    || _die "채널 env 에 필수 키가 없다(${_p} — $(_relay_env_names "$_p" | tr '\n' ' ')) — §13.2 를 채우거나 --relay-env-file 이 가리키는 파일을 본다. **값이 비었는지는 여기서 보지 않는다**(그 갈래는 러너가 RefusedMissingChannelConfig 로 말한다, §13.6)" 3
done
unset _p

# **금지 속성 둘** — 술어는 `tools/prod-env.sh` 에 하나로 둔다(평가 커밋 래퍼도 같은 판단을 한다).
# 보는 자리는 **해석된 compose 가 못 보는 둘**이다: 이 프로세스의 JVM 옵션 통로와 `relay.env`.
_prod_jvm_env_log_free || _die "${_PROD_HTTPCLIENT_LOG_ERR}(§13.3)" 3
_prod_httpclient_log_free_file "$RELAYENVF" || _die "${_PROD_HTTPCLIENT_LOG_ERR}" 3

# ── 훑기와 같은 전제들 ──────────────────────────────────────────────────────────
# 아래 `run --pull never` 가 compose 판에 기댄다 — 문턱이 반입에만 있으면 내려앉은 host 에서 「알 수
# 없는 플래그」의 종료 코드가 러너 자신의 「1 예외」와 섞인다(훑기 머리말의 CR L-7 과 같은 축).
_prod_compose_version_ok || _die "$_PROD_COMPOSE_VER_ERR" 3
# **`run --env-from-file` 을 아는 판인가** — 같은 축의 둘째 문턱이다. 판 번호로 묻지 않고 **그 도구에게
# 직접** 묻는다(이 플래그가 들어온 판 번호를 지어내면 그 수가 곧 거짓이 되는 자리다). 모르면 둘째 env
# 파일이 컨테이너에 닿지 않고, 그 run 은 §13.2 를 채웠는데도 기동 실패로 끝난다.
docker compose run --help 2>/dev/null | grep -q -- '--env-from-file' \
  || _die "이 docker compose 는 'run --env-from-file' 을 모른다 — 채널 env 가 일회 컨테이너에 닿지 않는다(§13.3). compose 를 올린다" 3

# **반입·되돌림·훑기와 같은 잠금 둘을 같은 순서로 잡는다**(`tools/prod-env.sh`). relay 는 멱등이 아니지만
# (집은 행은 종단으로 간다) **겹치면 돌지 않는 쪽**이 맞다 — 임대가 배타를 지더라도 반입 중에 옛
# `current` 로 일회 컨테이너를 띄우는 길은 잠금만이 막는다.
#
# **경합은 `4`, 전제 실패는 `3`**(M15/15M, cr H-1). 가르는 값은 문면이 아니라 `_PROD_LOCK_BUSY` 다.
_deferred_or_precondition() { [ -n "${_PROD_LOCK_BUSY:-}" ] && echo 4 || echo 3; }
PROJ_FOR_LOCK="${PROJECT:-$BIDVECTOR_PROD_PROJECT}"
_prod_project_lock "$PROJ_FOR_LOCK" || _die "$_PROD_LOCK_ERR" "$(_deferred_or_precondition)"
_prod_lock "$STATE/.lock" "이 상태 디렉터리" || _die "$_PROD_LOCK_ERR" "$(_deferred_or_precondition)"

rel="$(cat "$STATE/current" 2>/dev/null || true)"
[ -n "$rel" ] || _die "반입 기록이 없다($STATE/current) — 반입 전에는 보낼 스택이 없다" 3
_prod_valid_tag "$rel" \
  || _die "반입 기록이 release 태그 모양이 아니다($STATE/current) — 손상됐다. 반입 기록을 먼저 본다" 3

pending="$(cat "$STATE/pending" 2>/dev/null || true)"
if [ -n "$pending" ] && [ "$pending" != "$rel" ]; then
  _die "끝나지 않은 반입이 있다(pending=${pending} · current=${rel}) — 먼저 되돌리거나 반입을 마친다(runbook 「되돌림 — 반입이 실패했을 때」)" 3
fi

MAN="$STATE/releases/${rel}.manifest"
[ -r "$MAN" ] || _die "release manifest 가 없다: ${rel}(${MAN}) — 반입 기록과 release 기록이 어긋났다" 3
_prod_images_match "$MAN" "$rel" || _die "${_PROD_IMAGE_ERR} — 보내지 않는다" 3
_prod_git_sha_match "$MAN" || _die "${_PROD_GIT_SHA_ERR} — 보내지 않는다" 3

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")
export BIDVECTOR_RELEASE_TAG="$rel"

cfg="$("${COMPOSE[@]}" config --format json 2>/dev/null)" || _die "운영 compose 를 해석하지 못했다" 3
_prod_config_surface_ok "$cfg" || _die "${_PROD_SURFACE_ERR} — 보내지 않는다" 3
PROJ="$(printf '%s' "$cfg" | jq -r .name)"
[ -n "$PROJ" ] && [ "$PROJ" != null ] || _die "해석된 compose 프로젝트 이름을 읽지 못했다" 3
[ "$PROJ" = "$PROJ_FOR_LOCK" ] \
  || _die "해석된 프로젝트 이름이 잠금을 잡은 이름과 다르다: ${PROJ} ≠ ${PROJ_FOR_LOCK} — 잠금과 잔존·일회 컨테이너 이름이 다른 자리를 본다" 3

# **지난 run 의 일회 컨테이너가 남아 있으면 보내지 않는다.** 술어는 훑기와 **같은 것**이라 남은 훑기
# 컨테이너도 여기서 걸린다(그 반대도 같다 — runbook §11 제한 13).
_prod_oneoff_residue "$PROJ" || _die "${_PROD_ONEOFF_ERR}" 3

echo "notification-relay: release ${rel} · 프로젝트 ${PROJ} · 환경 ${ENVIRONMENT} · claim-limit ${CLAIM_LIMIT}"
ONEOFF_NAME="${PROJ}-app-run-relay"
clash="$(docker ps -aq --filter "name=^${ONEOFF_NAME}$" 2>/dev/null)" \
  || _die "일회 컨테이너 이름 충돌을 세지 못했다(docker ps 실패) — 판정 불가" 3
[ -z "$clash" ] \
  || _die "일회 컨테이너 이름 ${ONEOFF_NAME} 를 이미 다른 컨테이너가 쓰고 있다(이 프로젝트 밖일 수 있다) — 'docker ps -a --filter name=^${ONEOFF_NAME}\$' 로 보고 치운 뒤 다시" 3

_relay_on_signal() {
  local sig="$1"
  trap - TERM INT HUP
  docker rm -f "$ONEOFF_NAME" >/dev/null 2>&1 || true
  echo "notification-relay: ${sig} 로 끝났다 — 이 run 의 일회 컨테이너 ${ONEOFF_NAME} 를 치웠다" >&2
  kill -s "$sig" $$
}
trap '_relay_on_signal TERM' TERM
trap '_relay_on_signal INT' INT
trap '_relay_on_signal HUP' HUP
# `--env-from-file` — 둘째 env 파일을 **컨테이너까지** 넘기는 유일한 통로다(머리말 ①).
# `SPRING_MAIN_WEB_APPLICATION_TYPE=none` — 러너를 켤 때 HTTP 표면을 끈다(m6-6e §1.3).
# 채널은 `telegram` 으로 **고정한다** — 오늘 활성 경로가 있는 채널은 그것 하나이고(Email 은
# `enabled=false`), 고를 수 있게 두면 `RefusedChannelWithoutActiveRoute` 를 운영자가 만들 수 있다(§13.6).
rc=0
"${COMPOSE[@]}" run --rm --no-deps --pull never --name "$ONEOFF_NAME" \
  --env-from-file "$RELAYENVF" \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  app \
  --bidvector.relay.mode=once \
  --bidvector.relay.environment="$ENVIRONMENT" \
  --bidvector.relay.owner="$OWNER" \
  --bidvector.relay.channel=telegram \
  --bidvector.relay.claim-limit="$CLAIM_LIMIT" || rc=$?
case "$rc" in
  3) echo "notification-relay: 러너가 LEASE_BUSY(3) 로 끝났다 — 같은 owner 의 relay 가 임대를 쥐고 있다. 기다리면 풀린다(m6-6e §3.7). 래퍼의 전제 실패(3)와 겹치지 않게 **5** 로 낸다" >&2; exit 5 ;;
  4) echo "notification-relay: 러너가 ENV_SUPPRESSED(4) 로 끝났다 — 정책표가 이 환경에 붙인 모드가 Live 가 아니다(claim 0 · 행은 PENDING 보존). 기다려도 풀리지 않는다(m6-6e §5.1). 래퍼의 미룸(4)과 겹치지 않게 **6** 으로 낸다" >&2; exit 6 ;;
  *) exit "$rc" ;;
esac
