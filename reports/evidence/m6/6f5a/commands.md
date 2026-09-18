# M6/6F-5-a — acceptance 명령 로그

HEAD(이 로그의 실측 시점): `694fad4a` (base `ede5d5b`)

## 2026-09-18T22:20:00Z (S-50)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 13 tests, 0 failed — RequirementCollection 세 갈래(DataAbsent·CollectionFailed·
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

## 마지막 HEAD 표기
evidence 커밋(이 파일들) 이후 HEAD에서의 재실측 정본은 **verifier**가 낸다(CLAUDE.md
「acceptance 재실측은 evidence 커밋 뒤 HEAD 에서」, evidence-pack 규격 「마지막 HEAD는
verifier·조치 코멘트가 정본」) — 이 표는 그 직전(694fad4a)까지의 실측이다.
