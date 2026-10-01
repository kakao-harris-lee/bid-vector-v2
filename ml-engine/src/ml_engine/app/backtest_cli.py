"""`ml_engine.app.backtest_cli` — 백테스트 job 의 CLI(M6/6G-2e D-6G2e-6).

runbook 2-4 는 이 job 을 **heredoc 스크립트**로 불렀다. 그 스크립트는 저장소에 없어
리뷰도 test 도 닿지 않고, 실수집 당일 터미널에서 다시 적힌다 — 틀리기 쉬운 자리가 셋
있었다: 스냅숏 URI 를 `file://` 로 만드는 일, 판정을 **스냅숏 밖**에 쓰는 일, 실패를
종료 코드로 나르는 일. 셋을 코드로 옮겨 test 가 잠근다.

**판정 경로는 건드리지 않는다** — `run_backtest_job` 을 그대로 부르고 정책은 파일로만
온다(임계를 낱개 인자로 받는 자리가 없다, ML-07 acceptance ④ 와 같은 축). 이 모듈이
더하는 public 표면은 `main(argv)` 하나다.

실패는 `SystemExit(<문면>)` 로 낸다 — Python 이 문면을 stderr 로 보내고 **1** 로 끝낸다
(문서화된 계약). 성공은 그냥 돌아와 **0** 이 된다. 그래서 이 파일에는 숫자 리터럴이 없고,
`evaluation/backtest/**` 를 import 하는 app 모듈을 전수로 훑는 숫자 리터럴 게이트의 뿌리에
들어와도 허용 목록을 늘리지 않는다.

**scheme 판정의 자리**: `file://` 강제는 여기서, `file://<host>/…` 거부와 세 파일의 존재
확인은 판독기(`adapters.snapshot_files.read_snapshot_files`)에 있다 — 같은 뜻을 두 자리에서
재지 않는다. 첫 구간에 콜론이 있는 상대 경로(`a:b/c`)는 scheme 으로 읽혀 거부된다
(fail-closed — 조용히 다른 뜻으로 읽는 것보다 낫다).
"""

from __future__ import annotations

import argparse
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

from ml_engine.app.backtest_job import JobCompleted, run_backtest_job

_FILE_SCHEME = "file"
_VERDICT_NAME = "verdict.json"


@dataclass(frozen=True)
class _Arguments:
    snapshot_uri: str
    backtest_policy: Path
    inference_policy: Path
    output_dir: Path


def _arguments(argv: Sequence[str] | None) -> _Arguments:
    parser = argparse.ArgumentParser(
        prog="python -m ml_engine.app.backtest_cli",
        description=(
            "스냅숏 하나와 정책 둘로 판정 JSON 을 낸다. 정책은 출하 파일 그대로 쓴다 — "
            "임계를 인자로 완화할 수 없다."
        ),
    )
    parser.add_argument(
        "--snapshot-uri",
        required=True,
        help="file:// URI, 또는 맨 경로(절대 file:// URI 로 변환하고 그 변환을 낸다)",
    )
    parser.add_argument(
        "--backtest-policy", required=True, type=Path, help="판정 정책 YAML"
    )
    parser.add_argument(
        "--inference-policy", required=True, type=Path, help="분포 엔진 정책 YAML"
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        type=Path,
        help="판정 JSON 을 쓸 디렉터리 — 스냅숏 디렉터리 안은 거부한다",
    )
    parsed = parser.parse_args(argv)
    return _Arguments(
        snapshot_uri=str(parsed.snapshot_uri),
        backtest_policy=Path(parsed.backtest_policy),
        inference_policy=Path(parsed.inference_policy),
        output_dir=Path(parsed.output_dir),
    )


def _snapshot_uri(raw: str) -> str:
    """맨 경로면 절대 `file://` URI 로 바꾸고 그 변환을 공시한다. 이미 `file://` 면 그대로.

    공시가 있는 이유: 상대 경로는 **부른 자리의 cwd** 로 풀린다 — 어느 디렉터리를 읽었는지
    출력에 남지 않으면 판정이 어느 스냅숏의 것인지 사후에 말할 수 없다."""
    scheme = urlparse(raw).scheme
    if scheme == _FILE_SCHEME:
        return raw
    if scheme:
        raise SystemExit(
            f"REFUSED --snapshot-uri 는 file:// 여야 한다 — 받은 scheme: {scheme}"
        )
    uri = Path(raw).resolve().as_uri()
    print("snapshot-uri", uri, "converted-from", raw)
    return uri


def _verdict_path(output_dir: Path, snapshot_uri: str) -> Path:
    """판정을 쓸 자리. 스냅숏 디렉터리 **안**이면 거부한다 — 스냅숏은 불변 입력이고 그
    sha256 이 evidence 의 닻이다(출력이 입력의 해시를 움직이면 닻이 사라진다).

    문면에 경로를 싣지 않는다(실패 문면이 호스트 디렉터리 구조를 나르지 않는다) — 무엇을
    넘겼는지는 부른 쪽이 안다."""
    snapshot = Path(urlparse(snapshot_uri).path).resolve()
    resolved = output_dir.resolve()
    if resolved.is_relative_to(snapshot):
        raise SystemExit(
            "REFUSED --output-dir 가 --snapshot-uri 의 디렉터리 안이다 — "
            "스냅숏은 불변 입력이다"
        )
    return resolved / _VERDICT_NAME


def main(argv: Sequence[str] | None = None) -> None:
    arguments = _arguments(argv)
    snapshot_uri = _snapshot_uri(arguments.snapshot_uri)
    verdict_path = _verdict_path(arguments.output_dir, snapshot_uri)
    outcome = run_backtest_job(
        snapshot_uri=snapshot_uri,
        backtest_policy_path=arguments.backtest_policy,
        inference_policy_path=arguments.inference_policy,
    )
    if not isinstance(outcome, JobCompleted):
        raise SystemExit(f"FAILED {outcome.reason} {outcome.detail}")
    verdict_path.parent.mkdir(parents=True, exist_ok=True)
    verdict_path.write_bytes(outcome.verdict_bytes)
    print(
        "verdict",
        verdict_path,
        "sha256",
        outcome.checksum,
        "bytes",
        len(outcome.verdict_bytes),
    )


if __name__ == "__main__":
    main()
