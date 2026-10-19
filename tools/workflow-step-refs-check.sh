#!/usr/bin/env bash
# M7/7F-local(검토 r2 R2-H1, r3 에서 술어를 단단히 함) — 워크플로의 조건·식이 **없는 step id** 를
# 가리키는지 잰다.
#
# 왜 있는가: r1 이 빌드 step 둘을 하나로 합치면서(`build_web`·`build_proxy` → `build_web_proxy`)
# 취약점 게이트 두 step 의 조건은 **옛 id 를 그대로** 가리켰다. Actions 에서 없는 step 의
# `outcome` 은 null 이라 `== 'success'` 가 거짓이 되고, 두 게이트는 **매 실행 조용히 skipped**
# 였다 — 실패가 아니라 부재라서 job 은 초록이었다.
#
# 술어: **job 마다** 그 job 의 어느 자리에서든 참조하는 `steps.<id>` 전부가 그 job 에 정의된 id
# 집합 안에 있어야 한다(Actions 의 `steps` 문맥은 job 안으로 닫혀 있다).
#
# **파서 선택(계약 D-7F-7)**: 진짜 YAML 파서를 쓰고 싶었으나 이 검사는 `container` job 에서 돌고
# 그 job 이 설치하는 것은 Java·Gradle 뿐이다. 저장소가 고정한 `pyyaml` 은 `ml-engine` job 의
# uv 환경 안에만 있고, 러너의 주변 python 패키지에 기대는 것은 「러너에 도구가 없으면 로컬 초록은
# 의미가 없다」 그 자체다. 그래서 **새 의존 0(awk)** 으로 가되, 읽지 못하는 표기는 통과가 아니라
# **정지(exit 2)** 로 둔다 — 열거가 아니라 구성이다.
#
# r3 이 닫은 것:
#   · id 는 **step 항목 자리**에서만 센다(`run:` 본문의 `id:` 한 줄이 가짜 정의를 만들지 못한다).
#   · 블록 스칼라(`|`·`>`) 본문을 추적해 id 집계에서 뺀다. 참조는 **본문에서도** 줍는다
#     (`run:`·`env:` 안의 `${{ steps.… }}` 도 같은 부류의 조용한 결함이다).
#   · `if:` 를 블록 스칼라로 쓰면 **exit 2**(이 저장소는 한 줄로만 쓴다 — 읽지 못하는 형태는 정지).
#   · 흐름 매핑 step(`- {id: …}`)도 **exit 2**.
#   · 색인 표기 `steps['id']`·`steps["id"]` 를 함께 센다.
#   · 끝에 점이 없는 참조(`toJSON(steps.foo)`)도 센다.
#   · 주석은 따옴표 밖의 `#` 부터 잘라낸다(주석 속 이름은 참조가 아니다).
#   · 따옴표로 쓴 id 정의(`id: "foo"`)도 정의로 센다(r3 이 남긴 fail-closed 오탐 제거).
#
# **범위 밖**: `needs.<job>` 참조는 이 검사가 보지 않는다(job 간 의존은 다른 축이고 이 워크플로는
# 오늘 `needs` 를 쓰지 않는다). `${{ }}` 안의 동적 조립(`steps[format(…)]`)도 보지 않는다 —
# 색인 표기 규칙에 걸리지 않고 조용히 빠지므로, 그런 표기가 들어오는 날 이 줄을 고친다.
#
# 실행:
#
#     ./tools/workflow-step-refs-check.sh .github/workflows/ci.yml
set -euo pipefail

WORKFLOW="${1:-}"
[ -n "$WORKFLOW" ] || { echo "사용법: $0 <워크플로 yml>" >&2; exit 2; }
[ -f "$WORKFLOW" ] || { echo "워크플로 파일이 없다: $WORKFLOW" >&2; exit 2; }

# 양성 대조 하한 — 2026-10-19 재실측(`ci.yml`): job **4** · step id **14** · `steps.<id>` 참조 **17**.
# 하한은 실측의 절반(내림)이고, 잡으려는 것은 「조금 줄었다」가 아니라 **「훑지 못했다」**다 —
# 한두 개가 조용히 사라지는 경우는 하한이 아니라 위의 **exit 2 표기 거부**가 받는다(cr R3-2·R3-4).
MIN_JOBS=2
MIN_IDS=7
MIN_REFS=8

report="$(
  awk '
    function strip_comment(s,   i, c, q) {
      q = ""
      for (i = 1; i <= length(s); i++) {
        c = substr(s, i, 1)
        if (q != "") { if (c == q) q = "" }
        else if (c == "\"" || c == "\047") { q = c }
        else if (c == "#" && (i == 1 || substr(s, i - 1, 1) ~ /[ \t]/)) { return substr(s, 1, i - 1) }
      }
      return s
    }
    function indent_of(s) { match(s, /^ */); return RLENGTH }
    function scan_refs(s,   rest, hit, name) {
      rest = s
      while (match(rest, /steps(\.[A-Za-z0-9_-]+|\[ *["\047][A-Za-z0-9_-]+["\047] *\])/)) {
        hit = substr(rest, RSTART, RLENGTH)
        rest = substr(rest, RSTART + RLENGTH)
        name = hit
        sub(/^steps/, "", name)
        gsub(/[].[ "\047]/, "", name)
        if (name != "") { refs[job "\t" name] = 1; nrefs++ }
      }
    }
    function unsupported(what) { printf "UNSUPPORTED\t%s\t%d\n", what, NR; bad = 1 }
    {
      line = strip_comment($0)
      ind = indent_of(line)
      body = line
      sub(/^ +/, "", body)
      sub(/[ \t]+$/, "", body)
      blank = (body == "")
    }
    # 블록 스칼라 본문 — id 집계에서 빼고, 참조는 줍는다.
    inblock {
      if (blank || ind > block_indent) { if (job != "") scan_refs(line); next }
      inblock = 0
    }
    /^jobs:[ \t]*$/ { injobs = 1; job = ""; next }
    injobs && ind == 0 && !blank { injobs = 0; job = "" }
    injobs && ind == 2 && body ~ /^[A-Za-z0-9_.-]+:$/ {
      job = body; sub(/:$/, "", job); njobs++
      step_key_indent = -1; next
    }
    job == "" { next }
    {
      scan_refs(line)
      if (body ~ /^- /) {
        if (body ~ /^- *\{/) { unsupported("흐름 매핑 step(- {…}) — 이 검사가 읽지 못하는 표기") }
        step_key_indent = ind + 2
        if (body ~ /^- +id: *["\047]?[A-Za-z0-9_-]+["\047]? *$/) {
          v = body; sub(/^- +id: */, "", v); gsub(/["\047 ]/, "", v)
          defined[job "\t" v] = 1; nids++
        }
      }
      else if (step_key_indent > 0 && ind == step_key_indent && body ~ /^id: *["\047]?[A-Za-z0-9_-]+["\047]? *$/) {
        v = body; sub(/^id: */, "", v); gsub(/["\047 ]/, "", v)
        defined[job "\t" v] = 1; nids++
      }
      if (body ~ /:[ \t]*[|>][-+0-9]*$/) {
        key = body; sub(/^- +/, "", key); sub(/:[ \t]*[|>][-+0-9]*$/, "", key)
        if (key == "if") { unsupported("블록 스칼라 if:(이 저장소는 if: 를 한 줄로만 쓴다)") }
        inblock = 1; block_indent = ind
      }
    }
    END {
      for (k in refs) {
        split(k, a, "\t")
        if (!(k in defined)) printf "DANGLING\t%s\t%s\n", a[1], a[2]
      }
      printf "COUNT\t%d\t%d\t%d\t%d\n", njobs, nids, nrefs, bad
    }
  ' "$WORKFLOW"
)"

counts="$(printf '%s\n' "$report" | awk -F'\t' '$1=="COUNT" {print $2, $3, $4, $5}')"
set -- $counts
njobs="${1:-0}"; nids="${2:-0}"; nrefs="${3:-0}"; bad="${4:-0}"

if [ "$bad" != "0" ]; then
  echo "워크플로에 이 검사가 읽지 못하는 표기가 있다 — 통과시키지 않고 정지한다(판정 불가):" >&2
  printf '%s\n' "$report" | awk -F'\t' '$1=="UNSUPPORTED" {printf "  %d행: %s\n", $3, $2}' | sort -n >&2
  exit 2
fi

if [ "$njobs" -lt "$MIN_JOBS" ] || [ "$nids" -lt "$MIN_IDS" ] || [ "$nrefs" -lt "$MIN_REFS" ]; then
  echo "워크플로를 훑지 못했다 — job ${njobs}(하한 ${MIN_JOBS}) · id ${nids}(하한 ${MIN_IDS}) · 참조 ${nrefs}(하한 ${MIN_REFS}): ${WORKFLOW}(판정 불가)" >&2
  exit 2
fi

dangling="$(printf '%s\n' "$report" | awk -F'\t' '$1=="DANGLING" {printf "  job %s 의 식이 없는 step id 를 가리킨다: %s\n", $2, $3}' | sort)"
if [ -n "$dangling" ]; then
  echo "워크플로 step 참조 위반 — 없는 id 를 가리키는 조건은 **실패가 아니라 영원한 skip** 이다:" >&2
  printf '%s\n' "$dangling" >&2
  exit 1
fi

echo "워크플로 step 참조 통과 — job ${njobs} · id ${nids} · steps.<id> 참조 ${nrefs} · 매달린 참조 0 (${WORKFLOW})"
