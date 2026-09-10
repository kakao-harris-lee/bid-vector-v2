package bidvector.adapters.event

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val DOMAIN_IMPORT = Regex("""^import (bidvector\.[a-zA-Z0-9_.]+)""")

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(설계 검토 (4)-①). `event` 패키지는
 * [bidvector.workflow.event]의 port·봉투([bidvector.workflow.event.EventEnvelope] 등)와
 * [bidvector.workflow.strategy.Actor](봉투가 나르는 actor)·[bidvector.strategy.StrategyEvent]
 * (payload)·`shared-kernel`(정책 값)을 봐야 한다. `bidvector.adapters.persistence`는 domain이
 * 아니라 **같은 모듈의 다른 adapter 패키지**다 — 트랜잭션 경계([bidvector.adapters
 * .persistence.ConnectionSource])가 거기 산다(설계 검토 「트랜잭션 경계 클래스는 도메인
 * import가 없으므로 persistence에 둬도 되고, event에서 쓴다」). `PersistenceAdapterDependencyTest`
 * 의 3D allow-list(procurement·shared-kernel)는 넓히지 않는다 — 이 test는 그 test와 별개다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.event",
        "bidvector.workflow.strategy",
        "bidvector.strategy",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

class EventAdapterDependencyTest {
    @Test
    fun `event 패키지는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/event")
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
        isDisallowed("bidvector.workflow.notification") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.event") shouldBe false
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
    }
}
