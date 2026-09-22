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
 */
@ConfigurationProperties(prefix = "operator.credential")
data class OperatorCredentialProperties(
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

fun main(args: Array<String>) {
    SpringApplicationBuilder(BidVectorApplication::class.java)
        .properties(PRODUCTION_DISPATCH_PROPERTIES)
        .run(*args)
}
