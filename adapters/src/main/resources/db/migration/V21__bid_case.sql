-- M7/7B — 입찰 건(`BID-01`)의 표 넷. 계약 산출물 5.
--
-- 번호는 D-6A1-12 의 정의를 따른다(「PR 시점 main 최대 번호보다 크고, 병행 레인이 선점 통보한
-- 번호를 피한다」) — 착수 시점 main 최대는 V20 이고, 병행 레인 중 마이그레이션을 만드는 것은
-- 이 slice 뿐이다(계약 base). PR 직전에 그 정의대로 재확인한다.
--
-- **모든 표에 `account_id BIGINT NOT NULL REFERENCES account(id)`** 가 있다(R-2 (c)). 계정은
-- 아직 하나지만 열은 처음부터 둔다 — 「계정이 둘이 되는 순간 표를 바꿔야 하는」 자리를 만들지
-- 않는다는 것이 그 결정의 내용이다. `operator_profile`(V12) · `operator_strategy`(V9) 의
-- `CHECK (id = 1)` 이전은 **식별자 모델 결정과 함께 별 slice** 다(운영자 결정 2026-10-21,
-- 계약 r2 D-7B-1 · `milestone-7.md` R-2 판단 문단).
--
-- =============================================================================
-- **`notice` 를 FK 로 참조하지 않는다 — 의도된 설계다.**
--
-- 6B-3 의 보존 분류가 `notice` 를 `Purge(TimeOnly)` 로 둔다. 건이 공고를 FK 로 가리키면
-- 기한이 지난 공고를 지울 때 ⓐ RESTRICT 로 **파기 sweep 자체가 실패**하거나 ⓑ CASCADE 를
-- 걸면 **운영자의 투찰 기록이 공고와 함께 사라진다**. 둘 다 받아들일 수 없다.
--
-- 그래서 공고 키는 **사본**이고, 초안이 싣는 공고 스냅샷(마감 · 하한율 · 기초금액)도 사본이다.
-- 공고 행이 파기된 뒤에도 건과 기록은 남고, 그때 필요한 값은 스냅샷이 이미 들고 있다.
-- 되돌리기 메모는 `reports/evidence/m7/7b/rollback.md`, 운영 메모는 runbook 해당 절.
-- =============================================================================

-- =============================================================================
-- ① bid_case — 건. (계정, 공고, 종류) 하나당 하나다(§6.1 「같은 공고에 기록이 여러 번
--    갱신돼도 건은 하나다」).
--
--    상태 어휘 열넷은 `ux-journey-research.md` §6.1 그대로이고 **'ON_HOLD'(보류)가 없다** —
--    「나중에(리마인드)」와 「건너뜀(사유)」로 갈렸다. 어휘를 늘리려면 새 V 파일이 필요하다.
--
--    일정 축 넷은 「다음 확인 시점」의 입력이다. 상태별 필수는 CHECK 가 진다 — 도메인에서는
--    그 값을 **상태가 싣고** 있어 애초에 빠질 수 없고(`BidState.Deferred(remindAt)` 등),
--    이 CHECK 는 그 불변식의 둘째 겹이다.
-- =============================================================================
CREATE TABLE bid_case (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES account (id),

    -- 공고 키 **사본**(위 머리말). 형식만 재선언한다(V1 notice 와 같은 규칙).
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),

    bid_kind TEXT NOT NULL CHECK (bid_kind IN ('REAL', 'PAPER')),

    state TEXT NOT NULL CHECK (state IN (
        'WATCHING',
        'DEFERRED',
        'PRICE_RECEIVED',
        'BID_PLANNED',
        'BID_RECORDED',
        'AWAITING_OPENING',
        'OPENED_PROVISIONAL_FIRST',
        'OPENED_OUT_OF_RANK',
        'UNDER_QUALIFICATION_REVIEW',
        'WON',
        'LOST',
        'FAILED_OR_REBID',
        'SKIPPED',
        'DEADLINE_PASSED_UNRECORDED'
    )),

    -- 낙관적 잠금 축. 타임라인의 UNIQUE (case_id, case_version) 가 같은 불변식의 둘째 겹이다.
    case_version INT NOT NULL CHECK (case_version >= 0),

    remind_at TIMESTAMPTZ,
    opening_at TIMESTAMPTZ,
    document_due_at TIMESTAMPTZ,
    outcome_notice_expected_at TIMESTAMPTZ,

    skip_reason TEXT CHECK (skip_reason <> ''),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL,

    UNIQUE (account_id, notice_number, notice_round, bid_kind),

    -- 「나중에 ⇒ 리마인드 NOT NULL」·「건너뜀 ⇒ 사유 NOT NULL」(계약 산출물 5)
    CONSTRAINT bid_case_deferred_requires_remind
        CHECK (state <> 'DEFERRED' OR remind_at IS NOT NULL),
    CONSTRAINT bid_case_skipped_requires_reason
        CHECK (state <> 'SKIPPED' OR skip_reason IS NOT NULL),
    -- 반대 방향도 막는다 — 건너뜀이 아닌데 사유가 실려 있으면 그 행은 자기 상태를 거짓말한다.
    CONSTRAINT bid_case_reason_only_when_skipped
        CHECK (state = 'SKIPPED' OR skip_reason IS NULL),
    CONSTRAINT bid_case_awaiting_requires_opening
        CHECK (state <> 'AWAITING_OPENING' OR opening_at IS NOT NULL),
    CONSTRAINT bid_case_review_requires_due
        CHECK (state <> 'UNDER_QUALIFICATION_REVIEW' OR document_due_at IS NOT NULL)
);

-- 내 입찰함 질의(계정 → 종류 → 상태 탭)의 선두 열이 `account_id` 다 — 소유 필터가 색인의
-- 첫 축이라 「필터를 빠뜨린 질의」가 느린 것으로도 드러난다.
CREATE INDEX bid_case_owner_idx ON bid_case (account_id, bid_kind, state);

-- =============================================================================
-- ② bid_case_transition — 타임라인. **append-only** 다(§6.1 「모든 전이가 시각 · 주체와 함께
--    타임라인에 남는다」).
--
--    두 겹으로 막는다: 앱 역할에 UPDATE · DELETE 권한이 없고(아래 GRANT), V3 의
--    `reject_mutation()` 트리거가 시도 자체를 거부한다(`raw_observation` · `notice_audit` 과
--    같은 관례). TRUNCATE 도 주지 않는다.
--
--    **본문은 들어오지 않는다** — `reason` 은 건너뜀 · 정정의 사유이고, 메모 · 증빙 참조는
--    이 표에 열이 없다(계약 산출물 8 「audit 는 본문을 남기지 않는다」).
-- =============================================================================
CREATE TABLE bid_case_transition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES bid_case (id),
    -- 비정규화가 아니라 **소유 필터의 자리**다 — 타임라인 질의가 부모를 조인하지 않고도
    -- 계정으로 좁혀진다(조인을 빠뜨린 질의가 남의 줄을 보는 길을 없앤다).
    account_id BIGINT NOT NULL REFERENCES account (id),

    case_version INT NOT NULL CHECK (case_version >= 0),
    -- 생성 전이만 NULL — 출발 상태가 없다.
    from_state TEXT CHECK (from_state <> ''),
    to_state TEXT NOT NULL CHECK (to_state <> ''),
    command TEXT NOT NULL CHECK (command <> ''),

    actor_kind TEXT NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM')),
    -- USER 의 주체는 `account_id` 다 — 사람의 이름을 적는 자리가 아니다. SYSTEM 만 경로
    -- 이름을 싣는다(V6 outbox 의 actor_kind↔actor_detail 짝 관례).
    actor_detail TEXT CHECK (actor_detail <> ''),
    CONSTRAINT bid_case_transition_actor_pair
        CHECK ((actor_kind = 'SYSTEM') = (actor_detail IS NOT NULL)),

    reason TEXT CHECK (reason <> ''),
    retroactive BOOLEAN NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 한 건의 한 판에 전이는 하나다 — 경합이 여기서도 걸린다.
    UNIQUE (case_id, case_version),

    -- 사유를 요구하는 명령 둘(건너뜀 · 결과 정정). 도메인에서는 타입이 같은 것을 진다.
    CONSTRAINT bid_case_transition_reason_required
        CHECK (command NOT IN ('SKIP', 'CORRECT_OUTCOME') OR reason IS NOT NULL),
    -- 소급은 마감 지남에서 온 기록뿐이다(Q-11) — 서버 판정을 표가 한 번 더 좁힌다.
    CONSTRAINT bid_case_transition_retroactive_origin
        CHECK (
            NOT retroactive
            OR (from_state = 'DEADLINE_PASSED_UNRECORDED' AND command = 'RECORD_BID')
        )
);

CREATE INDEX bid_case_transition_case_idx ON bid_case_transition (case_id, case_version);

CREATE TRIGGER bid_case_transition_append_only
    BEFORE UPDATE OR DELETE ON bid_case_transition
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- =============================================================================
-- ③ bid_draft — 투찰 예정 초안. **append-only 이고 그것이 「스냅샷 불변」의 본체다.**
--
--    초안 수정은 같은 행을 고치는 것이 아니라 `draft_seq + 1` 의 **새 행**이다. 그래서
--    「공고가 정정된 뒤 옛 초안이 달라져 있다」가 DB 층에서 불가능하다 — 문장이 아니라
--    UPDATE 권한의 부재와 트리거가 진다(§6.1).
--
--    금액 출처 짝(`amount_provenance` ↔ `chosen_option_ref`)이 **메뉴 값 위장**을 막는
--    둘째 겹이다. 첫째 겹은 HTTP 요청 DTO 에 그 두 자리가 **없는** 것이다(계약 산출물 6) —
--    오늘 이 열에 들어오는 값은 전부 'OPERATOR_ENTERED' 다.
-- =============================================================================
CREATE TABLE bid_draft (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES bid_case (id),
    account_id BIGINT NOT NULL REFERENCES account (id),
    draft_seq INT NOT NULL CHECK (draft_seq >= 0),
    UNIQUE (case_id, draft_seq),

    -- 원 단위 Long 상한을 표가 막는다(V9 의 won 범위 CHECK 와 같은 방어).
    amount_won NUMERIC(20, 0) NOT NULL
        CHECK (amount_won BETWEEN 0 AND 9223372036854775807),
    amount_currency TEXT NOT NULL CHECK (amount_currency <> ''),
    amount_vat TEXT NOT NULL CHECK (amount_vat <> ''),
    amount_provenance TEXT NOT NULL
        CHECK (amount_provenance IN ('OPERATOR_ENTERED', 'PRICE_MENU')),
    chosen_option_ref TEXT CHECK (chosen_option_ref <> ''),
    CONSTRAINT bid_draft_menu_requires_option
        CHECK ((amount_provenance = 'PRICE_MENU') = (chosen_option_ref IS NOT NULL)),

    presented_at TIMESTAMPTZ NOT NULL,

    -- 공고 스냅샷(사본, 불변). 판 번호가 없으면 「어느 판을 보고 정했는가」를 되짚을 수 없다.
    snapshot_notice_revision TEXT NOT NULL CHECK (snapshot_notice_revision ~ '^[0-9]{3}$'),
    snapshot_taken_at TIMESTAMPTZ NOT NULL,
    snapshot_deadline_at TIMESTAMPTZ,
    snapshot_floor_rate_fraction NUMERIC,
    snapshot_floor_rate_origin_kind TEXT,
    snapshot_floor_rate_origin_detail TEXT,
    snapshot_base_amount_won NUMERIC(20, 0)
        CHECK (snapshot_base_amount_won IS NULL
               OR snapshot_base_amount_won BETWEEN 0 AND 9223372036854775807),
    snapshot_base_amount_currency TEXT,
    snapshot_base_amount_vat TEXT,
    snapshot_base_amount_provenance TEXT,
    -- V1 notice 와 같은 짝 CHECK — basis 없는 금액, 출처 없는 율을 두지 않는다.
    CONSTRAINT bid_draft_snapshot_floor_pair
        CHECK ((snapshot_floor_rate_fraction IS NULL) = (snapshot_floor_rate_origin_kind IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_currency_pair
        CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_currency IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_vat_pair
        CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_vat IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_provenance_pair
        CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_provenance IS NULL)),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX bid_draft_case_idx ON bid_draft (case_id, draft_seq);

CREATE TRIGGER bid_draft_append_only
    BEFORE UPDATE OR DELETE ON bid_draft
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- =============================================================================
-- ④ bid_record — 투찰 기록. append-only(기록 갱신도 새 행이다 — 「건은 하나」이고 기록은
--    쌓인다).
--
--    **제출 시각과 확인 시각이 따로 있다**(§6.1) — 앞은 운영자의 진술, 뒤는 서버의 시계.
--    소급 확인에서 둘은 크게 벌어지고, 그 사실 자체가 복기의 재료다.
--
--    **사업자번호 · 대표자명 슬롯이 없다**(계약 산출물 8). `evidence_ref` 는 파일도 경로도
--    아닌 **식별자**이고, `memo` 는 자유 텍스트이되 로그 · audit 어디에도 실리지 않는다.
-- =============================================================================
CREATE TABLE bid_record (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES bid_case (id),
    account_id BIGINT NOT NULL REFERENCES account (id),
    record_seq INT NOT NULL CHECK (record_seq >= 0),
    UNIQUE (case_id, record_seq),

    amount_won NUMERIC(20, 0) NOT NULL
        CHECK (amount_won BETWEEN 0 AND 9223372036854775807),
    amount_currency TEXT NOT NULL CHECK (amount_currency <> ''),
    amount_vat TEXT NOT NULL CHECK (amount_vat <> ''),
    amount_provenance TEXT NOT NULL
        CHECK (amount_provenance IN ('OPERATOR_ENTERED', 'PRICE_MENU')),
    chosen_option_ref TEXT CHECK (chosen_option_ref <> ''),
    CONSTRAINT bid_record_menu_requires_option
        CHECK ((amount_provenance = 'PRICE_MENU') = (chosen_option_ref IS NOT NULL)),

    submitted_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    -- 기본값이 된 초안. 소급 확인은 초안 없이도 성립하므로 nullable 이다.
    draft_id BIGINT REFERENCES bid_draft (id),
    retroactive BOOLEAN NOT NULL,

    -- 추첨번호(V5 opening_result.draw_numbers 와 같은 형태).
    draw_numbers INT[],

    evidence_ref TEXT CHECK (evidence_ref <> ''),
    -- R-5 3년. **「이 참조를 언제까지 보관하는가」이지 파일 파기 기한이 아니다** — PDF
    -- 저장소는 이 slice 밖이고, 저장소 slice 가 열리면 같은 날짜를 승계한다.
    evidence_retain_until DATE,
    CONSTRAINT bid_record_evidence_pair
        CHECK ((evidence_ref IS NULL) = (evidence_retain_until IS NULL)),

    memo TEXT CHECK (memo <> ''),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX bid_record_case_idx ON bid_record (case_id, record_seq);

CREATE TRIGGER bid_record_append_only
    BEFORE UPDATE OR DELETE ON bid_record
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- =============================================================================
-- GRANT — **열 단위**다(V20 관례). 요청 경로(앱 역할)가 할 수 있는 일을 최소로 줄인다.
--
--  · `bid_case` 의 UPDATE 는 **전이가 실제로 바꾸는 열에만** 있다 — 앱 역할은 `account_id` ·
--    `notice_number` · `notice_round` · `bid_kind` · `created_at` 을 **바꿀 수 없다**.
--    건의 소유자를 갈아끼우거나 다른 공고의 건으로 둔갑시키는 길이 권한 층에서 없다.
--  · 타임라인 · 초안 · 기록 셋은 INSERT · SELECT 뿐이다(append-only).
--  · DELETE · TRUNCATE 는 **어느 표에도** 없다. 보존 · 파기는 6B-3 의 소유자 연결이 지고,
--    이 넷은 `OPERATOR_BID_HISTORY` 로 시간 기준 파기에서 빠진다(RetentionTableRegistry).
-- =============================================================================
GRANT SELECT, INSERT ON bid_case TO bidvector_app;
GRANT UPDATE (
    state,
    case_version,
    remind_at,
    opening_at,
    document_due_at,
    outcome_notice_expected_at,
    skip_reason,
    updated_at
) ON bid_case TO bidvector_app;

GRANT SELECT, INSERT ON bid_case_transition TO bidvector_app;
GRANT SELECT, INSERT ON bid_draft TO bidvector_app;
GRANT SELECT, INSERT ON bid_record TO bidvector_app;

-- 방어 심층 문서화(V3 관례) — 기본값이 이미 미부여지만 명시한다.
REVOKE TRUNCATE ON bid_case, bid_case_transition, bid_draft, bid_record FROM PUBLIC;
