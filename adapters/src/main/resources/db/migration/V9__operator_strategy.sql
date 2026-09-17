-- M6/6F-1 D-6F1-1~6 — 전략 영속: 현재 전략 한 벌(싱글턴, operator_strategy)과 개정 이력
-- (append-only, operator_strategy_revision). 어댑터는 이 표를 초안(StrategyRow)으로만
-- 읽고 `bidvector.strategy.validate()`를 지나야 값을 낸다(D-6F1-2) — 이 표 자체는 그
-- 불변식을 강제하지 않는다(임계값 순서·예산 순서 등은 Kotlin validate()의 몫, notice
-- provenance 점유 가드와 다른 축).
--
-- D-6F1-5 — `revision` 컬럼에 DEFAULT·트리거를 두지 않는다. 도메인(StrategyRevision)이
-- 정한 값을 그대로 저장한다 — 이 표가 스스로 번호를 매기지 않는다(V2의 notice_revision
-- 트리거는 공고 축이고 이 표에 상속되지 않는다).

CREATE TABLE operator_strategy (
    -- 싱글턴 고정 키(id=1) — 이 표는 항상 한 행이다. 애플리케이션이 리터럴 1을 쓴다
    -- (DEFAULT가 아니다 — id 자체는 D-6F1-5의 대상이 아니지만 이 표 전체를 값 지어내기
    -- 없는 관례로 통일한다).
    id SMALLINT PRIMARY KEY CHECK (id = 1),

    focus_categories TEXT[] NOT NULL,
    focus_region_terms TEXT[] NOT NULL,
    exclude_region_terms TEXT[] NOT NULL,
    required_keyword_terms TEXT[] NOT NULL,
    exclude_keyword_terms TEXT[] NOT NULL,

    min_budget_won NUMERIC(20, 0),
    min_budget_currency TEXT,
    min_budget_vat TEXT,
    min_budget_provenance TEXT,
    min_budget_provenance_detail TEXT,

    max_budget_won NUMERIC(20, 0),
    max_budget_currency TEXT,
    max_budget_vat TEXT,
    max_budget_provenance TEXT,
    max_budget_provenance_detail TEXT,

    -- [0,1] 구조적 경계(bidvector.strategy.Score)는 여기서도 CHECK로 방어 심층을 둔다 —
    -- 정책이 정하는 더 좁은 범위(StrategyPolicyData)는 이 표가 알지 못하고 validate()가
    -- 진다(D-6F1-3).
    minimum_match_score NUMERIC CHECK (minimum_match_score BETWEEN 0 AND 1),
    minimum_probability_score NUMERIC CHECK (minimum_probability_score BETWEEN 0 AND 1),
    bid_now_threshold NUMERIC CHECK (bid_now_threshold BETWEEN 0 AND 1),
    review_threshold NUMERIC CHECK (review_threshold BETWEEN 0 AND 1),

    candidate_limit INT CHECK (candidate_limit > 0),

    revision INT NOT NULL CHECK (revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK ((min_budget_won IS NULL) = (min_budget_currency IS NULL)),
    CHECK ((min_budget_won IS NULL) = (min_budget_vat IS NULL)),
    CHECK ((min_budget_won IS NULL) = (min_budget_provenance IS NULL)),
    CHECK ((max_budget_won IS NULL) = (max_budget_currency IS NULL)),
    CHECK ((max_budget_won IS NULL) = (max_budget_vat IS NULL)),
    CHECK ((max_budget_won IS NULL) = (max_budget_provenance IS NULL))
);

-- 개정 이력(append-only) — save()마다 한 행을 더한다(왕복 안정성 감사, D-6F1-1 ①). 이
-- slice의 위협 모델은 이 표의 보존·파기를 다루지 않는다(6B-3 소관) — 삭제·갱신 트리거는
-- 두지 않는다(이 slice의 threat model 경계 밖).
CREATE TABLE operator_strategy_revision (
    revision INT PRIMARY KEY CHECK (revision >= 0),

    focus_categories TEXT[] NOT NULL,
    focus_region_terms TEXT[] NOT NULL,
    exclude_region_terms TEXT[] NOT NULL,
    required_keyword_terms TEXT[] NOT NULL,
    exclude_keyword_terms TEXT[] NOT NULL,

    min_budget_won NUMERIC(20, 0),
    min_budget_currency TEXT,
    min_budget_vat TEXT,
    min_budget_provenance TEXT,
    min_budget_provenance_detail TEXT,

    max_budget_won NUMERIC(20, 0),
    max_budget_currency TEXT,
    max_budget_vat TEXT,
    max_budget_provenance TEXT,
    max_budget_provenance_detail TEXT,

    minimum_match_score NUMERIC CHECK (minimum_match_score BETWEEN 0 AND 1),
    minimum_probability_score NUMERIC CHECK (minimum_probability_score BETWEEN 0 AND 1),
    bid_now_threshold NUMERIC CHECK (bid_now_threshold BETWEEN 0 AND 1),
    review_threshold NUMERIC CHECK (review_threshold BETWEEN 0 AND 1),

    candidate_limit INT CHECK (candidate_limit > 0),

    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK ((min_budget_won IS NULL) = (min_budget_currency IS NULL)),
    CHECK ((min_budget_won IS NULL) = (min_budget_vat IS NULL)),
    CHECK ((min_budget_won IS NULL) = (min_budget_provenance IS NULL)),
    CHECK ((max_budget_won IS NULL) = (max_budget_currency IS NULL)),
    CHECK ((max_budget_won IS NULL) = (max_budget_vat IS NULL)),
    CHECK ((max_budget_won IS NULL) = (max_budget_provenance IS NULL))
);

-- GRANT — operator_strategy 는 upsert(SELECT·INSERT·UPDATE)를 진다(V2 관례,
-- notice/opening_result 와 같은 축). operator_strategy_revision 은 append-only(SELECT·INSERT만,
-- outbox·raw_observation 관례) — bidvector_app 은 이 표에 UPDATE·DELETE 권한이 없다.
GRANT SELECT, INSERT, UPDATE ON operator_strategy TO bidvector_app;
GRANT SELECT, INSERT ON operator_strategy_revision TO bidvector_app;
