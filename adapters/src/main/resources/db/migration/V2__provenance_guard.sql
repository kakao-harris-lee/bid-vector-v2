-- M3/3D ④ — 점유 가드의 DB 측 실물(D-3D-2 (a)). provenance_authority seed +
-- 일반화된 BEFORE UPDATE 가드 함수 둘(TG_ARGV로 값 컬럼을 받는다, to_jsonb(NEW/OLD)로 동적
-- 필드 접근 — 3D 설계 검토 「구성상 닫힘」 표 ④) + AFTER UPDATE notice_audit 자동 삽입 +
-- revision 증가.
--
-- verifier r1(F-1·F-4·F-5) 뒤 개정 — 원래 판(값이 있고 provenance가 내려갈 때만 거부)은
-- 「값은 바뀌었는데 provenance는 그대로」인 write(F-1)와 「비권위→비권위」(F-4)를 놓쳤다.
-- 새 규칙은 축 하나당 셋을 함께 본다:
--   (1) 존재 가드 — 새 값이 NULL이면 거부.
--   (2) 점유 가드 — 새 provenance가 권위 없으면 거부(Kotlin mayOverwrite와 같은 술어 —
--       기존 provenance의 권위는 더 이상 보지 않는다. 「이미 값이 있다」가 전제이므로
--       그 사실 하나가 「새 유입은 권위 있어야 한다」를 결정한다).
--   (3) 신선도 가드(F-1) — 값이 바뀌었는데 observation_key가 그대로면 거부. 「같은 관측을
--       다시 실었다」는 것을 그 값이 왜 바뀌었는지 설명하지 못하면 값 변경 자체가 성립하지
--       않는다.
--
-- verifier r2(N-1, 회귀) 뒤 재개정 — 위 (1)~(3)은 **값이 실제로 바뀔 때만** 적용됐는데, 그
-- 단락이 「값은 그대로 두고 provenance만 강등」(직접 SQL이 새 관측 없이 `*_provenance`만
-- `UNDECLARED`로 바꿈)을 놓쳤다 — provenance는 가드가 보는 「값」이 아니라 보조 입력일
-- 뿐이라, 값만 고정하면 provenance를 어느 값으로든 바꿀 수 있었다. 이제 단락 조건을
-- 「값과 provenance 가 **둘 다** 안 바뀔 때만」으로 좁힌다 — 즉 이 축에 실제로 손이 닿았는지
-- (값 또는 provenance 중 하나라도 변경)를 먼저 보고, 손이 닿았으면 (1)(2)를 항상 적용한다.
-- (3) 신선도 가드는 여전히 「값이 바뀐 경우」에만 새 관측을 요구한다 — provenance만 바뀌고
-- 값은 그대로인 write는 (2) 점유 가드가 이미 막으므로(권위 강등이면 거부) 별도 신선도
-- 요구가 필요 없다.

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
-- N-2(verifier r2, medium) 뒤 — TG_ARGV[2..]는 같은 축의 동반 컬럼(통화·과세 등)이다.
-- 값·provenance가 그대로여도 동반 컬럼만 바뀌면(예: base_amount_vat만 EXCLUSIVE→INCLUSIVE)
-- 같은 금액 사실의 경제적 의미가 달라진다 — 축 변경 여부 판정에 반드시 포함한다.
CREATE FUNCTION guard_authoritative_slot() RETURNS trigger AS $guard$
DECLARE
    value_col TEXT := TG_ARGV[0];
    provenance_col TEXT := TG_ARGV[1];
    old_value TEXT := (to_jsonb(OLD) ->> value_col);
    new_value TEXT := (to_jsonb(NEW) ->> value_col);
    old_provenance TEXT := (to_jsonb(OLD) ->> provenance_col);
    new_provenance TEXT := (to_jsonb(NEW) ->> provenance_col);
    new_authoritative BOOLEAN;
    companion_col TEXT;
    axis_changed BOOLEAN;
BEGIN
    IF old_value IS NULL THEN
        RETURN NEW;
    END IF;

    -- N-1(verifier r2, 회귀) — 값만 보면 「값은 그대로 두고 provenance만 강등」을 놓친다.
    -- 값과 provenance 가 둘 다 안 바뀔 때만(이 축에 손이 안 닿은 wide UPDATE) 통과시킨다.
    axis_changed := old_value IS DISTINCT FROM new_value OR old_provenance IS DISTINCT FROM new_provenance;

    IF NOT axis_changed THEN
        FOR companion_col IN SELECT unnest(TG_ARGV[2:TG_NARGS - 1]) LOOP
            IF (to_jsonb(OLD) ->> companion_col) IS DISTINCT FROM (to_jsonb(NEW) ->> companion_col) THEN
                axis_changed := TRUE;
            END IF;
        END LOOP;
    END IF;

    IF NOT axis_changed THEN
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

    -- 신선도 가드 — N-2(verifier r2) 뒤 값만이 아니라 축 전체(값·provenance·동반 컬럼)가
    -- 대상이다. 값이 안 바뀌어도 동반 컬럼(과세 구분 등)만 바뀌면 점유 가드(위)는 새
    -- provenance가 여전히 권위 있으면 그냥 통과시키므로(권위는 그대로다), 신선도 가드가
    -- 없으면 「같은 관측인데 과세 구분만 슬쩍 바뀐다」가 열린다 — axis_changed 전체에
    -- 새 관측을 요구해 막는다.
    IF axis_changed AND NEW.observation_key = OLD.observation_key THEN
        RAISE EXCEPTION 'guard_authoritative_slot: % 축 변경은 새 observation_key(새 관측)를 동반해야 한다', value_col
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$guard$ LANGUAGE plpgsql;

-- P-1(verifier r3, medium) — 동반 컬럼에 provenance의 detail 절반
-- (`*_provenance_detail`)과 estimated_amount의 sourceKey를 더한다. 함수 본문은 그대로다
-- (인자 확장만) — `ProvenanceCodec`는 provenance를 kind(`*_provenance`)+detail
-- (`*_provenance_detail`) 두 컬럼에 나눠 담는데, kind만 가드가 보면 값의 절반이
-- 무방비였다(예: FilledFromBudgetKey의 RawKey가 detail에 산다).
CREATE TRIGGER guard_notice_base_amount
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot(
        'base_amount_won', 'base_amount_provenance',
        'base_amount_currency', 'base_amount_vat', 'base_amount_provenance_detail');

CREATE TRIGGER guard_notice_estimated_amount
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot(
        'estimated_amount_won', 'estimated_amount_provenance',
        'estimated_amount_currency', 'estimated_amount_vat',
        'estimated_amount_provenance_detail', 'estimated_amount_source_key');

-- allocated_budget은 통화·과세 동반 컬럼이 스키마에 없다 — 통화는 항상 KRW로 고정이라
-- 컬럼화하지 않았고, vat 축도 두지 않았다(V1__schema.sql). provenance detail은 있다(P-1).
CREATE TRIGGER guard_notice_allocated_budget
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_authoritative_slot(
        'allocated_budget_won', 'allocated_budget_provenance', 'allocated_budget_provenance_detail');

-- =============================================================================
-- guard_existence_and_freshness() — provenance 축이 없는 값 컬럼용(F-5). 존재 가드(1)와
-- 신선도 가드(3)만 진다 — 「권위」 개념 자체가 없는 축이라 (2)는 적용되지 않는다.
-- TG_ARGV[0] = 값 컬럼 이름. TG_ARGV[1..] = 동반 컬럼(P-1, verifier r3) —
-- guard_authoritative_slot의 TG_ARGV[2..]와 같은 대칭 확장이다. 함수 본문은 그대로고
-- 인자만 늘린다.
-- =============================================================================
CREATE FUNCTION guard_existence_and_freshness() RETURNS trigger AS $guard2$
DECLARE
    value_col TEXT := TG_ARGV[0];
    old_value TEXT := (to_jsonb(OLD) ->> value_col);
    new_value TEXT := (to_jsonb(NEW) ->> value_col);
    companion_col TEXT;
    axis_changed BOOLEAN;
BEGIN
    IF old_value IS NULL THEN
        RETURN NEW;
    END IF;

    axis_changed := old_value IS DISTINCT FROM new_value;

    IF NOT axis_changed THEN
        FOR companion_col IN SELECT unnest(TG_ARGV[1:TG_NARGS - 1]) LOOP
            IF (to_jsonb(OLD) ->> companion_col) IS DISTINCT FROM (to_jsonb(NEW) ->> companion_col) THEN
                axis_changed := TRUE;
            END IF;
        END LOOP;
    END IF;

    IF NOT axis_changed THEN
        RETURN NEW;
    END IF;

    IF new_value IS NULL THEN
        RAISE EXCEPTION 'guard_existence_and_freshness: % 는 값이 있는 자리를 비울 수 없다(존재 가드)', value_col
            USING ERRCODE = 'P0001';
    END IF;

    IF NEW.observation_key = OLD.observation_key THEN
        RAISE EXCEPTION 'guard_existence_and_freshness: % 축 변경은 새 observation_key(새 관측)를 동반해야 한다', value_col
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$guard2$ LANGUAGE plpgsql;

-- P-1(verifier r3) — floor_rate_origin_kind·_detail은 floor_rate_fraction의 출처 라벨이다
-- (base_amount의 *_provenance_detail과 같은 자리). 값은 가드가 무는데 출처만 안 물면
-- FloorRateOriginCodec.decode가 다른 FloorRateOrigin을 낸다.
CREATE TRIGGER guard_notice_floor_rate
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'floor_rate_fraction', 'floor_rate_origin_kind', 'floor_rate_origin_detail');

-- N-2(verifier r2, medium) — status는 provenance 축이 없는 상태 라벨이라(권위 계층 개념
-- 자체가 없음) guard_authoritative_slot이 아니라 이 함수를 쓴다. status는 NOT NULL이라
-- 존재 가드는 사실상 발동하지 않고(제약이 이미 막는다), 신선도 가드가 실질 방어다 —
-- 직접 SQL로 status만 위조하면(observation_key 그대로) 거부된다.
CREATE TRIGGER guard_notice_status
    BEFORE UPDATE ON notice
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('status');

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
