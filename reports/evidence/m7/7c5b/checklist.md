# M7/7C⑤-b 설계 검토 (Phase 2.5) — 내 입찰 결과 조회

계약: `scope.md`(D-7C5B-1~10 · 레인 정정 2026-10-31 「사유 열」). 이 문서는 **구현 전 검토와 그 뒤의
갱신**을 담는다. 라운드 이력은 적지 않는다 — 지금 참인 문장만 둔다.

## §0 위협 모델 경계

**방어한다**

- 다른 계정의 투찰 기록·건이 응답에 섞임
- 다른 업체의 상호·사업자등록번호·대표자명이 wire 에 닿음
- 잠정 1위 금액이 확정 낙찰가로 쓰여 차이가 지어내짐
- 판정 불가가 패찰로 접혀 분모가 오염됨
- 분모 0 이 0% 로 표시됨
- 운영자 기록과 서버 대사가 서로를 덮어씀
- 내 메모·증빙 참조·추첨번호·건너뜀 사유가 응답에 실림
- 조회가 쓰기를 일으킴(마감 정산 타임라인 포함)

**방어하지 않는다**

- SET-01 커널 판정의 옳고 그름(법인 표기 토큰 목록의 충분성 — `OPEN-SET-03`)
- 수집·승격의 완전성(낙찰자 상호 적재 부재 — `OPEN-SET01-AWARD-NAME-INGEST`)
- 상류 원문 자체의 오류
- 운영자가 적은 결과의 진실성

## §1 열거인가 구성인가 — **구성**

대사 어휘는 커널이 통째로 주지 않는다. `AwardOutcome` 은 낙찰·패찰과 사유 넷뿐이고, 유찰·재입찰은
진행구분 축, 개찰 전·승격 전·수집 전·공고 부재는 그 앞단이다. 손으로 적은 표로 두면 개찰 축 다섯 ×
진행구분 다섯 × 커널 넷의 조합에서 갈래가 늘 때 조용히 빠진다.

→ `reconcileBid` 가 **소진 `when` 세 겹**으로 조립한다(뷰 축 → 진행구분 축 → 커널 축). 갈래가 늘면
답을 주지 않고는 컴파일되지 않는다. 사유는 `enum` 이라 `entries` 로 **전수 도달 test** 가 기계로 선다.

## §2 우회 — 여덟, 각각 무엇이 막는가

| # | 우회 | 막는 것 |
| --- | --- | --- |
| ① | 응답 DTO 에 낙찰자 상호 칸을 더한다 | 상호를 나르는 타입이 `AwardWinnerTradeName` 하나이고 커널 산출(`ReconciliationVerdict`)에 이름 자리가 없다 — 그래서 **조립이 그 값을 손에 쥐지 않는다**. 다만 `Published.rawName` 이 public 이라 그 타입을 다시 읽어 아무 String 칸에 싣는 코드는 **컴파일된다**(r1 v L-2) — 실제로 막는 것은 **표지 글자 전문 단언**이고 DB 층·wire 층 둘에 있다. 계약 test 는 칸 **이름**을 따로 잰다 |
| ② | 7C⑤-a 의 개찰 질의에 상호 열을 더해 재사용한다 | 그 질의의 열 목록(`RESULT_COLUMNS`)을 `ResultSetMetaData` 등식이 잠근다(7C⑤-a). 이 slice 는 그 목록을 **그대로 import** 하므로 더하면 양쪽이 함께 붉는다. 상호는 **별 질의**로만 읽는다 |
| ③ | 화면이 목록을 세어 집계를 지어낸다 | 서버가 분자·분모·표본·제외 둘을 내고, 목록과 집계가 **한 operation · 한 판정 패스**에서 난다. 둘로 가르면 화면이 자기가 띄운 목록과 어긋나는 집계를 보일 수 있다 |
| ④ | 계정 없이 부르는 조회가 생긴다 | 포트 서명의 첫 인자가 `AccountId`(7B R-2) — 「계정 없이 부르는 조회」가 **존재할 수 없다**. 실 DB 소유 경계 test + wire 401 test |
| ⑤ | 판정 불가를 `false`/`null` 로 접어 분모에 넣는다 | `ReconciliationVerdict` 에 접는 API 가 없다(`AwardOutcome` 규율 승계). `AxisTally` 는 **표본이 파생**이라 「분자 + 제외 = 표본」이 깨질 자리가 없고, `MyBidResultAggregate` 가 두 축의 표본 등식을 생성자에서 진다 |
| ⑥ | 확정 낙찰가가 없을 때 잠정 1위 금액으로 차이를 채운다 | `awardGapOf` 의 **시그니처에 잠정 금액 자리가 없다**(`judgeAwardOutcome` 이 금액 인자를 두지 않은 것과 같은 수법) — 다만 그것만으로는 닫히지 않는다: `AwardAmount` 는 생성자가 public 이라 잠정 금액으로 **지어서** 넘기는 코드가 컴파일된다. 실제로 막는 것은 **「잠정 `Determined`(금액 있음) + 확정 없음」 픽스처 위의 단언**이고 두 층에 있다(workflow `AwardAbsent` · wire `gapState`·`gapWon: null`). 잠정을 `NotObserved` 로 심은 픽스처에서는 그 변이가 같은 답을 내 지나간다 — 그래서 이 픽스처가 유일한 측정 자리다 |
| ⑦ | 메모·증빙·추첨번호·건너뜀 사유가 응답에 실린다 | 포트 값(`RecordedRealBid`)에 **자리가 없고** 질의 열 목록에도 없다. 목록 단언 + 메모를 실제로 심어 산출에 그 글자가 없음을 값으로 잰다 |
| ⑧ | 페이퍼 건이 섞인다 | `RecordedRealBid` 의 생성자가 `internal` 이고 모듈 밖 유일한 경로인 `recordedRealBidOrNull` 이 페이퍼에 `null` 을 준다 — 질의 필터가 틀려도 **값이 서지 않는다** |

**쓰기 0 의 자리**(우회 ③·④와 별도 축): 포트 셋이 전부 읽기 하나짜리 `fun interface` 이고,
`BidCaseWorkflow` 를 부르지 않는다(그 읽기는 마감 정산이 타임라인에 쓴다, D-7C5B-8). 경로도
입찰함 뿌리 아래 두지 않았다. 실측은 읽기 전용 증인 + 네 표 행 수 불변 + 음성 대조.

## §2b 값 획득 축 — 새 public 표면 전수

| 표면 | 상호에 닿는가 | 막는 것 |
| --- | --- | --- |
| `ReconciliationBlocker`(enum 10) | 아니다 | 값을 싣지 않는다 |
| `ReconciliationVerdict`(sealed 5) | 아니다 | 이름 자리가 없다 — 커널 산출의 유일한 통로 |
| `AwardGap`(sealed 2) | 아니다 | 금액·통화뿐 |
| `AxisTally` · `AwardRateState` · `AwardRateAbsence` · `MyBidResultAggregate` · `OutcomeAgreement` | 아니다 | 수와 상태뿐 |
| `RecordedRealBid` | 아니다 | 메모·증빙·사유 자리 없음. 생성자 `internal` |
| `recordedRealBidOrNull` | 아니다 | 페이퍼 배제의 유일 경로 |
| `RecordedBidReadPort` | 아니다 | 첫 인자 주체 |
| `BatchOpeningFactsPort` | **아니다** | 7C⑤-a 의 사실 타입 — 상호 자리가 없는 그 타입 |
| `AwardWinnerNamePort` | **그렇다** | 상호를 나르는 **유일한** 표면. 산출이 `AwardWinnerTradeName` 하나이고 그 타입은 `toString()` 이 가려져 있다. 값은 `reconcileBid` 인자로만 지나가고 반환에 남지 않는다 — **타입이 막는 것은 거기까지**이고, 그 값을 다시 읽어 싣는 길은 표지 글자 단언이 막는다 |
| `MyBidResult` · `MyBidResults` | 아니다 | 상호 칸 없음(전문 단언) |
| `ReadMyBidResults` | 아니다 | 산출이 위 둘 |
| `MyBidResultsResponse` · `MyBidCaseResponse` · `MyBidAggregateResponse` | 아니다 | 계약 키 등식 + 칸 이름 금지어 + 전문 표지 단언 |
| `MyBidResultController` | 아니다 | use case 하나만 받는다 |
| `AccountScopedRoutes.BID_RESULTS` | 아니다 | 경로 상수 |
| `JdbcRecordedBidView` · `JdbcBatchOpeningFactsView` | 아니다 | 열 목록 등식 |
| `JdbcAwardWinnerNameView` | **그렇다** | 상호 열을 읽는 유일한 자리. 산출 타입이 경계 |
| `MyBidResultWiring` 의 빈 다섯 | 아니다 | 조립만 |

상호에 닿는 표면은 **둘**이고, 둘 다 산출 타입이 `AwardWinnerTradeName` 이다. 조립은 그 값을 쥐지
않는다 — 커널을 지나 판정이 되고 판정에는 이름 자리가 없다. 「닿을 **타입 경로가 없다**」는 과장이다
(r1 v L-2): `Published.rawName` 은 public 이라 다시 읽는 코드가 컴파일된다. 그 길을 막는 것은
**표지 글자 단언 둘**(DB 층 · wire 층)이고, 변이로 붉는 것이 확인돼 있다.

## §3 과잉·미달

**과잉으로 하지 않은 것**: 대사 판정 영속화 · 알림함 기록 · 새 수집 끝점 · 마이그레이션 · SET-03 역산 ·
SET-05 추천 오차 · 참가자 수·개찰 시각 · **잠정 1위 금액**(셋 다 7C⑤-a 가 공고 단위로 낸다 — 같은 사실을
두 operation 이 내면 두 출처가 갈린다, D-7C5B-12). 화면 E 는 이미 그 operation 으로 잠정 축을 읽어 그린다.

**죽은 분기를 두지 않았다**(변이로 잴 수 없는 방어는 방어가 아니다):

- `AwardGap` 의 통화 불일치 갈래 — `Currency` 어휘가 하나라 닿지 않는다. 갈래 대신 **어휘 래칫
  단언**으로 지킨다(어휘가 늘면 그 test 가 붉으며 그 자리를 가리킨다).
- 공고번호 접기의 `null` 갈래는 **남겼다** — `NoticeNumber.ofOrNull` 자신의 규율(「판독은 적재 경로를
  신뢰하지 않는다」)을 따른다. 다만 오늘 닿지 않는다는 사실을 KDoc 이 명시한다.

**미달로 남는 것**(알려진 제한, 아래 절).

## 알려진 제한

1. **대사 분모는 오늘 구조적으로 0 이다.** 낙찰자 상호(`bidwinnrNm`)의 production 적재 경로가 없다 —
   그 값을 주는 낙찰 목록 끝점이 출하 수집 조립에 없고(유일한 출하 팩토리가 개찰결과 목록 축으로
   고정), `OPEN-SET01-AWARD-NAME-INGEST` 가 그 자리다(운영자 2026-10-22 (a) 승인 · 착수 전). 그래서
   모든 건의 대사가 `WINNER_NAME_NOT_PUBLISHED` 로 판정 불가이고 비율은 「부재」로 나간다. 이 slice 는
   수집을 열지 않고 **그 사실을 사유로 낸다**.
2. **「고른 안」은 오늘 언제나 부재다.** `BidAmountOrigin.PriceMenu` 가 도달 불가다 — 투찰가 메뉴가
   출하 경로에 없고 HTTP 요청 DTO 에 provenance·메뉴 참조 칸이 없어 use case 가 `OperatorEntered` 를
   상수로 싣는다. 칸은 내되 상태로 「메뉴 아님」이 보인다.
3. **차이는 과세 축을 단언하지 않는다.** 확정 낙찰가의 과세 축은 언제나 `UNKNOWN` 이고(상류가
   선언하지 않는다) 투찰가는 운영자가 선언한 축이라, 둘이 같은 축이라는 근거가 없다. 그래서 그 값은
   「같은 축의 차이」가 아니라 **「표기된 두 수의 차이」**다. 계약 설명이 그 사실을 적는다.
4. **개찰 축 원문 훑기**가 남는다(7C⑤-a 와 같은 대가, `OPEN-7C5-RAW-AXIS-INDEX`). 묶어 묻기로
   **요청당 한 번**이 됐고 canonical 이 전부 있으면 돌지 않는다.
5. **격리 수준은 기본값**이다 — 한 요청의 질의 다섯이 한 순간을 보지 않는다(7C⑤-a 와 같은 자리).
6. **운영자 기록 축의 「미확정」은 한 칸**이다 — 아직 결과를 적지 않은 건과 건너뛴 건이 같은 칸에
   든다. 둘 다 분모 밖이라는 점에서 같고, 가르려면 상태 꼬리표를 소비자가 본다.

## 되돌리기

`rollback.md`.
