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

    /** D-6A3-17(c) — `app.http` 가 우회하면 안 되는 workflow port 타입 전수(평가 port 여덟 + StrategyRepository). */
    val httpForbiddenPorts: List<String> get() = list("app.http.forbidden-workflow-ports")

    /** D-6A3-17(c) — (참조자, port) 쌍의 명시 예외 — `"From->To"` 형태. */
    val httpAllowedPortReferences: List<Pair<String, String>>
        get() =
            list("app.http.allowed-port-references").map { entry ->
                val parts = entry.split("->").map(String::trim)
                check(parts.size == 2) { "app.http.allowed-port-references 항목이 'From->To' 형태가 아니다: $entry" }
                parts[0] to parts[1]
            }

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
