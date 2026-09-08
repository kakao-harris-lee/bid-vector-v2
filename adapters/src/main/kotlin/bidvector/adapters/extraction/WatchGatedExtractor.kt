package bidvector.adapters.extraction

import bidvector.procurement.FetchedDocument
import bidvector.strategy.WatchVerdict
import java.time.Clock
import java.util.concurrent.ExecutorService

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
 * 이 인터페이스 자체는 public 이지만(타입 참조는 자유), 이것을 만족하는 인스턴스를
 * **adapters 가 실제로 조립해 내주는** 유일한 공개 경로는 [createWatchGatedExtractor]
 * 하나다 — 조립 함수 자체([createRequirementExtractionEngine])는 `internal`이라
 * 엔진만 따로 꺼낼 수 없다(verifier r1 F-1, probe A 가 그 우회를 실측했다).
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

/**
 * 프로덕션 배선의 유일한 공개 진입점(D-3C-6, verifier r1 F-1 수정) — 이 함수로만
 * [WatchGatedExtractor]를 얻는다. 내부에서만 `internal` [createRequirementExtractionEngine]
 * 을 부르므로, 이 함수를 거치지 않고 감시 게이트 없는 추출 엔진을 얻는 adapters 밖 경로가
 * 없다. 값은 전부 호출부가 넘긴다(기본 인자 없음, 위협 모델 방어 (d)).
 */
fun createWatchGatedExtractor(
    llmClient: LlmClient,
    schemaValidator: RequirementSchemaValidator,
    promptText: String,
    model: ModelId,
    policy: ExtractionPolicyData,
    clock: Clock,
    callExecutor: ExecutorService,
): WatchGatedExtractor =
    WatchGatedExtractor(
        createRequirementExtractionEngine(llmClient, schemaValidator, promptText, model, policy, clock, callExecutor),
    )
