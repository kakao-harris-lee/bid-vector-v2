-- M9/ops-migrations (OPS-10) — 실행 행에 **자기를 만든 릴리스**를 싣는다.
--
-- **표 둘이다.** OPS-10 이 지목한 넷 가운데 `notice`·`opening_result` 는 열을 더하지 않는다 —
-- 그 두 표는 `observation_key` 가 **NOT NULL FK** 로 `raw_observation` 을 가리키고 그 표의
-- `release_sha` 가 NOT NULL 이므로, 「이 행을 만든 릴리스」가 조인 하나로 이미 답해진다.
-- 베껴 두면 같은 사실이 두 자리에 살고 한쪽만 바뀌는 날이 온다(보존 규칙도 이 조인을 깨지
-- 않는다 — `raw_observation` 은 파기 대상이 아니라 마스킹 대상이다). 그 도달성은 스키마
-- 사실이라 test 가 잠근다(`ExecutionRowReleaseTest` 의 FK·NOT NULL 등식).
--
-- `collection_run`·`outbox` 는 그 경로가 **없다** — 관측을 가리키는 열이 없고(회계 행은 배치
-- 하나를 요약하고, outbox 행은 도메인 write 에서 태어난다) 그래서 이 둘만 칸을 받는다.
--
-- **기존 행은 NULL 로 남는다.** 지어낸 값으로 과거를 채우지 않는다(sentinel 금지,
-- `data-dictionary.md` §4.1) — 이 두 표에는 릴리스를 되짚을 원천이 아예 없다. 대신 **앞으로**
-- 들어오는 행은 트리거가 요구한다(V26 의 종단 사유와 같은 모양: CHECK 로는 기존 행 때문에
-- NOT NULL 을 적을 수 없다).
ALTER TABLE collection_run ADD COLUMN release_sha TEXT;
ALTER TABLE outbox ADD COLUMN release_sha TEXT;

ALTER TABLE collection_run
    ADD CONSTRAINT collection_run_release_sha_check
    CHECK (release_sha IS NULL OR release_sha <> '');
ALTER TABLE outbox
    ADD CONSTRAINT outbox_release_sha_check
    CHECK (release_sha IS NULL OR release_sha <> '');

-- 새 행은 식별자를 요구하고, 적힌 식별자는 지울 수 없다. **갱신에서 요구하지는 않는다** —
-- outbox 의 상태 전이 UPDATE 는 이 열을 손대지 않고, 열 추가 전에 들어온 행(release_sha
-- NULL)도 전이가 계속 돌아야 한다. 「지우지 않는다」만 지면 그 둘이 함께 선다.
CREATE FUNCTION require_release_sha() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.release_sha IS NULL THEN
        RAISE EXCEPTION '% 의 새 행은 릴리스 식별자를 요구한다', TG_TABLE_NAME;
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.release_sha IS NOT NULL AND NEW.release_sha IS NULL THEN
        RAISE EXCEPTION '% 의 릴리스 식별자를 지울 수 없다', TG_TABLE_NAME;
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
