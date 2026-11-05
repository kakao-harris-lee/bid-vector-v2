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

## 계약 갱신 2026-11-06 (팀장)

**설계 승인** — 투찰 종류 축은 **사실이 아니라 키**에 둔다(`NotificationGroupKey(공고, 투찰종류)` ·
`NotificationItemKey(묶음, 알림종류)`). `bidding.BidKind` 를 알림함이 직접 들지 않고 닫힌 어휘
`NotificationBidKind{REAL, PAPER, NONE}` 를 둔다(사상의 1:1 등식 test 가 두 벌이 갈리는 것을 막는다).
`NONE` 의 근거: 알림 원천 셋 중 하나(투찰가 요청 기록)와 새 후보는 **건이 아니라 공고**에 붙고 PK 열은
NOT NULL 이라 NULL 로 적을 수 없다 — 종류 전수 표(`bidKindAxis`)와 짝 CHECK 가 그 짝을 못박는다.
**축은 기본값 없는 필수 인자다**(ops-code 레인이 뒤에 붙일 `NEW_CANDIDATE` 생산자가 컴파일에서 걸리게 —
나중에 머지되는 쪽이 맞춘다).

**PK 열 순서 승인**: `(account_id, notice_number, notice_round, bid_kind, kind)` — 묶음 읽음 처리의
술어가 그 접두가 되므로 보조 인덱스를 더하지 않는다.

**`X-Unread-Count` 는 한 수를 유지**하고 「페이퍼 안읽음도 그 수에 센다」를 test 로 잠근다(축의 구분은
묶음 계수와 묶음 라벨이 진다). 알려진 제한에 등재.

### in_scope 확장 (열둘 전부 승인)

계약의 글로브가 가드 제거(D-2)·키 변경의 귀결을 담지 못해, 아래 경로를 in_scope 에 더한다.

| 묶음 | 경로 | 왜 피할 수 없나 |
| --- | --- | --- |
| ⓐ 원천 셋 | `workflow/**/bidding/NotificationSweep.kt` | 훑기 질의의 `bid_kind = 'REAL'` 을 걷으면 건의 종류를 값으로 실어 나를 자리가 필요하다 |
| | `workflow/**/bidding/BidCaseNotifications.kt` | `BidKind` → `NotificationBidKind` 사상의 자리(`asNotificationNoticeId()` 의 형제) |
| | `app/**/wiring/BidPriceRequestFactory.kt` | 셋째 원천이 `NONE` 축을 **명시로** 싣는다(기본값을 두지 않으므로) |
| ⓑ HTTP·조립 | `app/**/http/NotificationInboxRequests.kt` | 응답 `bidKind` 칸 + 요청 본문 둘의 허용 키·어휘 판독 |
| | `app/**/wiring/NotificationInboxQuery.kt` | `markNoticeRead` 가 묶음 키를 받는다 |
| | `app/**/wiring/NotificationInboxWiring.kt` | 잃은 알림 표지에 축 한 칸(없으면 두 축의 실패가 로그에서 같은 줄이다) |
| ⓒ 스키마 래칫 | `adapters/**/persistence/CleanMigration{,Column,Check,Index}Test.kt` | 열·PK 열 집합·CHECK 수(5→7)·pkey 열 목록이 바뀐다. **`CleanMigrationPrivilegeTest` 는 바뀌지 않는다** — 알림함 행은 열 단위 부여라 표 단위 술어가 `insert`·`update` 를 이미 `false` 로 본다 |
| | `adapters/**/retention/RetentionFixture.kt` | 보존 fixture 의 seed INSERT 가 `bid_kind` 를 적어야 한다(NOT NULL · 기본값 없음) |
| ⓓ 그 test 들 | `adapters/**/bidding/NotificationSweepCandidatesTest.kt` · `app/**/http/{FakeNotificationInboxStore,InMemoryNotificationInbox,NotificationInboxEndpointTest,OpenApiNotificationInboxContractTest}.kt` · `workflow/**/bidding/{BidCaseNotificationTest,NotificationSweepTest,BidCaseWorkflowTest}.kt` · `app/**/notificationinbox/NotificationSweepRunnerTest.kt` | in_scope 산출물의 test 가 다른 디렉터리에 있거나 바뀐 타입을 짓는다 |

**openapi 의 요청 스키마 둘**(`MarkNotificationReadRequest`·`MarkNoticeNotificationsReadRequest`)도
승인 — 묶음 읽음 요청에 축이 없으면 서면 묶음을 읽는 요청이 같은 공고의 실투찰 묶음까지 읽는다.

**손대지 않는 것**: `web/src/api/queries.ts`(요청·응답 타입을 생성 타입에서 파생한다) ·
`adapters/**/retention/RetentionTableRegistry.kt`(`created_at` 기준 Purge 라 열 추가에 무영향) ·
`docs/discovery/capability-map.md`·`reports/evidence/m7/**` 의 OPEN 종결 등재(inbox-vocab 선례대로
M9 장부 slice 몫).

### 병행 레인 겹침 (팀장 공지 2026-11-06)

- **ops-code**: 평가 커밋 경로에 `NEW_CANDIDATE` 알림함 기록 **생산자**를 붙이는 중이다. 그 record 는
  `NONE` 축을 실어야 하고, 축이 필수 인자라 **나중에 머지되는 쪽**이 컴파일에서 걸려 맞춘다.
- **ops-migrations**: V26~V28 로 같은 `CleanMigration*` 래칫을 만진다 — 같은 파일 다른 행이라 병합 시
  계수로 합친다. 이 slice 의 CHECK 수 5→7 이 그 레인의 기준선이다.
