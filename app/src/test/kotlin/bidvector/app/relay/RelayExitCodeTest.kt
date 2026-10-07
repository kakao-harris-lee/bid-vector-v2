package bidvector.app.relay

import bidvector.workflow.notification.RelayAborted
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

    /**
     * cr R-3 — **두 갈래를 따로 잰다.** `INCOMPLETE` 의 술어는 「미지 payload 가 있다」 또는
     * 「집었는데 하나도 전달하지 못했다」인데, 위 표본은 둘 다 참이라(전달 0) 앞 항을 지워도
     * 초록이었다. 여기서는 **하나를 전달한** run 에 미지 payload 를 하나 섞어 뒷 항을 거짓으로
     * 만든다 — 앞 항만으로 붉어야 한다.
     *
     * 뒷 항만 참인 표본은 아래 `집었는데 하나도 전달하지 못하면` 이 든다(미지 payload 0).
     * 둘이 짝이다.
     */
    @Test
    fun `전달이 있어도 미지 payload 하나면 INCOMPLETE 다 — 앞 항 단독`() {
        val report = completed(claimed = 2, delivered = 1, isolated = 1, unknownPayload = 1)

        exitCodeOf(report) shouldBe RelayExitCode.INCOMPLETE
    }

    /**
     * D-6F10-27 (7) — **집었는데 아무것도 전달하지 못한 run 은 비-0 이다**(cr L-3). 앞 판은
     * 집은 행이 전부 거부·격리로 타도 0 이었고, 그것은 「왜 발송이 안 되는가」의 가장 나쁜
     * 판이 가장 약한 신호를 내는 꼴이었다.
     */
    @Test
    fun `집었는데 하나도 전달하지 못하면 INCOMPLETE 다 — 뒷 항 단독`() {
        // 미지 payload 가 0 이라 앞 항은 거짓이다 — 뒷 항만으로 붉어야 한다(cr R-3 의 짝).
        exitCodeOf(completed(claimed = 2, failed = 2)) shouldBe RelayExitCode.INCOMPLETE
        exitCodeOf(completed(claimed = 1, isolated = 1)) shouldBe RelayExitCode.INCOMPLETE
    }

    /** 하나라도 전달됐으면 경로는 살아 있다 — 개별 행의 거부·격리는 정상 처분이고 0 이다. */
    @Test
    fun `일부가 전달된 부분 실패는 COMPLETE 다`() {
        exitCodeOf(completed(claimed = 3, delivered = 1, failed = 1, isolated = 1)) shouldBe RelayExitCode.COMPLETE
    }

    /** 조건 셋을 함께 보는 이유 — 집을 것이 없던 **빈 run** 과 전부 중복인 run 은 붉지 않다. */
    @Test
    fun `빈 run 과 전부 중복인 run 은 COMPLETE 다`() {
        exitCodeOf(completed(claimed = 0)) shouldBe RelayExitCode.COMPLETE
        exitCodeOf(completed(claimed = 2, skippedDuplicates = 2)) shouldBe RelayExitCode.COMPLETE
    }

    /**
     * R1-M-1 — 임대를 도중에 잃은 run 은 `FAILED` 다. `Skipped` 와 다른 값인 이유: 저쪽은
     * 아무것도 집지 않았고 이쪽은 이미 집었고 일부를 발송했을 수 있다(행이 `CLAIMED` 에 남는다).
     */
    @Test
    fun `임대를 도중에 잃으면 FAILED 1 이고 부분 계수가 보고에 남는다`() {
        val partial = completed(claimed = 3, delivered = 1)
        val report = RelayReport.LeaseLost(partial)

        exitCodeOf(report) shouldBe RelayExitCode.FAILED
        RelayExitCode.FAILED.value shouldBe 1
        relayFinishLine(report, exitCodeOf(report)) shouldBe
            "relay lease-lost orphansIsolated=0 claimed=3 delivered=1 skippedDuplicates=0 " +
            "failed=0 isolated=0 unknownPayload=0 exit=1"
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

        // exit 는 **2** 다 — 고아를 하나 태웠다(PR #63 finding 2 로 이 핀을 뒤집었다).
        relayFinishLine(report, exitCodeOf(report)) shouldBe
            "relay finished orphansIsolated=1 claimed=3 delivered=1 skippedDuplicates=1 " +
            "failed=1 isolated=0 unknownPayload=0 exit=2"
    }

    /**
     * **PR #63 finding 2** — 고아 격리는 **알림을 영구히 잃는 사건**이라(`ISOLATED` 에서 나가는
     * 간선 0) 그 run 은 0 일 수 없다. 앞 판은 이 계수를 보지 않아 고아 50 을 태운 run 이
     * exit 0 이었다 — 열거가 세는 사건 가운데 가장 센 것이 가장 약한 신호를 냈다.
     *
     * **다른 두 갈래를 거짓으로** 둔 표본이다: 미지 payload 0, 전달 1(발송 경로는 살아 있다).
     * 그래서 이 한 갈래만으로 붉어야 한다.
     */
    @Test
    fun `고아를 태운 run 은 전달이 있어도 INCOMPLETE 다 — 가운데 항 단독`() {
        val report = completed(orphansIsolated = 1, claimed = 1, delivered = 1)

        exitCodeOf(report) shouldBe RelayExitCode.INCOMPLETE
    }

    /** 고아 0 이면 그 갈래는 거짓이다 — 위 test 가 「늘 INCOMPLETE」 구현에서도 초록이 아니게. */
    @Test
    fun `고아가 없고 전부 전달되면 COMPLETE 다 — 가운데 항 음성 대조`() {
        exitCodeOf(completed(orphansIsolated = 0, claimed = 1, delivered = 1)) shouldBe RelayExitCode.COMPLETE
    }

    /**
     * **PR #63 finding 4** — 실패 줄이 **그때까지의 집계를 싣는다.** [partialOf] 가 그 값을
     * 예외에서 꺼내고(없으면 null), [relayFailureLine] 이 그것을 줄에 붙인다. 러너는 이 둘을
     * 이어 붙이기만 한다 — 그래서 결정 내용이 순수 함수 둘로 측정된다.
     */
    @Test
    fun `실패 줄은 부분 집계를 싣는다 — 없으면 싣지 않는다`() {
        val partial = completed(orphansIsolated = 1, claimed = 20, delivered = 12)

        relayFailureLine("Boom", partial) shouldBe
            "relay failed cause=Boom orphansIsolated=1 claimed=20 delivered=12 " +
            "skippedDuplicates=0 failed=0 isolated=0 unknownPayload=0"
        relayFailureLine("Boom", null) shouldBe "relay failed cause=Boom"
    }

    @Test
    fun `부분 집계는 RelayAborted 만 나른다`() {
        val partial = completed(claimed = 20, delivered = 12)

        partialOf(RelayAborted(partial, IllegalStateException("전이 실패"))) shouldBe partial
        partialOf(IllegalStateException("그냥 터짐")) shouldBe null
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
