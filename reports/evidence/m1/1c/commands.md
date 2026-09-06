# commands — M1 / 1C (Qualification, 면허 자격 판정 커널)

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절은 만들지 않는다 — 아래는 verifier r1 수정 라운드 뒤 **최종
재실측**이다(초판 수치는 이 문서가 아니라 git log 가 갖는다).

HEAD(최종): `bb85177`. base_sha: `4a6ca5c4ee5bb666fe786fbe395c5c00be775e75`.

## Q-0 — `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`

- exit: 0
- 핵심 결과: 격리 worktree(scratchpad, 검증 뒤 `git worktree remove --force` + 잔여 디렉터리
  없음 확인)에서 `clean check` 전건 통과 — 295 tasks 전부 executed(캐시 없음).
  `qualification:test` — `LicenseEligibilityTest` 22/22 · `LicenseEligibilityPropertyTest`
  5/5(XML `tests=`/`failures=` 실측, worktree 안에서 직접 확인).

## Q-1 — `./gradlew --no-build-cache clean check`

- exit: 0
- 핵심 결과: 9 모듈(+build-logic) 전건 `check` 통과(286 tasks). 1A·1A-b 게이트(sizeGate·
  typeShapeGate depth 0·domainApiTypeGate·domainSourceReferenceGate·CPD 관찰)가
  `qualification` 도메인 코드(6 파일, 최대 139줄) 위에서 초록.

## Q-2 — `./gradlew :qualification:test`

- exit: 0
- 핵심 결과: `LicenseEligibilityTest` 22/22(example — 위협 모델 우회 (1)~(6) 대응 +
  verifier r1 F-1 PROBE A·D·F-2 구성 거부·F-4 PROBE F2·F3) · `LicenseEligibilityPropertyTest`
  5/5(R-QUAL-06·결측 그룹 폴딩·보유 부재 불변식·그룹 순서 무관·보유 0 이면 Eligible
  없음 — F-1 새 property).

## Q-3 — `./gradlew :qualification:domainApiTypeGate :qualification:domainSourceReferenceGate :qualification:typeShapeGate :qualification:sizeGate`

- exit: 0
- 핵심 결과: 넷 다 통과. `LicenseVerdict`·`UncertainReason`·`RequirementGroupId`·
  `LicenseValidity`·`OperatorLicenses`·`RequirementRow`·`RequirementCollection` 전부
  `sealed interface`(typeShapeGate 래칫 0 유지). 구현 중 겪은 게이트 회피 셋:
  (a) `sortedBy`(inline 비교자 합성 class 의 SourceFile 이 stdlib `Comparisons.kt`)가
  `jarContentGate` 를 깨 `sorted()`(자연 순서)로 회피 (b) `String.lowercase()`가
  `Locale.ROOT`/`Appendable`을 참조해 `ArchitectureGateTest`(app, T-C 이질 패키지 축)가
  잡아 `CharArray` 직접 조립으로 회피 (c) verifier r1 수정 뒤 detekt `TooManyFunctions`
  (`LicenseEligibility.kt` 12 함수, 한도 11)를 `LicenseGroupFolding.kt` 분리(그룹 폴딩
  관심사)로 회피. 셋 다 임계값·정책 파일은 무변경.

## Q-4 — `./gradlew :app:test --tests '*Conformance*'`

- exit: 0
- 핵심 결과: `SharedKernelCorpusConformanceTest` 21/21 — license-*(002·003·004·005·006·
  007·009·012) authoritative 8 이 dynamic test 로 실행·대조(verifier r1 F-7 수정 뒤
  runner 가 `permsnIndstrytyList` 를 실제로 읽지만 authoritative 8 입력엔 그 필드가 없어
  결과 불변) + dispatch 표 완결성 test + 1B/1C 두 축의 insufficient-evidence 이월 test
  둘(money-basis-003 하나 / license-001·008·010·011 넷).

## Q-5 — `./gradlew qualityBaseline`

- exit: 0
- 핵심 결과: qualification 6 파일·513줄·최대 139줄·타입 40·최대 멤버 26·depth 0·
  interfaces 1(래칫 위반 없음, `build/reports/quality-baseline/quality-baseline.md`).

## Q-7 — `./gradlew :build-logic:test`

- exit: 0
- 핵심 결과: 1A 승계 test 스위트 무변경 통과(build-logic in_scope 밖 — 회귀 없음 확인).

## Q-6 — 생략

manifest.yaml 을 편집하지 않았다(fixture-curator 소관, decision 19 관례 — 계약 어휘가
fixture 어휘를 따른다). scope.md 조건대로 스윕 생략.

## 변이 실측 (위협 모델 우회 대응 — 한시적 편집, 매 회 원본으로 복원 후 `git status
--short` 로 무변경 확인, 커밋 없음)

**최초 구현 라운드(0d2e995~262eec0 대상, 3건)**

1. **결측 그룹 폴딩을 OR(행마다 별개 그룹)로 바꾼다** — 2 실패(example + property). 원복 확인.
2. **보유 선언 부재를 `Ineligible`로 접는다** — 2 실패(example + property). 원복 확인.
3. **`missingByGroup`에 보유 면허도 포함시킨다(R-QUAL-06 위반)** — 3 실패(example 둘 +
   property). 원복 확인.

**verifier r1 수정 라운드(F-1·F-4 대상, 2건, 이 레인 실측)**

4. **F-1 회귀 재현** — `restrictedSatisfied` 를 `restrictedRows.isNotEmpty() &&` 없이
   원래 공허 충족 형태로 되돌린다. 결과: **3 실패**(example PROBE A·D 둘 + property
   `보유 면허가 0이면...Eligible이 나오지 않는다`). 원복 확인.
5. **F-4 회귀 재현** — `KEY_STRIP_CHARS` 를 이전 판(반각 괄호·가운뎃점 하나만)으로
   되돌린다. 결과: **2 실패**(example 전각 괄호·나카구로 test 둘). 원복 확인.
6. **F-3 재확인(변이가 아니라 존재 확인)** — `licenseComparisonKey` 본문에 하드코딩
   `mapOf("종합건설업" to "토목공사업", …)` 를 끼운다. 결과: **`clean check` exit 0**(어떤
   게이트도 잡지 못함 — verifier M7 과 같은 결과, checklist.md 알려진 제한 ⑦로 등재).
   원복 확인.

여섯 변이 모두 조치 뒤 `:qualification:test` 재실행 exit 0(22/5)로 복원을 확인했다.
