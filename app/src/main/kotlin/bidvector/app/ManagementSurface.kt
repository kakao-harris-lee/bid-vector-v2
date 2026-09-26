package bidvector.app

import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource

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
 * 배치가 **정할 수 있는** 관리 표면 키(D-6A2a-4·15). 그 자유의 상한은 [lockManagementSurface] 의
 * 포트 분리 판정이 가둔다.
 *
 * `management.server.address` 가 둘째 키인 근거(D-6A2a-15, code-review r2 MEDIUM-1): 계약은 관리
 * 포트의 **네트워크 노출 통제를 배치 환경에 넘겼는데**(`OPEN-6A2A-MGMT-PORT-EXPOSURE`), 그 통제의
 * 가장 싼 형태가 이 키다. 이 키는 표면을 **좁히기만** 한다 — Boot 기본값이 「전 인터페이스」라
 * 넓힐 방향이 없고, 포트 분리 판정은 그대로 선다. 그래서 계약 (2b) 「환경이 넓히지 못한다」를
 * 어기지 않는다.
 */
val MANAGEMENT_SURFACE_DEPLOYMENT_KEYS: Set<String> =
    setOf("management.server.port", "management.server.address")

/**
 * **거부 대상 이름공간**(D-6A2a-10·14·17 ①). `ManagementSurfaceLockTest` 가 이 집합의 리터럴과
 * [MANAGEMENT_SURFACE_LOCK] 키 전부를 덮는다는 포함 관계를 함께 단언한다 — 잠금에 새 이름공간의
 * 키를 더하면 여기도 함께 늘리지 않는 한 붉어진다.
 *
 * 왜 키 열거가 아니라 접두사인가(r1 실측): 잠금이 **이름으로** 고정한 키는 우선순위상 빈틈이
 * 없었다. 그러나 `management.endpoint.health.*` 는 열린 이름공간이고, 같은 출력에 닿는 **형제
 * 키**가 잠금 밖에 있었다 — 그룹별 `show-details`·`show-components`·`include`/`exclude`, 새 그룹
 * 이름, `status.http-mapping`, `probes.add-additional-paths`, `validate-group-membership`.
 * 환경변수 한두 줄이 그 키들로 우회 2·3·7 을 다시 열었다. 키를 더 적는 쪽으로 고치면 **다음
 * Boot 판이 새 키를 더할 때 같은 결함이 돌아온다** — 접두사는 그때도 닫혀 있다.
 *
 * 관리 이름공간 밖의 두 항목은 **같은 표면에 닿는 인접 이름공간**이고, 앱은 둘 다 쓰지 않는다.
 * - `server.servlet.context-parameters` (r2 **보조 잠금**, verifier r2 F-1r): 이 이름공간의 값은
 *   refresh 중에 서블릿 컨텍스트 init-param 으로 옮겨져 **환경변수보다 높은 우선순위**로 환경에
 *   들어온다. 주 잠금은 [refuseManagementSurfaceKeysAfterRefresh] 이고 이 항목은 가장 짧은 경로를
 *   가장 이른 자리에서 끊는다.
 * - `spring.web.error` (r2, privacy-gate r2 L-4 실측): 관리 child context 의 `ManagementErrorEndpoint`
 *   가 이 이름공간을 읽어 `/error` 본문의 예외·메시지·스택 포함 여부를 정한다(`management.` 밖이라
 *   r1 판정이 보지 못했다). 2026-09-26 실측 — `include-message=always` 하나로 관리 포트 `/error`
 *   본문 키가 셋에서 넷으로 늘었다(`message`).
 */
val MANAGEMENT_SURFACE_GOVERNED_PREFIXES: Set<String> =
    setOf("management", "spring.jmx", "server.servlet.context-parameters", "spring.web.error")

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
 * 열거할 수 없는 소스는 **이 호출 시점에** 볼 것이 없다. 그 사실이 「닫혔다」는 뜻은 아니다
 * (code-review r2 LOW-2 의 문면 정정): 그런 소스가 뒤에 실체로 채워지면서 잠금이 이름 대지 않은
 * 형제 키를 들고 오면 `addFirst` 는 아무것도 하지 않는다. 그 축을 닫는 것은 **같은 술어를 모든
 * 소스가 선 뒤에 한 번 더 도는 것**이다([refuseManagementSurfaceKeysAfterRefresh], D-6A2a-14).
 */
fun managementSurfaceKeysOutsideLock(environment: ConfigurableEnvironment): List<String> =
    governedKeysOutsideLock(environment).map { it.second }.distinct()

/**
 * 문면에 **이름을 그대로 실을 수 있는** 소스 — Boot·Spring 이 **상수로** 정하는 이름들이다.
 * `ManagementSurfaceLockTest` 가 이 열을 그 상수들과 대조한다(`server.ports` 만 리터럴이다 —
 * `ServerPortInfoApplicationContextInitializer` 의 그 이름이 `private` 상수다).
 *
 * **이 열거는 공개하는 쪽이라 fail-closed 다**(privacy-gate r3 L-6, code-review r3 LOW-2 의
 * 「열거가 돌아온다」 우려에 대한 답): 이름이 이 열에 없으면 문면은 **분류**로 내려간다. 즉 빠뜨린
 * 이름은 문면의 유용성만 떨어뜨리고 표면을 열지 않는다. 거부 **판정**의 모집단은 여전히 환경의
 * 소스 전부이고 이 열과 무관하다.
 */
private val CONSTANT_PROPERTY_SOURCE_NAMES: Set<String> =
    setOf(
        "systemEnvironment",
        "systemProperties",
        "commandLineArgs",
        "spring.application.json",
        "servletContextInitParams",
        "servletConfigInitParams",
        "jndiProperties",
        "random",
        "defaultProperties",
        "server.ports",
    )

/** 클래스 이름을 얻을 수 없는 소스(익명 클래스)의 분류. */
private const val UNCLASSIFIED_SOURCE = "other"

/**
 * 소스를 **문면에 실을 수 있는 형태**로 바꾼다 — 상수 이름이면 그대로, 아니면 분류다.
 *
 * 왜 이름을 그대로 실을 수 없는가(privacy-gate r3 L-6, 바이트코드 실독): 설정 데이터 소스의 이름은
 * Boot 이 `Config resource '<resource>' via location '<location>'` 으로 짓고, 그 두 조각에 운영자가
 * 준 location 원문이 들어간다. `spring.config.import` 가 URL 이면 그 URL 의 userinfo 와 쿼리
 * 토큰이 이름 안에 남아 **자격이 기동 실패 로그로** 나간다. 가리는 절삭(`://…@` 치환)을 쓰지
 * 않는 이유는 그것이 문자열 술어라 다른 운반 형태에 열려 있기 때문이다 — 분류는 **이름을 아예
 * 싣지 않으므로** 형태와 무관하다.
 *
 * 분류로 쓰는 것은 소스 **클래스**의 단순 이름이다. 클래스 이름은 Boot·Spring·우리 코드가 정하고
 * 입력이 정하지 않으므로 값이 실릴 자리가 없고, 그러면서 「설정 데이터에서 왔다」
 * (`OriginTrackedMapPropertySource`)·「설정 트리에서 왔다」(`ConfigTreePropertySource`)처럼 운영에
 * 필요한 만큼은 말한다.
 */
private fun sourceLabel(source: PropertySource<*>): String =
    if (source.name in CONSTANT_PROPERTY_SOURCE_NAMES) {
        source.name
    } else {
        source.javaClass.simpleName.ifBlank { UNCLASSIFIED_SOURCE }
    }

/**
 * 같은 판정을 **키와 그 키를 실은 소스 표지의 짝**으로 돌려준다 — 거부 문면이 「어디서 온 값인가」를
 * 말할 수 있게 한다. 운영 배치가 거부를 만났을 때 가장 먼저 필요한 정보가 그것이다: 같은 키가
 * 환경변수·명령행·서블릿 init-param 어디로든 들어올 수 있다. 표지는 [sourceLabel] 이 정한다 —
 * **소스 이름 자체는 값을 실을 수 있어서**(r3 정정) 상수 이름만 그대로 나간다.
 */
private fun governedKeysOutsideLock(environment: ConfigurableEnvironment): List<Pair<String, String>> =
    environment.propertySources
        .asSequence()
        .filterNot { it.name == MANAGEMENT_SURFACE_LOCK_SOURCE }
        .filterNot { ConfigurationPropertySources.isAttachedConfigurationPropertySource(it) }
        .flatMap { source ->
            ConfigurationPropertySources
                .from(source)
                .asSequence()
                // `filterIsInstance` 를 쓰지 않는다 — reified inline 이라 stdlib(`_Sequences.kt`) 의
                // 람다 클래스가 이 모듈 아카이브에 복사되고, `jarContentGate` 가 「게이트를 통과한
                // 소스가 아니다」로 끊는다(2026-09-26 실측). `as?` 는 이 파일의 람다다.
                .mapNotNull { it as? IterableConfigurationPropertySource }
                .flatMap { it.asSequence() }
                .filter(::isGovernedByLock)
                .map { sourceLabel(source) to it.toString() }
        }.distinct()
        .sortedWith(compareBy({ it.second }, { it.first }))
        .toList()

private fun refusalMessage(
    decision: String,
    environment: ConfigurableEnvironment,
): String {
    val offending = governedKeysOutsideLock(environment).map { (label, key) -> "$key(소스 $label)" }
    return "관리 표면 키를 환경이 정하려 한다($decision): ${offending.joinToString(", ")} — " +
        "배치가 정하는 것은 ${MANAGEMENT_SURFACE_DEPLOYMENT_KEYS.joinToString(", ")} 둘이다" +
        "(속성 값도, 소스 이름이 담을 수 있는 설정 위치 문자열도 이 문면에 싣지 않는다)"
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
 * 아무 말도 하지 않는** 형태가 다시 생긴다. 거부 메시지에는 **키 이름과 소스 표지**만 싣는다 —
 * 값에는 자격이 실려 올 수 있고(예: 오타로 들어온 자격 키), 소스 **이름**에도 실려 올 수 있다
 * ([sourceLabel] 이 그 자리를 닫는다). 기동 실패 문면은 운영 로그로 간다.
 *
 * 이 검사는 **빠른 실패**다 — 모집단이 「초기화자 시점에 열거 가능한 소스」로 한정되므로 이것만으로
 * 표면이 닫히지 않는다(D-6A2a-14 가 같은 술어를 refresh 뒤에 한 번 더 돈다).
 */
fun lockManagementSurface(environment: ConfigurableEnvironment) {
    val portType = ManagementPortType.get(environment)
    check(portType == ManagementPortType.DIFFERENT) {
        "관리 포트가 API 포트와 분리되지 않았다(ManagementPortType=$portType) — " +
            "management.server.port 가 비어 있거나 server.port 와 같거나 음수다(D-6A2a-4)"
    }
    val outsideLock = managementSurfaceKeysOutsideLock(environment)
    check(outsideLock.isEmpty()) { refusalMessage("D-6A2a-10", environment) }
    environment.propertySources.addFirst(
        MapPropertySource(MANAGEMENT_SURFACE_LOCK_SOURCE, MANAGEMENT_SURFACE_LOCK),
    )
}

/**
 * **refresh 완료 시점에 서 있는 모든 소스**를 대상으로 같은 술어를 한 번 더 돈다(D-6A2a-14).
 *
 * 「모든 소스」가 아니라 이렇게 적는다(r3 정정 — code-review r3 LOW-4): `finishRefresh()` 는
 * `ContextRefreshedEvent` 를 발행한 **뒤에** web server 를 띄우고, 그 event 에서
 * `ServerPortInfoApplicationContextInitializer` 가 소스 하나(`server.ports`)를 더한다. 그 소스의
 * 키는 `local.` 이름공간뿐이라 거부 대상 밖이고 관리 표면을 넓힐 수 없다 — 그래서 위험은 0 이지만,
 * 「모든 소스」라고 적으면 뒤 slice 가 그 문장을 근거로 쓴다.
 *
 * [lockManagementSurface] 의 모집단은
 * 초기화자가 도는 그 순간 **열거 가능한** 소스뿐이다. 서블릿 컨텍스트 init-param 소스는 그 시점에
 * 비열거 stub 이고 `createWebServer()` 끝의 `initPropertySources()` 가 실체로 바꾼다 — 그 안의 키는
 * 환경변수보다 높은 우선순위로 들어오고, 잠금이 **이름 대지 않은** 형제 키라면 `addFirst` 는
 * 아무것도 하지 않는다(verifier r2 F-1r 이 출하 이미지에서 환경변수 두 줄로 r1 결함 셋을 전부
 * 재현했다). 채널을 하나씩 막으면 다음 늦은 소스가 같은 자리를 연다 — **시점**을 고친다.
 *
 * 부모 환경과 관리 child 환경은 서로 다른 소스 집합을 갖는다(child 는 자기 `StandardEnvironment`
 * 를 세우고 부모 소스를 **이름이 겹치지 않는 것만** 뒤에 붙인다 — 즉 부모의 실체화된 init-param
 * 소스는 child 에 없다). 그래서 둘 다 판정한다.
 *
 * **여기에 readiness 가드를 두지 않는다**(r3 — verifier r3 M-1·L-2, code-review r3 LOW-5).
 * r2 는 「이 검사가 도는 시점에 readiness 가 이미 수락 상태면 그 사실로 기동을 거부한다」는
 * 가드를 두어 자리 이동을 막으려 했다. 실측은 그것이 **발동할 수 없음**을 보였다 — 가드가 읽는
 * 상태는 같은 event 를 받는 가용성 bean 이 **이 listener 뒤에** 기록하므로, 재검사를 수락 시점으로
 * 옮긴 변이에서도 가드는 여전히 수락 전 상태를 본다. 죽은 코드가 구조적 방어로 읽히면 다음
 * 라운드가 그것을 근거로 쓴다. 게다가 기동 뒤의 두 번째 refresh(refresh scope·config client)가
 * 생기면 그 가드는 **정상 refresh 를 기동 실패로** 바꾼다. 자리를 잠그는 것은 이제 test 다 —
 * 적대 부팅에서 `ApplicationStartedEvent`·`ApplicationReadyEvent`·`AvailabilityChangeEvent`
 * (수락) 관측이 0 임을 단언한다(`ManagementSurfaceLateSourceRefusalTest`).
 */
fun refuseManagementSurfaceKeysAfterRefresh(context: ApplicationContext) {
    val environment = context.environment
    check(environment is ConfigurableEnvironment) {
        "관리 표면 재검사가 환경을 읽을 수 없다(D-6A2a-14) — context=${context.id}"
    }
    val outsideLock = managementSurfaceKeysOutsideLock(environment)
    check(outsideLock.isEmpty()) { refusalMessage("D-6A2a-14", environment) }
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

/**
 * [refuseManagementSurfaceKeysAfterRefresh] 를 조립에 얹는 자리. `ContextRefreshedEvent` 를 고른
 * 근거는 셋이다(Boot 4.1.1 바이트코드 실독 + 실측).
 *
 * ① **모든 소스가 서 있다.** 이 event 는 `finishRefresh()` 끝에서 발행되고, 서블릿 컨텍스트
 * init-param 소스를 실체로 바꾸는 `initPropertySources()` 는 그보다 앞인 `onRefresh()` 안이다.
 *
 * ② **부모와 관리 child 둘 다 온다.** Spring 은 child context 의 event 를 부모에게도 발행하므로
 * 조립에 listener 하나만 얹으면 두 환경을 모두 판정한다. 관리 child 의 refresh 는 부모의
 * `finishRefresh` 안(`SmartLifecycle` 단계 `Integer.MAX_VALUE - 1536`)에서 끝나므로 **부모 event
 * 보다 먼저** 온다.
 *
 * ③ **readiness 가 수락을 알리기 전이다** — 「트래픽을 한 번도 받지 않는다」가 아니다(r3 정정 —
 * privacy-gate r3 L-5 · verifier r3 L-3). Boot 은 `ReadinessState.ACCEPTING_TRAFFIC` 을
 * `ApplicationReadyEvent` **뒤**에 발행한다(`EventPublishingRunListener.ready`) — 이 event 보다 두
 * 단계 뒤다. 그 전의 readiness 프로브는 `REFUSING_TRAFFIC` → `OUT_OF_SERVICE` → 503 이고, 실측으로
 * 잰다(`ManagementSurfaceLateSourceRefusalTest`: 재검사 시점 503, 기동 완료 뒤 200). 그 503 이
 * 곧 **connector 가 이미 bind 되어 있다**는 증거이기도 하다 — `onRefresh()` 의 lifecycle 단계가 이
 * event 보다 앞이라 관리 포트는 이 검사보다 먼저 열린다. 그 창(밀리초)에 관한 알려진 제한과,
 * 지금 그 창에 닿는 유일한 경로를 조기 거부가 막고 있다는 사실은 `checklist.md` 제한 18 이 든다.
 *
 * 여기서 던진 예외는 `refresh()` 안에서 `SpringApplication.run` 의 catch 로 올라가 context 를 닫고
 * 그대로 다시 던져진다 — 즉 **프로세스가 뜨지 않는다**. 부모 축에서는 `IllegalStateException` 이
 * 그대로 올라오지만 **관리 child 축은 형이 다르다**(code-review r3 LOW-3, 바이트코드 실독):
 * child 의 refresh 는 `ChildManagementContextInitializer.start()` 안에서 끝나고 그 `Throwable` 을
 * `DefaultLifecycleProcessor` 가 `ApplicationContextException` 으로 감싼다. 그래서 child 축을 재는
 * test 를 쓸 때 `shouldThrow<IllegalStateException>` 은 공허해진다.
 *
 * 우선순위를 최상위로 두는 이유는 같은 event 의 다른 listener 가 위반 상태에서 먼저 도는 것을 막기
 * 위해서다. 그 우선순위를 `Ordered` 구현이 아니라 `@Order` 로 주는 이유는 `typeShapeGate` 의
 * 인터페이스 수 래칫이다(구현 인터페이스 상한 1) — `AnnotationAwareOrderComparator` 가 둘을 같게
 * 읽으므로 거동은 같다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManagementSurfaceLateCheck : ApplicationListener<ContextRefreshedEvent> {
    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        refuseManagementSurfaceKeysAfterRefresh(event.applicationContext)
    }
}
