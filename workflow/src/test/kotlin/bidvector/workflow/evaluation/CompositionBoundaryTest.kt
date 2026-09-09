package bidvector.workflow.evaluation

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * S-3b(scope.md ⑦, 설계 검토 (4) 9) — legacy 실패 형태 ⑦(「한 함수가 수집·DB·ML·알림을
 * 함께 한다」)의 뒤집기를 소스 스캔으로 단언한다. `workflow`는 application 층이라
 * `moduleDependencyGate`·`domainSourceReferenceGate`(도메인 게이트 가족)가 걸리지 않으므로
 * (`SourceReferencePolicy.isDomain`이 `layer.domain`만 보고 `workflow`는
 * `layer.application`) 이 test가 유일한 방어선이다 — `EditSessionImportBoundaryTest`
 * (4A)의 골격을 이 패키지 allow-list로 좁혀 재사용한다(4A의 술어를 재사용하되 목록을
 * 이 패키지에 맞게 좁히라는 지시, 설계 검토 (4) 9).
 *
 * allow-list다(deny-list가 아니다) — 「참조 루트가 이것뿐임을 단언」한다. import 문뿐
 * 아니라 완전정규화 참조(FQN)도 같은 술어로 본다.
 */
class CompositionBoundaryTest {
    @Test
    fun `조합 패키지는 allow-list 밖의 좌표를 참조하지 않는다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/workflow/evaluation")
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

    /** 양성 대조 — 심은 표본이 같은 술어에 실제로 걸리는지 직접 확인한다(4A 관례). */
    @Test
    fun `Telegram 패키지를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedImport = "import org.telegram.telegrambots.meta.api.objects.Update"
        val plantedFqn = "    val update: org.telegram.telegrambots.meta.api.objects.Update? = null"

        disallowedQualifiedReferences(plantedImport).shouldNotBeEmpty()
        disallowedQualifiedReferences(plantedFqn).shouldNotBeEmpty()
    }

    /** 이 패키지가 실제로 참조하지 않는 형제 도메인 모듈(예: `adapters`)을 심어도 걸린다. */
    @Test
    fun `이 slice out_of_scope 인 adapters 참조를 심은 표본도 이 술어에 걸린다 — 양성 대조`() {
        disallowedQualifiedReferences("import bidvector.adapters.SomeJpaEntity").shouldNotBeEmpty()
    }

    @Test
    fun `허용 루트를 쓰는 정상 소스 줄은 이 술어에 걸리지 않는다`() {
        val allowedLines =
            listOf(
                "import bidvector.decision.Verdict",
                "import bidvector.procurement.Notice",
                "import bidvector.qualification.LicenseVerdict",
                "import bidvector.strategy.OperatorStrategy",
                "import bidvector.sharedkernel.Resolution",
                "import bidvector.workflow.event.CorrelationId",
                "import bidvector.workflow.strategy.StrategyRepository",
                "import kotlinx.coroutines.runBlocking",
                "import java.math.BigDecimal",
                "    val now: java.time.Instant = clock.now()",
            )

        allowedLines.flatMap(::disallowedQualifiedReferences).shouldBeEmpty()
    }
}

/** 이 패키지가 참조해도 되는 정규화 이름의 루트 — `EditSessionImportBoundaryTest`(4A) 목록을 이 slice가 실제로 쓰는 도메인 모듈로 넓힌 것. */
private val ALLOWED_ROOTS =
    listOf(
        "kotlin",
        "kotlinx",
        "java",
        "javax",
        "bidvector.sharedkernel",
        "bidvector.procurement",
        "bidvector.qualification",
        "bidvector.strategy",
        "bidvector.decision",
        "bidvector.workflow",
    )

/**
 * `foo.Baz`·`foo.bar.Baz` 형태 — 점으로 이어진 소문자(숫자·언더스코어 포함) 세그먼트
 * 하나 이상 다음에 대문자로 시작하는 식별자(4A `EditSessionImportBoundaryTest` verifier
 * L-7 수정판과 동일 정규식 — 단일 소문자 세그먼트 루트도 잡는다).
 *
 * **알려진 사각(4A와 동일)**: 대문자로 시작하는 단일 세그먼트 루트(`Telegram.Bot`)는 잡지
 * 못한다 — 강화하면 이 패키지 자신의 정당한 sealed 하위 타입 접근(`Verdict.BidNow`·
 * `WatchVerdict.Rejected` 등)까지 오탐한다. milestone-4.md 알려진 제한에 등재한다.
 */
private val QUALIFIED_REFERENCE = Regex("""\b([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*)\.[A-Z][A-Za-z0-9_]*\b""")

private fun isAllowedRoot(qualifiedPackage: String): Boolean =
    ALLOWED_ROOTS.any {
        qualifiedPackage == it ||
            qualifiedPackage.startsWith("$it.")
    }

internal fun disallowedQualifiedReferences(line: String): List<String> =
    QUALIFIED_REFERENCE
        .findAll(line)
        .map { match -> match.groupValues[1] }
        .filterNot(::isAllowedRoot)
        .toList()
