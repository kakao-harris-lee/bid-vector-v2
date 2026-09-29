package bidvector.app.http

import bidvector.app.architecture.ArchitecturePolicy
import bidvector.app.productionApplication
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanFactoryUtils
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.function.support.RouterFunctionMapping
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 종류를 모르는 `HandlerMapping` 을 만났을 때 쓰는 표기 — 뒤에 그 빈의 클래스 이름을 붙인다.
 * 계약 파일에 그 이름이 없으면 등식이 깨지고(fail-closed), 있으면 **어느 종류를 알고도 풀지
 * 않았는지**가 계약에 남는다. 종류를 열거하는 것이 아니라, 모르는 것이 조용히 지나지 못하게 한다.
 */
private const val UNKNOWN_HANDLER_KIND = "알 수 없는 종류"

/**
 * D-6A2b-27 — **HTTP 로 무엇이 닿는가를 출하 조립에서 거둬 계약 집합과 등식으로 잠근다.**
 *
 * 의존 게이트의 대상을 「핸들러 종류」로 두면: `RouterFunction`
 * 빈 · 빈 이름 URL 매핑 · 인증보다 앞선 필터가 목록 밖에서 SQL 을 실행해도 전건 초록일 수 있다.
 * 종류를 더 세는 대신, **등록된 것을 전부 거둔다**: 모든 `HandlerMapping` 빈이 내는 handler
 * 집합과 서블릿 컨테이너의 `Filter`·`Servlet` 등록 집합. 하나라도 늘면 등식이 깨진다.
 *
 * D-6A2b-26(의존 허용 목록)과 **다른 축**이다: 26 은 「누가 무엇에 의존하는가」를, 여기서는
 * 「무엇이 실제로 HTTP 에 꽂혔는가」를 본다. 면제된 조립 클래스가 `@Bean` 으로 진입점을
 * 만드는 길은 26 이 보지 못하고 여기서만 잡힌다.
 *
 * API 포트와 관리 포트를 **각각** 잰다 — 두 컨텍스트는 서로 다른 서블릿 컨테이너다.
 */
class HttpSurfaceCensusTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "surface-census-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_surface_census_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext

        /**
         * 두 서블릿 컨텍스트(API·관리)를 모두 잡는다 — 관리 자식 컨텍스트는 부모에서 빈으로
         * 꺼낼 수 없고, Boot 가 각각 발행하는 초기화 이벤트가 유일한 공개 통로다.
         */
        private val initialized = mutableListOf<WebServerApplicationContext>()

        @JvmStatic
        @BeforeAll
        fun boot() {
            context =
                productionApplication()
                    .listeners(
                        ApplicationListener<ServletWebServerInitializedEvent> { event ->
                            initialized += event.applicationContext
                        },
                    ).properties(
                        mapOf(
                            "server.port" to "0",
                            "management.server.port" to "0",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to "1000",
                        ),
                    ).run()
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            context.close()
            postgres.stop()
        }
    }

    private val policy = ArchitecturePolicy.load()

    /** 관리 자식 컨텍스트는 부모를 갖는다 — 그것으로 두 컨텍스트를 가른다. */
    private fun contextOf(management: Boolean): ServletWebServerApplicationContext =
        initialized
            .filterIsInstance<ServletWebServerApplicationContext>()
            .single { (it.parent != null) == management }

    /**
     * 한 컨텍스트의 handler 집합. 종류마다 꺼내는 API 가 다르므로 아는 형태 셋을 풀고,
     * **모르는 형태는 표기 하나로 남겨** 등식이 깨지게 한다(열거가 아니라 fail-closed).
     */
    private fun handlers(management: Boolean): Set<String> =
        mappingBeans(management)
            .flatMap { mapping ->
                when (mapping) {
                    is AbstractHandlerMethodMapping<*> -> {
                        mapping.handlerMethods.values.map { it.beanType.name }
                    }

                    is AbstractUrlHandlerMapping -> {
                        mapping.handlerMap.values.map { handlerName(it) }
                    }

                    is RouterFunctionMapping -> {
                        listOfNotNull(mapping.routerFunction?.let { "RouterFunction" })
                    }

                    else -> {
                        listOf("$UNKNOWN_HANDLER_KIND: ${mapping::class.java.name}")
                    }
                }
            }.toSet()

    /** census 가 도는 모집단 — 한 컨텍스트에 **국소**인 `HandlerMapping` 빈이다. */
    private fun mappingBeans(management: Boolean): Collection<HandlerMapping> = namedMappingBeans(management).values

    /**
     * 같은 모집단을 **빈 이름과 함께** 낸다(N-r4-14). 타입 이름만으로 견주면 제3의 조상
     * 컨텍스트가 **이미 census 된 타입**의 다른 빈을 기여해도 포함 관계가 성립한다.
     */
    private fun namedMappingBeans(management: Boolean): Map<String, HandlerMapping> =
        contextOf(management).getBeansOfType(HandlerMapping::class.java)

    private fun handlerName(handler: Any): String = if (handler is String) handler else handler::class.java.name

    /** 어노테이션 매핑만 — 문서 등식이 실제로 읽는 모집단이다. */
    private fun annotationHandlers(management: Boolean): Set<String> =
        mappingBeans(management)
            .filterIsInstance<AbstractHandlerMethodMapping<*>>()
            .flatMap { mapping -> mapping.handlerMethods.values.map { it.beanType.name } }
            .toSet()

    private fun filters(management: Boolean): Set<String> =
        contextOf(management)
            .servletContext
            ?.filterRegistrations
            ?.values
            ?.map { it.className }
            ?.toSet()
            .orEmpty()

    private fun servlets(management: Boolean): Set<String> =
        contextOf(management)
            .servletContext
            ?.servletRegistrations
            ?.values
            ?.map { it.className }
            ?.toSet()
            .orEmpty()

    @Test
    fun `두 서블릿 컨텍스트가 뜬다 — API 포트와 관리 포트`() {
        initialized.filterIsInstance<ServletWebServerApplicationContext>().size shouldBe 2
    }

    @Test
    fun `API 포트의 handler 집합이 계약과 같다`() {
        val observed = handlers(management = false)

        observed.shouldNotBeEmpty()
        observed shouldBe policy.apiSurfaceHandlers.toSet()
    }

    /**
     * D-6A2b-27 마지막 절 — **문서↔매핑 등식의 모집단이 완전한가.** 그 등식
     * (`ProductionHttpSurfaceTest`)은 어노테이션 매핑 하나에서 (경로, 메서드)를 거둔다.
     * API 포트의 handler 가 전부 그 매핑에서 나온다면 그 모집단은 완전하고, `RouterFunction`·
     * 빈 이름 URL 매핑 같은 다른 종류가 하나라도 끼면 여기서 먼저 깨진다.
     */
    @Test
    fun `API 포트의 모든 handler 가 어노테이션 매핑에서 나온다 — 문서 등식의 모집단 완전성`() {
        handlers(management = false) shouldBe annotationHandlers(management = false)
    }

    /**
     * M-r3-4 — 진입점 동일성이 **클래스 이름**이라 세 자리에서 접힌다. 그 가운데 가장 좁은 갈래
     * (두 번째 method mapping 빈이 **이미 계약에 있는 컨트롤러**를 새 경로에 거는 형태)는 위
     * 두 등식이 보지 못한다. 그래서 「빈이 하나뿐」과 「`RouterFunction` 이 없음」을 직접 못박는다 —
     * 둘 중 하나가 생기는 순간 **게이트 자체를 고쳐야** 열린다.
     */
    @Test
    fun `API 포트의 진입점 축이 하나다 — method mapping 빈 하나 · RouterFunction 없음`() {
        contextOf(management = false)
            .getBeansOfType(AbstractHandlerMethodMapping::class.java)
            .map { (name, bean) -> "$name:${bean::class.java.name}" }
            .sorted() shouldBe policy.apiSurfaceMethodMappingBeans.sorted()
        contextOf(management = false)
            .getBeansOfType(RouterFunctionMapping::class.java)
            .values
            .mapNotNull { it.routerFunction } shouldBe emptyList()
    }

    @Test
    fun `API 포트의 Filter·Servlet 등록 집합이 계약과 같다`() {
        filters(management = false) shouldBe policy.apiSurfaceFilters.toSet()
        servlets(management = false) shouldBe policy.apiSurfaceServlets.toSet()
    }

    @Test
    fun `관리 포트의 handler 집합이 계약과 같다`() {
        handlers(management = true) shouldBe policy.managementSurfaceHandlers.toSet()
    }

    /**
     * N-r4-9 — 진입점 축을 관리 포트에도 세운다. handler 집합은 **클래스 이름**의 집합이라
     * 같은 종류의 handler 를 내는 mapping 빈이 하나 더 생겨도 그대로다. 오늘은 D-6A2a 의
     * 노출 잠금이 따로 서 있지만, 두 포트의 축이 같은 모양이어야 한 쪽만 조용해지지 않는다.
     */
    @Test
    fun `관리 포트의 진입점 축도 계약과 같다 — method mapping 빈 · RouterFunction 없음`() {
        contextOf(management = true)
            .getBeansOfType(AbstractHandlerMethodMapping::class.java)
            .map { (name, bean) -> "$name:${bean::class.java.name}" }
            .sorted() shouldBe policy.managementSurfaceMethodMappingBeans.sorted()
        contextOf(management = true)
            .getBeansOfType(RouterFunctionMapping::class.java)
            .values
            .mapNotNull { it.routerFunction } shouldBe emptyList()
    }

    @Test
    fun `관리 포트의 Filter·Servlet 등록 집합이 계약과 같다`() {
        filters(management = true) shouldBe policy.managementSurfaceFilters.toSet()
        servlets(management = true) shouldBe policy.managementSurfaceServlets.toSet()
    }

    /**
     * D-6A2b-38(L-r3-5·verifier L-r3-2) — 계약에 오른 `알 수 없는 종류: …CompositeHandlerMapping`
     * 은 **풀지 않고 수용한 항목**이다. 수용의 근거는 그것이 스스로 handler 를 만들지 않고
     * 자기 컨텍스트와 **조상**의 `HandlerMapping` 빈에게 넘기기만 한다는 것 — 즉 census 가
     * 두 컨텍스트에서 이미 도는 빈들이다. 근거를 문장으로만 두지 않고 여기서 잠근다.
     *
     * census 는 컨텍스트마다 **국소** 빈만 돈다. 조상까지 포함해 닿는 집합이 그 합집합을
     * 넘어서면(제3의 조상 컨텍스트가 끼면) 위임 대상 중 census 밖이 생긴 것이라 RED 다.
     */
    @Test
    fun `관리 포트 composite 가 위임할 수 있는 mapping 빈은 census 가 이미 도는 빈이다`() {
        val reachable =
            BeanFactoryUtils
                .beansOfTypeIncludingAncestors(contextOf(management = true), HandlerMapping::class.java)
                .map { (name, bean) -> "$name:${bean::class.java.name}" }
        val censused =
            (namedMappingBeans(management = true) + namedMappingBeans(management = false))
                .map { (name, bean) -> "$name:${bean::class.java.name}" }

        reachable.shouldNotBeEmpty()
        censused shouldContainAll reachable
    }
}
