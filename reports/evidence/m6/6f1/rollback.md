# M6/6F-1 — rollback.md

**정본은 base `8652893`(계약 갱신 (7)) 기준 아래 두 절이다.** 착수 시점(base `c4d09cc`)
초판은 rebase로 무효가 됐다 — 사실은 문서 맨 아래 「부록」에 남기되 실행 가능한 명령
형태로는 남기지 않는다(verifier r2 HIGH-1: 그 초판을 그대로 실행하면 6B-1이 병합한
의존 게이트 파일이 삭제되고 컴파일이 깨진다 — 팀장 지시로 재정정: 「예측은 보존하고
고장난 실행 절차는 보존하지 않는다」, 이 문서 하단 「팀장 오류 기록」 참고).

## 되돌릴 대상(기계 산출, base `8652893`)

`git diff --name-status 8652893..HEAD -- . ':!reports/evidence'`가 낸 **열셋**(라운드마다
파일이 늘면 이 라운드의 **마지막 내용 커밋 뒤에** 이 명령을 다시 돌린다 — 너무 일찍
돌리면 6B-1이 두 번, 이 slice가 code-reviewer 라운드에서 한 번 겪은 것과 같은 자리에서
목록이 곧 낡는다. 2026-09-18 code-reviewer HIGH: Codex 회귀 라운드가 신설한
`JdbcStrategyRepositoryCodexRegressionTest.kt`가 이 목록에서 빠져 있었다 — 아래는 그
수정 뒤 재산출이다):

```
M  adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/ProvenanceCodec.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A  adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt
A  adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt
A  adapters/src/main/resources/db/migration/V9__operator_strategy.sql
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryCodexRegressionTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt
M  milestone-6.md
```

`StrategyAdapterDependencyTest.kt`는 **이 목록에 없다** — base(`8652893`)가 6B-1의
병합으로 이미 그 파일을 담고 있어(이번 라운드의 D-6F1-8 결함을 실제로 잡은 그 게이트)
이 range의 diff에 잡히지 않는다. 그 파일을 목록에 넣고 되돌리면 6B-1의 게이트가
삭제된다 — 아래 「부록」이 바로 그 고장을 실측한 기록이다.

`PersistenceAdapterDependencyTest.kt`는 커밋 히스토리에는 나타나지만(임시로 열었다가
계약 갱신 뒤 원복, checklist.md 「scope 이탈과 정정」) base(`8652893`) 대비 순 diff가
비어 있어 이 목록에 없다(왕복 실측).

**공유 파일 여부** — 위 열셋 중 `CleanMigration*`·`PersistenceTestSupport.kt`는 6B-1도
편집한 파일이지만, base가 이미 `8652893`(6B-1 병합 후)이라 이 range의 diff는 **이
slice가 그 위에 더한 줄만** 담는다 — 6B-1의 줄과 물리적으로 섞이지 않는다.

**하네스 레인 변경**: `git log --oneline 8652893..HEAD -- CLAUDE.md .claude/ docs/harness/`
— 없음.

## 절차

```bash
git restore --source=8652893 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/ProvenanceCodec.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt \
  adapters/src/main/resources/db/migration/V9__operator_strategy.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryCodexRegressionTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  milestone-6.md
```

`StrategyAdapterDependencyTest.kt`는 목록에서 **의도적으로 빠졌다** — base(`8652893`)에
이미 있는 6B-1 소유 파일이라 이 slice의 rollback이 손대지 않는다(손대면 그 게이트가
사라진다). `--source`에 없는 나머지 다섯(신규 파일 + 신규 test 둘)은 이 명령 한 번으로
삭제된다 — 별도 `git rm` 불필요. `git checkout <base> --`는 쓰지 않는다(신규 경로마다
pathspec 오류로 exit 1이 나는 문제가 이 저장소에서 이미 실측됨).

## 임시 clone 실측(2026-09-18, code-reviewer HIGH 수정 뒤 — 이 축이 정본)

`git clone --no-hardlinks`로 만든 버릴 clone에서 HEAD(`9dc5bf5`, Codex 회귀 라운드
전체 포함) 기준으로 위 명령을 그대로 실행했다. **이전 실측(HEAD `7bef521`)은 그 뒤
네 커밋(`b22f010`·`2e41ab2`·`5d9890f`·`da36f08`·`068f6f5`·`602a169`·`9dc5bf5`)이
반영되지 않은 낡은 기록이었다** — code-reviewer 가 문서의 명령을 그대로 실행해
`JdbcStrategyRepositoryCodexRegressionTest.kt`가 목록 밖이라 되돌려지지 않고 남고, 그
파일이 참조하는 것들이 지워져 **`:adapters:compileTestKotlin` 이 실패**함을 실측했다
(HIGH, 세 번째 반복 — 선행 slice 에서 둘, verifier 에서 한 번, 이번이 세 번째).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① restore | 위 명령 | exit 0 |
| ② 내 줄 사라짐 | `git diff 8652893 -- <위 열세 경로>` | 0줄 |
| ② D/M 수 | `git status --porcelain` | **D 5 · M 8** = 13(기계 목록과 일치) |
| ②b 신규 디렉터리 잔존 | `find adapters/.../strategy` (main+test) | `JdbcEditSessionRepository.kt`·`EditSessionRow.kt`·`StrategyAdapterDependencyTest.kt`(전부 6B-1 소유, base에서 옴)만 남고 이 slice의 `JdbcStrategyRepository.kt`·`StrategyRow.kt`는 삭제됨 |
| ②c 남의 줄 남음 | `test -f .../StrategyAdapterDependencyTest.kt` · `grep -c edit_session CleanMigrationTest.kt`·`PersistenceTestSupport.kt` | 파일 존재 · 6건·2건(6B-1 몫 그대로) |
| ②d 내 표 소멸 | `grep -c operator_strategy CleanMigrationTest.kt` | 0 |
| ②e 하네스 무변경 | `git diff --name-only 8652893 -- CLAUDE.md .claude/ docs/harness/` | 0줄 |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | **exit 0**(수정 전엔 `JdbcStrategyRepositoryCodexRegressionTest.kt`가 남아 `Unresolved reference` 다섯으로 exit 1 — code-reviewer 실측 그대로 재현 확인) |
| ⑤ test | `./gradlew --no-daemon :adapters:test`(필터 없이 전체) | exit 0 |
| ⑥ 게이트 | `./gradlew --no-daemon check` | **exit 0**(전 모듈, 346 tasks) |

**양방향 차집합**: 기계 산출(`git diff --name-status 8652893..HEAD`)과 이 문서의
목록·`git restore` 인자를 `comm`으로 대조 — diff-only 0 · doc-only 0(둘 다 13).

신규 디렉터리 잔존 확인의 기대값 — `adapters.strategy` 패키지에는 6B-1이 이미 세션
파일 둘을 심어 뒀으므로 「이 slice의 파일 둘만 사라지고 6B-1의 파일 셋은 남는다」가
맞는 판독이다(패키지 자체가 사라지는 것이 기대값이 아니다).

## 마이그레이션 비대칭(실제 DB의 위험 — base 선택과 무관)

위 실측은 전부 Testcontainers의 **매 실행 새 컨테이너**(V9 없이 처음부터 migrate)라 이
비대칭을 재현하지 않는다 — 재현하지 않는다는 사실 자체가 여기 실측이다. 실제 배포
DB에 V9가 이미 적용된 뒤 이 rollback으로 코드만 되돌리면: `operator_strategy`·
`operator_strategy_revision` 두 표는 DB에 그대로 남고(코드가 그 표를 다시 지우지
않는다), Flyway는 코드에 없는 미래 버전 파일을 알지 못하므로 `flyway.validate()`가
그 DB에서 실패하지는 않는다(V9는 단순히 "적용된 적 없는 파일"이 아니라 "적용 이력은
있는데 로컬에 파일이 없는" 상태가 되어 오히려 **Flyway가 이력 불일치로 에러**를 낸다
— `Detected applied migration not resolved locally: 9`). **이 코드 되돌림만으로는
배포 DB에서 안전하지 않다** — 실제 운영에서 되돌리려면 (a) 두 표를 `DROP TABLE`하거나
(b) `flyway repair`로 이력에서 V9 항목을 제거해야 하고, 둘 다 데이터 삭제를 수반해
**사용자 승인 대상**이다(scope.md rollback 절 그대로).

## 게이트 결과가 초록이 아닐 때

위 실측에서 ④⑤⑥ 전부 초록이었다(로컬 clean-checkout 시나리오). 초록이 아닌 유일한
알려진 조건은 위에서 서술한 **이미 V9가 적용된 실제 DB**뿐이고, 그 경우의 보완 경로는
바로 위 문단의 (a)/(b)다 — 둘 다 사용자 승인 없이 이 rollback 절차만으로 자동 수행하지
않는다.

---

## 부록 — 폐기된 초판 절차(base `c4d09cc`, **실행 금지**, verifier r2 HIGH-1로 무효화)

착수 시점(2026-09-17)에는 base가 `c4d09cc`였고 그 base로 만든 되돌릴 대상 열둘·`git
restore` 절차·임시 clone 실측(④⑤⑥ 전부 초록)이 있었다. **rebase(2026-09-18)로 그
base가 낡았고, 그 절차를 그대로 실행하면 다음이 일어난다**(verifier r2가 판정 대상
HEAD에서 버릴 clone으로 실행 실측):

1. 6B-1이 `main`에 병합한 `StrategyAdapterDependencyTest.kt`(base `c4d09cc`에는 없던
   파일이자 **이번 라운드의 D-6F1-8 결함을 실제로 잡은 그 게이트**)가 **삭제**된다 —
   그 초판 절차가 `--source=c4d09cc`로 만들며, 그 base엔 이 파일이 없어 restore가
   지운다.
2. `CleanMigrationTest.kt` 등 공유 파일에서 6B-1이 더한 줄(`edit_session` 관련)이
   함께 지워진다.
3. **되돌린 트리가 컴파일되지 않는다** — `JdbcEditSessionRepository.kt`에서
   `Unresolved reference 'SELECT_EDIT_SESSION'`/`'UPSERT_EDIT_SESSION'`(compile exit 1).

목록 자체도 낡아서, 옛 base(`c4d09cc`)로 다시 산출하면 6B-1·M3 후속 등이 섞여 49항목이
나오고, 진짜 base(`8652893`)로 산출한 12항목과도 다르다(옛 문서의 열둘은
`StrategyAdapterDependencyTest.kt`를 `A`로 담고 `ProvenanceCodec.kt`를 빠뜨렸다 —
이번 라운드가 만든 파일이 반영되지 않은, 6B-1이 두 번 걸린 바로 그 함정이다).

**이 「부록」은 실행 절차가 아니라 사실 기록이다** — 위에서 서술한 세 가지 고장이
일어난다는 것 자체가 verifier r2 HIGH-1의 근거이므로 문장으로만 남긴다. 실행 가능한
명령·목록 형태로는 복원하지 않는다.

### 팀장 오류 기록 (2026-09-18, 재정정)

verifier r2 HIGH-1 수정 라운드에서 「병합 계획」 절의 예측 대조를 두고 팀장이 「옛 절을
지우지 말라」고 지시했고, 그 지시를 이 rollback.md에도 그대로 적용해 옛 `git restore
--source=c4d09cc` 블록을 실행 가능한 코드 블록으로 문서 상단에 남겼다. 그 결과 문서를
위에서부터 읽고 처음 만나는 명령이 **고장난 절차**가 되는 결함이 생겼다 — rollback
문서는 사고 시 급히 참조하는 문서라 이 결함 자체가 위험이다. **팀장 오류**: 「예측은
보존할 값이 있고(다음 slice에 다음번 병합 리허설의 근거가 된다), 고장난 실행 절차는
보존할 값이 없다」는 구분 없이 「지우지 말라」를 모든 문서에 일괄 적용하도록 지시했다.
이 문서는 그 구분에 따라 옛 절차를 실행 불가능한 산문(위 「부록」)으로 바꾸고, 실행
가능한 유일한 절차를 문서 상단(새 base)으로 옮겼다.
