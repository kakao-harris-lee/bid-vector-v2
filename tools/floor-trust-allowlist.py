#!/usr/bin/env python3
"""DEC-10 게이트 등식(`FloorTrustGateTest`)의 실패 로그에서 **관측 집합**을 뽑는다.

왜 도구인가 — 허용 목록의 키는 서명을 담아 길고(`패키지.클래스.메서드(인자|인자)`) 수가
90 을 넘는다. 손으로 베끼면 한 글자 차이로 등식이 깨지고, 그 실패는 「우회가 생겼다」와
구별되지 않는다. 그래서 **로그에서 파서로** 뽑는다.

사용:

    ./gradlew --no-daemon :app:test --tests 'bidvector.app.architecture.FloorTrustGateTest' \\
        > /tmp/gate.log 2>&1 || true
    python3 tools/floor-trust-allowlist.py /tmp/gate.log

**재생성은 그 자체로 우회가 될 수 있다**(CR r4 M-1) — 관측을 그대로 허용으로 덮으면 등식은
언제나 초록이고, 새로 생긴 소비자가 그 덮기에 숨는다. 그래서 출력을 정책 파일
(`config/quality/architecture-policy.properties`)에 옮길 때 규율 둘을 지킨다.

1. **키 단위 diff** — 통째로 덮지 않고 추가·삭제된 키만 본다(`comm -13`/`comm -23` 또는
   정렬 후 `diff`). 집합 크기만 비교하면 「하나 늘고 하나 줄었다」가 보이지 않는다.
2. **추가된 키마다 자리를 적는다** — 그 코드 단위가 왜 원값·통과 값·정책 값·진입 타입을
   만지는지를 evidence 에 한 줄로. 설명할 수 없는 키는 허용이 아니라 **우회 후보**다.

출력이 비면 **비영 종료**한다 — 「축이 초록이어서 블록이 없다」와 「파서가 로그 형태를 못
읽는다」가 같은 값(빈 출력)을 내지 않게 한다.
"""

from __future__ import annotations

import re
import sys

# 축 ↔ 실패 블록을 찾는 test 이름 조각. `FloorTrustGateTest` 의 등식 test 이름이 바뀌면
# 여기도 바뀐다(이름을 좌표로 쓰지 않으려면 조각이 짧아야 한다).
AXES = {
    "ⓐ 게이트 통과 값의 멤버 접근": "gated_class",
    "ⓐ′ 생성 계열": "creation",
    "ⓑ 원값을 얻는": "acquisition",
    "ⓑ′ 원값의 멤버": "member",
    "ⓒ 정책 값을 만드는": "policy",
    "ⓓ 가격 경로 입력 타입을": "price_entry",
}


def parse_items(blob: str) -> list[str]:
    """`but was:<[...]>` 안쪽을 항목으로 쪼갠다. 쌍은 `(a, b)`, 단일은 문자열이다."""
    blob = blob.strip()
    if blob.startswith("("):
        parts = re.findall(r"\(([^()]*(?:\([^()]*\)[^()]*)*)\)(?=, \(|$)", blob)
        out = []
        for part in parts:
            # 마지막 쉼표가 쌍 구분자다(키 안에는 쉼표가 없다 — 인자 타입은 `|` 로 잇는다).
            left, _, right = part.rpartition(", ")
            out.append(f"{left}->{right}" if left else part)
        return out
    return [x.strip().strip('"') for x in blob.split(", ") if x.strip()]


def main(log_path: str) -> int:
    text = open(log_path, encoding="utf-8").read()
    found = 0
    for title, name in AXES.items():
        match = re.search(re.escape(title) + r".*?but was:<\[(.*?)\]>", text, re.S)
        if not match:
            print(f"# {name}: 실패 블록 없음(그 축이 초록이거나 출력 형태가 다르다)")
            continue
        found += 1
        items = parse_items(match.group(1))
        print(f"### {name} — {len(items)}")
        for item in sorted(items):
            print(item)
        print()
    if found == 0:
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
