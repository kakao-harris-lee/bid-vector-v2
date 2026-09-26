package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/** 시각 같은 **주변 값**만 주는 포트를 가르는 구조 술어가 보는 반환 타입 패키지. */
private const val TIME_PACKAGE = "java.time"

/** 평가 루트 아래 HTTP 층의 패키지 세그먼트 — production 에서는 `bidvector.app.http` 다. */
private const val HTTP_LAYER_SEGMENT = "http"

/** 핸들러를 표시하는 Spring 애너테이션 — 메타 애너테이션까지 본다(`@RestController` → `@Controller`). */
private val HANDLER_ANNOTATIONS =
    listOf(
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.ControllerAdvice",
    )

/**
 * M6/6A-2b D-6A2b-8·19 — HTTP 로 닿는 층이 **무엇을 쥘 수 있는가**를 의존 층에서 닫는다.
 *
 * **r1 재건(verifier r1 F-1).** 이전 판은 계약 문면(「의존 집합 ⊆ {…}」)과 달리 **금지 열거
 * 셋**이었고 대상이 **패키지 이름** `app.http` 하나였다. 그래서 (a) 그 패키지 안이라도 열거에
 * 없는 좌표(Boot 자동 구성이 올린 JDBC 클라이언트 등)는 통과했고 (b) **다른 패키지의 진짜
 * `@RestController`** 는 아예 보이지도 않았다 — 변이 셋이 전건 초록이었다(실측).
 *
 * 지금은 두 축을 모두 구조로 바꾼다.
 * - **대상**([targets]): `bidvector.app` 아래에서 `@Controller`·`@ControllerAdvice`(메타 포함)를
 *   단 모든 클래스 ∪ HTTP 층 패키지 전체. 중첩·동반 객체는 **최상위 클래스**로 판정해
 *   컨트롤러 안쪽에 숨기는 형태도 함께 든다.
 * - **술어**: 의존 집합 ⊆ 허용 목록. 허용은 **패키지 접두 + 정확한 클래스 이름**이고, 그
 *   안에서도 ① 저장·발행 능력 포트(use case 생성자에서 도출) ② `java.sql`·`javax.sql` 은
 *   다시 판다. 열거가 아니라 구성이라 새 좌표는 **기본이 거부**다.
 *
 * `Throwable` 예외 하나: 오류 매핑표가 어댑터의 구체 예외 타입을 상태 코드로 옮겨야 하고,
 * 예외 타입은 값일 뿐 포트를 건네지 않는다. 판정은 이름이 아니라
 * `isAssignableTo(Throwable)`(계층 해석)이고 어댑터 루트 안으로 좁힌다.
 *
 * **경계 밖**(D-6A2b-19 ④): 트랜잭션 경계와 편집 트랜잭션은 app 컨텍스트 빈이라 조립 층에서
 * 주입받을 수 있다. 그 자리의 SQL 은 빌드 스크립트를 고치는 저자와 같은 층이고, **HTTP 로
 * 닿는 길**은 위 ①②가 막는다 — 핸들러가 그 둘(또는 그것을 쥔 헬퍼)을 참조하는 순간 허용
 * 목록 밖이라 걸린다.
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

    /** 판정 대상 전수 — 게이트가 **무엇을 보고 있는지**를 test 가 직접 단언할 수 있게 낸다. */
    fun targets(
        classes: JavaClasses,
        appRoot: String,
    ): Set<String> =
        classes
            .filter { isTarget(it, appRoot) }
            .map(JavaClass::getName)
            .toSet()

    fun rules(
        appRoot: String,
        capabilityPorts: Set<String>,
    ): List<ArchRule> =
        listOf(
            classes()
                .that(handlerOrHttpLayer(appRoot))
                .should(onlyDependOnAllowed(capabilityPorts))
                .because("D-6A2b-19 — HTTP 로 닿는 층의 의존 집합은 허용 목록의 부분집합이다(열거가 아니라 구성)"),
        )

    private fun handlerOrHttpLayer(appRoot: String): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("$appRoot 아래의 핸들러이거나 HTTP 층인 클래스") {
            override fun test(input: JavaClass): Boolean = isTarget(input, appRoot)
        }

    private fun isTarget(
        item: JavaClass,
        appRoot: String,
    ): Boolean {
        val top = item.topLevel()
        if (!(top.packageName == appRoot || top.packageName.startsWith("$appRoot."))) return false
        // HTTP 층은 **평가 루트 기준**으로 센다 — 같은 규칙 값을 위반 fixture 루트에 그대로
        // 적용해 음성 대조를 세우기 위해서다(규칙을 fixture 전용으로 새로 만들면 그 단언은
        // production 게이트에 대해 아무것도 말하지 않는다). 계약 파일의
        // `app.http.package` 와 어긋나지 않는지는 게이트 test 가 따로 단언한다.
        val httpPackage = "$appRoot.$HTTP_LAYER_SEGMENT"
        val inHttpLayer = top.packageName == httpPackage || top.packageName.startsWith("$httpPackage.")
        return inHttpLayer || HANDLER_ANNOTATIONS.any(top::isMetaAnnotatedWith)
    }

    private fun onlyDependOnAllowed(capabilityPorts: Set<String>): ArchCondition<JavaClass> {
        val allowedPackages = policy.appHttpAllowedPackages
        val allowedClasses = policy.appHttpAllowedClasses.toSet()
        val deniedPackages = policy.appHttpDeniedPackages
        val adaptersRoot = policy.appHttpAdaptersRoot
        return object : ArchCondition<JavaClass>(
            "허용 목록 밖을 참조하지 않는다 " +
                "(패키지 ${allowedPackages.size} · 클래스 ${allowedClasses.size} · 능력 포트 ${capabilityPorts.size} 제외)",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType }
                    .filterNot { it.isPrimitive || it.isArray }
                    .filterNot { target ->
                        isAllowed(
                            target,
                            allowedPackages,
                            allowedClasses,
                            deniedPackages,
                            capabilityPorts,
                            adaptersRoot,
                        )
                    }.distinct()
                    .forEach { target ->
                        events.add(SimpleConditionEvent.violated(item, "${item.fullName} -> ${target.fullName}"))
                    }
            }
        }
    }

    private fun isAllowed(
        target: JavaClass,
        allowedPackages: List<String>,
        allowedClasses: Set<String>,
        deniedPackages: List<String>,
        capabilityPorts: Set<String>,
        adaptersRoot: String,
    ): Boolean {
        if (target.fullName in capabilityPorts) return false
        if (target.packageName.isUnder(deniedPackages)) return false
        // 중첩 타입(sealed 의 하위 등)은 **최상위 이름**으로 판정한다 — 허용 목록에 하위 타입을
        // 하나씩 적으면 그것이 곧 열거로 되돌아가는 길이다.
        if (target.topLevel().fullName in allowedClasses) return true
        if (target.packageName.isUnder(listOf(adaptersRoot)) &&
            target.isAssignableTo(Throwable::class.java)
        ) {
            return true
        }
        return target.packageName.isUnder(allowedPackages)
    }

    private fun String.isUnder(roots: List<String>): Boolean = roots.any { this == it || startsWith("$it.") }

    /** 중첩·동반 객체는 자신을 담은 최상위 클래스로 판정한다 — 컨트롤러 안쪽에 숨기는 형태를 함께 든다. */
    private fun JavaClass.topLevel(): JavaClass = enclosingClass.map { it.topLevel() }.orElse(this)
}
