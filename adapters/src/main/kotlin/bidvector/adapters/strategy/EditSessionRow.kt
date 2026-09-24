package bidvector.adapters.strategy

import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.EditCommandSnapshot
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditableFieldSnapshot
import bidvector.workflow.strategy.MoneySnapshot
import bidvector.workflow.strategy.StrategyDraftSnapshot
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.time.Instant

/**
 * `edit_session.state_payload`·`last_command` JSON 왕복(V8__edit_session.sql) — 순수
 * **기계적** 변환이다. [EditSessionSnapshot]과 그 nested 타입은 이미 원시 필드만 갖고
 * 있다(D-6B1-7, `EditSession.toSnapshot()`이 kind 문자열로 내린 뒤다) — 여기서 도메인
 * 어휘를 다시 판정하지 않는다. `EditSession`은 만들지 않는다(D-6B1-6).
 *
 * 값이 중첩 리스트·중첩 객체(`StrategyDraftSnapshot`)를 담아 구분자 이스케이프 방식
 * (`OutboxPayloadCodec`, "값이 단순할 때"가 전제)의 적용 범위를 벗어난다 — Jackson 을
 * 쓴다. `adapters`는 이미 `com.fasterxml.jackson.databind`를 두 파일(`HttpLlmRequirement
 * Extractor`·`SchemaValidation`)에서 직접 쓰고 있다(json-schema-validator 전이 의존,
 * `group.forbidden`은 domain 층 대상이라 adapters 는 대상이 아니다) — 새 의존이 아니라
 * 기존 것의 세 번째 사용이다.
 */
internal object EditSessionRow {
    /**
     * verifier r7 HIGH-5 수정 — 이 코덱 전용 인스턴스(`adapters` 전체에서 `ObjectMapper`를
     * 쓰는 다른 자리는 `SchemaValidation.kt`가 자기 인스턴스를 따로 갖는다, 실측:
     * `grep -rn "ObjectMapper(" adapters/src/main/kotlin/` 2건, 공유 0). 인코더는 정확한
     * 십진 노드로 쓰지만, 기본 `ObjectMapper`는 `readTree`가 부동소수 토큰을
     * `DoubleNode`로 읽어 `.asText()`가 그 double 의 최단 표기를 돌려준다 — 척도·유효숫자가
     * 예외·거부 없이 바뀐다(`0.70`→`0.7`, 고정밀 값은 끝자리가 바뀜). 두 설정이 함께
     * 필요하다(바이트코드 실측, `BaseNodeDeserializer._fromFloat`) — `USE_BIG_DECIMAL_
     * FOR_FLOATS`는 부동소수 토큰을 `DecimalNode`로 파싱하게 하지만, Jackson 내부가 그 값을
     * 만든 뒤 기본으로 켜진 `STRIP_TRAILING_BIGDECIMAL_ZEROES`(디폴트 `true`)로 **끝자리
     * 0 을 지운다** — `getDecimalValue()`가 척도 2 로 정확히 돌려준 `0.70`이 이 단계에서
     * 척도 1(`0.7`)로 깎인다(고정밀 값은 지울 끝자리 0 이 없어 영향받지 않는다 — 짧은
     * 척도값만 조용히 깎이는 이유). 이 기능도 꺼야 척도·정밀도 두 축이 함께 닫힌다.
     */
    private val mapper =
        ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)

    fun encodeStatePayload(snapshot: EditSessionSnapshot): String? {
        if (snapshot.stateKind == "EXPIRED") return null
        val node = mapper.createObjectNode()
        snapshot.stateField?.let { node.set<JsonNode>("field", fieldNode(it)) }
        snapshot.stateDraft?.let { node.set<JsonNode>("draft", draftNode(it)) }
        snapshot.stateRevision?.let { node.put("revision", it) }
        snapshot.stateCancelReasonKind?.let { node.put("cancelReasonKind", it) }
        snapshot.stateCancelReasonNote?.let { node.put("cancelReasonNote", it) }
        return mapper.writeValueAsString(node)
    }

    fun encodeLastCommand(command: EditCommandSnapshot?): String? {
        if (command == null) return null
        val node = mapper.createObjectNode()
        node.put("commandId", command.commandId)
        node.put("kind", command.kind)
        command.field?.let { node.set<JsonNode>("field", fieldNode(it)) }
        command.draft?.let { node.set<JsonNode>("draft", draftNode(it)) }
        command.seenRevision?.let { node.put("seenRevision", it) }
        command.cancelReasonKind?.let { node.put("cancelReasonKind", it) }
        command.cancelReasonNote?.let { node.put("cancelReasonNote", it) }
        return mapper.writeValueAsString(node)
    }

    fun toSnapshot(
        id: EditSessionId,
        operator: Actor.Operator,
        stateKind: String,
        statePayload: String?,
        expiresAt: Instant,
        sessionVersion: Int,
        lastCommand: String?,
    ): EditSessionSnapshot {
        val payload = statePayload?.let(mapper::readTree)
        return EditSessionSnapshot(
            id = id,
            operator = operator,
            expiresAt = expiresAt,
            sessionVersion = sessionVersion,
            stateKind = stateKind,
            stateField = payload?.get("field")?.let(::readField),
            stateDraft = payload?.get("draft")?.let(::readDraft),
            stateRevision = payload?.get("revision")?.let { it.requireIntValue("revision") },
            stateCancelReasonKind = payload?.get("cancelReasonKind")?.asText(),
            stateCancelReasonNote = payload?.get("cancelReasonNote")?.asText(),
            lastCommand = lastCommand?.let(mapper::readTree)?.let(::readCommand),
        )
    }

    private fun fieldNode(field: EditableFieldSnapshot): ObjectNode =
        mapper.createObjectNode().apply {
            put("kind", field.kind)
            field.detail?.let { put("detail", it) }
        }

    private fun readField(node: JsonNode): EditableFieldSnapshot =
        EditableFieldSnapshot(node.get("kind").asText(), node.get("detail")?.asText())

    private fun moneyNode(money: MoneySnapshot): ObjectNode =
        mapper.createObjectNode().apply {
            put("won", money.won)
            put("currency", money.currency)
            put("vatTreatment", money.vatTreatment)
            put("provenanceKind", money.provenanceKind)
            money.provenanceDetail?.let { put("provenanceDetail", it) }
        }

    private fun readMoney(node: JsonNode): MoneySnapshot =
        MoneySnapshot(
            won = node.get("won").requireLongValue("won"),
            currency = node.get("currency").asText(),
            vatTreatment = node.get("vatTreatment").asText(),
            provenanceKind = node.get("provenanceKind").asText(),
            provenanceDetail = node.get("provenanceDetail")?.asText(),
        )

    private fun draftNode(draft: StrategyDraftSnapshot): ObjectNode =
        mapper.createObjectNode().apply {
            set<JsonNode>("focusCategories", stringListNode(draft.focusCategories))
            set<JsonNode>("focusRegionTerms", stringListNode(draft.focusRegionTerms))
            set<JsonNode>("excludeRegionTerms", stringListNode(draft.excludeRegionTerms))
            set<JsonNode>("requiredKeywordTerms", stringListNode(draft.requiredKeywordTerms))
            set<JsonNode>("excludeKeywordTerms", stringListNode(draft.excludeKeywordTerms))
            draft.minBudget?.let { set<JsonNode>("minBudget", moneyNode(it)) }
            draft.maxBudget?.let { set<JsonNode>("maxBudget", moneyNode(it)) }
            draft.minimumMatchScore?.let { put("minimumMatchScore", it) }
            draft.minimumProbabilityScore?.let { put("minimumProbabilityScore", it) }
            draft.bidNowThreshold?.let { put("bidNowThreshold", it) }
            draft.reviewThreshold?.let { put("reviewThreshold", it) }
            draft.candidateLimit?.let { put("candidateLimit", it) }
            draft.maxActiveBids?.let { put("maxActiveBids", it) }
        }

    private fun readDraft(node: JsonNode): StrategyDraftSnapshot =
        StrategyDraftSnapshot(
            focusCategories = requireStringArray(node.get("focusCategories"), "focusCategories"),
            focusRegionTerms = requireStringArray(node.get("focusRegionTerms"), "focusRegionTerms"),
            excludeRegionTerms = requireStringArray(node.get("excludeRegionTerms"), "excludeRegionTerms"),
            requiredKeywordTerms = requireStringArray(node.get("requiredKeywordTerms"), "requiredKeywordTerms"),
            excludeKeywordTerms = requireStringArray(node.get("excludeKeywordTerms"), "excludeKeywordTerms"),
            minBudget = node.get("minBudget")?.let(::readMoney),
            maxBudget = node.get("maxBudget")?.let(::readMoney),
            minimumMatchScore = node.get("minimumMatchScore")?.let { BigDecimal(it.asText()) },
            minimumProbabilityScore = node.get("minimumProbabilityScore")?.let { BigDecimal(it.asText()) },
            bidNowThreshold = node.get("bidNowThreshold")?.let { BigDecimal(it.asText()) },
            reviewThreshold = node.get("reviewThreshold")?.let { BigDecimal(it.asText()) },
            candidateLimit = node.get("candidateLimit")?.let { it.requireIntValue("candidateLimit") },
            maxActiveBids = node.get("maxActiveBids")?.let { it.requireIntValue("maxActiveBids") },
        )

    private fun readCommand(node: JsonNode): EditCommandSnapshot =
        EditCommandSnapshot(
            commandId = node.get("commandId").asText(),
            kind = node.get("kind").asText(),
            field = node.get("field")?.let(::readField),
            draft = node.get("draft")?.let(::readDraft),
            seenRevision = node.get("seenRevision")?.let { it.requireIntValue("seenRevision") },
            cancelReasonKind = node.get("cancelReasonKind")?.asText(),
            cancelReasonNote = node.get("cancelReasonNote")?.asText(),
        )
}

// detekt TooManyFunctions(11) — 이 넷은 EditSessionRow 밖 top-level 로 뺀다(JsonNodeFactory
// 는 ObjectMapper 없이도 노드를 만든다, 상태 없는 순수 변환이라 무해).
private fun stringListNode(values: List<String>): ArrayNode =
    JsonNodeFactory.instance.arrayNode().apply { values.forEach(::add) }

/**
 * verifier r1 HIGH-2 수정 — 이전 판은 `node?.map { it.asText() } ?: emptyList()`라 리스트
 * 키가 **없거나 배열이 아니면 조용히 빈 리스트로 채웠다**(D-6B1-7 「지어내지 않는다」 위반).
 * 인코더([bidvector.adapters.strategy.EditSessionRow.draftNode])는 다섯 리스트 필드를
 * 예외 없이 항상 배열로 쓰므로, 정상적으로 인코딩된 행이라면 이 키는 **항상 존재하고
 * 항상 배열**이다 — 그 전제가 깨지면(키 없음·배열 아님·원소가 문자열이 아님) 거부한다.
 */
private fun requireStringArray(
    node: JsonNode?,
    field: String,
): List<String> {
    val array = requireNotNull(node) { "$field 가 없다" }
    require(array.isArray) { "$field 는 배열이어야 한다 — 실제: $array" }
    return array.map {
        require(it.isTextual) { "$field 의 원소는 문자열이어야 한다 — 실제: $it" }
        it.asText()
    }
}

/**
 * verifier r1 HIGH-2 수정 — 이전 판은 `JsonNode.asInt()`/`asLong()`을 그대로 썼는데, 그
 * 함수들은 숫자로 변환할 수 없는 값(문자열 `"not-a-number"` 등)에 **조용히 0 을 돌려준다**
 * (Jackson 관용). `StrategyRevision`은 0 을 허용하는 값이라 그 위조값이 그대로 통과해
 * workflow 가 "아무도 쓴 적 없는" revision 을 받았다. `canConvertToInt`/`canConvertToLong`
 * 으로 먼저 확인해 변환 불가능하면 거부한다.
 *
 * **Codex 1라운드 HIGH 수정 — `canConvertToInt`/`canConvertToLong`은 변환 "가능성"만 보고
 * 정수 "표기"인지는 안 본다.** Jackson 2.21.5(`:adapters` 실 컴파일 클래스패스 실측,
 * `2.18.3 -> 2.21.5` 카탈로그 상향)에서 `DoubleNode(1.2).canConvertToInt()`는 `true`이고
 * `asInt()`는 `1`을 돌려준다 — `1e2`도 `canConvertToInt()=true`·`asInt()=100`. 둘 다
 * 정수가 아닌데 통과해 **아무도 쓴 적 없는 값으로 절삭**된다(HIGH-2 가 남긴 반쪽 —
 * 그때는 변환 "불가능"만 막았고 변환은 되지만 표기가 정수가 아닌 경우는 열려 있었다).
 * `isIntegralNumber`(정수 표기 JSON 노드에서만 참 — `IntNode`·`LongNode`·`BigIntegerNode`
 * 등, `DoubleNode`·`FloatNode`는 값이 `2.0`처럼 정수여도 거짓)를 먼저 검사해 닫는다 —
 * 인코더([EditSessionRow.encodeStatePayload] 등)가 이 필드들에 항상 `Int`/`Long`을
 * `put()`하므로 정상 행은 언제나 정수 표기 노드다.
 */
private fun JsonNode.requireIntValue(field: String): Int {
    require(isIntegralNumber && canConvertToInt()) { "$field 는 정수여야 한다 — 실제: $this" }
    return asInt()
}

private fun JsonNode.requireLongValue(field: String): Long {
    require(isIntegralNumber && canConvertToLong()) { "$field 는 정수여야 한다 — 실제: $this" }
    return asLong()
}
