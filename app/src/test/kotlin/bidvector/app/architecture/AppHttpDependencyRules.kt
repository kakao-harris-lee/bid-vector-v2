package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.ArchRule
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

/**
 * 규칙의 이름표(D-6A2b-45) — 규칙마다 **영구 음성 fixture** 를 하나씩 붙이고 「그 규칙이 그
 * fixture 를 보고한다」를 규칙별로 단언하려면, 규칙을 목록 위치가 아니라 이름으로 골라야 한다.
 * 규칙 하나를 공집합으로 바꾸면 그 이름의 단언이 RED 다.
 */
enum class AppRuleId {
    RESTRICTED_ALLOWLIST,
    INJECTION_SURFACE,
    TIER2_ALLOWLIST,
    COLLECTION_REFERENCE,
    ADAPTER_MEMBER_CALL,
    TIER2_CAPABILITY,
    USE_CASE_CONSTRUCTION,
    TIER1_HTTP_API,
}

/** 시각 같은 **주변 값**만 주는 포트를 가르는 구조 술어가 보는 반환 타입 패키지. */
private const val TIME_PACKAGE = "java.time"

/**
 * HTTP 로 닿는 층이 **무엇을 쥐고 무엇을 부를 수 있는가**를 의존 층에서 닫는다.
 *
 * 규칙은 **여덟**이고 모양이 서로 다르다([AppRuleId]). 「전부 허용 목록 ⊆」가 아니다.
 *
 * - [AppRuleId.INJECTION_SURFACE] — HTTP 로 닿는 층이 **무엇을 받을 수 있는가**의 정확 목록이다.
 *   참조 축과 다른 축이다 — 허용 접두 안의 일반 타입(`() -> Int`)이 능력을 나른다.
 * - [AppRuleId.RESTRICTED_ALLOWLIST] · [AppRuleId.TIER2_ALLOWLIST] — **허용 목록 ⊆**(구성).
 *   허용은 패키지 접두와 정확한 타입 이름이고, 두 층 모두 **쓰기 능력 포트**(use case 생성자에서
 *   도출)를 먼저 판다. 다시 파는 접두는 층마다 다르다 — 제한 층은 `java.sql`·`javax.sql`,
 *   ② 층은 거기에 `org.springframework.jdbc` 를 더한다. 제한 층은 읽기 port 도 직접 받지
 *   못한다(`app.http.denied-types`). 새 좌표는 기본이 거부다.
 * - [AppRuleId.COLLECTION_REFERENCE] — 수집 레인에 대한 **참조 자체**가 위반이다(방향).
 * - [AppRuleId.ADAPTER_MEMBER_CALL] — 어댑터 **멤버 호출**은 등재된 (호출자, 선언 타입,
 *   메서드, 서술자) 쌍만이다(**fail-closed**). 어댑터 예외는 정확 목록 소속이어야 하고
 *   `Throwable` 이 준 멤버만 부를 수 있다 — 여기는 **정확 열거**다.
 * - [AppRuleId.TIER2_CAPABILITY] — ② 층은 쓰기 능력 포트를 서명·구현으로 **쥐지 못한다**.
 * - [AppRuleId.USE_CASE_CONSTRUCTION] — 능력 포트를 받는 use case 의 **조립**은 등재된
 *   (호출자, 타입) 쌍에서만.
 * - [AppRuleId.TIER1_HTTP_API] — ① 층은 HTTP 확장 API 접두에 **의존하지 못한다**. 예외는
 *   정확한 타입 목록이다 — 여기도 **금지 접두 + 정확 열거**이지 허용 목록 ⊆ 가 아니다.
 *
 * **대상**([targets]): `bidvector.app` **전체**에서 계약 파일이 이름으로 적은 면제 세 층을 뺀
 * 것이다(D-6A2b-26·32). 중첩·동반 객체는 **최상위 소유자**로 판정한다. 「핸들러 종류 ∪
 * HTTP 층」 술어는 그 목록 밖의 진입점이 SQL 을 실행할 수 있어 반증된다.
 *
 * **경계 밖**(D-6A2b-19 ④): 트랜잭션 경계와 편집 트랜잭션은 app 컨텍스트 빈이라 조립 층에서
 * 주입받을 수 있다. 그 자리의 SQL 은 빌드 스크립트를 고치는 저자와 같은 층이고, **HTTP 로
 * 닿는 길**은 위 규칙들이 막는다.
 */
class AppHttpDependencyRules(
    private val policy: ArchitecturePolicy,
) {
    private val conditions = AppDependencyConditions(policy)

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
                        method.rawParameterTypes.isEmpty() &&
                            method.rawReturnType.packageName.isUnder(listOf(TIME_PACKAGE))
                    }
            }.toSet()

    fun capabilityPorts(classes: JavaClasses): Set<String> = derivedUseCasePorts(classes) - ambientPorts(classes)

    /**
     * **use case 조립 지점**(D-6A2b-41 ③) — 생성자로 능력 포트를 받는 `workflow` 타입이다.
     *
     * 「use case 란 무엇인가」를 이름·애너테이션·패키지로 세지 않는다. 능력 포트 자체가 편집
     * use case 생성자에서 도출된 값이므로([capabilityPorts]), 이 분류는 손 목록이 아니라 그
     * 분류를 한 번 더 적용한 것이다. 새 use case 가 생기면 자동으로 이 집합에 들어온다.
     */
    fun useCaseAssemblyTypes(
        classes: JavaClasses,
        capabilityPorts: Set<String>,
    ): Set<String> =
        classes
            .filter { it.packageName.isUnder(listOf(policy.appWorkflowRoot)) }
            .filter { type ->
                type.constructors.any { ctor -> ctor.rawParameterTypes.any { it.name in capabilityPorts } }
            }.map(JavaClass::getName)
            .toSet()

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
            .map { it.outermostClassName() }
            .toSet()

    /** ② 층이 오늘 실제로 참조하는 `workflow` 타입 전수 — 목록 등식의 다른 한쪽. */
    fun observedTier2WorkflowTypes(
        classes: JavaClasses,
        appRoot: String,
        layers: LayerAssignment = productionLayers(),
    ): Set<String> =
        classes
            .filter { layerOf(it, appRoot, layers) == AppLayer.REQUEST_SCOPED }
            .flatMap { item ->
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType.outermostClassName() }
                    .filter { it.substringBeforeLast('.').isUnder(listOf(policy.appWorkflowRoot)) }
            }.toSet()

    /** 주입 표면 규칙의 대상 — 제한 층과 ② 층이다(D-6A2b-49). */
    fun injectionTargets(
        classes: JavaClasses,
        appRoot: String,
        layers: LayerAssignment = productionLayers(),
    ): List<JavaClass> =
        classes.filter { layerOf(it, appRoot, layers) in setOf(AppLayer.RESTRICTED, AppLayer.REQUEST_SCOPED) }

    /** 한 클래스가 **받을 수 있는** 타입 전개 — 조건 술어가 쓰는 것과 같은 도출이다. */
    fun injectionTypes(item: JavaClass): Set<JavaClass> = conditions.injectionTypes(item)

    /** 범용 능력 운반 타입인가 — 목록 검증이 쓰는 것과 같은 술어다. */
    fun isCapabilityCarrier(type: JavaClass): Boolean = conditions.isCapabilityCarrier(type)

    /** 대상 집합 등식의 다른 한쪽 — `appRoot` 아래 최상위 클래스 전수. */
    fun appTopLevelClasses(
        classes: JavaClasses,
        appRoot: String,
    ): Set<String> =
        classes
            .filter { it.packageName == appRoot || it.packageName.startsWith("$appRoot.") }
            .map { it.outermostClassName() }
            .toSet()

    /**
     * 규칙 여덟을 **세 축**으로 묶는다 — 참조 축(무엇을 쥘 수 있는가) · 능력 축(무엇을 부르고
     * 쥐고 조립할 수 있는가) · 배선 축(① 층이 HTTP 에 꽂을 수 있는가). 축마다 목표가 다르고,
     * 한 축의 규칙이 늘어도 다른 축을 읽지 않아도 된다.
     */
    fun rules(
        appRoot: String,
        capabilityPorts: Set<String>,
        useCaseAssemblyTypes: Set<String>,
        layers: LayerAssignment = productionLayers(),
        useCaseConstructionPairs: Set<String> = policy.appUseCaseConstructionPairs.toSet(),
    ): Map<AppRuleId, ArchRule> =
        referenceAxis(appRoot, capabilityPorts, layers) +
            capabilityAxis(appRoot, useCaseAssemblyTypes, capabilityPorts, layers, useCaseConstructionPairs) +
            bootstrapAxis(appRoot, layers)

    /** 참조 축 — 각 층이 **무엇을 이름으로 알 수 있는가**. */
    private fun referenceAxis(
        appRoot: String,
        capabilityPorts: Set<String>,
        layers: LayerAssignment,
    ): Map<AppRuleId, ArchRule> =
        mapOf(
            AppRuleId.INJECTION_SURFACE to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.RESTRICTED, AppLayer.REQUEST_SCOPED))
                    .should(conditions.injectOutsideContract(policy.appInjectionAllowedTypes.toSet()))
                    .because(
                        "D-6A2b-49 — HTTP 로 닿는 층이 **무엇을 받을 수 있는가**는 정확 목록이다. " +
                            "참조 축이 아무리 좁아도 능력은 주입된 값으로 온다",
                    ),
            AppRuleId.RESTRICTED_ALLOWLIST to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.RESTRICTED))
                    .should(conditions.onlyDependOnAllowed(capabilityPorts))
                    .because("D-6A2b-26 — 제한 층의 의존 집합은 허용 목록의 부분집합이다(열거가 아니라 구성)"),
            AppRuleId.TIER2_ALLOWLIST to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.REQUEST_SCOPED))
                    .should(conditions.onlyDependOnTier2Allowed(capabilityPorts))
                    .because(
                        "D-6A2b-32 ② — 컨트롤러가 받는 요청 스코프 층은 면제가 아니라 별도 허용 목록 ⊆ 다. " +
                            "여기에 SQL 이 들어오면 그것이 곧 HTTP 지름길이다",
                    ),
            AppRuleId.COLLECTION_REFERENCE to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.RESTRICTED, AppLayer.REQUEST_SCOPED))
                    .should(conditions.dependOnCollectionLayer(layers))
                    .because("D-6A2b-32 ③ — 수집 레인은 HTTP 로 닿지 않는다(의존 방향으로 잠근다)"),
        )

    /** 능력 축 — **무엇을 부르고 쥐고 조립할 수 있는가**. 참조만으로는 잡히지 않는 자리다. */
    private fun capabilityAxis(
        appRoot: String,
        useCaseAssemblyTypes: Set<String>,
        capabilityPorts: Set<String>,
        layers: LayerAssignment,
        useCaseConstructionPairs: Set<String>,
    ): Map<AppRuleId, ArchRule> =
        mapOf(
            AppRuleId.ADAPTER_MEMBER_CALL to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.RESTRICTED, AppLayer.REQUEST_SCOPED))
                    .should(conditions.callAdapterMemberOutsideContract())
                    .because(
                        "D-6A2b-36·43·44 — 어댑터 멤버 호출은 등재된 (호출자, 선언 타입, 메서드, 서술자) 쌍만이다" +
                            "(인터페이스든 구체 클래스든, 오버로드도 따로 센다). D-6A2b-37·42 — 어댑터 예외는 " +
                            "정확 목록 소속이어야 하고 `Throwable` 이 준 멤버만 부를 수 있다",
                    ),
            AppRuleId.TIER2_CAPABILITY to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.REQUEST_SCOPED))
                    .should(conditions.holdCapabilityPort(capabilityPorts))
                    .because(
                        "D-6A2b-41 ① — 요청 스코프 층은 쓰기 능력 포트를 **쥐지 못한다**. 능력은 호출 지점이 " +
                            "아니라 **전달**에서 샌다: 쓰기 포트 하나를 쥐면 나머지는 메모리 구현으로 채워 " +
                            "use case 를 스스로 조립할 수 있다",
                    ),
            AppRuleId.USE_CASE_CONSTRUCTION to
                classes()
                    .that(
                        inLayer(
                            appRoot,
                            layers,
                            AppLayer.RESTRICTED,
                            AppLayer.REQUEST_SCOPED,
                            AppLayer.BOOTSTRAP,
                            AppLayer.COLLECTION,
                        ),
                    ).should(
                        conditions.callUseCaseConstructorOutsideContract(
                            useCaseAssemblyTypes,
                            useCaseConstructionPairs,
                        ),
                    ).because(
                        "D-6A2b-41 ③ — 능력 포트를 받는 use case 의 조립은 등재된 (호출자, 타입) 쌍에서만 " +
                            "일어난다. 편집 use case 는 등재된 쌍이 **없다** — 그 조립 자리는 어댑터 " +
                            "트랜잭션 경계뿐이고(D-6A2b-3), app 어느 층에서든 부르면 위반이다",
                    ),
        )

    /** 배선 축 — ① 층이 **HTTP 에 꽂을 재료**를 갖지 못하게 한다. */
    private fun bootstrapAxis(
        appRoot: String,
        layers: LayerAssignment,
    ): Map<AppRuleId, ArchRule> =
        mapOf(
            AppRuleId.TIER1_HTTP_API to
                classes()
                    .that(inLayer(appRoot, layers, AppLayer.BOOTSTRAP))
                    .should(conditions.dependOnHttpExtensionApi())
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
     * **대상은 `bidvector.app` 전체다**(D-6A2b-26). 대상을
     * 「핸들러 애너테이션 ∪ HTTP 층」으로 **열거**하면, 그 목록 밖의 진입점
     * (`RouterFunction` 빈 · 빈 이름 URL 매핑 · 인증보다 앞선 필터)이 SQL 을 실행해도 전건
     * 초록일 수 있다. 종류를 하나 더 세는 처방은 같은 병을 다시 앓는다.
     *
     * **면제는 한 덩어리가 아니라 세 층이다**(D-6A2b-32). 한 덩어리로 두면 ② 층에 SQL 메서드
     * 하나를 더하는 것만으로 컨트롤러 → HTTP 지름길이 열린다(R3-M1 ⓑ 「어댑터 추가
     * 메서드」와 같은 형태).
     *
     * 중첩·익명·컴패니언은 **최상위 소유자**로 판정한다. 층 배정은 **평가 루트와 무관한 값**이다
     * — production 은 계약 파일이, 위반 fixture 는 test 가 자기 배정을 넘긴다(M-r3-3·D-6A2b-45).
     * 규칙 **값**은 두 루트에서 같고 배정만 바뀐다.
     */
    private fun layerOf(
        item: JavaClass,
        appRoot: String,
        layers: LayerAssignment,
    ): AppLayer {
        val top = item.outermostClassName()
        val topPackage = top.substringBeforeLast('.')
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
}
