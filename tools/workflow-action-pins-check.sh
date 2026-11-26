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
DIR="${1:-}"
PINS="${2:-$REPO_ROOT/config/quality/workflow-action-pins.txt}"
SELFCHECK="${WORKFLOW_ACTION_PINS_SELFCHECK:-1}"

[ -n "$DIR" ] || { echo "사용법: $0 <워크플로 디렉터리> [기대 파일]" >&2; exit 2; }
[ -d "$DIR" ] || { echo "워크플로 디렉터리가 없다: $DIR" >&2; exit 2; }
[ -f "$PINS" ] || { echo "기대 파일이 없다: $PINS" >&2; exit 2; }

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
  ci = comment_at($0)
  code = (ci > 0) ? substr($0, 1, ci - 1) : $0
  cmt = (ci > 0) ? trim(substr($0, ci)) : ""
  match(code, /^ */); ind = RLENGTH
  body = trim(code)
  blank = (body == "")
}
inblock {
  if (blank || ind > block_indent) { next }
  inblock = 0
}
code ~ /(^|[ \t[{,])[&*][A-Za-z0-9_-]+([ \t\]},]|$)/ {
  unsup("YAML 앵커·별칭 — 별칭이 가리키는 값을 이 검사가 풀지 못한다"); next
}
{
  # `uses` **키**는 두 꼴로만 읽는다. 그 밖의 자리에 `uses:` 가 보이면(흐름 매핑 `- {uses: …}` ·
  # 중첩 · 한 줄 `run:` 본문) 통과가 아니라 정지다. 콜론 없는 `uses` 글자(step 이름·주석)는 키가
  # 아니므로 보지 않는다.
  if (body ~ /(^|[^A-Za-z0-9_-])uses[ \t]*:/) {
    if (body ~ /^-[ \t]+uses:/) { v = body; sub(/^-[ \t]+uses:/, "", v); take_use(v, cmt) }
    else if (body ~ /^uses:/)   { v = body; sub(/^uses:/, "", v); take_use(v, cmt) }
    else { unsup("uses: 키가 읽을 수 있는 두 꼴(- uses: · uses:)이 아닌 자리에 있다") }
  }
  if (body ~ /:[ \t]*[|>][-+0-9]*$/) { inblock = 1; block_indent = ind }
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
  if (val ~ /^\.\//) { nlocal++; next }
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

_judge() { # <디렉터리> <기대 파일> — 판정 줄을 stdout 에, 종료 코드로 결과를 낸다
  local dir="$1" pins="$2" files=() report judged summary localdir=""
  local nuses npinned nlocal npins bad nviol
  while IFS= read -r f; do files+=("$f"); done < <(find "$dir" -type f \( -name '*.yml' -o -name '*.yaml' \) | sort)
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

_first_pinned_file() { # <디렉터리> — 핀 줄을 가진 첫 워크플로 파일의 **상대 경로**
  local dir="$1" f
  while IFS= read -r f; do
    if awk '/uses:/ && /@[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]/ { found = 1 }
            END { exit !found }' "$f"; then
      printf '%s\n' "${f#"$dir"/}"
      return 0
    fi
  done < <(find "$dir" -type f \( -name '*.yml' -o -name '*.yaml' \) | sort)
  return 1
}

_mutate() { # <사본 디렉터리> <모드> <상대 경로> — 첫 핀 줄을 **바꿔치운다**
  local wf="$1" mode="$2" target out
  target="$wf/$3"
  out="$target.mutant"
  awk -v mode="$mode" '
    function hexshift(s,   i, c, r) {
      r = ""
      for (i = 1; i <= length(s); i++) { c = substr(s, i, 1); r = r ((c == "0") ? "1" : "0") }
      return r
    }
    hit { print; next }
    {
      line = $0
      if (line ~ /uses:/ && match(line, /@[0-9a-f]+/) && RLENGTH == 41) {
        sha = substr(line, RSTART + 1, 40)
        pre = substr(line, 1, RSTART - 1); post = substr(line, RSTART + 41)
        if (mode == "tag")            { line = pre "@v5" post }
        else if (mode == "short")     { line = pre "@" substr(sha, 1, 39) post }
        else if (mode == "upper")     { line = pre "@" toupper(sha) post }
        else if (mode == "unlisted")  { line = pre "@" hexshift(sha) post }
        else if (mode == "nocomment") { sub(/[ \t]*#.*$/, "", line) }
        hit = 1
      }
      print line
    }
  ' "$target" > "$out"
  mv "$out" "$target"
}

_selfcheck() {
  local tmp rc out spec mode want rel n_red=0
  local modes="tag:FORM short:FORM upper:FORM unlisted:SET nocomment:VERSION newfile:FORM"
  rel="$(_first_pinned_file "$DIR")" || {
    echo "자기 변이 대조: 핀 줄을 가진 워크플로 파일이 없다 — 변이를 얹을 자리가 없다" >&2
    return 2
  }
  tmp="$(mktemp -d)"
  MUTATION_TMP="$tmp"   # 예기치 못한 죽음에서도 사본을 남기지 않는다(아래 EXIT trap)
  for spec in $modes; do
    mode="${spec%%:*}"; want="${spec##*:}"
    rm -rf "$tmp/wf" "$tmp/pins.txt"
    cp -R "$DIR" "$tmp/wf"
    cp "$PINS" "$tmp/pins.txt"
    if [ "$mode" = "newfile" ]; then
      printf 'name: mutant\non:\n  push:\njobs:\n  j:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v5  # v5.1.0\n' \
        > "$tmp/wf/zzz-mutant.yml"
      [ -f "$tmp/wf/zzz-mutant.yml" ] || { _mutation_abort "$tmp" "$mode" "사본에 적용되지 않았다"; return 2; }
    else
      _mutate "$tmp/wf" "$mode" "$rel"
      if cmp -s "$tmp/wf/$rel" "$DIR/$rel"; then
        _mutation_abort "$tmp" "$mode" "사본이 원본과 같다 — 더하기만 한 변이는 초록이 된다"
        return 2
      fi
    fi
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

# shellcheck disable=SC2086
set -- $stats
echo "워크플로 액션 핀 통과 — uses ${1} 건(핀 ${2} · 저장소 안 ${3}) · 기대 파일 ${4} 줄과 집합 등식 · 판 주석 일치"

if [ "$SELFCHECK" = "1" ]; then
  _selfcheck
fi
