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
# 에서 읽는다(정본은 그 파일 하나다). 호출자가 `--project` 를 주면 **뒤에 오는 `-p` 가 이긴다**.
BIDVECTOR_PROD_PROJECT="$(sed -n 's/^name:[[:space:]]*//p' "${_prod_env_root}/${BIDVECTOR_PROD_COMPOSE_FILES[2]}" 2>/dev/null | head -n1)"
unset _k _prod_env_template _prod_env_root
if [ -z "$BIDVECTOR_PROD_PROJECT" ]; then
  echo "prod-env: 운영 compose 의 name: 을 읽지 못했다 — 프로젝트 이름을 고정할 수 없다" >&2
  return 2 2>/dev/null || exit 2
fi

BIDVECTOR_PROD_COMPOSE=(docker compose)
for _f in "${BIDVECTOR_PROD_COMPOSE_FILES[@]}"; do
  BIDVECTOR_PROD_COMPOSE+=(-f "$_f")
done
BIDVECTOR_PROD_COMPOSE+=(-p "$BIDVECTOR_PROD_PROJECT")
unset _f

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
