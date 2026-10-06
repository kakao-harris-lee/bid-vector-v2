package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * D-6F10-15 — **dry-run 과 커밋 경로를 클래스 단위로 가른다.**
 *
 * 왜 패키지 규칙으로는 안 되는가: `EvaluationDryRunFactory`·`EvaluationDryRunRun` 과
 * `EvaluationCommitWiring` 이 **같은 `app.wiring` 패키지**에 산다. 패키지로 가르자면 dry-run
 * 쪽을 옮겨야 하는데 그 파일들은 이 slice 의 범위 밖이다.
 *
 * 왜 기존 게이트로는 부족한가: D-6A3-17(a) 셋은 **무변경으로 초록**이다 — 커밋 조립이
 * `adapters` 에 살고 `app` 은 조립 결과만 들므로 ②(`allowed-impls`)·③(`forbidden.outbox-types`)
 * 가 그대로 통과한다. 그런데 그 게이트의 **뜻**이 바뀌었다: 「app 이 outbox 에 못 닿는다」에서
 * 「app 은 어댑터 조립을 통해서만 닿는다」로. dry-run 의 effect 0(D-6A3-3)을 지키는 것은 이제
 * 그 셋이 아니라 ① 기존 E2E 의 outbox 전후 등식과 ② 이 규칙이다.
 *
 * 이 규칙이 보는 것은 **참조**다 — dry-run 클래스 집합이 커밋 조립 타입을 **이름으로 알지
 * 못한다**. 람다 본문·bridge 안의 `invokedynamic` 참조는 ArchUnit 의 의존 그래프가 보지
 * 못하므로(6G-2g 교훈) 거동 쪽은 E2E 등식이 든다. 그 분담이 이 KDoc 의 요점이고, 변이 실측이
 * 둘의 사각을 각각 보인다(게이트 초록 + E2E RED 를 함께 기록한다).
 */
internal class DryRunCommitSeparationRules {
    fun dryRunMustNotReferenceCommitTypes(
        dryRunClasses: Set<String>,
        commitTypes: Set<String>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that(haveFullNameIn(dryRunClasses))
                .should(referenceAnyNamed(commitTypes))
                .because("D-6F10-15 — dry-run 조립은 커밋 조립 타입을 참조하지 않는다(effect 0 유지)"),
        )
}

/**
 * 이름 집합으로 고른다 — 집합이 실재하지 않는 이름만 담으면 **고르는 클래스가 0** 이고,
 * `archunit.properties` 의 빈 `should` 실패 핀이 그 자리에서 붉어진다(낡은 이름이 남아 규칙이
 * 조용히 아무것도 재지 않는 회귀를 막는다).
 */
private fun haveFullNameIn(names: Set<String>): DescribedPredicate<JavaClass> =
    object : DescribedPredicate<JavaClass>("이름이 dry-run 조립 집합(${names.size} 종)에 있다") {
        override fun test(input: JavaClass): Boolean = input.fullName in names
    }

/**
 * `ArchitectureRules.referenceAnyOf` 와 **같은 형태**다 — hit 마다 `satisfied` 만 더한다.
 * `noClasses()` 가 그 결과를 뒤집으므로 hit 가 곧 위반이고, hit 가 없으면 이벤트도 없다.
 */
private fun referenceAnyNamed(forbidden: Set<String>): ArchCondition<JavaClass> =
    object : ArchCondition<JavaClass>("커밋 조립 타입을 참조한다 (${forbidden.size} 종)") {
        override fun check(
            item: JavaClass,
            events: ConditionEvents,
        ) {
            item
                .directDependenciesFromSelf
                .map { it.targetClass }
                .filter { it.fullName in forbidden }
                .distinct()
                .forEach { target ->
                    events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${target.fullName}"))
                }
        }
    }
