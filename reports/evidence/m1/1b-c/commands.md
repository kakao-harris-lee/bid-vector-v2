# commands — M1 / 1B-c

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절은 만들지 않는다 — 그 기록은 git log 와 리뷰 verdict 가 갖는다.

**verifier r1 L-2 — `--offline` 갈래.** `scope.md` `acceptance_commands` 문면에는
`--offline` 플래그가 없다 — **정본은 무플래그**다. 이 레인의 로컬 실행은 반복 속도를 위해
`--offline` 을 동반했고(RO 의존 캐시가 이미 다 채워져 있어 결과가 같다), 아래 최종 수치는
**무플래그로 재실행해 얻었다**(verifier r1 이 이미 그렇게 실행해 exit 0 을 확인한 방식과
같다). 이후 라운드에서 다시 로컬 반복 속도가 필요하면 `--offline` 을 동반하되 이 갈래를
다시 적지 않는다.

## C-0 — `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`

- HEAD(최종): `ae0d9ad`(verifier r1 M-1~M-3·L-1 처리 + 크기 한도 분리 + fixture 7 위임 철회).
- exit: 0
- 핵심 결과: 격리 worktree 에서 `clean check` 전건 통과(262 tasks). `app:test` 12/12
  (conformance runner 10 case + dispatch 완결성·insufficient-evidence 필터 test 둘) ·
  `shared-kernel:test` `CompileFailureHarnessTest` 21/21(XML `tests=` 실측). worktree 는
  검증 뒤 `git worktree remove`(scratchpad 경로, 잔여 없음 확인) — evidence-pack 규격.

## C-1 — `./gradlew --no-build-cache clean check`

- HEAD(최종): `ae0d9ad`.
- exit: 0
- 핵심 결과: 9 모듈 전건 `check` 통과(253 tasks). **D5 기제 변경(아래 참고) 전 최초 실측은
  4 failures** 였다 — `shared-kernel testFixtures` 채택 직후 `packageOwnershipGate`·
  `sourceSetLayoutGate`·`ArchitectureGateTest`(ArchUnit 프로덕션 스캔) 셋이 동시에
  깨졌다(각 사유는 「D5 기제 변경」 절). **verifier r1 수정 라운드 중간에도 한 번 더
  실패**했다 — `SharedKernelCorpusConformanceTest.kt` 가 506 줄로 `sizeGate` 500 줄
  한도를 넘어(M-1~M-3·L-1 추가분), `CorpusExecutors.kt`(신규, 251 줄)로 관심사를 갈라
  재해소했다(기계적 분할이 아니라 「fixture 인프라」 대 「계약 dispatch」 분리 — 각 파일
  KDoc 참고). 최종 재실측 exit 0.

## C-2 — `./gradlew :shared-kernel:test :app:test`

- HEAD(최종): `ae0d9ad`.
- exit: 0
- 핵심 결과: `shared-kernel:test` — `CompileFailureHarnessTest` 21/21(기존 19 + fixture 12
  둘). `app:test` — `SharedKernelCorpusConformanceTest` 12/12(rate-unit 5·money-basis
  5 case 대조 10 + dispatch 표 완결성 test + insufficient-evidence 필터 test). scope.md
  acceptance 문면의 C-2 는 `:shared-kernel:test` 만 들었으나 실행자(runner)가 `app` 에
  사는 것이 확인된 뒤 팀장이 **`:app:test` 를 더해 acceptance 문면 자체를 갱신**했다
  (`scope.md` 참고 — 「C-2 보강」 별도 표기 불필요, 이미 명령 자체에 반영됨).

## C-3 — `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate`

- HEAD(최종): `ae0d9ad`.
- exit: 0
- 핵심 결과: 둘 다 UP-TO-DATE(전건 exit 0) — `shared-kernel/src/main` 무변경 확인(이 slice
  는 그 트리를 건드리지 않는다, verifier r1 수정 라운드도 포함).

## C-4 — `./gradlew qualityBaseline`

- HEAD(최종): `ae0d9ad`.
- exit: 0
- 핵심 결과: UP-TO-DATE(전건 exit 0).

## 변이 실측 — verifier r1 재현 셋(M-1·M-2·M-3)

최종 HEAD `ae0d9ad` 에서 재실측, 전부 원복 후 `git diff` 빈 것 확인.

1. **M-1(fallback 부재)** — `fixtures/input/money-basis-006.json` 의
   `row.declaredVatTreatment` 를 로컬에서 `null` 로 되돌리고 재실행 →
   `IllegalArgumentException: 이 case 는 declaredVatTreatment 가 명시 선언(예: UNKNOWN)
   이어야 한다` 로 `money-basis-006` 만 FAILED(다른 11 은 그대로 초록). 접기가 완전히
   없어졌음을 확인한다.
2. **M-2(declaredUnit 주도)** — `fixtures/input/rate-unit-005.json` 의 `declaredUnit`
   을 `"percent"` → `"fraction"` 으로 바꾸고 재실행 → `rate-unit-005` 만 FAILED
   (`Rate.ofFraction(0.875)` ≠ 기대값 `Rate.ofFraction(0.00875)`). executor 가 실제로
   그 필드를 읽는다.
3. **M-3(존재 단언)** — `fixtures/manifest.yaml` 의 `money-basis-002` `verified_paths`
   에 `- "$.bogusNotThere"` 를 추가하고 재실행 → `money-basis-002` FAILED(`경로
   $.bogusNotThere 가 actual projection 에 없다`). 정정 전에는 이 변이가 **초록**이었다
   (verifier r1 우회 7 재현).

## D5 기제 변경 — (b′) testFixtures → (d) 공개 API 동등 비교 (2026-09-05)

**사유.** D5(b′)(`java-test-fixtures`)를 shared-kernel 에 적용한 뒤 `./gradlew
--no-build-cache clean check --offline --continue` 로 실측한 결과 **4 failures** —
Phase 2 조사(§8)가 확인한 것은 컴파일 성공과 `moduleDependencyGate` 통과뿐이었고, 아래
셋은 조사 범위 밖이었다:

1. `:shared-kernel:packageOwnershipGate` FAILED — `build-logic/src/main/kotlin/
   bidvector.kotlin-conventions.gradle.kts:134` 의 `expectedSourceSets = setOf("main",
   "test")` 가 하드코딩. `testFixtures` 가 「예상 밖 source set」으로 잡히고, 그 산출물이
   `verifiedSources`(main 만)에 없어 「게이트를 통과한 소스가 아니다」로 이중 실패.
2. `:shared-kernel:sourceSetLayoutGate` FAILED — 같은 파일 줄 191 에 같은 하드코딩. 「source
   set 집합이 다르다 — 기대 [main, test], 실제 [main, test, testFixtures]」.
3. `bidvector.app.architecture.ArchitectureGateTest > 도메인 모듈이 허용 목록 밖을 보지
   않는다()` FAILED — ArchUnit `ImportOption.DoNotIncludeTests()` 가 "test" 정확
   세그먼트만 거르고 "testFixtures" 는 안 걸러, testFixtures 산출물이 production
   바이트코드로 스캔된다. `Money.projectProvenance()` 의 `::class.simpleName`
   (kotlin.reflect 호출)이 허용 목록 밖이라 걸렸다 — 이 한 호출을 없애도 **testFixtures
   안의 어떤 코드든 앞으로 영구히 domain 순수성 규칙(외부 import 전면 금지) 적용을
   받는다**는 구조적 사실은 남는다.

**셋 다 `build-logic` 코드 자체(scope.md in_scope 밖) 수정 없이는 해소되지 않는다** — 이
slice 한 곳의 문제가 아니라 전 모듈 공유 아키텍처 게이트의 맹점이라 하네스 slice(1A-b) 몫
이다. 운영자 사후 확인 하 팀장 결정(D5(d)) — testFixtures 를 쓰지 않는다. `Rate.fraction`
(internal, verifier r1 L-4)은 `Rate` 가 `data class` 라는 사실로 우회한다: 기대값으로 만든
`Rate.ofFraction(expected)` 과 실제 산출을 `==`(구조적 동등)로 비교하면 `internal` 성분을
직접 읽지 않고도 값이 잠긴다(양쪽 다 `normalized` 를 거치므로 P-1a 왕복 성질이 동등을
보증). `Fact`·`Provenance` 판별은 소진 `when`(리플렉션 금지, sealed 라 컴파일러가 소진을
강제). shared-kernel 은 fixture 12(컴파일 fixture, `src/test`) 밖 완전 무접촉으로
되돌렸다 — `build.gradle.kts`·`src/testFixtures/` 전부 제거, `git diff HEAD --
shared-kernel/build.gradle.kts` 빈 것 확인.

**알려진 제한·이월** — `OPEN-1BC-TESTFIXTURES-GATE` 문면을 이 셋으로 확장한다: 1차 게이트
(`moduleDependencyGate`)의 `testFixtures*` 버킷 사각(조사 §8)뿐 아니라
`packageOwnershipGate`·`sourceSetLayoutGate` 의 `expectedSourceSets` 하드코딩과
ArchUnit `DoNotIncludeTests()` 가 `testFixtures` 를 인식하지 못하는 사실도 같은 OPEN 이
받는다 — 하네스 slice 1A-b 가 `java-test-fixtures` 를 다시 채택하려는 향후 slice를 위해
네 지점을 함께 고쳐야 한다.

## 변이 실측 둘 (팀장 지시)

1. **값 변이** — `fixtures/expected/rate-unit-005.json` 의 `$.rate.fraction` 을 로컬에서
   `0.00875` → `0.5` 로 바꾸고 재실행 → `rate-unit-005` 만 FAILED(`Rate.ofFraction(0.5) !=
   Rate.ofFraction(0.00875)` 불일치). 원복 후 `git diff` 빈 것 확인.
2. **dispatch 표 제거** — `RATE_EXECUTORS` 에서 `"rate-unit-005"` 항목을 로컬에서 빼고
   재실행 → `rate-unit-005`(개별 dynamic test, `dispatch 표에 없는 case` 예외) **와**
   `dispatch 표 밖의 authoritative case 가 없다` 전용 test 둘 다 FAILED. 원복 후 `git
   diff` 빈 것 확인.

## C-0~C-4(Gradle acceptance) — 이월 없음, ①②③(corpus 레인)의 몫인 **C-5~C-10** 은 아래.
각 커밋 뒤에 전건을 다시 돌렸다 — 아래 수치는 **`6ef8fb5`**(계약 정정 포함) 시점이다.

## C-5 — `python3 fixtures/tools/mutation_sweep_adversarial.py`

- exit: 0
- 핵심 결과: **강등 대상 0 · 잔존 authoritative 28.** 착수 시점 18 에서 1B 축 열이 돌아왔고
  (`rate-unit` 다섯 · `money-basis` 001·002·004·005·006) **1B 축 밖 18 은 집합이 그대로다**
  (base `a5ea955` 의 authoritative 집합과 대조: 추가 10 · 빠짐 0). `money-basis-003` 은 이월로
  `insufficient-evidence` 에 남는다.
- 함께 도는 것: **술어 self-check 22 건 통과**(`manifest_contract.self_check()`, corpus 를 읽지
  않는 격리 검사). 동결 상태의 `holds()` 에서 **15 건 실패(RED)** 를 먼저 확인하고 구현했다.

## C-6 — `python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml`

- exit: 0
- 핵심 결과: manifest reader 와 PyYAML 이 **63 case** 에서 일치. 새 술어 키(`is-present` 항목의
  `operand` 부재 포함)를 둘이 같게 읽는다.

## C-7 — `python3 fixtures/tools/mutation_sweep_targeted.py`

- exit: 0
- 핵심 결과: 통과 자리 **둘**. ① `license-006` **대조군(무변이)** — 착수 시점과 같다.
  ② `money-basis-006` 의 **`$.eligibleForAuthoritativeCorpus` 단독 뒤집기** — 2026-09-05 계약
  정정으로 적격성 축이 `verified_paths` 에서 빠져(1D 이관, `OPEN-1BC-ELIGIBILITY`) **B7 의 셋째
  갈래가 다시 열린 자리**다. 표적을 지우지 않고 남겨 매 실행 눈에 보이게 뒀다.
  나머지 세탁 변이체(B7 의 과세·provenance·전체 · B8 표기 셋)는 **전부 caught** 다 — 착수
  시점에는 그 case 가 강등돼 있어 아예 돌지 않았다.

## C-8 — `python3 fixtures/tools/manifest_prose_consistency.py`

- exit: 0
- 핵심 결과: 불일치 **case id 7 · (블록,case) 10**. 착수 시점 8·11 에서 **하나 줄었다** —
  `rate-unit-004` 가 `authoritative` 로 돌아와 그 자리가 낡은 덮개 주장이 아니게 됐다.
  **새로 생긴 불일치는 없다**(나머지 열 자리는 착수 시점과 같은 (case, 사유) 짝이고 줄 번호만
  밀렸다).

## C-9 — `python3 fixtures/tools/check_legacy_numbers.py`

- exit: 0
- 핵심 결과: legacy-number hits **0**. 재추출이 실은 수는 `100`·`1`·`0.875`·`0.00875` 뿐이고
  넷 다 `FORBIDDEN` 밖이다(정정으로 `rate-unit-003`·`004` 에서는 수가 아예 사라졌다).

## C-10 — 11 case 의 `input_file`·`expected_file` SHA-256 재계산 후 manifest 값과 대조

- exit: 0
- 핵심 결과: **63 case 126 해시 전수 대조, 불일치 0.** 값이 바뀐 파일은 기대값 **9** · 입력 **4**
  이고(11 은 처분한 case 수다 — verifier r1 L-4), 전부 해당 case 의 `change_history` 에
  `previous_expected_sha256`/`previous_sha256` 을 실었다. `golden-manifest.json` 이 case 별
  `sha256`·`expected_sha256` 을 함께 싣고(L-5) 이 명령이 그것을 재대조한다.

## 무해성 대조 (착수 시점 기준선)

기준선은 `_workspace/m1-1b-c/01_scout_preflight.md` §5 가 HEAD `edbeee5` 에서 잰 값이고, 이
레인이 **편집 직전에 다시 재서** 같은 값임을 확인한 뒤 그 위에서 작업했다.

| 도구 | 착수 시점 | ① 뒤 | ③ 뒤 | 계약 정정 뒤 |
| --- | --- | --- | --- | --- |
| C-5 강등 / 잔존 | 0 / 18 | 0 / 18 | 0 / 28 | 0 / 28 |
| C-7 통과 자리 | `license-006` 대조군 | 같음 | 같음 | **둘**(대조군 + 적격성) |
| C-8 불일치 | 8 · 11 | 8 · 11 | 7 · 10 | 7 · 10 |
| C-9 hits | 0 | 0 | 0 | 0 |

**계약 정정은 authoritative 수를 바꾸지 않았다** — 잠그는 경로가 줄었을 뿐 어느 case 도
내려가지 않았고, 1B 축 밖 18 은 base 집합과 여전히 같다(빠짐 0).

**① 뒤 전건이 착수 시점과 같다는 것이 술어 확장의 무해성 증거다** — 어휘를 넓혔을 뿐 어느
case 의 계약도 바뀌지 않았고, `not-equals` 의 의미는 한 글자도 건드리지 않았다.
