#!/usr/bin/env bash
# M2/2D S-6 — 교차 언어 socket 스모크. Python fake servicer(2B·2C)를 localhost 에 실제
# TCP 소켓으로 띄우고, Kotlin 생성 client(`CrossLangSmokeTest`)로 한 번씩 부른다 —
# "요청만으로 Python 이 DB 조회 없이 계산 가능"의 형태 증명(D-2D-3 (a), 상시 게이트 아님).
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
ADDRESS="127.0.0.1:50099"
PYTHON_BIN="$REPO_ROOT/ml-engine/.venv/bin/python"
SERVER_SCRIPT="$REPO_ROOT/ml-engine/tests/crosslang_smoke_server.py"
SERVER_LOG=$(mktemp "${TMPDIR:-/tmp}/bidvector-crosslang-server-XXXXXX.log")

if [[ ! -x "$PYTHON_BIN" ]]; then
    echo "Python venv 가 없다 — $PYTHON_BIN (ml-engine/.venv 를 먼저 준비하라)" >&2
    exit 2
fi

SERVER_PID=""
cleanup() {
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill "$SERVER_PID" 2>/dev/null
        wait "$SERVER_PID" 2>/dev/null
    fi
    rm -f "$SERVER_LOG"
}
trap cleanup EXIT

echo "== Python 서버 기동 ($ADDRESS) =="
"$PYTHON_BIN" "$SERVER_SCRIPT" "$ADDRESS" >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

READY=0
for _ in $(seq 1 50); do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Python 서버가 기동 중 종료됐다 — 로그:" >&2
        cat "$SERVER_LOG" >&2
        exit 2
    fi
    if grep -q "^READY " "$SERVER_LOG" 2>/dev/null; then
        READY=1
        break
    fi
    sleep 0.1
done

if [[ "$READY" -ne 1 ]]; then
    echo "Python 서버가 시간 안에 준비되지 않았다 — 로그:" >&2
    cat "$SERVER_LOG" >&2
    exit 2
fi

echo "== Kotlin client 로 2B·2C 한 번씩 호출 =="
cd "$REPO_ROOT"
# verifier r1 F-2 — build cache 가 이 task 의 이전 실행 결과를 복원하면 소켓을 한 번도 열지
# 않고도 exit 0 이 난다(실측). `--rerun-tasks --no-build-cache` 로 매 실행이 실제 socket
# 왕복이게 한다 — "로컬 실측 1회"의 증거가 이 실행 자체여야 한다(D-2D-3 (a)).
./gradlew --offline --rerun-tasks --no-build-cache :adapters:crossLangSmokeTest \
    "-Pbidvector.crosslang.address=$ADDRESS"
GRADLE_EXIT=$?

if [[ "$GRADLE_EXIT" -eq 0 ]]; then
    echo "== 교차 언어 socket 스모크 통과 =="
else
    echo "== 교차 언어 socket 스모크 실패(exit=$GRADLE_EXIT) — Python 서버 로그 =="
    cat "$SERVER_LOG" >&2
fi

exit "$GRADLE_EXIT"
