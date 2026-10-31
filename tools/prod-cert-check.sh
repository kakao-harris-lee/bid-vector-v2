#!/usr/bin/env bash
# M7/7F 본편 — **서빙 중인** 인증서의 남은 일수를 잰다(갱신 실패 감지, D-7F2-2).
#
#     ./tools/prod-cert-check.sh <도메인> [--connect <주소:포트>] [--min-days <N>]
#
# 파일이 아니라 실제 핸드셰이크에서 받은 인증서를 본다 — certbot 이 갱신했어도 nginx 가 reload
# 되지 않았으면 서빙 인증서는 옛것이고, 이 검사는 그 상태를 붉게 본다(파일만 보면 초록이 된다).
#
# 기본 접속 자리는 `127.0.0.1:443`(host nginx)이고 SNI 는 도메인이다 — DNS 를 거치지 않는다.
# 임계 기본값 21 일: certbot 은 90 일 인증서를 잔여 30 일에 갱신하므로, 21 일 미만은 「갱신이 9 일째
# 실패하고 있다」는 뜻이다. 주기 실행은 host 타이머(운영자 설치, runbook 「인증서」).
#
# 이름 대조도 한다 — 인증서의 SAN 에 도메인이 없으면(다른 site 의 인증서가 나왔다 — 예: 기본 대상
# 사고) 붉다. 대조는 `DNS:<도메인>` **완전 일치**라 와일드카드 인증서(`DNS:*.example.com`)는 거짓 음성이
# 된다 — Let's Encrypt 는 이 배치에서 per-domain 인증서를 내므로 오늘은 해당이 없고, 와일드카드로
# 바꾸는 날 이 술어도 함께 바꾼다. 체인 신뢰는 보지 않는다(staging·테스트 대역도 같은 경로로 재야
# 해서) — 그 축은 첫 발급 직후 runbook 의 브라우저·`curl` 확인이 진다.
#
# 종료 코드: 0 · 1 임계 미달 또는 이름 불일치 · 2 판독 불가(접속 실패·도구 부재·사용법).
set -euo pipefail

_die() { echo "prod-cert-check: $1" >&2; exit "${2:-1}"; }

DEFAULT_CONNECT="127.0.0.1:443"
DEFAULT_MIN_DAYS=21
CONNECT_TIMEOUT_S=10

[ "$#" -ge 1 ] || _die "사용법: $0 <도메인> [--connect <주소:포트>] [--min-days <N>]" 2
domain="$1"; shift
connect="$DEFAULT_CONNECT" min_days="$DEFAULT_MIN_DAYS"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --connect) [ "$#" -ge 2 ] || _die "--connect 값이 없다" 2; connect="$2"; shift 2 ;;
    --min-days) [ "$#" -ge 2 ] || _die "--min-days 값이 없다" 2; min_days="$2"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
[[ "$min_days" =~ ^[0-9]+$ ]] || _die "--min-days 는 정수다" 2
command -v openssl >/dev/null 2>&1 || _die "openssl 이 필요하다" 2

# `timeout` 을 씌운다 — 443 은 열려 있는데 TLS 핸드셰이크가 응답하지 않으면 `s_client` 는 매달린다.
# 이 검사는 systemd 타이머 안에서 도므로 매달린 실행이 다음 실행과 겹쳐 쌓인다.
_fetch_pem() {
  if command -v timeout >/dev/null 2>&1; then
    timeout "$CONNECT_TIMEOUT_S" openssl s_client -connect "$connect" -servername "$domain" </dev/null 2>/dev/null
  else
    openssl s_client -connect "$connect" -servername "$domain" </dev/null 2>/dev/null
  fi
}
pem="$(_fetch_pem | openssl x509 2>/dev/null || true)"
[ -n "$pem" ] || _die "인증서를 받지 못했다 — $connect 에 TLS 가 서 있지 않거나 ${CONNECT_TIMEOUT_S}초 안에 응답하지 않았다(판독 불가)" 2

end="$(printf '%s\n' "$pem" | openssl x509 -noout -enddate 2>/dev/null | sed 's/^notAfter=//')"
[ -n "$end" ] || _die "만료 시각을 읽지 못했다" 2
end_epoch="$(date -d "$end" +%s 2>/dev/null)" || _die "만료 시각 형식을 해석하지 못했다" 2
days=$(( (end_epoch - $(date +%s)) / 86400 ))

san="$(printf '%s\n' "$pem" | openssl x509 -noout -ext subjectAltName 2>/dev/null || true)"
if ! printf '%s' "$san" | tr ',' '\n' | sed 's/^[[:space:]]*//' | grep -qx "DNS:${domain}"; then
  _die "서빙 인증서의 SAN 에 ${domain} 이 없다 — 다른 site 의 인증서가 나왔다(남은 ${days}일)" 1
fi
if [ "$days" -lt "$min_days" ]; then
  _die "남은 일수 ${days} < 임계 ${min_days} — 갱신이 실패하고 있다(runbook 「인증서 장애」)" 1
fi
echo "prod-cert-check: ${domain} 남은 ${days}일 (임계 ${min_days})"
