package bidvector.app.wiring

import bidvector.adapters.persistence.ReleaseIdentifier
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.StandardEnvironment

/**
 * M9/OPS-10 — 「**값이 없으면 기동이 실패한다**」를 재는 음성 대조(verifier M-2 · cr OQ-2).
 *
 * 다른 배선 test 들은 `ReleaseIdentifier` 빈을 **직접 꽂으므로** 이 경로를 지나지 않고, 출하 조립
 * test 들은 속성을 전부 **준다** — 즉 「기본값 없음」이라는 약속을 재는 자리가 어디에도 없었다.
 * 기본값이 되살아나면(옛 `unversioned`) 이 test 가 먼저 붉어진다.
 *
 * **실패의 자리를 특정하지 않는다** — Boot 의 값 객체 바인딩과 `ReleaseIdentifier.require` 중
 * 어느 쪽이 먼저 끊는지는 Boot 판에 따라 다를 수 있고, 약속은 「뜨지 않는다」이므로 그 사실만
 * 단언한다(메시지는 키 이름을 담는지만 본다).
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
            TestPropertyValues.of("bidvector.release.sha=wiring-positive-release").applyTo(context)
            context.register(ReleaseWiring::class.java)
            context.refresh()

            context.getBean(ReleaseIdentifier::class.java) shouldBe ReleaseIdentifier("wiring-positive-release")
        }
    }

    /**
     * **출하 설정 원천에는 기본값이 없다**(verifier M-3). 위 셋은 `AnnotationConfigApplicationContext`
     * 라 Boot 의 **ConfigData**(출하 jar 가 실제로 읽는 설정 원천 — classpath 의
     * `application.properties`·`application.yaml`, 프로필 파일, `spring.config.import`)를 지나지
     * 않는다. 그래서 `app/src/main/resources/application.properties` 에 옛 기본값을 한 줄 적으면
     * **위 셋은 그대로 초록인 채** 약속이 깨진다(변이 `r2_resource_default` 실측).
     *
     * **호스트 환경변수는 뺀다.** 이 test 가 재는 것은 「저장소가 싣는 설정 원천」이고, 운영
     * 환경이 값을 주는 것은 정상이다(compose·cron 래퍼가 준다). 환경을 남겨 두면
     * `BIDVECTOR_RELEASE_SHA` 가 설정된 호스트에서 이 음성 대조가 조용히 공허해진다.
     */
    @Test
    fun `출하 기동 경로도 값이 없으면 뜨지 않는다 — ConfigData 를 지난다`() {
        val failure = runCatching { shippingBoot().use { } }.exceptionOrNull()

        failure shouldNotBe null
        failureText(failure) shouldContain "bidvector.release"
    }

    @Test
    fun `출하 기동 경로에 값을 주면 그 값이 빈으로 선다 — 양성 대조`() {
        shippingBoot("--bidvector.release.sha=shipping-positive-release").use { context ->
            context.getBean(ReleaseIdentifier::class.java) shouldBe ReleaseIdentifier("shipping-positive-release")
        }
    }

    /** 출하와 같은 기동 경로 — `SpringApplication` + classpath 설정 원천. 웹 표면은 없다. */
    private fun shippingBoot(vararg args: String) =
        SpringApplication(ReleaseWiring::class.java)
            .apply {
                setWebApplicationType(WebApplicationType.NONE)
                setBannerMode(Banner.Mode.OFF)
                setRegisterShutdownHook(false)
                setEnvironment(
                    StandardEnvironment().apply {
                        propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
                    },
                )
            }.run(*args)

    private fun bootFailure(vararg properties: String): Throwable? {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(ReleaseWiring::class.java)
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        runCatching { context.close() }
        return failure
    }

    private fun failureText(failure: Throwable?): String =
        generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
}
