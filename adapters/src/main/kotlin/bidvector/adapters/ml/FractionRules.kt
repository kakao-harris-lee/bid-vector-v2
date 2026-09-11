package bidvector.adapters.ml

import java.math.BigDecimal

/**
 * M4/4D-1(D-4D-3) — M2/2A·2B `ContractFractionRules`(adapters/src/test/kotlin/bidvector/
 * adapters/contract) 를 main 으로 승격한 실물. decimal string 정규형 규칙(scale 보존,
 * 지수 표기 거부) — `RequestMapping.kt`·`ResponseMapping.kt`가 `Rate.fraction`·
 * `Weight.fraction`·`PriceFitness.score`·`Uncertainty.dispersion`·`Uncertainty.estimate_margin`
 * 매핑에 그대로 쓴다. `adapters/src/test/kotlin/bidvector/adapters/contract/
 * ContractFractionRules.kt`는 이제 이 함수를 위임만 한다(2A~2D 소비자 test 는 그대로
 * 통과해야 한다, scope.md S-3).
 *
 * `""`·지수 표기(`e`/`E`)·앞뒤 공백·`NaN`은 전부 거부한다 — `BigDecimal(String)`은 공백·`NaN`을
 * 파싱하지 않고 예외를 던지므로 `runCatching`이 그대로 `false`로 접는다.
 */
internal fun isNormalizedFraction(fraction: String): Boolean {
    if (fraction.isEmpty() || fraction.any { it == 'e' || it == 'E' }) return false
    return runCatching { BigDecimal(fraction).toPlainString() == fraction }.getOrDefault(false)
}
