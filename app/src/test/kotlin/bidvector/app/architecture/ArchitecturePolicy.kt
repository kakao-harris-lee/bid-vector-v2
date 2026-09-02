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
