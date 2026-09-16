package bidvector.workflow.prediction

import bidvector.decision.MlUnavailableReason
import bidvector.sharedkernel.Rate
import java.math.BigDecimal

/**
 * **파일 규율(verifier r3 G-1·r4 I-1)** — 이 파일의 값 타입 `init` 불변식은 마지막
 * 안전판이지 게이트가 아니다. `bidvector.adapters.ml.isAcceptableSuccessShape`(구조
 * 검증층)가 응답 단계에서 먼저 걸러야 `predict` 밖으로 예외가 새지 않는다(r1 F-5 수정이
 * 이 짝을 빠뜨려 r2 G-1이 났고, `SuccessShapeFailClosedTest`의 table-driven test는 손으로
 * 유지돼 새 조건은 못 잡는다 — r4 I-1). **값 타입 `init` 조건을 늘리는 커밋은 같은
 * 커밋에서 검증층 술어와 `SuccessShapeFailClosedTest`의 table 행을 함께 늘린다.**
 *
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
 * **부호 불변식을 두지 않는다**(verifier r2 G-2 — r1 F-5 가 넣었던 「음수 거부」를 되돌림).
 * `prediction.proto`의 `PriceFitness` 주석이 "값의 산식은 이 계약이 규정하지 않는다"라고
 * 명시하고, `data-dictionary.md`에도 이 축의 범위 규정이 없다 — 부호를 제약하면 계약이
 * 허용하는 정직한 `Success`를 버리게 된다. 형태 검증은 decimal string 정규형(파싱 가능성)
 * 만 잰다 — 어댑터 쪽 매핑(package `adapters ml`)의 구조 검증층.
 */
data class PriceFitness(
    val score: BigDecimal,
)

/**
 * 불확실성 출처(scope.md ⑦, 2B `IntervalSource` 미러) — legacy 의 합성 `confidence` 단일
 * 값과 달리 성분+출처로 구조화된다(`docs/discovery/data-dictionary.md` §6.5).
 *
 * `PosteriorPredictive`(M2/2F additive, D-2F-3) — 사후예측분산 기반(분포 엔진). 잔차 기반
 * 두 값과 다른 축이라 `null`로 접지 않는다(2A ⑥ 제3 변환 금지의 정신 — 엔진이 정직하게
 * 낸 답을 client 가 버리지 않는다). `docs/discovery/data-dictionary.md` §6.5 는 아직 이
 * 값을 정의하지 않는다(`OPEN-2F-DICT-INTERVAL-SOURCE`, 문서 소유 — 후속).
 */
enum class IntervalSource {
    CrossValidationResidual,
    TimeHoldoutResidual,
    PosteriorPredictive,
}

/**
 * release 의 종류(scope.md ⑥, M2/2F additive, D-2F-2) — 아티팩트가 있는 release(`Artifact`,
 * GBM 등)와 아티팩트 없이 정책·코드만으로 서빙하는 release(`Derived`, 분포 엔진)를 값 위장
 * 없이 1급으로 가른다(5D-2 위협 (h)). 계약의 `RELEASE_KIND_UNSPECIFIED`/`UNRECOGNIZED`는
 * 이 타입에 값이 없다 — 어댑터 검증층(`hasValidReleaseShape`)이 그 값을 가진 응답을
 * `ModelReleaseRef`가 지어지기 전에 거부한다(fail-closed, D-2F-6).
 */
enum class ReleaseKind {
    Artifact,
    Derived,
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
 *
 * `kind`(M2/2F additive, D-2F-2) — 기본값 [ReleaseKind.Artifact]다. 이 타입은
 * `adapters.ml`(prediction)뿐 아니라 `adapters.ml`(embedding, `EmbeddingResponseMapping.kt`)
 * 도 같이 짓는다 — embedding 쪽은 release_kind 축을 아직 나르지 않으므로(2F out_of_scope)
 * 기본값을 명시하지 않는 기존 호출부가 그대로 컴파일된다. `datasetId` 공백 허용은
 * `kind == Derived`일 때만이다(아티팩트 없는 release, D-2B-6과 충돌하지 않는다 — 학습
 * dataset 자체가 없다).
 */
data class ModelReleaseRef(
    val releaseId: String,
    val artifactChecksum: String,
    val featureSchemaVersion: String,
    val codeVersion: String,
    val datasetId: String,
    val kind: ReleaseKind = ReleaseKind.Artifact,
) {
    init {
        require(releaseId.isNotBlank()) { "ModelReleaseRef.releaseId는 빈 문자열일 수 없다" }
        require(artifactChecksum.isNotBlank()) { "ModelReleaseRef.artifactChecksum은 빈 문자열일 수 없다" }
        require(featureSchemaVersion.isNotBlank()) { "ModelReleaseRef.featureSchemaVersion은 빈 문자열일 수 없다" }
        require(codeVersion.isNotBlank()) { "ModelReleaseRef.codeVersion은 빈 문자열일 수 없다" }
        require(kind == ReleaseKind.Derived || datasetId.isNotBlank()) {
            "ModelReleaseRef.datasetId는 kind=Derived가 아니면 빈 문자열일 수 없다"
        }
    }
}

/**
 * [0,1] 구간의 fraction 가중치 일반(scope.md D-4D3-1, wire `Weight` 미러) —
 * `PredictionDiagnostics.shrinkageWeight`가 이 범위를 나른다. [bidvector.decision.UnitScore]를
 * 재사용하지 않는다(설계 검토 (1)) — 점수와 가중치를 한 타입으로 두면 사다리가 가중치를
 * 점수로 오인해 받을 수 있다. 생성자가 public인 이유는 [BidRateCandidates] KDoc과 같다
 * (cross-module 어댑터 생성).
 */
data class Weight(
    val value: BigDecimal,
) {
    init {
        require(value.signum() >= 0 && value <= BigDecimal.ONE) {
            "Weight.value는 [0,1] 구간이어야 한다: $value"
        }
    }
}

/**
 * 진단의 세그먼트 지지 근거(scope.md D-4D3-1, wire `SegmentSupport` 미러) — "그 추정이
 * 무엇으로 지지되는가" 하나의 축(2F H-8 회피 — legacy `historical_sample_size`는 엔진마다
 * 다른 모수를 같은 이름으로 날랐다).
 */
enum class SegmentSupport {
    Direct,
    ParentCategory,
    Global,
}

/**
 * 예측 진단 여섯 성분(scope.md D-4D3-1, wire `Diagnostics` 미러) — 진단은 사다리 점수를
 * 바꾸지 않는다(D-4D3-3 — 수축은 엔진이 이미 반영했고, Kotlin 은 임계를 재판정하지
 * 않는다). `agencySampleBelowThreshold`가 `true`인데 `agencySampleCount`가 큰 조합도
 * 그대로 나른다 — Kotlin 은 임계를 모른다(알려진 제한, 우회 (9)). 음수 성분은 계약
 * 위반이라 `init`이 방어적으로 다시 막는다 — [BidPredictionOutcome] 파일 규율(검증층이
 * 먼저 걸러야 한다) 대상이다.
 */
data class PredictionDiagnostics(
    val trainingRowCount: Int,
    val segmentSupport: SegmentSupport,
    val shrinkageWeight: Weight,
    val excludedObservations: Int,
    val agencySampleCount: Int,
    val agencySampleBelowThreshold: Boolean,
) {
    init {
        require(trainingRowCount >= 0) {
            "PredictionDiagnostics.trainingRowCount는 0 이상이어야 한다: $trainingRowCount"
        }
        require(excludedObservations >= 0) {
            "PredictionDiagnostics.excludedObservations는 0 이상이어야 한다: $excludedObservations"
        }
        require(agencySampleCount >= 0) {
            "PredictionDiagnostics.agencySampleCount는 0 이상이어야 한다: $agencySampleCount"
        }
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
        // M4/4D-3(scope.md D-4D3-1) — 필수 인자, 기본값 없음. 진단 없는 Predicted 를
        // 표현 불가능하게 한다(불가능한 상태는 타입으로 닫는다).
        val diagnostics: PredictionDiagnostics,
    ) : BidPredictionOutcome

    data class Unmeasurable(
        val reason: UnmeasurableReason,
    ) : BidPredictionOutcome

    data class Unavailable(
        val reason: MlUnavailableReason,
    ) : BidPredictionOutcome
}
