# M7/7B — 입찰 건 모듈(`bidding`) · BID-01 등재 · V21 — evidence

계약: `reports/evidence/m7/7b/scope.md`(착수 계약 · 계약 r2(D-7B-1) · 계약 D-7B-2 — 커밋은 제목으로 가리킨다). base `bee7c36f` → rebase `a61ddff1` → `56bb7474` → `998b59dd`.

---

## Phase 1 — 설계 (코드·마이그레이션 쓰기 전, 팀장 답 대기)

읽은 정본: `ux-journey-research.md` §6.1 · `adr/0006-gradle-modules.md` D-2·D-2.1 ·
`decisions-2026-09-07.md` Q-7·Q-11·Q-12 · `m7-screen-data-requirements.md` 7B 행 ·
`capability-map.md` OPEN-ADR-14·§0.6·§10·STR-16 블록·활성 집계 ·
`milestone-7.md` R-2·R-5. 선례: V20 · V9 · V12 · V3 · V16 · `RetentionTableRegistry` ·
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
| `config/quality/architecture-policy.properties` | `layer.domain` | `+bidding` | 새 domain 모듈. 이 값이 층 판정·모집단 단언의 단일 출처다 |
| 〃 | `app.injection.domain-port-roots` | `+bidvector.bidding` | 「이 저장소가 이름 붙인 계약의 뿌리」 — controller 생성자가 bidding 타입을 이름 부른다 |
| 〃 | `collection.reflection.roots` | `+bidvector.bidding` | **판정 대상 뿌리**다. 빠뜨리면 새 모듈의 반사가 조용히 안 재진다 |
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
   사용자 입력 경로는 열려 있다(§6.1 「몇 위였나」 문장 — 사용자가 보고 오거나 입력한다).
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
| 2 초안 스냅샷 불변(서버가 뜸) | `BidDraft.kt` · `JdbcNoticeSnapshotQuery`(정본 코덱) · V21 `bid_draft` append-only · 출처 어휘 CHECK | `BidCaseOwnershipTest`「초안 수정은 새 행을 쌓고」 · `BidCaseSnapshotTest`(기초금액 · 하한율 여덟 열) · 변이 ⑩ · B1-read · B1-write | 출하 — 앞 판은 기초금액이 production 에서 늘 비었다(C-B1) |
| 2 기록 갱신해도 건은 하나 | `BidRecord.kt` · V21 `bid_record`(`record_seq + 1` 새 행) · 담기는 (공고, 종류)당 건 하나 | 전이표 골든의 자기 간선 · `BidCaseColumnPrivilegeTest`「출하 저장소가 앱 역할 연결로 끝까지 돈다」(기록 둘 · 건 하나 · 실 DB) · `BidCaseOwnershipTest`「같은 공고 같은 종류를 두 번 담아도 건은 하나다」 | 출하 — 앞 판의 「실 DB 행동 test 없음」 공백은 이 라운드에 닫혔다 |
| 2 소급 확인 무기한 + 표시 | `BidCaseWorkflow` + `DeadlineSettlement`(마감은 서버가 판정, 결정 ①) · `bid_case.deadline_at` 사본 | `BidCaseScheduleTest` 다섯(소급 기록 · 타임라인 순서 · 파기된 공고 · 마감 전 무전이 · 앱 역할) · `BidInvariantsTest` · 변이 ② · D1-lazy2 | 출하 — 앞 판은 출하 경로에서 도달 불가였다(V-H1) |
| 2 타임라인 append-only | V21 트리거 셋 + GRANT | `BidCaseOwnershipTest`「앱 역할은 … 고치거나 지울 수 없다」 · 변이 ⑦ ⑪ | 출하 |
| 2 다음 확인 시점 비지 않음(sealed) | `NextCheckpoint.kt` | `NextCheckpointTest`(전 상태) · 변이 ④ | 출하 — **통보 예정일 후보는 쓰는 경로가 없어 도달 불가**(§6 · `OPEN-7B-NOTICE-EXPECTED-WRITER`), 전이가 그 값을 지우지는 않는다(결정 ②) |
| 2 페이퍼/실 분리(구조) | `BidCase.kt` 의 좁힘 · 목록 `kind` 필수 | `BidInvariantsTest` · `BidCaseOwnershipTest`「목록은 종류로 갈린다」 · 변이 ⑤ | 출하 |
| 2 SET-02 후보 술어 | `ReconciliationCandidate.kt` | `BidInvariantsTest` | 출하(대사 자체는 SET 소유) |
| 3 보관 정책 3년(versioned) | `EvidenceRetentionPolicyData.kt` · `RetentionTableRegistry` 등재 | `EvidenceRetentionPolicyTest` · 변이 ⑥ | 출하 — PDF 저장소·파기 실행은 범위 밖(§6) |
| 4 포트가 주체를 받는다 · 남의 건 거부 | `BiddingPorts.kt` · `JdbcBidCaseStore`(모든 SQL `account_id = ?`) | `BidCaseOwnershipTest`(실 DB) · 변이 ① | 출하 |
| 5 V21 표 넷 · `account_id` FK · 열 단위 GRANT · CHECK | `V21__bid_case.sql` — 자식 셋은 (건, 계정) 복합 FK · 타임라인 어휘 CHECK · 사유 길이 · 추첨번호 부호(D-7B-3) | `CleanMigration*Test` 여섯 · `BidCaseColumnPrivilegeTest`(열 UPDATE 등식) · `BidCaseIntegrityTest` · 변이 ⑧ ⑨ · V-M7 · FK-old · VOC-old | 출하 — `notice` FK 없음(D-7B-1) |
| 5 R-2 남은 절반 | — | — | 별 slice(D-7B-1 운영자 결정) |
| 6 operation 열넷 · 새 무인증 0 · 상태 변경 CSRF | `BidCaseCommandController` · `BidCaseQueryController` · `openapi/…yaml` · 생성 타입 | `ProductionHttpSurfaceTest` · `OpenApiSecurityContractTest` · `OpenApiRequestBodyContractTest`(본문 선언 ↔ 매핑 등식) · `BidCaseEndpointTest` · 변이 ⑫ ⑬ · D4-old · M11-old | 출하 — 담기는 201/200, `resume` 은 본문 없음(D-7B-3) |
| 6 초안 금액 provenance(메뉴 위장 금지) | 요청 DTO 에 자리 없음 · 서버가 `OPERATOR_ENTERED` | V21 CHECK · `BidCaseRequests` | 출하 — `PRICE_MENU` 도달 불가(§6) |
| 7 각 핵심 성질이 변이에서 RED | §2 표 | §2 | 실측 |
| 8 개인정보 — 사업자번호·대표자명 슬롯 0 · 메모 로그/audit 노출 0 | V21 · 응답에 본문 자리 없음 | `BidTimelineEntryResponse`(본문 없음) · `leakPatternGate` | 출하 |
| 9 문서 — milestone-7 R-5 닫음 · milestone-6 예외 줄 · runbook · 리허설 | `milestone-7.md` · `milestone-6.md` · `docs/runbook/m6-6b3-retention.md` | §7 `db-rehearsal.sh` | 출하 |
| **D-7B-2 (계약 갱신으로 추가)** typed `AccountId` 운반 · 라벨 파싱 0 | `AuthenticatedAccount.kt`(속성 키 · 접근자) · `AuthenticationResult.Authenticated.account` · 필터가 판정 전에 지우고 다시 적음 · `BidCaseRouting.accountOf` 삭제 | `AuthenticatedAccountTest`(라벨 위조 불변 · 속성 부재 null · 미리 놓인 속성 소거 · 세션 E2E · 자격 헤더 401) · `AccountCarrierStructureTest`(javap 집합 등식 셋 + 양성 대조) · 변이 ⑭ | 출하 |
| **D-7B-3 (계약 갱신으로 추가)** 검토 r1 처분 — 결정 ① ~ ④ + 고칠 목록 | §3.5 처분표의 「출하」 칸 | §3.5 처분표 · 변이 표(r1) | 출하 — 근거 있는 거부 하나(C-M6) · 등재 셋(C-L5 · C-L6 · C-L7) |
| **D-7B-4 (계약 갱신으로 추가)** 검토 r2 처분 — 결정 ⑤ ~ ⑧ · 게이트 우회 둘 · 고칠 목록 | §3.6 처분표의 「출하」 칸 | §3.6 처분표 · 변이 표(r2) | 출하 — 등재 넷(C2-N10 · C2-N12 · C2-N19 · V2-W6 사실) · OPEN 후보 둘(활성 등재는 팀장 문서 PR) |
| **D-7B-5 (계약 갱신으로 추가)** 검토 r3 승인 전 일괄 — 게이트 보강 둘 · 고칠 목록 · 등재 | §3.7 처분표의 「출하」 칸 | §3.7 처분표 · 변이 표(승인 전 일괄) | 출하 — 등재 넷(C3-M1 · V3-L1 · C3-L2 · C3-Q1) · OPEN 후보 셋 |
| (발견) 요청 값의 도메인 `require` 실패가 500 | `BidCaseRequests.requestValue` — `IllegalArgumentException` 만 400 으로 | `BidCaseRequestsTest`(차수 형식 · 길이 상한 둘 · 추첨번호 부호 + 양성 대조 둘) | 출하 — D-7B-2 test 를 쓰다 발견 |
| (발견) OpenAPI 생성 타입이 문서와 어긋나 있었다 | `web/src/api/generated/operator-api.ts` 재생성(「OpenAPI 생성 타입 재생성 …」 커밋) | `one-command-check.sh` W-5 바이트 등식 | 출하 — 수정 라운드의 OpenAPI 편집 뒤 재생성 누락, W-5 가 잡았다 |
| (발견) 입찰 건 이중체가 `AccountId` 를 참조 동일성으로 비교 | `InMemoryBidCaseStore` · `BidCaseWorkflowTest` 의 Fake — 값 비교 | 세션 E2E(요청마다 새 인스턴스) | 출하 — test 이중체 결함, production(SQL 값 비교)은 무관 |

### 1. 판정 대상 커밋

커밋은 **제목으로** 가리킨다 — 이 slice 는 세 번 rebase 됐고(`a61ddff1` · `56bb7474` · `998b59dd`) 그때마다 SHA 가 바뀌었다. 판정 SHA 는 동결
보고와 PR 조치 코멘트가 정본이다.

| 커밋(제목) | 내용 |
|---|---|
| feat 「입찰 건 도메인·workflow·V21·영속」 | 산출물 1 — 도메인 · workflow · V21 · 영속 |
| feat 「HTTP 경계·조립·서버 스냅샷」 | 산출물 2 — HTTP 경계 · 조립 · 서버 스냅샷(변이 실측 뒤 보강) |
| fix 「기계 전수 게이트 대응」 | 반사 제거 · 주입 등재 · 본문 모양 일치 |
| refactor 「저장 어휘 함수·인자 이름 …」 · feat 「계정 전용 경로 선언 …」 · fix 「목록·타임라인 응답을 맨 위 배열로」 · fix 「게이트 등재 …」 · test 「입찰 건 test double …」 | 수정 라운드 — 어휘 이름 · 계정 전용 경로와 공유 test 모집단 · 맨 위 배열 응답 · 게이트 등재 · test double. 「계정 전용 경로 선언」 커밋 제목의 「(D-7B-2)」는 **옛 표기**다(아래 §3.1 — 새 D-7B-2 와 다른 것, 이력은 되쓰지 않는다) |
| feat 「계약 D-7B-2 — typed AccountId 를 요청 속성으로 운반 …」 · test 「입찰 건 이중체의 계정 비교를 값으로」 · test 「요청 값 test …」 · test 「append-only test 가 초안·기록 행을 심는다」 | 계약 D-7B-2 와 그 라운드의 발견 셋(§3.4) |
| feat 「전이표에 시스템 되돌림 간선 …」 · feat 「마감 판정 두 방향 …」 · fix 「V21·영속 — 되돌림 명령 어휘 …」 · fix 「HTTP — resume 400 선언 …」 · fix 「detekt(함수 수 · 복잡도) …」 · test 「사용자가 적은 마감 지남은 되돌리지 않는다 …」 | 수정 라운드 2(계약 D-7B-4, §3.6). 마지막 산출물 커밋은 맨 뒤의 test 커밋이다 |
| fix 「승인 전 일괄 — 마감 지남 진입은 시스템 전용 …」 · fix 「승인 전 일괄 HTTP — 데이터 바인딩 진입점 등식 …」 | 승인 전 일괄(계약 D-7B-5, §3.7). 마지막 산출물 커밋은 뒤의 HTTP 커밋이다 |
| docs 커밋들 | 계약 · 설계 · evidence |

base `998b59dd`. 변경 목록은 `git diff --name-status 998b59dd..HEAD` 기계 산출이다.

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
| `config/quality/architecture-policy.properties` | `layer.domain` | `+bidding` | 새 domain 모듈. 이 값이 층 판정·모집단 단언의 **단일 출처**이고, 빠뜨리면 모듈이 어느 층도 아니게 된다 |
| 〃 | `app.injection.domain-port-roots` | `+bidvector.bidding` | controller 생성자가 bidding 타입을 이름 부른다(「이 저장소가 이름 붙인 계약의 뿌리」) |
| 〃 | `collection.reflection.roots` | `+bidvector.bidding` | **판정 대상 뿌리**다. 빠뜨리면 새 모듈의 반사가 조용히 안 재진다 |
| `config/quality/gate-tests.properties` | `gate.tests.bidding`(신설) | test 4 | 모듈 신설. 없으면 `gateRegistrationGate` 양방향 등식에서 즉시 붉다. 넷째는 수정 라운드 1 의 `BidTransitionPropertyTest`(C-M10) |
| 〃 | `gate.tests.workflow` | +2 | `BidCaseWorkflowTest` · `EvidenceRetentionPolicyTest`(⑵ 의 산물) |
| 〃 | `gate.tests.adapters` | +7 | `BidCaseOwnershipTest` — 실 DB 소유 경계. 수정 라운드 1 이 여섯: `BidCaseColumnPrivilegeTest` · `BidCaseRowTest` · `BidCaseScheduleTest` · `BidCaseSnapshotTest` · `BiddingSqlOwnershipTest` · `BidCaseIntegrityTest`(§3.5) |
| `settings.gradle.kts` | `include(...)` | `+bidding` | ADR 0006 D-2 되살림 |
| `app/src/test/.../ProductionHttpSurfaceTest.kt` | 본문 거부 전수 test | **모집단을 판정으로 가름** | 아래 §3.1 |
| `config/quality/architecture-policy.properties` | `app.http.allowed-packages` | `+bidvector.bidding` · `+bidvector.workflow.bidding` | controller 가 요청을 command 로 옮기려면 그 어휘를 이름 불러야 한다 |
| 〃 | `app.http.denied-types` | `+` 저장·조회 포트 셋 | 패키지를 열었으므로 **포트만 다시 닫는다** — controller 가 받는 것은 use case 하나다(D-6A2b-8) |
| 〃 | `app.injection.allowed-types` | `+3` | `BidCaseWorkflow`(두 controller 의 단 하나의 협력자) · `SubmittedBidAmount`(요청 값 타입) — 응답 원소 타입 둘은 목록을 맨 위 배열로 바꾸며 표면에서 빠져 등재도 뺐다 — 그리고 **D-7B-2 의 `AccountId`**(`AuthenticationResult.Authenticated` 생성자 인자 — 이 게이트는 data class 생성자 인자도 주입으로 센다) |
| 〃 | `app.surface.api.handlers` · `app.assembly.tier1-bootstrap-classes` | `+2` · `+1` | 두 controller · `BidCaseWiring` |
| `config/quality/gate-tests.properties` | `gate.tests.app` | `+6` | `OpenApiSecurityContractTest`(분할) · D-7B-2 의 `AuthenticatedAccountTest` · `AccountCarrierStructureTest` · 발견 결함의 `BidCaseRequestsTest` · 수정 라운드 1 의 `OpenApiRequestBodyContractTest`(결정 ④) · `BidCaseEndpointTest` |
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
- **`capability-map.md` §10 집계**(95 → 96 · `V2 필수` 61 → 62)와 **활성 OPEN 수** — `998b59dd` 위 rebase 에서
  g1set01(+1, 45 → 46)과 충돌했고 **합집합 45**(7B −1)로 풀었다(두 레인의 주석 줄 유지). §10
  블록에도 「머지 직전 재계산」 표시를 문면에 넣어 두었다. g1dec05 · g1set01 이 같은 파일의
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

### 3.5 수정 라운드 1 — 계약 D-7B-3 (검토 r1 처분, 2026-10-24)

판정 SHA `1d90c834` 에 대한 verifier r1 **not-ready** 와 code-reviewer r1 **request_changes** 를 받은 라운드다(재작업 1/5).
지적 ID 는 `V-`(verifier) · `C-`(code-reviewer) 접두다. **처분**은 고침 · 등재(근거) · 근거 있는 거부 셋 중 하나다.
**구판 RED** 칸은 그 지적의 회귀 test 가 **고치기 전 거동**에서 붉은지다 — `1d90c834` 와 이번 판은 API(`OpenedCase` ·
`BidCase.deadlineAt` · 포트 서명)가 달라 새 test 를 구판 트리에 그대로 얹을 수 없으므로, **지금 트리에서 그 거동만 구판으로
되돌리는 변이**로 쟀다(변이 이름이 `-old` 로 끝난다, 결과는 아래 변이 표).

| 지적 | 내용(한 줄) | 처분 | 출하 | 재는 자리 · 구판 RED |
|---|---|---|---|---|
| C-B1 | 기초금액 스냅샷이 production 에서 늘 NULL — 출처 어휘를 클래스 이름 표기로 맞댐 | 고침 | 읽기 · 쓰기 모두 정본 `ProvenanceCodec` · `FloorRateOriginCodec` 재사용, `bid_draft` 에 `snapshot_base_amount_provenance_detail` 열 · 출처 어휘 CHECK 둘 · detail 짝 CHECK 둘 | `BidCaseSnapshotTest` 넷(기초금액이 **있는** 공고로 여덟 열 단언 · 두 출처 갈래 · 어휘 등식 둘) · **B1-read · B1-write RED** |
| C-L2 · C-L3 | 하한율 출처 `STATUTORY_TABLE` 을 버림 · 출처를 건의 차수로 재구성 | 고침(B-1 과 같은 고침) | 코덱이 저장된 detail 로 푼다 | `BidCaseSnapshotTest`「예산 키 · 법정표」 |
| V-H1 | Q-11 소급 확인이 출하 경로에서 도달 불가 | 고침(결정 ①) | `DeadlineSettlement` — 읽기 · 명령 · 목록 · 타임라인 · 초안이 먼저 지난다. `bid_case.deadline_at`(담을 때의 공고 마감 사본) | `BidCaseScheduleTest` 넷(소급 기록 + 타임라인 순서 · 파기된 공고 · 마감 전 무전이 · 읽기와 상태 탭) · **D1-lazy RED** |
| C-H1 · V-L5 · C-M8 | 전이가 통보 예정일을 NULL 로 덮음 · 쓰는 경로 없음 · 이중체는 보존 | 고침(결정 ②) + 등재 | `UPDATE_CASE_STATE` 에서 열 제외 · V21 열 GRANT 에서 제외 · 이중체와 실 어댑터가 같은 거동(보존) | `BidCaseScheduleTest`「통보 예정일 값은 전이가 지우지 않는다」 · **D2-old RED** · 쓰는 경로 부재는 §6 알려진 제한 + OPEN 후보 |
| V-M2 · C-M4 | `snapshotTakenAt` 이 필수인데 버려짐 | 고침(결정 ③) | DTO · `DraftInput` · OpenAPI · 생성 타입에서 제거 | `BidCaseEndpointTest`「snapshotTakenAt 을 받지 않는다」 · **D3-old RED** |
| V-H3 · C-H2 | `resumeBidCase` 문서는 본문 없음 · 구현은 `{}` 요구 | 고침(결정 ④) | 구현이 본문을 받지 않는다. **새 계약 test**: 문서 `requestBody` 유무 ↔ 매핑 `@RequestBody` 유무 집합 등식(전 operation). 본문 거부 전수는 **본문을 받는 매핑**으로 모집단을 좁혔다(공유 test 변경 — 아래) | `OpenApiRequestBodyContractTest` · `BidCaseEndpointTest`「본문 없이 부른다」 · **D4-old RED** |
| V-H2 · C-H3 | 주석이 가리키는 test 둘이 없음 · 열 GRANT 미잠김 · 앱 역할 양성 경로 없음 | 고침 | `BidCaseColumnPrivilegeTest`(열 UPDATE 집합 등식 + 출하 저장소를 앱 역할로 끝까지) · `BidCaseRowTest`(꼬리표 전수 + payload 넷 거부) | **V-M7 RED** · **ROW-old RED** |
| C-M9 | 권한 거부 단언에 앱 역할 양성 대조 없음 | 고침 | `assertAllowedAsApp` — 같은 연결 방식으로 허락된 갱신 · 조회가 성공 | `BidCaseOwnershipTest`「앱 역할은 … 고치거나 지울 수 없다」 |
| V §4(팀장 표적) · V-M1 | `AccountId` 한 줄이 어느 class 에나 주입 길을 엶(M4 초록) · 키 조립으로 문자열 술어 회피(M2 초록) | 고침(게이트 술어 추가 — 표적 재검증 대상) | `AccountCarrierStructureTest` 에 등식 셋: `getAttribute` 호출자 = 넷 · app main 의 `AccountId.<init>` 호출자 = ∅ · `AccountId` 생성자 인자 class = `AuthenticationResult$Authenticated` 하나. 양성 대조 1 | **V-M2 · V-M4 RED** |
| V-L4 | 조건부 UPDATE 의 계정 필터 미측정(M3 초록) | 고침 | `BiddingSqlOwnershipTest` — `BiddingSql` 문자열 상수 **전수**(반사)에서 사용자 소유 표를 만지는 문장은 `account_id = ?`(삽입은 열 목록에 `account_id`) | **V-M3 RED** |
| C-M1 · V-L3(앞 절반) | 자식 행 계정 ↔ 부모 소유자 일치가 구조에 없음 | 고침 | `bid_case UNIQUE (id, account_id)` + 자식 셋 복합 FK | `BidCaseIntegrityTest`「복합 FK」 · **FK-old RED** |
| C-M2 | 타임라인 상태 · 명령 어휘가 CHECK 로 닫혀 있지 않음 | 고침 | `from_state` · `to_state` · `command` 열거 CHECK | `BidCaseIntegrityTest`「보류는 들어오지 않는다」 · **VOC-old RED** |
| C-M3 | 스냅샷 출처 열 셋에 어휘 · 짝 CHECK 없음 | 고침(B-1 과 같은 커밋) | 위 C-B1 | `BidCaseSnapshotTest` 어휘 등식 둘 |
| C-M5 | 건너뜀 · 정정 사유에 길이 상한 없음(지울 수 없는 표 · 개인정보) | 고침 | 도메인 `MAX_LENGTH` 500 · 요청 판독 400 · OpenAPI `maxLength` · DB `char_length` CHECK | `BidCaseEndpointTest`「사유 상한 400」 · `BidCaseIntegrityTest`「사유 상한」 · **M5-old RED**(도메인 상한이 없으면 DB CHECK 가 500 을 낸다) |
| C-L4 | `draw_numbers` 원소 부호를 DB 가 보지 않음 | 고침 | `CHECK (0 < ALL (draw_numbers))` | `BidCaseIntegrityTest`「추첨번호는 양수뿐」 |
| C-M6 · V-L3(뒤 절반) | `draft_id` 가 「기본값 초안」이 아니라 「최신 초안」 | **근거 있는 거부**(팀장 수용 2026-10-24) — 코드 무변경, 주석 정정 | 계약 문면(ux §6.1 ⑤): 확인 화면은 「**초안이 채워진 상태**: 금액(**기본 = 고른 안, 다르게 냈으면 수정**)」이고 acceptance 는 「『제출함』 확인은 **초안 금액을 기본값으로** 하되 다르게 냈으면 수정할 수 있고」다. 확인 화면이 기본값으로 채우는 초안이 **그 시점의 최신 초안**이므로 둘은 같은 것이다. 거짓은 코드가 아니라 주석의 「이 초안대로 냈다」로 읽히는 문면이었다 — V21 · `BiddingSql` · `BidEffectWriter` 주석을 「기본값으로 채운 초안 = 최신 초안, 금액은 기록이 따로 싣는다」로 고쳤다. **그 등식 문면은 r2 에서 다시 고쳤다** — 화면이 뜬 뒤 초안이 새로 쌓이면 둘이 갈린다(TOCTOU, §3.6) | — |
| C-M7 | HTTP 이중체가 생성 전이를 남기지 않음 | 고침 | 이중체가 생성 전이 한 줄을 남긴다 | `BidCaseEndpointTest`「담은 직후 타임라인」 · **M7d-old RED** |
| C-M10 · V-L8 | 「속성 test 포함」 미이행 · 196 쌍 | 고침 | `BidTransitionPropertyTest`(`checkAll` — 받아들임 ⊆ 표의 행 · 주체 · 도착, 소급 ⇔ 마감 지남의 기록, 건너뜀에서 전부 거부, 결정성) · 주석 182 | **PROP RED** |
| C-M11 | 이미 담긴 건도 201 | 고침 | `OpenedCase(view, created)` · 새로 담으면 201, 이미 있으면 200 · OpenAPI 200 추가 · 생성 타입 재생성 | `BidCaseEndpointTest`「201 · 200 같은 건」 · **M11-old RED** |
| V-L1 | public `connectionSourceOf` 신설(in_scope 밖) | 고침 | 지웠다 — 같은 모듈의 `internal DataSourceConnectionSource` 를 직접 쓴다. `ConnectionSource.kt` 는 base 와 **무변경** | `git diff 998b59dd -- …/ConnectionSource.kt` 빈 출력 |
| V-L2 | `UNIQUE` 와 중복인 `CREATE INDEX` 셋 | 고침 | 셋 제거 | `CleanMigrationIndexTest` |
| V-L6 | ConstantTime 근거의 실재하지 않는 「provenance 짝 판정」 | 고침 | 근거 문장 정정 | — |
| V-L7 | evidence §3 의 낡는 좌표 | 고침 | 정책 파일 키 옆의 줄 번호 셋을 지웠다(Phase 1 표의 같은 셋도) — 키 이름만 남긴다 | `grep -nE "\(:[0-9]+\)"` 이 이 파일에서 0 |
| C-L1 | `requestValue("noticeRound")` 가 공고번호 판독까지 감쌈 | 고침 | 감싸는 범위를 `NoticeRound.of` 하나로 | — |
| C-L5 | 표면 전수의 본문 probe 가 입찰 건 본문 판독에 닿지 않음(경로 판독이 먼저 400) | **등재(근거)** — 근거 문장은 r2 C2-N20 으로 정정 | 그 자리표시는 test 전용 상수(`ProductionHttpSurfaceTest` 의 `PATH_VARIABLE_PROBE`, 숫자가 아닌 문자열)이고 **출하 거동은 바뀌지 않는다** — 바꾸면 바뀌는 것은 그 게이트가 무엇을 재는지다. 경로 변수가 있는 입찰 건 매핑만 본문 판독 전에 400 이고, 경로 변수가 없는 `POST /api/bid-cases` 는 본문 판독까지 닿는다. 공유 게이트의 측정 범위를 바꾸는 일이라 이 slice 의 처분이 아니고, 입찰 건 본문 판독은 `BidCaseRequestsTest` · `BidCaseEndpointTest` 가 직접 잰다 | — |
| C-L6 | `AccountId` 에 `equals` 가 없음 | **등재(근거)** | `workflow/access` 는 7D 의 파일이고 in_scope 밖이다. 이 slice 의 비교는 전부 `.value` 이고(이중체 둘을 고친 것이 그 축), 후속 slice 의 위험으로 남긴다 | — |
| C-L7 | runbook 에 `account` 행을 더한 것은 선언된 절 밖 | **등재** | 7D 의 누락을 메운 한 줄이다(보존 분류 「제외」 표). 되돌리지 않고 여기 적는다 | — |

**공유 test 변경(이 라운드)**: `ProductionHttpSurfaceTest` 의 본문 거부 전수 모집단을 「POST 매핑 전부」에서 「`@RequestBody` 를 가진 POST
매핑」으로 좁혔다. 판정은 핸들러 메서드의 **서명**이다(경로 목록을 적지 않는다). 커버리지 단언(어느 축으로도 안 닿는 매핑 0 · 두 축의
합집합 = POST 전수)은 **그대로 POST 전수**로 남는다. 좁힌 쪽의 짝 — 「문서가 본문을 적은 operation = 본문을 받는 매핑」 — 은
`OpenApiRequestBodyContractTest` 가 진다. 결정 ④ 가 정한 변경이고, 게이트 술어 변경이므로 표적 재검증 대상이다.

**수정 라운드가 만든 새 파일 ↔ in_scope**:

| 새 파일 | in_scope 근거 |
|---|---|
| `workflow/.../bidding/DeadlineSettlement.kt` | 「`workflow/**` 의 bidding application」 |
| `bidding/src/test/.../BidTransitionPropertyTest.kt` | 「`bidding/**`」 |
| `adapters/src/test/.../bidding/` 의 `BidCaseDbFixture` · `BidCaseSnapshotTest` · `BidCaseScheduleTest` · `BidCaseColumnPrivilegeTest` · `BidCaseRowTest` · `BiddingSqlOwnershipTest` · `BidCaseIntegrityTest` | 「`adapters/**` 의 bidding 영속」 |
| `app/src/test/.../http/BidCaseEndpointTest.kt` | 「`app/**` 의 bidding controller」 |
| `app/src/test/.../http/OpenApiRequestBodyContractTest.kt` · `SessionTestClient.kt` | D-7B-3 in_scope 명시 「app http test 의 공유 파일(D-7B-2 · 계약 test 분할)」 — 앞은 결정 ④ 가 정한 계약 test, 뒤는 세션 HTTP test 두 class 가 쓰는 한 벌 |

#### 변이 표 — 수정 라운드 1 (`--rerun`, 표적 test 만, `git restore` 뒤 작업 트리 깨끗 확인)

| # | 변이 | 표적 | 결과 |
|---|---|---|---|
| V-M2 | dispatch 가 **실행 시 조립한 키**로 `getAttribute` 해 라벨을 파싱(verifier 변이 그대로) | `AccountCarrierStructureTest` | **RED** 2 — 속성 읽는 자리 등식 · `AccountId` 만드는 자리 ∅ |
| V-M4 | 조회 controller 생성자에 `AccountId` + 배선이 `AccountId(1L)` 빈(verifier 변이 그대로) | `AccountCarrierStructureTest` | **RED** 2 — 생성자 인자 등식 · `AccountId` 만드는 자리 ∅ |
| V-M7 | V21 열 GRANT `updated_at` → `created_at`(verifier 변이 그대로) | `BidCaseColumnPrivilegeTest` | **RED** 2 — 열 집합 등식 · 앱 역할 끝까지 경로(`permission denied`) |
| V-M3 | 조건부 UPDATE 의 `account_id = ?` → `?::bigint IS NOT NULL`(verifier 변이 그대로) | `BiddingSqlOwnershipTest` | **RED** 1 |
| D1-lazy2 | 마감 판정 조건을 늘 거짓으로(결정 ① 의 지연 판정을 끈다) | `BidCaseScheduleTest` | **RED** 4 — 남은 둘은 「마감 전 무전이」·「통보 예정일」(판정과 무관한 축, 기대대로 초록). **측정 트리: 정정 공고 test 둘을 더하기 전**(r2 C2-N21) — 현행 트리 재측정은 §3.6 변이 표 |
| D1-control | 통제군 — 변이 없음 | `BidCaseScheduleTest` | 초록 6 / 6(정정 공고 test 둘을 더한 뒤 다시 재서 8 / 8) |
| COPY-first | 마감을 **사본 우선**으로(정정 공고의 지금 값을 무시) | `BidCaseScheduleTest` | **RED** 2 — 정정이 마감을 앞당긴 건 · 미룬 건(팀장 확인 2026-10-24 로 더한 test 둘) |
| B1-read | 구판 거동 — 읽기가 출처를 클래스 이름 표기로 맞댄다 | `BidCaseSnapshotTest` | **RED** 2(기초금액 단언 둘) |
| B1-write | 구판 거동 — 쓰기가 출처를 클래스 이름 표기로 쓴다 | `BidCaseSnapshotTest` | **RED** 2(새 어휘 CHECK 가 쓰기를 거부) |
| D2-old | 구판 거동 — 전이가 통보 예정일을 NULL 로 덮는다 | `BidCaseScheduleTest` | **RED** 1 |
| D3-old | 구판 거동 — 초안 요청이 `snapshotTakenAt` 을 받는다 | `BidCaseEndpointTest` | **RED** 1 |
| D4-old | 구판 거동 — `resume` 이 본문을 요구한다 | `OpenApiRequestBodyContractTest` | **RED** 1 |
| M5-old | 구판 거동 — 건너뜀 사유에 도메인 상한이 없다(DB CHECK 만) | `BidCaseEndpointTest` | **RED** 1(400 이 아니라 500) |
| M7d-old | 구판 거동 — 이중체가 생성 전이를 남기지 않는다 | `BidCaseEndpointTest` | **RED** 1 |
| M11-old | 구판 거동 — 담기가 늘 201 | `BidCaseEndpointTest` | **RED** 1 |
| FK-old | 구판 구조 — 타임라인의 건 FK 가 단일 열 | `BidCaseIntegrityTest` | **RED** 1 |
| VOC-old | 구판 구조 — 타임라인 `to_state` 가 열거 없이 열림 | `BidCaseIntegrityTest` | **RED** 1 |
| ROW-old | 복원표에서 `WON` 한 줄을 뺀다 | `BidCaseRowTest` | **RED** 1 |
| PROP | 소급 판정에서 출발 상태 조건을 뺀다 | `BidTransitionPropertyTest` | **RED** 1 |
| ⑭(재측정) | 계정 접근자가 주체 라벨로 되돌아간다 | `AccountCarrierStructureTest` | **RED** 4(앞 판의 둘 + 새 등식 둘) |

첫 시도의 `D1-lazy`(판정 결과를 `Rejected` 로 바꿔치우기)는 **컴파일 실패**였다 — 타입이 좁아져 뒤의 `!is Accepted` 검사가
성립하지 않았다. 측정이 아니므로 결과로 세지 않고, 컴파일되는 변형(`D1-lazy2`)과 통제군으로 다시 쟀다.

### 3.6 수정 라운드 2 — 계약 D-7B-4 (검토 r2 처분, 2026-10-24)

판정 SHA `10cf5d02` 에 대한 verifier r2 **ready-for-review**(차단 0 · 게이트 medium 2 · 장부층 6)와 code-reviewer r2 **request_changes**
(high 1 · medium 6 · low 14)를 합친 라운드다(재작업 2/5). 지적 ID 는 `V2-`(verifier) · `C2-`(code-reviewer) 접두다. **구판 RED** 는 §3.5 와
같은 방식이다 — `10cf5d02` 와 이번 판은 포트 서명(`NoticeDeadline` · `settleCandidates`)과 전이표(새 명령)가 달라 새 test 를 구판 트리에 그대로
얹을 수 없으므로, **지금 트리에서 그 거동만 구판으로 되돌리는 변이**로 쟀다(이름이 `-old` 거나 「구판 거동」이라 적힌 행, 결과는 아래 변이 표).

| 지적 | 내용(한 줄) | 처분 | 출하 | 재는 자리 · 구판 RED |
|---|---|---|---|---|
| C2-N1 · V2-W2 | 마감 판정이 비가역 — 판정 뒤 마감을 미룬 정정 공고가 건을 입찰 생애에서 영구히 뺀다 | 고침(결정 ⑤ · 보충) | 전이표에 `DEADLINE_PASSED_UNRECORDED --REVOKE_DEADLINE_PASSED(SYSTEM_ONLY)--> 마감 전 넷`. `DeadlineSettlement` 가 두 방향을 본다 — 마지막 타임라인 행이 시스템 `MARK_DEADLINE_PASSED` 이고 유효 마감이 미래이거나(행이 있는데) 미정이면 그 행의 `from_state` 로(`DEFERRED` 는 `WATCHING`). V21 명령 CHECK 에 새 명령 | `BidCaseDeadlineRevocationTest` 여덟(CR 재현 그대로 · `DEFERRED` → `WATCHING` · 소급 기록으로 떠난 건 · 사용자가 적은 판정 · 목록 경로 · NULL 셋) · `BidCaseEndpointTest`「GET 이 판정을 거둔다」 · 전이표 골든 · **D5-off RED**(구판 거동) · D5-target · D5-actor |
| C2-N7 | 「공고 지금 값 우선, 없으면 사본」이 「행 없음」과 「값 없음」을 같은 값으로 접는다 | 고침(결정 ⑥) | 포트가 `Map<NoticeKey, NoticeDeadline>`(행 있음 = 키 있음, 값은 nullable)를 돌려준다. 유효 마감 = 행이 있으면 그 값, 행이 없을 때만 사본. 조회 SQL 의 `deadline_at IS NOT NULL` 을 걷었다 | `BidCaseDeadlineRevocationTest`「마감을 지운 정정이면 … 판정하지 않는다」·「판정 뒤 마감을 지운 정정이 오면 되돌린다」 · **D6-sql-old RED** · D6-null |
| C2-N2 | GET 셋이 상태를 바꾸는데 KDoc 은 부정한다 | 고침(결정 ⑦ — 유지 + 사실 선언) | `BidCaseQueryController` KDoc 을 사실로(읽기가 시스템 판정을 쓸 수 있다 · 값은 요청자가 고르지 못한다 · CSRF 면제 메서드다). §6-9 에 위협 모델 경계 문장 | `BidCaseEndpointTest`「GET 으로 읽으면 시스템 판정이 타임라인에 남는다」 |
| C2-N3 | 목록이 N 건 쓰기 + 전량 적재 · 상태 탭이 SQL 필터를 잃음 · 상한 없음 | 고침(결정 ⑧) + 등재 | 판정 후보 질의 하나(`settleCandidates` — 마감 전 넷 중 유효 마감이 지난 건 + 되돌림 후보)만 판정하고, 목록은 `state` 를 SQL 에서 거른다. 상한 · 커서는 §6-10 + `OPEN-7B-BIDBOX-PAGING` 후보 | `BidCaseDeadlineRevocationTest`「목록의 판정 후보 경로」 · `BidCaseScheduleTest`「상태 탭이 그 판정을 따른다」 · D8-cand · D8-filter |
| C2-N4 | `resumeBidCase` 가 400 을 선언하지 않는다 | 고침 | OpenAPI 400 · 생성 타입 재생성. **새 등식**: 경로 변수를 읽는 매핑 전수 ↔ 문서 400 선언 | `OpenApiRequestBodyContractTest`「경로 변수를 읽는 매핑은 전부 문서에 400 을 적는다」 · C2N4-400 · S-20 W-5 |
| C2-N5 | 상태 · 명령 어휘 CHECK 가 도메인 열거와 등식으로 묶여 있지 않다 | 고침 | `checkVocabulary` 로 `bid_case` 상태 · 타임라인 `from_state` · `to_state` · `command` CHECK 를 `BidStateTag.entries` · `BidCommandTag.entries` + 생성 명령과 **집합 등식** | `BidCaseIntegrityTest`「상태 · 명령 어휘 CHECK 는 도메인 열거 전수와 같다」 · C2N5-state(상위집합) · C2N5-cmd(하위집합) |
| C2-N6 | HTTP 층의 마감 판정 coverage 0 · 이중체가 「마감 지남」을 표현 못 함 | 고침 | HTTP 이중체의 마감 포트를 test 가 옮기는 값(`MovableNoticeDeadlines`, 기존 `InMemoryBidCaseStore.kt` 안) · 조립이 그 빈을 쓴다 | `BidCaseEndpointTest` 셋(마감 뒤 `skip`/`draft` 409 · GET 판정 전이 · GET 되돌림) · CMD-nosettle · D5-off-http |
| V2-N1 (게이트) | 계정 운반 등식이 소유자 이름 열거에 걸려 래퍼 `getAttribute` + `convertValue` 로 우회된다 | 고침(게이트 술어 변경 — 표적 재검증 대상) | `getAttribute` 술어를 **소유자 무관 서명**(`getAttribute:(Ljava/lang/String;)Ljava/lang/Object;`) 호출자 집합 등식으로. app main 의 `AccountId` **Class 상수(ldc)** 보유 class = ∅ 등식을 더했다(양성 대조 하나) | `AccountCarrierStructureTest` · V2N1-E(verifier 우회 원형) · E1(래퍼만) · E2(직렬화기만) 각각 RED |
| V2-N2 (게이트) | 본문 등식과 좁힌 거부 모집단이 `@RequestBody` 주석에 걸려 있다 | 고침(게이트 술어 변경 — 표적 재검증 대상) | 문서에서 `requestBody` 없는 operation 을 **도출**하고(지금 `DELETE /api/session` · `POST …/resume`), 새로 로그인한 세션으로 전부 **본문 없이** 불러 400 · 415 가 아님을 집합으로 잰다(경로 변수는 `1`). 주석 유무에 걸지 않는다 | `OpenApiRequestBodyContractTest`「문서가 본문을 적지 않은 operation 은 …」 · V2N2-N3B(주석 없이 본문을 요구하는 핸들러) RED |
| C2-M1 잔여 · V2-W4 · C2-N18(뒤 절반) | `bid_record.draft_id` 가 같은 건 · 같은 계정의 초안이라는 제약이 구조에 없다 · 자식 둘 복합 FK 가 행동으로 안 재짐 | 고침 | `bid_draft UNIQUE (id, case_id, account_id)` + `bid_record (draft_id, case_id, account_id)` 복합 FK(NULL 은 MATCH SIMPLE 로 통과) | `BidCaseIntegrityTest`「기록의 초안은 같은 건 · 같은 계정의 것 — 없으면 통과」 · 자식 둘 복합 FK 거부 · `CleanMigration*Test` ratchet · **FK-draft-old RED** |
| C-M6(문면) | 「최신 초안 = 화면의 기본값」 등식은 TOCTOU 로 깨진다 | 고침(문면) | 세 자리(`LATEST_DRAFT_ID` · `latestDraftId` · V21 주석)를 「기록 시점의 최신 초안이다 — 화면의 기본값과 같다는 보장은 없다」로. 요청에 초안 id 를 싣는 안은 채택하지 않았다(D-7B-4) | — |
| V2-W1 · C2-N9 | `open` 의 `Opened` 갈래가 판정을 지나지 않는다 | 고침 | 두 갈래 모두 판정을 지난다 · 담을 때 쓰는 사본도 같은 조회 결과 | `BidCaseDeadlineRevocationTest`「이미 마감이 지난 공고를 담으면 담기 응답부터 마감 지남」 · **W1-old RED** |
| V2-W3 | 지연 판정의 동시성 test 가 커밋에 없다 | 고침 | `BidCaseConcurrencyTest`(8 스레드 × 12 판, 읽기 셋 섞음 — 시스템 판정은 건마다 한 줄 · 오류 0 · 상태 하나) | **CONC RED**(버전 조건을 빼면 타임라인 UNIQUE 위반 = 500) |
| V2-W5 | 스냅샷 test 가 공고 provenance 를 손 리터럴로 심는다 | 고침 | `BidCaseDbFixture` 가 정본 `ProvenanceCodec` · `FloorRateOriginCodec` 으로 심는다 | `BidCaseSnapshotTest` |
| C2-N8 | `settle` 이 쓰기 결과를 버린다 | 고침(재시도 한 번 — 레인 선택) | `VersionConflict` 면 다시 읽어 **한 번 더** 판정한다(다른 전이가 이긴 뒤의 상태로 다시 묻는다). 그 뒤에도 지면 다시 읽은 값을 쓴다 — KDoc · §6-2. 재시도를 택한 근거: 동시 읽기에서 진 쪽이 낡은 상태를 응답하지 않는다 | **N8-noretry RED**(진 쪽이 `WATCHING` 을 응답) |
| C2-N10 | 요청 하나가 트랜잭션 둘~넷이고 원자적이지 않다 | 등재(사실) | **전이 하나가 트랜잭션 하나다. 요청 하나는 판정 전이(0~1)와 요청 전이(0~1) · 읽기로 트랜잭션 둘~넷이고, 판정 전이는 요청의 성패와 무관한 사실로 남는다** — 요청이 409 여도 그 앞의 판정은 커밋돼 있다 | `BidCaseScheduleTest`「마감 뒤의 제출함은 시스템 전이 뒤 소급 기록이 된다 — 타임라인 순서까지」(판정 행과 요청 행이 따로 남는다) |
| C2-N11 | `submission(body)` 가 판독 전체를 `requestValue("record")` 로 감싼다 | 고침 | 생성자별로 좁혔다(`evidenceRef` · `memo` · `drawNumbers`) | 라벨은 로그 진단만 바뀐다 — 상태 코드(400)는 `BidCaseRequestsTest` 가 그대로 잰다 |
| C2-N12 | 상한 등식의 단위가 다르다(UTF-16 코드 단위 ↔ 문자) | 등재(문면) | §6-11 에 단위 차이와 방향(도메인이 더 엄격 — fail-closed)을 적었다. BMP 밖 경계 test 는 두지 않았다 | — |
| C2-N13 · C2-N14 | 메모(2000) · 증빙 참조(128)에 DB CHECK · OpenAPI `maxLength` 없음 · 스냅샷 통화 · 과세 `<> ''` 없음 | 고침 | `char_length` CHECK 둘 · `RecordBidRequest` `maxLength` 둘 · 스냅샷 통화 · 과세 `<> ''` | `BidCaseIntegrityTest` 경계 둘 · C2N13-memo · C2N13-evid · C2N14-cur |
| C2-N15 | `BidCaseRequests` KDoc 에 줄 번호 좌표 | 고침 | 절 제목(「§6.1 『몇 위였나』 문장」)으로 | — |
| C2-N16 | 속성 test 에 공허성 대조가 없다 | 고침 | 반대 방향(표의 행 · 주체가 맞으면 반드시 받아들여진다)과 `Accepted` 발생 수 > 0 | **N16-vacuous RED**(커널이 늘 거부) |
| C2-N17 | 소유 문장 술어가 문자열이다 | 고침(KDoc) + 변이 | test KDoc 에 「이 술어는 문자열이다 — 정본은 행동 축」. 변이 둘을 표에 뒀다 — 조건부 UPDATE 의 계정 조건을 `OR TRUE` 로 열면 **초록**(같은 트랜잭션의 소유 SELECT 가 먼저 막으므로 행동이 같다 — 문자열 술어도 지나간다), 그 첫 겹(건 적재 SELECT)을 열면 행동 test 가 **RED** 이고 그때 UPDATE 의 계정 조건이 쓰기를 막는다(`CaseNotFound` 대신 `VersionConflict`) | OR-TRUE(초록 — 등가 변이) · OR-TRUE-load RED |
| C2-N18(앞 절반) | `attempt` 주석이 「실패는 롤백한다」 | 고침 | autocommit 연결이라 실패 문장은 그냥 실패한다로 | — |
| C2-N19 | NULL 사본은 영구히 판정되지 않는다 | 등재 | §6-2 에 적었다(담을 때 공고 행이 없었고 뒤에 파기되면 그 건은 마감 판정에 닿지 않는다) | — |
| C2-N20 | C-L5 등재 근거가 부정확 | 고침(문면) | §3.5 C-L5 행을 사실로 고쳤다 | — |
| C2-N21 | `D1-lazy2` 행이 test 둘을 더하기 전 트리의 수 | 고침 | 현행 트리에서 다시 쟀다(아래 변이 표) · §3.5 행에 측정 트리를 적었다 | D1-lazy2 · D1-control |
| V2-W6(절차) | 검증 도중 레인 HEAD 가 움직였다 | 사실 기록 — 레인 조치 없음 | **원인은 팀장 지시 순서다**(검증 시작 뒤 test 추가 지시 → 커밋). 팀장 정정으로 판정 SHA 를 `10cf5d02` 로 고정했다. 이번 라운드는 판정 SHA 보고 뒤 커밋하지 않는다 | — |

**OPEN 후보(이 PR 에서 활성 등재하지 않는다 — 팀장이 다음 M7 문서 PR 에서 등록)**: `OPEN-7B-NOTICE-EXPECTED-WRITER`(유지, §6-7) ·
`OPEN-7B-BIDBOX-PAGING`(새, §6-10).

**수정 라운드 2 가 만든 새 파일 ↔ in_scope**:

| 새 파일 | in_scope 근거 |
|---|---|
| `adapters/src/test/.../bidding/BidCaseDeadlineRevocationTest.kt` · `BidCaseConcurrencyTest.kt` | 「`adapters/**` 의 bidding 영속」 |

그 밖은 기존 파일의 편집이다(`MovableNoticeDeadlines` 는 기존 `InMemoryBidCaseStore.kt` 안의 class). 두 test 는 `config/quality/gate-tests.properties` 에 등재했다.

#### 변이 표 — 수정 라운드 2 (현행 트리 · `--rerun` · 표적 test 만 · `git restore`/새 파일 삭제 뒤 작업 트리 깨끗 확인)

측정 트리: 「fix … detekt」 커밋(D5-actor · OR-TRUE-load · W1-old · N8-noretry · N16-vacuous 는 그 뒤 「test … 사용자가 적은 마감 지남」 커밋).
변이는 전부 **바꿔치우기**이고, 게이트 우회 셋(V2N1-*)만 우회 코드를 담은 새 파일을 더한다(우회의 정의가 「그런 파일이 생긴다」이므로).

| # | 변이 | 표적 | 결과 |
|---|---|---|---|
| D5-off | 결정 ⑤ — 되돌림 판정을 끈다(구판 거동) | `BidCaseDeadlineRevocationTest` | **RED** 4(CR 재현 · `DEFERRED` · 지운 정정 · 목록 경로) |
| D5-off-http | 같은 변이, 운반 층 | `BidCaseEndpointTest` | **RED** 1(GET 되돌림) |
| D5-target | 되돌림 도착을 늘 관심으로 | `BidCaseDeadlineRevocationTest` | **RED** 1(CR 재현 — `BID_PLANNED` 기대) |
| D5-actor | 되돌림 근거에서 시스템 주체 조건을 뺀다 | `BidCaseDeadlineRevocationTest` | 첫 측정 **초록 7/7 — 사각**. test 「사용자가 적은 마감 지남은 … 되돌리지 않는다」를 더해(커밋) 다시 재서 **RED** 1 |
| D6-null | 결정 ⑥ — 「값 없음」을 「행 없음」처럼(NULL 이면 사본) | `BidCaseDeadlineRevocationTest` | **RED** 1 |
| D6-sql-old | 구판 거동 — 마감 조회가 NULL 행을 거른다 | `BidCaseDeadlineRevocationTest` | **RED** 2 |
| COPY-first | 마감은 사본 우선 | `BidCaseScheduleTest` | **RED** 2 |
| D1-lazy2 | 결정 ① 지연 판정을 끈다 — 현행 트리 재측정(C2-N21) | `BidCaseScheduleTest` | **RED** 5 / 8 — 초록 셋은 「마감 전 무전이」·「통보 예정일」·「정정이 마감을 미루면 아직 마감 전」(판정이 서지 않아야 하는 축) |
| D1-control | 통제군 — 변이 없음 | `BidCaseScheduleTest` | 초록 8 / 8 |
| D8-cand | 결정 ⑧ — 판정 후보 질의가 아무것도 안 고른다 | `BidCaseDeadlineRevocationTest` · `BidCaseScheduleTest` | **RED** 2(목록 경로 둘) |
| D8-filter | 결정 ⑧ — 상태 탭 필터를 SQL 에 넘기지 않는다 | `BidCaseScheduleTest` | **RED** 1 |
| W1-old | 구판 거동 — 새로 담은 건은 판정을 지나지 않는다 | `BidCaseDeadlineRevocationTest` | **RED** 1 |
| N8-noretry | 경합으로 진 판정을 다시 묻지 않는다 | `BidCaseConcurrencyTest` | **RED** 1(응답 상태에 `WATCHING` 이 섞인다) |
| CMD-nosettle | 명령 경로가 판정을 건너뛴다 | `BidCaseEndpointTest` | **RED** 1(409 기대 · 200) |
| V2N1-E | verifier 우회 원형(래퍼 `getAttribute` + `convertValue`) | `AccountCarrierStructureTest` | **RED** 2(서명 호출자 등식 · Class 상수 ∅) |
| V2N1-E1 | 래퍼 `getAttribute` 만 | `AccountCarrierStructureTest` | **RED** 1(서명 호출자 등식) |
| V2N1-E2 | 직렬화기 `AccountId` 만 | `AccountCarrierStructureTest` | **RED** 1(Class 상수 ∅) |
| V2N2-N3B | 주석 없이 본문을 요구하는 핸들러(`resume` 이 `Content-Length` 0 이면 400) | `OpenApiRequestBodyContractTest` | **RED** 1(본문 없는 operation 행동 등식) |
| C2N4-400 | `resume` 의 400 선언을 지운다 | `OpenApiRequestBodyContractTest` | **RED** 1 |
| C2N5-state | 상태 CHECK 에 어휘 밖 값 하나를 더한다(상위집합) | `BidCaseIntegrityTest` | **RED** 1 |
| C2N5-cmd | 명령 CHECK 에서 `REVOKE_DEADLINE_PASSED` 를 뺀다(하위집합) | `BidCaseIntegrityTest` | **RED** 1 |
| FK-draft-old | 구판 구조 — 기록의 `draft_id` FK 가 단일 열 | `BidCaseIntegrityTest` | **RED** 1 |
| CONC | 조건부 갱신의 `case_version` 조건을 뺀다 | `BidCaseConcurrencyTest` | **RED** 1(타임라인 UNIQUE 위반) |
| OR-TRUE | 조건부 UPDATE 의 계정 조건을 `OR TRUE` 로 | `BiddingSqlOwnershipTest` · `BidCaseOwnershipTest` | 초록 12 / 12 — **등가 변이**(위 C2-N17 행) |
| OR-TRUE-load | 건 적재 SELECT 의 계정 조건을 `OR TRUE` 로(첫 겹) | 같음 | **RED** 2(남의 건 읽힘 · 남의 건 쓰기는 UPDATE 의 계정 조건이 `VersionConflict` 로 막음) |
| C2N13-memo · C2N13-evid | 메모 · 증빙 상한 CHECK 를 뺀다 | `BidCaseIntegrityTest` | 각각 **RED** 1 |
| C2N14-cur | 스냅샷 통화 `<> ''` CHECK 를 뺀다 | `BidCaseIntegrityTest` | **RED** 1 |
| N16-vacuous | 전이 커널이 늘 거부 | `BidTransitionPropertyTest` | **RED** 1 |

### 3.7 승인 전 일괄 — 계약 D-7B-5 (검토 r3 처분, 2026-10-24)

판정 SHA `1b33c925` 에 대한 verifier r3 **ready-for-review**(차단 0 · 게이트 medium 2 · 장부층 2)와 code-reviewer r3 **approve**(high 0 · medium 3 ·
low 8 · 질문 1)의 남은 지적을 한 번에 처리했다(재작업 횟수 2/5 유지). ID 접두는 `V3-` · `C3-`. 게이트 술어를 바꾼 항목은 V3-N1 · V3-N2 둘이다
(표적 재검증 대상). **구판 RED** 칸: 게이트 둘은 verifier 가 `1b33c925` 에서 **초록**으로 잰 우회(F1 · N3C)를 지금 트리에 다시 얹어 RED 인지로 쟀다.

| 지적 | 내용(한 줄) | 처분 | 출하 | 재는 자리 · 변이 |
|---|---|---|---|---|
| V3-N1 (게이트) | 손에 든 `AccountId` 의 `getClass()` 로 `convertValue` 하면 Class 상수 · `<init>` · 반사 없이 다른 id 를 짓는다 | 고침(술어 추가) + OPEN 후보 | `AccountCarrierStructureTest` — 대상 타입(`Class` · `JavaType` · `TypeReference`)을 받는 데이터 바인딩 진입점(`convertValue` · `readValue` · `treeToValue` · `readerFor` 등)을 부르는 app main class = ∅(관측값 — app main 은 `JsonNode` 접근자만 쓴다), **소유자 무관 서명** 술어. 양성 대조(F1 모양 · `TypeReference` 판독기) · 음성 대조(`JsonNode.get`). KDoc 「다른 길은 모두 Class 상수를 요구한다」를 사실로 좁혔다(V3-L2). 생성 경로 봉쇄는 7D 파일 — `OPEN-7D-ACCOUNTID-CONSTRUCTION`(§6-12) | **V3N1-F1 RED**(`1b33c925` 에서는 초록 — verifier 실측) |
| V3-N2 (게이트) | 본문 없는 op 행동 등식이 「없는 건 → 404」에서 멈춘다 | 고침(술어 보강) | 경로 변수는 **그 세션이 담은 건**의 id 로 채우고, 응답이 401 · 404 가 아님(핸들러 본체에 닿음)을 양성 대조로 단언한 뒤 400 · 415 아님을 잰다 | **V3N2-N3C RED**(`1b33c925` 에서는 초록 — verifier 실측) |
| C3-M2 | 메모 · 증빙 상한이 네 자리 손 복사 · 「어긋나면 붉다」 주석이 거짓 | 고침 | `BidMemo` · `EvidenceRef` 상한을 공개 companion 상수로(Kotlin `internal` 은 모듈 경계라 adapters · app test 가 못 본다 — `SkipReason` 과 같은 모양). DB 쪽: `pg_get_constraintdef` 에서 읽은 `char_length` 상한 전수 ↔ 도메인 상수 넷 등식. 문서 쪽: 입찰 건 요청 셋의 `maxLength` 전수 ↔ 도메인 상수 등식. 옮겨 적은 test 상수 둘을 지웠다 | `BidCaseIntegrityTest`「자유 텍스트 상한 CHECK 는 …」 · `BidCaseRequestsTest`「… 문자열 상한은 …」 · C3M2-db · C3M2-doc RED |
| C3-M3 | `restoreOf` 의 `else -> Watching` 이 새 마감 전 상태를 조용히 접는다 | 고침 | 되돌림 도착표(마감 전 넷 키, `DEFERRED → WATCHING` 은 결정 ⑤ 보충의 의도 항목으로 KDoc 표기) · 표 밖은 `error`. 등식: 키 집합 == `DEADLINE_JUDGED_STATES` · 도착 ⊆ 전이표의 되돌림 도착 집합 | `BidCaseWorkflowTest`「되돌림 도착표의 키는 …」 · C3M3-key · C3M3-deferred RED |
| C3-L6 | 사용자 「마감 지남」은 되돌릴 수 없다 | 고침 — **좁힘**(추천 안) | 근거 문장(ux §6.1 acceptance): 「확인 없이 마감이 지나면 『마감 지남(미확인)』이 되고 소급 확인이 가능하다」 — 사용자가 그 상태를 적는 길을 요구하지 않는다. 그래서 `MARK_DEADLINE_PASSED` 를 `SYSTEM_ONLY` 로. 전이표 골든(시스템 전용 셋) · 사용자 MARK 를 쓰던 test 하나(`BidCaseOwnershipTest`「소급 확인」)는 시스템 주체로 바꿨다 | `BidTransitionTableTest` · `BidCaseDeadlineRevocationTest`「사용자는 마감 지남을 적을 수 없다」 · C3L6-user-table · C3L6-user-db RED. 결과: r2 의 D5-actor 는 이제 **등가 변이**(사용자 MARK 행을 쓰는 길이 없다 — 아래 표) |
| C3-L3 | 판정 후보 질의의 계정 조건이 행동으로 안 재짐 | 고침 | test 「남의 건은 판정 후보로도 고르지 않는다」(저장소 직접 · 양성 대조 · 내 목록이 남의 건을 판정하지 않음) | C3L3-ORTRUE RED |
| C3-L4 | `rowPresent` 호출자 0 — 운반 층 「행 없음」 축 미측정 | 고침(test 를 둠) | `BidCaseEndpointTest`「공고 행이 사라지면 담을 때 적은 사본으로 판정한다」(판정 → 거둠 → 행 없음 → 사본으로 다시 판정) | C3L4-copy RED |
| C3-L7 | `SubmittedBidAmount(...)` 가 `requestValue` 밖 | 고침 | `requestValue("amountWon")` 로 감쌌다 | — (판독기의 `minimum` 이 먼저 막아 오늘 거동은 같다 — 변이로 가를 길이 없다) |
| C3-L8 | 되돌림 판단이 타임라인 전문을 적재 | 고침 | 포트 `lastTransition`(기본 구현 = 전 타임라인의 끝, test 이중체용) · 출하 저장소는 `ORDER BY case_version DESC LIMIT 1` 한 줄 질의. 행 해석은 새 `BidTimelineRows` 가 두 질의에 한 벌로(detekt 함수 수 상한 때문에 타임라인 행 쓰기도 그리로 옮겼다) | 기존 되돌림 test 전부 · C3L8-first RED 4 |
| C3-L5 | `targetOf` KDoc 의 「명령 전수」가 넓다 | 고침(문면) | 「전이표에 행이 있는 명령에 한해서」 | — |
| C3-M1 | 판정 뒤 소급 기록 + 연장 정정이면 `retroactive=true` 가 거짓인 채 남는다 | 등재 | §6-2 | — |
| V3-L1 | 파기 뒤 사본이 정정으로 선 판정을 거두거나 이른 판정을 세운다 | 등재 | §6-2 | — |
| C3-L2 | 「요청 하나는 트랜잭션 둘~넷」이 목록에서 거짓 | 등재(문면 정정) | §6-2 — 명령 · 단건 조회 경로로 좁히고 목록은 후보 수 비례 | — |
| C3-Q1 | 마감이 지금 시각을 반복 교차하면 교차마다 시스템 행 둘 | 등재 | §6-10 타임라인 축 | — |
| C3-L1 | Phase 1 절의 줄 번호 좌표 | 고침 + 재측정 | 절 제목 · 인용으로. 좌표 술어를 `grep -nE ':[0-9]+'` 로 넓혀 다시 잰 결과 이 파일 · `rollback.md` 의 걸림은 **시각뿐**(`HH:MM` · ISO 시각)이다 | — |

**OPEN 후보(evidence 에만 — 팀장이 다음 M7 문서 PR 에서 등록)**: `OPEN-7B-NOTICE-EXPECTED-WRITER`(§6-7) · `OPEN-7B-BIDBOX-PAGING`(§6-10) ·
`OPEN-7D-ACCOUNTID-CONSTRUCTION`(새, §6-12).

**새 파일 ↔ in_scope**: `adapters/src/main/.../bidding/BidTimelineRows.kt` — 「`adapters/**` 의 bidding 영속」. 그 밖은 기존 파일의 편집이다.

#### 변이 표 — 승인 전 일괄 (`--rerun` · 표적 test 만 · `git restore`/새 파일 삭제 뒤 작업 트리 깨끗 확인)

측정 트리: 「fix … 승인 전 일괄 HTTP …」 커밋(마지막 산출물 커밋). 첫 행만 앞 판정 SHA 의 버릴 clone 측정이다.

| # | 변이 | 표적 | 결과 |
|---|---|---|---|
| r2-DEFERRED | 결정 ⑤ 보충 — `DEFERRED` 로 복원(`Deferred(EPOCH)` 갈래를 더함) — **판정 SHA `1b33c925` 의 버릴 clone 측정**(동결 뒤라 HEAD 를 움직이지 않았다) | `BidCaseDeadlineRevocationTest` | **RED** 1 / 8(「나중에에서 판정된 건 … 관심으로」) |
| V3N1-F1 | verifier 우회 F1(손에 든 값의 `getClass()` 로 `convertValue`) | `AccountCarrierStructureTest` | **RED** 1(바인딩 진입점 등식) |
| V3N2-N3C | verifier 우회 N3C(건 존재 확인 뒤에 본문을 요구) | `OpenApiRequestBodyContractTest` | **RED** 1(본문 없는 op 행동 등식) |
| C3M2-db | V21 메모 상한 2000 → 2001 | `BidCaseIntegrityTest` | **RED** 2(상한 등식 · 경계) |
| C3M2-doc | OpenAPI 메모 `maxLength` 2000 → 2001 | `BidCaseRequestsTest` | **RED** 1 |
| C3M3-key | 되돌림 도착표에서 `BID_PLANNED` 행을 뺀다 | `BidCaseWorkflowTest` | **RED** 1 |
| C3M3-deferred | 도착표의 `DEFERRED → WATCHING` 을 `Deferred(EPOCH)` 로 바꿔치움(위 r2 행의 현행 트리판) | `BidCaseWorkflowTest` | **RED** 1 |
| C3L3-ORTRUE | 판정 후보 질의의 계정 조건을 `OR TRUE` 로 | `BidCaseDeadlineRevocationTest` | **RED** 1 |
| C3L6-user-table | 구판 — `MARK_DEADLINE_PASSED` 가 `USER_OR_SYSTEM` | `BidTransitionTableTest` | **RED** 1 |
| C3L6-user-db | 같은 변이, 실 DB 행동 | `BidCaseDeadlineRevocationTest` | **RED** 1 |
| C3L4-copy | 행이 없을 때 사본을 쓰지 않는다 | `BidCaseEndpointTest` | **RED** 1 |
| C3L8-first | 마지막 행 질의가 첫 행을 고른다 | `BidCaseDeadlineRevocationTest` | **RED** 4 |
| D5-actor | 되돌림 근거의 시스템 주체 조건을 뺀다 — C3-L6 뒤 재측정 | `BidCaseDeadlineRevocationTest` | 초록 9 / 9 — **등가 변이**: 「마감 지남」에 들어가는 길이 시스템뿐이라 사용자 MARK 행이 생기지 않는다. 조건은 심층 방어로 남긴다 |
| REV-control | 통제군 — 변이 없음 | `BidCaseDeadlineRevocationTest` | 초록 9 / 9 |

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
| **BID-01** | `V2 필수` | **ⓐ** | 상태·전이: `BidTransitionTableTest`(골든 등식 + 전수 182 쌍) · 스냅샷 불변: `BidCaseOwnershipTest`「초안 수정은 새 행을 쌓고」 + append-only 트리거 · 다음 확인 시점: `NextCheckpointTest`(전 상태) · 소급 표시: `BidInvariantsTest` + `BidCaseOwnershipTest`「소급 확인이 기록과 소급 표시를 남긴다」 · 페이퍼/실 분리: `BidInvariantsTest` + `BidCaseOwnershipTest`「목록은 종류로 갈린다」 · 건은 하나: `BidCaseOwnershipTest` · 보관 기한: `EvidenceRetentionPolicyTest` + workflow test | 없음 — 소급 표시는 `BidCaseScheduleTest` 가 **출하 조립과 같은 구성**(JDBC 저장소 · 마감 질의 · 스냅샷 질의 · use case, `BidCaseWiring` 과 같다)으로 실 DB 위에서 재고, 그중 하나는 **출하 앱 역할 연결**이다(결정 ① 이 ⓐ 유지 조건으로 건 test). 화면 끝까지는 7E 가 붙어야 보인다 |

6E-1 의 `acceptance-trace.md` 집계는 **다시 세지 않았다**(남의 slice 의 동결 evidence).
그 파일에는 모수 이동(61 → 62)을 적은 **날짜 붙은 교차 참조 한 줄**만 넣었다.

### 6. 알려진 제한

1. **`PRICE_RECEIVED` 는 오늘 도달 불가** — 진입 명령(`RECEIVE_PRICE_MENU`)이 `SYSTEM` 전용이고
   그 호출자가 출하 경로에 없다(DEC-05 메뉴는 ML-01 · 7C② 대기). 어휘 · CHECK · 전이표 행은
   두었다(나중에 CHECK 어휘를 늘리는 둘째 마이그레이션을 피한다).
2. **자동 전이의 시스템 호출자는 마감 판정 하나뿐이다** — SET-01 대사 결과 · SET-02 개찰 · 리마인드 도달은
   호출자가 없다. 사용자 입력 경로는 열려 있다(§6.1 「사용자가 나라장터에서 보고 오거나 입력한다」). 마감 판정
   (결정 ①)은 **읽거나 명령할 때 지연 판정**이다 — 아무도 읽지 않는 건은 저장소에서 마감 전 상태로 남아 있다가
   다음 읽기에 넘어간다. 저장된 상태만 보는 소비자(예: 알림 7C③)는 이 판정을 지나지 않으면 마감 전으로 읽는다.
   판정은 **두 방향**이다(결정 ⑤): 시스템이 세운 「마감 지남」은 유효 마감이 다시 미래가 되거나(공고 행이 있는데) 미정이
   되면 다음 판정이 그 직전 상태로 거둔다. 기록으로 이미 떠난 건은 거두지 않는다. 「마감 지남」에 들어가는 간선은 **시스템 전용**이다
   (D-7B-5 C3-L6 — §6.1 은 「확인 없이 마감이 지나면 『마감 지남(미확인)』이 되고」라 적을 뿐 사용자가 그 상태를 적는 길을 요구하지 않는다).
   **미룸에서 판정된 건은 되돌릴 때 미룸을 잃는다 — 다시 미루려면 운영자가 새 리마인드로 미룬다**(결정 ⑤ 보충, 판정이
   리마인드 시각을 지웠다). 유효 마감은 **공고 행이 있으면 그 행의 값**(값이 없으면 미정 — 판정하지 않는다)이고, **행이
   없을 때만**(6B-3 파기) 담을 때 적은 사본이다(결정 ⑥). **담을 때 공고 행이 없었으면 사본이 NULL 이고, 그 공고가 뒤에
   파기되면 그 건은 마감 판정에 닿지 않는다**(C2-N19 — 사본은 담을 때 한 번 쓰고 열 GRANT 밖이다). 판정 저장이 경합으로 지면
   다시 읽어 한 번 더 판정하고, 그 뒤에도 지면 다시 읽은 값을 응답한다(C2-N8). **전이 하나가 트랜잭션 하나이고, 명령 · 단건 조회
   요청 하나는 트랜잭션 둘~넷이다 — 판정 전이는 요청의 성패와 무관한 사실로 남는다**(C2-N10: 요청이 409 여도 앞의 판정은 커밋돼 있다).
   **목록은 판정 후보 수에 비례한다**(C3-L2 — 후보 질의 · 목록 질의 · 마감 조회 둘에 후보 하나마다 판정 한두 번).
   **소급 표시는 판정 시점의 사실이다**(C3-M1): 판정 뒤 소급 기록된 건에 마감을 미룬 정정이 와도 그 기록의 `retroactive=true` 는
   그대로 남는다(기록은 「마감 지남」을 이미 떠나 되돌림 대상이 아니다) — Q-11 소급 집계는 그만큼 과대다.
   **파기 뒤 사본은 정정을 따라가지 않는다**(V3-L1): 「앞당김 정정 → 판정 → 파기」면 담을 때의 사본(옛 미래 마감)이 그 판정을
   거두고, 「연장 정정 → 파기」면 옛 사본이 이른 판정을 세운다. 6B-3 파기는 보존 기한 축이라 대개 두 마감이 모두 지난 뒤이므로
   실해는 작다.
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
7. **통보 예정일을 쓰는 경로가 없다**(계약 D-7B-3 결정 ②) — 그래서 다음 확인 시점의 `NOTICE_EXPECTED` 후보는 오늘
   도달 불가이고, 「개찰됨」 두 상태의 다음 확인 시점은 출하 경로에서 늘 「없음」이다. 전이는 그 값을 지우지 않는다(앞 판은
   지웠다). **OPEN 후보 `OPEN-7B-NOTICE-EXPECTED-WRITER`** — 그 값을 누가(사용자 입력 · SET-02 개찰 결과 · 공고 문면)
   어느 명령으로 쓰는가. **이 PR 에서는 후보로만 둔다**(팀장 결정 2026-10-24 — capability map 활성 등재는 다음 M7 문서 PR 에서
   `OPEN-7C5P-*` 넷과 함께 한 번에 한다. 활성 수 충돌을 피한다).
8. **사유 자유 텍스트는 지울 수 없는 표에 남는다** — 건너뜀 · 정정 사유는 타임라인(append-only)에 남고 타임라인 응답에
   실린다. 상한(500자)을 도메인 · 요청 · 문서 · DB 네 겹에 두었지만 **내용**(사람 이름 · 연락처가 들어올 가능성)은 막지
   않는다. 메모 · 증빙과 달리 가리지 않는 것은 타임라인이 그 사유를 보여 주는 것이 계약이기 때문이다(§6.1).
9. **읽기가 시스템 전이를 쓴다**(결정 ⑦) — GET 셋(목록 · 건 하나 · 타임라인)이 마감 판정을 지나므로 판정 전이(세움 · 거둠)를
   타임라인에 쓸 수 있고, GET 은 CSRF 면제 메서드다. **위협 모델 경계**: 판정의 값은 요청자가 고르지 못하고(서버 시계와 공고가
   정한다) 결정 ⑤ 로 되돌릴 수 있으므로, 교차 출처가 GET 을 일으켜 얻는 것은 「다음 읽기에 어차피 설 판정을 앞당김」뿐이다.
   읽기 전용 응답 + 판정 enqueue 분리는 이 slice 가 하지 않는다.
10. **목록에 상한 · 커서가 없다**(결정 ⑧) — 판정은 후보 질의 하나로 좁혔고 상태 탭은 SQL 이 거르지만, 응답은 그 계정 · 종류의 건
    전부다. 근거: 1 운영자가 손으로 담는 입찰함이라 건 수가 그 운영자가 담은 공고 수를 넘지 않고, 상한 · 커서는 OpenAPI · web 을
    함께 바꾼다. **OPEN 후보 `OPEN-7B-BIDBOX-PAGING`** — 이 PR 에서는 후보로만 둔다(팀장이 다음 M7 문서 PR 에서 등록).
    **타임라인 축**(C3-Q1): 마감이 지금 시각을 가로질러 반복 정정되면 교차마다 시스템 행 둘(판정 · 되돌림)이 쌓인다 — 상한은
    정정 횟수이고, 운영 데이터가 생기면 같은 OPEN 후보에서 본다.
12. **`AccountId` 를 짓는 길 자체는 열려 있다**(V3-N1) — 공개 생성자이고 7D 파일이다. app main 에서 짓는 길(생성자 호출 · Class
    상수 · 타입을 받는 데이터 바인딩 진입점)은 구조 등식이 ∅ 으로 잠그지만, 생성 경로 봉쇄는 **OPEN 후보
    `OPEN-7D-ACCOUNTID-CONSTRUCTION`** 으로 둔다(이 PR 에서는 후보로만).
11. **상한의 단위가 층마다 다르다**(C2-N12) — 도메인 상한은 `String.length`(UTF-16 코드 단위), DB CHECK 는 `char_length`(문자)다.
    BMP 밖 문자는 도메인에서 둘로 세어지므로 도메인이 **더 엄격**하다(fail-closed — 도메인을 지난 값은 CHECK 도 지난다).
    「도메인 상한 = CHECK 값」 경계 test 는 ASCII 로만 잰다.

### 7. acceptance 실측

`ci.yml` 의 job 명령 그대로다. 로그는 레인 scratchpad 에 있고 여기에는 결과 한 줄씩만 적는다.

**아래 표는 `56bb7474` 위 트리의 실측이다.** 동결 직전 main 이 `998b59dd`(#85 g1set01 — settlement 코드 포함)로 움직여 다시
rebase 했으므로, 같은 명령을 새 트리에서 다시 잰 결과는 §7.1 이다.

| 항목 | 명령 | 자리 | 결과 |
|---|---|---|---|
| `check` job — Kotlin | `./gradlew --no-daemon check --continue` | 「요청 값 test …」 커밋 | **exit 0** · 13m23s(23:28–23:42) · 새 test 셋 6/5/4 실행 · 실패 0(XML 확인) |
| `check` job — S-20 | `./tools/one-command-check.sh`(Kotlin `check` · `qualityBaseline` · Python 전건 · web 전건) | 「OpenAPI 생성 타입 재생성 …」 커밋 | **exit 0** · pytest 1605 passed · vitest 28 passed · W-5 「바이트까지 같다(99658 bytes)」 · Playwright 2 passed. 같은 명령이 한 커밋 앞(evidence 커밋)에서는 **W-5 에서 exit 1**(§3.4 발견 4) |
| `web` job | W-1~W-11(S-20 의 web 절이 같은 명령을 돈다) | 위와 같음 | 위와 같음 |
| `container` job | `ci.yml` 의 `container` step 전부를 job env · `GITHUB_ENV` · `GITHUB_PATH` · step `if:` 평가와 함께 순서대로 재생 | evidence 커밋(「약속↔출하 표 …」) | **JOB SUCCESS** — `run` step 23 전부 성공(S-20z · 값 생성 둘 · S-21~S-21c · S-22a~e3 · trivy 설치 · S-23 · S-23b · S-23c · S-24 · S-25 · S-26), `uses:` 넷은 해당 없음. 이미지 태그는 재생 전 ID 로 복원(등식 확인) |
| 백업·복원 리허설 | `tools/db-rehearsal.sh`(container job S-23c) | 위와 같음 | **통과** — Flyway 이력 top **21** · 복원본 `validate` 통과 · 복원 등식 10가지 · forward-fix · 데이터 손실 왕복 |
| `ml-engine` job | 입력 변경 0 | — | 생략(S-20 이 Python 전건을 따로 돌렸다) |

### 7.1 `998b59dd` 위 재실측 (동결 판)

| 항목 | 자리 | 결과 |
|---|---|---|
| `check` job — Kotlin `:adapters:test --rerun check --continue` | evidence 커밋 「rebase(998b59dd) 반영 …」 | **exit 0** · 13m33s · `adapters` · `app` · `workflow` · `settlement` test 와 `leakPatternGate` 실행(나머지 모듈은 입력 불변 UP-TO-DATE) |
| `container` job 재생 | 같은 자리 | **JOB SUCCESS** — `run` step 23 / 23 성공 · 이미지 태그 복원 등식 |
| 백업·복원 리허설(S-23c) | 같은 자리 | **통과** — Flyway top 21 · 복원본 `validate` · 복원 등식 10가지 · forward-fix · 데이터 손실 왕복 |
| `check` job — S-20 `one-command-check.sh` | 같은 자리 | **exit 0** · pytest 1605 passed · vitest 28 passed · W-5 바이트 등식(99658 bytes) · Playwright 2 passed |

### 7.2 수정 라운드 1 재실측 (base `998b59dd`)

아래 표는 산출물 커밋 「style … 앱 역할 마감 test」에서 쟀다. 그 뒤 팀장 확인(정정 공고가 마감을 앞당긴 경우)으로 **test 파일 하나**
(`BidCaseScheduleTest`, test 둘 추가)만 바뀐 산출물 커밋이 더 생겼고(마지막 산출물 커밋 「style … 정정 공고 test」), 그 커밋에서
Kotlin `check` 를 다시 잰다(결과는 동결 보고 · PR 조치 코멘트가 정본). S-20 의 Python · web 절과 container 재생의 입력에는 그 파일이
없다 — `git diff --name-only` 로 두 커밋 사이는 그 test 파일과 evidence 뿐이다.

| 항목 | 결과 |
|---|---|
| `check` job — Kotlin `./gradlew --no-daemon :adapters:test --rerun check --continue` | **exit 0** · 16m10s(07:08–07:24) |
| `check` job — S-20 `one-command-check.sh` | **exit 0** · pytest 1605 passed · vitest 28 passed · W-5 「바이트까지 같다(100113 bytes)」(생성 타입 재생성 포함) · Playwright 2 passed |
| `container` job 재생 | **run step 23 중 21 성공 · 2 실패** — 실패 둘은 S-22e2(web) · S-22e3(proxy) 이미지 취약점 게이트이고 **이 slice 와 무관한 외부 원인**이다: Trivy DB 갱신(2026-10-23T19:15Z)이 새로 잡은 `CVE-2026-111497`(brotli-libs, HIGH, alpine 3.23.6 베이스, 수정판 있음). 같은 job 이 04:13 에는 통과했다. main CI 에도 같은 실패가 온다 — 처방(별 chore PR)은 팀장에게 올렸다. 그 두 step 이 막은 뒤 step 을 재려고 하네스에 「기록된 채로 이어 가는 실패」를 그 두 step 에만 걸고 다시 돌렸다 |
| 백업·복원 리허설(S-23c, 위 재생 안) | **통과** — Flyway top 21 · 복원본 `validate` · 복원 등식 10가지 · forward-fix · 데이터 손실 왕복(V21 의 새 열 · 제약을 실은 판) |
| S-23 · S-23b · S-24 · S-26 | 전부 성공 · 이미지 태그는 재생 전 ID 로 복원(등식 확인) |

**재생 하네스의 첫 시도는 하네스 탓으로 멈췄다**: `GITHUB_PATH` 를 흉내 내지 않아 `install trivy` 가 unbound 변수로 끝났다. 하네스를
고쳐 다시 돌린 것이 위 결과다(산출물 변경 없음).

**container 재생 뒤의 변경**: 생성 타입 재생성(타입 선언 파일 하나)은 재생 **뒤**다. web 번들의 입력이지만 타입 선언이라 산출 JS 가 바뀌지
않는다고 보고 container 를 다시 돌리지 않았다 — 같은 파일을 담은 트리의 web 빌드 · Playwright 는 S-20 이 돌렸다. 이 판단을 verifier
표적으로 남긴다.

**호스트 규율**: 모든 무거운 명령은 `flock ~/.local/bid-vector-build.lock` 아래 돌렸고, 점검 셋(pgrep · `free -m` · `ps`)을
**별도 호출로 먼저** 봤다. 락을 얻은 순간 available ≥ 6GB · swap free ≥ 2GB 를 다시 재고 그 값을 로그 첫 줄에 남겼으며(미달이면
시작하지 않는다), **00:30~03:00 에는 시작하지 않았다**(변이 스크립트는 변이마다 그 창을 확인한다).

### 7.3 수정 라운드 2 재실측 (base `998b59dd`)

| 항목 | 자리 | 결과 |
|---|---|---|
| `check` job — Kotlin `./gradlew --no-daemon check --continue` | 「fix … detekt」 커밋 | **exit 0** · 17m24s(09:44–10:01) · `workflow` 528 · `adapters` 1141 · `app` 711 실행 · 실패 0(XML 확인) · `bidding` 30 은 같은 입력으로 09:41 에 실행(UP-TO-DATE). 앞 시도(「fix … HTTP」 커밋)는 test 전부 초록이고 detekt(함수 수 · 복잡도) · ktlint 에서 붉었다 — 그 고침이 이 커밋이다 |
| `check` job — S-20 `one-command-check.sh` | 마지막 산출물 커밋(「test … 사용자가 적은 마감 지남」) | **exit 0** · pytest 1605 passed · vitest 28 passed · W-5 「바이트까지 같다(100510 bytes)」 · Playwright 2 passed. 그 안의 Kotlin `check` 는 `adapters:test` 실행 · `app` · `bidding` test 는 FROM-CACHE — 입력이 위 줄의 커밋과 같고(두 커밋 사이는 `adapters` test 파일 하나) 거기서 실행해 초록이었다 |
| `container` job 재생 | 같은 자리 | **run step 23 중 21 성공 · 2 실패** — 실패 둘은 S-22e2(web) · S-22e3(proxy) 이미지 취약점 게이트이고 원인은 §7.2 와 같은 외부 `CVE-2026-111497`(brotli-libs)이다. 그 면제는 main 의 #86(allowlist 만)에 있고 이 트리는 그것을 rebase 하지 않았다(D-7B-4) — PR 시점 update-branch 로 받는다. 두 step 에만 「기록된 채로 이어 가기」를 걸었고 나머지 step 은 전부 실행됐다 |
| 백업·복원 리허설(S-23c, 위 재생 안) | 같은 자리 | **통과** — Flyway 이력 19행 top 21 · 복원본 `validate` · 복원 등식 10가지 · forward-fix · 데이터 손실 왕복(V21 의 새 명령 · 복합 FK · CHECK 를 실은 판) |
| S-23 · S-23b · S-24 · S-26 | 같은 자리 | 전부 성공 · 이미지 태그 넷은 재생 전 ID 로 복원(전후 등식 확인) |
| 변이 표(r2) | §3.6 | 변이 28 · 통제군 1 — **RED 27** · 등가 변이 초록 1(OR-TRUE, 근거는 C2-N17 행) · 첫 측정에서 사각이던 D5-actor 는 test 를 더해 RED |

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

**`998b59dd` 위 재실측(동결 판)** — clone 을 「OpenAPI 생성 타입 재생성 …」 커밋(rebase 뒤)에 세웠다: 변경 A 47 · M 32 →
① exit 0 · ② D 47 · M 32 · 그 밖 0 · ③ base 와 diff 0 줄 · untracked 0 · 내 표지 633 → **0** · main 표지 3 보존 · 되돌린 트리 객체 =
`998b59dd` 트리 · ④⑤⑥ `check` **exit 0**(9m42s). (측정 스크립트의 「전」 줄과 ③ 이 옛 base 를 보고 있어 그 두 줄만 `998b59dd` 기준으로
다시 냈다 — 되돌린 트리 자체는 스크립트와 무관하게 `rollback.md` 의 HEAD 판 명령이 만든 것이다.)

**마지막 산출물 커밋(「style … 정정 공고 test」)** — ①~③ 을 같은 절차로 다시 쟀다: A 59 · M 31 → ① exit 0 · ② D 59 · M 31 · ③ diff 0 ·
untracked 0 · 표지 737 → 0 · 되돌린 트리 객체 = `998b59dd` 트리. 더해진 test 파일은 `A` 라 ⓑ 가 통째로 지운다.

**수정 라운드 1 재실측** — clone 을 산출물 커밋 「style … 앱 역할 마감 test」에 세웠다: 변경 A 59 · M 31 →
① exit 0 · ② D 59 · M 31 · 그 밖 0 · ③ base(`998b59dd`)와 diff 0 줄 · untracked 0 · 내 표지 737 → **0** · main 표지 3 보존 · 되돌린 트리 객체
= `998b59dd` 트리(`83461186…`). ④⑤⑥ 의 clone `check` 는 exit 0 이었지만 **162 task 전부 build cache 적중**이라 실행이 아니다 — 그래서
결과로 세지 않고 **트리 동일성으로 갈음**한다: 같은 트리 객체에서 04:08 의 clone `check` 가 **실제로 실행해** exit 0(9m42s)이었다.

**첫 시도는 무효였고 그 사실이 절차의 결함을 드러냈다.** clone 을 산출물 커밋에 세우고 **그 커밋 판의** `rollback.md` 를 읽었더니,
base 를 `56bb7474` 로 옮긴 것이 뒤의 evidence 커밋이라 문면이 옛 base(`a61ddff1`)를 가리켰고, 복원이 **main 의 변경까지** 되돌렸다
(③ diff 7277 줄). 거기서 멈추고 문면을 HEAD 판에서 읽게 고쳐 다시 쟀다. 머지 전 되돌림을 실제로 할 사람도 **HEAD 의 문면**을
읽으므로 측정이 그쪽에 맞는 것이 맞다.

**수정 라운드 2 재실측** — clone 을 마지막 산출물 커밋(「test … 사용자가 적은 마감 지남」)에 세우고 절차 문면은 worktree HEAD 의 `rollback.md` 에서
읽었다: 변경 A 61 · M 31(종류는 `A` · `M` 뿐) → ① exit 0 · ② D 61 · M 31 · 그 밖 0 · ③ base(`998b59dd`)와 diff 0 줄 · untracked 0 · 내 표지
777 → **0** · main 표지 3 보존 · 되돌린 트리 객체 = `998b59dd` 트리(`83461186…`). ④⑤⑥ 은 **트리 동일성으로 갈음**한다 — 같은 트리 객체에서
04:08 의 clone `check` 가 실제로 실행해 exit 0(9m42s)이었다. 첫 시도는 측정 스크립트의 실행 권한이 없어 아무것도 돌지 않았다(하네스 문제,
산출물 무관) — `bash` 로 불러 다시 잰 것이 위 결과다.
