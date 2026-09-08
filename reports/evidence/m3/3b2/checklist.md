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
| ⑥ | 개찰 축 필드 계약 13행 중 12행 인스턴스화(P-9) | `CollectionPolicy.kt`(`KONEPS_OPENING_FIELD_ROWS`) | `CollectionPolicyTest`(등재 스물세 개·basis·WINNING_RATE 재사용·COUNT scale·presentIn·bidwinnrBizno 미등재) |
| ⑥b | 식별자 치환(P-10 (a)) — `bidwinnrNm` 원문 저장, `bidwinnrBizno`·대표자명 폐기, `opengCorpInfo` 부분 masking + fail-closed | `KonepsIdentifierMasking.kt`(`MaskedKonepsItem`·`maskOpengCorpInfo`·`mapMaskedOpeningItem`) | `KonepsIdentifierMaskingTest`(6 — 단일/다수/협상 세 변형 + allow-list + 식별자 부재 + `maskOpengCorpInfo` 단위) |
| ⑦ | contract mock server test — 세 어휘(제한 없음/03/08)·`bidNtceOrd` 필수·`inqryDiv` 회귀 | `KonepsOpeningResultSourceTest`·`KonepsLicenseLimitDocumentSourceTest`·`KonepsOperationDescriptorTest` | 아래 시나리오 대응표 |

## 시나리오 대응표 — scope.md ⑦

| 시나리오 | test | 결과 |
| --- | --- | --- |
| 낙찰 목록 정상 fetch, OPENING_AWARD_LIST 로 관측 | `KonepsOpeningResultSourceTest` | PASS |
| 예비가격 상세 단건 조회, RESERVE_PRICE_DETAIL 로 관측 | `KonepsOpeningResultSourceTest` | PASS |
| 제한 없음(`00`+`totalCount=0`) → 성공+빈 항목 | `KonepsOpeningResultSourceTest`·`KonepsLicenseLimitDocumentSourceTest` 각 1건 | PASS |
| `03` → 데이터 없음 | 같음 | PASS |
| `08`(필수값 입력 에러, `bidNtceOrd` 누락 시나리오) → `InputError`, 비재시도, 호출 1회 | `KonepsLicenseLimitDocumentSourceTest` | PASS |
| 미지 resultCode → 비재시도(3B 공통 기반 재사용 확인) | `KonepsOpeningResultSourceTest` | PASS |
| COL-07(license-limit 미등재 응답 키) → unknownFields 계수, masking 없음 | `KonepsLicenseLimitDocumentSourceTest` | PASS |
| opengCorpInfo 단일 낙찰자(5성분) → masking 성공 | `KonepsIdentifierMaskingTest` | PASS |
| opengCorpInfo 「낙찰예정자 다수」(3성분, 자리 뜻 다름) → 값 전체 폐기 | 같음 | PASS |
| opengCorpInfo 협상 계약(3성분, 투찰금액·투찰율 없음) → 값 전체 폐기 | 같음 | PASS |
| allow-list — 계약 없는 식별자 키가 fields·sourceText 둘 다에 없다 | 같음 | PASS |
| `inqryDiv` 값별 URI(AWARD_LIST/RESERVE_PRICE_DETAIL/LICENSE_LIMIT_DETAIL) + 표→URI 회귀 | `KonepsOperationDescriptorTest` | PASS |
| 서술 구성 실패(기간창+단건 동시 요구·bidNtceOrd 요구인데 bidNtceNo 없음·인자 불일치) | 같음 | PASS |

## 위협 모델 대응표 — scope.md 「방어한다」

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 술어 우회 | `fetchReservePrices`·`fetchQualificationText` 서명이 internal-constructor 증거 값만 받는다(3A 기존 + D-3B2-5 신규) | `PortsTest`, `KonepsOpeningResultSourceTest`(evidence 필수) |
| (b) `inqryDiv` 오용 | `KonepsOperationDescriptor` 필수 생성자 인자 + 기본값·폴백 없음 + 표→URI 회귀 test | `KonepsOperationDescriptorTest` |
| (c) 사업자 식별자 유입 | allow-list 반전(`MaskedKonepsItem.from`) — 미등재 키 자동 제외 | `KonepsIdentifierMaskingTest`(allow-list test) |
| (d) `08`·`03`·`00+totalCount=0` 혼동 | 3A `resultCodeCategories` 표(변경 없음, 재사용) + 세 회계 어휘 test | `KonepsOpeningResultSourceTest`·`KonepsLicenseLimitDocumentSourceTest` |
| (e) 3B 공통 기반 회귀 | `KonepsOpenApiNoticeSourceTest`(20)·`KonepsAdapterDependencyTest`(1)·`ServiceKeyTest` 편집 없이 green | commands.md S-2 |
| (f) 서비스 키 노출 | `ServiceKey` 재사용(3B 기존, 미변경) | 3B `ServiceKeyTest`(변경 없음) |

## 우회 후보 대응표 — scope.md 「우회 후보(≥5)」

| # | 우회 | 막는 것 |
| --- | --- | --- |
| 1 | 4B 가 `Fetch` 없이 상세를 부름 | `fetchReservePrices`·`fetchQualificationText` 서명(컴파일 차단) |
| 2 | 목록군에 `inqryDiv=2`(공고번호축)를 문서와 다르게 씀 | `KonepsOperationPolicy` 정책 표 + URI 회귀 test |
| 3 | `bidwinnrNm`을 `sourceText`에 원문 substring 으로 저장 | `sourceText`가 걸러진 `JsonObject.render()`(설계 검토 게이트 ① (2)) |
| 4 | 「제한 없음」을 실패로 회계 | 3A `NO_DATA`/성공 분류(재사용) + 어휘 test |
| 5 | `08`을 재시도 | 3A `INPUT_ERROR`(비재시도, 재사용) + `resultCode 08` test |
| 6 | 3B walker 시그니처를 바꿔 3B test 를 고침 | `itemMapper` 매개변수가 기본값을 가져 3B 호출부·test 편집 없이 green(commands.md S-2 실측) |

## 판단이 갈린 지점 — 계약이 명시하지 않아 이 레인이 정한 것

1. **`sucsfbidRate`는 새 `FieldConcept` 대신 기존 `WINNING_RATE`를 재사용한다.** 설계 검토 문서의
   ① 목록에는 없었으나(그 목록이 12개 개념 중 「최종낙찰업체명」을 빠뜨린 것과 같은 누락), 3A 에
   이미 있던 미사용 토큰이 정확히 이 개념에 맞아 2026-09-01 규칙(어휘를 지어내지 않는다)에 따라
   재사용했다.
2. **`fnlSucsfDate`(최종낙찰일자, 일자만·시각 없음)는 `DATETIME_NO_ZONE`이 아니라 `OPAQUE_TEXT`로
   등재했다.** `DATETIME_NO_ZONE`은 `sourceZone` 필수 짝을 강제하는데 이 필드는 시각 축이 아니라
   짝을 지을 자리가 없다. 새 스케일 토큰(날짜 전용 축)을 만드는 대신 원문 보존만 하는 `OPAQUE_TEXT`
   로 좁혔다(canonicalize 는 D-3B2-8 후속, 이 slice 밖).
3. **`opengCorpInfo` masking 실패는 procurement `CollectionDropReason`을 확장하지 않고 어댑터
   `unknownFieldCount`에 접었다.** 새 drop 사유 축을 여는 것은 procurement 편집(Accounting.kt)이
   필요한데 이 slice 의 procurement in_scope 는 여섯 파일로 고정돼 있다(P-9·D-3B2-5 범위). 3B
   `blankKeyCount`를 `unknownFieldCount`에 접은 기존 선례와 같은 자리를 썼다 — 후속 slice 가
   procurement 를 다시 열면 전용 사유를 추가할 수 있다(알려진 제한).
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
- **`opengCorpInfo` masking 실패의 회계 축이 procurement 전용 사유를 갖지 않는다**(판단 4번 —
  `unknownFieldCount`에 접힘). 후속 slice 가 procurement 를 다시 열 때 전용 `CollectionDropReason`
  변형을 추가하면 닫힌다.
- **`fnlSucsfDate`의 대문자 표기 변형(`FnlSucsfDate`, 외자 2종)은 등재하지 않는다** — §1.7.4 가
  「두 표기를 각각 등재하고 관측으로 좁힌다」로 남긴 `insufficient-evidence` 를 그대로 따른다.
- **`bssamt`(기초금액)의 `presentIn`을 예비가격 상세 축까지 넓히지 않는다** — 이미 등재된 행(3A
  P-1 승인분)을 수정하지 않고 기존 계약 행 불변을 우선했다(policy-values.md §1.7.1 각주가 지적한
  「같은 오퍼레이션이 bssamt 도 준다」는 이번 slice 가 반영하지 않은 사실로 남는다).
- **실제 KONEPS 호출·오퍼레이션 경로·파라미터 이름의 실물 일치는 검증하지 않는다**(out_of_scope,
  실제 호출 승인 뒤).
- **낙찰 목록 검색·개찰결과 목록 검색(`…PPSSrch`) 미구현** — legacy 미소비, 판단 7번.

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
`DetailFetch`·`Ports`·`KonepsPageUriBuilder`·`KonepsRawItemMapper`·`gate-tests.properties` 여덟
전부를 조회 — **매치 0건**. 이 slice 가 줄을 넣은 코드 파일을 `file:line`으로 인용하는 문서가
없다(코드 파일은 evidence·설계 문서가 보통 심볼·KDoc 앵커로 가리키지 줄 번호로 가리키지 않는다).

## 완료 조건

acceptance S-0~S-4 전부 통과(commands.md), 3A corpus 27/27 불변(`:procurement:test` 는 fixture
corpus 를 직접 재실행하지 않지만 `fixtures/**`가 out_of_scope 라 편집되지 않았다 — corpus 자체는
3A/3C 소관 test 가 잰다), 3B 기존 test 편집 없이 green.
