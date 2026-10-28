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

# ---- 이미지 내용 지문(chore 2026-10-28) ----
#
# 「게이트가 판정한 바이트 == 스모크가 실행하는 바이트」를 재는 값이다. 앞 판은 `docker image
# inspect --format '{{.Id}}'` 를 썼는데 **그 값의 뜻이 데몬의 이미지 저장소에 따라 다르다**:
#   · 기존 저장소(overlay2) — `.Id` 는 이미지 **config 다이제스트**다. 캐시가 맞는 재빌드면 같다.
#   · containerd 이미지 저장소 — BuildKit 이 provenance attestation 을 함께 내보내므로 태그가
#     **manifest list(index)** 를 가리키고 `.Id` 는 그 index 다이제스트다. attestation 에는 빌드
#     시각·빌드 ID 가 들어가 **같은 바이트를 다시 구워도 매번 달라진다.**
# 2026-10-27 실측(run 40528127458 로그) — 같은 캐시로 두 번 구운 web 이미지의 manifest
# `ffd7268a…`·config `791f0e02…` 는 **같고** index 만 `39e034ce…` → `a133fb77…` 로 달랐다.
# 즉 등식이 깨진 자리는 바이트가 아니라 **provenance 기록**이었다. `.Id` 를 재면 바이트가 아니라
# 그 기록을 재게 되므로, 그 축에서는 등식이 영원히 성립하지 않는다(상시 붉은 게이트).
#
# 그래서 저장소와 무관하게 **내용만** 담는 두 값으로 지문을 만든다 — layer 사슬(`RootFS.Layers`,
# config 의 `rootfs.diff_ids` 와 같은 값)과 이미지 config(`Config`). 둘이 같으면 실행되는 바이트가
# 같고, 어느 layer·설정 하나라도 바뀌면 지문이 바뀐다. index·attestation 은 이 지문에 들어오지
# 않는다. 약속을 **약하게 하지 않고 같은 축으로** 옮긴 것이다.
#
# 선언적 대안은 쓰지 못했다: compose 의 `build.provenance`·`build.sbom` 키는 **조용히 무시된다**
# (2026-10-28 실측 — `docker compose build --print` 의 bake target 에 `attest` 가 없다). 그 경로로
# 핀을 적으면 파일은 핀을 주장하는데 빌드는 그대로 attestation 을 붙인다.
# 없는 이미지에는 **아무것도 찍지 않고 비-0** 이다. 빈 입력을 그대로 해시하면 `e3b0c442…`(빈
# 문자열의 해시)라는 **그럴듯한 지문**이 나와 「없는 이미지 둘이 서로 같다」가 되는데, 그것이
# 이 스크립트가 2026-10-19 에 한 번 겪은 함정의 같은 형태다(`--format` 은 없는 이미지에 빈 줄을
# 찍고 실패한다). 그래서 값을 먼저 받아 비었는지 보고 나서 해시한다.
bidvector_image_fingerprint() { # <이미지 참조> → 지문 한 줄, 이미지가 없으면 비-0
  local _out
  _out="$(docker image inspect "$1" --format '{{json .RootFS.Layers}}{{json .Config}}' 2>/dev/null)" || return 1
  [ -n "$_out" ] || return 1
  printf '%s' "$_out" | sha256sum | cut -d' ' -f1
}
