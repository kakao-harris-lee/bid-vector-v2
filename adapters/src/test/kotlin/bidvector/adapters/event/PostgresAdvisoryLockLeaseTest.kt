package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.OutboxConsumerKind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * lease 의 상호 배제를 **실 DB 두 연결**로 잰다(D-6F10-21) — fake 로는 잴 수 없는 사실이다.
 * 「정직하지 않은 어댑터가 `Held` 를 거짓으로 줄 수 있다」는 배선 주체가 이미 가진 권한이고,
 * 이 test 가 재는 것은 **정직한 어댑터가 실제로 배제하는가**다.
 *
 * 왜 세션 advisory lock 인가: 자물쇠를 **지키는 것과 같은 범위**에 걸어야 한다. 고아 판정의
 * 범위는 DB(「이 DB 의 `CLAIMED` 행」)이므로 파일 잠금(프로세스·호스트 범위)으로는 다른
 * 호스트의 두 relay 가 둘 다 `Held` 를 받아 서로의 in-flight 행을 격리한다.
 */
class PostgresAdvisoryLockLeaseTest : PersistenceTestSupport() {
    @Test
    fun `임대를 쥔 동안 두 번째 획득은 Busy 이고 본문을 부르지 않는다`() {
        val lease = PostgresAdvisoryLockLease(dataSource())
        var innerCalled = false
        var innerAttempt: LeaseAttempt<Unit>? = null

        val outer =
            lease.withLease(KIND) {
                // 같은 어댑터·같은 DataSource 지만 **다른 연결**이다 — 두 프로세스의 대역.
                innerAttempt =
                    PostgresAdvisoryLockLease(dataSource()).withLease(KIND) {
                        innerCalled = true
                    }
                "held"
            }

        outer.shouldBeInstanceOf<LeaseAttempt.Held<String>>().result shouldBe "held"
        innerAttempt shouldBe LeaseAttempt.Busy
        innerCalled shouldBe false
    }

    @Test
    fun `본문이 끝나면 임대가 풀려 다음 획득이 성공한다`() {
        val lease = PostgresAdvisoryLockLease(dataSource())

        lease.withLease(KIND) { "first" }.shouldBeInstanceOf<LeaseAttempt.Held<String>>()
        val second = lease.withLease(KIND) { "second" }

        second.shouldBeInstanceOf<LeaseAttempt.Held<String>>().result shouldBe "second"
    }

    /** 본문이 던져도 임대가 남지 않는다 — 「홀더 사망 뒤 영구 점유」가 이 slice 의 최악이다. */
    @Test
    fun `본문이 예외로 끝나도 임대가 풀린다`() {
        val lease = PostgresAdvisoryLockLease(dataSource())

        // `runCatching` 을 쓰지 않는다 — 무엇이든 삼켜 다른 실패를 감춘다(PR #62 review J).
        try {
            lease.withLease(KIND) { error("본문 실패") }
        } catch (
            @Suppress("SwallowedException") expected: IllegalStateException,
        ) {
            // 의도한 본문 실패만 삼킨다.
        }

        lease.withLease(KIND) { "after" }.shouldBeInstanceOf<LeaseAttempt.Held<String>>()
    }

    /** D-6F10-13 — 종류마다 자물쇠가 다르다. 한 종류의 소비자가 다른 종류를 막지 않는다. */
    @Test
    fun `종류가 다르면 서로를 막지 않는다`() {
        val lease = PostgresAdvisoryLockLease(dataSource())
        var otherKindHeld = false

        lease.withLease(OutboxConsumerKind.NotificationRequested) {
            PostgresAdvisoryLockLease(dataSource())
                .withLease(OutboxConsumerKind.StrategyUpdated) { otherKindHeld = true }
        }

        otherKindHeld shouldBe true
    }
}

private val KIND = OutboxConsumerKind.NotificationRequested
