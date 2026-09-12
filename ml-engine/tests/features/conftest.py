"""PR #10 리뷰 LOW — hypothesis CI 프로파일(`features` test 전용, 신규 파일).

`tests/conftest.py`는 5A 소유(계약 생성 배선) — 편집 대신 이 디렉터리 스코프의 별도
conftest 에 hypothesis 설정을 둔다. `deadline=None`은 CI 러너의 비결정적 처리 시간
변동(로드에 따라 예제 하나가 느려지는 것)이 `DeadlineExceeded`로 test 를 실패시키는
것을 막는다 — 속도 회귀를 잡는 것은 이 profile 의 역할이 아니다. `HYPOTHESIS_PROFILE`
환경변수로 로컬에서 다른 profile(예: 기본 `default`, 더 많은 example)을 쓸 수 있다.
"""

from __future__ import annotations

import os

from hypothesis import settings

settings.register_profile("ci", deadline=None, max_examples=50)
settings.load_profile(os.environ.get("HYPOTHESIS_PROFILE", "ci"))
