package bidvector.app

import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * 관리 표면(D-6A2a-4) — health 를 **API 포트가 아닌 별도 관리 포트**에 내고, 그 포트에 health
 * 말고 아무것도 두지 않는다.
 *
 * 같은 포트에 두면 [bidvector.app.http.OperatorCredentialFilter] 에 경로 예외가 필요해지고,
 * 경로 예외는 정규화(`..`·`;`·인코딩·후행 `/`) 우회의 문이다(D-6A1-21 이 디스패치 한 가지로
 * audit·인증 우회를 실측한 계열). 포트 분리는 그것을 **구성으로** 닫는다 — API 포트에는
 * actuator 가 아예 없고(자격증명을 줘도 404), 관리 포트에는 health 말고 아무것도 없다.
 * 관리 포트가 받는 요청은 audit 대상이 아니다(프로브 트래픽) — 이것이 경계다.
 *
 * 이 상수는 잠금 property source 의 이름이다 — `addFirst` 가 같은 이름을 먼저 치우므로
 * 잠금을 두 번 걸어도 source 가 쌓이지 않는다.
 */
const val MANAGEMENT_SURFACE_LOCK_SOURCE: String = "bidvector-management-surface-lock"

/**
 * **환경이 넓힐 수 없는** 관리 표면 값. [lockManagementSurface] 가 이 값을 가장 높은
 * 우선순위 property source 로 심는다.
 *
 * 왜 [PRODUCTION_DISPATCH_PROPERTIES] 자리로는 부족한가(실측, 2026-09-26):
 * `SpringApplicationBuilder.properties(...)` 는 `SpringApplication.setDefaultProperties` 로
 * 들어가고 그 source 는 **가장 낮은** 우선순위다 — 환경변수 한 줄
 * (`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,env`)이 그것을 이기고 우회 2 를 다시 연다.
 * Boot 4.1 의 기본값 자체는 이미 안전하지만(노출 기본 `health`·세부 기본 `never`·프로브 기본
 * 켜짐 — 실측) **기본값은 환경이 이긴다**. 이 잠금이 지키는 것은 기본값이 아니라 그 순서다.
 *
 * `spring.jmx.enabled` 도 여기서 끈다 — JMX 노출 기본값(`health`)을 환경이 `*` 로 넓히면
 * 관리 포트와 무관한 두 번째 표면이 열린다. Boot 기본도 꺼짐이지만 같은 이유로 못박는다.
 *
 * readiness 그룹에 `db` 를 넣는다(우회 7) — DB 가 없을 때 「트래픽 받아도 됨」이 참이면
 * 준비 상태가 거짓을 말한다. `management.endpoint.health.validate-group-membership` 기본값이
 * 참이라, `db` 기여자가 없는 classpath 에서는 **기동이 실패**한다(조용한 축 소실이 없다).
 */
val MANAGEMENT_SURFACE_LOCK: Map<String, String> =
    mapOf(
        "management.endpoints.web.base-path" to "/actuator",
        "management.endpoints.web.exposure.include" to "health",
        "management.endpoints.web.exposure.exclude" to "",
        // 링크 목록 endpoint(`/actuator`)도 표면이다 — 끄면 health 외 경로가 전부 404 다.
        "management.endpoints.web.discovery.enabled" to "false",
        "management.endpoint.health.show-details" to "never",
        "management.endpoint.health.show-components" to "never",
        "management.endpoint.health.probes.enabled" to "true",
        "management.endpoint.health.group.liveness.include" to "livenessState",
        "management.endpoint.health.group.readiness.include" to "readinessState,db",
        "spring.jmx.enabled" to "false",
    )

/**
 * 배치가 **정할 수 있는** 관리 표면 키(D-6A2a-4 의 유일한 자유). 그 자유의 상한은
 * [lockManagementSurface] 의 포트 분리 판정이 가둔다.
 */
val MANAGEMENT_SURFACE_DEPLOYMENT_KEYS: Set<String> = setOf("management.server.port")

/**
 * **거부 대상 이름공간**(D-6A2a-10). [MANAGEMENT_SURFACE_LOCK] 이 이름 댄 키가 걸린 접두사
 * 전부이며, `ManagementSurfaceLockTest` 가 두 집합의 리터럴과 그 포함 관계를 함께 단언한다 —
 * 잠금에 새 이름공간의 키를 더하면 여기도 함께 늘리지 않는 한 붉어진다.
 *
 * 왜 키 열거가 아니라 접두사인가(r1 실측): 잠금이 **이름으로** 고정한 키는 우선순위상 빈틈이
 * 없었다. 그러나 `management.endpoint.health.*` 는 열린 이름공간이고, 같은 출력에 닿는 **형제
 * 키**가 잠금 밖에 있었다 — 그룹별 `show-details`·`show-components`·`include`/`exclude`, 새 그룹
 * 이름, `status.http-mapping`, `probes.add-additional-paths`, `validate-group-membership`.
 * 환경변수 한두 줄이 그 키들로 우회 2·3·7 을 다시 열었다. 키를 더 적는 쪽으로 고치면 **다음
 * Boot 판이 새 키를 더할 때 같은 결함이 돌아온다** — 접두사는 그때도 닫혀 있다.
 */
val MANAGEMENT_SURFACE_GOVERNED_PREFIXES: Set<String> = setOf("management", "spring.jmx")

private val GOVERNED_PREFIX_NAMES: List<ConfigurationPropertyName> =
    MANAGEMENT_SURFACE_GOVERNED_PREFIXES.map(ConfigurationPropertyName::of)

private val DEPLOYMENT_KEY_NAMES: List<ConfigurationPropertyName> =
    MANAGEMENT_SURFACE_DEPLOYMENT_KEYS.map(ConfigurationPropertyName::of)

private fun isGovernedByLock(name: ConfigurationPropertyName): Boolean =
    GOVERNED_PREFIX_NAMES.any { it == name || it.isAncestorOf(name) } && DEPLOYMENT_KEY_NAMES.none { it == name }

/**
 * 잠금 밖의 속성 소스가 정하려 하는 관리 표면 키 — **정규형 이름**으로 돌려준다(값은 담지 않는다).
 *
 * 판정을 원시 키 문자열이 아니라 Boot 의 [ConfigurationPropertySources] 가 내는 정규형에 거는
 * 이유는 relaxed binding 이다. `MANAGEMENT_ENDPOINT_HEALTH_GROUP_READINESS_SHOWDETAILS` ·
 * `SPRING_APPLICATION_JSON` 이 평탄화한 점 표기 · `include[0]` 같은 색인 형태는 **글자가 서로
 * 다르지만 같은 속성에 바인딩된다** — 원시 문자열 접두사 비교는 그 가운데 하나만 막는다.
 * 이름공간 판정은 [ConfigurationPropertyName.isAncestorOf] 가 하므로 `.` 를 글자로 세지 않는다.
 *
 * 열거할 수 없는 소스(서블릿 컨텍스트 stub 등)는 이 시점에 볼 것이 없다 — 그 축을 막는 것은
 * 잠금을 **맨 앞**에 심는다는 사실이고, `ManagementSurfaceLockTest` 가 그 형태를 잠근다.
 */
fun managementSurfaceKeysOutsideLock(environment: ConfigurableEnvironment): List<String> {
    val offending = sortedSetOf<String>()
    environment.propertySources
        .asSequence()
        .filterNot { it.name == MANAGEMENT_SURFACE_LOCK_SOURCE }
        .filterNot { ConfigurationPropertySources.isAttachedConfigurationPropertySource(it) }
        .forEach { source ->
            ConfigurationPropertySources.from(source).forEach { adapted ->
                if (adapted is IterableConfigurationPropertySource) {
                    adapted
                        .stream()
                        .toList()
                        .filter(::isGovernedByLock)
                        .forEach { offending += it.toString() }
                }
            }
        }
    return offending.toList()
}

/**
 * 관리 표면을 못박는다 — ① 관리 포트가 API 포트와 분리됐는지 확인하고 ② 잠금 밖에서 관리 표면
 * 키를 정하려는 소스가 있으면 **기동을 거부하고** ③ [MANAGEMENT_SURFACE_LOCK] 을 가장 높은
 * 우선순위로 심는다.
 *
 * ① 의 판정은 Boot 자신의 [ManagementPortType] 이 한다. 포트 비교를 손으로 다시 쓰지 않는 이유:
 * **actuator 가 child context 로 갈라지는지를 결정하는 것이 바로 이 enum** 이라, 직접 비교는
 * Boot 의 실제 분기 규칙과 조용히 어긋날 수 있다. `SAME`(관리 포트 미설정 또는 API 포트와 같음)과
 * `DISABLED`(음수) 둘 다 거부한다 — 앞은 actuator 를 API 포트의 필터 체인 안으로 들이고,
 * 뒤는 준비 상태를 알릴 자리를 없앤다.
 *
 * ② 는 ① 과 **같은 모양**의 fail-closed 판정이다(D-6A2a-10). 값을 조용히 덮어쓰지 않고 거부하는
 * 쪽을 고른 근거: 덮어쓰면 배치는 자기 설정이 왜 먹지 않는지 모르고, 같은 잠금이 **새 키에 대해
 * 아무 말도 하지 않는** 형태가 다시 생긴다. 거부 메시지에는 **키 이름만** 싣는다 — 값에는 자격이
 * 실려 올 수 있고(예: 오타로 들어온 자격 키), 기동 실패 문면은 운영 로그로 간다.
 */
fun lockManagementSurface(environment: ConfigurableEnvironment) {
    val portType = ManagementPortType.get(environment)
    check(portType == ManagementPortType.DIFFERENT) {
        "관리 포트가 API 포트와 분리되지 않았다(ManagementPortType=$portType) — " +
            "management.server.port 가 비어 있거나 server.port 와 같거나 음수다(D-6A2a-4)"
    }
    val outsideLock = managementSurfaceKeysOutsideLock(environment)
    check(outsideLock.isEmpty()) {
        "관리 표면 키를 환경이 정하려 한다(D-6A2a-10): ${outsideLock.joinToString(", ")} — " +
            "배치가 정하는 것은 ${MANAGEMENT_SURFACE_DEPLOYMENT_KEYS.joinToString(", ")} 하나다" +
            "(값은 이 문면에 싣지 않는다)"
    }
    environment.propertySources.addFirst(
        MapPropertySource(MANAGEMENT_SURFACE_LOCK_SOURCE, MANAGEMENT_SURFACE_LOCK),
    )
}

/**
 * [lockManagementSurface] 를 조립에 얹는 자리. `ApplicationContextInitializer` 를 쓰는 이유는
 * 이 시점이 **환경은 준비됐고 `@ConfigurationProperties` 바인딩은 아직 전**이라, property
 * source 순서 변경이 그대로 바인딩에 반영되기 때문이다.
 */
class ManagementSurfaceLock : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        lockManagementSurface(applicationContext.environment)
    }
}
