-- M4/4C-2 ① — outbox·inbox 영속(scope.md ①, ADR 0005 D-2·D-3). `state` 어휘 다섯은
-- 4C-1 `OutboxEntryState`(workflow/event/OutboxEntryState.kt)를 그대로 싣는다 — 여기서
-- 다시 정의하지 않는다. 봉투 아홉 필드(EventEnvelope.kt)를 그대로 편다.
--
-- **PK는 애플리케이션이 주는 TEXT다**(설계 검토 (4)-②) — 시퀀스를 만들지 않는다: V2 의
-- `GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public`은 그 시점 스냅샷이라 이 마이그레
-- 이션이 새 시퀀스를 만들면 그 GRANT가 조용히 누락된다. 시퀀스 자체를 안 만들면 그 함정이
-- 없다(`JdbcOutboxPort`가 `entry_id`를 애플리케이션에서 발급한다).
--
-- **actor는 두 컬럼(kind+detail)** — `bidvector.workflow.strategy.Actor`(Operator|System)를
-- notice의 provenance 짝 관례(kind 컬럼 + detail 컬럼)와 같은 형태로 편다. 둘 다 NULL이면
-- actor 없음(EventEnvelope.actor는 nullable)이다.
--
-- **payload는 (payload_type, payload) 짝** — 오늘 payload는 `StrategyEvent.StrategyUpdated`
-- 하나뿐이다(설계 검토 (1) 「payload 직렬화의 타입 판별」 — 열거로 하면 어댑터의 `when`이
-- 미지 타입을 조용히 넘길 수 있다. 등록·복원 둘 다 fail-closed 로 던진다,
-- `bidvector.adapters.event.OutboxPayloadCodec`).
--
-- **outbox 는 idempotency_key 에 UNIQUE 를 걸지 않는다**(4C-1 L-2, 알려진 제한 — 이
-- slice 가 바꾸는 계약이 아니다. ⑤ 는 **inbox** 의 요구다). inbox 의 idempotency_key 는
-- PK 자체가 유일성을 진다(사실상 UNIQUE, 별도 제약을 추가하지 않는다 — 축5 「PK 밖의
-- 별도 UNIQUE는 없다」관례를 그대로 잇는다).
CREATE TABLE outbox (
    entry_id TEXT PRIMARY KEY CHECK (entry_id <> ''),
    event_id TEXT NOT NULL CHECK (event_id <> ''),
    aggregate_id TEXT NOT NULL CHECK (aggregate_id <> ''),
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id TEXT NOT NULL CHECK (correlation_id <> ''),
    causation_id TEXT,
    idempotency_key TEXT NOT NULL CHECK (idempotency_key <> ''),
    actor_kind TEXT,
    actor_detail TEXT,
    payload_type TEXT NOT NULL CHECK (payload_type <> ''),
    payload TEXT NOT NULL CHECK (payload <> ''),
    -- D-M4-5 (a) — 4C-1 전이표의 다섯 어휘를 그대로 CHECK 로 고정한다. 재정의가 아니라
    -- 그 표를 저장 층에 실어 나르는 것이다.
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'CLAIMED', 'DELIVERED', 'FAILED', 'ISOLATED')),
    -- actor 축 — kind가 있으면 detail도 있어야 하고(짝), kind는 두 값 중 하나다.
    CHECK ((actor_kind IS NULL) = (actor_detail IS NULL)),
    CHECK (actor_kind IS NULL OR actor_kind IN ('OPERATOR', 'SYSTEM')),
    inserted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- claim(SELECT ... FOR UPDATE SKIP LOCKED)이 도착 순서를 보도록(설계 검토 (4)-④) — claim
-- 경합 test 의 순서 보장 근거.
CREATE INDEX outbox_pending_order_idx ON outbox (inserted_at) WHERE state = 'PENDING';

-- ⑤ consumer 측 재수신 기록 — idempotency_key 재적재는 행 1개로 수렴한다(dedup, ADR 0005 D-3).
CREATE TABLE inbox (
    idempotency_key TEXT PRIMARY KEY CHECK (idempotency_key <> ''),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GRANT — outbox 는 claim(SELECT ... FOR UPDATE)·등록(INSERT)·전이(UPDATE)가 필요하다.
-- inbox 는 조회(SELECT)·기록(INSERT)만. DELETE·TRUNCATE 는 어느 쪽에도 주지 않는다(보존/
-- 삭제 정책은 운영 소관, 설계 검토 (3) 과잉 2).
GRANT SELECT, INSERT, UPDATE ON outbox TO bidvector_app;
GRANT SELECT, INSERT ON inbox TO bidvector_app;
