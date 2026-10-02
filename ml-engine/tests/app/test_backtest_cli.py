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

import hashlib
import inspect
import json
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import url2pathname

import pytest

from ml_engine.app import backtest_cli
from ml_engine.app.backtest_job import JobFailureReason

# 파생 정책(판정식 축은 출하값, 표본 크기·계산량만 낮춘다)과 합성 스냅숏은 **재사용한다** —
# 두 벌을 만들면 CLI 판과 job 판이 조용히 갈린다. 허용 완화 축의 등식은 그 모듈이 잠근다.
from tests.app.test_backtest_job import _derived_policy, _snapshot_dir

_POLICY_DIR = Path(__file__).resolve().parents[2] / "policy"
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

    되돌릴 때 `url2pathname` 을 쓴다 — `urlparse(...).path` 는 퍼센트 인코딩을 **풀지
    않는다**. 앞 판의 이 헬퍼가 그 디코딩을 빼먹었고, `tmp_path` 가 ASCII·공백 없음이라
    아무 test 도 그 자리를 밟지 않았다(cr r1 H-1 의 test 사각과 같은 뿌리)."""
    return Path(url2pathname(urlparse(_snapshot_dir(root)).path))


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

    끝까지 도는 것은 요구하지 않는다: 판독기(`adapters/snapshot_files.py`, in_scope 밖)가
    퍼센트 인코딩을 풀지 않아 이 URI 를 못 읽는다(`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`,
    6G-2c). 여기서 재는 것은 변환이 맞다는 것과 **예외로 새지 않는다**는 것이다."""
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
    **둘 다**. `url2pathname` 이 둘을 같은 `Path` 로 되돌리므로 거부가 입력 형태와 무관하다."""
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
