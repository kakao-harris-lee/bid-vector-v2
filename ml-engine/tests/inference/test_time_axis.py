"""RED — `ml_engine.inference.time_axis`(M9/ml-lifecycle, SET-06 acceptance 「시간축
provenance」·「결측의 명시적 처리」).

legacy 는 개찰 시각이 없으면 **마감 시각으로 조용히 대체**했고 대체 사실이 결과에 남지
않았다(capability-map SET-06 「legacy 형태 처리 — 시간축 대체」). V2 는 그 형태를 채택하지
않는다: 대체는 **승인된 정책으로만** 허용하고(`OPEN-SET-10` 미결이라 출하값은 미승인),
대체 출처와 정책 version 을 결과에 보존하며, 대체 불가 행은 **사유 있는 측정 불가**로
산출한다.

구조로 닫은 자리 둘:
- 「출처를 모르는데 시각 값은 있다」가 **타입으로 표현되지 않는다** — `UnmeasurableTimeAxis`
  에는 시각 필드가 없다.
- 「대체를 코드에서 승인한다」가 막힌다 — `TimeAxisPolicy` 를 짓는 자리가 src 에 하나이고
  그것이 정책 객체에서 읽는 함수다."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

import pytest
import yaml

from ml_engine.inference.policy import (
    InferencePolicy,
    load_inference_policy,
    time_axis_policy_of,
)
from ml_engine.inference.time_axis import (
    KnownTimeAxis,
    TimeAxisPolicy,
    TimeAxisSource,
    TimeAxisSubstitute,
    TimeAxisUnmeasurableReason,
    UnmeasurableTimeAxis,
    resolve_time_axis,
)
from tests.ast_sites import construction_sites

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_SRC = _ML_ENGINE_ROOT / "src" / "ml_engine"
_SHIPPED_INFERENCE_POLICY = _ML_ENGINE_ROOT / "policy" / "inference-v1.yaml"

_OPENED_AT = datetime(2026, 9, 14, 3, 0, tzinfo=UTC)
_BID_CLOSE_AT = datetime(2026, 9, 14, 2, 0, tzinfo=UTC)

_STRICT = TimeAxisPolicy(version="test", substitute_approved=False)
_PERMISSIVE = TimeAxisPolicy(version="test", substitute_approved=True)


def test_opened_at_is_the_primary_axis_and_carries_provenance() -> None:
    axis = resolve_time_axis(opened_at=_OPENED_AT, bid_close_at=None, policy=_STRICT)
    assert isinstance(axis, KnownTimeAxis)
    assert axis.value == _OPENED_AT
    assert axis.source == TimeAxisSource.OPENED_AT
    assert axis.policy_version == "test"


def test_opened_at_wins_even_when_substitution_is_approved() -> None:
    """대체는 **결측일 때만**이다 — 승인됐다고 개찰 시각을 밀어내지 않는다."""
    axis = resolve_time_axis(
        opened_at=_OPENED_AT, bid_close_at=_BID_CLOSE_AT, policy=_PERMISSIVE
    )
    assert isinstance(axis, KnownTimeAxis)
    assert axis.source == TimeAxisSource.OPENED_AT
    assert axis.value == _OPENED_AT


def test_unapproved_substitute_is_unmeasurable_not_a_silent_fallback() -> None:
    """legacy 의 조용한 대체가 **거부**된다 — 값이 있어도 승인 밖 출처면 측정 불가다."""
    axis = resolve_time_axis(opened_at=None, bid_close_at=_BID_CLOSE_AT, policy=_STRICT)
    assert isinstance(axis, UnmeasurableTimeAxis)
    assert axis.reason == TimeAxisUnmeasurableReason.UNAPPROVED_TIME_AXIS_SOURCE


def test_approved_substitute_is_known_with_its_own_source() -> None:
    """승인되면 쓰되 **어느 시각 종류였는지**가 결과에 남는다(provenance 소실 없음)."""
    axis = resolve_time_axis(
        opened_at=None, bid_close_at=_BID_CLOSE_AT, policy=_PERMISSIVE
    )
    assert isinstance(axis, KnownTimeAxis)
    assert axis.source == TimeAxisSource.BID_CLOSE_AT
    assert axis.value == _BID_CLOSE_AT


def test_no_time_value_at_all_is_unmeasurable_with_its_own_reason() -> None:
    axis = resolve_time_axis(opened_at=None, bid_close_at=None, policy=_PERMISSIVE)
    assert isinstance(axis, UnmeasurableTimeAxis)
    assert axis.reason == TimeAxisUnmeasurableReason.NO_TIME_AXIS_VALUE


def test_unmeasurable_time_axis_cannot_carry_a_time_value() -> None:
    """「출처를 모르는데 값은 있다」를 적을 자리가 없다 — 주를 고르려면 값을 지어내야
    하고, 지어내면 성숙도 표의 항등식이 어긋난다."""
    axis = resolve_time_axis(opened_at=None, bid_close_at=None, policy=_STRICT)
    assert not hasattr(axis, "value")
    assert not hasattr(axis, "source")


def test_shipped_inference_policy_does_not_approve_substitution() -> None:
    """`OPEN-SET-10` 미결 상태의 **기계적 표현** — 운영자가 결정하면 정책 파일의 이 한
    칸이 바뀐다(코드에는 승인 목록이 없다)."""
    policy = load_inference_policy(_SHIPPED_INFERENCE_POLICY)
    assert isinstance(policy, InferencePolicy), policy
    assert policy.maturity_time_axis_substitute is TimeAxisSubstitute.NOT_APPROVED
    axis_policy = time_axis_policy_of(policy)
    assert axis_policy.substitute_approved is False
    assert axis_policy.version == policy.version


def test_time_axis_policy_is_constructed_only_from_the_versioned_policy() -> None:
    """「대체를 코드에서 승인한다」를 막는 자리 — src 안에서 `TimeAxisPolicy` 를 짓는 곳이
    하나이고, 그 하나가 정책 객체에서 값을 읽는 함수다. 이름 해석은 공용 보조가 한다
    (별칭·재대입 한 단계 포함, cr M-6 — 두 게이트가 **같은 본문**을 쓴다)."""
    assert construction_sites("TimeAxisPolicy") == {
        ("inference/policy.py", "time_axis_policy_of")
    }


def _policy_text_with_substitute(raw: str) -> str:
    """출하 정책 **원문**의 대체 승인 한 줄만 `raw` 로 바꾼다 — 따옴표 없이, 적은 그대로.

    v r2 M-2 — 앞 판은 값을 `yaml.safe_dump` 로 썼고, safe_dump 는 `yes` 를 `'yes'` 로
    **인용한다**. 그래서 실제 공격 벡터(비인용 `yes` → YAML 1.1 이 bool `True` 로 읽는다)가
    파일에 한 번도 들어가지 않았다 — 그 test 는 문자열 여덟만 재고 있었다."""
    original = _SHIPPED_INFERENCE_POLICY.read_text(encoding="utf-8")
    replaced = original.replace(
        'maturity.time_axis_substitute: "NOT_APPROVED"',
        f"maturity.time_axis_substitute: {raw}",
    )
    assert replaced != original, "대체 승인 줄을 찾지 못했다"
    return replaced


@pytest.mark.parametrize(
    ("raw", "parses_to_bool"),
    [
        ("yes", True),  # YAML 1.1 참값 — 앞 판의 bool 칸은 이것을 **승인으로** 읽었다
        ("on", True),
        ("y", False),  # YAML 1.2 코어 스키마로 읽는 PyYAML 에서는 문자열이다
        ("true", True),
        ("True", True),
        ("1", False),  # int
        ("APPROVED_SOMEDAY", False),
        ("approved", False),
        ('"yes"', False),  # 인용판 — 앞 판 test 가 실제로 재던 것
    ],
)
def test_loose_truthy_spellings_never_become_an_approval(
    tmp_path: Path, raw: str, parses_to_bool: bool
) -> None:
    """승인은 닫힌 어휘 두 단어 중 하나를 적어야만 생긴다.

    `parses_to_bool` 은 **입력이 실제로 YAML bool 로 들어갔는지**를 함께 단언한다 — 그
    단언이 없으면 「bool 을 승인으로 받는」 변이가 이 test 를 지나갈 수 있다(파일에 bool 이
    한 번도 없으면 그 변이는 아무것도 바꾸지 않는다, 앞 판의 결함)."""
    path = tmp_path / "inference.yaml"
    path.write_text(_policy_text_with_substitute(raw), encoding="utf-8")

    parsed = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert isinstance(parsed["maturity.time_axis_substitute"], bool) is parses_to_bool

    assert not isinstance(load_inference_policy(path), InferencePolicy), raw


def test_the_closed_vocabulary_actually_loads(tmp_path: Path) -> None:
    """양성 대조 — 위 거부 test 들이 「비튼 칸과 무관한 이유로」 통과하지 않는다."""
    values = dict(yaml.safe_load(_SHIPPED_INFERENCE_POLICY.read_text(encoding="utf-8")))
    for member in TimeAxisSubstitute:
        values["maturity.time_axis_substitute"] = member.value
        path = tmp_path / f"inference-{member.value}.yaml"
        path.write_text(yaml.safe_dump(values), encoding="utf-8")
        loaded = load_inference_policy(path)
        assert isinstance(loaded, InferencePolicy), (member, loaded)
        assert loaded.maturity_time_axis_substitute is member
        assert time_axis_policy_of(loaded).substitute_approved is (
            member is TimeAxisSubstitute.APPROVED
        )
