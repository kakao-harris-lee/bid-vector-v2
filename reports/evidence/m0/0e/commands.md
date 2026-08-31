# 실행 명령 — M0 / 0E · spec-writer 레인

**명령 · exit · 핵심 결과 한 줄.** 출력 전문을 붙이지 않는다 — 감사자는 명령을 다시 돌린다
(`.claude/skills/evidence-pack/SKILL.md`). 저장소 루트에서 돌린다.

## 이 range 에는 세 레인이 있다

`14686db..HEAD` 는 **spec-writer(이 evidence) · fixture-curator(`fixtures/**` ·
`reports/evidence/m0/0e/fixtures-*.md`) · 하네스(`.claude/**` · `CLAUDE.md`)** 셋의 커밋을
함께 담는다. **아래 `C-0` 이 이 레인의 커밋 집합을 pathspec 으로 뽑고, `C-7` 이 그 집합만으로
불변을 확인한다** — `14686db..HEAD` 전체로 재면 다른 레인의 변경이 섞인다.

```sh
LANE=(docs/adr docs/discovery/capability-map.md milestone-0.md milestone-1.md \
      'v2-지침서.md' reports/evidence/m0/0e/scope.md \
      reports/evidence/m0/0e/commands.md reports/evidence/m0/0e/checklist.md)
```

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| **C-0** | `git log --format=%h 14686db..HEAD -- "${LANE[@]}"` | 0 | 이 레인의 커밋 집합 (`1fe87eb` 이후). **수를 여기 적지 않는다** — 이 명령이 낸다. `C-7a`·`C-7b` 의 분모다 |
| **C-1a** | `sed -n '/^## 12\. OPEN/,/^### 12\.1/p' docs/discovery/capability-map.md \| grep -cE '^\| *OPEN-[A-Z]+-[0-9]+ *\|'` | 0 | **43** (45 → 43. `OPEN-OPS-07`·`OPEN-QUAL-05` 해소) |
| **C-1b** | `sed -n '/^## 9\. \`OPEN\`/,/^## 10\./p' docs/discovery/regression-ledger.md \| grep -oE 'OPEN-REG-[0-9]+' \| sort -u \| wc -l` | 0 | **5** (무변경) |
| **C-1c** | `sed -n '/^## 9\. \`OPEN\`/,/^## 10\./p' docs/discovery/data-dictionary.md \| grep -coE '^\| \*\*\`OPEN-DIC-[0-9]+\`\*\*'` | 0 | **10** (무변경) |
| **C-1d** | `grep -hcE '^### \`OPEN-ADR-[0-9]+\`' docs/adr/*.md \| paste -sd+ - \| bc` | 0 | **11** (12 → 11. `OPEN-ADR-01` 해소) |
| **C-2** | 그룹별 셈 — `scripts` 없이 python3 인라인, §12 G1~G6 의 행 수 (전문은 이 표 아래) | 0 | `{G1:3, G2:7, G3:14, G4:8, G5:7, G6:4} TOTAL 43` — §12 머리말의 선언과 일치 |
| **C-3** | §14 커버리지 양방향 대조 (전문은 이 표 아래) | 0 | 활성 **69** · (B) **14** · (C) **55** · **활성인데 미분류 `[]` · 분류인데 비활성 `[]` · (B)∩(C) `[]`** |
| **C-4** | `git diff 14686db HEAD -- 'v2-지침서.md' milestone-1.md milestone-0.md` | 0 | 개정 셋의 전후 문면. Q1 `3.x`→`4.x`+4.x 재확인 항목 · Q2 유효기간 축 제거+미검증 노출 유지 · Q3 산출물 행 삭제+`ADR 0009` (d) 사유. `milestone-0.md` 는 「M0 완료 기록」(A/B 리뷰 · 승인 표의 ADR 행)도 함께 낸다 |
| **C-5a** | `git diff -U0 14686db HEAD -- docs/adr/ \| grep -cE '^@@'` | 0 | **11** = 아홉 파일의 「상태」 줄 각 1 hunk + `ADR 0001` §5 의 두 hunk. **§1~§4·§6 무접촉** — 파일별 분해는 같은 명령에 `awk '/^\+\+\+/{f=$2} /^@@/{c[f]++}'` 를 이어 붙이면 난다 |
| **C-5b** | `for f in docs/adr/*.md; do sed -n '3p' "$f"; done \| grep -c '^- \*\*상태\*\*: \*\*결정\*\*'` | 0 | **9** — 아홉 전부 「결정」 |
| **C-6a** | `grep -n '^## 3\.' docs/adr/0005-domain-events-and-outbox.md` | 0 | `236:## 3. 대안 — \`OPEN-OPS-07\` 후보의 판정` — 종료 조건이 산출물에 실재 |
| **C-6b** | `grep -c '^### \`OPEN-ADR-01\`' docs/adr/0001-target-architecture.md` | 1 | **0** — 취소선 없는 활성 제목 없음 (`^### ~~\`OPEN-ADR-01\`~~` 은 1) |
| **C-7a** | `for c in $(git log --format=%h 14686db..HEAD -- "${LANE[@]}"); do git diff --name-only $c^ $c; done \| sort -u` | 0 | 이 레인이 건드린 파일 **16개** — `in_scope` 와 정확히 같다 |
| **C-7b** | 같은 목록 \| `grep -E '^(fixtures/\|docs/discovery/(data-dictionary\|regression-ledger)\|reports/evidence/m0/0[a-d])'` | **1** | **매치 0** — `fixtures/` · `data-dictionary.md` · `regression-ledger.md` · 앞 여섯 slice evidence **무접촉** |
| **C-8** | `for f in reports/evidence/m0/*/codex-review-*.json; do python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print(d['verdict'],d['reviewed_base'][:8],d['reviewed_head'][:8])" "$f"; done` | 0 | 각 리뷰 JSON 의 `verdict`·base·head 를 낸다. **건수도 head SHA 도 여기 옮겨 적지 않는다** — verdict 가 등재될 때마다 바뀌므로 **이 명령이 낸다**. 읽는 법: slice 별 마지막 줄이 그 slice 의 현재 상태이고, 0A 의 `approve` 는 `6c6b3a2a..6af70199` 의 **별도 리뷰 A** 다(`C-9b`·`C-9c` 가 그 range 를 다룬다) |
| **C-9a** | `git log --oneline 6c6b3a2..6af7019 -- docs/discovery/capability-map.md` | 0 | 커밋 **4개** (`0b0c8aa`·`23b9c2a`·`0581e97`·`d140f92`) — 0A 라운드 4~6 |
| **C-9b** | `git diff --stat 6c6b3a2 6af7019 -- docs/discovery/capability-map.md` | 0 | **31 삽입 / 14 삭제** — 실행 시점에 어느 리뷰 range 에도 들지 않았던 delta. **그 뒤 별도 리뷰 A(`6c6b3a2..6af7019`) 가 덮어 `approve`** |
| **C-9c** | `python3 -c "import json;print(json.load(open('reports/evidence/m0/0a2/codex-review-20260827T034348Z.json'))['reviewed_base'])"` | 0 | `6af7019…` — 0A2 리뷰의 base 가 그 넷 **뒤**다 |
| **C-10** | `git diff --numstat 14686db HEAD -- 'v2-지침서.md' milestone-0.md milestone-1.md docs/adr docs/discovery/capability-map.md \| awk '{a+=$1;d+=$2} END{print a-d}'` · `wc -l reports/evidence/m0/0e/{scope,commands,checklist}.md` | 0 | **게이트 통과** — 판정은 `evidence ≤ 산출물`. **두 수를 여기 옮겨 적지 않는다** — 커밋이 늘 때마다 바뀌므로 **이 명령이 낸다** |

**`C-2`·`C-3` 의 python3 블록** (긴 명령이라 표 밖에 둔다. **quoted heredoc 이라 셸 확장이
없다** — 이대로 붙여 돌리면 재현된다).

```sh
# C-2 — §12 의 그룹별 활성 행 수
python3 - <<'PY'
import re, io
s = io.open('docs/discovery/capability-map.md', encoding='utf-8').read()
sec = s.split('## 12. OPEN 결정 목록')[1].split('### 12.1')[0]
cur, g = None, {}
for l in sec.splitlines():
    m = re.match(r'^### (G\d)\.', l)
    if m: cur = m.group(1); g[cur] = []
    m2 = re.match(r'^\| *(OPEN-[A-Z]+-\d+) *\|', l)
    if m2 and cur: g[cur].append(m2.group(1))
print({k: len(v) for k, v in sorted(g.items())}, 'TOTAL', sum(len(v) for v in g.values()))
PY

# C-3 — 네 정본의 활성 합집합을 §14 의 (B)/(C) 와 양방향 대조
python3 - <<'PY'
import re, io, glob
rd = lambda f: io.open(f, encoding='utf-8').read()
cap = rd('docs/discovery/capability-map.md')
a = set(re.findall(r'^\| *(OPEN-[A-Z]+-\d+) *\|',
                   cap.split('## 12. OPEN 결정 목록')[1].split('### 12.1')[0], re.M))
sec9 = lambda t: re.search(r'^## 9\. `OPEN`.*?^## 10\.', t, re.M | re.S).group(0)
a |= set(re.findall(r'OPEN-REG-\d+', sec9(rd('docs/discovery/regression-ledger.md'))))
a |= set(re.findall(r'^\| \*\*`(OPEN-DIC-\d+)`\*\*', sec9(rd('docs/discovery/data-dictionary.md')), re.M))
for f in glob.glob('docs/adr/*.md'):                      # 취소선 없는 §5 제목만 = 활성
    a |= set(re.findall(r'^### `(OPEN-ADR-\d+)`', rd(f), re.M))
s = cap.split('## 14. M0 이월 목록')[1]
row = lambda t: set(re.findall(r'^\| `(OPEN-[A-Z]+-\d+)`', t, re.M))
B = row(s.split('### 14.2')[1].split('### 14.3')[0])
C = row(s.split('### 14.3')[1].split('### 14.4')[0])
print('활성', len(a), '| B', len(B), '| C', len(C),
      '| 미분류', sorted(a - (B | C)), '| 비활성분류', sorted((B | C) - a), '| B∩C', sorted(B & C))
PY
```
