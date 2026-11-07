package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsOpenApiNoticeSource
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.CollectionProperties
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.MockKonepsHttp
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CallSpend
import bidvector.procurement.CollectionCallBudget
import bidvector.procurement.CollectionReferenceDate
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.system.measureTimeMillis

private val PROBE_DAY: LocalDate = LocalDate.of(2026, 9, 24)
private val PROBE_NOW: Instant = Instant.parse("2026-09-24T03:00:00Z")

/** 기다림을 실제로 재는 짧은 값 — 벽시계 **하한** 단언이라 느린 호스트에서 흔들리지 않는다. */
private const val SLEEP_PROBE_MILLIS = 40L

/** 전송을 만드는 함수의 이름 — 상수 풀 항목으로 **호출자 전수**를 찾는 열쇠다. */
private const val TRANSPORT_FACTORY = "konepsTransportFor"

/**
 * **출하 간격이 배선을 타고 실제 대기까지 가는가**(M9/ops-code · v M-1 라운드 둘).
 *
 * `CollectionWiringTest` 에서 갈라 나왔다 — 그 파일이 500 줄 한도를 넘겼고(실측 616), 간격 축은
 * 켜짐 조건·기동 실패와 **다른 관심사**라 갈라도 읽기가 나빠지지 않는다.
 *
 * 이 축은 **네 단계**이고 각 단계에 자기 자리가 있다.
 * 1. 표의 값 — `KonepsShippedPolicyWireTest`(adapters).
 * 2. 배선이 pacer 에 넘기는 값 — [배선은 출하 간격을 관문의 기다림으로 넘긴다].
 * 3. 그 기다림 함수가 실제로 잠그는가 — [배선의 기다림은 실제로 잠든다].
 * 4. **production 이 넘기는 것이 그 함수인가** — [배선이 세운 소스 둘은 출하 간격만큼 벌어진다].
 *    production 메서드(`CollectionWiring.collectionSources`)가 **실제로 쓰는 전송 인스턴스**로 재므로,
 *    기본값을 비우든 호출 자리가 자기 sleeper 를 넘기든 **어느 경로로 바꿔도** 이 단언이 붉어진다
 *    (참조 형태를 세던 앞 판은 「5인자 호출을 버리고 다른 전송을 쓰는」 변이를 통과했다).
 */
class CollectionWiringSleeperTest {
    /**
     * **배선이 출하 간격을 관문의 기다림으로 넘긴다** — 넘겨지는 **값**을 본다.
     *
     * 벽시계로 값을 재지 않는 이유: 계수 축(rate limiter 1 permit/1초)이 같은 크기의 지연을 만들어
     * 두 축이 섞인다(간격을 0 으로 둔 변이가 벽시계 단언을 통과한 실측이 있다). 그래서 기다림 함수를
     * 기록기로 바꿔 끼우고 **요청된 기다림**을 출하 간격과 대조한다 — 기대값도 출하 정책에서 읽는다.
     */
    @Test
    fun `배선은 출하 간격을 관문의 기다림으로 넘긴다`() {
        val shipped = shippedMinimumCallInterval()
        val requested = mutableListOf<Duration>()
        MockKonepsHttp { _, _ -> listOf(noticeRow("SYN-M9-W-1")) }.use { mock ->
            val transport = pacedTransport { requested += it }
            val source = noticeSourceFrom(transport, mock.baseUrl, BusinessDivision.SERVICE)
            val day = CollectionReferenceDate(PROBE_DAY)

            source.fetchNotices(day, null)
            source.fetchNotices(day, null)
        }

        // 첫 호출은 기다리지 않고 둘째가 간격만큼 기다린다 — 고정 시계라 그 값이 정확히 선언값이다.
        requested shouldBe listOf(shipped)
    }

    /**
     * **배선이 넘기는 그 함수가 실제로 잠근다**. 위 test 는 값을 재므로 기다림을 빈 식으로 바꾸는
     * 변이를 붉히지 못한다 — 그 축을 짧은 기다림으로 직접 잰다(production 기본값이 이 함수다).
     */
    @Test
    fun `배선의 기다림은 실제로 잠든다`() {
        val requested = Duration.ofMillis(SLEEP_PROBE_MILLIS)

        val elapsed = measureTimeMillis { sleepFor(requested) }

        withClue("요청 $requested · 실제 ${elapsed}ms") { (elapsed >= SLEEP_PROBE_MILLIS) shouldBe true }
    }

    /**
     * **production 이 실제로 쓰는 전송으로 잰다**(v r4 L-1).
     *
     * 앞 판은 「기본값 형태의 참조가 있다」를 상수 풀에서 셌다 — 그 술어는 **버려지는 5인자 호출**을
     * 남겨 두고 선언 파일 안 헬퍼로 만든 다른 전송을 쓰는 변이를 통과했다(MW4d). 참조의 모양이
     * 아니라 **쓰이는 인스턴스**를 봐야 닫힌다.
     *
     * 그래서 조립 메서드(`CollectionWiring.collectionSources`)를 **그대로 불러** 그것이 낸 소스 둘로
     * 잰다(Spring 컨텍스트가 필요 없다 — 인자를 받는 `open fun` 이다). 두 소스는 관문 **하나**를
     * 공유하고 계수 축은 각자 자기 몫이 비어 있으므로, 둘째 소스의 호출이 기다린 시간은 **간격
     * 축의 것**이다. 기본값을 비우든(MW3) 호출 자리가 자기 sleeper 를 넘기든(MW4·MW4d) 이 단언이
     * 붉어진다 — 어느 경로로 바꿔도 「쓰인 전송」이 잠그지 않게 되기 때문이다.
     */
    @Test
    fun `배선이 세운 소스 둘은 출하 간격만큼 벌어진다`() {
        val shipped = shippedMinimumCallInterval()
        MockKonepsHttp { _, _ -> listOf(noticeRow("SYN-M9-W-2")) }.use { mock ->
            val sources = productionSources(mock.baseUrl)
            val day = CollectionReferenceDate(PROBE_DAY)
            // 두 소스가 관문 하나를 공유하는 판이어야 간격 축이 계수 축에서 떼어진다.
            withClue("배선이 낸 소스") { sources.all.size shouldBe 2 }

            val first = sources.all.first().port
            val second = sources.all.last().port

            first.fetchNotices(day, null)
            val elapsedMillis = measureTimeMillis { second.fetchNotices(day, null) }

            withClue("출하 간격 $shipped · 둘째 소스의 호출 ${elapsedMillis}ms") {
                (elapsedMillis >= shipped.toMillis()) shouldBe true
            }
        }
    }

    /**
     * **전송을 만드는 production 자리는 둘뿐이다** — 셋째 자리가 생기면 위 거동 단언이 그 자리를
     * 보지 못한다(그 자리가 자기 sleeper 를 넘길 수 있다). 컴파일된 상수 풀로 **호출자 전수**를
     * 잰다(소스 문법이 아니라 컴파일러가 만든 항목이라 스타일로 비껴갈 수 없다).
     *
     * 형태(`$default` 유무)는 **세지 않는다**(v r4 L-1): 그 계수는 「쓰이는 전송」을 말하지 못했고,
     * 그 축은 위 거동 단언이 진다. 여기서 재는 것은 **자리의 수**다.
     */
    @Test
    fun `전송을 만드는 production 자리는 둘뿐이다`() {
        val callers =
            appProductionConstantPools()
                .filterValues { it.contains(TRANSPORT_FACTORY) }
                .keys
                // 선언한 파일 자신의 산출물(합성 람다 포함)은 호출 자리가 아니다.
                .filterNot { it.substringAfterLast('/').startsWith("CollectionWiringSupportKt") }

        callers shouldContainExactlyInAnyOrder listOf("wiring/CollectionWiring", "wiring/OpeningCollectionWiring")
    }
}

private fun noticeRow(number: String): Map<String, String> = mapOf("bidNtceNo" to number, "bidNtceOrd" to "000")

/** 상한·실행 상태·시계는 production 과 같은 타입으로 세운다 — 다른 것은 기다림 함수뿐이다. */
private fun probeBudget(): CallBudgetLedger =
    CallBudgetLedger(
        CollectionCallBudget(perDay = 10, total = 10),
        PROBE_DAY,
        CallSpend(total = 0, today = 0),
    )

private fun probeRunState(name: String): RunStateDirectory = RunStateDirectory(Files.createTempDirectory(name))

/** 기다림 함수를 **주입해** 만든 전송 — 넘겨진 값을 보는 자리가 쓴다. */
private fun pacedTransport(sleeper: (Duration) -> Unit): KonepsTransport =
    konepsTransportFor(
        credential = KonepsCredentialProperties("TRANSPORT-PROBE-KEY"),
        on = PROBE_DAY,
        runState = probeRunState("m9-paced-run-state"),
        budget = probeBudget(),
        clock = Clock { PROBE_NOW },
        sleeper = sleeper,
    )

/**
 * **production 조립 메서드가 낸 소스들** — 이 slice 가 재려는 「실제로 쓰이는 전송」이 그 안에 있다.
 * 업종 둘을 주는 이유는 두 소스가 관문 하나를 공유하는 판이 간격 축을 계수 축에서 떼어 내기 때문이다.
 */
private fun productionSources(baseUrl: String): CollectionSources =
    CollectionWiring().collectionSources(
        properties =
            CollectionProperties(
                from = PROBE_DAY,
                to = PROBE_DAY,
                categories = listOf("construction", "service"),
                callsPerDay = 10,
                callsTotal = 10,
                runStateDir = Files.createTempDirectory("m9-production-run-state").toString(),
            ),
        endpoint = KonepsEndpointProperties(baseUrl = baseUrl),
        credential = KonepsCredentialProperties("TRANSPORT-PROBE-KEY"),
        range = probeRange(),
        runState = probeRunState("m9-production-gate-run-state"),
        budget = probeBudget(),
        clock = Clock { PROBE_NOW },
    )

/**
 * 창도 **production 과 같은 자리**로 짓는다 — `CollectionRange` 의 생성자가 `internal` 이라 test 가
 * 상한을 지어낼 수 없고(그 금지가 옳다), 배선이 쓰는 해소 함수를 그대로 부른다.
 */
private fun probeRange(): CollectionRange =
    resolveCollectionRange(
        from = PROBE_DAY,
        to = PROBE_DAY,
        clock = Clock { PROBE_NOW },
        label = "표본 창",
        rangePolicy = COLLECTION_RANGE_POLICY,
    )

/** `CollectionWiring.sourceFor` 와 같은 인자 여섯 — 전송만 밖에서 받는다. */
private fun noticeSourceFrom(
    transport: KonepsTransport,
    baseUrl: String,
    division: BusinessDivision,
): KonepsOpenApiNoticeSource =
    KonepsOpenApiNoticeSource(
        gate = transport.gate,
        baseUri = URI.create("$baseUrl/getBidPblancListInfoServc02"),
        serviceKey = transport.serviceKey,
        httpPolicy = transport.httpPolicy,
        collectionPolicyProvider = ::collectionPolicyAt,
        businessDivision = division,
    )

/**
 * 출하 전송 정책의 최소 간격 — **test 가 그 수를 다시 적지 않는다**(두 자리에 같은 수를 두지 않는다).
 * 값이 바뀌면 단언도 함께 움직인다.
 */
private fun shippedMinimumCallInterval(): Duration =
    when (val resolution = KONEPS_HTTP_POLICY.resolve(PROBE_DAY)) {
        is Resolution.Resolved -> resolution.value.minimumCallInterval
        else -> error("출하 전송 정책이 적용되지 않는다: $resolution")
    }

/**
 * app production 클래스의 **상수 풀을 원시 바이트로** 읽는다(이름 → 내용). UTF8 항목은 class 파일에
 * 그대로 들어 있으므로 「이 이름을 참조하는가」는 바이트에서 바로 답이 나온다 — `javap` 전수는 같은
 * 질문에 96 초였다(실측).
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
