# 마일스톤 9 — M8 뒤 남은 OPEN 해소

## 지위 — 사후 기록이다

M9 는 **착수 전 milestone 문서 없이** 사용자 지시로 열렸다. 그래서 이 문서는 계획서가 아니라 종결 기록이고,
**완료 조건을 사후에 만들어 자기 증명하지 않는다** — 대조 기준은 아래 지시·결정의 문면이다(대조표는
`reports/evidence/m9/close/checklist.md`). slice 마다의 계약·실측·rollback 은 `reports/evidence/m9/<slice>/` 가
정본이고, OPEN 의 정본 자리는 `docs/discovery/capability-map.md` §12(머리 집계)·§12.3(M9 장부)·§14.3 이다.

## 근거가 된 지시와 결정 (축어)

- 2026-11-05 지시: **「OPEN-STR-12 알림 실발송부터, 남은 항목이 없도록 전체 병렬 진행」**.
  2026-11-06 지시: **「남은 묶음도 지금 같이 병렬로」**.
- 2026-11-05 사용자 결정:
  - 알림 채널은 **Telegram**(Bot API 한 방향)이다. 실 토큰·실 발송으로 켜는 운영 활성화는 코드가 완성된 뒤 다시 확인한다.
  - `OPEN-SET01-AWARD-NAME-INGEST` 는 **승인**이다. 새 조달청 호출이 들어간다.
  - 수집 연장(ML 재판정)과 6F-5-b LLM 추출은 **보류**다.
  - 6B-3 보존 기간은 **1년**이다.
- 같은 날 조달청 공지(NOTICE_0000000005128): 입찰공고정보서비스가 `…/ad/BidPublicInfoService02` 로 개편되고
  옛 URL 은 공지 90일 뒤(≈2027-02-01) 중단된다.
- 범위 규칙(M9/ledger 계약): **외부 비용·운영 결정이 필요한 축은 만들지 않고 등재한다.**
  결정 지점은 추천안으로 진행한다(전역 규칙).

## slice

머지 순서가 아니라 축별로 묶었다. 머지 커밋은 각 PR 의 것이다.

| 축 | PR | slice | 한 줄 |
| --- | --- | --- | --- |
| 알림 | #127 | str12 | Telegram 실발송 채널 — 발송 축 닫힘, 운영 활성화 전 |
| 알림 | #123 | inbox-vocab | 유찰 알림 어휘(V24) · 개정 재안읽음 |
| 알림 | #137 | paper-bid | 서면 투찰 알림 — 알림함 키에 투찰 종류 축(V25) |
| 수집 | #128 | award-ingest | 낙찰 목록 일회 수집 — 원문 적재, 승격이 같은 행을 채운다 |
| 수집 | #130 | portal-02 | 조달청 02 개편 대응 — 기본 URL 전환 · mock 정확 일치 |
| 수집·운영 | #140 · #144 | ops-code (+followups) | 403·401 비재시도 · 호출 간격 · 새 후보 알림함 생산자 |
| 운영 | #141 · #145 | ops-migrations (+followups) | V26 outbox 종단 사유 · V27 실행 행 release · 기본값 없는 릴리스 키 · runbook |
| 운영 | #122 | prod-tools | 반입 트리↔manifest 묶기 · 일회 컨테이너 잔존 확인 |
| 운영 | #129 | retention-1y | 보존 90일 → 1년(정책 날짜 개정) |
| ML | #139 | ml-lifecycle | 승격 정책 승인 지문 등록부 · effective date · 정산 성숙도 시간축 |
| 화면 | #124 | budget-clear | 금액대 비우기 · STR-02 요건 축 정리 |
| 화면 | #125 · #132 | screen-display · web-followups | 시각 KST 표기 · 금액 문면 · 타임라인 문면 · D-day |
| 하네스 | #134 | gates-tests | adapters 게이트 술어를 구조로 |
| 하네스 | #135 | opening-harness | 개찰 축 여정 하네스 — 아홉 갈래 한 실행 |
| 하네스 | #136 | adr07-pitest | pitest 채택 — 순수 도메인 래칫 · 별도 CI job(`OPEN-ADR-07` 닫음) |
| 하네스 | #138 · #143 | gates-build (+followups) | 마이그레이션 순서 · bootJar · CPD · unused import 를 구조로 · 두 층 허용 뿌리 동치 |
| 장부 | #133 | ledger | M9 종결 등재 · 신설 OPEN · 운영 잔여 처분 후보(capability map §12.3) |
| 보안 | #126 · #131 · #142 | chore cve | apt 하한(CVE-2026-109518) · 그 후속 · x/net 0.62.0(CVE-2026-78661) |

M9 가 더한 Flyway 마이그레이션은 V24~V27 넷이다. CI 게이트가 결번·중복·내림차순 0 을 확인한다(#138 의
`migrationOrderGate`).

## 닫힌 것과 남은 것

**닫힌 것**의 건별 목록은 capability map §12.3 (a)·(d) 와 각 slice checklist 다. 이 문서는 다시 판정하지
않는다.

**남은 것**은 둘로 갈린다.

1. **§12 활성 OPEN 54건**(G1 3 / G2 7 / G3 16 / G4 8 / G5 7 / G6 7 / M6 잔여·메뉴 6).
   - M9 가 늘린 것은 `OPEN-STR-12-INTERACTIVE`(G3 +1)와 `OPEN-ADR07-*` 둘(G6 +2)이다.
   - 줄인 것은 0 이다. `OPEN-ADR-07` 은 §14.2 에 살았으므로 이 수에 들어 있지 않았다.
   - 머리 집계는 그동안 51 에 머물러 있었다. **이 종결이 머지 직전 재계산 규약대로 54 로 고친다.**
2. **slice 잔여 OPEN.** 각자 받는 자리(다음에 그 자리를 만지는 slice)가 있고 활성 집계 밖이다.
   - §12.3 (b) 는 ledger 시점의 등재다.
   - (f) 는 그 뒤 slice 가 낳고 registry 에 행이 없던 넷이다. 이 종결이 등재한다.

M9 는 registry 를 비우지 않았다. 남은 것은 대부분 **운영 결정·외부 비용·실데이터가 선행 조건**인 축이고,
위 범위 규칙이 그 축을 만들지 않고 등재하게 했다. 「남은 항목이 없도록」의 이행 판정은
`reports/evidence/m9/close/checklist.md` 에 있다.

## 운영 확인 항목 — 매번 별도 확인

코드·runbook 은 섰고 실행만 남은 것들이다. 어느 것도 이 문서나 M9 머지로 승인되지 않는다.

- **Telegram 실발송 활성화**
  - 실 토큰·chat id 는 사용자가 운영 env 에 직접 넣는다.
  - 절차는 `docs/runbook/m7-7f-production.md` §13 이다.
  - 대화형 편집(`OPEN-STR-12-INTERACTIVE`)은 `후속` 이다.
- **낙찰 목록 실 수집**
  - 제안 상한은 `calls-per-day 200` · `calls-total 3,000` 이다(award-ingest D-4).
  - 실행 전까지 production 의 낙찰자 상호는 전건 미공개다.
  - 그래서 `OPEN-SET01-AWARD-NAME-INGEST` 는 열려 있다.
- **운영 host 반입·DNS·인증서** — M7 종결이 남긴 별도 승인 항목 그대로다.
- **02 개편의 분리 오퍼레이션**(`OPEN-M9-PORTAL-02-SPLIT-OPERATIONS`): 기본 URL 은 이미 02 다. 첨부·지역·부대공종
  오퍼레이션을 걷는 것은 새 호출이다.

## 보류 (사용자 결정 2026-11-05)

- 수집 연장(ML 재판정).
- 6F-5-b LLM 추출.
- `OPEN-ML-ANALYSIS-WIRING` 은 M8 종결대로 shadow 통과 뒤다.

## 종결 (2026-11-11)

**M9 종결.** 지시가 연 레인 slice 는 모두 PR 로 머지됐다. 그 뒤 리뷰가 낸 비차단 지적은 후속 PR 셋(#143·#144·#145)으로 닫았다.

- 머지 PR 은 스물넷이다(#122~#145).
- 종결 판정 대조는 `reports/evidence/m9/close/checklist.md` 다.
- 하네스 장부로 넘기는 것은 `OPEN-ROLLBACK-PREDICATE-WORDING` 과 rollback 원천 교체 시 셋째 검사 후보다(#145 처분 코멘트). 다음 하네스 편집에서 처리한다.
