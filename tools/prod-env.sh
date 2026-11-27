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
#
# **`GIT_*` 도 같은 축이다**(CR M-1 실측): `git -C <트리>` 는 `GIT_DIR` 을 **이기지 못한다** — 그 변수
# 하나면 아래 `_prod_git_sha_match` 의 HEAD 등식과 더티 검사가 **둘 다** 다른 저장소를 읽는다. git 이
# hook·`rebase --exec`·`bisect run` 안에서 스스로 내보내는 변수라 사고 경로도 가깝다. 역시 이름을
# 열거하지 않고 접두로 비운다(`GIT_DIR`·`GIT_WORK_TREE`·`GIT_INDEX_FILE`·`GIT_COMMON_DIR`·
# `GIT_OBJECT_DIRECTORY`·`GIT_CONFIG_*` …). source 한 **뒤에** 들어온 값은 `_prod_git` 이 막는다.
while IFS='=' read -r _k _; do
  case "$_k" in COMPOSE_*|GIT_*) unset "$_k" 2>/dev/null || true ;; esac
done < <(env)
# **데몬 선택 축도 닫는다**(M15/15R, S-8). `DOCKER_HOST`·`DOCKER_CONTEXT` 는 **이 조각을 source 하는 전부**
# (반입·되돌림·health·키 집합 검사·러너 셋)의 **모든 docker
# 호출**이 어느 데몬을 보는지를 환경 문자열 하나로 바꾼다. 오늘도 fail-closed 이긴 하다 — 남의 데몬에서는
# `_prod_images_match` 가 이미지를 못 찾아 `exit 3` 에서 선다. 그래도 §13.4 ①~⑤ 는 **운영자 셸에서 손으로**
# 미는 절차이고 그 셸이 다른 context 를 쥐고 있을 수 있는데, 그때 나오는 거절은 「데몬을 잘못 골랐다」는
# 이야기를 하지 않는다.
#
# **접두로 비우지 않는다** — `DOCKER_CONFIG`·`HOME` 은 **플러그인 탐색 축**이고 cr N-1 이 되살린 자리다
# (떨어뜨리면 compose 가 사용자 플러그인으로 깔린 host 에서 매 run 이 「통째로 버렸다」로 끝난다. docker 는
# `HOME` 이 없으면 `/etc/passwd` 로 되돌아가지 않는다).
#
# **그런데 데몬을 고르는 축은 둘이 아니라 셋이다**(M15/15R, v F6): 그 둘을 비워도 `DOCKER_CONFIG`(또는
# `HOME`) 아래 `config.json` 의 **`currentContext`** 가 남고, 그것이 `docker context use` 가 쓰는 바로 그
# 자리다 — 「운영자 셸이 다른 context 를 쥐고 있다」의 **가장 흔한 꼴**이 이것이다(실측: 버릴
# `DOCKER_CONFIG` 에 `currentContext` 를 세우면 두 변수를 비운 셸도 그 context 로 간다).
# 그래서 지우는 대신 **내장 `default` context 를 못박는다** — `DOCKER_CONTEXT` 는 `currentContext` 를
# 이기므로(같은 실측) 플러그인 탐색 축을 건드리지 않고 셋째 축이 닫힌다.
#
# **이 못박기는 운영자가 `docker context use` 로 고른 context 도 덮는다** — 앞 판(`unset`)과 base 에서는
# 그 선택이 살아 있었다. 운영 데몬이 `default` **밖**에만 있는 host(rootless 를 context 로 쓰는 배치)에서는
# 기본 소켓을 보게 되고, 그때 **멈추는 쪽과 틀린 답을 내는 쪽이 갈린다**(cr L-9): 이미지 등식
# (`_prod_images_match`)을 부르는 넷(반입·훑기·평가 커밋·relay. 되돌림은 반입의 preflight 로 전이)은
# `exit 3` 에서 서지만, **`prod-health.sh` 는 그 등식이 없어 다른 데몬의 상태를 건강 판정으로 낸다** —
# 멈추지 않고 틀린 답을 낸다. 오늘 영향은 0 이다(이 host 와 CI 러너는 두 변수 없이 `default` 로 돈다 —
# 실측). 등재된 제한 5 가 그 둘을 가른다.
unset DOCKER_HOST 2>/dev/null || true
export DOCKER_CONTEXT=default

BIDVECTOR_PROD_COMPOSE_FILES=(docker/compose.yaml docker/compose.local-tls.yaml docker/compose.prod.yaml)

# 프로젝트 이름을 **고정한다.** `-p` 는 `COMPOSE_PROJECT_NAME` 보다 우선이므로(실측), 위 unset 이
# 어떤 이유로 비껴가도 이 자리가 다시 막는다 — 겹을 둘로 둔다. 값은 짓지 않고 운영 덧쓰기의 `name:`
# 에서 읽는다(정본은 그 파일 하나다 — **마지막** 덧쓰기의 `name:` 이 이기므로 배열의 마지막 원소다).
# 호출자가 `--project` 를 주면 **뒤에 오는 `-p` 가 이긴다**.
BIDVECTOR_PROD_PROJECT="$(sed -n 's/^name:[[:space:]]*//p' "${_prod_env_root}/${BIDVECTOR_PROD_COMPOSE_FILES[-1]}" 2>/dev/null | head -n1)"
# **반입 트리의 자리**. 아래 `_prod_git_sha_match` 가 보는 저장소다 — cwd 에 기대지 않는다(호출자가
# 어디서 부르든 compose 파일을 읽는 트리는 이 조각이 있는 트리 하나다).
BIDVECTOR_PROD_REPO_ROOT="$_prod_env_root"
unset _k _prod_env_template _prod_env_root
if [ -z "$BIDVECTOR_PROD_PROJECT" ]; then
  echo "prod-env: 운영 compose 의 name: 을 읽지 못했다 — 프로젝트 이름을 고정할 수 없다" >&2
  return 2 2>/dev/null || exit 2
fi

# release 가 싣는 이미지 넷 — 반입·되돌림·훑기·release 가 **같은 집합**을 본다.
BIDVECTOR_PROD_SERVICES=(app web proxy ml-serving)

# compose 의 하한 — `!override` 와 `run --pull` 을 아는 판. 문턱이 반입에만 있던 앞 판은 **러너 단독
# 경로에 문턱이 없었고**, 내려앉은 host 에서 `--pull` 이 「알 수 없는 플래그」로 죽으면 그 종료 코드가
# 러너 자신의 「1 예외」와 섞여 journal 에서 앱 장애로 보였다(CR L-7 — V4-4 가 닫은 것과 같은 혼동 축).
BIDVECTOR_PROD_COMPOSE_MIN="2.24.0"

# 어긋나면 1 과 `_PROD_COMPOSE_VER_ERR`.
_prod_compose_version_ok() {
  local cv
  _PROD_COMPOSE_VER_ERR=""
  cv="$(docker compose version --short 2>/dev/null | sed 's/^v//')"
  if [ -z "$cv" ]; then
    _PROD_COMPOSE_VER_ERR="docker compose 판을 읽지 못했다"
    return 1
  fi
  if [ "$(printf '%s\n%s\n' "$BIDVECTOR_PROD_COMPOSE_MIN" "$cv" | sort -V | head -n1)" != "$BIDVECTOR_PROD_COMPOSE_MIN" ]; then
    _PROD_COMPOSE_VER_ERR="compose ${cv} < ${BIDVECTOR_PROD_COMPOSE_MIN} — !override 와 run --pull 을 모른다"
    return 1
  fi
  return 0
}

BIDVECTOR_PROD_COMPOSE=(docker compose)
for _f in "${BIDVECTOR_PROD_COMPOSE_FILES[@]}"; do
  BIDVECTOR_PROD_COMPOSE+=(-f "$_f")
done
BIDVECTOR_PROD_COMPOSE+=(-p "$BIDVECTOR_PROD_PROJECT")
unset _f

# **해석된 compose 가 컨테이너에 주는 env 키의 집합**(ml 프로필 없이 — 반입·훑기가 쓰는 모양).
# 값이 아니라 **키**를 고정한다: 새 키가 compose 에 들어오면 preflight 가 멈추고, 사람이 「이것이 app
# 설정을 바꾸는 통로인가」를 한 번 본다. v V5-1 이 바로 그 축이었다 — `JAVA_TOOL_OPTIONS` 는 템플릿
# 키 집합 게이트를 통과하는 키인데 **내용은 아무도 보지 않았고**, 거기 넣은 `-D` 시스템 속성이 compose
# 가 준 env 를 이겨 app 이 가드와 다른 DB 에 붙었다. 통로 이름을 열거해 거부하면 새 이름 하나로 다시
# 열리므로 **표면 자체를 등식으로** 닫는다.
BIDVECTOR_PROD_SERVICE_ENV_KEYS=(
  app.BIDVECTOR_EVALUATION_CANDIDATECAP
  app.BIDVECTOR_PERSISTENCE_CREDENTIAL
  app.BIDVECTOR_PERSISTENCE_JDBCURL
  app.BIDVECTOR_PERSISTENCE_USERNAME
  # M9/ops-migrations(V28): 실행 행에 찍는 릴리스 식별자. 값은 반입 스크립트가 스스로 내보내는
  # `BIDVECTOR_RELEASE_TAG` 이고(compose.yaml) 운영 env 파일에서 오지 않는다 — app 설정을 바꾸는 통로가
  # 아니다. 2026-11-21 첫 반입에서 이 목록에 빠진 것이 드러났다.
  app.BIDVECTOR_RELEASE_SHA
  app.JAVA_TOOL_OPTIONS
  app.MANAGEMENT_SERVER_PORT
  app.OPERATOR_CREDENTIAL_VALUE
  app.SERVER_PORT
  postgres.POSTGRES_DB
  postgres.POSTGRES_PASSWORD
  postgres.POSTGRES_USER
  proxy.BIDVECTOR_PROXY_APP_UPSTREAM
  proxy.BIDVECTOR_PROXY_DOMAIN
  proxy.BIDVECTOR_PROXY_HTTPS_PORT
  proxy.BIDVECTOR_PROXY_WEB_UPSTREAM
  web.BIDVECTOR_WEB_PORT
)

# app 의 JVM 옵션 문자열 — **허용 형태 하나**다(거부 목록이 아니다). runbook 「메모리」 표가 쓰는
# 그 형태이고, 그 밖의 무엇도 받지 않는다. 자유 문자열인 이 키가 운영 app 의 유일한 옵션 통로다.
_prod_jvm_options_ok() {
  [[ "${1:-}" =~ ^-XX:MaxRAMPercentage=([0-9]{1,2}|100)(\.[0-9]+)?$ ]]
}

# <해석된 config json> → app 설정 통로가 기대대로인가. 어긋나면 1 과 `_PROD_SURFACE_ERR`.
# **값은 문면에 옮기지 않는다** — 이 키에 자격을 실을 수 있다.
_prod_config_surface_ok() {
  local cfg="$1" got want
  _PROD_SURFACE_ERR=""
  got="$(printf '%s' "$cfg" | jq -r '.services | to_entries[] | .key as $s | (.value.environment // {} | keys[]) | "\($s).\(.)"' | sort)"
  want="$(printf '%s\n' "${BIDVECTOR_PROD_SERVICE_ENV_KEYS[@]}" | sort)"
  if [ "$got" != "$want" ]; then
    _PROD_SURFACE_ERR="해석된 compose 의 서비스 env 키 집합이 고정 목록과 다르다 — 더해짐[$(comm -23 <(printf '%s\n' "$got") <(printf '%s\n' "$want") | tr '\n' ' ')] 빠짐[$(comm -13 <(printf '%s\n' "$got") <(printf '%s\n' "$want") | tr '\n' ' ')]. 새 키가 app 설정을 바꾸는 통로인지 보고 tools/prod-env.sh 의 목록을 갱신한다"
    return 1
  fi
  if ! _prod_jvm_options_ok "$(printf '%s' "$cfg" | jq -r '.services.app.environment.JAVA_TOOL_OPTIONS // empty')"; then
    _PROD_SURFACE_ERR="app 의 JVM 옵션이 허용 형태가 아니다 — 받는 것은 '-XX:MaxRAMPercentage=<0..100>' 하나뿐이다. 이 문자열의 -D 시스템 속성은 compose 가 준 env 를 이기므로 app 이 가드와 다른 DB·자격으로 갈 수 있다"
    return 1
  fi
  return 0
}

# JDK HttpClient 의 **내부 로그 속성 둘**(M15/15Q). 켜면 JDK 자신이 요청 URL 을 stderr 로 찍고, 이
# 저장소의 바깥 호출은 자격을 **URL 에** 싣는다(Telegram 봇 자격은 경로에 · 조달청 서비스 키는 질의
# 문자열에). 그래서 운영의 일회 러너에서 이 둘은 **금지**다 — 이름은 `app` 의 `HttpClientLogProperty`
# 와 같은 쌍이고, 러너 자신도 `RefusedHttpClientLogging` 으로 기동을 거부한다(runbook §13.3·§13.6).
#
# 래퍼가 **먼저** 끊는 이유: 기동 거부는 종료 1 이라 러너의 「예외」와 같은 값이다. 전제 실패(3)로
# 가르면 journal 에서 둘이 구별된다 — 훑기가 손상된 `current` 를 앞에서 끊는 것과 같은 축이다.
#
# compose 축은 여기 없다 — `_prod_config_surface_ok` 가 `app.JAVA_TOOL_OPTIONS` 를 허용 형태 **하나**로
# 이미 묶었다(그 형태에는 `-D` 가 들어갈 자리가 없다). 겹을 두 자리에 두지 않는다.
BIDVECTOR_PROD_HTTPCLIENT_LOG_PROPS=(jdk.httpclient.HttpClient.log jdk.internal.httpclient.debug)

# 이 프로세스의 JVM 옵션 통로 셋에 그 속성이 있는가. 있으면 1 과 `_PROD_HTTPCLIENT_LOG_ERR`.
# **판정은 존재만 본다** — `=false`·빈 값처럼 JDK 가 실제로는 아무것도 찍지 않는 값도 거절이다
# (값마다 JDK 의 해석을 흉내 내지 않는 쪽이 fail-closed 다, runbook §13.3).
_prod_jvm_env_log_free() {
  local v p
  _PROD_HTTPCLIENT_LOG_ERR=""
  for v in JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
    for p in "${BIDVECTOR_PROD_HTTPCLIENT_LOG_PROPS[@]}"; do
      case "${!v:-}" in
        *"$p"*)
          _PROD_HTTPCLIENT_LOG_ERR="이 프로세스의 ${v} 에 JDK HttpClient 내부 로그 속성이 지정돼 있다(${p}) — 켜면 요청 URL 이 stderr 로 나가고 그 URL 은 자격을 싣는다. 값이 false·빈 값이어도 거절이다(판정은 존재만 본다). 그 속성을 지운 뒤 다시"
          return 1 ;;
      esac
    done
  done
  return 0
}

# <파일> → 그 파일에 그 속성이 있는가. 있으면 1 과 `_PROD_HTTPCLIENT_LOG_ERR`.
# **파일 내용은 어느 출력으로도 건너오지 않는다**(`grep -q`) — 이 자리에 오는 파일은 자격을 담는다.
_prod_httpclient_log_free_file() {
  local f="$1" p
  _PROD_HTTPCLIENT_LOG_ERR=""
  for p in "${BIDVECTOR_PROD_HTTPCLIENT_LOG_PROPS[@]}"; do
    if grep -qF -- "$p" "$f" 2>/dev/null; then
      _PROD_HTTPCLIENT_LOG_ERR="${f} 에 JDK HttpClient 내부 로그 속성이 있다(${p}) — runbook §13.3 이 금지한다. 그 줄을 지운다(문면은 줄 내용을 옮기지 않는다)"
      return 1
    fi
  done
  return 0
}

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
  # 통과를 루프 마지막 `if` 의 암묵 반환에 맡기지 않는다 — 루프 끝에 0 이 아닐 수 있는 명령이 하나
  # 들어오면 함수가 **조용히 1** 이 되고 `_PROD_IMAGE_ERR` 는 빈 채라, 호출자가 사유 없는 거절을 찍는다.
  return 0
}

# <manifest 경로> → **지금 반입 트리가 그 release 를 만든 커밋인가.** 다르면 1 과 함께
# `_PROD_GIT_SHA_ERR` 에 사유를 둔다(종료 코드는 호출자마다 다르다 — 반입 1 · 되돌림 2 · 러너 3).
#
# 이미지 ID 등식(`_prod_images_match`)이 묶는 것은 **이미지 바이트**뿐이다. compose 파일 셋·프로필·
# 포트·볼륨 이름·`mem_limit`·헬스체크·그리고 이 스크립트들 자신은 **이 checkout 에서** 읽히므로,
# 다른 커밋의 트리에서 반입하면 「release 의 이미지」가 「다른 커밋의 배선」으로 뜬다. 주기 러너는
# 더 멀다 — 타이머가 도는 동안 누가 트리를 옮기면 그 뒤의 모든 run 이 옮겨진 배선으로 돈다
# (러너는 사본이 아니라 저장소에서 직접 돈다, runbook §9.1).
#
# 더티 판정은 `tools/prod-release.sh` 와 **같은 규칙**이다(`--untracked-files=no`) — 그래서 같은 한계를
# 물려받는다: 추적되지 않는 파일은 세지 않는다(CR L-10).

# 반입 트리의 git — **`GIT_*` 를 비운 채** 부른다. 위 source 시점의 unset 과 **겹 둘**이다: 이 조각을
# 읽은 뒤에 누가 `GIT_DIR` 을 내보내도(hook·`rebase --exec`·`bisect run` 안이 그런 자리다) 술어가
# 다른 저장소를 읽지 않는다. 이름을 열거하지 않고 접두로 비운다 — 열거하는 처방은 같은 구멍을 다시 연다.
_prod_git() {
  (
    local _gk
    while IFS='=' read -r _gk _; do
      case "$_gk" in GIT_*) unset "$_gk" 2>/dev/null || true ;; esac
    done < <(env)
    git -C "$BIDVECTOR_PROD_REPO_ROOT" "$@"
  )
}

_prod_git_sha_match() {
  local man="$1" want have dirty
  _PROD_GIT_SHA_ERR=""
  # 매치 0 을 실패로 올리지 않는다 — 그러면 아래 「git_sha 가 없다」 문면에 닿지 못한다.
  want="$({ grep -E '^git_sha=' "$man" || true; } | cut -d= -f2-)"
  if [ -z "$want" ]; then
    _PROD_GIT_SHA_ERR="manifest 에 git_sha 가 없다: ${man}"
    return 1
  fi
  have="$(_prod_git rev-parse HEAD 2>/dev/null || true)"
  if [ -z "$have" ]; then
    _PROD_GIT_SHA_ERR="반입 트리의 커밋을 읽지 못했다(${BIDVECTOR_PROD_REPO_ROOT} — git 이 없거나 저장소가 아니다)"
    return 1
  fi
  if [ "$have" != "$want" ]; then
    _PROD_GIT_SHA_ERR="반입 트리가 이 release 를 만든 커밋이 아니다(HEAD ${have:0:12} ≠ manifest ${want:0:12}) — compose 배선과 이 스크립트들은 트리에서 읽힌다. 'git -C ${BIDVECTOR_PROD_REPO_ROOT} checkout ${want}' 뒤에 다시 돌린다"
    return 1
  fi
  dirty="$(_prod_git status --porcelain --untracked-files=no 2>/dev/null)" || {
    _PROD_GIT_SHA_ERR="반입 트리의 상태를 읽지 못했다(${BIDVECTOR_PROD_REPO_ROOT})"
    return 1
  }
  if [ -n "$dirty" ]; then
    _PROD_GIT_SHA_ERR="반입 트리의 추적 파일에 커밋되지 않은 변경이 $(printf '%s\n' "$dirty" | grep -c '')개 있다 — release 를 만든 커밋 그대로여야 한다(git status --porcelain --untracked-files=no)"
    return 1
  fi
  return 0
}

# <프로젝트 이름> → 그 프로젝트의 **일회(one-off) app 컨테이너 잔존이 0 인가.** 남아 있으면 1 과
# `_PROD_ONEOFF_ERR`(이름 목록은 `_PROD_ONEOFF_NAMES`).
#
# 일회 컨테이너는 `compose run --rm` 이 끝날 때 지워진다. 그런데 러너가 **신호로** 끝나면
# (systemd 의 `TimeoutStartSec` 상한 — runbook §9.2) `--rm` 이 돌지 못하고 **운영 DB 자격을 받은
# 컨테이너가 남는다.** 다음 run 이 그것을 보지 않으면 잔존이 아무 때나 쌓이고, 남은 컨테이너가
# 떠 있으면 그 안의 러너가 다음 run 과 같은 DB 를 동시에 쓴다.
#
# 판정 축은 **둘의 합집합**이다 — ⓐ compose 의 `oneoff` 라벨 ⓑ 이름 `<프로젝트>-app-run-…`.
# 교집합으로 두면 compose 의 라벨 값 표기나 이름 규칙 한쪽이 바뀌는 날 술어가 **조용히 0** 이 된다
# (「안 돌린 게이트」와 같은 값). 조회 자체가 실패하면 0 으로 읽지 않고 거절한다.
_prod_oneoff_residue() {
  local proj="$1" out name oneoff
  _PROD_ONEOFF_ERR=""; _PROD_ONEOFF_NAMES=""
  out="$(docker ps -a \
      --filter "label=com.docker.compose.project=${proj}" \
      --filter "label=com.docker.compose.service=app" \
      --format '{{.Names}}|{{.Label "com.docker.compose.oneoff"}}' 2>/dev/null)" || {
    _PROD_ONEOFF_ERR="프로젝트 ${proj} 의 일회 컨테이너 잔존을 세지 못했다(docker ps 실패) — 판정 불가"
    return 1
  }
  while IFS='|' read -r name oneoff; do
    [ -n "$name" ] || continue
    case "$oneoff" in
      [Tt]rue) ;;
      *) case "$name" in "${proj}-app-run-"*) ;; *) continue ;; esac ;;
    esac
    _PROD_ONEOFF_NAMES="${_PROD_ONEOFF_NAMES}${name} "
  done < <(printf '%s\n' "$out")
  if [ -n "$_PROD_ONEOFF_NAMES" ]; then
    _PROD_ONEOFF_ERR="프로젝트 ${proj} 에 지난 run 의 일회 컨테이너가 남아 있다: ${_PROD_ONEOFF_NAMES}— 신호로 끝났거나 docker 가 치우지 못했다. 'docker logs <이름>' 으로 그 run 을 보고 'docker rm -f <이름>' 으로 치운 뒤 다시 돌린다(runbook §9.4)"
    return 1
  fi
  return 0
}

# **잠금의 자리는 환경에서 오지 않는다 — 고정 절대 경로다.** 앞 판은 `${TMPDIR:-/tmp}` 였고, `TMPDIR`
# 이 서로 다른 두 셸은 **다른 파일을 잠갔다** — 같은 프로젝트의 동시 반입이 환경 문자열 하나로 다시
# 열렸다(v V4-1). systemd unit 의 `PrivateTmp=yes` 도 같은 꼴이다. 그 설정이 가리는 것은 `/tmp` 와
# `/var/tmp` 뿐이라, FHS 의 잠금 자리는 unit 안팎이 **같은 파일**을 본다. tmpfs 라 재부팅에 지워지는
# 것도 맞다 — 잠금은 살아 있는 프로세스의 것이지 남겨 두는 기록이 아니다.
BIDVECTOR_PROD_LOCK_DIR=/run/lock/bidvector-v2

# <잠금 파일> <무엇> → `flock -n` 으로 잡는다(기다리지 않는다). 실패하면 1 과 `_PROD_LOCK_ERR` 를
# 남긴다 — 문면의 접두는 호출자마다 다르므로 끊는 것은 호출자가 한다.
#
# **경합과 전제 실패를 가른다**(M15/15M, cr H-1). 이 함수가 1 을 돌려주는 사유는 두 부류다: 자리·권한이
# 어긋난 **전제 실패**와, 남이 쥐고 있어 **이번만 못 잡은 경합**이다. 뒤엣것은 §9.1 이 「다음 주기로
# 미룬다」고 적는 **설계된 정상**이라 경보를 울리면 안 된다. 호출자가 그 둘을 **문면으로** 가르면
# 스타일 하나로 열리므로 여기서 `_PROD_LOCK_BUSY` 로 넘긴다(경합일 때만 `1`). 읽지 않는 호출자는
# 지금까지와 같다.
#
# **경합을 종료 코드로 못박는다**(v F1). `flock -n` 의 실패를 전부 경합으로 보면 **경합이 아닌 실패**
# (EBADF 65 · `flock` 부재 127 · 사용법 64)까지 busy 로 올라가고, 그러면 훑기가 `4` 로 끝나 운영의
# `SuccessExitStatus=4` 가 그것을 **영구히 삼킨다**(실측: `PATH=/nonexistent` 로 부르면 busy 가 섰다).
# `-E <코드>` 는 **경합일 때만** 그 코드를 돌려주므로(util-linux 2.39.3 실측: 경합 = 준 코드 · 획득 0 ·
# EBADF 65 · 부재 127 · 모르는 옵션 64) 갈림이 열거가 아니라 **구성**으로 닫힌다. `-E` 를 모르는
# util-linux 는 64 를 내므로 그 host 에서는 전부 전제 실패(`3`)다 — fail-closed 다.
#
# **코드는 sysexits(64~78) 밖에서 고른다**(v R2-3). util-linux 는 자기 오류에 그 범위를 쓰므로(실측
# 64·65), 범위 안의 값을 쓰면 「경합」과 「도구 오류」가 언젠가 같은 수로 만날 수 있다. 200 은 그
# 범위 밖이고 셸이 특별히 보는 자리(126·127·128+n)도 아니다 — `( exit 200 )` 이 200 으로 돌아옴을 쟀다.
_PROD_LOCK_BUSY_CODE=200
_prod_lock() {
  local f="$1" what="$2" fd rc=0
  _PROD_LOCK_ERR=""
  _PROD_LOCK_BUSY=""
  exec {fd}>>"$f" || { _PROD_LOCK_ERR="${what} 잠금 파일을 열지 못했다: $f"; return 1; }
  flock -n -E "$_PROD_LOCK_BUSY_CODE" "$fd" || rc=$?
  if [ "$rc" -ne 0 ]; then
    if [ "$rc" -eq "$_PROD_LOCK_BUSY_CODE" ]; then
      _PROD_LOCK_ERR="다른 반입·되돌림·훑기가 ${what}에서 돌고 있다($f) — 끝난 뒤 다시"
      _PROD_LOCK_BUSY=1
    else
      _PROD_LOCK_ERR="${what} 잠금을 시험하지 못했다 — flock 종료 ${rc}(경합이 아니다): $f"
    fi
    eval "exec ${fd}>&-" 2>/dev/null || true
    return 1
  fi
  BIDVECTOR_LOCK_FDS="${BIDVECTOR_LOCK_FDS:-} $fd"
}

# <프로젝트 이름> → 그 프로젝트의 잠금. **잠금 둘의 순서는 프로젝트 먼저, 그 다음 상태 디렉터리**이고
# 셋 스크립트가 같은 순서를 쓴다(교착이 생길 수 없다 — 셋 다 기다리지 않는다).
_prod_project_lock() {
  local proj="$1" owner mode
  _PROD_LOCK_ERR=""
  _PROD_LOCK_BUSY=""
  mkdir -p "$BIDVECTOR_PROD_LOCK_DIR" 2>/dev/null \
    || { _PROD_LOCK_ERR="잠금 디렉터리를 만들지 못했다: ${BIDVECTOR_PROD_LOCK_DIR}"; return 1; }
  # **자리의 소유자와 권한을 본다.** `/run/lock` 은 sticky·world-writable(1777)이고 tmpfs 라 재부팅마다
  # 비므로, 그 사이 다른 계정이 이 이름을 선점할 수 있다. 남이 소유한 자리는 잠금이 서로 안 보이게
  # 만들고, group/other 쓰기가 열린 자리는 **잠금 파일을 지워 inode 를 가르는** 우회를 누구에게나
  # 연다(v V5-2 실측: 쥐어진 잠금의 파일을 지우면 다음 반입이 새 inode 를 잠그고 지나간다).
  # 부팅 때 자리를 미리 만드는 법은 runbook §1.4 의 tmpfiles.d 한 줄.
  owner="$(stat -c '%u' "$BIDVECTOR_PROD_LOCK_DIR" 2>/dev/null || true)"
  mode="$(stat -c '%a' "$BIDVECTOR_PROD_LOCK_DIR" 2>/dev/null || true)"
  if [ "$owner" != "$(id -u)" ]; then
    _PROD_LOCK_ERR="잠금 디렉터리의 소유자가 내가 아니다(${BIDVECTOR_PROD_LOCK_DIR} uid=${owner:-?} ≠ $(id -u)) — 남이 선점한 자리에서는 잠금이 서로 보이지 않는다. runbook §1.4"
    return 1
  fi
  # 마지막 세 자리가 ugo 다(앞의 setuid·setgid·sticky 자리는 쓰기 권한과 무관하다). `022` = g+o 쓰기.
  if [ -z "$mode" ] || [ "$(( 8#${mode: -3} & 8#022 ))" -ne 0 ]; then
    _PROD_LOCK_ERR="잠금 디렉터리의 권한을 읽지 못했거나 group/other 쓰기가 열려 있다(${BIDVECTOR_PROD_LOCK_DIR} mode=${mode:-?}) — 잠금 파일을 지워 우회할 수 있다. runbook §1.4"
    return 1
  fi
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

# ── 둘째 env 파일 — compose 템플릿 **밖**의 설정을 일회 컨테이너까지 넘기는 통로 ──────────────
# M15/15Q 가 relay 래퍼에 세우고 **M15/15U 가 수집 래퍼와 공유하게 뽑았다.** 사본 둘을 두면 한쪽만
# 고쳐지는 날이 온다(v2-지침서 §5 중복 금지) — 이 통로의 술어는 여기 하나다.
#
# 이 통로가 왜 좁혀야 하는가: compose 의 최상위 `--env-file` 은 **보간에만** 쓰여 컨테이너에 닿지
# 않으므로(실측) 둘째 파일이 컨테이너까지 가는 길은 `run --env-from-file` 하나다. 그리고 그 값은 서비스가
# 선언한 env 에 **더하고, 같은 이름이면 이긴다**(실측). 그래서 이 파일에 들어온 `JAVA_TOOL_OPTIONS` 나
# `BIDVECTOR_PERSISTENCE_JDBCURL` 한 줄이 **반입 가드를 거치지 않은 채** compose 가 준 값을 덮는다
# (`_prod_config_surface_ok` 는 해석된 compose 만 본다 — 이 파일은 그 밖이다). 통로 이름을 열거해
# 거부하면 새 이름 하나로 다시 열리므로 **표면 자체를 등식으로** 닫는다.
#
# 허용 이름은 손으로 열거하지 않고 **속성 이름에서 만든다** — Spring 의 `SystemEnvironmentPropertyMapper`
# 가 환경 변수 후보를 **둘** 만든다(dash 제거 · dash→`_`). 두 사상을 여기서 같은 규칙으로 되풀이하면
# 「문서에 적힌 철자」와 「바인딩되는 철자」가 갈리지 않는다.
#
# 호출자가 쓰는 자리:
#   BIDVECTOR_SECOND_ENV_REQUIRED_PROPS=( <속성 이름…> )   # 없으면 거절
#   BIDVECTOR_SECOND_ENV_OPTIONAL_PROPS=( <속성 이름…> )   # 있어도 되고 없어도 된다
#   _prod_stub_trap_install                                  # 해석 **앞**에 신호 trap 을 건다
#   _prod_second_env_ok <경로> <라벨> <자리 참조> || _die "$_PROD_SECOND_ENV_ERR" 3
#   <그 뒤> "$_PROD_SECOND_ENV_ABS"                          # 재는 것과 넘기는 것이 **같은 경로**다
BIDVECTOR_SECOND_ENV_REQUIRED_PROPS=()
BIDVECTOR_SECOND_ENV_OPTIONAL_PROPS=()

# <속성 이름> → 그 속성이 받는 환경 변수 이름 둘
_prod_spring_env_names() {
  local p="$1" dots
  dots="$(printf '%s' "$p" | tr '.' '_' | tr '[:lower:]' '[:upper:]')"
  printf '%s\n' "${dots//-/}"   # dash 제거   — bidvector.…bot-credential → …BOTCREDENTIAL
  printf '%s\n' "${dots//-/_}"  # dash → `_`  — bidvector.…bot-credential → …BOT_CREDENTIAL
}
_prod_second_env_allowed_names() {
  local p
  # 빈 배열에서도 `set -u` 아래 안전한 꼴로 펼친다(선택 속성이 없는 호출자가 있다).
  for p in ${BIDVECTOR_SECOND_ENV_REQUIRED_PROPS[@]+"${BIDVECTOR_SECOND_ENV_REQUIRED_PROPS[@]}"} \
           ${BIDVECTOR_SECOND_ENV_OPTIONAL_PROPS[@]+"${BIDVECTOR_SECOND_ENV_OPTIONAL_PROPS[@]}"}; do
    _prod_spring_env_names "$p"
  done
}

# **임시 자리는 부모가 만들고 부모가 치운다**(cr R-7 · N-2). 해석 함수는 명령 치환 `$( )` 안에서 돌므로
# **서브셸**이고, 거기서 전역에 적은 자리는 부모에게 건너오지 않는다(첫 판이 그래서 신호 뒤에 자리를
# 남겼다 — 실측). `RETURN` trap 도 서브셸 안에서만 도므로 신호 갈래를 덮지 못한다.
_PROD_STUB_DIR=""
# `|| true` — 지우지 못한 것이 이 자리의 반환값을 바꾸지 않는다(cr N-4).
_prod_stub_cleanup() {
  [ -z "$_PROD_STUB_DIR" ] || rm -rf "$_PROD_STUB_DIR" || true
  _PROD_STUB_DIR=""
}
_prod_stub_on_signal() {
  local sig="$1"
  trap - TERM INT HUP
  _prod_stub_cleanup
  kill -s "$sig" $$
}
# `$$` 는 이 함수가 서브셸이 아니므로 **부르는 스크립트의 PID** 다(trap 이 그것을 자살시킨다).
_prod_stub_trap_install() {
  trap '_prod_stub_on_signal TERM' TERM
  trap '_prod_stub_on_signal INT' INT
  trap '_prod_stub_on_signal HUP' HUP
}

# **키 집합은 소비자와 같은 해석기로 뽑는다**(v H-1). 앞 판은 줄마다 **첫 `KEY=` 를 sed 로** 뽑았고,
# compose 의 dotenv 해석기는 **따옴표로 닫힌 값 뒤의 둘째 대입을 새 변수로** 읽는다 — 그래서
# `…ROUTEKEY="rk" BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:evil` 한 줄이 래퍼의 모든 검사를 통과하고
# 컨테이너에는 그 DB 자격 통로가 들어갔다(verifier 실측). 술어가 **소비자와 다른 문법**에 걸려 있으면
# 꼴을 하나씩 막는 처방은 다음 꼴에서 다시 열린다. 그래서 문법을 늘리지 않고 **해석기를 바꾼다**:
# `env_file:` 로 이 파일을 받는 stub 서비스에 `docker compose config` 를 돌려 그 서비스의 environment
# 키 집합을 읽는다.
#
# **두 해석기가 같다는 것은 실측이다**(M15/15Q evidence commands — 정상 · 우회 넷 · 버려짐 셋 · 빈 파일,
# 아홉 입력 전부에서 `config`+`env_file:` 의 키 집합 == `run --env-from-file` 이 컨테이너에 넣는 키 집합).
#
# **값은 어디로도 나가지 않는다** — `config` 출력은 변수로 받아 `jq` 가 **키만** 낸다(argv·화면·로그
# 어디에도 값이 없다). stderr 는 버린다.
# 돌려주는 코드: 0 해석함 · 1 **compose 가 거절했다**(그 파일의 일) · 2 그 밖의 도구가 어긋났다
# (`mktemp`·`jq` — 그 파일의 일이 **아니다**, cr R-1). 호출자가 둘을 다른 문면으로 낸다.
_prod_second_env_parsed_keys() { # <파일 절대 경로> <stub 디렉터리 — 부모가 만들어 준다>
  local f="$1" stub="$2" out keys rc=0
  # **경로를 JSON 문자열로 넣는다**(v L-2). 따옴표 없이 넣으면 경로의 ` #' 이 YAML 주석이 되어
  # `config` 가 **다른 파일**을 읽는다 — 그러면 검사한 파일과 컨테이너가 받는 파일이 갈린다(실측).
  {
    printf 'name: bidvector-second-env-check\n'
    printf 'services:\n'
    printf '  probe:\n'
    printf '    image: bidvector-second-env-check:none\n'
    printf '    env_file:\n'
    printf '      - %s\n' "$(jq -Rn --arg p "$f" '$p')"
  } > "$stub/compose.yaml" || return 2
  # **평소 환경으로 부른다 — 문턱 둘과 같은 env 다.** 한때 이 호출만 `env -i "PATH=$PATH"` 로
  # 좁혔는데(v L-3 처방의 절반), 그것이 `HOME`·`DOCKER_CONFIG` 까지 떨어뜨려 **compose 가 사용자
  # 플러그인으로 깔린 host 에서 매 run 이 「통째로 버렸다」로 끝나게** 했다(cr N-1 실측 — docker 는
  # `HOME` 이 없으면 `/etc/passwd` 로 되돌아가지 않는다). 그 좁힘은 지금 아무것도 막지 않는다 —
  # 아래 「값에 `$` 를 쓰지 않는다」 전제가 **해석보다 먼저** 파일 전수를 보므로 여기 닿는 파일에는
  # `$` 가 한 글자도 없고, 두 해석기 어느 쪽에서도 보간이 **일어날 수 없다**(cr N-1 처방 ⓐ).
  out="$(docker compose -f "$stub/compose.yaml" config --format json 2>/dev/null)" || rc=$?
  [ "$rc" -eq 0 ] || return 1
  keys="$(printf '%s' "$out" | jq -r '.services.probe.environment // {} | keys[]' 2>/dev/null)" || return 2
  printf '%s' "$keys" | sort -u
}

# 주석·빈 줄이 아닌 줄의 수 — **해석기가 파일을 통째로 버렸는가**를 가르는 데만 쓴다(v L-1). 닫히지
# 않은 따옴표·`"v" !!x`·`${X:?m}` 같은 줄이 있으면 compose 는 rc 0 으로 **파일 전체를 무시한다** —
# 그대로 두면 러너가 「설정이 비었다」로 끝나 진단이 엉뚱한 곳을 가리킨다.
_prod_second_env_content_lines() { sed -n '/^[[:space:]]*$/d; /^[[:space:]]*#/d; p' "$1" | grep -c '' || true; }

# <경로> <라벨> <자리 참조> → 0 이면 `_PROD_SECOND_ENV_ABS` 에 **해석된 절대 경로**가 있다.
# 어긋나면 1 과 `_PROD_SECOND_ENV_ERR`(종료 코드는 호출자가 정한다 — 세 래퍼 모두 전제 실패 `3` 이다).
_prod_second_env_ok() {
  local f="$1" label="$2" where="$3" abs mode keys content_n parse_rc=0 stray_raw stray_named stray_odd p
  _PROD_SECOND_ENV_ERR=""
  _PROD_SECOND_ENV_ABS=""
  # **이 파일은 부르는 사용자가 직접 읽어야 한다**(cr H-2). 앞 판의 `EnvironmentFile=` 은 systemd
  # manager(root)가 읽어 환경으로 넘겼지만, 이제는 래퍼와 docker CLI 가 `User=` 로 읽는다.
  if [ ! -r "$f" ]; then
    _PROD_SECOND_ENV_ERR="${label} 파일을 읽지 못했다: ${f} — 이 파일은 **이 스크립트를 돌리는 사용자**($(id -un))가 직접 읽는다(${where} 의 'install -m 600 -o \$USER'). root 전용으로 조이면 매 run 이 여기서 멈춘다"
    return 1
  fi
  # **경로를 한 번만 풀고 그 뒤로는 그것만 쓴다**(cr R-2). 해석·권한·금지 속성 스캔·컨테이너 전달이
  # **같은 것을 보아야** 「술어와 소비자가 같은 것을 본다」가 경로 축에서도 참이 된다.
  abs="$(readlink -f "$f" 2>/dev/null || true)"
  if [ -z "$abs" ]; then
    _PROD_SECOND_ENV_ERR="${label} 의 절대 경로를 구하지 못했다: ${f}"
    return 1
  fi
  # **other 에 한 비트라도 열려 있으면 거절한다.** 이 파일은 자격을 담는다 — 잠금 자리의 권한을 보는
  # `_prod_project_lock` 과 같은 축이다. 소유자 **등식**은 걸지 않는다: `root:<내 group> 0640` 처럼
  # 읽을 수 있고 좁은 배치가 있고, 그것까지 거절하면 도는 host 를 멈춘다. group 은 잠금 자리 술어
  # (`& 022`)보다 느슨하다 — 그 틈은 설치 절차의 「그 사용자의 전용 group」이 닫는다.
  mode="$(stat -c '%a' "$abs" 2>/dev/null || true)"
  if [ -z "$mode" ] || [ "$(( 8#${mode: -3} & 8#007 ))" -ne 0 ]; then
    _PROD_SECOND_ENV_ERR="${label} 의 권한을 읽지 못했거나 other 에 열려 있다(${f} mode=${mode:-?}) — 이 파일은 자격을 담는다(${where}). 'chmod 600' 뒤에 다시"
    return 1
  fi
  # ── compose 문턱 둘 — 이 파일을 그 해석기로 읽기 **전에** 선다 ─────────────────
  # 아래 `run --pull never` 가 compose 판에 기댄다 — 문턱이 반입에만 있으면 내려앉은 host 에서 「알 수
  # 없는 플래그」의 종료 코드가 러너 자신의 「1 예외」와 섞인다. 이 둘이 **먼저** 서야 아래 해석의
  # 실패가 「판이 낡았다」와 섞이지 않는다.
  _prod_compose_version_ok || { _PROD_SECOND_ENV_ERR="$_PROD_COMPOSE_VER_ERR"; return 1; }
  # **`run --env-from-file` 을 아는 판인가** — 같은 축의 둘째 문턱이다. 판 번호로 묻지 않고 **그 도구에게
  # 직접** 묻는다(이 플래그가 들어온 판 번호를 지어내면 그 수가 곧 거짓이 되는 자리다). 모르면 둘째 env
  # 파일이 컨테이너에 닿지 않고, 그 run 은 설정을 채웠는데도 기동 실패로 끝난다.
  # 여기서는 **파이프를 물리지 않는다**: `docker … | grep -q` 는 플래그 줄이 앞쪽에 있어 docker 를
  # SIGPIPE 로 죽일 수 있고, `pipefail` 아래에서 그 141 이 「모르는 판」으로 읽힌다. 값으로 받아 맞춘다.
  # **빈 출력은 따로 말한다**(cr L-6) — 「플래그가 없다」와 「도움말을 받지 못했다」는 고칠 자리가 다르다.
  local run_help
  run_help="$(docker compose run --help 2>/dev/null || true)"
  if [ -z "$run_help" ]; then
    _PROD_SECOND_ENV_ERR="'docker compose run --help' 이 아무것도 내지 않았다 — 플래그 지원을 판정할 수 없다(docker 가 깨졌거나 권한이 없다). 'docker compose version' 을 먼저 본다"
    return 1
  fi
  case "$run_help" in
    *--env-from-file*) ;;
    *)
      _PROD_SECOND_ENV_ERR="이 docker compose 는 'run --env-from-file' 을 모른다 — ${label} 가 일회 컨테이너에 닿지 않는다(${where}). compose 를 올린다"
      return 1 ;;
  esac
  # **`jq` 도 같은 문턱을 받는다**(cr R-1). 아래 키 집합 조회는 compose 와 `jq` 두 도구를 지나는데,
  # `jq` 가 깨진 것을 「그 파일이 깨졌다」로 읽으면 운영자가 엉뚱한 파일을 들여다본다.
  command -v jq >/dev/null 2>&1 || {
    _PROD_SECOND_ENV_ERR="jq 가 필요하다 — ${label} 의 키 집합을 compose 출력에서 뽑는 데 쓴다"
    return 1
  }
  printf '{}' | jq -e . >/dev/null 2>&1 || {
    _PROD_SECOND_ENV_ERR="jq 가 가장 단순한 JSON 도 읽지 못한다 — 도구가 깨졌다(${label} 의 일이 아니다)"
    return 1
  }
  # **경로에도 `$` 를 쓰지 않는다**(v M-1). 경로는 stub YAML 의 **값**으로 들어가고 compose 는 그 값을
  # 보간한다 — JSON 인용은 YAML 파싱만 닫고 보간은 닫지 않는다. 그래서 `…/${NOPE:-relay.env}` 는
  # `config` 가 이웃을, `run --env-from-file` 이 그 파일을 읽게 만든다(실측).
  case "$abs" in
    *'$'*)
      _PROD_SECOND_ENV_ERR="${label} 의 경로에 '\$' 가 있다 — 그 글자는 compose 가 stub 의 값으로 보간해서, 검사한 파일과 컨테이너가 받는 파일이 갈린다. '\$' 없는 경로에 두고 다시(${where} 의 자리가 그 꼴이다)"
      return 1 ;;
  esac
  # **값에도 `$` 를 쓰지 않는다**(v L-3). `config` 와 `run --env-from-file` 은 `${VAR}` 를 **서로 다른
  # env** 로 푼다 — `$` 가 한 글자도 없으면 조회가 일어날 수 없어 둘이 같은 파일을 같게 읽는다. 이것은
  # 키 집합을 뽑는 문법이 아니라 **좁히는 전제**다(거절만 더할 수 있고 통과를 만들 수 없다).
  #
  # **값 자리만 본다**(cr N-3) — 주석·빈 줄은 건너뛰고 첫 `=` **뒤**를 본다. 주석에 쓴 `$` 까지 거절하면
  # 운영자가 고칠 수 없는 자리를 가리키게 된다(`=` 가 없는 줄은 해석기가 따로 끊는다).
  if sed -n '/^[[:space:]]*#/d; s/^[^=]*=//p' "$abs" | grep -q '\$'; then
    _PROD_SECOND_ENV_ERR="${label} 의 **값**에 '\$' 가 있다(${f}) — 값의 \${VAR} 는 compose 의 두 통로가 **서로 다른 env** 로 풀어 같은 파일이 다르게 읽힌다(${where} 의 값에는 '\$' 가 필요 없다). 그 글자를 지운다(문면은 줄 내용을 옮기지 않는다)"
    return 1
  fi
  _PROD_STUB_DIR="$(mktemp -d)" || {
    _PROD_SECOND_ENV_ERR="${label} 의 키 집합을 뽑는 **도구**가 어긋났다(임시 자리를 만들지 못했다) — ${label} 의 일이 아니다. TMPDIR 를 본다"
    return 1
  }
  keys="$(_prod_second_env_parsed_keys "$abs" "$_PROD_STUB_DIR")" || parse_rc=$?
  _prod_stub_cleanup
  if [ "$parse_rc" -eq 2 ]; then
    _PROD_SECOND_ENV_ERR="${label} 의 키 집합을 뽑는 **도구**가 어긋났다(stub 쓰기·jq) — ${label} 의 일이 **아니다**. 임시 자리와 jq 를 본다"
    return 1
  fi
  content_n="$(_prod_second_env_content_lines "$abs")"
  # **읽지 못했거나 키가 0 인데 내용 줄이 있다 = 해석기가 이 파일을 통째로 버렸다.**
  # 두 갈래를 한 문면으로 모으는 이유: `config` 는 그런 입력에 **rc 1 로 말하지만**(실측)
  # `run --env-from-file` 은 **rc 0 으로 조용히 버린다**(verifier 실측). 소비자 쪽이 조용하므로 여기서
  # 크게 말하는 것이 이 검사의 값이다.
  if [ "$parse_rc" -ne 0 ] || { [ -z "$keys" ] && [ "$content_n" != 0 ]; }; then
    if [ "$content_n" = 0 ]; then
      _PROD_SECOND_ENV_ERR="${label} 를 compose 해석기로 읽지 못했다(${f} — 내용 줄이 0 이다) — 판정 불가. 'docker compose version' 과 그 파일의 권한을 본다"
      return 1
    fi
    _PROD_SECOND_ENV_ERR="compose 해석기가 ${label} 를 **통째로 버렸다**(${f} — 내용 줄 ${content_n} 인데 키가 0 이다). 닫히지 않은 따옴표 · 값 뒤의 '!!' · '\${VAR:?…}' · 'KEY=' 모양이 아닌 줄이 그렇게 만든다. **일회 컨테이너 쪽은 이것을 조용히 버린다** — 그대로 두면 러너가 '설정이 비었다'로 끝나 엉뚱한 곳을 가리킨다(문면은 줄 내용을 옮기지 않는다 — 이 파일은 자격을 담는다)"
    return 1
  fi
  # **거절 문면은 이름을 전수로 찍지 않는다**(M15/15R, S-7). 여기 보이는 키는 compose 의 dotenv 해석기가
  # **이 파일의 바이트에서** 만들어 낸 이름이고(따옴표 뒤의 둘째 대입을 새 변수로 읽는다 — v H-1), 그래서
  # 편집 사고가 자격 조각을 키 **이름**으로 만들 수 있다. journal 은 지우기 어려우므로 **환경 변수 이름
  # 꼴만** 찍고 나머지는 개수로만 말한다. 거절 자체는 꼴과 무관하게 그대로다.
  # 남는 자리: 이름 꼴인 조각(`LEAKED_123456` 따위)은 여전히 찍힌다 — 이름을 전부 가리면 운영자가 무엇을
  # 지워야 하는지 모른다(등재된 제한).
  stray_raw="$(comm -23 <(printf '%s\n' "$keys" | grep -v '^$' | sort -u) <(_prod_second_env_allowed_names | sort -u))"
  if [ -n "${stray_raw//[[:space:]]/}" ]; then
    # **글자 범위를 로캘에서 떼어 놓는다**(cr L-6) — `[A-Z]` 는 로캘의 조합 순서에 따라 뜻이 달라질 수
    # 있고, 이것은 **무엇을 journal 에 찍을지 고르는 보안 술어**다. 이 두 줄에만 건다.
    stray_named="$(printf '%s\n' "$stray_raw" | LC_ALL=C grep -E '^[A-Z][A-Z0-9_]*$' | tr '\n' ' ' || true)"
    stray_odd="$(printf '%s\n' "$stray_raw" | grep -v '^$' | LC_ALL=C grep -cvE '^[A-Z][A-Z0-9_]*$' || true)"
    _PROD_SECOND_ENV_ERR="${label} 에 ${where} 밖의 키가 있다: 이름 꼴 ${stray_named:-(없음) }· 이름 꼴이 아닌 키 ${stray_odd} 개(이름을 찍지 않는다 — 그 이름은 이 파일의 바이트에서 만들어진 것이라 자격 조각일 수 있다) — 이 파일은 일회 컨테이너의 env 를 **덮으므로**, 여기 들어온 이름 하나가 반입 가드를 거치지 않고 compose 가 준 값을 이긴다. ${where} 의 키만 둔다(키는 compose 자신의 해석기가 뽑은 것이다 — 한 줄에 대입이 둘이면 둘 다 보인다)"
    return 1
  fi
  for p in ${BIDVECTOR_SECOND_ENV_REQUIRED_PROPS[@]+"${BIDVECTOR_SECOND_ENV_REQUIRED_PROPS[@]}"}; do
    local n found=""
    while IFS= read -r n; do
      [ -n "$n" ] || continue
      case $'\n'"${keys}"$'\n' in *$'\n'"$n"$'\n'*) found=1 ;; esac
    done < <(_prod_spring_env_names "$p")
    if [ -z "$found" ]; then
      _PROD_SECOND_ENV_ERR="${label} 에 필수 키가 없다(${p} — $(_prod_spring_env_names "$p" | tr '\n' ' ')) — ${where} 를 채우거나 그 파일을 가리키는 인자를 본다. **값이 비었는지는 여기서 보지 않는다**(그 갈래는 러너가 기동 거부로 말한다)"
      return 1
    fi
  done
  _PROD_SECOND_ENV_ABS="$abs"
  return 0
}
