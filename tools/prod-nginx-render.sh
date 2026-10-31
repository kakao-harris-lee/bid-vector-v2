#!/usr/bin/env bash
# M7/7F 본편 — host nginx 조각 템플릿을 채운다(파일만 만든다 — host 에 놓지도 reload 하지도 않는다).
#
#     ./tools/prod-nginx-render.sh [--http-only] <도메인> <proxy 루프백 포트> <출력 파일>
#
# `--http-only`: 80 server(리다이렉트 + ACME webroot)와 유량 제한 존까지만 낸다 — certbot **첫 발급 전**에
# 놓는 판이다(443 server 는 인증서 파일을 요구해 그 전에는 `nginx -t` 가 실패한다). runbook 「host nginx」.
#
# 출력 파일 이름은 **`zz-` 로 시작해야 한다**(템플릿 머리 주석 — include 순서가 443 기본 대상을
# 정한다). 다른 이름이면 거부한다. 채운 뒤 `__BIDVECTOR_` 자리가 하나라도 남으면 실패다.
#
# 종료 코드: 0 · 1 검증 실패 · 2 사용법.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$REPO_ROOT/deploy/nginx/zz-bidvector_kr.conf.template"

_die() { echo "prod-nginx-render: $1" >&2; exit "${2:-1}"; }

HTTP_ONLY=0
if [ "${1:-}" = "--http-only" ]; then HTTP_ONLY=1; shift; fi
[ "$#" -eq 3 ] || _die "사용법: $0 [--http-only] <도메인> <proxy 루프백 포트> <출력 파일>" 2
domain="$1" port="$2" out="$3"

# 도메인은 소문자 호스트 이름만 — 설정 문법으로 새는 문자를 막는다(세미콜론·공백·중괄호).
[[ "$domain" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] \
  || _die "도메인 형식이 아니다" 2
[[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 1024 ] && [ "$port" -le 65535 ] \
  || _die "포트는 1024~65535 의 정수다(루프백 고포트)" 2
case "$(basename "$out")" in
  zz-*) ;;
  *) _die "출력 파일 이름은 zz- 로 시작해야 한다 — include 순서가 443 기본 대상을 정한다" 1 ;;
esac

MARKER='# ---- 443 (인증서가 생긴 뒤) ----'
grep -qxF "$MARKER" "$TEMPLATE" || _die "템플릿에 443 구분 줄이 없다" 1
rendered="$(sed -e "s/__BIDVECTOR_DOMAIN__/${domain}/g" -e "s/__BIDVECTOR_UPSTREAM_PORT__/${port}/g" "$TEMPLATE")"
if [ "$HTTP_ONLY" -eq 1 ]; then
  rendered="$(printf '%s\n' "$rendered" | sed "/^$(printf '%s' "$MARKER" | sed 's/[][\/.*^$]/\\&/g')\$/,\$d")"
  ! printf '%s' "$rendered" | grep -q 'listen 443' || _die "--http-only 판에 443 이 남았다" 1
fi
if printf '%s' "$rendered" | grep -q '__BIDVECTOR_'; then
  _die "채우지 못한 자리가 남았다 — 템플릿과 이 스크립트의 자리 이름이 어긋났다" 1
fi
printf '%s\n' "$rendered" > "$out"
echo "prod-nginx-render: $out"
