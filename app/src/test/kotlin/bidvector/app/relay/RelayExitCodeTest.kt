package bidvector.app.relay

import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelaySkipReason
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 사유 토큰과 종료 코드가 **한 쌍**인지(D-6F10-18 ⑧) — 어휘가 두 자리에 갈리면 운영자가
 * 보는 로그와 cron 이 보는 코드가 다른 말을 한다.
 *
 * **`LEASE_BUSY` 와 `ENV_SUPPRESSED` 가 0 이 아닌** 것이 이 파일의 요점이다(설계 검토 우회
 * 11). 둘은 실패가 아니지만 「아무것도 하지 않았다」이고, 0 으로 접으면 cron 이 성공으로
 * 읽어 「왜 발송이 안 되는가」가 어디에도 보이지 않는다.
 */
class RelayExitCodeTest {
    @Test
    fun `집은 행 전부가 종단에 닿으면 COMPLETE 0 이다`() {
        exitCodeOf(completed(claimed = 2, delivered = 2)) shouldBe RelayExitCode.COMPLETE
        RelayExitCode.COMPLETE.value shouldBe 0
    }

    @Test
    fun `사유 불명으로 격리된 행이 있으면 INCOMPLETE 2 다`() {
        val report = completed(claimed = 1, isolated = 1, unknownPayload = 1)

        exitCodeOf(report) shouldBe RelayExitCode.INCOMPLETE
        RelayExitCode.INCOMPLETE.value shouldBe 2
    }

    @Test
    fun `격리가 있어도 사유가 분명하면 COMPLETE 다 — Unknown 은 정상 처분이다`() {
        val report = completed(claimed = 1, isolated = 1, unknownPayload = 0)

        exitCodeOf(report) shouldBe RelayExitCode.COMPLETE
    }

    @Test
    fun `임대를 못 쥐면 LEASE_BUSY 3 이고 0 이 아니다`() {
        val report = RelayReport.Skipped(RelaySkipReason.LeaseBusy)

        exitCodeOf(report) shouldBe RelayExitCode.LEASE_BUSY
        RelayExitCode.LEASE_BUSY.value shouldBe 3
    }

    @Test
    fun `환경 억제는 ENV_SUPPRESSED 4 이고 LEASE_BUSY 와 다른 값이다`() {
        val report = RelayReport.Skipped(RelaySkipReason.EnvironmentSuppressed)

        exitCodeOf(report) shouldBe RelayExitCode.ENV_SUPPRESSED
        RelayExitCode.ENV_SUPPRESSED.value shouldBe 4
        // 처방이 다르다 — 저쪽은 기다리면 풀리고 이쪽은 설정을 고쳐야 풀린다.
        RelayExitCode.ENV_SUPPRESSED.value shouldBe RelayExitCode.LEASE_BUSY.value + 1
    }

    /** 코드 값이 서로 다르다 — 두 사유가 같은 값을 쓰면 가른 의미가 사라진다. */
    @Test
    fun `종료 코드 값은 서로 겹치지 않는다`() {
        val values = RelayExitCode.entries.map { it.value }

        values.toSet().size shouldBe values.size
    }

    /** 로그 한 줄에 사유 토큰과 코드가 **함께** 실린다 — 둘 중 하나만 보고 판단하지 않게. */
    @Test
    fun `억제 로그 줄은 사유 토큰과 종료 코드를 함께 싣는다`() {
        val report = RelayReport.Skipped(RelaySkipReason.EnvironmentSuppressed)

        relayFinishLine(report, exitCodeOf(report)) shouldBe
            "relay skipped reason=EnvironmentSuppressed exit=4"
    }

    @Test
    fun `완료 로그 줄은 계수 일곱을 싣는다 — 공고 내용은 싣지 않는다`() {
        val report = completed(orphansIsolated = 1, claimed = 3, delivered = 1, skippedDuplicates = 1, failed = 1)

        relayFinishLine(report, exitCodeOf(report)) shouldBe
            "relay finished orphansIsolated=1 claimed=3 delivered=1 skippedDuplicates=1 " +
            "failed=1 isolated=0 unknownPayload=0 exit=0"
    }
}

@Suppress("LongParameterList")
private fun completed(
    orphansIsolated: Int = 0,
    claimed: Int = 0,
    delivered: Int = 0,
    skippedDuplicates: Int = 0,
    failed: Int = 0,
    isolated: Int = 0,
    unknownPayload: Int = 0,
) = RelayReport.Completed(
    orphansIsolated = orphansIsolated,
    claimed = claimed,
    delivered = delivered,
    skippedDuplicates = skippedDuplicates,
    failed = failed,
    isolated = isolated,
    unknownPayload = unknownPayload,
)
