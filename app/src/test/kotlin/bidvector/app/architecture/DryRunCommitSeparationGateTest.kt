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

    /**
     * cr G-3 — **금지 대상 집합의 모집단 등식.** 앞 판은 「등재된 이름이 실재한다」(낡은 이름)만
     * 재고 「빠진 이름이 없다」(새 이름)는 아무것도 재지 않았다. 그래서 규칙의 적용 범위가
     * 수동 등재 의무에 달려 있었다.
     *
     * 금지 대상 쪽은 **도출할 수 있다**: 커밋 경로의 뿌리는 어댑터 조립
     * (`EvaluationCommitRun`) 하나이고, 그것을 **참조하는 production 클래스**가 곧 커밋 경로를
     * 손에 쥔 것들이다. 그 닫힘(뿌리 + 참조자)이 등재와 같은지 잰다 — 새로 그 조립을 감싸는
     * 클래스를 더하고 등재를 잊으면 이 등식이 그 자리에서 붉어진다(`gate-tests.properties` 의
     * 「모집단 == 등재」와 같은 형태, D-6G2g-10).
     *
     * **선택자(dry-run) 쪽은 도출하지 않는다** — 「dry-run 조립」에 기계적 정의가 없다(이름
     * 접두로 뽑으면 요청·응답 값 타입과 파일 facade 가 함께 들어온다, 실측). 그래서 그 쪽의
     * 보증 범위는 **등재된 세 이름과 그 중첩 클래스로 한정**하고, 그 밖의 모양은 주입 표면 집합
     * 등식(`AppHttpDependencyGateTest`)과 dry-run E2E 의 outbox 전후 등식이 본다(규칙 KDoc).
     */
    @Test
    fun `커밋 등재는 어댑터 조립과 그 참조자의 닫힘과 같다 — 모집단 등식`() {
        val root = "bidvector.adapters.evaluation.EvaluationCommitRun"
        val referrers =
            production
                .filter { candidate ->
                    !candidate.fullName.contains('$') &&
                        candidate.directDependenciesFromSelf.any { it.targetClass.fullName == root }
                }.map { it.fullName }
                .toSet()

        (referrers + root) shouldBe policy.evaluationCommitTypes.toSet()
    }

    /**
     * R1-L-1 음성 대조 — **중첩 클래스**가 커밋 타입을 참조해도 잡힌다. 선택자에 바깥 이름만
     * 주고 fixture 의 중첩 클래스가 참조를 들고 있게 해서, 접두 비교가 실제로 그것을 고르는지
     * 잰다(정확 일치였던 앞 판에서는 이 단언이 RED 다).
     */
    @Test
    fun `중첩 클래스의 참조도 잡는다 — 선택자 접두 비교`() {
        val outer = "${policy.packageRoot}.archfixture.violating.app.RogueDryRunNestedCommitReferencer"

        val messages =
            rules
                .dryRunMustNotReferenceCommitTypes(
                    dryRunClasses = setOf(outer),
                    commitTypes = policy.evaluationCommitTypes.toSet(),
                ).flatMap { rule -> rule.evaluateMessages(violating) }

        messages.joinToString("\n") shouldContain "RogueDryRunNestedCommitReferencer\$"
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
