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

/** 이 test 가 재는 것은 줄의 형태다 — 재호출 상한은 값으로 고정한다(정책 판이 아니다). */
private const val LEDGER_RETRY_LIMIT = 3

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

    /**
     * **D-6G2d-41 (cr r5 ①③) — 원장이 장부보다 먼저 굳는다.** 장부(`state.json`)는 원장의 누적 해시와
     * 줄 수를 싣는다. 장부가 먼저 굳으면 정전 뒤에 「줄 수가 장부보다 적다」가 되고 그 디렉터리는
     * **영구 거부**된다 — 이 slice 가 닫으려던 부류 그대로다. 반대 순서의 손해는 「굳은 줄을 장부가
     * 아직 모른다」이고 다음 기동이 장부를 원장 쪽으로 재동기해 흡수한다.
     *
     * 내구성 자체는 단위 test 로 잴 수 없다(크래시를 심을 자리가 없다). 잴 수 있는 것은 **순서**이고,
     * 그래서 덧붙임 채널을 값으로 뺐다 — 대역이 두 걸음을 기록하고 장부 갱신이 세 번째로 온다.
     */
    @Test
    fun `원장 바이트를 굳힌 뒤에 장부를 갱신한다`() {
        val order = mutableListOf<String>()
        val ledger = FileAttemptLedger(ledgerFile(), { RecordingAppend(order) }) { order += "장부" }

        ledger.append(attemptOf(AttemptOutcome.Succeeded))

        order shouldContainExactly listOf("덧붙임", "굳힘", "장부")
    }

    private class RecordingAppend(
        private val order: MutableList<String>,
    ) : DurableAppend {
        override fun append(text: String) {
            order += "덧붙임"
        }

        override fun force() {
            order += "굳힘"
        }

        override fun close() = Unit
    }

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
        // 축 결말 줄만 이어 돌기의 답을 정한다. HTTP 줄은 상한만 센다(둘이 한 파일에 있어도 섞이지 않는다).
        history
            .axisResumptions(LEDGER_RETRY_LIMIT)
            .getValue(LEDGER_KEY.value)
            .filterValues { it }
            .keys shouldContainExactly listOf(SourceEndpoint.RESERVE_PRICE_DETAIL)
        history.spend(LEDGER_AT).total shouldBe 2
    }

    /**
     * **D-6G2d-16 — 결말 어휘 넷이 왕복한다.** 관문 거부가 어휘로 갈라졌으므로 원장이 그 답을 나른다.
     * 코드 문자열로 되읽어 분류하지 않기 때문에, 판독이 이 라벨을 모르면 상한 셈이 조용히 갈린다.
     */
    @Test
    fun `결말 어휘 넷이 왕복한다 — 관문 거부 포함`() {
        val ledger = ledger()
        val written =
            listOf(
                attemptOf(AttemptOutcome.Succeeded, kind = AttemptKind.AXIS),
                attemptOf(AttemptOutcome.Empty, kind = AttemptKind.AXIS),
                attemptOf(AttemptOutcome.Failed("STRUCTURE_FAILURE"), kind = AttemptKind.AXIS),
                attemptOf(AttemptOutcome.FinalFailure("MAX_PAGES"), kind = AttemptKind.AXIS),
                attemptOf(AttemptOutcome.Refused("BUDGET_EXHAUSTED_DAILY"), kind = AttemptKind.AXIS),
            )

        written.forEach(ledger::append)

        // 같은 축의 줄이라 마지막이 이긴다 — 왕복을 재는 것은 줄 수와 그 마지막 값이다.
        ledger.read().size shouldBe written.size
        ledger
            .read()
            .axisConclusions()
            .getValue(LEDGER_KEY.value)
            .getValue(SourceEndpoint.RESERVE_PRICE_DETAIL)
            .outcome shouldBe AttemptOutcome.Refused("BUDGET_EXHAUSTED_DAILY")
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
    fun `걷기를 싣지 않은 AXIS 줄은 형식으로 거부한다`() {
        val ledger = ledger()
        Files.writeString(ledgerFile(), axisLineWithoutWalk("\"walk\":null"))

        // **형식 거부**다(D-6G2d-44) — generic 예외로 죽으면 운영자 출력에서 「손상」과 구별되지 않고,
        // 그 구별이 처방을 가른다(옛 디렉터리는 폐기해도 되고 손상은 사람이 봐야 한다).
        shouldThrow<RunStateFormatRefusedException> { ledger.read() }.fault shouldBe RunStateFormatFault.LEGACY_LINE
    }

    /** 같은 이유로 **칸이 아예 없는** 옛 줄도 읽지 않는다 — 부재와 명시 null 을 함께 거부한다. */
    @Test
    fun `걷기 칸이 없는 옛 AXIS 줄도 형식으로 거부한다`() {
        val ledger = ledger()
        Files.writeString(ledgerFile(), axisLineWithoutWalk(null))

        shouldThrow<RunStateFormatRefusedException> { ledger.read() }.fault shouldBe RunStateFormatFault.LEGACY_LINE
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

    private fun ledgerFile(): Path = Files.createDirectories(temp.resolve("run-state")).resolve(ATTEMPT_LEDGER_NAME)

    /** 걷기 칸이 [walkField] 인 AXIS 줄 — `null` 이면 칸 자체를 싣지 않는다(옛 형식). */
    private fun axisLineWithoutWalk(walkField: String?): String =
        "{\"at\":\"$LEDGER_AT\",\"axis\":\"RESERVE_PRICE_DETAIL\"," +
            "\"notice_key_hash\":\"${LEDGER_KEY.value}\",\"outcome\":\"SUCCEEDED\"," +
            "\"kind\":\"AXIS\"${walkField?.let { ",$it" } ?: ""}}\n"
}
