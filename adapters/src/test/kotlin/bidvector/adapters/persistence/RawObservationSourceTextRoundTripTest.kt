package bidvector.adapters.persistence

import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * F-7 운영자 결정(2026-09-08) — `raw_observation.payload` 는 원문 전체를 재직렬화 없이
 * 담는다. 이 test는 그 요구를 문자 그대로 잰다: 저장한 [RawNoticeObservation.sourceText]와
 * 조회한 `payload` 컬럼 값이 **바이트 동일**해야 한다 — 공백·키 순서·escape 형태가 조금이라도
 * 바뀌면(예: JSONB 재직렬화를 거치면) 이 test가 깨진다.
 */
class RawObservationSourceTextRoundTripTest : PersistenceTestSupport() {
    private fun fetchPayload(observationKey: String): String =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("SELECT payload FROM raw_observation WHERE observation_key = ?")
                .use { statement ->
                    statement.setString(1, observationKey)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getString(1)
                    }
                }
        }

    @Test
    fun `원문(sourceText)이 있으면 payload 는 그 바이트 그대로 저장된다 — 재직렬화 없음`() {
        // 일부러 표준 정렬(키 알파벳 순)과 다른 순서 + 불규칙한 공백을 둔다 — 재직렬화를
        // 거치면(JSONB 캐스팅 등) 이 형태가 사라진다.
        val servedBytes = "{\"bidNtceOrd\":  \"000\", \"bidNtceNo\":\"SRC-TEXT-001\"}"
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to "SRC-TEXT-001", RawKey("bidNtceOrd") to "000"),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-08T00:00:00Z"),
                sourceText = servedBytes,
            )

        val key = appendRawObservation(observation)

        fetchPayload(key.value) shouldBe servedBytes
    }

    @Test
    fun `원문(sourceText)이 없으면 payload 는 등재분 투영으로 대신한다 — 알려진 제한`() {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to "SRC-TEXT-002", RawKey("bidNtceOrd") to "000"),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-08T00:00:01Z"),
            )

        val key = appendRawObservation(observation)
        val expectedFallback = ObservationPayloadCodec.encode(observation, testFieldContracts())

        fetchPayload(key.value) shouldBe expectedFallback
    }

    @Test
    fun `sourceText 가 다르면 등재분이 같아도 다른 ObservationKey 를 낸다`() {
        val fields = mapOf(RawKey("bidNtceNo") to "SRC-TEXT-003", RawKey("bidNtceOrd") to "000")
        val observedAt = Instant.parse("2026-09-08T00:00:02Z")
        val observationA =
            RawNoticeObservation.of(fields, SourceEndpoint.NOTICE_LIST, observedAt, sourceText = "{\"a\":1}")
        val observationB =
            RawNoticeObservation.of(fields, SourceEndpoint.NOTICE_LIST, observedAt, sourceText = "{\"a\": 1}")

        val keyA = appendRawObservation(observationA)
        val keyB = appendRawObservation(observationB)

        (keyA == keyB) shouldBe false
    }
}
