package bidvector.app

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.boot.env.DefaultPropertiesPropertySource
import org.springframework.boot.env.RandomValuePropertySource
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor
import org.springframework.core.env.CommandLinePropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.env.SimpleCommandLinePropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.web.context.support.StandardServletEnvironment

/** 잠금이 이름 대지 않은 형제 키 — 거부 문면 축의 적대 입력으로 쓴다. */
private const val SIBLING_KEY = "management.endpoint.health.group.readiness.show-details"

/** 소스 **이름** 안에 값이 실려 오는 형태의 표지 — 문면에 이것이 나오면 안 된다. */
private const val SOURCE_NAME_MARKER = "VALUE-MUST-NOT-APPEAR"

/**
 * `spring.config.import` 가 URL 을 받으면 Boot 의 `StandardConfigDataLoader` 가 소스 이름을
 * `Config resource '<resource>' via location '<location>'` 으로 짓고, 그 두 조각에 **원문 URL 이
 * 그대로** 남는다(privacy-gate r3 L-6, 바이트코드 실독). userinfo 가 든 URL 이면 자격이 소스
 * 이름을 타고 기동 실패 로그로 나간다. 표지를 보간으로 넣어 이 파일에 실 자격 형태를 적지 않는다.
 */
private val CONFIG_IMPORT_SOURCE_NAME =
    "Config resource 'URL [https://operator:$SOURCE_NAME_MARKER@cfg.internal/app.properties]' " +
        "via location 'https://operator:$SOURCE_NAME_MARKER@cfg.internal/app.properties'"

/**
 * D-6A2a-10 「관리 표면은 **접두사 거부**로 닫는다(구성)」 — 잠금 밖의 어느 속성 소스에든
 * `management.` 이름공간 키가 있으면 기동을 거부한다. 허용은 `management.server.port` 하나다.
 *
 * **왜 이 판을 쓰는가(r1 세 레인이 같은 결함을 따로 실측했다).** 직전 판은 잠금이 **이름으로
 * 고정한 키**의 우선순위만 쟀다. 그 축은 실제로 빈틈이 없었으나, `management.*` 에는 **같은
 * 출력에 닿는 다른 키**가 있다 — 그룹별 `show-details`·`show-components`·`include`/`exclude`,
 * 새 그룹 이름, `status.http-mapping`, `probes.add-additional-paths`,
 * `validate-group-membership`. 환경변수 한두 줄이 그 키들로 우회 2·3·7 을 다시 열었고, 직전
 * 판의 단언은 **모집단이 잠금 자신**(`MANAGEMENT_SURFACE_LOCK.forEach`)이라 열거 밖 키를
 * 구조적으로 잴 수 없었다. 그래서 여기서는 ① 잠금 키 집합을 **리터럴**로 못박고(키를 지우면
 * 곧바로 붉다) ② 판정은 열거가 아니라 **이름공간 접두사**로 하고 ③ 값 축이 아니라 **기동
 * 거부**를 단언한다. 새 Boot 판이 새 `management.*` 키를 더해도 접두사에 걸린다.
 *
 * 실행 단언(출하 조립을 실제로 부팅해 거부를 재는 축)은 [bidvector.app.management] 의
 * `ManagementSurfaceBootRefusalTest` 가 든다 — 이 파일은 순수 환경 판정이다.
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

    /**
     * 잠금이 고정하는 키 집합을 **손으로 적는다**(code-review r1 MEDIUM). 직전 판의 주 단언은
     * `MANAGEMENT_SURFACE_LOCK` 을 순회했다 — 키를 **지우면** 루프가 짧아질 뿐 붉지 않았고,
     * 지운 순간 그 키의 환경 축이 다시 열렸다(Boot 기본값이 같은 거동을 내는 다섯 키는 값
     * 단언으로도 잡히지 않는다). 이제 집합이 바뀌면 여기가 먼저 붉다.
     */
    @Test
    fun `잠금이 고정하는 키 집합은 리터럴이다`() {
        MANAGEMENT_SURFACE_LOCK.keys.sorted() shouldContainExactly
            listOf(
                "management.endpoint.health.group.liveness.include",
                "management.endpoint.health.group.readiness.include",
                "management.endpoint.health.probes.enabled",
                "management.endpoint.health.show-components",
                "management.endpoint.health.show-details",
                "management.endpoints.web.base-path",
                "management.endpoints.web.discovery.enabled",
                "management.endpoints.web.exposure.exclude",
                "management.endpoints.web.exposure.include",
                "spring.jmx.enabled",
            )
    }

    /**
     * 판정 데이터 둘도 리터럴로 못박고, **둘을 잇는다** — 잠금이 새 이름공간의 키를 얻는 날
     * 접두사 집합을 함께 늘리지 않으면 여기가 붉어진다(그러지 않으면 그 키만 조용히 잠금 밖
     * 형제에게 열린다: 이번 라운드가 고친 결함의 형태 그대로다).
     */
    @Test
    fun `거부 대상 이름공간과 배치 자유 키는 리터럴이고 잠금 키 전부를 덮는다`() {
        MANAGEMENT_SURFACE_GOVERNED_PREFIXES.sorted() shouldContainExactly
            listOf(
                "management",
                "server.servlet.context-parameters",
                "spring.jmx",
                "spring.web.error",
            )
        MANAGEMENT_SURFACE_DEPLOYMENT_KEYS.sorted() shouldContainExactly
            listOf("management.server.address", "management.server.port")

        MANAGEMENT_SURFACE_LOCK.keys
            .filterNot { key ->
                MANAGEMENT_SURFACE_GOVERNED_PREFIXES.any { key == it || key.startsWith("$it.") }
            }.shouldBeEmpty()
    }

    /** 판정 함수 자신도 직접 잰다 — 적대 없는 환경에서 위반 0 이어야 거부 단언이 공허하지 않다. */
    @Test
    fun `출하 기본값만 있는 환경에는 잠금 밖 관리 표면 키가 없다`() {
        managementSurfaceKeysOutsideLock(environmentWithShippedDefaults()).shouldBeEmpty()
    }

    @Test
    fun `환경변수가 그룹 단위 세부를 정하려 하면 기동을 거부하고 값은 싣지 않는다`() {
        val environment =
            environmentWithSystemEnvironment(
                mapOf(
                    "MANAGEMENT_SERVER_PORT" to "19081",
                    "MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_SHOWDETAILS" to "VALUE-MUST-NOT-APPEAR",
                ),
            )

        val thrown = shouldThrow<IllegalStateException> { lockManagementSurface(environment) }

        thrown.message!! shouldContain "management.endpoint.health.group.readiness"
        thrown.message!! shouldNotContain "VALUE-MUST-NOT-APPEAR"
    }

    @Test
    fun `명령행 인자가 관리 표면 키를 정하려 하면 기동을 거부한다`() {
        val environment =
            environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19082")).apply {
                propertySources.addFirst(
                    SimpleCommandLinePropertySource(
                        "--management.endpoint.health.group.x.include=*",
                        "--management.endpoint.health.status.http-mapping.down=200",
                    ),
                )
            }

        val thrown = shouldThrow<IllegalStateException> { lockManagementSurface(environment) }

        thrown.message!! shouldContain "management.endpoint.health.group.x.include"
        thrown.message!! shouldContain "management.endpoint.health.status.http-mapping.down"
    }

    /**
     * `SPRING_APPLICATION_JSON` 은 Boot 의 환경 후처리기가 **평탄화한 map source** 로 심는다
     * (초기화자보다 먼저 돈다). 그 source 의 키는 점 표기이므로 같은 접두사 판정에 걸려야 한다.
     */
    @Test
    fun `평탄화된 JSON source 의 관리 표면 키도 거부된다`() {
        val environment =
            environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19084")).apply {
                propertySources.addFirst(
                    MapPropertySource(
                        "spring.application.json",
                        mapOf("management.endpoint.health.probes.add-additional-paths" to "true"),
                    ),
                )
            }

        val thrown = shouldThrow<IllegalStateException> { lockManagementSurface(environment) }

        thrown.message!! shouldContain "management.endpoint.health.probes.add-additional-paths"
    }

    /**
     * 목록 값의 **색인·대괄호 형태** — relaxed binding 이 `include[0]` 을 같은 속성의 원소로
     * 읽으므로, 이름 비교 하나로 닫으면 이 형태가 남는다. 정규형 이름으로 판정한다.
     */
    @Test
    fun `대괄호 색인 형태의 관리 표면 키도 거부된다`() {
        val environment =
            environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19085")).apply {
                propertySources.addFirst(
                    MapPropertySource(
                        "hostile-indexed",
                        mapOf("management.endpoints.web.exposure.include[0]" to "env"),
                    ),
                )
            }

        shouldThrow<IllegalStateException> { lockManagementSurface(environment) }
    }

    /**
     * `spring.jmx` 도 잠금이 고정하는 이름공간이다 — JMX 는 관리 포트와 무관한 **두 번째
     * 표면**이므로 같은 방식으로 닫는다(잠금이 이름 댄 키가 걸린 접두사는 전부 대상이다).
     */
    @Test
    fun `spring jmx 이름공간의 키도 거부된다`() {
        val environment =
            environmentWithSystemEnvironment(
                mapOf("MANAGEMENT_SERVER_PORT" to "19086", "SPRING_JMX_ENABLED" to "true"),
            )

        val thrown = shouldThrow<IllegalStateException> { lockManagementSurface(environment) }

        thrown.message!! shouldContain "spring.jmx.enabled"
    }

    /**
     * 거부 문면의 **소스 이름**도 값 축이다(privacy-gate r3 L-6 · code-review r3 LOW-2).
     * `spring.config.import` 가 URL 을 받으면 그 URL 의 userinfo 가 소스 이름에 그대로 남는다 —
     * 이름을 문면에 그대로 실으면 자격이 기동 실패 로그로 나간다. 그래서 상수 이름이 아닌 소스는
     * **분류**(소스 클래스의 단순 이름)로만 실린다.
     */
    @Test
    fun `상수 이름이 아닌 소스는 문면에 분류로만 실린다`() {
        val environment = environmentWithNamedSource(CONFIG_IMPORT_SOURCE_NAME)

        val thrown = shouldThrow<IllegalStateException> { lockManagementSurface(environment) }

        thrown.message!! shouldContain SIBLING_KEY
        thrown.message!! shouldContain "MapPropertySource"
        thrown.message!! shouldNotContain SOURCE_NAME_MARKER
        thrown.message!! shouldNotContain "cfg.internal"
    }

    /**
     * **양성 대조와 근거를 함께** — 문면에 이름을 그대로 싣는 소스는 Boot·Spring 이 **상수로**
     * 정하는 것들이다. 이름을 손으로 적은 목록이 그 상수와 어긋나면 그 소스는 분류로 내려간다
     * (문면 품질만 떨어지고 표면은 열리지 않는다 — 이 열거는 **공개하는 쪽**이라 fail-closed 다).
     * 목록을 상수에 묶어 두면 판 올림에서 이름이 바뀌어도 여기가 먼저 붉다.
     *
     * `server.ports` 만 리터럴이다 — `ServerPortInfoApplicationContextInitializer` 의 그 이름이
     * `private` 상수라 참조할 수 없다(javap 실독).
     */
    @Test
    fun `문면에 이름을 그대로 싣는 소스는 Boot·Spring 상수와 같다`() {
        val constantSourceNames =
            listOf(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
                SpringApplicationJsonEnvironmentPostProcessor.SPRING_APPLICATION_JSON_PROPERTY,
                StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME,
                StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME,
                StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
                RandomValuePropertySource.RANDOM_PROPERTY_SOURCE_NAME,
                DefaultPropertiesPropertySource.NAME,
                "server.ports",
            )

        constantSourceNames.forEach { sourceName ->
            val thrown =
                shouldThrow<IllegalStateException> {
                    lockManagementSurface(environmentWithNamedSource(sourceName))
                }

            thrown.message!! shouldContain "(소스 $sourceName)"
        }
    }

    /**
     * 적대 키를 **이름을 지정한 소스**에 싣는다. 관리 포트는 별 이름의 소스에 둔다 —
     * `addFirst` 는 같은 이름의 소스를 먼저 치우므로(`MutablePropertySources` 의 계약), 포트를
     * `systemEnvironment` 에 두면 그 이름을 쓰는 측정에서 포트가 함께 사라져 판정이 포트 축에서
     * 먼저 끊긴다(측정이 아무것도 재지 못한다).
     */
    private fun environmentWithNamedSource(sourceName: String): StandardEnvironment =
        StandardEnvironment().apply {
            propertySources.addLast(
                MapPropertySource("deployment-choice", mapOf("management.server.port" to "19090")),
            )
            propertySources.addFirst(MapPropertySource(sourceName, mapOf(SIBLING_KEY to "always")))
        }

    /**
     * **양성 대조** — 배치가 정할 수 있는 유일한 키는 거부되지 않는다. 이 단언이 없으면 위
     * 거부 단언들이 「아무 것이나 거부한다」로도 참이 된다.
     */
    @Test
    fun `관리 포트 값은 환경변수로도 명령행으로도 정할 수 있다`() {
        val fromEnvironment = environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19087"))
        lockManagementSurface(fromEnvironment)
        fromEnvironment.getProperty("management.server.port") shouldBe "19087"

        val fromCommandLine =
            environmentWithSystemEnvironment(emptyMap()).apply {
                propertySources.addFirst(SimpleCommandLinePropertySource("--management.server.port=19088"))
            }
        lockManagementSurface(fromCommandLine)
        fromCommandLine.getProperty("management.server.port") shouldBe "19088"
        fromCommandLine.getProperty("management.endpoints.web.exposure.include") shouldBe "health"
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
     * 잠금이 두 번 걸려도 property source 가 쌓이지 않고, **자기 자신을 위반으로 읽지도
     * 않는다**(잠금 source 는 판정 모집단에서 이름으로 빠진다).
     */
    @Test
    fun `잠금을 두 번 걸어도 property source 는 하나다`() {
        val environment = environmentWithSystemEnvironment(mapOf("MANAGEMENT_SERVER_PORT" to "19083"))

        lockManagementSurface(environment)
        lockManagementSurface(environment)

        environment.propertySources.count { it.name == MANAGEMENT_SURFACE_LOCK_SOURCE } shouldBe 1
    }

    /**
     * **배선 형태(`addFirst`)를 잠근다.** `addFirst` 가 지키는 것은 **잠금이 이름 댄 키**다 —
     * 뒤에 실체로 채워지는 source 가 그 키를 들고 와도 잠금이 이긴다. `addFirst` 를 `addLast`
     * 로 바꾸면 이 단언이 붉어진다.
     *
     * **이 단언이 지키지 못하는 것**(code-review r2 LOW-2 · verifier r2 F-1r): 잠금이 이름
     * 대지 **않은** 형제 키다. `addFirst` 는 그 키에 대해 아무 것도 하지 않는다. 아래 test 가
     * 그 사실을 실측으로 적고, 그 축을 닫는 것은 [refuseManagementSurfaceKeysAfterRefresh]
     * (D-6A2a-14)의 **늦은 재검사**다 — 「닫혔다」와 「이 배포물에서는 도달 불가다」는 다른
     * 주장이므로 구별해 적는다.
     */
    @Test
    fun `잠금 뒤에 실체가 채워지는 source 보다 잠금이 우선한다`() {
        val environment = environmentWithLateSource(mapOf("management.endpoint.health.show-details" to "always"))

        environment.getProperty("management.endpoint.health.show-details") shouldBe "never"
    }

    /**
     * D-6A2a-14 — **늦은 source 가 운반하는 잠금 밖 형제 키**. verifier r2 가 출하 이미지에서
     * 환경변수 두 줄로 이 축을 재현했다(`server.servlet.context-parameters.*` 가 refresh 중에
     * 실체로 바뀌면서 그 안의 `management.endpoint.health.group.…` 키가 환경변수보다 높은
     * 우선순위로 들어온다). 그래서 **같은 술어를 모든 source 가 선 뒤에 한 번 더** 돌린다.
     * 여기서는 그 술어(판정 함수)가 형제 키를 실제로 잡는다는 사실만 잰다 — 부팅 축은
     * [bidvector.app.management] 의 `ManagementSurfaceLateSourceRefusalTest` 가 든다.
     */
    @Test
    fun `늦게 실체가 채워지는 source 의 잠금 밖 형제 키는 판정에 걸린다`() {
        val environment = environmentWithLateSource(mapOf(SIBLING_KEY to "always"))

        // 잠금은 이 키를 이기지 못한다 — 이름 대지 않은 형제다.
        environment.getProperty(SIBLING_KEY) shouldBe "always"
        managementSurfaceKeysOutsideLock(environment) shouldContainExactly listOf(SIBLING_KEY)
    }

    /**
     * 초기화자 시점에는 stub(비열거)이고 [lockManagementSurface] 뒤에 실체로 교체되는 source 를
     * 세운다 — 서블릿 컨텍스트 init-param 이 실제로 그리는 순서다(`initPropertySources()` 가
     * `createWebServer()` 끝에서 stub 을 열거 가능한 source 로 바꾼다). 교체 **전**에 판정이
     * 비어 있음을 함께 요구한다 — 그러지 않으면 아래 단언들이 「원래 걸려 있었다」로도 참이 된다.
     */
    private fun environmentWithLateSource(entries: Map<String, Any>): StandardEnvironment {
        val stubName = "servletContextInitParams"
        val environment =
            environmentWithShippedDefaults().apply {
                propertySources.addFirst(PropertySource.StubPropertySource(stubName))
            }

        lockManagementSurface(environment)
        managementSurfaceKeysOutsideLock(environment).shouldBeEmpty()

        environment.propertySources.replace(stubName, MapPropertySource(stubName, entries))
        return environment
    }
}
