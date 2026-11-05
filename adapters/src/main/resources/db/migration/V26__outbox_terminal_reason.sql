-- M9/ops-migrations (OPS-05) — 종단 행의 **사유**를 저장 층에 싣는다. 지금까지 「왜 이 행이
-- FAILED·ISOLATED 인가」는 relay 의 run 보고와 로그에만 있었고 run 이 끝나면 사라졌다.
-- 어휘는 `bidvector.workflow.event.OutboxTerminalReason` 일곱을 그대로 싣는다 — 여기서 다시
-- 정의하지 않는다(V6 가 `state` 다섯을 실은 것과 같은 관례).
--
-- **사유가 상태를 정한다.** Kotlin 쪽은 종단 명령이 사유만 받고(`OutboxCommand.Terminate`),
-- 목표 상태를 사유에서 유도한다 — 어긋난 조합을 만들 자리가 없다. 아래 CHECK 는 그 사상을
-- 저장 층에서 다시 선언해, 이 표에 다른 경로로 들어오는 UPDATE 도 같은 쌍만 쓰게 한다.
--
-- **기존 종단 행은 NULL 로 남는다**(열 추가 시점에 이미 DELIVERED·FAILED·ISOLATED 인 행).
-- 그 행들의 사유는 로그에만 갔으므로 **진실한 원천이 없다** — 지어낸 값(`legacy`·`unknown`)
-- 으로 채우지 않는다(sentinel 금지, `data-dictionary.md` §4.1). 그래서 쌍을 「종단이면 사유가
-- 있다」로 적을 수 없다: 그렇게 적으면 이 마이그레이션 자신이 기존 행에 걸려 적용되지 않는다.
-- 대신 두 겹으로 가른다 — ① CHECK: **사유가 있으면** 그 사유의 상태다(비종단 행에 사유가
-- 붙는 길을 닫는다) ② 트리거: **새로** 종단에 닿는 INSERT·UPDATE 는 사유를 요구한다
-- (V18 의 `pii_masked_at` NULL→값 1회 트리거와 같은 형태).
--
-- 열·PK·GRANT·인덱스 변경은 **CHECK 하나와 트리거 하나 외에 0** 이다.
ALTER TABLE outbox ADD COLUMN terminal_reason TEXT;

-- ① 사유 어휘 + 사유→상태 사상을 한 제약으로 닫는다. 행 값 비교(`IN ((...), ...)`)라
--    「어휘에 있으나 다른 상태에 붙은」 조합도 같이 거부된다 — 어휘만 세는 CHECK 둘로
--    나누면 `FAILED` + `ORPHANED_CLAIM` 이 통과한다.
ALTER TABLE outbox
    ADD CONSTRAINT outbox_terminal_reason_state_check
    CHECK (
        terminal_reason IS NULL
        OR (state, terminal_reason) IN (
            ('DELIVERED', 'DELIVERED'),
            ('DELIVERED', 'DUPLICATE_SKIPPED'),
            ('FAILED', 'DELIVERY_REJECTED'),
            ('FAILED', 'DELIVERY_SUPPRESSED'),
            ('ISOLATED', 'DELIVERY_OUTCOME_UNKNOWN'),
            ('ISOLATED', 'UNKNOWN_PAYLOAD'),
            ('ISOLATED', 'ORPHANED_CLAIM')
        )
    );

-- ② 새 종단 전이는 사유를 요구하고, 적힌 사유는 **한 번만** 적는다. 「새」의 판정은 **이
--    갱신이 종단으로 옮기는가**다 — 이미 종단인 행(열 추가 전에 종단한 행)을 건드리는
--    갱신은 첫 검사에 걸리지 않는다. 둘째 검사가 지우기와 바꿔치기를 함께 거부한다
--    (앱 역할은 이 표에 UPDATE 권한이 있으므로 사유를 고치는 길이 열려 있었다).
CREATE FUNCTION require_outbox_terminal_reason() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
        AND OLD.terminal_reason IS NOT NULL
        AND NEW.terminal_reason IS DISTINCT FROM OLD.terminal_reason THEN
        RAISE EXCEPTION 'outbox 종단 사유는 한 번만 적는다: entry_id=%', NEW.entry_id;
    END IF;

    IF NEW.state IN ('DELIVERED', 'FAILED', 'ISOLATED')
        AND NEW.terminal_reason IS NULL
        AND (TG_OP = 'INSERT' OR OLD.state NOT IN ('DELIVERED', 'FAILED', 'ISOLATED')) THEN
        RAISE EXCEPTION 'outbox 종단 전이는 사유를 요구한다: entry_id=% state=%', NEW.entry_id, NEW.state;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER outbox_terminal_reason_required
    BEFORE INSERT OR UPDATE ON outbox
    FOR EACH ROW EXECUTE FUNCTION require_outbox_terminal_reason();
