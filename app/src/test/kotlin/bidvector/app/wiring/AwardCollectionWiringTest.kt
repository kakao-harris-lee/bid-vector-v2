package bidvector.app.wiring

import bidvector.app.collection.AwardCollectionRunner
import bidvector.procurement.CallBudgetLedger
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.function.Supplier
import javax.sql.DataSource

/** 저장소 밖 실행 상태 — 배선은 디렉터리가 **있는지**만 본다. */
private val AWARD_WIRING_RUN_STATE: Path = Files.createTempDirectory("m9-award-wiring-run-state")

/** relay 러너를 켜는 최소 설정 — 일회 러너가 둘이 되는 조합을 짓는 데만 쓴다. */
private val RELAY_ON_FOR_AWARD =
    arrayOf(
        "bidvector.relay.mode=once",
        "bidvector.relay.environment=development",
        "bidvector.relay.owner=relay-operator",
        "bidvector.relay.channel=telegram",
        "bidvector.relay.claim-limit=50",
    )

/**
 * M9/award-ingest D-1·D-2·D-3 — 낙찰 목록 배선의 **켜짐 조건과 기동 실패**.
 *
 * 잠그는 것: ① `mode=once` 가 아니면 이 갈래가 아예 올라오지 않는다(끄는 법이 한 줄이다) ② 켜졌을 때
 * 승인 값(기간·상한 둘·실행 상태·서비스 키)이 하나라도 없으면 **기동하지 않는다** ③ 조각 상한을 넘는
 * 창은 기동 거부다 ④ 다른 일회 러너와 함께 켜면 기동이 실패한다(먼저 끝난 러너가 JVM 을 끝내 나머지가
 * 조용히 돌지 않는 배포를 기동 자리에서 막는다).
 */
class AwardCollectionWiringTest {
    private val fixedNow = Instant.parse("2026-10-21T03:00:00Z")

    private val approved =
        arrayOf(
            "bidvector.award-collection.mode=once",
            "bidvector.award-collection.from=2026-10-19",
            "bidvector.award-collection.to=2026-10-20",
            "bidvector.award-collection.categories=construction,service",
            "bidvector.award-collection.calls-per-day=100",
            "bidvector.award-collection.calls-total=1000",
            "bidvector.award-collection.run-state-dir=$AWARD_WIRING_RUN_STATE",
            "bidvector.koneps.service-key=AWARD-WIRING-TEST-KEY",
        )

    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(vararg properties: String): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(AwardCollectionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { fixedNow } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        return Booted(context, failure)
    }

    private fun bootWithout(key: String): Booted = boot(*approved.filterNot { it.startsWith("$key=") }.toTypedArray())

    /** 승인 설정에서 한 항목만 바꿔 넣는다 — 하나만 넘기면 나머지가 없어 조건 자체가 서지 않는다. */
    private fun bootWith(property: String): Booted =
        boot(*approved.filterNot { it.startsWith(property.substringBefore('=') + "=") }.toTypedArray(), property)

    @Test
    fun `mode 가 없으면 이 갈래는 아예 올라오지 않는다 — 서비스 키가 없어도 뜬다`() {
        val booted = boot("bidvector.award-collection.from=2026-10-19")

        booted.failure shouldBe null
        booted.context.use { it.getBeanNamesForType(ApplicationRunner::class.java).toList() }.shouldBeEmpty()
    }

    @Test
    fun `승인 값이 다 있으면 러너와 예산 원장이 선다`() {
        val booted = boot(*approved)

        booted.failure shouldBe null
        booted.context.use { context ->
            context.getBean(AwardCollectionRunner::class.java) shouldNotBe null
            context.getBean(CallBudgetLedger::class.java) shouldNotBe null
        }
    }

    @Test
    fun `호출 상한이 없으면 기동하지 않는다 — 기본값을 지어내지 않는다`() {
        bootWithout("bidvector.award-collection.calls-per-day").failure shouldNotBe null
        bootWithout("bidvector.award-collection.calls-total").failure shouldNotBe null
    }

    @Test
    fun `기간 창이 없으면 기동하지 않는다`() {
        bootWithout("bidvector.award-collection.from").failure shouldNotBe null
        bootWithout("bidvector.award-collection.to").failure shouldNotBe null
    }

    @Test
    fun `실행 상태 디렉터리가 없거나 저장소 안이면 기동하지 않는다`() {
        bootWithout("bidvector.award-collection.run-state-dir").failure shouldNotBe null
        bootWith("bidvector.award-collection.run-state-dir=reports/evidence").failure shouldNotBe null
    }

    @Test
    fun `서비스 키가 없으면 기동하지 않는다`() {
        bootWithout("bidvector.koneps.service-key").failure shouldNotBe null
    }

    @Test
    fun `등재되지 않은 업종이면 기동하지 않는다 — 경로를 지어내지 않는다`() {
        bootWith("bidvector.award-collection.categories=goods").failure shouldNotBe null
    }

    @Test
    fun `평문 http 외부 호스트는 기동 실패다 — 서비스 키가 쿼리로 나간다`() {
        boot(*approved, "bidvector.koneps.award.scsbid-base-url=http://koneps.example.com/api")
            .failure shouldNotBe null
    }

    /**
     * 조각 상한(60일)은 **이 갈래의 값**이다 — 호출 수가 창 길이에 비례하므로 한 조각이 하루 승인 상한
     * 안에서 끝나야 한다. 경계 양쪽을 재 둔다: 상한을 조용히 올리면 이 단언이 붉어진다.
     */
    @Test
    fun `조각 상한 정확히(60일)는 뜨고 하루 더 넓으면 기동하지 않는다 — 경계 양쪽`() {
        bootWith("bidvector.award-collection.from=$AWARD_SPAN_60_DAYS_FROM").failure shouldBe null
        bootWith("bidvector.award-collection.from=$AWARD_SPAN_61_DAYS_FROM").failure shouldNotBe null
    }

    /**
     * 일회 러너 둘은 **기동 실패**다(PR #63 finding 1 의 guard). 이 갈래가 러너를 하나 더 세우므로
     * 그 guard 가 새 러너를 **등재 없이** 세는지 여기서 잰다 — guard 는 러너의 종류를 모른다.
     */
    @Test
    fun `다른 일회 러너와 함께 켜면 기동하지 않는다`() {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*approved, *RELAY_ON_FOR_AWARD).applyTo(context)
        context.register(
            OneShotRunnerGuard::class.java,
            AwardCollectionWiring::class.java,
            RelayWiring::class.java,
        )
        context.registerBean(Clock::class.java, Supplier { Clock { fixedNow } })
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        context.close()

        requireNotNull(failure).toString() shouldContain "일회 러너는 한 프로세스에 하나만 켠다"
    }
}

/** `approved` 의 `to`(2026-10-20)에서 정확히 60일 — 조각 상한의 경계다. */
private const val AWARD_SPAN_60_DAYS_FROM = "2026-08-21"

/** 같은 `to` 에서 61일 — 경계 바로 밖이다. */
private const val AWARD_SPAN_61_DAYS_FROM = "2026-08-20"
