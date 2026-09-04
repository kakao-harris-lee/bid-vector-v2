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
| 승인된 authoritative corpus 전체 통과 | **N/A(1B 축) + 사유** | 1B 축 authoritative case 가 0 건이다. `OPEN-1B-CONTRACT`(계약 술어 설계, 담당은 1B 로 확정됐으나 술어 자체는 이번 Phase 3 범위 밖 — 아래 「이월 항목」) 가 닫혀야 계상된다 |
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

**브리프(설계 검토 §4.8)와 갈린 점**: 브리프의 커밋 여덟 개(C1~C8) 대신 넷으로 묶었다 —
Kotlin 은 모듈 전체를 한 번에 컴파일하므로(`Carrier.kt`의 `Measurement.Measured` 가
`PolicyVersion` 을, `Policy.kt`의 `Resolution` 이 `ReasonCode` 를 서로 참조) 브리프가 그린
경계(①어휘 ②Rate ③carrier ④정책 ⑤파생)를 그대로 커밋 경계로 쓰면 중간 커밋이 컴파일되지
않는다. 대신 **파일 의존 순서를 실제로 확인하며** 네 커밋으로 묶었고, 커밋마다
`git stash --keep-index` 로 이후 파일을 제외한 상태에서 `:shared-kernel:check` 를 실제로
돌려 **각 커밋이 독립적으로 통과함을 실측**했다(`commands.md`).

## evidence 최소 목록 (`agent-workflow.md` §6)

| 파일 | 상태 |
| --- | --- |
| `scope.md`·`rollback.md`·`commands.md`·`checklist.md` | 있다 |
| `differential.json` | **N/A** — 1B 는 legacy 코드와의 수치 비교(Python 대비 diagnostic run)를 만들지 않았다. 회귀 대응은 example test(E-1~E-5)가 문면·조사 A 인용으로 고정한다 |
| `golden-manifest.json` | **N/A** — 1B 축 authoritative fixture 가 0 건이라 소비한 corpus 가 없다(`OPEN-1B-CONTRACT`) |
| `codex-review-*.json` | 심판 레인 소유. 이 레인이 만들지 않는다 |

## 알려진 제한

### 설계 검토(§5 L-1~L-12) 처리 결과

| # | 요지 | 처리 |
| --- | --- | --- |
| L-1 | `Unknown` 금액 산술 차단이 타입이 아니라 런타임(`sameKnownVat`) | **등재 유지.** `data-dictionary.md` §1.2.1 문면(「타입 차단」)과의 어긋남 — 과세 처리를 타입 파라미터로 올리면 I-4(개념마다 타입 하나)와 충돌해 불채택. 런타임 실패 계약(P-3c)으로 대신한다 |
| L-2 | 「상호 대입이 컴파일되지 않는다」가 CI로 증명되지 않음 | **미착수.** 컴파일 실패 하네스(C8)를 이번 Phase 3 에서 붙이지 않았다 — 아래 「이월 항목」 |
| L-3 | `Long` 누출을 게이트가 재지 않음 | **`internal` 가시성으로 세웠다.** `Money.amount`·각 타입의 `won` 이 전부 `internal` — `api-type-policy.properties` 는 갱신하지 않았다(그러면 `AmountRecord` 조차 못 만든다) |
| L-4 | `basis` 가 생성자 파라미터가 아니라 파생 `val` — `data-dictionary.md` §1.1 서명과 형태 차이 | **등재 유지.** 다섯 성분은 그대로 있어 D-1 충족. `override val basis: Basis = Basis.XXX` 로 각 타입이 상수를 낸다 |
| L-5 | `PolicyVersion(effectiveFrom: LocalDate, …)` 문면과 `Initial` variant 요구가 같은 절에서 어긋남 | **`EffectiveFrom` sealed 로 구현.** `PolicyVersion.effectiveFrom: EffectiveFrom`(`LocalDate` 아님) — 문면 정정은 별도 승인 문서 개정이 필요하므로 이 slice 가 스스로 고치지 않는다 |
| L-6 | 「승인된 authoritative corpus 전체 통과」— 1B 축 corpus 0 건 | **N/A + 사유로 판정(공집합 통과로 계상하지 않음).** 위 완료 조건 표 참조 |
| L-7 | 크기 기반 단위 추측 분기 부재의 architecture test 축이 1A 게이트 집합에 없음 | **미착수.** `Rate.ofFraction`/`ofPercent` 는 실제로 이름만 보고 값 크기를 보지 않지만(P-3a), 이를 강제하는 architecture test 는 세우지 않았다 — 아래 「이월 항목」 |
| L-8 | `Provenance` ↔ `FactProvenance` 이름 불일치 | **승인 명세 표기(`Provenance`)를 채택.** A1·A2 와 같은 갈래 |
| L-9 | `a == b` 만 쓰면 `UNKNOWN`×`UNKNOWN` 이 통과 | **닫혔다.** `sameKnownVat`(`a == b && a != UNKNOWN`) 를 산술 함수 전건의 유일한 자리로 두고 property test(P-3c)로 고정했다 |
| L-10 | 잘못된 단위 거부 — 상한 밴드를 두면 D-4 금지 경로가 됨 | **하한만 둔다.** `Rate.init` 이 `fraction.signum() >= 0` 만 요구. E-4 example test 가 「2.0 을 클램프하지 않는다」를 고정한다 |
| L-11 | `Money` 상위 타입에 이항 연산이 들어가면 basis 차단이 무너짐 | **지켰다.** `Money` 는 `Comparable`·이항 연산을 구현하지 않는다. 각 금액 타입이 자기 축의 `Comparable<Self>` 만 구현 |
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

## 이월 항목 (Phase 3 범위 밖으로 명시 이월)

| 항목 | 왜 이번에 안 했는가 | 다음 |
| --- | --- | --- |
| **컴파일 실패 하네스**(설계 검토 §4.9, C8) | 설계 검토 자신이 "1B 의 마지막 커밋으로 미루고 타입 설계가 착지한 뒤에 붙이는 것을 추천"했고, `gate-tests.properties` 신규 등재(운영자 결정 지점)가 따라붙는다. 이 저장소가 게이트 하나마다 리뷰 라운드를 크게 쓴 이력(`CLAUDE.md` 변경 이력)을 고려해 타입 설계가 Codex 리뷰를 먼저 통과한 뒤 별도 slice 증분으로 붙이길 권한다 | 운영자 결정 대기 — 채택 시 `scope.md` out_of_scope → in_scope 승격 필요 |
| **크기 기반 단위 추측 분기 부재의 architecture test**(L-7, 설계 검토 §3.2 미달 판정) | 1A 게이트 집합에 이 축이 없고, 새 architecture test 자체가 `build-logic` 확장이 필요할 수 있어(위협 모델 경계 — `milestone-1.md` 「게이트 위협 모델」이 `build-logic/**` 을 방어 대상 밖으로 둔다) 범위가 이 slice 를 넘을 수 있다 | 후속 slice 또는 별도 결정 |
| **fixture 되돌림 실행**(C13, `OPEN-1B-CONTRACT`) | 계약 술어(presence/non-null·형태 / 의미 범주 / `equals-path`) 설계는 1B 소관이나 `fixtures/manifest.yaml` 편집(분류 되돌림·SHA-256 재기록)은 **fixture-curator 소관**이다(설계 검토 §4.7 단계 3). 이 slice(kotlin-implementer)가 직접 편집하면 레인 경계를 넘는다 | fixture-curator 에게 술어 설계를 넘기는 별도 요청 필요 — 이 checklist 가 그 설계 착수점(위 완료 조건 표의 `OPEN-1B-CONTRACT`)을 든다 |

## 리뷰 요청 조건 (`evidence-pack` SKILL)

| 항목 | 상태 |
| --- | --- |
| 구현 diff 가 커밋되어 base/head 고정 | **충족.** `git status --porcelain -- shared-kernel reports/evidence/m1/1b` 가 비어 있다(양성 대조는 `commands.md`) |
| `acceptance_commands` 전부 exit 0 | **충족.** B-0~B-7 전건 `commands.md` |
| test/lint/type/architecture 통과 | **충족.** `commands.md` B-1(전 모듈) |
| 변경된 fixture 와 정책 version 의 근거 | **N/A** — 이 slice 는 fixture 를 변경하지 않았다(`fixtures/manifest.yaml` 미편집). 정책 version 은 `VAT_RATE_POLICY`(source 문자열이 `data-dictionary.md` §12.1 을 가리킨다) 하나뿐이고 그 근거는 `PolicyTable.kt` 주석이 든다 |
| 알려진 제한과 rollback | 위 절들 + `rollback.md` |
| 비밀값 스캔 | **충족.** `commands.md` — 매치는 스캔 명령 자신의 인용문뿐(판독 규칙 적용) |
