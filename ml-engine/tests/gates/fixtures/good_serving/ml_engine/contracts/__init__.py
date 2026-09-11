"""fixture 재수출 — 실제 `ml_engine.contracts`처럼 `bidvector`를 재수출한다(간접 체인을 만드는
쪽, grimp 는 실행하지 않으므로 실제 `bidvector` 설치는 필요 없다)."""

import bidvector.ml.v1.common_pb2  # noqa: F401 — 양성 대조용 재수출 흉내
