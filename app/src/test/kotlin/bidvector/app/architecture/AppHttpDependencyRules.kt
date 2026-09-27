package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/** 층 배정 — production 은 계약 파일이, 위반 fixture 는 test 가 정한다(M-r3-3). */
data class LayerAssignment(
    val bootstrap: Set<String>,
    val requestScoped: Set<String>,
    val collection: Set<String>,
)

/** D-6A2b-32 — 면제의 세 층과 그 밖(제한 층). 층마다 다른 규칙이 선다. */
enum class AppLayer {
    /** `bidvector.app` 밖 — 이 게이트의 대상이 아니다. */
    OUTSIDE,

    /** 기본값. 허용 목록 ⊆ 가 그대로 걸린다. */
    RESTRICTED,

    /** ① 부팅·배선 — 어댑터 구체 클래스·JDBC 에 의존해도 된다(조립이 일이다). */
    BOOTSTRAP,

    /** ② 요청 스코프 조립과 그 결과 — 컨트롤러가 받는다. 별도 허용 목록 ⊆. */
    REQUEST_SCOPED,

    /** ③ 수집 레인 — 자기 의존은 자유지만 컨트롤러·② 층이 참조하면 위반이다. */
    COLLECTION,
}

/** 시각 같은 **주변 값**만 주는 포트를 가르는 구조 술어가 보는 반환 타입 패키지. */
private const val TIME_PACKAGE = "java.time"

/**
 * M6/6A-2b D-6A2b-8·19 — HTTP 로 닿는 층이 **무엇을 쥘 수 있는가**를 의존 층에서 닫는다.
 *
 * **r1 재건(verifier r1 F-1).** 이전 판은 계약 문면(「의존 집합 ⊆ {…}」)과 달리 **금지 열거
 * 셋**이었고 대상이 **패키지 이름** `app.http` 하나였다. 그래서 (a) 그 패키지 안이라도 열거에
 * 없는 좌표(Boot 자동 구성이 올린 JDBC 클라이언트 등)는 통과했고 (b) **다른 패키지의 진짜
 * `@RestController`** 는 아예 보이지도 않았다 — 변이 셋이 전건 초록이었다(실측).
 *
 * 지금은 두 축을 모두 구조로 바꾼다.
 * - **대상**([targets]): `bidvector.app` **전체**에서 계약 파일이 이름으로 적은 면제 세 층을 뺀
 *   것이다(D-6A2b-26·32). 중첩·동반 객체는 **최상위 소유자**로 판정한다. r1 의 「핸들러 종류 ∪
 *   HTTP 층」 술어는 F-r2-1 이 반증했다 — 그 목록 밖의 진입점 셋이 SQL 을 실행했다.
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

    /**
     * 판정 대상 전수(**최상위 이름**) — 게이트가 무엇을 보고 있는지를 test 가 직접 단언할 수
     * 있게 낸다. 중첩·컴패니언은 소유자로 접힌다(판정 자체가 그렇게 한다).
     */
    fun targets(
        classes: JavaClasses,
        appRoot: String,
        layers: LayerAssignment = productionLayers(),
    ): Set<String> =
        classes
            .filter { isTarget(it, appRoot, layers) }
            .map { it.topLevel().fullName }
            .toSet()

    /** 대상 집합 등식의 다른 한쪽 — `appRoot` 아래 최상위 클래스 전수. */
    fun appTopLevelClasses(
        classes: JavaClasses,
        appRoot: String,
    ): Set<String> =
        classes
            .filter { it.packageName == appRoot || it.packageName.startsWith("$appRoot.") }
            .map { it.topLevel().fullName }
            .toSet()

    fun rules(
        appRoot: String,
        capabilityPorts: Set<String>,
        layers: LayerAssignment = productionLayers(),
    ): List<ArchRule> =
        listOf(
            classes()
                .that(inLayer(appRoot, layers, AppLayer.RESTRICTED))
                .should(onlyDependOnAllowed(capabilityPorts))
                .because("D-6A2b-26 — 제한 층의 의존 집합은 허용 목록의 부분집합이다(열거가 아니라 구성)"),
            classes()
                .that(inLayer(appRoot, layers, AppLayer.REQUEST_SCOPED))
                .should(onlyDependOnTier2Allowed())
                .because(
                    "D-6A2b-32 ② — 컨트롤러가 받는 요청 스코프 층은 면제가 아니라 별도 허용 목록 ⊆ 다. " +
                        "여기에 SQL 이 들어오면 그것이 곧 HTTP 지름길이다",
                ),
            classes()
                .that(inLayer(appRoot, layers, AppLayer.RESTRICTED, AppLayer.REQUEST_SCOPED))
                .should(dependOnCollectionLayer(layers))
                .because("D-6A2b-32 ③ — 수집 레인은 HTTP 로 닿지 않는다(의존 방향으로 잠근다)"),
            classes()
                .that(inLayer(appRoot, layers, AppLayer.RESTRICTED, AppLayer.REQUEST_SCOPED))
                .should(callAdapterMemberOutsideContract())
                .because(
                    "D-6A2b-36·37 — 어댑터 인터페이스 메서드는 허용된 (호출자, 인터페이스, 메서드) 쌍으로만, " +
                        "어댑터 예외는 `Throwable` 이 준 멤버로만 부른다(인터페이스에 메서드를 더해 부르는 형태를 막는다)",
                ),
            classes()
                .that(inLayer(appRoot, layers, AppLayer.BOOTSTRAP))
                .should(dependOnHttpExtensionApi())
                .because(
                    "D-6A2b-34 — 배선 층은 **조립은 하되 HTTP 에 꽂지 못한다**. 확장점의 종류를 세는 대신 " +
                        "그 API 에 대한 의존 자체를 막는다(interceptor·valve·advice·argument resolver 가 " +
                        "전부 같은 패키지에서 온다)",
                ),
        )

    private fun inLayer(
        appRoot: String,
        assignment: LayerAssignment,
        vararg layers: AppLayer,
    ): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("$appRoot 아래 ${layers.joinToString()} 층의 클래스") {
            override fun test(input: JavaClass): Boolean = layerOf(input, appRoot, assignment) in layers
        }

    /**
     * **대상은 `bidvector.app` 전체다**(D-6A2b-26, verifier r2 F-r2-1 시정). r1 은 대상을
     * 「핸들러 애너테이션 ∪ HTTP 층」으로 **열거**했고, 그 목록 밖의 진입점 셋
     * (`RouterFunction` 빈 · 빈 이름 URL 매핑 · 인증보다 앞선 필터)이 SQL 을 실행하며 전건
     * 초록이었다. 종류를 하나 더 세는 처방은 같은 병을 다시 앓는다.
     *
     * **면제는 한 덩어리가 아니라 세 층이다**(D-6A2b-32). 한 덩어리로 두면 ② 층에 SQL 메서드
     * 하나를 더하는 것만으로 컨트롤러 → HTTP 지름길이 열린다(6A-3 R3-M1 ⓑ 「어댑터 추가
     * 메서드」와 같은 형태).
     *
     * 중첩·익명·컴패니언은 **최상위 소유자**로 판정한다. 위반 fixture 루트에는 층이 없다 —
     * 같은 규칙 값이 그대로 서게 평가 루트가 production 일 때만 층을 읽는다.
     */
    private fun layerOf(
        item: JavaClass,
        appRoot: String,
        layers: LayerAssignment,
    ): AppLayer {
        val top = item.topLevel().fullName
        val topPackage = item.topLevel().packageName
        return when {
            !(topPackage == appRoot || topPackage.startsWith("$appRoot.")) -> AppLayer.OUTSIDE
            top in layers.bootstrap -> AppLayer.BOOTSTRAP
            top in layers.requestScoped -> AppLayer.REQUEST_SCOPED
            top in layers.collection -> AppLayer.COLLECTION
            else -> AppLayer.RESTRICTED
        }
    }

    /** 계약 파일이 정한 production 의 층 배정 — fixture 는 자기 배정을 넘긴다(M-r3-3). */
    fun productionLayers(): LayerAssignment =
        LayerAssignment(
            bootstrap = policy.appAssemblyTier1Classes.toSet(),
            requestScoped = policy.appAssemblyTier2Classes.toSet(),
            collection = policy.appAssemblyTier3Classes.toSet(),
        )

    private fun isTarget(
        item: JavaClass,
        appRoot: String,
        layers: LayerAssignment,
    ): Boolean = layerOf(item, appRoot, layers) == AppLayer.RESTRICTED

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
                    // 자기 자신(중첩·컴패니언 포함)은 의존이 아니다 — 최상위 소유자로 가린다.
                    .filterNot { it.topLevel().fullName == item.topLevel().fullName }
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

    /**
     * 허용 판정 — **순서가 곧 규칙**이다. 능력 포트와 금지 접두를 먼저 파고(허용 접두 안에
     * 있어도 예외 없이), 그 다음에 허용 목록을 본다.
     */
    private fun isAllowed(
        target: JavaClass,
        allowedPackages: List<String>,
        allowedClasses: Set<String>,
        deniedPackages: List<String>,
        capabilityPorts: Set<String>,
        adaptersRoot: String,
    ): Boolean =
        when {
            target.fullName in capabilityPorts -> false

            target.packageName.isUnder(deniedPackages) -> false

            // 중첩 타입(sealed 의 하위 등)은 **최상위 이름**으로 판정한다 — 허용 목록에 하위
            // 타입을 하나씩 적으면 그것이 곧 열거로 되돌아가는 길이다.
            target.topLevel().fullName in allowedClasses -> true

            // 오류 매핑표가 옮기는 어댑터 예외 — 값일 뿐 포트를 건네지 않는다(계층 해석).
            target.packageName.isUnder(listOf(adaptersRoot)) -> target.isAssignableTo(Throwable::class.java)

            else -> target.packageName.isUnder(allowedPackages)
        }

    private fun String.isUnder(roots: List<String>): Boolean = roots.any { this == it || startsWith("$it.") }

    /**
     * ② 층의 허용 목록 ⊆ — 패키지 접두와 **정확한 클래스 이름**이다. 그 안에서도 원시 SQL·
     * Spring JDBC 는 다시 파고, `adapters` 는 **인터페이스만** 통과한다(구체 클래스는 이름을
     * 적어야 한다). 이 층은 컨트롤러에서 닿으므로 「무엇을 쥘 수 있는가」가 곧 HTTP 표면이다.
     */
    private fun onlyDependOnTier2Allowed(): ArchCondition<JavaClass> {
        val allowedPackages = policy.appTier2AllowedPackages
        val allowedClasses = policy.appTier2AllowedClasses.toSet()
        val deniedPackages = policy.appTier2DeniedPackages
        val adaptersRoot = policy.appHttpAdaptersRoot
        return object : ArchCondition<JavaClass>(
            "요청 스코프 층의 허용 목록 밖을 참조한다 (패키지 ${allowedPackages.size} · 클래스 ${allowedClasses.size})",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType }
                    .filterNot { it.isPrimitive || it.isArray }
                    .filterNot { it.topLevel().fullName == item.topLevel().fullName }
                    .filterNot { target ->
                        tier2Allows(target, allowedPackages, allowedClasses, deniedPackages, adaptersRoot)
                    }.distinct()
                    .forEach { target ->
                        events.add(SimpleConditionEvent.violated(item, "${item.fullName} -> ${target.fullName}"))
                    }
            }
        }
    }

    private fun tier2Allows(
        target: JavaClass,
        allowedPackages: List<String>,
        allowedClasses: Set<String>,
        deniedPackages: List<String>,
        adaptersRoot: String,
    ): Boolean =
        when {
            target.packageName.isUnder(deniedPackages) -> {
                false
            }

            target.topLevel().fullName in allowedClasses -> {
                true
            }

            // 어댑터는 **인터페이스만** — 구체 클래스를 쥐는 것은 구현을 쥐는 것이고 메서드가 늘 수 있다.
            // 예외 타입 하나는 판다: 값일 뿐 포트를 건네지 않고, 제한 층의 같은 규칙에서 이미
            // 같은 근거로 열려 있다(`isAssignableTo(Throwable)` — 이름이 아니라 계층 해석).
            target.packageName.isUnder(listOf(adaptersRoot)) -> {
                target.isInterface || target.isAssignableTo(Throwable::class.java)
            }

            else -> {
                target.packageName.isUnder(allowedPackages)
            }
        }

    /**
     * 어댑터 **멤버 호출**의 두 축(D-6A2b-36·37, verifier r3 A4·A5).
     *
     * 타입 층의 허용(인터페이스 O / 예외 O)은 「무엇을 쥘 수 있는가」만 말하고 「무엇을 부를 수
     * 있는가」는 말하지 않는다 — 인터페이스에 메서드를 하나 더하면(A5) 그 타입은 여전히
     * 허용된 인터페이스이고, 예외 타입에 메서드를 더하면(A4) 여전히 `Throwable` 하위다.
     * 그래서 멤버 층에서 다시 판다.
     *
     * - 인터페이스: 계약 파일의 **(호출자, 인터페이스, 메서드) 쌍** 목록에 있어야 한다.
     * - 예외: `Throwable`(과 `Object`)이 **선언한** 멤버만. 자기 메서드는 이름을 적을 자리가 없다.
     *
     * 판정은 owner 의 상위 타입까지 훑는다 — 구체 구현을 거쳐 불러도 인터페이스가 그 이름의
     * 메서드를 선언하면 걸린다(6A-3 D-6A3-25 의 `matchedPortCalls` 와 같은 형태).
     */
    private fun callAdapterMemberOutsideContract(): ArchCondition<JavaClass> {
        val interfaceCalls = policy.appAdapterInterfaceCallTriples.toSet()
        val exceptions = policy.appAdapterExceptionTypes.toSet()
        val adaptersRoot = policy.appHttpAdaptersRoot
        return object : ArchCondition<JavaClass>(
            "계약 밖의 어댑터 멤버 호출이 없어야 한다 (허용 쌍 ${interfaceCalls.size} · 예외 타입 ${exceptions.size})",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .filterIsInstance<JavaMethodCall>()
                    .mapNotNull { call -> disallowedAdapterCall(call, item, interfaceCalls, exceptions, adaptersRoot) }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.violated(item, it)) }
            }
        }
    }

    private fun disallowedAdapterCall(
        call: JavaMethodCall,
        item: JavaClass,
        interfaceCalls: Set<String>,
        exceptions: Set<String>,
        adaptersRoot: String,
    ): String? {
        val owner = call.targetOwner
        val candidates =
            (listOf(owner) + owner.allRawSuperclasses + owner.allRawInterfaces)
                .filter { it.packageName.isUnder(listOf(adaptersRoot)) }
                .filter { declarer -> declarer.methods.any { it.name == call.target.name } }
        val caller = item.topLevel().fullName
        return candidates
            .firstNotNullOfOrNull { declarer ->
                when {
                    declarer.fullName in exceptions -> {
                        throwableMemberViolation(caller, declarer, call)
                    }

                    declarer.isInterface -> {
                        "$caller -> ${declarer.fullName}.${call.target.name}"
                            .takeIf { "$caller|${declarer.fullName}|${call.target.name}" !in interfaceCalls }
                    }

                    else -> {
                        null
                    }
                }
            }
    }

    /** 예외 타입의 멤버는 `Throwable`·`Object` 가 선언한 것만 — 자기 메서드는 부를 수 없다. */
    private fun throwableMemberViolation(
        caller: String,
        declarer: JavaClass,
        call: JavaMethodCall,
    ): String? {
        val inherited =
            (declarer.allRawSuperclasses + declarer)
                .filter { it.fullName == "java.lang.Throwable" || it.fullName == "java.lang.Object" }
                .any { base -> base.methods.any { it.name == call.target.name } }
        return if (inherited) null else "$caller -> ${declarer.fullName}.${call.target.name}(예외 자기 멤버)"
    }

    /**
     * ① 층이 HTTP 확장 API 를 참조하면 위반이다(D-6A2b-34, verifier r3 A1·A2·A6).
     *
     * 세 라운드가 **종류를 세다가** 뚫렸다 — handler·Filter·Servlet 을 거두자 interceptor·
     * Tomcat valve·`@ControllerAdvice` 가 그 밖에 있었다. 확장점의 목록에는 끝이 없지만 그것들이
     * **어느 API 에서 오는가**는 유한하다: Spring web·Boot web·Tomcat·서블릿 API. 배선 층이 그
     * API 에 의존하지 못하면 확장점을 만들 재료가 없다.
     *
     * **애너테이션도 의존이다** — `@ControllerAdvice` 를 클래스에 붙이는 것은 타입 참조이므로
     * 함께 본다(ArchUnit 의 직접 의존에 애너테이션이 늘 잡히지는 않아 명시로 더한다).
     *
     * 예외는 **클래스가 아니라 타입 목록**이다 — 「이 클래스는 통째로 봐준다」로 두면 그 클래스가
     * interceptor 를 등록하는 형태(A1)가 그대로 열린다. 오늘 쓰는 타입 하나(필터 등록)만 열고,
     * 같은 클래스라도 다른 확장 API 를 만지면 걸린다. 그 예외가 만드는 등록은 표면 실측
     * (D-6A2b-27)이 이미 잰다.
     */
    private fun dependOnHttpExtensionApi(): ArchCondition<JavaClass> {
        val forbidden = policy.appBootstrapForbiddenPackages
        val allowed = policy.appBootstrapHttpApiTypes.toSet()
        return object : ArchCondition<JavaClass>("HTTP 확장 API(${forbidden.size} 접두)에 대한 의존이 없어야 한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val referenced =
                    item.directDependenciesFromSelf.map { it.targetClass.baseComponentType } +
                        item.annotations.map { it.rawType } +
                        item.methods.flatMap { method -> method.annotations.map { it.rawType } }
                referenced
                    .filter { it.packageName.isUnder(forbidden) }
                    .filterNot { it.fullName in allowed }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${item.fullName} -> ${it.fullName}")) }
            }
        }
    }

    /** ③ 층을 참조하면 그 자체가 위반이다 — 수집 레인은 HTTP 로 닿지 않아야 한다. */
    private fun dependOnCollectionLayer(layers: LayerAssignment): ArchCondition<JavaClass> {
        val collection = layers.collection
        return object : ArchCondition<JavaClass>("수집 레인(${collection.size} 종)에 대한 참조가 없어야 한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map {
                        it.targetClass.baseComponentType
                            .topLevel()
                            .fullName
                    }.filter { it in collection }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${item.fullName} -> $it")) }
            }
        }
    }

    /** 중첩·동반 객체는 자신을 담은 최상위 클래스로 판정한다 — 컨트롤러 안쪽에 숨기는 형태를 함께 든다. */
    private fun JavaClass.topLevel(): JavaClass = enclosingClass.map { it.topLevel() }.orElse(this)
}
