"""RED — 전이표 **네 자리 교차 등식**(M6/6B-4 Q-1). 같은 데이터가 네 곳에 있다:

1. Python `ml_engine.training.jobs.state._TRANSITIONS` (실행되는 표)
2. Kotlin `TrainingContractRules.allowedJobStateTransitions` (소비자 계약 test 의 기대값)
3. `contracts/proto/bidvector/ml/v1/training.proto` `JobState` 주석 (wire 계약 문면)
4. `docs/adr/0010-*.md` D-8 + 그 개정 블록 (결정 문서)

**손 대조 금지**(설계 검토 (1)) — 넷을 각자 기계로 뽑아 집합 등식을 단언한다. 한 자리만
고치면 여기가 붉어진다. 등식이 공허해지지 않도록 ① 간선 수 하한 ② 이 slice 가 더한
간선이 **네 자리 모두**에 있음 ③ 각 파서가 바뀐 문면에서 다른 답을 낸다는 음성 대조를
함께 둔다.

주석·문서는 텍스트라 파서가 문면 모양에 기댄다. 그래서 파서가 **못 읽으면 조용히 비는
것이 아니라 붉어진다**(집합 등식 + 하한). 문면 모양을 바꾸려면 이 파서를 함께 고쳐야
한다.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from ml_engine.training.jobs.state import _TRANSITIONS, JobState

_REPO_ROOT = Path(__file__).resolve().parents[3]
_PROTO_PATH = _REPO_ROOT / "contracts/proto/bidvector/ml/v1/training.proto"
_ADR_PATH = _REPO_ROOT / "docs/adr/0010-ml-call-budget-and-training-transport.md"
_KOTLIN_RULES_PATH = (
    _REPO_ROOT
    / "adapters/src/test/kotlin/bidvector/adapters/contract/TrainingContractRules.kt"
)

_STATE_NAMES = frozenset(state.value for state in JobState)
_EXPECTED_EDGE_COUNT = 6
_DISCARD_EDGE = ("ACCEPTED", "FAILED")

type _Edges = frozenset[tuple[str, str]]


def _parse_chain(text: str) -> _Edges:
    """`A → B | C → D` 꼴 한 줄을 간선 집합으로. `|` 는 같은 자리의 여러 상태다."""
    groups: list[list[str]] = []
    for raw_group in text.split("→"):
        names = [
            token.strip().strip("`*").strip()
            for token in raw_group.split("|")
            if token.strip()
        ]
        assert names, f"빈 상태 묶음: {text!r}"
        for name in names:
            assert name in _STATE_NAMES, f"상태 어휘 밖: {name!r} (문면 {text!r})"
        groups.append(names)
    assert len(groups) >= 2, f"간선이 없는 사슬: {text!r}"
    return frozenset(
        (left, right)
        for index in range(len(groups) - 1)
        for left in groups[index]
        for right in groups[index + 1]
    )


def _before_parenthesis(text: str) -> str:
    """괄호 앞까지만 본다 — 전이표 행의 괄호는 **설명**이고, 그 설명이 줄을 넘어가는
    자리가 있다(짝을 맞춰 지우는 방식은 그 줄에서 깨진다)."""
    return text.split("(")[0]


def python_edges() -> _Edges:
    """종단 `CANCEL` 멱등 no-op(출발 = 도착)은 **새 전이가 아니라** 표에 명시된
    데이터다(`state.py` 주석) — 간선 집합에서 뺀다."""
    return frozenset(
        (from_state.value, to_state.value)
        for (from_state, _event), to_state in _TRANSITIONS.items()
        if from_state is not to_state
    )


def parse_kotlin_edges(text: str) -> _Edges:
    head = text.index("private val allowedJobStateTransitions")
    body = text[head:]
    opened = body.index("setOf(")
    block = body[opened : body.index("\n    )", opened)]
    pairs = re.findall(
        r"JobState\.JOB_STATE_(\w+)\s+to\s+JobState\.JOB_STATE_(\w+)", block
    )
    assert pairs, "Kotlin 허용표에서 간선을 하나도 읽지 못했다"
    for left, right in pairs:
        assert left in _STATE_NAMES and right in _STATE_NAMES, (left, right)
    return frozenset(pairs)


def parse_proto_edges(text: str) -> _Edges:
    """`JobState` 주석의 전이표 줄 — `//` + 공백 셋 들여쓰기(표 행)만 본다. 이어지는
    줄(더 깊은 들여쓰기)과 산문 줄(들여쓰기 없음)은 표가 아니다."""
    edges: set[tuple[str, str]] = set()
    for line in text.splitlines():
        match = re.match(r"^//   (?=\S)(.*)$", line)
        if match is None:
            continue
        head = _before_parenthesis(match.group(1))
        if "→" not in head:
            continue
        edges |= _parse_chain(head)
    assert edges, "proto 주석에서 간선을 하나도 읽지 못했다"
    return frozenset(edges)


def parse_proto_forbidden_pairs(text: str) -> _Edges:
    """같은 주석이 **표 밖 예시**를 든다(「표에 없는 쌍(예: …)」) — 그 쌍이 허용 집합에
    들어오지 않았는지 따로 단언한다(문면이 스스로 반례를 들고 있다)."""
    forbidden: set[tuple[str, str]] = set()
    for line in text.splitlines():
        if "표에 없는 쌍" not in line:
            continue
        for span in re.findall(r"`([^`]*→[^`]*)`", line):
            forbidden |= _parse_chain(span)
    assert forbidden, "proto 주석의 「표에 없는 쌍」 예시를 읽지 못했다"
    return frozenset(forbidden)


def _adr_d8_section(text: str) -> str:
    head = text.index("### D-8.")
    body = text[head:]
    end = body.index("\n---")
    section = body[:end]
    # 마크다운 인용·줄바꿈을 지운다 — 백틱 범위가 줄을 넘어가는 자리가 있다.
    return " ".join(line.lstrip("> ").strip() for line in section.splitlines())


def parse_adr_edges(text: str) -> _Edges:
    """D-8 절(+개정 블록)의 백틱 범위 중 화살표를 가진 것만."""
    edges: set[tuple[str, str]] = set()
    for span in re.findall(r"`([^`]*→[^`]*)`", _adr_d8_section(text)):
        edges |= _parse_chain(_before_parenthesis(span))
    assert edges, "ADR D-8 에서 간선을 하나도 읽지 못했다"
    return frozenset(edges)


def _all_four() -> dict[str, _Edges]:
    return {
        "python": python_edges(),
        "kotlin": parse_kotlin_edges(_KOTLIN_RULES_PATH.read_text(encoding="utf-8")),
        "proto": parse_proto_edges(_PROTO_PATH.read_text(encoding="utf-8")),
        "adr": parse_adr_edges(_ADR_PATH.read_text(encoding="utf-8")),
    }


# ---- 등식 ----


def test_four_places_carry_the_same_edge_set() -> None:
    sets = _all_four()
    assert len(set(sets.values())) == 1, {
        name: sorted(edges) for name, edges in sets.items()
    }


def test_edge_set_is_not_vacuous() -> None:
    """파서가 비어 있거나 한 줄만 읽고 등식이 성립하는 일이 없도록 하한을 둔다."""
    for name, edges in _all_four().items():
        assert len(edges) == _EXPECTED_EDGE_COUNT, (name, sorted(edges))


def test_pending_discard_edge_is_present_in_all_four() -> None:
    """M6/6B-4 D-6B4-1 — 이 slice 가 더한 간선이 네 자리 모두에 있다."""
    for name, edges in _all_four().items():
        assert _DISCARD_EDGE in edges, name


def test_direct_accepted_to_succeeded_is_absent_in_all_four() -> None:
    """과잉 추가 방지 — 폐기 간선을 더했을 뿐 다른 직행 간선이 열리지 않았다."""
    for name, edges in _all_four().items():
        assert ("ACCEPTED", "SUCCEEDED") not in edges, name
        assert ("FAILED", "RUNNING") not in edges, name


def test_proto_comment_counterexamples_are_not_allowed_edges() -> None:
    proto_text = _PROTO_PATH.read_text(encoding="utf-8")
    forbidden = parse_proto_forbidden_pairs(proto_text)
    assert forbidden <= {("RUNNING", "ACCEPTED"), ("SUCCEEDED", "RUNNING")}
    assert not (forbidden & parse_proto_edges(proto_text))


# ---- 음성 대조 — 파서가 바뀐 문면에서 다른 답을 낸다 ----


def test_kotlin_parser_sees_a_removed_edge() -> None:
    original = _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_FAILED,\n", ""
    )
    assert mutated != original
    assert _DISCARD_EDGE not in parse_kotlin_edges(mutated)


def test_proto_parser_sees_a_removed_table_row() -> None:
    original = _PROTO_PATH.read_text(encoding="utf-8")
    mutated = "\n".join(
        line
        for line in original.splitlines()
        if not line.startswith("//   ACCEPTED → FAILED")
    )
    assert mutated != original
    assert _DISCARD_EDGE not in parse_proto_edges(mutated)


def test_adr_parser_sees_a_removed_revision_block() -> None:
    original = _ADR_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "`ACCEPTED → FAILED(WORKER_RESOURCE_EXHAUSTED)`", "그 간선"
    )
    assert mutated != original
    assert _DISCARD_EDGE not in parse_adr_edges(mutated)


def test_python_parser_would_see_an_added_edge() -> None:
    """Python 쪽은 데이터를 직접 읽으므로 문면 변이가 아니라 **표 변이**로 대조한다."""
    mutated = dict(_TRANSITIONS)
    from ml_engine.training.jobs.state import JobEvent

    mutated[(JobState.FAILED, JobEvent.START)] = JobState.RUNNING
    edges = frozenset(
        (from_state.value, to_state.value)
        for (from_state, _event), to_state in mutated.items()
        if from_state is not to_state
    )
    assert ("FAILED", "RUNNING") in edges
    assert edges != python_edges()


@pytest.mark.parametrize(
    "text", ["ACCEPTED → ZOMBIE", "ACCEPTED", "ACCEPTED → ", " → RUNNING"]
)
def test_chain_parser_rejects_malformed_text(text: str) -> None:
    """파서가 모르는 문면을 **조용히 건너뛰지 않는다** — 그러면 등식이 공허해진다."""
    with pytest.raises(AssertionError):
        _parse_chain(text)
