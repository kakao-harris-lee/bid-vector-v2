package bidvector.adapters.notice

import bidvector.adapters.persistence.Sql
import bidvector.procurement.ProvenanceKind
import bidvector.sharedkernel.export
import bidvector.workflow.notice.NoticeAmountBound
import bidvector.workflow.notice.NoticeSearchCriteria
import java.time.Instant

/**
 * 질의에 실리는 값 하나 — 타입이 갈려 있어 어댑터가 `when` 소진으로 바인딩한다(`Any?` 로
 * 받아 `setObject` 에 넘기지 않는다: 그 통로는 문자열을 숫자 칸에 조용히 넣는 자리가 된다).
 */
internal sealed interface NoticeSearchBinding {
    data class Text(
        val value: String,
    ) : NoticeSearchBinding

    data class Amount(
        val won: Long,
    ) : NoticeSearchBinding

    data class Moment(
        val value: Instant,
    ) : NoticeSearchBinding

    data class Count(
        val value: Long,
    ) : NoticeSearchBinding
}

/**
 * 조립된 질의 — 문면([clause])과 값([bindings])이 갈려 있다. **[clause] 는 조건의 유무만
 * 보고 만들어지고 값에는 의존하지 않는다**(`NoticeSearchStatementTest` 가 서로 다른 값으로
 * 같은 문면이 나오는지를 등식으로 잰다) — 운영자 입력이 SQL 문면에 닿는 통로가 없다.
 */
internal data class NoticeSearchStatement(
    val clause: String,
    val bindings: List<NoticeSearchBinding>,
)

/**
 * `LIKE`/`ILIKE` 의 이스케이프 문자. PostgreSQL 기본값과 같은 역슬래시를 **명시**한다 —
 * 기본값에 기대면 `standard_conforming_strings` 같은 설정 변화가 조용히 뜻을 바꾼다.
 */
internal const val NOTICE_SEARCH_LIKE_ESCAPE: String = "\\"

/**
 * 부분 일치 패턴 — 운영자가 적은 **리터럴**을 감싸고, 그 안의 와일드카드(`%`·`_`)와
 * 이스케이프 문자 자신(`\`)을 글자로 바꾼다(역슬래시를 **먼저** 바꾼다 — 나중에 바꾸면
 * 앞서 넣은 이스케이프를 다시 이스케이프해 뜻이 어긋난다).
 */
internal fun noticeSearchContainsPattern(literal: String): String {
    val escaped =
        literal
            .replace(NOTICE_SEARCH_LIKE_ESCAPE, NOTICE_SEARCH_LIKE_ESCAPE + NOTICE_SEARCH_LIKE_ESCAPE)
            .replace("%", NOTICE_SEARCH_LIKE_ESCAPE + "%")
            .replace("_", NOTICE_SEARCH_LIKE_ESCAPE + "_")
    return "%$escaped%"
}

private const val ESCAPE_SUFFIX = " ESCAPE '$NOTICE_SEARCH_LIKE_ESCAPE'"

private const val TEXT_PREDICATE =
    "(notice_title ILIKE ?$ESCAPE_SUFFIX OR notice_number ILIKE ?$ESCAPE_SUFFIX)"

private const val AGENCY_PREDICATE =
    "(demand_agency_name ILIKE ?$ESCAPE_SUFFIX OR notice_agency_name ILIKE ?$ESCAPE_SUFFIX)"

/**
 * 금액 비교가 **성립하는** 행만 남기는 전제 — `compareKnownVat`(shared-kernel)의 두 전건을
 * 그대로 옮긴다: provenance 가 선언돼 있고(`hasDeclaredProvenance`), 과세가 한계값과 같은
 * 알려진 값이다(`sameKnownVat` — `UNKNOWN` 은 자기끼리도 같지 않다). 통화 축은 그 비교가
 * 보지 않으므로 여기서도 보지 않는다 — 검색이 감시보다 좁으면 같은 쌍에 다른 답이 난다.
 */
private const val AMOUNT_COMPARABLE_PREDICATE =
    "(base_amount_vat = ? AND base_amount_provenance <> ?)"

private const val AMOUNT_MIN_PREDICATE = "base_amount_won >= ?"
private const val AMOUNT_MAX_PREDICATE = "base_amount_won <= ?"
private const val DEADLINE_FROM_PREDICATE = "deadline_at >= ?"
private const val DEADLINE_TO_PREDICATE = "deadline_at <= ?"

/**
 * 조건 → (문면, 값). 조건이 없으면 `TRUE` 하나다(문면에 빈 `WHERE` 가 생기지 않는다).
 *
 * 금액 조건이 있으면 **비교 성립 전제**가 함께 걸린다 — 기초금액이 없거나 비교가 성립하지
 * 않는 공고는 결과에서 빠진다(계약 선언: 0 으로 간주하지 않는다). 마감 조건도 같은 성질이다
 * (마감이 없는 공고는 마감 범위로 거를 수 없다).
 */
internal fun noticeSearchStatement(criteria: NoticeSearchCriteria): NoticeSearchStatement {
    val predicates = mutableListOf<String>()
    val bindings = mutableListOf<NoticeSearchBinding>()
    criteria.text?.let { text ->
        predicates += TEXT_PREDICATE
        val pattern = noticeSearchContainsPattern(text.value)
        bindings += NoticeSearchBinding.Text(pattern)
        bindings += NoticeSearchBinding.Text(pattern)
    }
    criteria.agencyText?.let { agency ->
        predicates += AGENCY_PREDICATE
        val pattern = noticeSearchContainsPattern(agency.value)
        bindings += NoticeSearchBinding.Text(pattern)
        bindings += NoticeSearchBinding.Text(pattern)
    }
    criteria.amount?.let { amount -> appendAmount(amount, predicates, bindings) }
    criteria.deadlineFrom?.let { from ->
        predicates += DEADLINE_FROM_PREDICATE
        bindings += NoticeSearchBinding.Moment(from)
    }
    criteria.deadlineTo?.let { to ->
        predicates += DEADLINE_TO_PREDICATE
        bindings += NoticeSearchBinding.Moment(to)
    }
    val clause = if (predicates.isEmpty()) "TRUE" else predicates.joinToString(separator = " AND ")
    return NoticeSearchStatement(clause, bindings.toList())
}

private fun appendAmount(
    amount: NoticeAmountBound,
    predicates: MutableList<String>,
    bindings: MutableList<NoticeSearchBinding>,
) {
    val declared = (amount.min ?: amount.max)?.export() ?: return
    predicates += AMOUNT_COMPARABLE_PREDICATE
    bindings += NoticeSearchBinding.Text(declared.vatTreatment.name)
    bindings += NoticeSearchBinding.Text(ProvenanceKind.UNDECLARED.name)
    amount.min?.let { min ->
        predicates += AMOUNT_MIN_PREDICATE
        bindings += NoticeSearchBinding.Amount(min.export().won)
    }
    amount.max?.let { max ->
        predicates += AMOUNT_MAX_PREDICATE
        bindings += NoticeSearchBinding.Amount(max.export().won)
    }
}

/**
 * 검색 SQL 셋. `notice` 표 하나만 읽는다(join 없음) — 원문 관측(`raw_observation`)·감사
 * (`notice_audit`)·개찰 표에 닿지 않는다. 열 목록은 [Sql.NOTICE_COLUMNS] 를 그대로 쓴다
 * (복원이 `toNoticeRow`·`reconstructNotice` 를 재사용하므로 같은 목록이어야 한다).
 *
 * 정렬은 **마감 임박 순 하나**고 결정적이다 — 마감이 없는 공고는 뒤로 보내고(`NULLS LAST`)
 * 공고번호·차수로 묶음을 깨서 같은 질의가 같은 순서를 낸다.
 */
internal object NoticeSearchSql {
    const val ORDER = "ORDER BY deadline_at ASC NULLS LAST, notice_number ASC, notice_round ASC"

    fun selectPage(clause: String): String =
        """
        SELECT notice_number, notice_round, ${Sql.NOTICE_COLUMNS}
        FROM notice
        WHERE $clause
        $ORDER
        LIMIT ? OFFSET ?
        """

    fun selectCount(clause: String): String = "SELECT count(*) AS total FROM notice WHERE $clause"

    /** 빈 결과의 두 뜻을 가르는 유일한 질의 — 조건 없이 「수집된 공고가 하나라도 있는가」. */
    const val SELECT_ANY_COLLECTED = "SELECT EXISTS (SELECT 1 FROM notice) AS collected"
}
