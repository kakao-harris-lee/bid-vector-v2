-- M6/6B-1 ③ — 편집 세션 영속(M4/4B 알려진 제한 ② 인계: "세션 영속 실 구현 부재 +
-- sessionVersion 낙관적 동시성 미검증 — port+fake 까지가 그 slice 경계"). 이 마이그레이션이
-- 그 자리를 세운다.
--
-- **session_version 은 애플리케이션이 싣고 DB 가 전제조건으로만 검사한다(D-6B1-3, 우회
-- (5))** — V2 의 notice.revision(BEFORE UPDATE 트리거 notice_revision_bump 가 값을 정한다,
-- V3)과 다른 축이다. 낙관적 동시성은 "내가 읽은 값이 아직 그 값인가"를 묻는 것이고
-- 트리거가 값을 정하면 그 질문이 사라진다 — 그래서 이 표에는 session_version 에 손대는
-- DEFAULT·트리거를 두지 않는다. 저장 경로(JdbcEditSessionRepository)의
-- INSERT ... ON CONFLICT ... WHERE 문 하나가 그 전제조건을 진다.
--
-- **state 는 EditSessionState 다섯 값의 닫힌 어휘를 CHECK 로 고정한다**
-- (workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionTypes.kt) — 재선언이
-- 아니라 그 표를 저장 층에 실어 나르는 것이다(V6 outbox.state 관례). state_payload 와의
-- 짝(존재 가드) 아래 CHECK 가 "EXPIRED 만 부가 데이터가 없다"를 강제한다.
--
-- **state_payload·last_command 는 각각 JSON 텍스트 하나** — notice 처럼 상태별 필드를
-- 전부 펴지 않는다(scope.md ③ "최소 열"). 원문 그대로 담는다(outbox.payload 관례, V6) —
-- adapters 가 도메인 값(EditSessionState·EditCommand)에서 원시 필드를 뽑아 JSON 으로 쓰고,
-- 복원은 workflow 안 internal restoreEditSession 하나만 한다(D-6B1-6·D-6B1-7,
-- EditSessionSnapshot.kt) — 어댑터는 EditSession 을 만들지 않는다.
--
-- **인덱스는 PK(id) 하나뿐이다** — 소비 질의가 id 조회 하나(EditSessionRepository.load)라
-- 그 밖의 인덱스를 추가하지 않는다(D-6B1-5, 근거 없는 인덱스는 쓰기 비용만 늘린다).
CREATE TABLE edit_session (
    id TEXT PRIMARY KEY CHECK (id <> ''),
    operator_id TEXT NOT NULL CHECK (operator_id <> ''),
    state TEXT NOT NULL CHECK (state IN ('WAITING_FOR_VALUE', 'WAITING_FOR_CONFIRMATION', 'APPLIED', 'CANCELLED', 'EXPIRED')),
    -- 존재 가드 — EXPIRED 만 부가 데이터가 없다(EditSessionState.Expired 는 payload 가 없는
    -- data object). 나머지 넷(WaitingForValue·WaitingForConfirmation·Applied·Cancelled)은
    -- 전부 최소 한 값(field·draft·revision·reason)을 나른다.
    state_payload TEXT,
    CHECK ((state = 'EXPIRED') = (state_payload IS NULL)),
    expires_at TIMESTAMPTZ NOT NULL,
    session_version INTEGER NOT NULL CHECK (session_version >= 0),
    last_command TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GRANT — 세션은 조회(SELECT)·최초 생성(INSERT)·전이 저장(UPDATE, 실제로는 저장 경로가
-- INSERT ... ON CONFLICT ... DO UPDATE 한 문이라 INSERT 권한이 UPDATE 분기도 함께 쓴다)
-- 이 필요하다. DELETE·TRUNCATE 는 주지 않는다(보존·파기는 6B-3 소관, D-6B1-1).
GRANT SELECT, INSERT, UPDATE ON edit_session TO bidvector_app;
