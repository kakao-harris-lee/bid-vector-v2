package bidvector.app.wiring

import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.KonepsOpeningEndpointProperties
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.NoticeId
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.function.Supplier
import javax.sql.DataSource

/** 배선은 이 port 를 요구할 뿐 쓰지 않는다 — 기동 **조건**만 재므로 빈 응답으로 족하다. */
private object RollbackAxisStore : CollectedAxisStore {
    override fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<NoticeId>,
    ): Set<NoticeId> = emptySet()
}

/**
 * M9/portal-02 — **선언한 되돌림이 실제로 뜨는가.** 이 slice 의 전환은 설정 값이라 「재빌드 없이
 * 옛 서비스로 돌아간다」가 계약의 acceptance 축인데, 그 인자 목록은 문서에만 있었고 **어느 test 도
 * 재지 않았다**(변이 ①②는 전환 **방향**만 잰다).
 *
 * 문서가 틀릴 수 있는 자리가 구체적이다: `operations` 두 축은 **값 객체의 Map** 이고 행 타입의 칸이
 * 전부 필수다. Spring 의 Map 바인딩은 속성 소스에서 찾은 항목으로 맵을 **구성**하므로 한 칸만 주면
 * 그 행에 나머지 칸이 없어 **프로세스가 뜨지 않는다**. 그래서 이 test 는 둘을 함께 잰다 —
 * ⓐ 정본 목록으로 두 배선이 **뜨고** 여섯 경로가 옛 이름으로 돌아오는가
 * ⓑ 행의 한 칸만 주는 꼴은 **기동 실패**인가(정본 목록이 행 전수인 이유를 거동으로 남긴다).
 *
 * 인자 목록의 정본은 `app/src/test/resources/rollback/koneps-portal-02-rollback-args.txt` 이고
 * runbook §8-4 가 그 파일을 가리킨다 — 문서와 test 가 **한 소스**에서 온다. 목록이 틀리면 ⓐ 가 붉다.
 */
class KonepsRollbackArgumentsTest {
    private val fixedNow = Instant.parse("2026-11-05T03:00:00Z")

    /** 되돌림 인자 정본 — `#` 줄과 빈 줄은 뺀다. */
    private val rollbackArguments: List<String> =
        checkNotNull(javaClass.getResourceAsStream("/rollback/koneps-portal-02-rollback-args.txt")) {
            "되돌림 인자 목록 리소스가 없다"
        }.bufferedReader()
            .readLines()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }

    private fun argumentsFor(prefix: String): Array<String> =
        rollbackArguments.filter { it.startsWith(prefix) }.toTypedArray()

    @Test
    fun `정본 목록으로 공고 목록 배선이 뜨고 base 와 경로 둘이 옛 이름으로 돌아온다`() {
        val booted =
            bootNoticeBranch(*argumentsFor("bidvector.koneps.base-url"), *argumentsFor("bidvector.koneps.operations."))
        try {
            withClue(failureText(booted.failure)) { booted.failure shouldBe null }
            val endpoint = booted.context.getBean(KonepsEndpointProperties::class.java)
            endpoint.baseUrl shouldBe "https://apis.data.go.kr/1230000/ad/BidPublicInfoService"
            endpoint.operations.mapValues { (_, operation) -> operation.path } shouldBe
                mapOf(
                    "construction" to "getBidPblancListInfoCnstwk",
                    "service" to "getBidPblancListInfoServc",
                )
            // 업무 대분류가 살아 있다 — 행 전수를 주는 목적이 이것이다(경로만 주면 이 칸이 사라진다).
            endpoint.operations.mapValues { (_, operation) -> operation.division } shouldBe
                mapOf(
                    "construction" to BusinessDivision.CONSTRUCTION,
                    "service" to BusinessDivision.SERVICE,
                )
        } finally {
            booted.context.close()
        }
    }

    @Test
    fun `정본 목록으로 개찰 배선이 뜨고 A값·기초금액이 옛 이름으로 돌아오며 낙찰정보 경로는 그대로다`() {
        val booted = bootOpeningBranch(*argumentsFor("bidvector.koneps.opening."))
        try {
            withClue(failureText(booted.failure)) { booted.failure shouldBe null }
            val opening = booted.context.getBean(KonepsOpeningEndpointProperties::class.java)
            opening.bidPriceFormulaAPath shouldBe "getBidPblancListBidPrceCalclAInfo"
            opening.operations.mapValues { (_, operation) -> operation.baseAmountPath } shouldBe
                mapOf(
                    "construction" to "getBidPblancListInfoCnstwkBsisAmount",
                    "service" to "getBidPblancListInfoServcBsisAmount",
                )
            // 낙찰정보서비스 축은 **같은 값으로 다시 적어야** 행이 서는 자리다(행 전체 교체의 대가).
            opening.operations.mapValues { (_, operation) -> operation.openingResultListPath } shouldBe
                mapOf(
                    "construction" to "getOpengResultListInfoCnstwk",
                    "service" to "getOpengResultListInfoServc",
                )
            opening.operations.mapValues { (_, operation) -> operation.reservePriceDetailPath } shouldBe
                mapOf(
                    "construction" to "getOpengResultListInfoCnstwkPreparPcDetail",
                    "service" to "getOpengResultListInfoServcPreparPcDetail",
                )
            opening.scsbidBaseUrl shouldBe "https://apis.data.go.kr/1230000/as/ScsbidInfoService"
        } finally {
            booted.context.close()
        }
    }

    /**
     * 행의 한 칸만 주는 꼴 — **기동 실패**다. 이것이 정본 목록을 행 전수로 두는 이유이고, 짧게 적고
     * 싶은 다음 사람이 이 test 를 붉히지 않고는 목록을 줄일 수 없게 한다. 공고 목록 축의 같은 거동은
     * `CollectionWiringTest` 가 이미 잠갔고 여기서는 **개찰 축**을 함께 닫는다.
     */
    @Test
    fun `개찰 축 행의 한 칸만 주면 기동 실패다 — 그래서 목록이 행 전수여야 한다`() {
        val oneCellOnly =
            "bidvector.koneps.opening.operations.construction.base-amount-path=" +
                "getBidPblancListInfoCnstwkBsisAmount"
        val booted = bootOpeningBranch(oneCellOnly)
        try {
            booted.failure shouldNotBe null
        } finally {
            booted.context.close()
        }
    }

    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun bootNoticeBranch(vararg properties: String): Booted =
        boot(CollectionWiring::class.java, noticeApproval(), properties)

    private fun bootOpeningBranch(vararg properties: String): Booted =
        boot(OpeningCollectionWiring::class.java, openingApproval(), properties)

    private fun boot(
        wiring: Class<*>,
        approval: Array<String>,
        properties: Array<out String>,
    ): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*approval, *properties).applyTo(context)
        context.registerBean(CollectedAxisStore::class.java, Supplier { RollbackAxisStore })
        context.register(wiring)
        context.registerBean(Clock::class.java, Supplier { Clock { fixedNow } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        return Booted(context, failure)
    }

    private fun noticeApproval() =
        arrayOf(
            "bidvector.collection.mode=once",
            "bidvector.collection.from=2026-10-06",
            "bidvector.collection.to=2026-11-05",
            "bidvector.collection.categories=construction,service",
            "bidvector.collection.calls-per-day=100",
            "bidvector.collection.calls-total=100",
            "bidvector.collection.run-state-dir=${newRunStateDir()}",
            "bidvector.koneps.service-key=ROLLBACK-TEST-KEY",
        )

    private fun openingApproval() =
        arrayOf(
            "bidvector.opening-collection.mode=once",
            "bidvector.opening-collection.from=2026-11-01",
            "bidvector.opening-collection.to=2026-11-03",
            "bidvector.opening-collection.categories=construction,service",
            "bidvector.opening-collection.sampling-seed=portal-02-rollback",
            "bidvector.opening-collection.sample-size=2",
            "bidvector.opening-collection.calls-per-day=100",
            "bidvector.opening-collection.calls-total=1000",
            "bidvector.opening-collection.run-state-dir=${newRunStateDir()}",
            "bidvector.koneps.service-key=ROLLBACK-TEST-KEY",
        )

    private fun newRunStateDir(): Path = Files.createTempDirectory("portal-02-rollback-run-state")

    private fun failureText(failure: Throwable?): String =
        generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
}
