package bidvector.adapters.contract

/*
 * M4/4D-1(D-4D-3) — 이 규칙의 실물은 `adapters/src/main/kotlin/bidvector/adapters/ml/
 * FractionRules.kt`로 승격됐다. 이 파일은 2A~2D 소비자 test(`PredictionContractTest` 등)가
 * 같은 이름·시그니처로 계속 통과하도록 **위임만** 한다(CPD 0, scope.md S-3).
 */

/** decimal string 정규형 — scale 보존, 지수 표기 거부. 실물은 `bidvector.adapters.ml.isNormalizedFraction`. */
internal fun isNormalizedFraction(fraction: String): Boolean = bidvector.adapters.ml.isNormalizedFraction(fraction)
