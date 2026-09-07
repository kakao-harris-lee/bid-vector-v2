package bidvector.procurement

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment

/**
 * 파생이 원본을 덮지 않는다 — 타입(⑥). 같은 자리(기초금액)에 다른 개념이 오면 타입이
 * 갈린다(§5.2 「자리가 같아야 한다면 타입이 달라야 한다」).
 */
sealed interface ResolvedBaseAmount {
    val amount: BaseAmount

    /**
     * 공고 게시값 그대로 — **`Provenance.Published`만 받는다.** 유일한 공개 생성 경로는
     * [of]이고 그 서명이 `Provenance.Published` 타입을 요구하므로, 예산 키·개찰 역산으로
     * 조립한 `BaseAmount`는 이 자리에 컴파일되지 않는다(위협 모델 우회 (4)).
     */
    @ConsistentCopyVisibility
    data class Direct internal constructor(
        override val amount: BaseAmount,
    ) : ResolvedBaseAmount {
        companion object {
            fun of(
                won: Long,
                currency: Currency,
                vatTreatment: VatTreatment,
                provenance: Provenance.Published,
            ): Direct = Direct(BaseAmount(won, currency, vatTreatment, provenance))
        }
    }

    /** 예산 키 폴백 — `sourceKey`는 감사를 위해 어느 필드에서 왔는지 남긴다. */
    data class FallbackFromBudget(
        val sourceKey: RawKey,
        override val amount: BaseAmount,
    ) : ResolvedBaseAmount {
        init {
            require(amount.provenance is Provenance.FilledFromBudgetKey) {
                "FallbackFromBudget은 Provenance.FilledFromBudgetKey만 받는다: ${amount.provenance}"
            }
        }
    }

    /** 개찰 역산 — 수학 자체는 3A 범위 밖이다(3D). 여기서는 파생 결과 + provenance만 나른다. */
    data class DerivedFromOpeningAmount(
        override val amount: BaseAmount,
    ) : ResolvedBaseAmount {
        init {
            require(amount.provenance is Provenance.DerivedFromOpening) {
                "DerivedFromOpeningAmount는 Provenance.DerivedFromOpening만 받는다: ${amount.provenance}"
            }
        }
    }
}

/** [Provenance]의 variant 식별자 — `isAuthoritative` 데이터 표의 키(리플렉션 대신 소진 `when`). */
enum class ProvenanceKind {
    PUBLISHED,
    DERIVED_FROM_OPENING,
    FILLED_FROM_BUDGET_KEY,
    COPIED_FROM_BASE_AMOUNT,
    OPERATOR_DECLARED,
    UNDECLARED,
}

fun Provenance.kind(): ProvenanceKind =
    when (this) {
        is Provenance.Published -> ProvenanceKind.PUBLISHED
        Provenance.DerivedFromOpening -> ProvenanceKind.DERIVED_FROM_OPENING
        is Provenance.FilledFromBudgetKey -> ProvenanceKind.FILLED_FROM_BUDGET_KEY
        Provenance.CopiedFromBaseAmount -> ProvenanceKind.COPIED_FROM_BASE_AMOUNT
        Provenance.OperatorDeclared -> ProvenanceKind.OPERATOR_DECLARED
        Provenance.Undeclared -> ProvenanceKind.UNDECLARED
    }

/**
 * "덮을 수 있는가"는 **술어가 아니라 데이터**다(§5.1) — 어휘가 늘 때 이 표 하나만 갱신하면
 * 된다. 표에 없으면 기본은 **보수(fill-only, false)**다 — [isAuthoritative]가 그 기본을 낸다.
 */
val IS_AUTHORITATIVE: Map<ProvenanceKind, Boolean> =
    mapOf(
        ProvenanceKind.PUBLISHED to true,
        ProvenanceKind.OPERATOR_DECLARED to true,
    )

fun isAuthoritative(kind: ProvenanceKind): Boolean = IS_AUTHORITATIVE[kind] ?: false

/**
 * 점유 가드 **판정**(3A) — 이미 값이 있으면 권위 없는 유입으로 덮지 않는다(§5.1 규율 1).
 * **실행**(실제 write 시점의 적용)은 3D 소유다(COL-02 경계) — 이 함수는 「덮어도 되는가」의
 * 참/거짓만 낸다.
 */
fun mayOverwrite(
    existingProvenance: Provenance?,
    incomingProvenance: Provenance,
): Boolean = existingProvenance == null || isAuthoritative(incomingProvenance.kind())
