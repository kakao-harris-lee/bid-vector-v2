package bidvector.adapters.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import java.time.LocalDate

/**
 * outbox `payload_type`/`payload` 직렬화(scope.md ①, 설계 검토 (1) 「payload 직렬화의 타입
 * 판별」) — 오늘 payload는 [StrategyEvent.StrategyUpdated] 하나뿐이다. **등록·복원 둘 다
 * 알 수 없는 타입은 예외로 거부한다**(fail-closed) — 「모르면 건너뛴다」(legacy 조사가
 * 찾아낸 열셋과 같은 형태, `_workspace/m4-4b1`)를 쓰지 않는다.
 *
 * 값이 단순(정수 하나·날짜 하나·짧은 문자열 하나)이라 Jackson 등 JSON 라이브러리를
 * 새로 끌어오지 않는다(측정된 필요 없음, §7) — 이스케이프를 인식하는 구분자 하나(`|`)로
 * 충분하다.
 */
internal object OutboxPayloadCodec {
    const val STRATEGY_UPDATED_TYPE = "StrategyUpdated"

    private const val FIELD_SEPARATOR = '|'
    private const val ESCAPE = '\\'

    /** [encodeStrategyUpdated]가 내는 필드 수(revision·effectiveFrom·source) — [decodeStrategyUpdated]의 형식 검증 상수. */
    private const val STRATEGY_UPDATED_FIELD_COUNT = 3

    fun payloadTypeOf(payload: Any?): String =
        when (payload) {
            is StrategyEvent.StrategyUpdated -> STRATEGY_UPDATED_TYPE
            else -> unknownPayloadType(payload)
        }

    fun encode(payload: Any?): String =
        when (payload) {
            is StrategyEvent.StrategyUpdated -> encodeStrategyUpdated(payload)
            else -> unknownPayloadType(payload)
        }

    fun decode(
        payloadType: String,
        payload: String,
    ): Any =
        when (payloadType) {
            STRATEGY_UPDATED_TYPE -> decodeStrategyUpdated(payload)
            else -> error("알 수 없는 outbox payload_type 이다: $payloadType")
        }

    private fun unknownPayloadType(payload: Any?): Nothing =
        error("알 수 없는 outbox payload 타입이다: ${payload?.let { it::class.qualifiedName }}")

    private fun encodeStrategyUpdated(event: StrategyEvent.StrategyUpdated): String {
        val effectiveFrom = event.policyVersion.effectiveFrom
        val effectiveFromField = if (effectiveFrom is EffectiveFrom.On) effectiveFrom.date.toString() else ""
        return listOf(event.revision.value.toString(), effectiveFromField, event.policyVersion.source)
            .joinToString(FIELD_SEPARATOR.toString(), transform = ::escape)
    }

    private fun decodeStrategyUpdated(payload: String): StrategyEvent.StrategyUpdated {
        val fields = splitEscaped(payload)
        check(fields.size == STRATEGY_UPDATED_FIELD_COUNT) {
            "StrategyUpdated payload 형식이 아니다(필드 ${STRATEGY_UPDATED_FIELD_COUNT}개 기대): $payload"
        }
        val revision = StrategyRevision(fields[0].toInt())
        val effectiveFrom =
            if (fields[1].isEmpty()) EffectiveFrom.Initial else EffectiveFrom.On(LocalDate.parse(fields[1]))
        return StrategyEvent.StrategyUpdated(revision, PolicyVersion(effectiveFrom, fields[2]))
    }

    private fun escape(value: String): String =
        value
            .replace(ESCAPE.toString(), "$ESCAPE$ESCAPE")
            .replace(FIELD_SEPARATOR.toString(), "$ESCAPE$FIELD_SEPARATOR")

    /** [escape]의 역함수 — 이스케이프를 인식하며 [FIELD_SEPARATOR]로 나눈다. */
    private fun splitEscaped(payload: String): List<String> {
        val fields = mutableListOf(StringBuilder())
        var index = 0
        while (index < payload.length) {
            val char = payload[index]
            when {
                char == ESCAPE && index + 1 < payload.length -> {
                    fields.last().append(payload[index + 1])
                    index += 2
                }

                char == FIELD_SEPARATOR -> {
                    fields += StringBuilder()
                    index += 1
                }

                else -> {
                    fields.last().append(char)
                    index += 1
                }
            }
        }
        return fields.map { it.toString() }
    }
}
