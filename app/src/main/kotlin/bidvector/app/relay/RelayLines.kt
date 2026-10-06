package bidvector.app.relay

import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelaySkipReason

// 0~2 와 달리 detekt 의 기본 허용 숫자가 아니라 이름을 붙인다 — 값 자체에 뜻은 없다
// (수집 러너의 `ALREADY_RUNNING_EXIT_CODE` 와 같은 관례).
private const val LEASE_BUSY_EXIT_CODE = 3
private const val ENV_SUPPRESSED_EXIT_CODE = 4

/**
 * relay 러너의 프로세스 종료 코드 — **0 은 집은 행 전부가 종단에 닿았을 때만**이다.
 *
 * [LEASE_BUSY] 와 [ENV_SUPPRESSED] 를 한 값으로 접지 않는다: 처방이 다르다. 저쪽은
 * 기다리면 풀리고(다른 relay 가 돌고 있다) 이쪽은 설정을 고쳐야 풀린다(환경이 발송 가능
 * 모드가 아니다). 접으면 「조금 뒤 다시 돌려 보라」가 끝나지 않는 조언이 된다 — 수집의
 * `ALREADY_RUNNING`/`UNLOCKABLE` 을 가른 것과 같은 축이다.
 *
 * 사유 토큰은 `name` 이고 종료 코드는 [value] 다 — **한 쌍**으로 간다(어휘가 두 자리에
 * 갈리지 않게).
 */
enum class RelayExitCode(
    val value: Int,
) {
    COMPLETE(0),

    /** 실행 자체가 실패했다 — Spring 이 러너 예외를 받아 기동 실패로 끝낸다. */
    FAILED(1),

    /**
     * 집은 행 가운데 **사유 불명으로 격리된** 것이 있다(미지 payload) — 실행은 끝났지만
     * 미완이다. 전이 예외는 이 값이 아니라 [FAILED] 쪽이다(수집 러너가 「실행 실패」와
     * 「끝났지만 미완」을 가른 것과 같다).
     */
    INCOMPLETE(2),

    /** 다른 relay 가 임대를 쥐고 있다 — 아무것도 집지 않았다. */
    LEASE_BUSY(LEASE_BUSY_EXIT_CODE),

    /** 환경이 발송 가능 모드가 아니다 — claim 0, 행은 `PENDING` 보존. */
    ENV_SUPPRESSED(ENV_SUPPRESSED_EXIT_CODE),
}

/**
 * 보고서 → 종료 코드. 억제·Busy 는 **실패가 아니다**(정상적인 답이고, 그 사실이 코드로
 * 보인다 — ADR 0005 D-10 ③).
 */
internal fun exitCodeOf(report: RelayReport): RelayExitCode =
    when (report) {
        is RelayReport.Skipped -> {
            skipExitCodeOf(report.reason)
        }

        is RelayReport.Completed -> {
            if (report.unknownPayload > 0) RelayExitCode.INCOMPLETE else RelayExitCode.COMPLETE
        }
    }

private fun skipExitCodeOf(reason: RelaySkipReason): RelayExitCode =
    when (reason) {
        RelaySkipReason.LeaseBusy -> RelayExitCode.LEASE_BUSY
        RelaySkipReason.EnvironmentSuppressed -> RelayExitCode.ENV_SUPPRESSED
    }

/**
 * 러너가 로그에 남기는 한 줄들 — **계수와 열거값만** 싣는다. 입력 타입([RelayReport])이
 * 공고 내용·대상·키를 나르지 않으므로 여기서 새는 값은 구조적으로 없다(수집 러너의
 * `CollectionLines` 와 같은 근거).
 */
internal fun relayStartLine(limit: Int): String = "relay start limit=$limit"

internal fun relayFinishLine(
    report: RelayReport,
    exitCode: RelayExitCode,
): String =
    when (report) {
        is RelayReport.Skipped -> {
            "relay skipped reason=${report.reason.name} exit=${exitCode.value}"
        }

        is RelayReport.Completed -> {
            "relay finished orphansIsolated=${report.orphansIsolated} claimed=${report.claimed} " +
                "delivered=${report.delivered} skippedDuplicates=${report.skippedDuplicates} " +
                "failed=${report.failed} isolated=${report.isolated} " +
                "unknownPayload=${report.unknownPayload} exit=${exitCode.value}"
        }
    }

internal fun relayFailureLine(causeCode: String): String = "relay failed cause=$causeCode"
