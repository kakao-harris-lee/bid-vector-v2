package bidvector.adapters.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.event.OutboxConsumerKind
import java.time.LocalDate

/**
 * outbox `payload_type`/`payload` 직렬화(scope.md ①, 설계 검토 (1) 「payload 직렬화의 타입
 * 판별」) — payload는 [StrategyEvent.StrategyUpdated]와 [NotificationRequestedPayload]
 * 둘(D-6F7-1·D-6F7-5). **등록·복원 둘 다 알 수 없는 타입은 예외로 거부한다**(fail-closed)
 * — 「모르면 건너뛴다」(legacy 조사가 찾아낸 열셋과 같은 형태, `_workspace/m4-4b1`)를
 * 쓰지 않는다.
 *
 * 값이 단순(정수·날짜·짧은 문자열, 목록·map도 원시 원소뿐)이라 Jackson 등 JSON
 * 라이브러리를 새로 끌어오지 않는다(측정된 필요 없음, §7) — 이스케이프를 인식하는
 * 구분자로 충분하다. `NotificationRequestedPayload`가 목록·map을 나르므로 구분자를
 * 계층별로 쓴다(`FIELD_SEPARATOR` 최상위, `LIST_SEPARATOR` 목록, map 은 `MAP_ENTRY_SEPARATOR`
 * 항목·`MAP_KV_SEPARATOR` 키/값 **둘**). **주의 — "각 계층이 자신의 구분자만 보호하면
 * 중첩이 안전하다"는 계층이 하나씩 고르게 겹칠 때만 맞다.** map 처럼 한 계층 안에 구분자가
 * 둘(항목·키값)이면 안쪽 계층(키를 `MAP_KV_SEPARATOR`로 먼저 보호)을 만든 **결과
 * 전체**를 바깥 계층(`MAP_ENTRY_SEPARATOR`)으로 다시 감싸야 한다 — 키 하나만 보호하고
 * 바깥 구분자로 이어 붙이면(join) 바깥쪽이 안쪽 이스케이프를 감싸지 않아 깨진다
 * (D-6F7-12 실측: decode 의 첫 `splitEscapedFor`는 이스케이프 종류를 가리지 않고
 * 전부 해제하므로, 감싸이지 않은 안쪽 보호는 그 자리에서 사라진다). 감싸인 계층은
 * 바깥 계층이 안쪽이 이미 이스케이프한 `ESCAPE` 문자를 다시 보호해 중첩이 안전하다
 * (CSV-in-CSV와 같은 원리) — `excludedSamplesField`가 이 감싸기를 명시적으로 한다.
 *
 * 직렬화 상수·하위 조립 함수는 이 object의 멤버가 아니라 **파일 스코프 private
 * top-level**이다(D-6F7 수정) — object 멤버로 두면 detekt `TooManyFunctions`(상한 11)와
 * `sizeGate`(함수 50줄 한도, v2-지침서 §5)가 동시에 걸린다: 지역 함수로 나누면 함수
 * 개수는 줄지만 바깥 함수의 줄 수는 그대로다(지역 함수 본문도 바깥 함수 줄 수에 든다).
 * 파일 top-level private 함수는 object 멤버가 아니라 `TooManyFunctions` 계수에 들지
 * 않으면서 줄 수도 각자 짧게 남는다 — 같은 파일이라 접근 범위는 그대로 이 파일 안이다.
 */
internal object OutboxPayloadCodec {
    const val STRATEGY_UPDATED_TYPE = "StrategyUpdated"
    const val NOTIFICATION_REQUESTED_TYPE = "NotificationRequested"

    fun payloadTypeOf(payload: Any?): String =
        when (payload) {
            is StrategyEvent.StrategyUpdated -> STRATEGY_UPDATED_TYPE
            is NotificationRequestedPayload -> NOTIFICATION_REQUESTED_TYPE
            else -> unknownPayloadType(payload)
        }

    /**
     * [OutboxConsumerKind] -> `payload_type` 매핑(D-6F10-13) — kind 는 `workflow` 의
     * 어휘이고 저장 문자열은 이 object 의 것이다. 소진 `when` 이라 새 kind 가 생기면
     * 컴파일이 깨진다.
     *
     * **같은 문자열로 가는 길이 둘이 됐다** — payload 클래스 기준([payloadTypeOf]의 다른
     * 오버로드)과 kind 기준(이 함수). 한쪽만 고치는 표류는 타입이 막지 못하므로
     * `OutboxPayloadCodecTest` 가 kind 마다 표본 payload 를 짝지어 두 길의 결과가 같다는
     * **등식**을 잰다(알려진 제한의 잠금).
     */
    fun payloadTypeOf(kind: OutboxConsumerKind): String =
        when (kind) {
            OutboxConsumerKind.NotificationRequested -> NOTIFICATION_REQUESTED_TYPE
            OutboxConsumerKind.StrategyUpdated -> STRATEGY_UPDATED_TYPE
        }

    fun encode(payload: Any?): String =
        when (payload) {
            is StrategyEvent.StrategyUpdated -> encodeStrategyUpdated(payload)
            is NotificationRequestedPayload -> encodeNotificationRequested(payload)
            else -> unknownPayloadType(payload)
        }

    fun decode(
        payloadType: String,
        payload: String,
    ): Any =
        when (payloadType) {
            STRATEGY_UPDATED_TYPE -> decodeStrategyUpdated(payload)
            NOTIFICATION_REQUESTED_TYPE -> decodeNotificationRequested(payload)
            else -> error("알 수 없는 outbox payload_type 이다: $payloadType")
        }

    private fun unknownPayloadType(payload: Any?): Nothing =
        error("알 수 없는 outbox payload 타입이다: ${payload?.let { it::class.qualifiedName }}")

    private fun encodeStrategyUpdated(event: StrategyEvent.StrategyUpdated): String =
        (listOf(event.revision.value.toString()) + policyVersionFields(event.policyVersion))
            .joinToString(FIELD_SEPARATOR.toString()) { escapeFor(it, FIELD_SEPARATOR) }

    private fun decodeStrategyUpdated(payload: String): StrategyEvent.StrategyUpdated {
        val fields = splitEscapedFor(payload, FIELD_SEPARATOR)
        check(fields.size == STRATEGY_UPDATED_FIELD_COUNT) {
            "StrategyUpdated payload 형식이 아니다(필드 ${STRATEGY_UPDATED_FIELD_COUNT}개 기대): $payload"
        }
        return StrategyEvent.StrategyUpdated(
            StrategyRevision(fields[0].toInt()),
            decodePolicyVersion(fields[1], fields[2]),
        )
    }

    private fun encodeNotificationRequested(payload: NotificationRequestedPayload): String {
        val fields =
            listOf(payload.noticeId, encodeReasons(payload.bidNowReasons)) +
                policyVersionFields(payload.ladderPolicyVersion) +
                payload.strategyRevision.value.toString() +
                evidenceFieldsOf(payload.evidence)
        return fields.joinToString(FIELD_SEPARATOR.toString()) { escapeFor(it, FIELD_SEPARATOR) }
    }

    private fun decodeNotificationRequested(payload: String): NotificationRequestedPayload {
        val fields = splitEscapedFor(payload, FIELD_SEPARATOR)
        check(fields.size == NOTIFICATION_REQUESTED_FIELD_COUNT) {
            "NotificationRequested payload 형식이 아니다(필드 ${NOTIFICATION_REQUESTED_FIELD_COUNT}개 기대, " +
                "실제 ${fields.size}개): $payload"
        }
        val reasonsField = fields[REASONS_FIELD_INDEX]
        return NotificationRequestedPayload(
            noticeId = fields[NOTICE_ID_FIELD_INDEX],
            bidNowReasons = if (reasonsField.isEmpty()) emptyList() else splitEscapedFor(reasonsField, LIST_SEPARATOR),
            ladderPolicyVersion =
                decodePolicyVersion(
                    fields[LADDER_EFFECTIVE_FROM_FIELD_INDEX],
                    fields[LADDER_SOURCE_FIELD_INDEX],
                ),
            strategyRevision = StrategyRevision(fields[STRATEGY_REVISION_FIELD_INDEX].toInt()),
            evidence = decodeEvidence(fields.subList(EVIDENCE_FIRST_FIELD_INDEX, NOTIFICATION_REQUESTED_FIELD_COUNT)),
        )
    }
}

private const val FIELD_SEPARATOR = '|'
private const val LIST_SEPARATOR = ','
private const val MAP_ENTRY_SEPARATOR = ';'
private const val MAP_KV_SEPARATOR = '='
private const val ESCAPE = '\\'

/** `encodeStrategyUpdated`가 내는 필드 수(revision·effectiveFrom·source) — `decodeStrategyUpdated`의 형식 검증 상수. */
private const val STRATEGY_UPDATED_FIELD_COUNT = 3

/**
 * `encodeNotificationRequested`가 내는 최상위 필드 수 — noticeId·bidNowReasons·
 * ladderPolicyVersion 두 칸·strategyRevision·evidenceKind·(Diagnosed 열둘 | NotPredicted
 * 하나, 안 쓰는 자리는 빈 문자열)·excludedSamples. `decodeNotificationRequested`의 형식 검증
 * 상수.
 *
 * **M6/6F-10 D-6F10-19 — 17 → 20.** 새 칸 셋(`ladderPolicyVersion` 둘 + `strategyRevision`
 * 하나)은 `bidNowReasons` **뒤·evidence 앞**(인덱스 2·3·4)에 들어간다. 꼬리에 붙이지 않는
 * 이유는 아래 파생 상수 사슬이다 — evidence 블록이 목록의 **마지막 연속 구간**이어야
 * [EVIDENCE_TRAILING_SLOT_INDEX] 가 「그 블록의 마지막 칸」이라는 뜻을 유지한다.
 *
 * 저장된 구행을 깨지 않는가: **깨뜨릴 구행이 없다**(D-6F10-16). `OutboxNotificationRequestPort`
 * 를 참조하는 main 코드는 자기 파일뿐이었고 app 게이트가 그 참조를 금지한 채 초록이었다 —
 * 어느 DB 에도 이 `payload_type` 행이 영속된 적이 없다(test 는 Testcontainers 일회성).
 * 그래서 `payload_type` 토큰을 유지하고 구 디코더를 두지 않는다(없는 구행을 위한 죽은 코드).
 */
private const val NOTIFICATION_REQUESTED_FIELD_COUNT = 20

/**
 * `NotificationRequested` 최상위 칸의 자리 이름 — `encodeNotificationRequested` 가 내는 순서
 * 그대로다. 인덱스를 리터럴로 쓰지 않는 이유는 매직 넘버 금지(v2-지침서 §5)만이 아니다:
 * 칸이 늘면 **이 블록 하나만** 고치면 되고, evidence 블록의 시작이 여기서 **도출**되므로 둘이
 * 어긋날 자리가 없다.
 */
private const val NOTICE_ID_FIELD_INDEX = 0
private const val REASONS_FIELD_INDEX = 1
private const val LADDER_EFFECTIVE_FROM_FIELD_INDEX = 2
private const val LADDER_SOURCE_FIELD_INDEX = 3
private const val STRATEGY_REVISION_FIELD_INDEX = 4

/** evidence 블록이 시작하는 인덱스 — 위 다섯 칸 **바로 뒤**(도출, 손으로 세지 않는다). */
private const val EVIDENCE_FIRST_FIELD_INDEX = STRATEGY_REVISION_FIELD_INDEX + 1

/** evidence 블록의 칸 수 — evidenceKind 1 + Diagnosed 필드 12 + 미사용 1 + excludedSamples/reason 1. */
private const val EVIDENCE_FIELD_COUNT = NOTIFICATION_REQUESTED_FIELD_COUNT - EVIDENCE_FIRST_FIELD_INDEX
private const val EVIDENCE_KIND_DIAGNOSED = "DIAGNOSED"
private const val EVIDENCE_KIND_NOT_PREDICTED = "NOT_PREDICTED"

/** [EVIDENCE_FIELD_COUNT]에서 evidenceKind(1)·마지막 칸(1)을 뺀 나머지 — `NotPredicted`가 채우지 않는 빈 칸 수. */
private const val EVIDENCE_BLANK_SLOTS_FOR_NOT_PREDICTED = EVIDENCE_FIELD_COUNT - 2

/** evidence 필드 목록의 마지막 칸 — Diagnosed 는 excludedSamples, NotPredicted 는 reason. */
private const val EVIDENCE_TRAILING_SLOT_INDEX = EVIDENCE_FIELD_COUNT - 1

/**
 * `PolicyVersion` 을 **두 칸**으로 편다(`effectiveFrom`·`source`) — `StrategyUpdated` 와
 * `NotificationRequested` 가 같은 모양을 쓰므로 한 자리에 둔다(§5 중복 금지).
 *
 * `effectiveFrom` 은 소진 `when` 이다. `if (… is On) … else ""` 로 쓰면 `EffectiveFrom` 에
 * 셋째 하위 타입이 생겨도 조용히 `Initial` 처럼 인코딩한다(복원 쪽 fail-closed 규율의 반대
 * 방향) — 소진 `when` 은 그 커밋에서 컴파일을 깨뜨린다.
 */
private fun policyVersionFields(version: PolicyVersion): List<String> {
    val effectiveFromField =
        when (val effectiveFrom = version.effectiveFrom) {
            EffectiveFrom.Initial -> ""
            is EffectiveFrom.On -> effectiveFrom.date.toString()
        }
    return listOf(effectiveFromField, version.source)
}

/** [policyVersionFields] 의 역함수 — 빈 `effectiveFrom` 칸이 `Initial` 이다. */
private fun decodePolicyVersion(
    effectiveFromField: String,
    source: String,
): PolicyVersion {
    val effectiveFrom =
        if (effectiveFromField.isEmpty()) {
            EffectiveFrom.Initial
        } else {
            EffectiveFrom.On(LocalDate.parse(effectiveFromField))
        }
    return PolicyVersion(effectiveFrom, source)
}

private fun encodeReasons(reasons: List<String>): String =
    reasons.joinToString(LIST_SEPARATOR.toString()) { escapeFor(it, LIST_SEPARATOR) }

private fun excludedSamplesField(excludedSamples: Map<String, Int>): String =
    // toSortedMap 은 자연 순서 비교자를 쓴다 — sortedBy 람다가 낳는 합성 클래스
    // (jarContentGate가 거부하는 stdlib SourceFile 유출)를 만들지 않는다.
    //
    // D-6F7-12 — 키는 MAP_KV_SEPARATOR('=')와 MAP_ENTRY_SEPARATOR(';') 둘 다 담을 수
    // 있다. decode 가 먼저 ';'로 전체를 나누고(splitEscapedFor) 그 다음 각 항목을
    // '='로 나누므로, 두 구분자를 같은 단계에서 나란히 이스케이프하면 안 된다(첫
    // splitEscapedFor 가 이스케이프 종류를 안 가리고 전부 해제해 두 번째 '=' 보호가
    // 사라진다 — 실측). 안쪽 계층(키를 '='만 보호)으로 먼저 감싸고, 그 결과 전체를
    // 바깥 계층('; '보호)으로 한 번 더 감싼다 — FIELD_SEPARATOR/LIST_SEPARATOR 가
    // 이미 쓰는 것과 같은 중첩(파일 KDoc).
    excludedSamples
        .toSortedMap()
        .entries
        .joinToString(MAP_ENTRY_SEPARATOR.toString()) { (reason, count) ->
            val entry = "${escapeFor(reason, MAP_KV_SEPARATOR)}$MAP_KV_SEPARATOR$count"
            escapeFor(entry, MAP_ENTRY_SEPARATOR)
        }

/**
 * evidenceKind + Diagnosed 12칸 + NotPredicted 1칸 + excludedSamples 1칸(총
 * [EVIDENCE_FIELD_COUNT]칸, noticeId·bidNowReasons와 합쳐 [NOTIFICATION_REQUESTED_FIELD_COUNT])
 * — 안 쓰는 갈래는 빈 문자열이다(§3.1 「reason 코드 + 구조화 payload」 — 두 갈래를 한
 * 행에 펴는 관례상 선택이지 값의 혼동은 아니다. `evidenceKind`가 갈래를 소진적으로
 * 가른다).
 */
private fun evidenceFieldsOf(evidence: NotificationEvidencePayload): List<String> =
    when (evidence) {
        is NotificationEvidencePayload.Diagnosed -> {
            listOf(
                EVIDENCE_KIND_DIAGNOSED,
                evidence.trainingRowCount.toString(),
                evidence.segmentSupport,
                evidence.shrinkageWeight,
                evidence.excludedObservations.toString(),
                evidence.agencySampleCount.toString(),
                evidence.agencySampleBelowThreshold.toString(),
                evidence.releaseId,
                evidence.artifactChecksum,
                evidence.featureSchemaVersion,
                evidence.codeVersion,
                evidence.datasetId,
                evidence.releaseKind,
                "",
                excludedSamplesField(evidence.excludedSamples),
            )
        }

        is NotificationEvidencePayload.NotPredicted -> {
            listOf(EVIDENCE_KIND_NOT_PREDICTED) +
                List(EVIDENCE_BLANK_SLOTS_FOR_NOT_PREDICTED) { "" } +
                evidence.reason
        }
    }

private fun decodeExcludedSamples(field: String): Map<String, Int> =
    if (field.isEmpty()) {
        emptyMap()
    } else {
        // entry 분리는 splitEscapedFor 로 한다 — 키가 MAP_ENTRY_SEPARATOR 를 이스케이프해
        // 실었을 수 있어(encode 대칭) 순진한 String.split 은 그 이스케이프를 못 본다.
        splitEscapedFor(field, MAP_ENTRY_SEPARATOR).associate { entry ->
            val kv = splitEscapedFor(entry, MAP_KV_SEPARATOR)
            check(kv.size == 2) { "excludedSamples 항목 형식이 아니다: $entry" }
            kv[0] to kv[1].toInt()
        }
    }

private fun decodeEvidence(evidenceFields: List<String>): NotificationEvidencePayload =
    when (val kind = evidenceFields[0]) {
        EVIDENCE_KIND_DIAGNOSED -> {
            NotificationEvidencePayload.Diagnosed(
                trainingRowCount = evidenceFields[1].toInt(),
                segmentSupport = evidenceFields[2],
                shrinkageWeight = evidenceFields[3],
                excludedObservations = evidenceFields[4].toInt(),
                agencySampleCount = evidenceFields[5].toInt(),
                agencySampleBelowThreshold = evidenceFields[6].toBooleanStrict(),
                releaseId = evidenceFields[7],
                artifactChecksum = evidenceFields[8],
                featureSchemaVersion = evidenceFields[9],
                codeVersion = evidenceFields[10],
                datasetId = evidenceFields[11],
                releaseKind = evidenceFields[12],
                excludedSamples = decodeExcludedSamples(evidenceFields[EVIDENCE_TRAILING_SLOT_INDEX]),
            )
        }

        EVIDENCE_KIND_NOT_PREDICTED -> {
            NotificationEvidencePayload.NotPredicted(reason = evidenceFields[EVIDENCE_TRAILING_SLOT_INDEX])
        }

        else -> {
            error("알 수 없는 NotificationRequested evidenceKind 다: $kind")
        }
    }

/** [delimiter] 계층 하나를 보호한다 — [ESCAPE] 자신을 먼저 보호해야 중첩이 안전하다(파일 KDoc). */
private fun escapeFor(
    value: String,
    delimiter: Char,
): String =
    value
        .replace(ESCAPE.toString(), "$ESCAPE$ESCAPE")
        .replace(delimiter.toString(), "$ESCAPE$delimiter")

/** [escapeFor]의 역함수 — 이스케이프를 인식하며 [delimiter]로 나눈다. */
private fun splitEscapedFor(
    payload: String,
    delimiter: Char,
): List<String> {
    val fields = mutableListOf(StringBuilder())
    var index = 0
    while (index < payload.length) {
        val char = payload[index]
        when {
            char == ESCAPE && index + 1 < payload.length -> {
                fields.last().append(payload[index + 1])
                index += 2
            }

            char == delimiter -> {
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
