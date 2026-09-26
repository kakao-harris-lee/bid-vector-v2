package bidvector.app

import bidvector.adapters.audit.ApiAuditRow
import bidvector.adapters.audit.ApiAuditStore
import bidvector.adapters.evaluation.UuidCorrelationIdFactory
import bidvector.adapters.strategy.SystemClock
import bidvector.app.http.ApiAuditRecord
import bidvector.app.http.OperatorCredential
import bidvector.app.http.OperatorCredentialFilter
import bidvector.app.http.RequestAuditFilter
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.strategy.Clock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.core.Ordered
import javax.sql.DataSource

/**
 * 조립 근(D-6A1-4, (2b) 「경계로 처리」) — 이 slice가 저장소 최초로 `main()`을 만든다.
 * 이 클래스 자신은 도메인 값을 만들지 않는다 — 환경변수/설정을 읽어 어댑터·필터를
 * 생성자에 꽂을 뿐이다(v2-지침서 §5 「생성자 주입 DI, 전역 상태·service locator 금지」).
 *
 * **D-6A1-21 실측 전제 — 정정: 둘 다 필요한 것이 아니라 `add-mappings=false` 하나가
 * 지탱한다.** `spring.web.resources.add-mappings=false`가 없으면 미매핑 경로가 정적
 * 리소스 핸들러(`add-mappings` 기본값 true가 등록하는 전체 경로 매핑)에 먼저 걸려
 * `sendError()`로 컨테이너의 **별도 ERROR 재디스패치**를 유발한다 — 그 재디스패치는 이
 * 필터 체인 밖이라(`FilterRegistrationBean` 기본 dispatcher가 REQUEST뿐) audit·인증을
 * 모두 우회한다(우회 (1)·(2)가 만나는 자리, D-6A1-21). `spring.mvc.throw-exception-if-
 * no-handler-found=true`는 정적 리소스 핸들러가 비활성화된 뒤 남는 미매핑 경로를
 * `NoHandlerFoundException`으로 만들어 [bidvector.app.http.GlobalErrorHandler]를
 * 지나게 한다 — **실측(팀장 지시): 이 값만 빼면 여전히 GREEN이다**(`add-mappings=false`
 * 하나로 이미 같은 REQUEST 디스패치 안에서 끝난다). 그래도 두 값을 함께 둔다 — 정적
 * 리소스 핸들러 재활성 같은 미래 변경에서 `NoHandlerFoundException`으로의 변환을 명시
 * 보장으로 남겨 두는 편이 암묵적 부작용에 기대는 것보다 낫다(비용은 설정 값 한 줄).
 * `RequestAuditFilterTest`(app/src/test)의 D-6A1-21 test와 `ProductionAssemblyAuthAuditTest`가
 * 실제 디스패치 형태를 실측으로 확인한다(가정이 아니라 실측).
 *
 * **D-6A1-27 — 중첩 `@SpringBootApplication`을 스캔에서 뺀다.** 기본 컴포넌트 스캔은
 * `bidvector.app`과 그 하위 전부를 본다 — `bidvector.app.http.HttpTestApplication`
 * (test 전용, 하위 패키지)도 그 범위 안이다. production 조립을 직접 부팅하는
 * `ProductionAssemblyAuthAuditTest`가 이 클래스를 부팅하면 두 `@SpringBootApplication`이
 * 같은 컨텍스트에서 겹쳐 `strategyRepository` 등 bean 이름이 충돌한다(실측 —
 * `BeanDefinitionOverrideException`). 배포되는 jar에는 test 클래스가 없어 이 필터는
 * production 런타임에서 공집합이다 — 순수하게 test 부팅을 여는 변경이다.
 */
@SpringBootApplication
@ComponentScan(
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [SpringBootApplication::class]),
    ],
)
@EnableConfigurationProperties(OperatorCredentialProperties::class)
open class BidVectorApplication {
    @Bean
    open fun clock(): Clock = SystemClock()

    @Bean
    open fun correlationIdFactory(): CorrelationIdFactory = UuidCorrelationIdFactory()

    @Bean
    open fun apiAuditStore(dataSource: DataSource): ApiAuditStore = ApiAuditStore(dataSource)

    /**
     * `RequestAuditFilter`가 부르는 sink — [ApiAuditRecord](app/http, HTTP 어휘) →
     * [ApiAuditRow](adapters/audit, 저장 어휘) 변환은 이 조립 지점 하나가 진다(두 타입을
     * 합치지 않는다 — app이 adapters의 저장 행 형태를 몰라도 되게 한다).
     *
     * **D-6A1-43** — 환경변수를 읽는 이 자리에서 곧바로 [OperatorCredential.of]로 감싼다.
     * 그 뒤로는 [OperatorCredentialFilter]도, 이 조립 메서드도 raw 자격증명 문자열을
     * 다시 다루지 않는다(`properties.value`는 이 한 줄에서만 참조된다).
     */
    @Bean
    open fun operatorCredentialFilterRegistration(
        properties: OperatorCredentialProperties,
    ): FilterRegistrationBean<OperatorCredentialFilter> =
        FilterRegistrationBean(OperatorCredentialFilter(OperatorCredential.of(properties.value))).apply {
            urlPatterns = listOf("/*")
            order = Ordered.HIGHEST_PRECEDENCE + 1
        }

    @Bean
    open fun requestAuditFilterRegistration(
        clock: Clock,
        correlationIdFactory: CorrelationIdFactory,
        apiAuditStore: ApiAuditStore,
    ): FilterRegistrationBean<RequestAuditFilter> {
        val filter =
            RequestAuditFilter(clock, correlationIdFactory) { record: ApiAuditRecord ->
                apiAuditStore.append(
                    ApiAuditRow(
                        occurredAt = record.occurredAt,
                        subject = record.subject,
                        method = record.method,
                        path = record.path,
                        statusCode = record.status,
                        durationMillis = record.durationMillis,
                        correlationId = record.correlationId,
                    ),
                )
            }
        return FilterRegistrationBean(filter).apply {
            urlPatterns = listOf("/*")
            // 우회 (1)·(2)가 만나는 자리 — 인증 필터보다 바깥(먼저 진입/마지막에 기록).
            order = Ordered.HIGHEST_PRECEDENCE
        }
    }
}

/**
 * 단일 운영자 자격증명 설정 키(D-6A1-9, 「인증 관련」 이름 규율) — 값은 환경변수 주입,
 * 기본값 없음.
 *
 * **verifier r4 실측 — `data class`였을 때 `toString()`이 원문을 그대로 냈다.** Spring은
 * 기동 실패·바인딩 오류·actuator 환경 노출 등에서 `@ConfigurationProperties` 객체를
 * 문자열화해 로그·응답에 낼 수 있다 — 컴파일러가 합성하는 `data class`의 `toString()`이
 * 그 경로로 자격증명 원문을 흘린다(D-6A1-43의 (2b) 값 획득 축 전수에서 이 타입이 누락돼
 * 있었다). 그래서 **`data class`가 아니다** — `equals`/`hashCode`/`copy`/구조 분해도
 * 같이 사라지지만 이 타입은 [OperatorCredentialFilter]에 생성 직후 한 번 읽히고 버려질
 * 뿐이라 필요하지 않다. 재정의하지 않은 `Any.toString()`(클래스명@해시코드)을 그대로
 * 쓴다 — [OperatorCredential]과 같은 근거.
 */
@ConfigurationProperties(prefix = "operator.credential")
class OperatorCredentialProperties(
    val value: String,
)

/**
 * D-6A1-21 — 위 클래스 문서의 실측 전제. 새 파일(application.yml)을 만들지 않는다
 * (scope.md in_scope 파일 목록 밖) — 조립 근이 프로그램적으로 못박는다.
 *
 * **D-6A1-27 시정 — `main()`과 production 조립 boot test(`ProductionAssemblyAuthAuditTest`)가
 * 같은 값을 참조한다.** 이전 판은 이 두 속성을 test 파일에 `const val`로 중복 선언하고
 * (`HttpTestSupport.PROP_*`) 「드리프트가 나면 test가 곧바로 실패한다」고 적었는데, 그
 * test는 `main()`을 부르지 않고 자기 사본을 그대로 써서 실측으로 거짓임이 드러났다
 * (verifier — 이 둘을 제거해도 기존 http test 넷은 exit 0). 이 값을 `main()`과 production
 * boot test가 **같은 참조**로 공유하면, 여기서 지우는 순간 두 자리 모두 같이 비어 실제
 * 런타임 동작(미매핑 경로의 디스패치 형태)이 갈라지고 그 test가 붉어진다.
 */
val PRODUCTION_DISPATCH_PROPERTIES: Map<String, String> =
    mapOf(
        "spring.mvc.throw-exception-if-no-handler-found" to "true",
        "spring.web.resources.add-mappings" to "false",
    )

/**
 * 관리 포트 기본값(D-6A2a-4) — 이 값 하나만 **환경이 덮을 수 있다**(배치가 포트를 고른다).
 * 기본값을 두는 이유는 기존 `java -jar` 경로가 새 환경변수 없이 그대로 뜨게 하는 것이고,
 * 그 값이 API 포트 기본값(8080)과 달라야 [lockManagementSurface] 의 분리 판정이 선다.
 * 나머지 관리 표면 값은 [MANAGEMENT_SURFACE_LOCK] 이 환경보다 높은 자리에서 못박는다.
 */
val PRODUCTION_MANAGEMENT_PROPERTIES: Map<String, String> =
    mapOf("management.server.port" to "8081")

/**
 * 출하 조립의 **유일한** 조립 호출(D-6A1-27 의 교훈 — test 가 자기 사본으로 조립하면
 * production 배선을 지워도 초록이다). `main()` 과 production boot test 가 이 하나를 공유하므로,
 * 여기서 [ManagementSurfaceLock]·[ManagementSurfaceLateCheck] 나 두 property 묶음을 지우면 두
 * 자리가 함께 무너진다.
 *
 * 관리 표면 잠금이 **두 자리**인 근거(D-6A2a-14): 초기화자는 그 시점에 열거 가능한 소스만 보고,
 * 서블릿 컨텍스트 init-param 처럼 refresh 중에 실체로 채워지는 소스는 그 모집단 밖이다. 같은
 * 술어를 refresh 뒤에 한 번 더 도는 listener 가 주 잠금이고 초기화자는 빠른 실패다.
 */
fun productionApplication(): SpringApplicationBuilder =
    SpringApplicationBuilder(BidVectorApplication::class.java)
        .properties(PRODUCTION_DISPATCH_PROPERTIES + PRODUCTION_MANAGEMENT_PROPERTIES)
        .initializers(ManagementSurfaceLock())
        .listeners(ManagementSurfaceLateCheck())

fun main(args: Array<String>) {
    productionApplication().run(*args)
}
