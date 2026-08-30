# M0 / 0C — acceptance 대조 checklist

- slice: `0c-data-dictionary`. 계약과 라운드 이력은 `scope.md`.
- 산출물: `docs/discovery/data-dictionary.md` · `reports/evidence/m0/0c/`
- 기준: `scope.md`의 **A1~A8**
- 규격: `.claude/skills/evidence-pack/SKILL.md` §"문서 slice(M0)의 evidence"

문서 slice이므로 acceptance command 대신 이 파일이 A1~A8을 대조한다.
**셈은 여기 적지 않고 `commands.md`의 재현 명령이 산출한다.**

---

## A1 — `milestone-0.md` §"Slice 0C"의 6축 전부를 덮는다

| 요구 | 충족 근거 |
| --- | --- |
| 여섯 축 절이 전부 존재한다 | `commands.md` **C-3**의 출력 블록. 축 이름과 절 머리의 대응을 그 블록이 찍는다 |
| 축 1 — 용어·단위·basis·nullable | 사전 **§1** — `Money`(§1.1) · 금액 개념과 과세 처리(§1.2) · 부재 금지 규칙(§1.3) · `Rate`와 밴드 인벤토리(§1.4) · 시각(§1.5) · 측정 불가 어휘(§1.6) |
| 축 2 — aggregate와 상태 전이 | 사전 **§2** — aggregate 넷(§2.1) · 전이표(§2.2) · "정산됨" 정의(§2.3) |
| 축 3 — rule의 입력·출력·reason code | 사전 **§3** — reason code 규약(§3.1) · 면허(§3.2) · 하한 미달 빈도(§3.3) · 기초금액 provenance(§3.4) · 하한 적용 범위(§3.5) · 투찰 판정(§3.6) |
| 축 4 — 정책 version과 effective date | 사전 **§4** — `PolicyVersion`(§4.1) · 대상 전수 확인(§4.2) · 정책 값과 미분류(§4.3) · 유일한 effective-date 실물(§4.4) |
| 축 5 — canonical fact와 derived fact | 사전 **§5** — `FactProvenance`(§5.1) · 경계 정의(§5.2) · 필드 레지스트리(§5.3) · 정산 관측 시각(§5.4) · 신설 수집 필드(§5.5) |
| 축 6 — ML feature와 업무 판단의 경계 | 사전 **§6** — 경계 규칙(§6.1) · 경계 필드 목록(§6.2) · 피처 계약과 결측 사유(§6.3) · 성숙도 분리(§6.4) · `confidence`(§6.5) |

**축 2와 축 4는 legacy에서 옮겨온 것이 아니다.** 축 2의 전이표는 legacy에 선언이 없어
(`commands.md` **C-7.4**) 이 slice가 도메인 명세로 새로 썼고, 그 사실을 사전 §2 머리가
적는다. 축 4는 운영자 결정 U-6·U-6b와 지침서 규율에서 나오며 legacy의 실물은
`app/ai/predictors/legal_floor_spec.py:53-103` 하나다(사전 §4.4).

## A2 — 모든 도메인 숫자의 unit/basis/provenance가 정의되거나 `OPEN`이다

| 요구 | 충족 근거 |
| --- | --- |
| 전수 인덱스가 **둘** 있다 | **§12.1**(숫자 리터럴 — 값·단위·basis·provenance·층·소유 `OPEN`) · **§12.2**(타입이 나르는 필드 — 수인 것에는 단위·basis·provenance, 수가 아닌 것에는 그 사실) |
| §12.1 밖에 정의 없는 **리터럴**이 없다 | `commands.md` **C-4.1** — **§12 전 구간의 표 첫 칸**을 정본으로 삼고(§12.1의 값 칸이 그 대부분이다) §12 밖 본문을 훑는다 |
| §12.2가 **`C-4.2`가 뽑은 이름**을 덮고, 덮지 않는 것은 그 블록이 **갈래별로 분류한다** — 어느 갈래에도 들지 않은 이름이 남으면 `FAIL`이다(「갈래」가 무엇의 갈래인지, 그 밖의 이름을 왜 `FAIL`로 세지 않는지는 `commands.md` C-4.2 각주가 적는다) | `commands.md` **C-4.2** — 뽑는 자리와 빼는 갈래, 그리고 **자기가 못 본 자리**를 그 블록이 **매 실행마다 출력으로 낸다**(여기 옮겨 적지 않는다). **리터럴만 보던 C-4의 사각지대**가 Codex finding **A**로 드러나 신설했고, 그 신설이 얹은 **「모든 필드」 전칭이 다시 거짓**이던 것(`entries`·`isAuthoritative` 미등재)을 이 라운드가 **범위 선언**으로 바꿨다 |
| 값이 미정인 것은 `OPEN`으로 귀속된다 | 두 표의 `소유 OPEN` 칸 — `OPEN-DEC-07`(신뢰 비율 마진) · `OPEN-DEC-10`(하한율 표) · `OPEN-ML-05`(미분류 계수·`confidence`) · `OPEN-DIC-02`(`evaluationYear`) · `OPEN-SET-04`·`OPEN-DIC-07`(`reobservationCount` — 노출 여부와 두 키의 구성) |
| 검사의 한계를 밝힌다 | **C-4.1** 아래 인용 블록(마스크가 무엇을 빼는지 `MASKS` 각 줄이 적는다) · **`C-4.2`가 재지 않는 방향**(「요구된 필드가 선언됐는가」)은 **그 블록이 낼 수 없으므로 산출물이 적는다** — 사전 §12.2 각주와 §8 항목 여덟. Codex 2차 high가 그 방향으로 났다 · **C-4.2**는 **자기가 재는 방향의 한계를 산문이 아니라 자기 출력으로 낸다** — 그 목록은 여기 옮겨 적지 않고 `commands.md` **C-4.2**의 출력 블록이 낸다. **두 앵커의 관계를 무엇으로 어떻게 재는지의 정본도 `commands.md` C-4.2 한 자리이고, 결과는 그 블록의 출력이 낸다** — 여기 옮겨 적지 않는다 |

**본문의 셈은 한글 수사로 쓴다**(사전 §0.4). 그래야 **§12.1**이 도메인 숫자 **리터럴**만
담고 전수 검사가 성립한다. **필드가 나르는 수는 §12.2가 따로 덮으며**, §12.2는 수를
나르지 않는 필드도 담는다. 검사는 **C-4.1**(리터럴)과 **C-4.2**(필드) 둘이다.

## A3 — 확정된 운영자 결정을 재조사·재확정하지 않고 인용한다

| 요구 | 충족 근거 |
| --- | --- |
| 결정 원본이 리뷰 worktree에서 확인된다 | `reports/evidence/m0/0c/decisions-2026-08-28.md` — 원본과의 일치는 `commands.md` **C-1** |
| 2026-08-26 묶음 | `reports/evidence/m0/0a2/decisions.md`(0A2가 커밋) 인용 |
| 결정 열셋의 반영 자리 | 아래 표 |
| 결정이 위임한 확인을 **실행**했다 | `U-10` → 사전 §4.3 · `OPEN-ML-01` 잔여 → 사전 §6.4 · `U-2b` → 사전 §1.2.4 · `U-6` → 사전 §4.2 |

| # | 결정 | 사전의 자리 |
| --- | --- | --- |
| U-1 | 추정가격 부가세 제외, legacy 선언은 `legacy-defect` | §1.2 · §1.2.1 |
| U-1b | 기초금액 부가세 포함 | §1.2 · §1.2.1 · §1.2.2 |
| U-2 | 시공능력평가금액 부가세 포함 | §1.2 · §1.2.4 |
| U-2b | 직전 연도 공시값 | §1.2.4 |
| U-3 | `TenderOutcome` aggregate, current = event fold | §2.1 · §2.2.3 |
| U-4 | 수집 어댑터 write 시점 기록, 과거는 `Absent` | §5.4 |
| U-5 | `UncertainReason` 네 값 | §3.2.3 |
| U-6 | 정책 version은 수치가 바뀌는 판정만 | §4.2 |
| U-6b | 식별은 날짜(`effectiveFrom`) | §4.1 |
| U-7 | 면허 유효기간 다루지 않음 | §3.2.4 · §8 |
| U-8 | `lmtGrpNo` = 요건 묶음 (**`OPEN-QUAL-01`의 확정을 재확인**) | §3.2.2 |
| U-9 | 오염 수치 인용 금지 유지, V2 재정의 | §7 |
| U-10 | 바꾼 값 없음, 근거 주석 유무로 잠정 분류 | §4.3 |

**재확정하지 않았다.** 사전은 각 자리에서 결정을 인용하고 그 결정이 **닫는 것과 닫지
않는 것**을 함께 적는다 — 특히 §1.2.1이 **정의(확정)**와 **저장 값의 실측(여전히 행마다
다름)**을 분리하고, §1.2.2가 **차이의 나머지 성분을 정량화하지 않는다**고 명시한다.

## A4 — 모호한 것을 legacy로 채우지 않는다

| 자리 | legacy가 준 것 | 사전이 한 것 |
| --- | --- | --- |
| `UncertainReason` | evidence 문자열 셋(`app/services/license_eligibility.py:161-164`) | 문자열을 옮기지 않고 **U-5의 네 값**을 명세로 쓴다. `CollectionFailed`는 legacy에 대응물이 없다 |
| 정산 관측 시각 | 후보 컬럼 넷 — **전부 결함이 legacy에 실측돼 있다** | 넷 중 어느 것도 승격하지 않고 **신설 + `Absent`**(U-4). 백필하지 않는다 |
| 하한 미달 판정 불가 사유 | 넷(한국어 문장) | **인프라 실패("산출 오류")를 어휘에서 뺐다.** 도메인 사유와 고장을 같은 값으로 두지 않는다(§3.3) |
| `confidence` | 근거 없는 계수 아홉의 아핀 결합 | **산식을 사전에 올리지 않고** 관측 가능한 구성 요소를 노출한다(§6.5) |
| `capacity_score` 정규화 | 값 크기로 스케일 추측 | 채택하지 않는다 — 운영자 결정 `OPEN-QUAL-08`이 폐기로 확정 |
| rate scale 판별 | 값 크기 추측(임계 비교) | 채택하지 않는다. 원문 unit을 수집 시점에 붙잡는다(§1.4.1) |
| 신뢰 비율 상한 | 값 하나(마진 포함) | **값을 승계하지 않고** 자리와 구성만 적는다 — 마진이 `OPEN-DEC-07` 대상(§4.3) |
| 정책 값 다섯의 "근거 주석" | U-10 표가 다섯을 「정책(근거 있음)」으로 분류 | **직접 열어 확인했고 넷은 근거가 아니라 용도 서술이었다** → 미분류로 남긴다(§4.3) |
| 오염 비율 | 수치 하나가 다섯 자리에 복제 | **옮기지 않는다.** 정의·모수·측정 규격을 새로 쓴다(§7) |
| 기초금액 provenance 라벨 | legacy 라벨 다섯 + `NULL` | **승인 명세의 다섯 값을 그대로 쓴다.** legacy 실측이 겹치지 않는 두 자리는 **`OPEN-DIC-05`**로 등록하고 **임의로 집합을 바꾸지 않는다**(§3.4) |

## A5 — 계열 A 금지 (활성 `OPEN`이 소유한 쟁점을 선점하지 않는다)

`commands.md` **C-6.3**이 사전이 언급한 `OPEN` id를 전부 분류한다. 「활성」으로 찍힌
것들을 사전이 어떻게 다뤘는지는 아래가 항목별로 적는다.

**분류를 읽는 법**: 운영자 결정 2026-08-28이 닫은 것 가운데 `OPEN-REG-05`도 「활성」으로
찍힌다 — 두 상류 파일의 행을 **고치지 않았기 때문**이며
(out_of_scope), 사전 §11이 그것을 결정 근거와 함께 등재해 닫는다.
**`OPEN-QUAL-11`과 `OPEN-QUAL-10`은 다르다 — 둘은 (전부 또는 일부가) 실제로 활성이다.**
앞서 이 자리가 둘을 「닫혔는데 상류 행만 안 고쳐 활성으로 찍히는 것」 쪽에 세웠고
**그 분류가 틀렸다**(사전 §11의 ⚠ 정정 둘 — U-8은 `OPEN-QUAL-11`의 질문에 답하지 않고,
U-2·U-2b는 `OPEN-QUAL-10`의 **운영자 보유액 축만** 답한다). `OPEN-QUAL-06`은
분해 방식이 2026-08-26에 이미 등재돼 **`capability-map.md` §12.2**(결정 완료 구간)에 있고
U-5가 남은 목록을 닫는다(사전 §3.2.3). **이 파일에서 수식어 없는 §12.2는 사전의 것이다.**

**`OPEN-QUAL-01`이 이 라운드에 목록으로 들어왔다** — `C-6.3`이 **「결정 완료」**로 찍는다.
사전이 앞서 그 id를 **한 번도 적지 않았고**, 그 자리에 *"U-8의 결합 축을 소유한 `OPEN`이
따로 없었다"*는 **부재 주장**을 두었다. **그 주장이 거짓이었다** — 그 축의 소유자가
`OPEN-QUAL-01`이고 **운영자 결정 2026-08-26으로 이미 해소**됐다(사전 §3.2.2 · §11의
재정정). **U-8이 닫는 활성 `OPEN`이 없다는 결론은 그대로 서고 사유가 바뀐다.**

| 활성 `OPEN` | 사전의 자리 | 무엇을 하지 않았나 |
| --- | --- | --- |
| `OPEN-DEC-07` | §3.4 · §4.3 · §12 | 신뢰 비율 마진의 **값을 쓰지 않았다.** 자리와 구성만 적는다 |
| `OPEN-DEC-10` | §4.4 · §12 | 하한율 표의 **값을 V2 정책으로 확정하지 않았다.** 형태만 계승하고 층을 `legacy-behavior`로 둔다 |
| `OPEN-ML-05` | §4.3 · §6.5 · §12 | 미분류 계수를 **정책으로 승격하지 않았다.** 미분류가 늘었다 |
| `OPEN-NUM-01` · `OPEN-REG-04` | §7.4 | 오염 수치를 인용하지 않았고, **§7의 새 정의가 그 질문에 답한다고 쓰지 않았다** |
| `OPEN-OPS-10` | §2.2.5 | outbox **상태 값 집합과 전이를 정하지 않았다.** 형태 요구만 적는다 |
| `OPEN-QUAL-05` | §3.2.4 · §8 | 유효기간을 다루지 않는 결정을 등재하되 **남는 위험을 한계로 적었다** |
| `OPEN-QUAL-09` | §3.2.2 | 시공능력 요건 **미달의 처리**를 정하지 않았다 |
| `OPEN-SET-04` | §2.2.3 | 재관측 **노출 여부**를 정하지 않았다. 필드만 둔다 |
| `OPEN-SET-05` | 사전 §9 나열 · `scope.md` 「닫지 않는 것」 | 재공고 대사 대상 선택 규칙은 **이 문서가 다루는 축이 아니라 본문에 자리가 없다.** 사전 §9가 그 예외를 명시한다 |
| `OPEN-SET-06` | §6.4 | embargo **임계 값을 쓰지 않았다** |
| `OPEN-SET-10` | §1.5 · §4.2 | 시간축 대체의 **승인 여부와 허용 출처**를 정하지 않았다. `Substituted` variant 자리만 둔다 |
| `OPEN-REG-05` | §11 | **결정으로 닫힌 것을 등재**했을 뿐 새로 결정하지 않았다. 상류 registry 행은 고치지 않았다 |
| `OPEN-QUAL-10` | §1.2 · §1.2.4 · §5.5 · §9 · §11 · §12.2 · §13.4 | **운영자 보유액 축만** 결정 등재로 닫았다. **공고 게시 요건 축의 단위·과세는 정하지 않았다** — 조달청 문서에 근거가 없어 관측으로만 알 수 있고, §12.2가 `requiredAmount`의 단위에 이 `OPEN`을 소유자로 달았다. **`Money`로 정규화할 수 있다고 단정하지 않는다** |
| `OPEN-QUAL-11` | §3.2.2 · §3.2.5 · §9 · §11 | **허용업종의 단독/결합 충족을 정하지 않았다.** 앞서 U-8로 닫았다고 적었으나 **U-8은 다른 질문에 답한다** — §11의 ⚠ 정정이 그 귀속을 되돌렸고, 임시 처리는 **사유 있는 `Uncertain`**이다. 운영자 판정(2026-08-30)으로 **M1 1C가 관측해 닫는다** |

**새 미결을 만나면 정의를 붙이지 않고 `OPEN-DIC`로 등록했다** — A7 참조. 특히 §4.2의
`QUAL-03` 충돌은 **두 읽기를 병기하고 어느 쪽도 고르지 않았다.**

**상류 산출물 무변경**: `commands.md` **C-6.1**.

## A6 — 모든 legacy 인용이 `ed4b06c`에서 확인된다

0B가 세운 두 축 규약(`regression-ledger.md` §0.5)을 그대로 쓴다.

| 축 | 대상 | 방법 | 근거 |
| --- | --- | --- | --- |
| **기계** | 경로 존재 · 행 범위가 파일 길이 안 · 역전 없음 · **파일명만 쓴 인용** | 스크립트 전수(본문 인라인) | `commands.md` **C-7.1** |
| **사람** | 그 행 범위의 **내용이 서술과 맞는가** | **정본을 계산할 수 없다.** 항목마다 `git show ed4b06c:<path>`로 직접 열어 읽었다 | 아래 |

**사람 축에서 직접 연 파일** — 사전의 서술이 그 블록의 내용에 걸리는 것들:

`app/domain/money.py` · `app/services/bid_base.py` · `app/schemas/bid_summary.py` ·
`app/services/award_verification.py` · `app/services/query_predicates.py` ·
`app/models/models.py` · `app/models/pipeline.py` ·
`app/ai/predictors/historical/statistics.py` ·
`app/ai/predictors/historical/__init__.py` · `app/domain/settlement_maturity.py` ·
`app/services/settlement_maturity.py` · `app/services/license_eligibility.py` ·
`app/ai/predictors/legal_floor_spec.py` · `app/domain/floor_shortfall.py` ·
`app/services/floor_shortfall.py` · `app/core/constants.py` ·
`app/ai/predictors/distribution_extraction.py` · `app/domain/published_floor_rate.py` ·
`app/domain/rate_normalization.py` · `app/services/base_amount_basis.py` ·
`app/domain/reliable_base.py` · `app/services/notice_floor_shortfall.py` ·
`app/services/allocation_core.py` · `app/ai/price_prediction/price_regime.py` ·
`app/domain/award_rate_features.py` · `app/services/koneps/field_contract_spec.py` ·
`app/ai/floor_applicability.py` · `app/domain/aggregates.py` ·
`app/services/classification/text.py` · `app/services/koneps/scsbid.py` ·
`app/services/koneps/persistence.py` · `app/services/koneps/budget_fields.py` ·
`app/domain/estimate_provenance.py` · `app/ai/predictors/artifact_contracts.py` ·
`app/ai/bid_target.py` · `app/domain/basis_conversion.py`

**상류 노트에서 옮겨 온 인용도 직접 열었다.** 그 결과가 두 가지다.

1. **행 범위를 블록 경계로 넓힌 자리** — 선행 조사가 서술이 걸리는 줄만 가리킨 것을
   블록 경계로 바꿨다. 예: `app/schemas/bid_summary.py`는 `Field(` 호출 한 덩어리로,
   `app/ai/predictors/historical/statistics.py`의 confidence는 `def`부터 `return`까지,
   `app/domain/basis_conversion.py`는 docstring 전체로 인용한다.
2. **U-10이 위임한 근거 확인의 결과** — 사전 **§4.3**의 표가 정본이다. 「값의 근거인가」
   칸이 항목마다 판정을 낸다. **셈을 여기 옮겨 적지 않는다.**

**재확인하지 않은 것을 명시한다**: `capability-map.md` ML-11.4 F3의 legacy 실물(백필
커버리지 시계열)은 이 slice가 열지 않았다 — 선행 조사도 열지 않았다. 사전 §6.3이 그
사실을 `legacy-behavior` 인용으로만 다룬다고 적는다.

**이 축은 Codex 리뷰어 worktree에서 재현되지 않는다** — `bid-vector` symlink가 이
저장소 밖이고 git에 추적되지 않는다. 이 slice가 유일한 확인 지점이다(`scope.md`
「알려진 제한」).

## A7 — 미결은 `OPEN-DIC-NN`으로 등록한다

| id | 질문 | 등록 근거 |
| --- | --- | --- |
| `OPEN-DIC-01` | 면허·지역 축이 정책 version을 싣는가 | U-6의 제외 절 ↔ `capability-map.md` QUAL-03의 **무조건** acceptance ↔ `v2-지침서.md` §4.2가 어긋난다. **운영자 결정의 범위**다 |
| `OPEN-DIC-02` | 시공능력평가액 공시의 갱신 주기·시행 구간 | "직전 연도"와 "공고일 기준 직전 해"가 같으려면 갱신이 역년 경계여야 한다. 이 저장소에 근거가 없다 |
| `OPEN-DIC-03` | `SkipReason` 어휘의 전수성 | 게이트 사다리를 M1에서 옮길 때 확정된다. 지금 닫으면 발견될 사유가 갈 곳을 잃는다 |
| `OPEN-DIC-04` | `AllocatedBudget`·`YegaAmount`·`AwardAmount`의 과세 처리 | U-1·U-1b는 두 금액만 정했다. 나머지 셋은 결정도 문서 근거도 없다 |
| `OPEN-DIC-05` | `BaseAmountProvenance`의 승인 라벨 다섯이 legacy 실측을 덮는가 | **승인 명세(`v2-지침서.md` §4.3)의 집합을 이 문서가 바꿀 수 없다.** Codex finding **B**가 그 변경을 적출했고, 이 문서는 **승인 집합으로 되돌린 뒤 차이를 미결로 등록**했다 |
| `OPEN-DIC-06` | V2 canonical write 경로가 `Undeclared` provenance를 거부하는가 | Codex 2차 high 1이 「provenance 필수 필드이므로 C2가 타입으로 `0`」을 적출했다. `Undeclared`가 집합의 멤버라 그 주장이 서지 않고, **거부할지는 어댑터의 의무를 정하는 결정**이라 이 문서가 정할 자리가 아니다 |
| `OPEN-DIC-07` | 전송 멱등 키와 재관측 키가 각각 무엇으로 이루어지는가 | Codex 2차 high 3이 멱등 단위와 재관측 계수의 충돌을 적출했다. **두 역할을 한 키가 겸할 수 없다는 것**은 모순 제거로 확정되나 **키의 구성**은 U-3도 `OPEN-SET-04`도 정하지 않았다 |

**등록 자리는 사전 §9 하나다.** `capability-map.md` §12와 ledger `OPEN-REG`는
**out_of_scope**이며 무변경이다(`commands.md` **C-6.1**). 중앙 registry 통합은 별도
slice의 몫이다(`regression-ledger.md` §10.3).

**id 유일성과 표 행 수**: `commands.md` **C-6.2**.

## A8 — 불변

| 불변 | 확인 |
| --- | --- |
| 인용 형식 위반 0 (파일명만 쓴 인용 · 행 범위 초과 · 경로 부재) | `commands.md` **C-7.1** |
| 중복 id 0 | `commands.md` **C-6.2** |
| secret 스캔 | `commands.md` **C-8** — 매치와 그 판정이 그 절에 있다 |
| `git diff --check` 0 | `commands.md` **C-2.2** — **`base_sha` 기준과 `review_base` 기준을 함께 낸다.** 그 블록은 **선언 SHA 하나만** 재고 커밋 이력만 읽으므로 `bid-vector`·`_workspace` 없이도 재현된다. 리뷰 시점의 HEAD로 확인하려면 그 블록의 SHA 자리를 HEAD로 바꾼다. **이 검사가 지적을 낸 적이 있고 닫힌 방식은 한 가지가 아니었다** — 어느 커밋에서 무엇이 어떻게 닫혔는지는 `scope.md`의 커밋 표에 있다. **C-10은 이 축을 재지 않는다** |
| `in_scope` 밖 경로 변경 0 | `commands.md` **C-2.1** — 이 slice의 커밋을 고른 뒤 **그 커밋이 건드린 경로 전부**를 본다 |
| **기록된 출력이 실제 stdout인가** | `commands.md` **C-10** — **두 환경에서 잰다**: `C-10.1`(`bid-vector`·`_workspace`가 있는 트리) · `C-10.2`(없는 clean worktree). **검사의 형태·전제·자기제외 기제는 그 절 머리가 선언한다** — 이 행은 그것을 가리키기만 한다 |

---

## 다른 slice 소관 — `pending`

`milestone-0.md` 「완료 조건」 가운데 이 slice가 담당하지 않는 항목.

| 완료 조건 | 소관 |
| --- | --- |
| V2 필수 capability마다 사용자 가치와 acceptance scenario | 0A/0A2/0A3 (`capability-map.md`) |
| Kotlin service 범위에 Python 파일/endpoint를 그대로 옮기는 항목이 없다 | 0A 계열 |
| ML 재활용 대상의 모듈 단위 식별과 잘라낼 결합 | 0A2 ML-11 · 0D ADR 0001·0009 |
| 기존 회귀마다 타입·계약·테스트 중 최소 하나의 예방책 | 0B (`regression-ledger.md`) |
| Python 결과가 정답이 아니라 참고임을 모든 문서가 명시 | 각 산출물 — 이 사전은 §0.2가 층 어휘로 명시한다 |
| `fixtures/manifest.yaml` schema와 후보 목록 | fixture-curator |
| ADR 아홉 건 | 0D |

---

## 알려진 제한

사전 **§8**이 정본이다. 이 slice에 고유한 것만 여기 덧붙인다.

- **`capability-map.md`에서 승인된 목록 밖의 부정확을 하나 더 찾았다**(사전 §10.1 **X-7**).
  고치지 않고 구별해 적었으며 실측은 `commands.md` **C-7.5**가 낸다.
- **`v2-지침서.md` §4.2와 U-7이 어긋난 채 남는다**(사전 §13.5). 지침서 개정은 이 slice의
  범위 밖이다.
- **`OPEN-ML-05`의 미분류가 이 slice로 늘었다**(사전 §4.3). U-10의 기준을 확인된 문면에
  적용한 결과이며, U-10 자신이 보호하는 방향이다.
