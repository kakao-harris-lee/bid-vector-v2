package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.assertions.withClue
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
    private val violating: JavaClasses =
        ClassFileImporter().importPackages(fixtureRoot, "${policy.packageRoot}.adapters.archfixture")

    private val appRoot = "${policy.packageRoot}.app"

    private fun capabilityPorts(): Set<String> = rules.capabilityPorts(production)

    private fun useCaseAssemblyTypes(): Set<String> = rules.useCaseAssemblyTypes(production, capabilityPorts())

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

    /**
     * 규칙 다섯을 **한 번에** 평가한다(verifier r3 L-r3-1) — `forEach { check }` 로 돌면 첫 규칙이
     * RED 일 때 뒤 규칙의 위반이 가려져, 한 변이가 몇 개의 잠금을 지났는지 알 수 없다.
     */
    @Test
    fun `production 의 app 층들이 각자의 허용 목록 밖을 참조하지 않는다`() {
        val violations = productionDetails()

        withClue(violations.joinToString("\n")) { violations shouldBe emptyList() }
    }

    /**
     * D-6A2b-32 — 세 층이 **서로 겹치지 않고**, 합이 면제 전체이며, `app.http` 가 어느 층에도
     * 없다. 한 클래스가 두 층에 들면 어느 규칙이 서는지가 목록의 순서에 달리게 된다.
     */
    @Test
    fun `면제는 세 층으로 갈리고 서로 겹치지 않는다`() {
        val tier1 = policy.appAssemblyTier1Classes
        val tier2 = policy.appAssemblyTier2Classes
        val tier3 = policy.appAssemblyTier3Classes

        (tier1 + tier2 + tier3).size shouldBe (tier1 + tier2 + tier3).toSet().size
        policy.appAssemblyExemptClasses.toSet() shouldBe (tier1 + tier2 + tier3).toSet()
        (tier1 + tier2 + tier3).filter { it.startsWith(policy.appHttpPackage + ".") } shouldBe emptyList()
        // ② 층은 컨트롤러가 받는 것이다 — HTTP 층의 허용 클래스 목록에 실제로 들어 있어야 한다.
        tier2.filter { it !in policy.appHttpAllowedClasses } shouldBe emptyList()
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

        // 「대상 == 전체 − 면제」는 구현이 그대로 하는 일이라 항진식이다(M-r3-2) — 빼고, 실제로
        // 일하는 둘만 남긴다. ① 면제가 **실재하는** 클래스만 가리키는가(낡은 이름이 남아 조용히
        // 넓어지지 않게) ② 면제의 크기가 고정인가(새 이름이 눈에 띄지 않게 늘지 않게).
        exempt - allTopLevel shouldBe emptySet()
        exempt.size shouldBe EXPECTED_EXEMPT_COUNT
        // `app.http` 는 어느 면제 갈래에도 없다 — 면제는 「어댑터를 쥐어도 되는 자리」다.
        exempt.filter { it.startsWith(policy.appHttpPackage + ".") } shouldBe emptyList()
    }

    /** 면제가 없는 루트에서는 모든 클래스가 제한 층이다 — 위반 fixture 로 실측한다. */
    @Test
    fun `면제가 없는 루트에서는 핸들러도 조립도 전부 제한 층이다`() {
        rules.targets(violating, fixtureRoot + ".app", emptyLayers()).any {
            it.contains("RogueAdminBumpController")
        } shouldBe true
        policy.appHttpPackage shouldBe "$appRoot.http"
    }

    /** M-r3-3 — ② 층 규칙의 **영구 음성 대조**. 층 배정만 바꾸고 규칙 값은 production 과 같다. */
    @Test
    fun `요청 스코프 층이 JDBC 를 쥐면 잡는다`() {
        tierFixtureRules() mustReport ("RogueTier2JdbcHolder" to "JdbcClient")
    }

    /** M-r3-3 — ③ 층 규칙의 영구 음성 대조. */
    @Test
    fun `제한 층이 수집 레인을 참조하면 잡는다`() {
        tierFixtureRules() mustReport ("RogueCollectionReferencer" to "RogueCollectionLane")
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
     * 예외 통로의 **양성 대조** — 오류 매핑표가 참조하는 어댑터 예외 타입은 **타입 축**에서
     * 보고되지 않는다. 이 단언이 없으면 「어댑터는 전부 금지」로 좁혀도 음성 test 가 초록이라,
     * 매핑표가 설 자리가 사라진 것을 아무도 모른다.
     *
     * 멤버 축은 별개다 — 같은 타입이라도 **자기 멤버**를 부르면 보고된다(D-6A2b-37, 음성
     * fixture 가 그 자리를 잡는다). 그래서 타입 축의 두 규칙만 본다.
     */
    @Test
    fun `어댑터 예외 타입 참조는 타입 축에서 보고되지 않는다 — 정확 목록 통로`() {
        val typeAxis = listOf(AppRuleId.RESTRICTED_ALLOWLIST, AppRuleId.TIER2_ALLOWLIST)
        val fixtures = fixtureRules()
        val production = rules.rules(appRoot, capabilityPorts(), useCaseAssemblyTypes())
        val details =
            typeAxis.flatMap { fixtures.getValue(it).detailsOn(violating) } +
                typeAxis.flatMap { production.getValue(it).detailsOn(this.production) }

        details.filter { it.contains("InvalidStoredStrategyException") } shouldBe emptyList()
        details.filter { it.contains("CandidateCapExceededException") } shouldBe emptyList()
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

    /**
     * D-6A2b-50(N-r5-1·N-r5-2·F-r5-3) — ② 층 workflow 허용 목록의 두 성질.
     *
     * ① **능력 포트를 담지 않는다.** 계약 문면이 요구한 「구조 분류로 도출」이 이 단언이다 —
     * 다음 편집이 `EventSink` 를 목록에 넣는 순간 RED 다. 술어 쪽에도 같은 갈래가 서 있어
     * (`tier2Allows` 첫 갈래) 목록이 유일한 방벽이 아니다.
     * ② **오늘 실제 참조와 등식이다.** 죽은 항목이 남으면 「받아도 된다」가 사실이 아닌 채 남는다.
     */
    @Test
    fun `② 층 workflow 목록은 능력 포트를 담지 않고 오늘의 참조와 같다 — 집합 등식`() {
        val allowed = policy.appTier2AllowedWorkflowTypes.toSet()

        (allowed intersect capabilityPorts()) shouldBe emptySet()
        rules.observedTier2WorkflowTypes(production, appRoot) shouldBe allowed
    }

    /**
     * D-6A2b-49 — 주입 목록은 **오늘 실제 주입 타입과 등식**이다(죽은 항목 금지). 목록이 관측보다
     * 넓으면 「받아도 된다」가 사실이 아닌 채로 남고, 좁으면 게이트가 붉다.
     */
    @Test
    fun `주입 허용 목록이 오늘의 주입 표면과 같다 — 집합 등식`() {
        val exempted = policy.appInjectionCarrierExemptions.map { it.substringAfterLast('|') }.toSet()

        observedInjectionTypes() shouldBe policy.appInjectionAllowedTypes.toSet() + exempted
    }

    /**
     * D-6A2b-49 — **범용 능력 운반 타입은 목록에 오를 수 없다.** 무엇이든 담는 그릇이라 「이 타입을
     * 받아도 된다」가 아무것도 제한하지 않는다(verifier r5 F-r5-1 이 그 그릇으로 SQL 을 날랐다).
     * 예외는 계약 파일의 (클래스, 타입) 쌍뿐이고, 그 쌍도 **오늘 실재하는 주입점**이어야 한다.
     */
    @Test
    fun `목록과 예외가 범용 운반 타입을 담지 않는다 — 목록 검증`() {
        val carrierNames = observedCarrierInjections().map { it.substringAfterLast('|') }.toSet()

        (carrierNames intersect policy.appInjectionAllowedTypes.toSet()) shouldBe emptySet()
        policy.appInjectionCarrierExemptions.toSet() shouldBe observedCarrierInjections()
    }

    /** 오늘 대상 층이 실제로 받는 타입 전수(자기 자신 제외 — 규칙과 같은 모양). */
    private fun observedInjectionTypes(): Set<String> =
        rules
            .injectionTargets(production, appRoot)
            .flatMap { item ->
                rules
                    .injectionTypes(item)
                    .filterNot { it.name.substringBefore('$') == item.name.substringBefore('$') }
                    .map { it.name }
            }.toSet()

    /** 그 가운데 운반 타입인 것 — (클래스, 타입) 쌍으로 낸다. */
    private fun observedCarrierInjections(): Set<String> =
        rules
            .injectionTargets(production, appRoot)
            .flatMap { item ->
                rules
                    .injectionTypes(item)
                    .filter { rules.isCapabilityCarrier(it) }
                    .map { "${item.name.substringBefore('$')}|${it.name}" }
            }.toSet()

    /**
     * D-6A2b-45 — 규칙마다 **영구 음성 fixture** 가 하나씩 있고, **그 규칙이** 그것을 보고한다.
     *
     * verifier r4 F-r4-4: 규칙 넷을 항상 공집합이 되게 바꿔도 RED 는 한 건뿐이었다. production 이
     * 오늘 그 규칙들을 어기지 않으니 **항진식이 되어도 조용하다**. 합쳐서 보면 다른 규칙의 위반이
     * 그 자리를 메우므로, 판정은 **규칙별로** 한다 — 어느 규칙 하나를 공집합으로 바꾸면 그 규칙의
     * 줄이 RED 다. 층 배정은 규칙마다 다르므로 규칙 값은 그대로 두고 배정만 얹는다.
     */
    @Test
    fun `규칙마다 자기 음성 fixture 를 보고한다 — 규칙별 비공허성`() {
        val expected =
            mapOf(
                AppRuleId.RESTRICTED_ALLOWLIST to listOf("RogueAdminSqlHelper", "RogueUnlistedExceptionUser"),
                AppRuleId.INJECTION_SURFACE to listOf("RogueInjectedClosure"),
                AppRuleId.TIER2_ALLOWLIST to listOf("RogueTier2JdbcHolder"),
                AppRuleId.COLLECTION_REFERENCE to listOf("RogueCollectionReferencer"),
                AppRuleId.ADAPTER_MEMBER_CALL to listOf("RogueAdapterMemberCaller", "RogueExceptionOwnMember"),
                AppRuleId.TIER2_CAPABILITY to listOf("RogueTier2CapabilityHolder"),
                AppRuleId.USE_CASE_CONSTRUCTION to listOf("RogueUseCaseAssembler"),
                AppRuleId.TIER1_HTTP_API to listOf("RogueTier1HttpExtension"),
            )
        val byRule = tierFixtureRules()

        // 규칙이 늘면 fixture 도 함께 늘어야 한다 — 새 규칙이 음성 대조 없이 서지 못한다.
        byRule.keys shouldBe AppRuleId.entries.toSet()
        expected.keys shouldBe AppRuleId.entries.toSet()

        val missing =
            expected.flatMap { (id, markers) ->
                val details = byRule.getValue(id).detailsOn(violating)
                markers.filter { marker -> details.none { it.contains(marker) } }.map { "$id: $it" }
            }
        missing shouldBe emptyList()
    }

    private fun fixtureRules(): Map<AppRuleId, ArchRule> =
        rules.rules(fixtureRoot + ".app", capabilityPorts(), useCaseAssemblyTypes(), emptyLayers())

    /**
     * fixture 루트의 층 배정 — 같은 규칙 값에 배정만 얹는다. 규칙마다 영구 음성 fixture 를
     * 하나씩 두려면 그 fixture 가 서는 층도 함께 정해야 한다(D-6A2b-45).
     */
    private fun tierFixtureRules(): Map<AppRuleId, ArchRule> =
        rules.rules(
            fixtureRoot + ".app",
            capabilityPorts(),
            useCaseAssemblyTypes(),
            LayerAssignment(
                bootstrap = setOf("$fixtureRoot.app.rules.RogueTier1HttpExtension"),
                requestScoped =
                    setOf(
                        "$fixtureRoot.app.tiers.RogueTier2JdbcHolder",
                        "$fixtureRoot.app.rules.RogueTier2CapabilityHolder",
                    ),
                collection = setOf("$fixtureRoot.app.tiers.RogueCollectionLane"),
            ),
        )

    private fun emptyLayers(): LayerAssignment = LayerAssignment(emptySet(), emptySet(), emptySet())

    private fun fixtureDetails(): List<String> = fixtureRules().values.flatMap { it.detailsOn(violating) }

    private fun productionDetails(): List<String> =
        rules
            .rules(appRoot, capabilityPorts(), useCaseAssemblyTypes())
            .values
            .flatMap { it.detailsOn(production) }

    private fun ArchRule.detailsOn(classes: JavaClasses): List<String> =
        allowEmptyShould(true)
            .evaluate(classes)
            .failureReport.details

    /** 이름만 보지 않는다 — **어느 대상 때문에** 잡혔는지까지 확인한다(기존 음성 test 관례). */
    private infix fun Map<AppRuleId, ArchRule>.mustReport(expected: Pair<String, String>) {
        val details = values.flatMap { it.detailsOn(violating) }
        details.filter { it.contains(expected.first) && it.contains(expected.second) }.shouldNotBeEmpty()
    }
}

/**
 * 면제 목록의 크기 — 새 이름이 눈에 띄지 않게 늘지 않도록 못박는다(M-r3-2). 늘려야 하면 이
 * 숫자를 함께 고치게 되고, 그 커밋이 사유를 남긴다.
 */
private const val EXPECTED_EXEMPT_COUNT = 28
