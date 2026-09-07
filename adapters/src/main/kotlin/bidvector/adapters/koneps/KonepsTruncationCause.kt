package bidvector.adapters.koneps

import bidvector.procurement.ResultCodeCategory
import bidvector.procurement.TruncationCause

private const val HTTP_TOO_MANY_REQUESTS_TC = 429

/**
 * [KonepsRawStep] → [TruncationCause]·사람이 읽는 사유 문자열 변환(H-3, verifier r1) —
 * `KonepsResilientCall.kt` 의 detekt `TooManyFunctions`(파일당 함수 상한 11)를 지키려고
 * 분리한 파일이다(로직상 새 관심사는 아니다).
 */
internal fun isQuotaSignal(step: KonepsRawStep): Boolean =
    when (step) {
        is KonepsRawStep.TransportStep -> {
            (step.outcome as? KonepsTransportOutcome.Received)?.status == HTTP_TOO_MANY_REQUESTS_TC
        }

        is KonepsRawStep.EnvelopeStep -> {
            (step.outcome as? KonepsEnvelopeOutcome.Classified)?.category == ResultCodeCategory.QUOTA_EXCEEDED
        }
    }

internal fun describeTransport(outcome: KonepsTransportOutcome): String =
    when (outcome) {
        KonepsTransportOutcome.TimedOut -> "timeout"
        is KonepsTransportOutcome.TransportFailed -> outcome.message
        is KonepsTransportOutcome.Received -> "HTTP ${outcome.status}"
    }

internal fun describeEnvelope(outcome: KonepsEnvelopeOutcome): String =
    when (outcome) {
        is KonepsEnvelopeOutcome.Classified -> "resultCode ${outcome.code}(${outcome.category})"
        is KonepsEnvelopeOutcome.Unclassified -> "resultCode 미지·부재(${outcome.code})"
        is KonepsEnvelopeOutcome.StructureFailure -> outcome.reason
        else -> error("성공/NoData 는 foldFinal 의 별도 분기가 처리한다")
    }

private fun causeForCategory(category: ResultCodeCategory): TruncationCause =
    when (category) {
        ResultCodeCategory.RETRYABLE -> TruncationCause.ServerError
        ResultCodeCategory.QUOTA_EXCEEDED -> TruncationCause.QuotaExhausted
        ResultCodeCategory.NOT_RETRYABLE -> TruncationCause.NotRetryable
        ResultCodeCategory.INPUT_ERROR -> TruncationCause.InputError
        ResultCodeCategory.NO_DATA -> error("NoData 는 foldFinal 의 별도 분기가 처리한다")
    }

/** 최종 실패 스텝을 [TruncationCause] 로 접는다(H-3) — 다섯 사유가 회계에서 구별되게 한다. */
internal fun causeFor(step: KonepsRawStep): TruncationCause =
    when (step) {
        is KonepsRawStep.TransportStep -> {
            when (step.outcome) {
                KonepsTransportOutcome.TimedOut -> TruncationCause.Timeout

                is KonepsTransportOutcome.TransportFailed -> TruncationCause.TransportFailure

                // 이 분기에 도달하는 유일한 Received 는 status==429 가 재시도를 소진한 경우다
                // (rawStep 이 그 외의 Received 는 EnvelopeStep 으로 분류한다).
                is KonepsTransportOutcome.Received -> TruncationCause.QuotaExhausted
            }
        }

        is KonepsRawStep.EnvelopeStep -> {
            when (val envelope = step.outcome) {
                is KonepsEnvelopeOutcome.Classified -> causeForCategory(envelope.category)
                is KonepsEnvelopeOutcome.Unclassified -> TruncationCause.Unclassified
                is KonepsEnvelopeOutcome.StructureFailure -> TruncationCause.StructureFailure
                else -> error("성공/NoData 는 foldFinal 의 별도 분기가 처리한다")
            }
        }
    }
