"""M5/5A 게이트 — `[[tool.mypy.overrides]]` 각 항목에 `# reason:`·`# resolve:` 주석이
있는지 확인한다(D-5A-2 — 사유·해소 계획 없는 strict 예외 금지, 설계 검토 (1) 「strict」)."""

from __future__ import annotations

import re
from pathlib import Path

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_PYPROJECT_PATH = _ML_ENGINE_ROOT / "pyproject.toml"
_SECTION_HEADER_PATTERN = re.compile(r"(?m)^\[")
_REASON_PATTERN = re.compile(r"#\s*reason:")
_RESOLVE_PATTERN = re.compile(r"#\s*resolve:")


def _section_starts(text: str) -> list[int]:
    return [match.start() for match in _SECTION_HEADER_PATTERN.finditer(text)]


def _override_blocks(text: str) -> list[str]:
    """`[[tool.mypy.overrides]]`마다 다음 섹션 헤더(또는 EOF) 전까지를 한 블록으로 자른다."""
    starts = _section_starts(text)
    blocks: list[str] = []
    for index, start in enumerate(starts):
        header_line = text[start : text.find("\n", start)]
        if header_line.strip() != "[[tool.mypy.overrides]]":
            continue
        end = starts[index + 1] if index + 1 < len(starts) else len(text)
        blocks.append(text[start:end])
    return blocks


def has_reason_and_resolve(block: str) -> bool:
    return bool(_REASON_PATTERN.search(block)) and bool(_RESOLVE_PATTERN.search(block))


def test_every_mypy_override_has_reason_and_resolve_comments() -> None:
    text = _PYPROJECT_PATH.read_text(encoding="utf-8")
    blocks = _override_blocks(text)
    assert blocks, "pyproject.toml 에 [[tool.mypy.overrides]] 가 없다"
    missing = [
        block.splitlines()[1] for block in blocks if not has_reason_and_resolve(block)
    ]
    assert not missing, f"reason/resolve 주석이 없는 override: {missing}"


def test_override_without_comments_is_detected_by_checker() -> None:
    """양성 대조 — 주석 없는 override 텍스트는 검사 함수가 실제로 걸러낸다."""
    bad_text = '[[tool.mypy.overrides]]\nmodule = "some.module"\nignore_errors = true\n'
    blocks = _override_blocks(bad_text)
    assert blocks
    assert not has_reason_and_resolve(blocks[0])


def test_override_with_comments_passes_the_checker() -> None:
    good_text = (
        '[[tool.mypy.overrides]]\nmodule = "some.module"\nignore_errors = true\n'
        "# reason: 사유\n# resolve: 해소 계획\n"
    )
    blocks = _override_blocks(good_text)
    assert blocks
    assert has_reason_and_resolve(blocks[0])
