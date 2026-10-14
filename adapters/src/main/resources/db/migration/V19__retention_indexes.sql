-- M6/6B-3 — 보존 인덱스와 투영 열 형태 제약의 검증(mig H-1 · M-2 · vr F-9).
--
-- **왜 V18 과 갈라져 있나.** V18 의 첫 문장이 `ALTER TABLE … ADD COLUMN` 이고 그것이 쥐는
-- `ACCESS EXCLUSIVE` 는 트랜잭션 끝까지 유지된다. 인덱스 빌드가 그 트랜잭션 안에 있으면 빌드 내내
-- **SELECT 까지** 막히고, migrate 가 `DataSource` 빈 생성의 부수효과이므로 그 창은 곧 기동 블록이다
-- (`raw_observation` 은 이 체계에서 가장 크고 영원히 자라는 표다 — A-3 이 파기 대상에서 뺐으므로
-- 90일 상한이 없다). 갈라 두면 V18 은 메타데이터 변경만으로 밀리초에 끝나고, 이 파일이 쥐는 것은
-- 인덱스 빌드의 `SHARE`(쓰기만 차단 · **읽기는 지난다**)와 검증의 `SHARE UPDATE EXCLUSIVE`
-- (읽기·쓰기 모두 지난다)뿐이다.
--
-- **`CONCURRENTLY` 를 쓰지 않는 이유는 실측이다.** `CREATE INDEX CONCURRENTLY` + 형제
-- `.sql.conf`(`executeInTransaction=false`) 조합은 **영구히 대기한다**: Flyway 는 메타데이터
-- 연결에서 트랜잭션을 열어 둔 채(`flyway.postgresql.transactional.lock` 기본값) 마이그레이션을
-- 다른 연결로 돌리고, `CONCURRENTLY` 는 동시 트랜잭션 전부가 끝나기를 기다린다 — 그 대기가
-- 서로를 푸는 일이 없다(Testcontainers 실측: `pg_stat_activity` 에 `idle in transaction` 인 Flyway
-- 메타데이터 연결과 `Lock/virtualxid` 로 막힌 이 마이그레이션이 14분간 그대로 있었다). 그 설정을
-- 끄면 풀리지만 **Flyway 를 부르는 자리 넷이 전부** 그것을 알아야 하고, 하나라도 잊으면 거기서
-- **실패가 아니라 정지**한다 — 적용 창을 줄이려고 더 나쁜 실패 모양을 사는 거래다. 남은 창
-- (쓰기 차단)은 runbook §1.3 이 들고, 적용 전 측정 절차를 함께 둔다.
--
-- 이 파일은 **한 트랜잭션**이다(Flyway 기본). 실패하면 통째로 롤백되어 invalid 인덱스나 반쯤 적용된
-- 상태가 남지 않는다 — `CONCURRENTLY` 가 남기는 잔해의 반대쪽 이득이다.

-- **첫 문장은 잠금 대기의 상한이다**(cr M-1 · mig L-1) — V18 과 **같은 사유**다. 인덱스 빌드는
-- `SHARE` 를 요구하고 그것은 **진행 중인 쓰기**(`ROW EXCLUSIVE`)와 부딪힌다. 기본 `lock_timeout` 은
-- 0 이라 그 대기는 홀더가 끝날 때까지 무한이고, migrate 가 기동 부수효과이므로 곧 **기동 정지**다.
-- 상한을 두면 같은 상황이 **실패**가 되고(기동 실패 → 쓰기를 멈춘 정비 창에서 재시도) 「정지」보다
-- 싼 실패 모양이 된다.
--
-- **이 상한은 긴 인덱스 빌드를 깨지 않는다**: `lock_timeout` 은 **잠금 대기**에만 걸리고 문장 길이에는
-- 걸리지 않는다(독립 레인 실측 — 충돌이 없을 때 50ms 상한으로도 202ms 짜리 빌드가 정상 완료했다).
SET LOCAL lock_timeout = '5s';

-- =============================================================================
-- ① 마스킹 후보 조회(`maskSelectSqlFor`) — 아직 마스킹되지 않은 행만 색인한다. 부분 인덱스라
--    마스킹이 진행되면 작아진다(적용 순간에는 전수가 대상이다).
-- =============================================================================
CREATE INDEX raw_observation_pii_unmasked_idx
    ON raw_observation (inserted_at) WHERE pii_masked_at IS NULL;

-- =============================================================================
-- ② 파기 cutoff 지원(mig M-2) — `purgeSqlFor` 는 `… WHERE <cutoff 열> < ? ORDER BY <cutoff 열>
--    LIMIT ?` 다. 인덱스가 없으면 일괄마다 전수 스캔 + 정렬이고 그 스캔이 N/B 번 반복된다.
--    `api_request_audit` 는 `idx_api_request_audit_occurred_at`(V15)이 이미 진다.
--    `outbox` 는 **(상태, 적재 시각)** 복합이다 — 부분 술어(`WHERE state = 'DELIVERED'`)로 두면
--    종단 집합이 바뀌는 날 인덱스가 조용히 쓰이지 않는다(그 집합은 레지스트리가 정한다).
--    **이 인덱스는 다른 slice 의 OPEN 에도 닿는다**(PR #73 low): `OPEN-6F10-CLAIM-INDEX` 는
--    `claimedEntries`(`state = 'CLAIMED'`)가 어느 인덱스도 덮지 않아 순차 스캔이라는 등재였고,
--    선행 열이 `state` 라 그 조회가 이제 이것을 쓸 수 있다. **그 OPEN 을 닫지는 않는다** — 닫을
--    근거는 그 레인의 측정이고, 여기서는 「전제가 바뀌었다」만 남긴다.
--    `notice_requirement_row` 는 파기 대상이 아니지만 **CASCADE 기한 가드**(D-6B3-3 ②)의
--    `NOT EXISTS … AND created_at >= cutoff` 부분질의가 그 열을 읽는다. 선행 열을 조인 키로 둔 것은
--    **정상 운영 규모**를 택한 선택이다(mig M-2 실측, 149만 행):
--      · 만료 부모가 적을 때(cutoff 179일 · 부모 444) 계획기는 **중첩 루프 안티 조인**을 고르고 이
--        복합 인덱스가 **index-only scan** 을 준다(4.5~7.0ms · 자식 PK 경유 4.1~5.6ms 와 비슷).
--      · 만료 부모가 많을 때(cutoff 90일 · 부모 ≈ 4만 — **첫 운영 실행**) 계획기는 **해시 안티 조인**을
--        고르고 그때 필요한 것은 「`created_at >= cutoff` 인 자식 전부」라 **`created_at` 선행 인덱스가
--        더 맞는 도구**다 — 이 복합은 자식 전수 스캔으로 내려간다. 두 레인의 재현을 합치면
--        **복합 93~143ms 대 `created_at` 단독 66~82ms**(이 레인 97~126 대 75~82 · 독립 레인
--        93~143 대 66~72 — 재현 폭이 이만큼 넓다).
--    즉 **첫 실행에서는 1.2~2.2배 느린 쪽**이고 그 뒤 매일의 run 에서는 비슷하거나 낫다. 일괄당 수십
--    ms 차이라 거동은 어느 쪽이든 옳다 — 앞 판 주석이 적었던 「`created_at` 단독은 계획기가 고르지
--    않는다」는 **측정으로 거짓**이었고(실제로 쓰인다) 그 문장을 지운다.
-- =============================================================================
CREATE INDEX notice_retention_cutoff_idx ON notice (created_at);
CREATE INDEX opening_result_retention_cutoff_idx ON opening_result (created_at);
CREATE INDEX opening_reserve_price_retention_cutoff_idx ON opening_reserve_price (created_at);
CREATE INDEX qualification_text_retention_cutoff_idx ON qualification_text (created_at);
CREATE INDEX notice_requirement_retention_cutoff_idx ON notice_requirement (created_at);
CREATE INDEX notice_requirement_row_retention_cutoff_idx
    ON notice_requirement_row (notice_number, notice_round, created_at);
CREATE INDEX rejected_write_retention_cutoff_idx ON rejected_write (recorded_at);
CREATE INDEX edit_session_retention_cutoff_idx ON edit_session (created_at);
CREATE INDEX outbox_retention_cutoff_idx ON outbox (state, inserted_at);

-- =============================================================================
-- ③ V18 이 `NOT VALID` 로 세운 투영 열 형태 제약을 **검증한다**(vr F-9). 쓰기 경로
--    (`ObservationPayloadCodec.encode`)는 늘 평평한 객체를 쓰므로 기존 행은 통과한다. 실패하면
--    **그 전에 이미** 객체가 아닌 행이 있었다는 뜻이고, 그때는 재시도가 아니라 그 행을 조사하는
--    것이 답이다(runbook §1.3).
-- =============================================================================
ALTER TABLE raw_observation VALIDATE CONSTRAINT raw_observation_payload_fields_object;
