#!/usr/bin/env bash
# M9/prod-tools — 운영 스크립트 **가드 술어의 거동 검사**: 반입 트리 ↔ manifest 묶기(7F 장부 v r2 L-1)와
# 일회 컨테이너 잔존·신호 경로 정리(L-2).
#
#     ./tools/prod-guards-check.sh
#
# **실제 docker 와 운영 host 에 닿지 않는다.** PATH 앞에 가짜 `docker`·`git` 을 두고 상태 디렉터리·
# 운영 env 파일·manifest 를 `mktemp -d` 아래 fixture 로 만든다 — 컨테이너를 하나도 만들지 않고
# 세 스크립트의 거절·통과와 trap 의 **삭제 대상**을 잰다. 각 술어마다 양성 대조(통과해야 하는 입력)와
# 음성 대조(거절해야 하는 입력)를 둘 다 돌린다.
#
# 가짜 쪽이 **거꾸로 단언하는 것 셋**(술어가 조용히 헐거워지는 축을 변이 없이도 잡는다):
#   · 가짜 git 은 `GIT_*` 가 보이면 exit 99 — 술어는 그 환경을 비운 채 git 을 불러야 한다(cr M-1).
#   · 가짜 git 은 `-C` 가 없거나 기대 루트가 아니면 exit 98 — 술어는 cwd 에 기대지 않는다(v L-2).
#   · 가짜 docker `ps` 는 `-a` 가 없으면 **멈춘 행을 숨긴다** — 잔존 술어는 멈춘 것까지 세야 한다(v L-1).
#
# 잠금만은 가짜가 없다 — 세 스크립트가 **고정 절대 경로**(`tools/prod-env.sh` 의
# `BIDVECTOR_PROD_LOCK_DIR`)를 쓰는 것이 설계이기 때문이다(환경변수에서 오면 두 셸이 서로 다른 파일을
# 잠근다). 그래서 이 검사는 **그 자리를 만들지 않는다**(cr M-2 — 만들면 검사를 돌린 uid 가 디렉터리
# 소유자가 되고 그 뒤 운영 스크립트 셋이 전부 멈춘다). 자리가 없으면 exit 2(판정 불가)다. 자리가 있으면
# **검사 전용 프로젝트 이름**의 잠금 파일만 만들고 끝에 지운다 — 운영 프로젝트의 잠금 파일은 건드리지 않는다.
#
# 신호 경로의 한계: systemd 는 cgroup 전체에 TERM 을 보내므로 compose 도 함께 죽고 trap 이 바로 돈다.
# 여기서는 `setsid` + 그룹 kill 로 같은 모양을 흉내낸다(그룹 번호는 `$!` 가 아니라 `ps -o pgid=` 로 읽는다 —
# `setsid` 가 fork 하면 `$!` 는 남의 그룹일 수 있다). 그룹 kill 이 안 되는 환경이면 가짜 run 이 끝난 뒤
# trap 이 도는데, **무엇을 지우는가**는 같은 값으로 잰다.
#
# 종료 코드: 0 전부 통과 · 1 하나라도 어긋남 · 2 전제 실패(도구·fixture·잠금 자리 — 판정 불가).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

_die() { echo "prod-guards-check: $1" >&2; exit "${2:-1}"; }

for t in jq git flock mktemp setsid ps sed awk docker; do
  command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2
done
# 가짜 `docker` 를 PATH 앞에 꽂기 **전에** 진짜 자리를 잡아 둔다 — 채널 env 해석만은 그것으로
# 넘긴다(M15/15Q 수정 라운드 1: 소비자와 같은 해석기로 재야 우회 줄의 거절이 측정이 된다).
REAL_DOCKER="$(command -v docker)"
# 아래에서 `$T/bin` 을 PATH 앞에 꽂으므로 **그 전에** 진짜 `flock` 의 자리를 잡아 둔다(가짜 flock 이
# 평소에는 이것으로 exec 한다 — 변이를 켜지 않은 동안 검사가 진짜 잠금으로 돈다).
REAL_FLOCK="$(command -v flock)"


# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

PASS=0 FAIL=0
_ok()  { PASS=$((PASS + 1)); echo "  ok   — $1"; }
_bad() { FAIL=$((FAIL + 1)); echo "  BAD  — $1" >&2; }

# <자리> — 없으면 **만들지 않고** 판정 불가로 끝난다(cr M-2).
_require_lock_dir() {
  [ -d "$1" ] || _die "잠금 자리가 없다: $1 — 이 검사는 운영 자리를 만들지 않는다(만들면 그 uid 가 소유자가 되어 반입·되돌림·훑기가 전부 멈춘다). 'sudo install -d -m 0755 -o \$(id -un) -g \$(id -gn) $1' 뒤에 다시(판정 불가)" 2
}

T="$(mktemp -d)" || _die "임시 디렉터리를 만들지 못했다" 2
PROJ="bv-guards-check-$$"
# **저장소 안에 만드는 fixture 는 trap 이 지운다**(M15/15V D-15V-9, cr L-4). 평상 흐름의 `rm -rf` 만
# 두면 중간의 Ctrl-C·`_die` 에서 저장소에 미추적 디렉터리가 남고, clean-tree 게이트는 in_scope 경로만
# 보므로 그 자리는 잡히지 않는다. 이름은 한 자리에 둔다(만드는 곳과 지우는 곳이 갈리지 않게).
REPO_LINK_TARGET="$REPO_ROOT/reports/.m15v-link-target"
_cleanup() {
  rm -rf "$T"
  rm -rf "${REPO_LINK_TARGET:?}"
  rm -f "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock" "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}-probe.lock"
}
trap _cleanup EXIT

# **자리 점검은 이 한 줄이고, 자기 검사도 같은 줄을 지난다.** 함수만 따로 시험하면 이 호출이 빠지는
# 변이가 초록이 된다(v r2 F-2) — 그래서 검사 전용 변수로 **자리만 바꿔** 자식으로 다시 돌리고, 그
# 경우에는 잠금을 잡지 않고 끝낸다. 운영 스크립트의 잠금 자리는 여전히 환경에서 오지 않는다(이 변수는
# 이 검사 파일 안에만 있다).
_require_lock_dir "${BV_GUARDS_SELFTEST_LOCK_DIR:-$BIDVECTOR_PROD_LOCK_DIR}"
if [ -n "${BV_GUARDS_SELFTEST_LOCK_DIR:-}" ]; then
  echo "prod-guards-check: 잠금 자리 점검만 하고 끝낸다(자기 검사) — ${BV_GUARDS_SELFTEST_LOCK_DIR}"
  exit 0
fi
# 잠금 자리의 **양성 대조** — 운영 스크립트와 같은 술어로 먼저 잡아 본다(검사 전용 이름으로 잡고 바로 놓는다).
_prod_project_lock "${PROJ}-probe" || _die "${_PROD_LOCK_ERR} — 이 검사의 전제다(판정 불가)" 2
_prod_unlock_all
rm -f "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}-probe.lock"

# ── fixture ────────────────────────────────────────────────────────────────────
REL=rel-guardcheck
PREV=rel-guardprev
REL_SHA=1111111111111111111111111111111111111111
PREV_SHA=2222222222222222222222222222222222222222
OTHER_SHA=3333333333333333333333333333333333333333

mkdir -p "$T/bin" "$T/state/releases" "$T/env"

_manifest() { # <release> <git_sha>
  local rel="$1" sha="$2" svc
  {
    echo "release=${rel}"
    echo "git_sha=${sha}"
    echo "created_kst=2026-11-05T00:00:00+0900"
    echo "provenance=source-tag:guards-check"
    echo "vuln=pass"
    for svc in "${BIDVECTOR_PROD_SERVICES[@]}"; do
      printf 'image.%s=sha256:%s\n' "$svc" "$(printf '%s%s' "$rel" "$svc" | sha256sum | cut -d' ' -f1)"
    done
    # 두 release 가 **같은** 마이그레이션 집합이어야 되돌림이 트리 축까지 간다(마이그레이션 축은
    # 이 slice 가 바꾸지 않는다 — 다르면 그쪽이 먼저 exit 3 으로 멈춘다).
    echo "migration=V1__init.sql"
  } > "$T/state/releases/${rel}.manifest"
}
_manifest "$REL" "$REL_SHA"
_manifest "$PREV" "$PREV_SHA"
printf '%s\n' "$REL" > "$T/state/current"
printf '%s\n' "$PREV" > "$T/state/previous"

# 운영 env 파일 — 키 집합은 템플릿이 단일 출처다(여기서 베껴 적지 않는다). 값은 자리값 하나.
awk -F= '/^[A-Z][A-Z0-9_]*=/ { print ($1 == "BIDVECTOR_RELEASE_TAG" ? $1 "=" : $1 "=x") }' \
  docker/prod.env.template > "$T/env/prod.env"
chmod 600 "$T/env/prod.env"
[ -s "$T/env/prod.env" ] || _die "운영 env fixture 가 비었다(템플릿을 읽지 못했다) — 판정 불가" 2

# 해석된 compose fixture — 서비스 env 키 집합도 `tools/prod-env.sh` 의 배열이 단일 출처다.
_cfg_json() { # <프로젝트 이름>
  local proj="$1" entry svc key prev=""
  printf '{"name":"%s","services":{' "$proj"
  for entry in $(printf '%s\n' "${BIDVECTOR_PROD_SERVICE_ENV_KEYS[@]}" | sort); do
    svc="${entry%%.*}"; key="${entry#*.}"
    if [ "$svc" != "$prev" ]; then
      [ -z "$prev" ] || printf '}},'
      printf '"%s":{"environment":{' "$svc"
      prev="$svc"
    else
      printf ','
    fi
    case "$key" in
      JAVA_TOOL_OPTIONS) printf '"%s":"-XX:MaxRAMPercentage=75"' "$key" ;;
      *)                 printf '"%s":"x"' "$key" ;;
    esac
  done
  printf '}}}}'
}
_cfg_json "$PROJ" > "$T/cfg.json"
_cfg_json "${PROJ}-elsewhere" > "$T/cfg-othername.json"
jq -e . "$T/cfg.json" >/dev/null || _die "compose config fixture 가 JSON 이 아니다 — 판정 불가" 2

# ── 가짜 docker · git ──────────────────────────────────────────────────────────
cat > "$T/bin/docker" <<'SHIM'
#!/usr/bin/env bash
# 가짜 docker — argv 를 기록하고 fixture 로 답한다(tools/prod-guards-check.sh 전용).
printf 'docker %s\n' "$*" >> "${SHIM_LOG:-/dev/null}"
ORIG=("$@")
if [ "${1:-}" = compose ]; then
  shift
  sub="" ffile=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -f) ffile="${2:-}"; shift 2 ;;
      -p|--env-file|--profile|--project-name) shift 2 ;;
      -*) shift ;;
      *) sub="$1"; shift; break ;;
    esac
  done
  case "$sub" in
    version) printf 'v%s\n' "${SHIM_COMPOSE_VER:-2.29.0}" ;;
    config)
      # **둘째 env 파일의 해석만은 진짜 compose 에 맡긴다**(M15/15Q 수정 라운드 1, v H-1 · M15/15U 부터
      # 래퍼 둘이 같은 술어를 쓴다 — 가르는 글자는 stub 의 프로젝트 이름이다). 래퍼는 그 파일의
      # 키 집합을 **소비자와 같은 해석기**로 뽑는데, 그 해석을 여기서 흉내 내면 단언이 재는 것은
      # compose 가 아니라 **이 가짜의 셋째 문법**이 된다 — 우회 줄이 거절되는지는 그러면 영영 미측정이다.
      # 그 호출은 stub 파일 하나를 읽는 client-side 렌더링이라 운영 스택·데몬에 닿지 않는다.
      # 진짜 자리는 환경에서 읽는다 — 래퍼가 평소 env 로 부르므로 그대로 건너온다(cr N-1 로
      # `env -i` 가 걷히면서 파일 우회도 함께 사라졌다).
      if [ -n "$ffile" ] && grep -q 'bidvector-second-env-check' "$ffile" 2>/dev/null; then
        [ -n "${SHIM_REAL_DOCKER:-}" ] || { echo "SHIM: 진짜 docker 자리를 찾지 못했다" >&2; exit 97; }
        # 신호 경로 단언용 — 해석이 도는 **창**을 연다(cr N-2).
        [ -z "${SHIM_CONFIG_SLEEP:-}" ] || sleep "$SHIM_CONFIG_SLEEP"
        exec "$SHIM_REAL_DOCKER" "${ORIG[@]}"
      fi
      [ -n "${SHIM_CONFIG_FILE:-}" ] && [ -r "${SHIM_CONFIG_FILE:-}" ] || exit 1
      cat "$SHIM_CONFIG_FILE" ;;
    run)
      # `run --help` — **플래그 지원 질의**(M15/15Q). relay 래퍼는 `--env-from-file` 을 판 번호가
      # 아니라 도구에게 직접 묻는다. `SHIM_NO_ENV_FROM_FILE` 이 그 음성 대조다(모르는 판 흉내).
      case " $* " in
        *" --help "*)
          # `SHIM_EMPTY_RUN_HELP` — 도움말을 **한 줄도** 못 받는 경우(cr L-6). 「플래그가 없다」와
          # 고칠 자리가 다르므로 래퍼가 다른 문면을 내야 한다.
          [ -z "${SHIM_EMPTY_RUN_HELP:-}" ] || exit 0
          [ -n "${SHIM_NO_ENV_FROM_FILE:-}" ] \
            || printf '      --env-from-file stringArray   Set environment variables from file\n'
          exit 0 ;;
      esac
      [ -z "${SHIM_RUN_MARKER:-}" ] || : > "$SHIM_RUN_MARKER"
      [ -z "${SHIM_RUN_SLEEP:-}" ] || sleep "$SHIM_RUN_SLEEP"
      exit "${SHIM_RUN_RC:-0}" ;;
  esac
  exit 0
fi
case "${1:-}" in
  image) # image inspect bidvector/<svc>:<tag> --format {{.Id}}
    ref="${3:-}"; svc="${ref#bidvector/}"; tag="${svc#*:}"; svc="${svc%%:*}"
    if [ "${SHIM_IMAGE_IDS_OK:-1}" = 1 ]; then
      id="$(sed -n "s/^image\.${svc}=//p" "${SHIM_STATE:-/dev/null}/releases/${tag}.manifest" 2>/dev/null)"
    else
      id="sha256:deadbeef"
    fi
    [ -n "$id" ] || exit 1
    printf '%s\n' "$id" ;;
  ps)
    [ "${SHIM_PS_RC:-0}" = 0 ] || exit "${SHIM_PS_RC}"
    all=0 projf="" namef=""
    for a in "$@"; do
      case "$a" in
        -a|-aq|-qa|--all) all=1 ;;
        label=com.docker.compose.project=*) projf="${a#label=com.docker.compose.project=}" ;;
        name=*) namef="${a#name=}" ;;
      esac
    done
    if [ -n "$namef" ]; then
      # 전역 이름 조회(`--filter name=^<이름>$`) — 프로젝트 라벨과 무관하다. **여기서도 `-a` 를 본다**:
      # 멈춘 동명 컨테이너도 이름을 쥐고 있어 `run` 이 「name already in use」로 죽는다.
      if [ -n "${SHIM_PS_NAME_HIT:-}" ] \
         && { [ "$all" = 1 ] || [ "${SHIM_PS_NAME_STATE:-running}" = running ]; }; then
        printf '%s\n' "$SHIM_PS_NAME_HIT"
      fi
      exit 0
    fi
    # fixture 행: <프로젝트>|<이름>|<oneoff 라벨>|<상태>. **`-a` 가 없으면 running 만 보인다** —
    # 실제 `docker ps` 와 같다(그래서 「멈춘 것까지 센다」가 이 가짜로 잠긴다).
    [ -z "${SHIM_PS_FILE:-}" ] || while IFS='|' read -r p n o s; do
      [ -n "$p" ] || continue
      [ "$p" = "$projf" ] || continue
      [ "$all" = 1 ] || [ "${s:-running}" = running ] || continue
      printf '%s|%s\n' "$n" "$o"
    done < "$SHIM_PS_FILE" ;;
  rm) : ;;  # 삭제 대상은 위 로그 한 줄이 담는다
  *) exit 0 ;;
esac
exit 0
SHIM
cat > "$T/bin/git" <<'SHIM'
#!/usr/bin/env bash
# 가짜 git — 반입 트리의 HEAD·더티만 흉내낸다(tools/prod-guards-check.sh 전용).
printf 'git %s\n' "$*" >> "${SHIM_LOG:-/dev/null}"
# **거꾸로 단언 하나**: 술어는 `GIT_*` 를 비운 채 git 을 불러야 한다. `GIT_DIR` 하나면 `-C` 를 이기고
# 다른 저장소의 HEAD·더티가 나온다(cr M-1 실측).
while IFS='=' read -r _k _; do
  case "$_k" in
    GIT_*) printf 'SHIM: %s 가 보인다 — 술어가 GIT_* 를 비우지 않았다\n' "$_k" >&2; exit 99 ;;
  esac
done < <(env)
# **거꾸로 단언 둘**: `-C <기대 루트>` 가 있어야 한다(cwd 에 기대지 않는다).
if [ "${1:-}" = -C ]; then
  if [ -n "${SHIM_GIT_EXPECT_C:-}" ] && [ "${2:-}" != "$SHIM_GIT_EXPECT_C" ]; then
    printf 'SHIM: git -C 가 기대 루트가 아니다(%s ≠ %s)\n' "${2:-}" "$SHIM_GIT_EXPECT_C" >&2
    exit 98
  fi
  shift 2
elif [ -n "${SHIM_GIT_EXPECT_C:-}" ]; then
  printf 'SHIM: git 에 -C 가 없다 — cwd 에 기댄다\n' >&2
  exit 98
fi
case "${1:-}" in
  rev-parse)
    [ -n "${SHIM_GIT_HEAD:-}" ] || exit 128
    printf '%s\n' "$SHIM_GIT_HEAD" ;;
  status)
    [ "${SHIM_GIT_STATUS_RC:-0}" = 0 ] || exit "${SHIM_GIT_STATUS_RC}"
    [ -z "${SHIM_GIT_DIRTY:-}" ] || printf '%s\n' "$SHIM_GIT_DIRTY" ;;
  *) exit 0 ;;
esac
exit 0
SHIM
# **가짜 `flock`** — `SHIM_FLOCK_RC` 가 있을 때만 그 코드로 끝난다(v F1 의 회귀 단언용). 없으면 진짜
# flock 으로 exec 하므로 나머지 단언은 실제 잠금으로 돈다(가짜가 켜져 있지 않다는 증인은 아래
# 신호 경로 둘이다 — 그 갈래는 잠금을 실제로 잡아야 끝까지 간다).
cat > "$T/bin/flock" <<'SHIM'
#!/bin/sh
[ -z "${SHIM_FLOCK_RC:-}" ] || exit "${SHIM_FLOCK_RC}"
exec "$SHIM_REAL_FLOCK" "$@"
SHIM
chmod +x "$T/bin/docker" "$T/bin/git" "$T/bin/flock"
export PATH="$T/bin:$PATH"
export SHIM_REAL_FLOCK="$REAL_FLOCK" SHIM_FLOCK_RC="" SHIM_REAL_DOCKER="$REAL_DOCKER"

SHIM_LOG="$T/shim.log"
export SHIM_LOG SHIM_STATE="$T/state" SHIM_GIT_EXPECT_C="$REPO_ROOT"
export SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY="" SHIM_GIT_STATUS_RC=0
export SHIM_PS_FILE="" SHIM_PS_RC=0 SHIM_PS_NAME_HIT="" SHIM_CONFIG_FILE="" SHIM_IMAGE_IDS_OK=1
export SHIM_RUN_MARKER="" SHIM_RUN_SLEEP="" SHIM_RUN_RC=0 SHIM_NO_ENV_FROM_FILE="" SHIM_EMPTY_RUN_HELP="" SHIM_CONFIG_SLEEP=""

# ── 공통 단언 ──────────────────────────────────────────────────────────────────
RUN_RC=0
_run() { # <명령…>
  : > "$SHIM_LOG"
  RUN_RC=0
  "$@" > "$T/out" 2> "$T/err" || RUN_RC=$?
}
_expect_rc() { # <기대> <이름>
  if [ "$RUN_RC" = "$1" ]; then _ok "$2 (exit ${RUN_RC})"
  else _bad "$2 — exit ${RUN_RC} ≠ $1 · 마지막 줄: $(tail -n1 "$T/err" 2>/dev/null)"; fi
}
_expect_text() { # <정규식> <이름>
  if cat "$T/out" "$T/err" | grep -qE -- "$1"; then _ok "$2"; else _bad "$2 — 문면에 /$1/ 가 없다"; fi
}
_expect_no_text() { # <정규식> <이름>
  if cat "$T/out" "$T/err" | grep -qE -- "$1"; then _bad "$2 — 문면에 /$1/ 가 있다"; else _ok "$2"; fi
}
_expect_log() { # <정규식> <이름>
  if grep -qE -- "$1" "$SHIM_LOG"; then _ok "$2"; else _bad "$2 — 가짜 docker 가 /$1/ 를 받지 않았다"; fi
}
_expect_no_log() { # <정규식> <이름>
  if grep -qE -- "$1" "$SHIM_LOG"; then _bad "$2 — 가짜 docker 가 /$1/ 를 받았다"; else _ok "$2"; fi
}

echo "== 검사 자신의 전제 — 운영 잠금 자리를 만들지 않는다"
# **호출 경로로 잰다**(함수 직접 호출이 아니라 스크립트 실행) — 그래야 호출 줄이 빠지는 변이가 잡힌다.
BV_GUARDS_SELFTEST_LOCK_DIR="$T/no-such-lock-dir" _run ./tools/prod-guards-check.sh
_expect_rc 2 "잠금 자리가 없으면 판정 불가로 끝난다"
_expect_text '운영 자리를 만들지 않는다' "문면이 사유와 고치는 명령을 보인다"
_expect_no_text '잠금 자리 점검만 하고 끝낸다' "자리 점검을 건너뛰고 끝나지 않는다"
if [ -e "$T/no-such-lock-dir" ]; then _bad "그 자리를 만들었다"; else _ok "그 자리를 만들지 않았다"; fi

echo "== 코드의 살아 있는 백틱 — 인용 상태를 따라가는 스캐너 (M15/15V r2, vr M-a · cr L-2)"
# 큰따옴표 안의 백틱 짝은 **명령 치환**이다. r1 이 더한 네 문면이 그 꼴이었고, 실측으로 **가짜 `sudo` 가
# 두 번 실행됐고** 그 단어가 출력에서 사라졌다. `bash -n` 은 백틱 안을 파싱 시점에 보지 않아 rc 0 이다.
#
# r1 의 래칫은 모집단이 **열거**였다 — 호출 이름(`_ok|_bad|_die|_expect_*`) 뒤에 공백 + 큰따옴표. 그래서
# 실측된 미탐이 셋이었다(전부 가짜 `sudo` 가 실제로 실행되고 GREEN): `_expect_text '…' "… 설명 …"` 의
# **둘째** 인자(그 꼴이 가드에 97 줄) · 래퍼의 `echo "collection: …"`(비주석 6 줄) · `|| _die \` 다음 줄의
# 문면. 분류를 더 정교하게 하는 처방은 그 분류가 다시 열거라 같은 자리를 다시 연다.
#
# 그래서 **구성**으로 바꾼다: 두 스크립트의 **모든 줄**을 셸 인용 상태를 따라가며 훑고, 살아 있는 백틱
# (단일 인용 밖 · 주석 밖 · 탈출 아님)이 **0** 인지 본다. `$( )` 치환은 이 저장소의 의도된 관용구라
# (모집단에 22 줄) 금지하지 않는다 — 사고 경로인 백틱만 닫는다.
#
# **받는 꼴을 정하고 나머지는 정지시킨다.** 상태는 줄을 넘어 이어진다(이 파일의 awk 프로그램과
# `"$(printf … \` 꼴이 여러 줄에 걸친다). 줄 끝에서 인용이 닫히지 않은 채 **파일이 끝나면** STOP 이고,
# heredoc 의 꼴이 `<<'DELIM'`·`<<DELIM` 가 아니어도 STOP 이다 — 조용히 빠지는 가지를 두지 않는다.
# 인용 heredoc 본문은 **그 자체로 셸 프로그램**이라(이 파일의 heredoc 은 실행되는 가짜를 만든다) 새
# 상태로 따로 훑는다. 비인용 heredoc 본문은 백틱이 인용과 무관하게 살아 있으므로 글자로 본다.
#
# **비공허는 줄 수가 아니라 대조로 잰다**(cr L-2: r1 의 문턱은 `-ge 1` 인데 실값이 216 이었다 — 추출이
# 99.5% 무너져도 초록이다). 스캐너를 **양성 probe**(미탐 셋을 그대로 심은 fixture)와 **음성 probe**
# (오탐 셋: 줄 끝 주석 · 단일 인용 · awk 프로그램 안 · `${0##*/}` · 여러 줄 연속)에 돌려 둘 다 잰다.
cat > "$T/backtick-scan.awk" <<'BTSCAN'
BEGIN { SQ = "'"; DQ = "\""; BT = sprintf("%c", 96); sp = 0; bsp = 0; hd = ""; hdraw = 0 }
function ctx(st, n) { return n > 0 ? st[n] : "B" }
function hit(ln, why) { mark = mark FILENAME ":" ln why " " }
function walk(s, st, sp, ln,    i, c, n, cx, esc, prev, rest) {
  esc = 0; prev = ""; n = length(s)
  for (i = 1; i <= n; i++) {
    c = substr(s, i, 1); cx = ctx(st, sp)
    if (esc)                    { esc = 0; prev = c; continue }
    if (c == "\\" && cx != "S") { esc = 1; prev = c; continue }
    if (cx == "S")              { if (c == SQ) sp--; prev = c; continue }
    if (c == BT)                { live++; hit(ln, ""); prev = c; continue }
    if ((cx == "B" || cx == "C") && c == "#" && (i == 1 || prev == " " || prev == "\t")) return sp
    if (c == SQ && cx != "D")   { st[++sp] = "S"; prev = c; continue }
    if (c == "$" && substr(s, i + 1, 1) == "(") { st[++sp] = "C"; i++; prev = "("; continue }
    if (c == DQ)                { if (cx == "D") sp--; else st[++sp] = "D"; prev = c; continue }
    if (c == ")" && cx == "C")  { sp--; prev = c; continue }
    if (c == "<" && substr(s, i + 1, 1) == "<" && (cx == "B" || cx == "C")) {
      rest = substr(s, i + 2)
      if (rest ~ /^[<(]/) { i += 2; prev = "<"; continue }
      if (match(rest, /^-?'[A-Za-z_][A-Za-z0-9_]*'/)) {
        hd = substr(rest, RSTART, RLENGTH); sub(/^-?'/, "", hd); sub(/'$/, "", hd); hdraw = 0
      } else if (match(rest, /^-?[A-Za-z_][A-Za-z0-9_]*/)) {
        hd = substr(rest, RSTART, RLENGTH); sub(/^-?/, "", hd); hdraw = 1
      } else { stop++; hit(ln, "(heredoc 꼴)"); prev = "<"; continue }
      i += 1 + RLENGTH; prev = SQ; continue
    }
    prev = c
  }
  return sp
}
hd != "" {
  t = $0; sub(/^[[:space:]]+/, "", t)
  if (t == hd) {
    if (bsp > 0) { stop++; hit(FNR, "(heredoc 본문 인용 미닫힘)") }
    hd = ""; bsp = 0; next
  }
  scanned++
  if (hdraw) { if (index($0, BT) > 0) { live++; hit(FNR, "(비인용 heredoc)") } ; next }
  bsp = walk($0, bst, bsp, FNR)
  next
}
{ scanned++; sp = walk($0, st, sp, FNR) }
END {
  if (sp > 0)   { stop++; hit("EOF", "(인용 미닫힘)") }
  if (hd != "") { stop++; hit("EOF", "(heredoc 미닫힘)") }
  printf "SCANNED %d\n", scanned
  if (live > 0) printf "LIVE %d %s\n", live, mark
  if (stop > 0) printf "STOP %d %s\n", stop, mark
}
BTSCAN
BACKTICK_FILES=(tools/prod-collection.sh tools/prod-guards-check.sh)
BACKTICK_FLOOR=2500   # 실값 2805(위 둘의 줄 수 − heredoc 종단자). 줄어들면 그 사유를 적고 함께 내린다.
_bt_scan() { # <파일…> — 파일마다 새 상태로
  local _f
  for _f in "$@"; do awk -f "$T/backtick-scan.awk" "$_f" || true; done
}
_bt_num() { printf '%s\n' "$2" | awk -v k="$1" '$1 == k { s += $2 } END { print s + 0 }'; }
_bt_where() { printf '%s\n' "$2" | awk -v k="$1" '$1 == k { for (i = 3; i <= NF; i++) print $i }' \
  | LC_ALL=C sort -u | tr '\n' ' '; }
# 양성 probe — 미탐 셋을 그대로 심는다. 백틱은 **런타임에 끼운다**: 이 파일에 축어로 적으면 스캐너가
# 자기 fixture 를 집어 영원히 붉다(그 꼴을 예외로 빼면 그 예외가 구멍이다).
BT_CH="$(printf '\140')"   # 8진 140 = 백틱. `printf %c 96` 은 인자의 첫 글자(9)를 낸다 — 양성 대조가 그 실수를 잡았다.
{ printf '%s\n' "_expect_text 'p' \"설명 ${BT_CH}sudo -n true${BT_CH} 끝\""
  printf '%s\n' "echo \"collection: ${BT_CH}sudo -n true${BT_CH} 거절\""
  printf '%s\n' '[ -n "$x" ] \'
  printf '%s\n' '  || _die \'
  printf '%s\n' "    \"root 에 ${BT_CH}sudo -n true${BT_CH} setgid 가 없다\" 3"
} > "$T/bt-probe-live.sh"
# 음성 probe — 오탐 꼴 다섯. 여기는 **축어**로 적는다(이 블록 자신이 그 꼴들을 담고도 0 이어야 한다는
# 진술이고, 가드를 훑을 때 이 본문도 같은 스캐너를 지난다).
cat > "$T/bt-probe-dead.sh" <<'BTDEAD'
_die "실행 상태 root 를 읽지 못했다" 3  # `stat` 실패
x='literal `backtick` in single quotes'
y="$(awk '/^```$/ {inb = 1} inb {print}' f)"
z="${0##*/}"
w="$(printf '%s\n' 'a' \
  'b' | sort)"
BTDEAD
bt_live_probe="$(_bt_scan "$T/bt-probe-live.sh")"
bt_dead_probe="$(_bt_scan "$T/bt-probe-dead.sh")"
bt_pos="$(_bt_num LIVE "$bt_live_probe")"
bt_neg="$(( $(_bt_num LIVE "$bt_dead_probe") + $(_bt_num STOP "$bt_dead_probe") ))"
if [ "$bt_pos" -ge 3 ] && [ "$bt_neg" = 0 ]; then
  _ok "백틱 스캐너의 양성·음성 대조가 선다 — 미탐 셋을 심은 probe 에서 ${bt_pos} 자를 집고(둘째 인자·echo 문면·연속 줄), 오탐 꼴 다섯(줄 끝 주석·단일 인용·awk 프로그램 안·매개변수 확장·여러 줄 연속)에서 0 이다"
else
  _bad "백틱 스캐너의 대조가 어긋났다 — 양성 ${bt_pos} 자(3 이상이어야) · 음성 ${bt_neg}(0 이어야). 스캐너가 죽었으면 아래 「0 이다」는 공허하다"
fi
bt_out="$(_bt_scan "${BACKTICK_FILES[@]}")"
bt_scanned="$(_bt_num SCANNED "$bt_out")"
if [ "${#BACKTICK_FILES[@]}" = 2 ] && [ "$bt_scanned" -ge "$BACKTICK_FLOOR" ]; then
  _ok "백틱 스캐너의 모집단이 두 in_scope 스크립트의 ${bt_scanned} 줄이다(하한 ${BACKTICK_FLOOR}) — 주석·문면 자리만이 아니라 **모든 줄**이다"
else
  _bad "백틱 스캐너의 모집단이 무너졌다 — 파일 ${#BACKTICK_FILES[@]} 개(2 이어야) · 훑은 줄 ${bt_scanned}(하한 ${BACKTICK_FLOOR}). 파일을 빼거나 크게 줄였으면 열거와 하한을 함께 고친다"
fi
bt_live="$(_bt_num LIVE "$bt_out")"
bt_stop="$(_bt_num STOP "$bt_out")"
if [ "$bt_live" = 0 ] && [ "$bt_stop" = 0 ]; then
  _ok "두 스크립트에 살아 있는 백틱이 0 이다(단일 인용·주석·탈출 밖의 백틱은 명령 치환이다 — 문면을 짜는 자리가 명령을 돌리는 자리가 된다)"
else
  _bad "살아 있는 백틱 ${bt_live} 자 · 스캐너가 받지 않는 꼴 ${bt_stop} 군데: $(_bt_where LIVE "$bt_out")$(_bt_where STOP "$bt_out")— 백틱은 **실행된다**. 걷거나 탈출한다(걷는 쪽이 읽기에 낫다). STOP 은 인용이 파일 끝까지 닫히지 않았거나 heredoc 꼴이 받는 둘 밖이라는 뜻이다"
fi
unset bt_out bt_live bt_stop bt_scanned bt_pos bt_neg bt_live_probe bt_dead_probe BT_CH BACKTICK_FLOOR

echo "== prod-env.sh(M15/15R · S-8) — 데몬 선택 축 셋을 닫는다"
# `COMPOSE_*`·`GIT_*` 는 접두로 비우면서 `DOCKER_HOST`·`DOCKER_CONTEXT` 는 남아 있었다. 그 둘은 세 래퍼의
# **모든 docker 호출**이 어느 데몬을 보는지를 환경 문자열 하나로 바꾼다 — §13.4 ①~⑤ 는 운영자 셸에서
# 손으로 미는 절차이고, 그 셸이 다른 context 를 쥐고 있을 수 있다. 오늘도 fail-closed 이긴 하다
# (남의 데몬에서는 이미지 등식이 `exit 3` 에서 선다) — 그 거절이 **데몬을 잘못 골랐다**는 이야기를
# 하지 않는 것이 비용이다.
#
# **`DOCKER_CONFIG`·`HOME` 은 건드리지 않는다**(cr N-1 이 되살린 자리 — 떨어뜨리면 compose 가 사용자
# 플러그인으로 깔린 host 에서 매 run 이 「통째로 버렸다」로 끝난다). 그래서 이 단언의 **음성 대조는
# 같은 줄 안에 있다**: `DOCKER_CONFIG` 가 살아남는지를 함께 본다(접두로 비우는 처방이면 여기서 선다).
#
# **셋째 축**(v F6): 두 변수를 비워도 `DOCKER_CONFIG` 아래 `config.json` 의 `currentContext` 가 남는다
# (`docker context use` 가 쓰는 자리). 그래서 `DOCKER_CONTEXT` 는 **비우는 것이 아니라 내장 `default` 로
# 못박는다** — 그 변수가 `currentContext` 를 이기므로 플러그인 탐색 축을 건드리지 않고 닫힌다.
# 여기서 `default` **값까지** 단언한다: 그냥 비우는 앞 판으로 되돌리면 `exit 12` 에서 선다.
RUN_RC=0
(
  export DOCKER_HOST=tcp://127.0.0.1:1 DOCKER_CONTEXT=elsewhere DOCKER_CONFIG="$T/dockercfg"
  # shellcheck source=tools/prod-env.sh
  . "$REPO_ROOT/tools/prod-env.sh"
  [ -z "${DOCKER_HOST:-}" ] || exit 11
  [ "${DOCKER_CONTEXT:-}" = default ] || exit 12
  [ "${DOCKER_CONFIG:-}" = "$T/dockercfg" ] || exit 13
) > "$T/out" 2> "$T/err" || RUN_RC=$?
_expect_rc 0 "source 뒤 DOCKER_HOST 는 비고 DOCKER_CONTEXT 는 default 로 못박히고 DOCKER_CONFIG 는 남는다(11 안 비움 · 12 못박기 없음 · 13 과잉)"

echo "== L-1 술어 — 반입 트리 ↔ manifest git_sha"
MAN="$T/state/releases/${REL}.manifest"
MAN_ARG="$MAN"
_predicate_git() { # <HEAD> <dirty> [추가 export…] — manifest 는 $MAN_ARG
  RUN_RC=0
  : > "$SHIM_LOG"
  ( export SHIM_GIT_HEAD="$1" SHIM_GIT_DIRTY="$2"
    shift 2
    [ "$#" -eq 0 ] || export "$@"
    _prod_git_sha_match "$MAN_ARG" || { printf '%s\n' "$_PROD_GIT_SHA_ERR" >&2; exit 1; }
  ) > "$T/out" 2> "$T/err" || RUN_RC=$?
}
_predicate_git "$REL_SHA" ""
_expect_rc 0 "양성 — HEAD == git_sha · 추적 파일 깨끗이면 통과"
_predicate_git "$OTHER_SHA" ""
_expect_rc 1 "음성 — 다른 커밋의 트리는 거절"
_expect_text "checkout ${REL_SHA}" "음성 — 문면이 필요한 checkout 커밋을 보인다"
_predicate_git "$REL_SHA" " M tools/prod-env.sh"
_expect_rc 1 "음성 — 추적 파일이 더러우면 거절(커밋 등식만으로는 트리가 같지 않다)"
_expect_text '커밋되지 않은 변경이 1개' "음성 — 더티 계수를 문면에 보인다"
MAN_ARG="$T/nogitsha.manifest"
printf 'release=x\n' > "$MAN_ARG"
_predicate_git "$REL_SHA" ""
_expect_rc 1 "음성 — manifest 에 git_sha 가 없으면 거절(없음을 통과로 읽지 않는다)"
MAN_ARG="$MAN"
_predicate_git "" ""
_expect_rc 1 "음성 — HEAD 를 읽지 못하면 거절(git 부재를 통과로 읽지 않는다)"
# **GIT_* 축** — 소스 **뒤에** 들어온 값도 술어를 이기지 못해야 한다. 가짜 git 이 그 환경을 보면
# exit 99 로 거절하므로, 통과(exit 0)가 곧 「술어가 비웠다」의 실측이다.
_predicate_git "$REL_SHA" "" GIT_DIR="$T/elsewhere.git" GIT_WORK_TREE="$T" GIT_INDEX_FILE="$T/idx"
_expect_rc 0 "양성 — GIT_DIR·GIT_WORK_TREE·GIT_INDEX_FILE 이 있어도 트리 묶기가 산다"

echo "== L-2 술어 — 일회 컨테이너 잔존"
_predicate_oneoff() { # <ps fixture 내용> <ps rc>
  printf '%s' "$1" > "$T/ps"
  RUN_RC=0
  : > "$SHIM_LOG"
  ( export SHIM_PS_FILE="$T/ps" SHIM_PS_RC="$2"
    _prod_oneoff_residue "$PROJ" || { printf '%s\n' "$_PROD_ONEOFF_ERR" >&2; exit 1; }
  ) > "$T/out" 2> "$T/err" || RUN_RC=$?
}
_predicate_oneoff "" 0
_expect_rc 0 "양성 — 잔존이 없으면 통과"
_predicate_oneoff "${PROJ}|${PROJ}-app-1|False|running
" 0
_expect_rc 0 "양성 — 떠 있는 서비스 컨테이너는 잔존이 아니다"
_predicate_oneoff "${PROJ}|${PROJ}-operator-once|True|running
" 0
_expect_rc 1 "음성 — oneoff 라벨 축으로 잡는다(이름이 규칙 밖이어도)"
_expect_text "${PROJ}-operator-once" "음성 — 문면이 치울 이름을 보인다"
_predicate_oneoff "${PROJ}|${PROJ}-app-run-9f2c||running
" 0
_expect_rc 1 "음성 — 이름 축으로 잡는다(라벨이 비어도)"
# **멈춘 잔존** — 신호·KILL 로 남는 컨테이너는 Exited 다. 가짜 ps 는 `-a` 가 없으면 이 행을 숨긴다.
_predicate_oneoff "${PROJ}|${PROJ}-app-run-dead|True|exited
" 0
_expect_rc 1 "음성 — **멈춘** 일회 컨테이너도 센다(docker ps -a)"
_expect_text "${PROJ}-app-run-dead" "음성 — 멈춘 것의 이름을 보인다"
_predicate_oneoff "other-proj|other-proj-app-run-zzz|True|running
" 0
_expect_rc 0 "양성 — 남의 프로젝트 일회 컨테이너는 세지 않는다"
_predicate_oneoff "" 1
_expect_rc 1 "음성 — 조회가 실패하면 「잔존 0」으로 읽지 않는다"

echo "== 반입(prod-deploy.sh) — 트리 축이 preflight 에 서 있는가"
DEPLOY_ARGS=(--env-file "$T/env/prod.env" --state-dir "$T/state" --release "$REL" --project "$PROJ")
export SHIM_CONFIG_FILE=""   # 트리 축을 지나면 다음 단계(compose 해석)에서 멈춘다
SHIM_GIT_HEAD="$OTHER_SHA" _run ./tools/prod-deploy.sh "${DEPLOY_ARGS[@]}"
_expect_rc 1 "다른 커밋의 트리에서는 반입하지 않는다"
_expect_text '반입 트리가 이 release 를 만든 커밋이 아니다' "거절 사유가 트리 축이다"
_expect_no_log ' config ' "거절이 compose 해석 앞에서 끝난다"
SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY=" M docker/compose.prod.yaml" _run ./tools/prod-deploy.sh "${DEPLOY_ARGS[@]}"
_expect_rc 1 "커밋은 같지만 더러운 트리에서는 반입하지 않는다"
SHIM_GIT_HEAD="$REL_SHA" _run ./tools/prod-deploy.sh "${DEPLOY_ARGS[@]}"
_expect_text '반입 트리: HEAD == manifest git_sha' "양성 — 같은 커밋·깨끗한 트리는 통과한다"
_expect_text '운영 compose 를 해석하지 못했다' "양성 — 통과 뒤 다음 preflight 단계에 닿는다"
# `GIT_*` 가 **소스 시점부터** 있는 경우 — `tools/prod-env.sh` 의 unset 루프가 그 자리다.
SHIM_GIT_HEAD="$REL_SHA" GIT_DIR="$T/elsewhere.git" GIT_WORK_TREE="$T" _run ./tools/prod-deploy.sh "${DEPLOY_ARGS[@]}"
_expect_text '반입 트리: HEAD == manifest git_sha' "양성 — 환경에 GIT_* 가 있어도 반입의 트리 축이 산다"

echo "== 되돌림(prod-rollback.sh) — 되돌릴 release 의 트리를 요구하는가"
ROLLBACK_ARGS=(--env-file "$T/env/prod.env" --state-dir "$T/state" --project "$PROJ")
SHIM_GIT_HEAD="$REL_SHA" _run ./tools/prod-rollback.sh "${ROLLBACK_ARGS[@]}"
_expect_rc 2 "새 release 의 트리에서는 되돌리지 않는다"
_expect_text "checkout ${PREV_SHA}" "문면이 되돌릴 release 의 커밋을 보인다(스크립트가 checkout 하지 않는다)"
_expect_text "되돌릴 release 는 ${PREV}" "문면이 되돌릴 release 를 보인다"
_expect_no_text '== preflight' "거절이 반입 호출 앞에서 끝난다"
SHIM_GIT_HEAD="$PREV_SHA" _run ./tools/prod-rollback.sh "${ROLLBACK_ARGS[@]}"
_expect_text "되돌릴 트리: HEAD == ${PREV} 의 git_sha" "양성 — 그 release 의 트리면 통과한다"
_expect_text "== preflight — release ${PREV}" "양성 — 반입으로 넘어가고 그쪽 트리 축도 통과한다"

echo "== 주기 러너(prod-notification-sweep.sh) — 트리·프로젝트·잔존·이름·trap"
SWEEP_ARGS=(--env-file "$T/env/prod.env" --state-dir "$T/state" --project "$PROJ")
export SHIM_CONFIG_FILE="$T/cfg.json" SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY=""
SHIM_GIT_HEAD="$OTHER_SHA" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "다른 커밋의 트리에서는 훑지 않는다"
_expect_no_log ' run ' "거절이 일회 컨테이너 앞에서 끝난다"
# 해석된 프로젝트 ≠ 잠금을 잡은 프로젝트 — 잠금과 잔존·일회 이름이 다른 자리를 보게 된다.
SHIM_CONFIG_FILE="$T/cfg-othername.json" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "해석된 프로젝트 이름이 잠금과 다르면 훑지 않는다"
_expect_no_log 'docker ps' "그 거절이 잔존 조회 앞에서 끝난다"
printf '%s|%s-app-run-abc|True|exited\nother-proj|other-proj-app-run-zzz|True|running\n' "$PROJ" "$PROJ" > "$T/ps-residue"
SHIM_PS_FILE="$T/ps-residue" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "지난 run 의 (멈춘) 일회 컨테이너가 남아 있으면 훑지 않는다"
_expect_text "${PROJ}-app-run-abc" "거절 문면이 남은 이름을 보인다"
_expect_no_log ' run ' "거절이 일회 컨테이너 앞에서 끝난다(잔존 축)"
printf '%s|%s-app-1|False|running\nother-proj|other-proj-app-run-zzz|True|running\n' "$PROJ" "$PROJ" > "$T/ps-clean"
export SHIM_PS_FILE="$T/ps-clean"
# 이름 공간은 **전역**이다 — 같은 이름이 이 프로젝트 밖에 있으면 `run` 의 rc 가 러너의 「1 예외」와 섞인다.
SHIM_PS_NAME_HIT="deadbeefcafe" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "일회 컨테이너 이름이 전역에서 이미 쓰이면 훑지 않는다"
_expect_no_log ' run ' "그 거절이 compose run 앞에서 끝난다"
# **멈춘** 동명 컨테이너도 이름을 쥔다 — 그래서 충돌 조회는 `-aq` 여야 한다(v r2 F-3).
SHIM_PS_NAME_HIT="deadbeefcafe" SHIM_PS_NAME_STATE=exited _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "**멈춘** 동명 컨테이너가 있어도 훑지 않는다(docker ps -aq)"
_expect_no_log ' run ' "그 거절도 compose run 앞에서 끝난다"
_run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 0 "양성 — 잔존 0 · 같은 커밋 · 같은 프로젝트면 훑는다"
_expect_log "run .*--rm .*--pull never .*--name ${PROJ}-app-run-sweep" "일회 컨테이너 이름을 고정해 띄운다"
# M15/15Q cr H-1 — **값 없는 플래그**는 전제 실패(3)다. 앞 판은 `shift 2` 가 `set -e` 아래에서 스크립트를
# **1 로, 아무 말 없이** 죽였고 그 `1` 은 러너의 「예외」와 같은 값이라 경보가 틀린 이야기를 했다.
# 도달 경로는 runbook 의 `ExecStart=` 자신이다(자리표시자를 바꾸지 않고 **지운** 운영자).
_run ./tools/prod-notification-sweep.sh --env-file
_expect_rc 3 "훑기 — 값 없는 플래그는 전제 실패(3)다 — 러너의 1 과 섞이지 않는다"
_expect_text '값이 없다' "훑기 — 그 거절이 문면을 남긴다(앞 판은 조용히 죽었다)"

# M15/15M(cr H-1) — **미룸은 4, 전제 실패는 3.** 둘이 같은 코드면 §9.5 의 실패 경보가 반입·되돌림·손
# 실행 때마다 진짜 사건과 같은 문면으로 울린다. 위 양성 대조(`_expect_rc 0`)가 **잠금이 비어 있을 때**
# 를 이미 쟀으므로, 아래 둘은 「잡혀 있을 때만 4」를 가른다. 위의 `_expect_rc 3` 들이 음성 대조다 —
# 전제 실패는 여전히 3 이다.
_hold_lock() { # <잠금 파일> — 이 검사 셸이 쥐고, 호출자가 `_release_lock` 으로 놓는다
  exec {HOLD_FD}>>"$1" || _die "경합 대조의 잠금 파일을 열지 못했다: $1 — 판정 불가" 2
  flock -n "$HOLD_FD" || _die "경합 대조의 잠금을 잡지 못했다: $1(남이 쥐고 있다) — 판정 불가" 2
}
_release_lock() { eval "exec ${HOLD_FD}>&-" 2>/dev/null || true; }

_hold_lock "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock"
_run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 4 "프로젝트 잠금이 잡혀 있으면 **미룬다**(4) — 전제 실패(3)가 아니다"
_expect_no_log ' run ' "미룸이 일회 컨테이너 앞에서 끝난다"
_release_lock

_hold_lock "$T/state/.lock"
_run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 4 "상태 디렉터리 잠금이 잡혀 있어도 **미룬다**(4)"
_expect_no_log ' run ' "그 미룸도 일회 컨테이너 앞에서 끝난다"
_release_lock
# 공유 잠금 **파일은 지우지 않는다**(cr N-5) — 놓은 직후 남이 잡으면 그 inode 를 지워 상호배제가
# 깨지는 좁은 창이 있다. `_prod_project_lock` 이 보는 것은 **디렉터리**의 소유자·권한이므로 검사
# 전용 이름의 파일이 남아도 무해하다(끝의 `_cleanup` 이 정리한다).

# **음성 대조 — 「경합일 때만 4」의 반대쪽**(cr N-1 · v F1). 양성만 재면
# `_PROD_LOCK_BUSY=1` 을 함수 진입부에 놓는 변이가 통째로 초록이다(실측: 라운드 1 에서 65/65).
# 위의 `_expect_rc 3` 들은 잠금 경로에 들어가지도 않으므로 그 축을 가르지 못한다.
#
# ⓐ **잠금 파일을 열지 못한다** — 상태 디렉터리는 이 검사가 소유하므로 안전하게 쓰기를 막는다.
# 기존 `.lock` 이 있으면 append 가 열리므로 **먼저 지우고** 디렉터리를 r-x 로 바꾼다(그래야 생성이
# 막힌다). 읽기는 열려 있어 `current`·manifest 는 그대로 읽힌다.
rm -f "$T/state/.lock"
chmod 500 "$T/state"
_run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "잠금 **파일을 열지 못하면** 전제 실패(3)다 — 미룸(4)이 아니다"
chmod 700 "$T/state"

# ⓑ **경합이 아닌 `flock` 실패** — 0 이 아닌 종료를 전부 경합으로 보면 `SuccessExitStatus=4` 가
# 그 실패를 영구히 삼킨다. 가짜 flock 으로 **다섯 꼴**을 민다. 둘(65·127)만 재던 앞 판은
# 「65·127 이 아니면 busy」 같은 **열거형 회귀**가 초록이었다(v R2-3 실측) — 그래서 옛 기본 경합 코드
# `1`, 사용법·`-E` 미지원의 `64`, 그리고 **앞 판이 경합 코드로 쓰던 `70`** 을 더한다. 마지막 하나가
# 중요하다: 코드를 sysexits 밖으로 옮긴 뒤에도 옛 값이 busy 로 서면 그 이사가 헛되다.
#
# **`137`(=128+9, 신호로 죽음)은 위쪽을 닫는다**(v R3-3). 대조 값이 전부 `≤127` 이면 「`rc ≥ 128` 이면
# busy」 같은 회귀가 초록이다 — 경합 코드(200)가 그 범위 안이라 공허한 대조가 아니다.
for frc in 1 64 65 70 127 137; do
  SHIM_FLOCK_RC="$frc" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
  _expect_rc 3 "flock 종료 ${frc} 는 전제 실패(3) — 경합으로 접지 않는다"
done

# ⓒ **`_prod_project_lock` 의 이른 return 갈래** — 잠금 **자리**의 소유자·권한이 어긋난 경우다. 그 갈래는
# `_prod_lock` 을 부르기도 전에 돌아서므로 위 ⓐ·ⓑ 로는 가를 수 없다. 실측: `_PROD_LOCK_BUSY=1` 을
# 그 함수 진입부에 놓는 변이가 ⓐ·ⓑ 를 포함한 68 건을 **그대로 초록**으로 통과한다.
#
# 운영 자리(`/run/lock/bidvector-v2`)는 **건드리지 않는다**(이 파일의 머리말 — 모드를 잠깐 바꾸는 것도
# 같은 자리를 쓰는 반입·되돌림과 겹친다). 대신 **술어를 직접 몰아** 버릴 자리에서 잰다: 호출 경로의
# 종료 코드 매핑(`_deferred_or_precondition`)은 ⓐ·ⓑ 와 위 경합 둘이 이미 잠갔으므로, 여기서 닫을 것은
# 「이 갈래에서 busy 가 서지 않는다」 하나다.
_probe_lock_dir() { # <자리> → 0 획득 · 8 거절인데 busy 가 섰다 · 9 거절이고 busy 가 아니다
  (
    BIDVECTOR_PROD_LOCK_DIR="$1"
    _prod_project_lock "${PROJ}-probe2" >/dev/null 2>&1 && exit 0
    [ -n "${_PROD_LOCK_BUSY:-}" ] && exit 8
    exit 9
  )
}
mkdir -p "$T/lockdir-ok" "$T/lockdir-gw"
chmod 755 "$T/lockdir-ok"
chmod 775 "$T/lockdir-gw"   # group 쓰기 — 잠금 파일을 지워 우회할 수 있는 자리다
RUN_RC=0; _probe_lock_dir "$T/lockdir-ok" || RUN_RC=$?
_expect_rc 0 "양성 — 소유·권한이 맞는 자리에서는 잠금을 잡는다(술어가 전부 거절하지 않는다)"
RUN_RC=0; _probe_lock_dir "$T/lockdir-gw" || RUN_RC=$?
_expect_rc 9 "잠금 **자리**의 group 쓰기가 열려 있으면 거절하고 **미룸이 아니다**(busy 가 서지 않는다)"

# 신호 경로 — trap 이 **자기 일회 컨테이너만** 치운다.
# 스크립트·인자·일회 이름은 전역으로 받는다(M15/15Q 에서 러너 셋이 같은 갈래를 쓴다 — 배열을 인자로
# 넘기지 않고 호출 전에 세운다). 호출 직전에 셋을 세운다(cr L-5: 정의 앞에도 두면 뒤의 같은 대입에
# 곧바로 덮여, 함수가 정의 시점에 묶는 것처럼 읽힌다).
_signal_case() { # <신호> <기대 종료 코드>
  local sig="$1" want="$2" pid pg i n oneoff="${PROJ}-app-run-${SIG_ONEOFF_SUFFIX}"
  : > "$SHIM_LOG"
  rm -f "$T/marker"
  RUN_RC=0
  SHIM_RUN_MARKER="$T/marker" SHIM_RUN_SLEEP=20 \
    setsid "$SIG_SCRIPT" "${SIG_ARGS[@]}" > "$T/out" 2> "$T/err" &
  pid=$!
  for i in $(seq 1 200); do [ -e "$T/marker" ] && break; sleep 0.1; done
  [ -e "$T/marker" ] || { kill -9 "$pid" 2>/dev/null || true; _die "가짜 일회 컨테이너가 시작되지 않았다 — 판정 불가" 2; }
  # **`$!` 를 그룹 번호로 쓰지 않는다** — `setsid` 가 fork 하면 그 번호는 남의 그룹일 수 있다(공유 호스트).
  pg="$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')"
  [ -n "$pg" ] || { kill -9 "$pid" 2>/dev/null || true; _die "프로세스 그룹을 읽지 못했다 — 판정 불가" 2; }
  kill -s "$sig" -"$pg" 2>/dev/null || kill -s "$sig" "$pid"
  # `wait` 의 stderr 를 버린다 — 신호로 죽은 배경 작업의 셸 알림(`… Hangup …`)이 검사 출력에 섞이면
  # CI 로그에서 그것이 결함처럼 보인다. 판정은 아래 `RUN_RC` 와 단언이 한다.
  wait "$pid" 2>/dev/null || RUN_RC=$?
  _expect_rc "$want" "${SIG_ONEOFF_SUFFIX} ${sig} 으로 끝나면 그 신호로 죽는다(128+n — 종료 코드를 꾸미지 않는다)"
  _expect_text "일회 컨테이너 ${oneoff} 를 치웠다" "${SIG_ONEOFF_SUFFIX} ${sig} — trap 이 치운 것을 문면에 보인다"
  _expect_log "docker rm -f ${oneoff}\$" "${SIG_ONEOFF_SUFFIX} ${sig} — trap 이 자기 컨테이너 이름 하나만 지운다"
  _expect_no_log 'rm .*other-proj' "${SIG_ONEOFF_SUFFIX} ${sig} — trap 이 남의 컨테이너를 지우지 않는다"
  _expect_no_log 'rm .*(--filter|\*)' "${SIG_ONEOFF_SUFFIX} ${sig} — trap 이 접두·필터로 지우지 않는다"
  n="$(grep -cE '^docker rm ' "$SHIM_LOG" || true)"
  if [ "$n" = 1 ]; then _ok "${SIG_ONEOFF_SUFFIX} ${sig} — 삭제 호출이 하나다 (${n})"; else _bad "${SIG_ONEOFF_SUFFIX} ${sig} — 삭제 호출이 ${n} 개다"; fi
}
SIG_SCRIPT=./tools/prod-notification-sweep.sh
SIG_ONEOFF_SUFFIX=sweep
SIG_ARGS=("${SWEEP_ARGS[@]}")
_signal_case TERM 143
_signal_case HUP 129

echo "== 평가 커밋 러너(prod-evaluation-commit.sh) — 훑기와 같은 전제 + run 당 여력 인자"
# M15/15Q. 훑기가 받는 단언을 **같은 모양으로** 받는다 — 트리 축 · 프로젝트 이름 · 잔존 · 이름 충돌 ·
# 미룸(4) ↔ 전제 실패(3) · 금지 JVM 옵션. 더해지는 축은 **여력 인자**(기본값을 지어내지 않는다)와
# **run 당 계정 상한에 플래그를 열지 않는다**(M15/15B·15H 의 회전 계약).
EVAL_ARGS=(--env-file "$T/env/prod.env" --state-dir "$T/state" --current-active-bids 7 --project "$PROJ")
SHIM_GIT_HEAD="$OTHER_SHA" _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — 다른 커밋의 트리에서는 돌지 않는다"
_expect_no_log ' run ' "평가 — 그 거절이 일회 컨테이너 앞에서 끝난다"
SHIM_CONFIG_FILE="$T/cfg-othername.json" _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — 해석된 프로젝트 이름이 잠금과 다르면 돌지 않는다"
_expect_no_log 'docker ps' "평가 — 그 거절이 잔존 조회 앞에서 끝난다"
SHIM_PS_FILE="$T/ps-residue" _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — 지난 run 의 (멈춘) 일회 컨테이너가 남아 있으면 돌지 않는다"
_expect_text "${PROJ}-app-run-abc" "평가 — 거절 문면이 남은 이름을 보인다"
SHIM_PS_NAME_HIT="deadbeefcafe" _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — 일회 컨테이너 이름이 전역에서 이미 쓰이면 돌지 않는다"
_expect_no_log ' run ' "평가 — 그 거절이 compose run 앞에서 끝난다"
# cr L-2 — **멈춘** 동명 컨테이너도 이름을 쥔다. 훑기·relay 에만 있던 갈래를 여기도 둔다
# (이 slice 의 주장이 「훑기와 **같은 모양**」이라 그 집합에서 하나가 비면 안 된다).
SHIM_PS_NAME_HIT="deadbeefcafe" SHIM_PS_NAME_STATE=exited _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — **멈춘** 동명 컨테이너가 있어도 돌지 않는다(docker ps -aq)"
_expect_no_log ' run ' "평가 — 그 거절도 compose run 앞에서 끝난다"
# cr H-1 — 값 없는 플래그는 전제 실패(3)다(러너의 「계정 전수를 못 읽었다」와 같은 1 이 되지 않는다).
_run ./tools/prod-evaluation-commit.sh --current-active-bids
_expect_rc 3 "평가 — 값 없는 플래그는 전제 실패(3)다"
_expect_text '값이 없다' "평가 — 그 거절이 문면을 남긴다"
_run ./tools/prod-evaluation-commit.sh --env-file "$T/env/prod.env" --state-dir "$T/state" --project "$PROJ"
_expect_rc 3 "평가 — --current-active-bids 가 없으면 돌지 않는다(여력을 0 으로 지어내지 않는다)"
_expect_no_log ' run ' "평가 — 그 거절이 compose run 앞에서 끝난다(여력 축)"
_run ./tools/prod-evaluation-commit.sh --env-file "$T/env/prod.env" --state-dir "$T/state" --current-active-bids -1 --project "$PROJ"
_expect_rc 3 "평가 — --current-active-bids 가 0 이상의 정수가 아니면 돌지 않는다"
JAVA_TOOL_OPTIONS='-Djdk.httpclient.HttpClient.log=all' _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — JAVA_TOOL_OPTIONS 에 JDK HttpClient 내부 로그 속성이 있으면 돌지 않는다"
_expect_text 'jdk\.httpclient\.HttpClient\.log' "평가 — 문면이 지워야 할 속성 이름을 보인다"
_expect_no_log ' run ' "평가 — 그 거절이 compose run 앞에서 끝난다(JVM 옵션 축)"
_run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 0 "평가 — 양성: 잔존 0 · 같은 커밋 · 같은 프로젝트면 돈다"
_expect_log "run .*--rm .*--pull never .*--name ${PROJ}-app-run-evaluation" "평가 — 자기 일회 컨테이너 이름을 고정해 띄운다"
_expect_log "--env-file $T/env/prod.env" "평가 — 운영 env 파일이 compose 에 간다"
_expect_log "--bidvector.evaluation.mode=once" "평가 — 러너 스위치를 준다"
_expect_log "--bidvector.evaluation.commit.current-active-bids=7" "평가 — 여력 값을 그대로 넘긴다"
_expect_log "-e SPRING_MAIN_WEB_APPLICATION_TYPE=none" "평가 — 러너 run 은 HTTP 표면을 끈다"
_expect_no_log 'max-accounts-per-run|max-consecutive-account-failures' \
  "평가 — run 당 계정 상한·멈춤 줄에 플래그를 열지 않는다(출하 기본값 그대로 — M15/15B·15H 의 회전 계약)"

_hold_lock "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock"
_run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 4 "평가 — 프로젝트 잠금이 잡혀 있으면 **미룬다**(4) — 전제 실패(3)가 아니다"
_expect_no_log ' run ' "평가 — 그 미룸이 일회 컨테이너 앞에서 끝난다"
_release_lock
_hold_lock "$T/state/.lock"
_run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 4 "평가 — 상태 디렉터리 잠금이 잡혀 있어도 **미룬다**(4)"
_release_lock
rm -f "$T/state/.lock"
chmod 500 "$T/state"
_run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
_expect_rc 3 "평가 — 잠금 **파일을 열지 못하면** 전제 실패(3)다"
chmod 700 "$T/state"
for frc in 1 64 65 70 127 137; do
  SHIM_FLOCK_RC="$frc" _run ./tools/prod-evaluation-commit.sh "${EVAL_ARGS[@]}"
  _expect_rc 3 "평가 — flock 종료 ${frc} 는 전제 실패(3) — 경합으로 접지 않는다"
done

echo "== relay 러너(prod-notification-relay.sh) — 훑기와 같은 전제 + 채널 env · 금지 JVM 옵션 · 코드 옮김"
RELAYENV="$T/env/relay.env"
_relay_env() { # [추가 줄…] — §13.2 의 필수 셋 + 인자. **값은 전부 자리값**이다(자격이 아니다).
  { printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=fixture\n'
    printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=fixture\n'
    printf 'BIDVECTOR_NOTIFICATION_SITE_BASE_URL=https://example.invalid\n'
    [ "$#" -eq 0 ] || printf '%s\n' "$@"
  } > "$RELAYENV"
  chmod 600 "$RELAYENV"
}
_relay_env
RELAY_ARGS=(--env-file "$T/env/prod.env" --relay-env-file "$RELAYENV" --state-dir "$T/state" --owner op-fixture --project "$PROJ")
SHIM_GIT_HEAD="$OTHER_SHA" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 다른 커밋의 트리에서는 보내지 않는다"
_expect_no_log ' run --rm' "relay — 그 거절이 일회 컨테이너 앞에서 끝난다"
SHIM_CONFIG_FILE="$T/cfg-othername.json" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 해석된 프로젝트 이름이 잠금과 다르면 보내지 않는다"
_expect_no_log 'docker ps' "relay — 그 거절이 잔존 조회 앞에서 끝난다"
SHIM_PS_FILE="$T/ps-residue" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 지난 run 의 (멈춘) 일회 컨테이너가 남아 있으면 보내지 않는다"
_expect_text "${PROJ}-app-run-abc" "relay — 거절 문면이 남은 이름을 보인다"
SHIM_PS_NAME_HIT="deadbeefcafe" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 일회 컨테이너 이름이 전역에서 이미 쓰이면 보내지 않는다"
SHIM_PS_NAME_HIT="deadbeefcafe" SHIM_PS_NAME_STATE=exited _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — **멈춘** 동명 컨테이너가 있어도 보내지 않는다(docker ps -aq)"

# ── 둘째 env 파일 축 — 이 러너가 훑기에 더하는 자리 ─────────────────────────────
_run ./tools/prod-notification-relay.sh --env-file "$T/env/prod.env" --state-dir "$T/state" --owner op-fixture --project "$PROJ"
_expect_rc 3 "relay — --relay-env-file 이 없으면 보내지 않는다"
_run ./tools/prod-notification-relay.sh --env-file "$T/env/prod.env" --relay-env-file "$RELAYENV" --state-dir "$T/state" --project "$PROJ"
_expect_rc 3 "relay — --owner 가 없으면 보내지 않는다(기본값 없는 키를 지어내지 않는다)"
# cr H-1 — 값 없는 플래그는 전제 실패(3)다(러너의 `FAILED`(1)와 같은 값이 되지 않는다).
_run ./tools/prod-notification-relay.sh --owner
_expect_rc 3 "relay — 값 없는 플래그는 전제 실패(3)다"
_expect_text '값이 없다' "relay — 그 거절이 문면을 남긴다(앞 판은 조용히 1 로 죽었다)"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}" --environment development
_expect_rc 3 "relay — 운영 스택에서 받는 환경은 production·staging 둘뿐이다"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}" --claim-limit 0
_expect_rc 3 "relay — claim-limit 0 은 거절한다(러너가 1 미만을 기동 실패로 본다)"
printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=fixture\n' > "$RELAYENV"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 채널 env 에 필수 키가 없으면 보내지 않는다"
_expect_text 'bot-credential' "relay — 문면이 빠진 속성 이름을 보인다"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(필수 키 축)"
# **덮어쓰기 축** — `--env-from-file` 은 서비스가 선언한 env 를 **이긴다**(실측). §13.2 밖의 이름 하나가
# 반입 가드를 거치지 않고 compose 가 준 값을 바꾸는 자리라, 키 집합을 등식으로 닫는다.
_relay_env 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 채널 env 에 §13.2 밖의 키가 있으면 보내지 않는다(컨테이너 env 를 덮는 자리다)"
_expect_text 'JAVA_TOOL_OPTIONS' "relay — 문면이 지워야 할 키 이름을 보인다"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(키 집합 축)"
_relay_env 'BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:postgresql://elsewhere/x'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — DB 자격 통로 이름도 같은 등식이 막는다(열거가 아니라 집합이다)"
_relay_env 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTE_KEY=-Djdk.internal.httpclient.debug=true'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 허용된 키의 **값**에 숨은 JDK HttpClient 로그 속성도 막는다"
_expect_text 'jdk\.internal\.httpclient\.debug' "relay — 문면이 그 속성 이름을 보인다"
_relay_env 'this line is not KEY=value'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 채널 env 에 'KEY=값' 도 주석도 아닌 줄이 있으면 보내지 않는다"
_expect_no_text 'this line is not' "relay — 그 문면이 줄 내용을 옮기지 않는다(이 파일은 자격을 담는다)"

# ── v H-1 — **소비자의 해석기가 보는 키**로 재는가 ──────────────────────────────
# compose 의 dotenv 해석기는 **따옴표로 닫힌 값 뒤의 둘째 대입을 새 변수로** 읽는다. 앞 판의 술어는
# 줄마다 첫 `KEY=` 만 sed 로 뽑아 아래 넷을 전부 통과시켰고, 컨테이너에는 DB 자격 통로·
# `SPRING_APPLICATION_JSON`·`JAVA_TOOL_OPTIONS` 가 들어갔다(verifier 실측). 지금은 키 집합을
# **compose 자신에게** 묻는다 — 이 가짜 docker 는 그 호출만 진짜로 넘긴다(위 shim 의 `config` 갈래).
# 그래서 아래 넷은 「우리 문법이 막는다」가 아니라 **「소비자가 보는 키가 허용 집합 밖이다」**를 잰다.
_relay_bypass() { # <이름> <덧붙일 줄>
  _relay_env "$2"
  _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
  _expect_rc 3 "relay — 우회 ${1}: 따옴표 뒤 둘째 대입도 키 집합이 본다"
  _expect_no_log ' run --rm' "relay — 우회 ${1}: 그 거절이 compose run 앞에서 끝난다"
}
_relay_bypass '쌍따옴표+공백' 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY="rk" BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil'
_relay_bypass '홑따옴표' "BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY='rk' BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil"
_relay_bypass '공백 없음' 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY="rk"SPRING_APPLICATION_JSON={}'
_relay_bypass 'JVM 통로' 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY="rk" JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=9'
_expect_text 'JAVA_TOOL_OPTIONS' "relay — 우회 JVM 통로: 문면이 그 키 이름을 보인다"

# ── M15/15R(S-7) — 거절 문면의 「키 이름」은 **자격 파일의 바이트에서 만들어진다** ─────
# 위 등식이 보는 키는 compose 의 dotenv 해석기가 그 파일에서 만들어 낸 이름이고, 그 해석기는 따옴표로
# 닫힌 값 뒤의 둘째 대입을 **새 변수로** 읽는다(v H-1). 그래서 운영자의 편집 사고가 자격 조각을 키
# **이름**으로 만들 수 있고, 전수로 찍으면 그것이 journal 로 간다(지우기 어려운 자리다). 이름 꼴
# (`^[A-Z][A-Z0-9_]*$`)만 찍고 나머지는 **개수로만** 말한다 — 거절 자체는 그대로 `exit 3` 이다.
# 이름 꼴인 조각은 여전히 찍힌다(등재된 제한) — 이름을 전부 가리면 운영자가 무엇을 지울지 모른다.
_relay_env 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY="rk" fixtureOddKey=x'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 이름 꼴이 아닌 stray 키도 거절한다(개수로 세어 말한다)"
_expect_no_text 'fixtureOddKey' "relay — 그 키 **문자열**은 어느 출력에도 없다(자격 조각이 이름이 되는 자리다)"
# 뒤 글자까지 묶는다(cr L-2) — `1` 까지만 보면 10·11·100 도 접두 일치로 통과해 「뽑은 수를 고정한다」가 깨진다.
_expect_text '이름 꼴이 아닌 키 1 개' "relay — 대신 개수를 말한다(0 이면 공허한 단언이 된다)"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(stray 꼴 축)"

# ── v L-1 — compose 가 파일을 **통째로 버리는** 입력 ────────────────────────────
# 그대로 두면 러너가 `RefusedMissingChannelConfig`(1)로 끝나 진단이 엉뚱한 곳을 가리킨다. 키가 0 인데
# 내용 줄이 있으면 **그 사실을 말하고** 멈춘다 — 「필수 키가 없다」와 고칠 자리가 다르다.
_relay_dropped() { # <이름> <덧붙일 줄>
  _relay_env "$2"
  _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
  _expect_rc 3 "relay — 버려짐 ${1}: compose 가 파일을 통째로 버리면 보내지 않는다"
  _expect_text '통째로 버렸다' "relay — 버려짐 ${1}: 문면이 「필수 키가 없다」가 아니라 그 사실을 말한다"
}
_relay_dropped '안 닫힌 따옴표' 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY="v'
_relay_dropped '값 뒤 !!' 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY="v" !!x'
# `${VAR:?…}` 도 compose 가 파일을 버리게 하는 꼴이지만, 이제는 **그 앞의 좁히는 전제**(값에 `$` 를
# 쓰지 않는다, v L-3)가 먼저 끊는다 — 아래 「보간 축」 단언이 그 자리를 잰다. 같은 입력을 두 문면으로
# 기대하지 않는다.

# ── v L-2 — 검사한 파일과 **컨테이너가 받는 파일**이 같은가 ─────────────────────
# stub YAML 에 경로를 따옴표 없이 넣으면 경로의 ` #' 이 YAML 주석이 되어 `config` 가 **다른 파일**을
# 읽는다. 그러면 우회 줄을 담은 파일이 깨끗한 이웃으로 검사되고 `--env-from-file` 로는 그 파일이 간다.
RELAY_HASH_ENV="$T/env/relay.env #x"
{ printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=fixture\n'
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=fixture\n'
  printf 'BIDVECTOR_NOTIFICATION_SITE_BASE_URL=https://example.invalid\n'
  printf 'BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil\n'; } > "$RELAY_HASH_ENV"
chmod 600 "$RELAY_HASH_ENV"
_relay_env   # 이웃의 깨끗한 relay.env — 따옴표가 없으면 이쪽이 읽힌다
_run ./tools/prod-notification-relay.sh --env-file "$T/env/prod.env" --relay-env-file "$RELAY_HASH_ENV" --state-dir "$T/state" --owner op-fixture --project "$PROJ"
_expect_rc 3 "relay — 경로에 ' #' 이 있어도 **그 파일**을 검사한다(이웃의 깨끗한 파일이 아니라)"
_expect_text 'BIDVECTOR_PERSISTENCE_JDBCURL' "relay — 그 파일의 우회 키를 문면이 보인다"

# ── v M-1 — 경로의 `$` 는 compose 가 stub 의 **값**에서 보간한다 ────────────────
# JSON 인용은 YAML 파싱만 닫고 보간은 닫지 않는다. 그래서 `…/${NOPE:-relay.env}` 는 `config` 가
# **이웃**을 읽고 `--env-from-file` 은 **그 파일**을 읽게 만든다 — L-2 와 같은 계열의 다른 통로다.
RELAY_DOLLAR_ENV="$T/env/\${NOPE:-relay.env}"
{ printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=fixture\n'
  printf 'BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=fixture\n'
  printf 'BIDVECTOR_NOTIFICATION_SITE_BASE_URL=https://example.invalid\n'
  printf 'BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil\n'; } > "$RELAY_DOLLAR_ENV"
chmod 600 "$RELAY_DOLLAR_ENV"
_relay_env   # 이웃의 깨끗한 relay.env — 보간이 열려 있으면 이쪽이 읽힌다
_run ./tools/prod-notification-relay.sh --env-file "$T/env/prod.env" --relay-env-file "$RELAY_DOLLAR_ENV" --state-dir "$T/state" --owner op-fixture --project "$PROJ"
_expect_rc 3 "relay — 경로에 '\$' 가 있으면 보내지 않는다(검사한 파일과 받는 파일이 갈린다)"
_expect_text '경로에' "relay — 그 거절이 **경로** 축의 것이다(값 축이 아니라)"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(경로 보간 축)"

# ── v L-3 — 두 해석기의 `${VAR}` 조회 집합이 다르다 ─────────────────────────────
# `config` 는 래퍼 프로세스의 env 로, `run --env-from-file` 은 컨테이너에 주는 env 로 푼다. 그래서
# `…=${HOME:?x}` 는 `config` 를 통과하고 run 에서는 파일이 **통째로** 버려진다. 좁히는 전제로 닫는다.
_relay_env 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY=${HOME:?x}'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 값의 \${VAR} 는 거절한다(두 통로가 서로 다른 env 로 푼다)"
_expect_text "'\\\$' 가 있다" "relay — 그 거절이 **보간 축**의 것이다(「통째로 버렸다」가 아니다)"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(보간 축)"
# **`env -i` 만으로는 닫히지 않는 한 칸** — `config` 에 남긴 유일한 이름이 `PATH` 라 `${PATH:?x}` 는
# 좁힌 env 에서도 **통과한다**(실측: rc 0 · 키 3). 그 한 칸을 닫는 것이 위의 좁히는 전제이고,
# 이 입력이 그 전제를 **단독으로** 잰다(앞의 `${HOME:?x}` 는 두 기제가 겹쳐 가르지 못한다).
_relay_env 'BIDVECTOR_NOTIFICATION_TELEGRAM_ROUTEKEY=${PATH:?x}'
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — \${PATH:?…} 도 거절한다(좁힌 env 가 못 보는 한 칸)"
_expect_no_log ' run --rm' "relay — 그 거절도 compose run 앞에서 끝난다"

# ── cr R-2 — 재는 경로와 넘기는 경로가 **같은가**(심볼릭 링크로 가른다) ──────────
# 둘 다 같은 문자열이면 이 축은 미측정이다. 링크를 주면 원문과 해석된 경로가 **다른 글자**가 된다.
_relay_env
ln -sfn "$RELAYENV" "$T/env/relay-link.env"
_run ./tools/prod-notification-relay.sh --env-file "$T/env/prod.env" --relay-env-file "$T/env/relay-link.env" --state-dir "$T/state" --owner op-fixture --project "$PROJ"
_expect_rc 0 "relay — 링크로 준 채널 env 도 돈다"
_expect_log "--env-from-file $(readlink -f "$RELAYENV")" "relay — 컨테이너에 **해석된** 경로를 넘긴다(재는 것과 같은 파일)"
_expect_no_log "--env-from-file $T/env/relay-link.env" "relay — 원문 경로를 그대로 넘기지 않는다"

# ── cr R-1 — 도구 어긋남은 compose 거절과 **다른 코드**로 돌아온다 ───────────────
# `mktemp` 축이 그 갈래를 단독으로 잰다(`jq` 는 위 문턱이 먼저 잡아 가르지 못한다).
_relay_env
TMPDIR="$T/no-such-tmpdir" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 임시 자리를 만들지 못하면 멈춘다"
_expect_text '도구.*가 어긋났다' "relay — 그 문면이 **도구**를 가리킨다"
_expect_no_text '통째로 버렸다' "relay — 도구 어긋남을 「채널 env 가 깨졌다」로 읽지 않는다"

# ── cr N-2 — 해석 **도중에** 신호를 받아도 stub 임시 자리를 남기지 않는다 ────────
# `RETURN` trap 은 함수가 스스로 끝날 때만 돈다 — 신호에는 돌지 않는다. 그래서 신호 trap 을 해석
# 앞에 걸었고, 여기서 **그 창 안에서** TERM 을 보내 남는 것이 없음을 센다. 가짜 docker 가
# `SHIM_CONFIG_SLEEP` 으로 창을 연다(그 갈래는 진짜 compose 로 넘어가기 직전이다).
_relay_env
mkdir -p "$T/stubtmp"
rm -rf "${T:?}/stubtmp/"*
RUN_RC=0
: > "$SHIM_LOG"
TMPDIR="$T/stubtmp" SHIM_CONFIG_SLEEP=10 \
  setsid ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}" > "$T/out" 2> "$T/err" &
stub_pid=$!
for i in $(seq 1 200); do [ -n "$(ls -A "$T/stubtmp" 2>/dev/null)" ] && break; sleep 0.1; done
if [ -z "$(ls -A "$T/stubtmp" 2>/dev/null)" ]; then
  kill -9 "$stub_pid" 2>/dev/null || true
  _bad "stub 임시 자리가 만들어지지 않았다 — 신호 갈래를 잴 수 없다"
else
  _ok "stub 임시 자리가 해석 중에 실제로 있다(이 단언이 공허하지 않다)"
  stub_pg="$(ps -o pgid= -p "$stub_pid" 2>/dev/null | tr -d ' ')"
  kill -s TERM -"${stub_pg:-$stub_pid}" 2>/dev/null || kill -s TERM "$stub_pid" 2>/dev/null || true
  wait "$stub_pid" 2>/dev/null || RUN_RC=$?
  _expect_rc 143 "relay — 해석 중 TERM 이면 그 신호로 죽는다(128+15)"
  if [ -z "$(ls -A "$T/stubtmp" 2>/dev/null)" ]; then
    _ok "relay — 해석 중 TERM 이어도 stub 임시 자리를 남기지 않는다"
  else
    _bad "relay — 해석 중 TERM 이 stub 임시 자리를 남겼다: $(ls -A "$T/stubtmp" | tr '\n' ' ')"
  fi
fi
# **정상 경로도 치운다** — 신호 갈래만 재면 「끝까지 돈 run 이 자리를 쌓는다」가 열린 채다(실측:
# 이 단언이 없으면 호출 직후의 정리를 걷는 변이가 **초록**이다). 양성 run 뒤에 자리가 비어야 한다.
rm -rf "${T:?}/stubtmp/"*
TMPDIR="$T/stubtmp" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 0 "relay — 양성: 임시 자리를 따로 줘도 돈다"
if [ -z "$(ls -A "$T/stubtmp" 2>/dev/null)" ]; then
  _ok "relay — 끝까지 돈 run 도 stub 임시 자리를 남기지 않는다"
else
  _bad "relay — 끝까지 돈 run 이 stub 임시 자리를 남겼다: $(ls -A "$T/stubtmp" | tr '\n' ' ')"
fi
rm -rf "${T:?}/stubtmp"

# ── cr R-1 — 도구가 깨진 것을 「채널 env 가 깨졌다」로 읽지 않는다 ───────────────
mkdir -p "$T/badbin"
printf '#!/bin/sh\nexit 127\n' > "$T/badbin/jq"
chmod +x "$T/badbin/jq"
_relay_env
PATH="$T/badbin:$PATH" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — jq 가 깨지면 멈춘다"
# **제 문면을 잰다**(cr N-5) — 「jq 문턱」과 「해석 뒤 도구 어긋남」은 고칠 자리가 같아 보이지만
# 서는 자리가 다르다. 문턱이 먼저 잡는다는 것을 그 글자로 못박는다.
_expect_text '가장 단순한 JSON' "relay — 그 문면이 **jq 문턱**의 것이다(해석 뒤 갈래가 아니라)"
_expect_no_text '도구.*가 어긋났다' "relay — 문턱이 먼저 잡으므로 해석 뒤 문면이 나오지 않는다"
_expect_no_text '통째로 버렸다' "relay — 깨진 도구를 「채널 env 가 깨졌다」로 읽지 않는다"

# ── cr H-2 — 이 파일은 **부르는 사용자**가 읽는다. other 에 열리면 거절한다 ──────
_relay_env
chmod 644 "$RELAYENV"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 채널 env 가 other 에 열려 있으면 보내지 않는다(이 파일은 봇 자격을 담는다)"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(권한 축)"
_relay_env
JAVA_TOOL_OPTIONS='-Djdk.internal.httpclient.debug=' _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — JAVA_TOOL_OPTIONS 의 로그 속성은 **빈 값이어도** 거절이다(존재만 본다)"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(JVM 옵션 축)"
SHIM_NO_ENV_FROM_FILE=1 _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — compose 가 run --env-from-file 을 모르면 보내지 않는다(채널 env 가 컨테이너에 닿지 않는다)"
_expect_no_log ' run --rm' "relay — 그 거절이 compose run 앞에서 끝난다(플래그 지원 축)"
# cr L-6 — **빈 도움말은 따로 말한다.** 「플래그가 없다」와 「도움말을 못 받았다」는 고칠 자리가 다르다.
SHIM_EMPTY_RUN_HELP=1 _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — run --help 이 빈 출력이어도 멈춘다"
_expect_text '아무것도 내지 않았다' "relay — 빈 출력은 「플래그를 모른다」와 **다른 문면**이다"

_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 0 "relay — 양성: 잔존 0 · 같은 커밋 · 같은 프로젝트 · 채널 env 가 §13.2 면 보낸다"
_expect_log "run .*--rm .*--pull never .*--name ${PROJ}-app-run-relay" "relay — 자기 일회 컨테이너 이름을 고정해 띄운다"
_expect_log "--env-from-file $RELAYENV" "relay — 둘째 env 파일이 **일회 컨테이너까지** 간다(§13.3)"
_expect_log "--env-file $T/env/prod.env" "relay — 스택 env 파일은 그대로 compose 에 간다(파일이 둘이다)"
_expect_log "--bidvector.relay.mode=once" "relay — 러너 스위치를 준다"
_expect_log "--bidvector.relay.environment=production" "relay — 기본 환경은 production 이다"
_expect_log "--bidvector.relay.owner=op-fixture" "relay — owner 를 그대로 넘긴다"
_expect_log "--bidvector.relay.channel=telegram" "relay — 채널은 telegram 으로 고정이다"
_expect_log "--bidvector.relay.claim-limit=50" "relay — claim-limit 기본은 50 이다(§13.3)"
_expect_log "-e SPRING_MAIN_WEB_APPLICATION_TYPE=none" "relay — 러너 run 은 HTTP 표면을 끈다"
# cr L-3 — `_expect_no_log ' run --rm'` 은 훑기·평가의 `' run '` 보다 **느슨하다**(첫 플래그가 다른
# `run` 이 생기면 지나간다). 느슨해진 사유는 플래그 지원 질의(`run --help`) 하나뿐이므로 그 **수**를
# 세어 원래 세기를 되찾는다: 양성 run 에서 ` run ` 이 든 줄은 **정확히 둘**(질의 하나 + 진짜 run 하나).
relay_run_n="$(grep -cE '^docker .* run ' "$SHIM_LOG" || true)"
if [ "$relay_run_n" = 2 ]; then
  _ok "relay — 양성 run 에서 ' run ' 줄은 둘이다(도움말 질의 하나 + 일회 컨테이너 하나)"
else
  _bad "relay — ' run ' 줄이 ${relay_run_n} 개다(둘이어야 한다 — 거절 갈래의 ' run --rm' 단언이 그만큼 느슨해진다)"
fi
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}" --environment staging --claim-limit 1
_expect_rc 0 "relay — §13.4 의 단계 실행(staging · claim-limit 1)이 선다"
_expect_log "--bidvector.relay.environment=staging" "relay — staging 을 그대로 넘긴다"
_expect_log "--bidvector.relay.claim-limit=1" "relay — 첫 실발송의 상한 1 을 그대로 넘긴다"

# **러너의 다섯과 래퍼의 둘이 겹치지 않는가**(M15/15Q). 러너 3(`LEASE_BUSY` — 기다리면 풀린다)과
# 래퍼 3(전제 실패 — 고쳐야 한다), 러너 4(`ENV_SUPPRESSED` — 고쳐야 한다)와 래퍼 4(미룸 — 기다리면
# 풀린다)는 **뜻이 정확히 반대**다. 겹치면 §9.5 의 경보가 거꾸로 운다.
#
# **M15/15R(S-3) — 통과를 열거로 닫는다.** 앞 판의 `*) exit "$rc"` 는 **모르는 코드를 그대로 흘렸고**,
# 래퍼가 이미 쓰는 `5`·`6` 이 러너의 다음 코드 자리였다. 러너에 여섯째가 `5` 로 들어오는 날 창 셋이
# 한꺼번에 조용해진다 — `10-defer.conf` 가 그 값을 깨끗한 종료로 만들어 `OnFailure` 가 안 걸리고,
# 래퍼는 아무 줄도 남기지 않으며, §13.4 의 미룸 계수는 그 문면이 없어 0 이다. 이제 러너의 다섯만
# 이름으로 받고 **그 밖은 `7`**(`SuccessExitStatus=4 5` 밖 — 경보가 운다)로 낸다.
# **거동은 대표 구간으로, 가지의 꼴은 구조로 닫는다**(v G3). 0..255 전수 스윕을 재 보았더니 전건이
# **16.6 초 → 94.2 초**(+78 초 · 5.7 배)였다 — 매 PR 과 매 로컬 반복에 붙는 값이라 받지 않았다. 대신 둘로
# 나눈다: ⓐ 여기서 **대표 구간**(러너의 다섯 · 래퍼 자신의 셋 · `docker` CLI 실패 125·126·127 · 신호
# 128·137·143 · 상한 255)을 재고, ⓑ 아래 「가지 전수 == 인식 수」를 **빈 줄이 아닌 모든 줄**로 세어
# 어떤 꼴의 가지가 더해지거나 라벨 꼴이 바뀌어도 붉게 한다(그 둘이 스윕이 덮던 공간을 비용 없이 덮는다 —
# 라벨이 파싱되는 꼴은 집합 등식과 교집합 단언이 다시 받는다).
_relay_env
for _rc_pair in "0 0" "1 1" "2 2" "3 5" "4 6" "5 7" "6 7" "7 7" "9 7" \
                "125 7" "126 7" "127 7" "128 7" "137 7" "143 7" "255 7"; do
  _rc_in="${_rc_pair%% *}" _rc_want="${_rc_pair##* }"
  SHIM_RUN_RC="$_rc_in" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
  _expect_rc "$_rc_want" "relay — 러너 종료 ${_rc_in} → 래퍼 ${_rc_want}"
done
unset _rc_pair _rc_in _rc_want
SHIM_RUN_RC=3 _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_text 'LEASE_BUSY' "relay — 그 줄이 옮긴 코드의 원래 이름을 보인다"
# M15/15R(D-15R-8 ③) — 임대는 **owner 별이 아니다**(`PostgresAdvisoryLockLease` 의 키는 소비자 종류
# 단위다). 「같은 owner 의 relay 가 쥐고 있다」로 적으면 운영자가 다른 owner 로 다시 쳐서 풀릴 것처럼
# 읽고, 실제로는 그 run 도 같은 자물쇠에서 선다.
_expect_no_text '같은 owner' "relay — LEASE_BUSY 문면이 임대를 owner 별이라고 말하지 않는다"
SHIM_RUN_RC=4 _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_text 'ENV_SUPPRESSED' "relay — 그 줄이 옮긴 코드의 원래 이름을 보인다"
SHIM_RUN_RC=137 _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_text '예상 밖의 종료 코드 137' "relay — 모르는 코드는 **원래 값을 문면에 담아** 7 로 난다"

# **등식 — 러너의 값 집합 == 래퍼 `case` 가 이름으로 받는 집합**(M15/15R, S-3). 위 거동 단언은 오늘의
# 사상을 잠그고, 이 등식은 **러너 쪽이 늘어나는 날**을 잡는다. 러너에 여섯째가 들어오면 그 값이
# 래퍼의 열거 밖이 되어 여기서 선다(운영에서는 그 run 이 `7` 로 끝나 경보가 운다 — 조용한 통과가 없다).
#
# **뽑은 수를 함께 고정한다**(이 slice 의 경계 문장): 열거의 꼴이 바뀌어(`LEASE_BUSY(value = 3)` 따위)
# 추출이 비면 「집합이 같다」가 공허하게 참이 되므로, **항목 수 == 해석된 값 수**를 먼저 센다.
# **M15/15U — 래퍼가 둘이 됐다**(relay · 수집). 술어를 사본으로 두지 않고 **라벨·열거 이름·두 경로를
# 인자로 받는 함수 하나**로 둔다(v2-지침서 §5 중복 금지와 같은 사유: 사본 둘은 한쪽만 고쳐지는 날이 온다).
# 단언 **수**는 래퍼마다 넷으로 그대로다 — relay 쪽 계수는 앞 판과 같다.
_exit_code_equation_check() { # <라벨> <enum 이름> <KT 경로> <래퍼 경로>
  local label="$1" enum="$2" kt="$3" sh="$4"
  local body rex_entry_n rex_args rex_vals rex_val_n rex_set _a _v
  local case_body case_branch_n case_known_n wrap_set wrap_n xlat_set xlat_clash
  # **자리를 못 읽으면 판정 불가로 선다**(cr M-2) — 열거가 옮겨졌는데 추출이 조용히 비면 아래 등식이
  # 공허하게 참이 된다. 이 파일의 관례대로 전제 실패는 `exit 2` 다.
  [ -r "$kt" ] || _die "${enum} 의 자리를 읽지 못했다: ${kt}(열거가 옮겨졌다 — 등식을 고친다) · 판정 불가" 2
  [ -r "$sh" ] || _die "${label} 래퍼를 읽지 못했다: ${sh} · 판정 불가" 2
  body="$(awk -v e="enum class ${enum}(" 'index($0, e) == 1 {inb = 1; next} inb && /^}/ {exit} inb {print}' "$kt")"
  # **항목 수와 값 추출은 서로 다른 술어로 센다**(v F3). 앞 판은 둘 다 **같은 정규식**이라 꼴이 다른 항목이
  # 분자·분모에서 **함께** 빠졌고, `NEW_CODE(5), // 주석` 한 줄이 등식을 공허하게 참으로 만들었다(실측).
  # 이제 분모는 「식별자로 시작해 여는 괄호가 오는 줄」이라는 **느슨한** 술어이고, 분자는 값을 실제로
  # 해석한 수다 — 꼴이 바뀌면 분모만 늘어 여기서 선다.
  rex_entry_n="$(printf '%s\n' "$body" | grep -cE '^[[:space:]]*[A-Z][A-Za-z0-9_]*\(' || true)"
  rex_args="$(printf '%s\n' "$body" | sed -nE 's/^    [A-Z][A-Z0-9_]*\((.+)\),$/\1/p' || true)"
  rex_vals=""
  while IFS= read -r _a; do
    [ -n "$_a" ] || continue
    case "$_a" in
      *[!0-9]*) _v="$(sed -nE "s/^private const val ${_a} = ([0-9]+)\$/\1/p" "$kt" | head -n1 || true)" ;;
      *) _v="$_a" ;;
    esac
    [ -n "$_v" ] || continue
    rex_vals="${rex_vals}${_v}"$'\n'
  done <<REXEOF
${rex_args}
REXEOF
  rex_val_n="$(printf '%s' "$rex_vals" | grep -c '' || true)"
  rex_set="$(printf '%s' "$rex_vals" | sort -u | tr '\n' ' ' || true)"
  # 래퍼 쪽도 같은 모양으로 **가지 수**(느슨)와 **인식한 가지 수**(엄격 — 숫자 라벨 또는 `*`)를 가른다.
  # 앞 판은 숫자 라벨만 추출해 `"8")`·`12[5-7])` 같은 가지가 추출 **밖**이라 조용히 통과했고, 라운드 1 의
  # 느슨한 쪽도 **꼴 열거**(`[^[:space:]()]+\)`)여서 공백 낀 대안(`125 | 126)`)과 여는 괄호 꼴(`(125)`)을
  # 놓쳤다(v G3 실측). 이제 느슨한 쪽은 **빈 줄이 아닌 줄 전부**다 — 가지가 어떤 꼴로 쓰이든, 본문이 여러
  # 줄로 늘어나든 분모가 늘어 선다(그 오탐은 기대를 함께 고치라는 뜻이고, 줄 집합 등식과 같은 래칫이다).
  case_body="$(awk '/^case "\$rc" in$/ {inb = 1; next} inb && /^esac$/ {exit} inb {print}' "$sh")"
  case_branch_n="$(printf '%s\n' "$case_body" | grep -cE '[^[:space:]]' || true)"
  case_known_n="$(printf '%s\n' "$case_body" | grep -cE '^  ([0-9|]+|\*)\)' || true)"
  wrap_set="$(printf '%s\n' "$case_body" | sed -nE 's/^  ([0-9|]+)\).*/\1/p' | tr '|' '\n' | grep -v '^$' | sort -u | tr '\n' ' ' || true)"
  wrap_n="$(printf '%s\n' "$case_body" | sed -nE 's/^  ([0-9|]+)\).*/\1/p' | tr '|' '\n' | grep -v '^$' | grep -c '' || true)"
  # **래퍼가 번역해서 내는 값**(가지 본문의 `exit <정수>` — 통과 갈래는 `exit "$rc"` 라 여기 안 든다).
  xlat_set="$(printf '%s\n' "$case_body" | grep -oE 'exit [0-9]+' | awk '{print $2}' | sort -u || true)"
  if [ "$rex_entry_n" -ge 1 ] && [ "$rex_entry_n" = "$rex_val_n" ]; then
    _ok "${enum} 의 항목 ${rex_entry_n} 개가 모두 값으로 해석된다(꼴이 바뀌면 분모만 늘어 선다)"
  else
    _bad "${enum} 항목 ${rex_entry_n} 개 중 ${rex_val_n} 개만 값으로 해석됐다 — 열거의 꼴이 바뀌었다(추출을 고친다)"
  fi
  if [ "$case_branch_n" -ge 1 ] && [ "$case_branch_n" = "$case_known_n" ]; then
    _ok "${label} — 래퍼 case 의 가지 ${case_branch_n} 개를 모두 인식했다(인식 못 하는 꼴의 가지가 없다)"
  else
    _bad "${label} — 래퍼 case 의 가지 ${case_branch_n} 개 중 ${case_known_n} 개만 인식했다 — 숫자도 '*' 도 아닌 라벨이 있다(그 가지는 아래 등식 밖이다)"
  fi
  if [ "$wrap_n" -ge 1 ] && [ "$wrap_n" = "$rex_val_n" ] && [ "$wrap_set" = "$rex_set" ]; then
    _ok "${label} — 러너의 종료 코드 집합 == 래퍼 case 가 이름으로 받는 집합 (${rex_set})"
  else
    _bad "${label} — 러너 {${rex_set}}(${rex_val_n}) ≠ 래퍼 case {${wrap_set}}(${wrap_n}) — 러너에 값을 더했으면 래퍼의 case 와 runbook 의 표를 함께 고친다"
  fi
  # **번역 값 ∩ 러너 값 = ∅**(cr M-3). 위 등식은 「집합이 같다」만 보므로, 러너에 `5` 가 생긴 날 래퍼에
  # `5) … exit 5` 를 더하면 **둘 다 초록**인데 그 순간 러너의 `5` 와 래퍼가 옮겨 내는 `5` 가 다시 한 수로
  # 만난다 — 이 축이 없앤 바로 그 꼴이다. 오늘 그것을 막는 것은 산문 한 줄뿐이었다.
  xlat_clash="$(printf '%s\n' "$xlat_set" | grep -v '^$' | sort -u | comm -12 - <(printf '%s' "$rex_vals" | grep -v '^$' | sort -u) | tr '\n' ' ' || true)"
  if [ -n "${xlat_set//[[:space:]]/}" ] && [ -z "${xlat_clash// /}" ]; then
    _ok "${label} — 래퍼가 번역해 내는 값 {$(printf '%s' "$xlat_set" | tr '\n' ' ')} 와 러너 값 {${rex_set}} 의 교집합이 비었다"
  else
    _bad "${label} — 래퍼의 번역 값과 러너 값이 겹친다: {${xlat_clash}} — 뜻이 다른 둘이 같은 수로 만나면 경보가 거꾸로 운다(번역 값을 러너 밖으로 옮긴다)"
  fi
}
_exit_code_equation_check relay RelayExitCode \
  app/src/main/kotlin/bidvector/app/relay/RelayLines.kt tools/prod-notification-relay.sh

_hold_lock "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 4 "relay — 프로젝트 잠금이 잡혀 있으면 **미룬다**(4) — 전제 실패(3)가 아니다"
_expect_no_log ' run --rm' "relay — 그 미룸이 일회 컨테이너 앞에서 끝난다"
_release_lock
_hold_lock "$T/state/.lock"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 4 "relay — 상태 디렉터리 잠금이 잡혀 있어도 **미룬다**(4)"
_release_lock
rm -f "$T/state/.lock"
chmod 500 "$T/state"
_run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
_expect_rc 3 "relay — 잠금 **파일을 열지 못하면** 전제 실패(3)다"
chmod 700 "$T/state"
for frc in 1 64 65 70 127 137; do
  SHIM_FLOCK_RC="$frc" _run ./tools/prod-notification-relay.sh "${RELAY_ARGS[@]}"
  _expect_rc 3 "relay — flock 종료 ${frc} 는 전제 실패(3) — 경합으로 접지 않는다"
done

echo "== 수집 러너(prod-collection.sh) — 훑기와 같은 전제 + 창·상한 인자 · 서비스 키 env · 실행 상태 붙임"
# M15/15U. 러너 셋이 받는 단언을 **같은 모양으로** 받고(트리 축 · 프로젝트 이름 · 잔존 · 이름 충돌 ·
# 미룸(4) ↔ 전제 실패(3) · 금지 JVM 옵션 · 신호 trap), 더해지는 축은 넷이다 —
#   ① **창·상한 인자**(기본값을 지어내지 않는다 · 모양 검사 · 인자 주입 거절)
#   ② **서비스 키 env**(둘째 env 파일 — 키 집합을 `bidvector.koneps.service-key` 가 받는 이름으로 닫는다)
#   ③ **실행 상태 host 붙임**(`run -v` — `_prod_config_surface_ok` 는 `config` 출력을 보므로 이 자리를
#      **보지 못한다**. 그래서 붙임의 원천을 래퍼가 지은 한 경로로 묶는 것이 이 단언들의 일이다)
#   ④ **러너 다섯과 래퍼 둘의 겹침 옮김**(relay 와 같은 축, 다른 수)
#
# 단언은 문면 grep 이 아니라 **가짜 docker 가 받은 argv** 로 잰다 — 창·상한·업종·붙임·env 파일이 실제
# 호출에 실렸는가.
KONEPSENV="$T/env/koneps.env"
_koneps_env() { # [추가 줄…] — 서비스 키 한 키. **값은 자리값이다**(자격이 아니다).
  { printf 'BIDVECTOR_KONEPS_SERVICE_KEY=fixture\n'
    [ "$#" -eq 0 ] || printf '%s\n' "$@"
  } > "$KONEPSENV"
  chmod 600 "$KONEPSENV"
}
_koneps_env
# 실행 상태 root — **저장소 밖**이고 group 쓰기가 열려 있다(컨테이너의 uid/gid 10001 이 그 group 으로
# 쓴다 — runbook §14 ①). **gid 의 *값* 등식은 여기서 재지 못한다**: 이 검사를 돌리는 계정은 gid 10001 로
# `chgrp` 할 수 없다(등재된 제한 — 그 등식은 §14 ① 의 운영자 확인이다). 그러나 **setgid 의 유무는
# 잰다**(M15/15U r1) — 그 비트가 group 을 달 디렉터리로 잇는 자리이고 권한이 필요 없다.
# root 는 `2700` 이다(**2770 이 아니다**): 컨테이너는 host 쪽 조상을 지나지 않으므로(이 라운드의 busybox
# 실측) 조상에 group 쓰기를 열면 같은 group 의 다른 계정에게 달 디렉터리 바꿔치기를 허락하는 것뿐이다.
RSROOT="$T/runstate"
install -d -m 2700 "$RSROOT" || _die "실행 상태 root fixture 를 만들지 못했다 — 판정 불가" 2
# **달·창은 KST 로 짓는다**(우회 6) — host 의 TZ 에 기대지 않는다. 자정 경계의 한 초에서 이 계산과
# 래퍼의 계산이 갈릴 수 있다(등재된 제한 — 그때는 조용히 지나가지 않고 붉다).
KST_MONTH="$(TZ=Asia/Seoul date +%Y-%m)"
KST_TODAY="$(TZ=Asia/Seoul date +%F)"
KST_BACK1="$(TZ=Asia/Seoul date -d "$KST_TODAY -1 day" +%F)"
COL_MONTH_DIR="$RSROOT/notice-list/$KST_MONTH"
# 컨테이너 안 경로는 **고정**이다(D-15U-4) — 인자에서 오지 않는다.
COL_IN_CONTAINER=/var/lib/bidvector-run-state
COL_BASE=(--env-file "$T/env/prod.env" --koneps-env-file "$KONEPSENV" --state-dir "$T/state" --project "$PROJ")
COL_ARGS=("${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories construction,service --calls-per-day 50 --calls-total 50)
COL_FIXED=("${COL_BASE[@]}" --run-state-root "$RSROOT" --from 2026-11-01 --to 2026-11-20 \
  --categories construction --calls-per-day 7 --calls-total 9)

# ── 훑기와 같은 전제 ────────────────────────────────────────────────────────────
export SHIM_CONFIG_FILE="$T/cfg.json" SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY=""
export SHIM_PS_FILE="$T/ps-clean" SHIM_PS_NAME_HIT="" SHIM_PS_NAME_STATE=running
SHIM_GIT_HEAD="$OTHER_SHA" _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 다른 커밋의 트리에서는 수집하지 않는다"
_expect_no_log ' run --rm' "수집 — 그 거절이 일회 컨테이너 앞에서 끝난다"
SHIM_CONFIG_FILE="$T/cfg-othername.json" _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 해석된 프로젝트 이름이 잠금과 다르면 수집하지 않는다"
_expect_no_log 'docker ps' "수집 — 그 거절이 잔존 조회 앞에서 끝난다"
SHIM_PS_FILE="$T/ps-residue" _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 지난 run 의 (멈춘) 일회 컨테이너가 남아 있으면 수집하지 않는다"
_expect_text "${PROJ}-app-run-abc" "수집 — 거절 문면이 남은 이름을 보인다"
SHIM_PS_NAME_HIT="deadbeefcafe" _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 일회 컨테이너 이름이 전역에서 이미 쓰이면 수집하지 않는다"
SHIM_PS_NAME_HIT="deadbeefcafe" SHIM_PS_NAME_STATE=exited _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — **멈춘** 동명 컨테이너가 있어도 수집하지 않는다(docker ps -aq)"
# cr H-1 선례 — 값 없는 플래그는 전제 실패(3)다(러너의 `FAILED`(1)와 같은 값이 되지 않는다).
_run ./tools/prod-collection.sh --days-back
_expect_rc 3 "수집 — 값 없는 플래그는 전제 실패(3)다"
_expect_text '값이 없다' "수집 — 그 거절이 문면을 남긴다"
_run ./tools/prod-collection.sh --what-is-this 1
_expect_rc 3 "수집 — 알 수 없는 인자는 전제 실패(3)다"
JAVA_TOOL_OPTIONS='-Djdk.httpclient.HttpClient.log=all' _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — JAVA_TOOL_OPTIONS 에 JDK HttpClient 내부 로그 속성이 있으면 수집하지 않는다"
_expect_text 'jdk\.httpclient\.HttpClient\.log' "수집 — 문면이 지워야 할 속성 이름을 보인다"
_expect_no_log ' run --rm' "수집 — 그 거절이 compose run 앞에서 끝난다(JVM 옵션 축)"

# ── ① 창·상한·업종 인자 — 기본값을 지어내지 않는다 ─────────────────────────────
_col_reject() { # <이름> <인자…> — 전제 실패(3)이고 일회 컨테이너를 띄우지 않는다
  local name="$1"; shift
  _run ./tools/prod-collection.sh "$@"
  _expect_rc 3 "수집 — 인자 ${name}: 거절한다"
  _expect_no_log ' run --rm' "수집 — 인자 ${name}: 그 거절이 compose run 앞에서 끝난다"
}
_col_reject '창 없음' "${COL_BASE[@]}" --run-state-root "$RSROOT" \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--from 만' "${COL_BASE[@]}" --run-state-root "$RSROOT" --from 2026-11-01 \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--to 만' "${COL_BASE[@]}" --run-state-root "$RSROOT" --to 2026-11-20 \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--days-back + --from' "${COL_FIXED[@]}" --days-back 1
_col_reject '--days-back 0' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 0 \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--days-back -1' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back -1 \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--days-back 031' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 031 \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--days-back 공백' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back ' ' \
  --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--from 2026-13-40' "${COL_BASE[@]}" --run-state-root "$RSROOT" \
  --from 2026-13-40 --to 2026-11-20 --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--to 날짜 꼴 아님' "${COL_BASE[@]}" --run-state-root "$RSROOT" \
  --from 2026-11-01 --to 20261120 --categories construction --calls-per-day 7 --calls-total 9
_col_reject '--calls-per-day 0' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories construction --calls-per-day 0 --calls-total 9
_col_reject '--calls-per-day 정수 아님' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories construction --calls-per-day 7x --calls-total 9
_col_reject '--calls-total 없음' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories construction --calls-per-day 7
_col_reject '--categories 없음' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --calls-per-day 7 --calls-total 9
_col_reject '--run-state-root 없음' "${COL_BASE[@]}" --days-back 1 \
  --categories construction --calls-per-day 7 --calls-total 9
# **인자 주입**(우회 5) — 업종 값에 러너 플래그를 끼운다. 러너 인자는 배열로 넘어가므로 낱말 쪼개짐이
# 일어나지 않지만, 그 사실에 기대지 않고 **값의 모양**으로도 끊는다(겹 둘).
_col_reject '업종 값 주입' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories 'construction --bidvector.collection.calls-total=999999' --calls-per-day 7 --calls-total 9
_col_reject '업종 대문자' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories CONSTRUCTION --calls-per-day 7 --calls-total 9
_col_reject '업종 빈 칸' "${COL_BASE[@]}" --run-state-root "$RSROOT" --days-back 1 \
  --categories 'construction,' --calls-per-day 7 --calls-total 9

# ── ③ 실행 상태 root — 붙임의 원천을 묶는다 ────────────────────────────────────
_col_root_reject() { # <이름> <root>
  local name="$1" root="$2"
  _col_reject "실행 상태 root ${name}" "${COL_BASE[@]}" --run-state-root "$root" --days-back 1 \
    --categories construction --calls-per-day 7 --calls-total 9
}
_col_root_reject '상대 경로' runstate
_col_root_reject '없는 경로' "$T/no-such-runstate"
_col_root_reject '파일' "$T/env/prod.env"
# **저장소 안** — 실험 입력은 커밋되지 않는다(러너도 거부하지만 래퍼가 **먼저** 끊는다: 러너의 거부는
# 종료 1 이라 「예외」와 같은 값이다).
_col_root_reject '저장소 안' "$REPO_ROOT/reports"
# **심링크로 저장소 안** — 이름만 대조하면 지나간다(`readlink -f` 로 푼 경로를 본다).
ln -sfn "$REPO_ROOT/reports" "$T/rs-link"
_col_root_reject '심링크로 저장소 안' "$T/rs-link"
# **붙임 문법** — root 에 ':' 가 있으면 `run -v` 의 둘째 붙임·옵션(':ro' 뒤집기)을 만들 수 있다.
install -d -m 2770 "$T/rs:colon" || _die "콜론 fixture 를 만들지 못했다 — 판정 불가" 2
_col_root_reject "콜론이 든 경로" "$T/rs:colon"
# **실경로의 ':'** (M15/15U r1, cr low) — 인자 가지와 다른 자리다. 타깃에 ':' 가 든 심링크로만 닿는다.
ln -sfn "$T/rs:colon" "$T/rs-colon-link"
_col_root_reject "심링크 타깃에 콜론" "$T/rs-colon-link"
# **심링크된 저장소 경로로 래퍼를 부른다**(M15/15U r1, cr medium) — `REPO_ROOT` 는 `pwd` 가 심링크를
# 유지하는 **논리** 경로이고 비교 대상은 `readlink -f` 로 해석된 경로다. 둘을 그대로 맞대면 저장소
# **안**을 가리키는 root 가 통과한다(실측: 논리 비교 MISSED · 실경로 비교 CAUGHT). 그래서 래퍼는
# 논리·실경로 **양쪽**에 비교를 걸고, 이 단언이 그 겹을 잰다.
# **가짜 git 의 기대 루트를 그 호출에 맞춰 준다** — 안 그러면 `git -C` 가 어긋나(exit 98) run 이 **다른
# 사유로** 3 이 되고 이 단언이 공허하게 참이 된다(실측: 그 상태에서 실경로 비교를 걷는 변이가 GREEN).
# 문면 단언이 축을 못박는 자리다.
ln -sfn "$REPO_ROOT" "$T/repolink"
: > "$SHIM_LOG"; RUN_RC=0
SHIM_GIT_EXPECT_C="$T/repolink" "$T/repolink/tools/prod-collection.sh" "${COL_BASE[@]}" \
  --run-state-root "$REPO_ROOT/reports" --days-back 1 --categories construction \
  --calls-per-day 7 --calls-total 9 > "$T/out" 2> "$T/err" || RUN_RC=$?
_expect_rc 3 "수집 — 심링크된 저장소 경로로 불러도 저장소 안을 거절한다(논리 ↔ 실경로 비대칭)"
_expect_text '저장소 안이다' "수집 — 그 거절이 **저장소 포함 축**의 것이다(git 축이 아니다 — 공허한 3 을 가른다)"
_expect_no_log ' run --rm' "수집 — 그 거절이 compose run 앞에서 끝난다(저장소 포함 축)"

# ── ③-b 실행 상태 디렉터리 — **두 층**의 모양 (M15/15U r1) ─────────────────────
# `mkdir -p -m <mode>` 의 `-m` 은 **마지막 디렉터리에만** 적용되고 중간 디렉터리는 **호출자 umask** 로
# 만들어진다(실측: umask 0022 → 2755 · 0077 → 2700 · **0000 → 2777**). 마지막 꼴이 이 축의 사고다 —
# `notice-list` 에 other 쓰기가 열리면 다른 host 계정이 달 디렉터리를 **바꿔칠 수 있고** 그 순간 호출
# 상한 회계가 0 에서 다시 선다. 그래서 래퍼가 두 층을 각각 명시적으로 만들고 **층마다 다른 술어**를 본다.
#
# **모드가 층마다 다른 사유는 실측이다**(이 라운드에서 버릴 busybox 컨테이너로 쟀다): 컨테이너는 host 쪽
# **조상을 지나지 않는다** — bind 붙임은 데몬(root)이 경로를 풀고 컨테이너 안에서는 붙임 자리부터 권한이
# 재어진다. 조상이 2700 이어도 uid 가 다르고 **gid 만 같은** 컨테이너가 잎에 썼고(양성), 잎이 2700 이면
# 같은 호출이 실패했다(음성). 그래서 group 쓰기가 필요한 자리는 **잎 하나**이고 조상은 `2700` 이다.
#
# 이 fixture 가 앞 판의 혼선을 고친 자리다 — 앞 판의 양성 대조는 `notice-list` 를 **0700 으로 남긴 채**
# `_expect_rc 0` 을 단언했고(setgid 도 없었다), 그래서 「운영에서 거부될 상태」를 수집한다고 고정했다.
# **setgid 를 지우려면 `g-s` 가 필요하다**(이 라운드 실측) — `install -d -m 0700` 도 `chmod 0700` 도
# 디렉터리의 set-group-ID 를 **지우지 않는다**(둘 다 2700 이 나왔다). 그래서 음성 fixture 는 `chmod g-s`
# 를 쓴다. 그 사실 자체가 운영자에게 유리하다 — 권한을 고치러 `chmod 2700` 을 다시 쳐도 비트가 살아 있다.
_col_run_state_reject() { # <이름> <갈래 mode> <달 mode> [갈래에 더 걸 chmod]
  local name="$1" pm="$2" lm="$3" extra="${4:-}"
  rm -rf "${RSROOT:?}/notice-list"
  install -d -m "$pm" "$RSROOT/notice-list"
  [ -z "$extra" ] || chmod "$extra" "$RSROOT/notice-list"
  install -d -m "$lm" "$COL_MONTH_DIR"
  _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc 3 "수집 — 실행 상태 ${name}: 수집하지 않는다"
  _expect_no_log ' run --rm' "수집 — 실행 상태 ${name}: 그 거절이 compose run 앞에서 끝난다"
}
_col_run_state_reject '갈래에 setgid 가 없다' 2700 2770 g-s
_expect_text '갈래 디렉터리에 setgid 가 없다' "수집 — 그 문면이 setgid 축의 것이다(group 을 잇는 비트다)"
# **그 처방의 `sudo` 가 문면에 실제로 남는가**(M15/15V r1, cr H-1) — 앞 판은 그 단어를 큰따옴표 안
# 백틱에 담아 **명령 치환으로 실행**하고 출력에서는 지웠다(`** 가 필요하다**`). 비구성원 소유자에게
# `sudo` 없는 `chmod` 는 아무것도 고치지 못하므로 그 단어가 이 문장의 전부다.
# **단일 인용의 명령(`'sudo chmod …'`)이 아니라 그 사유 절을 본다** — 백틱을 되살리는 변이에서 명령
# 쪽은 살아 있고 **사유 절의 그 단어만** 사라졌다(실측: `sudo` 로만 보면 그 변이가 이 단언을 통과한다).
# **탈출한 꼴(`` \`sudo\` ``)도 이 단언을 붉힌다 — 의도다**(M15/15V r2, vr L-b). 처방은 둘(탈출·걷기)
# 가운데 **걷기**로 정했고(읽기에 낫다 — cr 가 그 선택을 되짚었다) 이 문면 단언은 그 결정을 문자열로
# 고정한다. 탈출로 돌아가려면 이 기대도 함께 바꾸는 것이 맞다(fail-closed — 조용히 둘 다 받으면
# 「어느 꼴이 이 저장소의 꼴인가」가 코드에서 사라진다).
_expect_text '\*\*sudo 가 필요하다\*\*' "수집 — setgid 처방의 **사유 절**에 sudo 라는 단어가 남아 있다(백틱 치환으로 사라지지 않았다)"
_col_run_state_reject '갈래에 group 쓰기가 열렸다' 2770 2770
_expect_text '달 디렉터리를 \*\*바꿔칠 수 있고\*\*' "수집 — 그 문면이 바꿔치기 사고를 가리킨다"
_expect_text 'setgid 를 함께 되세우려면 sudo' "수집 — group 쓰기 처방에도 **sudo 라는 단어**가 남아 있다"
# **rc 만 보던 셋에 문면을 붙인다**(M15/15V D-15V-8, cr L-5) — 같은 args 의 양성이 바로 뒤에 있어 오늘은
# 다른 축의 3 을 받을 길이 좁지만, 이 라운드에 스스로 겪은 공허함(`SHIM_GIT_EXPECT_C`)이 그 꼴이었다.
_col_run_state_reject '갈래가 other 에 열렸다' 2707 2770
_expect_text '갈래 디렉터리가 other 에 열려 있다' "수집 — 그 문면이 조상 other 축의 것이다"
_col_run_state_reject '달에 group 쓰기가 닫혔다' 2700 2750
_expect_text '달 디렉터리에 group 읽기·쓰기·탐색이 열려 있지 않다' "수집 — 그 문면이 잎 group 축의 것이다"
_col_run_state_reject '달이 other 에 열렸다' 2700 2777
_expect_text '달 디렉터리가 other 에 열려 있다' "수집 — 그 문면이 잎 other 축의 것이다"

# ── D-15V-1 — **root 도 술어 안이다**(cr M-1 · vr L-h) ─────────────────────────
# 앞 판은 root 에 대해 mode 를 한 번도 읽지 않았고, `RSROOT` fixture 가 언제나 `install -d -m 2700`
# (setgid 있음)이라 **그 축의 음성이 한 꼴도 없었다**. root 에 setgid 가 없으면 두 층의 group 이 래퍼의
# gid 로 박히고 컨테이너가 원장에 쓰지 못한다(실측: 버릴 컨테이너로 `쓰기 실패` · 러너 종료 1).
# **그 꼴의 rc 3 을 지금 내는 것은 조상 setgid 술어다**(M15/15V r1, vr L-2) — `-m` 을 걷은 뒤로는 조상이
# `700` 으로 생기기 때문이다. 그래서 아래 셋은 **문면 단언이 축을 못박는다**(rc 만 보면 다른 축의 3 을
# 받아 공허해지는 자리다 — verifier 가 그것을 실측했다).
_col_root_mode_reject() { # <이름> <root 에 걸 chmod> <기대 문면>
  local name="$1" mode="$2" want="$3"
  rm -rf "${RSROOT:?}/notice-list"
  chmod "$mode" "$RSROOT"
  _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc 3 "수집 — 실행 상태 root ${name}: 수집하지 않는다"
  _expect_text "$want" "수집 — 실행 상태 root ${name}: 그 문면이 그 축의 것이다"
  _expect_no_log ' run --rm' "수집 — 실행 상태 root ${name}: 그 거절이 compose run 앞에서 끝난다"
  chmod 2700 "$RSROOT"
}
_col_root_mode_reject 'setgid 없음' g-s '실행 상태 root 에 setgid 가 없다'
_col_root_mode_reject 'group 쓰기 열림' 2770 '실행 상태 root 에 group 쓰기가 열려 있다'
_col_root_mode_reject 'other 쓰기 열림' 2702 '실행 상태 root 에 other 쓰기가 열려 있다'

# ── D-15V-3 — **group 등식**(비트가 아니라 결과를 잰다) ────────────────────────
# 비트 술어는 「앞으로 만들 자리」만 묶으므로 **이미 있는 자리의 group 이 틀린 경우**를 보지 못한다 —
# 그것이 cr M-1 의 귀결이다. fixture 는 **호출자가 속한 보조 group** 으로 세운다(비구성원은 `chgrp` 를
# 치지 못하므로 임의 gid 는 쓸 수 없다. `chgrp` 는 **디렉터리**의 setgid 를 지우지 않으므로 — 이 라운드
# 실측 — 비트는 멀쩡한 채 gid 만 틀린 자리가 선다: 축이 섞이지 않는다).
# 보조 group 이 없는 계정에서는 이 꼴을 세울 수 없다 — 그때는 **통과로 적지 않고 판정 불가로 선다**.
COL_ALT_GID=""
for _g in $(id -G); do [ "$_g" != "$(id -g)" ] && { COL_ALT_GID="$_g"; break; }; done
[ -n "$COL_ALT_GID" ] \
  || _die "gid 불일치 fixture 를 세울 보조 group 이 없다(id -G 가 primary 하나뿐이다) — 이 축을 재지 못하므로 판정 불가다. 그 계정에 보조 group 을 하나 둔다" 2
_col_gid_reject() { # <이름> <어느 층> <기대 문면>
  local name="$1" which="$2" want="$3"
  rm -rf "${RSROOT:?}/notice-list"
  install -d -m 2700 "$RSROOT/notice-list"
  install -d -m 2770 "$COL_MONTH_DIR"
  chgrp "$COL_ALT_GID" "$which"
  _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc 3 "수집 — gid 불일치 ${name}: 수집하지 않는다"
  _expect_text "$want" "수집 — gid 불일치 ${name}: 그 문면이 group 등식 축의 것이다"
  _expect_no_log ' run --rm' "수집 — gid 불일치 ${name}: 그 거절이 compose run 앞에서 끝난다"
}
_col_gid_reject '조상' "$RSROOT/notice-list" '갈래 디렉터리의 group 이 실행 상태 root 의 group 과 다르다'
_col_gid_reject '잎' "$COL_MONTH_DIR" '달 디렉터리의 group 이 실행 상태 root 의 group 과 다르다'
# **양성 — 두 층의 gid 가 root 와 같으면 돈다**(위 둘이 공허하지 않음의 반대쪽).
rm -rf "${RSROOT:?}/notice-list"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 0 "양성 — 두 층을 래퍼가 만들면 gid 가 root 와 같다(등식이 전부 거절하는 술어가 아니다)"
if [ "$(stat -c '%g' "$COL_MONTH_DIR")" = "$(stat -c '%g' "$RSROOT")" ]; then
  _ok "수집 — 래퍼가 만든 달 디렉터리의 gid 가 root 의 gid 와 같다(커널 상속)"
else
  _bad "수집 — 래퍼가 만든 달 디렉터리의 gid 가 root 와 다르다($(stat -c '%g' "$COL_MONTH_DIR") ≠ $(stat -c '%g' "$RSROOT"))"
fi
# **기준값이 `id -g` 가 아니라 root 의 gid 임을 가른다**(M15/15V r1, vr M-2). 위 fixture 의 root gid 는
# 호출자의 primary gid 와 **같아서** 그 둘을 가르지 못했다 — `RUN_STATE_ROOT_GID="$(id -g)"` 변이가
# GREEN 이었고, 「커널 상속」 양성도 그 구성에서는 상속 없이 참이다(cr M-1 축이 CI 에서 안 보이는 구성).
# root 를 **보조 group** 으로 세우면 두 층이 그 gid 를 물려받고, 기준값을 `id -g` 로 바꾸는 변이는
# 불일치로 선다. 권한은 필요 없고 `chgrp` 는 디렉터리의 setgid 를 지우지 않는다(실측).
rm -rf "${RSROOT:?}/notice-list"
chgrp "$COL_ALT_GID" "$RSROOT"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 0 "양성 — root 의 gid 가 호출자 primary 와 다를 때도 수집한다(기준값은 root 의 gid 다)"
_col_alt_leaf="$(stat -c '%g' "$COL_MONTH_DIR" 2>/dev/null || true)"
if [ "$_col_alt_leaf" = "$COL_ALT_GID" ]; then
  _ok "수집 — 두 층이 root 의 보조 group(${COL_ALT_GID})을 물려받았다 — 「id -g」 로는 설명되지 않는 값이다"
else
  _bad "수집 — 달 디렉터리의 gid 가 ${_col_alt_leaf:-?} 로 root 의 ${COL_ALT_GID} 와 다르다(커널 상속이 서지 않았다)"
fi
unset _col_alt_leaf
chgrp "$(id -g)" "$RSROOT"
rm -rf "${RSROOT:?}/notice-list"
# **양성** — 두 층이 각각 2700·2770 이면 돈다(위 다섯이 공허하지 않음의 반대쪽).
rm -rf "${RSROOT:?}/notice-list"
install -d -m 2700 "$RSROOT/notice-list"
install -d -m 2770 "$COL_MONTH_DIR"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 0 "양성 — 갈래 2700 · 달 2770 이면 수집한다(위 다섯이 mode 축의 것이다)"

# **umask 에 기대지 않는다** — 래퍼가 처음 만드는 경우를 두 방향의 umask 로 잰다. `0000` 은 other 쓰기가
# 열리는 쪽이고 `0077` 은 group 이 닫히는 쪽이다. 둘 다에서 두 층의 모양이 같아야 한다.
for _um in 0000 0077 0022; do
  rm -rf "${RSROOT:?}/notice-list"
  : > "$SHIM_LOG"; RUN_RC=0
  ( umask "$_um"; ./tools/prod-collection.sh "${COL_ARGS[@]}" ) > "$T/out" 2> "$T/err" || RUN_RC=$?
  _expect_rc 0 "수집 — umask ${_um} 에서도 수집한다(래퍼가 두 층을 직접 만든다)"
  _pm="$(stat -c '%a' "$RSROOT/notice-list" 2>/dev/null || true)"
  _lm="$(stat -c '%a' "$COL_MONTH_DIR" 2>/dev/null || true)"
  if [ "$_pm" = 2700 ] && [ "$_lm" = 2770 ]; then
    _ok "수집 — umask ${_um} 에서 갈래 2700 · 달 2770 이다(umask 가 모드를 정하지 않는다)"
  else
    _bad "수집 — umask ${_um} 에서 갈래 ${_pm:-?} · 달 ${_lm:-?} 다(2700 · 2770 이어야 한다 — '-m' 이 중간 디렉터리에 안 먹는 자리다)"
  fi
done
unset _um _pm _lm

# **거절·미룬 run 은 자리를 남기지 않는다**(M15/15U r1, cr medium) — 디렉터리 생성이 전제 검사 **뒤**로
# 갔다는 측정이다. 앞 판은 첫 거절 run 이 이미 `notice-list/<달>` 을 만들어, 뒤의 fixture 가 「새로
# 만드는 것」이 아니라 「기존 자리의 mode 를 바꾸는 것」이 됐다(그 혼선이 양성 대조를 공허하게 만들었다).
_col_no_residue() { # <이름> <기대 종료 코드> <명령 앞에 붙일 env…>
  local name="$1" want="$2"; shift 2
  rm -rf "${RSROOT:?}/notice-list"
  : > "$SHIM_LOG"; RUN_RC=0
  env "$@" ./tools/prod-collection.sh "${COL_ARGS[@]}" > "$T/out" 2> "$T/err" || RUN_RC=$?
  _expect_rc "$want" "수집 — 자리 남김 ${name}: 종료 ${want}"
  if [ -z "$(ls -A "$RSROOT" 2>/dev/null)" ]; then
    _ok "수집 — 자리 남김 ${name}: root 아래에 아무것도 남기지 않았다"
  else
    _bad "수집 — 자리 남김 ${name}: root 아래에 $(ls -A "$RSROOT" | tr '\n' ' ')를 남겼다"
  fi
}
_col_no_residue '다른 커밋의 트리(3)' 3 SHIM_GIT_HEAD="$OTHER_SHA"
_hold_lock "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock"
_col_no_residue '프로젝트 잠금 경합(4)' 4
_release_lock
# **표본을 넷으로 늘린다**(M15/15V D-15V-7, cr L-1) — 절 제목이 전칭인데 표본이 둘뿐이던 자리다.
# **둘의 거절 자리가 다르다**(M15/15V r1, cr L-3 — 앞 판 주석은 둘을 「생성보다 앞에 서는 마지막 전제」로
# 묶어 적었고 그것은 거짓이다): 이름 충돌만 **생성 바로 앞**이고, `docker ps` 조회 실패는 그보다 앞인
# **잔존 검사**에서 끊긴다(가짜 docker 의 `ps` 분기 첫 줄이 `SHIM_PS_RC` 를 보므로 `name=` 분기에 닿지
# 않는다 — verifier 실측). 그래서 D-15V-7 을 되돌리는 변이를 단독으로 받는 것은 **첫 줄 하나**다.
# 그 사실을 문면 단언으로 코드에 적는다(rc 만 보는 음성은 축을 못박아야 한다는 교훈을 자기가 더한
# 자리에도 적용한다).
_col_no_residue '일회 이름이 전역에서 쓰임(3)' 3 SHIM_PS_NAME_HIT=deadbeefcafe
_expect_text '이미 다른 컨테이너가 쓰고 있다' "수집 — 그 표본이 **이름 충돌** 축에서 끊긴다(생성 바로 앞)"
_col_no_residue 'docker ps 조회 실패(3)' 3 SHIM_PS_RC=7
_expect_text '잔존을 세지 못했다' "수집 — 그 표본은 **잔존** 축에서 끊긴다(이름 충돌 축이 아니다)"
rm -rf "${RSROOT:?}/notice-list"

# **만든 뒤 실경로를 다시 본다**(M15/15U r1, vr M1) — 인자 검사는 **root** 의 실경로만 본다. root 는
# 저장소 **밖**인데 `notice-list` 가 저장소 **안**을 가리키는 심링크면, 붙는 자리가 저장소 안이 된다
# (실측: rc 0 · 달 디렉터리가 저장소 안에 생기고 그 자리가 붙었다). 러너 쪽 같은 가드는 컨테이너 경로가
# 고정이라 이 배치에서 **아무것도 보지 못하므로** 래퍼의 재검사가 유일한 겹이다.
# 타깃의 mode 는 **2700** 으로 둔다 — 그래야 mode 술어를 지나 경계 술어까지 닿는다(0755 로 두면
# other 축이 먼저 받아 이 단언이 다른 축을 재게 된다).
_col_dir_link_reject() { # <이름> <심링크 자리> <타깃> — 호출 전에 fixture 를 세워 둔다
  local name="$1" link="$2" target="$3"
  install -d -m 2700 "$target"
  ln -sfn "$target" "$link"
  _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc 3 "수집 — 심링크 ${name}: 수집하지 않는다"
  _expect_no_log ' run --rm' "수집 — 심링크 ${name}: 그 거절이 compose run 앞에서 끝난다"
}
# **갈래가 저장소 안을 가리킨다** — root 는 밖인데 붙는 자리가 저장소 안이 되는 바로 그 배치다.
rm -rf "${RSROOT:?}/notice-list"
_col_dir_link_reject '갈래가 저장소 안을 가리킨다' "$RSROOT/notice-list" "$REPO_LINK_TARGET"
_expect_text '심링크다' "수집 — 그 문면이 심링크 축의 것이다(mode 축이 아니다)"
# **root 밖을 가리키는 심링크도 거절한다** — 저장소 안이 아니어도 붙임의 원천이 root 밖으로 나간다
# (「붙이는 원천은 root 아래 래퍼가 지은 한 경로뿐」이라는 (2b) 의 주장이 그 자리다).
rm -rf "${RSROOT:?}/notice-list"
_col_dir_link_reject '갈래가 root 밖을 가리킨다' "$RSROOT/notice-list" "$T/outside-root"
_expect_text '심링크다' "수집 — root 밖을 가리키는 심링크도 같은 축으로 끊는다"
# **달 디렉터리 자신이 심링크인 꼴** — 갈래는 멀쩡하고 잎만 바꿔 끼운 자리다.
rm -rf "${RSROOT:?}/notice-list"
install -d -m 2700 "$RSROOT/notice-list"
_col_dir_link_reject '달 디렉터리가 저장소 안을 가리킨다' "$COL_MONTH_DIR" "$REPO_LINK_TARGET"
# **잎 꼴에도 문면을 못박는다**(M15/15V D-15V-8, vr L-e · cr L-5). 앞 판은 이 꼴의 `rc 3` 을 **심링크
# 술어가 아니라 `lstat` 이 내는 777 의 mode 축**이 받고 있었고(vr V6b: 잎의 심링크 거절 둘만 지우면
# GREEN), 그래서 (2b) 의 「세 꼴 문면이 심링크 축」은 둘만 참이었다.
_expect_text '달 디렉터리 가 심링크다' "수집 — 잎 꼴도 **심링크 축**이 받는다(mode 축이 아니다)"
rm -rf "${RSROOT:?}/notice-list"

# ── ② 서비스 키 env — 키 집합을 닫는다 ─────────────────────────────────────────
# 술어는 relay 와 **같은 것**이다(`tools/prod-env.sh` 의 둘째 env 파일 검사) — 사본 둘을 두지 않는다.
# 허용 이름은 손 목록이 아니라 `bidvector.koneps.service-key` 에서 Spring 의 두 사상으로 도출된다.
_col_env_reject() { # <이름> <덧붙일 줄…>
  local name="$1"; shift
  _koneps_env "$@"
  _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc 3 "수집 — 서비스 키 env ${name}: 거절한다"
  _expect_no_log ' run --rm' "수집 — 서비스 키 env ${name}: 그 거절이 compose run 앞에서 끝난다"
}
_col_env_reject 'JVM 통로 키' 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50'
_expect_text 'JAVA_TOOL_OPTIONS' "수집 — 문면이 지워야 할 키 이름을 보인다"
_col_env_reject 'DB 자격 통로 키' 'BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:postgresql://elsewhere/x'
_expect_text 'BIDVECTOR_PERSISTENCE_JDBCURL' "수집 — 문면이 그 키 이름을 보인다"
# **값은 어느 출력에도 없다** — 이 파일은 서비스 키를 담는다(위 파일에 그 자리값이 실제로 있다).
_expect_no_text 'fixture' "수집 — 거절 문면이 서비스 키 env 의 **값**을 옮기지 않는다"
_col_env_reject '따옴표 뒤 둘째 대입' 'BIDVECTOR_KONEPS_SERVICEKEY="k" BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil'
_col_env_reject '값의 ${VAR}' 'BIDVECTOR_KONEPS_SERVICEKEY=${PATH:?x}'
_col_env_reject '안 닫힌 따옴표' 'BIDVECTOR_KONEPS_SERVICEKEY="v'
_expect_text '통째로 버렸다' "수집 — 그 문면이 「필수 키가 없다」가 아니라 버려짐을 말한다"
_col_env_reject '값에 숨은 JDK 로그 속성' 'BIDVECTOR_KONEPS_SERVICEKEY=-Djdk.internal.httpclient.debug=true'
_expect_text 'jdk\.internal\.httpclient\.debug' "수집 — 문면이 그 속성 이름을 보인다"
# **키 집합 밖** — `base-url` 은 compose 축이다(이 파일의 키가 아니다).
printf 'BIDVECTOR_KONEPS_BASE_URL=https://example.invalid\n' > "$KONEPSENV"
chmod 600 "$KONEPSENV"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 서비스 키 env 에 허용 집합 밖의 키가 있으면 수집하지 않는다(base-url 은 compose 축이다)"
# **필수 키 부재** — 서비스 키가 없으면 러너가 기동 실패(1)로 끝난다. 래퍼가 3 으로 먼저 가른다.
printf '# 주석만 있고 키가 없다\n' > "$KONEPSENV"
chmod 600 "$KONEPSENV"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 서비스 키 env 에 필수 키가 없으면 수집하지 않는다"
_expect_text 'service-key' "수집 — 문면이 빠진 속성 이름을 보인다"
_koneps_env
chmod 644 "$KONEPSENV"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 서비스 키 env 가 other 에 열려 있으면 수집하지 않는다(이 파일은 서비스 키를 담는다)"
_koneps_env
_col_reject '--koneps-env-file 없음' --env-file "$T/env/prod.env" --state-dir "$T/state" \
  --project "$PROJ" --run-state-root "$RSROOT" --days-back 1 \
  --categories construction --calls-per-day 7 --calls-total 9
# **재는 경로와 넘기는 경로가 같은가**(M15/15U r1, vr L1 J) — 둘 다 같은 문자열이면 이 축은 미측정이다.
# 링크를 주면 원문과 해석된 경로가 **다른 글자**가 된다(relay 의 cr R-2 와 같은 축, 다른 파일).
_koneps_env
rm -rf "${RSROOT:?}/notice-list"
ln -sfn "$KONEPSENV" "$T/env/koneps-link.env"
_run ./tools/prod-collection.sh --env-file "$T/env/prod.env" --koneps-env-file "$T/env/koneps-link.env" \
  --state-dir "$T/state" --project "$PROJ" --run-state-root "$RSROOT" --days-back 1 \
  --categories construction --calls-per-day 7 --calls-total 9
_expect_rc 0 "수집 — 링크로 준 서비스 키 env 도 돈다"
_expect_log "--env-from-file $(readlink -f "$KONEPSENV")" "수집 — 컨테이너에 **해석된** 경로를 넘긴다(재는 것과 같은 파일)"
_expect_no_log "--env-from-file $T/env/koneps-link.env" "수집 — 원문 경로를 그대로 넘기지 않는다"
rm -rf "${RSROOT:?}/notice-list"

# ── 양성 — argv 로 잰다 ────────────────────────────────────────────────────────
_koneps_env
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 0 "수집 — 양성: 잔존 0 · 같은 커밋 · 같은 프로젝트 · 서비스 키 env 가 허용 집합이면 수집한다"
_expect_log "run .*--rm .*--pull never .*--name ${PROJ}-app-run-collection" "수집 — 자기 일회 컨테이너 이름을 고정해 띄운다"
_expect_log " -v ${COL_MONTH_DIR}:${COL_IN_CONTAINER}" "수집 — 붙임은 달 디렉터리 하나 → 고정된 컨테이너 경로다"
_expect_log "--env-from-file ${KONEPSENV}" "수집 — 서비스 키 env 가 **일회 컨테이너까지** 간다"
_expect_log "--env-file $T/env/prod.env" "수집 — 스택 env 파일은 그대로 compose 에 간다(파일이 둘이다)"
_expect_log "-e SPRING_MAIN_WEB_APPLICATION_TYPE=none" "수집 — 러너 run 은 HTTP 표면을 끈다"
_expect_log "--bidvector.collection.mode=once" "수집 — 러너 스위치를 준다"
_expect_log "--bidvector.collection.from=${KST_BACK1}" "수집 — --days-back 1 의 from 은 어제(KST)다"
_expect_log "--bidvector.collection.to=${KST_TODAY}" "수집 — --days-back 1 의 to 는 오늘(KST)다"
_expect_log "--bidvector.collection.categories=construction,service" "수집 — 업종을 그대로 넘긴다"
_expect_log "--bidvector.collection.calls-per-day=50" "수집 — 일 상한을 그대로 넘긴다"
_expect_log "--bidvector.collection.calls-total=50" "수집 — 총 상한을 그대로 넘긴다"
_expect_log "--bidvector.collection.run-state-dir=${COL_IN_CONTAINER}" "수집 — 러너가 받는 실행 상태 경로는 **고정된 컨테이너 경로**다(host 경로가 아니다)"
# **달 디렉터리는 래퍼가 `date` 로 짓는다 — 인자에서 오지 않는다.** root 밖에는 아무것도 만들지 않는다.
if [ -d "$COL_MONTH_DIR" ]; then _ok "수집 — 달 디렉터리를 root 아래에 만든다(${KST_MONTH})"; else _bad "수집 — 달 디렉터리를 만들지 않았다: $COL_MONTH_DIR"; fi
if [ "$(ls -A "$RSROOT")" = notice-list ]; then _ok "수집 — root 아래에 만든 것은 notice-list 하나다"; else _bad "수집 — root 아래에 다른 것이 생겼다: $(ls -A "$RSROOT" | tr '\n' ' ')"; fi
if [ ! -e "$T/notice-list" ] && [ ! -e "$T/state/notice-list" ]; then _ok "수집 — root 밖에는 아무것도 만들지 않았다"; else _bad "수집 — root 밖에 디렉터리를 만들었다"; fi
# cr L-3 선례 — `' run --rm'` 은 `' run '` 보다 느슨하다(첫 플래그가 다른 `run` 이 생기면 지나간다).
# 느슨해진 사유는 플래그 지원 질의(`run --help`) 하나뿐이므로 그 **수**를 세어 원래 세기를 되찾는다.
col_run_n="$(grep -cE '^docker .* run ' "$SHIM_LOG" || true)"
if [ "$col_run_n" = 2 ]; then
  _ok "수집 — 양성 run 에서 ' run ' 줄은 둘이다(도움말 질의 하나 + 일회 컨테이너 하나)"
else
  _bad "수집 — ' run ' 줄이 ${col_run_n} 개다(둘이어야 한다 — 거절 갈래의 ' run --rm' 단언이 그만큼 느슨해진다)"
fi
# **붙임은 하나다** — 둘째 `-v` 가 생기면 여기서 선다(「인자가 붙임을 실을 수 없다」의 계수 축).
col_mount_n="$(grep -oE ' -v [^ ]+' "$SHIM_LOG" | grep -c '' || true)"
if [ "$col_mount_n" = 1 ]; then _ok "수집 — 붙임은 정확히 하나다 (${col_mount_n})"; else _bad "수집 — 붙임이 ${col_mount_n} 개다(하나여야 한다)"; fi
# **`--from/--to` 갈래** — 창 계산 축이 아니라 인자 축이다(자정 경계와 무관하다).
_run ./tools/prod-collection.sh "${COL_FIXED[@]}"
_expect_rc 0 "수집 — 양성: --from/--to 갈래도 돈다(첫 적재·메우기)"
_expect_log "--bidvector.collection.from=2026-11-01" "수집 — --from 을 그대로 넘긴다"
_expect_log "--bidvector.collection.to=2026-11-20" "수집 — --to 를 그대로 넘긴다"
_expect_log "--bidvector.collection.categories=construction" "수집 — 업종 하나도 그대로 넘긴다"
_expect_no_log "days-back" "수집 — 래퍼의 인자 이름이 러너에 새지 않는다"
# **달을 넘는 창의 달 디렉터리는 `to` 의 달이다**(M15/15U r1, vr L1 E) — 앞 판의 fixture 둘은 `from` 과
# `to` 가 **같은 달**이라 `${FROM%-*}` 로 바꾸는 변이가 GREEN 이었다. 창을 달 둘에 걸쳐 가른다.
rm -rf "${RSROOT:?}/notice-list"
_run ./tools/prod-collection.sh "${COL_BASE[@]}" --run-state-root "$RSROOT" \
  --from 2026-09-15 --to 2026-10-14 --categories construction --calls-per-day 7 --calls-total 9
_expect_rc 0 "수집 — 양성: 달을 넘는 --from/--to 창도 돈다"
_expect_log " -v ${RSROOT}/notice-list/2026-10:" "수집 — 달을 넘는 창의 달 디렉터리는 **to** 의 달이다(from 이 아니다)"
_expect_no_log " -v ${RSROOT}/notice-list/2026-09:" "수집 — from 의 달을 쓰지 않는다"
rm -rf "${RSROOT:?}/notice-list"
# **KST 축 — 24시간 판별한다**(M15/15V D-15V-10, vr L-c). 앞 판은 `Pacific/Kiritimati`(UTC+14) 하나만
# 썼는데 그 구역은 KST 보다 5시간 앞이라 **KST 19:00~23:59 에만** 날짜가 다르다 — 그 밖 시각의 CI 에서는
# `TZ=Asia/Seoul` 제거 변이가 초록이었다. 반대 방향 구역(`Etc/GMT+12` = UTC-12, KST 보다 21시간 뒤라
# **KST 00:00~20:59 에** 날짜가 다르다)을 함께 써서 두 창의 합집합이 하루를 덮게 한다.
for _tz in Pacific/Kiritimati Etc/GMT+12; do
  rm -rf "${RSROOT:?}/notice-list"
  : > "$SHIM_LOG"; RUN_RC=0
  TZ="$_tz" ./tools/prod-collection.sh "${COL_ARGS[@]}" > "$T/out" 2> "$T/err" || RUN_RC=$?
  _expect_rc 0 "수집 — host TZ 가 ${_tz} 여도 돈다"
  _expect_log "--bidvector.collection.to=${KST_TODAY}" "수집 — ${_tz} 에서도 창의 to 는 **KST** 의 오늘이다"
  _expect_log " -v ${RSROOT}/notice-list/${KST_MONTH}:" "수집 — ${_tz} 에서도 달 디렉터리를 **KST** 로 짓는다"
done
unset _tz
# **그 합집합이 하루를 덮는다는 것을 이 자리에서 센다** — 두 구역 가운데 **적어도 하나**가 지금 KST 와
# 다른 날짜여야 위 단언이 공허하지 않다(둘 다 같은 날짜인 시각이 있으면 그 시각의 CI 에서 변이가 초록이다).
_kst_now="$(TZ=Asia/Seoul date +%F)"
_kir="$(TZ=Pacific/Kiritimati date +%F)"
_gmt12="$(TZ=Etc/GMT+12 date +%F)"
if [ "$_kir" != "$_kst_now" ] || [ "$_gmt12" != "$_kst_now" ]; then
  _ok "수집 — 두 구역 가운데 적어도 하나가 지금 KST 와 다른 날짜다(KST ${_kst_now} · UTC+14 ${_kir} · UTC-12 ${_gmt12}) — 위 단언이 지금 시각에 판별력이 있다"
else
  _bad "수집 — 두 구역 모두 KST 와 같은 날짜다(${_kst_now}) — 지금 시각에는 KST 축 단언이 공허하다(구역 선택을 고친다)"
fi
unset _kst_now _kir _gmt12
# **붙임의 원천은 실경로다**(M15/15V D-15V-8, vr V14 · cr L-2). 앞 판의 그 단언은 `COL_MONTH_DIR` 와
# **글자까지 같아** 아무것도 재지 않았다(fixture 에 심링크 성분이 없고 두 층의 심링크는 전부 거절된다).
# **root 자신은 거절되지 않는 유일한 층**이므로 거기서 잰다 — root 를 심링크로 주고 `-v` 에 실린 값이
# 해석된 경로인지 본다.
rm -rf "${RSROOT:?}/notice-list"
ln -sfn "$RSROOT" "$T/rs-root-link"
: > "$SHIM_LOG"; RUN_RC=0
./tools/prod-collection.sh "${COL_BASE[@]}" --run-state-root "$T/rs-root-link" --days-back 1 \
  --categories construction --calls-per-day 7 --calls-total 9 > "$T/out" 2> "$T/err" || RUN_RC=$?
_expect_rc 0 "수집 — root 를 심링크로 줘도 돈다(두 층과 달리 root 는 받는다)"
_expect_log " -v ${RSROOT}/notice-list/${KST_MONTH}:${COL_IN_CONTAINER}" "수집 — 붙임의 원천은 **해석된** 경로다(심링크 원문이 아니다)"
_expect_no_log " -v $T/rs-root-link/" "수집 — 심링크 원문을 붙임의 원천으로 쓰지 않는다"
rm -rf "${RSROOT:?}/notice-list"
# **러너의 술어를 셸에 사본으로 두지 않는다**(과잉 금지) — 31일 넘는 창·미래는 러너가 기동 실패로
# 드러낸다. 래퍼는 그것을 다시 검사하지 않으므로 **통과**해야 한다.
_run ./tools/prod-collection.sh "${COL_BASE[@]}" --run-state-root "$RSROOT" \
  --from 2026-01-01 --to 2026-11-20 --categories construction --calls-per-day 7 --calls-total 9
_expect_rc 0 "수집 — 31일 넘는 창은 래퍼가 막지 않는다(러너의 술어를 셸에 베껴 적지 않는다)"
_expect_log "--bidvector.collection.from=2026-01-01" "수집 — 그 창을 그대로 러너에 넘긴다"

# ── ④ 종료 코드 — 러너 다섯과 래퍼 둘이 겹치지 않게 옮겼다 ────────────────────
# 러너 `3`(`ALREADY_RUNNING` — 기다리면 풀린다)과 래퍼 `3`(전제 실패 — 고쳐야 한다), 러너
# `4`(`UNLOCKABLE` — 기다려도 안 풀린다)와 래퍼 `4`(미룸 — 기다리면 풀린다)는 **뜻이 정확히 반대**다.
# 거동은 대표 구간으로, 가지의 꼴은 아래 등식이 구조로 닫는다(v G3 와 같은 교환).
for _rc_pair in "0 0" "1 1" "2 2" "3 5" "4 6" "5 7" "6 7" "7 7" "9 7" \
                "125 7" "126 7" "127 7" "128 7" "137 7" "143 7" "255 7"; do
  _rc_in="${_rc_pair%% *}" _rc_want="${_rc_pair##* }"
  SHIM_RUN_RC="$_rc_in" _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc "$_rc_want" "수집 — 러너 종료 ${_rc_in} → 래퍼 ${_rc_want}"
done
unset _rc_pair _rc_in _rc_want
SHIM_RUN_RC=3 _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_text 'ALREADY_RUNNING' "수집 — 그 줄이 옮긴 코드의 원래 이름을 보인다"
SHIM_RUN_RC=4 _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_text 'UNLOCKABLE' "수집 — 그 줄이 옮긴 코드의 원래 이름을 보인다"
SHIM_RUN_RC=137 _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_text '예상 밖의 종료 코드 137' "수집 — 모르는 코드는 **원래 값을 문면에 담아** 7 로 난다"
_exit_code_equation_check 수집 CollectionExitCode \
  app/src/main/kotlin/bidvector/app/collection/CollectionLines.kt tools/prod-collection.sh

# ── 잠금 — 미룸(4) ↔ 전제 실패(3) ──────────────────────────────────────────────
_hold_lock "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 4 "수집 — 프로젝트 잠금이 잡혀 있으면 **미룬다**(4) — 전제 실패(3)가 아니다"
_expect_no_log ' run --rm' "수집 — 그 미룸이 일회 컨테이너 앞에서 끝난다"
_release_lock
_hold_lock "$T/state/.lock"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 4 "수집 — 상태 디렉터리 잠금이 잡혀 있어도 **미룬다**(4)"
_release_lock
rm -f "$T/state/.lock"
chmod 500 "$T/state"
_run ./tools/prod-collection.sh "${COL_ARGS[@]}"
_expect_rc 3 "수집 — 잠금 **파일을 열지 못하면** 전제 실패(3)다"
chmod 700 "$T/state"
for frc in 1 64 65 70 127 137; do
  SHIM_FLOCK_RC="$frc" _run ./tools/prod-collection.sh "${COL_ARGS[@]}"
  _expect_rc 3 "수집 — flock 종료 ${frc} 는 전제 실패(3) — 경합으로 접지 않는다"
done
rm -rf "${RSROOT:?}/notice-list"

# 신호 경로 — 러너 셋이 **각자의** 일회 컨테이너만 치운다. **HUP 도 건다**(cr L-1): §9.2 가 끊긴
# ssh 세션·`KillSignal=` 을 바꾼 unit 에 돌리는 갈래이고, trap 이 어긋나면 **운영 DB 자격을 받은 일회
# 컨테이너가 남는** 바로 그 경로다. 훑기에만 있던 것을 셋 모두에 둔다.
SIG_SCRIPT=./tools/prod-evaluation-commit.sh
SIG_ONEOFF_SUFFIX=evaluation
SIG_ARGS=("${EVAL_ARGS[@]}")
_signal_case TERM 143
_signal_case HUP 129
SIG_SCRIPT=./tools/prod-notification-relay.sh
SIG_ONEOFF_SUFFIX=relay
SIG_ARGS=("${RELAY_ARGS[@]}")
_signal_case TERM 143
_signal_case HUP 129
# M15/15U — 넷째 러너. 같은 갈래를 받는다: `TimeoutStartSec` 상한에 걸려 죽은 run 이 **운영 DB 자격과
# 서비스 키를 받은 일회 컨테이너**를 남기지 않아야 한다(이 러너는 그 둘을 함께 쥔다).
_koneps_env
SIG_SCRIPT=./tools/prod-collection.sh
SIG_ONEOFF_SUFFIX=collection
SIG_ARGS=("${COL_ARGS[@]}")
_signal_case TERM 143
_signal_case HUP 129
rm -rf "${RSROOT:?}/notice-list"

echo "== runbook — ExecStopPost 가 조건 없이 돌지 않는가"
# 문서에는 구조 술어가 없다 — 이 단언은 **텍스트 술어**다(unit 예시가 있는 층이 문서뿐이다). 조건 없는
# `ExecStopPost=…docker rm -f <고정 이름>` 은 수동 run 이 도는 중에 타이머가 발화하면(그 run 은 잠금에서
# exit 3) **남의 컨테이너**를 지운다.
#
# **글자 하나가 아니라 조건식 전문을 잠근다**(v r2 F-1): 조항 넷(성공 건너뜀 · `exit 3` 건너뜀 ·
# **`exit 4` 건너뜀** · 그 뒤의 `docker rm -f <…-app-run-sweep>`)과 **`$$` 형태**까지 본다.
# `SERVICE_RESULT` 글자만 보던 앞 판은 「`|| [ "$$EXIT_STATUS" = 3 ]` 삭제」와
# 「`${SERVICE_RESULT}`(systemd 가 미리 빈 값으로 펼친다)」가 둘 다 초록이었다 — exit 3 조항은
# v M-1 시나리오를 막는 바로 그 조항이다. **`4` 조항은 M15/15M 이 더했다**: 미룸은 자기 일회
# 컨테이너를 띄우지도 않으므로(위 `_expect_no_log ' run '`) 지울 것이 없고, 조건이 없으면 그때도
# 돌아 **남의 컨테이너**를 지운다(exit 3 조항과 같은 사유).
#
# **M15/15Q — 러너가 셋이 됐다**(훑기·평가 커밋·relay). 이름 하나를 박던 꼬리를 `-app-run-<이름>` 으로
# 넓히고 **개수 등식은 그대로** 둔다 — 새 unit 이 조건 없는 줄을 들고 들어오면 그 등식이 깨진다.
# 러너 이름 집합도 함께 센다(꼬리를 `.*` 로 열면 아무 이름이나 통과해 등식이 공허해진다).
RB=docs/runbook/m7-7f-production.md
ESP_WANT='^ExecStopPost=-/bin/sh -c .\[ "\$\$SERVICE_RESULT" = success \] \|\| \[ "\$\$EXIT_STATUS" = 3 \] \|\| \[ "\$\$EXIT_STATUS" = 4 \] \|\| .*docker rm -f [^ ]*-app-run-(sweep|evaluation|relay|collection)'
esp_n="$(grep -cE '^ExecStopPost=' "$RB" || true)"
esp_cond="$(grep -cE "$ESP_WANT" "$RB" || true)"
if [ "$esp_n" -ge 1 ] && [ "$esp_n" = "$esp_cond" ]; then
  _ok "runbook 의 ExecStopPost ${esp_n} 줄이 모두 조건식 전문과 같다(성공·exit 3·exit 4 건너뜀 · \$\$ 형태)"
else
  _bad "runbook 의 ExecStopPost ${esp_n} 줄 중 ${esp_cond} 만 조건식 전문과 같다 — 조항이 빠지거나 \$\$ 형태가 아니면 남의 run 을 지우는 길이 다시 열린다"
fi
# **러너 셋이 각자의 이름을 지운다** — 한 unit 의 줄이 남의 일회 컨테이너 이름을 들면 그 unit 의 실패가
# 남의 run 을 지운다. 이름 집합의 크기로 센다(같은 이름 셋이면 1 이다).
# **추출을 `^ExecStopPost=` 줄에 묶는다**(cr L-4): 문서 전체에서 긁으면 §9.4 의 **산문** 줄
# (`docker rm -f <프로젝트>-app-run-sweep`)도 집어, unit 의 그 줄을 지우고 산문만 남겨도 집합 등식이
# 초록이었다. 이제 세는 모집단은 `esp_n` 이 세는 바로 그 줄들이다.
esp_names="$(grep -E '^ExecStopPost=' "$RB" | grep -oE 'docker rm -f [^ ]*-app-run-[a-z]+' | sed 's/.*-app-run-//' | sort -u | tr '\n' ' ')"
if [ "$esp_names" = "collection evaluation relay sweep " ]; then
  _ok "runbook 의 ExecStopPost 가 러너 넷의 **서로 다른** 일회 이름을 지운다(${esp_names})"
else
  _bad "runbook 의 ExecStopPost 가 지우는 이름 집합이 {collection, evaluation, relay, sweep} 가 아니다: ${esp_names}"
fi

# M15/15M(cr H-1) — **`SuccessExitStatus=4` 가 drop-in 에 있어야** 미룸이 `OnFailure` 를 끌지 않는다.
# 스크립트 쪽만 고치면 종료 코드는 갈렸는데 systemd 는 여전히 둘 다 실패로 보고, 경보는 그대로 울린다
# (거동이 갈리는 자리가 둘이다 — 한쪽만 고친 판이 조용히 통과하지 않게 여기서 함께 본다).
#
# **자리까지 묻는다**(cr N-4 · N-2): 그 줄은 `10-defer.conf` 블록 안에 있어야 한다. 경보 drop-in
# (`20-alert.conf`)에 있으면 경보를 끄는 `rm` 한 줄이 **미룸 의미까지** 되돌린다 — 그 host 에서는
# 반입과 겹친 미룸이 다시 unit failed 로 찍힌다. `ExecStopPost` 단언과 같은 모양으로 **개수 등식**을
# 건다(문서 어디에 있어도 초록이던 앞 판의 느슨함을 없앤다). 숫자 경계는 `14`·`40` 을 배제한다.
#
# **M15/15Q — drop-in 이 셋이 됐다.** 블록 이름을 하나 박던 자리를 `*/10-defer.conf` 전수로 넓히고
# 개수 등식은 그대로 둔다. awk 는 그 주석 줄에서 켜지고 닫는 울타리에서 **꺼진다**(`exit` 가 아니다 —
# 블록이 여럿이라 첫 블록에서 멈추면 나머지가 안 보이고, 그 느슨함이 「어디에 있어도 초록」을 되살린다).
defer_block="$(awk '/^# \/etc\/systemd\/system\/[a-z-]+\.service\.d\/10-defer\.conf$/ {inb = 1; next} inb && /^```$/ {inb = 0; next} inb {print}' "$RB")"
ses_total="$(grep -cE '^SuccessExitStatus=' "$RB" || true)"
ses_in="$(printf '%s\n' "$defer_block" | grep -cE '^SuccessExitStatus=([^#]*[^0-9])?4([^0-9]|$)' || true)"
if [ "$ses_total" -ge 1 ] && [ "$ses_total" = "$ses_in" ]; then
  _ok "runbook 의 SuccessExitStatus ${ses_total} 줄이 모두 10-defer.conf 블록 안에서 4 를 뺀다"
else
  _bad "SuccessExitStatus 줄 ${ses_total} 중 10-defer.conf 블록 안의 4 는 ${ses_in} 이다 — 자리가 경보 drop-in 이면 경보를 끄는 rm 이 미룸 의미까지 되돌린다"
fi
# **relay 의 drop-in 은 `5` 도 빼야 한다**(M15/15Q). 러너의 `LEASE_BUSY` 를 옮긴 코드이고 「기다리면
# 풀린다」라 미룸과 같은 갈래다 — 빼지 않으면 임대가 겹칠 때마다 경보가 한 통 온다. 반대로 `6`
# (`ENV_SUPPRESSED`, 옛 4)은 **빼면 안 된다**: 기다려도 풀리지 않는 설정 오류다. 둘을 함께 센다.
# **relay unit 이 평가 커밋 뒤에 선다**(cr M-3). 타이머 셋이 `Persistent=true` 라 창을 놓친 뒤 부팅하면
# systemd 가 밀린 run 들을 거의 동시에 띄우고, 잠금 둘은 **직렬화는 하지만 순서를 정하지 않는다** —
# relay 가 먼저 잡으면 그 catch-up run 은 평가 커밋의 행 없이 돌고 끝난다. 그 한 줄이 그 순서를 세운다.
# **M15/15U — 사슬이 넷이 됐다**(수집 → 훑기 → 평가 커밋 → relay). 한 unit 의 줄만 세던 앞 판을
# **`(unit, After 값)` 쌍 집합 등식**으로 넓힌다 — 줄이 빠지든 남의 unit 으로 옮겨지든 값이 바뀌든 붉다.
# 평가 커밋이 수집 뒤에 서면 그 run 이 **그 주기의 공고를 보고** 판정한다(relay 의 `After=` 와 같은 뜻).
# relay 에 수집 간선을 다시 걸지 않는다 — systemd 의 순서는 그래프이고 relay → 평가 커밋 → 수집 경로가
# 이미 서 있다(간선을 겹쳐 걸면 사슬이 늘 때마다 모든 unit 을 고쳐야 한다).
AFTER_WANT_SET="$(printf '%s\n' \
  'bidvector-evaluation-commit|bidvector-collection.service' \
  'bidvector-notification-relay|bidvector-evaluation-commit.service' \
  'bidvector-notification-sweep|bidvector-collection.service' | sort)"
after_pairs="$(awk '
  /^# \/etc\/systemd\/system\/[a-z@-]+\.service$/ {
    unit = $2; sub(/^\/etc\/systemd\/system\//, "", unit); sub(/\.service$/, "", unit)
    inb = 1; next
  }
  inb && /^```$/ { inb = 0; next }
  inb && /^[[:space:]]*After[[:space:]]*=/ {
    line = $0; sub(/^[^=]*=[[:space:]]*/, "", line); sub(/[[:space:]]*$/, "", line)
    print unit "|" line
  }' "$RB" | sort || true)"
# ── M15/15U r1(vr L3) — **타이머 시각과 시작 상한도 게이트 입력이다** ───────────
# 앞 판은 이 값들이 문서층에만 있어 `07:10`→`07:45` · `TimeoutStartSec=15m`→`45m` 변이가 **GREEN** 이었다.
# 그 둘은 거동이다: 시각은 사슬의 순서(수집 → 훑기 07:30 → 평가 07:35 → relay 07:40)를 정하고, 상한은
# 「잠금을 쥔 채 매달리는 run 을 끊는 자리」다(§9.2). 두 쌍 집합을 고정 집합과 등식으로 묶는다 —
# 무엇이 더해지든 빠지든 값이 바뀌든 붉다. 새 unit 이 정당하면 기대 집합을 **함께** 고친다(래칫).
#
# **시각은 집합 등식에 더해 순서까지 본다** — 집합만 보면 수집과 훑기의 분을 **맞바꾼** 변이가 통과한다.
ONCAL_WANT_SET="$(printf '%s\n' \
  'bidvector-cert-check|*-*-* 09:00 Asia/Seoul' \
  'bidvector-collection|*-*-* 07:10 Asia/Seoul' \
  'bidvector-collection|*-*-* 13:10 Asia/Seoul' \
  'bidvector-evaluation-commit|*-*-* 07:35 Asia/Seoul' \
  'bidvector-evaluation-commit|*-*-* 13:35 Asia/Seoul' \
  'bidvector-notification-relay|*-*-* 07:40 Asia/Seoul' \
  'bidvector-notification-relay|*-*-* 13:40 Asia/Seoul' \
  'bidvector-notification-sweep|*-*-* 07:30 Asia/Seoul' \
  'bidvector-notification-sweep|*-*-* 13:30 Asia/Seoul' | sort)"
oncal_pairs="$(awk '
  /^# \/etc\/systemd\/system\/[a-z@-]+\.timer$/ {
    unit = $2; sub(/^\/etc\/systemd\/system\//, "", unit); sub(/\.timer$/, "", unit)
    inb = 1; next
  }
  inb && /^```$/ { inb = 0; next }
  inb && /^[[:space:]]*OnCalendar[[:space:]]*=/ {
    line = $0; sub(/^[^=]*=[[:space:]]*/, "", line); sub(/[[:space:]]*$/, "", line)
    print unit "|" line
  }' "$RB" | sort || true)"
if [ "$oncal_pairs" = "$ONCAL_WANT_SET" ]; then
  _ok "runbook 의 타이머 시각 쌍 집합이 기대와 정확히 같다(수집 07:10·13:10 이 사슬의 머리다 · 인증서 감시 09:00)"
else
  _bad "타이머 OnCalendar 쌍이 기대와 다르다 — 받은 것: [$(printf '%s' "$oncal_pairs" | tr '\n' ';')] · 기대: [$(printf '%s' "$ONCAL_WANT_SET" | tr '\n' ';')]"
fi
# **순서** — 수집의 두 시각이 훑기·평가 커밋·relay 의 같은 창보다 앞이어야 그 주기의 평가가 그 run 의
# 공고를 본다. 분을 맞바꾼 변이는 집합 등식을 통과하므로 여기서 받는다.
oncal_order_bad=0
for _slot in 07 13; do
  _col_min="$(printf '%s\n' "$oncal_pairs" | sed -nE "s/^bidvector-collection\|\*-\*-\* ${_slot}:([0-9]{2}) .*/\1/p" | head -n1)"
  for _u in bidvector-notification-sweep bidvector-evaluation-commit bidvector-notification-relay; do
    _other="$(printf '%s\n' "$oncal_pairs" | sed -nE "s/^${_u}\|\*-\*-\* ${_slot}:([0-9]{2}) .*/\1/p" | head -n1)"
    [ -n "$_col_min" ] && [ -n "$_other" ] && [ "$((10#$_col_min))" -lt "$((10#$_other))" ] || oncal_order_bad=$((oncal_order_bad + 1))
  done
done
if [ "$oncal_order_bad" -eq 0 ]; then
  _ok "runbook 의 수집 시각이 두 창 모두에서 훑기·평가 커밋·relay 보다 앞이다(사슬의 머리)"
else
  _bad "수집 시각이 뒤 러너보다 앞이 아닌 쌍이 ${oncal_order_bad} 개다 — 그 주기의 평가는 이 run 의 공고를 보지 못한다"
fi
# **시작 상한** — 네 unit 전부 같은 값이고, 그 값은 수집 창(07:10)과 훑기(07:30) 사이 20 분보다 작아야
# 그 주기의 사슬이 굶지 않는다. 값 집합과 그 부등식을 함께 본다.
# `bidvector-alert@` 는 경보 unit 이고 값이 다르다(2m — 경보가 매달려 자리를 쥐지 않게 한다, §9.5).
# 그 줄도 이 등식에 들어온다 — 모집단을 「unit 블록 전수」로 두는 쪽이 새 unit 을 놓치지 않는다.
TSS_WANT_SET="$(printf '%s\n' \
  'bidvector-alert@|2m' \
  'bidvector-collection|15m' 'bidvector-evaluation-commit|15m' \
  'bidvector-notification-relay|15m' 'bidvector-notification-sweep|15m' | sort)"
tss_pairs="$(awk '
  /^# \/etc\/systemd\/system\/[a-z@-]+\.service$/ {
    unit = $2; sub(/^\/etc\/systemd\/system\//, "", unit); sub(/\.service$/, "", unit)
    inb = 1; next
  }
  inb && /^```$/ { inb = 0; next }
  inb && /^[[:space:]]*TimeoutStartSec[[:space:]]*=/ {
    line = $0; sub(/^[^=]*=[[:space:]]*/, "", line); sub(/[[:space:]]*$/, "", line)
    print unit "|" line
  }' "$RB" | sort || true)"
if [ "$tss_pairs" = "$TSS_WANT_SET" ]; then
  _ok "runbook 의 TimeoutStartSec 쌍 집합이 기대와 정확히 같다(러너 넷은 15m — 수집 창의 20 분 안이다 · 경보는 2m)"
else
  _bad "TimeoutStartSec 쌍이 기대와 다르다 — 받은 것: [$(printf '%s' "$tss_pairs" | tr '\n' ';')] · 기대: [$(printf '%s' "$TSS_WANT_SET" | tr '\n' ';')]"
fi
# **부등식을 코드로 세운다**(M15/15V D-15V-8, vr L-d). 위 주석과 §14.3 이 「그 상한은 수집 창과 훑기 사이
# 간격보다 작아야 그 주기의 사슬이 굶지 않는다」고 주장하는데 **그 부등식이 코드에 없었다** — 기대 집합을
# 함께 고치는 변이(vr V12c: 수집을 25m 로)가 GREEN 이었다. 집합 등식은 「오늘의 값」을 묶고, 이 부등식은
# **왜 그 값인가**를 묶는다(두 수를 함께 올리는 변경이 그 사슬을 굶기면 여기서 선다).
# 간격은 위 `oncal_pairs` 에서 **수집과 바로 뒤 러너(훑기)** 의 분 차로 산출한다 — 손으로 적지 않는다.
tss_ineq_bad=0 tss_min_gap=""
# <systemd 시간 문자열> → 분(정수). **받는 꼴을 먼저 가르고 나머지는 빈 값**이다(그 빈 값을 아래
# `_bad` 가 받아 판정 없이 지나가지 않는다). 앞 판은 접미사만 보고 바로 산술에 넣어 `1min30s`(「value too
# great for base」)·`0.5h`(「invalid arithmetic operator」)에서 **게이트가 요약 줄도 없이 죽었다**(실측) —
# 이 slice 가 앞 라운드에 고친 바로 그 침묵 꼴이 한 자리 남아 있었다. systemd 는 그 둘을 받으므로
# 도달 경로가 있다.
_tss_minutes() {
  # **열거가 아니라 구성**(M15/15V r2, cr M-2). r1 의 거름망은 이물 글자(`*[!0-9smh]*`)와 단위 없음
  # (`*[0-9]`)만 보아 **단위 글자가 둘인 꼴**을 통과시켰다: `2m30s` 는 `$(( 2m30 / 60 ))` 에서 대입이
  # 실패해 `set -e` 가 게이트를 **요약 줄도 없이** 죽이고, `1h30m` 은 `1h30` 을 내 뒤의 `[ -lt ]` 가
  # stderr 로 운다. systemd 는 둘 다 받으므로 도달 경로는 `1min30s` 와 같다(이 slice 에서 같은 침묵
  # 꼴이 세 번째다). 받는 꼴을 **하나의 정규식**으로 적고 나머지는 빈 값으로 보내 `_bad` 가 받는다 —
  # 새 꼴이 들어와도 통과하지 않는다. 이 파일은 bash 전용이라 `[[ =~ ]]` 를 쓴다.
  [[ $1 =~ ^[0-9]+[smh]$ ]] || { printf ''; return 0; }
  case "$1" in
    *m) printf '%s' "${1%m}" ;;
    *s) printf '%s' "$(( ${1%s} / 60 ))" ;;
    *h) printf '%s' "$(( ${1%h} * 60 ))" ;;
  esac
}
for _slot in 07 13; do
  _c="$(printf '%s\n' "$oncal_pairs" | sed -nE "s/^bidvector-collection\|\*-\*-\* ${_slot}:([0-9]{2}) .*/\1/p" | head -n1)"
  _s="$(printf '%s\n' "$oncal_pairs" | sed -nE "s/^bidvector-notification-sweep\|\*-\*-\* ${_slot}:([0-9]{2}) .*/\1/p" | head -n1)"
  if [ -z "$_c" ] || [ -z "$_s" ]; then
    tss_ineq_bad=$((tss_ineq_bad + 1))
    continue
  fi
  _gap=$(( 10#$_s - 10#$_c ))
  if [ -z "$tss_min_gap" ] || [ "$_gap" -lt "$tss_min_gap" ]; then tss_min_gap="$_gap"; fi
done
_tss_col="$(_tss_minutes "$(printf '%s\n' "$tss_pairs" | sed -nE 's/^bidvector-collection\|(.*)$/\1/p' | head -n1)")"
if [ -z "$tss_min_gap" ] || [ -z "$_tss_col" ] || [ "$tss_ineq_bad" -ne 0 ]; then
  _bad "수집의 TimeoutStartSec 부등식을 재지 못했다(간격 ${tss_min_gap:-?} · 상한 ${_tss_col:-?} · 못 읽은 창 ${tss_ineq_bad}) — 꼴이 바뀌었으면 추출을 고친다(판정 없이 지나가지 않는다)"
elif [ "$_tss_col" -lt "$tss_min_gap" ]; then
  _ok "수집의 TimeoutStartSec(${_tss_col}분) < 수집→훑기 최소 간격(${tss_min_gap}분) — 상한에 걸린 run 이 그 주기의 사슬을 굶기지 않는다"
else
  _bad "수집의 TimeoutStartSec(${_tss_col}분) ≥ 수집→훑기 최소 간격(${tss_min_gap}분) — 상한까지 매달린 run 이 훑기·평가·relay 의 그 주기를 모두 미루게 한다(상한을 줄이거나 OnCalendar 간격을 넓힌다)"
fi
unset _slot _c _s _gap _tss_col
unset _slot _u _col_min _other

if [ "$after_pairs" = "$AFTER_WANT_SET" ]; then
  _ok "runbook 의 unit 순서 간선 집합이 기대와 정확히 같다(수집 → 훑기 · 수집 → 평가 커밋 · 평가 커밋 → relay)"
else
  _bad "unit 의 After= 쌍이 기대와 다르다 — 받은 것: [$(printf '%s' "$after_pairs" | tr '\n' ';')] · 기대: [$(printf '%s' "$AFTER_WANT_SET" | tr '\n' ';')] — Persistent=true 의 catch-up 에서 순서가 뒤집힌다"
fi
# **제외 목록을 「값 집합 등식」으로 본다**(M15/15R, sec N-2). 앞 판은 「`5` 를 빼는 줄이 1 · `6` 을 빼는
# 줄이 0」만 봤고, 그래서 `SuccessExitStatus=4 5 7` 로 바꾼 변이가 **GREEN** 이었다 — 그 순간 `7`(모르는
# 코드)이 깨끗한 종료가 되어 S-3 이 닫은 창 셋이 **한 층 위에서** 그대로 다시 열린다(경보 없음 · unit 은
# 「Deactivated successfully」 · §13.4 의 계수는 `7` 을 세지 않는다). 이제 drop-in 마다 **unit 이름과 값
# 집합의 쌍**을 모아 고정 집합과 등식으로 묶는다 — 무엇이 더해지든 빠지든 붉다.
# **M15/15U — 수집 drop-in 은 `4` 만 뺀다.** 러너의 `ALREADY_RUNNING` 을 옮긴 `5` 는 **빼지 않는다**:
# 래퍼의 잠금 둘 아래에서 그 코드가 서는 경로는 **래퍼를 거치지 않은 실행**(손으로 띄운 `java -jar`·
# 컨테이너)이 같은 실행 상태 디렉터리를 쥔 경우뿐이고, 그것은 설계된 정상이 아니라 사람이 알아야 하는
# 어긋남이다(래퍼를 거친 손 실행은 프로젝트 잠금에서 `4` 로 먼저 미룬다 — 위 잠금 단언이 그 축이다).
# relay 의 `5`(`LEASE_BUSY`)와 다른 판단이고, 그 차이의 사유는 runbook §14 가 적는다.
SES_WANT_SET="$(printf '%s\n' \
  'bidvector-collection|4' \
  'bidvector-evaluation-commit|4' \
  'bidvector-notification-relay|4 5' \
  'bidvector-notification-sweep|4' | sort)"
ses_pairs="$(awk '
  /^# \/etc\/systemd\/system\/[a-z-]+\.service\.d\/10-defer\.conf$/ {
    unit = $2; sub(/^\/etc\/systemd\/system\//, "", unit); sub(/\.service\.d\/10-defer\.conf$/, "", unit)
    inb = 1; next
  }
  inb && /^```$/ { inb = 0; next }
  inb && /^[[:space:]]*SuccessExitStatus[[:space:]]*=/ {
    # **꼬리 `#` 를 지우지 않는다**(sec R-1). systemd 는 **줄 머리** `#`·`;` 만 주석으로 보므로
    # (`systemd.syntax`: "lines starting with # or ; are ignored") 꼬리 주석은 **값의 일부**다 —
    # `SuccessExitStatus=4 5 # 7 도 뺀다` 에서 systemd 는 `7` 을 받아 깨끗한 종료로 만든다
    # (`systemd-analyze verify` 실측: `#`·`도`·`뺀다` 만 버리고 `7` 에 대해서는 말이 없다).
    # 앞 판은 그 꼬리를 지워 값을 `4 5` 로 읽었고 그래서 **술어와 소비자가 다른 것을 보았다.**
    line = $0; sub(/^[^=]*=[[:space:]]*/, "", line)
    n = split(line, v, /[[:space:]]+/); out = ""
    for (i = 1; i <= n; i++) if (v[i] != "") out = (out == "" ? v[i] : out " " v[i])
    print unit "|" out
  }' "$RB" | sort || true)"
if [ "$ses_pairs" = "$SES_WANT_SET" ]; then
  _ok "네 drop-in 의 SuccessExitStatus 값 집합이 기대와 정확히 같다(relay 는 4 5 · 나머지 셋은 4 — 수집의 5·6·7 과 relay 의 6·7 은 빼지 않는다)"
else
  _bad "drop-in 의 SuccessExitStatus 쌍이 기대와 다르다 — 받은 것: [$(printf '%s' "$ses_pairs" | tr '\n' ';')] · 기대: [$(printf '%s' "$SES_WANT_SET" | tr '\n' ';')]"
fi

# ── M15/15R — 활성화 절차가 **가장 먼저 치는 두 줄** ────────────────────────────
# S-1·S-2 는 §13.4 ① 을 밟기 전의 §13.2 블록이고, 하나는 자격 둘을 담은 디렉터리의 권한을 되돌리고
# 다른 하나는 이미 채운 `relay.env` 를 말없이 0 바이트로 만든다. 둘 다 텍스트 술어다(unit·설치 명령이
# 있는 층이 문서뿐이다 — 이 파일 머리말의 경계) — 그래서 술어마다 **뽑은 수를 고정**해, 줄이 사라지거나
# 꼴이 바뀌어 아무것도 못 집는 경우가 초록이 되지 않게 한다.
#
# **S-1** — GNU `install -d` 는 **이미 있는 디렉터리에도** `-m` 을 적용하고 `-o` 가 없으면 소유자는
# 그대로라, §13.2 의 `-m 755` 한 줄이 §1 ③ 의 `0700` 을 조용히 되돌렸다. 그 디렉터리에는 `prod.env` 와
# `relay.env` 가 함께 있다 — 파일은 `0600` 이라 내용이 바로 새지는 않지만, 「같은 host 의 다른 계정은
# 들어가지도 못한다」 겹과 편집기 부산물(umask 022 의 `relay.env~` 는 `0644` 다)의 여유를 잃는다.
# **꼴을 열거하지 않고 「정확한 줄 집합」으로 본다**(v F1). 앞 판은 `install -d` 라는 **한 꼴**을 찾아
# `-m 700` 글자를 셌고, 그래서 같은 사고의 **정직한 다른 꼴** 셋이 통과했다(실측, coreutils 9.4 — 기존
# 0700 디렉터리에 `install -m 755 -d` → 755 · `install -dm755` → 755 · `chmod 755` → 755. 셋 다 GREEN 이었다).
# 꼴을 더 열거하면 다음 꼴에서 다시 열리므로, **그 디렉터리를 만지는 줄 전부를 모아 고정 집합과 등식**으로
# 묶는다 — 플래그 순서를 바꾸든 묶음 플래그를 쓰든 `chmod`·`chown`·`mkdir` 로 바꾸든 집합이 달라져 붉다.
# 모으는 범위: `install`·`mkdir`·`chmod`·`chown` 이 **디렉터리 자체를 인자 토큰으로** 받는 줄
# (`…/relay.env` 처럼 그 아래 파일을 받는 줄은 이 집합이 아니다 — 아래 S-2 가 본다).
# 새 줄이 정당하면 기대 집합을 **함께** 고친다(그것이 이 등식의 래칫이다).
# **수집도 열거를 쓰지 않는다**(M15/15R 라운드 2 — v G1·G2 · cr M-4). 라운드 1 은 **판정**을 집합 등식으로
# 바꿨지만 **수집**이 아직 꼴 열거였다: 동사 목록(`install|mkdir|chmod|chown`) · 경로 뒤 글자
# (`[[:space:]`]|$`) · 펜스 꼴(`^``` `). 그래서 끝 슬래시(`/etc/bidvector-v2/`) · 따옴표 경로
# (`"…"`) · **들여쓴 목록 안 코드 블록**(§1 ③ 의 블록 — 이 문서에 그런 펜스가 여덟 있다)이 모이지 않아
# GREEN 이었다(전부 실측). 분류를 더 정교하게 하는 처방은 그 분류가 다시 열거라 같은 자리를 다시 연다.
#
# 그래서 수집 기준을 **「그 경로를 입에 담은 코드 블록 줄 전부」**로 바꾼다 — 동사·끝 글자·변수 대입을
# 가리지 않는다. 기대 집합은 스크립트 안 리터럴이 아니라 **별도 파일**에 두고(선례:
# `config/quality/leak-pattern-baseline.txt`) 그 파일과 **정확히 같은지**를 본다.
DIR_EXPECT=config/quality/runbook-prod-path-lines.txt
RELAYENV_EXPECT=config/quality/runbook-relay-env-lines.txt
KONEPSENV_EXPECT=config/quality/runbook-koneps-env-lines.txt
for _e in "$DIR_EXPECT" "$RELAYENV_EXPECT" "$KONEPSENV_EXPECT"; do
  [ -r "$_e" ] || _die "기대 집합 파일을 읽지 못했다: ${_e}(옮겨졌거나 지워졌다) · 판정 불가" 2
done
unset _e
# 펜스를 `^[[:space:]]*` 로 센다 — 들여쓴 블록을 보지 않으면 그 안의 BAD 줄이 축어로 들어와도 조용하다.
_rb_code_lines() { awk '/^[[:space:]]*```/{inb = !inb; next} inb' "$RB"; }
_rb_norm() { sed 's/^[[:space:]]*//; s/[[:space:]]\+/ /g; s/[[:space:]]*$//'; }
# **콜레이션을 명령 단위로 못박는다**(cr M-5 · v H3). 라운드 1 의 술어는 기대와 실측을 **같은 프로세스의
# 같은 `sort`** 로 만들어 로캘과 무관했는데, 기대를 파일로 옮기면서 **한쪽 순서가 동결**되고 받는 쪽만
# 주변 로캘을 따랐다 — 둘이 갈리면 `comm` 은 경고를 stderr 로만 내고 **조용히 틀린 답**을 낸다
# (실측: `LC_ALL=en_US.utf8` 로 전건을 돌리면 같은 줄 스물셋이 「사라진 줄」과 「새로 생긴 줄」 양쪽에
# 동시에 서며 **거짓으로** 붉었다 — exit 1 · 268 중 2 어긋남). 파일 전체에 `export LC_ALL=C` 를 걸면 이
# 파일의 다른 `comm`·`sort` 조합 순서까지 바뀌므로 **비교하는 자리에만** 건다(래퍼의 `LC_ALL=C grep` 과
# 같은 모양). 양쪽을 다 핀하므로 **기대 파일의 줄 순서는 더 이상 저자 몫이 아니다** — 내용이 맞으면 초록.
_rb_sort() { LC_ALL=C sort; }
_rb_expect() { grep '^| ' "$1" | sed 's/^| //' | _rb_sort || true; }
_rb_set_check() { # <이름> <받은 집합> <기대 집합> <진단 꼬리>
  local name="$1" got="$2" want="$3" tail="$4" missing extra
  missing="$(LC_ALL=C comm -23 <(printf '%s\n' "$want") <(printf '%s\n' "$got") | tr '\n' '|' || true)"
  extra="$(LC_ALL=C comm -13 <(printf '%s\n' "$want") <(printf '%s\n' "$got") | tr '\n' '|' || true)"
  if [ -n "$got" ] && [ -z "$missing" ] && [ -z "$extra" ]; then
    _ok "${name} — 코드 블록의 그 줄 $(printf '%s' "$got" | grep -c '' || true) 개가 기대 집합과 **정확히** 같다"
  else
    _bad "${name} — 줄 집합이 기대와 다르다. 사라진 줄: [${missing}] · 새로 생긴 줄: [${extra}] — runbook 의 그 줄을 바꿨다면 ${tail} 도 함께 바꾼다(바뀐 줄은 리뷰가 본다)"
  fi
}
# **수집이 백틱 펜스만 본다 — 그 전제를 고정한다**(v H1). 틸드 펜스(`~~~`)도 렌더링되는 코드 블록이라
# 그 안의 BAD 줄은 수집 밖이다(실측: GREEN). 이 문서는 백틱만 쓰므로 **틸드가 0 임을 단언**해 두면
# 그 꼴이 들어오는 날 붉어진다(수집을 넓히는 쪽은 산문 오탐을 키운다 — 제한 7 의 비용). 네 칸 들여쓰기
# 코드 블록은 산문 들여쓰기와 구별할 수 없어 제한에 등재한다.
tilde_fences="$(grep -cE '^[[:space:]]*~~~' "$RB" || true)"
if [ "$tilde_fences" = 0 ]; then
  _ok "runbook 에 틸드 펜스가 없다(수집이 백틱 펜스만 보는 전제 — 생기면 그 안이 수집 밖이라 여기서 선다)"
else
  _bad "runbook 에 틸드 펜스가 ${tilde_fences} 줄 생겼다 — 그 블록의 줄은 경로 수집 밖이다(펜스 인식을 넓히거나 백틱으로 바꾼다)"
fi
# **`/var/tmp/bidvector` 를 수집 필터에 더한다**(M15/15V r1, cr L-1). 앞 판의 필터는 §14.5 ③④ 가 만든
# 새 sink 다섯 줄 가운데 **한 줄만** 집었고(그 줄이 `/etc/bidvector-v2` 를 담아서 들어왔다) 나머지 넷은
# 래칫 밖이었다 — 둘째 `/var/tmp` sink 를 더하는 줄은 집합 등식도 전용 단언도 보지 못했다.
# 이제 집합 등식이 그 축까지 들고, 전용 단언(`col_log_*`)은 **순서** 전담으로 남는다.
dir_lines="$(_rb_code_lines | grep -E '/etc/bidvector-v2|/var/lib/bidvector-v2|/var/tmp/bidvector' | _rb_norm | _rb_sort || true)"
_rb_set_check "자격·상태 디렉터리 경로" "$dir_lines" "$(_rb_expect "$DIR_EXPECT")" "$DIR_EXPECT"
# **S-2** — `install … /dev/null` 은 **이미 채운 파일을 0 바이트로** 만든다(출력 없음 · rc 0). 도달
# 경로가 가깝다: §13.3 이 「root 전용으로 조이면 매 run 이 `exit 3`」을 가리키며 §13.2 의 꼴을 지목하므로,
# 권한을 고치러 그 블록을 다시 친 운영자가 방금 넣은 자격과 chat id 를 함께 날린다. 그 사고는 **경보까지
# 끈다** — 빈 파일 → relay `exit 3` → `OnFailure` → 경보가 **같은 빈 파일**을 읽고 `exit 2`.
# 그래서 가드(`test -e`)와 `install` 은 **같은 줄**에 둔다(줄을 쪼개면 이 술어가 가드를 못 본다).
# **여기도 「정확한 줄 집합」이다**(v F2). 앞 판은 `test -e` 라는 **글자의 존재**를 셌고, 그래서 가드를
# `&&` 로 **뒤집은** 줄이 통과했다 — 그 꼴은 「있을 때만 비운다」라서 채운 파일을 실제로 0 바이트로
# 만든다(실측). `;` 로 잇기 · 다른 파일(`prod.env`)을 보는 가드 · 권한 줄 자리에 `cp /dev/null` 추가도
# 전부 GREEN 이었다. 연산자·경로·절단 수단을 하나씩 열거하는 대신 **relay.env 에 쓰는 줄 전부**를 모아
# 고정 집합과 등식으로 묶는다(읽기·편집기 줄은 쓰기 어휘가 없어 모이지 않는다 — `sudo -e` 가 그렇다).
# 같은 처방을 자격 **파일** 축에도 쓴다. 라운드 1 의 동사 목록(`install|cp|tee|truncate|chown|chmod|dd`)은
# `sed -i`·`rm -f`·`mv`·`ex` 로 같은 파일을 비우는 줄을 통과시켰다(실측) — **`sed -i` 는 이 문서의 집안
# 관용구**라(§9.5 ① 이 그 꼴이다) 다음 저자가 고를 확률이 낮지 않았다. 이제 동사를 보지 않는다.
relayenv_lines="$(_rb_code_lines | grep 'relay\.env' | _rb_norm | _rb_sort || true)"
_rb_set_check "자격 파일(relay.env) 언급" "$relayenv_lines" "$(_rb_expect "$RELAYENV_EXPECT")" "$RELAYENV_EXPECT"
# **M15/15U — 자격 파일이 둘이 됐다.** `koneps.env` 는 조달청 서비스 키를 담고, §14 가 그 파일을 입에
# 담는다. S-2 의 사고 부류(이미 채운 자격 파일을 `install … /dev/null` 이 **말없이 0 바이트로** 만든다)는
# 파일 이름과 무관하므로 같은 처방을 이 축에도 둔다 — 동사를 보지 않고 **그 파일을 입에 담은 코드 블록 줄
# 전부**를 고정 집합과 묶는다. 이 파일이 비면 수집 run 이 기동 실패(1)로 끝난다.
# ── M15/15V(D-15V-5) — §14.5 ③ 의 로그 파일은 **0600 으로 먼저 만든 뒤** `tee` 가 쓴다 ─────
# 그 파일은 래퍼의 stderr 와 러너 출력 전부를 담고 `/var/tmp` 는 재부팅에도 남는다. 평소 umask(0022)가
# 만드는 `0644` 로 두지 않는 유일한 길은 **`tee` 보다 앞선 `install -m 600`** 이다(`tee` 는 있는 파일의
# mode 를 바꾸지 않는다 — 실측: 먼저 만들면 600 유지 · 안 만들면 644). 그래서 **순서**가 거동이다.
# 자리 이름은 하나로 두고(등재 줄 목록의 수집 필터 밖이라 그 래칫이 따라가지 않는다) 여기서 직접 센다.
# ── M15/15V r1(vr M-1 · cr L-2) — §14.5 ④ 의 전제 가드를 **돌려서** 잰다 ───────
# 앞 판은 줄 **존재**를 셋으로만 셌고(`if` 1 · `docker exec` 1 · `else` 1), 그래서 **막으려던 바로 그
# 회귀**가 지나갔다(실측: `docker exec` 를 `fi` 뒤로 옮겨도 · `PGUSER_`·`PGDB_` 조건을 지워도 GREEN).
# 정본 Phase 2.5 (1) 의 「문자열 술어 → 실행 층」으로 옮긴다: runbook 에서 그 토막을 **축어로 뽑아**
# 가짜 `docker` 앞에서 돌리고 **`docker exec` 가 불린 횟수**를 센다.
#
# ── M15/15V r2(vr H-1 · cr H-2) — **실행하는 것의 범위를 구성으로 묶는다** ─────
# r1 의 추출은 `_rb_code_lines`(펜스 경계를 버린 전 코드 줄)에서 첫 `^fi$` 까지였고, 그 토막을 이 host 의
# 평소 PATH·cwd 에서 돌렸다. 종단자가 한 글자 달라지면(`fi  # 주석` · `fi;` · 들여쓴 `fi`) 그 꼴이
# 스트림에서 **사라져** 추출이 문서 끝까지 간다 — 정적 실측: 세 꼴 모두 **62 줄**이고 그 안에
# `sudo -e /etc/bidvector-v2/prod.env` · `./tools/prod-health.sh --env-file …` · `shred -u …` ·
# `sudo systemctl enable --now …` 가 든다. r1 의 비공허 단언은 그 꼴을 **받는다**(62 ≥ 10 · psql 줄은
# 문서에 하나뿐이라 1). **하한은 상한이 아니다.** 품질 게이트가 운영 타이머를 켜고 운영 자격 파일을
# 편집기로 여는 길이므로 네 층으로 묶는다.
#  ⓐ 추출을 **시작 표지가 든 펜스 안**으로 묶는다(펜스를 이 awk 가 직접 센다). 종단자를 보지 못한 채
#     그 펜스가 닫히면 **아무것도 내지 않는다** → 0 줄 → `_bad`. 줄 수 **상한**과 「마지막 줄이 fi」도
#     함께 단언한다(하한만으로는 넓어진 꼴이 지나간다 — 그것이 이 지적이다).
#  ⓑ 실행은 추출이 검증된 **`_ok` 가지 안에서만** 한다(r1 은 판정과 무관하게 무조건 돌았다).
#  ⓒ 실행은 `env -i` + **열거한 이름만 든 전용 PATH**(`$T/qbin`) · stdin `/dev/null` · cwd `$T/qrun`
#     (저장소 밖이라 `./tools/*` 가 해석되지 않는다)에서 한다.
#  ⓓ 위험 이름은 **가짜를 두고 호출 0 회를 센다**. 「PATH 에 없음」 대신 이 쪽을 고른 이유: 없음은 그
#     순간의 PATH 만 말하고 호출을 **막지는 못하는데**(절대 경로로 부르면 그만이다) 가짜는 넓어진 토막이
#     그 이름을 실제로 부를 때 **아무것도 하지 않으면서** 같은 계수로 그 사실을 보고한다. 그 계수가
#     공허하지 않다는 것은 **양성 대조**로 잰다(가짜를 직접 부르는 probe → 다섯 회 기록 · 그 가짜
#     shred·rm 이 지나간 파일이 그대로 남음). 「없음」은 열거 **밖** 이름으로 따로 잰다(넓어진 토막의
#     `sed`·`paste`·`bc` 가 서는 자리다).
#
# 가짜 `docker` 의 `config` 는 `$SHIM_CONFIG_FILE` 을 그대로 내므로 운영 env 파일을 열지 않는다
# (블록의 `--env-file` 경로는 가짜가 읽지 않는다 — 운영 자원 접촉 0).
#
# 일곱 cfg fixture — ④ 의 **여섯 조건을 하나씩** 받는다(M15/15V r2, cr M-1 · vr L-a). r1 은 둘
# (이름 빈 값 · 사용자 null)뿐이어서 나머지 넷을 지운 변이가 GREEN 이었다. 꼴은 `jq` 로 만든다
# (앞 판의 `sed` 치환은 값 모양이 바뀌는 날 조용히 아무것도 바꾸지 않는다).
jq -e '.services.postgres.environment | has("POSTGRES_USER") and has("POSTGRES_DB")' "$T/cfg.json" \
  >/dev/null || _die "compose config fixture 에 postgres 자격 키가 없다 — ④ fixture 를 만들 수 없다(판정 불가)" 2
_q_fixture() { # <꼬리> <jq 식>
  jq -c "$2" "$T/cfg.json" > "$T/cfg-q-$1.json" \
    || _die "④ cfg fixture 를 만들지 못했다: $1 — 판정 불가" 2
}
_q_fixture ok        '.'
_q_fixture noname    '.name = ""'
_q_fixture namenull  '.name = "null"'
_q_fixture useremp   '.services.postgres.environment.POSTGRES_USER = ""'
_q_fixture usernull  'del(.services.postgres.environment.POSTGRES_USER)'
_q_fixture dbemp     '.services.postgres.environment.POSTGRES_DB = ""'
_q_fixture dbnull    'del(.services.postgres.environment.POSTGRES_DB)'
# ⓒ 격리 실행 환경 — 열거한 이름만 든 PATH, 빈 환경, 저장소 밖 cwd.
Q_REAL_TOOLS=(bash cat grep jq)                       # 토막과 가짜 docker 가 쓰는 도구
Q_DANGER_TOOLS=(sudo systemctl journalctl shred rm)   # 넓어진 토막이 부르는 이름 — 가짜로 둔다
Q_UNLISTED_TOOLS=(sed awk paste bc)                   # 열거 밖 — 해석되지 않아야 한다
mkdir -p "$T/qbin" "$T/qrun"
for _t in "${Q_REAL_TOOLS[@]}"; do
  _p="$(command -v "$_t" || true)"
  [ -n "$_p" ] || _die "격리 PATH 에 넣을 ${_t} 를 찾지 못했다 — 판정 불가" 2
  ln -sf "$_p" "$T/qbin/$_t"
done
cp "$T/bin/docker" "$T/qbin/docker"
chmod +x "$T/qbin/docker"
for _t in "${Q_DANGER_TOOLS[@]}"; do
  cat > "$T/qbin/$_t" <<'QFAKE'
#!/usr/bin/env bash
# 위험 이름의 가짜 — 부르면 argv 만 적고 **아무것도 하지 않는다**(tools/prod-guards-check.sh 전용).
printf '%s %s\n' "${0##*/}" "$*" >> "${SHIM_LOG:-/dev/null}"
exit 0
QFAKE
  chmod +x "$T/qbin/$_t"
done
unset _t _p
QBASH="$(command -v bash)"
Q_DANGER_RE="$(printf '%s|' "${Q_DANGER_TOOLS[@]}")"; Q_DANGER_RE="${Q_DANGER_RE%|}"
q_danger_n=0
q_danger_hit=""
_q_run() { # <돌릴 스크립트> <cfg fixture> → q_exec_n 세우고 위험 호출을 q_danger_n 에 누적
  : > "$SHIM_LOG"
  ( cd "$T/qrun" && env -i "PATH=$T/qbin" "SHIM_LOG=$SHIM_LOG" "SHIM_CONFIG_FILE=$2" \
      "$QBASH" "$1" ) > "$T/qout" 2> "$T/qerr" < /dev/null || true
  q_exec_n="$(grep -cE '^docker exec ' "$SHIM_LOG" || true)"
  q_danger_n=$(( q_danger_n + $(grep -cE "^(${Q_DANGER_RE}) " "$SHIM_LOG" || true) ))
  q_danger_hit="${q_danger_hit}$(grep -E "^(${Q_DANGER_RE}) " "$SHIM_LOG" | tr '\n' ';' || true)"
}
q_bin_got="$(ls -1 "$T/qbin" | LC_ALL=C sort | tr '\n' ' ' || true)"
q_bin_want="$(printf '%s\n' docker "${Q_REAL_TOOLS[@]}" "${Q_DANGER_TOOLS[@]}" | LC_ALL=C sort | tr '\n' ' ')"
if [ "$q_bin_got" = "$q_bin_want" ]; then
  _ok "④ 격리 PATH 가 열거한 이름 그대로다(${q_bin_want}) — 그 디렉터리 하나가 PATH 전부이므로 밖의 이름은 해석되지 않는다"
else
  _bad "④ 격리 PATH 의 이름 집합이 열거와 다르다: 받은 [${q_bin_got}] · 기대 [${q_bin_want}] — 이름을 더하거나 뺐으면 열거도 함께 바꾼다(그 집합이 토막이 쓸 수 있는 도구 전부다)"
fi
q_unlisted="$(env -i "PATH=$T/qbin" "$QBASH" -c 'for _u in "$@"; do command -v "$_u" || true; done' _ \
  "${Q_UNLISTED_TOOLS[@]}" < /dev/null | tr '\n' ' ' || true)"
if [ -z "${q_unlisted// /}" ]; then
  _ok "열거 밖 이름 ${#Q_UNLISTED_TOOLS[@]} 개(${Q_UNLISTED_TOOLS[*]})가 그 PATH 에서 해석되지 않는다 — 넓어진 토막의 sed·paste·bc 줄이 서는 자리다"
else
  _bad "열거 밖 이름이 그 PATH 에서 해석된다: ${q_unlisted}— PATH 가 새지 않는지 본다(격리가 열리면 아래 「호출 0」이 아무것도 뜻하지 않는다)"
fi
# **양성 대조** — 가짜가 살아 있고 계수가 움직인다는 것을 먼저 잰다. 이것이 없으면 아래 「호출 0」은
# 「아무도 부르지 않았다」와 「계수가 죽었다」를 구별하지 못한다(이 저장소가 거듭 밟은 꼴이다).
: > "$T/qrun/probe-target"
cat > "$T/qprobe.sh" <<'QPROBE'
sudo -n true
systemctl is-active bidvector-collection.timer
journalctl -u bidvector-collection -n 1
shred -u probe-target
rm -f probe-target
QPROBE
_q_run "$T/qprobe.sh" "$T/cfg-q-ok.json"
if [ "$q_danger_n" = "${#Q_DANGER_TOOLS[@]}" ] && [ -e "$T/qrun/probe-target" ]; then
  _ok "위험 이름 가짜가 산다 — 양성 대조에서 ${q_danger_n} 회 기록되고, 그 가짜 shred·rm 이 지나간 파일은 **그대로 있다**(계수가 살아 있고 가짜는 아무것도 하지 않는다)"
else
  _bad "위험 이름 가짜의 양성 대조가 어긋났다 — 기록 ${q_danger_n} 회(${#Q_DANGER_TOOLS[@]} 이어야) · probe 대상 파일 잔존 $([ -e "$T/qrun/probe-target" ] && echo 있음 || echo 없음). 계수가 죽었으면 아래 「호출 0」은 공허하다"
fi
q_danger_n=0
q_danger_hit=""
# ⓐ 펜스 동일성 추출 — 시작 표지가 **든 그 펜스** 안에서, 종단자까지. 종단자 없이 펜스가 닫히면 0 줄.
q_block="$T/q-block.sh"
awk '
  /^[[:space:]]*```/ {
    if (fence && collecting && !closed) n = 0
    if (fence && collecting) exit
    fence = !fence
    next
  }
  !fence { next }
  !collecting && /^CFG="\$\(docker compose/ { collecting = 1 }
  collecting && !closed { buf[++n] = $0 }
  collecting && !closed && /^fi$/ { closed = 1 }
  END { if (closed) for (i = 1; i <= n; i++) print buf[i] }
' "$RB" > "$q_block"
q_block_n="$(grep -c '' "$q_block" || true)"
q_block_exec="$(grep -cE '^docker exec -i "\$\{P\}-postgres-1" psql ' "$q_block" || true)"
q_block_last="$(tail -n1 "$q_block" 2>/dev/null || true)"
if [ "$q_block_n" -ge 10 ] && [ "$q_block_n" -le 40 ] && [ "$q_block_exec" = 1 ] \
   && [ "$q_block_last" = fi ]; then
  _ok "runbook 에서 ④ 토막을 뽑았다(${q_block_n} 줄 · 상한 40 · psql 호출 한 줄 · 마지막 줄이 닫는 fi) — 아래 측정이 공허하지 않고 그 펜스를 넘지도 않는다"
  # ⓑ 실행은 여기서만 — 추출이 위 넷을 통과했을 때다.
  _q_run "$q_block" "$T/cfg-q-ok.json";       q_ok_n="$q_exec_n"
  _q_run "$q_block" "$T/cfg-q-noname.json";   q_noname_n="$q_exec_n"
  _q_run "$q_block" "$T/cfg-q-namenull.json"; q_namenull_n="$q_exec_n"
  _q_run "$q_block" "$T/cfg-q-useremp.json";  q_useremp_n="$q_exec_n"
  _q_run "$q_block" "$T/cfg-q-usernull.json"; q_usernull_n="$q_exec_n"
  _q_run "$q_block" "$T/cfg-q-dbemp.json";    q_dbemp_n="$q_exec_n"
  _q_run "$q_block" "$T/cfg-q-dbnull.json";   q_dbnull_n="$q_exec_n"
  q_blocked="${q_noname_n}${q_namenull_n}${q_useremp_n}${q_usernull_n}${q_dbemp_n}${q_dbnull_n}"
  if [ "$q_ok_n" = 1 ] && [ "$q_blocked" = 000000 ]; then
    _ok "④ 토막을 일곱 꼴로 돌려 쟀다 — 정상 psql 1 회 · 여섯 결손 꼴(이름 빈 값·이름 null·사용자 빈 값·사용자 null·DB 빈 값·DB null) 모두 0 회"
  else
    _bad "④ 토막이 결손 값에서 질의를 막지 못한다 — 정상 ${q_ok_n}(1 이어야) · 이름 빈 값 ${q_noname_n} · 이름 null ${q_namenull_n} · 사용자 빈 값 ${q_useremp_n} · 사용자 null ${q_usernull_n} · DB 빈 값 ${q_dbemp_n} · DB null ${q_dbnull_n}(여섯 다 0 이어야). 질의가 'if …; then' **안**에 있고 세 값 모두 -n 과 != null 로 보는지 확인한다"
  fi
  # ⓓ 그 일곱 run 이 위험 이름을 한 번도 부르지 않았는가 — 추출이 ④ 펜스를 넘지 않았다는 **실행 층** 진술.
  if [ "$q_danger_n" = 0 ]; then
    _ok "④ 토막을 일곱 꼴로 돌리는 동안 위험 이름 호출 0 회(${Q_DANGER_TOOLS[*]}) — 추출이 ④ 펜스를 넘지 않았다"
  else
    _bad "④ 토막 실행이 위험 이름을 ${q_danger_n} 회 불렀다: ${q_danger_hit}— 추출이 §14.5 ④ 의 펜스를 넘었다. 닫는 fi 의 꼴을 확인한다(그 줄이 'fi' 가 아니면 추출이 문서 끝까지 간다). 가짜가 받아 **아무 일도 일어나지 않았다**"
  fi
else
  _bad "④ 토막을 뽑지 못했다(${q_block_n} 줄 · 상한 40 · psql 호출 ${q_block_exec} · 마지막 줄 [${q_block_last}]) — 종단자를 못 본 추출은 **아무것도 내지 않는다**. §14.5 ④ 의 닫는 줄이 'fi' 하나인지 확인한다(판정 없이 지나가지 않고, 실행도 하지 않는다)"
fi
COL_LOG=/var/tmp/bidvector-collection-step3.log
col_log_lines="$(_rb_code_lines | grep -F "$COL_LOG" | _rb_norm || true)"
# 꼬리의 `&& \` 를 받는다(vr L-5 가 `install` 실패를 보게 하려고 그 줄을 `&&` 로 이었다) — 앵커는
# 명령과 **정확한 경로**에 그대로 둔다.
# **주석 줄은 어느 계수도 세지 않는다**(M15/15V r2, cr L-5 — r1 은 제거 계수만 고쳐 비대칭이 남았다:
# `tee` 는 앵커가 없어 주석 달린 `# ./tools/… | tee …` 도 셌다). 한 번 걷어 네 자리가 같은 모집단을
# 본다. `_rb_norm` 이 줄 머리 공백을 걷으므로 첫 글자가 `#` 인지만 보면 된다.
col_log_code="$(printf '%s\n' "$col_log_lines" | grep -v '^#' || true)"
col_log_install_n="$(printf '%s\n' "$col_log_code" | grep -cE "^install -m 600 /dev/null ${COL_LOG}( &&.*)?\$" || true)"
col_log_tee_n="$(printf '%s\n' "$col_log_code" | grep -cF "| tee ${COL_LOG}" || true)"
# **주석 줄은 세지 않는다**(M15/15V r1, vr L-4) — 앞 판은 그 줄 앞에 `# ` 를 붙여도 초록이었다(실측).
# `_rb_norm` 이 줄 머리 공백을 걷으므로 첫 글자가 `#` 인지만 보면 된다.
col_log_rm_n="$(printf '%s\n' "$col_log_code" | grep -cE "(shred -u|rm -f) ${COL_LOG}" || true)"
# 순서: `install` 줄이 `tee` 줄보다 **앞**이어야 한다. 수집 순서가 문서 순서다.
# `|| true` — 무매치(`grep` exit 1)가 `set -e` 아래에서 **게이트를 죽이지 않게** 한다. 죽은 게이트는
# 판정이 아니라 침묵이다(이 라운드의 변이가 그 꼴을 드러냈다 — exit 1 · BAD 0 · 요약 줄 없음).
col_log_install_at="$(printf '%s\n' "$col_log_code" | grep -nE "^install -m 600 /dev/null ${COL_LOG}( &&.*)?\$" | head -n1 | cut -d: -f1 || true)"
col_log_tee_at="$(printf '%s\n' "$col_log_code" | grep -nF "| tee ${COL_LOG}" | head -n1 | cut -d: -f1 || true)"
if [ "$col_log_install_n" = 1 ] && [ "$col_log_tee_n" = 1 ] && [ "$col_log_rm_n" -ge 1 ] \
   && [ -n "$col_log_install_at" ] && [ -n "$col_log_tee_at" ] && [ "$col_log_install_at" -lt "$col_log_tee_at" ]; then
  _ok "runbook 의 ③ 로그 파일은 0600 으로 먼저 만들고(install 1 줄 · tee 보다 앞) 지우는 줄이 있다(${col_log_rm_n})"
else
  _bad "③ 로그 파일의 모양·순서가 어긋났다 — install ${col_log_install_n} 줄(자리 ${col_log_install_at:-?}) · tee ${col_log_tee_n} 줄(자리 ${col_log_tee_at:-?}) · 제거 ${col_log_rm_n} 줄. 먼저 만들지 않으면 평소 umask 에서 0644 로 생기고 tee 는 그 mode 를 바꾸지 않는다"
fi
konepsenv_lines="$(_rb_code_lines | grep 'koneps\.env' | _rb_norm | _rb_sort || true)"
_rb_set_check "자격 파일(koneps.env) 언급" "$konepsenv_lines" "$(_rb_expect "$KONEPSENV_EXPECT")" "$KONEPSENV_EXPECT"

# ── M15/15R(S-4 · S-9) — 경보 unit 의 자격 통로와 core dump ──────────────────────
# §13.3 은 relay unit 에서 `EnvironmentFile=` 을 **일부러 걷으면서** 사유로 `/proc/<pid>/environ` 축을
# 든다. 그런데 **같은 자격을 읽는 경보 unit** 이 그 통로를 그대로 썼고, 그것을 받아들인 사유는
# 「오늘은 relay 와 같은 배선을 쓰는 쪽을 골랐다」였다 — 15Q 에서 그 전제가 사라졌다. 비용은 0 이다:
# 파일이 `deploy:deploy 0600` 이고 경보가 `User=deploy` 라 스크립트가 직접 읽으며, 파일 갈래를 환경
# 갈래보다 **먼저** 본다. 파일 부재도 여전히 스크립트의 `exit 2` 한 창구로 모인다.
#
# **줄 머리 지시만 센다** — 산문·주석의 `EnvironmentFile=` 언급은 걸리지 않아야 한다(§13.3 은 「쓰지
# 않는다」는 판단을 그 글자로 적는다). 아래 둘째 계수가 그 **오탐 대조**다: 파일에 그 글자가 여럿
# 있는데도 첫째 계수가 0 이어야 앵커가 실제로 가르고 있다는 뜻이다.
# **systemd 가 받는 꼴을 전부 앵커에 넣는다**(v F5). 앞 판은 `^EnvironmentFile=` 라 **등호 앞뒤 공백**과
# **줄 머리 공백**을 놓쳤고(systemd 는 둘 다 받는다) 그 꼴로 되살린 변이가 GREEN 이었다. 주석(`#`·`;`)
# 으로 시작하는 줄은 지시가 아니므로 뺀다 — 그것이 산문 오탐 대조와 같은 자리다.
# **같은 통로를 여는 둘째 지시도 본다**(cr L-4): `Environment=` 로 그 두 키를 직접 실으면 막은 축
# (`/proc/<pid>/environ`)이 그대로 다시 열린다.
ENVF_DIRECTIVE='^[[:space:]]*EnvironmentFile[[:space:]]*='
envf_relay="$(grep -cE "${ENVF_DIRECTIVE}[^=]*relay\.env" "$RB" || true)"
envf_mentions="$(grep -cE 'EnvironmentFile' "$RB" || true)"
envkey_inline="$(grep -cE '^[[:space:]]*Environment[[:space:]]*=.*BIDVECTOR_NOTIFICATION_TELEGRAM_(BOT_CREDENTIAL|CHAT_ID)' "$RB" || true)"
if [ "$envf_relay" = 0 ] && [ "$envkey_inline" = 0 ] && [ "$envf_mentions" -ge 1 ]; then
  _ok "runbook 의 어떤 EnvironmentFile·Environment 지시도 자격을 환경에 올리지 않는다(산문 언급 ${envf_mentions} 건은 세지 않는다)"
else
  _bad "자격을 환경에 올리는 지시가 있다 — EnvironmentFile→relay.env ${envf_relay} 줄 · Environment= 로 그 키 ${envkey_inline} 줄(둘 다 0 이어야 한다 · 문서의 그 글자 전체는 ${envf_mentions} 건)"
fi
# **core dump** — 봇 자격은 경보 스크립트의 셸 변수와 `curl` 의 메모리에 요청이 끝날 때까지 살아 있고,
# 덤프가 남으면 그 값이 디스크로 간다. 15P 가 세운 「이 파일에는 파일로 쓰는 자리가 없다」 정적 술어는
# 스크립트의 구문을 보지 **커널이 쓰는 파일**을 보지 않는다. 등재된 hardening 묶음과 달리 이 지시는
# 경보 unit 하나에만 넣어도 세 unit 의 모양이 어긋나지 않는다.
# **섹션까지 본다**(v F4). 앞 판은 블록 전체에서 `^LimitCORE=0$` 을 셌고, 그래서 그 줄을 `[Unit]` 밑으로
# **옮긴** 변이가 GREEN 이었다(systemd 는 `[Unit]` 의 그 키를 무시한다). 뒤에 `LimitCORE=infinity` 를 더해
# **마지막 값**으로 뒤집는 변이도 GREEN 이었다. 이제 `[Service]` 구간으로 자른 뒤 **그 구간의 LimitCORE
# 지시 전부**를 모아 「한 줄이고 값이 0」을 본다 — 옮기면 구간 밖이라 0 이 되고, 덮어쓰면 2 가 된다.
alert_block="$(awk '/^# \/etc\/systemd\/system\/bidvector-alert@\.service$/ {inb = 1; next} inb && /^```$/ {exit} inb {print}' "$RB" || true)"
alert_service="$(printf '%s\n' "$alert_block" | awk '/^\[Service\]$/ {ins = 1; next} ins && /^\[/ {exit} ins {print}' || true)"
# **값은 줄 끝까지가 값이다**(sec R-1 과 같은 사유) — 첫 토막만 떼면 `LimitCORE=0 # 메모` 를 `0` 으로
# 읽는데, systemd 는 그 값을 통째로 파싱해 **실패하고 지시를 무시한다**(그러면 rlimit 가 안 선다).
limitcore_vals="$(printf '%s\n' "$alert_service" | sed -nE 's/^[[:space:]]*LimitCORE[[:space:]]*=[[:space:]]*(.*)$/\1/p' | sed 's/[[:space:]]*$//' | tr '\n' ';' || true)"
if [ "$limitcore_vals" = "0;" ]; then
  _ok "경보 unit 의 [Service] 구간에 LimitCORE 지시가 하나이고 값이 정확히 0 이다(옮기면 0 줄 · 덮어쓰면 둘 · 꼬리 주석이 붙으면 값이 달라져 선다)"
else
  _bad "경보 unit [Service] 구간의 LimitCORE 값 열이 {0} 가 아니다: {${limitcore_vals}} — [Unit] 로 옮겼거나 뒤에서 덮어썼거나 꼬리 주석이 붙었거나 줄이 없다"
fi
# **§9.5 의 「깔린 unit 맞추기」 토막이 기대는 앵커 둘을 템플릿과 묶는다**(cr L-10). 그 토막은
# `^User=deploy$` 뒤에 줄을 넣고 `grep -qx 'LimitCORE=0'` 로 멱등을 판정하는데, 템플릿이 그 두 꼴을
# 유지하는지 보는 단언이 없었다 — 템플릿에서 `User=deploy` 를 옮기거나 `LimitCORE = 0` 으로 적으면
# 게이트는 초록인데 운영자 토막이 아무것도 넣지 않거나 둘째 줄을 넣는다(④ 의 `show` 가 드러내긴 한다).
alert_user_n="$(printf '%s\n' "$alert_service" | grep -cE '^User=deploy$' || true)"
alert_lc_verbatim="$(printf '%s\n' "$alert_service" | grep -cE '^LimitCORE=0$' || true)"
if [ "$alert_user_n" = 1 ] && [ "$alert_lc_verbatim" = 1 ]; then
  _ok "§9.5 토막이 기대는 두 앵커가 템플릿에 축어로 있다(User=deploy 1 줄 · LimitCORE=0 1 줄)"
else
  _bad "§9.5 토막의 앵커가 템플릿과 어긋났다 — '^User=deploy\$' ${alert_user_n} 줄 · 축어 'LimitCORE=0' ${alert_lc_verbatim} 줄(둘 다 1 이어야 한다. 템플릿을 바꿨으면 그 토막의 sed 앵커도 함께 바꾼다)"
fi

# ── M15/15R(S-10) — `ExecStopPost` 가 지우는 이름의 **접두**가 운영 compose 의 `name:` 인가 ─────
# 위 `esp_names` 는 꼬리(`sweep`·`evaluation`·`relay`)만 세고 접두는 보지 않았다. `--project` 를
# 기본값과 다르게 쓰는 host 에서는 KILL 로 끝난 run 의 뒤처리가 **아무것도 지우지 않고**, 운영 DB
# 자격을 받은 일회 컨테이너가 남는다(다음 주기의 잔존 검사가 `exit 3` 으로 크게 말하므로 조용하지는
# 않다). 접두의 정본은 `docker/compose.prod.yaml` 의 `name:` 이고, 그 값은 `tools/prod-env.sh` 가
# 이미 거기서 읽는다 — 여기서 베껴 적지 않는다(§9.2 의 경고가 그 교환을 문면으로 적는다).
esp_prefixes="$(grep -E '^ExecStopPost=' "$RB" | grep -oE 'docker rm -f [^ ]*-app-run-[a-z]+' \
  | sed 's/^docker rm -f //; s/-app-run-[a-z]*$//' | sort -u | tr '\n' ' ' || true)"
if [ "$esp_prefixes" = "${BIDVECTOR_PROD_PROJECT} " ]; then
  _ok "runbook 의 ExecStopPost 가 지우는 이름의 접두가 운영 compose 의 name: 하나다(${BIDVECTOR_PROD_PROJECT})"
else
  _bad "ExecStopPost 가 지우는 이름의 접두 집합이 {${BIDVECTOR_PROD_PROJECT}} 가 아니다: {${esp_prefixes}} — 프로젝트 이름이 다르면 그 줄도 함께 바꾼다(§9.2)"
fi
# 이 블록들이 쓴 임시값을 걷는다(cr L-8 — 같은 파일의 다른 임시값과 같은 관례).
unset DIR_EXPECT RELAYENV_EXPECT KONEPSENV_EXPECT dir_lines relayenv_lines konepsenv_lines \
  COL_LOG col_log_lines col_log_code col_log_install_n col_log_tee_n col_log_rm_n col_log_install_at col_log_tee_at \
  q_block q_block_n q_block_exec q_block_last q_ok_n q_noname_n q_namenull_n q_useremp_n \
  q_usernull_n q_dbemp_n q_dbnull_n q_blocked q_exec_n q_danger_n q_danger_hit q_bin_got \
  q_bin_want q_unlisted Q_REAL_TOOLS Q_DANGER_TOOLS Q_UNLISTED_TOOLS Q_DANGER_RE QBASH \
  ENVF_DIRECTIVE envf_relay envf_mentions \
  envkey_inline alert_block alert_service limitcore_vals alert_user_n alert_lc_verbatim \
  esp_prefixes SES_WANT_SET ses_pairs AFTER_WANT_SET after_pairs \
  ONCAL_WANT_SET oncal_pairs oncal_order_bad TSS_WANT_SET tss_pairs tss_ineq_bad tss_min_gap

echo "prod-guards-check: 통과 ${PASS} · 어긋남 ${FAIL}"
[ "$FAIL" -eq 0 ] || exit 1
