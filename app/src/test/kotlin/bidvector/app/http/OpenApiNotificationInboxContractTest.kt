package bidvector.app.http

import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationInboxLimits
import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C③ — 알림함 operation 셋의 **문서 자신**을 잰다(HTTP 호출 없음). 7C① 의 계약 test 와
 * 같은 모양이고, 여기서 닫는 것은 「문서와 구현이 같은 것을 말한다」의 문서 쪽 절반이다.
 *
 * 응답 키 집합은 **Kotlin DTO 의 생성자 속성 전수**와 맞댄다 — 문서에 키를 더하고 DTO 에 안
 * 더하면(또는 반대로) 붉다. 어휘 둘(종류·detail)도 Kotlin enum 전수와 맞댄다: 저장 층의 CHECK
 * 와 enum 이 같다는 것은 어댑터 test 가 이미 잠그므로, 이 등식이 서면 **세 자리가 한 집합**이다
 * (문서 · Kotlin · DB).
 */
class OpenApiNotificationInboxContractTest {
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    @Test
    fun `목록 operation 의 상태 코드 집합이 계약과 일치한다`() {
        spec.responseStatusCodes(LIST_PATH, "get") shouldBe setOf("200", "400", "401", "500")
    }

    @Test
    fun `읽음 처리 두 operation 의 상태 코드 집합이 같다 — 같은 규약이다`() {
        val single = spec.responseStatusCodes(READ_PATH, "post")
        val group = spec.responseStatusCodes(READ_NOTICE_PATH, "post")

        single shouldBe setOf("200", "400", "401", "415", "500")
        group shouldBe single
    }

    /**
     * **계정 전용의 문서 쪽 선언** — 세 operation 전부 `operatorCredential` 대안을 적지 **않는다**.
     * 적으면 문서가 구현보다 **넓어지고**(실제로는 401) 호출자가 되는 줄 알고 401 을 받는다.
     * 전역 기본 요구에는 자격 헤더 대안이 있으므로, 각 operation 이 자기 `security` 로 그 기본을
     * **덮어야** 한다 — 선언을 빼면 기본을 물려받아 문서가 거짓이 된다.
     */
    @Test
    fun `세 operation 은 자격 헤더 대안을 적지 않는다 — 계정 전용이다`() {
        val offenders =
            OPERATIONS
                .filter { (path, method) -> CREDENTIAL_SCHEME in securitySchemesOf(path, method).flatten() }
                .map { (path, method) -> "$method $path" }

        offenders.shouldBeEmpty()
    }

    @Test
    fun `목록은 쿠키 대안만 적고 CSRF 를 요구하지 않는다 — 읽기 메서드다`() {
        securitySchemesOf(LIST_PATH, "get") shouldBe listOf(setOf("browserSession"))
    }

    /** 상태를 바꾸는 둘은 쿠키 단독 대안이 **없다** — CSRF 가 묶여 있다. */
    @Test
    fun `읽음 처리 둘은 쿠키 경로에 CSRF 를 묶어 선언한다`() {
        listOf(READ_PATH, READ_NOTICE_PATH).forEach { path ->
            securitySchemesOf(path, "post") shouldBe listOf(setOf("browserSession", "operatorCsrf"))
        }
    }

    @Test
    fun `항목 응답의 키 집합이 Kotlin DTO 와 완전히 일치한다 — 여분 필드가 생기면 깨진다`() {
        spec.propertyKeys("NotificationItem") shouldBe ITEM_KEYS
        // nullable 칸도 키 자체는 남는다(기존 관례) — 그래서 required 가 전수다.
        spec.requiredKeys("NotificationItem") shouldBe ITEM_KEYS
    }

    @Test
    fun `읽음 처리 요청·응답의 키 집합이 계약과 일치한다`() {
        spec.propertyKeys("MarkNotificationReadRequest") shouldBe
            setOf("kind", "noticeNumber", "noticeRound")
        spec.propertyKeys("MarkNoticeNotificationsReadRequest") shouldBe
            setOf("noticeNumber", "noticeRound")
        spec.propertyKeys("MarkNotificationReadResponse") shouldBe setOf("markedCount", "unreadCount")
    }

    /**
     * **요청에 계정·시각 칸이 없다**(설계 검토 (2b)). 있으면 요청자가 소유 경계와 「언제
     * 읽었는가」를 말할 수 있다. 키 집합 등식이 이미 막지만, 그 등식은 문서와 DTO 를 **함께**
     * 바꾸면 지나간다 — 이 단언은 그 편집도 어휘 수준에서 막는다.
     */
    @Test
    fun `읽음 처리 요청에 계정·시각·읽음 어휘가 없다`() {
        val forbidden = listOf("account", "operator", "at", "time", "read", "user", "subject")
        val declared =
            spec.propertyKeys("MarkNotificationReadRequest") +
                spec.propertyKeys("MarkNoticeNotificationsReadRequest")

        declared.filter { key -> forbidden.any { word -> key.lowercase().contains(word) } }.shouldBeEmpty()
    }

    /**
     * **항목 응답에 운영자 PII 어휘가 없다.** 알림함 표에는 자유 문자열 칸이 아예 없고(어댑터
     * 쪽 카탈로그 등식이 그것을 잠근다) 이 응답의 자유 문면은 합쳐 온 공고 칸뿐이다. 이 단언은
     * 그 사실이 문서에서도 유지되는지를 어휘로 본다.
     */
    @Test
    fun `항목 응답에 사업자·담당자·연락 어휘가 없다`() {
        val forbidden =
            listOf("business", "bizno", "company", "owner", "manager", "officer", "contact", "phone", "email", "tel")

        val declared = spec.propertyKeys("NotificationItem")

        declared.filter { key -> forbidden.any { word -> key.lowercase().contains(word) } }.shouldBeEmpty()
    }

    @Test
    fun `종류 어휘가 Kotlin enum 전수와 같다 — 문서·Kotlin·DB 가 한 집합이다`() {
        enumOf("NotificationItem", "kind") shouldContainExactlyInAnyOrder
            NotificationKind.entries.map { it.name }
        enumOf("MarkNotificationReadRequest", "kind") shouldContainExactlyInAnyOrder
            NotificationKind.entries.map { it.name }
    }

    @Test
    fun `detail 어휘가 Kotlin enum 전수와 같다`() {
        enumOf("NotificationItem", "detail") shouldContainExactlyInAnyOrder
            NotificationDetail.entries.map { it.name }
    }

    @Test
    fun `선언된 페이지 한계가 Kotlin 상수와 같다 — 상한이 두 자리에서 갈라지지 않는다`() {
        val size = queryParameterSchema(LIST_PATH, "size")

        size["maximum"] shouldBe NotificationInboxLimits.MAX_PAGE_SIZE
        size["default"] shouldBe NotificationInboxLimits.DEFAULT_PAGE_SIZE
        size["minimum"] shouldBe 1
        queryParameterSchema(LIST_PATH, "page")["minimum"] shouldBe 0
    }

    @Test
    fun `문서가 든 질의 매개변수 전수가 구현이 읽는 이름과 같다`() {
        queryParameterNames(LIST_PATH) shouldContainExactlyInAnyOrder listOf("page", "size", "unreadOnly")
    }

    @Test
    fun `200 응답이 선언한 헤더 둘이 구현의 헤더 이름과 같다`() {
        responseHeaderNames(LIST_PATH, "get", "200") shouldBe
            setOf(NOTIFICATION_TOTAL_COUNT_HEADER, NOTIFICATION_UNREAD_COUNT_HEADER)
    }

    // ---------------------------------------------------------------------
    // spec 판독기 — 7C① 의 private helper 와 같은 모양이다(그쪽은 `private` 이라 공유 불가).
    // ---------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun operationOf(
        path: String,
        method: String,
    ): Map<String, Any?> {
        val pathItem = (spec["paths"] as Map<String, Any?>)[path] as Map<String, Any?>
        return pathItem[method] as Map<String, Any?>
    }

    /** operation 의 `security` 요구 목록 — 각 요구가 든 스킴 이름 집합들이다. */
    @Suppress("UNCHECKED_CAST")
    private fun securitySchemesOf(
        path: String,
        method: String,
    ): List<Set<String>> {
        val requirements = operationOf(path, method)["security"] as? List<Map<String, Any?>>
        checkNotNull(requirements) { "$method $path 가 security 를 선언하지 않았다 — 전역 기본을 물려받는다" }
        return requirements.map { it.keys }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumOf(
        schemaName: String,
        property: String,
    ): List<String> {
        val properties = spec.schema(schemaName)["properties"] as Map<String, Any?>
        val definition = properties[property] as Map<String, Any?>
        return definition["enum"] as List<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun queryParameters(path: String): List<Map<String, Any?>> =
        (operationOf(path, "get")["parameters"] as? List<Map<String, Any?>>).orEmpty()

    private fun queryParameterNames(path: String): List<String> = queryParameters(path).map { it["name"] as String }

    @Suppress("UNCHECKED_CAST")
    private fun queryParameterSchema(
        path: String,
        name: String,
    ): Map<String, Any?> = queryParameters(path).single { it["name"] == name }["schema"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun responseHeaderNames(
        path: String,
        method: String,
        status: String,
    ): Set<String> {
        val responses = operationOf(path, method)["responses"] as Map<String, Any?>
        val response = responses[status] as Map<String, Any?>
        return (response["headers"] as Map<String, Any?>).keys
    }

    private companion object {
        const val LIST_PATH = "/api/notifications"
        const val READ_PATH = "/api/notifications/read"
        const val READ_NOTICE_PATH = "/api/notifications/read-notice"
        const val CREDENTIAL_SCHEME = "operatorCredential"

        val OPERATIONS =
            listOf(LIST_PATH to "get", READ_PATH to "post", READ_NOTICE_PATH to "post")

        /**
         * `NotificationItemResponse` 의 속성 전수. **손 목록이고 그것이 요점이다** — DTO 에 칸을
         * 더하면 문서 쪽 등식이 먼저 붉고, 이 목록까지 고쳐야 통과한다(세 자리를 모두 지나야
         * 한다). 반사로 훑으면 목록이 자동으로 자라 그 신호가 사라진다.
         */
        val ITEM_KEYS =
            setOf(
                "kind",
                "detail",
                "noticeNumber",
                "noticeRound",
                "occurredAt",
                "read",
                "readAt",
                "noticeItemCount",
                "noticeUnreadCount",
                "noticeKnown",
                "noticeTitle",
                "noticeStatus",
                "noticeDeadlineAt",
            )
    }
}
