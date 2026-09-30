package bidvector.app.architecture

import bidvector.procurement.AttemptOutcome
import bidvector.procurement.BudgetLimit
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.TruncationCause
import bidvector.procurement.attemptOutcomeOf
import bidvector.procurement.truncationCodeOf
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test

/**
 * **D-6G2d-17 · 30 — 절단 사유의 분류를 등식으로 잠그는 게이트.**
 *
 * 값 단위 거동은 도메인 test 가 재고(사유 하나 → 결말 하나), 이 자리는 **집합**을 잰다: 확정 실패가
 * 정확히 셋인가, 그리고 **전수**가 세 갈래 중 하나로 가는가. 부분집합 단언은 집합이 넓어지는 것을
 * 보지 못하고, 넓어진 한 사유가 곧 「분류되지 않은 오류 한 번에 그 축을 영구히 버린다」다.
 *
 * 전수를 **sealed 계층에서 도출한다**(D-6G2d-30). 손으로 적은 목록은 새 사유가 추가돼도 조용히
 * 초록이다 — 그 사유가 목록에 없으니 등식이 **측정하지 않는다**. 이 게이트가 app test 에 있는 이유는
 * 리플렉션이다: 도메인 모듈은 `java.lang.Class` 를 보지 않고(architecture 게이트) 그 test classpath 에
 * 리플렉션이 없다. 구조를 훑는 게이트가 이 패키지에 모여 있는 것과 같은 이유다.
 */
class TruncationCauseClassificationGateTest {
    @Test
    fun `확정 실패 집합은 정확히 셋이다`() {
        val finalCodes =
            allTruncationCauses()
                .filter { attemptOutcomeOf(truncatedBy(it)) is AttemptOutcome.FinalFailure }
                .map(::truncationCodeOf)

        finalCodes.sorted() shouldContainExactly listOf("INPUT_ERROR", "MAX_PAGES", "NOT_RETRYABLE")
    }

    /** 관문 거부 셋과 일시 실패 나머지 — 어느 사유도 분류 밖에 남지 않는다. */
    @Test
    fun `절단 사유 전수가 세 갈래 중 하나로 간다`() {
        val causes = allTruncationCauses()
        val byBranch = causes.groupBy { attemptOutcomeOf(truncatedBy(it))::class.simpleName }

        byBranch.getValue("Refused") shouldHaveSize 3
        byBranch.getValue("FinalFailure") shouldHaveSize 3
        byBranch.getValue("Failed") shouldHaveSize causes.size - 6
        byBranch.keys shouldContainExactly listOf("Refused", "FinalFailure", "Failed").intersect(byBranch.keys)
    }

    /**
     * 계층에서 뽑은 전수 — 값을 나르는 갈래는 `objectInstance` 가 없어 대표값을 [PARAMETRISED_CAUSES]
     * 에서 가져온다. 그런 갈래가 새로 생기면 `getValue` 가 던져 **여기서 멈춘다**(조용히 빠지지 않는다).
     */
    private fun allTruncationCauses(): List<TruncationCause> =
        TruncationCause::class.sealedSubclasses.map { branch ->
            branch.objectInstance ?: PARAMETRISED_CAUSES.getValue(requireNotNull(branch.simpleName))
        }
}

/** 값을 나르는 절단 사유의 대표값 — `objectInstance` 가 없는 갈래는 여기 등재돼야 한다. */
private val PARAMETRISED_CAUSES: Map<String, TruncationCause> =
    mapOf("BudgetExhausted" to TruncationCause.BudgetExhausted(BudgetLimit.DAILY))

/** 그 사유로 절단된 걷기의 회계 — 사유만 다르다. */
private fun truncatedBy(cause: TruncationCause): CollectionAccounting =
    CollectionAccounting(
        received = 0,
        normalized = 0,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = null,
        pagesFetched = 1,
        truncated = true,
        unknownFields = 0,
        truncationCause = cause,
    )
