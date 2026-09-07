-- M3/3D ④ — 점유 가드의 DB 측 실물(D-3D-2 (a)). provenance_authority seed +
-- 일반화된 BEFORE UPDATE 가드 함수 하나(TG_ARGV로 (값 컬럼, provenance 컬럼) 쌍을 받는다,
-- to_jsonb(NEW/OLD)로 동적 필드 접근 — 3D 설계 검토 「구성상 닫힘」 표 ④) + AFTER UPDATE
-- notice_audit 자동 삽입 + revision 증가.

-- =============================================================================
-- provenance_authority seed — bidvector.procurement.IS_AUTHORITATIVE(ResolvedBaseAmount.kt)
-- 와 값이 같아야 한다. Kotlin 쪽 test(ProvenanceAuthoritySeedTest)가 이 표를 읽어 대조한다
-- (3D 설계 검토 「구현 지침」 — 코드↔SQL 이중 선언은 허용하되 일치 test 필수).
-- =============================================================================
INSERT INTO provenance_authority (provenance, authoritative) VALUES
    ('PUBLISHED', TRUE),
    ('OPERATOR_DECLARED', TRUE),
    ('DERIVED_FROM_OPENING', FALSE),
    ('FILLED_FROM_BUDGET_KEY', FALSE),
    ('COPIED_FROM_BASE_AMOUNT', FALSE),
    ('UNDECLARED', FALSE);

-- =============================================================================
-- guard_authoritative_slot() — 일반화된 트리거 함수 하나(전 금액 축 공통, ④).
-- TG_ARGV[0] = 값 컬럼 이름, TG_ARGV[1] = provenance(또는 origin) 컬럼 이름.
--
-- 규칙 둘:
--   (1) 존재 가드 — 이미 값이 있는 자리를 NULL로 비울 수 없다.
--   (2) 점유 가드 — OLD가 권위 있고 NEW가 권위 없으면(provenance_authority 조회) 거부한다.
--       두 컬럼 값이 provenance_authority에 없으면(예: floor_rate의 origin 어휘) 기본
--       비권위(FALSE)이므로 (2)는 발동하지 않고 (1)만 적용된다 — floor_rate가 존재
--       가드만 받는 것은 이 기본값에서 자연히 나온다(별도 분기 없음).
-- =============================================================================
CREATE FUNCTION guard_authoritative_slot() RETURNS trigger AS $guard$
DECLARE
    value_col TEXT := TG_ARGV[0];
    provenance_col TEXT := TG_ARGV[1];
    old_value TEXT := (to_jsonb(OLD) ->> value_col);
    new_value TEXT := (to_jsonb(NEW) ->> value_col);
    old_provenance TEXT := (to_jsonb(OLD) ->> provenance_col);
    new_provenance TEXT := (to_jsonb(NEW) ->> provenance_col);
    old_authoritative BOOLEAN;
    new_authoritative BOOLEAN;
BEGIN
    IF old_value IS NULL THEN
        RETURN NEW;
    END IF;

    IF new_value IS NULL THEN
        RAISE EXCEPTION 'guard_authoritative_slot: % 는 값이 있는 자리를 비울 수 없다(존재 가드)', value_col
            USING ERRCODE = 'P0001';
    END IF;

    SELECT authoritative INTO old_authoritative FROM provenance_authority WHERE provenance = old_provenance;
    SELECT authoritative INTO new_authoritative FROM provenance_authority WHERE provenance = new_provenance;
    old_authoritative := COALESCE(old_authoritative, FALSE);
    new_authoritative := COALESCE(new_authoritative, FALSE);

    IF old_authoritative AND NOT new_authoritative THEN
        RAISE EXCEPTION 'guard_authoritative_slot: % 는 권위 값을 비권위 값(provenance=%)으로 덮을 수 없다',
            value_col, new_provenance
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$guard$ LANGUAGE plpgsql;

CREATE TRIGGER guard_notice_base_amount
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot('base_amount_won', 'base_amount_provenance');

CREATE TRIGGER guard_notice_estimated_amount
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot('estimated_amount_won', 'estimated_amount_provenance');

CREATE TRIGGER guard_notice_allocated_budget
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot('allocated_budget_won', 'allocated_budget_provenance');

CREATE TRIGGER guard_notice_floor_rate
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot('floor_rate_fraction', 'floor_rate_origin_kind');

-- =============================================================================
-- notice_revision_bump — revision은 트리거가 정한다. 입력값을 무시한다(우회 (4)).
-- =============================================================================
CREATE FUNCTION notice_revision_bump() RETURNS trigger AS $bump$
BEGIN
    NEW.revision := OLD.revision + 1;
    NEW.updated_at := now();
    RETURN NEW;
END;
$bump$ LANGUAGE plpgsql;

CREATE TRIGGER notice_revision_bump_trigger
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION notice_revision_bump();

-- =============================================================================
-- notice_audit_insert — AFTER UPDATE 자동 삽입(⑥). Kotlin이 잊어도 남는다 — NEW.revision은
-- 위 bump 트리거가 이미 증가시킨 값이다(같은 문 안에서 BEFORE 트리거가 먼저 적용된 NEW를
-- AFTER 트리거가 본다, PostgreSQL 트리거 순서 의미론).
-- =============================================================================
CREATE FUNCTION notice_audit_insert() RETURNS trigger AS $audit$
BEGIN
    INSERT INTO notice_audit (notice_number, notice_round, revision, observation_key, reason, previous_row)
    VALUES (OLD.notice_number, OLD.notice_round, NEW.revision, NEW.observation_key, 'REVISION_UPDATE', to_jsonb(OLD));
    RETURN NEW;
END;
$audit$ LANGUAGE plpgsql;

CREATE TRIGGER notice_audit_insert_trigger
    AFTER UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION notice_audit_insert();

-- =============================================================================
-- 최소 권한 애플리케이션 역할(D-3D-2, 우회 (8)) — LOGIN도 비밀번호도 두지 않는다. test·운영
-- 모두 이미 인증된 세션에서 `SET ROLE bidvector_app`으로 전환한다(권한 경계 확인용 — 비밀
-- 문자열을 evidence에 남기지 않는다).
-- =============================================================================
CREATE ROLE bidvector_app NOLOGIN;

GRANT SELECT, INSERT, UPDATE ON notice, opening_result, qualification_text TO bidvector_app;
GRANT SELECT, INSERT ON raw_observation TO bidvector_app;
GRANT SELECT, INSERT ON notice_audit, rejected_write, collection_run TO bidvector_app;
GRANT SELECT ON provenance_authority TO bidvector_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bidvector_app;
