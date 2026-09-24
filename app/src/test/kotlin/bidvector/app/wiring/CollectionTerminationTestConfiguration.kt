package bidvector.app.wiring

import bidvector.app.collection.CollectionTermination
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.util.concurrent.CopyOnWriteArrayList

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
