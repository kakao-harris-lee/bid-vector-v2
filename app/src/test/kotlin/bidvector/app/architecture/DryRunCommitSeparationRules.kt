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
 * 못한다**.
 *
 * **사각은 「람다·bridge」가 아니었다**(R1-L-1 로 문면 정정). 변이 실측 결과 람다 본문·클래스
 * 리터럴·소거 제네릭 세 모양은 ArchUnit 이 전부 잡았다(제네릭 시그니처 속성까지 읽는다).
 * 실제 사각은 **선택자**였다 — 정확 이름 일치라 `외부$익명` 으로 컴파일되는 중첩 클래스가
 * 대상 밖이었다. 그 자리는 위 선택자의 접두 비교가 닫는다.
 *
 * **중첩 접기는 세 자리에 다 있다**(cr T-4 로 마지막 자리를 채웠다): 선택자(누구를 고르는가)는
 * 접두 비교, 모집단 도출(누가 참조자인가)과 **대상**(무엇을 참조하면 위반인가)은 바깥 이름
 * 접기. 한 자리만 남겨 두면 그 자리의 모양으로 규칙 전체가 열린다.
 *
 * 남는 분담은 그대로다: 이 규칙은 **이름 참조**를 보고, 「불투명 클로저만 받고 커밋 조립은
 * 열거 밖에서 짓는다」처럼 참조가 아예 생기지 않는 모양은 기존 주입 표면 집합 등식
 * (`AppHttpDependencyGateTest`)과 dry-run E2E 의 outbox 전후 등식이 본다.
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
        // R1-L-1 — **중첩·합성 클래스까지 고른다.** 앞 판은 정확 일치라
        // `EvaluationDryRunFactory$forRequest$tee$1`(익명 클래스)이 커밋 타입을 참조하는 변이를
        // 보지 못했다(기존 주입 표면 게이트와 E2E 등식이 잡아 방어는 섰지만, 이 규칙이
        // 주장하는 범위 밖이었다). Kotlin 의 람다·익명 object·내부 클래스는 전부
        // `외부이름$...` 으로 컴파일되므로 그 접두가 선택자의 올바른 경계다.
        override fun test(input: JavaClass): Boolean =
            names.any { name -> input.fullName == name || input.fullName.startsWith("$name\$") }
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
                // cr T-4 — **대상 쪽도 바깥으로 접는다.** 선택자(접두 비교)와 모집단 도출
                // (`substringBefore`)만 중첩을 덮고 여기만 정확 일치로 남아 대칭이 반쪽이었다.
                // 그러면 커밋 조립이 중첩 타입을 갖는 날 dry-run 이 `EvaluationCommitRun$Inner`
                // 만 이름으로 참조하는 모양을 규칙도 등식도 보지 못한다(오늘은 중첩 타입이
                // 없어 도달 불가인 사각이었다).
                .filter { it.fullName.substringBefore('$') in forbidden }
                .distinct()
                .forEach { target ->
                    events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${target.fullName}"))
                }
        }
    }
