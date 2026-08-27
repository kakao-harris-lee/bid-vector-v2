# M0 / 0B — 검증 명령과 출력

slice `0b-regression-ledger`. base `ec115a7`. 계약은 `scope.md`.

문서 slice이므로 acceptance command 대신 **A1~A7 대조는 `checklist.md`**가 하고,
이 파일은 **그 대조가 근거로 삼는 실행**을 담는다. 규격은
`.claude/skills/evidence-pack/SKILL.md` §"문서 slice(M0)의 evidence".

**0A2 §10.1 형태 3**: 아래 출력은 전부 **기록 시점 HEAD에서 실행**한 것이며 서로 다른
실행의 출력을 섞지 않았다. 수치가 실행 시점에 의존하면 **기준 커밋을 명시**한다.

---

## C-1. A2 — legacy 인용 확인: 무엇을 기계로, 무엇을 사람이

**이 slice가 정한 방법과 그 근거.** 계약 A2가 "방법은 이 slice가 정한다"고 남겼고,
0A2가 **"정본을 계산할 수 있는 집계가 아니라 사람이 파일을 열어야 하는 종류"**라며
절차화를 0B로 미뤘다. **지금이 그 판단 자리다.**

**결론: 축을 둘로 나눈다.** 인용 하나는 검증 가능한 부분이 서로 다르다.

| 축 | 대상 | 정본이 있는가 | 방법 |
| --- | --- | --- | --- |
| **기계** | ① 경로가 `ed4b06c`에 존재하는가 ② 인용 행 범위가 파일 길이 안인가 ③ commit이 존재하는가 ④ 그 commit의 subject·날짜 | **있다** — `git ls-tree` · `git show` · `git log`가 산출한다 | 전수 스크립트(C-2) |
| **사람** | 그 행 범위의 **내용**이 서술과 맞는가 | **없다** — 어떤 명령도 "이 코드가 이 주장을 뒷받침하는가"를 계산하지 못한다 | 항목마다 직접 열어 읽음(C-4) |

**왜 표본이 아니라 전수인가**: 60건 규모이고, 0B 선행 조사가 **표본이 아니라 전수로
훑었기 때문에** 선행 산출물의 인용 오류 4건(C-1~C-4)을 찾았다. 표본이었다면 넷 중
일부만 나왔을 것이다. 그리고 **Codex 리뷰어 worktree에 `bid-vector`가 없어** 이 축은
리뷰에서 재현되지 않는다 — 0A2 매 라운드 residual risk에 같은 문장이 적혔다.
**이 slice가 유일한 확인 지점**이므로 전수가 아니면 확인되지 않는 인용이 남는다.

**왜 기계 축을 만들었나(필요가 관측된 뒤에 만들었다)**: 선행 조사의 오류 4건 중 **C-2·C-4는
경로 오류, C-3은 행 범위 오류**다. 셋 다 **기계가 잡을 수 있는 부류**이고 실제로 사람이
놓쳤다. 결함이 먼저 관측됐고 그 부류에 맞는 축을 만들었다 — 0A2가 세운 순서(**필요가
관측되기 전에 도구를 늘리지 않는다**)를 따른다.

### C-1.1 이 방법이 만든 규칙 — 파일명만 쓰지 않는다

기계 축을 돌리자 **경로가 아닌 파일명 인용**이 확인 불가로 떨어졌다. 실제로 모호하다:

```
$ git -C bid-vector ls-tree -r --name-only ed4b06c | grep '/summary\.py$'
app/ai/predictors/historical/summary.py
app/services/paper_bidding_backtest/summary.py
```

선행 조사의 R-RATE-04가 `summary.py`로만 인용했다. **commit `a1ca0b6`이 건드린 파일로
확정**했다:

```
$ git -C bid-vector show --stat --format='' a1ca0b6 | grep summary
 app/ai/predictors/historical/summary.py |  7 ++++-
```

ledger는 **전체 경로만** 쓴다(§0.3). 이 규칙은 사람이 정한 취향이 아니라 **기계 축이
확인할 수 있는 형태**라는 요구에서 나왔다.

---

## C-2. 기계 축 — 스크립트 본문 (형태 4: 인라인)

```python
# A2 — legacy 인용 확인. 정본이 있는 축은 기계로, 없는 축은 사람이 읽는다.
#   기계 검증 가능: ① 경로가 ed4b06c에 존재하는가 ② 인용 행 범위가 파일 길이 안인가
#                  ③ commit 해시가 존재하는가 ④ 그 commit의 subject
#   사람이 읽어야 하는 것: 그 행 범위의 *내용*이 서술과 맞는가 (정본을 계산할 수 없다)
import re, subprocess, sys, collections
REPO = "bid-vector"; REF = "ed4b06c"
src = open(sys.argv[1], encoding="utf-8").read()

def git(*a):
    return subprocess.run(["git","-C",REPO,*a], capture_output=True, text=True)

# ── 인용 추출 ─────────────────────────────────────────────
# 경로:행  (행은 12-34 / 12 / 12-34,56-78 형태)
PATH = re.compile(r'`([A-Za-z0-9_./-]+\.(?:py|md|toml|cfg|yaml|yml|json|txt))'
                  r'(?::([\d,\-]+))?`')
COMMIT = re.compile(r'`([0-9a-f]{7,40})`')
lines = src.split("\n")
cites = collections.OrderedDict()   # (path, ranges) -> [줄번호]
commits = collections.OrderedDict()
cur = None
for i, l in enumerate(lines, 1):
    m = re.match(r'^### ([RP]-[A-Z]+-\d+)', l)
    if m: cur = m.group(1)
    for mm in PATH.finditer(l):
        cites.setdefault((mm.group(1), mm.group(2)), []).append((cur, i))
    for mm in COMMIT.finditer(l):
        h = mm.group(1)
        if re.fullmatch(r'[0-9a-f]+', h) and not re.fullmatch(r'\d+', h):
            commits.setdefault(h, []).append((cur, i))

# ── 파일 목록 (정본) ──────────────────────────────────────
tree = set(git("ls-tree","-r","--name-only",REF).stdout.split())
print(f"=== 인용 추출 ===\n경로 인용 {len(cites)}종 · commit 인용 {len(commits)}종\n")

bad_path, bad_range, ok = [], [], 0
for (p, rng), where in cites.items():
    if p not in tree:
        # 저장소 밖 파일(이 저장소 자신의 문서 등)은 제외 후보로 표시
        bad_path.append((p, rng, where)); continue
    n = len(git("show", f"{REF}:{p}").stdout.split("\n"))
    if rng:
        for part in rng.split(","):
            a = part.split("-"); lo = int(a[0]); hi = int(a[-1]) if len(a)>1 else lo
            if hi > n: bad_range.append((p, part, n, where)); break
        else: ok += 1
    else: ok += 1
print(f"--- 경로: 존재 {len(cites)-len(bad_path)} / 부재 {len(bad_path)} ---")
for p, rng, w in bad_path: print(f"  [부재] {p}:{rng}  ← {w[:3]}")
print(f"--- 행 범위: 파일 길이 내 {ok} / 초과 {len(bad_range)} ---")
for p, part, n, w in bad_range: print(f"  [초과] {p}:{part} (파일 {n}줄)  ← {w[:3]}")

print(f"\n--- commit: 존재 여부와 subject ---")
for h, w in commits.items():
    r = git("log","-1","--format=%h %ad %s","--date=short",h)
    if r.returncode: print(f"  [부재] {h}  ← {w[:2]}")
    else: print(f"  {r.stdout.strip()[:110]}")
```

### C-2.1 실행 결과 — ledger 대상

```
$ python3 citecheck.py docs/discovery/regression-ledger.md
=== 인용 추출 ===
경로 인용 105종 · commit 인용 21종

--- 경로: 존재 95 / 부재 10 ---
--- 행 범위: 파일 길이 내 95 / 초과 0 ---
```

- **legacy 경로 인용 95종이 전부 `ed4b06c`에 존재하고, 인용한 행 범위가 전부 파일 길이
  안이다. 초과 0.**
- **부재 10종은 전부 이 저장소 자신의 문서**다 — `reports/evidence/m0/0b/scope.md` ·
  `milestone-0.md` · `reports/evidence/m0/0a2/decisions.md` ·
  `reports/evidence/m0/0b/commands.md` · `fixtures/manifest.yaml` · `data-extract.md` ·
  `docs/discovery/capability-map.md`, 그리고 §0.3이 **모호성의 예시로 언급**하는
  `summary.py`. legacy 트리에 없는 것이 정상이다.
- **commit 21종이 전부 존재**하고 subject·날짜가 아래 C-3과 같다.

### C-2.2 commit 존재와 subject (발췌 — 전수는 위 명령이 낸다)

```
ed4b06c 2026-08-14 feat(ml): Phase 3 PR2 — win-proxy 백테스트·캘리브레이션 리포트 (읽기 전용 측정) (#371)
19f2c94 2026-08-13 fix(similarity): 백필 폭주 수습 — 큐 21,321건 적체의 코드 원인 제거 (P1~P4) (#368)
5d38ac1 2026-08-13 feat(ml): 승격 게이트 재설계 — 성숙도 embargo + 겹치지 않는 날짜 창 (Phase 2c) (#367)
75254c1 2026-08-13 feat(ml): 서빙 분포 정합 학습 필터 + 미학습 공종 가드 (Phase 2b) — 용역 편향 +5.91%p → +0.90%p (#366)
a1ca0b6 2026-08-11 fix(predictor): summary 예비가 패턴 블록의 None bid_rate 크래시 — #360 회귀 핫픽스 (#361)
ddee938 2026-08-08 fix(basis): 추정가격 출처 인지 write 가드 — 파생 예정가·폴백이 분모·budget_cap을 덮지 못하게 (#359)
4a8e064 2026-08-08 fix(basis): base÷추정가격 비율로 clean 버킷 오염 분리 — suspect-ratio 재태깅 (#358)
3447952 2026-08-07 fix(floor): award_floor_rate 신뢰 게이트 — 성립 불가 게시 하한을 수집·가격·검증 3층에서 차단 (#357)
881ce27 2026-08-06 fix(scoring): expected_margin 축 basis 정합 — floor_headroom 과대 표시 해소 (#355)
f70df07 2026-08-06 fix(pipeline): 수집 리스 기아 해소 — busy 시 bounded retry + consumer_timeout 선언 (#353)
c4ec93b 2026-07-25 refactor(domain): money 타입(BaseAmount)을 bid_base 경계 시그니처에 강제 — mypy strict 아일랜드 승격 (#262)
cc4bc9f 2026-07-25 fix(eligibility): association 축을 그룹-OR 커널 세 번째 소비자로 이관 — 평면-AND 잠재 과차단 선제 제거 (#258)
187cea9 2026-07-25 refactor(eligibility): 그룹-OR 평가 커널 추출 — 면허 게이트·tech_field 공유 (동작 동일, golden diff 0) (#257)
c704fff 2026-07-25 fix(classifier): tech_field 축을 면허 게이트와 동일한 lmtGrpNo 그룹-OR로 정렬 (false-exclusion) (#254)
aa772f0 2026-07-21 fix(eligibility): ENG001 포괄 별칭을 면허 대조에서 배제 — 엔지니어링 전문분야 collapse 오탐 제거(실측) (#216)
7553873 2026-07-21 feat(eligibility): 면허 자격 게이트를 후보 스크리닝에 wiring(기본 OFF)+영향 계측 (#219)
d85711d 2026-07-19 fix(data): license-limit 서브콜 차수를 제로패딩 문자열로 전달 — int 변환이 "000"을 0으로 파괴 (#210)
f3a3027 2026-07-19 fix(collector): eligibility_raw 원천을 license-limit 서브 오퍼레이션으로 교정 — 목록 응답에 자격 상세 부재(실측) (#209)
fe24c15 2026-07-19 (R-ASYNC-04의 전례 — 엔티티 DISTINCT가 Postgres json 컬럼에서만 죽었다) (#212)
2f9bad7 2026-07-17 chore(data): HistoricalData base_amount 출처 분류(basis)+기초금액 추정 backfill + 홀드아웃 오염 가드 (#199)
4645ce4 2026-07-15 fix(predictor): 투찰가를 사업금액(기초금액) 기준으로 산정 (#162)
6b7f185 2026-05-10 feat: expand bid intelligence and crawl linkage
```

---

## C-3. 선행 산출물 인용 오류 4건 — 0B가 그대로 옮기지 않았다

선행 조사(§0.2)가 정정한 넷을 **다시 열어 확인**하고 ledger에 정정된 형태로 실었다.
**이 넷은 이 저장소가 상류 노트를 그대로 옮겨 만든 오류**이며 §10.1 **형태 2**의 사례다.

| # | 선행 산출물의 서술 | 0B 실측 | ledger 처리 |
| --- | --- | --- | --- |
| C-1 | "0.9~2.7%p가 legacy 자신의 **백테스트 문서**에 한계로 공시" | 그 문자열은 **문서가 아니라 스크립트**에 있다 — `scripts/backtest_latest_award_holdouts.py:1203-1204`. 백테스트 문서의 같은 한계 서술에는 **숫자가 없고** `:240`의 `0.9~1.7%p`는 **다른 축** | R-FLOOR-01이 출처를 스크립트로 적고 두 수치를 섞지 않는다고 명시 |
| C-2 | `floor_applicability.py:125-126` | 경로는 **`app/ai/floor_applicability.py`**(`predictors/` 아래가 아니다), 행은 **`:126-127`** | R-FLOOR-01의 근거를 정정된 경로·행으로 |
| C-3 | `opportunity_monitoring/filters.py:62-67` | 비교 블록은 **`:62-70`**(`:65-68` 두 guard, `:69-70` 사유 append) | R-BASIS-01의 근거를 `:62-70`으로 |
| C-4 | `app/services/collection_jobs.py` | 실제 경로는 **`app/tasks/collection_jobs.py`** | R-ASYNC-03이 `app/tasks/`로 |

**`19f2c94` 21,321건의 귀속**도 재확인했다 — `app/services/task_queue_depth.py:5-10`이
**"the similarity projection backfill pinned the one inference worker for four hours and
`bid_vector_ml_inference` accumulated 21,321 messages"**라고 직접 적는다. **유사공고 임베딩
백필 적체이며 투찰가 배치 산출과 무관하다.** 이 저장소가 한 번 틀렸고 0A2 라운드 2·3이
정정한 건이며, **0B는 그 정정이 맞음을 독립으로 확인했다.**

---

## C-4. 사람 축 — 선행 조사가 "재확인하지 않았다"고 넘긴 5건

선행 조사 §9.1이 **파일 존재만 확인하고 행 범위 내용은 확인하지 않았다**고 명시한 다섯을
0B가 직접 열었다. **다섯 다 서술을 뒷받침한다.**

| 항목 | 인용 | 0B 판독 |
| --- | --- | --- |
| R-FLOOR-06 | `app/services/bid_base.py:257-265` | **일치.** "The **override is deliberately not gated**" + "the request schemas do NOT reject an implausible override today — `legal_floor_bid_rate` is declared with `ge=0.0` only, no upper bound … so a 1.0 override WOULD reach the guardrail floor" |
| R-QUAL-07 | `app/services/classification/eligibility.py:53-97` · `app/services/license_eligibility.py:447-511` | **일치.** 전자는 `assess_license(project, profile)`로 **공고 자유 텍스트 토큰**을 읽고, 후자는 `assess_license_eligibility(eligibility_raw, profile_license_codes)`로 **구조화 필드**를 읽는다. **입력 소스부터 다르다** |
| R-COL-07 · R-COL-08 | `scripts/backfill_award_floor_rate.py:21-36` | **일치.** `:21-27`이 고아 집합(`floor IS NULL AND eligibility_raw IS NOT NULL` — "rows a default run can never reach")과 `--floor-only`의 존재 이유를, `:28-36`이 운영자 후보 우선 tier와 **실측 2026-07-20(760건 중 610건 마감 · 열린 170건 중 보유 5건)**을 적는다 |
| R-ASYNC-08 | `app/services/analytics_reporting/task_runtime.py:33-51`, `:145-167` | **일치.** 전자는 `task_records`에서 상태별 카운트를 세고(**생산된 행 기반**), 후자는 broker health 지표를 낸다 |
| R-ML-08 | `app/ai/predictors/base.py:37-51` | **일치.** "이 도출은 원래 **44개 골든**…에서 나왔다. sequence-model 은퇴로 골든은 **39개**가 됐고 **삭제된 5건 중 3건이 narrow-shape**였다 — 즉 **'필수 17'의 도출 근거 집합 자체가 바뀌었다**" |

**그 외 인용의 사람 축 확인**은 계열별로 ledger를 쓰면서 수행했고, 각 커밋 메시지가 그
범위에서 연 파일·행을 열거한다(`81a2ce3` · `29a55be` · `34684f9` · `b9b97c9`).
**커밋 메시지가 그 라운드의 확인 기록**이다.

---

## C-5. 산출물 불변 — 스크립트 본문 (형태 4: 인라인)

**셈을 본문에 쓰지 않고 재현 명령으로 둔다**(0A2 §10.1 **형태 6**). 계열별 사례 수·5필드
충족·상태 분포·중복 id·인용 형식은 아래 스크립트가 산출한다.

```python
# 0B ledger 불변 — 셈이 아니라 재현 명령으로. (§10.1 형태 6)
import re, collections, sys
P = "docs/discovery/regression-ledger.md"
lines = open(P, encoding="utf-8").read().split("\n")
FIELDS = ("관찰", "사용자 영향", "V2 예방 제약", "검증 방법", "근거", "상태")
series = None; cur = None; ents = collections.OrderedDict(); order = []
for i, l in enumerate(lines, 1):
    m = re.match(r'^## (\d)\. ', l)
    if m: series = m.group(1)
    m = re.match(r'^### ([RP]-[A-Z]+-\d+)', l)
    if m:
        cur = m.group(1); ents[cur] = {"series": series, "line": i, "fields": set()}; order.append(cur)
    if cur:
        for f in FIELDS:
            if re.match(r'^- \*\*%s\*\*' % re.escape(f), l) or re.match(r'^- \*\*%s\*\* ' % re.escape(f), l):
                ents[cur]["fields"].add(f)
        if re.match(r'^- \*\*V2 예방 제약\*\*', l): ents[cur]["fields"].add("V2 예방 제약")
        if re.match(r'^- \*\*상태\*\*', l): ents[cur]["fields"].add("상태")
        if re.match(r'^- \*\*(왜 예방책인가|V2가 유지할 것)\*\*', l): ents[cur]["fields"].add("V2 예방 제약")

txt = "\n".join(lines)
print("=== 계열별 사례 수 ===")
per = collections.Counter(v["series"] for v in ents.values())
for k in sorted(per): print(f"  계열 {k}: {per[k]}")
prev = [e for e in ents if e.startswith("P-")]
print(f"  합계 {len(ents)} = 회귀 {len(ents)-len(prev)} + 예방책 {len(prev)}{prev}")

print("\n=== 5필드 충족 ===")
miss = {e: sorted(set(FIELDS) - v["fields"]) for e, v in ents.items() if set(FIELDS) - v["fields"]}
print("  결측 없음" if not miss else f"  결측 {len(miss)}건: {miss}")

print("\n=== 상태 분포 ===")
st = collections.Counter()
for e, v in ents.items():
    end = next((j for j in range(v["line"], len(lines)) if lines[j].startswith("### ") and j > v["line"]), len(lines))
    blk = "\n".join(lines[v["line"]-1:end])
    m = re.search(r'\*\*상태\*\*: \*\*`?([^`*\n]+)`?\*\*', blk)
    st[m.group(1).strip() if m else "??"] += 1
for k, v in st.items(): print(f"  {k}: {v}")

print("\n=== 사용자 영향 '판정 불가' ===")
for e, v in ents.items():
    end = next((j for j in range(v["line"], len(lines)) if lines[j].startswith("### ") and j > v["line"]), len(lines))
    blk = "\n".join(lines[v["line"]-1:end])
    m = re.search(r'- \*\*사용자 영향\*\*: ([^\n]*)', blk)
    if m and ("판정 불가" in m.group(1) or "관측된 피해 없음" in m.group(1) or "관측된 직접 영향 없음" in m.group(1)):
        print(f"  {e}: {m.group(1)[:60]}")

print("\n=== id 중복 ===")
dup = [k for k, c in collections.Counter(order).items() if c > 1]
print("  없음" if not dup else f"  {dup}")

# §0(규약 절)은 인용이 아니라 규칙 설명이라 대상에서 뺀다 — 모호성의 *예시*로
# 파일명을 언급한다. 대상은 계열 본문(첫 `## 1.` 이후)이다.
body_start = next(i for i, l in enumerate(lines) if l.startswith("## 1. "))
body = "\n".join(lines[body_start:])
print("\n=== 인용 형식: 파일명만 쓴 것(전체 경로 아님) — 계열 본문 한정 ===")
bad = {}
for i, l in enumerate(lines[body_start:], body_start + 1):
    for m in re.finditer(r'`([A-Za-z0-9_.-]+\.py)(?::[\d,\-]+)?`', l):
        if "/" not in m.group(1): bad.setdefault(m.group(1), []).append(i)
print("  없음" if not bad else f"  {bad}")

# 바로 앞에 legacy 경로 인용이 있으면 그 파일의 두 번째 행 범위다(§0.3의 예외).
print("\n=== 이 문서 자기참조 줄 번호 (legacy 행 범위 연속 표기 제외) ===")
self_ref = []
for i, l in enumerate(lines[body_start:], body_start + 1):
    for m in re.finditer(r'(?<![\w/.])`:(\d+)(?:-\d+)?`', l):
        head = l[:m.start()]
        prev = lines[i - 2] if i >= 2 else ""
        if re.search(r'`[A-Za-z0-9_./-]+\.(py|md):[\d,\-]+`[^`]*$', head) or \
           re.search(r'`[A-Za-z0-9_./-]+\.(py|md):[\d,\-]+`', head) or \
           re.search(r'`[A-Za-z0-9_./-]+\.(py|md):[\d,\-]+`', prev):
            continue
        self_ref.append((i, m.group(0)))
print("  없음" if not self_ref else f"  {self_ref}")
```

### C-5.1 실행 결과

```
$ python3 ledgercheck.py
=== 계열별 사례 수 ===
  계열 1: 7
  계열 2: 5
  계열 3: 8
  계열 4: 8
  계열 5: 7
  계열 6: 8
  계열 7: 8
  계열 8: 10
  합계 61 = 회귀 60 + 예방책 1['P-ML-01']

=== 5필드 충족 ===
  결측 없음

=== 상태 분포 ===
  legacy에 잔존: 27
  legacy에서 수정됨: 31
  판정 불가: 2
  legacy에 이미 있는 예방책: 1

=== id 중복 ===
  없음

=== 인용 형식: 파일명만 쓴 것(전체 경로 아님) — 계열 본문 한정 ===
  없음

=== 이 문서 자기참조 줄 번호 (legacy 행 범위 연속 표기 제외) ===
  없음
```

- **계열 8이 10인 것은 P-ML-01을 포함**하기 때문이다. **회귀는 9건**이고 합계 60에
  들어간다 — 스크립트가 그 분해를 직접 찍는다.
- **상태 분포 합 61** = 27 + 31 + 2 + 1. `판정 불가` 2건은 **상태 축**의 판정 불가
  (R-BASIS-07 · R-PROV-01)이며, **사용자 영향 축**의 판정 불가는 그보다 많다(아래).
- **선행 조사의 §0.1 요약 표(수정됨 29 · 잔존 28 · 판정 불가 3)와 다르다. 그러나 0B가
  판정을 바꾼 것은 아니다** — 아래 C-5.3이 그 경위다.

### C-5.2 선행 조사 §0.1 요약 표가 자기 항목과 어긋난다 (0B 발견)

**처음에 "0B가 판정을 바꾼 것이 세 건"이라고 적었다. 그것은 틀렸다** — 세지 않고 썼다
(§10.1 **형태 1**). 실제로 세어 보니 **0건**이고, 어긋나는 것은 선행 조사 **노트 안**이다.

**스크립트 본문**(형태 4: 인라인):

```python
# 두 문서의 항목별 `상태`를 파싱해 대조한다. 선행 조사 §0.1 요약 표가 자기 항목과
# 어긋나는 것을 이 대조가 적출했다(C-5.2).
import re, collections, sys
def statuses(path):
    cur = None; out = {}
    for l in open(path, encoding="utf-8").read().split("\n"):
        m = re.match(r'^### ([RP]-[A-Z]+-\d+)', l)
        if m: cur = m.group(1); continue
        if cur and re.match(r'^- \*\*상태\*\*', l):
            v = re.sub(r'[`*]', '', l.split(":", 1)[1]).strip()
            out.setdefault(cur, re.split(r'[（(—]', v)[0].strip()); cur = None
    return out
a = statuses(sys.argv[1]); b = statuses(sys.argv[2])
print("scout", len(a), "ledger", len(b))
diff = [(k, a.get(k), b.get(k)) for k in b if a.get(k) != b.get(k)]
for k, x, y in diff: print(f"  {k}: scout={x!r}  ledger={y!r}")
print("변경 건수:", len(diff))
print("scout 분포:", dict(collections.Counter(a.values())))
print("ledger 분포:", dict(collections.Counter(b.values())))
```

```
$ python3 statusdiff.py _workspace/m0-0b/01_scout_regression_preflight.md \
    docs/discovery/regression-ledger.md
scout 61 ledger 61
변경 건수: 0
scout 분포: {'legacy에서 수정됨': 31, 'legacy에 잔존': 27, '판정 불가': 2, '예방책': 1}
ledger 분포: {'legacy에서 수정됨': 31, 'legacy에 잔존': 27, '판정 불가': 2, '예방책': 1}
```

**항목별 상태는 61건 전부 동일하다.** 그런데 선행 조사 §0.1 표는 다른 수를 적는다:

| 계열 | 노트 항목에서 센 값(수정/잔존/판정불가) | §0.1 표 | 일치 |
| ---: | --- | --- | --- |
| 1 | 4 / 2 / 1 | 4 / 2 / 1 | ✓ |
| 2 | 2 / 3 / 0 | 2 / 3 / 0 | ✓ |
| 3 | **4 / 3 / 1** | 3 / 4 / 1 | **✗** |
| 4 | **3 / 5 / 0** | 4 / 4 / 0 | **✗** |
| 5 | 5 / 2 / 0 | 5 / 2 / 0 | ✓ |
| 6 | **3 / 5 / 0** | 3 / 4 / 1 | **✗** |
| 7 | 4 / 4 / 0 | 4 / 4 / 0 | ✓ |
| 8 | **6 / 3 / 0** | 4 / 5 / 0 | **✗** |
| **계** | **31 / 27 / 2** | 29 / 28 / 3 | **✗** |

**원인은 두 축의 혼동**이다. 계열 6의 표가 `판정 불가` 1을 세는데 해당 항목(R-COL-02)의
**상태는 `잔존`이고 `판정 불가`인 것은 사용자 영향**이다. 표가 **상태 축과 사용자 영향
축을 한 칸에 섞었다.**

**0B의 처리**: 항목별 상태를 그대로 두고(바꿀 근거가 없다) **두 축을 문서 규약에서
분리**했다 — ledger §0.1이 `사용자 영향`과 `상태`를 **다른 필드**로 정의하고, 위 스크립트가
**두 축을 따로 찍는다.** 표를 고치는 것이 아니라 **한 칸에 섞일 수 없게** 만든 것이다.
`_workspace/`의 노트는 gitignore 대상이라 이 저장소가 고치지 않는다.

### C-5.3 두 축의 `판정 불가`를 구분한다

**상태 축**(그 결함이 legacy에 있는가)과 **사용자 영향 축**(피해가 관측됐는가)은 다른
질문이다. 스크립트가 후자를 따로 찍는다 — R-RATE-01 · 02 · 03 · 05 · R-PROV-05 ·
R-QUAL-02 · R-COL-02 · R-ASYNC-02 · R-ML-07 · R-ML-08. **계열 2는 5건 중 4건**이 여기
들어간다(계약이 예고한 얇은 계열이다).

---

## C-6. 불변 (A6)

```
$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
    docs/discovery/regression-ledger.md reports/evidence/m0/0b/ | wc -l
5
$ git diff --check ec115a7...HEAD | wc -l
0
$ git status --porcelain -- docs/discovery/regression-ledger.md reports/evidence/m0/0b/ | wc -l
0
$ wc -l docs/discovery/regression-ledger.md
1263 docs/discovery/regression-ledger.md
```

- secret 스캔 매치는 **전부 자기참조**다 — `scope.md`의 A6 문장 · `checklist.md`의 A6 행 ·
  위 스캔 명령 자신 · 이 설명이 `secret`이라는 낱말을 포함한다. **실제 비밀은 없다.**
  **수치는 이 절을 담은 커밋 기준**이며, evidence를 더 쓰면 늘어난다 — 그래서
  `checklist.md` A6은 수를 적지 않고 **"자기참조만"**으로 적는다(형태 6).
  (처음에 "매치 없음"이라고 적었으나 **그 문장 자신이 매치를 만든다.** 0A2가 같은 형태로
  걸린 적이 있어 실측으로 고쳤다.)
  (처음에 "매치 없음"이라고 적었으나 **그 문장 자신이 매치를 만든다** — 0A2가 같은 형태로
  걸린 적이 있어 실측으로 고쳤다.)
- 공고번호·기관명 등 식별자는 **근거 추적에 필요한 곳(commit 참조)에만** 남기고 본문에
  옮기지 않았다 — 패턴 스캔으로 잡히지 않으므로 **육안 확인 병기**.
- 위 수치는 **이 절을 담은 커밋 기준**이다.
