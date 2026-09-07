package bidvector.procurement

import java.time.Instant

/** 수집 실행 하나를 식별하는 메타데이터(⑥) — 회계 값 자체가 아니라 그 실행의 맥락(D-M3-7 (a)). */
data class CollectionRunMeta(
    val referenceDate: CollectionReferenceDate,
    val source: SourceEndpoint,
    val startedAt: Instant,
    val finishedAt: Instant,
)

/**
 * 수집 회계 저장 port(⑤·⑥, ADR 0005 D-10.1) — 배치 마지막에 **별도 트랜잭션**으로 기록한다
 * (항목 트랜잭션과 분리, D-3D-4). 구현이 그 경계를 강제한다.
 */
interface CollectionRunStore {
    fun record(
        accounting: CollectionAccounting,
        meta: CollectionRunMeta,
    )
}
