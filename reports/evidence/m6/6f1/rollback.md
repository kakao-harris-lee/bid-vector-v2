# M6/6F-1 — rollback.md

## 되돌릴 대상(기계 산출)

`git diff --name-status c4d09cc..HEAD -- . ':!reports/evidence'`가 낸 열둘(라운드마다
파일이 늘면 이 명령을 다시 돌린다):

```
M  adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A  adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt
A  adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt
A  adapters/src/main/resources/db/migration/V9__operator_strategy.sql
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt
A  adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt
M  milestone-6.md
```

`PersistenceAdapterDependencyTest.kt`는 이력에는 나타나지만(임시로 열었다가 계약 갱신
뒤 원복, checklist.md 「scope 이탈과 정정」) base 대비 순 diff가 비어 있어 이 목록에
없다 — `git diff c4d09cc..HEAD -- adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceAdapterDependencyTest.kt`
가 공백을 실측 확인.

**공유 파일 여부** — 이 range(`c4d09cc..HEAD`) 안에서 위 열둘을 만진 커밋은 전부 이
slice(m6-6f1) 자신이다(다른 slice와의 겹침 없음, `git log --oneline c4d09cc..HEAD --
<파일>`로 파일마다 확인). 6B-1(worktree `bid-vector-v2-m6b`)이 같은 파일들 중 일부
(`CleanMigration*`·`PersistenceTestSupport.kt`·`adapters.strategy` 신규 파일)를 **자기
브랜치에서** 별도로 편집하고 있으나, 그 커밋들은 이 range 밖(다른 브랜치)이라 여기서는
겹치지 않는다 — 두 브랜치가 병합될 때(오케스트레이터 소관) 다시 판정해야 하는 대상이다.

**하네스 레인 변경**: `git log --oneline c4d09cc..HEAD -- CLAUDE.md .claude/ docs/harness/`
— 없음.

## 절차

```bash
git restore --source=c4d09cc --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt \
  adapters/src/main/resources/db/migration/V9__operator_strategy.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt \
  milestone-6.md
```

`--source`에 없는 다섯(신규 파일)은 이 명령 한 번으로 삭제된다 — 별도 `git rm` 불필요.
`git checkout <base> --`는 쓰지 않는다(신규 경로마다 pathspec 오류로 exit 1이 나는
문제가 이 저장소에서 이미 실측됨).

## 임시 clone 실측(2026-09-17)

`git clone` + `git checkout 48133c9`(이 slice 최종 HEAD)로 만든 임시 clone에서 위
명령을 그대로 실행했다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① restore | 위 명령 | exit 0 |
| ② 내 줄 사라짐 | `git diff c4d09cc -- <열두 경로>` | 0줄(공백) — in_scope 전부 base와 동일 |
| ②b 신규 디렉터리 잔존 확인 | `find .../adapters/strategy` | 빈 결과 — `adapters.strategy` 패키지 흔적 없음 |
| ②c 하네스 무변경 | `git diff --name-only c4d09cc -- CLAUDE.md .claude/` | 0줄 |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ test | `./gradlew --no-daemon :adapters:test` | exit 0(전 test, Testcontainers 새 컨테이너로 V1~V7만 migrate) |
| ⑤b 왕복 스모크 | `./gradlew --no-daemon :adapters:test --tests '*NoticeFindRoundTripTest*' --rerun-tasks` | exit 0 — 되돌린 트리가 기존 canonical 왕복도 정상 유지 |
| ⑥ 게이트 | `./gradlew --no-daemon check` | exit 0(전 모듈) |

**마이그레이션 비대칭(계약 그대로, 실제 위험)**: 위 실측은 전부 Testcontainers의 **매
실행 새 컨테이너**(V9 없이 처음부터 migrate)라 이 비대칭을 재현하지 않는다 — 그래서
재현하지 않는다는 사실 자체가 여기 실측이다. 실제 배포 DB에 V9가 이미 적용된 뒤 이
rollback으로 코드만 되돌리면: `operator_strategy`·`operator_strategy_revision` 두
표는 DB에 그대로 남고(코드가 그 표를 다시 지우지 않는다), Flyway는 코드에 없는 미래
버전 파일을 알지 못하므로 `flyway.validate()`가 그 DB에서 실패하지는 않는다(V9는 단순히
"적용된 적 없는 파일"이 아니라 "적용 이력은 있는데 로컬에 파일이 없는" 상태가 되어
오히려 **Flyway가 이력 불일치로 에러**를 낸다 — `Detected applied migration not
resolved locally: 9`). **이 코드 되돌림만으로는 배포 DB에서 안전하지 않다** — 실제
운영에서 되돌리려면 (a) 두 표를 `DROP TABLE`하거나 (b) `flyway repair`로 이력에서
V9 항목을 제거해야 하고, 둘 다 데이터 삭제를 수반해 **사용자 승인 대상**이다(scope.md
rollback 절 그대로).

## 게이트 결과가 초록이 아닐 때

위 실측에서 ④⑤⑥ 전부 초록이었다(로컬 clean-checkout 시나리오). 초록이 아닌 유일한
알려진 조건은 위에서 서술한 **이미 V9가 적용된 실제 DB**뿐이고, 그 경우의 보완 경로는
바로 위 문단의 (a)/(b)다 — 둘 다 사용자 승인 없이 이 rollback 절차만으로 자동 수행하지
않는다.
