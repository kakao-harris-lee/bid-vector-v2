#!/usr/bin/env bash
# M15/15M — `tools/prod-alert-telegram.sh` 의 **거동 검사**. 가짜 Telegram 서버(127.0.0.1 의 임의
# 포트)를 띄우고 그 앞에서 경보 스크립트를 실제로 돌린다.
#
#     ./tools/prod-alert-check.sh
#
# **실 Telegram 과 운영 host 에 닿지 않는다.** 자격·chat id 는 이 파일 안의 가짜 값이고, 기준 URL 은
# 가짜 서버를 가리킨다(`…_TELEGRAM_BASE_URL` — 운영 기본값은 `https://api.telegram.org` 다).
#
# 재는 것 — 누출 축이 먼저다(`TelegramCredentialLeakTest` 와 같은 규율: **바늘이 진짜임을 먼저**
# 재고 나서 부재를 잰다. 그러지 않으면 「없다」가 「보내지 않았다」와 구별되지 않는다):
#   ① 양성 대조 — 가짜 서버가 자격을 **경로**에서, chat id 를 **form** 에서 실제로 받는다.
#   ② 문면 모양 — unit 이름·host·result·시각·`journalctl` 안내가 있고 journal 본문은 없다.
#   ③ stdout·stderr 에 자격 원문이 없다(성공·실패 두 갈래). 술어의 양성 대조를 함께 돌린다.
#   ④ **argv 에 자격이 없다** — 느린 서버가 요청을 쥐고 있는 동안 `/proc/*/cmdline` 전수를 훑는다.
#      훑개 자신의 양성 대조(바늘을 argv 에 담은 미끼 프로세스)와 창이 살아 있었다는 증인
#      (경보 스크립트 · `curl --config`)을 함께 요구한다 — 셋이 없으면 「없다」는 공허하다.
#   ⑤ 2xx 아님 → 0 이 아닌 종료 + 가려진 한 줄.
#   ⑥ 키 부재 → 0 이 아닌 종료 · unit 이름 모양 거부 → 네트워크 호출 0 · 기준 URL 모양 거부.
#   ⑦ 환경 경로(systemd `EnvironmentFile=`) — 파일을 읽지 못해도 환경에서 값을 찾으면 보낸다.
#
# 종료 코드: 0 전부 통과 · 1 하나라도 어긋남 · 2 전제 실패(도구 부재 — 판정 불가).
set -euo pipefail
set +x

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

_die() { echo "prod-alert-check: $1" >&2; exit "${2:-1}"; }

for t in python3 curl mktemp tr; do
  command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2
done

ALERT="$REPO_ROOT/tools/prod-alert-telegram.sh"
[ -x "$ALERT" ] || _die "tools/prod-alert-telegram.sh 가 실행 가능하지 않다" 2

PASS=0 FAIL=0
_ok()  { PASS=$((PASS + 1)); echo "  ok   — $1"; }
_bad() { FAIL=$((FAIL + 1)); echo "  BAD  — $1" >&2; }

# 검사 수의 하한이자 상한 — 「전수」가 조용히 줄어드는 것을 막는다(공허한 전수 금지).
EXPECTED_CHECKS=21

T="$(mktemp -d)" || _die "임시 디렉터리를 만들지 못했다" 2
SERVER_PID=""
DECOY_PID=""
_cleanup() {
  [ -z "$SERVER_PID" ] || kill "$SERVER_PID" 2>/dev/null || true
  [ -z "$DECOY_PID" ] || kill "$DECOY_PID" 2>/dev/null || true
  rm -rf "$T"
}
trap _cleanup EXIT

# 가짜 값. 자격은 **Bot API 모양만** 맞춘 상수이고 실 자격이 아니다(`TelegramCredentialLeakTest`
# 의 가짜 값과 같은 부류). 이 둘이 아래 모든 단언의 바늘이다.
FAKE_CREDENTIAL='811122233:FAKEalertFAKEvalueFAKE_-9'
FAKE_CHAT_ID='-1009876543210'
UNIT='bidvector-notification-sweep.service'

# ── 가짜 Telegram 서버 ─────────────────────────────────────────────────────────
# 받은 요청을 **해독해** 한 줄 JSON 으로 적는다(경로 · form 칸). 응답 모양은 호출자가 고른다.
cat > "$T/fake-telegram.py" <<'PY'
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs

mode, log_path, port_path = sys.argv[1], sys.argv[2], sys.argv[3]
delay = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0

OK_BODY = b'{"ok":true,"result":{"message_id":7,"chat":{"id":-1009876543210}}}'
FAIL_BODY = b'{"ok":false,"description":"Forbidden: bot was blocked by the user"}'


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def do_POST(self):  # noqa: N802
        size = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(size).decode("utf-8", "replace")
        form = {k: v[0] for k, v in parse_qs(raw, keep_blank_values=True).items()}
        with open(log_path, "a", encoding="utf-8") as handle:
            handle.write(json.dumps({"path": self.path, "form": form}, ensure_ascii=False) + "\n")
        if delay:
            time.sleep(delay)
        body, code = (OK_BODY, 200) if mode == "ok" else (FAIL_BODY, 403)
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


server = HTTPServer(("127.0.0.1", 0), Handler)
with open(port_path, "w", encoding="utf-8") as handle:
    handle.write(str(server.server_address[1]))
server.serve_forever()
PY

LOG="" BASE_URL=""
_start_server() { # <mode: ok|fail> [지연 초]
  local mode="$1" delay="${2:-0}" port=""
  LOG="$T/calls-$$-${RANDOM}.jsonl"
  : > "$LOG"
  rm -f "$T/port"
  python3 -I "$T/fake-telegram.py" "$mode" "$LOG" "$T/port" "$delay" &
  SERVER_PID=$!
  local i
  for ((i = 0; i < 100; i++)); do
    [ -s "$T/port" ] && port="$(cat "$T/port")" && break
    sleep 0.05
  done
  [ -n "$port" ] || _die "가짜 서버가 뜨지 않았다(포트를 적지 못했다) — 판정 불가" 2
  BASE_URL="http://127.0.0.1:${port}"
}

_stop_server() {
  [ -z "$SERVER_PID" ] || kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
  SERVER_PID=""
}

_write_env() { # <파일> <자격을 넣는가: yes|no> <chat id 를 넣는가: yes|no>
  local file="$1" with_credential="$2" with_chat="$3"
  : > "$file"
  chmod 600 "$file"
  [ "$with_credential" = yes ] && printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s\n' "$FAKE_CREDENTIAL" >> "$file"
  [ "$with_chat" = yes ] && printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s\n' "$FAKE_CHAT_ID" >> "$file"
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' "$BASE_URL" >> "$file"
  return 0
}

# 로그 한 줄의 칸을 꺼낸다 — 값을 **변수로만** 받고 찍지 않는다.
# 갈래마다 **그 갈래가 낸 호출만** 센다 — 로그를 물려주면 「호출 0」이 앞 갈래의 수에 묻힌다.
_forget_calls() { : > "$LOG"; }
_call_count() { python3 -I -c 'import sys;print(sum(1 for line in open(sys.argv[1]) if line.strip()))' "$LOG"; }
_call_field() { # <path|chat_id|text>
  python3 -I -c '
import json, sys
rows = [json.loads(line) for line in open(sys.argv[1]) if line.strip()]
key = sys.argv[2]
row = rows[0]
print(row["path"] if key == "path" else row["form"].get(key, ""))
' "$LOG" "$1"
}

_run_alert() { # <출력 파일> [추가 인자…] — stdout·stderr 를 한 파일에 모으고 종료 코드를 돌려준다
  local out="$1"; shift
  set +e
  "$ALERT" "$@" > "$out" 2>&1
  local rc=$?
  set -e
  return "$rc"
}

echo "prod-alert-check: 가짜 Telegram 서버 앞에서 경보 스크립트의 거동을 잰다"

# ── ① 양성 대조 + ② 문면 모양 + ③ 출력 누출 0 (성공 갈래) ────────────────────────
_start_server ok
_write_env "$T/relay.env" yes yes
rc=0; _run_alert "$T/out-ok.txt" "$UNIT" --env-file "$T/relay.env" || rc=$?
_stop_server

[ "$rc" -eq 0 ] && _ok "2xx 는 종료 코드 0" || _bad "2xx 인데 종료 코드가 ${rc} 다"
[ "$(_call_count)" -eq 1 ] && _ok "요청은 한 번이다" || _bad "요청 수가 1 이 아니다: $(_call_count)"

got_path="$(_call_field path)"
case "$got_path" in
  *"/bot${FAKE_CREDENTIAL}/sendMessage") _ok "가짜 서버가 자격을 경로에서 받았다 — 바늘 양성 대조" ;;
  *) _bad "경로가 기대 모양이 아니다(값은 적지 않는다)" ;;
esac
[ "$(_call_field chat_id)" = "$FAKE_CHAT_ID" ] && _ok "chat id 가 그대로 실렸다" || _bad "chat id 가 다르다"

text="$(_call_field text)"
case "$text" in *"unit: ${UNIT}"*) _ok "문면에 unit 이름이 있다" ;; *) _bad "문면에 unit 이름이 없다" ;; esac
case "$text" in *"journalctl -u ${UNIT} -n 50"*) _ok "문면에 확인 명령이 있다" ;; *) _bad "문면에 확인 명령이 없다" ;; esac
case "$text" in *"host: "*) _ok "문면에 host 칸이 있다" ;; *) _bad "문면에 host 칸이 없다" ;; esac
case "$text" in *"result: "*) _ok "문면에 result 칸이 있다" ;; *) _bad "문면에 result 칸이 없다" ;; esac
case "$text" in *"$FAKE_CREDENTIAL"*) _bad "문면에 자격이 실렸다" ;; *) _ok "문면에 자격이 없다" ;; esac

# 술어의 양성 대조 — 같은 훑개가 **있는** 바늘은 찾아낸다(없으면 아래 부재 단언이 공허하다).
printf '%s\n' "$FAKE_CREDENTIAL" > "$T/control.txt"
if command grep -qF -- "$FAKE_CREDENTIAL" "$T/control.txt"; then
  _ok "출력 훑개의 양성 대조 — 있는 바늘은 찾는다"
else
  _bad "출력 훑개가 있는 바늘도 못 찾는다(판정 공허)"
fi
if command grep -qF -- "$FAKE_CREDENTIAL" "$T/out-ok.txt"; then
  _bad "성공 갈래의 출력에 자격이 있다"
else
  _ok "성공 갈래의 stdout·stderr 에 자격이 없다"
fi

# ── ④ argv 축 — 느린 서버가 요청을 쥐는 동안 /proc 전수 ──────────────────────────
_start_server ok 3
_write_env "$T/relay-slow.env" yes yes
rc=0
( "$ALERT" "$UNIT" --env-file "$T/relay-slow.env" > "$T/out-slow.txt" 2>&1 ) &
ALERT_PID=$!
# 미끼 — 바늘을 **argv 에** 담은 프로세스. `$0` 자리에 둔다(`sleep` 은 남는 인자를 받지 않는다).
/bin/sh -c 'sleep 3' "$FAKE_CREDENTIAL" &
DECOY_PID=$!

# **창이 닫힐 때까지 멈추지 않는다.** 앞 판은 증인 셋이 모이면 `break` 했고, 그 조건은 호출자가
# 띄우기도 **전에** 남의 `curl` 로 채워질 수 있었다 — 변이 ①(자격을 argv 로 올린 판)이 그 틈으로
# 초록이 됐다(실측). 증인도 「어딘가의 curl」이 아니라 **이 경보 스크립트의 자식 curl** 이다.
ALERT_PIDS=" " CURL_PPIDS=" " seen_decoy=0 leaked_pids=""
for ((i = 0; i < 200; i++)); do
  for d in /proc/[0-9]*; do
    pid="${d#/proc/}"
    # 리다이렉션 오류는 `2>/dev/null` 보다 먼저 찍히므로 **순서를 뒤집는다** — 훑는 동안 프로세스가
    # 사라지는 것은 정상이고, 그 줄이 판정 출력에 섞이면 안 된다.
    cmd="$(tr '\0' ' ' 2>/dev/null < "$d/cmdline" || true)"
    [ -n "$cmd" ] || continue
    case "$cmd" in
      *prod-alert-telegram.sh*)
        case "$ALERT_PIDS" in *" ${pid} "*) ;; *) ALERT_PIDS="${ALERT_PIDS}${pid} " ;; esac
        ;;
    esac
    case "$cmd" in
      *curl*)
        ppid="$(sed -n 's/^PPid:[[:space:]]*//p' "$d/status" 2>/dev/null || true)"
        [ -z "$ppid" ] || case "$CURL_PPIDS" in *" ${ppid} "*) ;; *) CURL_PPIDS="${CURL_PPIDS}${ppid} " ;; esac
        ;;
    esac
    case "$cmd" in
      *"$FAKE_CREDENTIAL"*)
        if [ "$pid" = "$DECOY_PID" ]; then
          seen_decoy=1
        else
          case "$leaked_pids" in *" ${pid} "*) ;; *) leaked_pids="${leaked_pids} ${pid} " ;; esac
        fi
        ;;
    esac
  done
  kill -0 "$ALERT_PID" 2>/dev/null || break
  sleep 0.05
done

seen_alert=0; [ "$ALERT_PIDS" = " " ] || seen_alert=1
seen_curl=0
for p in $CURL_PPIDS; do
  case "$ALERT_PIDS" in *" ${p} "*) seen_curl=1 ;; esac
done

wait "$ALERT_PID" || rc=$?
kill "$DECOY_PID" 2>/dev/null || true
wait "$DECOY_PID" 2>/dev/null || true
DECOY_PID=""
_stop_server

[ "$seen_decoy" = 1 ] && _ok "argv 훑개의 양성 대조 — 미끼의 argv 에서 바늘을 찾았다" || _bad "argv 훑개가 미끼도 못 찾는다(판정 공허)"
[ "$seen_alert" = 1 ] && _ok "훑는 동안 경보 스크립트가 살아 있었다 — 창 증인" || _bad "창 안에서 경보 스크립트를 보지 못했다(판정 공허)"
[ "$seen_curl" = 1 ] && _ok "훑는 동안 이 스크립트의 자식 curl 이 떠 있었다 — 창 증인" || _bad "창 안에서 이 스크립트의 자식 curl 을 보지 못했다(판정 공허)"
[ -z "$leaked_pids" ] && _ok "미끼 밖의 어느 argv 에도 자격이 없다" || _bad "argv 에 자격이 있는 프로세스:${leaked_pids}"
[ "$rc" -eq 0 ] && _ok "느린 응답도 끝까지 간다" || _bad "느린 갈래의 종료 코드가 ${rc} 다"

# ── ⑤ 2xx 아님 ────────────────────────────────────────────────────────────────
_start_server fail
_write_env "$T/relay-fail.env" yes yes
rc=0; _run_alert "$T/out-fail.txt" "$UNIT" --env-file "$T/relay-fail.env" || rc=$?
_stop_server
if [ "$rc" -ne 0 ] && command grep -q 'HTTP 403' "$T/out-fail.txt" \
   && ! command grep -qF -- "$FAKE_CREDENTIAL" "$T/out-fail.txt" \
   && ! command grep -q 'blocked by the user' "$T/out-fail.txt"; then
  _ok "403 은 0 이 아닌 종료 + 응답 본문 없는 한 줄"
else
  _bad "403 갈래가 기대와 다르다(종료 ${rc})"
fi

# ── ⑥ 전제 거부 ───────────────────────────────────────────────────────────────
# **설정은 지금 떠 있는 서버를 가리켜야 한다.** 앞 판은 첫 갈래의 env 파일(이미 죽은 포트)을 재사용했고,
# 그래서 「네트워크 호출 0」이 **접속 실패**와 구별되지 않았다 — 변이 ③(unit 이름 검사 제거)이 그 틈으로
# 초록이 됐다(실측). 거절의 사유가 **검사 하나뿐**이게 나머지 전제를 전부 살려 둔다.
_start_server ok
_write_env "$T/relay-live.env" yes yes
_write_env "$T/relay-nochat.env" yes no
_forget_calls
rc=0; _run_alert "$T/out-nochat.txt" "$UNIT" --env-file "$T/relay-nochat.env" || rc=$?
if [ "$rc" -ne 0 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "chat id 가 없으면 보내지 않고 거절한다"
else
  _bad "chat id 부재가 거절되지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

_forget_calls
rc=0; _run_alert "$T/out-badunit.txt" 'sweep; rm -rf /' --env-file "$T/relay-live.env" || rc=$?
if [ "$rc" -ne 0 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "unit 이름 모양이 아니면 네트워크 호출 0 에 거절한다"
else
  _bad "unit 이름 모양 거부가 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s\n' "$FAKE_CREDENTIAL" > "$T/relay-badurl.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s\n' "$FAKE_CHAT_ID" >> "$T/relay-badurl.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' "${BASE_URL}/..%2f" >> "$T/relay-badurl.env"
_forget_calls
rc=0; _run_alert "$T/out-badurl.txt" "$UNIT" --env-file "$T/relay-badurl.env" || rc=$?
if [ "$rc" -ne 0 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "기준 URL 에 경로가 붙으면 네트워크 호출 0 에 거절한다"
else
  _bad "기준 URL 모양 거부가 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

# ── ⑦ 환경 경로 — 파일이 없어도 systemd EnvironmentFile 이 준 값으로 보낸다 ──────
_forget_calls
rc=0
set +e
(
  export BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL="$FAKE_CREDENTIAL"
  export BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID="$FAKE_CHAT_ID"
  export BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL="$BASE_URL"
  "$ALERT" "$UNIT" --env-file "$T/없는-파일.env"
) > "$T/out-env.txt" 2>&1
rc=$?
set -e
_stop_server
if [ "$rc" -eq 0 ] && [ "$(_call_count)" -eq 1 ] && ! command grep -qF -- "$FAKE_CREDENTIAL" "$T/out-env.txt"; then
  _ok "파일을 읽지 못하면 환경에서 값을 찾는다(systemd EnvironmentFile 경로)"
else
  _bad "환경 경로가 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

# ── 합계 ──────────────────────────────────────────────────────────────────────
total=$((PASS + FAIL))
[ "$total" -eq "$EXPECTED_CHECKS" ] || _die "검사 수가 ${total} 이다 — 기대 ${EXPECTED_CHECKS}(공허한 전수 금지: 단언을 더하거나 빼면 이 수도 함께 고친다)" 1
[ "$FAIL" -eq 0 ] || _die "어긋남 ${FAIL} 건(통과 ${PASS})" 1
echo "prod-alert-check: ${PASS} 건 전부 통과"
