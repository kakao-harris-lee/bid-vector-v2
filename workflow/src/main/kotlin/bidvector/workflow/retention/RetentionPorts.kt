package bidvector.workflow.retention

import bidvector.workflow.event.LeaseAttempt

/**
 * 표 하나의 처분(M6/6B-3 P-2) — 운영자 결정 A-3·A-4 의 네 갈래다. 열거이지만 **표 집합을
 * 열거하지 않는다**: 어느 표가 어느 갈래인지는 어댑터의 레지스트리가 말하고, 그 레지스트리가
 * 스키마 정본(카탈로그)과 집합 등식이어야 한다([RetentionStorePort.storedTables]).
 */
enum class RetentionTreatment {
    /** 기한이 지난 행을 지운다. */
    PURGE,

    /** 지우지 않고 PII 만 지운다(A-3 — 6G 백테스트 표본 보존). */
    MASK_PII,

    /** 자기 기준으로 지우지 않는다 — 부모가 지워질 때 FK `ON DELETE CASCADE` 로 함께 사라진다. */
    CASCADE_WITH_PARENT,

    /** 시간 기준 파기 대상이 아니다(A-4 — 싱글턴 설정·멱등 원장·개정 이력·수집 원장·참조 데이터). */
    EXEMPT,
}

/** 분류된 표 하나. `table` 은 저장소의 표 이름이고 이 모듈에서는 **보고에 실리는 라벨**이다. */
data class RetentionTarget(
    val table: String,
    val treatment: RetentionTreatment,
)

/**
 * FK 한 간선 — 자식이 부모를 참조한다. `cascade` 는 `ON DELETE CASCADE` 여부다(부모를 지울 때
 * 자식이 막는가, 함께 사라지는가가 갈린다).
 */
data class TableDependency(
    val child: String,
    val parent: String,
    val cascade: Boolean,
)

/** 마스킹 한 일괄의 계수 — 성공과 **fail-closed 폐기**를 하나로 접지 않는다(설계 검토 우회 10). */
data class MaskBatchCounts(
    val masked: Int,
    val failClosed: Int,
) {
    val total: Int get() = masked + failClosed
}

/**
 * 보존 저장소 port — **cutoff 를 받지 않는 함수가 하나도 없고, 그 cutoff 를 만들 수 있는 것은
 * 이 모듈뿐이다**([RetentionCutoff]).
 *
 * [withExclusiveLease] 가 클로저 경계인 이유는 `ConsumerLeasePort` 와 같다: 세션 범위 잠금은
 * 「연결을 쥔다」는 성질에 기대므로 본문이 도는 내내 한 연결이 살아 있어야 한다. 쓰기 능력
 * ([RetentionWriter])이 그 클로저 안에서만 손에 들어오므로, 임대 없이 지우는 호출부는
 * 쓸 수 있는 객체가 없다.
 */
interface RetentionStorePort {
    /** 레지스트리가 분류한 표 전수. */
    fun classifiedTargets(): List<RetentionTarget>

    /** 저장소에 **실제로 있는** 표 전수(스키마 정본). 분류와 이 집합이 다르면 실행하지 않는다. */
    fun storedTables(): Set<String>

    /** 저장소가 보고하는 FK 간선 전수 — 삭제 순서는 이것에서 도출한다(손 순서 금지). */
    fun dependencies(): Set<TableDependency>

    fun <T> withExclusiveLease(body: (RetentionWriter) -> T): LeaseAttempt<T>
}

/** 임대를 쥔 동안에만 손에 들어오는 쓰기 능력. */
interface RetentionWriter {
    /** 기한이 지난 행을 최대 [batchSize] 개 지운다 — 지운 수를 돌려준다. */
    fun purge(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): Int

    /** 기한이 지난 **아직 마스킹되지 않은** 행을 최대 [batchSize] 개 마스킹한다. */
    fun mask(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): MaskBatchCounts
}
