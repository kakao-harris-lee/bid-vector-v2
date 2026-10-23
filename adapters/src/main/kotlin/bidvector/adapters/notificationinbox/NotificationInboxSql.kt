package bidvector.adapters.notificationinbox

/**
 * 알림함 질의 넷(M7/7C③). **문자열 조립이 없다** — 운영자 입력은 전부 바인딩 매개변수이고,
 * 안읽음 필터조차 절을 붙이는 대신 boolean 바인딩 하나로 접는다(7C① 이 조건 유무로 절을
 * 조립한 것과 다른 선택이다: 여기서 켜고 끄는 축이 **하나**라 절 조립기를 둘 이유가 없다).
 */
internal object NotificationInboxSql {
    /**
     * **중복 억제의 전부가 이 한 문장이다**(NOTI-10). select-then-insert 로 나누면 두 연결이
     * 같은 키로 동시에 들어올 때 둘 다 「없다」를 보고 둘째가 PK 위반으로 떨어진다 — 거부가
     * 아니라 **갱신**이 계약이므로 한 문장이어야 한다.
     *
     * · `created_at` 은 INSERT 때 DB 가 찍고(DEFAULT) **충돌 경로에서 건드리지 않는다** —
     *   보존 cutoff 의 기준이 재기록으로 밀리지 않는다.
     * · `read_at` 은 **detail 이 바뀔 때만** 비운다(D-7C3-1 ④). 같은 사실의 재기록이 읽은
     *   항목을 안읽음으로 되돌리면 운영자가 같은 것을 반복해 읽는다. `IS DISTINCT FROM` 이라
     *   NULL ↔ 값 전이도 「바뀐 것」으로 센다(`<>` 는 NULL 이 끼면 NULL 이 되어 ELSE 로 샌다).
     * · `RETURNING (xmax = 0)` 으로 삽입/갱신을 가른다 — `opening_result` upsert 의 기존 관례다.
     */
    const val UPSERT_ITEM = """
        INSERT INTO notification_inbox
            (account_id, kind, notice_number, notice_round, detail_code, occurred_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (account_id, kind, notice_number, notice_round) DO UPDATE SET
            occurred_at = EXCLUDED.occurred_at,
            updated_at = EXCLUDED.updated_at,
            detail_code = EXCLUDED.detail_code,
            read_at = CASE
                WHEN notification_inbox.detail_code IS DISTINCT FROM EXCLUDED.detail_code THEN NULL
                ELSE notification_inbox.read_at
            END
        RETURNING (xmax = 0) AS inserted
    """

    /**
     * 목록 한 페이지. 셋을 한 문장에서 한다 — 항목, **묶음 계수**, 그리고 공고 표시 칸의 합침.
     *
     * **묶음 계수를 CTE 안에서 센다**(계정 필터까지만 적용된 집합 위에서). 안읽음 필터를 창
     * 함수와 **같은 층**에 두면 계수가 걸러진 부분집합을 세어 `itemCount` 가 「묶음 전체
     * 크기」가 아니게 된다 — 그 값은 묶음이 페이지 경계에 걸쳤는지 클라이언트가 아는 유일한
     * 단서라 전체 크기여야 한다.
     *
     * **LEFT JOIN 이다**(V23 주석 ③) — 공고 행이 파기됐거나 아직 수집되지 않았으면 표시 칸이
     * 비고 항목은 남는다. `n.notice_number` 를 함께 뽑아 「행이 없다」와 「행은 있고 제목 칸이
     * 비었다」를 판독이 가를 수 있게 한다(제목 하나로는 두 경우가 같은 NULL 이다).
     *
     * **정렬이 묶음을 연속시킨다** — 묶음의 최신 사실 시각 역순으로 묶음을 세우고, 묶음 안에서
     * 사실 시각 역순, 같으면 종류 이름으로 못박는다(마지막 축이 없으면 같은 시각의 두 항목
     * 순서가 실행마다 달라져 페이지가 흔들린다).
     */
    const val SELECT_PAGE = """
        WITH scoped AS (
            SELECT
                i.kind,
                i.notice_number,
                i.notice_round,
                i.detail_code,
                i.occurred_at,
                i.read_at,
                count(*) OVER grouped AS group_item_count,
                count(*) FILTER (WHERE i.read_at IS NULL) OVER grouped AS group_unread_count,
                max(i.occurred_at) OVER grouped AS group_recent
            FROM notification_inbox i
            WHERE i.account_id = ?
            WINDOW grouped AS (PARTITION BY i.notice_number, i.notice_round)
        )
        SELECT
            s.kind,
            s.notice_number,
            s.notice_round,
            s.detail_code,
            s.occurred_at,
            s.read_at,
            s.group_item_count,
            s.group_unread_count,
            n.notice_number AS joined_notice_number,
            n.notice_title,
            n.status AS notice_status,
            n.deadline_at
        FROM scoped s
        LEFT JOIN notice n
            ON n.notice_number = s.notice_number AND n.notice_round = s.notice_round
        WHERE (? = FALSE OR s.read_at IS NULL)
        ORDER BY s.group_recent DESC, s.notice_number, s.notice_round, s.occurred_at DESC, s.kind
        LIMIT ? OFFSET ?
    """

    /**
     * 계수 둘 — 전수와 안읽음. **안읽음 계수는 필터와 무관하게 계정 전수**다(화면의 배지가
     * 읽는 값이라 목록 필터에 흔들리면 안 된다). `unreadOnly` 일 때의 전체 건수는 안읽음
     * 계수와 같으므로 질의에 매개변수를 더하지 않고 호출자가 고른다.
     */
    const val SELECT_COUNTS = """
        SELECT count(*) AS total_all,
               count(*) FILTER (WHERE read_at IS NULL) AS unread
        FROM notification_inbox
        WHERE account_id = ?
    """

    /**
     * 읽음 처리(단건). `read_at IS NULL` 이 술어에 있으므로 **이미 읽은 항목은 영향 행 0** 이고
     * 처음 읽은 시각이 보존된다. 계정 축이 술어의 첫 조건이라 남의 항목은 어떤 키를 보내도
     * 영향 행 0 이다 — 예외가 아니라 **계수가** 답한다.
     */
    const val MARK_ITEM_READ = """
        UPDATE notification_inbox SET read_at = ?
        WHERE account_id = ?
          AND kind = ?
          AND notice_number = ?
          AND notice_round = ?
          AND read_at IS NULL
    """

    /** 읽음 처리(한 공고의 묶음 전체). 종류 축만 빠진 같은 술어다. */
    const val MARK_NOTICE_READ = """
        UPDATE notification_inbox SET read_at = ?
        WHERE account_id = ?
          AND notice_number = ?
          AND notice_round = ?
          AND read_at IS NULL
    """
}
