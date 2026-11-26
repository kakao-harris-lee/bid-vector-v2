#!/usr/bin/env python3
"""M15/15S 수정 라운드 1 — 핀 게이트의 **수집 완전성**을 고정된 YAML 파서로 잰다.

왜 있는가: `tools/workflow-action-pins-check.sh` 는 러너에 도구가 없어도 서도록 awk 로 YAML 을
훑는다(계약 D-15S-2, 선례 `workflow-step-refs-check.sh`). 그 선택의 대가가 r1 verifier H-1 이다 —
`- if: >-` 다음 줄의 형제 `uses:`, `name: x:|`, 따옴표 친 키, 복합 키가 **유효한 YAML 인데도**
수집에서 조용히 빠졌고 게이트는 초록이었다. 표기를 하나씩 막는 것은 같은 결함을 또 부른다.

그래서 수집 완전성을 **문자열이 아니라 등식**에 건다: 게이트가 `--count` 로 내는 `uses` 값
다중집합 == PyYAML 이 같은 파일들에서 읽는 `uses` 값 다중집합. 게이트의 awk 가 모르는 표기는
이제 「조용히 건너뜀」이 아니라 **등식 붉음**이다.

**두 자리가 나뉘는 사유**: 꼴·집합 등식·판 주석을 보는 게이트 자신은 `container` job 에서 돈다 —
새 의존 0 이 그 자리의 전제다. 이 parity 는 **고정된 파서가 있는 자리**인 `ml-engine` job 에서
돈다(`pyyaml` 이 `uv.lock` 에 핀돼 있고 `uv sync --frozen --all-extras` 가 깐다). 파서를
`container` 로 들이면 그 job 의 전제가 깨지고, 게이트를 `ml-engine` 으로 옮기면 「새 의존 0」이
깨진다.

**막지 않는 것**: 두 수집이 **같은 방식으로** 틀린 경우. PyYAML 도 파일 목록을 게이트와 같은
규칙으로 모으므로, 수집 대상 파일 자체를 놓치면 둘 다 놓친다(그 축은 게이트의 비공허 술어와
`.github/actions` 술어가 따로 받는다).

실행:

    cd ml-engine && uv run --no-sync python ../tools/workflow-action-pins-parity.py ../.github/workflows
"""

from __future__ import annotations

import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any

import yaml

GATE = Path(__file__).resolve().parent / "workflow-action-pins-check.sh"


def workflow_files(root: Path) -> list[Path]:
    """게이트의 `_workflow_files` 와 **같은 규칙** — 확장자 대소문자 무시, 심볼릭 링크 따라감."""
    found = [
        p
        for p in sorted(root.rglob("*"))
        if p.is_file() and p.suffix.lower() in (".yml", ".yaml")
    ]
    return found


def collect_uses(node: Any, out: list[str]) -> None:
    """트리 어디에 있든 `uses` 키의 값을 전부 줍는다 — step 자리·job 자리를 가리지 않는다."""
    if isinstance(node, dict):
        for key, value in node.items():
            if isinstance(key, str) and key == "uses":
                if not isinstance(value, str):
                    raise ValueError(f"uses 값이 문자열이 아니다: {value!r}")
                out.append(value)
            else:
                collect_uses(value, out)
    elif isinstance(node, list):
        for item in node:
            collect_uses(item, out)


def yaml_uses(root: Path) -> list[str]:
    out: list[str] = []
    for path in workflow_files(root):
        text = path.read_text(encoding="utf-8")
        for doc in yaml.safe_load_all(text):
            collect_uses(doc, out)
    return out


def gate_uses(root: Path) -> list[str]:
    run = subprocess.run(
        [str(GATE), "--count", str(root)],
        capture_output=True,
        text=True,
        check=False,
    )
    if run.returncode != 0:
        sys.stderr.write(run.stderr)
        sys.stderr.write(
            f"게이트의 --count 가 비-0({run.returncode})으로 끝났다 — 수집을 비교할 수 없다(판정 불가)\n"
        )
        # 종료 코드 계약은 게이트와 같다: `0 통과 · 1 위반 · 2 판정 불가`.
        # `raise SystemExit("문자열")` 은 **1** 로 끝나므로 쓰지 않는다(실측).
        sys.exit(2)
    return [line for line in run.stdout.splitlines() if line]


def mismatch(gate: list[str], parsed: list[str]) -> list[str]:
    """두 다중집합의 차이를 양방향으로 낸다. 같으면 빈 목록."""
    left, right = Counter(gate), Counter(parsed)
    lines: list[str] = []
    for value in sorted((left - right).elements()):
        lines.append(f"  게이트만 수집: {value}")
    for value in sorted((right - left).elements()):
        lines.append(f"  파서만 수집: {value}")
    return lines


def main() -> int:
    if len(sys.argv) != 2:
        sys.stderr.write(f"사용법: {sys.argv[0]} <워크플로 디렉터리>\n")
        return 2
    root = Path(sys.argv[1])
    if not root.is_dir():
        sys.stderr.write(f"워크플로 디렉터리가 없다: {root}\n")
        return 2

    gate = gate_uses(root)
    parsed = yaml_uses(root)

    # 비공허 — 「오탐 0 인 판이 양성도 0」을 막는다. 둘 다 비면 등식은 공허하게 참이다.
    if not gate or not parsed:
        sys.stderr.write(
            f"수집이 비었다(게이트 {len(gate)} · 파서 {len(parsed)}) — 등식이 공허하다(판정 불가)\n"
        )
        return 2

    # 음성 대조 — 비교 자체가 **한 건의 차이를 낼 수 있는가**를 매 실행 잰다. 각 목록을 **자기
    # 자신**과 비교하되 한 건을 빼고 본다. 두 목록을 서로 비교하면서 한쪽만 줄이면 안 된다 —
    # 애초에 둘이 다를 때 그 조작이 우연히 둘을 같게 만들 수 있고(실측: 게이트가 한 건을 놓친
    # 판에서 파서 쪽을 하나 줄이자 두 다중집합이 같아졌다), 그러면 진짜 위반이 「음성 대조
    # 실패」로 가려진다.
    for label, values in (("게이트", gate), ("파서", parsed)):
        if not mismatch(values, values[1:]):
            sys.stderr.write(
                f"음성 대조 실패 — {label} 수집에서 한 건을 빼도 비교가 어긋나지 않는다(판정 불가)\n"
            )
            return 2

    diff = mismatch(gate, parsed)
    if diff:
        sys.stderr.write(
            "수집 등식 위반 — awk 게이트가 읽은 uses 다중집합이 YAML 파서의 것과 다르다:\n"
        )
        sys.stderr.write("\n".join(diff) + "\n")
        return 1

    print(
        f"수집 등식 통과 — uses {len(gate)} 건이 awk 게이트와 PyYAML "
        f"{yaml.__version__} 에서 같은 다중집합 · 음성 대조 둘 통과"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
