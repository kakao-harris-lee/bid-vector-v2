# M6/6F-4 — commands.md

base_sha 3차 갱신값 `128f9cd3`(main 3차 흡수, 6F-5-a·V13 포함, PR #40 오픈 뒤). 아래는
main 3차 흡수 충돌 해소(`CleanMigrationCheckTest.kt` 하나, 「둘 다 취한다」) 뒤 최종 HEAD
`e47901ac`에서 재실행한 acceptance 전건이다(그 이전 HEAD에서 돌린 결과는 base가 움직여
무효 — 최종 HEAD 값으로 갈음한다).

## 2026-09-19T09:34:36Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(71 executed·266 up-to-date)

## 2026-09-19T09:35:20Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE, 위 `check`가 이미 baseline을 갱신했다)

## 2026-09-19T09:35:28Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `== one-command-check: 완료 — Kotlin 전건 + Python 전건 통과 ==`

## ③ main 3차 흡수(`128f9cd3`, 6F-5-a·V13) — 충돌 해소와 「둘 다 취한다」 확인

병합 `git merge origin/main`(128f9cd3) 실행 시 충돌은 `CleanMigrationCheckTest.kt`
하나뿐이었다 — 양쪽이 같은 KDoc~`}` 사이 위치에 각자 새 `@Test`를 더한 형태(공유 문맥은
충돌 블록 위 `/**`와 아래 `}`). 해소: 내 KDoc + 내 test + 닫는 `}` → 빈 줄 → 그쪽 KDoc +
그쪽 test 둘, 마지막 test의 닫는 `}`는 공유 줄이 맡도록 재구성했다. `Sql.kt`·
`CleanMigrationColumnTest.kt`·`milestone-6.md`는 자동 병합(충돌 마커 없음).

- cmd: `grep -c "@Test" adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt`
- 핵심 결과: `7`(병합 전 내 쪽 5 + 그쪽이 더한 2 — 개수만으로도 「둘 다 있음」 1차 확인).
- cmd: `grep -c 'notice_notice_title_check\|D-6F4-1' …CleanMigrationCheckTest.kt` /
  `grep -c 'notice_requirement\|D-6F5' …CleanMigrationCheckTest.kt`
- 핵심 결과: 각각 `3`·`18` — 내 test와 그쪽 test 둘 다 실측으로 확인(`rollback.md` 표).
- cmd: `./gradlew --no-daemon compileKotlin compileTestKotlin`(병합 직후)
- exit: 0 — 병합 자체가 컴파일을 깨지 않았다.
- cmd: `./gradlew --no-daemon :adapters:test --tests
  'bidvector.adapters.persistence.CleanMigrationCheckTest'`(병합 뒤)
- exit: 0, `tests="7" skipped="0" failures="0" errors="0"`.

## 신설/수정 test 실행 확인 (JUnit XML 직접 대조, 최종 HEAD `e47901ac`)

- `adapters/…/TEST-bidvector.adapters.persistence.NoticeFindRoundTripTest.xml` —
  `tests="8" failures="0" errors="0"`.
- `adapters/…/TEST-bidvector.adapters.persistence.CleanMigrationCheckTest.xml` —
  `tests="7" failures="0" errors="0"`(내 notice_title 본문 등식 test 1 + 6F-5-a가 흡수로
  더한 notice_requirement 열거·본문 집합 test 2, 병합 전 5 → 7).
- `procurement/…/TEST-bidvector.procurement.NoticeTitleTest.xml` —
  `tests="4" failures="0" errors="0"`.
- `strategy/…/TEST-bidvector.strategy.WatchTextAssemblyTest.xml` —
  `tests="9" failures="0" errors="0"`.

## 자동 병합 Sql.kt 의미 정합 확인 (1차 흡수 때 이미 확인, 3차 흡수 뒤에도 유효)

- cmd: `git diff 128f9cd3..e47901ac -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`
- 핵심 결과: 이 slice의 `notice_title` 추가 세 곳만 나온다(`NOTICE_COLUMNS`·`INSERT_NOTICE`·
  `UPDATE_NOTICE`) — 6F-2·6F-6·6F-5-a가 더한 `SELECT_OPEN_CANDIDATES`·`operator_profile`·
  `notice_requirement` SQL은 base(`128f9cd3`)에 이미 있어 이 diff에 나오지 않는다. 위
  `check` 전건 통과가 그 정합을 실측으로 확인한다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/resources/db/migration/V14__notice_title.sql adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt strategy/src/main/kotlin/bidvector/strategy/Text.kt strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt reports/evidence/m6/6f4/`
- exit: 1 (매치 없음 = 통과)
- 육안 확인: 위 파일 전체에 실제 사업자/연락처 값·Telegram id 없음(합성 예시 텍스트만).

## rollback ①~⑥ 재실측(별도 clone, HEAD `e47901ac`, base `128f9cd3`)

`rollback.md`에 절 전체를 둔다 — 요약: 복원 exit 0, D 4 + M 12 = 16(목록과 일치), diff
`128f9cd3`와 0줄, compile/test/`check` 전부 exit 0. 6F-5-a의 `notice_requirement` 관련
내용은 되돌린 트리에서도 그대로 유지됨을 확인(남의 줄 안 지워짐).
