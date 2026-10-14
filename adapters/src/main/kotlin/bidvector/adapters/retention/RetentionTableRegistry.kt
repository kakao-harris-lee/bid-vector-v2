package bidvector.adapters.retention

import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.workflow.retention.RetentionTarget
import bidvector.workflow.retention.RetentionTreatment

/**
 * 시간 기준 파기에서 빠지는 사유(운영자 결정 A-4) — **열거이고, 열거라야 한다**: 「왜 안
 * 지우는가」가 표마다 적혀 있지 않으면 분류 누락과 의도된 제외가 구별되지 않는다.
 */
internal enum class ExemptReason {
    /** 싱글턴 설정 행(id=1) — 「90일 지난 행 삭제」가 현재 설정을 지운다. */
    SINGLETON_SETTING,

    /** 멱등 키 원장 — 지우면 중복 발송 방어가 열린다(relay 가 이 표로 접는다). */
    IDEMPOTENCY_LEDGER,

    /** 개정 이력 — 지우면 전략 왕복 감사가 사라진다. */
    REVISION_HISTORY,

    /** 수집 회계 원장 — PII 가 없고 수집 이력이 회계의 정본이다. */
    COLLECTION_LEDGER,

    /** 참조 데이터 — 시각 열 자체가 없다. */
    REFERENCE_DATA,

    /** Flyway 이력 — 건드리면 `validate()` 가 깨진다. */
    SCHEMA_HISTORY,

    /**
     * 추가 전용 감사 원장이고 **담당자 PII 가 들어올 경로가 없다.** `notice_audit.previous_row`
     * 는 `to_jsonb(OLD)`(V2 `notice_audit_insert`)라 `notice` 의 열 집합뿐이고, 담당자 키는 필드
     * 계약 레지스트리에 애초에 등재되지 않는다(D-3H-7, `FieldContractTest` 가 어떤 rawName 에도
     * `Ofcl` 가 없음을 단언). 파기도 마스킹도 하지 않는다 — 여기에 마스킹을 붙이면 얻는 것 없이
     * append-only 트리거의 구멍이 하나 더 생긴다(`RawObservationMaskingGuardTest` 가 그 성질을
     * 이 표에서도 재서 「구멍이 하나뿐」임을 잠근다).
     */
    AUDIT_WITHOUT_PII,
}

/** 파기 술어의 추가 조건 — 「시각만」과 「시각 + 종단 상태」 둘뿐이다. */
internal sealed interface PurgeCondition {
    data object TimeOnly : PurgeCondition

    /**
     * 상태 열이 [values] 안일 때만 지운다(운영자 결정 A-4 「미발송·임대 중 행 제외」).
     * `outbox` 의 `PENDING`·`CLAIMED`·`FAILED` 가 이 집합 밖이라 살아남는다.
     */
    data class TerminalStates(
        val column: String,
        val values: Set<String>,
    ) : PurgeCondition
}

/**
 * 표 하나의 보존 규칙(M6/6B-3 P-2). **불법 상태를 타입으로 막는다** — 「PURGE 인데 기준 시각
 * 열이 없다」·「EXEMPT 인데 마스킹 열이 있다」가 표현되지 않는다(열거 + nullable 필드로 두면
 * 둘 다 표현된다).
 */
internal sealed interface RetentionRule {
    val table: String
    val treatment: RetentionTreatment

    data class Purge(
        override val table: String,
        val insertedAtColumn: String,
        val condition: PurgeCondition,
    ) : RetentionRule {
        override val treatment: RetentionTreatment get() = RetentionTreatment.PURGE
    }

    data class MaskPii(
        override val table: String,
        val insertedAtColumn: String,
        val maskedAtColumn: String,
        val keyColumn: String,
        /** 원문 열 — 남김 집합으로 **반전 마스킹**한다(allow-list 밖은 전부 사라진다). */
        val rawColumn: String,
        /**
         * 투영 열 — 남김 집합을 **그 행 자신에게서** 얻는 자리이면서 **자신도 마스킹 대상**이다
         * (운영자 결정 2026-10-14, D-6B3-3 ①). 둘째가 아니면 업체명은 이 열에 영구히 남는다.
         */
        val projectionColumn: String,
        /**
         * 지우는 PII 를 **개념 축**으로 선언한다(rawName 열거가 아니다) — 상류가 그 개념에 키를
         * 더하면 계약을 지나 자동으로 따라온다([maskedRawNamesOf]).
         */
        val maskedConcepts: Set<FieldConcept>,
    ) : RetentionRule {
        override val treatment: RetentionTreatment get() = RetentionTreatment.MASK_PII

        /** V18 관문 ④가 불변 비교에서 **빼는 집합**과 같아야 하는 집합. */
        val maskedColumns: Set<String> get() = setOf(rawColumn, projectionColumn)
    }

    data class CascadeWithParent(
        override val table: String,
        val parentTable: String,
        /**
         * 이 표에도 **기한 가드가 걸린다**(운영자 결정 2026-10-14, D-6B3-3 ② — vr F-3). 부모를
         * 지우면 `ON DELETE CASCADE` 가 자식을 함께 지우므로, 기한 안쪽 자식이 있으면 부모를
         * 남긴다. 그 판정에 쓰는 자식의 적재 시각 열이다(비-CASCADE 자식과 같은 성질).
         */
        val insertedAtColumn: String,
    ) : RetentionRule {
        override val treatment: RetentionTreatment get() = RetentionTreatment.CASCADE_WITH_PARENT
    }

    data class Exempt(
        override val table: String,
        val reason: ExemptReason,
    ) : RetentionRule {
        override val treatment: RetentionTreatment get() = RetentionTreatment.EXEMPT
    }
}

/**
 * **보존 분류 레지스트리** — 운영자 결정 A-2·A-3·A-4 를 표 단위로 편 것이다.
 *
 * 이 목록은 **스키마 정본과 집합 등식**이어야 한다(설계 검토 우회 7): 등식이 깨지면
 * `PurgeExpiredData` 가 아무것도 지우지 않고 `UnclassifiedTables` 로 거부하고,
 * `RetentionRegistrySchemaEqualityTest` 가 빌드에서 먼저 붉어진다. 그래서 V19 가 표를 더하면
 * **분류를 적지 않고는 초록이 되지 않는다**.
 *
 * 기준 시각은 **적재 시각 축 하나**다(A-2) — 표마다 DB 가 적재 때 찍는 열
 * (`inserted_at`·`created_at`·`recorded_at`)이고, `api_request_audit` 만 애플리케이션이 싣는
 * `occurred_at`(그 표의 유일한 시각 열이자 적재 시각 — `RequestAuditFilter` 가 요청 종료
 * 시점에 한 행을 쓴다)이다.
 */
internal val RETENTION_TABLE_RULES: List<RetentionRule> =
    listOf(
        // --- 마스킹(A-3) — 지우지 않고 PII 만 지운다. 6G 백테스트 표본을 남긴다.
        RetentionRule.MaskPii(
            table = "raw_observation",
            insertedAtColumn = "inserted_at",
            maskedAtColumn = "pii_masked_at",
            keyColumn = "observation_key",
            rawColumn = "payload",
            projectionColumn = "payload_fields",
            // 운영자 결정 2026-10-14 — 업체명은 90일 결정의 대상이다. 담당자 키와 달리 이 둘은
            // 계약에 **등재되어** 투영에 실리므로, 개념 축으로 빼지 않으면 남김 집합에 들어온다.
            maskedConcepts = setOf(FieldConcept.BID_COMPANY_NAME, FieldConcept.AWARD_COMPANY_NAME),
        ),
        // --- 파기(결정 문면 「공고」) ---
        RetentionRule.Purge("notice", "created_at", PurgeCondition.TimeOnly),
        RetentionRule.Purge("opening_result", "created_at", PurgeCondition.TimeOnly),
        RetentionRule.Purge("opening_reserve_price", "created_at", PurgeCondition.TimeOnly),
        RetentionRule.Purge("qualification_text", "created_at", PurgeCondition.TimeOnly),
        RetentionRule.Purge("notice_requirement", "created_at", PurgeCondition.TimeOnly),
        RetentionRule.Purge("rejected_write", "recorded_at", PurgeCondition.TimeOnly),
        // --- 파기(결정 문면 「audit」) ---
        RetentionRule.Purge("api_request_audit", "occurred_at", PurgeCondition.TimeOnly),
        // --- 파기(결정 문면 「평가 이력」의 유일한 영속 흔적, A-4) ---
        RetentionRule.Purge(
            table = "outbox",
            insertedAtColumn = "inserted_at",
            condition =
                PurgeCondition.TerminalStates(
                    column = "state",
                    // V6 의 CHECK 어휘 다섯 가운데 **배달 하나**다(D-6B3-3 ③). `FAILED`·`ISOLATED`
                    // 도 종단이지만 둘 다 **한 번도 보내지지 않은** 상태라 A-4 의 「미발송 제외」에
                    // 들어간다 — 그 행은 잃은 알림의 유일한 흔적이다(알려진 제한: 영구 잔류).
                    values = setOf("DELIVERED"),
                ),
        ),
        // --- 파기(버려진 편집 세션, A-4 — `OPEN-6A2B-ABANDONED-SESSIONS` 를 닫는다) ---
        RetentionRule.Purge("edit_session", "created_at", PurgeCondition.TimeOnly),
        // --- 부모와 함께 사라진다(FK ON DELETE CASCADE) — 단 기한 안쪽 행은 부모를 남긴다 ---
        RetentionRule.CascadeWithParent(
            table = "notice_requirement_row",
            parentTable = "notice_requirement",
            insertedAtColumn = "created_at",
        ),
        // --- 시간 기준 파기 대상 아님(A-4) ---
        RetentionRule.Exempt("notice_audit", ExemptReason.AUDIT_WITHOUT_PII),
        RetentionRule.Exempt("inbox", ExemptReason.IDEMPOTENCY_LEDGER),
        RetentionRule.Exempt("collection_run", ExemptReason.COLLECTION_LEDGER),
        RetentionRule.Exempt("operator_strategy", ExemptReason.SINGLETON_SETTING),
        RetentionRule.Exempt("operator_profile", ExemptReason.SINGLETON_SETTING),
        RetentionRule.Exempt("operator_strategy_revision", ExemptReason.REVISION_HISTORY),
        RetentionRule.Exempt("provenance_authority", ExemptReason.REFERENCE_DATA),
        RetentionRule.Exempt("flyway_schema_history", ExemptReason.SCHEMA_HISTORY),
    )

internal fun RetentionRule.asTarget(): RetentionTarget = RetentionTarget(table, treatment)

/**
 * 마스킹 규칙의 개념을 **계약을 지나** rawName 으로 편다(D-6B3-3 ①). 키 이름의 사본을 이
 * 파일에 두지 않는다 — 상류가 그 개념에 키를 더하면 여기도 함께 따라온다.
 *
 * 계약이 없는 개념은 **구성 오류로 던진다**: 조용히 빈 집합이 되면 「업체명을 지운다」가
 * 아무 일도 하지 않으면서 초록이다(공허의 가장 비싼 꼴 — 되돌릴 수 없는 경로에서).
 */
internal fun maskedRawNamesOf(
    rule: RetentionRule.MaskPii,
    contracts: KonepsFieldContractRegistry,
): Set<String> {
    val byConcept = rule.maskedConcepts.associateWith { contracts.contractsFor(it).map { c -> c.rawName.name } }
    val empty = byConcept.filterValues { it.isEmpty() }.keys
    require(empty.isEmpty()) { "마스킹 개념에 필드 계약이 없다: $empty" }
    return byConcept.values.flatten().toSet()
}

