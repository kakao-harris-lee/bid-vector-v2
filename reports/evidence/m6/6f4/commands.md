# M6/6F-4 — commands.md

base_sha 2차 갱신값 `edeaa9a3`(main 2차 흡수, 6F-6 포함). 아래는 팀장 지적 넷(① CHECK 범위
정정 · ㉮ main 2차 흡수 · ㉯ V11→V14 재번호 · ㉰ CHECK 본문 등식 test) 반영 뒤 최종 HEAD
`15da367f`에서 재실행한 acceptance 전건이다(그 이전 HEAD에서 돌린 결과는 production 코드가
바뀌어 무효 — 최종 HEAD 값으로 갈음한다).

## 2026-09-19T07:00:14Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(70 executed·267 up-to-date)

## 2026-09-19T07:00:33Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE, 위 `check`가 이미 baseline을 갱신했다)

## 2026-09-19T07:00:41Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `== one-command-check: 완료 — Kotlin 전건 + Python 전건 통과 ==`

## ㉮ main 2차 흡수(`edeaa9a3`, 6F-6 포함) — 충돌 해소와 「둘 다 취한다」 확인

병합 `git merge origin/main`(edeaa9a3) 실행 시 팀장이 미리 알려준 다섯 자리 중
`Sql.kt`·`CleanMigrationCheckTest.kt`·`CleanMigrationColumnTest.kt`는 **자동 병합**(충돌
마커 없음), `milestone-6.md`만 수동 충돌(`<<<<<<< HEAD`~`>>>>>>> origin/main`, 6F-4 결정
문단과 6F-6 착수 문단이 같은 위치에 추가됨) — 두 문단을 순서대로 이어 붙여 해소했다.
`CleanMigrationTest.kt`·`PersistenceTestSupport.kt`·`config/quality/gate-tests.properties`는
이 slice가 원래 손대지 않은 파일이라 병합 자체에 델타가 없었다(자동 반영).

- cmd: `for f in Sql.kt CleanMigrationCheckTest.kt CleanMigrationColumnTest.kt
  CleanMigrationTest.kt PersistenceTestSupport.kt gate-tests.properties; do grep -c
  'notice_title\|NoticeTitle' "$f"; grep -ci 'operator_profile\|OperatorProfile' "$f"; done`
- 핵심 결과: 여섯 파일 전부 「내 줄(notice_title 계열)」과 「남의 줄(operator_profile 계열)」이
  기대대로 공존(0건인 자리는 애초에 그 slice가 안 건드린 파일) — `rollback.md`의 표에 전체
  수치를 남겼다.
- cmd: `./gradlew --no-daemon compileKotlin compileTestKotlin`(병합 직후, 재번호 전)
- exit: 0 — 병합 자체가 컴파일을 깨지 않았다.

## ㉯ V11 → V14 재번호(main에 V12가 이미 있고 V13이 병합 대기)

- cmd: `git mv adapters/src/main/resources/db/migration/V11__notice_title.sql
  adapters/src/main/resources/db/migration/V14__notice_title.sql`
- **주의(자기 실측)**: 뒤이은 `git commit -- V14__notice_title.sql ...`가 옛 경로
  `V11__notice_title.sql`을 pathspec에서 빠뜨려, `git mv`가 스테이징한 삭제가 커밋되지
  않고 인덱스에 남았다(`git ls-tree HEAD`로 V11·V14가 동시에 존재하는 것을 발견). 후속
  커밋(`64332338`)으로 옛 경로의 삭제를 마무리했다 — 워킹트리는 애초에 V14 하나뿐이었으므로
  코드 동작에는 영향이 없었다(발견 즉시 정정).
- cmd: `grep -rn "V11" adapters/ procurement/ strategy/` (build/ 제외)
- 핵심 결과: 재번호 근거를 설명하는 V14 파일 헤더 주석 2줄만 남고 잔존 0건.
- cmd: `./gradlew --no-daemon :adapters:test`(재번호 뒤)
- exit: 0 — Flyway가 V14를 정상 인식(적용 순서 문제 없음, 실 DB 인스턴스가 없어 이력
  충돌도 없다).

## ㉰ `notice_notice_title_check` CHECK 본문을 `shouldBe`로 정확히 고정

팀장 지적(`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`, 6F-6 세션 실측) — 개수 축(축8 CHECK
개수)만으로는 문자 클래스가 좁아져도 안 잡힌다(이번 라운드에서 실제로 두 번 안 잡혔다).
`outbox_state_check`·`edit_session_state_check`와 같은 관례로 `pg_get_constraintdef` 원문을
`shouldBe`로 대조하는 test를 `CleanMigrationCheckTest.kt`에 추가했다.

- **사후 검증(버그 재현)**: V14의 문자 클래스를 이전 라운드의 좁은 형태(TAB-LF만, 정확히
  이번 라운드에서 겪은 결함)로 일시 되돌려
  `./gradlew --no-daemon :adapters:test --tests '…CleanMigrationCheckTest'` 실행 →
  새 test가 즉시 실패(`expected:<...	-...> but was:<...\t-\n...>`, 부분 일치
  진단까지 출력). 원복 후 재실행 → 5/0/0 그린.
- cmd: `./gradlew --no-daemon :adapters:test --tests
  'bidvector.adapters.persistence.CleanMigrationCheckTest'`
- exit: 0, `tests="5" skipped="0" failures="0" errors="0"`(개수 test 1 + 본문 존재 test 1 +
  outbox/edit_session 본문 test 2 + 신설 notice_title 본문 test 1).

## 신설/수정 test 실행 확인 (JUnit XML 직접 대조, 최종 HEAD)

- `adapters/…/TEST-bidvector.adapters.persistence.NoticeFindRoundTripTest.xml` —
  `tests="8" failures="0" errors="0"`.
- `adapters/…/TEST-bidvector.adapters.persistence.CleanMigrationCheckTest.xml` —
  `tests="5" failures="0" errors="0"`(V14 CHECK 본문 등식 test 신설로 4 → 5).
- `procurement/…/TEST-bidvector.procurement.NoticeTitleTest.xml` —
  `tests="4" failures="0" errors="0"`.
- `strategy/…/TEST-bidvector.strategy.WatchTextAssemblyTest.xml` —
  `tests="9" failures="0" errors="0"`.

## verifier r2 MEDIUM-1·2 처방 재검증(변이 재현, 1차 수정 라운드 — 이전 기록 유지)

- **MEDIUM-1(V14 CHECK)**: `NoticeFindRoundTripTest`의 test가 빈 문자열·ASCII 공백·탭·개행·
  NBSP·전각 공백(1차 표본) 및 이후 정의에서 유도한 BMP 공백 전체 집합(2차 재작성) 모두에서
  DB 직접 UPDATE 거부(`PSQLException`)와 `NoticeTitle.of` `null`이 일치함을 실측했다.
- **MEDIUM-2(merge guard)**: `NoticeRowMerge.kt`의 `title = incomingRow.title ?:
  existing.title`를 `title = existing.title`로 바꾸는 변이 재현 → merge guard test가
  `expected:<Updated(2)> but was:<Unchanged>`로 즉시 실패. 원복 후 그린 복귀 확인.

## 팀장 2차 지적 재검증(V14 CHECK가 V4 선례보다 좁았다 — 이전 기록 유지)

- **원인**: 1차 수정에서 넣은 CHECK 정규식이 유니코드 이스케이프 표기 입력 과정에서 실제
  제어문자로 치환돼, 의도한 범위(코드포인트 9-13, TAB부터 CR까지)가 9-10(TAB·LF)으로
  좁아졌다 — VT(11)·FF(12)·CR(13) 누락.
- **정정 확인**: V4 파일의 `reserve_price_sequence` CHECK 정규식 텍스트를 프로그램으로
  그대로 읽어 V14에 재사용 — `diff`로 두 bracket 표현이 완전히 동일한 텍스트임을 확인(0
  diff), 파일에 원시 제어문자 없음도 확인.
- **회귀 test 재작성**: Kotlin의 공백 판정 함수(`Char.isWhitespace()`)가 참인 코드포인트
  전부를 BMP(0..0x3001)에서 실측으로 나열해 표본을 만들고, DB 거부와 `NoticeTitle.of`
  null이 그 집합 전부에서 일치하는지를 술어로 단언하도록 바꿨다.
- **버그 재현 확인**: 옛 좁은 CHECK로 일시 되돌려 재실행 → `AssertionFailedError:
  codepoints=[11]`로 정확히 VT에서 실패. 고친 버전으로 복원 후 8/0/0 그린.

## 자동 병합 Sql.kt 의미 정합 확인 (1차 흡수 때 이미 확인, 2차 흡수 뒤에도 유효)

- cmd: `git diff edeaa9a3..15da367f -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`
- 핵심 결과: 이 slice의 `notice_title` 추가 세 곳만 나온다(`NOTICE_COLUMNS`·`INSERT_NOTICE`·
  `UPDATE_NOTICE`) — 6F-2·6F-6이 더한 `SELECT_OPEN_CANDIDATES`·`operator_profile` SQL은
  base(`edeaa9a3`)에 이미 있어 이 diff에 나오지 않는다. 위 `check` 전건 통과가 그 정합을
  실측으로 확인한다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/resources/db/migration/V14__notice_title.sql adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt strategy/src/main/kotlin/bidvector/strategy/Text.kt strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt reports/evidence/m6/6f4/`
- exit: 1 (매치 없음 = 통과)
- 육안 확인: 위 파일 전체에 실제 사업자/연락처 값·Telegram id 없음(합성 예시 텍스트만).

## rollback ①~⑥ 재실측(별도 clone, HEAD `15da367f`, base `edeaa9a3`)

`rollback.md`에 절 전체를 둔다 — 요약: 복원 exit 0, D 4 + M 12 = 16(목록과 일치), diff
`edeaa9a3`와 0줄, compile/test/`check` 전부 exit 0. 6F-6의 `operator_profile` 관련 내용은
되돌린 트리에서도 그대로 유지됨을 확인(남의 줄 안 지워짐).
