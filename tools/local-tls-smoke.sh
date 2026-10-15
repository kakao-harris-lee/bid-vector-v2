#!/usr/bin/env bash
# M7/7F-local — 로컬 패키징 스모크(scope.md 산출물 4). 로컬 프로필을 올리고 **프록시의
# https 하나로만** 다음을 재고 내린다:
#
#   · web 셸이 뜬다(빌드된 번들 · SPA fallback)                      · 산출물 1
#   · `/api/**` 가 app 에 닿는다(무인증 401 · 인증 200)               · 산출물 2
#   · 설계 검토 우회 ①~⑤ 가 각각 막힌다                               · scope.md (2)
#   · 자격 값이 프록시 접근 로그에 남지 않는다                         · 아래 (M)
#
# **공허하지 않게** 짠다 — 막힘을 재는 단언마다 같은 표지의 **양성 대조**를 붙인다(내부망에서는
# 같은 요청이 실제로 통한다). 그러지 않으면 상류가 통째로 죽어도 「막혔다」로 초록이 된다.
#
# 실행:
#
#     ./tools/local-tls-smoke.sh
#
# 전제 하나 — `bidvector/app:local` 이미지가 있어야 한다(이 스크립트는 Gradle 을 돌리지
# 않는다). 없으면 그 자리에서 무엇을 먼저 돌릴지 말하고 멈춘다. web·proxy 이미지는 이
# 스크립트가 compose 의 `build` 정의로 만든다 — 빌드 명령이 두 자리(여기와 ci.yml)로 갈리지
# 않게 **compose 가 단일 출처**다.
#
# 이 스크립트는 자기 스택을 올리고 **반드시 내린다**(`down -v`, 종료 경로 전부). 그래서
# 시작할 때 같은 프로젝트의 컨테이너가 이미 있으면 거부한다 — 남의 스택을 조용히 지우지
# 않는다. 다른 프로젝트(예: 개발용 DB 컨테이너)는 라벨이 달라 애초에 대상이 아니다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

COMPOSE=(docker compose -f docker/compose.yaml -f docker/compose.local-tls.yaml)

# ---- 값: 있으면 그대로 쓰고 없으면 이 자리에서 만든다 ----
# **이미 설정된 값을 덮지 않는다**: CI 는 자격 값을 job 안에서 만들어 `::add-mask::` 로
# 가려 두었고, 여기서 새 값을 지으면 그 가림 밖의 값이 하나 생긴다. 포트 기본값의 자리는
# 이 스크립트 하나다(Caddyfile·compose 는 `${VAR:?}` 로 요구만 한다).
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
# 도메인은 **여기서 반드시 export 한다** — compose 는 이 변수를 통과시킬 뿐이라(기본값의
# 정본은 `docker/proxy.Caddyfile`), 아래 curl 이 쓰는 이름과 컨테이너가 받는 이름이 한
# 실행 안에서 같은 값임이 이 한 줄로 보장된다.
export BIDVECTOR_PROXY_DOMAIN="${BIDVECTOR_PROXY_DOMAIN:-localhost}"

DOMAIN="$BIDVECTOR_PROXY_DOMAIN"
PORT="$BIDVECTOR_PROXY_HTTPS_PORT"
BASE="https://${DOMAIN}:${PORT}"

TMP="$(mktemp -d)"
CA="$TMP/root.crt"

fail() {
  echo "스모크 실패: $1" >&2
  # 실패한 자리에서 필요한 것은 각 서비스의 **마지막 줄들**이다(전문을 찍지 않는다 —
  # 공개 CI 로그로 가는 경로이고, 앱 로그는 뒤 편집에서 값이 실릴 수 있는 자리다).
  for svc in proxy web app; do
    echo "---- $svc (tail) ----" >&2
    "${COMPOSE[@]}" logs --tail 20 "$svc" >&2 2>/dev/null || true
  done
  exit 1
}

_step() { echo ""; echo "-- $1 --"; }

_cleanup() {
  local rc=$?
  echo ""
  echo "-- 정리: down -v(볼륨까지) --"
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$TMP"
  # **잔여 0 을 종료 경로에서 센다**(성공·실패 무관). `down -v` 가 지우는 것은 이 프로젝트
  # 라벨이 붙은 것뿐이다 — 다른 프로젝트의 볼륨은 애초에 대상이 아니다(6B-2 이름 충돌 교훈).
  local left_c left_v left_n
  # PROJECT 가 없으면(preflight 가 그 전에 멈춘 경우) 셈을 돌리지 않는다 — 빈 값으로 필터를 걸면
  # 무엇을 세는지가 불분명해진다.
  [ -n "${PROJECT:-}" ] || return "$rc"
  left_c="$(docker ps -aq --filter "label=com.docker.compose.project=${PROJECT}" 2>/dev/null || true)"
  left_v="$(docker volume ls -q --filter "label=com.docker.compose.project=${PROJECT}" 2>/dev/null || true)"
  left_n="$(docker network ls -q --filter "label=com.docker.compose.project=${PROJECT}" 2>/dev/null || true)"
  if [ -n "$left_c$left_v$left_n" ]; then
    echo "정리 뒤 잔여가 있다 — 컨테이너[$(printf '%s' "$left_c" | tr '\n' ' ')] 볼륨[$(printf '%s' "$left_v" | tr '\n' ' ')] 네트워크[$(printf '%s' "$left_n" | tr '\n' ' ')]" >&2
    exit 1
  fi
  echo "잔여 0 — 컨테이너·볼륨·네트워크(프로젝트 ${PROJECT})"
  return "$rc"
}

# ---- preflight ----
_step "preflight: 도구·이미지·포트·프로젝트"
for t in docker curl jq openssl; do
  command -v "$t" >/dev/null 2>&1 || { echo "$t 가 필요하다" >&2; exit 2; }
done

# 프로젝트 이름은 **해석된 설정에서 읽는다** — 리터럴로 적으면 compose 파일의 `name:` 과
# 어긋날 자리가 하나 생긴다.
PROJECT="$("${COMPOSE[@]}" config --format json | jq -r '.name')"
[ -n "$PROJECT" ] && [ "$PROJECT" != "null" ] || { echo "compose 프로젝트 이름을 읽지 못했다" >&2; exit 2; }
echo "프로젝트: $PROJECT"

docker image inspect bidvector/app:local >/dev/null 2>&1 || {
  echo "bidvector/app:local 이미지가 없다 — 먼저 만든다(ci.yml container job 의 S-21a·S-21b 와 같은 명령):" >&2
  echo "  ./gradlew --no-daemon :app:bootJar" >&2
  echo "  docker build -f docker/app.Dockerfile -t bidvector/app:local ." >&2
  exit 2
}

if [ -n "$(docker ps -aq --filter "label=com.docker.compose.project=${PROJECT}" 2>/dev/null || true)" ]; then
  echo "프로젝트 ${PROJECT} 의 컨테이너가 이미 있다 — 이 스모크는 올리고 내리므로 먼저 치운다:" >&2
  echo "  docker compose -f docker/compose.yaml -f docker/compose.local-tls.yaml down -v" >&2
  exit 2
fi

_port_listening() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }
# 프록시 포트는 publish 대상이고, 나머지 셋은 **「host 에서 닿지 않음」을 재는 대상**이라
# 셋 다 비어 있어야 판정이 정직하다(남이 쥔 포트를 우리 서비스로 오인하지 않는다).
for p in "$PORT" "$BIDVECTOR_APP_PORT" "$BIDVECTOR_APP_MANAGEMENT_PORT" "$BIDVECTOR_WEB_PORT"; do
  if _port_listening "$p"; then
    echo "127.0.0.1:$p 에 이미 무엇이 떠 있다 — 다른 포트를 주고 다시 돈다(BIDVECTOR_PROXY_HTTPS_PORT·BIDVECTOR_APP_PORT·BIDVECTOR_APP_MANAGEMENT_PORT·BIDVECTOR_WEB_PORT)" >&2
    exit 2
  fi
done

trap _cleanup EXIT

# ---- 빌드·기동 ----
_step "빌드: web·proxy 이미지(compose 의 build 정의)"
"${COMPOSE[@]}" build web proxy

_step "기동: proxy(의존으로 app·postgres·web 이 함께 뜬다)"
"${COMPOSE[@]}" up -d proxy

_step "healthy 수렴 대기: app·web·proxy"
for _ in $(seq 1 40); do
  ps_out="$("${COMPOSE[@]}" ps --format '{{.Service}}: {{.Status}}')"
  if echo "$ps_out" | grep -q "app:.*healthy" \
    && echo "$ps_out" | grep -q "web:.*healthy" \
    && echo "$ps_out" | grep -q "proxy:.*healthy"; then
    echo "$ps_out"
    break
  fi
  sleep 3
done
ps_out="$("${COMPOSE[@]}" ps --format '{{.Service}}: {{.Status}}')"
echo "$ps_out" | grep -q "proxy:.*healthy" || { echo "$ps_out" >&2; fail "app·web·proxy 가 제한 시간 안에 healthy 로 수렴하지 않았다"; }

# 로컬 CA 의 뿌리 인증서를 컨테이너에서 꺼낸다 — **실행 시 발급된 것**이고 저장소에는 없다.
# 아래 모든 https 요청이 이 체인으로 검증된다(`-k` 를 쓰지 않는다 — 그러면 TLS 가 서 있는지
# 아닌지를 구분하지 못한다).
_step "로컬 CA 뿌리 인증서 반출(프록시가 실행 시 발급한 것)"
# `|| fail` 로 받는다 — 그러지 않으면 `set -e` 가 이 줄에서 즉사해 **무엇이 없었는지**가
# 문면에 남지 않는다(실측: TLS 를 뺀 변이에서 cat 의 오류 한 줄만 남았다).
"${COMPOSE[@]}" exec -T proxy cat /data/caddy/pki/authorities/local/root.crt > "$CA" \
  || fail "로컬 CA 뿌리 인증서를 꺼내지 못했다 — 이 프로필에서 TLS 가 서지 않았다"
[ -s "$CA" ] || fail "로컬 CA 뿌리 인증서가 비었다"
openssl x509 -in "$CA" -noout -subject >/dev/null || fail "반출한 파일이 인증서가 아니다"

# ---- helper ----
# 공통 축: 신뢰 체인 + 이름 고정(`--resolve`). 루프백에만 publish 하므로 이름이 `::1` 로
# 먼저 풀리는 장비에서도 같은 주소로 간다(web job 의 vite host 리터럴 IP 와 같은 사유).
_c()      { curl -sS --cacert "$CA" --resolve "${DOMAIN}:${PORT}:127.0.0.1" "$@"; }
_code()   { local o; o="$(_c -o /dev/null -w '%{http_code}' "$BASE$1")" || o="transport-error"; printf '%s' "$o"; }
_body()   { local o; o="$(_c "$BASE$1")" || o=""; printf '%s' "$o"; }
_raw_code() { local o; o="$(_c --path-as-is -o /dev/null -w '%{http_code}' "$BASE$1")" || o="transport-error"; printf '%s' "$o"; }
_raw_body() { local o; o="$(_c --path-as-is "$BASE$1")" || o=""; printf '%s' "$o"; }
_hdrs()   { local o; o="$(_c -D - -o /dev/null "$BASE$1")" || o=""; printf '%s' "$o"; }
# 자격 값은 **argv 를 지나지 않는다**(ci.yml S-23b 와 같은 형태 — `printf` 는 셸 내장이라
# 별 프로세스를 만들지 않고 값은 파이프로만 흐른다).
_auth_code() {
  local o
  o="$(printf 'header = "X-Operator-Credential: %s"\n' "$BIDVECTOR_APP_OPERATOR_CREDENTIAL" \
    | _c --config - -o /dev/null -w '%{http_code}' "$BASE$1")" || o="transport-error"
  printf '%s' "$o"
}
# 본문 + 마지막 줄 코드. **인증까지 보는 이유**: 무인증 응답만 보면 「막은 것이 경로 규칙인가
# 인증 필터인가」를 가리지 못한다 — 자격을 가진 운영자도 관리 표면에 닿지 못해야 한다.
_auth_body_code() {
  local o
  o="$(printf 'header = "X-Operator-Credential: %s"\n' "$BIDVECTOR_APP_OPERATOR_CREDENTIAL" \
    | _c --config - --path-as-is -w '\n%{http_code}' "$BASE$1")" || o=$'\ntransport-error'
  printf '%s' "$o"
}
_tail_code() { printf '%s' "$1" | tail -n1; }
_drop_code() { printf '%s' "$1" | sed '$d'; }
# 평문: **같은 주소의 같은 포트**에 http 로 던진다(`--resolve` 로 주소를 고정한다 — 이름이 `::1` 로
# 먼저 풀리는 장비에서 「연결 자체가 안 됐다」가 「평문이 거부됐다」로 읽히면 그 단언은 공허해진다).
# 남는 공허 축 하나는 「리스너가 죽어서 전송 오류」인데, 그것은 같은 실행의 (B)·(C) 가 https 로
# 200/401 을 받은 뒤이므로 성립하지 않는다 — 순서가 그 역할을 한다.
_plain_code() { local o; o="$(curl -sS --max-time 5 --resolve "${DOMAIN}:${PORT}:127.0.0.1" -o /dev/null -w '%{http_code}' "http://${DOMAIN}:${PORT}$1" 2>/dev/null)" || o="transport-error"; printf '%s' "$o"; }
_plain_body() { local o; o="$(curl -sS --max-time 5 --resolve "${DOMAIN}:${PORT}:127.0.0.1" "http://${DOMAIN}:${PORT}$1" 2>/dev/null)" || o=""; printf '%s' "$o"; }

# 관리 표면의 표지. 이 문자열이 프록시를 지난 어떤 응답에도 없어야 한다.
MGMT_MARKER='"status":"UP"'
_has_mgmt_marker() { case "$1" in *'"status":"UP"'*|*'"status": "UP"'*) return 0 ;; esac; return 1; }

# ---- (A) publish 표면: 프록시 하나, 루프백 ----
_step "(A) 우회 ① — publish 하는 서비스가 프록시 하나이고 루프백에만 연다"
ports_json="$("${COMPOSE[@]}" config --format json)"
publishers="$(printf '%s' "$ports_json" | jq -r '[.services | to_entries[] | select((.value.ports // []) | length > 0) | .key] | sort | join(",")')"
[ "$publishers" = "proxy" ] || fail "host 포트를 여는 서비스가 프록시 하나가 아니다: [$publishers]"
pub_count="$(printf '%s' "$ports_json" | jq -r '.services.proxy.ports | length')"
[ "$pub_count" = "1" ] || fail "프록시가 여는 host 포트가 하나가 아니다: $pub_count"
pub_host_ip="$(printf '%s' "$ports_json" | jq -r '.services.proxy.ports[0].host_ip')"
[ "$pub_host_ip" = "127.0.0.1" ] || fail "프록시 publish 가 루프백이 아니다: $pub_host_ip"
# **해석된 설정에 관리 포트가 상류로 실려 있지 않다**(우회 ② 의 구조 축). app 서비스의
# `MANAGEMENT_SERVER_PORT` 선언 하나만 그 값을 든다.
mgmt_refs="$(printf '%s' "$ports_json" \
  | jq -r --arg p "$BIDVECTOR_APP_MANAGEMENT_PORT" \
    '[.services | to_entries[] | . as $s | (.value.environment // {}) | to_entries[] | select((.value|tostring) | contains($p)) | "\($s.key).\(.key)"] | sort | join(",")')"
[ "$mgmt_refs" = "app.MANAGEMENT_SERVER_PORT" ] \
  || fail "관리 포트 값을 든 설정 자리가 app.MANAGEMENT_SERVER_PORT 하나가 아니다: [$mgmt_refs]"

_step "(A3) 우회 ② — 프록시의 상류 집합이 web·app API 포트 **둘뿐**이다(적응된 설정에서)"
# 설정 파일을 grep 하지 않는다 — 새 `handle` 블록이 관리 포트를 리터럴로 적는 형태는 compose
# env 검사(위 (A))를 지나간다. **Caddy 자신이 적응한 JSON**에서 상류(`dial`) 전부를 모아
# 집합 등식을 본다: 경로를 몇 개 더 열어도, 리터럴로 적어도, 상류가 늘면 여기서 붉어진다.
adapted="$("${COMPOSE[@]}" exec -T proxy caddy adapt --config /etc/caddy/Caddyfile --adapter caddyfile 2>/dev/null)" \
  || fail "프록시 설정을 적응하지 못했다(상류 집합을 재지 못한다)"
dials="$(printf '%s' "$adapted" | jq -r '[.. | objects | select(has("dial")) | .dial] | unique | join(",")')"
expected_dials="$(printf '%s\n%s\n' "app:${BIDVECTOR_APP_PORT}" "web:${BIDVECTOR_WEB_PORT}" | sort | paste -sd, -)"
[ "$dials" = "$expected_dials" ] || fail "프록시 상류 집합이 기대와 다르다: [$dials] 기대 [$expected_dials]"
echo "  상류 집합 = [$dials]"

_step "(A2) 우회 ① — host 에서 app·web 포트에 직접 닿지 않는다(양성 대조: 내부망에서는 닿는다)"
for p in "$BIDVECTOR_APP_PORT" "$BIDVECTOR_APP_MANAGEMENT_PORT" "$BIDVECTOR_WEB_PORT"; do
  if _port_listening "$p"; then
    fail "host 의 127.0.0.1:$p 에 연결이 됐다 — 내부 서비스가 host 에 노출됐다"
  fi
done
inner_app="$("${COMPOSE[@]}" exec -T proxy curl -sS -o /dev/null -w '%{http_code}' "http://app:${BIDVECTOR_APP_PORT}/api/strategy")" \
  || fail "내부망에서 app API 포트에 닿지 못했다(양성 대조 실패 — 위 거부 단언이 공허하다)"
[ "$inner_app" = "401" ] || fail "내부망 app API 포트가 401 이 아니다: $inner_app(양성 대조)"
inner_web="$("${COMPOSE[@]}" exec -T proxy curl -sS -o /dev/null -w '%{http_code}' "http://web:${BIDVECTOR_WEB_PORT}/")" \
  || fail "내부망에서 web 에 닿지 못했다(양성 대조 실패)"
[ "$inner_web" = "200" ] || fail "내부망 web 이 200 이 아니다: $inner_web(양성 대조)"

# ---- (B) web 셸 ----
_step "(B) web 셸이 프록시 https 로 뜬다 — 셸 · 번들 자산 · SPA fallback"
code="$(_code /)"
[ "$code" = "200" ] || fail "https / 가 200 이 아니다: $code"
shell="$(_body /)"
case "$shell" in
  *'id="root"'*) ;;
  *) fail "https / 응답이 web 셸이 아니다(마운트 지점 없음)" ;;
esac
asset="$(printf '%s' "$shell" | sed -n 's|.*<script[^>]*src="\([^"]*\)".*|\1|p' | head -n1)"
[ -n "$asset" ] && [ "${asset#/}" != "$asset" ] || fail "셸이 참조하는 번들 경로를 읽지 못했다: [$asset]"
code="$(_code "$asset")"
[ "$code" = "200" ] || fail "셸이 참조하는 번들($asset)이 200 이 아니다: $code"
ctype="$(_c -o /dev/null -w '%{content_type}' "$BASE$asset")" || ctype=""
case "$ctype" in *javascript*) ;; *) fail "번들의 content-type 이 javascript 가 아니다: [$ctype]" ;; esac
# 자리 표 밖 주소도 셸로 돌아온다(react-router 의 클라이언트 라우트).
code="$(_code /no-such-page)"
[ "$code" = "200" ] || fail "SPA fallback 이 200 이 아니다: $code"
case "$(_body /no-such-page)" in
  *'id="root"'*) ;;
  *) fail "SPA fallback 응답이 셸이 아니다" ;;
esac

# ---- (C) /api 가 app 에 닿는다 ----
_step "(C) /api/** 가 app 에 닿는다 — 무인증 401 · 인증 200"
code="$(_code /api/strategy)"
[ "$code" = "401" ] || fail "무인증 /api/strategy 가 401 이 아니다: $code"
code="$(_auth_code /api/strategy)"
[ "$code" = "200" ] || fail "인증 /api/strategy 가 200 이 아니다: $code"

# ---- (D) 보안 헤더 ----
_step "(D) 보안 헤더 — 셋 · CSP · HSTS 부재(로컬 선택)"
hdrs="$(_hdrs /)"
[ -n "$hdrs" ] || fail "응답 헤더를 읽지 못했다"
_hdr_has() { printf '%s' "$hdrs" | grep -qi "^$1:" ; }
_hdr_val() { printf '%s' "$hdrs" | grep -i "^$1:" | head -n1 | sed 's/^[^:]*: *//' | tr -d '\r'; }
for h in X-Content-Type-Options Referrer-Policy X-Frame-Options Content-Security-Policy; do
  _hdr_has "$h" || fail "응답에 $h 헤더가 없다"
done
[ "$(_hdr_val X-Content-Type-Options)" = "nosniff" ] || fail "X-Content-Type-Options 값이 nosniff 가 아니다"
case "$(_hdr_val Content-Security-Policy)" in
  *"default-src 'none'"*) ;;
  *) fail "CSP 가 default-src 'none' 으로 시작하지 않는다" ;;
esac
# 로컬 TLS 에서는 HSTS 를 보내지 않는다(`proxy.Caddyfile` 의 사유 — host 단위로 걸려 다른
# localhost 평문 개발 서버까지 막는다). 실수로 켜지면 여기서 붉어진다.
if _hdr_has Strict-Transport-Security; then
  fail "로컬 프로필 응답에 HSTS 가 실렸다 — localhost 에 HSTS 를 보내지 않는다"
fi

# ---- (E) 우회 ② — 관리 경로 ----
_step "(E) 우회 ② — 관리 경로가 프록시를 지나지 못한다(양성 대조: 내부 관리 포트는 UP 을 낸다)"
# 양성 대조 먼저 — 이 표지가 실재함을 확인하고 나서 「없음」을 단언한다.
mgmt_body="$("${COMPOSE[@]}" exec -T app curl -sS "http://127.0.0.1:${BIDVECTOR_APP_MANAGEMENT_PORT}/actuator/health")" \
  || fail "내부 관리 포트에서 health 를 읽지 못했다(양성 대조 실패 — 아래 단언이 공허하다)"
_has_mgmt_marker "$mgmt_body" || fail "내부 관리 포트 health 에 표지($MGMT_MARKER)가 없다(양성 대조 실패)"
for path in /actuator /actuator/health /actuator/health/readiness /actuator/env /actuator/metrics; do
  code="$(_code "$path")"
  [ "$code" = "404" ] || fail "프록시의 $path 가 404 가 아니다: $code"
  body="$(_body "$path")"
  ! _has_mgmt_marker "$body" || fail "프록시의 $path 응답에 관리 표지가 실렸다"
  # 자격을 가진 요청도 같은 404 다 — 막는 것이 인증이 아니라 **경로 규칙과 상류 집합**이다.
  out="$(_auth_body_code "$path")"
  [ "$(_tail_code "$out")" = "404" ] || fail "인증된 $path 가 404 가 아니다: $(_tail_code "$out")"
  ! _has_mgmt_marker "$(_drop_code "$out")" || fail "인증된 $path 응답에 관리 표지가 실렸다"
done
# `/api/actuator/**` 는 app 의 **API 포트**로 간다 — 그 포트에 관리 표면이 없다는 것은 기존
# 컨테이너 스모크(S-23b)가 재는 사실이고, 여기서는 무인증·인증 양쪽에서 표지가 없음을 본다.
for path in /api/actuator/health /api/actuator/env; do
  body="$(_body "$path")"
  ! _has_mgmt_marker "$body" || fail "프록시의 $path 응답에 관리 표지가 실렸다"
  out="$(_auth_body_code "$path")"
  ! _has_mgmt_marker "$(_drop_code "$out")" || fail "인증된 $path 응답에 관리 표지가 실렸다"
done

# ---- (F) 우회 ③ — 경로 정규화 ----
_step "(F) 우회 ③ — 경로 정규화 우회가 관리 표면을 열지 못한다"
# `--path-as-is` 로 보낸다 — curl 이 먼저 정리해 버리면 프록시가 무엇을 받는지 재지 못한다.
for path in \
  '/api/../actuator/health' \
  '/api/./../actuator/health' \
  '/api/%2e%2e/actuator/health' \
  '/api/..%2factuator/health' \
  '/api/..;/actuator/health' \
  '//actuator/health' \
  '/%2e%2e/actuator/env' \
  '/api/%2e%2e%2factuator/health' ; do
  code="$(_raw_code "$path")"
  body="$(_raw_body "$path")"
  # **자격을 가진 요청까지 본다** — 401 로 끊긴 경로가 「인증만 막고 있었다」면 운영자
  # 자격으로는 열렸다는 뜻이 되므로, 같은 경로를 자격과 함께 다시 던져 표지 부재를 잰다.
  auth_out="$(_auth_body_code "$path")"
  auth_code="$(_tail_code "$auth_out")"
  ! _has_mgmt_marker "$body" || fail "정규화 우회 $path 가 관리 표지를 냈다(code=$code)"
  ! _has_mgmt_marker "$(_drop_code "$auth_out")" \
    || fail "인증된 정규화 우회 $path 가 관리 표지를 냈다(code=$auth_code)"
  for c in "$code" "$auth_code"; do
    case "$c" in
      200)
        # 200 이면 셸이어야 한다(web 정적) — app 이나 관리 표면의 200 이면 안 된다.
        case "$body$(_drop_code "$auth_out")" in
          *'id="root"'*) ;;
          *) fail "정규화 우회 $path 가 셸이 아닌 200 을 냈다" ;;
        esac
        ;;
      # 401·403 은 app 의 **API 포트**가 받아 인증 경계에서 끊은 것이다(그 포트에 관리 표면이
      # 없다는 것은 기존 컨테이너 스모크가 재는 사실이고, 위 표지 단언이 여기서 다시 잰다).
      400|401|403|404|301|308) ;;
      *) fail "정규화 우회 $path 의 응답 코드가 예상 밖이다: $c" ;;
    esac
  done
  echo "  $path -> 무인증 $code · 인증 $auth_code"
done

# ---- (G) 우회 ④ — 평문 ----
_step "(G) 우회 ④ — 평문은 거부된다(리다이렉트도 내용도 아니다)"
for path in / /api/strategy; do
  code="$(_plain_code "$path")"
  body="$(_plain_body "$path")"
  case "$body" in
    *'id="root"'*) fail "평문 $path 가 web 셸을 냈다" ;;
  esac
  case "$code" in
    # 실측(2026-10-16): https 리스너에 평문 바이트가 오면 Caddy 가 **400** 으로 끊는다 —
    # 내용도 리다이렉트도 아니고, app 에 닿지도 않는다(그 경로가 열렸다면 `/api/strategy` 가
    # 401 을 냈을 것이고 401 은 아래 허용 집합 밖이라 붉어진다). 이 한 칸이 우회 ④ 의 선택
    # 「거부」가 실제로 어떻게 보이는지다.
    transport-error|400) ;;
    301|302|307|308)
      # 리다이렉트로 끝나는 구성도 허용 범위이지만(설계 검토 ④ 「리다이렉트 또는 거부」),
      # 그 목적지는 https 여야 한다.
      loc="$(curl -sS --max-time 5 -o /dev/null -w '%{redirect_url}' "http://${DOMAIN}:${PORT}$path" 2>/dev/null)" || loc=""
      case "$loc" in https://*) ;; *) fail "평문 $path 의 리다이렉트 목적지가 https 가 아니다: [$loc]" ;; esac
      ;;
    *) fail "평문 $path 가 $code 로 응답했다 — 평문이 거부되지 않는다" ;;
  esac
  echo "  평문 $path -> $code"
done

# ---- (H) 우회 ⑤ — non-root ----
_step "(H) 우회 ⑤ — web·proxy 이미지가 root 로 돌지 않는다"
for img_svc in "bidvector/web:local web" "bidvector/proxy:local proxy"; do
  set -- $img_svc
  img="$1"; svc="$2"
  cfg_user="$(docker image inspect "$img" --format '{{.Config.User}}')"
  case "$cfg_user" in
    ""|root|0|0:*) fail "$img 의 Config.User 가 root/미지정이다: '$cfg_user'" ;;
  esac
  cid="$("${COMPOSE[@]}" ps -q "$svc")"
  [ -n "$cid" ] || fail "$svc 컨테이너 id 를 읽지 못했다"
  # 선언된 사용자만 보면 setuid entrypoint 를 놓친다 — **실제로 도는 프로세스의 uid 전부**를
  # 본다(`tools/image-hygiene-check.sh` 와 같은 축).
  # `docker top` 은 ps 출력에 PID 칸을 요구한다(없으면 daemon 이 거부한다 — 2026-10-16 실측).
  uids="$(docker top "$cid" -eo pid,uid,comm | tail -n +2 | awk 'NF{print $2}' | sort -u | tr '\n' ',')"
  [ -n "$uids" ] || fail "$svc 컨테이너 안 프로세스 uid 를 읽지 못했다"
  case ",$uids" in
    *,0,*) fail "$svc 컨테이너 안 프로세스가 uid 0(root)으로 돈다: [$uids]" ;;
  esac
  echo "  $img Config.User=$cfg_user 실프로세스-uid=[${uids%,}]"
done

# ---- (I) 자격 값이 접근 로그에 남지 않는다 ----
_step "(I) 프록시 접근 로그에 자격 값이 남지 않는다(양성 대조: 그 요청의 URI 는 남는다)"
"${COMPOSE[@]}" logs --no-color proxy > "$TMP/proxy.log" 2>&1 || fail "프록시 로그를 읽지 못했다"
grep -q '/api/strategy' "$TMP/proxy.log" \
  || fail "프록시 로그에 /api/strategy 요청 줄이 없다(양성 대조 실패 — 아래 단언이 공허하다)"
# 값은 **파이프로만** 흐른다(argv·파일에 쓰지 않는다).
hits="$(printf '%s\n' "$BIDVECTOR_APP_OPERATOR_CREDENTIAL" | grep -cFf - "$TMP/proxy.log" || true)"
[ "$hits" = "0" ] || fail "프록시 접근 로그에 자격 값이 ${hits}줄 남았다"
echo "  로그 줄 $(grep -c '' "$TMP/proxy.log") · 자격 값 일치 0"

echo ""
echo "== 로컬 패키징 스모크 통과 =="
