package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsOpenApiNoticeSource
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.CollectionProperties
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.KonepsOpeningEndpointProperties
import bidvector.app.collection.MockKonepsHttp
import bidvector.app.collection.OpeningCollectionProperties
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CallSpend
import bidvector.procurement.CollectionCallBudget
import bidvector.procurement.CollectionReferenceDate
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangePolicyData
import bidvector.workflow.collection.OPENING_COLLECTION_RANGE_POLICY
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
import kotlin.system.measureNanoTime

private val PROBE_DAY: LocalDate = LocalDate.of(2026, 9, 24)
private val PROBE_NOW: Instant = Instant.parse("2026-09-24T03:00:00Z")

/**
 * 기다림을 실제로 재는 짧은 값 — 벽시계 **하한** 단언이라 느린 호스트에서 흔들리지 않는다. 재는 자는
 * `nanoTime` 이다: ms 로 자른 두 값의 차는 1 ms 아래를 잃어 하한에 여유를 0 으로 만든다(cr r6 L-C).
 */
private val SLEEP_PROBE: Duration = Duration.ofMillis(40)

/**
 * 전송을 만드는 함수의 이름 — 상수 풀 항목으로 **호출자 클래스 전수**를 찾는 열쇠다. 상수 풀은 같은
 * Methodref 를 **한 번만** 담으므로 세는 단위가 클래스이고 호출 자리가 아니다(v r6 M-1).
 */
private const val TRANSPORT_FACTORY = "konepsTransportFor"

/** census 의 뿌리 — 클래스 파일 경로의 뿌리이자 [censusNameOf] 가 떼는 접두다(두 자리가 같은 상수다). */
private const val APP_PRODUCTION_PACKAGE = "bidvector.app"

/** 조립이 **읽지 않는** 자리 — 실행 상태 빈은 다른 메서드가 만든다(cr r6 L-F). 디렉터리를 만들지 않는다. */
private const val UNREAD_RUN_STATE_DIR = "조립이 읽지 않는 자리"

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
 * 4. **production 이 넘기는 것이 그 함수인가** — [전송을 만드는 production 자리 전수가 출하 간격을 지킨다].
 *    두 조립 메서드(`CollectionWiring.collectionSources`·`OpeningCollectionWiring.openingCollectionSources`)
 *    가 **실제로 쓰는 전송 인스턴스**로 재고, 그 **호출자 클래스 전수**는 상수 풀에서 도출해 탐침표와
 *    대조한다. 그래서 **그 두 자리에서는** 기본값을 비우든 자기 sleeper 를 넘기든 붉어지고, 셋째
 *    **클래스**가 전송을 만들면 거동을 재지 않은 채 지나갈 수 없다(집합 대조가 먼저 붉어진다).
 *
 * **알려진 제한 — 같은 클래스 안의 둘째 호출은 census 밖이다**(v r6 M-1). 상수 풀이 같은 Methodref 를
 * 한 번만 담아 세는 단위가 클래스이고, 탐침은 클래스마다 조립 메서드 **하나**만 잰다. 그래서 이미 키에
 * 있는 두 클래스에 `konepsTransportFor` 호출을 하나 더 두면 어느 게이트도 붉어지지 않는다. 현존하는 두
 * 호출은 그 조립 메서드의 것이라 아래 ②가 거동으로 잠근다 — 잠기지 않은 자리는 지금 없고, 반례는 새
 * 전송 생성 경로를 **더하는** 변경이라 diff 에 그 호출이 보인다.
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
        val elapsedNanos = measureNanoTime { sleepFor(SLEEP_PROBE) }

        withClue("요청 $SLEEP_PROBE · 실제 ${elapsedNanos}ns") {
            (elapsedNanos >= SLEEP_PROBE.toNanos()) shouldBe true
        }
    }

    /**
     * **전송을 만드는 production 자리 전수가 출하 간격을 지킨다**(v r4 L-1 · v r5 H-1).
     *
     * 두 측정을 **한 자리에서 잇는다**.
     * ① 전수는 컴파일된 상수 풀에서 도출한다 — `konepsTransportFor` 를 참조하는 production **클래스**
     *    집합이 아래 탐침표의 키와 **같아야** 한다. 셋째 **클래스**가 전송을 만들면 거동을 재지 않은
     *    채 지나갈 수 없다(그 클래스가 자기 sleeper 를 넘길 수 있다). 같은 클래스 **안**의 둘째 호출은
     *    이 축 밖이다(v r6 M-1) — 클래스 KDoc 의 알려진 제한.
     * ② 각 자리의 거동은 **그 자리의 조립 메서드가 낸 소스**로 잰다. 두 소스가 관문 하나를
     *    공유하고 계수 축(rate limiter)은 소스마다 자기 몫이 비어 있으므로, 둘째 호출이 기다린
     *    시간은 간격 축의 것이다. 그 **전제 자체를 음성 대조가 단언한다**(cr r6 M-3): 첫 호출이
     *    간격 미만이어야 한다 — 쪽 넘김 따위로 첫 소스가 관문을 두 번 지나면 계수 축이 같은 크기의
     *    대기를 보태 둘째의 하한이 간격 축에 둔감해지고, 그때 이 대조가 먼저 붉어진다.
     *
     * 앞 판은 ②를 `CollectionWiring` 한 자리에서만 쟀고, 그래서 `OpeningCollectionWiring` 의 호출에
     * `sleeper = { }` 를 붙이는 변이가 **모든 게이트를 통과했다**(v r5 H-1). 자리의 수를 세면서 거동을
     * 하나만 재던 그 틈을 ①↔② 의 연결이 닫는다.
     */
    @Test
    fun `전송을 만드는 production 자리 전수가 출하 간격을 지킨다`() {
        val shipped = shippedMinimumCallInterval()
        val callers =
            appProductionConstantPools()
                .filterValues { it.contains(TRANSPORT_FACTORY) }
                .keys
                // 선언한 파일 자신의 산출물(합성 람다 포함)은 호출 자리가 아니다.
                .filterNot { it.substringAfterLast('/').startsWith("CollectionWiringSupportKt") }

        callers shouldContainExactlyInAnyOrder CALL_SITE_PROBES.keys

        MockKonepsHttp { _, _ -> listOf(noticeRow("SYN-M9-W-2")) }.use { mock ->
            CALL_SITE_PROBES.forEach { (caller, probeFor) ->
                val calls = probeFor(mock.baseUrl)
                withClue("$caller — 관문을 공유하는 소스 둘") { calls.size shouldBe 2 }

                val firstNanos = measureNanoTime { calls.first().invoke() }
                val secondNanos = measureNanoTime { calls.last().invoke() }

                withClue("$caller · 음성 대조 — 첫 소스의 호출 ${firstNanos}ns 는 출하 간격 $shipped 미만") {
                    (firstNanos < shipped.toNanos()) shouldBe true
                }
                withClue("$caller · 출하 간격 $shipped · 둘째 소스의 호출 ${secondNanos}ns") {
                    (secondNanos >= shipped.toNanos()) shouldBe true
                }
            }
        }
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
 * **호출자 클래스 → 그 클래스의 조립이 낸 호출 둘**(v r5 H-1). 키는 **클래스 심볼에서 도출한다**
 * (cr r6 M-2 ⓐ) — 문자열을 손으로 적지 않으므로 오타난 키가 컴파일을 통과하지 못한다. 값은 그
 * 클래스의 **production 조립 메서드**로 만든 소스 둘의 호출이다. 두 집합을 test 가 대조하므로 전송을
 * 만드는 클래스가 늘면 탐침도 늘려야 하고, 늘리지 않으면 붉어진다.
 *
 * **키↔탐침의 결속은 게이트가 재지 않는 손 불변식이다**(cr r6 M-2 ⓑ). 집합 대조가 보는 것은 키뿐이라,
 * 새 클래스의 키를 **기존 탐침에 묶으면** 집합은 맞고 거동 루프는 같은 자리를 두 번 잰다. 탐침을 더하는
 * 사람이 그것이 **그 클래스의** 조립 메서드를 부르는지 확인해야 한다. 같은 클래스 안의 둘째 호출도
 * 측정 밖이다(v r6 M-1).
 */
private val CALL_SITE_PROBES: Map<String, (String) -> List<() -> Unit>> =
    mapOf(
        censusNameOf(CollectionWiring::class.java) to ::noticeCalls,
        censusNameOf(OpeningCollectionWiring::class.java) to ::openingCalls,
    )

/** 클래스 심볼 → [appProductionConstantPools] 가 내는 이름(경로 형태). 둘이 같은 접두를 쓴다. */
private fun censusNameOf(type: Class<*>): String {
    val prefix = "$APP_PRODUCTION_PACKAGE."
    check(type.name.startsWith(prefix)) { "app production 클래스가 아니다: ${type.name}" }
    return type.name.removePrefix(prefix).replace('.', '/')
}

/** 공고 수집 자리 — 업종 둘의 소스가 관문 하나를 공유한다. */
private fun noticeCalls(baseUrl: String): List<() -> Unit> {
    val day = CollectionReferenceDate(PROBE_DAY)
    return productionSources(baseUrl).all.map { source -> { source.port.fetchNotices(day, null) } }
}

/**
 * 개찰 수집 자리 — 공고 갈래와 **같은 전송 공장, 다른 관문 인스턴스**다(cr r6 L-D). 두 조립이 각자
 * `konepsTransportFor` 를 부르므로 관문은 갈래마다 하나이고, **그 갈래 안의** 소스 둘이 그것을 공유한다.
 * 이 자리가 앞 판에서 측정 밖이었다(v r5 H-1).
 */
private fun openingCalls(baseUrl: String): List<() -> Unit> {
    val day = CollectionReferenceDate(PROBE_DAY)
    return productionOpeningSources(baseUrl).all.map { source -> { source.port.fetchOpeningResults(day, null) } }
}

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
                runStateDir = UNREAD_RUN_STATE_DIR,
            ),
        endpoint = KonepsEndpointProperties(baseUrl = baseUrl),
        credential = KonepsCredentialProperties("TRANSPORT-PROBE-KEY"),
        range = probeRange(COLLECTION_RANGE_POLICY, "수집 범위"),
        runState = probeRunState("m9-production-gate-run-state"),
        budget = probeBudget(),
        clock = Clock { PROBE_NOW },
    )

/**
 * 창도 **production 과 같은 자리**로 짓는다 — `CollectionRange` 의 생성자가 `internal` 이라 test 가
 * 상한을 지어낼 수 없고(그 금지가 옳다), 배선이 쓰는 해소 함수를 그대로 부른다.
 *
 * **기본값을 두지 않는다**(cr r6 L-B) — production 의 `resolveCollectionRange` 가 거부한 그 조용한
 * 상속을 탐침이 되살리면, 새 갈래의 탐침이 자리를 비운 채 남의 상한으로 재고도 초록이 된다. 라벨도
 * **production 이 넘기는 문자열 그대로**다(cr r6 L-A): 탐침이 도메인 어휘를 바꿔 쓰면 실패 문면이
 * 가리키는 자리를 코드에서 찾을 수 없다.
 */
private fun probeRange(
    rangePolicy: EffectiveDatedPolicy<CollectionRangePolicyData>,
    label: String,
): CollectionRange =
    resolveCollectionRange(
        from = PROBE_DAY,
        to = PROBE_DAY,
        clock = Clock { PROBE_NOW },
        label = label,
        rangePolicy = rangePolicy,
    )

/** 개찰 갈래의 production 조립 — 같은 `konepsTransportFor` 자리를 쓰는 **둘째** 호출자다. */
private fun productionOpeningSources(baseUrl: String): OpeningCollectionSources =
    OpeningCollectionWiring().openingCollectionSources(
        properties =
            OpeningCollectionProperties(
                from = PROBE_DAY,
                to = PROBE_DAY,
                categories = listOf("construction", "service"),
                samplingSeed = "m9-ops-code-probe",
                sampleSize = 2,
                callsPerDay = 10,
                callsTotal = 10,
                runStateDir = UNREAD_RUN_STATE_DIR,
            ),
        endpoint = KonepsEndpointProperties(baseUrl = baseUrl),
        opening = KonepsOpeningEndpointProperties(scsbidBaseUrl = baseUrl),
        credential = KonepsCredentialProperties("TRANSPORT-PROBE-KEY"),
        range = probeRange(OPENING_COLLECTION_RANGE_POLICY, "공고일 범위"),
        runState = probeRunState("m9-opening-probe-gate"),
        budget = probeBudget(),
        clock = Clock { PROBE_NOW },
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
    val root = File("build/classes/kotlin/main/${APP_PRODUCTION_PACKAGE.replace('.', '/')}")
    check(root.isDirectory) { "빌드 산출물을 찾지 못했다: ${root.absolutePath} — :app:compileKotlin 선행 필요" }
    return root
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .associate {
            it.relativeTo(root).path.removeSuffix(".class") to String(it.readBytes(), StandardCharsets.ISO_8859_1)
        }
}
