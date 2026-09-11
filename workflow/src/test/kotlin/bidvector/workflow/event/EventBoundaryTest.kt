package bidvector.workflow.event

import bidvector.workflow.strategy.disallowedQualifiedReferences
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S-3b(scope.md, 4A `EditSessionImportBoundaryTest` 관례 계승) — `event` 패키지도
 * `workflow`(application 층)라 `moduleDependencyGate`·도메인 소스 참조 게이트가 걸리지
 * 않는다. allow-list 술어는 `disallowedQualifiedReferences`(4A `strategy` 패키지, 같은
 * 파일 안 `internal`)를 그대로 재사용한다 — 두 패키지가 같은 모듈이라 술어를 복제하지
 * 않는다(§5 중복 금지).
 */
class EventBoundaryTest {
    @Test
    fun `event 패키지는 kotlin·kotlinx·java·shared-kernel·strategy·workflow 밖의 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/workflow/event")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines() }
                .flatMap(::disallowedQualifiedReferences)
                .toList()

        violations.shouldBeEmpty()
    }

    /** 양성 대조(4A 관례 계승) — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `Telegram 패키지를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import org.telegram.telegrambots.meta.api.objects.Update"

        disallowedQualifiedReferences(plantedImport).shouldNotBeEmpty()
    }

    @Test
    fun `허용 루트를 쓰는 정상 소스 줄은 이 술어에 걸리지 않는다`() {
        val allowedLines =
            listOf(
                "import bidvector.strategy.StrategyEvent",
                "import bidvector.sharedkernel.PolicyVersion",
                "import bidvector.workflow.strategy.Actor",
                "import bidvector.workflow.event.EventId",
                "import java.time.Instant",
            )

        allowedLines.flatMap(::disallowedQualifiedReferences).shouldBeEmpty()
    }
}
