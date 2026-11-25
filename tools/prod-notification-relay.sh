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
# 신호(TERM·INT·HUP)로 끝나면 그 신호로 죽는다(128+n) — 그 경로는 trap 이 받아 이 run 의 일회 컨테이너를
# 치우고 같은 신호로 자살하므로 **위 `case` 에 닿지 않는다**(측정 — guards-check 의 신호 갈래).
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
# **이 파일은 부르는 사용자가 직접 읽어야 한다**(cr H-2). 앞 판의 `EnvironmentFile=` 은 systemd
# manager(root)가 읽어 환경으로 넘겼지만, 이제는 이 스크립트와 docker CLI 가 `User=` 로 읽는다 —
# §13.2 가 그 꼴(`install -m 600 -o "$USER"`)로 만들라고 적는다.
[ -r "$RELAYENVF" ] \
  || _die "채널 env 파일을 읽지 못했다: $RELAYENVF — 이 파일은 **이 스크립트를 돌리는 사용자**($(id -un))가 직접 읽는다(§13.2 의 'install -m 600 -o \$USER'). root 전용으로 조이면 매 run 이 여기서 멈춘다" 3
# **경로를 한 번만 풀고 그 뒤로는 그것만 쓴다**(cr R-2). 해석·권한·금지 속성 스캔·컨테이너 전달이
# **같은 것을 보아야** 이 slice 의 규율(「술어와 소비자가 같은 것을 본다」)이 경로 축에서도 참이 된다.
RELAY_ABS="$(readlink -f "$RELAYENVF" 2>/dev/null || true)"
[ -n "$RELAY_ABS" ] || _die "채널 env 의 절대 경로를 구하지 못했다: $RELAYENVF" 3
# **other 에 한 비트라도 열려 있으면 거절한다.** 이 파일은 봇 자격과 chat id 를 담는다(§13.1) —
# 잠금 자리의 권한을 보는 `_prod_project_lock` 과 같은 축이다. 소유자 **등식**은 걸지 않는다:
# root:<내 group> 0640 처럼 읽을 수 있고 좁은 배치가 있고, 그것까지 거절하면 도는 host 를 멈춘다.
# group 은 잠금 자리 술어(`& 022`)보다 느슨하다 — 그 틈은 §13.2 가 「그 사용자의 전용 group」으로 닫는다.
_relay_mode="$(stat -c '%a' "$RELAY_ABS" 2>/dev/null || true)"
[ -n "$_relay_mode" ] && [ "$(( 8#${_relay_mode: -3} & 8#007 ))" -eq 0 ] \
  || _die "채널 env 의 권한을 읽지 못했거나 other 에 열려 있다($RELAYENVF mode=${_relay_mode:-?}) — 이 파일은 봇 자격을 담는다(§13.2). 'chmod 600' 뒤에 다시" 3
unset _relay_mode

# ── compose 문턱 둘 — relay.env 를 그 해석기로 읽기 **전에** 선다 ───────────────
# 아래 `run --pull never` 가 compose 판에 기댄다 — 문턱이 반입에만 있으면 내려앉은 host 에서 「알 수
# 없는 플래그」의 종료 코드가 러너 자신의 「1 예외」와 섞인다(훑기 머리말의 CR L-7 과 같은 축).
# 이 둘이 **먼저** 서야 아래 채널 env 해석의 실패가 「판이 낡았다」와 섞이지 않는다.
_prod_compose_version_ok || _die "$_PROD_COMPOSE_VER_ERR" 3
# **`run --env-from-file` 을 아는 판인가** — 같은 축의 둘째 문턱이다. 판 번호로 묻지 않고 **그 도구에게
# 직접** 묻는다(이 플래그가 들어온 판 번호를 지어내면 그 수가 곧 거짓이 되는 자리다). 모르면 둘째 env
# 파일이 컨테이너에 닿지 않고, 그 run 은 §13.2 를 채웠는데도 기동 실패로 끝난다.
# 여기서는 **파이프를 물리지 않는다**: `docker … | grep -q` 는 플래그 줄이 앞쪽에 있어 docker 를
# SIGPIPE 로 죽일 수 있고, `pipefail` 아래에서 그 141 이 「모르는 판」으로 읽힌다. 값으로 받아 맞춘다.
# **빈 출력은 따로 말한다**(cr L-6) — 「플래그가 없다」와 「도움말을 받지 못했다」는 고칠 자리가 다르다.
_relay_run_help="$(docker compose run --help 2>/dev/null || true)"
[ -n "$_relay_run_help" ] \
  || _die "'docker compose run --help' 이 아무것도 내지 않았다 — 플래그 지원을 판정할 수 없다(docker 가 깨졌거나 권한이 없다). 'docker compose version' 을 먼저 본다" 3
case "$_relay_run_help" in
  *--env-from-file*) ;;
  *) _die "이 docker compose 는 'run --env-from-file' 을 모른다 — 채널 env 가 일회 컨테이너에 닿지 않는다(§13.3). compose 를 올린다" 3 ;;
esac
unset _relay_run_help
# **`jq` 도 같은 문턱을 받는다**(cr R-1). 아래 키 집합 조회는 compose 와 `jq` 두 도구를 지나는데,
# `jq` 가 깨진 것을 「채널 env 가 깨졌다」로 읽으면 운영자가 엉뚱한 파일을 들여다본다. 도구 문턱은
# 도구 자리에 둔다 — 아래 해석 실패는 그때 **채널 env 의 일**만 뜻하게 된다.
command -v jq >/dev/null 2>&1 \
  || _die "jq 가 필요하다 — 채널 env 의 키 집합을 compose 출력에서 뽑는 데 쓴다" 3
printf '{}' | jq -e . >/dev/null 2>&1 \
  || _die "jq 가 가장 단순한 JSON 도 읽지 못한다 — 도구가 깨졌다(채널 env 의 일이 아니다)" 3

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
# **키 집합은 소비자와 같은 해석기로 뽑는다**(v H-1). 앞 판은 줄마다 **첫 `KEY=` 를 sed 로** 뽑았고,
# compose 의 dotenv 해석기는 **따옴표로 닫힌 값 뒤의 둘째 대입을 새 변수로** 읽는다 — 그래서
# `…ROUTEKEY="rk" BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil` 한 줄이 래퍼의 모든 검사를 통과하고
# 컨테이너에는 그 DB 자격 통로가 들어갔다(verifier 실측). 술어가 **소비자와 다른 문법**에 걸려 있으면
# 꼴을 하나씩 막는 처방은 다음 꼴에서 다시 열린다. 그래서 문법을 늘리지 않고 **해석기를 바꾼다**:
# `env_file:` 로 이 파일을 받는 stub 서비스에 `docker compose config` 를 돌려 그 서비스의 environment
# 키 집합을 읽는다.
#
# **두 해석기가 같다는 것은 실측이다**(evidence commands — 정상 · 우회 넷 · 버려짐 셋 · 빈 파일,
# 아홉 입력 전부에서 `config`+`env_file:` 의 키 집합 == `run --env-from-file` 이 컨테이너에 넣는 키 집합).
#
# **값은 어디로도 나가지 않는다** — `config` 출력은 변수로 받아 `jq` 가 **키만** 낸다(argv·화면·로그
# 어디에도 값이 없다). stderr 는 버린다.
# 돌려주는 코드: 0 해석함 · 1 **compose 가 거절했다**(채널 env 의 일) · 2 그 밖의 도구가 어긋났다
# (`mktemp`·`jq` — 채널 env 의 일이 **아니다**, cr R-1). 호출자가 둘을 다른 문면으로 낸다.
# **신호로 끊겨도 임시 자리를 남기지 않는다**(cr R-7 · N-2). 자리는 **부모가 만들고 부모가 치운다** —
# 해석 함수는 명령 치환 `$( )` 안에서 돌므로 **서브셸**이고, 거기서 전역에 적은 자리는 부모에게
# 건너오지 않는다(첫 판이 그래서 신호 뒤에 자리를 남겼다 — 실측). `RETURN` trap 도 서브셸 안에서만
# 도므로 신호 갈래를 덮지 못한다. 그래서 자리를 부모의 변수에 두고 신호 trap 을 해석 **앞에** 건다.
_RELAY_STUB_DIR=""
# `|| true` — 지우지 못한 것이 이 자리의 반환값을 바꾸지 않는다(cr N-4).
_relay_stub_cleanup() {
  [ -z "$_RELAY_STUB_DIR" ] || rm -rf "$_RELAY_STUB_DIR" || true
  _RELAY_STUB_DIR=""
}
_relay_stub_on_signal() {
  local sig="$1"
  trap - TERM INT HUP
  _relay_stub_cleanup
  kill -s "$sig" $$
}
trap '_relay_stub_on_signal TERM' TERM
trap '_relay_stub_on_signal INT' INT
trap '_relay_stub_on_signal HUP' HUP

_relay_parsed_keys() { # <채널 env 절대 경로> <stub 디렉터리 — 부모가 만들어 준다>
  local f="$1" stub="$2" out keys rc=0
  # **경로를 JSON 문자열로 넣는다**(v L-2). 따옴표 없이 넣으면 경로의 ` #` 이 YAML 주석이 되어
  # `config` 가 **다른 파일**을 읽는다 — 그러면 검사한 파일과 컨테이너가 받는 파일이 갈린다(실측).
  {
    printf 'name: bidvector-relay-envcheck\n'
    printf 'services:\n'
    printf '  probe:\n'
    printf '    image: bidvector-relay-envcheck:none\n'
    printf '    env_file:\n'
    printf '      - %s\n' "$(jq -Rn --arg p "$f" '$p')"
  } > "$stub/compose.yaml" || return 2
  # **평소 환경으로 부른다 — 문턱 둘과 같은 env 다.** 한때 이 호출만 `env -i "PATH=$PATH"` 로
  # 좁혔는데(v L-3 처방의 절반), 그것이 `HOME`·`DOCKER_CONFIG` 까지 떨어뜨려 **compose 가 사용자
  # 플러그인으로 깔린 host 에서 매 run 이 「통째로 버렸다」로 끝나게** 했다(cr N-1 실측 — docker 는
  # `HOME` 이 없으면 `/etc/passwd` 로 되돌아가지 않는다). 멀쩡한 자격 파일을 들여다보게 만드는
  # 혼동이고, cr R-1 이 끊으려던 바로 그 축이다.
  #
  # **그 좁힘은 지금 아무것도 막지 않는다** — 위의 「값에 `$` 를 쓰지 않는다」 전제가 **해석보다
  # 먼저** 파일 전수를 보므로 여기 닿는 파일에는 `$` 가 한 글자도 없고, 두 해석기 어느 쪽에서도
  # 보간이 **일어날 수 없다.** 좁힘이 더하는 것은 실패 경로 하나뿐이라 걷었다(cr N-1 처방 ⓐ).
  # 그래서 이 호출은 문턱 둘(`version`·`run --help`)과 **같은 env** 로 돈다 — 「술어와 소비자가
  # 같은 것을 본다」가 경로 축에 이어 env 축에서도 참이다.
  out="$(docker compose -f "$stub/compose.yaml" config --format json 2>/dev/null)" || rc=$?
  [ "$rc" -eq 0 ] || return 1
  keys="$(printf '%s' "$out" | jq -r '.services.probe.environment // {} | keys[]' 2>/dev/null)" || return 2
  printf '%s' "$keys" | sort -u
}
# 주석·빈 줄이 아닌 줄의 수 — **해석기가 파일을 통째로 버렸는가**를 가르는 데만 쓴다(v L-1). 닫히지
# 않은 따옴표·`"v" !!x`·`${X:?m}` 같은 줄이 있으면 compose 는 rc 0 으로 **파일 전체를 무시한다** —
# 그대로 두면 러너가 `RefusedMissingChannelConfig`(1)로 끝나 진단이 엉뚱한 곳을 가리킨다.
_relay_content_lines() { sed -n '/^[[:space:]]*$/d; /^[[:space:]]*#/d; p' "$1" | grep -c '' || true; }

# **경로에도 `$` 를 쓰지 않는다**(v M-1). 경로는 stub YAML 의 **값**으로 들어가고 compose 는 그 값을
# 보간한다 — JSON 인용은 YAML 파싱만 닫고 보간은 닫지 않는다. 그래서 `…/${NOPE:-relay.env}` 는
# `config` 가 이웃을, `run --env-from-file` 이 그 파일을 읽게 만든다(실측). 경로는 운영자가 고르는
# 값이고 runbook 은 고정 경로를 적으므로, 거절이 비용 없는 자리다.
case "$RELAY_ABS" in
  *'$'*) _die "채널 env 의 경로에 '\$' 가 있다 — 그 글자는 compose 가 stub 의 값으로 보간해서, 검사한 파일과 컨테이너가 받는 파일이 갈린다. '\$' 없는 경로에 두고 다시(runbook §13.2 의 자리가 그 꼴이다)" 3 ;;
esac
# **값에도 `$` 를 쓰지 않는다**(v L-3). `config` 와 `run --env-from-file` 은 `${VAR}` 를 **서로 다른
# env** 로 푼다 — `$` 가 한 글자도 없으면 조회가 일어날 수 없어 둘이 같은 파일을 같게 읽는다. 이것은
# 키 집합을 뽑는 문법이 아니라 **좁히는 전제**다(거절만 더할 수 있고 통과를 만들 수 없다). §13.2 의
# 일곱 값 가운데 `$` 가 필요한 것은 없다(§13.2·§13.6 이 그 제약을 적는다).
#
# **값 자리만 본다**(cr N-3) — 주석·빈 줄은 건너뛰고 첫 `=` **뒤**를 본다. 주석에 쓴 `$` 까지 거절하면
# 운영자가 고칠 수 없는 자리를 가리키게 된다(`=` 가 없는 줄은 해석기가 따로 끊는다).
if sed -n '/^[[:space:]]*#/d; s/^[^=]*=//p' "$RELAY_ABS" | grep -q '\$'; then
  _die "채널 env 의 **값**에 '\$' 가 있다($RELAYENVF) — 값의 \${VAR} 는 compose 의 두 통로가 **서로 다른 env** 로 풀어 같은 파일이 다르게 읽힌다(§13.2 의 일곱 값에는 '\$' 가 필요 없다). 그 글자를 지운다(문면은 줄 내용을 옮기지 않는다)" 3
fi
RELAY_KEYS=""; _relay_parse_rc=0
_RELAY_STUB_DIR="$(mktemp -d)" \
  || _die "채널 env 의 키 집합을 뽑는 **도구**가 어긋났다(임시 자리를 만들지 못했다) — 채널 env 의 일이 아니다. TMPDIR 를 본다" 3
RELAY_KEYS="$(_relay_parsed_keys "$RELAY_ABS" "$_RELAY_STUB_DIR")" || _relay_parse_rc=$?
_relay_stub_cleanup
[ "$_relay_parse_rc" -ne 2 ] \
  || _die "채널 env 의 키 집합을 뽑는 **도구**가 어긋났다(stub 쓰기·jq) — 채널 env 의 일이 아니다. 임시 자리와 jq 를 본다" 3
RELAY_CONTENT_N="$(_relay_content_lines "$RELAY_ABS")"
# **읽지 못했거나 키가 0 인데 내용 줄이 있다 = 해석기가 이 파일을 통째로 버렸다.**
# 두 갈래를 한 문면으로 모으는 이유: `config` 는 그런 입력에 **rc 1 로 말하지만**(실측)
# `run --env-from-file` 은 **rc 0 으로 조용히 버린다**(verifier 실측). 소비자 쪽이 조용하므로 여기서
# 크게 말하는 것이 이 검사의 값이다 — 그대로 두면 러너가 `RefusedMissingChannelConfig`(1)로 끝나
# 진단이 엉뚱한 곳을 가리킨다.
if [ "$_relay_parse_rc" -ne 0 ] || { [ -z "$RELAY_KEYS" ] && [ "$RELAY_CONTENT_N" != 0 ]; }; then
  if [ "$RELAY_CONTENT_N" = 0 ]; then
    _die "채널 env 를 compose 해석기로 읽지 못했다($RELAYENVF — 내용 줄이 0 이다) — 판정 불가. 'docker compose version' 과 그 파일의 권한을 본다" 3
  fi
  _die "compose 해석기가 채널 env 를 **통째로 버렸다**($RELAYENVF — 내용 줄 ${RELAY_CONTENT_N} 인데 키가 0 이다). 닫히지 않은 따옴표 · 값 뒤의 '!!' · '\${VAR:?…}' · 'KEY=' 모양이 아닌 줄이 그렇게 만든다. **일회 컨테이너 쪽은 이것을 조용히 버린다** — 그대로 두면 러너가 '설정이 비었다'로 끝나 엉뚱한 곳을 가리킨다(문면은 줄 내용을 옮기지 않는다 — 이 파일은 자격을 담는다)" 3
fi
unset _relay_parse_rc
# **거절 문면은 이름을 전수로 찍지 않는다**(M15/15R, S-7). 여기 보이는 키는 compose 의 dotenv 해석기가
# **이 파일의 바이트에서** 만들어 낸 이름이고(따옴표 뒤의 둘째 대입을 새 변수로 읽는다 — v H-1), 그래서
# 편집 사고가 자격 조각을 키 **이름**으로 만들 수 있다. journal 은 지우기 어려우므로 **환경 변수 이름
# 꼴만** 찍고 나머지는 개수로만 말한다. 거절 자체(3)는 꼴과 무관하게 그대로다.
# 남는 자리: 이름 꼴인 조각(`LEAKED_123456` 따위)은 여전히 찍힌다 — 이름을 전부 가리면 운영자가 무엇을
# 지워야 하는지 모른다(등재된 제한).
stray_raw="$(comm -23 <(printf '%s\n' "$RELAY_KEYS" | grep -v '^$' | sort -u) <(_relay_allowed_names | sort -u))"
if [ -n "${stray_raw//[[:space:]]/}" ]; then
  stray_named="$(printf '%s\n' "$stray_raw" | grep -E '^[A-Z][A-Z0-9_]*$' | tr '\n' ' ' || true)"
  stray_odd="$(printf '%s\n' "$stray_raw" | grep -v '^$' | grep -cvE '^[A-Z][A-Z0-9_]*$' || true)"
  _die "채널 env 에 §13.2 밖의 키가 있다: 이름 꼴 ${stray_named:-(없음) }· 이름 꼴이 아닌 키 ${stray_odd} 개(이름을 찍지 않는다 — 그 이름은 이 파일의 바이트에서 만들어진 것이라 자격 조각일 수 있다) — 이 파일은 일회 컨테이너의 env 를 **덮으므로**, 여기 들어온 이름 하나가 반입 가드를 거치지 않고 compose 가 준 값을 이긴다. §13.2 의 일곱 키만 둔다(키는 compose 자신의 해석기가 뽑은 것이다 — 한 줄에 대입이 둘이면 둘 다 보인다)" 3
fi
unset stray_raw
_relay_env_has() { # <속성 이름>
  local n
  while IFS= read -r n; do
    [ -n "$n" ] || continue
    case $'\n'"${RELAY_KEYS}"$'\n' in *$'\n'"$n"$'\n'*) return 0 ;; esac
  done < <(_relay_env_names "$1")
  return 1
}
for _p in "${_RELAY_REQUIRED_PROPS[@]}"; do
  _relay_env_has "$_p" \
    || _die "채널 env 에 필수 키가 없다(${_p} — $(_relay_env_names "$_p" | tr '\n' ' ')) — §13.2 를 채우거나 --relay-env-file 이 가리키는 파일을 본다. **값이 비었는지는 여기서 보지 않는다**(그 갈래는 러너가 RefusedMissingChannelConfig 로 말한다, §13.6)" 3
done
unset _p

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
  *) echo "notification-relay: 러너가 예상 밖의 종료 코드 ${rc} 를 냈다 — 래퍼가 5·6 을 쓰므로 그대로 통과시키지 않는다. docker run 자신의 실패(125·126·127) · 신호 종료(137·143) · 러너에 새로 생긴 코드가 여기 온다. **7** 로 낸다(SuccessExitStatus 밖이라 경보가 운다 — runbook §13.3 의 표)" >&2; exit 7 ;;
esac
