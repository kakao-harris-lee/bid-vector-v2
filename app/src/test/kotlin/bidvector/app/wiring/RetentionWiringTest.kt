package bidvector.app.wiring

import bidvector.adapters.retention.JdbcRetentionStore
import bidvector.app.retention.RetentionRunner
import bidvector.workflow.retention.PurgeExpiredData
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
import java.util.function.Supplier
import javax.sql.DataSource

private const val OWNER_DATA_SOURCE_TYPE = "org.postgresql.ds.PGSimpleDataSource"
private const val RETENTION_CUTOFF_TYPE = "bidvector.workflow.retention.RetentionCutoff"

/**
 * **소유자 권한 연결을 쥐어도 되는 자리**(설계 검토 (2b) 「소유자 연결 팩토리」) — 둘뿐이다.
 * 하나는 migration(`PersistenceWiring.migrateAsOwner`), 하나는 보존(`RetentionWiring`)이다.
 * 셋째가 생기면 이 등식이 붉어진다.
 */
private val OWNER_CONNECTION_HOLDERS =
    listOf("bidvector.app.wiring.PersistenceWiring", "bidvector.app.wiring.RetentionWiring")

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

    private fun boot(vararg properties: String): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(RetentionWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { Instant.EPOCH } })
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
        block: (Booted) -> T,
    ): T {
        val booted = boot(*properties)
        return try {
            block(booted)
        } finally {
            booted.context.close()
        }
    }

    @Test
    fun `mode 가 없으면 러너 빈이 없다 — 기본 꺼짐`() {
        withBoot { booted ->
            booted.failure shouldBe null
            booted.context.getBeanNamesForType(ApplicationRunner::class.java).toList().shouldBeEmpty()
        }
    }

    @Test
    fun `mode 가 다른 값이면 러너 빈이 없다`() {
        withBoot("bidvector.retention.mode=dry-run") { booted ->
            booted.failure shouldBe null
            booted.context.getBeanNamesForType(ApplicationRunner::class.java).toList().shouldBeEmpty()
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

    @Test
    fun `소유자 연결은 빈이 아니다 — 컨텍스트에서 주입받을 자리가 없다`() {
        withBoot("bidvector.retention.mode=once") { booted ->
            booted.context.getBeanNamesForType(DataSource::class.java).toList().shouldBeEmpty()
        }
    }

    @Test
    fun `파기 능력도 빈이 아니다 — use case 와 저장소를 주입받을 자리가 없다`() {
        withBoot("bidvector.retention.mode=once") { booted ->
            booted.context.getBeanNamesForType(PurgeExpiredData::class.java).toList().shouldBeEmpty()
            booted.context.getBeanNamesForType(JdbcRetentionStore::class.java).toList().shouldBeEmpty()
        }
    }

    @Test
    fun `소유자 연결 타입을 참조하는 production 클래스는 배선 둘뿐이다`() {
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
