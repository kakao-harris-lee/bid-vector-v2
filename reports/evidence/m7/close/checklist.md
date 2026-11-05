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
| 로그인·세션·CSRF·audit verifier ready + privacy-gate | 7D #80 verifier ready. **privacy-gate 판정은 없다** — 이 장비에 privacy-gate 정의가 없어 7D 계약이 착수 때 「verifier 표적으로 넣는다」로 대체를 선언했다(7D verifier r1: test stdout 비밀 원문 전수 스캔 0 · 러너 로그 줄 판독). 이름 붙은 개인정보 판정은 아니다. 대체로 닫는 것은 사용자 release 승인 항목이다 |
| 도메인 TLS · one-command 반입·되돌림 리허설 | 7F #112 · 7F-3 #113 (리허설) · 이 문서 1 (실반입) |
| 비밀값·개인정보 스캔 | CI 누출 게이트는 evidence 만 본다 — 화면·로그·audit 축은 test 가 진다: `NotificationInboxPrivacyTest` · `AccountCommandRunnerTest`(로그에 값·해시 0) · audit 은 구조로 — `RequestAuditFilter` 가 경로(`requestURI`)만 적고 query·본문을 적지 않는다 · `KonepsIdentifierMaskingTest` · `OpeningCompanyInfoMaskingTest` · retention 마스킹 test 들. 운영 로그는 종결 verifier r1 이 스캔해 0 |
| 활용사례 캡처·서비스 설명 | 7E-3b #110 (`docs/usecase/`) |
| G2 13 | 7C①#78 · 7C①b#87 · 7C②#94·#103 · 7C③#96 · 7C③-b#111 · 7C⑤#91·#109 · 7C④#90·#104·#106 — STR-02 요건 원문 축은 조건 문면대로 6F-5-b 에서 판정(홀더 `OPEN-6F4-STR02-REQUIREMENTS-AXIS`, 넘김) |
| 화면 C 투찰가 3안 「아직 산출 안 함」 | 7E-1 #102 |
| verifier 최종 ready + 사용자 release 승인 | 이 PR 의 verifier 코멘트 · 사용자 승인 |

## 3. host 타이머 (운영자 sudo, 2026-11-02 등록 확인)

- 알림 훑기 07:30·13:30 KST(runbook §9.3) · 인증서 감시 09:00 KST `--min-days 30`(§4.7-4) — 손 실행 「남은 198일 (임계 30)」.

## 4. 넘기는 것 (M7 밖, 등재 유지)

`OPEN-SET01-AWARD-NAME-INGEST`(운영자 결정: M7 뒤) · `OPEN-7E2B-BUDGET-CLEAR` · `OPEN-7E3B-TIME-DISPLAY` ·
`OPEN-7E3B-MONEY-WORDING` · `OPEN-7E3A-OPENING-AXIS-HARNESS` · `OPEN-7C3B-FAILED-BID-DETAIL` ·
`OPEN-7C3B-PAPER-BID-NOTIFICATION` · `OPEN-7C3B-REVISION-REUNREAD` · `OPEN-6F4-STR02-REQUIREMENTS-AXIS` · 7F 장부 v r2 L-1(checkout ↔ manifest
`git_sha` 묶기) · L-2(timeout 뒤 일회 컨테이너 확인).

## 5. evidence N/A

`differential.json` · `golden-manifest.json` — 문서 slice 라 비교할 산출물·golden 이 없다(코드·fixture 변경 0).

## 6. M9 처분 — M9/ledger (2026-11-06)

이 문서는 2026-11-02 종결 판정의 기록이라 **덮어쓰지 않는다.** 위 **4** 「넘기는 것」의 열하나 가운데
M9 머지분이 **일곱**을 닫았다. 장부 쪽 정본은 `docs/discovery/capability-map.md` §12.3 (a) 이고 근거 PR·머지
커밋을 그 절이 든다. **이 절은 재판정하지 않는다** — 각 slice 의 evidence 가 판정 자리다.

| 4 의 항목 | 처분 | 근거 |
| --- | --- | --- |
| `OPEN-7E2B-BUDGET-CLEAR` | **닫힘** | PR **#124** 머지 `319fe212` · `reports/evidence/m9/budget-clear/` |
| `OPEN-7E3B-TIME-DISPLAY` · `OPEN-7E3B-MONEY-WORDING` | **닫힘** | PR **#125** 머지 `4953d743` · `reports/evidence/m9/screen-display/` |
| `OPEN-7C3B-FAILED-BID-DETAIL` · `OPEN-7C3B-REVISION-REUNREAD` | **닫힘** | PR **#123** 머지 `df4976cc` · `reports/evidence/m9/inbox-vocab/` |
| 7F 장부 v r2 **L-1**(checkout ↔ manifest `git_sha`) · **L-2**(timeout 뒤 일회 컨테이너) | **닫힘** | PR **#122** 머지 `6c38fb32` · `reports/evidence/m9/prod-tools/` |
| `OPEN-SET01-AWARD-NAME-INGEST` | **열린 채** — 적재 slice 가 PR **#127** 과 나란히 열려 있다(PR **#128**, 미머지). **그 PR 이 머지되면 처분을 확인한다** | 운영자 결정 2026-10-22 (a) · 착수는 6G 라이브 수집 종료 뒤 |
| `OPEN-7E3A-OPENING-AXIS-HARNESS` | **열린 채** — M9 가 만지지 않았다 | — |
| `OPEN-7C3B-PAPER-BID-NOTIFICATION` | **열린 채** — #123 의 out_scope 다(그 계약이 「이 slice 머지 뒤 별도」로 적었다). 알림함 키에 종류 축이 없어 페이퍼 투찰 항목이 실투찰 항목을 덮는 축도 그대로 | `reports/evidence/m9/inbox-vocab/` 알려진 제한 7 |
| `OPEN-6F4-STR02-REQUIREMENTS-AXIS` | **열린 채 상태 갱신** — 6F-5-b LLM 추출이 사용자 결정 2026-11-05 로 보류라 축소가 유지된다 | PR **#124** · 정본은 capability map 의 STR-02 절과 그 OPEN 행 |

**M9 가 새로 만든 것은 이 문서가 받지 않는다** — 신설 OPEN 일곱(화면 표시 다섯 · CVE apt 둘)은 capability
map §12.3 (b) 에 섰고, 미머지 PR 둘이 만들 OPEN 둘은 그 PR 이 자기 머지에서 세운다.

**위 2 「M7 완료 조건 대조」는 바뀌지 않는다** — 닫힌 일곱은 전부 조건 **밖**으로 넘긴 항목이고(4 의 정의),
조건 자체의 근거 칸을 건드리지 않는다. 2 의 `privacy-gate` 판정 부재도 그대로다(이 장비에 그 정의가 없다).
