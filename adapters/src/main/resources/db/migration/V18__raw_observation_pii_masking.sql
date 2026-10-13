-- M6/6B-3 P-1(운영자 결정 A-3) — `raw_observation` 은 **파기하지 않고 PII 만 지운다**(6G
-- 백테스트 표본 보존). 그래서 V3 의 append-only 계약을 **한 방향으로만** 좁게 연다:
-- DELETE 거부는 그대로이고, UPDATE 는 「마스킹 한 번」일 때만 지난다.
--
-- 왜 열을 더하는가: 「이 행의 PII 는 이미 지웠다」가 행 자신에 적혀 있지 않으면, 마스킹이
-- 멱등인지(두 번째 run 이 무엇을 건드리는지)를 아무도 셀 수 없고 트리거도 「되돌림」을
-- 구별할 수 없다. `pii_masked_at` 은 NULL → 값으로 **한 번만** 간다.
--
-- 트리거 술어는 **열 이름 열거가 아니다**(설계 검토 (1)): 마스킹 열과 표지 열을 뺀 **행 전체**를
-- jsonb 로 비교한다. V19 가 열을 더해도 그 열은 자동으로 「불변이어야 하는 열」에 들어간다 —
-- 열거였다면 새 열이 조용히 변경 가능해진다.

ALTER TABLE raw_observation
    ADD COLUMN pii_masked_at TIMESTAMPTZ;

-- 마스킹된 행만 추려 다음 run 이 건너뛸 수 있게 한다(부분 인덱스 — 아직 마스킹되지 않은 행이
-- 질의 대상이고, 그 집합은 시간이 가도 작게 유지된다).
CREATE INDEX raw_observation_pii_unmasked_idx
    ON raw_observation (inserted_at) WHERE pii_masked_at IS NULL;

-- =============================================================================
-- reject_mutation_except_masking — V3 `reject_mutation` 의 좁은 개정판. **네 관문**을 전부
-- 지나야 UPDATE 가 선다. 하나라도 걸리면 V3 와 같은 ERRCODE(P0001)로 거부한다.
--
--   ① DELETE 는 조건 없이 거부한다(append-only 의 본체 — 운영자 결정 A-3 「파기하지 않는다」).
--   ② 실행 역할이 애플리케이션 역할이면 거부한다(A-1 「런타임 풀의 권한 경계 무변경」).
--      GRANT 가 이미 1차 방어이고(앱 역할에 UPDATE 권한이 없다) 이것이 2차다 — 뒤의 어느
--      마이그레이션이 실수로 UPDATE 를 주더라도 이 줄이 남는다.
--   ③ 마스킹 표지가 NULL → 값으로 **한 번만** 간다. 값 → 값(두 번째 마스킹)·값 → NULL
--      (되돌림)은 거부다 — 원문을 다시 써 넣는 경로가 그 둘이다.
--   ④ 마스킹 열(`payload`)과 표지 열을 뺀 행 전체가 **같아야** 한다 — 마스킹 UPDATE 에
--      다른 열 변경(`observed_at`·`source_endpoint`·`release_sha`…)을 끼워 넣지 못한다.
-- =============================================================================
CREATE FUNCTION reject_mutation_except_masking() RETURNS trigger AS $masking$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '%.% 는 append-only다 — DELETE를 허용하지 않는다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    IF current_user = 'bidvector_app' THEN
        RAISE EXCEPTION '%.% 의 마스킹은 애플리케이션 역할이 하지 않는다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    IF OLD.pii_masked_at IS NOT NULL OR NEW.pii_masked_at IS NULL THEN
        RAISE EXCEPTION '%.% 의 마스킹 표지는 NULL에서 값으로 한 번만 간다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    IF (to_jsonb(OLD) - 'payload' - 'pii_masked_at') <> (to_jsonb(NEW) - 'payload' - 'pii_masked_at') THEN
        RAISE EXCEPTION '%.% 의 마스킹 UPDATE는 마스킹 열 밖을 바꿀 수 없다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$masking$ LANGUAGE plpgsql;

-- 트리거 이름은 그대로 둔다 — 이 표의 계약은 여전히 「추가 전용」이고, 바뀐 것은 그 계약의
-- 예외 하나다. 바뀐 사실은 이름이 아니라 **함수 등재**(`CleanMigrationTriggerTest` 의
-- 트리거 → 함수 쌍)와 거동 test 가 나른다.
DROP TRIGGER raw_observation_append_only ON raw_observation;

CREATE TRIGGER raw_observation_append_only
    BEFORE UPDATE OR DELETE ON raw_observation
    FOR EACH ROW EXECUTE FUNCTION reject_mutation_except_masking();

-- GRANT 는 바뀌지 않는다(운영자 결정 A-1) — 어느 표에도 DELETE·UPDATE 를 새로 주지 않는다.
-- 파기·마스킹은 풀을 지나지 않는 **소유자 비풀링 연결**이 한다(`PersistenceWiring.migrateAsOwner`
-- 선례). 이 마이그레이션에 GRANT 문이 하나도 없다는 것이 그 결정의 등재다.
