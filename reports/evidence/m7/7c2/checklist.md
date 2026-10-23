# M7/7C② — 점검표

## 0. Phase 1 설계 (code 0, 2026-10-23)

계약(`scope.md`)의 -a 범위를 설계로 고정한다. 아래 0.8 은 **조사가 뒤집은 계약 전제**이고 0.9 가 그에 따른
팀장 결정 요청이다. 구현은 팀장 답(`D-7C2-n`) 뒤에 시작한다.

### 0.1 operation 모양 — POST, 본문에 공고 식별자

`POST /api/bid-price-requests`, 본문 `{ "noticeNumber": …, "noticeRound": … }`.

| 축 | 선택 | 근거 |
|---|---|---|
| 메서드 | **POST** | ① 계약의 종착점이 **알림함 한 항목 기록**(R-4)이다 — 7C③ 머지 뒤 같은 operation 이 쓰기를 가진다. GET 으로 출하한 뒤 POST 로 바꾸면 이미 출하된 계약을 깨는 변경이 된다. ② CSRF 가 **구성으로** 닫힌다: `BrowserSessionAuthenticator` 의 면제 집합은 `GET`·`HEAD`·`OPTIONS` 셋뿐이라 POST 는 요구 쪽에 자동으로 선다(열거를 더하지 않는다). GET 이면 CSRF 요구가 애초에 없는 자리가 하나 더 생긴다. ③ 브라우저·프록시의 prefetch·재시도가 평가를 임의로 발화시키지 않는다. ④ 선례가 같은 파일군에 있다 — `evaluateDryRun` 도 POST + 본문이다. |
| 식별자 자리 | **본문** | 7C④ 관례(「값을 경로에 넣지 않기 위해 본문으로」)와 `evaluateDryRun` 형태를 따른다. 공고번호·차수는 비밀이 아니지만, 경로 매개변수로 두면 퍼센트 인코딩 축이 하나 더 생기고(7C① 이 그 축에서 400 경로를 따로 지었다) 전 요청 audit 의 경로 칸 어휘가 공고마다 달라진다. |
| 복수형 경로 | `bid-price-requests` | `evaluation-dry-runs`·`edit-sessions` 와 같은 「요청·세션을 만드는 자리」 어휘. |
| 본문 파싱 | 원시 트리 + 명시 검증 | `parseCurrentActiveBids` 와 같은 규율(D-6A3-19) — Jackson 기본 강제 변환이 `123`(수) 을 공고번호로 조용히 받아들이지 않게 한다. 누락·`null`·비문자열·공백·제어문자·길이 상한 초과는 400 `INVALID_REQUEST`. |
| 없는 공고 | 404 `NOT_FOUND` | 기존 `ErrorBody` 어휘에 이미 있다(새 code 를 만들지 않는다). |

**멱등성**: -a 는 도메인 쓰기가 0 이라 멱등 키가 필요 없다. 7C③ 배선에서 알림함 기록이 붙는 순간
중복 억제 키(NOTI-10)가 필요해진다 — 그 키는 7C③ 의 표가 소유하고 이 operation 은 그 포트를 부를 뿐이다.
`Idempotency-Key` 헤더를 -a 에 미리 두지 않는다(소비자 없는 필드 금지).

### 0.2 응답 계약 — 평탄하다(D-6A1-38 게이트를 넓히지 않는다)

**계약 결정의 핵심**: `OpenApiContractTest` 의 평탄 술어는 「속성 정의는 스칼라 또는 스칼라 배열만」이고
순회 루트가 문서 전체다. 이 slice 는 **그 술어를 건드리지 않는다**. 축별·태도별 값은
`NoticeSummary` 가 이미 쓰는 **접두사 평탄화**(`baseAmountWon`·`baseAmountBasis`…)로 낸다.

근거: ① V2 에서 판정이 서는 자격 축은 **면허 하나**이고(QUAL-08·10·11 은 판정 로직 자체가 없다), 태도는
**고정 3개 enum** 이다 — 둘 다 「열거인가 구성인가」에서 **열거가 정직한 쪽**이다(가변 arity 가 아니다).
② 중첩을 열면 contract-keeper 결정(`OPEN-6A3-EVALUATION-DETAIL` 의 「깊이 정확히 1」)이 필요하고, 그 게이트
술어 변경은 severity 무관 표적 재검증 대상이다 — **이 slice 가 그 비용을 질 근거가 없다**(열거로 같은 정보를 낸다).
③ 7C④ 레인이 같은 판단을 이미 했다(`unjudgedAxes` 를 스칼라 배열로).

**유일한 중첩 후보**는 `LicenseJudgement.missingByGroup`(그룹 → 미보유 면허 집합)이다. 그룹은 가변 arity 라
접두사 열거가 안 된다. 합집합으로 접으면 **그룹 간 OR 을 AND 로 오독**하게 만든다(「이것들이 다 필요하다」로
읽힌다). 그래서 **접지 않고 상태로 가른다** — 아래 `licenseMissingDetailState`.

#### 응답 필드 (전부 스칼라 또는 스칼라 배열)

| 필드 | 형 | 출처 / 뜻 |
|---|---|---|
| `noticeNumber` · `noticeRound` | string | 요청 echo |
| `correlationId` | string | 이 평가가 쓴 상관 ID(기존 `ErrorBody`·audit 어휘와 같은 값) |
| `evaluatedAt` | date-time | 주입 시계 |
| `strategyRevision` | integer | 요청당 한 번 읽은 전략의 개정 |
| **평가 상세 (`OPEN-6A3-EVALUATION-DETAIL` 해소)** | | |
| `outcome` | enum `REACHED`·`NOT_REACHED` | `CandidateEvaluation` 두 갈래 |
| `verdict` | enum? `BID_NOW`·`REVIEW`·`SKIP` | `Verdict` — `NOT_REACHED` 면 없다 |
| `verdictReasonCodes` | [string] | `BidNowReason`·`ReviewReason`·`SkipReason` variant 이름 |
| `dropStage` | enum? | `EvaluationStage` variant 이름 |
| `dropReasonCode` | enum? | `EvaluationDropReason` variant 이름 |
| `noticeStatus` | enum | `Notice.status`(`NoticeNotBiddable` 의 payload 와 같은 값) |
| `failedWatchRuleIds` | [string] | `WatchGateRejected` payload |
| `watchUndeterminableReason` | enum? | `WatchGateUndeterminable` payload |
| `scoreThreshold` · `scoreActual` | string?(10진) | `BelowMinimum*Score` payload 두 수 |
| `inboxRecordState` | enum `NOT_RECORDED_YET` | **R-4 공백을 계약에 드러낸다** — 7C③ 머지 뒤 값이 늘어난다 |
| `inboxRecordNotRecordedReason` | enum `INBOX_WRITE_PORT_NOT_MERGED` | 같은 자리 |
| **자격 (QUAL-01·03·05)** | | |
| `licenseVerdict` | enum `ELIGIBLE`·`INELIGIBLE`·`UNCERTAIN` | `LicenseVerdict` 세 갈래(접지 않는다 — `Uncertain ≠ Ineligible`, U-5) |
| `licenseUncertainReason` | enum? | `UncertainReason` 다섯 |
| `licenseRequiredLicenses` | [string]? | `LicenseJudgement.requiredLicenses` — **근거 원문의 「무엇을 요구했나」** |
| `licenseRequirementSourceFields` | [string] | `RequirementSourceField` — **근거 원문의 「어느 필드에서」**(QUAL-05) |
| `licenseGroupsSatisfied` · `licenseGroupsMissing` | [string] | 그룹 라벨(`G<번호>` / `UNGROUPED`) — 그룹 간 OR 구조를 숫자가 아니라 라벨로 낸다 |
| `licenseMissingLicenses` | [string] | **미보유 그룹이 정확히 하나일 때만 채운다** |
| `licenseMissingDetailState` | enum `NOT_APPLICABLE`·`SINGLE_GROUP`·`MULTIPLE_GROUPS_NOT_FLATTENED` | 둘 이상이면 합집합을 **내지 않는다**(OR 을 AND 로 오독시키지 않는다) |
| `licenseUnparsableRowCount` | integer | 파싱 실패 요건 행 수 |
| `licenseValidity` | enum `NOT_VERIFIED` | **U-7 의 「유효기간 미검증이 판정 결과에 드러난다」 요구를 이 표면이 처음 집행한다** |
| `licensePolicyVersion` | string | 어휘 테이블 version(QUAL-05 acceptance 둘째) |
| `unjudgedAxes` | [string] | `QUAL_08_REGION`·`QUAL_10_PERFORMANCE`·`QUAL_11_CONSTRUCTION_CAPACITY` — 7C④ 어휘와 **집합 등식 test** |
| **기초금액 basis (DEC-01)** | | |
| `baseAmountWon` · `baseAmountCurrency` · `baseAmountBasis` · `baseAmountVatTreatment` · `baseAmountProvenance` | | **7C① `NoticeSummary` 와 같은 이름·같은 뜻**(두 표면이 같은 공고에 다른 어휘를 쓰지 않는다) |
| `baseAmountResolution` | enum? `DIRECT`·`FALLBACK_FROM_BUDGET`·`DERIVED_FROM_OPENING` | `ResolvedBaseAmount` 세 갈래 — 「파생이 원본을 덮지 않는다」가 응답에서도 보인다 |
| **하한율 (DEC-03 · DEC-10 표시 축)** | | |
| `publishedFloorRateFraction` | string?(10진) | `Notice.floorRate.rate` — 부동소수로 내지 않는다 |
| `publishedFloorRateOrigin` | enum? `NOTICE_VALUE`·`STATUTORY_TABLE` | `FloorRateOrigin` |
| `publishedFloorRateNoticeRound` | string? | `NoticeValue` payload |
| `floorRateGroundsState` | enum `USABLE`·`ABSENT`·`ORIGIN_NOT_PUBLISHED` | **게시값만 근거다**(운영자 결정 2026-10-15) — origin 이 `STATUTORY_TABLE` 이면 근거로 쓰지 않고 그 사실을 말한다 |
| **하한 미달 (DEC-04)** | | |
| `floorShortfallState` | enum `NOT_COMPUTED` | -a 는 값 하나. -b/ML-01 이 `MEASURED`·`UNMEASURABLE` 을 더한다 |
| `floorShortfallNotComputedReason` | enum `SHORTFALL_POLICY_NOT_PUBLISHED`·`ASSESSMENT_SAMPLE_SOURCE_NOT_WIRED` | **`FloorUnmeasurableReason` 을 쓰지 않는다** — 그 어휘는 「재려 했으나 재지 못했다」이고 지금은 「잴 기계가 배선되지 않았다」다. `SampleInsufficient(required=N, actual=0)` 로 적으면 보지 않은 것을 봤다고 말하는 것이 된다 |
| **신뢰도 (ML-03)** | | |
| `mlAnalysisState` | enum `ANALYZED`·`UNAVAILABLE`·`SIMILARITY_PROJECTION_NOT_READY`·`NOT_REACHED` | 이 요청이 실제로 받은 `MlAnalysisOutcome`(기록 어댑터가 잡는다) |
| `mlUnavailableReason` | enum? | `MlUnavailableReason` 일곱 |
| `confidenceState` | enum `NOT_AVAILABLE` | -a 는 값 하나 |
| `confidenceNotAvailableReason` | enum | `ML_SCORE_NOT_PROVIDED`·`ML_UNAVAILABLE`·`SIMILARITY_PROJECTION_NOT_READY`·`EVALUATION_NOT_REACHED_ML_STAGE` |
| **3안 (DEC-05 · ML-01)** | | |
| `bidPriceMenuState` | enum `NOT_COMPUTED` | **빈 배열이 아니라 이유 있는 상태**(계약 문면) |
| `bidPriceMenuNotComputedReason` | enum `MENU_PATH_NOT_WIRED` | **이 배선에 산출 경로가 없다**는 사실 하나만 말한다. 「커널을 불렀더니 출처가 없었다」(`SOURCE_ABSENT`)로 적지 않는다 — 부르지 않았다. 상류 사실 둘(DEC-05 커널 미머지 · 후보 비율 출처가 미가용 ML)은 스키마 description 과 0.8 에 적고, 후속 slice 가 이 사유를 **커널 자신의 결과**로 바꾼다 |
| **고지** | | |
| `disclosures` | [string] | 전수 넷 — `YEGA_DRAWN_PRIVATELY` · `NOT_AUTO_SUBMITTED` · `STATUTORY_FLOOR_RATE_NOT_INTERPRETED` · `PRICE_FITNESS_IS_NOT_PROBABILITY` |

**고지를 enum 코드로 내는 이유**: 한국어 완성 문장을 서버가 들지 않는다(DEC-12 A1 — legacy 는 문구를
영속해 계산이 바뀐 뒤에도 옛 분모를 말했다). g1dec05 의 `BidMenuDisclosure` 가 같은 판단을 이미 했고
-a 의 두 코드는 그 enum 의 두 variant 와 **이름이 맞물린다**(가산성, 0.7).

**「확률 아님」을 bool 필드로 두지 않는다** — 상수 `false` 필드는 아무것도 막지 못한다. 대신 ① 필드 이름을
`confidence…`/`priceFitness…` 로 두고 `probability` 를 쓰지 않는다 ② 고지 코드 `PRICE_FITNESS_IS_NOT_PROBABILITY`
를 전수 집합에 넣어 표면이 문장을 지을 의무를 값과 함께 옮긴다 ③ **응답 필드 이름 전수에 `probability`·`확률`
이 없다**를 계약 test 가 집합으로 잰다(ML-03 의 타입 분리를 표면에서 집행).

**값 없는 자리에는 필드를 두지 않는다.** `floorShortfallFrequencyNumerator`·`priceFitnessScore`·태도별 금액은
-a 에 **선언하지 않는다** — 생산자 없는 필드는 소비자 없는 variant 와 같은 결함이다(g1dec05 가 같은 이유로
`DerivationPolicyData` 에 필드를 두지 않았다). 상태 enum 과 사유만 선언하고 값은 생산자가 서는 slice 가 더한다.

### 0.3 기존 커널 재사용 — 무편집 조립

**원칙**: 공고 단건 평가는 `EvaluateCandidatesUseCase` **그 자체**를 돈다. 판정 경로를 복제하지 않는다.
응답이 필요한 모든 값은 **그 한 번의 실행이 실제로 만든 값**에서 온다 — 두 번째 계산 자리를 만들지 않는다.

| 필요한 것 | 조립 수단 | 선례 |
|---|---|---|
| 후보를 공고 하나로 | `SingleNoticeCandidateSource(notice)` — `CandidateSourcePort` 구현, 1원소 리스트 | `PinnedStrategyRepository` |
| 공고 읽기 | **새 읽기 포트** `NoticeByIdPort` + `reconstructNotice` 재사용 어댑터 | `JdbcCandidateSource` 가 같은 복원 함수를 쓴다 |
| 자격 provenance | `LicenseJudgementPort.judgementFor(notice)` 로 **한 번** 판정 → 그 `verdict` 를 `PinnedLicenseGate` 로 use case 에 넘긴다 | `EvaluationDryRunFactory` 가 전략에 쓰는 pin 관례 그대로 |
| ML 결과 | `RecordingMlAnalysis(delegate)` — use case 가 부른 결과를 그대로 보관 | `RecordingNotificationRequestPort` |
| 알림 처분 | 기존 `RecordingNotificationRequestPort` 재사용 | 그대로 |
| 전략·여력 | 기존 `StrategyReader` + `RequestCapacityPort` | 그대로 |

**pin 이 핵심이다.** 자격 판정은 요청당 **정확히 한 번** 돌고, 응답의 provenance 와 커널이 쓴 verdict 가
**같은 값**에서 나온다 — 두 자리에 같은 사실을 두지 않으므로 둘이 갈릴 자리가 없다. `LicenseGatePort` 의
시그니처를 넓히지 않으므로 `EvaluateCandidatesUseCase` 문면도 그대로다.

**기존 어댑터 1줄 위임**: `StoredRequirementLicenseGate` 는 지금 `LicenseEligibility.judge(...).verdict` 로
봉투를 버린다. 같은 세 줄을 새 어댑터에 복사하지 않기 위해, 새 `StoredRequirementLicenseJudgement` 가
봉투를 내고 기존 클래스가 그 결과의 `.verdict` 를 내도록 **위임 한 줄로** 바꾼다(거동 불변, 위임을 test 로 잠근다).
→ in_scope 조정 요청(0.9 Q4).

**분석 예산**: 단건 경로는 `analysisBudget = null` 로 돈다 — 예산은 **스캔 비용 통제**이고 운영자가 지목한
단건 요청의 허가 조건이 아니다. 이것이 차분의 **유일한 의도된 어긋남**이다(0.4 ②). → Q2.

### 0.4 차분 test 계획 (vs `evaluateDryRun`)

① **값 차분(workflow, fake port)** — 같은 전략·같은 fake(watch·license·ml·clock)·공고 셋 {A,B,C}에서
`evaluateDryRun` 경로의 전건 실행 결과와 단건 실행 결과를 **공고별로 대조**한다. 비교는
`CandidateEvaluation` 전 필드 등식(상관 ID 제외 — 호출마다 새 값이다). 표는 **드롭 단계 전수 + 판정 세 갈래**를
덮는다(`EvaluationStage` 일곱 · `Verdict` 셋) — 하나라도 빠지면 그 단계의 차분이 측정되지 않는다.

② **의도된 어긋남 하나를 고정한다** — `candidateLimit = 1` 인 전략에서 전건은 B·C 를
`AnalysisBudgetExhausted` 로 떨어뜨리지만 단건은 B 를 평가한다. 그 차이를 **test 가 단언**하고 계약 문면과
알려진 제한에 적는다(조용한 차이로 두지 않는다).

③ **어댑터 차분(실 DB)** — 같은 `notice` 행에서 `NoticeByIdPort` 어댑터와 `JdbcCandidateSource` 가 **같은
`Notice` 값**을 복원한다. 두 어댑터가 같은 복원 함수를 쓴다는 사실이 ①의 전제다.

④ **변이로 RED 되는지**: 단건 경로가 use case 를 거치지 않고 응답을 조립하면(판정 복제) ① 이 붉어진다.
pin 을 끊어 자격을 두 번 판정하면 「응답 provenance ↔ 커널 verdict 일치」 test 가 붉어진다.

### 0.5 요청당 계산 비용 상한 (선언 + 계수 측정)

| 축 | 상한 | 어떻게 재는가 |
|---|---|---|
| 공고 읽기 | 1행(번호·차수 지정 조회) | **후보 전건 스캔을 부르지 않는다** — 단건 factory 의 생성자가 `CandidateSourcePort` 를 **받지 않는다**(주입 표면 test) |
| 전략 읽기 | 1 | 계수 fake |
| 자격 판정 | 1 | 계수 fake(pin 이 두 번째 호출을 구조적으로 없앤다) |
| ML 호출 | ≤1 | 계수 fake |
| 감시 입력 조회 | ≤1 | 계수 fake |
| 도메인 쓰기 | **0** | path-limited `git diff` 로 마이그레이션 0 + 쓰기 SQL 0 |
| 그 밖 | 전 요청 audit 1행 | 기존 `RequestAuditFilter` 가 모든 경로에 거는 것과 같다(이 경로가 새로 만드는 쓰기가 아니다) |
| 응답 크기 | O(요건 행 수) | 면허명 배열만 가변 — 요건 행은 공고당 유한 |

**rate limit 을 두지 않는다**(과잉) — 지금 비용은 「색인 1행 + 순수 커널」이고 ML 은 미가용 고정이다.
ML 이 배선되는 날 요청당 deadline 이 필요해진다 → 알려진 제한 + 위 계수 test 가 그 변화의 발화 장치다.

### 0.6 설계 검토 (0)~(3)

#### (0) 위협 모델 경계

**방어한다** — ① 인증 없는 호출이 평가를 돌리는 것 ② 다른 계정 프로필로 평가되는 것 ③ 응답이 **근거 없는
숫자**를 싣는 것(특히 3안·신뢰도·하한 미달 빈도) ④ 응답이 PII·raw 관측을 싣는 것 ⑤ 한 요청이 상한 없는
계산을 돌리는 것 ⑥ 판정을 사다리 밖에서 지어낸 값으로 내는 것.
**방어하지 않는다** — 인증된 단일 운영자가 공개 공고를 여러 번 평가하는 것(공고는 공공 데이터, 비용은 0.5 상한 안).

#### (1) 열거인가 구성인가

| 축 | 닫는 방식 |
|---|---|
| 인증 | **구성** — `RequestAuthenticationFilter` 가 와일드카드 하나로 등록돼 새 endpoint 가 필터 밖에서 태어날 수 없다. 허용 목록 밖은 전부 401 |
| CSRF | **구성** — 면제는 `GET`·`HEAD`·`OPTIONS` 셋이고 POST 는 그 밖이다(열거를 더하지 않는다) |
| 응답 평탄성 | **구성** — 문서 전체 재귀 + 허용 목록 술어(이 slice 가 안 건드린다) |
| 판정 위조 | **구성** — `Verdict`·`CandidateEvaluation`·`NotificationRequest` 생성자가 `internal`, 유일 생성 경로가 커널 |
| 근거 없는 값 | **구성** — 생산자 없는 값은 **필드가 없다**. 상태 enum 이 -a 에서 단일값이라 「값이 있는 척」이 표현 불가능 |
| 자격 두 번 판정 | **구성** — pin 이 두 번째 호출 자리를 없앤다 |
| 고지 집합 · 미판정 축 · 필드 이름 | **열거**(가변 arity 가 아니다) — 집합 등식 test 가 잠근다 |

#### (2) 우회 — 열하나

| # | 우회 | 무엇이 막는가 |
|---|---|---|
| 1 | 자격 헤더·세션 없이 호출 | 필터 기본 거부 → 401. 새 경로가 허용 목록에 없음을 **집합 등식**으로 잰다 |
| 2 | 세션은 있고 CSRF 헤더 없이 POST | 면제 집합 밖 → 401. 표적 test |
| 3 | 메서드 변형(`GET`·`PUT`·`HEAD`)으로 같은 경로 | 매핑이 POST 하나 → 405 `METHOD_NOT_ALLOWED`, 기존 전수 test 가 센다 |
| 4 | 경로 변형(`/api/bid-price-requests/`·대소문자·`;a=b`·`/api/./…`) | 허용 목록 정확 일치 → 거부 쪽으로 떨어진다(fail-closed) |
| 5 | 공고번호에 제어문자·초장문·수 타입 | 원시 트리 명시 검증 → 400, 어느 입력인지 싣지 않는다 |
| 6 | 없는 공고로 **열거 탐색** | 404 하나, 사유를 나누지 않는다(「있으나 평가 불가」와 「없음」을 응답으로 구분시키지 않는다) |
| 7 | 판정을 커널 밖에서 조립해 `BID_NOW` 를 내기 | `Verdict` 생성자 `internal` + 차분 test ① (복제하면 결과가 갈린다) |
| 8 | 3안·신뢰도·빈도 자리에 **0 이나 빈 배열**을 넣어 「없음」을 「0」으로 보이게 | 그 자리에 **값 필드가 없다**. 상태 enum + 사유만 있고, 상태 enum 의 값 집합을 test 가 집합 등식으로 잠근다 |
| 9 | 법정 하한율을 게시 하한율 자리에 실어 근거로 보이게 | `floorRateGroundsState=ORIGIN_NOT_PUBLISHED` 분기 + 고지 코드 전수. 그 분기를 지우면 test 가 붉어진다 |
| 10 | 그룹 간 OR 을 합집합으로 접어 「다 필요하다」로 읽히게 | 미보유 그룹 ≥2 면 `licenseMissingLicenses` 를 **비우고** `MULTIPLE_GROUPS_NOT_FLATTENED` 를 낸다. 양성·음성 두 test |
| 11 | 응답에 공고명·기관명·요건 **원문 blob**·사업자번호를 실어 상세를 늘리기 | 응답 **필드 이름·값 전수**를 PII·raw 관측 어휘와 대조하는 test(7C① 관례) + 비밀값 스캔 게이트. 「근거 원문」은 (출처 필드 + 요건 면허명 + 정책 version)으로 낸다 — raw 관측 blob 은 **의도적으로 싣지 않는다** |

#### (2b) 값 획득 축 — 새 public 표면 전수

| 새 표면 | 밖에 허락하는 것 | 「경계로 처리」 실측 |
|---|---|---|
| `POST /api/bid-price-requests` (controller) | 공고 하나의 평가 결과 **읽기** | 인증·CSRF·audit 셋 다 필터 층 — 이 컨트롤러가 그 판정을 복제하지 않음을 의존 게이트로 잰다 |
| `NoticeByIdPort` (읽기 하나) | 공고 fact 한 건 | 쓰기 메서드가 **시그니처에 없다**. `app.http.denied-types` 로 컨트롤러 직접 수신 금지 |
| `LicenseJudgementPort` (읽기 하나) | 자격 판정 봉투 | 판정은 `LicenseEligibility.judge` 가 낸다 — 어댑터가 `LicenseVerdict` 를 조립하지 않음을 참조 단언으로 잰다 |
| `SingleNoticeCandidateSource` | 1원소 후보 목록 | `openCandidates()` 외 메서드 없음 |
| `PinnedLicenseGate` | pin 된 verdict 반복 반환 | 생성자가 이미 판정된 값만 받는다 — 스스로 판정하지 않는다 |
| `RecordingMlAnalysis` | delegate 결과 보관 | delegate 를 **바꾸지 않는다**(결과를 만들지 않는다) |
| 응답 DTO | 위 0.2 표 전부 | PII·raw 관측 0 을 필드 전수 test 로 |
| `app.wiring.BidPriceRequestFactory` | 요청 스코프 묶음 | **`CandidateSourcePort` 를 받지 않는다**(0.5) — 주입 표면 test |

**`object` 커널 주입 자리**: 이 slice 는 `object` 싱글턴을 새로 만들지 않는다. 정책은 전부 생성자 주입이고
(`LicenseQualificationPolicyData` 는 기존 배선 그대로), 사다리 정책 슬롯은 use case 기본값을 쓴다 —
이 경로에서 그 자리를 **밖에서 바꿀 인자를 열지 않는다**(단건 factory 가 `ladderPolicySlot` 을 받지 않는다).

#### (3) 과잉·미달

**하지 않는다(과잉)**: 평가·자격·DEC-05 커널 문면 변경 · 평탄 게이트 술어 변경 · ML 신규 배선 ·
알림함 임시 표·가짜 포트 · 화면 · 마이그레이션 · rate limit · `Idempotency-Key` · 법정 하한율 해석기 ·
DEC-04 표본 공급 port 와 하한 미달 정책 표(값 결정이 운영자 몫, 0.9 Q3).

**미달 금지(반드시 선다)**: 축별 판정 + 근거(출처 필드·요건 면허명·정책 version·유효기간 미검증) ·
기초금액 basis 다섯 칸 + `ResolvedBaseAmount` 갈래 · 게시 하한율 + origin + 「법정 하한율 미해석」 고지 ·
드롭 단계·사유 전수 · 미판정 축 · 세 「아직 산출 안 함」 상태와 사유 · 차분 test · 401/CSRF/405/404/400 ·
PII·raw 0 · 실 DB E2E 하나.

### 0.7 후속 slice 가 더하는 것 — 가산성 (이 slice 는 **-a 로 종결**, `D-7C2-1` Q1)

`D-7C2-1` 로 **-b 를 열지 않는다.** 3안은 ML-01 뒤 별 slice 다. 이 절은 그 slice 들이 **-a 필드의 뜻을
바꾸지 않고** 얹을 수 있음을 확인하는 자리이고, 이 slice 는 아래 어느 것도 구현하지 않는다.

#### (a) 알림함 기록 — 7C③ 머지 뒤 (붙일 자리만)

7C③ Phase 1(`D-7C3-1`, 미머지)이 확정한 시그니처:

| 축 | 값 |
|---|---|
| 패키지 | `bidvector.workflow.notification` |
| 포트 | `fun interface NotificationInboxPort { fun record(record: NotificationInboxRecord): NotificationInboxOutcome }` |
| record | `(account: AccountId, notice: NoticeId, fact: NotificationFact, occurredAt: Instant)` |
| 이 경로의 fact | `BidPriceRequestRecorded`(종류 `BID_PRICE_REQUEST_RECORDED`) |
| 중복 억제 키 | `(account_id, kind, notice_number, notice_round)` — 같은 공고 재요청은 **항목 하나를 갱신** |
| 결과 | `Created`·`Updated`·`Failed`(예외가 아니라 결과 갈래) |

**붙일 자리**: 단건 factory 가 이 포트를 생성자로 받고, **factory 가 낸 run 객체**가 평가 뒤 `record(...)` 를
한 번 부른다 — 컨트롤러는 포트를 직접 쥐지 않는다(기존 `app.http` 의존 게이트 규율, `EvaluationDryRunRun` 이
어댑터가 아니라 값을 내는 것과 같은 이유). `occurredAt` 은 이미 주입된 시계, `notice` 는 이 요청이 평가한
`NoticeId` 그대로다.

**-a 필드의 가산 경로**: `inboxRecordState` 가 `NOT_RECORDED_YET` → `CREATED`·`UPDATED`·`FAILED` 로 넓어지고
(enum 확장), `inboxRecordNotRecordedReason` 이 `FAILED` 의 사유 칸으로 이어진다. 중복 억제가 「갱신」이므로
`CREATED` 와 `UPDATED` 를 **접지 않는다** — 운영자에게 「새 항목」과 「같은 항목 갱신」은 다른 사실이다.

**실측한 공백과 그 처분(`D-7C2-2`, 보고 B1 의 답)**: 지금 **typed `AccountId` 가 핸들러까지 오지 않는다.**
세션 인증 경로는 계정을 알지만 그 값을 audit 주체 **문자열**(`account:<값>` 규약)로만 바꿔 심고, 자격증명
인증 경로는 **계정 개념이 아예 없다**(단일 운영자 자격 비교 하나). **audit 주체 문자열을 다시 파싱해
`AccountId` 를 복원하지 않는다** — R-2 소유 경계를 문자열 왕복으로 세우는 형태이고, 주체 라벨 규약이 바뀌는
날 조용히 틀린다.

처분: **운반 장치는 7B 가 세운다**(`D-7B-2` — 세션 인증이 typed `AccountId` 를 요청 속성에 심고 계정 범위
핸들러가 접근자 하나로 읽는다; 자격증명 경로는 계정이 없다). **따라서 알림함 기록을 배선하는 slice 에서 이
operation 은 계정 범위가 되어 세션 전용이 된다**(자격증명 경로로는 401). 그 전환은 이 operation 의 **인증
대안 집합을 바꾸므로** 그 slice 가 OpenAPI 의 `security` 선언과 계약 test 의 인증 대안 전수(「인증을 요구하는
operation 은 전부 자격 헤더 대안을 남긴다」)를 **함께** 고쳐야 한다 — 지금 그 전수 test 는 자격 헤더 대안을
요구하므로, 세션 전용으로 바꾸면서 그 test 를 손대지 않으면 붉어진다. 이 slice(-a)는 계정이 필요 없어
`NOT_RECORDED_YET` 에서 멈춘다.

#### (b) 3안 — ML-01 뒤 별 slice

g1dec05 의 `BidMenuInput` 은 다섯을 요구한다: `base`(선다) · `publishedFloorRate`(선다) ·
`shortfallPolicy`(**출하 정본 표 없음**) · `assessmentSamples`(**공급 port 없음**) ·
`rates: StanceRates`(**유일 출처가 ML 응답**).

그 slice 가 더하는 것(전부 **가산**, -a 필드는 뜻이 바뀌지 않는다):

| 더하는 것 | 모양 |
|---|---|
| `bidPriceMenuState` 값 | `OFFERED`·`PARTIALLY_OFFERED`·`BAND_COLLAPSED`·`UNMEASURABLE`·`SOURCE_ABSENT` 추가(enum 확장) |
| `bidPriceMenuNotComputedReason` | `MENU_PATH_NOT_WIRED` 를 **커널 자신의 결과**로 바꾼다(`SOURCE_ABSENT`·`UNMEASURABLE` 등) — 그 전환이 그 slice 의 효과를 응답에서 측정하게 한다 |
| 태도별 옵션 | **접두사 평탄화 3 × n** — `safe…`·`recommended…`·`aggressive…` 각각 `AmountWon`·`BidRateFraction`·`BindingConstraint`·`GroundsPolicyVersion`·`WithheldReason`. 태도는 고정 3 enum 이라 평탄 게이트가 그대로 닫혀 있다 |
| 고지 | -a 의 네 코드 중 둘을 `BidMenuDisclosure.entries` **매핑으로 바꾸고 집합 등식 test** 를 더한다(어휘 이중화를 닫는다) |
| DEC-04 | `floorShortfallState` 에 `MEASURED`·`UNMEASURABLE` 추가 + **태도별** 빈도·임계 사정률·편향 방향(`BidMenuOption.risk` 의 모양 그대로). -a 의 상태는 「이 공고에서 DEC-04 를 **잴 수 있는가**」라 그대로 유효하다 |

**-a 가 후속을 위해 미리 하는 일**: ① 3안·DEC-04·신뢰도를 **상태 + 사유**로 선언해 값 필드를 나중에
더하는 자리를 만든다 ② 고지 코드 이름을 `BidMenuDisclosure` variant 와 맞춘다 ③ 태도 접두사 어휘
(`safe`/`recommended`/`aggressive`)를 `BidStance` 와 같은 이름으로 예약한다(계약 문면에 적는다).
세 가지 다 **-a 가 쓰는 비용은 0**이다 — 필드를 미리 만드는 것이 아니라 **이름과 상태 축을 고르는 것**뿐이다.

### 0.8 조사로 드러난 사실 — **가격 절반은 production 에서 서지 않는다**

계약은 -b 를 「DEC-05 커널을 불러 3안을 채운다」로 적었다. 조사 결과 **그것은 g1dec05 머지만으로는 성립하지
않는다**. 근거 셋(전부 저장소 실측):

1. 출하 조립의 `MlAnalysisPort` 는 **항상-미가용 구현 고정**이다(`EvaluationWiring` 이 `UnavailableMlAnalysis`
   를 내고, 의존 게이트가 「`app.wiring` 이 `adapters.ml` 에서 참조하는 집합 == {UnavailableMlAnalysis}」를 잠근다).
   따라서 production 의 모든 공고는 사다리에서 `Review(MlUnavailable)` 로 간다 — 3안의 후보 비율도,
   ML-03 신뢰도도 **값이 없다**.
2. g1dec05 레인이 같은 질문을 Phase 1 에서 측정해 **「ML-01 이 닫히기 전에는 메뉴가 production 에서
   `SourceAbsent` 말고 다른 값을 낼 수 없다」** 고 적었고, **「계약 갱신으로 endpoint 자리를 열 것을
   권하지 않는다 — 미가용을 돌려주는 표면이 하나 더 생길 뿐」** 이라고 썼다. ML 없이 서는 후보 비율의
   대체 출처는 **없다**(발주처 밴드 축 0, 공사 floor 앵커 금액 축 불성립).
3. `OPEN-ML-ANALYSIS-WIRING`(실 gRPC 배선)은 **운영자 결정 ④ 개정(2026-09-27)으로 보류**다 — 선행이 6G
   백테스트이고, 그 귀결(`D-6G-7`)에는 「분포 엔진을 운영 경로에 꽂지 않는다」도 들어 있다.

같은 조사가 DEC-04 도 막는다 — `FloorShortfallPolicyData` 의 **출하 정본 표가 저장소에 0**이고(소비자는
conformance 실행기와 kernel test 뿐) 표본 공급 port 도 평가 경로에 없다. 정책 수치를 이 레인이 정하는 것은
금지다(값 결정은 운영자).

**그래서 -a 의 정직한 산출물은 「자격·금액·하한율 근거 + 평가 상세」이고, 가격 세 축(3안·신뢰도·하한 미달
빈도)은 상태와 사유로만 선다.** 이것은 계약의 「근거 절반」 문면과 일치한다. 어긋나는 것은 **-b 의 약속**뿐이다.

### 0.9 팀장 결정 — **전부 수령**(`D-7C2-1`, 2026-10-23)

여섯 다 `scope.md` 「계약 갱신 이력」의 `D-7C2-1` 로 답이 왔다. 아래 표는 **질문과 그때의 권고**이고,
확정된 답은 그 결정 블록이 정본이다. 권고와 결정이 갈린 자리는 **Q1 하나**다 — 권고는 「둘 중 하나를
팀장이 고른다」였고 결정은 **ⓑ(-b 를 열지 않고 -a 단독 종결)** 다. 그에 따라 0.7 을 고쳐 적었고,
`bidPriceMenuNotComputedReason` 은 전환 쌍이 아니라 **값 하나**(`MENU_PATH_NOT_WIRED`)가 됐다.

| # | 질문 | 그때의 권고 |
|---|---|---|
| **Q1** | -b 를 「3안을 채운다」에서 **「메뉴 커널 호출 자리를 배선하고 그 커널이 내는 사유 있는 부재를 응답에 옮긴다」**로 바꿀 것인가? | **바꾼다.** 0.8 의 셋이 그렇지 않으면 -b 가 약속을 못 지킨다. 가치는 남는다 — 커널이 배선되면 ML-01 이 닫히는 날 **표면 변경 없이** 3안이 흐른다. 대안으로 **-b 를 열지 않고 ML-01 뒤로 미루는 것**도 정직하다(g1dec05 권고에 더 가깝다). 그 경우 이 slice 는 -a 단독 PR 이 된다. 둘 중 하나를 팀장이 고른다 |
| **Q2** | 단건 경로에 분석 예산(`candidateLimit`)을 적용하는가? | **적용하지 않는다**(`analysisBudget = null`). 예산은 스캔 비용 통제이고 운영자가 지목한 단건 요청의 허가 조건이 아니다. 차이는 test + 계약 문면 + 알려진 제한에 명시 |
| **Q3** | DEC-04 를 -a 에서 **실제로 재는가**(하한 미달 정책 표 신설 + 개찰 표본 공급 port) ? | **재지 않는다.** 정책 수치는 운영자 결정이고(최소 표본 수·밴드·비교 방향), 표본 공급 port + 개찰 질의는 이 slice 의 「새 조립만」을 넘는다. 별 slice 로 올리고 -a 는 상태·사유만 낸다 |
| **Q4** | in_scope 에 **기존 어댑터 1줄 위임**(`StoredRequirementLicenseGate` → 새 봉투 어댑터 위임)과 **게이트 등재 셋**(`architecture-policy.properties` 의 `app.surface.api.handlers`·`app.http.allowed-packages`·`app.http.denied-types`·주입 표면, `gate-tests.properties` 의 새 test 클래스 전수, `api-type-policy`)을 넣는가? | **넣는다.** acceptance 가 서려면 필요하다(`gateRegistrationGate` 는 모듈의 컴파일된 test 전수 등식을 요구한다 — 새 test 클래스를 등재하지 않으면 `check` 가 붉다). 위임은 거동 불변이고 중복 금지 규율의 요구다 |
| **Q5** | `unjudgedAxes` 축 ID 어휘를 7C④ 와 **같은 문자열**로 고정하는가(`QUAL_08_REGION`·`QUAL_11_CONSTRUCTION_CAPACITY`)? QUAL-10(수행실적)을 더하는가? | **같은 문자열로 고정하고 `QUAL_10_PERFORMANCE` 를 더한다.** 7C④ 는 프로필 입력이 있는 두 축만 들었지만, 이 표면은 **자격 판정 카드**라 판정 없는 축을 빠뜨리면 「면허만 보면 끝」으로 읽힌다. 두 레인이 머지될 때 문자열이 갈리지 않도록 팀장이 어휘를 확정해 주기를 요청한다 |
| **Q6** | 7C③ 머지 전 `inboxRecordState: NOT_RECORDED_YET` 를 계약에 **미리 선언**하는가? | **선언한다.** R-4 공백을 침묵이 아니라 계약 문면으로 둔다(가짜 포트는 만들지 않는다). 7C③ 머지 뒤 값이 늘어나는 것은 가산 |

### 0.9b Phase 2 에서 뒤집힌 전제 — 평탄은 공짜가 아니다 (실측, 미해결 보고)

0.2 는 「평탄 유지」를 **비용 0 의 선택**으로 적었다. 구현 중 실측이 그것을 뒤집었다.

| 사실 | 실측 |
|---|---|
| 계약의 「미달 금지」를 평탄하게 다 담은 응답 data class | **49 필드**(기계 계수). 계약 문서에만 넣고 DTO 에 빠뜨린 3안 칸 둘을 더하면 51 |
| `limit.type.members`(ADR 0007, main source set) | **30**. `sizeGate` 가 Kotlin PSI 로 **주 생성자 `val` 파라미터를 멤버로** 센다(`MeasuredType` 의 「멤버」 정의 그대로) |
| 정책 주석이 적은 저장소 실측 최대 | **21** — 30 은 아무것도 근처에 가지 않는 천장이고 49 는 그것을 넘는다 |

정직하게 깎아도 ~40 이다. 더 깎으면 0.6 (3) 「미달 금지」가 든 근거(기초금액 basis 다섯 칸 · 하한율 출처 ·
드롭 단계·사유 · 자격 provenance)를 잃는다. **평탄 유지와 멤버 30 상한이 동시에 성립하지 않는다** — 명세
모순이므로 구현을 멈추고 보고했다(팀장 결정 대기).

**두 게이트가 서로 다른 대상을 본다**는 것이 해법의 자리다 — 멤버 상한은 **Kotlin 타입**에, 평탄 술어는
**OpenAPI 스키마**에 걸린다. 그래서 JSON·스키마를 평탄하게 두고 Kotlin 타입만 축별로 쪼개 직렬화에서 펴면
둘 다 통과한다(권고안). 그 펴기가 조용히 깨지는 축은 **이미 쓴 키 집합 등식 계약 test**(응답 키 == 스키마
속성 키)가 잡는다. 대안은 평탄 술어를 「깊이 정확히 1」로 넓히는 것(contract-keeper 가 `OPEN-6A3-EVALUATION-DETAIL`
의 해소 경로로 권고해 둔 방식, 게이트 술어 변경 → 표적 재검증)과 operation 둘로 쪼개는 것(권하지 않는다 —
한 동작이 두 왕복이 되고 자격을 따로 읽으면 판정이 두 번 일어나 pin 이 닫은 성질이 다시 열린다).

### 0.9c in_scope ↔ 실제 변경 경로 대조 (기계 산출, 보고 항목)

목록은 `git diff --name-status <base> -- .` + `git ls-files --others --exclude-standard` 로 산출했다
(라운드마다 재산출한다 — 수정 라운드가 만드는 새 파일이 계약 밖에 서는 것이 반복 사각이다).

**계약·`D-7C2-1` Q4 안에 있는 것**: `openapi/**` · `reports/evidence/m7/7c2/**` ·
`config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` ·
기존 자격 어댑터의 위임 한 줄과 그 거동 불변 test · 단건 평가의 새 workflow 포트·어댑터·요청 스코프
팩토리·controller·응답 타입과 그 test 일곱.

**계약 문면에 축어로 없는 것 셋(보고 — 팀장 수용 요청)**:

| 경로 | 왜 필요했나 | 성격 |
|---|---|---|
| `app/.../http/ErrorBody.kt` | 「없는 공고 404」가 계약 산출물이고, **오류 형태를 만드는 자리는 이 파일 하나**라는 규율 때문에 새 code 와 404 핸들러를 여기 더해야 했다. 다른 자리에서 `ErrorBody` 를 지으면 그 규율이 깨진다 | 공유 파일, **추가만**(기존 갈래 무편집) |
| `app/.../wiring/EvaluationWiring.kt` | 새 읽기 포트 둘과 요청 스코프 팩토리의 출하 배선 자리. 「단건 평가 controller」가 서려면 조립이 있어야 한다 | 공유 파일, 추가 + 자격 정책 해소를 두 빈이 공유하도록 private 함수로 **중복 제거** |
| `app/src/test/.../HttpTestSupport.kt` | 새 controller 가 요구하는 빈을 `HttpTestApplication` 이 내야 Spring 기반 test 전체가 뜬다(이중체 둘 + 팩토리 빈) | 공유 test 하네스, 추가만 |

셋 다 7C① 이 같은 자리에서 받은 확장과 같은 종류다(그 slice 의 계약 갱신 이력이 `architecture-policy`·
`gate-tests` 확장을 수용한 선례). **기존 operation·기존 갈래의 문면은 바꾸지 않았다.**

**수용된 셋의 「기존 문면 불변」 증명**(`D-7C2-4`) — **새 test 를 만들지 않는다.** 이 축은 이미 있는
test 가 담당하고, 같은 사실을 재는 test 를 하나 더 두면 둘이 갈릴 자리가 생긴다. 대응은 이렇다:

| 공유 파일 | 바꾼 것 | 불변을 재는 기존 test |
|---|---|---|
| `ErrorBody.kt` | `ErrorCode` 에 code 하나 추가 · 매핑표에 갈래 하나 추가 · 404 핸들러가 예외 **둘**을 받게 | 「`ErrorCode` 상수 집합과 문서 enum 이 양방향으로 같다」가 추가를 양쪽에서 강제한다. 「미매핑 경로의 404 응답도 `ErrorBody` 계약과 일치한다」가 **경로 부재 쪽 거동이 그대로임**을 잰다(핸들러 서명이 넓어졌어도 그 갈래의 코드·문구가 바뀌면 붉어진다). 다른 상태 코드 갈래는 dry-run·편집·검색의 기존 계약 test 가 각자 잰다 |
| `EvaluationWiring.kt` | 빈 둘 추가 · 자격 정책 해소를 private 함수로 묶음 | 자격 게이트 빈의 거동은 `StoredRequirementLicenseGateTest`(실 DB)가 잰다 — 그 안에 이 slice 가 더한 「게이트의 verdict == 판정 봉투의 verdict」가 있고, 해소를 묶은 것이 판정을 바꾸지 않음을 그 등식이 받친다. 출하 조립이 뜨는지는 production 조립 E2E 계열이 잰다 |
| `HttpTestSupport.kt` | 빈 셋 추가(이중체 둘 + 팩토리) | **이 파일을 쓰는 Spring 기반 test 전건**이 그 증명이다 — 기존 빈을 건드렸으면 그 test 들이 뜨지 못한다(추가만이라 기존 배선은 그대로) |

**아직 하지 않은 계약 산출물 둘**(종결 전 반드시 선다):
1. `web/src/api/generated/operator-api.ts` 재생성(W-5 바이트 등식) — `npm ci` 가 필요하고 같은 빌드 락을 쓴다.
2. 추적표·capability map 의 QUAL-02 · DEC-03 · DEC-04 · STR-16 행 근거(산출물 4). 판정 문자는 측정이 서는
   만큼만 — 가격 세 축이 상태·사유로만 서므로 DEC-04 는 ⓒ 를 유지하고 「표면은 섰다, 값 없음」만 적는다.

### 0.10 모듈 배치 (Phase 2 예고)

- `workflow/.../evaluation/`: `NoticeByIdPort` · `LicenseJudgementPort`(읽기 하나씩). 기존 use case·커널 무편집.
- `adapters/.../evaluation/`: `SingleNoticeCandidateSource` · `PinnedLicenseGate` · `RecordingMlAnalysis`.
- `adapters/.../persistence/`(또는 `notice/`): 번호·차수 지정 조회 어댑터(바인딩 매개변수만, `reconstructNotice` 재사용).
- `adapters/.../qualification/`: `StoredRequirementLicenseJudgement` + 기존 게이트의 1줄 위임.
- `app/wiring/`: `BidPriceRequestFactory`·`BidPriceRequestRun`(`EvaluationDryRunFactory` 관례).
- `app/http/`: `BidPriceRequestController` + 응답 DTO(평탄) + 요청 본문 명시 파싱.
- `openapi/`: operation 하나 + `BidPriceRequest` 응답 스키마. 생성 타입 재생성(W-5 바이트 등식).
- 게이트 등재: Q4 목록.
- 모든 Gradle·npm 은 `flock ~/.local/bid-vector-build.lock`, 점검 셋(`pgrep`·`free -m`·`ps`)을 별도 호출로 먼저,
  00:30~03:00 KST 무거운 빌드 금지.
