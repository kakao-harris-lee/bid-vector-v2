package bidvector.adapters.strategy

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val DOMAIN_IMPORT = Regex("""^import (bidvector\.[a-zA-Z0-9_.]+)""")

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6B1-9, `EventAdapterDependencyTest`
 * 와 같은 형태·같은 이유). `strategy` 패키지는 [bidvector.workflow.strategy]
 * (`EditSessionRepository`·`EditSessionSnapshot` 등, D-6B1-7)·[bidvector.strategy]
 * (`StrategyDraft` 등, 스냅숏 nested 타입이 참조)·`shared-kernel`(값 타입)을 봐야 한다.
 * `bidvector.adapters.persistence`는 domain이 아니라 **같은 모듈의 다른 adapter
 * 패키지**다 — 세션 SQL 문자열([bidvector.adapters.persistence.Sql])이 거기 산다
 * (`PersistenceAdapterDependencyTest`의 3D allow-list(procurement·shared-kernel)는
 * 넓히지 않는다 — 이 test는 그 test와 별개다, D-6B1-9 근거).
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.strategy",
        "bidvector.strategy",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

class StrategyAdapterDependencyTest {
    @Test
    fun `strategy 패키지는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/strategy")
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

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.procurement") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.workflow.event") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
    }
}
