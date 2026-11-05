-- M9/paper-bid — 알림함 중복 억제 키에 **투찰 종류 축**을 더한다(D-M9P-1 · D-M9P-2).
--
-- 번호는 D-6A1-12 의 정의를 따른다(「PR 시점 main 최대 번호보다 크고, 병행 레인이 선점 통보한
-- 번호를 피한다」) — 착수 시점 main 최대는 V24 이고 **V25 는 팀장이 이 레인에 고정 배정**했다
-- (V26~V28 은 운영 마이그레이션 레인 몫). PR 직전에 그 정의대로 재확인한다.
--
-- =============================================================================
-- 왜 이 마이그레이션이 필요한가
--
-- 7C③ 의 PK 는 `(account_id, kind, notice_number, notice_round)` 였고 투찰 종류 축이 **없었다**.
-- 그래서 같은 공고에 서면(페이퍼) 건과 실투찰 건이 함께 있으면 둘의 소식이 **한 항목을
-- 덮어썼다** — 가상 연습의 결과가 실제 결과를 가리는 꼴이다. 7C③-b 와 7B 는 그것을 피하려고
-- 원천 양쪽에 가드를 두어 **페이퍼를 아예 버렸다**(`BidCaseWorkflow` 되비춤의 `asReal()` ·
-- 훑기 질의의 `bid_kind = 'REAL'`). 그 대가는 「서면으로 연습하는 운영자는 알림을 받지 못한다」
-- 였고 그것이 `OPEN-7C3B-PAPER-BID-NOTIFICATION` 이다.
--
-- 이 파일은 그 축을 키에 넣어 **두 연습이 각자의 항목을 갖게** 한다. 그러면 가드가 필요 없다 —
-- 덮어쓸 자리가 애초에 없으므로 「버려서 지키던 것」을 구조가 지킨다.
--
-- **어휘가 둘이 아니라 셋이다.** 알림 원천 셋 가운데 하나는 건이 아니라 **공고**에 붙는다
-- (투찰가 요청 기록 — 7C②; 새 후보도 같다: 건이 생기기 전의 소식이다). PK 열은 NOT NULL 이라
-- NULL 로 「축이 없음」을 적을 수 없으므로 `'NONE'` 을 둔다. 그 값이 **어느 종류에만 올 수
-- 있는지**는 아래 ③ 의 짝 제약이 못박는다 — 투찰가 요청을 실투찰로 라벨하는 길과 마감 알림을
-- 축 없이 적는 길이 **둘 다** 거부된다.
--
-- **열 하나만 더한다.** V23 이 세운 성질 둘이 그대로 성립한다: ① 「열 전수가 닫힌 어휘이거나
-- 공고 키이거나 등재된 (이름, 자료형) 쌍」 — 새 열은 닫힌 어휘 CHECK 에 갇힌다. ② 열 단위
-- GRANT — 아래 ⑤.
-- =============================================================================

-- 잠금 대기의 상한(V18·V19 가 세운 관례, V23·V24 와 같은 사유) — 이 파일이 쥐는 것은 한 표의
-- ACCESS EXCLUSIVE 뿐이고 창이 짧지만, 기본 `lock_timeout` 은 0 이고 migrate 가 기동의
-- 부수효과라 그 대기는 곧 **기동 정지**다. 상한을 두면 「정지」가 아니라 「실패」가 된다.
SET LOCAL lock_timeout = '5s';

-- ① 열 추가 — **기본값과 함께**. 기존 행이 NOT NULL 을 만족해야 하고, 기본값이 비휘발성이라
--    PostgreSQL 11+ 는 표를 다시 쓰지 않는다(메타데이터 연산).
ALTER TABLE notification_inbox ADD COLUMN bid_kind TEXT NOT NULL DEFAULT 'REAL';

-- ② backfill — **종류로 가른다.** 기본값 `'REAL'` 이 맞는 것은 **건에서 온** 항목이고, 그 값은
--    추측이 아니라 **사실**이다: 이 표에 건 기반 항목을 적는 원천 둘이 위 가드로 실투찰만
--    적었다. 건에서 오지 않는 종류 둘은 그 사실이 성립하지 않으므로 `'NONE'` 으로 옮긴다 —
--    남기면 투찰가 요청 기록이 **실투찰로 라벨되고**, 그 행은 아래 ③ 의 검증에서 걸려
--    마이그레이션이 실패한다(즉 이 UPDATE 를 잊는 길은 조용하지 않다).
UPDATE notification_inbox
SET bid_kind = 'NONE'
WHERE kind IN ('NEW_CANDIDATE', 'BID_PRICE_REQUEST_RECORDED');

-- ③ 기본값 제거 — **② 뒤에 반드시.** 남기면 `bid_kind` 를 빠뜨린 INSERT 가 **조용히 실투찰로**
--    들어간다. 값을 지어내는 것이 코드가 아니라 DDL 이 되고, 그 지어냄은 어느 test 에도 걸리지
--    않는다(열 래칫의 「기본값 있음」 축이 이 표에서는 `created_at` 하나다).
ALTER TABLE notification_inbox ALTER COLUMN bid_kind DROP DEFAULT;

-- ④ 어휘 CHECK — Kotlin `NotificationBidKind` 와 **같아야 한다**. 그 등식은
--    `NotificationInboxPrivacyTest` 가 이 제약의 정의에서 어휘를 읽어 잰다(종류·detail 축과
--    같은 술어). 한쪽만 늘리면 빌드가 붉다. 자유 문자열이 아니므로 이 축으로 PII 가 들어올 수
--    없다는 V23 의 성질이 새 열에도 선다.
ALTER TABLE notification_inbox
    ADD CONSTRAINT notification_inbox_bid_kind_check CHECK (
        bid_kind IN ('REAL', 'PAPER', 'NONE')
    );

-- ⑤ 종류 ↔ 투찰 종류 짝(심층 방어) — **어느 종류가 축을 갖는가**를 여기서 정한다. 어휘만
--    닫고 이 제약을 두지 않으면 마감 알림이 `'NONE'` 으로, 투찰가 요청이 `'PAPER'` 로 들어간다.
--    Kotlin 쪽은 종류 전수 표(`NotificationKind.bidKindAxis`)가 같은 규칙을 지고, 둘이 같은
--    파티션이라는 등식과 짝의 **전수 교차**(종류 여덟 × 어휘 셋)를 실 DB test 가 잰다.
--
--    **②·④ 뒤라야 한다**: `ADD CONSTRAINT` 는 기존 행을 검증하므로 backfill 이 앞서지 않으면
--    투찰가 요청 행에서 실패한다.
--
--    두 갈래가 종류 전수를 **덮는다**. V23·V24 의 detail 짝 CHECK 와 달리 `IS NOT NULL` 갈래가
--    필요 없다 — 이 열은 NOT NULL 이라 세 값 논리가 제약을 열어 줄 자리가 없다.
ALTER TABLE notification_inbox
    ADD CONSTRAINT notification_inbox_bid_kind_pair_check CHECK (
        (kind IN ('NEW_CANDIDATE', 'BID_PRICE_REQUEST_RECORDED') AND bid_kind = 'NONE')
        OR (kind NOT IN ('NEW_CANDIDATE', 'BID_PRICE_REQUEST_RECORDED') AND bid_kind IN ('REAL', 'PAPER'))
    );

-- ⑥ PK 교체 — 중복 억제 키가 **다섯 열**이 된다. 파생 문자열 열(`dedupe_key`)을 두지 않는
--    V23 주석 ① 의 결정은 그대로다: 「한 계정 · 한 공고 · **한 투찰 종류** · 한 알림 종류에
--    한 항목」이 다섯 열의 PK 로 표현되고 PK 밖 UNIQUE 는 여전히 없다.
--
--    **`IF EXISTS` 를 붙이지 않는다**(V24 와 같은 판단). 이름이 틀렸다면 그 자리에서
--    마이그레이션이 **실패해야 한다** — 붙이면 옛 PK 가 남은 채 새 PK 를 더하려다 다른 오류로
--    떨어지거나, 더 나쁘게는 축이 늘지 않은 상태로 지나간다.
--
--    **열 순서가 V23 과 다르다.** 공고 쌍을 앞으로 올리고 종류를 맨 뒤로 보낸다:
--    묶음 읽음 처리(`MARK_NOTICE_READ`)의 술어가 `(account_id, notice_number, notice_round,
--    bid_kind)` 이고 이 순서에서 그것이 PK 인덱스의 **접두**가 된다(V23 순서에서는 `account_id`
--    하나만 접두였다). 목록 질의의 묶음 파티션도 같은 축이다. 그래서 축이 늘면서 질의 하나가
--    나아지고, 보조 인덱스를 더할 근거가 없다 — 아래 「인덱스를 더하지 않는다」.
ALTER TABLE notification_inbox DROP CONSTRAINT notification_inbox_pkey;

ALTER TABLE notification_inbox
    ADD PRIMARY KEY (account_id, notice_number, notice_round, bid_kind, kind);

-- **인덱스를 더하지 않는다**(V23 의 둘을 재검토한 결과). `(account_id, occurred_at DESC)` 는
-- 목록의 계정 경계·정렬 축이고 새 열과 무관하다. `(created_at)` 는 보존 cutoff 축이고 무관하다.
-- 새 PK 가 묶음 질의의 접두를 이미 덮으므로(위 ⑥) 셋째 인덱스는 쓰기 비용만 늘린다.

-- =============================================================================
-- ⑦ GRANT — **열 단위**다(V23 의 결정을 새 열까지 잇는다). 전 목록을 다시 적는다: 읽는 사람이
--    「앱 역할이 이 표에 할 수 있는 일」을 이 파일 하나에서 보게 하려는 것이고, 같은 부여를
--    다시 하는 것은 무해하다.
--
--  · INSERT 에 `bid_kind` 를 **더한다** — 어댑터의 upsert 가 넣는 열이다. `created_at`
--    (기본값 `now()`)과 `read_at`(읽음 처리의 UPDATE 로만 움직인다)은 코드가 INSERT 에 쓰지
--    않으므로 여전히 빠진다(V23 의 사유: 적재 시각을 INSERT 때 먼 미래로 지정하는 길).
--
--  · UPDATE 목록은 **그대로다 — `bid_kind` 를 넣지 않는다.** 넣으면 앱 역할이 항목을 다른
--    축으로 **옮길** 수 있고, 그것은 ⓐ 중복 억제 우회(한 사실을 두 항목으로)이며 ⓑ 페이퍼를
--    실투찰로 바꿔 쓰는 길이다. V23 이 계정·종류·공고 키를 UPDATE 밖에 둔 것과 **같은 사유**이고,
--    투찰 종류는 이제 그 키의 한 열이다. 앱 역할로 직접 쏘아 재는 자리가
--    `NotificationInboxPrivilegeTest` 다.
--
--  · DELETE·TRUNCATE 는 여전히 주지 않는다 — 파기는 소유자 연결의 일이다.
-- =============================================================================
GRANT SELECT ON notification_inbox TO bidvector_app;
GRANT INSERT (account_id, bid_kind, kind, notice_number, notice_round, detail_code, occurred_at, updated_at)
    ON notification_inbox TO bidvector_app;
GRANT UPDATE (detail_code, occurred_at, updated_at, read_at) ON notification_inbox TO bidvector_app;
