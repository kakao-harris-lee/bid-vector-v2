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
--
-- **이 파일은 인덱스를 만들지 않는다**(mig H-1). `ALTER TABLE` 이 쥐는 `ACCESS EXCLUSIVE` 가
-- Flyway 의 한 트랜잭션 안에서 전수 인덱스 빌드 내내 유지되면 적용 창이 「전수·전차단·기동중」이
-- 된다(migrate 는 `DataSource` 빈 생성의 부수효과다). 인덱스는 V19 로 떼어냈고, 그래서 **이 파일의
-- 문장은 전부 메타데이터 변경**이다(열 추가 nullable·DEFAULT 없음 · CHECK `NOT VALID`(스캔 없음)
-- · 함수 생성 · 트리거 교체) — 밀리초로 끝나고, `ALTER` + 트리거 교체의 원자성은 여기 남는다
-- (교체 순간의 무방비 구간이 없다).

-- **첫 문장은 잠금 대기의 상한이다**(mig M-1). 이 파일의 작업은 밀리초인데 창은 그렇지 않다:
-- `ALTER TABLE` 의 `ACCESS EXCLUSIVE` 는 그 표에 열린 트랜잭션 뒤에 줄을 서고, **그 뒤에 오는
-- 평범한 읽기까지 전부 같은 큐에 선다**(실측). 기본 `lock_timeout` 은 0 이고 Flyway 는 걸지
-- 않으므로, 그 대기는 열린 트랜잭션의 길이만큼 무한하다 — 기동 부수효과이므로 곧 기동 정지다.
-- 상한을 두면 같은 상황이 **실패**가 되고(기동 실패 → 긴 트랜잭션을 정리한 뒤 재시도) 되돌리기
-- 어려운 경로에서 「정지」보다 싼 실패 모양이 된다. `SET LOCAL` 이라 이 트랜잭션에만 걸린다.
SET LOCAL lock_timeout = '5s';

ALTER TABLE raw_observation
    ADD COLUMN pii_masked_at TIMESTAMPTZ;

-- 투영 열은 **객체**여야 한다(vr F-9) — 객체가 아닌 한 행이 섞이면 마스킹 후보 조회의
-- `jsonb_object_keys` 가 그 run 전체를 매 run 실패시킨다(같은 행에서). 조회 쪽에서 피하면 그 행의
-- 남김 집합이 조용히 비어 원문이 `{}` 가 되므로, 들어올 수 없게 **구조로** 닫는다.
-- `NOT VALID` 는 **기존 행을 스캔하지 않는다**(메타데이터 변경, 짧은 잠금) — 검증은 V19 가
-- `VALIDATE CONSTRAINT`(`SHARE UPDATE EXCLUSIVE`, 읽기·쓰기를 막지 않는다)로 뒤이어 한다.
ALTER TABLE raw_observation
    ADD CONSTRAINT raw_observation_payload_fields_object
        CHECK (jsonb_typeof(payload_fields) = 'object') NOT VALID;

-- =============================================================================
-- reject_mutation_except_masking — V3 `reject_mutation` 의 좁은 개정판.
--
--   ① DELETE 는 조건 없이 거부한다(append-only 의 본체 — 운영자 결정 A-3 「파기하지 않는다」).
--   ② INSERT 는 **마스킹을 정하는 입력을 적재자에게 주지 않는다** — 둘을 본다.
--      ⓐ 표지가 NULL 이어야 한다(vr F-4). 앱 역할의 INSERT GRANT 는 표 단위라 새 열도 포함하고,
--         마스킹 조회는 `pii_masked_at IS NULL` 만 보므로 표지를 미리 찍은 행은 영원히 마스킹되지
--         않으면서 계수에도 없다 — 표지를 쓰는 주체를 마스킹 주체로 닫는다.
--      ⓑ **적재 시각은 DB 시계가 소유한다**(vr R2-F2(b)). `inserted_at` 이 `DEFAULT now()` 일
--         뿐이었으므로 앱 역할이 `2999-01-01` 로 적재하면 그 행은 `inserted_at < cutoff` 에 영원히
--         들지 않았다. 비소유자 적재에는 `now()` 를 **써 넣는다**(거부가 아니다: 트랜잭션 안에서
--         `now()` 는 트랜잭션 시작 시각이라 긴 트랜잭션의 정상 적재를 허용 오차로 거부할 수 없다).
--         출하 수집 경로는 그 열을 **쓰지 않으므로**(`Sql.INSERT_RAW_OBSERVATION` 의 열 여섯에
--         없다 — 실측) 비용이 0 이다. 소유자에게는 강제하지 않는다 — 묵은 행을 심는 fixture·복원이
--         그 자유에 기대고, 소유자 자격은 이 slice 의 비방어 경계다.
--   ③ UPDATE 의 실행 역할은 **표 소유자(의 권한을 쓸 수 있는 역할)** 여야 한다(vr F-5 · mig L-1).
--      역할 **이름**을 거부하는 deny-list 였다면 뒤의 마이그레이션이 다른 역할에 UPDATE 를 주는
--      순간 열렸다 — 카탈로그(`pg_class.relowner`)에서 끌어온 allow-list 는 미래의 어떤 역할도
--      열거 없이 막는다. 술어는 `'USAGE'` 다(mig L-3 — `'MEMBER'` 는 `NOINHERIT` 로 상속하지 않는
--      멤버까지 통과시켜 더 넓었다). 출하 경로(소유자 직접 접속)는 그대로 지난다.
--      GRANT 부재가 여전히 1차 방어이고 이것이 2차다.
--   ④ 마스킹 표지가 NULL → 값으로 **한 번만** 간다. 값 → 값(두 번째 마스킹)·값 → NULL
--      (되돌림)은 거부다 — 원문을 다시 써 넣는 경로가 그 둘이다.
--   ⑤ **마스킹 열 둘**(`payload`·`payload_fields`)과 표지 열을 뺀 행 전체가 **같아야** 한다 —
--      마스킹 UPDATE 에 다른 열 변경(`observed_at`·`source_endpoint`·`release_sha`…)을 끼워 넣지
--      못한다. 투영 열이 마스킹 열인 이유는 운영자 결정 2026-10-14(D-6B3-3 ①)다: 업체명은 계약에
--      등재되어 그 열에도 실리므로, 그 열이 불변이면 업체명을 **구조적으로 지울 수 없다**.
-- 하나라도 걸리면 V3 와 같은 ERRCODE(P0001)로 거부한다.
-- =============================================================================
CREATE FUNCTION reject_mutation_except_masking() RETURNS trigger AS $masking$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '%.% 는 append-only다 — DELETE를 허용하지 않는다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.pii_masked_at IS NOT NULL THEN
            RAISE EXCEPTION '%.% 의 마스킹 표지는 적재가 찍지 않는다', TG_TABLE_SCHEMA, TG_TABLE_NAME
                USING ERRCODE = 'P0001';
        END IF;
        IF NOT pg_has_role(current_user, (SELECT relowner FROM pg_class WHERE oid = TG_RELID), 'USAGE') THEN
            NEW.inserted_at := now();
        END IF;
        RETURN NEW;
    END IF;

    IF NOT pg_has_role(current_user, (SELECT relowner FROM pg_class WHERE oid = TG_RELID), 'USAGE') THEN
        RAISE EXCEPTION '%.% 의 마스킹은 표 소유자만 한다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    IF OLD.pii_masked_at IS NOT NULL OR NEW.pii_masked_at IS NULL THEN
        RAISE EXCEPTION '%.% 의 마스킹 표지는 NULL에서 값으로 한 번만 간다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    IF (to_jsonb(OLD) - 'payload' - 'payload_fields' - 'pii_masked_at')
        <> (to_jsonb(NEW) - 'payload' - 'payload_fields' - 'pii_masked_at') THEN
        RAISE EXCEPTION '%.% 의 마스킹 UPDATE는 마스킹 열 밖을 바꿀 수 없다', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$masking$ LANGUAGE plpgsql;

-- 트리거 이름은 그대로 둔다 — 이 표의 계약은 여전히 「추가 전용」이고, 바뀐 것은 그 계약의
-- 예외 하나다. 바뀐 사실은 이름이 아니라 **함수 등재**(`CleanMigrationTriggerTest` 의
-- 트리거 → 함수 쌍)와 거동 test 가 나른다. 이벤트에 INSERT 가 더해진 것도 같은 등재가 싣는다
-- (관문 ② — 적재마다 이 함수가 한 번 돈다. plpgsql 분기 셋이라 행당 비용은 마이크로초 단위이고,
-- 그 대가로 마스킹을 정하는 입력 둘(표지·적재 시각)이 적재자 손을 벗어난다).
DROP TRIGGER raw_observation_append_only ON raw_observation;

CREATE TRIGGER raw_observation_append_only
    BEFORE INSERT OR UPDATE OR DELETE ON raw_observation
    FOR EACH ROW EXECUTE FUNCTION reject_mutation_except_masking();

-- GRANT 는 바뀌지 않는다(운영자 결정 A-1) — 어느 표에도 DELETE·UPDATE 를 새로 주지 않는다.
-- 파기·마스킹은 풀을 지나지 않는 **소유자 비풀링 연결**이 한다(`PersistenceWiring.migrateAsOwner`
-- 선례). 이 마이그레이션에 GRANT 문이 하나도 없다는 것이 그 결정의 등재다.
