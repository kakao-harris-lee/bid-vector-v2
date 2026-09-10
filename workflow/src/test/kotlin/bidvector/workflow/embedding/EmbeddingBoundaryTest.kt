package bidvector.workflow.embedding

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S-4(scope.md 위협 모델) — `workflow`는 application 층이라 `moduleDependencyGate`가 걸리지
 * 않는다(4A `EditSessionImportBoundaryTest`·4D-1 `PredictionBoundaryTest`와 같은 이유). 이
 * 패키지는 `bidvector.decision`을 **소비하지 않는다**(D-4D2-1, scope.md out_of_scope) —
 * `prediction` 패키지보다 좁은 allow-list다(procurement·decision 제외).
 */
class EmbeddingBoundaryTest {
    @Test
    fun `embedding 패키지는 kotlin·kotlinx·java·shared-kernel·workflow 밖의 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/workflow/embedding")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines() }
                .flatMap(::disallowedEmbeddingReferences)
                .toList()

        violations.shouldBeEmpty()
    }

    /** 양성 대조(4A·4D-1 관례 계승) — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `Telegram 패키지를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import org.telegram.telegrambots.meta.api.objects.Update"

        disallowedEmbeddingReferences(plantedImport).shouldNotBeEmpty()
    }

    @Test
    fun `decision 패키지를 심은 표본은 이 술어에 걸린다 — 양성 대조(D-4D2-1, 소비 금지)`() {
        val plantedImport = "import bidvector.decision.MlUnavailableReason"

        disallowedEmbeddingReferences(plantedImport).shouldNotBeEmpty()
    }

    @Test
    fun `grpc 채널 타입을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import io.grpc.ManagedChannel"

        disallowedEmbeddingReferences(plantedImport).shouldNotBeEmpty()
    }

    @Test
    fun `허용 루트를 쓰는 정상 소스 줄은 이 술어에 걸리지 않는다`() {
        val allowedLines =
            listOf(
                "import bidvector.sharedkernel.Rate",
                "import bidvector.workflow.event.CorrelationId",
                "import bidvector.workflow.prediction.CallBudget",
                "import bidvector.workflow.prediction.ModelReleaseSelector",
                "import kotlinx.coroutines.runBlocking",
                "import java.time.Duration",
                "    val now: java.time.Instant = clock.now()",
            )

        allowedLines.flatMap(::disallowedEmbeddingReferences).shouldBeEmpty()
    }
}

/** 이 패키지가 참조해도 되는 정규화 이름의 루트(scope.md ①, D-4D2-1). */
private val ALLOWED_ROOTS =
    listOf(
        "kotlin",
        "kotlinx",
        "java",
        "javax",
        "bidvector.sharedkernel",
        "bidvector.workflow",
    )

/** `foo.Baz`·`foo.bar.Baz` 형태 — 4D-1 `disallowedPredictionReferences`와 같은 정규식. */
private val QUALIFIED_REFERENCE = Regex("""\b([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*)\.[A-Z][A-Za-z0-9_]*\b""")

private fun isAllowedRoot(qualifiedPackage: String): Boolean =
    ALLOWED_ROOTS.any { qualifiedPackage == it || qualifiedPackage.startsWith("$it.") }

internal fun disallowedEmbeddingReferences(line: String): List<String> =
    QUALIFIED_REFERENCE
        .findAll(line)
        .map { match -> match.groupValues[1] }
        .filterNot(::isAllowedRoot)
        .toList()
