package bidvector.adapters.strategy

import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.EditCommandSnapshot
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditableFieldSnapshot
import bidvector.workflow.strategy.MoneySnapshot
import bidvector.workflow.strategy.StrategyDraftSnapshot
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
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
    private val mapper = ObjectMapper()

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
            stateRevision = payload?.get("revision")?.asInt(),
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
            won = node.get("won").asLong(),
            currency = node.get("currency").asText(),
            vatTreatment = node.get("vatTreatment").asText(),
            provenanceKind = node.get("provenanceKind").asText(),
            provenanceDetail = node.get("provenanceDetail")?.asText(),
        )

    private fun stringListNode(values: List<String>): ArrayNode = mapper.createArrayNode().apply { values.forEach(::add) }

    private fun readStringList(node: JsonNode?): List<String> = node?.map { it.asText() } ?: emptyList()

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
        }

    private fun readDraft(node: JsonNode): StrategyDraftSnapshot =
        StrategyDraftSnapshot(
            focusCategories = readStringList(node.get("focusCategories")),
            focusRegionTerms = readStringList(node.get("focusRegionTerms")),
            excludeRegionTerms = readStringList(node.get("excludeRegionTerms")),
            requiredKeywordTerms = readStringList(node.get("requiredKeywordTerms")),
            excludeKeywordTerms = readStringList(node.get("excludeKeywordTerms")),
            minBudget = node.get("minBudget")?.let(::readMoney),
            maxBudget = node.get("maxBudget")?.let(::readMoney),
            minimumMatchScore = node.get("minimumMatchScore")?.let { BigDecimal(it.asText()) },
            minimumProbabilityScore = node.get("minimumProbabilityScore")?.let { BigDecimal(it.asText()) },
            bidNowThreshold = node.get("bidNowThreshold")?.let { BigDecimal(it.asText()) },
            reviewThreshold = node.get("reviewThreshold")?.let { BigDecimal(it.asText()) },
            candidateLimit = node.get("candidateLimit")?.asInt(),
        )

    private fun readCommand(node: JsonNode): EditCommandSnapshot =
        EditCommandSnapshot(
            commandId = node.get("commandId").asText(),
            kind = node.get("kind").asText(),
            field = node.get("field")?.let(::readField),
            draft = node.get("draft")?.let(::readDraft),
            seenRevision = node.get("seenRevision")?.asInt(),
            cancelReasonKind = node.get("cancelReasonKind")?.asText(),
            cancelReasonNote = node.get("cancelReasonNote")?.asText(),
        )
}
