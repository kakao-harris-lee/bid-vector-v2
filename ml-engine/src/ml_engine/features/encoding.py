"""Reuse: bid-vector/app/domain/award_rate_features.py@ed4b06c

`ml_engine.features.encoding` — 발주기관 → 공종 → 전역 2단 pseudo-count 수축 인코딩
(D-5B-6·D-5B-7). legacy `AgencyTargetEncoding`·`AwardRateObservation`·
`build_agency_target_encoding`·`_ObservationTotals`·`_collect_totals`·
`_shrunk_category_means`·`_shrunk_agency_means`·`_accumulate`를 그대로 가져왔다.

수행한 수정(reuse.md와 대조): (1) κ 둘(`agency_prior_strength`·`category_prior_strength`)의
**기본값을 제거**하고 `EncodingPolicy` 필수 인자 하나로 외부화했다(D-5B-7 — legacy는
모듈 상수를 함수 기본값으로 썼다, 매직넘버 금지). (2) 정규화(`normalize_feature_key`)는
이 모듈에서 하지 않는다 — legacy는 `_collect_totals`/`build_row`에서 원문 키를 직접
정규화했지만, V2에서는 `facts.py`가 wire fact를 검증하며 이미 정규화된 키를 낸다(관심사
분리 — 이 모듈은 이미 정규화된 문자열 키만 받는다). (3) `encode`의 `category` 인자를
`str | None`으로 넓혔다 — `category`가 결측(wire Missing)이면 `(agency, category)` 복합
키 자체를 만들 수 없어 전역 평균으로 직행해야 하는데, legacy는 이 경우를 `normalize_
feature_key(None) == ""`로 접어 `category_means.get("", ...)`가 우연히 폴백되게
했다(falsy→빈 문자열 접힘, V2에서 폐기 대상). `category=None`을 타입으로 받아 그 접힘
없이 같은 폴백 결과를 낸다.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from types import MappingProxyType
from typing import Final

from ml_engine.features.shrinkage import shrink_toward


@dataclass(frozen=True)
class EncodingPolicy:
    """수축 강도 κ 둘 — 기본값 없음(D-5B-7, 매직넘버를 코드 상수로 되돌리지 않는다)."""

    agency_prior_strength: float
    category_prior_strength: float


# 출하 값 — legacy-behavior(근거 주석 없음, `reports/evidence/m5/5b/policy-values.md` §1
# 승인). 재학습이 튜닝 근거를 내면 이 상수와 policy-values.md를 함께 갱신한다.
SHIPPED_ENCODING_POLICY: Final[EncodingPolicy] = EncodingPolicy(
    agency_prior_strength=12.0,
    category_prior_strength=40.0,
)


@dataclass(frozen=True)
class AwardRateObservation:
    """인코딩 표를 만들 때 쓰는 관측 한 건(값만 — ORM 도, 시각도 없다). 키는 이미 정규화된
    문자열이어야 한다(정규화는 `facts.py`/`normalize.py` 소관)."""

    agency: str
    category: str
    value: float


@dataclass(frozen=True)
class AgencyTargetEncoding:
    """발주기관 → 공종 → 전역으로 수축된 라벨 평균 표. 두 표는 생성 시점에 사본을 읽기
    전용으로 봉인한다(서빙 캐시 공유 안전 근거, legacy 그대로)."""

    agency_means: Mapping[tuple[str, str], tuple[float, int]]
    category_means: Mapping[str, float]
    global_mean: float

    def __post_init__(self) -> None:
        object.__setattr__(
            self, "agency_means", MappingProxyType(dict(self.agency_means))
        )
        object.__setattr__(
            self, "category_means", MappingProxyType(dict(self.category_means))
        )

    def category_sample_count(self, category: str) -> int:
        """이 공종을 뒷받침한 학습 표본 수 — `agency_means`에서 유도(새 필드 없음)."""
        return sum(
            count
            for (_agency, entry_category), (_mean, count) in self.agency_means.items()
            if entry_category == category
        )

    def encode(self, *, agency: str, category: str | None) -> tuple[float, int]:
        """(수축된 평균, 그 평균을 뒷받침한 기관 표본 수).

        `category`가 `None`이면(결측) `(agency, category)` 복합 키를 만들 수 없어 공종
        폴백도 건너뛰고 전역 평균으로 직행한다 — falsy→빈 문자열 접힘과 다른 경로다.
        """
        if category is None:
            return self.global_mean, 0
        observed = self.agency_means.get((agency, category))
        if observed is not None:
            return observed
        return self.category_means.get(category, self.global_mean), 0


@dataclass(frozen=True)
class _ObservationTotals:
    """수축 이전의 원시 집계 — 계층별 (합, 개수)와 전역 평균."""

    agency: dict[tuple[str, str], tuple[float, int]]
    category: dict[str, tuple[float, int]]
    global_mean: float


def _collect_totals(observations: Iterable[AwardRateObservation]) -> _ObservationTotals:
    """관측을 한 번 훑어 기관·공종·전역 집계를 동시에 쌓는다(키는 이미 정규화됐다고 가정)."""
    agency_totals: dict[tuple[str, str], tuple[float, int]] = {}
    category_totals: dict[str, tuple[float, int]] = {}
    global_sum = 0.0
    global_count = 0
    for observation in observations:
        value = float(observation.value)
        global_sum += value
        global_count += 1
        _accumulate(category_totals, observation.category, value)
        _accumulate(agency_totals, (observation.agency, observation.category), value)
    return _ObservationTotals(
        agency=agency_totals,
        category=category_totals,
        global_mean=global_sum / global_count if global_count else 0.0,
    )


def _shrunk_category_means(
    totals: _ObservationTotals, *, prior_strength: float
) -> dict[str, float]:
    """공종 평균을 전역 평균으로 수축한다 — 표본이 얕은 신생 공종만 실제로 접힌다."""
    return {
        key: shrink_toward(
            total / count,
            count,
            prior_mean=totals.global_mean,
            prior_strength=prior_strength,
        )[0]
        for key, (total, count) in totals.category.items()
    }


def _shrunk_agency_means(
    totals: _ObservationTotals,
    category_means: Mapping[str, float],
    *,
    prior_strength: float,
) -> dict[tuple[str, str], tuple[float, int]]:
    """기관 평균을 자기 공종 평균(없으면 전역)으로 수축하고 표본 수를 함께 낸다."""
    return {
        key: (
            shrink_toward(
                total / count,
                count,
                prior_mean=category_means.get(key[1], totals.global_mean),
                prior_strength=prior_strength,
            )[0],
            count,
        )
        for key, (total, count) in totals.agency.items()
    }


def build_agency_target_encoding(
    observations: Iterable[AwardRateObservation],
    *,
    policy: EncodingPolicy,
) -> AgencyTargetEncoding:
    """관측 집합에서 계층 수축 인코딩 표를 만든다(순수). 시간 누수는 호출부 책임 — 이
    커널은 시각을 보지 않는다(legacy 그대로)."""
    totals = _collect_totals(observations)
    category_means = _shrunk_category_means(
        totals, prior_strength=policy.category_prior_strength
    )
    return AgencyTargetEncoding(
        agency_means=_shrunk_agency_means(
            totals, category_means, prior_strength=policy.agency_prior_strength
        ),
        category_means=category_means,
        global_mean=totals.global_mean,
    )


def _accumulate[KeyT](
    totals: dict[KeyT, tuple[float, int]], key: KeyT, value: float
) -> None:
    """(합, 개수) 누적 한 벌 — 공종 계층과 기관 계층이 같은 식을 쓰게 한다."""
    total, count = totals.get(key, (0.0, 0))
    totals[key] = (total + value, count + 1)
