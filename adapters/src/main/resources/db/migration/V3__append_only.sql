-- M3/3D ② — raw·audit는 갱신·삭제 경로가 없다(두 겹 방어: 권한 GRANT를 주지 않는 것과
-- 별개로, 트리거가 UPDATE/DELETE 시도 자체를 거부한다 — 우회 (9) TRUNCATE/COPY도 막는다).

CREATE FUNCTION reject_mutation() RETURNS trigger AS $reject$
BEGIN
    RAISE EXCEPTION '%.% 는 append-only다 — UPDATE/DELETE를 허용하지 않는다', TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'P0001';
END;
$reject$ LANGUAGE plpgsql;

CREATE TRIGGER raw_observation_append_only
    BEFORE UPDATE OR DELETE ON raw_observation
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER notice_audit_append_only
    BEFORE UPDATE OR DELETE ON notice_audit
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- 애플리케이션 역할은 UPDATE/DELETE 권한 자체가 없다(1차 방어 — 권한). TRUNCATE 권한도
-- 주지 않는다(우회 (9)) — GRANT 목록(V2)에 TRUNCATE가 없으므로 REVOKE는 불필요하지만,
-- PostgreSQL은 테이블 소유자·superuser가 아니면 기본으로 TRUNCATE 권한이 없다는 것을
-- 명시적으로 남긴다(문서화 목적, 실제 강제는 기본값 자체가 이미 한다).
REVOKE TRUNCATE ON raw_observation, notice_audit FROM PUBLIC;

-- provenance_authority는 애플리케이션 역할에 SELECT만 있다(V2) — 여기서 다시 한번 UPDATE·
-- INSERT·DELETE 권한이 없음을 확정한다(우회 (8), 기본은 미부여이므로 REVOKE는 방어 심층
-- 문서화다).
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON provenance_authority FROM bidvector_app;
