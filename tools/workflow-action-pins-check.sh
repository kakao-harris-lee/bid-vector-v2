#!/usr/bin/env bash
# M15/15S(D-15S-2) — 워크플로의 `uses:` 가 **전부 commit SHA 로 핀돼 있는가**를 잰다.
#
# 왜 있는가: 떠 있는 major 태그(`actions/checkout@v5`)는 상류에서 **움직일 수 있다**. 2026-03-19
# 공급망 사고(GHSA-69fq-xp46-6x23)에서 먼저 무너진 것이 바로 액션 태그였고, 이 저장소는 그 사고를
# 근거로 trivy·shfmt·buf 를 액션이 아니라 릴리스 바이너리로 받으면서도 **자기가 쓰는 액션은 태그로**
# 두고 있었다. 액션은 게이트보다 **먼저** 돌므로(checkout·툴체인), 태그가 무너지면 게이트가 재기 전에
# 작업 트리가 바뀌고 게이트는 심어진 내용을 보증한다.
#
# 막는 것은 **태그 이동**이다. 핀한 그 커밋이 이미 나쁜 경우는 막지 못한다 — 핀은 「바뀌지 않음」이지
# 「검토됨」이 아니다. 이 게이트 자신이 checkout 뒤에 도는 스크립트이므로 **저자에 대한 방어도 아니다**
# (핀을 태그로 되돌리면서 기대 파일까지 함께 고치는 PR 은 통과한다 — 그 줄은 리뷰가 본다). 이것은
# 실수의 래칫이다. 진짜 강제 층은 저장소 설정의 「full-length SHA 로 핀한 액션만 허용」이다.
#
# **파서 선택**: 선례 `tools/workflow-step-refs-check.sh`(계약 D-7F-7)와 같다 — 이 검사도 `container`
# job 에서 돌고 그 job 이 설치하는 것은 Java·Gradle 뿐이다. 새 의존 0(awk·bash)으로 가되, **읽지 못하는
# 표기는 통과가 아니라 정지(exit 2)** 로 둔다. 열거가 아니라 구성이다.
#
# 술어:
#   · 수집 — 디렉터리 아래 모든 `*.yml`·`*.yaml` 의 `uses` 키 값 **전부**(step 자리·job 자리 양쪽).
#     블록 스칼라 본문은 세지 않는다. 따옴표는 벗긴다. `uses` 글자가 보이는데 읽을 수 있는 두 꼴이
#     아니면 **exit 2**(흐름 매핑 · 여러 줄 값 · 식). 앵커·별칭도 exit 2.
#   · 꼴 — ⓐ `./…`(저장소 안) ⓑ `owner/repo[/path]@<40자리 소문자 16진>` ⓒ `docker://…@sha256:<64hex>`.
#     그 밖(태그·브랜치·짧은 SHA·대문자 16진)은 실패.
#   · 등식 — 수집한 `(액션, SHA)` **집합** == 기대 파일의 집합(양방향). 한 액션이 두 SHA 로 쓰이면 실패.
#   · 판 — `uses` 줄의 꼬리 주석 `# vX…` == 기대 파일의 그 액션의 판 칸. 주석이 없으면 실패.
#     **이 한 항목만 텍스트 술어다**(주석은 YAML 에 보이지 않는다). SHA↔판 대응의 진위는 오프라인에서
#     재지 못한다 — 리뷰가 조회로 잰다.
#   · 비공허 — 수집 건수 ≥ 1 이고 건수를 출력한다. 「오탐 0 인 판이 양성도 0」을 막는다.
#   · 복합 액션 디렉터리 — 워크플로 디렉터리의 형제 `actions/` 가 생기면 **실패**다. 이 검사가 보지
#     않는 자리(`action.yml` 안의 `uses`)가 생겼다는 뜻이고, 조용히 넘기지 않는다.
#   · 자기 변이 대조 — 임시 사본에 변이 여섯을 **바꿔치우기**로 넣고 각각 붉어지는지를 매 실행 잰다.
#     변이마다 사본이 원본과 **실제로 달라졌는지** 먼저 확인한다(더하기만 한 변이는 초록이 된다).
#
# 이 호스트의 `grep` 은 ugrep 래퍼라 스크립트 안에서 쓰지 않는다 — 전부 awk 와 셸 내장이다.
# 정렬·비교는 `LC_ALL=C` 로 고정한다.
#
# 실행:
#
#     ./tools/workflow-action-pins-check.sh .github/workflows
#     ./tools/workflow-action-pins-check.sh <디렉터리> <기대 파일>   # 자기 변이 대조가 쓰는 꼴
set -euo pipefail
export LC_ALL=C

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COUNT_MODE=0
if [ "${1:-}" = "--count" ]; then COUNT_MODE=1; shift; fi

DIR="${1:-}"
# 후행 슬래시 정규화(r1 cr medium-3) — 벗기지 않으면 사본 안의 상대 경로 산출이 어긋나 **판정이
# 아닌 사고**로 exit 1 이 났다. 종료 코드 계약은 `0 통과 · 1 위반 · 2 판정 불가`다.
while [ -n "$DIR" ] && [ "${DIR%/}" != "$DIR" ] && [ -n "${DIR%/}" ]; do DIR="${DIR%/}"; done
PINS="${2:-$REPO_ROOT/config/quality/workflow-action-pins.txt}"
SELFCHECK="${WORKFLOW_ACTION_PINS_SELFCHECK:-1}"

[ -n "$DIR" ] || { echo "사용법: $0 [--count] <워크플로 디렉터리> [기대 파일]" >&2; exit 2; }
[ -d "$DIR" ] || { echo "워크플로 디렉터리가 없다: $DIR" >&2; exit 2; }
[ "$COUNT_MODE" = "1" ] || [ -f "$PINS" ] || { echo "기대 파일이 없다: $PINS" >&2; exit 2; }

# 자기 변이 대조 스위치는 **열거로 닫는다**(r1 cr medium-2) — 읽지 못하는 값에서 조용히 꺼지면
# 게이트의 자기 건전성 측정만 사라지고 CI 는 초록이다. 끌 때도 한 줄 찍는다.
case "$SELFCHECK" in
  0 | 1) ;;
  *) echo "WORKFLOW_ACTION_PINS_SELFCHECK 가 0·1 이 아니다: $SELFCHECK" >&2; exit 2 ;;
esac

AWK_SCAN='
function trim(s) { sub(/^[ \t]+/, "", s); sub(/[ \t]+$/, "", s); return s }
function comment_at(s,   i, c, q) {
  q = ""
  for (i = 1; i <= length(s); i++) {
    c = substr(s, i, 1)
    if (q != "") { if (c == q) q = "" }
    else if (c == "\"" || c == "\047") { q = c }
    else if (c == "#" && (i == 1 || substr(s, i - 1, 1) ~ /[ \t]/)) { return i }
  }
  return 0
}
function unsup(why) { printf "UNSUPPORTED\t%s\t%d\t%s\n", FILENAME, FNR, why; bad = 1 }
function take_use(v, cmt,   q, i, val, rest) {
  v = trim(v)
  if (v == "" || v ~ /^[|>]/) { unsup("uses 값이 비었거나 블록 스칼라다 — 여러 줄로 쪼갠 값을 읽지 못한다"); return }
  if (v ~ /\$\{\{/) { unsup("uses 값이 식(${{ }})이다 — 정적으로 읽을 수 없다"); return }
  q = substr(v, 1, 1)
  if (q == "\"" || q == "\047") {
    i = index(substr(v, 2), q)
    if (i == 0) { unsup("uses 값의 따옴표가 닫히지 않았다"); return }
    val = substr(v, 2, i - 1)
    rest = trim(substr(v, i + 2))
    if (rest != "") { unsup("uses 값 뒤에 읽지 못하는 텍스트가 있다"); return }
  } else {
    if (v ~ /[ \t]/) { unsup("uses 값에 공백이 있다 — 읽지 못하는 표기"); return }
    val = v
  }
  nuses++
  printf "USE\t%s\t%d\t%s\t%s\n", FILENAME, FNR, val, cmt
}
FNR == 1 { inblock = 0 }
{
  raw = $0
  sub(/\r$/, "", raw)      # CRLF — 기대 파일 reader 와 **대칭**으로 벗긴다(r1 cr low: 벗기지
                           # 않으면 「v5.1.0 != v5.1.0」 같은 자기모순 진단이 난다)
  ci = comment_at(raw)
  code = (ci > 0) ? substr(raw, 1, ci - 1) : raw
  cmt = (ci > 0) ? trim(substr(raw, ci)) : ""
  match(code, /^[ \t]*/); ws = RLENGTH      # 블록 본문 비교용 — 들여쓰기 문자 수
  match(code, /^ */); ind = RLENGTH         # 열 계산용 — 공백만(아래에서 탭을 거부한다)
  body = trim(code)
  blank = (body == "")
}
# ── 블록 스칼라 본문 ────────────────────────────────────────────────────────
# **기준은 대시 자리가 아니라 키의 열이다**(r1 verifier H-1 ①). `- if: >-` 에서 대시는 6열이고
# 같은 step 의 형제 키 `uses:` 는 8열이다. 앞 판은 `block_indent` 에 대시 열(6)을 넣어 8열의
# 형제 키를 **본문으로 삼켰고**, 그 줄의 `uses` 가 수집에서 조용히 빠졌다(초록).
inblock {
  if (blank || ws > block_indent) { next }
  inblock = 0
}
# YAML 은 들여쓰기에 탭을 금지한다. 블록 본문 **밖**에서 선두 탭이 보이면 열 계산의 전제가
# 깨지므로 통과가 아니라 정지다.
code ~ /^ *\t/ { unsup("들여쓰기에 탭 — 열 계산의 전제가 깨진다"); next }
code ~ /(^|[ \t[{,])[&*][A-Za-z0-9_-]+([ \t\]},]|$)/ {
  unsup("YAML 앵커·별칭 — 별칭이 가리키는 값을 이 검사가 풀지 못한다"); next
}
{
  # 키 자리를 연다 — 중첩 목록(`- - key:`)까지 대시를 벗기고 **키가 시작하는 열**을 센다.
  rest = body
  keycol = ind
  while (match(rest, /^-[ \t]+/)) { keycol += RLENGTH; rest = substr(rest, RLENGTH + 1) }

  # 읽지 못하는 키 표기는 전부 정지다(r1 verifier H-1 ③). 따옴표 친 키·복합 키는 앞 판에서
  # 「`uses` 키가 아님」으로 보여 unsupported 로도 가지 않았다.
  # 따옴표 뒤에 **콜론이 오는 것만** 키로 본다(r1 cr low). 앞 판은 따옴표로 시작하면 키인지
  # 값인지 가리지 않고 멈춰서, `- "src/**"` 같은 **평범한 시퀀스 값** 하나가 게이트를 판정
  # 불가로 세우고 사실과 다른 사유를 찍었다. 좁혀도 안전한 이유가 이 라운드에 생겼다 —
  # 수집 누락은 parity 등식이 따로 받는다. `- "a: b"`(콜론을 품은 값)도 바르게 지나간다.
  if (rest ~ /^"[^"]*"[ \t]*:/ || rest ~ /^\047[^\047]*\047[ \t]*:/) {
    unsup("따옴표 친 키 — 키 이름을 이 검사가 풀지 못한다"); next
  }
  if (rest == "?" || rest ~ /^\?[ \t]/) { unsup("복합 키(? …) — 이 검사가 읽지 못하는 표기"); next }
  if (rest ~ /^[|>][-+0-9]*$/) { unsup("홀로 선 블록 스칼라 지시자 — 앞 줄의 키와 짝을 잇지 못한다"); next }

  if (rest ~ /(^|[^A-Za-z0-9_-])uses[ \t]*:/) {
    if (rest ~ /^uses[ \t]*:/) { v = rest; sub(/^uses[ \t]*:/, "", v); take_use(v, cmt) }
    else { unsup("uses: 키가 읽을 수 있는 자리에 있지 않다 — 흐름 매핑·중첩·한 줄 run 본문") }
  }

  # 블록 스칼라 시작. 지시자 앞에 **공백을 요구한다**(r1 verifier H-1 ②) — `name: x:|` 처럼
  # 콜론 뒤 공백 없는 일반 스칼라를 블록으로 오인하던 자리다. 첫 콜론에만 건다.
  if (rest ~ /^[^:]*:[ \t]+[|>][-+0-9]*$/) { inblock = 1; block_indent = keycol }
}
END { printf "COUNT\t%d\t%d\n", nuses, bad }
'

AWK_JUDGE='
function hex(s, n) { return (length(s) == n && s ~ /^[0-9a-f]+$/) }
function pinnable(s) { return (hex(s, 40) || (substr(s, 1, 7) == "sha256:" && hex(substr(s, 8), 64))) }
function viol(code, msg) { printf "%s\t%s\n", code, msg; nviol++ }
BEGIN {
  FS = "\t"
  while ((getline line < pins) > 0) {
    sub(/\r$/, "", line)
    sub(/^[ \t]+/, "", line); sub(/[ \t]+$/, "", line)
    if (line == "" || substr(line, 1, 1) == "#") { continue }
    n = split(line, f, /[ \t]+/)
    if (n != 3)                 { viol("POLICY", "기대 파일의 칸이 셋이 아니다: " line); continue }
    if (!pinnable(f[2]))        { viol("POLICY", "기대 파일의 핀이 40자리 소문자 16진도 sha256:<64hex> 도 아니다: " line); continue }
    if (f[3] !~ /^v[0-9]/)      { viol("POLICY", "기대 파일의 판이 vN 꼴이 아니다: " line); continue }
    if (f[1] in pinsha)         { viol("POLICY", "기대 파일에 액션이 두 번 등재됐다: " f[1]); continue }
    pinsha[f[1]] = f[2]; pinver[f[1]] = f[3]; npins++
  }
  close(pins)
}
$1 == "USE" {
  file = $2; ln = $3; val = $4; cmt = $5
  # 저장소 안 액션은 **오늘 실패다**(r1 verifier M-1 · cr medium-1). 앞 판은 세기만 하고
  # 「보지 않는 자리가 생기면 실패」를 `.github/actions` **디렉터리 이름**에 걸어, `./tools/act`
  # 처럼 다른 자리에 두면 그 `action.yml` 안의 태그 `uses` 가 한 단계 간접으로 그대로 열렸다.
  # 술어를 이름이 아니라 **참조 자체**로 옮긴다. 저장소 안 액션을 실제로 들이는 날에는 이 게이트를
  # 그 `action.yml` 까지 넓히는 것이 맞고, 그 전에는 조용히 통과시키지 않는다(오늘 0 건).
  if (val ~ /^\.\//) {
    nlocal++
    viol("LOCALDIR", file " " ln "행: 저장소 안 액션 참조 — 그 action.yml 안의 uses 를 이 검사가 보지 않는다: " val)
    next
  }
  at = index(val, "@")
  if (at == 0) { viol("FORM", file " " ln "행: 핀이 없다(@ 가 없다): " val); next }
  action = substr(val, 1, at - 1); ref = substr(val, at + 1)
  if (val ~ /^docker:\/\//) {
    if (substr(ref, 1, 7) != "sha256:" || !hex(substr(ref, 8), 64)) {
      viol("FORM", file " " ln "행: docker 참조가 @sha256:<64hex> 가 아니다: " val); next
    }
  } else {
    if (action !~ /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.\/-]+$/) {
      viol("FORM", file " " ln "행: 액션 이름을 읽지 못한다: " val); next
    }
    if (!hex(ref, 40)) {
      viol("FORM", file " " ln "행: commit SHA 핀이 아니다(태그·브랜치·짧은 SHA·대문자): " val); next
    }
  }
  npinned++
  if (action in seen) {
    if (seen[action] != ref) { viol("SET", "한 액션이 두 SHA 로 쓰였다: " action) }
  } else { seen[action] = ref; order[++nord] = action }
  if (cmt == "") { viol("VERSION", file " " ln "행: 꼬리 주석(# vX.Y.Z)이 없다: " action); next }
  c = cmt; sub(/^#[ \t]*/, "", c); split(c, cf, /[ \t]+/); ver = cf[1]
  if (!(action in pinsha)) { next }
  if (ver != pinver[action]) {
    viol("VERSION", file " " ln "행: 꼬리 주석의 판이 기대 파일과 다르다: " action " " ver " != " pinver[action])
  }
}
$1 == "COUNT" { nuses = $2; bad = $3 }
END {
  for (a in seen) {
    if (!(a in pinsha)) { viol("SET", "기대 파일에 없는 액션·SHA 가 워크플로에 있다: " a "@" seen[a]) }
    else if (pinsha[a] != seen[a]) { viol("SET", "기대 파일과 SHA 가 다르다: " a " " seen[a] " != " pinsha[a]) }
  }
  for (a in pinsha) {
    if (!(a in seen)) { viol("SET", "기대 파일에만 있고 워크플로가 쓰지 않는 액션: " a) }
  }
  if (nuses < 1) { viol("EMPTY", "uses 를 한 건도 수집하지 못했다 — 훑지 못한 것과 구별되지 않는다") }
  printf "SUMMARY\t%d\t%d\t%d\t%d\t%d\t%d\n", nuses, npinned, nlocal, npins, bad, nviol
}
'

# 수집 대상 파일. **fail-closed 로 넓게 잡는다**(r1 verifier L-1) — 확장자는 대소문자를 무시하고
# (`.YML`), 심볼릭 링크도 따라간다(`-L`). GitHub 이 그 둘을 워크플로로 실행하지 않더라도 손해는
# 「안 돌릴 파일에까지 핀을 요구한다」뿐이고, 반대 방향은 조용한 수집 누락이다.
# **parity 스크립트가 같은 규칙을 쓴다** — 두 수집이 어긋나면 등식 자체가 거짓말이 된다.
_workflow_files() { # <디렉터리>
  find -L "$1" -type f \( -iname '*.yml' -o -iname '*.yaml' \) | sort
}

_judge() { # <디렉터리> <기대 파일> — 판정 줄을 stdout 에, 종료 코드로 결과를 낸다
  local dir="$1" pins="$2" files=() report judged summary localdir="" f
  local nuses npinned nlocal npins bad nviol
  while IFS= read -r f; do files+=("$f"); done < <(_workflow_files "$dir")
  if [ "${#files[@]}" -eq 0 ]; then
    echo "EMPTY	워크플로 파일(*.yml·*.yaml)이 한 건도 없다: $dir"
    return 2
  fi
  report="$(awk "$AWK_SCAN" "${files[@]}")"
  judged="$(printf '%s\n' "$report" | awk -v pins="$pins" "$AWK_JUDGE")"
  if [ -e "$(dirname "$dir")/actions" ]; then
    localdir="LOCALDIR	복합 액션 디렉터리가 생겼다 — 그 안의 uses 는 이 검사가 보지 않는다: $(dirname "$dir")/actions"
  fi
  summary="$(printf '%s\n' "$judged" | awk -F'\t' '$1=="SUMMARY" {print $2, $3, $4, $5, $6, $7}')"
  # shellcheck disable=SC2086
  set -- $summary
  nuses="${1:-0}"; npinned="${2:-0}"; nlocal="${3:-0}"; npins="${4:-0}"; bad="${5:-1}"; nviol="${6:-1}"
  printf '%s\n' "$report" | awk -F'\t' '$1=="UNSUPPORTED"'
  printf '%s\n' "$judged" | awk -F'\t' '$1!="SUMMARY" && NF>0'
  [ -z "$localdir" ] || printf '%s\n' "$localdir"
  printf 'STATS\t%s\t%s\t%s\t%s\n' "$nuses" "$npinned" "$nlocal" "$npins"
  [ "$bad" = "0" ] || return 2
  [ -z "$localdir" ] || return 1
  [ "$nviol" = "0" ] || return 1
  return 0
}

# `--count` — 수집한 `uses` 값만 한 줄씩 정렬해 낸다. `tools/workflow-action-pins-parity.py` 가
# **고정된 YAML 파서(PyYAML)와의 다중집합 등식**으로 수집 완전성을 재는 입력이다. 꼴·등식·판은
# 보지 않는다(기대 파일도 읽지 않는다) — 이 모드가 답하는 물음은 「무엇을 수집했는가」 하나다.
_count_mode() { # <디렉터리>
  local dir="$1" files=() report f
  while IFS= read -r f; do files+=("$f"); done < <(_workflow_files "$dir")
  if [ "${#files[@]}" -eq 0 ]; then
    echo "워크플로 파일(*.yml·*.yaml)이 한 건도 없다: $dir" >&2
    return 2
  fi
  report="$(awk "$AWK_SCAN" "${files[@]}")"
  if [ -n "$(printf '%s\n' "$report" | awk -F'\t' '$1=="UNSUPPORTED"')" ]; then
    echo "워크플로에 이 검사가 읽지 못하는 표기가 있다 — 수집을 내지 않는다(판정 불가):" >&2
    printf '%s\n' "$report" | awk -F'\t' '$1=="UNSUPPORTED" {printf "  %s %s행: %s\n", $2, $3, $4}' >&2
    return 2
  fi
  printf '%s\n' "$report" | awk -F'\t' '$1=="USE" {print $4}' | sort
}

_first_pinned_file() { # <디렉터리> — 핀 줄을 가진 첫 워크플로 파일의 **상대 경로**
  local dir="$1" f
  while IFS= read -r f; do
    if awk '/uses:/ && /@[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]/ { found = 1 }
            END { exit !found }' "$f"; then
      printf '%s\n' "${f#"$dir"/}"
      return 0
    fi
  done < <(_workflow_files "$dir")
  return 1
}

_mutate_wf() { # <사본 디렉터리> <모드> <상대 경로> — 워크플로 쪽 변이(전부 **바꿔치우기**)
  local wf="$1" mode="$2" target out
  target="$wf/$3"
  out="$target.mutant"
  awk -v mode="$mode" '
    function hexshift(s,   i, c, r) {
      r = ""
      for (i = 1; i <= length(s); i++) { c = substr(s, i, 1); r = r ((c == "0") ? "1" : "0") }
      return r
    }
    mode != "allshas" && hit { print; next }
    {
      line = $0
      if (line ~ /uses:/ && match(line, /@[0-9a-f]+/) && RLENGTH == 41) {
        sha = substr(line, RSTART + 1, 40)
        pre = substr(line, 1, RSTART - 1); post = substr(line, RSTART + 41)
        if (mode == "tag")             { line = pre "@v5" post }
        else if (mode == "short")      { line = pre "@" substr(sha, 1, 39) post }
        else if (mode == "upper")      { line = pre "@" toupper(sha) post }
        else if (mode == "allshas")    { line = pre "@" hexshift(sha) post }
        else if (mode == "twoshas")    { line = line "\n" pre "@" hexshift(sha) post }
        else if (mode == "nocomment")  { sub(/[ \t]*#.*$/, "", line) }
        else if (mode == "badversion") { sub(/#[ \t]*v[0-9][0-9A-Za-z.-]*/, "# v0.0.0", line) }
        else if (mode == "flowmap")    { line = pre "@v5}"; sub(/uses: /, "{uses: ", line) }
        else if (mode == "localuses")  { sub(/uses:.*$/, "uses: ./ci/act", line) }
        else if (mode == "quotedkey")  { sub(/uses:.*$/, "\"uses\": actions/checkout@v5", line) }
        else if (mode == "blockkey" && line ~ /^[ ]*-[ ]+uses:/) {
          # r1 verifier H-1 ⓐ — step 의 첫 키가 블록 스칼라면 형제 키가 본문으로 삼켜지던 꼴.
          # 들여쓰기만 재사용한다(`pre` 는 `@` 앞 전부라 쓸 수 없다).
          match(line, /^[ ]*/); lead = substr(line, 1, RLENGTH)
          line = lead "- if: >-\n" lead "    true\n" lead "  uses: actions/checkout@v5  # v5.1.0"
        }
        hit = 1
      }
      print line
    }
  ' "$target" > "$out"
  mv "$out" "$target"
}

_mutate_pins() { # <사본 기대 파일> <모드> — 기대 파일 쪽 변이(r1 cr low: 앞 판은 이 쪽이 0 이었다)
  local pins="$1" mode="$2" out="$1.mutant"
  awk -v mode="$mode" '
    function hexshift(s,   i, c, r) {
      r = ""
      for (i = 1; i <= length(s); i++) { c = substr(s, i, 1); r = r ((c == "0") ? "1" : "0") }
      return r
    }
    hit || /^[ \t]*(#|$)/ { print; next }
    {
      n = split($0, f, /[ \t]+/)
      if (n == 3) {
        if (mode == "pins_sha")          { print f[1] "  " hexshift(f[2]) "  " f[3]; hit = 1; next }
        else if (mode == "pins_version") { print f[1] "  " f[2] "  v0.0.0"; hit = 1; next }
        else if (mode == "pins_drop")    { hit = 1; next }
        else if (mode == "pins_policy")  { print f[1] "  " f[2]; hit = 1; next }
      }
      print
    }
    END {
      if (mode == "pins_extra") { print "zz/never-used  0123456789abcdef0123456789abcdef01234567  v1.0.0" }
    }
  ' "$pins" > "$out"
  mv "$out" "$pins"
}

# 술어마다 변이 하나 이상(r1 verifier M-2). 앞 판의 여섯은 FORM 과 「한 액션 두 SHA」만 실질로
# 지켰고 — 기대 불일치 · 판 불일치 · 역방향 등식 · unsupported · LOCALDIR · EMPTY · POLICY 는
# 술어를 꺼도 변이가 초록이었다. 꺼서 재는 무력화 대조는 evidence 가 든다.
_SELFCHECK_MODES="
tag:FORM
short:FORM
upper:FORM
blockkey:FORM
newfile:FORM
twoshas:SET
allshas:SET
localuses:LOCALDIR
flowmap:UNSUPPORTED
quotedkey:UNSUPPORTED
nocomment:VERSION
badversion:VERSION
empty:EMPTY
pins_sha:SET
pins_drop:SET
pins_extra:SET
pins_version:VERSION
pins_policy:POLICY
"

_selfcheck() {
  local tmp rc out spec mode want rel n_red=0
  rel="$(_first_pinned_file "$DIR")" || {
    echo "자기 변이 대조: 핀 줄을 가진 워크플로 파일이 없다 — 변이를 얹을 자리가 없다" >&2
    return 2
  }
  tmp="$(mktemp -d)"
  MUTATION_TMP="$tmp"   # 예기치 못한 죽음에서도 사본을 남기지 않는다(아래 EXIT trap)
  for spec in $_SELFCHECK_MODES; do
    mode="${spec%%:*}"; want="${spec##*:}"
    rm -rf "$tmp/wf" "$tmp/pins.txt"
    cp -R "$DIR" "$tmp/wf"
    cp "$PINS" "$tmp/pins.txt"
    case "$mode" in
      newfile)
        printf 'name: mutant\non:\n  push:\njobs:\n  j:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v5  # v5.1.0\n' \
          > "$tmp/wf/zzz-mutant.yml"
        [ -f "$tmp/wf/zzz-mutant.yml" ] || { _mutation_abort "$tmp" "$mode" "사본에 적용되지 않았다"; return 2; }
        ;;
      empty)
        # 수집 0 건을 만든다 — **바꿔치우기**다(사본의 워크플로 파일을 전부 지우고 하나를 둔다).
        find "$tmp/wf" -type f \( -iname '*.yml' -o -iname '*.yaml' \) -delete
        printf 'name: mutant\non:\n  push:\njobs:\n  j:\n    runs-on: ubuntu-latest\n    steps:\n      - run: true\n' \
          > "$tmp/wf/zzz-mutant.yml"
        if [ -n "$(_count_mode "$tmp/wf" 2>/dev/null)" ]; then
          _mutation_abort "$tmp" "$mode" "수집이 0 건이 되지 않았다"; return 2
        fi
        ;;
      pins_*)
        _mutate_pins "$tmp/pins.txt" "$mode"
        if cmp -s "$tmp/pins.txt" "$PINS"; then
          _mutation_abort "$tmp" "$mode" "사본이 원본과 같다 — 더하기만 한 변이는 초록이 된다"
          return 2
        fi
        ;;
      *)
        _mutate_wf "$tmp/wf" "$mode" "$rel"
        if cmp -s "$tmp/wf/$rel" "$DIR/$rel"; then
          _mutation_abort "$tmp" "$mode" "사본이 원본과 같다 — 더하기만 한 변이는 초록이 된다"
          return 2
        fi
        ;;
    esac
    out="$(WORKFLOW_ACTION_PINS_SELFCHECK=0 bash "${BASH_SOURCE[0]}" "$tmp/wf" "$tmp/pins.txt" 2>&1)" && rc=0 || rc=$?
    if [ "$rc" = "0" ]; then
      _mutation_abort "$tmp" "$mode" "변이를 넣었는데 게이트가 초록이다"
      return 2
    fi
    case "$out" in
      *"$want"*) n_red=$((n_red + 1)) ;;
      *) _mutation_abort "$tmp" "$mode" "붉었지만(exit ${rc}) 사유가 ${want} 가 아니다"
         return 2 ;;
    esac
  done
  rm -rf "$tmp"
  MUTATION_TMP=""
  echo "자기 변이 대조 통과 — 변이 ${n_red} 건 전부 적용 확인 + 기대 사유로 붉음"
}

_mutation_abort() { # <임시 디렉터리> <모드> <사유>
  echo "자기 변이 대조 실패 — 변이 '$2': $3" >&2
  rm -rf "$1"
  MUTATION_TMP=""
}

MUTATION_TMP=""
trap '[ -z "$MUTATION_TMP" ] || rm -rf "$MUTATION_TMP"' EXIT

if [ "$COUNT_MODE" = "1" ]; then
  _count_mode "$DIR"
  exit $?
fi

verdict="$(_judge "$DIR" "$PINS")" && rc=0 || rc=$?
stats="$(printf '%s\n' "$verdict" | awk -F'\t' '$1=="STATS" {print $2, $3, $4, $5}')"
body="$(printf '%s\n' "$verdict" | awk -F'\t' '$1!="STATS" && NF>0')"

if [ "$rc" = "2" ]; then
  echo "워크플로에 이 검사가 읽지 못하는 표기가 있다 — 통과시키지 않고 정지한다(판정 불가):" >&2
  printf '%s\n' "$body" >&2
  exit 2
fi
if [ "$rc" != "0" ]; then
  echo "워크플로 액션 핀 위반 — 떠 있는 태그는 상류에서 움직인다(GHSA-69fq-xp46-6x23):" >&2
  printf '%s\n' "$body" >&2
  exit 1
fi

# `set --` 는 스크립트의 위치 인자를 덮는다(r1 cr low) — 이름으로 읽는다.
read -r nuses npinned nlocal npins <<< "$stats"

# **자기 변이 대조가 먼저다**(r1 cr medium-3) — 앞 판은 「통과」를 찍은 뒤 게이트 자신을 쟀다.
# 그래서 자기 변이가 실패하는 어떤 갈래에서도 stdout 첫 줄이 「통과」였고, 로그를 훑는 사람에게는
# 초록으로 보였다. 판정 문구는 **게이트 자신의 건전성까지 선 뒤에만** 낸다.
case "$SELFCHECK" in
  1) _selfcheck ;;
  0) echo "자기 변이 대조 건너뜀(SELFCHECK=0) — 이 실행은 게이트 자신을 재지 않았다" >&2 ;;
esac

echo "워크플로 액션 핀 통과 — uses ${nuses} 건(핀 ${npinned} · 저장소 안 ${nlocal}) · 기대 파일 ${npins} 줄과 집합 등식 · 판 주석 일치"
