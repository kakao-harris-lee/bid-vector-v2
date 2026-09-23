-- M6/6A-3+6F-3 D-6A3-4 — 활성 투찰 여력 상한. 두 표(operator_strategy 싱글턴 현재 값,
-- operator_strategy_revision 개정 이력)가 이미 감시·임계·상한 열 형태를 공유한다(V9)
-- — 그 형태에 열 하나를 더한다(추가만, 6F-1 관례).
--
-- nullable, DEFAULT 없음(D-6F1-5 「값 지어내기 없음」과 같은 규율) — 기존 행은 NULL로
-- 보존된다(미설정). CHECK는 양수만 허용(candidate_limit과 같은 형태). 상한 미설정은
-- fail-closed 로 소비된다(app 조립 축, D-6A3-4) — 이 표 자체는 그 정책을 모른다.
--
-- 되돌림 두 갈래(rollback.md) — 미적용 DB: 이 파일 삭제. 적용된 DB: 새 V 파일의
-- `ALTER TABLE ... DROP COLUMN`(파일 삭제로 되돌리지 않는다, 6F-4 규율).

ALTER TABLE operator_strategy
    ADD COLUMN max_active_bids INT CHECK (max_active_bids IS NULL OR max_active_bids > 0);

ALTER TABLE operator_strategy_revision
    ADD COLUMN max_active_bids INT CHECK (max_active_bids IS NULL OR max_active_bids > 0);
