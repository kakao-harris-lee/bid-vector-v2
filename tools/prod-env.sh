# M7/7F 본편 — 운영 프로필의 **compose 호출 단일 출처**. 실행 파일이 아니라 `source` 로 읽는 조각이다
# (`tools/local-tls-env.sh` 와 같은 형태 — 실행 비트를 주지 않는다).
#
# 로컬 조각과 달리 **값을 하나도 짓지 않는다**: 운영 값은 host 의 env 파일 하나가 주고(저장소 밖),
# 그 경로를 호출자가 `--env-file` 로 넘긴다. 여기서 자리값을 만들면 운영 env 파일에 없는 키가
# 조용히 채워져 「키 집합 등식」 preflight 가 공허해진다.
#
# 덧쓰기 순서가 두 자리로 갈리지 않게 파일 목록만 한 자리에 둔다. **반입·되돌림이 함께 보는 술어**도
# 여기 둔다(아래 `_prod_migrations`) — 두 스크립트가 같은 판단을 서로 다른 글자로 하면 한쪽만 고쳐지는
# 날이 온다.

_prod_env_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# **셸 env 가 `--env-file` 을 이긴다** — compose 보간의 우선순위다(v M-1 실측: env 파일이 38443 인데
# `BIDVECTOR_PROXY_HTTPS_PORT=18443` 을 내보낸 셸에서 preflight 가 18443 으로 통과했다).
# 가장 나쁜 꼴은 `tools/local-tls-env.sh` 를 source 한 셸에서 반입하는 것이다 — 그 조각이 같은 이름의
# 개발용 PG 자격을 `export` 하므로, 운영 DB 볼륨이 개발 자격으로 초기화될 수 있었다.
#
# 운영 값의 출처는 env 파일 **하나**여야 한다. 템플릿이 선언한 키를 여기서 비우고, 그 뒤 호출자가
# 명시적으로 내보내는 것(예: `prod-deploy.sh` 의 `BIDVECTOR_RELEASE_TAG`)만 의도된 덮어쓰기로 남긴다.
_prod_env_template="${_prod_env_root}/docker/prod.env.template"
if [ -r "$_prod_env_template" ]; then
  while IFS= read -r _k; do
    [ -n "$_k" ] || continue
    unset "$_k" 2>/dev/null || true
  done < <(grep -oE '^[A-Z_][A-Z0-9_]*=' "$_prod_env_template" | tr -d '=')
else
  echo "prod-env: 운영 env 템플릿을 읽지 못했다: ${_prod_env_template}(키 집합을 모르면 셸 값을 비울 수 없다)" >&2
  return 2 2>/dev/null || exit 2
fi
# **compose 자신의 변수도 비운다 — 이름을 열거하지 않고 접두로.** 템플릿 18키만 비우던 앞 판은
# `COMPOSE_PROJECT_NAME` 을 그대로 두었고, 그 변수는 compose 파일의 `name:` 을 이긴다. 개발 스택을
# 만지던 셸에서 반입하면 **다른 프로젝트의 빈 볼륨 위에 운영이 떴다**(v V-2 실측: `--project` 없이
# dry-run 이 「프로젝트 …-shadow · 데이터 볼륨이 없다」로 exit 0). 이름을 더 적는 처방은 같은 구멍을
# 다시 여므로 `COMPOSE_` 로 시작하는 것을 전부 비운다(`COMPOSE_FILE`·`COMPOSE_PROFILES`·
# `COMPOSE_ENV_FILES`·`COMPOSE_IGNORE_ORPHANS` …).
while IFS='=' read -r _k _; do
  case "$_k" in COMPOSE_*) unset "$_k" 2>/dev/null || true ;; esac
done < <(env)

BIDVECTOR_PROD_COMPOSE_FILES=(docker/compose.yaml docker/compose.local-tls.yaml docker/compose.prod.yaml)

# 프로젝트 이름을 **고정한다.** `-p` 는 `COMPOSE_PROJECT_NAME` 보다 우선이므로(실측), 위 unset 이
# 어떤 이유로 비껴가도 이 자리가 다시 막는다 — 겹을 둘로 둔다. 값은 짓지 않고 운영 덧쓰기의 `name:`
# 에서 읽는다(정본은 그 파일 하나다 — **마지막** 덧쓰기의 `name:` 이 이기므로 배열의 마지막 원소다).
# 호출자가 `--project` 를 주면 **뒤에 오는 `-p` 가 이긴다**.
BIDVECTOR_PROD_PROJECT="$(sed -n 's/^name:[[:space:]]*//p' "${_prod_env_root}/${BIDVECTOR_PROD_COMPOSE_FILES[-1]}" 2>/dev/null | head -n1)"
unset _k _prod_env_template _prod_env_root
if [ -z "$BIDVECTOR_PROD_PROJECT" ]; then
  echo "prod-env: 운영 compose 의 name: 을 읽지 못했다 — 프로젝트 이름을 고정할 수 없다" >&2
  return 2 2>/dev/null || exit 2
fi

# release 가 싣는 이미지 넷 — 반입·되돌림·훑기·release 가 **같은 집합**을 본다.
BIDVECTOR_PROD_SERVICES=(app web proxy ml-serving)

BIDVECTOR_PROD_COMPOSE=(docker compose)
for _f in "${BIDVECTOR_PROD_COMPOSE_FILES[@]}"; do
  BIDVECTOR_PROD_COMPOSE+=(-f "$_f")
done
BIDVECTOR_PROD_COMPOSE+=(-p "$BIDVECTOR_PROD_PROJECT")
unset _f

# release 태그의 모양 — `tools/prod-release.sh` 가 만들 수 있는 집합과 같다. 상태 파일에서 읽은 값을
# 경로·이미지 참조로 쓰기 전에 이것으로 거른다. 손상된 `current` 가 **compose 의 이미지 조회까지**
# 흘러가 거기서 exit 1 로 끝나던 길을 앞에서 끊는다(v V4-4 — 러너 자신의 「예외(1)」와 구별되지 않았다).
_prod_valid_tag() {
  [[ "${1:-}" =~ ^[a-z0-9][a-z0-9._-]{0,62}$ ]] && [ "$1" != local ]
}

# <manifest 경로> <태그> → 이미지 넷의 **현재 태그 ID 가 manifest 와 같은가.** 다르면 1 과 함께
# `_PROD_IMAGE_ERR` 에 사유를 둔다(종료 코드는 호출자마다 다르므로 끊는 것은 호출자가 한다).
#
# 반입 preflight 에만 있던 앞 판은 **주기 러너가 이 불변식을 거치지 않았다** — 누가 태그를 옮기면
# 반입은 멈추는데 러너는 옮겨진 이미지를 운영 DB 자격과 함께 띄웠다(v V4-2 실측).
_prod_images_match() {
  local man="$1" tag="$2" svc want have
  _PROD_IMAGE_ERR=""
  for svc in "${BIDVECTOR_PROD_SERVICES[@]}"; do
    # 매치 0 을 실패로 올리지 않는다 — 그러면 아래 「ID 가 없다」 문면에 닿지 못하고 조용히 죽는다.
    want="$({ grep -E "^image\.${svc}=" "$man" || true; } | cut -d= -f2-)"
    if [ -z "$want" ]; then
      _PROD_IMAGE_ERR="manifest 에 ${svc} 이미지 ID 가 없다: ${man}"
      return 1
    fi
    have="$(docker image inspect "bidvector/${svc}:${tag}" --format '{{.Id}}' 2>/dev/null || true)"
    if [ "$have" != "$want" ]; then
      _PROD_IMAGE_ERR="bidvector/${svc}:${tag} 의 ID 가 manifest 와 다르다(없거나 태그가 옮겨졌다)"
      return 1
    fi
  done
}

# **잠금의 자리는 환경에서 오지 않는다 — 고정 절대 경로다.** 앞 판은 `${TMPDIR:-/tmp}` 였고, `TMPDIR`
# 이 서로 다른 두 셸은 **다른 파일을 잠갔다** — 같은 프로젝트의 동시 반입이 환경 문자열 하나로 다시
# 열렸다(v V4-1). systemd unit 의 `PrivateTmp=yes` 도 같은 꼴이다. 그 설정이 가리는 것은 `/tmp` 와
# `/var/tmp` 뿐이라, FHS 의 잠금 자리는 unit 안팎이 **같은 파일**을 본다. tmpfs 라 재부팅에 지워지는
# 것도 맞다 — 잠금은 살아 있는 프로세스의 것이지 남겨 두는 기록이 아니다.
BIDVECTOR_PROD_LOCK_DIR=/run/lock/bidvector-v2

# <잠금 파일> <무엇> → `flock -n` 으로 잡는다(기다리지 않는다). 실패하면 1 과 `_PROD_LOCK_ERR` 를
# 남긴다 — 문면의 접두는 호출자마다 다르므로 끊는 것은 호출자가 한다.
_prod_lock() {
  local f="$1" what="$2" fd
  _PROD_LOCK_ERR=""
  exec {fd}>>"$f" || { _PROD_LOCK_ERR="${what} 잠금 파일을 열지 못했다: $f"; return 1; }
  flock -n "$fd" || {
    _PROD_LOCK_ERR="다른 반입·되돌림·훑기가 ${what}에서 돌고 있다($f) — 끝난 뒤 다시"
    eval "exec ${fd}>&-" 2>/dev/null || true
    return 1
  }
  BIDVECTOR_LOCK_FDS="${BIDVECTOR_LOCK_FDS:-} $fd"
}

# <프로젝트 이름> → 그 프로젝트의 잠금. **잠금 둘의 순서는 프로젝트 먼저, 그 다음 상태 디렉터리**이고
# 셋 스크립트가 같은 순서를 쓴다(교착이 생길 수 없다 — 셋 다 기다리지 않는다).
_prod_project_lock() {
  local proj="$1"
  _PROD_LOCK_ERR=""
  mkdir -p "$BIDVECTOR_PROD_LOCK_DIR" 2>/dev/null \
    || { _PROD_LOCK_ERR="잠금 디렉터리를 만들지 못했다: ${BIDVECTOR_PROD_LOCK_DIR}"; return 1; }
  _prod_lock "${BIDVECTOR_PROD_LOCK_DIR}/prod.${proj}.lock" "프로젝트 ${proj}"
}

_prod_unlock_all() {
  local fd
  for fd in ${BIDVECTOR_LOCK_FDS:-}; do
    flock -u "$fd" 2>/dev/null || true
    eval "exec ${fd}>&-" 2>/dev/null || true
  done
  BIDVECTOR_LOCK_FDS=""
}

# <상태 디렉터리> <release> → 그 release 의 마이그레이션 목록(정렬). manifest 가 없으면 1 을 돌려준다.
_prod_migrations() {
  local man="$1/releases/$2.manifest"
  [ -r "$man" ] || return 1
  grep -E '^migration=' "$man" | cut -d= -f2- | sort
}

# <상태 디렉터리> <from release> <to release> → from 에만 있는 마이그레이션(= 되돌리면 넘어야 하는 것).
# 둘 중 하나라도 manifest 를 못 읽으면 1 을 돌려준다(판정 불가 — 호출자가 끊는다).
_prod_migrations_lost() {
  local state="$1" from="$2" to="$3"
  _prod_migrations "$state" "$from" >/dev/null || return 1
  _prod_migrations "$state" "$to"   >/dev/null || return 1
  comm -23 <(_prod_migrations "$state" "$from") <(_prod_migrations "$state" "$to")
}
