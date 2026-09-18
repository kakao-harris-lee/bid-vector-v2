# M6/6F-6 — rollback.md

**실측 HEAD: fcd111b0187808de532cb5ced235c479435c7a5e**

## 되돌릴 커밋 범위

이 slice의 구현 커밋은 넷이다(base `ede5d5b`, `milestone-6.md` 착수 커밋 `73624ab`는
팀장 소유 — 아래 목록에 넣지 않는다, 「rollback 목록은 자기를 담은 커밋을 가리킬 수
없다」와 별개로 이 slice의 구현 커밋이 그 파일을 만지지 않았다):

| 커밋 | 내용 |
| --- | --- |
| `97cf81db` | V12 마이그레이션 신설 + `Sql.kt` 프로필 SQL 추가 |
| `8c5b6d62` | `JdbcOperatorProfileRepository`·`OperatorProfileRow` + 신설 test 셋 + `gate-tests.properties`·`CleanMigration*Test`·`PersistenceTestSupport` 갱신 |
| `e56b20b3` | `OPEN-6F6-CATEGORY-CODE-NORMALIZATION` 회귀 test 추가 |
| `fcd111b0` | ktlint/detekt 서식 수정(같은 test 파일) |

## 되돌릴 경로 목록(기계 산출)

`git diff --name-status ede5d5b..fcd111b0 -- . ':!reports/evidence'`:

```
M  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A  adapters/src/main/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepository.kt
A  adapters/src/main/kotlin/bidvector/adapters/profile/OperatorProfileRow.kt
A  adapters/src/main/resources/db/migration/V12__operator_profile.sql
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt
A  adapters/src/test/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepositoryTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/profile/ProfileAdapterDependencyTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/profile/ProfileGateRegistrationTest.kt
M  config/quality/gate-tests.properties
```

(`milestone-6.md`도 `ede5d5b..HEAD` 범위에서 `M`으로 잡히지만, 그 변경은 착수 커밋
`73624ab`(팀장 소유)뿐이다 — 이 slice의 네 구현 커밋은 그 파일을 만지지 않았다. 되돌릴
때는 이 목록만 다루고 `milestone-6.md`는 손대지 않는다.)

## 신규 파일(A) — 삭제로 완전 복귀

다섯 파일은 base에 없었다. 삭제만으로 base 상태와 동일하다:
```
rm adapters/src/main/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepository.kt
rm adapters/src/main/kotlin/bidvector/adapters/profile/OperatorProfileRow.kt
rm adapters/src/main/resources/db/migration/V12__operator_profile.sql
rm adapters/src/test/kotlin/bidvector/adapters/profile/JdbcOperatorProfileRepositoryTest.kt
rm adapters/src/test/kotlin/bidvector/adapters/profile/ProfileAdapterDependencyTest.kt
rm adapters/src/test/kotlin/bidvector/adapters/profile/ProfileGateRegistrationTest.kt
rmdir adapters/src/main/kotlin/bidvector/adapters/profile adapters/src/test/kotlin/bidvector/adapters/profile
```

**마이그레이션 비대칭 없음**(D-6F6-8) — 이 프로젝트의 적용된 DB 인스턴스는 0이다(볼륨·
컨테이너 없음, 실 JDBC URL 없음; 스키마는 일회성 Testcontainers와 CI compose에만 선다).
V12를 지우면 완전히 되돌아간다 — 롤포워드/롤백 비대칭이 생기지 않는다.

## 공유 파일(M) 여섯 — 경로별 복원

각 파일은 base(`ede5d5b`)로 경로 단위 복원한다(다른 레인이 같은 파일을 더 편집하지
않았다면 이 명령으로 충분하다 — 병행 레인 6F-4가 `Sql.kt`·`gate-tests.properties`·
`CleanMigration*Test`도 건드릴 수 있으므로, 이 slice 병합 뒤 그 레인이 아직 병합 전이면
이 복원이 안전하다. 이미 둘 다 병합됐다면 아래 「병합 후 복원」 절차를 쓴다):

```
git restore --source=ede5d5b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  config/quality/gate-tests.properties
```

### 병합 후 복원(다른 레인의 추가만 있는 편집과 섞인 경우)

여섯 파일 모두 이 slice의 편집은 **추가만**(기존 줄 무편집)이었다 — 커밋 해시 hunk
격리로 이 slice가 더한 줄만 역적용한다:

```
git diff 97cf81db~1..97cf81db -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt | git apply -R
git diff 8c5b6d62~1..8c5b6d62 -- \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  config/quality/gate-tests.properties | git apply -R
```

`--3way`는 쓰지 않는다(자동 해소 실패 시 수동 충돌 해결이 필요 — 위 hunk가 어긋나면
해당 파일만 손으로 그 slice의 추가분(`operator_profile`·`OperatorProfile*` 관련 줄)을
찾아 지운다).

## 확인 — 임시 clone에서 ①~⑥ 실측

```
git clone --no-hardlinks /Users/harris/Development/private/bid-vector-v2-m6f6 /tmp/6f6-rollback-check
cd /tmp/6f6-rollback-check
git checkout fcd111b0
<위 삭제·복원 명령 실행>
git status --porcelain -- . ':!reports/evidence'   # ③ diff 빈 것(base와 트리 동일성)
./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin  # ④ compile
./gradlew --no-daemon check                                                # ⑤⑥ test·게이트
```

갈음 판정은 「HEAD 초록」이 아니라 **트리 동일성**(되돌린 트리의 파일이 `ede5d5b`의
같은 경로 파일과 SHA 동일)으로만 — `git diff ede5d5b -- . ':!reports/evidence'`가 빈
출력이면 동일성 확인 완료. 확인은 「이 slice가 더한 줄이 사라짐」과 「병행 레인이
더한 줄(있다면)이 남음」 둘 다 본다.

## 실측 결과(2026-09-18, `/tmp/6f6-rollback-check`, `git clone --no-hardlinks`)

위 절차를 실제로 실행했다(`fcd111b0` 체크아웃 → 신규 파일 6개 삭제 → 공유 파일 6개
`git restore --source=ede5d5b` → `git add -A`).

- `git diff ede5d5b -- . ':!reports/evidence'` — **트리 동일성 확인**: 남은 차이는
  `milestone-6.md` 하나뿐이고(20줄 추가, 6F-6 착수 문단 — 팀장 커밋 `73624ab`, 이 slice의
  구현 커밋 넷은 그 파일을 만지지 않았다), 이 slice가 만진 열두 경로는 diff 0.
- `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` — exit 0,
  BUILD SUCCESSFUL(④ compile).
- `./gradlew --no-daemon check` — exit 0, BUILD SUCCESSFUL, 346 actionable tasks(⑤⑥
  test·게이트 — `gateExecutionGate`·`koverVerify` 포함 전건 통과, `operator_profile` 관련
  게이트 test는 파일이 없으므로 대상에서 자연히 빠지고 나머지 전건이 base 상태로
  깨끗하게 초록).

임시 clone은 확인 뒤 삭제했다(`rm -rf /tmp/6f6-rollback-check`).
