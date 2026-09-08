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
    maxChunksPerDocument: Int = maxCallsPerDocument,
    chunkChars: Int = 40,
    callTimeout: Duration = Duration.ofMillis(300),
    // verifier r1 F-2 — httpRequestTimeout 은 callTimeout 보다 항상 커야 한다(init 강제).
    // 고정폭(+5s)으로 둬 커스텀 callTimeout 값과 무관하게 항상 성립한다.
    httpRequestTimeout: Duration = callTimeout.plusSeconds(5),
): ExtractionPolicyData =
    ExtractionPolicyData(
        chunkChars = chunkChars,
        maxChunksPerDocument = maxChunksPerDocument,
        maxCallsPerDocument = maxCallsPerDocument,
        maxTokensPerCall = 500,
        callTimeout = callTimeout,
        httpRequestTimeout = httpRequestTimeout,
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

/**
 * **위반이 둘**인 응답(미등록 최상위 키 + `assertedAbsent:false`인데 `items` 가 비어
 * `else: minItems 1` 위반) — `additionalProperties` 단독 위반 test 로는 쓰지 않는다
 * (verifier r1 F-4(a) — 이 fixture 만 쓰면 `additionalProperties:false` 를 지워도
 * 두 번째 위반 때문에 여전히 Invalid 라 test 가 그 규칙의 강제를 증명하지 못한다).
 */
const val SCHEMA_VIOLATING_RESPONSE_JSON = """{"assertedAbsent": false, "items": [], "extra": "field"}"""

/**
 * `additionalProperties:false` **단독** 위반 — 그 밖은 전부 유효(항목 하나가 스키마의
 * 다른 모든 요건을 만족)하다. `additionalProperties` 를 `true` 로 바꾸면 이 응답은
 * Valid 로 뒤집힌다(F-4(a) 변이 사멸 조건).
 */
const val ADDITIONAL_PROPERTY_ONLY_VIOLATION_JSON =
    """
    {"assertedAbsent": false, "extra": "field", "items": [
      {"groupNo": "01", "serialNo": "001", "sourceField": "LICENSE_LIMIT_NAME",
       "licenseNames": ["전기공사업"], "evidence": {"charStart": 0, "charEnd": 5}}
    ]}
    """

/** scope ⑧ 「빈 본문」 시나리오(F-5(b)) — LLM 응답 자체가 빈 문자열. */
const val EMPTY_RESPONSE_BODY = ""

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
            requestTimeout = policy.httpRequestTimeout,
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
