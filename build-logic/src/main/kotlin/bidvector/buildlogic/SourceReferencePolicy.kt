package bidvector.buildlogic

import java.io.File

/**
 * 소스 층 판정의 **완전 술어**. `architecture-policy.properties` 의 같은 네 키를
 * (`package.allowed.subtree` · `package.allowed.exact` · `package.allowed.byclass` ·
 * `class.allowed.api`/`runtime`) 세 번째 표현으로 읽는다 — 목록은 이 정책 파일 하나가 갖는다
 * (설계 검토 §1 「한 자리 규율」). 새 정책 키는 없다.
 *
 * T-D(멤버)는 이 술어의 대상이 아니다 — 해석이 필요해 바이트코드 층이 정본이다(설계 검토 §0
 * 「두 층의 분담」).
 */
internal class SourceReferencePolicy private constructor(
    private val subtrees: List<String>,
    private val exactPackages: Set<String>,
    private val byClassPackages: Set<String>,
    private val allowedClasses: Set<String>,
    private val domainModules: Set<String>,
) {
    /**
     * 정규화된 FQN(패키지는 `.`, 중첩 클래스는 `$`)이 허용 목록 안인가. T-A(하위 허용) ·
     * T-B(정확 패키지) · T-C(클래스 단위)를 그 순서로 본다.
     */
    fun admits(fqn: String): Boolean {
        val packageName = fqn.substringBeforeLast('.', missingDelimiterValue = "")
        return isUnderSubtree(packageName) ||
            packageName in exactPackages ||
            (packageName in byClassPackages && fqn in allowedClasses)
    }

    /**
     * wildcard import(`import x.y.*`)는 T-A·T-B 에서만 허용된다 — T-C 패키지의 wildcard 는
     * 목록 유무와 무관하게 그 자체가 위반이다(설계 검토 §1 「wildcard import 는 T-C 를
     * 되돌린다」).
     */
    fun admitsWildcard(packageName: String): Boolean = isUnderSubtree(packageName) || packageName in exactPackages

    fun isDomain(moduleName: String): Boolean = moduleName in domainModules

    private fun isUnderSubtree(packageName: String): Boolean =
        subtrees.any { packageName == it || packageName.startsWith("$it.") }

    companion object {
        fun load(policyFile: File): SourceReferencePolicy {
            val policy = readPolicy(policyFile)
            return SourceReferencePolicy(
                subtrees = policy.requireList("package.allowed.subtree"),
                exactPackages = policy.requireList("package.allowed.exact").toSet(),
                byClassPackages = policy.requireList("package.allowed.byclass").toSet(),
                allowedClasses =
                    (policy.requireList("class.allowed.api") + policy.requireList("class.allowed.runtime")).toSet(),
                domainModules = policy.requireList("layer.domain").toSet(),
            )
        }
    }
}
