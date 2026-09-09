package bidvector.adapters.persistence

import bidvector.procurement.DrawNumberObservation
import bidvector.procurement.ObservedBidAmount
import bidvector.procurement.OpeningRankOneBid
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Rate
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * M3/3F D-3F-4 — `OpeningRankOneOutcome` ↔ `opening_rank_one_*` 컬럼 왕복(왕복 안정성 — 저장한
 * 값을 그대로 복원한다, [ProvenanceCodec]과 같은 자리). `bind`/`read`를 한 object 에 묶어
 * [JdbcOpeningResultRepository]의 함수 수를 줄인다(detekt TooManyFunctions, CPD 중복 제거와
 * 같은 「분산시켜 재사용」 판단). kind 문자열은 이 파일이 정하는 내부 어휘이지 KONEPS 원문
 * 라벨이 아니다 — `V5__opening_complete_axis.sql` 의 `opening_rank_one_kind_enum` CHECK 와
 * 반드시 같은 값이어야 한다.
 */
internal object OpeningRankOneKind {
    const val RANK_MISSING = "RANK_MISSING"
    const val RANK_DUPLICATED = "RANK_DUPLICATED"
    const val DETERMINED = "DETERMINED"

    private fun of(outcome: OpeningRankOneOutcome): String? =
        when (outcome) {
            OpeningRankOneOutcome.NotObserved -> null
            OpeningRankOneOutcome.RankMissing -> RANK_MISSING
            is OpeningRankOneOutcome.RankDuplicated -> RANK_DUPLICATED
            is OpeningRankOneOutcome.Determined -> DETERMINED
        }

    /** 10 컬럼(kind·중복 건수·1위 행 축 8)을 `startIndex`부터 바인딩하고 다음 free index 를 낸다. */
    fun bind(
        statement: PreparedStatement,
        startIndex: Int,
        outcome: OpeningRankOneOutcome,
    ): Int {
        var index = startIndex
        val bid = (outcome as? OpeningRankOneOutcome.Determined)?.bid
        statement.setString(index++, of(outcome))
        statement.setNullableInt(index++, (outcome as? OpeningRankOneOutcome.RankDuplicated)?.count)
        statement.setString(index++, bid?.bidderName)
        statement.setBigDecimal(index++, bid?.bidAmount?.won?.let(BigDecimal::valueOf))
        statement.setString(index++, bid?.bidAmount?.currency?.name)
        statement.setBigDecimal(index++, bid?.bidRate?.fraction)
        statement.setString(index++, bid?.priceEvaluationScore)
        statement.setString(index++, bid?.technicalEvaluationScore)
        statement.setString(index++, bid?.technicalEvaluationNatureScore)
        statement.setString(index++, bid?.totalEvaluationAmountScore)
        return index
    }

    /** 저장한 [OpeningRankOneOutcome] 그대로 복원한다(kind 가 왕복의 유일한 판별자). */
    fun read(rs: ResultSet): OpeningRankOneOutcome =
        when (val kind = rs.getString("opening_rank_one_kind")) {
            null -> {
                OpeningRankOneOutcome.NotObserved
            }

            RANK_MISSING -> {
                OpeningRankOneOutcome.RankMissing
            }

            RANK_DUPLICATED -> {
                val count = requireNotNull(rs.getInt("opening_rank_one_duplicate_count"))
                OpeningRankOneOutcome.RankDuplicated(count)
            }

            DETERMINED -> {
                OpeningRankOneOutcome.Determined(readBid(rs))
            }

            else -> {
                error("알 수 없는 opening_rank_one_kind: $kind")
            }
        }

    private fun readBid(rs: ResultSet): OpeningRankOneBid {
        val won = rs.getBigDecimal("opening_rank_one_bid_amount_won")
        val currencyName = rs.getString("opening_rank_one_bid_amount_currency")
        val bidAmount =
            if (won != null && currencyName != null) {
                ObservedBidAmount(won.toLong(), Currency.valueOf(currencyName))
            } else {
                null
            }
        return OpeningRankOneBid(
            bidderName = requireNotNull(rs.getString("opening_rank_one_bidder_name")),
            bidAmount = bidAmount,
            bidRate = rs.getBigDecimal("opening_rank_one_bid_rate_fraction")?.let(Rate::ofFraction),
            priceEvaluationScore = rs.getString("opening_rank_one_price_evaluation_score"),
            technicalEvaluationScore = rs.getString("opening_rank_one_technical_evaluation_score"),
            technicalEvaluationNatureScore = rs.getString("opening_rank_one_technical_evaluation_nature_score"),
            totalEvaluationAmountScore = rs.getString("opening_rank_one_total_evaluation_amount_score"),
        )
    }
}

/**
 * M3/3F D-3F-4 — `DrawNumberObservation` ↔ `draw_numbers_*` 컬럼 왕복. `OutOfRange`의
 * `validRange`는 별도 컬럼이 없다 — 이미 부모에 있는 `total_reserve_price_candidate_count`
 * (3E 슬롯)에서 재구성한다(중복 저장 금지) — 그래서 [read]가 그 값을 인자로 받는다.
 */
internal object DrawNumbersKind {
    const val VERIFIED = "VERIFIED"
    const val OUT_OF_RANGE = "OUT_OF_RANGE"
    const val RANGE_CHECK_UNAVAILABLE = "RANGE_CHECK_UNAVAILABLE"

    private fun of(observation: DrawNumberObservation): String? =
        when (observation) {
            DrawNumberObservation.NotObserved -> null
            is DrawNumberObservation.Verified -> VERIFIED
            is DrawNumberObservation.OutOfRange -> OUT_OF_RANGE
            is DrawNumberObservation.RangeCheckUnavailable -> RANGE_CHECK_UNAVAILABLE
        }

    private fun numbersOf(observation: DrawNumberObservation): Set<Int>? =
        when (observation) {
            DrawNumberObservation.NotObserved -> null
            is DrawNumberObservation.Verified -> observation.numbers
            is DrawNumberObservation.OutOfRange -> observation.numbers
            is DrawNumberObservation.RangeCheckUnavailable -> observation.numbers
        }

    /** 2 컬럼(kind·번호 배열)을 `startIndex`부터 바인딩하고 다음 free index 를 낸다. */
    fun bind(
        statement: PreparedStatement,
        startIndex: Int,
        observation: DrawNumberObservation,
    ): Int {
        var index = startIndex
        statement.setString(index++, of(observation))
        val numbers = numbersOf(observation)
        if (numbers != null) {
            statement.setArray(index++, statement.connection.createArrayOf("integer", numbers.toTypedArray()))
        } else {
            statement.setNull(index++, Types.ARRAY)
        }
        return index
    }

    fun read(
        rs: ResultSet,
        totalReservePriceCandidateCount: Int?,
    ): DrawNumberObservation {
        val kind = rs.getString("draw_numbers_kind") ?: return DrawNumberObservation.NotObserved
        val numbers = readNumberSet(rs)
        return when (kind) {
            VERIFIED -> {
                DrawNumberObservation.Verified(numbers)
            }

            OUT_OF_RANGE -> {
                val total = requireNotNull(totalReservePriceCandidateCount)
                DrawNumberObservation.OutOfRange(numbers, 1..total)
            }

            RANGE_CHECK_UNAVAILABLE -> {
                DrawNumberObservation.RangeCheckUnavailable(numbers)
            }

            else -> {
                error("알 수 없는 draw_numbers_kind: $kind")
            }
        }
    }

    private fun readNumberSet(rs: ResultSet): Set<Int> {
        val sqlArray = rs.getArray("draw_numbers") ?: return emptySet()

        @Suppress("UNCHECKED_CAST")
        val elements = sqlArray.array as Array<Int?>
        return elements.filterNotNull().toSet()
    }
}
