# M6/6F-4 — commands.md

base_sha(갱신값) `ede5d5b`, 아래는 `main` 흡수 병합 뒤 HEAD `6f8466f1`에서 재실행한 acceptance
전건이다(그 이전 HEAD 에서 돌린 결과는 team-lead 지시로 폐기 — base 가 움직였다).

## 2026-09-18T22:32:18Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(80 executed·257 up-to-date)

## 2026-09-19T00:43:39Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` 생성 완료

## 2026-09-19T00:44:38Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `== one-command-check: 완료 — Kotlin 전건 + Python 전건 통과 ==`

## 신설 test 실행 확인 (JUnit XML 직접 대조)

- `adapters/build/test-results/test/TEST-bidvector.adapters.persistence.NoticeFindRoundTripTest.xml`
  — `tests="6" failures="0" errors="0"`, timestamp가 위 `check` 실행 시각과 일치(신선한 실행).
- `strategy/build/test-results/test/TEST-bidvector.strategy.WatchTextAssemblyTest.xml` —
  `tests="9" failures="0" errors="0"`. 이 task는 위 `check`에서 `UP-TO-DATE`로 스킵됐다 —
  흡수 병합 커밋이 `strategy` 모듈 입력을 전혀 바꾸지 않아 정당한 캐시 히트다(직전 별도
  실행 `./gradlew --no-daemon :strategy:test --tests "bidvector.strategy.WatchTextAssemblyTest"`
  에서 같은 결과를 이미 확보).

## 자동 병합 Sql.kt 의미 정합 확인

- cmd: `git diff ede5d5b..6f8466f1 -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`
- 핵심 결과: 이 slice의 `notice_title` 추가 세 곳만 나온다(`NOTICE_COLUMNS`·`INSERT_NOTICE`·
  `UPDATE_NOTICE`) — 6F-2가 흡수 병합으로 더한 `SELECT_OPEN_CANDIDATES`는 base(`ede5d5b`)에
  이미 있어 이 diff에 나오지 않는다. `SELECT_OPEN_CANDIDATES`는 `$NOTICE_COLUMNS`를 참조라
  `notice_title`을 자동으로 포함하고, 소비자 `JdbcCandidateSource.kt`는 컬럼명 기반
  `ResultSet` 읽기라 순서 영향이 없다 — 위 `check` 전건 통과가 그 정합을 실측으로 확인한다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/resources/db/migration/V11__notice_title.sql adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt strategy/src/main/kotlin/bidvector/strategy/Text.kt strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt reports/evidence/m6/6f4/`
- exit: 1 (매치 없음 = 통과)
- 육안 확인: 위 파일 전체에 실제 사업자/연락처 값·Telegram id 없음(합성 예시 텍스트만).
