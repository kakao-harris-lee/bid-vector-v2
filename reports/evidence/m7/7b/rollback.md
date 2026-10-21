# M7/7B — 되돌리기 (rollback)

**마이그레이션이 있으므로 forward-fix 가 정본이다**(6B-2 runbook 관례). 아래는 그 전제 위의 두
경우이고, 둘은 서로 다른 절차다 — 섞어 적으면 머지 뒤에 「파일을 지우면 된다」가 된다.

base: `bee7c36f`. 대상 목록은 손으로 적지 않고 **기계 산출**이다:

```bash
git diff --name-status bee7c36f..HEAD
```

---

## 경우 ① — 머지 전 (이 브랜치에서 되돌린다)

in_scope 경로 **한정** 복원이다. 다른 레인의 파일을 건드리지 않는다.

```bash
# ⓐ 수정된 기존 파일 — base 의 내용으로 되돌린다
git restore --source=bee7c36f -- \
  settings.gradle.kts \
  workflow/build.gradle.kts adapters/build.gradle.kts app/build.gradle.kts \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  adapters/src/main/kotlin/bidvector/adapters/persistence/ConnectionSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/retention/RetentionTableRegistry.kt \
  adapters/src/test/kotlin/bidvector/adapters/retention/RetentionFixture.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationIndexTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt \
  app/src/main/kotlin/bidvector/app/http/ErrorBody.kt \
  app/src/main/kotlin/bidvector/app/http/JsonValueReaders.kt \
  openapi/bidvector-operator-api.yaml \
  web/src/api/generated/operator-api.ts \
  docs/discovery/capability-map.md \
  docs/adr/0006-gradle-modules.md \
  milestone-6.md milestone-7.md \
  reports/evidence/m6/6e1/acceptance-trace.md

# ⓑ 새로 만든 것 — 지운다
rm -rf bidding \
  workflow/src/main/kotlin/bidvector/workflow/bidding \
  workflow/src/test/kotlin/bidvector/workflow/bidding \
  adapters/src/main/kotlin/bidvector/adapters/bidding \
  adapters/src/test/kotlin/bidvector/adapters/bidding \
  adapters/src/main/resources/db/migration/V21__bid_case.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/BidCaseColumnSpecs.kt \
  app/src/main/kotlin/bidvector/app/http/BidCaseController.kt \
  app/src/main/kotlin/bidvector/app/http/BidCaseRequests.kt \
  app/src/main/kotlin/bidvector/app/http/BidCaseResponses.kt \
  app/src/main/kotlin/bidvector/app/wiring/BidCaseWiring.kt
```

**개발 DB 는 파일 삭제로 돌아오지 않는다.** V21 을 이미 적용한 DB 가 있으면 재생성한다
(`tools/db-rehearsal.sh` 가 쓰는 것과 같은 경로) — Flyway `validate()` 가 「적용된 적 있는데
파일이 없다」로 거부하기 때문이다.

**`ColumnSpec` 주의**: ⓐ 의 `CleanMigrationColumnTest.kt` 복원은 그 파일 안에 `private data
class ColumnSpec` 을 되살리고, ⓑ 의 `BidCaseColumnSpecs.kt` 삭제는 top-level `internal`
선언을 함께 지운다 — **둘을 같이 해야** 컴파일된다. 한쪽만 하면 선언이 둘이거나 0 이다.

---

## 경우 ② — 머지 뒤

**파일 삭제로 되돌리지 않는다**(6F-4 규율).

1. **PR revert** — `git revert -m 1 <merge-sha>`. 코드·문서·OpenAPI·생성 타입이 함께 돌아간다.
2. **개발 DB 재생성** — revert 는 `V21` 파일을 지우므로, 그 마이그레이션이 적용된 DB 는
   Flyway `validate()` 에서 거부된다. 개발·CI DB 는 **재생성**이 정답이다.
3. **운영 DB(있다면)** — 표를 `DROP` 하지 않는다. 운영자의 투찰 기록이 들어 있을 수 있고,
   그것은 `OPERATOR_BID_HISTORY` 로 시간 기준 파기에서 빠지는 데이터다. 되돌릴 일이 생기면
   **새 V 파일**로 접근하고(필요하면 읽기만 막고) 데이터 처분은 **운영자 승인**을 받는다.

---

## 되돌리기 어려운 것 — 미리 적는다

- **`V21` 번호 자체.** 다른 레인이 V22 를 올린 뒤에는 이 번호를 재사용할 수 없다. PR 직전에
  `main` 최대 번호를 D-6A1-12 정의대로 재확인한다.
- **`notice` FK 부재는 되돌리기 쉬운 쪽이 아니다.** 나중에 FK 를 **더하는** 편집은 6B-3 파기
  sweep 과 충돌한다(RESTRICT 면 sweep 실패, CASCADE 면 운영자 기록 소실). 이 결정은 V21
  머리말과 runbook 에 사유와 함께 적혀 있고, 바꾸려면 그 두 자리를 함께 고쳐야 한다.
- **`ColumnSpec` 가시성 승격**(private 중첩 → top-level `internal`)은 다른 slice 가 그 타입을
  쓰기 시작하면 되돌리기 어려워진다. 오늘은 쓰는 곳이 두 파일뿐이다.

## 실측

경우 ①의 ⓐ~ⓑ 를 마지막 산출물 커밋에서 실측한다(결과는 `checklist.md` 의 rollback 절).
