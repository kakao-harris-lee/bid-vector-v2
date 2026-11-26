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

**파일 목록 규칙은 게이트의 `_workflow_files`(`find -L … -iname`)와 같아야 한다** — 확장자
대소문자 무시 · 심볼릭 링크 **파일과 디렉터리** 둘 다 따라감 · 하위 디렉터리 재귀. 한쪽만 바꾸면
등식이 거짓말이 된다. `Path.rglob` 은 디렉터리 링크를 따라가지 않아 쓰지 않는다(r1 cr medium ·
verifier L-A 실측: 링크 디렉터리 하나로 `find -L` 2 · `rglob` 1).

**종료 코드 계약은 게이트와 같다 — `0 통과 · 1 위반 · 2 판정 불가`.** 「어긋났다」만 1 이다.
깨진 YAML · 비-UTF8 · 문자열이 아닌 `uses` 값 · 게이트를 부르지 못함 · 링크 순환은 전부 2 다.

**막지 않는 것**: 두 수집이 **같은 방식으로** 틀린 경우. 파일 목록 규칙을 공유하므로 수집 대상
파일 자체를 놓치면 둘 다 놓친다(그 축은 게이트의 비공허 술어와 LOCALDIR 술어가 따로 받는다).

실행:

    cd ml-engine && uv run --no-sync python ../tools/workflow-action-pins-parity.py ../.github/workflows
"""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:  # 러너에 파서가 없으면 **통과가 아니라 정지**다.
    sys.stderr.write(f"PyYAML 을 import 하지 못했다 — 수집을 비교할 수 없다(판정 불가): {exc}\n")
    raise SystemExit(2) from exc

GATE = Path(__file__).resolve().parent / "workflow-action-pins-check.sh"
SUFFIXES = (".yml", ".yaml")


class Unmeasurable(Exception):
    """잴 수 없다 — 「어긋났다(1)」가 아니라 「판정 불가(2)」로 나가는 모든 사유."""


def workflow_files(root: Path) -> list[Path]:
    """게이트의 `_workflow_files` 와 **같은 집합**을 낸다(`find -L` + `-iname`)."""
    found: list[Path] = []
    seen: set[tuple[int, int]] = set()
    for dirpath, dirnames, filenames in os.walk(root, followlinks=True):
        try:
            stat = os.stat(dirpath)
        except OSError as exc:
            raise Unmeasurable(f"{dirpath}: 디렉터리를 stat 하지 못했다: {exc}") from exc
        key = (stat.st_dev, stat.st_ino)
        if key in seen:
            # `find -L` 은 순환을 경고하고 건너뛴다. 여기서는 멈춘다 — 두 수집이 그 지점부터
            # 서로 다른 집합을 보게 되므로 등식이 말하는 바가 사라진다.
            raise Unmeasurable(f"{dirpath}: 심볼릭 링크 순환 — 파일 목록이 정해지지 않는다")
        seen.add(key)
        dirnames.sort()
        for name in sorted(filenames):
            path = Path(dirpath) / name
            if path.is_file() and path.suffix.lower() in SUFFIXES:
                found.append(path)
    return sorted(found)


def collect_uses(node: Any, out: list[str], where: Path) -> None:
    """트리 어디에 있든 `uses` 키의 값을 전부 줍는다 — step 자리·job 자리를 가리지 않는다."""
    if isinstance(node, dict):
        for key, value in node.items():
            if isinstance(key, str) and key == "uses":
                if not isinstance(value, str):
                    raise Unmeasurable(f"{where}: uses 값이 문자열이 아니다: {value!r}")
                out.append(value)
            else:
                collect_uses(value, out, where)
    elif isinstance(node, list):
        for item in node:
            collect_uses(item, out, where)


def yaml_uses(root: Path) -> list[str]:
    out: list[str] = []
    for path in workflow_files(root):
        try:
            text = path.read_text(encoding="utf-8")
            for doc in yaml.safe_load_all(text):
                collect_uses(doc, out, path)
        except Unmeasurable:
            raise
        except (OSError, UnicodeDecodeError, yaml.YAMLError) as exc:
            raise Unmeasurable(f"{path}: YAML 을 읽지 못한다: {exc}") from exc
    return out


def gate_uses(root: Path) -> list[str]:
    try:
        run = subprocess.run(
            [str(GATE), "--count", str(root)],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise Unmeasurable(f"{GATE}: 게이트를 실행하지 못했다: {exc}") from exc
    if run.returncode != 0:
        sys.stderr.write(run.stderr)
        raise Unmeasurable(f"게이트의 --count 가 비-0({run.returncode})으로 끝났다")
    return [line for line in run.stdout.splitlines() if line]


def mismatch(gate: list[str], parsed: list[str]) -> list[str]:
    """두 다중집합의 차이를 양방향으로 낸다. 같으면 빈 목록."""
    left, right = Counter(gate), Counter(parsed)
    lines = [f"  게이트만 수집: {v}" for v in sorted((left - right).elements())]
    lines += [f"  파서만 수집: {v}" for v in sorted((right - left).elements())]
    return lines


def compare_dir(root: Path) -> tuple[int, list[str], int]:
    """**본 판정을 내는 단 하나의 경로.** 음성 대조도 이 함수를 지난다."""
    gate = gate_uses(root)
    parsed = yaml_uses(root)
    if not gate and not parsed:
        raise Unmeasurable(f"{root}: 양쪽 수집이 비었다 — 등식이 공허하다")
    diff = mismatch(gate, parsed)
    return (1 if diff else 0, diff, len(gate))


# 한 매핑에 `uses:` 를 두 번 둔다 — **두 수집이 실제로 갈리는 입력**이다(실측): awk 는 줄 단위라
# 둘 다 세고, PyYAML 은 중복 키를 마지막 하나로 접는다. 그래서 심은 사본에서 `compare_dir` 은
# 반드시 1 을 내야 한다.
_PLANT = """on: push
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09
        uses: actions/setup-node@a0853c24544627f65ddf259abe73b1d18a591444
"""


def negative_control() -> None:
    """**호출 자리까지** 받는 음성 대조(r1 cr low · verifier L-B).

    앞 판은 `mismatch(v, v[1:])` 였다 — 다중집합 차이의 정의상 비지 않은 `v` 에 대해 **언제나**
    참이라 발화할 수 없었고, 비교 함수만 지켰다. 이제 본 판정과 **같은 `compare_dir`** 에 두
    수집이 실제로 갈리는 입력을 주고 1 이 나오는지 잰다. 그래서 `parsed = list(gate)` 처럼
    두 수집을 한 출처로 묶는 치환은 여기서 **0 이 나와 정지**한다.
    """
    with tempfile.TemporaryDirectory() as tmp:
        (Path(tmp) / "plant.yml").write_text(_PLANT, encoding="utf-8")
        rc, _diff, _n = compare_dir(Path(tmp))
    if rc != 1:
        raise Unmeasurable(
            "음성 대조 실패 — 두 수집이 갈리는 입력에서도 판정 경로가 어긋남을 내지 않는다"
        )


def main() -> int:
    if len(sys.argv) != 2:
        sys.stderr.write(f"사용법: {sys.argv[0]} <워크플로 디렉터리>\n")
        return 2
    root = Path(sys.argv[1])
    if not root.is_dir():
        sys.stderr.write(f"워크플로 디렉터리가 없다: {root}\n")
        return 2

    try:
        negative_control()
        rc, diff, count = compare_dir(root)
    except Unmeasurable as exc:
        sys.stderr.write(f"{exc} — 수집을 비교할 수 없다(판정 불가)\n")
        return 2

    if rc != 0:
        sys.stderr.write(
            "수집 등식 위반 — awk 게이트가 읽은 uses 다중집합이 YAML 파서의 것과 다르다:\n"
        )
        sys.stderr.write("\n".join(diff) + "\n")
        return 1

    print(
        f"수집 등식 통과 — uses {count} 건이 awk 게이트와 PyYAML "
        f"{yaml.__version__} 에서 같은 다중집합 · 음성 대조(심은 중복 키) 통과"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
