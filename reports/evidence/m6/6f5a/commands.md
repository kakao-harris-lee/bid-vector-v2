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

## 마지막 HEAD 표기
evidence 커밋(이 파일들) 이후 HEAD에서의 재실측 정본은 **verifier**가 낸다(CLAUDE.md
「acceptance 재실측은 evidence 커밋 뒤 HEAD 에서」, evidence-pack 규격 「마지막 HEAD는
verifier·조치 코멘트가 정본」) — 이 표는 그 직전(round 2 마지막 **내용** 커밋 `08397073`)까지의
실측이다.
