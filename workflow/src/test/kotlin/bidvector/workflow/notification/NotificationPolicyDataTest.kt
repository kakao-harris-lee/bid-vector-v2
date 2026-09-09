package bidvector.workflow.notification

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * scope.md ⑦ — 환경 매핑은 전사상(전 값을 덮는다)이고 마스킹 suffix는 1 이상이다.
 * 환경이 하나라도 빠지면 「모르는 환경은 보낸다」 경로가 생기므로 생성 자체를 거부한다
 * (우회 (6)).
 */
class NotificationPolicyDataTest {
    /**
     * verifier r1 M-2 시정 — 손으로 만든 map(아래 test들)만으로는 출하 값
     * `NOTIFICATION_DELIVERY_POLICY`(`NotificationDeliveryPolicyData.kt`) 자신이 정의
     * 자리 외 어디서도 참조되지 않아, 그 값을 담은 파일의 JVM 클래스 초기화가 트리거되지
     * 않았다(초기화가 안 되면 `init`의 `require`도 안 돈다) — 출하 map에서 환경 하나를
     * 지워도 `:workflow:test`가 초록이었던 이유. 이 test는 출하 val을 직접 `resolve`해
     * 그 값 자체가 전사상·suffix≥1 불변식을 만족함을 단언한다.
     */
    @Test
    fun `출하 정책 NOTIFICATION_DELIVERY_POLICY 는 환경 전 값을 덮고 suffix가 1 이상이다`() {
        val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.now())

        resolution.shouldBeInstanceOf<Resolution.Resolved<NotificationDeliveryPolicyData>>()
        val data = resolution.value
        data.environmentModes.keys shouldBe RuntimeEnvironment.entries.toSet()
        (data.maskedSuffixLength >= 1) shouldBe true
    }

    @Test
    fun `환경 전 값이 채워지면 생성에 성공한다`() {
        val data =
            NotificationDeliveryPolicyData(
                environmentModes =
                    mapOf(
                        RuntimeEnvironment.Production to DeliveryMode.Live,
                        RuntimeEnvironment.Staging to DeliveryMode.DryRun,
                        RuntimeEnvironment.Development to DeliveryMode.DryRun,
                        RuntimeEnvironment.Test to DeliveryMode.Blocked,
                    ),
                maskedSuffixLength = 4,
            )

        data.environmentModes.keys shouldBe RuntimeEnvironment.entries.toSet()
    }

    @Test
    fun `환경 하나라도 빠지면 생성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            NotificationDeliveryPolicyData(
                environmentModes =
                    mapOf(
                        RuntimeEnvironment.Production to DeliveryMode.Live,
                        RuntimeEnvironment.Staging to DeliveryMode.DryRun,
                        RuntimeEnvironment.Development to DeliveryMode.DryRun,
                    ),
                maskedSuffixLength = 4,
            )
        }
    }

    @Test
    fun `suffixLength 가 0 이면 생성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            NotificationDeliveryPolicyData(
                environmentModes = RuntimeEnvironment.entries.associateWith { DeliveryMode.Live },
                maskedSuffixLength = 0,
            )
        }
    }

    @Test
    fun `suffixLength 가 음수면 생성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            NotificationDeliveryPolicyData(
                environmentModes = RuntimeEnvironment.entries.associateWith { DeliveryMode.Live },
                maskedSuffixLength = -1,
            )
        }
    }
}
