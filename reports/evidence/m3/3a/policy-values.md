# 3A 정책 데이터 값 표 — **운영자 승인 2026-09-07** · **§1.7·§1.9 추가·승인 2026-09-08**

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
>
> **2026-09-08 추가 — 승인됐다(P-9·P-10·P-12).** 운영자가 **낙찰정보서비스 1.1 참고자료**와
> **공공데이터개방표준서비스 1.2 참고자료**를 확보해(P-6 의 선행 조건) M3/3B-2 curator 레인이
> **§1.7 을 「문서 미확보」에서 필드 계약 표로 채우고**, 그 레인이 중단된 뒤 세션 모델이
> **§1.9 「낙찰정보서비스 오퍼레이션 계약」을 신설했다**(저작 경계는 §1.9.6). 두 절의
> `authoritative` 칸은 §1.1~§1.6 과 같은 잣대(항목설명 문면에서 직접 읽은 것)로 썼고,
> **초기값 채택·masking 정책·오퍼레이션 계약은 2026-09-08 에 승인됐다** — 결정 정본은 **§6b**,
> 요청 표는 `_workspace/m3-3b2/01_curator_approval_request.md`. **P-1~P-6 의 승인 범위는 바뀌지
> 않았다.** 두 절의 「미확정」 칸은 여전히 인스턴스화 대상이 아니다.

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
| `authoritative` | 조달청 공식 문서 문면 | **문서 넷**을 인용한다. ① **참고자료 — 나라장터 입찰공고정보서비스**(2025, `koneps-openapi-reference`) — §1.1~§1.6·§2 의 근거. ② **활용가이드 — 나라장터 공공데이터개방표준서비스**(2019.06, `pps-openapi-guide`) — §3 의 근거이며 **대상 서비스가 다르다**(§3.1). ③ **참고자료 — 나라장터 낙찰정보서비스**(`koneps-scsbid-reference`, 2026-09-08 확보) — **§1.7·§1.9 의 근거**. ④ **참고자료 — 나라장터 공공데이터개방표준서비스**(`pps-opnstd-reference`, 2026-09-08 확보) — §1.7 의 「다른 서비스의 선언」 행과 §1.9 의 기간 상한 대조. 넷 다 `fixtures/manifest.yaml` 의 `official_documents` 가 경로·버전·SHA-256 을 고정한다 |
| `legacy-behavior` | 기존 Python 이 실제로 하는 것 | legacy `ed4b06c` (read-only). **정답 지위 없음** |
| `observed` | 운영 데이터·로그의 관찰 | **이 문서에 0건.** 운영 DB 접근 승인이 선행한다 |
| 미확정 | 어느 층으로도 서지 않는다 | 근거를 찾지 못했다는 판정이며 소유 `OPEN` 을 단다 |

**공식 문서가 주는 것과 주지 않는 것.** 참고자료는 오퍼레이션별 요청·응답 항목 명세를 담는다 —
항목명(영문/국문) · **항목크기** · **항목구분**(필수 `1` / 옵션 `0` / `1..n` / `0..n`) · 샘플데이터 ·
**항목설명**. 금액·율 항목의 항목설명이 단위와 과세를 문면으로 적는 자리다. 입찰공고정보서비스와
낙찰정보서비스가 각각 23 오퍼레이션이다. 활용가이드는 그 위에 **에러코드 표**를 준다(§3) —
**같은 표가 낙찰정보서비스·개방표준 참고자료에도 실려 있다**(§3.1 갱신).

문서에 **없는** 것은 **둘**이다 — `resultCode` 의 **범주**(코드는 확보됐고 재시도 가능/불가 분류가
없다) · 일시의 **출처 타임존**. 2026-09-07 판에서 셋째였던 **개찰·예비가격 항목 명세는 확보됐다**
(§1.7·§1.9).

**개찰 축 확보가 새로 드러낸 미선언 셋**은 그 자리에 남는다 — ① **낙찰정보서비스의 조회 기간 상한**
(문서가 창의 최대 길이를 적지 않는다. 개방표준서비스는 자기 서비스에 대해 적으며 전이하지 않는다) ·
② **개찰 축 금액의 과세**(최종낙찰금액·예정가격·기초예정가격 전부 `(원화,원)` 만 — `OPEN-REG-05` 와
같은 갈래) · ③ **업무구분 코드 전체 체계**(`OPEN-COL-03` 불변 — §5 각주 참조).

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
| `sucsfbidRate` | **최종낙찰률**(문서 국문 항목명) — *"최종낙찰금액/예정가격 × 100"* | **percent** | fraction (제수 `100`) | legacy 계약이 `0.5`~`1.0` 을 자체 선언 — **밴드 재선언이라 채택하지 않는다** | nullable(옵션) | 낙찰 목록 4종 + `…PPSSrch` 4종 — **§1.7.2 가 정본** | scale `authoritative`(낙찰정보서비스, 2026-09-08) · 밴드 **미확정** |

> **원문 scale 이 percent 라는 것이 이 절의 핵심이다.** 항목설명이 `(%)` 를 달고 샘플이 두 자리
> 정수부다. legacy 는 이 축을 **값 크기로 판별**한다(`rate_normalization.PERCENT_SCALE_THRESHOLD`)
> — `ADR 0002` D-4 · §1.4.1 이 금지한 경로이고 fixture `koneps-collection-010` 이 그 대체를 고정한다.
> **legacy 의 `FieldContract` 는 `sucsfbidLwltRate` 를 `Scale.FRACTION` 으로 선언**하는데, 그것은
> **정규화 뒤** 값을 재는 검증기의 관점이다. V2 계약은 **원문 scale**(percent)과 canonical scale
> (fraction)을 따로 나른다 — 한 슬롯에 두 관점을 담으면 어댑터가 어느 쪽을 읽어야 하는지 갈린다.
>
> **`sucsfbidRate` 의 concept 이 2026-09-08 에 정정됐다.** 2026-09-07 판은 이 키를 「사정률
> (낙찰가/예정가)」로 적었으나 낙찰정보서비스 참고자료의 국문 항목명은 **최종낙찰률**이고 항목설명이
> 분자를 **최종낙찰금액**으로 적는다 — 협상에 의한 계약에서는 투찰금액과 다른 값이다(§1.7.2).
> **의미가 바뀐 것이 아니라 이름과 분자가 문서로 확정된 것**이고, 밴드 불채택 결론은 불변이다.

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

### 1.7 개찰·예비가격 키 — **문서 확보·승인 2026-09-08(P-9)**

2026-09-07 판에서 이 절은 「문서 미확보」였다. 운영자가 **낙찰정보서비스 참고자료**
(`koneps-scsbid-reference`)와 **공공데이터개방표준서비스 참고자료**(`pps-opnstd-reference`)를 확보해
아래 17 키 가운데 **13 이 항목 명세를 얻었다.** 슬롯은 §1.1~§1.6 과 같고 축별로 나눈다.
`authoritative` 칸은 **항목설명·항목크기·항목구분 문면에서 직접 읽은 것만**이며, `effectiveFrom` 은
전 행 **해당 없음**(문서가 항목별 시행일을 적지 않는다), `expectedRange` 는 **밴드를 재선언하지
않는다**(§5.3 계승 규율 2).

**오퍼레이션 군 약칭**(§1.9 가 전수를 싣는다) — **낙찰 목록** `getScsbidListSttus{Thng,Cnstwk,Servc,Frgcpt}`
4종 · **낙찰 목록 검색** 같은 넷의 `…PPSSrch` 4종 · **개찰결과 목록**
`getOpengResultListInfo{Thng,Cnstwk,Servc,Frgcpt}` 4종 + `…PPSSrch` 4종 · **예비가격 상세**
`getOpengResultListInfo{…}PreparPcDetail` 4종.

#### 1.7.1 금액 키

| rawName | concept(문서 국문) | basis | unit / scale | vatTreatment | nullability | presentIn(문서) | 층 | provenance |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `sucsfbidAmt` | 최종낙찰금액 | `AWARD` | 원(KRW) / 정수, 항목크기 21 | **미선언 → `UNKNOWN`** | nullable(옵션) | 낙찰 목록 4 + 낙찰 목록 검색 4 | 단위·개념 `authoritative` · 과세 **미선언** | 항목설명 *"최종낙찰은 개찰순위 순서대로 협상등을 통해 최종 낙찰된정보를 의미하며 최종낙찰금액은 최종낙찰된 금액 (원화,원)으로 개찰완료된건에 대하여 제공"* |
| `plnprc` | 예정가격 | `YEGA` | 원(KRW) / 정수, 21 | **미선언 → `UNKNOWN`** | nullable(옵션) | 예비가격 상세 4 | 같음 | 항목설명이 예정가격을 정의한다 — *"… 낙찰자 선정의 기준이고 계약체결에 대한 최고 상한 금액을 의미함.  (원화,원)"* |
| `bsisPlnprc` | 기초예정가격 | **미확정** | 원(KRW) / 정수, 21 | **미선언 → `UNKNOWN`** | nullable(옵션) | 예비가격 상세 4 | 단위 `authoritative` · **basis·과세 미확정** | 항목설명은 *"기초예정가격(원화,원)"* 뿐이다. 행이 `compnoRsrvtnPrceSno`(복수예가순번)마다 반복되는 **복수예비가격 후보 하나**이고 확정 예정가격이 아니다 — `YEGA` 로 접으면 §1.1 `bssAmtPurcnstcst` 와 같은 **「부분을 전체 자리에」** 가 된다. `OPEN` 후보 |

> **같은 오퍼레이션이 `bssamt`(기초금액)도 준다** — 항목설명이 §1.1 의 기초금액조회 3종과 **같은
> 문면**이다(*"… 검토조정한 가격(원화,원)"*). §1.1 의 `bssamt` 행은 그대로 서고 `presentIn` 만
> 예비가격 상세 4종으로 넓어진다. 함께 `PrearngPrcePurcnstcst`(예정가격순공사비)가 있고 그 항목설명이
> `bssAmtPurcnstcst` 와 같은 *"… 및 이에 대한 부가가치세 합산금액"* 을 적는다 — **legacy 미소비이며
> 같은 「부분 개념」 주의가 걸린다.**

#### 1.7.2 율 키

| rawName | concept(문서 국문) | scale(원문) | canonical | expectedRange | nullability | presentIn(문서) | 층 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `sucsfbidRate` | 최종낙찰률 | **percent** (항목설명 `(%)`, 항목크기 18) | fraction (제수 `100`) | **미확정** — legacy 계약의 `0.5`~`1.0` 은 밴드 재선언이라 불채택 | nullable(옵션) | 낙찰 목록 4 + 낙찰 목록 검색 4 | scale·산식 `authoritative` · 밴드 미확정 |

> **분자가 「투찰금액」이 아니라 「최종낙찰금액」이다** — 항목설명이 *"예정가격대비 최종낙찰금액으로
> 최종낙찰금액/예정가격 * 100 으로 계산되며 개찰완료된 건에 대하여 제공 (%)"* 이라 적는다. 같은 문서가
> **다른 키로 투찰률을 따로 준다**(개찰완료 목록의 투찰률 — *"예정가격에 대한 투찰금액의 비율"*, §1.9).
> 협상에 의한 계약에서는 최종낙찰금액과 투찰금액이 다르므로 **두 율을 한 축으로 접지 않는다.**
>
> **샘플의 소수 자리가 오퍼레이션마다 다르다**(두 자리·세 자리, 한 곳은 `0`) — 자릿수는 계약이 아니고
> 항목크기 18 만 계약이다. **값을 이 문서에 싣지 않는다**(`numeric_discipline` 과 같은 이유 — 문서
> 샘플을 표에 박으면 그 수가 곧 경계가 된다).

#### 1.7.3 식별자·셈 키

| rawName | concept(문서 국문) | scale | 형식(문서) | nullability | presentIn(문서) | 층 |
| --- | --- | --- | --- | --- | --- | --- |
| `bidwinnrBizno` | 최종낙찰업체사업자등록번호 | identifier | 항목크기 **10**. 항목설명 *"협상이 완료 후 최종낙찰된 업체의 사업자등록번호임"* | **필수** | 낙찰 목록 4 + 낙찰 목록 검색 4 | 형태 `authoritative` · **취급은 미승인**(P-10) |
| `compnoRsrvtnPrceSno` | 복수예가순번 | 셈(순번) | 항목크기 6. 항목설명이 이름을 되풀이할 뿐 범위·기점을 적지 않는다 | 옵션 | 예비가격 상세 4 | 형태 `authoritative` · 범위 미확정 |
| `prtcptCnum` | 참가업체수 | 셈(건수) | 항목크기 6. 항목설명이 이름을 되풀이한다 | 옵션 | 낙찰 목록 4 + 낙찰 목록 검색 4 + 개찰결과 목록 8 | 형태 `authoritative` |

> **`bidwinnrBizno` 가 「필수」라는 것이 P-10 의 무게중심이다.** 사업자등록번호를 **옵션으로 빼는 선택이
> 문서에 없다** — 낙찰 목록을 부르면 그 열이 온다. 그래서 masking 정책은 「받지 않기」가 아니라
> 「받은 것을 어디까지 나르는가」의 문제다.
>
> **셈 축의 scale 토큰이 3A 어휘에 없다** — 현 `FieldScale` 에 `WON_INTEGER`·`FRACTION`·`PERCENT`·
> `IDENTIFIER`·`DATETIME_NO_ZONE`·`OPAQUE_TEXT`·`DELIMITED_LIST` 뿐이다. **이 표는 어휘를 지어내지
> 않는다**(2026-09-01 규칙) — 필요한 확장 목록은 아래 「P-9 채택이 요구하는 3A 확장」이 싣는다.

#### 1.7.4 일시 키 — `sourceZone` 축

| rawName | concept(문서 국문) | 형식(문서) | nullability | `sourceZone` | presentIn(문서) | 층 |
| --- | --- | --- | --- | --- | --- | --- |
| `rlOpengDt` | 실개찰일시 | *"실제 개찰일시  “YYYY-MM-DD HH:MM:SS”"*, 항목크기 19 | 옵션 | **미선언** | 낙찰 목록 4 + 낙찰 목록 검색 4 + 예비가격 상세 4 | 형식·개념 `authoritative` · zone **미확정** |
| `fnlSucsfDate` | 최종낙찰일자 | `YYYY-MM-DD`(**일자, 시각 없음**), 항목크기 10 | 옵션 | 해당 없음(일자 축) | 낙찰 목록 3 + 낙찰 목록 검색 3 — **외자 2종은 대문자 `FnlSucsfDate`** | 형식 `authoritative` · **표기 변형이 문서 안에 있다** |

> **`rlOpengDt` 가 §4 의 앵커 물음에 답한다.** §1.4 는 입찰공고정보서비스의 `opengDt` 가 *"집행관이
> 개찰을 수행할 수 있는 시작일시이며 … 실제 개찰을 수행한 시간을 의미하지 않음"* 이라 적는 것을
> 등재했다. 낙찰정보서비스는 그 실제 시각을 **`rlOpengDt`(실개찰일시)** 로 따로 준다 — **age-gate 의
> 앵커 후보가 문서로 생겼다.** 다만 **옵션**이라 부재를 다루는 규칙이 함께 필요하다(§4 각주 갱신).
>
> **같은 이름 `opengDt` 의 항목설명이 서비스마다 다르다.** 낙찰정보서비스의 개찰결과 목록에서는
> *"조달업체가 제출한 입찰서를 개찰하는 일시  “YYYY-MM-DD HH:MM:SS”"* 이고 **항목구분이 필수**다 —
> 입찰공고정보서비스판의 「시작일시이며 실제 개찰 시각이 아니다」 단서가 **없다.** 두 선언을 한 행에
> 접지 않는다(§1.4 는 공고 축, 이 절은 개찰 축).
>
> **`fnlSucsfDate` / `FnlSucsfDate` 는 문서 안의 표기 변형이다.** 물품·공사·용역 오퍼레이션은 소문자
> `f`, 외자 2종(`…Frgcpt`·`…FrgcptPPSSrch`)은 대문자 `F` 로 싣는다. 항목크기·항목구분·항목설명은 같다.
> **어느 쪽이 실제 응답 키인지는 문서가 판정하지 않는다** — 대소문자 무시 조회로 접으면 §5.3 규율 1
> (미지 필드 리포트)이 무력해지므로 **두 표기를 각각 등재하고 관측으로 좁힌다**(`insufficient-evidence`).

#### 1.7.5 코드·라벨·플래그·복합 키

| rawName | concept(문서 국문) | 어휘·형식(문서) | nullability | presentIn(문서) | 층 |
| --- | --- | --- | --- | --- | --- |
| `drwtYn` | 추첨여부 | *"추첨여부(Y/N)"*, 항목크기 1 | **필수** | 예비가격 상세 4 | `authoritative` |
| `progrsDivCdNm` | 진행구분코드명 | *"진행구분이 유찰, 개찰완료, 재입찰로 구분 됨"* — **3값 열거**, 항목크기 4 | **필수** | 개찰결과 목록 8 | `authoritative` |
| `opengCorpInfo` | 개찰업체정보 | **`^` 구분 5성분** — 업체명 · 사업자번호 · 대표자명 · 투찰금액 · 투찰율. 항목크기 500 | **필수** | 개찰결과 목록 8 | 형식 `authoritative` · **취급은 미승인**(P-10) |
| `bidwinnrNm` | 최종낙찰업체명 | 항목크기 200. 항목설명 *"최종낙찰된 업체의 명으로 개찰완료된 건에 대하여 제공"* | **필수** | 낙찰 목록 4 + 낙찰 목록 검색 4 | 형태 `authoritative` · **취급은 미승인**(P-10) |

> **`opengCorpInfo` 는 한 항목에 세 종류의 식별 정보를 담고 그것이 필수다.** 항목설명이 세 갈래를
> 적는다 — ① 단일 낙찰자면 *"업체명과 사업자번호, 대표자명, 투찰금액, 투찰율을 보여줌"* ② 다수
> 낙찰자면 *"”낙찰예정자 다수”와 개찰순위 1위의 투찰금액과 투찰율"* ③ *"협상에 의한 계약일 경우는
> 투찰금액,투찰율 안나옴"*. **성분 수가 경우마다 다르고 첫 성분이 업체명이 아닐 수 있다** — 파싱은
> 성분 수를 계약으로 두지 못한다. `cnstrtnAbltyEvlAmtList`(§1.5)와 같은 `DELIMITED_LIST` 축이나
> **`[...]` 레코드 감싸기가 없고 구분자만 `^` 다.**
>
> **대표자명은 사업자 식별자가 아니라 개인 성명이다.** legacy 는 이 문자열을 다섯 성분으로 갈라
> 대표자명까지 보존한다(`openapi.parse_openg_corp_info`). **P-10 의 선택지가 사업자번호만 다루면 이
> 자리가 남는다** — 그래서 P-10 은 성명 축을 함께 묻는다.
>
> **문서가 선언하는 개인·사업자 식별 항목은 legacy 소비 17 키보다 많다.** 낙찰 목록이
> 최종낙찰업체대표자명·최종낙찰업체주소·최종낙찰업체전화번호(*"핸드폰번호는 “*”로 표기"*)·
> 최종낙찰업체담당자를 함께 싣고, 개찰완료 목록은 투찰업체사업자등록번호(**필수**)·투찰업체명·
> 투찰업체대표자명을 싣는다(§1.9). **legacy 가 안 읽는다는 사실이 안 온다는 뜻이 아니다** —
> 응답에 실려 오므로 P-10 은 「저장」뿐 아니라 **「원문 보존(`sourceText`)·로그·evidence」** 층까지
> 정해야 한다.

#### 1.7.6 다른 서비스의 선언 · 미등재 키

| rawName | 상태 | 근거 | 층 |
| --- | --- | --- | --- |
| `opengDate` | **다른 서비스의 선언** — 개찰**일자**(시각 없음), 항목크기 10, 항목설명 *"조달업체가 제출한 입찰서를 개찰하는 일자"*. 입찰공고정보·낙찰정보 오퍼레이션에서 필수, 계약정보에서 옵션 | `pps-opnstd-reference`(**PubDataOpnStdService**) | `authoritative` **단, 대상 서비스가 다르다** — §3.1 이 활용가이드에 한 것과 같이 **ScsbidInfoService 계약으로 승격하지 않는다.** legacy 는 이 키를 `opengDt` 다음 폴백으로 읽는다 |
| `bidOpenDt` | **어느 문서에도 없다** | legacy 개찰일시 폴백 사슬의 **셋째** 키(`opengDt` → `opengDate` → `bidOpenDt`) | 미확정(**미등재 키**) — §5.3 규율 1 의 「미지 필드」 |
| `ntceNm` | **어느 문서에도 없다** | legacy 공고명 폴백의 **둘째** 키(`bidNtceNm` → `ntceNm`) | 같음 |
| `prcmBsneSeCd` | **어느 문서에도 없다** — 사전 대조의 「개방표준에만 있음」을 **정정한다** | 개방표준 참고자료에 문자열로 한 번 나오지만 그 자리는 계약정보 오퍼레이션의 계약정보URL **샘플 값 안의 질의 파라미터**이고 항목 행이 아니다. legacy 는 이 키를 업무구분 폴백으로 읽고 **개찰결과 그리드 경로에서는 §5 의 코드→라벨 표를 이 키에 적용한다** | 같음 |

> **`prcmBsneSeCd` 의 정정이 §5 의 긴장을 해소한다.** §5 는 legacy 의 코드→라벨 표(4행)와 §1.5 의
> `bsnsDivNm` 열거(4값)가 다른 축이라고 적었다. 이제 셋째 사실이 붙는다 — **legacy 표가 적용되는 키
> (`prcmBsneSeCd`)는 어느 조달청 API 문서의 응답 항목도 아니다**(웹 질의 파라미터로 보인다). 그리고
> 개방표준 참고자료는 **또 다른 키**(업무구분코드, §1.9)로 코드 넷을 열거하는데 그 값 집합이 legacy 표와
> 다르다. **세 축을 한 자리에 접지 않는다**(COL-08). `OPEN-COL-03` 은 열린 채이고, 업무구분 승인 항목
> **P-7** 은 §6b 에서 승인됐다(2026-09-08) — 세 축을 접지 않는다는 결론은 그 행이 싣는다.

#### P-9 채택이 요구하는 3A 확장 — **이 표는 어휘를 지어내지 않는다**

§1.1~§1.6 의 승인분(P-1)은 3A `FieldContractRow` 열로 옮겨졌다. 위 13 행을 같은 방식으로 옮기려면
**3A 어휘·구조에 없는 자리가 넷** 있다. 값이 아니라 **자리**의 부재라 이 표가 정할 수 없다.

| # | 없는 자리 | 이 표의 어느 행이 요구하는가 |
| --- | --- | --- |
| ① | `FieldConcept` 에 개찰 축 개념 토큰이 없다(최종낙찰금액 · 최종낙찰률 · 예정가격 · 기초예정가격 · 사업자등록번호 · 참가업체수 · 복수예가순번 · 실개찰일시 · 최종낙찰일자 · 추첨여부 · 진행구분 · 개찰업체정보) | 1.7.1~1.7.5 전 행 |
| ② | `FieldScale` 에 **셈(정수 건수·순번)** 축이 없다 — `WON_INTEGER` 는 금액, `IDENTIFIER` 는 제로패딩 식별자다 | `prtcptCnum` · `compnoRsrvtnPrceSno` |
| ③ | `Basis` 에 복수예비가격 후보 축이 없다(`YEGA` 는 확정 예정가격) | `bsisPlnprc` — 다만 이 행은 basis 자체가 **미확정**이라 ① 과 성질이 다르다 |
| ④ | 필드 계약 행 helper 가 `presentIn` 을 `NOTICE_LIST` 로 고정한다. `SourceEndpoint` 에 `OPENING_RESULT` 는 있으나 **낙찰 목록·개찰결과 목록·예비가격 상세 세 군을 한 토큰이 덮는다** — 위 표의 `presentIn` 열이 오퍼레이션 군을 구별하는데 계약은 그 구별을 나르지 못한다 | 1.7.1~1.7.5 전 행 |

**P-9 는 값의 채택을 묻고 ①~④ 는 그 채택의 비용이다.** 어휘 승인은 3A 계약 타입 확정 뒤라는 규칙
(2026-09-01)이 그대로 적용되므로, **③ 을 뺀 ①②④ 는 3A 좁은 확장 한 커밋**이 되고 그 범위는 3B-2
slice 계약이 정한다.

### 1.8 셈

| 축 | 수 |
| --- | --- |
| legacy `KNOWN_FIELDS` 키 | 60 |
| 그중 **입찰공고정보서비스** 참고자료의 응답 항목 명세에 있는 키(§1.1~§1.6) | 37 |
| 그중 **낙찰정보서비스** 참고자료의 응답 항목 명세에 있는 키(§1.7.1~§1.7.5, 2026-09-08 확보) | **13** |
| 그중 **다른 서비스**(개방표준) 참고자료에만 있는 키 — `opengDate` | 1 |
| 그중 **어느 문서에도 없는 키** — §1.1 `bssAmt`·`presmptAmt` · §1.3 `bidPbancNo` · §1.5 `indstrytyCd`·`indstrytyNm`·`prtcptLmtRgnNm`(요청 파라미터로만 선언) · §1.7.6 `bidOpenDt`·`ntceNm`·`prcmBsneSeCd` | **9** |
| 이 문서들에만 있고 legacy 가 소비하지 않는 키(신설 후보 예: `usefulAmt`·`reNtceYn`·`sucsfbidMthdNm`·`cnstrtnAbltyEvlAmtList`·`rsrvtnPrceFileExistnceYn`·`totRsrvtnPrceNum`·`drwtNum`·`bssamtBssUpNum`·`compnoRsrvtnPrceMkngDt`) | 전수 등재는 3A·3B-2 범위 밖 — 아래 각주가 개찰 축의 신설 후보만 짚는다 |

`37 + 13 + 1 + 9 = 60`. **2026-09-07 판의 「문서에 없는 키 23」이 「13 + 1 + 9」로 갈라진 것이 이
갱신의 전부**이고 legacy 소비 키 집합 자체는 변하지 않았다.

**§5.3 은 「소비되는 모든 키에 계약이 필수」다.** §1.1~§1.6 이 37 건을 덮고, §1.7 이 13 건에 문서
근거를 붙였다 — 다만 **P-9 승인과 「P-9 채택이 요구하는 3A 확장」 ①②④ 전까지는 계약이 없으므로 그
13 건도 여전히 소비 불가**다(계약 없는 키는 소비 함수에 들어가지 못한다 — 3A ④). 나머지 10 건
(`opengDate` + 미등재 9)은 **미지 필드로 리포트**되는 자리다.

> **개찰 축의 신설 후보 하나가 COL-03 에 바로 걸린다** — 개찰결과 목록의 **예비가격파일존재여부**
> (`rsrvtnPrceFileExistnceYn`, 항목크기 1, `Y`/`N`, **필수**)다. COL-03 acceptance 의 「예비가격이 이미
> 있으면 상세 호출 0회」가 지금까지 **관측된 저장 상태**로만 판정됐는데, 문서가 **목록 응답에 그 신호를
> 필수로 싣는다.** legacy 는 이 키를 소비하지 않는다(`KNOWN_FIELDS` 밖). 3A ⑪ 의 조회 가치 술어가 이
> 입력을 받을지는 **3B-2 계약의 물음**이고 이 표는 존재와 형태만 준다.

---

### 1.9 낙찰정보서비스 오퍼레이션 계약 — **문서 확보·승인 2026-09-08(P-12)**

**필드 계약만으로는 개찰 축을 부를 수 없다.** §1.7 이 「어떤 값이 오는가」를 주고 이 절이 「무엇을
보내야 오는가」를 준다. 세션 모델이 §1.7 뒤 이어 쓴다(curator 레인 중단, 아래 「저작 경계」).

**서비스 셋의 엔드포인트 접두가 다르다** — 낙찰정보 `…/1230000/**as**/ScsbidInfoService` · 입찰공고정보
`…/1230000/**ad**/BidPublicInfoService` · 개방표준 `…/1230000/**ao**/PubDataOpnStdService`. 한 host 아래
세 접두라 base URI 를 서비스별 정책 값으로 둔다(오퍼레이션 경로까지 포함 — 3B 관례).

#### 1.9.1 오퍼레이션 23 — 군과 소비 여부

| 군 | 오퍼레이션 | 번호 | legacy 호출 | §1.7 의 어느 키를 주는가 |
| --- | --- | --- | --- | --- |
| **낙찰 목록** | `getScsbidListSttus{Thng,Cnstwk,Servc,Frgcpt}` | 1~4 | **부른다** | `sucsfbidAmt`·`sucsfbidRate`·`rlOpengDt`·`fnlSucsfDate`·`prtcptCnum`·`bidwinnrNm`·`bidwinnrBizno` |
| **낙찰 목록 검색** | 같은 넷 + `PPSSrch` | 16~19 | 안 부른다 | 같음 + 요청에 `bizno`·`intrntnlDivCd` (1.1 추가) |
| **개찰결과 목록** | `getOpengResultListInfo{Thng,Cnstwk,Servc,Frgcpt}` | 5~8 | **부른다** | `opengDt`·`prtcptCnum`·`opengCorpInfo`·`progrsDivCdNm`·`rsrvtnPrceFileExistnceYn` |
| **개찰결과 목록 검색** | 같은 넷 + `PPSSrch` | 20~23 | 안 부른다 | 같음 |
| **예비가격 상세** | `getOpengResultListInfo{…}PreparPcDetail` | 9~12 | **부른다** | `plnprc`·`bssamt`·`bsisPlnprc`·`compnoRsrvtnPrceSno`·`drwtYn`·`rlOpengDt` + 신설 후보 `totRsrvtnPrceNum`·`drwtNum`·`bssamtBssUpNum`·`compnoRsrvtnPrceMkngDt`·`PrearngPrcePurcnstcst` |
| **개찰완료** | `getOpengResultListInfoOpengCompt` | 13 | **안 부른다** | §1.7 키 없음 — **투찰 축을 여기서만 준다**(아래 1.9.4) |
| **유찰** | `getOpengResultListInfoFailing` | 14 | 안 부른다 | `nobidRsn`(유찰사유) |
| **재입찰** | `getOpengResultListInfoRebid` | 15 | 안 부른다 | `opengDt`(필수) |

문서 전수는 23 이고 legacy 가 부르는 것은 **세 군 12** 다. 나머지 11 은 이름·요청 계약만 등재한다.

#### 1.9.2 `inqryDiv` — **오퍼레이션 군마다 값 의미가 다르다**

**같은 이름의 한 자리 코드가 군마다 다른 축을 뜻한다.** 전역 상수로 두면 조용히 다른 축을 조회한다.

| 군 | `1` | `2` | `3` | `4` | 기간 항목 필수 조건(문면) |
| --- | --- | --- | --- | --- | --- |
| 낙찰 목록(1~4) | 등록일시 | 공고일시 | 개찰일시 | 입찰공고번호 | `inqryBgnDt`·`inqryEndDt` 는 *"조회구분 1,2,3일 경우 필수"* · `bidNtceNo` 는 *"조회구분 4인 경우 필수"* |
| 낙찰 목록 검색(16~19) | 공고게시일시 | 개찰일시 | **입찰공고번호** | — | *"'1'인 경우 공고게시일시 필수, '2'인 경우 개찰일시 필수"* · `bidNtceNo` 는 *"조회구분이 '3'인 경우 필수"* |
| 개찰결과 목록(5~8) | **입력일시** | 공고일시 | 개찰일시 | 입찰공고번호 | 낙찰 목록과 같은 문면 |
| 개찰결과 목록 검색(20~23) | 공고일시 | 개찰일시 | **입찰공고번호** | — | `bidNtceNo` 는 조회구분 `3` 에서 필수 |
| **예비가격 상세(9~12)** | 입력일시 | **입찰공고번호** | — | — | `inqryBgnDt`·`inqryEndDt` 는 *"조회구분이 1일 경우 필수"* |
| 개찰완료·유찰·재입찰(13~15) | **`inqryDiv` 자체가 없다** | | | | `bidNtceNo` **필수** + `bidNtceOrd`·`bidClsfcNo`·`rbidNo` 옵션 |

> **legacy 두 호출의 판정이 갈린다.** ① 예비가격 상세의 `inqryDiv="2"` + `bidNtceNo`(단건)는 문서와
> **일치**한다 — 그 군에서 `2` 가 입찰공고번호다. ② 낙찰 목록 스윕의 `inqryDiv="1"` 은 값은 문서에
> 있으나 legacy 주석의 *"등록일시 구간 조회"* 가 낙찰 목록 군에서는 맞고 **개찰결과 목록 군에서는
> 「입력일시」**다 — 두 군에 같은 주석을 쓰면 어긋난다. 운영 피해 기록이 없어 `insufficient-evidence`
> 로 등재하고 판정하지 않는다(`data-extract.md` §6).
>
> **개찰일시 축으로 걷는 경로가 문서로 열린다.** 낙찰 목록에서 `inqryDiv=3`, 개찰결과 목록에서 `3`,
> 검색군에서 `2` 가 개찰일시다. 「어느 날 개찰된 건」을 모으는 것이 개찰 축 수집의 자연스러운 축인데
> legacy 는 등록일시로 걷는다 — **어느 축으로 걸을지는 3B-2 계약의 물음**(D-3B2-3)이고 이 표는 선택지가
> 문서에 있다는 사실만 준다.

#### 1.9.3 공통 요청·응답 항목과 문서가 적는 한계

- **요청 공통**: `numOfRows`·`pageNo`(항목크기 4) · `ServiceKey`(400, **필수**) · `type`(4, 옵션, *"'json' 으로 지정"*).
  `numOfRows`·`pageNo` 의 항목구분이 **오퍼레이션마다 필수(1)와 옵션(0)으로 갈린다**(예: 낙찰 목록 물품은 필수,
  개찰결과 목록 공사·용역·외자는 옵션) — 문서 내부 불일치다. **둘을 항상 보내면 양쪽을 만족**하므로 어댑터는
  항상 보낸다(값은 정책).
- **응답 봉투**: `resultCode`(2, 필수) · `resultMsg`(50, 필수) · `numOfRows`·`pageNo`·`totalCount`(4, 필수).
  §1.6 의 입찰공고정보서비스 봉투와 **같은 형태**다 — 3B 의 envelope 검증이 그대로 선다.
- **문서가 적는 운영 한계**: 전 23 오퍼레이션이 **최대 메시지 4000 bytes · 평균 응답 500 ms · 초당 최대 30 tps**.
  `authoritative` 이며 `OPEN-COL-05`(rate 정책값)의 상한 근거다. **4000 bytes 는 `numOfRows` 상한과 직결**된다 —
  legacy 가 목록 999·상세 전용 페이지 크기를 쓰는데 문서 한계와 대조하면 한 페이지가 초과할 수 있다(관측 필요).
- **식별자 항목크기가 서비스·군마다 다르다**: `bidNtceNo` 는 낙찰 목록군 **40** · 개찰결과·예비가격군 **11** ·
  license-limit(입찰공고정보) **40**. `bidNtceOrd` 는 요청에서 **2**(13·15)와 **3**(14), 응답에서 **3**.
  **제로패딩 식별자라 크기 선언이 계약**인데 문서가 갈린다 — 계약은 크기를 최댓값으로 두지 않고 **문자열
  원문 보존**(3A `NoticeRound` 관례)으로 회피한다. 크기 불일치는 `insufficient-evidence`.

#### 1.9.4 legacy 가 안 부르는 오퍼레이션이 여는 것 — **COL-03 의 추첨번호**

`getOpengResultListInfoOpengCompt`(13)만이 **투찰자별 행**을 준다 — `opengRank`(개찰순위) ·
`prcbdrBizno`(투찰업체사업자등록번호, **필수**) · `prcbdrNm` · `prcbdrCeoNm`(대표자명) · `bidprcAmt`(투찰금액) ·
`bidprcrt`(투찰률, *"예정가격에 대한 투찰금액의 비율"*) · `drwtNo1`·`drwtNo2`(추첨번호) · `bidprcDt` ·
1.1 추가분 `bidPrceEvlVal`·`techEvlVal`·`techEvlNaturVal`·`totalEvlAmtVal`. 요청은 `bidNtceNo` **필수**의
**단건 조회**다(창 스윕 불가).

> **`COL-03` 문면이 「복수예비가격 15개와 **추첨번호**를 확보한다」인데, 추첨번호(`drwtNo1`·`drwtNo2`)를
> 주는 오퍼레이션은 예비가격 상세가 아니라 개찰완료(13)다.** 예비가격 상세는 `drwtYn`(추첨여부)·
> `drwtNum`(추첨횟수)까지다. legacy 는 13 을 부르지 않으므로 **추첨번호의 수집 출처가 legacy 에 없다** —
> 투찰 축(`bidprcAmt`·`bidprcrt`)도 같다(legacy 는 `opengCorpInfo` 문자열을 갈라 얻는다, §1.7.5).
> **이것은 신설 능력이지 이식이 아니다** — 3B-2 가 13 을 부를지는 계약의 물음(D-3B2-9 후보)이고,
> `prcbdrBizno` 가 **필수**라 P-10 masking 이 그 축까지 덮어야 한다.
>
> **`opengCorpInfo` 파싱을 대체할 구조적 경로가 문서에 있다.** §1.7.5 가 적은 「`^` 5 성분, 경우마다 성분 수
> 다름」의 위험은 13 의 **필드 분리된 행**으로 회피된다 — 다만 호출이 공고당 1회다(쿼터 비용, COL-03 과 같은 축).

#### 1.9.5 자격 원문 서브콜 — **다른 서비스(입찰공고정보) 의 오퍼레이션**

3B-2 ③ 이 부르는 `getBidPblancListInfoLicenseLimit`(입찰공고정보서비스 참고자료 오퍼레이션 15,
`koneps-openapi-reference`)의 계약이다. **낙찰정보서비스가 아니므로 이 절의 tps·봉투와 별개**이나
문서가 같은 값을 적는다(4000 bytes · 500 ms · 30 tps).

| 축 | 문면 |
| --- | --- |
| 요청 `inqryDiv` | 필수, *"1:등록일시 , 2.입찰공고번호"* — **이 서비스에서는 두 값뿐** |
| 요청 `bidNtceNo` | 항목크기 40, 옵션 — *"(조회구분이 '2'인 경우 필수)"* |
| 요청 `bidNtceOrd` | 항목크기 3, 옵션 — *"(조회구분이 2인 경우 필수)"*, 샘플 `000` |
| 응답 | `bidNtceNo`(40, 필수) · `bidNtceOrd`(3) · `lmtGrpNo`(제한그룹번호 3) · `lmtSno`(제한순번 6) · `lcnsLmtNm`(면허제한명 200, *"면허제한코드/면허제한명"*) · `permsnIndstrytyList`(허용업종목록 4000, **`0..n`**, *"[허용업종명1/허용업종코드1],[허용업종명2/…]"*) · `rgstDt`(19, 필수) · `bsnsDivNm`(30) · `indstrytyMfrcFldList`(주력업종분야목록 4000) |

> **legacy 의 실측이 문서로 확인된다.** legacy 주석은 *"필수 파라미터(실측): inqryDiv=2 + bidNtceNo +
> bidNtceOrd(차수). bidNtceOrd 누락 시 resultCode 08"* 이라 적었다 — 문서가 **`bidNtceOrd` 를 조회구분 2 의
> 필수**로 선언하므로 그 실측은 `authoritative` 로 승격한다. `08`(필수값 입력 에러)이 그 귀결이다(§3.2).
> **`R-QUAL-05`(차수 `int` 변환으로 1차 공고 자격 상실)의 뿌리도 여기다** — 샘플이 `000` 이다.
>
> **「제한 없음」과 「수집 실패」의 구분(COL-04 acceptance 넷째)이 문서로 선다.** `permsnIndstrytyList` 의
> 항목구분이 **`0..n`**(0건 또는 복수건)이라 **빈 목록이 계약 위반이 아니다.** 업종제한 여부는 공고 목록의
> `indstrytyLmtYn`(항목크기 1, 옵션, 샘플 `Y`, *"해당 공고 입찰 시 업종(면허)제한을 두는지의 여부"*)로 오므로
> **서브콜 0회 판단의 입력이 목록 응답에 이미 있다**(3B-2 ④·D-3B2-6 의 근거).

#### 1.9.7 실측 대조 — 2026-09-08 (운영자 승인 아래 실제 호출)

`OPEN-3B2-TARGETED-OPENING-QUERY` 를 닫기 위해 운영자 승인(2026-09-08) 아래 조달청 OpenAPI 를
**읽기 전용으로** 호출했다. **값은 저장·인용하지 않는다**(개찰 축 응답에 사업자등록번호·대표자명이
실려 온다 — P-10 (a) 규율을 탐침 자신에게 적용했다). 서비스 키는 저장소 밖 파일에서만 읽었고
명령·출력·이 문서 어디에도 없다. 아래는 **구조 관측**이며 **전수가 아니라 표본**이다.

| 관측 | 결과 | 표본 |
| --- | --- | --- |
| **표적조회** — 개찰결과 목록에 `inqryDiv=4` + `bidNtceNo` | **동작한다**. `resultCode=00`, `totalCount=1` — 그 공고 한 건만 온다 | 1회 |
| **개찰완료 단건** — `getOpengResultListInfoOpengCompt` + `bidNtceNo` | **동작한다**. 응답 항목 20 — §1.9.4 가 적은 축(개찰순위·투찰업체 식별자 셋·투찰금액·투찰률·추첨번호 둘·투찰일시·평가점수 넷)이 **전부 실재** | 1회 |
| **예비가격 상세** 응답 항목 이름 | 18개이고 **8건 전부 동일**. §1.9.1 이 적은 축과 일치 | 8건 |
| **순번(`compnoRsrvtnPrceSno`) 공백** | **실재한다** — 8건 중 2건. **다만 `totRsrvtnPrceNum`(총예가건수)이 1 일 때만** 났고, **15행 건 4건은 순번이 전부 채워져** 있었다 | 8건 23행 |
| `totRsrvtnPrceNum` 대 실제 행 수 | 행이 온 6건 모두 **일치**(15·15·15·15·1·1) | 6건 |
| **`rsrvtnPrceFileExistnceYn='Y'` 대 상세 0행** | **어긋나는 사례 8건 중 2건** — 목록이 `Y` 인데 상세 조회가 `totalCount=0` | 8건 |

**읽어야 할 것 둘.**

1. **§1.7.3 의 「옵션」 선언이 실측으로 뒷받침되고, 그 조건이 좁혀졌다** — 순번이 비는 것은
   **단수 예가**(총예가건수 1)일 때이고 그때는 행이 하나라 정체성 모호가 없다. **복수예비가격
   15행은 순번이 있다** — `COL-03` 이 요구하는 축은 관측 범위에서 온전하다.
2. **`rsrvtnPrceFileExistnceYn` 은 「상세 행이 있다」의 신뢰할 수 있는 지표가 아니다.** §1.8 각주가
   그 키를 `COL-03` 조회 가치 술어의 입력 후보로 짚었는데, **이 관측이 그 후보를 약화시킨다** —
   플래그를 믿고 상세를 건너뛰면 있는 행을 놓치고, 플래그를 믿고 호출하면 빈 응답에 쿼터를 쓴다.
   `OPEN-3E-RESERVE-FLAG-MISMATCH`(capability-map §14.3)가 정본이다.

**관측의 지위**: 층은 `observed` 다(§0). 문서 문면(`authoritative`)을 바꾸지 않고, 문서가 말하지
않는 것(순번이 비는 **조건**·플래그의 신뢰도)에 대해서만 관측이 말한다. 표본이 작으므로
**「항상 그렇다」로 승격하지 않는다**.

#### 1.9.6 저작 경계 — 이 절을 누가 썼는가

§1.7(§1.7.1~§1.7.6·§1.8 갱신·머리·§0)은 **M3/3B-2 curator 레인**이 썼고 `official_documents` 등재
커밋도 그 레인이다. 그 레인이 사용량 한도로 중단된 뒤 **§1.9 와 §6b·「승인 대기」 절은 세션 모델이 직접**
썼다(운영자 지시 2026-09-04 「기획 문서는 세션 모델 단독」과 같은 자리). 세션 모델은 §1.7 의 주장 가운데
`presentIn`·항목구분·`inqryDiv` 문면·`prcmBsneSeCd` 정정을 **문서 원문 재추출로 대조**했고 어긋난 것은
없었다. **문서 샘플의 사업자등록번호·업체명·전화번호는 어느 절에도 인용하지 않았다**(`data-extract.md` §7).

### 1.10 첨부 문서 키 — **P-8, 승인 대기 (2026-09-08 작성)**

3C 가 첨부를 취득하려면 URL 이 어느 키로 오는지의 계약이 필요한데 §1.1~§1.9 에 그 자리가 없었다
(`OPEN-3C-ATTACHMENT-FIELD-CONTRACT`). 출처는 **입찰공고정보서비스 참고자료**(`koneps-openapi-reference`)
이며 §1.1~§1.6 과 같은 잣대로 읽는다.

| rawName | concept(문서 국문) | scale | 항목크기 | nullability | presentIn | 층 |
| --- | --- | --- | --- | --- | --- | --- |
| `ntceSpecDocUrl1` … `ntceSpecDocUrl10` | 공고규격서URL 1~10 | `OPAQUE_TEXT`(URL) | 800 | **옵션** | 공고 목록 오퍼레이션 다수 | `authoritative` |
| `ntceSpecFileNm1` … `ntceSpecFileNm10` | 공고규격파일명 1~10 | `OPAQUE_TEXT` | 400 | **옵션** | 같음 | `authoritative` |

**읽어야 할 것 넷.**

1. **슬롯이 정확히 열이고 문서가 그 이유·초과 시 동작을 적지 않는다.** 첨부가 열한 개인 공고를
   어떻게 표현하는지 **문서에 없다** — 잘리는지, 다른 오퍼레이션이 나머지를 주는지 알 수 없다.
   신설 `OPEN-3C-ATTACHMENT-SLOT-OVERFLOW`(`capability-map.md` §14.3)가 그 미지를 나른다. 열 칸을 순회하는 구현은
   **「열 개가 전부다」를 가정하지 않는다**는 것을 주석·test 로 남겨야 한다.
2. **URL 과 파일명은 인덱스로 짝지어지지만 문서가 그 짝을 선언하지 않는다.** 둘 다 옵션이라
   한쪽만 오는 경우가 계약 위반인지 정상인지 모른다. **짝이 어긋나면 조용히 넘기지 말고
   회계에 남긴다**(3B-2 의 미지 필드 회계와 같은 결).
3. **파일명 확장자가 형식의 유일한 사전 신호다.** 문서 샘플은 `.hwp` 다 — 3C 가 D-3C-4 (a) 로
   플레인 텍스트 + PDF 텍스트 층만 다루므로 그 밖은 `Uncertain(UnsupportedFormat)` 회계로 간다.
   **확장자를 형식의 근거로 삼지 않는다**(내용과 다를 수 있다) — 사전 분기 힌트일 뿐이고 판정은
   내용이 한다. `OPEN-3C-DOC-FORMATS` 가 분포를 기다리는 자리다.
4. **URL 샘플 안의 질의 파라미터를 응답 항목으로 읽지 않는다.** 샘플 URL 은 `bidPbancNo`·
   `bidPbancOrd`·`fileSeq`·`prcmBsneSeCd` 를 쿼리로 싣는데, 그 넷 가운데 `bidPbancNo`·
   `prcmBsneSeCd` 는 **이 문서의 어느 응답 항목 명세에도 없다**(§1.8 의 「어느 문서에도 없는 키」에
   그대로 남는다). §1.7.6 이 `prcmBsneSeCd` 에 대해 한 정정과 **같은 갈래**다 — URL 문자열 안에
   이름이 보이는 것은 등재 근거가 아니다.

> **P-8 이 묻는 것**: 위 표를 3A 필드 계약 정책 데이터의 초기값으로 채택하는가(P-1 의 첨부 축 판).
> 채택하면 `KONEPS_COLLECTION_POLICY` 에 스무 행이 늘고, 3C 의 `AttachmentUrl` 이 **등재된 계약**에서
> 값을 얻는다(지금은 3C test 가 메커니즘만 증명하고 실제 수집 관측에서 만들지 못한다).
> **값을 바꾸는 결정이 아니라 등재 결정**이다 — 표의 모든 칸이 문서 문면 인용이다.

---

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

---

## 6b. 승인 결과 — 2026-09-08 (개찰 축 + P-7)

**P-7 · P-9 · P-10 · P-12 가 승인됐다** — 요청 표는 `_workspace/m3-3b2/01_curator_approval_request.md`,
운영자 결정은 **전부 추천안 (a)** 다. 아래가 이 결정의 정본이다.

| # | 승인 대상 | 층 | **결정** |
| --- | --- | --- | --- |
| **P-7** | **업무구분 문서 열거값**(`bsnsDivNm` = 물품·용역·공사·외자)을 P-1~P-6 과 같은 격의 승인 항목으로 등재 | `authoritative`(문서 출처) | **(a) 그대로 등재 채택** — 값 넷은 입찰공고정보서비스 참고자료가 **필수 항목**(항목크기 30)으로 선언하는 문면이라 새 주장이 아니다. **값을 바꾸지 않으므로 3A 코드 변경이 없다**(`CollectionPolicyTest` 의 표-인스턴스 대조가 이미 이 값을 잰다). 이 결정이 고치는 것은 **격**이다 — 3A 가 소비하는 어휘가 일반 조사 표가 아니라 승인 항목에서 온다. **업무구분 축이 셋이라는 사실은 그대로 남는다**: 이 열거(문서 `bsnsDivNm` 넷) · §5 의 legacy 관찰 코드→라벨 넷(용역을 일반/기술로 가름) · §1.9.1 의 낙찰정보서비스 `bsnsDivCd`. **세 축을 한 자리에 접지 않는다**(COL-08) — 통합은 `OPEN-COL-03` 이 열린 채이고 이 승인이 그것을 닫지 않는다 | 인계 정본 `_workspace/m3-3a/05_handoff_to_curator_P7.md` |
| **P-9** | §1.7 의 `authoritative` 칸을 **3B-2 필드 계약 정책 데이터의 초기값**으로(P-1 의 개찰 축 판) | `authoritative`(문서 출처) | **(a) 전 13 행 채택.** 「미확정」 칸(`bsisPlnprc` 의 basis · `sucsfbidRate` 밴드)은 그대로 미확정이고 인스턴스화 대상이 아니다. **함께 승인된 것**: 「P-9 채택이 요구하는 3A 확장」 **①②④**(`FieldConcept` 개찰 축 토큰 · `FieldScale` 셈 축 · 계약 행이 오퍼레이션 군을 구별) — **추가만** 하는 3A 좁은 확장 한 커밋이며 기존 계약 행과 corpus 27/27 은 불변. ③(복수예가 후보 basis)은 값이 미확정이라 열지 않는다 |
| **P-10** | `bidwinnrNm`·`bidwinnrBizno`·`opengCorpInfo`(및 §1.9.4 의 `prcbdrBizno`·`prcbdrCeoNm`)의 **수집·저장·원문 보존·로그 masking 정책** | 정책 결정(층 부여 대상 아님) | **(a) 상호만 남기고 나머지는 어댑터 경계에서 제거.** `bidwinnrNm` 은 원문 문자열로 canonical 에 저장한다(SET-01 이 상호 정규화 정확매치로 낙찰/패찰을 판정하므로 소비자가 확인된 유일한 식별자다). **사업자등록번호·대표자명은 저장하지 않는다** — 어댑터가 관측을 만들기 **전에** 그 자리를 고정 토큰으로 치환하고 치환 사실을 회계에 남긴다. `sourceText`(3D append 감사 통로)와 로그·evidence 에도 원문이 남지 않는다 — **「원문 무변환」의 예외를 이 축에 한해 명시적으로 둔다.** `opengCorpInfo` 는 성분 분해 뒤 상호·투찰금액·투찰율만 남기고, 성분 수가 경우마다 다르므로 **분해 실패는 조용한 성공이 아니라 명시적 결과**로 회계한다 |
| **P-12** | §1.9 의 오퍼레이션 계약 — `inqryDiv` 의 자리와 걷는 축 | 문면 `authoritative` · 축 선택은 정책 | **(a) 오퍼레이션 → (조회 축 값, 필수 항목, 기간 조건) 표를 정책 데이터로** 두고 URI 빌더는 해석만 한다(전역 상수 금지 — 같은 값이 군마다 다른 축을 뜻한다). **걷는 축 초기값은 개찰일시**(낙찰 목록 `3` · 개찰결과 목록 `3` · 검색군 `2`) — 기준일의 뜻이 `CollectionReferenceDate`(KST 캘린더 일자)와 맞는다. legacy 의 주석-문서 어긋남은 `insufficient-evidence` 로 등재하고 판정하지 않는다. base URI·tps·항목크기 문면은 초기값으로 채택 |

**같은 승인에서 slice 범위 결정 하나** — `getOpengResultListInfoOpengCompt`(개찰완료) 는 **이번 3B-2
밖**이다(D-3B2-9 (a)). `COL-03` 문면의 **추첨번호**(`drwtNo1`·`drwtNo2`)와 투찰 축(`bidprcAmt`·`bidprcrt`)을
주는 유일한 오퍼레이션이지만 공고당 1콜의 쿼터 비용과 P-10 대상 하나(`prcbdrBizno`, 필수)가 더 붙는다 —
**미구현을 알려진 제한으로 등재**하고 M4 4B 착수 전 후속 slice 로 둔다.

---

## 승인 대기 — P-8 (+ P-11 은 귀결)

| # | 대상 | 근거 절 | 상태 |
| --- | --- | --- | --- |
| **P-8** | **§1.10 의 첨부 문서 키 계약**(`ntceSpecDocUrl1~10`·`ntceSpecFileNm1~10`)을 3A 필드 계약 정책 데이터의 초기값으로 | §1.10 · `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` | **표 작성됨 2026-09-08(세션 모델) · 채택은 대기** — M4 4B 착수 전. 표의 모든 칸이 문서 문면 인용이라 값 결정이 아니라 등재 결정이다 |
| **P-11** | `sucsfbidRate` 의 percent → fraction(제수 `100`)을 계약 `scale` 로, 밴드는 `data-dictionary.md` §1.4.3 참조 | §1.7.2 | **별도 결정 대상이 아니다 — 기존 승인 규율의 귀결.** `ADR 0002` D-4(값 크기로 단위 판별 금지) · §5.3 규율 2(계약이 밴드를 재선언하지 않는다) · P-1 이 §1.1~§1.6 의 율 축에 이미 적용한 배관(`rate-unit-001`)이 셋 다 같은 답을 강제한다. 새 선택지가 없어 승인 요청에서 물음으로 올리지 않았다 — **운영자가 뒤집으려면 위 셋 가운데 하나를 개정해야 한다.** 덧붙여 **분자가 최종낙찰금액**이고 투찰률은 개찰완료의 `bidprcrt` 로 따로 오므로 두 율을 한 축으로 접지 않는다 |

**§1.7·§1.9 는 이제 인스턴스화 대상이다**(P-9·P-12 승인). **「미확정」 칸은 여전히 아니다** —
`bsisPlnprc` 의 basis 와 `sucsfbidRate` 의 밴드가 그 자리이고, 소유 `OPEN` 이 닫힌 뒤다.
