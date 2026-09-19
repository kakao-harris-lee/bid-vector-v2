# M6/6F-4 — rollback.md

**실측 HEAD: `15da367f`**(수정 라운드 3의 마지막 산출물 커밋 — **base_sha 2차 갱신값
`edeaa9a3`**, scope.md 참고). 이 라운드는 팀장 지적 넷을 반영했다: ① V11 CHECK가 V4
선례보다 좁았던 것(VT·FF·CR 누락) 정정 — 별도 evidence 갱신에 이미 기록. ㉮ `main` 2차 흡수
(edeaa9a3, 6F-6 포함) — 겹친 파일 다섯을 「둘 다 취한다」로 해소. ㉯ 마이그레이션 번호를
V11에서 V14로 재번호(6F-6이 V12를, 대기 중 PR #39가 V13을 선점). ㉰ `notice_notice_title_check`
CHECK 본문을 `shouldBe`로 정확히 고정(개수 축만으로는 문자 클래스가 좁아져도 안 잡혔다).

## 목록(기계 산출)

```
$ git diff --name-status edeaa9a3..15da367f -- . ':!reports/evidence' ':!milestone-6.md' ':!.claude'
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

`A` 4(신규, 파일명은 `V14__notice_title.sql`로 재번호됨) + `M` 12(수정) = 16경로. 목록
자체는 이전 라운드(구 base `ede5d5b`)와 파일 집합이 같다 — `edeaa9a3`가 이미 `ede5d5b`의
후손이라 6F-6이 건드린 세 파일(`CleanMigrationTest.kt`·`PersistenceTestSupport.kt`·
`config/quality/gate-tests.properties`)에는 **이 slice의 추가 델타가 0**이기 때문이다(아래
공유 파일 절 참고). `reports/evidence/m6/6f4/**`·`milestone-6.md`·`.claude/`는 이 목록에서
뺐다(되돌리지 않는다). **라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을 재산출한다.**

## 공유 파일 셋 — 겹침 확인과 절차(2차 흡수, 팀장이 미리 알려준 다섯 자리)

`main` 2차 흡수(`edeaa9a3`, PR #38·6F-6) 병합 시 5개 파일에서 겹침이 예상됐다 — 전부
자동 병합됐다(수동 충돌 마커 없음). **자동 병합 성공은 의미 정합이 아니므로** 「내 줄
있음」과 「남의 줄 남음」을 각각 grep으로 확인했다:

| 파일 | 내 줄(notice_title 계열) | 남의 줄(operator_profile 계열, 6F-6) |
| --- | --- | --- |
| `Sql.kt` | 3건 | 2건(`SELECT_PROFILE`·`INSERT ... INTO operator_profile`) |
| `CleanMigrationCheckTest.kt` | 1건(`"notice" to 13`) + 이번 라운드 신설 CHECK 본문 test | 2건(`"operator_profile" to 2` 등) |
| `CleanMigrationColumnTest.kt` | 1건(`ColumnSpec("notice", "notice_title", ...)`) | 9건(`operator_profile` 컬럼 6개 + 관련) |
| `CleanMigrationTest.kt` | 0건(이 slice가 원래 안 건드림) | 6건(`operator_profile` 스냅샷 래칫 예외) |
| `PersistenceTestSupport.kt` | 0건(이 slice가 원래 안 건드림) | 2건(`TRUNCATE` 목록의 `operator_profile`) |
| `config/quality/gate-tests.properties` | 0건 | 9건(`bidvector.adapters.profile.*` 게이트 키 셋) |

**하나도 지워지지 않았다** — `Sql.kt`의 `notice_title` 3곳과 `operator_profile` 2곳이 서로
다른 SQL 상수(다른 테이블)라 겹치지 않고, `CleanMigrationCheckTest.kt`·
`CleanMigrationColumnTest.kt`는 두 slice가 각자 자기 테이블 항목을 맵/리스트 끝에 추가한
형태라 자동 병합이 hunk 단위로 정확히 합쳤다. `milestone-6.md`만 유일하게 수동 충돌
마커가 났다(같은 위치에 두 슬라이스가 각자 문단을 추가) — 두 문단을 순서대로(6F-4 먼저,
6F-6 다음) 이어 붙이고 빈 줄 하나만 정리했다(내용 손실 없음).

## 복원 명령

```
git restore --source=edeaa9a3 --staged --worktree -- \
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

`A` 항목 넷은 `--source`에 없어 `git restore`가 자동으로 삭제한다(`V14__notice_title.sql`
포함 — 신설 파일은 `git rm` 효과). `M` 항목 열둘은 base(`edeaa9a3`) 내용으로 복원되며,
그 안의 `operator_profile` 관련 내용(6F-6)은 base 자체에 있으므로 그대로 남는다.
`reports/evidence/m6/6f4/**`·`milestone-6.md`·`.claude/`는 되돌리지 않는다. `checkout --`는
쓰지 않는다.

**zsh 함정(verifier r1 참고, 재확인)** — 위 열여섯 경로를 따옴표 없는 셸 변수 하나로 묶어
`-- $P` 로 넘기면 zsh는 단어 분리를 하지 않아 pathspec이 하나로 합쳐진다. 아래 실측은
배열(`paths=(...)`)과 `"${paths[@]}"`로 각 경로를 개별 인자로 넘겼다.

## 임시 clone 실측(①~⑥, `git clone .` → `15da367f` → 복원, 전건 실행)

| # | 확인 | 명령 | 결과 |
| --- | --- | --- | --- |
| ① 복원 exit | 위 `git restore`(배열 인자, base `edeaa9a3`) | exit 0 |
| ② D/M 수 | `git status --porcelain -- . ':!reports/evidence'` | `D` 4 + `M` 12 = 16 — 위 목록과 정확히 일치 |
| ③ diff 빈 것 | `git diff edeaa9a3 -- <같은 열여섯 경로>` | 출력 없음(0줄, exit 0) — **내 줄이 사라졌다**. `git status --porcelain -- CLAUDE.md .claude/ milestone-6.md` = 0줄(하네스·6F-6 인계 문서 무변경). `grep -c operator_profile adapters/.../Sql.kt` = 2(**남의 줄이 남았다**, 6F-6의 프로필 SQL 유지) |
| ④ compile | `./gradlew --no-daemon :adapters:compileKotlin :procurement:compileKotlin :strategy:compileKotlin :workflow:compileKotlin :app:compileKotlin` | BUILD SUCCESSFUL(exit 0), 32 actionable tasks(14 executed·18 from cache) |
| ⑤ test | `./gradlew --no-daemon :adapters:test :strategy:test :procurement:test` | BUILD SUCCESSFUL(exit 0), 45 actionable tasks(9 executed·5 from cache·31 up-to-date) — 되돌린 트리에서 6F-2·6F-6·기존 notice test도 그대로 선다 |
| ⑥ 게이트(`check`) | `./gradlew --no-daemon check` | BUILD SUCCESSFUL(exit 0), 346 actionable tasks(188 executed·112 from cache·46 up-to-date) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** — ③이 `git diff edeaa9a3`가 빈 것을
직접 재므로, 되돌린 트리는 새 base(`edeaa9a3`)의 해당 열여섯 경로와 파일 내용이 완전히
동일하다. ④⑤⑥이 그 트리가 서고·통과하고·게이트를 지난다는 것까지 확인한다.

## 하네스 레인 절 갱신 필요성

`git log --oneline edeaa9a3..15da367f -- CLAUDE.md .claude/`는 **빈 출력이 아니다**
(verifier LEDGER-1 — 이전 기록의 오류) — 실제로는 `2b61dff4`(1건) 이 나온다. 이 slice가
직접 커밋한 것이 아니라 팀장 레인이 운영자 승인 하에 같은 range에 둔 `.claude/skills/
evidence-pack/SKILL.md` 편집이고, scope.md 「하네스 레인 변경」 절이 정확히 등재 중이다
— **되돌림 대상이 아니다**(위 복원 명령·목록에 없고, 앞으로도 넣지 않는다).
`milestone-6.md`도 병합 충돌 해소로 이 range에 등장하지만(수동 머지 커밋 `7a0a8c4f`)
**양쪽 슬라이스의 기존 문단을 그대로 이어 붙인 것**이라 새 결정·새 OPEN을 만들지 않았고
— 이 역시 되돌림 대상이 아니다.
