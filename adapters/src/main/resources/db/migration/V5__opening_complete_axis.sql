-- M3/3F — 개찰완료 축 부모 슬롯(층 C, D-3F-4 (a)). D-3F-3 해소로 투찰자별 canonical 자식
-- 표를 만들지 않는다 — 부모 컬럼만 더한다. V1~V4 를 고치지 않는다(V4 는 3E 종결과 함께
-- push 됐다 — 되쓸 수 없다).
--
-- 「최신 관측 우선」(권위 계층 없음, D-M3-7 OPEN-DIC-09) — opening_result 의 기존 관례
-- (guard_existence_and_freshness) 를 그대로 잇는다. 이 축엔 provenance 컬럼 자체가 없다
-- (opening_reserve_price 와 같은 결정, NoticeFacts.kt ObservedBidAmount·OpeningRankOneBid
-- 가 provenance 를 안 갖는 것과 대응).
--
-- 두 그룹.
--   (1) opening_rank_one_* — OpeningRankOneOutcome 을 그대로 편다. kind 가 NULL 이면
--       NotObserved(투찰 행 자체가 관측되지 않음) — 저장하지 않는다(다른 상태처럼 값이
--       없으면 컬럼이 NULL 인 기존 관례를 따른다). kind='RANK_MISSING'/'RANK_DUPLICATED' 는
--       순위 1 을 특정할 수 없었다는 명시적 회계이지 조용한 NULL 이 아니다.
--   (2) draw_numbers_* — DrawNumberObservation 을 그대로 편다. kind='RANGE_CHECK_UNAVAILABLE'
--       은 총예가건수를 몰라 범위 검사를 못 했다는 명시적 결과다(§1.9.7 — 이 오퍼레이션
--       응답에 총예가건수가 없다). 범위(1..total_reserve_price_candidate_count) 는 이미
--       부모에 있는 값(3E 슬롯)에서 재구성하므로 별도로 저장하지 않는다(중복 금지).
-- =============================================================================
ALTER TABLE opening_result
    ADD COLUMN opening_rank_one_kind TEXT,
    ADD COLUMN opening_rank_one_duplicate_count INT,
    ADD COLUMN opening_rank_one_bidder_name TEXT,
    ADD COLUMN opening_rank_one_bid_amount_won NUMERIC(20, 0),
    ADD COLUMN opening_rank_one_bid_amount_currency TEXT,
    ADD COLUMN opening_rank_one_bid_rate_fraction NUMERIC,
    ADD COLUMN draw_numbers_kind TEXT,
    ADD COLUMN draw_numbers INT[],
    ADD CONSTRAINT opening_result_opening_rank_one_kind_enum
        CHECK (opening_rank_one_kind IN ('RANK_MISSING', 'RANK_DUPLICATED', 'DETERMINED')),
    -- kind='DETERMINED' 는 bidderName 이 있다는 것과 정확히 동치다(OpeningRankOneBid.bidderName
    -- 은 blank 를 타입으로 거부한다) — IS NOT DISTINCT FROM 으로 kind가 NULL(NotObserved)일
    -- 때도 bidderName 이 NULL 이어야 함을 함께 진다(단순 `=` 는 NULL 비교가 NULL 이 되어 이
    -- 경우를 놓친다).
    ADD CONSTRAINT opening_result_opening_rank_one_determined_pair
        CHECK ((opening_rank_one_kind IS NOT DISTINCT FROM 'DETERMINED') = (opening_rank_one_bidder_name IS NOT NULL)),
    ADD CONSTRAINT opening_result_opening_rank_one_duplicated_pair
        CHECK (
            (opening_rank_one_kind IS NOT DISTINCT FROM 'RANK_DUPLICATED')
                = (opening_rank_one_duplicate_count IS NOT NULL)
        ),
    ADD CONSTRAINT opening_result_opening_rank_one_bid_amount_currency_pair
        CHECK ((opening_rank_one_bid_amount_won IS NULL) = (opening_rank_one_bid_amount_currency IS NULL)),
    ADD CONSTRAINT opening_result_opening_rank_one_duplicate_count_min
        CHECK (opening_rank_one_duplicate_count IS NULL OR opening_rank_one_duplicate_count >= 2),
    ADD CONSTRAINT opening_result_draw_numbers_kind_enum
        CHECK (draw_numbers_kind IN ('VERIFIED', 'OUT_OF_RANGE', 'RANGE_CHECK_UNAVAILABLE')),
    ADD CONSTRAINT opening_result_draw_numbers_pair
        CHECK ((draw_numbers_kind IS NULL) = (draw_numbers IS NULL));

CREATE TRIGGER guard_opening_result_opening_rank_one
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'opening_rank_one_kind', 'opening_rank_one_duplicate_count', 'opening_rank_one_bidder_name',
        'opening_rank_one_bid_amount_won', 'opening_rank_one_bid_amount_currency',
        'opening_rank_one_bid_rate_fraction');

CREATE TRIGGER guard_opening_result_draw_numbers
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness('draw_numbers_kind', 'draw_numbers');
