package bidvector.adapters.ml

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import contract.bidvector.ml.v1.AmountProvenanceKind
import contract.bidvector.ml.v1.Basis
import bidvector.sharedkernel.Basis as DomainBasis
import contract.bidvector.ml.v1.Currency as ProtoCurrency
import contract.bidvector.ml.v1.Money as ProtoMoney
import contract.bidvector.ml.v1.VatTreatment as ProtoVatTreatment

/**
 * M4/4D-1(scope.md ①) — `bidvector.sharedkernel.Money`(다섯 성분, [Money.export]로만 나온다)
 * → 계약 `Money`. `RequestMapping.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions` — 한
 * 파일에 열넷을 몰아두지 않는다). `internal` — `RequestMapping.kt`(다른 파일, 같은 모듈)가
 * [toProtoMoney]를 그대로 쓴다.
 */
internal fun Money.toProtoMoney(): ProtoMoney {
    val record = export()
    return ProtoMoney
        .newBuilder()
        .setAmountWon(record.won)
        .setCurrency(record.currency.toProto())
        .setBasis(record.basis.toProto())
        .setVatTreatment(record.vatTreatment.toProto())
        .setProvenance(record.provenance.toProto())
        .build()
}

private fun Currency.toProto(): ProtoCurrency =
    when (this) {
        Currency.KRW -> ProtoCurrency.CURRENCY_KRW
    }

private fun DomainBasis.toProto(): Basis =
    when (this) {
        DomainBasis.BASE_AMOUNT -> Basis.BASIS_BASE_AMOUNT
        DomainBasis.ESTIMATED -> Basis.BASIS_ESTIMATED
        DomainBasis.YEGA -> Basis.BASIS_YEGA
        DomainBasis.BID -> Basis.BASIS_BID
        DomainBasis.ALLOCATED_BUDGET -> Basis.BASIS_ALLOCATED_BUDGET
        DomainBasis.AWARD -> Basis.BASIS_AWARD
    }

private fun VatTreatment.toProto(): ProtoVatTreatment =
    when (this) {
        VatTreatment.INCLUSIVE -> ProtoVatTreatment.VAT_TREATMENT_INCLUSIVE
        VatTreatment.EXCLUSIVE -> ProtoVatTreatment.VAT_TREATMENT_EXCLUSIVE
        VatTreatment.UNKNOWN -> ProtoVatTreatment.VAT_TREATMENT_UNKNOWN
    }

private fun Provenance.toProto(): AmountProvenanceKind =
    when (this) {
        is Provenance.Published -> AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_PUBLISHED
        Provenance.DerivedFromOpening -> AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_DERIVED_FROM_OPENING
        is Provenance.FilledFromBudgetKey -> AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_FILLED_FROM_BUDGET_KEY
        Provenance.CopiedFromBaseAmount -> AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_COPIED_FROM_BASE_AMOUNT
        Provenance.OperatorDeclared -> AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_OPERATOR_DECLARED
        Provenance.Undeclared -> AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_UNDECLARED
    }
