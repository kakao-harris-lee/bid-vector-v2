package bidvector.workflow.suggestion

import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.OperatorLicenses
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.UncertainReason
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyDraft
import bidvector.workflow.evaluation.DEFAULT_FOCUS_CATEGORY
import bidvector.workflow.evaluation.EMPTY_SUBJECT
import bidvector.workflow.evaluation.FakeCandidateSource
import bidvector.workflow.evaluation.FakeLicenseGatePort
import bidvector.workflow.evaluation.FakeWatchSubjectPort
import bidvector.workflow.evaluation.FixedClock
import bidvector.workflow.evaluation.MATCHING_SUBJECT
import bidvector.workflow.evaluation.NOW
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.testNotice
import bidvector.workflow.evaluation.testStrategy
import bidvector.workflow.strategy.StrategyReader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * 제안 목록 use case(M7/7C①b) — 게이트 통과만 담는다 · 표시 상한은 계산 뒤에 자른다(STR-06) ·
 * 계산 중 입력이 바뀐 결과는 채택하지 않는다(STR-07) · 빈 결과의 뜻.
 */
class ListSuggestionsUseCaseTest {
    private val first = testNotice(number = "20260101001")
    private val second = testNotice(number = "20260101002")
    private val third = testNotice(number = "20260101003")
    private val fourth = testNotice(number = "20260101004")

    /** 읽을 때마다 다음 값을 낸다(끝에 닿으면 마지막 값을 계속 낸다) — 계산 중 변경의 대역. */
    private class SequenceStrategyReader(
        private val values: List<OperatorStrategy>,
    ) : StrategyReader {
        var loadCount = 0

        override fun load(): OperatorStrategy = values[minOf(loadCount++, values.lastIndex)]
    }

    private class SequenceProfilePort(
        private val values: List<ProfileFacts?>,
    ) : OperatorProfilePort {
        var readCount = 0

        override fun current(): ProfileFacts? = values[minOf(readCount++, values.lastIndex)]
    }

    /** [flip] 전에는 [initial], 뒤에는 [next] 를 낸다 — 판정 **도중** 프로필 저장의 대역. */
    private class FlippingProfilePort(
        private val initial: ProfileFacts?,
        private val next: ProfileFacts?,
    ) : OperatorProfilePort {
        private var flipped = false

        fun flip() {
            flipped = true
        }

        override fun current(): ProfileFacts? = if (flipped) next else initial
    }

    private fun strategy(
        candidateLimit: Int? = null,
        revision: Int = 1,
        watched: Boolean = true,
    ): OperatorStrategy =
        testStrategy(
            StrategyDraft(
                focusCategories = if (watched) listOf(DEFAULT_FOCUS_CATEGORY) else emptyList(),
                bidNowThreshold = BigDecimal("0.7"),
                reviewThreshold = BigDecimal("0.45"),
                candidateLimit = candidateLimit,
            ),
            revision = revision,
        )

    private fun useCase(
        strategies: StrategyReader,
        notices: List<Notice>,
        watchSubjects: FakeWatchSubjectPort = FakeWatchSubjectPort(),
        licenseGate: FakeLicenseGatePort = FakeLicenseGatePort(),
        profiles: OperatorProfilePort = SequenceProfilePort(listOf(null)),
    ): ListSuggestionsUseCase =
        ListSuggestionsUseCase(
            strategies = strategies,
            profiles = profiles,
            candidateSource = FakeCandidateSource(notices),
            watchSubjects = watchSubjects,
            licenseGate = licenseGate,
            clock = FixedClock(),
        )

    private fun rejectingWatch(vararg rejected: Notice): FakeWatchSubjectPort {
        val ids = rejected.map { it.id }.toSet()
        return FakeWatchSubjectPort { notice ->
            WatchSubjectOutcome.Found(if (notice.id in ids) EMPTY_SUBJECT else MATCHING_SUBJECT)
        }
    }

    private fun SuggestionList.ids(): List<NoticeId> = suggestions.map { it.notice.id }

    @Test
    fun `게이트를 통과한 공고만 원천 순서 그대로 담고 면허 판정 실제 값을 싣는다`() {
        val uncertain = LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared)
        val licenseGate =
            FakeLicenseGatePort { notice ->
                when (notice.id) {
                    second.id -> LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to emptySet()))
                    fourth.id -> uncertain
                    else -> LicenseVerdict.Eligible(emptySet())
                }
            }
        val result =
            useCase(
                SequenceStrategyReader(listOf(strategy(revision = 7))),
                listOf(first, second, third, fourth),
                watchSubjects = rejectingWatch(third),
                licenseGate = licenseGate,
            ).list()

        result.ids() shouldBe listOf(first.id, fourth.id)
        result.suggestions.map { it.license } shouldBe listOf(LicenseVerdict.Eligible(emptySet()), uncertain)
        result.totalCount shouldBe 2
        result.state shouldBe SuggestionState.MATCHED
        result.strategyRevision.value shouldBe 7
        result.computedAt shouldBe NOW
    }

    @Test
    fun `STR-06 — 표시 상한은 게이트를 다 돈 뒤에 자르고 계산량은 상한과 무관하다`() {
        val narrow = rejectingWatch(first)
        val wide = rejectingWatch(first)
        val narrowLicense = FakeLicenseGatePort()
        val wideLicense = FakeLicenseGatePort()
        val notices = listOf(first, second, third, fourth)

        val narrowResult =
            useCase(SequenceStrategyReader(listOf(strategy(candidateLimit = 1))), notices, narrow, narrowLicense).list()
        val wideResult =
            useCase(SequenceStrategyReader(listOf(strategy(candidateLimit = 5))), notices, wide, wideLicense).list()

        narrowResult.ids() shouldBe listOf(second.id)
        wideResult.ids() shouldBe listOf(second.id, third.id, fourth.id)
        narrowResult.totalCount shouldBe 3
        wideResult.totalCount shouldBe 3
        // 계산 예산(게이트 호출)이 표시 상한을 따라 움직이지 않는다 — 두 값이 따로 관측된다.
        narrow.calledFor shouldBe wide.calledFor
        narrowLicense.calledFor shouldBe wideLicense.calledFor
        narrow.calledFor shouldBe notices.map { it.id }
    }

    @Test
    fun `STR-06 — 원시 순번 앞쪽의 비통과 공고가 상한을 먹지 않는다(F-1 굶김을 물려받지 않는다)`() {
        val result =
            useCase(
                SequenceStrategyReader(listOf(strategy(candidateLimit = 1))),
                listOf(first, second, third, fourth),
                watchSubjects = rejectingWatch(first, second, third),
            ).list()

        result.ids() shouldBe listOf(fourth.id)
    }

    @Test
    fun `표시 상한이 미설정이면 자르지 않는다`() {
        val result = useCase(SequenceStrategyReader(listOf(strategy())), listOf(first, second, third)).list()

        result.ids() shouldBe listOf(first.id, second.id, third.id)
    }

    @Test
    fun `빈 결과의 뜻 — 열린 공고 없음 · 감시 미설정 · 통과 없음이 서로 다른 값이다`() {
        useCase(SequenceStrategyReader(listOf(strategy())), emptyList()).list().state shouldBe
            SuggestionState.NO_OPEN_NOTICES
        useCase(SequenceStrategyReader(listOf(strategy(watched = false))), listOf(first)).list().state shouldBe
            SuggestionState.WATCH_NOT_CONFIGURED
        useCase(SequenceStrategyReader(listOf(strategy())), listOf(first), rejectingWatch(first)).list().state shouldBe
            SuggestionState.NO_MATCH
    }

    @Test
    fun `STR-07 — 계산 중 전략이 바뀌면 그 결과를 버리고 새 전략으로 다시 계산한다`() {
        val before = strategy(revision = 1, watched = false)
        val after = strategy(revision = 2)
        // 시도 1: 고정(before) → 비교(after) — 다르다. 시도 2: 고정(after) → 비교(after).
        val reader = SequenceStrategyReader(listOf(before, after))

        val result = useCase(reader, listOf(first)).list()

        result.strategyRevision.value shouldBe 2
        result.ids() shouldBe listOf(first.id)
        result.state shouldBe SuggestionState.MATCHED
        reader.loadCount shouldBe 4
    }

    @Test
    fun `STR-07 — 개정이 같아도 값이 바뀌면 채택하지 않는다(쓰기 쪽 규율에 기대지 않는다)`() {
        val before = strategy(revision = 3, watched = false)
        val sameRevisionDifferentValue = strategy(revision = 3)

        val result = useCase(SequenceStrategyReader(listOf(before, sameRevisionDifferentValue)), listOf(first)).list()

        result.ids() shouldBe listOf(first.id)
    }

    @Test
    fun `STR-07 — 계산 중 프로필이 바뀌면 옛 프로필로 판정한 결과를 채택하지 않고 새 프로필로 다시 판정한다`() {
        val declared = ProfileFacts(emptySet(), OperatorLicenses.Declared(emptyList()), emptyList())
        val profiles = FlippingProfilePort(initial = null, next = declared)
        // 게이트는 프로필을 보고 판정한다 — 미선언이면 미달, 선언이면 충족. 첫 판정 직후 프로필이 바뀐다.
        val licenseGate =
            FakeLicenseGatePort { _ ->
                val verdict =
                    if (profiles.current() == null) {
                        LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to emptySet()))
                    } else {
                        LicenseVerdict.Eligible(emptySet())
                    }
                profiles.flip()
                verdict
            }

        val result =
            useCase(
                SequenceStrategyReader(listOf(strategy())),
                listOf(first),
                licenseGate = licenseGate,
                profiles = profiles,
            ).list()

        // 첫 시도를 채택했다면 빈 목록(옛 프로필 → 미달)이다. 새 프로필의 판정만 나와야 한다.
        result.ids() shouldBe listOf(first.id)
        result.suggestions.single().license shouldBe LicenseVerdict.Eligible(emptySet())
        licenseGate.calledFor shouldBe listOf(first.id, first.id)
    }

    @Test
    fun `STR-07 — 다시 계산하는 동안에도 바뀌면 409 로 끝낸다(낡은 결과를 내지 않는다)`() {
        // 시도 1: 1 → 2(다름). 시도 2: 3 → 4(다름).
        val reader = SequenceStrategyReader((1..4).map { strategy(revision = it) })

        shouldThrow<StrategyChangedDuringComputationException> { useCase(reader, listOf(first)).list() }
    }

    @Test
    fun `면허 미달은 제안으로 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            Suggestion(first, LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to emptySet())))
        }
    }
}
