-- M3/3F — 개찰완료 축 부모 슬롯(층 C, D-3F-4 (a)). D-3F-3 해소로 투찰자별 canonical 자식
-- 표를 만들지 않는다 — 부모 컬럼만 더한다. V1~V4 를 고치지 않는다(V4 는 3E 종결과 함께
-- push 됐다 — 되쓸 수 없다). `V5` 자신은 아직 push 되지 않았다 — verifier r1 뒤 수정 라운드는
-- `V6` 를 새로 만들지 않고 이 파일을 그대로 고친다(3E 가 같은 자리에서 세운 관례).
--
-- 「최신 관측 우선」(권위 계층 없음, D-M3-7 OPEN-DIC-09) — opening_result 의 기존 관례
-- (guard_existence_and_freshness) 를 그대로 잇는다. 이 축엔 provenance 컬럼 자체가 없다
-- (opening_reserve_price 와 같은 결정, NoticeFacts.kt ObservedBidAmount·OpeningRankOneBid
-- 가 provenance 를 안 갖는 것과 대응).
--
-- 두 그룹, 각각 **kind + 관측 시각(observed_at)을 포함한 값 컬럼 전부가 한 축**이다
-- (verifier r1 F-1·F-2 뒤 — 아래 UPSERT 술어가 이 축을 통째로 교체하거나 통째로 보존한다).
--   (1) opening_rank_one_* — OpeningRankOneOutcome 을 그대로 편다. kind 가 NULL 이면
--       NotObserved(투찰 행 자체가 관측되지 않음) — 저장하지 않는다. kind='RANK_MISSING'/
--       'RANK_DUPLICATED' 는 순위 1 을 특정할 수 없었다는 명시적 회계이지 조용한 NULL 이
--       아니다. `opening_rank_one_observed_at`(F-2) 은 이 축 자신이 마지막으로 관측된
--       시각 — 부모 `observed_at`(관측 전체의 시각)과 다르다. 3E `OpeningReservePriceRow
--       .observedAt`과 같은 이유·같은 형태다: 재수집이 이 축을 안 실으면(NotObserved) 옛
--       값과 옛 관측 시각이 함께 보존되고, 소비자가 부모의 최신 `observed_at`과 이 값을
--       비교해 스스로 낡음을 판정한다(파생 플래그 컬럼을 만들지 않는다).
--   (2) draw_numbers_* — DrawNumberObservation 을 그대로 편다. kind='RANGE_CHECK_UNAVAILABLE'
--       은 총예가건수를 몰라 범위 검사를 못 했다는 명시적 결과다(§1.9.7 — 이 오퍼레이션
--       응답에 총예가건수가 없다). `draw_numbers_observed_at`(F-2) 은 축별 관측 시각이다.
--
--       **verifier r2 N-1 뒤 — `draw_numbers_valid_range_max`.** r1 F-3 은 `OutOfRange`
--       의 `validRange` 를 부모의 `total_reserve_price_candidate_count`(3E 슬롯, 별도
--       COALESCE 축)에서 읽기 시점에 재구성하고, 그 재구성이 항상 성립하도록 CHECK 로
--       두 축을 묶었다. 그런데 `INSERT ... ON CONFLICT` 의 CHECK 는 **병합 뒤 행이 아니라
--       들어오는 제안 tuple** 에 걸린다(PostgreSQL 관용구 실측) — 그래서 부모가 총예가건수를
--       **이미 갖고 있어도**, 개찰완료 관측이 그 값을 다시 싣지 않으면(관례상 관측하지 않은
--       축은 안 싣는다, 다른 모든 컬럼이 COALESCE 를 쓰는 이유와 같다) 정상 저장이 거부됐다.
--       뿌리는 **`OutOfRange` 가 자기 판정 범위를 값으로 나르지 않았다는 것**이다 — 판정에
--       필요한 정보(총예가건수)를 저장 시점에 다른 축에서 빌려 오려 했다. `OutOfRange` 는
--       이미 Kotlin 타입에서 `validRange` 를 필수 생성자 인자로 받는다(그 값 없이는
--       `OutOfRange` 자체를 만들 수 없다) — 저장도 그 값을 **이 축 자신의 컬럼**으로 그대로
--       실어 tuple 을 자기 완결로 만든다. `validRange.first` 는 1-기반 인덱스라 항상 1이고
--       (verifier r3 M-1 뒤 — `OutOfRange.init` 의 `require` 가 이제 그것을 타입으로
--       강제한다, `NoticeFacts.kt` 참고) 저장하지 않는다(중복 금지) — `validRange.last`
--       (위 상한)만 싣는다. 그러면 draw_numbers
--       축의 다른 컬럼들과 같은 자리(같은 tuple 안)에서 페어가 성립해, 총예가건수가 이
--       관측에 실려 왔는지 여부와 무관하게 저장이 성립한다.
-- =============================================================================
ALTER TABLE opening_result
    ADD COLUMN opening_rank_one_kind TEXT,
    ADD COLUMN opening_rank_one_duplicate_count INT,
    ADD COLUMN opening_rank_one_bidder_name TEXT,
    ADD COLUMN opening_rank_one_bid_amount_won NUMERIC(20, 0),
    ADD COLUMN opening_rank_one_bid_amount_currency TEXT,
    ADD COLUMN opening_rank_one_bid_rate_fraction NUMERIC,
    ADD COLUMN opening_rank_one_observed_at TIMESTAMPTZ,
    ADD COLUMN draw_numbers_kind TEXT,
    ADD COLUMN draw_numbers INT[],
    ADD COLUMN draw_numbers_observed_at TIMESTAMPTZ,
    ADD COLUMN draw_numbers_valid_range_max INT,
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
    -- F-2 — 관측 시각은 kind 와 정확히 짝을 이룬다(NotObserved 만 둘 다 NULL).
    ADD CONSTRAINT opening_result_opening_rank_one_observed_at_pair
        CHECK ((opening_rank_one_kind IS NULL) = (opening_rank_one_observed_at IS NULL)),
    ADD CONSTRAINT opening_result_draw_numbers_kind_enum
        CHECK (draw_numbers_kind IN ('VERIFIED', 'OUT_OF_RANGE', 'RANGE_CHECK_UNAVAILABLE')),
    ADD CONSTRAINT opening_result_draw_numbers_pair
        CHECK ((draw_numbers_kind IS NULL) = (draw_numbers IS NULL)),
    ADD CONSTRAINT opening_result_draw_numbers_observed_at_pair
        CHECK ((draw_numbers_kind IS NULL) = (draw_numbers_observed_at IS NULL)),
    -- verifier r2 N-1 뒤 — kind='OUT_OF_RANGE' 는 이 축 자신의 valid_range_max 컬럼이
    -- 있다는 것과 정확히 동치다(다른 두 컬럼과 같은 자리, 다른 축을 참조하지 않는다) —
    -- IS NOT DISTINCT FROM 으로 kind가 NULL 일 때도 이 컬럼이 NULL 이어야 함을 함께 진다.
    ADD CONSTRAINT opening_result_draw_numbers_valid_range_max_pair
        CHECK ((draw_numbers_kind IS NOT DISTINCT FROM 'OUT_OF_RANGE') = (draw_numbers_valid_range_max IS NOT NULL));

-- verifier r1 F-1 — 컬럼별 COALESCE 는 opening_rank_one 축의 여섯 값 컬럼(kind 자신 포함)
-- 사이의 짝을 깨뜨릴 수 있다(예: RankMissing 으로 갱신하면서 이전 Determined 의
-- bidderName 이 컬럼별 COALESCE 로 그대로 남아 determined_pair CHECK 를 위반). UPSERT
-- 술어는 Sql.kt 에서 「EXCLUDED.opening_rank_one_kind IS NULL 이면 이 축 전부를 보존,
-- 아니면 이 축 전부를 EXCLUDED 로 교체」— 컬럼 단위가 아니라 **축 단위**로 갱신한다
-- (draw_numbers 축도 같은 술어). 이 CHECK 들은 느슨하게 하지 않는다 — 그 반대 방향(축 단위
-- UPSERT)으로 문제를 없앤다.

CREATE TRIGGER guard_opening_result_opening_rank_one
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'opening_rank_one_kind', 'opening_rank_one_duplicate_count', 'opening_rank_one_bidder_name',
        'opening_rank_one_bid_amount_won', 'opening_rank_one_bid_amount_currency',
        'opening_rank_one_bid_rate_fraction', 'opening_rank_one_observed_at');

CREATE TRIGGER guard_opening_result_draw_numbers
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'draw_numbers_kind', 'draw_numbers', 'draw_numbers_observed_at', 'draw_numbers_valid_range_max');
