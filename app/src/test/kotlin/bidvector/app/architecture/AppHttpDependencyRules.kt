package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** 시각 같은 **주변 값**만 주는 포트를 가르는 구조 술어가 보는 반환 타입 패키지. */
private const val TIME_PACKAGE = "java.time"

/**
 * M6/6A-2b D-6A2b-8 — `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` 폐쇄. HTTP 층이 **무엇을
 * 쥘 수 있는가**를 의존 층에서 닫는다: 컨트롤러는 조립된 실행기만 받고 저장·발행 포트나
 * 어댑터 구현, 원시 SQL 에 닿지 못한다.
 *
 * 포트 집합은 **손 목록이 아니라 도출**이다 — use case 생성자의 매개변수 타입 가운데
 * 인터페이스인 것이 포트다([derivedUseCasePorts]). 그 가운데 **주변 포트**(무인자 메서드로
 * `java.time` 값만 내는 것 — 오늘은 `Clock` 하나)는 아무 권한도 주지 않으므로 빼고
 * ([ambientPorts]), 나머지가 **능력 포트**다([capabilityPorts]). 분류가 구조 술어라
 * 새 포트는 **기본적으로 능력 쪽**에 떨어진다(닫히는 방향) — 이름 목록에 빠뜨려서
 * 조용히 허용되는 자리가 없다.
 *
 * 어댑터 축에는 **`Throwable` 예외 하나**를 판다: 오류 매핑표(`app.http.ErrorMapping`)가
 * 어댑터가 던지는 구체 예외 타입을 상태 코드로 옮겨야 하고, 예외 타입은 값일 뿐 포트를
 * 건네지 않는다. 판정은 이름이 아니라 `isAssignableTo(Throwable)`(계층 해석)이다.
 */
class AppHttpDependencyRules(
    private val policy: ArchitecturePolicy,
) {
    /** use case 생성자 매개변수 타입 가운데 인터페이스 — 「포트」의 도출 정의다. */
    fun derivedUseCasePorts(classes: JavaClasses): Set<String> =
        classes
            .get(policy.appHttpUseCaseType)
            .constructors
            .flatMap { it.rawParameterTypes }
            .filter(JavaClass::isInterface)
            .map(JavaClass::getName)
            .toSet()

    /** 무인자 메서드로 `java.time` 값만 내는 포트 — 권한이 아니라 주변 값이다. */
    fun ambientPorts(classes: JavaClasses): Set<String> =
        derivedUseCasePorts(classes)
            .filter { name ->
                val port = classes.get(name)
                port.methods.isNotEmpty() &&
                    port.methods.all { method ->
                        method.rawParameterTypes.isEmpty() && method.rawReturnType.packageName.startsWith(TIME_PACKAGE)
                    }
            }.toSet()

    fun capabilityPorts(classes: JavaClasses): Set<String> = derivedUseCasePorts(classes) - ambientPorts(classes)

    fun rules(
        httpPackage: String,
        capabilityPorts: Set<String>,
        adaptersRoot: String,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("$httpPackage..")
                .should(dependOnAnyOf(capabilityPorts, "능력 포트"))
                .because("D-6A2b-8 — 컨트롤러는 조립된 실행기만 받는다(저장·발행 포트를 쥐지 않는다)"),
            noClasses()
                .that()
                .resideInAPackage("$httpPackage..")
                .should(dependOnAdapterTypeThatIsNotThrowable(adaptersRoot))
                .because("D-6A2b-8 — HTTP 층은 어댑터 구현을 보지 않는다(예외 타입만 예외)"),
            noClasses()
                .that()
                .resideInAPackage("$httpPackage..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("java.sql..", "javax.sql..")
                .because("D-6A2b-8 — HTTP 층에 원시 SQL·DataSource 가 없다(6A-3 r3 지름길 셋째)"),
        )

    private fun dependOnAnyOf(
        forbidden: Set<String>,
        label: String,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$label 을 참조한다 (${forbidden.size} 종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType }
                    .filter { it.fullName in forbidden }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${it.fullName}")) }
            }
        }

    private fun dependOnAdapterTypeThatIsNotThrowable(adaptersRoot: String): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$adaptersRoot 의 비-Throwable 타입을 참조한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType }
                    .filter { it.packageName == adaptersRoot || it.packageName.startsWith("$adaptersRoot.") }
                    .filterNot { it.isAssignableTo(Throwable::class.java) }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${it.fullName}")) }
            }
        }
}
