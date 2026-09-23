-- M6/6A-1 D-6A1-7 — 요청 감사 표. 전 요청(성공·실패·인증 실패 포함)에 대해 한 행을
-- 남긴다(RequestAuditFilter가 유일한 쓰기 경로, append-only). 요청 본문·자격증명 값은
-- 담지 않는다 — 보존·파기 정책은 6B-3 소관이라 이 slice가 개인정보를 지어 쌓지 않는다.
--
-- 번호는 D-6A1-12의 정의(「PR 시점 main 최대 번호보다 크고, 병행 레인이 선점 통보한
-- 번호를 피한다」)를 따른다 — 착수 시점 main 최대는 V13, 6F-4가 V14를 선점했다. PR 직전에
-- 그 정의대로 재확인한다.

CREATE TABLE api_request_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    occurred_at TIMESTAMPTZ NOT NULL,

    -- 단일 운영자 소비자(운영자 결정 2026-09-16 ②) — 다중 사용자·RBAC은 OPEN-6A-RBAC.
    -- 인증 실패 요청도 남아야 하므로(우회 (2)) NOT NULL 고정 라벨로 둔다(자격증명 값이
    -- 아니라 인증 판정 결과의 라벨 — "operator"/"unauthenticated").
    subject TEXT NOT NULL,

    method TEXT NOT NULL,
    path TEXT NOT NULL,
    status_code INT NOT NULL CHECK (status_code BETWEEN 100 AND 599),
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    correlation_id TEXT NOT NULL
);

CREATE INDEX idx_api_request_audit_occurred_at ON api_request_audit (occurred_at);

-- GRANT — append-only(outbox·operator_strategy_revision과 같은 관례, V6·V9). bidvector_app은
-- 이 표에 UPDATE·DELETE 권한이 없다 — 보존·파기(6B-3)가 실제 삭제 경로를 열기 전까지는
-- 관리자 권한으로만 지운다.
GRANT SELECT, INSERT ON api_request_audit TO bidvector_app;
