package bidvector.workflow.evaluation

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * scope.md 우회 (7)(계약 갱신 2, verifier r1 V-1) — `ArchitectureGateTest`의 Locale 축은
 * `policy.domainModules`(`layer.domain`)에만 걸리고 `workflow`는 `layer.application`이라
 * 대상 밖이다(`architecture-policy.properties`). 이 test 가 S-5 `NotificationBoundaryTest`
 * 관례(소스 텍스트 스캔 + 양성 대조)를 `EvidenceLines.kt` 한 파일에 적용해 그 자리를 대신한다.
 */
class EvidenceLinesBoundaryTest {
    @Test
    fun `EvidenceLines kt 소스에 Locale 서식 API 참조가 없다`() {
        val target = File("src/main/kotlin/bidvector/workflow/evaluation/EvidenceLines.kt")
        check(target.isFile) { "대상 파일을 찾지 못했다: ${target.absolutePath}" }

        val violations = target.readLines().flatMap(::disallowedFormattingReferences)

        violations.shouldBeEmpty()
    }

    /** 양성 대조(4A·4C-1·S-5 관례 계승) — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `Locale 서식 API 를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        val plantedLines =
            listOf(
                "import java.util.Locale",
                "import java.text.DecimalFormat",
                "String.format(Locale.getDefault(), \"%d\", value)",
                "value.format(pattern)",
            )

        plantedLines.forEach { line -> disallowedFormattingReferences(line).shouldNotBeEmpty() }
    }

    @Test
    fun `정상 소스 줄은 이 술어에 걸리지 않는다`() {
        val allowedLines =
            listOf(
                "import bidvector.decision.BidNowReason",
                "import bidvector.workflow.prediction.SegmentSupport",
                "diagnostics.trainingRowCount.toString() + \"건\"",
                "diagnostics.shrinkageWeight.value.toPlainString()",
            )

        allowedLines.flatMap(::disallowedFormattingReferences).shouldBeEmpty()
    }
}

/** 이 파일에서 금지하는 서식 API 어휘(팀장 지시 — 매직 문자열은 이 목록 하나로). */
private val DISALLOWED_FORMATTING_SUBSTRINGS =
    listOf(
        "java.util.Locale",
        "java.text.",
        "String.format",
        ".format(",
        "Locale.",
    )

internal fun disallowedFormattingReferences(line: String): List<String> =
    DISALLOWED_FORMATTING_SUBSTRINGS.filter { pattern -> line.contains(pattern) }
