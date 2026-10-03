package bidvector.app.wiring

import bidvector.app.collection.CollectionTermination
import bidvector.workflow.strategy.Clock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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
 *
 * **못 박은 시각도 읽을 때마다 전진한다**(D-6G2c-19 (c) = cr r5-t L-4). 걷기의 이름은 시계 값을
 * 마이크로초로 절삭한 것이므로(D-6G-80), 시각을 한 값으로 고정하면 **서로 다른 걷기가 같은 이름**을
 * 갖는다. 그러면 (공고, 축)마다 한 걷기만 쓰는 선별이 두 걷기를 하나로 보고 행을 합치고 — 행이
 * 늘 뿐 오류가 없어 아무 데서도 붉어지지 않는다. 전진 폭은 **마이크로초 하나**다: 절삭을 지나 이름이
 * 갈리면서도 하루 경계(상한의 「오늘」)는 움직이지 않는다. 그 경계를 재는 test 가 이 시계를 쓴다.
 *
 * 전진을 production 쪽(`walkNameOf`)에 넣지 않는다 — 출하 시계는 이미 나노초를 주므로 고칠 것이
 * 없고, 고치면 harness 의 결함을 출하 코드로 덮는 것이 된다.
 */
@TestConfiguration
@Profile("collection-e2e")
open class FixedClockTestConfiguration {
    @Bean
    @Primary
    open fun fixedClock(): Clock = Clock { E2E_FIXED_NOW.get()?.let(::advancedFromPinned) ?: Instant.now() }
}

/** 못 박은 시각에서 읽은 횟수 — 같은 JVM 의 모든 기동이 공유한다(걷기 이름이 run 을 넘어 갈린다). */
private val PINNED_CLOCK_READS = AtomicLong(0)

private fun advancedFromPinned(pinned: Instant): Instant =
    pinned.plus(PINNED_CLOCK_READS.getAndIncrement(), ChronoUnit.MICROS)
