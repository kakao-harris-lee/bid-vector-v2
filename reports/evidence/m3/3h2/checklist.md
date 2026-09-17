# M3/3H-2 checklist — 요청·표본 축 agencyId 조립 + 표본 축 결측 사유 통일

## D-3H2-1~4 근거(함수·test 이름)

| 결정 | 구현 | test |
| --- | --- | --- |
| D-3H2-1 — 요청·표본 축 `agencyId` = `notice.demandAgency?.code`의 값(`AgencyId(code.value)`). 공고기관 폴백 없음, 이름을 키로 쓰지 않음 | `predictionRequestFor`(`PredictionFacts.kt`), `sampleOf`(`SampleConversion.kt`) — 둘 다 같은 한 줄 표현식 | `PredictionFactsTest`·`SampleEligibilityTest`의 「수요기관 코드가 있으면 … 그 코드 값이다」·「수요기관이 없고 공고기관만 있으면 … null이다」·「이름만 있고 코드가 없으면 … null이다」 세 케이스씩. `OpportunityAnalysisTest`「notice 의 수요기관 코드가 analyze() 경유로도 요청 agencyId 에 그대로 실린다(D-3H2-1)」(배선 확인) |
| D-3H2-2 — 표본 축 `agency_id` 결측 사유를 `NOT_COLLECTED_YET`에서 요청 축과 같은 `UNKNOWN`으로. `categoryCode` 표본 축은 무변경 | `toSampleAgencyIdFact`(`RequestMapping.kt`)가 `toAgencyIdFact`에 위임 | `RequestMappingTest`「표본 agencyId 가 null 이면 wire 는 MISSING_REASON_UNKNOWN 이다 — NOT_COLLECTED_YET 이 아니다」·「표본 agencyId 값이 있으면 그 값 그대로 왕복한다」 |
| D-3H2-3 — 엔진 표본 축 허용 결측 사유 집합을 `{NOT_COLLECTED_YET, UNKNOWN}`으로(`ml-implementer` 레인, 이 문서 소관 아님) | `_is_segment_missing_reason_allowed`(`distribution.py`) | commands.md의 D-3H2-3 절(RED→GREEN, 전건 956 passed) |
| D-3H2-4 — 엔진 교차 실측: 요청·표본이 같은 코드를 실은 `mapRequest` 산출 바이트를 `serve_bid_rates`에 직접 통과시켜 `segment_support = DIRECT`·`agency_sample_count ≥ 1`을 관측 | 일회성 probe(커밋 없음) — `mapRequest`가 실제로 만드는 wire 바이트를 그대로 Python에 넘긴다(손으로 지은 proto 아님) | commands.md의 D-3H2-4 절 — `DIRECT`·`agency_sample_count=8`·`shrinkage_weight=0.4` 관측 + `UNKNOWN` 표본 혼입 시 `excluded_observations=0` |

## 위협 모델 우회 대응표(scope.md 원본 기준)

| # | 우회 | 닫힘 |
| --- | --- | --- |
| (1) | `predictionRequestFor`/`sampleOf`가 `noticeAgency`로 폴백 | `PredictionFactsTest`·`SampleEligibilityTest`의 「수요기관이 없고 공고기관만 있으면 … null이다」 — `demandAgency = null`, `noticeAgency = Agency(...)`로 심어도 결과가 `null` |
| (2) | 표본 축에 `NOT_COLLECTED_YET` 잔존 | `RequestMappingTest`가 `null → UNKNOWN`을 직접 대조. `toSampleAgencyIdFact`가 `toAgencyIdFact`에 위임하는 한 줄 구현이라 `NOT_COLLECTED_YET`을 내는 별도 분기 자체가 코드에 없다(코드 검토로 grep 대신 확인 — 함수 본문이 위임 한 줄뿐) |
| (3) | 엔진이 `UNKNOWN` 표본을 거부 | D-3H2-3(ml-implementer, engine test) + D-3H2-4 교차 실측의 「`UNKNOWN` 표본 혼입 시 `excluded_observations=0`」 |
| (4) | 이름을 `AgencyId`로 싣는다 | `predictionRequestFor`/`sampleOf` 둘 다 `notice.demandAgency?.code`(코드 필드)만 읽는다 — `Agency.name`을 참조하는 경로가 없다(코드 검토). `PredictionFactsTest`·`SampleEligibilityTest`의 「이름만 있고 코드가 없으면 … null이다」가 이름만 있는 `Agency(code = null, name = ...)` 입력에서도 `agencyId`가 여전히 `null`임을 직접 잰다 |
| (5) | 요청·표본 정규화 불일치로 `DIRECT` 미도달 | D-3H2-4 — 두 축 모두 같은 `AgencyId("agency-cross-3h2")` 문자열을 실은 교차 실측에서 `DIRECT`를 실제로 관측 |
| (6) | 계약 문면과 송신·수신 구현이 서로 다른 말을 한다(verifier r1 F-1, HIGH) | **닫힘.** `features.proto`의 `CompetitionSample.agency_id` 주석이 이제 허용 사유 둘(`NOT_COLLECTED_YET`·`UNKNOWN`)을 선언하고, 송신(`RequestMapping.kt` `toSampleAgencyIdFact`→`toAgencyIdFact` 위임)과 수신(`distribution.py` `_ALLOWED_SEGMENT_MISSING_REASONS`)이 그 선언과 값 집합이 일치한다 — 세 자리 전부 `{NOT_COLLECTED_YET, UNKNOWN}` 하나로 수렴(도메인층 사본 `BidPredictionRequest.kt` KDoc도 같은 문면, F-2 동시 닫힘). `distribution.py`의 인용 주석도 새 계약 문면에 맞게 정정 |

## (2b) 값 획득 축

`AgencyId` 생성 자리는 `predictionRequestFor`·`sampleOf` 둘뿐(기존 타입 `bidvector.workflow.
prediction.AgencyId`, 새 public 표면 0) — 값은 `AgencyCode.value`에서만 온다(3H-1이 이미
확정한 정규화 규칙 재사용, 이 slice가 새 정규화 경로를 만들지 않는다). `toSampleAgencyIdFact`는
새 함수가 아니라 기존 함수의 본문을 위임으로 바꾼 것 — 새 public 표면 0.

## 알려진 제한

- `OPEN-3H-AGENCY-BACKFILL`(3H-1에서 이미 등재) — 3H-1 이전 저장 행은 기관 컬럼이 비어 있고,
  그 부재가 「수집 전」인지 「원천에 없음」인지 저장 층에서 구별하지 않는다(D-3H2-2가 그
  구별을 하지 않기로 결정한 것 자체가 이 열린 항목을 닫지는 않는다 — 백필 여부와 무관하게
  표본 축 사유는 항상 `UNKNOWN` 하나로 통일된다).
- 이 slice의 통합 test(`OpportunityAnalysisTest`)는 요청 축만 관측한다 — 표본 축
  (`CompetitionSample.agencyId`)은 `FakeCompetitionSamplePort`가 `sampleOf`를 거치지 않고
  fake가 직접 `CompetitionSample`을 주므로 그 test 층에서 재지 못한다. 표본 축 조립은
  `SampleEligibilityTest`(단위)가 잰다 — 4B-7의 같은 구조적 한계와 동일.

## 종결 조건 대응(scope.md 기준)

요청·표본 축 값 test(D-3H2-1) · 사유 `UNKNOWN` test(D-3H2-2) · 엔진 허용 집합 test + golden
불변(D-3H2-3, ml-implementer 레인) · 교차 실측 `DIRECT` 관측(D-3H2-4) · 전건 `check`(+ pytest)
— commands.md 전부 충족. verifier ready·`OPEN-2B-AGENCY-ID`·`OPEN-3H-SAMPLE-MISSING-REASON`
닫힘·사용자 승인은 이 문서 소관 밖(팀장·verifier).
