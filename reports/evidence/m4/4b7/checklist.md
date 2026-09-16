# M4/4B-7 checklist — 과거 경쟁 표본 공급

## D-4B7-1~9 근거(함수·test 이름)

| 결정 | 구현 | test |
| --- | --- | --- |
| D-4B7-1 (a) — `bsisPlnprc`를 기초금액 축 원문 관측값으로, `Published(표본 자기 회차)` | `reservePriceAmounts`(`SampleConversion.kt`) | `SampleEligibilityTest`「정상 — 자격을 모두 만족하면 Eligible, 필드가 전수 변환된다」의 `reserveDraw.reservePrices.first().provenance` 대조(회차 `"001"` — 대상 아님) |
| D-4B7-2 — 자격 규칙표 ②~⑦(+ verifier r1 신설 둘: `CANDIDATE_VANISHED`·`RESERVE_PRICE_SEQUENCE_INVALID`, 사유 8종) | `judgeEligibility`+`Check<T>` guard 체인(`SampleEligibility.kt`), `JdbcCompetitionSampleSource.aggregate`(`CANDIDATE_VANISHED`) | `SampleEligibilityTest`의 사유별 test(아래 대응표) + `JdbcCompetitionSampleSourceTest`「합계 불변식」 |
| D-4B7-3 — 조회 축(공종·대상 제외·개찰일 창·최신 순·상한) | `CompetitionSampleQuery`(`Ports.kt`), `Sql.SELECT_COMPETITION_SAMPLE_CANDIDATES`, `OpportunityPolicyData.sampleWindowDays`·`maxSamples` | `JdbcCompetitionSampleSourceTest`(창·상한·최신순·대상제외·공종일치·결측포함), `OpportunityAnalysisTest`「businessCategory 있으면 categoryCode 를 실은 query 로 port 를 부른다」 |
| D-4B7-4 — 어댑터는 조인만, 판정은 workflow | `JdbcCompetitionSampleSource.aggregate`가 `judgeEligibility` 호출, SQL은 자격 조건 없음(`Sql.kt` 주석) | 컴파일 시점 구조(어댑터에 자격 predicate 없음) + `JdbcCompetitionSampleSourceTest`「개찰일 결측 후보는 초과 집합으로 포함되고...」(SQL이 거르지 않음을 실증) |
| D-4B7-5 — `ReserveDrawObservation`·`CompetitionSample.reserveDraw`·`toProto()` 7번 필드 | `BidPredictionRequest.kt`, `RequestMapping.kt`→`MoneyMapping.kt`의 `toProtoReserveDraw()` | `PredictionValueTest`(init 넷) + `RequestMappingTest`(왕복 셋 — 15가격/4번호, null 미채움, 빈 집합) |
| D-4B7-6 — 마이그레이션 없음 | `db/migration/` 무변경(git status) | 해당 없음(migration-reviewer 불요) |
| D-4B7-7 — `actual_opening_at` → `Asia/Seoul` 날짜, 결측 제외 | `OPENING_DATE_ZONE`, `openedOnCheck`(`SampleEligibility.kt`) | `SampleEligibilityTest`「정상」 test의 `openedOn` 경계값 대조(UTC 15:30 → KST 다음날 00:30) + 「OPENING_DATE_MISSING」 test |
| D-4B7-8 — provenance 라벨(`ProvenanceRules.judgeRow` 첫 production 호출) | `provenanceLabelFor`(`SampleConversion.kt`), `budgetEstimate = notice.estimatedAmount` | `SampleEligibilityTest`「라벨 — ... Clean」·「라벨 — ... SuspectRatio」 |
| D-4B7-9 — `analyze`가 표본 조회 → `predictionRequestFor` 인자, `Unavailable` → `Absent(ScoreNotProvided)` 계열 | `OpportunityAnalysis.competitionSampleSupplyFor`+`predictionFacts` | `OpportunityAnalysisTest`의 4B-7 절 넷(query 실림·무공종 접힘·Supplied 전달·Unavailable 시 `prediction.callCount == 0`) |

## 우회 (1)~(18) 대응표

| 우회 | 방어 | 실측 |
| --- | --- | --- |
| (1) 미래 표본 누출 | `Sql` WHERE의 `actual_opening_at < asOf` + 대상 자기 제외 | `JdbcCompetitionSampleSourceTest`「개찰일이 미래거나 창 밖이면 후보에서 빠진다」 |
| (2) 14행을 15로 채움 | `RESERVE_PRICE_COUNT_MISMATCH`(정확 일치) | `SampleEligibilityTest`「RESERVE_PRICE_COUNT_MISMATCH — 14행」 |
| (3) 추첨 번호 범위 밖 | `OutOfRange` → `DRAW_NUMBERS_OUT_OF_RANGE` | `SampleEligibilityTest`「DRAW_NUMBERS_OUT_OF_RANGE」 |
| (4) 협상 계약 낙찰율 없음 | `Determined`인데 `bidRate == null` → `RANK_ONE_RATE_MISSING` | `SampleEligibilityTest`「... Determined 이지만 bidRate 없음」 |
| (5) 오염 표본이 CLEAN으로 | 공급 측 CLEAN 필터 없음 — 라벨만 붙이고 엔진 `admit_clean`이 거른다(`provenanceLabelFor`에 필터 분기 없음, `sampleOf`가 라벨을 그대로 싣는다) | 코드 검토(필터 코드 부재가 곧 증거) — SuspectRatio 라벨이 달려도 표본이 나간다는 것은 `SampleEligibilityTest`「라벨 — ... SuspectRatio」가 `Eligible`을 반환함으로 확인 |
| (6) 예비가격 basis 오판 | D-4B7-1 운영자 결정 + 엔진 `CENTER_OUT_OF_BAND`(0.8~1.2) fail-closed — 엔진 쪽 방어, 이 slice는 정직 변환만 | 범위 밖(엔진 관문) — evidence는 basis 채택 근거만 진다(policy-values.md §3) |
| (7) SQL에 판정 로직 | `Sql.SELECT_COMPETITION_SAMPLE_CANDIDATES`는 공종·대상 제외·(NULL-safe) 개찰일 창만 — 자격 predicate(①~⑦) 0건 | SQL 문 자체(코드 검토) + (4)의 결측 포함 test가 SQL이 자격을 안 거른다는 것을 간접 실증 |
| (8) DB 예외가 analyze 를 죽임 | `samplesFor`가 `SQLException`을 잡아 `Unavailable(TransportFailed)` 값으로 접는다 | `JdbcCompetitionSampleSourceTest`「DB 접속 실패는 예외 대신 Unavailable 값으로 접힌다」(닫힌 포트로 실측, Testcontainers 컨테이너 불사용) |
| (9) 공종 정규화 불일치 | Kotlin 측은 `CategoryCode` 값 동일 비교(정규화 없음) — 아래 「우회 (16) 실측」 참고 | `JdbcCompetitionSampleSourceTest`「공종이 다르면 후보에서 빠진다」(정확 일치 확인) |
| (10)(13) `maxSamples` 초과 시 오래된 표본 잘림 | `ORDER BY actual_opening_at DESC NULLS LAST LIMIT` | `JdbcCompetitionSampleSourceTest`「상한을 넘는 후보는 최신 순으로 잘리고 결측일 후보가 상한을 먼저 먹지 않는다」(verifier r1 F-1 뒤 개정 — 서로 다른 날짜 3개 + 결측 1개로 정렬을 실제로 구별한다). **변이 실측**: `ORDER BY … DESC` → `ASC`로 바꾸면 이 test가 `expected:<[2026-09-15, 2026-09-14]> but was:<[2026-09-13, 2026-09-14]>`로 실패하고, 되돌리면 다시 통과한다(원복 확인 완료) — commands.md에 명령·exit 기록 |
| (11) `selected_numbers` 중복/0 | `Set<Int>` + `ReserveDrawObservation.init`(전부 ≥1) | `PredictionValueTest`「selectedNumbers 에 1 미만 값이 있으면 거부한다」 |
| (12)(18) `Published(round)` 위조 | 변환 입력이 표본 `Notice` 자체(`notice.id.round`) — 대상 공고 회차를 빌리지 않는다 | `SampleEligibilityTest`「정상」 test(round `"001"` 로 판정, 대상과 다름을 대조) |
| (14) 재관측 행의 최신성 | 이 slice는 읽기만(`find` 재사용) — 저장 층 UPSERT가 이미 축 단위 최신 우선(M3/3E·3F 종결 사항) | 범위 밖(기존 persistence test suite가 커버, 재검증 안 함) |
| (15) `is_drawn` 표시 불일치 | 자격에 넣지 않음(과잉 금지, 엔진이 `selected_numbers`를 거부 축으로 안 씀) | 설계 결정 — `judgeEligibility`에 `isDrawn` 검사 부재가 곧 증거 |
| (16) 공종 코드 정규화 | 「우회 (16) 실측」 절 참고 — 팀장 보고 사항 | 아래 |
| (17) 정책 슬롯 placeholder | `policy-values.md` §1·§2·§3 | 문서 |

## 우회 (16) 실측 — CategoryCode 정규화(팀장 보고)

`procurement/src/main/kotlin/bidvector/procurement/BusinessCategory.kt`의 `CategoryCode.init`은
`require(value.isNotBlank())`만 두고 있다 — 공백(앞뒤·중간)이나 대소문자를 정규화·거부하지
않는다("A01 "·"a01"이 모두 통과한다). 팀장 지시("아니면 조회 술어에 정규화를 두지 말고
팀장에게 보고")에 따라 **SQL 조회 술어(`Sql.SELECT_COMPETITION_SAMPLE_CANDIDATES`)에
TRIM/정규화를 추가하지 않았다** — 정확 일치(`n.business_category_code = ?`)만 쓴다. 공종
수집 시점에 정규화가 이미 되는지는 이 slice가 확인하지 않았다 — `OPEN-4B7-CATEGORY-NORMALIZATION`
으로 별도 등재한다(범위: 표본 조회·대상 공고 양쪽 다 같은 `CategoryCode` 값 비교 축).

## (2b) 값 획득 축 — 실측(생성부 목록)

| 표면 | 실측 생성부 |
| --- | --- |
| `CompetitionSample(` | `BidPredictionRequest.kt:86`(타입 선언) · `SampleConversion.kt`의 `sampleOf`(유일한 production 생성부 — verifier r1 뒤 `SampleEligibility.kt`에서 이관, TooManyFunctions) · test 셋(`OpportunityAnalysisTest`·`RequestMappingTest`·`MlTestFixtures`) |
| `OpportunityAnalysis(` | test만(`OpportunityAnalysisTest`) — production 조립 근(`app/`)은 M6 |
| `judgeEligibility(` | `JdbcCompetitionSampleSource.aggregate`(유일한 production 호출부) · `SampleEligibilityTest`(직접 호출) |
| `CompetitionSamplePort`·`CompetitionSampleQuery`·`CompetitionSampleSupply` | 정의(`Ports.kt`) · 구현체는 `JdbcCompetitionSampleSource` 하나 · test fake `FakeCompetitionSamplePort`(workflow 쪽) |
| `SampleEligibilityPolicyData`·`SAMPLE_ELIGIBILITY_POLICY`·`SAMPLE_PROVENANCE_POLICY`·`OPENING_DATE_ZONE` | `SampleEligibility.kt` 선언, 소비는 `judgeEligibility`(직접 인자) 또는 `JdbcCompetitionSampleSource`(정책 singleton 참조) |
| `reservePriceAmounts`·`drawNumberSet`·`provenanceLabelFor`·`sampleOf` | `internal`(모듈 스코프, `SampleConversion.kt`) — `SampleEligibility.kt`의 `judgeEligibility`/`Check` 함수들만 호출. 「경계로 처리」 행 없음 |

설계 검토 (2b) 표는 `SampleEligibility(internal 함수들) | 닫는다 — 같은 패키지`로 적었으나,
**실제 구현은 이 판단을 갱신한다** — `judgeEligibility`는 `public`이다(아래 「설계 이탈」 절).
그 밖 표면(포트·값 타입·정책 singleton)은 scope.md (2b) 표 그대로 닫힌다 — 구현체 주입은
기존 `BidPredictionPort` 관례와 같은 자리(없던 권한 아님), `Supply`는 도메인 쌍에서만
값을 만든다(지어내지 않음).

**verifier r1 수정 라운드 뒤 재확인(2026-09-16)** — 새로 연 public 표면 0. 신설 함수
(`resolveEligibilityPolicy`·`recordOutcome`·`reservePriceSequenceCheck`)는 전부
`private`(`JdbcCompetitionSampleSource`·`SampleEligibility.kt` 각각 파일 스코프)이고,
`sampleOf`는 `public`→`internal`로 이관됐을 뿐 가시성이 더 넓어지지 않았다. `SampleExclusionReason`
에 값 둘(`CANDIDATE_VANISHED`·`RESERVE_PRICE_SEQUENCE_INVALID`)이 늘었으나 이미 `public`
enum이라 값 추가는 새 권한이 아니다(같은 enum을 소비하는 `when`은 소진적이라 컴파일이
빠짐을 막는다).

## 설계 이탈 — 팀장 보고 사항 둘

1. **`judgeEligibility`는 `public`이다(설계 검토 (2b) 「internal, 같은 패키지」 가정과 다름).**
   D-4B7-4는 `CompetitionSamplePort.samplesFor`의 반환 타입 자체가 이미 판정이 끝난
   `CompetitionSampleSupply`라고 못 박는다(`Ports.kt` 계약) — 즉 자격 판정은 **port 구현
   (어댑터) 안에서** 끝나야 한다. 어댑터는 다른 Gradle 모듈(`adapters`)이라 `judgeEligibility`
   (`workflow` 모듈)를 `internal`로 두면 컴파일이 안 된다. `OPENING_DATE_ZONE`도 같은
   이유로 `public`이다(어댑터 test가 날짜 경계를 직접 대조하려면 필요). 값 획득 축 관점의
   위험은 낮다 — `judgeEligibility`는 순수 함수이고 새 권한(쓰기·위조 가능한 값)을 열지
   않는다.
2. **`JdbcCompetitionSampleSource.kt`가 scope.md in_scope 경로(`adapters/persistence/`)가
   아니라 `adapters/ml/`에 있다.** `PersistenceAdapterDependencyTest`(기존 게이트, `check`
   전건 실행 중 실측 발견)가 `adapters.persistence` 패키지의 domain import를
   `procurement`·`shared-kernel`로 좁게 막는다(`bidvector.decision`·`bidvector.workflow`
   거부) — `judgeEligibility`(`decision.ProvenancePolicyData` 소비)와
   `CompetitionSamplePort`(`workflow.evaluation`)를 직접 다루는 이 클래스는 그 경계를
   구조적으로 넘는다. `adapters.ml` 패키지는 정확히 이 넷(`procurement`·`decision`·
   `sharedkernel`·`workflow`)을 허용 목록으로 이미 갖고 있다(`MlAdapterDependencyTest`,
   기존 `RequestMapping.kt`가 같은 이유로 `workflow.prediction`을 쓴다). 테스트 파일도 같이
   옮겼다(`adapters/src/test/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSourceTest.kt`).
3. **`JdbcNoticeRepository`·`JdbcOpeningResultRepository`(구현체)를 직접 재사용하지 않고,
   `procurement`의 port 인터페이스(`NoticeRepository`·`OpeningResultRepository`)를 생성자로
   받는다 — `Sql.SELECT_COMPETITION_SAMPLE_CANDIDATES`도 `Sql.kt`가 아니라 이 클래스 안에
   직접 둔다.** 2를 고친 뒤 같은 게이트(`MlAdapterDependencyTest`)를 다시 돌리니
   `bidvector.adapters.persistence.JdbcNoticeRepository`·`...JdbcOpeningResultRepository`·
   `...Sql` import 셋이 또 걸렸다(`check` 재실행 실측) — 이 게이트는 allow-list 방식이라
   `procurement`·`decision`·`sharedkernel`·`workflow` 넷 **밖의 모든 `bidvector.*` import**를
   거부한다. 형제 패키지(`adapters.persistence`)도 그 넷에 없어 거부 대상이다. 그래서
   `JdbcCompetitionSampleSource`는 구현체 대신 `procurement`가 이미 소유한 port
   인터페이스만 받고(값 획득 축 관점: 새 권한이 아니다 — `OpportunityAnalysis`가 포트를
   주입받는 것과 같은 자리), 실 JDBC 구현은 조립 근(M6, `app`)이 주입한다.
   `SELECT_COMPETITION_SAMPLE_CANDIDATES`는 `Sql.kt`에서 이 클래스의 `private companion
   object`로 옮겼다 — 공유하면 그 참조 자체가 게이트에 걸리므로 「중복 금지」보다 「경계가
   강제한 위치」다. test(`adapters/src/test/kotlin/bidvector/adapters/ml/...`)는 게이트
   스캔 대상이 아니라서(`src/main`만 스캔) `JdbcNoticeRepository`·`JdbcOpeningResultRepository`
   구현체를 그대로 생성해 주입한다 — 재사용 자체가 사라진 것이 아니라 **주입 경계가
   test/조립 근으로 옮겨졌다**.
4. **`reservePriceAmounts`의 정렬에 `sortedBy { ... }`(inline `compareBy` 경유)를 쓰지
   않는다** — F-4 수정 중 실측: 그 형태는 합성 클래스의 `SourceFile` 디버그 속성이 stdlib
   `Comparisons.kt`로 남아 `workflow:jarContentGate`(ADR 0006 §6)가 「게이트를 통과한
   소스가 아니다」로 거부한다. `adapters/persistence/ObservationPayloadCodec.kt`가 이미
   문서화한 같은 함정(`CONTRACT_RAW_NAME_ORDER`)이라 같은 처방(이름 있는 `Comparator`,
   `RESERVE_PRICE_SEQUENCE_ORDER`)을 그대로 썼다. 새 이탈이 아니라 기존에 등재된 규율의
   재적용이다.

## verifier r1 수정 라운드(F-1~F-4·F-11) — 무엇을 어떻게 닫았는가

- **F-1(HIGH)** — `JdbcCompetitionSampleSourceTest`「상한을 넘는 후보는 최신 순으로
  잘리고 결측일 후보가 상한을 먼저 먹지 않는다」로 개정: 후보 넷(1·2·3일 전 + 결측)을
  심고 상한 2로 좁혀 `openedOn` 집합을 대조한다. `ORDER BY … DESC` → `ASC` 변이 실측
  결과 `expected:<[2026-09-15, 2026-09-14]> but was:<[2026-09-13, 2026-09-14]>`로
  실패, 원복 뒤 재통과 확인(commands.md).
- **F-2(MEDIUM)** — `JdbcCompetitionSampleSource.resolveEligibilityPolicy`가
  `SAMPLE_ELIGIBILITY_POLICY.resolve(referenceDate)`를 쓴다(`.entries.single()` 제거) —
  형제 `resolveProvenancePolicy`와 같은 형태로 통일. entry가 하나 더 붙어도 `IllegalArgumentException`
  대신 정상 resolve된다.
- **F-3(MEDIUM)** — `candidatePair`의 `null`을 `SampleExclusionReason.CANDIDATE_VANISHED`
  로 계수(`aggregate`의 `recordOutcome` 분기). `JdbcCompetitionSampleSourceTest`「합계
  불변식」이 `VanishingNoticeRepository`(fake, `find`가 특정 id에 `null`을 내는 wrapper)
  로 스캔-복원 사이 소실을 흉내 내 `samples.size + excluded.values.sum() == 3`을 잰다.
  `Ports.kt`의 `SampleExclusionReason` KDoc도 「조인이 이미 보장」 서술을 정정했다.
- **F-4(MEDIUM, 게이트 술어)** — `SampleEligibility.reservePriceSequenceCheck`(신설) —
  `sequenceNumber` 집합이 정확히 `1..expectedReservePriceCount`가 아니면
  `RESERVE_PRICE_SEQUENCE_INVALID`로 실격. `reservePriceAmounts`도 입력 순서 대신
  `RESERVE_PRICE_SEQUENCE_ORDER`로 정렬해 위치-번호 결합을 명시적으로 만든다.
  `SampleEligibilityTest`에 순번 `002~016`(재현 케이스 그대로)·비정수 순번 두 test 추가.
- **F-11** — `predictionRequestFor`의 `competitionSamples` 기본값 `emptyList()` 제거 —
  호출부가 인자를 빠뜨리면 컴파일이 실패한다(유일한 호출부 `OpportunityAnalysis.predictionFacts`
  는 이미 `supply.samples`를 명시).
- **F-5~F-7(LOW, 등재분 일괄)** — `SampleEligibilityTest`에 16행(초과) case ·
  `JdbcCompetitionSampleSourceTest`에 `actual_opening_at == asOf`(제외)·창 하한 경계
  (포함) 두 test · `RequestMappingTest`에 예비가 `Money` 4성분(basis·currency·
  provenance·vat) 왕복 test 추가.
- **F-8·F-9·F-10(LOW, 장부)** — scope.md in_scope에 `MoneyMapping.kt`·`SampleConversion.kt`
  갱신 이력 반영은 팀장 소관 문서라 이 레인이 직접 편집하지 않는다(팀장이 in_scope 목록에
  이미 두 파일을 추가함, `git log` 확인). commands.md·rollback.md의 HEAD 재실측 행은
  이 라운드의 최종 commit에서 갱신한다(아래).
- **F-11 관련 참고** — D-4B7-9 문면과 구현(`absentPair(supply.reason)`)의 사유 정합은
  scope.md 소유라 이 레인이 판단하지 않는다(verifier 리포트도 "구현 쪽이 더 정직하다"로
  적었다) — 팀장이 scope.md를 갱신 중이므로 이 레인은 손대지 않는다.

## 알려진 제한

- `OPEN-4B7-QUERY-INDEX` — 인덱스 없음(D-4B7-6). `JdbcCompetitionSampleSource`가 후보마다
  `JdbcNoticeRepository.find`+`JdbcOpeningResultRepository.find`를 개별 호출하는 N+1 조회다.
- `OPEN-4B7-POLICY-VALUES` — `sampleWindowDays`(365)·`maxSamples`(500)·
  `expectedReservePriceCount`(15, 엔진과 1:1 확인됨)·D-4B7-8 provenance 임계 넷+순서(legacy
  값 그대로 이관, `decision` 모듈에 정본이 아직 없음) 전부 승인 대기(`policy-values.md`).
- `OPEN-4B7-TARGET-LABEL` — 대상 공고 요청 축 `baseAmountProvenanceLabel`은 여전히
  `Unknown`(이 slice는 표본 축만 라벨링한다, `PredictionFacts.kt` `predictionRequestFor`
  불변).
- `OPEN-4B7-CATEGORY-NORMALIZATION` — 「우회 (16) 실측」 절 참고.
- 기관 축(`agencyId`)은 표본·대상 공고 둘 다 `NOT_COLLECTED_YET`/`UNKNOWN`(`OPEN-2B-AGENCY-ID`,
  이 slice 범위 밖 — D-4B7-3에 이미 명시).
- 예비가격 basis(D-4B7-1)는 엔진 밴드(`CENTER_OUT_OF_BAND` 0.8~1.2)로 fail-closed — 이
  slice는 그 축을 직접 검증하지 않는다(엔진 쪽 관문).
- `SampleEligibilityTest`의 라벨 test 둘(Clean·SuspectRatio)만 `provenanceLabelFor`를
  재는데, `DerivedYega`·`DerivedVat`·`Unknown` 경로는 `ProvenanceRulesTest`(1D, 커널
  자체)가 이미 규칙표 전수를 재므로 이 slice에서 다시 재지 않았다(중복 금지).

## Codex 범위

`decision`·`adapters` 모두 되돌리기 어려운 경로(인증·인가·암호화·마이그레이션·데이터 파기)가
아니다 — Codex 심판 대상 아님(CLAUDE.md 운영자 지시 2026-09-11). `verifier`가 다음 레인이다.
