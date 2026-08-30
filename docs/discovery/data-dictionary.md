# V2 도메인 명세와 데이터 사전

slice **0C**. base `2b05684`. 계약은 `reports/evidence/m0/0c/scope.md`.

`milestone-0.md` §"Slice 0C"가 요구하는 **여섯 축**을 덮는다 — 용어·단위·basis·nullable 의미 /
aggregate와 상태 전이 / rule의 입력·출력·reason code / 정책 version과 effective date /
canonical KONEPS fact와 derived fact / ML feature와 업무 판단의 경계.

이 문서는 **legacy를 옮겨 적기 위한 것이 아니다.** legacy는 형태의 출처이고 정의의 출처가
아니다. 정의는 **운영자 결정** · **조달청 문서** · **`v2-지침서.md`의 규율** 셋에서 나오며,
그 셋이 답하지 않는 것은 §9에 `OPEN-DIC-NN`으로 남긴다.

---

## 0. 이 문서의 규약

### 0.1 지위

- `docs/discovery/capability-map.md`는 **무엇을 만드는가**를, `regression-ledger.md`는
  **무엇이 재발하면 안 되는가**를, 이 문서는 **그것들이 말하는 값이 무엇인가**를 정한다.
- 이 문서가 정의를 쓰는 자리에서 `capability-map.md`의 서술과 다르면 **§10이 그 사실을
  항목별로 밝힌다.** 그 경우 **이 문서가 정본**이다(운영자 승인 2026-08-27,
  `reports/evidence/m0/0a3/scope.md` 「알려진 제한」). 이 slice는 `capability-map.md`를
  고치지 않는다.
- **활성 `OPEN`이 소유한 쟁점에는 정의를 붙이지 않는다.** 그 자리에는 어느 `OPEN`이
  그것을 소유하는지만 적는다.

### 0.2 층 어휘 — `data-extract.md` §1

| 층 | 뜻 |
| --- | --- |
| `authoritative` | 공식 규정·API 문서 또는 **운영자가 승인한 업무 규칙** |
| `observed` | 기존 운영 데이터·로그에서 관찰한 실제 형태 |
| `legacy-behavior` | 기존 Python이 낸 결과·선언. **참고만 가능하며 정답 지위가 없다** |

**층은 값마다 붙는다.** 같은 항목에서 정의가 `authoritative`이고 저장 값의 실측이
`observed`인 경우가 있으며(§1.2 과세 처리가 그 예다), **그 둘을 섞지 않는다.**

### 0.3 인용 규약

- legacy 인용은 **`ed4b06c` 기준**이고 **저장소 루트부터의 전체 경로**를 쓴다.
  **파일명만 쓰지 않는다** — 기계로 확인할 수 없고 실제로 모호하다.
  `capability-map.md` DEC-05가 `bid_target.py`를 디렉터리 없이 인용한 것이 그 예이며
  실물은 `app/ai/bid_target.py`다(§10 **X-6**).
- 행 범위는 **블록 경계까지** 뜬다. 서술이 걸리는 문장만 잘라 뜨지 않는다.
- 수치를 인용할 때 **legacy가 기록한 측정일**을 함께 적는다. 측정일이 없는 수치는
  사실로 승격하지 않는다.
- **이 문서 안의 자기참조에 줄 번호를 쓰지 않는다.** 절 번호와 항목 id로 가리킨다.
  legacy 파일의 행 범위는 예외다 — 그 파일은 이 저장소가 편집하지 않는다.
- 인용의 기계 확인(경로 존재·행 범위)은 `reports/evidence/m0/0c/commands.md` **C-7**이다.

### 0.4 정의는 다섯 자리를 갖는다

| 자리 | 무엇을 적는가 |
| --- | --- |
| **개념** | 이 값이 무엇인가. 한국어 표시 라벨이 아니라 도메인 개념 |
| **타입** | V2가 이 값을 어떤 타입으로 나르는가 |
| **단위 / basis** | 무엇의 단위이고 무엇을 기준으로 잰 값인가 |
| **부재 표현** | 값이 없을 때 무엇이 되는가. **`0`·빈 문자열·`false`를 부재로 쓰지 않는다** |
| **provenance / 층** | 그 값이 어디서 왔는가, 그리고 §0.2의 어느 층인가 |

**모든 도메인 숫자는 §12에 등재된다** — **리터럴은 §12.1**에 단위·basis·provenance·층·
소유 `OPEN`과 함께, **타입이 나르는 필드는 §12.2**에 단위·basis·provenance와 함께.
**수가 아닌 필드는 §12.2가 그 사실을 적는다.**

본문에서 셈(항목 수)은 한글 수사로 쓰고 아라비아 숫자를 쓰지 않는다 —
그래야 §12.1이 도메인 숫자 리터럴만 담고 전수 검사가 성립한다. **필드가 나르는 수는
§12.2가 따로 덮는다.** 검사는 **C-4.1**(리터럴)과 **C-4.2**(필드)다.

### 0.5 `OPEN` 규약

- 이 slice가 발견한 미결은 **§9에 `OPEN-DIC-NN`**으로만 등록한다.
- `capability-map.md` §12와 `regression-ledger.md` §9의 `OPEN-REG`는 **out_of_scope**다.
  중앙 registry 통합은 별도 slice의 몫이며 ledger §10.3이 그렇게 인계했다.
- 운영자 결정이 이미 닫은 `OPEN`은 §11에 **근거와 함께 등재**한다. 등재가 곧 닫힘이며
  이 문서가 새로 결정하지 않는다.

### 0.6 결정 인용 규약

- 2026-08-26 묶음 → `reports/evidence/m0/0a2/decisions.md`
- 2026-08-28 묶음(`U-1`~`U-10`) → `reports/evidence/m0/0c/decisions-2026-08-28.md`

**결정은 재조사·재확정하지 않고 인용한다.** 결정이 명시적으로 0C에 위임한 확인
(`U-10`의 근거 주석 확인, `OPEN-ML-01`의 `SET-06` 분리 확인)은 재확정이 아니라 **실행**이며,
그 결과는 §4.3·§6.4에 있다.

---

## 1. 축 1 — 용어 · 단위 · basis · nullable 의미

### 1.1 금액 — `Money`

`v2-지침서.md` §4.1이 형태를 정한다: *"`Money`는 `amount`, `currency`, `basis`,
`vatTreatment`, `provenance`를 가진다."*

| 자리 | 정의 |
| --- | --- |
| **개념** | 원화 확정 금액 하나 |
| **타입** | `Money(amount: Long, currency: Currency, basis: AmountBasis, vatTreatment: VatTreatment, provenance: FactProvenance)` |
| **단위** | **원(KRW), 정수.** 소수 자리를 만들지 않는다 |
| **basis** | `AmountBasis` — §1.2의 개념 축 |
| **부재 표현** | `Known(Money)` / `Absent(reason)` sealed. **`0`은 "0원"이지 "모름"이 아니다** |
| **provenance / 층** | `FactProvenance`(§5.1). 층은 값마다 다르다 |

**왜 `Long` 정수인가.** legacy는 모든 금액 컬럼이 `Column(Float)`이고
(`app/models/models.py:264-306` · `app/models/pipeline.py:147-167`) 투찰가를
`round(float(budget) * rate, 2)`로 만든다(`app/ai/bid_target.py:56-59`). 원화에 없는 소수
자리를 만들고 이진 부동소수 오차를 금액에 들인다. 그 표현의 결과가 provenance 분류의
`clean` 판정이 "float integer 정확 일치"에 의존하는 형태다
(`app/services/base_amount_basis.py:43-55`).

**반올림은 중앙화한다.** legacy는 반올림 자리수가 콜사이트마다 갈려
`app/domain/aggregates.py:20-26`이 그 사실을 적고 `digits`를 필수 키워드로 받는다.
V2는 `RoundingPolicy`를 정책 데이터로 두고 version을 붙인다(§4).

**basis 교차 대입은 타입으로 막는다.** legacy는 `NewType` 두 개(`BaseAmount`·`YegaAmount`)만
두고 나머지 둘에는 타입이 없으며, `NewType`은 런타임에 소멸한다고 코드가 명시한다
(`app/domain/money.py:20-37`). 같은 모듈 docstring이 이 축의 교차 대입을 반복 회귀의 근본
원인으로 지목한다(`app/domain/money.py:1-12`). V2는 **역할별 타입**을 두고
원시 숫자 상호 대입을 컴파일 단계에서 막는다.

### 1.2 금액 개념 — 이름 하나에 값 하나

legacy는 **한 basis 태그가 두 개념을 덮고**(추정가격 ↔ 배정예산,
`app/services/koneps/field_contract_spec.py:114-171`), **한 개념에 세 이름이 붙는다**
(기초금액 = 사업금액 = 배정예산, `app/domain/money.py:20-37` ↔
`app/services/bid_base.py:84-104`). V2는 개념마다 타입 하나를 둔다.

| 개념 | 타입 | 단위 / basis | 과세 처리(정의) | 층 |
| --- | --- | --- | --- | --- |
| **추정가격** (`presmptPrce`) | `EstimatedPrice` | 원 / 추정가격 | **`Exclusive`(부가세 제외)** | `authoritative` — 조달청 정의 (**U-1**) |
| **기초금액 / 사업금액** (`bssAmt` 계열) | `BaseAmount` | 원 / 기초금액 | **`Inclusive`(부가세 포함)** | `authoritative` — 운영자 결정 2026-08-28 (**U-1b**) |
| **배정예산** (`asignBdgtAmt`·`bdgtAmt`) | `AllocatedBudget` | 원 / 배정예산 | `Unknown` — 정해진 바 없다 | 개념 분리는 legacy 선언(`app/services/koneps/field_contract_spec.py:137-161`), 과세는 미정 |
| **예정가** (`planned_price`) | `YegaAmount` | 원 / 예정가 | `Unknown` — 정해진 바 없다 | 개념은 `legacy-behavior` |
| **낙찰가** | `AwardAmount` | 원 / 낙찰가 | `Unknown` | 개념은 `legacy-behavior` |
| **투찰가** | `BidAmount` | 원 / 투찰가. **기초금액에 투찰율을 곱해 얻는다** | 곱셈 base를 따른다 → `Inclusive` | 관계는 `authoritative`(U-1b가 base를 확정) |
| **시공능력평가금액 — 운영자 보유액** | `ConstructionCapacityAmount` | 원 / 시공능력평가액 | **`Inclusive`** | `authoritative` — 운영자 결정 (**U-2**). **공고가 게시하는 요건 값은 이 행이 아니다** — §5.5의 별도 타입이고 단위·과세가 미결이다 |
| **도급한도** | `AwardedContractLimit` | 원 / 도급한도 | `Unknown` | 적합도 축(`OPEN-QUAL-08` 분할 확정) |

#### 1.2.1 과세 처리 — **정의와 저장 값을 구별한다**

`VatTreatment = sealed { Inclusive, Exclusive, Unknown }`이며 **금액의 필수 필드**다.

- **정의상 기본값**은 위 표다 — 추정가격 `Exclusive`, 기초금액 `Inclusive`.
- **저장된 legacy 값의 실제 과세 처리는 여전히 행마다 다르다.** legacy 자신이 그렇게
  공시한다: *"저장된 추정가격의 부가세 포함 여부가 이력상 일관되지 않다"*
  (`app/schemas/bid_summary.py:69-77`). 그 원인과 결론은 다른 자리에 있다 —
  *"부가세 포함 여부가 이력상 일관되지 않아**(수집 키가 공고마다 다름)** 이 비율에서 과세
  여부를 단정하면 틀릴 수 있다"*(`app/services/bid_base.py:69-81`).
  **이것은 `observed` 층의 참인 관찰이지 `legacy-defect`가 아니다.**
- **`legacy-defect`인 것은 선언이다** — `app/domain/money.py:20-37`의
  `# 추정가격: 부가세 포함 추정 총액`과 같은 방향의 주석
  (`app/services/koneps/field_contract_spec.py:114-171` · `app/services/koneps/field_contract_spec.py:137-161`).
  **조달청 정의와 반대다**(**U-1**).

**따라서 V2의 규칙**: 수집 어댑터는 각 금액에 `vatTreatment`를 **선언과 함께** 싣는다.
선언을 만들 수 없으면 `Unknown`이고, **`Unknown` 금액은 다른 과세 처리의 금액과 산술
비교에 들어갈 수 없다**(타입 차단). legacy 유래 행은 정의상 기본값으로 **자동 태깅하지
않는다** — 저장 값이 정의를 따른다는 근거가 없다.

#### 1.2.2 두 금액의 차이 — 무엇이 설명되고 무엇이 안 되는가

U-1·U-1b가 합쳐 **`OPEN-REG-05`("기초금액과 추정가격의 과세 처리 — 두 금액의 차이가
무엇으로 이루어지는가")를 닫는다**(§11).

- **설명되는 성분**: 부가세. 포함/제외 혼용이 두 금액 사이에 부가세율 크기의 어긋남을
  만든다. 이것이 `capability-map.md` §6 **NOTI-08**의 *"과세 공고에서 하한 여유가 부풀어
  보인다"*와 §7 **SET-09 F-4**의 *"과세 공고에서 어긋난다"*를 설명한다(§10 **O-2**).
- **설명되지 않는 성분**: **차이의 나머지(사정률 등)는 이 결정이 정하지 않는다.**
  이 문서는 **잔차를 정량화하지 않는다.** 정량화는 측정이 선행해야 하고 그 측정은 없다.

#### 1.2.3 표시 라벨과 도메인 이름의 분리

legacy의 회귀 원인이 **화면의 "예산"이라는 한 단어**였다고 코드가 적는다
(`app/services/bid_base.py:50-58`, commit `a05deb3`). V2는 도메인 이름을 하나로 두고
(`BaseAmount`) 한국어 표시 라벨("사업금액"·"배정예산")은 presentation 리소스로 외부화한다.
**표시 라벨을 도메인 타입 이름으로 채택하지 않는다.**

#### 1.2.4 시공능력평가금액 — 기준 시점 규칙 (0C가 도출)

`ConstructionCapacityAmount(money, evaluationYear)`. **이 타입이 나르는 것은 운영자
보유액 하나다** — 공고가 게시하는 요건은 §5.5의 `ConstructionCapacityRequirement`가 나른다.

- 과세 처리 **`Inclusive`**(U-2), `evaluationYear` = **직전 연도 공시값**(U-2b).
- 미입력은 **`Absent`**이며 `0`이 아니다. legacy는 `0`을 "not provided"로 쓰고
  `nullable=False`로 못박아 "미입력"과 "미달"을 구분하지 않는다
  (`app/models/models.py:161-182`).
- **출처는 `money.provenance`가 나른다** — 운영자 보유액은 **`OperatorDeclared`**(§5.1).
  공고 요건 값의 출처는 **그 다른 타입의 `requiredAmount`가 딛는 §5.3 필드 계약의
  `provenance`**가 나르며 **`Published`**다 — **그 값은 `Money`가 아니다**(§5.5).
  비교 결과에는 **무엇과 무엇을 비교했는지가 남는다**
  (`capability-map.md` QUAL-11의 무조건 acceptance).

> **⚠ 정정** — 앞서 이 자리는 서명을
> `ConstructionCapacityAmount(money, evaluationYear, provenance)`로 적고, 그 바깥
> `provenance`의 **타입을 선언하지 않은 채** 값으로 `OperatorDeclared`·`NoticePublished`를
> 지정했다. **셋이 함께 틀렸다.**
>
> - **`NoticePublished`는 `FactProvenance`에 없는 이름이고, 있는 이름의 둘째 이름이다** —
>   `Published(noticeRevision)`가 이미 "공고가 게시했다"의 자리다. **도입하지 않는다.**
>   **§5.1의 셋째 규율이 막는 것은 이 형태가 아니다** — 그 규율의 원문은 *"어휘 문자열이
>   이웃 축과 겹치지 않게 한다"*이고, 그것이 든 앵커가 말하는 겹침은 **다른 어휘 집합
>   사이의 문자열 충돌**이다(`app/core/constants.py:179-222` — *"이웃 어휘와 다른 집합이다
>   — 병합 금지"*). 여기 형태는 **한 어휘 집합 안의 둘째 이름**이라 겹치는 문자열이 없다.
>   **받는 자리는 §1.2 머리다** — 그 절은 legacy 결함을 *"한 개념에 세 이름이 붙는다"*로
>   적고 *"V2는 개념마다 타입 하나를 둔다"*로 닫는다. 그 처방은 **금액 개념의 타입**에
>   대해 적혔으므로 provenance 어휘의 variant에는 축어가 아니라 **같은 형태로 적용된다.**
> - **"두 값은 같은 타입"이 거짓이다** — §5.5가 공고 요건에 **별도 타입**을 준다.
>   그 타입은 `limitGroupNo`·`licenseRegionCode`를 나르는 **제한그룹별 요구 금액**이고
>   운영자 보유액은 회사 하나의 값이다. **한 타입이 둘을 겸할 수 없다.**
> - **바깥 `provenance`는 새 축이 아니라 `money.provenance`의 재선언이다.** §5.3의 **둘째
>   규율**이 같은 형태를 막는다 — *"계약이 밴드를 재선언하지 않고 단일 출처를 참조한다"*.
>   두 자리를 두면 갈린다. **그래서 바깥 필드를 걷고 `money.provenance` 하나로 나른다** —
>   §12.2는 이미 이 값을 `money` 행에 귀속하고 있었다.
>
> **U-2b 기록이 이 서명을 세 항으로 인용한다**(`decisions-2026-08-28.md`의 U-2b 절).
> 그 인용은 **0C가 제출한 서명**이고 운영자가 정한 것은 **`evaluationYear` = 직전 연도**다.
> **결정 내용은 바뀌지 않는다** — 0C가 자기 서명의 중복을 걷는다.

**"직전"의 기준일 — 규칙: 공고일이다.** 운영자 결정이 아니라 규칙 도출이므로 0C가 정한다.

> `evaluationYear = year(공고일) − 1`. 공고일을 모르면 **판정하지 않고**
> `Uncertain(RequirementDataAbsent)`를 낸다.

근거 셋:

1. **판정 재현성.** 투찰일 기준이면 같은 공고를 다시 판정할 때 판정 시점에 따라 답이
   달라진다. 연초 공고에서 실제로 갈린다. 공고일 기준은 공고마다 값이 하나로 고정된다.
2. **leakage-safe 설계 규율의 동형 적용.** 이 저장소에서 유효일자를 다루는 유일한 실물이
   `app/ai/predictors/legal_floor_spec.py:14-18`이고, 그것이 *"리졸버는 반드시 공고 기준일을
   받아 그 시점에 유효했던 율을 고른다. 기준일을 모르면 표를 적용하지 않는다"*를 규율로
   세운다. `capability-map.md` DEC-03 acceptance가 같은 요구를 담는다.
3. **요건은 공고가 게시한다.** 공고 측 요건 필드는 공고 시점의 기준이므로, 운영자 보유액도
   같은 기준일로 잡아야 두 값이 같은 시점의 값이 된다.

**한계(등록)**: 이 규칙은 **시공능력평가액 공시의 갱신 주기·시행 구간**이 역년(1월 1일
경계)과 일치할 때만 "직전 연도"와 "공고일 기준 직전 해"가 같다. 그 주기는 이 저장소의
근거로 확인되지 않는다 → **`OPEN-DIC-02`**.

**공고 측 요건 필드는 legacy가 수집하지 않는다** — `cnstrtnAbltyEvlAmt` 계열 키가
`ed4b06c`에 없다(재현: `commands.md` **C-7.2**). 비교 대상이 없으므로 **필드 자리만
정의하고 수집 신설은 M1 이후로 인계한다**(§13).

**위 규칙은 운영자 보유액 축의 것이다.** **공고 게시 요건 값의 단위·과세는 이 문서가
정하지 않는다** — 그 축은 **활성 `OPEN-QUAL-10`의 게시 요건 절반**이 소유하고(§11의
⚠ 정정), 정의 자리는 §5.5다. **U-2·U-2b가 정한 보유액의 성질을 그쪽에 전이시키지
않는다.**

### 1.3 부재는 1급 상태다 — `0`을 "미상"으로 쓰지 않는다

legacy는 `0.0`을 부재 표현으로 쓴다 — `HistoricalData.base_amount`·`bid_rate`
(`app/models/models.py:264-306`), `TenderResult.winning_amount`·`winning_rate`
(`app/models/pipeline.py:147-167`), `CompanyProfile.construction_capacity_amount`
(`app/models/models.py:161-182`). 그 결과가 성숙도 함정이다 — 정산 여부를 `IS NOT NULL`로
재면 성숙도가 항상 최댓값으로 나오고(분자와 분모가 같아진다), 로더는 그래서
`settled=(record.winning_amount or 0.0) > 0.0`으로 판정한다
(`app/services/settlement_maturity.py:99-108`, 함정 서술은
`app/domain/settlement_maturity.py:1-29`).

**운영자 결정(`OPEN-ML-04`)이 "'미정산'을 `0`으로 적재하지 않는다"를 확정했다.**
0C가 도출하는 것은 **그 금지가 적용되는 컬럼 집합**이다.

> **규칙 (부재 금지 규칙)**: 도메인 값을 나르는 필드는 **부재를 값으로 표현할 수 없다.**
> 금액·율·건수·시각 전부에 적용되며, 부재는 `Absent(reason)` 또는 `Unmeasurable(reason)`
> (§1.6)으로만 표현한다. **DB 기본값으로 `0`·`""`·`false`를 두지 않는다.**

**두 모델 파일에서 `0` 기본값을 선언한 컬럼 전부는 `commands.md` **C-7.3**이 낸다.**
그중 이 문서가 정의를 쓴 축을 예로 들면 — 정산 금액·율, 기초금액, 투찰율,
시공능력평가금액, 도급한도, 역량 점수, 페이퍼 투찰 금액·율.
**정산 축만으로 좁히지 않는다** — 같은 관례가 금액 축과 율 축 양쪽에 있다.
**그 출력의 모든 행이 이 규칙의 대상인 것은 아니다** — 불리언 플래그처럼 `0`이 진짜
값인 자리는 대상이 아니다.

### 1.4 Rate — 축별 타입과 밴드 인벤토리 (**정본**)

#### 1.4.1 `Rate`

- **내부 표현은 fraction 하나뿐이다.** percent 입력은 adapter에서 명시 변환하고
  **원문 unit을 수집 시점에 붙잡는다.**
- **값 크기로 단위를 추측하는 경로를 만들지 않는다.** legacy는
  `to_bid_rate_fraction(numeric) = numeric/100 if numeric > threshold else numeric`으로
  추측하며(`app/domain/rate_normalization.py:105-113`), 같은 규칙이 여러 곳에 독립 구현돼
  한 곳만 임계가 달라 회귀 엔진이 됐다(`regression-ledger.md` `R-RATE-01`).
- 이 금지는 **architecture test**로 고정한다(`v2-지침서.md` §4.1).

#### 1.4.2 축별 타입

한 축의 율을 다른 축에 대입할 수 없게 타입을 나눈다. **넷 다 `Rate`의 뉴타입이고, 수를
나르는 자리는 `Rate`의 `fraction` 하나다** — 축은 타입이 나르고 수는 한 자리에 둔다.
그래서 §12.2에 등재되는 **필드**는 `fraction` 하나이며, **넷의 축·provenance·층·부재
표현은 이 표가 정본이다.**

> `Rate(fraction)`. **부재는 값이 아니다** — 율이 아직 없으면 `Absent(reason)`, 재려
> 했으나 재지 못했으면 `Unmeasurable(reason)`이다(§1.3 · §1.6). **`0`은 "0"이지 "모름"이
> 아니고, 어떤 계산도 그 둘을 `0`으로 접을 수 없다**(타입 차단).

| 타입 | 분자 / 분모 | 단위 | 값의 provenance · 층 | 부재 표현 |
| --- | --- | --- | --- | --- |
| `AssessmentRate` (사정률) | 예정가 / 기초금액 | fraction | **파생값**(§5.2 derived fact) — 두 금액의 몫이다. **분자·분모 두 `Money`의 `provenance`가 결과의 성질을 정하므로 값과 함께 남는다.** 축 정의의 층은 `legacy-behavior`(§1.4.3 **B1**·**B2**) | `Absent(reason)` / `Unmeasurable(reason)` |
| `AwardRate` (낙찰률) | 낙찰가 / 기초금액 | fraction | **파생값** — 같은 형태이고 분자가 개찰 결과에서 온다. 축 정의의 층은 `legacy-behavior`(§1.4.3 **B4**) | 같음 |
| `FloorRate` (낙찰하한율) | **예정가격 기준** 율 | fraction | **출처가 둘이고 섞지 않는다** — ① **공고 게시값**(canonical fact, §5.2)에는 §1.4.3 **B6**의 신뢰 게이트가 걸린다 ② **§4.4 법정 하한율 표**(정책 데이터)는 `effectiveFrom`으로 고른다. **어느 쪽에서 왔는지가 값과 함께 남는다.** 표 값의 층은 `legacy-behavior`이고 V2 값의 확정은 활성 `OPEN-DEC-10`이 소유한다 | 같음. 해석 실패의 사유는 §3.3의 `FloorRateUnresolved` |
| `BidRate` (투찰율) | 투찰가 / 기초금액 | fraction | **자리가 둘이고 한 값으로 합치지 않는다** — ① 과거 실적에서 잰 **관측 파생값**(표본 축, §6.5) ② 우리가 산출한 **추천 투찰율**(파생). 축 정의의 층은 **`authoritative`** — 곱셈 base가 기초금액이라는 것을 U-1b가 확정했다(§1.2) | 같음. 산출 불가의 사유는 §3.3의 `BidRateUnavailable` |

**원문 unit이 보존되는 자리는 값이 아니라 필드 계약이다.** §1.4.1은 *"원문 unit을 수집
시점에 붙잡는다"*고만 적고 **어느 자리가 그것을 나르는지는 §5.3이 갖는다** —
`KonepsFieldContract`의 `unit`·`scale`이다. **`Rate` 값 자신은 fraction 하나만 나르므로
원문 unit을 나르지 않는다.** 그래서 값만 보고 단위를 되짚을 수 없고, **되짚으려고 값
크기를 보는 것이 §1.4.1이 금지한 바로 그 경로다.**

`FloorRate`가 예정가격 기준이라는 사실은 `app/ai/predictors/legal_floor_spec.py:20-33`이
명시하며, 투찰가는 기초금액 기준이므로 **두 축을 잇는 변환이 사정률**이다. 그 관계가
`capability-map.md` DEC-04의 핵심 통찰(`임계 사정률 = 추천 투찰율 ÷ 낙찰하한율`)이다.

#### 1.4.3 밴드 인벤토리 — **이 표가 정본이다** (§10 **X-2**·**X-3**·**X-4**)

밴드는 `(축, 목적, 값, 경계 포함성, version, 근거)`를 갖는 **정책 엔트리**다. 아래는
`ed4b06c`에서 직접 확인한 legacy 실물이며 층은 전부 `legacy-behavior`다.

| # | 밴드 | 값 | 축(분자/분모) | 목적 | legacy 위치 |
| --- | --- | --- | --- | --- | --- |
| B1 | 사정률 분모 필터 | `0.90` ~ `1.10` | 예정가/기초금액 | 하한 미달 빈도의 표본 개연 범위 | `app/domain/floor_shortfall.py:42-57` |
| B2 | 사정률 관측 편입 필터 | `0.8` ~ `1.2` | 예정가/기초금액 | 오적재 배제(관측 필터) | `app/core/constants.py:406-415`, 술어 `app/ai/predictors/distribution_extraction.py:51-58` |
| B3 | 사정률 **제외** 밴드 | `1` ± `1e-3` | 예정가/기초금액 | 낙찰률 basis 독립성 필터가 **버리는** 밴드 | `app/services/floor_shortfall.py:94-98` |
| B4 | 율 라벨 유효 창 | `0.5` ~ `1.5` | 낙찰가/금액 | 정규화 후 유효 범위 | `app/domain/rate_normalization.py:96-102` |
| B5 | 투찰비 개연 밴드 (**별도 사본**) | `0.5` ~ `1.5` | 투찰가/기초금액 | 분포 엔진 편입 | `app/ai/predictors/distribution_extraction.py:40-48` |
| B6 | 게시 하한율 신뢰 밴드 | `0.30` ~ `0.995` | 게시 하한율 | 게시값 신뢰 게이트 | `app/domain/published_floor_rate.py:34-43` |
| B7 | 투찰율 guardrail 클램프 | `0.7` ~ `1.4` | 투찰율(기초금액 기준) | 시나리오 율 클램프 | `app/ai/predictors/historical/statistics.py:65-67` |

**세 정정이 이 표에 있다.**

- **X-2** — `capability-map.md` DEC-11 표의 「사정률 관측 가능 판정」 행은 **별도 밴드가
  아니다.** `is_observable_assessment_rate`는 B2의 상수를 그대로 쓰는 **술어 한 벌**이며
  그 이유가 코드에 적혀 있다(*"상수만 나누고 비교식을 두 벌로 두면 경계 포함성이 한쪽만
  바뀌는 사고가 난다"*, `app/ai/predictors/distribution_extraction.py:51-58`).
  **밴드는 B2 하나이고 술어가 그 옆에 있다.**
- **X-3** — DEC-11 표에 **B3와 B5가 없다.** B3는 `capability-map.md` DEC-04가 기록한
  편향 방향의 원인 그 자체이고(`app/services/floor_shortfall.py:27-46`),
  B5는 B4와 값이 같고 **축이 다른 별도 사본**이다.
- **X-4** — DEC-11이 투찰율 클램프 출처로 가리킨 자리는 **docstring 언급**이고
  (`app/domain/basis_conversion.py`), 실제 리터럴은 B7의 위치에 있다.

**축이 같고 값이 다른 밴드가 사정률 축에만 셋(B1·B2·B3)이다.** legacy가 통합 금지를
명시적으로 방어하며(`app/core/constants.py:406-415` ·
`app/domain/floor_shortfall.py:42-57`), 운영자 결정 `OPEN-DEC-02`가 **두 벌을 목적별로
유지**로 확정했다.

> **규칙**: 같은 축에 밴드가 둘 이상이면 **CI가 그 사실을 드러낸다.** 통합은 금지가
> 아니라 **관측 가능해야 한다**는 요구다. 각 밴드는 `목적`과 `근거` 문자열을 필수로 갖고,
> **경계 포함성**(이상/초과)을 값과 함께 선언한다.

### 1.5 시각과 시간대

| 자리 | 정의 |
| --- | --- |
| **저장·전송** | `Instant`(UTC) |
| **업무 구간** | `Asia/Seoul` 고정. 주 경계는 **월요일 00:00 KST** |
| **구간 경계** | **시작 포함 · 끝 제외**(반개구간) |
| **부재 표현** | `Absent(reason)`. 시각 종류를 모르는 행은 **시간축 값을 갖지 않는다** |

근거: legacy가 KST 주를 쓰는 이유를 적는다 — *"개찰은 한국 업무시간에 일어나므로 UTC 자정
경계를 쓰면 월요일 오전 개찰이 전주로 밀린다"*(`app/domain/settlement_maturity.py:1-29`).
반개구간 규칙의 두 성질(인접 구간 비겹침, 학습·평가 범위의 경계 배타성)도 코드에 적혀
있다(`app/domain/settlement_maturity.py:88-94`). **타임존 리터럴을 코드에 두지 않고**
설정으로 외부화한다.

**한 컬럼이 여러 시각 종류를 겸하지 않는다.** legacy는 `opened_at`에
`opening_announced_at or opening_scheduled_at`을 넣고
(`app/services/koneps/persistence.py:690-692`), 그 원천이 다시
`rlOpengDt or fnlSucsfDate or rgstDt`로 갈린다(`app/services/koneps/scsbid.py:218-222`).
**어느 것이 들어갔는지 남지 않는다.**

> `OpeningTime = sealed { Scheduled(Instant), Actual(Instant),
> Substituted(Instant, source, policyVersion), Unknown(reason) }`

`Substituted`의 **승인 여부와 허용 출처**는 활성 **`OPEN-SET-10`**이 소유한다. 이 문서는
variant 자리만 두고 어느 출처가 승인되는지 정하지 않는다.

### 1.6 측정 불가 어휘 — 하나로 통합한다 (B-13)

legacy는 같은 개념을 축마다 다르게 표현한다 — 하한 미달 빈도는 `None` + 한국어 문장
(`app/services/notice_floor_shortfall.py:49-88`), 면허는 `VERDICT_UNKNOWN`
(`app/services/license_eligibility.py:100-137`), 하한 적용 범위는 `uncertain`
(`app/ai/floor_applicability.py:78-96`), 성숙도 빈 구간은 `0.0`
(`app/domain/settlement_maturity.py:81-86`), 0-분모는 함수마다 `0.0` 또는 `None`
(`app/domain/aggregates.py:20-26` · `app/domain/aggregates.py:59-68`).

> `Measured<T>(value, sampleSize, policyVersion)` / `Unmeasurable(reason: ReasonCode, detail)`

- **어떤 계산도 `Unmeasurable`을 `0`으로 접을 수 없다**(타입 차단).
- **`Unmeasurable`은 `0%`로 표시되지 않는다**(`v2-지침서.md` §4.4).
- 판정 불가와 "값이 0"은 **다른 표시**를 갖는다(`capability-map.md` DEC-04 acceptance).

`capability-map.md` §9 B-13이 이 통합을 **권고**로 남기고 소유를 DEC-04에 두었다.
이 문서가 그 통합을 **명세로 확정**한다 — 어휘가 하나여야 소비자가 통일된 처리를 한다.

---

## 2. 축 2 — aggregate와 상태 전이

> **이 축의 전이표는 legacy에서 옮긴 것이 아니라 이 문서가 새로 쓴 도메인 명세다.**
> legacy에는 상태 **어휘**만 있고 전이를 선언한 곳이 없다 — 전이는 각 service의 대입문에
> 흩어져 있다(두 모델 파일에서 `status = Column` 형태로 선언된 자리는 `commands.md`
> **C-7.4**가 내고, 같은 블록이 전이표 선언 매치가 없음을 낸다). `v2-지침서.md` §4.5가
> *"상태 전이는 명시적 state/event table로 표현한다"*를 요구한다.

### 2.1 aggregate 경계 — 넷

| aggregate | 소유하는 것 | 소유하지 않는 것 |
| --- | --- | --- |
| **`Notice`** | 공고의 canonical fact **전부**. **금액 축 전체**(추정가격·기초금액·배정예산), 공고 상태, 마감·개찰 예정 시각, 자격 원문 | 판정 결과 |
| **`Qualification`** | 자격 판정 결과와 근거(면허·지역·금액 capacity) | 요건 원문(=`Notice`의 fact) |
| **`BidDecision`** | 운영자 결정 lifecycle, 추천 투찰가와 그 근거, 제출 기록 | 개찰 결과 |
| **`TenderOutcome`** | 개찰·정산 관측 event stream과 그 fold 결과 | 공고 fact |

> **규칙**: **한 공고의 금액 축을 두 aggregate에 나누지 않는다.** legacy는 기초금액을
> `HistoricalData`가, 추정가격을 `Project`가 들고 있어(`app/models/models.py:264-306` ·
> `app/models/models.py:84-90`) 같은 공고의 두 금액이 다른 테이블에 갈라져 있고, 그 분리가 provenance 분류의
> 분모 문제를 만든다(§3.4).

**단일 회사 결정(`OPEN-STR-07`)과 소유권 경계**: 다중 operator를 V2 범위에 두지 않지만,
**소유권 경계 자체는 타입으로 유지한다.** 조용한 소유권 재할당이 실제 오염을 낸 이력이
있다(commit `fc291c7`, `decisions.md` `OPEN-STR-07` 절의 하류 주의). per-operator 스키마의
단순화 여부는 M1 설계 결정이며 이 문서가 정하지 않는다.

### 2.2 상태와 전이표

#### 2.2.1 `NoticeStatus`

legacy: `status` 컬럼이 제약 없는 문자열이고 값 목록이 **인라인 주석**으로만 있다 — 여섯 값
(`app/models/models.py:84-90`). **enum도 CHECK 제약도 없다.** 부분집합만 상수로 승격돼
있고, constants 자신이 그 둘의 혼동을 **잠재 버그**로 적는다
(`app/core/constants.py:117-156`).

`NoticeStatus = sealed { Open, Renoticed, Closed, Awarded, Failed, Cancelled }`

| 현재 상태 | 이벤트 | 다음 상태 |
| --- | --- | --- |
| (없음) | `NoticeCollected` | `Open` |
| `Open` | `RenoticeObserved` | `Renoticed` |
| `Open` · `Renoticed` | `DeadlineReached` | `Closed` |
| `Closed` | `AwardObserved` | `Awarded` |
| `Closed` | `FailureObserved`(유찰) | `Failed` |
| `Open` · `Renoticed` · `Closed` | `CancellationObserved` | `Cancelled` |

- 종단 상태: `Awarded` · `Failed` · `Cancelled`.
- **표에 없는 (상태, 이벤트) 쌍은 전이가 아니라 거부**이며, **거부 사실이 관측 가능해야
  한다**(조용히 무시하지 않는다).
- **"입찰 가능"은 상태 값 집합이 아니라 파생 술어다**:
  `isBiddable(notice, now) ⟺ status ∈ {Open, Renoticed} ∧ now < deadline`.
  호출부가 상태 리터럴을 고르는 경로를 만들지 않는다 — legacy의 잠재 버그가 정확히
  단수 `"open"` 리터럴로 거른 것이었다.

#### 2.2.2 `DecisionState` — 시스템 산출과 운영자 입력을 분리한다

legacy는 결정 lifecycle 어휘가 세 벌이고(`app/core/constants.py:55-115`), 자동 생성기가
낼 수 있는 상태 집합과 운영자가 도달할 수 있는 집합이 다르며, 권고 어휘(`bid_now`)와 제출
어휘(`submit`)가 **다른 문자열인데 둘 다 `action`이라는 같은 이름의 필드로 나른다**
(`app/core/constants.py:16-46` · `app/models/models.py:319-325`).

- `Recommendation`(시스템 산출) = `Verdict`(§3.6)
- `OperatorAction`(운영자 입력) = `sealed { Submit, Review, Skip }`
- **두 타입은 이름도 타입도 다르다.** 같은 필드명을 쓰지 않는다.

`DecisionState = sealed { Proposed, UnderReview, Submitted, Skipped }`

| 현재 상태 | 이벤트 | 행위자 | 다음 상태 |
| --- | --- | --- | --- |
| (없음) | `RecommendationProduced` | 시스템 | `Proposed` · `UnderReview` · `Skipped` |
| `Proposed` · `UnderReview` · `Skipped` | `OperatorAction(Review)` | 운영자 | `UnderReview` |
| `Proposed` · `UnderReview` | `OperatorAction(Submit)` | 운영자 | `Submitted` |
| `Proposed` · `UnderReview` | `OperatorAction(Skip)` | 운영자 | `Skipped` |
| `Submitted` | — | — | 종단 |

> **규칙**: **시스템이 낼 수 있는 상태를 타입으로 분리한다** — `SystemProducible ⊂
> DecisionState = { Proposed, UnderReview, Skipped }`. 부분집합을 상수 집합으로 나열하지
> 않는다. `Submitted`는 운영자 행위로만 도달한다.

**상호작용 모델은 pull이다**(운영자 결정 2026-08-26, `capability-map.md` §0.7). 운영자에게
강제되는 작업 큐가 없으므로 **이 상태 기계는 "처리해야 할 항목"을 추적하지 않는다** —
운영자가 이미 내린 판단의 기록일 뿐이다.

#### 2.2.3 `TenderOutcome` — 상태 기계가 아니라 fold다 (**U-3**)

**운영자 결정**: aggregate 경계를 `TenderOutcome`으로 두고 **current를 event stream의 fold
결과로 정의한다. 저장은 최적화이지 진실이 아니다.**

legacy는 event가 append-only인데 snapshot은 in-place mutate라
(`app/models/pipeline.py:130-177`), 두 결함이 나온다 —
**하향 정정을 표현할 수 없고**(`capability-map.md` SET-09 F-7),
**같은 값 재관측이 기록되지 않는다**(F-8, 멱등 단위가 `event_key` 하나다).

| fold 규칙 | 정의 |
| --- | --- |
| **입력** | `TenderObservation(deliveryKey, observationKey, observedAt, payload, source)` 의 append-only 스트림 |
| **멱등 단위(전송)** | `deliveryKey`. 같은 `deliveryKey`를 나르는 **재전송은 상태를 바꾸지 않는다** |
| **재관측 단위(의미)** | `observationKey`. **같은 사실의 관측을 묶는다.** **payload 전체 해시가 아니다** — 같은 값 재관측이 기록되어야 한다 |
| **재관측** | `reobservationCount`를 별도 필드로 센다 — **같은 `observationKey`를 가진 관측**을 세고 **첫 관측은 세지 않는다** |
| **정정 방향** | **상향·하향 모두 허용**한다. 단조 ratchet을 만들지 않는다 |
| **current** | `fold(observations)` — 각 필드는 그 필드를 마지막으로 관측한 event에서 온다 |
| **저장** | current를 물리적으로 저장할 수 있으나 **그 값은 fold의 캐시**이며 event stream이 진실이다 |

> **⚠ 정정 — 키가 둘인 이유: 하나로는 셀 수 없다.** 앞서 이 표는 **멱등 단위와 재관측
> 단위를 한 키(`observationKey`)에 겹쳐** 두었다. 그러면 **중복을 제거하면 재관측을 셀 수
> 없고, 세면 재전송이 상태를 바꾼다** — 멱등성과 `reobservationCount`가 **동시에 성립할 수
> 없다.** 두 역할을 **한 키가 겸할 수 없다는 것**이 여기서 확정되는 전부이고, 그것은 새
> 결정이 아니라 **모순의 제거**다. `capability-map.md` SET-09 `F-8`이 이미 같은 방향을
> 적는다 — *"멱등 단위와 재관측 횟수를 분리해 표현한다"*.

**각 키가 무엇으로 이루어지는지는 이 문서가 정하지 않는다** → **`OPEN-DIC-07`**.
무엇이 **한 전송**을 식별하고 무엇이 **같은 사실**을 식별하는지가 그 미결이다.

- **U-3을 넘지 않는다.** U-3이 정한 것은 **fold로의 재정의**이고, 그 결정은 **멱등 키의
  구성을 정하지 않았다**(`decisions-2026-08-28.md`의 U-3 절).
- **`OPEN-SET-04`가 소유한 것도 아니다.** 그 `OPEN`은 **재관측을 운영자에게 노출할지**를
  묻는다 — 키의 구성이 아니다. 그래서 신설 `OPEN`으로 올린다.

**`OPEN-SET-04`(재관측을 운영자에게 노출할지)는 이 결정에 종속되나 별개다.** fold로
정의하면 재관측이 **표현 가능**해지지만 **보일지**는 그 `OPEN`이 소유한다. 이 문서는
`reobservationCount` 필드를 두되 노출을 정하지 않는다.

#### 2.2.4 `AwardOutcome` — 동점을 표현한다

legacy는 `"won"/"lost"` 두 값 + `NULL = 미확정`이라 **"가격만으로 판정 불가"인 자리가
없다**(`app/models/models.py:344-360`). 같은 주석이 정직 명세를 적는다 —
**낙찰/패찰 판정에 금액을 쓰지 않는다**(같은 개찰에 동일 투찰가가 여러 업체에 실린다).

`AwardOutcome = sealed { Won, Lost, Undeterminable(reason), Pending }`

- **`Pending`**(아직 모른다)과 **`Undeterminable`**(구조적으로 가격만으로는 못 정한다)을
  분리한다. 운영자 결정 `OPEN-SET-02`가 확정한 것은 **제3 상태의 존재**이고, 그 셋과 넷의
  구분은 0C 소관이었다 — **넷으로 나눈다.** 둘을 합치면 "언젠가 알 수 있는 것"과 "이
  방법으로는 못 정하는 것"이 같은 값이 되어 운영자의 다음 행동이 갈리지 않는다.

#### 2.2.5 outbox 상태 — 어휘 자리만 둔다

legacy에 DB 기반 outbox의 상태·클레임 구조가 있으나
(`app/models/pipeline.py:95-127`) **상태 값 집합이 어디에도 선언돼 있지 않다.**

**V2의 상태 어휘와 전이는 활성 `OPEN-OPS-10`(DB 큐 계약)이 소유한다.** 이 문서는
`OutboxEntryState`가 sealed 어휘와 명시 전이표를 가져야 한다는 **형태 요구**만 적고
값 집합을 정하지 않는다. 브로커 없음 결정(`OPEN-OPS-05`)의 파급이 그 `OPEN`에 걸려 있다.

### 2.3 "정산됨"의 정의 — **이 절이 정본이다** (§10 **X-5**)

`capability-map.md` §9.1이 이 통합 여부를 **0C에 위임했다**(*"SET-07에 네 개 병기, 통합
여부는 0C"*). 0C가 정한다.

#### 2.3.1 legacy의 네 술어 — 실측

| # | 술어 | 정확한 조건 | 소비처 | 근거 |
| --- | --- | --- | --- | --- |
| S1 | `settled_with_amount()` | `is_current ∧ winning_amount IS NOT NULL ∧ winning_amount > 0` | 정산 계산 · 대시보드 · 정확도 리포트 · 페이퍼 백테스트 | `app/services/query_predicates.py:45-68` |
| S2 | `settled_any_signal()` | `is_current ∧ (winning_amount > 0 ∨ winning_rate > 0)` | ML dataset · 홀드아웃 라벨 | `app/services/query_predicates.py:71-115` |
| S3 | 성숙도 로더 인라인 | `(winning_amount or 0.0) > 0.0`, 모집단은 **피드 출처 한정** | embargo(SET-06) | `app/services/settlement_maturity.py:99-108` |
| S4 | 검증 CLI 인라인 | **`total_count > 0 ∨ bool(reserve_prices) ∨ winning_amount > 0`** | 개찰 완료 판정 | `app/services/award_verification.py:283-291` |

**X-5 정정**: `capability-map.md` SET-07이 S4를 *"예비가 존재 또는 낙찰가 > 0"*으로
요약했으나 실제 술어는 **세 논리합**이며 `total_count`(예비가 상세 총건수)가 **독립 축**이다.

#### 2.3.2 결정 — 세 개념으로 분해하고, 성숙도는 파생이다

> - **`AwardAmountKnown`** — 낙찰 금액을 아는가. (S1)
> - **`AnySettlementSignal`** — 결과 신호가 하나라도 있는가. (S2)
> - **`OpeningCompleted`** — 개찰이 끝났는가. (S4의 **세 논리합**)
>
> **성숙도(S3)는 독립 정의가 아니라 `AwardAmountKnown`에 모집단 제약이 붙은 파생이다.**

**근거 셋.**

1. **legacy가 통합을 근거와 함께 거부했다.** `app/services/query_predicates.py:78-101`이
   S1·S2 분리를 의도로 적고 그 분기 원인이 라이브 write 경로임을 밝힌다 — 외부 피드가
   금액과 율을 독립 파싱하고 persistence가 금액 결손을 `0`으로 강제하면서 율은 유지한다.
   **재측정 결과가 0행이라고 적으면서도 "이 결과는 KONEPS 데이터에 의존하지 코드가
   보장하지 않는다"**고 같은 자리에 적는다(측정일 2026-07-26).
2. **`OPEN-ML-04` 결정이 분기 원인 하나를 제거하지만 전부는 아니다.** "미정산을 `0`으로
   적재하지 않는다"가 서면 `winning_amount == 0`이라는 표현 자체가 사라진다. 그러나
   **외부 피드가 금액을 주지 않는 상황은 남는다** — 그때 `AwardAmountKnown`은 거짓이고
   `AnySettlementSignal`은 참이다. **두 질문이 다르다는 사실이 write 규칙으로 없어지지
   않는다.**
3. **S3의 판정식은 S1과 동치이고 다른 것은 모집단이다.** 정의 축과 모집단 축을 분리하면
   개념은 넷이 아니라 **셋 + 모집단 제약 하나**다.

**모집단 제약(성숙도 전용)**: 개찰결과 피드로만 본 공고는 **정산된 순간에만 관측되므로**
미정산 쪽 분모가 애초에 없고 그 모집단의 성숙도는 정의상 최댓값이 되어 embargo를
무력화한다. 따라서 성숙도·커버리지 계산에는 **미정산을 셀 수 있는 모집단만** 쓴다
(`capability-map.md` SET-06 「모집단 제약」과 같은 요구).

> **규칙**: 소비 지점은 셋 중 **어느 개념을 쓰는지 타입으로 고정한다.** "정산됨"이라는
> 한 단어로 뭉뚱그린 필드·함수·컬럼을 만들지 않는다.

---

## 3. 축 3 — rule의 입력 / 출력 / reason code

### 3.1 reason **code** 규약

legacy에는 reason code가 **없다.** 판정 근거가 한국어 완성 문장 리스트로 조립되고 그대로
영속된다(`app/services/allocation_core.py:163-181`, 영속 컬럼
`app/models/models.py:344-360`). 계산을 고치면 저장된 감사 문구가 틀린 채 남는다.

> - 판정은 `ReasonCode` **enum + 구조화 payload**를 낸다.
> - **사람이 읽는 문장은 렌더링 시점에 생성하고 영속하지 않는다.**
> - `ReasonCode` 어휘의 단일 소유는 `capability-map.md` §9 **B-12**가 DEC-06에 두었다.
>   각 축은 자기 코드를 **방출만** 한다.
> - **문장으로만 구분되는 판정을 만들지 않는다**(운영자 결정 `OPEN-DEC-05`).

### 3.2 면허 자격 rule

#### 3.2.1 입력 / 출력

| 자리 | 정의 |
| --- | --- |
| **입력** | 공고의 요건 원문(`Notice`가 소유): 면허 요건 행 집합(`lcnsLmtNm` · `lmtGrpNo` · `lmtSno` · `permsnIndstrytyList`) + 운영자 보유 면허 선언 |
| **출력** | `LicenseVerdict = sealed { Eligible(satisfiedGroup), Ineligible(missingByGroup), Uncertain(reason: UncertainReason) }` |
| **동반 산출** | 요구 면허 전체 집합, 그룹별 미충족 집합, **파싱 실패 행 수**, 판정에 쓰인 요건 소스 집합 |

legacy의 출력은 `verdict: str` + 표시용 튜플들이라 사유가 구조화돼 있지 않다
(`app/services/license_eligibility.py:225-237`).

#### 3.2.2 결합 규칙 — **`lmtGrpNo` = 요건 묶음** (**U-8**)

> **그룹 간 OR · 그룹 내 AND.** `lmtGrpNo` 결측 행은 **하나의 그룹으로 묶어 AND로**
> 해석한다(OR로 흩는 것보다 보수적이다).

층은 **`authoritative`**다 — 운영자 결정이 legacy의 가정을 명세로 올렸다.

**legacy는 이것을 가정으로 공시했다.** `GROUP_SEMANTICS_ASSUMPTION`이
*"가정 — 실데이터로 재검증 필요"*라 적고(`app/services/license_eligibility.py:107-111`),
**대안 해석(`lmtGrpNo`가 공동수급체 구성원 슬롯)**까지 적어 둔다(`app/services/license_eligibility.py:33-40`).
결측 행의 AND 폴딩 근거도 코드에 있다(`app/services/license_eligibility.py:128-130`).

**한계(반드시 적는다)**: 이 모듈은 legacy에서 **소비자가 없다**고 스스로 적는다
(`app/services/license_eligibility.py:44-45`). **확정된 의미론이 운영에서 검증된 적이
없다.**

**이 결정이 닫는 활성 `OPEN`은 없다 — 그 축을 소유한 `OPEN`이 이미 닫혀 있기 때문이다.**
**`OPEN-QUAL-01`이 그 소유자다**(`capability-map.md` §12.1 · §12.2). 그 항목의 결정은 U-8과
**같은 문장** — **(a) 그룹 간 OR / 그룹 내 AND** — 이고 **운영자 결정 2026-08-26으로
해소**됐다. **U-8은 그 확정을 2026-08-28에 재확인한 것**이며, 이 절은 그것을 데이터 사전의
타입 언어로 다시 적는다. **정본 등재는 `capability-map.md` §12.2의 그 행이 갖는다** —
그 파일은 out_of_scope이고 이 문서가 고치지 않는다.

**층이 `authoritative`인 근거는 운영자 결정 하나가 아니다.** `OPEN-QUAL-01` 행의 근거 칸이
**조달청 OpenAPI 참고자료(나라장터 입찰공고정보서비스)**를 든다 — 형제 필드
indstrytyMfrcFldList가 "와"를 `^`로, "또는"을 대괄호로 구분한다고 결합 표기를 명문화하며,
공동수급은 자기 필드군을 따로 가지므로 **대안 해석(공동수급체 구성원 슬롯)이 배제된다.**
`data-extract.md`의 `authoritative` 정의 — **공식 규정/API 문서 또는 승인된 업무 규칙** —
에 그대로 걸린다.

> **⚠ 정정** — 앞서 이 자리는 *"`OPEN-QUAL-11`은 이 등재로 **해소**된다"*고 적었고
> **그것은 거짓이었다.** 그 `OPEN`이 묻는 것은 **`permsnIndstrytyList`(허용업종) 보유가
> 그 요건 그룹을 단독으로 충족시키는가**이고(§3.2.5), U-8이 정한 것은 **`lmtGrpNo` 그룹
> 들이 서로 어떻게 결합하는가**다. **다른 질문이고, U-8은 그 선택지 (a)/(b) 어느 쪽도
> 고르지 않는다.** **U-8 자체는 그대로 유효하다** — 바뀌는 것은 그 결정이 어느 `OPEN`을
> 닫는가뿐이다. 정정의 근거와 운영자 판정은 §11이 적는다.
>
> **⚠ 그 정정의 사유도 틀렸다 (2026-08-30, 같은 날 재정정).** 앞서 이 자리는 *"이 결합
> 축을 소유한 `OPEN`은 따로 없었다"*고 적었고 **그 부재 주장이 거짓이다** — `OPEN-QUAL-01`이
> 소유했고 **이미 닫혀 있었다**(위 두 문단). **결론(U-8이 닫는 활성 `OPEN`은 없다)은 그대로
> 서지만 사유가 반대다: 소유자가 없어서가 아니라 소유자가 이미 닫혀 있어서다.**
> 앞서 이 자리가 *"이 절이 그 결정을 소유한다"*고도 적었으나 **정본 등재는 상류
> `capability-map.md` §12.2의 `OPEN-QUAL-01` 행이 갖는다** — 이 절은 그것을 사전의 언어로
> 다시 적는 자리다. **부재 주장(「소유한 `OPEN`이 없다」)도 그 자리를 열어 재고 나서
> 적는다** — 부재는 확인이 더 어렵고, 확인 없이 적으면 **실재하는 결정을 못 보게 만든다.**

**`OPEN-QUAL-09`(시공능력 요건 미달을 즉시 실격으로 볼 것인가, 적격심사 감점으로 볼
것인가)도 별개로 유지**된다 — 그것은 결합 규칙이 아니라 판정 결과의 문제다.

#### 3.2.3 `UncertainReason` — 네 값 확정 (**U-5**)

> `UncertainReason = sealed { RequirementDataAbsent, RequirementUnparsable,
> OperatorLicensesNotDeclared, CollectionFailed }`

- 앞 셋은 legacy의 evidence 문자열 셋에 대응한다
  (`app/services/license_eligibility.py:161-164`). **`CollectionFailed`는 수집 축(COL)에서
  온다** — legacy evidence에 대응물이 없다.
- **sealed이므로 필요해지면 variant를 더한다.** 그때는 명세 변경이지 데이터
  마이그레이션이 아니다.
- **`Uncertain ≠ Ineligible`.** 보유 면허 미기재는 "미보유"가 아니다. legacy도 이 규율을
  갖고 회귀 테스트로 고정한다(`app/services/license_eligibility.py:132-137`).
- `OPEN-QUAL-06`이 못박은 *"사유 enum은 legacy evidence 문자열을 옮기는 것이 아니라 도메인
  명세가 먼저 정한다"*를 이 결정이 충족한다 — **운영자가 도메인 판단으로 정했다.**

`OPEN-QUAL-06`은 이 등재로 **해소**된다(§11).

#### 3.2.4 면허 유효기간 — 다루지 않는다 (**U-7**)

> **V2는 면허 유효기간을 보지 않는다.** `OPEN-QUAL-05`는 **알려진 제한**으로 기록되고
> 후속으로 미뤄진다.

**남는 위험**: **만료된 면허로 자격을 오판정할 수 있다.** 운영자가 단일 사업자이고 보유
면허를 직접 관리하므로 현재는 감당 가능한 위험이나, **자격 판정 결과에 "유효기간
미검증"이 드러나야 한다.** 침묵하면 검증된 판정과 구별되지 않는다.

`v2-지침서.md` §4.2가 유효기간을 versioned policy data로 요구하므로 **이 결정은 지침서
서술과 어긋난다.** 지침서를 고치는 것은 이 slice의 범위 밖이다 → §13에 인계한다.

#### 3.2.5 `permsnIndstrytyList` — 요건 소스에 포함한다

운영자 결정 `OPEN-QUAL-02`가 포함을 확정했다. **잔여**: 허용 업종 보유만으로 충족인지
제한 면허와 함께여야 하는지는 조달청 문서에 없다. `capability-map.md` QUAL-03이 그 잔여를
**조건부 acceptance**로 분리해 두었으므로 이 문서도 확정하지 않는다 — 결정 전 임시 처리는
**사유 있는 `Uncertain`**이다(과차단·과추천 어느 쪽으로도 틀리지 않는 쪽).

**그 잔여를 소유하는 것이 활성 `OPEN-QUAL-11`이다** — `capability-map.md` §12가 선택지를
**(a) 허용업종만 보유해도 그 그룹 충족 / (b) 제한 면허를 보유한 상태에서만 대체 경로로
인정**으로 적는다. **U-8은 이 둘 중 어느 쪽도 고르지 않는다**(§3.2.2의 ⚠ 정정).

**운영자 판정(2026-08-30)**: **지금 답하지 않고 다시 열어 둔다.** 조달청 문서에 근거가
없으므로(0A2 라운드 5 재확인) **관측으로만** 답할 수 있고, **M1 자격 slice(1C)가 실제
공고로 관측해 닫는다.** 근거 없이 (a)/(b) 중 하나를 고르면 이 slice가 반복해서 데인
형태 — **미결을 확정으로 쓰기** — 가 된다.

### 3.3 하한 미달 빈도 rule

| 자리 | 정의 |
| --- | --- |
| **입력** | 추천 투찰율(`BidRate`) · 낙찰하한율(`FloorRate`) · 표본 집합 · 기준일 |
| **출력** | `FloorShortfall = sealed { Measured(frequency, criticalAssessmentRate, numerator, denominator, band, biasDirection, policyVersion), Unmeasurable(reason: FloorUnmeasurableReason) }` |

**`Measured`가 나르는 수 넷의 단위와 basis** — 전수는 §12.2에 있다.

| 필드 | 단위 | basis / 축 |
| --- | --- | --- |
| `frequency` | fraction | **`numerator` ÷ `denominator`**. **확률이 아니라 표본 비율**이다 |
| `criticalAssessmentRate` | fraction | **사정률 축**(예정가 ÷ 기초금액). `= 추천 투찰율 ÷ 낙찰하한율` |
| `numerator` | 건수 | 하한 미달로 판정된 표본 수 |
| `denominator` | 건수 | 밴드 필터를 통과해 분모에 남은 표본 수 |

**분자·분모를 비율과 함께 나르는 이유**는 성숙도와 같다(§6.4) — 비율 하나만 남기면
표본 규모가 사라진다.

**핵심 관계**: `임계 사정률 = 추천 투찰율 ÷ 낙찰하한율`이고 **하한 미달 ⟺ 실현 사정률 >
임계 사정률**. 이 basis 관계를 타입과 설명으로 보존한다(`v2-지침서.md` §4.4).

**정직 명세 셋을 계승한다.**

1. **확률이라고 부르지 않는다.** 필드·이름 어디에도 확률을 쓰지 않고 빈도만 쓴다.
2. **표본 부족은 `0.0`이 아니라 부재다.**
3. **판정 불가 사유를 분리한다** — 운영자가 취할 다음 행동이 다르기 때문이다.

**사유 어휘 — 도메인 사유와 인프라 실패를 가른다.** legacy는 넷을 한 축에 둔다
(`app/services/notice_floor_shortfall.py:49-88`): 낙찰하한율 미해석 · 하한 모델 미적용 ·
투찰율 산출 불가 · **산출 오류**. 앞 셋은 도메인 사유이고 **넷째는 인프라 실패다.**

> `FloorUnmeasurableReason = sealed { FloorRateUnresolved, FloorModelNotApplicable,
> BidRateUnavailable, SampleInsufficient(required, actual) }`
>
> **인프라 실패는 이 어휘에 넣지 않는다.** 산출 중 오류는 `Unmeasurable`이 아니라
> **실패**로 다뤄야 한다 — 사유 목록에 넣으면 "재지 못했다"와 "고장났다"가 같은 값이 되고,
> 운영자가 취할 행동(기다린다 vs 고친다)이 갈리지 않는다.

`SampleInsufficient`를 명시 variant로 두는 이유: `capability-map.md` DEC-04 acceptance가
*"개연 범위 밖 표본이 분모에서 빠지고, 그 결과 표본 부족이 되면 판정 불가로 전이된다"*를
요구하므로 **전이의 도착점에 이름이 있어야 한다.**

**편향 방향을 구조화 필드로 노출한다**(`biasDirection`, 운영자 결정 `OPEN-DEC-06`).
legacy가 스스로 공시한 편향이 있다 — 표본 필터가 사정률 `1` 근방 밴드(§1.4 **B3**)를
통째로 버려서, **임계 사정률이 `1` 근방이면 방향 보장이 없다**
(`app/services/floor_shortfall.py:27-46`). 측정 가능한 편향을 숨기지 않는다.

**최소 표본 수는 정책 데이터다** — §4.3.

### 3.4 기초금액 provenance rule

`v2-지침서.md` §4.3이 라벨 집합을 **승인 명세로** 정한다.

> `BaseAmountProvenance = sealed { Clean, DerivedYega, DerivedVat, SuspectRatio, Unknown }`
>
> **승인 명세의 다섯 값 그대로다.** 이 문서는 그 집합을 바꾸지 않는다 —
> 바꿔야 할 근거가 있으면 아래처럼 **`OPEN`으로 등록**하고 결정을 기다린다.

**legacy 실측이 그 다섯에 정확히 겹치지 않는다.** 두 가지가 남는다.

1. **여섯째 라벨 `suspect-fractional`** — legacy가 선언한 라벨은 `clean` · `derived-yega` ·
   `derived-vat` · **`suspect-fractional`** · `suspect-ratio` 다섯이고
   (`app/services/base_amount_basis.py:26-41`), 그중 `suspect-fractional`은 승인 명세에
   대응하는 이름이 없다.
2. **미판정 상태** — 컬럼이 nullable이라 **`NULL`이 별도 상태**이고,
   **소비자가 `NULL`을 `clean`과 같게 취급한다**:
   *"A `clean` row (or an unclassified `NULL` basis — the common case for open notices)
   returns its `base_amount` unchanged"*(`app/services/bid_base.py:84-104`).
   멱등 마커는 별도 컬럼이다(`app/models/models.py:264-306`의 `basis_checked_at`).

> **바뀌지 않는 요구**: **"아직 판정하지 않음"이 "신뢰함"으로 접히면 안 된다.**
> 그 요구를 **승인 명세의 어느 variant가 나르는가**(`Unknown`에 접는가, 별도 variant를
> 더하는가)는 이 문서가 정하지 않는다 → **`OPEN-DIC-05`**.

**first-match 순서가 load-bearing이다.** 비율 의심이 정수 판정 앞, 정수 판정이 VAT 파생
앞이며 그 이유가 코드에 선언돼 있다. **순서를 정책 데이터로 선언하고 테스트와 정책
version으로 고정한다**(`v2-지침서.md` §4.3).

> **규칙**: **원본 값을 조용히 교정하지 않는다.** 원본 · 판정 · 근거 · 정책 version을 함께
> 보존한다. 복구 추정치는 **별도 필드**이며 원본 필드에 기록되지 않는다.

**허용 오차는 명명된 정책 값이다**(단위 포함) — §4.3·§12. legacy는 이것들을 코드 상수로
선언하고 단위를 주석에만 둔다(`app/services/base_amount_basis.py:43-55`).

**분류기가 스스로 인정한 주장 범위를 명세로 옮긴다**: 이 규칙은 "개찰 전 정보만 쓴다"고
주장하지 않으며 실제 주장은 한 문장이다 — **"두 금액이 서로 모순이면, 어느 쪽이
파생값이든 그 행을 ground truth로 쓸 근거가 없다."**

**신뢰 비율 상한은 이 문서가 값을 쓰지 않는다** — 마진이 활성 **`OPEN-DEC-07`**의 재유도
대상이다(§4.3).

### 3.5 하한 적용 범위 rule

legacy 어휘 넷 — `applicable` / `not_applicable`(비국가기관) / `uncertain`(이름만으로
판별 불가) / `separate_regime`(별도 행정규칙)
(`app/ai/floor_applicability.py:78-96`).

> `FloorApplicability = sealed { Applicable(regime), NotApplicable(reason),
> Uncertain(reason), SeparateRegime(regime), **OutOfScope(regime)** }`

**`OutOfScope`가 0C가 더하는 variant다.** 운영자 결정 `OPEN-DEC-09`가 지방계약(지자체)을
명시 제외로 확정하면서 **필수 단서**를 달았다 — legacy의 "명시 제외"는 docstring 한 줄뿐이고
**런타임에는 지자체가 기본값 `applicable`에 남는다**
(`app/ai/floor_applicability.py:122-130`이 *"조직 종류 어미(공사/공단/청/시/군)는
국가·지자체 기관을 뜻하므로 여기 없고 기본값 `applicable`로 남는다"*를 적는다).

> **`OutOfScope`는 "실행되는 미적용 상태"다.** 지자체 공고를 국가계약 하한으로 조용히
> 판정하지 않고, **하한 체계 미지원을 관측 가능한 결과로 산출한다.** 선언과 실행이
> 일치해야 한다.

**첫 매칭 우선 순서가 도메인 의미를 갖는다** — 두 패턴에 걸리는 기관명에서 더 구체적인
쪽이 이긴다(`app/ai/floor_applicability.py:122-130`). 이 순서도 정책 데이터로 선언한다.

### 3.6 투찰 판정 rule — `Verdict`

legacy는 게이트 사다리 first-match로 `action`을 정하고, **두 보류가 같은 `"skip"`이며
한국어 문장으로만 구분된다**(`app/services/allocation_core.py:163-181`).

> `Verdict = sealed { BidNow(reasons), Review(reasons), Skip(reason: SkipReason) }`
> `SkipReason` **필수**. 최소 두 값: `CapacityHold` · `LowPriority`.

운영자 결정 `OPEN-DEC-05`가 확정한 것은 **하나의 `Skip` verdict + 필수 reason code**이며,
금지되는 것은 한-verdict가 아니라 **문장으로만 구분되는 형태**다. 목록의 전수성은 M1에서
게이트 사다리를 옮길 때 확정된다 → **`OPEN-DIC-03`**.

**force-bid 우회는 유지하되 출처를 판정 결과에 노출한다**(운영자 결정 `OPEN-STR-03`) —
우회의 존재를 감춘 것이 결함이었지 우회 자체가 아니다.

**운영자 하한 override**: 개연 밴드 밖 override는 **버리지도 통과시키지도 않고 사유와 함께
거부**한다(운영자 결정 `OPEN-DEC-04`). 거부가 **관측 가능한 결과**여야 한다.

---

## 4. 축 4 — 정책 version과 effective date

> **이 축은 legacy에서 옮겨올 형태가 거의 없다.** `policy_version` 개념이 legacy에 없고,
> 유효일자를 다루는 실물은 아래 하한율 표 하나다. **이 절은 운영자 결정(U-6·U-6b)과
> 지침서 규율에서 나온다.**

### 4.1 `PolicyVersion` — 날짜로 식별한다 (**U-6b**)

> `PolicyVersion(effectiveFrom: LocalDate, source: String)`
>
> 정책은 `EffectiveDatedPolicy<T>(entries: List<(effectiveFrom, T)>)`로 선언하고,
> `resolve(referenceDate) → Resolved(T, PolicyVersion) | NotApplicable(reason)`으로 푼다.

**날짜를 고른 근거가 legacy에 실물로 있다.** `app/ai/predictors/legal_floor_spec.py:53-103`의
`_FloorSchedule(effective_from, brackets)`가 이 저장소에 존재하는 **유일한 effective-date
표**다. 사람이 읽을 수 있고 순서가 자명하며, 법정 하한처럼 시행일이 있는 정책에 자연스럽다.

**계승할 규율 둘.**

1. **기준일은 대상 자신의 날짜다.** 공고를 판정할 때는 **공고 기준일**을 받는다.
2. **기준일을 모르면 표를 적용하지 않는다.** `None`을 돌려주고 호출부가 기존 값을
   유지한다 — **소급 적용 금지**(`app/ai/predictors/legal_floor_spec.py:14-18`).
   `date.min` sentinel 대신 **`Initial` variant**를 쓴다(sentinel을 값으로 두지 않는다).

**한계(반드시 적는다)**: **날짜는 같은 날 두 번 바뀌는 정책을 구별하지 못한다.**
필요해지면 그때 넓힌다 — **관측 뒤에 넓힌다.**

**version 하나로는 부족하다는 것을 legacy가 실측으로 배웠다.** ML 축의 아티팩트 계약이
`artifact_version` · `model_version` · `sample_scope`를 **전부 필수·기본값 없음**으로
선언하고 그 이유를 적는다 — *"`sample_scope`는 피처 공간과 **직교하는 축**이다: 같은
피처로 학습해도 코퍼스가 서빙 모집단이냐 혼합 모집단이냐에 따라 다른 모델이 된다"*
(`app/ai/predictors/artifact_contracts.py:196-226`).

> **규칙**: 정책·모델 식별은 **최소 두 축** — `ruleVersion`(규칙이 무엇인가)과
> `corpusScope`(입력 모집단이 무엇인가). **한 축만 두는 설계를 채택하지 않는다.**
> "규칙이 안 바뀌었는데 입력 모집단이 바뀐" 경우를 version 하나가 잡지 못한다.

**sentinel version을 만들지 않는다.** legacy는 `model_version default="current"` ·
`strategy_version default="local"`이라 그 행으로 어느 정책이 판정을 냈는지 재현할 수 없다
(`app/models/models.py:458-477`).

> `DecisionProvenance(policyVersion, modelArtifactId, inputSnapshotHash, decidedAt)` —
> **기본값 없는 비-nullable.** 기본값이 있는 version 필드를 만들지 않는다.

### 4.2 어떤 판정이 정책 version을 싣는가 — **U-6 전수 확인**

**운영자 결정(U-6)**: *"정책 version은 「수치가 바뀌는 판정」에만 싣는다. **대상**: 임계·
계수가 개입하는 판정 — 투찰가 · 하한 여유 · 성숙도 · ML 추론. **제외**: 면허·지역 같은
순수 규칙 판정."*

이 결정이 0C에 **전수 확인**을 위임했다. `capability-map.md`에서 정책 version을 말하는
자리 전수는 `commands.md` **C-5**가 낸다.

**전수를 훑으면 요구가 한 축이 아니라 두 축이다.** 먼저 그것을 가른다.

| 축 | 요구 | 출처 |
| --- | --- | --- |
| **축 A — 값의 외부화** | 정책 값을 **versioned policy data로 두고 코드 상수로 묻지 않는다** | `v2-지침서.md` §5(매직넘버 금지) |
| **축 B — 판정 결과의 표기** | **판정 결과에 그 판정이 쓴 policy version을 함께 남긴다** | 각 capability의 acceptance |

**U-6가 정한 것은 축 B다** — *"정책 version을 **싣는다**"*. **축 A를 U-6가 제한하지
않는다.** 값의 외부화는 지침서 규율이며 면허·지역 정책 데이터에도 그대로 걸린다.

축 B의 전수와 판정:

| capability | 요구 문면(요지) | 판정의 산출물 | 임계·계수 개입 | U-6 열거로 덮이는가 | 판정 |
| --- | --- | --- | --- | --- | --- |
| **DEC-03** 법정 하한 해석 | 기관 유형 → 모델 매핑이 versioned policy이고 시행일 표가 기준일로 풀린다 | **율**(수치) | 예(구간 경계·율) | 예 — 「하한 여유」 계열 | **덮인다** |
| **DEC-04** 하한 미달 빈도 | 결과가 `(…, 정책 version)` 묶음 | **빈도**(수치) | 예(최소 표본 수·밴드) | 예 — 「하한 여유」 | **덮인다** |
| **DEC-08** 기초금액 provenance 분류 | 분류에 쓴 정책 version이 결과와 함께 저장 | **라벨**(수치 아님) | 예(허용 오차 다섯) | **아니다** | **기준절로만 덮인다** |
| **SET-06** 정산 성숙도 | 시간축 값에 적용된 정책 version이 함께 남음 | **성숙도 비율**(수치) | 예(embargo 임계) | 예 — 「성숙도」 | **덮인다** |
| **ML-07** 모델 승격 게이트 | 게이트 리포트가 자신이 쓴 정책 version을 싣는다 | **게이트 판정** | 예(MDE·임계) | 예 — 「ML 추론」 | **덮인다** |
| **QUAL-03** 면허 그룹 AND/OR 판정 | **「결정 무관(무조건)」 항목 둘**이 판정 결과에 policy version을 요구 | **자격 판정**(수치 아님) | **아니다** | **아니다** | **덮이지 않는다 — 반례** |
| **QUAL-01** 면허 자격 판정 | 「legacy 형태 처리」가 *"정책 version 식별자로 참조한다"* | **자격 판정** | 아니다 | 아니다 | **덮이지 않는다**(acceptance는 아니다) |

축 A로만 걸리는 자리(참고 — U-6의 대상이 아니다): **DEC-03**의 시행일 표 · **DEC-05**의
투찰가 계수(*"코드 상수인 형태는 폐기 — versioned policy 데이터로"*) · **ML-11**과
`OPEN-ML-05`의 predictor 정책 값 · 운영자 결정 `OPEN-COL-02`의 `resultCode` 분류표 ·
`OPEN-SET-10`의 시간축 대체 승인 정책.

**결론 둘.**

1. **대상 판별의 정본은 결정의 기준절(「임계·계수가 개입하는 판정」)이고, 열거 넷은
   예시다.** `DEC-08`은 산출물이 라벨이라 열거로는 걸리지 않고 기준절로만 덮인다.
   0C는 기준절을 정본으로 쓴다 — 결정 자신이 그 문면을 갖고 있으므로 인용이지 확대가
   아니다.
2. **파생 규칙이 하나 필요하다.**
   > **수치 판정의 입력이 되는 규칙표는 그 수치 판정의 정책 version에 속한다.**

   이 규칙이 없으면 `DEC-03`의 **기관 유형 → 모델 매핑**과 `SET-06`의 **시간축 대체 출처
   승인 규칙**이 "순수 규칙 판정"으로 읽혀 제외된다. 둘 다 그 자체로는 규칙이지만
   **바뀌면 산출 수치가 바뀐다.**

**보고 대상 — `QUAL-03`이 U-6의 반례다. 고치지 않는다.**

`capability-map.md` QUAL-03의 **「결정 무관(무조건)」** acceptance 가운데 **축 B에 해당하는
둘**이 policy version을 요구한다 — 판정 결과에 어떤 policy version으로 판정했는지가 남을
것, 요건 소스 집합과 결합 규칙의 policy version이 결과에 함께 남을 것. (셋째 항목 —
그룹 의미론 변경이 코드가 아니라 정책 데이터 version의 변경일 것 — 은 축 A다.)
**QUAL-03은 면허 판정이고 임계·계수가 개입하지 않으며, U-6가 제외로 이름 붙인
「면허·지역 같은 순수 규칙 판정」 그 자체다.**

같은 방향의 요구가 둘 더 있다 — `v2-지침서.md` §4.2가 *"별칭, 포괄 코드, 유효기간,
지역·협회 조건은 versioned policy data로 관리한다"*를 요구하고, `capability-map.md` §12.2의
`OPEN-QUAL-02` 잔여가 *"versioned policy로 선언하고 policy version을 판정 결과에 남긴다"*를
적는다.

**이 문서는 이 충돌을 해소하지 않는다** → **`OPEN-DIC-01`**. 두 읽기 중 어느 쪽인지가
운영자 결정의 범위이기 때문이다. 그때까지 면허 축 acceptance는 `capability-map.md`가 확정한
문면대로 두고, 이 문서는 §3.2에서 면허 정책 데이터에 version을 요구하지도 금지하지도
않는다.

### 4.3 정책 값과 미분류 상수 (**U-10** — 0C가 확인했다)

**운영자 결정(U-10)**: *"운영 중에 바꾼 값이 없다. 따라서 「바꾼 적 있는가」로는 policy와
상수를 가를 수 없고 **근거 주석 유무**를 잠정 기준으로 쓴다."* 그리고 결정이
**0C에 확인을 위임했다** — *"주석에 근거가 있다 — 0C가 그 근거를 직접 열어 확인하고
옮긴다."*

**확인 결과는 아래 표의 「값의 근거인가」 칸이 낸다** — 셈을 여기 옮겨 적지 않는다.
가른 기준 하나는 적어 둔다: **값이 무엇을 하는지의 서술은 근거가 아니다.** 근거는
**왜 그 값인지**를 말해야 한다.

| 값 | U-10 표에서의 자리 | 주석이 말하는 것 | 값의 근거인가 | 근거 위치 |
| --- | --- | --- | --- | --- |
| `MIN_ASSESSMENT_SAMPLES` = `150` | 자체 근거를 단 행 — **확인 위임 대상이 아니다** | 이항 비율 표준오차 유도 | **예** | `app/domain/floor_shortfall.py:42-57` |
| `BID_BASE_TRUST_RATIO_MAX` = `1.15` | **확인 위임 행** | 부가세 배수 + 측정 마진의 합으로 분해 | **예** | `app/core/constants.py:158-177` |
| `clamp_bid_rate` `0.7` ~ `1.4` | **확인 위임 행** | *"Keep scenario bid rates inside a realistic bidding band."* | **아니다** — 용도 서술 | `app/ai/predictors/historical/statistics.py:65-67` |
| `_BASE_ADJUSTMENT` = `0.15` | **확인 위임 행** | *"recommended anchor: 15% up from the floor"* | **아니다** — 무엇을 하는지 | `app/ai/bid_target.py:20-25` |
| `_DISPERSION_REFERENCE` = `0.02` | **확인 위임 행** | *"winning-rate std at which dispersion fully lifts…"* | **아니다** — 무엇을 하는지 | `app/ai/bid_target.py:20-25` |
| `_MAX_ADJUSTMENT` = `0.85` | **확인 위임 행** | *"never let the signal alone push the recommended past…"* | **아니다** — 무엇을 하는지 | `app/ai/bid_target.py:20-25` |

**U-10의 기준을 확인된 문면에 적용하면 위 표에서 「아니다」로 찍힌 것들이 「미분류」로
간다.** U-10 자신이 그 방향을
보호한다 — *"「근거 없는 계수」를 정책으로 승격하면 「정책이니 바꿀 수 있다」로 읽히므로,
미분류는 미분류로 남긴다."*

#### 4.3.1 분류

| 분류 | 값 | 처리 |
| --- | --- | --- |
| **정책 (근거 있음)** | `MIN_ASSESSMENT_SAMPLES` = `150` | **versioned policy 데이터로 외부화.** 근거 문자열 = "통계적 편의(운영자 승인 2026-08-26)". 코드에 상수로 묻지 않는다. 재유도 대상이 **아니다** |
| **정책이나 값 미정** | `BID_BASE_TRUST_RATIO_MAX` = `1.15`(구성: 부가세 배수 `1.1` + 마진 `0.05`) | **마진이 활성 `OPEN-DEC-07`의 재유도 대상**이다. 이 문서는 값을 승계하지 않고 **자리와 구성만** 적는다 |
| **미분류 (근거 없음)** | `clamp_bid_rate` `0.7`~`1.4` · `_BASE_ADJUSTMENT` `0.15` · `_DISPERSION_REFERENCE` `0.02` · `_MAX_ADJUSTMENT` `0.85` | **`OPEN-ML-05` 유지.** 정책으로 승격하지 않는다 |
| **미분류 (근거 없음)** | confidence 산식 계수 아홉(`12` · `0.06` · `0.08` · `0.07` · `0.54` · `0.2` · `0.18` · `0.45` · `0.95`) · `_PRICE_REGIME_RULES` confidence(`0.58` · `0.86` · `0.84` · `0.82`) · fallback(`0.45`) | **`OPEN-ML-05` 유지.** §6.5 참조 |

**이 기준의 한계**: **근거 주석이 있다고 정책인 것은 아니고, 없다고 상수인 것도 아니다.**
잠정 분류이며 **M5에서 ML 이식 시 재판정한다.**

#### 4.3.2 정책 데이터로 외부화되는 자리 (전수 아님 — 이 문서가 정의를 쓴 것만)

| 자리 | 단위 / basis | 소유 |
| --- | --- | --- |
| 최소 표본 수 | 건수 / 표본 | §3.3 |
| 사정률·투찰비·게시 하한율 밴드(§1.4 B1~B7) | fraction / 각 축 | §1.4 |
| provenance 판정 허용 오차와 배수 | §12 참조 | §3.4 |
| 복수예비가격 개수 | 건수 | §3.4 |
| 법정 하한율 시행일 표 | fraction / 예정가격 기준, 경계는 원 / 추정가격 | §4.4 |
| 반올림 자리수 | 자리수 | §1.1 |
| 부가세율 | fraction | §1.2 |
| 기관 유형 → 하한 모델 매핑 순서 | — | §3.5 |
| provenance rule의 first-match 순서 | — | §3.4 |
| KONEPS `resultCode` 분류표 | — | 운영자 결정 `OPEN-COL-02` |

### 4.4 유일한 effective-date 실물 — 법정 하한율 표

`app/ai/predictors/legal_floor_spec.py:53-103`. **층은 `legacy-behavior`이고 V2 값의 확정은
활성 `OPEN-DEC-10`(특정 예규의 추정가격 구간별 차등)이 소유한다.** 이 문서는 **형태**를
계승하고 **값을 확정하지 않는다.**

| schedule | `effectiveFrom` | 구간(추정가격, 원) | 율(예정가격 기준 fraction) |
| --- | --- | --- | --- |
| 신율 | `2026-01-30` | `0` ~ `1,000,000,000` / `1,000,000,000` ~ `5,000,000,000` / `5,000,000,000` ~ `10,000,000,000` | `0.89745` / `0.88745` / `0.87495` |
| 구율 | `Initial` | 같음 | `0.87745` / `0.86745` / `0.85495` |

- 구간 경계는 **[하한, 상한)**이고 단위는 **원**, 기준 금액은 **추정가격**이다
  (`app/ai/predictors/legal_floor_spec.py:20-33` · `app/ai/predictors/legal_floor_spec.py:43-50`).
- **추정가격 상한 이상 구간은 표의 대상이 아니다**(종합심사낙찰제) → bracket을 두지 않고
  `NotApplicable`을 낸다.
- **legacy가 스스로 경계 위험을 적는다** — 구간 판정 입력이 추정가격인데 과세 여부에 따라
  VAT 포함/제외가 비일관적이라 **경계 부근에서 구간 오분류가 가능하다**
  (`app/ai/predictors/legal_floor_spec.py:20-33`). **U-1이 정의를 확정했으므로 V2에서는
  이 위험이 「정의 미결」이 아니라 「저장 값 비일관」으로 좁아진다**(§1.2.1).

---

## 5. 축 5 — canonical KONEPS fact와 derived fact

### 5.1 `FactProvenance` — 값이 어디서 왔는가

legacy의 가장 성숙한 장치가 이 축에 있다. `EstimatedAmountSource` 넷과 **권위 집합이
하나뿐**임을 데이터로 선언한다(`app/core/constants.py:179-222`).

> `FactProvenance = sealed { Published(noticeRevision), DerivedFromOpening,
> FilledFromBudgetKey(key), CopiedFromBaseAmount, OperatorDeclared, Undeclared }`
>
> `isAuthoritative`는 **술어가 아니라 데이터로 선언한다** — 어휘가 늘 때 "덮을 수 있는가"를
> 한 자리에서 정하고, 빠뜨리면 기본이 **보수(fill-only)**다.

**`OperatorDeclared`가 왜 여기 있는가 — 다섯으로는 덮이지 않는 자리가 실재한다.**
legacy에서 도출한 나머지는 **전부 KONEPS 수집 경로 안의 해석 경로**다(게시 · 개찰 역산 ·
예산 키 폴백 · 기초금액 사본 · 미신고). 그런데 `v2-지침서.md` §4.1이 **모든 `Money`에
`provenance`를 필수**로 두고, **KONEPS를 거치지 않는 금액이 실제로 있다** — 운영자가
직접 넣는 시공능력평가액이며 legacy가 그것을 **수기 입력** 컬럼으로 갖는다
(`app/models/models.py:161-182`). 그 값을 나를 자리가 다섯 안에 없다.
**§5.1이 어휘가 느는 경우를 설계에 넣어 둔 것이 이 자리다.**

- **`isAuthoritative`는 이 값에 대해 참이다.** 근거 둘 — ① legacy 실물이 **연 1회 갱신**이라
  (같은 자리) 덮지 못하면 갱신 자체가 불가능하다. ② 점유 가드가 막으려는 것은 **권위 없는
  자동 유입**이 이미 있는 값을 조용히 바꾸는 것이고(`app/services/koneps/budget_fields.py:24-34`),
  사람의 명시 신고는 그 대상이 아니다.
- **`OperatorDeclared`는 KONEPS fact의 자리에 들어갈 수 없다.** §5.2의 경계 규칙이
  **그대로 적용되지는 않는다** — 그 규칙의 원문은 *"derived fact가 canonical fact의 자리를
  차지할 수 없다"*이고 `OperatorDeclared`는 **derived도 canonical KONEPS fact도 아니다.**
  받는 것은 그 규칙의 **둘째 문장이 세운 같은 형태**다 — *"자리가 같아야 한다면 타입이
  달라야 한다"*.
- **`NoticePublished`라는 이름은 두지 않는다** — `Published(noticeRevision)`와 같은 것을
  가리키는 둘째 이름이다. **셋째 규율이 막는 것은 이것이 아니다** — 그 규율은 어휘
  **문자열**의 겹침을 막고 여기에는 겹치는 문자열이 없다. 받는 자리는 **§1.2 머리**이고
  적용은 축어가 아니라 **같은 형태**다(§1.2.4의 ⚠ 정정).

**계승할 규율 셋.**

1. **권위값만 저장값을 덮는다(점유 가드).** 나머지와 **미신고**는 **빈 자리만** 채운다.
   legacy가 그 강도를 구별해 적는다 — 존재 가드("유입이 비었으면 지우지 않는다")보다
   강한 점유 가드("이미 값이 있으면 권위 없는 유입으로 바꾸지 않는다")이며, **그 자리가
   판정의 분모라서 존재 가드로는 부족하다**(`app/services/koneps/budget_fields.py:24-34`).
2. **미신고(`Undeclared`)를 권위로 취급하지 않는다.**
3. **어휘 문자열이 이웃 축과 겹치지 않게 한다.** legacy는 겹침이 실제 혼동을 만들어
   접두 리네임으로 수습했다(`app/core/constants.py:179-222`).

**⚠ legacy의 판정 함수는 세 값만 낸다** — `notice` / 예산 폴백 / 기초금액 사본. `derived`는
개찰 경로가 따로 넣는다(`app/domain/estimate_provenance.py:40-53`). **어휘 넷과 판정 셋이
어긋난다.** V2는 **KONEPS 수집 경로의 variant 전부를 같은 판정 지점이 낸다** —
`OperatorDeclared`는 그 경로 밖에서 들어오므로 이 문장의 대상이 아니다.

### 5.2 canonical fact와 derived fact의 경계

| 구분 | 정의 | 예 |
| --- | --- | --- |
| **canonical fact** | 공고·개찰이 **게시한 값**을 그대로 받은 것 | 추정가격(`Published`), 기초금액, 게시 낙찰하한율, 자격 원문, 공고 차수 |
| **derived fact** | 우리가 **계산·복원·대체한 값** | 예정가 역산, 복구 추정 기초금액, 투찰율, 임계 사정률, 성숙도 |
| **관측 fact** | 우리가 **관측한 시각·사건** | 정산 관측 시각(§5.4), 수집 시각, 재관측 횟수 |

> **규칙**: **derived fact가 canonical fact의 자리를 차지할 수 없다.** 자리가 같아야 한다면
> **타입이 달라야 한다** — `ResolvedBaseAmount.Direct` vs `.FallbackFromBudget(sourceKey)`.
> "같은 자리에 다른 개념"이 타입으로 구분되게 한다.

**해석 순서는 정책 데이터다.** legacy는 기초금액과 추정가격의 해석 순서를 **별도의 두
상수**로 선언하고 추정가격 순서에는 **기초금액 키를 포함하지 않는다**
(`app/services/koneps/field_contract_spec.py:114-171`). 그 순서가 오염 회귀의 수정
결과이며 도메인 의미를 갖는다. **값이 `0`·미상인 후보는 건너뛴다.**

**코드가 스스로 자백한 취약점을 명세로 옮긴다**: positive-only 축이 프로덕션과 검증기에서
각각 구현돼 **상수 공유로 보장되지 않는다**(같은 자리). V2는 **해석을 한 지점에 두고**
검증기가 그 지점을 호출한다.

### 5.3 필드 레지스트리 — 소비하는 모든 키에 계약을 붙인다

legacy에 정확히 필요한 구조가 있고 **등재율이 낮다.** `FieldContract(raw_name, concept,
basis, scale, zero_padded, present_in, provenance, expected_min, expected_max)`가 선언돼
있으나 `FIELD_CONTRACTS`에 등재된 것은 세 건이고
(`app/services/koneps/field_contract_spec.py:185-238`), `KNOWN_FIELDS`가 선언한 키 집합은
그보다 훨씬 크다(`app/services/koneps/field_contract_spec.py:241-316`; 셈은 `commands.md` **C-7.5**가 낸다 — `capability-map.md` COL-07의 어림수와 다르다, §10.1 **X-7**).

> `KonepsFieldContract(rawName, concept, basis, scale, unit, nullability, vatTreatment,
> authoritative, presentIn, provenance, effectiveFrom, expectedRange)`
>
> **소비되는 모든 키에 필수.** 등재되지 않은 키는 **소비 전에 사람이 검토**한다.

**계승할 규율 둘.**

1. **선언에 없는 키는 "미지 필드"로 리포트되어 조용히 삼켜지지 않는다**
   (`app/services/koneps/field_contract_spec.py:241-316`).
2. **계약이 밴드를 재선언하지 않고 단일 출처를 참조한다** — 계약이 자체 밴드를 들면 DTO
   게이트와 판정이 갈린다(`app/services/koneps/field_contract_spec.py:185-238`).

**식별자는 숫자가 아니다.** 공고 차수(`bidNtceOrd`)는 **제로패딩 식별자**이며 `int` 변환
금지가 계약에 선언돼 있다(같은 자리). legacy가 그 변환으로 1차 공고 전부의 자격을 잃은
회귀가 있다(`regression-ledger.md` `R-QUAL-05`).

`capability-map.md` COL-07이 **"계약 테스트 승격 여부는 0C에서 결정한다"**를 인계했다.
**결정: 승격한다.** 필드 계약은 문서가 아니라 **경계에서 거부하는 계약 테스트**로
존재해야 한다 — 선언만 있고 강제가 없으면 등재율이 낮아지는 것이 legacy가 보여준 결과다.

### 5.4 정산 관측 시각 — 신설 canonical 관측 fact (**U-4**)

> `settlementObservedAt: Instant`를 **수집 어댑터가 write 시점에 기록**한다.
> **파생이 아니라 관측이다.** **V2 가동 이전 데이터는 `Absent`로 남긴다 — 백필하지
> 않는다.**

**legacy에 승계할 출처가 없다는 사실 자체가 조사 결과다.** 후보 넷 전부의 결함이 legacy에
실측으로 적혀 있다(`app/domain/settlement_maturity.py:1-29`) — `announced_at`은 개찰일시
복사본이고, `created_at`은 수집 시각이며, `opening_checked_at`은 거의 비어 있다.
`TenderResultEvent.observed_at`이 구조적으로 가장 가깝지만 nullable이고 채움 보장이 없다
(`app/models/pipeline.py:130-177`).

**없는 값을 추정값으로 오염시키지 않는다.** legacy가 회피(embargo)를 택한 것과 같은
방향이다.

> **파급(반드시 적는다)**: **성숙도 계산이 V2 가동 이전 기간을 제외하게 된다.**
> 성숙도 지표는 **모수 부재 구간을 구별해 노출해야 한다** — 그 구간이 "성숙하지 않음"으로
> 접히면 §1.6의 규칙을 위반한다.

### 5.5 신설 수집 필드 — 시공능력 요건

`ConstructionCapacityRequirement(limitGroupNo, licenseRegionCode, licenseRegionName,
requiredAmount: UnnormalizedFigure)`.

> **`Money`가 아니다.** `Money`는 `unit`·`scale`을 **필드로 갖지 않고**(§1.1) 그 단위를
> **원(KRW) 정수**로 못 박는다 — `v2-지침서.md` §4.1이 정한 형태다. 그러므로 **어떤 값을
> `Money`로 선언하는 것은 그 값의 단위를 원으로 확정하는 것**이고, **서명이 곧 그 주장이다.**
> 이 필드의 원문 단위는 확정되지 않았다(아래) — **그래서 이 자리를 `Money`로 선언할 수
> 없다.** 앞서 이 절은 서명을 `requiredAmount: Money`로 둔 채 세 줄 아래에 *"정해지기
> 전에는 `Money`로 정규화하지 않는다"*를 적어 **한 절 안에서 두 말을 했다**(2026-08-30 정정).

> `UnnormalizedFigure(figure, fieldContract)` — **정규화되지 않은 원문 수치 하나**와
> **그 값을 낸 §5.3의 필드 계약** 한 벌. 단위·`scale`·`vatTreatment`·`provenance`는 전부
> **그 계약이 나르고 값 자신은 아무것도 주장하지 않는다.** 부재는 `Absent(reason)`이며
> `0`이 아니다(§1.3).

- **이 타입을 고른 이유는 비교를 타입으로 막기 위해서다.** 보유액
  (`ConstructionCapacityAmount`, `Money` 한 벌)과 이 요건 값을 견주려면 **정규화가
  선행해야 하고 정규화 규칙은 그 축이 닫혀야 나온다.** `Money`로 선언해 두면 그 비교가
  **컴파일을 통과해 버리고**, §1.4.1이 금지한 「값 크기로 단위를 되짚는 경로」가 그 자리에
  생긴다. **다른 타입을 두면 그 경로가 애초에 만들어지지 않는다.**
- **legacy가 이 필드를 수집하지 않는다**(§1.2.4). 조달청 문서의 서술만이 근거이고
  **그 문서가 단위·과세를 명시하지 않는다**(0A2 라운드 5 확인).
- 따라서 `requiredAmount`의 **`vatTreatment`는 `Unknown`으로 선언한다.** 운영자 보유액의
  과세 처리(U-2, `Inclusive`)를 **요건 값에 전이시키지 않는다** — 운영자 결정은 보유액
  축의 답이고 공고 게시값의 성질을 정하지 않는다.
- **같은 이유로 단위도 전이시키지 않는다.** 문서가 단위를 적지 않는 것은 과세와 같은
  자리의 누락이고, **전이 금지는 두 성질에 똑같이 걸린다.** 그래서 이 문서는 게시값의
  단위를 **원으로 확정하지 않는다** — 그 축은 **활성 `OPEN-QUAL-10`의 게시 요건 절반**이
  소유한다(§11의 ⚠ 정정 · §12.2).
- **정해지기 전에는 `Money`로 정규화하지 않는다 — 서명이 그것을 강제한다.** 수집
  어댑터는 **원문 단위를 §5.3의 필드 계약(`unit`·`scale`)에 붙잡아** 두고, 그 선언이
  없으면 판정에 넣지 않는다. **그 계약을 나르는 자리가 `fieldContract`다.**
- **답은 관측으로만 나온다** — 조달청 문서에 없으므로 **M1이 게시값을 실제로 수집한
  뒤에** 닫힌다. **수집 신설은 M1 이후로 인계**하고(§13.4), 그때까지 금액 capacity 판정은
  비교 대상 부재로 `Uncertain(RequirementDataAbsent)`다.

---

## 6. 축 6 — ML feature와 업무 판단의 경계

### 6.1 경계 규칙 (운영자 결정 `OPEN-ML-01`)

> **수학 커널 = Python `ml-engine` / 업무 판정 = Kotlin.**

`v2-지침서.md` §3.2가 같은 선을 긋는다 — *"Python 응답은 후보·점수·불확실성·모델 근거만
반환한다. 최종 `bid/review/skip`은 Kotlin이 결정한다."*

**데이터 사전이 하는 일은 그 선에 걸리는 필드를 이름으로 고정하는 것이다.**

### 6.2 경계에 걸리는 필드

| 필드 | 현재(legacy) | V2 소유 | 근거 |
| --- | --- | --- | --- |
| `review_required` | **Python이 낸다.** 예측 payload에 실린다 | **Kotlin.** Python이 반환하지 않는다 | `app/ai/price_prediction/price_regime.py:36-42` · `app/ai/price_prediction/price_regime.py:45-69` |
| `regimeLabel` · `signals` | Python | Python(모델 근거) | 같은 자리 |
| `confidence` | Python | §6.5 참조 | 같은 자리 |
| 성숙도 **값** | Python 커널 | Python(`ml-engine`) | §6.4 |
| embargo **적용 여부** | 별도 모듈 | **Kotlin** | §6.4 |
| `Verdict`(`BidNow`/`Review`/`Skip`) | Kotlin 상당 없음 | **Kotlin** | §3.6 |

**`review_required`가 이 경계의 첫 항목이다.** legacy 자신이 그 파일에서 자기 범위를
*"descriptive regime metadata (label / signals / review_required), never the recommended
price"*라 적지만(`app/ai/price_prediction/price_regime.py:1-8`), **`review_required`는
`Verdict`의 `Review`와 같은 축의 업무 판정이다.**

> **규칙**: 데이터 사전은 **"모델 근거"와 "업무 판정"을 서로 다른 계약 필드로** 정의한다.
> 계약(M2 gRPC 스키마)이 이 분리를 강제한다.

### 6.3 피처 계약과 결측 사유

**피처 계약은 M2 gRPC 스키마가 소유하고, 이 문서는 피처 이름이 참조하는 도메인 개념만
정의한다.** 결합 하나를 명시한다 — **`denominator_source` 피처는 도메인 provenance 어휘
(`ReliableBaseSource` 상당)를 그대로 피처 공간에 싣는다**
(`app/domain/award_rate_features.py:60-90`). **도메인 어휘가 바뀌면 피처 공간이 바뀐다.**

**누수 차단이 규율이 아니라 타입으로 돼 있다** — legacy가 이 설계를 명시한다:
*"이 커널의 입력 시그니처가 곧 피처 계약이고 … 예비가격·추첨번호·실현 사정률·낙찰가는
아예 받지 않는다. '쓰지 말자'는 규율보다 '받을 수 없다'는 시그니처가 강하다"*
(`app/domain/award_rate_features.py:8-16`). **이 형태를 계승한다.**

**결측 사유를 나르는 필드가 legacy에 없다.** 결측이 미지 코드나 `0`으로 접힌다
(`app/domain/award_rate_features.py:60-90`). 그 대가가 `capability-map.md` ML-11.4 F3에
기록돼 있다 — 피처 커버리지가 도메인이 아니라 **백필 진행 상태**였고, 학습과 서빙에서 축의
의미가 달랐다.

> `MissingReason = sealed { Unknown, NotApplicable, NotCollectedYet }`
> **모든 피처가 결측 사유를 provenance로 갖는다.**
>
> **판단 기준(규칙)**: **학습과 서빙에서 한 축의 의미가 다르면 그 피처를 쓰지 않는다.**

**⚠ 이 항목의 legacy 실물(백필 커버리지 시계열)은 이 문서가 열지 않았다** — 선행 조사도
열지 않았다. `capability-map.md` ML-11.4 F3 인용으로만 다루며 층은 `legacy-behavior`다.
ADR 명문화는 0D 소관이다.

**서빙 분모 고정은 정책 데이터로 선언한다.** legacy는 코드 상수로 고정하고 근거를 적는다
(`app/domain/award_rate_features.py:60-90`). **`capability-map.md` §11.3이 이 도메인 판단을
`authoritative` 승격 후보로 올려 두었고 승격은 사용자 승인 사항이므로 이 문서가 임의로
승격하지 않는다** — 자리와 형태만 정의한다.

### 6.4 성숙도 — 계산과 판정의 분리 (`OPEN-ML-01` 잔여 확인)

운영자 결정이 **0C에 확인을 위임했다** — *"SET-06 acceptance가 「성숙도 계산」과 「성숙도
기반 판정」을 분리해 서술하는지 0C에서 확인이 필요하다."*

**확인 결과: acceptance 항목은 서로 갈라져 있으나, 어느 항목이 어느 언어의 소유인지는
적혀 있지 않다.** 항목별 귀속은 다음과 같고, 이 표가 그 분리를 명세로 만든다.

| SET-06 acceptance 항목(요지) | 귀속 |
| --- | --- |
| KST 주 경계 · 구간 반개구간 · 관측 없는 주 제외 · 분자·분모 동반 · 결측의 항등식 · 대체 출처 영향 산출 | **계산** — `ml-engine` |
| 시간축 provenance(어느 시각 종류에서 왔는가 + 정책 version) | **계산 입력의 provenance** — 수집 어댑터가 기록, 계산이 보존 |
| 성숙도 임계 미달 구간을 평가에서 제외 + 제외 사유 · 평가 창 겹침 0 | **판정** — Kotlin |

**커널 수준에서 접힘이 하나 있다.** `MaturityWindow.maturity`가 분모가 `0` 이하일 때
`0.0`을 반환한다(`app/domain/settlement_maturity.py:81-86`, *"빈 구간은 성숙하지 않은
것으로 본다"*). 이것은 §1.6과 SET-06 acceptance(*"관측 없는 주는 표에서 빠진다"*)가
요구하는 방향과 어긋난다 — **acceptance는 소비 층에서 성립하지만 커널이 값을 접는다.**

> `Maturity = sealed { Observed(settledCount, openedCount, ratio), NoObservation }`
> **`0/0`을 비율로 표현할 수 있는 경로를 만들지 않는다.**

**embargo 임계는 손잡이가 아니다.** legacy가 그 규율을 적고 CLI 플래그를 두지 않는다
(`app/services/ml_training/award_rate_windows.py:16-20`) —
임계의 의미는 **감수하는 무지의 상한 선언**이다. **V2 승계 여부는 활성 `OPEN-SET-06`이
소유하므로 이 문서는 값을 쓰지 않는다.**

### 6.5 `confidence` — 사전에 올리지 않는다

legacy의 `confidence`는 근거 없는 계수 아홉의 아핀 결합이고 클램프가 붙는다
(`app/ai/predictors/historical/statistics.py:49-55`). 사용자에게 "신뢰도"로 보이는 값이
임의 계수의 합성이라 **이름이 주장하는 의미를 산식이 뒷받침하지 않는다.**
`v2-지침서.md` §4.4의 *"빈도는 실제 확률이라고 표시하지 않는다"*와 같은 계열의 위험이다.

> **이 산식을 사전에 올리지 않는다.** 대신 **그 산식이 먹는 관측 가능한 구성 요소**를
> 그대로 노출한다. 합성 점수가 필요하면 **별도 이름과 명시 policy version**으로 둔다.

| 성분 | 단위 | basis / 축 | legacy 대응 |
| --- | --- | --- | --- |
| `sampleSize` | 건수 | 그 추정에 쓰인 과거 표본 수 | `sample_size` |
| `dispersion` | fraction | **투찰율 축**(투찰가 ÷ 기초금액) 표본의 **표준편차** | `std_rate` — `std_bid_rate`에서 온다 |
| `estimateMargin` | fraction | 같은 축. **평균 투찰율 신뢰구간의 반폭** | `margin` |

**세 성분의 provenance는 같다** — 과거 투찰율 표본 집합에서 계산한 파생값이다.
**세 값을 만드는 산식은 `app/ai/predictors/historical/__init__.py:294-308`**이고, 셋이 함께
`estimate_historical_confidence`로 들어가는 자리는 같은 파일
`app/ai/predictors/historical/__init__.py:371-375`다.
`app/ai/predictors/historical/statistics.py:49-55`는 **`confidence` 산식**이고 그 자리에서
`margin`은 **입력 인자**다 — **세 성분의 산식이 아니다.**

> **분기를 뭉개 적지 않는다.** legacy의 `margin`이 `t값 × (표준편차 ÷ √표본수)`인 것은
> **`sample_size > 1` 분기뿐**이다. **`sample_size == 1`이면 `margin`은 `std_rate` 그 자체**이고,
> 그 `std_rate`도 같은 자리에서 **다시 잡힌다** — 표본에서 온 표준편차와 **휴리스틱
> 예측율과의 거리의 절반**, 그리고 **바닥값** 가운데 **큰 것**을 쓴다
> (`app/ai/predictors/historical/__init__.py:304-308`). 그래서 **표본이 하나면 legacy에서
> `dispersion`과 `estimateMargin`이 같은 값이 되고, 그 값은 신뢰구간 반폭이 아니다.**
> **V2가 이 분기를 승계할지 이 문서는 정하지 않는다** — 승계하려면 그 분기가 쓰는 계수가
> §12.1에 등재돼야 하고 **지금은 등재돼 있지 않다.**

> **⚠ 정정** — 이 절은 앞서 셋째 성분을 **`marginToFloor`**로 적었다. **legacy의 `margin`은
> 하한까지의 거리가 아니라 신뢰구간 반폭**이라 이름이 축을 잘못 가리켰다.
> **하한 여유 축은 §3.3이 이미 소유한다**(`criticalAssessmentRate` · `frequency`) —
> 여기서 다시 정의하지 않는다.
>
> **이 정정의 근거 좌표도 함께 고쳤다.** 앞서 이 절은 세 성분의 산식을
> `app/ai/predictors/historical/statistics.py:49-55`로 귀속했으나 **그 자리는 `confidence`
> 산식이고 `margin`은 거기서 입력 인자**다. 산식은 위 좌표에 있다.

계수의 분류는 `OPEN-ML-05`가 소유한다(§4.3).

---

## 7. "오염"의 정의 — V2가 재정의하고 재측정한다 (**U-9**)

**운영자 결정(U-9)**: legacy가 다섯 자리에 복제한 **기초금액 오염 비율 수치의 인용 금지를
유지**하고, **V2가 재정의·재측정한다.** **그 수는 이 문서에 옮기지 않는다.** 문자열이 복제된 자리는 §10 `MISSING-EVIDENCE` 항목과
`regression-ledger.md` `R-PROV-01`이 이미 열거했고, 이 문서는 **정의를 새로 쓰는 일만**
한다.

### 7.1 정의

대상은 **기초금액 자리에 저장된 값**이다. *"저장된 기초금액이 그 공고의 기초금액이
아니다"*는 문면 그대로는 측정할 수 없다 — 정답이 그 행에 없기 때문이다. 그래서 V2는
**관측 가능한 세 술어**로 정의한다.

> `Contaminated(row) ⟺ C1 ∨ C2 ∨ C3`
>
> - **C1 출처 부적격** — 그 값의 `FactProvenance`가 **`DerivedFromOpening` ·
>   `FilledFromBudgetKey` · `CopiedFromBaseAmount` 중 하나다**(파생 · 폴백 · 사본).
>   **열거가 술어다** — 「`Published`도 `Undeclared`도 아니다」로 적으면 `OperatorDeclared`가
>   술어 안으로 들어와 오염으로 세어진다. 그 값은 이 자리에 **들어올 수 없고**(§5.1 · §5.2)
>   들어와 있으면 오염이 아니라 **경계 위반**이다 — 다른 판정이므로 이 분자가 받지 않는다.
> - **C2 출처 미신고** — `FactProvenance`가 `Undeclared`다.
> - **C3 모순 관측** — 같은 공고의 다른 금액과의 관계가 **선언된 개연 밴드 밖**이다.

**셋을 합쳐 하나의 비율로만 발표하지 않는다.** 각 성분의 분자를 따로 낸다 — legacy가 남긴
단일 수 하나가 **무엇의 비율인지 복원되지 않는 것**이 그 수를 못 쓰게 만든 원인이다.
**그 요구가 성분의 경계도 정한다** — 앞서 C1을 *"`Published`가 아니다"*로 적어 **C2를
통째로 품고 있었고**, 그러면 성분별 분자를 따로 내도 **두 분자가 겹쳐 복원되지 않는다.**
그래서 C1에서 `Undeclared`를 뺐다 — 한 값의 `FactProvenance`는 하나이므로 **C1과 C2는
이제 겹치지 않는다.** **C3는 여전히 둘 중 어느 쪽과도 겹칠 수 있다** — 출처와 모순은
다른 축이다. 그래서 **합집합의 분자를 성분 분자의 합으로 얻을 수 없다.**
**그리고 이 요구를 담는 것은 산문이 아니라 §7.3의 측정 타입이다.**

> **⚠ 정정** — 앞서 이 자리는 *"V2 write 경로에서 C2는 타입으로 `0`이다(provenance가 필수
> 필드다)"*라 적었고 **거짓이었다.** `Undeclared`가 `FactProvenance`의 **멤버**이므로(§5.1)
> **필수 필드는 값이 있게 할 뿐 `Undeclared`를 배제하지 않는다** — 필수로 두어도 그 값을
> 실으면 그만이다. 그 위에 얹혀 있던 *"C2의 분자가 `0`이 아니면 오염이 아니라 적재
> 결함이며 그렇게 분류한다"*도 함께 걷는다. **그 분류는 성립하지 않는 전제 위에 있었다.**

**C2는 V2 write 경로에서도 `0`이 아닐 수 있고, 그 분자는 다른 성분과 같은 방식으로 잰다.**
타입만으로 `0`이 되게 하려면 **write 계약의 입력 타입이 `Undeclared`를 갖지 않아야**
한다 — `FactProvenance`의 진부분집합을 그 경계에 두는 것이다. **그 부분집합을 둘지는 이
문서가 정하지 않는다** → **`OPEN-DIC-06`**. 근거가 한쪽으로 서지 않는다 — §5.1의 **둘째
규율**은 `Undeclared`를 **권위로 취급하지 않을** 뿐 **금지하지 않고**, 같은 축의
`vatTreatment`에서 §1.2.1은 *"선언을 만들 수 없으면 `Unknown`"*을 **허용**한다.

**legacy 유래 행은 별도 모집단이다.** 운영자 결정 `OPEN-DEC-08`이 legacy `clean` 라벨의
승계를 금지했다 — 표식이 값이 아니라 **write 경로**인데 legacy에 provenance가 없다.

### 7.2 모수 — 이름으로 고정한다

**오염률은 모수를 함께 선언하지 않으면 발표할 수 없다.**

| id | 모수 |
| --- | --- |
| `P1` | 수집된 전 공고 행 |
| `P2` | 개찰이 완료된 공고 행(`OpeningCompleted`, §2.3) |
| `P3` | 판정에 실제로 소비된 행(밴드 캘리브레이션·백테스트 입력) |

같은 정의라도 모수가 다르면 다른 수가 나온다. **발표는 `(정의, 모수)` 쌍으로만 한다.**

### 7.3 측정 규격 — 맥락 다섯과 결과 셋

> `ContaminationMeasurement(definition, population, measuredAt, method, policyVersion,
> populationSize, componentNumerators, unionNumerator)`
>
> **맥락 다섯**(`definition` · `population` · `measuredAt` · `method` · `policyVersion`)을
> 갖지 않는 오염률은 V2 문서·화면·리포트에 인용할 수 없다.
> **결과 셋**이 §7.1의 요구를 담고, §7.2가 이름으로 고정한 모수를 **셀 수 있게** 한다.

legacy의 수가 인용 금지가 된 이유가 정확히 맥락 중 셋(방법·모수·측정일)의 부재다.

**결과를 담는 자리가 없으면 요구가 성립하지 않는다.** §7.1이 *"각 성분의 분자를 따로
낸다"*를 요구하는데 **앞서 이 타입에는 분자도 분모도 없었다** — 요구를 산문에만 두고
타입이 담지 않으면 그 요구는 구현에 전달되지 않는다. **§7.2는 다르게 걸린다** — 그
문면(*"발표는 `(정의, 모수)` 쌍으로만 한다"*)이 요구하는 두 항은 **맥락 다섯이 이미
담고 있었다.** 새 셋 중 그 축에 닿는 것은 `populationSize` 하나이고, 그것도 문면 요구가
아니라 *"같은 정의라도 모수가 다르면 다른 수가 나온다"*에서 **도출한** 자리다 — 모수가
낸 수를 그 모수의 크기 없이 발표하면 「다른 수」인지 볼 수 없다. **아래 셋이 그 자리다.**

| 필드 | 무엇을 나르는가 |
| --- | --- |
| `populationSize` | 건수 — `population`이 지목한 모수(§7.2의 `P1` · `P2` · `P3`)의 행 수. **공통 분모다** |
| `componentNumerators` | 건수 — **성분마다 하나씩.** 성분 어휘는 `ContaminationComponent`가 정한다 |
| `unionNumerator` | 건수 — `Contaminated`가 참인 행 수. **성분 분자의 합이 아니다**(§7.1 — C3가 다른 둘과 겹칠 수 있다) |

> `ContaminationComponent = sealed { SourceIneligible, SourceUndeclared,
> ContradictoryObservation }` — 차례로 §7.1의 C1 · C2 · C3다.

**비율은 필드가 아니다.** `분자 ÷ populationSize`의 파생이며 **저장하지 않는다.**
**근거는 「저장하면 맥락에서 떨어진다」가 아니다** — 이 타입은 맥락 다섯을 **같이
나르므로** 저장이 곧 분리는 아니다. 근거는 둘이다. ① **파생값을 옆에 두면 같은 사실이 두
자리에 서고 갈릴 수 있다** — §5.3의 **둘째 규율**이 밴드에 대해 막는 것과 같은 형태다.
② **분자가 넷이다**(성분 셋 + 합집합). 「비율」 한 필드는 **어느 분자의 비율인지**를
이름 뒤에 숨겨 §7.1이 성분을 가른 이유를 되무른다. **맥락에서 떨어진 수가 홀로 인용되는
것을 막는 것은 미저장이 아니라 §7.2의 발표 규칙이다** — 그 인용이 legacy의 수를 못 쓰게
만든 형태다(U-9 · §7 머리). 발표는 §7.2가 정한 대로 **`(정의, 모수)` 쌍과 함께만** 한다.

**`populationSize`가 `0`이면 비율이 없다** — `Unmeasurable`이며 `0`이 아니다.
`v2-지침서.md` §4.4가 같은 규율을 다른 축에 적는다(*"표본 부족은 0%가 아니라
`Unmeasurable`"*). §6.4의 `NoObservation`과 같은 형태다.

### 7.4 이 정의가 답하지 않는 것

**활성 `OPEN-NUM-01`·`OPEN-REG-04`는 legacy 수치의 출처 질문으로 남는다.** 이 절의 정의는
그 질문에 답하지 않는다 — **V2가 새 정의로 잰 수는 legacy 수의 검증이 아니라 다른 수다.**
두 수를 같은 축에 놓고 비교하지 않는다.

---

## 8. 알려진 제한

1. **면허 그룹 의미론이 운영에서 검증된 적이 없다.** U-8이 확정한 의미론의 legacy 모듈은
   **소비자가 없다**고 스스로 적는다(§3.2.2).
2. **면허 유효기간을 다루지 않는다**(U-7). 만료 면허로 자격을 오판정할 수 있고, 그 위험은
   판정 결과의 "유효기간 미검증" 표시로만 가려진다(§3.2.4).
3. **정책 version의 날짜 식별은 같은 날 두 번 바뀌는 정책을 구별하지 못한다**(§4.1).
4. **`capability-map.md`의 여러 자리가 이 문서와 다르다**(§10). 그 파일은 고치지 않았으므로
   **두 문서를 함께 읽는 사람은 §10을 먼저 봐야 한다.** 승인된 목록 밖에서 이 slice가
   새로 발견한 것이 있고 §10.1에 구별해 적었다.
5. **`OPEN-ML-05` 미분류 계수를 이 문서가 줄이지 못했다.** U-10의 잠정 기준을 확인된
   문면에 적용한 결과 **미분류가 늘었다**(§4.3).
6. **`ed4b06c`는 이 저장소 밖이라 Codex 리뷰어 worktree에서 재현되지 않는다.** 인용의 기계
   확인은 `commands.md` **C-7**이 유일한 지점이다.
7. **`v2-지침서.md` §4.2가 요구하는 「유효기간을 versioned policy data로」와 U-7이
   어긋난다**(§3.2.4). 지침서 개정은 이 slice의 범위 밖이다 → §13.
8. **`C-4.2`는 이름을 통해서만 잰다 — 이름이 나오지 않는 자리에서는 침묵한다.**
   형태가 둘이다 — **㉠ 산문이 요구한 필드를 타입이 선언하지 않은 경우**와
   **㉡ 타입이 인자 이름을 하나도 내지 않아(값 하나를 감싸는 뉴타입) §12.2에 통째로
   없어도 걸리지 않는 경우.** **둘 다 이 문서에서 실제로 났고** 전문과 실례는 **§12.2의
   각주**가 갖는다. **이 문서의 요구·타입 목록과 §12.2의 대응은 사람이 읽어야 한다** —
   이 라운드도 그 형태를 재는 검사를 만들지 않았고, ㉡에 대해 한 것은 **§1.4.2의 율 타입
   넷이 `Rate`의 `fraction`으로 이름을 내게 한 것**뿐이다.

---

## 9. `OPEN` — 이 문서가 판정하지 못한 것

| id | 질문 | 왜 여기서 못 닫는가 | 걸린 자리 |
| --- | --- | --- | --- |
| **`OPEN-DIC-01`** | **면허·지역 축이 정책 version을 싣는가.** U-6의 제외 절과 `capability-map.md` QUAL-03의 **무조건** acceptance 가운데 축 B 둘, 그리고 `v2-지침서.md` §4.2가 서로 어긋난다 | **운영자 결정의 범위**다. 두 읽기 — ① 제외 절이 그 acceptance를 무효화한다 ② 제외 절은 「이 결정이 정한 `PolicyVersion` 어휘의 대상이 아니다」일 뿐이고 면허 축은 자기 축의 versioned policy를 갖는다 — 중 어느 쪽인지 이 문서가 정할 수 없다 | §4.2 · §3.2 |
| **`OPEN-DIC-02`** | **시공능력평가액 공시의 갱신 주기와 시행 구간.** "직전 연도"와 "공고일 기준 직전 해"가 같으려면 갱신이 역년 경계여야 한다 | 이 저장소에 근거가 없다. 조달청·협회 문서 확인이 필요하다 | §1.2.4 |
| **`OPEN-DIC-03`** | **`SkipReason` 어휘의 전수성.** 최소 두 값은 legacy 게이트 사다리에서 확인되나 목록이 닫혔는지는 미확인 | legacy 사다리를 M1에서 옮길 때 확정된다. 지금 닫으면 옮기는 과정에서 발견될 사유가 갈 곳을 잃는다 | §3.6 |
| **`OPEN-DIC-04`** | **`AllocatedBudget`·`YegaAmount`·`AwardAmount`의 과세 처리** | U-1·U-1b는 추정가격과 기초금액 둘만 정했다. 나머지 셋은 결정도 문서 근거도 없다 | §1.2 |
| **`OPEN-DIC-05`** | **`BaseAmountProvenance`의 승인 라벨 다섯이 legacy 실측을 덮는가** — ① `suspect-fractional`에 대응하는 이름이 승인 명세에 없다 ② **미판정(`NULL`)과 「출처를 모름」(`Unknown`)이 같은 값인가** | **승인 명세(`v2-지침서.md` §4.3)의 집합을 이 문서가 바꿀 수 없다.** `OPEN-DEC-08`은 **legacy `clean` 승계 금지**만 확정했고 라벨 집합 변경을 승인하지 않았다. **바꾸려면 별도 결정이 필요하다** | §3.4 |
| **`OPEN-DIC-06`** | **V2 canonical write 경로가 `Undeclared` provenance를 거부하는가** — 거부한다면 그 경계의 입력 타입은 `FactProvenance`의 진부분집합이다 | **근거가 한쪽으로 서지 않는다.** §5.1의 **둘째 규율**은 `Undeclared`를 **권위로 취급하지 않을** 뿐 **금지하지 않고**, 같은 축의 `vatTreatment`에서 §1.2.1은 *"선언을 만들 수 없으면 `Unknown`"*을 **허용**한다. **어댑터의 의무를 정하는 결정**이므로 이 문서가 정할 자리가 아니다 | §7.1 · §5.1 |
| **`OPEN-DIC-07`** | **전송 멱등 키와 재관측 키가 각각 무엇으로 이루어지는가** — 무엇이 **한 전송**을 식별하고 무엇이 **같은 사실**을 식별하는가 | **두 역할을 한 키가 겸할 수 없다는 것**은 모순 제거로 확정되나(§2.2.3) **키의 구성**은 아니다. **U-3은 fold로의 재정의를 정했고 멱등 키의 구성을 정하지 않았다.** `OPEN-SET-04`가 소유한 것은 **재관측의 노출 여부**이지 키의 구성이 아니다 | §2.2.3 |

**활성 `OPEN`은 해소하지 않았다.** 이 문서가 문면에서 마주친 것 — `OPEN-QUAL-05` ·
`OPEN-QUAL-09` · `OPEN-QUAL-10`(게시 요건 축) · `OPEN-QUAL-11` · `OPEN-ML-05` ·
`OPEN-SET-04` · `OPEN-SET-05` · `OPEN-SET-06` · `OPEN-SET-10` · `OPEN-DEC-07` ·
`OPEN-DEC-10` · `OPEN-OPS-10` · `OPEN-NUM-01` · `OPEN-REG-04` — 에
**정의를 붙이지 않았다.**
**둘은 앞서 이 목록 밖에 있었다** — `OPEN-QUAL-11`은 §11이 U-8로 닫혔다고 적었기
때문이고, `OPEN-QUAL-10`은 §11이 **전체가** 닫혔다고 적었기 때문이다. **두 귀속이 다
틀렸다**(§11의 ⚠ 정정 둘). **어느 id가 어느 상태인지의 판정은 이 산문이 아니라
`commands.md` C-6.3이 낸다.** 그중 `OPEN-SET-05`(재공고 대사 대상 선택
규칙)만은 **본문에 자리가 없다** — 이 문서가 다루는 축이 아니라 여기 이름만 남긴다.
나머지는 각 자리에서 **소유자를 밝힌다.**
재현은 `commands.md` **C-6**이다.

---

## 10. `capability-map.md`와 다른 자리 — **이 문서가 정본이다**

**`capability-map.md`를 고치지 않는다.** 아래 항목은 **성격이 둘로 갈린다.**

- **`X-2`~`X-6` — 운영자가 승인한 정본 교체 다섯 건**(2026-08-27).
  0A3이 그 목록을 **다섯 행 표**로 고정했다
  (`reports/evidence/m0/0a3/scope.md`의 「(A) 결정 — X-2~X-6은 0C가 정본이 된다」 절).
- **`O-2` — 승인 목록이 아니다.** 0A3의 in_scope 밖이라 **고치지 않고 out_of_scope로
  남긴 부채**이며, 같은 문서가 *"**X-2~X-6과 O-2는 여전히 out_of_scope**이고
  **0C 데이터 사전이 정본**이 된다"*고 인계했다.

**`O-1`은 여기에 없다 — 0A3에서 닫혔다.** 운영자 결정(2026-08-27, 0A3 수정 라운드 2 / F-4)으로
`capability-map.md` §13의 해당 행이 **`결정 무관(무조건)`과 `조건부 — `OPEN-REG-05` 결정에
따라 확정`으로 이미 갈라졌다.** **그 행이 무조건으로 요구하는 것은 `basis` 태그가 다른
값 쌍이고, `O-1`이 걸었던 과세/비과세 경계 쌍은 조건부 쪽으로 갔다.** 이 문서가 그
경계 쌍 요구를 **조건부**로 서술하는 것(§1.2 · §11)은 정본 교체가 아니라 **이미 정정된
문면·ledger §10.1과 일치하는 서술**이다.

| # | 성격 | `capability-map.md`의 서술 | 이 문서의 정본 | 근거 |
| --- | --- | --- | --- | --- |
| **X-2** | 승인(2026-08-27) | DEC-11 밴드 표의 「사정률 관측 가능 판정」 행이 별도 밴드다 | **§1.4.3** — 별도 밴드가 아니라 B2의 상수를 쓰는 **술어 한 벌**이다 | `app/ai/predictors/distribution_extraction.py:51-58` · `app/core/constants.py:406-415` |
| **X-3** | 승인(2026-08-27) | DEC-11이 밴드를 여섯 행으로 열거한다(전수를 주장하지는 않는다 — *"최소 6종"*) | **§1.4.3** — 그 여섯에 **없는 밴드가 둘**(**B3**·**B5**) 있다 | `app/services/floor_shortfall.py:94-98` · `app/ai/predictors/distribution_extraction.py:40-48` |
| **X-4** | 승인(2026-08-27) | 투찰율 클램프 출처가 `app/domain/basis_conversion.py:110-120`의 한 줄이다 | **§1.4.3 B7** — 그 줄은 **docstring 언급**이고 리터럴은 다른 파일에 있다 | `app/domain/basis_conversion.py:110-120` ↔ `app/ai/predictors/historical/statistics.py:65-67` |
| **X-5** | 승인(2026-08-27) | SET-07 S4가 "예비가 존재 또는 낙찰가 > 0" 두 논리합이다 | **§2.3.1 S4** — **세 논리합**이며 `total_count`가 독립 축이다 | `app/services/award_verification.py:283-291` |
| **X-6** | 승인(2026-08-27) | DEC-05가 `bid_target.py`를 디렉터리 없이 인용한다 | **§0.3** — 전체 경로는 `app/ai/bid_target.py`이며 저장소에 `app/services/bid_target_signals.py`가 따로 있어 모호하다 | `commands.md` **C-9** |
| **O-2** | out_of_scope 부채 | §6 **NOTI-08**의 투찰률 항목과 §7 **SET-09 F-4**가 두 금액의 차이를 정량화한다 | **§1.2가 정본이다.** 층은 **`legacy-behavior`**이고 귀속은 **`OPEN-REG-05`**(§11에서 해소). **U-1·U-1b가 그 관찰을 설명한다** — 부가세 혼용이 성분 하나다. **차이의 나머지는 이 문서가 정량화하지 않는다** | `reports/evidence/m0/0c/decisions-2026-08-28.md` |

### 10.1 이 slice가 새로 발견한 것 — 승인된 다섯 건 밖이다

**아래는 0A3이 넘긴 `X-2`~`X-6`에 없다.** 이 slice가 `commands.md` **C-7.5**를 돌리다 발견했다.
같은 처리를 적용하되(고치지 않고 정본을 여기 적는다) **승인된 목록과 구별해 표시한다.**

| # | `capability-map.md`의 서술 | 실측 | 근거 |
| --- | --- | --- | --- |
| **X-7** | COL-07이 `KNOWN_FIELDS`의 크기를 **「약」이 붙은 어림수**로 적고, 같은 문장이 `field_contract_spec.py`를 **디렉터리 없이** 인용한다(X-6와 같은 부류) | 고유 키의 실측 수는 그 어림수보다 크다. **수는 `commands.md` C-7.5가 낸다** — 이 문서는 옮겨 적지 않는다 | `app/services/koneps/field_contract_spec.py:241-316` |

**`X-1`은 여기에 없다.** 그것은 0A3이 이미 정정했고, 남은 자리는 **운영자 결정의 근거
서술**이라 이 문서로 대체되지 않는다(`reports/evidence/m0/0a3/scope.md`).

---

## 11. 운영자 결정으로 닫히는 `OPEN` — 근거와 함께 등재

**이 등재가 곧 닫힘이다.** 결정 로그는 `_workspace`이지 evidence가 아니므로,
`reports/evidence/m0/0c/decisions-2026-08-28.md`와 이 절이 그 자리를 대신한다.
**`capability-map.md` §12와 `regression-ledger.md` §9의 행 자체는 고치지 않는다** —
중앙 registry 통합은 별도 slice의 몫이다(ledger §10.3).

| `OPEN` | 원 질문 | 닫는 결정 | 등재 자리 |
| --- | --- | --- | --- |
| **`OPEN-REG-05`** (ledger §9) | 기초금액과 추정가격의 과세 처리 — 두 금액의 차이가 무엇으로 이루어지는가 | **U-1**(추정가격 `Exclusive`, 조달청 `authoritative`) + **U-1b**(기초금액 `Inclusive`, 운영자 2026-08-28) | **§1.2 · §1.2.1 · §1.2.2** |
| **`OPEN-QUAL-10`의 운영자 보유액 축만** (§12) | 운영자가 보유한 시공능력평가금액의 unit·basis·과세 처리·기준 시점 | **U-2**(부가세 포함) + **U-2b**(직전 연도 공시값) + 0C의 기준일 규칙(공고일) | **§1.2 · §1.2.4** |
| **`OPEN-QUAL-06`** (§12) | `Uncertain` 판정의 분해와 사유 목록 | **U-5**(`UncertainReason` 네 값) | **§3.2.3** |

> **⚠ 정정 (2026-08-30, Codex 리뷰 라운드 3 high #1)** — 이 표에 **`OPEN-QUAL-11` 행이 있었고
> 닫는 결정을 U-8로 적었다. 그 귀속이 틀렸다.**
>
> - **`OPEN-QUAL-11`의 원 질문**(`capability-map.md` §12): **`permsnIndstrytyList`(허용업종)
>   의 결합 규칙 — 단독 충족인가 결합 충족인가.** 선택지는 **(a) 허용업종만 보유해도 그
>   그룹 충족 / (b) 제한 면허를 보유한 상태에서만 대체 경로로 인정**이다.
> - **U-8이 답한 것**: **`lmtGrpNo` = 요건 묶음** — 그룹 **간** OR / 그룹 **내** AND,
>   결측 행은 단일 그룹 AND. **(a)/(b) 어느 쪽도 고르지 않는다.**
>
> **`OPEN-QUAL-11`은 활성으로 되돌린다.** **운영자 판정(2026-08-30)**: 조달청 문서에
> 근거가 없어 지금 답하지 않고 **M1 자격 slice(1C)가 실제 공고로 관측해 닫는다**(§3.2.5).
> **U-8 자체는 유효하다** — 바뀐 것은 **그 결정이 어느 `OPEN`을 닫는가**뿐이고,
> **U-8이 닫는 활성 `OPEN`은 없다.**
>
> **⚠ 이 정정의 사유를 다시 정정한다 (2026-08-30, 같은 날).** 앞서 이 자리는
> *"U-8이 닫는 `OPEN`은 없다"*의 사유를 **「그 축을 소유한 `OPEN`이 따로 없었다」**로
> 적었다. **그 부재 주장이 거짓이다.** **`OPEN-QUAL-01`이 그 축을 소유했고 운영자 결정
> 2026-08-26으로 이미 해소됐다** — `capability-map.md` §12.2의 그 행이 결정을 U-8과
> **같은 문장**(**(a)** 그룹 간 OR / 그룹 내 AND)으로 적고 근거로 **조달청 OpenAPI
> 참고자료**를 들며, §12.1이 그것을 해소로 등재한다. **결론은 그대로 선다 — 소유자가
> 없어서가 아니라 소유자가 이미 닫혀 있어서다.** **U-8은 그 확정의 재확인**이고
> 사전 §3.2.2가 그것을 사전의 언어로 적는다(정본 등재는 상류가 갖는다).
> **「소유한 `OPEN`이 없다」 같은 부재 주장도 그 자리를 열어 재고 나서 적는다.**
>
> **같은 형태의 두 번째다.** 앞서 U-3의 소유 `OPEN`을 `OPEN-SET-05`로 적었고 실제는
> `OPEN-SET-04`였다(Codex 1차 finding `E`). **결정을 `OPEN`에 매달 때 그 `OPEN`의 원문
> 질문을 열어 대조하지 않은 것**이 두 번 다 원인이다. 이후로는 **`capability-map.md`의
> 그 행을 열어 질문과 선택지를 대조하고, 결정이 그 선택지 중 하나를 고르는지 확인한
> 뒤에** 적는다.

> **⚠ 정정 (2026-08-30, Codex 리뷰 라운드 3 high #2)** — 이 표의 **`OPEN-QUAL-10` 행이 그 `OPEN`
> 전체를 닫는다고 적었다. 절반만 닫힌다.**
>
> - **`OPEN-QUAL-10`의 원 질문**(`capability-map.md` §12): 시공능력평가금액 비교에 쓰는
>   두 금액의 unit·basis·과세 처리·기준 시점 — **공고 게시 요건(`cnstrtnAbltyEvlAmtList`)과
>   운영자 보유액 양쪽.** 같은 행이 *"조달청 문서는 이 필드에 단위·과세를 명시하지
>   않는다"*고 적고 **그 사실이 이 `OPEN`의 존재 이유다.**
> - **U-2·U-2b가 정한 것**: **운영자 보유액 축**의 과세 처리와 기준 연도. **공고 게시
>   요건 축은 정해지지 않았다.**
>
> **`OPEN-QUAL-10`은 게시 요건 축에 대해 활성으로 되돌린다.** 그 축을 §5.5와 §12.2가
> 소유 `OPEN`으로 들고 있고, **§5.5 자신의 규율 — 「보유액의 성질을 게시 요건에 전이하지
> 않는다」 — 이 이 정정과 같은 방향이다.** **지금 답할 근거가 없다**: 조달청 문서에
> 없으므로 **관측으로만** 알 수 있고, §13.4가 그 수집 신설을 M1 이후로 인계한다.

**`R-BASIS-01`의 fixture 유보는 유지되고 사유가 바뀐다.** ledger §10.1이
*"과세/비과세 경계 쌍을 지금 고정하지 마라"*를 **`OPEN-REG-05` 조건부**로 적었다.
그 `OPEN`이 닫혔으나 **유보는 사라지지 않는다** — **유보 사유가 「정의 미결」에서
「저장 값 비일관」으로 교체된다**(§1.2.1). 각 행의 실제 과세 처리를 **행별로 판정**해야
경계 쌍을 만들 수 있다. **`regression-ledger.md`는 고치지 않았다.**

---

## 12. 숫자 인덱스 — 모든 도메인 숫자의 unit / basis / provenance

`milestone-0.md` 완료 조건: *"모든 도메인 숫자의 unit/basis/provenance가 정의되거나
`OPEN`이다."* **숫자는 두 자리에 있다 — 리터럴과 필드.** §12.1이 리터럴을, **§12.2가
필드**를 덮는다.

### 12.1 숫자 리터럴

**본문에 등장하는 도메인 숫자 리터럴은 전부 여기 있고, 검사는 `commands.md` C-4.1**이다.
셈(항목 수)은 본문에서 한글 수사로 쓰므로 이 표에 들어오지 않는다.

| 값 | 단위 | basis / 축 | provenance (근거 위치) | 층 | 소유 `OPEN` |
| --- | --- | --- | --- | --- | --- |
| `0.90` · `1.10` | fraction | 사정률(예정가/기초금액) 분모 필터 밴드 | `app/domain/floor_shortfall.py:42-57` | `legacy-behavior` | — (`OPEN-DEC-02` 확정: 두 벌 유지) |
| `0.8` · `1.2` | fraction | 사정률 관측 편입 필터 밴드 | `app/core/constants.py:406-415` | `legacy-behavior` | — |
| `1` ± `1e-3` | fraction | 사정률 제외 밴드의 중심과 반폭. 중심 `1`은 예정가 = 기초금액인 중립 사정률이다 | 리터럴은 `app/ai/holdout_quality.py:87-91`, 밴드 별칭은 `app/services/floor_shortfall.py:94-98` | `legacy-behavior` | — |
| `0.5` · `1.5` | fraction | 율 라벨 유효 창(낙찰가/금액) | `app/domain/rate_normalization.py:96-102` | `legacy-behavior` | — |
| `0.5` · `1.5` | fraction | **투찰비 개연 밴드**(투찰가/기초금액) — 별도 사본 | `app/ai/predictors/distribution_extraction.py:40-48` | `legacy-behavior` | — |
| `0.30` · `0.995` | fraction | 게시 낙찰하한율 신뢰 밴드 | `app/domain/published_floor_rate.py:34-43` | `legacy-behavior` | — |
| `0.7` · `1.4` | fraction | 투찰율 guardrail 클램프 | `app/ai/predictors/historical/statistics.py:65-67` | `legacy-behavior` | **`OPEN-ML-05`** (근거 없음 — §4.3) |
| `1.5` | fraction | percent/fraction 판별 임계(`PERCENT_SCALE_THRESHOLD`) | `app/domain/rate_normalization.py:70-73` | `legacy-behavior` | — (V2 미채택 — §1.4.1) |
| `100` | 무차원 | percent → fraction 변환 제수(정의상 `1` percent = `1` ÷ `100`) | `app/domain/rate_normalization.py:96-113` | **`authoritative`** — 단위 정의 | — |
| `150` | 건수 | 하한 미달 빈도의 최소 표본 수 | `app/domain/floor_shortfall.py:42-57` + 운영자 승인 2026-08-26 | **`authoritative`** | — (`OPEN-DEC-01` 해소) |
| `1.15` | fraction | 기초금액 ÷ 추정가격 신뢰 상한 | `app/core/constants.py:158-177` | `legacy-behavior` | **`OPEN-DEC-07`** (마진 재유도) |
| `0.05` | fraction | 위 값의 측정 마진 성분 | 같은 자리 | `legacy-behavior` | **`OPEN-DEC-07`** |
| `1e-6` | fraction | provenance 판정의 정수 허용 오차 | `app/services/base_amount_basis.py:43-55` | `legacy-behavior` | — |
| `1.0` | **원** | provenance 판정의 예정가 역산 허용 오차 | 같은 자리 | `legacy-behavior` | — |
| `0.01` | fraction | provenance 판정의 VAT 허용 오차 | 같은 자리 | `legacy-behavior` | — |
| `1.1` | 배수 | VAT 파생 판정 배수(= `1` + 부가세율) | 같은 자리 | `legacy-behavior` | — |
| `0.10` | fraction | **부가세율** | 운영자 결정 2026-08-28(U-1b)의 근거 서술 | **`authoritative`** | — |
| `15` | 건수 | 복수예비가격 개수 | `app/services/base_amount_basis.py:43-55` | `legacy-behavior` | — |
| `0.89745` · `0.88745` · `0.87495` | fraction | 법정 하한율(예정가격 기준), 신율 | `app/ai/predictors/legal_floor_spec.py:86-103` | `legacy-behavior` | **`OPEN-DEC-10`** |
| `0.87745` · `0.86745` · `0.85495` | fraction | 법정 하한율(예정가격 기준), 구율 | 같은 자리 | `legacy-behavior` | **`OPEN-DEC-10`** |
| `1,000,000,000` · `5,000,000,000` · `10,000,000,000` | **원** | 하한율 표의 구간 경계(추정가격 기준, `[하한, 상한)`) | `app/ai/predictors/legal_floor_spec.py:43-50` | `legacy-behavior` | **`OPEN-DEC-10`** |
| `0.15` · `0.02` · `0.85` | fraction | 투찰가 밴드 내 위치 조정 계수 | `app/ai/bid_target.py:20-25` | `legacy-behavior` | **`OPEN-ML-05`** (근거 없음 — §4.3) |
| `12` · `0.06` · `0.08` · `0.07` · `0.54` · `0.2` · `0.18` · `0.45` · `0.95` | 무차원 | `confidence` 산식 계수·클램프 | `app/ai/predictors/historical/statistics.py:49-55` | `legacy-behavior` | **`OPEN-ML-05`** |
| `0.58` · `0.86` · `0.84` · `0.82` · `0.45` | 무차원 | price regime 규칙표의 confidence·fallback | `app/ai/price_prediction/price_regime.py:45-69` | `legacy-behavior` | **`OPEN-ML-05`** |
| `-1.0` | 범주 코드 | 미지 범주 피처 코드 | `app/domain/award_rate_features.py:60-90` | `legacy-behavior` | — |
| `0` · `0.0` | 해당 없음 | **부재를 표현하는 데 쓰인 legacy sentinel** — 금액·율·건수·정산 여부 축에 걸쳐 있다 | `app/models/models.py:264-306` · `app/models/pipeline.py:147-167` · `app/domain/settlement_maturity.py:81-86` · `app/domain/aggregates.py:59-68` | `legacy-behavior` | — (`OPEN-ML-04` 확정: 부재를 `0`으로 적재하지 않는다 — §1.3) |
| `2` | 자리수 | legacy 투찰가 반올림 자리수 | `app/ai/bid_target.py:56-59` | `legacy-behavior` | — (V2 미채택 — §1.1) |
| `4` · `6` | 자리수 | legacy 집계 반올림 자리수(콜사이트별) | `app/domain/aggregates.py:20-26` | `legacy-behavior` | — (V2는 `RoundingPolicy`로 중앙화) |

**이 표에 없는 수를 본문에 쓰지 않는다.** 예외는 **legacy 파일의 행 범위** · **날짜** ·
**절 번호** · **식별자**(`OPEN-…` · `DEC-…` · `#…` · commit 해시)뿐이며 C-4.1이 그 예외를
명시적으로 뺀다.


### 12.2 타입이 나르는 필드 — 수인 것과 수가 아닌 것

**§12.1은 숫자 *리터럴*만 덮는다.** 필드도 수를 나르므로 이 표가 **필드**를 받아
**수인 것에는 단위·basis·provenance를, 수가 아닌 것에는 그 사실을** 적는다.

**「모든 필드」라고 쓰지 않는다.** 덮는 범위도, 그 범위에서 무엇이 어떤 갈래로 빠지는지도
정하는 것은 이 문장이 아니라 `commands.md` **C-4.2**이며, **그 블록이 매 실행마다 자기
범위와 예외를 출력으로 낸다.** 여기 옮겨 적지 않는다 — 두 벌이 되면 한쪽이 낡는다.
실제로 앞서 이 자리가 *"덮개 ∨ 「필드가 아님」, 둘 다 아니면 `FAIL`"* 이라 적어 그 블록이
**규칙으로 빼는 갈래(commit 해시)를 빠뜨렸고**, 그래서 그 갈래로 빠지는 이름들에 거짓이었다.

> **⚠ 정정** — 앞서 이 자리는 *"이 문서가 선언한 **모든** 타입의 **모든** 필드를 여기
> 등재하고"*라 적었고 **그것은 거짓이었다**: `entries`(§4.1)와 `isAuthoritative`(§5.1)가
> 등재돼 있지 않았다. **원인은 셈이 아니라 순서다** — 그때의 `C-4.2`는 중첩 괄호와
> 블록인용 줄바꿈을 못 읽어 그 둘을 **보지 못한 채 `PASS`를 냈고**, 이 문장이 그 `PASS`
> 위에 전칭을 얹었다. **덮개가 못 보는 것을 재기 전에 전칭을 쓰지 않는다.** 그래서
> 전칭을 지우고 **범위를 그 블록에 넘겼다.** 빠져 있던 두 필드는 아래 표에 등재했다.

#### 수를 나르는 필드

| 필드 | 단위 | basis / 축 | provenance | 소유 `OPEN` |
| --- | --- | --- | --- | --- |
| `amount` (`Money`) | **원(KRW), 정수** | 같은 값의 `basis` 필드가 정하는 금액 축(§1.2) | 수집 어댑터가 `provenance`·`vatTreatment`와 함께 싣는다 | — |
| `money` (`ConstructionCapacityAmount`) | `Money` 한 벌 — 위와 같다 | 시공능력평가액 **보유액** | 그 `Money`의 `provenance`가 나른다 — 운영자 보유액이므로 `OperatorDeclared`(§1.2.4 · §5.1) | — |
| `requiredAmount` (`ConstructionCapacityRequirement`) | **미정 — 게시값의 원문 단위가 확인되지 않았다.** 조달청 문서가 이 필드에 단위·과세를 적지 않는다(§5.5). **타입이 `Money`가 아니라 `UnnormalizedFigure`이고 서명이 그 미정을 나른다** — 단위는 같은 값의 `fieldContract`가 선언해야 정해진다 | 시공능력평가액 **요건** | 공고 게시값이므로 그 `fieldContract`의 `provenance`가 `Published`. **legacy가 수집하지 않으며 `vatTreatment`도 그 계약이 나르고 `Unknown`이다**(§5.5) | **`OPEN-QUAL-10`의 게시 요건 축**(§11의 ⚠ 정정). 수집 신설은 §13.4 인계 |
| `figure` (`UnnormalizedFigure`) | **미정 — 같은 값의 `fieldContract`가 선언하는 `unit`·`scale`이 정한다.** 그 선언이 없으면 판정에 넣지 않는다(§5.5). **값 자신은 단위를 주장하지 않는다** | 그 계약의 `basis`가 정하는 축. 이 타입을 쓰는 자리는 지금 `requiredAmount` 하나다 | 그 계약의 `provenance`가 나른다 | **`OPEN-QUAL-10`의 게시 요건 축** |
| `fraction` (`Rate` — `AssessmentRate` · `AwardRate` · `FloorRate` · `BidRate`) | fraction | **그 뉴타입이 정하는 율 축**(§1.4.2). 넷을 섞는 것은 타입이 막고, **값 크기로 단위를 추측하는 경로를 두지 않는다**(§1.4.1) | **§1.4.2의 「값의 provenance · 층」 열이 타입마다 정한다.** 이 값 자신은 **원문 unit을 나르지 않는다** — 그것은 §5.3 필드 계약의 `unit`·`scale`에 있다 | — |
| `frequency` | fraction | `numerator` ÷ `denominator`. **확률이 아니다**(§3.3) | 표본 집합에서 계산한 파생값 | — |
| `criticalAssessmentRate` | fraction | **사정률 축**(예정가 ÷ 기초금액) | 추천 투찰율 ÷ 낙찰하한율의 파생값 | — |
| `numerator` | 건수 | 하한 미달로 판정된 표본 수 | 표본 집합 | — |
| `denominator` | 건수 | 밴드 필터를 통과한 표본 수 | 표본 집합 | — |
| `band` (`Measured`) | fraction | §1.4.3 밴드 인벤토리의 한 엔트리(축은 그 엔트리가 정한다) | 정책 데이터 | — |
| `required` · `actual` (`SampleInsufficient`) | 건수 | 최소 표본 수 **정책값**과 **실제** 표본 수 | 정책 데이터 / 표본 집합 | — |
| `sampleSize` (`Measured<T>` · §6.5) | 건수 | 그 측정에 쓰인 표본 수 | 표본 집합 | — |
| `value` (`Measured<T>`) | **`T`가 정한다** | `T`의 축 | `T`를 낸 계산 | — |
| `dispersion` (§6.5) | fraction | **투찰율 축** 표본의 표준편차 | 과거 투찰율 표본 | — |
| `estimateMargin` (§6.5) | fraction | 같은 축. 평균 투찰율 **신뢰구간 반폭** — **단, 표본이 하나면 legacy에서 이 값이 `dispersion`과 같아져 반폭이 아니다**(§6.5) | 같음. **V2가 그 분기를 승계할지 이 문서는 정하지 않는다**(§6.5) | — |
| `settledCount` · `openedCount` (`Maturity.Observed`) | 건수 | 그 KST 주에 개찰된 공고 수와 그중 낙찰가를 아는 수(§2.3) | 성숙도 계산 입력 | — |
| `ratio` (`Maturity.Observed`) | fraction | `settledCount` ÷ `openedCount` | 파생값. **`0/0`은 `NoObservation`이며 비율이 아니다**(§6.4) | — |
| `evaluationYear` (`ConstructionCapacityAmount`) | **연도** | 시공능력평가액 공시 연도 = `year(공고일) − 1`(§1.2.4) | 그 규칙 | **`OPEN-DIC-02`** |
| `reobservationCount` (`TenderOutcome` fold) | 건수 | 같은 `observationKey`를 가진 관측을 센다. **첫 관측은 세지 않고, `deliveryKey`가 같은 재전송도 세지 않는다**(§2.2.3) | event stream fold | **`OPEN-SET-04`**(노출 여부) · **`OPEN-DIC-07`**(두 키의 구성) |
| `populationSize` (`ContaminationMeasurement`) | 건수 | 오염률의 **공통 분모** — `population`이 지목한 모수(§7.2)의 행 수 | 그 모수를 센 측정(§7.3) | — |
| `componentNumerators` (`ContaminationMeasurement`) | 건수 | 성분(`ContaminationComponent`)마다 하나 — §7.1 세 술어의 분자 | 같은 측정 | — |
| `unionNumerator` (`ContaminationMeasurement`) | 건수 | `Contaminated`가 참인 행 수. **성분 분자의 합이 아니다**(§7.1) | 같은 측정 | — |
| `expectedRange` (`KonepsFieldContract`) | 같은 계약의 `unit`이 정한다 | 같은 계약의 `scale`·`basis`가 정하는 축 | 필드 계약 선언(§5.3) | — |
| `confidence` (§6.2 경계 표) | 무차원 | **없다** — 이름이 주장하는 의미를 산식이 뒷받침하지 않는다(§6.5). legacy는 클램프된 아핀 결합을 낸다 | legacy 산식. **V2는 이 필드를 내지 않고 §6.5의 세 성분을 노출한다** | **`OPEN-ML-05`**(계수 분류) |

#### 수를 나르지 않는 필드

| 갈래 | 필드 |
| --- | --- |
| **열거·sealed 값** | `basis` · `biasDirection` · `nullability` · `provenance` · `reason` · `reasons` · `regime` · `scale` · `vatTreatment` · `currency` |
| **불리언** | `authoritative` (`KonepsFieldContract`) · `isAuthoritative` (`FactProvenance` — §5.1) — 둘 다 **술어가 아니라 데이터로 선언한다** |
| **식별자** — 수처럼 보여도 셈이 아니다. **정수 변환 금지**(§5.3의 공고 차수와 같은 부류) | `limitGroupNo` · `licenseRegionCode` · `noticeRevision` · `deliveryKey`(전송 멱등 단위, §2.2.3) · `observationKey`(재관측 단위, 같은 자리) · `key` · `sourceKey` · `modelArtifactId` · `inputSnapshotHash` · `policyVersion` |
| **시각·날짜** | `decidedAt` · `measuredAt` · `observedAt` · `settlementObservedAt` · `effectiveFrom` |
| **텍스트·이름** | `concept` · `definition` · `detail` · `licenseRegionName` · `method` · `population` · `rawName` · `source` · `unit` |
| **집합·구조** | `entries` (`EffectiveDatedPolicy` — 유효일자와 값의 쌍 목록, §4.1) · `fieldContract` (`UnnormalizedFigure` — 그 값을 낸 §5.3 `KonepsFieldContract` 한 벌. **단위·`scale`·`vatTreatment`·`provenance`를 나르는 자리가 여기다**) · `missingByGroup` · `satisfiedGroup` · `payload` · `presentIn` · `row` |
| **경계 표의 비수치 필드**(§6.2) | `review_required`(불리언 — **업무 판정이므로 Kotlin 소유**) · `regimeLabel` · `signals` |

> **`C-4.2`는 이름을 통해서만 잰다 — 이름이 나오지 않는 자리에서는 침묵한다.**
> 그 블록이 재는 것은 **「뽑힌 이름이 §12.2에 등재됐는가」** 하나이므로 **뽑을 이름이
> 없으면 미등재가 드러나지 않는다.** 그 형태가 둘이고 **둘 다 이 문서에서 실제로 났다.**
>
> - **㉠ 요구된 필드를 타입이 선언하지 않은 경우.** 요구는 산문에 있고(§7.1의
>   *"각 성분의 분자를 따로 낸다"*가 그렇다) **선언되지 않은 이름은 뽑히지도 않으므로**
>   타입이 그 요구를 빠뜨려도 덮개는 침묵한다. 오염 측정 타입이 **§7.1이 요구한 분자도
>   그 분자가 딛는 분모도 갖지 않은 채** `PASS`가 났다(Codex 2차 high).
> - **㉡ 타입이 인자 이름을 하나도 내지 않는 경우.** 값 하나를 감싸는 뉴타입이 그렇다 —
>   **타입 이름은 대문자라 ③의 이름 규칙 밖이고, ①은 인자에서 이름을 뽑으므로 인자가
>   없으면 뽑히는 것이 없다.** 그래서 그런 타입이 §12.2에 **통째로 없어도** 판정에 걸리지
>   않는다. §1.4.2의 율 타입 넷이 그 자리였고 `PASS`가 났다(Codex 3차 high).
>   **이 라운드가 고친 것은 검사가 아니라 문서다** — 넷의 carrier를 `Rate`의 `fraction`
>   으로 명시해 **이름이 나오게** 했다. **덮개의 이 성질은 그대로 남는다.**
>
> **Codex 1차 finding `A`와 같은 계열이 도메인 층위에서 되풀이된 것이고 ㉡이 세 번째다.**
> **두 형태를 재는 검사는 이 라운드도 만들지 않는다** — 한계로 적고 §8에 등재한다.
>
> **이 표가 덮는 범위와 `C-4.2`가 못 보는 자리는 산문이 적지 않고 그 블록의 실행이 낸다.**
> 「필드가 늘면 이 검사가 낸다」처럼 **덮개 범위를 산문이 주장하지 않는다** — 그 주장은
> 검사의 이름 규칙 밖(예: 밑줄 든 이름)에서 곧 거짓이 되고, 여기 옮겨 적은 목록과 셈은
> 검사가 넓어질 때 낡는다. 그래서 **셈도 목록도 그 블록만 낸다.**

---

## 13. 인계

### 13.1 fixture-curator

- **과세/비과세 경계 쌍은 아직 고정하지 마라.** `OPEN-REG-05`는 닫혔으나 **저장 값이
  행마다 다르다**(§1.2.1). 각 행의 실제 과세 처리를 **행별로 판정**해야 라벨이 붙는다.
  그때까지는 **basis 태그가 다른 값 쌍**만 만들고 **어느 쪽이 크다는 전제를 넣지 않는다.**
- **`기초금액 ÷ 추정가격` 경계값도 아직이다** — 마진이 `OPEN-DEC-07`의 재유도 대상이다.
  그때까지는 **판정 순서**만 fixture로 고정한다.
- **밴드 경계 fixture는 §1.4.3의 일곱 밴드를 축별로 나눠 만든다.** 값이 같고 축이 다른
  B4·B5를 한 case에 섞지 않는다.
- **부재 fixture가 필요하다** — §1.3의 부재 금지 규칙이 걸리는 모든 필드에 대해
  `Absent`와 `0`이 다른 결과를 내는 case.
- `legacy` 유래 행은 `OPEN-DEC-08`에 따라 **authoritative corpus에 들어갈 수 없다.**

### 13.2 M1 도메인 커널

- §1.1의 basis 교차 대입 차단, §1.4.1의 값 크기 추측 금지는 **architecture test**로 고정한다.
- §2.2의 전이표 셋은 **명시적 state/event table**로 구현한다. 표에 없는 쌍은 거부이며
  거부가 관측 가능해야 한다.
- §5.3의 필드 계약은 **경계에서 거부하는 계약 테스트**로 승격한다(COL-07 결정).
- **`SkipReason` 전수성**(`OPEN-DIC-03`)은 게이트 사다리를 옮길 때 확정한다.

### 13.3 M2 계약

- §6.2의 경계를 **gRPC 스키마가 강제한다** — Python 응답에 `review_required` 상당 필드를
  두지 않는다.
- §6.3의 `MissingReason`은 피처 계약의 필수 동반 필드다.

### 13.4 수집 축 (M1 이후)

- **`cnstrtnAbltyEvlAmtList` 계열 수집 신설**(§5.5). 그때까지 금액 capacity 판정은
  `Uncertain(RequirementDataAbsent)`다.
  **그 수집이 닫는 것이 하나 더 있다** — 게시값의 **단위·과세**는 조달청 문서에 없어
  **관측으로만** 알 수 있고, 그 축을 활성 `OPEN-QUAL-10`(게시 요건 절반)이 소유한다.
  수집 어댑터는 원문 단위를 §5.3의 필드 계약에 붙잡아 그 관측을 남긴다.
- **`settlementObservedAt` write**(§5.4). V2 가동 이전은 `Absent`이며 백필하지 않는다.

### 13.5 기준 문서 개정 후보 (이 slice의 범위 밖)

- **`v2-지침서.md` §4.2**가 면허 유효기간을 versioned policy data로 요구하는데 **U-7이
  다루지 않기로 확정**했다(§3.2.4). 지침서 문면과 결정이 어긋난 채 남아 있다.
- **`OPEN-DIC-01`**의 두 읽기 중 하나가 정해지면 `capability-map.md` QUAL-03의 무조건
  acceptance가 그대로 서는지 다시 봐야 한다.
