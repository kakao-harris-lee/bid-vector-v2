package bidvector.app.wiring

import bidvector.app.collection.CollectionTermination
import bidvector.workflow.strategy.Clock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** 프로세스를 끝내지 않고 종료 코드만 기록하는 종료 자리 — E2E 가 러너의 끝을 관측한다. */
class RecordingCollectionTermination : CollectionTermination {
    val exitCodes = CopyOnWriteArrayList<Int>()

    override fun terminate(exitCode: Int) {
        exitCodes += exitCode
    }
}

/**
 * D-6F8-3 E2E 전용 배선 — production `CollectionWiring.collectionTermination`(`exitProcess`)은 test JVM 을
 * 죽이므로 이 profile 에서만 기록형 종료로 대체한다(`BidNowFakeMlAnalysisTestConfiguration` 과 같은 잠금:
 * profile 이 없으면 `@Bean` 이 하나도 등록되지 않는다). production 배선에 주입 자리를 새로 열지 않는다.
 */
@TestConfiguration
@Profile("collection-e2e")
open class CollectionTerminationTestConfiguration {
    @Bean
    @Primary
    open fun recordingCollectionTermination(): RecordingCollectionTermination = RecordingCollectionTermination()
}

/**
 * E2E 가 기동 전에 못 박는 시각 — 못 박지 않으면 출하와 같은 실시간이다. KST 하루 경계(상한의
 * 「오늘」)는 시계를 잡지 않고는 잴 수 없다.
 */
val E2E_FIXED_NOW = AtomicReference<Instant?>(null)

/**
 * 시계만 덮는 E2E 배선(D-6G-56) — 출하 조립의 나머지는 그대로다. `@TestConfiguration` 이라
 * 컴포넌트 스캔에 걸리지 않고, 기동 소스로 **명시한 test 에서만** 올라온다.
 */
@TestConfiguration
@Profile("collection-e2e")
open class FixedClockTestConfiguration {
    @Bean
    @Primary
    open fun fixedClock(): Clock = Clock { E2E_FIXED_NOW.get() ?: Instant.now() }
}
