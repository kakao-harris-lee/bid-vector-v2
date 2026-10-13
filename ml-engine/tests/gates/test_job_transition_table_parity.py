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

**vr M-2 — 앞 판은 「제거」에 닫혀 있고 「다른 모양의 추가」에 열려 있었다.** 파서가 정해진
문면 모양만 읽으므로 다른 모양으로 더한 간선이 등식에 들어오지 않았다(실측: proto 주석에
들여쓰기 둘짜리 표 행 · 괄호 안 산문의 화살표 · ADR 의 백틱 없는 산문 · Kotlin 허용표의
`Pair(…)` 형태 넷이 전부 초록). 이 판은 **닫힌 세계**다:

- **proto·ADR** — 통치 구역(`JobState` enum 위 주석 블록 · D-8 절)의 `→` 는 **전부** 파서를
  지나야 한다. 표 행도 아니고 「표에 없는 쌍」 예시도 아닌 자리에 `→` 가 있으면, 또는
  괄호·백틱 밖에 `→` 가 있으면 **RED** 다(조용히 건너뛰지 않는다).
- **Kotlin** — 허용표 블록의 `JOB_STATE_*` 언급 수가 파싱된 쌍 수의 정확히 두 배여야 한다
  (`Pair(a, b)` 처럼 다른 모양으로 더하면 언급이 남아 RED). 술어(`isAllowedTransition`)가
  그 데이터와 어긋나는 변이는 **Kotlin 쪽 5×5 전 쌍 진리표 test** 가 잡고, 여기서는 그
  술어의 본문이 데이터 조회 하나임을 구조로 고정한다.
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
_ARROW = "→"
_COUNTEREXAMPLE_MARKER = "표에 없는 쌍"
# Kotlin 술어의 본문 — 데이터 조회 **하나**여야 한다(절을 더하는 변이는 여기서 붉어지고,
# 데이터와 어긋나는 거동은 Kotlin 쪽 5×5 진리표 test 가 잡는다).
_KOTLIN_PREDICATE_BODY = "(from to target) in allowedJobStateTransitions"

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


def kotlin_allow_block(text: str) -> str:
    head = text.index("val allowedJobStateTransitions")
    body = text[head:]
    opened = body.index("setOf(")
    return body[opened : body.index("\n    )", opened)]


def kotlin_unaccounted_state_mentions(text: str) -> int:
    """허용표 블록에서 **파싱된 쌍으로 설명되지 않는** 상태 언급 수(vr M-2 P1).
    `Pair(JobState.JOB_STATE_CANCELLED, JobState.JOB_STATE_RUNNING)` 처럼 다른 모양으로
    더하면 언급 둘이 남는다."""
    block = kotlin_allow_block(text)
    mentions = len(re.findall(r"JobState\.JOB_STATE_\w+", block))
    pairs = re.findall(
        r"JobState\.JOB_STATE_(\w+)\s+to\s+JobState\.JOB_STATE_(\w+)", block
    )
    return mentions - 2 * len(pairs)


def kotlin_predicate_body(text: str) -> str:
    """`isAllowedTransition` 의 본문(공백 정규화) — 절을 더한 변이(P1b)가 드러난다."""
    head = text.index("internal fun isAllowedTransition(")
    body = text[head:]
    after = body[body.index("): Boolean =") + len("): Boolean =") :]
    return " ".join(after[: after.index("\n\n")].split())


def parse_kotlin_edges(text: str) -> _Edges:
    head = text.index("val allowedJobStateTransitions")
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


def governed_proto_lines(text: str) -> list[str]:
    """`enum JobState {` **바로 위**의 주석 블록 — 전이표가 사는 통치 구역. 이 구역의
    `→` 를 가진 줄은 전부 파서를 지나야 한다."""
    lines = text.splitlines()
    end = next(
        index for index, line in enumerate(lines) if line.startswith("enum JobState {")
    )
    start = end
    while start > 0 and lines[start - 1].startswith("//"):
        start -= 1
    assert start < end, "JobState 주석 블록을 찾지 못했다"
    return lines[start:end]


def _arrows_outside_backticks(text: str) -> int:
    return re.sub(r"`[^`]*`", "", text).count(_ARROW)


def unparsed_proto_arrow_lines(text: str) -> list[str]:
    """통치 구역에서 **파서가 읽지 못한** `→` 줄(vr M-2 P2·P2b)."""
    unparsed: list[str] = []
    for line in governed_proto_lines(text):
        if _ARROW not in line:
            continue
        if _COUNTEREXAMPLE_MARKER in line:
            # 반례 예시 줄 — 화살표는 전부 백틱 안이어야 하고 그 스팬은 파싱된다.
            if _arrows_outside_backticks(line) or not parse_proto_forbidden_pairs(line):
                unparsed.append(line)
            continue
        match = re.match(r"^//   (?=\S)(.*)$", line)
        if match is None:
            unparsed.append(line)  # 표 행의 모양이 아닌 줄에 화살표가 있다
            continue
        head, _separator, rest = match.group(1).partition("(")
        if _ARROW not in head or _ARROW in rest:
            # 괄호 **안**의 화살표는 `_before_parenthesis` 가 버리므로 등식에 못 들어온다.
            unparsed.append(line)
    return unparsed


def unparsed_adr_arrows(text: str) -> int:
    """D-8 절에서 파서가 읽지 못한 `→` 개수(vr M-2 P3). 백틱 밖의 화살표와, 백틱 안이지만
    괄호 뒤로 밀려 `_before_parenthesis` 가 버리는 화살표를 함께 센다."""
    section = _adr_d8_section(text)
    unparsed = _arrows_outside_backticks(section)
    for span in re.findall(r"`([^`]*" + _ARROW + r"[^`]*)`", section):
        _head, separator, rest = span.partition("(")
        if separator:
            unparsed += rest.count(_ARROW)
    return unparsed


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


# ---- 닫힌 세계(vr M-2) — 파서가 모르는 문면은 조용히 비지 않고 붉어진다 ----


def test_every_arrow_in_the_governed_proto_block_is_parsed() -> None:
    assert unparsed_proto_arrow_lines(_PROTO_PATH.read_text(encoding="utf-8")) == []


def test_every_arrow_in_the_adr_d8_section_is_parsed() -> None:
    assert unparsed_adr_arrows(_ADR_PATH.read_text(encoding="utf-8")) == 0


def test_kotlin_allow_table_has_no_unaccounted_state_mentions() -> None:
    assert (
        kotlin_unaccounted_state_mentions(
            _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
        )
        == 0
    )


def test_kotlin_predicate_is_a_single_table_lookup() -> None:
    """술어에 절을 더하는 변이(P1b)를 구조로 막는다 — 본문이 데이터 조회 하나다.
    그 데이터와 거동이 어긋나는지는 Kotlin 쪽 5×5 진리표 test 가 잰다."""
    assert (
        kotlin_predicate_body(_KOTLIN_RULES_PATH.read_text(encoding="utf-8"))
        == _KOTLIN_PREDICATE_BODY
    )


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


# ---- 닫힌 세계 술어의 음성 대조(vr M-2 P1~P3 재현) ----


def test_governed_block_scanner_sees_a_shallow_indent_table_row() -> None:
    """P2 — 들여쓰기 둘짜리 표 행은 앞 판 파서가 건너뛰었다."""
    original = _PROTO_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "enum JobState {", "//  FAILED → RUNNING\nenum JobState {", 1
    )
    assert mutated != original
    assert unparsed_proto_arrow_lines(mutated) == ["//  FAILED → RUNNING"]


def test_governed_block_scanner_sees_an_arrow_inside_parentheses() -> None:
    """P2b — 표 들여쓰기의 산문이 괄호 안에 화살표를 숨겼다(`_before_parenthesis` 가
    버리던 자리)."""
    original = _PROTO_PATH.read_text(encoding="utf-8")
    planted = "//   재시도 시 (FAILED → RUNNING) 도 허용한다"
    mutated = original.replace("enum JobState {", f"{planted}\nenum JobState {{", 1)
    assert mutated != original
    assert unparsed_proto_arrow_lines(mutated) == [planted]


def test_governed_block_scanner_ignores_an_arrow_outside_the_block() -> None:
    """통치 구역 밖(다른 메시지의 주석)의 화살표는 이 등식의 대상이 아니다 — 범위가
    무한히 넓어지지 않는다는 통제군."""
    original = _PROTO_PATH.read_text(encoding="utf-8")
    mutated = original + "\n// 딴 곳의 산문 FAILED → RUNNING\n"
    assert unparsed_proto_arrow_lines(mutated) == []


def test_adr_scanner_sees_a_plain_prose_arrow() -> None:
    """P3 — 백틱 없는 산문의 화살표는 앞 판 파서가 보지 못했다."""
    original = _ADR_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "> 고아는 대기 큐로 재적재(실행된 적이 없다, 상한 적용).",
        "> 고아는 대기 큐로 재적재(실행된 적이 없다, 상한 적용). FAILED → RUNNING 으로 "
        "재시도할 수 있다.",
        1,
    )
    assert mutated != original
    assert unparsed_adr_arrows(mutated) == 1


def test_kotlin_mention_counter_sees_a_pair_shaped_addition() -> None:
    """P1 — `Pair(a, b)` 형태로 더한 간선은 ` to ` 정규식에 걸리지 않았다."""
    original = _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_RUNNING,",
        "        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_RUNNING,\n"
        "        Pair(JobState.JOB_STATE_CANCELLED, JobState.JOB_STATE_RUNNING),",
        1,
    )
    assert mutated != original
    assert parse_kotlin_edges(mutated) == parse_kotlin_edges(original)  # 집합은 그대로
    assert kotlin_unaccounted_state_mentions(mutated) == 2  # 언급이 남는다


def test_kotlin_predicate_scanner_sees_an_added_clause() -> None:
    """P1b — 술어에 절을 더하면 본문이 데이터 조회 하나가 아니게 된다."""
    original = _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "): Boolean = (from to target) in allowedJobStateTransitions",
        "): Boolean = (from to target) in allowedJobStateTransitions ||\n"
        "    from == JobState.JOB_STATE_FAILED",
        1,
    )
    assert mutated != original
    assert kotlin_predicate_body(mutated) != _KOTLIN_PREDICATE_BODY
