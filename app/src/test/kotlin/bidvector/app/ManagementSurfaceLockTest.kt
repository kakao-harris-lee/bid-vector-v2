package bidvector.app

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.SimpleCommandLinePropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * D-6A2a-4 (2b) 「새 설정 키(`management.*`)」 — 관리 표면 값은 조립 근이 고정하고 **환경이
 * 넓히지 못해야** 한다. 넓힐 수 있으면 우회 2(관리 포트에서 `env`·`configprops`·`heapdump`로
 * 비밀값을 읽는다)가 다시 열린다.
 *
 * **실측 전제(구현 레인, 2026-09-26)** — `SpringApplicationBuilder.properties(...)`는
 * `SpringApplication.setDefaultProperties`로 들어가고 그 property source 는 **가장 낮은**
 * 우선순위다. 그래서 [PRODUCTION_DISPATCH_PROPERTIES] 형태만으로는 환경변수 한 줄이 노출을
 * 넓힌다. [lockManagementSurface]는 잠금 값을 `addFirst`로 **가장 높은** 자리에 심어 그 축을
 * 닫는다 — 이 test 가 재는 것이 그 순서다.
 *
 * 명령행 인자까지 함께 재는 이유: `commandLineArgs`는 표준 우선순위에서 환경변수보다 **위**다.
 * 명령행을 이기면 환경변수는 자동으로 닫힌다(더 강한 축 하나로 두 축을 덮는다).
 */
class ManagementSurfaceLockTest {
    private fun environmentWithSystemEnvironment(entries: Map<String, String>): StandardEnvironment =
        StandardEnvironment().apply {
            propertySources.replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    entries,
                ),
            )
        }

    /** 출하 기본값(가장 낮은 자리) — `main()`이 주는 것과 같은 참조를 같은 우선순위로 둔다. */
    private fun environmentWithShippedDefaults(): StandardEnvironment =
        StandardEnvironment().apply {
            propertySources.addLast(
                MapPropertySource(
                    "defaultProperties",
                    PRODUCTION_DISPATCH_PROPERTIES + PRODUCTION_MANAGEMENT_PROPERTIES,
                ),
            )
        }

    @Test
    fun `환경변수로 관리 표면을 넓히려 해도 잠금 값이 이긴다`() {
        val environment =
            environmentWithSystemEnvironment(
                mapOf(
                    "MANAGEMENT_SERVER_PORT" to "19081",
                    "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE" to "health,env,configprops,heapdump",
                    "MANAGEMENT_ENDPOINTS_WEB_DISCOVERY_ENABLED" to "true",
                    "MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS" to "always",
                    "MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS" to "always",
                    "MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_INCLUDE" to "readinessState",
                    "SPRING_JMX_ENABLED" to "true",
                ),
            )

        // 잠금 전에는 환경변수가 이긴다 — 이 단언이 없으면 아래 단언이 공허하게 참일 수 있다.
        environment.getProperty("management.endpoints.web.exposure.include") shouldBe
            "health,env,configprops,heapdump"

        lockManagementSurface(environment)

        MANAGEMENT_SURFACE_LOCK.forEach { (key, locked) ->
            environment.getProperty(key) shouldBe locked
        }
    }

    @Test
    fun `명령행 인자로도 관리 표면을 넓힐 수 없다`() {
        val environment =
            environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19082")).apply {
                propertySources.addFirst(
                    SimpleCommandLinePropertySource(
                        "--management.endpoints.web.exposure.include=health,env",
                        "--management.endpoint.health.show-details=always",
                    ),
                )
            }

        environment.getProperty("management.endpoints.web.exposure.include") shouldBe "health,env"

        lockManagementSurface(environment)

        environment.getProperty("management.endpoints.web.exposure.include") shouldBe "health"
        environment.getProperty("management.endpoint.health.show-details") shouldBe "never"
    }

    @Test
    fun `관리 포트가 API 포트와 같으면 조립을 거부한다`() {
        val environment =
            environmentWithSystemEnvironment(
                mapOf("SERVER_PORT" to "8080", "MANAGEMENT_SERVER_PORT" to "8080"),
            )

        ManagementPortType.get(environment) shouldBe ManagementPortType.SAME
        shouldThrow<IllegalStateException> { lockManagementSurface(environment) }
    }

    @Test
    fun `관리 포트가 없으면 조립을 거부한다`() {
        val environment = environmentWithSystemEnvironment(emptyMap())

        shouldThrow<IllegalStateException> { lockManagementSurface(environment) }
    }

    @Test
    fun `관리 포트를 끄면 조립을 거부한다`() {
        val environment = environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "-1"))

        ManagementPortType.get(environment) shouldBe ManagementPortType.DISABLED
        shouldThrow<IllegalStateException> { lockManagementSurface(environment) }
    }

    @Test
    fun `출하 기본값만으로 관리 포트가 API 포트와 분리된다`() {
        val environment = environmentWithShippedDefaults()

        ManagementPortType.get(environment) shouldBe ManagementPortType.DIFFERENT
        lockManagementSurface(environment)
        environment.getProperty("management.endpoints.web.exposure.include") shouldBe "health"
    }

    /**
     * 잠금이 두 번 걸려도 property source 가 쌓이지 않는다 — `main()`과 boot test 가 같은
     * 초기화자를 공유하므로 재적용이 조용히 중복되는 형태를 막는다.
     */
    @Test
    fun `잠금을 두 번 걸어도 property source 는 하나다`() {
        val environment = environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19083"))

        lockManagementSurface(environment)
        lockManagementSurface(environment)

        environment.propertySources.count { it.name == MANAGEMENT_SURFACE_LOCK_SOURCE } shouldBe 1
    }
}
