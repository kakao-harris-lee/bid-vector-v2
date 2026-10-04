"""`ml_engine.app.backtest_cli` — 백테스트 job 의 CLI(M6/6G-2e D-6G2e-6).

runbook 2-4 는 이 job 을 **heredoc 스크립트**로 불렀다. 그 스크립트는 저장소에 없어
리뷰도 test 도 닿지 않고, 실수집 당일 터미널에서 다시 적힌다 — 틀리기 쉬운 자리가 넷
있었다: 스냅숏 URI 를 `file://` 로 만드는 일, 판정을 **스냅숏 밖**에 쓰는 일, 앞 판정을
**덮어쓰지 않는** 일(D-6G2c-21 ④), 실패를 종료 코드로 나르는 일. 넷을 코드로 옮겨 test 가
잠근다.

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

**디렉터리가 「전부」인 이유**(vr r2 L-r2-1): 판독기도 이제 같은 함수로 디코딩하므로
(D-6G2c-22, `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE` 종결) 두 해석은 **수렴한다** — 그래서
공백·한글 경로의 스냅숏은 변환부터 판독까지 끝까지 돈다. 그래도 문자 그대로의 경로를 함께
내는 것은 **보수적 여분**이다: 이름에 유효한 `%XX` 가 문자 그대로 든 디렉터리(`a%41b`)가
실제로 있으면 그 자리로도 포함 검사가 선다(어느 쪽이든 안이면 거부). 거부를 하나 더 세우는
방향이라 둘을 하나로 줄이지 않는다.
"""

from __future__ import annotations

import argparse
from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from typing import NoReturn
from urllib.parse import unquote, urlparse

from ml_engine.app.backtest_job import JobCompleted, run_backtest_job

_FILE_SCHEME = "file"
_VERDICT_NAME = "verdict.json"


class _Refusal(StrEnum):
    """CLI 가 **판정을 돌리기 전에** 멈추는 사유 — 닫힌 어휘(D-6G2c-21 ④).

    job 실패 사유(`JobFailureReason`)와 다른 축이다: 저쪽은 판정 경로가 낸 결과이고 이쪽은
    인자가 틀려 **판정을 시작조차 하지 않은** 자리다. 어휘를 닫는 이유는 job 쪽과 같다 —
    닫혀 있어야 사유가 늘 때 출력·종료 코드 계약 밖에 새 사유가 남지 않는다."""

    UNSUPPORTED_SCHEME = "UNSUPPORTED_SCHEME"
    INVALID_SNAPSHOT_URI = "INVALID_SNAPSHOT_URI"
    OUTPUT_INSIDE_SNAPSHOT = "OUTPUT_INSIDE_SNAPSHOT"
    OUTPUT_NOT_A_DIRECTORY = "OUTPUT_NOT_A_DIRECTORY"
    VERDICT_EXISTS = "VERDICT_EXISTS"


def _refuse(reason: _Refusal, detail: str) -> NoReturn:
    """`SystemExit(<문면>)` — Python 이 문면을 stderr 로 보내고 **1** 로 끝낸다.

    문면에 호스트 경로를 싣지 않는다(실패 문면이 디렉터리 구조를 나르지 않는다) — 무엇을
    넘겼는지는 부른 쪽이 안다."""
    raise SystemExit(f"REFUSED {reason} {detail}")


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
        _refuse(
            _Refusal.UNSUPPORTED_SCHEME,
            f"--snapshot-uri 는 file:// 여야 한다 — 받은 scheme: {parsed.scheme}",
        )
    if not parsed.scheme:
        return _converted(Path(raw).resolve(), raw)
    decoded = Path(unquote(parsed.path))
    try:
        if not decoded.is_absolute():
            return _converted(decoded.resolve(), raw)
        # 그대로 넘기는 갈래 — 여기서만 두 해석이 갈릴 수 있다(vr r2 L-r2-1).
        literal = Path(parsed.path).resolve()
        resolved = decoded.resolve()
    except ValueError:
        # `%00` 처럼 푼 뒤 운영체제가 경로로 받지 않는 바이트(F-5). 앞 판은 traceback 으로
        # 끝났다 — 멈추기는 했지만 닫힌 어휘 밖이라 「왜 멈췄는지」가 출력에 이름으로 없었다.
        # 문면에 경로를 싣지 않는다.
        _refuse(
            _Refusal.INVALID_SNAPSHOT_URI,
            "--snapshot-uri 를 푼 경로를 운영체제가 받지 않는다",
        )
    return raw, (resolved,) if resolved == literal else (resolved, literal)


def _verdict_path(output_dir: Path, snapshot_dirs: tuple[Path, ...]) -> Path:
    """판정을 쓸 자리 — 거부 **둘**을 여기서 낸다(둘 다 job 을 돌리기 전이다).

    ① 스냅숏 디렉터리 **안**이면 거부한다 — 스냅숏은 불변 입력이고 그 sha256 이 evidence 의
    닻이다(출력이 입력의 해시를 움직이면 닻이 사라진다). ② 그 자리에 판정이 **이미 있으면**
    거부한다(D-6G2c-21 ④).

    `snapshot_dirs` 는 `_snapshot_dirs` 가 이미 해석해 건넨 `Path` 들이다 — URI 문자열에서
    다시 유도하면 퍼센트 인코딩된 판에서 비교가 조용히 빗나가고(cr r1 H-1), 해석이 하나뿐이면
    판독기와 갈리는 판에서 거부가 통과한다(vr r2 L-r2-1). **어느 쪽이든 안이면 거부**다."""
    resolved = output_dir.resolve()
    if any(resolved.is_relative_to(directory) for directory in snapshot_dirs):
        _refuse(
            _Refusal.OUTPUT_INSIDE_SNAPSHOT,
            "--output-dir 가 --snapshot-uri 의 디렉터리 안이다 — 스냅숏은 불변 입력이다",
        )
    # cr r1 P-9 — 출력 자리가 **기존 파일**이면 세 검사를 다 지나고, job 이 끝난 뒤
    # `mkdir(exist_ok=True)` 가 `FileExistsError` 로 터져 traceback 과 함께 판정 바이트를
    # 잃었다(`exist_ok=True` 는 디렉터리일 때만 삼킨다). 닫힌 어휘가 「판정 앞에 멈추는 사유」를
    # 닫았다고 적으면서 실제 사전 실패 집합에 대해서는 닫혀 있지 않던 자리다 — 사전 거부로
    # 옮기고 어휘에 값 하나를 더한다.
    if resolved.exists() and not resolved.is_dir():
        _refuse(
            _Refusal.OUTPUT_NOT_A_DIRECTORY,
            "--output-dir 가 디렉터리가 아니다 — 판정을 쓸 자리가 없다",
        )
    verdict_path = resolved / _VERDICT_NAME
    # D-6G2c-21 ④ — **이미 있으면 거부**한다. 앞 판은 조용히 덮어썼다: 같은 디렉터리로 두
    # 번 부르면 앞 판정이 사라지고, 그 판정의 sha256 을 적은 evidence 가 가리키는 바이트가
    # 없어진다(판정은 재현 대조의 닻이다). 덮어쓰기 플래그는 **만들지 않는다** — runbook 은
    # 스냅숏별 판정 디렉터리를 쓰므로 그 플래그가 필요한 자리가 없고, 있으면 「한 번만 쓴다」가
    # 인자 하나로 풀린다.
    if verdict_path.exists():
        _refuse(
            _Refusal.VERDICT_EXISTS,
            f"--output-dir 에 {_VERDICT_NAME} 이 이미 있다 — 덮어쓰지 않는다",
        )
    return verdict_path


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
    # **정본 술어는 이 쓰기다**(D-6G2c-35 F-3 = cr P-2). 앞 판은 `exists()` 로 거부하고 job 이
    # 끝난 뒤 `write_bytes`(= `open("wb")`, 잘라내기 + 링크 추종)로 썼다 — 검사와 쓰기 사이가
    # **실행 전체**였고 둘을 두 가지가 지나갔다: 같은 디렉터리로 두 번 기동하면 둘 다 검사를
    # 지나 뒤가 앞을 덮었고, `exists()` 는 링크를 따라가므로 **끊어진** 링크가 거부를 통과해
    # 쓰기가 링크 대상(출력 디렉터리 밖, 스냅숏 안일 수도 있다)으로 나갔다.
    # `"xb"` 는 `O_CREAT|O_EXCL` 이라 둘을 한 술어로 닫는다 — 끊어진 링크에도 `FileExistsError`
    # 이고, 검사와 쓰기 사이에 틈이 없다. 위의 `exists()` 는 **빠른 거부**로만 남는다(10초짜리
    # 실행을 낭비하지 않는다).
    try:
        with verdict_path.open("xb") as handle:
            handle.write(outcome.verdict_bytes)
    except FileExistsError:
        _refuse(
            _Refusal.VERDICT_EXISTS,
            f"--output-dir 에 {_VERDICT_NAME} 이 이미 있다 — 덮어쓰지 않는다",
        )
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
