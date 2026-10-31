#!/usr/bin/env bash
# M7/7F 본편 — 운영 스택의 **헬스**(읽기 전용, D-7F2-2). 반입 사후 확인과 운영자 점검이 같은 스크립트다.
#
#     ./tools/prod-health.sh --env-file <운영 env> [--project <이름>] [--public]
#
#   기본: compose 서비스(app·postgres·web·proxy)가 모두 `(healthy)` · 루프백 proxy 에 운영 도메인 Host 로
#         셸 200 · `/api/strategy` 무인증 401 · `/actuator/health` 404 · HSTS 값.
#   --public: host nginx 를 지나는 공개 경로도 잰다 — 127.0.0.1:443 에 SNI 로 같은 넷 + 인증서 남은
#         일수(`tools/prod-cert-check.sh`). DNS 전환·certbot 발급 뒤에만 의미가 있다.
#
# env 파일에서는 **도메인과 루프백 포트 두 키만** 읽는다(자격 값은 읽지 않는다).
#
# 종료 코드: 0 · 1 하나라도 실패 · 2 판정 불가.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

EXPECT_SERVICES="app,postgres,proxy,web"
EXPECT_HSTS="max-age=86400"

_die() { echo "prod-health: $1" >&2; exit "${2:-1}"; }
FAILS=0
fail() { echo "  FAIL: $1" >&2; FAILS=$((FAILS + 1)); }

ENVF="" PROJECT="" PUBLIC=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --public) PUBLIC=1; shift ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
[ -r "$ENVF" ] || _die "사용법: $0 --env-file <운영 env> [--project <이름>] [--public]" 2
for t in docker curl jq; do command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2; done

_env_value() { grep -E "^$1=" "$ENVF" | tail -n1 | cut -d= -f2-; }
DOMAIN="$(_env_value BIDVECTOR_PROXY_DOMAIN)"
PORT="$(_env_value BIDVECTOR_PROXY_HTTPS_PORT)"
[ -n "$DOMAIN" ] && [[ "$PORT" =~ ^[0-9]+$ ]] || _die "env 파일에서 도메인·포트를 읽지 못했다" 2

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")

echo "== 컨테이너"
# `ps` 는 env 보간이 필요 없지만 compose 가 파일을 해석하므로 release 태그 자리가 있어야 한다 — 현재
# 컨테이너의 상태만 보므로 값은 무엇이든 같다.
ps_json="$(BIDVECTOR_RELEASE_TAG="${BIDVECTOR_RELEASE_TAG:-health-probe}" "${COMPOSE[@]}" ps --format json 2>/dev/null)" \
  || _die "compose ps 실패(판정 불가)" 2
ps_json="$(printf '%s' "$ps_json" | jq -s 'if (.[0] | type) == "array" then .[0] else . end')"
running="$(printf '%s' "$ps_json" | jq -r '[.[] | select(.State == "running") | .Service] | sort | join(",")')"
[ "$running" = "$EXPECT_SERVICES" ] || fail "실행 중 서비스가 기대와 다르다: [$running]"
unhealthy="$(printf '%s' "$ps_json" | jq -r '[.[] | select(.Health != "healthy") | .Service] | join(",")')"
[ -z "$unhealthy" ] || fail "(healthy) 가 아닌 서비스: $unhealthy"

_probe() {
  # $1 기준 URL(끝 / 없음) · $2 경로 · 나머지 curl 인자
  local base="$1" path="$2"; shift 2
  curl -s --max-time 5 "$@" "${base}${path}"
}
_check_site() {
  local what="$1" base="$2"; shift 2
  local code body hsts
  body="$(_probe "$base" / "$@" || true)"
  case "$body" in *'id="root"'*) ;; *) fail "$what: / 가 web 셸이 아니다" ;; esac
  code="$(_probe "$base" /api/strategy "$@" -o /dev/null -w '%{http_code}' || true)"
  [ "$code" = "401" ] || fail "$what: 무인증 /api/strategy 가 401 이 아니다: $code"
  code="$(_probe "$base" /actuator/health "$@" -o /dev/null -w '%{http_code}' || true)"
  [ "$code" = "404" ] || fail "$what: /actuator/health 가 404 가 아니다: $code"
  hsts="$(_probe "$base" / "$@" -D - -o /dev/null | tr -d '\r' | grep -i '^Strict-Transport-Security:' | sed 's/^[^:]*: *//' || true)"
  [ "$hsts" = "$EXPECT_HSTS" ] || fail "$what: HSTS 값이 기대와 다르다: '$hsts'"
}

echo "== 루프백 proxy (127.0.0.1:${PORT})"
_check_site "루프백" "http://${DOMAIN}:${PORT}" --resolve "${DOMAIN}:${PORT}:127.0.0.1"

if [ "$PUBLIC" -eq 1 ]; then
  echo "== 공개 경로 (host nginx 127.0.0.1:443)"
  _check_site "공개" "https://${DOMAIN}" --resolve "${DOMAIN}:443:127.0.0.1"
  ./tools/prod-cert-check.sh "$DOMAIN" || fail "인증서 검사 실패"
fi

if [ "$FAILS" -gt 0 ]; then
  echo "prod-health: FAIL ${FAILS}" >&2
  exit 1
fi
echo "prod-health: OK"
