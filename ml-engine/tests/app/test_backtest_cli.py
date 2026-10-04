"""RED — M6/6G-2e D-6G2e-6. 백테스트 job 의 **CLI**.

runbook 2-4 는 `uv run python - <<PY` heredoc 으로 job 을 불렀다 — 그 스크립트는 저장소에
없으므로 리뷰도 test 도 닿지 않고, 실수집 당일 터미널에서 다시 적힌다. 여기서 재는 것 다섯:

1. `python -m ml_engine.app.backtest_cli` 가 판정 바이트를 `verdict.json` 으로 쓰고
   sha256·바이트 수를 내고 **0** 으로 끝난다.
2. `JobFailed` 사유 다섯이 전부 출력에 사유 어휘로 나오고 **1** 로 끝난다.
3. 출력 디렉터리가 스냅숏 **안**이면 거부된다 — 스냅숏은 불변 증거 닻이다(그 sha256 이
   evidence 의 기준). 거부 뒤 스냅숏 디렉터리가 바이트까지 그대로다.
4. 맨 경로(절대·상대)는 **절대 `file://` URI 로 변환**되고 그 변환이 출력에 남는다.
5. `file://` 이 아닌 scheme 은 거부된다.

종료 코드는 전부 **하위 프로세스로 실측**한다 — `main()` 을 같은 프로세스에서 부르면
`SystemExit.code` 만 보이고 「프로세스가 1 로 끝난다」는 계약은 재지 않는다. 성공 판은
판정 경로 전체가 도는 한 판뿐이라 거기서 ①④ 를 함께 잰다(판 하나가 약 10초다).

CLI 에는 숫자 리터럴이 없다 — 실패는 `SystemExit(<문면>)` 로 내고 Python 이 문면을
stderr 로 보내며 1 로 끝낸다(문서화된 계약). 그래서 `app/backtest_cli.py` 가 백테스트를
import 하는 app 모듈이 되어 `tests/evaluation/test_evaluation_no_stray_numeric_literals.py`
의 뿌리에 들어와도 허용 목록을 늘릴 필요가 없다.
"""

from __future__ import annotations

import ast
import hashlib
import inspect
import json
import shutil
import subprocess
import sys
import tempfile
import tomllib
from collections.abc import Sequence
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

import pytest

import ml_engine
import ml_engine.app
from ml_engine.app import backtest_cli
from ml_engine.app.backtest_job import JobFailureReason

# 파생 정책(판정식 축은 출하값, 표본 크기·계산량만 낮춘다)과 합성 스냅숏은 **재사용한다** —
# 두 벌을 만들면 CLI 판과 job 판이 조용히 갈린다. 허용 완화 축의 등식은 그 모듈이 잠근다.
from tests.app.test_backtest_job import _derived_policy, _snapshot_dir

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_LINT_IMPORTS_BIN = Path(sys.executable).parent / "lint-imports"
_BAD_APP_HTTP_FIXTURE = Path(__file__).resolve().parent / "fixtures" / "bad_app_http"
_POLICY_DIR = _ML_ENGINE_ROOT / "policy"
_INFERENCE_POLICY = _POLICY_DIR / "inference-v1.yaml"
_MODULE = "ml_engine.app.backtest_cli"


def _cli(*args: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, "-m", _MODULE, *args],
        capture_output=True,
        text=True,
        cwd=cwd,
        check=False,
    )


def _argv(
    *,
    snapshot: str,
    backtest_policy: Path,
    output_dir: Path,
    inference_policy: Path = _INFERENCE_POLICY,
) -> list[str]:
    return [
        "--snapshot-uri",
        snapshot,
        "--backtest-policy",
        str(backtest_policy),
        "--inference-policy",
        str(inference_policy),
        "--output-dir",
        str(output_dir),
    ]


def _snapshot_path(root: Path) -> Path:
    """`root` 아래 합성 스냅숏 디렉터리의 **경로**. 생성기는 URI 를 내므로 거기서 되돌린다
    (두 번째 생성기를 만들지 않는다).

    되돌릴 때 `unquote` 를 쓴다 — `urlparse(...).path` 는 퍼센트 인코딩을 **풀지
    않는다**. 앞 판의 이 헬퍼가 그 디코딩을 빼먹었고, `tmp_path` 가 ASCII·공백 없음이라
    아무 test 도 그 자리를 밟지 않았다(cr r1 H-1 의 test 사각과 같은 뿌리)."""
    return Path(unquote(urlparse(_snapshot_dir(root)).path))


def _spaced_snapshot(root: Path) -> Path:
    """**공백과 한글**이 든 디렉터리 안의 합성 스냅숏. `tmp_path` 는 ASCII·공백 없음이라
    이 판을 따로 만들지 않으면 퍼센트 인코딩 자리를 전부 지나간다."""
    return _snapshot_path(root / "snap dir" / "스냅숏")


def test_module_run_writes_the_verdict_and_exits_zero(tmp_path: Path) -> None:
    """① 성공 판 — `-m` 진입점 · 종료 코드 0 · `verdict.json` · sha256·바이트 수 공시.

    ④ 도 여기서 함께 잰다: 스냅숏을 **맨 절대 경로**로 넘겨 변환이 실제 판독까지 닿는지
    본다(변환이 출력에만 있고 job 에 닿지 않으면 판독이 `UNSUPPORTED_SCHEME` 로 선다)."""
    snapshot = _snapshot_path(tmp_path)
    output_dir = tmp_path / "verdicts"
    result = _cli(
        *_argv(
            snapshot=str(snapshot),
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=output_dir,
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 0, (result.stdout, result.stderr)
    verdict = output_dir / "verdict.json"
    payload = verdict.read_bytes()
    printed = result.stdout.split()
    assert "converted-from" in printed, result.stdout
    assert snapshot.resolve().as_uri() in printed, result.stdout
    assert hashlib.sha256(payload).hexdigest() in printed, result.stdout
    assert str(len(payload)) in printed, result.stdout
    variants = json.loads(payload)["variants"]
    assert variants[0]["variant"]["variant"] == "MAIN", variants[0]


def _unreadable(tmp_path: Path) -> list[str]:
    return _argv(
        snapshot=(tmp_path / "absent").as_uri(),
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=tmp_path / "out",
    )


def _rejected_snapshot(tmp_path: Path) -> list[str]:
    """manifest 의 `rows_sha256` 과 어긋나는 행 바이트 — 판독 거부."""
    snapshot = _snapshot_path(tmp_path)
    rows = snapshot / "rows.jsonl"
    rows.write_bytes(rows.read_bytes() + b"\n")
    return _argv(
        snapshot=snapshot.as_uri(),
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=tmp_path / "out",
    )


def _rejected_backtest_policy(tmp_path: Path) -> list[str]:
    path = _derived_policy(tmp_path / "policy")
    path.write_text(
        path.read_text(encoding="utf-8") + "verdict.alpha_relaxed: 0.5\n",
        encoding="utf-8",
    )
    return _argv(
        snapshot=(tmp_path / "absent").as_uri(),
        backtest_policy=path,
        output_dir=tmp_path / "out",
    )


def _rejected_inference_policy(tmp_path: Path) -> list[str]:
    path = tmp_path / "inference-broken.yaml"
    path.write_text("version: inference-v1\nnot_a_known_key: 1\n", encoding="utf-8")
    return _argv(
        snapshot=(tmp_path / "absent").as_uri(),
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=tmp_path / "out",
        inference_policy=path,
    )


def _hypothesis_count_mismatch(tmp_path: Path) -> list[str]:
    return _argv(
        snapshot=(tmp_path / "absent").as_uri(),
        backtest_policy=_derived_policy(
            tmp_path / "policy", {"verdict.primary_hypothesis_count": "4"}
        ),
        output_dir=tmp_path / "out",
    )


_FAILURE_INPUTS = {
    JobFailureReason.SNAPSHOT_UNREADABLE: _unreadable,
    JobFailureReason.SNAPSHOT_REJECTED: _rejected_snapshot,
    JobFailureReason.BACKTEST_POLICY_REJECTED: _rejected_backtest_policy,
    JobFailureReason.INFERENCE_POLICY_REJECTED: _rejected_inference_policy,
    JobFailureReason.PRIMARY_HYPOTHESIS_COUNT_MISMATCH: _hypothesis_count_mismatch,
}


def test_the_failure_reasons_are_covered_exhaustively() -> None:
    """사유가 늘면 이 표가 RED — 새 사유가 출력·종료 코드 계약 밖에 남지 않는다."""
    assert set(_FAILURE_INPUTS) == set(JobFailureReason)


@pytest.mark.parametrize("reason", sorted(_FAILURE_INPUTS, key=str))
def test_each_failure_reason_is_reported_and_exits_one(
    reason: JobFailureReason, tmp_path: Path
) -> None:
    """② 사유 다섯 — 사유 어휘가 출력에 있고 프로세스가 1 로 끝나며 산출물은 없다."""
    result = _cli(*_FAILURE_INPUTS[reason](tmp_path), cwd=tmp_path)
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert str(reason) in result.stdout + result.stderr, (result.stdout, result.stderr)
    assert not (tmp_path / "out" / "verdict.json").exists()


@pytest.mark.parametrize("inside", ["", "nested"])
def test_output_dir_inside_the_snapshot_is_refused(tmp_path: Path, inside: str) -> None:
    """③ 스냅숏은 불변 증거 닻이다 — 그 안에 판정을 쓰면 입력의 sha256 이 출력에 따라
    움직인다. 디렉터리 자신과 하위 둘을 다 잰다."""
    snapshot = _snapshot_path(tmp_path)
    before = {path.name: path.read_bytes() for path in sorted(snapshot.iterdir())}
    result = _cli(
        *_argv(
            snapshot=snapshot.as_uri(),
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=snapshot / inside if inside else snapshot,
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert {
        path.name: path.read_bytes() for path in sorted(snapshot.iterdir())
    } == before, "거부된 실행이 스냅숏 디렉터리를 건드렸다"


_SENTINEL_VERDICT = '{"schema_version":"앞-판정"}'.encode()
"""앞 판정의 표식 — 출하 판정 바이트와 섞이지 않는 값이라 덮어쓰기가 바이트로 보인다."""


def _existing_verdict(tmp_path: Path) -> list[str]:
    """출력 디렉터리에 **앞 판정이 이미 있는** 판(D-6G2c-21 ④)."""
    output_dir = tmp_path / "out"
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "verdict.json").write_bytes(_SENTINEL_VERDICT)
    return _argv(
        snapshot=_snapshot_path(tmp_path).as_uri(),
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=output_dir,
    )


def _output_inside_snapshot(tmp_path: Path) -> list[str]:
    snapshot = _snapshot_path(tmp_path)
    return _argv(
        snapshot=snapshot.as_uri(),
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=snapshot / "verdicts",
    )


def _unsupported_scheme(tmp_path: Path) -> list[str]:
    return _argv(
        snapshot="s3://bucket/snapshot",
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=tmp_path / "out",
    )


def _output_dir_is_a_file(tmp_path: Path) -> list[str]:
    """출력 자리가 **기존 파일**인 판(cr r1 P-9)."""
    output_dir = tmp_path / "out"
    output_dir.write_bytes(_SENTINEL_VERDICT)
    return _argv(
        snapshot=_snapshot_path(tmp_path).as_uri(),
        backtest_policy=_derived_policy(tmp_path / "policy"),
        output_dir=output_dir,
    )


_REFUSAL_INPUTS = {
    "UNSUPPORTED_SCHEME": _unsupported_scheme,
    "OUTPUT_INSIDE_SNAPSHOT": _output_inside_snapshot,
    "OUTPUT_NOT_A_DIRECTORY": _output_dir_is_a_file,
    "VERDICT_EXISTS": _existing_verdict,
}


def test_the_refusal_reasons_are_covered_exhaustively() -> None:
    """D-6G2c-21 ④ — 거부 어휘가 **닫혀 있다**. 사유가 늘면 이 표가 RED 다.

    job 실패 사유 표(`_FAILURE_INPUTS`)와 같은 모양이다 — 저쪽은 판정 경로가 낸 결과이고
    이쪽은 판정을 시작하기 전에 멈춘 자리다."""
    assert set(_REFUSAL_INPUTS) == {str(reason) for reason in backtest_cli._Refusal}


@pytest.mark.parametrize("reason", sorted(_REFUSAL_INPUTS))
def test_each_refusal_reason_is_reported_and_exits_nonzero(
    reason: str, tmp_path: Path
) -> None:
    """사유 토큰이 출력에 있고 프로세스가 0 이 아닌 코드로 끝나며 산출물은 없다."""
    result = _cli(*_REFUSAL_INPUTS[reason](tmp_path), cwd=tmp_path)
    assert result.returncode != 0, (result.stdout, result.stderr)
    output = result.stdout + result.stderr
    assert f"REFUSED {reason}" in output, output
    assert "verdict" not in result.stdout.split(), (
        "성공 경로가 돌았다 — 거부가 서지 않았다"
    )


def test_an_existing_verdict_is_not_overwritten(tmp_path: Path) -> None:
    """D-6G2c-21 ④ — 앞 판정의 **바이트가 그대로다**.

    앞 판은 조용히 덮어썼다: 같은 디렉터리로 두 번 부르면 앞 판정이 사라지고, 그 sha256 을
    적은 evidence 가 가리키는 바이트가 없어진다. 거부는 **판정을 돌리기 전**이라 10초짜리
    실행을 낭비하지도 않는다."""
    argv = _existing_verdict(tmp_path)
    result = _cli(*argv, cwd=tmp_path)
    assert result.returncode != 0, (result.stdout, result.stderr)
    assert (tmp_path / "out" / "verdict.json").read_bytes() == _SENTINEL_VERDICT, (
        "앞 판정이 덮어써졌다"
    )


def test_a_dangling_verdict_symlink_is_refused_and_writes_nothing(
    tmp_path: Path,
) -> None:
    """D-6G2c-35 F-3 = cr P-2 — **끊어진 링크가 거부를 통과하지 않는다**.

    `exists()` 는 링크를 **따라간다**. 그래서 출력 자리에 끊어진 `verdict.json` 링크를 두면
    사전 검사는 「없다」로 읽고, 쓰기가 링크 대상으로 나간다 — 대상을 스냅숏 안으로 겨누면
    판정이 **불변 입력 안에** exit 0 으로 쓰였다(`VERDICT_EXISTS` 와 `OUTPUT_INSIDE_SNAPSHOT`
    둘 다 우회). `O_EXCL` 은 링크 자체를 존재로 보므로 그 길이 닫힌다."""
    snapshot = _snapshot_path(tmp_path)
    before = {path.name: path.read_bytes() for path in sorted(snapshot.iterdir())}
    output_dir = tmp_path / "out"
    output_dir.mkdir()
    (output_dir / "verdict.json").symlink_to(snapshot / "verdict.json")
    assert not (output_dir / "verdict.json").exists(), (
        "링크가 끊어져 있어야 이 판이 선다"
    )

    result = _cli(
        *_argv(
            snapshot=snapshot.as_uri(),
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=output_dir,
        ),
        cwd=tmp_path,
    )
    assert result.returncode != 0, (result.stdout, result.stderr)
    assert f"REFUSED {backtest_cli._Refusal.VERDICT_EXISTS}" in (
        result.stdout + result.stderr
    ), (result.stdout, result.stderr)
    assert not list(snapshot.glob("**/verdict.json")), "판정이 스냅숏 안에 쓰였다"
    assert {
        path.name: path.read_bytes() for path in sorted(snapshot.iterdir())
    } == before, "거부된 실행이 스냅숏 디렉터리를 건드렸다"


def test_a_second_run_into_the_same_output_dir_is_refused(tmp_path: Path) -> None:
    """D-6G2c-35 F-3 — 같은 `--output-dir` 로 **두 번 기동**하면 뒤가 거부된다.

    앞 test 는 판정 바이트를 **미리 둔** 판이라 「우리가 쓴 판정」과 「누가 둔 파일」을 가르지
    못했다. 여기서는 첫 기동이 실제로 쓰고, 둘째가 그 바이트를 보고 선다 — 첫 판정이
    그대로임을 해시로 확인한다."""
    snapshot = _snapshot_path(tmp_path)
    policy = _derived_policy(tmp_path / "policy")
    output_dir = tmp_path / "verdicts"
    argv = _argv(
        snapshot=snapshot.as_uri(), backtest_policy=policy, output_dir=output_dir
    )

    first = _cli(*argv, cwd=tmp_path)
    assert first.returncode == 0, (first.stdout, first.stderr)
    written = (output_dir / "verdict.json").read_bytes()
    assert hashlib.sha256(written).hexdigest() in first.stdout.split(), first.stdout

    second = _cli(*argv, cwd=tmp_path)
    assert second.returncode != 0, (second.stdout, second.stderr)
    assert f"REFUSED {backtest_cli._Refusal.VERDICT_EXISTS}" in (
        second.stdout + second.stderr
    ), (second.stdout, second.stderr)
    assert (output_dir / "verdict.json").read_bytes() == written, "첫 판정이 덮어써졌다"


def test_there_is_no_overwrite_flag(tmp_path: Path) -> None:
    """덮어쓰기 플래그를 **만들지 않았다**(D-6G2c-21 ④) — 있으면 「한 번만 쓴다」가 인자
    하나로 풀린다. argparse 가 모르는 플래그로 끝난다(2)."""
    for flag in ("--overwrite", "--force"):
        result = _cli(*_existing_verdict(tmp_path), flag, cwd=tmp_path)
        assert result.returncode == 2, (flag, result.stdout, result.stderr)
        assert "unrecognized arguments" in result.stderr, (flag, result.stderr)


def test_a_relative_bare_path_becomes_an_absolute_file_uri(tmp_path: Path) -> None:
    """④ 상대 경로도 절대 URI 로 — 변환 결과가 출력에 남는다(판독은 뒤에서 선다)."""
    result = _cli(
        *_argv(
            snapshot="absent",
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=tmp_path / "out",
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert (tmp_path / "absent").resolve().as_uri() in result.stdout.split(), (
        result.stdout
    )


def test_a_file_uri_is_passed_through_without_a_conversion_notice(
    tmp_path: Path,
) -> None:
    """이미 URI 면 변환하지 않는다 — 변환 공시가 없을 때만 「그대로 넘겼다」가 참이다."""
    result = _cli(*_unreadable(tmp_path), cwd=tmp_path)
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert "converted-from" not in result.stdout.split(), result.stdout


def test_a_non_file_scheme_is_refused(tmp_path: Path) -> None:
    """⑤ `file://` 강제 — 네트워크 scheme 은 CLI 가 먼저 거부한다(판독까지 가지 않는다).
    `file://<host>/…` 의 거부는 판독기(`read_snapshot_files`)가 지는 경계 그대로다 —
    여기서 다시 판정하지 않는다(같은 뜻을 두 자리에서 재지 않는다)."""
    result = _cli(
        *_argv(
            snapshot="s3://bucket/snapshot",
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=tmp_path / "out",
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert "s3" in result.stdout + result.stderr


def test_the_public_surface_is_one_entry_point() -> None:
    """(2b) 값 획득 축 — 이 slice 가 더하는 public 표면은 `main(argv)` 하나다. 임계·seed·
    경로를 낱개 인자로 받는 자리가 생기지 않았다(정책은 파일로만 온다)."""
    defined = {
        name
        for name, value in vars(backtest_cli).items()
        if not name.startswith("_")
        and getattr(value, "__module__", None) == backtest_cli.__name__
    }
    assert defined == {"main"}, defined
    assert list(inspect.signature(backtest_cli.main).parameters) == ["argv"]


def test_a_bare_path_with_a_space_converts_to_a_percent_encoded_uri(
    tmp_path: Path,
) -> None:
    """D-6G2e-16 — 공백·한글 경로도 **올바른** URI 로 바뀐다.

    여기서 재는 것은 변환 자체다(맞는 URI 이고 예외로 새지 않는다). 그 URI 로 **끝까지
    도는가**는 D-6G2c-22 가 판독기를 고친 뒤의 자리이고
    `test_a_spaced_snapshot_runs_end_to_end` 가 잰다."""
    snapshot = _spaced_snapshot(tmp_path).resolve()
    expected = snapshot.as_uri()
    assert "%20" in expected, expected
    result = _cli(
        *_argv(
            snapshot=str(snapshot),
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=tmp_path / "out",
        ),
        cwd=tmp_path,
    )
    assert expected in result.stdout.split(), result.stdout
    assert "Traceback" not in result.stderr, result.stderr


def test_output_inside_a_spaced_snapshot_is_refused_before_any_read(
    tmp_path: Path,
) -> None:
    """cr r1 H-1 ② — 거부 술어가 **입력 형태에 따라 조용히 비활성**이 되지 않는다.

    앞 판은 URI 문자열에서 경로를 되유도해 `/…/snap%20dir/…` 를 비교했고, 그래서 공백이
    든 경로에서는 거부가 서지 않았다. 그 판에서 막혀 보였던 유일한 이유는 판독기가 같은
    결함을 공유해 job 이 먼저 `NOT_FOUND` 로 선 것이다 — fail-closed 가 설계가 아니라
    우연이었다. 그 우연을 쓰지 않는지 보려고 **읽기 사유가 출력에 없음**을 함께 단언한다."""
    snapshot = _spaced_snapshot(tmp_path)
    before = {path.name: path.read_bytes() for path in sorted(snapshot.iterdir())}
    result = _cli(
        *_argv(
            snapshot=str(snapshot),
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=snapshot / "verdicts",
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    output = result.stdout + result.stderr
    assert "REFUSED" in output, output
    assert str(JobFailureReason.SNAPSHOT_UNREADABLE) not in output, (
        "판독이 먼저 섰다 — 거부가 읽기 **앞**이 아니다"
    )
    assert not list(snapshot.glob("**/verdict.json"))
    assert {
        path.name: path.read_bytes() for path in sorted(snapshot.iterdir())
    } == before, "거부된 실행이 스냅숏 디렉터리를 건드렸다"


@pytest.mark.parametrize("encoded", [True, False])
def test_a_uri_input_for_a_spaced_snapshot_also_refuses_output_inside(
    tmp_path: Path, encoded: bool
) -> None:
    """URI 로 받은 판도 같은 거부를 받는다 — 퍼센트 인코딩된 URI 와 공백이 그대로 든 URI
    **둘 다**. `unquote` 가 둘을 같은 `Path` 로 되돌리므로 거부가 입력 형태와 무관하다."""
    snapshot = _spaced_snapshot(tmp_path).resolve()
    uri = snapshot.as_uri() if encoded else f"file://{snapshot}"
    result = _cli(
        *_argv(
            snapshot=uri,
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=snapshot / "verdicts",
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert "REFUSED" in result.stdout + result.stderr, result.stdout
    assert "converted-from" not in result.stdout.split(), result.stdout


def test_a_relative_file_scheme_input_prints_the_conversion_notice(
    tmp_path: Path,
) -> None:
    """vr r1 L-3 — `file:<상대 경로>` 도 절대 URI 로 바꾸고 **공시한다**. 앞 판은 scheme 이
    `file` 이라 통지 없이 통과했고, 그러면 어느 디렉터리를 읽었는지가 출력에서 사라진다 —
    D-6 이 변환을 공시하라고 한 목적이 그것이다."""
    result = _cli(
        *_argv(
            snapshot="file:absent-dir",
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=tmp_path / "out",
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert (tmp_path / "absent-dir").resolve().as_uri() in result.stdout.split(), (
        result.stdout
    )


def test_output_inside_a_percent_named_snapshot_is_refused(tmp_path: Path) -> None:
    """vr r2 L-r2-1 — 이름에 유효한 `%XX` 가 **문자 그대로** 든 디렉터리.

    guard 가 디코딩한 경로만 보면 `a%41b` 를 `aAb` 와 비교해 **통과시킨다**. D-6G2c-22 로
    판독기도 디코딩하게 되어 그 URI 로는 스냅숏을 못 찾지만(그 자리가 비어 있다), guard 는
    **문자 그대로의 경로도** 보수적 여분으로 본다 — 그래서 거부가 판독보다 먼저 선다. 거부가
    먼저여야 「읽기가 실패해서 안 썼다」와 「거부해서 안 썼다」가 섞이지 않는다.

    같은 디렉터리를 맨 경로로 주면 앞 판에서도 거부된다 — 이 판이 성립하는 입력은 **URI** 다."""
    snapshot = _snapshot_path(tmp_path / "a%41b")
    before = {path.name: path.read_bytes() for path in sorted(snapshot.iterdir())}
    result = _cli(
        *_argv(
            snapshot=f"file://{snapshot}",
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=snapshot / "verdicts",
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 1, (result.stdout, result.stderr)
    assert "REFUSED" in result.stdout + result.stderr, result.stdout
    assert "verdict" not in result.stdout.split(), (
        "성공 경로가 돌았다 — 거부가 서지 않았다"
    )
    assert not list(snapshot.glob("**/verdict.json"))
    assert {
        path.name: path.read_bytes() for path in sorted(snapshot.iterdir())
    } == before, "거부된 실행이 스냅숏 디렉터리를 건드렸다"


def test_a_spaced_snapshot_runs_end_to_end(tmp_path: Path) -> None:
    """D-6G2c-22 — 공백·한글이 든 스냅숏 경로를 판독기가 **끝까지** 읽는다.

    `Path.as_uri()` 는 그 이름을 `%XX` 로 인코딩하고 `urlparse(...).path` 는 풀지 않는다 —
    앞 판은 그 문자열을 그대로 경로로 써서 `SNAPSHOT_UNREADABLE` 로 섰다. 변환과 거부는
    맞았고 **읽기만** 못 했다(`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`).

    URI 를 직접 넘긴다 — 맨 경로 판은 변환 자리가 한 번 더 끼어 「판독기가 디코딩하는가」를
    가리지 않는다."""
    snapshot = _spaced_snapshot(tmp_path).resolve()
    uri = snapshot.as_uri()
    assert "%20" in uri, uri
    output_dir = tmp_path / "verdicts"
    result = _cli(
        *_argv(
            snapshot=uri,
            backtest_policy=_derived_policy(tmp_path / "policy"),
            output_dir=output_dir,
        ),
        cwd=tmp_path,
    )
    assert result.returncode == 0, (result.stdout, result.stderr)
    payload = (output_dir / "verdict.json").read_bytes()
    assert hashlib.sha256(payload).hexdigest() in result.stdout.split(), result.stdout
    assert json.loads(payload)["variants"], result.stdout


# ── app 층의 HTTP·소켓 금지: 계약 파일 한 자리 + AST 스윕 (D-6G2c-23 · 35 F-1) ───
# `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` 종결. 두 층이 같은 집합을 본다:
#   ① `lint-imports` — 뿌리 단위(도구가 외부 패키지의 하위 모듈을 forbidden 으로 받지 않는다).
#   ② 이 AST 스윕 — 하위 모듈 단위(`urllib.request` 는 막고 `urllib.parse` 는 허용).
# 금지 목록은 **계약 파일 한 자리**이고 스윕이 그것을 읽어 만든다 — 두 벌을 두면 갈린다.
#
# **스윕이 보는 자리도 그 계약에서 나온다**(verifier r1 F-1 = cr P-4). 뿌리 간선을 지우는
# `ignore_imports` 는 그 모듈의 `urllib` **전부**를 연다 — `urllib.parse` 만 여는 것이 아니다.
# 그래서 예외를 받은 모듈은 app 밖에 있어도 스윕 대상이다. 그 목록을 손으로 적지 않고 계약의
# `ignore_imports` 출발 모듈에서 유도한다: 예외를 늘리면 스윕 범위가 **같은 커밋에서** 함께
# 늘고, 예외와 감시가 갈릴 자리가 없다.
#
# **재는 층은 정적 import 하나다**(F-2 경계, 알려진 제한). 두 층이 보는 것은 `ast.Import` ·
# `ast.ImportFrom` 과 grimp 의 import 그래프, 즉 **import 문**이다. 그래서 경계 밖인 것:
#   ① 동적 import — `importlib.import_module("http.client")` · `__import__("socket")`.
#   ② 목록 밖 네트워크 경로 — `asyncio`(`open_connection`) · `multiprocessing.connection` ·
#      `subprocess`·`os`(curl 호출) · `webbrowser` · `wsgiref`. 네트워크 전용이 아닌 이름이라
#      금지하면 정상 사용까지 막는다 — 열거하지 않고 경계 밖으로 적는다.
# 둘 다 **코드로 막지 않는다**(계약 D-6G2c-35 F-2). 막는 길이 없다는 뜻이 아니라, 이 게이트가
# 재는 층이 아니라는 뜻이다 — 그 층을 재려면 실행 시점 관측(`sys.modules` 감시)이 필요하고
# 그것은 `tests/gates/test_serving_purity.py` 가 serving 에 대해 지는 다른 게이트다.
_APP_IMPORT_CONTRACT_NAME = "app 은 DB·HTTP·업무 모듈을 모른다"

_ALLOWED_SUBMODULES = frozenset({"urllib.parse"})
"""금지 뿌리 **아래에서 유일하게 허용되는** 하위 모듈들.

`urllib.parse` 는 scheme 판정(`urlparse`)과 퍼센트 인코딩 해제(`unquote`)를 낸다.
`urllib.request.url2pathname` 은 POSIX 에서 `unquote` 와 같은 함수이지만 `urlopen`·opener
기계와 `http.client` 를 함께 들여온다 — 이 저장소의 실행 호스트는 Linux 하나이므로 잃는
것은 Windows 드라이브 문면뿐이고 그것은 쓰이지 않는 범위다."""


def _app_import_contract() -> dict[str, Any]:
    """실제 `pyproject.toml` 에서 app 층 forbidden 계약 하나를 읽는다.

    계약이 지워지거나 이름이 바뀌면 이 함수가 먼저 터진다 — 스윕이 **빈 집합으로 조용히
    통과하는** 상태를 막는 자리다(계약 없음이 스윕을 장식으로 만든다)."""
    data = tomllib.loads(
        (_ML_ENGINE_ROOT / "pyproject.toml").read_text(encoding="utf-8")
    )
    matching = [
        contract
        for contract in data["tool"]["importlinter"]["contracts"]
        if contract.get("name") == _APP_IMPORT_CONTRACT_NAME
    ]
    assert len(matching) == 1, (
        f"app 층 forbidden 계약이 pyproject.toml 에 하나가 아니다: {len(matching)}"
    )
    contract = matching[0]
    assert contract["type"] == "forbidden", contract
    assert contract["source_modules"] == ["ml_engine.app"], contract
    return contract


def _forbidden_roots() -> frozenset[str]:
    roots = frozenset(_app_import_contract()["forbidden_modules"])
    assert roots, "금지 목록이 비었다 — 스윕이 아무것도 막지 않는다"
    return roots


def _imported_names(tree: ast.AST) -> list[tuple[str, int]]:
    found: list[tuple[str, int]] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            found.extend((alias.name, node.lineno) for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.level == 0:
            found.append((node.module or "", node.lineno))
    return found


def _ignored_source_modules() -> tuple[str, ...]:
    """app 계약의 `ignore_imports` **출발 모듈** 전수 — 손 목록이 아니라 계약에서 읽는다.

    비어 있으면 유도가 공허해지므로 그 자체를 거부한다(예외가 없으면 F-1 의 구멍도 없지만,
    조용히 빈 집합이 되는 길을 열어 두지 않는다)."""
    contract = _app_import_contract()
    sources = {
        str(entry).split("->", maxsplit=1)[0].strip()
        for entry in contract.get("ignore_imports", [])
    }
    assert sources, "app 계약에 ignore_imports 가 없다 — 스윕 범위 유도가 공허하다"
    for module in sources:
        assert module.startswith("ml_engine."), (
            f"계약 밖 패키지의 예외는 이 스윕이 따라갈 수 없다: {module}"
        )
    return tuple(sorted(sources))


def _module_path(module: str) -> Path:
    root = Path(ml_engine.__file__).resolve().parent
    path = root.joinpath(*module.split(".")[1:]).with_suffix(".py")
    assert path.is_file(), f"예외가 가리키는 모듈 파일이 없다: {module}"
    return path


def _swept_paths() -> tuple[Path, ...]:
    """스윕이 여는 파일 전수 — `app/**` 과 계약의 예외를 받은 모듈들의 합집합."""
    app_dir = Path(ml_engine.app.__file__).resolve().parent
    paths = set(app_dir.rglob("*.py"))
    paths |= {_module_path(module) for module in _ignored_source_modules()}
    return tuple(sorted(paths))


def _offending_imports(paths: Sequence[Path], roots: frozenset[str]) -> list[str]:
    """그 파일들이 금지 뿌리를 import 하는 자리 — 허용 하위 모듈만 뺀다."""
    offenders: list[str] = []
    for path in sorted(paths):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for name, lineno in _imported_names(tree):
            if name.split(".")[0] not in roots:
                continue
            if any(
                name == allowed or name.startswith(f"{allowed}.")
                for allowed in _ALLOWED_SUBMODULES
            ):
                continue
            offenders.append(f"{path.name}:{lineno} -> {name}")
    return offenders


def test_the_swept_modules_import_no_forbidden_http_module() -> None:
    """D-6G2c-23 · F-1 — 스윕 대상 전수에 금지 뿌리의 import 가 없다(허용 하위 모듈 제외).

    앞 판은 둘 다 좁았다: 보는 이름이 `urllib.request` **하나**였고(그래서 `http.client`·
    `socket` 이 지나갔다), 여는 자리가 `app/**` **뿐**이었다(그래서 뿌리 간선 예외를 받은
    app 밖 셋이 지나갔다). 이제 금지 집합도 스윕 범위도 계약 파일에서 온다."""
    offenders = _offending_imports(_swept_paths(), _forbidden_roots())
    assert not offenders, f"스윕 대상이 금지 모듈을 import 한다: {offenders}"


def test_the_sweep_covers_every_module_the_contract_excepts() -> None:
    """verifier r1 F-1 — 예외를 받은 모듈은 **전부** 스윕 대상이다.

    `ignore_imports` 의 `-> urllib` 는 뿌리 간선이라 그 모듈의 `urllib` 를 **전부** 연다 —
    `urllib.parse` 만 여는 것이 아니다. 그래서 예외 넷 가운데 app 밖 셋(`adapters` 둘 ·
    `training.jobs.servicer`)에 `import urllib.request` 를 넣으면 `lint-imports` 는 ignore 로
    지우고, 앞 판의 스윕은 `app/**` 만 열어 그 자리를 보지 못했다 — 두 층 모두 초록이었다.

    범위를 손으로 적지 않고 **계약에서 유도**하므로 예외가 늘면 스윕도 같은 커밋에서 는다.
    여기서 재는 것은 그 유도가 실제로 성립하는가다: 예외 모듈의 파일이 스윕 집합 안이고,
    그중 **app 밖인 것이 실제로 있다**(없으면 이 단언이 공허하다)."""
    swept = set(_swept_paths())
    excepted = {_module_path(module) for module in _ignored_source_modules()}
    assert excepted <= swept, (
        f"예외를 받았는데 스윕 밖인 모듈: {sorted(excepted - swept)}"
    )

    app_dir = Path(ml_engine.app.__file__).resolve().parent
    outside = {path for path in excepted if app_dir not in path.parents}
    assert outside, (
        "app 밖 예외가 하나도 없다 — 이 단언이 공허하다. 예외가 전부 app 안이면 "
        "F-1 의 구멍도 없지만, 그 사실을 여기서 보고 판단해야 한다"
    )
    assert set(app_dir.rglob("*.py")) <= swept, "app 층이 스윕에서 빠졌다"


def test_every_allowed_submodule_has_a_forbidden_root() -> None:
    """허용 예외가 **무엇의 예외인지** 분명하다 — 뿌리가 금지 목록에 없으면 그 예외는
    아무것도 열지 않는 장식이고, 그 상태는 「뿌리를 목록에서 빼도 초록」과 같다.

    이것이 목록이 조용히 줄어드는 것을 막는 자리다: `urllib` 를 계약에서 지우면 여기서 RED."""
    roots = _forbidden_roots()
    orphans = sorted(
        allowed for allowed in _ALLOWED_SUBMODULES if allowed.split(".")[0] not in roots
    )
    assert not orphans, f"금지 뿌리가 없는 허용 예외: {orphans}"


def test_the_sweep_set_equals_the_contract_list(tmp_path: Path) -> None:
    """**등식**: 스윕이 보는 금지 집합 == 계약 파일의 목록(D-6G2c-23).

    열거를 두 벌 두지 않았다는 것을 선언이 아니라 **거동**으로 잰다 — 금지 뿌리마다 한 줄씩
    import 하는 합성 모듈을 지어 스윕에 물리고, 뿌리 **전부**가 지목되는지 본다. 허용 하위
    모듈 한 줄은 지목되지 않아야 한다(예외가 실제로 열려 있음)."""
    roots = _forbidden_roots()
    probe = tmp_path / "probe"
    probe.mkdir()
    lines = [f"import {root}" for root in sorted(roots)]
    lines += [f"import {allowed}" for allowed in sorted(_ALLOWED_SUBMODULES)]
    (probe / "sample.py").write_text("\n".join(lines) + "\n", encoding="utf-8")

    reported = {
        entry.rsplit(" -> ", maxsplit=1)[-1]
        for entry in _offending_imports(tuple(probe.rglob("*.py")), roots)
    }
    assert reported == set(roots), (
        f"스윕이 놓친 뿌리: {sorted(roots - reported)} · "
        f"계약 밖인데 지목된 이름: {sorted(reported - roots)}"
    )


def test_the_import_contract_refuses_a_stdlib_http_import() -> None:
    """양성 대조 — 계약이 **실제로** 막는다. `lint-imports` 를 독립 미니 프로젝트에 걸어
    `ml_engine/app` 의 `urllib.request` 가 BROKEN 임을 실행으로 확인한다.

    계약 블록은 실제 `pyproject.toml` 에서 읽어 fixture 사본에 덧쓴다 — 계약을 지우면
    `_app_import_contract` 가 먼저 터져 이 양성 대조가 그 사실과 무관하게 통과하는 일이
    없다(`tests/gates/test_import_contracts.py` H-3 과 같은 갈래)."""
    contract = _app_import_contract()
    with tempfile.TemporaryDirectory(prefix="bad-app-http-") as tmp:
        root = Path(tmp)
        shutil.copytree(_BAD_APP_HTTP_FIXTURE, root, dirs_exist_ok=True)
        block = [
            "",
            "[[tool.importlinter.contracts]]",
            f"name = {contract['name']!r}",
            'type = "forbidden"',
            f"source_modules = {list(contract['source_modules'])!r}",
            f"forbidden_modules = {sorted(contract['forbidden_modules'])!r}",
            "",
        ]
        with (root / "pyproject.toml").open("a", encoding="utf-8") as handle:
            handle.write("\n".join(block))
        result = subprocess.run(
            [str(_LINT_IMPORTS_BIN), "--config", "pyproject.toml", "--no-cache"],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )
    assert result.returncode != 0, result.stdout + result.stderr
    assert "BROKEN" in result.stdout, result.stdout
    assert "urllib" in result.stdout, result.stdout
