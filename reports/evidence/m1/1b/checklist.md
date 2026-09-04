# checklist — M1 / 1B

Phase 1(계약)·Phase 2.5(설계 검토)·Phase 3(구현) 결과를 담는다. Phase 1 골격은 아래
「M1 완료 조건 대조」·「evidence 최소 목록」·「리뷰 요청 조건」 표로 흡수했다 — 별도
이력 절을 두지 않는다(그 서술은 자기 커밋의 diff를 다시 말하므로 낡는다, `evidence-pack`
SKILL).

## M1 완료 조건 대조 (`milestone-1.md` 「완료 조건」, 1B 가 지는 몫)

`milestone-1.md` 의 완료 조건은 M1 전체의 것이다. 1A 판정은
`reports/evidence/m1/1a/checklist.md` 가 정본이므로 여기서 되풀이하지 않는다.

| 조건 (`milestone-1.md` 축어) | 1B 판정 | 근거 |
| --- | --- | --- |
| `./gradlew check` 통과 | **충족** | `commands.md` B-0(격리 worktree)·B-1(작업 트리) |
| 금지 import와 순환 의존을 일부러 넣은 test fixture가 실제로 실패 | **충족(1A 승계 확인)** | `commands.md` B-2 — `shared-kernel` 이 Spring·JPA·JSON·HTTP 를 import 하지 않음(`ADR 0002` D-9)이 걸린다. 1B 는 새 위반 fixture 를 추가하지 않았다(1A 것을 재확인만) |
| 승인된 authoritative corpus 전체 통과 | **1B 종결 조건 제외, `1B-c` 소유(운영자 결정 2026-09-04 decision 15).** | 1B 축 authoritative case 가 0 건이고 **fixture-curator 레인이 되돌림을 실제로 시도해 0 건이 확정됐다**(`fixtures.md` §1). 되돌리지 못한 사유 다섯(BLOCK-1~5, `fixtures.md`) — 술어 어휘가 도구(`manifest_contract.py`) 쪽에서 안 풀림 · 결과 토큰이 1B 계약과 다른 이름 · money-basis 둘째 자물쇠(운영자 승인 부재) · `money-basis-001`·`004`(basis 혼합이 컴파일 차단이라 거부 객체 자체가 없음) · `money-basis-003`(검색 경로 타입 계약에 없음). 이 미충족이 신설 slice `1B-c`(corpus 계약 정렬, fixture-curator+spec-writer, `milestone-1.md` 신설 항목·`scope.md` decision 15)로 이관된다 — `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS`(`scope.md`)의 담당도 `1B-c` |
| 중요 rule mutation이 생존하지 않음 | **pending** | 도구는 `OPEN-ADR-07` 로 미결(1A 관례 승계, 카탈로그 좌표만) — 1B 는 mutation 을 적용하지 않는다(out_of_scope) |
| raw `Double` 금액/rate가 public domain API에 없음 | **충족** | `commands.md` B-6 — `domainApiTypeGate` 가 실제 도메인 API(`Money`·`Rate`·`Measurement`·`Fact` 등, `Long`/`BigDecimal` 백킹) 위에서 처음 실효했다. 위반 0 |
| `Uncertain`/`Unmeasurable`가 성공 또는 0으로 합쳐지지 않음 | **부분 충족(1B 몫은 완료)** | `Absent(reason)`·`Unmeasurable(reason)`·`Measured<T>` carrier 를 만들고 `orElse`·`getOrDefault`·`orZero`·`getOrThrow` 를 선언하지 않아 접는 API 자체가 없다. `sumOfBaseAmounts` 의 「빈 목록 = `Absent`, `0` 은 `Known(0)`과 분리」를 property test(P-4)로 고정했다. `Uncertain` variant 자체(1C·1D 소유)는 여전히 pending |
| 신규 파일/함수 예산 위반 없음 | **충족** | 1A 가 건 두 한도(함수 50·파일 500)를 `sizeGate` 가 그대로 걸었다(`commands.md` B-1). `qualityBaseline` 실측이 갱신됐다(B-5) |

### 1A 가 1B 로 넘긴 비-완료조건 항목 둘

| 항목 | 1B 판정 |
| --- | --- |
| coverage 임계 | **지어내지 않는다.** 승인 문서에 근거가 없다는 1A 의 사정이 도메인 코드가 생긴 뒤에도 그대로다 — 값은 `OPEN-1B-COVERAGE`, 실측(수치 없는 측정)은 `qualityBaseline` 이 낸다 |
| `duplicate mechanical helper` 측정 정의 | **미착수.** 운영자 결정 2026-09-04(C12)로 `OPEN-ADR-06`과 함께 **1B 종료 시점**에 결정하기로 시점만 확정됐다 — 이 slice 종료가 그 시점이다. 도구 조사(CPD 등)는 아래 「이월 항목」 |

## 구현 결과 — 커밋 목록

| 커밋 | 내용 |
| --- | --- |
| `feat(m1-1b): Money 다섯 성분과 basis·과세·provenance 어휘, 정책 배관 형태를 선언한다` | `Money`·금액 여섯 타입·`export()`·`Fact`/`Measurement`·정책 형태(`PolicyVersion`·`EffectiveDatedPolicy`·`RoundingPolicy`)·`ModuleBoundaryAnchor` 삭제 |
| `feat(m1-1b): Rate 축별 뉴타입 넷과 percent/fraction 생성 지점을 둔다` | `Rate`·축별 넷(`AssessmentRate`·`AwardRate`·`FloorRate`·`BidRate`)·`origin` sealed 둘 |
| `feat(m1-1b): 파생 금액 산술과 부재·overflow 합산 규칙, 회귀 example을 고정한다` | `times`·division 함수 셋·`sumOfBaseAmounts`·example test 다섯(E-1~E-5) |
| `feat(m1-1b): 파생 Money·율이 입력 fact와 계산 정책 version을 되짚게 한다 (B11)` | `DerivationRecord`·`Derived<T>`, 파생 산출 넷의 반환형을 `Measurement<Derived<T>>` 로 확장 |
| `feat(m1-1b): kotlin-compiler-embeddable로 상호 대입 컴파일 실패를 기계로 증명한다` | `CompileFailureHarnessTest` + 음성·양성 fixture 열(다섯 쌍) |

**브리프(설계 검토 §4.8)와 갈린 점**: 브리프의 커밋 여덟 개(C1~C8) 대신 다섯(타입 넷 +
컴파일 하네스 하나)으로 묶었다 —
Kotlin 은 모듈 전체를 한 번에 컴파일하므로(`Carrier.kt`의 `Measurement.Measured` 가
`PolicyVersion` 을, `Policy.kt`의 `Resolution` 이 `ReasonCode` 를 서로 참조) 브리프가 그린
경계(①어휘 ②Rate ③carrier ④정책 ⑤파생)를 그대로 커밋 경계로 쓰면 중간 커밋이 컴파일되지
않는다. 대신 **파일 의존 순서를 실제로 확인하며** 네 커밋으로 묶었고, 커밋마다
`git stash --keep-index` 로 이후 파일을 제외한 상태에서 `:shared-kernel:check` 를 실제로
돌려 **각 커밋이 독립적으로 통과함을 실측**했다(`commands.md`).

### verifier r1 수정 라운드 — 커밋 아홉(finding 별)

| 커밋 | finding |
| --- | --- |
| `6a2f4af` | H-1 — `BidAmount`·파생 율 셋·`DerivationRecord` 주 생성자를 `internal`+`@ConsistentCopyVisibility` 로 닫는다. 음성·양성 fixture 6 추가 |
| `a72660a` | H-2 — `rollback.md` 경로를 `scope.md` in_scope 전건으로 재구성, 임시 clone 실측(diff 0) |
| `637bbd9` | M-1 — `roundedWith` 의 `setScale`/`longValueExact` 실패를 `runCatching` 안으로, `ReasonCode` 둘(`ROUNDING_NOT_REPRESENTABLE`·`NEGATIVE_AMOUNT`) 신설 |
| `5a93ce2` | M-2 — 컴파일 하네스 음성 3·5 단언을 진단 종류(「receiver type mismatch」·「cannot access」)로 좁히고, 오타 변이가 새 단언을 만족시키지 않음을 test 로 고정 |
| `6113acb` | M-3 — `sumOfBaseAmounts` 의 vat 전건이 첫 원소 자체의 `Unknown` 을 통과시키던 결함 수정 |
| `e3437e8` | M-4 — `Provenance`/`FactProvenance` 표기 불일치를 `scope.md` OPEN 표에 `OPEN-1B-PROVENANCE-NAME` 으로 신설 등재(문서 수정 없음, 코드 변경 없음) |
| `a007523` | M-5 — 역방향 파급 grep 에 `*.yaml`·`*.yml` 을 더해 `fixtures/manifest.yaml` 좌표 넷을 잡고, m0 evidence 좌표 수(「일곱」→ 명령 포인터, 실측 열하나/열둘)를 정정 |
| `b33c4b2` | low L-1~L-6 — 시드 고정·「31 test」/「한 토큰」 문면 정정·근거 등재(상세는 아래 표) |
| `a9e0448` | scope 정정 — `b33c4b2` 가 `build-logic/**`(in_scope 밖)에 건 시드 설정을 `shared-kernel/build.gradle.kts` 로 옮긴다. 이 레인이 스스로 발견하고 고쳤다 |

### verifier r2 이전 후속 지시 — 커밋 둘(팀장 전달)

| 커밋 | 내용 |
| --- | --- |
| `617b9f3` | decision 15·16 — corpus 종결 조건을 `1B-c` 로 이관(`milestone-1.md` 「Slice 1B-c」 신설, `scope.md`·`capability-map.md` 담당 정정), `Provenance` 이름 확정(`data-dictionary.md` §5.1·§1.1 취소선 정정). 코드 무변경 |
| `b656b60` | B9 구현 — `AllocatedBudget`·`YegaAmount`·`AwardAmount` 의 `vatTreatment` 를 `Unknown` 고정으로 바꾼다(위 L-5 갱신 참고). `sameKnownVat` 전건이 `assessmentRateAgainst`·`awardRateAgainst` 를 항상 `Unmeasurable` 로 막는 property test, 컴파일 하네스 fixture 7, B11 커버리지 이관(`bidRateAgainst`) 포함 |
| `a58871e` | Phase 5 evidence — B-0~B-7 재실행, 하네스 레인·clean-tree·secret 재확인 |

### verifier r2 수정 라운드 — 커밋 넷(finding 별)

| 커밋 | finding |
| --- | --- |
| `6f0a507` | H-3 — `Derived`·`Measurement.Measured` 를 `internal`+`@ConsistentCopyVisibility` 로 닫는다(B11 위조 세 형태 차단). fixture 8·9·10(음성·양성·변이 각 쌍), 리플렉션 우회를 알려진 제한으로 등재(L-8 합침) |
| `5290fd5` | H-4 — `rollback.md` 가 `scope.md` `in_scope` 를 손 목록이 아니라 awk 파싱으로 파생하게 한다. 실측 중 「restore 가 scope.md 자신을 지워 확인 지점 2 가 실패하는」 진짜 버그를 발견해 캡처 순서를 고쳤다. `mapfile`(bash4+) 이 이 하네스의 macOS 기본 bash(3.2)에 없음도 실측으로 발견 |
| `796311e` | M-6 — `RoundingPolicy.init` 에 `require(scaleDigits >= 0)` 를 건다(`Rate`·`BaseAmount` 와 같은 construction-time invariant 관례). `ArithmeticTest` M-1 property test 의 scale 후보에서 `-1` 을 뺐다(층이 다르다) |
| `90eef2c` | low L-7·L-9·L-10 — fixture 6·7 변이 쌍둥이 추가, `BidRateOrigin.Recommended` 제한을 checklist 에도 등재, `commands.md` Phase 3 의 「31건」 하드코딩을 명령 포인터로 |

## evidence 최소 목록 (`agent-workflow.md` §6)

| 파일 | 상태 |
| --- | --- |
| `scope.md`·`rollback.md`·`commands.md`·`checklist.md` | 있다 |
| `differential.json` | **N/A** — 1B 는 legacy 코드와의 수치 비교(Python 대비 diagnostic run)를 만들지 않았다. 회귀 대응은 example test(E-1~E-5)가 문면·조사 A 인용으로 고정한다 |
| `golden-manifest.json` | **fixture-curator 레인 소유.** `reports/evidence/m1/1b/golden-manifest.json`이 그 레인의 산출물이다 — 1B(kotlin-implementer)는 이 파일을 만들지도 편집하지도 않는다. 이 slice 가 소비한 authoritative corpus 는 여전히 0 건이다 |
| `codex-review-*.json` | 심판 레인 소유. 이 레인이 만들지 않는다 |

## 알려진 제한

### 설계 검토(§5 L-1~L-12) 처리 결과

| # | 요지 | 처리 |
| --- | --- | --- |
| L-1 | `Unknown` 금액 산술 차단이 타입이 아니라 런타임(`sameKnownVat`) | **등재 유지.** `data-dictionary.md` §1.2.1 문면(「타입 차단」)과의 어긋남 — 과세 처리를 타입 파라미터로 올리면 I-4(개념마다 타입 하나)와 충돌해 불채택. 런타임 실패 계약(P-3c)으로 대신한다 |
| L-2 | 「상호 대입이 컴파일되지 않는다」가 CI로 증명되지 않음 | **닫혔다(운영자 결정 2026-09-04).** `CompileFailureHarnessTest`(`kotlin-compiler-embeddable`)가 다섯 음성·양성 쌍을 컴파일해 실패·성공을 단언하고 `gate-tests.properties`(`gate.tests.shared-kernel`)에 등재돼 `gateExecutionGate` 가 실행을 강제한다 |
| L-3 | `Long` 누출을 게이트가 재지 않음 | **`internal` 가시성으로 세웠다.** `Money.amount`·각 타입의 `won` 이 전부 `internal` — `api-type-policy.properties` 는 갱신하지 않았다(그러면 `AmountRecord` 조차 못 만든다) |
| L-4 | `basis` 가 생성자 파라미터가 아니라 파생 `val` — `data-dictionary.md` §1.1 서명과 형태 차이 | **등재 유지.** 다섯 성분은 그대로 있어 D-1 충족. `override val basis: Basis = Basis.XXX` 로 각 타입이 상수를 낸다 |
| L-5 | `PolicyVersion(effectiveFrom: LocalDate, …)` 문면과 `Initial` variant 요구가 같은 절에서 어긋남 | **`EffectiveFrom` sealed 로 구현.** `PolicyVersion.effectiveFrom: EffectiveFrom`(`LocalDate` 아님) — 문면 정정은 별도 승인 문서 개정이 필요하므로 이 slice 가 스스로 고치지 않는다 |
| L-6 | 「승인된 authoritative corpus 전체 통과」— 1B 축 corpus 0 건 | **미충족으로 판정한다(공집합 통과로 계상하지 않는다).** fixture-curator 레인이 되돌림을 실제로 시도해 0 건으로 확정됐다 — 위 완료 조건 표·`fixtures.md` 참조 |
| L-7 | 크기 기반 단위 추측 분기 부재의 architecture test 축이 1A 게이트 집합에 없음 | **판정(운영자 결정 2026-09-04): 신규 architecture test 를 세우지 않는다 — 기존 property test(P-3a)·example test(E-3)로 충분하다고 판정한다.** `Rate.ofFraction`/`ofPercent` 정의 자체에 값 크기를 보는 `if` 가 없다(코드 실측). **한계**: 이것은 회귀 방지 게이트가 아니다 — 누가 나중에 매직넘버 분기를 넣어도 CI 가 자동으로 막지 못하고 P-3a·E-3 의 기존 기대값과 충돌해야 간접적으로 잡힌다. 직접 게이트는 `build-logic/**` 확장이 필요해 위협 모델 경계 밖 비용을 문다 — `scope.md` 「계약 갱신」 2026-09-04 절이 판정 전문을 갖는다 |
| L-8 | `Provenance` ↔ `FactProvenance` 이름 불일치 | **승인 명세 표기(`Provenance`)를 채택.** A1·A2 와 같은 갈래 |
| L-9 | `a == b` 만 쓰면 `UNKNOWN`×`UNKNOWN` 이 통과 | **닫혔다.** `sameKnownVat`(`a == b && a != UNKNOWN`) 를 산술 함수 전건의 유일한 자리로 두고 property test(P-3c)로 고정했다 |
| L-10 | 잘못된 단위 거부 — 상한 밴드를 두면 D-4 금지 경로가 됨 | **하한만 둔다.** `Rate.init` 이 `fraction.signum() >= 0` 만 요구. E-4 example test 가 「2.0 을 클램프하지 않는다」를 고정한다 |
| L-11 | `Money` 상위 타입에 이항 연산이 들어가면 basis 차단이 무너짐 | **지켰다(Codex 1차 #2 로 처방이 갈렸다 — 이 행은 그 갈림을 반영해 갱신).** `Money` 는 여전히 `Comparable`·이항 연산을 구현하지 않는다. **각 금액 타입도 더는 자기 축의 공개 `Comparable<Self>`를 구현하지 않는다** — `won`만 비교해 `VAT`를 안 보던 결함(Codex #2)을 고치며 아예 없앴다. 비교는 `sameKnownVat` 전건을 건 `compareKnownVat(...)`(→ `Fact<Int>`)가 유일한 경로다. basis 교차 비교 차단은 여전히 지켜진다 — 제네릭 `T : Money`가 같은 타입만 받는다 |
| L-12 | 서브패키지 분할이 `packagesMustBeFreeOfCycles` 순환을 만듦(금액↔율) | **평면 패키지 하나(`bidvector.sharedkernel`)로 지켰다** |

### 이 slice 가 실측으로 새로 발견한 것

- **JUnit Jupiter가 `PropertyContext`(비-`Unit`) 반환 test 메서드를 조용히 건너뛴다.**
  `fun test() = runBlocking { checkAll(…) { … } }` 처럼 `checkAll` 을 식 본문의 마지막
  표현식으로 두면 함수 반환형이 `Unit` 이 아니라 `PropertyContext` 로 추론되고, 그 메서드는
  discovery 에서 빠진다(exit 0, 실패 0, 그러나 그 test 는 **실행되지 않는다** — 실측:
  `javap` 로 반환형 확인, `TEST-*.xml` 의 testcase 4/6 만 등재). **모든 property test 를
  블록 본문(`{ }`)으로 써서 `Unit` 을 고정**했다. `kotest-property` 를 처음 Jupiter 에
  붙이는 slice 라 이 함정을 여기서 처음 만났다 — 후속 slice(1C~1E)에 승계한다.
- **`internal constructor` + `data class` 조합이 Kotlin 2.4.10 의 `-Werror` 아래 빌드를
  깬다.** `Non-public primary constructor is exposed via the generated 'copy()' method`
  경고가 실제로 난다(설계 검토 §1a 미측정 ⓑ의 실측 답). `@ConsistentCopyVisibility` 로
  닫았다 — `Rate` 에 적용, 부수효과로 `copy()` 도 함께 `internal` 이 돼 비정규 `fraction`
  으로의 재구성 경로가 완전히 막힌다(설계가 기대한 것보다 더 좁게 닫힌다).
- **`kotest-property-jvm` 이 `kotlinx-coroutines-core` 를 runtime scope 로만 선언한다.**
  컴파일 classpath 에 전이되지 않아 `checkAll` 을 부르는 test 소스가 `runBlocking` 을 못
  찾는다 — `shared-kernel/build.gradle.kts` 에 `testImplementation(libs.kotlinx.coroutines.core)`
  를 직접 걸었다(카탈로그 좌표는 이미 있던 것, build-logic 은 손대지 않았다).
- **`sumOfBaseAmounts` 에 vat 일관성 검사를 브리프보다 하나 더 넣었다.** 브리프 §1d 의
  합산 규칙 넷은 vat 를 언급하지 않으나, 서로 다른 vatTreatment 의 금액을 더하는 것은
  L-9 와 같은 성질의 오류라 판단해 `sameKnownVat` 전건을 합산에도 적용했다(사유:
  `VAT_TREATMENT_MISMATCH`). 값 결정이 아니라 기존 규율(L-9)의 일관 적용이라 `OPEN` 으로
  등재하지 않는다.
- **`capability-map.md` 의 `milestone-1.md:NN` 인용 다수가 이 라운드의 편집 이전부터
  이미 어긋나 있었다(decision 15·16 등재 라운드, 역방향 파급 실측).** `milestone-1.md`
  에 「Slice 1B-c」를 신설하며 stem 기준 역방향 파급 grep 을 돌린 결과, `capability-map.md`
  §14.2 의 `OPEN-ADR-07`(`:80`)·`OPEN-DEC-07`(`:48`)·`OPEN-DIC-01`(`:42`)·`OPEN-QUAL-07`
  (`:42`·`:79`)·`OPEN-DIC-06`(`:60`)·`OPEN-COL`(`:98`)·`OPEN-ML`(`:99`)·`OPEN-ML-03`(`:82`)·
  `OPEN-STR`(`:54`~`:56`)·N-5(`:42`) 열 곳이 인용한 줄이 **base(`HEAD` 이전) 시점에도 이미**
  인용문과 다른 내용을 가리켰다(실측: `git show HEAD:milestone-1.md`의 해당 줄과 인용문
  대조 — 전부 불일치). **이 slice 의 `milestone-1.md` 편집(+26줄)이 만든 새 어긋남이
  아니다** — 편집 전부터 있던 drift 를 이 라운드의 역방향 파급 검사가 처음 발견했다(1B
  H-2 라운드가 `capability-map.md` 의 다른 네 좌표에서 이미 확인한 것과 같은 패턴).
  **고치지 않는다** — `capability-map.md`·`docs/adr/0007` 자체의 `file:line` 드리프트는
  기존 `OPEN-ADR-15`(registry 통합 slice 소관)가 이미 추적 중이다(`scope.md` 「역방향
  파급」절). 이 slice 가 신설한 `OPEN-1B-CONTRACT` 담당 정정·`OPEN-1B-CORPUS` 신설 행
  자체는 절 제목 포인터(`milestone-1.md` 「Slice 1B-c」)만 쓰고 새 줄 번호를 심지
  않았다 — 같은 부류의 drift 를 새로 만들지 않았다. 재현 명령:
  ```
  grep -rnoE 'milestone-1\.md:[0-9]+|milestone-1:[0-9]+' \
    --include='*.md' --include='*.kt' --include='*.kts' --include='*.properties' \
    --include='*.yaml' --include='*.yml' . \
    | grep -v '^\./bid-vector/' | grep -v '/build/' | grep -v '^\./milestone-1.md:'
  ```

### verifier r1 low(L-1~L-6) 처리 결과

**주의 — 번호가 위 설계 검토 표의 L-1~L-12 와 다른 번호 체계다.** 이 표는
`_workspace/m1-1b/05_verifier_r1.md` §6 의 L-번호를 그대로 쓴다.

| # | 요지 | 처리 |
| --- | --- | --- |
| L-1 | `(f as? Fact.Known)?.value ?: 0L` 이 컴파일된다 — 설계 검토 §1d 의 「값을 꺼내는 유일한 수단이 소진 `when`」 넓은 서술은 성립하지 않는다 | **등재 유지.** `checklist.md` 자신의 좁은 주장(「접는 API(`orElse`·`getOrDefault`·`orZero`·`getOrThrow`) 자체를 선언하지 않는다」— `Carrier.kt` KDoc)은 여전히 참이다. Kotlin `sealed interface` 는 `as?`·스마트캐스트로의 우회를 언어 차원에서 막지 못한다 — architecture test 로 막으려면 `build-logic` 확장이 필요해 L-7(설계 검토 표) 과 같은 비용을 문다. 새 게이트를 세우지 않고 문면만 좁혀 정확히 한다 |
| L-2 | `commands.md` B-4 가 「31 test」로 적으나 HEAD 는 다르다 | **닫혔다.** `commands.md` B-4 를 산문 개수 대신 `TEST-*.xml` 의 `tests="…"` 를 세는 명령 포인터로 바꿨다(`fix(m1-1b): … M-5` 커밋과 같은 원칙) |
| L-3 | 양성·음성 쌍이 2~3 토큰(함수명·변수명·import) 다른데 「한 토큰만 다른」으로 적었다 | **닫혔다.** `gate-tests.properties` 주석·`CompileFailureHarnessTest` KDoc 을 「2~3 토큰」으로 정정했다 |
| L-4 | `Rate.fraction` 이 `internal` 이고 `Money.export()` 에 대응하는 공개 export 가 없어 모듈 밖에서 율 값을 읽을 수단이 0 이다 | **등재 유지 — 1C~1E 착수 전 결정 필요(verifier r1 원문 그대로).** 의도적 설계(`Rate` 값 유출 경로를 `BidRate`/`AssessmentRate`/`AwardRate` 래퍼로만 열어 둔 것, `@ConsistentCopyVisibility` 의 부수효과)인지 누락인지를 이 slice 는 판단하지 않는다 — `Money.export()` 대응 함수(가칭 `Rate.export()` → `RateRecord`)가 필요한지는 1C 가 실제로 값을 읽어야 하는 순간에 결정한다 |
| L-5 | 운영자 결정 B9(「세 타입을 `vatTreatment = Unknown` 으로 선언」)이 코드에 없다 — 여섯 금액 타입 전부 `vatTreatment` 를 호출부 인자로 받는다 | **닫혔다 — 구현함(운영자 재확인, 2026-09-04 후속 지시).** 이 표에는 처음에 「원문(low)을 따라 구현하지 않는다」고 적었으나, team-lead 가 근거를 밝히며 재확인했다 — `data-dictionary.md` §1.2 가 `Unknown` 을 「정의상 현재 값, 미결의 표현이지 답이 아니다」로 정의하므로 `vatTreatment = Unknown` **선언**은 `OPEN-DIC-04`(실제 과세 처리 값)를 해소하는 것이 아니라 「모른다」를 타입에 싣는 것뿐이다 — `scope.md` 「OPEN 을 임의로 해소하지 않는다」위반이 아니다. `AllocatedBudget`·`YegaAmount`·`AwardAmount` 세 타입의 주 생성자에서 `vatTreatment` 파라미터를 제거하고 `Unknown` 고정 `val` 로 바꿨다(`Money.kt`, 커밋 `feat(m1-1b): 세 금액의 과세 처리를 Unknown 으로 고정 선언한다 (운영자 결정 B9)`) — `sameKnownVat` 전건이 이미 있어 `assessmentRateAgainst`·`awardRateAgainst` 가 항상 `Unmeasurable` 이 되는 것으로 산술 차단이 자동 성립한다. **부작용**: 두 함수가 지금 항상 `Unmeasurable` 이라 사실상 죽은 경로가 된다 — `OPEN-DIC-04` 가 실제 값으로 해소돼야 다시 살아난다. `scope.md` `OPEN-DIC-04` 행에 반영 |
| L-6 | kotest 시드 미고정 — property 실패의 재현이 그 실행에 찍힌 시드에만 의존. `divideForRate` 가 분모 0 을 `EMPTY_INPUT` 으로 라벨 | **시드는 닫혔다** — `build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts` 의 `tasks.withType<Test>` 에 `systemProperty("kotest.proptest.default.seed", "20260904")` 를 걸었다(kotest 의 JUnit5 러너를 안 붙이므로 `AbstractProjectConfig` 자동탐지가 아니라 이 시스템 property 가 유일한 전역 지점이다 — 실측: 값을 읽어 콘솔에 찍는 임시 test 로 확인 후 제거). **`EMPTY_INPUT` 라벨은 등재 유지.** 분모 0 은 "입력이 비었다"가 아니라 "0으로 나눌 수 없다"는 별도 사유이지만, `ReasonCode` 에 나눗셈 전용 코드를 새로 만들 근거(legacy 실측·운영자 결정)가 없어 이 slice 는 새 코드를 짓지 않는다 — 다음 slice 가 나눗셈 전건이 늘면 재검토 대상 |

### verifier r2 발견 — H-3·H-4·M-6·low 4 처리 결과

**주의 — `_workspace/m1-1b/06_verifier_r2.md` 의 번호를 그대로 쓴다.** H·M 은 위
설계 검토·verifier r1 표와 다른 라운드다. low 는 L-7~L-10(verifier r2 §7 번호).

| # | 요지 | 처리 |
| --- | --- | --- |
| H-3 | `DerivationRecord` 생성자만 닫혀 있고 그것을 나르는 `Derived<T>`·`Measurement.Measured<T>` 는 공개 생성자·공개 `copy()` 를 가져, B11 이 요구하는 "값이 **자기** 입력 fact 를 되짚는다"가 `a.copy(derivedFrom = b.derivedFrom)`(기록 교체)·`Derived(a.value, b.derivedFrom)`(위조 wrapper)·`Measurement.Measured(Derived(x, d.derivedFrom), 1, pv)`(임의 타입 포장) 세 형태로 모듈 밖에서 무너진다 | **닫혔다.** `Derived`·`Measurement.Measured` 양쪽에 `internal constructor`+`@ConsistentCopyVisibility` 를 걸었다(`Rate`·파생 `Money`·`DerivationRecord` 가 이미 쓴 처방과 동일). 「형제 다섯」식 부분 열거를 반복하지 않도록, `DerivationRecord`·`Derived<` 를 필드 타입으로 갖는 선언을 grep 으로 전수 확인해 이 둘이 B11 carrier 전부임을 확인했다(`Fact`·`Measurement.Unmeasurable` 은 파생 기록을 나르지 않아 대상이 아니다). 컴파일 하네스 fixture 8·9·10(음성 셋 + 양성 쌍둥이 + 변이 쌍둥이 각 하나, 변이는 정당한 읽기 경로에 오타만 넣어 "cannot access" 를 만족시키지 않음을 확인) |
| L-8(합침) | 리플렉션이 `Derived`·`Measurement.Measured` 의 새 컴파일 시점 보증을 둘 다 우회한다(설계 검토 §2 #14 가 "경계 밖"으로 이미 분류했으나 evidence 에 없었다) | **등재만 한다 — 알려진 제한.** `internal constructor`+`@ConsistentCopyVisibility` 는 컴파일 시점 API 표면만 막는다. `kotlin.reflect`/`java.lang.reflect` 로 `Derived`·`Measurement.Measured`(및 앞서 잠근 `Rate`·파생 `Money`·`DerivationRecord`) 의 생성자·필드에 접근하면 여전히 우회된다 — JVM 가시성 자체가 리플렉션을 막지 않는 것과 같은 성질이라 이 slice 의 게이트 구조(컴파일 실패 하네스)로는 닫을 수 없다. 런타임 리플렉션 사용 자체를 막는 것은 별도 architecture test(허용 import 목록에서 `kotlin.reflect`/`java.lang.reflect` 제외)가 필요하고 이는 `build-logic` 확장이라 위협 모델 경계 밖(L-7, 설계 검토 표와 같은 판단) |
| H-4 | rollback 경로 목록이 in_scope 와 재발적으로 어긋난다 — `a72660a`(H-2 수정)가 열 경로로 맞췄으나 그 뒤 `617b9f3`(decision 15)이 `milestone-1.md` 를 더해 열하나로 늘렸는데 갱신되지 않았다. rollback.md 자신이 정한 규칙("scope.md 가 넓어지면 같은 커밋에서 갱신")과 자신이 세운 확인 지점 2 가 둘 다 지켜지지 않았다 | **닫혔다.** `rollback.md` 를 손 목록에서 `scope.md` `in_scope:` 를 awk 로 파싱해 배열을 만드는 방식으로 바꿨다 — `scope.md` 가 넓어져도 이 문서를 다시 고칠 필요가 없다. 확인 지점 2 도 하드코딩 대신 독립 grep 카운트와 비교하는 명령으로 바꿨다. 임시 clone 실측 중 진짜 버그를 하나 발견해 고쳤다 — `scope.md` 자신이 in_scope(`reports/evidence/m1/1b/**`)에 속해 restore 가 그 파일을 지우므로, 확인 지점 2 를 restore **뒤**에 `scope.md` 를 다시 읽어 돌리면 실패한다. 독립 카운트 캡처를 restore **전**으로 옮겨 재실행해 네 확인 지점 전부 통과를 실측했다. 부수적으로 `mapfile`(bash 4+)이 이 하네스의 macOS 기본 `/bin/bash`(3.2)에 없음을 실측으로 발견해 while-read 루프로 바꿨다 |
| M-6 | `RoundingPolicy(scaleDigits, mode)` 가 정의역 검사 없이 아무 값이나 받아, 음수 `scaleDigits` 가 예외도 사유 있는 실패도 아니라 조용한 성공이 돼 백 원 단위 반올림 같은 값 오염이 통과한다(r1 M-1 이 닫은 예외 누출·사유 오라벨과 다른 셋째 축 — 불변식 부재) | **닫혔다.** `RoundingPolicy.init` 에 `require(scaleDigits >= 0)` 를 걸었다 — `Rate.init`·`BaseAmount.init` 이 이미 쓰는 construction-time invariant 관례(값을 `Measurement`/`Fact` 로 감싸지 않고 `require` 로 던진다)를 그대로 따른다. 상한(금액 축 밖 `scaleDigits`)은 `OPEN-DIC-10` 미결이라 걸지 않는다. example test 하나(`-2` 거부) + property test 둘(임의 음수는 항상 거부·0 이상은 계속 허용)을 추가했다. **부수 수정**: `ArithmeticTest` 의 M-1 property test 가 scale 후보에 `-1` 을 갖고 있었다 — 이제 그 값은 `RoundingPolicy` 생성 시점에 거부되므로(층이 다르다) 후보에서 뺐다. `mode` 값(`OPEN-DIC-10`) 은 여전히 OPEN — 이 결정과 무관하다 |
| L-7 | 새 음성 fixture 6·7 에는 변이 쌍둥이 test 가 없었다(3·5·8·9·10 에는 있다) | **닫혔다.** 6·7 에도 3·5 와 같은 자리(정당한 양성 경로에 오타만 넣는다)의 변이 쌍둥이 test 를 추가했다(`mutant-6-…-typo`·`mutant-7-…-typo`, 둘 다 "unresolved reference" 를 내고 각 negative 의 진단 단편은 만족시키지 않음을 실측). **verifier r2 원문이 든 더 날카로운 두 예(`YegaAmount(1L, KRW, Provenance.Published(1, 2))`가 vat 계약을 어기지 않고도 "too many arguments for" 를 냄 · `m.amount`(`BaseAmount`)가 파생 생성 계약을 어기지 않고도 "cannot access" 를 냄)는 원문 스스로 "성문화된 요구(오타가 통과시키면 안 된다)는 6·7 에서도 충족된다 — 이 지적은 그보다 높은 기준"이라 밝혀, 이 slice 의 표준(오타 변이 판별)을 넘는 요구다 — 등재만 하고 새 기준을 도입하지 않는다** |
| L-9 | `BidRate` 생성자를 `internal` 로 닫으면서 `BidRateOrigin.Recommended` 의 공개 생성 경로가 사라졌고, 그 결과 `BaseAmount.times(rate: BidRate)`(파생 투찰가 경로 전체)가 모듈 밖에서 호출 불가가 됐다. KDoc 에만 「알려진 한계」로 적혀 있고 `checklist.md` 알려진 제한에는 없었다 | **등재만 한다 — 알려진 제한(KDoc `Rate.kt`의 `BidRate` 와 같은 문면을 여기 옮긴다).** 1B 는 관측(`bidRateAgainst`) 경로만 만들고 추천 입력 경로는 만들지 않았다 — ML 추천을 받는 M2/M5 가 그 경로(이름 있는 factory, `Rate.ofFraction` 과 같은 형태)를 열어야 한다. 코드 변경 없음(이번 라운드에서 그 factory 를 새로 만들지 않는다 — 근거 없는 형태 확장 금지) |
| L-10 | `commands.md` Phase 3 절이 여전히 「test 31건」으로 적는다 — Phase 5 절이 47 을 명령 포인터로 갖고 있어 정본은 최신이나 같은 문서에 수가 둘 남아 있었다 | **닫혔다.** Phase 3 절의 「31건」을 산문 하드코딩 대신 Phase 5 의 명령 포인터를 가리키는 문장으로 바꿨다(L-2 원칙의 일관 적용 — 같은 문서 안에 낡을 수 있는 두 번째 숫자를 남기지 않는다) |

### verifier r3 발견 — M-7·low 2 처리 결과

| # | 요지 | 처리 |
| --- | --- | --- |
| M-7 | H-3 처방(`Measurement.Measured` 를 `internal` 로 닫음)의 부수효과 — 하류 도메인 모듈(1C~1E)이 `Measurement` 의 실패(`Unmeasurable`)는 만들 수 있으나 성공(`Measured`)은 만들 수 없다. `Fact` 는 `Known`/`Absent` 두 팔이 대칭인데 `Measurement` 만 비대칭이 됐다. `milestone-1.md` 완료 조건이 이 어휘를 M1 전체 축으로 들고 1E 항목이 `Unmeasurable` 을 직접 이름 든다 — 1C~1E 가 자기 축의 **성공** 측정을 이 어휘로 낼 수 없다는 제약이 후속 slice 설계를 구속한다 | **등재만 한다 — 알려진 제한(L-9 와 같은 기준).** **닫으라는 뜻이 아니다** — verifier r3 원문이 "열지 말지는 운영자·후속 slice 판단"이라 명시한다. 하류 slice 가 `Measured` 를 만들려면 `shared-kernel` 이 제공하는 파생 함수/팩토리를 통해야 하고, 그 팩토리는 1C~1E 자기 계약에서 연다 — 이번 라운드는 그 팩토리를 새로 만들지 않는다(근거 없는 API 확장 금지, L-9 와 같은 판단). `scope.md` 「이 slice 가 하는 일」 절에도 한 줄을 더했다 |
| L-11 | `rollback.md` 의 awk 파생이 in_scope 항목 줄이 `out_of_scope` 관례(`a · b · c` 한 줄에 여럿)를 쓰면 깨진 pathspec 하나를 조용히 만들 수 있다(지금 in_scope 에 그런 줄은 0건). 확인 지점 2(개수 대조)는 이 오류를 못 잡는다 — 수는 맞고 내용만 틀리기 때문이다 | **닫혔다.** `rollback.md` 의 awk 파생 뒤 각 줄을 `·` 로 다시 나눠 항목마다 배열 원소로 넣게 하고, 새 확인 지점 5(파생된 pathspec 각각이 `git ls-files` 로 실재 경로에 매치되는지, restore 전에 강제 확인)를 추가했다. 임시 clone 에서 `in_scope` 에 `·` 로 묶인 줄을 실제로 넣어 파생이 두 개별 경로로 정확히 갈라짐을 실측했다(verifier r3 가 H-4 를 검증한 것과 같은 「실제로 늘려서 따라오는지 본다」 방법) |
| L-12 | 하네스 `6-M2` KDoc 이 "fixture 3·5·8·9·10 에는 변이 쌍둥이가 있었는데 6·7 에는 없었다"로 적으나 8·9·10 은 자기 쌍둥이와 같은 커밋에서 생겨 그 「없었다」의 시점이 성립하지 않는다(production 이력 서술) | **닫혔다.** KDoc 을 이력 비교 대신 현재 규칙만 적도록 정정했다 — "모든 음성 fixture 는 변이 쌍둥이를 갖는다"(verifier r1 M-2 원칙) |

### Codex 1차 발견 — #1·#2·#3 처리 결과

| # | 요지 | 처리 |
| --- | --- | --- |
| #1 | `MoneyArithmetic` 의 산술·파생 성공 경계(`times`→`roundedWith`·`divideForRate`·`sumOfBaseAmounts`)가 `Provenance.Undeclared`를 검사하지 않고 성공 `Measured`로 통과시킨다 — `v2-지침서.md` §4.1("provenance가 없거나 모르는 값은 추측하지 않고 거부 또는 `Unmeasurable`로 반환한다")을 어긴다 | **닫혔다.** `hasDeclaredProvenance` 전건을 신설해 세 성공 경계 전부(`UnroundedBidAmount.roundedWith`·`divideForRate`(→`assessmentRateAgainst`·`awardRateAgainst`·`bidRateAgainst`가 공유)·`sumOfBaseAmounts`의 `accumulate`)에 건다. 새 `ReasonCode.UNDECLARED_PROVENANCE`. **순서 결정**: provenance 검사를 vat·overflow 검사보다 먼저 한다 — 여러 실패가 동시에 걸려도 "출처를 모른다"가 먼저 나온다. **`OPEN-DIC-06`(어댑터 write 경로가 `Undeclared`를 거부하는가)과는 다른 축임을 KDoc·test 양쪽에 명시** — 그 결정은 수집 시점 수용 여부이고, 여기서 막는 것은 이미 도메인에 들어온 값의 계산이다(data-dictionary.md 자신이 그 결정을 "어댑터의 의무"로 분류해 이 문서가 정할 자리가 아니라고 적는다 — 그래서 OPEN-DIC-06 을 건드리지 않고도 이 finding 을 닫을 수 있었다). **테스트 설계 메모**: `YegaAmount`/`AwardAmount`는 B9(vatTreatment 고정 Unknown)로 `assessmentRateAgainst`/`awardRateAgainst`가 이미 항상 `Unmeasurable`이라 "declared 면 성공" 대조를 못 낸다 — 그 대조(property, 임의 provenance 조합에서 Undeclared 하나라도 있으면 실패·아니면 성공)는 vat 제약이 없는 `bidRateAgainst`(`BidAmount`×`BaseAmount`)로 냈다 |
| #2 | 여섯 `Money` 타입의 `compareTo`가 `won`만 비교해 `VAT` `UNKNOWN`/`INCLUSIVE` 도 정렬되고, 동일 금액이면 `VAT` 가 달라도 0을 냈다 — `data-dictionary.md` 의 "Unknown 금액은 다른 과세 처리의 금액과 산술 비교에 들어갈 수 없다" 규칙 위반 | **닫혔다 — team-lead 결정대로 공개 `Comparable<Self>` 를 여섯 타입 전부에서 제거**하고 `sameKnownVat` 전건을 건 명시 API `compareKnownVat(left, right)` 로 대체했다(`Money.kt`, `export()` 옆). `UNKNOWN`/`UNKNOWN`·서로 다른 known VAT 비교가 실패하는 test, 같은 vat 이면 `won` 순서와 일치하는 property test 를 추가했다. **반환 타입 갈림(달리한 결정)**: 전달문은 "`VAT_TREATMENT_MISMATCH`/`Unmeasurable` 반환"이라 적었으나, `Measurement.Measured` 는 `policyVersion`/`sampleSize` 를 요구하고 비교에는 그 둘의 자연스러운 입력이 없다(정책을 소비하지 않는다) — 지어내면 매직 넘버 금지 원칙과 같은 성질의 문제가 된다. `sumOfBaseAmounts`(같은 "정책 비소비" 성질의 함수)가 이미 `Fact` 를 쓰는 것과 같은 판단으로, `Measurement.Unmeasurable` 대신 **`Fact.Absent(VAT_TREATMENT_MISMATCH)`** 를 썼다 — 사유 어휘(`VAT_TREATMENT_MISMATCH`)는 전달문 그대로다. 기존 `MoneyTest` 의 `compareTo` 기반 test(같은 `basis` 비교 확인용) 도 `compareKnownVat` 로 옮겼다 |
| #3 | `UnroundedBidAmount.roundedWith` 가 적용 하한을 받지도 사후 검증도 안 해, 하한이 소수(예: `1000.4`)이고 `scale=0`·`DOWN` 이면 결과(`1000`)가 하한 미만이다 — `data-dictionary.md` §1.1 정의 ②·`capability-map.md` DEC-02 「결정 무관(무조건)」acceptance("반올림 내림이 하한을 미세하게 밑도는 잔차가 발생하지 않는다") 위반. 기존 `P-2b` test 는 `floor`(정수)+ε 만 생성해 이 반례를 놓쳤다 | **닫혔다.** `roundedWith` 에 `floor: BigDecimal? = null` 을 추가했다(기본값 `null` — 하한을 모르는 기존 호출부와 호환, 전부 그대로 컴파일된다). `floor` 를 주고 반올림 결과가 그 미만이면 새 `ReasonCode.ROUNDED_BELOW_FLOOR` 로 `Unmeasurable`. **처리 방식 판단(clamp 대 Unmeasurable, evidence 근거)**: `capability-map.md` DEC-02 절 제목 자체가 "제약(clamp) 적용"이고 legacy 결함은 "서로 다른 basis 를 하나의 min/max 에 섞는 것"만 폐기 대상이라 clamp 개념 자체는 살아 있지만, **같은 acceptance 가 "최종 추천가가 어느 제약에 binding됐는지가 결과에 실린다"를 요구한다** — 그 binding 추적 장치(어느 제약에 걸렸는지 기록)는 DEC-02 의 더 큰 산정 알고리즘(하한·상한·신뢰비율)의 몫이지 1B 의 순수 반올림 함수가 가질 자리가 아니다. `roundedWith` 가 스스로 하한으로 clamp 하면 그 binding 정보 없이 값만 바뀌어 그 요구를 오히려 어길 위험이 있다고 판단해 **사유 있는 실패**를 택했다 — clamp 여부 판단은 DEC-02 알고리즘을 실제로 구현하는 (아직 미배정) slice 에 넘긴다. **P-2b 정정**: 정수 `floor`+ε 생성 방식을 소수 `floor`(원 단위로 안 떨어지는 실제 적용 하한을 흉내)로 바꾸고, `raw`를 `floor` 와 정확히 같게 둬(Codex 반례와 같은 가장 빡빡한 경계) `setScale` 오라클로 기대값을 계산해 대조한다. **파일 크기**: `ArithmeticTest.kt`가 500줄 한도를 넘어(509줄) Codex #1 의 Undeclared-provenance test 전부(주제가 뚜렷이 다른 묶음)를 `UndeclaredProvenanceTest.kt` 로 분리했다 — 크기 회피용 기계적 분할이 아니라 입력 provenance 전건이라는 별도 주제이기 때문이다. 공유 헬퍼(`base`·`resolvedPolicy`·`ALL_ROUNDING_MODES`·`ALL_PROVENANCES`)는 `ArithmeticTest.kt` 에 `internal` 로 남겨 중복 선언하지 않는다 |

## 이월 항목

**컴파일 실패 하네스(C8)·L-7·fixture 되돌림(C13) 셋 다 더는 이월이 아니다** — 앞 둘은
운영자 결정 2026-09-04로 닫혔고(위 완료 조건 표·설계 검토 처리 결과 표), fixture
되돌림은 fixture-curator 레인이 **실행해 결과를 냈다**(`fixtures.md`, 커밋 `93be34a`) —
되돌린 case 0 건, 술어 설계는 나왔으나 실행 도구 확장은 미착수. 이월 항목 표는 이제
비어 있다. 남은 미결은 `scope.md`의 `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS`가 든다 — 값·범위
결정을 운영자에게 올리는 것이 다음 단계이지, 이 slice 가 더 실행할 남은 작업이 아니다.

## 리뷰 요청 조건 (`evidence-pack` SKILL)

| 항목 | 상태 |
| --- | --- |
| 구현 diff 가 커밋되어 base/head 고정 | **충족.** `git status --porcelain -- shared-kernel reports/evidence/m1/1b` 가 비어 있다(양성 대조는 `commands.md`) |
| `acceptance_commands` 전부 exit 0 | **충족.** B-0~B-7 전건 `commands.md` |
| test/lint/type/architecture 통과 | **충족.** `commands.md` B-1(전 모듈) |
| 변경된 fixture 와 정책 version 의 근거 | **N/A** — 이 slice 는 fixture 를 변경하지 않았다(`fixtures/manifest.yaml` 미편집). 정책 version 은 `VAT_RATE_POLICY`(source 문자열이 `data-dictionary.md` §12.1 을 가리킨다) 하나뿐이고 그 근거는 `PolicyTable.kt` 주석이 든다 |
| 알려진 제한과 rollback | 위 절들 + `rollback.md` |
| 비밀값 스캔 | **충족.** `commands.md` — 매치는 스캔 명령 자신의 인용문뿐(판독 규칙 적용) |
