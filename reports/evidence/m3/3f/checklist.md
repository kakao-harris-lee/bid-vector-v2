# M3/3F — checklist.md

## 「이 slice가 하는 일」①~⑦ 대응표

| # | 일 | 닫은 자리 |
| --- | --- | --- |
| ① | 개찰완료 오퍼레이션 구현(`inqryDiv` 없는 오퍼레이션군) | `KonepsOperationDescriptor.inquiryDivValue` nullable화 + `KonepsOperationPolicy.OPENING_COMPLETE`(`bidNtceNo`+`bidNtceOrd` 단건, rowIdentifier=`prcbdrNm`). `buildKonepsOperationUri` 는 null 이면 `inqryDiv` 파라미터 자체를 안 낸다 |
| ② | 투찰자별 행은 raw_observation 까지, canonical 은 셋만 | `OpeningRankOneOutcome`(NotObserved·RankMissing·RankDuplicated·Determined) — `resolve()` 가 순위 1 부재·중복을 명시적으로 회계, 투찰금액 재계산 없음. `DrawNumberObservation`(NotObserved·Verified·OutOfRange·RangeCheckUnavailable) — `of()` 가 범위 검사·검사 불가를 명시적 결과로 |
| ③ | P-10 (a) 확장 — `prcbdrBizno`·`prcbdrCeoNm` 미보존 | P-13 (a) 승인 뒤 계약 레지스트리 경로로 통일 — `KonepsOpeningCompleteFieldContracts.kt`(§1.11 10행)에 그 둘이 등재되지 않아 `mapMaskedOpeningItem`의 allow-list 반전이 구조적으로 제외한다(어댑터 전용 masking 함수를 새로 만들지 않는다) |
| ④ | 추첨번호 1-기반 인덱스 + 범위 검사 | `DrawNumberObservation.of(numbers, totalReservePriceCandidateCount)` — 3E 부모 슬롯을 재사용, 별도 저장 없이 `OutOfRange.validRange` 를 읽기에서 재구성 |
| ⑤ | 조회 가치 술어 재사용 | `fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch)` — D-3F-2, 새 결정 타입 없음 |
| ⑥ | 저장(V5, 부모 컬럼만) | `opening_rank_one_*` 6컬럼 + `draw_numbers_*` 2컬럼, CHECK 7, 가드 트리거 2(그룹 단위). `OpeningCompleteAxisCodec.kt` 가 bind/read 를 묶어 detekt TooManyFunctions 회피 |
| ⑦ | mock server 시나리오 | `KonepsOpeningCompleteSourceTest.kt`(sizeGate 분리) — 정상 다수 행·단일 낙찰자·협상 계약·추첨번호 부재·순위 동값·치환 세 변형(bizno·ceoName·둘 다)·`bidNtceNo` 누락 08 비재시도 |

## D-3F-7·P-13 (a) — scope.md 중간 개정 반영

착수 뒤 구현 레인의 조사가 「`KonepsFieldContract` 생성자가 procurement `internal`이라
계약 없이 어댑터가 값을 꺼낼 경로가 구조적으로 없다」를 드러내 보고했다. 그 보고가
문서 레인의 재조사를 촉발했고, 문서 레인은 한 겹 더 깊은 문제(「파일 권한이 아니라
승인된 필드 계약 자체(§1.7)가 이 오퍼레이션엔 없다」)를 찾아 §1.11 표를 새로 쓰고
P-13 (a)를 승인, scope.md in_scope 를 `CollectionPolicy.kt`·`FieldContract.kt`·
`RawObservation.kt`(추가만)로 넓혔다(`bbbae9c`).

**판단**: 어댑터 하드코딩 allow-list(자체 `MaskedOpeningCompleteItem`)를 검토했으나, 계약
레지스트리가 allow-list·미지 필드 회계(§5.3 규율 1)의 정본이고 필드 수준 정책을 어댑터
상수 테이블에 박지 않는다는 규율(v2-지침서 §5)에 따라 계약 등재(`mapMaskedOpeningItem`
재사용)로 갔다(P-13 승인, in_scope 정정 `bbbae9c`). 평가점수 넷은 P-13 이 「제외」로
결정해 `OpeningRankOneBid`·V5 컬럼에서 뺐다 — D-3F-5 (a)의 「수집·보존」은 이 축에서 이
slice 범위 밖으로 좁혀진다(계약이 열리는 후속 slice 대상).

## 우회 여덟 — 무엇이 막는가

| # | 우회 | 막는 것 | 형태 |
| --- | --- | --- | --- |
| 1 | 원문 item 을 mapper 에 직접 넘김 | `mapMaskedOpeningItem`(`MaskedKonepsItem` 통로) — 원문 `JsonValue.JsonObject` 를 받지 않는다 | 컴파일 |
| 2 | `sourceText` 에 원문 substring | `MaskedKonepsItem.from` 이 걸러진 값의 `render()` 만 쓴다 + 문자열 검색 test(치환 세 변형) | 컴파일+test |
| 3 | 추첨번호 부재를 0 으로 | `DrawNumberObservation.of` 가 빈 집합만 `NotObserved` 를 내고 0 을 지어내는 경로가 없다 + 부재 test | 타입+test |
| 4 | 검사 불가를 조용한 통과로 | `RangeCheckUnavailable` 이 `Verified` 와 다른 sealed 분기 — 소비 `when` 이 소진돼야 컴파일된다 + test | 타입+test |
| 5 | 협상 계약 부재 금액을 0 으로 | `ObservedBidAmount` 는 `null` 로만 부재를 나른다(0 생성 경로 없음) + 3/15 형태 test | 타입+test |
| 6 | 술어 없이 상세 호출 | `fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch)` — `Fetch` 는 `decideDetailFetch` 만 만든다 | 컴파일 |
| 7 | 사업자등록번호 컬럼을 나중에 | V5 스키마에 컬럼 없음 + 3E `OpeningReservePriceRepositoryTest`의 기존 컬럼 전체 스캔 test 가 이 표 신규 컬럼도 자동으로 겸한다(편집 없음) | 스키마+기존 test |
| 8 | 순위 1 을 투찰금액으로 재계산 | `OpeningRankOneOutcome.resolve` 는 `rank == 1` 필터만 하고 금액 비교 분기가 없다 — `RankMissing`/`RankDuplicated` test 가 재계산 부재를 고정 | test |

## 판단이 갈린 지점

1. **`SourceEndpoint.OPENING_COMPLETE` 신설 대 기존 `OPENING_RESULT` 재사용** — 1차 구현은
   미사용이던 `OPENING_RESULT` 값을 재사용했으나, P-13 승인이 명시적으로 「넷째 군」을
   요구해 신설로 바꿨다(`OPENING_RESULT` 는 계속 미사용으로 남는다, 기존 상태 불변).
2. **`opengRsltDivNm` 을 기존 `PROGRESS_DIVISION` 개념에 얹음** — §1.11 이 새 raw 키를
   주지만 3E의 `progrsDivCdNm`(개찰결과 목록 축)과 같은 의미(진행/결과구분 라벨)로 판단해
   새 `FieldConcept` 토큰을 만들지 않았다(`finalAwardDateRow`가 세운 「다른 raw 키, 같은
   개념」 전례). 이 판단이 D-3F-4 ③(개찰결과구분명)을 `OpeningResult.progressDivision`
   재사용만으로 닫는다 — 새 procurement 필드가 없다.
3. **행 dedup 키로 `prcbdrNm`(상호)을 쓴다** — `opengRank`가 실측(§1.9.7)에서 4/15만 전
   행 유일해 자연 키로 못 쓴다. 상호는 표본에서 15/15 유일했으나(설계 검토 (1)) 그 관측이
   좁은 표본(참가업체수 상위 편향)이라, 실제 동명 투찰자가 있으면 뒤 행이 SourceBatch
   dedup 회계에서 `duplicate`로 잘못 집힐 잔여 위험을 감수한다 — canonical 정체성이 아니라
   raw 배치 내 dedup 회계 전용이라는 것을 KDoc에 명시했다(D-3F-3 가 canonical 승격 자체를
   없앴으므로 이 위험이 저장 손실로 번지지 않는다).
4. **`bidprcDt` 의 `sourceZone`을 `ASSUME_KST`로** — §1.11 이 zone 을 미확정으로 남기지만
   3A P-2 가 이미 다른 `DATETIME_NO_ZONE` 필드(`bidClseDt`·`opengDt`·`rlOpengDt`)에 같은
   초기값을 쓴 전례를 그대로 따랐다(새 미확정을 지어내지 않는다).
5. **`requiresNoticeRound = true`(bidNtceOrd 전송)** — §1.11 은 옵션이라 적지만, 한
   공고번호에 차수가 여럿일 수 있어 좁혀 보내는 편이 안전하다고 판단했다(문서 밖 구현
   판단, 대안 데이터 없음).
6. **Sql.kt 플레이스홀더 불일치 회귀** — P-13 반영 라운드에서 `OpeningCompleteAxisCodec.kt`
   의 bind/read 함수만 고치고 `Sql.kt`의 INSERT 컬럼·`VALUES` 플레이스홀더 수를 먼저 안
   맞춰 `매개 변수 35 에 대해 지정된 값이 없습니다` PSQLException 이 텄다(3D·3E 기존
   test까지 실패로 번짐). Sql.kt 를 함께 고쳐 해소 — 세 자리(바인딩·SELECT 열·INSERT 열)가
   항상 같은 컬럼 집합을 가리켜야 한다는 교훈, 별도 test 신설은 하지 않았다(기존 Testcontainers
   test 스위트 전체가 이미 그 불일치를 잡는다).

## 알려진 제한

- **평가점수 넷 미보존**(P-13 「제외」) — `bidPrceEvlVal`·`techEvlVal`·`techEvlNaturVal`·
  `totalEvlAmtVal`은 scale 미확정이라 계약이 없고, `raw_observation`에도 안 남는다(계약
  없이는 masking allow-list 를 못 넘는다). D-3F-5 (a)의 「수집·보존」은 이 축에서 실질적으로
  보류된다 — scale 이 확정되는 후속 slice 대상(신설 후보 `OPEN-3F-EVALUATION-SCORE-SCALE`).
- **추첨 귀속 미보존**(운영자 도메인 결정, D-3F-3) — 투찰자별 원문은 `raw_observation`
  감사 기록까지, 「누가 어느 번호를 골랐는가」는 저장하지 않는다. 소급 승격 가능(원문이
  남아 있다).
- **순위 1 부재·중복 시 개찰 1위 축 부재** — `RankMissing`·`RankDuplicated` 는 값을 비운
  명시적 회계이지 대체값을 만들지 않는다.
- **추첨번호 범위 검사 불가 경우** — 총예가건수를 모르는 공고는 `RangeCheckUnavailable`로
  남는다(3E 부모 슬롯 값이 없는 공고).
- **운영 배선 부재** — 실제 KONEPS 호출·M4 4B workflow 연결은 out_of_scope. `OpeningRankOneOutcome`·
  `DrawNumberObservation`을 raw 관측에서 실제로 구성하는 canonicalize 함수는 이 slice에
  없다(부모 fact 슬롯만 준비, 3E `reservePrices`와 같은 「능력 수준」 완료).
- **상호 dedup 키의 동명 위험** — 위 「판단이 갈린 지점」 3 참고, canonical 손실로 번지지
  않지만 raw 배치 회계(`duplicate` 카운트)에 잡음이 생길 수 있다.
