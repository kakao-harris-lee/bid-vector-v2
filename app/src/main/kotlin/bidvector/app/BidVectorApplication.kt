package bidvector.app

import bidvector.adapters.audit.ApiAuditRow
import bidvector.adapters.audit.ApiAuditStore
import bidvector.adapters.evaluation.UuidCorrelationIdFactory
import bidvector.adapters.strategy.SystemClock
import bidvector.app.http.ApiAuditRecord
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
import org.springframework.core.Ordered
import javax.sql.DataSource

/**
 * 조립 근(D-6A1-4, (2b) 「경계로 처리」) — 이 slice가 저장소 최초로 `main()`을 만든다.
 * 이 클래스 자신은 도메인 값을 만들지 않는다 — 환경변수/설정을 읽어 어댑터·필터를
 * 생성자에 꽂을 뿐이다(v2-지침서 §5 「생성자 주입 DI, 전역 상태·service locator 금지」).
 *
 * **D-6A1-21 실측 전제** — `spring.mvc.throw-exception-if-no-handler-found=true` +
 * `spring.web.resources.add-mappings=false`가 없으면 미매핑 경로가 정적 리소스
 * 핸들러(`add-mappings` 기본값 true가 등록하는 전체 경로 매핑)에 먼저 걸려 `sendError()`로
 * 컨테이너의 **별도 ERROR 재디스패치**를 유발한다 — 그 재디스패치는 이 필터 체인
 * 밖이라(`FilterRegistrationBean` 기본 dispatcher가 REQUEST뿐) audit·인증을 모두
 * 우회한다(우회 (1)·(2)가 만나는 자리, D-6A1-21). 이 두 설정으로 미매핑 경로도
 * `NoHandlerFoundException`이 되어 [bidvector.app.http.GlobalErrorHandler]를 지나는
 * **같은 REQUEST 디스패치** 안에서 끝난다 — `RequestAuditFilterDispatchTest`가 이것을
 * 실측으로 확인한다(가정이 아니라 실측, 팀장 지시).
 */
@SpringBootApplication
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
     */
    @Bean
    open fun operatorCredentialFilterRegistration(
        properties: OperatorCredentialProperties,
    ): FilterRegistrationBean<OperatorCredentialFilter> =
        FilterRegistrationBean(OperatorCredentialFilter(properties.value)).apply {
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

fun main(args: Array<String>) {
    SpringApplicationBuilder(BidVectorApplication::class.java)
        .properties(
            // D-6A1-21 — 위 클래스 문서의 실측 전제. 새 파일(application.yml)을 만들지
            // 않는다(scope.md in_scope 파일 목록 밖) — 조립 근이 프로그램적으로 못박는다.
            mapOf(
                "spring.mvc.throw-exception-if-no-handler-found" to "true",
                "spring.web.resources.add-mappings" to "false",
            ),
        ).run(*args)
}
