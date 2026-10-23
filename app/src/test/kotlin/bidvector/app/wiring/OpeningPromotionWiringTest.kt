package bidvector.app.wiring

import bidvector.app.collection.OpeningPromotionRunner
import bidvector.workflow.collection.PromoteOpeningResultsUseCase
import bidvector.workflow.strategy.Clock
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.time.Instant
import java.util.function.Supplier
import javax.sql.DataSource

private const val CANONICAL_OPENING_REPOSITORY = "bidvector.adapters.persistence.JdbcOpeningResultRepository"
private const val OPENING_RAW_AXIS_SOURCE = "bidvector.adapters.persistence.JdbcOpeningRawAxisSource"
private const val PROMOTION_WIRING = "bidvector.app.wiring.OpeningPromotionWiring"

/**
 * M7/7C⑤-0 — 승격 배선의 켜짐 조건과 **생성 자리 등식**.
 *
 * ① canonical 개찰 저장소를 **production 에서 만드는 자리가 이 배선 하나**다. 이 slice 전까지 그
 * 집합은 **비어 있었다**(V4·V5 의 슬롯에 쓰기 경로가 없었다 — 7C⑤ checklist §0.1 실측). 등식으로
 * 두는 이유: 둘째 자리가 생기면 「개찰 결과를 쓰는 곳」이 둘이 되고, 걷기 선택 규율을 지나지 않는
 * 쓰기가 조용히 생길 수 있다.
 * ② 읽기 어댑터도 같은 자리에서만 선다.
 * ③ 러너는 `bidvector.opening-promote.mode=once` 일 때만 빈이다 — 속성이 없으면 이 설정이 기여하는
 * 빈이 **하나도 없다**(끄는 법이 그 한 줄이다, rollback).
 */
class OpeningPromotionWiringTest {
    private fun productionClasses() =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("bidvector")

    private fun constructorCallersOf(target: String): List<String> =
        productionClasses()
            .flatMap { clazz -> clazz.constructorCallsFromSelf }
            .filter { call -> call.targetOwner.name == target }
            .map { call -> call.originOwner.name }
            .distinct()
            .sorted()

    @Test
    fun `canonical 개찰 저장소를 만드는 production 자리는 승격 배선 하나다`() {
        constructorCallersOf(CANONICAL_OPENING_REPOSITORY) shouldContainExactly listOf(PROMOTION_WIRING)
    }

    @Test
    fun `개찰 원문 읽기 어댑터를 만드는 production 자리도 승격 배선 하나다`() {
        constructorCallersOf(OPENING_RAW_AXIS_SOURCE) shouldContainExactly listOf(PROMOTION_WIRING)
    }

    @Test
    fun `mode 속성이 없으면 승격 배선은 빈을 하나도 기여하지 않는다`() {
        val context = AnnotationConfigApplicationContext()
        context.registerBean(Clock::class.java, Supplier { Clock { Instant.EPOCH } })
        context.register(OpeningPromotionWiring::class.java)
        context.refresh()

        context.use {
            it.getBeansOfType(ApplicationRunner::class.java).keys.shouldBeEmpty()
            it.getBeanNamesForType(Any::class.java)
                .filter { name -> name.startsWith("openingPromotion") || name.startsWith("promoteOpening") }
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode 속성이 켜지면 러너와 use case 가 선다`() {
        val runStateDir = Files.createTempDirectory("7c5p-promote-run-state")
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues
            .of(
                "bidvector.opening-promote.mode=once",
                "bidvector.opening-promote.run-state-dir=$runStateDir",
                "bidvector.opening-promote.reference-date=2026-10-23",
            ).applyTo(context)
        context.registerBean(Clock::class.java, Supplier { Clock { Instant.EPOCH } })
        // 연결하지 않는다 — 배선은 조립만 하고 질의는 러너가 돌린다(형제 배선 test 의 같은 관례).
        context.registerBean(DataSource::class.java, Supplier { unconnectedDataSource() })
        context.register(OpeningPromotionWiring::class.java)
        context.refresh()

        context.use {
            it.getBeansOfType(ApplicationRunner::class.java).values.map { bean -> bean::class.java.name } shouldBe
                listOf(OpeningPromotionRunner::class.java.name)
            it.getBean(PromoteOpeningResultsUseCase::class.java)
        }
    }

    private fun unconnectedDataSource(): DataSource =
        PGSimpleDataSource().apply {
            setURL("jdbc:postgresql://127.0.0.1:1/none")
            user = "none"
            password = "none"
        }
}
