package bidvector.adapters.retention

import bidvector.adapters.event.PostgresAdvisoryLockLease
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.OutboxConsumerKind
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 보존 임대 키가 **다른 임대 키와 겹치지 않는다**(설계 검토 우회 9 의 전제).
 *
 * 상수 비교로 재지 않는다 — relay 키 둘 가운데 하나는 `private` 라 test 가 볼 수 없고, 애초에
 * 「같은 값인가」보다 **「동시에 쥘 수 있는가」**가 재야 할 성질이다. 키가 겹치면 보존 러너가
 * 도는 동안 relay 가 `Busy` 를 받아 **알림이 한 주기 밀린다**(그 반대도 같다). 그래서 보존
 * 임대를 쥔 채 relay 임대 전수를 각각 잡아 본다.
 *
 * `OutboxConsumerKind.entries` 를 열거하지 않고 전수로 돈다 — 새 소비자 종류가 생기면 그 키도
 * 자동으로 이 대조에 든다.
 */
class RetentionLeaseKeyTest : PersistenceTestSupport() {
    @Test
    fun `공허 대조 — 대조할 relay 임대 종류가 하나 이상 있다`() {
        OutboxConsumerKind.entries.shouldNotBeEmpty()
    }

    @Test
    fun `보존 임대를 쥔 채 relay 임대 전수를 동시에 쥘 수 있다 — 키가 겹치지 않는다`() {
        val attempt =
            retentionStore().withExclusiveLease {
                OutboxConsumerKind.entries.map { kind ->
                    kind to PostgresAdvisoryLockLease(dataSource()).withLease(kind) { HELD }
                }
            }

        val relayAttempts = attempt.shouldBeHeld()
        relayAttempts.forEach { (kind, relay) ->
            withClue(kind) { relay.shouldBeHeld() shouldBe HELD }
        }
    }

    /**
     * 반대 방향도 같다 — relay 임대를 쥔 채 보존 임대를 쥘 수 있다. 한 방향만 재면 키가 아니라
     * **잡는 순서**가 성립의 근거일 수 있다.
     */
    @Test
    fun `relay 임대를 쥔 채 보존 임대를 쥘 수 있다`() {
        val attempt =
            PostgresAdvisoryLockLease(dataSource()).withLease(OutboxConsumerKind.NotificationRequested) {
                retentionStore().withExclusiveLease { HELD }
            }

        attempt.shouldBeHeld().shouldBeHeld() shouldBe HELD
    }

    private fun <T> LeaseAttempt<T>.shouldBeHeld(): T =
        when (this) {
            is LeaseAttempt.Held -> result
            LeaseAttempt.Busy -> error("임대를 쥐지 못했다 — 키가 겹쳤다는 뜻이다")
        }

    private companion object {
        const val HELD = "held"
    }
    /** 보존 저장소 — 마스킹이 지울 키를 **계약**에 물으므로 레지스트리를 함께 받는다(D-6B3-3 ①). */
    private fun retentionStore() = JdbcRetentionStore(dataSource(), testFieldContracts())
}
