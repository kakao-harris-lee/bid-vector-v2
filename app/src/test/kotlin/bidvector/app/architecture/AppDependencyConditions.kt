package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent

/** 패키지 **경계**로 판정하는 접두 포함 — `boot.web` 이 형제 `boot.webmvc` 를 삼키지 않는다. */
internal fun String.isUnder(roots: List<String>): Boolean = roots.any { this == it || startsWith("$it.") }

/** 메서드 좌표 — 이름만으로는 오버로드가 같은 자리로 접힌다(D-6A2b-44). */
data class MemberSignature(
    val name: String,
    val parameterTypes: List<String>,
) {
    override fun toString(): String = "$name(${parameterTypes.joinToString(",")})"
}

/**
 * 규칙의 **조건 술어**들 — 「어느 층에 어떤 규칙이 서는가」는 [AppHttpDependencyRules] 가
 * 정하고, 「그 규칙이 무엇을 위반으로 보는가」는 여기가 정한다. 두 관심사가 한 파일에 있으면
 * 규칙이 늘 때마다 파일이 함께 길어진다(500 줄 한도).
 */
internal class AppDependencyConditions(
    private val policy: ArchitecturePolicy,
) {
    internal fun onlyDependOnAllowed(capabilityPorts: Set<String>): ArchCondition<JavaClass> {
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

            // 오류 매핑표가 옮기는 어댑터 예외 — 값일 뿐 포트를 건네지 않는다. 판정은 **정확
            // 목록 소속**이다(D-6A2b-42): 계층 해석(`isAssignableTo(Throwable)`)은 목록 밖의
            // 새 예외 타입을 그대로 통과시켰다(verifier r4 F-r4-2).
            target.packageName.isUnder(listOf(adaptersRoot)) -> target.fullName in policy.appAdapterExceptionTypes

            else -> target.packageName.isUnder(allowedPackages)
        }

    internal fun String.isUnder(roots: List<String>): Boolean = roots.any { this == it || startsWith("$it.") }

    /**
     * ② 층의 허용 목록 ⊆ — 패키지 접두와 **정확한 클래스 이름**이다. 그 안에서도 원시 SQL·
     * Spring JDBC 는 다시 파고, `adapters` 는 **인터페이스만** 통과한다(구체 클래스는 이름을
     * 적어야 한다). 이 층은 컨트롤러에서 닿으므로 「무엇을 쥘 수 있는가」가 곧 HTTP 표면이다.
     */
    internal fun onlyDependOnTier2Allowed(): ArchCondition<JavaClass> {
        val allowedPackages = policy.appTier2AllowedPackages
        val allowedClasses = policy.appTier2AllowedClasses.toSet() + policy.appTier2AllowedWorkflowTypes.toSet()
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
            // 예외는 **정확 목록 소속**이어야 한다(D-6A2b-42). `isAssignableTo(Throwable)` 은
            // 목록 밖의 새 예외 타입을 그대로 통과시켰다(verifier r4 F-r4-2: A4 가 초록이었다).
            target.packageName.isUnder(listOf(adaptersRoot)) -> {
                target.isInterface || target.fullName in policy.appAdapterExceptionTypes
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
    internal fun callAdapterMemberOutsideContract(): ArchCondition<JavaClass> {
        val callPairs = policy.appAdapterMemberCallPairs.toSet()
        val exceptions = policy.appAdapterExceptionTypes.toSet()
        val adaptersRoot = policy.appHttpAdaptersRoot
        return object : ArchCondition<JavaClass>(
            "계약 밖의 어댑터 멤버 호출이 없어야 한다 (허용 쌍 ${callPairs.size} · 예외 타입 ${exceptions.size})",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .filterIsInstance<JavaMethodCall>()
                    .mapNotNull { call -> disallowedAdapterCall(call, item, callPairs, exceptions, adaptersRoot) }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.violated(item, it)) }
            }
        }
    }

    private fun disallowedAdapterCall(
        call: JavaMethodCall,
        item: JavaClass,
        callPairs: Set<String>,
        exceptions: Set<String>,
        adaptersRoot: String,
    ): String? {
        val owner = call.targetOwner
        val signature = MemberSignature(call.target.name, call.target.rawParameterTypes.map(JavaClass::getName))
        val candidates =
            (listOf(owner) + owner.allRawSuperclasses + owner.allRawInterfaces)
                .filter { it.packageName.isUnder(listOf(adaptersRoot)) }
                .filter { declarer -> declarer.declares(signature) }
        val caller = item.topLevel().fullName
        return candidates
            .firstNotNullOfOrNull { declarer ->
                if (declarer.fullName in exceptions) {
                    throwableMemberViolation(caller, declarer, signature)
                } else {
                    // **fail-closed**(D-6A2b-43): 인터페이스든 구체 클래스든 등재된 쌍이어야 한다.
                    // 「그 밖은 허용」이던 앞 판은 ② 층의 어댑터 구체 클래스 호출을 통째로 못 봤다
                    // (code-review r4 N-r4-3: 오늘 도는 호출 하나가 이미 계약 밖이었다).
                    "$caller -> ${declarer.fullName}.$signature"
                        .takeIf { callPairKey(caller, declarer, signature) !in callPairs }
                }
            }
    }

    /** 쌍의 좌표는 (호출자, 선언 타입, 메서드 이름, **서술자**) 넷이다 — 오버로드를 따로 센다(D-6A2b-44). */
    private fun callPairKey(
        caller: String,
        declarer: JavaClass,
        signature: MemberSignature,
    ): String = "$caller|${declarer.fullName}|${signature.name}|${signature.parameterTypes.joinToString(",")}"

    /** 예외 타입의 멤버는 `Throwable`·`Object` 가 선언한 것만 — 자기 메서드는 부를 수 없다. */
    private fun throwableMemberViolation(
        caller: String,
        declarer: JavaClass,
        signature: MemberSignature,
    ): String? {
        val inherited =
            (declarer.allRawSuperclasses + declarer)
                .filter { it.fullName == "java.lang.Throwable" || it.fullName == "java.lang.Object" }
                .any { base -> base.declares(signature) }
        return if (inherited) null else "$caller -> ${declarer.fullName}.$signature(예외 자기 멤버)"
    }

    private fun JavaClass.declares(signature: MemberSignature): Boolean =
        methods.any {
            it.name == signature.name &&
                it.rawParameterTypes.map(JavaClass::getName) == signature.parameterTypes
        }

    /**
     * ② 층이 쓰기 능력 포트를 **쥐는** 자리 전부(D-6A2b-41 ①) — 필드·생성자·메서드 서명과
     * 자신이 구현한 인터페이스다.
     *
     * 타입 축(참조 허용 목록)과 다른 축이다. 참조는 값 전달에도 생기지만(dry-run use case
     * 생성자의 서술자), **쥐는 것**은 그 능력을 임의 시점에 쓸 수 있다는 뜻이다. verifier r4
     * F-r4-1 이 쓴 것이 정확히 그 자리다 — 읽기 조회기가 쓰기 저장소 빈을 필드로 들고 있었다.
     */
    internal fun holdCapabilityPort(capabilityPorts: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("쓰기 능력 포트(${capabilityPorts.size})를 서명·구현으로 쥐지 않아야 한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val held =
                    item.fields.map { it.rawType } +
                        item.constructors.flatMap { it.rawParameterTypes } +
                        item.methods.flatMap { it.rawParameterTypes } +
                        item.methods.map { it.rawReturnType } +
                        item.allRawInterfaces
                held
                    .map { it.baseComponentType }
                    .filter { it.fullName in capabilityPorts }
                    .distinct()
                    .forEach {
                        events.add(
                            SimpleConditionEvent.violated(item, "${item.fullName} 이 쥔다 -> ${it.fullName}"),
                        )
                    }
            }
        }

    /** 능력 포트를 받는 use case 의 조립은 등재된 (호출자, 타입) 쌍에서만(D-6A2b-41 ③). */
    internal fun callUseCaseConstructorOutsideContract(
        assemblyTypes: Set<String>,
        pairs: Set<String>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>(
            "계약 밖의 use case 조립이 없어야 한다 (조립 지점 ${assemblyTypes.size} · 허용 쌍 ${pairs.size})",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val caller = item.topLevel().fullName
                item.constructorCallsFromSelf
                    .map { it.targetOwner }
                    .filter { it.fullName in assemblyTypes }
                    .filterNot { "$caller|${it.fullName}" in pairs }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.violated(item, "$caller 가 조립한다 -> ${it.fullName}")) }
            }
        }

    /**
     * ① 층이 HTTP 확장 API 를 참조하면 위반이다(D-6A2b-34, verifier r3 A1·A2·A6).
     *
     * 세 라운드가 **종류를 세다가** 뚫렸다 — handler·Filter·Servlet 을 거두자 interceptor·
     * Tomcat valve·`@ControllerAdvice` 가 그 밖에 있었다. 확장점의 목록에는 끝이 없지만 그것들이
     * **어느 API 에서 오는가**는 유한하다: Spring web·Boot web·Tomcat·서블릿 API. 배선 층이 그
     * API 에 의존하지 못하면 확장점을 만들 재료가 없다.
     *
     * **애너테이션도 의존이다** — 클래스·필드·생성자·메서드에 붙은 애너테이션 타입을 함께 본다
     * (ArchUnit 의 직접 의존에 애너테이션이 늘 잡히지는 않아 명시로 더한다). 매개변수
     * 애너테이션은 밖이다 — 그 자리에 HTTP 확장점을 만드는 애너테이션이 오늘 없다.
     *
     * **ArchUnit 이 「의존」이라 부르는 것은 상수 풀보다 좁다**(OQ-1, verifier r4 실측).
     * `checkcast`·`anewarray` 만으로 등장하는 타입은 `directDependenciesFromSelf` 에 들어오지
     * 않는다 — 오늘 `BidVectorApplication` 의 `jakarta.servlet.Filter`·
     * `org.springframework.boot.web.servlet.ServletRegistrationBean` 이 그 자리이고 게이트는
     * 초록이다(허용 타입 목록에 그 둘이 없는데도). 예외 타입 하나를 여는 일의 실제 폭이
     * 그만큼 다르다는 뜻이고, 그 좌표들이 만드는 등록은 표면 실측(D-6A2b-27)이 잰다.
     *
     * 예외는 **클래스가 아니라 타입 목록**이다 — 「이 클래스는 통째로 봐준다」로 두면 그 클래스가
     * interceptor 를 등록하는 형태(A1)가 그대로 열린다. 오늘 쓰는 타입 하나(필터 등록)만 열고,
     * 같은 클래스라도 다른 확장 API 를 만지면 걸린다. 그 예외가 만드는 등록은 표면 실측
     * (D-6A2b-27)이 이미 잰다.
     */
    internal fun dependOnHttpExtensionApi(): ArchCondition<JavaClass> {
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
                        item.fields.flatMap { field -> field.annotations.map { it.rawType } } +
                        item.constructors.flatMap { ctor -> ctor.annotations.map { it.rawType } } +
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
    internal fun dependOnCollectionLayer(layers: LayerAssignment): ArchCondition<JavaClass> {
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
