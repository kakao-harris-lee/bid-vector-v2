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
| **C-7a** | `for c in $(git log --format=%h 14686db..HEAD -- "${LANE[@]}"); do git diff --name-only $c^ $c; done \| sort -u` | 0 | 이 레인의 커밋들이 건드린 파일 집합. **수를 여기 적지 않는다** — 커밋이 늘면 바뀌므로 **이 명령이 낸다**. `in_scope` 밖 경로가 섞였는지는 `C-7b` 가 본다 |
| **C-7b** | 같은 목록 \| `grep -E '^(fixtures/\|docs/discovery/(data-dictionary\|regression-ledger)\|reports/evidence/m0/0[a-d])'` | **0** | **매치 하나 — `fixtures/manifest.yaml`.** `8701882` 이 `milestone-0.md` 와 `fixtures/manifest.yaml` 의 **같은 인용 좌표를 한 커밋에** 담아 pathspec 이 두 레인에 걸쳤다. **선언된 예외**이고 판정·근거·범위는 표 아래 **「`C-7b` 매치의 처리」**가 갖는다. `data-dictionary.md` · `regression-ledger.md` · 앞 여섯 slice evidence 는 여전히 **무접촉** |
| **C-8** | `for f in reports/evidence/m0/*/codex-review-*.json; do python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print(d['verdict'],d['reviewed_base'][:8],d['reviewed_head'][:8])" "$f"; done` | 0 | 각 리뷰 JSON 의 `verdict`·base·head 를 낸다. **건수도 head SHA 도 여기 옮겨 적지 않는다** — verdict 가 등재될 때마다 바뀌므로 **이 명령이 낸다**. 읽는 법: slice 별 마지막 줄이 그 slice 의 현재 상태이고, 0A 의 `approve` 는 `6c6b3a2a..6af70199` 의 **별도 리뷰 A** 다(`C-9b`·`C-9c` 가 그 range 를 다룬다) |
| **C-9a** | `git log --oneline 6c6b3a2..6af7019 -- docs/discovery/capability-map.md` | 0 | 커밋 **4개** (`0b0c8aa`·`23b9c2a`·`0581e97`·`d140f92`) — 0A 라운드 4~6 |
| **C-9b** | `git diff --stat 6c6b3a2 6af7019 -- docs/discovery/capability-map.md` | 0 | **31 삽입 / 14 삭제** — 실행 시점에 어느 리뷰 range 에도 들지 않았던 delta. **그 뒤 별도 리뷰 A(`6c6b3a2..6af7019`) 가 덮어 `approve`** |
| **C-9c** | `python3 -c "import json;print(json.load(open('reports/evidence/m0/0a2/codex-review-20260827T034348Z.json'))['reviewed_base'])"` | 0 | `6af7019…` — 0A2 리뷰의 base 가 그 넷 **뒤**다 |
| **C-10** | `git diff --numstat 14686db HEAD -- 'v2-지침서.md' milestone-0.md milestone-1.md docs/adr docs/discovery/capability-map.md \| awk '{a+=$1;d+=$2} END{print a-d}'` · `wc -l reports/evidence/m0/0e/{scope,commands,checklist}.md` | 0 | **게이트 통과** — 판정은 `evidence ≤ 산출물`. **두 수를 여기 옮겨 적지 않는다** — 커밋이 늘 때마다 바뀌므로 **이 명령이 낸다** |

**`C-7b` 매치의 처리 — `8701882` 을 선언된 예외로 둔다.**

**이 자리가 `C-7a` 의 경로 집합과 `C-7b` 매치의 정본이다** — `checklist.md`·`scope.md` 는 여기를
가리키고 같은 사실을 따로 적지 않는다. 수는 어느 쪽에도 적지 않는다(`C-7a`·`C-7b` 가 낸다).

*판정은 오케스트레이터가 냈고 **운영자가 승인했다**(2026-08-31).* 사후 승인 대상이던 자리가 닫혔다.

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).*
물음: *"오케스트레이터가 『8701882의 두 레인 교차 커밋은 선언된 예외』로 판정했고 운영자 사후 승인
대상으로 표지돼 있다. Codex는 checklist·scope가 아직 『fixtures 무접촉』을 주장해 모순이라 지적한다.
이 예외를 승인하는가?"*
답: ***"승인 + 정본 일치화"*** — 「불승인 — 미해결 제한으로 기록」을 물리치고 택한 답이다. 답이 든
선택지 문면: *"예외를 운영자 결정으로 기록하고 checklist·scope·rollback 서술을 C-7 실측과 한 정본으로
일치시킴."*

*이 레인이 그 답을 적용한 범위*(운영자 발화가 아니라 적용이다). 아래 판정 문면은 그대로 두고 표지만
「사후 승인 대상」에서 「승인됨」으로 옮겼으며, `checklist.md` 의 두 행과 `scope.md` 의 세 자리
(out_of_scope · rollback · 경계 서술)를 이 정본을 가리키는 형태로 고쳤다.

- **무엇이 잡히는가.** `8701882`(「`milestone-1.md` 인용 좌표를 `:12` 에서 `:10` 으로 — 두 자리」)이
  `milestone-0.md` 와 `fixtures/manifest.yaml` 을 한 커밋에 담았다. 두 레인의 pathspec 에 동시에
  걸리므로 `C-7b` 가 매치를 낸다.
- **왜 예외로 두는가.** ① 내용이 **한 인용 좌표 정정을 두 레인 파일에 일관 적용**한 것이라 레인별
  산출물을 갈라 쓴 자리가 아니다 — 두 자리 중 하나만 고치면 저장소가 같은 문장에 다른 좌표를
  갖는다. ② **소급 분리가 이 slice 의 게이트와 양립하지 않는다** — 부모 사슬 직선·amend/rebase
  금지가 걸려 있어 역사를 다시 쓸 수 없다. 그 커밋은 base `14686db` **뒤**이지만 **0E Codex 리뷰
  세 라운드의 head(`857e7767`·`74ef4415`·`71e3e3a1`) 전부의 조상**이라 이미 리뷰된 range 안에
  있다(`git merge-base --is-ancestor 8701882 <head>` 가 낸다). 분리하려면 리뷰된 역사를 다시
  써야 한다.
- **예외의 범위는 `8701882` 하나다.** `C-7b` 의 나머지 판정은 그대로 선다. **다음 커밋에 같은
  형태가 또 나오면 새 판정 대상**이며 이 항목이 그것을 덮지 않는다.

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
