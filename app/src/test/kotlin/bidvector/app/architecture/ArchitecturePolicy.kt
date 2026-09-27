package bidvector.app.architecture

import java.io.File
import java.util.Properties

/**
 * 경계 규칙의 목록은 `config/quality/architecture-policy.properties` 하나가 갖는다.
 * 같은 목록이 빌드 게이트(1차 강제)와 이 테스트(2차 그물)에 두 벌 있으면 한쪽이 낡는다.
 */
class ArchitecturePolicy private constructor(
    private val values: Map<String, String>,
    private val memberEffects: Map<String, String>,
) {
    val packageRoot: String get() = value("package.root")

    // 아래 넷은 **패키지 세그먼트**다. 정책 파일은 Gradle project 이름으로 적고
    // (`shared-kernel`) 변환 규칙은 하이픈 제거 하나다 — 그 파일의 `package.root` 주석이 정본.
    val domainModules: List<String> get() = packageSegments("layer.domain")
    val applicationModules: List<String> get() = packageSegments("layer.application")
    val adapterModules: List<String> get() = packageSegments("layer.adapters")
    val appModules: List<String> get() = packageSegments("layer.app")
    val shareableDomainModules: List<String> get() = packageSegments("layer.domain.shareable")
    val allowedSubtrees: List<String> get() = list("package.allowed.subtree")
    val allowedExactPackages: List<String> get() = list("package.allowed.exact")
    val byClassPackages: List<String> get() = list("package.allowed.byclass")
    val allowedApiClasses: List<String> get() = list("class.allowed.api")
    val allowedRuntimeClasses: List<String> get() = list("class.allowed.runtime")
    val allowedClasses: List<String> get() = allowedApiClasses + allowedRuntimeClasses
    val forbiddenPackageSegments: List<String> get() = list("package.segment.forbidden")

    /** M6/6A-3+6F-3 D-6A3-9 — `assemble*`(bidvector.strategy.TextKt) 호출 허용 목록. */
    val allowedAssembleCallers: List<String> get() = list("app.allowed.assemble-callers")

    /** D-6A3-17(a) — `NotificationRequestPort` 포트 타입 FQCN. */
    val notificationPortType: String get() = value("app.notification.port-type")

    /** D-6A3-17(a)② — app 이 참조해도 되는 `NotificationRequestPort` 구현 타입 허용 목록. */
    val notificationPortAllowedImpls: List<String> get() = list("app.notification.allowed-impls")

    /** D-6A3-17(a)③ — app 이 참조하면 안 되는 outbox 쓰기 타입 전수(구현 레인이 전수). */
    val outboxForbiddenTypes: List<String> get() = list("app.forbidden.outbox-types")

    /** D-6A3-17(b) — `adapters.ml` 패키지. */
    val mlPackage: String get() = value("app.ml.package")

    /** D-6A3-17(b) — app production 전체가 참조해도 되는 `adapters.ml` 타입 허용 목록. */
    val mlAllowedTypes: List<String> get() = list("app.ml.allowed-types")

    /** D-6A3-25 — 평가·전략 포트 아홉(evaluation 여덟 + StrategyRepository) 호출 판정 대상 집합. */
    val portCallPorts: List<String> get() = list("app.port-call.ports")

    /** D-6A3-25 — (호출자, 포트.메서드) 쌍의 허용 목록 — `"Caller->Port.method"` 형태. */
    val portCallAllowedPairs: List<Pair<String, String>>
        get() = pairs("app.port-call.allowed-pairs")

    /**
     * M6/6A-2b D-6A2b-8 — `app.http` 의존 게이트. 포트 집합은 이 파일이 아니라 use case
     * 생성자에서 **도출**한다 — [appHttpUseCasePorts] 는 그 도출 결과와 대조할 **관측 등식**
     * 이다(포트가 늘거나 줄면 RED 가 되어 분류를 다시 보게 한다).
     */
    val appHttpPackage: String get() = value("app.http.package")
    val appHttpUseCaseType: String get() = value("app.http.use-case-type")
    val appHttpUseCasePorts: List<String> get() = list("app.http.use-case-ports")
    val appHttpAdaptersRoot: String get() = value("app.http.adapters-root")

    /** D-6A2b-19 ② — 허용 목록(패키지 접두 · 정확한 클래스)과 그 안에서 다시 파는 금지 접두. */
    val appHttpAllowedPackages: List<String> get() = list("app.http.allowed-packages")
    val appHttpAllowedClasses: List<String> get() = list("app.http.allowed-classes")
    val appHttpDeniedPackages: List<String> get() = list("app.http.denied-packages")

    /**
     * D-6A2b-26·32 — 면제의 **세 층**. 한 덩어리로 두면 ② 층에 SQL 메서드 하나를 더하는 것만으로
     * 컨트롤러 → HTTP 지름길이 열린다. 목록은 전부 **정확한 클래스 이름**이다(접두·패턴 금지).
     */
    val appAssemblyTier1Classes: List<String> get() = list("app.assembly.tier1-bootstrap-classes")
    val appAssemblyTier2Classes: List<String> get() = list("app.assembly.tier2-request-scoped-classes")
    val appAssemblyTier3Classes: List<String> get() = list("app.assembly.tier3-collection-classes")

    /** 면제 전체 — 대상 집합 등식의 다른 한쪽(세 층의 합). */
    val appAssemblyExemptClasses: List<String>
        get() = appAssemblyTier1Classes + appAssemblyTier2Classes + appAssemblyTier3Classes

    /** D-6A2b-32 ② — 요청 스코프 층의 별도 허용 목록과 그 안에서 다시 파는 접두. */
    val appTier2AllowedPackages: List<String> get() = list("app.assembly.tier2.allowed-packages")
    val appTier2AllowedClasses: List<String>
        get() = list("app.assembly.tier2.allowed-classes") + list("app.assembly.tier2.allowed-app-classes")
    val appTier2DeniedPackages: List<String> get() = list("app.assembly.tier2.denied-packages")

    /** D-6A2b-41 ② — ② 층이 참조해도 되는 `workflow` 타입(정확한 이름). 패키지 통째가 아니다. */
    val appTier2AllowedWorkflowTypes: List<String> get() = list("app.assembly.tier2.allowed-workflow-types")

    /** D-6A2b-41 — 능력 포트 도출의 뿌리가 있는 모듈 루트와, use case 조립이 허용된 (호출자, 타입) 쌍. */
    val appWorkflowRoot: String get() = value("app.workflow-root")
    val appUseCaseConstructionPairs: List<String> get() = list("app.workflow.use-case-construction-pairs")

    /** D-6A2b-49 — 주입 표면(제한·② 층)이 받을 수 있는 정확 타입, 주입점 애너테이션, 도메인 port 뿌리. */
    val appInjectionAllowedTypes: List<String> get() = list("app.injection.allowed-types")
    val appInjectionAnnotations: List<String> get() = list("app.injection.annotations")
    val appDomainPortRoots: List<String> get() = list("app.injection.domain-port-roots")
    val appInjectionCarrierExemptions: List<String> get() = list("app.injection.carrier-exemptions")

    /** N-r5-5 — 제한 층이 허용 접두 안이라도 직접 받지 못하는 타입. */
    val appHttpDeniedTypes: List<String> get() = list("app.http.denied-types")

    /** D-6A2b-34 — ① 층이 의존할 수 없는 HTTP 확장 API 접두와, 오늘 실제로 쓰는 예외 클래스. */
    val appBootstrapForbiddenPackages: List<String> get() = list("app.assembly.tier1.forbidden-http-packages")
    val appBootstrapHttpApiTypes: List<String> get() = list("app.assembly.tier1.http-api-types")

    /** D-6A2b-36·43·44 — 어댑터 멤버 호출의 허용 쌍(`호출자|선언 타입|메서드|서술자`). */
    val appAdapterMemberCallPairs: List<String> get() = list("app.adapter.member-call-pairs")

    /** D-6A2b-37 — 제한·요청 스코프 층이 참조할 수 있는 어댑터 예외 타입(정확한 이름). */
    val appAdapterExceptionTypes: List<String> get() = list("app.adapter.exception-types")

    /** D-6A2b-27 — 출하 조립에서 거둔 HTTP 표면의 기대 집합(API 포트·관리 포트 각각). */
    val apiSurfaceHandlers: List<String> get() = list("app.surface.api.handlers")
    val apiSurfaceFilters: List<String> get() = list("app.surface.api.filters")
    val apiSurfaceServlets: List<String> get() = list("app.surface.api.servlets")

    /** M-r3-4 — API 포트의 method mapping 빈 집합(`빈 이름:타입`) — 진입점 축의 목록이다. */
    val apiSurfaceMethodMappingBeans: List<String> get() = list("app.surface.api.method-mapping-beans")
    val managementSurfaceMethodMappingBeans: List<String>
        get() = list("app.surface.management.method-mapping-beans")
    val managementSurfaceHandlers: List<String> get() = list("app.surface.management.handlers")
    val managementSurfaceFilters: List<String> get() = list("app.surface.management.filters")
    val managementSurfaceServlets: List<String> get() = list("app.surface.management.servlets")

    /** D-6A2b-10 — 운영자 자격증명 타입과 그것을 참조해도 되는 클래스 집합(집합 등식). */
    val operatorCredentialTypes: List<String> get() = list("app.secret.operator-credential-types")
    val operatorCredentialReferencers: List<String> get() = list("app.secret.operator-credential-referencers")

    /** M6/6F-8 (b) — 수집 use case 패키지와 그것이 참조해도 되는 procurement 최상위 타입. */
    val collectionPackage: String get() = value("workflow.collection.package")
    val collectionAllowedProcurementTypes: List<String> get() = list("workflow.collection.allowed-procurement-types")

    /** M6/6F-8 (b) — 멤버 접근이 금지되는 통과 전용 타입, 결과 타입 필드로 금지되는 타입. */
    val collectionPassThroughTypes: List<String> get() = list("workflow.collection.pass-through-types")
    val collectionForbiddenFieldTypes: List<String> get() = list("workflow.collection.forbidden-field-types")

    /** M6/6F-8 (c) — 공고명 키 리터럴을 상수 풀에 가져도 되는 클래스. */
    val titleKeyAllowedClasses: List<String> get() = list("collection.title-key.allowed-classes")

    /**
     * M6/6F-9 D-6F9-1(verifier r1 F-1 뒤 개정) — 대분류 값 획득 축 셋: 대상 타입 · 타입 멤버 접근 쌍 ·
     * 값 획득 쌍(`Caller->Owner#member`) · 클래스 객체 참조자. 멤버 이름 목록은 없다(정책 파일 주석 `(c'')`).
     */
    val divisionValueType: String get() = value("collection.division-value.type")

    val divisionTypeAccessPairs: List<Pair<String, String>>
        get() = pairs("collection.division-value.type-access-pairs")

    val divisionAcquisitionPairs: List<Pair<String, String>>
        get() = pairs("collection.division-value.acquisition-pairs")

    val divisionClassObjectReferencers: List<String>
        get() = list("collection.division-value.class-object-referencers")

    /** M6/6F-9 D-6F9-2 — 업무구분 세부 분류 키 리터럴 게이트: 대상 개념 집합과 그 키를 상수 풀에 가져도 되는 클래스. */
    val classificationKeyConcepts: List<String> get() = list("collection.classification-key.concepts")
    val classificationKeyAllowedClasses: List<String> get() = list("collection.classification-key.allowed-classes")

    /** M6/6F-8 (d)·(e)·(f) — 타입 → 그 타입을 참조해도 되는 app 클래스 집합. */
    val runnerTypes: List<String> get() = list("app.runner.types")
    val runnerAllowedReferencers: List<String> get() = list("app.runner.allowed-referencers")
    val serviceKeyType: String get() = value("app.secret.service-key-type")
    val serviceKeyReaders: List<String> get() = list("app.secret.service-key-readers")

    /** M6/6G D-6G-47 — HTTP 클라이언트 타입과 그것을 쥐어도 되는 클래스 집합(관문과 그 조립). */
    val httpClientType: String get() = value("collection.http-client.type")
    val httpClientRoots: List<String> get() = list("collection.http-client.roots")
    val httpClientHolders: List<String> get() = list("collection.http-client.holders")

    val loggingTypes: List<String> get() = list("app.logging.types")
    val loggingAllowedUsers: List<String> get() = list("app.logging.allowed-users")

    /** M6/6F-8 D-6F8-6 (g) — 원문 값 획득 봉쇄의 모듈 root·접근 타입과 참조자·멤버 접근자 허용 집합. */
    val rawAccessRoots: List<String> get() = list("collection.raw-access.roots")
    val rawAccessTypes: List<String> get() = list("collection.raw-access.types")
    val rawAccessAllowedReferencers: List<String> get() = list("collection.raw-access.allowed-referencers")
    val rawAccessAllowedMemberAccessors: List<String> get() = list("collection.raw-access.allowed-member-accessors")

    /** M6/6F-8 D-6F8-13 (i) — 리플렉션 봉쇄: 금지 패키지·허용 참조자 집합과 `Class` 의 허용 멤버(이름 조회). root 는 (g) 와 같다. */
    val reflectionPackages: List<String> get() = list("collection.reflection.packages")
    val reflectionAllowedReferencers: List<String> get() = list("collection.reflection.allowed-referencers")
    val reflectionClassType: String get() = value("collection.reflection.class-type")
    val reflectionClassAllowedMembers: List<String> get() = list("collection.reflection.class-allowed-members")

    /** M6/6F-8 D-6F8-6 (h) — 수집 use case 타입과 그것을 참조해도 되는 production 클래스 집합. */
    val collectionUseCaseType: String get() = value("collection.usecase.type")
    val collectionUseCaseReferencers: List<String> get() = list("collection.usecase.allowed-referencers")

    /**
     * T-D. **손 열거가 아니라 도출된 후보의 분류**다 — `memberEffectGate` 가 허용 클래스에서
     * 효과 표면에 닿는 멤버를 내고, `member-effects.properties` 가 그 후보를 하나씩
     * `forbidden`/`reviewed` 로 가른다. 여기서는 `forbidden` 만 읽는다.
     */
    val forbiddenMembers: List<String>
        get() = memberEffects.filterValues { it == "forbidden" }.keys.sorted()

    val allModules: List<String>
        get() = domainModules + applicationModules + adapterModules + appModules

    val businessDomainModules: List<String>
        get() = domainModules - shareableDomainModules.toSet()

    private fun value(key: String): String = values[key] ?: error("아키텍처 정책에 '$key' 가 없다")

    private fun packageSegments(key: String): List<String> = list(key).map { it.replace("-", "") }

    private fun list(key: String): List<String> = value(key).split(',').map(String::trim).filter(String::isNotEmpty)

    /** `"왼쪽->오른쪽"` 항목 목록 — 화살표가 없거나 둘 이상이면 정책 파일의 오타다(조용히 넘기지 않는다). */
    private fun pairs(key: String): List<Pair<String, String>> =
        list(key).map { entry ->
            val parts = entry.split("->").map(String::trim)
            check(parts.size == 2) { "$key 항목이 '왼쪽->오른쪽' 형태가 아니다: $entry" }
            parts[0] to parts[1]
        }

    companion object {
        private const val LOCATION_PROPERTY = "bidvector.architecture.policy"
        private const val MEMBER_EFFECTS_PROPERTY = "bidvector.member.effects"

        fun load(): ArchitecturePolicy = ArchitecturePolicy(read(LOCATION_PROPERTY), read(MEMBER_EFFECTS_PROPERTY))

        // UTF-8 Reader 로 읽는다 — `Properties.load(InputStream)` 은 ISO-8859-1 이라
        // 분류 사유(값에 든 한글)가 깨진다.
        private fun read(locationProperty: String): Map<String, String> {
            val location =
                System.getProperty(locationProperty)
                    ?: error("시스템 속성 '$locationProperty' 가 없다 — 빌드가 정책 파일 경로를 넘긴다")
            val properties =
                File(location).reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
            return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
        }
    }
}
