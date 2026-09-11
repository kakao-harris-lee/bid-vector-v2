package bidvector.workflow.prediction

import bidvector.procurement.BusinessCategory
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Rate
import bidvector.workflow.event.CorrelationId
import java.time.LocalDate

/**
 * 발주기관 식별자(scope.md ①) — 불투명 문자열(2B `AgencyIdFact` 미러, `OPEN-2B-AGENCY-ID`).
 * DB 내부 id 가 아니다 — 발급·안정성의 정본은 M3.
 */
data class AgencyId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AgencyId는 빈 문자열일 수 없다" }
    }
}

/**
 * `CalculateOptimalBid`의 목적함수(scope.md ①, 2B `OptimizationObjective` 미러). 계약이
 * 지원하는 값은 지금 하나뿐이다(`OPEN-2B-OBJECTIVE-VALUES`, 5D 확장 소관) — 값이 늘면 이
 * 미러도 같이 는다.
 */
enum class OptimizationObjective {
    SCENARIO_TRIPLE,
}

/**
 * 모델 release 선택자(scope.md ①, 2B `ModelReleaseSelector` 미러) — 미지정을 표현할 수
 * 없다(sealed, oneof 미설정 자리가 타입에 없다). `latest_promoted` 경로의 대조는 어댑터
 * 내부 단계다(D-4D-4).
 */
sealed interface ModelReleaseSelector {
    data object LatestPromoted : ModelReleaseSelector

    data class Exact(
        val releaseId: String,
        val artifactChecksum: String,
    ) : ModelReleaseSelector {
        init {
            require(releaseId.isNotBlank()) { "releaseId는 빈 문자열일 수 없다" }
            require(artifactChecksum.isNotBlank()) { "artifactChecksum은 빈 문자열일 수 없다" }
        }
    }
}

/**
 * 경쟁 표본 한 건(scope.md ①, 2B `CompetitionSample` 형태 미러) — 정제(어느 행을 보낼지)는
 * 호출부(4B 후속) 소관이고 이 타입은 형태만 정한다. `origin`은 나르지 않는다 — 이 축의
 * 값은 항상 관측(`BID_RATE_ORIGIN_OBSERVED`)이라 어댑터가 상수로 채운다(common.proto
 * D-2A-7). 예비가격 추첨 관측(`ReserveDrawObservation`)은 이 slice가 나르지 않는다(알려진
 * 제한 — distribution predictor 입력은 4B 후속).
 */
data class CompetitionSample(
    val observedBidRate: Rate,
    val baseAmount: BaseAmount,
    val baseAmountProvenanceLabel: BaseAmountProvenance,
    val openedOn: LocalDate,
    val awardRate: Rate? = null,
)

/**
 * `CalculateOptimalBid` 요청(scope.md ①) — **도메인 값만** 나른다(자유 `String` 0). 호출부
 * (workflow, 4B 후속)가 조립하고 어댑터가 계약 DTO 로만 매핑한다(제3 변환 금지, ADR 0010
 * D-3). `baseAmount`가 [ResolvedBaseAmount]인 이유는 3A 의 first-match 판정 결과를 그대로
 * 옮기기 위해서다 — 값 재획득이 아니다.
 */
data class BidPredictionRequest(
    val baseAmount: ResolvedBaseAmount,
    val businessCategory: BusinessCategory?,
    val agencyId: AgencyId?,
    val baseAmountProvenanceLabel: BaseAmountProvenance,
    val competitionSamples: List<CompetitionSample>,
    val objective: OptimizationObjective,
    val releaseSelector: ModelReleaseSelector,
    val correlationId: CorrelationId,
)
