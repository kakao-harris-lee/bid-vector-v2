package bidvector.adapters.persistence

import bidvector.procurement.ProvenanceKind
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import java.time.LocalDate

/**
 * `Provenance` ↔ (kind 문자열, detail 문자열) 왕복(D-3D-5) — canonical 테이블의
 * `*_provenance`/`*_provenance_detail` 컬럼 짝이 이 표현을 그대로 쓴다. `kind`는
 * [ProvenanceKind]의 `name()`(procurement가 이미 소유한 어휘, DB `provenance_authority`
 * seed와 같은 문자열) — 새 어휘를 짓지 않는다.
 */
internal object ProvenanceCodec {
    fun kindOf(provenance: Provenance): ProvenanceKind =
        when (provenance) {
            is Provenance.Published -> ProvenanceKind.PUBLISHED
            Provenance.DerivedFromOpening -> ProvenanceKind.DERIVED_FROM_OPENING
            is Provenance.FilledFromBudgetKey -> ProvenanceKind.FILLED_FROM_BUDGET_KEY
            Provenance.CopiedFromBaseAmount -> ProvenanceKind.COPIED_FROM_BASE_AMOUNT
            Provenance.OperatorDeclared -> ProvenanceKind.OPERATOR_DECLARED
            Provenance.Undeclared -> ProvenanceKind.UNDECLARED
        }

    /**
     * [kindOf]의 이름만 돌려주는 진입문(M6/6F-1 D-6F1-8) — `bidvector.procurement.ProvenanceKind`를
     * 허용 루트 밖(`bidvector.adapters.strategy` 등)에 반환형으로 노출하지 않는다. [kindOf]는
     * 그대로 둔다(`persistence` 패키지 자신·`JdbcOpeningResultRepository` 등 procurement가
     * 허용된 소비자가 쓴다) — 이 함수는 그 값의 `.name`만 원하는 소비자를 위한 별도 진입점이다.
     * `inline`이 아니다 — inline이면 본문이 호출부에 펴져 `ProvenanceKind` 좌표가 그대로
     * 호출부(컴파일된 클래스)에 실려 이 진입점의 목적이 무효가 된다.
     */
    fun kindNameOf(provenance: Provenance): String = kindOf(provenance).name

    /** 재구성에 필요한 detail — `Published.noticeRevision`·`FilledFromBudgetKey.key`만 값을 낸다. */
    fun detailOf(provenance: Provenance): String? =
        when (provenance) {
            is Provenance.Published -> provenance.noticeRevision.value
            is Provenance.FilledFromBudgetKey -> provenance.key
            else -> null
        }

    /** (kind, detail) → [Provenance] — [kindOf]·[detailOf]의 역함수. */
    fun decode(
        kind: String,
        detail: String?,
    ): Provenance =
        when (ProvenanceKind.valueOf(kind)) {
            ProvenanceKind.PUBLISHED -> {
                val noticeRevision = requireNotNull(detail) { "Published는 noticeRevision detail이 필요하다" }
                Provenance.Published(NoticeRound.of(noticeRevision))
            }

            ProvenanceKind.DERIVED_FROM_OPENING -> {
                Provenance.DerivedFromOpening
            }

            ProvenanceKind.FILLED_FROM_BUDGET_KEY -> {
                Provenance.FilledFromBudgetKey(requireNotNull(detail) { "FilledFromBudgetKey는 key detail이 필요하다" })
            }

            ProvenanceKind.COPIED_FROM_BASE_AMOUNT -> {
                Provenance.CopiedFromBaseAmount
            }

            ProvenanceKind.OPERATOR_DECLARED -> {
                Provenance.OperatorDeclared
            }

            ProvenanceKind.UNDECLARED -> {
                Provenance.Undeclared
            }
        }
}

/** [FloorRateOrigin] ↔ (kind, detail) — provenance와 같은 표현 형태. */
internal object FloorRateOriginCodec {
    const val NOTICE_VALUE_KIND = "NOTICE_VALUE"
    const val STATUTORY_TABLE_KIND = "STATUTORY_TABLE"
    private const val EFFECTIVE_FROM_INITIAL = "INITIAL"

    fun kindOf(origin: FloorRateOrigin): String =
        when (origin) {
            is FloorRateOrigin.NoticeValue -> NOTICE_VALUE_KIND
            is FloorRateOrigin.StatutoryTable -> STATUTORY_TABLE_KIND
        }

    fun detailOf(origin: FloorRateOrigin): String =
        when (origin) {
            is FloorRateOrigin.NoticeValue -> {
                origin.noticeRevision.value
            }

            is FloorRateOrigin.StatutoryTable -> {
                when (val from = origin.effectiveFrom) {
                    EffectiveFrom.Initial -> EFFECTIVE_FROM_INITIAL
                    is EffectiveFrom.On -> from.date.toString()
                }
            }
        }

    fun decode(
        kind: String,
        detail: String,
    ): FloorRateOrigin =
        when (kind) {
            NOTICE_VALUE_KIND -> FloorRateOrigin.NoticeValue(NoticeRound.of(detail))
            STATUTORY_TABLE_KIND -> FloorRateOrigin.StatutoryTable(statutoryEffectiveFrom(detail))
            else -> error("알 수 없는 FloorRateOrigin kind: $kind")
        }

    private fun statutoryEffectiveFrom(detail: String): EffectiveFrom =
        if (detail == EFFECTIVE_FROM_INITIAL) EffectiveFrom.Initial else EffectiveFrom.On(LocalDate.parse(detail))
}
