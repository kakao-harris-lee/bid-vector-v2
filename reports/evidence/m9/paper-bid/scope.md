# M9 / paper-bid — 서면(페이퍼) 투찰 알림 (OPEN-7C3B-PAPER-BID-NOTIFICATION)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- **DB 마이그레이션 slice, 번호 V25 고정**(V26~V28 은 ops-migrations 몫). 근거: `reports/evidence/m7/7c3b/scope.md` D-7C3B-12 · M7 종결 §4. 알림함 키 `(계정, 종류, 공고)` 에 투찰 종류 축이 없어 페이퍼 건이 실투찰 항목을 덮어쓴다 — 그래서 지금은 가드 둘이 페이퍼를 버린다.
- 추천안으로 진행(전역 규칙): **페이퍼 건도 알림을 받되 실투찰과 섞지 않는다** — 키에 투찰 종류 축을 넣는다.

## 결정
- D-1 V25: `notification_inbox` PK 에 투찰 종류 열(기존 행 backfill = `REAL`), 관련 인덱스 둘 재검토, **열 단위 GRANT 재선언**, privacy 등재 표 갱신.
- D-2 가드 둘 제거(`BidCaseWorkflow` 되비춤의 `asReal() ?: return` · `NotificationSweepSql` 의 `bid_kind = 'REAL'`), upsert `ON CONFLICT` 키·목록·읽음·계수 질의 전수 갱신.
- D-3 openapi 항목에 투찰 종류 칸 + 생성 타입 재생 + 화면 알림함이 종류를 구분해 보인다(배지·묶음 계수가 종류를 섞지 않는다).
- D-4 되돌림 리허설 두 갈래(미적용=파일 삭제 / 적용=새 V 로 원복 — 페이퍼 행 처분 포함).

## in_scope
`adapters/src/main/resources/db/migration/V25__*.sql` · `adapters/**/notificationinbox/**`·`NotificationSweepSql.kt` · `workflow/**/notificationinbox/**`·`bidding/BidCaseWorkflow.kt` · `openapi/**`(항목 절) · `web/src/api/generated/**`·`web/src/display/notificationDisplay.ts`·`web/src/screens/InboxScreen*` · 각 test · `config/quality/**` · evidence
## acceptance
같은 공고 PAPER+REAL → 항목 2(덮어쓰기 0) · 같은 종류 재기록 1행(멱등) · GRANT 전수 · 배지 계수 · 변이 RED. 전건 PR CI.
