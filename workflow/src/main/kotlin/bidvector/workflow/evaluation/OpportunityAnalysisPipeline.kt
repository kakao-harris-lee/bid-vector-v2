package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.decision.priority.MatchOutcome
import bidvector.decision.priority.PriorityOutcome
import bidvector.decision.priority.ScoreFact
import bidvector.decision.priority.SemanticMatch
import bidvector.decision.priority.UnitVector
import bidvector.decision.priority.derive.DerivationOutcome
import bidvector.decision.priority.derive.deriveLoadRatio
import bidvector.strategy.WatchSubject
import bidvector.workflow.embedding.EmbeddingOutcome
import java.math.BigDecimal

/**
 * `OpportunityAnalysis.kt`의 guard 체인이 쓰는 순수 변환 단계(scope.md ②④) — port 를
 * 읽지 않는다(입력이 이미 port 결과다). [Step]/[ok]/[halt]는 `OpportunityAnalysis.kt`
 * 소유 — 이 파일은 그 값을 만들 뿐이다.
 */
internal data class SynthesizedTexts(
    val notice: SynthesizedText,
    val profile: SynthesizedText,
)

internal data class EmbeddedVectors(
    val notice: UnitVector,
    val profile: UnitVector,
)

internal fun synthesizeTexts(
    subject: WatchSubject,
    profileFacts: ProfileFacts,
    opportunity: OpportunityPolicyData,
): Step<SynthesizedTexts> =
    synthesizedTextOrNull(synthesizeNoticeText(subject, opportunity))?.let { noticeText ->
        synthesizedTextOrNull(synthesizeProfileText(profileFacts, opportunity))?.let { profileText ->
            ok(SynthesizedTexts(noticeText, profileText))
        }
    } ?: halt(MlUnavailableReason.InvalidRequest)

private fun synthesizedTextOrNull(outcome: SynthesisOutcome): SynthesizedText? =
    when (outcome) {
        is SynthesisOutcome.Synthesized -> outcome.text
        SynthesisOutcome.Empty -> null
    }

/** scope.md ②(4) — 둘 다 성공하고 release 가 같아야 [EmbeddedVectors]로 이어진다(D-4B6B-2·3). */
internal fun combineEmbeddings(
    notice: EmbeddingOutcome,
    profile: EmbeddingOutcome,
    normEpsilon: BigDecimal,
): Step<EmbeddedVectors> =
    when (notice) {
        is EmbeddingOutcome.Unavailable -> {
            halt(bridgeEmbeddingReason(notice.reason))
        }

        is EmbeddingOutcome.Embedded -> {
            when (profile) {
                is EmbeddingOutcome.Unavailable -> halt(bridgeEmbeddingReason(profile.reason))
                is EmbeddingOutcome.Embedded -> matchedVectorsOf(notice, profile, normEpsilon)
            }
        }
    }

private fun matchedVectorsOf(
    notice: EmbeddingOutcome.Embedded,
    profile: EmbeddingOutcome.Embedded,
    normEpsilon: BigDecimal,
): Step<EmbeddedVectors> =
    if (notice.release != profile.release) {
        halt(MlUnavailableReason.ReleaseMismatch)
    } else {
        ok(EmbeddedVectors(notice.vector.toUnitVector(normEpsilon), profile.vector.toUnitVector(normEpsilon)))
    }

internal fun matchScoreStep(
    vectors: EmbeddedVectors,
    policies: ResolvedPolicies,
): Step<UnitScore> {
    val categoryOffset = policies.opportunity.categoryOffset
    return when (val outcome = SemanticMatch.of(vectors.notice, vectors.profile, categoryOffset, policies.priority)) {
        is MatchOutcome.Matched -> ok(outcome.score)
        MatchOutcome.DimensionMismatch -> halt(MlUnavailableReason.ContractViolation)
        MatchOutcome.OffsetOutOfRange -> halt(MlUnavailableReason.InvalidRequest)
    }
}

internal fun deriveLoadRatioStep(snapshot: CapacitySnapshot): Step<UnitScore> =
    when (val outcome = deriveLoadRatio(snapshot.currentActiveBids, snapshot.maxActiveBids)) {
        is DerivationOutcome.Present -> ok(outcome.value)
        is DerivationOutcome.Absent -> halt(bridgeDerivationAbsence(outcome.reason))
    }

internal fun finalOutcomeOf(
    priorityOutcome: PriorityOutcome,
    matchScore: UnitScore,
): MlAnalysisOutcome =
    when (priorityOutcome) {
        is PriorityOutcome.Unavailable -> MlAnalysisOutcome.Unavailable(priorityOutcome.reason)
        is PriorityOutcome.Composed -> MlAnalysisOutcome.Analyzed(priorityOutcome.priority, null, matchScore)
    }

internal fun <T> DerivationOutcome<T>.toScoreFact(): ScoreFact<T> =
    when (this) {
        is DerivationOutcome.Present -> ScoreFact.Present(value)
        is DerivationOutcome.Absent -> ScoreFact.Absent(bridgeDerivationAbsence(reason))
    }
