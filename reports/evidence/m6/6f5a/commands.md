# M6/6F-5-a — acceptance 명령 로그

HEAD(이 로그의 실측 시점): `694fad4a` (base `ede5d5b`)

## 2026-09-18T22:20:00Z (S-50)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 12 tests, 0 failed — RequirementCollection 세 갈래(DataAbsent·CollectionFailed·
  Collected) 왕복, groupNo 결측·제로패딩·재저장 전이·공고 간 격리 각각 통과.

## 2026-09-18T22:35:00Z (S-51)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*StoredRequirementLicenseGateTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 6 tests, 0 failed — DataAbsent/CollectionFailed/Eligible/Ineligible/
  OperatorLicensesNotDeclared/AND 폴딩 각각 리터럴 기대값과 대조(항진명제 회피).

## 2026-09-18T22:45:00Z (S-52·S-53)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*QualificationAdapterDependencyTest*' --tests '*QualificationGateRegistrationTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 6 tests, 0 failed — 바이트코드 상수 풀 허용 루트 판정(양성 대조 둘 포함) +
  LicenseEligibility 참조 단언 + gate-tests.properties 등재 완결성(양성 대조 포함).

## 2026-09-18T22:52:00Z (S-10 최초 라운드 — detekt/ktlint/CPD 위반 발견)
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `adapters:cpdCheck`(PersistenceJdbcSupport.getTextList와 6줄 토큰 중복) ·
  `adapters:detekt`(JdbcRequirementStore.kt MagicNumber 6건, StoredRequirementLicenseGateTest.kt
  MaxLineLength 2건) 실패. 수정 커밋(694fad4a)으로 해소.

## 2026-09-18T22:55:00Z (S-10 재실측)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — `adapters:cpdCheck`·`adapters:detekt`·`ktlintMainSourceSetCheck`
  포함 전건 통과.

## 2026-09-18T22:56:10Z (S-11)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE) — `build/reports/quality-baseline/quality-baseline.md` 산출.

## 2026-09-18T22:57:30Z (S-20)
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: 「완료 — Kotlin 전건 + Python 전건 통과」— Kotlin `check`+`qualityBaseline`과
  Python ruff/mypy/import-linter/pytest/설계 래칫/재활용 출처 대조/버전 대조/wheel
  재수출 확인까지 전부 통과. 이 slice는 Python 파일을 건드리지 않아 Python 축은 회귀
  없음의 확인(양성이 아니라 무변경 확인)이다.

## round 1(수정 라운드, D-6F5-9~11) — 판정 대상 `eac38c85` 뒤 재실측

verifier `not-ready`(HIGH-2) · code-reviewer(HIGH-1) 수정 뒤, 마지막 산출물 커밋
`b7da46af`에서 재측정.

## 2026-09-18T23:20:00Z (S-50~53, 개별)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --rerun-tasks`
- exit: 0 — 핵심 결과: 12 tests, 0 failed(무변경 축, D-6F5-11이 거동을 안 바꿨음을 재확인).
- cmd: `./gradlew --no-daemon :adapters:test --tests '*StoredRequirementLicenseGateTest*' --rerun-tasks`
- exit: 0 — 핵심 결과: 7 tests, 0 failed(D-6F5-10이 더한 `Collected 빈 rows` 케이스 포함, 6→7).
- cmd: `./gradlew --no-daemon :adapters:test --tests '*QualificationAdapterDependencyTest*' --rerun-tasks`
- exit: 0 — 핵심 결과: 8 tests, 0 failed(D-6F5-9·D-6F5-10이 더한 부재 단언 2 + 양성 대조 2,
  이 class 단독 4→8 — round 0의 "6 tests"는 `QualificationGateRegistrationTest`와 합산한
  수치였다).
- cmd: `./gradlew --no-daemon :adapters:test --tests '*QualificationGateRegistrationTest*' --rerun-tasks`
- exit: 0 — 핵심 결과: 2 tests, 0 failed(무변경).

## 2026-09-18T23:35:00Z (S-10)
- cmd: `./gradlew --no-daemon check`
- exit: 0 — 핵심 결과: BUILD SUCCESSFUL(수정 커밋 4개 각각 뒤에도 재확인, 아래 「닫힘 판정」 참고).

## 2026-09-18T23:36:00Z (S-11)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0 — 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE).

## 2026-09-18T23:49:58Z (S-20)
- cmd: `./tools/one-command-check.sh`
- exit: 0 — 핵심 결과: 「완료 — Kotlin 전건 + Python 전건 통과」(round 0과 동일 결론, Python
  축은 이 라운드도 무변경).

## 닫힘 판정 — 변이 셋 재현(버릴 clone, `git clone --no-hardlinks`)

| # | 심은 변이 | 결과(수정 뒤) |
| --- | --- | --- |
| ① | `verdictFor`에 `LICENSE_QUALIFICATION_POLICY.resolve(...)` 전체 한정 좌표 삽입 | **RED** — `QualificationAdapterDependencyTest`(정책 로더 부재 단언 실패, `LicensePolicyKt` 문자열 검출) + 전건 `check` exit 1 |
| ② | `judge(...)` 호출은 남긴 채 `Collected(빈 rows)` 경로만 `Eligible(emptySet())`로 반환 갈아치우기 | **RED** — `QualificationAdapterDependencyTest`(`LicenseVerdict$` 부재 단언 실패) **및** `StoredRequirementLicenseGateTest`(`Collected 빈 rows` 케이스가 `Uncertain(RequirementDataAbsent)` 기대값과 불일치) 둘 다 독립적으로 검출 |
| ③ | `getNullableTextArray`/`setNullableTextArray`를 D-6F5-11 이전(축어 복사에 가까운 재서술) 판으로 되돌리기 | **RED** — `:adapters:cpdCheck --rerun-tasks` exit 1(PersistenceJdbcSupport.getTextList와 6줄/60토큰 중복 재검출) |

셋 다 표본은 커밋하지 않고 검증 뒤 clone을 삭제했다.

## round 2(수정 라운드, verifier r2 HIGH-1·LOW-1) — 판정 대상 `d550ee05` 뒤 재실측

verifier r2 `not-ready`(신규 HIGH 1 · MEDIUM 1 · LOW 3) 수정 뒤, 마지막 **내용** 커밋
`08397073`(D-6F5-14·D-6F5-15)에서 재측정.

## 2026-09-19T(S-50~53, 통합)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --tests '*StoredRequirementLicenseGateTest*' --tests '*QualificationAdapterDependencyTest*' --tests '*QualificationGateRegistrationTest*' --rerun-tasks`
- exit: 0 — JUnit XML 실측: `12 / 7 / 8 / 2`, failures 0 · skipped 0(round 1과 동일 —
  D-6F5-14·D-6F5-15는 범위·문면만 바꿔 test 개수를 바꾸지 않는다).

## 2026-09-19T(S-10)
- cmd: `./gradlew --no-daemon check`
- exit: 0 — BUILD SUCCESSFUL.

## 2026-09-19T(S-11)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0 — BUILD SUCCESSFUL(UP-TO-DATE).

## 2026-09-19T(S-20)
- cmd: `./tools/one-command-check.sh`
- exit: 0 — 「완료 — Kotlin 전건 + Python 전건 통과」.

## 2026-09-19T(추가)
- cmd: `./gradlew --no-daemon :leakPatternGate --rerun-tasks`
- exit: 0.
- cmd: `./gradlew --no-daemon :adapters:cpdCheck --rerun-tasks`
- exit: 0(D-6F5-11 위임판 재확인, 무변경).
- 비밀값 참조형 스캔(`grep -rniE -f config/quality/leak-patterns.txt <이 라운드가 편집한
  경로>`) — exit 1(매치 없음 = 통과).

## round 2 닫힘 판정 — D-6F5-14 변이 둘 재현(버릴 clone, `git clone --no-hardlinks`)

| # | 심은 변이 | 결과(수정 뒤) |
| --- | --- | --- |
| MUT-R2-1 | 새 형제 파일(`internal fun readPolicyDirectly() = LICENSE_QUALIFICATION_POLICY.resolve(LocalDate.now())`)을 두고 `StoredRequirementLicenseGate`가 호출 | **RED** — `QualificationAdapterDependencyTest`(패키지 전체 정책 로더 부재 단언 실패, clue가 `PolicyDirectReadMutationKt.class`를 지목) |
| MUT-R2-2 | 새 형제 파일에서 `LicenseVerdict.Eligible(emptySet())` 조립 함수를 두고, 게이트가 게이트 test가 덮지 않는 경로(`Unparsable` 행이 섞인 `Collected`)에서 반환 | **RED** — `QualificationAdapterDependencyTest`(패키지 전체 `LicenseVerdict` subtype 좌표 부재 단언 실패, clue가 `VerdictAssemblyMutationKt.class`를 지목) **및** `StoredRequirementLicenseGateTest` 7/7 그대로 green(게이트 test는 이 경로를 덮지 않아 무변화 — 부재 단언이 단독으로 잡는다는 verifier r2 서술을 재확인) |

둘 다 표본은 커밋하지 않고 검증 뒤 clone을 삭제했다. 정당한 사용(정상 경로 —
`judge(...)` 결과를 그대로 반환)과 같은 패키지의 다른 두 클래스(`JdbcRequirementStore`·
`RequirementRowMapping`)는 이 라운드가 넓힌 범위에도 걸리지 않는다(위 S-50~53 통합 실행이
8/8 green으로 확인).

## round 3(수정 라운드, verifier r3 MEDIUM-b) — 판정 대상 `91eafb78` 뒤 재실측

verifier r3 `ready-for-review`(산출물 medium 3 · low 4) 처분 중 산출물 MEDIUM 하나
(D-6F5-16)를 이 slice가 닫는다. 마지막 **내용** 커밋 `584f226f`(D-6F5-16)에서 재측정.

## 2026-09-19T(S-50~53 + CleanMigrationCheckTest, 통합)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --tests '*StoredRequirementLicenseGateTest*' --tests '*QualificationAdapterDependencyTest*' --tests '*QualificationGateRegistrationTest*' --tests '*CleanMigrationCheckTest*' --rerun-tasks`
- exit: 0 — JUnit XML 실측: `12 / 7 / 8 / 2 / 6`. `CleanMigrationCheckTest`가 D-6F5-16이
  더한 test 둘로 4→6(round 1 이전부터 있던 개수 축·COL-06/H-3 부가·outbox·edit_session
  넷 + 신규 열거 셋 본문 고정·결합식 넷 본문 대조 둘 = 6).

## 2026-09-19T(S-10)
- cmd: `./gradlew --no-daemon check`
- exit: 0 — BUILD SUCCESSFUL(337 actionable tasks: 43 executed, 294 up-to-date).

## 2026-09-19T(S-11)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0 — BUILD SUCCESSFUL(UP-TO-DATE).

## 2026-09-19T(S-20)
- cmd: `./tools/one-command-check.sh`
- exit: 0 — 「완료 — Kotlin 전건 + Python 전건 통과」.

## 2026-09-19T(추가)
- 비밀값 참조형 스캔(`grep -rniE -f config/quality/leak-patterns.txt <이 라운드가 편집한
  경로>`) — exit 1(매치 없음 = 통과).
- evidence 안 축어 좌표 스캔(`grep -rnE '[A-Za-z0-9_]+(\.kt|\.sql|\.properties):[0-9]+'
  reports/evidence/m6/6f5a/`) — exit 1(매치 없음, 0건).

## round 3 닫힘 판정 — D-6F5-16 변이 셋 재현(버릴 clone, `git clone --no-hardlinks`)

`adapters/src/main/resources/db/migration/V13__notice_requirement.sql`을 각각 변이하고
`:adapters:test --tests '*CleanMigrationCheckTest*' --rerun-tasks`로 재고, 한 변이를 되돌린
뒤 다음 변이를 심는 순서로(같은 clone, `git checkout --` 로 원판 복귀) 셋 다 확인했다.

| # | 심은 변이 | 결과 |
| --- | --- | --- |
| MUT-R3-1 | `notice_requirement_row`의 `license_names` CHECK(`license_names IS NULL OR cardinality(license_names) > 0`)를 `CHECK (TRUE)`로 약화 | **RED** — `축8 부가 — notice_requirement_row 결합식 넷이 본문에 살아 있다(D-6F5-16)`, `AssertionFailedError: expected:<true> but was:<false>`(license_names 결합식이 상수 풀 대조에서 사라짐) |
| MUT-R3-2 | `notice_requirement.status` 열거에 `'DUMMY'` 추가 | **RED** — `축8 부가 — notice_requirement·notice_requirement_row 열거 셋이 본문으로 정확히 고정된다(D-6F5-16)`, `expected:<...COLLECTED'::text])))> but was:<...COLLECTED'::text, 'DUMMY'::text])))>` |
| MUT-R3-3 | `notice_requirement_row`의 `(kind = 'PARSED') = (source_field IS NOT NULL)` 결합식의 `=`를 `OR`로 약화 | **RED** — `축8 부가 — notice_requirement_row 결합식 넷이 본문에 살아 있다(D-6F5-16)`, `AssertionFailedError: expected:<true> but was:<false>`(항등식 문구가 상수 풀 대조에서 사라짐) |

셋 다 개수 축(`축8 CHECK 개수가...`)은 **그대로 green**이었다 — 개수만 보는 축은 이 결함
클래스를 애초에 못 잡는다는 verifier r3 서술을 재확인한다. 표본은 커밋하지 않고 clone을
삭제했다.

**과잉 차단 확인** — 본문 단언 다섯(열거 셋 `shouldBe` + 결합식 넷 `contains`)은 이
표에 없는 **정당한 미래 변경**(예: `notice_requirement_row`에 새 nullable 컬럼을 더하는
것)을 막지 않는다 — 그 변경이 이 다섯 CHECK의 텍스트 자체를 바꾸지 않는 한 본문이
그대로라 초록이다. 열거 값 자체를 늘리는 변경(예: `source_field`에 세 번째 값 추가)은
의도적으로 이 test를 RED로 만든다 — `outbox_state_check`(D-M4-5 (a))와 같은 「열거가
넓어지는 것 자체가 재평가 지점」 설계이지 과잉 차단이 아니다.

**버전 의존성 판단** — `pg_get_constraintdef()`의 정규화 형태(`= ANY (ARRAY[...])`,
괄호 중첩)는 이 파일이 이미 `outbox_state_check`·`edit_session_state_check`·COL-06·H-3
넷에서 써 온 것과 같은 형태다 — `PersistenceTestSupport`가 고정한 `postgres:16.4`
(Testcontainers) 하나로만 이 저장소 전체가 검증되므로, 이번 신설이 새 버전 의존성을
들이지 않는다(기존 넷과 같은 위험을 공유할 뿐 늘리지 않는다).

## round 4(수정 라운드, verifier r4 MEDIUM) — 판정 대상 `234079fd` 뒤 재실측

verifier r4 `ready-for-review`(산출물 medium 1 · low 2 · 장부층 medium 1) 처분 중 산출물
MEDIUM 하나(D-6F5-21)를 이 slice가 닫는다. 마지막 **내용** 커밋 `d8e37fa3`(D-6F5-21)에서
재측정.

## 2026-09-19T(S-50~53 + CleanMigrationCheckTest, 통합)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --tests '*StoredRequirementLicenseGateTest*' --tests '*QualificationAdapterDependencyTest*' --tests '*QualificationGateRegistrationTest*' --tests '*CleanMigrationCheckTest*' --rerun-tasks`
- exit: 0 — JUnit XML 실측: `12 / 7 / 8 / 2 / 6`. `CleanMigrationCheckTest`는 여전히
  6(D-6F5-21이 「결합식 넷」 test 하나를 「집합 등식」 test 하나로 1:1 대체했을 뿐 개수는
  안 늘었다).

## 2026-09-19T(S-10)
- cmd: `./gradlew --no-daemon check`
- exit: 0 — BUILD SUCCESSFUL(337 actionable tasks: 43 executed, 294 up-to-date).

## 2026-09-19T(S-11)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0 — BUILD SUCCESSFUL(UP-TO-DATE).

## 2026-09-19T(S-20)
- cmd: `./tools/one-command-check.sh`
- exit: 0 — 「완료 — Kotlin 전건 + Python 전건 통과」.

## 2026-09-19T(추가)
- 비밀값 참조형 스캔(`grep -rniE -f config/quality/leak-patterns.txt <이 라운드가 편집한
  경로>`) — exit 1(매치 없음 = 통과).
- evidence 안 축어 좌표 스캔(`grep -rnE '[A-Za-z0-9_]+(\.kt|\.sql|\.properties):[0-9]+'
  reports/evidence/m6/6f5a/`) — exit 1(매치 없음, 0건).

## round 4 닫힘 판정 — D-6F5-21 변이 넷 재현(버릴 clone, `git clone --no-hardlinks`)

`adapters/src/main/resources/db/migration/V13__notice_requirement.sql`을 각각 변이하고
`:adapters:test --tests '*CleanMigrationCheckTest*' --rerun-tasks`로 재고, 되돌린 뒤 다음
변이를 심는 순서로(같은 clone, `git checkout --`) 넷 다 확인했다.

| # | 심은 변이 | 결과 |
| --- | --- | --- |
| MUT-R4-1 | `(kind='PARSED') = (source_field IS NOT NULL)` 항등식을 제자리에서 `CHECK (((kind='PARSED') = (source_field IS NOT NULL)) OR TRUE)`로 약화 | **RED** — `축8 부가 — notice_requirement_row CHECK 본문 집합이 정확히 고정된다(D-6F5-21)`. Postgres가 `OR true`를 평탄화하지 않아 문자열 자체가 달라졌다(`CHECK ((((kind = 'PARSED'::text) = (source_field IS NOT NULL)) OR true))`) — 집합이 달라져 실패 |
| MUT-R4-2 | 같은 방식으로 `(kind='PARSED') = (license_names IS NOT NULL)` 항등식 약화 | **RED** — 같은 test, 같은 형태로 집합 불일치 |
| MUT-R4-3 | `kind = 'PARSED' OR group_no IS NULL` 결합식을 **삭제**(CHECK 절 자체 제거) | **RED** — 개수 축(`축8 CHECK 개수가...`)과 집합 등식 test **둘 다** 실패(집합이 5개로 줄어듦) |
| MUT-R4-4 | CHECK와 무관한 컬럼(`extraction_note TEXT`)을 `notice_requirement_row`에 추가 | **GREEN**(과잉 차단 없음 — CHECK 본문 집합이 안 바뀌어 `*CleanMigrationCheckTest*` 6/6 그대로 통과) |

셋(①②③)은 표본을 커밋하지 않고 clone을 삭제했다. MUT-R4-1·2가 각각 다른 항등식에서
같은 실패 유형을 내는 것은 D-6F5-21의 「어떤 제자리 편집도 집합 자체를 바꾼다」는
주장이 두 항등식 모두에서 성립함을 보인다.

## 마지막 HEAD 표기
evidence 커밋(이 파일들) 이후 HEAD에서의 재실측 정본은 **verifier**가 낸다(CLAUDE.md
「acceptance 재실측은 evidence 커밋 뒤 HEAD 에서」, evidence-pack 규격 「마지막 HEAD는
verifier·조치 코멘트가 정본」) — 이 표는 그 직전(round 4 마지막 **내용** 커밋 `d8e37fa3`)까지의
실측이다.
