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

for t in jq git flock mktemp setsid ps sed awk; do
  command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2
done

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
_cleanup() {
  rm -rf "$T"
  rm -f "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock" "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}-probe.lock"
}
trap _cleanup EXIT

_require_lock_dir "$BIDVECTOR_PROD_LOCK_DIR"
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
if [ "${1:-}" = compose ]; then
  shift
  sub=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -f|-p|--env-file|--profile|--project-name) shift 2 ;;
      -*) shift ;;
      *) sub="$1"; shift; break ;;
    esac
  done
  case "$sub" in
    version) printf 'v%s\n' "${SHIM_COMPOSE_VER:-2.29.0}" ;;
    config)
      [ -n "${SHIM_CONFIG_FILE:-}" ] && [ -r "${SHIM_CONFIG_FILE:-}" ] || exit 1
      cat "$SHIM_CONFIG_FILE" ;;
    run)
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
      # 전역 이름 조회(`--filter name=^<이름>$`) — 프로젝트 라벨과 무관하다.
      [ -z "${SHIM_PS_NAME_HIT:-}" ] || printf '%s\n' "$SHIM_PS_NAME_HIT"
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
chmod +x "$T/bin/docker" "$T/bin/git"
export PATH="$T/bin:$PATH"

SHIM_LOG="$T/shim.log"
export SHIM_LOG SHIM_STATE="$T/state" SHIM_GIT_EXPECT_C="$REPO_ROOT"
export SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY="" SHIM_GIT_STATUS_RC=0
export SHIM_PS_FILE="" SHIM_PS_RC=0 SHIM_PS_NAME_HIT="" SHIM_CONFIG_FILE="" SHIM_IMAGE_IDS_OK=1
export SHIM_RUN_MARKER="" SHIM_RUN_SLEEP="" SHIM_RUN_RC=0

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
RUN_RC=0
( _require_lock_dir "$T/no-such-lock-dir" ) > "$T/out" 2> "$T/err" || RUN_RC=$?
_expect_rc 2 "잠금 자리가 없으면 판정 불가로 끝난다"
_expect_text '운영 자리를 만들지 않는다' "문면이 사유와 고치는 명령을 보인다"
if [ -e "$T/no-such-lock-dir" ]; then _bad "그 자리를 만들었다"; else _ok "그 자리를 만들지 않았다"; fi

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
_run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 0 "양성 — 잔존 0 · 같은 커밋 · 같은 프로젝트면 훑는다"
_expect_log "run .*--rm .*--pull never .*--name ${PROJ}-app-run-sweep" "일회 컨테이너 이름을 고정해 띄운다"

# 신호 경로 — trap 이 **자기 일회 컨테이너만** 치운다.
_signal_case() { # <신호> <기대 종료 코드>
  local sig="$1" want="$2" pid pg i n
  : > "$SHIM_LOG"
  rm -f "$T/marker"
  RUN_RC=0
  SHIM_RUN_MARKER="$T/marker" SHIM_RUN_SLEEP=20 \
    setsid ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}" > "$T/out" 2> "$T/err" &
  pid=$!
  for i in $(seq 1 200); do [ -e "$T/marker" ] && break; sleep 0.1; done
  [ -e "$T/marker" ] || { kill -9 "$pid" 2>/dev/null || true; _die "가짜 일회 컨테이너가 시작되지 않았다 — 판정 불가" 2; }
  # **`$!` 를 그룹 번호로 쓰지 않는다** — `setsid` 가 fork 하면 그 번호는 남의 그룹일 수 있다(공유 호스트).
  pg="$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')"
  [ -n "$pg" ] || { kill -9 "$pid" 2>/dev/null || true; _die "프로세스 그룹을 읽지 못했다 — 판정 불가" 2; }
  kill -s "$sig" -"$pg" 2>/dev/null || kill -s "$sig" "$pid"
  wait "$pid" || RUN_RC=$?
  _expect_rc "$want" "${sig} 으로 끝나면 그 신호로 죽는다(128+n — 종료 코드를 꾸미지 않는다)"
  _expect_text "일회 컨테이너 ${PROJ}-app-run-sweep 를 치웠다" "${sig} — trap 이 치운 것을 문면에 보인다"
  _expect_log "docker rm -f ${PROJ}-app-run-sweep\$" "${sig} — trap 이 자기 컨테이너 이름 하나만 지운다"
  _expect_no_log 'rm .*other-proj' "${sig} — trap 이 남의 컨테이너를 지우지 않는다"
  _expect_no_log 'rm .*(--filter|\*)' "${sig} — trap 이 접두·필터로 지우지 않는다"
  n="$(grep -cE '^docker rm ' "$SHIM_LOG" || true)"
  if [ "$n" = 1 ]; then _ok "${sig} — 삭제 호출이 하나다 (${n})"; else _bad "${sig} — 삭제 호출이 ${n} 개다"; fi
}
_signal_case TERM 143
_signal_case HUP 129

echo "== runbook — ExecStopPost 가 조건 없이 돌지 않는가"
# 문서에는 구조 술어가 없다 — 이 단언은 **텍스트 술어**다(unit 예시가 있는 층이 문서뿐이다). 조건 없는
# `ExecStopPost=…docker rm -f <고정 이름>` 은 수동 run 이 도는 중에 타이머가 발화하면(그 run 은 잠금에서
# exit 3) **남의 컨테이너**를 지운다. 조건의 존재를 `SERVICE_RESULT` 로 잠근다.
RB=docs/runbook/m7-7f-production.md
esp_n="$(grep -cE '^ExecStopPost=' "$RB" || true)"
esp_cond="$(grep -E '^ExecStopPost=' "$RB" | grep -cE 'SERVICE_RESULT' || true)"
if [ "$esp_n" -ge 1 ] && [ "$esp_n" = "$esp_cond" ]; then
  _ok "runbook 의 ExecStopPost ${esp_n} 줄이 모두 종료 사유 조건을 갖는다"
else
  _bad "runbook 의 ExecStopPost ${esp_n} 줄 중 ${esp_cond} 만 조건을 갖는다 — 조건 없는 정리는 남의 run 을 지운다"
fi

echo "prod-guards-check: 통과 ${PASS} · 어긋남 ${FAIL}"
[ "$FAIL" -eq 0 ] || exit 1
