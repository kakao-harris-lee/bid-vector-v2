package bidvector.app

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.context.annotation.ComponentScan

/**
 * `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` 폐쇄 판정(D-6A2b-9). 명시
 * `@ComponentScan` 은 `@SpringBootApplication` 의 메타 선언을 **대체**하므로, 6A-1 이
 * 중첩 조립을 빼려고 그 애너테이션을 붙이면서 Boot 기본 필터 둘이 함께 사라졌다.
 *
 * 판정은 **집합 등식**이다 — 「둘이 들어 있다」(부분집합)로 재면 나중에 누가 예외 필터를
 * 더해 스캔 모집단을 조용히 넓혀도 초록이다. 세 필터가 **정확히** 그 셋인지를 본다.
 *
 * 되살린 뒤 빈 집합이 그대로인지는 이 단언이 아니라 실 조립을 부팅하는 test 들이 잰다
 * (`ProductionAssemblyAuthAuditTest`·`StrategyEditProductionE2ETest` — 필터가 우리 빈을
 * 걷어냈다면 그 부팅이 곧바로 실패한다).
 */
class ComponentScanFilterTest {
    @Test
    fun `출하 조립의 스캔 제외 필터는 Boot 기본 둘과 중첩 조립 하나다 — 집합 등식`() {
        val componentScan =
            requireNotNull(BidVectorApplication::class.java.getAnnotation(ComponentScan::class.java)) {
                "출하 조립에 명시 @ComponentScan 이 없다"
            }

        val declared =
            componentScan.excludeFilters
                .flatMap { filter -> filter.classes.map { it.java.name to filter.type } }
                .toSet()

        declared shouldBe
            setOf(
                TypeExcludeFilter::class.java.name to org.springframework.context.annotation.FilterType.CUSTOM,
                AutoConfigurationExcludeFilter::class.java.name to
                    org.springframework.context.annotation.FilterType.CUSTOM,
                SpringBootApplication::class.java.name to
                    org.springframework.context.annotation.FilterType.ANNOTATION,
            )
    }
}
