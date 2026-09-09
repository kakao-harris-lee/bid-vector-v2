package bidvector.workflow.notification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * scope.md ⑦ — 환경 매핑은 전사상(전 값을 덮는다)이고 마스킹 suffix는 1 이상이다.
 * 환경이 하나라도 빠지면 「모르는 환경은 보낸다」 경로가 생기므로 생성 자체를 거부한다
 * (우회 (6)).
 */
class NotificationPolicyDataTest {
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
