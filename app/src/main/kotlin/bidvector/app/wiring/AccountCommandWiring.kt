package bidvector.app.wiring

import bidvector.adapters.access.AccountProvisioningTransaction
import bidvector.adapters.access.BcryptPassphraseHasher
import bidvector.adapters.access.JdbcAccountProvisioningTransaction
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.app.access.AccountCommandLog
import bidvector.app.access.AccountCommandMode
import bidvector.app.access.AccountCommandRunner
import bidvector.app.access.AccountCommandTermination
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.strategy.Clock
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.system.exitProcess

/**
 * 계정 명령의 조립(M7/7D 산출물 2) — **`bidvector.account.mode` 가 있을 때만** 이 설정 전체가
 * 올라온다(`CollectionWiring` 의 `mode=once` 관례). 속성이 없으면(평소 기동) 러너도, 초기 값
 * 바인딩도, 소유자 연결도 생기지 않는다.
 *
 * **소유자 연결은 지역 객체다**(`PersistenceWiring.migrateAsOwner` 선례) — 빈으로 노출하지
 * 않고 필드로도 남기지 않으므로, 기동이 끝난 뒤 그 연결을 얻을 경로가 컨텍스트에 없다. 계정
 * 생성·해시 교체는 앱 역할이 권한을 갖지 않는 일이고(V20 의 열 단위 GRANT), 그래서 이 명령만
 * 그 일을 할 수 있다.
 *
 * 러너는 한 프로세스에 하나다(`OneShotRunnerGuard`) — 이 명령을 수집 러너와 함께 켜면 기동이
 * 실패한다(먼저 끝난 러너가 JVM 을 끝내 나머지가 조용히 안 도는 일을 막는다).
 */
@Configuration
@ConditionalOnProperty(prefix = "bidvector.account", name = ["mode"])
@EnableConfigurationProperties(AccountCommandProperties::class)
open class AccountCommandWiring {
    @Bean
    open fun accountProvisioningTransaction(
        persistence: PersistenceProperties,
        clock: Clock,
        resolvedBrowserAccessPolicy: BrowserAccessPolicyData,
    ): AccountProvisioningTransaction =
        JdbcAccountProvisioningTransaction(
            transactions = TransactionBoundary(ownerDataSource(persistence)),
            hasher = BcryptPassphraseHasher(resolvedBrowserAccessPolicy.hashCost),
            clock = clock,
        )

    @Bean
    open fun accountCommandRunner(
        accountProvisioningTransaction: AccountProvisioningTransaction,
        properties: AccountCommandProperties,
        accountCommandTermination: AccountCommandTermination,
    ): AccountCommandRunner {
        // 로거는 러너 레인의 관례대로 **배선이 한 줄 출구를 만들어** 넘긴다(러너는 로거를
        // 모른다 — `RetentionWiring`·`CollectionWiring` 과 같은 형태).
        val logger = LoggerFactory.getLogger(AccountCommandRunner::class.java)
        return AccountCommandRunner(
            transaction = accountProvisioningTransaction,
            mode = properties.mode,
            login = LoginName(properties.login),
            initial = ClearPassphrase.wrap(properties.initialValue),
            log = AccountCommandLog { line -> logger.info(line) },
            termination = accountCommandTermination,
        )
    }

    @Bean
    open fun accountCommandTermination(context: ConfigurableApplicationContext): AccountCommandTermination =
        AccountCommandTermination { exitCode ->
            exitProcess(SpringApplication.exit(context, ExitCodeGenerator { exitCode }))
        }

    /**
     * 비풀링 소유자 연결 — `PersistenceWiring` 의 migration 연결과 같은 모양이고 같은 이유로
     * 지역이다. 이름표를 달아 운영자가 `pg_stat_activity` 에서 이 명령의 세션을 가를 수 있게 한다.
     */
    private fun ownerDataSource(persistence: PersistenceProperties): PGSimpleDataSource =
        PGSimpleDataSource().apply {
            setUrl(persistence.jdbcUrl)
            user = persistence.username
            password = persistence.credential
            applicationName = OWNER_APPLICATION_NAME
        }

    private companion object {
        const val OWNER_APPLICATION_NAME = "bidvector-account-command"
    }
}

/**
 * 명령의 설정 키 — **기본값이 없다**(`mode`·`login`·`initialValue` 셋 다). 누락이면 Spring 의
 * 바인딩이 기동을 실패시킨다(`OperatorCredentialProperties`·`PersistenceProperties` 와 같은
 * 규율: 지어내지 않고 fail-fast).
 *
 * **빈 값도 누락과 같다**(verifier r1 F3). 앞 판은 「누락이면 실패한다」고만 적었고 빈 문자열은
 * **그대로 바인딩됐다** — `--…initial-value="$INITIAL"` 에서 변수를 빠뜨린 실행이 **빈 비밀
 * 문구로 계정을 만들고** `result=created`·exit 0 을 냈다. HTTP 는 빈 값을 400 으로 막으므로
 * 우회가 아니라 **잠금**(운영자가 다시는 로그인하지 못한다)이고, 계정은 이미 있으므로 다시
 * 만들 수도 없다. 아래 두 `require` 가 그 실행을 **기동 실패**(비-0 종료)로 떨어뜨린다.
 *
 * 실패 문면에 **값을 싣지 않는다** — 길이도 적지 않는다(짧은 값의 길이는 그 자체가 신호다).
 *
 * **`data class` 가 아니다** — 합성 `toString()` 이 [initialValue] 원문을 그대로 낸다
 * (바인딩 실패 분석기·actuator 환경 노출이 그 문면을 로그로 낸다, D-6A2a-13 ①).
 * 이름에 스캔 어휘를 쓰지 않는다(D-6A1-19 가 모든 설정 키로 일반화) — `initial-value`.
 */
@ConfigurationProperties(prefix = "bidvector.account")
class AccountCommandProperties(
    val mode: AccountCommandMode,
    val login: String,
    val initialValue: String,
) {
    init {
        require(login.isNotBlank()) { "bidvector.account.login 이 비어 있다" }
        require(initialValue.isNotBlank()) { "bidvector.account.initial-value 가 비어 있다" }
    }
}
