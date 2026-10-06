package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * D-6F10-15 — dry-run 조립이 커밋 조립 타입을 참조하지 않는다(양성)와, 그 규칙이 실제로
 * 잡는다(음성, 심은 fixture)를 한 파일에서 잰다.
 *
 * 음성 쪽은 「같은 규칙 값에 fixture 루트를 넣는다」는 기존 관례
 * (`ArchitectureGateCatchesViolationsTest`)를 **선택자 집합**으로 옮긴 것이다 — 이 규칙은
 * 패키지가 아니라 클래스 이름 집합으로 대상을 고르므로, fixture 의 이름을 그 자리에 넣는 것이
 * 루트를 바꾸는 것과 같은 치환이다. 금지 대상 집합(커밋 타입)은 **양쪽에서 같은 값**이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DryRunCommitSeparationGateTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = DryRunCommitSeparationRules()
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(policy.packageRoot)
    private val violating: JavaClasses =
        ClassFileImporter().importPackages("${policy.packageRoot}.archfixture.violating")

    @Test
    fun `dry-run 조립은 커밋 조립 타입을 참조하지 않는다`() {
        rules
            .dryRunMustNotReferenceCommitTypes(
                dryRunClasses = policy.evaluationDryRunTypes.toSet(),
                commitTypes = policy.evaluationCommitTypes.toSet(),
            ).forEach { rule -> rule.check(production) }
    }

    /**
     * 두 집합이 **실재하는 이름만** 담는지 — 낡은 이름이 남으면 규칙이 고르는 클래스가 줄거나
     * 0 이 되어 조용히 아무것도 재지 않는다. `commit-types` 는 금지 대상이라 선택자 실패 핀이
     * 지켜 주지 않으므로 여기서 따로 잰다.
     */
    @Test
    fun `등재된 두 집합의 이름은 production 바이트코드에 실재한다`() {
        val productionNames = production.map { it.fullName }.toSet()

        (policy.evaluationDryRunTypes.toSet() - productionNames) shouldBe emptySet()
        (policy.evaluationCommitTypes.toSet() - productionNames) shouldBe emptySet()
    }

    /** 음성 대조 — 심은 fixture 가 커밋 조립 타입을 참조하면 그 사유로 잡힌다. */
    @Test
    fun `커밋 조립 타입을 참조하는 표본을 그 사유로 잡는다`() {
        val fixture = "${policy.packageRoot}.archfixture.violating.app.RogueDryRunCommitReferencer"

        val messages =
            rules
                .dryRunMustNotReferenceCommitTypes(
                    dryRunClasses = setOf(fixture),
                    commitTypes = policy.evaluationCommitTypes.toSet(),
                ).flatMap { rule -> rule.evaluateMessages(violating) }

        messages.shouldNotBeEmpty()
        messages.joinToString("\n") shouldContain "RogueDryRunCommitReferencer"
        messages.joinToString("\n") shouldContain "EvaluationCommitRun"
    }
}

private fun ArchRule.evaluateMessages(classes: JavaClasses): List<String> = evaluate(classes).failureReport.details
