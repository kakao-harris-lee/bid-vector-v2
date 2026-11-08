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
-- **열 추가 전 종단 행에는 틈이 하나 있다**(의도된 대가). 둘째 검사가 「이 갱신이 종단으로
-- 옮기는가」를 묻기 때문에, 이미 종단이고 사유가 NULL 인 legacy 행은 종단 상태 사이를 사유 없이
-- 옮길 수 있다(`FAILED` → `DELIVERED`). 출하 경로로는 닿지 않는다 — 전이 SQL 이 모두
-- `WHERE state = 'CLAIMED'` 다. 그 틈을 막으려면 기존 행에 지어낸 사유를 넣어야 하고, 그것이
-- 이 마이그레이션이 피하는 바로 그 일이다(sentinel 금지).
--
-- 열·PK·GRANT·인덱스 변경은 **CHECK 하나와 트리거 하나 외에 0** 이다.

-- 잠금 대기의 상한(V18·V19 가 세운 관례, V23·V24·V25 와 같은 사유) — 이 파일이 쥐는 것은 한 표의
-- ACCESS EXCLUSIVE 뿐이고 창이 짧지만, 기본 `lock_timeout` 은 0 이고 migrate 가 기동의
-- 부수효과라 그 대기는 곧 **기동 정지**다. 상한을 두면 「정지」가 아니라 「실패」가 된다.
-- **이 표에서 특히 현실적이다**: relay 소비자가 `FOR UPDATE SKIP LOCKED` 로 연결을 본문 내내
-- 쥐고, 반입은 compose 교체라 구판 컨테이너가 연결을 쥔 채 신판이 migrate 를 시작할 수 있다.
SET LOCAL lock_timeout = '5s';

ALTER TABLE outbox ADD COLUMN terminal_reason TEXT;

-- ① 사유 어휘 + 사유→상태 사상을 한 제약으로 닫는다. 어휘가 아니라 **쌍**을 적으므로
--    「어휘에 있으나 다른 상태에 붙은」 조합도 같이 거부된다 — 어휘만 세는 CHECK 둘로
--    나누면 `FAILED` + `ORPHANED_CLAIM` 이 통과한다.
--
--    **쌍을 행 값 비교(`(state, terminal_reason) IN ((...), ...)`)로 적지 않는다.** 진리표는
--    같지만 그 문법은 파서가 **왼쪽으로 깊게 중첩된** OR 트리로 저장하고, 덤프가 싣는
--    `pg_get_constraintdef` 가 그 중첩을 그대로 찍는다. 복원이 그 텍스트를 다시 읽을 때 파서는
--    같은 OR 사슬을 **평평하게** 접으므로, 제약은 그대로인데 복원본의 문면만 달라진다 —
--    `tools/db-rehearsal.sh` 의 복원 등식 `constraints` 가 그 한 줄로 붉어졌다(CI `container`
--    job, 2026-11-08 실측). 아래처럼 OR 를 평평하게 적은 식은 자기 렌더링의 **고정점**이라
--    왕복에서 문면이 변하지 않는다. 그 성질은 `CheckConstraintRenderingFixpointTest` 가
--    스키마의 CHECK 전수로 잠근다 — 리허설은 CI 뒤쪽 비싼 job 에서야 같은 말을 한다.
ALTER TABLE outbox
    ADD CONSTRAINT outbox_terminal_reason_state_check
    CHECK (
        terminal_reason IS NULL
        OR (state = 'DELIVERED' AND terminal_reason = 'DELIVERED')
        OR (state = 'DELIVERED' AND terminal_reason = 'DUPLICATE_SKIPPED')
        OR (state = 'FAILED' AND terminal_reason = 'DELIVERY_REJECTED')
        OR (state = 'FAILED' AND terminal_reason = 'DELIVERY_SUPPRESSED')
        OR (state = 'ISOLATED' AND terminal_reason = 'DELIVERY_OUTCOME_UNKNOWN')
        OR (state = 'ISOLATED' AND terminal_reason = 'UNKNOWN_PAYLOAD')
        OR (state = 'ISOLATED' AND terminal_reason = 'ORPHANED_CLAIM')
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
        RAISE EXCEPTION 'outbox 종단 사유는 한 번만 적는다: entry_id=%', NEW.entry_id
            USING ERRCODE = 'P0001';
    END IF;

    IF NEW.state IN ('DELIVERED', 'FAILED', 'ISOLATED')
        AND NEW.terminal_reason IS NULL
        AND (TG_OP = 'INSERT' OR OLD.state NOT IN ('DELIVERED', 'FAILED', 'ISOLATED')) THEN
        RAISE EXCEPTION 'outbox 종단 전이는 사유를 요구한다: entry_id=% state=%', NEW.entry_id, NEW.state
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER outbox_terminal_reason_required
    BEFORE INSERT OR UPDATE ON outbox
    FOR EACH ROW EXECUTE FUNCTION require_outbox_terminal_reason();
