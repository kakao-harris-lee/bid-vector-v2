# M7/7F-local — 로컬 패키징 프로필의 **값 기본값 단일 출처**. 실행 파일이 아니라 `source` 로
# 읽는 조각이다(그래서 실행 비트를 주지 않는다).
#
# 왜 파일로 뽑았나(cr M-5): 이미지 빌드가 `tools/local-tls-smoke.sh` 와 `.github/workflows/ci.yml`
# 두 자리에 있었고, ci.yml 쪽은 태그·컨텍스트·Dockerfile 을 **다시 적는** 둘째 정의였다. 빌드를
# compose 한 자리로 모으려면 compose 가 요구하는 값(`${VAR:?}`)을 빌드 시점에도 줘야 하는데,
# 그 기본값을 ci.yml 에 또 적으면 이번에는 **값**이 두 자리로 갈린다. 그래서 값도 한 자리로 둔다.
#
# **이미 설정된 값을 덮지 않는다**: CI 는 자격 값을 job 안에서 만들어 `::add-mask::` 로 가려
# 두었고, 여기서 새 값을 지으면 그 가림 밖의 값이 하나 생긴다.

export BIDVECTOR_PG_USER="${BIDVECTOR_PG_USER:-bidvector_local}"
export BIDVECTOR_PG_DB="${BIDVECTOR_PG_DB:-bidvector_local}"
export BIDVECTOR_PG_PASSWORD="${BIDVECTOR_PG_PASSWORD:-$(openssl rand -hex 20)}"
export BIDVECTOR_APP_PORT="${BIDVECTOR_APP_PORT:-18080}"
export BIDVECTOR_APP_MANAGEMENT_PORT="${BIDVECTOR_APP_MANAGEMENT_PORT:-18081}"
export BIDVECTOR_APP_JDBC_URL="${BIDVECTOR_APP_JDBC_URL:-jdbc:postgresql://postgres:5432/${BIDVECTOR_PG_DB}}"
export BIDVECTOR_APP_OPERATOR_CREDENTIAL="${BIDVECTOR_APP_OPERATOR_CREDENTIAL:-$(openssl rand -hex 24)}"
export BIDVECTOR_APP_CANDIDATE_CAP="${BIDVECTOR_APP_CANDIDATE_CAP:-1000}"
export BIDVECTOR_WEB_PORT="${BIDVECTOR_WEB_PORT:-18090}"
export BIDVECTOR_PROXY_HTTPS_PORT="${BIDVECTOR_PROXY_HTTPS_PORT:-18443}"
# 도메인은 **반드시 export 한다** — compose 는 이 변수를 통과시킬 뿐이라(기본값의 정본은
# `docker/proxy.Caddyfile`), 스모크의 curl 이 쓰는 이름과 컨테이너가 받는 이름이 한 실행 안에서
# 같은 값임이 이 한 줄로 보장된다.
export BIDVECTOR_PROXY_DOMAIN="${BIDVECTOR_PROXY_DOMAIN:-localhost}"

# compose 호출의 단일 출처 — 덧쓰기 순서가 두 자리로 갈리지 않게 한다.
BIDVECTOR_LOCAL_TLS_COMPOSE=(docker compose -f docker/compose.yaml -f docker/compose.local-tls.yaml)
