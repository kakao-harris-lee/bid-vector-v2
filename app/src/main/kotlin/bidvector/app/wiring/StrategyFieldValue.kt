package bidvector.app.wiring

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.StrategyDraft
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import bidvector.workflow.strategy.EditableField
import java.math.BigDecimal

/**
 * 편집 값이 실리는 칸 넷(D-6A2b-1) — 요청 본문은 평탄하고(D-6A1-20 ⓑ) 값의
 * 모양이 필드 종류마다 다르므로, 한 칸짜리 다형 값 대신 **타입이 다른 칸 넷**을 두고
 * 필드가 어느 칸을 읽는지를 닫힌 표([valueSlot])가 정한다. 선언되지 않은 칸이 함께 오면
 * 형식 오류(400)다 — 조용히 무시하지 않는다.
 */
enum class EditValueSlot(
    val jsonKey: String,
) {
    TERMS("terms"),
    AMOUNT_WON("amountWon"),
    NUMBER("number"),
    COUNT("count"),
}

/** [EditValueSlot] 넷과 1:1 로 대응하는 파싱 결과 — 어느 칸을 읽었는지가 타입에 남는다. */
sealed interface EditValue {
    data class Terms(
        val values: List<String>,
    ) : EditValue

    data class AmountWon(
        val won: Long,
    ) : EditValue

    data class Number(
        val value: BigDecimal,
    ) : EditValue

    data class Count(
        val value: Int,
    ) : EditValue
}

/**
 * 필드 → 값 칸(전수 `when`, `else` 없음) — 새 [EditableField] 가 생기면 컴파일이 이 표의
 * 누락을 잡는다(D-6A2b-6 과 같은 축: 열거가 아니라 구성).
 */
fun EditableField.valueSlot(): EditValueSlot =
    when (this) {
        is EditableField.Watch -> id.valueSlot()
        is EditableField.Threshold -> EditValueSlot.NUMBER
        EditableField.CandidateLimit, EditableField.MaxActiveBids -> EditValueSlot.COUNT
    }

private fun WatchRuleId.valueSlot(): EditValueSlot =
    when (this) {
        WatchRuleId.MinBudget, WatchRuleId.MaxBudget -> EditValueSlot.AMOUNT_WON

        WatchRuleId.FocusCategory,
        WatchRuleId.FocusRegion,
        WatchRuleId.ExcludeRegion,
        WatchRuleId.RequiredKeyword,
        WatchRuleId.ExcludeKeyword,
        -> EditValueSlot.TERMS
    }

/**
 * 현재 전략에서 내보낸 초안([bidvector.strategy.toDraft])의 **한 필드만** 바꾼다
 * (D-6A2b-2) — 나머지 필드는 현재 값 그대로 실린다(지어내지 않는다). 값 자체의 불변식은
 * 여기서 재구현하지 않는다: [bidvector.strategy.validate] 하나가 본다(D-10).
 *
 * [value] 의 갈래가 [valueSlot] 이 정한 칸과 어긋나는 것은 업무 오류가 아니라 이 파일 안의
 * 두 표가 어긋났다는 뜻이다 — 파서가 [valueSlot] 이 이름한 칸만 읽으므로 구성상 일어날 수
 * 없고, 일어나면 `check` 가 크게 실패한다(조용히 다른 값을 쓰지 않는다).
 */
internal fun StrategyDraft.withFieldValue(
    field: EditableField,
    value: EditValue,
): StrategyDraft =
    when (field) {
        is EditableField.Watch -> withWatchValue(field.id, value)
        is EditableField.Threshold -> withThresholdValue(field.field, value.number())
        EditableField.CandidateLimit -> copy(candidateLimit = value.count())
        EditableField.MaxActiveBids -> copy(maxActiveBids = value.count())
    }

private fun StrategyDraft.withWatchValue(
    rule: WatchRuleId,
    value: EditValue,
): StrategyDraft =
    when (rule) {
        WatchRuleId.FocusCategory -> copy(focusCategories = value.terms())
        WatchRuleId.FocusRegion -> copy(focusRegionTerms = value.terms())
        WatchRuleId.ExcludeRegion -> copy(excludeRegionTerms = value.terms())
        WatchRuleId.RequiredKeyword -> copy(requiredKeywordTerms = value.terms())
        WatchRuleId.ExcludeKeyword -> copy(excludeKeywordTerms = value.terms())
        WatchRuleId.MinBudget -> copy(minBudget = operatorDeclared(value.amountWon()))
        WatchRuleId.MaxBudget -> copy(maxBudget = operatorDeclared(value.amountWon()))
    }

private fun StrategyDraft.withThresholdValue(
    threshold: ThresholdField,
    value: BigDecimal,
): StrategyDraft =
    when (threshold) {
        ThresholdField.MinimumMatchScore -> copy(minimumMatchScore = value)
        ThresholdField.MinimumProbabilityScore -> copy(minimumProbabilityScore = value)
        ThresholdField.BidNowThreshold -> copy(bidNowThreshold = value)
        ThresholdField.ReviewThreshold -> copy(reviewThreshold = value)
    }

/**
 * 운영자가 HTTP 로 선언한 예산 한계(D-6A2b-2) — 출처는 **운영자 입력**이고 과세 처리는
 * 세금 포함이다. 이 두 값은 [bidvector.strategy.validate] 의 `checkBudgetLimitDeclaration`
 * 이 요구하는 유일한 조합이고(D-6, 그 밖은 `BudgetLimitNotComparable`), 세션 스냅숏 코덱도
 * 같은 전제를 검사한다(`MoneySnapshot` KDoc). 통화는 이 앱의 유일한 통화다 — 요청에서
 * 받지 않는다(받으면 「어느 통화인지 모르는 값」을 만들 자리가 생긴다).
 */
private fun operatorDeclared(won: Long): BaseAmount =
    BaseAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)

private fun EditValue.terms(): List<String> =
    (this as? EditValue.Terms)?.values ?: error("이 필드는 terms 칸을 읽는데 다른 칸의 값을 받았다(칸 배정표와 패치표가 어긋났다)")

private fun EditValue.amountWon(): Long =
    (this as? EditValue.AmountWon)?.won ?: error("이 필드는 amountWon 칸을 읽는데 다른 칸의 값을 받았다(칸 배정표와 패치표가 어긋났다)")

private fun EditValue.number(): BigDecimal =
    (this as? EditValue.Number)?.value ?: error("이 필드는 number 칸을 읽는데 다른 칸의 값을 받았다(칸 배정표와 패치표가 어긋났다)")

private fun EditValue.count(): Int =
    (this as? EditValue.Count)?.value ?: error("이 필드는 count 칸을 읽는데 다른 칸의 값을 받았다(칸 배정표와 패치표가 어긋났다)")
