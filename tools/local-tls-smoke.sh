#!/usr/bin/env bash
# M7/7F-local — 로컬 패키징 스모크(scope.md 산출물 4). 로컬 프로필을 올리고 **프록시의
# https 하나로만** 다음을 재고 내린다:
#
#   · web 셸이 뜬다(빌드된 번들 · SPA fallback)                      · 산출물 1
#   · `/api/**` 가 app 에 닿는다(무인증 401 · 인증 200)               · 산출물 2
#   · 설계 검토 우회 ①~⑤ 가 각각 막힌다                               · scope.md (2)
#   · 자격 값이 프록시 접근 로그에 남지 않는다                         · 아래 (I)
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

# 값·compose 호출의 기본값은 **`tools/local-tls-env.sh` 한 자리**다(cr M-5 — 빌드를 compose
# 한 자리로 모으면서 값도 한 자리로 뺐다). ci.yml 의 빌드 step 과 이 스모크가 같은 조각을 읽는다.
# shellcheck source=tools/local-tls-env.sh
. "$REPO_ROOT/tools/local-tls-env.sh"
COMPOSE=("${BIDVECTOR_LOCAL_TLS_COMPOSE[@]}")

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
_step "빌드: web·proxy 이미지(단일 진입점 tools/local-tls-build.sh) + **게이트가 판정한 이미지와 같은가**"
# cr M-5 — 앞선 step 들(위생·취약점 게이트)이 판정한 것은 **빌드 전에 이미 있던 태그**다.
# 여기서 다시 빌드하므로, 재빌드가 캐시를 놓쳐 다른 바이트가 나오면 **판정한 이미지와 실행하는
# 이미지가 다르다.** 빌드 전후의 이미지 ID 를 재어 등식을 단언한다.
pre_ids=""
for img in bidvector/web:local bidvector/proxy:local; do
  # `docker image inspect --format` 는 없는 이미지에 **빈 줄을 찍고** 실패한다. 그래서
  # `… || echo none` 은 `"\nnone"` 을 내고, `none` 을 보려던 술어가 개행 때문에 맞지 않았다
  # (r1 이 둔 `*=none;` 가지도 같은 이유로 한 번도 타지 않았다 — 2026-10-19 실측). 값을 먼저
  # 받아 개행을 털고, 비었으면 그때 `none` 으로 적는다.
  img_id="$(docker image inspect "$img" --format '{{.Id}}' 2>/dev/null | tr -d '\n' || true)"
  pre_ids="${pre_ids}${img}=${img_id:-none};"
done
# **앞선 빌드 step 이 남긴 기록**(cr R2-6). 있으면 「그때 만든 바이트」와 「지금 태그가 가리키는
# 바이트」의 등식을 먼저 본다 — 그 사이에 아무도 태그를 바꾸지 않았다는 전제가 여기서 측정이
# 된다(앞 판은 그 전제를 가정만 했다). 로컬 단독 실행에는 기록이 없을 수 있고, 그때는 아래
# 재빌드 전후 등식만 남는다.
ID_RECORD="build/local-tls/built-image-ids.txt"
recorded_ids=""
# **태그가 아예 없는 경우는 대조 대상이 아니다**(cr R3-5). `docker image prune` 뒤처럼 기록만 남고
# 이미지가 사라진 로컬 상태에서 대조하면 「그 사이에 태그가 바뀌었다」로 끊기는데, 그것은 사실이
# 아니다 — 바뀐 것이 아니라 **없어진 것**이고 바른 처방은 바로 다음 줄이 하는 빌드다. CI 는
# S-21c 가 앞서므로 이 가지에 닿지 않는다.
if [ -f "$ID_RECORD" ] && [ "${pre_ids#*=none;}" = "$pre_ids" ]; then
  recorded_ids="$(tr '\n' ';' < "$ID_RECORD")"
  if [ "$recorded_ids" != "$pre_ids" ]; then
    fail "앞선 빌드가 기록한 이미지와 지금 태그가 가리키는 이미지가 다르다 — 그 사이에 태그가 바뀌었고, 게이트가 판정한 바이트는 지금 실행할 바이트가 아니다: 기록[$recorded_ids] 현재[$pre_ids]"
  fi
  echo "  빌드 기록 ↔ 현재 태그 등식 성립(게이트가 판정한 바이트가 그대로다)"
fi
./tools/local-tls-build.sh
post_ids=""
for img in bidvector/web:local bidvector/proxy:local; do
  post_ids="${post_ids}${img}=$(docker image inspect "$img" --format '{{.Id}}' | tr -d '\n');"
done
case "$pre_ids" in
  *=none\;*)
    # 이 실행이 태그를 처음 만든 경우다(로컬에서 단독 실행). 비교할 앞 판정이 없으므로 등식을
    # 주장하지 않고 **그 사실을 적는다** — CI 에서는 빌드 step 이 앞서므로 아래 등식 가지를 탄다.
    echo "  빌드 전 태그가 없었다(이 실행이 처음 만들었다) — 등식 비교 대상 없음: $post_ids"
    ;;
  *)
    [ "$pre_ids" = "$post_ids" ] \
      || fail "재빌드로 이미지 ID 가 바뀌었다 — 게이트가 판정한 바이트와 스모크가 실행하는 바이트가 다르다: 전[$pre_ids] 후[$post_ids]"
    echo "  이미지 ID 등식 성립(게이트가 판정한 것과 같은 바이트): $post_ids"
    ;;
esac

_step "기동: proxy(의존으로 app·postgres·web 이 함께 뜬다)"
"${COMPOSE[@]}" up -d proxy

_step "healthy 수렴 대기: app·web·proxy"
for _ in $(seq 1 40); do
  ps_out="$("${COMPOSE[@]}" ps --format '{{.Service}}: {{.Status}}')"
  # 괄호를 **리터럴로** 둔다(BRE) — `.*healthy` 는 `(unhealthy)` 의 `un`+`healthy` 에도 맞아
  # 「수렴에 실패했다」가 확정되는 순간을 「수렴했다」로 읽는다(cr M-1 실측). `(healthy)` 는
  # `(unhealthy)` 를 포함하지 않으므로 그 칸이 닫힌다.
  if echo "$ps_out" | grep -q "app:.*(healthy)" \
    && echo "$ps_out" | grep -q "web:.*(healthy)" \
    && echo "$ps_out" | grep -q "proxy:.*(healthy)"; then
    echo "$ps_out"
    break
  fi
  sleep 3
done
ps_out="$("${COMPOSE[@]}" ps --format '{{.Service}}: {{.Status}}')"
# 최종 관문의 술어는 **proxy 하나**다 — `up -d proxy` 의 `depends_on: service_healthy` 때문에
# app·web 이 healthy 가 아니면 proxy 가 애초에 서지 않으므로 사실상 셋을 함께 재지만, 문면이
# 술어보다 넓으면 실패했을 때 **무엇이 측정됐는지**가 어긋난다(cr L-8).
echo "$ps_out" | grep -q "proxy:.*(healthy)" || { echo "$ps_out" >&2; fail "proxy 가 제한 시간 안에 healthy 로 수렴하지 않았다(app·web 은 depends_on: service_healthy 로 그 앞에 선다 — 위 ps 출력이 어느 단계에서 멈췄는지 가리킨다)"; }

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
# 두 서비스의 `no-new-privileges` — 선언해 두고 **아무것도 재지 않으면** 지우는 편집이 조용히
# 지나간다(cr L-3). (A)가 이미 해석된 설정을 읽고 있으므로 같은 자리에서 본다.
for svc in web proxy; do
  nnp="$(printf '%s' "$ports_json" | jq -r --arg s "$svc" '[(.services[$s].security_opt // [])[] | select(. == "no-new-privileges:true")] | length')" \
    || fail "$svc 의 security_opt 를 해석된 설정에서 읽지 못했다"
  [ "$nnp" = "1" ] || fail "$svc 에 security_opt no-new-privileges:true 가 없다(해석된 설정 기준)"
done
# **해석된 설정에 관리 포트가 상류로 실려 있지 않다**(우회 ② 의 구조 축). app 서비스의
# `MANAGEMENT_SERVER_PORT` 선언 하나만 그 값을 든다.
# 술어는 **숫자 경계가 있는 등식**이다(cr L-2). 부분 문자열(`contains`)이면 관리 포트 8080 이
# API 포트 18080 과 프록시 상류 `app:18080` 에도 맞아 **스모크가 거짓 실패**한다 — runbook §9.2
# 가 포트 재정의를 권하므로 운영자가 실제로 밟는 자리다(fail-closed 라 보안 구멍은 아니지만
# 「게이트가 틀린 답을 낸다」는 같은 등급의 결함이다).
mgmt_refs="$(printf '%s' "$ports_json" \
  | jq -r --arg p "$BIDVECTOR_APP_MANAGEMENT_PORT" \
    '[.services | to_entries[] | . as $s | (.value.environment // {}) | to_entries[] | select((.value|tostring) | test("(^|[^0-9])" + $p + "([^0-9]|$)")) | "\($s.key).\(.key)"] | sort | join(",")')"
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

_step "(A4) 우회 ② — web 은 app 과 **다른 망**이고 상류를 하나도 갖지 않는다(검토 r1 F-1)"
# 앞 판의 구멍: 「관리 포트는 프록시의 상류가 아니다」만 구조였고, web 도 Caddy 이며 app 과
# 같은 망에 있었다 — web 설정에 `reverse_proxy app:<관리포트>` 한 블록이면 프록시 https 로
# 관리 표면이 나왔고 (A3)·(E)·위생 어느 것도 붉어지지 않았다. 축을 셋으로 닫는다.
#
# ⓐ **망**: 해석된 설정에서 web 의 망 집합과 app 의 망 집합이 겹치지 않고, 프록시만 둘 다에 있다.
net_verdict="$(printf '%s' "$ports_json" | jq -r '
  ((.services.web.networks   // {}) | keys) as $w |
  ((.services.app.networks   // {}) | keys) as $a |
  ((.services.proxy.networks // {}) | keys) as $p |
  [ (if ($w | length) == 0 then "web 의 망 선언이 비었다" else empty end),
    (if ($a | length) == 0 then "app 의 망 선언이 비었다" else empty end),
    (if (($w - ($w - $a)) | length) > 0 then "web 과 app 이 망을 공유한다: \($w - ($w - $a) | join(","))" else empty end),
    (if (($w - $p) | length) > 0 then "프록시가 web 의 망에 없다: \($w - $p | join(","))" else empty end),
    (if (($a - $p) | length) > 0 then "프록시가 app 의 망에 없다: \($a - $p | join(","))" else empty end)
  ] | join(" · ")')" \
  || fail "해석된 설정에서 망 집합을 읽지 못했다(망 분리를 재지 못한다)"
[ -z "$net_verdict" ] || fail "망 분리가 깨졌다 — $net_verdict"
# `edge` 는 **바깥으로 나가지 않는다**(cr R2-10). 선언만 두고 재지 않으면 지우는 편집이 조용히
# 지나간다 — r1 의 `no-new-privileges` 와 같은 자리다.
edge_internal="$(printf '%s' "$ports_json" | jq -r '(.networks.edge.internal // false) | tostring')" \
  || fail "해석된 설정에서 edge 망의 internal 을 읽지 못했다"
[ "$edge_internal" = "true" ] || fail "edge 망이 internal 이 아니다: $edge_internal — web 이 바깥으로 나갈 수 있다"
echo "  망: web=[$(printf '%s' "$ports_json" | jq -r '(.services.web.networks // {}) | keys | join(",")')] app=[$(printf '%s' "$ports_json" | jq -r '(.services.app.networks // {}) | keys | join(",")')] proxy=[$(printf '%s' "$ports_json" | jq -r '(.services.proxy.networks // {}) | keys | join(",")')]"

# ⓑ **설정**: web 의 적응된 설정에 상류(`dial`)가 0 이다 — (A3) 과 같은 축을 한 칸 옆 파일에도
# 건다. 양성 대조로 적응 결과가 실제 handler 를 담고 있는지 먼저 본다(빈 JSON 이면 「상류 0」이
# 공허하게 참이 된다).
web_adapted="$("${COMPOSE[@]}" exec -T web caddy adapt --config /etc/caddy/Caddyfile --adapter caddyfile 2>/dev/null)" \
  || fail "web 설정을 적응하지 못했다(상류 집합을 재지 못한다)"
web_handlers="$(printf '%s' "$web_adapted" | jq -r '[.. | objects | select(has("handler")) | .handler] | length')" \
  || fail "web 의 적응된 설정에서 handler 수를 읽지 못했다(상류 단언이 공허해진다)"
[ "${web_handlers:-0}" -gt 0 ] || fail "web 의 적응된 설정에 handler 가 0 이다(양성 대조 실패 — 아래 상류 단언이 공허하다)"
web_dials="$(printf '%s' "$web_adapted" | jq -r '[.. | objects | select(has("dial")) | .dial] | unique | join(",")')" \
  || fail "web 의 적응된 설정에서 상류 집합을 읽지 못했다"
[ -z "$web_dials" ] || fail "web 의 상류 집합이 비어 있지 않다: [$web_dials] — web 은 정적 서버이고 어떤 상류도 갖지 않는다"
echo "  web handler $web_handlers · 상류 0"

# ⓒ **실제 연결**: web 컨테이너에서 app 의 두 포트에 닿지 않는다. **양성 대조 둘**을 함께 돌린다
# — 같은 탐침이 (1) web 자기 포트에서는 200 을 받고(도구가 동작한다) (2) proxy 에서는 app 에
# 닿는다(그 포트가 실제로 살아 있다). 그러지 않으면 「닿지 않는다」가 공허해진다.
web_self="$("${COMPOSE[@]}" exec -T web curl -sS --max-time 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:${BIDVECTOR_WEB_PORT}/")" \
  || fail "web 컨테이너 안에서 자기 포트를 묻지 못했다(양성 대조 실패 — 탐침 자체가 동작하지 않는다)"
[ "$web_self" = "200" ] || fail "web 자기 포트가 200 이 아니다: $web_self(양성 대조)"
proxy_to_app="$("${COMPOSE[@]}" exec -T proxy curl -sS --max-time 5 -o /dev/null -w '%{http_code}' "http://app:${BIDVECTOR_APP_MANAGEMENT_PORT}/actuator/health")" \
  || fail "프록시에서 app 관리 포트에 닿지 못했다(양성 대조 실패 — 아래 거부 단언이 공허하다)"
[ "$proxy_to_app" = "200" ] || fail "프록시→app 관리 포트가 200 이 아니다: $proxy_to_app(양성 대조)"
for p in "$BIDVECTOR_APP_PORT" "$BIDVECTOR_APP_MANAGEMENT_PORT"; do
  if "${COMPOSE[@]}" exec -T web curl -sS --max-time 5 -o /dev/null "http://app:${p}/" >/dev/null 2>&1; then
    fail "web 컨테이너에서 app:${p} 에 닿았다 — 망 분리가 실효가 없다(web 설정 한 줄로 관리 표면이 열린다)"
  fi
done
echo "  web→app 두 포트 연결 불가 · 양성 대조(web 자기 200 · proxy→app 관리 200)"

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
_step "(D) 보안 헤더 — 값 등식 · 이름마다 1개 · Server·HSTS 부재 · **/api 응답도 같다**"
# 앞 판은 `/` 하나에서 **존재만** 보았다(값 단언은 `nosniff` 와 CSP 접두뿐). 그래서
# `Referrer-Policy unsafe-url`·`X-Frame-Options ALLOWALL`·CSP 에 `'unsafe-inline'` 을 넣어도
# 초록이었다(vr F-3 실측). 세 칸을 닫는다 — 값 등식 · 이름당 1개 · `/api/**` 응답.
#
# 기대값은 `docker/proxy.Caddyfile` 의 선언과 **글자 그대로** 같다. 정본이 둘이 되는 대가를
# 지불하는 대신, 값을 바꾸는 편집은 두 자리를 함께 고쳐야 하므로 diff 에 드러난다.
EXPECT_XCTO="nosniff"
EXPECT_REFERRER="no-referrer"
EXPECT_XFO="DENY"
EXPECT_CSP="default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

# `grep -c` 는 **0건일 때 종료 코드 1** 이고 이 스크립트는 `pipefail` 이라, 그대로 쓰면 「헤더가
# 없다」가 단언 실패가 아니라 **문면 없는 즉사**가 된다(실측). 0 을 정상 값으로 받는다.
_hdr_count() {
  local n
  n="$(printf '%s' "$1" | grep -ci "^$2:" || true)"
  printf '%s' "${n:-0}" | tr -d '[:space:]'
}
_hdr_value() {
  local v
  v="$(printf '%s' "$1" | grep -i "^$2:" || true)"
  printf '%s' "$v" | head -n1 | sed 's/^[^:]*: *//' | tr -d '\r'
}

_check_headers() {
  local what="$1" blob="$2" h n csp got
  [ -n "$blob" ] || fail "$what 의 응답 헤더를 읽지 못했다"
  # **이름마다 정확히 1개.** 상류(app)가 같은 이름을 보낼 때 `defer` 가 덮는 대신 덧붙이는
  # 퇴행이 생기면 값만 보는 술어(첫 줄만 읽는다)로는 보이지 않는다.
  for h in X-Content-Type-Options Referrer-Policy X-Frame-Options Content-Security-Policy; do
    n="$(_hdr_count "$blob" "$h")"
    [ "$n" = "1" ] \
      || fail "$what 의 $h 가 정확히 1개가 아니다: ${n}개(0=부재 · 2 이상=상류 값과 겹쳤다, defer 가 덮지 못한다)"
  done
  got="$(_hdr_value "$blob" X-Content-Type-Options)"
  [ "$got" = "$EXPECT_XCTO" ] || fail "$what 의 X-Content-Type-Options 값이 선언과 다르다: [$got]"
  got="$(_hdr_value "$blob" Referrer-Policy)"
  [ "$got" = "$EXPECT_REFERRER" ] || fail "$what 의 Referrer-Policy 값이 선언과 다르다: [$got]"
  got="$(_hdr_value "$blob" X-Frame-Options)"
  [ "$got" = "$EXPECT_XFO" ] || fail "$what 의 X-Frame-Options 값이 선언과 다르다: [$got]"
  # CSP 는 두 겹이다 — **전체 등식**과 **완화 어휘 금지**. 등식만 두면 기대값까지 함께 고치는
  # 편집이 지나가고, 금지 어휘만 두면 목록 밖의 약화가 지나간다.
  csp="$(_hdr_value "$blob" Content-Security-Policy)"
  [ "$csp" = "$EXPECT_CSP" ] || fail "$what 의 CSP 가 선언과 다르다: [$csp]"
  case "$csp" in
    *unsafe-inline*|*unsafe-eval*|*"http://"*|*"https://"*|*'*'*)
      fail "$what 의 CSP 에 완화 어휘가 들어왔다: [$csp]" ;;
  esac
  # `-Server` 로 지우기로 한 헤더의 **부재**. 앞 판에는 단언이 없었다.
  n="$(_hdr_count "$blob" Server)"
  [ "$n" = "0" ] || fail "$what 응답에 Server 헤더가 ${n}개 남아 있다 — -Server 가 듣지 않는다"
  # 로컬 TLS 에서는 HSTS 를 보내지 않는다(`proxy.Caddyfile` 의 사유 — host 단위로 걸려 다른
  # localhost 평문 개발 서버까지 막는다). 실수로 켜지면 여기서 붉어진다.
  n="$(_hdr_count "$blob" Strict-Transport-Security)"
  [ "$n" = "0" ] || fail "$what 응답에 HSTS 가 실렸다 — localhost 에 HSTS 를 보내지 않는다"
}

hdrs="$(_hdrs /)"
_check_headers "/" "$hdrs"
# **`/api/**` 가 상류(app)가 같은 이름을 보내는 유일한 경로**다 — `header { defer }` 가 상류를
# 덮는다는 주석의 주장을 실제로 재는 자리는 여기뿐이고, 앞 판에는 그 단언이 없었다(cr M-2).
api_hdrs="$(_hdrs /api/strategy)"
_check_headers "/api/strategy" "$api_hdrs"
echo "  / · /api/strategy 둘 다 — 네 헤더 값 등식 · 각 1개 · Server 0 · HSTS 0"

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
  # **코드와 본문을 짝으로 본다**(cr M-4). 앞 판은 코드는 응답마다 따로 보면서 본문은 둘을
  # 이어 붙인 하나로 봐서, 「무인증이 셸 아닌 200 을 냈고 인증 쪽이 셸을 냈다」가 통과했다.
  for who in 무인증 인증; do
    case "$who" in
      무인증) c="$code"; b="$body" ;;
      인증)   c="$auth_code"; b="$(_drop_code "$auth_out")" ;;
    esac
    case "$c" in
      200)
        # 200 이면 셸이어야 한다(web 정적) — app 이나 관리 표면의 200 이면 안 된다.
        case "$b" in
          *'id="root"'*) ;;
          *) fail "정규화 우회 $path 의 $who 응답이 셸이 아닌 200 을 냈다" ;;
        esac
        ;;
      # 401·403 은 app 의 **API 포트**가 받아 인증 경계에서 끊은 것이다(그 포트에 관리 표면이
      # 없다는 것은 기존 컨테이너 스모크가 재는 사실이고, 위 표지 단언이 여기서 다시 잰다).
      400|401|403|404|301|308) ;;
      *) fail "정규화 우회 $path 의 $who 응답 코드가 예상 밖이다: $c" ;;
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
    # **3xx 는 허용하지 않는다**(vr F-4). 설계 검토 ④ 는 「리다이렉트 또는 거부(**하나로**)」이고
    # 이 구현이 고른 하나는 「거부」다(`proxy.Caddyfile` 의 `auto_https disable_redirects` —
    # 평문 리스너 자체를 만들지 않는다). 허용 집합에 3xx 를 남겨 두면 같은 포트에
    # `listener_wrappers { http_redirect }` 를 더해 **선택을 바꾸는 편집**이 조용히 지나간다
    # (앞 판에서 그 변이가 exit 0 이었다). 선택을 바꾸려면 Caddyfile 과 이 줄을 함께 고쳐야 한다.
    301|302|307|308)
      fail "평문 $path 가 $code 로 리다이렉트됐다 — 이 구현이 고른 우회 ④ 의 선택은 「거부」다(리다이렉트로 바꾸려면 Caddyfile 과 이 술어를 함께 고친다)" ;;
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
  # `root:root`·`root:0` 형태도 root 다 — 앞 판은 `0:*` 만 보고 `root:*` 를 놓쳤다(vr F-7).
  # 실프로세스 uid 축이 뒤에서 잡으므로 판정은 붉었지만 **첫 칸의 문면이 틀렸다**.
  case "$cfg_user" in
    ""|root|0|0:*|root:*) fail "$img 의 Config.User 가 root/미지정이다: '$cfg_user'" ;;
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
