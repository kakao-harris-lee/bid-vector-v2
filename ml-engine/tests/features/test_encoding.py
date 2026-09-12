"""RED — `ml_engine.features.encoding`(D-5B-6·D-5B-7). 2단 pseudo-count 수축,
`MappingProxyType` 봉인, `category_sample_count` 유도, κ 필수 인자(기본값 없음)."""

from __future__ import annotations

from types import MappingProxyType

import pytest

from ml_engine.features.encoding import (
    SHIPPED_ENCODING_POLICY,
    AwardRateObservation,
    EncodingPolicy,
    build_agency_target_encoding,
)

_POLICY = EncodingPolicy(agency_prior_strength=12.0, category_prior_strength=40.0)


def test_encoding_policy_requires_both_kappas_no_defaults() -> None:
    import inspect

    signature = inspect.signature(EncodingPolicy)
    for name in ("agency_prior_strength", "category_prior_strength"):
        assert signature.parameters[name].default is inspect.Parameter.empty


def test_shipped_encoding_policy_matches_policy_values_md() -> None:
    assert (
        EncodingPolicy(agency_prior_strength=12.0, category_prior_strength=40.0)
        == SHIPPED_ENCODING_POLICY
    )


def test_build_agency_target_encoding_requires_policy_kwarg() -> None:
    import inspect

    signature = inspect.signature(build_agency_target_encoding)
    assert "policy" in signature.parameters
    assert signature.parameters["policy"].default is inspect.Parameter.empty


def test_build_agency_target_encoding_seals_tables_read_only() -> None:
    observations = [AwardRateObservation(agency="a1", category="civil", value=0.8)]
    encoding = build_agency_target_encoding(observations, policy=_POLICY)
    assert isinstance(encoding.agency_means, MappingProxyType)
    assert isinstance(encoding.category_means, MappingProxyType)


def test_encode_observed_agency_returns_own_shrunk_mean_and_count() -> None:
    observations = [
        AwardRateObservation(agency="a1", category="civil", value=0.9),
        AwardRateObservation(agency="a1", category="civil", value=0.9),
    ]
    encoding = build_agency_target_encoding(observations, policy=_POLICY)
    mean, count = encoding.encode(agency="a1", category="civil")
    assert count == 2
    # 표본이 얕아 전역 평균(=0.9)으로 수축돼도 값은 그 근방.
    assert 0.0 <= mean <= 1.0


def test_encode_unobserved_agency_falls_back_to_category_mean_with_zero_count() -> None:
    observations = [
        AwardRateObservation(agency="a1", category="civil", value=0.7),
        AwardRateObservation(agency="a2", category="civil", value=0.9),
    ]
    encoding = build_agency_target_encoding(observations, policy=_POLICY)
    mean, count = encoding.encode(agency="never-seen-agency", category="civil")
    assert count == 0
    assert mean == encoding.category_means["civil"]


def test_encode_unobserved_agency_and_category_falls_back_to_global_mean() -> None:
    observations = [
        AwardRateObservation(agency="a1", category="civil", value=0.7),
    ]
    encoding = build_agency_target_encoding(observations, policy=_POLICY)
    mean, count = encoding.encode(
        agency="never-seen-agency", category="never-seen-category"
    )
    assert count == 0
    assert mean == encoding.global_mean


def test_encode_category_none_bypasses_category_means_straight_to_global_mean() -> None:
    """category 가 결측(None)일 때는 (agency,category) 복합 키를 만들 수 없어 전역 평균으로
    직행한다 — falsy→빈문자열 접힘(폐기된 관례)과 다른 경로임을 구분한다."""
    observations = [
        AwardRateObservation(agency="a1", category="civil", value=0.7),
        AwardRateObservation(agency="a2", category="electrical", value=0.3),
    ]
    encoding = build_agency_target_encoding(observations, policy=_POLICY)
    mean, count = encoding.encode(agency="a1", category=None)
    assert count == 0
    assert mean == encoding.global_mean


def test_category_sample_count_sums_across_agencies() -> None:
    observations = [
        AwardRateObservation(agency="a1", category="civil", value=0.7),
        AwardRateObservation(agency="a2", category="civil", value=0.8),
        AwardRateObservation(agency="a3", category="electrical", value=0.2),
    ]
    encoding = build_agency_target_encoding(observations, policy=_POLICY)
    assert encoding.category_sample_count("civil") == 2
    assert encoding.category_sample_count("electrical") == 1
    assert encoding.category_sample_count("never-seen") == 0


def test_build_agency_target_encoding_empty_observations_yields_zero_global_mean() -> (
    None
):
    encoding = build_agency_target_encoding([], policy=_POLICY)
    assert encoding.global_mean == 0.0
    assert dict(encoding.agency_means) == {}
    assert dict(encoding.category_means) == {}


def test_agency_target_encoding_is_frozen() -> None:
    encoding = build_agency_target_encoding(
        [AwardRateObservation(agency="a1", category="civil", value=0.5)], policy=_POLICY
    )
    with pytest.raises((AttributeError, TypeError)):
        encoding.global_mean = 0.0  # type: ignore[misc]
