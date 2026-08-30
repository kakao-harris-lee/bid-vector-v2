# Slice 실행 명령 — M0 / 0E

**돌린 명령과 그 출력만 적는다.** 라운드 이력·자기 서술을 두지 않는다.
아래는 저장소 루트에서 **선언 `head_sha` 트리** 위에 돌린 것이다(`scope.md` 참조).

---

## C-0 — 트리 좌표
```
$ git rev-parse HEAD
77af80c52594b5837669638cdcb336b42ec113c6

$ git log --oneline 14686db..HEAD
77af80c docs(m0-0e): evidence 패키지 — scope · commands · checklist
6f7918d docs(m0-0e): Q3 집행(산출물 목록) + M0 완료 기록
fbbd320 docs(m0-0e): OPEN-OPS-07·OPEN-QUAL-05 registry 갱신 + §14 M0 이월 목록 신설
353912b docs(m0-0e): ADR 0001~0009 「상태」 줄을 0D 의 Codex approve 에 맞춘다
1fe87eb docs(m0-0e): Q1·Q2 문서 개정 집행 — Boot 4.x 이동, 면허 유효기간 축 제거

$ git status --porcelain
 M .claude/skills/v2-slice-pipeline/SKILL.md
 M CLAUDE.md
 M docs/discovery/capability-map.md
 M reports/evidence/m0/0e/checklist.md
 M reports/evidence/m0/0e/commands.md
?? fixtures/

```

**`git status`는 이 명령을 돌린 시점의 working tree이며 세 레인이 섞인다.** 0E의 커밋
집합은 `git status`가 아니라 **C-7 마지막의 `git diff --name-only 14686db..HEAD`** 가 낸다.

- `.claude/skills/v2-slice-pipeline/SKILL.md` · `CLAUDE.md` — **0E 착수 시점(`14686db`)의
  working tree에 이미 있던 다른 레인의 수정**이다(재작업 상한 5회 성문화).
- `fixtures/` — **fixture-curator 레인이 0E와 병행해 만들고 있다.**
- 0E 자신의 미커밋 편집이 이 목록에 보일 수 있다 — 이 파일(`commands.md`)이 자기를 다시
  생성하기 때문이다. **그 편집은 이 evidence 커밋에 들어간다.**

**앞의 둘은 스테이징하지 않았고 커밋에 넣지 않았다.**

---

## C-1 — 네 정본의 활성 `OPEN` registry 전수

```
$ sed -n '/^## 12\. OPEN 결정 목록/,/^### 12\.1/p' docs/discovery/capability-map.md | grep -oE '^\| *OPEN-[A-Z]+-[0-9]+ *\|' | tr -d '| ' | sort -u | tr '\n' ' '; echo
OPEN-COL-01 OPEN-COL-03 OPEN-COL-04 OPEN-COL-05 OPEN-DEC-03 OPEN-DEC-07 OPEN-DEC-10 OPEN-ML-02 OPEN-ML-03 OPEN-ML-05 OPEN-ML-06 OPEN-NOTI-01 OPEN-NOTI-04 OPEN-NOTI-05 OPEN-NOTI-06 OPEN-NOTI-07 OPEN-NOTI-08 OPEN-NUM-01 OPEN-NUM-02 OPEN-NUM-03 OPEN-OPS-01 OPEN-OPS-02 OPEN-OPS-03 OPEN-OPS-04 OPEN-OPS-08 OPEN-OPS-09 OPEN-OPS-10 OPEN-QUAL-04 OPEN-QUAL-07 OPEN-QUAL-09 OPEN-QUAL-10 OPEN-QUAL-11 OPEN-SET-01 OPEN-SET-03 OPEN-SET-04 OPEN-SET-05 OPEN-SET-06 OPEN-SET-08 OPEN-SET-09 OPEN-SET-10 OPEN-STR-02 OPEN-STR-04 OPEN-STR-12 

$ sed -n '/^## 12\. OPEN 결정 목록/,/^### 12\.1/p' docs/discovery/capability-map.md | grep -cE '^\| *OPEN-[A-Z]+-[0-9]+ *\|'
43

$ sed -n '/^## 9\. `OPEN`/,/^## 10\./p' docs/discovery/regression-ledger.md | grep -oE 'OPEN-REG-[0-9]+' | sort -u | tr '\n' ' '; echo
OPEN-REG-01 OPEN-REG-02 OPEN-REG-03 OPEN-REG-04 OPEN-REG-05 

$ sed -n '/^## 9\. `OPEN`/,/^## 10\./p' docs/discovery/data-dictionary.md | grep -oE '^\| \*\*`OPEN-DIC-[0-9]+`\*\*' | grep -oE 'OPEN-DIC-[0-9]+' | sort -u | tr '\n' ' '; echo
OPEN-DIC-01 OPEN-DIC-02 OPEN-DIC-03 OPEN-DIC-04 OPEN-DIC-05 OPEN-DIC-06 OPEN-DIC-07 OPEN-DIC-08 OPEN-DIC-09 OPEN-DIC-10 

$ grep -hE '^### `OPEN-ADR-[0-9]+`' docs/adr/*.md | grep -oE 'OPEN-ADR-[0-9]+' | sort -u | tr '\n' ' '; echo
OPEN-ADR-01 OPEN-ADR-02 OPEN-ADR-03 OPEN-ADR-04 OPEN-ADR-05 OPEN-ADR-06 OPEN-ADR-07 OPEN-ADR-08 OPEN-ADR-09 OPEN-ADR-11 OPEN-ADR-12 OPEN-ADR-13 

$ grep -nE '^### ~~`OPEN-ADR-[0-9]+`~~' docs/adr/*.md
docs/adr/0009-ml-reuse-provenance.md:230:### ~~`OPEN-ADR-10`~~ · ML 재활용 출처의 기록 위치 — **해소**

```

## C-2 — 그룹별 셈이 §12 머리말과 맞는가

```
$ python3 - <<'PY'
import re, io
s = io.open('docs/discovery/capability-map.md', encoding='utf-8').read()
sec = s.split('## 12. OPEN 결정 목록')[1].split('### 12.1')[0]
cur=None; g={}
for line in sec.splitlines():
    m = re.match(r'^### (G\d)\.', line)
    if m: cur=m.group(1); g[cur]=[]
    m2 = re.match(r'^\| *(OPEN-[A-Z]+-\d+) *\|', line)
    if m2 and cur: g[cur].append(m2.group(1))
print({k: len(v) for k,v in sorted(g.items())}, 'TOTAL', sum(len(v) for v in g.values()))
PY
{'G1': 3, 'G2': 7, 'G3': 14, 'G4': 8, 'G5': 7, 'G6': 4} TOTAL 43

$ grep -n '활성 43건' docs/discovery/capability-map.md
2912:**활성 43건** (G1 3 / G2 **7** / G3 **14** / G4 **8** / G5 7 / G6 **4**). 각 항목은 `결정 필요 사항` /
2955:**활성 43건 전부의 담당 slice는 §14가 지목한다** — `milestone-0.md`의 「사용자 명시 승인」이

$ grep -n '43건' docs/discovery/capability-map.md
417:  통과 43건(≈0.8%).
2882:- (측정일 미기재) 열린 공고 5,382건 중 watch 통과 43건 (STR-01)
2912:**활성 43건** (G1 3 / G2 **7** / G3 **14** / G4 **8** / G5 7 / G6 **4**). 각 항목은 `결정 필요 사항` /
2954:**43건**의 분포는 **G3 14 · G4 8 · G2 7 · G5 7 · G6 4 · G1 3**이다(머리말과 동일).
2955:**활성 43건 전부의 담당 slice는 §14가 지목한다** — `milestone-0.md`의 「사용자 명시 승인」이

```

## C-3 — §14 이월 목록이 활성 전수를 덮는가

```
$ python3 - <<'PY'
import re, io, subprocess
cap = io.open('docs/discovery/capability-map.md', encoding='utf-8').read()
sec12 = cap.split('## 12. OPEN 결정 목록')[1].split('### 12.1')[0]
a = set(re.findall(r'^\| *(OPEN-[A-Z]+-\d+) *\|', sec12, re.M))
reg = io.open('docs/discovery/regression-ledger.md', encoding='utf-8').read()
a |= set(re.findall(r'OPEN-REG-\d+', re.search(r'^## 9\. `OPEN`.*?^## 10\.', reg, re.M|re.S).group(0)))
dic = io.open('docs/discovery/data-dictionary.md', encoding='utf-8').read()
a |= set(re.findall(r'^\| \*\*`(OPEN-DIC-\d+)`\*\*', re.search(r'^## 9\. `OPEN`.*?^## 10\.', dic, re.M|re.S).group(0), re.M))
adr = subprocess.run(['bash','-c',"grep -hE '^### `OPEN-ADR-[0-9]+`' docs/adr/*.md"],capture_output=True,text=True).stdout
a |= set(re.findall(r'OPEN-ADR-\d+', adr))
s14 = cap.split('## 14. M0 이월 목록')[1]
A = set(re.findall(r'^\| `?(OPEN-[A-Z]+-\d+)`?', s14.split('### 14.2')[0], re.M))
B = set(re.findall(r'^\| `(OPEN-[A-Z]+-\d+)`', s14.split('### 14.2')[1].split('### 14.3')[0], re.M))
C = set(re.findall(r'^\| `(OPEN-[A-Z]+-\d+)`', s14.split('### 14.3')[1].split('### 14.4')[0], re.M))
print('활성 전수      :', len(a))
print('14.1 (A) 표    :', len(A), sorted(A))
print('14.2 (B)       :', len(B))
print('14.3 (C)       :', len(C))
print('(B) 교 (C)     :', sorted(B & C))
print('활성인데 미분류:', sorted(a - (B|C)))
print('분류인데 비활성:', sorted((B|C) - a))
PY
활성 전수      : 70
14.1 (A) 표    : 4 ['OPEN-ADR-01', 'OPEN-ADR-06', 'OPEN-ADR-08', 'OPEN-OPS-07']
14.2 (B)       : 14
14.3 (C)       : 55
(B) 교 (C)     : []
활성인데 미분류: ['OPEN-ADR-01']
분류인데 비활성: []

```

**`OPEN-ADR-01` 하나가 활성 전수에 남고 (B)∪(C)에 없다.** 결정은 났고(Q1) 남은 것은
`ADR 0001` §5 행의 기록 갱신뿐인데 그것이 이 slice의 편집 범위 밖이다 — §14.1과
§14.5-1이 그 사실과 담당(M1 1A)을 적는다.

---

## C-4 — 개정 셋의 전후 문면

```
$ git diff 14686db..HEAD -- 'v2-지침서.md'
diff --git "a/v2-\354\247\200\354\271\250\354\204\234.md" "b/v2-\354\247\200\354\271\250\354\204\234.md"
index 8b7c70d..47fc87b 100644
--- "a/v2-\354\247\200\354\271\250\354\204\234.md"
+++ "b/v2-\354\247\200\354\271\250\354\204\234.md"
@@ -195,7 +195,14 @@ ml-engine/
 - 단일 요구조건, 그룹 내 AND, 그룹 간 OR를 명시적으로 모델링한다.
 - 결과는 boolean 하나가 아니라 `Eligible`, `Ineligible(reasons)`, `Uncertain(reasons)`로
   구분한다.
-- 별칭, 포괄 코드, 유효기간, 지역·협회 조건은 versioned policy data로 관리한다.
+- 별칭, 포괄 코드, 지역·협회 조건은 versioned policy data로 관리한다.
+- **면허 유효기간은 V2가 다루지 않는다** (**운영자 결정 2026-08-28 · U-7**, 문면 집행
+  2026-08-29 · Q2). 원 문면은 유효기간을 versioned policy data 요구에 함께 넣었고,
+  그 요구와 U-7이 어긋난 채 남아 있었다(`docs/discovery/data-dictionary.md` §3.2.4 ·
+  §13.5). **유효기간 축만 뺀다 — 별칭·포괄 코드·지역·협회 조건은 그대로다.**
+- **그 대신 자격 판정 결과에 「유효기간 미검증」이 드러나야 한다.** 남는 위험은
+  **만료된 면허로 자격을 오판정할 수 있다**는 것이고, 침묵하면 그 판정이 검증된 판정과
+  구별되지 않는다(`data-dictionary.md` §3.2.4).
 
 ### 4.3 기초금액 provenance
 
@@ -226,7 +233,18 @@ ml-engine/
 
 ### Kotlin
 
-- Kotlin 2.x와 현재 지원되는 Spring Boot 3.x 조합을 M1에서 버전 고정한다.
+- Kotlin 2.x와 현재 지원되는 Spring Boot **4.x** 조합을 M1에서 버전 고정한다
+  (**운영자 결정 2026-08-29 · Q1**). 원 문면은 `3.x`였고, `ADR 0001` §5 `OPEN-ADR-01`이
+  *"조사 시점에 그 조건을 만족하는 3.x가 **없다**"*(Boot 3.5 OSS 지원 2026-06-30 종료)를
+  근거로 그 문면을 막고 있었다. 운영자가 그 판정을 받아들여 **라인을 4.x로 옮겼다** —
+  `OPEN-ADR-01`의 선택지 (b)다.
+- **`OPEN-OPS-07` 라이브러리 조사는 3.x 기준으로 수행됐다.** `ADR 0005` §3의 판정 기준
+  문장이 *"M1에서 고정할 Kotlin 2.x + Spring Boot 3.x 조합과의 호환"*이고 조사 노트
+  (`_workspace/m0-open-decisions/ops07-library-survey.md`)도 같은 전제 위에 있다.
+  **그러므로 나머지 라이브러리의 4.x 호환은 M1 slice 1A가 스모크 빌드로 실측 재확인한다.**
+  `ADR 0001` §5가 *"후보 라이브러리 9종 중 Boot 3.x·4.x 양쪽 아티팩트를 제공하는 것이
+  4종 … 이 선택을 미뤄도 후보 집합이 바뀌지 않는다"*고 적으므로 **후보 집합은 이 이동으로
+  바뀌지 않으나, 채택 판정이 딛은 버전 전제는 재확인 전까지 3.x 위에 있다.**
 - Gradle Kotlin DSL과 version catalog를 사용한다.
 - JUnit 5 + Kotest/AssertJ, property test, Testcontainers를 사용한다.
 - domain test에서는 mock framework를 사용하지 않고 값과 fake port를 사용한다.

```

```
$ git diff 14686db..HEAD -- milestone-1.md
diff --git a/milestone-1.md b/milestone-1.md
index a330051..ca437d3 100644
--- a/milestone-1.md
+++ b/milestone-1.md
@@ -39,7 +39,9 @@ property test로 변환 왕복, 반올림 경계, 잘못된 단위 거부를 검
 - 단일 면허 조건
 - 그룹 내 AND, 그룹 간 OR
 - `Eligible`, `Ineligible(reasons)`, `Uncertain(reasons)`
-- 별칭/포괄 코드/유효기간/지역 조건을 versioned policy data로 분리
+- 별칭/포괄 코드/지역 조건을 versioned policy data로 분리
+- **유효기간은 다루지 않는다** — `v2-지침서.md` §4.2(운영자 결정 2026-08-28 U-7, 문면
+  집행 2026-08-29 Q2). **대신 자격 판정 결과에 「유효기간 미검증」이 드러나야 한다**
 
 ### Slice 1D — Provenance와 Floor Shortfall
 

```

```
$ git diff --stat 14686db..HEAD -- milestone-0.md
 milestone-0.md | 70 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++-
 1 file changed, 69 insertions(+), 1 deletion(-)

$ git diff 14686db..HEAD -- milestone-0.md | sed -n '1,12p'
diff --git a/milestone-0.md b/milestone-0.md
index 4c3ced8..c9e2343 100644
--- a/milestone-0.md
+++ b/milestone-0.md
@@ -87,7 +87,6 @@ M5에서 "이식 시 분해"할지 allowlist 사유를 쓸지 판단할 근거
 docs/discovery/capability-map.md
 docs/discovery/regression-ledger.md
 docs/discovery/data-dictionary.md
-docs/discovery/legacy-reference-map.md
 docs/adr/0001-target-architecture.md
 docs/adr/0002-money-rate-basis.md
 docs/adr/0003-contract-transport.md

```

**`milestone-0.md`의 나머지 변경은 「M0 완료 기록」 절 신설이다** — 전문은 커밋
`6f7918d`의 `milestone-0.md`에 있고 여기 옮겨 적지 않는다(두 벌이 되면 한쪽이 낡는다).

---

## C-5 — ADR 「상태」 줄 아홉

```
$ for f in docs/adr/*.md; do printf '%-45s ' "$f"; sed -n '3p' "$f"; done
docs/adr/0001-target-architecture.md          - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0002-money-rate-basis.md             - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0003-contract-transport.md           - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0004-persistence-and-events.md       - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0005-domain-events-and-outbox.md     - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0006-gradle-modules.md               - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0007-test-pyramid-and-ratchet.md     - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0008-frontend-disposition.md         - **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
docs/adr/0009-ml-reuse-provenance.md          - **상태**: **결정** — **운영자 결정 2026-08-29**로 위치가 확정됐다(§2 D-5). M0 slice 0D가

$ git diff --stat 14686db..HEAD -- docs/adr/
 docs/adr/0001-target-architecture.md      | 8 +++++++-
 docs/adr/0002-money-rate-basis.md         | 9 ++++++++-
 docs/adr/0003-contract-transport.md       | 8 +++++++-
 docs/adr/0004-persistence-and-events.md   | 8 +++++++-
 docs/adr/0005-domain-events-and-outbox.md | 8 +++++++-
 docs/adr/0006-gradle-modules.md           | 8 +++++++-
 docs/adr/0007-test-pyramid-and-ratchet.md | 8 +++++++-
 docs/adr/0008-frontend-disposition.md     | 8 +++++++-
 docs/adr/0009-ml-reuse-provenance.md      | 8 ++++++--
 9 files changed, 63 insertions(+), 10 deletions(-)

$ git diff -U0 14686db..HEAD -- docs/adr/ | grep -E '^@@'
@@ -3 +3,7 @@
@@ -3 +3,8 @@
@@ -3 +3,7 @@
@@ -3 +3,7 @@
@@ -3 +3,7 @@
@@ -3 +3,7 @@
@@ -3 +3,7 @@
@@ -3 +3,7 @@
@@ -3,2 +3,6 @@

```

**`@@` 헤더 아홉이 전부 3행 하나를 가리킨다** — 결정 내용·대안·§5 `OPEN` registry는
건드리지 않았다.

---

## C-6 — `OPEN-OPS-07` 종료 조건의 실재

```
$ grep -n '^## 3\.' docs/adr/0005-domain-events-and-outbox.md
236:## 3. 대안 — `OPEN-OPS-07` 후보의 판정

$ sed -n '/^## 12\. OPEN 결정 목록/,/^### 12\.1/p' docs/discovery/capability-map.md | grep -c 'OPEN-OPS-07'
7

$ grep -n 'OPEN-OPS-07' docs/discovery/capability-map.md | grep -E '12\.1|해소|재해소' | head -4
2916:**2026-08-26 운영자 결정으로 29건이 해소됐다**(66 → 37). 그중 `OPEN-OPS-07`(라운드 5) ·
2945:**M0 종료 slice 0E(2026-08-30)가 2건을 해소했다**(45 → **43**) — `OPEN-OPS-07`(G6) ·
2951:`OPEN-OPS-07`을 0E가 닫았다. **M1 차단 17건 중 15건이 해소됐고**, 남은 **M1 차단 2건**은
2986:| OPEN-DEC-07 | 기준 금액 신뢰 비율 1.15의 **마진 0.05 값** | 결정은 **(b) V2 코퍼스에서 재유도**로 확정됐으나 **값이 아직 산출되지 않았다**. 코퍼스 확보가 선행 조건이다 | **0A2 라운드 7에 활성 복원**(전수 재판정). 라운드 1이 "결정 확정, 실행 대기"로 해소 계상했으나 **종료 조건(값 산출)이 미충족**이다 — `OPEN-OPS-07`에 적용한 기준을 같은 라벨의 이 항목에도 적용한다 |

```

## C-7 — 불변 확인

```
$ git diff --stat 14686db..HEAD -- docs/discovery/data-dictionary.md docs/discovery/regression-ledger.md

$ git diff --stat 14686db..HEAD -- reports/evidence/m0/0a reports/evidence/m0/0a2 reports/evidence/m0/0a3 reports/evidence/m0/0b reports/evidence/m0/0c reports/evidence/m0/0d

$ git log --oneline 14686db..HEAD -- fixtures

$ git diff --name-only 14686db..HEAD -- fixtures

$ git diff --name-only 14686db..HEAD
docs/adr/0001-target-architecture.md
docs/adr/0002-money-rate-basis.md
docs/adr/0003-contract-transport.md
docs/adr/0004-persistence-and-events.md
docs/adr/0005-domain-events-and-outbox.md
docs/adr/0006-gradle-modules.md
docs/adr/0007-test-pyramid-and-ratchet.md
docs/adr/0008-frontend-disposition.md
docs/adr/0009-ml-reuse-provenance.md
docs/discovery/capability-map.md
milestone-0.md
milestone-1.md
reports/evidence/m0/0e/checklist.md
reports/evidence/m0/0e/commands.md
reports/evidence/m0/0e/scope.md
"v2-\354\247\200\354\271\250\354\204\234.md"

```

**네 명령이 빈 출력이다** — `data-dictionary.md`·`regression-ledger.md` 무접촉, 앞 여섯
slice의 evidence 무접촉, `fixtures/` 무접촉. **`fixtures/`는 fixture-curator 레인이 0E와
병행해 만들고 있다** — 그래서 무접촉의 근거를 `ls`(시점 의존)가 아니라 **`git log`/`git diff`의
range**(0E가 만든 커밋 집합)로 잡는다. 마지막 `git diff --name-only`의 전체 목록이 그 범위를
다시 낸다.

---

## C-8 — M0 완료 기록의 근거 (Codex verdict 전수)

```
$ for f in reports/evidence/m0/*/codex-review-*.json; do printf '%-58s ' "$f"; python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print(d['verdict'],'|',d['reviewed_base'][:8],'…',d['reviewed_head'][:8],'|',d.get('reviewer',{}).get('model','?'))" "$f"; done
reports/evidence/m0/0a/codex-review-20260822T061532Z.json  request_changes | 3dc7d263 … ff754c22 | gpt-5.6-sol (model_reasoning_effort=high)
reports/evidence/m0/0a/codex-review-20260822T065525Z.json  request_changes | 3dc7d263 … 707b683b | gpt-5.6-sol (model_reasoning_effort=high)
reports/evidence/m0/0a/codex-review-20260825T235414Z.json  approve | 3dc7d263 … 6c6b3a2a | gpt-5.6-sol (model_reasoning_effort=medium)
reports/evidence/m0/0a/codex-review-20260825T235415Z.json  request_changes | 3dc7d263 … 6c6b3a2a | gpt-5.6-sol (model_reasoning_effort=high)
reports/evidence/m0/0a2/codex-review-20260826T102642Z.json request_changes | 6af70199 … 16d690b2 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a2/codex-review-20260826T113911Z.json request_changes | 6af70199 … 01676ad4 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a2/codex-review-20260827T015140Z.json request_changes | 6af70199 … ab690129 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a2/codex-review-20260827T021632Z.json request_changes | 6af70199 … ed73b559 | gpt-5.6-sol
reports/evidence/m0/0a2/codex-review-20260827T024238Z.json request_changes | 6af70199 … 210ec5ad | gpt-5.6-sol
reports/evidence/m0/0a2/codex-review-20260827T034348Z.json approve | 6af70199 … f790e193 | gpt-5.6-sol
reports/evidence/m0/0a3/codex-review-20260827T132148Z.json request_changes | 48151b9c … cb1be885 | gpt-5.6-sol
reports/evidence/m0/0a3/codex-review-20260827T204954Z.json request_changes | 48151b9c … fbdedb6f | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a3/codex-review-20260828T005833Z.json request_changes | 48151b9c … f49155d9 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a3/codex-review-20260828T030325Z.json request_changes | 48151b9c … 6b994202 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a3/codex-review-20260828T040618Z.json request_changes | 48151b9c … c69c04c0 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a3/codex-review-20260828T043354Z.json request_changes | 48151b9c … fea90f25 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0a3/codex-review-20260828T062714Z.json approve | 48151b9c … 20f09baf | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0b/codex-review-20260827T064055Z.json  request_changes | ec115a79 … 26199744 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0b/codex-review-20260827T085200Z.json  request_changes | ec115a79 … 4f4fd7ff | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0b/codex-review-20260827T104509Z.json  request_changes | ec115a79 … 105259a8 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0b/codex-review-20260827T121954Z.json  approve | ec115a79 … 7701556c | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0c/codex-review-20260829T061613Z.json  request_changes | aff62abf … 227ba0ca | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0c/codex-review-20260829T231517Z.json  request_changes | aff62abf … 197bea39 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0c/codex-review-20260830T034122Z.json  request_changes | aff62abf … f0bdfbb8 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0c/codex-review-20260830T053622Z.json  request_changes | aff62abf … 43bbf62e | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0c/codex-review-20260830T090353Z.json  request_changes | aff62abf … cf1b6a31 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0c/codex-review-20260830T124011Z.json  approve | aff62abf … 7cbdc9bb | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0d/codex-review-20260828T145351Z.json  request_changes | 998dc217 … f1cd72b4 | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0d/codex-review-20260829T063806Z.json  request_changes | 998dc217 … 584305bb | gpt-5.6-sol (reasoning effort: high)
reports/evidence/m0/0d/codex-review-20260829T222217Z.json  approve | 998dc217 … df056259 | gpt-5.6-sol (reasoning effort: high)

```

## C-9 — 0A의 미리뷰 구간 (리뷰 range 근거)

```
$ grep -n 'head_sha' reports/evidence/m0/0a/scope.md | head -1
7:head_sha: cd5a456470c8cec35c2dafaa9ca8fac4164f4da9  # 수정 라운드 6 최종 산출물 커밋. 아래 갱신 이력 참조

$ git merge-base --is-ancestor 6c6b3a2 HEAD && echo '6c6b3a2 는 HEAD 의 조상'
6c6b3a2 는 HEAD 의 조상

$ git log --oneline 6c6b3a2..6af7019 -- docs/discovery/capability-map.md
d140f92 fix(m0-0a): NOTI-05 사용자 가치가 OPEN-NOTI-02의 at-least-once를 배제하는 서술 한정 (verifier M-3)
0581e97 fix(m0-0a): OPEN-STR-08 쟁점을 선점하는 잔존 2건 정리 — NOTI-02 경계, STR-08 사용자 가치 (verifier M-1·M-2)
23b9c2a docs(m0-0a): 전수 스윕 신규 축 F1·F2 + ML-03 동일 계열 수정 + 라운드 4 evidence
0b0c8aa fix(m0-0a): STR-08 무조건 acceptance·중복 억제 경계에서 실패·취소 run 경로 분리 (codex 3차 high)

$ git diff --stat 6c6b3a2 6af7019 -- docs/discovery/capability-map.md
 docs/discovery/capability-map.md | 45 +++++++++++++++++++++++++++-------------
 1 file changed, 31 insertions(+), 14 deletions(-)

$ python3 -c "import json;d=json.load(open('reports/evidence/m0/0a2/codex-review-20260827T034348Z.json'));print('0A2 리뷰의 base =', d['reviewed_base'])"
0A2 리뷰의 base = 6af7019996ec26903c7b531ed7ede12c22cf176e

```

**0A 라운드 4~6이 `capability-map.md`를 네 커밋에서 고쳤고**(`0b0c8aa`·`23b9c2a`·`0581e97`·
`d140f92`) 그 넷은 **`6c6b3a2` 이후**다. 0A2 리뷰의 `reviewed_base`가 그 뒤의 `6af7019`이므로
**그 넷은 어느 리뷰 range에도 들지 않았다.** 그래서 0E의 리뷰 base를 `6c6b3a2`로 잡는다.
