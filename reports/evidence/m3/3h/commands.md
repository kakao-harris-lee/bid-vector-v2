# commands.md — M3 / 3H-1

## 2026-09-16T14:50:00Z
- cmd: `./gradlew --no-daemon :procurement:test --tests "bidvector.procurement.AgencyTest" --tests "bidvector.procurement.FieldContractTest" --tests "bidvector.procurement.AgencyFieldContractTest" --tests "bidvector.procurement.CanonicalizeTest" --tests "bidvector.procurement.CollectionPolicyTest" --tests "bidvector.procurement.NoticeTest"`
- exit: 0
- 핵심 결과: 도메인 층(`Agency`·`FieldConcept` 넷·`Canonicalize` 역할별 조립) 첫 GREEN 전환.

## 2026-09-16T14:55:00Z
- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`
- exit: 1
- 핵심 결과: `NoticeReconstructionTest.kt`가 `NoticeRow` 신규 컬럼 넷에 값을 안 줘 `No value passed for parameter` 4건 — 수정 뒤 재실측.

## 2026-09-16T14:57:00Z
- cmd: (수정 뒤) `./gradlew --no-daemon :adapters:compileTestKotlin`
- exit: 0

## 2026-09-16T15:00:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.persistence.CleanMigrationColumnTest" --tests "bidvector.adapters.persistence.NoticeFindRoundTripTest" --tests "bidvector.adapters.persistence.NoticeReconstructionTest" --tests "bidvector.adapters.persistence.NoticeVersioningTest" --tests "bidvector.adapters.persistence.PrecedenceMutationTest" --tests "bidvector.adapters.persistence.CleanMigrationTest" --tests "bidvector.adapters.persistence.CleanMigrationCheckTest" --tests "bidvector.adapters.persistence.CleanMigrationTriggerTest"`
- exit: 0
- 핵심 결과: V7 마이그레이션 적용·발주기관 넷 저장/복원(JDBC 왕복, Testcontainers) GREEN. `CleanMigrationColumnTest`가 스키마 스냅샷 컬럼 넷을 확인.

## 2026-09-16T15:05:00Z — 첫 전건 실측
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: 세 게이트 FAIL — `procurement:ktlintMainSourceSetCheck`(chain-method-continuation 8건, `Canonicalize.kt`의 `agencyFrom`), `procurement:sizeGate`(`CanonicalizeTest.kt` 559줄·`CollectionPolicy.kt` 541줄, 한도 500), `procurement:detekt`(`Canonicalize.kt` TooManyFunctions 14 > 11).

## 2026-09-16T15:10:00Z
- cmd: (`agencyFrom`·`demandAgencyFrom`·`noticeAgencyFrom`을 `Agency.kt`로 이동, `KonepsAgencyFieldContracts.kt` 신설로 발주기관 행 넷 분리) `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: `procurement:sizeGate`(함수 50줄) — `CollectionPolicyTest.kt:29`(등재 개수 단언 함수)가 리터럴 확장으로 56줄. `EXPECTED_ADOPTED_FIELD_RAW_NAMES` top-level 값으로 추출 뒤 재실측.

## 2026-09-16T15:13:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: `procurement:ktlintMainSourceSetCheck`(`CollectionPolicy.kt` 다단 `(...).map{}` 줄바꿈이 `Missing newline after "("`/`before ")"` 위반) — `KONEPS_ALL_FIELD_ROWS` 분리 시도가 sizeGate 500줄을 다시 넘겨 왕복.

## 2026-09-16T15:16:00Z
- cmd: (`KONEPS_OPERATIONAL_FIELD_CONTRACTS`는 원형 유지, `KonepsPresentInSets.kt` 신설로 `NOTICE_IDENTIFIER_PRESENT_IN` 분리해 headroom 확보) `./gradlew --no-daemon :procurement:ktlintMainSourceSetCheck :procurement:sizeGate :procurement:detekt :procurement:cpdCheck :procurement:compileKotlin :procurement:compileTestKotlin`
- exit: 0
- 핵심 결과: procurement 게이트 넷 전부 GREEN(500→488줄).

## 2026-09-16T15:18:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: `procurement:ktlintTestSourceSetCheck` — `CollectionPolicyTest.kt:27` "EOL comment may not be preceded by a KDoc"(새 `//` 주석이 기존 class KDoc과 `class` 선언 사이에 끼어듦). 주석+`EXPECTED_ADOPTED_FIELD_RAW_NAMES`를 KDoc 앞(파일 상단)으로 옮겨 재실측.

## 2026-09-16T15:20:00Z
- cmd: `./gradlew --no-daemon :procurement:ktlintTestSourceSetCheck :procurement:test`
- exit: 0

## 2026-09-16T15:21:38+09:00(커밋 `ec03ddb`) 뒤 — 전건 GREEN
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(346 actionable tasks, 46~47s, 재실행 두 번 모두 동일). **acceptance_commands 충족.**

## 우회 (2) 변이 실측 — 2026-09-16T15:22:00Z
- cmd: `AgencyCode(value = "직접호출")`(직접 생성자 호출)를 임시 test 파일에 추가한 뒤 `./gradlew --no-daemon :procurement:compileTestKotlin`
- exit: 1
- 핵심 결과: `Cannot access 'constructor(value: String): AgencyCode': it is private` — 즉시 컴파일 거부. 임시 파일 삭제 뒤 `git status --short` 빈 상태 확인, `./gradlew --no-daemon :procurement:compileTestKotlin` 재실행 exit 0(UP-TO-DATE).

## 비밀값 스캔
- cmd: `git diff --name-only 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1..HEAD -- procurement adapters config/quality | grep -v '^reports/evidence' | xargs grep -rniE -f config/quality/leak-patterns.txt`
- exit: 1
- 핵심 결과: 매치 없음(패턴 무매치 = 통과). 담당자 이름·전화·이메일 등 개인정보 문자열은 어떤 test·evidence 에도 옮기지 않았다(지어낸 기관 코드 `1234567`·`7654321`, 지어낸 기관명만 사용).

## 우회 (6) 확인 — workflow·ml·fixtures·conformance 무접촉
- cmd: `git diff --name-status 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1..HEAD -- workflow/ adapters/src/main/kotlin/bidvector/adapters/ml/ adapters/src/test/kotlin/bidvector/adapters/ml/ fixtures/ app/src/test/kotlin/bidvector/app/conformance/ | wc -l`
- exit: 0
- 핵심 결과: `0` — 5개 out_of_scope 경로 전부 diff 없음. `agencyId` 조립(3H-2 몫)은 이 slice에서 손대지 않았다.
