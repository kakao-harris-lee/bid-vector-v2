package bidvector.adapters.extraction

import bidvector.procurement.FetchedDocument
import bidvector.strategy.WatchVerdict

/** 감시 탈락 사유 — 어떤 [WatchVerdict]로 게이트에 걸렸는지 그대로 싣는다(관측용). */
sealed interface WatchGateSkipReason {
    data class NotWatched(
        val verdict: WatchVerdict,
    ) : WatchGateSkipReason
}

/** 게이트를 통과한 뒤의 결과(D-3C-6) — `Skipped`에서는 [HttpLlmRequirementExtractor]를 부르지 않는다. */
sealed interface GatedOutcome {
    data class Skipped(
        val reason: WatchGateSkipReason,
    ) : GatedOutcome

    data class Ran(
        val outcome: ExtractionAttempt,
    ) : GatedOutcome
}

/**
 * 추출 엔진의 공개 표면(D-3C-6) — `HttpLlmRequirementExtractor`(`internal`)가 구현한다.
 * `WatchGatedExtractor`의 생성자가 이 **public** 인터페이스를 요구해, 그 생성자 자체는
 * public 이지만 실제 구현을 만드는 경로(`HttpLlmRequirementExtractor`의 생성자)는 여전히
 * adapters 밖에 닫혀 있다 — 프로덕션 배선은 이 파일의 공개 factory 를 통해서만 인스턴스를
 * 얻는다.
 */
interface RequirementExtractionEngine {
    fun extractDetailed(document: FetchedDocument): ExtractionAttempt
}

/**
 * 공개 진입점(D-3C-6, 위협 모델 방어 (b)) — **`WatchVerdict.Passed`가 아니면 추출 엔진을
 * 전혀 부르지 않는다**. `WatchVerdict.Passed`의 유일한 생성 경로는 1E `WatchRules.evaluate`
 * 이므로(`internal constructor`), 이 함수를 감시 탈락 공고로 속여 부를 방법이 없다
 * (S-3 이 0회/1회 실행 증거를 낸다).
 */
class WatchGatedExtractor(
    private val extractor: RequirementExtractionEngine,
) {
    fun extract(
        verdict: WatchVerdict,
        document: FetchedDocument,
    ): GatedOutcome =
        when (verdict) {
            is WatchVerdict.Passed -> GatedOutcome.Ran(extractor.extractDetailed(document))
            else -> GatedOutcome.Skipped(WatchGateSkipReason.NotWatched(verdict))
        }
}
