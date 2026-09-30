package bidvector.adapters.snapshot

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.NoticeKeyHash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * 시도 원장 줄의 **형태와 규칙** — 디렉터리가 지는 잠금·무결성 장부와 다른 관심사라 파일이 따로다
 * (production 도 `FileAttemptLedger.kt` 로 갈라져 있다).
 */
private val LEDGER_KEY = NoticeKeyHash.of("SYN-6G-0001", "000")
private val LEDGER_AT: Instant = Instant.parse("2026-09-24T01:00:00Z")

class FileAttemptLedgerTest {
    @TempDir
    lateinit var temp: Path

    private fun ledger(): FileAttemptLedger =
        FileAttemptLedger(Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME)) {}

    private fun attemptOf(
        outcome: AttemptOutcome,
        key: String? = LEDGER_KEY.value,
        kind: AttemptKind = AttemptKind.PENDING,
    ) = CollectionAttempt(
        key,
        SourceEndpoint.RESERVE_PRICE_DETAIL,
        outcome,
        LEDGER_AT,
        kind,
        walk = LEDGER_AT.takeIf { kind == AttemptKind.AXIS },
    )

    @Test
    fun `원장이 없으면 빈 이력이다 — 첫 실행이다`() {
        ledger().read().size shouldBe 0
    }

    @Test
    fun `쓴 것을 그대로 읽는다 — 결말 셋 전부`() {
        val ledger = ledger()
        val written =
            listOf(
                attemptOf(AttemptOutcome.Succeeded),
                attemptOf(AttemptOutcome.Failed("TIMEOUT"), kind = AttemptKind.HTTP),
                attemptOf(AttemptOutcome.Succeeded, key = null),
                attemptOf(AttemptOutcome.Empty, kind = AttemptKind.AXIS),
            )

        written.forEach(ledger::append)

        val history = ledger.read()
        history.size shouldBe written.size
        // 축 결말 줄만 이어 돌기에 든다. HTTP 줄은 상한만 센다(둘이 한 파일에 있어도 섞이지 않는다).
        history.settledAxes().getValue(LEDGER_KEY.value) shouldContainExactly listOf(SourceEndpoint.RESERVE_PRICE_DETAIL)
        history.spend(LEDGER_AT).total shouldBe 2
    }

    /**
     * 원장을 반쯤 읽는 것은 상한을 반만 세는 것이고, 그것은 상한이 없는 것보다 나쁘다. 관용은
     * **끝 줄 하나**뿐이다(D-6G-70) — 그 앞의 줄이 형태를 어기면 여전히 멈춘다.
     */
    @Test
    fun `가운데의 형태를 어긴 줄은 읽지 않는다`() {
        val ledger = ledger()
        ledger.append(attemptOf(AttemptOutcome.Succeeded))
        val file = Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(file, Files.readString(file) + "{\"axis\":\"RESERVE_PRICE_DETAIL\"}\n")

        // 그 뒤에 성한 줄을 하나 더 붙이면 어긴 줄은 **끝 줄이 아니다**.
        ledger.append(attemptOf(AttemptOutcome.Succeeded))

        shouldThrow<IllegalArgumentException> { ledger.read() }
    }

    /**
     * **D-6G2d-4 ⓑ (vr r5-t probe W7) — 걷기를 싣지 않은 AXIS 줄은 형태 위반이다.** 이 칸이 생기기
     * 전에 쓰인 `AXIS`/`SUCCEEDED` 줄은 앞 판에서 `walk = null` 로 읽혀 **빈 응답과 같은 값**이 됐다:
     * 그 축의 원문은 전부 버려지면서 축은 **완료**로 세어져, 축이 통째로 빠진 완료 행이 나오고
     * `incomplete_axis` 는 0 이었다. 「모름」과 「빈 응답」을 같은 값으로 접지 않는다 — 모르는 줄은
     * 읽지 않는다(fail-closed). 빈 응답은 결말 어휘(`EMPTY`)가 말하고 걷기는 언제나 실린다.
     */
    @Test
    fun `걷기를 싣지 않은 AXIS 줄은 읽지 않는다`() {
        val ledger = ledger()
        Files.writeString(ledgerFile(), axisLineWithoutWalk("\"walk\":null"))

        shouldThrow<IllegalArgumentException> { ledger.read() }
    }

    /** 같은 이유로 **칸이 아예 없는** 옛 줄도 읽지 않는다 — 부재와 명시 null 을 함께 거부한다. */
    @Test
    fun `걷기 칸이 없는 옛 AXIS 줄도 읽지 않는다`() {
        val ledger = ledger()
        Files.writeString(ledgerFile(), axisLineWithoutWalk(null))

        shouldThrow<IllegalArgumentException> { ledger.read() }
    }

    /** 걷기는 AXIS 줄만 갖는다 — 호출 단위 줄이 걷기를 달고 있으면 그 원장은 이 코드의 것이 아니다. */
    @Test
    fun `걷기를 실은 호출 줄도 읽지 않는다`() {
        val ledger = ledger()
        Files.writeString(
            ledgerFile(),
            "{\"at\":\"$LEDGER_AT\",\"axis\":\"RESERVE_PRICE_DETAIL\"," +
                "\"notice_key_hash\":\"${LEDGER_KEY.value}\",\"outcome\":\"SUCCEEDED\"," +
                "\"kind\":\"PENDING\",\"walk\":\"$LEDGER_AT\"}\n",
        )

        shouldThrow<IllegalArgumentException> { ledger.read() }
    }

    private fun ledgerFile(): Path =
        Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME)

    /** 걷기 칸이 [walkField] 인 AXIS 줄 — `null` 이면 칸 자체를 싣지 않는다(옛 형식). */
    private fun axisLineWithoutWalk(walkField: String?): String =
        "{\"at\":\"$LEDGER_AT\",\"axis\":\"RESERVE_PRICE_DETAIL\"," +
            "\"notice_key_hash\":\"${LEDGER_KEY.value}\",\"outcome\":\"SUCCEEDED\"," +
            "\"kind\":\"AXIS\"${walkField?.let { ",$it" } ?: ""}}\n"
}
