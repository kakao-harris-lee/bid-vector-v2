package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
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
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating.transport"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)
    private val rules = transportRules(ReferenceCollection.FULL)
    private val ownerOnly = transportRules(ReferenceCollection.OWNER_ONLY)

    private fun transportRules(collection: ReferenceCollection) =
        TransportSurfaceRules(
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

    /** fixture 뿌리가 비어 있지 않다 — 컴파일 출력이 바뀌어 모집단을 잃으면 위 단언이 조용히 공허해진다. */
    @Test
    fun `음성 fixture 모집단이 비어 있지 않고 변이 표가 그 클래스들을 덮는다`() {
        val planted =
            violating
                .filter { it.simpleName.startsWith("Rogue") }
                .map { it.simpleName }
                .toSet()

        planted.shouldNotBeEmpty()
        (planted - MUTATIONS.keys) shouldBe emptySet()
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

        details.filter { it.contains("$holder -> java.net.Socket") }.shouldNotBeEmpty()
        details.filter { it.contains("$holder -> java.net.URI") }.shouldBeEmpty()
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

    private fun TransportSurfaceRules.mustReport(
        fixture: String,
        surfaceType: String,
    ) {
        details(emptySet()).filter { it.contains(".$fixture -> $surfaceType") }.shouldNotBeEmpty()
    }

    private fun TransportSurfaceRules.mustNotReport(
        fixture: String,
        surfaceType: String? = null,
    ) {
        val suffix = surfaceType?.let { " -> $it" }.orEmpty()
        details(emptySet()).filter { it.contains(".$fixture") && it.contains(suffix) }.shouldBeEmpty()
    }

    private companion object {
        /**
         * 심은 우회 → **그 변이를 성립시키는** 전송 표면 타입. verifier r4·r5 의 변이(KA1·KA2·KA3·KA4·
         * KA12·KA13·KA14·KA15)와 이 slice 가 고안한 열하나다. 한 fixture 에 여러 타입이 걸리는 경우에도
         * 여기 적은 타입으로 잡혀야 한다 — 「다른 이유로 잡혔다」를 통과로 세지 않는다.
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
                // KA14 — 열거 목록에 없던 채널·소켓 형태.
                "RogueAsyncChannelFetch" to "java.nio.channels.AsynchronousSocketChannel",
                "RogueRawSocket" to "java.net.Socket",
                "RogueDatagramSend" to "java.net.DatagramSocket",
                "RogueSocketFactoryFetch" to "javax.net.SocketFactory",
                // KA15·그 형제 — 클래스패스에 이미 있는 Spring 전송 표면.
                "RogueSpringRestClient" to "org.springframework.web.client.RestClient",
                "RogueSpringRequestFactory" to "org.springframework.http.client.JdkClientHttpRequestFactory",
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
            )
    }
}
