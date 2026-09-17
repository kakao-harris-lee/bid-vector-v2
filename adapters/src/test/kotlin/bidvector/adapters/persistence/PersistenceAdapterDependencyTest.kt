package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val DOMAIN_IMPORT = Regex("""^import (bidvector\.[a-zA-Z0-9_.]+)""")
private val OUT_OF_LAYER_DOMAIN_ROOTS =
    setOf(
        "bidvector.qualification",
        "bidvector.strategy",
        "bidvector.decision",
        "bidvector.settlement",
        "bidvector.workflow",
        "bidvector.app",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    OUT_OF_LAYER_DOMAIN_ROOTS.any { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * persistence 패키지가 참조하는 domain 모듈은 procurement·shared-kernel 뿐이다
 * (`bidvector.adapters.koneps.KonepsAdapterDependencyTest`와 같은 관례).
 */
class PersistenceAdapterDependencyTest {
    @Test
    fun `persistence 패키지는 procurement·shared-kernel 밖의 domain 패키지를 참조하지 않는다`() {
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
}
