package bidvector.adapters.snapshot

import bidvector.procurement.BusinessDivision
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleOutcome
import bidvector.workflow.collection.SampleStratum
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private fun keyOf(number: String): NoticeKeyHash = NoticeKeyHash.of(number, "000")

private fun outcomeOf(vararg entries: Pair<String, SampleStratum>): SampleOutcome =
    SampleOutcome(
        selected = entries.map { keyOf(it.first) },
        strata = emptyMap(),
        strataByKey = entries.associate { keyOf(it.first) to it.second },
    )

private val CONSTRUCTION_W07 = SampleStratum(BusinessDivision.CONSTRUCTION, "2026-W07")
private val SERVICE_W08 = SampleStratum(BusinessDivision.SERVICE, "2026-W08")

/** 표본 목록 파일(D-6G-39) — 형태와 **한 번만 확정된다**는 성질. */
class SampleListFileTest {
    @Test
    fun `같은 표본이면 같은 바이트다 — 후보 순서와 무관하게 해시 오름차순`() {
        val forward = outcomeOf("A-1" to CONSTRUCTION_W07, "A-2" to SERVICE_W08)
        val reversed = outcomeOf("A-2" to SERVICE_W08, "A-1" to CONSTRUCTION_W07)

        val text = SampleListFile.render(forward)

        text shouldBe SampleListFile.render(reversed)
        val hashes = text.trim().lines().map { it.substringBefore('\t') }
        hashes shouldContainExactly hashes.sorted()
        text.endsWith("\n") shouldBe true
    }

    @Test
    fun `쓴 것을 그대로 읽는다 — 키와 층 둘 다`() {
        val sample = outcomeOf("A-1" to CONSTRUCTION_W07, "A-2" to SERVICE_W08)

        val parsed = SampleListFile.parse(SampleListFile.render(sample))

        parsed.strataByKey shouldBe sample.strataByKey
    }

    /** 표본을 반쯤 읽는 것이 다시 뽑는 것보다 나쁘다 — 어느 쪽인지 모르는 채로 수집이 이어진다. */
    @Test
    fun `형태를 어긴 줄은 읽지 않는다`() {
        val good = SampleListFile.render(outcomeOf("A-1" to CONSTRUCTION_W07))

        shouldThrow<IllegalArgumentException> { SampleListFile.parse(good + "칸이\t둘뿐\n") }
        shouldThrow<IllegalArgumentException> { SampleListFile.parse(good + "not-hex\tSERVICE\t2026-W08\n") }
        shouldThrow<IllegalArgumentException> {
            SampleListFile.parse(good.replace("CONSTRUCTION", "건설"))
        }
        shouldThrow<IllegalArgumentException> { SampleListFile.parse("") }
    }
}

class FileSampleListLedgerTest {
    @TempDir
    lateinit var directory: Path

    private fun ledgerAt(name: String = "sample-list.tsv") = FileSampleListLedger(directory.resolve(name))

    @Test
    fun `확정 전에는 목록이 없다`() {
        ledgerAt().confirmed() shouldBe null
    }

    @Test
    fun `한 번 확정하면 두 번째 뽑기는 파일을 이기지 못한다`() {
        val ledger = ledgerAt()
        val first = ledger.confirm(outcomeOf("A-1" to CONSTRUCTION_W07))

        val second = ledger.confirm(outcomeOf("B-9" to SERVICE_W08))

        second shouldBe first
        ledger.confirmed() shouldBe first
    }

    @Test
    fun `해시는 파일 바이트의 것이다`() {
        val ledger = ledgerAt("nested/deeper/sample-list.tsv")
        ledger.confirm(outcomeOf("A-1" to CONSTRUCTION_W07, "A-2" to SERVICE_W08))

        val read = requireNotNull(ledger.read())

        read.sha256 shouldBe sha256Hex(Files.readString(directory.resolve("nested/deeper/sample-list.tsv")))
        read.size shouldBe 2
        // 행에서 역산한 값과 **다르다** — v3 의 순환 대조가 되살아나지 않는다는 뜻이다.
        read.sha256 shouldNotBe SampleOutcome(read.list.keys.toList(), emptyMap()).sampleListSha256
    }
}
