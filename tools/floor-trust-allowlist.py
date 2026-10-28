#!/usr/bin/env python3
"""DEC-10 게이트 등식(`FloorTrustGateTest`)의 실패 로그에서 **허용 목록의 키 단위 차이**를 뽑는다.

왜 도구인가 — 허용 목록의 키는 서명을 담아 길고(`패키지.클래스.메서드(인자|인자)`) 수가 90 을
넘는다. 손으로 베끼면 한 글자 차이로 등식이 깨지고, 그 실패는 「우회가 생겼다」와 구별되지
않는다. 그래서 **로그에서 파서로** 뽑는다.

읽는 자리는 `shouldMatchAllowed` 가 clue 로 내는 **`+`/`-` 줄**이다(CR r5 M-1):

    org.opentest4j.AssertionFailedError: 허용 밖 관측 1
      + bidvector…marginInputsFrom(…)
    낡은 허용 1
      - bidvector…neverObserved(…)

kotest 의 `expected:<[…]> but was:<[…]>` 줄은 **읽지 않는다**. 그 줄은 컬렉션 렌더링이라 출력
한도(기본 20)에서 잘리고, 앞 판은 그것을 읽어 **조용히 부분집합**을 냈다(exit 0). clue 는
문자열이라 잘리지 않으며, 선언된 개수(`허용 밖 관측 N`)와 실제 줄 수를 **대조**하므로 형태가
바뀌면 침묵하지 않고 비영 종료한다.

사용:

    ./gradlew --no-daemon :app:test --rerun --tests 'bidvector.app.architecture.FloorTrustGateTest' \\
        > /tmp/gate.log 2>&1 || true
    python3 tools/floor-trust-allowlist.py /tmp/gate.log

**관측 집합 전체가 필요하면** 그 축의 정책 키를 **비우고** 돌린다 — 관측 전부가 `+` 줄로 나온다.

**재생성은 그 자체로 우회가 될 수 있다**(D-DEC10-25) — 관측을 그대로 허용으로 덮으면 등식은
언제나 초록이고, 새로 생긴 소비자가 그 덮기에 숨는다. 그래서 규율 둘을 지킨다.

1. **키 단위 diff** — 이 도구의 출력이 곧 그 diff 다(`+` 는 허용 밖 관측, `-` 는 낡은 허용).
   집합 크기만 비교하면 「하나 늘고 하나 줄었다」가 보이지 않는다.
2. **추가된 키마다 자리를 적는다** — 그 코드 단위가 왜 원값·통과 값·정책 값·진입 타입을
   만지는지를 evidence 에 한 줄로. 설명할 수 없는 키는 허용이 아니라 **우회 후보**다.

종료 코드: `0` 축 하나 이상을 읽었고 모두 일관 · `2` 축 블록을 하나도 못 읽음(전건 초록이거나
로그 형태가 바뀜) · `3` 선언된 개수와 글머리 줄 수가 다름(절단·형태 변경) · `64` 인자 오류 ·
`66` 로그를 읽을 수 없음.

lint: 저장소 루트에 ruff 설정이 없어 이 파일은 **ruff 기본 규칙**으로 검사된다(통과). `ml-engine`
설정(더 넓은 select)으로 돌리면 `AXES` 의 프라임 문자(`ⓐ′`·`ⓑ′`)가 `RUF001` 로 잡히는데, 그
문자는 **Kotlin test 이름과 글자 그대로 맞아야** 하는 값이라 바꿀 수 없다 — `tools/` 를 그 범위에
넣는 날 필요한 것은 문자 교체가 아니라 per-file ignore 다(evidence 알려진 제한 ⑫).
"""

from __future__ import annotations

import re
import sys

# 축 ↔ 실패 블록을 찾는 test 이름 조각. `FloorTrustGateTest` 의 등식 test 이름이 바뀌면 여기도
# 바뀐다. 블록은 **다음 축 제목까지**로 잘라서 본다 — 그러지 않으면 초록 축의 이름이 로그에
# 나타날 때 다음 실패 축의 항목을 그 축 이름으로 낸다(CR r5 L-2 가 합성 로그로 재현했다).
AXES = {
    "ⓐ 게이트 통과 값의 멤버 접근": "gated_class",
    "ⓐ′ 생성 계열": "creation",
    "ⓑ 원값을 얻는": "acquisition",
    "ⓑ′ 원값의 멤버": "member",
    "ⓒ 정책 값을 만드는": "policy",
    "ⓓ 가격 경로 입력 타입을": "price_entry",
}

EXTRA_HEADER = re.compile(r"허용 밖 관측 (\d+)")
STALE_HEADER = re.compile(r"낡은 허용 (\d+)")


class ClueMismatchError(Exception):
    """선언된 개수와 실제 글머리 줄 수가 다르다 — 절단이거나 clue 형태가 바뀌었다."""


def as_policy_key(clue_key: str) -> str:
    """clue 의 Kotlin `Pair.toString()` 꼴 `(단위, 멤버)` 를 정책 파일 꼴 `단위->멤버` 로 바꾼다.

    도구가 생긴 이유가 「손으로 베끼면 한 글자 차이로 등식이 깨진다」이므로, **붙여 넣을 수 있는
    꼴**로 내는 것이 마지막 한 걸음이다(verifier r6 L-1). 단위가 서명을 담아 괄호를 포함하므로
    **마지막 `, ` 에서만** 가른다 — 키 안에는 쉼표가 없다(인자 타입은 `|` 로 잇는다).
    """
    if not (clue_key.startswith("(") and clue_key.endswith(")")):
        return clue_key
    unit, separator, member = clue_key[1:-1].rpartition(", ")
    return f"{unit}->{member}" if separator else clue_key


def axis_blocks(text: str) -> list[tuple[str, str]]:
    """(축 이름, 그 축의 블록) 목록. 블록은 그 제목부터 다음 제목(또는 끝)까지다."""
    marks: list[tuple[int, str]] = []
    for title, name in AXES.items():
        marks.extend(
            (match.start(), name) for match in re.finditer(re.escape(title), text)
        )
    marks.sort()
    blocks = []
    for index, (start, name) in enumerate(marks):
        end = marks[index + 1][0] if index + 1 < len(marks) else len(text)
        blocks.append((name, text[start:end]))
    return blocks


def collect(block: str, header: re.Pattern[str], bullet: str) -> list[str] | None:
    """머리글 뒤의 글머리 줄을 모은다. 그 머리글이 블록에 없으면 `None`(그 축은 초록이다)."""
    found = header.search(block)
    if not found:
        return None
    declared = int(found.group(1))
    keys = []
    for line in block[found.end() :].splitlines()[1:]:
        stripped = line.strip()
        if stripped.startswith(f"{bullet} "):
            keys.append(stripped[2:])
            continue
        if stripped:
            break  # 다음 머리글이거나 다른 출력 — 글머리 줄은 연속이다
    if len(keys) != declared:
        raise ClueMismatchError(f"선언 {declared} · 읽은 줄 {len(keys)}")
    return keys


def main(log_path: str) -> int:
    try:
        with open(log_path, encoding="utf-8") as handle:
            text = handle.read()
    except OSError as error:
        print(f"로그를 읽을 수 없다: {error}", file=sys.stderr)
        return 66

    # 한 축의 이름은 로그에 여러 번 나온다(실패 줄 · 스택 줄). clue 를 담은 블록만 쓰고,
    # 어느 블록에도 없으면 그 축을 한 번만 「초록」으로 적는다.
    parsed: dict[str, tuple[list[str], list[str]] | None] = {}
    for name, block in axis_blocks(text):
        try:
            extra = collect(block, EXTRA_HEADER, "+")
            stale = collect(block, STALE_HEADER, "-")
        except ClueMismatchError as error:
            print(f"{name}: clue 가 일관되지 않다 — {error}", file=sys.stderr)
            return 3
        if extra is None and stale is None:
            parsed.setdefault(name, None)
            continue
        # 같은 축의 clue 가 두 블록으로 갈리면 **덮지 않고 합친다**(CR r6 L-3) — 덮으면 먼저
        # 읽은 블록의 키가 조용히 사라지고, 선언 개수 대조는 그 경로를 덮지 못한다.
        previous = parsed.get(name) or ([], [])
        parsed[name] = (previous[0] + (extra or []), previous[1] + (stale or []))

    read = 0
    # 축 절의 순서는 **`AXES` 선언 순서**다(CR r6 L-2) — 로그 등장 순서로 내면 test 실행 순서가
    # 바뀔 때 같은 관측에서 출력 diff 가 생겨, 라운드 간 키 단위 diff 에 잡음이 된다.
    for name in [axis for axis in AXES.values() if axis in parsed]:
        found = parsed[name]
        if found is None:
            print(f"# {name}: clue 없음(그 축이 초록이다)")
            continue
        extra, stale = found
        read += 1
        print(f"### {name} — 허용 밖 관측 {len(extra)} · 낡은 허용 {len(stale)}")
        for key in sorted(as_policy_key(k) for k in extra):
            print(f"+ {key}")
        for key in sorted(as_policy_key(k) for k in stale):
            print(f"- {key}")
        print()

    if read == 0:
        print(
            "축 블록을 하나도 못 읽었다 — 전건 초록이거나 로그 형태가 바뀌었다."
            " 빈 출력을 성공으로 읽지 않도록 비영 종료한다.",
            file=sys.stderr,
        )
        return 2
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"사용: {sys.argv[0]} <게이트 test 로그>", file=sys.stderr)
        raise SystemExit(64)
    raise SystemExit(main(sys.argv[1]))
