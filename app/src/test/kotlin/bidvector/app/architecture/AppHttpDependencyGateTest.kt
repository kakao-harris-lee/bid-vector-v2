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

    private val appRoot = "${policy.packageRoot}.app"

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
    fun `production 의 HTTP 로 닿는 층은 허용 목록 밖을 참조하지 않는다`() {
        rules.rules(appRoot, capabilityPorts()).forEach { rule -> rule.check(production) }
    }

    /**
     * D-6A2b-26 — 대상은 **`bidvector.app` 전체**이고 면제는 계약 파일의 **정확한 이름**뿐이다
     * (verifier r2 F-r2-1 시정 — r1 은 대상을 「핸들러 종류」로 열거했고 그 목록 밖의 진입점
     * 셋이 SQL 을 실행했다). 게이트가 보고 있는 집합을 직접 단언한다: app 의 모든 최상위
     * 클래스에서 면제 목록을 뺀 것과 **같다**. 대상이 조용히 줄면 이 단언이 먼저 붉어진다.
     */
    @Test
    fun `게이트가 보는 대상 집합은 app 전체에서 면제 목록을 뺀 것과 같다`() {
        val exempt = policy.appAssemblyExemptClasses.toSet()
        val allTopLevel = rules.appTopLevelClasses(production, appRoot)

        rules.targets(production, appRoot) shouldBe (allTopLevel - exempt)
        // 면제가 실재하는 클래스만 가리키는지 — 낡은 이름이 목록에 남아 조용히 넓어지지 않게.
        exempt - allTopLevel shouldBe emptySet()
        // `app.http` 는 어느 면제 갈래에도 없다 — 면제는 「어댑터를 쥐어도 되는 자리」다.
        exempt.filter { it.startsWith(policy.appHttpPackage + ".") } shouldBe emptyList()
    }

    /** 핸들러 애너테이션을 단 클래스는 어느 패키지에 있어도 대상이다(위반 fixture 로 실측). */
    @Test
    fun `HTTP 층 밖의 핸들러도 대상 집합에 든다`() {
        rules.targets(violating, fixtureRoot + ".app").any { it.contains("RogueAdminBumpController") } shouldBe true
        policy.appHttpPackage shouldBe "$appRoot.http"
    }

    @Test
    fun `HTTP 층이 저장 포트를 쥐면 잡는다`() {
        fixtureRules() mustReport ("RogueHttpPortHolder" to "StrategyRepository")
    }

    @Test
    fun `HTTP 층이 어댑터 구현을 참조하면 잡는다`() {
        fixtureRules() mustReport ("RogueHttpAdapterUser" to "SystemClock")
    }

    @Test
    fun `HTTP 층의 원시 SQL 을 잡는다`() {
        fixtureRules() mustReport ("RogueHttpSqlUser" to "java.sql")
    }

    /** verifier r1 MU1 재현 — HTTP 층이 자동 구성 JDBC 클라이언트를 쥐는 형태. */
    @Test
    fun `HTTP 층의 JDBC 클라이언트 지름길을 잡는다 — MU1`() {
        fixtureRules() mustReport ("RogueHttpJdbcShortcut" to "JdbcClient")
    }

    /** verifier r1 MU2b 재현 — HTTP 층 **밖** 패키지의 진짜 컨트롤러. 이전 판은 보지 못했다. */
    @Test
    fun `다른 패키지의 컨트롤러가 JDBC 로 전략을 바꾸면 잡는다 — MU2b`() {
        fixtureRules() mustReport ("RogueAdminBumpController" to "JdbcClient")
    }

    /**
     * verifier r1 MU2 재현(계약 ④ 의 형태) — 경계 빈을 쥔 헬퍼 자체는 경계 밖이지만,
     * **컨트롤러가 그것을 참조하는 순간** 허용 목록 밖이라 걸린다.
     */
    @Test
    fun `컨트롤러가 경계 빈을 쥔 헬퍼를 참조하면 잡는다 — MU2`() {
        fixtureRules() mustReport ("RogueAdminBoundaryController" to "RogueAdminSqlHelper")
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

    private fun fixtureRules(): List<ArchRule> = rules.rules(fixtureRoot + ".app", capabilityPorts())

    private fun fixtureDetails(): List<String> =
        fixtureRules().flatMap {
            it
                .allowEmptyShould(true)
                .evaluate(violating)
                .failureReport.details
        }

    private fun productionDetails(): List<String> =
        rules
            .rules(appRoot, capabilityPorts())
            .flatMap {
                it
                    .allowEmptyShould(true)
                    .evaluate(production)
                    .failureReport.details
            }

    /** 이름만 보지 않는다 — **어느 대상 때문에** 잡혔는지까지 확인한다(기존 음성 test 관례). */
    private infix fun List<ArchRule>.mustReport(expected: Pair<String, String>) {
        val details =
            flatMap {
                it
                    .allowEmptyShould(true)
                    .evaluate(violating)
                    .failureReport.details
            }
        details.filter { it.contains(expected.first) && it.contains(expected.second) }.shouldNotBeEmpty()
    }
}
