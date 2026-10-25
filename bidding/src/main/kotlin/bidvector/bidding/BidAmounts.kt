package bidvector.bidding

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.VatTreatment

/**
 * 투찰 금액이 **어디서 온 값인가** — 「메뉴 값 위장 금지」(계약 산출물 6)를 타입이 진다.
 *
 * 두 갈래가 **불법 상태를 표현하지 못하게** 짝지어져 있다: 메뉴에서 고른 값은 고른 안의
 * 참조를 **반드시** 들고, 운영자가 적은 값은 그 자리가 **없다**. `V21` 의
 * `CHECK ((amount_provenance = 'PRICE_MENU') = (chosen_option_ref IS NOT NULL))` 가 같은
 * 불변식의 둘째 겹이다.
 *
 * **[PriceMenu] 는 오늘 도달 불가다.** 투찰가 메뉴(DEC-05)가 출하 경로에 없고(ML-01 · 7C②
 * 대기), HTTP 요청 DTO 에 provenance · 메뉴 참조 필드가 **없어** use case 가
 * [OperatorEntered] 를 상수로 싣는다. 즉 운영자 입력이 메뉴 값으로 보이는 경로는 **요청으로
 * 표현할 수 없다** — 문장으로 금지하는 것이 아니다. 어휘를 지금 두는 이유는 나중에 CHECK
 * 어휘를 늘리는 둘째 마이그레이션을 피하기 위해서다.
 */
sealed interface BidAmountOrigin {
    /** 운영자가 직접 적은 금액 — 오늘 유일하게 도달 가능한 갈래다. */
    data object OperatorEntered : BidAmountOrigin

    /** 투찰가 메뉴에서 고른 안. 참조가 **필수**다. */
    data class PriceMenu(
        val chosenOption: PriceMenuRef,
    ) : BidAmountOrigin
}

/**
 * 운영자가 내려는(또는 이미 낸) 투찰 금액 — **원 단위 `Long`** 이고 통화 · 과세 처리 ·
 * 출처를 함께 나른다(`.claude/rules/kotlin-service.md` 「금액에는 currency, basis, VAT
 * treatment, provenance 가 필요하다」).
 *
 * **`sharedkernel.BidAmount` 가 아니다.** 그 타입은 `BaseAmount × BidRate` 파생 경로
 * (`UnroundedBidAmount.roundedWith`)로만 생기는 **계산된** 값이라 생성자가 `internal` 이다.
 * 운영자가 손으로 적은 값은 파생이 아니라 **진술**이므로 그 타입을 빌리면 「계산해서 나온
 * 값」이라는 거짓을 싣게 된다 — `procurement.ObservedBidAmount` 가 같은 이유로 자기 모듈에
 * 있다. basis 는 묻지 않아도 투찰가 한 축이라 축 이름을 다시 싣지 않는다.
 */
data class SubmittedBidAmount(
    val won: Long,
    val currency: Currency,
    val vatTreatment: VatTreatment,
    val origin: BidAmountOrigin,
) {
    init {
        require(won >= 0L) { "투찰 금액은 음수일 수 없다: $won" }
    }
}
