package bidvector.app.architecture

import java.io.File
import java.util.Properties

/**
 * 경계 규칙의 목록은 `config/quality/architecture-policy.properties` 하나가 갖는다.
 * 같은 목록이 빌드 게이트(1차 강제)와 이 테스트(2차 그물)에 두 벌 있으면 한쪽이 낡는다.
 */
class ArchitecturePolicy private constructor(
    private val values: Map<String, String>,
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
    val forbiddenClasses: List<String> get() = list("class.forbidden")
    val forbiddenPackageSegments: List<String> get() = list("package.segment.forbidden")

    val allModules: List<String>
        get() = domainModules + applicationModules + adapterModules + appModules

    val businessDomainModules: List<String>
        get() = domainModules - shareableDomainModules.toSet()

    private fun value(key: String): String = values[key] ?: error("아키텍처 정책에 '$key' 가 없다")

    private fun packageSegments(key: String): List<String> = list(key).map { it.replace("-", "") }

    private fun list(key: String): List<String> = value(key).split(',').map(String::trim).filter(String::isNotEmpty)

    companion object {
        private const val LOCATION_PROPERTY = "bidvector.architecture.policy"

        fun load(): ArchitecturePolicy {
            val location =
                System.getProperty(LOCATION_PROPERTY)
                    ?: error("시스템 속성 '$LOCATION_PROPERTY' 가 없다 — 빌드가 정책 파일 경로를 넘긴다")
            val properties =
                File(location).inputStream().use { stream -> Properties().apply { load(stream) } }
            return ArchitecturePolicy(
                properties.entries.associate { (key, value) -> key.toString() to value.toString() },
            )
        }
    }
}
