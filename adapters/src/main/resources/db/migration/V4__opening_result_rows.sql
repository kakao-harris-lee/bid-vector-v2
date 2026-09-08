-- M3/3E ③④⑤⑥ — 개찰 fact 슬롯(층 C) + 복수예비가격 자식 표(층 B, D-3E-2 (a)). V1~V3 를
-- 고치지 않는다(적용된 마이그레이션 수정 금지) — 이 파일만 신규.
--
-- 팀리드 실측 정정(§1.9.7, 운영자 승인 아래 읽기 전용 호출 9회, 8건 23행 표본,
-- 2026-09-01~09-07 창) — 예정가격(plnprc)·기초금액(bssamt)·총예가건수(totRsrvtnPrceNum)·
-- 실개찰일시(rlOpengDt)는 응답이 행마다 반복해 실어도 **공고 층**(부모 opening_result)이다.
-- 순번(compnoRsrvtnPrceSno) 공백은 총예가건수가 1(단수 예가)일 때만 관측됐다 — 그 경우 자식
-- 행은 0개이지만 부모의 예정가격·기초금액은 이 분리 덕에 잃지 않는다.

-- =============================================================================
-- opening_result 신규 컬럼 — 층 C 부모 fact 슬롯(추가만, D-3E-4 (a)). 「최신 관측 우선」
-- (권위 계층 없음, D-M3-7 OPEN-DIC-09)이라 guard_existence_and_freshness(V2)를 그대로 쓴다 —
-- opening_result의 기존 winning_rate_fraction·derived_base_amount_won 가드와 같은 관례.
-- =============================================================================
ALTER TABLE opening_result
    ADD COLUMN final_award_amount_won NUMERIC(20, 0),
    ADD COLUMN final_award_amount_currency TEXT,
    ADD COLUMN final_award_company_name TEXT,
    ADD COLUMN participant_count INT,
    ADD COLUMN progress_division TEXT,
    ADD COLUMN planned_price_won NUMERIC(20, 0),
    ADD COLUMN planned_price_currency TEXT,
    ADD COLUMN opening_base_amount_won NUMERIC(20, 0),
    ADD COLUMN opening_base_amount_currency TEXT,
    ADD COLUMN opening_base_amount_vat TEXT,
    ADD COLUMN total_reserve_price_candidate_count INT,
    ADD COLUMN actual_opening_at TIMESTAMPTZ,
    ADD CONSTRAINT opening_result_final_award_amount_currency_pair
        CHECK ((final_award_amount_won IS NULL) = (final_award_amount_currency IS NULL)),
    ADD CONSTRAINT opening_result_planned_price_currency_pair
        CHECK ((planned_price_won IS NULL) = (planned_price_currency IS NULL)),
    ADD CONSTRAINT opening_result_opening_base_amount_currency_pair
        CHECK ((opening_base_amount_won IS NULL) = (opening_base_amount_currency IS NULL)),
    ADD CONSTRAINT opening_result_opening_base_amount_vat_pair
        CHECK ((opening_base_amount_won IS NULL) = (opening_base_amount_vat IS NULL)),
    ADD CONSTRAINT opening_result_participant_count_non_negative
        CHECK (participant_count IS NULL OR participant_count >= 0),
    ADD CONSTRAINT opening_result_total_reserve_price_candidate_count_non_negative
        CHECK (total_reserve_price_candidate_count IS NULL OR total_reserve_price_candidate_count >= 0);

-- final_award_amount·planned_price는 AwardAmount·YegaAmount(vatTreatment 항상 UNKNOWN 고정,
-- shared-kernel Money.kt)라 vat 컬럼을 따로 두지 않는다 — 유일한 값을 컬럼화하면 중복이다.
-- opening_base_amount는 BaseAmount(vatTreatment 가변)라 vat 컬럼이 있다 — notice 축과 같은 이유.

CREATE TRIGGER guard_opening_result_final_award_amount
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'final_award_amount_won', 'final_award_amount_currency');

CREATE TRIGGER guard_opening_result_final_award_company_name
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('final_award_company_name');

CREATE TRIGGER guard_opening_result_participant_count
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('participant_count');

CREATE TRIGGER guard_opening_result_progress_division
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('progress_division');

CREATE TRIGGER guard_opening_result_planned_price
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('planned_price_won', 'planned_price_currency');

CREATE TRIGGER guard_opening_result_opening_base_amount
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'opening_base_amount_won', 'opening_base_amount_currency', 'opening_base_amount_vat');

CREATE TRIGGER guard_opening_result_total_reserve_price_candidate_count
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('total_reserve_price_candidate_count');

CREATE TRIGGER guard_opening_result_actual_opening_at
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('actual_opening_at');

-- =============================================================================
-- opening_reserve_price — 층 B 자식 표(D-3E-2 (a)). 「복수예비가격 후보」만 남는다(§1.9.7
-- 정정 이후 — 공고 층 값은 위 opening_result 컬럼으로 옮겨갔다). 기본키는 부모
-- (notice_number, notice_round) + reserve_price_sequence(compnoRsrvtnPrceSno) — 순번이
-- **NOT NULL**이다(D-3E-1b (a)의 귀결). 순번 부재 행은 이 표에 지어낸 값 없이는 삽입 자체가
-- 불가능하다 — canonical 승격 거절이 스키마 층에서도 구조적으로 막힌다.
--
-- 「최신 관측 우선」(권위 계층 없음, opening_result와 같은 성질)이라
-- guard_authoritative_slot(provenance_authority 참조)이 아니라 guard_existence_and_freshness를
-- 쓴다 — 이 축에는 provenance 컬럼 자체가 없다(NoticeFacts.kt ReservePriceCandidateAmount가
-- provenance를 안 갖는 것과 같은 결정, 왕복 안정성 없는 필드를 만들지 않는다).
--
-- append-only 대상이 아니다(D-3E-3 (a)) — 15→12로 행이 준 재수집이 기존 행을 지우지 않고
-- 최신 관측만 갱신해야 한다(사라진 행은 응답에 없을 뿐 이 표에서 그대로 남는다). append-only
-- 는 raw_observation·notice_audit만의 성질이다(V3, 감사 원장).
-- =============================================================================
CREATE TABLE opening_reserve_price (
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),
    reserve_price_sequence TEXT NOT NULL CHECK (reserve_price_sequence <> ''),
    base_reserve_price_won NUMERIC(20, 0),
    base_reserve_price_currency TEXT,
    is_drawn BOOLEAN,
    draw_count INT CHECK (draw_count IS NULL OR draw_count >= 0),
    observed_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision >= 1),
    observation_key TEXT NOT NULL REFERENCES raw_observation (observation_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notice_number, notice_round, reserve_price_sequence),
    FOREIGN KEY (notice_number, notice_round) REFERENCES opening_result (notice_number, notice_round),
    CHECK ((base_reserve_price_won IS NULL) = (base_reserve_price_currency IS NULL))
);

CREATE TRIGGER guard_opening_reserve_price_base_reserve_price
    BEFORE UPDATE ON opening_reserve_price
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'base_reserve_price_won', 'base_reserve_price_currency');

CREATE TRIGGER guard_opening_reserve_price_is_drawn
    BEFORE UPDATE ON opening_reserve_price
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('is_drawn');

CREATE TRIGGER guard_opening_reserve_price_draw_count
    BEFORE UPDATE ON opening_reserve_price
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('draw_count');

GRANT SELECT, INSERT, UPDATE ON opening_reserve_price TO bidvector_app;
