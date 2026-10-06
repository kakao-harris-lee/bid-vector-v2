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

    /**
     * 실행 자체가 실패했다 — 러너가 예외를 잡아 정제된 원인 코드를 로그에 남기고 **이 값으로
     * 종료한다**(cr L-4: 앞 판은 이 값을 아무 코드도 만들지 않아 죽은 열거였다). 임대를 도중에
     * 잃은 run([RelayReport.LeaseLost])도 이 값이다 — 배타성이 깨진 것은 기다리면 풀리는
     * 상황이 아니다.
     */
    FAILED(1),

    /**
     * 실행은 끝났는데 **미완**이다(D-6F10-27 (7) 로 조건이 넓어졌다). 둘 중 하나다 —
     * (a) 사유 불명으로 격리된 행이 있다(미지 payload) (b) **집었는데 아무것도 전달하지
     * 못했다**(`claimed > 0` 이고 `delivered == 0` 이고 `failed + isolated > 0`).
     *
     * (b) 를 더한 이유(cr L-3): 앞 판은 집은 행이 **전부** `FAILED`·`ISOLATED` 로 타도 0 이었다.
     * 종단 셋은 단방향이라 그 행들은 되살릴 간선이 없는데, 같은 파일의 KDoc 이 `LEASE_BUSY` 를
     * 0 으로 접지 않는 이유로 든 「cron 이 성공으로 읽어 왜 발송이 안 되는가가 안 보인다」가
     * 그쪽에 더 세게 적용된다 — 신호의 세기가 사건의 심각도와 역순이었다.
     *
     * `delivered > 0` 인 부분 실패는 **0 이다**. 일부가 전달됐다면 경로는 살아 있고, 개별 행의
     * 거부·격리는 정상 처분이다(ADR 0005 D-3·D-4E-2) — 그것을 비-0 으로 올리면 정상 운영이
     * 늘 붉어진다.
     *
     * (a) 는 **심층 방어**다: kind 필터가 들어온 뒤 어댑터는 「종류는 맞고 타입은 아닌」 행을
     * 만들 수 없고(codec 이 둘을 함께 정한다) 형식을 어긴 행은 복호 fail-closed 로 run 을
     * 멈춘다. 그래서 배선된 production 에서 (a) 로 이 값에 닿는 경로는 없다(cr L-12) —
     * 도달하는 것은 (b) 다.
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

        is RelayReport.LeaseLost -> {
            RelayExitCode.FAILED
        }

        is RelayReport.Completed -> {
            completedExitCodeOf(report)
        }
    }

/**
 * D-6F10-27 (7) — 「집었는데 아무것도 전달하지 못했다」를 비-0 으로 올린다. 조건 셋을 함께
 * 보는 이유: `claimed > 0` 이 없으면 **빈 run**(집을 것이 없었다)이 붉어지고, `failed +
 * isolated > 0` 이 없으면 전부 중복(`skippedDuplicates`)으로 끝난 정상 run 이 붉어진다.
 */
private fun completedExitCodeOf(report: RelayReport.Completed): RelayExitCode {
    val nothingDelivered = report.claimed > 0 && report.delivered == 0 && report.failed + report.isolated > 0
    return if (report.unknownPayload > 0 || nothingDelivered) {
        RelayExitCode.INCOMPLETE
    } else {
        RelayExitCode.COMPLETE
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

        is RelayReport.LeaseLost -> {
            "relay lease-lost ${completedFields(report.partial)} exit=${exitCode.value}"
        }

        is RelayReport.Completed -> {
            "relay finished ${completedFields(report)} exit=${exitCode.value}"
        }
    }

private fun completedFields(report: RelayReport.Completed): String =
    "orphansIsolated=${report.orphansIsolated} claimed=${report.claimed} " +
        "delivered=${report.delivered} skippedDuplicates=${report.skippedDuplicates} " +
        "failed=${report.failed} isolated=${report.isolated} unknownPayload=${report.unknownPayload}"

internal fun relayFailureLine(causeCode: String): String = "relay failed cause=$causeCode"
