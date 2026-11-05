-- M9/ops-migrations (DEC-08) — 기초금액 provenance **판정 기록**. DEC-08 의 acceptance 넷째
-- 줄(「분류에 사용된 정책 version 이 결과와 함께 저장된다」)이 구현에 없었다: 커널은 원본·판정·
-- 근거·정책 version 을 한 봉투로 내는데(`ProvenanceJudgement`) 호출부가 라벨만 취해 나머지를
-- 버렸고, 그 version 을 담는 칸이 스키마에 **0** 이었다(6e1 추적).
--
-- **기존 표에 칸을 더하지 않는다.** 이 판정은 canonical **사실**이 아니라 사실 위의 **판단**이고
-- (`notice.base_amount_provenance` 는 write provenance — 다른 축이다), 정책이 바뀌면 같은 공고에
-- 새 판정이 선다. 사실 행에 판단 칸을 붙이면 ① 사실과 판단이 한 행에서 섞이고 ② 새 version 이
-- 과거 판정을 덮는다. 그래서 **append-only 판정 표**다(`notice_audit`·`rejected_write` 관례).
--
-- **PK 에 정책 두 축이 들어간다** — 같은 공고 · 같은 정책이면 한 행(멱등), 정책이 바뀌면 새 행.
-- `policy_effective_from` 은 `EffectiveFrom`(sealed: `Initial` | `On(date)`)의 인코딩이라 TEXT 다:
-- `'INITIAL'` 또는 ISO 날짜. 날짜 열 + NULL 로 두면 PK 에 들어가지 못하고(NULL 금지), `date.min`
-- 같은 sentinel 날짜로 접으면 `Initial` 변이를 둔 이유 자체가 사라진다(`data-dictionary.md` §4.1).
--
-- **릴리스 칸이 없다** — 이 표의 provenance 축은 「어느 **정책**이 판정했는가」이고, 「어느
-- 릴리스가 썼는가」는 이 판정 경로가 출하 조립에 들어오는 날 함께 선다(`OPEN-ML-ANALYSIS-WIRING`
-- 종속, `OPEN-M9-CLASSIFICATION-RELEASE` 로 등재).
CREATE TABLE base_amount_classification (
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    -- V1 notice 와 같은 형식 재선언(3A 정규화 규칙 자체는 재선언하지 않는다).
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),

    policy_source TEXT NOT NULL CHECK (policy_source <> ''),
    policy_effective_from TEXT NOT NULL
        CHECK (policy_effective_from = 'INITIAL' OR policy_effective_from ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'),

    -- 라벨 다섯(`BaseAmountProvenance`)·규칙 넷(`ProvenanceRuleId`)을 그대로 싣는다.
    classification TEXT NOT NULL
        CHECK (classification IN ('CLEAN', 'DERIVED_YEGA', 'DERIVED_VAT', 'SUSPECT_RATIO', 'UNKNOWN')),
    first_matched_rule TEXT
        CHECK (first_matched_rule IN ('SUSPECT_RATIO', 'CLEAN_INTEGER', 'DERIVED_YEGA', 'DERIVED_VAT')),

    -- 커널의 결속(`classificationFor`)을 저장 층이 다시 선다 — 「매치 없음 = UNKNOWN」이고 그
    -- 밖의 조합(규칙은 있는데 UNKNOWN · 규칙이 없는데 CLEAN)은 거부다. 쌍 전수는 아래 사상이고,
    -- 어긋난 행이 들어오면 판정 기록 자체를 믿을 수 없게 된다.
    CONSTRAINT base_amount_classification_rule_label_check CHECK (
        (first_matched_rule IS NULL AND classification = 'UNKNOWN')
        OR (classification, first_matched_rule) IN (
            ('SUSPECT_RATIO', 'SUSPECT_RATIO'),
            ('CLEAN', 'CLEAN_INTEGER'),
            ('DERIVED_YEGA', 'DERIVED_YEGA'),
            ('DERIVED_VAT', 'DERIVED_VAT')
        )
    ),

    -- 판정 대상 **원본**(D-6: 원본을 조용히 교정하지 않는다). 복구 추정치는 이 표에 없다 —
    -- 이 판정 경로가 계산하지 않는다(`Fact.Absent(POLICY_NOT_APPLICABLE)`).
    judged_amount_won NUMERIC(20, 0) NOT NULL
        CHECK (judged_amount_won BETWEEN 0 AND 9223372036854775807),
    judged_amount_currency TEXT NOT NULL CHECK (judged_amount_currency <> ''),
    judged_amount_vat TEXT NOT NULL CHECK (judged_amount_vat <> ''),

    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (notice_number, notice_round, policy_source, policy_effective_from)
);

-- append-only — 판정 이력은 고쳐 쓰지 않는다(V3 의 `reject_mutation` 을 그대로 쓴다).
CREATE TRIGGER base_amount_classification_append_only
    BEFORE UPDATE OR DELETE ON base_amount_classification
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- GRANT — 기록(INSERT)·조회(SELECT)만. UPDATE·DELETE 는 주지 않는다(위 트리거와 두 겹).
GRANT SELECT, INSERT ON base_amount_classification TO bidvector_app;
