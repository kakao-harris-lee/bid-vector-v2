# M6/6F-4 — commands.md

base_sha(갱신값) `ede5d5b`. 아래는 팀장 2차 지적(V11 CHECK 범위 정정) 반영 뒤 최종 HEAD
`d12b0947`에서 재실행한 acceptance 전건이다(그 이전 HEAD에서 돌린 결과는 이 라운드가
production 코드를 바꿔 무효 — 최종 HEAD 값으로 갈음한다).

## 2026-09-19T06:42:50Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(31 executed·306 up-to-date)

## 2026-09-19T06:43:01Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE, 위 `check`가 이미 baseline을 갱신했다)

## 2026-09-19T06:43:03Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `== one-command-check: 완료 — Kotlin 전건 + Python 전건 통과 ==`

## 신설/수정 test 실행 확인 (JUnit XML 직접 대조)

- `adapters/…/TEST-bidvector.adapters.persistence.NoticeFindRoundTripTest.xml` —
  `tests="8" failures="0" errors="0"`(verifier r2 MEDIUM-1·2 대응으로 6 → 8 — CHECK/타입 공백
  판정 일치 test 1 + 공고명 merge guard 3단계 test 1. CHECK/타입 test는 팀장 2차 지적 뒤
  「정의에서 유도한 공백 전체 집합」 형태로 재작성됐다).
- `procurement/…/TEST-bidvector.procurement.NoticeTitleTest.xml`(신설, review MEDIUM 대응) —
  `tests="4" failures="0" errors="0"`.
- `strategy/…/TEST-bidvector.strategy.WatchTextAssemblyTest.xml` — `tests="9" failures="0"
  errors="0"`(문면만 정정, 조립 규칙 무변경이라 test 수 그대로).

## verifier r2 MEDIUM-1·2 처방 재검증(변이 재현, 1차 수정 라운드)

- **MEDIUM-1(V11 CHECK)**: `NoticeFindRoundTripTest`의 새 test가 빈 문자열·ASCII 공백·탭·개행·
  NBSP·전각 공백 여섯 값 모두에서 DB 직접 UPDATE 거부(`PSQLException`)와 `NoticeTitle.of`
  `null`이 일치함을 실측했다(1차 수정, `f3bd4420`).
- **MEDIUM-2(merge guard)**: `NoticeRowMerge.kt:130`의 `title = incomingRow.title ?:
  existing.title`를 `title = existing.title`로 바꾸는 변이를 재현 —
  `perl -0pi -e 's/title = incomingRow\.title \?\: existing\.title,/title = existing.title,/' …`
  → `./gradlew --no-daemon :adapters:test --tests
  '…NoticeFindRoundTripTest'` → merge guard test가 `expected:<Updated(2)> but
  was:<Unchanged>`로 즉시 실패(1 failed/8). 변이를 원복하고 같은 명령을 다시 돌려 8/0/0
  그린으로 복귀한 것도 확인했다.

## 팀장 2차 지적 재검증(V11 CHECK가 V4 선례보다 좁았다, 2차 수정 라운드 `d12b0947`)

- **원인**: 1차 수정에서 넣은 CHECK 정규식이 유니코드 이스케이프 표기 입력 과정에서 실제
  제어문자로 치환돼, 의도한 범위(코드포인트 9-13, TAB부터 CR까지)가 9-10(TAB·LF)으로
  좁아졌다 — VT(11)·FF(12)·CR(13) 누락.
- **정정 확인**: V4 파일(`V4__opening_result_rows.sql`)의 `reserve_price_sequence` CHECK
  정규식 텍스트를 프로그램으로 그대로 읽어 V11에 재사용 — `diff`로 두 bracket 표현이
  완전히 동일한 텍스트임을 확인(0 diff), 파일에 원시 제어문자 없음도 확인(문자별 순회로
  `ord(c) < 0x20 and c != '\n'`인 문자 0건).
- **회귀 test 재작성**: 손으로 고른 표본 대신 Kotlin의 공백 판정 함수(`Char.isWhitespace()`)가
  참인 코드포인트 전부를 BMP(0..0x3001)에서 실측으로 나열해 표본을 만들고, DB 거부와
  `NoticeTitle.of` null이 그 집합 전부에서 일치하는지를 술어로 단언하도록 바꿨다. 그 정의에
  VT·FF·CR가 포함되는지부터 먼저 단언한다(정의 자체가 잘못되면 이 test가 먼저 깨진다).
- **버그 재현 확인(사후 검증)**: 옛 좁은 CHECK(TAB-LF만)로 일시 되돌려
  `./gradlew --no-daemon :adapters:test --tests '…NoticeFindRoundTripTest'` 실행 →
  `AssertionFailedError: codepoints=[11]`로 정확히 VT에서 실패(팀장이 지적한 결함을 그대로
  재현·검출). 고친 버전으로 복원 후 같은 명령 재실행 → 8/0/0 그린.
- **acceptance 전건**: 위 2026-09-19T06:42:50Z 이하 세 명령이 이 정정 뒤 HEAD `d12b0947`에서
  돌린 결과다.

## 자동 병합 Sql.kt 의미 정합 확인 (r1에서 이미 확인, HEAD 이동 뒤에도 유효)

- cmd: `git diff ede5d5b..d12b0947 -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`
- 핵심 결과: 이 slice의 `notice_title` 추가 세 곳만 나온다(`NOTICE_COLUMNS`·`INSERT_NOTICE`·
  `UPDATE_NOTICE`) — 6F-2가 흡수 병합으로 더한 `SELECT_OPEN_CANDIDATES`는 base(`ede5d5b`)에
  이미 있어 이 diff에 나오지 않는다. `SELECT_OPEN_CANDIDATES`는 `$NOTICE_COLUMNS`를 참조라
  `notice_title`을 자동으로 포함하고, 소비자 `JdbcCandidateSource.kt`는 컬럼명 기반
  `ResultSet` 읽기라 순서 영향이 없다 — 위 `check` 전건 통과가 그 정합을 실측으로 확인한다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/resources/db/migration/V11__notice_title.sql adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt strategy/src/main/kotlin/bidvector/strategy/Text.kt strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt reports/evidence/m6/6f4/`
- exit: 1 (매치 없음 = 통과)
- 육안 확인: 위 파일 전체에 실제 사업자/연락처 값·Telegram id 없음(합성 예시 텍스트만).

## rollback ①~⑥ 재실측(별도 clone, HEAD `d12b0947`)

`rollback.md`에 절 전체를 둔다 — 요약: 복원 exit 0, D 4 + M 12 = 16(목록과 일치), diff
`ede5d5b`와 0줄, compile/test/`check` 전부 exit 0.
