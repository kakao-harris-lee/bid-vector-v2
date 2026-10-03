"""D-6G2c-23 양성 대조 표본 — 의도적으로 금지 import 를 낸다(표준 HTTP 모듈 둘)."""

import http.client  # noqa: F401 — 의도적 위반, 양성 대조용
import urllib.request  # noqa: F401 — 의도적 위반, 양성 대조용
