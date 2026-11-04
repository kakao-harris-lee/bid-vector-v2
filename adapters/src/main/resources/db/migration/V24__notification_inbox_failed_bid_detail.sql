-- M9/inbox-vocab — 유찰 어휘(D-M9-1 · D-M9-2). `notification_inbox` 의 **제약 둘만** 다시
-- 선언한다: detail 어휘와 「종류 ↔ detail 짝」이다.
--
-- 번호는 D-6A1-12 의 정의를 따른다(「PR 시점 main 최대 번호보다 크고, 병행 레인이 선점 통보한
-- 번호를 피한다」) — 착수 시점 main 최대는 V23 이고 선점 통보는 없다. PR 직전에 그 정의대로
-- 재확인한다.
--
-- =============================================================================
-- 왜 이 마이그레이션이 필요한가
--
-- 7C③ 의 detail 어휘는 **넷**이었고 낙찰 축에는 `AWARDED`·`NOT_AWARDED` 둘뿐이었다. 그래서
-- 유찰(아무도 못 땄다 · 재입찰)은 ⓐ `NOT_AWARDED` 로 접히거나 ⓑ 알림을 아예 남기지 못하거나
-- 였고, 7C③-b 는 ⓑ 를 골랐다(`OPEN-7C3B-FAILED-BID-DETAIL`). ⓐ 는 거부됐다 — 「내가 졌다」와
-- 「아무도 못 땄다」가 한 값이 되면 운영자의 다음 할 일이 가려진다.
--
-- 그 어휘를 넓히는 길이 CHECK 재선언이고, 그것이 이 파일이다. 호출 쪽 어휘(Kotlin
-- `NotificationDetail`·`AwardOutcome`)와 이 CHECK 가 **같은 집합**이라는 등식은
-- `NotificationInboxPrivacyTest` 가 제약 정의에서 어휘를 읽어 잰다 — 한쪽만 늘리면 빌드가 붉다.
--
-- **열을 더하지 않는다.** 결과 셋은 detail 어휘 하나로 표현되고, 열을 더하면 V23 이 세운 성질
-- 둘이 함께 흔들린다(① 「열 전수가 닫힌 어휘이거나 공고 키이거나 등재된 쌍」 ② 열 단위 GRANT).
-- PK·열·인덱스·GRANT 변경은 **0** 이다(계약 out_scope).
--
-- **제약 이름을 유지한다.** 앞 판은 둘 다 무명이라 PostgreSQL 이 지은 이름을 쓰고 있었다
-- (열 제약 → `<표>_<열>_check`, 표 제약 → `<표>_check`). 같은 이름으로 다시 세우므로 제약
-- **개수**를 세는 스냅샷 래칫(`CleanMigrationCheckTest` — 이 표는 5)이 그대로 성립하고, 이름이
-- 바뀌어 래칫이 붉어지는 일도 없다.
--
-- **`DROP CONSTRAINT` 에 `IF EXISTS` 를 붙이지 않는다.** 이름 추측이 틀렸다면 그 자리에서
-- **마이그레이션이 실패해야 한다** — 붙이면 옛 제약이 남은 채 새 제약이 더해지고, 그 상태는
-- 「detail 어휘를 가두는 정의가 둘」이라 어휘 등식 test 가 간접적으로만 잡는다. 실패가 낫다.
-- =============================================================================

-- 잠금 대기의 상한(V18·V19 가 세운 관례, V23 과 같은 사유) — 이 파일이 쥐는 것은 한 표의
-- ACCESS EXCLUSIVE 뿐이고 창이 짧지만, 기본 `lock_timeout` 은 0 이고 migrate 가 기동의
-- 부수효과라 그 대기는 곧 **기동 정지**다. 상한을 두면 「정지」가 아니라 「실패」가 된다.
SET LOCAL lock_timeout = '5s';

-- ① detail 어휘 — 넷에서 **다섯**으로. 자유 문자열이 아니므로 이 축으로 PII 가 들어올 수
--    없다는 V23 의 성질은 그대로다(값이 하나 늘어도 집합은 닫혀 있다).
ALTER TABLE notification_inbox DROP CONSTRAINT notification_inbox_detail_code_check;

ALTER TABLE notification_inbox
    ADD CONSTRAINT notification_inbox_detail_code_check CHECK (
        detail_code IN (
            'PROVISIONAL_FIRST',
            'NOT_PROVISIONAL_FIRST',
            'AWARDED',
            'NOT_AWARDED',
            'FAILED_OR_REBID'
        )
    );

-- ② 종류 ↔ detail 짝(심층 방어) — **새 값이 어느 종류에 붙는지**를 여기서 정한다. 어휘만
--    넓히고 이 제약을 두면 `AWARD_DECIDED` + `FAILED_OR_REBID` 행이 거부되고, 반대로 이 제약을
--    느슨하게 풀면 개찰 완료에 유찰이 실릴 수 있다. 짝의 전수 교차(종류 둘 × 어휘 다섯)를
--    `NotificationInboxPrivacyTest` 가 실 DB 에서 잰다.
--
--    V23 본문과 **같은 세 갈래**이고 바뀐 것은 낙찰 갈래의 어휘 하나다. 각 갈래의
--    `detail_code IS NOT NULL` 은 그대로 둔다 — 없으면 세 값 논리가 제약을 열어 준다(V23 주석의
--    실측: `TRUE AND NULL` = NULL 이고 PostgreSQL 은 NULL 인 CHECK 를 만족으로 본다).
ALTER TABLE notification_inbox DROP CONSTRAINT notification_inbox_check;

ALTER TABLE notification_inbox
    ADD CONSTRAINT notification_inbox_check CHECK (
        (
            kind = 'OPENING_COMPLETED'
            AND detail_code IS NOT NULL
            AND detail_code IN ('PROVISIONAL_FIRST', 'NOT_PROVISIONAL_FIRST')
        )
        OR (
            kind = 'AWARD_DECIDED'
            AND detail_code IS NOT NULL
            AND detail_code IN ('AWARDED', 'NOT_AWARDED', 'FAILED_OR_REBID')
        )
        OR (kind NOT IN ('OPENING_COMPLETED', 'AWARD_DECIDED') AND detail_code IS NULL)
    );
