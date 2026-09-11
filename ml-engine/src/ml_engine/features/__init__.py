"""ml_engine.features — training/serving 공용 feature 변환(versioned schema, 5B). 계약 밖
어떤 결측/미지 값도 조용히 접지 않는다(v2-지침서.md §5) — DB·HTTP·업무 모듈을 모른다.
"""

from __future__ import annotations
