# M6/6G Kotlin 수집 레인 — 실행 명령과 결과

- base `678c6ed7` · worktree `bid-vector-v2-m6-6g` · 브랜치 `m6-6g/2026-09-27`
- 이 문서는 **Kotlin 레인**의 기록이다(Python 실험 레인은 자기 기록을 갖는다).

## acceptance (CI job 명령 그대로)

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon check` | BUILD SUCCESSFUL — 컴파일 · ktlint · detekt · sizeGate · 의존 방향 · architecture test · 계약 게이트 전부 |
| `./gradlew --no-daemon qualityBaseline` | BUILD SUCCESSFUL(게이트가 아니라 측정) |
| `./tools/one-command-check.sh` | 「Kotlin 전건 + Python 전건 통과」 |

실측 HEAD: `918a8e98`(이 레인의 마지막 산출물 커밋).

## 실 KONEPS 호출

**없다.** 이 레인은 코드·test·evidence 까지다. 모든 시나리오는 loopback in-process mock server
(`MockKonepsServer`, 소켓은 127.0.0.1 뿐)에서 돈다. 서비스 키는 합성값이고 운영 키를 쓰지 않았다.
실수집은 검증 뒤 운영자 키로 팀장이 연다.

## A-1 호출 상한 (승인 수치의 자리)

일 **20,000** · 총 **80,000**. 코드에 **기본값이 없다** — 두 값 모두 설정이 주어야 예산 원장이 선다.
원장이 「한 걸음 통째로 허가/거부」를 진다는 것과 일·총 두 한도가 서로 다른 사유로 무는 것은 test 가 잠근다.

## `OPEN-6F8-QUOTA-XML-ENVELOPE` — 닫았다

| 축 | 이전 | 지금 |
|---|---|---|
| 게이트웨이 XML 오류 봉투 | JSON 파서가 실패 → `StructureFailure` → 다음 슬롯 | 코드 원소를 읽어 **JSON 봉투와 같은 범주표**를 지난다 |
| 일 한도 초과(봉투 quota 범주) | 재시도 소진 뒤 멈춤(거부될 호출 여럿) | **재시도 없이 즉시** `QuotaExhausted` → 실행 멈춤 |
| HTTP 429(속도 한도) | 재시도 | **그대로 재시도**(legacy 실측 「~2분 안에 회복, 원인은 동시성」) |

판독은 문자열 탐색이 아니라 문서 구조다 — 한도 초과 메시지의 문면을 맞춰 보면 게이트웨이가 문구를
바꾸는 날 조용히 열린다. DTD·외부 엔티티는 끈다(오류 본문은 신뢰 경계 밖 바이트다).

**이 개정이 바꾼 기존 판정**: M3 의 두 test 가 「`resultCode 22` 는 재시도 대상」을 잠그고 있었다.
일 트래픽 한도는 백오프로 회복되지 않으므로 그 판정을 새 판정으로 바꿨다(두 test 모두 개정하고 사유를
문서에 남겼다). 게이트 술어를 바꾸는 변경이라 **표적 재검증 대상**이다.

## 누출 스캔

참조형: `grep -rniE -f config/quality/leak-patterns.txt <이 레인의 산출물 경로>` → **0건**.

## 이 레인이 만든 새 public 표면

| 표면 | 자리 |
|---|---|
| `SourceEndpoint.BID_PRICE_FORMULA_A` | procurement — 다섯째 수집 축 |
| `FieldConcept` 15 토큰(낙찰방법 2 · 예정가격결정방법 · 공고게시일시 · A 축 11) | procurement |
| `OpeningResultSourcePort.fetchBidPriceFormulaA` | procurement — 포트 메서드 하나 |
| `NoticeKeyHash` · `SamplingSeed` · `SampleStratum` · `SampleCandidate` · `StratumOutcome` · `SampleOutcome` · `StratifiedSampler` | workflow/collection |
| `CollectionCallBudget` · `CallBudgetLedger` · `BudgetOutcome` · `BudgetLimit` | workflow/collection |

`KonepsOperationPolicy.BID_PRICE_FORMULA_A`·`readGatewayErrorCode` 는 `internal` 이라 표면이 아니다.

## 계약 문장 ↔ 심볼 대조

| 계약 문장 | 심볼 | 무엇이 잠그는가 |
|---|---|---|
| D-6G-11 「일 한도 초과는 멈춤, 429 는 직렬 유지」 | `isRetryableStep` 의 봉투 분기 | 「즉시 멈춘다」·「429 는 재시도된다」 두 test |
| D-6G-11 「공고 식별자 해시 + 정책 seed 로 결정적으로」 | `StratifiedSampler.select` | 같은 seed 재현 · 후보 순서 무관 · seed 다르면 다름 |
| D-6G-11 「층 = 업무 대분류 × 공고 주」 | `SampleCandidate.stratum` | 업무 가름 · 주 가름 두 test |
| D-6G-11 「표본 목록 sha256」 | `SampleOutcome.sampleListSha256` | 정렬 목록에서만 나온다는 test |
| 우회 ⑦ 「결과를 보고 표본을 고른다」 | `SampleCandidate` 의 칸 | **타입**이 막는다 — 결과를 담을 칸이 없다 |
| D-6G-11 「호출 상한 — 설정값, 기본값 없음, 초과 시 멈춤」 | `CollectionCallBudget`·`CallBudgetLedger` | 기본값 없음 · 일/총 두 사유 · 한 걸음 통째 거부 |
| D-6G-12 「낙찰방법(`sucsfbidMthdCd`·`Nm`)」 | `AWARD_METHOD_CODE`·`AWARD_METHOD_NAME` | 관측까지 닿는 wire test(계약 표만 보는 test 는 이것을 못 잰다) |
| D-6G-12 「A값 오퍼레이션 + 합산 여부 술어 + A 공개일시」 | `SourceEndpoint.BID_PRICE_FORMULA_A` 축 13행 | 관측 생존 · 요청 축 · allow-list 반전 · 두 일시 분리 |
| D-6G-12 「예정가격 결정방법」 | `PLANNED_PRICE_DECISION_METHOD` | 계약 원장 test |
| D-6G-13 제외 열다섯 | `snapshot-schema.md` §4 의 입력 대조표 | 판정은 Python 레인 모듈이, 입력은 이 레인이 |
| D-6G-2 「스냅숏 · manifest」 | `snapshot-schema.md` | 레인 간 계약 문서(구현 전) |

## 스키마 합의 (2026-09-27, 두 레인)

초판(평평한 한 객체 · 금액 문자열 · `exclusions` 배열)과 Python 레인 독자 초안이 엇갈려 **Python 레인의
구조를 채택**했다 — `notice`(투찰 시점) / `outcome`(개찰 시점) 분리가 누출 금지를 규율이 아니라 **타입**으로
닫는다(전략이 예정가격을 보려면 타입을 고쳐야 하고 그 편집이 diff 에 드러난다). 금액 문자열 표기는 철회했다
— 소비 쪽 산술이 `float` 전 구간이라 읽는 즉시 float 이 되어 churn 만 남는다(금액은 JSON 정수로 못박아
왕복만 exact 하게 둔다). 생산 쪽 정정은 nullability 와 판정 가능성 — 상세는 `snapshot-schema.md`.

## 알려진 제한

1. **기초금액조회 오퍼레이션을 부르지 못해 값 셋이 비어 있다** — 예가 범위율 둘
   (`rsrvtnPrceRngBgnRate`·`EndRate`) · 순공사원가(`bssAmtPurcnstcst`) · `bidPrceCalclAYn`(A값 공고
   여부). 셋 다 기초금액조회 3종에만 있고, 그 오퍼레이션의 **요청 계약**(`inqryDiv` 축과 필수 항목)이
   선행 조사에 없어 지어내지 않았다. 신설 **`OPEN-6G-BASE-AMOUNT-OPERATION`**. 파급 셋: **S0 의 반폭 h**
   (D-6G-12 — 상수 2%·3% 로 메우면 안 된다, 공고별 필드라는 것이 P-3 의 발견이다) · 제외 ⑨(순공사원가
   98% — 「입력이 없으면 제외」가 전량에 걸린다) · 공사의 A값 공고 판정.
2. **호출 예산은 걸음 단위 근사다.** 한 걸음(공고 하나의 상세 조회)이 여러 페이지를 걸으면 그 걸음의
   실제 호출 수는 걸은 뒤에 알려진다 — 초과분은 최대 한 걸음의 페이지 수다. 어댑터 안쪽에 예산을
   넣으면 정확해지지만 의존 방향이 뒤집힌다.
3. **제외 ⑪(지자체 발주)을 판정하지 못한다.** 기관 코드에서 지자체를 가르려면 「행자부 코드 공간 ↔
   지자체」 대응이 필요한데 authoritative 하게 확보하지 못했다 — 지어내면 DEC-03(운영자 확정 범위)을
   코드가 조용히 재정의한다. `demand_agency_code` 를 입력으로 싣고 판정은 연다. 신설
   **`OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`**. 용역·물품의 「A값 공고인가」도 같은 성질이다 —
   `bidPrceCalclAYn` 이 공사기초금액조회에만 있어 A 항목 합이 0 인지로 간접 판정할 수밖에 없다.
4. **`smkpAmt`(표준시장단가금액)의 합산 근거 예규 문면을 확보하지 못했다.** 응답이 적용 여부 술어를
   함께 주므로 별도 규율이 있다는 뜻이지만, 계약은 관측값과 술어를 나르기만 하고 합산 판단을 하지 않는다.
5. **A 합산 항목의 `basis` 가 `null`이다.** A 는 기초금액·예정가격·낙찰금액 어느 축도 아니라 산식의
   항이고, 그 셀에 맞는 basis 어휘가 승인 표에 없다(미확정 칸은 인스턴스화하지 않는다).
6. **`bidNtceOrd` 를 A 오퍼레이션의 행 식별자로 쓴다.** 문서가 요청 항목으로 적지 않아 보내지 않고,
   응답의 차수 키가 행을 가른다. 한 공고번호·한 차수에 행이 둘 이상이면 뒤 행이 `duplicate` 로 접힌다
   — 그런 응답의 관측이 아직 없다.

## 하지 않은 것 (이 레인의 몫 가운데)

| 몫 | 상태 |
|---|---|
| 수집 러너 갈래 배선(개찰결과 목록 → 표본 → 공고별 상세 셋) | **안 했다** — 순수 코어(표본·예산)만 섰다 |
| 스냅숏 추출 명령(JSONL·manifest) | **안 했다** — 스키마 계약 문서만 섰다 |
| 제외 사유 판정의 구현 | **이 레인 몫이 아니게 됐다** — 두 레인 합의로 판정은 Python 쪽 한 자리에서 하고, 이 레인은 입력 칸만 진다(스키마 §4). 판정 자리가 둘이면 어긋날 때 정본이 없다 |

셋 다 이 레인이 만든 표본·예산·필드 계약을 입력으로 쓴다 — 넣을 자리는 열려 있고 채우지 않았다.
