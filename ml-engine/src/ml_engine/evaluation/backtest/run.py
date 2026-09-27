"""`ml_engine.evaluation.backtest.run` — 실험 한 번을 끝까지 도는 조율자(D-6G-8).

순서: **P-4 제도 분포 적합도 -> 제외(입력 단계) -> 창 계획 -> seed 다섯 -> 판정**.
적합도가 어긋나면 **판정 대신 멈춤 결과**를 낸다 — 「사정률은 난수」라는 전제가 흔들리면
그 뒤 판정은 의미가 없다(scope.md P-4).

전략 간 **표본 동일성을 단언한다**: 제외는 전략을 보기 전에 한 번만 돌고, 모든 전략이
같은 공고 목록을 같은 순서로 채점한다. 어긋나면 조용히 맞추지 않고 멈춘다(우회 ④).

`StrategyLike` 를 받으므로 S2(분포 엔진)도 여기 코드를 바꾸지 않고 들어온다 — 조립
근(`ml_engine.app`)이 주입한다(층 경계).

seed 안정성은 5C-2 `diagnostics.StabilitySummary` 의 개념(부호 일관·판정 일관)을 그대로
쓰되 그 타입을 재사용하지 않는다 — 그 dataclass 는 `paired_t` 필드를 요구하는데 이
실험의 검정 통계량은 t 가 아니라 이항 꼬리 p 다. 이름이 뜻과 어긋난 값을 담느니 두 불린을
여기서 센다(재사용하지 않은 사유).
"""

from __future__ import annotations

from collections.abc import Sequence

from ml_engine.evaluation.backtest.exclusions import (
    AdmissionResult,
    AdmittedNotice,
    admit_rows,
    exclusion_counts,
    fill_rates,
    standard_market_price_scope,
)
from ml_engine.evaluation.backtest.fit import FitResult, check_institutional_fit
from ml_engine.evaluation.backtest.metrics import (
    StrategyScores,
    score_strategy,
    scored_notice_keys,
)
from ml_engine.evaluation.backtest.observations import (
    LoadedSnapshot,
)
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.reasons import ExclusionReason
from ml_engine.evaluation.backtest.records import (
    ESTIMATED_LOCAL_AGENCY_PREFIXES,
    KNOWN_LIMITATIONS,
    BacktestRequest,
    BacktestStopped,
    BacktestVerdict,
    SampleVariant,
    SamplingRecord,
    SnapshotRecord,
    StopReason,
    VariantRecord,
    WindowRecord,
)
from ml_engine.evaluation.backtest.strategies import (
    StrategyLike,
    StrategyOutcome,
    build_strategy_input,
)
from ml_engine.evaluation.backtest.verdict import (
    StrategyVerdict,
    WindowOutcome,
    evaluate_window,
    passes_window,
    strategy_verdict,
)
from ml_engine.evaluation.backtest.windows import (
    WindowAssignment,
    WindowPlan,
    plan_backtest_windows,
    window_overlaps,
)


def _snapshot_record(snapshot: LoadedSnapshot) -> SnapshotRecord:
    return SnapshotRecord(
        snapshot_id=snapshot.snapshot_id,
        rows_sha256=snapshot.rows_sha256,
        sample_list_sha256=snapshot.sample_list_sha256,
        sample_size=snapshot.sample_size,
        sampled_without_detail=snapshot.sampled_without_detail,
        sampled_without_notice=snapshot.sampled_without_notice,
        sample_divisions=snapshot.sample_divisions,
        period_start=snapshot.period_start,
        period_end=snapshot.period_end,
    )


def _window_records(plan: WindowPlan) -> tuple[WindowRecord, ...]:
    return tuple(
        WindowRecord(
            index=assignment.window.index,
            start=assignment.window.start,
            end=assignment.window.end,
            notice_count=len(assignment.notices),
            history_count=len(assignment.history),
        )
        for assignment in plan.selected
    )


def _run_strategy(
    strategy: StrategyLike,
    assignment: WindowAssignment,
    policy: StrategyBacktestPolicy,
    seed: int,
) -> StrategyScores:
    """창 하나에서 전략 하나를 돌린다 — 공고 순서는 창의 순서 그대로다."""
    outcomes: list[StrategyOutcome] = [
        strategy.bid(
            build_strategy_input(notice, assignment.history, seed=seed), policy
        )
        for notice in assignment.notices
    ]
    return score_strategy(strategy.name, assignment.notices, outcomes)


def _pooled_scores(per_window: Sequence[StrategyScores]) -> StrategyScores:
    return StrategyScores(
        name=per_window[0].name,
        scores=tuple(score for window in per_window for score in window.scores),
    )


def _seed_run(
    request: BacktestRequest, plan: WindowPlan, seed: int
) -> dict[str, tuple[StrategyScores, ...]]:
    """seed 하나로 모든 전략 x 모든 창을 돌린다."""
    strategies = (request.baseline, *request.candidates)
    return {
        strategy.name: tuple(
            _run_strategy(strategy, assignment, request.policy, seed)
            for assignment in plan.selected
        )
        for strategy in strategies
    }


def _samples_match(run: dict[str, tuple[StrategyScores, ...]]) -> bool:
    """전략 간 채점 표본이 정확히 같은가 — 같은 공고를 같은 순서로."""
    signatures = {
        tuple(scored_notice_keys(window) for window in per_window)
        for per_window in run.values()
    }
    return len(signatures) == 1


def _is_primary(request: BacktestRequest, name: str) -> bool:
    """주 가설인가 — Bonferroni 보정이 걸리는 쪽이다(D-6G-6·32). 이 술어가 판정
    경로에 닿지 않으면 주/보조 구분이 공시용 장식이 된다(code-review r1 M-2)."""
    return name in request.primary_names


def _seed_sign_consistent(
    request: BacktestRequest,
    plan: WindowPlan,
    name: str,
    runs: Sequence[dict[str, tuple[StrategyScores, ...]]],
) -> bool:
    """seed 다섯에서 합동 판정의 **부호와 통과 여부**가 일관한가(5C-2 개념 승계)."""
    del plan
    primary = _is_primary(request, name)
    signs: set[bool] = set()
    verdicts: set[bool] = set()
    for run in runs:
        pooled = evaluate_window(
            window_index=None,
            baseline=_pooled_scores(run[request.baseline.name]),
            strategy=_pooled_scores(run[name]),
            policy=request.policy,
            primary=primary,
        )
        signs.add(pooled.relative_gain > 0.0)
        verdicts.add(
            passes_window(
                pooled.relative_gain,
                pooled.p_value,
                request.policy,
                primary=primary,
            )
        )
    return len(signs) <= 1 and len(verdicts) <= 1


def _strategy_verdict(
    request: BacktestRequest,
    plan: WindowPlan,
    name: str,
    runs: Sequence[dict[str, tuple[StrategyScores, ...]]],
) -> StrategyVerdict:
    first = runs[0]
    is_primary = _is_primary(request, name)
    baseline_windows = first[request.baseline.name]
    strategy_windows = first[name]
    windows: tuple[WindowOutcome, ...] = tuple(
        evaluate_window(
            window_index=plan.selected[index].window.index,
            baseline=baseline_windows[index],
            strategy=strategy_windows[index],
            policy=request.policy,
            primary=is_primary,
        )
        for index in range(len(plan.selected))
    )
    baseline_pooled = _pooled_scores(baseline_windows)
    strategy_pooled = _pooled_scores(strategy_windows)
    pooled = evaluate_window(
        window_index=None,
        baseline=baseline_pooled,
        strategy=strategy_pooled,
        policy=request.policy,
        primary=is_primary,
    )
    return strategy_verdict(
        strategy_name=name,
        primary=is_primary,
        windows=windows,
        pooled=pooled,
        ineligibility_delta=(
            strategy_pooled.ineligible_rate - baseline_pooled.ineligible_rate
        ),
        seed_sign_consistent=_seed_sign_consistent(request, plan, name, runs),
        policy=request.policy,
    )


def _sampling_record(request: BacktestRequest) -> SamplingRecord:
    """표본 크기 결정식(D-6G-20·32). 호출 수는 **업무별**로 세고(공사가 하나 더 부른다),
    최소 필요 표본은 창 규칙에서 파생한다 — 정책에 숫자로 적지 않는다.

    이 값들은 공시로 끝나지 않는다: 예산 초과도 최소 미달도 **판정 대신 멈춤**이다
    (verifier r1 M-3 — 앞 판의 `min_required_sample` 은 어디서도 멈춤을 만들지 않는
    죽은 값이었다).

    업무 수는 **표본 목록 파일이 말한다**(M-6). `len(BusinessCategory)` 로 세면 이
    레인의 코드 상수가 문턱을 정하게 되고, Kotlin 의 수집 대상 업무 설정과 갈리면
    영영 닿지 않는(또는 너무 낮은) 문턱이 된다 — 두 레인이 다 읽는 파일이 단일
    출처다."""
    budget = request.policy.sampling
    rows = request.snapshot.rows
    size = len(rows)
    detail_calls = sum(
        budget.calls_per_notice_for(str(row.notice.category)) for row in rows
    )
    total = budget.list_call_count + detail_calls
    minimum = budget.minimum_required_sample(
        rows_per_window=request.policy.verdict.min_window_rows,
        window_count=request.policy.verdict.min_window_count,
        category_count=len(request.snapshot.sample_divisions),
    )
    return SamplingRecord(
        row_count=size,
        notice_observed_count=request.snapshot.notice_observed_count,
        list_call_count=budget.list_call_count,
        detail_calls=detail_calls,
        total_calls=total,
        max_total_calls=budget.max_total_calls,
        minimum_required_sample=minimum,
        within_budget=total <= budget.max_total_calls,
        meets_minimum=size >= minimum,
    )


def _sampling_stop(
    request: BacktestRequest,
    counts: tuple[tuple[ExclusionReason, int], ...],
) -> BacktestStopped | None:
    """표본 결정식이 만드는 멈춤 둘(D-6G-32) — 예산 초과와 최소 미달. 둘 다 **판정
    대신 멈춤**이고, 죽은 enum 값으로 두지 않는다(verifier r1 M-3)."""
    sampling = _sampling_record(request)
    if not sampling.within_budget:
        return _stopped(
            request,
            StopReason.SAMPLING_BUDGET_EXCEEDED,
            f"{sampling.total_calls} > {sampling.max_total_calls}",
            None,
            counts,
        )
    if not sampling.meets_minimum:
        return _stopped(
            request,
            StopReason.SAMPLE_SIZE_BELOW_MINIMUM,
            f"{sampling.row_count} < {sampling.minimum_required_sample}",
            None,
            counts,
        )
    return None


def _variant_record(
    request: BacktestRequest, admitted: Sequence[AdmittedNotice]
) -> tuple[tuple[AdmittedNotice, ...], VariantRecord]:
    """판별 표본 걸러내기(D-6G-21). 뺀 수와 「추정이 가능했는가」를 함께 낸다."""
    if request.variant is SampleVariant.EXCLUDE_WIDE_RESERVE_RANGE:
        limit = request.policy.sensitivity.wide_reserve_half_width
        kept = tuple(
            item
            for item in admitted
            if (item.reserve_range_end_rate - item.reserve_range_begin_rate) / 2.0
            <= limit
        )
        available = True
    elif request.variant is SampleVariant.EXCLUDE_ESTIMATED_LOCAL_GOVERNMENT:
        kept = tuple(
            item
            for item in admitted
            if not _is_estimated_local(item.row.notice.demand_agency_code)
        )
        available = bool(ESTIMATED_LOCAL_AGENCY_PREFIXES)
    else:
        kept, available = tuple(admitted), True
    return kept, VariantRecord(
        variant=request.variant,
        notice_count=len(kept),
        removed_count=len(admitted) - len(kept),
        estimate_available=available,
    )


def _is_estimated_local(code: str | None) -> bool:
    return code is not None and any(
        code.startswith(prefix) for prefix in ESTIMATED_LOCAL_AGENCY_PREFIXES
    )


def _stopped(
    request: BacktestRequest,
    reason: StopReason,
    detail: str,
    fit: FitResult | None,
    admitted_excluded: tuple[tuple[ExclusionReason, int], ...],
) -> BacktestStopped:
    return BacktestStopped(
        reason=reason,
        detail=detail,
        variant=request.variant,
        snapshot=_snapshot_record(request.snapshot),
        sampling=_sampling_record(request),
        fit=fit,
        exclusions=admitted_excluded,
    )


def _plan_or_stop(
    request: BacktestRequest,
    admitted: Sequence[AdmittedNotice],
    fit: FitResult,
    counts: tuple[tuple[ExclusionReason, int], ...],
) -> WindowPlan | BacktestStopped:
    plan = plan_backtest_windows(admitted, request.policy)
    if len(plan.selected) < request.policy.verdict.min_window_count:
        return _stopped(
            request,
            StopReason.INSUFFICIENT_WINDOWS,
            f"선택된 창 {len(plan.selected)}개",
            fit,
            counts,
        )
    overlapping = [item for item in window_overlaps(plan) if item.notice_count]
    if overlapping:
        return _stopped(
            request,
            StopReason.WINDOW_OVERLAP,
            f"겹치는 창 쌍 {len(overlapping)}",
            fit,
            counts,
        )
    return plan


def run_strategy_backtest(
    request: BacktestRequest,
) -> BacktestVerdict | BacktestStopped:
    """표본 예산 -> 제외 -> 판 표본 -> P-4 -> 창 -> seed 다섯 -> 판정. 실패는 전부
    `BacktestStopped` 다."""
    admission = admit_rows(request.snapshot.rows, request.policy)
    counts = exclusion_counts(admission.excluded)
    sampling_stop = _sampling_stop(request, counts)
    if sampling_stop is not None:
        return sampling_stop
    if not admission.admitted:
        return _stopped(
            request, StopReason.NO_ADMITTED_NOTICE, "승인 0건", None, counts
        )
    selected, variant = _variant_record(request, admission.admitted)
    if not selected:
        return _stopped(
            request, StopReason.NO_ADMITTED_NOTICE, "판 표본 0건", None, counts
        )
    fit = check_institutional_fit(
        selected, request.policy, seed=request.policy.stability_seeds[0]
    )
    if not fit.accepted:
        return _stopped(
            request,
            StopReason.DISTRIBUTION_FIT_REJECTED,
            str(fit.rejection),
            fit,
            counts,
        )
    plan = _plan_or_stop(request, selected, fit, counts)
    if isinstance(plan, BacktestStopped):
        return plan
    runs = [_seed_run(request, plan, seed) for seed in request.policy.stability_seeds]
    if any(not _samples_match(run) for run in runs):
        return _stopped(
            request,
            StopReason.STRATEGY_SAMPLE_MISMATCH,
            "전략 간 표본 불일치",
            fit,
            counts,
        )
    return _assemble_verdict(request, plan, fit, counts, runs, variant, admission)


def _assemble_verdict(
    request: BacktestRequest,
    plan: WindowPlan,
    fit: FitResult,
    counts: tuple[tuple[ExclusionReason, int], ...],
    runs: Sequence[dict[str, tuple[StrategyScores, ...]]],
    variant: VariantRecord,
    admission: AdmissionResult,
) -> BacktestVerdict:
    return BacktestVerdict(
        policy_version=request.policy.version,
        policy_checksum=request.policy_checksum,
        variant=variant,
        snapshot=_snapshot_record(request.snapshot),
        sampling=_sampling_record(request),
        fit=fit,
        exclusions=counts,
        undecidable=admission.undecidable,
        fill_rates=fill_rates(
            request.snapshot.rows,
            notice_observed_count=request.snapshot.notice_observed_count,
        ),
        unmeasured_sample_count=request.snapshot.sampled_without_detail,
        standard_market_price_scope=standard_market_price_scope(request.snapshot.rows),
        base_amount_mismatch_count=sum(
            1 for item in admission.admitted if not item.base_amount_matches
        ),
        limitations=KNOWN_LIMITATIONS,
        selected_windows=_window_records(plan),
        excluded_windows=plan.excluded,
        scored_notice_count=sum(
            len(assignment.notices) for assignment in plan.selected
        ),
        seeds=request.policy.stability_seeds,
        primary_names=request.primary_names,
        verdicts=tuple(
            _strategy_verdict(request, plan, strategy.name, runs)
            for strategy in request.candidates
        ),
    )
