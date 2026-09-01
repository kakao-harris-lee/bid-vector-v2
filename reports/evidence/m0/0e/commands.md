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
| **C-7b** | 같은 목록 \| `grep -E '^(fixtures/\|docs/discovery/(data-dictionary\|regression-ledger)\|reports/evidence/m0/0[a-d])'` | **0** | **매치 하나 — `fixtures/manifest.yaml`.** `8701882` 이 `milestone-0.md` 와 `fixtures/manifest.yaml` 의 **같은 인용 좌표를 한 커밋에** 담아 pathspec 이 두 레인에 걸쳤다. **선언된 예외**이고 판정·근거·범위는 표 아래 **「`C-7b` 매치의 처리」**가 갖는다. **⚠ 이 검사는 이 레인의 `fixtures/` 접촉을 전수로 재지 못한다** — `C-0` 이 `in_scope` 경로로 레인을 뽑으므로 **`fixtures/manifest.yaml` 만 건드린 커밋은 입력에 들지 않는다.** 이 라운드에 그런 커밋이 났고 같은 절이 그것을 적는다. `data-dictionary.md` · `regression-ledger.md` · 앞 여섯 slice evidence 는 여전히 **무접촉** |
| **C-8** | `for f in reports/evidence/m0/*/codex-review-*.json; do python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print(d['verdict'],d['reviewed_base'][:8],d['reviewed_head'][:8])" "$f"; done` | 0 | 각 리뷰 JSON 의 `verdict`·base·head 를 낸다. **건수도 head SHA 도 여기 옮겨 적지 않는다** — verdict 가 등재될 때마다 바뀌므로 **이 명령이 낸다**. 읽는 법: slice 별 마지막 줄이 그 slice 의 현재 상태이고, 0A 의 `approve` 는 `6c6b3a2a..6af70199` 의 **별도 리뷰 A** 다(`C-9b`·`C-9c` 가 그 range 를 다룬다) |
| **C-9a** | `git log --oneline 6c6b3a2..6af7019 -- docs/discovery/capability-map.md` | 0 | 커밋 **4개** (`0b0c8aa`·`23b9c2a`·`0581e97`·`d140f92`) — 0A 라운드 4~6 |
| **C-9b** | `git diff --stat 6c6b3a2 6af7019 -- docs/discovery/capability-map.md` | 0 | **31 삽입 / 14 삭제** — 실행 시점에 어느 리뷰 range 에도 들지 않았던 delta. **그 뒤 별도 리뷰 A(`6c6b3a2..6af7019`) 가 덮어 `approve`** |
| **C-9c** | `python3 -c "import json;print(json.load(open('reports/evidence/m0/0a2/codex-review-20260827T034348Z.json'))['reviewed_base'])"` | 0 | `6af7019…` — 0A2 리뷰의 base 가 그 넷 **뒤**다 |
| **C-10** | `git diff --numstat 14686db HEAD -- 'v2-지침서.md' milestone-0.md milestone-1.md docs/adr docs/discovery/capability-map.md \| awk '{a+=$1;d+=$2} END{print a-d}'` · `wc -l reports/evidence/m0/0e/{scope,commands,checklist}.md` | 0 | **게이트 통과** — 판정은 `evidence ≤ 산출물`. **두 수를 여기 옮겨 적지 않는다** — 커밋이 늘 때마다 바뀌므로 **이 명령이 낸다** |
| **C-11** | B5-high 수정의 계약 불변 (전문은 이 표 아래) | 0 | `violations` **빈 목록**. ① `verified_paths` 의 어느 값도 **가족 A 의 네 이름이 아니다** ② `verified_projections` 는 다섯 필수 키 · 술어 `not-equals` · 피연산자가 가족 A 밖 ③ 같은 경로가 두 필드에 겹치지 않고 술어가 기대값에서 **참** ④ `insufficient-evidence` 에는 두 필드가 없다 ⑤ `change_history` 네 필수 키. **함께 fixture-curator 레인 `F-7b` 를 문면 그대로 재실행해** `violations` 빈 목록을 확인했다(신설 필드가 그 레인의 검사를 깨지 않는다). **이 검사가 못 보는 것**: `[i]` 첨자 경로는 값을 풀지 않고 건너뛴다 — 이 라운드가 바꾼 세 자리는 전부 최상위다 |
| **C-12** | 해소된 `OPEN` 의 **현재형 활성/대기 서술** (전문은 이 표 아래) | 0 | **B5 medium 이 든 둘(`OPS-21` 본문 · §13 하류 인계 표)은 사라졌다.** 남는 매치는 **하나**이고 `OPEN-OPS-07` 의 주장이 아니다 — `capability-map.md` §12 의 **`OPEN-DEC-07` 행**(활성)이 자기 종료 조건 미충족을 적으며 **판정 기준으로** `OPEN-OPS-07` 을 인용하는 자리다. **이 검사가 못 보는 것**: 같은 줄 안의 근접만 재므로 「누구에 대한 주장인가」는 사람이 읽는다 |
| **C-13** | `codex-review-gate` **preflight** — 리뷰 B5 (전문은 이 표 아래) | 0 | ① repo 흔적(`memory_summary`·`MEMORY.md`·`rollout_summaries/`·`skills/`·`rules`) **5건** ② `stage1_outputs` 중 `bid-vector-v2-review` 언급 **0** / **총 647행** — 총 행이 0이 아니므로 **DB 가 실제로 열렸고** 「0행」이 「못 열어서 0」이 아니다(독립성 전건 성립) ③ **B5 가 실제로 돈 CLI 는 `0.148.0`** — 정본은 실행 산출물 `_workspace/m0-0e/codex.raw-output-B5.txt` 의 머리글 `OpenAI Codex v0.148.0` 이며, 그 경로는 `.gitignore` 대상이라 clean worktree 에 없으므로 **저장소 안의 대응물은 verdict JSON 의 `reviewer.cli_version`** 이다 — 둘이 일치한다. **B~B4 는 0.149.0**(같은 디렉터리의 앞선 머리글 넷, verdict 쪽은 `C-8`). **알려진 제한 — B5 시점의 스킬은 심판 버전을 고정하지 않았다**: `git show dc27007^:.claude/skills/codex-review-gate/SKILL.md` 의 §4 가 맨 `codex` 를 부르므로 **어느 바이너리가 도는지를 PATH 가 정했고** 그래서 라운드마다 갈렸다(0.149.0 → 0.148.0). 이 evidence 를 쓰는 **도중에도 갈렸다** — `codex --version` 이 **0.148.0 → 0.151.0** 으로 바뀌었고 지금 `type -a codex` 는 `~/.nvm/versions/node/v22.21.1/bin/codex`(**0.151.0**) 하나만 낸다. **이 축은 다른 레인이 같은 range 에서 닫았다** — 하네스 커밋 `dc27007` 이 `CODEX_BIN` 을 절대 경로로, `CODEX_PIN` 을 **0.151.0** 으로 고정했다. **그래서 B6 부터는 B5 와 엔진 버전이 다르다**: 그 사실과 판단은 그 커밋이 갖는다(**이 slice 범위 밖**) |

**`C-7b` 매치의 처리 — 이 레인의 `fixtures/manifest.yaml` 접촉 둘을 선언된 예외로 둔다.**

**이 자리가 `C-7a` 의 경로 집합과 `C-7b` 매치의 정본이다** — `checklist.md`·`scope.md` 는 여기를
가리키고 같은 사실을 따로 적지 않는다. 수는 어느 쪽에도 적지 않는다(`C-7a`·`C-7b` 가 낸다).

**이 레인이 `fixtures/manifest.yaml` 을 건드린 자리는 둘이고 성격이 다르다** — **① 인용 좌표
정정**(`8701882`) · **② B5-high 수정**(`16d7a48`). **둘은 검출 방식도 다르다.**

```sh
# ① — 두 레인 pathspec 에 동시에 걸리는 혼합 커밋. C-7b 가 낸다
comm -12 <(git log --format=%h 14686db..HEAD -- "${LANE[@]}" | sort) \
         <(git log --format=%h 14686db..HEAD -- fixtures/manifest.yaml | sort)
# ② — `fixtures/manifest.yaml` **만** 건드린 커밋이라 `C-0` 의 pathspec 밖이고 위 교집합에
#     들지 않는다. 파일 목록으로 그 사실을 낸다
git show --stat --format='%h %s' 16d7a48
```

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
- **①의 범위는 `8701882` 하나다.** `C-7b` 의 나머지 판정은 그대로 선다. **다음 커밋에 같은
  형태가 또 나오면 새 판정 대상**이며 이 항목이 그것을 덮지 않는다.

**② B5-high 수정 — 다른 사유의 예외다.**

- **무엇이 잡히는가 — 아무것도 잡히지 않는다.** **B5 `findings[0]`(high)** 가 지목한 파일이
  `fixtures/manifest.yaml` 이고 그 수정 커밋이 이 레인에서 났으나, **그 커밋은 그 파일 하나만
  건드려 `C-0` 의 pathspec 밖에 있다.** `C-7b` 의 매치가 하나인 것은 ①만 세었기 때문이고
  **②는 그 검사가 구조적으로 보지 못한다** — 검출은 위 블록의 `git show --stat` 이 한다.
- **왜 이 레인이 고치는가.** finding 이 든 축은 **B4-high 가 세운 `verified_paths` 계약**이고 그
  계약을 세운 것도 이 레인이다. ①이 두 레인 파일에 걸친 인용 좌표 정정이라면 ②는 **이 레인이
  만든 계약 위의 정정**이다. 처리는 **운영자 결정 2026-09-01**(「projection 으로 좁힘」)이 정했다.
- **범위는 그 finding 이 든 자리에 그친다** — 세 case 의 `verified_paths`, 그 계약을 정의하는
  `schema.extensions`, 계약의 현재 형태를 서술하던 자리들(분류 정책·`uncovered_axes`·`next_steps`).
  **기대값·입력 파일과 해시는 무접촉**이고 `fixtures/` 의 다른 파일도 무접촉이다.
- **fixture-curator 레인의 파일을 고치지 않았다** — 신설 필드 `verified_projections` 는 그 레인의
  `F-7b` 가 읽는 필드의 **모양을 바꾸지 않고 형제로 선다**. 재실행 결과는 `C-11` 이 낸다.

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

**`C-11` 의 python3 블록.** venv 는 `fixtures-commands.md` **F-7** 과 같다. **`F-7b` 는 여기 옮겨
적지 않는다** — 그 레인 문면 그대로 돌렸고 정본은 그 파일이다. 아래는 이 라운드가 더한 것뿐이다.

```sh
/tmp/fxvenv/bin/python - <<'PY'
import yaml, json
m = yaml.safe_load(open('fixtures/manifest.yaml'))
A = {'Measured', 'SampleInsufficient', 'Undeclared', 'UNKNOWN'}   # 4문 4답의 가족 A
REQ = {'path', 'projection', 'operand', 'operand_source', 'means'}
bad = []
for c in m['cases']:
    vp, vj = c.get('verified_paths') or [], c.get('verified_projections') or []
    exp = json.load(open(c['expected_file']))
    def at(p):                       # `[i]` 첨자는 풀지 않는다 — 그 경로는 None 이 되어 건너뛴다
        cur = exp
        for k in p[2:].split('.'):
            if not isinstance(cur, dict) or k not in cur: return None
            cur = cur[k]
        return cur
    bad += [(c['id'], '가족 A 잠금: ' + p) for p in vp if str(at(p)) in A]
    for e in vj:
        if (set(e) != REQ or e['projection'] != 'not-equals' or e['operand'] in A
                or e['path'] in vp or at(e['path']) == e['operand']):
            bad.append((c['id'], 'projection: ' + str(e.get('path'))))
    if c['classification'] != 'authoritative' and (vp or vj):
        bad.append((c['id'], 'ie 인데 계약 필드가 있다'))
    bad += [(c['id'], 'change_history 필수 키') for h in c.get('change_history') or []
            if not {'date', 'change', 'reason', 'policy_version'} <= set(h)]
print('vj', sum(1 for c in m['cases'] if 'verified_projections' in c), '| violations', bad)
PY
```

**`C-12` 의 python3 블록.** 0E 가 닫은 `OPEN` 셋에 대해 **현재형 활성/대기 주장**을 남긴 줄을
찾는다. `capability-map.md` **§12.1(복원·해소 이력 절)** 과 **취소선(`~~…~~`) 안**은 이력 표시라 뺀다.

```sh
python3 - <<'PY'
import re, io, glob
resolved = ['OPEN-ADR-01', 'OPEN-OPS-07', 'OPEN-QUAL-05']
claim = re.compile(r'활성이다|활성이며|활성 실행 게이트|기입 대기|대기\*\*|아직 없다|미충족|미완이다')
docs = ['docs/discovery/capability-map.md', 'docs/discovery/data-dictionary.md',
        'docs/discovery/regression-ledger.md', 'milestone-0.md', 'milestone-1.md',
        'v2-지침서.md'] + sorted(glob.glob('docs/adr/*.md'))
hit = 0
for f in docs:
    lines = io.open(f, encoding='utf-8').read().splitlines()
    skip = set()
    if 'capability-map' in f:
        s = next(i for i, l in enumerate(lines) if l.startswith('### 12.1'))
        e = next(i for i, l in enumerate(lines) if l.startswith('### 12.2'))
        skip = set(range(s, e))
    for i, l in enumerate(lines):
        if i in skip: continue
        bare = re.sub(r'~~.*?~~', '', l)
        for r in resolved:
            if r in bare and claim.search(bare):
                hit += 1; print('%s:%d  %s' % (f, i + 1, r))
print('matches', hit)
PY
```

**`C-13` 의 preflight 명령 셋.** 절차의 정본은 `.claude/skills/codex-review-gate/SKILL.md` 이고
여기 있는 것은 **이 라운드의 실행 기록**이다 — 리뷰 레인은 verdict JSON 만 쓰므로 구현 레인이 갖는다.
②는 매치와 총 행 수를 **같은 핸들로** 받는다(총 행이 0이면 못 연 것이고 preflight 미충족이다).
③은 **실행된 버전이 아니라 지금 PATH 가 고르는 버전**을 낸다 — 실행된 버전의 정본은 raw-output 머리글이다.

```sh
grep -rniE 'bid-vector-v2|regression-ledger|capability-map|OPEN-REG|0a2|0b-regression' \
  ~/.codex/memories/memory_summary.md ~/.codex/memories/MEMORY.md \
  ~/.codex/memories/rollout_summaries/ ~/.codex/memories/skills/ \
  ~/.codex/rules/default.rules 2>/dev/null | wc -l          # ① sessions/ 는 훑지 않는다
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" \
  "select count(*) from stage1_outputs
    where raw_memory like '%bid-vector-v2-review%'
       or rollout_summary like '%bid-vector-v2-review%';"    # ②
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" "select count(*) from stage1_outputs;"
type -a codex && codex --version                             # ③ 어느 바이너리인지까지 낸다
head -1 _workspace/m0-0e/codex.raw-output-B5.txt             # ③ 실행된 버전의 정본
```
