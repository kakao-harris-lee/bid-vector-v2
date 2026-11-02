# M7 종결 — checklist

## 1. 운영 반입 기록 (2026-11-02, 운영자 승인)

| 단계 | 결과 |
|---|---|
| 저장소 자리 | `/srv/bidvector-v2` = main `0479e1ff` clone (레인 작업 트리 아님) |
| env | `prod-env-keys-check.sh` 키 18 등식 exit 0 · JDBC URL 전체 등식 · JVM 옵션 허용 형태 (값은 기록하지 않는다) |
| release | `rel-0479e1ff2f6e` dry-run → apply OK · 위생 4/4 · 취약점 4/4 (trivy 0.75.0 핀) · allowlist 등재 0 |
| 반입 | dry-run 「첫 반입 · 백업 없음」 → 운영자 확인 → apply OK · 컨테이너 4 healthy · `prod-health.sh` OK |
| nginx | `prod-nginx-check.sh --host-config /etc/nginx` OK(불변 5표지 · N2~N9) → 운영자 배치 `zz-bidvector_kr` |
| TLS | 구매 DV 인증서(runbook §4.7) · 키 일치 · 체인 verify OK · SAN `bidvector.kr` · 만료 2027-05-19 · `prod-cert-check.sh` 198일 |
| 경로 | host: 127.0.0.1:443 SNI `bidvector.kr` → 200 · HSTS `max-age=86400` · `/actuator/health` 404 · SNI 없음 = 기존 site 유지 · 외부: 운영자 LTE 접속·로그인 확인 |
| 계정 | runbook §12 명령으로 생성 → 운영자 로그인 확인 |
| 알림 훑기 | 러너 손 실행 `exit=COMPLETE(0)`(빈 DB) · 타이머 07:30·13:30 KST — 운영자 sudo 등록 (아래 3) |

## 2. M7 완료 조건 대조 (`milestone-7.md` 「완료 조건」)

| 조건 | 근거 |
|---|---|
| 화면 다섯 + 알림함, V2 API 만 · e2e 여정 ①~⑤ (fake KONEPS) | 7A #75 · 7E-1 #102 · 7E-2 #105 · 7E-2b #107 · 7E-3a #108 · 7E-3b #110 |
| BID-01 등재 · 상태 기계·타임라인 test | 7B #89 |
| OpenAPI 단일 출처 · 생성 타입 check 모드 CI | 7A #75 · 각 7C·7E PR 의 계약 test |
| 로그인·세션·CSRF·audit verifier ready + privacy-gate | 7D #80 |
| 도메인 TLS · one-command 반입·되돌림 리허설 | 7F #112 · 7F-3 #113 (리허설) · 이 문서 1 (실반입) |
| 비밀값·개인정보 스캔 | CI 누출 게이트(각 PR) · 이 slice evidence 도 참조형 스캔 |
| 활용사례 캡처·서비스 설명 | 7E-3b #110 (`docs/usecase/`) |
| G2 13 | 7C①#78 · 7C①b#87 · 7C②#94·#103 · 7C③#96 · 7C③-b#111 · 7C⑤#91·#109 · 7C④#90·#104·#106 |
| 화면 C 투찰가 3안 「아직 산출 안 함」 | 7E-1 #102 |
| verifier 최종 ready + 사용자 release 승인 | 이 PR 의 verifier 코멘트 · 사용자 승인 |

## 3. 운영자에게 남은 host 작업

- 알림 훑기 타이머 등록(runbook §9.3) · 인증서 감시 타이머 `--min-days 30`(§4.7-4).

## 4. 넘기는 것 (M7 밖, 등재 유지)

`OPEN-SET01-AWARD-NAME-INGEST`(운영자 결정: M7 뒤) · `OPEN-7E2B-BUDGET-CLEAR` · `OPEN-7E3B-TIME-DISPLAY` ·
`OPEN-7E3B-MONEY-WORDING` · `OPEN-7E3A-OPENING-AXIS-HARNESS` · `OPEN-7C3B-FAILED-BID-DETAIL` ·
`OPEN-7C3B-PAPER-BID-NOTIFICATION` · `OPEN-7C3B-REVISION-REUNREAD` · 7F 장부 v r2 L-1(checkout ↔ manifest
`git_sha` 묶기) · L-2(timeout 뒤 일회 컨테이너 확인).
