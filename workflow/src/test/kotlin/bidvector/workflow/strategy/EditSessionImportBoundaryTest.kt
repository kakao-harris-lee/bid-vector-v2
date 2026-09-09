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

    /**
     * verifier L-7 수정 — 단일 소문자 세그먼트 패키지도 이제 걸린다(강화 전에는 세그먼트
     * 둘 이상을 요구해 이 형태가 빠져나갔다).
     */
    @Test
    fun `단일 세그먼트 소문자 패키지를 심은 표본도 이 술어에 걸린다 — 양성 대조`() {
        disallowedQualifiedReferences("    val bot: telegram.Bot? = null").shouldNotBeEmpty()
        disallowedQualifiedReferences("    val bot: telegram_api.Bot? = null").shouldNotBeEmpty()
    }

    /**
     * 알려진 제한(verifier L-7) — 대문자로 시작하는 단일 세그먼트 루트(`Telegram.Bot`)는
     * 이 술어가 잡지 못한다. 잡으려면 `EditSessionState.Applied` 같은 이 패키지 자신의
     * 정당한 sealed 하위 타입 접근까지 오탐한다(위 `QUALIFIED_REFERENCE` KDoc 참고) —
     * 그래서 이 test 는 「걸린다」가 아니라 「걸리지 않는다(알려진 사각)」를 고정한다.
     */
    @Test
    fun `대문자로 시작하는 단일 세그먼트 루트는 알려진 사각이다 — 이 술어가 잡지 못한다`() {
        disallowedQualifiedReferences("    val bot: Telegram.Bot? = null").shouldBeEmpty()
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

/**
 * `foo.Baz`·`foo.bar.Baz` 형태 — 점으로 이어진 소문자(숫자·언더스코어 포함) 세그먼트
 * 하나 이상 다음에 대문자로 시작하는 식별자(verifier L-7 수정 — 이전 버전은 세그먼트
 * **둘 이상**을 요구해 `telegram.Bot`처럼 단일 세그먼트 패키지가 빠져나갔다).
 *
 * **여전히 잡지 못하는 자리 — 대문자로 시작하는 단일 세그먼트 루트**(`Telegram.Bot`).
 * 그 형태를 잡으려면 정규식이 `EditSessionState.Applied`·`TransitionOutcome.Rejected`
 * 같은 **이 패키지 자신의 정당한 sealed 하위 타입 접근**(대문자 루트 + 대문자 타입 —
 * verifier B-6 지적으로 재실측(2026-09-09): 정규식 `\b([A-Za-z][A-Za-z0-9_]*)\.[A-Z]
 * [A-Za-z0-9_]*\b` 를 이 디렉터리에 적용하면 총 매치 97건 · 매치를 담은 줄 85건 ·
 * 그중 주석 아닌 줄 81건 · 고유 매치 문자열 42종 — 원래 적었던 「46건」은 오산이었다)
 * 까지 함께 잡는다 — 강화가 대량 오탐을 부르므로 하지 않는다(설계 검토 (4) 6 이
 * 요구하는 것은 소문자 세그먼트 판별이지 대소문자 판별이 아니다). 그래서 이 사각은
 * 술어가 아니라 **알려진 제한**으로 남는다(milestone-4.md).
 */
private val QUALIFIED_REFERENCE = Regex("""\b([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*)\.[A-Z][A-Za-z0-9_]*\b""")

private fun isAllowedRoot(qualifiedPackage: String): Boolean =
    ALLOWED_ROOTS.any { qualifiedPackage == it || qualifiedPackage.startsWith("$it.") }

internal fun disallowedQualifiedReferences(line: String): List<String> =
    QUALIFIED_REFERENCE
        .findAll(line)
        .map { match -> match.groupValues[1] }
        .filterNot(::isAllowedRoot)
        .toList()
