"""M5/5D — hypothesis CI 프로파일(`inference` test 전용, 5B `tests/features/conftest.py`와
같은 방식 — 설계 검토 (5)-9). `tests/conftest.py`는 5A 소유(계약 생성 배선)라 편집하지
않고 이 디렉터리 스코프의 별도 conftest 에 둔다.
"""

from __future__ import annotations

import os

from hypothesis import settings

settings.register_profile("ci", deadline=None, max_examples=50)
settings.load_profile(os.environ.get("HYPOTHESIS_PROFILE", "ci"))
