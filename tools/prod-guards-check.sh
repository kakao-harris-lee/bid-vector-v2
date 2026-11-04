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
# 잠금만은 가짜가 없다 — 세 스크립트가 **고정 절대 경로**(`tools/prod-env.sh` 의
# `BIDVECTOR_PROD_LOCK_DIR`)를 쓰는 것이 설계이기 때문이다(환경변수에서 오면 두 셸이 서로 다른 파일을
# 잠근다). 그래서 이 검사는 그 자리에 **검사 전용 프로젝트 이름**의 잠금 파일을 만들고 끝에 지운다 —
# 운영 프로젝트의 잠금 파일은 건드리지 않는다. 자리를 쓸 수 없으면 「통과」가 아니라 exit 2 다.
#
# 신호 경로의 한계: systemd 는 cgroup 전체에 TERM 을 보내므로 compose 도 함께 죽고 trap 이 바로 돈다.
# 여기서는 `setsid` 로 프로세스 그룹을 만들어 같은 모양을 흉내낸다 — 그룹 kill 이 안 되는 환경이면
# 가짜 run 이 끝난 뒤 trap 이 돌고, 그래도 **무엇을 지우는가**는 같은 값으로 잰다.
#
# 종료 코드: 0 전부 통과 · 1 하나라도 어긋남 · 2 전제 실패(도구·fixture·잠금 자리 — 판정 불가).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

_die() { echo "prod-guards-check: $1" >&2; exit "${2:-1}"; }

for t in jq git flock mktemp setsid sed awk; do
  command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2
done

# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

PASS=0 FAIL=0
_ok()  { PASS=$((PASS + 1)); echo "  ok   — $1"; }
_bad() { FAIL=$((FAIL + 1)); echo "  BAD  — $1" >&2; }

T="$(mktemp -d)" || _die "임시 디렉터리를 만들지 못했다" 2
PROJ="bv-guards-check-$$"
_cleanup() {
  rm -rf "$T"
  rm -f "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}.lock" "${BIDVECTOR_PROD_LOCK_DIR}/prod.${PROJ}-probe.lock"
}
trap _cleanup EXIT

# 잠금 자리의 **양성 대조** — 운영 스크립트와 같은 술어로 먼저 잡아 본다. 못 잡으면 아래 세 경로가
# 전부 「잠금 실패」로 끝나므로 판정 불가다(검사 전용 이름으로 잡고 바로 놓는다).
_prod_project_lock "${PROJ}-probe" || _die "${_PROD_LOCK_ERR} — 이 검사의 전제다(CI 러너라면 'sudo install -d -m 0755 -o \$(id -un) -g \$(id -gn) ${BIDVECTOR_PROD_LOCK_DIR}'). 판정 불가" 2
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
    want=""
    for a in "$@"; do
      case "$a" in label=com.docker.compose.project=*) want="${a#label=com.docker.compose.project=}" ;; esac
    done
    [ -z "${SHIM_PS_FILE:-}" ] || while IFS='|' read -r p n o; do
      [ -n "$p" ] || continue
      [ "$p" = "$want" ] || continue
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
while [ "${1:-}" = -C ]; do shift 2; done
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
export SHIM_LOG SHIM_STATE="$T/state"
export SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY="" SHIM_GIT_STATUS_RC=0
export SHIM_PS_FILE="" SHIM_PS_RC=0 SHIM_CONFIG_FILE="" SHIM_IMAGE_IDS_OK=1
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

echo "== L-1 술어 — 반입 트리 ↔ manifest git_sha"
_predicate_git() { # <HEAD> <dirty> <manifest>
  RUN_RC=0
  : > "$SHIM_LOG"
  ( export SHIM_GIT_HEAD="$1" SHIM_GIT_DIRTY="$2"
    _prod_git_sha_match "$3" || { printf '%s\n' "$_PROD_GIT_SHA_ERR" >&2; exit 1; }
  ) > "$T/out" 2> "$T/err" || RUN_RC=$?
}
MAN="$T/state/releases/${REL}.manifest"
_predicate_git "$REL_SHA" "" "$MAN"
_expect_rc 0 "양성 — HEAD == git_sha · 추적 파일 깨끗이면 통과"
_predicate_git "$OTHER_SHA" "" "$MAN"
_expect_rc 1 "음성 — 다른 커밋의 트리는 거절"
_expect_text "checkout ${REL_SHA}" "음성 — 문면이 필요한 checkout 커밋을 보인다"
_predicate_git "$REL_SHA" " M tools/prod-env.sh" "$MAN"
_expect_rc 1 "음성 — 추적 파일이 더러우면 거절(커밋 등식만으로는 트리가 같지 않다)"
_expect_text '커밋되지 않은 변경이 1개' "음성 — 더티 계수를 문면에 보인다"
printf 'release=x\n' > "$T/nogitsha.manifest"
_predicate_git "$REL_SHA" "" "$T/nogitsha.manifest"
_expect_rc 1 "음성 — manifest 에 git_sha 가 없으면 거절(없음을 통과로 읽지 않는다)"
_predicate_git "" "" "$MAN"
_expect_rc 1 "음성 — HEAD 를 읽지 못하면 거절(git 부재를 통과로 읽지 않는다)"

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
_predicate_oneoff "${PROJ}|${PROJ}-app-1|False
" 0
_expect_rc 0 "양성 — 떠 있는 서비스 컨테이너는 잔존이 아니다"
_predicate_oneoff "${PROJ}|${PROJ}-operator-once|True
" 0
_expect_rc 1 "음성 — oneoff 라벨 축으로 잡는다(이름이 규칙 밖이어도)"
_expect_text "${PROJ}-operator-once" "음성 — 문면이 치울 이름을 보인다"
_predicate_oneoff "${PROJ}|${PROJ}-app-run-9f2c|
" 0
_expect_rc 1 "음성 — 이름 축으로 잡는다(라벨이 비어도)"
_predicate_oneoff "other-proj|other-proj-app-run-zzz|True
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

echo "== 주기 러너(prod-notification-sweep.sh) — 잔존·이름·trap"
SWEEP_ARGS=(--env-file "$T/env/prod.env" --state-dir "$T/state" --project "$PROJ")
export SHIM_CONFIG_FILE="$T/cfg.json" SHIM_GIT_HEAD="$REL_SHA" SHIM_GIT_DIRTY=""
SHIM_GIT_HEAD="$OTHER_SHA" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "다른 커밋의 트리에서는 훑지 않는다"
_expect_no_log ' run ' "거절이 일회 컨테이너 앞에서 끝난다"
printf '%s|%s-app-run-abc|True\nother-proj|other-proj-app-run-zzz|True\n' "$PROJ" "$PROJ" > "$T/ps-residue"
SHIM_PS_FILE="$T/ps-residue" _run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 3 "지난 run 의 일회 컨테이너가 남아 있으면 훑지 않는다"
_expect_text "${PROJ}-app-run-abc" "거절 문면이 남은 이름을 보인다"
_expect_no_log ' run ' "거절이 일회 컨테이너 앞에서 끝난다(잔존 축)"
printf '%s|%s-app-1|False\nother-proj|other-proj-app-run-zzz|True\n' "$PROJ" "$PROJ" > "$T/ps-clean"
export SHIM_PS_FILE="$T/ps-clean"
_run ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}"
_expect_rc 0 "양성 — 잔존 0 · 같은 커밋이면 훑는다"
_expect_log "run .*--rm .*--pull never .*--name ${PROJ}-app-run-sweep" "일회 컨테이너 이름을 고정해 띄운다"

# 신호 경로 — trap 이 **자기 일회 컨테이너만** 치운다.
: > "$SHIM_LOG"
rm -f "$T/marker"
RUN_RC=0
SHIM_RUN_MARKER="$T/marker" SHIM_RUN_SLEEP=20 \
  setsid ./tools/prod-notification-sweep.sh "${SWEEP_ARGS[@]}" > "$T/out" 2> "$T/err" &
sweep_pid=$!
for _ in $(seq 1 200); do [ -e "$T/marker" ] && break; sleep 0.1; done
[ -e "$T/marker" ] || { kill -9 "$sweep_pid" 2>/dev/null || true; _die "가짜 일회 컨테이너가 시작되지 않았다 — 판정 불가" 2; }
kill -TERM -"$sweep_pid" 2>/dev/null || kill -TERM "$sweep_pid"
wait "$sweep_pid" || RUN_RC=$?
_expect_rc 143 "TERM 으로 끝나면 그 신호로 죽는다(128+15 — 종료 코드를 꾸미지 않는다)"
_expect_text "일회 컨테이너 ${PROJ}-app-run-sweep 를 치웠다" "trap 이 치운 것을 문면에 보인다"
_expect_log "docker rm -f ${PROJ}-app-run-sweep\$" "trap 이 자기 컨테이너 **이름 하나**만 지운다"
_expect_no_log 'rm .*other-proj' "trap 이 남의 컨테이너를 지우지 않는다"
_expect_no_log 'rm .*(--filter|\*)' "trap 이 접두·필터로 지우지 않는다"
rm_n="$(grep -cE '^docker rm ' "$SHIM_LOG" || true)"
if [ "$rm_n" = 1 ]; then _ok "trap 의 삭제 호출이 하나다 (${rm_n})"; else _bad "trap 의 삭제 호출이 ${rm_n} 개다"; fi

echo "prod-guards-check: 통과 ${PASS} · 어긋남 ${FAIL}"
[ "$FAIL" -eq 0 ] || exit 1
