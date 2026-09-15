"""RED — D-2D-6 경계 쌍: `serving-v1.yaml`의 `embedding_text_max_chars`와 Kotlin
`config/quality/contract-policy.properties`의 `embedding.text.max-chars`가 같은 값인지
증명한다(두 파일 — 값 동일성만 test 가 확인, 산식은 없음)."""

from __future__ import annotations

import re
from pathlib import Path

from ml_engine.serving.policy import ServingPolicy, load_serving_policy

_REPO_ROOT = Path(__file__).resolve().parents[3]
_SHIPPED_SERVING_POLICY = _REPO_ROOT / "ml-engine" / "policy" / "serving-v1.yaml"
_CONTRACT_POLICY_PROPERTIES = (
    _REPO_ROOT / "config" / "quality" / "contract-policy.properties"
)


def _kotlin_embedding_text_max_chars() -> int:
    text = _CONTRACT_POLICY_PROPERTIES.read_text(encoding="utf-8")
    match = re.search(r"^embedding\.text\.max-chars=(\d+)$", text, re.MULTILINE)
    assert match is not None, "contract-policy.properties 에 키가 없다"
    return int(match.group(1))


def test_embedding_text_max_chars_matches_kotlin_contract_policy() -> None:
    result = load_serving_policy(_SHIPPED_SERVING_POLICY)
    assert isinstance(result, ServingPolicy)
    assert result.embedding_text_max_chars == _kotlin_embedding_text_max_chars()
