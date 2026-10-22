#!/usr/bin/env bash
# M7/7F 본편 — host nginx 조각(`deploy/nginx/zz-bidvector_kr.conf.template`)의 **검증 하네스**
# (D-7F2-2). host 의 `/etc/nginx` 에는 쓰지도 reload 하지도 않는다 — 설정의 **사본**을 임시
# 디렉터리에 만들어 host 와 같은 판의 nginx 컨테이너에서 돌린다.
#
#     ./tools/prod-nginx-check.sh [--host-config <디렉터리>] [--proxy-image <이미지>]
#
#   --host-config  운영 host 의 nginx 설정 디렉터리(예: /etc/nginx). 주지 않으면 저장소의 대역
#                  (`deploy/nginx/test-fixture/`)을 쓴다(CI). 사본은 실행마다 임시 디렉터리에만
#                  두고 끝나면 지운다. 출력에는 기존 site 의 이름·경로를 싣지 않는다(번호로만).
#   --proxy-image  운영 site 가 구워진 proxy 이미지(기본 `bidvector/proxy:local`).
#
# 무엇을 재는가 — 두 단계로 같은 설정을 띄운다.
#   단계 1: 사본 그대로(조각 없음) → 기준 응답을 뜬다.
#   단계 2: 사본 + 조각(`zz-bidvector_kr`) → ⓐ **불변**: SNI 없는 443 · 기존 이름들의 443·80 응답이
#           단계 1 과 같다(응답 코드 + 응답한 server 블록의 인증서 표지) ⓑ **새 거동**: 테스트 도메인
#           (`bidvector.test`) 80 → 301 · ACME 경로 200 · 443 → 운영 site 의 Caddy 를 지나 상류에 닿는다
#           · 보안 헤더·HSTS 값 등식 · `Server` 에 버전 없음 · `/actuator*` 404 · 위조 XFF 가 상류에
#           닿지 않음 · `/api/session` 버스트가 429 · 인증서 남은 일수 검사(`prod-cert-check.sh`).
#
# 상류는 대역이다: proxy(운영 site) 의 app·web 상류를 **요청 헤더를 되돌려 주는 echo**(proxy 와 같은
# caddy 베이스)로 바꾼다 — app 은 XFF 를 읽지 않으므로 「상류가 무엇을 받았는가」는 echo 로만 잴 수
# 있다. 실제 app·web 을 지나는 왕복은 반입 리허설(`tools/prod-deploy.sh`)이 잰다.
#
# 망 모양: nginx 는 proxy 컨테이너의 네트워크 이름공간을 공유한다(`--network container:`). 그래서
# 조각의 `proxy_pass http://127.0.0.1:<포트>` 가 운영과 같은 모양(루프백)으로 Caddy 에 닿는다. 그
# 이름공간의 80·443 을 host 루프백의 **임시 포트**에 publish 해 host 에서 잰다(host 의 80·443 과
# 겹치지 않는다).
#
# 종료 코드: 0 · 1 단언 실패 · 2 판정 불가(도구·이미지 부재, 사본 설정이 단계 1 에서 이미 실패 등).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# host 와 같은 판(Ubuntu 패키지 1.24.0 — runbook 「host nginx」). 다이제스트 고정.
NGINX_IMAGE="nginx:1.24.0@sha256:f6daac2445b0ce70e64d77442ccf62839f3f1b4c24bf6746a857eff014e798c8"
# echo 상류 — proxy 이미지와 같은 베이스(새 베이스를 들이지 않는다).
ECHO_IMAGE="caddy:2.11.7@sha256:685ae3d7c34d9b5f8caeddc5d14051db7bbd88ff1f5a2fa0c16bd8ca328fb7af"
# 조각의 파일 이름 — **이것이 거동이다**(템플릿 머리 주석). 변이 표의 「앞 정렬」은 이 값을 바꾼다.
FRAGMENT_NAME="zz-bidvector_kr"
TEST_DOMAIN="bidvector.test"
PROXY_PORT=18443
ECHO_PORT=9000
# nginx 가 **직접** 보내는 헤더를 재는 둘째 echo — proxy 이름공간의 루프백에 붙는다(아래 N9).
ECHO_LOOPBACK_PORT=19001
FORGED_XFF="203.0.113.66"
# 조각의 limit_req: 분당 10 + 버스트 5 → 첫 6 건 통과, 그 뒤 429. 버스트를 넘겨 보낸다.
SESSION_BURST_SEND=10
SESSION_EXPECT_PASS=6
# 하네스가 만드는 테스트 인증서의 유효 일수 — 인증서 검사의 양성(임계 21 통과)·음성(임계 90 미달) 대조.
TEST_CERT_DAYS=60
EXPECT_HSTS="max-age=86400"
EXPECT_CSP="default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

PREFIX="${BIDVECTOR_NGINX_CHECK_PREFIX:-bidvector-nginxcheck}"
HOST_CONFIG=""
PROXY_IMAGE="bidvector/proxy:local"

_die() { echo "prod-nginx-check: $1" >&2; exit "${2:-1}"; }
_step() { echo "== $1"; }
FAILS=0
fail() { echo "  FAIL: $1" >&2; FAILS=$((FAILS + 1)); }

while [ "$#" -gt 0 ]; do
  case "$1" in
    --host-config) [ "$#" -ge 2 ] || _die "--host-config 값이 없다" 2; HOST_CONFIG="$2"; shift 2 ;;
    --proxy-image) [ "$#" -ge 2 ] || _die "--proxy-image 값이 없다" 2; PROXY_IMAGE="$2"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done

for t in docker curl openssl python3; do
  command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2
done
docker image inspect "$PROXY_IMAGE" >/dev/null 2>&1 || _die "proxy 이미지가 없다: $PROXY_IMAGE — tools/local-tls-build.sh 로 먼저 굽는다" 2

RUN_ID="$$"
NET="${PREFIX}-net-${RUN_ID}"
C_ECHO="${PREFIX}-echo-${RUN_ID}"
C_PROXY="${PREFIX}-proxy-${RUN_ID}"
C_NGINX="${PREFIX}-nginx-${RUN_ID}"
C_ECHO_LB="${PREFIX}-echolb-${RUN_ID}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/prod-nginx-check.XXXXXX")"

_cleanup() {
  docker rm -f "$C_NGINX" "$C_ECHO_LB" "$C_PROXY" "$C_ECHO" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
  # 사본에는 host 설정이 들어 있다 — 실행이 끝나면 남기지 않는다(D-7F2-2).
  rm -rf "$WORK"
}
trap _cleanup EXIT

# ---- 사본 만들기 -------------------------------------------------------------------------------
_step "설정 사본 — $( [ -n "$HOST_CONFIG" ] && echo 'host 설정(읽기 전용 사본)' || echo '저장소 대역')"
CONF="$WORK/etc-nginx"
CERTS="$WORK/certs"
ACME="$WORK/acme"
mkdir -p "$CERTS" "$ACME/.well-known/acme-challenge"
if [ -n "$HOST_CONFIG" ]; then
  [ -r "$HOST_CONFIG/nginx.conf" ] || _die "host 설정을 읽지 못했다: nginx.conf" 2
  # 심볼릭 링크는 그대로 둔다(sites-enabled → /etc/nginx/sites-available/… 절대 경로). 컨테이너 안에서
  # /etc/nginx 가 이 사본이므로 같은 자리를 가리킨다.
  cp -a "$HOST_CONFIG" "$CONF"
else
  cp -a deploy/nginx/test-fixture "$CONF"
fi
mkdir -p "$CONF/sites-enabled" "$CONF/conf.d" "$CONF/modules-enabled"
[ -f "$CONF/mime.types" ] || printf 'types {\n    text/html html;\n}\n' > "$CONF/mime.types"

ACME_TOKEN="harness-token-${RUN_ID}"
printf '%s' "$ACME_TOKEN" > "$ACME/.well-known/acme-challenge/$ACME_TOKEN"

# 인증서 지시어를 하네스 인증서로 바꾼다 — 실제 인증서 파일은 읽지도 복사하지도 않는다. 경로마다
# 고유한 인증서를 만들어 「어느 server 블록이 응답했는가」를 인증서 CN 으로 가린다. 경로→번호 대응은
# 두 단계에서 같다(경로 정렬 순).
_rewrite_certs() {
  python3 - "$CONF" "$CERTS" "$TEST_CERT_DAYS" "$TEST_DOMAIN" <<'PY'
import hashlib, os, re, subprocess, sys
conf, certs, days, test_domain = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
pat = re.compile(r'^(\s*)(ssl_certificate|ssl_certificate_key)(\s+)([^;]+);', re.M)
HARNESS = "/harness-certs/"
files = []
for root, _, names in os.walk(conf, followlinks=False):
    for n in names:
        p = os.path.join(root, n)
        if os.path.islink(p):
            continue
        files.append(p)
paths = set()
for p in files:
    try:
        t = open(p, encoding="utf-8", errors="surrogateescape").read()
    except OSError:
        continue
    for m in pat.finditer(t):
        # 이미 바꾼 지시어(앞 단계)는 다시 바꾸지 않는다 — 두 단계에서 같은 server 블록이 같은 인증서를
        # 내야 불변 비교가 뜻을 갖는다.
        if m.group(2) == "ssl_certificate" and not m.group(4).strip().startswith(HARNESS):
            paths.add(m.group(4).strip())
def stem(path):
    return "c" + hashlib.sha256(path.encode()).hexdigest()[:12]
for path in sorted(paths):
    s = stem(path)
    crt, key = os.path.join(certs, s + ".crt"), os.path.join(certs, s + ".key")
    if os.path.exists(crt):
        continue
    # 하네스 자신의 조각만 테스트 도메인을 SAN 으로 갖는다(인증서 검사의 이름 대조용).
    san = f"DNS:{test_domain}" if test_domain in path else f"DNS:{s}.harness.invalid"
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", days,
                    "-subj", f"/CN={s}", "-addext", f"subjectAltName={san}",
                    "-keyout", key, "-out", crt], check=True, capture_output=True)
for p in files:
    try:
        t = open(p, encoding="utf-8", errors="surrogateescape").read()
    except OSError:
        continue
    # 블록 안에서 ssl_certificate_key 는 바로 앞의 ssl_certificate 경로를 따라간다.
    def sub(m):
        global last
        if m.group(4).strip().startswith(HARNESS):
            return m.group(0)
        if m.group(2) == "ssl_certificate":
            last = stem(m.group(4).strip())
            return f"{m.group(1)}ssl_certificate{m.group(3)}/harness-certs/{last}.crt;"
        if last is None:
            raise SystemExit("ssl_certificate_key 앞에 ssl_certificate 가 없다 — 이 하네스가 읽지 않는 배치")
        return f"{m.group(1)}ssl_certificate_key{m.group(3)}/harness-certs/{last}.key;"
    last = None
    new = pat.sub(sub, t)
    if new != t:
        open(p, "w", encoding="utf-8", errors="surrogateescape").write(new)
print(len(paths))
PY
}

# ---- 컨테이너 ---------------------------------------------------------------------------------
_step "대역 기동 — echo 상류 · 운영 site proxy(이미지 ${PROXY_IMAGE})"
docker network create "$NET" >/dev/null
cat > "$WORK/echo.Caddyfile" <<EOF
{
	admin off
	persist_config off
	auto_https off
}
:${ECHO_PORT} {
	respond "xff=[{header.X-Forwarded-For}] xri=[{header.X-Real-IP}] host=[{host}] uri=[{uri}]" 200
}
EOF
# 루프백에만 bind 한다. site 주소에 `127.0.0.1` 을 쓰면 Host 일치로 읽혀 `bidvector.test` 요청이 맞지 않는다(실측).
sed "s/^:${ECHO_PORT} {/:${ECHO_LOOPBACK_PORT} {\n\tbind 127.0.0.1/" "$WORK/echo.Caddyfile" > "$WORK/echo-lb.Caddyfile"
docker run -d --name "$C_ECHO" --network "$NET" --network-alias echo \
  --security-opt no-new-privileges \
  -v "$WORK/echo.Caddyfile:/etc/caddy/Caddyfile:ro" "$ECHO_IMAGE" >/dev/null
docker run -d --name "$C_PROXY" --network "$NET" \
  --security-opt no-new-privileges \
  -p 127.0.0.1::80 -p 127.0.0.1::443 -p "127.0.0.1::${PROXY_PORT}" \
  -e BIDVECTOR_PROXY_DOMAIN="$TEST_DOMAIN" \
  -e BIDVECTOR_PROXY_HTTPS_PORT="$PROXY_PORT" \
  -e BIDVECTOR_PROXY_APP_UPSTREAM="echo:${ECHO_PORT}" \
  -e BIDVECTOR_PROXY_WEB_UPSTREAM="echo:${ECHO_PORT}" \
  --entrypoint caddy "$PROXY_IMAGE" run --config /etc/caddy/Caddyfile.prod --adapter caddyfile >/dev/null

_port() { docker port "$C_PROXY" "$1/tcp" | head -n1 | sed 's/.*://'; }
P80="$(_port 80)"; P443="$(_port 443)"; PPROXY="$(_port "$PROXY_PORT")"
[ -n "$P80" ] && [ -n "$P443" ] && [ -n "$PPROXY" ] || _die "임시 포트를 읽지 못했다" 2

_wait_http() {
  local i
  for i in $(seq 1 40); do
    curl -s -o /dev/null --max-time 2 "$@" && return 0
    sleep 0.25
  done
  return 1
}
_wait_http "http://127.0.0.1:${PPROXY}/" -H "Host: ${TEST_DOMAIN}:${PROXY_PORT}" \
  || _die "운영 site proxy 가 뜨지 않았다(판정 불가)" 2

_nginx_up() {
  docker rm -f "$C_NGINX" >/dev/null 2>&1 || true
  local test_out
  if ! test_out="$(docker run --rm --network "container:${C_PROXY}" \
      -v "$CONF:/etc/nginx:ro" -v "$CERTS:/harness-certs:ro" \
      -v "$ACME:/var/www/bidvector-v2-acme:ro" "$NGINX_IMAGE" nginx -t 2>&1)"; then
    echo "$test_out" | tail -n 3 >&2
    return 1
  fi
  docker run -d --name "$C_NGINX" --network "container:${C_PROXY}" \
    -v "$CONF:/etc/nginx:ro" -v "$CERTS:/harness-certs:ro" \
    -v "$ACME:/var/www/bidvector-v2-acme:ro" "$NGINX_IMAGE" >/dev/null
  local i
  for i in $(seq 1 40); do
    curl -s -o /dev/null --max-time 2 "http://127.0.0.1:${P80}/" && return 0
    sleep 0.25
  done
  return 1
}

# 기존 이름 — 사본의 server_name 에서 걷는다(하네스 자신의 조각 제외). 출력에는 번호만 싣는다.
# 사본의 sites-enabled 항목은 host 와 같은 **절대 경로 링크**(/etc/nginx/sites-available/…)일 수 있다 —
# 컨테이너 안에서는 사본을 가리키지만 host 에서 그대로 따라가면 **host 의 원본**을 읽는다. 그래서 링크 대상을
# 사본 안의 경로로 옮겨 읽는다(host 원본은 cp 한 번 외에는 읽지 않는다).
_enabled_files() {
  local f target
  for f in "$CONF"/sites-enabled/* "$CONF"/conf.d/*.conf; do
    [ -e "$f" ] || [ -L "$f" ] || continue
    if [ -L "$f" ]; then
      target="$(readlink "$f")"
      case "$target" in
        /etc/nginx/*) printf '%s\n' "$CONF/${target#/etc/nginx/}" ;;
        /*) _die "사본 밖을 가리키는 링크가 있다 — 이 하네스가 읽지 않는 배치(판정 불가)" 2 ;;
        *) printf '%s\n' "$(dirname "$f")/$target" ;;
      esac
    else
      printf '%s\n' "$f"
    fi
  done
}
_existing_names() {
  local files
  files="$(_enabled_files)" || return 2
  [ -n "$files" ] || return 0
  # shellcheck disable=SC2086
  grep -hoE '^[[:space:]]*server_name[[:space:]]+[^;]+;' $files 2>/dev/null \
    | sed -E 's/^[[:space:]]*server_name[[:space:]]+//; s/;$//' | tr ' ' '\n' \
    | sed '/^$/d' | grep -vx '_' | grep -vxF "$TEST_DOMAIN" | grep -v '[*~]' | sort -u || true
}

# 응답 표지: "<코드> <인증서 CN 또는 -> <Location 또는 ->"
_probe_https() {
  # 응답 코드는 stdout, 인증서 주체는 verbose(stderr) — 두 흐름을 섞지 않는다(섞으면 순서가 흔들린다).
  local name="$1" code cn v="$WORK/probe.verbose"
  if [ -z "$name" ]; then
    code="$(curl -sk -o /dev/null -w '%{http_code}' -v --max-time 5 "https://127.0.0.1:${P443}/" 2>"$v" || true)"
  else
    code="$(curl -sk -o /dev/null -w '%{http_code}' -v --max-time 5 \
      --resolve "${name}:${P443}:127.0.0.1" "https://${name}:${P443}/" 2>"$v" || true)"
  fi
  cn="$(grep -oE 'subject: CN=[^;,]+' "$v" | head -n1 | sed 's/subject: CN=//' || true)"
  printf '%s %s' "${code:-000}" "${cn:--}"
}
_probe_http() {
  local name="$1"
  curl -s -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 5 \
    -H "Host: ${name}" "http://127.0.0.1:${P80}/probe" 2>/dev/null || printf '000 -'
}

_snapshot() {
  local i=0 name
  printf 'nosni %s\n' "$(_probe_https "")"
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    i=$((i + 1))
    printf 'name%d-443 %s\n' "$i" "$(_probe_https "$name")"
    printf 'name%d-80 %s\n' "$i" "$(_probe_http "$name")"
  done <<< "$NAMES"
}

# ---- 단계 1: 조각 없음 ---------------------------------------------------------------------------
_step "단계 1 — 사본 그대로(조각 없음)"
ncerts="$(_rewrite_certs)" || _die "인증서 지시어를 바꾸지 못했다(판정 불가)" 2
NAMES="$(_existing_names)" || _die "활성 site 목록을 읽지 못했다(판정 불가)" 2
echo "  인증서 지시어 경로 ${ncerts}개 · 기존 이름 $(printf '%s\n' "$NAMES" | grep -c . || true)개"
[ -n "$NAMES" ] || _die "기존 이름이 0 개다 — 불변 단언이 공허하다(양성 대조 실패, 판정 불가)" 2
_nginx_up || _die "조각 없는 사본이 이미 뜨지 않는다 — 기준을 잴 수 없다(판정 불가)" 2
BEFORE="$(_snapshot)"
# 양성 대조: SNI 없는 443 이 **어떤** 인증서로든 응답해야 한다(TLS 가 서 있다).
case "$(printf '%s\n' "$BEFORE" | grep '^nosni ' | awk '{print $3}')" in
  ''|-) _die "단계 1 의 SNI 없는 443 이 인증서를 내지 않았다 — 기준이 공허하다(판정 불가)" 2 ;;
esac

# ---- 단계 2a: 첫 발급 전 판(80 만) ---------------------------------------------------------------
# runbook 의 첫 배치는 `--http-only` 판이다(certbot 첫 발급 전 — 443 server 는 인증서 파일을 요구한다).
# 그 판도 기존 응답을 바꾸지 않아야 하고, ACME 경로가 서야 한다.
_step "단계 2a — 첫 발급 전 판(--http-only) ${FRAGMENT_NAME}"
./tools/prod-nginx-render.sh --http-only "$TEST_DOMAIN" "$PROXY_PORT" "$WORK/zz-http.conf" >/dev/null
cp "$WORK/zz-http.conf" "$CONF/sites-enabled/${FRAGMENT_NAME}"
if ! _nginx_up; then
  fail "첫 발급 전 판이 nginx -t 를 통과하지 못하거나 뜨지 않는다"
else
  [ "$(_snapshot)" = "$BEFORE" ] || fail "첫 발급 전 판을 넣자 기존 응답이 바뀌었다"
  body="$(curl -s --max-time 5 -H "Host: ${TEST_DOMAIN}" "http://127.0.0.1:${P80}/.well-known/acme-challenge/${ACME_TOKEN}" || true)"
  [ "$body" = "$ACME_TOKEN" ] || fail "첫 발급 전 판에서 ACME webroot 가 토큰을 내지 않는다"
  echo "  첫 발급 전 판: nginx -t 통과 · 기존 응답 불변 · ACME 경로 200"
fi

# ---- 단계 2: 조각 추가 ---------------------------------------------------------------------------
_step "단계 2 — 조각 ${FRAGMENT_NAME} 추가(443 포함)"
./tools/prod-nginx-render.sh "$TEST_DOMAIN" "$PROXY_PORT" "$WORK/zz-rendered.conf" >/dev/null
cp "$WORK/zz-rendered.conf" "$CONF/sites-enabled/${FRAGMENT_NAME}"
_rewrite_certs >/dev/null || _die "조각의 인증서 지시어를 바꾸지 못했다(판정 불가)" 2
if ! _nginx_up; then
  fail "조각을 넣은 설정이 nginx -t 를 통과하지 못하거나 뜨지 않는다"
  echo "prod-nginx-check: FAIL ${FAILS}" >&2
  exit 1
fi
AFTER="$(_snapshot)"

_step "(N1) 불변 — SNI 없음 · 기존 이름의 443·80 응답이 단계 1 과 같다"
if [ "$BEFORE" != "$AFTER" ]; then
  diff <(printf '%s\n' "$BEFORE") <(printf '%s\n' "$AFTER") | sed 's/^/    /' >&2 || true
  fail "조각을 넣자 기존 응답이 바뀌었다 — 파일 이름 정렬 또는 listen 이 기존 site 의 기본 대상을 가져갔다"
else
  echo "  같다 — 표지 $(printf '%s\n' "$AFTER" | wc -l | tr -d ' ')줄"
fi

_step "(N2) 80 — 테스트 도메인은 301 https · ACME 경로만 200"
got="$(_probe_http "$TEST_DOMAIN")"
[ "$got" = "301 https://${TEST_DOMAIN}/probe" ] || fail "80 리다이렉트가 기대와 다르다: $got"
body="$(curl -s --max-time 5 -H "Host: ${TEST_DOMAIN}" "http://127.0.0.1:${P80}/.well-known/acme-challenge/${ACME_TOKEN}" || true)"
[ "$body" = "$ACME_TOKEN" ] || fail "ACME webroot 경로가 토큰을 내지 않는다"

_https() { curl -sk --max-time 5 --resolve "${TEST_DOMAIN}:${P443}:127.0.0.1" "$@"; }
_u() { printf 'https://%s:%s%s' "$TEST_DOMAIN" "$P443" "$1"; }

_step "(N3) 443 — 조각이 운영 site 의 Caddy 를 지나 상류에 닿는다"
body="$(_https "$(_u /some/page)" || true)"
case "$body" in
  *"host=[${TEST_DOMAIN}"*"uri=[/some/page]"*) echo "  상류 도달" ;;
  *) fail "443 응답이 echo 상류에서 오지 않았다" ;;
esac
[ "$(_probe_https "$TEST_DOMAIN" | awk '{print $2}')" != "$(printf '%s\n' "$AFTER" | grep '^nosni ' | awk '{print $3}')" ] \
  || fail "테스트 도메인이 SNI 없는 요청과 같은 인증서를 받았다 — 조각의 server 블록이 응답하지 않았다"

_step "(N4) 헤더 — 보안 헤더 값 등식 · HSTS · Server 에 버전 없음 · 이름마다 1개"
hdrs="$(_https -D - -o /dev/null "$(_u /)" | tr -d '\r' || true)"
_hv() { printf '%s\n' "$hdrs" | grep -i "^$1:" | sed 's/^[^:]*: *//'; }
_hc() { printf '%s\n' "$hdrs" | grep -ci "^$1:" || true; }
[ "$(_hv Strict-Transport-Security)" = "$EXPECT_HSTS" ] || fail "HSTS 값이 기대와 다르다: '$(_hv Strict-Transport-Security)'"
[ "$(_hv Content-Security-Policy)" = "$EXPECT_CSP" ] || fail "CSP 값이 기대와 다르다"
[ "$(_hv X-Content-Type-Options)" = "nosniff" ] || fail "X-Content-Type-Options 값"
[ "$(_hv Referrer-Policy)" = "no-referrer" ] || fail "Referrer-Policy 값"
[ "$(_hv X-Frame-Options)" = "DENY" ] || fail "X-Frame-Options 값"
for h in Strict-Transport-Security Content-Security-Policy X-Content-Type-Options Referrer-Policy X-Frame-Options Server; do
  [ "$(_hc "$h")" = "1" ] || fail "헤더 $h 가 정확히 1개가 아니다: $(_hc "$h")"
done
[ "$(_hv Server)" = "nginx" ] || fail "Server 헤더가 이름만이 아니다(버전 노출?): '$(_hv Server)'"

_step "(N5) 관리 경로 — /actuator* 404 (Caddy 공유 본문)"
for p in /actuator /actuator/health /actuator/env; do
  code="$(_https -o /dev/null -w '%{http_code}' "$(_u "$p")" || true)"
  [ "$code" = "404" ] || fail "$p 가 404 가 아니다: $code"
done

_step "(N6) XFF — 위조 값이 상류에 닿지 않는다(nginx 경유 · Caddy 직접 둘 다)"
body="$(_https -H "X-Forwarded-For: ${FORGED_XFF}" -H "X-Real-IP: ${FORGED_XFF}" "$(_u /xff)" || true)"
case "$body" in
  *"xff=["*) ;;
  *) fail "nginx 경유 echo 응답을 받지 못했다(양성 대조)" ;;
esac
case "$body" in
  *"$FORGED_XFF"*) fail "nginx 경유에서 위조 XFF/X-Real-IP 가 상류에 닿았다" ;;
  *) echo "  nginx 경유: 위조 값 0" ;;
esac
# 루프백 직접 접속(nginx 를 거치지 않는 host 프로세스) — Caddy 가 신뢰하지 않는 출처의 XFF 를 버려야 한다.
body="$(curl -s --max-time 5 -H "Host: ${TEST_DOMAIN}:${PROXY_PORT}" -H "X-Forwarded-For: ${FORGED_XFF}" \
  "http://127.0.0.1:${PPROXY}/xff" || true)"
case "$body" in
  *"xff=["*) ;;
  *) fail "Caddy 직접 echo 응답을 받지 못했다(양성 대조)" ;;
esac
case "$body" in
  *"xff=[${FORGED_XFF}"*|*"xff=[${FORGED_XFF},"*|*", ${FORGED_XFF}"*) fail "Caddy 직접 접속에서 위조 XFF 가 상류에 닿았다" ;;
  *) echo "  Caddy 직접: 위조 XFF 0" ;;
esac

_step "(N7) /api/session 유량 제한 — 버스트 ${SESSION_BURST_SEND}건 중 ${SESSION_EXPECT_PASS}건 통과 · 나머지 429"
codes=""
for i in $(seq 1 "$SESSION_BURST_SEND"); do
  codes+="$(_https -o /dev/null -w '%{http_code}' -X POST "$(_u /api/session)" || echo 000) "
done
pass="$(printf '%s' "$codes" | tr ' ' '\n' | grep -c '^200$' || true)"
limited="$(printf '%s' "$codes" | tr ' ' '\n' | grep -c '^429$' || true)"
[ "$pass" = "$SESSION_EXPECT_PASS" ] && [ "$limited" = "$((SESSION_BURST_SEND - SESSION_EXPECT_PASS))" ] \
  || fail "유량 제한 분포가 기대와 다르다: [${codes% }]"
# 대조: 같은 순간 다른 경로는 제한되지 않는다(존이 /api/session 에만 걸렸다).
code="$(_https -o /dev/null -w '%{http_code}' "$(_u /api/other)" || true)"
[ "$code" = "200" ] || fail "제한 직후 다른 /api 경로가 200 이 아니다: $code"

_step "(N8) 인증서 검사 — 양성(임계 21) · 음성(임계 90 미달) · 이름 불일치"
if ! ./tools/prod-cert-check.sh "$TEST_DOMAIN" --connect "127.0.0.1:${P443}" --min-days 21 >/dev/null; then
  fail "prod-cert-check 양성 대조가 붉다"
fi
set +e
./tools/prod-cert-check.sh "$TEST_DOMAIN" --connect "127.0.0.1:${P443}" --min-days 90 >/dev/null 2>&1; rc_low=$?
./tools/prod-cert-check.sh "wrong-name.${TEST_DOMAIN}" --connect "127.0.0.1:${P443}" --min-days 1 >/dev/null 2>&1; rc_name=$?
set -e
[ "$rc_low" = "1" ] || fail "남은 일수 미달이 exit 1 이 아니다: $rc_low"
[ "$rc_name" = "1" ] || fail "다른 site 인증서가 exit 1 이 아니다: $rc_name"

_step "(N9) nginx 가 직접 보내는 헤더 — XFF·X-Real-IP 는 접속 주소로 덮어쓴다(위조 값 0)"
# N6 은 끝에서 끝까지를 잰다. 그런데 Caddy 는 신뢰하지 않는 출처의 XFF 를 스스로 버리므로, nginx 가 위조
# 값을 덧붙여도 N6 은 초록이다(2026-10-22 변이 실측) — 두 겹 중 nginx 겹이 측정되지 않는다. 그래서 조각의
# 상류를 같은 이름공간 루프백의 echo 로 바꾼 판을 한 번 더 띄워 nginx 의 출력 자체를 잰다.
docker run -d --name "$C_ECHO_LB" --network "container:${C_PROXY}" --security-opt no-new-privileges \
  -v "$WORK/echo-lb.Caddyfile:/etc/caddy/Caddyfile:ro" "$ECHO_IMAGE" >/dev/null
./tools/prod-nginx-render.sh "$TEST_DOMAIN" "$ECHO_LOOPBACK_PORT" "$WORK/zz-echo.conf" >/dev/null
cp "$WORK/zz-echo.conf" "$CONF/sites-enabled/${FRAGMENT_NAME}"
_rewrite_certs >/dev/null || _die "echo 판의 인증서 지시어를 바꾸지 못했다(판정 불가)" 2
n9_before="$FAILS"
if ! _nginx_up; then
  fail "echo 판이 뜨지 않는다"
else
  body="$(_https -H "X-Forwarded-For: ${FORGED_XFF}" -H "X-Real-IP: ${FORGED_XFF}" "$(_u /xff)" || true)"
  xff="$(printf '%s' "$body" | sed -n 's/.*xff=\[\([^]]*\)\].*/\1/p')"
  xri="$(printf '%s' "$body" | sed -n 's/.*xri=\[\([^]]*\)\].*/\1/p')"
  [ -n "$xff" ] || fail "nginx 직접 echo 응답에 XFF 가 없다(양성 대조)"
  case "$body" in *"$FORGED_XFF"*) fail "nginx 가 위조 XFF/X-Real-IP 를 상류로 보냈다" ;; esac
  [ "$xff" = "$xri" ] || fail "nginx 의 XFF 가 접속 주소 하나가 아니다: xff=[$xff] xri=[$xri]"
  [ "$FAILS" -gt "$n9_before" ] || echo "  nginx 출력: XFF == X-Real-IP == 접속 주소 · 위조 값 0"
fi

if [ "$FAILS" -gt 0 ]; then
  echo "prod-nginx-check: FAIL ${FAILS}" >&2
  exit 1
fi
echo "prod-nginx-check: OK — 불변 $(printf '%s\n' "$AFTER" | wc -l | tr -d ' ')표지 · 새 거동 N2~N9"
