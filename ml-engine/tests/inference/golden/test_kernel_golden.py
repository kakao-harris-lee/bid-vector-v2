"""M5/5D — golden 통합(scope.md ⑨, D-M5-7 (a), 팀장 통합 라운드 M-3). curator 가 승인
받아 병합한 `ml-kernel-001~014`(authoritative, `fixtures/manifest.yaml`)를 production
타입으로 소비한다 — 대응 규칙은 `_adapter.py`에만 있다(production 코드에 golden 전용
표면 없음).

`ml-kernel-011`(계층 수축 가중치가 `Diagnostics.shrinkage_weight`로 응답에 실리는 것을
요구)은 명시 skip 한다 — GBM 예측 경로(`predict.py`)는 K5 를 쓰지 않고, 그 값을 잇는
분포 엔진 조립은 `OPEN-5D-DISTRIBUTION-ENGINE`(5D-2 몫, checklist.md 알려진 제한 8·9)
이다. 부분 단언(예: `resolve_assessment_posterior`만 따로 확인)은 금지 — case 의
`verified_paths`가 요구하는 전체(진단 객체에 실제로 실리는 값)를 만족하지 못하면서
초록을 내는 것은 조용한 통과이기 때문이다(팀장 지시).
"""

from __future__ import annotations

import hashlib
import math
import statistics
from decimal import Decimal

import pytest
from _adapter import (
    fraction,
    load_ml_kernel_cases,
    parse_instant,
    policy_with,
    provenance_from_label,
    shipped_policy,
    sign_marker_to_int,
)

from ml_engine.features import (
    NameMismatch,
    Undeclared,
    UnsupportedSchema,
    require_declared,
    resolve_schema,
    verify_feature_names,
)
from ml_engine.inference.assessment import (
    AssessmentProvenance,
    AssessmentSample,
    admit_clean,
    aggregate_level_observation,
)
from ml_engine.inference.maturity import (
    NoObservation,
    Observed,
    SettlementObservation,
    build_weekly_maturity,
    week_start_utc,
)
from ml_engine.inference.predict import Available, segment_availability
from ml_engine.inference.reserve_draw import (
    DrawMeanDistribution,
    draw_mean_moments,
    exact_draw_mean_distribution,
)
from ml_engine.inference.results import Unmeasurable, UnmeasurableReason
from ml_engine.inference.rounding import quantize_bid_rate
from ml_engine.inference.scenario import build_scenario_candidates
from ml_engine.registry.artifact import (
    ArtifactRejected,
    _VerifiedBytes,
    _verify_checksum,
)

_CASES = load_ml_kernel_cases()


def test_golden_manifest_has_all_fourteen_cases() -> None:
    """M-3 통합 전 skip 사유였던 부재 자체가 이제 거짓임을 먼저 확인한다."""
    assert set(_CASES) == {f"ml-kernel-{i:03d}" for i in range(1, 15)}


def test_ml_kernel_001_002_gate_distinguishes_never_trained_from_shallow() -> None:
    """001: 학습 행 0 → UNTRAINED_SEGMENT. 002: 임계 바로 아래(23<24) → INSUFFICIENT_SAMPLES.
    `detail` 토큰 이름은 잠그지 않지만(D-5D-2 미승인 어휘), 두 case 의 detail 은 서로
    달라야 한다(verified_projections differs-from-case, ML-02 acceptance 첫째 「구별」)."""
    base = shipped_policy()
    results = {}
    for case_id in ("ml-kernel-001", "ml-kernel-002"):
        case = _CASES[case_id]
        inp, exp = case["input"], case["expected"]
        policy = policy_with(
            base, gbm_min_category_rows=inp["policy"]["gbm"]["minCategoryRows"]
        )
        result = segment_availability(
            inp["artifact"]["segmentTrainingRowCount"], policy
        )
        results[case_id] = result

        assert exp["measurable"] is False
        assert isinstance(result, Unmeasurable)
        assert result.reason.value == exp["reason"]
        assert exp["declaredMinimumRows"] == inp["policy"]["gbm"]["minCategoryRows"]
        assert exp["effectiveMinimumRows"] == max(
            1, inp["policy"]["gbm"]["minCategoryRows"]
        )
        assert (
            exp["segmentTrainingRowCount"] == inp["artifact"]["segmentTrainingRowCount"]
        )
        assert exp["candidateCount"] == 0
        assert exp["scoreEmitted"] is False
        assert exp["gateAndPredictShareOneDecision"] is True

    assert results["ml-kernel-001"].reason is UnmeasurableReason.UNTRAINED_SEGMENT
    assert results["ml-kernel-002"].reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert results["ml-kernel-001"].detail != results["ml-kernel-002"].detail


def test_ml_kernel_003_gate_passes_at_exact_threshold() -> None:
    """임계는 `>=`다 — 같음은 통과 쪽(001·002 와 함께 N-1/N 경계를 고정)."""
    case = _CASES["ml-kernel-003"]
    inp, exp = case["input"], case["expected"]
    base = shipped_policy()
    policy = policy_with(
        base, gbm_min_category_rows=inp["policy"]["gbm"]["minCategoryRows"]
    )
    result = segment_availability(inp["artifact"]["segmentTrainingRowCount"], policy)

    assert exp["measurable"] is True
    assert isinstance(result, Available)
    assert exp["reason"] is None
    assert exp["detail"] is None
    assert exp["declaredMinimumRows"] == inp["policy"]["gbm"]["minCategoryRows"]
    assert exp["effectiveMinimumRows"] == max(
        1, inp["policy"]["gbm"]["minCategoryRows"]
    )
    assert exp["segmentTrainingRowCount"] == inp["artifact"]["segmentTrainingRowCount"]
    assert exp["gatePassedAtExactThreshold"] is True
    assert exp["gateAndPredictShareOneDecision"] is True


def test_ml_kernel_004_zero_threshold_cannot_disable_guard() -> None:
    """정책이 임계로 `0`을 줘도(선언), 하한 1 클램프(코드 불변식)가 게이트를 계속
    막는다 — 설정으로 이 가드를 끌 수 없다(D-5D-3)."""
    case = _CASES["ml-kernel-004"]
    inp, exp = case["input"], case["expected"]
    declared = inp["policy"]["gbm"]["minCategoryRows"]
    base = shipped_policy()
    policy = policy_with(base, gbm_min_category_rows=declared)

    assert declared == 0
    assert exp["declaredMinimumRows"] == declared
    assert exp["effectiveMinimumRows"] == max(1, declared) == 1
    assert exp["guardDisabledByPolicy"] is False

    for probe, exp_probe in zip(inp["probes"], exp["probes"], strict=True):
        result = segment_availability(
            probe["artifact"]["segmentTrainingRowCount"], policy
        )
        if exp_probe["measurable"]:
            assert isinstance(result, Available)
            assert exp_probe["reason"] is None
            assert exp_probe["detail"] is None
        else:
            assert isinstance(result, Unmeasurable)
            assert result.reason.value == exp_probe["reason"]
            assert result.detail.value == exp_probe["detail"]


def test_ml_kernel_005_checksum_gate_rejects_before_object_construction() -> None:
    """checksum 대조 단계만 겨눈다 — curator 합성 바이트는 `ArtifactManifestV1` 전체
    스키마를 만족하지 않으므로(not_covered: writer 계약은 5C 몫), `_VerifiedBytes`
    생성 여부를 이 게이트가 실제로 만드는 유일한 객체의 대리 지표로 쓴다."""
    case = _CASES["ml-kernel-005"]
    inp, exp = case["input"], case["expected"]
    declared_checksum = inp["expectedRelease"]["artifactChecksum"]
    assert declared_checksum == exp["declaredChecksum"]

    for probe, exp_probe in zip(inp["probes"], exp["probes"], strict=True):
        raw = probe["artifactBytesUtf8"].encode("utf-8")
        actual_checksum = hashlib.sha256(raw).hexdigest()
        assert actual_checksum == exp_probe["computedChecksum"]
        assert (actual_checksum == declared_checksum) == exp_probe["checksumMatches"]

        result = _verify_checksum(raw, declared_checksum)
        assert isinstance(result, _VerifiedBytes) == exp_probe["loadedArtifactCreated"]
        assert isinstance(result, ArtifactRejected) == exp_probe["rejected"]

    intact_raw = inp["probes"][0]["artifactBytesUtf8"].encode("utf-8")
    tampered_raw = inp["probes"][1]["artifactBytesUtf8"].encode("utf-8")
    assert len(intact_raw) == len(tampered_raw)
    assert exp["probes"][1]["sameByteLength"] is True
    differing = sum(1 for a, b in zip(intact_raw, tampered_raw, strict=True) if a != b)
    assert differing == exp["probes"][1]["differingByteCount"]
    assert exp["rejectionHappensBeforeObjectConstruction"] is True


def test_ml_kernel_006_feature_names_require_exact_sequence() -> None:
    """같은 집합의 재배열도, 진부분집합도 거부된다 — 수용은 정확한 순열 일치다."""
    case = _CASES["ml-kernel-006"]
    inp, exp = case["input"], case["expected"]
    schema = resolve_schema(inp["schemaVersion"])
    assert not isinstance(schema, UnsupportedSchema)
    assert exp["acceptanceIsExactSequenceEquality"] is True

    any_subset_accepted = False
    for probe, exp_probe in zip(inp["probes"], exp["probes"], strict=True):
        names = probe["artifactFeatureNames"]
        same_multiset = sorted(names) == sorted(inp["schemaColumnOrder"])
        same_order = names == inp["schemaColumnOrder"]
        result = verify_feature_names(names, schema)
        accepted = not isinstance(result, NameMismatch)
        if not same_multiset and accepted:
            any_subset_accepted = True

        assert same_multiset == exp_probe["sameMultiset"]
        assert same_order == exp_probe["sameOrder"]
        assert accepted == exp_probe["accepted"]
        assert (not accepted) == exp_probe["inferenceRejected"]

    assert any_subset_accepted is False
    assert exp["subsetAcceptedAnywhere"] is False


def test_ml_kernel_007_feature_schema_version_gate() -> None:
    """지원 목록 밖 version 은 거부, 미선언은 기본값을 채우지 않고 거부한다."""
    case = _CASES["ml-kernel-007"]
    inp, exp = case["input"], case["expected"]

    for probe, exp_probe in zip(inp["probes"], exp["probes"], strict=True):
        raw_version = probe["artifactFeatureSchemaVersion"]
        declared = require_declared("feature_schema_version", raw_version)
        if isinstance(declared, Undeclared):
            declared_in_list = False
            accepted = False
        else:
            schema = resolve_schema(declared)
            declared_in_list = not isinstance(schema, UnsupportedSchema)
            accepted = declared_in_list
        assert declared_in_list == exp_probe["declaredInSupportedList"]
        assert accepted == exp_probe["accepted"]
        assert accepted == exp_probe["loadedArtifactCreated"]

    assert exp["nearestSupportedVersionSubstituted"] is False
    assert exp["defaultVersionAssumedWhenUndeclared"] is False


def _reserve_price_literal(value: object) -> float:
    if value == "NaN":
        return math.nan
    if value == "Infinity":
        return math.inf
    return float(value)  # type: ignore[arg-type]


def test_ml_kernel_008_reserve_draw_singular_inputs_never_fold_or_raise() -> None:
    """경계 입력 일곱(분산 0·NaN·Inf·음수·표본 부족·빈 입력·추첨 수 0)은 예외도 값도
    아닌 사유 있는 측정 불가를 낸다 — `0`으로 접히지 않는다."""
    case = _CASES["ml-kernel-008"]
    inp, exp = case["input"], case["expected"]

    any_value_emitted = False
    any_folded_to_zero = False
    for probe, exp_probe in zip(inp["probes"], exp["probes"], strict=True):
        values = [_reserve_price_literal(v) for v in probe["reservePriceValues"]]
        result = draw_mean_moments(values, probe["drawCount"])
        if isinstance(result, Unmeasurable):
            assert exp_probe["measurable"] is False
            assert exp_probe["valueEmitted"] is False
            assert exp_probe["reasonPresent"] is True
            # 경계별 reason·detail 토큰 배정은 verified_paths 밖(승인 문면 없음,
            # fixtures/manifest.yaml ml-kernel-008 not_covered) — 그 값 자체는
            # tests/inference/test_reserve_draw.py 의 일반 test(정책 무관, golden
            # 파일 미참조)가 경계 일곱 전부에 대해 이미 고정한다.
        else:
            any_value_emitted = True
            mean_value, _std = result
            if mean_value == 0.0:
                any_folded_to_zero = True

    assert exp["exceptionRaisedAnywhere"] is False
    assert exp["everyProbeCarriesReason"] is True
    assert exp["anyProbeFoldedToZero"] is False
    assert any_folded_to_zero is False
    assert exp["anyProbeEmittedValue"] is False
    assert any_value_emitted is False


def test_ml_kernel_009_deterministic_scenario_candidates_are_thread_count_invariant() -> (
    None
):
    """같은 아티팩트·입력에서 스레드 수만 다른 두 실행이 후보 3 의 투찰율을 같은 십진
    문자열로 낸다(scale 보존) — 비교는 `Rate.fraction` 문자열 동일이어야 D-5D-1 의 경계
    `Decimal` 변환이 실제로 검증된다."""
    case = _CASES["ml-kernel-009"]
    inp, exp = case["input"], case["expected"]
    scenario_cfg = inp["policy"]["scenario"]
    label_order = {"CONSERVATIVE": 0, "BASE": 1, "AGGRESSIVE": 2}
    ordered = sorted(scenario_cfg["candidates"], key=lambda c: label_order[c["label"]])

    base = shipped_policy()
    policy = policy_with(
        base,
        version=inp["policy"]["version"],
        scenario_z=Decimal(str(scenario_cfg["z"])),
        scenario_clamp_min=Decimal(str(scenario_cfg["clampMin"])),
        scenario_clamp_max=Decimal(str(scenario_cfg["clampMax"])),
        scenario_bid_rate_digits=scenario_cfg["bidRateDigits"],
        scenario_weights=tuple(Decimal(str(c["weight"])) for c in ordered),
        scenario_z_signs=tuple(sign_marker_to_int(c["zSign"]) for c in ordered),
    )
    center = float(fraction(inp["centerBidRate"]))
    std = float(fraction(inp["residualStd"]))
    scale = float(inp["scale"])

    labels_seen: list[str] = []
    for run_idx, run in enumerate(inp["runs"]):
        candidates = build_scenario_candidates(
            center=center, std=std, policy=policy, scale=scale
        )
        assert isinstance(candidates, tuple)
        exp_run = exp["runs"][run_idx]
        assert run["numThreads"] == exp_run["numThreads"]
        labels_seen = [c.label.value for c in candidates]
        for candidate, exp_candidate in zip(
            candidates, exp_run["candidates"], strict=True
        ):
            assert candidate.label.value == exp_candidate["label"]
            assert str(candidate.bid_rate) == exp_candidate["bidRate"]["fraction"]
            # `weight`·`weight_policy_version`·weightSum 은 verified_paths 밖(출하
            # 정책 값은 OPEN-5D-POLICY-VALUES 소유, fixtures/manifest.yaml
            # ml-kernel-009 not_covered) — tests/inference/test_scenario.py 의
            # `test_weight_is_decimal_without_quantize`(정책 주입, golden 파일 미참조)와
            # tests/inference/test_policy.py 의 합계 불변식 test 가 이미 고정한다.

    assert labels_seen == exp["candidateOrder"]
    assert exp["fractionStringsIdenticalAcrossRuns"] is True
    assert exp["comparedAsDecimalString"] is True
    assert exp["scaleDigits"] == policy.scenario_bid_rate_digits
    assert exp["clampApplied"] is False


def test_ml_kernel_010_non_clean_provenance_never_reaches_aggregation() -> None:
    """`clean` 이외 provenance 의 행은 집계 입력에 들어가지 못하고, 제외 수가 조용히
    사라지지 않고 계수된다 — 배제 축은 값의 생김새가 아니라 provenance 태그다."""
    case = _CASES["ml-kernel-010"]
    inp, exp = case["input"], case["expected"]

    samples = [
        AssessmentSample(
            center=float(fraction(s["assessmentRate"])),
            provenance=provenance_from_label(s["provenance"]),
        )
        for s in inp["samples"]
    ]
    clean, excluded = admit_clean(samples)

    admitted_ids = [
        s["sampleId"]
        for s, sample in zip(inp["samples"], samples, strict=True)
        if sample.provenance is AssessmentProvenance.CLEAN
    ]
    excluded_ids = [s["sampleId"] for s in inp["samples"] if s["provenance"] != "CLEAN"]

    assert admitted_ids == exp["admittedSampleIds"]
    assert len(clean) == exp["admittedCount"]
    assert excluded_ids == exp["excludedSampleIds"]
    assert excluded == exp["excludedObservations"]
    assert exp["nonCleanAdmittedCount"] == 0
    assert exp["silentlyDropped"] is False
    assert exp["admittedBecauseValueLooksNumeric"] is False

    observation = aggregate_level_observation(clean)
    assert observation is not None
    assert observation.sample_count == exp["levelObservationSampleCount"]
    assert quantize_bid_rate(observation.mean, 4) == Decimal(
        exp["levelObservationMean"]["fraction"]
    )


@pytest.mark.skip(reason="OPEN-5D-DISTRIBUTION-ENGINE — 5D-2 가 소비")
def test_ml_kernel_011_shrinkage_weight_carried_in_response() -> None:
    """대응 불가 — 부분 단언(예: `resolve_assessment_posterior`만 확인) 금지 지시에 따라
    구현하지 않는다. case 의 `verified_paths`는 `Diagnostics.shrinkageWeight`가 **응답에
    실리는 것**까지 요구하고, GBM 예측 경로(`predict.py`)는 K5 를 쓰지 않아 그 값의
    생산자가 없다(항상 `Decimal("0")`, checklist.md 알려진 제한 8·9). 분포 엔진 조립은
    `OPEN-5D-DISTRIBUTION-ENGINE`(5D-2 몫)이 만족시킨다."""


def test_ml_kernel_012_zero_opened_is_no_observation_not_a_zero_ratio() -> None:
    """개찰이 하나도 없는 주는 비율 `0.0`이 아니라 `NoObservation`이고 성숙도 표에
    나타나지 않는다 — 개찰은 있고 정산이 `0`인 주(0/2)와 값·표 등재 여부가 갈린다."""
    case = _CASES["ml-kernel-012"]
    inp, exp = case["input"], case["expected"]
    observations = [
        SettlementObservation(
            opened_at=parse_instant(o["openedAt"]), settled=o["settled"]
        )
        for o in inp["observations"]
    ]
    window_days = inp["policy"]["maturity"]["windowDays"]
    windows = build_weekly_maturity(observations, window_days=window_days)
    windows_by_start = {window.start: window for window in windows}

    for exp_window in exp["windows"]:
        requested_start = parse_instant(exp_window["weekStartKst"])
        normalized_start = week_start_utc(requested_start)
        window = windows_by_start.get(normalized_start)
        present = window is not None
        assert present == exp_window["presentInTable"]
        if not present:
            assert exp_window["state"] == "NoObservation"
            assert exp_window["ratio"] is None
            continue
        maturity = window.maturity
        if exp_window["state"] == "Observed":
            assert isinstance(maturity, Observed)
            assert maturity.opened_count == exp_window["openedCount"]
            assert maturity.settled_count == exp_window["settledCount"]
            assert maturity.ratio == float(Decimal(exp_window["ratio"]["fraction"]))
        else:
            assert isinstance(maturity, NoObservation)

    assert exp["zeroOpenedRatioFoldedToZero"] is False
    assert exp["zeroOpenedDistinctFromZeroSettled"] is True
    assert exp["weeksWithoutObservationsPresentInTable"] is False


def test_ml_kernel_013_closed_form_equals_full_enumeration() -> None:
    """15 개 예비가격에서 4 추첨 평균의 완전열거 (평균, 분산)과 유한모집단 보정 닫힌식의
    (평균, 분산)이 정확히 같다 — 이식에서 수학이 바뀌지 않았다는 유일한 관측 가능한 증거."""
    case = _CASES["ml-kernel-013"]
    inp, exp = case["input"], case["expected"]
    values = [float(v) for v in inp["reservePriceValues"]]
    draw_count = inp["drawCount"]

    assert len(values) == exp["sourceCount"] == inp["expectedPriceCount"]
    assert draw_count == exp["drawCount"]

    population_mean = statistics.fmean(values)
    population_variance = statistics.pvariance(values)
    assert population_mean == float(exp["populationMean"])
    assert population_variance == float(exp["populationVariance"])

    exact = exact_draw_mean_distribution(values, draw_count)
    assert isinstance(exact, DrawMeanDistribution)
    moments = draw_mean_moments(values, draw_count)
    assert not isinstance(moments, Unmeasurable)
    moment_mean, moment_std = moments

    assert len(exact.support) == exp["supportSize"]
    assert exact.mean == moment_mean
    assert (
        exact.mean
        == float(exp["drawMeanFromEnumeration"])
        == float(exp["drawMeanFromClosedForm"])
    )
    assert (exact.std**2) == pytest.approx(
        float(exp["drawMeanVarianceFromEnumeration"]), rel=1e-9
    )
    assert (moment_std**2) == pytest.approx(
        float(exp["drawMeanVarianceFromClosedForm"]), rel=1e-9
    )
    assert exact.std == pytest.approx(moment_std, rel=1e-12)
    assert exp["exactlyEqualAsRationals"] is True
    assert exp["agreeWithinDeclaredPrecision"] is True
    assert exact.std == float(exp["drawMeanStd"])
    assert min(exact.support) == float(exp["supportMin"])
    assert max(exact.support) == float(exp["supportMax"])


def test_ml_kernel_014_week_boundary_is_half_open_at_monday_00_00_kst() -> None:
    """주 경계는 KST 월요일 00:00 이고 구간은 `[start, end)`다 — 경계 정각은 다음 주에,
    그 직전 순간은 이전 주에 든다."""
    case = _CASES["ml-kernel-014"]
    inp, exp = case["input"], case["expected"]

    first_week_start: object = None
    for observation, exp_assignment in zip(
        inp["observations"], exp["assignments"], strict=True
    ):
        instant = parse_instant(observation["openedAt"])
        week_start = week_start_utc(instant)
        expected_start = parse_instant(exp_assignment["assignedWeekStartUtc"])
        assert week_start == expected_start
        if first_week_start is None:
            first_week_start = week_start
        assert (week_start > first_week_start) == exp_assignment["assignedToLaterWeek"]

    assert exp["weekStartInstantAssignedToStartingWeek"] is True
    assert exp["weekEndInstantAssignedToPreviousWeek"] is True
    assert exp["adjacentWindowsOverlap"] is False
    assert exp["zoneUsedForWeekPartition"] == "Asia/Seoul"
    assert exp["partitionedInUtcCalendarDays"] is False
