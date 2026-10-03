package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.adapters.snapshot.RunStateLock
import bidvector.workflow.collection.CollectionHalt
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionReport
import bidvector.workflow.collection.CollectionSlot
import bidvector.workflow.collection.CollectionSlotReport
import bidvector.workflow.collection.CollectionSource

/**
 * 러너가 로그에 남기는 한 줄들(D-6F8-4) — 건수·조회일·업종 이름·원인 코드만 실을 수 있다. 입력 타입이
 * 원문·키·공고 내용을 나르지 않으므로(`CollectionSlotReport` 등은 계수와 열거값뿐) 여기서 새는 값은
 * 구조적으로 없다.
 */
internal fun startLine(
    range: CollectionRange,
    sources: List<CollectionSource>,
): String = "collection start from=${range.from} to=${range.to} sources=${sources.joinToString(",") { it.name.value }}"

internal fun slotLine(report: CollectionSlotReport): String {
    val accounting = report.accounting
    val writes = report.writes
    return "collection slot ${slotFields(report.slot)} received=${accounting.received} " +
        "normalized=${accounting.normalized} duplicate=${accounting.duplicate} dropped=${accounting.dropped} " +
        "dropReasons=${accounting.dropReasons} sourceTotal=${accounting.sourceTotal} " +
        "pages=${accounting.pagesFetched} truncated=${accounting.truncationCause ?: "none"} " +
        "unknownFields=${accounting.unknownFields} quotaExceeded=${accounting.quotaExceeded} " +
        "backoffSkipped=${accounting.backoffSkipped} inserted=${writes.inserted} updated=${writes.updated} " +
        "unchanged=${writes.unchanged} rejected=${writes.rejected}"
}

internal fun haltLine(halt: CollectionHalt): String =
    "collection halted cause=${halt.cause} ${slotFields(halt.at)} notAttempted=${halt.notAttempted.size}"

internal fun finishLine(
    report: CollectionReport,
    exitCode: CollectionExitCode,
): String =
    "collection finished slots=${report.slots.size} truncatedSlots=${report.slots.count { it.accounting.truncated }} " +
        "halted=${report.halted != null} exit=${exitCode.value}"

internal fun failureLine(causeCode: String): String = "collection failed cause=$causeCode"

private fun slotFields(slot: CollectionSlot): String = "date=${slot.referenceDate} source=${slot.source.value}"

// 0~2 와 달리 detekt 의 기본 허용 숫자가 아니라 이름을 붙인다 — 값 자체에 뜻은 없다.
private const val ALREADY_RUNNING_EXIT_CODE = 3
private const val UNLOCKABLE_EXIT_CODE = 4

/** 프로세스 종료 코드 — 0 은 전 슬롯이 끝까지 읽혔을 때만이다(절단·쿼터 멈춤은 실행이 끝나도 미완이다). */
enum class CollectionExitCode(
    val value: Int,
) {
    COMPLETE(0),
    FAILED(1),
    INCOMPLETE(2),

    /** 다른 실행이 이미 잠금을 들고 있다 — 아무것도 부르지 않았다(미완과 구별해야 한다). */
    ALREADY_RUNNING(ALREADY_RUNNING_EXIT_CODE),

    /**
     * 자물쇠를 **걸 수 없다**(D-6G2c-2) — 잠금을 지원하지 않는 자리이거나 자물쇠 파일을 열 수 없다.
     * 역시 아무것도 부르지 않았지만 [ALREADY_RUNNING] 과 처방이 다르다: 저쪽은 기다리면 풀리고
     * 이쪽은 영영 풀리지 않는다(운영자가 경로를 고쳐야 한다). 둘을 한 값으로 접으면 「조금 뒤에
     * 다시 돌려 보라」가 끝나지 않는 조언이 된다.
     */
    UNLOCKABLE(UNLOCKABLE_EXIT_CODE),
}

/**
 * **잠금 안에서만 돈다**(D-6G-57) — 두 갈래가 같은 실행 상태 디렉터리를 쓰므로 잠금도, 잠금을 보고
 * 물러나는 모양도 하나다. 얻지 못한 것은 오류가 아니라 정상적인 답이라 스택 트레이스를 남기지 않는다.
 *
 * **잠금이 아니라 디렉터리를 받는다**(D-6G2c-4) — 놓는 길이 [RunStateDirectory.close] 하나이므로
 * 놓을 수 있는 것은 디렉터리를 쥔 자리뿐이다. 잠금만 받던 앞 판은 「밖에서 잠금만 풀고 원장은 쓰기
 * 가능한 채로」 두는 길을 열어 두었다.
 */
internal fun underRunStateLock(
    runState: RunStateDirectory,
    label: String,
    log: CollectionLog,
    termination: CollectionTermination,
    body: () -> Unit,
) {
    when (runState.lock) {
        RunStateLock.Busy -> skipRun(label, CollectionExitCode.ALREADY_RUNNING, log, termination)

        RunStateLock.Unlockable -> skipRun(label, CollectionExitCode.UNLOCKABLE, log, termination)

        is RunStateLock.Held -> {
            try {
                body()
            } finally {
                runState.close()
            }
        }
    }
}

/** 한 호출도 내지 않고 끝낸다 — 사유 토큰과 종료 코드가 **한 쌍**으로 간다(어휘가 두 자리에 갈리지 않게). */
private fun skipRun(
    label: String,
    reason: CollectionExitCode,
    log: CollectionLog,
    termination: CollectionTermination,
) {
    log.write("$label skipped reason=${reason.name}")
    termination.terminate(reason.value)
}

internal fun exitCodeOf(report: CollectionReport): CollectionExitCode =
    if (report.halted == null && report.slots.none { it.accounting.truncated }) {
        CollectionExitCode.COMPLETE
    } else {
        CollectionExitCode.INCOMPLETE
    }
