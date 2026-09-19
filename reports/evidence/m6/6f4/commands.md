# M6/6F-4 — commands.md

base_sha(갱신값) `ede5d5b`. 아래는 verifier r2·code-reviewer 수정 라운드 뒤 HEAD `bdfafc74`에서
재실행한 acceptance 전건이다(그 이전 HEAD에서 돌린 결과는 이 라운드가 production 코드를
바꿔 무효 — 최종 HEAD 값으로 갈음한다).

## 2026-09-19T06:22:24Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(88 executed·249 up-to-date)

## 2026-09-19T06:23:03Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE, 위 `check`가 이미 baseline을 갱신했다)

## 2026-09-19T06:23:10Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `== one-command-check: 완료 — Kotlin 전건 + Python 전건 통과 ==`

## 신설/수정 test 실행 확인 (JUnit XML 직접 대조, 위 `check` 실행 시각과 일치)

- `adapters/…/TEST-bidvector.adapters.persistence.NoticeFindRoundTripTest.xml` —
  `tests="8" failures="0" errors="0"`(verifier r2 MEDIUM-1·2 대응으로 6 → 8 — CHECK/타입 공백
  판정 일치 test 1 + 공고명 merge guard 3단계 test 1).
- `procurement/…/TEST-bidvector.procurement.NoticeTitleTest.xml`(신설, review MEDIUM 대응) —
  `tests="4" failures="0" errors="0"`.
- `strategy/…/TEST-bidvector.strategy.WatchTextAssemblyTest.xml` — `tests="9" failures="0"
  errors="0"`(문면만 정정, 조립 규칙 무변경이라 test 수 그대로).

## verifier r2 MEDIUM-1·2 처방 재검증(변이 재현)

- **MEDIUM-1(V11 CHECK)**: `NoticeFindRoundTripTest`의 새 test가 빈 문자열·ASCII 공백·탭·개행·
  NBSP·전각 공백 여섯 값 모두에서 DB 직접 UPDATE 거부(`PSQLException`)와 `NoticeTitle.of`
  `null`이 일치함을 실측한다(위 `check`에 포함, `:adapters:test` 그린).
- **MEDIUM-2(merge guard)**: `NoticeRowMerge.kt:130`의 `title = incomingRow.title ?:
  existing.title`를 `title = existing.title`로 바꾸는 변이를 재현 —
  `perl -0pi -e 's/title = incomingRow\.title \?\: existing\.title,/title = existing.title,/' …`
  → `./gradlew --no-daemon :adapters:test --tests
  '…NoticeFindRoundTripTest'` → 새 merge guard test가 `expected:<Updated(2)> but
  was:<Unchanged>`로 즉시 실패(1 failed/8). 변이를 원복하고 같은 명령을 다시 돌려 8/0/0
  그린으로 복귀한 것도 확인했다.

## 자동 병합 Sql.kt 의미 정합 확인 (r1에서 이미 확인, HEAD 이동 뒤에도 유효)

- cmd: `git diff ede5d5b..bdfafc74 -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`
- 핵심 결과: 이 slice의 `notice_title` 추가 세 곳만 나온다(`NOTICE_COLUMNS`·`INSERT_NOTICE`·
  `UPDATE_NOTICE`) — 6F-2가 흡수 병합으로 더한 `SELECT_OPEN_CANDIDATES`는 base(`ede5d5b`)에
  이미 있어 이 diff에 나오지 않는다. `SELECT_OPEN_CANDIDATES`는 `$NOTICE_COLUMNS`를 참조라
  `notice_title`을 자동으로 포함하고, 소비자 `JdbcCandidateSource.kt`는 컬럼명 기반
  `ResultSet` 읽기라 순서 영향이 없다 — 위 `check` 전건 통과가 그 정합을 실측으로 확인한다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/resources/db/migration/V11__notice_title.sql adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt strategy/src/main/kotlin/bidvector/strategy/Text.kt strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt reports/evidence/m6/6f4/`
- exit: 1 (매치 없음 = 통과)
- 육안 확인: 위 파일 전체에 실제 사업자/연락처 값·Telegram id 없음(합성 예시 텍스트만).

## rollback ①~⑥ 재실측(별도 clone, HEAD `bdfafc74`)

`rollback.md`에 절 전체를 둔다 — 요약: 복원 exit 0, D 4 + M 12 = 16(목록과 일치), diff
`ede5d5b`와 0줄, compile/test/`check` 전부 exit 0.
