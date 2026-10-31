package bidvector.app.notificationinbox

import bidvector.workflow.bidding.DaysBeforeWindow
import bidvector.workflow.bidding.NotificationSweepReport
import bidvector.workflow.bidding.NotificationWindowPolicyData

/**
 * 훑기 run 의 로그 줄 — **행 값을 싣지 않는다.** 실리는 것은 계수와 창 폭뿐이고, 그 둘은 어느
 * 것도 공고 내용·담당자 값·계정 주체가 아니다(보존 러너의 로그 규율과 같은 축).
 *
 * **계정 주체를 싣지 않는 것은 규율이자 게이트다.** 감사 주체 라벨(`account:…`)을 만드는 자리는
 * 저장소에 **하나**이고 그 등식을 구조 test 가 잰다 — 러너가 같은 접두를 로그에 적으면 그 등식이
 * 붉어진다. 계수만으로 「무엇이 몇 건」이 보이므로 주체가 필요하지 않다.
 */
internal fun sweepStartLine(policy: NotificationWindowPolicyData): String =
    "notification-sweep start " +
        "bidDeadlineImminent=${policy.bidDeadlineImminent.window()} " +
        "bidDeadlineToday=${policy.bidDeadlineToday.window()} " +
        "documentDeadline=${policy.documentDeadline.window()}"

internal fun sweepFinishLine(
    report: NotificationSweepReport,
    exitCode: NotificationSweepExitCode,
): String =
    "notification-sweep finish casesRead=${report.casesRead} created=${report.created} " +
        "updated=${report.updated} failed=${report.failed} " +
        "exit=${exitCode.name}(${exitCode.value})"

/** 실패 줄 — 원인은 **정제된 코드만** 싣는다(원 예외 메시지에 행 값이 실릴 수 있다). */
internal fun sweepFailureLine(causeCode: String): String =
    "notification-sweep failed cause=$causeCode " +
        "exit=${NotificationSweepExitCode.FAILED.name}(${NotificationSweepExitCode.FAILED.value})"

private fun DaysBeforeWindow.window(): String = "$minDaysBefore..$maxDaysBefore"
