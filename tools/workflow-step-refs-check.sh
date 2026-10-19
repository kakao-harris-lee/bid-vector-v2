#!/usr/bin/env bash
# M7/7F-local(검토 r2 R2-H1) — 워크플로의 `if:` 가 **없는 step id** 를 가리키는지 잰다.
#
# 왜 있는가: r1 이 빌드 step 둘을 하나로 합치면서(`build_web`·`build_proxy` → `build_web_proxy`)
# 취약점 게이트 두 step 의 조건은 **옛 id 를 그대로** 가리켰다. Actions 에서 없는 step 의
# `outcome` 은 null 이라 `== 'success'` 가 거짓이 되고, 두 게이트는 **매 실행 조용히 skipped**
# 였다 — 실패가 아니라 부재라서 job 은 초록이었고, `if:` 를 평가하지 않는 로컬 재생에서도
# 보이지 않았다. 「상시 붉은 게이트도, 안 돌린 게이트도 아무것도 막지 못한다」의 둘째 경우다.
#
# 술어: **job 마다** 그 job 의 `if:` 들이 참조하는 `steps.<id>` 전부가 그 job 에 정의된 id 집합
# 안에 있어야 한다(Actions 의 `steps` 문맥은 job 안으로 닫혀 있다 — 다른 job 의 id 는 애초에
# 보이지 않는다).
#
# 외부 도구를 들이지 않는다(awk 하나). 파서가 깨져 **아무것도 못 찾는 상태가 조용한 통과가
# 되지 않게**, 찾은 id 수와 참조 수에 하한을 두고 그 아래면 판정 불가(exit 2)로 끊는다.
#
# 실행:
#
#     ./tools/workflow-step-refs-check.sh .github/workflows/ci.yml
set -euo pipefail

WORKFLOW="${1:-}"
[ -n "$WORKFLOW" ] || { echo "사용법: $0 <워크플로 yml>" >&2; exit 2; }
[ -f "$WORKFLOW" ] || { echo "워크플로 파일이 없다: $WORKFLOW" >&2; exit 2; }

# 양성 대조 하한 — 2026-10-19 실측(`ci.yml`): job 4 · id 13 · `steps.<id>` 참조 17.
# 파싱이 깨지면 이 수들이 0 근처로 떨어진다. 하한은 실측의 절반(내림)이고, 잡으려는 것은
# 「조금 줄었다」가 아니라 **「훑지 못했다」**다.
MIN_JOBS=2
MIN_IDS=6
MIN_REFS=8

report="$(
  awk '
    # jobs: 블록 진입
    /^jobs:[[:space:]]*$/ { injobs = 1; next }
    # 들여쓰기 0 의 새 최상위 키 → jobs 블록 종료
    injobs && /^[^[:space:]#]/ { injobs = 0 }
    # job 머리 — 2칸 들여쓰기의 키
    injobs && /^  [A-Za-z0-9_.-]+:[[:space:]]*$/ {
      job = $1; sub(/:$/, "", job); jobs[job] = 1; njobs++; next
    }
    job == "" { next }
    # step id 선언 — `id: <값>`(들여쓰기 무관, 값은 따옴표 없이 쓰는 이 저장소 관례)
    /^[[:space:]]+id:[[:space:]]*[A-Za-z0-9_-]+[[:space:]]*$/ {
      v = $2; gsub(/[[:space:]]/, "", v); defined[job "\t" v] = 1; nids++; next
    }
    # `if:` 안의 steps.<id> 참조 — 한 줄에 여럿일 수 있다
    /^[[:space:]]+if:/ {
      line = $0
      while (match(line, /steps\.[A-Za-z0-9_-]+\./)) {
        ref = substr(line, RSTART + 6, RLENGTH - 7)
        refs[job "\t" ref] = refs[job "\t" ref] + 1
        nrefs++
        line = substr(line, RSTART + RLENGTH)
      }
    }
    END {
      for (k in refs) {
        split(k, a, "\t")
        if (!((k) in defined)) printf "DANGLING\t%s\t%s\n", a[1], a[2]
      }
      printf "COUNT\t%d\t%d\t%d\n", njobs, nids, nrefs
    }
  ' "$WORKFLOW"
)"

counts="$(printf '%s\n' "$report" | awk -F'\t' '$1=="COUNT" {print $2, $3, $4}')"
set -- $counts
njobs="${1:-0}"; nids="${2:-0}"; nrefs="${3:-0}"

if [ "$njobs" -lt "$MIN_JOBS" ] || [ "$nids" -lt "$MIN_IDS" ] || [ "$nrefs" -lt "$MIN_REFS" ]; then
  echo "워크플로를 훑지 못했다 — job ${njobs}(하한 ${MIN_JOBS}) · id ${nids}(하한 ${MIN_IDS}) · 참조 ${nrefs}(하한 ${MIN_REFS}): ${WORKFLOW}(판정 불가)" >&2
  exit 2
fi

dangling="$(printf '%s\n' "$report" | awk -F'\t' '$1=="DANGLING" {printf "  job %s 의 if: 가 없는 step id 를 가리킨다: %s\n", $2, $3}' | sort)"
if [ -n "$dangling" ]; then
  echo "워크플로 step 참조 위반 — 없는 id 를 가리키는 조건은 **실패가 아니라 영원한 skip** 이다:" >&2
  printf '%s\n' "$dangling" >&2
  exit 1
fi

echo "워크플로 step 참조 통과 — job ${njobs} · id ${nids} · steps.<id> 참조 ${nrefs} · 매달린 참조 0 (${WORKFLOW})"
