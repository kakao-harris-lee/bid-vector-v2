# M7/7B — 입찰 건 모듈(`bidding`) · BID-01 등재 · V21 — evidence

계약: `reports/evidence/m7/7b/scope.md`(착수 계약 · 계약 r2(D-7B-1) · 계약 D-7B-2 — 커밋은 제목으로 가리킨다). base `bee7c36f` → 2026-10-23 rebase `a61ddff1` → `56bb7474`.

---

## Phase 1 — 설계 (코드·마이그레이션 쓰기 전, 팀장 답 대기)

읽은 정본: `ux-journey-research.md` §6.1(:183-228) · `adr/0006-gradle-modules.md` D-2·D-2.1(:60,71-94) ·
`decisions-2026-09-07.md` Q-7·Q-11·Q-12(:20-25,32) · `m7-screen-data-requirements.md` 7B 행(:51-109,149) ·
`capability-map.md` OPEN-ADR-14(:3102)·§0.6(:120-131)·§10(:2831-2856)·STR-16 블록(:715-760)·활성 집계(:2962) ·
`milestone-7.md` R-2·R-5(:88-108). 선례: V20 · V9 · V12 · V3 · V16 · `RetentionTableRegistry` ·
`CleanMigration*Test` · `workflow/strategy`(Transition·TransitionOutcome·Actor) · `BrowserAccessPolicyData` ·
`RequestAuthenticationFilter` · `architecture-policy.properties` · `gate-tests.properties`.

### 1. 상태 집합

§6.1 의 어휘를 그대로 쓴다. **「보류」는 없다** — 「나중에(리마인드 시각)」와 「건너뜀(사유)」로 갈린다.
「후보(제안됨/검색됨)」는 **영속 상태가 아니다**: pull 모델(§0.7 — 「내 입찰함은 사용자가 담은 것만 담는다」)
에서 건은 사용자가 담는 순간 태어난다. 그래서 첫 영속 상태는 `WATCHING` 이다.

| # | 상태 | 어휘(DB) | 종단 | 다음 확인 시점 |
|---|---|---|---|---|
| 1 | 관심 | `WATCHING` | 아니다 | 마감 |
| 2 | 나중에 | `DEFERRED` | 아니다 | 리마인드(필수) |
| 3 | 투찰가 받음 | `PRICE_RECEIVED` | 아니다 | 마감 |
| 4 | 투찰 예정(초안) | `BID_PLANNED` | 아니다 | 마감 |
| 5 | 투찰 기록됨 | `BID_RECORDED` | 아니다 | 개찰일시, 없으면 「없음」 |
| 6 | 개찰 대기 | `AWAITING_OPENING` | 아니다 | 개찰일시(필수) |
| 7 | 개찰됨·잠정 1위 | `OPENED_PROVISIONAL_FIRST` | 아니다 | 서류 기한, 없으면 「없음」 |
| 8 | 개찰됨·순위 밖 | `OPENED_OUT_OF_RANK` | 아니다 | 통보 예정일, 없으면 「없음」 |
| 9 | 적격심사 중 | `UNDER_QUALIFICATION_REVIEW` | 아니다 | 서류 기한(필수) |
| 10 | 낙찰 | `WON` | 정정만 | 「없음」 |
| 11 | 패찰 | `LOST` | 정정만 | 「없음」 |
| 12 | 유찰·재공고 | `FAILED_OR_REBID` | 정정만 | 「없음」 |
| 13 | 건너뜀 | `SKIPPED` | **그렇다** | 「없음」 |
| 14 | 마감 지남(미확인) | `DEADLINE_PASSED_UNRECORDED` | **아니다** | 「없음」 |

**계약 문면과의 차이 둘 — 보고 대상.**

- 계약은 *"종료 가지는 건너뜀과 마감 지남(미확인)이다"* 라 적는다. 그런데 Q-11 이 **소급 확인을 무기한**
  허용하므로 `DEADLINE_PASSED_UNRECORDED` 에는 **나가는 간선이 정확히 하나**(소급 기록) 있어야 한다.
  그래서 이 상태는 「여정의 종료 가지」이되 **전이표의 종단은 아니다**. 둘이 같은 말이 아님을 전이표가
  구조로 적는다(`terminal` 술어 = 나가는 간선 0 → `SKIPPED` 하나뿐).
- §6.1 은 낙찰/패찰/유찰을 *"자동(SET-01) + 사용자 정정"* 이라 적는다. 정정을 두지 않으면 잘못 들어온
  결과가 영구히 굳는다. 그래서 10~12 끼리의 `CORRECT_OUTCOME` 전이를 두되 **사유를 필수**로 하고
  타임라인에 남긴다(원본을 덮어쓰지 않는다 — `.claude/rules/kotlin-service.md` 「확정 금액 정정」과 같은 축).

### 2. 전이표 (상태 × 명령 → 다음 상태 · 필수 인자 · 허용 주체)

선언적 표 하나다. **금지 전이는 표에 없는 것**이라 구조로 거부된다(중첩 분기 0).

| 현재 | 명령 | 다음 | 필수 인자 | 주체 |
|---|---|---|---|---|
| (없음) | `ADD_TO_BID_BOX` | `WATCHING` | 공고 키 · `bidKind` | User |
| `WATCHING` · `PRICE_RECEIVED` | `DEFER` | `DEFERRED` | `remindAt` | User |
| `DEFERRED` | `RESUME` | `WATCHING` | — | User · System(리마인드 도달) |
| `WATCHING` · `DEFERRED` | `RECEIVE_PRICE_MENU` | `PRICE_RECEIVED` | 메뉴 참조 | System(DEC) |
| `WATCHING` · `DEFERRED` · `PRICE_RECEIVED` | `DRAFT_BID` | `BID_PLANNED` | 금액(원·basis·provenance) · `presentedAt` · 공고 스냅샷 | User |
| `BID_PLANNED` | `REVISE_DRAFT` | `BID_PLANNED` | 위와 같음 | User |
| `BID_PLANNED` | `RECORD_BID` | `BID_RECORDED` | 금액 · `submittedAt` | User |
| `DEADLINE_PASSED_UNRECORDED` | `RECORD_BID_RETROACTIVELY` | `BID_RECORDED` | 금액 · `submittedAt` | User |
| `BID_RECORDED` | `UPDATE_BID_RECORD` | `BID_RECORDED` | 금액 · `submittedAt` | User |
| `BID_RECORDED` | `AWAIT_OPENING` | `AWAITING_OPENING` | `openingAt` | User · System |
| `AWAITING_OPENING` | `RECORD_OPENING_FIRST` | `OPENED_PROVISIONAL_FIRST` | — | User · System(SET-02) |
| `AWAITING_OPENING` | `RECORD_OPENING_OUT_OF_RANK` | `OPENED_OUT_OF_RANK` | — | User · System(SET-02) |
| `OPENED_PROVISIONAL_FIRST` | `ENTER_QUALIFICATION_REVIEW` | `UNDER_QUALIFICATION_REVIEW` | `documentDueAt` | User |
| `OPENED_PROVISIONAL_FIRST` · `OPENED_OUT_OF_RANK` · `UNDER_QUALIFICATION_REVIEW` | `RECORD_OUTCOME` | `WON` · `LOST` · `FAILED_OR_REBID` | 결과 | User · System(SET-01) |
| `WON` · `LOST` · `FAILED_OR_REBID` | `CORRECT_OUTCOME` | `WON` · `LOST` · `FAILED_OR_REBID` | 결과 · **사유** | User |
| `WATCHING` · `DEFERRED` · `PRICE_RECEIVED` · `BID_PLANNED` | `SKIP` | `SKIPPED` | **사유** | User |
| `WATCHING` · `DEFERRED` · `PRICE_RECEIVED` · `BID_PLANNED` | `MARK_DEADLINE_PASSED` | `DEADLINE_PASSED_UNRECORDED` | — | User · System(시각 판정) |

**자동 전이의 호출자가 이 slice 에 없다**(SET-01·SET-02·DEC 메뉴가 범위 밖). 표에는 행이 있고 HTTP 는
**사용자 명령만** 연다 — 그래서 `RECEIVE_PRICE_MENU` 는 오늘 도달 불가다(알려진 제한 ①). 어휘를 지금
두는 이유는 나중에 CHECK 어휘를 늘리는 마이그레이션(되돌리기 어려운 경로)을 한 번 더 만들지 않기
위해서다. §6.1 이 이미 이 상태를 적는다.

### 3. 다음 확인 시점 — 「비어 있지 않음」을 세 겹으로

- **타입**: `sealed interface NextCheckpoint { data class At(at, kind); data object None }` — nullable 이
  아니다. `kind` 는 `DEADLINE · OPENING · DOCUMENT_DUE · NOTICE_EXPECTED · REMINDER`.
- **순수 함수**: `nextCheckpoint(case, now): NextCheckpoint` 는 상태 sealed 에 대한 `when` 전수라
  **상태를 더하면 답을 주지 않고는 컴파일되지 않는다**. 저장하지 않는다(파생값을 영속화하면 드리프트가
  생긴다 — 「서버가 한 시각 기준으로 판정」과도 맞는다).
- **CHECK**: 함수가 전역(total)이려면 **입력이 상태별로 보장**돼야 한다. 그것을 DB 가 진다 —
  `state <> 'DEFERRED' OR remind_at IS NOT NULL` 등(아래 §5). 즉 타입이 출력을, CHECK 가 입력을 막고,
  속성 test 가 전 상태에 대해 전역성을 증명한다.
- `None` 은 **종단·종료 가지에서만** 나오고 「없음」으로 명시 표시된다(빈 값이 아니다).

### 4. 페이퍼/실 분리 — 구조로

- `BidKind` 는 sealed(`Real`/`Paper`)이고 **모든 목록 포트의 필수 인자**다. 둘을 함께 내는 operation 이
  **없다**(서명에 자리가 없다).
- SET-02 후보 술어는 `fun reconciliationCandidate(case: RealBidCase): Boolean` 이다. `RealBidCase` 는
  `internal constructor` 이고 좁히기 함수(`BidCase.asReal(): RealBidCase?`)로만 얻는다 — 페이퍼 건을 그
  자리에 **넘길 수 없다**(`AppliedStrategy` 선례와 같은 폐쇄). 술어 본문은 `state is BidState.BidRecorded`.
- 대사 **실행**은 SET 소유다. 이 slice 는 술어와 그 술어를 읽는 응답 필드(`reconciliationCandidate`)까지.

### 5. 표 설계 (V21) — DDL 초안

번호는 D-6A1-12 대로 PR 직전 재확인한다(착수 시점 main 최대 `V20`, 병행 레인 중 마이그레이션을 만드는
것은 이 slice 뿐). 표 넷이다. 모든 사용자 소유 표에 `account_id BIGINT NOT NULL REFERENCES account(id)`.

#### 5.1 `bid_case` — 건

```sql
CREATE TABLE bid_case (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES account (id),

    -- 공고 키는 **사본**이다(FK 를 두지 않는다 — §5.5 근거).
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round  TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),

    bid_kind TEXT NOT NULL CHECK (bid_kind IN ('REAL', 'PAPER')),
    state    TEXT NOT NULL CHECK (state IN (
        'WATCHING','DEFERRED','PRICE_RECEIVED','BID_PLANNED','BID_RECORDED',
        'AWAITING_OPENING','OPENED_PROVISIONAL_FIRST','OPENED_OUT_OF_RANK',
        'UNDER_QUALIFICATION_REVIEW','WON','LOST','FAILED_OR_REBID',
        'SKIPPED','DEADLINE_PASSED_UNRECORDED')),   -- 'ON_HOLD'(보류)는 어휘에 없다
    case_version INT NOT NULL CHECK (case_version >= 0),

    -- 「다음 확인 시점」의 입력 축
    remind_at                  TIMESTAMPTZ,
    opening_at                 TIMESTAMPTZ,
    document_due_at            TIMESTAMPTZ,
    outcome_notice_expected_at TIMESTAMPTZ,

    skip_reason TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL,

    -- 「같은 공고에 기록이 여러 번 갱신돼도 건은 하나다」 + 페이퍼/실 분리
    UNIQUE (account_id, notice_number, notice_round, bid_kind),

    CHECK (state <> 'DEFERRED'                   OR remind_at       IS NOT NULL),
    CHECK (state <> 'AWAITING_OPENING'           OR opening_at      IS NOT NULL),
    CHECK (state <> 'UNDER_QUALIFICATION_REVIEW' OR document_due_at IS NOT NULL),
    CHECK (state <> 'SKIPPED'                    OR (skip_reason IS NOT NULL AND skip_reason <> '')),
    CHECK (state =  'SKIPPED'                    OR skip_reason IS NULL)
);
CREATE INDEX bid_case_owner_idx ON bid_case (account_id, bid_kind, state);
```

#### 5.2 `bid_case_transition` — 타임라인(append-only)

```sql
CREATE TABLE bid_case_transition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id    BIGINT NOT NULL REFERENCES bid_case (id),
    account_id BIGINT NOT NULL REFERENCES account (id),   -- 소유 필터를 이 표에서도 구조로

    case_version INT NOT NULL CHECK (case_version >= 0),
    from_state TEXT,                                      -- 생성 전이만 NULL
    to_state   TEXT NOT NULL,
    command    TEXT NOT NULL,                             -- 전이표 명령 어휘

    actor_kind   TEXT NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM')),
    actor_detail TEXT CHECK (actor_detail <> ''),
    CHECK ((actor_kind = 'SYSTEM') = (actor_detail IS NOT NULL)),  -- USER 의 주체는 account_id

    reason      TEXT CHECK (reason <> ''),   -- 사유. **메모 본문은 여기 들어오지 않는다**
    retroactive BOOLEAN NOT NULL,            -- Q-11 소급 표시

    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (case_id, case_version),
    CHECK (command NOT IN ('SKIP', 'CORRECT_OUTCOME') OR reason IS NOT NULL)
);
CREATE INDEX bid_case_transition_case_idx ON bid_case_transition (case_id, case_version);

CREATE TRIGGER bid_case_transition_append_only
    BEFORE UPDATE OR DELETE ON bid_case_transition
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();      -- V3 의 함수를 재사용
REVOKE TRUNCATE ON bid_case_transition FROM PUBLIC;
```

#### 5.3 `bid_draft` — 투찰 예정 초안(append-only, 스냅샷 사본)

수정은 **새 행**(`draft_seq+1`)이다 — 그래서 스냅샷 불변이 *문장*이 아니라 **UPDATE 권한의 부재와
트리거**가 진다.

```sql
CREATE TABLE bid_draft (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id    BIGINT NOT NULL REFERENCES bid_case (id),
    account_id BIGINT NOT NULL REFERENCES account (id),
    draft_seq  INT NOT NULL CHECK (draft_seq >= 0),
    UNIQUE (case_id, draft_seq),

    -- 금액: 원 단위 + basis + provenance (§6 — 메뉴 값 위장 금지)
    amount_won      NUMERIC(20, 0) NOT NULL CHECK (amount_won BETWEEN 0 AND 9223372036854775807),
    amount_currency TEXT NOT NULL,
    amount_vat      TEXT NOT NULL,
    amount_provenance TEXT NOT NULL CHECK (amount_provenance IN ('OPERATOR_ENTERED', 'PRICE_MENU')),
    chosen_option_ref TEXT CHECK (chosen_option_ref <> ''),
    CHECK ((amount_provenance = 'PRICE_MENU') = (chosen_option_ref IS NOT NULL)),

    presented_at TIMESTAMPTZ NOT NULL,

    -- 공고 스냅샷 — **사본**이다(공고가 정정돼도 바뀌지 않는다)
    snapshot_notice_revision          BIGINT NOT NULL CHECK (snapshot_notice_revision >= 1),
    snapshot_taken_at                 TIMESTAMPTZ NOT NULL,
    snapshot_deadline_at              TIMESTAMPTZ,
    snapshot_floor_rate_fraction      NUMERIC,
    snapshot_floor_rate_origin_kind   TEXT,
    snapshot_floor_rate_origin_detail TEXT,
    snapshot_base_amount_won          NUMERIC(20, 0),
    snapshot_base_amount_currency     TEXT,
    snapshot_base_amount_vat          TEXT,
    snapshot_base_amount_provenance   TEXT,
    CHECK ((snapshot_floor_rate_fraction IS NULL) = (snapshot_floor_rate_origin_kind IS NULL)),
    CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_currency IS NULL)),
    CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_vat IS NULL)),
    CHECK ((snapshot_base_amount_won IS NULL) = (snapshot_base_amount_provenance IS NULL)),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER bid_draft_append_only BEFORE UPDATE OR DELETE ON bid_draft
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
REVOKE TRUNCATE ON bid_draft FROM PUBLIC;
```

#### 5.4 `bid_record` — 투찰 기록(append-only)

```sql
CREATE TABLE bid_record (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id    BIGINT NOT NULL REFERENCES bid_case (id),
    account_id BIGINT NOT NULL REFERENCES account (id),
    record_seq INT NOT NULL CHECK (record_seq >= 0),
    UNIQUE (case_id, record_seq),

    amount_won      NUMERIC(20, 0) NOT NULL CHECK (amount_won BETWEEN 0 AND 9223372036854775807),
    amount_currency TEXT NOT NULL,
    amount_vat      TEXT NOT NULL,
    amount_basis    TEXT NOT NULL,

    submitted_at TIMESTAMPTZ NOT NULL,            -- 제출 시각(운영자 진술)
    confirmed_at TIMESTAMPTZ NOT NULL,            -- 확인 시각 — **초안 시각과 따로 남는다**
    draft_id BIGINT REFERENCES bid_draft (id),    -- 기본값이 된 초안(소급 확인이면 NULL 가능)
    retroactive BOOLEAN NOT NULL,                 -- Q-11

    draw_numbers INT[],                           -- 추첨번호(opening_result 관례)

    evidence_ref          TEXT CHECK (evidence_ref <> ''),  -- 증빙 **참조**(식별자)
    evidence_retain_until DATE,                             -- R-5 3년, 정책이 계산
    CHECK ((evidence_ref IS NULL) = (evidence_retain_until IS NULL)),

    memo TEXT,                                    -- 자유 텍스트 — 로그·audit 노출 0

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER bid_record_append_only BEFORE UPDATE OR DELETE ON bid_record
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
REVOKE TRUNCATE ON bid_record FROM PUBLIC;
```

**사업자번호·대표자명 슬롯이 없다** — 넷 어디에도 그 열을 만들지 않는다(산출물 8).

#### 5.5 `notice` FK 를 두지 않는 이유 — **발견**

`bid_case` 가 `notice(notice_number, notice_round)` 를 FK 로 참조하면 **6B-3 보존 파기와 충돌한다**:
`RETENTION_TABLE_RULES` 가 `notice` 를 `Purge(TimeOnly)` 로 분류하므로, 살아 있는 건이 가리키는 공고를
지우려 할 때 ⓐ RESTRICT 로 파기 sweep 자체가 실패하거나 ⓑ CASCADE 를 걸면 **운영자의 투찰 기록이 함께
사라진다**. 둘 다 받아들일 수 없다. 그래서 공고 키는 **사본**이고(스냅샷 사본 결정과 같은 축), 공고가
파기된 뒤에도 건과 기록은 남는다. `bid_draft` 의 스냅샷이 그때 필요한 값(마감·하한율·basis)을 이미 싣는다.

#### 5.6 GRANT — 열 단위(7D 관례)

```sql
GRANT SELECT, INSERT ON bid_case TO bidvector_app;
GRANT UPDATE (state, case_version, remind_at, opening_at, document_due_at,
              outcome_notice_expected_at, skip_reason, updated_at) ON bid_case TO bidvector_app;

GRANT SELECT, INSERT ON bid_case_transition TO bidvector_app;   -- append-only: UPDATE 없음
GRANT SELECT, INSERT ON bid_draft           TO bidvector_app;
GRANT SELECT, INSERT ON bid_record          TO bidvector_app;
```

- `bid_case` 의 UPDATE 는 **전이가 실제로 바꾸는 열에만** 있다 — 앱 역할은 `account_id`·`notice_number`·
  `notice_round`·`bid_kind`·`created_at` 을 **바꿀 수 없다**(건의 소유자와 대상 공고를 갈아끼우는 길이 없다).
- 나머지 셋은 INSERT·SELECT 뿐이고 **DELETE·TRUNCATE 는 어느 표에도 없다**.

#### 5.7 보존 분류

`RetentionRegistrySchemaEqualityTest` 가 스키마와 집합 등식을 요구하므로 네 표를 모두 등재한다. 새
`ExemptReason.OPERATOR_BID_HISTORY` 하나(사유: 투찰 기록은 정산·증빙의 근거이고 수명이 시간이 아니라
R-5 3년 정책과 운영자 결정이 정한다). **파기 실행은 범위 밖**이고 그 사실을 사유 KDoc 에 적는다.

### 6. 초안 금액 입력 형태 — 메뉴 값 위장 금지 (산출물 6)

투찰가 메뉴(DEC-05)는 출하 경로에 없다(ML-01·7C② 대기). 그래서:

- 금액 provenance 어휘는 **둘**이다 — `OPERATOR_ENTERED`(운영자가 직접 적은 값) · `PRICE_MENU`(메뉴
  산출에서 고른 안, `chosen_option_ref` 동반 필수).
- **HTTP 요청 DTO 에 provenance·chosenOptionRef 필드가 없다.** use case 가 `OPERATOR_ENTERED` 를 상수로
  싣는다. 그래서 운영자 입력이 메뉴 값으로 보이는 경로는 **요청으로 표현할 수 없다**(위장이 타입·스키마
  층에서 불가능하다 — 문장으로 금지하는 것이 아니다).
- `PRICE_MENU` 어휘를 지금 두는 이유는 CHECK 어휘를 나중에 늘리는 둘째 마이그레이션을 피하기 위해서다.
  **오늘 그 값을 쓰는 경로는 0** 이고, 게이트 test 가 그 0 을 단언한다(「요청 DTO 에 provenance 자리가
  없다」 + 「HTTP 로 만들어진 draft 의 provenance 는 전부 `OPERATOR_ENTERED`」).
- 응답은 provenance 를 **그대로 내보낸다** — 화면(7E)이 「운영자 입력」과 「추천 메뉴에서 고름」을
  구별해 표시할 수 있어야 한다. 구별을 지우면 그것이 곧 위장이다.

### 7. 모듈·패키지 배치와 게이트 등재 지점

| 자리 | 내용 |
|---|---|
| `settings.gradle.kts` | `include(...)` 에 `"bidding"` 추가 + 「아홉 … 보류한다」 주석 교체(되살림 사유·`OPEN-ADR-14` 종결) |
| `bidding/build.gradle.kts` | `bidvector.kotlin-conventions`, `implementation(project(":shared-kernel"))` 만(`strategy` 사본) |
| `bidding/src/main/kotlin/bidvector/bidding/` | `BidState.kt` · `BidCommand.kt` · `BidTransitionTable.kt` · `BidCase.kt` · `BidDraft.kt` · `BidRecord.kt` · `NextCheckpoint.kt` · `BidKind.kt` · `ReconciliationCandidate.kt` · `ModuleBoundaryAnchor.kt` |
| `workflow/.../workflow/bidding/` | `BiddingPorts.kt` · `BidCaseWorkflow.kt` · `TransitionGuards.kt` · `TransitionOutcome.kt` · `BidActor.kt` · `EvidenceRetentionPolicyData.kt` |
| `adapters/.../adapters/bidding/` | `JdbcBidCaseStore.kt` · `BidCaseSql.kt` · `BidCaseRow.kt` |
| `app/.../app/http/` | `BidCaseController.kt` · `BidCaseRequests.kt` · `BidCaseResponses.kt` |
| `adapters/.../db/migration/V21__bid_case.sql` | 위 DDL |

**게이트 등재 지점**(넓히는 줄마다 근거를 PR 본문·evidence 에 적는다):

| 파일 | 키 | 변경 | 근거 |
|---|---|---|---|
| `config/quality/architecture-policy.properties` | `layer.domain`(:44) | `+bidding` | 새 domain 모듈. 이 값이 층 판정·모집단 단언의 단일 출처다 |
| 〃 | `app.injection.domain-port-roots`(:606) | `+bidvector.bidding` | 「이 저장소가 이름 붙인 계약의 뿌리」 — controller 생성자가 bidding 타입을 이름 부른다 |
| 〃 | `collection.reflection.roots`(:1351) | `+bidvector.bidding` | **판정 대상 뿌리**다. 빠뜨리면 새 모듈의 반사가 조용히 안 재진다 |
| `config/quality/gate-tests.properties` | `gate.tests.bidding`(신설) · `gate.tests.workflow` · `gate.tests.adapters` · `gate.tests.app` | 새 test class 전수 | `gateRegistrationGate` 가 **양방향 등식**이라 등재 없이는 즉시 붉다 |
| `build-logic` | `ModuleDependencyPolicyTest` · `DomainSourceReferenceFixtureTest` | 필요 시 모듈 이름 | 대부분 properties 에서 도출되므로 실측 후 최소로 |
| `openapi/bidvector-operator-api.yaml` + `web/src/api/generated/operator-api.ts` | — | 새 operation + `npm run api:types` 재생성 | W-5 바이트 등식 |

`config/quality` 는 병렬 레인과 겹치는 파일이다 — **자기 키·자기 줄만** 고치고 팀장에게 알린다.

### 8. operation 목록 (화면 C · D 최소)

전부 **기본 거부 필터 아래**다(`OPEN_ROUTES` 에 새 줄 0 — 무인증 경로 0). 상태 변경은 CSRF 를 요구한다.
경로 하나당 명령 하나는 `edit-sessions` 선례를 따른다.

| operationId | method · path | 용도 |
|---|---|---|
| `listBidCases` | GET `/api/bid-cases` | 내 입찰함 — `kind` **필수**, 상태 탭, 다음 확인 시점, 지난 일정 경고 |
| `readBidCase` | GET `/api/bid-cases/{caseId}` | 건 단건(초안·기록 최신 포함, `reconciliationCandidate`) |
| `readBidCaseTimeline` | GET `/api/bid-cases/{caseId}/timeline` | 타임라인(시각·주체·사유·소급 표시) |
| `addBidCase` | POST `/api/bid-cases` | 관심 담기 |
| `deferBidCase` | POST `/api/bid-cases/{caseId}/defer` | 나중에(리마인드 시각 필수) |
| `resumeBidCase` | POST `/api/bid-cases/{caseId}/resume` | 나중에 해제 |
| `skipBidCase` | POST `/api/bid-cases/{caseId}/skip` | 건너뜀(사유 필수) |
| `draftBidCase` | POST `/api/bid-cases/{caseId}/draft` | 초안 만들기·수정 |
| `recordBidCase` | POST `/api/bid-cases/{caseId}/record` | 「제출함」 기록·갱신·**소급 확인**(소급 여부는 서버가 판정) |
| `recordBidCaseOpening` | POST `/api/bid-cases/{caseId}/opening` | 개찰 결과 입력(잠정 1위 / 순위 밖) |
| `enterQualificationReview` | POST `/api/bid-cases/{caseId}/qualification-review` | 적격심사 중(서류 기한) |
| `recordBidCaseOutcome` | POST `/api/bid-cases/{caseId}/outcome` | 낙찰·패찰·유찰 기록 및 정정(정정은 사유 필수) |

열둘이다. 줄일 수 있는 자리는 `opening`·`qualification-review`·`outcome` 을 명령 판별자 하나의
`/transitions` 로 합치는 것인데, `edit-sessions` 선례가 경로당 명령 하나라 **합치지 않기를 권한다**
(합치면 OpenAPI 스키마가 oneOf 로 커지고 CSRF·감사 단위가 흐려진다). 팀장이 수를 줄이라면
`qualification-review` 를 7C 로 미루는 것이 가장 싸다(화면 D 의 「1순위 서류 기한 카드」가 늦어진다).

### 9. R-2 남은 절반 — 판정 (산출물 5)

**물음**: `operator_profile`(V12) · `operator_strategy`(V9) 의 `CHECK (id = 1)` 을 계정 단위로 옮기는 일을
같은 V21 / 별 V22 / 별 slice 중 어디에 두는가.

**범위 실측**(읽기로 확인한 것):

- 두 표만 바뀌지 않는다. `operator_strategy_revision` 의 **PK 가 `revision` 전역 단일**이라 계정 단위가
  되려면 PK 를 `(account_id, revision)` 로 바꿔야 한다 — 열 추가가 아니라 **키 변경**이다.
- `V16` 이 두 표에 같은 열을 더해 둔 상태라 둘을 함께 손대야 한다.
- 소비자: `adapters/strategy` · `adapters/profile` · `workflow/strategy`(`EditStrategyWorkflow`·
  `StrategyValidation`) · `app/http`(`StrategyReadController`·`StrategyEditController`) · `RETENTION_TABLE_RULES`
  의 `Exempt(SINGLETON_SETTING)` 둘 · `CleanMigrationCheckTest` 의 기대 CHECK 수(`operator_strategy` 15 등) ·
  `CleanMigrationPrivilegeTest` 의 권한 행렬.
- **진짜 걸림돌은 열이 아니라 식별자 모델이다.** `edit_session.operator_id` 는 `TEXT`(`OperatorId`)이고
  `account.id` 는 `BIGINT` 다. 전략을 계정 단위로 만들면 **「`OperatorId` 가 `account.id` 인가」를 먼저
  답해야** 하고, 그 답은 7D 의 audit 주체 어휘(`account:<id>` vs `operator`)와 `OperatorCredentialAuthenticator`
  (기존 운영자 자격 헤더 경로)까지 건드린다. 그것은 표 변경이 아니라 **R-2 의 식별자 결정**이다.

**판정: 별 slice 를 권한다**(같은 V21 아님, 별 V22 도 아님).

1. 범위가 「열 추가」가 아니라 **식별자 모델 결정 + 키 변경 + 기존 라이브 경로 전면**이다. 이 slice 의
   되돌리기 어려운 경로(새 표 넷)와 섞으면 검토 표면이 두 배가 되고, verifier 가 둘을 한 라운드에 본다.
2. 건드리는 코드가 전부 **기존** 경로(`workflow/strategy`·`adapters/strategy`·`app/http`)이고 병렬 레인
   (w-dec05 · w-c03)과 충돌 위험이 크다. 이 slice 의 in_scope 는 「신설」이다.
3. **지연 비용이 낮다.** 두 표는 각각 한 행이고 per-account 질의 표면이 **0** 이라, 계정이 둘이 되기
   전까지 노출이 없다. R-2 가 막으려는 것(「남의 데이터가 보이는 전환」)은 **새 표 넷**에서 실질이 있고,
   그 넷은 이 slice 가 처음부터 `account_id` 로 세운다 — R-2 (c) 의 본문은 그대로 이행된다.
4. **되돌리기**: 별 slice 면 그 마이그레이션 하나만 revert 된다. 같은 V21 에 섞으면 7B 를 되돌릴 때
   전략·프로필 표까지 함께 흔들린다(forward-fix 정본인 경로에서 특히 나쁘다).

**반대 근거(정직하게 적는다)**: `milestone-7.md` R-2 판단 문단이 *"7B 가 새 표를 세울 때 함께"* 라고
적는다. 위 판정은 그 문면을 **미루는** 것이므로 팀장(필요하면 운영자) 승인이 필요하다. 승인되면
`milestone-7.md` 그 줄에 「2026-10-21: 식별자 모델 결정이 선행이라 별 slice 로 분리」를 한 줄 적는다.

**검토했으나 권하지 않는 중간안**: V21 에서 두 표에 `account_id` 를 **추가만** 하고 `CHECK (id = 1)` 은
남기기. 열은 생기지만 **읽는 코드가 없는 죽은 열**이 되고, 진짜 일(키 변경·식별자 결정)은 그대로 남는다.
죽은 열은 다음 사람에게 「이미 계정 단위다」라고 거짓 신호를 준다.

### 10. BID-01 추적표 행 여부 (산출물 1 마지막 줄)

**판정**: `reports/evidence/m6/6e1/acceptance-trace.md` 의 **집계는 고치지 않는다.** 대신 둘을 한다.

1. 그 파일에 **날짜 붙은 교차 참조 한 줄**을 더한다 — 「2026-10-21: BID-01 등재로 `V2 필수` 모수가
   61 → 62 가 됐다. 그 행은 `reports/evidence/m7/7b/` 에 있다」. 이것 없이 두면 그 문서의 전제
   (*"`V2 필수` 61 전건"*)가 조용히 거짓이 된다.
2. **BID-01 의 추적 행 자체는 이 slice 의 evidence 에 적는다** — 같은 술어(ⓐ/ⓑ/ⓒ)로, 「무엇이 재는가」에
   이 slice 가 만드는 test 식별자를 적는다.

근거: ⓐ 6E-1 의 표는 그 slice 의 동결된 evidence 이고 그 집계(ⓐ16/ⓑ2/ⓒ43)는 2026-10-07 의 판정이다 —
7B 가 남의 evidence 집계를 다시 셈하는 것은 레인 경계를 넘는다. ⓑ 그 파일은 병렬 레인이 함께 만질 수
있는 자리라 집계 재작성은 충돌 비용이 크다. ⓒ 그래도 모수가 움직인 사실은 어딘가 적혀야 하고, 한 줄
교차 참조가 가장 싸다. `capability-map.md` §10 의 「2026-10-21 집계 변경」 블록에도 모수 이동(95 → 96,
`V2 필수` 61 → 62)을 명시한다.

### 11. PDF 저장소가 범위 밖이어도 되는가

**결론: 범위 밖이 맞다.** 단 완화 둘을 함께 넣는다.

범위 밖인 이유 — 저장소를 지금 만들면 ⓐ 파일 업로드라는 새 표면(크기·형식·바이러스·경로 탈출)과
ⓑ 바이트 저장 위치 결정(DB `bytea` / 파일시스템 / 객체 저장소)과 ⓒ 백업·복원 리허설 범위 확장
(`tools/db-rehearsal.sh` 는 **DB 만** 든다)과 ⓓ 3년 파기 실행(6B-3 의 90일·표 단위 축과 **다른 축**이라
보존 레지스트리를 파일까지 확장해야 한다)이 한꺼번에 딸려 온다. 넷은 각자 설계 검토가 필요하고, 이
slice 의 되돌리기 어려운 경로와 섞을 이유가 없다.

**범위 밖으로 둘 때 남는 위험과 완화**:

- 위험 ①: `evidence_ref` 가 가리킬 실체가 오늘 없어 **자유 텍스트 사실상 메모**가 된다 → 파일명에
  개인·사업자 정보가 흘러들 수 있다. 완화: 길이 상한 + 공백 불가 + **로그·audit 노출 0**(메모와 같은
  취급), 그리고 응답 외 어디에도 싣지 않는다.
- 위험 ②: `evidence_retain_until` 이 실체 없는 대상에 걸린다. 완화: 문면을 「이 **참조**를 언제까지
  보관하는가」로 못박는다(파일을 언제 지우는가가 아니다). 저장소 slice 가 열리면 같은 날짜를 파일
  파기 기한으로 승계한다.
- 둘 다 **알려진 제한**으로 등재한다.

`evidence_ref` 자체를 빼는 안은 권하지 않는다 — §6.1 acceptance 가 「증빙 참조」를 투찰 기록의 구성요소로
적으므로 빼면 acceptance 미달이다.

### 12. 알려진 제한 (Phase 1 시점)

1. `PRICE_RECEIVED` 는 오늘 도달 불가다 — 진입 명령(`RECEIVE_PRICE_MENU`)의 호출자가 출하 경로에 없다
   (DEC-05 메뉴는 ML-01 · 7C② 대기). 어휘·CHECK·전이표 행은 둔다.
2. 자동 전이(SET-01 대사 결과 · SET-02 개찰 · 리마인드 도달)의 **시스템 호출자가 이 slice 에 없다.**
   사용자 입력 경로는 열려 있다(§6.1 :175-176 — 「몇 위였나」는 사용자가 보고 오거나 입력한다).
3. PDF 저장소 없음 — §11 의 위험 ①②.
4. 다수 사업자 격리 수준 · 가입 · `operator_profile`/`operator_strategy` 의 계정 단위 전환(§9) 은 범위 밖.

### 13. 팀장 답을 기다리는 항목

1. **§9 R-2 남은 절반 — 별 slice 분리 승인**(`milestone-7.md` 문면을 미루는 것이라 승인이 필요하다).
2. **§10 추적표 처리 — 6E-1 에 교차 참조 한 줄 + 7B evidence 에 행**(그 파일은 병렬 레인과 겹친다).
3. **§8 operation 열둘** — 그대로 둘지, `qualification-review` 를 7C 로 미룰지.
4. **§1 의 차이 둘**(`DEADLINE_PASSED_UNRECORDED` 비종단 · `CORRECT_OUTCOME` 신설) 확인.
5. **§5.5** — `notice` FK 부재(보존 파기 충돌 회피) 확인.

---

## Phase 1.5 — Phase 2 정정 (설계 변경, 팀장 승인 2026-10-22)

구현 중에 **설계 두 곳이 틀렸다는 것이 드러났다.** Phase 1 설계 절을 고쳐 쓰지 않고 여기에
따로 적는다 — 무엇이 어떻게 바뀌었는지가 보여야 한다.

### 정정 ① — 공고 스냅샷은 **서버가 뜬다**(요청이 싣지 않는다)

- **Phase 1 설계**: 초안 명령이 공고 스냅샷을 싣고 그대로 저장한다.
- **무엇이 틀렸나**: 그러면 운영자가 「그때 하한율이 이랬다」를 **나중에 지어낼 수 있다.**
  스냅샷을 얼리는 목적 자체가 「그때 그 금액을 정한 근거」를 증거로 남기는 것인데, 그 값을
  요청이 들고 오면 증거이기를 그친다. 설계 검토 (0)의 **위협 ③「초안 스냅샷 변조」가 바로
  이것**이고, Phase 1 설계는 그 위협을 적어 두고도 열어 둔 채였다.
- **정정**: `NoticeSnapshotQueryPort` 를 두고 workflow 가 공고에서 뜬다. 요청 DTO 에 스냅샷
  자리가 **없다**. 공고를 뜰 수 없으면(없거나 6B-3 으로 파기) 초안을 만들지 않고 409
  (`NOTICE_SNAPSHOT_UNAVAILABLE`)다 — 빈 스냅샷을 지어내지 않는다.

### 정정 ② — 스냅샷은 공고 **판 번호**를 싣는다(차수가 아니라)

- **Phase 1 설계**: `snapshot_notice_revision TEXT CHECK (~ '^[0-9]{3}$')` — 공고 **차수**.
- **무엇이 틀렸나**: 차수는 건의 `NoticeKey` 가 이미 들고 있어 **같은 사실이 두 자리**에
  있었고, 더 나쁘게는 **「정정됐다」는 사실이 보이지 않았다.** 공고가 정정되면
  `notice.revision` 이 오르는데 차수는 그대로다 — 스냅샷을 얼린 이유의 절반이 사라진다.
- **정정**: `snapshot_notice_revision BIGINT CHECK (>= 1)`(= `notice.revision`). 도메인 필드는
  `NoticeSnapshot.noticeRevisionNumber` 이고 `Provenance.Published.noticeRevision`(차수)과
  **다른 축**임을 KDoc 이 적는다.

### 정정 ③ — operation 열둘 → **열넷**(13 경로)

`await-opening` 을 독립 operation 으로 두었고 `outcome` 과 `outcome-correction` 을 따로 셌다.
Phase 1 의 「열둘」은 그 둘을 각각 묶어 센 수다. 팀장 승인 2026-10-22(범위 축소 없음).

## Phase 2 — 구현 (팀장 승인 뒤, 계약 r2 D-7B-1)

### 0. 약속 ↔ 출하

계약(`scope.md` 산출물 1~9 · D-7B-1 · D-7B-2)의 약속 하나마다 **출하한 자리**와 **그것을 재는 자리**를 적는다. 결과는 §7(acceptance) ·
§2(변이)의 실측이고, 이 표는 그 실측이 무엇을 덮는지의 색인이다. 「부분」·「미출하」는 사유와 함께 적는다.

| 약속 | 출하 | 재는 자리 | 상태 |
|---|---|---|---|
| 1 BID-01 정식 등재 · 축 `BID` · §10 집계 95 → 96 · `OPEN-ADR-14` 닫음 | `docs/discovery/capability-map.md` · `docs/adr/0006-gradle-modules.md`(D-2 되살림) · `settings.gradle.kts` | 문서 — 집계 줄은 「머지 직전 재계산」 표시(§3.3) | 출하 |
| 1 추적표 행 | `acceptance-trace.md` 집계는 그대로, 교차 참조 한 줄 · 행은 §5 | §5 | 출하(D-7B-1 결정대로) |
| 2 상태 14 · 선언적 전이표 · 「보류」 없음 | `bidding/…/BidState.kt` · `BidTransitions.kt` | `BidTransitionTableTest`(손으로 옮긴 골든 30 행과 집합 등식) · 변이 ③ | 출하 |
| 2 초안 스냅샷 불변(서버가 뜸) | `BidDraft.kt` · `JdbcNoticeSnapshotQuery` · V21 `bid_draft` append-only | `BidCaseOwnershipTest`「초안 수정은 새 행을 쌓고」 · 변이 ⑩ | 출하 |
| 2 기록 갱신해도 건은 하나 | `BidRecord.kt` · V21 `bid_record`(`record_seq + 1` 새 행) · 담기는 (공고, 종류)당 건 하나 | 전이표 골든의 `BID_RECORDED --RECORD_BID--> BID_RECORDED` 자기 간선 · `BidCaseOwnershipTest`「같은 공고 같은 종류를 두 번 담아도 건은 하나다」 | 출하 — **한 건에 기록 두 번을 실 DB 로 쌓는 행동 test 는 없다**(알려진 공백, §6) |
| 2 소급 확인 무기한 + 표시 | `BidCaseWorkflow` | `BidInvariantsTest` · `BidCaseOwnershipTest`「소급 확인이」 · 변이 ② | 출하 |
| 2 타임라인 append-only | V21 트리거 셋 + GRANT | `BidCaseOwnershipTest`「앱 역할은 … 고치거나 지울 수 없다」 · 변이 ⑦ ⑪ | 출하 |
| 2 다음 확인 시점 비지 않음(sealed) | `NextCheckpoint.kt` | `NextCheckpointTest`(전 상태) · 변이 ④ | 출하 |
| 2 페이퍼/실 분리(구조) | `BidCase.kt` 의 좁힘 · 목록 `kind` 필수 | `BidInvariantsTest` · `BidCaseOwnershipTest`「목록은 종류로 갈린다」 · 변이 ⑤ | 출하 |
| 2 SET-02 후보 술어 | `ReconciliationCandidate.kt` | `BidInvariantsTest` | 출하(대사 자체는 SET 소유) |
| 3 보관 정책 3년(versioned) | `EvidenceRetentionPolicyData.kt` · `RetentionTableRegistry` 등재 | `EvidenceRetentionPolicyTest` · 변이 ⑥ | 출하 — PDF 저장소·파기 실행은 범위 밖(§6) |
| 4 포트가 주체를 받는다 · 남의 건 거부 | `BiddingPorts.kt` · `JdbcBidCaseStore`(모든 SQL `account_id = ?`) | `BidCaseOwnershipTest`(실 DB) · 변이 ① | 출하 |
| 5 V21 표 넷 · `account_id` FK · 열 단위 GRANT · CHECK | `V21__bid_case.sql` | `CleanMigration*Test` 여섯 · 변이 ⑧ ⑨ | 출하 — `notice` FK 없음(D-7B-1) |
| 5 R-2 남은 절반 | — | — | 별 slice(D-7B-1 운영자 결정) |
| 6 operation 열넷 · 새 무인증 0 · 상태 변경 CSRF | `BidCaseCommandController` · `BidCaseQueryController` · `openapi/…yaml` · 생성 타입 | `ProductionHttpSurfaceTest` · `OpenApiSecurityContractTest` · `BrowserSessionAuthenticationTest` · 변이 ⑫ ⑬ | 출하 |
| 6 초안 금액 provenance(메뉴 위장 금지) | 요청 DTO 에 자리 없음 · 서버가 `OPERATOR_ENTERED` | V21 CHECK · `BidCaseRequests` | 출하 — `PRICE_MENU` 도달 불가(§6) |
| 7 각 핵심 성질이 변이에서 RED | §2 표 | §2 | 실측 |
| 8 개인정보 — 사업자번호·대표자명 슬롯 0 · 메모 로그/audit 노출 0 | V21 · 응답에 본문 자리 없음 | `BidTimelineEntryResponse`(본문 없음) · `leakPatternGate` | 출하 |
| 9 문서 — milestone-7 R-5 닫음 · milestone-6 예외 줄 · runbook · 리허설 | `milestone-7.md` · `milestone-6.md` · `docs/runbook/m6-6b3-retention.md` | §7 `db-rehearsal.sh` | 출하 |
| **D-7B-2 (계약 갱신으로 추가)** typed `AccountId` 운반 · 라벨 파싱 0 | `AuthenticatedAccount.kt`(속성 키 · 접근자) · `AuthenticationResult.Authenticated.account` · 필터가 판정 전에 지우고 다시 적음 · `BidCaseRouting.accountOf` 삭제 | `AuthenticatedAccountTest`(라벨 위조 불변 · 속성 부재 null · 미리 놓인 속성 소거 · 세션 E2E · 자격 헤더 401) · `AccountCarrierStructureTest`(javap 집합 등식 셋 + 양성 대조) · 변이 ⑭ | 출하 |
| (발견) 요청 값의 도메인 `require` 실패가 500 | `BidCaseRequests.requestValue` — `IllegalArgumentException` 만 400 으로 | `BidCaseRequestsTest`(차수 형식 · 길이 상한 둘 · 추첨번호 부호 + 양성 대조 둘) | 출하 — D-7B-2 test 를 쓰다 발견 |
| (발견) OpenAPI 생성 타입이 문서와 어긋나 있었다 | `web/src/api/generated/operator-api.ts` 재생성(「OpenAPI 생성 타입 재생성 …」 커밋) | `one-command-check.sh` W-5 바이트 등식 | 출하 — 수정 라운드의 OpenAPI 편집 뒤 재생성 누락, W-5 가 잡았다 |
| (발견) 입찰 건 이중체가 `AccountId` 를 참조 동일성으로 비교 | `InMemoryBidCaseStore` · `BidCaseWorkflowTest` 의 Fake — 값 비교 | 세션 E2E(요청마다 새 인스턴스) | 출하 — test 이중체 결함, production(SQL 값 비교)은 무관 |

### 1. 판정 대상 커밋

커밋은 **제목으로** 가리킨다 — 이 slice 는 두 번 rebase 됐고(`a61ddff1` · `56bb7474`) 그때마다 SHA 가 바뀌었다. 판정 SHA 는 동결
보고와 PR 조치 코멘트가 정본이다.

| 커밋(제목) | 내용 |
|---|---|
| feat 「입찰 건 도메인·workflow·V21·영속」 | 산출물 1 — 도메인 · workflow · V21 · 영속 |
| feat 「HTTP 경계·조립·서버 스냅샷」 | 산출물 2 — HTTP 경계 · 조립 · 서버 스냅샷(변이 실측 뒤 보강) |
| fix 「기계 전수 게이트 대응」 | 반사 제거 · 주입 등재 · 본문 모양 일치 |
| refactor 「저장 어휘 함수·인자 이름 …」 · feat 「계정 전용 경로 선언 …」 · fix 「목록·타임라인 응답을 맨 위 배열로」 · fix 「게이트 등재 …」 · test 「입찰 건 test double …」 | 수정 라운드 — 어휘 이름 · 계정 전용 경로와 공유 test 모집단 · 맨 위 배열 응답 · 게이트 등재 · test double. 「계정 전용 경로 선언」 커밋 제목의 「(D-7B-2)」는 **옛 표기**다(아래 §3.1 — 새 D-7B-2 와 다른 것, 이력은 되쓰지 않는다) |
| feat 「계약 D-7B-2 — typed AccountId 를 요청 속성으로 운반 …」 · test 「입찰 건 이중체의 계정 비교를 값으로」 · test 「요청 값 test …」 · test 「append-only test 가 초안·기록 행을 심는다」 | 계약 D-7B-2 와 그 라운드의 발견 셋(§3.4) |
| docs 커밋들 | 계약 · 설계 · evidence |

base `56bb7474`. 변경 목록은 `git diff --name-status 56bb7474..HEAD` 기계 산출이다.

### 2. 변이 표 — ①~⑨ 9/9 적발 · ⑩~⑭ 아래와 §3.2 · §3.4

각 변이를 적용 → 표적 test 실행 → **되돌린다**(스크립트가 `.bak` 로 복원하고, 복원 뒤
`git status` 로 잔여 0 을 확인했다). 기대는 전부 RED 다.

| # | 변이 | 표적 | 결과 |
|---|---|---|---|
| ① | `SELECT_CASE_BY_ID` 에서 `account_id = ?` 제거 | `BidCaseOwnershipTest` | **RED** — 「남의 건은 id 를 알아도 읽히지 않는다」 외 1 |
| ② | 소급 판정을 상수 `false` 로 | `BidInvariantsTest` | **RED** — 「소급 표시는 출발 상태가 정한다」 외 1 |
| ③ | 전이표에 금지 전이 추가(`WON → SKIP`) | `BidTransitionTableTest` | **RED**(보강 뒤 — 아래 ⑴) |
| ④ | 다음 확인 시점을 `Watching` 에서 `None` 으로 | `NextCheckpointTest` | **RED** — 「전 상태에서 답이 나오고 종류가 기대와 같다」 |
| ⑤ | `asReal()` 이 페이퍼도 통과시키게 | `BidInvariantsTest` | **RED** — 「페이퍼 건은 실투찰로 좁혀지지 않는다」 |
| ⑥ | 보관 기간 정책 3년 → 1년 | `EvidenceRetentionPolicyTest` | **RED**(보강 뒤 — 아래 ⑵) |
| ⑦ | 타임라인 append-only 트리거 제거 | `CleanMigrationTriggerTest` | **RED** — 3 test |
| ⑧ | 「건너뜀 ⇒ 사유」 CHECK 제거 | `CleanMigrationCheckTest` | **RED** |
| ⑨ | `bid_case` GRANT 를 표 단위 UPDATE 로 확대 | `BidCaseOwnershipTest` · `CleanMigrationPrivilegeTest` | **RED** — 2 test |

**⑴·⑵ 는 첫 측정에서 GREEN 이었다 — 그것이 이 라운드의 실질 소득이다.**

- **⑴ 전이표 전수 test 가 순환이었다.** 「표에 있으면 허용 · 없으면 거부」를 재는 두 test 가
  **둘 다 `BID_TRANSITIONS` 를 읽었다.** 그래서 표에 금지 전이 한 줄을 더하면 test 가 그 줄을
  따라가며 초록을 유지했다(실측: `WON → SKIP` 추가 후 전건 초록). 정본을 코드 밖에 한 벌 더
  두는 것 말고는 고칠 방법이 없다 — `ux-journey-research.md` §6.1 에서 손으로 옮긴 골든 목록
  (30 행)을 두고 표와 **집합 등식**으로 맞댄다. 재측정 RED.
- **⑵ 출하 정책 값을 아무도 읽지 않았다.** `BidCaseWorkflowTest` 는 정책을 **주입해서**
  날짜 산술을 재므로 출하 상수를 1년으로 바꿔도 전건 초록이었다. 운영자가 정한 값(R-5 3년)이
  코드에 실제로 들어 있는지는 그 상수를 직접 읽는 자리가 있어야 잡힌다 —
  `EvidenceRetentionPolicyTest` 신설. 재측정 RED.

**도메인 성질의 행동 변이와 래칫 변이는 다른 것을 증명한다.** 위 ⑦⑧⑨ 는 **래칫이 문다**는
증거이고(등재와 스키마가 어긋나면 붉다), 도메인 성질이 **행동으로** 덮이는지는 따로 재야
한다(팀장 지적 2026-10-22). 지금 행동으로 덮인 것은 소유 경계 ① · 소급 ② · 다음 확인 시점 ④ ·
페이퍼/실 ⑤ 다. **append-only 와 스냅샷 불변**의 행동 변이 둘은 아래에 더한다.

| # | 변이 | 표적 | 결과 |
|---|---|---|---|
| ⑩a | 초안 둘째부터 `INSERT` 대신 **같은 행 `UPDATE`**(방어 두 겹 유지) | `BidCaseOwnershipTest`「초안 수정은 새 행을 쌓고」 | **RED** — DB 가 거부(`bid_draft` append-only 트리거의 예외). test 단언이 아니라 **방어가** 막은 것 |
| ⑩b | ⑩a + `bid_draft` 트리거 제거(UPDATE 가 실제로 성공하게) | 같은 test | **RED** — **단언이** 잡는다: 첫 초안 금액이 둘째 금액으로 덮였다 |
| ⑪ | 세 표 트리거 **와** 앱 역할 UPDATE·DELETE GRANT 를 **함께** 연다 | `BidCaseOwnershipTest`「앱 역할은 … 고치거나 지울 수 없다」 | **RED** — 첫 문장(`UPDATE bid_case_transition …`)부터 통과 |
| ⑪t | 대조: 트리거만 제거(GRANT 유지) | 같은 test | **GREEN** — 권한 층 혼자 막는다 |
| ⑪g | 대조: GRANT 만 연다(트리거 유지) | 같은 test | **GREEN**(test 보강 뒤) — 트리거 층 혼자 막는다. **보강 전에는 RED 였다**(아래) |
| ⑫ | 어느 인증 축에도 안 닿는 새 POST 매핑(항상 401) | `ProductionHttpSurfaceTest` 본문 거부 전수 | **RED** — `unreachable == ∅` 단언이 그 경로를 내놓았다 |

측정 자리: ⑩a · ⑩b · ⑫ 는 「요청 값 test …」 커밋, ⑪ · ⑪t · ⑪g 는 「append-only test 가 초안·기록 행을 심는다」 커밋. 변이마다
표적 test 만 돌리고 `git restore` 로 되돌린 뒤 작업 트리가 깨끗한지 확인했다(전부 깨끗).

**⑪g 가 test 의 공백을 잡았다 — 앞 판의 「두 겹이 각각 충분하다」는 반만 재고 있었다.** 앞 판 test 는 건만 열고 초안 · 기록을
심지 않았다. `FOR EACH ROW` 트리거는 **0 행 UPDATE · DELETE 에서 불리지 않으므로**, GRANT 만 열자 `UPDATE bid_draft …` 가 0 행으로
「성공」해 붉었다 — 트리거 층이 그 두 표에서 **행동으로는 한 번도 재지지 않았다**(래칫 ⑦ 만 있었다). test 가 초안 · 기록 행을 심고
그 수를 먼저 단언하게 고쳤고, 재측정에서 ⑪t · ⑪g 둘 다 초록, ⑪ 만 RED 다. 이제 「한 겹씩 열면 초록, 두 겹을 함께 열면 RED」가
세 표 모두에서 **실측**이다.

**래칫이 등재 없이는 초록이 되지 않는다는 것도 실측했다**: V21 을 넣고 등재 전에 돌린 첫
실행에서 스키마 래칫 여섯 파일의 **12 test 가 전부 붉었다**(표 목록 · 컬럼 · 인덱스 · CHECK ·
트리거 · 권한 행렬 · FK · UNIQUE · 보존 집합 등식). 등재는 그 붉은 것을 하나씩 끈 것이다.

### 3. 넓힌 게이트 줄과 근거

| 파일 | 키 | 변경 | 근거 |
|---|---|---|---|
| `config/quality/architecture-policy.properties` | `layer.domain`(:44) | `+bidding` | 새 domain 모듈. 이 값이 층 판정·모집단 단언의 **단일 출처**이고, 빠뜨리면 모듈이 어느 층도 아니게 된다 |
| 〃 | `app.injection.domain-port-roots`(:608) | `+bidvector.bidding` | controller 생성자가 bidding 타입을 이름 부른다(「이 저장소가 이름 붙인 계약의 뿌리」) |
| 〃 | `collection.reflection.roots`(:1356) | `+bidvector.bidding` | **판정 대상 뿌리**다. 빠뜨리면 새 모듈의 반사가 조용히 안 재진다 |
| `config/quality/gate-tests.properties` | `gate.tests.bidding`(신설) | test 3 | 모듈 신설. 없으면 `gateRegistrationGate` 양방향 등식에서 즉시 붉다 |
| 〃 | `gate.tests.workflow` | +2 | `BidCaseWorkflowTest` · `EvidenceRetentionPolicyTest`(⑵ 의 산물) |
| 〃 | `gate.tests.adapters` | +1 | `BidCaseOwnershipTest` — 실 DB 소유 경계 |
| `settings.gradle.kts` | `include(...)` | `+bidding` | ADR 0006 D-2 되살림 |
| `app/src/test/.../ProductionHttpSurfaceTest.kt` | 본문 거부 전수 test | **모집단을 판정으로 가름** | 아래 §3.1 |
| `config/quality/architecture-policy.properties` | `app.http.allowed-packages` | `+bidvector.bidding` · `+bidvector.workflow.bidding` | controller 가 요청을 command 로 옮기려면 그 어휘를 이름 불러야 한다 |
| 〃 | `app.http.denied-types` | `+` 저장·조회 포트 셋 | 패키지를 열었으므로 **포트만 다시 닫는다** — controller 가 받는 것은 use case 하나다(D-6A2b-8) |
| 〃 | `app.injection.allowed-types` | `+3` | `BidCaseWorkflow`(두 controller 의 단 하나의 협력자) · `SubmittedBidAmount`(요청 값 타입) — 응답 원소 타입 둘은 목록을 맨 위 배열로 바꾸며 표면에서 빠져 등재도 뺐다 — 그리고 **D-7B-2 의 `AccountId`**(`AuthenticationResult.Authenticated` 생성자 인자 — 이 게이트는 data class 생성자 인자도 주입으로 센다) |
| 〃 | `app.surface.api.handlers` · `app.assembly.tier1-bootstrap-classes` | `+2` · `+1` | 두 controller · `BidCaseWiring` |
| `config/quality/gate-tests.properties` | `gate.tests.app` | `+4` | `OpenApiSecurityContractTest`(분할) · D-7B-2 의 `AuthenticatedAccountTest` · `AccountCarrierStructureTest` · 발견 결함의 `BidCaseRequestsTest` |
| `app/src/test/.../AppHttpDependencyGateTest.kt` | `EXPECTED_EXEMPT_COUNT` | 65 → **66** | `BidCaseWiring`(§3.3) |
| `app/src/test/.../ConstantTimeComparisonStructureTest.kt` | 허용 목록 | `+8` | 입찰 건 HTTP 층의 data class 자동 equals · sealed when 소진 · 공개 어휘 비교 — 각 항목 근거 문장 |

### 3.1 공유 게이트 test 변경 — `ProductionHttpSurfaceTest` (팀장 결정 2026-10-22 (b) — 계약 D-7B-2(2026-10-23)와 다른 것)

**무엇이 충돌했나.** 그 test 는 `X-Operator-Credential` 하나로 **등록된 POST 매핑 전수**가
같은 모양(415/400/400)으로 본문을 거부하는지 잰다. 그런데 입찰함은 **계정 전용**이다 —
인증 경로 둘 중 계정을 내는 것은 세션뿐이고(자격 헤더의 주체는 `operator`, 계정 행이 없다),
소유 경계(R-2)를 세우려면 `account.id` 가 있어야 한다. 그래서 자격 헤더로는 401 이고,
전수 집합에 `(401, UNAUTHENTICATED)` 가 섞여 test 가 붉었다.

**고른 길과 근거.** 자격 헤더를 어떤 계정에 묶는 것(선택지 a)은 **R-2 남은 절반의 식별자
모델 결정**이라 이 slice 에서 할 수 없다. 소유 경계를 포기하는 것(c)은 논외다. 그래서 (b) —
**모집단을 「그 자격으로 실제로 닿는 매핑」으로 가른다.** 그 test 가 재는 성질은 「본문 거부의
모양이 매핑마다 갈리지 않는다」이고, **인증 단계에서 갈리는 것은 그 축이 아니다.**

**어떻게 갈랐나(조건 1 — 기계로).** 경로 문자열을 test 에 적지 않는다. 매핑마다 **두 축으로
한 번씩 두드려 401 인지 본다** — 401 이 아니면 그 축이 닿은 것이다. 즉 기준이 선언이 아니라
**필터·인증기의 실제 판정**이다. 선언(`AccountScopedRoutes`)은 그 판정과 **맞대는 쪽**으로만
쓴다(아래 셋째 단언).

**단언 셋(조건 2 — 합집합·교집합).**
1. `unreachable == ∅` — 어느 축으로도 안 닿는 매핑이 0. 이것이 커버리지가 줄지 않는다는
   보증이고, 조건 3 변이의 앵커다.
2. `credentialAxis ∪ sessionAxis == POST 매핑 전수`.
3. `credentialAxis` 에 계정 전용 경로가 **없고**, 계정 전용 경로 집합 == `sessionAxis −
   credentialAxis`. 선언과 판정이 어긋나면(예: 입찰함 매핑이 자격 헤더로 닿게 되면) 붉다.

본문 거부 모양은 **닿는 축의 자격으로** 매핑마다 잰다 — 축이 달라도 성질은 같아야 한다.

**조건 3 변이**: 어느 모집단에도 들지 않는 새 POST 매핑을 심으면 ①이 RED 여야 한다. **실측 RED**(변이 ⑫, §2).

**넓히지 않은 것**: `collection.external.allowed-packages.*` 는 application·adapters·app 층
키라 domain 모듈 신설과 무관하다. `module.expected-source-sets` 도 그대로다(`main,test`).

### 3.2 7B 가 바꾼 공유 게이트 test (팀장 조건 2026-10-22)

**둘이다.** 둘 다 **같은 설계 사실** 때문에 바뀌었다 — 입찰함은 계정 전용이고, 기존 두 test 는
「모든 매핑/operation 이 자격 헤더로 닿는다」를 전제하고 있었다. 전제가 깨진 것이지 성질이
약해진 것이 아니다. 아래에 전/후 단언과 **약화가 아닌 이유**, 그리고 **여전히 문다는 변이**를
적는다.

---

#### ① `ProductionHttpSurfaceTest` — 본문 거부 전수

| | 단언 |
|---|---|
| **전** | 자격 헤더 하나로 **모든 POST 매핑**을 두드려 `(415/400/400)` 집합이 하나인지 |
| **후** | 매핑마다 **두 축으로 두드려 닿는 축을 판정**하고 ⓐ `unreachable == ∅` ⓑ `credentialAxis ∪ sessionAxis == POST 전수` ⓒ 계정 전용 == `sessionAxis − credentialAxis` ⓓ **닿는 축의 자격으로** 같은 `(415/400/400)` 집합 |

**약화가 아닌 이유.** 재던 성질(본문 거부의 모양이 매핑마다 갈리지 않는다)은 **그대로 전수**
로 남는다 — 모집단이 준 것이 아니라 **각 매핑을 실제로 닿는 자격으로 재게** 됐다. 전 판은
계정 전용 매핑에 대해 그 성질을 **아예 재지 못했다**(401 에서 멈추니 본문 처리에 닿지 못한다).
그리고 단언 ⓐ 가 **새로 생겼다**: 어떤 자격으로도 닿지 않는 매핑이 0 이라는 보증은 전 판에
없던 것이다.

#### ② `OpenApiContractTest` — 자격 헤더 대안

| | 단언 |
|---|---|
| **전** | 인증을 요구하는 **모든** operation 이 `operatorCredential` 대안을 적는다 |
| **후** | **비계정** operation 은 그대로(변경 없음) + **계정 전용** operation 은 `browserSession`(상태 변경이면 `+operatorCsrf`)**만** 적는다 — 집합 **정확 일치** |

**약화가 아닌 이유.** 전 판이 막던 것은 「문서가 구현보다 **좁아짐**」이다(필터는 자격 헤더를
받는데 문서가 안 적으면 호출자가 막힌 줄 안다). 계정 전용 operation 은 전제가 **반대**다 —
구현이 실제로 401 이므로 `operatorCredential` 을 적으면 문서가 구현보다 **넓어지고** 호출자가
되는 줄 알고 401 을 받는다. 같은 결함의 반대쪽이다. 그래서 그쪽은 **양방향 정확 일치**로
재는데, 이것은 전 판의 「포함」보다 **더 조인 단언**이다(대안을 더해도 빼도 붉다).

---

**파일 분할(2026-10-22)**: 축을 더하자 `OpenApiContractTest` 가 500줄을 넘었다. **한도를 올리지
않고** 책임으로 갈랐다 — 보안 선언 축(무인증 집합 · CSRF 묶임 · 자격 헤더 대안 · 계정 전용 ·
읽기 쿠키 대안)이 `OpenApiSecurityContractTest` 로 나갔고, 남은 쪽은 응답의 **모양**(평탄 ·
키 집합 · ErrorBody)을 잰다. 두 축이 한 파일에 있으면 「인증 선언이 바뀌었다」와 「응답 모양이
바뀌었다」가 같은 실패로 보인다. 새 class 는 `gate.tests.app` 에 등재했고(그 키는 app test
**전수** 등식이다), **변이 ⑬ 의 표적도 함께 옮겼다**.

#### 변이 ⑬ — 두 test 가 여전히 문다

| # | 변이 | 표적 | 기대 | 결과 |
|---|---|---|---|---|
| ⑬a | 계정 전용 op(`listBidCases`)에 `operatorCredential` 대안을 **더한다**(문서가 넓어짐) | `OpenApiSecurityContractTest` | RED | **RED** — 「계정 전용 operation 은 세션만 적는다」 1 test(어긋난 op `GET /api/bid-cases`) |
| ⑬b | 문서 기본 `security` 에서 `operatorCredential` 을 **뺀다**(비계정 op 가 상속 — 전 판이 막던 바로 그것) | `OpenApiSecurityContractTest` | RED | **RED** — 「인증을 요구하는 operation 은 전부 자격 헤더 대안을 남긴다」 1 test(어긋난 op `GET /api/strategy` 외 여럿) |
| ⑬c | `AccountScopedRoutes.ROOTS` 를 비운다(선언↔판정 대조 파괴) | `ProductionHttpSurfaceTest` · `OpenApiSecurityContractTest` | RED | **RED 둘 다** — 표면: 선언↔판정 등식 · 문서: 「세션만 적는다」(비어 있음) + 「자격 헤더 대안」(입찰함 op 가 비계정으로 넘어감) — 2 test |
| ⑫ | 어느 축에도 안 닿는 새 POST 매핑 | `ProductionHttpSurfaceTest` ⓐ | RED | **RED** — §2 표 |

⑬b 가 **전 판이 지키던 성질이 그대로 살아 있다**는 증거이고, ⑬a 는 **새로 조인 쪽**이 실제로
무는다는 증거다. ⑬c 는 선언을 지우면 두 test 가 **함께** 붉어진다는 것 — 선언 한 자리를
고쳐 둘 다 조용히 통과시키는 길이 없다.

### 3.3 머지 때 충돌할 자리 (후행 레인이 재계산)

- **`EXPECTED_EXEMPT_COUNT`**(`AppHttpDependencyGateTest`) — 7B 는 `BidCaseWiring` 하나를 더한다.
  `56bb7474` 위 rebase 에서 이미 한 번 충돌했다: main 이 7C⑤-0 으로 62 → 65 였고, **합집합 66** 으로
  풀었다(근거 문단 둘 다 유지 · `check` 초록). **7C①b 레인(+2)이 뒤에 머지되면 그 레인이 다시
  합집합으로 센다**(팀장 지시 2026-10-22) — 수만 다시 세고 **사유를 지우지 않는다**.
- **`capability-map.md` §10 집계**(95 → 96 · `V2 필수` 61 → 62)와 **활성 OPEN 수**(45 → 44) —
  §10 블록에 「머지 직전 재계산」 표시를 문면에 넣어 두었다. g1dec05 · g1set01 이 같은 파일의
  다른 행을 고친다.
- **`config/quality` 의 등재 키들** — 7B 는 **자기 항목만** 더했다(`layer.domain` ·
  `app.injection.*` · `app.http.*` · `gate.tests.*` · `app.surface.api.handlers` ·
  `app.assembly.tier1-*`). 같은 키를 다른 레인이 함께 늘리면 줄 충돌이지 의미 충돌이 아니다.

### 3.4 계약 D-7B-2 — typed 계정 운반 (계약 갱신으로 추가, 2026-10-23)

**무엇을 바꿨나.** 앞 판은 계정 범위 핸들러가 audit 주체 라벨(`account:<값>`)을 `removePrefix` + `toLongOrNull` 로
되읽어 `AccountId` 를 얻었다. 소유 경계(R-2)가 문자열 왕복 위에 서 있었다 — 라벨 규약(접두 · 구분자 · 숫자 표기)이 바뀌면
조용히 다른 계정이나 `null` 이 나온다.

- `AuthenticationResult.Authenticated` 가 `account: AccountId?` 를 나른다. **세션 경로만** 채운다(자격 헤더 경로의 주체
  `operator` 에는 계정이 없다).
- `RequestAuthenticationFilter` 가 **판정 전에** 계정 속성을 지우고(fail-closed), 통과한 경로가 계정을 냈을 때만 다시 적는다.
  속성을 적는 자리는 이 필터 하나다.
- 계정 범위 핸들러(두 controller · `BidCaseHttp`)는 `request.authenticatedAccount()` 하나로 읽는다(`AuthenticatedAccount.kt`).
  `BidCaseRouting.accountOf` 와 접두 상수는 지웠다. audit 라벨은 그대로다(감사 행이 쓰는 값).
- 7C② · 7C③ · 7C④ 가 이 접근자를 공유한다(7B 선행 머지).

**재는 자리.**

| 계약 문면 | test | 결과 |
|---|---|---|
| 라벨을 위조 · 변경해도 계정이 바뀌지 않는다 | `AuthenticatedAccountTest`「주체 라벨을 바꿔도 계정은 바뀌지 않는다」 | 초록 |
| 속성 부재 → 401 | 같은 class「주체 라벨만 있고 계정 속성이 없으면 계정은 없다」 · 「자격 헤더로는 입찰함이 401 이다」(실 HTTP) · 「판정 전에 놓인 계정 속성은 지워진다 — 통과해도 거부돼도」 | 초록 |
| 실제 인증 경로가 계정을 심는다 | 「세션으로 닿으면 입찰함이 그 세션의 계정으로 열린다」(실 로그인 → 담기 201 → 목록 200) | 초록 |
| production 의 문자열 파싱 경로 0(구조) | `AccountCarrierStructureTest` — `bidvector.app` 컴파일 class 전수를 javap 로 읽어 **집합 등식** 셋: `account:` 로 시작하는 상수를 가진 class = 라벨을 만드는 `BrowserSessionAuthenticator$Companion` 하나 · `authenticatedSubject()` 호출자 = 전략 편집의 운영자 주체 하나 · 주체 속성 키 보유 = 정의 · 필터 둘. 그리고 typed 접근자 호출자 = 계정 범위 진입점 셋. 술어 자체의 양성 대조 1 | 초록 |

**변이 ⑭ — 접근자가 라벨로 되돌아가면 붉다.**

| # | 변이 | 표적 | 결과 |
|---|---|---|---|
| ⑭ | 접근자를 `authenticatedSubject().removePrefix("account:").toLongOrNull()` 로 | `AuthenticatedAccountTest` | **RED** 2 — 위조 라벨(999)이 계정이 됐다 · 라벨만 있는데 계정이 나왔다 |
| ⑭ | 같은 변이 | `AccountCarrierStructureTest` | **RED** 2 — `account:` 상수 보유 class 가 늘었다 · 주체 접근자 호출자가 늘었다 |
| ⑭b | 접두 리터럴 없이 `substringAfter(':')` 로 | `AccountCarrierStructureTest` | **RED** 1 — 주체 접근자 호출자 등식 |

⑭b 가 구조 test 의 둘째 등식이 왜 필요한지 보인다 — 접두 문자열 술어(첫째)만 있으면 리터럴을 피한 파싱은 초록이다.
구조 술어는 문자열 grep 이 아니라 **컴파일된 상수 풀과 호출 지점**에 건다(게이트 술어는 구조로 닫는다).

**이 라운드에서 찾은 것 둘(D-7B-2 test 를 쓰다가).**

1. **요청 값의 도메인 규칙 위반이 500 이었다.** `NoticeRound` 형식("00") · 증빙 참조 128자 · 메모 2000자 상한 · 추첨번호 부호를
   어긴 요청이 값 객체의 `require` 에서 `IllegalArgumentException` 으로 새어 기본 처리기가 500 을 냈다 — 요청 탓을 서버 탓으로
   말한 것이다. 요청 판독(`BidCaseRequests`)이 **그 예외 하나만** 400(`INVALID_REQUEST`)으로 옮긴다(`JsonValueReaders` 의 시각
   판독과 같은 관례). `BidCaseRequestsTest` 가 네 자리와 양성 대조 둘을 잰다.
2. **test 이중체가 `AccountId` 를 참조 동일성으로 비교했다.** `AccountId` 는 `equals` 를 재정의하지 않으므로, 요청마다 새
   인스턴스가 오는 HTTP 경로에서는 자기 건이 남의 것으로 보였다(세션 E2E 가 처음 그 경로를 지나며 드러났다). app 의
   `InMemoryBidCaseStore` 와 workflow test 의 Fake 를 **값** 비교로 고쳤다. production(`JdbcBidCaseStore`)은 SQL 의
   `account_id = ?` 라 무관하다.
3. (§2) append-only test 가 초안 · 기록 행을 심지 않아 트리거 층을 재지 못했다 — 변이 ⑪g.
4. **OpenAPI 생성 타입이 문서와 어긋나 있었다.** 수정 라운드(맨 위 배열 응답 · 감싸개 스키마 둘 삭제 · `NOTICE_SNAPSHOT_UNAVAILABLE`)
   에서 문서만 고치고 `npm run api:types` 를 다시 돌리지 않았다. Kotlin `check` 는 이 등식을 재지 않으므로 초록이었고,
   `one-command-check.sh` 의 W-5 가 잡았다(커밋본 99636 바이트 · 재생성 99658 바이트). 재생성해 커밋했다 — 타입 선언만
   바뀌었고 web 코드에 그 타입을 쓰는 자리는 없다.

**옛 「D-7B-2」 표기.** 공유 test 모집단 분리(2026-10-22 팀장 결정 (b))를 test 주석 · evidence 에서 「계약 r2 D-7B-2」로
불렀는데, 그 이름은 `scope.md` 에 없었고 이번 계약 D-7B-2 와 겹쳤다. 주석 · evidence 를 「팀장 결정 2026-10-22 (b)」로 고쳤다.
그 라운드의 커밋 제목 하나에는 옛 표기가 남아 있다(이력은 되쓰지 않는다).

### 4. 설계 검토 (2b) — 값 획득 축

새 public 표면이 밖에 허락하는 것:

| 표면 | 밖에서 할 수 있는 것 | 닫은 방법 |
|---|---|---|
| `bidding.applyCommand` | 전이 판정을 **계산**할 수 있다 | 계산 결과로 **저장에 닿지 못한다** — 저장 포트가 받는 `AppliedTransition` 이 `workflow` 의 `internal constructor` 다 |
| `BidCaseStorePort` | — | `app` 은 이 타입의 빈을 받지 않는다. controller 는 `BidCaseWorkflow` 하나만 받는다(`BidCaseWiring`) |
| `RealBidCase` | 실투찰 전용 자리에 값을 넘긴다 | `internal constructor` + `asReal()` 이 페이퍼에 `null` — 좁히기를 건너뛴 코드는 컴파일되지 않는다 |
| HTTP 요청 DTO | 금액 · 시각 · 사유를 보낸다 | **provenance · 메뉴 참조 · retroactive · evidenceRetainUntil · 공고 스냅샷 · accountId · caseVersion 자리가 없다** |

- **「전이 use case 밖에서 건 상태를 바꾸는 경로」 0** — `bid_case` 의 UPDATE 문장은
  `JdbcBidCaseStore.updateCaseState` 하나이고, 그 호출자는 `applyTransition` 뿐이다.
  권한 층이 한 겹 더 진다(열 단위 GRANT: `account_id` · 공고 키 · `bid_kind` 는 UPDATE 불가).
- **「주체 없이 부르는 포트」 0** — `BidCaseStorePort` 의 전 메서드 첫 인자가 `AccountId` 다.
  예외는 `NoticeDeadlineQueryPort` · `NoticeSnapshotQueryPort` 둘이고, **공고는 사용자 소유
  데이터가 아니기 때문**이며 그 사유를 각 포트 KDoc 이 적는다(적지 않으면 「빠뜨린 필터」와
  구별되지 않는다).

### 5. BID-01 추적 행 (6E-1 술어로)

| capability | 분류 | 판정 | 무엇이 재는가 | 비어 있는 항목 |
|---|---|---|---|---|
| **BID-01** | `V2 필수` | **ⓐ** | 상태·전이: `BidTransitionTableTest`(골든 등식 + 전수 182 쌍) · 스냅샷 불변: `BidCaseOwnershipTest`「초안 수정은 새 행을 쌓고」 + append-only 트리거 · 다음 확인 시점: `NextCheckpointTest`(전 상태) · 소급 표시: `BidInvariantsTest` + `BidCaseOwnershipTest`「소급 확인이 기록과 소급 표시를 남긴다」 · 페이퍼/실 분리: `BidInvariantsTest` + `BidCaseOwnershipTest`「목록은 종류로 갈린다」 · 건은 하나: `BidCaseOwnershipTest` · 보관 기한: `EvidenceRetentionPolicyTest` + workflow test | 없음 — 단 **production 배선에서 성립**하는지는 7E 화면이 붙어야 끝까지 보인다(아래 알려진 제한 ②와 같은 축) |

6E-1 의 `acceptance-trace.md` 집계는 **다시 세지 않았다**(남의 slice 의 동결 evidence).
그 파일에는 모수 이동(61 → 62)을 적은 **날짜 붙은 교차 참조 한 줄**만 넣었다.

### 6. 알려진 제한

1. **`PRICE_RECEIVED` 는 오늘 도달 불가** — 진입 명령(`RECEIVE_PRICE_MENU`)이 `SYSTEM` 전용이고
   그 호출자가 출하 경로에 없다(DEC-05 메뉴는 ML-01 · 7C② 대기). 어휘 · CHECK · 전이표 행은
   두었다(나중에 CHECK 어휘를 늘리는 둘째 마이그레이션을 피한다).
2. **자동 전이의 시스템 호출자 부재** — SET-01 대사 결과 · SET-02 개찰 · 리마인드 도달.
   사용자 입력 경로는 열려 있다(§6.1 「사용자가 나라장터에서 보고 오거나 입력한다」).
3. **PDF 저장소 없음** — `evidence_ref` 가 가리킬 실체가 저장소 안에 없다. 완화 둘:
   형태 제한(공백 불가 · 128자 상한 · `toString()` 가림)과 문면 고정(「이 **참조**를 언제까지
   보관하는가」). 보관 기한 **집행**은 저장소 slice 가 연다.
4. **다수 사업자 격리 · 가입 · `operator_profile`/`operator_strategy` 계정 단위 전환**은 범위
   밖이다(운영자 결정 2026-10-21 — 별 slice).
5. **화면 없음** — 7E 소관. 이 slice 의 응답 형태는 `BidCaseResponse` 가 고정한다.
6. **자격 헤더 경로는 입찰함에 닿지 못한다.** 운영자 자격 헤더(`X-Operator-Credential`)로만
   인증하는 소비자(운영 스크립트 등)는 `/api/bid-cases` 전부에서 401 을 받는다 — 그 경로의
   주체 `operator` 에는 계정 행이 없어 소유 경계를 세울 수 없기 때문이다. 그 헤더를 계정에
   묶는 일은 **R-2 남은 절반**(식별자 모델)이 풀 때까지 열리지 않는다(운영자 결정
   2026-10-21 · 계약 r2 D-7B-1). 선언은 `AccountScopedRoutes`, 그 사실을 재는 자리는
   `ProductionHttpSurfaceTest` 의 셋째 단언이다. 계정은 세션 인증이 심은 typed 속성으로만 온다(계약 D-7B-2, §3.4).
7. **한 건에 기록을 두 번 쌓는 실 DB 행동 test 가 없다.** 전이표 골든의 `BID_RECORDED --RECORD_BID--> BID_RECORDED`
   자기 간선과 `record_seq + 1` 쓰기는 있지만, 두 번째 기록이 새 행이고 건이 하나로 남는 것을 실 DB 로 재는 test 는 없다.

### 7. acceptance 실측

`ci.yml` 의 job 명령 그대로다. 로그는 레인 scratchpad 에 있고 여기에는 결과 한 줄씩만 적는다.

| 항목 | 명령 | 자리 | 결과 |
|---|---|---|---|
| `check` job — Kotlin | `./gradlew --no-daemon check --continue` | 「요청 값 test …」 커밋 | **exit 0** · 13m23s(23:28–23:42) · 새 test 셋 6/5/4 실행 · 실패 0(XML 확인) |
| `check` job — S-20 | `./tools/one-command-check.sh`(Kotlin `check` · `qualityBaseline` · Python 전건 · web 전건) | 「OpenAPI 생성 타입 재생성 …」 커밋 | **exit 0** · pytest 1605 passed · vitest 28 passed · W-5 「바이트까지 같다(99658 bytes)」 · Playwright 2 passed. 같은 명령이 한 커밋 앞(evidence 커밋)에서는 **W-5 에서 exit 1**(§3.4 발견 4) |
| `web` job | W-1~W-11(S-20 의 web 절이 같은 명령을 돈다) | 위와 같음 | 위와 같음 |
| `container` job | `ci.yml` 의 `container` step 전부를 job env · `GITHUB_ENV` · `GITHUB_PATH` · step `if:` 평가와 함께 순서대로 재생 | evidence 커밋(「약속↔출하 표 …」) | **JOB SUCCESS** — run step 24 전부 성공(S-20z · S-21~S-21c · S-22a~e3 · S-23 · S-23b · S-23c · S-24 · S-25 · S-26), `uses:` 셋은 해당 없음. 이미지 태그는 재생 전 ID 로 복원(등식 확인) |
| 백업·복원 리허설 | `tools/db-rehearsal.sh`(container job S-23c) | 위와 같음 | **통과** — Flyway 이력 top **21** · 복원본 `validate` 통과 · 복원 등식 10가지 · forward-fix · 데이터 손실 왕복 |
| `ml-engine` job | 입력 변경 0 | — | 생략(S-20 이 Python 전건을 따로 돌렸다) |

**재생 하네스의 첫 시도는 하네스 탓으로 멈췄다**: `GITHUB_PATH` 를 흉내 내지 않아 `install trivy` 가 unbound 변수로 끝났다. 하네스를
고쳐 다시 돌린 것이 위 결과다(산출물 변경 없음).

**container 재생 뒤의 변경**: 생성 타입 재생성(타입 선언 파일 하나)은 재생 **뒤**다. web 번들의 입력이지만 타입 선언이라 산출 JS 가 바뀌지
않는다고 보고 container 를 다시 돌리지 않았다 — 같은 파일을 담은 트리의 web 빌드 · Playwright 는 S-20 이 돌렸다. 이 판단을 verifier
표적으로 남긴다.

**호스트 규율**: 모든 무거운 명령은 `flock ~/.local/bid-vector-build.lock` 아래 돌렸고, 점검 셋(pgrep · `free -m` · `ps`)을
**별도 호출로 먼저** 봤다. 락을 얻은 순간 available ≥ 6GB · swap free ≥ 2GB 를 다시 재고 그 값을 로그 첫 줄에 남겼으며(미달이면
시작하지 않는다), **00:30~03:00 에는 시작하지 않았다**(변이 스크립트는 변이마다 그 창을 확인한다).

### 8. rollback 실측

`rollback.md` 경우 ①(머지 전)을 **버릴 clone** 에서 실행했다. 절차 문면은 **지금의 정본**(worktree HEAD 의 `rollback.md`)에서 읽고
`cd` 대상만 clone 으로 바꿨다.

| 단계 | 「append-only test …」 커밋(④⑤⑥ 실측) | 「OpenAPI 생성 타입 재생성 …」 커밋(마지막 산출물) |
|---|---|---|
| 전 — 변경 종류 · 수 | A · M 뿐 · A 47 · M 32 | 같음 |
| ① 명령 exit | 0 | 0 |
| ② 결과 | D 47 · M 32 · 그 밖 0 | 같음 |
| ③ base 와 diff · untracked | 0 줄 · 0 | 0 줄 · 0 |
| 내 표지 · 남의 표지 | 614 → **0**(base 0) · main 의 vuln-allowlist 줄 3 보존 | 633 → **0** · 3 보존 |
| 되돌린 트리 객체 | `0aef0adc…` = base 트리 | `0aef0adc…` = base 트리 |
| ④⑤⑥ `./gradlew --no-daemon check` | **exit 0**(9m59s) | **트리 동일성으로 갈음** — 되돌린 트리 객체가 왼쪽과 같다 |

**첫 시도는 무효였고 그 사실이 절차의 결함을 드러냈다.** clone 을 산출물 커밋에 세우고 **그 커밋 판의** `rollback.md` 를 읽었더니,
base 를 `56bb7474` 로 옮긴 것이 뒤의 evidence 커밋이라 문면이 옛 base(`a61ddff1`)를 가리켰고, 복원이 **main 의 변경까지** 되돌렸다
(③ diff 7277 줄). 거기서 멈추고 문면을 HEAD 판에서 읽게 고쳐 다시 쟀다. 머지 전 되돌림을 실제로 할 사람도 **HEAD 의 문면**을
읽으므로 측정이 그쪽에 맞는 것이 맞다.
