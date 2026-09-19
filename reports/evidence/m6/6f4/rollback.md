# M6/6F-4 — rollback.md

**실측 HEAD: `e47901ac`**(수정 라운드 4의 마지막 산출물 커밋 — **base_sha 3차 갱신값
`128f9cd3`**, scope.md 참고). PR #40 오픈 뒤 `main`이 다시 전진해(6F-5-a, PR #39, V13)
3차 흡수 병합했다. 충돌은 `CleanMigrationCheckTest.kt` 하나 — 양쪽이 같은 자리에 새
test를 더한 형태라 「둘 다 취한다」로 수동 해소했다(내 test 1 + 그쪽 test 2). V13이
먼저 병합되며 D-6F4-7의 V11→V14 재번호 전제가 충족됐다.

## 목록(기계 산출)

```
$ git diff --name-status 128f9cd3..e47901ac -- . ':!reports/evidence' ':!milestone-6.md' ':!.claude'
M	adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt
M	adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A	adapters/src/main/resources/db/migration/V14__notice_title.sql
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

`A` 4 + `M` 12 = 16경로. 목록 자체는 이전 라운드(base `edeaa9a3`)와 동일하다 — `128f9cd3`가
이미 `edeaa9a3`의 후손이라 6F-5-a가 새로 만든 파일들(`V13__notice_requirement.sql`,
`adapters/.../qualification/*`)에는 이 slice의 델타가 0이기 때문이다. `reports/evidence/
m6/6f4/**`·`milestone-6.md`·`.claude/`는 이 목록에서 뺐다. **라운드마다 파일이 늘면 이
명령을 다시 돌려 목록을 재산출한다.**

## 공유 파일 셋 — 겹침 확인과 절차(3차 흡수)

`main` 3차 흡수(`128f9cd3`, PR #39·6F-5-a) 병합 시 충돌은 **한 곳뿐**이었다 —
`CleanMigrationCheckTest.kt`. 양쪽이 같은 KDoc/`}` 사이 위치에 각자 새 `@Test`를
더한 전형적인 「둘 다 추가」 충돌이라, 내 test(`notice_notice_title_check` 본문 등식)
뒤에 닫는 `}`를 넣고 그 아래에 그쪽 KDoc + test 둘(`D-6F5-16`·`D-6F5-21`)을 이어 붙였다
— **어느 쪽도 지우지 않았다.**

| 파일 | 내 줄(notice_title 계열) | 남의 줄(notice_requirement 계열, 6F-5-a) |
| --- | --- | --- |
| `CleanMigrationCheckTest.kt`(수동 해소) | `@Test` 1(D-6F4-1) | `@Test` 2(D-6F5-16·D-6F5-21) — 전체 `@Test` 수 7로 확인 |
| `Sql.kt`(자동 병합) | 3건 | 1건(`notice_requirement` 관련) |
| `CleanMigrationColumnTest.kt`(자동 병합) | 1건 | 25건(`notice_requirement`·`notice_requirement_row` 컬럼) |
| `milestone-6.md`(자동 병합) | 24건(`6F-4`·`notice_title`·`D-6F4`) | 13건(`6F-5-a`·`D-6F5`·`notice_requirement`) |

**하나도 지워지지 않았다.** `expectedCheckCountByTable` 맵에도 `"notice" to 13`(내 것)과
`"notice_requirement" to 1`·`"notice_requirement_row" to 6`(6F-5-a 것)이 모두 살아 있다.

## 복원 명령

```
git restore --source=128f9cd3 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/resources/db/migration/V14__notice_title.sql \
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

`A` 항목 넷은 `--source`에 없어 `git restore`가 자동으로 삭제한다. `M` 항목 열둘은
base(`128f9cd3`) 내용으로 복원되며, 그 안의 `notice_requirement` 관련 내용(6F-5-a)은
base 자체에 있으므로 그대로 남는다. `reports/evidence/m6/6f4/**`·`milestone-6.md`·
`.claude/`는 되돌리지 않는다. `checkout --`는 쓰지 않는다.

**zsh 함정(재확인)** — 위 열여섯 경로를 따옴표 없는 셸 변수 하나로 묶어 `-- $P`로 넘기면
zsh는 단어 분리를 하지 않아 pathspec이 하나로 합쳐진다. 아래 실측은 배열(`paths=(...)`)과
`"${paths[@]}"`로 각 경로를 개별 인자로 넘겼다.

## 임시 clone 실측(①~⑥, `git clone .` → `e47901ac` → 복원, 전건 실행)

| # | 확인 | 명령 | 결과 |
| --- | --- | --- | --- |
| ① 복원 exit | 위 `git restore`(배열 인자, base `128f9cd3`) | exit 0 |
| ② D/M 수 | `git status --porcelain -- . ':!reports/evidence'` | `D` 4 + `M` 12 = 16 — 위 목록과 정확히 일치 |
| ③ diff 빈 것 | `git diff 128f9cd3 -- <같은 열여섯 경로>` | 출력 없음(0줄, exit 0) — **내 줄이 사라졌다**. `git status --porcelain -- CLAUDE.md .claude/ milestone-6.md` = 0줄. `grep -c notice_requirement adapters/.../Sql.kt` = 1(**남의 줄이 남았다**) |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :procurement:compileKotlin :strategy:compileKotlin :workflow:compileKotlin :app:compileKotlin` | BUILD SUCCESSFUL(exit 0), 32 actionable tasks(14 executed·18 from cache) |
| ⑤ test | `./gradlew --no-daemon :adapters:test :strategy:test :procurement:test` | BUILD SUCCESSFUL(exit 0), 45 actionable tasks(9 executed·5 from cache·31 up-to-date) |
| ⑥ 게이트(`check`) | `./gradlew --no-daemon check` | BUILD SUCCESSFUL(exit 0), 346 actionable tasks(188 executed·112 from cache·46 up-to-date) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** — ③이 `git diff 128f9cd3`가 빈 것을
직접 재므로, 되돌린 트리는 새 base의 해당 열여섯 경로와 파일 내용이 완전히 동일하다.
④⑤⑥이 그 트리가 서고·통과하고·게이트를 지난다는 것까지 확인한다.

## 하네스 레인 절 갱신 필요성

`git log --oneline 128f9cd3..e47901ac -- CLAUDE.md .claude/`는 `31305128`·`2b61dff4`
둘을 낸다(둘 다 이전 라운드부터 이 브랜치에 있던 팀장 레인의 하네스 커밋이고, `main`에는
없어 흡수 때마다 이 range에 다시 나온다). 이 병합 커밋(`e47901ac`) 자신은 `.claude/`를
편집하지 않았다 — scope.md 「하네스 레인 변경」 절이 등재 중이고 **되돌림 대상이 아니다**
(위 복원 명령·목록에 없고, 앞으로도 넣지 않는다). `milestone-6.md`는 자동 병합됐고
6F-4·6F-5-a 양쪽 문단이 모두 살아 있다(위 표) — 이 역시 되돌림 대상이 아니다.
