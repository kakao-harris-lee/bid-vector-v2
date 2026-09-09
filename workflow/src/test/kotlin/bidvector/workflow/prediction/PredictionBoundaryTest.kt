package bidvector.workflow.prediction

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S-4(scope.md 위협 모델 (j)) — `workflow`는 application 층이라 `moduleDependencyGate`·
 * `domainSourceReferenceGate`가 걸리지 않는다(설계 검토 (1), 4A `EditSessionImportBoundaryTest`
 * 와 같은 이유). 이 패키지는 `procurement`·`decision`을 도메인 값으로 쓰므로(scope.md ①)
 * 4A 의 고정 allow-list(`bidvector.sharedkernel`·`bidvector.strategy`·`bidvector.workflow`)를
 * 그대로 재사용할 수 없다 — allow-list 가 다르면 술어도 이 패키지 것을 따로 둔다(그
 * 함수의 allow-list 가 파일 안 `private val`로 고정돼 있어 매개변수화가 안 된다, `strategy`
 * 패키지 전체는 out_of_scope 라 원본을 고칠 수도 없다). 정규식·판별 로직은 4A 와 같은
 * 형태를 그대로 쓴다 — 술어의 **구조**를 재사용하고 **allow-list**만 이 패키지 것으로 연다.
 */
class PredictionBoundaryTest {
    @Test
    fun `prediction 패키지는 kotlin·kotlinx·java·procurement·decision·shared-kernel·workflow 밖의 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/workflow/prediction")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines() }
                .flatMap(::disallowedPredictionReferences)
                .toList()

        violations.shouldBeEmpty()
    }

    /** 양성 대조(4A 관례 계승) — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `Telegram 패키지를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import org.telegram.telegrambots.meta.api.objects.Update"

        disallowedPredictionReferences(plantedImport).shouldNotBeEmpty()
    }

    /** 채널 라이브러리·grpc 타입의 workflow 유입 차단(scope.md 위협 모델 (j)) — 양성 대조. */
    @Test
    fun `grpc 채널 타입을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import io.grpc.ManagedChannel"

        disallowedPredictionReferences(plantedImport).shouldNotBeEmpty()
    }

    @Test
    fun `허용 루트를 쓰는 정상 소스 줄은 이 술어에 걸리지 않는다`() {
        val allowedLines =
            listOf(
                "import bidvector.procurement.ResolvedBaseAmount",
                "import bidvector.decision.MlUnavailableReason",
                "import bidvector.sharedkernel.Rate",
                "import bidvector.workflow.event.CorrelationId",
                "import kotlinx.coroutines.runBlocking",
                "import java.time.Duration",
                "    val now: java.time.Instant = clock.now()",
            )

        allowedLines.flatMap(::disallowedPredictionReferences).shouldBeEmpty()
    }
}

/** 이 패키지가 참조해도 되는 정규화 이름의 루트(scope.md ①, 위협 모델 (j)). */
private val ALLOWED_ROOTS =
    listOf(
        "kotlin",
        "kotlinx",
        "java",
        "javax",
        "bidvector.sharedkernel",
        "bidvector.procurement",
        "bidvector.decision",
        "bidvector.workflow",
    )

/**
 * `foo.Baz`·`foo.bar.Baz` 형태 — 4A `EditSessionImportBoundaryTest.QUALIFIED_REFERENCE`와
 * 같은 정규식(그 파일은 strategy 패키지 전체가 out_of_scope 라 직접 재사용할 수 없어
 * 구조만 옮겨 적는다 — CPD 는 test source set 을 관찰만 한다, `duplicate-policy.properties`
 * `fail.source-sets=main`).
 */
private val QUALIFIED_REFERENCE = Regex("""\b([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*)\.[A-Z][A-Za-z0-9_]*\b""")

private fun isAllowedRoot(qualifiedPackage: String): Boolean =
    ALLOWED_ROOTS.any { qualifiedPackage == it || qualifiedPackage.startsWith("$it.") }

internal fun disallowedPredictionReferences(line: String): List<String> =
    QUALIFIED_REFERENCE
        .findAll(line)
        .map { match -> match.groupValues[1] }
        .filterNot(::isAllowedRoot)
        .toList()
