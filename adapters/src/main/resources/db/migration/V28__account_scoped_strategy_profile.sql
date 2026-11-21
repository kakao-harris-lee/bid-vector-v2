-- M15/15A — 전략 · 개정 이력 · 프로필 · 편집 세션을 **계정 소유**로 옮긴다.
--
-- 지금까지 네 표는 싱글턴이었다: `operator_strategy`·`operator_profile` 은 `CHECK (id = 1)` 로
-- 한 행을 못박았고, `operator_strategy_revision` 은 `revision` 하나가 PK 였으며 `edit_session`
-- 에는 계정 칸이 아예 없었다. 그 전제는 「운영자가 한 사람」일 때만 성립한다 — M15 가 가입을
-- 열면(15B) 가입자가 운영자의 감시 조건과 면허를 **보고 고치고**, 운영자의 후보 알림을 전부
-- 받는다. 그래서 이 마이그레이션이 15B 보다 먼저 선다.
--
-- **이관 규칙 — 가장 작은 `account.id`.** 지금 있는 싱글턴 행은 지금의 운영 계정의 것이고,
-- 그 계정은 `account` 표에서 가장 먼저 만들어진 행이다(V20 의 identity 는 단조 증가한다).
-- 이 규칙은 Telegram 전역 경로가 쓰는 규칙과 **같은 규칙**이다(계약 「Telegram」 행) — 두
-- 자리가 다른 규칙을 쓰면 운영 계정이 둘로 갈린다.
--
-- **계정이 0 이면 그 행을 버린다.** `min(id)` 가 NULL 이라 붙일 주인이 없고, 남겨 두려면
-- `account_id` 를 nullable 로 두어야 하는데 그러면 「주인 없는 전략」이라는 상태가 표에
-- 상설된다 — 그 행은 어느 계정도 읽지 못한다(질의 술어가 `account_id = ?` 하나다). 마이그레이션을
-- 실패시키지도 않는다: migrate 는 기동의 부수효과라 실패가 곧 **기동 정지**이고, 깨끗한 설치
-- (네 표가 비어 있다)에서는 이 걸음이 0 행이다. 운영 DB 는 계정이 하나라 이 갈래에 닿지 않는다.
-- 되돌릴 수 없는 선택이라 slice 계약의 알려진 제한으로 등재했고, 세 갈래(계정 0 · 1 · 2 이상)를
-- `AccountScopedMigrationTest` 가 잰다.
--
-- **싱글턴 키는 `DROP COLUMN` 으로 걷는다.** `CHECK (id = 1)` 은 열에 딸린 제약이라 열이 사라질
-- 때 함께 사라진다 — 제약 **이름**을 손으로 적지 않는다(V9·V12 가 이름을 주지 않았고, 그 자동
-- 이름을 이 파일에 베끼면 같은 사실이 두 자리에 산다).
--
-- **유일 제약**: 전략 · 프로필은 계정당 한 행(`PRIMARY KEY (account_id)`), 개정 이력은 계정
-- 범위 키(`PRIMARY KEY (account_id, revision)`) — 두 계정이 같은 개정 번호를 **각자** 가진다.
-- `edit_session` 은 `id` 가 서버가 만드는 UUID 라 이미 전역 유일하고, PK 를 그대로 두고
-- `account_id` 만 NOT NULL 로 단다. 남의 세션에 쓰는 길은 저장 경로의 upsert 가 `WHERE` 로
-- 막는다(`Sql.UPSERT_EDIT_SESSION`).
--
-- **GRANT 는 더하지 않는다.** V8·V9·V12 의 부여가 전부 **표 단위**라 새 열이 그 부여에 함께
-- 든다(열 단위 부여였다면 여기서 다시 적어야 했다). FK 검사는 `bidvector_app` 이 `account` 에
-- 이미 가진 SELECT 로 선다(V21·V22·V23 이 같은 모양이다).

-- 잠금 대기의 상한(V18·V19 가 세운 관례, V23~V27 과 같은 사유) — 기본 `lock_timeout` 은 0 이고
-- migrate 가 기동의 부수효과라 그 대기는 곧 기동 정지다. 상한을 두면 「정지」가 아니라 「실패」가
-- 된다. **이 파일은 네 표의 ACCESS EXCLUSIVE 를 차례로 쥔다** — 편집 세션과 전략은 HTTP 요청이,
-- 프로필은 자격 판정이 상시 읽으므로 경합 상대가 있다.
SET LOCAL lock_timeout = '5s';

-- ── operator_strategy ────────────────────────────────────────────────────────
ALTER TABLE operator_strategy ADD COLUMN account_id BIGINT REFERENCES account (id);
UPDATE operator_strategy SET account_id = (SELECT min(id) FROM account);
DELETE FROM operator_strategy WHERE account_id IS NULL;
ALTER TABLE operator_strategy DROP COLUMN id;
ALTER TABLE operator_strategy ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE operator_strategy ADD PRIMARY KEY (account_id);

-- ── operator_strategy_revision ───────────────────────────────────────────────
ALTER TABLE operator_strategy_revision ADD COLUMN account_id BIGINT REFERENCES account (id);
UPDATE operator_strategy_revision SET account_id = (SELECT min(id) FROM account);
DELETE FROM operator_strategy_revision WHERE account_id IS NULL;
ALTER TABLE operator_strategy_revision DROP CONSTRAINT operator_strategy_revision_pkey;
ALTER TABLE operator_strategy_revision ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE operator_strategy_revision ADD PRIMARY KEY (account_id, revision);

-- ── operator_profile ─────────────────────────────────────────────────────────
ALTER TABLE operator_profile ADD COLUMN account_id BIGINT REFERENCES account (id);
UPDATE operator_profile SET account_id = (SELECT min(id) FROM account);
DELETE FROM operator_profile WHERE account_id IS NULL;
ALTER TABLE operator_profile DROP COLUMN id;
ALTER TABLE operator_profile ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE operator_profile ADD PRIMARY KEY (account_id);

-- ── edit_session ─────────────────────────────────────────────────────────────
-- PK(`id`)는 그대로다 — 세션 id 는 서버가 만드는 UUID 라 계정을 섞어도 충돌하지 않는다.
ALTER TABLE edit_session ADD COLUMN account_id BIGINT REFERENCES account (id);
UPDATE edit_session SET account_id = (SELECT min(id) FROM account);
DELETE FROM edit_session WHERE account_id IS NULL;
ALTER TABLE edit_session ALTER COLUMN account_id SET NOT NULL;
