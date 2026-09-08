package bidvector.workflow.strategy

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S-3b(scope.md ⑦) — `workflow` 는 application 층이라 `moduleDependencyGate`·
 * `domainSourceReferenceGate`(도메인 게이트 가족)가 걸리지 않는다(설계 검토 (1) —
 * `SourceReferencePolicy.isDomain` 이 `layer.domain` 목록으로만 판정하고 `workflow`는
 * `layer.application`이다). 그래서 이 test 가 유일한 방어선이다.
 *
 * `KonepsAdapterDependencyTest`(S-3B `adapters` 관례)와 같은 골격이지만 **deny-list가
 * 아니라 allow-list다**(설계 검토 (1) — 「Telegram 패키지 금지 목록」이 아니라 「참조 루트가
 * 이것뿐임을 단언」). import 문뿐 아니라 완전정규화 참조(FQN, import 없이 쓰는 이름)도 같은
 * 술어로 본다(설계 검토 (4) 6) — 그래서 술어는 정규식 하나가 아니라 "점으로 이어진 소문자
 * 세그먼트 다음에 대문자로 시작하는 식별자"를 찾아 그 소문자 경로의 루트가 allow-list에
 * 있는지를 본다.
 */
class EditSessionImportBoundaryTest {
    @Test
    fun `strategy 편집 패키지는 kotlin·kotlinx·java·shared-kernel·strategy·workflow 밖의 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/workflow/strategy")
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

    /**
     * 양성 대조(설계 검토 (4) 6) — 심은 표본이 같은 술어에 실제로 걸리는지 직접 확인한다.
     * 술어가 늘 통과만 하는(빈 목록을 항상 내는) 회귀를 막는다.
     */
    @Test
    fun `Telegram 패키지를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import org.telegram.telegrambots.meta.api.objects.Update"
        val plantedFqn = "    val update: org.telegram.telegrambots.meta.api.objects.Update? = null"

        disallowedQualifiedReferences(plantedImport).shouldNotBeEmpty()
        disallowedQualifiedReferences(plantedFqn).shouldNotBeEmpty()
    }

    @Test
    fun `허용 루트를 쓰는 정상 소스 줄은 이 술어에 걸리지 않는다`() {
        val allowedLines =
            listOf(
                "import bidvector.strategy.StrategyDraft",
                "import bidvector.sharedkernel.Resolution",
                "import bidvector.workflow.strategy.Actor",
                "import kotlinx.coroutines.runBlocking",
                "import java.time.Instant",
                "    val now: java.time.Instant = clock.now()",
            )

        allowedLines.flatMap(::disallowedQualifiedReferences).shouldBeEmpty()
    }
}

/** 이 패키지가 참조해도 되는 정규화 이름의 루트(설계 검토 (1)). */
private val ALLOWED_ROOTS =
    listOf(
        "kotlin",
        "kotlinx",
        "java",
        "javax",
        "bidvector.sharedkernel",
        "bidvector.strategy",
        "bidvector.workflow",
    )

/** `foo.bar.Baz` 형태 — 점으로 이어진 소문자(숫자 포함) 세그먼트 다음에 대문자로 시작하는 식별자. */
private val QUALIFIED_REFERENCE = Regex("""\b([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+)\.[A-Z][A-Za-z0-9_]*\b""")

private fun isAllowedRoot(qualifiedPackage: String): Boolean =
    ALLOWED_ROOTS.any { qualifiedPackage == it || qualifiedPackage.startsWith("$it.") }

internal fun disallowedQualifiedReferences(line: String): List<String> =
    QUALIFIED_REFERENCE
        .findAll(line)
        .map { match -> match.groupValues[1] }
        .filterNot(::isAllowedRoot)
        .toList()
