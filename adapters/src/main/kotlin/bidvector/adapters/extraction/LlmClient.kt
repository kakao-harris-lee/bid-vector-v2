package bidvector.adapters.extraction

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** LLM provider endpoint — 코드 상수가 아니라 생성자 주입(위협 모델 방어 (d)). */
data class LlmEndpoint(
    val uri: URI,
)

/** 모델 식별자 — 코드 상수가 아니라 생성자 주입(위협 모델 방어 (d), grep test 대상). */
data class ModelId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ModelId는 빈 문자열일 수 없다" }
    }
}

/**
 * LLM 호출 요청 — chunk 원문 + prompt 텍스트를 함께 나른다. `toString`을 가리지 않는다
 * (요청은 우리가 만든 prompt 이지 응답이 아니다 — 로그 유출 위협은 **응답**에 있다).
 */
data class LlmRequest(
    val promptText: String,
    val chunkText: String,
    val maxTokens: Int,
)

/**
 * LLM 응답 원문 — **`toString`을 가린다**(scope.md ⑥, 위협 모델 방어 (e)). 원문 접근은
 * [rawText] 프로퍼티 하나뿐이고, 예외·로그 메시지는 이 값을 담지 않는다(evidence 규격의
 * secret/원문 스캔 대상).
 */
class LlmResponse(
    val rawText: String,
) {
    override fun toString(): String = "LlmResponse(rawText=<redacted, ${rawText.length} chars>)"

    override fun equals(other: Any?): Boolean = other is LlmResponse && rawText == other.rawText

    override fun hashCode(): Int = rawText.hashCode()
}

/** 호출 실패 — 원문을 담지 않는다(방어 (e)). */
data class LlmCallFailure(
    val detail: String,
)

sealed interface LlmCallOutcome {
    data class Success(
        val response: LlmResponse,
    ) : LlmCallOutcome

    data class Failure(
        val failure: LlmCallFailure,
    ) : LlmCallOutcome
}

/**
 * LLM 호출 인터페이스 — **domain port 가 아니다**(도메인은 LLM 을 모른다). adapters 안
 * 추상화이고, resilience4j 데코레이터(`ResilientLlmCall.kt`)가 이 인터페이스를 감싼다.
 */
interface LlmClient {
    fun complete(request: LlmRequest): LlmCallOutcome
}

/**
 * HTTP 기반 LlmClient 구현(③) — provider/model/credential 은 전부 생성자 주입, 기본 인자가
 * 없다(위협 모델 방어 (d)). 실제 provider SDK 를 넣지 않는다 — JDK `HttpClient` 하나로
 * 충분한 최소 표면(POST + 본문 문자열)만 쓴다.
 */
class HttpLlmClient(
    private val endpoint: LlmEndpoint,
    private val model: ModelId,
    private val credential: LlmCredential,
    private val httpClient: HttpClient,
    private val requestTimeout: Duration,
) : LlmClient {
    override fun complete(request: LlmRequest): LlmCallOutcome {
        val body = LlmRequestBody.render(model, request)
        val httpRequest =
            HttpRequest
                .newBuilder(endpoint.uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${credential.urlSafeValue()}")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return try {
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            interpretResponse(response)
        } catch (timeout: java.net.http.HttpTimeoutException) {
            LlmCallOutcome.Failure(LlmCallFailure("timeout: ${timeout.javaClass.simpleName}"))
        } catch (io: java.io.IOException) {
            LlmCallOutcome.Failure(LlmCallFailure("transport failure: ${io.javaClass.simpleName}"))
        }
    }

    private fun interpretResponse(response: HttpResponse<String>): LlmCallOutcome =
        if (response.statusCode() in SUCCESS_RANGE) {
            LlmCallOutcome.Success(LlmResponse(response.body()))
        } else {
            LlmCallOutcome.Failure(LlmCallFailure("http status ${response.statusCode()}"))
        }

    private companion object {
        val SUCCESS_RANGE = 200..299
    }
}

/** provider 인증 값 — `toString`에 노출되지 않는다(3B `ServiceKey`와 같은 관례). */
class LlmCredential private constructor(
    private val raw: String,
) {
    fun urlSafeValue(): String = raw

    override fun toString(): String = "LlmCredential(***)"

    companion object {
        fun of(raw: String): LlmCredential {
            require(raw.isNotBlank()) { "LlmCredential은 빈 문자열일 수 없다" }
            return LlmCredential(raw)
        }
    }
}

/** 요청 본문 렌더링 — 최소 JSON 조립(새 JSON 라이브러리 없이, 3B `KonepsJson` 원칙과 같은 이유). */
private object LlmRequestBody {
    fun render(
        model: ModelId,
        request: LlmRequest,
    ): String {
        val prompt = escape(request.promptText + "\n\n" + request.chunkText)
        return """{"model":"${escape(model.value)}","max_tokens":${request.maxTokens},"input":"$prompt"}"""
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
