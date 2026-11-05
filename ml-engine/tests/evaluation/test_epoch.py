"""RED — `ml_engine.evaluation.epoch`(M9/ml-lifecycle, ML-07 acceptance ③·⑥).

정책 effective date 가 **거동을 바꾸는 자리**를 잠근다: 같은 `Passed` 판정이 effective date
하나로 `Promotable` 과 `PromotionSuperseded` 로 갈린다. 수정 전에는 `effective_date` 칸이
없었고(로더가 미지 키로 거부) 승격은 정책이 언제부터의 규칙인지 묻지 않았다.

KST 경계가 이 모듈의 가장 날카로운 자리다 — UTC 날짜로 재면 월요일 오전 KST 에 시작한 창이
전날로 밀려 자기 effective date 의 창이 거부된다(SET-06 이 실측한 함정)."""

from __future__ import annotations

from datetime import UTC, date, datetime, time, timedelta

from ml_engine.evaluation.epoch import (
    POLICY_TIME_ZONE,
    Citable,
    CitationRejectionReason,
    NotCitable,
    cite_promotion_window,
    policy_epoch,
)
from ml_engine.evaluation.policy import EvaluationPolicy, policy_checksum
from ml_engine.evaluation.report import (
    Promotable,
    PromotionSuperseded,
    derive_promotion,
)
from ml_engine.evaluation.verdict import Passed
from ml_engine.inference.maturity import week_start_utc

_EFFECTIVE = date(2026, 9, 16)


def _policy(*, effective_date: date = _EFFECTIVE) -> EvaluationPolicy:
    return EvaluationPolicy(
        version="evaluation-v1",
        effective_date=effective_date,
        paired_t_threshold=2.58,
        gate_baseline="category_x_band",
        gate_model="gbm_all_strata",
        gate_stratum="clean-base",
        maturity_threshold=0.70,
        min_evaluation_rows=100,
        max_origins=5,
        agency_baseline_min_count=10,
        stability_seeds=(20260812, 1, 7, 42, 2026),
        amount_band_edges=(1e8, 5e8, 1e9, 5e9),
        segment_axes=("category", "amount_band"),
    )


_PASSED = Passed(
    baseline_rmse=0.1,
    model_rmse=0.05,
    improvement_ratio=0.5,
    paired_t=-3.0,
    min_detectable_improvement=0.1,
    required_row_count=None,
)


def _kst_day_start(day: date) -> datetime:
    return datetime.combine(day, time.min, tzinfo=POLICY_TIME_ZONE)


def test_policy_epoch_carries_version_checksum_and_kst_day_start() -> None:
    policy = _policy()
    epoch = policy_epoch(policy)
    assert epoch.version == policy.version
    assert epoch.checksum == policy_checksum(policy)
    assert epoch.effective_at == _kst_day_start(_EFFECTIVE)
    # KST 일 시작은 UTC 로 전날 15:00 이다 — 날짜가 하루 당겨지는 것이 바로 함정의 자리다.
    assert epoch.effective_at.astimezone(UTC).date() < _EFFECTIVE


def test_epoch_changes_with_effective_date_only() -> None:
    """effective date 하나만 달라도 epoch 가 갈린다 — checksum 이 그 칸을 포함한다."""
    first = policy_epoch(_policy())
    second = policy_epoch(_policy(effective_date=_EFFECTIVE + timedelta(days=1)))
    assert first.version == second.version
    assert first.checksum != second.checksum
    assert first.effective_at != second.effective_at


def test_window_starting_at_effective_moment_is_citable() -> None:
    citation = cite_promotion_window(
        policy=_policy(), window_start=_kst_day_start(_EFFECTIVE)
    )
    assert isinstance(citation, Citable)


def test_window_before_effective_date_is_not_citable_with_reason() -> None:
    citation = cite_promotion_window(
        policy=_policy(),
        window_start=_kst_day_start(_EFFECTIVE) - timedelta(microseconds=1),
    )
    assert isinstance(citation, NotCitable)
    assert citation.reason == CitationRejectionReason.MEASURED_BEFORE_EFFECTIVE_DATE
    assert "evaluation-v1" in citation.detail


def test_monday_morning_kst_window_is_not_pushed_to_the_previous_day() -> None:
    """KST 월요일 오전에 시작한 창은 그 날짜의 effective date 로 **인용 가능**하다.
    UTC 날짜로 재면 전날(일요일)로 밀려 거부된다 — 그 판을 이 test 가 거부한다."""
    monday_kst_morning = _kst_day_start(_EFFECTIVE) + timedelta(minutes=30)
    assert monday_kst_morning.astimezone(UTC).date() < _EFFECTIVE  # 함정의 전제
    citation = cite_promotion_window(
        policy=_policy(), window_start=monday_kst_morning
    )
    assert isinstance(citation, Citable)


def test_naive_window_start_is_read_as_utc() -> None:
    """naive datetime 은 UTC 로 간주한다(성숙도 커널과 같은 계약) — 로컬 시각으로 읽혀
    경계가 아홉 시간 움직이지 않는다."""
    boundary_utc = _kst_day_start(_EFFECTIVE).astimezone(UTC)
    naive_just_before = boundary_utc.replace(tzinfo=None) - timedelta(minutes=1)
    naive_at_boundary = boundary_utc.replace(tzinfo=None)
    assert isinstance(
        cite_promotion_window(policy=_policy(), window_start=naive_just_before),
        NotCitable,
    )
    assert isinstance(
        cite_promotion_window(policy=_policy(), window_start=naive_at_boundary),
        Citable,
    )


def test_kst_day_boundary_agrees_with_settlement_maturity_kernel() -> None:
    """cross-layer 등식 — epoch 의 KST 일 시작과 성숙도 커널의 KST 주 시작이 **같은
    경계**다. 두 모듈은 상수를 공유할 수 없다(evaluation → inference 는 forbidden 계약)
    — 그래서 공유가 아니라 등식으로 닫는다.

    2026-09-14 는 월요일이라 그 날의 KST 일 시작이 곧 KST 주 시작이다."""
    monday = date(2026, 9, 14)
    assert monday.weekday() == 0
    day_start = _kst_day_start(monday)
    assert week_start_utc(day_start) == day_start
    assert week_start_utc(day_start + timedelta(hours=12)) == day_start


def test_same_passed_verdict_splits_on_policy_effective_date() -> None:
    """ML-07 acceptance ⑥ — 「정책 version 전후로 판정이 갈린다」의 측정 자리. 판정·창·
    표본이 전부 같고 **정책의 effective date 만** 다른 두 실행이 서로 다른 승격 측정을
    낸다."""
    window_start = _kst_day_start(_EFFECTIVE)
    window_start_text = window_start.isoformat()
    earlier_policy = _policy(effective_date=_EFFECTIVE - timedelta(days=1))
    later_policy = _policy(effective_date=_EFFECTIVE + timedelta(days=1))

    promotable = derive_promotion(
        _PASSED,
        window_start=window_start_text,
        citation=cite_promotion_window(
            policy=earlier_policy, window_start=window_start
        ),
    )
    superseded = derive_promotion(
        _PASSED,
        window_start=window_start_text,
        citation=cite_promotion_window(policy=later_policy, window_start=window_start),
    )
    assert promotable == Promotable(window_start=window_start_text)
    assert isinstance(superseded, PromotionSuperseded)
    assert superseded.reason == CitationRejectionReason.MEASURED_BEFORE_EFFECTIVE_DATE
