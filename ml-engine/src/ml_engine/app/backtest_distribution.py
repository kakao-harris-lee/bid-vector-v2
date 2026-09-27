"""`ml_engine.app.backtest_distribution` — **S2(분포 엔진) 전략 어댑터**(D-6G-3).

이 모듈이 조립 근에 있는 이유는 하나다: `ml_engine.evaluation` 은 `ml_engine.inference`
를 import 할 수 없다(import-linter forbidden). 실험 커널은 `StrategyLike` Protocol 만
알고, **분포 엔진을 실제로 부르는 것은 여기**다.

**출하 정책 그대로 부른다** — `predict_distribution(request, policy)` 에 넘기는
`InferencePolicy` 는 `policy/inference-v1.yaml` 을 로더로 읽은 것이고, 실험용으로
값을 바꾸지 않는다. 바꾸면 서빙과 다른 것을 재게 된다(D-6G-3).

gRPC 서버를 띄우지 않는다 — 오프라인 실험에 서버 경로는 불필요하고, **같은 커널 함수**를
같은 정책으로 부르는 것이 서빙과 동치다(계약 「재사용 조사」 표, 서빙 경로 동등성은
`OPEN-5C2-SERVING-PATH-PARITY` 소관).

후보 셋(보수·기준·공격)은 **각각 별 전략**이다(S2c·S2b·S2a) — 하나의 전략이 셋 중
좋은 것을 고르면 그것이 곧 다중 비교다(우회 ⑥).
"""

from __future__ import annotations

from dataclasses import dataclass

from ml_engine.contracts import common_pb2, features_pb2, prediction_pb2
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.strategies import (
    Abstained,
    AbstentionReason,
    CompetitorObservation,
    StrategyInput,
    StrategyOutcome,
    bid_from_rate,
)
from ml_engine.inference.distribution import DistributionRequest, predict_distribution
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import CandidateLabel, Success

_KRW = common_pb2.CURRENCY_KRW
_BASE_AMOUNT_BASIS = common_pb2.BASIS_BASE_AMOUNT
# 스냅숏의 기초금액은 공고 게시값이다(수집 어댑터가 `bssamt` 로 채운다) — provenance
# 라벨도 그 사실 그대로다. 값을 지어내지 않는다.
_PROVENANCE = common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED
_PROVENANCE_LABEL = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN


def _money(amount: float) -> common_pb2.Money:
    return common_pb2.Money(
        amount_won=int(amount),
        currency=_KRW,
        basis=_BASE_AMOUNT_BASIS,
        vat_treatment=common_pb2.VAT_TREATMENT_UNSPECIFIED,
        provenance=_PROVENANCE,
    )


def _sample(observation: CompetitorObservation) -> features_pb2.CompetitionSample:
    """지난 공고의 투찰 한 건 -> wire 표본. 예비가격·추첨 번호가 없으면 빈 관측으로
    두고 엔진이 그 표본을 거부하게 둔다 — 여기서 값을 채워 넣지 않는다."""
    draw = features_pb2.ReserveDrawObservation(
        reserve_prices=[_money(price) for price in (observation.reserve_prices or ())],
        selected_numbers=list(observation.drawn_serial_numbers or ()),
    )
    return features_pb2.CompetitionSample(
        observed_bid_rate=common_pb2.Rate(fraction=repr(observation.bid_rate)),
        origin=common_pb2.BID_RATE_ORIGIN_OBSERVED,
        base_amount=_money(observation.base_amount),
        base_amount_provenance_label=_PROVENANCE_LABEL,
        opened_on=observation.opened_on.isoformat(),
        reserve_draw=draw,
        agency_id=features_pb2.AgencyIdFact(
            missing=common_pb2.MISSING_REASON_NOT_COLLECTED_YET
        ),
        category_code=features_pb2.CategoryCodeFact(value=str(observation.category)),
    )


def build_request(
    request: StrategyInput,
) -> prediction_pb2.CalculateOptimalBidRequest:
    """대상 공고 + 지난 창 표본 -> wire 요청. **대상 공고의 개찰 결과는 들어가지
    않는다** — `StrategyInput` 에 애초에 없다."""
    return prediction_pb2.CalculateOptimalBidRequest(
        features=features_pb2.FeatureInputs(
            base_amount=features_pb2.BaseAmountFact(value=_money(request.base_amount)),
            category_code=features_pb2.CategoryCodeFact(
                value=str(request.notice.category)
            ),
            agency_id=features_pb2.AgencyIdFact(
                missing=common_pb2.MISSING_REASON_NOT_COLLECTED_YET
            ),
            base_amount_provenance_label=features_pb2.BaseAmountProvenanceLabelFact(
                value=_PROVENANCE_LABEL
            ),
        ),
        competition_samples=[_sample(item) for item in request.competitors],
        objective=features_pb2.OPTIMIZATION_OBJECTIVE_SCENARIO_TRIPLE,
    )


class DistributionEngine:
    """분포 엔진 호출을 후보 셋·seed 다섯이 **공유**하는 자리.

    엔진 결과는 (대상 공고, 경쟁 표본) 에만 달려 있다 — 후보 라벨도 판정 seed 도 입력이
    아니다. 라벨마다·seed 마다 다시 부르면 같은 계산을 열다섯 번 한다(재현 test 실측:
    7분 -> 캐시 뒤 그 일부). 캐시 키는 공고 키 해시와 표본 수다: 창은 비중첩이고
    (`run` 이 `window_overlaps` 로 실측해 단언한다) 한 공고는 한 창에만 들어가므로,
    같은 공고에 두 가지 표본이 붙는 일이 없다.

    **순수 메모다** — 같은 입력이면 같은 결과이고, 캐시가 있든 없든 판정이 같다."""

    def __init__(self, inference_policy: InferencePolicy) -> None:
        self._policy = inference_policy
        self._cache: dict[tuple[str, int], Success | None] = {}

    def result_for(self, request: StrategyInput) -> Success | None:
        key = (request.notice.notice_key_hash, len(request.competitors))
        if key not in self._cache:
            self._cache[key] = self._evaluate(request)
        return self._cache[key]

    def _evaluate(self, request: StrategyInput) -> Success | None:
        distribution = DistributionRequest.from_proto(build_request(request))
        if not isinstance(distribution, DistributionRequest):
            return None
        result = predict_distribution(distribution, self._policy)
        return result if isinstance(result, Success) else None


@dataclass(frozen=True)
class DistributionEngineStrategy:
    """S2 후보 하나 — 라벨(보수·기준·공격)마다 별 전략 인스턴스다. 셋이 같은
    `DistributionEngine` 을 공유하지만 **읽는 후보만 다르다** — 한 전략이 셋 중 좋은
    것을 고르면 그것이 곧 다중 비교다(우회 ⑥)."""

    name: str
    label: CandidateLabel
    engine: DistributionEngine

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome:
        del policy  # 전략 임계는 출하 `InferencePolicy` 에서만 온다(D-6G-3).
        result = self.engine.result_for(request)
        if result is None:
            return Abstained(AbstentionReason.ENGINE_UNMEASURABLE)
        for candidate in result.candidates:
            if candidate.label is self.label:
                return bid_from_rate(request, float(candidate.bid_rate))
        return Abstained(AbstentionReason.ENGINE_UNMEASURABLE)


def distribution_strategies(
    inference_policy: InferencePolicy,
) -> tuple[DistributionEngineStrategy, ...]:
    """S2 세 후보 — 이름은 사전 등록한 라벨(S2c 보수 · S2b 기준 · S2a 공격)."""
    engine = DistributionEngine(inference_policy)
    return (
        DistributionEngineStrategy("S2c", CandidateLabel.CONSERVATIVE, engine),
        DistributionEngineStrategy("S2b", CandidateLabel.BASE, engine),
        DistributionEngineStrategy("S2a", CandidateLabel.AGGRESSIVE, engine),
    )


S2_STRATEGY_NAMES: tuple[str, ...] = ("S2c", "S2b", "S2a")
"""주 가설 셋(D-6G-6) — Bonferroni 분모 `verdict.primary_hypothesis_count` 와 같은 수여야
한다. `test_backtest_job.py` 가 그 일치를 단언한다."""
