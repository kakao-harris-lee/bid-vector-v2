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
# 글자 범위 술어가 로캘 collation 을 타지 않게 한다(경보 스크립트와 같은 자리).
export LC_ALL=C

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
EXPECTED_CHECKS=49

T="$(mktemp -d)" || _die "임시 디렉터리를 만들지 못했다" 2
SERVER_PID=""
DECOY_PID=""
CURL_DECOY_PID=""
CURL_DECOY_FILE=""
_cleanup() {
  [ -z "$SERVER_PID" ] || kill "$SERVER_PID" 2>/dev/null || true
  [ -z "$DECOY_PID" ] || kill "$DECOY_PID" 2>/dev/null || true
  [ -z "$CURL_DECOY_PID" ] || kill "$CURL_DECOY_PID" 2>/dev/null || true
  [ -z "$CURL_DECOY_FILE" ] || rm -f "$CURL_DECOY_FILE"
  rm -rf "$T"
}
trap _cleanup EXIT

# 가짜 값. 자격은 **Bot API 모양만** 맞춘 값이고 실 자격이 아니다(`TelegramCredentialLeakTest` 의
# 가짜 값과 같은 부류). 이 둘이 아래 모든 단언의 바늘이다.
#
# **바늘은 run 마다 유일하다**(cr M-7). 상수로 두면 같은 host 에서 이 검사를 겹쳐 돌리는 두 레인이
# `/proc` 전수 훑기에서 **서로의 미끼**를 잡아 「argv 에 자격이 있다」로 신고한다 — 가능한 오탐 중
# 가장 놀라게 하는 문면이다(이 저장소는 한 host 에서 레인을 여럿 돌린다). `$$` 를 섞어도 Bot API
# 모양은 그대로다.
FAKE_CREDENTIAL="811122233:FAKEalertFAKEvalue_-${$}x"
FAKE_CHAT_ID="-10098765${$}"

UNIT='bidvector-notification-sweep.service'

# ── 가짜 Telegram 서버 ─────────────────────────────────────────────────────────
# 받은 요청을 **해독해** 한 줄 JSON 으로 적는다(경로 · form 칸). 응답 모양은 호출자가 고른다.
cat > "$T/fake-telegram.py" <<'PY'
import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs

mode, log_path, port_path = sys.argv[1], sys.argv[2], sys.argv[3]
delay = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0

FAIL_BODY = b'{"ok":false,"description":"Forbidden: bot was blocked by the user"}'


def ok_body(form):
    """실제 Telegram 처럼 **받은 chat id 를 되비춘다** — 본문을 버리지 않는 변이가 그 바늘을
    호출자의 출력으로 끌어내므로, 「출력에 chat id 가 없다」단언이 공허해지지 않는다."""
    echoed = {"ok": True, "result": {"message_id": 7, "chat": {"id": form.get("chat_id", "")}}}
    return json.dumps(echoed).encode("utf-8")


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
        body, code = (ok_body(form), 200) if mode == "ok" else (FAIL_BODY, 403)
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


server = HTTPServer(("127.0.0.1", 0), Handler)
with open(port_path + ".tmp", "w", encoding="utf-8") as handle:
    handle.write(str(server.server_address[1]))
os.replace(port_path + ".tmp", port_path)   # 부분 기록이 보이지 않는다(cr L-3)
server.serve_forever()
PY

# ── 정적 술어 — **파일로 쓰는 자리가 없다** ────────────────────────────────────
# 실행 중 관찰로는 못 보는 꼴이 하나 남는다(v R2-1): 고정 경로에 설정을 쓰고 → curl 이 읽고 →
# 종료 전에 지우는 변이. curl 은 설정을 열자마자 닫으므로 0.05초 폴링이 그 순간을 거의 못 잡고,
# 전후 차이는 지워진 파일을 못 본다. 그래서 **거동이 아니라 글자 구조**로 한 겹 더 막는다 —
# 경보 스크립트에 **파일로 쓰는 자리 자체가 없음**을 토큰 수준에서 단언한다.
#
# grep 이 아니라 `shlex` 토큰화다(주석·따옴표 안 문자열은 토큰 하나로 묶이므로 설정 문자열의
# `output = "/dev/null"` 같은 글자가 리다이렉션으로 오독되지 않는다). 술어는 둘이다:
#   ① 출력 리다이렉션(`>`·`>>`)의 대상은 `/dev/null`·`/dev/fd/*`·`&<숫자>` 뿐이다.
#   ② 파일을 만드는 낱말(`mktemp`·`tee`·`touch`·`dd`·`cp`·`mv`·`install`·`truncate`)이 없다.
# 한계는 알려진 제한에 적는다 — 이것은 **이 파일의 글자**에 대한 단언이고 런타임 보증이 아니다.
cat > "$T/no-file-writes.py" <<'PY'
import shlex
import sys

ALLOWED_TARGETS = ("/dev/null", "/dev/stdout", "/dev/stderr")
FORBIDDEN_WORDS = {"mktemp", "tee", "touch", "dd", "cp", "mv", "install", "truncate"}
WORD_BOUNDARY = ";|&()<>"


def strip_comments(source):
    """bash 규칙으로 주석만 걷는다 — **낱말 첫머리의** `#` 부터 줄 끝까지.

    `shlex` 의 기본 `commenters='#'` 은 낱말 **중간**의 `#` 에서도 그 줄의 나머지를 버린다(v R3-1).
    그래서 `n=${#CONFIG}; … > /tmp/x` 한 줄이 술어를 통째로 비껴갔다 — `${#x}`·`${s#…}`·`$#` 는
    흔한 관용구이고 경보 스크립트도 `_trim` 에서 쓴다.

    인용 상태는 셋이다: `'…'` · `"…"` · **`$'…'`**(ANSI-C 인용 — 그 안에서는 `\\` 가 다음 글자를
    이스케이프한다). 셋째를 몰랐던 앞 판은 `$'\\' # '` 의 `\\'` 에서 인용이 닫혔다고 보고 뒤의 ` # `
    를 주석으로 걷어, **살아 있는 꼬리**를 지웠다(v R4-1).

    **이 함수가 틀려도 술어는 느슨해지지 않는다** — 아래가 걷은 보기와 **원문 보기 둘 다**에서 돌기
    때문이다. 걷개의 오독은 검사를 더 엄하게 만들 뿐이다(fail-closed).
    """
    out = []
    quote = ""
    boundary = True
    index = 0
    while index < len(source):
        char = source[index]
        if quote:
            out.append(char)
            if char == "\\" and quote in ('"', "$'") and index + 1 < len(source):
                out.append(source[index + 1])
                index += 2
                continue
            if char == quote[-1]:
                quote = ""
            boundary = False
            index += 1
            continue
        if char == "\\" and index + 1 < len(source):
            out.append(char)
            out.append(source[index + 1])
            boundary = False
            index += 2
            continue
        if char == "$" and index + 1 < len(source) and source[index + 1] in "'\"":
            out.append(char)
            out.append(source[index + 1])
            quote = "$" + source[index + 1] if source[index + 1] == "'" else source[index + 1]
            boundary = False
            index += 2
            continue
        if char in "'\"":
            quote = char
            out.append(char)
            boundary = False
            index += 1
            continue
        if char == "#" and boundary:
            while index < len(source) and source[index] != "\n":
                index += 1
            continue
        out.append(char)
        boundary = char.isspace() or char in WORD_BOUNDARY
        index += 1
    return "".join(out)


def redirection(piece):
    return ">" in piece and set(piece) <= set("<>&|();0123456789-")


def findings_for(source, view):
    """한 보기의 위반 목록. `whitespace_split` 은 낱말을 한 토큰으로 묶고 `punctuation_chars` 는
    `<>&|();` 를 따로 뗀다. `commenters` 는 비운다 — 걷기는 호출자가 정한다."""
    lex = shlex.shlex(source, posix=False, punctuation_chars=True)
    lex.commenters = ""
    lex.whitespace_split = True
    try:
        pieces = list(lex)
    except ValueError as failure:
        # **토큰화 실패도 위반이다**(fail-closed). 닫히지 않은 인용은 사람이 읽을 수 없는 코드이거나
        # 술어를 깨뜨리려는 꼴이고, 둘 다 통과시킬 이유가 없다. 삼키면 「술어가 터져서 초록」이 된다.
        return [view + " 토큰화 실패 — " + str(failure)]
    found = []
    for index, piece in enumerate(pieces):
        if piece in FORBIDDEN_WORDS:
            found.append(view + " 파일을 만드는 낱말: " + piece)
            continue
        if not redirection(piece):
            continue
        target = pieces[index + 1] if index + 1 < len(pieces) else "<EOF>"
        if piece.endswith("&") and (target.isdigit() or target == "-"):
            continue
        if target in ALLOWED_TARGETS or target.startswith("/dev/fd/"):
            continue
        found.append(view + " 출력 리다이렉션 대상: " + piece + " " + target)
    return found


raw = open(sys.argv[1], encoding="utf-8").read()
# **두 보기의 합집합**(v R4-1). 걷개를 한 꼴씩 고쳐 나가는 대신, 걷지 않은 원문에서도 같은 술어를
# 돌린다 — 걷개가 **살아 있는 꼬리를 주석으로 오독**해도 그 꼬리가 원문 보기에 그대로 남는다.
# 대가는 거짓 RED 다: 주석·문자열이 꺾쇠 짝이나 금지 낱말을 담으면 원문 보기가 잡는다. 그래서
# 경보 스크립트의 주석은 꺾쇠 짝을 쓰지 않는다(그 파일 머리에 적었다).
findings = findings_for(strip_comments(raw), "[걷은 보기]") + findings_for(raw, "[원문 보기]")

print(len(findings))
for one in findings:
    print(one)
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
# **임시 파일 축도 같은 창에서 잰다**(v M2 (b)). 헤더가 「디스크에도 남지 않는다」고 주장하므로, 이
# run 의 `TMPDIR` 과 cwd 를 **빈 자리**로 돌리고 창 안·창 뒤에 그 아래 바늘 0 을 단언한다. 설정을
# 임시 파일로 쓰는 변이는 argv 를 비운 채로도 디스크에 자격을 남긴다 — 앞 판이 그 변이를 놓쳤다.
rm -rf "$T/tmpscan" "$T/cwdscan" "$T/tmpctl"
mkdir -p "$T/tmpscan" "$T/cwdscan" "$T/tmpctl"
# **뿌리를 넓힌다**(v F2) — `TMPDIR`·cwd 만 보면 **고정 절대경로**(`/tmp/<이름>`)에 쓰는 변이가 초록으로
# 지나간다(앞 라운드 실측). 공유 임시 자리 셋의 **전후 차이**를 잡아 새로 생긴 것만 바늘로 훑는다.
# `find` 는 읽지 못하는 디렉터리에서 1 을 낸다(남의 자리) — `pipefail` 아래서 그 1 이 검사를 조용히
# 끝내므로 삼킨다. 판정은 아래 훑개와 그 양성 대조가 한다.
command find /tmp /var/tmp /dev/shm -maxdepth 2 2>/dev/null | sort > "$T/shared-before" || true
( cd "$T/cwdscan" && TMPDIR="$T/tmpscan" "$ALERT" "$UNIT" --env-file "$T/relay-slow.env" > "$T/out-slow.txt" 2>&1 ) &
ALERT_PID=$!
# 미끼 — 바늘을 **argv 에** 담은 프로세스. `$0` 자리에 둔다(`sleep` 은 남는 인자를 받지 않는다).
/bin/sh -c 'sleep 3' "$FAKE_CREDENTIAL" &
DECOY_PID=$!
# **거짓 RED 의 음성 대조**(v R2-2): cmdline 에 `curl` 글자가 들고 `/tmp` 아래 일반 파일을 쥔 **남의**
# 프로세스. 아래 fd 축이 이것을 집으면 안 된다 — 같은 host 의 다른 레인이 그런 모양이고, 앞 판은
# 변이 결과를 통째로 오염시켰다.
CURL_DECOY_FILE="/tmp/bv-alert-check-curldecoy-$$-${RANDOM}"
/bin/sh -c 'sleep 3' "curl-decoy" > "$CURL_DECOY_FILE" 2>&1 &
CURL_DECOY_PID=$!

# **창이 닫힐 때까지 멈추지 않는다.** 앞 판은 증인 셋이 모이면 `break` 했고, 그 조건은 호출자가
# 띄우기도 **전에** 남의 `curl` 로 채워질 수 있었다 — 변이 ①(자격을 argv 로 올린 판)이 그 틈으로
# 초록이 됐다(실측). 증인도 「어딘가의 curl」이 아니라 **이 경보 스크립트의 자식 curl** 이다.
ALERT_PIDS=" " CURL_PPIDS=" " seen_decoy=0 leaked_pids="" disk_leak="" FD_CAND=""
for ((i = 0; i < 200; i++)); do
  # 창 **안**의 디스크 — 설정이 임시 파일로 지나가면 여기서 잡힌다(창 뒤에 지워져도 잡는다).
  if command grep -rlF -- "$FAKE_CREDENTIAL" "$T/tmpscan" "$T/cwdscan" >/dev/null 2>&1; then
    disk_leak="창 안"
  fi
  for d in /proc/[0-9]*; do
    pid="${d#/proc/}"
    # 프로세스마다 `tr` 를 포크하지 않는다 — 한 바퀴가 3 초 창보다 길어지면 증인 수집이 빠듯해진다.
    # 읽지 못하는 자리(사라진 프로세스·남의 것)는 건너뛴다. 리다이렉션 오류 줄이 판정 출력에 섞이지
    # 않게 `2>/dev/null` 을 입력 리다이렉션 **앞**에 둔다.
    cmdline_parts=()
    mapfile -d '' -t cmdline_parts 2>/dev/null < "$d/cmdline" || true
    cmd="${cmdline_parts[*]}"
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
        # 그 curl 이 **공유 임시 자리의 파일을 열고 있는가**. 판정은 **부모가 경보 스크립트인 curl**
        # 에만 건다(v R2-2) — 앞 판은 cmdline 에 `curl` 글자가 든 **아무 프로세스**나 봐서, 같은 host
        # 의 다른 셸이 `/tmp` 아래로 출력을 내보내는 동안 변이 결과가 통째로 거짓 RED 가 됐다.
        # 부모는 한 바퀴 안에서 아직 모를 수 있으므로 **후보로 모아 두고** 창이 닫힌 뒤에 거른다.
        for fdl in "$d"/fd/*; do
          tgt="$(readlink "$fdl" 2>/dev/null || true)"
          case "$tgt" in
            "$T"/*) ;;
            /tmp/*|/var/tmp/*|/dev/shm/*) FD_CAND="${FD_CAND}${ppid} ${tgt}"$'\n' ;;
          esac
        done
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
kill "$CURL_DECOY_PID" 2>/dev/null || true
wait "$CURL_DECOY_PID" 2>/dev/null || true
rm -f "$CURL_DECOY_FILE"

# 후보 중 **부모가 경보 스크립트인 것**만 판정에 쓴다.
fd_leak=""
while IFS=' ' read -r cand_ppid cand_tgt; do
  [ -n "$cand_ppid" ] || continue
  case "$ALERT_PIDS" in *" ${cand_ppid} "*) fd_leak="${fd_leak:+${fd_leak} }${cand_tgt}" ;; esac
done <<< "$FD_CAND"
_stop_server

[ "$seen_decoy" = 1 ] && _ok "argv 훑개의 양성 대조 — 미끼의 argv 에서 바늘을 찾았다" || _bad "argv 훑개가 미끼도 못 찾는다(판정 공허)"
[ "$seen_alert" = 1 ] && _ok "훑는 동안 경보 스크립트가 살아 있었다 — 창 증인" || _bad "창 안에서 경보 스크립트를 보지 못했다(판정 공허)"
[ "$seen_curl" = 1 ] && _ok "훑는 동안 이 스크립트의 자식 curl 이 떠 있었다 — 창 증인" || _bad "창 안에서 이 스크립트의 자식 curl 을 보지 못했다(판정 공허)"
[ -z "$leaked_pids" ] && _ok "미끼 밖의 어느 argv 에도 자격이 없다" || _bad "argv 에 자격이 있는 프로세스:${leaked_pids}"

# 디스크 훑개의 **양성 대조** — 같은 훑개가 바늘을 심은 자리에서는 그것을 찾아낸다.
printf '%s\n' "$FAKE_CREDENTIAL" > "$T/tmpctl/planted"
if command grep -rlF -- "$FAKE_CREDENTIAL" "$T/tmpctl" >/dev/null 2>&1; then
  _ok "디스크 훑개의 양성 대조 — 심은 바늘은 찾는다"
else
  _bad "디스크 훑개가 심은 바늘도 못 찾는다(판정 공허)"
fi
# 창 **뒤**도 본다 — 쓰고 지우지 않는 변이는 여기서 잡힌다.
if command grep -rlF -- "$FAKE_CREDENTIAL" "$T/tmpscan" "$T/cwdscan" >/dev/null 2>&1; then
  disk_leak="${disk_leak:+${disk_leak}·}창 뒤"
fi
[ -z "$disk_leak" ] && _ok "TMPDIR·cwd 어디에도 설정이 파일로 남지 않는다" || _bad "자격이 디스크에 있다(${disk_leak})"

# 공유 임시 자리 셋 — 창 **뒤**에 새로 생긴 것만 훑는다(남의 파일을 뒤지지 않는다).
command find /tmp /var/tmp /dev/shm -maxdepth 2 2>/dev/null | sort > "$T/shared-after" || true
_scan_new_shared() { # → 바늘을 담은 새 파일 경로들
  local f hit=""
  while IFS= read -r f; do
    [ -f "$f" ] && [ -r "$f" ] || continue
    case "$f" in "$T"/*) continue ;; esac
    command grep -qF -- "$FAKE_CREDENTIAL" "$f" 2>/dev/null && hit="${hit} ${f}"
  done < <(comm -13 "$T/shared-before" "$T/shared-after")
  printf '%s' "$hit"
}
shared_leak="$(_scan_new_shared)"
[ -z "$shared_leak" ] && _ok "/tmp·/var/tmp·/dev/shm 에 새로 생긴 파일 어디에도 자격이 없다" || _bad "공유 임시 자리에 자격이 있다:${shared_leak}"

# 그 훑개의 **양성 대조** — 같은 자리에 바늘을 심으면 찾아낸다(심자마자 지운다).
planted="/tmp/bv-alert-check-planted-$$-${RANDOM}"
printf '%s\n' "$FAKE_CREDENTIAL" > "$planted"
command find /tmp /var/tmp /dev/shm -maxdepth 2 2>/dev/null | sort > "$T/shared-after" || true
if [ -n "$(_scan_new_shared)" ]; then
  _ok "공유 임시 자리 훑개의 양성 대조 — 심은 바늘은 찾는다"
else
  _bad "공유 임시 자리 훑개가 심은 바늘도 못 찾는다(판정 공허)"
fi
rm -f "$planted"

[ -z "$fd_leak" ] && _ok "창 안에서 **이 스크립트의** curl 이 공유 임시 자리의 파일을 쥐지 않았다(남의 curl 은 세지 않는다)" || _bad "curl 의 fd 가 공유 임시 파일을 쥔다:${fd_leak}"

# **거르기가 일을 했는가**(v R3-4) — 미끼를 후보로 **보았고** 판정에서 **뺐다**. 둘을 함께 재지 않으면
# 「거짓 RED 가 없다」가 「훑개가 아무것도 못 봤다」와 같은 값이 된다.
if ! printf '%s' "$FD_CAND" | command grep -qF -- "$CURL_DECOY_FILE"; then
  _bad "미끼를 후보로도 보지 못했다 — 거짓 RED 부재가 공허하다(훑개가 아무것도 못 봤다)"
elif printf '%s' "$fd_leak" | command grep -qF -- "$CURL_DECOY_FILE"; then
  _bad "미끼를 걸러내지 못했다 — 거르기가 서지 않는다"
else
  _ok "미끼를 후보로 보았고 판정에서 걸러냈다 — 거르기가 비공허하다"
fi
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
if [ "$rc" -eq 2 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "chat id 가 없으면 보내지 않고 거절한다"
else
  _bad "chat id 부재가 전제 실패(2)로 거절되지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

_forget_calls
rc=0; _run_alert "$T/out-badunit.txt" 'sweep; rm -rf /' --env-file "$T/relay-live.env" || rc=$?
if [ "$rc" -eq 2 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "unit 이름 모양이 아니면 네트워크 호출 0 에 거절한다"
else
  _bad "unit 이름 모양 거부가 전제 실패(2)로 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s\n' "$FAKE_CREDENTIAL" > "$T/relay-badurl.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s\n' "$FAKE_CHAT_ID" >> "$T/relay-badurl.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' "${BASE_URL}/..%2f" >> "$T/relay-badurl.env"
_forget_calls
rc=0; _run_alert "$T/out-badurl.txt" "$UNIT" --env-file "$T/relay-badurl.env" || rc=$?
if [ "$rc" -eq 2 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "기준 URL 에 경로가 붙으면 네트워크 호출 0 에 거절한다"
else
  _bad "기준 URL 경로 거부가 전제 실패(2)로 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

# **평문 http 는 루프백만**(cr M-2). Bot API 는 자격을 경로에 싣는다 — 루프백 밖의 `http://` 는
# 이 스크립트가 argv 에서 막은 그 값을 선로에 평문으로 올린다. 대상은 **문서용 예약 대역**
# (RFC 5737 TEST-NET-1)이라 바깥 서비스에 닿지 않는다. 종료 코드를 **2 까지** 본다 — 「0 이 아니다」는
# 거절과 **접속 실패**를 구별하지 못하고, 이 축의 변이(`https` → `https?`)가 바로 그 틈으로 통과한다.
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s\n' "$FAKE_CREDENTIAL" > "$T/relay-plain.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s\n' "$FAKE_CHAT_ID" >> "$T/relay-plain.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' 'http://192.0.2.1:9' >> "$T/relay-plain.env"
_forget_calls
rc=0; _run_alert "$T/out-plain.txt" "$UNIT" --env-file "$T/relay-plain.env" || rc=$?
if [ "$rc" -eq 2 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "루프백 밖의 평문 http 는 전제 실패(2)로 거절한다 — 자격이 선로에 오르지 않는다"
else
  _bad "평문 http 거부가 전제 실패(2)로 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

# **루프백 예외는 리터럴 주소뿐**(cr N-8) — `localhost` 는 `/etc/hosts`·resolver 가 정하므로, 그 이름을
# 받으면 자격을 경로에 싣는 URL 의 평문 예외가 **환경에 달리게** 된다. 거절을 거동으로 잠근다.
_write_env "$T/relay-localhost.env" yes yes
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' 'http://localhost:8080' >> "$T/relay-localhost.env"
_forget_calls
rc=0; _run_alert "$T/out-localhost.txt" "$UNIT" --env-file "$T/relay-localhost.env" || rc=$?
if [ "$rc" -eq 2 ] && [ "$(_call_count)" -eq 0 ]; then
  _ok "평문 예외는 리터럴 루프백뿐 — 이름 localhost 는 전제 실패(2)로 거절한다"
else
  _bad "localhost 평문 거부가 전제 실패(2)로 서지 않았다(종료 ${rc} · 요청 $(_call_count))"
fi

# **끝 `/` 하나는 받아들인다**(cr M-3 · v L1). §13.2 는 이 키를 relay 와 공유하면서 「끝에 `/` 없이」를
# 다른 키에만 적는다 — relay 에서 도는 값으로 경보만 조용히 서면 안 된다.
_write_env "$T/relay-slash.env" yes yes
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s/\n' "$BASE_URL" >> "$T/relay-slash.env"
_forget_calls
rc=0; _run_alert "$T/out-slash.txt" "$UNIT" --env-file "$T/relay-slash.env" || rc=$?
if [ "$rc" -eq 0 ] && [ "$(_call_count)" -eq 1 ]; then
  _ok "기준 URL 의 끝 슬래시 하나는 떼고 보낸다"
else
  _bad "끝 슬래시가 붙은 기준 URL 로 보내지 못했다(종료 ${rc} · 요청 $(_call_count))"
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

# ── ⑧ curl 실패 갈래(rc≠0) — 운영에서 가장 흔한 실패 자리다 ──────────────────
# 닫힌 포트로 한 번 친다(discard 포트 9 — 아무것도 듣지 않는다). 진단을 덧붙이고 싶어지는 자리라
# 바늘 둘의 부재를 여기서 못박는다(v M2 (a) — 앞 판의 실패 갈래는 HTTP 403 하나뿐이었다).
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s\n' "$FAKE_CREDENTIAL" > "$T/relay-closed.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s\n' "$FAKE_CHAT_ID" >> "$T/relay-closed.env"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' 'http://127.0.0.1:9' >> "$T/relay-closed.env"
rc=0; _run_alert "$T/out-closed.txt" "$UNIT" --env-file "$T/relay-closed.env" || rc=$?
if [ "$rc" -ne 0 ] && command grep -q 'curl 7' "$T/out-closed.txt"; then
  _ok "접속 실패는 0 이 아닌 종료 + 종료 코드에서 만든 한 줄"
else
  _bad "curl 실패 갈래가 기대와 다르다(종료 ${rc})"
fi
if command grep -qF -- "$FAKE_CREDENTIAL" "$T/out-closed.txt" || command grep -qF -- "$FAKE_CHAT_ID" "$T/out-closed.txt"; then
  _bad "curl 실패 갈래의 출력에 바늘이 있다"
else
  _ok "curl 실패 갈래의 stdout·stderr 에 자격도 chat id 도 없다"
fi

# ── ⑨ 해석기가 **실행하지 않는다** ────────────────────────────────────────────
# env 파일을 `source` 로 읽는 변이는 값 안의 `$(…)` 를 **돌린다**. 표지 파일의 부재로 잰다.
rm -f "$T/marker-cred" "$T/marker-chat" "$T/marker-url" "$T/marker-src"
{
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=$(touch %s)\n' "$T/marker-cred"
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=$(touch %s)\n' "$T/marker-chat"
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=$(touch %s)\n' "$T/marker-url"
} > "$T/relay-hostile.env"
rc=0; _run_alert "$T/out-hostile.txt" "$UNIT" --env-file "$T/relay-hostile.env" || rc=$?
# 종료 **2** 까지 본다 — 위 네 갈래와 같은 축이다(거절과 접속 실패를 가른다).
if [ "$rc" -eq 2 ] && [ ! -e "$T/marker-cred" ] && [ ! -e "$T/marker-chat" ] && [ ! -e "$T/marker-url" ]; then
  _ok "값 안의 명령 치환이 돌지 않는다 — 해석기는 파일을 실행하지 않는다"
else
  _bad "적대적 값이 실행됐거나 전제 실패(2)로 거절되지 않았다(종료 ${rc})"
fi
# **양성 대조** — 같은 모양을 `source` 하면 표지가 생긴다(부재 단언이 「그 값이 원래 아무것도 안 한다」
# 와 구별된다). 버릴 서브셸에서 한 번만 돌린다.
printf 'BIDVECTOR_X=$(touch %s)\n' "$T/marker-src" > "$T/relay-sourceable.env"
( set +e; . "$T/relay-sourceable.env" ) >/dev/null 2>&1 || true
if [ -e "$T/marker-src" ]; then
  _ok "표지 술어의 양성 대조 — source 하면 그 값이 실제로 돈다"
else
  _bad "source 해도 표지가 생기지 않는다(판정 공허)"
fi

# ── ⑩ `_safe` — 동적 칸이 설정 문법을 깨지 못한다 ────────────────────────────
# `systemctl` 을 PATH 앞에서 가로채 **인용·줄바꿈을 담은** 상태 값을 내게 한다. 검사 환경의 네 출처
# (systemctl·uname·date)가 전부 얌전해서 `_safe` 가 아무 단언도 지나지 않던 자리다.
mkdir -p "$T/bin"
cat > "$T/bin/systemctl" <<'STUB'
#!/bin/sh
printf 'Result=a"b\nc\n'
printf 'ExecMainStatus=1 x\n'
STUB
chmod 755 "$T/bin/systemctl"
_start_server ok
_write_env "$T/relay-safe.env" yes yes
rc=0
set +e
( PATH="$T/bin:$PATH" "$ALERT" "$UNIT" --env-file "$T/relay-safe.env" ) > "$T/out-safe.txt" 2>&1
rc=$?
set -e
_stop_server
if [ "$rc" -eq 0 ] && [ "$(_call_count)" -eq 1 ] && [ "$(_call_field chat_id)" = "$FAKE_CHAT_ID" ]; then
  _ok "적대적 상태 값에도 설정 인용이 깨지지 않는다(호출 1 · chat id 그대로)"
else
  _bad "적대적 상태 값에서 호출이 어긋났다(종료 ${rc} · 요청 $(_call_count))"
fi
safe_text="$(_call_field text)"
case "$safe_text" in *'result: a?b / exit 1?x'*) _ok "허용 밖 글자가 ? 로 바뀌어 실렸다" ;; *) _bad "_safe 치환이 문면에 보이지 않는다" ;; esac
case "$safe_text" in *'a"b'*) _bad "상태 값의 인용 원문이 문면에 그대로 있다" ;; *) _ok "상태 값의 인용 원문이 문면에 없다" ;; esac

# ── ⑪ chat id 의 출력 부재 — 자격과 같은 급의 바늘이다 ────────────────────────
# 성공 응답은 chat id 를 **되비춘다**(가짜 서버도 그렇게 짰다). 그 본문을 출력으로 흘리는 변이가
# 자격 술어의 곁효과로만 붉던 자리를 chat id 자신의 술어로 닫는다.
printf '%s\n' "$FAKE_CHAT_ID" > "$T/control-chat.txt"
if command grep -qF -- "$FAKE_CHAT_ID" "$T/control-chat.txt"; then
  _ok "chat id 훑개의 양성 대조 — 있는 바늘은 찾는다"
else
  _bad "chat id 훑개가 있는 바늘도 못 찾는다(판정 공허)"
fi
chat_leak=""
for f in "$T/out-ok.txt" "$T/out-fail.txt" "$T/out-slow.txt" "$T/out-env.txt" "$T/out-safe.txt"; do
  [ -f "$f" ] || continue
  command grep -qF -- "$FAKE_CHAT_ID" "$f" && chat_leak="${chat_leak} ${f##*/}"
done
[ -z "$chat_leak" ] && _ok "어느 갈래의 stdout·stderr 에도 chat id 가 없다" || _bad "출력에 chat id 가 있다:${chat_leak}"

# ── ⑫ env 파일 해석이 systemd 와 **같은 값**을 낸다 ──────────────────────────
# 이 host 의 `/etc/bidvector-v2/relay.env` 는 `deploy:deploy 0600` 이라 **이 해석기가 운영에서 쓰인다**
# (v M1). 손으로 고치는 파일에서 가장 흔한 artifact 넷을 systemd 와 같은 값으로 읽어야 한다 — 다르면
# 같은 파일이 relay 에서는 돌고 경보에서는 「Bot API 모양이 아니다」로 선다.
_start_server ok
_env_variant_ok() { # <이름> <printf 형식 — %s 둘: 자격 · chat id>
  local name="$1" fmt="$2" rc=0 file="$T/relay-variant.env"
  # shellcheck disable=SC2059
  printf "$fmt" "$FAKE_CREDENTIAL" "$FAKE_CHAT_ID" > "$file"
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BASE_URL=%s\n' "$BASE_URL" >> "$file"
  _forget_calls
  _run_alert "$T/out-variant.txt" "$UNIT" --env-file "$file" || rc=$?
  if [ "$rc" -eq 0 ] && [ "$(_call_count)" -eq 1 ] && [ "$(_call_field chat_id)" = "$FAKE_CHAT_ID" ]; then
    _ok "${name} — systemd 와 같은 값으로 읽는다"
  else
    _bad "${name} 을 다른 값으로 읽었다(종료 ${rc} · 요청 $(_call_count))"
  fi
}
_env_variant_ok "CRLF 줄끝" \
  'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s\r\nBIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s\r\n'
_env_variant_ok "값 뒤 공백" \
  'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=%s   \nBIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=%s  \n'
_env_variant_ok "= 뒤 공백" \
  'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=  %s\nBIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID= %s\n'
_env_variant_ok "따옴표 뒤 CR·공백" \
  'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL="%s" \r\nBIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID="%s"\r\n'
_stop_server

# ── ⑬ 정적 술어 — 경보 스크립트에 파일로 쓰는 자리가 없다 ────────────────────
static_out="$(python3 -I "$T/no-file-writes.py" "$ALERT")"
if [ "$(printf '%s' "$static_out" | head -n1)" = 0 ]; then
  _ok "경보 스크립트에 파일로 쓰는 자리가 없다(출력 리다이렉션은 /dev/null·fd 뿐 · 파일 생성 낱말 0)"
else
  _bad "경보 스크립트에 파일로 쓰는 자리가 있다: $(printf '%s' "$static_out" | tail -n +2 | tr '\n' ' ')"
fi

# **양성 대조** — 같은 술어가 심은 자리를 찾아낸다(없으면 위 단언이 「술어가 아무것도 안 본다」와
# 구별되지 않는다). 버릴 사본에 고정 경로 쓰기를 심는다.
sed 's|^HTTP="$(curl --config.*|CFG=/tmp/x; printf "%s" "$CONFIG" > "$CFG"; HTTP="$(curl --config "$CFG")"|' \
  "$ALERT" > "$T/planted-writer.sh"
if [ "$(python3 -I "$T/no-file-writes.py" "$T/planted-writer.sh" | head -n1)" != 0 ]; then
  _ok "정적 술어의 양성 대조 — 심은 파일 쓰기를 찾는다"
else
  _bad "정적 술어가 심은 파일 쓰기도 못 찾는다(판정 공허)"
fi

# **`#` 관용구를 앞에 둔 쓰기도 보인다**(v R3-1). `shlex` 의 기본 주석 규칙은 낱말 **중간**의 `#` 에서
# 그 줄의 나머지를 버리므로, 그 한 글자로 술어 전체가 열렸다(실측: 그 꼴의 변이가 3/3 초록). 관용구
# 셋을 각각 **같은 줄에서** 쓰기 앞에 두고 전부 보이는지 잰다.
_planted_hash_case() { # <이름> <쓰기 앞에 둘 bash 관용구>
  local name="$1" idiom="$2" file="$T/planted-hash.sh"
  {
    printf '#!/usr/bin/env bash\n'
    printf 'CONFIG=x\n'
    printf '%s CF=/dev/shm/.bvc$$; printf "%%s" "$CONFIG" > "$CF"\n' "$idiom"
  } > "$file"
  if [ "$(python3 -I "$T/no-file-writes.py" "$file" | head -n1)" != 0 ]; then
    _ok "${name} 를 앞에 둔 파일 쓰기도 보인다"
  else
    _bad "${name} 한 글자로 술어가 열린다 — 그 줄의 쓰기를 못 봤다"
  fi
}
_planted_hash_case '${#x}' 'n=${#CONFIG};'
_planted_hash_case '$#' 'n=$#;'
_planted_hash_case 'a#b' 'n=a#b;'

# **ANSI-C 인용으로 주석 걷개를 속이는 꼴**(v R4-1). bash 에서 `$'\' # '` 는 문자열 `' # ` 이고 그
# 뒤의 명령은 **실행된다** — 걷개가 그 줄의 꼬리를 주석으로 지우면 쓰기가 보이지 않는다. 지금은
# 걷개에 `$'…'` 상태가 있고, 그것이 틀리더라도 **원문 보기**가 같은 줄을 본다.
cat > "$T/planted-ansic.sh" <<'PLANT'
#!/usr/bin/env bash
CONFIG=x
x=$'\' # '; CF=/dev/shm/.bvc$$; printf "%s" "$CONFIG" > "$CF"
PLANT
if [ "$(python3 -I "$T/no-file-writes.py" "$T/planted-ansic.sh" | head -n1)" != 0 ]; then
  _ok "ANSI-C 인용으로 걷개를 속인 줄의 파일 쓰기도 보인다"
else
  _bad "ANSI-C 인용 한 꼴로 술어가 열린다 — 그 줄의 쓰기를 못 봤다"
fi


# ── 합계 ──────────────────────────────────────────────────────────────────────
total=$((PASS + FAIL))
[ "$total" -eq "$EXPECTED_CHECKS" ] || _die "검사 수가 ${total} 이다 — 기대 ${EXPECTED_CHECKS}(공허한 전수 금지: 단언을 더하거나 빼면 이 수도 함께 고친다)" 1
[ "$FAIL" -eq 0 ] || _die "어긋남 ${FAIL} 건(통과 ${PASS})" 1
echo "prod-alert-check: ${PASS} 건 전부 통과"
