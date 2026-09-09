package bidvector.workflow.notification

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

private const val RAW_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

private val TEST_POLICY =
    NotificationDeliveryPolicyData(
        environmentModes =
            mapOf(
                RuntimeEnvironment.Production to DeliveryMode.Live,
                RuntimeEnvironment.Staging to DeliveryMode.Live,
                RuntimeEnvironment.Development to DeliveryMode.Live,
                RuntimeEnvironment.Test to DeliveryMode.Live,
            ),
        maskedSuffixLength = 4,
    )

/**
 * scope.md ③, 설계 검토 (1) — `MaskedTarget`은 `internal constructor`이고 유일한 생성이
 * [MaskedTarget.mask] factory다. fragment 인식 없이 끝 `maskedSuffixLength`자 외 전부를
 * 덮는다(조사 (c-5)). `toString()`도 마스킹 값이다 — 원문 accessor가 없다.
 */
class MaskedTargetTest {
    @Test
    fun `끝 4자만 남고 나머지는 별표로 덮인다`() {
        MaskedTarget.mask("012345678901", TEST_POLICY).toString() shouldBe "********8901"
    }

    @Test
    fun `길이가 suffix 이하면 전부 별표로 덮인다`() {
        MaskedTarget.mask("12", TEST_POLICY).toString() shouldBe "**"
    }

    @Test
    fun `길이가 suffix 와 같으면 전부 별표로 덮인다 — 경계값`() {
        MaskedTarget.mask("1234", TEST_POLICY).toString() shouldBe "****"
    }

    @Test
    fun `toString 은 마스킹 값이다`() {
        val masked = MaskedTarget.mask("opaquevalue1234", TEST_POLICY)

        masked.toString() shouldBe "***********1234"
    }

    @Test
    fun `길이가 suffix보다 길면 앞부분이 전부 별표로 덮이고 원문 앞부분을 담지 않는다 — property`() {
        runBlocking {
            checkAll(Arb.string(5..40, RAW_ALPHABET)) { raw ->
                val masked = MaskedTarget.mask(raw, TEST_POLICY).toString()
                val prefixLength = raw.length - TEST_POLICY.maskedSuffixLength

                masked.take(prefixLength) shouldBe "*".repeat(prefixLength)
                masked.takeLast(TEST_POLICY.maskedSuffixLength) shouldBe raw.takeLast(TEST_POLICY.maskedSuffixLength)
            }
        }
    }
}
