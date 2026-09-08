# M3/3B-2 — checklist.md

base `0e83d6e84a5279352aae1584e3849aa355a991c8` · head — 이 문서를 담은 커밋 자신(`git rev-parse HEAD`,
N-6 재발 방지 — 자기참조 SHA 를 문면에 박지 않는다, 3B checklist.md 관례). 정본 순서: `scope.md` →
`_workspace/m3-3b2/02_design-review.md` → 이 문서.

## 완료 조건 대응표 — scope.md 「이 slice 가 하는 일」

| # | 요구 | 구현 | test |
| --- | --- | --- | --- |
| ① | `fetchOpeningResults` 두 군(낙찰 목록·개찰결과 목록)을 인스턴스당 오퍼레이션 하나로, `sourceEndpoint` 로 군 구별 | `KonepsOpeningResultSource`(`listOperation`·`listSourceEndpoint` 생성자 인자) | `fetchOpeningResults 는 낙찰 목록 항목을 OPENING_AWARD_LIST sourceEndpoint 로 낸다` |
| ② | `fetchReservePrices(evidence: Fetch)` — 서명이 3A 조회 가치 술어 증거를 요구 | `OpeningResultSourcePort.fetchReservePrices` 서명 불변(3A 기존) + `fetchSingleKonepsNotice` 로 단건 조회 | `fetchReservePrices 는 evidence 의 noticeId 로 단건 조회하고 RESERVE_PRICE_DETAIL 로 낸다` |
| ③ | `fetchQualificationText` — license-limit, `inqryDiv=2`+`bidNtceNo`+`bidNtceOrd` | `KonepsLicenseLimitDocumentSource` + `KonepsOperationPolicy.LICENSE_LIMIT_DETAIL`(`requiresNoticeRound=true`) | `업종제한이 있으면 정확히 1회 조회하고 LICENSE_LIMIT_DETAIL 로 낸다`, `KonepsOperationDescriptorTest`(URI 단위 test) |
| ④ | 업종제한 플래그 `N` → 서브콜 0회 — `decideQualificationFetch` | `DetailFetch.kt`(`QualificationFetchDecision`) + `Ports.kt`(`DocumentSourcePort` 서명) | `PortsTest`(Skip 이면 호출 0회·Fetch 면 1회), `DecideQualificationFetchTest` |
| ⑤ | 오퍼레이션별 요청 정책 데이터(`inqryDiv` 표) | `KonepsOperationDescriptor` + `KonepsOperationPolicy`(AWARD_LIST·OPENING_RESULT_LIST·RESERVE_PRICE_DETAIL·LICENSE_LIMIT_DETAIL) + `buildKonepsOperationUri`(기본값·폴백 없음) | `KonepsOperationDescriptorTest`(7 test — 값별 URI·표→URI 회귀·구성 실패 3종) |
| ⑥ | 개찰 축 필드 계약 §1.7 authoritative 13행 채택(P-9, `bidwinnrBizno` 제외 — P-10) + 대문자 `FnlSucsfDate` 변형(verifier r1 F-5) | `CollectionPolicy.kt`(`KONEPS_OPENING_FIELD_ROWS`) | `CollectionPolicyTest`(등재 스물네 개·basis·WINNING_RATE 재사용·COUNT scale·presentIn·bidwinnrBizno 미등재·`fnlSucsfDate`/`FnlSucsfDate` 양쪽 등재) |
| ⑥b | 식별자 치환(P-10 (a)) — `bidwinnrNm` 원문 저장, `bidwinnrBizno`·대표자명 폐기, `opengCorpInfo` 부분 masking + fail-closed | `KonepsIdentifierMasking.kt`(`MaskedKonepsItem`·`maskOpengCorpInfo`·`mapMaskedOpeningItem`) | `KonepsIdentifierMaskingTest`(7 — 단일/다수/협상 세 변형 + allow-list + 식별자 부재 + `maskOpengCorpInfo` 단위 + `FnlSucsfDate` 생존 회귀) |
| ⑥c | 행 식별자 — 상세 오퍼레이션은 한 공고에 여러 행(verifier r1 F-1) | `KonepsOperationDescriptor.rowIdentifierRawKeys`(필수, 기본값 없음) + `NoticeIdentity.rowDiscriminator` | `F-1 — 예비가격 상세 복수예가 15행이 모두 살아남는다`·`F-1 — 제한그룹 3행이 모두 살아남는다` |
| ⑥d | 개찰 축 회계 — 계약 밖 키 제외·masking 실패를 서로 다른 축으로(verifier r1 F-3·F-8, 운영자 승인 2026-09-08 `Accounting.kt` 좁은 확장) | `MaskedKonepsItem.excludedFieldCount`(신설)·`decompositionFailures`(기존) 분리, `RawItemOutcome.Mapped.maskingFailureCount`(신설, 기본값 0) + `CollectionAccounting.maskingFailures`(신설, 기본값 0, `dropReasons`/`dropped` 항등식 밖) | `AccountingTest`(2, 항등식 무관·음수 거부)·`KonepsIdentifierMaskingTest`(unknownFieldCount/maskingFailureCount 분리 3건)·`KonepsOpeningResultSourceTest`(`F-3·F-8 — …` 종단 test) |
| ⑦ | contract mock server test — 세 어휘(제한 없음/03/08)·`bidNtceOrd` 필수·`inqryDiv` 회귀·복수예가 15행·차수 없는 1건 drop·429/22 bounded retry | `KonepsOpeningResultSourceTest`·`KonepsLicenseLimitDocumentSourceTest`·`KonepsOperationDescriptorTest` | 아래 시나리오 대응표 |

## 시나리오 대응표 — scope.md ⑦

| 시나리오 | test | 결과 |
| --- | --- | --- |
| 낙찰 목록 정상 fetch, OPENING_AWARD_LIST 로 관측 | `KonepsOpeningResultSourceTest` | PASS |
| 예비가격 상세 단건 조회(1행), RESERVE_PRICE_DETAIL 로 관측 | `KonepsOpeningResultSourceTest` | PASS |
| **예비가격 상세 복수예가 15행(`compnoRsrvtnPrceSno` 1~15, 같은 공고) — 15행 전부 살아남음, duplicate=0**(verifier r1 F-1 재현 test) | `F-1 — 예비가격 상세 복수예가 15행이 모두 살아남는다` | PASS |
| **license-limit 제한그룹 3행(`lmtGrpNo`+`lmtSno` 축, 같은 공고) — 3행 전부 살아남음**(F-1) | `F-1 — 제한그룹 3행이 모두 살아남는다` | PASS |
| **차수 없는 1건이 목록 정상 N건 사이에서 port 수준 drop**(verifier r1 F-2) | `F-2 — 차수 없는 1건이 …` | PASS(3건 중 1건 drop) |
| **429/`22` bounded retry, 신규 두 port 각각**(F-2) | `F-2 — 429 연속 실패 뒤 성공` · `F-2 — resultCode 22 는 quota 초과로 재시도 대상이다` | PASS |
| 제한 없음(`00`+`totalCount=0`) → 성공+빈 항목 | `KonepsOpeningResultSourceTest`·`KonepsLicenseLimitDocumentSourceTest` 각 1건 | PASS |
| `03` → 데이터 없음 | 같음 | PASS |
| `08`(필수값 입력 에러, `bidNtceOrd` 누락 시나리오) → `InputError`, 비재시도, 호출 1회 | `KonepsLicenseLimitDocumentSourceTest` | PASS |
| 미지 resultCode → 비재시도(3B 공통 기반 재사용 확인) | `KonepsOpeningResultSourceTest` | PASS |
| COL-07(license-limit 미등재 응답 키) → unknownFields 계수, masking 없음 | `KonepsLicenseLimitDocumentSourceTest` | PASS |
| 외자 대문자 `FnlSucsfDate` → 관측에 생존(verifier r1 F-5, PROBE2 재현) | `KonepsIdentifierMaskingTest` | PASS |
| opengCorpInfo 단일 낙찰자(5성분) → masking 성공, unknownFieldCount=0·maskingFailureCount=0 | `KonepsIdentifierMaskingTest` | PASS |
| opengCorpInfo 「낙찰예정자 다수」(3성분, 자리 뜻 다름) → 값 전체 폐기, maskingFailureCount=1(unknownFieldCount 는 0) | 같음 | PASS |
| opengCorpInfo 협상 계약(3성분, 투찰금액·투찰율 없음) → 값 전체 폐기, maskingFailureCount=1 | 같음 | PASS |
| allow-list — 계약 없는 식별자 키가 fields·sourceText 둘 다에 없고 unknownFieldCount=1 로 계수(verifier r1 F-3) | 같음 | PASS |
| **계약 밖 키 제외와 masking 실패가 SourceBatch 회계에서 서로 다른 축(`unknownFields`·`maskingFailures`)으로 나온다**(F-3·F-8 종단, endpoint 분리 후 재정리) | `F-3 — …`·`F-8 — …` | PASS |
| **`presentIn` 강제 — 낙찰 목록 엔드포인트로 개찰결과 전용 필드(opengCorpInfo)를 받으면 제외된다**(verifier r1 F-6 재검토) | `F-6 — 낙찰 목록 엔드포인트로 …` | PASS |
| **`bssamt`(presentIn 확장분)가 예비가격 상세 관측에도 남는다**(F-6) | `F-6 — bssamt 는 …` | PASS |
| `inqryDiv` 값별 URI(AWARD_LIST/RESERVE_PRICE_DETAIL/LICENSE_LIMIT_DETAIL) + 표→URI 회귀 | `KonepsOperationDescriptorTest` | PASS |
| 서술 구성 실패(기간창+단건 동시 요구·bidNtceOrd 요구인데 bidNtceNo 없음·인자 불일치) | 같음 | PASS |

## 위협 모델 대응표 — scope.md 「방어한다」

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 술어 우회 | `fetchReservePrices`·`fetchQualificationText` 서명이 internal-constructor 증거 값만 받는다(3A 기존 + D-3B2-5 신규) | `PortsTest`, `KonepsOpeningResultSourceTest`(evidence 필수) |
| (b) `inqryDiv` 오용 | `KonepsOperationDescriptor` 필수 생성자 인자 + 기본값·폴백 없음 + 표→URI 회귀 test | `KonepsOperationDescriptorTest` |
| (c) 사업자 식별자 유입 | allow-list 반전(`MaskedKonepsItem.from`) — 미등재 키 자동 제외 + `excludedFieldCount`로 계수(F-3) | `KonepsIdentifierMaskingTest`(allow-list test) |
| (d) `08`·`03`·`00+totalCount=0` 혼동 | 3A `resultCodeCategories` 표(변경 없음, 재사용) + 세 회계 어휘 test | `KonepsOpeningResultSourceTest`·`KonepsLicenseLimitDocumentSourceTest` |
| (e) 3B 공통 기반 회귀 | `KonepsOpenApiNoticeSourceTest`(20)·`KonepsAdapterDependencyTest`(1)·`ServiceKeyTest` 편집 없이 green | commands.md S-2 |
| (f) 서비스 키 노출 | `ServiceKey` 재사용(3B 기존, 미변경) | 3B `ServiceKeyTest`(변경 없음) |
| (g) mapper 선택·기본값 누출(verifier r1 F-4) | `walkKonepsNoticePages`의 `itemMapper` 기본값 제거 — 세 호출부(3B `fetchNotices`·`fetchOpeningResults`·`fetchSingleKonepsNotice`) 모두 명시. 개찰 축 endpoint 로 `mapRawItem`(원문 보존)을 부르는 경로 자체는 여전히 컴파일된다 — 이 수정이 닫는 것은 「인자를 잊으면 조용히 새는」 폴백이지 「호출부가 잘못된 mapper 를 의도적으로 고르는」 실수가 아니다(장부에 남김) | 3B-2 호출부 3곳 실측(main 3·기본값 0) |
| (h) 복수행 상세가 duplicate 로 잘못 접힘(verifier r1 F-1) | `KonepsOperationDescriptor.rowIdentifierRawKeys`(기본값 없음) + `NoticeIdentity.rowDiscriminator` | 15행·3행 회귀 test(시나리오 대응표) |
| (i) 필드 단위 실패 두 축이 하나로 접혀 §5.3 규율 1 이 무력화(verifier r1 F-3·F-8) | `Accounting.kt` 좁은 확장(운영자 승인) — `unknownFields`는 이름 그대로 계약 밖 키만, 신설 `maskingFailures`가 masking 실패만. `dropReasons`/`dropped` 항등식 밖(필드 제외는 항목 drop 이 아니다) | `AccountingTest`·`KonepsIdentifierMaskingTest`·`KonepsOpeningResultSourceTest`(F-3·F-8 종단) |
| (j) `presentIn`(P-9 ④ 오퍼레이션 군 구별)이 서류로만 남고 하중을 안 짐(verifier r1 F-6 재검토) | `fieldOutcomeOf`가 `contract.presentIn.contains(sourceEndpoint)`를 대조 — 불일치는 `Excluded`(F-3 축으로 계수). 전제로 `bidNtceNo`·`bidNtceOrd`·`bssamt`의 `presentIn`을 문서·실제 사용에 맞게 넓혔다(둘 다 안 넓히면 강제가 identity 필드부터 모든 개찰 축 항목을 「공고번호 없음」으로 오분류한다 — 실측으로 확인) | `CollectionPolicyTest`(bidNtceNo/bidNtceOrd/bssamt presentIn 3건) · `KonepsOpeningResultSourceTest`(`F-6 —` 2건, endpoint 불일치 제외 + bssamt 생존) |

## 우회 후보 대응표 — scope.md 「우회 후보(≥5)」

| # | 우회 | 막는 것 |
| --- | --- | --- |
| 1 | 4B 가 `Fetch` 없이 상세를 부름 | `fetchReservePrices`·`fetchQualificationText` 서명(컴파일 차단) |
| 2 | 목록군에 `inqryDiv=2`(공고번호축)를 문서와 다르게 씀 | `KonepsOperationPolicy` 정책 표 + URI 회귀 test |
| 3 | `bidwinnrNm`을 `sourceText`에 원문 substring 으로 저장 | `sourceText`가 걸러진 `JsonObject.render()`(설계 검토 게이트 ① (2)) |
| 4 | 「제한 없음」을 실패로 회계 | 3A `NO_DATA`/성공 분류(재사용) + 어휘 test |
| 5 | `08`을 재시도 | 3A `INPUT_ERROR`(비재시도, 재사용) + `resultCode 08` test |
| 6 | 3B walker 시그니처를 바꿔 3B test 를 고침 | `itemMapper` 매개변수는 필수(기본값 없음, verifier r1 F-4 수정)이고 3B 호출부(`KonepsOpenApiNoticeSource.fetchNotices`)가 `defaultKonepsItemMapper`를 명시로 참조 — 3B test 는 편집 없이 green(commands.md S-2 실측) |
| 7 | 상세 오퍼레이션의 복수 행이 dedup 식별자 충돌로 뭉개짐(verifier r1 F-1) | `KonepsOperationDescriptor.rowIdentifierRawKeys`(기본값 없음, 새 오퍼레이션 추가 시 반드시 선언) |

## 판단이 갈린 지점 — 계약이 명시하지 않아 이 레인이 정한 것

1. **`sucsfbidRate`는 새 `FieldConcept` 대신 기존 `WINNING_RATE`를 재사용한다.** 설계 검토 문서의
   ① 목록에는 없었으나(그 목록이 12개 개념 중 「최종낙찰업체명」을 빠뜨린 것과 같은 누락), 3A 에
   이미 있던 미사용 토큰이 정확히 이 개념에 맞아 2026-09-01 규칙(어휘를 지어내지 않는다)에 따라
   재사용했다.
2. **`fnlSucsfDate`(최종낙찰일자, 일자만·시각 없음)는 `DATETIME_NO_ZONE`이 아니라 `OPAQUE_TEXT`로
   등재했다.** `DATETIME_NO_ZONE`은 `sourceZone` 필수 짝을 강제하는데 이 필드는 시각 축이 아니라
   짝을 지을 자리가 없다. 새 스케일 토큰(날짜 전용 축)을 만드는 대신 원문 보존만 하는 `OPAQUE_TEXT`
   로 좁혔다(canonicalize 는 D-3B2-8 후속, 이 slice 밖).
3. **`opengCorpInfo` masking 실패와 allow-list 제외는 `dropReasons`/`dropped` 항등식 밖에 별도
   필드로 세운다(운영자 승인 2026-09-08, `Accounting.kt` 좁은 확장, F-3·F-8 해소).** 필드 단위
   실패를 항목 단위 drop 사유(`CollectionDropReason`)에 억지로 넣지 않았다 — 항목은 살아남고
   그 안의 일부 필드만 빠지는 것이라 「몇 항목이 왔는가」(`dropReasons` 합=`dropped`)와 다른
   축이다. `unknownFields`는 이름 그대로 계약 밖 키만(F-3), 신설 `maskingFailures`가 masking
   실패만(F-8) 센다 — 이전 판(P-9 승인 시점)은 procurement in_scope 가 여섯 파일로 고정돼
   있어 이 둘을 어댑터 `unknownFieldCount` 한 슬롯에 접었으나, 운영자가 `Accounting.kt`·
   `AccountingTest.kt` 를 in_scope 예외로 승인해 바른 자리로 옮겼다.
4. **license-limit 은 masking 하지 않고 3B `mapRawItem`을 그대로 쓴다.** §1.9.5 응답 필드가 사업자·
   개인 식별자가 아니라 P-10 (a) 대상이 아니다 — 개찰 축 전용 `MaskedKonepsItem`을 이 오퍼레이션에
   억지로 씌우면 과잉이다(설계 검토 (3)).
5. **단건 조회(`fetchReservePrices`·`fetchQualificationText`)의 조회 기준일은 port 서명에 없어
   `clock` 기준 오늘 날짜로 정책을 해석한다.** `KONEPS_COLLECTION_POLICY`가 아직 단일 `EffectiveFrom
   .Initial` 항목뿐이라 어느 날짜로 해석하든 값이 같다 — 날짜 의존 정책이 생기면 이 자리를
   재검토해야 한다(`KonepsSourceConfig.kt` KDoc 에 명시).
6. **3B-2 신규 어댑터 두 클래스는 `KonepsSourceConfig` 값 객체로 생성자를 묶는다.** 애초 3B
   `KonepsOpenApiNoticeSource`와 같은 평탄한 인자 나열이었으나 그 클래스와 정확히 같은 7개 인자
   순서가 CPD 중복으로 잡혔다(`adapters:cpdCheck` 실패). `KonepsOpenApiNoticeSource`는 3B 기존
   test 계약이라 고치지 않고, 3B-2 신규 클래스 쪽만 값 객체로 묶어 중복을 없앴다 — 두 신규 클래스가
   서로 겹치는 단건 조회 배선(`fetchReservePrices`·`fetchQualificationText`)도 `fetchSingleKonepsNotice`
   공통 함수로 뽑아 같은 이유로 닫았다(2차 CPD 재발, `KonepsSourceConfig.kt`).
7. **개찰 축은 낙찰 목록 검색·개찰결과 목록 검색(`…PPSSrch`, §1.9.1 16~23)을 별도 `SourceEndpoint`로
   열지 않는다.** legacy 도 이 검색군을 부르지 않고(§1.9.1), 3B-2 acceptance 도 그 군을 요구하지
   않는다 — `sucsfbidAmt` 등 필드의 `presentIn`은 목록군 하나로만 좁혔다(과잉 금지).

## 알려진 제한

- **`getOpengResultListInfoOpengCompt`(개찰완료) 미구현**(D-3B2-9 (a) 운영자 승인) — 추첨번호
  (`drwtNo1`·`drwtNo2`)와 투찰 축(`bidprcAmt`·`bidprcrt`)은 이 오퍼레이션만 준다. `COL-03` 문면의
  「추첨번호 확보」가 이 slice 로 닫히지 않는다 — 후속 slice 대상(M4 4B 착수 전).
- **표적조회(`getBidPblancListInfo*`+`inqryDiv=2`) 미구현**(D-3B2-6 (a)) — 자격 원문 서브콜은
  license-limit 하나로 충분하다는 판단(공고 목록 관측이 플래그·차수를 이미 나른다).
- **(해소, F-3·F-8) `opengCorpInfo` masking 실패와 allow-list 제외가 이제 서로 다른 축**
  (`CollectionAccounting.unknownFields`/`maskingFailures`)으로 나온다 — 판단 3번. 남는 제한:
  두 축 다 **키/성분 단위**만 세고 **어느 raw 키가 제외됐는지**(이름 목록)는 회계에 없다 —
  §5.3 규율 1 의 「무엇이 빠졌는가」는 이 축이 수를 낼 뿐, 이름까지 필요하면 `sourceText`
  (걸러진 렌더)와 원본을 대조하는 별도 감사가 필요하다(이 slice 는 「몇 개 빠졌는가」까지만).
- **(해소, F-6 재검토) `presentIn` 은 이제 개찰 축 allow-list 경로에서 강제된다.** 직전 라운드의
  「강제하지 않기로 결정」은 그 강제가 깨는 것(승인 문면이 넓히라고 지시한 `bssamt`의 미확장)이
  원인이었지 강제 자체의 문제가 아니었다(verifier r1 지적 — 근거가 순환이었다). `bssamt`·
  `bidNtceNo`·`bidNtceOrd` 의 `presentIn`을 §1.7.1 각주·실제 사용에 맞게 넓힌 뒤(`bidNtceNo`·
  `bidNtceOrd`는 식별자라 세 엔드포인트 모두에 실린다 — 넓히지 않으면 강제가 식별자부터
  떨어뜨려 모든 개찰 축 항목이 「공고번호 없음」으로 오분류된다, 실측으로 확인) 강제를 걸었다.
  강제는 `fieldOutcomeOf`에서 `presentIn` 불일치를 `Excluded`(F-3 축)로 접는다 — P-9 ④가
  승인한 오퍼레이션 군 구별이 이제 실제로 하중을 진다.
- **실제 KONEPS 호출·오퍼레이션 경로·파라미터 이름의 실물 일치는 검증하지 않는다**(out_of_scope,
  실제 호출 승인 뒤).
- **낙찰 목록 검색·개찰결과 목록 검색(`…PPSSrch`) 미구현** — legacy 미소비, 판단 7번.
- **개찰결과 목록의 문서상 필수 키 둘이 개찰 축 계약에 없어 `presentIn` 강제로 제외·계수된다**
  (verifier r2 L-1) — `opengDt`·`rsrvtnPrceFileExistnceYn`(§1.7.4 가 「두 선언을 한 행에 접지
  않는다」로 공고 축 `opengDt` 행과의 병합을 금지, P-9 채택 13행에도 없다). 미탐이 아니라
  §1.7.4 의 정상 결과다 — `unknownFields` 로 계수돼 조용히 사라지지 않는다. 예비가격 상세의
  `totRsrvtnPrceNum`·`drwtNum`(신설 후보 필드, 문서에는 있으나 P-9 채택분 밖)도 같은 사유로
  같은 축에 계수된다.
- **`duplicate` 로 판정된 항목의 `unknownFields`·`maskingFailures` 는 세지 않는다**(verifier r2
  L-2, 3B 기존 누적기 거동 — 이 slice 가 만들지 않았다) — 중복으로 접힌 둘째 이후 행의 필드
  단위 결함은 회계에 나타나지 않는다. `normalized` 항목만 그 두 축을 낸다.
- **(판단, verifier r2 G-2) 개찰 축 엔드포인트로 원문 보존 mapper([mapRawItem])를 명시적으로
  부르는 호출부는 여전히 컴파일된다.** `itemMapper` 기본값 제거(F-4)가 닫은 것은 「인자를
  잊으면 조용히 새는」 폴백이지 「호출부가 [mapMaskedOpeningItem] 대신 [mapRawItem]을 의도적으로
  고르는」 실수가 아니다. 타입으로 막으려면 `SourceEndpoint`(공유 열거형, 3A·3B·전 procurement
  소비처가 참조)를 두 하위 체계(마스킹 필수/불필요)로 쪼개야 하는데, 이는 in_scope 「추가만」
  원칙을 넘는 광범위 개편이고 이 slice 가 다루는 세 호출부(`fetchNotices`·license-limit·개찰
  목록/상세)는 이미 각자 올바른 mapper 를 쓴다(실측: 전 main 소스 grep). 위협 모델이 저자
  실수가 아니라 우회를 방어 대상으로 삼으므로, 비용 대비 이 우회는 등재로 충분하다고 판단했다
  — 표 (g) 행에 이미 이 경계가 적혀 있다.

## 병렬 레인 경계 확인

`git status --porcelain` 최종 상태(commands.md clean-tree 절) — in_scope 열 개 경로만 변경.
`fixtures/**`·`docs/**`·`milestone-3.md`·`policy-values.md`·다른 procurement 파일·다른 세션의
untracked 파일 없음.

## 스테이징 규율

전 커밋이 `git add <in_scope 경로 개별 인자>`(`-A`·`-a`·`.` 미사용) 뒤 `git commit`(같은 명령 안에서
add+commit 분리 없음 — parallel-lane 오염 회피). 커밋 전 `git diff --cached --name-status`로 in_scope
대조(commands.md 에 남기지 않음 — 커밋 메시지 자체가 diff stat 를 갖는다).

## 역방향 파급 검사 — 이 slice 가 편집한 코드 파일

`grep -rn '<파일명>:[0-9]' --include='*.md'`로 `FieldContract`·`RawObservation`·`CollectionPolicy`·
`DetailFetch`·`Ports`·`KonepsPageUriBuilder`·`KonepsRawItemMapper`·`gate-tests.properties`·
`Accounting`·`AccountingTest`(F-3·F-8 라운드 추가) 열 전부를 조회 — **매치 0건**. 이 slice 가
줄을 넣은 코드 파일을 `file:line`으로 인용하는 문서가 없다(코드 파일은 evidence·설계 문서가
보통 심볼·KDoc 앵커로 가리키지 줄 번호로 가리키지 않는다).

## 판정 로직 변경 커밋 — 표적 재검증 대상(evidence-pack 2026-09-04 예외)

행 식별자(⑥c)·mapper 기본값 제거(위협 모델 (g))·`FnlSucsfDate` 등재(⑥)·`presentIn` 강제
결정(위협 모델 (j), 「알려진 제한」 — 직전 라운드의 비강제 결정을 뒤집었다)·gate-tests 등재
(완료 조건 대응표)·개찰 축 회계 두 축 분리(⑥d, 위협 모델 (i))·**dedup 부재값 처리(verifier r2
G-1 — `rowDiscriminatorOf`가 부재·공백을 `""`가 아니라 `null`로 내고 `identityOf`가 그 항목을
dedup 대상에서 제외)**·**license-limit 식별자 계약 보강(verifier r2 G-4 — `bidNtceNo`·
`bidNtceOrd` 의 `presentIn`에 `LICENSE_LIMIT_DETAIL` 추가 + `lmtGrpNo`·`lmtSno` 계약 행 신설)**
까지 여덟이 판정 로직·게이트 구성을 바꾼 커밋이다 — 심각도 무관 표적 재검증 대상이다. 각 항목의
회귀 test 는 시나리오 대응표·완료 조건 대응표에 이미 등재돼 있다 — 별도 절로 반복하지 않는다.

## 완료 조건

acceptance S-0~S-4 전부 통과(commands.md), 3A corpus 27/27 불변(`:procurement:test` 는 fixture
corpus 를 직접 재실행하지 않지만 `fixtures/**`가 out_of_scope 라 편집되지 않았다 — corpus 자체는
3A/3C 소관 test 가 잰다), 3B 기존 test 편집 없이 green.
