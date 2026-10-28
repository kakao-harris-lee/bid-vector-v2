-- M7/7C③ — 알림함 표(NOTI-10). **기존 `inbox`(V6)를 재사용하지 않는다**: 그것은 이벤트 소비
-- 멱등 표이고(ADR 0005 D-3) 키가 `idempotency_key` 하나이며 계정 축이 없다. 두 관심사를 한 표에
-- 얹으면 보존 분류가 갈린다(`inbox` 는 멱등 원장이라 파기 면제, 알림함은 90일 파기 대상).
--
-- 번호는 D-6A1-12 의 정의를 따른다(「PR 시점 main 최대 번호보다 크고, 병행 레인이 선점 통보한
-- 번호를 피한다」) — 착수 시점 main 최대는 V20 이고 병행 레인이 V21(7B)·V22(7C④)를 선점했다.
-- PR 직전에 그 정의대로 재확인한다.
--
-- =============================================================================
-- 이 표의 설계 사실 셋 (운영자 결정 D-7C3-1)
--
-- ① **중복 억제 키가 PRIMARY KEY 자체다.** 파생 문자열 열(`dedupe_key`)을 두지 않는다 — 두면
--    그 값과 그것을 만든 입력이 갈릴 자리가 생기고, 갈려도 DB 는 모른다. 「한 계정 · 한 공고 ·
--    한 종류에 한 항목」이 네 열의 PK 로 표현되므로 **PK 밖의 UNIQUE 가 없다**(스키마 스냅샷
--    래칫의 UNIQUE 축을 건드리지 않는다). 시퀀스도 만들지 않는다 — IDENTITY 를 쓰지 않으므로
--    V6 이 적은 「`GRANT … ALL SEQUENCES` 는 그 시점 스냅샷」 함정이 여기 없다.
--
-- ② **표시 문구를 저장하지 않는다**(D-7C3-1 ⑦). 공고명·기관·마감은 읽을 때 `notice` 에서
--    합친다. 그래서 두 성질이 규율이 아니라 구조다:
--      · 「제목 문구가 바뀌어도 중복 억제 키가 흔들리지 않는다」 — 흔들릴 값이 이 표에 없다.
--      · 「항목에 PII 가 담기지 않는다」 — 담을 칸이 없다. 이 표의 **열 전수**가 닫힌 어휘
--        CHECK 이거나 공고 키이거나 그 test 에 **(이름, 자료형) 쌍으로 등재된** 열이고,
--        그 성질을 카탈로그에서 재는 test 가 있다(`NotificationInboxPrivacyTest`).
--        **자료형과 무관하다** — 열을 더하면 자료형이 무엇이든 그 등식이 깨진다.
--
--        앞 판은 「**TEXT** 열 전수」라고 적었다. TEXT 축에서는 참이지만 그렇게 적으면
--        사업자번호·연락처가 **정수 열로** 들어오는 길이 문면에 남는다 — r3 검토가 실제로
--        뚫은 경로이고(`BIGINT`·`NUMERIC` 열이 게이트를 지났다), 그때 test 쪽 문면만 고쳐
--        이 주석이 게이트보다 **좁은** 주장을 하고 있었다.
--
-- ③ **`notice` 에 FK 를 걸지 않는다.** `notice` 는 90일 **파기 대상**이다(6B-3 보존 분류).
--      · CASCADE 없이 FK 를 걸면 공고 파기가 FK 위반으로 **멈춘다**(파기 run 전체가 막힌다).
--      · CASCADE 로 걸면 운영자의 알림함이 90일째에 **조용히 비워진다**.
--    알림함은 「그 사실이 있었다」의 기록이라 공고 행의 수명보다 오래 살아야 한다. 공고 키 두
--    열은 **주제 참조**일 뿐이고, 읽기 경로는 `notice` 를 LEFT JOIN 한다 — 공고 행이 없으면
--    표시 칸이 비고 항목은 종류와 공고 번호로 여전히 읽힌다. **이것은 빠뜨린 FK 가 아니다.**
-- =============================================================================

-- 잠금 대기의 상한(V18·V19 가 세운 관례) — 이 파일이 쥐는 것은 새 표 생성과 `account` 에 대한
-- FK 검증 잠금뿐이라 창이 짧지만, 기본 `lock_timeout` 은 0 이고 migrate 가 기동의 부수효과라
-- 그 대기는 곧 **기동 정지**다. 상한을 두면 같은 상황이 「정지」가 아니라 「실패」가 된다.
SET LOCAL lock_timeout = '5s';

CREATE TABLE notification_inbox (
    -- R-2 — 사용자 소유 표에는 계정 축이 처음부터 있다. 다수 계정을 전제로 두고(단일 행 CHECK
    -- 를 두지 않는다 — V9·V12 가 가진 그 자리를 이 표는 만들지 않는다) 행은 오늘 하나의 계정에만
    -- 생긴다. CASCADE 를 걸지 않는다: 계정을 지우는 경로가 아직 없고, 생기면 알림함 처분은 그
    -- 경로의 결정이다(V20 의 `browser_session` 과 같은 판단).
    account_id BIGINT NOT NULL REFERENCES account (id),

    -- 종류 어휘 여덟 — Kotlin `NotificationKind` 와 **같아야 한다**. 그 등식은
    -- `NotificationInboxPrivacyTest` 가 이 CHECK 의 정의에서 어휘를 읽어 잰다(`outbox` 의 상태
    -- 다섯을 재는 기존 선례와 같은 술어). 한쪽만 늘리면 빌드가 붉다.
    --
    -- 오늘 **부를 원천이 없는 값도 둔다**(D-7C3-1 ③) — 나중에 CHECK 어휘를 늘리는 둘째
    -- 마이그레이션을 피한다(7B 가 전이 어휘에 쓴 것과 같은 선례).
    kind TEXT NOT NULL CHECK (
        kind IN (
            'NEW_CANDIDATE',
            'BID_DEADLINE_IMMINENT',
            'BID_DEADLINE_TODAY',
            'DOCUMENT_DEADLINE_IMMINENT',
            'OPENING_COMPLETED',
            'AWARD_DECIDED',
            'NOTICE_REVISED',
            'BID_PRICE_REQUEST_RECORDED'
        )
    ),

    -- 공고 키 쌍 — 형식만 V1 `notice` 와 같은 CHECK 로 재선언한다(정규화 규칙은 재선언하지
    -- 않는다, V1 의 관례). FK 는 없다(위 ③).
    notice_number TEXT NOT NULL CHECK (notice_number <> ''),
    notice_round TEXT NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),

    -- detail 어휘 넷 또는 NULL. 자유 문자열이 아니므로 이 축으로 PII 가 들어올 수 없다.
    detail_code TEXT CHECK (
        detail_code IN ('PROVISIONAL_FIRST', 'NOT_PROVISIONAL_FIRST', 'AWARDED', 'NOT_AWARDED')
    ),

    -- 시각 셋은 **서로 다른 축**이다(하나로 접으면 각자의 일을 잃는다).
    --  · occurred_at — 사실의 시각. 호출자의 시계가 정하고 목록 정렬이 읽는다.
    --  · created_at  — 최초 적재. **DB 가 찍고**(A-2 의 적재 시각 축) 보존 cutoff 가 읽는다.
    --                  앱 역할은 이 열을 UPDATE 할 수 없다(아래 GRANT) — 적재 시각을 밀어 파기를
    --                  피하는 길을 권한 층에서 막는다.
    --  · updated_at  — 재기록 증거. **DB 가 찍는다**(upsert 문장의 `now()`). 호출자 시계를
    --                  싣지 않는 이유: 같은 사실을 같은 `occurred_at` 으로 다시 기록하면 그 값이
    --                  움직이지 않아 이 열의 일이 성립하지 않는다. `created_at` 과 같은 축이라
    --                  둘이 서로를 반박하지도 않는다. 보존 cutoff 는 이 열을 읽지 않는다.
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL,

    -- NULL = 안읽음. 지우지 않고 **시각으로** 표시한다 — 「언제 읽었는가」가 남는다.
    read_at TIMESTAMPTZ,

    PRIMARY KEY (account_id, kind, notice_number, notice_round),

    -- 종류 ↔ detail 짝(심층 방어). Kotlin 쪽은 `NotificationFact` 의 sealed 구성이라 불법 쌍이
    -- **표현되지 않지만**, 어댑터의 codec 이 어긋나게 쓰면 그것을 잡는 것은 이 CHECK 다.
    -- 세 갈래가 종류 전수를 **덮는다**: detail 을 갖는 둘, 그리고 나머지 전부는 NULL 이어야 한다.
    --
    -- **각 갈래에 `IS NOT NULL` 이 있어야 한다** — 없으면 세 값 논리가 이 제약을 열어 준다
    -- (앞 판에서 실측으로 잡힌 결함): `kind = 'OPENING_COMPLETED'` 인데 `detail_code` 가 NULL 이면
    -- `detail_code IN (…)` 이 **NULL** 이 되어 첫 갈래가 `TRUE AND NULL` = NULL 이고 나머지 둘은
    -- FALSE 이므로 전체가 `NULL OR FALSE OR FALSE` = **NULL** 이다. PostgreSQL 은 NULL 인 CHECK 를
    -- **만족으로 보므로**, 「detail 을 싣는 종류인데 detail 이 없는」 행이 조용히 들어갔다.
    -- `detail_code IS NOT NULL` 을 더하면 그 갈래가 `TRUE AND FALSE` 로 떨어져 닫힌다.
    CHECK (
        (
            kind = 'OPENING_COMPLETED'
            AND detail_code IS NOT NULL
            AND detail_code IN ('PROVISIONAL_FIRST', 'NOT_PROVISIONAL_FIRST')
        )
        OR (
            kind = 'AWARD_DECIDED'
            AND detail_code IS NOT NULL
            AND detail_code IN ('AWARDED', 'NOT_AWARDED')
        )
        OR (kind NOT IN ('OPENING_COMPLETED', 'AWARD_DECIDED') AND detail_code IS NULL)
    )

    -- **`read_at >= created_at` 제약을 두지 않는다**(실측으로 걷어낸 자리).
    --
    -- 앞 판에는 있었다. 그런데 그 둘은 **다른 시계**의 값이다 — `created_at` 은 DB 의 `now()` 이고
    -- `read_at` 은 애플리케이션 시계가 싣는다. 한 CHECK 로 두 시계를 비교하면 **시계 틀어짐이
    -- 곧 쓰기 실패**가 된다: 앱 컨테이너가 DB 보다 1초만 뒤처져도 정상적인 읽음 처리가 DB 오류로
    -- 떨어진다. 사소한 기능에 생기는 장애 경로치고 비싸다.
    --
    -- 그 대신 막아 주던 것은 거의 없다 — `read_at` 을 싣는 것은 애플리케이션이고, 그 애플리케이션은
    -- `occurred_at` 도 싣는다. 시계를 쥔 쪽이 둘 다 쥐므로 「읽음 시각 위조」만 따로 막지 못한다.
    -- 적재 시각을 미는 길은 **열 단위 GRANT** 가 이미 막는다(앱 역할에 `created_at` UPDATE 없음).
    --
    -- **발견 경위**: 고정 상수(2026-10-23)를 `read_at` 으로 쓰는 test 가 **그날은 통과했고 사흘 뒤
    -- 붉어졌다** — `created_at` 이 실제 `now()` 라 상수가 과거가 됐기 때문이다. 「그날만 통과하는
    -- test」였고, 그 뿌리가 이 교차 시계 제약이었다.
);

-- 목록 질의 — 계정 안에서 사실의 시각 역순. 선행 열이 `account_id` 라 계정 경계가 인덱스
-- 층에서도 첫 축이다.
CREATE INDEX notification_inbox_account_recent_idx
    ON notification_inbox (account_id, occurred_at DESC);

-- 보존 cutoff 지원(V19 가 파기 대상 표마다 둔 것과 같은 축) — `purgeSqlFor` 가
-- `… WHERE created_at < ? ORDER BY created_at LIMIT ?` 로 돈다.
CREATE INDEX notification_inbox_retention_cutoff_idx ON notification_inbox (created_at);

-- =============================================================================
-- GRANT — **열 단위**다. 요청 경로(앱 역할)가 할 수 있는 일을 최소로 줄인다.
--
--  · UPDATE 는 네 열에만 있다. 앱 역할은 **소유 계정 · 종류 · 공고 키 · 적재 시각을 바꿀 수
--    없다** — 즉 남의 항목으로 옮기거나, 종류를 바꿔 중복 억제를 우회하거나, 적재 시각을 밀어
--    파기를 피할 수 없다. upsert 의 `DO UPDATE SET` 과 읽음 처리가 필요한 열은 이 넷뿐이다.
--  · **INSERT 도 열 단위다**(r1 검토 verifier F5). 표 단위로 주면 적재 시각을 *UPDATE* 로는
--    못 밀어도 **INSERT 때 먼 미래로 지정**할 수 있고, 그 행은 보존 cutoff 를 영영 지나지
--    않는다(이후 upsert 는 `created_at` 을 건드리지 않는다) — 즉 「적재 시각을 밀어 파기를
--    피하는 길」이 UPDATE 쪽만 막혀 있었다. 여기 적은 일곱은 **어댑터의 upsert 가 실제로
--    넣는 열 전부**이고, `created_at`(기본값 `now()`)과 `read_at`(읽음 처리의 UPDATE 로만
--    움직인다)은 코드가 INSERT 에 쓰지 않으므로 빠진다. 열을 빼도 기본값은 서버가 넣는다.
--    V20 `browser_session` 은 표 단위 INSERT 인데 이 표가 다르게 가는 이유는 **보존 기준 열이
--    INSERT 로 지정 가능한가**가 여기서는 파기 회피 경로이기 때문이다(그 표의 보존 기준은
--    세션 만료라 같은 성질이 아니다).
--  · DELETE·TRUNCATE 는 주지 않는다 — 파기는 소유자 연결의 일이다(V8·V12·V15·V20 관례).
--    앱 프로세스가 장악돼도 **알림을 지울 수 없다**.
-- =============================================================================
GRANT SELECT ON notification_inbox TO bidvector_app;
GRANT INSERT (account_id, kind, notice_number, notice_round, detail_code, occurred_at, updated_at)
    ON notification_inbox TO bidvector_app;
GRANT UPDATE (detail_code, occurred_at, updated_at, read_at) ON notification_inbox TO bidvector_app;
