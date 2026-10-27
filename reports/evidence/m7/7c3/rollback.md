# M7/7C③ — 되돌림

base `46ac0949`(7B 머지 뒤 재고정 — 착수 base `a61ddff1` 이 아니다). **전제**: 그 SHA 에 이
slice 의 산출물이 **없다**(`46ac0949` 은 7B 의 머지 커밋이고 알림함은 그 뒤에 온다).
목록은 **기계 산출**이고 라운드마다 재산출한다
(`git diff --name-status 46ac0949..HEAD`, evidence 제외).

**실측 HEAD**: `2f0c29a7` (이 slice 의 **마지막 산출물 커밋** — capability map 등재. evidence 커밋은 이 뒤에 온다)

## 되돌림의 두 경로 (마이그레이션이 있어 갈린다)

이 slice 는 **표를 만든다**. 그래서 되돌림이 배포 전/후로 갈리고, 둘은 다른 수단이다.

### ⓐ 배포 전 (코드만 — 이 파일의 주 경로)

`V23` 이 어느 환경에도 적용되지 않은 상태라면 **파일을 되돌리는 것으로 끝난다**. 적용된
스키마가 없으므로 지울 표도 없다.

### ⓑ 배포 뒤 (표가 이미 선 환경)

**파일을 되돌리는 것으로는 표가 사라지지 않는다** — Flyway 는 되돌림 마이그레이션을 돌리지
않고, 적용 이력(`flyway_schema_history`)에 남은 V23 을 파일만 지우면 `validate()` 가 깨진다
(「적용됐는데 파일이 없다」). 그러므로 ⓑ 는 **순방향 마이그레이션**이다:

```sql
-- V<다음 번호>__drop_notification_inbox.sql
SET LOCAL lock_timeout = '5s';
REVOKE ALL ON notification_inbox FROM bidvector_app;
DROP TABLE notification_inbox;   -- 인덱스 둘이 함께 사라진다
```

그 커밋은 **보존 분류 레지스트리에서도 그 행을 빼야 한다**(레지스트리↔카탈로그 집합 등식이
표를 지우고 분류를 남기면 붉다). 그리고 **알림함 데이터가 사라진다** — 되돌릴 수 없는
파기이므로 ⓑ 는 운영자 승인 사안이다(이 slice 는 그 경로를 **실행하지 않고 기록만 한다**).

머지 뒤 되돌림이면 ⓑ 를 쓰지 않고 **PR revert** 가 먼저다.

## 복원 목록 — 수정된 공유 파일 **열아홉** (base 로 복원)

```
git restore --source=46ac0949 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/retention/RetentionTableRegistry.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationIndexTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/retention/RetentionFixture.kt \
  app/src/main/kotlin/bidvector/app/http/AccountScopedRoutes.kt \
  app/src/main/kotlin/bidvector/app/http/ErrorBody.kt \
  app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt \
  app/src/test/kotlin/bidvector/app/http/AccountCarrierStructureTest.kt \
  app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt \
  app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  openapi/bidvector-operator-api.yaml \
  web/src/api/generated/operator-api.ts
```

**머지 뒤라면 `--source` 는 base 가 아니라 병합된 main 커밋 SHA 다**(고정된 SHA — 움직이는
`origin/main` 을 쓰지 않는다). 전제: 그 SHA 에 이 slice 가 **없다**. 같은 라운드의 7B·7C④ 가
`config/quality/**` 와 `openapi/**` 의 다른 줄을 만지므로, base 복원은 **그 레인들의 줄도 함께
지운다** — 머지 뒤에는 반드시 병합 SHA 기준이어야 한다.

## 삭제 목록 — 신규 파일 **스물둘**

```
rm -f -- \
  adapters/src/main/kotlin/bidvector/adapters/notificationinbox/JdbcNotificationInbox.kt \
  adapters/src/main/kotlin/bidvector/adapters/notificationinbox/JdbcNotificationInboxStore.kt \
  adapters/src/main/kotlin/bidvector/adapters/notificationinbox/NotificationInboxSql.kt \
  adapters/src/main/resources/db/migration/V23__notification_inbox.sql \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxConcurrencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPersistenceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPrivacyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxQueryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxTestSupport.kt \
  app/src/main/kotlin/bidvector/app/http/NotificationInboxController.kt \
  app/src/main/kotlin/bidvector/app/http/NotificationInboxRequests.kt \
  app/src/main/kotlin/bidvector/app/wiring/NotificationInboxQuery.kt \
  app/src/main/kotlin/bidvector/app/wiring/NotificationInboxWiring.kt \
  app/src/test/kotlin/bidvector/app/http/FakeNotificationInboxStore.kt \
  app/src/test/kotlin/bidvector/app/http/HttpTestApplication.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiNotificationInboxContractTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInbox.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInboxPorts.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationPageParsing.kt \
  workflow/src/test/kotlin/bidvector/workflow/notificationinbox/NotificationFactTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notificationinbox/NotificationPageParsingTest.kt
```

## 등식 확인 (목록이 손 목록이 아님)

```
comm -23 <(git diff --name-only 46ac0949..HEAD | grep -v '^reports/evidence/' | sort) \
         <(이 파일이 든 경로 전수 | sort)
```

**양방향 모두 빈 출력**(실측 `2f0c29a7`): `comm -23`(기계 산출에만 있고 목록에 없는 것) 0 ·
`comm -13`(목록에만 있고 기계 산출에 없는 것) 0. 이 파일이 드는 경로 **41** = 복원 19 + 삭제 22
= 기계 산출(evidence 제외) 전수.

**빈 등식이 건전성의 증거는 아니다** — 「내가 커밋하지 않아도 그 경로가 들어오는가」를 함께
본다(위 기계 산출이 그 질문의 답이다). 이 라운드가 그 값을 실제로 바꿨다: 앞 판 목록은
복원 15 · 삭제 18 이었고, 그 뒤 라운드가 만든 파일 넷(어댑터 읽기 전용 class · 읽기 투영 test ·
test 조립 분할 둘)과 공유 파일 넷(`capability-map.md` 포함)이 **목록에 없었다**. 손으로 세지
않고 라운드마다 재산출하는 이유가 이것이다.

## ①~⑥ 실측 (임시 clone, 되돌린 트리)

버릴 clone 에서 돌린다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 한다.

실측 HEAD `2f0c29a7`, clone `--no-hardlinks`(공유 저장소에 아무 작용도 남기지 않는다).

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원·삭제 명령의 종료 코드 | `RESTORE_EXIT=0` · `DELETE_EXIT=0` |
| ② | `git diff --name-status HEAD` 의 D/M 수가 위 목록과 같다 | **M=19 · D=22**, 그 밖의 줄 0. ⚠ 첫 판은 `HEAD` 를 빼고 세어 `M=0` 이 나왔다 — `--staged --worktree` 가 **색인까지** 되돌리므로 작업트리↔색인 비교에는 복원분이 보이지 않는다. rollback 의 결함이 아니라 **계수기의 결함**이었고, 고쳐서 다시 쟀다 |
| ③ | 되돌린 트리의 `git diff 46ac0949`(evidence 제외)가 빈 출력 | 잔차 **0 줄** |
| ③b | **트리 동일성** — 되돌린 트리의 해시 == base 의 트리 해시 | 둘 다 `55b05134…` → `TREE_IDENTICAL=yes` |
| ④⑤⑥ | 컴파일 · test · 게이트 | **③b 로 갈음한다.** 되돌린 트리가 base `46ac0949` 와 **바이트까지 같은 트리**이므로 그 트리에서 다시 재는 것은 base 를 재는 것과 같다. 갈음 근거는 「HEAD 초록」이 **아니라** 트리 동일성이다(base 는 머지된 main 커밋이다) |

확인은 **「내 표지 0 · 남의 표지 보존」 계수**로 한다 — 내 줄이 사라졌는가와 남의 줄이 남았는가
둘 다 본다(종료 코드만으로 완료를 주장하지 않는다).

**실측 HEAD 와 판정 SHA 의 대조**: verifier 가 보는 것은 둘이 같은지가 아니라(evidence 커밋이
늘 뒤에 오므로 영원히 다르다) **그 사이에 되돌림 대상이 움직였는가**다 —
`git diff --name-only <실측 HEAD>..<판정 SHA> -- <이 파일이 되돌리는 경로들>` 이 빈 출력이면
유효하다.
