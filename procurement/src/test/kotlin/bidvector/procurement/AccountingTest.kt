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

    // 블록 본문 + 명시 반환 타입 없음(= Unit 고정)이어야 JUnit Jupiter 가 discover 한다 —
    // `= runBlocking { checkAll(…) }` 식 본문은 반환형이 `PropertyContext`로 추론돼 조용히
    // 건너뛴다(`test-discovery-guard`, MoneyTest.kt `P-3b`와 같은 회귀 형태, verifier r1 F-1).
    @Test
    fun `property — 음이 아닌 세 값의 합을 received 로 주면 항상 성립한다`() {
        runBlocking {
            checkAll(Arb.int(0..1000), Arb.int(0..1000), Arb.int(0..1000)) { normalized, duplicate, dropped ->
                val dropReasons: Map<CollectionDropReason, Int> =
                    if (dropped > 0) {
                        mapOf(CollectionDropReason.CollectionMissingNoticeNumber to dropped)
                    } else {
                        emptyMap()
                    }
                val result =
                    accounting(
                        received = normalized + duplicate + dropped,
                        normalized = normalized,
                        duplicate = duplicate,
                        dropped = dropped,
                        dropReasons = dropReasons,
                    )
                result.received shouldBe normalized + duplicate + dropped
            }
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

    // M3/3B 좁은 확장(verifier r1 H-3) — truncated 와 truncationCause 결합 불변식.
    @Test
    fun `truncated=true 인데 truncationCause 가 없으면 생성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            accounting(received = 1, normalized = 1, duplicate = 0, dropped = 0).copy(truncated = true)
        }
    }

    @Test
    fun `truncated=false 인데 truncationCause 가 있으면 생성이 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            accounting(received = 1, normalized = 1, duplicate = 0, dropped = 0)
                .copy(truncationCause = TruncationCause.MaxPages)
        }
    }

    @Test
    fun `truncated=true 와 truncationCause 가 짝지어지면 생성된다 — 사유별로 값이 갈린다`() {
        val causes =
            listOf(
                TruncationCause.MaxPages,
                TruncationCause.RepeatedPage,
                TruncationCause.QuotaExhausted,
                TruncationCause.Timeout,
                TruncationCause.TransportFailure,
                TruncationCause.ServerError,
                TruncationCause.NotRetryable,
                TruncationCause.InputError,
                TruncationCause.Unclassified,
                TruncationCause.StructureFailure,
                TruncationCause.SelfThrottled,
            )

        val results =
            causes.map { cause ->
                accounting(received = 0, normalized = 0, duplicate = 0, dropped = 0)
                    .copy(truncated = true, truncationCause = cause)
            }

        results.map { it.truncationCause }.toSet().size shouldBe causes.size
    }

    @Test
    fun `quotaExceeded·backoffSkipped 는 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            accounting(received = 0, normalized = 0, duplicate = 0, dropped = 0).copy(quotaExceeded = -1)
        }
        shouldThrow<IllegalArgumentException> {
            accounting(received = 0, normalized = 0, duplicate = 0, dropped = 0).copy(backoffSkipped = -1)
        }
    }

    // verifier r1 F-3·F-8(운영자 승인 2026-09-08, 3A Accounting.kt 좁은 확장) — 개찰 축
    // masking 실패를 unknownFields 와 분리해 센다.
    @Test
    fun `maskingFailures 는 기본값 0 이고 dropReasons_dropped 항등식 밖이다`() {
        val base = accounting(received = 3, normalized = 3, duplicate = 0, dropped = 0)

        base.maskingFailures shouldBe 0
        // dropped=0 인 채로 maskingFailures 를 올려도 COL-06 항등식(received=normalized+
        // duplicate+dropped)은 그대로 성립한다 — 필드 단위 실패가 항목 단위 drop 축을
        // 건드리지 않는다는 것을 생성 성공으로 증명한다.
        val withMaskingFailures = base.copy(maskingFailures = 2)
        withMaskingFailures.maskingFailures shouldBe 2
        withMaskingFailures.dropped shouldBe 0
    }

    @Test
    fun `maskingFailures 는 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            accounting(received = 0, normalized = 0, duplicate = 0, dropped = 0).copy(maskingFailures = -1)
        }
    }

    // verifier r2 G-1(운영자 승인 2026-09-08, 3A Accounting.kt 좁은 확장) — 행 식별자가
    // 선언됐는데 부재·공백이라 dedup 을 적용하지 못한 건수를 별도 축으로 낸다.
    @Test
    fun `rowIdentifierIndeterminate 는 기본값 0 이고 dropReasons_dropped 항등식 밖이다`() {
        val base = accounting(received = 3, normalized = 3, duplicate = 0, dropped = 0)

        base.rowIdentifierIndeterminate shouldBe 0
        val withIndeterminate = base.copy(rowIdentifierIndeterminate = 2)
        withIndeterminate.rowIdentifierIndeterminate shouldBe 2
        withIndeterminate.dropped shouldBe 0
    }

    @Test
    fun `rowIdentifierIndeterminate 는 음수를 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            accounting(received = 0, normalized = 0, duplicate = 0, dropped = 0).copy(rowIdentifierIndeterminate = -1)
        }
    }
}
