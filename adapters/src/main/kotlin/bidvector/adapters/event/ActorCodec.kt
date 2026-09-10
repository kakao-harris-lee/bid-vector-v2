package bidvector.adapters.event

import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.OperatorId

/**
 * [Actor] ↔ (kind, detail) 두 컬럼 왕복 — 3D `ProvenanceCodec`(kind+detail 컬럼 짝)와 같은
 * 형태다. 둘 다 `null`이면 actor 없음([bidvector.workflow.event.EventEnvelope.actor]는
 * nullable).
 */
internal object ActorCodec {
    fun kindOf(actor: Actor?): String? =
        when (actor) {
            null -> null
            is Actor.Operator -> "OPERATOR"
            is Actor.System -> "SYSTEM"
        }

    fun detailOf(actor: Actor?): String? =
        when (actor) {
            null -> null
            is Actor.Operator -> actor.id.value
            is Actor.System -> actor.reason
        }

    /** (kind, detail) → [Actor] — [kindOf]·[detailOf]의 역함수. 알 수 없는 kind는 예외(fail-closed). */
    fun decode(
        kind: String?,
        detail: String?,
    ): Actor? =
        when (kind) {
            null -> {
                null
            }

            "OPERATOR" -> {
                val operatorId = requireNotNull(detail) { "OPERATOR actor는 detail이 필요하다" }
                Actor.Operator(OperatorId(operatorId))
            }

            "SYSTEM" -> {
                Actor.System(requireNotNull(detail) { "SYSTEM actor는 detail이 필요하다" })
            }

            else -> {
                error("알 수 없는 actor_kind 이다: $kind")
            }
        }
}
