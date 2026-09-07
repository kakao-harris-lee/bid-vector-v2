package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

private fun accounting(
    received: Int,
    normalized: Int,
    duplicate: Int,
    dropped: Int,
    dropReasons: Map<CollectionDropReason, Int> = emptyMap(),
): CollectionAccounting =
    CollectionAccounting(
        received = received,
        normalized = normalized,
        duplicate = duplicate,
        dropped = dropped,
        dropReasons = dropReasons,
        sourceTotal = received,
        pagesFetched = 1,
        truncated = false,
        unknownFields = 0,
    )

/** ⑦ COL-06 — 회계 항등식은 생성자 불변식이고, `dropReasons` 어휘에 `Duplicate`가 없다(서로소 셈). */
class AccountingTest {
    @Test
    fun `received = normalized + duplicate + dropped 항등식을 만족하면 생성된다`() {
        val result =
            accounting(
                received = 10,
                normalized = 7,
                duplicate = 2,
                dropped = 1,
                dropReasons = mapOf(CollectionDropReason.CollectionMissingNoticeNumber to 1),
            )

        result.received shouldBe 10
    }

    @Test
    fun `항등식을 위반하면 생성 자체가 실패한다 — 우회 (2) copy 도 재검사된다`() {
        val ok =
            accounting(
                received = 10,
                normalized = 7,
                duplicate = 2,
                dropped = 1,
                dropReasons = mapOf(CollectionDropReason.CollectionMissingNoticeNumber to 1),
            )

        shouldThrow<IllegalArgumentException> { ok.copy(dropped = 5) }
    }

    @Test
    fun `dropReasons 합은 dropped 와 같아야 한다`() {
        shouldThrow<IllegalArgumentException> {
            accounting(
                received = 3,
                normalized = 1,
                duplicate = 1,
                dropped = 1,
                dropReasons = mapOf(CollectionDropReason.CollectionMissingNoticeNumber to 2),
            )
        }
    }

    @Test
    fun `음수 회계 값은 거부된다`() {
        shouldThrow<IllegalArgumentException> { accounting(received = -1, normalized = 0, duplicate = 0, dropped = 0) }
    }

    @Test
    fun `property — 음이 아닌 세 값의 합을 received 로 주면 항상 성립한다`() =
        runBlocking {
            checkAll(Arb.int(0..1000), Arb.int(0..1000), Arb.int(0..1000)) { normalized, duplicate, dropped ->
                val result =
                    accounting(
                        received = normalized + duplicate + dropped,
                        normalized = normalized,
                        duplicate = duplicate,
                        dropped = dropped,
                    )
                result.received shouldBe normalized + duplicate + dropped
            }
        }

    @Test
    fun `CollectionDropReason 어휘에는 Duplicate 변형이 없다 — 서로소 셈은 타입으로 보증된다`() {
        val allReasons: List<CollectionDropReason> =
            listOf(
                CollectionDropReason.CollectionMissingNoticeNumber,
                CollectionDropReason.CollectionUnknownField,
                CollectionDropReason.CollectionContractViolation(ContractViolationAxis.SCALE),
                CollectionDropReason.CollectionParseFailure(ParseFailureKind.NUMERIC),
            )

        allReasons.none { it.toString().contains("Duplicate") } shouldBe true
    }
}
