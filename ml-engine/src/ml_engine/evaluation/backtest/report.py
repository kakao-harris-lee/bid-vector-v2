"""`ml_engine.evaluation.backtest.report` — 판정 JSON 직렬화(D-6G-9).

정본 규칙은 5C-2 `evaluation.report.canonical_report_bytes` 와 같다: 키 정렬 ·
구분자 `(",", ":")` · `allow_nan=False`. 같은 스냅숏·같은 정책 version·같은 seed 면
**바이트 동일**한 JSON 이 나와야 한다(위협 모델 ③ 재현).

싣는 것: 정책 version 과 checksum · 스냅숏 id 와 sha256 · 표본 목록 sha256 · P-4 적합도
실측 · 제외 사유별 계수(0건 포함) · 창별 결과와 제외된 창 · 전략별 판정과 필요 표본 수.
**싣지 않는 것**: 공고 식별자(집계만 나간다, D-6G-9).
"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Sequence

from ml_engine.evaluation.backtest.fit import FitResult
from ml_engine.evaluation.backtest.records import (
    BacktestStopped,
    BacktestVerdict,
    SamplingRecord,
    SnapshotRecord,
    VariantRecord,
    WindowRecord,
)
from ml_engine.evaluation.backtest.verdict import (
    StrategyNotEvaluable,
    StrategyVerdict,
    WindowOutcome,
)
from ml_engine.evaluation.backtest.windows import WindowExclusion
from ml_engine.registry.artifact import JsonValue

VERDICT_SCHEMA_VERSION = "strategy-backtest-verdict-v1"


def _snapshot(record: SnapshotRecord) -> dict[str, JsonValue]:
    return {
        "snapshot_id": record.snapshot_id,
        "rows_sha256": record.rows_sha256,
        "sample_list_sha256": record.sample_list_sha256,
        "period_start": record.period_start.isoformat(),
        "period_end": record.period_end.isoformat(),
    }


def _fit(result: FitResult) -> dict[str, JsonValue]:
    return {
        "sample_count": result.sample_count,
        "ks_statistic": result.ks_statistic,
        "p_value": result.p_value,
        "max_bin_deviation": result.max_bin_deviation,
        "accepted": result.accepted,
        "rejection": None if result.rejection is None else str(result.rejection),
    }


def _window_outcome(outcome: WindowOutcome) -> dict[str, JsonValue]:
    return {
        "window_index": outcome.window_index,
        "row_count": outcome.row_count,
        "baseline_win_rate": outcome.baseline_win_rate,
        "strategy_win_rate": outcome.strategy_win_rate,
        "relative_gain": outcome.relative_gain,
        "discordant_strategy_only": outcome.discordant.strategy_only,
        "discordant_baseline_only": outcome.discordant.baseline_only,
        "p_value": outcome.p_value,
        "required_discordant_pairs": outcome.required_discordant_pairs,
        "passed": outcome.passed,
        "underpowered": outcome.underpowered,
    }


def _strategy(verdict: StrategyVerdict) -> dict[str, JsonValue]:
    payload: dict[str, JsonValue] = {
        "strategy": verdict.strategy_name,
        "outcome": type(verdict).__name__,
        "windows": [_window_outcome(window) for window in verdict.windows],
    }
    if isinstance(verdict, StrategyNotEvaluable):
        payload["reason"] = str(verdict.reason)
        payload["required_discordant_pairs"] = verdict.required_discordant_pairs
        payload["pooled"] = (
            None if verdict.pooled is None else _window_outcome(verdict.pooled)
        )
        return payload
    payload["pooled"] = _window_outcome(verdict.pooled)
    payload["ineligibility_delta"] = verdict.ineligibility_delta
    payload["seed_sign_consistent"] = verdict.seed_sign_consistent
    return payload


def _sampling(record: SamplingRecord) -> dict[str, JsonValue]:
    return {
        "sample_size": record.sample_size,
        "list_call_count": record.list_call_count,
        "calls_per_notice": record.calls_per_notice,
        "total_calls": record.total_calls,
        "max_total_calls": record.max_total_calls,
        "min_required_sample": record.min_required_sample,
        "within_budget": record.within_budget,
    }


def _variant(record: VariantRecord) -> dict[str, JsonValue]:
    return {
        "variant": str(record.variant),
        "notice_count": record.notice_count,
        "removed_count": record.removed_count,
        "estimate_available": record.estimate_available,
    }


def _window_record(record: WindowRecord) -> dict[str, JsonValue]:
    return {
        "index": record.index,
        "start": record.start.isoformat(),
        "end": record.end.isoformat(),
        "notice_count": record.notice_count,
        "history_count": record.history_count,
    }


def _excluded_window(exclusion: WindowExclusion) -> dict[str, JsonValue]:
    return {
        "index": exclusion.window.index,
        "start": exclusion.window.start.isoformat(),
        "end": exclusion.window.end.isoformat(),
        "reason": str(exclusion.reason),
        "row_count": exclusion.row_count,
    }


def verdict_payload(verdict: BacktestVerdict) -> dict[str, JsonValue]:
    """판정 JSON 의 값 전부 — 공고 식별자는 들어가지 않는다."""
    return {
        "schema_version": VERDICT_SCHEMA_VERSION,
        "policy_version": verdict.policy_version,
        "policy_checksum": verdict.policy_checksum,
        "variant": _variant(verdict.variant),
        "snapshot": _snapshot(verdict.snapshot),
        "sampling": _sampling(verdict.sampling),
        "distribution_fit": _fit(verdict.fit),
        "exclusions": {str(reason): count for reason, count in verdict.exclusions},
        "undecidable": {str(axis): count for axis, count in verdict.undecidable},
        "fill_rates": {name: value for name, value in verdict.fill_rates},
        "standard_market_price_scope": {
            "a_value_present_count": (
                verdict.standard_market_price_scope.a_value_present_count
            ),
            "applicable_count": verdict.standard_market_price_scope.applicable_count,
            "undecidable_count": (
                verdict.standard_market_price_scope.undecidable_count
            ),
        },
        "base_amount_mismatch_count": verdict.base_amount_mismatch_count,
        "limitations": list(verdict.limitations),
        "selected_windows": [
            _window_record(record) for record in verdict.selected_windows
        ],
        "excluded_windows": [
            _excluded_window(exclusion) for exclusion in verdict.excluded_windows
        ],
        "scored_notice_count": verdict.scored_notice_count,
        "seeds": list(verdict.seeds),
        "primary_hypotheses": list(verdict.primary_names),
        "strategies": [_strategy(item) for item in verdict.verdicts],
    }


def stopped_payload(stopped: BacktestStopped) -> dict[str, JsonValue]:
    """멈춤도 산출물이다 — 「판정이 없다」를 침묵이 아니라 기록으로 남긴다."""
    return {
        "schema_version": VERDICT_SCHEMA_VERSION,
        "stopped": str(stopped.reason),
        "detail": stopped.detail,
        "variant": str(stopped.variant),
        "snapshot": _snapshot(stopped.snapshot),
        "sampling": _sampling(stopped.sampling),
        "distribution_fit": None if stopped.fit is None else _fit(stopped.fit),
        "exclusions": {str(reason): count for reason, count in stopped.exclusions},
    }


def canonical_verdict_bytes(verdict: BacktestVerdict | BacktestStopped) -> bytes:
    """키 정렬·구분자 고정·`allow_nan=False`. 비유한 값이 섞이면 조용히 `NaN` 을 쓰지
    않고 `ValueError` 로 터진다 — 판정 JSON 에 `NaN` 이 들어가면 재현 대조가 깨진다."""
    payload = (
        stopped_payload(verdict)
        if isinstance(verdict, BacktestStopped)
        else verdict_payload(verdict)
    )
    return json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def verdict_checksum(verdict: BacktestVerdict | BacktestStopped) -> str:
    return hashlib.sha256(canonical_verdict_bytes(verdict)).hexdigest()


def canonical_multi_verdict_bytes(
    outcomes: Sequence[BacktestVerdict | BacktestStopped],
) -> bytes:
    """판 셋(주 판정 + 민감도 둘, D-6G-21)을 **한 문서**로 낸다 — 세 판의 부호가
    다르면 그 사실이 같은 자리에 보여야 한다. 판 순서는 호출자가 고정한다."""
    payload: dict[str, JsonValue] = {
        "schema_version": VERDICT_SCHEMA_VERSION,
        "variants": [
            stopped_payload(item)
            if isinstance(item, BacktestStopped)
            else verdict_payload(item)
            for item in outcomes
        ],
    }
    return json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")
