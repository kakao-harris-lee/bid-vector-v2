"""RED — 전이표 **네 자리 교차 등식**(M6/6B-4 Q-1). 같은 데이터가 네 곳에 있다:

1. Python `ml_engine.training.jobs.state._TRANSITIONS` (실행되는 표 — **권위**)
2. `contracts/proto/bidvector/ml/v1/training.proto` `JobState` 주석 (wire 계약 문면)
3. `docs/adr/0010-*.md` D-8 절 (결정 문서)
4. Kotlin `TrainingContractRules.allowedJobStateTransitions` (소비자 계약 기대값)

**vr M-1 — 앞 판의 「닫힌 세계」는 `→` 글리프 한 글자와 Kotlin 텍스트 regex 에 걸려 있었다.**
화살표를 `->`·`⟶`·`⇒`·`&rarr;` 로 바꾸거나, 통치 구역 밖(enum 값 주석 · 빈 줄로 떨어진 단락 ·
`---` 뒤 재개정 절 · 마크다운 표)에 전이 주장을 적거나, Kotlin 허용표의 한 행을 `//` 로
주석 처리하고 별칭(`JobState as JS`)으로 다른 행을 더하면 **전부 초록**이었다.

이 판의 술어는 셋이다:

① **렌더링 바이트 등식** — proto·ADR 의 표 블록은 `_TRANSITIONS` 에서 렌더링한 문자열과
   **줄 단위로 같아야** 한다. 파서가 문면을 읽는 것이 아니라 문면이 생성물과 같은지 본다 —
   화살표 철자·들여쓰기·표 모양이 달라지면 등식이 깨진다(글리프에 의존하지 않는다).
② **블록 밖 금지** — 두 파일 **전체**에서, 표 블록 밖에 「화살표류 글자 + 상태 이름 둘」이
   있으면 RED. 통치 구역 밖에 적는 길(enum 값 주석·떨어진 단락·`---` 뒤 절·마크다운 표)이
   함께 닫힌다.
③ **Kotlin** — 허용표 블록에서 `//` 주석 줄을 **먼저 지우고** 파싱하고(주석 행을 읽던
   틈), 쌍 수의 두 배가 `JOB_STATE_*` 언급 수와 같아야 하며, 술어 본문은 데이터 조회
   하나로 고정한다. 그리고 **Kotlin 쪽 test 가 ADR 의 렌더링 블록을 직접 읽어** 자기 허용표와
   5x5 진리표를 대조한다(`TrainingContractTest`) — 새 데이터 파일 없이 Python 에 전이적으로
   묶인다. 블록 밖 산술(`) - setOf(…) + setOf(…)`)·`forNumber`·`valueOf` 같은 런타임 조작은
   그 자리에서 드러난다.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from ml_engine.training.jobs.pipeline import DetailCode
from ml_engine.training.jobs.state import (
    _PENDING_FAILURE_REASONS,
    _TRANSITIONS,
    JobState,
)

_REPO_ROOT = Path(__file__).resolve().parents[3]
_PROTO_PATH = _REPO_ROOT / "contracts/proto/bidvector/ml/v1/training.proto"
_ADR_PATH = _REPO_ROOT / "docs/adr/0010-ml-call-budget-and-training-transport.md"
_KOTLIN_RULES_PATH = (
    _REPO_ROOT
    / "adapters/src/test/kotlin/bidvector/adapters/contract/TrainingContractRules.kt"
)
_KOTLIN_TEST_PATH = (
    _REPO_ROOT
    / "adapters/src/test/kotlin/bidvector/adapters/contract/TrainingContractTest.kt"
)

_STATE_NAMES = tuple(state.value for state in JobState)
_EXPECTED_EDGE_COUNT = 6
_DISCARD_EDGE = ("ACCEPTED", "FAILED")

_BEGIN = "transition-table-begin"
_END = "transition-table-end"
_PROTO_ROW = "//   {left} → {right}"
_ADR_ROW = "- `{left} → {right}`"

# 「상태 쌍을 잇는」 글자들 — 글리프 하나가 아니라 **부류**다(vr M-1 PR1·PR2·AD1~AD3).
_CONNECTORS = (
    "→",
    "->",
    "⟶",
    "⇒",
    "⟹",
    "⇨",
    "=>",
    "~>",
    "➝",
    "▶",
    "»",
    "&rarr;",
    "&rArr;",
    "&#8594;",
    "&#x2192;",
)

# Kotlin 술어의 본문 — 데이터 조회 **하나**여야 한다.
_KOTLIN_PREDICATE_BODY = "(from to target) in allowedJobStateTransitions"

type _Edges = frozenset[tuple[str, str]]


def python_edges() -> _Edges:
    """권위 있는 표. 종단 `CANCEL` 멱등 no-op(출발 = 도착)은 **새 전이가 아니라** 표에
    명시된 데이터다(`state.py` 주석) — 간선 집합에서 뺀다."""
    return frozenset(
        (from_state.value, to_state.value)
        for (from_state, _event), to_state in _TRANSITIONS.items()
        if from_state is not to_state
    )


def render_rows(template: str) -> list[str]:
    """`_TRANSITIONS` → 표 블록의 줄들. 정렬은 결정적(출발·도착 알파벳순)."""
    return [
        template.format(left=left, right=right)
        for left, right in sorted(python_edges())
    ]


def block_lines(text: str) -> list[str]:
    """`transition-table-begin`/`-end` **사이**의 줄 전부(마커는 제외)."""
    lines = text.splitlines()
    begins = [index for index, line in enumerate(lines) if _BEGIN in line]
    ends = [index for index, line in enumerate(lines) if _END in line]
    assert len(begins) == 1 and len(ends) == 1, (begins, ends)
    assert begins[0] < ends[0]
    return lines[begins[0] + 1 : ends[0]]


def connector_claims_outside_the_block(text: str) -> list[str]:
    """표 블록 **밖**에서 상태 쌍을 잇는 줄들(② 의 술어)."""
    offending: list[str] = []
    inside = False
    for line in text.splitlines():
        if _BEGIN in line:
            inside = True
            continue
        if _END in line:
            inside = False
            continue
        if inside:
            continue
        if any(connector in line for connector in _CONNECTORS) and (
            sum(1 for state in _STATE_NAMES if state in line) >= 2
        ):
            offending.append(line)
    return offending


# ---- ① 렌더링 바이트 등식 ----


def test_proto_table_block_equals_the_rendered_table() -> None:
    assert block_lines(_PROTO_PATH.read_text(encoding="utf-8")) == render_rows(
        _PROTO_ROW
    )


def test_adr_table_block_equals_the_rendered_table() -> None:
    assert block_lines(_ADR_PATH.read_text(encoding="utf-8")) == render_rows(_ADR_ROW)


def test_rendered_table_is_not_vacuous() -> None:
    """렌더러가 비어 있거나 한 줄만 내고 등식이 성립하는 일이 없도록 하한을 둔다."""
    assert len(python_edges()) == _EXPECTED_EDGE_COUNT
    assert len(render_rows(_PROTO_ROW)) == _EXPECTED_EDGE_COUNT
    assert _DISCARD_EDGE in python_edges()  # 이 slice 가 더한 간선
    assert ("ACCEPTED", "SUCCEEDED") not in python_edges()  # 과잉 추가 금지
    assert ("FAILED", "RUNNING") not in python_edges()


# ---- ② 블록 밖 금지 ----


@pytest.mark.parametrize("path", [_PROTO_PATH, _ADR_PATH])
def test_no_transition_claim_outside_the_table_block(path: Path) -> None:
    assert connector_claims_outside_the_block(path.read_text(encoding="utf-8")) == []


# ---- ③ Kotlin ----


def kotlin_allow_block(text: str) -> str:
    """허용표 `setOf(...)` 블록. **`//` 주석 줄을 먼저 지운다** — 앞 판의 정규식은 주석
    처리된 행도 읽어서, 한 행을 `//` 로 막고 별칭으로 다른 행을 더하는 변이(vr M-1 K1)가
    집합을 그대로 보이게 만들었다."""
    head = text.index("val allowedJobStateTransitions")
    body = text[head:]
    opened = body.index("setOf(")
    block = body[opened : body.index("\n    )", opened)]
    return "\n".join(
        line for line in block.splitlines() if not line.strip().startswith("//")
    )


def parse_kotlin_edges(text: str) -> _Edges:
    pairs = re.findall(
        r"JobState\.JOB_STATE_(\w+)\s+to\s+JobState\.JOB_STATE_(\w+)",
        kotlin_allow_block(text),
    )
    assert pairs, "Kotlin 허용표에서 간선을 하나도 읽지 못했다"
    for left, right in pairs:
        assert left in _STATE_NAMES and right in _STATE_NAMES, (left, right)
    return frozenset(pairs)


def kotlin_unaccounted_state_mentions(text: str) -> int:
    """파싱된 쌍으로 설명되지 않는 상태 언급 수 — `Pair(a, b)` 처럼 다른 모양으로 더하면
    언급 둘이 남는다(vr M-2 P1)."""
    block = kotlin_allow_block(text)
    mentions = len(re.findall(r"JobState\.JOB_STATE_\w+", block))
    pairs = re.findall(
        r"JobState\.JOB_STATE_(\w+)\s+to\s+JobState\.JOB_STATE_(\w+)", block
    )
    return mentions - 2 * len(pairs)


def kotlin_predicate_body(text: str) -> str:
    """`isAllowedTransition` 의 본문(공백 정규화) — 절을 더한 변이가 드러난다."""
    head = text.index("internal fun isAllowedTransition(")
    body = text[head:]
    after = body[body.index("): Boolean =") + len("): Boolean =") :]
    return " ".join(after[: after.index("\n\n")].split())


def test_kotlin_allow_table_equals_the_rendered_table() -> None:
    assert (
        parse_kotlin_edges(_KOTLIN_RULES_PATH.read_text(encoding="utf-8"))
        == python_edges()
    )


def test_kotlin_allow_table_has_no_unaccounted_state_mentions() -> None:
    assert (
        kotlin_unaccounted_state_mentions(
            _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
        )
        == 0
    )


def test_kotlin_predicate_is_a_single_table_lookup() -> None:
    """술어에 절을 더하는 변이(vr M-2 P1b)를 구조로 막는다 — 본문이 데이터 조회 하나다."""
    assert (
        kotlin_predicate_body(_KOTLIN_RULES_PATH.read_text(encoding="utf-8"))
        == _KOTLIN_PREDICATE_BODY
    )


def test_kotlin_test_derives_its_expectation_from_the_adr_block() -> None:
    """Kotlin 쪽 기대값의 **출처**가 ADR 의 렌더링 블록임을 이 자리에서도 센다 — Kotlin
    test 가 자기 데이터와 자기 술어만 대조하면(앞 판) 데이터를 바꾸는 변이가 Gradle 축까지
    초록으로 지나간다(vr M-1 K1·K2 실측). 그 test 가 ADR 경로와 마커를 읽는지 확인한다."""
    kotlin = _KOTLIN_TEST_PATH.read_text(
        encoding="utf-8"
    ) + _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
    assert "0010-ml-call-budget-and-training-transport.md" in kotlin
    assert _BEGIN in kotlin
    assert _END in kotlin
    # 기대값을 **자기 데이터**로 쓰지 않는다 — 진리표 test 가 ADR 파생 집합을 쓴다.
    assert "renderedTransitionTableFromAdr" in _KOTLIN_TEST_PATH.read_text(
        encoding="utf-8"
    )


# ---- cr M-3 — 「쓰이는 자리는 정확히 셋」을 문면과 잇는다 ----


def detail_codes_named_in(text: str) -> frozenset[str]:
    """`DetailCode` 닫힌 어휘의 값 중 이 문면이 **이름으로 든** 것들."""
    return frozenset(member.value for member in DetailCode if member.value in text)


def _proto_job_state_comment(text: str) -> str:
    """`enum JobState {` 바로 위의 주석 블록."""
    lines = text.splitlines()
    end = next(
        index for index, line in enumerate(lines) if line.startswith("enum JobState {")
    )
    start = end
    while start > 0 and lines[start - 1].startswith("//"):
        start -= 1
    assert start < end, "JobState 주석 블록을 찾지 못했다"
    return "\n".join(lines[start:end])


def _adr_d8_section(text: str) -> str:
    head = text.index("### D-8.")
    body = text[head:]
    return body[: body.index("\n---")]


def test_contract_text_names_exactly_the_listed_pending_reasons() -> None:
    """`state._PENDING_FAILURE_REASONS` 는 `transition()` 이 강제하는 **런타임 집합**이고,
    계약 문면 두 자리는 같은 셋을 이름으로 든다. 넷째를 코드에 더하면(집합을 고쳐야 한다)
    이 등식이 붉어지고, 문면에만 더해도 붉어진다 — 양방향이다."""
    listed = frozenset(detail for _code, detail in _PENDING_FAILURE_REASONS)
    assert len(listed) == 3
    proto_comment = _proto_job_state_comment(_PROTO_PATH.read_text(encoding="utf-8"))
    adr_section = _adr_d8_section(_ADR_PATH.read_text(encoding="utf-8"))
    assert detail_codes_named_in(proto_comment) == listed
    assert detail_codes_named_in(adr_section) == listed


def test_pending_reason_scanner_sees_a_fourth_reason_in_the_text() -> None:
    """음성 대조 — 문면에 넷째 사유를 더하면 등식이 깨진다."""
    original = _proto_job_state_comment(_PROTO_PATH.read_text(encoding="utf-8"))
    mutated = f"{original}\n//   [4] 산출물 디렉터리 충돌 — `OUTPUT_DIR_NOT_EMPTY`"
    listed = frozenset(detail for _code, detail in _PENDING_FAILURE_REASONS)
    assert detail_codes_named_in(original) == listed
    assert detail_codes_named_in(mutated) != listed


# ---- 음성 대조 — 술어가 바뀐 문면에서 **다른 답**을 낸다 ----


@pytest.mark.parametrize(
    "replacement",
    [
        "//   FAILED -> RUNNING",  # PR1 — ASCII 화살표
        "//   FAILED ⟶ RUNNING",  # PR2 — 다른 글리프
        "//   ACCEPTED → RUNNING   (재시도)",  # 설명을 덧붙인 줄
        "//  ACCEPTED → RUNNING",  # 들여쓰기 하나 차이
    ],
)
def test_rendered_equality_sees_any_edit_inside_the_proto_block(
    replacement: str,
) -> None:
    original = _PROTO_PATH.read_text(encoding="utf-8")
    mutated = original.replace("//   ACCEPTED → RUNNING", replacement, 1)
    assert mutated != original
    assert block_lines(mutated) != render_rows(_PROTO_ROW)


@pytest.mark.parametrize(
    "replacement",
    [
        "- `FAILED -> RUNNING`",  # AD1
        "- `FAILED &rarr; RUNNING`",  # AD2 — 렌더링하면 화살표가 된다
        "- `FAILED ⇒ RUNNING`",  # AD3
        "| FAILED | RUNNING |",  # AD6 — 마크다운 표
    ],
)
def test_rendered_equality_sees_any_edit_inside_the_adr_block(replacement: str) -> None:
    original = _ADR_PATH.read_text(encoding="utf-8")
    mutated = original.replace("- `ACCEPTED → RUNNING`", replacement, 1)
    assert mutated != original
    assert block_lines(mutated) != render_rows(_ADR_ROW)


@pytest.mark.parametrize(
    ("path", "planted"),
    [
        # PR3 — enum 값 주석(통치 구역 밖)
        (_PROTO_PATH, "  JOB_STATE_FAILED = 4;  // FAILED → RUNNING 재시도 허용"),
        # PR4 — 빈 줄로 떨어진 단락
        (_PROTO_PATH, "// 전이표 보충: FAILED -> RUNNING"),
        # AD4 — `---` 뒤 재개정 절
        (_ADR_PATH, "### D-8 재개정: `FAILED → RUNNING` 을 더한다"),
        # AD6 — 블록 밖 마크다운 표
        (_ADR_PATH, "| FAILED | &rarr; | RUNNING |"),
    ],
)
def test_outside_the_block_scanner_sees_a_planted_claim(
    path: Path, planted: str
) -> None:
    original = path.read_text(encoding="utf-8")
    assert connector_claims_outside_the_block(original) == []
    assert connector_claims_outside_the_block(f"{original}\n{planted}\n") == [planted]


def test_kotlin_parser_ignores_a_commented_out_row() -> None:
    """vr M-1 K1 — 앞 판 정규식은 `//` 로 막은 행도 읽어서, 한 행을 주석 처리하고 별칭으로
    다른 행을 더한 변이가 집합을 그대로 보이게 했다. 주석 줄을 먼저 지우므로 이제 집합이
    줄어들고 렌더링 등식이 깨진다."""
    original = _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_RUNNING,",
        "        // JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_RUNNING,",
        1,
    )
    assert mutated != original
    assert ("ACCEPTED", "RUNNING") not in parse_kotlin_edges(mutated)
    assert parse_kotlin_edges(mutated) != python_edges()


def test_kotlin_mention_counter_sees_a_pair_shaped_addition() -> None:
    """vr M-2 P1 — `Pair(a, b)` 형태로 더한 간선은 ` to ` 정규식에 걸리지 않는다."""
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
    """vr M-2 P1b — 술어에 절을 더하면 본문이 데이터 조회 하나가 아니게 된다."""
    original = _KOTLIN_RULES_PATH.read_text(encoding="utf-8")
    mutated = original.replace(
        "): Boolean = (from to target) in allowedJobStateTransitions",
        "): Boolean = (from to target) in allowedJobStateTransitions ||\n"
        "    from == JobState.JOB_STATE_FAILED",
        1,
    )
    assert mutated != original
    assert kotlin_predicate_body(mutated) != _KOTLIN_PREDICATE_BODY


def test_python_table_edit_breaks_the_rendered_equality() -> None:
    """권위가 Python 표라는 것의 다른 쪽 — 표를 고치면 문면 등식이 깨진다(문면을 함께
    고쳐야 초록이 된다)."""
    from ml_engine.training.jobs.state import JobEvent

    mutated = dict(_TRANSITIONS)
    mutated[(JobState.FAILED, JobEvent.START)] = JobState.RUNNING
    edges = frozenset(
        (from_state.value, to_state.value)
        for (from_state, _event), to_state in mutated.items()
        if from_state is not to_state
    )
    rows = [_PROTO_ROW.format(left=left, right=right) for left, right in sorted(edges)]
    assert rows != render_rows(_PROTO_ROW)
    assert block_lines(_PROTO_PATH.read_text(encoding="utf-8")) != rows
