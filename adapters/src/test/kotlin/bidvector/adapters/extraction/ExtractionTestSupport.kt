package bidvector.adapters.extraction

import bidvector.adapters.koneps.resolvedCollectionPolicy
import bidvector.procurement.AttachmentUrl
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * test 전용 좁은 정책값(3B `testKonepsHttpPolicy` 관례) — timeout 을 짧게 둬 timeout
 * 시나리오가 초 단위로 끝나게 한다.
 */
fun testExtractionPolicy(
    maxCallsPerDocument: Int = 5,
    chunkChars: Int = 40,
    callTimeout: Duration = Duration.ofMillis(300),
): ExtractionPolicyData =
    ExtractionPolicyData(
        chunkChars = chunkChars,
        maxChunksPerDocument = maxCallsPerDocument,
        maxCallsPerDocument = maxCallsPerDocument,
        maxTokensPerCall = 500,
        callTimeout = callTimeout,
        breakerFailureRateThresholdPercent = 50,
        breakerSlidingWindowSize = 2,
        breakerWaitDurationInOpenState = Duration.ofSeconds(30),
        attachmentFetchLimits = AttachmentFetchLimitsPolicy(maxBytes = 1024 * 1024, timeout = Duration.ofSeconds(2)),
    )

/**
 * [AttachmentUrl]은 등재된 [bidvector.procurement.KonepsFieldContract]로만 만들 수 있다
 * (procurement `AttachmentDocumentPort.kt`). 실제 `ntceSpecDocUrl1` 계약 등재는
 * `OPEN-3C-ATTACHMENT-FIELD-CONTRACT`(3A `CollectionPolicy.kt` 편집은 이 slice 의
 * in_scope 밖)라, 이 test 는 **이미 등재된** `bidNtceNo` 계약을 절차 증명용 "게이트
 * 토큰"으로 재사용한다 — 값의 의미(공고번호)가 아니라 "procurement 가 발급한 계약이
 * 아니면 만들 수 없다"는 메커니즘만 본다.
 */
fun sampleAttachmentUrl(raw: String): AttachmentUrl {
    val contract =
        resolvedCollectionPolicy(CollectionReferenceDate(LocalDate.of(2026, 9, 8)))
            .fieldContracts
            .contractFor(RawKey("bidNtceNo"))!!
    val observation =
        RawNoticeObservation.of(mapOf(RawKey("bidNtceNo") to raw), SourceEndpoint.NOTICE_LIST, Instant.EPOCH)
    return AttachmentUrl.from(observation, contract)!!
}

fun testHttpClient(): HttpClient = HttpClient.newHttpClient()

fun testExecutor(): ExecutorService = Executors.newCachedThreadPool()

/** 스키마를 통과하는 최소 응답 — 항목 하나. */
fun validExtractionResponseJson(
    licenseNames: List<String> = listOf("전기공사업"),
    serialNo: String = "001",
    groupNo: String? = "01",
): String {
    val names = licenseNames.joinToString(",") { "\"$it\"" }
    val group = if (groupNo == null) "null" else "\"$groupNo\""
    return """
        {"assertedAbsent": false, "items": [
          {"groupNo": $group, "serialNo": "$serialNo", "sourceField": "LICENSE_LIMIT_NAME",
           "licenseNames": [$names], "evidence": {"charStart": 0, "charEnd": 5}}
        ]}
        """.trimIndent()
}

const val ASSERTED_ABSENT_RESPONSE_JSON = """{"assertedAbsent": true, "items": []}"""

/** `additionalProperties:false` 를 어기는 응답(우회 (10) 방어 실증). */
const val SCHEMA_VIOLATING_RESPONSE_JSON = """{"assertedAbsent": false, "items": [], "extra": "field"}"""

internal fun buildExtractor(
    server: FakeLlmServer,
    policy: ExtractionPolicyData = testExtractionPolicy(),
    clock: Clock = Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC),
    executor: ExecutorService = testExecutor(),
): HttpLlmRequirementExtractor {
    val client =
        HttpLlmClient(
            endpoint = LlmEndpoint(server.endpointUri),
            model = ModelId("test-model"),
            credential = LlmCredential.of("test-credential"),
            httpClient = testHttpClient(),
            requestTimeout = policy.callTimeout,
        )
    return HttpLlmRequirementExtractor(
        llmClient = client,
        schemaValidator = RequirementSchemaValidator(),
        promptText = "extract",
        model = ModelId("test-model"),
        policy = policy,
        clock = clock,
        callExecutor = executor,
    )
}
