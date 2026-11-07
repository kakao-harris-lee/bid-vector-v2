package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsOpenApiNoticeSource
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.CollectionRunner
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.MockKonepsHttp
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CallSpend
import bidvector.procurement.CollectionCallBudget
import bidvector.procurement.CollectionReferenceDate
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeViolation
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.beans.factory.NoUniqueBeanDefinitionException
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.function.Supplier
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

/**
 * D-6F8-3 — 수집 러너 배선의 켜짐 조건과 기동 실패. 러너는 `bidvector.collection.mode=once` 일 때만
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
        // D-6G-47 H-1 — 이 갈래도 승인 상한 아래다. 기본값이 없으므로 켜려면 넷을 대야 한다.
        "bidvector.collection.calls-per-day=1000",
        "bidvector.collection.calls-total=1000",
        "bidvector.collection.run-state-dir=$NOTICE_RUN_STATE",
        "bidvector.koneps.service-key=$secretKey",
    )

    private fun failureText(failure: Throwable?): String =
        generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }

    /**
     * **D-6G2c-6 (cr r5 L-8) — 두 갈래는 따로 기동한다.** 둘이 각자 `RunStateDirectory`·
     * `CallBudgetLedger`·`CollectionRange` 빈을 등록하므로 한 프로세스에 함께 켜면 주입이 모호해져
     * 컨텍스트가 뜨지 않는다. 그 fail-closed 가 **선언이 아니라 거동**임을 여기서 잰다 — `@Qualifier`
     * 로 공존시키는 편집은 이 test 를 붉힌다.
     *
     * 공존시키지 않는 이유: 한 프로세스에 두 갈래를 두는 요구가 없고, 두면 **한 원장을 두 러너가
     * 번갈아 쓰는** 판이 생긴다(한 인스턴스는 한 스레드라는 전제가 프로세스 안에서 깨진다).
     */
    @Test
    fun `두 수집 모드를 함께 켜면 기동하지 않는다 — 한 프로세스에 두 갈래를 두지 않는다`() {
        val openingRunState = Files.createTempDirectory("6g2c-both-modes-opening")
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues
            .of(
                *validProperties(),
                "bidvector.opening-collection.mode=once",
                "bidvector.opening-collection.from=2026-09-20",
                "bidvector.opening-collection.to=2026-09-22",
                "bidvector.opening-collection.categories=construction,service",
                "bidvector.opening-collection.sampling-seed=6g2c-both-modes",
                "bidvector.opening-collection.sample-size=2",
                "bidvector.opening-collection.calls-per-day=100",
                "bidvector.opening-collection.calls-total=1000",
                "bidvector.opening-collection.run-state-dir=$openingRunState",
            ).applyTo(context)
        context.register(CollectionWiring::class.java, OpeningCollectionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { fixedNow } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })

        val failure = runCatching { context.refresh() }.exceptionOrNull()

        try {
            failure shouldNotBe null
            // 뜨지 못한 사유가 **주입의 모호함**임을 타입으로 본다. 어느 공유 타입에서 먼저 걸리는지는
            // 빈 생성 순서가 정하므로 이름 하나로 고정하지 않고, 걸린 것이 **두 갈래가 공유하는 타입
            // 뿐임**을 단언한다 — 그래야 「다른 사유로 뜨지 못했다」가 이 test 를 통과하지 못한다.
            val ambiguous =
                generateSequence(failure) { it.cause }
                    .filterIsInstance<NoUniqueBeanDefinitionException>()
                    .mapNotNull { it.beanType?.name }
                    .toSet()

            ambiguous.shouldNotBeEmpty()
            (ambiguous - SHARED_BETWEEN_BRANCHES) shouldBe emptySet()
        } finally {
            context.close()
        }
    }

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

    /**
     * **D-6G2e-1 — 상한의 뜻은 갈래마다 다르고, 이 갈래의 값은 31일 그대로다.** 개찰 갈래는 A-1 의
     * 16주 창을 받아야 해서 상한이 더 길지만, 이 갈래의 호출 수는 **창 길이에 비례한다**(조회일마다
     * 쪽을 걷는다). 두 갈래가 정책 인스턴스를 공유하면 개찰 쪽을 넓히는 한 줄이 이 갈래의 호출 폭주
     * 방지(6F-8 D-6F8-3)를 조용히 함께 연다 — 그 한 줄을 이 단언이 막는다.
     */
    @Test
    fun `개찰 갈래의 긴 창은 이 갈래에서 거부된다 — 범위 정책을 공유하지 않는다`() {
        withBoot(*validProperties(from = "2026-05-27")) { booted ->
            booted.failure shouldNotBe null
            failureText(booted.failure) shouldContain CollectionRangeViolation.SPAN_TOO_LONG.name
        }
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
            "bidvector.koneps.operations.goods.path=getBidPblancListInfoThng",
            "bidvector.koneps.operations.goods.division=goods",
        ) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBean(CollectionSources::class.java)
                .all
                .map { it.name.value } shouldContainExactly
                listOf("goods")
        }
    }

    @Test
    fun `기본 오퍼레이션 표는 업종 한 행이 경로와 업무 대분류를 함께 나른다(D-6F9-1) — 공사는 공사 용역은 용역`() {
        withBoot(*validProperties()) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBean(KonepsEndpointProperties::class.java)
                .operations
                .mapValues { it.value.division } shouldBe
                mapOf("construction" to BusinessDivision.CONSTRUCTION, "service" to BusinessDivision.SERVICE)
        }
    }

    /**
     * D-6F9-1 — 설정 표 한 행의 **대분류가 이긴다**. 경로와 대분류를 고의로 어긋나게 주고(공사
     * 경로 + 용역 대분류, 그리고 그 반대) 배선이 조립한 소스가 실제로 낸 관측의 대분류를 본다. 게이트는 「경로에서
     * 대분류를 짓는 코드가 없다」를 구조로 잠그고 이 test 는 그 값이 관측까지 가는 것을 **행동으로** 잠근다 —
     * 기본 표는 경로와 대분류가 일치해서 어느 쪽에서 얻어도 답이 같아 이 축을 재지 못한다(mock loopback, 실호출 0).
     */
    @Test
    fun `설정 행의 경로와 대분류가 어긋나면 설정한 대분류가 이긴다 — 경로에서 짓지 않는다`() {
        listOf(
            "getBidPblancListInfoCnstwk" to BusinessDivision.SERVICE,
            "getBidPblancListInfoServc" to BusinessDivision.CONSTRUCTION,
        ).forEach { (path, division) ->
            MockKonepsHttp { _, _ -> listOf(mapOf("bidNtceNo" to "SYN-6F9-W-1", "bidNtceOrd" to "000")) }.use { mock ->
                withBoot(
                    *validProperties(categories = "mismatch"),
                    "bidvector.koneps.base-url=${mock.baseUrl}",
                    "bidvector.koneps.operations.mismatch.path=$path",
                    "bidvector.koneps.operations.mismatch.division=${division.name}",
                ) { booted ->
                    withClue(failureText(booted.failure)) { booted.failure shouldBe null }
                    val port =
                        booted.context
                            .getBean(CollectionSources::class.java)
                            .all
                            .single()
                            .port
                    val batch = port.fetchNotices(CollectionReferenceDate(LocalDate.of(2026, 9, 24)), null)

                    withClue("$path -> $division") {
                        batch.items.map { it.sourceDivision }.toSet() shouldBe setOf(division)
                    }
                }
            }
        }
    }

    /**
     * **배선이 출하 간격을 관문의 기다림으로 넘긴다**(v r2 M-1 · cr r1 MEDIUM ② 잔여).
     *
     * `KonepsShippedPolicyWireTest` 는 출하 값으로 **자기 pacer 를 만들어** 관문의 기계를 잰다. 그래서
     * 「표의 값」과 「관문의 거동」은 잠겼지만 **그 사이의 운반 단계**(production 에서 pacer 를 만드는
     * 단 한 자리가 그 값을 넘기는가)는 아무 test 도 재지 않았다 — 배선에 `Duration.ZERO` 를 넣는
     * 변이가 모든 표적에서 초록이었다.
     *
     * **벽시계로 재지 않는다**: 계수 축(rate limiter 1 permit/1초)이 같은 크기의 지연을 만들어
     * 두 축이 섞인다 — 간격을 0 으로 둔 변이가 「두 호출이 1초 넘게 벌어진다」를 **통과했다**(실측).
     * 그래서 **요청된 기다림을 값으로** 본다: 배선이 pacer 에 넘기는 기다림 함수를 기록기로 바꿔
     * 끼우고, 그 기록이 출하 간격과 같은지 잰다(기대값도 출하 정책에서 읽는다 — 수를 다시 적지 않는다).
     */
    @Test
    fun `배선은 출하 간격을 관문의 기다림으로 넘긴다`() {
        val shipped = shippedMinimumCallInterval()
        val requested = mutableListOf<Duration>()
        MockKonepsHttp { _, _ -> listOf(mapOf("bidNtceNo" to "SYN-M9-W-1", "bidNtceOrd" to "000")) }.use { mock ->
            val source =
                noticeSourceFrom(pacedTransport { requested += it }, mock.baseUrl, BusinessDivision.SERVICE)
            val day = CollectionReferenceDate(PROBE_DAY)

            source.fetchNotices(day, null)
            source.fetchNotices(day, null)
        }

        // 첫 호출은 기다리지 않고 둘째가 간격만큼 기다린다 — 고정 시계라 그 값이 정확히 선언값이다.
        requested shouldContainExactly listOf(shipped)
    }

    /**
     * **배선이 넘긴 그 함수가 실제로 잠근다**(v r2 M-1 의 뒷면). 위 test 는 **값**을 재므로, 기다림을
     * 빈 식으로 바꾸는 변이는 그 test 를 붉히지 않는다. 그 축을 여기서 짧은 기다림으로 직접 잰다 —
     * production 이 pacer 에 넘기는 바로 그 함수다(위 test 의 주입 기본값).
     */
    @Test
    fun `배선의 기다림은 실제로 잠든다`() {
        val requested = Duration.ofMillis(SLEEP_PROBE_MILLIS)

        val elapsed = measureTimeMillis { sleepFor(requested) }

        withClue("요청 $requested · 실제 ${elapsed}ms") { (elapsed >= SLEEP_PROBE_MILLIS) shouldBe true }
    }

    /**
     * **production 이 넘기는 기다림 함수가 실제로 잠근다**(v r3 M-1).
     *
     * 위 두 test 는 「주입된 함수가 받는 값」과 「`sleepFor` 를 직접 부르면 잠든다」를 잰다. 그 사이에
     * 고리가 하나 더 있다 — **production 이 그 함수를 넘기는가**(다섯 인자 호출의 기본값). 그 고리를
     * 비우는 변이(기본값을 잠들지 않는 람다로)가 앞 판에서 **전 표적 초록**이었다.
     *
     * **두 소스가 한 관문을 공유하는 판**으로 잰다 — production 도 업종마다 소스를 만들고 관문은
     * 하나다. 둘째 소스의 **계수 축(rate limiter)은 자기 몫이 비어 있고** 공유하는 것은 pacer 뿐이라,
     * 둘째 호출이 기다리는 시간은 간격 축의 것이다(앞 판이 한 소스로 재서 두 축이 섞였다 — 그 단언은
     * 변이를 통과했고 지웠다). 전송은 **다섯 인자**로 만든다: 기다림 함수가 기본값이어야 이 측정이
     * production 의 것이 된다.
     */
    @Test
    fun `기본값 기다림 함수는 공유 관문의 둘째 호출을 출하 간격만큼 잠근다`() {
        val shipped = shippedMinimumCallInterval()
        MockKonepsHttp { _, _ -> listOf(mapOf("bidNtceNo" to "SYN-M9-W-2", "bidNtceOrd" to "000")) }.use { mock ->
            val transport = defaultSleeperTransport()
            val first = noticeSourceFrom(transport, mock.baseUrl, BusinessDivision.SERVICE)
            val second = noticeSourceFrom(transport, mock.baseUrl, BusinessDivision.CONSTRUCTION)
            val day = CollectionReferenceDate(PROBE_DAY)

            first.fetchNotices(day, null)
            val elapsedMillis = measureTimeMillis { second.fetchNotices(day, null) }

            withClue("출하 간격 $shipped · 둘째 소스의 호출 ${elapsedMillis}ms") {
                (elapsedMillis >= shipped.toMillis()) shouldBe true
            }
        }
    }

    /**
     * **production 의 호출 자리가 기본값을 쓴다 — 그리고 그 자리는 둘뿐이다**(v r3 M-1 구조 축).
     *
     * 위 test 는 기본값이 잠그는 것을 재지만, 호출 자리가 **자기 sleeper 를 명시**하면 그 기본값을
     * 비껴간다(그 변이도 앞 판에서 초록이었다). 컴파일된 **상수 풀**로 그 형태를 잠근다: 다섯 인자
     * 호출은 `konepsTransportFor$default` 를, 여섯 인자 호출은 함수 이름 자신을 UTF8 항목으로
     * 남기므로 ① 참조하는 production 클래스 전수와 ② 그 참조의 형태를 둘 다 잰다. 소스 문법이
     * 아니라 컴파일러가 만든 항목이라 스타일로 비껴갈 수 없다.
     *
     * **계수로 형태를 가른다**: 다섯 인자 호출만 있으면 두 계수가 같고(이름이 `$default` 항목 안에만
     * 있다), 여섯 인자 호출이 끼면 맨 이름 항목이 하나 더 생겨 계수가 갈린다 — 한 클래스가 둘을
     * 함께 가진 판까지 그 등식이 잡는다. `javap` 를 쓰지 않는 이유는 전수 census 가 281 클래스에
     * 96 초였기 때문이다(실측) — 여기서 필요한 것은 서술자가 아니라 **항목의 유무와 계수**다.
     */
    @Test
    fun `전송을 만드는 production 호출 자리는 둘이고 둘 다 기본값 형태다`() {
        val pools = appProductionConstantPools()
        val callers =
            pools
                .filterValues { it.contains(TRANSPORT_FACTORY) }
                .keys
                // 선언한 파일 자신의 산출물(합성 람다 포함)은 호출 자리가 아니다.
                .filterNot { it.substringAfterLast('/').startsWith("CollectionWiringSupportKt") }

        callers shouldContainExactlyInAnyOrder listOf("wiring/CollectionWiring", "wiring/OpeningCollectionWiring")
        callers.forEach { caller ->
            val pool = requireNotNull(pools[caller]) { "빌드 산출물에 $caller 가 없다" }
            withClue(caller) {
                pool.windowed(TRANSPORT_FACTORY_DEFAULT.length).count { it == TRANSPORT_FACTORY_DEFAULT } shouldBe
                    pool.windowed(TRANSPORT_FACTORY.length).count { it == TRANSPORT_FACTORY }
                pool shouldContain TRANSPORT_FACTORY_DEFAULT
            }
        }
    }

    @Test
    fun `업종 행의 대분류 누락·어휘 밖과 빈 경로는 기동 실패다 — 표류도 빈 값도 HTTP 시점까지 미루지 않는다`() {
        listOf(
            arrayOf("bidvector.koneps.operations.goods.path=getBidPblancListInfoThng"),
            arrayOf(
                "bidvector.koneps.operations.goods.path=getBidPblancListInfoThng",
                "bidvector.koneps.operations.goods.division=basket",
            ),
            // 빈·공백 경로는 `URI.create("$baseUri/")` 가 되어 HTTP 시점에 죽는다.
            arrayOf(
                "bidvector.koneps.operations.goods.path=",
                "bidvector.koneps.operations.goods.division=goods",
            ),
            arrayOf(
                "bidvector.koneps.operations.goods.path=   ",
                "bidvector.koneps.operations.goods.division=goods",
            ),
        ).forEach { rowProperties ->
            withBoot(*validProperties(categories = "goods"), *rowProperties) { booted ->
                booted.failure shouldNotBe null
                failureText(booted.failure) shouldNotContain secretKey
            }
        }
    }
}

/** 기다림을 실제로 재는 짧은 값 — 벽시계 하한 단언이라 느린 호스트에서 흔들리지 않는다. */
private const val SLEEP_PROBE_MILLIS = 40L

/** 전송을 만드는 함수의 이름과 그 **기본 인자 합성** 형태 — 상수 풀 항목으로 찾는 열쇠다. */
private const val TRANSPORT_FACTORY = "konepsTransportFor"
private const val TRANSPORT_FACTORY_DEFAULT = "konepsTransportFor\$default"

/**
 * app production 클래스의 **상수 풀을 원시 바이트로** 읽는다(이름 → 내용). UTF8 항목은 class 파일에
 * 그대로 들어 있으므로 「이 이름을 참조하는가」는 바이트에서 바로 답이 나온다 — `javap` 전수는
 * 같은 질문에 96 초였다(실측).
 */
private fun appProductionConstantPools(): Map<String, String> {
    val root = File("build/classes/kotlin/main/bidvector/app")
    check(root.isDirectory) { "빌드 산출물을 찾지 못했다: ${root.absolutePath} — :app:compileKotlin 선행 필요" }
    return root
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .associate {
            it.relativeTo(root).path.removeSuffix(".class") to String(it.readBytes(), StandardCharsets.ISO_8859_1)
        }
}

private val PROBE_DAY: LocalDate = LocalDate.of(2026, 9, 24)

/** 상한·실행 상태·시계는 production 과 같은 타입으로 세운다 — 다른 것은 기다림 함수뿐이다. */
private fun probeBudget(): CallBudgetLedger =
    CallBudgetLedger(
        CollectionCallBudget(perDay = 10, total = 10),
        PROBE_DAY,
        CallSpend(total = 0, today = 0),
    )

/** 기다림 함수를 **주입해** 만든 전송 — 넘겨진 값을 보는 자리가 쓴다. */
private fun pacedTransport(sleeper: (Duration) -> Unit): KonepsTransport =
    konepsTransportFor(
        credential = KonepsCredentialProperties("TRANSPORT-PROBE-KEY"),
        on = PROBE_DAY,
        runState = RunStateDirectory(Files.createTempDirectory("m9-paced-run-state")),
        budget = probeBudget(),
        clock = Clock { Instant.parse("2026-09-24T03:00:00Z") },
        sleeper = sleeper,
    )

/**
 * **production 과 같은 다섯 인자** 호출 — 기다림 함수는 그 기본값이다(`CollectionWiring`·
 * `OpeningCollectionWiring` 의 호출 형태와 같다). 그 기본값이 실제로 잠그는지를 재는 자리가 쓴다.
 */
private fun defaultSleeperTransport(): KonepsTransport =
    konepsTransportFor(
        credential = KonepsCredentialProperties("TRANSPORT-PROBE-KEY"),
        on = PROBE_DAY,
        runState = RunStateDirectory(Files.createTempDirectory("m9-default-run-state")),
        budget = probeBudget(),
        clock = Clock { Instant.parse("2026-09-24T03:00:00Z") },
    )

/**
 * **production 조립과 같은 인자 여섯**으로 세운 공고 소스(`CollectionWiring.sourceFor` 와 같다).
 * 전송을 인자로 받는 이유는 **두 소스가 한 관문을 공유하는** 판을 짓기 때문이다(production 도
 * 업종마다 소스를 만들고 관문은 하나다) — 그 판이 간격 축을 계수 축에서 떼어 낸다.
 */
private fun noticeSourceFrom(
    transport: KonepsTransport,
    baseUrl: String,
    division: BusinessDivision,
): KonepsOpenApiNoticeSource =
    KonepsOpenApiNoticeSource(
        gate = transport.gate,
        baseUri = URI.create("$baseUrl/${operationPathFor(division)}"),
        serviceKey = transport.serviceKey,
        httpPolicy = transport.httpPolicy,
        collectionPolicyProvider = ::collectionPolicyAt,
        businessDivision = division,
    )

private fun operationPathFor(division: BusinessDivision): String =
    when (division) {
        BusinessDivision.CONSTRUCTION -> "getBidPblancListInfoCnstwk"
        else -> "getBidPblancListInfoServc"
    }

/**
 * 출하 전송 정책의 최소 간격 — **test 가 그 수를 다시 적지 않는다**(두 자리에 같은 수를 두지 않는다).
 * 값이 바뀌면 단언도 함께 움직인다.
 */
private fun shippedMinimumCallInterval(): Duration =
    when (val resolution = KONEPS_HTTP_POLICY.resolve(LocalDate.of(2026, 9, 24))) {
        is Resolution.Resolved -> resolution.value.minimumCallInterval
        else -> error("출하 전송 정책이 적용되지 않는다: $resolution")
    }

/** 저장소 밖 — 배선은 디렉터리가 있는지만 본다(표본 확정과 시도 기록은 수집이 한다). */
private val NOTICE_RUN_STATE: Path = Files.createTempDirectory("6g-notice-run-state")

/**
 * 두 수집 배선이 **각자 등록하는** 타입(D-6G2c-6) — 함께 켜면 이 셋이 둘씩 되어 주입이 모호해진다.
 * 한 프로세스에 두 갈래를 두는 요구가 없으므로 `@Qualifier` 로 공존시키지 않는다.
 */
private val SHARED_BETWEEN_BRANCHES =
    setOf(
        CollectionRange::class.java.name,
        RunStateDirectory::class.java.name,
        CallBudgetLedger::class.java.name,
    )
