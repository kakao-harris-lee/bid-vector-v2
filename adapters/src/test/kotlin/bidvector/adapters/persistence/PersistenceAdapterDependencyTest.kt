package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val DOMAIN_IMPORT = Regex("""^import (bidvector\.[a-zA-Z0-9_.]+)""")

/**
 * M6/6F-1 — `strategy`·`workflow`를 열었다(`bidvector.adapters.ml.MlAdapterDependencyTest`와
 * 같은 관례 — 그 test도 `ml` 패키지의 `StrategyRepository` fake 사용 때문에 `workflow`를
 * 연다). [JdbcStrategyRepository]가 [bidvector.strategy.validate]를 통과시키고
 * `bidvector.workflow.strategy.StrategyRepository`를 구현하는 것이 D-6F1-2의 설계 자체다 —
 * 어댑터가 도메인 타입을 직접 만들지 않고 기존 공개 검증 문을 그대로 쓴다.
 */
private val OUT_OF_LAYER_DOMAIN_ROOTS =
    setOf(
        "bidvector.qualification",
        "bidvector.decision",
        "bidvector.settlement",
        "bidvector.app",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    OUT_OF_LAYER_DOMAIN_ROOTS.any { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * persistence 패키지가 참조하는 domain 모듈은 procurement·shared-kernel·strategy·workflow
 * 뿐이다(`bidvector.adapters.koneps.KonepsAdapterDependencyTest`와 같은 관례, M6/6F-1이
 * strategy·workflow를 추가로 열었다 — 위 KDoc).
 */
class PersistenceAdapterDependencyTest {
    @Test
    fun `persistence 패키지는 procurement·shared-kernel·strategy·workflow 밖의 domain 패키지를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/persistence")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines().mapNotNull(DOMAIN_IMPORT::find) }
                .map { match -> match.groupValues[1] }
                .filter(::isDisallowed)
                .toList()

        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(`MlAdapterDependencyTest`와 같은 관례). */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.settlement") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.workflow") shouldBe false
        isDisallowed("bidvector.procurement") shouldBe false
    }
}
