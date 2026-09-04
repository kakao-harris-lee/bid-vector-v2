package bidvector.sharedkernel

/**
 * 원화 확정 금액. 다섯 성분
 * (`amount`·`currency`·`basis`·`vatTreatment`·`provenance`)을 갖는다(`ADR 0002` D-1).
 * `basis`는 생성자 파라미터가 아니라 각 구현이 내는 파생 값이다 — `copy()`로 basis를 바꿀 수
 * 없다. `data-dictionary.md` §1.1 서명과의 형태 차이는 설계 검토 §5 L-4로 등재한다.
 *
 * 이 상위 타입에 `Comparable`이나 이항 연산을 두지 않는다 — 두면 서로 다른 basis 금액이
 * `f(a: Money, b: Money)`로 다시 섞인다(설계 검토 §1a·§5 L-11, 「개념마다 타입 하나」의
 * 단일 실패점). **여섯 구현 타입도 각자의 공개 `Comparable<Self>`를 갖지 않는다**(Codex
 * 1차 #2 정정 — 이전 판은 `won`만 비교해 `VAT` `UNKNOWN`/`INCLUSIVE`도 정렬됐고 동일 금액은
 * `VAT`가 달라도 `compareTo`가 0이었다). 비교가 필요하면 `sameKnownVat` 전건을 건
 * [compareKnownVat]가 유일한 경로다.
 */
sealed interface Money {
    val currency: Currency
    val basis: Basis
    val vatTreatment: VatTreatment
    val provenance: Provenance
}

/** 원 단위 값. `shared-kernel` 밖으로 내지 않는다 — `R-BASIS-01` 컴파일 차단의 필요조건. */
internal val Money.amount: Long
    get() =
        when (this) {
            is BaseAmount -> won
            is EstimatedAmount -> won
            is YegaAmount -> won
            is BidAmount -> won
            is AllocatedBudget -> won
            is AwardAmount -> won
        }

/**
 * 다섯 성분을 함께 내는 유일한 공개 export 경로 — `R-BASIS-06`이 금지한 "값만 있고 출처가
 * 없는 응답"을 구성상 만들 수 없다.
 */
data class AmountRecord(
    val won: Long,
    val currency: Currency,
    val basis: Basis,
    val vatTreatment: VatTreatment,
    val provenance: Provenance,
)

fun Money.export(): AmountRecord = AmountRecord(amount, currency, basis, vatTreatment, provenance)

/**
 * `sameKnownVat` 전건을 건 유일한 비교 경로다(Codex 1차 #2) — 여섯 `Money` 타입이 갖던
 * 공개 `Comparable`은 `won`만 비교해 `VAT` `UNKNOWN`과 `INCLUSIVE`도 정렬되고 동일 금액이면
 * `VAT`가 달라도 `compareTo`가 0을 냈다. 그 구현을 없애고 이 함수로 대체한다. 제네릭
 * `T : Money`가 같은 타입만 받으므로 basis 교차 비교는 이 함수로도 열리지 않는다(L-11).
 *
 * `Measurement` 대신 `Fact`를 반환한다 — 비교는 정책 version 을 소비하지 않는다
 * (`sumOfBaseAmounts`가 이미 같은 이유로 `Fact`를 쓴다). `Measurement.Measured`가 요구하는
 * `policyVersion`/`sampleSize`는 비교에 자연스러운 입력이 없어 지어내는 값이 되므로(매직
 * 넘버 금지 원칙과 같은 성질), Codex 전달문의 "Unmeasurable" 표현 대신 `Fact.Absent`로
 * 낸다 — 사유 어휘(`VAT_TREATMENT_MISMATCH`)는 그대로다.
 */
fun <T : Money> compareKnownVat(
    left: T,
    right: T,
): Fact<Int> =
    if (sameKnownVat(left.vatTreatment, right.vatTreatment)) {
        Fact.Known(left.amount.compareTo(right.amount))
    } else {
        Fact.Absent(ReasonCode.VAT_TREATMENT_MISMATCH)
    }

private fun requireNonNegative(won: Long) {
    require(won >= 0L) { "금액은 음수일 수 없다: $won" }
}

/** 기초금액 / 사업금액(`bssAmt` 계열). 개념 정의는 `data-dictionary.md` §1.2. */
data class BaseAmount(
    internal val won: Long,
    override val currency: Currency,
    override val vatTreatment: VatTreatment,
    override val provenance: Provenance,
) : Money {
    override val basis: Basis = Basis.BASE_AMOUNT

    init {
        requireNonNegative(won)
    }
}

/** 추정가격(`presmptPrce`). 운영자 결정 2026-09-04(A1)로 승인 명세 이름을 채택했다. */
data class EstimatedAmount(
    internal val won: Long,
    override val currency: Currency,
    override val vatTreatment: VatTreatment,
    override val provenance: Provenance,
) : Money {
    override val basis: Basis = Basis.ESTIMATED

    init {
        requireNonNegative(won)
    }
}

/**
 * 예정가(`planned_price`). 개념은 `legacy-behavior`(`data-dictionary.md` §1.2).
 *
 * `vatTreatment`는 생성자 파라미터가 아니라 `Unknown` 고정이다(운영자 결정 2026-09-04
 * B9) — `data-dictionary.md` §1.2의 `Unknown` 정의("정의상 현재 값 — 미결의 표현이지
 * 답이 아니다")를 그대로 싣는다. `OPEN-DIC-04`(실제 과세 처리 값)는 이 선언으로 해소되지
 * 않는다 — 값이 정해지면 이 필드가 `INCLUSIVE`/`EXCLUSIVE`로 바뀌는 것이 해소다. 다른
 * 값을 넣을 생성 경로가 없으므로 `sameKnownVat` 전건이 이 타입을 낀 산술(예:
 * [assessmentRateAgainst])을 항상 `Unmeasurable`로 막는다.
 */
data class YegaAmount(
    internal val won: Long,
    override val currency: Currency,
    override val provenance: Provenance,
) : Money {
    override val basis: Basis = Basis.YEGA
    override val vatTreatment: VatTreatment = VatTreatment.UNKNOWN

    init {
        requireNonNegative(won)
    }
}

/**
 * 투찰가. 기초금액에 투찰율을 곱해 얻는 파생값 — 유일한 생성 경로는 [MoneyArithmetic.kt]의
 * 반올림 함수다. 생성자를 `internal`로 닫아 이 보증을 컴파일 시점에 강제한다(verifier r1
 * H-1) — `Rate`가 이미 쓴 처방과 같다. `@ConsistentCopyVisibility`가 없으면 기본 공개
 * `copy()`가 이 경계를 우회한다(Kotlin 2.4.10 `-Werror`가 그 경고를 실제로 낸다).
 */
@ConsistentCopyVisibility
data class BidAmount internal constructor(
    internal val won: Long,
    override val currency: Currency,
    override val vatTreatment: VatTreatment,
    override val provenance: Provenance,
) : Money {
    override val basis: Basis = Basis.BID

    init {
        requireNonNegative(won)
    }
}

/**
 * 배정예산(`asignBdgtAmt`·`bdgtAmt`). 운영자 결정 2026-09-04(A3)로 1B 금액 타입 집합에
 * 포함됐다. `vatTreatment`는 `YegaAmount`와 같은 이유로 `Unknown` 고정이다(B9).
 */
data class AllocatedBudget(
    internal val won: Long,
    override val currency: Currency,
    override val provenance: Provenance,
) : Money {
    override val basis: Basis = Basis.ALLOCATED_BUDGET
    override val vatTreatment: VatTreatment = VatTreatment.UNKNOWN

    init {
        requireNonNegative(won)
    }
}

/**
 * 낙찰가. 운영자 결정 2026-09-04(A3)로 1B 금액 타입 집합에 포함됐다. `vatTreatment`는
 * `YegaAmount`와 같은 이유로 `Unknown` 고정이다(B9) — [awardRateAgainst]가 항상
 * `Unmeasurable`이 된다.
 */
data class AwardAmount(
    internal val won: Long,
    override val currency: Currency,
    override val provenance: Provenance,
) : Money {
    override val basis: Basis = Basis.AWARD
    override val vatTreatment: VatTreatment = VatTreatment.UNKNOWN

    init {
        requireNonNegative(won)
    }
}
