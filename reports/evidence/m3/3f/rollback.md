# M3/3F — rollback.md

## 대상 경로 (기계 산출)

`git diff --name-status 69e2afee6e9c49b9e44b9e8970408daabd6f695a..HEAD -- <in_scope 경로>`
— in_scope 는 scope.md 그대로(koneps 전체·procurement 4파일+P-13 승인 3파일+신규 파일·
V5 마이그레이션·persistence 전체·gate-tests.properties).

| 상태 | 경로 |
| --- | --- |
| M | adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsIdentifierMasking.kt |
| M | adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSource.kt |
| M | adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptor.kt |
| M | adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcOpeningResultRepository.kt |
| A | adapters/src/main/kotlin/bidvector/adapters/persistence/OpeningCompleteAxisCodec.kt |
| M | adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt |
| A | adapters/src/main/resources/db/migration/V5__opening_complete_axis.sql |
| A | adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpeningCompleteSourceTest.kt |
| M | adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSourceTest.kt |
| M | adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptorTest.kt |
| M | adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt |
| M | adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt |
| M | adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt |
| A | adapters/src/test/kotlin/bidvector/adapters/persistence/OpeningCompleteAxisRepositoryTest.kt |
| M | config/quality/gate-tests.properties |
| M | procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt |
| M | procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt |
| A | procurement/src/main/kotlin/bidvector/procurement/KonepsOpeningCompleteFieldContracts.kt |
| M | procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt |
| M | procurement/src/main/kotlin/bidvector/procurement/Ports.kt |
| M | procurement/src/main/kotlin/bidvector/procurement/RawObservation.kt |
| M | procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt |
| A | procurement/src/test/kotlin/bidvector/procurement/OpeningCompleteAxisTest.kt |

A=6(삭제 대상), M=17(base 로 restore 대상), 총 23경로(verifier r1 L-1 뒤 산문 수치 정정 —
표·restore 명령은 처음부터 옳았다). **라운드마다 파일이 늘거나 줄면
이 명령을 다시 돌려 목록을 갱신한다.**

## 되돌리는 방법

```
git restore --source=69e2afee6e9c49b9e44b9e8970408daabd6f695a --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsIdentifierMasking.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptor.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcOpeningResultRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/OpeningCompleteAxisCodec.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/resources/db/migration/V5__opening_complete_axis.sql \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpeningCompleteSourceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSourceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptorTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/OpeningCompleteAxisRepositoryTest.kt \
  config/quality/gate-tests.properties \
  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt \
  procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt \
  procurement/src/main/kotlin/bidvector/procurement/KonepsOpeningCompleteFieldContracts.kt \
  procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt \
  procurement/src/main/kotlin/bidvector/procurement/Ports.kt \
  procurement/src/main/kotlin/bidvector/procurement/RawObservation.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/OpeningCompleteAxisTest.kt
```

`--source`에 없는 경로(A 6건)는 이 명령이 삭제한다 — 별도 `git rm`이 필요 없다.
`git checkout <base> -- <경로>`는 쓰지 않는다(신규 경로마다 pathspec 오류로 exit 1).
하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서(`reports/evidence/m3/3f/**`·
`reports/evidence/m3/3a/policy-values.md`·`docs/discovery/capability-map.md`)는 되돌리지
않는다 — 문서 레인 소유이거나 이 slice의 in_scope가 아니다(P-13·D-3F-7 승인 지식은 코드를
걷어도 남아야 한다).

## 임시 clone 실측(2026-09-09, verifier r2 N-1 수정 뒤 재실측 — 대상 경로 불변, 23경로 그대로)

N-1 수정(`draw_numbers_valid_range_max` 컬럼 추가 등)은 이미 in_scope 로 등재된 8개
경로만 고쳤다 — 신규 경로가 없어 위 표·명령을 다시 산출할 필요가 없었다(기계 산출 재확인
결과 23경로 그대로).

- `git clone --quiet . <scratchpad>/3f-n1-rollback-<random>` → 위 `git restore` 실행 →
  **exit 0**.
- 결과: D=6(신규 파일 삭제됨) · M=17(base 상태로 복원됨) — 기계 산출 목록과 정확히 일치.
- `git diff 69e2afee6e9c49b9e44b9e8970408daabd6f695a -- <같은 23경로>` → **0줄**(완전히
  base와 같음, `config/quality/gate-tests.properties` 등 M 항목 포함).
- 되돌린 트리 `./gradlew :procurement:compileKotlin :procurement:compileTestKotlin
  :adapters:compileKotlin :adapters:compileTestKotlin` → **exit 0**.
- 되돌린 트리 `./gradlew :procurement:test --tests 'bidvector.procurement.*'` → **exit 0**.
- 되돌린 트리 `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` → **exit 0**
  (Docker 불필요 축).
- 임시 clone은 검증 뒤 삭제했다(`rm -rf`, 잔여 없음 확인).

persistence(Testcontainers) test 는 되돌린 트리에서 별도로 재실행하지 않았다 — `V5`
마이그레이션 파일 자체가 삭제 대상 목록에 있어(A) `git restore`가 지우고 나면 되돌린
트리에는 V1~V4만 남는다. compile 성공은 코드 경로가 그 스키마와 다시 정합함을 이미
증명한다(3E rollback.md가 같은 논리로 이 축을 생략한 전례).

## `V5` 삭제가 되돌림이 아닌 경우 — 적용 이력이 있는 DB

위 되돌림은 **코드·마이그레이션 파일** 롤백이다. **이미 `V5`가 migrate된 PostgreSQL
인스턴스(로컬 개발 DB 등, `flyway_schema_history`에 그 행이 있는 상태)에서는 파일 삭제만으로
스키마가 되돌아가지 않는다** — Flyway는 적용 이력을 검증하므로, 파일을 지운 채로
`flyway migrate`를 돌리면 이력과 파일 집합이 어긋나 `validate()`가 실패한다
(`CleanMigrationTest`의 flyway validate test가 그 어긋남을 잡는다).

**개발 DB 재생성 절차**(운영 DB는 out_of_scope, 3E rollback.md와 같은 절차):

1. 대상 PostgreSQL 인스턴스의 `public` 스키마를 `DROP SCHEMA public CASCADE; CREATE SCHEMA
   public;`로 비우거나 컨테이너를 폐기하고 새로 만든다(Testcontainers 경로는 test마다
   이미 이렇게 한다).
2. 위 「되돌리는 방법」으로 코드·마이그레이션 파일을 base로 되돌린 뒤 `flyway migrate`를
   V1부터 다시 실행한다(V5가 삭제됐으므로 V1~V4까지만 적용된다).
3. `V5`가 적용된 뒤 실제로 쓰인 데이터(`opening_result`의 `opening_rank_one_*`·
   `draw_numbers_*` 신규 컬럼값)는 스키마와 함께 사라진다 — 이 slice는 그 데이터의 별도
   백업·이관 절차를 정하지 않는다(out_of_scope, 3E와 같은 경계).

운영 DB(M6 6C 소관)에 `V5`가 적용된 뒤의 롤백 절차(down-migration 등)는 이 slice의 범위
밖이다.
