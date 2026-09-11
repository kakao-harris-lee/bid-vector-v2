"""S-4 양성 대조 표본 — `contracts` 재수출을 우회해 `bidvector`를 직접 import."""

from bidvector.ml.v1 import common_pb2  # noqa: F401 — 의도적 위반, 양성 대조용
