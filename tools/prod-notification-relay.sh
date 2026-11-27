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
#   7 **모르는 코드**(M15/15R)                       — 러너의 다섯 밖. 경보를 울린다
#
# **`5`·`6`·`7` 은 래퍼의 것이다.** 끝의 `case` 는 러너의 다섯을 **이름으로** 받고 그 밖을 `7` 로 낸다 —
# 앞 판처럼 모르는 코드를 그대로 흘리면 러너에 생긴 새 값이 래퍼의 `5` 자리로 들어와 `10-defer.conf` 의
# 깨끗한 종료에 삼켜진다(경보 없음 · journal 에 단서 없음 · 미룸 계수 0). **relay 러너에 값을 더하면
# 래퍼의 `case` 와 runbook §13.3 의 표를 함께 고친다** — `tools/prod-guards-check.sh` 의 등식 단언
# (`RelayExitCode` 의 값 집합 == 이 `case` 가 이름으로 받는 집합)이 그것을 강제한다.
#
# **「신호 종료」는 두 자리에서 뜻이 다르다.** ⓐ **래퍼 자신**이 TERM·INT·HUP 를 받으면 trap 이 받아 이 run 의
# 일회 컨테이너를 치우고 같은 신호로 자살한다(128+n) — 그 경로는 **위 `case` 에 닿지 않는다**(측정 —
# guards-check 의 신호 갈래). ⓑ **일회 컨테이너**가 KILL·TERM 으로 죽으면 `docker compose run` 이 128+n 을
# **돌려주므로** 그 값은 `case` 로 와서 `7` 이 된다. 위 `*)` 문면이 말하는 것은 ⓑ 다.
# 호출 모양·unit·타이머·활성화 절차의 정본은 runbook §13.3·§13.4 다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

_die() { echo "prod-notification-relay: $1" >&2; exit "${2:-3}"; }

# **값 없는 플래그는 전제 실패(3)다**(cr H-1). `$# == 1` 인데 `shift 2` 를 하면 bash 가 비-0 을 내고
# `set -e` 아래에서 스크립트가 **그 자리에서 1 로, 아무 말 없이** 죽는다 — 그 `1` 은 러너의
# `FAILED`(예외·임대 상실·기동 거부)와 같은 값이라 §9.5 의 경보가 틀린 이야기를 하고 journal 에 단서가
# 한 줄도 없다. 도달 경로는 §13.3 의 `ExecStart=` 자신이다(자리표시자를 **바꾸지 않고 지운** 운영자는
# 플래그를 argv 의 마지막 원소로 남긴다). 세 래퍼가 같은 가드를 쓴다.
_need() { [ "$#" -ge 2 ] || _die "$1 에 값이 없다 — 플래그 뒤에 값을 둔다" 3; }

ENVF="" RELAYENVF="" STATE="" PROJECT="" OWNER="" ENVIRONMENT=production CLAIM_LIMIT=50
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) _need "$@"; ENVF="$2"; shift 2 ;;
    --relay-env-file) _need "$@"; RELAYENVF="$2"; shift 2 ;;
    --state-dir) _need "$@"; STATE="$2"; shift 2 ;;
    --project) _need "$@"; PROJECT="$2"; shift 2 ;;
    --owner) _need "$@"; OWNER="$2"; shift 2 ;;
    --environment) _need "$@"; ENVIRONMENT="$2"; shift 2 ;;
    --claim-limit) _need "$@"; CLAIM_LIMIT="$2"; shift 2 ;;
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
# ── 둘째 env 파일 — 축 전부는 `tools/prod-env.sh` 의 **공유 술어**가 진다(M15/15U) ─────────────
# 권한(other 0) · compose 문턱 둘(판 · `run --env-from-file` 지원) · `jq` 문턱 · 경로와 값의 `$` ·
# 키 집합(**소비자와 같은 해석기**로 뽑는다) · 「통째로 버려짐」 · 필수 키 — 그 일곱을 한 자리에 둔다.
# M15/15U 의 수집 래퍼가 **같은 통로**(`run --env-from-file`)를 쓰므로 사본 둘을 두지 않는다
# (v2-지침서 §5 중복 금지: 사본 둘은 한쪽만 고쳐지는 날이 온다). **이 자리에 남는 것은 이 파일의 속성
# 목록과 라벨뿐이다** — §13.2 의 일곱(필수 셋 + 선택 넷)이고, 허용 환경 변수 이름은 그 속성 이름에서
# Spring 의 두 사상으로 도출된다(손 목록이 아니다).
#
# 거동은 앞 판과 **같다** — 검사의 순서·문면·종료 코드가 그대로이고, 그 등식의 증인은
# `tools/prod-guards-check.sh` 의 relay 단언 계수다(뽑기 전과 같은 수가 통과한다).
BIDVECTOR_SECOND_ENV_REQUIRED_PROPS=(
  bidvector.notification.telegram.bot-credential
  bidvector.notification.telegram.chat-id
  bidvector.notification.site-base-url
)
BIDVECTOR_SECOND_ENV_OPTIONAL_PROPS=(
  bidvector.notification.telegram.base-url
  bidvector.notification.telegram.route-key
  bidvector.notification.telegram.connect-timeout
  bidvector.notification.telegram.response-timeout
)
# **신호 trap 을 해석 앞에 건다**(cr R-7 · N-2) — 해석은 `$( )` 안에서 돌아 서브셸이므로 거기서 만든
# 임시 자리는 부모가 치워야 하고, `RETURN` trap 은 신호 갈래를 덮지 못한다. 아래 `_relay_on_signal` 이
# 이 trap 을 나중에 바꿔 단다(그때부터는 일회 컨테이너를 치우는 쪽이 신호를 받는다).
_prod_stub_trap_install
_prod_second_env_ok "$RELAYENVF" "채널 env" "§13.2" || _die "$_PROD_SECOND_ENV_ERR" 3
# **재는 경로와 넘기는 경로가 같다**(cr R-2) — 해석·권한·금지 속성 스캔·컨테이너 전달이 이 한 값을 쓴다.
RELAY_ABS="$_PROD_SECOND_ENV_ABS"

# **금지 속성 둘** — 술어는 `tools/prod-env.sh` 에 하나로 둔다(평가 커밋 래퍼도 같은 판단을 한다).
# 보는 자리는 **해석된 compose 가 못 보는 둘**이다: 이 프로세스의 JVM 옵션 통로와 `relay.env`.
_prod_jvm_env_log_free || _die "${_PROD_HTTPCLIENT_LOG_ERR}(§13.3)" 3
_prod_httpclient_log_free_file "$RELAY_ABS" || _die "${_PROD_HTTPCLIENT_LOG_ERR}" 3

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
  --env-from-file "$RELAY_ABS" \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  app \
  --bidvector.relay.mode=once \
  --bidvector.relay.environment="$ENVIRONMENT" \
  --bidvector.relay.owner="$OWNER" \
  --bidvector.relay.channel=telegram \
  --bidvector.relay.claim-limit="$CLAIM_LIMIT" || rc=$?
# **통과를 열거로 닫는다**(M15/15R, S-3). 앞 판의 `*) exit "$rc"` 는 모르는 코드를 그대로 흘렸는데,
# 래퍼가 쓰는 `5`·`6` 은 **러너의 다음 코드 자리**이고 `10-defer.conf` 가 `5` 를 깨끗한 종료로 만든다 —
# 러너에 여섯째가 `5` 로 들어오는 날 창 셋이 한꺼번에 조용해진다(경보 없음 · journal 에 단서 없음 ·
# 미룸 계수 0). 그래서 러너의 다섯만 **이름으로** 받고 그 밖은 `7` 로 낸다.
case "$rc" in
  0|1|2) exit "$rc" ;;
  3) echo "notification-relay: 러너가 LEASE_BUSY(3) 로 끝났다 — 다른 relay 프로세스가 임대를 쥐고 있다(임대는 owner 가 아니라 소비자 종류 단위다 — 다른 --owner 로 다시 쳐도 같은 자물쇠에서 선다). 기다리면 풀린다(m6-6e §3.7). 래퍼의 전제 실패(3)와 겹치지 않게 **5** 로 낸다" >&2; exit 5 ;;
  4) echo "notification-relay: 러너가 ENV_SUPPRESSED(4) 로 끝났다 — 정책표가 이 환경에 붙인 모드가 Live 가 아니다(claim 0 · 행은 PENDING 보존). 기다려도 풀리지 않는다(m6-6e §5.1). 래퍼의 미룸(4)과 겹치지 않게 **6** 으로 낸다" >&2; exit 6 ;;
  *) echo "notification-relay: 러너가 예상 밖의 종료 코드 ${rc} 를 냈다 — 래퍼가 5·6 을 쓰므로 그대로 통과시키지 않는다. docker run 자신의 실패(125·126·127) · **일회 컨테이너가** 신호로 죽어 돌아온 128+n(137·143) · 러너에 새로 생긴 코드가 여기 온다(래퍼 자신이 받은 신호는 위 trap 이 받아 이 자리에 닿지 않는다). **7** 로 낸다(SuccessExitStatus 밖이라 경보가 운다 — runbook §13.3 의 표)" >&2; exit 7 ;;
esac
