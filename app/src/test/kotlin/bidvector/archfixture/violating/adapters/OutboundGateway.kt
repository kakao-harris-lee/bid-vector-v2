package bidvector.archfixture.violating.adapters

/** 역방향 의존 fixture 의 대상. adapters 층은 domain 층에서 보이면 안 된다. */
class OutboundGateway {
    fun send(payload: String): String = payload.uppercase()
}
