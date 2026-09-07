package bidvector.adapters.persistence

import bidvector.procurement.FieldPresence
import bidvector.procurement.KonepsFieldContract
import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.RawNoticeObservation

/**
 * `rawName` 알파벳 순 정렬 — 이름 있는 [Comparator]로 둔다. `sortedBy { ... }`(inline
 * `compareBy` 경유)는 합성 클래스의 `SourceFile` 디버그 속성이 stdlib `Comparisons.kt`로
 * 남아 `jarContentGate`·`packageOwnershipGate`(ADR 0006 §6)가 「게이트를 통과한 소스가
 * 아니다」로 거부한다(실측) — 평범한 SAM 객체는 이 문제가 없다.
 */
private val CONTRACT_RAW_NAME_ORDER =
    Comparator<KonepsFieldContract> { left, right -> left.rawName.name.compareTo(right.rawName.name) }

/**
 * [RawNoticeObservation]을 `raw_observation.payload_fields` JSONB 문자열로 직렬화한다(①·②).
 *
 * **알려진 제한** — [RawNoticeObservation]은 계약 없는 값 열람을 막는다(위협 모델 우회
 * (1)(10), `RawObservation.kt` 헤더 KDoc) — `keys`(필드 이름 집합)는 공개지만 값은
 * [RawNoticeObservation.presenceOf]가 등재된 [bidvector.procurement.KonepsFieldContract]를
 * 요구한다. 그래서 이 투영은 **레지스트리가 계약을 등재한 필드만** 담는다 — 미등재
 * ("unknown") 필드는 여기 실리지 않는다(회계의 `unknownFields` 축이 그 수를 별도로 센다).
 * 원문 전체는 [RawNoticeObservation.sourceText]가 나르고 `raw_observation.payload`(TEXT)에
 * 그대로 실린다(F-7 운영자 결정 2026-09-08) — 이 객체는 그 등재분 투영만 만든다.
 *
 * Jackson 등 무거운 JSON 라이브러리를 새로 끌어오지 않는다(측정된 필요 없음, §7) — 이
 * 투영은 문자열 값의 flat object 하나뿐이라 손으로 짠 이스케이프면 충분하다.
 */
internal object ObservationPayloadCodec {
    /** JSON 문자열에서 이스케이프가 필요한 제어문자 상한(U+0020 미만, RFC 8259). */
    private const val JSON_CONTROL_CHAR_BOUNDARY = 0x20

    fun encode(
        observation: RawNoticeObservation,
        fieldContracts: KonepsFieldContractRegistry,
    ): String {
        val entries =
            fieldContracts.contracts
                .sortedWith(CONTRACT_RAW_NAME_ORDER)
                .map { contract ->
                    val jsonValue =
                        when (val presence = observation.presenceOf(contract)) {
                            is FieldPresence.Present -> jsonString(presence.text)
                            FieldPresence.ExplicitNull -> "null"
                            FieldPresence.Missing -> null
                        }
                    contract.rawName.name to jsonValue
                }.filter { (_, value) -> value != null }
        return entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (name, value) ->
            "${jsonString(name)}:$value"
        }
    }

    /**
     * 거부된 write 시도값(⑥) — `rejected_write.attempted_value`. 점유 가드가 지키는 축만
     * 담는다. `dbMessage`는 트리거가 실제로 던진 원문(PSQLException.message, 방어 심층
     * 경로의 근거) — 잡은 예외를 조용히 버리지 않고 감사 기록에 싣는다.
     */
    fun encodeNoticeRow(
        row: NoticeRow,
        dbMessage: String?,
    ): String {
        val fields =
            listOf(
                "baseAmountWon" to row.baseAmountWon?.toPlainString(),
                "baseAmountProvenance" to row.baseAmountProvenance,
                "estimatedAmountWon" to row.estimatedAmountWon?.toPlainString(),
                "estimatedAmountProvenance" to row.estimatedAmountProvenance,
                "allocatedBudgetWon" to row.allocatedBudgetWon?.toPlainString(),
                "allocatedBudgetProvenance" to row.allocatedBudgetProvenance,
                "floorRateFraction" to row.floorRateFraction?.toPlainString(),
                "floorRateOriginKind" to row.floorRateOriginKind,
                "dbMessage" to dbMessage,
            ).filter { (_, value) -> value != null }
        return fields.joinToString(prefix = "{", postfix = "}", separator = ",") { (name, value) ->
            "${jsonString(name)}:${jsonString(requireNotNull(value))}"
        }
    }

    private fun jsonString(value: String): String {
        val escaped = StringBuilder(value.length + 2)
        escaped.append('"')
        for (char in value) {
            when (char) {
                '"' -> {
                    escaped.append("\\\"")
                }

                '\\' -> {
                    escaped.append("\\\\")
                }

                '\n' -> {
                    escaped.append("\\n")
                }

                '\r' -> {
                    escaped.append("\\r")
                }

                '\t' -> {
                    escaped.append("\\t")
                }

                else -> {
                    if (char.code < JSON_CONTROL_CHAR_BOUNDARY) {
                        escaped.append("\\u%04x".format(char.code))
                    } else {
                        escaped.append(char)
                    }
                }
            }
        }
        escaped.append('"')
        return escaped.toString()
    }
}
