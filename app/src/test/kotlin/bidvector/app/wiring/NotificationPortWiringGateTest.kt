package bidvector.app.wiring

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * D-6A3-3 — app production 클래스가 참조하는 `NotificationRequestPort` 구현 집합은
 * `RecordingNotificationRequestPort`뿐이다(운영자 결정 2, dry-run 전용). `OutboxNotification
 * RequestPort`(workflow, outbox 커밋 경로)는 이 slice의 app 배선 어디에도 나타나지 않는다
 * — 우회 1 폐쇄. 구조 게이트(바이트코드 상수 풀의 두 후보 존재 여부, 문자열 grep 이 아니다).
 */
class NotificationPortWiringGateTest {
    @Test
    fun `app production 클래스가 참조하는 NotificationRequestPort 구현 집합은 RecordingNotificationRequestPort 뿐이다`() {
        val output = packageJavapOutput(File("build/classes/kotlin/main/bidvector/app"))
        referencedNotificationRequestPortImpls(output) shouldBe setOf("RecordingNotificationRequestPort")
    }

    /** 양성 대조 — OutboxNotificationRequestPort 참조를 심은 표본은 이 술어에 걸린다. */
    @Test
    fun `OutboxNotificationRequestPort 참조를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val synthetic =
            """
            // Lbidvector/workflow/evaluation/OutboxNotificationRequestPort;
            """.trimIndent()

        referencedNotificationRequestPortImpls(synthetic) shouldBe setOf("OutboxNotificationRequestPort")
    }

    @Test
    fun `양성 대조 대상 둘은 저장소의 실제 구현 클래스와 같은 정체다`() {
        // KNOWN_NOTIFICATION_REQUEST_PORT_IMPLS 의 내부 이름이 실 클래스 좌표와 어긋나면
        // 게이트가 항상 빈 집합을 내 공허하게 통과한다 — 상수 자체를 여기 한 번 더 고정한다.
        KNOWN_NOTIFICATION_REQUEST_PORT_IMPLS shouldBe
            mapOf(
                "RecordingNotificationRequestPort" to "bidvector/adapters/evaluation/RecordingNotificationRequestPort",
                "OutboxNotificationRequestPort" to "bidvector/workflow/evaluation/OutboxNotificationRequestPort",
            )
    }
}

/**
 * 저장소의 `NotificationRequestPort` production 구현 전수(간이 이름 → 내부 좌표) — 새 구현이
 * 생기면 이 상수도 더해야 그 구현의 존재를 이 게이트가 볼 수 있다(양성 대조가 이 상수 자체를
 * 실측으로 고정한다).
 */
private val KNOWN_NOTIFICATION_REQUEST_PORT_IMPLS =
    mapOf(
        "RecordingNotificationRequestPort" to "bidvector/adapters/evaluation/RecordingNotificationRequestPort",
        "OutboxNotificationRequestPort" to "bidvector/workflow/evaluation/OutboxNotificationRequestPort",
    )

private fun referencedNotificationRequestPortImpls(javapOutput: String): Set<String> =
    KNOWN_NOTIFICATION_REQUEST_PORT_IMPLS.filterValues { javapOutput.contains(it) }.keys
