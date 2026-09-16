"""S-11 — `OPEN-5A-WHEEL-BUILD-HOOK` 종결 증거(D-5E-8). `uv build --wheel`로 만든
wheel 을 **저장소 밖** 임시 디렉터리의 venv 에 설치하고, `sys.path` 조작 없이
`ml_engine.contracts` 재수출 + servicer import 가 성립하는지 확인한다(설계 검토
우회 (21) — 소스 트리 상대 경로가 우연히 잡히지 않게 저장소 밖에서 검증한다).
"""

from __future__ import annotations

import subprocess
import tempfile
import venv
from pathlib import Path

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]


def test_wheel_install_outside_repo_exposes_contracts_and_servicers() -> None:
    with tempfile.TemporaryDirectory(prefix="bidvector-wheel-outside-repo-") as tmp:
        # 저장소 밖(운영체제 임시 디렉터리, `/private/tmp` 등)임을 실측으로 보장한다.
        tmp_path = Path(tmp).resolve()
        assert not str(tmp_path).startswith(str(_ML_ENGINE_ROOT))

        wheel_dir = tmp_path / "wheel"
        wheel_dir.mkdir()
        build = subprocess.run(
            ["uv", "build", "--wheel", "-o", str(wheel_dir)],
            cwd=_ML_ENGINE_ROOT,
            capture_output=True,
            text=True,
            timeout=180,
        )
        assert build.returncode == 0, build.stderr

        wheels = list(wheel_dir.glob("*.whl"))
        assert len(wheels) == 1, wheels

        venv_dir = tmp_path / "venv"
        venv.create(venv_dir, with_pip=True)
        venv_python = venv_dir / "bin" / "python"

        install = subprocess.run(
            [str(venv_python), "-m", "pip", "install", "--no-deps", str(wheels[0])],
            capture_output=True,
            text=True,
            timeout=120,
        )
        assert install.returncode == 0, install.stderr

        deps_install = subprocess.run(
            [
                str(venv_python),
                "-m",
                "pip",
                "install",
                "grpcio==1.83.1",
                "protobuf==7.36.1",
                "pyyaml==6.0.3",
                "numpy==2.5.2",
                "lightgbm==4.7.0",
            ],
            capture_output=True,
            text=True,
            timeout=120,
        )
        assert deps_install.returncode == 0, deps_install.stderr

        probe_script = (
            "import sys\n"
            f"assert not any(p.startswith(r'{_ML_ENGINE_ROOT!s}') for p in sys.path), sys.path\n"
            "import ml_engine.contracts\n"
            "import bidvector.ml.v1.prediction_pb2_grpc\n"
            "import ml_engine.serving.prediction\n"
            "import ml_engine.training.jobs.servicer\n"
            "print('OK')\n"
        )

        probe = subprocess.run(
            [str(venv_python), "-c", probe_script],
            cwd=str(tmp_path),
            capture_output=True,
            text=True,
            timeout=60,
        )
        assert probe.returncode == 0, probe.stdout + probe.stderr
        assert "OK" in probe.stdout
