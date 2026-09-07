-- M3/3D ④ — 점유 가드의 DB 측 실물(D-3D-2 (a)). provenance_authority seed +
-- 일반화된 BEFORE UPDATE 가드 함수 둘(TG_ARGV로 값 컬럼을 받는다, to_jsonb(NEW/OLD)로 동적
-- 필드 접근 — 3D 설계 검토 「구성상 닫힘」 표 ④) + AFTER UPDATE notice_audit 자동 삽입 +
-- revision 증가.
--
-- verifier r1(F-1·F-4·F-5) 뒤 개정 — 원래 판(값이 있고 provenance가 내려갈 때만 거부)은
-- 「값은 바뀌었는데 provenance는 그대로」인 write(F-1)와 「비권위→비권위」(F-4)를 놓쳤다.
-- 새 규칙은 축 하나당 셋을 함께 본다 — **값이 실제로 바뀔 때만** 적용된다(값이 그대로인
-- wide UPDATE의 다른 컬럼 변경은 건드리지 않는다):
--   (1) 존재 가드 — 새 값이 NULL이면 거부.
--   (2) 점유 가드 — 새 provenance가 권위 없으면 거부(Kotlin mayOverwrite와 같은 술어 —
--       기존 provenance의 권위는 더 이상 보지 않는다. 「이미 값이 있다」가 전제이므로
--       그 사실 하나가 「새 유입은 권위 있어야 한다」를 결정한다).
--   (3) 신선도 가드(F-1) — observation_key가 그대로면 거부. 「같은 관측을 다시 실었다」는
--       것을 그 값이 왜 바뀌었는지 설명하지 못하면 값 변경 자체가 성립하지 않는다.

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
-- guard_authoritative_slot() — provenance 축이 있는 금액 컬럼용(base/estimated/allocated).
-- TG_ARGV[0] = 값 컬럼 이름, TG_ARGV[1] = provenance 컬럼 이름.
-- =============================================================================
CREATE FUNCTION guard_authoritative_slot() RETURNS trigger AS $guard$
DECLARE
    value_col TEXT := TG_ARGV[0];
    provenance_col TEXT := TG_ARGV[1];
    old_value TEXT := (to_jsonb(OLD) ->> value_col);
    new_value TEXT := (to_jsonb(NEW) ->> value_col);
    new_provenance TEXT := (to_jsonb(NEW) ->> provenance_col);
    new_authoritative BOOLEAN;
BEGIN
    IF old_value IS NULL THEN
        RETURN NEW;
    END IF;

    IF old_value IS NOT DISTINCT FROM new_value THEN
        RETURN NEW;
    END IF;

    IF new_value IS NULL THEN
        RAISE EXCEPTION 'guard_authoritative_slot: % 는 값이 있는 자리를 비울 수 없다(존재 가드)', value_col
            USING ERRCODE = 'P0001';
    END IF;

    SELECT authoritative INTO new_authoritative FROM provenance_authority WHERE provenance = new_provenance;
    IF NOT COALESCE(new_authoritative, FALSE) THEN
        RAISE EXCEPTION
            'guard_authoritative_slot: % 는 이미 값이 있는 자리라 권위 있는 유입(provenance)만 값을 바꿀 수 있다(요청 provenance=%)',
            value_col, new_provenance
            USING ERRCODE = 'P0001';
    END IF;

    IF NEW.observation_key = OLD.observation_key THEN
        RAISE EXCEPTION 'guard_authoritative_slot: % 값 변경은 새 observation_key(새 관측)를 동반해야 한다', value_col
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

-- =============================================================================
-- guard_existence_and_freshness() — provenance 축이 없는 값 컬럼용(F-5). 존재 가드(1)와
-- 신선도 가드(3)만 진다 — 「권위」 개념 자체가 없는 축이라 (2)는 적용되지 않는다.
-- TG_ARGV[0] = 값 컬럼 이름.
-- =============================================================================
CREATE FUNCTION guard_existence_and_freshness() RETURNS trigger AS $guard2$
DECLARE
    value_col TEXT := TG_ARGV[0];
    old_value TEXT := (to_jsonb(OLD) ->> value_col);
    new_value TEXT := (to_jsonb(NEW) ->> value_col);
BEGIN
    IF old_value IS NULL THEN
        RETURN NEW;
    END IF;

    IF old_value IS NOT DISTINCT FROM new_value THEN
        RETURN NEW;
    END IF;

    IF new_value IS NULL THEN
        RAISE EXCEPTION 'guard_existence_and_freshness: % 는 값이 있는 자리를 비울 수 없다(존재 가드)', value_col
            USING ERRCODE = 'P0001';
    END IF;

    IF NEW.observation_key = OLD.observation_key THEN
        RAISE EXCEPTION 'guard_existence_and_freshness: % 값 변경은 새 observation_key(새 관측)를 동반해야 한다', value_col
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$guard2$ LANGUAGE plpgsql;

CREATE TRIGGER guard_notice_floor_rate
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('floor_rate_fraction');

-- F-5 — opening_result·qualification_text는 provenance 축이 없다(파생/최신-관측-우선).
-- 존재 가드 + 신선도 가드는 「전 금액 축」(§5.1)에 여전히 적용된다: 이미 있는 값을 같은
-- observation_key로 슬쩍 바꾸거나 NULL로 지우는 직접 SQL을 막는다. 정상 경로(ON CONFLICT
-- ... RETURNING)는 매 호출마다 새 raw 관측의 observation_key를 실어 이 가드를 자연히 지난다.
CREATE TRIGGER guard_opening_result_winning_rate
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('winning_rate_fraction');

CREATE TRIGGER guard_opening_result_derived_base_amount
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('derived_base_amount_won');

CREATE TRIGGER guard_qualification_text_raw_text
    BEFORE UPDATE ON qualification_text
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('raw_text');

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
--
-- F-6 — SECURITY DEFINER다. bidvector_app은 notice_audit에 INSERT 권한이 없다(아래 GRANT) —
-- 이 함수가 소유자(마이그레이션 실행 역할, 슈퍼유저)의 권한으로 대신 삽입한다. app 역할이
-- 직접 INSERT로 위조 이력을 넣는 경로를 막고, audit 행은 이 트리거를 통해서만 생긴다.
-- search_path를 고정해 SECURITY DEFINER의 표준 위험(경로 하이재킹)을 막는다.
-- =============================================================================
CREATE FUNCTION notice_audit_insert() RETURNS trigger AS $audit$
BEGIN
    INSERT INTO notice_audit (notice_number, notice_round, revision, observation_key, reason, previous_row)
    VALUES (OLD.notice_number, OLD.notice_round, NEW.revision, NEW.observation_key, 'REVISION_UPDATE', to_jsonb(OLD));
    RETURN NEW;
END;
$audit$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER notice_audit_insert_trigger
    AFTER UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION notice_audit_insert();

-- =============================================================================
-- 최소 권한 애플리케이션 역할(D-3D-2, 우회 (8)) — LOGIN도 비밀번호도 두지 않는다. test·운영
-- 모두 이미 인증된 세션에서 `SET ROLE bidvector_app`으로 전환한다(권한 경계 확인용 — 비밀
-- 문자열을 evidence에 남기지 않는다).
--
-- F-6 — notice_audit은 SELECT만(INSERT 없음, 트리거가 SECURITY DEFINER로 대신 쓴다).
-- rejected_write·collection_run은 Kotlin이 직접 INSERT하므로(트리거가 아니다) 그대로 둔다.
-- =============================================================================
CREATE ROLE bidvector_app NOLOGIN;

GRANT SELECT, INSERT, UPDATE ON notice, opening_result, qualification_text TO bidvector_app;
GRANT SELECT, INSERT ON raw_observation TO bidvector_app;
GRANT SELECT ON notice_audit TO bidvector_app;
GRANT SELECT, INSERT ON rejected_write, collection_run TO bidvector_app;
GRANT SELECT ON provenance_authority TO bidvector_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bidvector_app;
