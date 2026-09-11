package bidvector.adapters.ml

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val DOMAIN_IMPORT = Regex("""^import (bidvector\.[a-zA-Z0-9_.]+)""")
private val ALLOWED_DOMAIN_ROOTS =
    setOf(
        "bidvector.procurement",
        "bidvector.decision",
        "bidvector.sharedkernel",
        "bidvector.workflow",
    )
private val RESILIENCE4J_RETRY_IMPORT = Regex("""^import io\.github\.resilience4j\.retry""")

private fun isDisallowedDomainImport(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") && ALLOWED_DOMAIN_ROOTS.none { importedPackage.startsWith(it) }

/**
 * scope.md 위협 모델 (j), 설계 검토 「닫히지 않는 자리」 — `adapters`는 domain 층이 아니라
 * `domainSourceReferenceGate`가 걸리지 않는다. 이 test 가 `ml` 패키지의 domain import
 * allow-list(3C `ExtractionAdapterDependencyTest` 관례)와 resilience4j `retry` 패키지
 * 부재(우회 (8), D-4D-3 「재시도 계층 하나」의 실행 증거)를 함께 잰다.
 */
class MlAdapterDependencyTest {
    @Test
    fun `ml 패키지는 procurement decision shared-kernel workflow 밖의 domain 모듈을 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/ml")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines().mapNotNull(DOMAIN_IMPORT::find) }
                .map { match -> match.groupValues[1] }
                .filter(::isDisallowedDomainImport)
                .toList()

        violations shouldBe emptyList()
    }

    @Test
    fun `ml 패키지는 resilience4j retry 패키지를 import 하지 않는다(재시도 계층 하나, 우회 8)`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/ml")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines() }
                .filter { line -> RESILIENCE4J_RETRY_IMPORT.containsMatchIn(line) }
                .toList()

        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowedDomainImport("bidvector.strategy") shouldBe true
        isDisallowedDomainImport("bidvector.qualification") shouldBe true
        isDisallowedDomainImport("bidvector.settlement") shouldBe true
        isDisallowedDomainImport("bidvector.procurement") shouldBe false
    }

    @Test
    fun `resilience4j retry import 를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        RESILIENCE4J_RETRY_IMPORT.containsMatchIn("import io.github.resilience4j.retry.Retry") shouldBe true
        val allowedImport = "import io.github.resilience4j.circuitbreaker.CircuitBreaker"
        RESILIENCE4J_RETRY_IMPORT.containsMatchIn(allowedImport) shouldBe false
    }
}
