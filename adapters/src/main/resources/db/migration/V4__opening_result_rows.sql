-- M3/3E ③④⑤⑥ — 개찰 fact 슬롯(층 C) + 복수예비가격 자식 표(층 B, D-3E-2 (a)). V1~V3 를
-- 고치지 않는다(적용된 마이그레이션 수정 금지) — 이 파일만 신규.
--
-- **verifier r2 뒤 흡수(운영자 결정)** — 원래 `V4`·`V5`(H-1 provenance)·`V6`(M-1 공백 CHECK)
-- 셋으로 나뉘어 있었다. 셋 다 `origin/main` 에 push되지 않았고(적용 이력 없음, 실측)
-- Testcontainers 만 이 스키마를 쓴다(프로젝트 DB 컨테이너 없음) — 「적용된 마이그레이션을
-- 고치지 마라」 규율은 **적용된 것**이 대상이라 흡수는 그 규율을 어기지 않는다. 흡수 이유는
-- 셋으로 가른 이력 자체가 위험이었기 때문이다 — `V4` 만 적용된 데이터 있는 DB 에 `V5`(결측
-- provenance 를 거부하는 CHECK)를 적용할 수 없다는 것이 verifier 실증으로 확인됐다(오늘은
-- 도달 경로가 없으나, push 하면 그 이력이 영구화된다). 결과 스키마는 세 파일을 순서대로
-- 적용한 것과 **동일**해야 한다 — evidence 의 실측 대조가 그 동일성을 확인한다.
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
--
-- **provenance(kind+detail) 컬럼 짝**(verifier r1 H-1 뒤, 원 `V5`) — 최종낙찰금액·예정가격·
-- 기초금액 셋에 `notice` 표 관례(`ProvenanceCodec`)를 그대로 적용한다. 저장한 provenance를
-- 그대로 왕복시킨다(상수를 씌우지 않는다). 권위 가드(`guard_authoritative_slot`,
-- `provenance_authority` 참조) 적용 여부는 3D 설계 변경이라 이 slice에서 열지 않는다 —
-- 가드 관례는 `guard_existence_and_freshness` 현행 유지, provenance 축은 companion
-- 컬럼으로만 추가한다(존재+신선도만 진다 — 알려진 제한, `OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`).
-- =============================================================================
ALTER TABLE opening_result
    ADD COLUMN final_award_amount_won NUMERIC(20, 0),
    ADD COLUMN final_award_amount_currency TEXT,
    ADD COLUMN final_award_amount_provenance TEXT,
    ADD COLUMN final_award_amount_provenance_detail TEXT,
    ADD COLUMN final_award_company_name TEXT,
    ADD COLUMN participant_count INT,
    ADD COLUMN progress_division TEXT,
    ADD COLUMN planned_price_won NUMERIC(20, 0),
    ADD COLUMN planned_price_currency TEXT,
    ADD COLUMN planned_price_provenance TEXT,
    ADD COLUMN planned_price_provenance_detail TEXT,
    ADD COLUMN opening_base_amount_won NUMERIC(20, 0),
    ADD COLUMN opening_base_amount_currency TEXT,
    ADD COLUMN opening_base_amount_vat TEXT,
    ADD COLUMN opening_base_amount_provenance TEXT,
    ADD COLUMN opening_base_amount_provenance_detail TEXT,
    ADD COLUMN total_reserve_price_candidate_count INT,
    ADD COLUMN actual_opening_at TIMESTAMPTZ,
    ADD CONSTRAINT opening_result_final_award_amount_currency_pair
        CHECK ((final_award_amount_won IS NULL) = (final_award_amount_currency IS NULL)),
    ADD CONSTRAINT opening_result_final_award_amount_provenance_pair
        CHECK ((final_award_amount_won IS NULL) = (final_award_amount_provenance IS NULL)),
    ADD CONSTRAINT opening_result_planned_price_currency_pair
        CHECK ((planned_price_won IS NULL) = (planned_price_currency IS NULL)),
    ADD CONSTRAINT opening_result_planned_price_provenance_pair
        CHECK ((planned_price_won IS NULL) = (planned_price_provenance IS NULL)),
    ADD CONSTRAINT opening_result_opening_base_amount_currency_pair
        CHECK ((opening_base_amount_won IS NULL) = (opening_base_amount_currency IS NULL)),
    ADD CONSTRAINT opening_result_opening_base_amount_vat_pair
        CHECK ((opening_base_amount_won IS NULL) = (opening_base_amount_vat IS NULL)),
    ADD CONSTRAINT opening_result_opening_base_amount_provenance_pair
        CHECK ((opening_base_amount_won IS NULL) = (opening_base_amount_provenance IS NULL)),
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
        'final_award_amount_won', 'final_award_amount_currency',
        'final_award_amount_provenance', 'final_award_amount_provenance_detail');

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
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'planned_price_won', 'planned_price_currency',
        'planned_price_provenance', 'planned_price_provenance_detail');

CREATE TRIGGER guard_opening_result_opening_base_amount
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'opening_base_amount_won', 'opening_base_amount_currency', 'opening_base_amount_vat',
        'opening_base_amount_provenance', 'opening_base_amount_provenance_detail');

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
-- **순번 CHECK(verifier r1 M-1 · r2 N-1 뒤)** — 순번은 「공백만으로 이루어진 값」을 거부해야
-- 한다. 요구되는 것은 표현이 아니라 성질이다: DB CHECK과 Kotlin
-- `String.isNotBlank()`(`OpeningReservePriceRow.init`이 쓴다)가 **같은 입력 집합**에 같은
-- 답을 내야 한다. Kotlin `Char.isWhitespace()`는 `Character.isWhitespace()` OR
-- `Character.isSpaceChar()`의 합집합이라(JDK), ASCII 공백·탭·개행류(U+0009-U+000D,
-- U+001C-U+001F)뿐 아니라 유니코드 공백 분리자(U+00A0 NBSP·U+1680·U+2000-U+200A·U+2028·
-- U+2029·U+202F·U+205F·U+3000 전각 공백)까지 「공백」으로 본다. 아래 정규식이 그 합집합을
-- 그대로 나열한다(문서가 선언하지 않는 새 제약을 만들지 않는다 — 겨누는 것은 공백뿐이고
-- 숫자 전용 같은 발명은 없다). `btrim()`(ASCII 공백만 깎는다)은 그 부분집합만 막아 N-1로
-- 남았던 자리다 — 이제 한 자리(신설 표) 안에서 처음부터 이 정규식으로 선언한다.
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
    reserve_price_sequence TEXT NOT NULL
        CHECK (reserve_price_sequence ~ '[^\u0009-\u000D\u001C-\u001F\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]'),
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
