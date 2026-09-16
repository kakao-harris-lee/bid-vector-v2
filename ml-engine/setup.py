"""`ml-engine/setup.py` — D-5E-8 wheel 빌드 훅 배선. 프로젝트 메타데이터는 전부
`pyproject.toml`([project]·[tool.setuptools])에 있다 — `setuptools`가 `cmdclass`를
`pyproject.toml`에서 선언적으로 읽는 경로를 제공하지 않아(실측, `[tool.setuptools]`에
`cmdclass` 키가 없다) 이 최소 `setup.py`가 `ContractsBuildPy`를 `build_py`에 꽂는 역할만
한다.
"""

from __future__ import annotations

import sys
from pathlib import Path

# PEP 517 빌드 프런트엔드가 이 파일을 문자열로 exec 할 때 `ml-engine/`가 항상
# `sys.path`에 있지는 않다(실측 — `uv sync` 의 editable 빌드가
# `ModuleNotFoundError: No module named 'tools'`를 냈다). `__file__`은 빌드 백엔드가
# 채워 주므로 그 디렉터리를 명시적으로 얹는다.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from setuptools import setup

from tools.build_hook import ContractsBuildPy

setup(cmdclass={"build_py": ContractsBuildPy})
