package bidvector.app.compatibility

import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.tngtech.archunit.core.importer.ClassFileImporter
import io.github.resilience4j.retry.Retry
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootVersion
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * `v2-지침서.md` §5 「Kotlin」의 `OPEN-OPS-07` 항목이 1A 에 지운 실측의 **두 번째·세 번째 층**이다. `compatibilitySmoke`
 * task 가 「해석되는가」를 재고, 이 테스트가 「그 버전의 API 로 **컴파일되는가**」와 「JDK 21 에서
 * **로드·초기화되는가**」를 잰다. 해석만 재면 조사 노트의 3.x 전제가 그대로 남는다(Codex #4).
 *
 * 각 호출은 그 라이브러리의 대표 진입점을 건드리되 **부작용이 없는 것**만 고른다 — 스케줄을
 * 돌리지 않고, migration 을 실행하지 않고, 컨테이너를 띄우지 않는다(scope.md D-7).
 * 채택 일곱 중 detekt 는 Gradle plugin 이라 여기 없다 — `check` 가 detekt task 를 돌리는 것이
 * 그 라이브러리의 같은 증거다.
 */
class BootCompatibilitySmokeTest {
    @Test
    fun `Spring Boot 4 가 로드되고 자기 버전을 안다`() {
        SpringBootVersion.getVersion().orEmpty().shouldNotBeBlank()
    }

    @Test
    fun `db-scheduler 가 작업을 정의한다 — 실행하지 않는다`() {
        Tasks.oneTime("compatibility-smoke").execute { _, _ -> } shouldNotBe null
    }

    @Test
    fun `Resilience4j 가 기본 retry 를 만든다`() {
        Retry.ofDefaults("compatibility-smoke").retryConfig shouldNotBe null
    }

    @Test
    fun `Micrometer registry 가 선다`() {
        val registry = SimpleMeterRegistry()
        try {
            registry.counter("compatibility.smoke") shouldNotBe null
        } finally {
            registry.close()
        }
    }

    @Test
    fun `Flyway 설정이 만들어진다 — DB 에 붙지 않는다`() {
        Flyway
            .configure()
            .locations
            .toList()
            .shouldNotBeEmpty()
    }

    @Test
    fun `Testcontainers PostgreSQL 타입이 로드된다 — 컨테이너를 띄우지 않는다`() {
        // 2.x 에서 좌표가 `org.testcontainers:testcontainers-postgresql` 로, 패키지가
        // `org.testcontainers.postgresql` 로 옮겼다. 1.x 좌표는 1.21.4 에서 멈춰 있다.
        PostgreSQLContainer::class.java.name shouldNotBe null
    }

    @Test
    fun `ArchUnit importer 가 선다`() {
        ClassFileImporter().importPackages("bidvector.app") shouldNotBe null
    }
}
