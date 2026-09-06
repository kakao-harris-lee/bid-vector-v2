package bidvector.adapters.contract

import java.math.BigDecimal

/**
 * M2/2A가 세운 decimal string 정규형 규칙(scale 보존, 지수 표기 거부) — `ContractRoundTripTest`가
 * 정의했고(`Rate.fraction`), `PredictionContractTest`가 2B의 새 `Rate` 자리(`observed_bid_rate`·
 * `bid_rate`·`award_rate`)와 decimal string 넷(`Weight.fraction`·`PriceFitness.score`·
 * `Uncertainty.dispersion`·`Uncertainty.estimate_margin`)에 그대로 재사용한다(verifier r1 F-3 —
 * 두 test 파일에 같은 술어를 복제하지 않는다, CPD).
 *
 * `""`·지수 표기(`e`/`E`)·앞뒤 공백·`NaN`은 전부 거부한다 — `BigDecimal(String)`은 공백·`NaN`을
 * 파싱하지 않고 예외를 던지므로 `runCatching`이 그대로 `false`로 접는다(F-6 대칭 — Python
 * 쪽은 `Decimal`이 공백을 허용하고 `NaN`이 생성은 되므로 같은 판정을 얻으려면 대칭 규칙을
 * 별도로 강제해야 한다, `test_prediction_contract.py`의 `_is_normalized_fraction` 참고).
 */
internal fun isNormalizedFraction(fraction: String): Boolean {
    if (fraction.isEmpty() || fraction.any { it == 'e' || it == 'E' }) return false
    return runCatching { BigDecimal(fraction).toPlainString() == fraction }.getOrDefault(false)
}
