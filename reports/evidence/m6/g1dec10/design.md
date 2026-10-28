# M6/G1-DEC10 — Phase 1 설계 (게시 낙찰하한율 신뢰 게이트)

계약은 `scope.md` 가 정본이다. 이 문서는 착수 설계 — ① 코드 지도(바이트코드 실측) ②
Phase 2.5 설계 검토 ③ in_scope 경로·acceptance ④ 팀장 결정이 필요한 질문이다. 구현은
팀장 수령 뒤에 시작한다.

## §0 코드 지도 — 게시 하한율 값이 생기는 자리 (바이트코드 실측)

측정은 grep 이 아니라 **컴파일된 main 바이트코드의 상수 풀 참조**다. `javap -p -c` 로 모듈별
`build/classes/kotlin/main` 전 클래스를 떠서 두 축을 센다 — `DivisionValueRules` 의 축 ①②와
같은 정의다.

- 축 ① — 참조 **소유자**가 `bidvector.sharedkernel.FloorRate` 인 멤버 접근(getter·`copy`·
  `equals` 무엇이든).
- 축 ② — 반환 타입 또는 필드 타입이 `FloorRate` 인 **획득**(`)Lbidvector/sharedkernel/FloorRate;`
  · `:Lbidvector/sharedkernel/FloorRate;`).

명령과 종료 코드는 `commands.md` 에 있다.

### 실측 결과 (base `23837b19` + 착수 커밋, main 클래스 13 디렉터리 · 참조 84,365)

| 축 | 참조 | 호출자 클래스 | 모듈 |
| --- | --- | --- | --- |
| ① `FloorRate` 멤버 접근 | 17 | 15 | `adapters` 4 · `decision` 4 · `procurement` 3 · `bidding` 1 · `shared-kernel` 1 · `workflow` 1 |
| ② `FloorRate` 획득 | 17 | 16 | `adapters` 4 · `decision` 4 · `procurement` 4 · `bidding` 1 · `shared-kernel` 1 · `workflow` 1 |
| (참고) `FloorRateOrigin` 참조 | 21 | — | 생성 자리 셋(`procurement.CanonicalizeKt` · `adapters.persistence.NoticeReconstructionKt` · `adapters.bidding.JdbcNoticeSnapshotQuery`)과 영속 codec |

이 수치는 ⓑ 게이트가 실제로 셀 집합의 **상계**다 — `DivisionValueRules` 축 ②는 **자기 소유
멤버 읽기(호출자 == 소유자)를 제외**하는데 census 는 그 제외를 적용하지 않는다. 운반 타입
(`Notice`·`NoticeCollected`·`bidding.NoticeSnapshot`·`BidMenuInput`·`OptionContext`·
`BidMenuGrounds`)의 자기 필드 읽기와 `FloorRate.copy` 를 빼면 **교차 소유 획득 쌍은 7 자리**다.
허용 목록의 크기는 그 값이다.

**census 가 찾아낸, 계약 문면에 없던 자리 하나** — `decision.menu.BidMenuGrounds` 가
`publishedFloorRate: FloorRate` 를 **결과로 내보낸다**(메뉴의 근거 칸). 가격 경로가 만든
출력이므로 **게이트 통과 값을 실어야** 근거가 정직하다(「이 가격이 어느 값에서 나왔는가」).
7C② 표시 상태의 입력이 되는 자리이기도 하다(OQ-3).

### 가격 경로 (게이트 대상, D-DEC10-3)

| # | 자리 | 값을 어디서 얻는가 | 오늘의 거동 | production 배선 |
| --- | --- | --- | --- | --- |
| ① | `decision.menu` — `BidMenuInput.publishedFloorRate` → `OptionContext.floorRate` → 효력 투찰율 하한·적용 하한·DEC-04 호출, 그리고 **`BidMenuGrounds.publishedFloorRate` 로 결과에 실려 나간다**(실측: 읽는 자리는 `BidMenuKernelKt`·`BidMenuStepsKt` 둘) | 호출부가 값으로 준다(port 없음) | `null` 이면 `SourceAbsent(PublishedFloorRateAbsent)`, 그 밖에는 **밴드 검사 없이** 율 축 하한으로 쓴다 | **없음** — `composeBidMenu` 의 호출자는 test 뿐이다 |
| ② | `decision` — `criticalAssessmentRateFor(bid, floor: FloorRate, policy)`(DEC-04 임계 사정률) | 인자 | 밴드 검사 없이 `criticalAssessmentRate` 로 넘긴다 | **없음** — 유일한 호출자가 ① 의 메뉴 단계 함수다 |
| ③ | `workflow.evaluation` — `PredictionFacts` 의 기대 마진 fact → `MarginInputs.floorRate` → `MarginDerivation` 의 floor headroom | `Notice.floorRate?.rate`(수집·영속에서 온 게시값) | `fraction > 1` 만 `DerivationAbsence.FloorRateOutOfRange` 로 걸러 margin 성분을 떨어뜨리고, `≤ 1` 은 그대로 점수에 들어간다 — **`1.00000`(R-FLOOR-03 의 라이브 47건)은 통과**해 `floorHeadroomOf` 의 `floor == 1` 가지로 접히고 **사유가 남지 않는다** | **없음** — `MlAnalysisPort` 출하 배선이 `UnavailableMlAnalysis` 고정(D-6A3-2, 운영자 결정 1)이라 `OpportunityAnalysis` 가 돌지 않는다(`OPEN-ML-ANALYSIS-WIRING`) |

**세 가격 경로 전부 출하 배선에서 돌지 않는다.** 그래서 이 slice 의 게이트는 「지금 새는 값을
막는다」가 아니라 **「배선되는 날 새지 못하게 구조를 먼저 닫는다」**이고, `floor_implausible`
계수는 출하 앱에서 구조상 항상 0 이다(§4 OQ-1). 이 사실을 알려진 제한으로 등재한다 —
R-FLOOR-04 가 경고한 「소비자가 X 만 하므로 안전」의 형태를 **주석이 아니라 게이트로** 적는
것이 이 slice 의 일이고, 그 게이트는 소비자가 늘 때 깨져야 한다.

### 분석·표시 경로 (게이트 비대상, D-DEC10-4)

| 자리 | 하는 일 |
| --- | --- |
| `procurement` — `Canonicalize` 의 하한율 변환 | 원문 percent → `Rate.ofPercent`, `origin = NoticeValue(noticeRound)`. 공고를 버리지 않는다 |
| `adapters.persistence` — `NoticeRow`·`NoticeRowMerge`·`JdbcNoticeRepository`·`NoticeReconstruction`·`ObservationPayloadCodec` | 영속·병합·재구성(fraction + origin kind/detail 세 열) |
| `adapters.snapshot` — `SnapshotRowAssembly`·`SnapshotWriter`·`SnapshotRawRow` | ML 스냅샷 `floor_rate` |
| `adapters.bidding` — `BidEffectWriter`·`JdbcNoticeSnapshotQuery`(7B `bidding.NoticeSnapshot`) | 초안 시점 공고 사본의 쓰기·읽기. **가격 계산 자리가 없다** — 실측: `NoticeSnapshot.floorRate` 의 소비자는 이 둘뿐이다 |
| `adapters.retention` — `NoticeAuditSnapshotAxis` | 감사 스냅샷 축 |

**원값 `FloorRate` 의 생성 자리는 셋이고 전부 이 쪽이다**(실측) — `procurement.CanonicalizeKt`
(수집 정규화) · `adapters.persistence.NoticeReconstructionKt`(DB 재구성) ·
`adapters.bidding.JdbcNoticeSnapshotQuery`(7B 스냅샷 재구성). 가격 경로에는 생성 자리가 없다 —
가격 경로는 **받아 쓰는** 쪽이고, 그래서 게이트의 자리는 「생성」이 아니라 「받는 자리」다.

**경계 하나 더** — `shared-kernel.RateArithmeticKt`(`criticalAssessmentRate`)는 원값 `FloorRate`
서명을 유지한다. shared-kernel 은 `decision` 의 게이트 타입을 볼 수 없다(의존 방향). 그 함수의
전건(「입력이 게이트를 통과한 값이다」)은 **호출부가 진다**: 유일한 호출자가 `decision` 의
`criticalAssessmentRateFor` 이고 그것이 게이트 값만 받게 되므로 closure 가 선다. 이 쌍은 ⓑ
허용 목록에 **「가격 산술 — 전건은 `decision` 이 닫는다」**로 분류해 적는다.

`procurement` 의 `CollectionPolicy.rangeBands`·`violatesExpectedRange` 는 **쓰지 않는다** —
금액 축 항목 drop 기계이고(그 자리의 주석이 「운영 정책은 아직 빈 표」라 적는다) 하한율 축에
걸면 공고를 버리게 된다(D-DEC10-4).

## §1 Phase 2.5 설계 검토

### (0) 위협 모델 경계

**방어한다** — 개연 밴드 밖 게시 하한율이 ⓐ 투찰가·투찰율 하한(①) ⓑ 하한 미달 판정의 임계
사정률(②) ⓒ verdict 사다리의 기대 마진 점수(③)에 닿는 것 · 그 제외가 **사유 없이** 사라지는 것
(침묵 스킵) · **새 소비자가 원값을 가격 경로로 끌고 오는 것**.

**방어하지 않는다** — 밴드 **안**의 틀린 값(원문 품질) · 법정 하한율 해석(DEC-03,
`StatutoryTable` origin) · 분석·표시 경로의 값(계수가 0 이 되면 원문 품질을 관측할 수 없다) ·
영속된 값의 사후 정정(마이그레이션 없음).

### (1) 열거인가 구성인가

**구성이다.** 소비자 목록을 열거하지 않는다. 게이트 통과 값은 **다른 타입**이고 가격 경로의
시그니처가 그 타입만 받는다 — ①②는 착수부터 그랬고, ③은 **r5 에서 그렇게 됐다**(D-DEC10-22).
착수 판의 이 문장은 ③ 에 대해 **참이 아니었다**: 그 진입이 날 `Rate?` 여서 원값을 다른 표현으로
세탁해 넣을 수 있었고(r4 실측), 그 사실이 네 라운드에 걸쳐 드러났다. 지금은 세 경로 전부
진입 타입이 게이트 산출물이고, 그 폐쇄를 컴파일 실패 fixture 가 잰다. 「소비자가 늘면 깨진다」는 두 등식이 진다 — 게이트 값
타입의 멤버 접근 쌍 등식(ⓐ)과 production 전 층의 원값 획득 쌍 등식(ⓑ). 둘 다 **관측 == 허용**
양방향이라 낡은 허용 항목도 실패한다.

### (2) 우회와 그것을 닫는 자리

| # | 우회 | 닫는 자리 |
| --- | --- | --- |
| 1 | 원값을 `copy` 로 게이트 값에 실어 나르기 | `@ConsistentCopyVisibility` + `internal` 생성자 → 모듈 밖 `copy` 컴파일 실패 fixture(음성 + 오타 변이 쌍둥이) |
| 2 | 모듈 밖에서 게이트 값을 직접 생성 | 같은 폐쇄의 다른 명제 — 별 fixture(1B·DEC-05 하네스 관례: 생성 폐쇄와 `copy` 폐쇄는 다른 fixture) |
| 3 | **모듈 안**에서 게이트 함수를 거치지 않고 생성 — `internal` 생성자 직접 호출 **또는 `internal` 된 `copy`** | ⓐ 바이트코드 등식 — 게이트 값 타입의 **모든 멤버 접근** (호출자, 멤버) 쌍 집합 == 허용 쌍(`DivisionValueRules` 축 ①: 멤버 이름을 열거하지 않으므로 새 멤버·`copy`·companion 이 생기면 그 쌍이 새로 관측된다). 생성 계열(`<init>`·`copy`) 쌍은 **게이트 함수 하나**뿐이다 |
| 4 | 가격 경로가 `Notice.floorRate`(또는 다른 운반 슬롯)를 직접 읽기 | ⓑ 바이트코드 등식 — **production 전 층**에서 원값 `FloorRate` 를 **획득**하는 (호출자, 소유자#멤버) 쌍 집합 == 허용 쌍. 허용 목록이 쌍마다 **가격/분석·표시**를 분류해 적고, 가격 쪽 허용은 「게이트에 먹이는 자리」뿐이다. 패키지 목록으로 범위를 좁히지 않는 이유는 R-FLOOR-04 자체다 — 좁히면 **새 패키지의 새 가격 경로**가 게이트 밖에 생긴다. 대가는 허용 목록이 분석·표시 쌍까지 담아 영속·스냅샷 변경이 한 줄을 요구하는 것이고, 그것이 「소비자가 늘면 깨진다」의 값이다 |
| 5 | 개연 불가 가지를 `else` 로 통과 처리 | 결과가 **sealed 셋**이고 가격 경로는 통과 값만 받는 시그니처 → `else` 로 뭉개면 통과 값을 얻을 수 없어 컴파일되지 않는다. 사유별 거동은 세 가지 전수 test |
| 6 | 경계값 — `0.30`·`0.995` 정확값, `0.2999…`·`0.9951`, 척도 변형(`0.3000`) | 밴드 포함 경계 전수 test. `PlausibilityBand.contains` 는 `BigDecimal` 비교(`compareTo`)라 척도에 둔감하고, `Rate` 는 생성 시 정규화된다 — 그 둘을 함께 잠근다 |
| 7 | `StatutoryTable` origin 위장 — 밴드 밖 값에 법정표 origin 을 달아 게이트를 비켜 가기 | 게이트는 `NoticeValue` 에만 **통과**를 내준다. `StatutoryTable` 은 통과도 개연 불가도 아닌 **별 가지**(이 게이트가 심판이 아닌 값)로 떨어지고 가격 경로는 통과 값만 받으므로 그 값으로는 가격이 서지 않는다. 오늘 생성 자리 0 이고, DEC-03 해석기가 생기는 날 **그 slice 가 자기 게이트를 들어야 한다**는 사실이 타입으로 남는다 |
| 8 | margin 경로가 `rate` 를 **다른 함수**로 읽기(`criticalAssessmentRate` 직접 호출 등) | ⓑ 는 함수 이름이 아니라 **값의 획득**을 센다 — 원값을 손에 쥘 수 없으면 어느 함수에도 넘길 수 없다. shared-kernel 의 `criticalAssessmentRate`(원값 서명)는 그대로 둔다: 그 함수의 입력을 가격 경로가 얻을 길이 없다 |
| 9 | 게이트 함수를 부르고 결과를 **버리고** 원값을 쓰기 | ⓑ 의 허용 쌍이 「게이트에 먹이는 자리」로 한정되고, 그 자리의 반환을 쓰지 않으면 가격 경로가 쓸 floor 값이 없다(통과 값만 받는 시그니처). 다만 **데이터 흐름은 구조로 증명되지 않는다** — 같은 클래스 안에서 획득과 게이트 호출의 **공존**만 재므로, 그 한계를 알려진 제한으로 적는다 |
| 10 | 리플렉션·직렬화로 게이트 값 생성 | `decision` 은 도메인 모듈이라 직렬화 표면이 없고(JSON·JPA import 0, `domainDependencyGate`), 리플렉션 뿌리 게이트(`collection.reflection.roots`)가 production 전 층을 덮는다. 구조로 닫지 못하는 범위(제네릭 소멸·컨테이너 원소)는 기존 `OPEN-6F9-DIVISION-REFLECTION` 과 같은 형태로 등재 |

### (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 무엇을 내주는가 | 경계 |
| --- | --- | --- |
| 게이트 함수(유일한 생성 경로) | 통과·부재·개연 불가·심판 아님 네 가지 | 정책을 인자로 받는다 — 밴드를 스스로 짓지 않는다 |
| 게이트 값 타입의 공개 읽기 | `rate: Rate` **하나만** 공개한다 | 원값 `FloorRate` 는 `internal` 로 둔다 → 모듈 밖(= `workflow` 의 margin 경로)은 원값을 손에 쥘 수 없다. ②(DEC-04)는 같은 모듈이라 `internal` 읽기로 shared-kernel 에 넘기고, 그 한 쌍이 ⓑ 허용의 전부가 된다 |
| 개연 불가·부재 가지 | 원값 + 밴드(사유 있는 실패는 어디서든 만들 수 있다 — `FloorShortfall.Unmeasurable`·`FloorOverrideOutcome.Rejected` 관례) | 가격이 서지 않는 값이라 위조할 것이 없다 |
| 출하 정책 데이터 | 밴드 `[0.30, 0.995]` | `EffectiveDatedPolicy` + `PolicyVersion`(`BID_MENU_POLICY` 관례). 값 일치 test 가 Python 백테스트 yaml 과 묶는다 |
| 사유 어휘(가격 경로별) | 메뉴 `MenuSourceAbsence` 한 항 · margin 쪽 `DerivationAbsence` 한 항 | **부재와 다른 이름**이어야 acceptance ①·④ 가 선다 |
| `object` 커널 주입 자리 | **없다** — 게이트는 top-level 함수이고 상태를 갖지 않는다(`FloorOverrideValidation` 은 기존 `object` 이나 이 slice 는 그 타입을 바꾸지 않는다) | |

### (3) 과잉·미달

- **과잉이면** — 분석·표시 경로(수집 정규화·영속·ML 스냅샷·7B 스냅샷)에 게이트를 걸면 계수가
  0 이 되고 원문 품질을 관측할 수 없다(DEC-10 분류 근거의 「의도적으로 다르다」). 걸지 않는다.
- **과잉이면 ②** — `shared-kernel` 의 `FloorRate` 자체에 `init` 밴드 검사를 넣는 것. 그러면
  수집이 공고를 버리고(또는 던지고) 분석 경로가 죽는다. 넣지 않는다.
- **미달이면** — margin(③)을 빼면, 투찰가를 내지 않는다는 이유로 **verdict 점수**가 밴드 밖
  값을 먹는다. `1.00000` 이 오늘 조용히 `floorHeadroom = recommended` 로 접히는 자리가 바로
  그것이다. 넣는다.
- **미달이면 ②** — 게이트를 걸고 **계수를 남기지 않으면** 침묵 스킵이고 acceptance 셋째가 선다.

## §2 설계 요약

1. **정책**(`decision`) — `PublishedFloorTrustPolicyData(band: PlausibilityBand)` +
   출하 `EffectiveDatedPolicy` 정본(밴드 `[0.30, 0.995]`, 운영자 결정 2026-10-24 ·
   data dictionary §1.4.3 B6 근거를 `source` 에 적는다). 기존 `PlausibilityBand` 재사용 —
   `FloorOverrideValidation` 의 거동은 바꾸지 않는다.
2. **게이트**(`decision`) — 게이트 값 타입(`internal` 생성자 + `@ConsistentCopyVisibility`,
   공개 읽기는 `rate: Rate` 하나) · 네 가지 sealed 결과 · 생성 경로는 게이트 함수 하나.
3. **가격 경로 전환** — ① `BidMenuInput` 의 칸이 게이트 결과를 받고 커널이 소진 `when` 으로
   가른다(개연 불가 → `SourceAbsent` + 별 사유) ② `criticalAssessmentRateFor` 가 게이트 값을
   받는다 ③ margin 은 게이트 결과를 받아 개연 불가를 **`floorRate = null` 과 같은 값**으로
   접고 사유를 남긴다(DEC-10 acceptance 첫째 「하한 미보고와 동일하게 취급」 — 오늘
   `1.00000` 이 이미 그 값으로 접히므로 거동 회귀가 없다. `fraction > 1` 은 개연 불가가
   먼저 잡으므로 `FloorRateOutOfRange` 의 생산자가 0 이 된다 → §4 OQ-2).
4. **계수** — 사유를 결과 값에 싣고 평가 실행 집계(`EvaluationCommitTally` + finish 줄,
   dry-run 대칭)에 사유별 건수로 낸다. 영속 계수·OpenAPI 필드는 이 slice 밖.
5. **구조 test** — ⓐ 게이트 값 멤버 접근 쌍 등식 ⓑ 원값 획득 쌍 등식(둘 다 ArchUnit +
   `architecture-policy.properties` 허용 쌍, `DivisionValueRules` 틀) ⓒ 컴파일 실패 fixture
   (생성·`copy` + 오타 변이 쌍둥이) ⓓ 양성 대조(관측 집합 비지 않음) · 음성 fixture 루트
   (`archfixture/violating`) · 밴드 경계 전수 · 값 일치 test(Python yaml).
   텍스트 grep 술어는 쓰지 않는다.

## §3 in_scope 경로 · acceptance · rollback

### in_scope (계약 문면 → 실제 경로)

| 계약 문면 | 경로 |
| --- | --- |
| `decision` 게이트·가격 커널 입력 | `decision/src/main/kotlin/bidvector/decision/` (게이트 신규 파일 · `FloorShortfallKernel.kt` · `menu/`) · `decision/src/test/kotlin/bidvector/decision/` · `decision/src/test/resources/compile-fixtures/` |
| `shared-kernel` 의 필요한 자리 | **오늘 계획 0** — 게이트 값을 `decision` 에 두면 `shared-kernel` 편집이 없다(`criticalAssessmentRate` 는 원값 서명 그대로). 필요해지면 계약을 갱신한다 |
| `workflow/**/evaluation/**` | `workflow/src/main/kotlin/bidvector/workflow/evaluation/` (`PredictionFacts.kt` · `EmbeddingBridge.kt` 사유 사상 · `Ports.kt` · `CandidateEvaluation.kt` · `EvaluateCandidatesUseCase.kt`) · 같은 경로의 test |
| `app` 평가 집계 줄과 architecture test | `app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitLines.kt` · `app/src/test/kotlin/bidvector/app/architecture/` · `app/src/test/kotlin/bidvector/archfixture/` · `app/build.gradle.kts`(값 일치 test 의 정책 파일 경로 system property) · `app/src/test/kotlin/bidvector/app/evaluation/`·`http/`(집계·dry-run 대칭 test) |
| `config/quality/**` 의 필요한 키 | `config/quality/architecture-policy.properties`(허용 쌍) · `config/quality/gate-tests.properties`(신규 test 클래스 등재 — 등재 게이트가 모집단 등식을 양방향으로 잰다) |
| `procurement` 의 `rangeBands` 주석 문면 | `procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt` (주석만) |
| 문면 정정 다섯 | 위 `CollectionPolicy.kt` 주석 · `reports/evidence/m3/3a/policy-values.md` · `reports/evidence/m6/6g/policy-values.md` · `reports/evidence/m6/6e1/acceptance-trace.md`(COL-07·DEC-03 홀더 칸) |
| capability map · 추적표 · 마일스톤 | `docs/discovery/capability-map.md` DEC-10 절 · `reports/evidence/m6/6e1/acceptance-trace.md` DEC-10 행과 ⓐⓑⓒ 집계 · `milestone-6.md` 결정 블록 |
| evidence | `reports/evidence/m6/g1dec10/` |
| D-DEC10-7 이 이 slice 몫이 되면 | `openapi/` · `web/src/api/generated/` · 7C② 표시 상태 파일 — **오늘 미착수**(§4 OQ-3) |

### out_scope

마이그레이션 · 수집 정규화 거동 · Python `ml-engine/`(값 일치 test 는 Kotlin 이 yaml 을
읽는다) · ML 배선(`OPEN-ML-ANALYSIS-WIRING`) · DEC-03 해석기 · 투찰가 clamp 축
(`OPEN-DEC02-SCORE-AXIS-FLOOR`).

### acceptance

`ci.yml` 의 `check` job 명령 그대로 — `./gradlew --no-daemon check`. OpenAPI 가 in_scope 로
들어오면(OQ-3) `web` job 도 붙는다. `ml-engine`·`container` 는 입력 변경 0 을 path-limited
diff 로 보이면 생략한다.

### rollback

`rollback.md` 가 정본이다(목록 기계 산출 · 공유 문서의 고정 SHA 복원 · 임시 clone ①~⑥ 실측
· 실측 HEAD). 이 문서는 그 절차를 다시 적지 않는다.

## §4 팀장 결정이 필요한 질문

### OQ-1 — 계수가 닿는 자리 (추천: 네 타입 체인)

`floor_implausible` 을 평가 실행 집계에 **사유별 건수**로 내려면 사유가 `CandidateEvaluation`
까지 올라와야 한다. 오늘 그 길이 끊겨 있다 — margin 사유는 `ScoreFact.Absent` 에만 있고
`PriorityOutcome.Composed` 는 `droppedComponents`(사유 없는 `Component` 집합)만 나른다.

- **추천** — 사유를 네 자리에 싣는다: `PredictionComponents`(internal) → `MlAnalysisOutcome.Analyzed`
  (필수 인자, 기본값 없음 — 그 타입의 `evidence` 가 이미 쓰는 처방) → `CandidateEvaluation.Reached`
  (internal 생성자라 생성 경로가 use case 하나) → `EvaluationCommitTally`·finish 줄. 전부
  in_scope 이고 전부 컴파일 거부로 「조용히 사라짐」을 막는다. HTTP dry-run 응답은 평탄 스칼라
  규칙(D-6A3-6)대로 이 필드를 읽지 않으므로 **OpenAPI 변경이 없다**.
- **대안(더 좁게)** — `MlAnalysisOutcome.Analyzed` 까지만 싣고 집계 줄을 더하지 않는다.
  D-DEC10-5 의 「평가 실행 집계」 절은 충족되지 않는다.
- **딸린 사실** — 어느 쪽이든 출하 배선에서 그 계수는 **구조상 항상 0**이다(`MlAnalysisPort` =
  `UnavailableMlAnalysis`). 계수가 실제로 움직이는 것은 `OpportunityAnalysis` 를 직접 도는
  test(dry-run E2E 포함)뿐이고, 그 사실을 알려진 제한으로 등재한다.

### OQ-2 — `DerivationAbsence.FloorRateOutOfRange` 의 처분 (추천: 개연 불가가 승계)

게이트가 `> 0.995` 를 먼저 잡으므로 `fraction > 1` 전용 사유는 생산자가 0 이 된다.

- **추천** — 그 항을 개연 불가 사유로 **승계**한다(항 하나, 생산자 하나 = 게이트). `MarginInputs.init`
  의 `≤ 1` 요구는 타입 최후 방어선으로 **그대로 둔다**(그 KDoc 이 「이 `init` 이 유일한 관문」
  이라 적는 문면은 이 slice 가 정정한다 — 이제 게이트가 앞에 선다). `bridgeDerivationAbsence`
  의 사상 한 줄이 함께 바뀐다.
- **대안** — 두 항을 함께 둔다. 인접 값(`0.996` vs `1.0001`)이 서로 다른 거동(접기 vs 성분
  드롭)을 갖는 상태가 남고, 사유 어휘가 둘로 겹친다.

### OQ-3 — 7C② 표시 상태 (추천: 이 slice 는 착수하지 않고 머지 순서를 보고 결정)

**실측**: base `23837b19` 에 `FloorRateGroundsState` 가 **없다**(`m7-7c2` 미머지 — base 의
머지 목록은 #89 7B · #90 7C④ · #91 7C⑤ · #92 container · #93 DEC-05). D-DEC10-7 의 전제
(「7C② 가 먼저 머지될 것이 확실」)가 착수 시점에 성립하지 않는다.

- **추천** — 이 slice 는 `openapi/`·`web/` 을 만지지 않고 게이트만 낸다. 리뷰 요청 시점에
  7C② 가 main 에 있으면 main 을 병합해 **그 위에서** 표시 상태를 더하고 `web` job 을
  acceptance 에 붙인다(D-DEC10-7 그대로). 없으면 그 항을 **나중에 머지되는 쪽**(= 7C②)에
  인계하고 그 사실을 계약·evidence 에 적는다. 어느 쪽이든 「양쪽 다 안 더함」이 되지 않도록
  종결 보고에 한 줄로 남긴다.
- **대안** — 7C② 머지를 기다렸다 착수한다(이 slice 가 블록된다).

### OQ-4 — 값 일치 test 의 자리 (추천: `app` test)

Python 백테스트 yaml(`floor.rate_band_low`·`high`)과의 값 일치 test 는 yaml 파서가 필요하다.
`decision` 모듈 test 에는 없고(coroutines·kotlin-compiler-embeddable 둘뿐), `app` test 에는
snakeyaml 과 **정책 파일 경로를 system property 로 넘기는 기존 관례**가 있다.

- **추천** — `app/src/test` 에 두고 `app/build.gradle.kts` 에 ml-engine 정책 파일 경로
  system property 한 줄을 더한다(기존 네 선례와 같은 형태). `decision` 에 의존을 더하지 않고
  손으로 yaml 을 쪼개지 않는다.
- **대안** — `decision` test 에 yaml 파서를 더한다(도메인 모듈의 test classpath 가 넓어진다).
