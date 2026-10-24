# M7/7C④ — 설정 API 체크리스트

계약: `reports/evidence/m7/7c4/scope.md`. base main `bee7c36f`, 브랜치 `m7-7c4/2026-10-22`.

## §0 Phase 1 — 설계 (코드·마이그레이션 0, 팀장 답 대기)

작성 2026-10-22 KST. 아래 인용은 이 worktree(main `bee7c36f`)의 `파일:줄`이고, 다른 레인 것은 worktree 이름을
앞에 붙인다(`7b:` = `bid-vector-v2-m7-7b` `fc115d8b`, `set01:` = `bid-vector-v2-m6-g1set01` `4a52c2fd`).
Gradle·DB 실행 0 — 전부 읽기로 확인한 사실이다.

### 0.1 먼저 드러난 사실 (설계를 가르는 것)

| # | 사실 | 근거 |
|---|---|---|
| F1 | V12 의 세 상태는 **행 존재 + NOT NULL 두 열의 짝**으로 표현된다. 행이 생기는 순간 `licenses_declared` 에 값이 있어야 한다 | `V12__operator_profile.sql:4-8`, `:13-15`, `:22` |
| F2 | `ProfileFacts` 는 프로필 텍스트 합성(임베딩 입력)의 **allow-list 자체**다. 필드를 더하면 그 값이 합성 텍스트로 들어간다 | `ProfilePorts.kt:8-17`, `TextSynthesis.kt:65-95` |
| F3 | V12 `region_terms` 의 소비자는 텍스트 합성 하나뿐이다. 자격 판정은 이 값을 읽지 않는다. QUAL-08 acceptance 는 「텍스트가 아니라 **구조화 지역제한 필드**로 판정」이다 | `TextSynthesis.kt:94`, `capability-map.md:956-975` |
| F4 | 시공능력평가액은 **면허(업종)마다 하나**다. 공고 쪽 원천 `cnstrtnAbltyEvlAmtList` 가 제한그룹·면허별 금액이고, 운영자 쪽은 legacy 에서 연 1회 수기 입력이라 기준 연도가 정의돼 있지 않았다(`OPEN-QUAL-10`) | `capability-map.md:1013-1021` |
| F5 | OpenAPI 평탄 규칙은 **object 배열을 허용하지 않는다**(스칼라 또는 스칼라 배열만). 7B 는 응답 **최상위**를 배열로 둬서 이 규칙을 피했다 | `OpenApiContractTest.kt:473-497`, `7b:BidCaseQueryController.kt` `list` 주석 |
| F6 | 편집 세션은 전략 전용이고 주체 열이 `operator_id TEXT` 다. 계정은 `account.id BIGINT` 다 | `V8__edit_session.sql:26-27`, `V20__account_browser_session.sql:26-27` |
| F7 | 본문 거부 기계 전수는 **POST 매핑만** 센다. 401 전수는 모든 매핑을 센다. CSRF 는 메서드로 구조적으로 요구되지만(GET·HEAD·OPTIONS 외 전부) **전 매핑을 훑는 CSRF test 는 없다** | `ProductionHttpSurfaceTest.kt:179-187`, `BrowserSessionAuthenticationTest.kt:184-200`, `BrowserSessionAuthenticator.kt:37-52` |
| F8 | **CSRF 실패는 403 이 아니라 401 이다**. 실패 사유를 나누지 않는다는 결정(D-6A1-6) 때문이다. scope.md 의 「CSRF 403」 문면과 다르다 | `RequestAuthentication.kt:46-47`, `BrowserSessionAuthenticationTest.kt:117-122` |
| F9 | audit 는 `requestURI` 만 담는다(본문·쿼리 없음). 즉 **경로에 값을 넣으면 그 값이 audit 에 남는다** | `RequestAuditFilter.kt:77`, `V15__api_audit.sql:17-23` |
| F10 | g1set01 커널의 운영자 입력은 `OperatorTradeName.Declared(rawName)` / `NotDeclared` 다. 정규화는 커널 안에서 판정 시점에 한다. 상호 운반 타입은 `toString()` 을 가린다 | `set01:CompanyName.kt:135-150`, `set01:AwardMatchingKernel.kt:11-31`, `set01:CompanyName.kt:12,36` |
| F11 | R-2 의 남은 절반(기존 싱글턴 V9·V12 를 계정 단위로 전환)은 **운영자 결정 2026-10-21 로 별 slice** 가 됐다. 미룬 것은 **기존 표 전환**이고, 새 표를 `account_id` 로 세운다는 R-2 본문은 그대로 이행됐다 | `7b:reports/evidence/m7/7b/checklist.md:358-395`, `:686-693`, `milestone-7.md:88` |
| F12 | 전략과 프로필이 같은 개념을 두 번 저장한다: 업종(V12 `business_types` ↔ 전략 `focusCategories`), 지역어(V12 `region_terms` ↔ 전략 `focusRegionTerms`). UX 는 이 이중 저장을 없애라고 적는다 | `ux-journey-research.md:231-232`, `openapi/bidvector-operator-api.yaml` `StrategyReadResponse` |

### 0.2 (P1) 저장 위치 — **(b) 새 표, `account_id` 소유**. V12 는 건드리지 않는다

**결정**: 새 필드는 V12 에 열로 더하지 않는다. 계정이 소유하는 새 표 둘에 담는다.

```sql
-- 잠정 V22 (P3). 초안이다 — Phase 2 에서 CleanMigration* 관례에 맞춰 다듬는다.
CREATE TABLE account_business_profile (
    account_id BIGINT PRIMARY KEY REFERENCES account (id),
    trade_name TEXT CHECK (trade_name IS NULL OR (trade_name <> '' AND char_length(trade_name) <= :상한)),
    headquarters_region TEXT CHECK (headquarters_region IS NULL OR headquarters_region IN (:시도 17)),
    nara_registration TEXT NOT NULL CHECK (nara_registration IN ('UNANSWERED','REGISTERED','NOT_REGISTERED')),
    nara_registered_fields TEXT[] NOT NULL,
    business_certificate_expires_on DATE,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (nara_registration = 'REGISTERED' OR (nara_registered_fields = '{}' AND business_certificate_expires_on IS NULL))
);
CREATE TABLE account_construction_capacity (
    account_id BIGINT NOT NULL REFERENCES account (id),
    license_name TEXT NOT NULL CHECK (license_name <> ''),
    amount_won BIGINT NOT NULL CHECK (amount_won >= 0),
    assessment_year SMALLINT NOT NULL CHECK (assessment_year BETWEEN 2000 AND 2100),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, license_name)
);
-- GRANT: business_profile = SELECT·INSERT·UPDATE(열 단위, account_id 제외).
--        construction_capacity = SELECT·INSERT·UPDATE(amount_won, assessment_year, updated_at)·DELETE.
```

체크리스트 진행 상태는 **저장하지 않는다**. 항목 완료는 매 요청마다 위 표·V12·V9 에서 판정한다(0.8). 저장하면 원천과
어긋날 수 있는 둘째 사본이 생긴다.

**근거**

1. **(a) 를 고르면 면허 세 상태가 납작해진다(F1).** 상호만 저장해도 V12 행이 생겨야 하고, 그 행은 `licenses_declared`
   에 값을 요구한다. 면허를 아직 안 정한 운영자의 상태가 「미설정」에서 `NotDeclared` 나 `Declared(빈 목록)` 으로 바뀐다
   — 위협 모델이 막으라는 바로 그 납작화다. 피하려면 V12 의 NOT NULL 열을 nullable 로 바꿔야 하는데, 그것은 V12 의
   세 상태 정의 자체를 고치는 일이다.
2. **새 필드가 텍스트 합성에 들어가면 안 된다(F2).** 상호·시평액·인증서 만료일이 `ProfileFacts` 에 들어가면 임베딩
   입력이 된다. 다른 타입·다른 port 에 두면 이 경로가 구조적으로 없다. (a) 에서도 타입은 나눌 수 있지만, 같은 행을
   두 port 가 반씩 쓰는 모양이 된다.
3. **R-2 와 충돌하지 않는다(F11).** 7B §9 가 미룬 것은 「기존 싱글턴의 계정 단위 전환」이다. 이 slice 가 만드는 것은
   **새 사용자 소유 데이터**이고, R-2 본문(`milestone-7.md:88`)은 그런 데이터에 처음부터 `account_id` 를 두라고 한다.
   (a) 는 새 데이터를 싱글턴에 넣어 R-2 위반 범위를 넓히는 쪽이다.
4. **별 slice 가 덜 어려워진다.** (b) 의 새 표는 이미 목표 모양이라 별 slice 는 V12 하나만 전환하면 된다. (a) 면
   전환할 열이 늘고, 이 slice 가 만드는 새 소비자(프로필 HTTP)까지 같이 고쳐야 한다. 7C⑤ 대사도 7B `bid_case.account_id`
   와 상호를 계정으로 맞대므로 (b) 에서 자연스럽다.

**대가 (정직하게)**: 프로필이 두 키 체계에 나뉜다 — V12(싱글턴: 업종·면허·지역어)와 새 표(계정: 나머지).
조회 응답은 둘을 합친다. 이 비대칭은 숨기지 않는다:

- V12 쪽 port 는 **`AccountId` 를 받지 않는다**(지금처럼 싱글턴). 받는 척하고 무시하면 「이미 계정 단위다」라는 거짓
  신호가 된다(7B §9 의 「죽은 열」 논리와 같다). 별 slice 가 그 port 에 `AccountId` 를 더하면 컴파일 오류가 고칠 자리를
  전부 가리킨다.
- 새 표 쪽 port 는 모든 메서드의 첫 인자가 `AccountId` 다(7B `BidCaseStorePort` 관례).
- 계정이 둘이 되면 V12 필드가 계정 사이에 공유된다. 이것은 V9 전략과 같은 기존 노출이고 별 slice 의 범위다. 오늘 계정은
  하나다(R-2 「계정 하나」).

**함께 정하는 필드 모양**

- **상호(SET-01)**: 운영자가 입력한 원문 그대로 저장한다. 앞뒤 공백도 자르지 않는다 — 자르는 것도 정규화다(F10, 이중
  정규화 금지). 공백뿐인 입력은 400 으로 거부한다(빈 값으로 저장하지 않는다). `NULL` = `OperatorTradeName.NotDeclared` 로
  그대로 대응한다. 운반 타입은 `toString()` 을 가린다(F10 관례).
- **지역(QUAL-08)**: V12 `region_terms` 를 재사용하지 **않고**, 본사 소재지 **시·도 하나**를 닫힌 어휘 17 로 받는다
  (선언 표). 근거 F3 — 자유 텍스트 지역어를 QUAL-08 입력으로 쓰면 나중의 판정이 acceptance 가 금지한 텍스트 매칭으로
  기울어진다. `region_terms` 는 V12 편집 경로에서 지금 그대로 편집된다.
- **실적·시평액(QUAL-11)**: **면허별 시평액 + 기준 연도**만 받는다(F4). 금액은 원 단위 `Long`, 기준 연도는 필수다(빈
  값으로 두지 않는다 — `OPEN-QUAL-10` 의 운영자 쪽 절반을 입력 형태로 닫는다. 공고 쪽 절반은 열린 채 둔다). 이 값은
  가격이 아니므로 공유 커널 `Basis`/`VatTreatment` 를 끌어오지 않는다 — 시평액에 맞는 값이 그 어휘에 없다
  (`shared-kernel/.../Basis.kt:11-27`). 「실적」(QUAL-10 수행실적)은 capability 가 `근거 부족` 이라 받지 않는다 → **확인 요청 Q2**.
- **나라장터 준비**: 등록 여부 3값(미응답·등록·미등록), 등록분야(닫힌 어휘 배열), **사업자용** 인증서 만료일 하나.
  근거: 투찰 서명에 쓰는 것이 사업자용이다(`ux-journey-research.md:57`). 개인용(로그인) 인증서 만료일은 받지 않는다 →
  **Q3**.

### 0.3 (P2) 인증 축 — **세션 전용**. 운영자 자격 헤더 경로는 받지 않는다

- 새 경로 전부(`/api/profile/**`, `/api/onboarding-checklist`)를 7B 의 계정 전용 선언(`7b:AccountScopedRoutes.kt:19-27`)에
  뿌리로 더한다. 계정 소유 표(P1 b)를 읽으려면 `account.id` 가 필요하고, 자격 헤더 경로의 주체 `operator` 에는 계정
  행이 없다(`BrowserSessionAuthenticator.kt:54-58`, `7b:BidCaseRequests.kt:142-155`).
- V12 쪽(업종·면허·지역어) 편집도 **같이 세션 전용**으로 둔다. 자격 헤더로도 열어 두면, 별 slice 가 V12 를 계정 단위로
  전환할 때 그 경로를 **닫아야** 한다 — 이미 쓰이는 경로를 닫는 것은 회귀다. 처음부터 세션 전용이면 닫을 것이 없다.
  지금 프로필 HTTP 소비자는 0 이라 자격 헤더 쪽에서 잃는 것도 없다(`app/src/main` 에 프로필 controller 없음).
- **main 기준으로 설계하고, 7B 머지 뒤 rebase 에서 맞춘다.** 단 **실행상 걸림돌이 있다**: main 의 `OpenApiContractTest`
  는 인증 operation 전부에 `operatorCredential` 대안을 요구하고, `ProductionHttpSurfaceTest` 는 자격 헤더 하나로 POST
  전수를 두드린다(`7b:checklist.md:565-622` 의 「전」 단언). 세션 전용 operation 을 main 위에 얹으면 **이 둘이 붉다**.
  7B 가 그 두 test 를 고친 판이 들어와야 초록이 된다 → **요청 R1**.
- 계정 꺼내기 헬퍼: 7B 는 `BidCaseRouting.accountOf`(`7b:BidCaseRequests.kt:149`)에 두었다. 이 slice 가 같은 것을 또
  쓰면 중복 헬퍼 게이트에 걸린다. rebase 때 그 함수를 입찰함 밖 공용 자리로 옮기고 두 slice 가 함께 쓴다(7B 코드 이동
  한 건이다 — 팀장 확인 필요, R1 과 같이).

### 0.4 (P3) 마이그레이션 번호 — 잠정 **V22**

main 최대 V20(`ls adapters/src/main/resources/db/migration`), 7B 가 V21 보유(`7b:...migration/V21__bid_case.sql`),
g1c03·g1dec05·g1set01 은 V20 에서 멈춤(마이그레이션 없음, 2026-10-22 15:2x KST `ls` 실측). 잠정 V22 로 쓰고, PR 직전에
D-6A1-12 정의(「PR 시점 main 최대보다 크고 선점 통보 번호를 피한다」)대로 다시 잰다. 7B 보다 먼저 머지되지 않는다는
전제를 그대로 받는다. 마이그레이션은 **하나**다(새 표 둘, V12 무변경).

### 0.5 (P4) 개인정보·민감 사업정보

- **만들지 않는 슬롯**: 사업자번호·대표자명·인증서 원문/일련번호/발급기관. 표·DTO·OpenAPI 어디에도 자리가 없다. 요구표
  :28 은 사업자번호를 「자기 신고 입력만」으로 적었지만, scope 가 슬롯 금지를 명시했으므로 scope 를 따른다(요구표 그 행은
  `─` 그대로 둔다).
- **audit**: 본문을 담지 않는다(F9). 그래서 값은 **경로에 넣지 않는다** — 시평액 행도 면허명을 본문으로 받는다
  (`/construction-capacities/{licenseName}` 형태를 쓰지 않는다).
- **로그·오류 응답**: 검증 실패 `ErrorBody` 는 입력값을 되울리지 않는다(필드 이름과 코드만). 상호·시평액 운반 타입은
  `toString()` 을 가린다.
- **evidence**: 실값 0. test fixture 는 합성 값만 쓰고, `leakPatternGate` 실행 확인을 완료 조건에 둔다.
- **보존 분류**: 새 표 둘 모두 `RetentionTableRegistry` 에 **등재한다**(스키마와 집합 등식 test 가 요구한다).
  `SINGLETON_SETTING` 은 그 사유의 KDoc 문면(「싱글턴 설정 행(id=1)」)대로 싱글턴 전용이어서 맞지 않는다. 새 사유
  `ACCOUNT_SETTING`(계정의 현재 설정값 — 수명은 시간이 아니라 계정이 정하고, 파기 경로는 R-2 `후속` 의 탈퇴 파기가 연다,
  같은 레지스트리의 `ACCOUNT_IDENTITY` 서술과 같은 축) 하나를 더한다 → **Q4**.
  (**좌표 제거 2026-10-24**: 이 두 문장은 `RetentionTableRegistry` 의 `파일:줄` 을 가리켰다. 이 slice 가 그 파일을 편집하므로
  좌표가 낡는다 — 문면 인용으로 바꿨다.)
- 상호는 공고 쪽에서 `COMPANY_IDENTITY_CONCEPTS`(PII 축)다(`FieldConceptPiiAxis.kt:19-27`). 운영자 자신의 상호는
  `FieldConcept` 가 아니어서 그 게이트에 들지 않는다. 그래서 위 가림·경로 규칙을 이 slice 가 직접 test 로 잠근다.

### 0.6 (P5) 쓰기 경로 — **단건 POST + CSRF**, 절 단위 교체. 편집 세션은 쓰지 않는다

| operation(잠정) | 메서드·경로 | 의미 |
|---|---|---|
| `readProfile` | GET `/api/profile` | V12 + 새 표 합본, 평탄. `unjudgedAxes` 포함(0.7) |
| `replaceQualificationFacts` | POST `/api/profile/qualification-facts` | 업종·면허(3상태 중 `NOT_DECLARED`/`DECLARED`)·지역어. V12 기존 `save` 를 그대로 쓴다 |
| `replaceBusinessDetails` | POST `/api/profile/business-details` | 상호·소재지·나라장터 준비 셋 |
| `listConstructionCapacities` | GET `/api/profile/construction-capacities` | 최상위 배열(F5 — 7B 관례) |
| `upsertConstructionCapacity` | POST `/api/profile/construction-capacities` | 면허명·금액·기준연도 한 행 |
| `removeConstructionCapacity` | POST `/api/profile/construction-capacities/removal` | 면허명 한 행 삭제(값을 경로에 넣지 않기 위해 본문으로) |
| `readOnboardingChecklist` | GET `/api/onboarding-checklist` | 0.8 |

**근거**

1. **편집 세션 재사용은 R-2 식별자 결정을 끌어온다(F6).** 세션의 주체 열이 `TEXT operator_id` 라, 계정 소유 프로필을
   그 세션으로 편집하면 「`OperatorId` 가 `account.id` 인가」를 이 slice 가 답해야 한다. 그것이 7B 가 별 slice 로 미룬
   바로 그 질문이다. 또 세션의 필드 어휘·초안·개정 이력은 전략 전용이다.
2. **POST 는 기존 기계 전수 안에 있다(F7).** 본문 거부 전수가 POST 만 센다. PUT/PATCH 를 쓰면 새 쓰기 경로가 그 전수
   밖에 서고, 그 전수를 PUT 으로 넓히려면 7B 가 지금 고치는 공유 test 를 또 건드려야 한다. 저장소의 쓰기도 지금 POST·
   DELETE 뿐이다.
3. **절 단위 교체라 부분 갱신이 다른 필드를 못 지운다.** 각 POST 는 자기 절 전체를 교체하고 다른 절에 닿는 SQL 이 없다.
   merge-patch(`null` = 지우기)를 쓰지 않는 이유: 면허의 「미설정」을 `null` 로 표현하게 되면 세 상태가 다시 납작해진다.
   면허 상태는 `licenseState` 문자열로 명시하고, 요청에서 `UNSET` 은 400 이다(되돌리는 경로는 없다 — V12 에 DELETE 권한도
   없다, `V12__operator_profile.sql:27`).
4. **알려진 제한**: 동시성 토큰 없음(마지막 쓰기가 이긴다). 운영자 1인·탭 여럿 정도라 받아들인다. 필요하면 `updatedAt`
   전제조건을 더하는 것은 쉽다 → Q5.

CSRF 는 구조적으로 덮인다(POST 는 면제 집합 밖, F7). 다만 **전 매핑 CSRF 전수 test 가 없으므로** 새 쓰기 경로마다
「CSRF 없음 → 401, 세션 살아 있음」을 표적 test 로 둔다. 401 은 기존 전수(`BrowserSessionAuthenticationTest.kt:184`)에
자동 포함된다. scope 의 「CSRF 403」 은 실제 동작(401, F8)으로 적는다 → Q6.

### 0.7 「아직 판정하지 않음」 표시 (QUAL-08 · QUAL-11)

- `readProfile` 응답에 `unjudgedAxes: ["QUAL_08_REGION", "QUAL_11_CONSTRUCTION_CAPACITY"]`(스칼라 배열 — 평탄). 값은 선언
  표 하나에서 온다.
- test 로 잠그는 것: (1) 입력이 있든 없든 두 축이 그 배열에 있다. (2) 자격 판정 경로(`qualification`·`OpportunityAnalysis`)
  가 새 표의 port 를 **참조하지 않는다** — 아키텍처 test 로 재서, 누가 몰래 판정에 쓰기 시작하면 붉어지게 한다. 판정이
  생기는 날 그 slice 가 이 표와 test 를 함께 고쳐야 한다.
- 추적표·capability map 의 QUAL-08·QUAL-11 행은 **ⓒ 유지**. 「입력 칸은 섰다, 판정 없음」만 적는다.

### 0.8 시작하기 체크리스트 — 서버 판정, 선언 표

항목은 `(항목 ID, 판정 함수)` 선언 표 하나다. 판정 함수는 스냅샷(V12 + 새 표 + V9 전략 + 오늘 날짜 KST) → `Boolean` 순수
함수다. 응답은 평탄하게 `completedItems[]`·`pendingItems[]`(둘의 합 = 전 항목, 서로소 — test 로 잠근다).

| 항목(잠정) | 완료 조건 |
|---|---|
| `TRADE_NAME` | 상호가 있다 |
| `NARA_READINESS` | 등록 = `REGISTERED` 이고 등록분야 ≥ 1 이고 사업자용 인증서 만료일 ≥ 오늘(KST) |
| `BUSINESS_TYPES` | V12 행이 있고 업종 ≥ 1 |
| `LICENSES` | 면허 상태 ≠ 미설정(`NOT_DECLARED` 도 완료 — 운영자가 명시적으로 고른 상태다) |
| `HEADQUARTERS_REGION` | 소재지가 있다(입력만 — 판정 없음) |
| `CONSTRUCTION_CAPACITY` | 시평액 ≥ 1 행. **업종에 공사가 없으면 이 항목을 내지 않는다**(적용 여부도 표의 열) |
| `BUDGET_RANGE` | 전략 최소·최대 금액 중 하나 이상 설정 |
| `WATCH_CONDITIONS` | 전략 관심 업종 ≥ 1 |

넣지 않은 것: 사업자번호(슬롯 금지), 알림 채널(R-4·`OPEN-STR-12`), 첫 공고 미리보기(7C① 검색 소관). 항목 목록 자체 → **Q1**.
`NARA_READINESS` 는 시계가 들어가므로 시계를 주입한다(만료 경계 test 가능).

### 0.9 전략 설정 — 기존 표면으로 충분한가 (산출물 3)

- 설정 화면의 「감시 조건·임계치·후보 상한·활성 투찰 상한」은 `readStrategy` + 편집 세션 여섯으로 **전부 덮인다**:
  `candidateLimit`·`maxActiveBids` 가 조회 응답과 편집 필드 어휘에 있다(`openapi/bidvector-operator-api.yaml:874-878`,
  `:947-960`). 세션 경로로도 이미 통한다(`BrowserSessionAuthenticationTest.kt:278`). **이 slice 는 전략 쪽에 아무것도 더하지 않는다.**
- **STR-06 은 이 slice 범위 밖으로 둔다.** 「신설 필요」의 내용은 편집이 아니라 **후보 표시 표면**과 「표시 건수 ↔ 분석
  예산 독립」 측정이다(`capability-map.md:528-538`, `reports/evidence/m6/6e1/acceptance-trace.md:115`). 7A 조정안은 그것을
  **7C①** 에 배정했다(`reports/evidence/m7/7a/m6-condition3-adjustment-proposal.md:57`, `:112`). 그런데 7C① evidence 에
  STR-06 언급이 없다(`grep -rn STR-06 reports/evidence/m7/7c1` 0건) → **G2 의 STR-06 이 홀더 없이 떠 있을 수 있다. 팀장
  확인 필요(보고 B1).**

### 0.10 (P6) 범위 밖

- QUAL-08·QUAL-11 **판정 로직**(입력만 선다, 0.7).
- 상호 **정규화 커널**(g1set01 소관 — 코드를 끌어오지 않는다. 저장 형태만 맞물린다: 원문 + `NULL` = `NotDeclared`, F10).
- 화면(7E).
- R-2 남은 절반: V12·V9 계정 단위 전환, 편집 세션 주체 식별자, 자격 헤더의 계정 묶기.
- 전략·프로필 이중 저장 해소(F12) — 이 slice 는 V12 편집을 **지금 의미 그대로** 노출할 뿐이다. 보고만 한다(B2).
- STR-06(0.9).
- QUAL-10 수행실적, 사업자번호·국세청 확인, 알림 채널.
- 나라장터 자기 신고값의 진위 확인.

### 0.11 모듈 배치 (Phase 2 예고)

- `workflow/.../profile/`: use case(조회·절 교체·시평액 upsert/remove·체크리스트), 새 port(`AccountId` 첫 인자), 체크리스트
  선언 표와 순수 판정, 판정 대기 축 선언 표.
- `adapters/.../profile/`: 새 표 JDBC 어댑터(바인딩 매개변수만). 기존 `JdbcOperatorProfileRepository` 는 무변경.
- `app/http`: controller 둘(조회 / 쓰기 — 7B 처럼 GET 과 쓰기를 가른다), `AccountScopedRoutes` 에 뿌리 추가.
- 게이트 등재: `gate-tests.properties`, `architecture-policy.properties` 의 `app.http.allowed-packages`·`denied-types`
  (controller 는 use case 하나만 받는다 — 7B 관례), `RetentionTableRegistry`, `CleanMigration*Test`.
- 무거운 빌드는 `flock ~/.local/bid-vector-build.lock` 로 직렬, 확인 셋을 별도 호출로 먼저 돌리고, 00:30~03:00 KST 는 피한다.

### 0.12 팀장에게 요청·확인

**요청**

- **R1 — Phase 2 의 base.** 세션 전용 operation 은 main 의 `OpenApiContractTest`·`ProductionHttpSurfaceTest` 에서 붉다
  (0.3). 셋 중 하나를 정해 달라: (가) 7B 머지 뒤 그 main 에서 Phase 2 시작, (나) 지금 7B 브랜치 끝(`fc115d8b`) 위에 쌓고
  7B 머지 뒤 main 으로 rebase, (다) main 에서 쓰되 그 두 test 는 7B 머지 전까지 붉은 채 둔다(비권장 — `check` 가 초록이
  아니다). **권고: (나).** 그리고 `accountOf` 를 공용 자리로 옮기는 7B 코드 이동 한 건을 허락해 달라.

**확인 질문**

- **Q1** 체크리스트 항목 표(0.8)가 맞는가. 특히 `CONSTRUCTION_CAPACITY` 를 공사 업종일 때만 내는 것.
- **Q2** 「실적·시평액」을 **면허별 시평액 + 기준연도**로 좁히고 QUAL-10 수행실적은 받지 않는 것.
- **Q3** 인증서 만료일은 **사업자용 하나**만.
- **Q4** 보존 사유 `ACCOUNT_SETTING` 신설.
- **Q5** 동시성 토큰 없이 시작(마지막 쓰기 이김).
- **Q6** scope 의 「CSRF 403」을 실제 동작 「CSRF 실패 401(사유 미구분)」로 고쳐 읽어도 되는가.

**보고**

- **B1** STR-06 은 7A 조정안상 7C① 홀더인데 7C① evidence 에 흔적이 없다(0.9).
- **B2** 전략과 프로필이 업종·지역어를 이중 저장한다(F12). UX 가 없애라고 한 구조다. 이 slice 는 고치지 않는다 — 별
  slice 또는 R-2 별 slice 와 묶을 후보다.

## §1 팀장 결정 D-7C4-1 (2026-10-22) — 수령

- §0 설계 전부 승인: P1 (b) 새 표 둘 · V12 무변경 · 체크리스트 무저장, P2 세션 전용, P3 잠정 V22, P4, P5 POST + CSRF 절 단위
  교체, `unjudgedAxes` + 아키텍처 test.
- **R1 — (나)를 고쳐 두 단계로**: 2a = main `bee7c36f` 위에서 공유 전수 test 에 닿지 않는 층(도메인 타입 · port · 체크리스트
  판정 표 · V22 · persistence 어댑터와 그 test · CleanMigration* 등재). HTTP · OpenAPI · `AccountScopedRoutes` 는 손대지 않는다.
  2b = 7B 동결 SHA(팀장 통보) 위로 rebase 한 뒤 HTTP · OpenAPI · 등재 · 생성 타입. 7B 머지 뒤 main 으로 다시 rebase.
  `BidCaseRouting.accountOf` 공용화는 2b 의 별 커밋(refactor, 동작 무변경)으로 하고 rollback 목록에 넣는다.
- Q1 8항목 승인(공사일 때만 시평액) · Q2 승인(면허별 시평액 + 기준연도, QUAL-10 수행실적 범위 밖 — 추적표 QUAL-11 행에 기록) ·
  Q3 승인 · Q4 `ACCOUNT_SETTING` 승인(보존 기간 값을 지어내지 않고 계정 수명과 같다는 근거를 적는다) · Q5 승인(알려진 제한에
  적는다) · Q6 정정 수용(401 로 적고 scope.md 문면을 이 레인이 고친다).
- B1 STR-06 은 팀장이 따로 추적, B2 이중 저장은 알려진 제한 + 후속 후보로만.

### 해석 기록 — 「공사 업종일 때만」을 무엇으로 재는가

V12 업종(`business_types`)은 자유 텍스트 `CategoryCode` 라 「공사인가」를 기계로 가를 수 없다
(`strategy/.../Text.kt:4-6` — 정규화 없는 평범한 값, `JdbcOperatorProfileRepositoryTest` 의
`OPEN-6F6-CATEGORY-CODE-NORMALIZATION`). 닫힌 어휘로 「공사로 입찰한다」를 신고하는 자리는 **나라장터 등록분야**
(`NaraRegistrationField.CONSTRUCTION`)뿐이다. 그래서 `CONSTRUCTION_CAPACITY` 항목의 적용 조건을 「등록분야에 공사가 있다」로
구현했다. 결과: 나라장터 준비를 아직 답하지 않은 계정에는 시평액 항목이 나오지 않는다(그 계정은 `NARA_READINESS` 가 미완으로
남는다). 팀장 확인 필요 항목으로 보고에 올린다.

**→ 팀장 결정 D-7C4-2(2026-10-22): 숨기지 않는다 — 세 상태.** 숨기면 「해당 없음」과 「아직 모름」이 같아진다(면허
세 상태를 지킨 것과 같은 이유). 구현(`OnboardingChecklist.kt`):

| 나라장터 준비 | 시평액 항목 | 응답 자리 |
|---|---|---|
| 등록 + 분야에 공사 있음 | `APPLIES` — 완료 판정 대상 | `completed` / `pending` |
| 등록 + 분야 ≥ 1, 공사 없음 | `NOT_APPLICABLE` — 나오지 않음 | 어디에도 없음 |
| 미응답 · 미등록 · 등록 + 분야 빈 목록 | `AWAITING_PREREQUISITE` — 「선행: 나라장터 준비」 | `awaiting`(완료로도 미완으로도 세지 않는다) |

선행 항목은 **적용 상태 타입이 함께 든다**(`AwaitingPrerequisite(prerequisite)`) — 선행을 최상위 선언 표로 따로 두면 두 자리가
어긋날 수 있고, 그 표가 열거 상수를 읽는 최상위 값이 되어 **초기화 순환**이 된다(§3.1 실측). **미등록을 「분야 미답」으로 둔 이유**:
미등록 계정은 등록분야를 고른 적이 없다 — 「해당 없음」으로 접으면 등록 전 계정이 공사로 입찰할지가 사라진다.

## §2 Phase 2a — 구현 (main `bee7c36f` 위)

### 알려진 제한 (2a 시점)

1. **동시 편집 시 마지막 쓰기 승리 — 단일 운영자 전제**(D-7C4-1 Q5). 절 교체 · 시평액 upsert 에 동시성 토큰이 없다.
2. **전략과 프로필의 이중 저장**(B2) — 업종(V12 `business_types` ↔ 전략 `focusCategories`) · 지역어(V12 `region_terms` ↔ 전략
   `focusRegionTerms`). UX §6.2 A 가 없애라고 한 구조이고 이 slice 는 고치지 않는다 — 후속 후보(R-2 별 slice 와 묶을 수 있다).
3. **V12 는 아직 싱글턴이다** — 계정이 둘이 되면 업종 · 면허 · 지역어가 공유된다(V9 전략과 같은 기존 노출, R-2 남은 절반).
4. **입력만 선 축 둘**(QUAL-08 소재지 · QUAL-11 시평액) — 판정 없음. 판정 대기 표에는 축이 **셋**이다: QUAL-10(수행실적)은
   입력도 판정도 없이 들어간다(§3.2). `UnjudgedQualificationAxis` 와 `ProfileJudgementIsolationTest` 가 그 사실을 잠근다.
5. **나라장터 자기 신고값의 진위는 확인하지 않는다**(위협 모델 「방어하지 않음」).
6. **HTTP 표면 없음(2a)** — 2b 에서 선다. 그때까지 production 의 어느 경로도 V22 표에 쓰지 않는다.

## §3 레인 재개 (2026-10-24)

### 3.1 남아 있던 미커밋 변경의 판정 — D-7C4-2 구현이었고, **깨져 있었다**

앞 세션이 종료되며 커밋되지 않은 변경 셋(이 파일 · `OnboardingChecklist.kt` · `OnboardingChecklistTest.kt`)이 남았다. 전문을
계약과 대조한 결과 **D-7C4-2(시평액 항목 세 상태)의 구현**이었고 in_scope 밖 파일에 닿지 않았다 — 버리지 않고 이어받았다
(원본은 레인 scratchpad 의 패치 파일로 백업).

이어받기 전에 표적 test 를 돌린 것이 값을 했다. **`OnboardingChecklistTest` 9 중 7 이 `IllegalStateException` 으로 실패했다**
(「선행 항목이 선언되지 않은 「아직 모름」 항목: CONSTRUCTION_CAPACITY」). 원인은 선행 표를 **최상위 `val`** 로 둔 것이다:

- 그 표의 초기화가 열거 상수(`OnboardingItem.CONSTRUCTION_CAPACITY`)를 읽고,
- 열거 항목의 초기화가 같은 파일의 최상위 값(`ALWAYS`)을 읽는다.

한 스레드에서 서로의 `<clinit>` 에 재진입하므로 **먼저 시작한 쪽이 상대의 미초기화 상태를 본다** — 열거가 먼저면 표의 키가
`null` 이 되고(실측된 쪽), 표가 먼저면 열거 항목의 적용 함수가 `null` 이 된다. 어느 쪽이든 판정 전체가 깨진다.

**고친 방식**: 선행을 표에서 빼고 **적용 상태 타입이 들게** 했다(`ItemApplicability.AwaitingPrerequisite(prerequisite)`, sealed).
선행이 상태와 한 자리에 있으므로 (1) 두 자리가 어긋나 「선행 없는 대기」가 생길 수 없고 (2) 열거 상수를 읽는 최상위 값이 없어
순환이 사라진다. 판정 함수의 `when` 은 소진형이라 상태를 더하면 컴파일이 막는다(새 상태가 조용히 어느 목록에도 들지 않는 일이
없다). test 는 「대기의 선행은 그 스냅샷에서 걸리는 항목이다」로 바꿨다 — 죽은 선행·순환을 재고, 대기가 한 번도 관측되지
않으면 단언이 빈 모집단 위에 서므로 그것도 함께 잰다.

### 3.2 미판정 축 어휘에 `QUAL_10_PERFORMANCE` (팀장 전달, D-7C2-1 Q5)

7C ② 가 같은 이름의 열거를 `workflow.evaluation` 에 두고 **두 표면의 단일 출처**로 삼기로 했다. 이 레인은 ID 셋을 바이트 동일로
맞춘다(`QUAL_08_REGION` · `QUAL_10_PERFORMANCE` · `QUAL_11_CONSTRUCTION_CAPACITY`, 이 순서 — 7C ② worktree 의 정의와 대조).
QUAL-10(수행실적)은 이 slice 가 **입력을 받지 않는다**(D-7C4-1 Q2). 그래도 드는 이유는 이 표가 「입력이 선 축」이 아니라
**「판정이 없는 축」**이기 때문이다 — 입력도 판정도 없는 축을 빼면 운영자에게 「면허만 보면 끝」으로 읽힌다. 축 ID 는 wire 값
이므로 이름을 직접 잠갔다(2b 의 응답 계약 test 가 서기 전까지 이것이 유일한 잠금이다).

**머지 방향은 한쪽뿐이다.** 뒤에 머지되는 쪽이 자기 리터럴을 지우고 상대를 참조하는데, 가능한 방향은 `profile` →
`evaluation` 이다(그 방향은 이미 `ProfileFacts` 참조로 서 있다). 반대로 `evaluation` 이 `profile` 을 참조하면
`ProfileJudgementIsolationTest` 의 격리 단언이 붉어진다 — 그 test 가 막는 것이 바로 판정 경로의 이 패키지 참조다.
어휘를 capability map · 추적표에 어떻게 적을지는 단일 출처를 쥔 7C ② 소관으로 둔다.

### 3.3 base 재고정과 병합으로 해소된 전제 둘

main 이 `fc15975c` 까지 앞섰다(7C①b · g1set01 · g1c03 · 7C⑤p · CVE allowlist 둘). 레인 브랜치에 병합했고 **충돌 0** 이다
(`gate-tests.properties` 와 M6 추적표는 양쪽이 고쳤지만 서로 다른 절·행이다). base 와 rollback 기준을 `fc15975c` 로 고쳤다.

- **g1set01 머지로 §0.2 F10 의 「미머지」 전제가 해소됐다.** 상호 커널의 운영자 입력은 `OperatorTradeName.Declared(원문)` ·
  `NotDeclared` 이고 정규화(공백 제거 · 법인 토큰 절삭 · ASCII 소문자화)는 **대사 시점**에 커널 안에서 돈다. 이 slice 의 저장
  형태(원문 무편집 · `NULL` = 미신고)가 그 전제와 맞는다 — 이중 정규화가 생기는 자리가 없다. 커널 코드는 끌어오지 않았다.
- **B1 해소**: STR-06 은 main 의 7C①b 가 제안 목록 operation 으로 홀더를 세웠다. 분류는 여전히 ⓒ 이고 남은 결합(표시 상한 ↔
  분석 예산)은 팀장 등재 예정 별 slice 다. 이 slice 는 전략 쪽에 아무것도 더하지 않는다(§0.9 그대로).

### 3.4 2b 로 넘기는 결정 — `awaiting` 의 응답 형태

D-7C4-2 의 `awaiting` 은 **(항목, 선행 항목) 짝**이다. 응답 평탄 규칙은 object 배열을 허용하지 않으므로(§0.1 F5) 2b 에서 형태를
정해야 한다. 후보 둘: (가) 스칼라 배열 둘(`awaitingItems` · 같은 순서의 `awaitingPrerequisites`) — 짝이 자리로만 묶여 약하다,
(나) 7C ② 의 평탄 술어 확장(깊이 1 · 대상 속성 전부 스칼라)이 머지되면 그 허용 안에서 짝 배열. **지금 고르지 않는다** — 2a 는
HTTP 표면이 없고(알려진 제한 6), 고르면 7C ② 의 게이트 술어 결정을 이 레인이 선점한다.
