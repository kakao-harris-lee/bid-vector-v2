package bidvector.workflow.notification

/**
 * 배달 경로 판정 커널 입력 — 정책 축(scope.md ②, 설계 검토 (0)). 호출부(app/4B)가 계산한
 * 사실을 받는다 — `채널 활성`과 `route 유무`는 다른 축이다.
 */
data class ChannelPolicyFacts(
    val channelEnabled: Boolean,
    val routeConfigured: Boolean,
)

/** 배달 경로 판정 커널 입력 — 환경 축(scope.md ②⑦). [mode]는 정책 데이터의 전사상 매핑 결과다. */
data class EnvironmentFacts(
    val environment: RuntimeEnvironment,
    val mode: DeliveryMode,
)

/** 정책 축 판정(scope.md ②) — 채널 비활성이 route 없음보다 먼저 이긴다(첫 위반 승). */
sealed interface PolicyVerdict {
    data object Allowed : PolicyVerdict

    data object ChannelDisabled : PolicyVerdict

    data object TargetMissing : PolicyVerdict
}

/** 환경 축 판정(scope.md ②⑥). */
sealed interface EnvironmentVerdict {
    data object Allowed : EnvironmentVerdict

    data object DryRun : EnvironmentVerdict

    data class Blocked(
        val environment: RuntimeEnvironment,
    ) : EnvironmentVerdict
}

/** 억제 사유(scope.md ②) — 자유 문자열 필드 0, sealed 넷(우회 (4)). */
sealed interface SuppressionReason {
    data object ChannelDisabled : SuppressionReason

    data object TargetMissing : SuppressionReason

    data class EnvironmentBlocked(
        val environment: RuntimeEnvironment,
    ) : SuppressionReason

    data object DryRun : SuppressionReason
}

/** [DeliveryPlan]의 결과(scope.md ②) — [DeliveryPlan.outcome]에서만 얻는다. */
sealed interface PlanOutcome {
    data object Send : PlanOutcome

    data class Suppressed(
        val reason: SuppressionReason,
    ) : PlanOutcome
}

/**
 * 배달 경로 판정 결과(scope.md ②, 설계 검토 (2)) — [policy]·[environment] 두 필드로 갖고
 * [outcome]은 그 둘에서 계산된다. `internal constructor`라 필드와 어긋난 결과를 손으로
 * 못 만든다(우회 (8)) — [resolveDeliveryPlan]만 만든다.
 */
@ConsistentCopyVisibility
data class DeliveryPlan internal constructor(
    val policy: PolicyVerdict,
    val environment: EnvironmentVerdict,
) {
    val outcome: PlanOutcome = planOutcomeOf(policy, environment)
}

private fun planOutcomeOf(
    policy: PolicyVerdict,
    environment: EnvironmentVerdict,
): PlanOutcome =
    when (policy) {
        PolicyVerdict.ChannelDisabled -> {
            PlanOutcome.Suppressed(SuppressionReason.ChannelDisabled)
        }

        PolicyVerdict.TargetMissing -> {
            PlanOutcome.Suppressed(SuppressionReason.TargetMissing)
        }

        PolicyVerdict.Allowed -> {
            environmentOutcomeOf(environment)
        }
    }

private fun environmentOutcomeOf(environment: EnvironmentVerdict): PlanOutcome =
    when (environment) {
        is EnvironmentVerdict.Blocked -> {
            PlanOutcome.Suppressed(SuppressionReason.EnvironmentBlocked(environment.environment))
        }

        EnvironmentVerdict.DryRun -> {
            PlanOutcome.Suppressed(SuppressionReason.DryRun)
        }

        EnvironmentVerdict.Allowed -> {
            PlanOutcome.Send
        }
    }

/**
 * 배달 경로 판정 커널(scope.md ②) — 순수 함수, 사실 둘을 받아 [DeliveryPlan]을 낸다.
 * 첫 위반이 이긴다: 정책(비활성 → route 없음) 다음 환경(차단 → dry-run). 비활성+dry-run
 * 은 비활성이다(정책이 먼저 확정되면 환경을 보지 않는다).
 */
fun resolveDeliveryPlan(
    policy: ChannelPolicyFacts,
    environment: EnvironmentFacts,
): DeliveryPlan {
    val policyVerdict =
        when {
            !policy.channelEnabled -> PolicyVerdict.ChannelDisabled
            !policy.routeConfigured -> PolicyVerdict.TargetMissing
            else -> PolicyVerdict.Allowed
        }
    val environmentVerdict =
        when (environment.mode) {
            DeliveryMode.Live -> EnvironmentVerdict.Allowed
            DeliveryMode.DryRun -> EnvironmentVerdict.DryRun
            DeliveryMode.Blocked -> EnvironmentVerdict.Blocked(environment.environment)
        }
    return DeliveryPlan(policyVerdict, environmentVerdict)
}
