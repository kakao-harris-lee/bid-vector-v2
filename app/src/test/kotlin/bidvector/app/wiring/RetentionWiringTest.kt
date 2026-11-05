package bidvector.app.wiring

import bidvector.adapters.retention.JdbcRetentionStore
import bidvector.app.retention.RetentionRunner
import bidvector.sharedkernel.Resolution
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionCutoff
import bidvector.workflow.strategy.Clock
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.function.Supplier
import javax.sql.DataSource

private const val OWNER_DATA_SOURCE_TYPE = "org.postgresql.ds.PGSimpleDataSource"
private const val RETENTION_CUTOFF_TYPE = "bidvector.workflow.retention.RetentionCutoff"

/**
 * **소유자 권한 연결을 쥐어도 되는 자리**(설계 검토 (2b) 「소유자 연결 팩토리」) — **셋**이다.
 * migration(`PersistenceWiring.migrateAsOwner`) · 보존(`RetentionWiring`) · 계정 명령
 * (`AccountCommandWiring`, M7/7D)이다. 넷째가 생기면 이 등식이 붉어진다.
 *
 * **셋째의 근거**: 계정 생성과 비밀 문구 교체는 **앱 역할이 권한을 갖지 않는 일**이다(V20 의
 * 열 단위 GRANT — 앱 역할은 `account` 에 INSERT 가 없고 해시 열에 UPDATE 가 없다). 그래서 그
 * 명령만 소유자 연결로 돌고, 그 배선은 **조건부**다(`bidvector.account.mode` 가 없으면 설정
 * 자체가 올라오지 않는다 — 평소 기동에는 이 자리가 존재하지 않는다). 연결은 `PersistenceWiring`
 * 과 같은 관례로 **지역 객체**이고 빈으로 노출되지 않는다.
 */
private val OWNER_CONNECTION_HOLDERS =
    listOf(
        "bidvector.app.wiring.PersistenceWiring",
        "bidvector.app.wiring.RetentionWiring",
        "bidvector.app.wiring.AccountCommandWiring",
    )

/**
 * 기본 boot 가 정책을 고르는 기준일 — 그 컨텍스트의 시계가 `Instant.EPOCH` 이므로 배선은 그 날로
 * 해소한다(배선이 시스템 시계를 쓰지 않는다는 것이 cr L-4 의 조치이고, 이 상수가 그 사실에 기댄다).
 * 구역을 **배선에서 읽어 오지 않는다**(앵커 독립과 같은 이유). 그럴 필요도 없다 — EPOCH 의 날짜는
 * UTC 와 KST 에서 같은 1970-01-01 이라 이 상수는 어느 쪽으로 계산해도 같은 값이고, 그래서 **이
 * 등식만으로는 구역 축도, 어느 시대로 해소하는가도 잴 수 없다**. 그 둘은 아래 리터럴 단언과 경계
 * test 가 잰다.
 */
private val BOOT_DATE: LocalDate = LocalDate.ofInstant(Instant.EPOCH, ZoneOffset.UTC)

/** 개정일(2026-11-05 KST)의 **직전 1초**. UTC 날짜로 해소하면 11-04 이므로 옛 값이 선다. */
private val JUST_BEFORE_REVISION_KST: Instant = Instant.parse("2026-11-04T14:59:59Z")

/** 개정일의 **첫 순간**(KST 00:00) — UTC 로는 아직 11-04 다. 이 한 초가 두 값을 가른다. */
private val FIRST_INSTANT_OF_REVISION_KST: Instant = Instant.parse("2026-11-04T15:00:00Z")

/**
 * 승인된 두 값을 **리터럴로** 둔다(v H-1). 정책에서 읽어 오면 「배선이 어느 시대로 해소하는가」를
 * 재는 등식의 양변이 함께 움직여 아무것도 가르지 않는다 — 배선을 옛 날짜 고정으로 바꾼 변이가
 * 바로 그 모양에서 살아남았다.
 */
private const val DAYS_BEFORE_REVISION = 90
private const val DAYS_FROM_REVISION = 365

/**
 * M6/6B-3 P-3 — 보존 배선의 켜짐 조건과 **표면**.
 *
 * ① 러너는 `bidvector.retention.mode=once` 일 때만 빈이다(기본 꺼짐 — compose 는 `*.mode` 를
 * 설정하지 않는다) ② 켜져도 빈은 **러너 하나**다: 소유자 `DataSource`·저장소·use case 가
 * 컨텍스트에 없으므로 다른 빈이 「파기 능력」이나 「소유자 권한 연결」을 주입받을 자리가 없다
 * ③ 소유자 연결 타입을 참조하는 production 클래스 집합이 둘로 닫혀 있다.
 */
class RetentionWiringTest {
    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(
        vararg properties: String,
        now: Instant = Instant.EPOCH,
    ): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(RetentionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { now } })
        context.registerBean(
            PersistenceProperties::class.java,
            Supplier {
                // 연결하지 않는다 — 배선은 조립만 하고 질의는 러너가 refresh 뒤에 돌린다.
                PersistenceProperties("jdbc:postgresql://127.0.0.1:1/none", "none", "none")
            },
        )
        val failure =
            try {
                context.refresh()
                null
            } catch (
                @Suppress("TooGenericExceptionCaught") thrown: Exception,
            ) {
                thrown
            }
        return Booted(context, failure)
    }

    private fun <T> withBoot(
        vararg properties: String,
        now: Instant = Instant.EPOCH,
        block: (Booted) -> T,
    ): T {
        val booted = boot(*properties, now = now)
        return try {
            block(booted)
        } finally {
            booted.context.close()
        }
    }

    /** `mode=once` 로 띄운 배선이 러너에 쥐여 준 보존 기간 — 조립을 지난 **그 한 벌**의 값이다. */
    private fun retentionDaysAt(now: Instant): Int =
        withBoot("bidvector.retention.mode=once", now = now) { booted ->
            booted.context.getBean(RetentionRunner::class.java).purge.policy.retentionDays
        }

    @Test
    fun `mode 가 없으면 러너 빈이 없다 — 기본 꺼짐`() {
        withBoot { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(ApplicationRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode 가 다른 값이면 러너 빈이 없다`() {
        withBoot("bidvector.retention.mode=dry-run") { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(ApplicationRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode=once 면 보존 러너 하나가 올라온다`() {
        withBoot("bidvector.retention.mode=once") { booted ->
            booted.failure shouldBe null
            booted.context.getBeanNamesForType(RetentionRunner::class.java).size shouldBe 1
            booted.context.getBeanNamesForType(ApplicationRunner::class.java).size shouldBe 1
        }
    }

    /**
     * **vr F-1(운반)** — 출하 배선이 조립한 러너가 쥔 정책이 `RETENTION_POLICY` 해석값과 **같다**.
     *
     * 이 등식이 없을 때 배선을 보존 1일로 바꾼 변이(V-1)가 app test 전건을 초록으로 지났다:
     * 값→거동(경계 ±1초)은 adapters 쪽에서 `productionPolicy()` 를 **직접** 넣어 재므로 배선을
     * 우회하고, 러너 test 는 stub 이고, 배선 test 는 조립만 봤다 — 되돌릴 수 없는 파기 경로의
     * 지배 값에 아무 술어도 걸려 있지 않았다. 이제 조립 안의 정책 값은 **한 벌**이고
     * (`RetentionRunner` 가 보존 기간을 따로 받지 않는다) 그 한 벌을 여기서 센다.
     */
    @Test
    fun `러너가 쥔 정책은 승인된 보존 정책 해석값이다 — 배선이 값을 바꿀 수 없다`() {
        withBoot("bidvector.retention.mode=once") { booted ->
            val runner = booted.context.getBean(RetentionRunner::class.java)

            runner.purge.policy shouldBe (RETENTION_POLICY.resolve(BOOT_DATE) as Resolution.Resolved).value
        }
    }

    /**
     * **v H-1** — 위 등식은 「배선이 주입된 시계로 해소한다」만 재고 **어느 시대로 해소하는가**는 재지
     * 않는다(기본 boot 의 EPOCH 에서는 양변이 둘 다 90 이다). 그래서 배선의 해소를 개정 앞 날짜로
     * 고정한 변이가 app test 전건을 초록으로 지났다 — 되돌릴 수 없는 파기 경로의 지배 값에 걸린
     * 술어가 그 변이를 보지 못했다. 이 test 는 **개정일 이후 시계로 한 번 더 띄우고** 승인된 값을
     * **리터럴로** 단언한다.
     */
    @Test
    fun `개정일 이후 시계로 띄운 배선은 1년을 쥔다 — 리터럴 앵커`() {
        retentionDaysAt(Instant.parse("2026-11-05T00:00:00Z")) shouldBe DAYS_FROM_REVISION
    }

    /**
     * **D-8** — 개정일은 **KST 달력일**로 판정한다. 이 한 초가 두 값을 가르고, UTC 로 해소하면 둘 다
     * 90 이 된다(그 판에서는 runbook 의 04:10 KST cron 이 **언제나** 어제 날짜로 해소해 개정일 당일에도
     * 옛 값으로 돌았다). 두 행이 짝이라 **구역을 UTC 로 되돌리면 아래쪽이 RED** 다.
     */
    @Test
    fun `개정일 직전 1초(KST)는 아직 90일이다`() {
        retentionDaysAt(JUST_BEFORE_REVISION_KST) shouldBe DAYS_BEFORE_REVISION
    }

    @Test
    fun `개정일 첫 순간(KST)부터 1년이다 — UTC 날짜로는 아직 전날이다`() {
        retentionDaysAt(FIRST_INSTANT_OF_REVISION_KST) shouldBe DAYS_FROM_REVISION
    }

    @Test
    fun `소유자 연결은 빈이 아니다 — 컨텍스트에서 주입받을 자리가 없다`() {
        withBoot("bidvector.retention.mode=once") { booted ->
            booted.context
                .getBeanNamesForType(DataSource::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `파기 능력도 빈이 아니다 — use case 와 저장소를 주입받을 자리가 없다`() {
        withBoot("bidvector.retention.mode=once") { booted ->
            booted.context
                .getBeanNamesForType(PurgeExpiredData::class.java)
                .toList()
                .shouldBeEmpty()
            booted.context
                .getBeanNamesForType(JdbcRetentionStore::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `소유자 연결 타입을 참조하는 production 클래스는 등재된 배선뿐이다`() {
        val holders =
            productionClasses()
                .filter { candidate ->
                    candidate.directDependenciesFromSelf.any { it.targetClass.fullName == OWNER_DATA_SOURCE_TYPE }
                }.map { it.fullName }

        holders shouldContainExactlyInAnyOrder OWNER_CONNECTION_HOLDERS
    }

    /**
     * cutoff 를 **만드는** 자리는 `workflow` 안에만 있다([RetentionCutoff] 의 생성자가
     * `internal`). 그 사실을 바이트코드에서도 센다 — `adapters`·`app` 가 그 생성자를 부르면
     * 「`now` 를 cutoff 로 넘겨 전부 파기」가 가능해진다.
     */
    @Test
    fun `cutoff 생성자를 부르는 production 클래스는 workflow 밖에 없다`() {
        val callers =
            productionClasses()
                .filter { candidate ->
                    candidate.constructorCallsFromSelf.any { it.targetOwner.fullName == RETENTION_CUTOFF_TYPE }
                }.map { it.fullName }
                .filterNot { it.startsWith("bidvector.workflow.retention.") }

        callers.shouldBeEmpty()
    }

    private fun productionClasses() =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages("bidvector")
}
