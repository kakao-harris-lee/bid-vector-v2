package bidvector.archfixture.violating.strategy

typealias Amount = Double

/**
 * typealias 우변(D-5) — 별칭 해석은 안 한다. **사용 자리**(`fun amount(): Amount`)가 아니라
 * **선언 자리**(`typealias Amount = Double`)의 우변이 걸린다. `private` 로 둬도 걸린다는 것은
 * `PublicApiTypesTest` 의 인라인 테스트가 잰다 — 이 fixture 는 실제로 쓰이는 형태를 고정한다.
 */
class TypeAliasDoubleApi {
    fun amount(): Amount = 0.0
}
