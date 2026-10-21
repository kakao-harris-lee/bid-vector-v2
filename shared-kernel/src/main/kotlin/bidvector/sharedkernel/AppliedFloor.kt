package bidvector.sharedkernel

import java.math.BigDecimal

/**
 * 적용 하한 — **같은 basis 의 곱에서만 나온다**(M6/G1-DEC05, D-G1DEC05-2).
 *
 * 앞 판의 [UnroundedBidAmount.roundedWith]는 하한을 날 `BigDecimal?`로 받았다. 그 자리에는
 * **어떤 수든** 들어갔고, 특히 예정가격 기준 율(게시 낙찰하한율)에서 나온 값도 컴파일됐다 —
 * 투찰가는 기초금액 기준이므로 그 조합은 basis 가 섞인 비교이고, `capability-map.md` DEC-12
 * A5 가 적는 legacy 결함 형태(「같은 임계를 각자 구현해 경계 근처에서 판정이 갈린다」)와 같은
 * 축이다. DEC-02 무조건 acceptance 셋째(「서로 다른 basis 의 금액이 하나의 min/max 연산에 함께
 * 들어갈 수 없다」)는 **하한 자리에도** 걸려야 한다 — 이 타입이 그 자리를 닫는다.
 *
 * 생성 경로는 [appliedFloorFrom] 하나이고 `raw`는 `internal`이다 — `Rate`·`BidAmount`·
 * `Derived`가 이미 쓴 처방(`internal constructor` + `@ConsistentCopyVisibility`)과 같다.
 */
@ConsistentCopyVisibility
data class AppliedFloor internal constructor(
    internal val raw: BigDecimal,
)

/**
 * 적용 하한 = 기초금액 × **투찰율 하한**. 두 입력이 같은 기초금액 basis 라 섞이지 않는다 —
 * [times]와 정확히 같은 곱이므로 하한과 피교차값이 같은 축 위에 있음이 구성으로 보장된다.
 * 하한 자신은 원 단위로 딱 떨어지지 않을 수 있다(그 반례가 `ArithmeticTest`의 `P-2b`다).
 *
 * `FloorRate`를 받지 않는다 — 게시 낙찰하한율은 **예정가격 기준** 율이라 기초금액에 곱하면
 * basis 가 섞인다(`data-dictionary.md` §1.4.2 · `capability-map.md` DEC-04 분류 근거).
 * 하한율을 투찰율로 읽는 해석은 도메인 결정이고 이 모듈이 하지 않는다 — 그 결정은 `decision`
 * 의 메뉴 정책(`FloorRateReading`)이 든다.
 */
fun BaseAmount.appliedFloorFrom(rate: BidRate): AppliedFloor =
    AppliedFloor(BigDecimal(amount).multiply(rate.rate.fraction))
