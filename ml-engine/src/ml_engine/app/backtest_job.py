"""`ml_engine.app.backtest_job` — 6G 오프라인 job 진입점(D-6G-8).

스냅숏 URI 와 정책 파일 둘을 받아 **판정 JSON 바이트 하나**를 낸다. 같은 입력이면 같은
바이트가 나온다(재현 test 가 그것을 잰다). 실패는 전부 결과 타입이다 — 예외로 새지
않는다.

여기가 전략 다섯을 **한 자리에서 조립하는 유일한 곳**이다: S0(기준선) · S1 · S2 세 후보
(분포 엔진, `backtest_distribution`) · S4. S3(GBM)은 V2 에 학습 산출물이 없어 **N/A
선언**이고, 이 목록에 자리를 만들지 않는다 — 빈 전략을 넣으면 「돌렸는데 졌다」와
「돌릴 수 없었다」가 뒤섞인다(D-6G-3).

**실 데이터 실행은 이 모듈의 몫이 아니다** — 이 함수는 주어진 스냅숏을 읽을 뿐이고,
수집·적재·실행은 운영자 승인 아래 별도 명령으로 돈다.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path

from ml_engine.adapters.snapshot_files import SnapshotUnreadable, read_snapshot_files
from ml_engine.app.backtest_distribution import (
    S2_STRATEGY_NAMES,
    distribution_strategies,
)
from ml_engine.evaluation.backtest.observations import (
    LoadedSnapshot,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
    strategy_backtest_policy_checksum,
)
from ml_engine.evaluation.backtest.records import BacktestRequest, SampleVariant
from ml_engine.evaluation.backtest.report import canonical_multi_verdict_bytes
from ml_engine.evaluation.backtest.run import run_strategy_backtest
from ml_engine.evaluation.backtest.snapshot import (
    load_snapshot,
)
from ml_engine.evaluation.backtest.strategies import (
    InstitutionalMonteCarloStrategy,
    RuleAnchorStrategy,
    StrategyLike,
    UniformBandStrategy,
)
from ml_engine.evaluation.policy import PolicyRejected
from ml_engine.inference.policy import InferencePolicy, load_inference_policy


class JobFailureReason(StrEnum):
    SNAPSHOT_UNREADABLE = "SNAPSHOT_UNREADABLE"
    SNAPSHOT_REJECTED = "SNAPSHOT_REJECTED"
    BACKTEST_POLICY_REJECTED = "BACKTEST_POLICY_REJECTED"
    INFERENCE_POLICY_REJECTED = "INFERENCE_POLICY_REJECTED"
    PRIMARY_HYPOTHESIS_COUNT_MISMATCH = "PRIMARY_HYPOTHESIS_COUNT_MISMATCH"


@dataclass(frozen=True)
class JobFailed:
    reason: JobFailureReason
    detail: str


@dataclass(frozen=True)
class JobCompleted:
    """판정 JSON 바이트와 그 checksum. 멈춤 결과도 여기로 나온다(멈춤은 실패가
    아니라 산출물이다 — 「판정이 없다」를 기록으로 남긴다)."""

    verdict_bytes: bytes
    checksum: str


def build_strategies(
    inference_policy: InferencePolicy,
) -> tuple[StrategyLike, tuple[StrategyLike, ...]]:
    """(기준선 S0, 후보들). 후보 순서는 사전 등록 순서 그대로 고정한다 — 순서가
    판정 JSON 의 배열 순서이고, 재현 대조가 그 순서에 걸린다."""
    candidates: tuple[StrategyLike, ...] = (
        RuleAnchorStrategy(),
        *distribution_strategies(inference_policy),
        InstitutionalMonteCarloStrategy(),
    )
    return UniformBandStrategy(), candidates


def _load_policies(
    backtest_policy_path: Path, inference_policy_path: Path
) -> tuple[StrategyBacktestPolicy, InferencePolicy] | JobFailed:
    # privacy r1 LOW-2 의 같은 갈래(code-review r2) — 정책 로더의 거부 `detail` 에는
    # **파일 경로가 들어 있다**(뿌리 로더가 메시지에 `path` 를 넣는다). 실패 문면이
    # 호스트의 디렉터리 구조를 나르지 않게 사유 어휘만 낸다. 어느 파일인지는 부르는
    # 쪽이 이미 안다(인자로 넘겼다).
    backtest_policy = load_strategy_backtest_policy(backtest_policy_path)
    if isinstance(backtest_policy, PolicyRejected):
        return JobFailed(
            JobFailureReason.BACKTEST_POLICY_REJECTED, str(backtest_policy.reason)
        )
    inference_policy = load_inference_policy(inference_policy_path)
    if not isinstance(inference_policy, InferencePolicy):
        return JobFailed(
            JobFailureReason.INFERENCE_POLICY_REJECTED,
            type(inference_policy).__name__,
        )
    if backtest_policy.verdict.primary_hypothesis_count != len(S2_STRATEGY_NAMES):
        return JobFailed(
            JobFailureReason.PRIMARY_HYPOTHESIS_COUNT_MISMATCH,
            f"{backtest_policy.verdict.primary_hypothesis_count} != "
            f"{len(S2_STRATEGY_NAMES)}",
        )
    return backtest_policy, inference_policy


def _load_snapshot(snapshot_uri: str) -> LoadedSnapshot | JobFailed:
    files = read_snapshot_files(snapshot_uri)
    if isinstance(files, SnapshotUnreadable):
        # privacy r1 LOW-2 — `detail` 에 절대 경로가 실리면 실패 문면이 호스트의
        # 디렉터리 구조를 나른다. 사유 어휘만 낸다(경로는 부르는 쪽이 이미 안다).
        return JobFailed(JobFailureReason.SNAPSHOT_UNREADABLE, str(files.reason))
    snapshot = load_snapshot(
        files.manifest_bytes, files.rows_bytes, files.sample_list_bytes
    )
    if not isinstance(snapshot, LoadedSnapshot):
        # 판독 거부 사유의 `detail` 은 필드 이름만 나른다(식별자·경로 없음) —
        # 그대로 실어도 안전하다.
        return JobFailed(
            JobFailureReason.SNAPSHOT_REJECTED,
            f"{snapshot.reason}: {snapshot.detail}",
        )
    return snapshot


def run_backtest_job(
    *,
    snapshot_uri: str,
    backtest_policy_path: Path,
    inference_policy_path: Path,
) -> JobCompleted | JobFailed:
    """스냅숏과 정책 둘 -> 판정 JSON. 실패는 전부 `JobFailed`(예외 없음)."""
    policies = _load_policies(backtest_policy_path, inference_policy_path)
    if isinstance(policies, JobFailed):
        return policies
    backtest_policy, inference_policy = policies
    snapshot = _load_snapshot(snapshot_uri)
    if isinstance(snapshot, JobFailed):
        return snapshot
    baseline, candidates = build_strategies(inference_policy)
    checksum = strategy_backtest_policy_checksum(backtest_policy)
    # 판 셋을 **같은 전략 인스턴스**로 돈다(D-6G-21) — S2 의 엔진 캐시를 공유해야
    # 같은 공고에 같은 엔진 결과가 쓰이고, 판 사이 차이가 표본 차이에서만 온다.
    outcomes = [
        run_strategy_backtest(
            BacktestRequest(
                snapshot=snapshot,
                baseline=baseline,
                candidates=candidates,
                primary_names=S2_STRATEGY_NAMES,
                policy=backtest_policy,
                policy_checksum=checksum,
                variant=variant,
            )
        )
        for variant in SampleVariant
    ]
    payload = canonical_multi_verdict_bytes(outcomes)
    return JobCompleted(
        verdict_bytes=payload, checksum=hashlib.sha256(payload).hexdigest()
    )
