-- M7/7D — 브라우저 인증의 표 둘(계약 산출물 1). 운영자 결정 R-2 (c): **표는 다수 계정을
-- 전제로 세우고 행은 하나만 만든다** — 7B·7C 의 사용자 소유 표가 `account.id` 를 참조한다.
--
-- 번호는 D-6A1-12 의 정의를 따른다(「PR 시점 main 최대 번호보다 크고, 병행 레인이 선점 통보한
-- 번호를 피한다」) — 착수 시점 main 최대는 V19 이고, 병행 레인 중 마이그레이션을 만드는 것은
-- 이 slice 뿐이다(계약 산출물 1). PR 직전에 그 정의대로 재확인한다.
--
-- **평문은 어느 열에도 없다.** 비밀 문구는 bcrypt 문면(salt·cost 포함)으로만, 세션 식별자와
-- CSRF 값은 SHA-256 hex 로만 담는다 — 쿠키를 읽지 못하는 자리(이 표·백업·audit·로그)에서는
-- 세션을 재현할 수 없다. 그 성질을 문장이 아니라 **타입**이 지킨다: 저장 포트의 서명이
-- `BrowserSessionDigest` 라 어댑터는 원문을 받을 수 없다(`workflow/access/AccessPorts.kt`).
--
-- **감사 주체의 값 규약**(산출물 1 셋째 줄) — `api_request_audit.subject` 는 **열을 바꾸지
-- 않고** 값으로 계정을 가리킨다: 세션으로 인증된 요청은 `account:<id>`, 기존 운영자 자격
-- 헤더는 `operator`(무회귀), 실패는 `unauthenticated`. 그 세 어휘는 `app` 쪽 필터가 정하고
-- `ProductionAssemblyAuthAuditTest` 가 실측한다.

-- =============================================================================
-- ① account — 계정. 행은 하나지만 제약은 다수를 전제로 둔다(단일 행 CHECK 를 두지 않는다 —
--    V9·V12 의 `CHECK (id = 1)` 이 R-2 가 지목한 「계정이 둘이 되는 순간 표를 바꿔야 하는」
--    자리다. 이 표는 그 자리를 처음부터 만들지 않는다).
--
--    시각 둘은 **애플리케이션이 싣는다**(DEFAULT 를 두지 않는다, D-6F1-5 의 정신) — 계정
--    생성·해시 교체는 운영자 명령의 사건이고 그 시각은 명령이 쥔 시계가 정한다.
-- =============================================================================
CREATE TABLE account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 로그인 식별자. UNIQUE 가 「같은 이름의 둘째 계정」을 표 층에서 막는다.
    login_name TEXT NOT NULL UNIQUE CHECK (login_name <> ''),

    -- bcrypt 문면 — 평문·가역 암호 0. 길이를 못박지 않는다(cost·판 표기가 바뀌면 길이가 바뀐다).
    passphrase_hash TEXT NOT NULL CHECK (passphrase_hash <> ''),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    -- 잠금 상태는 **두 열의 쌍**이다: 연속 실패 계수와 「이 시각까지 거부」. 잠그는 순간
    -- 계수를 비우므로(정책 데이터가 상한을 든다) 잠금이 겹겹이 길어지지 않는다.
    failed_attempts INT NOT NULL CHECK (failed_attempts >= 0),
    locked_until TIMESTAMPTZ
);

-- =============================================================================
-- ② browser_session — 서버 세션. **PK 가 쿠키 값의 해시**다(평문 쿠키 값을 담을 열이 없다).
--
--    `csrf_digest` 는 세션 값에서 유도한 CSRF 값의 해시다(계약 「해시 또는 세션 비밀로부터
--    유도」 — 둘 다 만족한다: 유도하므로 별도 난수를 보관하지 않고, 해시로만 담으므로 이 행
--    에서 CSRF 값을 꺼낼 수 없다). 검증은 제시값의 해시와 이 열의 상수 시간 비교다.
--
--    `created_at` 만 DB 가 찍는다 — 보존·파기(6B-3)의 기준 시각 축이 「DB 가 적재 때 찍는
--    열」이기 때문이다(운영자 결정 A-2). 나머지 시각은 세션 판정의 입력이라 애플리케이션의
--    시계가 정한다(test 가 그 시계를 밀어 만료를 잰다).
-- =============================================================================
CREATE TABLE browser_session (
    session_digest TEXT PRIMARY KEY CHECK (session_digest <> ''),

    account_id BIGINT NOT NULL REFERENCES account (id),

    csrf_digest TEXT NOT NULL CHECK (csrf_digest <> ''),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL,

    -- 만료는 두 축이다(유휴·절대). 유휴 시한은 절대 시한을 넘지 못한다 — 밀기만으로 세션이
    -- 영구해지는 길을 표 층에서도 막는다(애플리케이션의 `idleDeadline` 과 같은 불변식, 심층 방어).
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    CHECK (idle_expires_at <= absolute_expires_at),

    -- 로그아웃·재발급·해시 교체가 찍는 폐기 시각. 지우지 않고 표시한다 — 「폐기된 세션이
    -- 있었다」는 사실이 남아야 감사가 성립한다(파기는 6B-3 의 보존 분류가 진다).
    revoked_at TIMESTAMPTZ
);

-- 보존·파기(6B-3)의 cutoff 질의 지원 — V19 가 파기 대상 표마다 둔 인덱스와 같은 축이다
-- (`RETENTION_TABLE_RULES` 가 이 표를 PURGE 로 분류하므로 그 질의가 이 열을 읽는다).
CREATE INDEX browser_session_retention_cutoff_idx ON browser_session (created_at);

-- =============================================================================
-- GRANT — **열 단위**다. 요청 경로(앱 역할)가 할 수 있는 일을 최소로 줄인다.
--
--  · `account` 에 INSERT 가 없다 — 계정을 만드는 것은 운영자 명령이고 그 명령은 소유자
--    연결로 돈다(`PersistenceWiring.migrateAsOwner`·`JdbcRetentionStore` 선례). 앱 프로세스가
--    장악돼도 **새 계정을 만들 수 없다**.
--  · `account` 의 UPDATE 는 실패 계수·잠금 두 열에만 있다 — 앱 역할은 `passphrase_hash` ·
--    `login_name` 을 **덮어쓸 수 없다**(비밀 문구 교체도 운영자 명령의 소유자 연결이 한다).
--  · `browser_session` 의 UPDATE 는 사용 시각·유휴 시한·폐기 시각 세 열에만 있다 — 앱 역할은
--    세션의 소유 계정(`account_id`)·CSRF 해시·절대 시한을 **바꿀 수 없다**.
--  · DELETE·TRUNCATE 는 어느 표에도 주지 않는다(보존·파기는 6B-3 의 소유자 연결이 한다,
--    V8·V12·V15 와 같은 관례).
-- =============================================================================
GRANT SELECT ON account TO bidvector_app;
GRANT UPDATE (failed_attempts, locked_until) ON account TO bidvector_app;

GRANT SELECT, INSERT ON browser_session TO bidvector_app;
GRANT UPDATE (last_used_at, idle_expires_at, revoked_at) ON browser_session TO bidvector_app;
