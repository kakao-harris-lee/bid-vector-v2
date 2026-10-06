"""`ml_engine.evaluation.backtest.records` — 판정 산출물의 **형태**(D-6G-9·20·21).
조율자(`run`)에서 분리한 이유는 하나다: 산출물의 모양은 오래 남고 직렬화
(`report`)·조립 근(`app`)이 함께 읽는데, 조율 절차와 한 파일에 있으면 그 파일이
설계 래칫의 파일 크기 한도를 넘는다(실측).

여기 있는 것은 전부 **공시 대상**이다 — 제외 계수, 판정 불가 축, 표본 크기 결정식의
입력과 결과, 창 목록, 알려진 제한. 숨기면 안 되는 것을 타입으로 고정해 둔다.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import StrEnum
from typing import Final

from ml_engine.evaluation.backtest.exclusions import StandardMarketPriceScope
from ml_engine.evaluation.backtest.fit import FitResult
from ml_engine.evaluation.backtest.observations import (
    LoadedSnapshot,
)
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.reasons import (
    DivisionCoverage,
    ExclusionReason,
    UndecidableAxis,
)
from ml_engine.evaluation.backtest.strategies import StrategyLike
from ml_engine.evaluation.backtest.verdict import StrategyVerdict
from ml_engine.evaluation.backtest.windows import WindowExclusion


class StopReason(StrEnum):
    """판정을 내지 않고 멈춘 사유 — 「판정이 없다」와 「판정이 나빴다」를 구별한다."""

    DISTRIBUTION_FIT_REJECTED = "DISTRIBUTION_FIT_REJECTED"
    NO_ADMITTED_NOTICE = "NO_ADMITTED_NOTICE"
    SAMPLE_SIZE_BELOW_MINIMUM = "SAMPLE_SIZE_BELOW_MINIMUM"
    SAMPLING_BUDGET_EXCEEDED = "SAMPLING_BUDGET_EXCEEDED"
    INSUFFICIENT_WINDOWS = "INSUFFICIENT_WINDOWS"
    WINDOW_OVERLAP = "WINDOW_OVERLAP"
    STRATEGY_SAMPLE_MISMATCH = "STRATEGY_SAMPLE_MISMATCH"


class SampleVariant(StrEnum):
    """표본 판 셋(D-6G-21). 주 판정은 지자체를 **가르지 못한 채로** 내고, 보조 민감도
    둘을 함께 낸다 — 세 판의 판정 부호가 다르면 판정문에 그대로 적는다."""

    MAIN = "MAIN"
    EXCLUDE_WIDE_RESERVE_RANGE = "EXCLUDE_WIDE_RESERVE_RANGE"
    EXCLUDE_ESTIMATED_LOCAL_GOVERNMENT = "EXCLUDE_ESTIMATED_LOCAL_GOVERNMENT"


# 기관 코드에서 지자체를 **추정**하는 접두 목록 — **비어 있다.** authoritative 대응을
# 확보하지 못했고(스키마 §4, `OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`) 지어내면 DEC-03 을
# 코드가 조용히 재정의한다. 비어 있는 동안 보조 판 (b) 는 주 판정과 같은 표본이고,
# 판정 JSON 의 `estimate_available: false` 가 그 사실을 명시한다.
ESTIMATED_LOCAL_AGENCY_PREFIXES: Final[frozenset[str]] = frozenset()

# 알려진 제한 — 판정문이 숨기면 안 되는 것들. 코드로 고정해 판정 JSON 이 매번 싣는다.
KNOWN_LIMITATIONS: Final[tuple[str, ...]] = (
    "COUNTERFACTUAL_NOT_MEASURED",
    "LOCAL_GOVERNMENT_UNDECIDABLE",
    "SHIP_CLASS_CODE_UNRESOLVED",
    "BID_METHOD_MATCHED_BY_SUBSTRING",
    "SNAPSHOT_FIELD_FILL_RATES_UNMEASURED",
    "A_VALUE_EXCLUDES_STANDARD_MARKET_PRICE",
    "STANDARD_MARKET_PRICE_MAGNITUDE_UNMEASURED",
    "FLOOR_ROUNDING_RULE_UNRESOLVED",
    "S1_CONSTRUCTION_ONLY",
    "S3_GBM_NOT_AVAILABLE",
    "POSTED_FLOOR_RATE_BAND_UNVERIFIED",
)


@dataclass(frozen=True)
class BacktestRequest:
    """실험 한 번의 입력. `baseline` 이 S0 이고 `candidates` 가 나머지 전략이다."""

    snapshot: LoadedSnapshot
    baseline: StrategyLike
    candidates: tuple[StrategyLike, ...]
    primary_names: tuple[str, ...]
    policy: StrategyBacktestPolicy
    policy_checksum: str
    variant: SampleVariant = SampleVariant.MAIN


@dataclass(frozen=True)
class DivisionCoverageRecord:
    """확정 범위의 업무 **하나**가 표본에서 어떻게 대표됐는가(M6/6G-2c D-6G2c-17).

    행 수와 표지를 **같은 자리**에 둔다 — 앞 판은 행 수만 날랐고 표지를 직렬화 시점에
    `count` 의 참/거짓으로 지었다. 그러면 「행 하나 == 대표됨」이라는 판단이 직렬화 코드에
    숨고, 그 판단이 쓰는 문턱(정책의 창당 표본 하한)은 판정 경로에 나타나지 않는다."""

    division: str
    row_count: int
    status: DivisionCoverage

    def __post_init__(self) -> None:
        """표지와 행 수가 **서로를 설명하는가**(M6/6G-2c 수정 r1, verifier F-7).

        `ABSENT` 는 「행이 하나도 오지 않았다」는 뜻이다. 그 둘이 어긋난 값(`row_count=0` 인데
        `COVERED`)을 만들 수 있으면 판정문이 스스로 모순된 것을 실을 수 있다 — 지금 생성자는
        판정 경로 하나뿐이지만 타입이 그것을 보증하지는 않았다.

        문턱 쪽(`UNDERPOWERED` ↔ `COVERED`)은 **여기서 볼 수 없다**: 그 경계는 정책의 창당
        표본 하한이고 이 값은 그것을 모른다. 그 축은 `run._division_coverage` 와 그 자리를
        재는 test 가 진다 — 여기서 닫는 것은 정책을 몰라도 참이어야 하는 한 가지다.

        음수 행 수는 이 등식이 잡지 못한다(`not -1` 이 거짓이라 `COVERED` 와 짝이 맞는 것처럼
        보인다). 유일한 생성자가 `Counter` 로 세므로 도달하지 않고, 수를 적으면 숫자 리터럴
        게이트의 허용 목록을 늘려야 해서 닫지 않았다 — 알려진 제한."""
        absent = self.status is DivisionCoverage.ABSENT
        if absent != (not self.row_count):
            raise ValueError(
                "업무 대표 표지와 행 수가 어긋납니다 — "
                f"{self.division}: row_count={self.row_count}, status={self.status}"
            )


@dataclass(frozen=True)
class SnapshotRecord:
    """판정 JSON 이 싣는 입력 좌표 — 같은 값이면 같은 판정이 나와야 한다."""

    snapshot_id: str
    rows_sha256: str
    sample_list_sha256: str
    sample_size: int
    sampled_without_detail: int
    sampled_without_notice: int
    incomplete_axis: int
    sample_divisions: tuple[str, ...]
    """표본 목록에 **실제로 나타난** 업무 구분들(D-6G-53)."""

    sample_scope_divisions: tuple[str, ...]
    """**확정 범위**의 업무 구분들(D-6G-66). 최소 표본 문턱이 이 **수**로 정해지므로
    판정문이 그 근거를 싣는다 — 값이 보이지 않으면 문턱이 왜 그 값인지 알 수 없다."""

    division_coverage: tuple[DivisionCoverageRecord, ...]
    """확정 범위의 업무마다 온 행 수와 **대표 표지**(D-6G2c-17). 행 0 인 업무는 `ABSENT`,
    행은 있는데 창당 표본 하한에 못 미치면 `UNDERPOWERED` 로 공시된다 — 표본에서 사라지는
    대신 이름이 남아야 문턱이 내려가지 않은 이유가 읽힌다."""

    period_start: date
    period_end: date


@dataclass(frozen=True)
class WindowRecord:
    index: int
    start: date
    end: date
    notice_count: int
    history_count: int


@dataclass(frozen=True)
class SamplingRecord:
    """표본 크기 결정식의 입력과 결과(D-6G-20) — 판정 JSON 이 그대로 싣는다."""

    row_count: int
    """이 스냅숏에서 **행이 된** 공고 수(cr r3 L-8). 앞 판은 `sample_size` 라 불렀는데
    판정 JSON 안에 `snapshot.sample_size`(표본 파일의 키 수)가 따로 있어 **같은 이름이
    다른 것**을 가리켰다."""

    notice_observed_count: int
    """`has_*` 채움률의 분모(M-8) — 표본에서 공고 canonical 이 없던 것만 뺀 수.
    채움률이 하한인 이유가 이 수와 행 수의 차이이므로 함께 공시한다."""

    list_call_count: int
    detail_calls: int
    total_calls: int
    max_total_calls: int
    minimum_required_sample: int
    within_budget: bool
    meets_minimum: bool


@dataclass(frozen=True)
class VariantRecord:
    """이 판이 무엇을 뺐는가. `estimate_available` 이 거짓이면 「추정 규칙이 없어
    아무것도 빼지 못했다」는 뜻이고, 그것을 0 건으로 적으면 판정문이 거짓말을 한다."""

    variant: SampleVariant
    notice_count: int
    removed_count: int
    estimate_available: bool


@dataclass(frozen=True)
class BacktestStopped:
    reason: StopReason
    detail: str
    variant: SampleVariant
    snapshot: SnapshotRecord
    sampling: SamplingRecord
    fit: FitResult | None
    exclusions: tuple[tuple[ExclusionReason, int], ...]


@dataclass(frozen=True)
class BacktestVerdict:
    """실험 한 번의 산출물 전부 — 판정 JSON 의 원본."""

    policy_version: str
    policy_checksum: str
    variant: VariantRecord
    snapshot: SnapshotRecord
    sampling: SamplingRecord
    fit: FitResult
    exclusions: tuple[tuple[ExclusionReason, int], ...]
    undecidable: tuple[tuple[UndecidableAxis, int], ...]
    fill_rates: tuple[tuple[str, float], ...]
    """공고 축 여섯의 채움률(D-6G-46). 분모는 `sampling.notice_observed_count` 하나를
    공유하고, 분자는 행에서만 세므로 **하한**이다 — 판정 JSON 이 분모와 하한 표지를
    값 옆에 함께 싣는다."""

    unmeasured_sample_count: int
    """분모에는 있는데 분자를 셀 수 없는 표본 수(= `sampled_without_detail`). 이 수가
    0 보다 크면 위 여섯이 **엄격한 하한**이고, 0 이면 실측이다. 표지를 상수로 적지
    않고 이 수에서 파생하는 이유다 — 항상 참인 표지는 아무것도 말하지 않는다."""
    standard_market_price_scope: StandardMarketPriceScope
    base_amount_mismatch_count: int
    """두 출처의 기초금액이 다른 공고 수(D-6G-19). 값을 고치지 않고 계수만 공시한다 —
    어느 쪽이 옳은지는 이 레인이 판정할 일이 아니다."""

    limitations: tuple[str, ...]
    selected_windows: tuple[WindowRecord, ...]
    excluded_windows: tuple[WindowExclusion, ...]
    scored_notice_count: int
    seeds: tuple[int, ...]
    primary_names: tuple[str, ...]
    verdicts: tuple[StrategyVerdict, ...]
