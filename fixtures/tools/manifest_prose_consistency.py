#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C-12b — manifest 산문의 **낡은 덮개 주장**을 찾는다.

강등이 일어난 뒤, manifest 안의 산문이 여전히 어떤 case 를 **지금 `authoritative` 다**
라고 **주장형**으로 말하는 자리를 문단 단위로 센다. 정의는
`reports/evidence/m0/0e/commands.md` **C-12b** 가 갖고 이 스크립트는 그 실행이다.

**정의(그대로 옮긴다).**

- **블록은 빈 줄로 가른다.** 줄 단위였던 앞 판(B11)은 id 와 `authoritative` 가 다른 줄에
  있으면 놓쳤다(Codex B12 medium). 그래서 문단으로 넓혔다.
- **주장형 동사 여덟**(`ASSERT`)과 case id 가 **한 블록의 ±200자**(`WIN`) 안에 함께 있으면
  센다. 절 이름·과거형·강등 서술은 대상이 아니다.
- **제외는 과거형 표지 여섯**(`SKIP`). 창(窓) 안에 하나라도 있으면 그 자리는 세지 않는다.

**출력은 두 단위를 함께 낸다.** 헤드라인은 **적중한 case id 종류 수**이고(C-12b 행이
선언하는 수가 이것이다), 괄호가 **(블록, case) 쌍의 수**를 함께 적는다. 한 case 가 여러
문단에서 걸리면 둘이 갈리므로 어느 쪽을 말하는지 출력에 박아 둔다.

**이 검사가 못 보는 것 둘 — 고치지 않는다(정의 불변).**

1. **축약 인용을 잡지 못한다.** `money-basis-001`·`` `004` `` 처럼 뒤를 번호로만 적은
   자리는 id 문자열이 문면에 없어 걸리지 않는다. Codex 가 든 `money-basis-004` 가 그
   자리다.
2. **무해/유해를 기계가 가르지 못한다.** 남는 적중은 결정 축어 · `change_history` 의
   강등 기록 · 축 자신이 그 case 를 **강등된 이유로** 드는 자리 · 이미 ⚠ 로 층을 밝힌
   자리를 포함한다. **0 을 목표로 두지 않으며 잔존 목록은 사람이 읽는다.**

실행(표준 라이브러리만 쓴다. `python3` 하나면 된다):

    python3 fixtures/tools/manifest_prose_consistency.py
    python3 fixtures/tools/manifest_prose_consistency.py <manifest 경로>

인자를 주면 **산문과 분류를 둘 다 그 파일에서** 읽는다 — 옛 트리의 manifest 를 물려
그때의 값을 다시 낼 때 쓴다.
"""
import io
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import manifest_contract as mc  # noqa: E402

# 「지금 authoritative 다」는 **주장형**만 — 절 이름·과거형·강등 서술은 대상이 아니다
ASSERT = re.compile(r'`authoritative`\s*(로|으로)\s*(남|고정|덮|유지)|그대로\s*`authoritative`|`authoritative`\s*이고|덮인다|덮는다|고정한다|고정하고|계속 고정')
SKIP = re.compile(r'열릴 당시|이전의 상태|내려갔|비었|였다|이던')
WIN = 200


def blocks_of(src):
    """빈 줄로 가른 (시작 줄 번호, 줄 목록) 목록."""
    lines = src.splitlines()
    blocks, cur, start = [], [], 1
    for i, l in enumerate(lines, 1):
        if l.strip() == '':
            if cur:
                blocks.append((start, cur))
                cur = []
        else:
            if not cur:
                start = i
            cur.append(l)
    if cur:
        blocks.append((start, cur))
    return blocks


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    path = argv[0] if argv else mc.MANIFEST

    src = io.open(path, encoding='utf-8').read()
    cls = {c['id']: c['classification'] for c in mc.read_cases(path)}
    ids = sorted(cls, key=len, reverse=True)

    hit = []
    for st, blk in blocks_of(src):
        body = ' '.join(x.strip() for x in blk)
        for am in ASSERT.finditer(body):
            seg = body[max(0, am.start() - WIN): am.end() + WIN]
            if SKIP.search(seg):
                continue
            for cid in ids:
                if cid in seg and cls[cid] != 'authoritative':
                    hit.append((st, cid, cls[cid]))

    pairs = sorted(set(hit))
    for h in pairs:
        print('%d: %s -> %s' % h)
    print('불일치(case id) %d · 불일치(블록,case) %d'
          % (len({cid for _, cid, _ in pairs}), len(pairs)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
