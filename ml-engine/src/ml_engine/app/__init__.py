"""ml_engine.app — 조립 근(composition root, D-5E-1). `serving`·`training.jobs`·
`inference`·`adapters`를 **여기서만** 잇는다 — layers 계약 밖(어느 층도 혼자서는 이
넷을 다 import 할 수 없다). `sqlalchemy`·`psycopg`·`requests`·`httpx`·`celery`는 여기서도
금지된다(pyproject.toml `app 은 DB·HTTP·업무 모듈을 모른다`, 설계 검토 우회 (11)) —
조립 근이라고 serving 순수성이 새는 자리가 되지 않는다.

`ml_engine.serving`·`ml_engine.training`이 이 패키지를 import 하지 않는다(역방향 의존
없음) — 이 패키지가 유일하게 양쪽을 아는 쪽이다."""

from __future__ import annotations
