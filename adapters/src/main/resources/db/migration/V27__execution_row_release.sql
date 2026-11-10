-- M9/ops-migrations (OPS-10) — 실행 행에 **자기를 만든 릴리스**를 싣는다.
--
-- **표 둘이다.** OPS-10 이 지목한 넷 가운데 `notice`·`opening_result` 는 열을 더하지 않는다 —
-- 그 두 표는 `observation_key` 가 **NOT NULL FK** 로 `raw_observation` 을 가리키고 그 표의
-- `release_sha` 가 NOT NULL 이라, 조인 하나가 「그 **원문을 수집한** 릴리스」를 답한다.
-- **그 답이 「이 행을 쓴 릴리스」인 표는 `notice` 하나다** — 수집 run 이 원문과 canonical 을
-- 같은 run 에 쓰기 때문이다. `opening_result`(그리고 `opening_reserve_price`)는 **승격 러너**가
-- 쓰므로 별 프로세스·다른 릴리스일 수 있고, 조인은 그 질문에 답하지 않는다.
-- 그래도 이번에 칸을 더하지 않는 이유는 비용이 그대로이기 때문이다 — 승격 경로의 `UPDATE` 가
-- V2 의 판 번호 증가와 감사 행 삽입 트리거를 깨우고, 베껴 둔 값은 같은 사실이 두 자리에 살아
-- 한쪽만 바뀌는 날이 온다(보존 규칙은 이 조인을 깨지 않는다 — `raw_observation` 은 파기 대상이
-- 아니라 마스킹 대상이다). 그 결정과 범위는 `OPEN-M9-PROMOTED-ROW-RELEASE` 가 받는다.
-- 조인의 **도달성**은 스키마 사실이라 test 가 잠근다(`ExecutionRowReleaseTest` 의 FK·NOT NULL
-- 등식) — 그 등식이 재는 것은 도달성이고, 「누가 썼는가」와 같지 않다.
--
-- `collection_run`·`outbox` 는 그 경로가 **없다** — 관측을 가리키는 열이 없고(회계 행은 배치
-- 하나를 요약하고, outbox 행은 도메인 write 에서 태어난다) 그래서 이 둘만 칸을 받는다.
--
-- **기존 행은 NULL 로 남는다.** 지어낸 값으로 과거를 채우지 않는다(sentinel 금지,
-- `data-dictionary.md` §4.1) — 이 두 표에는 릴리스를 되짚을 원천이 아예 없다. 대신 **앞으로**
-- 들어오는 행은 트리거가 요구한다(V26 의 종단 사유와 같은 모양: CHECK 로는 기존 행 때문에
-- NOT NULL 을 적을 수 없다).

-- 잠금 대기의 상한(V18·V19 가 세운 관례, V23·V24·V25·V26 과 같은 사유) — 기본 `lock_timeout` 은
-- 0 이고 migrate 가 기동의 부수효과라 그 대기는 곧 **기동 정지**다. 상한을 두면 「정지」가 아니라
-- 「실패」가 된다. **이 파일은 표 둘의 ACCESS EXCLUSIVE 를 차례로 쥔다** — 수집 cron 이
-- `collection_run` 에 쓰고 relay 가 `outbox` 연결을 쥐므로 둘 다 경합 상대가 상시 있다.
-- `SET LOCAL` 은 트랜잭션 범위이고 Flyway 는 마이그레이션마다 자기 트랜잭션이라, V26 의 같은
-- 줄이 이 파일을 덮어 주지 않는다(그래서 두 파일이 각자 적는다).
SET LOCAL lock_timeout = '5s';

ALTER TABLE collection_run ADD COLUMN release_sha TEXT;
ALTER TABLE outbox ADD COLUMN release_sha TEXT;

-- CHECK 가 막는 것은 **빈 문자열**이고 공백 하나(`' '`)는 통과한다 — 어휘가 아니라 식별자라
-- 저장 층에서 모양을 재지 않는다. 공백·길이는 쓰기 전에 값 객체(`ReleaseIdentifier`)가 지고,
-- 이 CHECK 는 V1 의 `raw_observation.release_sha <> ''` 와 같은 층의 최소 방어다.
ALTER TABLE collection_run
    ADD CONSTRAINT collection_run_release_sha_check
    CHECK (release_sha IS NULL OR release_sha <> '');
ALTER TABLE outbox
    ADD CONSTRAINT outbox_release_sha_check
    CHECK (release_sha IS NULL OR release_sha <> '');

-- 새 행은 식별자를 요구하고, **적힌 식별자는 한 번만 적는다**(V26 의 종단 사유와 같은 모양) —
-- 지우기와 바꿔치기를 함께 거부한다. 「어느 릴리스가 이 행을 만들었는가」가 OPS-10 의 전부인데
-- 지우기만 막으면 값 A → 값 B 가 두 검사를 모두 지나, 그 답이 나중에 덮인다. 그 길은 `outbox`
-- 에서 실제로 열려 있다 — V6 의 `GRANT SELECT, INSERT, UPDATE ON outbox TO bidvector_app` 가 앱
-- 역할에 UPDATE 를 준다(`collection_run` 은 V2 가 SELECT·INSERT 만 준다).
--
-- **갱신에서 값을 요구하지는 않는다**(NULL → NULL 은 통과) — outbox 의 상태 전이 UPDATE 는 이
-- 열을 손대지 않고, 열 추가 전에 들어온 행(`release_sha` NULL)도 전이가 계속 돌아야 한다.
-- `OLD.release_sha IS NOT NULL` 전제가 그 둘을 함께 세운다: 적힌 적 없는 행은 이 검사를 지나고,
-- 한 번 적힌 값은 움직이지 않는다.
CREATE FUNCTION require_release_sha() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.release_sha IS NULL THEN
        RAISE EXCEPTION '% 의 새 행은 릴리스 식별자를 요구한다', TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;
    IF TG_OP = 'UPDATE'
        AND OLD.release_sha IS NOT NULL
        AND NEW.release_sha IS DISTINCT FROM OLD.release_sha THEN
        RAISE EXCEPTION '% 의 릴리스 식별자는 한 번만 적는다: %', TG_TABLE_NAME, OLD.release_sha
            USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER collection_run_release_sha_required
    BEFORE INSERT OR UPDATE ON collection_run
    FOR EACH ROW EXECUTE FUNCTION require_release_sha();

CREATE TRIGGER outbox_release_sha_required
    BEFORE INSERT OR UPDATE ON outbox
    FOR EACH ROW EXECUTE FUNCTION require_release_sha();
