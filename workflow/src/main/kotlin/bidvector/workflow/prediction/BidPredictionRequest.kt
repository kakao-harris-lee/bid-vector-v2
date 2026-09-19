package bidvector.workflow.prediction

import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
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
 * 예비가격 추첨 관측(M4/4B-7, D-4B7-5) — 표본 공고의 예비가격 원문 관측값(기초금액 축,
 * D-4B7-1 (a))과 추첨된 번호 집합을 함께 나른다. `reservePrices`는 비어 있을 수 없다 —
 * 이 타입이 만들어지면 그 값이 있다는 뜻이다(`null`인 [CompetitionSample.reserveDraw]가
 * "이 축 자체가 관측되지 않았다"를 진다, 엔진이 `NO_RESERVE_DRAW`로 계수). `selectedNumbers`는
 * 비어 있을 수 있다(`DrawNumberObservation.NotObserved` → 빈 집합, 엔진은 거부하지 않는다) —
 * 대신 값이 있으면 전부 1-기반 인덱스(1 이상)여야 한다.
 */
data class ReserveDrawObservation(
    val reservePrices: List<BaseAmount>,
    val selectedNumbers: Set<Int>,
) {
    init {
        require(reservePrices.isNotEmpty()) { "reservePrices는 비어 있을 수 없다" }
        require(selectedNumbers.all { it >= 1 }) {
            "selectedNumbers는 모두 1 이상이어야 한다(1-기반 인덱스): $selectedNumbers"
        }
    }
}

/**
 * 경쟁 표본 한 건(scope.md ①, 2B `CompetitionSample` 형태 미러) — 정제(어느 행을 보낼지)는
 * 호출부(4B 후속) 소관이고 이 타입은 형태만 정한다. `origin`은 나르지 않는다 — 이 축의
 * 값은 항상 관측(`BID_RATE_ORIGIN_OBSERVED`)이라 어댑터가 상수로 채운다(common.proto
 * D-2A-7).
 *
 * `agencyId`·`categoryCode`(M2/2F additive, D-2F-1) — 표본의 발주기관·업종 fact. **식별자가
 * 아니다**(D-2B-3 유지 — 표본 식별·중복 제거는 여전히 불가). 값의 정본은 [AgencyId]와
 * 같다(`OPEN-2B-AGENCY-ID`, 정본 M3). 어댑터가 `null`을 나르는 결측 사유는 두 축이
 * 다르다(M3/3H-2 D-3H2-2) — `agencyId`는 `MISSING_REASON_UNKNOWN`(수집했으나 원천에
 * 없음), `categoryCode`는 여전히 `MISSING_REASON_NOT_COLLECTED_YET` 하나뿐이다
 * (`RequestMapping.kt` — 사유를 지어내지 않는다).
 *
 * `reserveDraw`(M4/4B-7, D-4B7-5) — `null`이면 이 축이 관측되지 않았다. 4D-1 알려진
 * 제한 2(distribution predictor 입력 부재)를 이 slice가 채운다.
 */
data class CompetitionSample(
    val observedBidRate: Rate,
    val baseAmount: BaseAmount,
    val baseAmountProvenanceLabel: BaseAmountProvenance,
    val openedOn: LocalDate,
    val awardRate: Rate? = null,
    val agencyId: AgencyId? = null,
    val categoryCode: CategoryCode? = null,
    val reserveDraw: ReserveDrawObservation? = null,
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
