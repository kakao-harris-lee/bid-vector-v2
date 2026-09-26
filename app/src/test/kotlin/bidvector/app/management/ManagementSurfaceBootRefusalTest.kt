package bidvector.app.management

import bidvector.app.productionApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * D-6A2a-11 — **잠금 밖에서 부팅해** 잰다. `ManagementSurfaceLockTest` 는 `ConfigurableEnvironment`
 * 하나를 손으로 세워 판정 함수를 부른다. 그 형태로는 **배선**(잠금이 출하 조립에 실제로
 * 얹혀 있는가, 얹힌 자리가 우선순위 최상위인가)을 잴 수 없다 — 직전 판의 사각이 정확히
 * 그것이었다(code-review r1 MEDIUM ②: `.initializers(ManagementSurfaceLock())` 를 떼고
 * `.properties(MANAGEMENT_SURFACE_LOCK)` 로 바꿔도 test 전건이 초록이었다).
 *
 * 그래서 여기서는 **출하 조립 자신**([productionApplication])을 적대적 명령행 인자·환경변수로
 * 부팅한다. 두 가지를 구별해야 한다:
 * - 잠금이 **거부**했다 → 잠금이 던진 [IllegalStateException] 이 나온다.
 * - 잠금이 **통과**시켰다 → 초기화자 전부가 끝난 뒤 발행되는 [ApplicationPreparedEvent] 에
 *   달아 둔 [PassedTheLockException] 이 나온다.
 *
 * 두 번째 것이 **양성 대조**다. 이 표지가 없으면 「부팅이 실패했다」는 단언이 DB 부재 같은 다른
 * 사유로도 참이 되고, 잠금을 떼어낸 트리에서도 초록일 수 있다. 표지를 초기화자가 아니라
 * 이벤트로 둔 이유는 순서 보장이다 — `ApplicationPreparedEvent` 는 `applyInitializers` **뒤**에
 * 발행되므로 초기화자 사이의 정렬 규칙에 기대지 않는다.
 *
 * 이 부팅은 **refresh 에 닿지 않는다**(초기화자 또는 그 직후에서 끝난다) — 그래서 DB·포트·
 * 컨테이너가 필요 없고, 관리 포트도 API 포트도 bind 되지 않는다.
 */
class ManagementSurfaceBootRefusalTest {
    private class PassedTheLockException : RuntimeException("잠금이 이 조립을 통과시켰다")

    /**
     * 적대적 환경변수 — Boot 의 환경 후처리기(예: `SPRING_APPLICATION_JSON` 평탄화)보다 **먼저**
     * 돌아야 하므로 우선순위를 최상위로 둔다. 그래야 후처리기가 이 값을 실제로 읽는다.
     */
    private class HostileSystemEnvironment(
        private val entries: Map<String, String>,
    ) : ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
        override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

        override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
            val source = LinkedHashMap<String, Any>()
            source.putAll(entries)
            event.environment.propertySources.replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    source,
                ),
            )
        }
    }

    private fun boot(
        arguments: List<String> = emptyList(),
        environment: Map<String, String> = emptyMap(),
    ) {
        productionApplication()
            .listeners(
                HostileSystemEnvironment(environment),
                ApplicationListener<ApplicationPreparedEvent> { throw PassedTheLockException() },
            ).run(*arguments.toTypedArray())
    }

    /** 환경변수 이름은 Boot 의 규약대로 **파생**한다(손으로 적은 두 번째 목록을 만들지 않는다). */
    private fun environmentVariableName(key: String): String = key.uppercase().replace(".", "_").replace("-", "")

    @Test
    fun `적대적 명령행 인자는 기동을 거부한다`() {
        HOSTILE_MANAGEMENT_KEYS.forEach { (key, value) ->
            withClue(key) {
                val thrown =
                    shouldThrow<IllegalStateException> {
                        boot(arguments = listOf("--$key=$value"), environment = MINIMAL_ENVIRONMENT)
                    }
                thrown.message!! shouldContain key
            }
        }
    }

    @Test
    fun `적대적 환경변수는 기동을 거부한다`() {
        HOSTILE_MANAGEMENT_KEYS.forEach { (key, value) ->
            withClue(key) {
                shouldThrow<IllegalStateException> {
                    boot(environment = MINIMAL_ENVIRONMENT + mapOf(environmentVariableName(key) to value))
                }
            }
        }
    }

    /**
     * `SPRING_APPLICATION_JSON` 은 이름 대응이 아니라 **평탄화**로 들어온다 — 환경변수 이름
     * 접두사만 보는 판정은 이 축을 놓친다(그래서 정규형 이름으로 판정한다).
     */
    @Test
    fun `SPRING_APPLICATION_JSON 으로 넣은 관리 표면 키도 기동을 거부한다`() {
        val json = """{"management":{"endpoint":{"health":{"show-details":"always"}}}}"""

        val thrown =
            shouldThrow<IllegalStateException> {
                boot(environment = MINIMAL_ENVIRONMENT + mapOf("SPRING_APPLICATION_JSON" to json))
            }

        thrown.message!! shouldContain "management.endpoint.health.show-details"
    }

    /** 양성 대조 — 배치가 정하는 유일한 키만 주면 잠금을 **통과**한다(표지가 나온다). */
    @Test
    fun `관리 포트만 정한 환경변수는 잠금을 통과한다`() {
        shouldThrow<PassedTheLockException> { boot(environment = mapOf("MANAGEMENT_SERVER_PORT" to "0")) }
    }

    @Test
    fun `관리 포트만 정한 명령행 인자는 잠금을 통과한다`() {
        shouldThrow<PassedTheLockException> {
            boot(arguments = listOf("--management.server.port=0"), environment = emptyMap())
        }
    }

    private companion object {
        /**
         * 출하 기본값(`management.server.port=8081`)만으로도 포트 분리 판정은 선다. 그래도
         * 환경변수 축 test 가 계통 환경을 **비우므로**, 적대 키와 같은 자리에 관리 포트를 함께
         * 둬 「포트 미설정」이라는 다른 사유로 거부되는 것을 배제한다(거부 사유를 하나로 좁힌다).
         */
        private val MINIMAL_ENVIRONMENT = mapOf("MANAGEMENT_SERVER_PORT" to "0")

        /**
         * r1 세 레인이 실측한 우회 키 전부(verifier F-1·F-2·F-3 · code-review HIGH-1 ·
         * privacy-gate M-1). 값은 그 레인들이 실제로 쓴 값이다 — 판정은 값을 보지 않지만,
         * 이 표가 **무엇을 모형하는지**가 값에 남는다.
         */
        private val HOSTILE_MANAGEMENT_KEYS: List<Pair<String, String>> =
            listOf(
                "management.endpoint.health.group.readiness.show-details" to "always",
                "management.endpoint.health.group.readiness.show-components" to "always",
                "management.endpoint.health.group.x.include" to "*",
                "management.endpoint.health.status.http-mapping.down" to "200",
                "management.endpoint.health.probes.add-additional-paths" to "true",
                "management.endpoint.health.group.readiness.exclude" to "db",
                "management.endpoint.health.validate-group-membership" to "false",
            )
    }
}
