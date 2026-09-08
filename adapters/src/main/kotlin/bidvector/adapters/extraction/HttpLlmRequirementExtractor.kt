package bidvector.adapters.extraction

import bidvector.procurement.ExtractedGroupNo
import bidvector.procurement.ExtractedRequirementItem
import bidvector.procurement.ExtractedRequirements
import bidvector.procurement.ExtractedSerialNo
import bidvector.procurement.ExtractedSourceField
import bidvector.procurement.ExtractionEvidenceSpan
import bidvector.procurement.ExtractionOutcome
import bidvector.procurement.FetchedDocument
import bidvector.procurement.RequirementExtractionPort
import com.fasterxml.jackson.databind.JsonNode
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.timelimiter.TimeLimiter
import java.time.Clock
import java.util.concurrent.ExecutorService

/** chunk 준비 결과 — 형식·텍스트 층·예산 검사를 하나로 접어 [extractDetailed]의 반환문 수를 줄인다. */
private sealed interface PrepareOutcome {
    data class Ready(
        val chunks: List<DocumentChunk>,
    ) : PrepareOutcome

    data class Failed(
        val reason: ExtractionFailure,
    ) : PrepareOutcome
}

private sealed interface ChunkAttemptOutcome {
    data class Items(
        val items: List<ExtractedRequirementItem>,
    ) : ChunkAttemptOutcome

    data object AssertedAbsent : ChunkAttemptOutcome

    data class Failure(
        val reason: ExtractionFailure,
    ) : ChunkAttemptOutcome
}

/**
 * 문서 → 구조화 요건 추출 엔진(D-3C-6) — **`internal`**이라 adapters 밖에서 게이트 없이
 * 구성할 수 없다(위협 모델 방어 (b), 우회 (8)). public 진입점은 `WatchGatedExtractor`
 * 뿐이다. domain port(`RequirementExtractionPort`) 구현은 이 class 자신이 아니라
 * [LlmRequirementExtractionPortAdapter](얇은 위임)가 맡는다 — 이 class 가 port 까지
 * 함께 구현하면 구현 인터페이스가 2개가 돼 `typeShapeGate`의 상속 형태 래칫(D-3,
 * `ratchet.type.interfaces.max=1`)을 넘는다(실측).
 */
internal class HttpLlmRequirementExtractor(
    private val llmClient: LlmClient,
    private val schemaValidator: RequirementSchemaValidator,
    private val promptText: String,
    private val model: ModelId,
    private val policy: ExtractionPolicyData,
    private val clock: Clock,
    private val callExecutor: ExecutorService,
    private val circuitBreaker: CircuitBreaker = buildLlmCircuitBreaker("requirement-extraction", policy),
    private val timeLimiter: TimeLimiter = buildLlmTimeLimiter("requirement-extraction", policy),
) : RequirementExtractionEngine {
    override fun extractDetailed(document: FetchedDocument): ExtractionAttempt =
        when (val prepared = prepareChunks(document)) {
            is PrepareOutcome.Failed -> ExtractionAttempt.Uncertain(prepared.reason)
            is PrepareOutcome.Ready -> runChunks(prepared.chunks, document)
        }

    private fun prepareChunks(document: FetchedDocument): PrepareOutcome {
        val format = detectDocumentFormat(document.bytes, document.mediaType)
        val text = extractDocumentText(document.bytes, format)
        if (text !is DocumentTextExtraction.Extracted) {
            return PrepareOutcome.Failed(unsupportedReasonFor(text, format))
        }
        val chunking = chunkDocumentText(text.text, policy.chunkChars, policy.maxChunksPerDocument)
        val chunks = (chunking as? ChunkingOutcome.Chunks)?.chunks?.filter { it.text.isNotBlank() }
        return when {
            chunks == null -> {
                PrepareOutcome.Failed(ExtractionFailure.BudgetExceeded(BudgetExceededKind.ChunkCount))
            }

            chunks.size > policy.maxCallsPerDocument -> {
                PrepareOutcome.Failed(ExtractionFailure.BudgetExceeded(BudgetExceededKind.CallCount))
            }

            else -> {
                PrepareOutcome.Ready(chunks)
            }
        }
    }

    private fun unsupportedReasonFor(
        text: DocumentTextExtraction,
        format: DocumentFormat,
    ): ExtractionFailure =
        when (text) {
            DocumentTextExtraction.NoTextLayer -> ExtractionFailure.UnsupportedFormat("$format(no-text-layer)")
            is DocumentTextExtraction.Unreadable -> ExtractionFailure.UnsupportedFormat(text.detail)
            is DocumentTextExtraction.Extracted -> error("도달 불가 — Extracted 는 호출부에서 이미 걸러졌다")
        }

    /**
     * chunk 를 순서대로 호출하고 **첫 실패에서 멈춘다**(예산 방어 — 실패 뒤 chunk 는
     * LLM 을 부르지 않는다). `return` 하나로 접기 위해 조기 반환 대신 `break` + 결과
     * 변수를 쓴다(detekt `ReturnCount`).
     */
    private fun runChunks(
        chunks: List<DocumentChunk>,
        document: FetchedDocument,
    ): ExtractionAttempt {
        val items = mutableListOf<ExtractedRequirementItem>()
        val provenance = mutableListOf<ExtractionProvenance>()
        var anyAssertedAbsent = false
        var failure: ExtractionFailure? = null
        for (chunk in chunks) {
            if (failure != null) break
            when (val outcome = callChunk(chunk)) {
                is ChunkAttemptOutcome.Failure -> {
                    failure = outcome.reason
                }

                ChunkAttemptOutcome.AssertedAbsent -> {
                    anyAssertedAbsent = true
                }

                is ChunkAttemptOutcome.Items -> {
                    items += outcome.items
                    provenance += outcome.items.map { provenanceFor(document, chunk) }
                }
            }
        }
        return when {
            failure != null -> {
                ExtractionAttempt.Uncertain(failure)
            }

            items.isNotEmpty() -> {
                ExtractionAttempt.Extracted(ExtractedRequirements(items, assertedAbsent = false), provenance)
            }

            anyAssertedAbsent -> {
                ExtractionAttempt.Extracted(ExtractedRequirements(emptyList(), assertedAbsent = true), emptyList())
            }

            else -> {
                ExtractionAttempt.Uncertain(ExtractionFailure.EmptyResponse)
            }
        }
    }

    private fun provenanceFor(
        document: FetchedDocument,
        chunk: DocumentChunk,
    ): ExtractionProvenance =
        ExtractionProvenance(
            documentSha256 = document.sha256,
            sourceUrl = document.sourceUrl.value,
            evidence = ExtractionEvidenceLocation(chunk.index, chunk.charStart, chunk.charEnd),
            schemaVersion = REQUIREMENT_EXTRACTION_SCHEMA_VERSION,
            modelId = model.value,
            promptVersion = REQUIREMENT_EXTRACTION_PROMPT_VERSION,
            extractedAt = clock.instant(),
        )

    private fun callChunk(chunk: DocumentChunk): ChunkAttemptOutcome {
        val request = LlmRequest(promptText, chunk.text, policy.maxTokensPerCall)
        val resilientOutcome =
            callWithResilience(circuitBreaker, timeLimiter, callExecutor) { llmClient.complete(request) }
        return when (val resilient = resilientOutcome) {
            is ResilientCallOutcome.Success -> {
                interpretResponse(resilient.response)
            }

            ResilientCallOutcome.BreakerOpen -> {
                ChunkAttemptOutcome.Failure(ExtractionFailure.BreakerOpen)
            }

            ResilientCallOutcome.TimedOut -> {
                ChunkAttemptOutcome.Failure(ExtractionFailure.Timeout)
            }

            is ResilientCallOutcome.Failed -> {
                ChunkAttemptOutcome.Failure(ExtractionFailure.TransportFailed(resilient.failure.detail))
            }
        }
    }

    private fun interpretResponse(response: LlmResponse): ChunkAttemptOutcome {
        if (response.rawText.isBlank()) return ChunkAttemptOutcome.Failure(ExtractionFailure.EmptyResponse)
        return when (val validation = schemaValidator.validate(response.rawText)) {
            is SchemaValidationOutcome.Valid -> {
                parseValidatedNode(validation.node)
            }

            is SchemaValidationOutcome.Invalid -> {
                ChunkAttemptOutcome.Failure(
                    ExtractionFailure.SchemaViolation(REQUIREMENT_EXTRACTION_SCHEMA_VERSION, validation.messages),
                )
            }

            is SchemaValidationOutcome.MalformedJson -> {
                ChunkAttemptOutcome.Failure(
                    ExtractionFailure.SchemaViolation(REQUIREMENT_EXTRACTION_SCHEMA_VERSION, listOf(validation.detail)),
                )
            }
        }
    }

    private fun parseValidatedNode(node: JsonNode): ChunkAttemptOutcome =
        try {
            if (node.path("assertedAbsent").asBoolean(false)) {
                ChunkAttemptOutcome.AssertedAbsent
            } else {
                ChunkAttemptOutcome.Items(node.path("items").map(::parseItem))
            }
        } catch (malformed: IllegalArgumentException) {
            ChunkAttemptOutcome.Failure(
                ExtractionFailure.SchemaViolation(
                    REQUIREMENT_EXTRACTION_SCHEMA_VERSION,
                    listOf(malformed.message ?: "parse failure"),
                ),
            )
        }

    private fun parseItem(node: JsonNode): ExtractedRequirementItem {
        val groupNoNode = node.path("groupNo")
        val groupNo = if (groupNoNode.isTextual) ExtractedGroupNo(groupNoNode.asText()) else null
        val sourceField =
            if (node.path("sourceField").asText() == "PERMITTED_INDUSTRY_LIST") {
                ExtractedSourceField.PermittedIndustryList
            } else {
                ExtractedSourceField.LicenseLimitName
            }
        val evidenceNode = node.path("evidence")
        return ExtractedRequirementItem(
            groupNo = groupNo,
            serialNo = ExtractedSerialNo(node.path("serialNo").asText()),
            sourceField = sourceField,
            licenseNames = node.path("licenseNames").map { it.asText() },
            evidence =
                ExtractionEvidenceSpan(evidenceNode.path("charStart").asInt(), evidenceNode.path("charEnd").asInt()),
        )
    }
}

/**
 * 추출 엔진 조립 — **`internal`**이다(verifier r1 F-1). 이 함수가 이전에는 public 이라
 * `LlmRequirementExtractionPortAdapter`(당시 public)와 결합해 `WatchGatedExtractor` 없이도
 * adapters 밖(app test)에서 감시 게이트를 거치지 않고 실제 추출 엔진을 조립할 수 있었다
 * (probe A, 컴파일 성공 실측 — scope.md ②·설계 검토 (2) 우회 (8) 위반). 프로덕션 배선의
 * 유일한 공개 진입점은 [createWatchGatedExtractor](`WatchGatedExtractor.kt`)뿐이다.
 */
internal fun createRequirementExtractionEngine(
    llmClient: LlmClient,
    schemaValidator: RequirementSchemaValidator,
    promptText: String,
    model: ModelId,
    policy: ExtractionPolicyData,
    clock: Clock,
    callExecutor: ExecutorService,
): RequirementExtractionEngine =
    HttpLlmRequirementExtractor(llmClient, schemaValidator, promptText, model, policy, clock, callExecutor)

/**
 * domain port(`RequirementExtractionPort`, ADR 0005 D-10.1) 구현 — [RequirementExtractionEngine]
 * 의 풍부한 [ExtractionAttempt]를 port 의 거친 이분법([ExtractionOutcome])으로 투영만
 * 한다(D-3C-3 — 세부 사유는 port 밖 [ExtractionFailure]에 남는다). 구현 인터페이스 하나뿐
 * (`typeShapeGate` 래칫). **`internal`**이다(verifier r1 F-1) — 이 port 구현은 `WatchVerdict`
 * 를 인자로 받지 않으므로(ADR 0006 D-4, 도메인 port 시그니처 제약) public 이면 그 자체가
 * 게이트 없는 진입점이 된다. 이 slice 는 이 어댑터의 공개 factory 를 두지 않는다 — 이
 * 구체 구현으로 `RequirementExtractionPort` 가 필요해지면(4B) 그 배선을 만들 때 함께
 * 재검토한다.
 */
internal class LlmRequirementExtractionPortAdapter(
    private val engine: RequirementExtractionEngine,
) : RequirementExtractionPort {
    override fun extract(document: FetchedDocument): ExtractionOutcome =
        when (val attempt = engine.extractDetailed(document)) {
            is ExtractionAttempt.Extracted -> ExtractionOutcome.Extracted(attempt.requirements)
            is ExtractionAttempt.Uncertain -> ExtractionOutcome.Uncertain
        }
}
