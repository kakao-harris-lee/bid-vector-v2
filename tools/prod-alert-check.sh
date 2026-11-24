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
# `shfmt` 는 아래 ⑬ 의 정적 술어가 드는 **구문 파서**다. 없으면 그 술어가 조용히 비는 것이 아니라
# **검사 전체가 판정 불가로 선다** — 「안 돌린 게이트는 아무것도 막지 못한다」. CI 는 `container`
# job 의 `install shfmt` step 이 핀한 판을 깔고, 로컬은 사람이 깐다(evidence 의 설치 한 줄).
command -v shfmt >/dev/null 2>&1 \
  || _die "shfmt 가 PATH 에 없다 — 정적 술어의 구문 파서다(ci.yml 의 install shfmt 와 같은 판을 깐다) · 판정 불가" 2

# 파서의 **판**도 술어의 일부다 — 러너와 로컬이 다른 판을 쓰면 같은 파일에 다른 답이 나온다.
# 숫자는 정책 파일 한 자리에 있고 워크플로와 이 검사가 함께 읽는다(`install buf` 선례).
SHFMT_POLICY="$REPO_ROOT/config/quality/shell-guard-policy.properties"
[ -r "$SHFMT_POLICY" ] || _die "config/quality/shell-guard-policy.properties 를 읽지 못했다 — 판정 불가" 2
# **「정확히 한 줄」을 여기서도 잰다**(r1 code-review L — 앞 판은 CI 의 `_one` 만 재고 로컬은 안 쟀다).
# 키가 두 번 선언되면 값이 두 줄이 되고, 그 뒤의 판 대조가 「판이 다르다」로 서서 진짜 사유(중복 키)를
# 가린다. 같은 값을 읽는 두 자리가 다른 엄격도를 갖는 것 자체가 둘째 축이다.
SHFMT_PIN="$(sed -n 's/^tool\.shfmt\.version=//p' "$SHFMT_POLICY")"
SHFMT_PIN_LINES="$(printf '%s' "$SHFMT_PIN" | command grep -c '' || true)"
[ -n "$SHFMT_PIN" ] && [ "$SHFMT_PIN_LINES" -eq 1 ] \
  || _die "shell-guard-policy.properties 의 tool.shfmt.version 이 없거나 ${SHFMT_PIN_LINES}번 선언됐다(정확히 한 번) — 판정 불가" 2
# 아래 「부재·판 어긋남」 대조가 PATH 를 비운 채 돌므로 해석기 자리를 먼저 절대 경로로 잡아 둔다.
PYBIN="$(command -v python3)"

ALERT="$REPO_ROOT/tools/prod-alert-telegram.sh"
[ -x "$ALERT" ] || _die "tools/prod-alert-telegram.sh 가 실행 가능하지 않다" 2

PASS=0 FAIL=0
_ok()  { PASS=$((PASS + 1)); echo "  ok   — $1"; }
_bad() { FAIL=$((FAIL + 1)); echo "  BAD  — $1" >&2; }

# 검사 수의 하한이자 상한 — 「전수」가 조용히 줄어드는 것을 막는다(공허한 전수 금지).
EXPECTED_CHECKS=87

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
# 전후 차이는 지워진 파일을 못 본다. 그래서 **거동이 아니라 구문 구조**로 한 겹 더 막는다 —
# 경보 스크립트에 **파일로 쓰는 자리 자체가 없음**을 구문 트리 수준에서 단언한다.
#
# **술어는 토큰이 아니라 구문 트리를 센다**(15P — `OPEN-M15-ALERT-STATIC-GUARD-AST`). 15M 은 `shlex`
# 토큰을 두 보기(주석을 걷은 보기 · 원문 보기)의 합집합으로 셌고, 다섯 라운드가 그 층의 우회를 한 꼴씩
# 닫았다. 마지막 하나는 닫히지 않았다(v REG-1): 본문에 `"` 하나만 든 here-doc 두 개 사이에 쓰기 줄을
# 두면 `shlex` 는 그 사이를 큰따옴표 토큰 하나로 묶고, bash 에게 here-doc 본문은 **데이터**라 그 줄은
# 코드로 실행된다. 짝이 맞아 토큰화 실패도 나지 않으니 두 보기가 함께 열렸다. **here-doc 은 문법
# 구조이므로 토큰 단위의 열거로는 닫히지 않는다** — 하네스의 「게이트 술어는 문자열이 아니라 구조로
# 닫는다」가 가리키는 바로 그 자리다.
#
# 그래서 층을 옮긴다. `shfmt --to-json` 이 bash 를 **파싱해** 내놓는 트리를 보고, 판정의 뼈대는 하나다
# — **이 파일을 실측한 census 를 allowlist 로 두고 그 밖이면 위반**이다. 여섯 자리에 같은 규율을 건다:
#   ① **구문 노드 종류**(census 18 종). 목록 밖 종류가 나오면 위반이다. 이것 하나가 `let`·C 꼴 `for`·
#      `(( ))`·`$(( ))`·`Subshell`·`BraceExp`·`ExtGlob`·`UnaryTest` 처럼 **아직 이 파일에 없는 문법
#      전부**를 닫는다 — 「아직 안 본 꼴」마다 가지를 더하는 것이 아니라 한 술어로.
#   ② **쓰기 리다이렉션 대상**(`>`·`>>`·`>|`·`&>`·`&>>`·`>&`·`<>`)은 `/dev/null`·`/dev/fd/<숫자>`·
#      fd 복제뿐이다. 대상이 **상수가 아니면** 위반이다 — 「`$CF` 가 무엇인지 모른다」는 통과 사유가
#      아니다(fail-closed).
#   ③ **명령어 자리**(`CallExpr.Args[0]`)는 census 20 종. 상수가 아니어도, 역슬래시가 섞여도, 목록
#      밖이어도 위반이다. 허용 명령의 **인자**도 census 다(`curl --config` · `command -v` ·
#      `systemctl show` · `printf` 는 플래그 0 …). **접두 대입**도 census(`IFS`·`TZ`, 상수 값)라
#      `PATH=/tmp/x curl …` 가 이름만 빌려 다른 바이너리를 부르지 못한다.
#   ④ **선언**(`DeclClause`)은 `local`·`export` 뿐이고 **옵션 인자를 받지 않는다**(`declare -n`·
#      `local -n` 이 변수 하나로 명령어를 바꾼다).
#   ⑤ **산술 평가 자리**. bash 는 배열 첨자·slice·`[[ -eq ]]` 에서 값을 **산술로 다시 읽고**, 그때
#      값 안의 `$(…)` 가 **돈다**(실측). 그래서 `Assign.Index`·`ParamExp.Index`·`ParamExp.Slice` 는
#      **상수 ASCII 숫자**만 받고, `[[ … ]]` 의 이항 연산자는 census(`=~`)만 받는다. **`[` 쪽은 제약
#      하지 않는다** — `[ "$x" -eq 0 ]` 은 산술 평가를 하지 **않는다**(실측). 재지 않고 좁히면 그
#      파일이 쓰는 `[ "$#" -ge 1 ]` 넷이 거짓 RED 가 된다.
#   ⑥ **확장 연산자 `@`**(`${x@P}`)는 값을 프롬프트 문자열로 다시 해석해 그 안의 `$(…)` 를 돌린다.
# 파싱 실패도 위반이고 **사유를 따로 적는다** — 양성 대조가 「술어가 잡았다」와 「파싱이 깨졌다」를
# 가를 수 있어야 한다.
#
# **열거를 allowlist 로 뒤집은 것이 r1·r2 의 수정이다**(v H-1·M-1 · cr HIGH·N-1). 첫 판의 낱말 칸은
# 금지 집합과 문자열이 같은지만 봐서 `/usr/bin/tee`·`\tee`·`c=tee; "$c"`·`declare -n`·`trap`·`source`·
# `/bin/bash -c`·`sort -o` 가 전부 지나갔고(열일곱 꼴), 둘째 판의 산술 검사는 `ArithmCmd`/`ArithmExp`
# **두 노드의 denylist** 라 `let`·`a[x]=1`·`${a[x]}`·`${y:x}`·`[[ -eq ]]`·`printf -v`·`read "a[$x]"` 가
# 지나갔다(일곱 꼴). 같은 실패를 두 번 했으므로 **모든 자리를 census 로 통일**했다.
#
# 새 명령·새 문법·새 플래그를 들이는 편집은 이 목록들을 함께 고쳐야 지나간다 — 하네스의 「우회하려면
# 게이트 자체를 바꿔야 하는 층」이 뜻하는 바가 그것이고, 두 자리 등재의 비용은 알고 치른다.
#
# **이 층에서 사라지는 것들**: 주석 걷개 · 원문 보기 합집합 · `$'…'` 상태 · `${#x}`·`$#`·`a#b` 특례가
# 전부 필요 없어진다(파서가 주석과 인용을 정본으로 안다). 15M 이 그 합집합의 대가로 치르던 거짓 RED —
# 주석 속 `a > b`·`->`·꺾쇠 짝(v REG-2) — 도 사라진다.
#
# 한계는 알려진 제한에 적는다 — 이것은 **이 파일의 구조**에 대한 단언이고 런타임 보증이 아니다.
cat > "$T/no-file-writes.py" <<'PY'
import json
import shutil
import subprocess
import sys

# 쓰기로 **여는** 연산자. 입력(`<`·`<<`·`<<-`·`<<<`)은 파일을 만들지 않으므로 보지 않는다 —
# here-doc 본문은 bash 에게도 파서에게도 데이터다. `<>` 는 읽기·쓰기로 열고 없으면 만든다.
# zsh 전용 쓰기 연산자(`>>|`·`&>|`·`&>>|`)는 **여기 없어도 된다** — `-ln bash` 에서 파싱이 실패하고
# 파싱 실패는 아래에서 위반이다(실측: 「`>>|` redirects are a zsh feature」로 비-0).
WRITE_OPS = {">", ">>", ">|", "&>", "&>>", ">&", "<>"}
ALLOWED_TARGET = "/dev/null"
FD_PREFIX = "/dev/fd/"

# ── census allowlist 넷 — 전부 이 파일을 **실측해서** 만들었다 ───────────────────
# ① 구문 노드 종류. 목록 밖 종류가 나오면 위반이다. 이것 하나가 `let`·C 꼴 `for`·`(( ))`·`$(( ))`·
#    `Subshell`·`BraceExp`·`ExtGlob`·`UnaryTest` 처럼 **이 파일에 아직 없는 문법 전부**를 닫는다.
ALLOWED_NODE_TYPES = {
    "File", "FuncDecl", "Block", "IfClause", "WhileClause", "CaseClause",
    "BinaryCmd", "CallExpr", "DeclClause", "TestClause", "BinaryTest",
    "Word", "Lit", "SglQuoted", "DblQuoted", "ParamExp", "CmdSubst", "ProcSubst",
}
# ② 명령어 자리(`CallExpr.Args[0]`) — 상수 20 종 · 비상수 0.
ALLOWED_COMMANDS = {
    "[", "echo", "exit", "printf", "read", "return", "set", "shift", "true",
    "command", "curl", "date", "systemctl", "uname",
    "_curl_reason", "_die", "_env_file_value", "_safe", "_trim", "_value_of",
}
# ③ 선언. `declare`·`typeset`·`readonly` 는 없다 — `declare -n`(nameref)이 변수 하나로 명령어를 바꾼다.
ALLOWED_DECL_VARIANTS = {"local", "export"}
# ④ `[[ … ]]` 의 이항 연산자. 이 파일은 `=~` 뿐이다 — 산술 평가하는 `-eq`·`-lt` 따위가 닫힌다
#    (실측: `[[ $x -eq 0 ]]` 는 값 안의 `$(…)` 를 **돌린다**. 반면 `[ "$x" -eq 0 ]` 는 돌지 않는다 —
#    그래서 `[` 쪽에는 피연산자 제약을 두지 않는다).
ALLOWED_TEST_OPS = {"=~"}
# 접두 대입(`IFS= read …` · `TZ=… date …`) — 이름과 **상수 값**까지 census 다. 이것이 없으면
# `PATH=/tmp/x curl …` 가 allowlist 안의 이름으로 **다른 바이너리**를 부른다.
ALLOWED_PREFIX_ASSIGNS = {"IFS", "TZ"}
# 허용 명령의 인자. **상수이면서 `-` 로 시작하는** 인자만 본다(열거가 아니라 census — 빈 집합은
# 「이 명령은 플래그를 쓰지 않는다」는 실측이다). 이 파일이 정의하는 함수 여섯은 여기 없다 —
# 그 본문은 같은 술어가 전수로 보고, 인자는 메시지 문자열이라 `-` 로 시작하는 것도 있다.
ARG_ALLOWED_FLAGS = {
    "[": {"-n", "-r", "-eq", "-ge", "-gt"},
    "command": {"-v"},
    "curl": {"--config"},
    "date": set(),
    "echo": set(),
    "exit": set(),
    "printf": set(),          # `printf -v "a[$x]"` 가 첨자를 산술 평가한다(실측)
    "read": {"-r"},           # `read -r "a[$x]"` 도 같다(실측)
    "return": set(),
    "set": {"-e", "-euo"},
    "shift": set(),
    "systemctl": {"-p", "--"},
    "true": set(),
    "uname": {"-n"},
}
REQUIRED_FIRST_FLAG = {"command": "-v", "systemctl": "show"}
# `read` 가 값을 넣는 자리는 **평범한 이름**이어야 한다 — `a[$x]` 꼴이면 첨자가 산술 평가된다.
NAME_ARG_COMMANDS = {"read"}
SHFMT_TIMEOUT_S = 30


def ascii_digits(text):
    """`str.isdigit()` 는 `'²'`·`'٣'` 에도 참이다 — fd 번호·첨자는 ASCII 숫자뿐이다."""
    return bool(text) and text.isascii() and text.isdigit()


def plain_name(text):
    return bool(text) and text.isascii() and (text[0].isalpha() or text[0] == "_") \
        and all(c.isalnum() or c == "_" for c in text)


def literal(word):
    """확장이 하나도 없는 Word 의 **상수 값**. 하나라도 들면 None(= 상수가 아니다).

    `$'…'`(ANSI-C 인용)은 상수가 아니다 — `Value` 가 원문이라 그 글자와 실행되는 값이 다르다.
    """
    if not isinstance(word, dict):
        return None
    pieces = []
    for part in word.get("Parts") or []:
        kind = part.get("Type")
        if kind == "Lit":
            pieces.append(part.get("Value") or "")
        elif kind == "SglQuoted":
            if part.get("Dollar"):
                return None
            pieces.append(part.get("Value") or "")
        elif kind == "DblQuoted":
            inner = literal(part)
            if inner is None:
                return None
            pieces.append(inner)
        else:
            return None
    return "".join(pieces)


def redirect_findings(node):
    found = []
    for red in node.get("Redirs") or []:
        op = red.get("Op") or ""
        if op not in WRITE_OPS:
            continue
        target = literal(red.get("Word"))
        if op == ">&" and target is not None and (ascii_digits(target) or target == "-"):
            continue
        if target == ALLOWED_TARGET:
            continue
        if target is not None and target.startswith(FD_PREFIX) and ascii_digits(target[len(FD_PREFIX):]):
            continue
        found.append("쓰기 리다이렉션 대상: " + op + " " + (target if target is not None else "(상수가 아니다)"))
    return found


def call_findings(node):
    """명령어 자리는 allowlist · 접두 대입은 이름과 상수 값까지 · 허용 명령은 인자까지."""
    found = []
    for assign in node.get("Assigns") or []:
        if not (node.get("Args") or []):
            continue                      # 명령 없는 평범한 대입 — 접두가 아니다
        name = (assign.get("Name") or {}).get("Value") or "(이름 없음)"
        if name not in ALLOWED_PREFIX_ASSIGNS:
            found.append("census 밖의 접두 대입: " + name)
        elif assign.get("Value") is not None and literal(assign.get("Value")) is None:
            found.append("접두 대입 " + name + " 의 값이 상수가 아니다")
    args = node.get("Args") or []
    if not args:
        return found
    name = literal(args[0])
    if name is None:
        return found + ["명령어가 상수가 아니다 — 무엇이 도는지 모른다"]
    if "\\" in name:
        found.append("명령어에 역슬래시가 있다: " + name)
        return found
    if name not in ALLOWED_COMMANDS:
        found.append("allowlist 밖의 명령어: " + name)
        return found
    required = REQUIRED_FIRST_FLAG.get(name)
    if required is not None and literal(args[1] if len(args) > 1 else None) != required:
        found.append("명령어 " + name + " 는 " + required + " 꼴만 허용한다")
    allowed_flags = ARG_ALLOWED_FLAGS.get(name)
    if allowed_flags is not None:
        for arg in args[1:]:
            value = literal(arg)
            if value is not None and value.startswith("-") and value not in allowed_flags:
                found.append("명령어 " + name + " 의 허용 밖 인자: " + value)
    if name in NAME_ARG_COMMANDS:
        for arg in args[1:]:
            value = literal(arg)
            if value is None:
                found.append("명령어 " + name + " 의 변수 이름이 상수가 아니다")
            elif not value.startswith("-") and not plain_name(value):
                found.append("명령어 " + name + " 의 변수 이름이 평범한 이름이 아니다: " + value)
    return found


def decl_findings(node):
    """`local`·`export` 뿐이고 **옵션 인자를 받지 않는다**. 옵션은 `Name` 없는 Arg 로 온다."""
    variant = (node.get("Variant") or {}).get("Value") or "(이름 없음)"
    if variant not in ALLOWED_DECL_VARIANTS:
        return ["allowlist 밖의 선언: " + variant]
    found = []
    for arg in node.get("Args") or []:
        if not arg.get("Name"):
            found.append("선언 " + variant + " 의 옵션 인자: " + (literal(arg.get("Value")) or "(상수가 아니다)"))
    return found


def subscript_findings(node, kind):
    """첨자·slice 는 **상수 ASCII 숫자**만 받는다 — 그 자리는 bash 의 산술 문맥이고, 변수의 값 안에
    든 `$(…)` 가 거기서 돈다(실측: `a[x]=1` · `${a[x]}` · `${y:x}` 셋 다 돈다)."""
    found = []
    index = node.get("Index")
    if index is not None and not ascii_digits(literal(index) or ""):
        found.append("첨자가 상수 숫자가 아니다 — 산술 평가 자리다")
    if kind == "ParamExp":
        piece = node.get("Slice")
        if piece is not None:
            for label, word in (("offset", piece.get("Offset")), ("length", piece.get("Length"))):
                if word is not None and not ascii_digits(literal(word) or ""):
                    found.append("slice 의 " + label + " 이 상수 숫자가 아니다 — 산술 평가 자리다")
    return found


def walk(node, found):
    """트리 전수. 명령 치환·here-doc 본문 안의 치환도 그 안에 **노드로** 있으므로 같은 술어가 닿는다."""
    if isinstance(node, dict):
        kind = node.get("Type")
        if kind is not None and kind not in ALLOWED_NODE_TYPES:
            found.append("census 밖의 구문 노드: " + kind)
        if isinstance(node.get("Redirs"), list):
            found.extend(redirect_findings(node))
        if kind == "CallExpr":
            found.extend(call_findings(node))
        elif kind == "DeclClause":
            found.extend(decl_findings(node))
        elif kind == "BinaryTest":
            if node.get("Op") not in ALLOWED_TEST_OPS:
                found.append("census 밖의 test 연산자: " + str(node.get("Op")))
        elif kind == "ParamExp":
            if (node.get("Exp") or {}).get("Op") == "@":
                # `${x@P}` 는 값을 프롬프트 문자열로 **다시 해석**해 그 안의 `$(…)` 를 돌린다.
                found.append("확장 연산자 @ — 값을 코드로 되돌린다")
            found.extend(subscript_findings(node, "ParamExp"))
        # Assign 은 Type 이 없다 — 첨자(`a[x]=1`)는 같은 산술 문맥이다.
        if kind is None and isinstance(node.get("Name"), dict) and node.get("Index") is not None:
            found.extend(subscript_findings(node, "Assign"))
        for value in node.values():
            walk(value, found)
    elif isinstance(node, list):
        for item in node:
            walk(item, found)


def _unavailable(reason):
    """**판정 불가(2)** — 「위반 0」과 구별된다. 삼키면 「파서가 없어서 초록」이 된다."""
    sys.stderr.write("no-file-writes: " + reason + " — 판정 불가\n")
    raise SystemExit(2)


def _run(argv, stdin_bytes=None):
    try:
        return subprocess.run(argv, input=stdin_bytes, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, timeout=SHFMT_TIMEOUT_S)
    except (OSError, subprocess.SubprocessError) as failure:
        _unavailable("shfmt 를 돌리지 못했다: " + str(failure))


target_path, pinned_version = sys.argv[1], sys.argv[2]
shfmt = shutil.which("shfmt")
if shfmt is None:
    _unavailable("shfmt 가 PATH 에 없다")
probe = _run([shfmt, "--version"])
found_version = probe.stdout.decode("utf-8", "replace").strip()
if probe.returncode != 0 or found_version != "v" + pinned_version:
    _unavailable("shfmt 판이 핀과 다르다: " + (found_version or "(판을 읽지 못했다)") + " != v" + pinned_version)

with open(target_path, "rb") as handle:
    source = handle.read()
done = _run([shfmt, "-ln", "bash", "--to-json"], source)
if done.returncode != 0:
    # 구문을 읽지 못하는 것은 통과가 아니다(fail-closed). **사유를 따로 적는다** — 양성 대조가
    # 「술어가 잡았다」와 「파싱이 깨졌다」를 가를 수 있어야 한다.
    detail = (done.stderr.decode("utf-8", "replace").strip() or "사유 없음").splitlines()[0]
    print(1)
    print("bash 구문을 읽지 못했다: " + detail)
    raise SystemExit(0)
try:
    tree = json.loads(done.stdout.decode("utf-8"))
except ValueError as failure:
    _unavailable("shfmt 가 낸 것이 JSON 이 아니다: " + str(failure))

findings = []
walk(tree, findings)
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
_static() { # <파일> → 위반 수(첫 줄) + 위반 목록
  python3 -I "$T/no-file-writes.py" "$1" "$SHFMT_PIN"
}
static_out="$(_static "$ALERT")"
if [ "$(printf '%s' "$static_out" | head -n1)" = 0 ]; then
  _ok "경보 스크립트에 파일로 쓰는 자리가 없다(census allowlist 여섯 자리 전부 통과)"
else
  _bad "경보 스크립트에 파일로 쓰는 자리가 있다: $(printf '%s' "$static_out" | tail -n +2 | tr '\n' ' ')"
fi

# **붉음은 「0 이 아님」이 아니라 「그 사유로 0 보다 큼」이다**(r1 cr MEDIUM · r2 cr N-2·N-5).
# 셋을 함께 본다 — ① 종료 코드 0(판정 불가·예외가 아니다) ② 첫 줄이 0 보다 큰 정수 ③ **기대한 사유가
# 위반 목록에 있다**. ③ 이 없으면 대조가 **다른 가지 때문에** 붉어도 초록이고(가지 격리), 씨앗이
# 파싱되지 않아 붉어도 초록이다(파싱 실패는 사유가 다르다). 이 slice 의 미덕이 「위반 0」과 「판정
# 불가」를 가른 것인데 대조만 예외일 수 없다.
_assert_red() { # <이름> <파일> <기대 사유 조각>
  local out n rc=0
  out="$(_static "$2")" || rc=$?
  n="$(printf '%s' "$out" | head -n1)"
  case "$n" in '' | *[!0-9]*) n=-1 ;; esac
  if [ "$rc" -ne 0 ] || [ "$n" -le 0 ]; then
    _bad "$1 — 붉지 않거나 판정 불가다(종료 ${rc} · 첫 줄 '${n}')"
  elif ! printf '%s' "$out" | tail -n +2 | command grep -qF -- "$3"; then
    _bad "$1 — 붉지만 사유가 다르다(기대 '$3' · 실제 '$(printf '%s' "$out" | tail -n +2 | tr '\n' ' ')')"
  else
    _ok "$1 — 그 가지가 잡는다"
  fi
}
_planted_case() { # <이름> <기대 사유 조각> <심을 줄…>
  local name="$1" reason="$2"; shift 2
  local file="$T/planted-case.sh"
  {
    printf '#!/usr/bin/env bash\n'
    printf 'CONFIG=x\n'
    printf '%s\n' "$@"
  } > "$file"
  _assert_red "$name" "$file" "$reason"
}

# **양성 대조** — 같은 술어가 심은 자리를 찾아낸다(없으면 위 단언이 「술어가 아무것도 안 본다」와
# 구별되지 않는다). 버릴 사본에 고정 경로 쓰기를 심는다. 이 꼴이 ⑬ 이 있는 **이유**다(v R2-1):
# 고정 경로에 설정을 쓰고 → curl 이 읽고 → 종료 전에 지우는 변이는 런타임 관찰이 거의 못 잡는다.
sed 's|^HTTP="$(curl --config.*|CFG=/tmp/x; printf "%s" "$CONFIG" > "$CFG"; HTTP="$(curl --config "$CFG")"|' \
  "$ALERT" > "$T/planted-writer.sh"
_assert_red '정적 술어의 양성 대조 — 심은 고정 경로 쓰기' "$T/planted-writer.sh" '쓰기 리다이렉션 대상'

# **15M 의 다섯 라운드가 찾은 우회를 전부 다시 잰다**(15P). 층을 옮기면 앞 층이 한 꼴씩 닫아 온 것이
# 조용히 열릴 수 있다 — 그래서 양성 대조 모집단은 **옛 술어가 붉게 만든 변이 전부 + 그 술어가 끝내
# 못 잡은 하나(REG-1)**로 시작한다. 각 변이는 `bash -n` 이 통과하고 실제로 디스크에 쓴다.
# `#` 관용구(v R3-1) — `shlex` 의 주석 규칙이 낱말 **중간**의 `#` 에서 줄의 나머지를 버려 열리던 셋.
# 파서는 `#` 의 뜻을 정본으로 알므로 특례가 필요 없다.
_planted_case '${#x} 관용구' '쓰기 리다이렉션 대상' 'n=${#CONFIG}; CF=/dev/shm/.bvc$$; printf "%s" "$CONFIG" > "$CF"'
_planted_case '$# 관용구' '쓰기 리다이렉션 대상' 'n=$#; CF=/dev/shm/.bvc$$; printf "%s" "$CONFIG" > "$CF"'
_planted_case 'a#b 관용구' '쓰기 리다이렉션 대상' 'n=a#b; CF=/dev/shm/.bvc$$; printf "%s" "$CONFIG" > "$CF"'
# ANSI-C 인용(v R4-1) — bash 에서 `$'\' # '` 는 문자열 `' # ` 이고 그 뒤의 명령은 **실행된다**.
cat > "$T/planted-ansic.sh" <<'PLANT'
#!/usr/bin/env bash
CONFIG=x
x=$'\' # '; CF=/dev/shm/.bvc$$; printf "%s" "$CONFIG" > "$CF"
PLANT
_assert_red 'ANSI-C 인용' "$T/planted-ansic.sh" '쓰기 리다이렉션 대상'
# **REG-1 — 15M 이 끝내 못 잡은 꼴**(`OPEN-M15-ALERT-STATIC-GUARD-AST` 가 선 자리). 본문에 `"` 하나만
# 든 here-doc 두 개 사이에 쓰기 줄을 두면 `shlex` 는 그 사이를 큰따옴표 토큰 하나로 묶었다. 파서에게
# here-doc 본문은 데이터이고 그 사이의 줄은 **코드**다 — 실측으로 `/dev/shm` 에 쓰이던 그 변이다.
_planted_case 'REG-1 here-doc 짝' '쓰기 리다이렉션 대상' \
  'cat <<A >/dev/null' '"' 'A' \
  'CF=/dev/shm/.bvc$$; printf %s $CONFIG > $CF' \
  'cat <<B >/dev/null' '"' 'B'
# 파일을 만드는 낱말 — 리다이렉션을 하나도 쓰지 않는 갈래다.
_planted_case 'tee 로 쓰기' 'allowlist 밖의 명령어: tee' 'printf "%s" "$CONFIG" | tee /dev/shm/.bvc$$ >/dev/null'
_planted_case 'mktemp 로 쓰기' 'allowlist 밖의 명령어: mktemp' 'CF="$(mktemp)"; printf "%s" "$CONFIG" >"$CF"'
# **간접 실행** — 파서에게 따옴표 안의 프로그램은 데이터라 그 리다이렉션은 트리에 없다. 낱말 층이 받는다.
cat > "$T/planted-eval.sh" <<'PLANT'
#!/usr/bin/env bash
CONFIG=x
eval 'printf "%s" "$CONFIG" > /dev/shm/.bvc$$'
PLANT
_assert_red 'eval 로 쓰기' "$T/planted-eval.sh" 'allowlist 밖의 명령어: eval'

# **r1 verifier 가 찾은 열일곱 꼴**(v H-1 · cr HIGH). 앞 판의 denylist 는 금지 낱말과 문자열이 **같은
# 지만** 봐서 형식을 지킨 변이에 전부 초록이었다. 열일곱 모두 `bash -n` 이 통과하고 게이트 밖에서
# 실제로 디스크 항목을 만든다(실측). 이 열일곱이 붉은 것은 allowlist 한 술어의 결과다.
_planted_case '역슬래시 명령어 \tee' '명령어에 역슬래시가 있다' 'printf "%s" "$CONFIG" | \tee /dev/shm/.bvc$$ >/dev/null'
_planted_case '낱말 속 역슬래시 te\e' '명령어에 역슬래시가 있다' 'printf "%s" "$CONFIG" | te\e /dev/shm/.bvc$$ >/dev/null'
_planted_case '경로 한정 /usr/bin/tee' 'allowlist 밖의 명령어: /usr/bin/tee' 'printf "%s" "$CONFIG" | /usr/bin/tee /dev/shm/.bvc$$ >/dev/null'
_planted_case '비상수 명령어 "$c"' '명령어가 상수가 아니다' 'c=tee; printf "%s" "$CONFIG" | "$c" /dev/shm/.bvc$$ >/dev/null'
_planted_case 'nameref declare -n' 'allowlist 밖의 선언: declare' 'declare -n r=y; y=tee; printf "%s" "$CONFIG" | $r /dev/shm/.bvc$$ >/dev/null'
_planted_case 'alias 로 이름 바꾸기' 'allowlist 밖의 명령어: shopt' 'shopt -s expand_aliases' 'alias t=tee' 'printf "%s" "$CONFIG" | t /dev/shm/.bvc$$ >/dev/null'
_planted_case '/bin/bash -c' 'allowlist 밖의 명령어: /bin/bash' '/bin/bash -c '"'"'printf "%s" "$CONFIG" > /dev/shm/.bvc$$'"'"''
_planted_case 'dash -c' 'allowlist 밖의 명령어: dash' 'dash -c '"'"'printf x > /dev/shm/.bvc$$'"'"''
_planted_case 'trap 본문' 'allowlist 밖의 명령어: trap' 'trap '"'"'printf x > /dev/shm/.bvc$$'"'"' EXIT'
_planted_case 'source /dev/stdin' 'allowlist 밖의 명령어: source' 'source /dev/stdin <<<'"'"'printf x > /dev/shm/.bvc$$'"'"''
_planted_case '. /dev/stdin here-doc' 'allowlist 밖의 명령어: .' '. /dev/stdin <<X' 'printf x > /dev/shm/.bvc$$' 'X'
_planted_case 'sort -o' 'allowlist 밖의 명령어: sort' 'printf "%s" "$CONFIG" | sort -o /dev/shm/.bvc$$'
_planted_case 'ln -s' 'allowlist 밖의 명령어: ln' 'ln -s /etc/hostname /dev/shm/.bvc$$'
_planted_case 'history -w' 'allowlist 밖의 명령어: history' 'set -o history; history -s a; history -w /dev/shm/.bvc$$'
# 허용 명령의 **쓰기 인자**(v M-2) — `curl` 은 이 파일이 쓰는 도구라 목록에서 뺄 수 없다. 인자를 좁힌다.
_planted_case 'curl -o' '명령어 curl 의 허용 밖 인자: -o' 'curl -o /dev/shm/.bvc$$ file:///etc/hostname'
# `command` 는 `-v` 꼴만 허용한다 — 그 제약이 없으면 한 낱말로 allowlist 를 우회한다.
_planted_case 'command tee' '명령어 command 는 -v 꼴만 허용한다' 'printf "%s" "$CONFIG" | command tee /dev/shm/.bvc$$ >/dev/null'

# **r2 verifier·code-reviewer 가 찾은 산술 문맥 일곱 + 노드 종류 둘**(v M-1 · cr N-1). bash 는 배열
# 첨자·slice·`[[ -eq ]]`·`printf -v`·`read <이름>`·`[ -v ]` 에서 값을 **산술로 다시 읽고**, 그때 값
# 안의 `$(…)` 가 돈다 — 실측으로 전부 파일이 생긴다. 앞 판은 `ArithmCmd`/`ArithmExp` 두 노드만 봐서
# 이 일곱이 트리에 **노드 없이** 지나갔다. 씨앗은 전부 「값에 코드 문자열을 담고 그 자리에 넣는」 꼴이다.
_ARITH_SEED='x='"'"'a[$(printf x > /dev/shm/.bvc$$)]'"'"';'
_planted_case '산술 첨자 (( x ))' 'census 밖의 구문 노드: ArithmCmd' "$_ARITH_SEED"' (( x )) || true'
_planted_case 'let x' 'census 밖의 구문 노드: LetClause' "$_ARITH_SEED"' let x || true'
_planted_case 'C 꼴 for' 'census 밖의 구문 노드: CStyleLoop' "$_ARITH_SEED"' for ((i = x; 0; )); do :; done'
_planted_case '대입 첨자 a[x]=1' '첨자가 상수 숫자가 아니다' "$_ARITH_SEED"' a[x]=1'
_planted_case '확장 첨자 ${a[x]}' '첨자가 상수 숫자가 아니다' "$_ARITH_SEED"' echo "${a[x]}" >/dev/null'
_planted_case 'slice offset ${y:x}' 'slice 의 offset 이 상수 숫자가 아니다' "$_ARITH_SEED"' y=abc; echo "${y:x}" >/dev/null'
_planted_case '[[ $x -eq 0 ]]' 'census 밖의 test 연산자: -eq' "$_ARITH_SEED"' [[ $x -eq 0 ]] || true'
_planted_case 'printf -v "a[$x]"' '명령어 printf 의 허용 밖 인자: -v' "$_ARITH_SEED"' printf -v "a[$x]" %s v'
_planted_case 'read -r "a[$x]"' '명령어 read 의 변수 이름이 상수가 아니다' "$_ARITH_SEED"' read -r "a[$x]" <<<v'
_planted_case '[ -v "a[$x]" ]' '명령어 [ 의 허용 밖 인자: -v' "$_ARITH_SEED"' [ -v "a[$x]" ] || true'

# **가지 격리 대조 넷**(cr N-2·N-3·N-4·N-6). 위 씨앗들과 달리 이 넷은 **쓰기 변이가 아니라 가지
# 대조**다 — 그 가지를 지우면 검사가 초록이 되는지를 재는 것이 목적이고, 그래서 다른 가지가 함께
# 붉어지지 않게 씨앗을 깎았다(기대 사유 단언이 그것을 강제한다). `systemctl enable` 은 **돌리지
# 않는다** — 이 host 의 `/etc/systemd/system` 에 링크를 만드는 명령이고, 술어는 글자만 본다.
_planted_case '확장 연산자 ${x@P}' '확장 연산자 @' 'x='"'"'$(printf x > /dev/shm/.bvc$$)'"'"'; printf "%s" "${x@P}" >/dev/null'
_planted_case '선언 옵션 local -n' '선언 local 의 옵션 인자: -n' 'local -n r=y; printf "%s" "$r" >/dev/null'
_planted_case 'systemctl enable' '명령어 systemctl 는 show 꼴만 허용한다' 'systemctl enable x'
_planted_case '접두 대입 PATH=' 'census 밖의 접두 대입: PATH' 'PATH=/tmp/x curl --config /dev/null'
_planted_case '접두 대입 SSLKEYLOGFILE=' 'census 밖의 접두 대입: SSLKEYLOGFILE' 'SSLKEYLOGFILE=/dev/shm/.bvc$$ curl --config /dev/null'

# **음성 대조 — 거짓 RED 가 사라졌다.** 15M 의 원문 보기는 주석 속 `>`·꺾쇠 짝(v REG-2)을 리다이렉션
# 으로 셌고, 그 대가로 경보 스크립트의 주석에 꺾쇠 짝 금지라는 규약을 물렸다. 파서는 주석을 리다이렉션
# 과 가른다. 이 단언이 없으면 그 규약을 거둔 근거가 문면뿐이다. **`(( a > b ))`·`[[ b > a ]]` 는 여기
# 없다** — 리다이렉션으로 오독되지는 않지만 census 가 둘 다 위반으로 세기 때문이고(알려진 제한 7),
# 앞의 산술 대조가 그 꼴을 든다.
cat > "$T/negative-noise.sh" <<'PLANT'
#!/usr/bin/env bash
# 주석에 a > b 와 -> 와 <꺾쇠 짝> 을 쓴다
a=2 b=1
printf '%s' "output = \"/dev/null\"" >/dev/null
echo "$a" >&2
PLANT
if [ "$(_static "$T/negative-noise.sh" | head -n1)" = 0 ]; then
  _ok "주석의 꺾쇠 짝·문자열 속 설정 지시는 리다이렉션이 아니다 — 거짓 RED 0"
else
  _bad "거짓 RED 가 남아 있다: $(_static "$T/negative-noise.sh" | tail -n +2 | tr '\n' ' ')"
fi

# **파서가 없거나 판이 다르면 「위반 0」이 아니라 판정 불가(2)다.** 이 둘이 없으면 술어의 fail-closed
# 가 문면뿐이고, 러너에서 설치가 조용히 깨지는 날 검사가 초록으로 지나간다.
mkdir -p "$T/noshfmt" "$T/badver"
rc=0; ( PATH="$T/noshfmt" "$PYBIN" -I "$T/no-file-writes.py" "$ALERT" "$SHFMT_PIN" ) > "$T/out-noshfmt.txt" 2>&1 || rc=$?
if [ "$rc" -eq 2 ] && command grep -q '판정 불가' "$T/out-noshfmt.txt"; then
  _ok "shfmt 가 없으면 판정 불가(2)로 선다 — 위반 0 과 구별된다"
else
  _bad "shfmt 부재가 판정 불가(2)로 서지 않았다(종료 ${rc})"
fi
printf '#!/bin/sh\necho v0.0.0-not-the-pin\n' > "$T/badver/shfmt"
chmod 755 "$T/badver/shfmt"
rc=0; ( PATH="$T/badver" "$PYBIN" -I "$T/no-file-writes.py" "$ALERT" "$SHFMT_PIN" ) > "$T/out-badver.txt" 2>&1 || rc=$?
if [ "$rc" -eq 2 ] && command grep -q '판정 불가' "$T/out-badver.txt"; then
  _ok "shfmt 판이 핀과 다르면 판정 불가(2)로 선다 — 다른 파서의 답을 측정으로 세지 않는다"
else
  _bad "shfmt 판 어긋남이 판정 불가(2)로 서지 않았다(종료 ${rc})"
fi

# ── 합계 ──────────────────────────────────────────────────────────────────────
total=$((PASS + FAIL))
[ "$total" -eq "$EXPECTED_CHECKS" ] || _die "검사 수가 ${total} 이다 — 기대 ${EXPECTED_CHECKS}(공허한 전수 금지: 단언을 더하거나 빼면 이 수도 함께 고친다)" 1
[ "$FAIL" -eq 0 ] || _die "어긋남 ${FAIL} 건(통과 ${PASS})" 1
echo "prod-alert-check: ${PASS} 건 전부 통과"
