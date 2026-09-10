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
import java.time.Instant

/**
 * M3/3F D-3F-4 — `OpeningRankOneOutcome` ↔ `opening_rank_one_*` 컬럼 왕복(왕복 안정성 — 저장한
 * 값을 그대로 복원한다, [ProvenanceCodec]과 같은 자리). `bind`/`read`를 한 object 에 묶어
 * [JdbcOpeningResultRepository]의 함수 수를 줄인다(detekt TooManyFunctions, CPD 중복 제거와
 * 같은 「분산시켜 재사용」 판단). kind 문자열은 이 파일이 정하는 내부 어휘이지 KONEPS 원문
 * 라벨이 아니다 — `V5__opening_complete_axis.sql` 의 `opening_rank_one_kind_enum` CHECK 와
 * 반드시 같은 값이어야 한다.
 *
 * **verifier r1 F-1 뒤** — `bind`가 이 축의 일곱 컬럼을 [OpeningRankOneOutcome.observedAt]과
 * 함께 하나로 묶어 내보낸다. `Sql.kt`의 UPSERT 술어가 `EXCLUDED.opening_rank_one_kind`가
 * NULL 인지로 「이 축 전부 보존」 대 「이 축 전부 교체」를 가른다 — 컬럼별 갱신이 아니다.
 */
internal object OpeningRankOneKind {
    const val RANK_MISSING = "RANK_MISSING"
    const val RANK_DUPLICATED = "RANK_DUPLICATED"
    const val DETERMINED = "DETERMINED"

    private fun of(outcome: OpeningRankOneOutcome): String? =
        when (outcome) {
            OpeningRankOneOutcome.NotObserved -> null
            is OpeningRankOneOutcome.RankMissing -> RANK_MISSING
            is OpeningRankOneOutcome.RankDuplicated -> RANK_DUPLICATED
            is OpeningRankOneOutcome.Determined -> DETERMINED
        }

    /** 7 컬럼(kind·중복 건수·1위 행 축 4·관측 시각)을 `startIndex`부터 바인딩하고 다음 free index 를 낸다. */
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
        statement.setNullableTimestamp(index++, outcome.observedAt)
        return index
    }

    /** 저장한 [OpeningRankOneOutcome] 그대로 복원한다(kind 가 왕복의 유일한 판별자). */
    fun read(rs: ResultSet): OpeningRankOneOutcome =
        when (val kind = rs.getString("opening_rank_one_kind")) {
            null -> {
                OpeningRankOneOutcome.NotObserved
            }

            RANK_MISSING -> {
                OpeningRankOneOutcome.RankMissing(readObservedAt(rs))
            }

            RANK_DUPLICATED -> {
                val count = requireNotNull(rs.getInt("opening_rank_one_duplicate_count"))
                OpeningRankOneOutcome.RankDuplicated(count, readObservedAt(rs))
            }

            DETERMINED -> {
                OpeningRankOneOutcome.Determined(readBid(rs), readObservedAt(rs))
            }

            else -> {
                error("알 수 없는 opening_rank_one_kind: $kind")
            }
        }

    private fun readObservedAt(rs: ResultSet): Instant =
        requireNotNull(rs.getTimestamp("opening_rank_one_observed_at")?.toInstant())

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
        )
    }
}

/**
 * M3/3F D-3F-4 — `DrawNumberObservation` ↔ `draw_numbers_*` 컬럼 왕복.
 *
 * **verifier r2 N-1 뒤 — `OutOfRange`의 `validRange` 는 이 축 자신의 컬럼
 * (`draw_numbers_valid_range_max`)에 싣는다.** 이전 판(r1 F-3)은 그 값을 부모의
 * `total_reserve_price_candidate_count`(3E 슬롯, 별도 COALESCE 축)에서 읽기 시점에
 * 재구성했는데, `INSERT ... ON CONFLICT` 의 CHECK 가 병합 뒤 행이 아니라 들어오는 제안
 * tuple 에 걸려(PostgreSQL 관용구) 부모가 이미 총예가건수를 가진 상태에서도 그 축을 안
 * 실은 정상 저장이 거부됐다. `validRange.first`(1-기반 인덱스라 항상 1 — `OutOfRange.init`
 * 의 `require` 가 verifier r3 M-1 뒤 그것을 타입으로 강제한다, `NoticeFacts.kt` 참고)는
 * 저장하지 않는다(중복 금지) — `validRange.last`(상한)만 싣는다. `read`가 재구성하는
 * `1..max`의 하한이 그 `require` 와 항상 일치한다.
 *
 * **verifier r1 F-1·F-2 뒤** — `bind`가 이 축의 네 컬럼(kind·번호 배열·관측 시각·범위 상한)
 * 을 하나로 묶어 내보낸다. `Sql.kt`가 `EXCLUDED.draw_numbers_kind` NULL 여부로 축 전체를
 * 보존/교체한다.
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

    /** 4 컬럼(kind·번호 배열·관측 시각·`OutOfRange` 범위 상한)을 `startIndex`부터 바인딩하고 다음 free index 를 낸다. */
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
        statement.setNullableTimestamp(index++, observation.observedAt)
        val validRangeMax = (observation as? DrawNumberObservation.OutOfRange)?.validRange?.last
        statement.setNullableInt(index++, validRangeMax)
        return index
    }

    fun read(rs: ResultSet): DrawNumberObservation {
        val kind = rs.getString("draw_numbers_kind") ?: return DrawNumberObservation.NotObserved
        val numbers = readNumberSet(rs)
        val observedAt = requireNotNull(rs.getTimestamp("draw_numbers_observed_at")?.toInstant())
        return when (kind) {
            VERIFIED -> {
                DrawNumberObservation.Verified(numbers, observedAt)
            }

            OUT_OF_RANGE -> {
                val max = requireNotNull(rs.getInt("draw_numbers_valid_range_max").takeUnless { rs.wasNull() })
                DrawNumberObservation.OutOfRange(numbers, 1..max, observedAt)
            }

            RANGE_CHECK_UNAVAILABLE -> {
                DrawNumberObservation.RangeCheckUnavailable(numbers, observedAt)
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
