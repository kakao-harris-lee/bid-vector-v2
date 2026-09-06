package bidvector.decision

import bidvector.sharedkernel.Rate
import java.math.BigDecimal

/**
 * provenance 판정이 소비하는 정책(D-5, D-12) — `ruleOrder`(순서·부분집합, D-12)와 술어 넷의
 * 임계값. `trustRatioMax` 는 슬롯만이다(OPEN-DEC-07 — V2 코퍼스 재유도 전 기본값 없음).
 * 나머지 임계값(정수 허용 오차·VAT 배수·VAT 허용 오차·예가 허용 오차)은 legacy 값이
 * `data-dictionary.md` §12.1 에 `legacy-behavior`로 등재돼 있으나, 이 타입은 값을 지어내지
 * 않는다 — 호출부(test 정책·runner)가 준다.
 *
 * `SuspectRatio` 가 `ruleOrder`에 선언되면서 `trustRatioMax` 가 없으면 구성 시점에 거부한다
 * (D-5 — "first-match 에서 건너뛰지 않고 정책 부재 실패로") — 그 규칙이 활성인데 임계값이
 * 없는 상태를 값으로 만들 수 없게 한다.
 */
data class ProvenancePolicyData(
    val ruleOrder: List<ProvenanceRuleId>,
    val trustRatioMax: Rate?,
    val cleanIntegerTolerance: BigDecimal,
    val vatMultiplier: BigDecimal,
    val vatTolerance: BigDecimal,
    val yegaTolerance: BigDecimal,
) {
    init {
        require(ruleOrder.isNotEmpty()) { "ruleOrder는 비어 있을 수 없다" }
        require(ruleOrder.toSet().size == ruleOrder.size) { "ruleOrder에 중복 규칙이 있다: $ruleOrder" }
        require(ProvenanceRuleId.SuspectRatio !in ruleOrder || trustRatioMax != null) {
            "SuspectRatio가 ruleOrder에 있으면 trustRatioMax가 필요하다(D-5 — 정책 부재 실패)"
        }
    }
}
