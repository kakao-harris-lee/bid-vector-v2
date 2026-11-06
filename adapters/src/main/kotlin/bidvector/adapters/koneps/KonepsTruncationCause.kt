package bidvector.adapters.koneps

import bidvector.procurement.ResultCodeCategory
import bidvector.procurement.TruncationCause

/**
 * [KonepsRawStep] → [TruncationCause]·사람이 읽는 사유 문자열 변환 —
 * `KonepsResilientCall.kt` 의 detekt `TooManyFunctions`(파일당 함수 상한 11)를 지키려고
 * 분리한 파일이다(로직상 새 관심사는 아니다).
 */
internal fun isQuotaSignal(step: KonepsRawStep): Boolean =
    when (step) {
        // 속도 한도는 quota 압력의 두 표면 중 하나다(H-3) — 그 시도가 나중에 성공해도
        // 압력을 겪은 사실 자체는 사라지지 않는다.
        is KonepsRawStep.RateLimitedStep -> {
            true
        }

        // 차단은 **한도와 다른 사실**이다 — 겹쳐 세면 「차단당함」이 「다 썼음」으로 읽힌다.
        is KonepsRawStep.BlockedStep -> {
            false
        }

        is KonepsRawStep.TransportStep -> {
            false
        }

        // 승인 상한은 우리 쪽 셈이다 — KONEPS 가 낸 quota 신호가 아니다.
        is KonepsRawStep.BudgetStep -> {
            false
        }

        is KonepsRawStep.EnvelopeStep -> {
            (step.outcome as? KonepsEnvelopeOutcome.Classified)?.category == ResultCodeCategory.QUOTA_EXCEEDED
        }
    }

/** 상태 코드 축이 가른 실패의 사유 문자열 — 본문·URI 는 싣지 않는다(서비스 키가 실릴 수 있다). */
internal fun describeStatus(status: Int): String = "HTTP $status"

internal fun describeTransport(outcome: KonepsTransportOutcome): String =
    when (outcome) {
        KonepsTransportOutcome.TimedOut -> "timeout"
        is KonepsTransportOutcome.TransportFailed -> outcome.exceptionType
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
        is KonepsRawStep.BudgetStep -> {
            TruncationCause.BudgetExhausted(step.limit)
        }

        is KonepsRawStep.RateLimitedStep -> {
            TruncationCause.QuotaExhausted
        }

        is KonepsRawStep.BlockedStep -> {
            TruncationCause.AccessBlocked
        }

        // 받은 응답은 상태 처분 표가 가르므로 이 자리에 오지 않는다 — 그 불변식은 주석이 아니라
        // 인자 타입([KonepsTransportOutcome.NoResponse])이 든다.
        is KonepsRawStep.TransportStep -> {
            when (step.outcome) {
                KonepsTransportOutcome.TimedOut -> TruncationCause.Timeout
                is KonepsTransportOutcome.TransportFailed -> TruncationCause.TransportFailure
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
