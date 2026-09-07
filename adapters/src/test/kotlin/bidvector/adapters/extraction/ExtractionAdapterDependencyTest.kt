package bidvector.adapters.extraction

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val DOMAIN_IMPORT = Regex("""^import (bidvector\.[a-zA-Z0-9_.]+)""")
private val OUT_OF_LAYER_DOMAIN_ROOTS =
    setOf(
        "bidvector.decision",
        "bidvector.settlement",
        "bidvector.workflow",
        "bidvector.app",
        "bidvector.adapters.koneps",
        "bidvector.adapters.persistence",
        "bidvector.adapters.contract",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    OUT_OF_LAYER_DOMAIN_ROOTS.any { importedPackage.startsWith(it) }

/**
 * extraction 패키지가 참조하는 domain 모듈은 procurement·qualification·strategy·
 * shared-kernel 뿐이다(3B `KonepsAdapterDependencyTest`와 같은 관례, D-4). 형제 adapters
 * 패키지(koneps·persistence·contract)도 직접 참조하지 않는다.
 */
class ExtractionAdapterDependencyTest {
    @Test
    fun `extraction 패키지는 허용된 domain 모듈 밖을 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/extraction")
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
