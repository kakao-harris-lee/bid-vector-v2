package bidvector.workflow.event

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/** 공백만으로는 [IdempotencyKey] 불변식(빈 문자열 금지)이 깨지므로 영숫자로 좁힌다. */
private const val KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/**
 * scope.md ④, ADR 0005 D-3 — 「동일 dedupe key 의 동시 삽입 2건은 1개 행으로 수렴한다」를
 * property 로 단언한다. [decideInbox]는 순수 함수라 실 [InboxPort] 없이 in-memory fold 로
 * 재현할 수 있다 — 판정 자체가 「이미 처리했는가」라는 한 bit 만 보기 때문이다.
 */
class InboxDedupPropertyTest {
    @Test
    fun `같은 idempotencyKey 가 여러 번(순서 무관) 도착해도 처리 횟수는 정확히 1이다`() {
        runBlocking {
            checkAll(Arb.string(1..8, KEY_ALPHABET), Arb.int(1, 20)) { keyValue, repeatCount ->
                val key = IdempotencyKey(keyValue)
                val arrivals = List(repeatCount) { key }

                val processedCount = foldProcessedCount(arrivals)

                processedCount shouldBe 1
            }
        }
    }

    @Test
    fun `duplicate·out-of-order 수신은 도착 순서와 무관하게 같은 최종 처리 집합·같은 처리 횟수로 수렴한다`() {
        // verifier L-1 — 최종 집합만 비교하면 dedup 을 「항상 Process」로 바꿔도 초록이다
        // (같은 key 를 여러 번 처리해도 집합 자체는 안 바뀌므로). shuffledOnce 는
        // arrivals 의 모든 원소를 정의상 최소 두 번(reversed 절반 + 원본 절반) 반복시켜
        // 처리 횟수 비교가 그 변이를 실제로 가른다 — 아래 「자기 점검」이 그 실측이다.
        runBlocking {
            checkAll(Arb.list(Arb.string(1..4, KEY_ALPHABET), 1..15)) { rawValues ->
                val arrivals = rawValues.map(::IdempotencyKey)
                val shuffledOnce = arrivals.reversed() + arrivals // out-of-order + duplicate 재수신 섞기
                val uniqueCount = arrivals.toSet().size

                // 최종 처리 집합: 순열·중복 재수신과 무관하게 「도착한 고유 key 전체」다.
                foldProcessedSet(arrivals) shouldBe arrivals.toSet()
                foldProcessedSet(shuffledOnce) shouldBe arrivals.toSet()

                // 효과 0: 처리 **횟수**도 고유 key 수로 수렴한다 — 재수신·순서 반전이
                // 처리 횟수를 늘리지 않는다(이 단언이 「항상 Process」 변이를 가른다).
                foldProcessedCount(arrivals) shouldBe uniqueCount
                foldProcessedCount(shuffledOnce) shouldBe uniqueCount
            }
        }
    }
}

private fun foldProcessedCount(arrivals: List<IdempotencyKey>): Int {
    val seen = mutableSetOf<IdempotencyKey>()
    var processed = 0
    arrivals.forEach { key ->
        if (decideInbox(seen.contains(key)) is InboxDecision.Process) {
            processed++
            seen += key
        }
    }
    return processed
}

private fun foldProcessedSet(arrivals: List<IdempotencyKey>): Set<IdempotencyKey> {
    val seen = mutableSetOf<IdempotencyKey>()
    arrivals.forEach { key ->
        if (decideInbox(seen.contains(key)) is InboxDecision.Process) {
            seen += key
        }
    }
    return seen
}
