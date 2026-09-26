package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val EDIT_SESSIONS = "/api/strategy/edit-sessions"

/**
 * D-6A2b-12 — 편집 endpoint 여섯의 수작성 계약(D-6A1-8 단일 출처)과 **구현의 어휘**를
 * 대조한다. 여기 있는 것은 전부 **정적 등식**이다: 문서의 닫힌 어휘(enum)·필수 키·상태 코드
 * 선언이 구현의 표와 같은지. 서버를 띄우지 않는다.
 *
 * **HTTP 를 지나는 대조는 `ProductionHttpSurfaceTest` 로 옮겼다**(D-6A2b-19 ③) — 응답 키
 * 집합과 「문서의 경로·메서드 == 등록된 매핑」 등식은 모집단이 **출하 조립**이어야 의미가
 * 있고, test 전용 조립에서 재면 다른 패키지의 컨트롤러를 보지 못한다(verifier r1 F-1).
 *
 * 평탄성 순회(`OpenApiContractTest` 의 D-6A1-44)는 문서 전체를 훑으므로 이 slice 가 더한
 * 스키마도 그 test 가 이미 덮는다 — 여기서 다시 재지 않는다.
 */
@Suppress("UNCHECKED_CAST")
class OpenApiEditSessionContractTest {
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    /** `null` 은 어휘가 아니라 「값 없음」의 표기다(C-4) — 등식에서는 뺀다. */
    private fun enumOf(
        schema: String,
        property: String,
    ): Set<String> =
        (spec.schema(schema)["properties"] as Map<String, Any?>)
            .let { it[property] as Map<String, Any?> }
            .let { (it["enum"] as List<String?>).filterNotNull().toSet() }

    /**
     * 문서의 `field` enum ↔ 구현의 필드 어휘 전수가 **양방향** 등식이다. 코드에만 필드를
     * 더하면 HTTP 로 부를 수 없는 필드가 생기고, 문서에만 더하면 있지도 않은 필드를
     * 약속한다 — 둘 다 이 단언이 잡는다.
     */
    @Test
    fun `문서의 field enum 과 구현의 필드 어휘가 양방향으로 같다`() {
        enumOf("EditSessionValueRequest", "field") shouldBe EDITABLE_FIELD_TOKENS
    }

    /**
     * C-5a — `state` enum 도 같은 등식에 건다. 새 `EditSessionState` 하위 타입은 전수 `when`
     * 이 컴파일로 막지만 **문서는 조용히 낡는다**. 구현 쪽 집합은 상태 대표값 전수에 구현의
     * 표(`token()`)를 그대로 적용해 만든다 — 어휘의 출처가 하나다. 그 대표값이 하위 타입
     * 전부를 덮는지는 `EditableFieldVocabularyGateTest` 가 바이트코드에서 도출해 잠근다.
     */
    @Test
    fun `문서의 state enum 과 구현의 상태 어휘가 양방향으로 같다`() {
        enumOf("EditSessionResponse", "state") shouldBe EDIT_SESSION_STATE_SAMPLES.map { it.token() }.toSet()
    }

    @Test
    fun `편집 요청 스키마의 필수 키가 구현이 요구하는 것과 같다`() {
        spec.requiredKeys("EditSessionBeginRequest") shouldBe setOf("field")
        spec.requiredKeys("EditSessionValueRequest") shouldBe setOf("commandId", "field")
        spec.requiredKeys("EditSessionConfirmRequest") shouldBe setOf("commandId", "seenRevision")
        spec.requiredKeys("EditSessionEditRequest") shouldBe setOf("commandId", "field")
        spec.requiredKeys("EditSessionCancelRequest") shouldBe setOf("commandId")
    }

    @Test
    fun `각 편집 path 의 상태 코드 선언 집합이 기대값과 같다`() {
        val commandCodes = setOf("200", "400", "401", "404", "409", "415", "500")
        spec.responseStatusCodes(EDIT_SESSIONS, "post") shouldBe setOf("201", "400", "401", "415", "500")
        spec.responseStatusCodes("$EDIT_SESSIONS/{sessionId}", "get") shouldBe
            setOf("200", "400", "401", "404", "409", "500")
        listOf("value", "confirm", "edit", "cancel").forEach { command ->
            spec.responseStatusCodes("$EDIT_SESSIONS/{sessionId}/$command", "post") shouldBe commandCodes
        }
    }
}
