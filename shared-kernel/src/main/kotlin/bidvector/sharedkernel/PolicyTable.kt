package bidvector.sharedkernel

import java.math.BigDecimal

/**
 * 부가세율 — 운영자 결정 2026-08-28(U-1b), `authoritative`(`data-dictionary.md` §12.1).
 * 코퍼스 무관 정책이라 `ruleVersion` 한 축이다(운영자 결정 2026-09-04, A6).
 */
val VAT_RATE_POLICY: EffectiveDatedPolicy<BigDecimal> =
    EffectiveDatedPolicy(
        source = "data-dictionary.md §12.1 (U-1b, 2026-08-28)",
        entries = listOf(EffectiveFrom.Initial to BigDecimal("0.10")),
    )

/**
 * 금액 축의 반올림 자리수는 이미 정의로 닫혔다 — 원 단위 정수(`data-dictionary.md` §1.1 정의 ①).
 * `mode` 값은 `OPEN-DIC-10`이 미결이라 이 표에 값을 두지 않는다 — 호출부가 `RoundingPolicy`를
 * 직접 구성해 주입한다.
 */
const val MONEY_AXIS_SCALE_DIGITS: Int = 0
