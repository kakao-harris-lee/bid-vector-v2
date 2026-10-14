package bidvector.adapters.retention

import bidvector.procurement.FieldConcept
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **개념 축의 분할 등식**(D-6B3-5 ①, privacy F-7) — 「어느 등재 개념이 업체 식별을 나르는가」의 열거
 * 자체에 게이트를 건다.
 *
 * 앞 판에는 이 게이트가 없었고, 그래서 셋째 운반자(`OPENING_COMPANY_INFO`)가 마스킹 집합 밖에 조용히
 * 남았다(vr R2-F1 · privacy F-6). 마스킹 test 들은 전부 선언된 개념에서 키를 끌어왔으므로 그 누락을
 * 볼 수 없었다 — **「선언한 것은 지운다」는 재고 「나르는 것을 다 선언했는가」는 재지 못하는** 모집단
 * 이었다. 이 분할이 그 모집단을 `FieldConcept` 전수로 바꾼다.
 */
class FieldConceptPiiAxisTest {
    private val maskingRule = RETENTION_TABLE_RULES.filterIsInstance<RetentionRule.MaskPii>().single()

    @Test
    fun `분류 둘은 FieldConcept 전수의 분할이다 — 새 개념은 분류 없이 빌드를 지나지 못한다`() {
        (COMPANY_IDENTITY_CONCEPTS + NON_PII_CONCEPTS) shouldBe FieldConcept.entries.toSet()
    }

    @Test
    fun `두 분류는 서로소다 — 한 개념이 양쪽에 있으면 지울지 남길지가 목록 순서에 달린다`() {
        COMPANY_IDENTITY_CONCEPTS.intersect(NON_PII_CONCEPTS).shouldBeEmpty()
    }

    /** 분할이 **공허하지 않다** — 업체 식별 쪽이 비면 위 두 등식은 「전부 비PII」로도 성립한다. */
    @Test
    fun `업체 식별 분류는 비어 있지 않다`() {
        COMPANY_IDENTITY_CONCEPTS.shouldNotBeEmpty()
    }

    /** **마스킹 집합 == 선언 집합.** 레지스트리가 개념을 따로 열거하면 둘이 갈리고, 갈린 쪽이 남는다. */
    @Test
    fun `마스킹 규칙의 개념 집합은 업체 식별 분류와 같다`() {
        maskingRule.maskedConcepts shouldBe COMPANY_IDENTITY_CONCEPTS
    }

    /**
     * 선언된 개념이 **실제로 키를 가진다** — 계약 없는 개념을 선언해 두면 지울 키가 0 이면서 위 등식은
     * 초록이다(`removedRawNames` 의 `require` 와 같은 축을 개념마다 다시 센다).
     */
    @Test
    fun `업체 식별 개념은 모두 계약 rawName 을 가진다 — 전 개정에서`() {
        val missing = COMPANY_IDENTITY_CONCEPTS.filter { rawNamesOfConcepts(setOf(it)).isEmpty() }

        missing.shouldBeEmpty()
    }

    /** 그 개념들의 rawName 이 **지울 집합**에 들어 있고 **남길 집합**에는 없다. */
    @Test
    fun `업체 식별 rawName 은 지울 집합에만 있고 남길 집합 밖이다`() {
        val removed = maskingRule.removedRawNames()
        removed shouldBe rawNamesOfConcepts(COMPANY_IDENTITY_CONCEPTS)

        removed.shouldNotBeEmpty()
        removed.intersect(maskingRule.keptRawNames()).shouldBeEmpty()
    }

    /**
     * 남길 집합은 **계약 전수 − 지울 키**다(행이 무엇을 담았는지와 무관하다) — 그래서 등재되지 않은
     * 키(담당자 키 넷 꼴)는 애초에 남길 집합에 들어올 수 없다.
     */
    @Test
    fun `남길 집합은 계약 rawName 전수에서 지울 키를 뺀 것이다`() {
        maskingRule.keptRawNames() shouldBe (allContractRawNames() - maskingRule.removedRawNames())

        OFFICER_FIXTURE_KEYS.keys.forEach { officerKey ->
            (officerKey in maskingRule.keptRawNames()) shouldBe false
        }
    }
}
