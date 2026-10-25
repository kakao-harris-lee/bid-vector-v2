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

    -- **공고 마감의 사본**(계약 D-7B-3 결정 ①). 건을 담을 때 공고의 마감을 적어 둔다 — 공고가
    -- 6B-3 으로 파기된 뒤에도 서버가 「마감이 지났는가」를 판정할 수 있어야 소급 확인(Q-11)이
    -- 닿는다. 담을 때 공고가 없으면 NULL 이다(지어내지 않는다). 전이가 고치지 않는다(열 GRANT 밖).
    deadline_at TIMESTAMPTZ,

    remind_at TIMESTAMPTZ,
    opening_at TIMESTAMPTZ,
    document_due_at TIMESTAMPTZ,
    outcome_notice_expected_at TIMESTAMPTZ,

    -- 자유 텍스트이고 지울 수 없는 타임라인에도 남는다 — 상한을 도메인(`SkipReason.MAX_LENGTH`)과
    -- 같은 값으로 표가 한 번 더 진다(계약 D-7B-3, 개인정보 축).
    skip_reason TEXT CHECK (skip_reason <> '' AND char_length(skip_reason) <= 500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL,

    UNIQUE (account_id, notice_number, notice_round, bid_kind),
    -- 자식 표 셋의 복합 FK 대상 — 자식 행의 `account_id` 가 **부모 건의 소유자와 같다**는 것을
    -- 구조로 진다(계약 D-7B-3). 단일 열 FK 둘(건 · 계정)로는 둘의 일치가 강제되지 않는다.
    CONSTRAINT bid_case_id_account_key UNIQUE (id, account_id),

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
    case_id BIGINT NOT NULL,
    -- 비정규화가 아니라 **소유 필터의 자리**다 — 타임라인 질의가 부모를 조인하지 않고도
    -- 계정으로 좁혀진다(조인을 빠뜨린 질의가 남의 줄을 보는 길을 없앤다). 그 값이 부모의
    -- 소유자와 같다는 것은 아래 복합 FK 가 진다.
    account_id BIGINT NOT NULL REFERENCES account (id),
    CONSTRAINT bid_case_transition_case_owner_fk
        FOREIGN KEY (case_id, account_id) REFERENCES bid_case (id, account_id),

    case_version INT NOT NULL CHECK (case_version >= 0),
    -- 생성 전이만 NULL — 출발 상태가 없다. 상태 · 명령 어휘는 **건 표와 같은 열거**다(계약
    -- D-7B-3) — 타임라인에만 다른 어휘(예: 'ON_HOLD')가 들어오면 그 건의 타임라인 조회 전체가
    -- 깨지고, append-only 라 그 행을 지울 수도 없다.
    from_state TEXT CHECK (from_state IN (
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
    to_state TEXT NOT NULL CHECK (to_state IN (
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
    -- 명령 열넷(`BidCommandTag`) + 생성 명령(`OPEN_CASE_COMMAND`). 열넷째는 마감 판정 되돌림(D-7B-4 결정 ⑤).
    command TEXT NOT NULL CHECK (command IN (
        'ADD_TO_BID_BOX',
        'DEFER',
        'RESUME',
        'RECEIVE_PRICE_MENU',
        'DRAFT_BID',
        'RECORD_BID',
        'AWAIT_OPENING',
        'RECORD_OPENING_FIRST',
        'RECORD_OPENING_OUT_OF_RANK',
        'ENTER_QUALIFICATION_REVIEW',
        'RECORD_OUTCOME',
        'CORRECT_OUTCOME',
        'SKIP',
        'MARK_DEADLINE_PASSED',
        'REVOKE_DEADLINE_PASSED'
    )),

    actor_kind TEXT NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM')),
    -- USER 의 주체는 `account_id` 다 — 사람의 이름을 적는 자리가 아니다. SYSTEM 만 경로
    -- 이름을 싣는다(V6 outbox 의 actor_kind↔actor_detail 짝 관례).
    actor_detail TEXT CHECK (actor_detail <> ''),
    CONSTRAINT bid_case_transition_actor_pair
        CHECK ((actor_kind = 'SYSTEM') = (actor_detail IS NOT NULL)),

    -- 건너뜀 · 정정의 사유 — 상한은 도메인(`SkipReason` · `TransitionReason` 의 `MAX_LENGTH`)과 같다.
    reason TEXT CHECK (reason <> '' AND char_length(reason) <= 500),
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

-- 따로 `CREATE INDEX` 를 두지 않는다 — `UNIQUE (case_id, case_version)` 의 색인이 같은 열을 같은
-- 순서로 덮는다(중복 색인은 쓰기 증폭뿐이다, 계약 D-7B-3). 초안 · 기록 표도 같다.

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
    case_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES account (id),
    CONSTRAINT bid_draft_case_owner_fk
        FOREIGN KEY (case_id, account_id) REFERENCES bid_case (id, account_id),
    draft_seq INT NOT NULL CHECK (draft_seq >= 0),
    UNIQUE (case_id, draft_seq),
    -- 기록의 복합 FK 대상 — 기록이 가리키는 초안이 **같은 건 · 같은 계정**의 것임을 구조가 진다(계약 D-7B-4).
    CONSTRAINT bid_draft_id_case_account_key UNIQUE (id, case_id, account_id),

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
    -- 공고 **판 번호**(`notice.revision`) — 정정될 때마다 오르는 카운터다. 차수
    -- (`notice_round`)는 건이 이미 들고 있어 여기 다시 싣지 않는다.
    snapshot_notice_revision BIGINT NOT NULL CHECK (snapshot_notice_revision >= 1),
    snapshot_taken_at TIMESTAMPTZ NOT NULL,
    snapshot_deadline_at TIMESTAMPTZ,
    snapshot_floor_rate_fraction NUMERIC,
    -- 출처 어휘는 `notice` 와 **같은 코덱**(`FloorRateOriginCodec` · `ProvenanceCodec`)이 쓴다 — 저장소에
    -- 셋째 어휘가 생기지 않게 열거로 닫는다(계약 D-7B-3, 검토 B-1).
    snapshot_floor_rate_origin_kind TEXT
        CHECK (snapshot_floor_rate_origin_kind IN ('NOTICE_VALUE', 'STATUTORY_TABLE')),
    snapshot_floor_rate_origin_detail TEXT CHECK (snapshot_floor_rate_origin_detail <> ''),
    snapshot_base_amount_won NUMERIC(20, 0)
        CHECK (snapshot_base_amount_won IS NULL
               OR snapshot_base_amount_won BETWEEN 0 AND 9223372036854775807),
    snapshot_base_amount_currency TEXT CHECK (snapshot_base_amount_currency <> ''),
    snapshot_base_amount_vat TEXT CHECK (snapshot_base_amount_vat <> ''),
    snapshot_base_amount_provenance TEXT CHECK (snapshot_base_amount_provenance IN (
        'PUBLISHED',
        'DERIVED_FROM_OPENING',
        'FILLED_FROM_BUDGET_KEY',
        'COPIED_FROM_BASE_AMOUNT',
        'OPERATOR_DECLARED',
        'UNDECLARED'
    )),
    -- `PUBLISHED` 는 공고 판, `FILLED_FROM_BUDGET_KEY` 는 예산 키를 싣는다(`ProvenanceCodec.detailOf`).
    snapshot_base_amount_provenance_detail TEXT CHECK (snapshot_base_amount_provenance_detail <> ''),
    -- V1 notice 와 같은 짝 CHECK — basis 없는 금액, 출처 없는 율을 두지 않는다.
    CONSTRAINT bid_draft_snapshot_floor_pair
        CHECK ((snapshot_floor_rate_fraction IS NULL) = (snapshot_floor_rate_origin_kind IS NULL)),
    CONSTRAINT bid_draft_snapshot_floor_detail_pair
        CHECK ((snapshot_floor_rate_origin_kind IS NULL) = (snapshot_floor_rate_origin_detail IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_currency_pair
        CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_currency IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_vat_pair
        CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_vat IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_provenance_pair
        CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_provenance IS NULL)),
    CONSTRAINT bid_draft_snapshot_base_provenance_detail_pair
        -- CASE 로 적는다 — `IN` 이 NULL 을 내면 CHECK 는 통과로 읽으므로, 출처가 없을 때를 따로 가른다.
        CHECK (
            CASE
                WHEN snapshot_base_amount_provenance IS NULL
                    THEN snapshot_base_amount_provenance_detail IS NULL
                WHEN snapshot_base_amount_provenance IN ('PUBLISHED', 'FILLED_FROM_BUDGET_KEY')
                    THEN snapshot_base_amount_provenance_detail IS NOT NULL
                ELSE snapshot_base_amount_provenance_detail IS NULL
            END
        ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

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
    case_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES account (id),
    CONSTRAINT bid_record_case_owner_fk
        FOREIGN KEY (case_id, account_id) REFERENCES bid_case (id, account_id),
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
    -- **기록 시점의 최신 초안**이다 — 화면의 기본값과 같다는 보장은 없다(요청이 초안 식별자를 싣지 않으므로
    -- 화면 적재와 기록 사이에 초안이 하나 더 쌓이면 둘은 다르다). 「이 초안대로 냈다」도 아니다: 금액은 기록이
    -- 따로 싣는다. 소급 확인은 초안 없이도 성립하므로 nullable 이다(아래 복합 FK 는 NULL 이면 MATCH SIMPLE 로 통과).
    draft_id BIGINT,
    CONSTRAINT bid_record_draft_owner_fk
        FOREIGN KEY (draft_id, case_id, account_id) REFERENCES bid_draft (id, case_id, account_id),
    retroactive BOOLEAN NOT NULL,

    -- 추첨번호(V5 opening_result.draw_numbers 와 같은 형태). 원소 부호는 도메인(`BidSubmission`)과
    -- 같은 규칙을 표가 한 번 더 진다.
    draw_numbers INT[] CHECK (draw_numbers IS NULL OR 0 < ALL (draw_numbers)),

    -- 상한은 도메인(`EvidenceRef.MAX_LENGTH`)과 같은 값 — 지울 수 없는 표다(D-7B-4).
    evidence_ref TEXT CHECK (evidence_ref <> '' AND char_length(evidence_ref) <= 128),
    -- R-5 3년. **「이 참조를 언제까지 보관하는가」이지 파일 파기 기한이 아니다** — PDF
    -- 저장소는 이 slice 밖이고, 저장소 slice 가 열리면 같은 날짜를 승계한다.
    evidence_retain_until DATE,
    CONSTRAINT bid_record_evidence_pair
        CHECK ((evidence_ref IS NULL) = (evidence_retain_until IS NULL)),

    -- 상한은 도메인(`BidMemo.MAX_LENGTH`)과 같은 값.
    memo TEXT CHECK (memo <> '' AND char_length(memo) <= 2000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER bid_record_append_only
    BEFORE UPDATE OR DELETE ON bid_record
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- =============================================================================
-- GRANT — **열 단위**다(V20 관례). 요청 경로(앱 역할)가 할 수 있는 일을 최소로 줄인다.
--
--  · `bid_case` 의 UPDATE 는 **전이가 실제로 바꾸는 열에만** 있다 — 앱 역할은 `account_id` ·
--    `notice_number` · `notice_round` · `bid_kind` · `deadline_at` · `created_at` 을 **바꿀 수 없다**.
--    `outcome_notice_expected_at` 도 뺐다 — 오늘 그 열을 쓰는 경로가 없고(알려진 제한,
--    `OPEN-7B-NOTICE-EXPECTED-WRITER`), 쓰지 않는 열에 권한을 열어 두지 않는다(계약 D-7B-3 결정 ②).
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
    skip_reason,
    updated_at
) ON bid_case TO bidvector_app;

GRANT SELECT, INSERT ON bid_case_transition TO bidvector_app;
GRANT SELECT, INSERT ON bid_draft TO bidvector_app;
GRANT SELECT, INSERT ON bid_record TO bidvector_app;

-- 방어 심층 문서화(V3 관례) — 기본값이 이미 미부여지만 명시한다.
REVOKE TRUNCATE ON bid_case, bid_case_transition, bid_draft, bid_record FROM PUBLIC;
