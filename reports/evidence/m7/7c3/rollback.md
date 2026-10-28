# M7/7C③ — 되돌림

base **`0768bda1`**(go-jose CVE chore(PR #97)가 머지된 main. 이 slice 가 받은 base 는
`46ac0949` → `23837b19` → `1632242e` → `734e11b9` → `0768bda1` 로 **다섯 번** 바뀌었다).
**전제**: 그 SHA 에 이 slice 의 산출물이 **없다** — 첫 산출물 커밋이 그 뒤에 온다는 것을
`git merge-base --is-ancestor 0768bda1 HEAD` 와 아래 등식(복원 19 가 모두 `M`, 삭제 23 이 모두
`A`)으로 함께 본다. 목록은 **기계 산출**이고 라운드마다 재산출한다
(`git diff --name-status 0768bda1..HEAD`, evidence 제외).

**base 가 움직인 사유는 산출물이 아니다** — 2026-10-28 취약점 DB 갱신으로 `CVE-2026-116587`
(go-jose, Caddy 바이너리 내장)이 web·proxy 이미지에서 잡혀 **모든 PR 의 `container` job 이
막혔다.** 그 chore 가 건드린 경로는 `config/quality/vuln-allowlist.properties` 하나와 자기
evidence 셋이고, **이 slice 의 42 경로와 교집합 0** 이다(`comm -12` 빈 출력으로 실측).

**실측 HEAD**: `b65e35d0` (r3 의 main 병합 커밋 — 이 slice 의 마지막 산출물 변경은 그 앞
`1c4e6fa4` 이고, 이 병합이 들여온 것은 chore 의 allowlist 한 줄과 그 evidence 셋이라 42 경로를
건드리지 않는다)

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
git restore --source=0768bda1 --staged --worktree -- \
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

## 삭제 목록 — 신규 파일 **스물셋**

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
  app/src/test/kotlin/bidvector/app/http/NotificationInboxEndpointTest.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiNotificationInboxContractTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInbox.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInboxPorts.kt \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationPageParsing.kt \
  workflow/src/test/kotlin/bidvector/workflow/notificationinbox/NotificationFactTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notificationinbox/NotificationPageParsingTest.kt
```

## 등식 확인 (목록이 손 목록이 아님)

```
comm -23 <(git diff --name-only 0768bda1..HEAD | grep -v '^reports/evidence/' | sort) \
         <(이 파일이 든 경로 전수 | sort)
```

**양방향 모두 빈 출력**(실측 `b65e35d0`, base `0768bda1`): `comm -23` 0 · `comm -13` 0.
이 파일이 드는 경로 **42** = 복원 19 + 삭제 23 = 기계 산출(evidence 제외) 전수. 내가 **지운**
파일(D)은 0 이다.

**빈 등식이 건전성의 증거는 아니다** — 「내가 커밋하지 않아도 그 경로가 들어오는가」를 함께
본다. 라운드마다 재산출하는 이유가 r1·r2 에서 값으로 나왔다: r1 에서 앞 판 목록(복원 15 ·
삭제 18)에 그 뒤 라운드가 만든 파일 넷과 공유 파일 넷이 없었고, r2 에서는 base 가 두 번
움직였다(`46ac0949` → `23837b19` → `1632242e` → `734e11b9`). base 가 바뀌면 **같은 branch 인데도 목록이
바뀐다** — 그 main 에 이미 들어간 공유 파일 편집은 더 이상 내 차이가 아니기 때문이다.
r3 에서 base 가 다섯째로 움직였을 때는 **42 가 그대로였다**(19 M · 23 A) — chore 가 공유 파일을
건드리지 않았기 때문이고, 「목록이 안 바뀌었으니 재산출이 필요 없었다」가 아니라 **재산출해
보아야 알 수 있는 것**이다.

**r3 이 만든 새 파일은 0 이다**(`git diff --name-status acade492..HEAD --diff-filter=A` 에서
산출물 쪽 빈 출력 — 나온 셋은 병합이 들여온 chore 의 evidence 다). 그래서 이 라운드에는
「수정 라운드가 만든 새 파일 ↔ in_scope 대조」가 대조할 대상이 없다.

## ①~⑥ 실측 (임시 clone, 되돌린 트리)

버릴 clone 에서 돌린다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 한다.

실측 HEAD `b65e35d0`, base `0768bda1`, clone `--no-hardlinks`(공유 저장소에 아무 작용도 남기지 않는다).

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원·삭제 명령의 종료 코드 | `RESTORE_EXIT=0` · `DELETE_EXIT=0` |
| ② | `git diff --name-status HEAD` 의 D/M 수가 위 목록과 같다 | **M=19 · D=23**, 그 밖의 표지 0. (계수는 반드시 `HEAD` 기준이다 — r1 에서 `HEAD` 를 빼고 세어 `M=0` 을 냈고, 그것은 rollback 이 아니라 계수기의 결함이었다. `--staged --worktree` 가 색인까지 되돌린다) |
| ③ | 되돌린 트리와 base 의 차이 전수 | **4 줄**, 전부 `reports/evidence/m7/7c3/` 의 `A` 다 — **evidence 밖 잔차 0**. scope.md 의 rollback 은 evidence 를 남기므로 이것이 기대값이다 |
| ③b | **트리 동일성** — 이 slice 의 evidence 디렉터리까지 걷어낸 트리의 해시 == base 의 트리 해시 | 둘 다 `924ccdb8…` → `TREE_IDENTICAL=yes`. ③ 이 「잔차는 evidence 뿐」을, ③b 가 「그것까지 걷으면 **바이트까지 같다**」를 본다 — 둘을 같이 적는 이유는 전수 트리 해시 하나만으로는 evidence 때문에 **영원히 다르게** 나오기 때문이다 |
| ④⑤⑥ | 컴파일 · test · 게이트 | **③b 로 갈음한다.** 되돌린 트리가 base `0768bda1` 와 **바이트까지 같은 트리**이므로 그 트리에서 다시 재는 것은 base 를 재는 것과 같다. 갈음 근거는 「HEAD 초록」이 **아니라** 트리 동일성이다(base 는 머지된 main 커밋이다) |

확인은 **「내 표지 0 · 남의 표지 보존」 계수**로 한다 — 내 줄이 사라졌는가와 남의 줄이 남았는가
둘 다 본다(종료 코드만으로 완료를 주장하지 않는다). r3 실측:

| 방향 | 계수 |
|---|---|
| 내 표지 — 되돌린 트리에서 `notification_inbox`·`NotificationInbox` 를 담은 파일 | **0** |
| 남의 표지 — chore 가 더한 allowlist 줄(go-jose · 그 CVE 번호) | **6 보존** |

**실측 HEAD 와 판정 SHA 의 대조**: verifier 가 보는 것은 둘이 같은지가 아니라(evidence 커밋이
늘 뒤에 오므로 영원히 다르다) **그 사이에 되돌림 대상이 움직였는가**다 —
`git diff --name-only <실측 HEAD>..<판정 SHA> -- <이 파일이 되돌리는 경로들>` 이 빈 출력이면
유효하다.
