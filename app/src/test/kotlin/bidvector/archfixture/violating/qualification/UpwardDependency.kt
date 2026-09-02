package bidvector.archfixture.violating.qualification

import bidvector.archfixture.violating.adapters.OutboundGateway

/** domain 이 adapters 를 참조한다 — 의존 방향 역전. */
class UpwardDependency(
    private val gateway: OutboundGateway,
) {
    fun call(): String = gateway.send(javaClass.simpleName)
}
