package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * D-6A2b-8 — `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` 폐쇄 판정. 양성(production 이
 * 규칙을 지킨다)과 음성(심은 위반을 그 사유로 잡는다)을 한 자리에서 잰다 — 규칙 값은
 * 같고 평가 루트만 바뀐다.
 *
 * D-6A2b-10 의 재측정(자격증명 참조 집합)도 여기 둔다 — 같은 「집합 등식」 축이고, 새
 * 쓰기 표면이 그 집합을 넓혔는지가 이 slice 의 물음이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppHttpDependencyGateTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = AppHttpDependencyRules(policy)
    private val collectionRules = CollectionArchitectureRules()
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(policy.packageRoot)
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)

    private fun capabilityPorts(): Set<String> = rules.capabilityPorts(production)

    @Test
    fun `use case 생성자에서 도출한 포트 집합이 계약과 같다 — 집합 등식`() {
        rules.derivedUseCasePorts(production) shouldBe policy.appHttpUseCasePorts.toSet()
    }

    /**
     * 분류는 손이 아니라 구조 술어가 한다 — 주변 포트는 「무인자 메서드로 `java.time` 값만
     * 내는 것」이고 오늘은 `Clock` 하나다. 새 포트가 이 술어를 만족하지 못하면 자동으로
     * 능력 쪽(닫히는 방향)에 떨어진다.
     */
    @Test
    fun `주변 포트는 시각 포트 하나이고 나머지는 전부 능력 포트다`() {
        rules.ambientPorts(production) shouldBe setOf("bidvector.workflow.strategy.Clock")
        capabilityPorts() shouldBe policy.appHttpUseCasePorts.toSet() - "bidvector.workflow.strategy.Clock"
    }

    @Test
    fun `production 의 app http 는 능력 포트·어댑터 구현·원시 SQL 을 참조하지 않는다`() {
        rules
            .rules(policy.appHttpPackage, capabilityPorts(), policy.appHttpAdaptersRoot)
            .forEach { rule -> rule.check(production) }
    }

    @Test
    fun `app http 가 저장 포트를 쥐면 잡는다`() {
        fixtureRules() mustReport ("RogueHttpPortHolder" to "StrategyRepository")
    }

    @Test
    fun `app http 가 어댑터 구현을 참조하면 잡는다`() {
        fixtureRules() mustReport ("RogueHttpAdapterUser" to "SystemClock")
    }

    @Test
    fun `app http 의 원시 SQL 을 잡는다`() {
        fixtureRules() mustReport ("RogueHttpSqlUser" to "java.sql")
    }

    /**
     * 예외 통로의 **양성 대조** — 오류 매핑표가 참조하는 어댑터 예외 타입은 보고되지
     * 않는다. 이 단언이 없으면 「어댑터는 전부 금지」로 좁혀도 음성 test 가 초록이라,
     * 매핑표가 설 자리가 사라진 것을 아무도 모른다.
     */
    @Test
    fun `어댑터 예외 타입 참조는 보고되지 않는다 — Throwable 통로`() {
        val details = fixtureDetails() + productionDetails()
        details.filter { it.contains("InvalidStoredStrategyException") }.toList() shouldBe emptyList()
        details.filter { it.contains("CandidateCapExceededException") }.toList() shouldBe emptyList()
    }

    /**
     * D-6A2b-10 — 이 slice 가 새 표면을 열었지만 원문 자격증명을 참조하는 클래스 집합은
     * 그대로다(완전 폐쇄는 6E 로 넘긴다 — 원문은 어딘가에 `String` 으로 존재해야 한다).
     */
    @Test
    fun `운영자 자격증명 타입을 참조하는 production 클래스 집합은 그대로다 — 집합 등식`() {
        collectionRules.observedReferencers(
            production,
            listOf("${policy.packageRoot}.app"),
            policy.operatorCredentialTypes.toSet(),
        ) shouldBe policy.operatorCredentialReferencers.toSet()
    }

    private fun fixtureRules(): List<ArchRule> =
        rules.rules("$fixtureRoot.app.http", capabilityPorts(), policy.appHttpAdaptersRoot)

    private fun fixtureDetails(): List<String> =
        fixtureRules().flatMap { it.allowEmptyShould(true).evaluate(violating).failureReport.details }

    private fun productionDetails(): List<String> =
        rules
            .rules(policy.appHttpPackage, capabilityPorts(), policy.appHttpAdaptersRoot)
            .flatMap { it.allowEmptyShould(true).evaluate(production).failureReport.details }

    /** 이름만 보지 않는다 — **어느 대상 때문에** 잡혔는지까지 확인한다(기존 음성 test 관례). */
    private infix fun List<ArchRule>.mustReport(expected: Pair<String, String>) {
        val details = flatMap { it.allowEmptyShould(true).evaluate(violating).failureReport.details }
        details.filter { it.contains(expected.first) && it.contains(expected.second) }.shouldNotBeEmpty()
    }
}
