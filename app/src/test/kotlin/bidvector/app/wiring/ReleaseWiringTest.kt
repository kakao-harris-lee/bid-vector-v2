package bidvector.app.wiring

import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.app.productionApplication
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.Banner
import org.springframework.boot.context.event.ApplicationContextInitializedEvent
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment

/**
 * M9/OPS-10 — 「**값이 없으면 기동이 실패한다**」를 재는 음성 대조(verifier M-2 · cr OQ-2).
 *
 * 다른 배선 test 들은 `ReleaseIdentifier` 빈을 **직접 꽂으므로** 이 경로를 지나지 않고, 출하 조립
 * test 들은 속성을 전부 **준다** — 즉 「기본값 없음」이라는 약속을 재는 자리가 어디에도 없었다.
 * 기본값이 되살아나면(옛 `unversioned`) 이 test 가 먼저 붉어진다.
 *
 * **약속은 두 쪽이고 각각 자기 자리에서 잰다.**
 * - ① 바인딩: 값이 없으면 조립이 **뜨지 않는다**(아래 `bootFailure` 셋).
 * - ② 설정 원천: 출하 기동점이 싣는 **어느 원천에도** 그 키가 없다(아래 `productionEnvironment` 둘).
 *   ① 만 재면 어딘가 기본값을 되살려 놓고도 초록이다 — 바인딩은 값을 받으니 성공한다.
 *
 * **실패의 자리를 특정하지 않는다**(①) — Boot 의 값 객체 바인딩과 `ReleaseIdentifier.require` 중
 * 어느 쪽이 먼저 끊는지는 Boot 판에 따라 다를 수 있고, 약속은 「뜨지 않는다」이므로 그 사실만
 * 단언한다(메시지는 키 이름을 담는지만 본다).
 *
 * **호스트 환경변수는 어느 갈래에서도 뺀다.** 재는 것은 「저장소가 싣는 설정 원천」이고, 운영
 * 환경이 값을 주는 것은 정상이다(compose·cron 래퍼가 준다). 환경을 남겨 두면 운영 env 를 source
 * 한 셸에서 — `BIDVECTOR_RELEASE_SHA` 가 있는 자리에서 — 이 test 들이 **거짓 RED** 로 시끄럽게
 * 실패한다(실측: v L-a). 제거가 하중을 지는 줄이라는 것은 반대 방향으로도 실측됐다(제거를
 * 무력화하고 그 변수를 두면 ② 가 RED 다, v `r4_env_kept`).
 */
class ReleaseWiringTest {
    @Test
    fun `릴리스 식별자 속성이 없으면 조립이 기동하지 않는다`() {
        val failure = bootFailure()

        failure shouldNotBe null
        failureText(failure) shouldContain "bidvector.release"
    }

    @Test
    fun `빈 문자열도 거부된다 — 값이 있는 척하는 설정이 통과하지 않는다`() {
        val failure = bootFailure("bidvector.release.sha=")

        failure shouldNotBe null
    }

    @Test
    fun `값을 주면 그 값이 빈으로 선다 — 양성 대조`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment = hostEnvironmentRemoved()
            TestPropertyValues.of("bidvector.release.sha=wiring-positive-release").applyTo(context)
            context.register(ReleaseWiring::class.java)
            context.refresh()

            context.getBean(ReleaseIdentifier::class.java) shouldBe ReleaseIdentifier("wiring-positive-release")
        }
    }

    /**
     * **출하 기동점이 싣는 설정 원천에는 릴리스 기본값이 없다**(v M-3 · v N-1).
     *
     * 그 원천은 한 층이 아니다 — `productionApplication()` 의 default properties
     * (`PRODUCTION_DISPATCH_PROPERTIES`·`PRODUCTION_MANAGEMENT_PROPERTIES`) · Boot 의 **ConfigData**
     * (classpath 의 `application.properties`·`application.yaml`, 프로필 파일, `spring.config.import`)
     * · 시스템 속성 · 명령행이 **한 환경에** 쌓인다. 그래서 자리 하나를 겨누는 단언이 아니라
     * **「이 환경에 그 키가 있는가」** 를 묻는다.
     *
     * **`SpringApplication` 을 자기 사본으로 만들지 않는다**(v N-1 — 앞 판이 그랬다). 사본은 출하
     * 기동점이 거는 default properties 층을 지나지 않아, 그 맵에 기본값을 한 줄 넣어도 app 전건이
     * 초록이었다. `productionApplication()` KDoc 의 D-6A1-27 교훈(「test 가 자기 사본으로 조립하면
     * production 배선을 지워도 초록」)이 이 축에서 반복된 자리다.
     */
    @Test
    fun `출하 기동점의 어느 설정 원천에도 릴리스 기본값이 없다`() {
        productionEnvironment().containsProperty(RELEASE_SHA_KEY) shouldBe false
    }

    /**
     * 같은 경로의 **양성 대조** — 운영이 실제로 값을 주는 방식(명령행)이 이 환경에 올라오고, 위
     * 단언이 그것을 본다. 이 대조가 없으면 「언제나 없다고 답하는」 술어로 바뀌어도 초록이다.
     */
    @Test
    fun `출하 기동점에 값을 주면 그 환경에 보인다 — 양성 대조`() {
        val environment = productionEnvironment("--$RELEASE_SHA_KEY=production-path-positive")

        environment.getProperty(RELEASE_SHA_KEY) shouldBe "production-path-positive"
    }

    /**
     * 출하 기동점이 **실제로 준비한** 환경을 refresh 전에 집는다 — 컨텍스트를 띄우지 않으므로 DB·
     * 외부 자격 없이 돌고, 환경 층은 production `main()` 과 **같은 호출**이 쌓은 그대로다.
     *
     * 멈추는 자리는 `ApplicationContextInitializedEvent` 다 — **조립 초기화자가 다 돈 뒤**이고
     * bean 정의 적재·refresh 는 그 뒤다. 한 층 앞(`ApplicationEnvironmentPreparedEvent`)에서 멈추면
     * `productionApplication()` 이 거는 `ApplicationContextInitializer` 가 환경에 property source 를
     * 더하는 형태가 모집단 **밖**으로 남는다(v L-1). 그 층은 `org.springframework.core.env` 안이라
     * 패키지 게이트도 잡지 않는다.
     *
     * 중단 표지를 **단언**하므로, 다른 사유로 먼저 죽으면 이 test 는 조용히 통과하지 않는다.
     */
    private fun productionEnvironment(vararg args: String): ConfigurableEnvironment {
        val stop = StopWhenContextInitialized()
        val failure =
            runCatching {
                productionApplication()
                    .environment(hostEnvironmentRemoved())
                    .listeners(stop)
                    .registerShutdownHook(false)
                    .bannerMode(Banner.Mode.OFF)
                    .run(*args)
            }.exceptionOrNull()

        generateSequence(failure) { it.cause }.any { it is ContextInitialized } shouldBe true
        return checkNotNull(stop.prepared) { "환경을 집지 못했다" }
    }

    private fun bootFailure(vararg properties: String): Throwable? {
        val context = AnnotationConfigApplicationContext()
        context.environment = hostEnvironmentRemoved()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(ReleaseWiring::class.java)
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        runCatching { context.close() }
        return failure
    }

    private fun hostEnvironmentRemoved(): ConfigurableEnvironment =
        StandardEnvironment().apply {
            propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        }

    private fun failureText(failure: Throwable?): String =
        generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
}

private const val RELEASE_SHA_KEY = "bidvector.release.sha"

/** 중단 표지 — 「환경까지는 갔다」를 단언할 수 있게 사유를 타입으로 든다. */
private class ContextInitialized : RuntimeException("초기화자까지 돈 뒤 중단(test)")

/**
 * **타입 인자를 명시한 이름 있는 listener** 다(람다가 아니다) — 람다는 Spring 이 선언 event 타입을
 * 풀 수 없어 모든 event 를 받게 되고, 그러면 첫 event 에서 캐스트가 깨진다.
 * `ManagementSurfaceLateCheck` 가 `ContextRefreshedEvent` 로 같은 모양을 쓴다.
 */
private class StopWhenContextInitialized : ApplicationListener<ApplicationContextInitializedEvent> {
    var prepared: ConfigurableEnvironment? = null

    override fun onApplicationEvent(event: ApplicationContextInitializedEvent) {
        prepared = event.applicationContext.environment
        throw ContextInitialized()
    }
}
