#!/usr/bin/env bash
# M15/15M — systemd unit 이 실패하면 **Telegram 한 줄**을 보낸다(운영자 결정 2026-11-23).
#
#     ./tools/prod-alert-telegram.sh <unit 이름> [--env-file <경로>]
#
# 왜 있는가: 알림 훑기 타이머(runbook §9)의 실패는 **journal 에만** 남았다. 2026-11-21 의 세 번
# 연달은 실패(반입 트리가 release 커밋이 아니어서 `exit 3`)는 며칠 동안 아무도 보지 못했다 —
# 「사람이 journal 을 본다」는 것은 감시가 아니라 희망이다. 이 스크립트는 systemd `OnFailure=` 가
# 부르는 **한 줄 경보**이고, 그 이상을 하지 않는다(진단은 여전히 journal 이 정본이다).
#
# **이 메시지는 journal 본문을 싣지 않는다.** 싣는 것은 unit 이름 · host 이름 · `Result`/
# `ExecMainStatus` · KST 시각 · 확인 명령 한 줄뿐이다. 훑기 로그에는 공고·계정 식별자가 들어갈 수
# 있고 그것을 외부 채널로 옮기는 자리는 두지 않는다(채널은 「무엇이 깨졌는가」만 안다).
#
# **자격은 argv 에 올리지 않는다.** Bot API 는 자격을 **URL 경로**에 둔다(`/bot<자격>/sendMessage`) —
# 그래서 `curl <url>` 로 부르면 그 값이 `/proc/<pid>/cmdline` 에 올라 같은 host 의 아무나 읽는다.
# 여기서는 URL 을 **설정으로** 넘긴다(`--config <(…)` — 프로세스 치환이라 argv 에 남는 것은
# `/dev/fd/<n>` 이고 값은 파이프 안이다. 파일로 쓰지 않으므로 디스크에도 남지 않는다).
#
# **curl 의 글자는 우리 출력으로 한 자도 건너오지 않는다.** 응답 본문은 `/dev/null` 로 버리고
# (성공 응답은 chat id 를, 실패 응답은 설명을 되비춘다), stderr 는 닫는다. 실패 사유는 **종료 코드**
# 에서 만든다 — 「새는 문자열을 지운다」(redact)는 문자열 술어라 낯선 메시지 하나로 열리지만,
# 「문자열이 애초에 건너오지 않는다」는 구조로 닫힌다(하네스 「게이트 술어는 문자열이 아니라 구조로」).
#
# 거동의 정본은 `tools/prod-alert-check.sh`(가짜 Telegram 서버로 재는 거동 검사)이고, **거는 법**은
# `docs/runbook/m7-7f-production.md` §9.5 다.
#
# 값의 출처는 둘이고 **키마다** 본다: ① `--env-file` 이 가리키는 파일(기본
# `/etc/bidvector-v2/relay.env` — runbook §13.2 의 알림 채널 설정) ② 그 파일에 없거나 파일을 읽지
# 못하면 **환경**(systemd `EnvironmentFile=` 경로 — 파일은 root 0600 으로 두고 systemd 가 읽어
# 넘긴다). 파일은 `source` 로 실행하지 않는다 — `KEY=VALUE` 줄만 읽는다.
#
# 종료 코드: 0 보냈다 · 1 보내지 못했다(curl 실패·2xx 아님) · 2 전제 실패(사용법·unit 이름 모양·
# 값 부재·값 모양·도구 부재).
set -euo pipefail
# 자격이 trace 로 새지 않게 한다 — 부모가 `SHELLOPTS=xtrace` 를 물려줄 수 있다.
set +x

DEFAULT_ENV_FILE=/etc/bidvector-v2/relay.env
DEFAULT_BASE_URL=https://api.telegram.org
CONNECT_TIMEOUT_S=5
MAX_TIME_S=15
KEY_CREDENTIAL=BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL
KEY_CHAT_ID=BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID
KEY_BASE_URL=BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL

_die() { echo "prod-alert-telegram: $1" >&2; exit "${2:-1}"; }

[ "$#" -ge 1 ] || _die "사용법: $0 <unit 이름> [--env-file <경로>]" 2
UNIT="$1"; shift
ENVF="$DEFAULT_ENV_FILE"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) [ "$#" -ge 2 ] || _die "--env-file 값이 없다" 2; ENVF="$2"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done

# unit 이름 모양 — systemd 의 `%n`·`%i` 가 주는 꼴이다. 뒤에서 이 값은 메시지 문면과 `journalctl`
# 안내 줄에 들어가므로, 모양을 먼저 막는다(아래 `_safe` 가 둘째 겹이다).
[[ "$UNIT" =~ ^[A-Za-z0-9][A-Za-z0-9@:._-]{0,127}\.(service|timer|socket|path|mount|target)$ ]] \
  || _die "unit 이름 모양이 아니다 — [A-Za-z0-9@:._-] 128자 이하 · .service·.timer 따위로 끝난다" 2

# env 파일에서 한 키의 값을 읽는다. **파일을 실행하지 않는다** — `KEY=VALUE` 줄만 본다(마지막이
# 이긴다, systemd 와 같다). 값을 찍지 않는다. 파일을 읽지 못하면 빈 값으로 돌아가고 호출자가 환경을 본다.
_env_file_value() {
  local key="$1" line value=""
  [ -r "$ENVF" ] || { printf '%s' ''; return 0; }
  while IFS= read -r line || [ -n "$line" ]; do
    if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] && [ "${BASH_REMATCH[1]}" = "$key" ]; then
      value="${BASH_REMATCH[2]}"
    fi
  done < "$ENVF"
  if [[ "$value" =~ ^\"(.*)\"$ ]] || [[ "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
  printf '%s' "$value"
}

_value_of() {
  local key="$1" value
  value="$(_env_file_value "$key")"
  [ -n "$value" ] || value="${!key-}"
  printf '%s' "$value"
}

# 메시지 문면으로 들어가는 모든 **동적** 값이 지나는 자리. 허용 글자 밖은 `?` 로 바꾸고 길이를 자른다 —
# 설정 문법의 인용·줄바꿈을 값이 만들 수 없게 하는 구조적 겹이다(열거가 아니라 구성).
_safe() {
  local s="${1:0:128}"
  printf '%s' "${s//[^A-Za-z0-9@:._+-]/\?}"
}

CREDENTIAL="$(_value_of "$KEY_CREDENTIAL")"
CHAT_ID="$(_value_of "$KEY_CHAT_ID")"
BASE_URL="$(_value_of "$KEY_BASE_URL")"
[ -n "$BASE_URL" ] || BASE_URL="$DEFAULT_BASE_URL"

[ -n "$CREDENTIAL" ] || _die "${KEY_CREDENTIAL} 이 비어 있다 — ${ENVF} 또는 unit 의 EnvironmentFile 을 본다(runbook §13.2)" 2
[ -n "$CHAT_ID" ] || _die "${KEY_CHAT_ID} 가 비어 있다 — ${ENVF} 또는 unit 의 EnvironmentFile 을 본다(runbook §13.2)" 2
# 모양 거부는 **원문도 길이도 적지 않는다**(`TelegramCredentialLeakTest` 와 같은 규율) — 남는 것은
# 「어느 칸이 어떤 모양을 어겼는가」뿐이고 그것이 runbook §13.6 의 행 키다.
[[ "$CREDENTIAL" =~ ^[0-9]+:[A-Za-z0-9_-]+$ ]] || _die "${KEY_CREDENTIAL} 이 Bot API 모양이 아니다" 2
[[ "$CHAT_ID" =~ ^(-?[0-9]{1,32}|@[A-Za-z0-9_]{5,32})$ ]] || _die "${KEY_CHAT_ID} 가 chat id 모양이 아니다" 2
# 경로를 받지 않는다 — 스킴·호스트·포트까지다. 뒤에서 이 값이 URL 의 앞머리가 되므로 모양이 곧 경계다.
[[ "$BASE_URL" =~ ^https?://[A-Za-z0-9._-]+(:[0-9]{1,5})?$ ]] || _die "${KEY_BASE_URL} 모양이 아니다 — 스킴·호스트·포트까지만 적는다" 2

command -v curl >/dev/null 2>&1 || _die "curl 이 필요하다" 2

# 실패한 unit 의 종료 상태. 읽지 못하면 `unknown` 이다 — 경보가 멈추지 않는다(상태를 모르는 것보다
# 「실패했다」를 늦게 아는 쪽이 나쁘다).
RESULT=unknown
EXIT_STATUS=unknown
if command -v systemctl >/dev/null 2>&1; then
  while IFS= read -r line; do
    case "$line" in
      Result=?*) RESULT="${line#*=}" ;;
      ExecMainStatus=?*) EXIT_STATUS="${line#*=}" ;;
    esac
  done < <(systemctl show -p Result -p ExecMainStatus -- "$UNIT" 2>/dev/null || true)
fi

UNIT_SAFE="$(_safe "$UNIT")"
HOST_SAFE="$(_safe "$(uname -n 2>/dev/null || echo unknown)")"
RESULT_SAFE="$(_safe "$RESULT")"
EXIT_SAFE="$(_safe "$EXIT_STATUS")"
WHEN_SAFE="$(_safe "$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || echo unknown)")"

# 고정 모양의 한 줄짜리 본문. `\n` 은 curl 설정의 인용 값이 푸는 줄바꿈이고, 동적 칸은 전부 `_safe`
# 를 지났으므로 인용을 깨는 글자가 없다.
TEXT="bidvector 운영 경보 — unit 실패"
TEXT="${TEXT}\nunit: ${UNIT_SAFE}"
TEXT="${TEXT}\nhost: ${HOST_SAFE}"
TEXT="${TEXT}\nresult: ${RESULT_SAFE} / exit ${EXIT_SAFE}"
TEXT="${TEXT}\n시각: ${WHEN_SAFE} (KST)"
TEXT="${TEXT}\n확인: journalctl -u ${UNIT_SAFE} -n 50"
TEXT="${TEXT}\n(본문은 journal 에만 있다 — 이 메시지는 로그 줄을 싣지 않는다)"

CONFIG="url = \"${BASE_URL}/bot${CREDENTIAL}/sendMessage\"
data-urlencode = \"chat_id=${CHAT_ID}\"
data-urlencode = \"text=${TEXT}\"
output = \"/dev/null\"
write-out = \"%{http_code}\"
silent
connect-timeout = ${CONNECT_TIMEOUT_S}
max-time = ${MAX_TIME_S}"

set +e
HTTP="$(curl --config <(printf '%s\n' "$CONFIG") 2>/dev/null)"
RC=$?
set -e

_curl_reason() {
  case "$1" in
    6)  echo "이름 해소 실패" ;;
    7)  echo "접속 실패" ;;
    28) echo "시간 초과(connect ${CONNECT_TIMEOUT_S}s · 전체 ${MAX_TIME_S}s)" ;;
    35|60|77) echo "TLS" ;;
    *)  echo "그 밖 — curl 종료 코드 표" ;;
  esac
}

HTTP_SAFE="$(_safe "$HTTP")"
[ "$RC" -eq 0 ] || _die "보내지 못했다 — curl ${RC}: $(_curl_reason "$RC") · 대상 ${BASE_URL}(자격·응답 본문은 적지 않는다)" 1
case "$HTTP_SAFE" in
  2??) ;;
  *) _die "보내지 못했다 — HTTP ${HTTP_SAFE}(응답 본문은 적지 않는다 · 401·403 이면 runbook §13.2 의 두 값을 본다)" 1 ;;
esac
echo "prod-alert-telegram: ${UNIT_SAFE} 실패 경보를 보냈다(HTTP ${HTTP_SAFE})"
