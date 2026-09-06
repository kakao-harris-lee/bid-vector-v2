# commands — M1 / 1C (Qualification, 면허 자격 판정 커널)

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절은 만들지 않는다.

HEAD(최종): `50ca8e1`. base_sha: `4a6ca5c4ee5bb666fe786fbe395c5c00be775e75`.

## Q-0 — `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`

- exit: 0
- 핵심 결과: 격리 worktree(scratchpad, 검증 뒤 `git worktree remove --force` + 잔여 디렉터리
  없음 확인)에서 `clean check` 전건 통과 — 295 tasks 전부 executed(캐시 없음).

## Q-1 — `./gradlew --no-build-cache clean check`

- exit: 0
- 핵심 결과: 9 모듈(+build-logic) 전건 `check` 통과(286 tasks). 1A·1A-b 게이트(sizeGate·
  typeShapeGate depth 0·domainApiTypeGate·domainSourceReferenceGate·CPD 관찰)가 새
  `qualification` 도메인 코드 위에서 초록.

## Q-2 — `./gradlew :qualification:test`

- exit: 0
- 핵심 결과: `LicenseEligibilityTest` 17/17(example, 위협 모델 우회 (1)~(6) 대응 포함) ·
  `LicenseEligibilityPropertyTest` 4/4(R-QUAL-06·결측 그룹 폴딩·보유 부재 불변식·그룹
  순서 무관). XML `tests=`/`failures=` 실측.

## Q-3 — `./gradlew :qualification:domainApiTypeGate :qualification:domainSourceReferenceGate :qualification:typeShapeGate :qualification:sizeGate`

- exit: 0
- 핵심 결과: 넷 다 통과. `LicenseVerdict`·`UncertainReason`·`RequirementGroupId`·
  `LicenseValidity`·`OperatorLicenses`·`RequirementRow`·`RequirementCollection` 전부
  `sealed interface`(typeShapeGate 래칫 0 유지) — 실측 중 처음에 `sortedBy`(inline 비교자
  합성 class 의 SourceFile 이 stdlib `Comparisons.kt`)가 `jarContentGate`(`check` 축)를
  깼고, `String.lowercase()`가 `Locale.ROOT`/`Appendable`을 참조해 `domainSourceReferenceGate`
  가 아니라 `ArchitectureGateTest`(app, T-C 이질 패키지 축)가 잡았다 — 둘 다 `sorted()`+
  `CharArray` 직접 조립으로 회피(commit `0d2e995`·`efc8f82` 안에 포함, 별도 수정 커밋 없음
  — 구현 중 RED 로 잡혀 커밋 전에 고쳤다).

## Q-4 — `./gradlew :app:test --tests '*Conformance*'`

- exit: 0
- 핵심 결과: `SharedKernelCorpusConformanceTest` 21/21 — license-*(002·003·004·005·006·
  007·009·012) authoritative 8 이 dynamic test 로 실행·대조 + dispatch 표 완결성 test +
  1B/1C 두 축의 insufficient-evidence 이월 test 둘(money-basis-003 하나 / license-001·
  008·010·011 넷).

## Q-5 — `./gradlew qualityBaseline`

- exit: 0
- 핵심 결과: 실측 갱신, ratchet 위반 없음(`build/reports/quality-baseline/quality-baseline.md`).

## Q-7 — `./gradlew :build-logic:test`

- exit: 0
- 핵심 결과: 1A 승계 test 스위트 무변경 통과(build-logic in_scope 밖 — 회귀 없음 확인).

## Q-6 — 생략

manifest.yaml 을 편집하지 않았다(fixture-curator 소관, decision 19 관례 — 계약 어휘가
fixture 어휘를 따른다). scope.md 조건대로 스윕 생략.

## 변이 실측 (위협 모델 우회 대응 — `qualification/src/main/.../LicenseEligibility.kt` 한시적
편집, 매 회 원본으로 복원 후 `git status --short` 로 무변경 확인, 커밋 없음)

1. **결측 그룹 폴딩을 OR(행마다 별개 그룹)로 바꾼다** — `groupRows` 의 `?: Ungrouped` 를
   `?: Numbered(LmtGrpNo(row.serialNo.value))` 로 치환. 결과: **`:qualification:test`
   2 실패**(example `lmtGrpNo 결측 행은...폴딩된다` — `Ineligible`이었어야 할 것이
   `Eligible`로 나옴, property `...개수와 무관하게...` 도 동반 실패). 원복 확인.
2. **보유 선언 부재를 `Ineligible`로 접는다** — `OperatorLicenses.NotDeclared` 분기의
   반환값을 `Uncertain(OperatorLicensesNotDeclared)` 대신 `Ineligible(emptyMap())`으로
   치환. 결과: **2 실패**(example `보유 면허 선언 부재는 Uncertain이지 Ineligible이 아니다`
   + property `보유 선언 부재는...접히지 않는다`). 원복 확인.
3. **`missingByGroup`에 보유 면허도 포함시킨다(R-QUAL-06 위반)** — `evaluateGroup.missing`
   에서 `filterNot { held }` 를 제거해 합집합 전체를 담게 함. 결과: **3 실패**(example
   둘 + property `missingByGroup은...부분집합이다 — R-QUAL-06`, diff 가 보유 면허가 사유에
   섞인 것을 정확히 보여준다). 원복 확인.

세 변이 모두 조치 뒤 `:qualification:test` 재실행 exit 0(원본과 동일 17/4)로 복원을
확인했다.
