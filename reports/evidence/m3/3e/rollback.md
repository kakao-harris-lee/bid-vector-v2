# M3/3E — rollback.md

## 대상 경로 (기계 산출)

`git diff --name-status 9948c6e4056bbf71fa6683aa67d30c2a49fc6eae..HEAD -- <in_scope 경로>` —
in_scope는 scope.md 그대로(procurement 3파일·adapters/persistence 전체·V4 migration·
config/quality/gate-tests.properties, koneps는 무변경이라 목록에 없음).

| 상태 | 경로 |
| --- | --- |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcOpeningResultRepository.kt` |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcRawObservationStore.kt` |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/ObservationKeyDerivation.kt` |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt` |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt` |
| A | `adapters/src/main/resources/db/migration/V4__opening_result_rows.sql` |
| A | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt` |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt` |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt` |
| A | `adapters/src/test/kotlin/bidvector/adapters/persistence/OpeningReservePriceRepositoryTest.kt` |
| A | `adapters/src/test/kotlin/bidvector/adapters/persistence/RowDiscriminatorRawKeyTest.kt` |
| M | `config/quality/gate-tests.properties` |
| M | `procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt` |
| M | `procurement/src/main/kotlin/bidvector/procurement/RawObservationStore.kt` |
| A | `procurement/src/test/kotlin/bidvector/procurement/OpeningResultFactSlotsTest.kt` |
| A | `procurement/src/test/kotlin/bidvector/procurement/RowDiscriminatorTest.kt` |

A=6(삭제 대상), M=10(base로 restore 대상). **라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을
갱신한다** — 손으로 쓰지 않는다.

## 되돌리는 방법

```
git restore --source=9948c6e4056bbf71fa6683aa67d30c2a49fc6eae --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcOpeningResultRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcRawObservationStore.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/ObservationKeyDerivation.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/resources/db/migration/V4__opening_result_rows.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/OpeningReservePriceRepositoryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/RowDiscriminatorRawKeyTest.kt \
  config/quality/gate-tests.properties \
  procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt \
  procurement/src/main/kotlin/bidvector/procurement/RawObservationStore.kt \
  procurement/src/test/kotlin/bidvector/procurement/OpeningResultFactSlotsTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/RowDiscriminatorTest.kt
```

`--source`에 없는 경로(A 6건)는 이 명령이 삭제한다 — 별도 `git rm`이 필요 없다.
`git checkout <base> -- <경로>`는 쓰지 않는다(신규 경로마다 pathspec 오류로 exit 1).
하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서(`reports/evidence/m3/3e/**`)는 **되돌리지
않는다** — scope.md·checklist.md·commands.md·rollback.md 자체는 이 slice의 산출 기록이라
남긴다(승인 여부와 무관하게 리뷰 대상).

## 임시 clone 실측(2026-09-08)

- `git clone --quiet . <scratchpad>/3e-rollback-<random>` → 위 `git restore` 실행 →
  **exit 0**.
- 결과: D=6(신규 파일 삭제됨) · M=10(base 상태로 복원됨) — 기계 산출 목록과 정확히 일치.
- `git diff 9948c6e..HEAD -- <같은 16경로>` → **0줄**(완전히 base와 같음).
- 하네스 경로(`CLAUDE.md` `.claude/`) `git status --porcelain` → **빈 출력**(HEAD 그대로).

## 되돌린 트리 빌드·test 실측(2026-09-08 규격)

- `./gradlew :adapters:compileKotlin :procurement:compileKotlin` → **exit 0**.
- `./gradlew :adapters:test :procurement:test` → **exit 0**(Testcontainers 포함 — 되돌린
  트리는 V4가 없으므로 컨테이너가 V1~V3만 migrate하고, `CleanMigrationTest` 등도 base
  버전으로 복원돼 그 스키마와 대조한다 — 정합).

임시 clone은 검증 뒤 삭제했다(`rm -rf`, 잔여 없음 확인).

## `V4__opening_result_rows.sql` 삭제가 되돌림이 아닌 경우 — 적용 이력이 있는 DB

위 되돌림은 **코드·마이그레이션 파일** 롤백이다. **이미 `V4`가 migrate된 PostgreSQL 인스턴스
(예: 로컬 개발 DB, `flyway_schema_history`에 V4 행이 있는 상태)에서는 파일 삭제만으로 스키마가
되돌아가지 않는다** — Flyway는 적용 이력을 보고 검증하므로, 파일을 지운 채로 `flyway migrate`를
돌리면 `flyway_schema_history`와 파일 집합이 어긋나 `validate()`가 실패한다
(`CleanMigrationTest`의 「flyway validate가 통과한다」 test가 그 어긋남을 실제로 잡는다).

**개발 DB 재생성 절차**(운영 DB는 out_of_scope):

1. 해당 PostgreSQL 인스턴스의 대상 스키마(`public`)를 `DROP SCHEMA public CASCADE; CREATE
   SCHEMA public;`로 비우거나, 컨테이너 자체를 폐기하고 새로 만든다(Testcontainers 경로는
   테스트마다 이미 이렇게 한다 — 운영 코드 경로가 아니다).
2. 위 「되돌리는 방법」으로 코드·마이그레이션 파일을 base로 되돌린 뒤 `flyway migrate`(또는
   `PersistenceTestSupport` companion의 `init` 블록과 같은 경로)를 V1부터 다시 실행한다.
3. `V4`가 적용된 뒤 실제로 쓰인 데이터(`opening_reserve_price`·`opening_result`의 신규
   컬럼값)는 스키마와 함께 사라진다 — 이 slice는 그 데이터의 별도 백업·이관 절차를 정하지
   않는다(out_of_scope, backfill과 같은 경계).

운영 DB(M6 6C 소관)에 이미 V4가 적용된 뒤의 롤백 절차(down-migration 등)는 이 slice의 범위 밖이다.
