# M6/6F-4 — rollback.md

**실측 HEAD: `bdfafc74`**(수정 라운드 2의 마지막 산출물 커밋 — base_sha는 흡수 병합 뒤 갱신값
`ede5d5b` 그대로다. 이 라운드는 verifier r2·code-reviewer 판정에 답한 커밋 다섯을 더했다:
V11 CHECK 정규식 교체, CHECK/타입 일치 test + 공고명 merge guard 3단계 test, `NoticeTitle`
단위 test, KDoc 정정 둘, checklist 갱신 하나. `reports/evidence/m6/6f4/**` 편집 커밋은 이 목록
계산에서 뺀다).

## 목록(기계 산출)

```
$ git diff --name-status ede5d5b..bdfafc74 -- . ':!reports/evidence' ':!milestone-6.md' ':!.claude'
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
A	procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt
M	strategy/src/main/kotlin/bidvector/strategy/Text.kt
A	strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt
```

`A` 4(신규, 라운드 2에서 `NoticeTitleTest.kt` 한 편 늘었다) + `M` 12(수정) = 16경로.
`reports/evidence/m6/6f4/**`·`milestone-6.md`·`.claude/`는 이 목록에서 뺐다(되돌리지 않는다
— 뒤 둘은 팀장 레인이 운영자 승인 하에 같은 range에 둔 것이고 scope.md 「하네스 레인 변경」
절이 등재한다). **라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을 재산출한다.**

## 공유 파일 셋 — 겹침 확인과 절차

`git log --oneline ede5d5b..bdfafc74 -- <파일>`로 이 range 안에서 각 파일을 만진 커밋을
먼저 나열했다. 유일하게 `Sql.kt`가 병합 커밋(`6154e2d1`)에도 나타난다 — 6F-2가 흡수 병합으로
더한 `SELECT_OPEN_CANDIDATES`가 같은 파일에 있기 때문이다. 그러나 **`base_sha`가 이미
`ede5d5b`(6F-2 병합 뒤 값)라 6F-2의 기여는 base 자체에 포함돼 있다** — 실측:

```
$ git diff ede5d5b..bdfafc74 -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
```

출력은 `notice_title` 세 곳(NOTICE_COLUMNS·INSERT_NOTICE·UPDATE_NOTICE)뿐이고
`SELECT_OPEN_CANDIDATES`는 등장하지 않는다(이미 base에 있어 diff에 안 잡힌다) — **hunk 격리
없이 `--source=ede5d5b` 전체 복원으로 충분**하다. 나머지 15경로는 이 range 안에서 이 slice의
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
  procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt \
  strategy/src/main/kotlin/bidvector/strategy/Text.kt \
  strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt
```

`A` 항목 넷(`V11__notice_title.sql`·`NoticeTitle.kt`·`NoticeTitleTest.kt`·
`WatchTextAssemblyTest.kt`)은 `--source`에 없어 `git restore`가 자동으로 삭제한다(신설 파일은
`git rm` 효과 — 별도 `git rm` 명령이 필요 없다). `M` 항목 열둘은 base 내용으로 복원된다.
`reports/evidence/m6/6f4/**`·`milestone-6.md`·`.claude/`는 되돌리지 않는다. `checkout --`는
쓰지 않는다(pathspec이 base에 없는 경로에서 exit 1을 내 아무것도 적용되지 않는다).

**zsh 함정(verifier r1 참고, 재확인)** — 위 열여섯 경로를 따옴표 없는 셸 변수 하나로 묶어
`-- $P` 로 넘기면 zsh는 단어 분리를 하지 않아 pathspec이 하나로 합쳐진다. 아래 실측은
배열(`paths=(...)`)과 `"${paths[@]}"`로 각 경로를 개별 인자로 넘겼다.

## 임시 clone 실측(①~⑥, `git clone .` → `bdfafc74` → 복원, 전건 실행)

| # | 확인 | 명령 | 결과 |
| --- | --- | --- | --- |
| ① 복원 exit | 위 `git restore`(배열 인자) | exit 0 |
| ② D/M 수 | `git status --porcelain -- . ':!reports/evidence'` | `D` 4 + `M` 12 = 16 — 위 목록과 정확히 일치 |
| ③ diff 빈 것 | `git diff ede5d5b -- <같은 열여섯 경로>` | 출력 없음(0줄, exit 0) — **내 줄이 사라졌다**. `grep -rn 'notice_title\|NoticeTitle\|assembleKeywordScopeText' -- <같은 경로>` = 0건(신설 파일은 이미 삭제돼 grep 대상에서 빠진다). `SELECT_OPEN_CANDIDATES`는 `Sql.kt`에 1건 유지(**남의 줄이 남았다**). `git status --porcelain -- CLAUDE.md .claude/` = 0줄(하네스 경로 무변경) |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :procurement:compileKotlin :strategy:compileKotlin :workflow:compileKotlin :app:compileKotlin` | BUILD SUCCESSFUL(exit 0), 32 actionable tasks(14 executed·18 from cache) |
| ⑤ test | `./gradlew --no-daemon :adapters:test :strategy:test :procurement:test` | BUILD SUCCESSFUL(exit 0), 45 actionable tasks(9 executed·5 from cache·31 up-to-date) — 되돌린 트리에서 6F-2·기존 notice test도 그대로 선다 |
| ⑥ 게이트(`check`) | `./gradlew --no-daemon check` | BUILD SUCCESSFUL(exit 0), 346 actionable tasks(188 executed·112 from cache·46 up-to-date) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** — ③이 `git diff ede5d5b`가 빈 것을 직접
재므로, 되돌린 트리는 base(`ede5d5b`)의 해당 열여섯 경로와 파일 내용이 완전히 동일하다.
④⑤⑥이 그 트리가 서고·통과하고·게이트를 지난다는 것까지 확인한다.

## 하네스 레인 절 갱신 필요성

`git log --oneline ede5d5b..bdfafc74 -- CLAUDE.md .claude/`는 **`.claude/skills/evidence-pack/
SKILL.md` 한 커밋(`2b61dff4`)** 을 낸다 — 팀장 레인이 운영자 승인 하에 같은 range에 둔 것이고
scope.md 「하네스 레인 변경」 절이 이미 등재했다(이 slice 산출물이 아니라 되돌림 대상도 아니다).
