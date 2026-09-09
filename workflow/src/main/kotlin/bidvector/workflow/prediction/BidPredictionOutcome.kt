package bidvector.workflow.prediction

import bidvector.decision.MlUnavailableReason
import bidvector.sharedkernel.Rate
import java.math.BigDecimal

/**
 * 후보 셋 — 정확히 셋, 리스트가 아니다(scope.md ①·⑦, 2B ③ 라벨 순서 고정). 생성자는
 * public이다 — `internal`은 **Kotlin 컴파일 모듈**(Gradle 프로젝트) 단위라 이 결과를
 * 실제로 짓는 어댑터(`adapters` 모듈, 다른 Gradle 프로젝트)에서 `internal` 생성자를 애초에
 * 호출할 수 없다(실측: `internal constructor`로 시도하면 `adapters:compileKotlin` 자체가
 * `Cannot access '<init>': it is internal` 로 실패 — 같은 모듈 안에서만 서는 [Rate]·
 * [bidvector.decision.ReviewReason.MlUnavailable] 류의 폐쇄 패턴과 다른 축이다). 이미 같은
 * 이유로 열려 있는 [MlAnalysisOutcome.Analyzed](`workflow.evaluation`, 4B-2 — cross-module
 * adapter 생성 전제)와 같은 판단이다. 위조 방어는 이 타입이 아니라 어댑터 쪽 매핑 함수
 * (`bidvector.adapters.ml.mapSuccess`)의 유일 생성 경로 관례와 code review·test 커버리지가
 * 진다(설계 검토 대비 결정 변경 — 알려진 제한).
 */
data class BidRateCandidates(
    val conservative: Rate,
    val base: Rate,
    val aggressive: Rate,
) {
    init {
        // verifier r1 F-5(low) — 라벨이 나르는 순서 의미(2B ③ 라벨 고정 CONSERVATIVE·
        // BASE·AGGRESSIVE, legacy "기준 후보는 항상 가운데")를 값 스스로도 지킨다. 현재
        // 소비자는 0 이라 사는 값이 없으나(T-1a), 4B 후속이 소비하는 순간 이 불변식이
        // 역순·중복 값의 위조를 컴파일이 아니라 생성 시점에 막는다.
        require(conservative <= base) {
            "BidRateCandidates.conservative($conservative)는 base($base) 이하여야 한다"
        }
        require(base <= aggressive) {
            "BidRateCandidates.base($base)는 aggressive($aggressive) 이하여야 한다"
        }
    }
}

/**
 * 가격 적합도 — 확률이 아니다(ML-03, D-M2-8, scope.md ⑨). `UnitScore`·`Rate`와 상호
 * 대입되지 않는 별도 타입이다 — `OPEN-ML-03`의 「타입 분리」 후보를 Kotlin 쪽에서 실물로
 * 세운다. 생성자가 public인 이유는 [BidRateCandidates] KDoc과 같다(cross-module 어댑터 생성).
 * 음수를 거부한다(verifier r1 F-5) — "적합도"가 해석 가능한 값의 최소 하한.
 */
data class PriceFitness(
    val score: BigDecimal,
) {
    init {
        require(score.signum() >= 0) { "PriceFitness.score는 음수일 수 없다: $score" }
    }
}

/**
 * 불확실성 출처(scope.md ⑦, 2B `IntervalSource` 미러) — legacy 의 합성 `confidence` 단일
 * 값과 달리 성분+출처로 구조화된다(`docs/discovery/data-dictionary.md` §6.5).
 */
enum class IntervalSource {
    CrossValidationResidual,
    TimeHoldoutResidual,
}

/**
 * 불확실성(scope.md ⑦) — `sampleSize`가 1 미만이면 애초에 `Success`가 아니라 `Unmeasurable`
 * 이어야 한다(계약 불변식, 위협 모델 우회 (3)) — 이 타입도 방어적으로 같은 하한을 강제한다.
 * 생성자가 public인 이유는 [BidRateCandidates] KDoc과 같다(cross-module 어댑터 생성).
 */
data class Uncertainty(
    val sampleSize: Int,
    val dispersion: BigDecimal,
    val estimateMargin: BigDecimal,
    val intervalSource: IntervalSource,
) {
    init {
        require(sampleSize >= 1) { "Uncertainty.sampleSize는 1 이상이어야 한다: $sampleSize" }
    }
}

/**
 * 모델 release 식별(scope.md ⑥, 2B `ModelRelease` 미러) — 다섯 성분 전부 non-null 이라
 * provenance 가 탈락할 수 없다(우회 (10)). 생성자가 public인 이유는 [BidRateCandidates]
 * KDoc과 같다(cross-module 어댑터 생성). **다섯 성분 전부 비공백을 생성 시점에 강제한다**
 * (verifier r1 F-2 — proto3 기본값 `""`이 「non-null이라 탈락할 수 없다」는 문면을 뚫어
 * 공백 release가 `Predicted`로 샜었다). 어댑터 쪽 매핑(package `adapters ml`)이 이
 * 불변식을 fail-closed 검사로 먼저 걸러 예외가 새지 않게 한다 — 이 `init`은
 * 방어의 마지막 층(2B `ModelRelease` KDoc "여기 넷[+식별자 하나]은 release를 지목하는 데
 * 필요한 성분과 식별자다" — 다섯 전부가 지목에 필요하다).
 */
data class ModelReleaseRef(
    val releaseId: String,
    val artifactChecksum: String,
    val featureSchemaVersion: String,
    val codeVersion: String,
    val datasetId: String,
) {
    init {
        require(releaseId.isNotBlank()) { "ModelReleaseRef.releaseId는 빈 문자열일 수 없다" }
        require(artifactChecksum.isNotBlank()) { "ModelReleaseRef.artifactChecksum은 빈 문자열일 수 없다" }
        require(featureSchemaVersion.isNotBlank()) { "ModelReleaseRef.featureSchemaVersion은 빈 문자열일 수 없다" }
        require(codeVersion.isNotBlank()) { "ModelReleaseRef.codeVersion은 빈 문자열일 수 없다" }
        require(datasetId.isNotBlank()) { "ModelReleaseRef.datasetId는 빈 문자열일 수 없다" }
    }
}

/**
 * 「측정 불가」 사유(scope.md ⑤, 2B `UnmeasurableReason` 미러) — 성공한 호출의 정직한 답
 * 셋이고 서로 다른 사유다(ADR 0010 D-3, `OPEN-2A-RELEASE-CHECK-4D`와 다른 축).
 */
sealed interface UnmeasurableReason {
    data object InsufficientSamples : UnmeasurableReason

    data object UntrainedSegment : UnmeasurableReason

    data object FeatureAbsent : UnmeasurableReason
}

/**
 * `BidPredictionPort.predict`의 결과(scope.md ①, ADR 0010 D-3) — 세 가지 말고는 없다.
 * `Predicted`는 성공한 예측, `Unmeasurable`은 성공한 호출의 정직한 「측정 불가」 답,
 * `Unavailable`은 어댑터가 답을 얻지 못했다는 것이다(D-6). 셋을 값·기본값으로 접지 않는다
 * — 어댑터 밖으로 예외가 나가지 않는다(위협 모델 (c)).
 */
sealed interface BidPredictionOutcome {
    /** 생성자가 public인 이유는 [BidRateCandidates] KDoc과 같다(cross-module 어댑터 생성). */
    data class Predicted(
        val candidates: BidRateCandidates,
        val fitness: PriceFitness,
        val uncertainty: Uncertainty,
        val release: ModelReleaseRef,
    ) : BidPredictionOutcome

    data class Unmeasurable(
        val reason: UnmeasurableReason,
    ) : BidPredictionOutcome

    data class Unavailable(
        val reason: MlUnavailableReason,
    ) : BidPredictionOutcome
}
