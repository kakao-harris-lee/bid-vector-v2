package bidvector.app.wiring

import bidvector.app.collection.CollectionRunner
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.time.Instant
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * D-6F8-3(M6/6F-8) — 수집 러너 배선의 켜짐 조건과 기동 실패. 러너는 `bidvector.collection.mode=once` 일 때만
 * 빈이고(기본 꺼짐), 켜졌을 때의 설정 오류는 전부 기동 실패다. 꺼져 있으면 서비스 키가 없어도 뜬다.
 */
class CollectionWiringTest {
    private val secretKey = "SECRET-SERVICE-KEY-VALUE"
    private val fixedNow = Instant.parse("2026-09-24T03:00:00Z")

    /** 부팅 시도 결과 — 실패했으면 [failure] 가 있고 컨텍스트는 이미 닫혔다. */
    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(
        vararg properties: String,
        now: Instant = fixedNow,
        customizeEnvironment: (ConfigurableEnvironment) -> Unit = {},
    ): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        customizeEnvironment(context.environment)
        context.register(CollectionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { now } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        return Booted(context, failure)
    }

    private fun <T> withBoot(
        vararg properties: String,
        now: Instant = fixedNow,
        customizeEnvironment: (ConfigurableEnvironment) -> Unit = {},
        block: (Booted) -> T,
    ): T {
        val booted = boot(*properties, now = now, customizeEnvironment = customizeEnvironment)
        return try {
            block(booted)
        } finally {
            booted.context.close()
        }
    }

    private fun validProperties(
        from: String = "2026-08-25",
        to: String = "2026-09-24",
        categories: String = "construction,service",
    ) = arrayOf(
        "bidvector.collection.mode=once",
        "bidvector.collection.from=$from",
        "bidvector.collection.to=$to",
        "bidvector.collection.categories=$categories",
        "bidvector.koneps.service-key=$secretKey",
    )

    private fun failureText(failure: Throwable?): String =
        generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }

    @Test
    fun `속성이 없으면 러너도 수집 빈도 없다 — 키·범위·업종 속성이 하나도 없어도 컨텍스트가 뜬다`() {
        withBoot { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeansOfType(ApplicationRunner::class.java)
                .values
                .shouldBeEmpty()
            booted.context
                .getBeanNamesForType(CollectionRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode 가 once 가 아니면 다른 속성이 다 있어도 러너는 없다`() {
        listOf("twice", "false", "", "run").forEach { mode ->
            withBoot(*validProperties(), "bidvector.collection.mode=$mode") { booted ->
                booted.failure shouldBe null
                booted.context
                    .getBeansOfType(ApplicationRunner::class.java)
                    .values
                    .shouldBeEmpty()
            }
        }
    }

    @Test
    fun `once 와 유효한 설정이면 러너가 정확히 하나 있고 업종 소스가 설정 순서대로 조립된다`() {
        withBoot(*validProperties()) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeansOfType(ApplicationRunner::class.java)
                .values
                .map { it::class } shouldContainExactly
                listOf(CollectionRunner::class)
            booted.context
                .getBean(CollectionSources::class.java)
                .all
                .map { it.name } shouldContainExactly
                listOf("construction", "service").map { requireNotNull(CollectionSourceName.of(it)) }
        }
    }

    @Test
    fun `서비스 키는 환경변수 형태로도 바인딩된다 — 속성 파일 없이 환경변수만으로 켠다`() {
        val withoutKey = validProperties().filterNot { it.startsWith("bidvector.koneps.service-key") }.toTypedArray()
        val environment =
            SystemEnvironmentPropertySource(
                "test-systemEnvironment",
                mapOf("BIDVECTOR_KONEPS_SERVICEKEY" to secretKey),
            )

        withBoot(*withoutKey, customizeEnvironment = { it.propertySources.addFirst(environment) }) { booted ->
            withClue(failureText(booted.failure)) { booted.failure shouldBe null }
            booted.context.getBeansOfType(ApplicationRunner::class.java).size shouldBe 1
        }
    }

    @Test
    fun `서비스 키 설정 객체의 문자열 표현은 원문을 내지 않는다 — data class 로 바뀌면 잡힌다`() {
        KonepsCredentialProperties(secretKey).toString() shouldNotContain secretKey
    }

    @Test
    fun `러너가 켜졌는데 서비스 키가 없거나 비어 있으면 기동 실패다`() {
        val withoutKey = validProperties().filterNot { it.startsWith("bidvector.koneps.service-key") }.toTypedArray()

        withBoot(*withoutKey) { it.failure shouldNotBe null }
        withBoot(*withoutKey, "bidvector.koneps.service-key=") { it.failure shouldNotBe null }
    }

    @Test
    fun `범위 설정 오류는 조용히 자르지 않고 기동 실패다 — 상한 초과·미래·역전`() {
        listOf(
            validProperties(from = "2026-08-23", to = "2026-09-24"),
            validProperties(from = "2026-09-24", to = "2026-09-25"),
            validProperties(from = "2026-09-24", to = "2026-09-23"),
        ).forEach { properties ->
            withBoot(*properties) { booted ->
                booted.failure shouldNotBe null
                failureText(booted.failure) shouldNotContain secretKey
            }
        }
    }

    @Test
    fun `상한 정확히(31일)는 뜬다 — 경계`() {
        withBoot(*validProperties(from = "2026-08-24")) { it.failure shouldBe null }
    }

    @Test
    fun `오늘은 KST 달력일이다 — UTC 날짜와 갈리는 시각에도 KST 자정이 지나면 to=오늘 이 서고 그 전엔 미래다`() {
        val kstMidnight = Instant.parse("2026-09-24T15:00:00Z")
        val properties = validProperties(from = "2026-08-26", to = "2026-09-25")

        withBoot(*properties, now = kstMidnight) { it.failure shouldBe null }
        withBoot(*properties, now = kstMidnight.minusSeconds(1)) { it.failure shouldNotBe null }
    }

    @Test
    fun `같은 업종을 두 번 적으면 조립 시점에 기동 실패다 — 실행 도중에 죽지 않는다`() {
        withBoot(*validProperties(categories = "construction,construction")) { booted ->
            booted.failure shouldNotBe null
            failureText(booted.failure) shouldNotContain secretKey
        }
    }

    @Test
    fun `KONEPS 기본 URL 이 평문 http 로 외부 호스트를 가리키면 기동 실패다 — 서비스 키가 평문으로 나가지 않는다`() {
        listOf("http://apis.data.go.kr/1230000", "ftp://apis.data.go.kr/x", "apis.data.go.kr/x").forEach { baseUrl ->
            withBoot(*validProperties(), "bidvector.koneps.base-url=$baseUrl") { booted ->
                booted.failure shouldNotBe null
                failureText(booted.failure) shouldNotContain secretKey
            }
        }
    }

    @Test
    fun `KONEPS 기본 URL 은 https 이거나 loopback 호스트(mock 서버)면 선다`() {
        listOf("https://apis.data.go.kr/1230000", "http://127.0.0.1:8080/x", "http://localhost:9/x", "http://[::1]:9/x")
            .forEach { baseUrl ->
                withBoot(*validProperties(), "bidvector.koneps.base-url=$baseUrl") { it.failure shouldBe null }
            }
    }

    @Test
    fun `표에 없는 업종·빈 업종 목록·형식이 틀린 업종은 기동 실패다`() {
        listOf("goods", "", "Construction", "construction,").forEach { categories ->
            withBoot(*validProperties(categories = categories)) { booted ->
                booted.failure shouldNotBe null
                failureText(booted.failure) shouldNotContain secretKey
            }
        }
    }

    @Test
    fun `기본 오퍼레이션 표는 환경 속성으로 덮어쓸 수 있다 — 표에 새 업종을 더하면 그 업종이 선다`() {
        withBoot(
            *validProperties(categories = "goods"),
            "bidvector.koneps.operations.goods=getBidPblancListInfoThng",
        ) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBean(CollectionSources::class.java)
                .all
                .map { it.name.value } shouldContainExactly
                listOf("goods")
        }
    }
}
