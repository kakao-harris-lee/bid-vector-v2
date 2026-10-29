# M7/7C④ — 설정 API 체크리스트

계약: `reports/evidence/m7/7c4/scope.md`. base main **`fc15975c`**(2026-10-24 재고정 — 착수 base 는 `bee7c36f` 였다, §3.3),
브랜치 `m7-7c4/2026-10-22`.

## §0 Phase 1 — 설계 (코드·마이그레이션 0, 팀장 답 대기)

작성 2026-10-22 KST. 아래 인용은 이 worktree(main `bee7c36f`)의 `파일:줄`이고, 다른 레인 것은 worktree 이름을
앞에 붙인다(`7b:` = `bid-vector-v2-m7-7b` `fc115d8b`, `set01:` = `bid-vector-v2-m6-g1set01` `4a52c2fd`).
Gradle·DB 실행 0 — 전부 읽기로 확인한 사실이다.

### 0.1 먼저 드러난 사실 (설계를 가르는 것)

| # | 사실 | 근거 |
|---|---|---|
| F1 | V12 의 세 상태는 **행 존재 + NOT NULL 두 열의 짝**으로 표현된다. 행이 생기는 순간 `licenses_declared` 에 값이 있어야 한다 | `V12__operator_profile.sql:4-8`, `:13-15`, `:22` |
| F2 | `ProfileFacts` 는 프로필 텍스트 합성(임베딩 입력)의 **allow-list 자체**다. 필드를 더하면 그 값이 합성 텍스트로 들어간다 | `ProfilePorts.kt:8-17`, `TextSynthesis.kt:65-95` |
| F3 | V12 `region_terms` 의 소비자는 텍스트 합성 하나뿐이다. 자격 판정은 이 값을 읽지 않는다. QUAL-08 acceptance 는 「텍스트가 아니라 **구조화 지역제한 필드**로 판정」이다 | `TextSynthesis.kt:94`, capability map 의 QUAL-08 절 |
| F4 | 시공능력평가액은 **면허(업종)마다 하나**다. 공고 쪽 원천 `cnstrtnAbltyEvlAmtList` 가 제한그룹·면허별 금액이고, 운영자 쪽은 legacy 에서 연 1회 수기 입력이라 기준 연도가 정의돼 있지 않았다(`OPEN-QUAL-10`) | capability map 의 QUAL-11 절 |
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
| `removeConstructionCapacity` | POST `/api/profile/construction-capacities/remove` | 면허명 한 행 삭제(값을 경로에 넣지 않기 위해 본문으로) |
| `readOnboardingChecklist` | GET `/api/onboarding-checklist` | 0.8 — **2b 가 내지 않는다**: `OPEN-7C4-AWAITING-CHECKLIST`(전제는 평탄 술어의 「깊이 1 object」 확장) |

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
  예산 독립」 측정이다(capability map 의 STR-06 절 · M6 추적표의 STR-06 행 — **좌표 제거 2026-10-24**, r1 F3 · CR L-5:
  이 slice 가 그 두 파일을 편집하므로 `파일:줄` 이 낡는다). 7A 조정안은 그것을
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

7C ② 가 같은 이름의 열거를 `workflow.evaluation` 에 두고 **두 표면의 단일 출처**로 삼기로 했다. 이 레인은 **ID 셋의 이름과
순서**를 그 정의와 같게 맞춘다(`QUAL_08_REGION` · `QUAL_10_PERFORMANCE` · `QUAL_11_CONSTRUCTION_CAPACITY`, 이 순서 —
7C ② worktree 의 정의와 대조). **「바이트 동일」은 정정한다**(r1 CR M-5): 두 열거는 패키지도 모양도 다르다(이쪽만
`capability` 속성을 든다). 같은 것은 **항목 이름과 순서**이고, 그 등식을 잠그는 게이트는 없다 — 수동이다(scope 알려진 제한 2).
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

## §4 Phase 2b — 설계 (코드 0 · 빌드 0, 팀장 답 대기)

base main `46ac0949`, 브랜치 `m7-7c4b/2026-10-26`. 아래는 전부 **main 을 읽어 확인한 사실** 위에 선다.

### 4.0 2a 이후 달라진 전제 (7B 가 들여온 것)

| # | 사실 | 2b 에 주는 제약 |
|---|---|---|
| G1 | 계정은 **세션 인증이 심은 typed 요청 속성** 하나에서만 나온다(`authenticatedAccount()`), 주체 라벨 문자열 파싱은 금지 | 내 controller 도 그 접근자만 쓴다. 본문·쿼리에서 계정을 읽는 자리를 만들지 않는다 |
| G2 | **요청 바인딩은 `AccountId` 를 짓지 못한다**(fail-closed 가드: Jackson 거부 · 객체 바인딩 거부 · 단일 매개변수 변환 거부) | 「본문에 계정을 넣는」 우회가 **앱 전역에서 이미 닫혀 있다**. 2b 가 다시 세울 필요가 없고, 대신 그 전제를 쓰는 쪽을 test 로 적는다 |
| G3 | 본문은 **`@RequestBody JsonNode` 만** 허용된다(객체 바인딩 자체가 거부된다) | 쓰기 네 operation 의 본문을 DTO 로 받지 않는다 — `JsonNode` 를 읽어 값 타입으로 옮긴다(7B · 전략 편집과 같은 관례) |
| G4 | `AccountScopedRoutes` 가 **계정 전용 뿌리**를 선언하고 표면 전수 test 가 그 선언과 판정을 대조한다 | `/api/profile` · `/api/onboarding-checklist` 를 그 상수에 더한다. `@RequestMapping` 은 상수에서 경로를 받는다 |
| G5 | `ErrorBody` 는 **code · 고정 문구 · correlationId 셋뿐**이고 예외 메시지·스택트레이스를 담지 않는다 | **2b 표적 1 의 응답 쪽은 이미 닫혀 있다**(아래 4.5) |
| G6 | 평탄 술어는 **허용 목록**이고 7C ② 의 확장은 **main 에 없다** | `awaiting` 의 짝 배열을 지금 넣을 수 없다(4.7) |

### 4.1 표면 — §0.6 표를 그대로 세운다

`readProfile`(GET `/api/profile`) · `replaceQualificationFacts` · `replaceBusinessDetails` ·
`listConstructionCapacities`(GET, 최상위 배열) · `upsertConstructionCapacity` · `removeConstructionCapacity`(POST) ·
`readOnboardingChecklist`(GET `/api/onboarding-checklist`). 쓰기는 전부 POST — CSRF 는 메서드로 구조적으로 요구되고
실패는 **401**(사유 미구분, D-6A1-6). controller 는 조회/쓰기로 가른다(7B 관례).

### 4.2 `unauthenticated` 공용화 (D-7C4-1 R1 의 남은 한 건)

`authenticatedAccount()` 는 이미 `app.http` 공용이다. 공용화가 필요한 것은 **401 응답 한 벌**
(`BidCaseHttp.unauthenticated(request)`) — 입찰함 이름 아래 있지만 내용은 계정 경로 공통이다. **거동 무변경 refactor
한 건**으로 공용 자리에 옮기고 7B 호출부를 그 자리로 돌린다(2b 의 별 커밋, rollback 목록에 포함).

### 4.3 쓰기 소유 경계 — 2b 표적 2 (controller 가 세션 계정만 넘긴다)

G2 가 「본문이 계정을 짓는 길」을 이미 닫았으므로 2b 가 더할 것은 **두 겹**이다.
1. **구조**: use case 서명의 첫 인자가 `AccountId` 이고(2a port 관례) controller 는 `authenticatedAccount()` 의 값만
   넘긴다. 조립(wiring)에 계정을 아는 다른 출처를 두지 않는다.
2. **test**: 「본문에 `accountId` 를 넣어도 다른 계정의 행이 바뀌지 않는다」를 실 DB E2E 로 잰다(두 계정). 이것이
   2a 알려진 제한 1(「쓰기의 소유 경계는 호출자 전제」)을 2b 에서 **갚는 자리**다.

### 4.4 `CAPACITY_ROWS_MAX` 집행 — 2b 표적 3

use case 가 upsert 전에 행 수를 센다. **검사-후-삽입은 경합한다**(두 요청이 동시에 상한 직전 수를 읽으면 둘 다 통과).
후보 둘: **(가) 앱에서만 재고 경합을 알려진 제한으로 등재**(운영자 1인 전제 — 2a 제한 1 과 같은 축) · **(나) 표 층
제약**(행 수는 CHECK 로 못 센다 — 트리거나 부분 유니크가 필요하고, 그것은 V23 과 트리거 관례를 끌어온다).
**권고는 (가)** — 2a 가 「동시 편집은 마지막 쓰기 승리」를 이미 등재했고, 같은 전제 위에서 수단만 늘리는 것이 과잉이다.
→ **확인 Q1**.

### 4.5 DB 예외 문면 누출 — 2b 표적 1 (응답은 닫혔고 **로그가 남는다**)

- **응답**: `ErrorBody` 가 code · 고정 문구 · correlationId 뿐이라(G5) `Failing row contains (…)` 가 본문으로 나가지
  않는다. 2b 는 그 사실을 **test 로 고정**한다(제약 위반을 일으키는 요청의 응답 본문에 행 값이 없다).
- **로그**: 그 문면이 스택트레이스로 로그에 남을 수 있다. 수단 둘 — **(가) 어댑터가 제약 위반을 도메인 결과로 바꾸며
  메시지를 버린다**(내 두 표에만 듣는다) · **(나) 데이터소스에 `logServerErrorDetail=false`**(pgjdbc 연결 속성 —
  **모든 표**에 듣지만 저장소 전역 설정이라 `detekt.yml` 과 같은 성격이다). → **확인 Q2**(권고: 2b 는 (가)로 자기 표를
  닫고, (나)는 전역 등재로 올린다).

### 4.6 격리 게이트 모집단 재서술 — 2b 표적 4

2b 는 `app` 이 `workflow.profile` 을 **정당하게** 참조하기 시작한다. 그래서 (1) 격리 test 의 「재지 않는 것 ⓐ `app`」
문면을 「2b 부터 참조한다」로 고치고, (2) **진짜 방어를 조립 자리에 세운다** — 「자격 판정 use case 의 조립이 V22 port 를
받지 않는다」를 wiring test 로 잠근다(2a 제한 5 의 N1 · N4 · N5 · B2 · B4 가 그 경계 뒤에서 무력해진다).
**새 게이트 술어를 또 만들지 않는다** — 2a 에서 「우회마다 술어 하나」를 멈추기로 한 결정 그대로다.

### 4.7 `awaiting` 응답 형태 — 지금 고르지 않는다

| 후보 | 모양 | 영향 |
|---|---|---|
| (가) 스칼라 배열 둘 | `awaitingItems[]` + 같은 순서의 `awaitingPrerequisites[]` | 지금 평탄 술어로 **통과한다**. 짝이 자리로만 묶여 약하고, 클라이언트가 두 배열의 순서를 믿어야 한다 |
| (나) 짝 배열 | `awaiting: [{item, prerequisite}]` | 7C ② 의 평탄 술어 확장(깊이 1 · 속성 전부 스칼라)이 **머지된 뒤에만** 가능하다. 지금 넣으면 계약 test 가 붉다 |

7C ② 머지 뒤에 정한다(팀장 지시). 그때까지 체크리스트 응답은 **완료 · 미완만** 내고 `awaiting` 을 **내지 않는다** —
그러면 D-7C4-2 의 세 상태가 응답에서 두 상태로 접힌다. → **확인 Q3**: 7C ② 를 기다렸다가 한 번에 낼지, (가)로 먼저
내고 7C ② 뒤에 (나)로 옮길지(그 전환은 wire 변경이다).

### 4.8 in_scope 예정 · 게이트 등재 자리

`app/.../http/`(controller 둘 · 요청 읽기 · 응답 · `AccountScopedRoutes` 추가) · `app/.../wiring/`(조립) ·
`openapi/bidvector-operator-api.yaml` · `web/src/.../generated`(재생성) · `config/quality/gate-tests.properties` ·
`architecture-policy.properties`(app.http 허용 목록 · use case 포트) · 2a 의 격리 test 둘(문면) ·
`workflow/.../profile/`(use case 신설). **마이그레이션은 없다**(V22 그대로).

### 4.9 팀장 확인

- **Q1** `CAPACITY_ROWS_MAX` 집행을 앱에서만(경합은 알려진 제한) — 권고 (가).
- **Q2** DB 예외 문면의 로그 쪽을 어댑터에서 닫고(내 표), `logServerErrorDetail` 전역화는 별 등재로 — 권고 (가).
- **Q3** `awaiting` 을 7C ② 머지까지 **응답에서 빼둘지**, (가)로 먼저 낼지.
- **Q4** `unauthenticated` 공용화가 7B 파일을 만진다(거동 무변경) — 2b in_scope 로 받아도 되는가.

## §5 Phase 2b — 구현 (r1)

설계 §4 의 표면을 세웠다. **`awaiting` 과 체크리스트 operation 은 빠져 있다**(D-7C4-8 Q3 — 7C ② 머지 뒤
짝 배열로 함께 선다). 아래는 설계가 고르지 않았던 자리의 판단과 그 근거다.

### 5.1 쓰기 결과를 값으로 — 갈래가 두 층인 이유

`ProfileWriteOutcome`(상한 초과) ⊃ `StorageWriteOutcome`(썼다 · 제약이 거부했다). **저장 port 의 반환 타입이
좁은 쪽**이라 어댑터는 「행 수 상한」을 지어낼 수 없고, use case 만 셋을 낸다. 한 타입에 셋을 두면 「저장 층이
낼 수 없는 값」이 저장 port 의 서명에 보이고, 두 타입을 평평하게 두면 use case 가 둘을 합치는 매핑을 또 쓴다.

거부가 지니는 것은 **SQLSTATE 다섯 글자뿐**이다. 드라이버 예외의 `detail`(거부된 행의 값)은 이 타입에 자리가
없다 — 어댑터가 문면을 버리는 것을 **잊을 수 없다**(D-7C4-8 Q2 의 구조 쪽).

### 5.2 응답이 평탄해야 해서 생긴 모양

평탄 술어(D-6A1-20 ⓑ)는 속성이 **스칼라이거나 스칼라 배열**일 때만 통과한다. 그래서
- 시평액 목록은 프로필 응답에 **없고** 자기 경로의 최상위 배열이다(7B `listBidCases` 와 같은 모양),
- 면허의 세 상태는 **불리언 둘 + 배열 하나**로 편다(`qualificationDeclared` · `licensesDeclared` ·
  `licenseNames`). 세 상태를 두 상태로 접지 않는다는 D-7C4-2 의 규율이 wire 에서도 유지된다.

### 5.3 나라장터 세 이름의 단일 선언

같은 세 문자열이 V22 CHECK · 어댑터 · HTTP 세 곳에 복사될 참이었다. **열거 `NaraRegistration` 하나**로 모으고
어댑터와 요청 판독이 그것을 읽는다(r1 에서는 companion `const` 셋이었다 — r1 의 비교 게이트가 문자열 `when` 을
막으면서 열거로 바꿨다, §5.6 ①). 열거라 각 층이 이름 비교 대신 `when` 소진으로 상태를 가른다.

**선언 한 자리는 Kotlin 안에서만 참이다**(r2 CR L-5): V22 의 `nara_registration` CHECK 가 같은 세 문자열을 SQL
문면으로 따로 적고 있고, 둘을 잇는 끈은 `JdbcBusinessProfileStoreTest` 의 왕복뿐이다 — 그 사실을 타입 KDoc 에 적었다.

### 5.4 2b 표적 넷의 수단

| 표적 | 수단 | 재는 자리 |
|---|---|---|
| 1 DB 예외 문면 | 어댑터가 SQLSTATE class 23 을 값으로 바꾼다(문면 버림) | 실 DB test(외래 키 거부) · endpoint test(응답 세 필드 · 행 값 0) |
| 2 쓰기 소유 경계 | controller 가 `authenticatedAccount()` 값만 넘긴다 · 요청에 `accountId` 자리 없음 | use case test(받은 계정만) · endpoint test(알 수 없는 키 400 · 계정 없는 인증 401) |
| 3 행 수 상한 | use case 가 세고 거부(앱 집행, D-7C4-8 Q1) | use case test(상한 · 갱신은 통과 · 남의 행 안 셈) · endpoint test(409) |
| 4 격리 모집단 재서술 | **조립 경계 집합 규칙** — V22 를 받거나 내는 `@Bean` 은 프로필 조립의 것뿐 | `ProfileAssemblyBoundaryTest` · 2a 격리 test 의 「ⓐ app」 문면 갱신 |

표적 4 는 **술어를 더 만들지 않는다**(2a 에서 「우회마다 술어 하나」를 멈추기로 한 결정 그대로). 판정 조립을
이름으로 열거하지도 않는다 — 기본이 거부라 새 조립이 생겨도 그 자리를 지나야 한다.

### 5.5 알려진 제한(2b 에서 느는 것)

1. **검사-후-삽입 경합** — 두 요청이 상한 직전의 같은 수를 읽으면 상한을 하나 넘길 수 있다(D-7C4-8 Q1 이 받아들인
   제한, 단일 운영자 전제).
2. **제약 거부 번역은 이 slice 의 두 표에만** 듣는다. 저장소 전역은 `OPEN-7C4-LOG-DETAIL-GLOBAL`.
3. **오늘 앱 경로로 닿는 제약 위반이 없다** — 값 타입이 먼저 거부하기 때문이다. 그래서 번역은 방어 겹이고, 실
   DB test 는 **없는 계정**(외래 키)으로 그 경로를 연다.
4. `OperatorSessionController` 의 private 401 사본은 그대로다(7D 파일 — 공용화 후속 후보).
4b. **체크리스트 operation 이 없다** — 그 endpoint 와 `awaiting` 은 `OPEN-7C4-AWAITING-CHECKLIST` 가 닫는다
   (전제: 평탄 술어의 「깊이 1 object」 확장). 2a 가 세운 판정 커널은 production 경로가 없는 채로 남고,
   화면 A 는 그 slice 뒤에 체크리스트를 그릴 수 있다.
5. **V12 자격 사실 쓰기는 계정 경계 밖이다**(r2 v F3 — 앞 판은 이것을 「동시 편집」으로 줄여 적었다). 어느 계정의
   세션이 쓰든 **모든 계정이 보는** 한 행을 덮고, 그 값은 모든 계정의 프로필 조회와 자격 판정 입력이 된다. 경로가
   계정 범위이고 요청에 `accountId` 자리가 없다는 것이 **소유 경계를 만들지 않는다**. D-7C4-1 §0.6 이 「V12 기존
   저장을 그대로」 승인했고 계정 단위 전환은 R-2 남은 절반이라 설계대로이지만, 문면이 동시성 문제처럼 읽히지
   않게 적는다. OpenAPI description 과 `ProfileOwnershipE2ETest` 의 test 하나가 같은 사실을 말한다 — 그 test 가
   붉어지면 이 제한이 사라졌다는 뜻이고, 그때 이 줄과 계약 문면을 함께 고친다.

### 5.6 계정 경계·비교 게이트가 요구한 것 — 코드 수정 둘과 등재 셋

2b 의 새 표면이 7B·7D 가 연 게이트 넷에 걸렸다. **등재로 초록을 만들기 전에 각 게이트가 요구하는
것을 코드가 지키는지 먼저 판정했다**(팀장 지시 2026-10-26). 결과는 코드 수정 둘, 등재 셋이다.

**RED 의 출처**: acceptance A-1 (`check` job, base `46ac0949` 위 HEAD `f69d95e1`) — `:app:test` 의
architecture test 셋과 `:app:ktlintMainSourceSetCheck`. 등재 항목은 **그 실행에서 붉은 것을 보고** 더했다.

| # | 게이트 | 판정 | 무엇을 | 근거(실측) |
|---|---|---|---|---|
| ① | 상수 시간 비교 구조(`String.equals` 전면 금지) | **코드 결함** | 어휘를 `NaraRegistration` 열거로 세우고 각 층이 `when` 으로 소진 | 고치기 전 `ProfileRequestsKt` 가 `String.equals` **3**(문자열 `when` 이 switch 로 컴파일). 고친 뒤 app.http **전수 0** |
| ② | ktlint | **코드 결함** | `when` 분기 중괄호 · 체인 줄바꿈 · 겹친 KDoc 둘을 자기 선언 위로 합침 | 사전 실행 P-3 의 위반 목록 |
| ③ | `AccountCarrierStructureTest` 「계정 범위 핸들러는 typed 접근자를 쓴다」 | **등재** | 관측 집합에 controller 둘 | 그 집합은 `authenticatedAccount()` **호출자 전수**다. 같은 파일의 다른 등식(주체 라벨 접근자 호출자 · 라벨 문자열 보유 · 속성 읽기 자리 넷)은 **그대로 초록**이었다 — 내 두 controller 는 라벨을 읽지 않는다 |
| ④ | `AppHttpDependencyGateTest` 면제 크기 | **등재** | 68 → 69 | 늘어난 하나는 `ProfileWiring` 이고 JDBC 어댑터를 조립하는 `@Configuration` 이라 `BidCaseWiring` 과 같은 tier1 이다. 같은 test 의 「면제가 실재하는 클래스만 가리킨다」와 「`app.http` 는 어느 갈래에도 없다」는 그대로 초록 |
| ⑤ | `Intrinsics.areEqual` 허용 목록 | **등재(추가 넷) + 죽은 항목 삭제(하나)** | 아래 표 | javap 계수(사전 실행 P-2) |

**④ 의 정책 키는 다섯이다**(r2 v F8 — 앞 표는 면제 크기 하나만 들었다). `architecture-policy.properties` 의
2b 변경 전수와 각 근거는 다음과 같다.

| 키 | 더한 것 | 근거 |
|---|---|---|
| `app.http.allowed-packages` | `bidvector.workflow.profile` **패키지** | controller 가 요청을 값으로 옮기고 응답을 그리려면 그 어휘를 이름 불러야 한다. 패키지를 열어도 **주입은 따로 허용 목록**이라 열림이 핸들러 주입으로 새지 않는다 |
| `app.http.denied-types` | 저장 · 읽기 port 셋 | 위에서 연 패키지 안의 **능력 port** 를 다시 닫는다 — 핸들러가 받는 것은 use case 뿐이다 |
| `app.injection.allowed-types` | use case 여섯 | 그 목록은 **오늘 실제 주입 타입과 등식**이다(죽은 항목 금지). 응답 값 타입의 생성자 인자는 이미 있던 스칼라·`List` 로 끝난다(중첩 응답이 없다) |
| `app.assembly.tier1-bootstrap-classes` | `ProfileWiring` | JDBC 어댑터를 세우는 `@Configuration` — `BidCaseWiring` 과 같은 층이다. 면제 **크기** 단언(68→69)이 이 등재를 눈에 띄게 한다 |
| `app.surface.api.handlers` | controller 둘 | 출하 조립에서 거둔 handler 집합과의 **등식**이다 — 표면이 늘면 반드시 이 줄을 고쳐야 초록이 된다 |

**⑤ 의 두 축을 따로 센다.**

*추가 넷* — 전부 app.http 전수 계수에서 실제로 `areEqual` 을 가진 새 class 이고, `String.equals` 는 0 이다.

| class | areEqual | 성격 |
|---|---|---|
| `RequestValuesKt` | 2 | 어휘 조회 둘(단일 값 · 집합) — 공개 enum 이름 비교 |
| `ProfileResponse` | 9 | data class 자동 생성 equals |
| `ConstructionCapacityResponse` | 1 | data class 자동 생성 equals |
| `ProfileHttp` | 1 | sealed 싱글턴 `when` 소진(객체 동일성) |

*삭제 하나* — `BidCaseRequestsKt`. 어휘 조회 둘이 `RequestValues.kt` 로 옮겨 가면서 그 파일에 최상위
선언이 남지 않아 **facade class 자체가 사라졌다**(사전 실행 P-2: 그 이름의 class 파일 없음). 등재를
남겨 두면 가리키는 것이 없는 항목이 된다.

**자격증명 좌표 0** — 네 class 어디에도 `OperatorCredential` · 세션 토큰 · 해시 값이 없다. 비교하는 것은
요청 본문의 공개 어휘와 응답 값 객체의 필드뿐이다.

### 5.7 변이 — 표적 넷(임시 clone, 바꿔치기, 통제군 `--rerun`)

판정 SHA 의 clone 에서만 돌렸고, 매 변이는 **있던 코드를 바꿔치웠다**(더하기 변이는 초록이 되기
쉽다). 적용 표지를 못 찾으면 그 자리에서 멈추게 했고(네 변이 모두 「mutated」 출력), 끝난 뒤 트리
변경 0 을 확인했다. 통제군은 앞뒤로 두 번 두고 `--rerun` 으로 돌려 **결과 XML 의 mtime 이 그 실행의
것**임을 확인했다(cache 적중이 아니다).

| 변이 | 바꿔치운 것 | 결과 | 무엇이 잡았나 |
|---|---|---|---|
| M-1 계정 경계 | 조회 controller 가 세션 계정 대신 **고정 계정**을 쓴다 | RED(표면 2건 · 구조 1건) | 「계정 없는 인증은 401」과 왕복이 깨지고, `AccountCarrierStructureTest` 의 「app main 의 어디도 `AccountId` 를 만들지 않는다」가 함께 붉었다 — 요청 밖에서 계정을 **짓는** 길이 구조로 막혀 있다 |
| M-2 어휘 | `NaraRegistration.UNANSWERED` → 다른 이름(열거·어댑터·요청 판독 네 자리 동시에) | RED(1건) | 「프로필 미설정」 표면 test — 그 이름이 **wire 에 실제로 닿는다**는 증거다(열거만 바꾸고 응답이 그대로면 어휘가 죽은 값이라는 뜻) |
| M-3 쓰기 port 분리 | 읽기 어댑터가 저장 port 도 구현하도록 되돌림 | RED | `:adapters:typeShapeGate` — 「구현 인터페이스 2 > 1」. 분리를 되돌리면 래칫이 즉시 막는다 |
| M-4 허용 목록 | `areEqual` 등재에서 `ProfileHttp` 를 뺌(키 집합·근거 맵 둘 다) | RED(1건) | 「허용 목록 밖에서 `Intrinsics.areEqual` 을 쓰지 않는다」 — 등재가 **판정을 실제로 받치고 있다** |

**통제군**: 변이 전 표면 9건·비교 게이트 3건·모양 게이트 전부 초록, 변이 뒤 되돌린 상태에서 표면
9건 다시 초록. 네 번의 실행이 서로 다른 mtime 을 가져 「돌지 않고 초록」이 아니다.

**이 라운드가 재지 않은 것**: `awaiting`(7C ② 머지 뒤).

> **정정(r2)**: 앞 판은 여기에 「실 DB 소유 경계(2a 가 이미 쟀다)」도 적었다. **절반만 맞았다** — 2a 가
> 잰 것은 SQL 의 계정 필터이고, 「controller 가 어떤 계정을 넘기는가」는 재지 않았다. r2 의
> `ProfileOwnershipE2ETest` 가 그 자리를 받는다(§6.1).

### 5.8 이 판정에서 빠지는 것 — `awaiting` 과 체크리스트 operation

**`awaiting` 응답과 시작하기 체크리스트 operation 은 이 slice 에서 빠진다 — 별 slice 다**
(`OPEN-7C4-AWAITING-CHECKLIST`, 운영자 결정 2026-10-28).

D-7C4-8 Q3 은 「7C ② 머지 뒤 짝 배열로 넣는다」였고 그 전제는 **평탄 술어의 「깊이 1 object」 확장**
이었다. 7C②-a 가 머지된 main 에서 그 전제를 쟀더니 **확장이 없었다**: 계약 게이트의 평탄 술어가 그대로
(해당 test diff 0 줄)이고 병합된 spec 에 깊이 1 object 속성이 **0** 이다. 짝 배열은 지금 거부된다.

술어 확장은 **두 레인이 공유하는 계약 게이트 변경**이라 별 설계 검토가 필요하고, 2b 는 측정이 끝난
상태로 종결할 수 있다 — 그래서 이 판정이 덮는 표면은 **operation 여섯 + OPEN 하나**다. 스칼라 배열 둘로
먼저 내고 뒤에 옮기는 길은 택하지 않는다(wire 를 두 번 바꾼다).

**D-7C4-8 은 그대로 남는다**: 세 상태를 두 상태로 접은 응답은 내보내지 않으므로 체크리스트 endpoint 는
`awaiting` 과 함께 선다. 그때까지 그 endpoint 를 내지 않는 것이 이 slice 의 상태다.

## §6 r2 — 승인 전 일괄 (verifier r1 ready-for-review · code-review r1 approve)

차단은 0 이었고, medium 둘과 low 여덟을 한 라운드로 처리했다. 아래는 **무엇을 고쳤고 그것이 무엇을
막는가**다.

### 6.1 방어가 넓어진 둘 (medium)

| # | 전 | 후 | 그 전에 무엇이 지나갔나 |
|---|---|---|---|
| v F1 조립 경계 | `@Bean` 서명 타입의 **이름이** `workflow.profile.` 로 시작하는가 | 타입의 **상위 타입 전이 폐포**에 그 패키지 선언이 있는가 | 판정 조립이 V22 를 **어댑터 구현 타입**(`JdbcBusinessProfileStore`)으로 받으면 지나갔다(변이 MB5 가 초록으로 살아남았다) |
| v F2 쓰기 소유 경계 | 이중체 계정 **하나** — 「붙든 계정」과 「세션 계정」이 같은 값이라 갈라낼 수 없었다 | 출하 조립 · 실 DB · **로그인 둘**로 A 의 행이 B 에게 보이지 않음을 잰다 | controller 가 첫 세션 계정을 붙들어도 전부 초록이었다(변이 MB1) |

§5.7 의 「실 DB 소유 경계는 2a 가 이미 쟀다」는 **절반만 맞았다**: 2a 가 잰 것은 SQL 의 계정 필터이고,
「controller 가 어떤 계정을 넘기는가」는 재지 않았다. 그 자리를 `ProfileOwnershipE2ETest` 가 받는다.

### 6.2 입력 경계가 좁아진 넷

- **공백뿐인 상호는 400**(CR M-2 · v F6). 앞 판은 `null` 로 접어 저장된 상호를 지우고 200 을 냈다 —
  절 단위 교체에서 공백이 조용한 「지우기」였다. 부재·`null` 만 미설정이다.
- **제어 문자는 400**(v F5). NUL 은 PostgreSQL 이 SQLSTATE `22021` 로 거부하고 그 예외는 어댑터의
  class 23 번역 밖이라 **500** 이 됐다 — 요청 탓을 서버 탓으로 말하는 자리였다. 저장 층에 닿기 전에 막는다.
- **class 23 밖은 값으로 접지 않는다**를 실 DB test 로 고정(v F5 의 짝). 변이 MB4(전부 값으로 접기)가
  초록이던 자리다.
- **V12 자유 어휘에 값 규칙**(v F4): 원소 공백 거부 · 길이 상한 · 배열 크기 상한 · 중복 거부. V22 와
  같은 수를 쓰고(상호·면허명 100), 새로 지은 수는 배열 크기 하나다 — `ProfileInputPolicy` 에 근거와
  함께 선언했다. 2b 가 **처음 HTTP 로 연** 쓰기 경로라 경계가 앱에만 있다.

### 6.3 값이 wire 에 닿는가 (CR M-1 · v F7)

응답의 `unjudgedAxes` 가 use case 의 `ProfileView.unjudgedAxes` 를 버리고 선언 표를 다시 셌다. 지금은
같은 값이라 거동 차이가 0 이지만, 판정이 생긴 축을 use case 가 걸러도 wire 는 전부를 실었을 것이다.

### 6.4 장부·문면 (low)

- 정책 등재 표를 **키 다섯 전부**로(v F8) · 제한 5 를 「계정 경계 밖」으로(v F3, test 와 OpenAPI 가 같은
  사실을 말한다) · §5.3 의 `const` → 열거(CR L-2) · 설계 표의 `removal` → `remove`(v F10).
- OpenAPI 에 값 범위(v F9 · CR L-4) · 어휘 배열 규칙 · V12 쓰기의 계정 경계 문면.
- `NaraRegistration` KDoc 에 **V22 CHECK 가 같은 문자열을 따로 적고 있고 둘을 잇는 끈은 왕복 test 뿐**
  이라고(CR L-5) · 경로 상수를 선언 한 자리로(CR L-6) · 외래 키 거부의 사유(CR L-7) · 두 이중체가
  서로를 가리키게(CR L-8).
- **CR L-1(두 endpoint 의 `unjudgedAxes` 어휘)은 이 라운드에서 고치지 않는다.** 7C ② 가
  `workflow.profile.UnjudgedQualificationAxis` 를 지우고 `workflow.evaluation` 으로 옮기므로, 그 머지 뒤
  병합에서 두 어휘가 한 선언으로 합류한다. `awaiting` 작업과 같은 자리에서 처리한다.

### 6.5 새 CSRF 표적 test

쓰기 넷을 **CSRF 없이** 보내면 401 임을 endpoint 에서 잰다(CR L-3). 구조적으로는 필터가 덮지만, 그 사실을
이 표면의 test 가 말하지 않고 있었다.

### 6.6 변이 — r2 표적 일곱 (임시 clone, 바꿔치기, 통제군 `--rerun`)

판정 SHA 의 clone 에서만 돌렸다. 변이식은 **상수 조건을 쓰지 않는다** — `-Werror` 가 「항상 참/거짓」을
컴파일 오류로 올리기 때문이다(r1 verifier 가 겪은 자리). 규칙 표에서 갈래를 지울 때는 비-`else`
갈래를 하나 남겨 `when { else -> … }` 꼴이 되지 않게 했다.

**적용 표지**: 일곱의 앵커(실제로는 아홉 — M-1 이 세 자리를 바꾼다)를 변이 실행 **전에** 현행 소스와
대조해 일치 수가 전부 1 임을 확인했다. 첫 시도에서 M-5 의 앵커가 0 건이었고(그 파일에 애너테이션 한 줄이
더 있었다) 가드가 그 자리에서 멈췄다 — 앵커를 고친 뒤 다시 돌렸다.

**실행의 증거는 Gradle task 상태 줄**이다(`> Task :app:test` 뒤 `FROM-CACHE`·`UP-TO-DATE` 표식). 아래 모든
실행에서 그 표식은 **0 건**이다. XML mtime 은 캐시가 출력을 풀어 쓴 시각일 수 있어 근거로 쓰지 않는다
(팀장 지시 2026-10-28).

| 변이 | 바꿔치운 것 | 결과 | 붉은 단언 |
|---|---|---|---|
| **M-5** | 판정 조립(`EvaluationWiring`)에 V22 **어댑터 구현 타입** `@Bean` | RED 1 | 「V22 타입을 받거나 내는 조립은 ProfileWiring 하나다」 — **r1 에서는 초록이던 자리**(v F1) |
| **M-1** | 조회 controller 가 첫 세션 계정을 붙든다 | RED 3 | 두 계정 E2E 셋(조회 격리 · 삭제 404 · 같은 면허명 각자) — **r1 에서는 초록이던 자리**(v F2) |
| M-6 | 공백 상호를 규칙 표에 닿기 전에 `null` 로 접기 | RED 1 | 「공백뿐인 상호는 400」 |
| M-7 | 제어 문자 규칙 한 줄 제거 | RED 1 | 「제어 문자는 400」 |
| M-8 | 자유 어휘 개수 상한 한 줄 제거 | RED 1 | 「자유 어휘도 값 규칙을 받는다」 |
| M-9 | 응답이 선언 표를 다시 세기 | RED 2 | 응답 축 둘(목록 그대로 · 빈 목록도 값) |
| M-4 | class 23 **밖도** 값으로 접기 | RED 1 | 「무결성 제약이 아닌 DB 오류는 그대로 던진다」 |

**통제군**: 변이 전 app 넷(표면 13 · 두 계정 4 · 조립 경계 2 · 응답 2)과 adapters 프로필 셋(격리 6 ·
스키마 8 · 실 DB 13) 전부 초록, 변이 뒤 되돌린 상태에서 app 넷 다시 초록. 끝난 뒤 clone 트리 변경 0.

**읽을 때 주의 하나**: 로그의 「최근 XML 셋」 줄에는 **같은 clone 의 앞 변이가 남긴 결과 파일**이 섞일 수
있다(M-4 는 `:adapters:test` 만 돌렸는데 M-9 가 남긴 app 쪽 XML 이 함께 보인다). 판정에 쓰는 신호는
**task 상태 줄과 이름이 찍힌 붉은 단언**이고, XML 목록은 참고다.

## §7 r3 전 일괄 (verifier r2 ready-for-review · code-review r2 approve)

차단 0. medium 둘(게이트 모집단 · 길이 규칙 test)과 low 들을 한 라운드로 처리했다.

### 7.1 조립 경계 — 술어는 r2 에서, **모집단은 이번에** 구조가 됐다 (v N1)

| 판 | 무엇으로 판정했나 | 무엇이 지나갔나 |
|---|---|---|
| r1 | `@Bean` 서명 타입의 **이름 접두** | 어댑터 구현 타입으로 감싼 길(MB5) |
| r2 | 서명 타입의 **상위 타입 폐포** | **모집단**이 `@Bean` 메서드 서명뿐 — 생성자 주입 · 중첩 `@Configuration` · 소거된 제네릭 · 컨텍스트 조회 넷(MF2) |
| r3(이번) | **ArchUnit 의존 규칙** — `app.wiring` 의 class(중첩 포함) 중 `ProfileWiring` 밖은 V22 타입에 의존하지 않는다 | 아래 「재지 않는 것」 둘 |

ArchUnit 의 의존은 생성자 · 필드 · 메서드 서명 · 제네릭 타입 인자 · class 리터럴 · 호출을 담으므로
MF2 의 네 꼴과 MB5 가 **한 술어**에 들어온다.

**배열 축(r2 CR L-2)은 술어를 늘리지 않고 전제를 test 로 두는 쪽으로 처분했다.** 과정은 이렇다.

1. 술어에 성분 하강(`baseComponentType`)을 넣었다 — `Array<…Port>` 는 Spring 이 주입하는 실재 길인데
   배열 class 에는 패키지가 없어 이름 비교가 지나가기 때문이다.
2. 양성 대조를 고치다가 ArchUnit 이 배열 필드 하나에 위반 줄을 **둘** 내는 것을 봤다(배열 타입과 성분
   타입을 따로 센다). 그러면 하강은 도구가 이미 하는 일의 덧댐일 수 있다.
3. **변이 MA-arr 로 가렸다**: 하강 줄을 지우고 같은 test class 를 돌렸다 → **초록**(4/4, task 상태에
   cache 표식 0). 덧댄 술어라는 뜻이다.
4. 그래서 **그 줄을 지웠다.** 배열 양성 대조는 **남긴다** — 이제 그것이 「도구가 배열 의존을 성분
   타입으로도 센다」는 **전제의 잠금**이다. 전제가 깨지면 그 test 가 붉어지고, 그때 술어를 다시 본다.

이 처분의 근거는 추측이 아니라 MA-arr 의 초록이다. 「고쳐서 초록」과 「원래 초록」을 가른 자리다.

**재지 않는 것을 실측 범위로 다시 적었다**: ⓐ 실행 시 이름으로 조립한 길(class 리터럴이 없어 의존
그래프에 없다 — 2a 격리 게이트가 상수 풀의 점 표기를 본다) · ⓑ `app.wiring` 밖의 조립. ⓑ 를 덮는
게이트 이름도 바로잡았다(r2 v L-c): 표면 census 가 아니라 `AppHttpDependencyGateTest` ·
`ArchitectureGateTest` 다 — census 는 HTTP handler · filter · servlet 만 거둔다.

### 7.2 이름 규칙이 넓어졌다 (v L-a)

제어(Cc)만 보던 것을 **유니코드 분류 여섯**(Cc · Cf · Cs · Co · Zl · Zp)으로 거부한다. 앞 판은
**보이지 않는 이름**(폭 0 문자만으로 된 상호) · `U+2028` 줄 구분 · 짝 없는 대리를 통과시켰다.
할당되지 않은 코드포인트(Cn)는 거부하지 않는다 — 유니코드가 커지면 정상 입력을 막는다. 문자를
열거하지 않고 분류로 거부하는 이유도 같다(열거에는 종점이 없다).

### 7.3 길이 규칙에 test 가 붙었다 (CR M-2)

`requiringName` 의 세 규칙 가운데 길이만 test·변이가 없었다. 그 줄이 **유일한 방어**인 표면이 둘이다
(삭제 요청의 면허명 — 값 타입이 없다 · V12 자유 어휘 원소). 상한 100 과 101 두 단언을 붙였고,
`optionalDate` 의 공백 날짜 400 도 함께 잠갔다.

### 7.4 판단해서 고치지 않은 것

- **`codePointCount` 중복**(CR L-5): 주인은 `ProfileInputPolicy.codePointLength()` 인데 그것은
  `internal` 이라 **다른 모듈에서 부를 수 없다**. 한 줄을 다시 적는 쪽이 가시성을 넓히는 것보다 작다.
- **`unjudgedAxes` 만 `.sorted()` 가 없다**(CR L-7): 이제 **순서의 주인이 use case** 다(선언 표의 순서가
  뜻을 가진다 — 7C ② 자격 카드와 같은 순서). 정렬하면 그 뜻이 사라진다. KDoc 에 적었다.
- **응답 스키마의 범위 선언**(CR L-8): 무해하고 근거 문면은 요청 쪽에 있다 — 그대로 둔다.

### 7.5 병합 주의를 코드에 적었다 (CR L-4)

`HttpTestApplication` 의 `@Import` 는 반복 불가 애너테이션이라 다른 레인과 겹치면 **한 줄로 접어야**
하고, `gate.tests.app` 은 역슬래시 연속 줄이라 충돌 해소가 **항목 합집합**이어야 한다(한쪽 묶음을
고르면 다른 레인의 등재가 조용히 사라지고 등식이 붉어진다). 둘 다 그 자리에 주석으로 남겼다.

### 7.6 변이 — r3 표적 일곱 (임시 clone, 바꿔치기, 통제군 `--rerun`)

**MF2 의 네 꼴을 따로 걸었다.** 한꺼번에 걸면 하나만 잡혀도 실행이 붉어서 「어느 꼴이 잡히는가」를
가릴 수 없다. 넷 다 **r2 에서는 초록으로 살아남은** 자리다.

가드 셋: 앵커 일치 수를 **빌드 전에 전수** 확인(하나라도 1 이 아니면 빌드 없이 중단) · 각 변이 **적용
직전 clean 단언** · 통제군 앞뒤 `--rerun`(task 이름 뒤). 모든 실행의 task 상태 줄에 cache 표식 **0**.

| 변이 | 바꿔치운 것 | 결과 | 붉은 단언 |
|---|---|---|---|
| **MF2-①** | 설정 class **생성자 주입**(`EvaluationWiring(private val leakedCtor: …Port?)`) | RED | 「V22 타입에 의존하는 조립은 ProfileWiring 하나다」 |
| **MF2-②** | **중첩 `@Configuration`** 안의 `@Bean` | RED | 같은 단언 |
| **MF2-③** | 소거된 **제네릭 인자**(`List<…Port>`) | RED | 같은 단언 |
| **MF2-④** | 컨텍스트 **조회**(`getBeansOfType`) | RED | 같은 단언 |
| MB5 | 어댑터 **구현 타입** `@Bean` | RED | 같은 단언 |
| LEN | 길이 규칙 한 줄 제거 | RED | 「길이 상한은 두 표면에서 선다」 |
| MS-pair | `index += Character.charCount(…)` → `+= 1` | RED | 「짝을 이룬 대리가 든 이름은 통과한다 — 이모지 한 자」 |

**통제군**: 변이 전후 모두 초록(조립 경계 4 · 요청 3 · 표면 15), 끝난 뒤 clone 트리 변경 0.

MS-pair 가 RED 인 것이 **코드포인트 단위의 근거**다: 그 줄을 `+= 1` 로 바꾸면 이모지의 두 조각이 각각
대리로 읽혀 정상 입력이 400 이 된다. 「짝 없는 대리를 막는다」와 「짝을 이룬 대리를 통과시킨다」가
같은 한 줄에 달려 있고, 두 test 가 양쪽을 잡는다.

## §8 둘째 병합 라운드 (7C③ 선머지 수용, 2026-10-29)

### 8.1 해소 대상은 「충돌」이 아니라 교집합이다

main 이 들여온 경로 ∩ 이 slice 의 변경 경로 = **열하나**. git 이 충돌로 세운 것은 **여덟**이고
**셋은 조용히 자동 병합**됐다. 그 셋을 「충돌이 없었으니 괜찮다」로 넘기지 않고 같은 술어로 쟀다 —
이 저장소에는 **두 레인이 각자 같은 수를 들고 있어 git 이 충돌 없이 한쪽으로 접은** 전례가 둘 있다.

양쪽이 **각자 낡은** 자리가 하나 나왔다: 계정 전용 뿌리 선언에서 두 레인이 각각 「뿌리 **둘**」을
적고 있었다. 줄의 합집합을 택하면 `ROOTS` 가 한쪽 둘만 담는다 — **셋으로 다시 쓰는 것**이 옳은
해소였다. 합집합은 「줄의 합」이 아니라 「뜻의 합」이다.

### 8.2 합집합을 눈이 아니라 술어로 쟀다

| 자리 | 술어 | 결과 |
|---|---|---|
| 정책 파일 둘 | base·ours·theirs 를 파싱해 **한쪽이라도 건드린 키 전수**에 `(theirs − 내가 뺀 것) ∪ 내가 더한 것` 과 대조 | 키 **13** 전부 일치 · **같은 키 중복 0** · 키 수 보존(129 · 16) |
| 면제 래칫 수 | 세 tier 목록의 **이름 합집합**을 기계로 셈(숫자를 옮기지 않는다) | 병합 기준 72 · main 둘 · 내 하나 = **75** |
| OpenAPI | **중복 키를 거부하는 로더**로 적재 + 블록 본문 대조 | 중복 **0** · paths **35** · schemas **39** · 추가 블록이 출처 측과 본문 동일 · 기존 블록 변형 **0** |
| 생성 타입 | 병합된 spec 에서 **재생성** | 바이트 등식 통과 |
| 줄 단위 접힘 | 교집합 열하나 전수에 「내 신규 줄 전부 있는가」·「main 신규 줄 중 내가 지우지 않은 것 전부 있는가」 | 걸린 **넷 전부 의도된 치환**(낡은 2-항목 선언 둘 · 래칫 한 수 · 옮겨간 `@Import`) |

**술어를 한 번 틀렸고 그 자체가 기록이다.** 첫 판은 `theirs ∪ (ours − base)` 였고 상수 시간 비교
구조 test 에서 한 항목 누락을 올렸다. 그것은 접힘이 아니라 **이 slice 의 refactor**(어휘 조회 둘이
공용 요청 값 파일로 옮겨 가며 그 자리를 새 파일이 받았다)였다 — **내가 뺀 항목을 술어에서 빼야**
한다. 「합집합 = theirs ∪ (ours − base)」는 **한쪽이 의도적으로 지운 자리를 되살린다**.

### 8.3 찬 check 가 잡은 선행 결함 하나 (그리고 합집합 술어의 사각)

첫 찬 `check` 가 붉었다 — 계약의 오류 코드 enum 이 Kotlin 23 · 문서 22. **첫 병합이 접은 자리**이고
이번 라운드가 물려받았다(둘 다 「main 판 바탕 + 내 블록 재적재」를 썼고, 그 enum 한 줄은 「내 블록」이
아니었다).

**이것이 §8.2 술어의 사각이다**: 그 schema 는 `ours == base` 였으므로 **합집합이 요구되지 않았고**,
어떤 합집합 술어로도 찾을 수 없다. 한쪽에 **이미 있던** 결함은 다른 종류의 측정이 필요하다 —
구현 ↔ 문서 등식을 **실제로 돌리는 전건 `check`** 다. 그래서 병합 뒤 판정을 표적으로 줄이지 않았다.

회귀 방어는 새 test 가 아니라 **이미 등재된 그 계약 등식**이고, RED 가 선행했다(그 찬 check).
그 RED 는 동시에 이 고침의 **음성 대조**다 — enum 한 줄이 없는 트리에서 실제로 붉었다.

### 8.4 알려진 제한(이 라운드에서 느는 것)

1. **이 판정은 `a45105a5` 기준이고, 더 뒤의 main 과는 겹침이 0 이 아니다.** 기계 계수로
   `a45105a5..bb6a187e` 130 경로 중 **셋**이 in_scope 와 겹친다(허용·면제 목록 · test 등재 · OPEN 행).
   세 자리 모두 이 slice 가 **항목 합집합**으로만 만지는 성격이지만, 최신화는 `update-branch` 가
   아니라 **main 병합 라운드 한 번 더**를 요구한다. 그 판단은 팀장이 한다.
2. **`awaiting` 과 체크리스트 operation 은 여전히 이 slice 밖이다**(`OPEN-7C4-AWAITING-CHECKLIST`).
   그 전제(평탄 술어가 깊이 1 object 를 거부한다)를 병합된 트리에서 **다시 쟀고 그대로 참**이다.
3. **시계 이중체 파일 분리의 승인 사유가 갱신됐다.** 「그 파일이 정확히 500 줄」은 main 이
   `HttpTestApplication` 을 갈라 더 이상 서지 않는다(이제 202 줄). 분리는 **유지한다** — 세 레인의
   축 배분이 main 쪽 문면에 이미 적혀 있고, 되돌리면 그 문면이 거짓이 된다.

### 8.5 변이 — 면제 래칫 셋 (임시 clone, 바꿔치우기, 통제군 `--rerun`)

이 라운드가 바꾼 **게이트 입력**은 래칫 수(72 계열 → **75**)와 면제 목록 한 항목이다. 「그 값이 만드는
거동 변화」를 재려고 세 변이를 걸었다. 가드: 앵커 일치 수를 **빌드 전에** 확인(1) · 각 변이 적용 전후
clean 단언 · 표적에 `--rerun`(표적 test task 는 매 실행 표식 없이 돌았고 결과 XML 시각이 새 실행이다).

| 변이 | 바꿔치운 것 | 결과 | 붉은 단언 |
|---|---|---|---|
| **M1** | 래칫 수를 **74** 로(면제 하나가 조용히 숨는 쪽) | RED | 「게이트가 보는 대상 집합은 app 전체에서 면제 목록을 뺀 것과 같다」 — `expected:<74> but was:<75>` |
| **M2** | 래칫 수를 **76** 으로 | RED | 같은 단언 — `expected:<76> but was:<75>` |
| **M3** | **수는 75 그대로 두고** 면제 **목록에서 내 항목을 뺀다**(「숫자만 맞추는」 함정) | RED | **둘이 함께** — 의존 규칙이 그 조립 → 어댑터 참조 여덟을 적발하고, 계수 등식도 붉다 |

**통제군**: 변이 전후 모두 초록(21 test · 실패 0), 끝난 뒤 clone 트리 변경 **0**.

M3 이 이 라운드의 핵심이다 — 이 저장소가 두 번 틀렸던 자리가 **「계수만 올리고 목록에 넣지 않는 것」**
이고, 그 반대 방향(목록에서 빼고 계수를 남기는 것)도 **목록 쪽 규칙이 먼저** 잡는다. 수와 목록이 서로를
검산하므로 병합에서 한쪽만 접히면 반드시 붉어진다.

## §9 셋째 병합 라운드 (main `bb6a187e`, 2026-10-29)

### 9.1 충돌 0 을 「접히지 않았다」로 읽지 않았다

교집합은 **셋**(main 130 경로 중)이고 git 은 **충돌 0** 으로 자동 병합했다. 그래도 §8.2 와 같은 술어를
걸었다 — 아키텍처 정책의 건드린 키 **15**, 게이트 등재의 건드린 키 **셋** 전부 일치, 같은 키 중복 **0**,
교집합 셋 전수의 줄 단위 접힘 **0**. OpenAPI 는 이 main 이 무편집이지만 중복 거부 로더를 한 번 돌렸다
(paths 35 · schemas 39 · 오류 코드 enum **23** — §8.3 이 고친 한 줄이 그대로다).

술어는 **§8.2 에서 고친 판**(`(theirs − 내가 뺀 것) ∪ 내가 더한 것`)을 썼다. 이번에는 양쪽 모두 뺀
항목이 0 이라 두 술어의 답이 같지만, 답이 같은 것은 **입력 때문이고 술어 때문이 아니다**.

### 9.2 종료 코드 0 인데 「안 돌린 게이트」가 있었다

rollback ④⑤⑥ 첫 시도는 **exit 0 이었지만 전부 실행이 아니었다**: clone 에 앞 변이 라운드의 build
산출물이 남아 `401 중 198 실행 · 203 up-to-date` 로 돌았고 **모듈 게이트 19 개가 up-to-date** 였다.
종료 코드만 보면 통과이고, 게이트 계수를 따로 세야 드러난다. build 디렉터리를 비우고 `--rerun-tasks`
로 다시 재서 **401 전부 실행 · 게이트 up-to-date 0** 을 얻었다(그 사이 복원된 트리 상태 `D 17 · M 29`
는 보존됐다).

### 9.3 알려진 제한 — 갱신

§8.4 ① 은 **닫혔다**: 그 교집합 셋을 이 라운드가 받았다. 지금 판정은 main `bb6a187e` 기준이고, 그
뒤로 main 이 또 움직이면 같은 계수를 다시 내야 한다. §8.4 ②③ 은 그대로다.
