package bidvector.adapters.snapshot

import bidvector.procurement.AttemptHistory
import bidvector.procurement.AttemptLedger
import bidvector.procurement.CollectionAttempt
import bidvector.workflow.collection.SampleConfirmation
import bidvector.workflow.collection.SampleList
import bidvector.workflow.collection.SampleListLedger

/**
 * 쓸 수 있는 실행만 쓴다 — 두 원장의 **쓰기 술어**다(D-6G2c-4 · vr r1 F-1). `RunStateDirectory` 에서
 * 갈라낸 파일이다(sizeGate 500, `RunStateLock`·`FileAttemptLedger` 와 같은 전례): 디렉터리가 지는 것은
 * 무결성 장부·복구·표본 확정이고, 여기가 지는 것은 **누가 쓸 수 있는가**다.
 *
 * 시도 원장은 읽기는 되고 쓰기는 거부한다(D-6G-57). 두 실행이 나란히 원장에 쓰면 무결성 장부가 서로의
 * 줄에 어긋나고, 그보다 먼저 두 상한 회계가 서로의 호출을 못 본다.
 */
internal class GuardedAttemptLedger(
    private val refusal: () -> String?,
    private val reads: AttemptLedger,
) : AttemptLedger {
    override fun append(attempt: CollectionAttempt) {
        refusal()?.let { error(it) }
        reads.append(attempt)
    }

    override fun read(): AttemptHistory = reads.read()
}

/**
 * 표본 목록도 같은 술어 아래다(D-6G2c-4) — 읽기는 되고 **확정은 거부**한다. 확정은 한 번뿐이라
 * (`CREATE_NEW`) 쓰지 못할 실행의 확정 하나가 그 디렉터리의 표본을 영구히 정한다. 두 원장이 같은
 * 술어를 묻어야 「한쪽만 살아 있는」 모양이 생기지 않는다.
 */
internal class GuardedSampleListLedger(
    private val refusal: () -> String?,
    private val reads: SampleListLedger,
) : SampleListLedger {
    override fun confirm(confirmation: SampleConfirmation): SampleList {
        refusal()?.let { error(it) }
        return reads.confirm(confirmation)
    }

    override fun confirmed(): SampleList? = reads.confirmed()
}

/**
 * 쓸 수 있는가 — **거부 사유 또는 `null`**(D-6G2c-4 · vr r1 F-1). 두 원장이 같은 답을 받으므로 사유가
 * 자리마다 갈리지 않고, 매 호출에 묻으므로 생성 뒤에 바뀐 사실(닫힘)이 반영된다. 멤버가 아니라 파일
 * 수준 함수다 — 디렉터리의 멤버 수 상한을 이 술어로 쓰지 않는다.
 */
internal fun writeRefusalFor(
    lock: RunStateLock,
    closed: Boolean,
): String? =
    when {
        lock !is RunStateLock.Held -> "실행 상태 잠금을 들지 않았다 — 다른 실행이 돌고 있거나 자물쇠를 걸 수 없다"
        closed -> "실행 상태 디렉터리가 이미 닫혔다 — 놓은 잠금 뒤의 쓰기는 다른 실행의 줄과 섞인다"
        else -> null
    }
