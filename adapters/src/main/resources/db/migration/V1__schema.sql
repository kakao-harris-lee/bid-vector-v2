-- M3/3D ① — Flyway가 스키마의 단일 출처다(ADR 0004 D-2). ORM은 이 스키마를 만들지 않는다.
-- 네 영역: raw_observation(append-only) · notice/opening_result/qualification_text(canonical)
-- · notice_audit(감사) · collection_run(회계). PostgreSQL 전용 문법(JSONB)을 그대로 쓴다
-- (ADR 0004 D-1 — dialect 분기 없음, 다른 엔진은 실패한다).

-- =============================================================================
-- raw_observation — ② 원본 보존. 갱신·삭제 트리거는 V3가 건다(append-only 두 겹 방어).
-- =============================================================================
CREATE TABLE raw_observation (
    observation_key TEXT PRIMARY KEY CHECK (observation_key <> ''),
    source_endpoint TEXT NOT NULL CHECK (source_endpoint <> ''),
    -- ⑥ 「원문 전체, 재직렬화 없이」(verifier r1 F-7 뒤 운영자 결정 2026-09-08) — 항목의
    -- 원문 JSON 텍스트를 파서가 받은 토큰 범위 그대로 TEXT 로 담는다. JSONB 로 담으면
    -- 재직렬화라 키 순서·공백·escape 형태를 잃는다(그래서 TEXT). 원문이 없는 관측
    -- (koneps 밖 호출부·구 fixture)은 payload_fields 로부터 재구성한 문자열을 대신 싣는다
    -- (알려진 제한 — 그 경우 바이트 동일을 보장하지 않는다, evidence 기록).
    payload TEXT NOT NULL CHECK (payload <> ''),
    -- 계약이 등재한 필드만 담는 투영(구 payload 정의) — 조회·회계 편의용, 원문 정본은 위 payload.
    -- 미등재("unknown") 필드는 회계의 unknownFields 축이 세지만 이 투영에는 실리지 않는다.
    payload_fields JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    -- ⑥ 감사 — 이 행을 만든 배포 식별자(구성 근의 주입값, git SHA 등). 3D는 값의 출처를
    -- 정하지 않는다(운영 배선은 M6 6C 소관) — 빈 문자열만 거부한다.
    release_sha TEXT NOT NULL CHECK (release_sha <> ''),
    inserted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- provenance_authority — ④ 점유 가드가 읽는 데이터(V2 migration이 seed, 술어가 아니라
-- 데이터로 「덮을 수 있는가」를 진술한다, §5.1). floor_rate의 origin 어휘도 같은 표를
-- 공유한다(둘 다 없으면 기본 비권위 — 존재 가드만 적용).
-- =============================================================================
CREATE TABLE provenance_authority (
    provenance TEXT PRIMARY KEY CHECK (provenance <> ''),
    authoritative BOOLEAN NOT NULL
);

-- =============================================================================
-- notice — ① Notice canonical fact(NoticeFacts.kt). 금액 축마다 값+통화+과세+provenance
-- 짝이 있고(④'), provenance 없는 금액은 CHECK가 거부한다. PK는 문자열 쌍(D-3D-3) —
-- notice_round 형식만 CHECK로 재선언한다(3A NoticeRound 정규화 규칙은 재선언하지 않는다).
-- =============================================================================
CREATE TABLE notice (
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),
    status TEXT NOT NULL CHECK (status <> ''),
    business_category_code TEXT,
    business_category_label TEXT,

    base_amount_won NUMERIC(20, 0),
    base_amount_currency TEXT,
    base_amount_vat TEXT,
    base_amount_provenance TEXT,
    base_amount_provenance_detail TEXT,

    estimated_amount_won NUMERIC(20, 0),
    estimated_amount_currency TEXT,
    estimated_amount_vat TEXT,
    estimated_amount_provenance TEXT,
    estimated_amount_provenance_detail TEXT,
    -- ResolvedEstimatedAmount.sourceKey — Published provenance에서는 유도되지 않는 축이라
    -- (noticeRevision만 나른다) 별도 컬럼이 필요하다(base_amount와 다른 점, 코드 KDoc).
    estimated_amount_source_key TEXT,

    allocated_budget_won NUMERIC(20, 0),
    allocated_budget_provenance TEXT,
    allocated_budget_provenance_detail TEXT,

    floor_rate_fraction NUMERIC,
    floor_rate_origin_kind TEXT,
    floor_rate_origin_detail TEXT,

    deadline_at TIMESTAMPTZ,

    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision >= 1),
    -- 가장 최근 승인된 write를 낸 raw 관측 — AFTER UPDATE 감사 트리거가 이 값을 읽는다.
    observation_key TEXT NOT NULL REFERENCES raw_observation (observation_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (notice_number, notice_round),

    CHECK ((base_amount_won IS NULL) = (base_amount_currency IS NULL)),
    CHECK ((base_amount_won IS NULL) = (base_amount_vat IS NULL)),
    CHECK ((base_amount_won IS NULL) = (base_amount_provenance IS NULL)),
    CHECK ((estimated_amount_won IS NULL) = (estimated_amount_currency IS NULL)),
    CHECK ((estimated_amount_won IS NULL) = (estimated_amount_vat IS NULL)),
    CHECK ((estimated_amount_won IS NULL) = (estimated_amount_provenance IS NULL)),
    CHECK ((allocated_budget_won IS NULL) = (allocated_budget_provenance IS NULL)),
    CHECK ((floor_rate_fraction IS NULL) = (floor_rate_origin_kind IS NULL))
);

-- =============================================================================
-- notice_audit — ⑥ canonical 갱신마다 이전 값·provenance·사유·observation_key. AFTER UPDATE
-- 트리거(V2)가 자동 삽입한다 — Kotlin이 잊어도 남는다.
-- =============================================================================
CREATE TABLE notice_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notice_number TEXT NOT NULL,
    notice_round TEXT NOT NULL,
    revision BIGINT NOT NULL,
    observation_key TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (reason <> ''),
    previous_row JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX notice_audit_notice_idx ON notice_audit (notice_number, notice_round);

-- =============================================================================
-- rejected_write — ⑥ 거부된 mutation의 시도값·사유(항목별). 정상 경로에서 점유 가드가
-- Kotlin write 규칙에 걸려 시도조차 하지 않은 것과 달리, 실제로 DB에 도달했다가 트리거가
-- 거부한(또는 Kotlin이 스스로 감지해 기록한) 시도만 여기 쌓인다.
-- =============================================================================
CREATE TABLE rejected_write (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notice_number TEXT NOT NULL,
    notice_round TEXT NOT NULL,
    observation_key TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (reason <> ''),
    attempted_value JSONB,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- opening_result — ① OpeningResult canonical fact. 「최신 관측 우선」(observed_at, D-M3-7의
-- OPEN-DIC-09 잠정 ⓐ) — notice의 권위 계층 가드와는 다른 축이라(파생값끼리의 재관측) 이
-- 테이블은 provenance 점유 가드를 두지 않는다(3D 설계 검토 「구현 지침」의 범위 좁힘,
-- evidence 「판단이 갈린 지점」).
-- =============================================================================
CREATE TABLE opening_result (
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),
    winning_rate_fraction NUMERIC,
    derived_base_amount_won NUMERIC(20, 0),
    derived_base_amount_currency TEXT,
    derived_base_amount_vat TEXT,
    observed_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision >= 1),
    observation_key TEXT NOT NULL REFERENCES raw_observation (observation_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notice_number, notice_round),
    CHECK ((derived_base_amount_won IS NULL) = (derived_base_amount_currency IS NULL)),
    CHECK ((derived_base_amount_won IS NULL) = (derived_base_amount_vat IS NULL))
);

-- =============================================================================
-- qualification_text — ① QualificationText canonical fact. 「최신 관측 우선」, 같은 이유로
-- 점유 가드를 두지 않는다.
-- =============================================================================
CREATE TABLE qualification_text (
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),
    raw_text TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision >= 1),
    observation_key TEXT NOT NULL REFERENCES raw_observation (observation_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notice_number, notice_round)
);

-- =============================================================================
-- collection_run — ①·⑥ 3A CollectionAccounting(3B 확장 포함) 그대로 + 항등식 CHECK. 배치
-- 마지막에 별도 트랜잭션으로 기록한다(D-3D-4).
-- =============================================================================
CREATE TABLE collection_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_date DATE NOT NULL,
    source_endpoint TEXT NOT NULL CHECK (source_endpoint <> ''),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    received INT NOT NULL CHECK (received >= 0),
    normalized INT NOT NULL CHECK (normalized >= 0),
    duplicate INT NOT NULL CHECK (duplicate >= 0),
    dropped INT NOT NULL CHECK (dropped >= 0),
    drop_reasons JSONB NOT NULL,
    source_total INT CHECK (source_total IS NULL OR source_total >= 0),
    pages_fetched INT NOT NULL CHECK (pages_fetched >= 0),
    truncated BOOLEAN NOT NULL,
    unknown_fields INT NOT NULL CHECK (unknown_fields >= 0),
    truncation_cause TEXT,
    quota_exceeded INT NOT NULL DEFAULT 0 CHECK (quota_exceeded >= 0),
    backoff_skipped INT NOT NULL DEFAULT 0 CHECK (backoff_skipped >= 0),
    inserted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (finished_at >= started_at),
    -- COL-06 항등식 — CollectionAccounting.init과 같은 불변식을 DB도 진다.
    CHECK (received = normalized + duplicate + dropped),
    -- H-3 결합 불변식 — CollectionAccounting.init과 동일.
    CHECK (truncated = (truncation_cause IS NOT NULL))
);

CREATE INDEX collection_run_reference_date_idx ON collection_run (reference_date);
