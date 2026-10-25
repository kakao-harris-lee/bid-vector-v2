# M7/7C③ — 되돌림

base `1107f848`(7B 머지 뒤 재고정 — 착수 base `a61ddff1` 이 아니다). **전제**: 그 SHA 에 이
slice 의 산출물이 **없다**(`1107f848` 은 7B 의 머지 커밋이고 알림함은 그 뒤에 온다).
목록은 **기계 산출**이고 라운드마다 재산출한다
(`git diff --name-status 1107f848..HEAD`, evidence 제외).

**실측 HEAD**: (기록 — 마지막 산출물 커밋에서 돌린다)

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

## 복원 목록 — 수정된 공유 파일 열 (base 로 복원)

```
git restore --source=1107f848 --staged --worktree -- \
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
  app/src/test/kotlin/bidvector/app/http/AccountCarrierStructureTest.kt \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  openapi/bidvector-operator-api.yaml
```

**머지 뒤라면 `--source` 는 base 가 아니라 병합된 main 커밋 SHA 다**(고정된 SHA — 움직이는
`origin/main` 을 쓰지 않는다). 전제: 그 SHA 에 이 slice 가 **없다**. 같은 라운드의 7B·7C④ 가
`config/quality/**` 와 `openapi/**` 의 다른 줄을 만지므로, base 복원은 **그 레인들의 줄도 함께
지운다** — 머지 뒤에는 반드시 병합 SHA 기준이어야 한다.

## 삭제 목록 — 신규 파일 18

```
  adapters/src/main/kotlin/bidvector/adapters/notificationinbox/JdbcNotificationInbox.kt \
  adapters/src/main/kotlin/bidvector/adapters/notificationinbox/NotificationInboxSql.kt \
  adapters/src/main/resources/db/migration/V23__notification_inbox.sql \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxConcurrencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPersistenceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPrivacyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxTestSupport.kt \
  app/src/main/kotlin/bidvector/app/http/NotificationInboxController.kt \
  app/src/main/kotlin/bidvector/app/http/NotificationInboxRequests.kt \
  app/src/main/kotlin/bidvector/app/wiring/NotificationInboxQuery.kt \
  app/src/main/kotlin/bidvector/app/wiring/NotificationInboxWiring.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiNotificationInboxContractTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInbox.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInboxPorts.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationPageParsing.kt \
  workflow/src/test/kotlin/bidvector/workflow/notificationinbox/NotificationFactTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notificationinbox/NotificationPageParsingTest.kt
```

**7B 머지로 분류가 움직인 자리 셋**(앞 라운드 대비) — 이 절이 「라운드마다 재산출한다」의 실례다:

| 파일 | 앞 라운드 | 지금 | 사유 |
|---|---|---|---|
| `app/.../http/AccountScopedRoutes.kt` | 신규(삭제) | **수정(복원)** | 7B 가 먼저 세웠다 — 이제 그 파일에 뿌리 한 줄을 더한 것이다 |
| `app/.../http/SessionAccount.kt` | 신규(삭제) | **목록에 없음** | 임시 산출물이었고 7B 접근자로 교체하며 지웠다(D-7C3-2 ⓑ) |
| `app/.../http/SessionAccountTest.kt` | 신규(삭제) | **목록에 없음** | 〃 |

## 등식 확인 (목록이 손 목록이 아님)

```
comm -23 <(git diff --name-only 1107f848..HEAD | grep -v '^reports/evidence/' | sort) \
         <(이 파일이 든 경로 전수 | sort)
```

빈 출력이어야 한다. **빈 등식이 건전성의 증거는 아니다** — 「내가 커밋하지 않아도 그 경로가
들어오는가」를 함께 본다(위 기계 산출이 그 질문의 답이다).

## ①~⑥ 실측 (임시 clone, 되돌린 트리)

버릴 clone 에서 돌린다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 한다.

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원·삭제 명령의 종료 코드 | (기록) |
| ② | `git diff --name-status` 의 D/M 수가 위 목록과 같다 | (기록) |
| ③ | 되돌린 트리의 `git diff 1107f848` 가 빈 출력 | (기록) |
| ④ | 되돌린 트리에서 컴파일 | (기록) |
| ⑤ | 되돌린 트리에서 test | (기록) |
| ⑥ | 되돌린 트리에서 **게이트**(초록이 아니면 보완 경로까지 실행) | (기록) |

확인은 **「내 표지 0 · 남의 표지 보존」 계수**로 한다 — 내 줄이 사라졌는가와 남의 줄이 남았는가
둘 다 본다(종료 코드만으로 완료를 주장하지 않는다).

**실측 HEAD 와 판정 SHA 의 대조**: verifier 가 보는 것은 둘이 같은지가 아니라(evidence 커밋이
늘 뒤에 오므로 영원히 다르다) **그 사이에 되돌림 대상이 움직였는가**다 —
`git diff --name-only <실측 HEAD>..<판정 SHA> -- <이 파일이 되돌리는 경로들>` 이 빈 출력이면
유효하다.
