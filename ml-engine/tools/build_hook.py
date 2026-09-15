"""`ml-engine/tools/build_hook.py` — D-5E-8 wheel 빌드 훅. setuptools `build_py`
서브클래스가 5A `tools/generate_contracts.generate()`(재사용, 같은 함수를 conftest.py·
CLI·이 훅이 공유한다)로 `bidvector/ml/v1/*_pb2*.py`를 wheel 의 **별도 top-level 패키지**
로 생성한다 — 소스 트리·`ml_engine` 패키지 트리 안에는 두지 않는다(`.contracts-generated/`
와 같은 구조적 경계, `OPEN-5A-WHEEL-BUILD-HOOK` 종결).

`ml_engine.contracts.__init__`는 무편집이다 — 이 훅은 그 모듈이 이미 하는 일(생성
디렉터리를 `sys.path`에 얹고 절대 import)이 **설치본에서도** 성립하도록, `bidvector/`를
`site-packages`(wheel 루트)에 직접 심는다. 설치본에서는 `sys.path` 조작 없이도
`import bidvector.ml.v1.prediction_pb2_grpc`가 성립한다(S-11 실측).

`ml-engine/`(개발 도구) 전용 — 배포되는 `ml_engine` 런타임 패키지는 이 모듈을 import
하지 않는다(`tools/generate_contracts.py`와 같은 경계)."""

from __future__ import annotations

from pathlib import Path

from setuptools.command.build_py import build_py as _build_py

from tools.generate_contracts import generate


class ContractsBuildPy(_build_py):
    """`build_py` 표준 동작 뒤에 생성 stub 을 `build_lib`(wheel 루트가 될 디렉터리)에
    심는다 — `ml_engine` 패키지가 이미 그 자리에 복사된 뒤라 `bidvector/`가 형제
    top-level 패키지로 놓인다."""

    def run(self) -> None:
        super().run()
        build_lib = Path(self.build_lib)
        build_lib.mkdir(parents=True, exist_ok=True)
        generate(build_lib)
