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

**URI 를 다시 경로로 되읽지 않는다**(cr r1 H-1). `Path.as_uri()` 는 공백·비ASCII 를 퍼센트
인코딩하고 `urlparse(...).path` 는 그것을 **풀지 않는다** — 그래서 앞 판은 공백이나 한글이 든
스냅숏 경로에서 포함 검사가 **없는 경로**를 비교해 조용히 비활성이 됐다. 거부가 그 판에서도
막혀 보인 유일한 이유는 판독기가 같은 결함을 공유해 job 이 먼저 서는 것이었고, 그것은
fail-closed 가 아니라 우연이다. 이제 `_snapshot_dirs` 가 `(URI, 그 URI 가 가리킬 수 있는
디렉터리 전부)` 를 함께 내고 포함 검사는 **그 `Path` 들**로 한다. 퍼센트 인코딩 해제는
`urllib.parse.unquote` 로 한다 — `urllib.request.url2pathname` 은 POSIX 에서 같은 함수이지만
`urlopen`·opener 기계와 `http.client` 를 함께 들여오고, app 층의 import 계약은 서드파티
클라이언트 **다섯의 열거**라 그 모듈을 막지 못한다(cr r2 MR2-1 — 초록인데 비어 있는 게이트).

**디렉터리가 「전부」인 이유**(vr r2 L-r2-1): 판독기는 URI 를 **문자 그대로** 읽는다. 그래서
이름에 유효한 `%XX` 가 문자 그대로 든 디렉터리(`a%41b`)를 URI 로 주면 이 모듈이 디코딩한
경로(`aAb`)와 판독기가 읽는 경로(`a%41b`)가 **갈린다** — 디코딩 쪽만 보면 스냅숏 안 출력이
통과하고 판정이 입력 디렉터리 안에 쓰인다. 판독기 쪽이 고쳐질 때까지 포함 검사는 **두 경로를
다** 본다(어느 쪽이든 안이면 거부 — 보수적인 방향이다).

판독기 쪽 같은 자리는 이 slice 의 범위 밖이라 `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`(6G-2c)
로 등재했다 — 그것이 닫히기 전에는 공백·한글 경로의 **실행 자체가** `SNAPSHOT_UNREADABLE`
로 선다(변환과 거부는 맞고, 읽기가 못 한다).
"""

from __future__ import annotations

import argparse
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote, urlparse

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


def _converted(directory: Path, raw: str) -> tuple[str, tuple[Path, ...]]:
    """절대 URI 로 바꾼 판 — 변환을 공시하고 디렉터리 하나를 낸다.

    이 갈래는 **갈림이 없다**: 우리가 낸 URI 를 판독기가 문자 그대로 읽으면 그 경로가
    `directory` 와 같다(퍼센트 인코딩이 생기는 이름은 우리가 다시 인코딩하므로, 판독기가
    찾지 못하고 설 뿐 다른 디렉터리를 읽지는 않는다)."""
    uri = directory.as_uri()
    print("snapshot-uri", uri, "converted-from", raw)
    return uri, (directory,)


def _snapshot_dirs(raw: str) -> tuple[str, tuple[Path, ...]]:
    """`(URI, 그 URI 가 가리킬 수 있는 디렉터리 전부)`. 절대 경로가 아니면 절대로 바꾸고
    공시한다.

    공시가 있는 이유: 상대 경로는 **부른 자리의 cwd** 로 풀린다 — 어느 디렉터리를 읽었는지
    출력에 남지 않으면 판정이 어느 스냅숏의 것인지 사후에 말할 수 없다. `file:<상대 경로>`
    도 같은 길로 보낸다(vr r1 L-3 — scheme 이 `file` 이라고 공시를 건너뛰면 그 목적이 빈다).

    디렉터리를 **여기서 함께 내는** 이유와 그것이 하나가 아닌 이유는 모듈 docstring 에 있다."""
    parsed = urlparse(raw)
    if parsed.scheme and parsed.scheme != _FILE_SCHEME:
        raise SystemExit(
            f"REFUSED --snapshot-uri 는 file:// 여야 한다 — 받은 scheme: {parsed.scheme}"
        )
    if not parsed.scheme:
        return _converted(Path(raw).resolve(), raw)
    decoded = Path(unquote(parsed.path))
    if not decoded.is_absolute():
        return _converted(decoded.resolve(), raw)
    # 그대로 넘기는 갈래 — 여기서만 두 해석이 갈릴 수 있다(vr r2 L-r2-1).
    literal = Path(parsed.path).resolve()
    resolved = decoded.resolve()
    return raw, (resolved,) if resolved == literal else (resolved, literal)


def _verdict_path(output_dir: Path, snapshot_dirs: tuple[Path, ...]) -> Path:
    """판정을 쓸 자리. 스냅숏 디렉터리 **안**이면 거부한다 — 스냅숏은 불변 입력이고 그
    sha256 이 evidence 의 닻이다(출력이 입력의 해시를 움직이면 닻이 사라진다).

    `snapshot_dirs` 는 `_snapshot_dirs` 가 이미 해석해 건넨 `Path` 들이다 — URI 문자열에서
    다시 유도하면 퍼센트 인코딩된 판에서 비교가 조용히 빗나가고(cr r1 H-1), 해석이 하나뿐이면
    판독기와 갈리는 판에서 거부가 통과한다(vr r2 L-r2-1). **어느 쪽이든 안이면 거부**다.

    문면에 경로를 싣지 않는다(실패 문면이 호스트 디렉터리 구조를 나르지 않는다) — 무엇을
    넘겼는지는 부른 쪽이 안다."""
    resolved = output_dir.resolve()
    if any(resolved.is_relative_to(directory) for directory in snapshot_dirs):
        raise SystemExit(
            "REFUSED --output-dir 가 --snapshot-uri 의 디렉터리 안이다 — "
            "스냅숏은 불변 입력이다"
        )
    return resolved / _VERDICT_NAME


def main(argv: Sequence[str] | None = None) -> None:
    arguments = _arguments(argv)
    snapshot_uri, snapshot_dirs = _snapshot_dirs(arguments.snapshot_uri)
    verdict_path = _verdict_path(arguments.output_dir, snapshot_dirs)
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
