"""RED — `ml_engine.training.holdout`(scope ⑦, `Reuse:
award_rate_holdout.py@ed4b06c`). `run_holdout`유일 실행 진입점 — 합성 코퍼스로 창
분할·경계 동시각·양쪽 비어 있음·seed 안정성·report 왕복을 확인한다."""

from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta

import numpy as np

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.evaluation import (
    BaselineSpec,
    EvaluationPolicy,
    EvaluationReportV1,
    Failed,
    ModelScore,
    NotEvaluable,
    Passed,
    derive_promotion,
)
from ml_engine.evaluation.report import PromotionNotEvaluable
from ml_engine.evaluation.windows import (
    WeekMaturity,
    WindowExclusion,
    WindowExclusionReason,
)
from ml_engine.training._holdout_fit import ModelFit, Split, WindowSkip, build_split
from ml_engine.training._holdout_window import _BaselineFit, _run_stability
from ml_engine.training.booster import BoosterLike, LightGbmTrainer, TrainerFailed
from ml_engine.training.corpus import AdmittedCorpus, admit_corpus
from ml_engine.training.dataset import DatasetManifestV1, LoadedDataset, RawTrainingRow
from ml_engine.training.holdout import (
    HoldoutRejected,
    HoldoutRejectionReason,
    _unaccounted_or_reject,
    _WindowsOutcome,
    run_holdout,
)
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.spec import LightGbmHyperparameters, TrainingSpec
from ml_engine.training.train import CodeVersion

_HYPERPARAMETERS = LightGbmHyperparameters(
    objective="regression",
    metric="rmse",
    learning_rate=0.3,
    num_leaves=7,
    min_data_in_leaf=1,
    feature_fraction=1.0,
    bagging_fraction=1.0,
    bagging_freq=0,
    lambda_l2=0.0,
    verbosity=-1,
    deterministic=True,
    force_row_wise=True,
    num_threads=1,
)

_EPOCH = datetime(2026, 1, 1, tzinfo=UTC)


def _spec(**overrides: object) -> TrainingSpec:
    base: dict[str, object] = dict(
        version="test-holdout-v1",
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=3,
        encoding_folds=2,
        seed=20260812,
        min_residual_std=0.002,
    )
    base.update(overrides)
    return TrainingSpec(**base)  # type: ignore[arg-type]


def _policy(**overrides: object) -> EvaluationPolicy:
    base: dict[str, object] = dict(
        version="test",
        paired_t_threshold=2.58,
        gate_baseline="category_x_band",
        gate_model="gbm_all_strata",
        gate_stratum="clean-base",
        maturity_threshold=0.70,
        min_evaluation_rows=2,
        max_origins=5,
        agency_baseline_min_count=2,
        stability_seeds=(20260812, 1),
        amount_band_edges=(1e8, 5e8, 1e9, 5e9),
        segment_axes=("category", "amount_band"),
    )
    base.update(overrides)
    return EvaluationPolicy(**base)  # type: ignore[arg-type]


def _feature_inputs(*, category: str, agency: str) -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.amount_won = 200_000_000
    inputs.base_amount.value.currency = common_pb2.CURRENCY_KRW
    inputs.base_amount.value.basis = common_pb2.BASIS_BASE_AMOUNT
    inputs.base_amount.value.provenance = common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED
    inputs.category_code.value = category
    inputs.agency_id.value = agency
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    return inputs


def _feature_inputs_missing_base_amount(
    *, category: str, agency: str
) -> features_pb2.FeatureInputs:
    """verifier H-1 재현 — `base_amount`가 wire `Missing`(구조적으로 유효, `admit_corpus`
    는 거부하지 않는다). `is_buildable`/`build_row`만 이 행을 걸러낸다(5C-1
    `test_train_artifact.py::_feature_inputs_missing_base_amount`와 같은 패턴)."""
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.missing = common_pb2.MISSING_REASON_NOT_COLLECTED_YET
    inputs.category_code.value = category
    inputs.agency_id.value = agency
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    return inputs


def _raw_row(
    day: int, *, category: str, agency: str, label: float, stratum: str = "clean-base"
) -> RawTrainingRow:
    return RawTrainingRow(
        feature_inputs=_feature_inputs(category=category, agency=agency),
        label_value=label,
        opened_at=_EPOCH + timedelta(days=day),
        stratum=stratum,
    )


def _raw_row_missing_base_amount(
    day: int, *, category: str, agency: str, label: float, stratum: str = "clean-base"
) -> RawTrainingRow:
    return RawTrainingRow(
        feature_inputs=_feature_inputs_missing_base_amount(
            category=category, agency=agency
        ),
        label_value=label,
        opened_at=_EPOCH + timedelta(days=day),
        stratum=stratum,
    )


def _dataset(
    rows: tuple[RawTrainingRow, ...], *, dataset_id: str = "ds-holdout"
) -> LoadedDataset:
    manifest = DatasetManifestV1(
        dataset_id=dataset_id,
        sample_scope="feed-origin-only",
        feed_origin_only=True,
        row_count=len(rows),
        rows_checksum="0" * 64,
        opened_at_first=rows[0].opened_at if rows else _EPOCH,
        opened_at_last=rows[-1].opened_at if rows else _EPOCH,
        feature_schema_version="award-rate-features-v2",
    )
    return LoadedDataset(manifest=manifest, raw_rows=rows)


class _DeterministicBooster:
    """행렬 첫 열(공종 코드)로 예측하는 결정적 부스터 — LightGBM 없이도 재현성·판정
    구조를 검증할 수 있게 한다."""

    def __init__(self, base: float, slope: float) -> None:
        self._base = base
        self._slope = slope

    def predict(self, matrix: np.ndarray) -> np.ndarray:
        return self._base + self._slope * matrix[:, 0]

    def model_to_string(self) -> str:
        return "fake"

    def feature_name(self) -> list[str]:
        return ["category", "log_amount", "agency_encoding", "agency_sample", "denom"]


class _DeterministicTrainer:
    def __init__(self, base: float = 0.7, slope: float = 0.0) -> None:
        self._base = base
        self._slope = slope

    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ) -> BoosterLike | TrainerFailed:
        if matrix.shape[0] == 0:
            return TrainerFailed("empty matrix")
        return _DeterministicBooster(self._base, self._slope)


def _training_policy(min_training_rows: int = 5) -> TrainingPolicy:
    return TrainingPolicy(version="test-v1", min_training_rows=min_training_rows)


def _many_rows(
    n: int, *, start_day: int = 0, stratum: str = "clean-base"
) -> tuple[RawTrainingRow, ...]:
    return tuple(
        _raw_row(
            start_day + i,
            category="civil" if i % 2 == 0 else "it",
            agency=f"agency-{i % 3}",
            label=0.6 + 0.001 * (i % 5),
            stratum=stratum,
        )
        for i in range(n)
    )


def test_run_holdout_rejects_empty_dataset() -> None:
    dataset = _dataset(())
    result = run_holdout(
        dataset,
        [],
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert isinstance(result, HoldoutRejected)
    assert result.reason == HoldoutRejectionReason.EMPTY_SIDE


def test_run_holdout_rejects_overlapping_maturity_windows() -> None:
    rows = _many_rows(10)
    dataset = _dataset(rows)
    week1 = WeekMaturity(
        start=_EPOCH, end=_EPOCH + timedelta(days=7), opened_count=5, settled_count=4
    )
    overlapping = WeekMaturity(
        start=_EPOCH + timedelta(days=3),
        end=_EPOCH + timedelta(days=10),
        opened_count=5,
        settled_count=4,
    )
    result = run_holdout(
        dataset,
        [week1, overlapping],
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert isinstance(result, HoldoutRejected)
    assert result.reason == HoldoutRejectionReason.INVALID_MATURITY_INPUT


def test_run_holdout_with_no_evaluable_windows_still_produces_report() -> None:
    rows = _many_rows(10)
    dataset = _dataset(rows)
    immature = WeekMaturity(
        start=_EPOCH + timedelta(days=8),
        end=_EPOCH + timedelta(days=15),
        opened_count=10,
        settled_count=1,
    )
    result = run_holdout(
        dataset,
        [immature],
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert result.windows == ()
    assert len(result.excluded_windows) == 1
    assert result.excluded_windows[0].reason == WindowExclusionReason.IMMATURE
    assert isinstance(result.promotion, PromotionNotEvaluable)


def _selected_window_scenario(
    min_training_rows: int = 5,
) -> tuple[LoadedDataset, WeekMaturity]:
    """학습측 20행 + 평가 창 안 10행(성숙도 0.9, min_evaluation_rows=2 충족)."""
    train_part = _many_rows(20, start_day=0)
    window_start_day = 30
    eval_part = _many_rows(10, start_day=window_start_day)
    rows = train_part + eval_part
    dataset = _dataset(rows)
    window = WeekMaturity(
        start=_EPOCH + timedelta(days=window_start_day),
        end=_EPOCH + timedelta(days=window_start_day + 7),
        opened_count=10,
        settled_count=9,
    )
    return dataset, window


def test_run_holdout_evaluates_selected_window_and_produces_gate_outcome() -> None:
    dataset, window = _selected_window_scenario()
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(base=0.6, slope=0.0),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert len(result.windows) == 1
    window_result = result.windows[0]
    assert isinstance(window_result.outcome, (Passed, Failed, NotEvaluable))
    assert window_result.release_id
    assert len(window_result.stability.trials) == len(_policy().stability_seeds)
    # 헤드라인 seed 가 목록 선두
    assert window_result.stability.trials[0].seed == _spec().seed


def test_stability_trials_headline_seed_first_even_when_not_first_in_policy() -> None:
    """verifier r1 H-2 변이 #7 재현 — 기존 test 는 헤드라인 seed 가 정책 목록에서도
    이미 첫 자리였다(20260812, 1). `stability_seed_order`가 `tuple(policy.
    stability_seeds)`로 퇴화해도 그 test 는 우연히 통과한다 — 헤드라인이 목록
    **끝**에 있는 정책으로 재조립이 실제로 일어나는지 확인한다."""
    dataset, window = _selected_window_scenario()
    policy = _policy(stability_seeds=(1, 7, 20260812))  # 헤드라인(20260812)이 끝
    result = run_holdout(
        dataset,
        [window],
        _spec(),  # seed=20260812
        _training_policy(),
        policy,
        _DeterministicTrainer(base=0.6, slope=0.0),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    trials = result.windows[0].stability.trials
    assert len(trials) == 3
    assert trials[0].seed == _spec().seed


def test_run_stability_trial_passed_reflects_significance_not_rmse_alone() -> None:
    """verifier r2 MEDIUM M-2r 재현 — 변이 ⑥(`_run_stability`의 `passed`에서
    `paired_t_threshold` 조건 삭제)이 652 passed 로 통과했다. 기존
    `test_passes_gate_is_the_single_predicate_definition`은 `gate_outcome`의
    결과만 보고 안정성 sweep 의 **호출 지점**은 보지 않는다. 이 test 는
    `_run_stability`를 white-box 로 직접 불러(`build_split`과 같은 관행)
    RMSE 는 이기지만(`trial_rmse=9.987… < baseline_rmse=10.0`) 유의하지
    않은(`|t|=0.0256 ≪ threshold 2.58`) trial 을 headline seed 자체로
    구성한다 — `passed`가 RMSE 만으로 판정했다면 `True`, `passes_gate`의
    두 조건을 다 쓰면 `False`다."""
    targets = np.array([0.0, 0.0, 0.0, 0.0])
    baseline_predictions = np.array([10.0, 10.0, 10.0, 10.0])
    # 손 계산 확인(스크립트 실측): baseline_rmse=10.0, trial_rmse≈9.9875(개선),
    # paired_t≈-0.0256(2.58 문턱에 한참 못 미침) — RMSE 조건만 True, 유의성 조건 False.
    trial_predictions = np.array(
        [9.486832980505138, 9.486832980505138, 9.486832980505138, 11.357816691600547]
    )
    split = Split(
        gate_train=[],
        train_rows_all=[],
        usable_test_rows=[],
        dropped_rows=(),
        gate_train_raw=(),
        train_rows_raw=(),
        targets=targets,
        usable_facts=[],
        gate_train_facts=[],
        gate_train_labels=np.array([0.0]),
        gate_train_mean=0.0,
    )
    baselines = _BaselineFit(
        scores=(),
        gate_predictions=baseline_predictions,
        gate_covered=np.array([True, True, True, True]),
        gate_rmse=10.0,
        gate_spec=BaselineSpec(name="test", key=lambda facts: ""),
    )
    fit = ModelFit(
        trained_all=None,  # type: ignore[arg-type]  # _run_stability 의 headline 경로는 안 씀
        predictions_all=trial_predictions,
        model_score=ModelScore(
            name="gbm_all_strata", rmse=9.987492177719089, bias=0.0, residual_std=0.0
        ),
        conservative_score=None,
    )
    spec = _spec()
    policy = _policy(stability_seeds=(spec.seed,))  # 헤드라인 하나뿐 — 재학습 없음
    summary = _run_stability(
        _dataset(()),
        split,
        spec,
        _training_policy(),
        policy,
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
        fit,
        baselines,
    )
    assert not isinstance(summary, WindowSkip)
    assert len(summary.trials) == 1
    trial = summary.trials[0]
    assert trial.improvement_ratio > 0  # RMSE 는 실제로 개선됐다(전제 확인).
    assert abs(trial.paired_t) < policy.paired_t_threshold  # 유의하지 않다(전제 확인).
    assert trial.passed is False


def test_build_split_boundary_row_at_window_start_excluded_from_training() -> None:
    """verifier r1 H-2 변이 #1 재현 — `_structural_rows`의 `opened_at < window.start`
    를 `<= window.start`로 바꾸면 경계 동시각 행이 학습측에도 새어 든다(D-5C2-8,
    누수는 크기가 아니라 비대칭). 경계 행은 평가측에만 있어야 한다."""
    window_start_day = 30
    train_part = _many_rows(20, start_day=0)
    boundary_row = _raw_row(window_start_day, category="civil", agency="a1", label=0.6)
    # window 는 [day30, day37) — day30(경계)+day31~36(6일) = 7행이 안에 들어간다.
    eval_rest = _many_rows(6, start_day=window_start_day + 1)
    rows = (*train_part, boundary_row, *eval_rest)
    dataset = _dataset(rows)
    admitted = admit_corpus(dataset.raw_rows)
    assert isinstance(admitted, AdmittedCorpus)
    ordered = tuple(sorted(admitted.rows, key=lambda row: row.opened_at))
    window = WeekMaturity(
        start=_EPOCH + timedelta(days=window_start_day),
        end=_EPOCH + timedelta(days=window_start_day + 7),
        opened_count=7,
        settled_count=6,
    )
    split = build_split(window, ordered, dataset, _policy(min_evaluation_rows=2))
    assert not isinstance(split, WindowSkip)
    # 경계 행(day30)은 평가측 7행에 들어가고, 학습측(day0~19, 20행)에는 없어야 한다.
    assert len(split.usable_test_rows) == 7
    assert len(split.gate_train) == 20


def _mixed_buildability_window_scenario(
    *, buildable_count: int, missing_count: int
) -> tuple[LoadedDataset, WeekMaturity]:
    """verifier r1 H-1 재현 — 창 안 구조적 행(opened_at·stratum 만 봄)과 실제로
    채점 가능한(buildable) 행 수가 다르다. `missing_count`행은 `base_amount`가 wire
    `Missing`이라 `admit_corpus`는 통과하지만 `is_buildable`은 걸러낸다."""
    train_part = _many_rows(20, start_day=0)
    window_start_day = 100
    buildable_rows = tuple(
        _raw_row(
            window_start_day + 1,
            category="civil" if i % 2 == 0 else "it",
            agency=f"agency-{i % 3}",
            label=0.6 + 0.001 * (i % 5),
        )
        for i in range(buildable_count)
    )
    missing_rows = tuple(
        _raw_row_missing_base_amount(
            window_start_day + 1,
            category="civil",
            agency=f"agency-{i % 3}",
            label=0.6 + 0.001 * (i % 5),
        )
        for i in range(missing_count)
    )
    rows = train_part + buildable_rows + missing_rows
    dataset = _dataset(rows)
    total = buildable_count + missing_count
    window = WeekMaturity(
        start=_EPOCH + timedelta(days=window_start_day),
        end=_EPOCH + timedelta(days=window_start_day + 7),
        opened_count=total,
        settled_count=max(total - 1, 1),
    )
    return dataset, window


def test_run_holdout_excludes_window_when_buildable_rows_below_min_evaluation_rows() -> (
    None
):
    """verifier r1 H-1 / code-reviewer HIGH — `min_evaluation_rows` 하한은 구조적
    행 수가 아니라 **실제로 채점되는(buildable) 행 수**에 재대조돼야 한다. 창 안
    120행 중 30행만 buildable, 정책 하한 100 → 창이 `INSUFFICIENT_EVALUATION_ROWS`
    로 제외돼야 한다(수정 전에는 `Passed`+`Promotable`까지 통과했다)."""
    dataset, window = _mixed_buildability_window_scenario(
        buildable_count=30, missing_count=90
    )
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=5),
        _policy(min_evaluation_rows=100),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert result.windows == ()
    assert len(result.excluded_windows) == 1
    assert (
        result.excluded_windows[0].reason
        == WindowExclusionReason.INSUFFICIENT_EVALUATION_ROWS
    )


def _run_holdout_with_mixed_buildability_exclusion(
    *, buildable_count: int, missing_count: int
) -> EvaluationReportV1:
    dataset, window = _mixed_buildability_window_scenario(
        buildable_count=buildable_count, missing_count=missing_count
    )
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=5),
        _policy(min_evaluation_rows=100),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    return result


def test_window_exclusion_reports_buildable_row_count_and_dropped_rows() -> None:
    """verifier r2 MEDIUM M-1r 재현 — 제외된 창(`INSUFFICIENT_EVALUATION_ROWS`)의
    문면은 `evaluation_row_count=120`(구조적 행 수)과 하한 100 을 나란히 실어
    **자기모순**(120 ≥ 100 인데 「행 부족」)이었다. `build_split`이 이미 계산한
    `buildable=30`·`dropped=[(base_amount, 90)]`(`WindowSkip.detail` 문자열에만
    있었다)를 `WindowExclusion`의 구조화 필드로 공시해 그 모순을 없앤다.

    verifier r3 MEDIUM M-1r(잔존) — 수정 전 이 단언은 전부 dataclass 필드였고
    `canonical_report_bytes`를 한 번도 지나지 않았다. `_window_exclusion_json`이
    두 필드를 직렬화하지 않아, 서명·저장되는 형태(canonical JSON/checksum)에서는
    공시가 사라지고 buildable 30 과 50 이 같은 checksum 을 냈다 — 이 test 를
    canonical bytes 를 파싱해 그 값을 보도록 바꾸고, 별도 test 로 checksum 이
    실제로 달라짐을 확인한다."""
    result = _run_holdout_with_mixed_buildability_exclusion(
        buildable_count=30, missing_count=90
    )
    assert len(result.excluded_windows) == 1
    excluded = result.excluded_windows[0]
    assert excluded.evaluation_row_count == 120
    assert excluded.buildable_row_count == 30
    dropped_total = sum(item.row_count for item in excluded.dropped_rows)
    assert dropped_total == 90
    # 자기모순 해소 확인 — buildable(30) < 하한(100) 이 실제 제외 사유임이 문면
    # 자체에서 산술로 성립한다(구조적 행 수 120 만으로는 알 수 없던 사실).
    assert excluded.buildable_row_count < 100 <= excluded.evaluation_row_count

    from ml_engine.evaluation.report import canonical_report_bytes

    canonical = canonical_report_bytes(result)
    assert isinstance(canonical, bytes)
    payload = json.loads(canonical)
    excluded_json = payload["excluded_windows"][0]
    # canonical JSON 에서도 같은 값이 나와야 한다 — dataclass 단언만으로는
    # 최종 산출물(서명·저장 대상)의 공백을 못 잡는다(r3 가 지적한 바로 그 층).
    assert excluded_json["buildable_row_count"] == 30
    assert sum(item["row_count"] for item in excluded_json["dropped_rows"]) == 90
    assert excluded_json["evaluation_row_count"] == 120
    # verifier r3 표적 정정 — _window_exclusion_json 이 실제로 키 둘을
    # "추가"했는지(대체·누락 없이)를 정확한 키 수로 고정한다: window_start·
    # window_end·window_opened_count·window_settled_count·reason·
    # evaluation_row_count(기존 6) + buildable_row_count·dropped_rows(신규 2) = 8.
    assert len(excluded_json) == 8


def _excluded_window_scenario_with_fixed_composition(
    *, missing_indices: frozenset[int]
) -> tuple[LoadedDataset, WeekMaturity]:
    """`test_window_exclusion_checksum_differs_by_buildable_row_count` 전용 —
    120개 창 행 각각의 category(짝/홀 인덱스)·agency·label 은 **인덱스 하나로만
    결정**되고 missing/buildable 여부와 무관하다. 그래서 어느 인덱스 집합을
    missing 으로 고르든 코퍼스 전체의 `category_counts`(civil 60·it 60)·
    `corpus_row_count`·`unaccounted_row_count` 등 **다른 report 필드는 전부
    동일**하게 유지되고, `buildable_row_count`/`dropped_rows`만 실제로 달라진다
    — checksum 차이의 원인을 그 두 필드로 좁힌다(`_mixed_buildability_window_
    scenario`는 missing 행을 전부 `category="civil"`로 고정해 이 목적에 안 맞음)."""
    train_part = _many_rows(20, start_day=0)
    window_start_day = 200
    rows = []
    for i in range(120):
        category = "civil" if i % 2 == 0 else "it"
        agency = f"agency-{i % 3}"
        label = 0.6 + 0.001 * (i % 5)
        if i in missing_indices:
            rows.append(
                _raw_row_missing_base_amount(
                    window_start_day + 1, category=category, agency=agency, label=label
                )
            )
        else:
            rows.append(
                _raw_row(
                    window_start_day + 1, category=category, agency=agency, label=label
                )
            )
    dataset = _dataset(train_part + tuple(rows))
    window = WeekMaturity(
        start=_EPOCH + timedelta(days=window_start_day),
        end=_EPOCH + timedelta(days=window_start_day + 7),
        opened_count=120,
        settled_count=119,
    )
    return dataset, window


def test_window_exclusion_checksum_differs_by_buildable_row_count() -> None:
    """verifier r3 MEDIUM M-1r(잔존) 재현 — buildable 30(dropped 90)과 buildable
    50(dropped 70)은 둘 다 하한 100 미달로 같은 사유(`INSUFFICIENT_EVALUATION_
    ROWS`)·같은 구조적 행 수(120)로 제외되지만, 실제로 버려진 행 수는 다르다.
    `_window_exclusion_json`이 그 필드를 직렬화하지 않으면 두 실행이 **같은
    checksum**을 낸다(수정 전 재현값) — 서로 다른 실행이 같은 서명을 갖는 것은
    서명의 존재 이유(어떤 실행 결과인지 식별)를 무너뜨린다. 두 시나리오의 코퍼스
    구성(category·agency·label 분포, 총 행 수)은 인덱스 기반으로 고정해
    `buildable_row_count`/`dropped_rows` 외의 어떤 report 필드도 달라지지 않게
    한다(전제 확인 포함)."""
    from ml_engine.evaluation.report import report_checksum

    def _run(missing_indices: frozenset[int]) -> EvaluationReportV1:
        dataset, window = _excluded_window_scenario_with_fixed_composition(
            missing_indices=missing_indices
        )
        result = run_holdout(
            dataset,
            [window],
            _spec(),
            _training_policy(min_training_rows=5),
            _policy(min_evaluation_rows=100),
            _DeterministicTrainer(),
            CodeVersion("sha-1"),
        )
        assert not isinstance(result, HoldoutRejected)
        return result

    result_30 = _run(frozenset(range(90)))  # buildable 30(90..119), dropped 90
    result_50 = _run(frozenset(range(70)))  # buildable 50(70..119), dropped 70

    # 전제 확인 — 정말로 buildable_row_count/dropped_rows 만 다르다는 것.
    assert result_30.corpus_row_count == result_50.corpus_row_count
    assert result_30.corpus_categories == result_50.corpus_categories
    assert result_30.unaccounted_row_count == result_50.unaccounted_row_count
    excluded_30 = result_30.excluded_windows[0]
    excluded_50 = result_50.excluded_windows[0]
    assert excluded_30.buildable_row_count == 30
    assert excluded_50.buildable_row_count == 50
    assert excluded_30.evaluation_row_count == excluded_50.evaluation_row_count == 120

    checksum_30 = report_checksum(result_30)
    checksum_50 = report_checksum(result_50)
    assert isinstance(checksum_30, str)
    assert isinstance(checksum_50, str)
    assert checksum_30 != checksum_50


def test_window_exclusion_leaves_buildable_fields_unset_for_planning_stage_exclusion() -> (
    None
):
    """계획 단계 제외(`IMMATURE`·`NO_TRAINING_ROWS`·`BEYOND_MAX_ORIGINS`)는
    buildability 재대조 자체가 실행되지 않는 자리다 — 실행 단계 필드를 임의의
    기본값으로 채우지 않는다(`None`/빈 tuple, 「몰라서 0」과 「실제로 0」을 구별)."""
    dataset = _dataset(_many_rows(5, start_day=0))
    immature_window = WeekMaturity(
        start=_EPOCH + timedelta(days=100),
        end=_EPOCH + timedelta(days=107),
        opened_count=1,
        settled_count=0,
    )
    result = run_holdout(
        dataset,
        [immature_window],
        _spec(),
        _training_policy(),
        _policy(min_evaluation_rows=2),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert len(result.excluded_windows) == 1
    excluded = result.excluded_windows[0]
    assert excluded.reason == WindowExclusionReason.IMMATURE
    assert excluded.buildable_row_count is None
    assert excluded.dropped_rows == ()


def test_window_result_reports_dropped_rows_for_unbuildable_facts() -> None:
    """H-A — buildability 로 버려진 행은 `WindowResult.dropped_rows`(사유별 계수)로
    공시된다. 하한(100)을 채우고도 남는 20행이 buildable 하지 않은 시나리오."""
    dataset, window = _mixed_buildability_window_scenario(
        buildable_count=100, missing_count=20
    )
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=5),
        _policy(min_evaluation_rows=100),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert len(result.windows) == 1
    window_result = result.windows[0]
    assert window_result.gate_test_row_count == 100
    dropped_total = sum(item.row_count for item in window_result.dropped_rows)
    assert dropped_total == 20


def test_unaccounted_row_count_adds_dropped_rows_not_just_window_membership() -> None:
    """H-A 회계식 정정 — `unaccounted_row_count`가 창 소속 여부만으로 「회계됨」을
    선언하지 않는다. train_part(20행, 이 단일 창 밖) + 버려진 5행이 함께 실린다."""
    train_part = _many_rows(20, start_day=0)
    window_start_day = 30
    buildable = tuple(
        _raw_row(
            window_start_day + 1,
            category="civil",
            agency="a1",
            label=0.6 + 0.001 * i,
        )
        for i in range(10)
    )
    missing = tuple(
        _raw_row_missing_base_amount(
            window_start_day + 1, category="civil", agency="a1", label=0.6
        )
        for _ in range(5)
    )
    rows = train_part + buildable + missing
    dataset = _dataset(rows)
    window = WeekMaturity(
        start=_EPOCH + timedelta(days=window_start_day),
        end=_EPOCH + timedelta(days=window_start_day + 7),
        opened_count=15,
        settled_count=14,
    )
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(),
        _policy(min_evaluation_rows=2),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert len(result.windows) == 1
    assert result.windows[0].gate_test_row_count == 10
    dropped_total = sum(item.row_count for item in result.windows[0].dropped_rows)
    assert dropped_total == 5
    # train_part 20행은 이 단일 창 밖(구조적 unaccounted) + 버려진 5행이 더해진다.
    assert result.unaccounted_row_count == 20 + 5


def test_unaccounted_row_count_does_not_double_count_execution_stage_skip() -> None:
    """verifier r2 HIGH H-2r 재현 — 창이 **계획 단계**(구조적 행 수 120 ≥ 하한 100)는
    통과하고 **실행 단계**(H-A 의 buildable 재대조, 30 < 100)에서 skip 되면,
    `plan.selected`(계획 통과분, skip 뒤에도 그대로 남음)와
    `windows_outcome.excluded`(skip 이 추가한 같은 창)에 그 창이 **둘 다** 잡혀
    이중 계수되고, `160 - 240 + 0 = -80`이 `max(…, 0)`에 걸려 0 이 된다(수정 전
    재현값). 창 밖 40행이 실제로 남아있으므로 정답은 40 이다."""
    outside = _many_rows(40, start_day=0)
    window_start_day = 100
    buildable = tuple(
        _raw_row(
            window_start_day + 1,
            category="civil" if i % 2 == 0 else "it",
            agency=f"agency-{i % 3}",
            label=0.6 + 0.001 * (i % 5),
        )
        for i in range(30)
    )
    missing = tuple(
        _raw_row_missing_base_amount(
            window_start_day + 1, category="civil", agency=f"agency-{i % 3}", label=0.6
        )
        for i in range(90)
    )
    rows = outside + buildable + missing
    dataset = _dataset(rows)
    window = WeekMaturity(
        start=_EPOCH + timedelta(days=window_start_day),
        end=_EPOCH + timedelta(days=window_start_day + 7),
        opened_count=120,
        settled_count=119,
    )
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=5),
        _policy(min_evaluation_rows=100),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    # 전제 확인 — 창이 실행 단계에서 skip 됐다는 것(계획 단계는 통과, 즉 이중 계수가
    # 실제로 발생할 조건이라는 것).
    assert result.windows == ()
    assert len(result.excluded_windows) == 1
    assert (
        result.excluded_windows[0].reason
        == WindowExclusionReason.INSUFFICIENT_EVALUATION_ROWS
    )
    assert result.unaccounted_row_count == 40


def test_unaccounted_or_reject_returns_accounting_mismatch_for_negative_result() -> (
    None
):
    """verifier r2 H-2r — `max(…, 0)` clamp 를 지운 자리를 그냥 두면 회계 결함이
    조용히 음수로 새거나(치명적이진 않지만 report 필드가 거짓), clamp 를 되살리면
    변이 저항이 없어진다(§5 표 「max 복원 → test 붉음」). `_unaccounted_or_reject`
    를 직접 호출해(white-box, `build_split`/`WindowSkip`과 같은 관행) **서로소가
    깨진** 상태(같은 창이 `succeeded_windows`와 `excluded` 양쪽에 있음, `_evaluate_
    windows`가 정상적으로는 절대 만들지 않는 인위적 구성)를 주입하면
    `HoldoutRejected(ACCOUNTING_MISMATCH)`가 나오는지 — clamp 로 감춘 0 이 아니라
    결과 타입 거부인지를 직접 확인한다."""
    rows = _many_rows(10, start_day=0)
    admitted = admit_corpus(rows)
    assert isinstance(admitted, AdmittedCorpus)
    ordered = tuple(sorted(admitted.rows, key=lambda row: row.opened_at))
    window = WeekMaturity(
        start=_EPOCH, end=_EPOCH + timedelta(days=7), opened_count=5, settled_count=4
    )
    # 인위적 구성 — 같은 창이 succeeded_windows 와 excluded 양쪽에 잡히게 해
    # 이중 계수(음수)를 직접 유발한다.
    windows_outcome = _WindowsOutcome(
        results=(),
        excluded=(
            WindowExclusion(
                window=window,
                reason=WindowExclusionReason.TRAINING_REJECTED,
                evaluation_row_count=10,
            ),
        ),
        succeeded_windows=(window,),
    )
    result = _unaccounted_or_reject(
        ordered, _policy(min_evaluation_rows=2), windows_outcome
    )
    assert isinstance(result, HoldoutRejected)
    assert result.reason == HoldoutRejectionReason.ACCOUNTING_MISMATCH


def test_run_holdout_accounting_invariant_unaccounted_is_zero_for_feed_origin_only() -> (
    None
):
    """회계 불변식은 **주어진 성숙도 창이 전 구간을 덮을 때만** 0 이 된다(legacy와
    같은 정의 — `unaccounted_row_count`는 선택+제외 창의 합집합 밖 행 수). 실제 운영은
    K7 `build_weekly_maturity`가 전 구간 주간표를 주므로 이 불변식이 항상 성립한다."""
    train_part = _many_rows(20, start_day=0)
    eval_part = _many_rows(10, start_day=30)
    dataset = _dataset(train_part + eval_part)

    def _immature(start_day: int) -> WeekMaturity:
        return WeekMaturity(
            start=_EPOCH + timedelta(days=start_day),
            end=_EPOCH + timedelta(days=start_day + 7),
            opened_count=1,
            settled_count=0,
        )

    selected_window = WeekMaturity(
        start=_EPOCH + timedelta(days=35),
        end=_EPOCH + timedelta(days=42),
        opened_count=5,
        settled_count=4,
    )
    maturities = [_immature(day) for day in (0, 7, 14, 21, 28)] + [selected_window]
    result = run_holdout(
        dataset,
        maturities,
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert result.unaccounted_row_count == 0
    assert len(result.windows) == 1


def test_run_holdout_is_reproducible_with_fake_trainer() -> None:
    dataset, window = _selected_window_scenario()
    first = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    second = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(first, HoldoutRejected)
    assert not isinstance(second, HoldoutRejected)
    from ml_engine.evaluation.report import canonical_report_bytes

    first_bytes = canonical_report_bytes(first)
    second_bytes = canonical_report_bytes(second)
    assert isinstance(first_bytes, bytes)
    assert first_bytes == second_bytes
    assert first.windows[0].release_id == second.windows[0].release_id


def test_run_holdout_two_windows_have_distinct_release_ids() -> None:
    train_part = _many_rows(30, start_day=0)
    window1_start = 40
    window2_start = 50
    eval1 = _many_rows(5, start_day=window1_start)
    eval2 = _many_rows(5, start_day=window2_start)
    rows = train_part + eval1 + eval2
    dataset = _dataset(rows)
    window1 = WeekMaturity(
        start=_EPOCH + timedelta(days=window1_start),
        end=_EPOCH + timedelta(days=window1_start + 7),
        opened_count=5,
        settled_count=4,
    )
    window2 = WeekMaturity(
        start=_EPOCH + timedelta(days=window2_start),
        end=_EPOCH + timedelta(days=window2_start + 7),
        opened_count=5,
        settled_count=4,
    )
    result = run_holdout(
        dataset,
        [window1, window2],
        _spec(),
        _training_policy(min_training_rows=5),
        _policy(min_evaluation_rows=2, max_origins=5),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert len(result.windows) == 2
    assert result.windows[0].release_id != result.windows[1].release_id
    assert result.holdout_overlaps  # 두 창 쌍에 대해 겹침이 측정된다
    assert all(overlap.row_count == 0 for overlap in result.holdout_overlaps)


def test_run_holdout_window_with_training_rejected_is_excluded() -> None:
    """min_training_rows 를 창의 학습 행 수보다 크게 잡아 TrainingRejected 를 유도."""
    dataset, window = _selected_window_scenario()
    result = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=10_000),
        _policy(),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    assert result.windows == ()
    assert any(
        item.reason == WindowExclusionReason.TRAINING_REJECTED
        for item in result.excluded_windows
    )


def test_run_holdout_promotion_uses_latest_evaluable_window_not_latest_calendar_window() -> (
    None
):
    """code-reviewer 「확인 불가」 처분(2026-09-16, 팀장 지시) — 시간상 가장 늦은
    선택 창이 제외되고(이 test 는 `INSUFFICIENT_EVALUATION_ROWS` 경로로 유도, H-A)
    더 이른 창만 성공했을 때, `_assemble_report`의 `latest = windows_outcome.results[-1]`
    은 **성숙·평가 가능했던 가장 최근 창**(=성공한 것 중 최신)을 승격 판정에 쓴다 —
    "달력상 가장 최근 창"이 아니다. 이것은 의도된 완화다: 계산이 아니라 결과가 있는
    창에서만 판정을 뽑는다(설계 검토 (1) 「창 실패는 창 제외로 흡수, report 는 여전히
    성공 반환」과 같은 원칙 — 실패한 최신 창이 존재한다고 승격 자체를 막지 않는다).
    scope.md/설계 검토 문면에는 이 경계가 없었다(code-reviewer 지적) — 이 test 와
    checklist.md 「알려진 제한」 등재로 문면화한다."""
    train_part = _many_rows(20, start_day=0)
    window1_start = 40  # 더 이른 창 — buildable, 성공해야 한다.
    window2_start = 80  # 달력상 더 늦은 창 — buildable 미달로 제외돼야 한다.
    window1_rows = _many_rows(10, start_day=window1_start + 1)
    window2_buildable = _many_rows(1, start_day=window2_start + 1)
    window2_missing = tuple(
        _raw_row_missing_base_amount(
            window2_start + 1, category="civil", agency="a1", label=0.6
        )
        for _ in range(9)
    )
    rows = train_part + window1_rows + window2_buildable + window2_missing
    dataset = _dataset(rows)
    window1 = WeekMaturity(
        start=_EPOCH + timedelta(days=window1_start),
        end=_EPOCH + timedelta(days=window1_start + 7),
        opened_count=10,
        settled_count=9,
    )
    window2 = WeekMaturity(
        start=_EPOCH + timedelta(days=window2_start),
        end=_EPOCH + timedelta(days=window2_start + 7),
        opened_count=10,
        settled_count=9,
    )
    assert window1.start < window2.start  # 전제 확인 — window2 가 달력상 더 늦다.
    result = run_holdout(
        dataset,
        [window1, window2],
        _spec(),
        _training_policy(min_training_rows=5),
        _policy(min_evaluation_rows=5),
        _DeterministicTrainer(),
        CodeVersion("sha-1"),
    )
    assert not isinstance(result, HoldoutRejected)
    # window2(달력상 최신)가 제외되고 window1(더 이른 창)만 성공해야 이 test 가
    # 실제로 그 경계를 재는 것이다.
    assert len(result.windows) == 1
    assert result.windows[0].window_start == window1.start.isoformat()
    assert len(result.excluded_windows) == 1
    assert result.excluded_windows[0].window.start == window2.start
    assert (
        result.excluded_windows[0].reason
        == WindowExclusionReason.INSUFFICIENT_EVALUATION_ROWS
    )
    # 승격 판정은 「성숙·평가 가능했던 최신」(window1) 결과에서 나온다 — NotEvaluable
    # 로 떨어지지 않는다(제외된 window2 를 승격 대상에서 조용히 빼는 것도 아니다).
    assert not isinstance(result.promotion, PromotionNotEvaluable)
    assert (
        derive_promotion(
            result.windows[0].outcome, window_start=result.windows[0].window_start
        )
        == result.promotion
    )
    # verifier r3 LOW L-2r 재현 — holdout_overlaps 가 plan_selected(skip 된 창도
    # 그대로 포함) 대신 succeeded_windows(서로소 집합)를 써야 회계(H-2r)와
    # 일관된다. window2 는 skip 됐으므로 겹침 측정 대상은 window1 하나뿐이고,
    # 창이 하나면 쌍이 없어 holdout_overlaps 자체가 빈 tuple 이어야 한다 —
    # plan_selected 를 그대로 썼다면 (window1, window2) 쌍 하나가 (겹치지 않는
    # 시간 구간이라 row_count=0 이더라도) 측정 대상으로 잡혔을 것이다.
    assert result.holdout_overlaps == ()


def test_run_holdout_reproducible_with_real_lightgbm() -> None:
    """설계 검토 (5)-9 — fake + **실 LightGBM 1건**. 창당 학습 행이 5C-1
    `min_training_rows`(training-v1, 500)를 넘어야 하므로 test 용 `TrainingPolicy`를
    직접 구성해 낮춘다(컨벤션 허용, 5C-1 관행)."""
    dataset, window = _selected_window_scenario(min_training_rows=5)
    trainer = LightGbmTrainer()
    small_policy = _policy(stability_seeds=(20260812,))  # seed 1개 — 실행 비용 절감

    first = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=5),
        small_policy,
        trainer,
        CodeVersion("sha-1"),
    )
    second = run_holdout(
        dataset,
        [window],
        _spec(),
        _training_policy(min_training_rows=5),
        small_policy,
        trainer,
        CodeVersion("sha-1"),
    )
    assert not isinstance(first, HoldoutRejected)
    assert not isinstance(second, HoldoutRejected)

    from ml_engine.evaluation.report import canonical_report_bytes

    first_bytes = canonical_report_bytes(first)
    second_bytes = canonical_report_bytes(second)
    assert isinstance(first_bytes, bytes)
    assert first_bytes == second_bytes


def test_no_bare_threshold_seed_or_layer_parameter_on_run_holdout() -> None:
    """설계 검토 (1) 첫 행 — 임계·seed 목록·층 이름을 낱개 인자로 받지 않는다."""
    import inspect

    signature = inspect.signature(run_holdout)
    forbidden = {"threshold", "seeds", "stratum", "max_origins", "paired_t_threshold"}
    assert forbidden.isdisjoint(signature.parameters)
