# 3A 정책 데이터 값 표 — **운영자 승인 2026-09-07**

> **승인일: 2026-09-07.** 승인 요청 표는 `_workspace/m3-3a/02_curator_approval_request.md` 이고,
> 결정 원문은 아래 「운영자 승인 2026-09-07」 절이다. **`fixtures/manifest.yaml` 의 승격된 case 들이
> `source.kind: operator-decision` 의 reference 로 그 절을 가리킨다** — 이 문서가 그 결정의 정본이다.
>
> **지위**: M3/3A curator 레인(별도 세션)의 산출물이다 — `3a/scope.md` in_scope 의
> `reports/evidence/m3/3a/policy-values.md`, 착수 결정 **D-3A-2 (a)**. 3A 구현 레인은 이 표를 **읽기만**
> 하고, 승인 뒤 Kotlin `EffectiveDatedPolicy` 인스턴스로 옮긴다(1C `LICENSE_QUALIFICATION_POLICY` ·
> 1D `ProvenancePolicyData` 관례). **이 문서는 Kotlin 을 쓰지 않는다.**
>
> **승인된 것과 아닌 것이 표 안에서 갈린다.** `authoritative` 행과 §6 의 P-1~P-6 은 승인됐다.
> **「미확정」 칸은 그대로 미확정**이며 비워 둔 것이 아니라 **근거가 없다는 판정**이다 —
> `legacy-behavior` 행도 정답 지위를 얻지 않았다(`data-extract.md` §1). 승인이 바꾼 것은
> **어느 값을 초기값으로 쓸 것인가**이지 근거의 층이 아니다.

---

## 운영자 승인 2026-09-07 — 결정 원문

> 아래는 운영자 결정의 **축어**다. 이 레인이 대신 판단하지 않는다.

**「curator 승인」(추천안 그대로, P-4·P-6 은 오케스트레이터 수정안).**

- **Q-1: 예** — 갈래 A 아홉 건(`-010`~`-018`) `authoritative` 유지(공식 문서 인용).
  `-011`(추정가격 부가세 제외)도 포함.
- **Q-2: B-1~B-10 전부 승인** — 해당 case(`-019`~`-026`, 기존 `-001`~`-009`)
  `insufficient-evidence` → `authoritative`, `source.kind` `m0-derived-rule` → `operator-decision`,
  `verified_paths` 도출. 어휘 미승인 경로(`MissingNoticeNumber`·`AlreadyHeld`·`AgeGateNotPassed` 등
  사유 이름)는 목록에 넣지 않는다(2026-09-01 규칙) — 3A 가 계약 어휘를 세운 뒤 별도.
- **Q-3**: P-1 **채택** · P-2 `sourceZone` 초기값 **`Asia/Seoul`**, `OPEN-3A-SOURCE-TZ` 열어 둠 ·
  P-3 legacy `BASE_RESOLUTION_ORDER` **불채택**(문서로 서는 키는 `bssamt` 하나,
  `ESTIMATED_RESOLUTION_ORDER` 의 「기초금액 키 없음」 성질은 유지) · P-4 ① **적용된다고 본다**
  ② **범주 후보 열 채택**(재시도 가능 01·02·04·05 / 입력 오류 06·07·08·10·11 /
  재시도 불가·인증 12·20·30·31·32 / quota 22 / 데이터 없음 03) ③ **`03` 은 성공도 실패도 아닌
  세 번째 상태**, legacy `OK_RESULT_CODES={"00","03"}` 불채택, 부재·미지 코드 `Unclassified`
  비재시도 · P-5 24h/48h **「측정 전 잠정」 채택** · P-6 **개찰·예비가격 축
  (`OpeningResultSourcePort` 구현)만 ScsbidInfoService 참고자료 확보를 선행 조건으로, 공고 축 3B 는
  진행** — 3B scope 의 착수 전 결정 후보로 등재.

**승인 범위는 `koneps-collection` 도메인이다.** 다른 도메인의 case 는 이 결정의 대상이 아니고
`review.approved_by_user: false` 로 남는다.

**승인이 잠그지 못한 자리.** Q-2 의 단서대로 사유·상태 토큰(`ValueOutsideDeclaredRange` ·
`ExplicitNull` · `KeyMissing` · `CodeNotInMapping` · `OccupancyGuard` ·
`NonAuthoritativeFillOnly` · `FallbackFromBudget` · `MissingNoticeNumber` · `Success` ·
`AccountingIdentityViolated` · `Skip`/`Fetch`/`AlreadyHeld`/`AgeGateNotPassed`/`RecheckGateNotPassed`)은
`verified_paths` 에 들지 않았다. 각 case 는 그 주장을 **불리언·셈·합성 식별자로 우회해** 잠근다 —
예: 「조회하지 않는다」를 `$.detailFetchCallCount: 0` 으로, 「정확히 1회」를
`$.totalDetailFetchCallCount: 1` 로. **어휘 승인은 3A 의 계약 타입 확정 뒤**다.

---

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 조달청 공식 문서 문면 | **문서 둘**을 인용한다. ① **OpenAPI 참고자료 — 나라장터 입찰공고정보서비스**(2025, `koneps-openapi-reference`) — §1·§2 의 근거. ② **OpenAPI 활용가이드 — 나라장터 공공데이터개방표준서비스**(2019.06, `pps-openapi-guide`) — §3 의 근거이며 **대상 서비스가 다르다**(§3.1). 둘 다 `fixtures/manifest.yaml` 의 `official_documents` 가 경로·버전·SHA-256 을 고정한다 |
| `legacy-behavior` | 기존 Python 이 실제로 하는 것 | legacy `ed4b06c` (read-only). **정답 지위 없음** |
| `observed` | 운영 데이터·로그의 관찰 | **이 문서에 0건.** 운영 DB 접근 승인이 선행한다 |
| 미확정 | 어느 층으로도 서지 않는다 | 근거를 찾지 못했다는 판정이며 소유 `OPEN` 을 단다 |

**공식 문서가 주는 것과 주지 않는 것.** 참고자료는 23 오퍼레이션의 요청·응답 항목 명세를 담는다 —
항목명(영문/국문) · **항목크기** · **항목구분**(필수 `1` / 옵션 `0` / `1..n` / `0..n`) · 샘플데이터 ·
**항목설명**. 금액·율 항목의 항목설명이 단위와 과세를 문면으로 적는 자리다. 활용가이드는 그 위에
**에러코드 표**를 준다(§3).

문서에 **없는** 것은 셋이다 — `resultCode` 의 **범주**(코드는 확보됐고 재시도 가능/불가 분류가 없다) ·
일시의 **출처 타임존** · **개찰·예비가격**(ScsbidInfoService) 항목 명세.

**좌표 규약**: legacy 는 파일과 **심볼 이름**으로 가리킨다(줄 번호를 쓰지 않는다 — 이 저장소의
`evidence-pack` 「낡는 좌표」 규격). 공식 문서는 **오퍼레이션명과 항목명**으로 가리킨다.

---

## 1. 필드 계약 — `data-dictionary.md` §5.3 슬롯

슬롯은 §5.3 의 열둘에 **`sourceZone`** 을 더한 열셋이다(D-3A-4, 조사 **G-7** — 시각 필드의 출처
타임존을 담을 자리가 §5.3 에 없었다). 아래 표는 축별로 나눈 뒤 그 축에 뜻이 있는 슬롯만 싣는다.

- `authoritative`(문서 출처) 칸: **항목설명 문면에서 직접 읽은 것**만.
- `effectiveFrom`: 이 corpus 의 어느 키도 시행일 분기를 갖지 않는다 — 전 행 **해당 없음**. 문서가
  version 을 갖되 항목별 시행일을 적지 않는다.
- `expectedRange`: **밴드를 재선언하지 않는다.** `data-dictionary.md` §1.4.3 의 밴드 id 를 참조한다
  (§5.3 계승 규율 2 — *"계약이 밴드를 재선언하지 않고 단일 출처를 참조한다"*).

### 1.1 금액 키

| rawName | concept | basis | unit / scale | vatTreatment | nullability | presentIn(문서) | 층 | provenance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `presmptPrce` | 추정가격 | `ESTIMATED` | 원(KRW) / 정수 | **`EXCLUSIVE`** | nullable(옵션) | 공고목록 4종 + PPSSrch 4종 + 기타공고 2종 + 입찰가격산식A | `authoritative` | 항목설명 *"… 부가가치세 및 조달 수수료를 **제외**한 금액(원화,원)"* · fixture `koneps-collection-011` |
| `asignBdgtAmt` | 배정예산금액 | `ALLOCATED_BUDGET` | 원(KRW) / 정수 | **`UNKNOWN`** | nullable(옵션) | 용역·물품·외자 목록 + PPSSrch | 단위 `authoritative` · 과세 **미선언** | 항목설명 *"배정된 예산액 또는 설계금액(원화,원)"* — 과세를 적지 않는다. `data-dictionary.md` §1.2 의 배정예산 행과 같은 결론 · fixture `koneps-collection-015` |
| `bdgtAmt` | 예산금액 | `ALLOCATED_BUDGET` | 원(KRW) / 정수 | **`UNKNOWN`** | nullable(옵션) | 공사 목록 + PPSSrch | 단위 `authoritative` · 과세 **미선언** | 항목설명 *"공고의 예산금액(원화,원)"* |
| `bssamt` | 기초금액 | `BASE_AMOUNT` | 원(KRW) / 정수 | **미확정** | nullable(옵션) | 물품·공사·용역 **기초금액조회** 3종 | 단위 `authoritative` · 과세 **미확정** | 항목설명 *"… 검토조정한 가격(기초금액)(원화,원)"* · fixture `koneps-collection-017`. **과세는 `OPEN-REG-05`** — 같은 오퍼레이션의 `rmrk1`(비고1) **샘플데이터**가 *"기초금액은 부가세 포함 가격임."* 이나 그것은 담당자 메시지의 예시이지 항목의 규범 선언이 아니다 |
| `bssAmtPurcnstcst` | 기초금액**순공사비** | **`bssamt` 와 다른 개념** | 원(KRW) / 정수 | 항목설명이 *"… 및 이에 대한 부가가치세 합산금액"* — **포함으로 읽히나 슬롯 선언이 아니다** → 미확정 | nullable(옵션) | **공사기초금액조회 1종만** | 개념 구분 `authoritative` · 과세 미확정 | *"기초금액 중 순공사원가 (재료비,노무비,경비,및 이에 대한 부가가치세 합산금액)"*. **legacy 가 이 키를 기초금액 후보로 순회한다** — 부분을 전체 자리에 넣는 형태(§5.2) |
| `bssAmt` | — | — | — | — | — | **문서에 없다** | 미확정(**미등재 키**) | legacy `field_contract_spec.TRUE_BASE_KEYS` 가 순회하는 대문자 `A` 변형. 이 문서의 어느 오퍼레이션에도 행이 없다 → §5.3 규율 1 의 「미지 필드」 |
| `presmptAmt` | 추정금액 | — | — | — | — | **문서에 없다** | 미확정(**미등재 키**) | legacy `NOTICE_ESTIMATE_KEYS` 의 둘째 키 |
| `usefulAmt` | 가용금액 | 미확정 | 원(KRW) / 정수 | 미확정 | nullable(옵션) | 기초금액조회 3종 | 단위 `authoritative` | *"수요기관 예산에서 수수료를 제외한 금액(원화,원)"*. **legacy 가 소비하지 않는다** — 신설 후보 |

> **`KEY_BASIS` 의 4키 접힘을 옮기지 않는다.** legacy `field_contract_spec.KEY_BASIS` 는
> `presmptPrce`·`presmptAmt`·`asignBdgtAmt`·`bdgtAmt` **넷 전부를 `Basis.BUDGET_ESTIMATE` 하나로**
>접는다. `data-dictionary.md` §1.2 머리가 그것을 *"한 basis 태그가 두 개념을 덮고"* 로 지목한
> 자리다. 위 표는 **키별로 basis 를 따로 적고**, 추정가격과 배정예산을 다른 basis 로 둔다.

### 1.2 율 키

| rawName | concept | scale(원문) | canonical | expectedRange | nullability | presentIn(문서) | 층 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `sucsfbidLwltRate` | 낙찰하한율 | **percent** | fraction (제수 `100`) | §1.4.3 **B6**(게시 하한율 신뢰 밴드) 참조 — 값은 `legacy-behavior`, `OPEN-DEC-10` 소유 | nullable(옵션) | 공고목록 4종 + PPSSrch 4종 | scale `authoritative` · 밴드 `legacy-behavior` |
| `rsrvtnPrceRngBgnRate` | 예비가격범위시작률 | **percent** | fraction (제수 `100`) | 미확정 | nullable(옵션) | 기초금액조회 3종 | scale `authoritative` |
| `rsrvtnPrceRngEndRate` | 예비가격범위종료율 | **percent** | fraction (제수 `100`) | 미확정 | nullable(옵션) | 기초금액조회 3종 | scale `authoritative` |
| `sucsfbidRate` | 사정률(낙찰가/예정가) | 미확정 | — | legacy 계약이 `0.5`~`1.0` 을 자체 선언 — **밴드 재선언이라 채택하지 않는다** | — | **문서에 없다**(ScsbidInfoService) | `legacy-behavior` |

> **원문 scale 이 percent 라는 것이 이 절의 핵심이다.** 항목설명이 `(%)` 를 달고 샘플이 두 자리
> 정수부다. legacy 는 이 축을 **값 크기로 판별**한다(`rate_normalization.PERCENT_SCALE_THRESHOLD`)
> — `ADR 0002` D-4 · §1.4.1 이 금지한 경로이고 fixture `koneps-collection-010` 이 그 대체를 고정한다.
> **legacy 의 `FieldContract` 는 `sucsfbidLwltRate` 를 `Scale.FRACTION` 으로 선언**하는데, 그것은
> **정규화 뒤** 값을 재는 검증기의 관점이다. V2 계약은 **원문 scale**(percent)과 canonical scale
> (fraction)을 따로 나른다 — 한 슬롯에 두 관점을 담으면 어댑터가 어느 쪽을 읽어야 하는지 갈린다.

### 1.3 식별자 키

| rawName | concept | scale | 형식(문서) | nullability | 층 |
| --- | --- | --- | --- | --- | --- |
| `bidNtceNo` | 입찰공고번호 | identifier | 항목크기 40. *"차세대나라장터 번호체계 개편 : R+년도(2)+단계구분(2)+순번(8) 총 13자리"*, 단계구분 `BK`(입찰)·`TA`(계약)·`DD`(발주계획)·`BD`(사전규격) | **필수** | `authoritative` |
| `bidNtceOrd` | 입찰공고차수 | identifier | **항목크기 3**, 샘플 `000` — 폭 고정 제로패딩 | **필수** | `authoritative` · fixture `koneps-collection-012` |
| `bfSpecRgstNo` | 사전규격등록번호 | identifier | 항목크기 17 | 옵션 | `authoritative` |
| `refNo` | 참조번호 | identifier | 항목크기 105 — 자체 시스템 공고번호 또는 G2B 번호 | 옵션 | `authoritative` |
| `bidClsfcNo` | 입찰분류번호 | identifier | 항목크기 5 | 필수 | `authoritative` |
| `rbidNo` | 재입찰번호 | identifier | 항목크기 11 | 옵션 | `authoritative` |
| `bidPbancNo` | — | — | **문서에 없다** | — | 미확정(미등재 키) — legacy 의 공고번호 폴백 사슬 둘째 |

> **`bidNtceOrd` 의 `int` 변환 금지가 이 표의 무게중심이다.** 폭이 3으로 고정된 자리에서 선행 `0` 은
> 표기가 아니라 토큰의 일부이므로 `"000"` 과 `"0"` 은 다른 토큰이다. `R-QUAL-05` 가 그 변환으로
> 1차 공고 전부의 자격을 잃은 회귀이고, D-3A-0 (a) 가 `NoticeRound` 값 객체를 신설했다.
> **문서가 떠받치는 것은 원문 형태와 왕복 보존까지**이고 「산술 미정의」는 §5.3 의 V2 설계 규칙이다.

### 1.4 일시 키 — `sourceZone` 축 (D-3A-4)

| rawName | concept | 형식(문서) | `sourceZone` | 층 |
| --- | --- | --- | --- | --- |
| `bidClseDt` | 입찰마감일시 | `"YYYY-MM-DD HH:MM:SS"`, 항목크기 19 | **미선언** | 형식 `authoritative` · zone **미확정** |
| `opengDt` | 개찰일시 | 같음. *"집행관이 개찰을 수행할 수 있는 **시작일시**이며 … 실제 개찰을 수행한 시간을 의미하지 않음"* | **미선언** | 같음 |
| `bidNtceDt` | 입찰공고일시 | 같음 | **미선언** | 같음 |
| `bidBeginDt` | 입찰개시일시 | 같음 | **미선언** | 같음 |
| `rgstDt` | 등록일시 | 같음, 필수 | **미선언** | 같음 |
| `bssamtOpenDt` | 기초금액공개일시 | 같음 | **미선언** | 같음 |

> **문서가 형식만 정하고 타임존을 정하지 않는다.** 항목크기 19 가 `YYYY-MM-DD HH:MM:SS` 를 꽉 채워
> offset 자리가 없다는 것도 같은 방향이다. fixture `koneps-collection-014` 가 그 **부재**를 고정한다.
>
> **legacy 는 naive 문자열을 UTC 로 가정한다** — `app/core/time.ensure_utc` 의 *"assuming naive values
> are already UTC"*. 원문은 한국 현지시각으로 보이므로 저장 마감이 9시간 밀렸을 수 있으나 **운영 피해
> 기록을 찾지 못했다**(조사 a-10). **판정하지 않는다** — 해석 규칙을 정책 데이터로 두고 원문을
> 보존하면 규칙이 틀려도 재해석할 수 있다. 초기값 후보는 `Asia/Seoul` 이며 **승인 대상**이고
> 소유는 `OPEN-3A-SOURCE-TZ` 다. fixture `koneps-collection-026` 이 그 배관을 고정한다.
>
> **`opengDt` 는 「개찰 가능 시작 시각」이지 「실제 개찰 시각」이 아니다** — 문서가 직접 구별한다.
> COL-03 의 age-gate 앵커가 어느 쪽인지는 이 구별에 걸린다(§4 참조).

### 1.5 코드·라벨·플래그 키

| rawName | concept | 어휘(문서) | 층 |
| --- | --- | --- | --- |
| `bsnsDivNm` | 업무구분명 | **물품 · 용역 · 공사 · 외자** (필수, 항목크기 30) | `authoritative` · fixture `koneps-collection-016` |
| `ntceKindNm` | 공고종류명 | **등록공고 · 변경공고 · 취소공고 · 재공고** — *"공고의 공고상태명"* | `authoritative`. §2.2.1 `NoticeStatus` 와의 대응은 **3A 가 정한다**(이 표는 원문 어휘만) |
| `rgstTyNm` | 등록유형명 | *"조달청 또는 나라장터 자체 공고건"* · *"나라장터 기타 공고건"* | `authoritative` |
| `reNtceYn` | 재공고여부 | `Y`/`N` | `authoritative`. `ntceKindNm` 의 「재공고」와 **두 자리에 같은 사실** — 3A 가 어느 쪽을 읽는지 정한다 |
| `bidMethdNm` | 입찰방식명 | 9값 열거(전자입찰·직찰·우편·상시·전자시담·복수견적(역경매) 조합) | `authoritative` |
| `cntrctCnclsMthdNm` | 계약체결방법명 | 일반경쟁·제한경쟁·지명경쟁·수의 등 산문 정의 — **열거 목록이 아니다** | 산문 `authoritative` · 열거 미확정 |
| `sucsfbidMthdNm` | 낙찰방법명 | *"낙찰자를 결정하는 방법-낙찰방법 세부기준"* — 항목크기 700 의 자유 텍스트 | 미확정(열거 없음) |
| `indstrytyLmtYn` | 업종제한여부 | `Y`/`N` | `authoritative`. COL-04 의 쿼터 절약 게이트 입력 |
| `bidPrtcptLmtYn` · `prdctClsfcLmtYn` · `cmmnSpldmdCorpRgnLmtYn` | 각 제한 여부 | `Y`/`N` | `authoritative` |
| `rgnLmtBidLocplcJdgmBssCd` / `…Nm` | 지역제한 소재지 판단기준 코드/명 | 코드 항목크기 1, 명 25 — **코드표 없음** | 형태 `authoritative` · 코드표 미확정 |
| `lcnsLmtNm` | 면허제한명 | *"면허제한코드/면허제한명"* — **한 셀에 코드와 명** | `authoritative`. COL-08 이 겨누는 형태의 문서 근거 |
| `permsnIndstrytyList` | 허용업종목록 | `[허용업종명1/허용업종코드1],[허용업종명2/허용업종코드2]`, `0..n`, 항목크기 4000 | `authoritative` |
| `lmtGrpNo` · `lmtSno` | 제한그룹번호 · 제한순번 | identifier, 항목크기 3 · 6 | `authoritative` |
| `cnstrtnAbltyEvlAmtList` | 시공능력평가금액목록 | `[제한그룹번호^면허지역통합코드명^면허지역통합코드^시공능력평가금액]`, `0..n`, 항목크기 4000. **단위·과세 미선언** | 형식 `authoritative` · 단위·과세 **미확정**(`OPEN-QUAL-10`) · fixture `koneps-collection-018` |
| `indstrytyCd` · `indstrytyNm` · `prtcptLmtRgnNm` | 업종코드·업종명·참가제한지역명 | 이 문서에서는 **요청 파라미터**로만 선언된다(*"검색하고자하는 …"*) — 응답 항목 명세에 없다 | 미확정 — legacy 는 응답에서 읽는다(`LICENSE_TEXT_KEYS`) |

### 1.6 봉투·페이지네이션 키

| rawName | concept | 항목크기 | 항목구분 | 층 |
| --- | --- | --- | --- | --- |
| `resultCode` | 결과코드 | 2 | **필수** | `authoritative` · fixture `koneps-collection-013` |
| `resultMsg` | 결과메세지 | 50 | **필수** | `authoritative` |
| `numOfRows` | 한 페이지 결과 수 | 4 | 필수 | `authoritative` |
| `pageNo` | 페이지 번호 | 4 | 필수 | `authoritative` |
| `totalCount` | 전체 결과 수 | 4 | 필수 | `authoritative` |

> **`totalCount` 가 「필수」라는 것이 COL-06 백스톱의 전제를 바꾼다.** legacy 의 종료 조건은
> `totalCount` **부재**를 정상 경로로 다루고(short page · max_pages 백스톱), `R-COL-04` 가 그 부재
> 상황의 무한 루프를 막는다. 문서 기준으로는 부재가 **계약 위반**이다 — 3B 는 백스톱을 유지하되
> 부재를 「정상」이 아니라 관측 가능한 이상으로 낸다. **이 판단은 3B 의 것이고 이 표는 필수성만 준다.**

### 1.7 개찰·예비가격 키 — **문서 미확보**

legacy 가 소비하는 다음 키는 **ScsbidInfoService**(낙찰·개찰·복수예비가격) 소관이고, 그 서비스의
참고자료는 이 저장소에 없다. 전 행 **미확정**이며 층은 `legacy-behavior` 조차 부여하지 않는다
(값이 아니라 키 존재만 아는 상태다).

`sucsfbidAmt` · `sucsfbidRate` · `rlOpengDt` · `fnlSucsfDate` · `prtcptCnum` · `bidwinnrNm` ·
`bidwinnrBizno` · `plnprc` · `compnoRsrvtnPrceSno` · `bsisPlnprc` · `drwtYn` · `opengCorpInfo` ·
`progrsDivCdNm` · `opengDate` · `bidOpenDt` · `ntceNm` · `prcmBsneSeCd`

> **3B 착수 전에 ScsbidInfoService 참고자료 확보가 필요하다.** 없이 진행하면 개찰 축 전체가
> legacy 형태 이식이 되고, 그것이 `data-extract.md` §1 이 금지하는 자리다. `bidwinnrNm`·
> `bidwinnrBizno` 는 **사업자 식별자**라 수집·저장 시 masking 정책이 선행한다.

### 1.8 셈

| 축 | 수 |
| --- | --- |
| legacy `KNOWN_FIELDS` 키 | 60 |
| 그중 이 문서의 **응답** 항목 명세에 있는 키 | 37 |
| 그중 문서에 없는 키(개찰·예비가격 포함) | 23 |
| 이 문서에만 있고 legacy 가 소비하지 않는 키(신설 후보 예: `usefulAmt`·`reNtceYn`·`sucsfbidMthdNm`·`cnstrtnAbltyEvlAmtList`) | 문서 응답 항목 264개 중 다수 — 전수 등재는 3A 범위 밖 |

**§5.3 은 「소비되는 모든 키에 계약이 필수」다.** 위 1.1~1.6 이 legacy 소비 키 가운데 **문서로 설 수
있는 37 건**을 덮고, 1.7 의 17 건은 문서 확보 전까지 **소비 불가**로 남는다(계약 없는 키는 소비
함수에 들어가지 못한다 — 3A ④).

---

## 2. 해석 순서 — `legacy-behavior`

legacy `field_contract_spec` 의 두 상수다. **이 순서는 정답이 아니다** — `data-dictionary.md` §5.2 가
*"해석 순서는 정책 데이터"* 로 두었고, 아래는 그 정책의 **초기값 후보**이자 legacy 실물이다.

| 이름 | 순서 | 층 |
| --- | --- | --- |
| `BASE_RESOLUTION_ORDER` (기초금액) | `bssAmt` → `bssamt` → `bssAmtPurcnstcst` → `asignBdgtAmt` → `bdgtAmt` → `presmptPrce` → `presmptAmt` | `legacy-behavior` |
| `ESTIMATED_RESOLUTION_ORDER` (추정가격) | `presmptPrce` → `presmptAmt` → `asignBdgtAmt` → `bdgtAmt` | `legacy-behavior` |

**읽어야 할 네 가지.**

1. **추정가격 순서에 기초금액 키가 없다.** legacy 주석이 근거를 적는다 — 추정가격의 basis 는 기초금액과
   섞지 않는다(#162). **이 성질은 V2 가 유지한다.**
2. **기초금액 순서의 앞 세 후보 중 문서로 서는 것은 `bssamt` 하나다.** `bssAmt` 는 문서에 없고
   `bssAmtPurcnstcst` 는 **다른 개념**(기초금액순공사비)이다 — §1.1 참조. 그대로 옮기면 **부분을 전체
   자리에** 넣는다. **3A 는 이 순서를 그대로 채택하지 않는다.**
3. **`0`·미상 후보를 건너뛴다**(`first_openapi_amount(..., positive_only=True)`). 근거가 legacy
   docstring 에 있다 — *"a `bssAmt` filled with 0 would otherwise shadow the positive budget fallback
   behind it"*. **이 성질도 유지하되 「건너뜀」이 산출에 남아야 한다**(§5.2 「해석을 한 지점에」).
4. **legacy 가 자기 취약점을 자백한다** — positive-only 축이 프로덕션과 검증기에 **각각** 구현돼
   상수 공유로 보장되지 않는다. 3A ⑤ 가 해석을 한 함수로 두고 검증기가 그것을 호출한다.

---

## 3. `resultCode` — **표 확보(2026-09-07) · 범주는 미확정**

> **갱신.** 이 절은 2026-09-07 오전까지 「원문 미확보」였다. 같은 날 오케스트레이터가 **조달청 공공데이터
> 개방 OpenAPI 활용가이드 v1.2(2019.06)** 를 확보했고 그 문서 끝의 「OPEN API 에러코드별 조치방안」 절이
> 표 본문이다. curator 레인이 docx 원문에서 **독립 재추출**해 인수본과 대조했다(16행 일치).
> 문서 고정은 `fixtures/manifest.yaml` 의 `official_documents` 항목 `pps-openapi-guide` 이고,
> **표 본문의 커밋된 정본은 `fixtures/input/koneps-collection-027.json`** 이다(원 docx 는 `_workspace/`
> 아래라 저장소에 없다).

### 3.1 두 문서가 각각 주는 것

| 축 | 출처 | 층 |
| --- | --- | --- |
| `resultCode` 는 **필수 항목**(항목크기 2, 샘플 `00`) — 부재는 「정상」이 아니라 계약 위반 | 입찰공고정보서비스 참고자료(`koneps-openapi-reference`) · §1.6 | `authoritative` · fixture `-013` |
| **16 에러 코드의 이름 · 설명 · 조치방안 문면** | 활용가이드(`pps-openapi-guide`) 「에러코드별 조치방안」 | `authoritative` · fixture `-027` |
| `00` = 정상 | **두 문서 모두** 응답 명세·예시에 `resultCode 00` / `resultMsg 정상` 을 싣는다 | `authoritative` |
| 코드 → **범주**(재시도 가능/불가·quota·입력 오류·데이터 없음) | **어느 문서에도 없다** | **미확정 — 승인 대상** |

**「17」의 셈이 맞는다.** 표의 코드는 16개이고 `00` 은 표에 없다. **16 + `00` = 17** 이 `OPEN-COL-02`
문면의 「17 `resultCode`」와 맞는다.

**대상 서비스가 다르다 — 이 절의 유일한 한계.** 활용가이드는 `PubDataOpnStdService`(공공데이터개방
표준서비스, 2019)의 것이고 수집이 부르는 `BidPublicInfoService`(입찰공고정보서비스, 2025)가 아니다.
「에러코드별 조치방안」은 **공공데이터포털 공통 절**로 보이나 **그 사실을 문서가 선언하지 않는다.**
**적용 범위는 승인 대상**(§6 **P-4**)이고, 방증 둘은 `observed`/`legacy-behavior` 층이다 —
① 두 문서의 `resultCode` 선언이 같다(항목크기 2 · 필수 · 샘플 `00`) ② legacy 가 **입찰공고정보서비스에서
관측한** `08` 의 뜻이 이 표의 `08` 행과 **문면까지 일치**한다.

### 3.2 표 — 16 코드

`설명`·`조치방안` 열의 **전문은 `fixtures/input/koneps-collection-027.json` 이 축어로 싣는다**(정본
한 자리). 아래는 코드 · 이름 · 설명 요지와 **V2 범주 후보**다.

| 코드 | 이름(문서) | 설명 요지(문서) | V2 범주 **후보** | 층 |
| --- | --- | --- | --- | --- |
| `00` | (표 밖) 정상 | 응답 명세·예시의 샘플 | 성공 | 코드 `authoritative` · 「성공」 판정은 미확정 |
| `01` | Application Error | 제공기관 서비스 상태 불량 | 재시도 가능(제공기관 일시 장애) | 문면 `authoritative` · 범주 **미확정** |
| `02` | DB Error | 같음 | 재시도 가능 | 같음 |
| `03` | No Data | *"데이터 없음 에러"* — **조치방안 칸이 비어 있다** | **데이터 없음**(실패 아님) | 같음. **legacy 는 성공으로 둔다** — §3.3 |
| `04` | HTTP Error | 제공기관 서비스 상태 불량 | 재시도 가능 | 같음 |
| `05` | service time out | 같음 | 재시도 가능 | 같음 |
| `06` | 날짜Format 에러 | 날짜 Default/Format Error | 입력 오류(재시도 불가) | 같음 |
| `07` | 입력범위값 초과 에러 | 파라미터 입력값 범위 초과 | 입력 오류 | 같음 |
| `08` | 필수값 입력 에러 | 필수 파라미터 누락 | 입력 오류 | 같음. **legacy 가 대상 서비스에서 관측** |
| `10` | 잘못된 요청 파라미터 에러 | `ServiceKey` 파라미터 없음 | 입력 오류(인증 구성) | 같음 |
| `11` | 필수 요청 파라미터가 없음 | 필수 파라미터 누락 | 입력 오류 | 같음. `08` 과 설명이 같다 — **두 코드가 같은 뜻** |
| `12` | 해당 오픈API 서비스가 없거나 폐기됨 | 호출 URL 이 잘못됨 | 재시도 불가(구성 오류) | 같음 |
| `20` | 서비스 접근 거부 | 활용승인 되지 않은 호출 | 재시도 불가(인증) | 같음 |
| `22` | 서비스 요청 제한 횟수 초과 에러 | **일일 활용건수 초과** | **quota** | 같음 |
| `30` | 등록되지 않은 서비스 키 | 키가 틀렸거나 URL 인코딩 안 됨 | 재시도 불가(인증) | 같음. **`OPEN-COL-01` 의 3-variant 재시도가 겨눈 자리** |
| `31` | 기한 만료된 서비스 키 | 사용기간 만료 | 재시도 불가(인증) | 같음 |
| `32` | 등록되지 않은 도메인명 또는 IP주소 | 신청 IP 와 호출 서버가 다름 | 재시도 불가(인증) | 같음 |

**「V2 범주 후보」 열은 문서가 주지 않는다.** 조치방안은 사람이 읽는 안내이지 「이 실패를 재시도해도
되는가」의 답이 아니다. 그 판정이 D-M3-4 가 `procurement` 정책 데이터로 두기로 한 자리이고
**갈래 B(승인 대상)** 다. 위 열은 이 레인의 **제안**이며 층이 없다.

**세 가지가 3B 에 바로 걸린다.**

1. **`03` 을 어느 쪽으로 볼 것인가.** 표는 에러로 등재하되 조치방안을 비운다. legacy 는 성공으로
   둔다. 「데이터 없음」은 실패가 아니지만 「정상」도 아니다 — 세 번째 상태가 필요하다.
2. **`22` 로 quota 가 `resultCode` 축에도 있다.** legacy 는 quota 를 HTTP 429 로만 안다(§3.3 각주).
   3B 의 rate limiter 는 **두 축**을 다 봐야 한다.
3. **`30` 이 `OPEN-COL-01` 을 설명한다.** 「키가 틀렸거나 **URL 인코딩하지 않음**」이 legacy 의
   3-variant 순회가 대응하려던 상황으로 보인다. 그러나 legacy 의 재시도 조건은 **HTTP 401 전용**이라
   `resultCode 30` 에 걸리지 않는다 — **재시도가 겨눈 실패와 실제 트리거가 어긋나 있다.**
   D-M3-5 (a)(키 variant 불채택 + 단일 인코딩)를 **뒤집지는 않으나 근거를 바꾼다**: 「근거 문서가
   없다」가 아니라 「문서가 원인을 적고, 그 원인은 인코딩을 한 번 바르게 하면 사라진다」다.

### 3.3 legacy 가 아는 코드 — `legacy-behavior`

| 코드 | legacy 의 처리 | 좌표 | 비고 |
| --- | --- | --- | --- |
| `00` | 성공 | `http_client.OK_RESULT_CODES` | 문서 샘플과 일치 |
| `03` | 성공 — *"정상 서비스·빈 결과"* | 같음 | **문서와 어긋난다** — 활용가이드는 `03` 을 「No Data」 **에러**로 등재한다(§3.2). 「성공」은 legacy 의 해석이고 문서 근거가 없다 |
| `08` | *"필수값 입력 에러"* — 표적조회·license-limit 서브콜에서 `bidNtceOrd` 를 빠뜨렸을 때. 같은 주석이 *"제한 없는 공고는 `resultCode 00` + `totalCount=0`"* 도 적는다 | `openapi.py` 의 license-limit 서브콜 주석 | **문서 근거가 생겼다**(2026-09-07) — 활용가이드 `08` 행의 이름이 *"필수값 입력 에러"* 로 **문면까지 같다.** 이것이 표의 적용 범위를 뒷받침하는 방증 둘 중 하나다(§3.1). 「제한 없음」과 「호출 실패」가 다른 코드로 갈린다는 사실은 그대로 3B 에 중요하다 |
| 빈 문자열 / 부재 | **성공으로 통과**시킨 뒤 회계에 `"00"` 으로 기록 | `http_client.check_result_code` · `collection.py` | **`R-COL-01` 이 겨누는 결함.** V2 미채택 |
| 그 밖의 모든 코드 | `ValueError` 로 즉시 중단 | `http_client.check_result_code` | 범주 구분 없음 — 재시도 가능/불가가 갈리지 않는다. **표가 확보됐으므로 이 접기는 이제 이식할 이유가 없다** |

> **HTTP 429 는 `resultCode` 축이 아니다.** legacy 는 HTTP 상태 `>= 400` 을 `ValueError` 로 중단하며
> **429 를 다른 4xx 와 구별하지 않는다**. 관찰된 문구는 *"API token quota exceeded"* 이고 legacy 가
> **daily quota 가 아니라 rate limit** 이며 **약 2분에 회복**된다고 적는다(`app/tasks/jobs.py`).
> 원인 진단도 적혀 있다 — *"Concurrency, not total volume, was the cause."* **3B rate limiter 정책의
> 1차 근거는 동시성 상한 + 회복 대기**이고 총량 예산은 2차다(`OPEN-COL-05`, D-M3-5 (a)).

---

## 4. 조회 가치 게이트 초기값 — `legacy-behavior`

COL-03 의 4겹 게이트 가운데 **도메인 판단에 해당하는 두 값**이다. 나머지 둘(defer · reuse)은 실행
구조라 3B 소관이다.

| 이름 | legacy 값 | 뜻 | 좌표 | 층 |
| --- | --- | --- | --- | --- |
| age-gate | **24 시간** | 개찰 이후 이만큼 지나기 전에는 예비가격 상세를 조회하지 않는다 — 갓 개찰한 공고는 예비가격이 아직 없어 조회가 낭비다 | `config.KONEPS_SCSBID_RESERVE_DETAIL_MIN_SETTLE_AGE_HOURS` | `legacy-behavior` |
| recheck-gate | **48 시간** | 「조회했으나 비어 있었다」로 확인된 공고를 이만큼 다시 조회하지 않는다 — 영원히 비는 공고가 매 스윕 rate limit 을 태우는 것을 막는다 | `config.KONEPS_SCSBID_RESERVE_DETAIL_RECHECK_HOURS` | `legacy-behavior` |
| (참고) 목록 페이지 간 throttle | 0.2 초 | | `config.KONEPS_SCSBID_COLLECTION_REQUEST_DELAY_SECONDS` | `legacy-behavior` · **OPS-08 소관**, 3A 아님 |
| (참고) 상세 백필 호출 간 throttle | 1.0 초 | 상세 엔드포인트가 목록보다 강하게 제한된다는 관찰의 대응 | `config.KONEPS_SCSBID_RESERVE_DETAIL_REQUEST_DELAY_SECONDS` | 같음 |

**두 값 다 근거 문서가 없다.** legacy 주석이 **왜 게이트가 필요한가**는 적지만 **왜 24 와 48 인가**는
적지 않는다. **운영자 승인 2026-09-07(P-5) — 「측정 전 잠정」으로 채택한다.** 층은 여전히
`legacy-behavior` 이고, 승인이 준 것은 **초기값으로 쓸 권한**이지 근거가 아니다. 관측이 쌓이면
정책 데이터의 값만 바꾼다.

**fixture 는 이 값을 쓰지 않는다.** `koneps-collection-024`·`025` 는 `SYN-POLICY-detail-fetch-2026-09-07`
(8h · 20h)를 입력으로 받고, 기대값은 값이 아니라 **스윕 시각과 게이트의 대소 관계**에만 의존한다
(`manifest.yaml` 의 `numeric_discipline.synthetic_policy_inputs`).

> **앵커가 무엇인지 정해야 한다.** legacy 는 age-gate 를 「개찰/마감 일시」 기준으로 재는데, §1.4 가
> 보인 대로 `opengDt` 는 **개찰 가능 시작 시각**이지 실제 개찰 시각이 아니다. 실제 개찰 시각은
> ScsbidInfoService 의 `rlOpengDt` 이고 그 서비스 문서를 확보하지 못했다(§1.7). **3A ⑪ 의 술어는
> 앵커를 인자로 받고**, 어느 필드를 앵커로 쓸지는 3B 가 문서 확보 뒤 정한다.

---

## 5. 업무구분 매핑 — `legacy-behavior`

legacy `html_parsing` 이 **개찰결과 그리드의 업무코드**를 라벨로 옮기는 표다.

| 코드 | 라벨 | 층 |
| --- | --- | --- |
| `01` | 물품 | `legacy-behavior` |
| `03` | 일반용역 | `legacy-behavior` |
| `05` | 기술용역 | `legacy-behavior` |
| `07` | 공사 | `legacy-behavior` |

**이 표는 §1.5 의 `bsnsDivNm` 열거와 다른 축이다.** 문서가 업무구분**명**을 물품·용역·공사·외자
넷으로 열거하는데, 위 표는 **코드 → 라벨**이고 「용역」을 일반/기술 둘로 가른다. **두 축을 한 자리에
접지 않는다** — COL-08 이 코드와 라벨을 두 값으로 두라고 하는 이유가 이것이고, fixture
`koneps-collection-016` 이 「기술용역은 문서 열거 밖」을 고정한다.

**전체 코드 체계는 미확정이다** — `OPEN-COL-03` 이 활성이다. 위 넷은 legacy 가 **관찰한** 값이고
전수가 아니다. 매핑에 없는 코드에 임의 라벨을 붙이지 않는다(COL-08 · `koneps-collection-007`).

---

## 6. 승인 결과 — 2026-09-07

**P-1 ~ P-6 전부 승인됐다**(P-4·P-6 은 오케스트레이터 수정안). 원문은 위 「운영자 승인 2026-09-07」 절.

| # | 승인 대상 | 층 | **결정** |
| --- | --- | --- | --- |
| P-1 | §1.1~§1.6 의 `authoritative` 칸을 **3A 필드 계약 정책 데이터의 초기값**으로 | `authoritative`(문서 출처) | **채택.** 3A 가 `KonepsFieldContract` 인스턴스로 옮긴다. 값 자체가 문서 인용이라 새 주장이 아니다 |
| P-2 | §1.4 의 `sourceZone` 초기값 | 값은 **미확정** — 승인은 초기값 채택이지 근거가 아니다 | **`Asia/Seoul` 채택, `OPEN-3A-SOURCE-TZ` 는 열어 둔다.** 원문 보존 + 규칙 id 기록이라 재해석 경로가 남는다. `koneps-collection-026` 이 그 배관을 잠근다 |
| P-3 | §2 의 두 해석 순서 | `legacy-behavior` | **legacy `BASE_RESOLUTION_ORDER` 불채택.** 문서로 서는 기초금액 키는 `bssamt` 하나이고 `bssAmtPurcnstcst` 는 부분 개념이다. 3A 가 `bssamt` 를 유일한 기초금액 키로 두고 나머지 폴백의 provenance 를 나눈다. **`ESTIMATED_RESOLUTION_ORDER` 의 「기초금액 키 없음」 성질은 유지** |
| P-4 | §3.2 의 **범주 후보 열**을 초기값으로 채택하고 표가 **입찰공고정보서비스에도 적용된다**고 보는가 (표 본문은 2026-09-07 확보) | 코드·문면 `authoritative` · 범주·적용 범위 **미확정** | **채택.** 방증 둘(두 문서의 `resultCode` 선언 일치 · legacy 가 대상 서비스에서 관측한 `08` 문면 일치)이 있고, 틀려도 정책 데이터라 값만 바꾼다. **`03` 은 성공도 실패도 아닌 「데이터 없음」 세 번째 상태**로 두고 legacy 의 성공 처리는 채택하지 않는다. 부재·미지 코드는 `Unclassified`(fail-safe 비재시도) |
| P-5 | §4 의 게이트 초기값 **24h · 48h** | `legacy-behavior` — 승인이 층을 올리지 않는다 | **「측정 전 잠정」으로 채택.** 근거 문서가 없다는 사실은 그대로 남고, 관측이 쌓이면 정책 데이터의 값만 바꾼다. fixture 는 이 값을 쓰지 않으므로(합성 정책 입력) corpus 가 흔들리지 않는다 |
| P-6 | §1.7 의 **ScsbidInfoService 참고자료 확보**를 3B 선행 조건으로 둘지 | 미확보 | **축을 갈라 둔다**(오케스트레이터 수정안). **개찰·예비가격 축(`OpeningResultSourcePort` 구현)만** 참고자료 확보를 선행 조건으로 하고 **공고 축 3B 는 진행**한다. 3B `scope.md` 의 착수 전 결정 후보로 등재 |

**3A 구현 레인은 이제 이 표의 값을 인스턴스화할 수 있다.** `3a/scope.md` 계약 정정 ① 이 정한 대로
운영 값 `EffectiveDatedPolicy` 인스턴스는 **한 커밋**으로 들어간다(3A 잔여 일괄 또는 3B 착수 전).
**「미확정」 칸은 인스턴스화하지 않는다** — 그 자리는 소유 `OPEN` 이 닫힌 뒤다.

**P-6 이 3B 를 둘로 가른다.** 공고 축(`NoticeSourcePort`)은 진행하고, **개찰·예비가격 축
(`OpeningResultSourcePort` 구현)만 ScsbidInfoService 참고자료 확보를 선행 조건**으로 둔다 —
3B `scope.md` 의 착수 전 결정 후보로 등재한다(이 레인의 경로가 아니라 문서 레인·3B 소관).
