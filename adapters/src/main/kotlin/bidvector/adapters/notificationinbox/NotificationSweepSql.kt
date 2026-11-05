package bidvector.adapters.notificationinbox

/**
 * 훑기 후보 질의(M7/7C③-b · D-7C3B-8) — **`BiddingSql` 에 두지 않는다.**
 *
 * 그 파일의 불변식은 「모든 문장에 `account_id = ?` 가 있다」이고 `BiddingSqlOwnershipTest` 가
 * 상수 전수로 그것을 잰다. 이 문장은 **전 계정**이 모집단이라 그 불변식을 깬다 — 같은 파일에
 * 두면 예외를 하나 뚫어야 하고, 그러면 그 뒤에 오는 요청 경로 문장도 그 구멍으로 들어온다.
 * 사유가 다른 문장은 파일을 달리해 각자의 KDoc 이 자기 사유를 지게 한다.
 *
 * **계정은 결과에 실려 나온다**(`c.account_id`). 이 경로에 계정을 지어낼 자리가 없고, 알림을
 * 받는 주체는 그 건을 담은 계정 그대로다.
 *
 * **상위집합을 돌려준다** — 창 판정(KST 달력일)은 순수 함수가 다시 한다. 여기서 하는 일은
 * 「전 계정의 전 건」을 훑지 않도록 시간 축으로 좁히는 것뿐이다.
 */
internal object NotificationSweepSql {
    /**
     * 유효 마감 — 공고 행이 있으면 **그 행의 값**, 없으면 담을 때의 사본(`effectiveDeadline` ·
     * `SELECT_SETTLE_CANDIDATES` 와 **같은 정의**다. 두 자리가 다른 답을 내면 마감 판정과 마감
     * 알림이 서로 다른 마감을 본다).
     */
    private const val EFFECTIVE_DEADLINE =
        "(CASE WHEN n.notice_number IS NULL THEN c.deadline_at ELSE n.deadline_at END)"

    /**
     * 후보 셋을 한 문장으로 — 마감 창 · 서류 기한 창 · 공고 개정이다.
     *
     * **투찰 종류를 거르지 않는다 — 실어 나른다**(M9/paper-bid · D-M9P-2). 앞 판은
     * `bid_kind = 'REAL'` 로 서면 건을 **버렸다**: 알림함의 중복 억제 키에 투찰 종류 축이 없어
     * 같은 공고의 두 건이 한 항목을 덮어썼고, 가상 연습이 실제 마감 알림을 가리는 것보다 서면
     * 운영자가 알림을 못 받는 쪽을 골랐기 때문이다. V25 가 그 축을 키에 넣어 **덮어쓸 자리가
     * 없어졌으므로** 거를 이유도 없어졌다 — 건의 종류는 `c.bid_kind` 로 결과에 실려 나가고 순수
     * 함수가 그것을 알림의 묶음 키에 넣는다.
     *
     * 그래서 이 문장에 투찰 종류 **리터럴이 없다**. 그 부재는 문자열이 아니라 **거동**으로
     * 재진다 — 서면 건 하나와 실투찰 건 하나를 심고 후보 집합이 **둘 다** 담는지 본다.
     *
     * **공백뿐인 공고번호를 거른다**(`btrim(…) <> ''`). V1·V21 의 CHECK 는 `<> ''` 라 공백 한 칸을
     * 허용하고, 그런 행에서는 `NoticeKey` 가 서지 않아 **한 행이 훑기 전체를 멈춘다**. 요청 경로로는
     * 들어올 수 없는 모양이지만(HTTP 가 공백을 거부한다) 판독은 적재 경로를 신뢰하지 않는다 —
     * 원문 판독이 `ofOrNull` 로 같은 자리를 막는 것과 같은 규율이다.
     *
     * 매개변수 다섯: ① 마감 하한(now) ② 마감 상한(horizon) ③ 서류 하한(now) ④ 서류 상한(horizon)
     * ⑤ 개정 축의 마감 하한(now — 지난 공고의 개정은 할 일을 바꾸지 않는다).
     */
    const val SELECT_SWEEP_CANDIDATES =
        "SELECT c.account_id, c.notice_number, c.notice_round, c.bid_kind, c.state, c.created_at, " +
            "c.document_due_at, " +
            "$EFFECTIVE_DEADLINE AS effective_deadline_at, n.revision AS notice_revision, " +
            "n.updated_at AS notice_updated_at " +
            "FROM bid_case c LEFT JOIN notice n " +
            "ON n.notice_number = c.notice_number AND n.notice_round = c.notice_round " +
            "WHERE btrim(c.notice_number) <> '' AND (" +
            "$EFFECTIVE_DEADLINE BETWEEN ? AND ? " +
            "OR c.document_due_at BETWEEN ? AND ? " +
            "OR (n.revision > 1 AND n.updated_at > c.created_at AND $EFFECTIVE_DEADLINE >= ?)" +
            ") ORDER BY c.account_id, c.id"
}
