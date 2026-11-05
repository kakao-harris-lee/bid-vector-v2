package bidvector.buildlogic

import java.io.File

/**
 * 소스 층 판정의 **완전 술어**. 축이 셋이다.
 *
 * ⓐ **domain 허용 목록** — `architecture-policy.properties` 의 같은 네 키를
 * (`package.allowed.subtree` · `package.allowed.exact` · `package.allowed.byclass` ·
 * `class.allowed.api`/`runtime`) 세 번째 표현으로 읽는다. 목록은 그 정책 파일 하나가 갖는다
 * (설계 검토 §1 「한 자리 규율」).
 *
 * ⓑ **패키지 범위 축**(`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) — adapters 의 네 게이트 축이 재는
 * 것과 같은 허용 뿌리를 바이트코드가 아니라 **이름**에 건다. `const val` 참조는 컴파일러가 인라인해
 * 상수 풀에서 사라지므로 바이트코드 층이 못 보지만(실측: `object` 참조로 바꾸자 RED), 이름은 소스에
 * 남는다. D-6A1-34 가 그 문면을 「모든 컴파일 시간 상수 참조」로 넓혔다.
 *
 * ⓒ **환경 스니핑 축**(OPS-13, 임계 0) — production 소스가 자기 실행 환경을 물어 거동을 가르지
 * 않는다. 억제는 환경 스니핑이 아니라 명시적 정책 값이어야 한다(OPS-07 (a) 형태).
 *
 * T-D(멤버)는 ⓐ 의 대상이 아니다 — 해석이 필요해 바이트코드 층이 정본이다(설계 검토 §0
 * 「두 층의 분담」).
 */
internal class SourceReferencePolicy private constructor(
    private val subtrees: List<String>,
    private val exactPackages: Set<String>,
    private val byClassPackages: Set<String>,
    private val allowedClasses: Set<String>,
    private val domainModules: Set<String>,
    private val scopedRoots: Map<String, List<String>>,
    private val envSniffPairs: Set<String>,
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

    /** 이 파일의 패키지를 품는 범위 축의 허용 뿌리 — 축 밖이면 `null`(판정 대상이 아니다). */
    fun scopeOf(packageName: String): List<String>? =
        scopedRoots.entries
            .filter { (scope, _) -> packageName == scope || packageName.startsWith("$scope.") }
            .maxByOrNull { (scope, _) -> scope.length }
            ?.value

    /** 범위 축의 판정 대상은 **자기 저장소 좌표**다 — 외부 좌표는 ⓐ 와 의존 층이 든다. */
    fun isSelfNamespace(fqn: String): Boolean = fqn.startsWith("$SELF_NAMESPACE.")

    fun admitsInScope(
        fqn: String,
        roots: List<String>,
    ): Boolean = roots.any { fqn == it || fqn.startsWith("$it.") }

    /** `*` 수신자는 「어떤 수신자든」이다 — Spring `Environment` 처럼 수신자가 지역 이름인 축을 위해. */
    fun isEnvSniff(call: SourceMemberCall): Boolean =
        "${call.receiver}.${call.member}" in envSniffPairs || "*.${call.member}" in envSniffPairs

    private fun isUnderSubtree(packageName: String): Boolean =
        subtrees.any { packageName == it || packageName.startsWith("$it.") }

    companion object {
        private const val SELF_NAMESPACE = "bidvector"
        private const val SCOPED_PACKAGES = "source.scoped-packages"
        private const val SCOPED_ROOTS_PREFIX = "source.scoped-allowed-roots."
        private const val ENV_SNIFF_PAIRS = "source.env-sniff.member-pairs"

        fun load(policyFile: File): SourceReferencePolicy {
            val policy = readPolicy(policyFile)
            return SourceReferencePolicy(
                subtrees = policy.requireList("package.allowed.subtree"),
                exactPackages = policy.requireList("package.allowed.exact").toSet(),
                byClassPackages = policy.requireList("package.allowed.byclass").toSet(),
                allowedClasses =
                    (policy.requireList("class.allowed.api") + policy.requireList("class.allowed.runtime")).toSet(),
                domainModules = policy.requireList("layer.domain").toSet(),
                scopedRoots =
                    policy.requireList(SCOPED_PACKAGES).associateWith { scope ->
                        policy.requireList("$SCOPED_ROOTS_PREFIX$scope")
                    },
                envSniffPairs = policy.requireList(ENV_SNIFF_PAIRS).toSet(),
            )
        }
    }
}
