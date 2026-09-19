# M6/6F-4 — rollback.md

**실측 HEAD: `6f8466f1`**(마지막 산출물 커밋 — 이 뒤에 붙는 커밋은 `reports/evidence/m6/6f4/**`
만 편집하며 production 코드를 바꾸지 않는다. base_sha는 흡수 병합 뒤 갱신값 `ede5d5b`다).

## 목록(기계 산출)

```
$ git diff --name-status ede5d5b..6f8466f1 -- . ':!reports/evidence'
M	adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A	adapters/src/main/resources/db/migration/V11__notice_title.sql
M	adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt
M	adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M	adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt
M	adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt
M	procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt
M	procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt
A	procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt
M	strategy/src/main/kotlin/bidvector/strategy/Text.kt
A	strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt
```

`A` 3(신규) + `M` 12(수정) = 15경로. `reports/evidence/m6/6f4/**`는 이 목록에서 뺐다 —
되돌리지 않는다. **라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을 재산출한다.**

## 공유 파일 셋 — 겹침 확인과 절차

`git log --oneline ede5d5b..6f8466f1 -- <파일>`로 이 range 안에서 각 파일을 만진 커밋을
먼저 나열했다. 유일하게 `Sql.kt`가 병합 커밋(`6154e2d1`)에도 나타난다 — 6F-2가 흡수 병합으로
더한 `SELECT_OPEN_CANDIDATES`가 같은 파일에 있기 때문이다. 그러나 **`base_sha`가 이미
`ede5d5b`(6F-2 병합 뒤 값)라 6F-2의 기여는 base 자체에 포함돼 있다** — 실측:

```
$ git diff ede5d5b..6f8466f1 -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
```

출력은 `notice_title` 세 곳(NOTICE_COLUMNS·INSERT_NOTICE·UPDATE_NOTICE)뿐이고
`SELECT_OPEN_CANDIDATES`는 등장하지 않는다(이미 base에 있어 diff에 안 잡힌다) — **hunk 격리
없이 `--source=ede5d5b` 전체 복원으로 충분**하다. 나머지 14경로는 이 range 안에서 이 slice의
커밋만 만졌다(단독 저자).

## 복원 명령

```
git restore --source=ede5d5b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/resources/db/migration/V11__notice_title.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt \
  procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt \
  procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt \
  procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt \
  strategy/src/main/kotlin/bidvector/strategy/Text.kt \
  strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt
```

`A` 항목 셋(`V11__notice_title.sql`·`NoticeTitle.kt`·`WatchTextAssemblyTest.kt`)은
`--source`에 없어 `git restore`가 자동으로 삭제한다(신설 파일은 `git rm` 효과 — 별도
`git rm` 명령이 필요 없다). `M` 항목 열둘은 base 내용으로 복원된다.
`reports/evidence/m6/6f4/**`는 되돌리지 않는다. `checkout --`는 쓰지 않는다(pathspec이
base에 없는 경로에서 exit 1을 내 아무것도 적용되지 않는다).

## 임시 clone 실측(①~⑥, `git clone .` → `6f8466f1` checkout → 복원, 전경 실행)

| # | 확인 | 명령 | 결과 |
| --- | --- | --- | --- |
| ① 복원 exit | 위 `git restore` | exit 0 |
| ② D/M 수 | `git status --porcelain -- . ':!reports/evidence'` | `D` 3 + `M` 12 = 15 — 위 목록과 정확히 일치 |
| ③ diff 빈 것 | `git diff ede5d5b -- <같은 열다섯 경로>` | 출력 없음(exit 0) — **내 줄이 사라졌다** |
| ④ compile | `./gradlew --no-daemon :procurement:compileKotlin :strategy:compileKotlin :adapters:compileKotlin :adapters:compileTestKotlin :strategy:compileTestKotlin` | exit 0 |
| ⑤ test | `./gradlew --no-daemon :adapters:test :strategy:test :procurement:test` | BUILD SUCCESSFUL(exit 0), 45 actionable tasks(9 executed·3 from cache·33 up-to-date) — 되돌린 트리에서 6F-2·기존 notice test도 그대로 선다(**남의 줄이 남았다** — `strategy:test`·`procurement:test`는 이 slice가 되돌리지 않은 다른 test까지 포함해 통과) |
| ⑥ 게이트(`check`) | `./gradlew --no-daemon check` | BUILD SUCCESSFUL(exit 0), 346 actionable tasks(188 executed·113 from cache·45 up-to-date) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** — ③이 `git diff ede5d5b`가 빈 것을 직접
재므로, 되돌린 트리는 base(`ede5d5b`)의 해당 열다섯 경로와 파일 내용이 완전히 동일하다.
④⑤⑥이 그 트리가 서고·통과하고·게이트를 지난다는 것까지 확인한다.

## 하네스 레인 절 갱신 필요성

`git log --oneline ede5d5b..6f8466f1 -- CLAUDE.md .claude/`는 빈 출력이다(하네스 레인 커밋
혼입 없음) — scope.md의 「하네스 레인 변경: 착수 시점 없음」은 이 시점까지도 사실이다.
