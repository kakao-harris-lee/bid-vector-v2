-- M7/7C④ — 운영자 사업 정보의 표 둘(D-7C4-1, checklist §0.2). 운영자 결정 R-2 (c): **새 사용자
-- 소유 데이터는 처음부터 `account_id` 로 세운다** — 7B 의 입찰 건 넷(V21)과 같은 축이다.
--
-- 번호는 잠정이다(D-6A1-12 의 정의: 「PR 시점 main 최대보다 크고, 병행 레인이 선점 통보한 번호를
-- 피한다」). 착수 시점 main 최대는 V20, 7B 가 V21 을 쥐고 있다. PR 직전에 그 정의대로 다시 잰다.
--
-- **V12 `operator_profile` 은 건드리지 않는다.** 그 표의 세 상태(행 없음 / 미신고 / 신고(빈 목록))
-- 는 「행이 있으면 면허 상태가 있다」에 기대므로, 상호만 저장해도 행이 생기는 구조로 열을 더하면
-- 면허 「미설정」이 다른 상태로 납작해진다(checklist §0.2 근거 1). 그래서 이 표들은 V12 와
-- 독립이고, 저장한다고 V12 행이 생기지 않는다. V12 의 계정 단위 전환은 R-2 남은 절반(별 slice)이다.
--
-- **슬롯을 만들지 않는 것**(scope P4): 사업자번호 · 대표자명 · 인증서 원문/일련번호/발급기관.
-- 아래 열 목록이 그 부재의 증거이고 `CleanMigrationColumnTest` 가 열 전수를 잠근다.
--
-- 시각 열은 **애플리케이션이 싣는다**(DEFAULT 를 두지 않는다 — V20 `account` 와 같은 정신).
-- 체크리스트 진행 상태는 표가 없다 — 이 표들 · V12 · V9 에서 매번 판정한다(§0.2, 무저장).

-- =============================================================================
-- ① account_business_profile — 계정당 한 행. 상호 · 본사 소재지 · 나라장터 준비.
--
--    `trade_name` 은 **운영자가 입력한 원문**이다(앞뒤 공백도 자르지 않는다 — 정규화는 SET-01 대사
--    시점의 커널이 한다, 이중 정규화 금지). NULL 이 「상호 미설정」이고, **공백만으로 된 값은 값이
--    될 수 없다**(r1 CR M-3: 빈 문자열만 막으면 `'  '` 가 저장되고 그 행은 앱이 읽을 수 없다 — 앱은
--    공백 상호를 쓸 수도 없으므로 고칠 길 없는 읽기 실패였다). 술어는 「POSIX `[:space:]` 밖 문자가
--    하나는 있다」이고 앱의 `isNotBlank` 와 **문자 집합이 완전히 같지는 않다** — 두 집합의 차이는
--    유니코드 공백의 변두리이고, 그 틈으로 직접 SQL 이 넣은 값을 읽으면 앱이 크게 실패한다(조용히
--    접지 않는다). 길이 상한은 `ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS` 와 같은 값이다
--    (코드포인트 수 — `char_length` 가 세는 단위와 같다).
--
--    `headquarters_region` 은 시·도 17 의 닫힌 어휘(`HeadquartersRegion` 열거 이름)다. QUAL-08 의
--    **입력**일 뿐이고 그 판정은 아직 없다 — V12 `region_terms`(텍스트 합성용 자유 어휘)와 다른 축이다.
--
--    나라장터 준비는 세 열의 짝이다: 등록 여부(미응답 · 등록 · 미등록) · 등록분야 · 사업자용 인증서
--    만료일. 「등록」이 아닌데 분야나 만료일이 실리는 것은 모순이다 — 표 층에서도 막는다.
--    등록분야는 **집합**이라 중복 원소도 받지 않는다(r1 CR L-8: 앱은 늘 정렬된 distinct 를 쓰고 읽기
--    에서 `toSet()` 이 접으므로 중복은 조용한 정규화였다). 어휘 목록이 두 자리에 나오는 것은 의도다 —
--    하나는 「어휘 안인가」, 다른 하나는 「각 값이 한 번뿐인가」이고 둘 다 같은 닫힌 어휘에서 나온다.
-- =============================================================================
CREATE TABLE account_business_profile (
    account_id BIGINT PRIMARY KEY REFERENCES account (id),

    trade_name TEXT CHECK (trade_name IS NULL
        OR (trade_name ~ '[^[:space:]]' AND char_length(trade_name) <= 100)),

    headquarters_region TEXT CHECK (headquarters_region IS NULL OR headquarters_region IN (
        'SEOUL', 'BUSAN', 'DAEGU', 'INCHEON', 'GWANGJU', 'DAEJEON', 'ULSAN', 'SEJONG', 'GYEONGGI',
        'GANGWON', 'CHUNGBUK', 'CHUNGNAM', 'JEONBUK', 'JEONNAM', 'GYEONGBUK', 'GYEONGNAM', 'JEJU')),

    nara_registration TEXT NOT NULL CHECK (nara_registration IN ('UNANSWERED', 'REGISTERED', 'NOT_REGISTERED')),
    nara_registered_fields TEXT[] NOT NULL
        CHECK (nara_registered_fields <@ ARRAY['GOODS', 'SERVICE', 'CONSTRUCTION']::TEXT[]
            AND cardinality(array_positions(nara_registered_fields, 'GOODS')) <= 1
            AND cardinality(array_positions(nara_registered_fields, 'SERVICE')) <= 1
            AND cardinality(array_positions(nara_registered_fields, 'CONSTRUCTION')) <= 1),
    business_certificate_expires_on DATE,

    updated_at TIMESTAMPTZ NOT NULL,

    CHECK (nara_registration = 'REGISTERED'
        OR (nara_registered_fields = '{}' AND business_certificate_expires_on IS NULL))
);

-- =============================================================================
-- ② account_construction_capacity — 면허(업종)별 시공능력평가액. 계정 × 면허명 하나당 한 행.
--
--    시평액은 면허마다 하나다(공고 쪽 `cnstrtnAbltyEvlAmtList` 도 제한그룹 · 면허별 금액 —
--    capability-map QUAL-11). 금액은 원 단위 정수이고 **0 을 받지 않는다** — 0 은 「모름」의 기본값
--    으로 흘러들기 쉬운 값이다(.claude/rules/kotlin-service.md). 기준 연도는 필수다 — legacy 의 연 1회
--    수기 입력은 어느 해 평가액인지를 남기지 않았다(`OPEN-QUAL-10` 의 운영자 쪽 절반).
--    상한 · 연도 범위는 `ProfileInputPolicy` 와 같은 값이다.
--
--    QUAL-11 의 **입력**일 뿐이고 판정은 아직 없다. 공고 요건과의 단위 · 과세 · 기준 시점 대조는
--    `OPEN-QUAL-10` 의 공고 쪽 절반이 열려 있는 채다.
-- =============================================================================
CREATE TABLE account_construction_capacity (
    account_id BIGINT NOT NULL REFERENCES account (id),
    license_name TEXT NOT NULL CHECK (license_name <> '' AND char_length(license_name) <= 100),
    amount_won BIGINT NOT NULL CHECK (amount_won > 0 AND amount_won <= 1000000000000000),
    assessment_year SMALLINT NOT NULL CHECK (assessment_year BETWEEN 2000 AND 2100),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, license_name)
);

-- =============================================================================
-- GRANT — 열 단위(7D · 7B 관례). 앱 역할은 **소유자 열(`account_id`)을 바꿀 수 없다** — 행을 다른
-- 계정으로 옮기는 길이 권한 층에 없다.
--
-- 사업 정보 행은 지우지 않는다(DELETE 없음): 「미설정」은 열 값(NULL · UNANSWERED)으로 표현한다.
-- 시평액 행은 현재 값의 목록이라 면허 하나를 빼는 일이 정당하다 — DELETE 를 준다. 이력이 아니므로
-- 지워서 잃는 감사 사실이 없다(요청 이력은 `api_request_audit` 가 든다).
-- TRUNCATE 는 어느 쪽에도 없다.
-- =============================================================================
GRANT SELECT, INSERT ON account_business_profile TO bidvector_app;
GRANT UPDATE (trade_name, headquarters_region, nara_registration, nara_registered_fields,
              business_certificate_expires_on, updated_at) ON account_business_profile TO bidvector_app;

GRANT SELECT, INSERT, DELETE ON account_construction_capacity TO bidvector_app;
GRANT UPDATE (amount_won, assessment_year, updated_at) ON account_construction_capacity TO bidvector_app;
