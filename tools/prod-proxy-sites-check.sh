#!/usr/bin/env bash
# M7/7F 본편 — 두 proxy site 와 운영 compose 의 **구조 등식**(D-7F2-2). 컨테이너를 띄우지 않는다 —
# `caddy adapt` 와 `docker compose config` 의 해석 결과만 본다.
#
#     ./tools/prod-proxy-sites-check.sh [--proxy-image <이미지>]
#
#   (S1) 두 site 의 공유 본문이 같다 — 로컬(`/etc/caddy/Caddyfile`)과 운영(`/etc/caddy/Caddyfile.prod`)을
#        **이미지 안에 구워진 바이트로** adapt 하고, site 라우트의 하위 라우트에서 운영의 HSTS **핸들러**
#        하나를 뺀 나머지가 같은지, 접근 로그 설정이 같은지 잰다. 문자열이 아니라 JSON 값의 등식이다.
#        (Caddy 는 같은 조건의 `header` 지시어 둘을 한 라우트의 핸들러 둘로 합친다 — 2026-10-22 실측.
#        그래서 빼는 단위는 라우트가 아니라 핸들러다.)
#   (S2) 차이는 선언한 것뿐이다 — 운영: TLS 앱 없음 · 자동 HTTPS 끔 · 프로토콜 h1 · HSTS 값 하나 ·
#        site 이름이 도메인 하나. 로컬: `tls internal` · HSTS 없음.
#   (S3) 운영 compose 의 해석 결과 — 프로젝트 이름이 개발 스택과 다르다 · host 포트를 여는 서비스는
#        proxy 하나 · 그 publish 는 `127.0.0.1` 하나 · proxy 는 운영 site 를 고른다 · ml-serving 은 기본
#        미기동(profile) · 모든 기동 서비스에 mem_limit · release 태그가 `local` 이 아니다.
#
# 종료 코드: 0 · 1 등식·단언 실패 · 2 판정 불가(도구·이미지 부재, 해석 실패).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

PROXY_IMAGE="bidvector/proxy:local"
TEST_DOMAIN="bidvector.test"
TEST_PORT=28443
EXPECT_HSTS="max-age=86400"
DEV_PROJECT="bidvector-v2"
EXPECT_PROJECT="bidvector-v2-prod"

_die() { echo "prod-proxy-sites-check: $1" >&2; exit "${2:-1}"; }
FAILS=0
fail() { echo "  FAIL: $1" >&2; FAILS=$((FAILS + 1)); }

while [ "$#" -gt 0 ]; do
  case "$1" in
    --proxy-image) [ "$#" -ge 2 ] || _die "--proxy-image 값이 없다" 2; PROXY_IMAGE="$2"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
for t in docker jq; do command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2; done
docker image inspect "$PROXY_IMAGE" >/dev/null 2>&1 || _die "proxy 이미지가 없다: $PROXY_IMAGE" 2

_adapt() {
  docker run --rm --security-opt no-new-privileges \
    -e BIDVECTOR_PROXY_DOMAIN="$TEST_DOMAIN" -e BIDVECTOR_PROXY_HTTPS_PORT="$TEST_PORT" \
    -e BIDVECTOR_PROXY_APP_UPSTREAM=app:1 -e BIDVECTOR_PROXY_WEB_UPSTREAM=web:2 \
    --entrypoint caddy "$PROXY_IMAGE" adapt --config "$1" --adapter caddyfile 2>/dev/null
}

echo "== (S1)(S2) site 등식 — 이미지 ${PROXY_IMAGE}"
local_json="$(_adapt /etc/caddy/Caddyfile)" || _die "로컬 site 를 adapt 하지 못했다" 2
prod_json="$(_adapt /etc/caddy/Caddyfile.prod)" || _die "운영 site 를 adapt 하지 못했다" 2
[ -n "$local_json" ] && [ -n "$prod_json" ] || _die "adapt 결과가 비었다" 2

# 서버가 정확히 하나, site 라우트가 정확히 하나여야 아래 경로가 뜻을 갖는다(양성 대조).
for which in local prod; do
  j="$([ "$which" = local ] && printf '%s' "$local_json" || printf '%s' "$prod_json")"
  [ "$(printf '%s' "$j" | jq '.apps.http.servers | length')" = "1" ] || _die "$which: 서버가 하나가 아니다(판정 불가)" 2
  [ "$(printf '%s' "$j" | jq '[.apps.http.servers[].routes[]] | length')" = "1" ] || _die "$which: site 라우트가 하나가 아니다(판정 불가)" 2
done

SUB='[.apps.http.servers[].routes[0].handle[] | select(.handler == "subroute") | .routes[]]'
HAS_HSTS='any(.. | objects; has("Strict-Transport-Security"))'
local_sub="$(printf '%s' "$local_json" | jq -c "$SUB")"
prod_sub_all="$(printf '%s' "$prod_json" | jq -c "$SUB")"
prod_sub="$(printf '%s' "$prod_sub_all" | jq -c "[.[] | .handle |= map(select(($HAS_HSTS) | not))]")"
[ "$(printf '%s' "$local_sub" | jq length)" -ge 4 ] || _die "로컬 하위 라우트가 4 개 미만이다 — 공유 본문을 읽지 못했다(판정 불가)" 2

if [ "$(printf '%s' "$local_sub" | jq -S .)" != "$(printf '%s' "$prod_sub" | jq -S .)" ]; then
  fail "공유 본문이 두 site 에서 다르다(운영의 HSTS 라우트를 뺀 하위 라우트 비교)"
fi
# 로그 — 서버 단위 로거 설정과 전역 로그 설정이 같다.
for sel in '.apps.http.servers[].logs' '.logging.logs'; do
  [ "$(printf '%s' "$local_json" | jq -S "$sel")" = "$(printf '%s' "$prod_json" | jq -S "$sel")" ] \
    || fail "접근 로그 설정이 두 site 에서 다르다: $sel"
done
[ "$(printf '%s' "$local_json" | jq '.logging.logs | length')" -ge 1 ] || fail "로그 설정이 비었다 — 헤더 삭제 필터가 없다"

# 운영의 HSTS 는 정확히 한 핸들러 · 값 하나.
hsts_handlers="$(printf '%s' "$prod_sub_all" | jq "[.[] | .handle[] | select($HAS_HSTS)] | length")"
[ "$hsts_handlers" = "1" ] || fail "운영 HSTS 핸들러가 정확히 1 개가 아니다: $hsts_handlers"
hsts_vals="$(printf '%s' "$prod_json" | jq -c '[.. | objects | select(has("Strict-Transport-Security")) | .["Strict-Transport-Security"]] | flatten')"
[ "$hsts_vals" = "[\"${EXPECT_HSTS}\"]" ] || fail "운영 HSTS 값이 기대와 다르다: $hsts_vals"
[ "$(printf '%s' "$local_json" | jq '[.. | objects | select(has("Strict-Transport-Security"))] | length')" = "0" ] \
  || fail "로컬 site 에 HSTS 가 있다"

# 운영 — TLS 를 다루지 않는다 · h1 · site 이름이 도메인 하나.
[ "$(printf '%s' "$prod_json" | jq '.apps.tls')" = "null" ] || fail "운영 site 에 TLS 앱이 있다"
[ "$(printf '%s' "$prod_json" | jq -c '.apps.http.servers[].automatic_https.disable')" = "true" ] || fail "운영 site 의 자동 HTTPS 가 꺼져 있지 않다"
[ "$(printf '%s' "$prod_json" | jq -c '.apps.http.servers[].protocols')" = '["h1"]' ] || fail "운영 site 의 프로토콜이 h1 하나가 아니다"
[ "$(printf '%s' "$prod_json" | jq -c '[.apps.http.servers[].routes[0].match[].host[]]')" = "[\"${TEST_DOMAIN}\"]" ] \
  || fail "운영 site 의 Host 일치가 도메인 하나가 아니다"
[ "$(printf '%s' "$prod_json" | jq -c '.apps.http.servers[].listen')" = "[\":${TEST_PORT}\"]" ] || fail "운영 site 의 리스너가 기대와 다르다"
# 로컬 — tls internal.
[ "$(printf '%s' "$local_json" | jq -c '[.apps.tls.automation.policies[]?.issuers[]?.module] | unique')" = '["internal"]' ] \
  || fail "로컬 site 의 발급자가 internal 하나가 아니다"
# 도메인이 비면 모든 Host 를 받는 쪽으로 열리지 않는다 — 기본값이 실재할 수 없는 이름이다.
empty_json="$(docker run --rm --security-opt no-new-privileges -e BIDVECTOR_PROXY_HTTPS_PORT="$TEST_PORT" \
  -e BIDVECTOR_PROXY_APP_UPSTREAM=app:1 -e BIDVECTOR_PROXY_WEB_UPSTREAM=web:2 \
  --entrypoint caddy "$PROXY_IMAGE" adapt --config /etc/caddy/Caddyfile.prod --adapter caddyfile 2>/dev/null)" \
  || _die "도메인 없는 운영 site 를 adapt 하지 못했다" 2
[ "$(printf '%s' "$empty_json" | jq -c '[.apps.http.servers[].routes[0].match[]?.host[]?]')" = '["domain-not-set.invalid"]' ] \
  || fail "도메인이 비었을 때 운영 site 가 실재할 수 없는 이름으로 닫히지 않는다"

echo "== (S3) 운영 compose 해석"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/prod-proxy-sites-check.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
# 해석만 하므로 값은 형식만 맞는 자리값이다(자격 값 자리에도 의미 없는 문자열 — 어디에도 쓰이지 않는다).
envf="$WORK/prod.env"
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  key="${line%%=*}"
  case "$key" in
    *_PORT) val=28000 ;;
    *_MEM_LIMIT) val=64m ;;
    BIDVECTOR_PROXY_DOMAIN) val="$TEST_DOMAIN" ;;
    BIDVECTOR_RELEASE_TAG) val=check-only ;;
    *) val=placeholder ;;
  esac
  [ "$key" = BIDVECTOR_PROXY_HTTPS_PORT ] && val="$TEST_PORT"
  printf '%s=%s\n' "$key" "$val" >> "$envf"
done < docker/prod.env.template
cfg="$("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$envf" config --format json 2>/dev/null)" || _die "운영 compose 를 해석하지 못했다" 2
cfg_all="$("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$envf" --profile ml config --format json 2>/dev/null)" || _die "운영 compose(ml profile)를 해석하지 못했다" 2

name="$(printf '%s' "$cfg" | jq -r .name)"
[ "$name" = "$EXPECT_PROJECT" ] || fail "프로젝트 이름이 $EXPECT_PROJECT 가 아니다: $name"
[ "$name" != "$DEV_PROJECT" ] || fail "운영 프로젝트 이름이 개발 스택과 같다 — 개발 레인의 down -v 가 운영 볼륨을 지운다"
services="$(printf '%s' "$cfg" | jq -r '.services | keys | sort | join(",")')"
[ "$services" = "app,postgres,proxy,web" ] || fail "기본 기동 서비스 집합이 기대와 다르다: $services"
[ "$(printf '%s' "$cfg_all" | jq -r '.services["ml-serving"].profiles | join(",")')" = "ml" ] || fail "ml-serving 이 ml profile 뒤에 있지 않다"
published="$(printf '%s' "$cfg_all" | jq -r '[.services | to_entries[] | select((.value.ports // []) | length > 0) | .key] | sort | join(",")')"
[ "$published" = "proxy" ] || fail "host 포트를 여는 서비스가 proxy 하나가 아니다: [$published]"
ports="$(printf '%s' "$cfg" | jq -c '[.services.proxy.ports[] | {host_ip, published, target}]')"
[ "$ports" = "[{\"host_ip\":\"127.0.0.1\",\"published\":\"${TEST_PORT}\",\"target\":${TEST_PORT}}]" ] \
  || fail "proxy publish 가 루프백 하나가 아니다: $ports"
[ "$(printf '%s' "$cfg" | jq -c '.services.proxy.entrypoint')" = '["caddy","run","--config","/etc/caddy/Caddyfile.prod","--adapter","caddyfile"]' ] \
  || fail "proxy 가 운영 site 를 고르지 않는다"
nolimit="$(printf '%s' "$cfg_all" | jq -r '[.services | to_entries[] | select(.value.mem_limit == null) | .key] | join(",")')"
[ -z "$nolimit" ] || fail "mem_limit 이 없는 서비스: $nolimit"
local_tags="$(printf '%s' "$cfg_all" | jq -r '[.services[] | .image | select(endswith(":local"))] | join(",")')"
[ -z "$local_tags" ] || fail "운영 프로필이 :local 태그를 쓴다: $local_tags"
nonrestart="$(printf '%s' "$cfg_all" | jq -r '[.services | to_entries[] | select(.value.restart != "unless-stopped") | .key] | join(",")')"
[ -z "$nonrestart" ] || fail "재시작 정책이 없는 서비스: $nonrestart"
# 7F-local 망 분리를 물려받는다 — web 은 edge 하나, proxy 만 둘 다.
[ "$(printf '%s' "$cfg" | jq -r '.services.web.networks | keys | join(",")')" = "edge" ] || fail "web 이 edge 하나에만 붙어 있지 않다"
[ "$(printf '%s' "$cfg" | jq -r '.services.proxy.networks | keys | sort | join(",")')" = "default,edge" ] || fail "proxy 가 두 망에 붙어 있지 않다"

if [ "$FAILS" -gt 0 ]; then
  echo "prod-proxy-sites-check: FAIL ${FAILS}" >&2
  exit 1
fi
echo "prod-proxy-sites-check: OK — 하위 라우트 $(printf '%s' "$local_sub" | jq length)개 등식 · 운영 compose 단언"
