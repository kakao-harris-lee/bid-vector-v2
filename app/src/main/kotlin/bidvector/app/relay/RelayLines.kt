package bidvector.app.relay

import bidvector.workflow.notification.RelayAborted
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
     * 실행은 끝났는데 **미완**이다. 셋 중 하나다 — (a) 사유 불명으로 격리된 행이 있다(미지
     * payload) (b) **고아를 하나라도 태웠다**(`orphansIsolated > 0` — PR #63 finding 2 로
     * 더했다) (c) **발송이 한 번도 성공하지 않았고 종단 실패가 있다**(`claimed > 0` 이고
     * `delivered == 0` 이고 `failed + isolated > 0`).
     *
     * (b) 를 더한 이유: 고아 격리는 **알림을 영구히 잃는 사건**이다(`ISOLATED` 에서 나가는
     * 간선 0). 앞 판은 이 계수를 보지 않아 고아 50 을 태운 run 이 exit 0 이었고, 그것은 이
     * 열거가 세는 사건들 가운데 **가장 센 것이 가장 약한 신호**를 내는 꼴이었다. 조건의
     * 이력은 D-6F10-18 ⑧ → 27 ⑦ → 이 줄이다.
     *
     * (b) 의 문면을 술어 그대로 적는다(cr R-8) — 앞 판은 「집었는데 **전부** 거부·격리된」으로
     * 적어 **술어보다 좁았다.** 술어는 `delivered == 0` 만 보므로 「중복 다섯 + 거부 하나」 run
     * 도 이 값이다. 그 run 은 「전부 거부·격리」가 아니고, 중복 행은 `DELIVERED` 로 간다
     * (D-6F10-12) — 그래도 이 값이 맞다: **중복 처리는 발송 경로가 살아 있다는 증거가 아니다**
     * (한 통도 보내지 않고 끝난 run 이다). 그래서 `skippedDuplicates` 를 전달로 세지 않는다.
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
    // 고아 격리는 **알림을 영구히 잃는 사건**이다(PR #63 finding 2) — 앞 판은 이 계수를 보지
    // 않아 고아 50 을 태운 run 이 exit 0 이었다. 격리는 되돌릴 간선이 없으므로 「끝났지만
    // 미완」의 가장 센 사례다.
    return if (report.unknownPayload > 0 || report.orphansIsolated > 0 || nothingDelivered) {
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

/**
 * 실패 줄 — **그때까지의 집계를 함께 싣는다**(PR #63 finding 4). [partial] 이 null 이면 행
 * 처분에 닿기도 전에 터진 것이라(예: 임대 획득 질의) 실을 계수가 없다.
 *
 * 계수를 싣는 이유: 운영자가 알아야 하는 것은 「터졌다」가 아니라 **얼마나 갔는가**다. 앞 판은
 * 사유 코드만 남겨, 스물 집어 열둘 전달하고 열셋째에서 터진 run 과 집기도 전에 터진 run 이
 * 로그에서 같아 보였다.
 */
internal fun relayFailureLine(
    causeCode: String,
    partial: RelayReport.Completed? = null,
): String =
    if (partial == null) {
        "relay failed cause=$causeCode"
    } else {
        "relay failed cause=$causeCode ${completedFields(partial)}"
    }

/**
 * 예외가 나르는 부분 집계 — [RelayAborted] 만 그것을 갖는다(순수 함수라 러너의 분기를 test 가
 * 직접 칠 수 있다).
 */
internal fun partialOf(failure: Exception): RelayReport.Completed? = (failure as? RelayAborted)?.partial
