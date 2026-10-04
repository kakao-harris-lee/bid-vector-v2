package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * D-6G2b-7 전송 표면 게이트의 **음성** 쪽 — production 을 지키는 **같은 규칙 값**에 fixture 뿌리를 넣어
 * 심은 우회를 잡는지 잰다. 위반 상세에서 심은 클래스 이름과 **어느 전송 타입 때문에** 잡혔는지를 함께
 * 확인한다(다른 이유로 잡혀도 통과하는 masking 을 막는다).
 *
 * verifier 가 매 라운드 손으로 심던 변이를 저장소에 둔다 — 술어를 고치지 않고 fixture 만 빼면 [MUTATIONS]
 * 의 그 행이 RED 다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransportSurfaceGateCatchesViolationsTest {
    private val policy = ArchitecturePolicy.load()
    private val violatingRoot = "${policy.packageRoot}.archfixture.violating"
    private val fixtureRoot = "$violatingRoot.transport"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)
    private val violatingAll: JavaClasses = ClassFileImporter().importPackages(violatingRoot)
    private val rules = transportRules(policy.depth(DepthAxis.TRANSPORT))
    private val ownerOnly = transportRules(ReferenceCollection.OWNER_ONLY)

    private fun transportRules(collection: ReferenceCollection) =
        TransportSurfaceRules(
            packageRoot = policy.packageRoot,
            surfacePackages = policy.transportSurfacePackages.toSet(),
            surfaceTypes = policy.transportSurfaceTypes.toSet(),
            collection = collection,
        )

    @Test
    fun `심은 우회마다 그 전송 타입 때문에 잡힌다`() {
        MUTATIONS.forEach { (fixture, surfaceType) ->
            withClue("$fixture -> $surfaceType") { rules.mustReport(fixture, surfaceType) }
        }
    }

    /**
     * PR #58 D — 덮개를 **열거에서 구조로**. 모집단은 패키지 모양이 아니라 **이 slice 의 게이트 셋이 실제로
     * 신고하는 fixture 전수**다. 그래서 `violating..` 아래 어느 패키지에 fixture 를 더해도, 그 게이트가 그것을
     * 신고하면 모집단에 들어오고 덮개 표에 없으면 RED 다. 앞 판은 `…transport` 한 패키지와
     * `endsWith(".external")` 라는 **이름 모양**에 기대어 새 패키지를 놓쳤다.
     *
     * 양방향이다 — 표에만 있고 신고되지 않는 이름도 RED(fixture 가 지워졌거나 변이가 더는 성립하지 않는다).
     */
    @Test
    fun `게이트가 신고하는 음성 fixture 전수가 덮개 표와 같다 — 양방향`() {
        val reported = reportedFixtureNames()

        reported.shouldNotBeEmpty()
        (reported - COVERED) shouldBe emptySet()
        (COVERED - reported) shouldBe emptySet()
    }

    /** 이 slice 의 세 게이트(전송 쌍 · 바깥 참조 · 반사)가 `violating..` 에서 신고하는 최상위 단순 이름 전수. */
    private fun reportedFixtureNames(): Set<String> {
        val transport = rules.rules(listOf(violatingRoot), policy.transportHolderPairs).details(violatingAll)
        val external =
            policy.externalJudgedModules.flatMap { module ->
                rules
                    .externalReferenceRules(
                        "$violatingRoot.$module",
                        policy.externalAllowedPackages(module).toSet(),
                    ).details(violatingAll)
            }
        val reflection =
            CollectionArchitectureRules(policy.depth(DepthAxis.REFLECTION))
                .moduleMustNotUseReflection(
                    roots = policy.reflectionJudgedModules.map { "$violatingRoot.$it" },
                    reflectionPackages = policy.reflectionPackages.toSet(),
                    allowedTypePairs = policy.reflectionTypePairs.toSet(),
                    classType = policy.reflectionClassType,
                    allowedMemberPairs = policy.reflectionClassMemberPairs.toSet(),
                ).details(violatingAll)

        return (transport + external + reflection)
            .map { it.substringBefore(" ->").substringAfterLast('.') }
            .filter { it.startsWith("Rogue") }
            .toSet()
    }

    private fun List<ArchRule>.details(classes: JavaClasses): List<String> =
        flatMap {
            it
                .allowEmptyShould(true)
                .evaluate(classes)
                .failureReport.details
        }

    /**
     * D-6G2b-2 양성 대조 — 호출 대상의 소유 타입만 보는 수집은 `uri.toURL().readText()` 의 `java.net.URL`
     * 을 보지 못하고, 자원 URL 을 Kotlin 확장으로 읽는 길은 **아예** 보지 못한다. 인자·반환 타입 수집이
     * 조용히 되돌려지면 이 대조가 RED 다.
     */
    @Test
    fun `소유 타입만 보는 수집은 호출 사슬로만 지나는 길을 놓친다 — 깊은 수집의 양성 대조`() {
        ownerOnly.mustNotReport("RogueUrlChainFetch", "java.net.URL")
        rules.mustReport("RogueUrlChainFetch", "java.net.URL")

        ownerOnly.mustNotReport("RogueResourceUrlRead")
        rules.mustReport("RogueResourceUrlRead", "java.net.URL")
    }

    /**
     * D-6G2b-3 쌍 축 — 등재된 보유자가 **새 전송 타입을 더 쥐는** 길. 등재를 클래스 단위로 두면 초록인
     * 자리다. fixture 를 `java.net.URI` 쌍으로만 등재한 합성 집합을 넘겨, 그 쌍은 신고되지 않고 더 쥔
     * 타입만 신고됨을 잰다.
     */
    @Test
    fun `등재된 보유자가 전송 타입을 더 쥐면 그 타입만 잡는다 — 쌍 등식 축`() {
        val holder = "$fixtureRoot.RogueRegisteredHolderGainingTransport"
        val registered = setOf(holder to "java.net.URI")

        val details = rules.details(registered)

        details shouldContain "$holder -> java.net.Socket"
        details.filter { it == "$holder -> java.net.URI" }.shouldBeEmpty()
    }

    /**
     * D-6G2b-22·25 — 전송 표면 뿌리 **밖**의 JDK API 로 바이트를 내는 길. 금지 뿌리 열거에서는 전부 초록
     * 이었다(vr H-1 실측). 기본 거부 허용 목록이 모듈마다 그것을 잡는지 잰다 — 허용 집합은 production 을
     * 지키는 **같은 값**이다.
     */
    @Test
    fun `전송 뿌리 밖 JDK API 로 바이트를 내는 길을 모듈마다 잡는다 — 기본 거부`() {
        EXTERNAL_MUTATIONS.forEach { (module, fixture, externalPackage) ->
            val details =
                externalDetails("$violatingRoot.$module", policy.externalAllowedPackages(module).toSet())

            withClue("$module / $fixture -> $externalPackage") {
                details shouldContain "$violatingRoot.$fixture -> $externalPackage"
            }
        }
    }

    /**
     * `java.util.ServiceLoader` 는 **1층이 보지 않는다**. 이유는 「`java.util` 이 허용 패키지라서」가 아니다 —
     * 1층은 패키지를 유도하기 **전에** 전송 표면 타입을 걸러 내므로(두 층의 분기) 낱개 전송 타입으로 등재된
     * 이 타입은 1층의 입력에 아예 들어가지 않는다. 그래서 1층 신고가 0 이고 2층이 잡는다. `java.lang` 의
     * `ProcessBuilder` 와 같은 자리다.
     */
    @Test
    fun `허용 패키지 안의 확장 지점은 전송 낱개 타입으로 잡는다 — ServiceLoader`() {
        val holder = "$violatingRoot.adapters.external.RogueServiceLoaderExtension"
        val layerOne =
            externalDetails("$violatingRoot.adapters", policy.externalAllowedPackages("adapters").toSet())
        val layerTwo =
            rules
                .rules(listOf("$violatingRoot.adapters"), emptySet())
                .flatMap {
                    it
                        .allowEmptyShould(true)
                        .evaluate(violatingAll)
                        .failureReport.details
                }

        layerOne.filter { it == "$holder -> java.util" }.shouldBeEmpty()
        layerTwo shouldContain "$holder -> java.util.ServiceLoader"
    }

    /**
     * 양성 대조 — 허용 집합에서 패키지 하나를 빼면 **그 패키지로** 신고가 늘어난다. 빼는 것은 고정 문자열이
     * 아니라 **fixture 뿌리가 실제로 참조하면서 허용에도 있는** 패키지다(허용 목록 앞머리를 집으면 fixture
     * 가 쓰지 않는 패키지라 늘어나는 신고가 없다 — 실측으로 한 번 그렇게 됐다).
     */
    @Test
    fun `허용 패키지 하나를 빼면 그 패키지로 신고가 늘어난다 — 음성 쪽 양성 대조`() {
        val root = "$violatingRoot.adapters"
        val allowed = policy.externalAllowedPackages("adapters").toSet()
        val dropped = rules.observedExternalPackages(violatingAll, root).first { it in allowed }

        val before = externalDetails(root, allowed)
        val after = externalDetails(root, allowed - dropped)

        withClue("빼낸 패키지 $dropped") {
            (after - before.toSet()).filter { it.endsWith(" -> $dropped") }.shouldNotBeEmpty()
        }
    }

    private fun externalDetails(
        root: String,
        allowed: Set<String>,
    ): List<String> =
        rules
            .externalReferenceRules(root, allowed)
            .flatMap {
                it
                    .allowEmptyShould(true)
                    .evaluate(violatingAll)
                    .failureReport.details
            }

    /**
     * 과잉 대조 둘. 들어오는 HTTP(서블릿 표면)는 바이트를 밖으로 내지 않아 뿌리 밖이고, 전송을 아예
     * 언급하지 않는 계산은 당연히 밖이다. 어느 쪽이든 신고되면 뿌리가 넓은 것이다.
     */
    @Test
    fun `들어오는 서블릿 표면과 전송 무관 계산은 신고하지 않는다 — 과잉 대조`() {
        rules.mustNotReport("CleanInboundServletHandler")
        rules.mustNotReport("CleanLocalComputation")
    }

    private fun TransportSurfaceRules.details(registered: Set<Pair<String, String>>): List<String> =
        rules(listOf(fixtureRoot), registered)
            .flatMap {
                it
                    .allowEmptyShould(true)
                    .evaluate(violating)
                    .failureReport.details
            }

    /**
     * vr L-5 · cr ④ — 상세 줄을 **정확히** 비교한다. `contains("-> java.net.URL")` 로 재면
     * `java.net.URLConnection` 으로 잡혀도 통과해, 「다른 이유로 잡힘」을 거르려는 취지가 한 칸 헐거웠다.
     */
    private fun TransportSurfaceRules.mustReport(
        fixture: String,
        surfaceType: String,
    ) {
        details(emptySet()) shouldContain "$fixtureRoot.$fixture -> $surfaceType"
    }

    /**
     * cr ⑤ — 타입을 주지 않으면 앞 판은 `contains("")` 로 항상 참이어서 **무동작**이었다. 지금은 그
     * 클래스로 시작하는 상세가 하나도 없음을 잰다(타입을 주면 그 한 줄만).
     */
    private fun TransportSurfaceRules.mustNotReport(
        fixture: String,
        surfaceType: String? = null,
    ) {
        val reported = details(emptySet()).filter { it.startsWith("$fixtureRoot.$fixture -> ") }
        if (surfaceType == null) {
            reported.shouldBeEmpty()
        } else {
            reported.filter { it == "$fixtureRoot.$fixture -> $surfaceType" }.shouldBeEmpty()
        }
    }

    private companion object {
        /**
         * PR #58 D — 이 slice 의 음성 단언이 덮는 fixture 전수(**한 자리**). 세 표의 합이고, 전용 test 가
         * 덮는 둘(`RogueServiceLoaderExtension` 두 층 분기 · `RogueRegisteredHolderGainingTransport` 쌍 축)과
         * 반사 fixture 셋은 그 사유를 [DEDICATED_TEST_FIXTURES] 에 적는다.
         */
        val COVERED: Set<String>
            get() =
                MUTATIONS.keys +
                    EXTERNAL_MUTATIONS.map { it.second.substringAfterLast('.') } +
                    DEDICATED_TEST_FIXTURES +
                    FOREIGN_FIXTURES_REPORTED

        /**
         * **다른 slice 의 fixture 인데 이 게이트들도 신고하는 것들**(PR #58 D 의 구조적 모집단이 드러낸 전수).
         * 6A·6F 의 음성 test 가 각자의 축으로 덮는 fixture 이고, 이 게이트에는 전송 표면·바깥 참조·반사
         * 타입이 들어 있어 함께 신고된다 — 위반인 것이 맞으므로 모집단에서 빼지 않고 여기 적는다. 이 집합이
         * 늘면 다른 slice 가 fixture 를 더한 것이고, 줄면 지운 것이다(어느 쪽이든 RED 로 드러난다).
         */
        val FOREIGN_FIXTURES_REPORTED =
            setOf(
                "RogueAdminBumpController",
                "RogueDivisionFromString",
                "RogueHttpJdbcShortcut",
                "RogueTier1HttpExtension",
                "RogueTier2JdbcHolder",
            )

        /**
         * 변이 표가 아니라 **전용 test** 가 덮는 fixture — 표의 「한 fixture, 한 타입」 모양에 들어가지 않는
         * 축들이다. 반사 셋은 `CollectionArchitectureGateCatchesViolationsTest` 가 덮는다.
         */
        val DEDICATED_TEST_FIXTURES =
            setOf(
                "RogueServiceLoaderExtension",
                "RogueReflectionPeek",
                "RogueAdapterReflectionPeek",
                "RogueAdapterNameLookupGainingReflection",
            )

        /** D-6G2b-25 — (모듈, fixture 의 뿌리 아래 경로, 그 변이를 성립시키는 바깥 패키지). */
        val EXTERNAL_MUTATIONS =
            listOf(
                Triple("adapters", "adapters.external.RogueLoggingSocketSend", "java.util.logging"),
                Triple("adapters", "adapters.external.RogueXmlParseFetch", "javax.xml.parsers"),
                Triple("adapters", "adapters.external.RogueJmxConnect", "javax.management.remote"),
                Triple("adapters", "adapters.external.RogueSwingPageFetch", "javax.swing"),
                Triple("adapters", "adapters.external.RogueDesktopBrowse", "java.awt"),
                Triple("adapters", "adapters.external.RogueScriptEval", "javax.script"),
                Triple("workflow", "workflow.external.RogueWorkflowSocketSend", "java.util.logging"),
            )

        /**
         * 심은 우회 → **그 변이를 성립시키는** 전송 표면 타입. verifier r4·r5 의 변이(KA1·KA2·KA3·KA4·
         * KA12·KA13·KA14·KA15)와 이 slice 가 고안한 것들이다(cr M-2 의 간접 시그니처 넷 포함). 한 fixture 에
         * 여러 타입이 걸리는 경우에도 여기 적은 타입으로 잡혀야 하고, 비교는 상세 줄 **전체 일치**다 —
         * 「다른 이유로 잡혔다」를 통과로 세지 않는다.
         */
        val MUTATIONS =
            mapOf(
                // KA1 — 호출 사슬의 반환 타입으로만 지난다.
                "RogueUrlChainFetch" to "java.net.URL",
                // KA12·그 형제 — 대상 메서드를 문자열로 지어 부른다.
                "RogueBeanExpressionCall" to "java.beans.Expression",
                "RogueBeanStatementCall" to "java.beans.Statement",
                // KA13·그 형제 — 바깥 호출을 프로세스에 맡긴다.
                "RogueProcessCurl" to "java.lang.ProcessBuilder",
                "RogueRuntimeExec" to "java.lang.Runtime",
                // KA14 — 열거 목록에 없던 비동기 채널.
                "RogueAsyncChannelFetch" to "java.nio.channels.AsynchronousSocketChannel",
                // KA3 — 평범한 소켓(6G 가 이미 잡던 형태의 회귀).
                "RogueRawSocket" to "java.net.Socket",
                // 열거 목록에 없던 소켓 형태 둘.
                "RogueDatagramSend" to "java.net.DatagramSocket",
                "RogueSocketFactoryFetch" to "javax.net.SocketFactory",
                // KA15·그 형제 — 클래스패스에 이미 있는 Spring 전송 표면.
                "RogueSpringRestClient" to "org.springframework.web.client.RestClient",
                "RogueSpringRequestFactory" to "org.springframework.http.client.JdkClientHttpRequestFactory",
                // D-6G2g-9 (B-2 (가)) — 허용 패키지 **안**의 파일 시스템 출구. 뿌리와 낱개 열거 각각.
                "RogueFileSystemWrite" to "java.nio.file.Path",
                "RogueLegacyFileWrite" to "java.io.File",
                // D-6G2g-20 — 타입 없이 문자열 경로로 여는 생성자 셋(세 번째 기제).
                "RogueStringPathPrintWriter" to "java.io.PrintWriter",
                "RogueStringPathPrintStream" to "java.io.PrintStream",
                "RogueStringPathFormatter" to "java.util.Formatter",
                // 이 slice 가 고안한 것 — JDK 내장 서버·원격 이름 조회·자원 URL·메서드 핸들.
                "RogueHttpServerExposure" to "com.sun.net.httpserver.HttpServer",
                "RogueRmiLookup" to "java.rmi.Naming",
                "RogueJndiLookup" to "javax.naming.InitialContext",
                "RogueResourceUrlRead" to "java.net.URL",
                "RogueMethodHandleInvoke" to "java.lang.invoke.MethodHandles",
                // KA2·KA4 — 6G 가 이미 잡던 형태의 회귀.
                "RogueUnlistedHttpClientHolder" to "java.net.http.HttpClient",
                "RogueTypealiasedClient" to "java.net.http.HttpClient",
                // 쌍 등식 축 — 등재된 보유자가 전송 타입을 더 쥔다.
                "RogueRegisteredHolderGainingTransport" to "java.net.Socket",
                // cr M-2 — 전송 타입이 시그니처의 간접 자리에만 있는 형태 넷.
                "RogueGenericArgHolder" to "java.net.http.HttpClient",
                "RogueSupplierArgHolder" to "java.net.http.HttpClient",
                "RogueSamLambdaHolder" to "java.net.http.HttpClient",
                "RogueAnnotatedHolder" to "java.net.http.HttpClient",
            )
    }
}
