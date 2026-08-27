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
# `path.py:12-34` · `path.py` · `path.py::func` 세 형태를 전부 본다.
# ::func 접미를 못 보면 부분 경로 인용이 검사를 빠져나간다(verifier L3).
# 문자 클래스가 ASCII 전용이라 **한글이 든 파일명을 경로로 보지 못했다**(`v2-지침서.md`).
# 그 결과 그 인용은 **어느 축도 검사하지 않았다.** 한글을 넣어 검사 대상으로 들인다.
PATH = re.compile(r'`([A-Za-z0-9_./가-힣-]+\.(?:py|md|toml|cfg|yaml|yml|json|txt))'
                  r'(?::([\d,\-]+))?(?:::[A-Za-z_][A-Za-z0-9_]*)?`')
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
        # 십진 숫자로만 된 해시도 해시다(`0755695`·`2610826`·`3447952`·`7553873`).
        # 이전 판이 `not re.fullmatch(r'\d+', h)` 로 그 넷을 배제해 커버리지가 20/24였다.
        # 대신 **길이 7 이상의 16진수**만 받고, 실제 존재 여부는 아래 git 이 판정한다.
        if re.fullmatch(r'[0-9a-f]{7,40}', h):
            commits.setdefault(h, []).append((cur, i))

# ── 파일 목록 (정본) ──────────────────────────────────────
tree = set(git("ls-tree","-r","--name-only",REF).stdout.split())
print(f"=== 인용 추출 ===\n경로 인용 {len(cites)}종 · commit 인용 {len(commits)}종\n")

# 이 저장소(v2)의 문서도 인용된다(`v2-지침서.md`). legacy 트리에 없는 것이 정상이므로
# **commit 축과 같은 방식으로 갈라** v2 파일로 검사한다 — 빠뜨리면 아무도 안 본다.
def v2_body(p):
    r = subprocess.run(["git","show",f"HEAD:{p}"], capture_output=True, text=True)
    return None if r.returncode else r.stdout.splitlines()

bad_path, bad_range, ok = [], [], 0
v2_ok, v2_bad = [], []
for (p, rng), where in cites.items():
    if p not in tree:
        b = v2_body(p)
        if b is None:
            bad_path.append((p, rng, where)); continue
        n = len(b)
        bad = False
        for part in (rng or "").split(",") if rng else []:
            a = part.split("-"); hi = int(a[-1])
            if hi > n: bad = True
        (v2_bad if bad else v2_ok).append((p, rng, n, where))
        continue
    # `split("\n")` 은 trailing newline 때문에 실제 줄 수보다 **1 크다**
    # (`"one\n".split("\n")` → 2). 그만큼 이 축이 마지막 줄 +1 까지 통과시켰다.
    n = len(git("show", f"{REF}:{p}").stdout.splitlines())
    if rng:
        for part in rng.split(","):
            a = part.split("-"); lo = int(a[0]); hi = int(a[-1]) if len(a)>1 else lo
            if hi > n: bad_range.append((p, part, n, where)); break
        else: ok += 1
    else: ok += 1
print(f"--- v2 저장소 파일(legacy 검사 대상 아님): {len(v2_ok)+len(v2_bad)}종 · 행 범위 초과 {len(v2_bad)} ---")
for p, rng, n, w in v2_ok + v2_bad:
    # 행 범위가 없는 인용에는 **파일 길이를 찍지 않는다.** 이 evidence 자신이 그 목록에
    # 들어 있어, 길이를 찍으면 출력이 자기 길이를 바꿔 **고정점에 닿지 못한다.**
    tag = "초과" if (p, rng, n, w) in v2_bad else "ok"
    print(f"  [{tag}] {p}:{rng} (파일 {n}줄)  ← {w[:2]}" if rng else f"  [{tag}] {p}  ← {w[:2]}")
print(f"--- 경로: 존재 {len(cites)-len(bad_path)-len(v2_ok)-len(v2_bad)} / 부재 {len(bad_path)} ---")
for p, rng, w in bad_path: print(f"  [부재] {p}:{rng}  ← {w[:3]}")
print(f"--- 행 범위: 파일 길이 내 {ok} / 초과 {len(bad_range)} ---")
for p, part, n, w in bad_range: print(f"  [초과] {p}:{part} (파일 {n}줄)  ← {w[:3]}")

# 이 저장소(v2)의 SHA는 legacy 트리에 없는 것이 정상이다 — legacy 검사와 분리한다.
def in_v2(h):
    return subprocess.run(["git","cat-file","-e",h+"^{commit}"],
                          capture_output=True).returncode == 0
v2, leg = [], []
for h, w in commits.items():
    (v2 if in_v2(h) else leg).append((h, w))
print(f"\n--- commit: legacy {len(leg)}종 / 이 저장소(v2) {len(v2)}종 ---")
if v2: print("  [v2 — legacy 검사 대상 아님] " + ", ".join(h for h, _ in v2))
missing = 0
for h, w in leg:
    r = git("log","-1","--format=%h %ad %s","--date=short",h)
    if r.returncode: missing += 1; print(f"  [부재] {h}  ← {w[:2]}")
    else: print(f"  {r.stdout.strip()[:110]}")
print(f"  → legacy commit {len(leg)}종 중 존재 {len(leg)-missing} · 부재 {missing}")
```

### C-2.1 실행 결과 — ledger 대상 (전문)

**아래는 잘라내지 않은 전체 출력이며 HEAD에서 재실행한 것이다.**

> **정정 (verifier H-N1)** — 이전 판은 같은 선언을 달고 **`f24bf6d` 시점 ledger의
> 출력**을 실었다. **같은 커밋(`4db8830`) 안에서 L3 경로 수정을 하기 전**에 뜬 실행분이라
> 경로 존재/부재가 `97 / 12`였고 줄 번호도 어긋났다. **한 커밋 안에서도 편집 순서 때문에
> 출력이 낡는다** — §10.1 **형태 3**의 재발이며 그 사실을 아래 C-2.4에 기록했다.

```
$ python3 citecheck.py docs/discovery/regression-ledger.md
=== 인용 추출 ===
경로 인용 116종 · commit 인용 26종

--- v2 저장소 파일(legacy 검사 대상 아님): 7종 · 행 범위 초과 0 ---
  [ok] reports/evidence/m0/0b/scope.md  ← [(None, 3)]
  [ok] milestone-0.md  ← [(None, 5), ('R-PROV-01', 348)]
  [ok] v2-지침서.md  ← [(None, 45), ('R-BASIS-03', 147)]
  [ok] reports/evidence/m0/0a2/decisions.md  ← [(None, 46)]
  [ok] reports/evidence/m0/0b/commands.md  ← [(None, 60)]
  [ok] data-extract.md  ← [('R-COL-06', 914), ('R-ML-07', 1289)]
  [ok] v2-지침서.md:215-223 (파일 355줄)  ← [('R-ASYNC-06', 1102)]
--- 경로: 존재 104 / 부재 5 ---
  [부재] summary.py:None  ← [(None, 34)]
  [부재] 0a2/decisions.md:None  ← [('R-BASIS-01', 94), ('R-RATE-03', 285), ('R-RATE-05', 331)]
  [부재] fixtures/manifest.yaml:None  ← [('R-PROV-07', 474)]
  [부재] commands.md:None  ← [('R-FLOOR-06', 619), ('R-QUAL-07', 780)]
  [부재] capability-map.md:None  ← [('R-ML-09', 1335), ('R-ML-09', 1345), ('R-ML-09', 1381)]
--- 행 범위: 파일 길이 내 104 / 초과 0 ---

--- commit: legacy 25종 / 이 저장소(v2) 1종 ---
  [v2 — legacy 검사 대상 아님] ec115a7
  ed4b06c 2026-08-14 feat(ml): Phase 3 PR2 — win-proxy 백테스트·캘리브레이션 리포트 (읽기 전용 측정) (#371)
  881ce27 2026-08-06 fix(scoring): expected_margin 축 basis 정합 — floor_headroom 과대 표시 해소 (#355)
  0755695 2026-08-06 fix(basis): budget_estimate capture 축 basis 정합 3종 세트 (#354)
  c4ec93b 2026-07-25 refactor(domain): money 타입(BaseAmount)을 bid_base 경계 시그니처에 강제 — mypy strict 아일랜드 승격 (#262)
  4645ce4 2026-07-15 fix(predictor): 투찰가를 사업금액(기초금액) 기준으로 산정 (#162)
  6b7f185 2026-05-10 feat: expand bid intelligence and crawl linkage
  2610826 2026-08-07 fix(market): budget_cap 하한 정책 — published 법정하한+신뢰 비율 게이트(V3)로 실격측 추천 제거 (#356)
  a1ca0b6 2026-08-11 fix(predictor): summary 예비가 패턴 블록의 None bid_rate 크래시 — #360 회귀 핫픽스 (#361)
  2f9bad7 2026-07-17 chore(data): HistoricalData base_amount 출처 분류(basis)+기초금액 추정 backfill + 홀드아웃 오염 가드 (#199)
  4a8e064 2026-08-08 fix(basis): base÷추정가격 비율로 clean 버킷 오염 분리 — suspect-ratio 재태깅 (#358)
  ddee938 2026-08-08 fix(basis): 추정가격 출처 인지 write 가드 — 파생 예정가·폴백이 분모·budget_cap을 덮지 못하게 (#359)
  3447952 2026-08-07 fix(floor): award_floor_rate 신뢰 게이트 — 성립 불가 게시 하한을 수집·가격·검증 3층에서 차단 (#357)
  c704fff 2026-07-25 fix(classifier): tech_field 축을 면허 게이트와 동일한 lmtGrpNo 그룹-OR로 정렬 (false-exclusion) (#254)
  187cea9 2026-07-25 refactor(eligibility): 그룹-OR 평가 커널 추출 — 면허 게이트·tech_field 공유 (동작 동일, golden diff 0) (#257)
  cc4bc9f 2026-07-25 fix(eligibility): association 축을 그룹-OR 커널 세 번째 소비자로 이관 — 평면-AND 잠재 과차단 선제 제거 (#258)
  aa772f0 2026-07-21 fix(eligibility): ENG001 포괄 별칭을 면허 대조에서 배제 — 엔지니어링 전문분야 collapse 오탐 제거(실측) (#216)
  f3a3027 2026-07-19 fix(collector): eligibility_raw 원천을 license-limit 서브 오퍼레이션으로 교정 — 목록 응답에 자격 상세 부재(실측) (#209
  d85711d 2026-07-19 fix(data): license-limit 서브콜 차수를 제로패딩 문자열로 전달 — int 변환이 "000"을 0으로 파괴 (#210)
  7553873 2026-07-21 feat(eligibility): 면허 자격 게이트를 후보 스크리닝에 wiring(기본 OFF)+영향 계측 (#219)
  19f2c94 2026-08-13 fix(similarity): 백필 폭주 수습 — 큐 21,321건 적체의 코드 원인 제거 (P1~P4) (#368)
  93ecf9e 2026-08-13 docs(similarity): 처리량 산술 정정 — 임베딩 모델이 두 벌이었다 (M1·m3·n1)
  f70df07 2026-08-06 fix(pipeline): 수집 리스 기아 해소 — busy 시 bounded retry + consumer_timeout 선언 (#353)
  fe24c15 2026-07-19 fix(opening): 후보 쿼리 SELECT DISTINCT 제거 — Postgres json 컬럼 비호환 (라이브 실증) (#212)
  75254c1 2026-08-13 feat(ml): 서빙 분포 정합 학습 필터 + 미학습 공종 가드 (Phase 2b) — 용역 편향 +5.91%p → +0.90%p (#366)
  5d38ac1 2026-08-13 feat(ml): 승격 게이트 재설계 — 성숙도 embargo + 겹치지 않는 날짜 창 (Phase 2c) (#367)
  → legacy commit 25종 중 존재 25 · 부재 0
```

- **legacy 경로 인용이 전부 `ed4b06c`에 존재하고 인용 행 범위는 전부 파일 길이 안이다
  (초과 0).** 수는 **위 블록이 정본**이며 이 줄은 옮겨 적지 않는다(H-R3). 부재로 찍히는
  것은 **legacy 트리에 있을 수 없는 것**들이다 — 이 저장소 자신의 문서를 **부분 경로로 적은
  것**, §0.3이 모호성의 **예시로 언급**하는 `summary.py`, 그리고 **아직 존재하지 않는 0C
  소관 예정 경로 `fixtures/manifest.yaml`**(verifier F-c). **전체 경로로 적은 이 저장소
  문서는 이제 `v2 저장소 파일` 절에서 행 범위까지 검사된다**(C-11.1).
- **legacy commit 25종이 전부 존재**한다. `ec115a7`은 **이 저장소의 base SHA**라 legacy
  검사 대상이 아니며 스크립트가 그렇게 분리해 찍는다.

### C-2.2 커버리지 정정 — 이전 판의 기계 축은 전수가 아니었다 (verifier M2)

이전 판의 필터가 **십진 숫자로만 된 해시를 배제**했다:

```python
if re.fullmatch(r'[0-9a-f]+', h) and not re.fullmatch(r'\d+', h):
```

`0755695` · `2610826` · `3447952` · `7553873` **네 종이 그렇게 빠졌다.** 실측:

```
$ python3 - <<'PY'
import re
txt = open("docs/discovery/regression-ledger.md", encoding="utf-8").read()
hs  = set(re.findall(r'`([0-9a-f]{7,40})`', txt))
old = {h for h in hs if re.fullmatch(r'[0-9a-f]+', h) and not re.fullmatch(r'\d+', h)}
new = {h for h in hs if re.fullmatch(r'[0-9a-f]{7,40}', h)}
print("추출 대상 해시 총:", len(new))
print("이전 필터가 검사한 것:", len(old))
print("이전 필터가 빠뜨린 것:", sorted(new - old))
PY
추출 대상 해시 총: 26
이전 필터가 검사한 것: 22
이전 필터가 빠뜨린 것: ['0755695', '2610826', '3447952', '7553873']
```

**필터를 길이 7 이상의 16진수로 바꿨고**(존재 여부는 `git`이 판정한다) 재실행 결과가
C-2.1이다. **커버리지 22/26 → 26/26**이며 그중 legacy 25종은 전부 존재, 나머지 1종은
v2 SHA로 분리됐다.

**이전 판의 "commit 21종이 전부 존재" 주장은 문장과 붙여넣기가 둘 다 부정확했다** —
수를 잘못 셌고 출력도 잘려 있었다. 위 전문이 그 자리를 대신한다.

### C-2.3 부분 경로 인용 2종 (verifier L3)

ledger가 R-QUAL-07에서 `classification/eligibility.py::assess_license` ·
`license_eligibility.py::assess_license_eligibility`로 **부분 경로**를 썼다. §0.3의
"파일명만 쓰지 않는다"에 저촉되고, **검사기가 `::함수` 접미 때문에 그 인용을 보지도
못했다.** 둘 다 고쳤다 — ledger는 전체 경로를 쓰고, 검사기 `PATH` 정규식에
`(?:::[A-Za-z_][A-Za-z0-9_]*)?`를 더해 **그 형태도 추출 대상**이 되게 했다.

### C-2.4 형태 3의 재발 — 한 커밋 안에서도 출력이 낡는다 (verifier H-N1)

라운드 1이 **M1(잘린 출력)을 고치겠다고 선언한 바로 그 블록에서 형태 3이 재발**했다.
원인은 잘라낸 것이 아니라 **뜬 시점**이다 — `4db8830` 한 커밋이 ① L3 경로 수정과
② 출력 붙여넣기를 함께 했는데, **출력을 ① 이전에 떴다.** 커밋 단위로는 "그 커밋의
출력"이 맞지만 **그 커밋 최종 상태의 출력이 아니었다.**

그리고 그 값이 `checklist.md` A2에 **"97종"으로 전파**됐다(형태 5).

**규칙**: 출력을 붙일 때 **그 커밋의 최종 상태에서 다시 뜬다.** "이 커밋 기준"이라는
선언만으로는 부족하다 — 같은 커밋 안에서도 편집 순서가 출력을 낡게 만든다.

**범위 확장 (verifier H-R3, 형태 5의 재발)** — 위 규칙을 지켰는데도 같은 자리가 또 낡았다.
`eeaa8e1`이 **출력 블록을 102/10으로 정확히 갱신**했으나 **그 출력을 인용하는 산문 두 곳이
`99종`으로 남았다.** `97 → 99`가 났던 **그 자리**다.

> **규칙이 지켜진 대상은 "붙여넣기 블록"이었고, 그 블록을 인용하는 산문은 규칙 밖에 있었다.**

**규칙의 적용 대상을 「블록」에서 「블록 + 그 블록을 인용하는 산문」으로 넓힌다.** 그리고
**가능하면 산문이 수를 옮겨 적지 않게 한다** — `checklist.md` A2와 위 C-2.1 bullet은 이제
**블록을 가리키기만** 한다(형태 6의 "셈이 아니라 재현 명령으로"와 같은 취지다).
전수 훑기와 그 사각지대를 보는 축은 **C-2.6**에 있다.

### C-2.5 형태 6의 재발 — 축이 지목한 것을 순환 논증으로 닫았다 (verifier H-R2)

라운드 2가 `numsrc`를 넓혀 `R-PROV-01`의 `5`를 **정확히 지목**했다. 그런데 그것을
**"근거에 다섯 경로가 열거돼 있다"**로 닫았다 — **legacy가 아니라 ledger 자신을 센 것**이다.
그 위에 **"추가 발견 0건"**이라는 전칭이 얹혔고, 그 전칭은 **M-N2를 닫으며 새로 쓴
문장**이었다.

**뿌리는 정본의 소재를 정하지 않은 것**이다. "N곳에 복제"는 **legacy 트리에 대한 주장**
이므로 세어야 할 것은 트리다. 이 문서의 인용 목록은 **그 주장의 사본**이지 정본이 아니다.

**규칙**: **주장의 정본이 어디 있는지 먼저 정하고, 판정 근거 서술이 그 구분을 드러내게
한다.** legacy에 대한 주장이면 **재현 명령과 그 결과**를 적는다 — "이 문서에 열거돼
있다"는 판정 근거가 될 수 없다. **축이 지목한 것을 닫을 때 특히 그렇다**(도구를 만든
라운드가 그 도구의 출력을 무력화하기 쉽다).

C-7.3의 판정 근거를 **전부 재현 명령으로 교체**했고, 그 결과 **추가 발견은 0이 아니라
1건**(R-PROV-01)이었다.

### C-2.6 산문 인용 전수 훑기 — 이 자리가 축의 사각지대였다 (verifier H-R3)

**기존 축 넷은 이 부류를 보지 않는다.** `citecheck`·`numsrc`는 **(ledger ↔ legacy)**를,
`ledgercheck`는 **ledger 내부 구조**를, `statusdiff`는 **두 문서의 상태**를 본다.
**evidence 안에서 산문이 출력 블록의 수를 옮겨 적는 자리**는 **어느 축의 대상도 아니었다** —
`97 → 99`와 `99 → 102`가 **같은 칸에서 두 번** 난 이유다.

**축을 하나 더 만들었다**(결함이 두 번 관측된 뒤에 만든다는 순서를 따른다). 정본은
`commands.md`의 **출력 블록**이고 대조 대상은 **같은 파일들의 산문**이다.

```python
# H-R3의 사각지대 —— **스크립트 출력 블록**과 **그 출력을 인용하는 산문**의 대조.
# 기존 축들은 (ledger↔legacy)만 본다. evidence 안에서 산문이 블록의 수를 옮겨 적을 때
# 블록만 갱신되고 산문이 남는 형태를 아무 축도 보지 않았다(형태 5의 변종).
#
# 정본 = commands.md 안의 ``` 출력 블록. 대조 대상 = 같은 파일들의 산문.
import re, glob, sys

EV = sorted(glob.glob("reports/evidence/m0/0b/*.md"))
blocks = "\n".join(
    re.findall(r'```[a-z]*\n(.*?)\n```', open("reports/evidence/m0/0b/commands.md",
               encoding="utf-8").read(), re.S))

# 블록에서 뽑는 정본 수치: "이름: N" / "존재 N / 부재 M" 같은 명명된 집계
GT = {}
for pat, key in [
    (r'경로: 존재 (\d+) / 부재 (\d+)',              ("경로 존재", "경로 부재")),
    (r'행 범위: 파일 길이 내 (\d+) / 초과 (\d+)',    ("행 범위 내", "행 범위 초과")),
    (r'legacy commit (\d+)종 중 존재 (\d+)',        ("legacy commit", "legacy commit 존재")),
    (r'경로 인용 (\d+)종 · commit 인용 (\d+)종',     ("경로 인용", "commit 인용")),
]:
    m = re.search(pat, blocks)
    if m:
        for name, val in zip(key, m.groups()): GT[name] = int(val)

print("=== 정본 (commands.md 출력 블록에서 추출) ===")
for k, v in GT.items(): print(f"  {k}: {v}")

# 산문에서 같은 양을 가리키는 표현
CHK = [
    ("경로 존재", re.compile(r'legacy 경로 인용 \*{0,2}(\d+)종')),
    ("경로 존재", re.compile(r'경로 인용 \*{0,2}(\d+)종 전부 존재')),
    ("legacy commit", re.compile(r'legacy commit \*{0,2}(\d+)종')),
]
print("\n=== 산문 대조 ===")
# 패턴 둘이 같은 문자열에 함께 걸릴 수 있다(`legacy 경로 인용 N종 전부 존재`).
# 자리 지목은 정확하나 **불일치 수가 부풀므로** (파일, 줄, 매치 구간)으로 중복을 없앤다.
# 그 수 자체가 evidence 산문이 인용하는 값이라 부풀면 그대로 틀린 수가 된다(verifier L-2).
seen, hits = set(), []
for f in EV:
    for i, l in enumerate(open(f, encoding="utf-8").read().split("\n"), 1):
        if l.lstrip().startswith(("$", "---", "===")): continue
        for name, rx in CHK:
            for m in rx.finditer(l):
                got, exp = int(m.group(1)), GT.get(name)
                if exp is None or got == exp: continue
                # 한 줄에서 **한 집계에 대한 주장은 하나**다. 패턴이 여럿 걸려도
                # (파일, 줄, 집계 이름)으로 한 번만 센다 — 매치 구간으로 키를 잡으면
                # 시작 위치가 달라 중복이 남는다(변이 시험으로 확인).
                key = (f, i, name)
                if key in seen: continue
                seen.add(key); hits.append((name, f, i, got, exp, l.strip()[:110]))
for name, f, i, got, exp, l in hits:
    print(f"  [{name}] {f}:{i}  산문={got} 정본={exp}\n      {l}")
print(f"  불일치 {len(hits)}건 (이력 서술 포함 — 사람 판정 대상)")


# ── 2차: **한글 수사 전칭** — 출력 블록 바로 뒤의 산문이 그 블록의 건수를 딴 말로 적는가 ──
# `scope.md` 형태 6이 **"`뿐`·`전부`처럼 수를 쓰지 않는 전칭도 포함한다"**고 이미 적는데
# 1차는 **아라비아 숫자만** 봐서 `남은 **둘**은 오탐이며`를 놓쳤다(verifier F-a).
# 정본은 **그 블록이 스스로 헤더에 찍는 `N건`**이고, 대조 대상은 **블록 직후 산문**이다.
NUM = {"하나":1,"한":1,"둘":2,"두":2,"셋":3,"세":3,"넷":4,"네":4,"다섯":5,
       "여섯":6,"일곱":7,"여덟":8,"아홉":9,"열":10}
NRX = re.compile(r'(?<![가-힣])(' + "|".join(NUM) + r')(?=\s*(?:건|개|곳|자리|항목|은|는|다|뿐|만))')
WIN = 12          # 블록 끝에서 이 줄 수 안의 산문만 그 블록에 대한 논평으로 본다
print("\n=== 한글 수사 대조 (출력 블록 헤더의 `N건`이 정본) ===")
hits2 = []
for f in EV:
    ls = open(f, encoding="utf-8").read().split("\n")
    fence, start, decl = None, None, set()
    for i, l in enumerate(ls, 1):
        t = l.strip()
        if t.startswith("```"):
            if fence is None:
                fence, start, decl = t[:3], i, set()
            else:
                fence = None
                # 블록이 스스로 선언한 건수들
                # **마지막 헤더**가 정본이다. 블록이 여러 절을 찍으면 직후 산문은
                # 보통 **마지막 절**을 논평한다 — 선언된 값을 전부 허용하면 어떤 수사든
                # 통과해 F-a 가 그렇게 새어 나갔다.
                for k in range(start, i):
                    if ls[k-1].lstrip().startswith("==="):
                        d = {int(x) for x in re.findall(r'(\d+)건', ls[k-1])}
                        if d: decl = d
                if decl:
                    for j in range(i + 1, min(i + 1 + WIN, len(ls) + 1)):
                        pl = ls[j-1]
                        if pl.strip().startswith("```"): break
                        for m in NRX.finditer(pl):
                            v = NUM[m.group(1)]
                            if v not in decl:   # 정본 = 블록의 마지막 `N건` 헤더
                                hits2.append((f, j, m.group(1), v, sorted(decl), pl.strip()[:96]))
for f, j, w, v, decl, l in hits2:
    print(f"  [{f.split('/')[-1]}:{j}] 산문 `{w}`({v}) ↔ 블록 선언 {decl}\n      {l}")
print(f"  지목 {len(hits2)}건 — **오탐이 섞인다. 사람이 읽어 판정한다.**")
```

```
$ python3 prosecheck.py
=== 정본 (commands.md 출력 블록에서 추출) ===
  경로 존재: 104
  경로 부재: 5
  행 범위 내: 104
  행 범위 초과: 0
  legacy commit: 25
  legacy commit 존재: 25
  경로 인용: 116
  commit 인용: 26

=== 산문 대조 ===
  불일치 0건 (이력 서술 포함 — 사람 판정 대상)

=== 한글 수사 대조 (출력 블록 헤더의 `N건`이 정본) ===
  [commands.md:1011] 산문 `열`(10) ↔ 블록 선언 [6]
      > **판정 근거를 전부 "legacy에서 무엇을 어떻게 셌는가"로 교체**했다. 아래 표의 `실측` 열은
  [commands.md:1285] 산문 `둘`(2) ↔ 블록 선언 [6]
      > 없이 고정했다. **둘 다 고쳤다**(결정 무관 = 판정 **순서** / 조건부 = 경계 **값**).
  [commands.md:1290] 산문 `하나`(1) ↔ 블록 선언 [6]
      > 어휘와 달리 우연 일치가 드물어 **하나만 겹쳐도 지목**한다.
  [commands.md:1290] 산문 `한`(1) ↔ 블록 선언 [6]
      > 어휘와 달리 우연 일치가 드물어 **하나만 겹쳐도 지목**한다.
  [commands.md:1292] 산문 `둘`(2) ↔ 블록 선언 [6]
      **판정 — 아래는 축을 고친 뒤의 결과다.** 2차가 남긴 후보 둘은 **어휘만 겹치는 오탐**이다:
  지목 5건 — **오탐이 섞인다. 사람이 읽어 판정한다.**
```

**전수 훑기 결과 — 이 축이 보는 범위 안에서 추가 발견 0건.** 지목된 자리는 전부 이
라운드가 고친 두 곳(`checklist.md` A2 · C-2.1 bullet)이며, **고친 뒤에는 산문이 수를
옮겨 적지 않으므로 그 칸이 다시 낡을 수 없다.**

**"0건"은 축의 범위 안에서의 0이지 evidence 전체에 낡은 수가 없다는 뜻이 아니다.**
실제로 **사각지대 ③에 같은 부류의 실물이 살아 있었다** — C-5.1의 "현재 28종"(verifier
M-1). 이 라운드에 그것도 재현 명령으로 바꿨으나, **사각지대가 남아 있다는 사실 자체는
아래 한계 목록이 계속 진다.**

**이 축이 못 보는 것**(한계):

- **표현이 다른 인용** — `C-2.4`의 "현재는 99다" 같은 문장은 명명된 집계 패턴에 걸리지
  않아 **사람이 찾았다.** 그 자리는 아예 **살아 있는 수를 빼서** 닫았다.
- **이력 서술과 현재 값 주장의 구분** — 축은 낡음을 지목만 하고 **그 수가 그 시점 기록인지
  현재 주장인지는 사람이 판정**한다(다른 축들과 같다).
- **정본이 블록에 없는 수** — 산문이 계산해 적은 파생값은 대조 대상이 없다.
  **C-5.1의 "현재 28종"이 그 실물이었다**(verifier M-1) — 이 라운드에 재현 명령으로
  바꿨다.

**축 자신의 결함 하나를 고쳤다**(verifier L-2): `CHK`의 두 패턴이 **같은 문자열에 함께
걸려** `checklist.md`의 한 줄을 **두 번 계상**했다. 자리 지목은 정확했으나 **불일치 수가
부풀었고, 그 수는 evidence 산문이 인용하는 값**이라 부풀면 그대로 틀린 수가 된다 —
이 slice가 반복해 태운 부류다. **한 줄에서 한 집계에 대한 주장은 하나**이므로
`(파일, 줄, 집계 이름)`으로 중복을 없앴다. **매치 구간을 키로 잡으면 시작 위치가 달라
중복이 남는다** — 변이 시험(고친 칸을 `99종`으로 되돌림)으로 확인했고 지목 **2건 → 1건**이
됐다. **한계로 기록하지 않고 고친 이유**는 그 수가 **문서로 새어 나가는 값**이기
때문이다.

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
        # legacy 파일의 두 번째 이후 행 범위(`path.py:1-2`, `:3-4`, `:5`)는 §0.3의 예외다.
        # 한 `- **근거**:` 항목이 여러 줄에 걸치므로 **그 항목 시작까지 거슬러** 본다 —
        # 직전 한 줄만 보면 줄바꿈이 끼었을 때 놓친다(이전 판이 그랬다).
        ctx = [l[:m.start()]]
        for k in range(i - 2, max(-1, i - 8), -1):
            ctx.append(lines[k])
            if re.match(r'^- \*\*', lines[k]): break
        # 문자 클래스가 ASCII 전용이라 `v2-지침서.md:215-223` 같은 **한글 파일명 인용**을
        # 경로로 보지 못해 뒤따르는 `:222`·`:223` 을 자기참조로 오탐했다(verifier F-1).
        if any(re.search(r'`[A-Za-z0-9_./가-힣-]+\.(py|md):[\d,\-]+`', c) for c in ctx):
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

=== 사용자 영향 '판정 불가' ===
  R-RATE-01: **`판정 불가`.** `(1.5, 2.0]` 구간의 오해석이 정산 백테스트에서 몇 건을
  R-RATE-02: **`판정 불가`** — 남은 콜사이트가 실제로 잘못된 답을 냈다는 관측이 없다.
  R-RATE-03: **`판정 불가`.** 이 경로로 잘못된 점수가 운영자에게 갔다는 관측이 없다.
  R-RATE-05: **`판정 불가`** — 실제로 한쪽만 바뀌어 사고가 났다는 기록이 없다.
  R-PROV-05: **관측된 직접 영향 없음.** 두 축의 provenance가 같은 문자열로 저장돼
  R-FLOOR-07: 표본이 **149건이면 판정 불가, 150건이면 판정된다.** 그 경계에 도메인
  R-QUAL-02: **관측된 피해 없음(잠재).** 데이터가 아직 그 형태를 만들지 않았을 뿐이며
  R-COL-02: **`판정 불가`.** 코드 경로는 확정적으로 읽히지만 **실제로 이 경로가 429를
  R-ASYNC-02: **관측된 피해 없음(잠재).** 상수 하나를 바꾸면 재발 방지선이 조용히
  R-ML-07: **관측된 피해 없음.** 다만 이 golden들은 **회귀 검출 장치이지 정답 판정
  R-ML-08: **관측된 피해 없음.** 그러나 계약의 근거가 "현재 구현들의 교집합"이면

=== id 중복 ===
  없음

=== 인용 형식: 파일명만 쓴 것(전체 경로 아님) — 계열 본문 한정 ===
  없음

=== 이 문서 자기참조 줄 번호 (legacy 행 범위 연속 표기 제외) ===
  없음
```

- **계열 8이 10인 것은 P-ML-01을 포함**하기 때문이다. **회귀는 9건**이고 합계 60에
  들어간다 — 스크립트가 그 분해를 직접 찍는다.
- **상태 분포 합 61**. `판정 불가` 2건은 **상태 축**(R-BASIS-07 · R-PROV-01)이며
  **사용자 영향 축**은 그보다 많다(C-5.3).
- **자기참조 줄 번호 검사의 예외 판정을 고쳤다**(라운드 1) — `- **근거**:` 한 항목이
  여러 줄에 걸치면 legacy 행 범위 연속 표기(`:162`)를 놓쳤다. **항목 시작까지 거슬러
  보게** 바꿨다.
  **한계(verifier L-N1)**: 이 완화는 **트레이드오프**다. lookback 범위 안에 legacy 경로
  인용이 하나라도 있으면 그 뒤의 `` `:NNN` ``을 전부 예외로 본다 — **같은 항목 안에서
  이 문서 자신을 줄 번호로 가리키면 놓친다.** 이 검사가 지금 `없음`을 내지만
  **없다는 것을 이 검사가 증명하지는 않는다.**
- **한글 파일명이 두 축의 사각지대였다**(verifier F-1). `d205d31`이 넣은
  `` `v2-지침서.md:215-223` `` 인용에 대해 이 검사가 뒤따르는 `` `:222` ``·`` `:223` ``을
  **자기참조 2건으로 오탐**했다 — 예외 판정 정규식의 문자 클래스가 **ASCII 전용**이라
  한글이 든 파일명을 **경로로 인식하지 못했다.** 실질은 자기참조가 아니고 §0.3 위반도
  아니다. **정규식에 한글을 넣어 고쳤고 지금은 `없음`이다.** 판단 근거는 C-11.1에 있다.
- **`citecheck`도 같은 문자 클래스였다** — 그래서 **`v2-지침서.md:215-223`은 경로 존재도
  행 범위도 어느 축이 검사하지 않았다.** 같이 넓혔고, **legacy 트리에 없는 것이 정상인 v2
  저장소 파일**은 commit 축과 같은 방식으로 갈라 **v2 HEAD에서 행 범위를 검사**한다.
  C-2.1 출력의 `v2 저장소 파일` 절이 그것이다 — **검사가 늘었다.**
- **bare 연속 범위의 사각지대(verifier L-N2)**: `path.py:1-2` 뒤에 오는 `` `:3-4` `` 형태는
  **어느 기계 축도 행 범위를 검사하지 않는다** — `citecheck`의 `PATH`가 경로를 요구하기
  때문이다. **수를 여기 적지 않는다**(아래 ※). 세는 명령:

  ```
  grep -oE '(^|[^A-Za-z0-9/._])`:[0-9]+(-[0-9]+)?`' docs/discovery/regression-ledger.md | wc -l
  ```

  이 형태는 **사람이 읽은 것으로만 확인**됐다(계열별 커밋 메시지가 그 기록이다).
  **축을 늘릴지는 근거가 관측된 뒤에 판단한다** — 지금 이 부류에서 적발된 오류는 없다.
  > ※ 이전 판은 **"현재 28종"**이라 적었고 그 수는 이 문장을 쓴 커밋(`88e759d`)에서만
  > 맞았다 — **바로 다음 커밋(`88fac65`)의 M-R2 수정이 `R-ASYNC-02` `근거`에 앵커 7개를
  > 더해 35가 됐다.** 형태 5이고 **H-R3와 같은 부류**이며, **이 절이 스스로 적은 사각지대
  > ③(정본이 블록에 없는 파생값)의 실물**이다(verifier M-1). 그래서 `checklist.md` A2·
  > C-2.1 bullet과 같은 처리를 했다 — **수를 빼고 재현 명령을 남긴다.**
- **선행 조사 §0.1 요약 표와 다르다. 그러나 0B가 판정을 바꾼 것은 아니다** — C-5.2.

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
scout 분포: {'legacy에 잔존': 27, 'legacy에서 수정됨': 31, '판정 불가': 2, 'legacy에 이미 있는 예방책': 1}
ledger 분포: {'legacy에 잔존': 27, 'legacy에서 수정됨': 31, '판정 불가': 2, 'legacy에 이미 있는 예방책': 1}
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

**원인은 하나가 아니다**(verifier M5 — 이전 판은 전부를 "두 축의 혼동"으로 적었으나
재계산하니 그 설명이 성립하는 것은 **한 칸뿐**이다).

```
계열3: 항목=(4, 3, 1) 표=(3, 4, 1)  → 수정/잔존 배분만 다름(합은 같다)
계열4: 항목=(3, 5, 0) 표=(4, 4, 0)  → 수정/잔존 배분만 다름(합은 같다)
계열6: 항목=(3, 5, 0) 표=(3, 4, 1)  → 판정불가 축 다름, 수정+잔존 합 다름
계열8: 항목=(6, 3, 0) 표=(4, 5, 0)  → 수정/잔존 배분만 다름(합은 같다)
```

- **계열 6 한 칸만 두 축의 혼동**이다 — 표가 `판정 불가` 1을 세는데 해당 항목(R-COL-02)의
  **상태는 `잔존`이고 `판정 불가`인 것은 사용자 영향**이다.
- **계열 3·4·8은 단순 오계수**다 — `수정 / 잔존` 배분만 어긋나고 **합은 같다.**

**0B의 처리**: 항목별 상태를 그대로 두고(바꿀 근거가 없다) **두 축을 문서 규약에서
분리**했다 — ledger §0.1이 `사용자 영향`과 `상태`를 **다른 필드**로 정의하고, 위 스크립트가
**두 축을 따로 찍는다.** 표를 고치는 것이 아니라 **한 칸에 섞일 수 없게** 만든 것이다.
`_workspace/`의 노트는 gitignore 대상이라 이 저장소가 고치지 않는다.

### C-5.3 두 축의 `판정 불가`를 구분한다

**상태 축**(그 결함이 legacy에 있는가)과 **사용자 영향 축**(피해가 관측됐는가)은 다른
질문이다. 스크립트가 후자를 따로 찍고 **11행**을 낸다:

R-RATE-01 · 02 · 03 · 05 · R-PROV-05 · **R-FLOOR-07** · R-QUAL-02 · R-COL-02 ·
R-ASYNC-02 · R-ML-07 · R-ML-08.

- **실제 `판정 불가`는 10건**이다. **`R-FLOOR-07`은 문자열 오탐**이다(verifier L1) —
  그 항목의 사용자 영향은 "표본이 **149건이면 판정 불가**, 150건이면 판정된다"로
  **legacy 동작을 설명하며 그 낱말을 쓴 것**이고, 사용자 영향 축이 `판정 불가`로
  표시된 것이 아니다. **스크립트는 낱말을 보고 사람이 판정한다** — 출력을 줄이지 않고
  오탐임을 여기 적는다.
- **계열 2는 5건 중 4건**이 여기 들어간다(계약이 예고한 얇은 계열이다).

---

## C-6. 불변 (A6)

```
$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
    docs/discovery/regression-ledger.md reports/evidence/m0/0b/ | wc -l
13
$ git diff --check ec115a7...HEAD | wc -l
0
$ git status --porcelain -- docs/discovery/regression-ledger.md reports/evidence/m0/0b/ | wc -l
0
$ wc -l docs/discovery/regression-ledger.md
1402 docs/discovery/regression-ledger.md
```

- secret 스캔 매치는 **전부 자기참조**다 — `scope.md`의 A6 문장 · `checklist.md`의 A6 행 ·
  위 스캔 명령 자신 · 이 설명이 `secret`이라는 낱말을 포함한다. **실제 비밀은 없다.**
  **수치는 이 절을 담은 커밋 기준**이며, evidence를 더 쓰면 늘어난다 — 그래서
  `checklist.md` A6은 수를 적지 않고 **"자기참조만"**으로 적는다(형태 6).
  (처음에 "매치 없음"이라고 적었으나 **그 문장 자신이 매치를 만든다** — 0A2가 같은 형태로
  걸린 적이 있어 실측으로 고쳤다.)
- 공고번호·기관명 등 식별자는 **근거 추적에 필요한 곳(commit 참조)에만** 남기고 본문에
  옮기지 않았다 — 패턴 스캔으로 잡히지 않으므로 **육안 확인 병기**.

---

## C-7. 수정 라운드 1 — H1·H2 재확인 실측 (2026-08-27)

검증 판정 `not-ready`. **A2 하나가 미충족**이었고 **H1·H2가 형태 2의 실물**이다 — 둘 다
선행 조사 노트의 문장이 축어로 이월된 것이며, 계약 A2가 "상류 노트에서 옮겨 온 인용도
**그 파일을 직접 열어** 확인한다"고 요구한 바로 그 지점이다.

### C-7.1 H1 — `필요 951/시`가 인용한 범위에 없다

```
$ git -C bid-vector show ed4b06c:app/core/inference_config.py | sed -n '77,89p'
    # 이걸 상한으로 만드는 것이 **산술적으로 불가능하지는 않다** — 필요량은 창에 따라
    # 3,800~5,600건/시이고 소진 6,000건/시 아래다. 하지 않는 이유는 다른 데 있다:
    # 5,600건/시는 **소진 용량의 94%** 이고, 그러면 투입 ≈ 소진이 되어 아래 부등식의
    # 3배 여유가 정확히 사라진다. 그 여유가 이 PR 의 핵심 재발 방지선이므로, drift
    # 상한을 사려고 재발 방지선을 파는 거래는 하지 않는다. 여기서 보장하는 것은 수명뿐이다.
    #
    # 위쪽 부등식(투입 2,000 ≤ 소진 6,000, 3배)이 이번 사고의 재발 방지선이다. 투입이
    # 소진을 넘으면 큐가 무한히 자란다(실제로 21,321건). 60초 유지 시 투입이 6,000건/시로
    # 소진과 정확히 같아져 여유가 0 이 된다.
    #
    # 한계점: 수명 보장은 활성 대상이 투입×6h = **12,000건**을 넘으면 깨진다(현재
    # 5,822건, 2.1배 여유). 그 선을 넘으면 배치 크기를 올려야 하고, 그때도 투입이
    # 소진을 넘지 않도록 outbox 처리량을 함께 올려야 한다.
```

- **필요량은 `3,800~5,600건/시`**다. **`951`은 이 범위에 없다.**
- **「투입 2,000 ≤ 소진 6,000(3배)」과 「60초 유지 시 여유 0」은 주석과 일치**하므로
  건드리지 않았다. 틀린 것은 `951` 하나다.

**951의 출처를 추적했다** — 이전 판에 있었고 legacy가 스스로 지웠다:

```
$ git -C bid-vector show 93ecf9e -- app/core/inference_config.py | grep -E '^[-+].*(951|필요)'
-    #   필요 처리량 = 활성 대상 / 스냅샷 최대 수명 = 5,708 / 6h ≈ 951건/시
-    #   필요(951) ≤ 투입(2,000) ≤ 소진(6,000)
+    # 그렇다고 필요 처리량이 무효화 횟수에 비례하지는 **않는다**. 한 대상은 한 회전에
+    # 작업량의 단위는 "무효화 이벤트"가 아니라 **서로 다른 대상**이고, 필요 처리량은
+    # 실시간 경보가 아니다. 더 빠른 검출이 필요해지면 임계를 낮출 것이 아니라 이 점검을
$ git -C bid-vector log -1 --format='%h %ad %s' --date=short 93ecf9e
93ecf9e 2026-08-13 docs(similarity): 처리량 산술 정정 — 임베딩 모델이 두 벌이었다 (M1·m3·n1)
```

**`93ecf9e`가 그 두 줄을 지웠다.** legacy가 오류로 판정해 제거한 값을 이 문서가 되살린
셈이다. **그 정정 사실을 R-ASYNC-02의 근거에 넣었다** — 관계가 주석에만 있으면 산술이
틀려도 아무것도 깨지지 않고 사람이 읽을 때까지 남는다는 **그 항목의 논거를 강화**한다.

**`951건/시`는 `ed4b06c`에 여전히 있다** — 다만 **다른 파일에서 다른 양**을 가리킨다:

```
$ git -C bid-vector grep -n '951건/시' ed4b06c -- '*.py'
ed4b06c:app/core/constants.py:445:# 신선도면 판단이 달라지지 않는다. 부하는 활성 대상 수 / 수명 = 5,708/6h ≈ 951건/시,
```

여기서는 **스냅샷 6h 수명이 만드는 부하**이지 **재발 방지선의 필요량이 아니다.**
ledger에 그 구분을 명시했다. **verifier가 "951이 트리 전체에 없다(테스트의 0.951뿐)"고
적었는데 그 부분은 정확하지 않다** — `constants.py:445`에 `951건/시`가 있다. **H1의 실질
(인용한 범위에 없고 그 값이 재발 방지선의 필요량이 아니다)은 그대로 성립**하며, 이 구분을
적어 두는 이유는 **다음 사람이 `951`을 다시 만났을 때 어느 쪽인지 알게 하기 위해서**다.

### C-7.2 H2 — R-FLOOR-08 관찰이 코드와 반대였다

```
$ git -C bid-vector show ed4b06c:app/domain/floor_shortfall.py | sed -n '48,57p'
# 사정률(예정가/기초금액)의 물리적 개연 범위. KONEPS 복수예비가격은 기초금액을
# 대략 ±2~3% 로 둘러싸도록 생성되므로 실제 추첨 결과가 이 범위를 벗어날 수 없다.
# 범위 밖 값은 추첨 결과가 아니라 수집/파싱 오류이므로 표본에서 제외한다.
# ⚠ app/core/constants.ASSESSMENT_RATE_PLAUSIBLE_*(0.8~1.2)와 이름이 비슷하지만
# **다른 밴드·다른 목적**(그쪽=관측 필터)이다 — §4.5-8 근거 통합 금지(리뷰 N4):
# 합치면 빈도가 왜곡된다 — 하단 꼬리(0.8~0.90)는 분모만 늘려 낙관, 상단 꼬리
# ((1.10,1.20])는 임계(≈1.00125)도 넘어 분자에 들어가 비관, 순방향은 꼬리 분포에
# 달린다(2026-08-12 실측 하단 76·상단 0 → 현재는 낙관 방향, 리뷰 O2).
ASSESSMENT_RATE_MIN: Final[float] = 0.90
ASSESSMENT_RATE_MAX: Final[float] = 1.10
```

`is_plausible_assessment_rate`(`:89-97`)가 **범위 안만** 담는 순수 술어이고 `:162`가 그것을
표본 필터로 쓴다.

- 밴드는 **`[0.90, 1.10]`** — **버리는 것은 1에서 먼 꼬리**(하단 `0.8~0.90` · 상단
  `(1.10, 1.20]`)이고 **1 근방은 유지된다.** 관찰이 정반대였다.
- **관찰과 제목을 코드에 맞게 뒤집었고**, 소스가 적은 **메커니즘**으로 대체했다 —
  하단 꼬리는 낙관, 상단 꼬리는 비관, **순방향은 꼬리 분포에 달린다**(실측 **2026-08-12**
  하단 76 · 상단 0 → 그 시점 **낙관 방향**).
- **사용자 영향은 임계 사정률 `≈1.00125` 축이라 성립하므로 유지**하되, 위 실측을 붙여
  **"틀릴 수 있었고 실제로 그 방향이었다"**로 정확히 했다.
- **선행 조사의 제목이 이미 뒤집혀 있었고 본문은 정확했다.** ledger가 **그 제목 표현을
  관찰 본문으로 승격**한 것이 이 결함의 경로다 — 상류의 **제목도 인용 대상**이다.

### C-7.3 같은 형태가 더 없는지 — 기계로 훑고 전수 판정했다

**형태 2에도 기계 검증 가능한 절반이 있다** — 항목이 인용한 legacy 범위에 그 항목이
적은 **수치가 실제로 있는가**. H1이 정확히 그 축이었으므로 축을 만들었다(**결함이 먼저
관측된 뒤에 도구를 만든다**).

#### 커버리지 — 이 축이 무엇을 보고 무엇을 못 보는가 (verifier M-N2)

이전 판의 전칭("H1·H2 외에 인용 범위에 없는 수치는 없다")이 **자기 재현 명령의 범위를
넘었다.** 정규식이 뒤에 오는 `\w`를 전부 배제해 **한글 단위가 붙은 수를 통째로 놓쳤다** —
`78건` · `5,822건` · `1,046행` · `1.15배`가 전부 검사 밖이었다. **좁히는 대신 패턴을
넓혔다**(라틴 문자·추가 숫자가 이어붙는 경우만 배제).

**그리고 스캔 대상 필드를 `관찰`로 한정했다.** 이전 판은 이어지는 줄을 전부 훑어
`V2 예방 제약`·`검증 방법`의 수치까지 지목했는데, **그것들은 이 문서가 V2에 요구하는
것이라 legacy 원문에 있을 이유가 없다.** 넓힌 패턴 그대로 필드만 좁히니 31건 → 6건이 됐고
**그 6건은 전부 실제 `관찰`의 수치**다.

**이 축이 못 보는 것**(한계로 기록):

- **방향·의미의 정합** — H2가 그 부류다. 수치가 맞아도 서술이 반대일 수 있다.
- **원문에 리터럴로 없는 파생·집계 수치** — 아래 6건이 전부 그 부류이며 **사람이 원문을
  세어 확인**했다.
- **단일 숫자와 백분율 표기**는 잡되, 그 값이 원문의 다른 문맥에 우연히 있으면 통과한다.

```python
# 형태 2의 기계 검증 가능한 절반 —
# ledger 항목이 인용한 legacy 행 범위에 그 항목이 적은 수치가 실제로 있는가.
# (방향·의미의 정합은 기계가 못 본다. 그 절반은 사람이 읽는다.)
import re, subprocess, collections
REPO, REF = "bid-vector", "ed4b06c"
lines = open("docs/discovery/regression-ledger.md", encoding="utf-8").read().split("\n")
PATH = re.compile(r'`([A-Za-z0-9_./-]+\.(?:py|md))(?::([\d,\-]+))?`')
# 이전 판은 뒤에 오는 `\w` 를 전부 배제해 **한글 단위가 붙은 수를 놓쳤다**
# (`78건`·`5,822건`·`1,046행`·`1.15배`가 전부 검사 밖이었다 — verifier M-N2).
# 지금은 **라틴 문자·추가 숫자가 이어붙는 경우만** 배제한다(식별자·버전 문자열 방지).
NUM  = re.compile(r'(?<![\w.:/-])(\d{1,3}(?:,\d{3})+|\d+(?:\.\d+)?)(?![\d,]*[A-Za-z_])')
starts = [i for i, l in enumerate(lines) if re.match(r'^### [RP]-', l)]
cache = {}
def body(p):
    if p not in cache:
        cache[p] = subprocess.run(["git","-C",REPO,"show",f"{REF}:{p}"],
                                  capture_output=True, text=True).stdout.split("\n")
    return cache[p]
def norm(x): return x.replace(",", "")
flag = []
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    eid = re.match(r'^### ([RP]-[A-Z]+-\d+)', lines[st]).group(1)
    blk = lines[st:e]; txt = "\n".join(blk)
    # 인용된 (경로, 범위)들의 원문을 모은다
    src = []
    for m in PATH.finditer(txt):
        p, rng = m.group(1), m.group(2)
        b = body(p)
        if len(b) <= 1: continue
        if rng:
            for part in rng.split(","):
                a = part.split("-"); lo = int(a[0]); hi = int(a[-1]) if len(a) > 1 else lo
                src += b[lo-1:hi]
        else:
            src += b
    # 커밋 본문도 근거로 인용된다 — 그 본문의 수치도 pool에 넣는다(H1이 이 축이었다).
    for h in set(re.findall(r'`([0-9a-f]{7,40})`', txt)):
        r = subprocess.run(["git","-C",REPO,"log","-1","--format=%B",h],
                           capture_output=True, text=True)
        if r.returncode == 0: src += r.stdout.split("\n")
    if not src: continue
    pool = set()
    for l in src:
        for mm in NUM.finditer(l): pool.add(norm(mm.group(1)))
        for mm in re.finditer(r'\d[\d,\.]*', l): pool.add(norm(mm.group(0)))
    # **`관찰` 필드만** 본다. 그 필드가 legacy 사실을 주장하는 자리이고,
    # `V2 예방 제약`·`검증 방법`은 이 문서가 **V2에 요구하는 것**이라 legacy 원문에
    # 있을 이유가 없다(이전 판은 이어지는 줄을 전부 훑어 그것들까지 지목했다 — M-N2).
    field = None
    for l in blk:
        m = re.match(r'^- \*\*([^*]+)\*\*', l)
        if m: field = m.group(1).split("(")[0].strip()
        if field != "관찰": continue
        for mm in NUM.finditer(l):
            v = norm(mm.group(1))
            if v in pool: continue
            if v in {norm(x) for x in re.findall(r'\d[\d,\.]*', " ".join(
                    re.findall(r'`[^`]*`', l)))}: continue   # 인용부호 안 식별자
            if re.match(r'^20\d\d$', v): continue           # 연도
            if re.search(r'#%s\b' % re.escape(v), l): continue  # PR 번호
            if re.search(r'§\s*%s\b' % re.escape(v), l): continue  # 이 문서 절 번호
            flag.append((eid, v, l.strip()[:95]))
print(f"=== 인용 범위에서 찾지 못한 수치: {len(flag)}건 (전부 사람 판정 대상) ===")
seen = collections.Counter()
for eid, v, l in flag:
    seen[eid] += 1
    if seen[eid] <= 4: print(f"[{eid}] {v}\n    {l}")
```

```
$ python3 numsrc.py
=== 인용 범위에서 찾지 못한 수치: 6건 (전부 사람 판정 대상) ===
[R-PROV-01] 7
    복제돼 있고 **테스트까지 넣으면 7곳**이다(재현: `git grep -n -E '66(\.2)?%' ed4b06c -- '*.py'`).
[R-FLOOR-05] 4
    적는다. 서비스 층은 사유 4종을 나누고 각 사유에 "위험이 없다는 뜻이 아닙니다"를 명시한다.
[R-COL-01] 0
    `resultCode`로 신호한다(4xx가 아니다).** 이게 통과하면 payload가 0건으로 파싱되어
[R-COL-02] 7
    `selector_drift` / `timeout` / `unknown` **7종이고 `429`·`rate limit`·`quota`에 대응하는
[R-COL-03] 30
    - **관찰**: 30여 개 리터럴 substring 매칭(`"captcha"` · `"403"` · `"net::"` ·
[R-COL-06] 2
    - **관찰**: 라이브 수집이 실패하면 `build_mock_items`가 **하드코딩 공고 2건**을 만들어 정상
```

#### 7건 전수 판정 — **legacy 트리를 세었다**

> **정정 (verifier H-R2)** — 이전 판의 `R-PROV-01` 행은 **"근거에 다섯 경로가 열거돼
> 있다"**로 닫았다. **그건 legacy를 센 것이 아니라 ledger 자신의 인용 목록을 센 것**이다.
> "N곳에 복제"는 **legacy 트리에 대한 주장**이므로 정본은 트리에 있다. **축이 지목한 것을
> 순환 논증으로 닫은 것**이며, 도구를 만든 라운드가 그 도구의 출력을 무력화한 형태다.
>
> **판정 근거를 전부 "legacy에서 무엇을 어떻게 셌는가"로 교체**했다. 아래 표의 `실측` 열은
> **재현 명령과 그 결과**이지 이 문서의 내용이 아니다.

| 항목 | 주장 | legacy 실측 (재현 명령) |
| --- | --- | --- |
| **R-PROV-01** | ~~"5곳에 복제"~~ → **앱 코드 6곳 · 테스트 포함 7곳** | `git grep -n -E '66(\.2)?%' ed4b06c -- '*.py'` → 앱 6 · 테스트 1. **미계상이던 두 곳**은 `app/services/prediction_dataset.py:436`(학습·데이터셋 조립 경로가 근거로 삼는다)과 `tests/test_holdout_agency_quality.py:451`(회귀 가드 사유로 복제). **산출물을 실측값으로 고쳤다** |
| R-FLOOR-05 | "사유 4종" | `git grep -c '^_SCOPE_' ed4b06c -- app/services/notice_floor_shortfall.py` → **4**(`:55-58`). 파일 전체 기준이며 인용 범위가 그 넷을 덮는다 |
| R-COL-02 | "7종" | `git -C bid-vector show ed4b06c:app/services/koneps/live_failure.py \| grep -oE 'return "[a-z_]+"' \| sort -u \| wc -l` → **정확히 7**(파일 전체). 이전 판은 경로를 `…`로 줄여 **복사 실행이 안 됐다**(verifier L-2, 형태 4) |
| R-COL-03 | "30여 개" | **세는 대상 정의에 달렸다.** `F=app/services/koneps/live_failure.py`로 두고 — 리터럴 전체 `git -C bid-vector show ed4b06c:$F \| sed -n '115,159p' \| grep -oE '"[^"]*"' \| wc -l` → **30**, 그중 **반환 카테고리명 7종을 뺀 매칭 마커** `… \| grep -vE '"(access_denied\|browser_runtime\|network\|no_data\|selector_drift\|timeout\|unknown)"' \| wc -l` → **24**(고유값도 24). **"30여 개"라는 어림은 성립하나 "정확히 30"이라고 판정할 수는 없다** — 이전 판의 그 표현을 철회한다(verifier 참고 1). 이전 판이 적은 `22~27`은 **셈 정의를 밝히지 않은 밴드**여서 함께 걷어냈다(verifier L-1) |
| R-COL-06 | "하드코딩 공고 2건" | `git -C bid-vector show ed4b06c:app/services/koneps/collection.py \| sed -n '255,315p' \| grep -c 'notice_number=f"KONEPS-'` → **2**(`-001`·`-002`). 이전 판은 파일명만 쓰고 범위를 `:255-330`으로 적었는데 **`build_mock_items`는 `:255-315`**이고 `:318`이 다음 `def`다 — ledger 본문 인용(`:255-315`)이 정확하다(verifier L-2) |
| R-COL-01 | "0건으로 파싱" | **수치 주장이 아니라 서술**이다(빈 payload). 셈의 대상이 없다 |
| R-BASIS-04 | "19.5%" | **이 문서가 `78/400`을 계산한 파생값**이며 legacy는 "400건 중 78건"만 적는다(`2610826` 본문). 그 사실이 산출물에 표시돼 있다 |

- **추가 발견 1건**(R-PROV-01) — 이전 판의 **"추가 발견 0건"은 성립하지 않는다.**
  그 전칭은 **M-N2를 닫으며 새로 쓴 문장**이었고, **축이 정확히 지목한 것을 순환 논증으로
  넘긴 결과** 0으로 적혔다. 지금은 **7건 전부 legacy 재현 명령으로 닫혔다.**
- **`19.5%`의 이력을 잘못 적었던 것도 고쳤다**(verifier L-R2-2) — 이전 판은 "이 라운드에
  `관찰`에서 `사용자 영향`으로 **옮겨져**"라고 적었으나 **텍스트는 최초 커밋
  `81a2ce3`부터 줄곧 `사용자 영향`에 있었다.** 옮겨진 것은 **스캐너의 필드 범위**다.
  재현: `for c in 81a2ce3 4db8830 21383fd 5f88973 HEAD; do git show $c:… ; done` → 전부
  `사용자 영향`.

#### 이 축이 못 보는 것 (한계)

- **방향·의미의 정합** — H2가 그 부류다. 수치가 맞아도 서술이 반대일 수 있다.
- **원문에 리터럴로 없는 파생·집계 수치** — 위 표의 대부분이 그 부류이며 **사람이 원문을
  세어** 확인했다. **그 셈의 대상 정의가 흔들리면 판정도 흔들린다**(R-COL-03이 그 예다).
- **`사용자 영향` 필드**(verifier L-R2-1) — 필드를 `관찰`로 한정하면서 **`사용자 영향`도
  함께 빠졌고 그 사실이 기록되지 않았다.** 그 필드에도 **legacy 실측이 들어 있다** —
  R-BASIS-04의 **400건 중 78건**, R-FLOOR-03의 **라이브 400건 중 1건 실측 · 최대 +10%
  과추천**. 지금은 **사람 판독으로만** 확인된다. **축을 넓힐지는 근거가 관측된 뒤에
  판단한다.**
  ※ 이전 판은 두 번째 예시로 **R-QUAL-03의 "3건 중 1건"**을 들었으나 **그 값은 `관찰`에
  있어** 스캐너가 이미 보는 자리다(verifier M-1) — **가려진 것의 예시가 될 수 없어**
  실제로 `사용자 영향`에 있는 값으로 교체했다. 자기 문서의 필드 배치라 **확인 비용이 0인
  자리에서 형태 1을 냈다.**
- **단일 숫자와 백분율 표기**는 잡되, 그 값이 원문의 다른 문맥에 우연히 있으면 통과한다.

---

## C-8. Codex 1차 대응 (2026-08-27) — 계열 A와 축의 경계

Codex 1차 판정 `request_changes` — blocker 0 / high 1 / medium 2. verdict는
`codex-review-20260827T064055Z.json`(등재, append-only).

### C-8.1 high — 계열 A. 계약이 건 여섯 형태에 없던 축이다

`R-COL-02`가 **"분류 불가(`unknown`)는 재시도 가능이 될 수 없다"**를 확정하고 `검증 방법`
에서 고정했다. 그런데 **같은 문서 `R-COL-03`과 `capability-map.md` §12가 그 정책은 활성
`OPEN-OPS-01` 소유**라고 적는다.

**0A2가 `OPS-09`에 쓴 기법을 그대로 따랐다** — 결정과 무관하게 성립하는 것과 정책에 달린
것을 **갈랐다**:

| 갈래 | 내용 |
| --- | --- |
| **결정 무관** | 실패 분류가 **응답 신호**에서 나온다 · rate limit/quota는 **1급 카테고리**이고 처리는 **backoff**다 · **재시도 가능 여부가 분류마다 선언되고 관측된다** — **값이 아니라 선언의 존재**를 고정한다(지침서의 침묵 fallback 금지에서 나오므로 **정책 결정과 무관**) |
| **조건부** | `unknown`을 재시도 가능으로 둘지는 **미결**. `OPS-09`가 fail-safe를 **결정 전 기본값**으로 쓰되 **"작성자 판단이지 확정이 아니다"**라고 명시했고 **이 문서도 그 지위를 그대로 따른다.** (a)/(b) 각각에서 `검증 방법`이 무엇으로 대체되는지도 적었다 |

`동반 OPEN` 항목을 새로 달아 `OPEN-OPS-01`의 **두 질문(측정·정책)**을 밝히고 **둘 다 이
문서가 결정하지 않음**을 명시했다. **`OPEN-OPS-01`을 해소하지 않았다.**

### C-8.2 계열 A 전수 훑기 — 축을 하나 더 만들었다

**기존 축 다섯은 이 부류를 보지 않는다.** 전부 **근거의 지위**(확인했는가 · 실행했는가 ·
전파했는가 · 셌는가)를 보고, 계열 A는 **미결을 확정으로 쓰는가**라는 다른 축이다.

**두 겹으로 짰다.** 1차는 결정 필드가 **활성 OPEN id를 언급**하는 자리를 보고, 2차는
**id를 언급하지 않는 선점**을 활성 OPEN의 `결정 필요 사항` 특징어로 찾는다 —
**`R-COL-02`가 정확히 1차를 빠져나갔기 때문**이다(그 항목은 `OPEN-OPS-01`을 아예 적지
않았다).

```python
# 계열 A 스윕 — 활성 OPEN이 소유한 쟁점을 `V2 예방 제약`·`검증 방법`이 확정하는가.
# 정본: capability-map.md §12의 활성 registry + ledger §9의 OPEN-REG.
# 기계가 판정할 수 없는 것: "그 문장이 그 OPEN의 분기를 선점하는가". 지목만 하고 사람이 읽는다.
import re, collections, sys
CM = "docs/discovery/capability-map.md"
LGP = "docs/discovery/regression-ledger.md"
def load(a):
    """인자가 git ref 면 그 커밋의 ledger 를 본다 — 수정 전 트리에서 유효성을 실증한다."""
    if not a: return open(LGP, encoding="utf-8").read()
    if re.fullmatch(r'[0-9a-f]{7,40}', a):
        import subprocess
        return subprocess.run(["git","show",f"{a}:{LGP}"],capture_output=True,text=True,check=True).stdout
    return open(a, encoding="utf-8").read()


cm = open(CM, encoding="utf-8").read()
active = set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|', cm, re.M))
lg = load(sys.argv[1] if len(sys.argv) > 1 else None)
active |= set(re.findall(r'\*\*(OPEN-REG-\d+)\*\*', lg))
print(f"활성 OPEN 정본: capability-map §12 {len(active - set(re.findall(r'OPEN-REG-\d+', lg)))}건"
      f" + OPEN-REG {len(set(re.findall(r'OPEN-REG-\d+', lg)))}건")

lines = lg.split("\n")
starts = [i for i, l in enumerate(lines) if re.match(r'^### [RP]-', l)]
DECIDE = ("V2 예방 제약", "검증 방법")
hits = []
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    eid = re.match(r'^### ([RP]-[A-Z]+-\d+)', lines[st]).group(1)
    field = None
    for k in range(st, e):
        l = lines[k]
        m = re.match(r'^- \*\*([^*]+)\*\*', l)
        if m: field = m.group(1).split("(")[0].split(" —")[0].strip()
        if field not in DECIDE: continue
        for o in set(re.findall(r'OPEN-[A-Z]+-\d+', l)):
            if o in active:
                cond = bool(re.search(r'조건부|미정|소유|결정하지 않는다|판단이지|결정에 따라', l))
                hits.append((eid, field, o, k+1, cond, l.strip()[:95]))
print(f"\n=== 결정 필드가 활성 OPEN을 언급하는 자리: {len(hits)}건 ===")
for eid, f, o, ln, cond, l in hits:
    print(f"  [{eid}] {f} → {o}  {'조건부 표시 있음' if cond else '★ 확정 서술 의심'}  :{ln}\n      {l}")


# ── 2차: **id를 언급하지 않는** 선점 후보 (R-COL-02가 그렇게 새어 나갔다) ──
# 활성 OPEN의 `결정 필요 사항`에서 특징어를 뽑아 결정 필드와 대조한다.
# 어휘 매칭이라 오탐이 많다 — **지목만 하고 사람이 읽는다.**
rows = re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|([^|]*)\|', cm, re.M)
# **`OPEN-REG`는 ledger §9에 산다.** 정본이 다른 파일이라 2차가 통째로 못 봤고
# 사각지대 목록에서도 빠져 있었다 — Codex 2차 medium #1이 그 구멍으로 새어 나갔다.
rows += re.findall(r'^\|\s*\*\*(OPEN-REG-\d+)\*\*\s*\|([^|]*)\|', lg, re.M)
STOP = set("결정 필요 사항 여부 것인가 무엇 어떤 할 를 을 이 가 의 와 과 에 로 는 은 수 그 이번 V2 legacy".split())
topics = {}
for oid, q in rows:
    # **숫자 리터럴도 특징어다** — `OPEN-DEC-07`(마진 `0.05`, 비율 `1.15`)이 어휘만으로는
    # 하나도 안 걸려 F-1이 두 겹을 다 빠져나갔다.
    terms = {t for t in re.findall(r'[A-Za-z_][A-Za-z0-9_]{4,}|[가-힣]{3,}|\d+\.\d+', q)
             if t not in STOP}
    if terms: topics[oid] = terms

named = {(eid, o) for eid, _, o, _, _, _ in hits}
cands = collections.defaultdict(set)
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    eid = re.match(r'^### ([RP]-[A-Z]+-\d+)', lines[st]).group(1)
    field = None
    for k in range(st, e):
        l = lines[k]
        m = re.match(r'^- \*\*([^*]+)\*\*', l)
        if m: field = m.group(1).split("(")[0].split(" —")[0].strip()
        if field not in DECIDE: continue
        for oid, terms in topics.items():
            hit = terms & set(re.findall(r'[A-Za-z_][A-Za-z0-9_]{4,}|[가-힣]{3,}', l))
            if len(hit) >= 2 and (eid, oid) not in named:
                cands[(eid, oid)] |= hit
# 숫자 리터럴은 **하나만 겹쳐도** 지목한다(어휘와 달리 우연 일치가 드물다).
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    eid = re.match(r'^### ([RP]-[A-Z]+-\d+)', lines[st]).group(1)
    field = None
    for k in range(st, e):
        l = lines[k]
        m = re.match(r'^- \*\*([^*]+)\*\*', l)
        if m: field = m.group(1).split("(")[0].split(" —")[0].strip()
        if field not in DECIDE: continue
        nums = set(re.findall(r'\d+\.\d+', l))
        for oid, terms in topics.items():
            hit = {t for t in terms if re.match(r'\d+\.\d+$', t)} & nums
            if hit and (eid, oid) not in named: cands[(eid, oid)] |= hit
print(f"\n=== id 없이 주제가 겹치는 후보: {len(cands)}건 (오탐 다수 — 사람 판정) ===")
for (eid, oid), terms in sorted(cands.items()):
    print(f"  [{eid}] ~ {oid}  공통어: {sorted(terms)[:5]}")

# ── 사각지대 명시: 특징어를 뽑을 수 없어 **2차가 볼 수 없는** 활성 OPEN ──
weak = sorted(o for o in active if len(topics.get(o, set())) < 2)
print(f"\n=== 2차가 매칭할 수 없는 활성 OPEN: {len(weak)}건 (특징어 2개 미만) ===")
for o in weak:
    q = next((qq.strip() for oo, qq in rows if oo == o), "")
    print(f"  {o}: 특징어 {sorted(topics.get(o, set()))} | 질문 {q[:60]}")
print("  → 이 항목들은 **사람이 읽어 판정**한다. 기록은 evidence C-8.2.")


# ── 3차: **사실 주장 필드의 방향·대소 주장**이 미결에 기대는가 (Codex 2차 medium #1) ──
# 1·2차는 `V2 예방 제약`·`검증 방법`만 본다. medium #1 은 **`사용자 영향`의 방향 주장**이
# 미결(`OPEN-REG-05`)에 기댄 것이라 **필드 범위 밖**이었다.
# 관측된 형태를 그대로 겨눈다: **방향·대소를 말하는 문장은 대소 관계를 전제한다.**
# 그 항목이 활성 OPEN과 엮여 있으면 그 전제가 미결일 수 있다 — **지목만 하고 사람이 읽는다.**
FACT  = ("관찰", "사용자 영향")
# `~10% 낮은` 이 어휘에 없어 `R-BASIS-06` 이 새어 나갔다(verifier F-3). **관측된 뒤에** 넓힌다 —
# 넓히면 지목이 늘고 검사가 늘어난다(줄이는 방향이 아니다).
DIR   = re.compile(r'한 방향|일관되게|더 크|더 작|보다 크|보다 작|상회|하회|넘는|초과하|미만'
                   r'|낮은|높은|낮아|높아|적은|많은'
                   # 증감 어휘도 방향이다 — `부풀었다` 가 없어 `R-BASIS-03` 이 또 새어
                   # 나갔다(verifier F-b). 한 낱말씩 메우는 대신 **부류로** 넣는다.
                   r'|부풀|줄어|늘어|커지|작아|과대|과소|올라가|내려가|깎|치솟')
HEDGE = re.compile(r'조건부|미정|소유한다|확정하지 않는다|판정할 수 없다|정하지 않는다'
                   r'|결정에 따라|단정할 수 없다|전제를 넣지 않는다')
f3 = []
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    eid = re.match(r'^### ([RP]-[A-Z]+-\d+)', lines[st]).group(1)
    blk = "\n".join(lines[st:e])
    # 이 항목과 엮인 활성 OPEN: 문면에 이름이 있거나 2차 주제가 겹치는 것
    tied = {o for o in active if o in blk}
    # 특징어는 **조사가 붙은 채** 뽑힌다(`기초금액과`·`추정가격의`). 그대로 대조하면
    # 같은 낱말을 쓴 항목도 안 걸린다 — `R-BASIS-03` 이 그렇게 새어 나갔다(verifier F-b).
    # 한글 특징어는 **어간(끝 한 글자를 뗀 형태)이 블록에 있으면** 겹친 것으로 본다.
    def covers(t):
        if t in blk: return True
        return len(t) >= 4 and re.fullmatch(r'[가-힣]+', t) is not None and t[:-1] in blk
    tied |= {o for o, ts in topics.items()
             if o in active and sum(1 for t in ts if covers(t)) >= 2}
    if not tied: continue
    field = None
    for k in range(st, e):
        l = lines[k]
        m = re.match(r'^- \*\*([^*]+)\*\*', l)
        if m: field = m.group(1).split("(")[0].split(" —")[0].strip()
        if field not in FACT or not DIR.search(l): continue
        f3.append((eid, field, k+1, bool(HEDGE.search(l)), sorted(tied), l.strip()[:88]))
print(f"\n=== 3차: 사실 주장 필드의 방향·대소 주장: {len(f3)}건 ===")
for eid, fl, ln, hg, tied, l in f3:
    print(f"  [{eid}] {fl} :{ln}  {'유보 표시 있음' if hg else '★ 전제 확인 필요'}"
          f"  엮인 활성 OPEN: {tied[:4]}\n      {l}")
print("  → 방향을 말하려면 대소 관계가 정해져 있어야 한다. **사람이 읽어 판정**한다.")
```

```
$ python3 openstance.py
활성 OPEN 정본: capability-map §12 45건 + OPEN-REG 5건

=== 결정 필드가 활성 OPEN을 언급하는 자리: 7건 ===
  [R-BASIS-01] 검증 방법 → OPEN-REG-05  조건부 표시 있음  :100
      - **조건부 — `OPEN-REG-05` 결정에 따라 확정.** **과세/비과세로 정의된 경계 쌍은 그 OPEN이
  [R-BASIS-04] 검증 방법 → OPEN-REG-05  조건부 표시 있음  :175
      공시하며, 과세 의미의 확정은 활성 `OPEN-REG-05`가 소유한다. **값 조건으로 고르면 그
  [R-PROV-02] 검증 방법 → OPEN-DEC-07  조건부 표시 있음  :380
      - **조건부 — `OPEN-DEC-07` 결정에 따라 확정.** legacy의 임계 **1.15**(부가세 1.10 +
  [R-COL-02] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :836
      - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** **분류 불가(`unknown`)를
  [R-COL-02] 검증 방법 → OPEN-OPS-01  조건부 표시 있음  :845
      - **조건부 — `OPEN-OPS-01` 결정에 따라 확정.** (b) fail-safe면 "`unknown`이 재시도
  [R-COL-03] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :866
      ※ 재시도 정책 자체(`unknown → retryable` 여부)는 **활성 `OPEN-OPS-01`이 소유**하며
  [R-ASYNC-01] V2 예방 제약 → OPEN-OPS-10  조건부 표시 있음  :981
      ※ DB 기반 큐의 backlog 관측·가시성 timeout 계약은 **활성 `OPEN-OPS-10`이 소유**하며

=== id 없이 주제가 겹치는 후보: 2건 (오탐 다수 — 사람 판정) ===
  [R-BASIS-01] ~ OPEN-QUAL-10  공통어: ['basis', '운영자']
  [R-COL-01] ~ OPEN-ML-05  공통어: ['policy', 'versioned']

=== 2차가 매칭할 수 없는 활성 OPEN: 8건 (특징어 2개 미만) ===
  OPEN-COL-03: 특징어 ['업무구분'] | 질문 업무구분 코드 전체 체계(현재 4개만 매핑)
  OPEN-COL-05: 특징어 ['limit'] | 질문 외부 API rate limit의 실제 수치
  OPEN-NOTI-06: 특징어 ['초기값'] | 질문 알림 임계 4종의 초기값
  OPEN-NUM-02: 특징어 ['unknown'] | 질문 자격 관련 수치 5종(92% unknown 포함)
  OPEN-OPS-03: 특징어 [] | 질문 큐 깊이 검출 SLO (**임계 미결**)
  OPEN-REG-04: 특징어 ['측정일'] | 질문 **"66% 오염"의 측정 방법·모수·측정일**
  OPEN-SET-01: 특징어 ['페이퍼'] | 질문 페이퍼 정산 근접 임계 3종의 도출 근거
  OPEN-SET-05: 특징어 ['재공고'] | 질문 재공고(차수 다수) 대사 대상 선택 규칙
  → 이 항목들은 **사람이 읽어 판정**한다. 기록은 evidence C-8.2.

=== 3차: 사실 주장 필드의 방향·대소 주장: 6건 ===
  [R-BASIS-03] 관찰 :136  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-QUAL-10', 'OPEN-REG-05']
      rate x 1.1 -> 1.0 clamp로 부풀어, 같은 공고가 어느 경로로 평가됐는지에 따라
  [R-BASIS-04] 관찰 :159  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-05']
      기초금액-relative `price_range`를 자르면서 **하한까지 깎았다**(`price_lower = min(price_lower, budget_ca
  [R-BASIS-06] 관찰 :203  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-ML-02', 'OPEN-QUAL-10', 'OPEN-REG-05']
      ~10% 낮은 투찰가가 나온다 — 이 저장소 실투찰 실격 이력과 같은 계열의 실패다."*
  [R-RATE-01] 관찰 :248  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-03']
      - **관찰**: "값이 임계치보다 크면 백분율로 보고 `/100`" 규칙과 그 임계치가 **7곳에 독립
  [R-QUAL-06] 관찰 :762  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-ML-02']
      기반으로 교체하고 다중 그룹에서는 **누락이 가장 적은 그룹**을 대표로 쓴 것이다.
  [R-ML-04] 관찰 :1234  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-SET-06']
      **"측정할 수 없는 편향은 없다고 주장할 수 없으므로 회피한다"** — 성숙도 임계 미만 구간
  → 방향을 말하려면 대소 관계가 정해져 있어야 한다. **사람이 읽어 판정**한다.
```

> **정정 (verifier F-1) — 이 절의 "추가 발견 0건"은 참이 아니었다.**
> **계열 A 실물이 하나 더 있었다** — `OPEN-DEC-07`(마진 `0.05` 재유도 대기, **활성 ·
> M1 차단**)의 값을 `R-PROV-02`의 `검증 방법`과 **§10 fixture-curator 인계**가 조건 표시
> 없이 고정했다. **둘 다 고쳤다**(결정 무관 = 판정 **순서** / 조건부 = 경계 **값**).
>
> **축이 두 겹 다 못 봤다** — 1차는 그 두 줄이 `OPEN-DEC-07`을 적지 않아서, 2차는 그
> OPEN의 `결정 필요 사항`("기준 금액 신뢰 비율 **1.15**의 마진 **0.05** 값")에 **어휘
> 토큰이 하나도 없어서**다(전부 숫자다). **2차에 숫자 리터럴 대조를 넣었다** — 숫자는
> 어휘와 달리 우연 일치가 드물어 **하나만 겹쳐도 지목**한다.

**판정 — 아래는 축을 고친 뒤의 결과다.** 2차가 남긴 후보 둘은 **어휘만 겹치는 오탐**이다:

| 후보 | 판정 |
| --- | --- |
| `R-BASIS-01` ~ `OPEN-QUAL-10` | **오탐 — 다른 금액 축.** `OPEN-QUAL-10`은 **시공능력평가금액**(QUAL 축)의 unit·과세이고, R-BASIS-01의 제약은 **예산 필터 basis**로 운영자 결정 `OPEN-STR-01`(확정)에 선다. 라운드 4에 두 금액의 **차이 크기**를 `OPEN-QUAL-10` 소유로 넘긴 것은 **참조이지 선점이 아니다** |
| `R-COL-01` ~ `OPEN-ML-05` | **오탐 — 다른 정책 영역.** `OPEN-ML-05`는 **predictor 정책 값 33개**의 versioned policy 경계이고, R-COL-01은 **KONEPS `resultCode`**의 versioned policy로 운영자 결정 `OPEN-COL-02`(확정)에 선다. `versioned`·`policy` 두 낱말만 겹친다 |

1차가 지목한 `R-COL-03`·`R-ASYNC-01`은 **이미 "활성 OPEN이 소유한다"고 적고 있어** 선점이
아니다(축이 그렇게 표시한다).

#### 축의 사각지대 — 특징어를 뽑을 수 없는 활성 OPEN (verifier F-1 · F-4)

숫자 대조를 넣어도 **`결정 필요 사항`이 짧아 특징어가 2개 미만인 활성 OPEN**은 2차가
매칭하지 못한다. **그 목록을 축이 직접 찍게 했고**(위 출력 마지막 절) **전부 사람이 읽어
판정했다.** **셈은 여기 옮겨 적지 않는다 — 그 출력이 찍는다**(형태 6).

| OPEN | 질문 | 판정 |
| --- | --- | --- |
| `OPEN-COL-03` | 업무구분 코드 전체 체계 | ledger 결정 필드에 **업무구분 코드가 없다** |
| `OPEN-COL-05` | 외부 API rate limit의 **실제 수치** | `R-COL-02`가 "rate limit/quota는 1급 카테고리, 처리는 backoff"라고만 적는다 — **수치를 고정하지 않는다** |
| `OPEN-NOTI-06` | 알림 임계 4종의 초기값 | ledger에 **알림 임계가 없다** |
| `OPEN-NUM-02` | 자격 관련 수치 5종 | `R-QUAL-03`의 "3건 중 1건"은 **legacy 관찰**이고 V2 요구가 아니다 |
| `OPEN-REG-04` | **"66% 오염"의 측정 방법·모수·측정일** | ledger는 그 수치를 **사실로 승격하지 않는다** — §10.3이 `OPEN-NUM-01`과 같은 쟁점임을 적고 **승격 금지**를 유지하며, §10.4가 출처 포인터의 진전이 **해소 근거가 아님**을 명시한다. **선점 없음** (verifier F-4 — 이번 라운드의 `OPEN-REG` 제외 제거로 **새로 드러난 자리**다) |
| `OPEN-OPS-03` | 큐 깊이 검출 SLO **임계** | `R-ASYNC-01`·`R-ASYNC-08`은 **무엇을 재는가**(큐 깊이·마지막 진행 시각)를 요구할 뿐 **임계값을 고정하지 않는다.** `R-ASYNC-01`은 DB 큐 계약을 `OPEN-OPS-10` 소유로 이미 넘긴다 |
| `OPEN-SET-01` | 페이퍼 정산 근접 임계 3종 | ledger에 **정산 근접 임계가 없다** |
| `OPEN-SET-05` | 재공고 대사 대상 선택 규칙 | ledger에 **재공고 대사가 없다** |

**전부 선점 없음.** 판정은 각 OPEN의 핵심어로 결정 필드를 훑어(`임계`·`rate limit`·
`업무구분` 등) 걸린 줄을 읽는 방식으로 했고, **`임계`처럼 흔한 낱말은 오탐이 많아 전부 열어
읽었다.**

**이 목록은 축의 출력에 남는다** — 다음 라운드가 같은 판정을 다시 하지 않아도 되고,
활성 OPEN이 바뀌면 목록도 바뀐다.

### C-8.3 medium #1 — 축이 1줄 느슨했다

`citecheck`가 파일 길이를 `len(stdout.split("\n"))`으로 계산해 **trailing newline 때문에
실제보다 1 컸다.** `splitlines()`로 고쳤다.

> **정정 (verifier F-0, blocker) — 앞 라운드는 고치지 않고 고쳤다고 적었다.**
> `splitlines()` 치환이 **산문에만** 들어갔고 **인라인된 스크립트 본문은 그대로**였다
> (`2619974`의 같은 줄과 바이트 동일). **evidence가 도구의 상태를 사실과 다르게
> 말한 것**이라 그 위에 쌓인 "행 범위 초과 0"의 근거가 흔들렸다.
> **§10.1 형태 6의 정면 위반**이며 — `"고쳤다"는 진술도 전칭이다. 적기 전에 그 지점을
> 열어 확인한다` — **내가 앞 라운드에 세운 규칙이다.**
>
> **원인은 resync 루프가 「출력」만 갱신하고 「본문」은 갱신하지 않은 것**이다. 이번에
> **본문 동기화를 그 루프에 넣었고**(`resync.sh`), **인라인된 스크립트를 실물과 전수
> 대조**했다 — **어긋난 것은 `citecheck` 하나뿐**이고 나머지는 **바이트 동일**이었다.
> (이 자리에 적었던 **"여섯"**은 같은 라운드에 `slack.py`가 인라인되며 낡았다 —
> **셈은 C-9.8의 `resync.sh`가 매 실행에 찍는다**. C-9.7과 같은 처리다.)

**변이 검사로 고침을 실증했다** — `eligibility.py:53-97`을 `:53-98`(파일 끝 +1)로 바꾸면
**현행 본문은 `초과 1`로 적출**하고 **옛 본문은 `초과 0`으로 통과**시킨다.

> **정정 (verifier M-C2)** — 이 자리는 원래 **손으로 옮겨 적은 출력**이었고 그 안의 ledger
> 행 번호가 **두 라운드 연속 낡았다**(편집으로 밀릴 때마다). 실행 키가 없어 `resync`가
> 덮지 않았고 펜스 안이라 `claimcheck`도 보지 않는 **두 축 모두의 사각지대**였다.
> **실행 키를 갖는 블록으로 바꿨다** — 이제 L-1의 유도 루프가 자동으로 덮고
> **행 번호는 아래 블록이 매 실행에 다시 찍는다.**

스크립트 본문(형태 4: 인라인):

```python
# 변이 검사 — `citecheck` 의 경계 수정(splitlines)이 실제로 잡는가.
# 손으로 옮겨 적은 수가 두 라운드 연속 낡았으므로(754 → 760 → 765) **실행 키를 갖는
# 블록**으로 바꾼다. 이제 resync 가 매 실행에 이 출력을 다시 뜬다.
import sys, os
# 스크래치패드에 `grp.py` 같은 이름이 있으면 sys.path[0] 이 표준 모듈을 가린다
# (`tempfile` → `shutil` → `grp`). 자기 디렉터리를 경로에서 빼고 시작한다.
SP = os.path.dirname(os.path.abspath(__file__))
sys.path[:] = [q for q in sys.path if os.path.abspath(q or ".") != SP]
import pathlib, re, subprocess, tempfile
LEDGER = "docs/discovery/regression-ledger.md"
CITE = pathlib.Path(f"{SP}/citecheck.py").read_text(encoding="utf-8")
MUT = ("app/services/classification/eligibility.py:53-97",
       "app/services/classification/eligibility.py:53-98")   # 파일 끝 +1

src = pathlib.Path(LEDGER).read_text(encoding="utf-8")
n = src.count(MUT[0])
print(f"변이 대상: {MUT[0]} → {MUT[1]}  (ledger 안 출현 {n}회)")
if n != 1:
    print("  대상이 유일하지 않다 — 검사를 중단한다."); sys.exit(1)

d = tempfile.mkdtemp()
mut = os.path.join(d, "mutant.md")
pathlib.Path(mut).write_text(src.replace(*MUT), encoding="utf-8")

def run(body, tag):
    p = os.path.join(d, tag + ".py")
    pathlib.Path(p).write_text(body, encoding="utf-8")
    out = subprocess.run(["python3", p, mut], capture_output=True, text=True).stdout
    for ln in out.split("\n"):
        if "행 범위" in ln or "[초과]" in ln:
            print(f"  {tag:22s} {ln.strip()}")

old = CITE.replace('.splitlines()', '.split("\\n")')
assert old != CITE, "옛 판을 만들 수 없다 — splitlines() 가 본문에 없다"
run(CITE, "현행(splitlines)")
run(old,  "옛 판(split)")
print("  → 현행은 초과 1로 적출하고 옛 판은 초과 0으로 통과시킨다.")
```

```
$ python3 mutcheck.py
변이 대상: app/services/classification/eligibility.py:53-97 → app/services/classification/eligibility.py:53-98  (ledger 안 출현 1회)
  현행(splitlines)         --- v2 저장소 파일(legacy 검사 대상 아님): 7종 · 행 범위 초과 0 ---
  현행(splitlines)         --- 행 범위: 파일 길이 내 103 / 초과 1 ---
  현행(splitlines)         [초과] app/services/classification/eligibility.py:53-98 (파일 97줄)  ← [('R-QUAL-07', 789)]
  옛 판(split)             --- v2 저장소 파일(legacy 검사 대상 아님): 7종 · 행 범위 초과 0 ---
  옛 판(split)             --- 행 범위: 파일 길이 내 104 / 초과 0 ---
  → 현행은 초과 1로 적출하고 옛 판은 초과 0으로 통과시킨다.
```

**그 틈을 실제로 쓴 인용이 있었는지 재 봤다** — 인용 끝과 파일 끝의 여유를 전수로
계산한다. **스크립트 본문**(형태 4 — verifier F-3이 지적해 인라인했다):

```python
# 인용 행 범위 끝과 파일 끝의 여유 — 축이 1줄 느슨했던 동안 그 틈을 쓴 인용이 있었는가.
import re, subprocess
REPO, REF = "bid-vector", "ed4b06c"
src = open("docs/discovery/regression-ledger.md", encoding="utf-8").read()
PATH = re.compile(r'`([A-Za-z0-9_./-]+\.(?:py|md|toml|cfg|yaml|yml|json|txt))'
                  r'(?::([\d,\-]+))?(?:::[A-Za-z_][A-Za-z0-9_]*)?`')
tree = set(subprocess.run(["git","-C",REPO,"ls-tree","-r","--name-only",REF],
                          capture_output=True, text=True).stdout.split())
cache = {}
def body(p):
    if p not in cache:
        cache[p] = subprocess.run(["git","-C",REPO,"show",f"{REF}:{p}"],
                                  capture_output=True, text=True).stdout.splitlines()
    return cache[p]
worst = []
for m in PATH.finditer(src):
    p, rng = m.group(1), m.group(2)
    if p not in tree or not rng: continue
    n = len(body(p))
    for part in rng.split(","):
        hi = int(part.split("-")[-1])
        worst.append((n - hi, p, part, n))
worst.sort()
print("행 범위 끝과 파일 끝의 여유(작을수록 경계에 가깝다) — 하위 6건:")
for slack, p, part, n in worst[:6]:
    print(f"  여유 {slack:>5}  {p}:{part}  (파일 {n}줄)")
print(f"\n음수(=초과) 건수: {sum(1 for s,_,_,_ in worst if s < 0)}")
print(f"여유 0(=마지막 줄까지 인용) 건수: {sum(1 for s,_,_,_ in worst if s == 0)}")
```

```
$ python3 slack.py
행 범위 끝과 파일 끝의 여유(작을수록 경계에 가깝다) — 하위 6건:
  여유     0  app/services/classification/eligibility.py:53-97  (파일 97줄)
  여유     0  app/services/classification/text.py:108-117  (파일 117줄)
  여유     0  app/services/license_eligibility.py:447-511  (파일 511줄)
  여유     2  app/services/allocation_core.py:133-179  (파일 181줄)
  여유     4  app/core/constants.py:445  (파일 449줄)
  여유     4  app/core/inference_config.py:114-124  (파일 128줄)

음수(=초과) 건수: 0
여유 0(=마지막 줄까지 인용) 건수: 3
```

- **초과 0.** 느슨한 동안에도 **그 틈을 쓴 인용은 없었다.**
- **다만 세 인용이 정확히 마지막 줄에서 끝난다.** 셋 다 파일이 실제로 거기서 끝나는 것을
  `tail -2`로 확인했다 — **고친 축은 이 경계를 이제 정확히 잡는다.**

**다른 세 축의 같은 부류 점검**: `ledgercheck`·`numsrc`·`prosecheck`도 `split("\n")`을
쓰지만 **모두 줄을 순회하는 용도**이고 **길이를 경계로 쓰지 않는다** — 끝에 붙는 빈 원소는
어떤 패턴에도 걸리지 않는다. `numsrc`가 legacy 본문을 `b[lo-1:hi]`로 자르는 곳은
**슬라이스라 범위를 넘어도 잘려 나갈 뿐** 잘못된 결과를 만들지 않는다. **경계 오차 없음.**

### C-8.4 medium #2 — `prosecheck`가 이 부류를 보지 못하는 이유

`checklist.md`와 `scope.md`가 선행 조사 표 불일치의 원인을 **"전부 두 축의 혼동"**으로
요약했는데, `commands.md` C-5.2는 라운드 3에 그것을 **"계열 6 한 칸만 그렇고 3·4·8은 단순
오계수"**로 좁혔다. **두 곳에 전파되지 않았다**(형태 5). 둘 다 고쳤다.

**`prosecheck`는 이 부류를 설계상 보지 않는다.** 그 축의 정본은 **출력 블록에서 뽑은 명명된
수치**이고 대조 대상은 **산문이 옮겨 적은 그 수**다. 이번 것은 **수가 아니라 판정 요약**이며
**출력 블록에 정본이 없다** — 어느 명령도 "이 요약이 저 절의 판정과 맞는가"를 계산하지
못한다.

**축을 늘리지 않았다.** 계산할 정본이 없으면 도구를 만들 수 없고, 이 부류의 처방은 이미
표에 있다 — **형태 5(정정을 인용 지점에 전파하지 않기)**다. 판정을 좁혔으면 **그 판정을
요약한 자리를 전수로 찾는 것**이 규칙이고, 라운드 3이 그것을 하지 않았다. **한계로 적고
규칙을 다시 가리킨다.**

### C-8.6 형태 6 재발 — 산문을 고치고 대상을 안 고쳤다 (verifier F-0, blocker)

**§10.1 형태 6의 재발이며 새 형태가 아니다.** 그 형태의 규칙은
**"'고쳤다'는 진술도 전칭이다 — 적기 전에 그 지점을 열어 확인한다"**이고,
**내가 앞 라운드에 세운 규칙**이다.

**이번 모양은 "산문을 고치고 대상을 안 고쳤다"**다. 앞선 재발들이 *대상을 고치고
인용 지점을 놓친* 형태(형태 5 계열)였다면, 이번은 **반대 방향**이다 — 세 파일이
"고쳤다"고 적는 동안 **고쳐야 할 스크립트 본문은 손대지 않았다.**

**원인은 도구다.** resync 루프가 **「출력」만 갱신하고 「본문」은 갱신하지 않았다** —
스크래치패드의 실물은 고쳐져 있었고 **그것으로 실행한 출력도 맞았지만**, evidence에
인라인된 본문만 옛 상태로 남았다. 그래서 **누가 evidence의 본문을 그대로 돌리면 느슨한
검사가 나온다.**

**처방**: **본문 동기화를 resync에 넣었다.** 그리고 인라인된 스크립트를 실물과
**전수 대조**해 어긋난 것이 `citecheck` 하나뿐임을 확인했다 — 나머지는 바이트 동일.
**"고쳤다"고 적을 때 그 대상이 파일이면 그 파일의 diff를 본다.**

> **정정 (C-9.7)** — 이 자리에 적었던 **"6종"**은 같은 라운드에 `slack.py`가 인라인되며
> 낡았다(형태 5). **셈을 고쳐 적지 않고 세는 도구가 세게 했다** — C-9.8의 `resync.sh`가
> 매 실행에서 블록 수·차지한 수·발췌 수·불일치 수를 찍는다.

### C-8.5 §10.1 판단 — 계열 A는 **일곱 번째 형태로 추가할 만하다**(단 그 파일은 범위 밖)

**추가하는 쪽이 맞다고 판단한다.** 근거 넷:

1. **기존 여섯은 전부 "근거의 지위" 축**이다 — 확인했는가(1·2) · 실행했는가(3·4) ·
   전파했는가(5) · 셌는가(6). 계열 A는 **"미결을 확정으로 쓰는가"**로 **다른 축**이다.
2. **여섯을 다 지켜도 발생한다.** legacy를 직접 열고, 명령을 실행하고, 전파하고, 재현
   명령으로 셈을 대신해도 **활성 OPEN의 분기를 선점할 수 있다.** `R-COL-02`가 그 실물이다.
3. **0B 계약이 여섯 형태를 처음부터 걸었는데도 났다.** 표가 이 부류를 덮지 못한다는
   **직접 증거**다.
4. **고유한 적출 방법과 처방이 있다** — 축은 C-8.2, 처방은 **결정 무관/조건부 분리**
   (`OPS-09` 기법)다. 기존 여섯 중 어느 것도 이 처방을 주지 않는다.

**앞서 두 번은 새 형태를 만들지 않는 쪽이 옳았다**(형태 2로 흡수, 형태 5의 절반 복원).
그때는 **뿌리가 기존 형태와 같았고** 지금은 다르다.

**다만 §10.1은 `reports/evidence/m0/0a2/checklist.md`에 있고 0B의 out_of_scope다.**
이 slice는 **판단만 기록하고 그 파일을 편집하지 않는다** — 표를 고치려면 별도 slice 계약이
필요하다. **0B는 그 사이 계열 A 축(C-8.2)을 자기 evidence에 두고 돌린다.**

---

## C-9. 주장 대조 축 — "했다"가 산출물에 있는가 (verifier F-2 · F-4 · checklist A4)

### C-9.1 왜 이 축을 만들었는가 — 같은 결함이 **네 번** 관측됐다

| # | 라운드 | evidence가 서술한 수정 | 산출물의 실제 상태 |
| --- | --- | --- | --- |
| 1 | Codex 1차 | `citecheck`의 `splitlines()` 치환 | **인라인 본문 미수정**(verifier F-0, blocker) |
| 2 | F-0 대응 | `R-BASIS-01`의 귀속을 `OPEN-REG-05`로 | **`:80`은 `OPEN-QUAL-10` 그대로** — ledger가 `:1305`와 **자기 모순** |
| 3 | F-0 대응 | `동반 OPEN`을 §0.1 필드 표에 등재 | **표에 행이 없음** |
| 4 | F-0 대응 | checklist A4 "둘 다 `동반 OPEN`을 달았다" | **`R-PROV-02`에 그 필드가 없음** |

**F-0의 처방은 이 부류의 일부만 덮었다.** 본문 동기화를 resync에 넣은 것은
**「인라인 스크립트 본문」**을 대조하게 했을 뿐이고, **산출물 편집을 주장하는 산문은
여전히 아무것도 대조하지 않았다.** 결함이 네 번 관측됐으므로
**"필요가 관측되기 전에 도구를 늘리지 마라"**의 조건은 충족됐다.

### C-9.2 정의 — 무엇을 정본으로 삼는가

| | |
| --- | --- |
| **정본 (변경)** | `git diff ec115a7 <head> -- docs/discovery/regression-ledger.md`의 **추가 줄** |
| **정본 (상태)** | `<head>`의 산출물 본문 |
| **주장** | evidence 3파일에서 **완료 동사**(`고쳤`·`등재했`·`달았`·`교체했`·`추가했`·`제거했`·`걷어냈`·`넣었`·`갈랐`·`등록했`·`인라인했`·`옮겼`·`없앴`·`맞췄`·`좁혔`·`넓혔`·`정정했`·`반영했`·`삭제했`·`바꿨`·`붙였`·`보강했`)를 담은 단위 |
| **주장의 단위** | **문장이 아니라 블록**(bullet·문단). F-2가 그 이유다 — 대상(`R-BASIS-01`)이 앞 문장에 있고 완료 동사(`등록했다`)가 뒤 문장에 있었다 |
| **위치** | `§N`·`§N.N`(**포함 관계** — "§10 인계"는 §10.1의 내용으로 충족된다) 또는 항목 id(`R-BASIS-01`) |
| **대상 토큰** | `OPEN-*` id + **산출물이 스스로 쓰는 필드명**. 필드 어휘는 손으로 적지 않고 ledger의 `- **X**:` 라벨을 훑어 만든다 — **정본은 산출물이다** |
| **판정** | 대상 토큰이 그 위치에 **없으면 지목**. 있는데 range의 추가 줄이 아니면 참고 |

**축을 만들며 축 자신의 결함 셋을 고쳤다.** ① 마크다운 강조가 한국어 동사를 가른다
(`등록**했다`)라 정규식 전에 `*`·`` ` ``·`>`를 걷어낸다 — **F-2가 그것 때문에 안 잡혔다.**
② **부정형은 강등이 아니라 표시**다. 처음엔 블록에 부정 어휘가 있으면 지목을 내렸는데,
그러면 한 문장의 "…하지 않았다"가 **블록의 모든 주장을 가린다**(checklist A4가 정확히
그렇게 숨었다). ③ **펜스 블록은 주장이 아니다** — 스크립트 본문과 명령 출력을 건너뛴다.
넣지 않으면 **이 축이 자기 출력을 주장으로 읽어 자기 출력에 폭주한다.**
**수를 적지 않는다** — 폭주하던 판은 폐기된 중간판이라 **그 수를 내는 명령이 남아 있지
않다**(verifier L-C7). **재현할 수 없는 수는 적지 않는다**(형태 4·6).

**셋 다 "고쳤다"이므로 대상은 인라인 본문이고, 그 본문은 C-9.8의 `resync.sh`가 매 실행에
실물과 대조한다**(`불일치 0`). **이 축의 주장은 이 축이 아니라 그 루프가 닫는다.**

### C-9.3 스크립트 본문 (형태 4: 인라인)

```python
#!/usr/bin/env python3
# claimcheck — evidence 의 "완료 주장" 을 산출물 실물과 대조한다.
#
# 정본:
#   (변경) git diff <BASE> <HEAD> -- <산출물> 의 추가 줄
#   (상태) <HEAD> 의 산출물 본문
# 판정: 주장이 지목한 「대상 토큰」이 주장이 지목한 「위치」에 실제로 있는가.
#
# 못 하는 것 — 아래 두 부류는 기계가 닫지 않고 사람에게 넘긴다.
#   (1) 위치를 특정할 수 없는 주장 (절 번호도 항목 id 도 없는 것)
#   (2) 부정형 주장 (".. 가 소유자가 아니다") — 부재가 정답인 경우
import re, subprocess, sys, collections

BASE = sys.argv[1] if len(sys.argv) > 1 else "ec115a7"
HEAD = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
LEDGER = "docs/discovery/regression-ledger.md"
EV = ["reports/evidence/m0/0b/scope.md",
      "reports/evidence/m0/0b/checklist.md",
      "reports/evidence/m0/0b/commands.md"]

def git(*a):
    return subprocess.run(["git"] + list(a), capture_output=True, text=True, check=True).stdout

# HEAD 에 "WT" 를 주면 **작업 트리**를 본다 — 커밋 전에 돌려야 의미가 있는 축이다.
WT = (HEAD == "WT")
def read(path):
    return open(path, encoding="utf-8").read() if WT else git("show", f"{HEAD}:{path}")
def diff(path):
    a = ["diff", "-U0", BASE] + ([] if WT else [HEAD]) + ["--", path]
    return git(*a)

# ---- 1. 산출물의 절 색인: 줄 -> 위치 키 -------------------------------------
body = read(LEDGER).splitlines()
sec_of = [None] * (len(body) + 1)      # 1-based
cur = None
for i, ln in enumerate(body, 1):
    m = re.match(r'^#{2,4}\s+(\d+(?:\.\d+)?)[.\s]', ln)          # "## 0. .."  "### 0.1 .."
    if m: cur = "§" + m.group(1)
    m = re.match(r'^#{3,4}\s+((?:R|P)-[A-Z]+-\d+)\b', ln)        # "### R-BASIS-01 · .."
    if m: cur = m.group(1)
    sec_of[i] = cur

# ---- 2. range 가 추가한 줄 (새 파일 줄번호) ---------------------------------
added = set()
d = diff(LEDGER).splitlines()
newline = 0
for ln in d:
    h = re.match(r'^@@ -\S+ \+(\d+)(?:,(\d+))? @@', ln)
    if h:
        newline = int(h.group(1)); continue
    if ln.startswith("+") and not ln.startswith("+++"):
        added.add(newline); newline += 1
    elif ln.startswith(" "):
        newline += 1

# ---- 3. 대상 토큰 어휘: 산출물이 스스로 쓰는 필드명 (정본은 산출물) ----------
fields = sorted({m.group(1) for ln in body
                 for m in [re.match(r'^\s*[-*]\s+\*\*([^*]+?)\*\*\s*(?:—|:)', ln)] if m},
                key=len, reverse=True)

ID = re.compile(r'\b((?:R|P)-[A-Z]+-\d+|OPEN-[A-Z]+-\d+)\b')
SEC = re.compile(r'§(\d+(?:\.\d+)?)')
CLAIM = re.compile(r'(고쳤|등재했|달았|교체했|추가했|제거했|걷어냈|넣었|갈랐|등록했'
                   r'|인라인했|옮겼|없앴|맞췄|좁혔|넓혔|정정했|반영했|삭제했|바꿨|붙였|보강했)')
NEG = re.compile(r'(아니다|아니라|않는다|않았다|없다|없음|없었|못 |못했|빠져|틀렸|저촉)')

def units(text):
    blocks, cur, fence = [], [], None
    for ln in text.split("\n"):
        s = ln.strip()
        # 펜스 블록(스크립트 본문·명령 출력)은 **주장이 아니다.** 건너뛴다 —
        # 넣지 않으면 이 축이 자기 출력을 주장으로 읽어 자기참조로 폭주한다.
        if s.startswith("```"):
            m = re.match(r'^(`{3,})', s)
            if fence is None: fence = m.group(1)
            elif s.startswith(fence): fence = None
            if cur: blocks.append(" ".join(cur)); cur = []
            continue
        if fence is not None: continue
        if not s or s.startswith(("#", "|")) or re.match(r'^[-*]\s|^\d+\.\s', s):
            if cur: blocks.append(" ".join(cur)); cur = []
            if s.startswith(("|", "#")): blocks.append(s); continue
            if re.match(r'^[-*]\s|^\d+\.\s', s): cur = [s]
            continue
        cur.append(s)
    if cur: blocks.append(" ".join(cur))
    # 단위는 **문장이 아니라 블록**(bullet/문단)이다 — 주장의 대상이 앞 문장에
    # 있고 완료 동사가 뒤 문장에 있는 경우가 실제로 있다(F-2 가 그것이다).
    return [b.strip() for b in blocks if b.strip()]

def lines_in(loc):
    # 절은 포함 관계다 — "§10 인계" 라는 주장은 §10.1 의 내용으로 충족된다.
    return [i for i in range(1, len(body) + 1)
            if sec_of[i] and (sec_of[i] == loc or sec_of[i].startswith(loc + "."))]

def flat(x):                      # 마크다운 강조가 한국어 동사를 가른다 (`등록**했다`)
    return re.sub(r'[*`~>]', '', x)

def sents(b):
    return [x for x in re.split(r'(?<=다\.)\s+|(?<=다\.\*\*)\s+', b) if x.strip()]

flag, note, unloc = [], [], []
for f in EV:
    for u in units(read(f)):
        fu = flat(u)
        if not CLAIM.search(fu): continue
        ids  = ID.findall(u)
        locs = ["§" + s for s in SEC.findall(u)] + [i for i in ids if not i.startswith("OPEN-")]
        locs = [l for l in dict.fromkeys(locs) if lines_in(l)]
        toks = [i for i in dict.fromkeys(ids) if i.startswith("OPEN-")] \
             + [t for t in fields if "`" + t + "`" in u or "**" + t + "**" in u]
        if not locs or not toks:
            unloc.append((f, u, ids)); continue
        ss = sents(u)
        for loc in locs:
            ns = lines_in(loc)
            for t in toks:
                if t in loc: continue
                # 그 토큰을 실제로 말하는 문장만 본다 — 부정형 판정을 블록 전체로 넓히면
                # 한 문장의 "..하지 않았다" 가 블록의 모든 주장을 가린다.
                mine = [x for x in ss if t in x] or [u]
                if not any(CLAIM.search(flat(x)) for x in mine): continue
                neg = any(NEG.search(flat(x)) for x in mine)
                hd  = [i for i in ns if t in body[i - 1]]
                say = max(mine, key=len)
                # 부정형은 **강등이 아니라 표시**다 — 부정이 토큰에 걸리는지 절 단위로
                # 가르는 것은 기계가 못 한다. 지목은 남기고 사람이 판정한다.
                if not hd:
                    flag.append((f, loc, t, say, "부정형 어휘 포함" if neg else ""))
                elif not any(i in added for i in hd):
                    note.append((f, loc, t, say, "HEAD 에는 있으나 이 range 가 넣은 것이 아니다"))

def dump(title, rows):
    seen, keep = set(), []
    for r in rows:
        k = r[:3]
        if k in seen: continue
        seen.add(k); keep.append(r)
    print(f"\n=== {title} ({len(keep)}건) ===")
    for f, loc, t, u, tag in keep:
        print(f"[{f.split('/')[-1]} → {loc}] 대상 `{t}` 가 그 위치에 없다" + (f"   ※ {tag}" if tag else ""))
        print(f"    주장: {' '.join(u.split())[:180]}".rstrip())

print(f"정본: git diff {BASE} {'' if WT else HEAD} -- {LEDGER}  (추가 줄 {len(added)})")
print(f"산출물 절 {len({s for s in sec_of if s})}종 · 필드 어휘 {len(fields)}종 · evidence {len(EV)}파일")
dump("지목 — 주장이 가리키는 대상이 그 위치에 없다 (전부 사람이 판정한다)", flag)
dump("참고 — 대상은 있으나 이 range 가 넣은 것이 아니다", note)
# 위치를 특정할 수 없는 주장 — 축이 닫지 않는다. 파일별로 세어 찍고 사람이 읽는다.
c = collections.Counter(f.split("/")[-1] for f, _, _ in unloc)
print(f"\n=== 위치 특정 불가 — 축이 닫지 않는다. 사람이 읽는다 ({len(unloc)}건: "
      + " · ".join(f"{k} {v}" for k, v in c.items()) + ") ===")
for f, u, ids in unloc:
    print(f"[{f.split('/')[-1]}] {' '.join(u.split())[:104]}".rstrip())
```

### C-9.4 유효성 실증 — **수정 전 트리에서 셋을 지목한다**

`8e6b78a`는 세 건을 고치기 **전** 커밋이다. 축이 그 트리에서 셋을 전부 찍지 못하면
축이 작동하지 않는 것이다.

```
$ python3 claimcheck.py ec115a7 8e6b78a
정본: git diff ec115a7 8e6b78a -- docs/discovery/regression-ledger.md  (추가 줄 1357)
산출물 절 81종 · 필드 어휘 15종 · evidence 3파일

=== 지목 — 주장이 가리키는 대상이 그 위치에 없다 (전부 사람이 판정한다) (8건) ===
[scope.md → R-BASIS-01] 대상 `OPEN-DEC-10` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: 등록된 소유자가 없음을 확인하고(`OPEN-DEC-10`은 예규 구간 차등으로 다른 축) **A7대로 `OPEN-REG-05`로 등록**했다.
[scope.md → R-BASIS-01] 대상 `OPEN-REG-05` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: 등록된 소유자가 없음을 확인하고(`OPEN-DEC-10`은 예규 구간 차등으로 다른 축) **A7대로 `OPEN-REG-05`로 등록**했다.
[scope.md → §0.1] 대상 `동반 OPEN` 가 그 위치에 없다
    주장: - **F-4**: **`동반 OPEN`**을 §0.1 필드 표에 **선택 필드**로 등재했다.
[checklist.md → §10] 대상 `동반 OPEN` 가 그 위치에 없다
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`·§10 인계(`OPEN-DEC-07`, verifier F-1). 둘 다 **결정 무관/조건부를 가르고** `동반 OPEN`을 달았다.
[checklist.md → R-COL-02] 대상 `OPEN-DEC-07` 가 그 위치에 없다
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`·§10 인계(`OPEN-DEC-07`, verifier F-1). 둘 다 **결정 무관/조건부를 가르고** `동반 OPEN`을 달았다.
[checklist.md → R-PROV-02] 대상 `OPEN-OPS-01` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`·§10 인계(`OPEN-DEC-07`, verifier F-1). 둘 다 **결정 무관/조건부를 가르고** `동반 OPEN`을 달았다.
[checklist.md → R-PROV-02] 대상 `동반 OPEN` 가 그 위치에 없다
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`·§10 인계(`OPEN-DEC-07`, verifier F-1). 둘 다 **결정 무관/조건부를 가르고** `동반 OPEN`을 달았다.
[commands.md → §0.3] 대상 `근거` 가 그 위치에 없다
    주장: - **자기참조 줄 번호 검사의 예외 판정을 고쳤다**(라운드 1) — `- **근거**:` 한 항목이 여러 줄에 걸치면 legacy 행 범위 연속 표기(`:162`)를 놓쳤다.

=== 참고 — 대상은 있으나 이 range 가 넣은 것이 아니다 (0건) ===

=== 위치 특정 불가 — 축이 닫지 않는다. 사람이 읽는다 (65건: scope.md 40 · checklist.md 1 · commands.md 24) ===
[scope.md] | **5. 정정을 인용 지점에 전파하지 않기** | 수치를 바꿨으면 인용 지점을 전수 확인. **자기 편집이 만든 오프셋도 대상이다** |
[scope.md] | **6. 셈으로 전칭을 주장하기** | 전칭은 셈이 아니라 **재현 명령**으로 쓴다. `뿐`·`전부`처럼 **수를 쓰지 않는 전칭도 포함**한다. **"고쳤다"는 진술도 전칭이다 —
[scope.md] 계열별로 나눈 이유는 **각 커밋에서 문서가 자체 정합**하기 위해서다 — 진행 중인 커밋은 "계열 N~8은 후속 커밋"을 문서 말미에 명시했고, 마지막 계열 커밋이 그 문구를 걷어냈다.
[scope.md] **없다.** 선행 조사가 정정한 C-1·C-2(백테스트 문서 출처 · `floor_applicability` 경로·행)는 **0A2가 이미 산출물에 반영해 고쳤고**, 0B가 `ed4b
[scope.md] **둘 다 선행 조사 노트의 문장이 축어로 이월된 것**이고, 계약 A2가 "상류 노트에서 옮겨 온 인용도 그 파일을 직접 열어 확인한다"고 요구한 바로 그 지점이다. **둘 다 lega
[scope.md] | **H1** | R-ASYNC-02가 `필요 951/시`를 부등식에 넣었다 | `inference_config.py:77-89`의 필요량은 **3,800~5,600건/시**다. `95
[scope.md] | **H2** | R-FLOOR-08이 "필터가 사정률 1 근방을 버린다"고 적었다 | **반대다.** 밴드 `[0.90, 1.10]` **안만** 담고 버리는 것은 **1에서 먼 꼬리
[scope.md] **H1에서 `93ecf9e`의 정정 사실을 근거에 넣었다** — 관계가 주석에만 있으면 그 산술이 틀려도 아무것도 깨지지 않고 사람이 읽을 때까지 남는다는 **이 항목의 논거를 강화**
[scope.md] `commands.md`의 출력 블록에서 **실행되지 않은 줄을 걷어냈다**(형태 3) — 손으로 고른 발췌 블록을 통째로 제거하고 **전문으로 교체**했다. **기계 축이 전수가 아니었
[scope.md] **자기참조 줄 번호 검사가 H2 수정으로 들어간 legacy 행 범위 연속 표기를 오탐으로 지목**했다. 검사기의 lookback이 직전 한 줄만 봐서 여러 줄에 걸친 `근거` 항목을
[scope.md] 라운드 1이 H1을 고치며 `93ecf9e`의 정정 사실을 근거에 넣은 것은 옳았으나 **그 정정의 *내용*을 커밋 본문과 다르게 적었다.** `git show 93ecf9e`로 본문과
[scope.md] **관찰도 함께 정확히 했다** — `3,800~5,600건/시`는 운영 필요량이 아니라 **0.5% drift 임계를 상한으로 만들려 할 때** 필요한 양이다. 주석이 **"보장되는 것
[scope.md] **규칙으로 남겼다**(`commands.md` C-2.4): **출력은 그 커밋의 최종 상태에서 다시 뜬다.** "이 커밋 기준"이라는 선언만으로는 부족하다. **이번 커밋은 그 규칙대
[scope.md] ### M-N2 — 축의 전칭을 실제 커버리지에 맞췄다
[scope.md] `numsrc`의 정규식이 뒤에 오는 `\w`를 전부 배제해 **한글 단위가 붙은 수를 통째로 놓쳤다**(`78건`·`5,822건`·`1,046행`·`1.15배`). **좁히는 대신 패턴
[scope.md] - **bare 연속 범위는 어느 기계 축도 행 범위를 검사하지 않는다** — 사람이 읽은 것으로만 확인됐다. **축을 늘릴지는 근거가 관측된 뒤에 판단한다.** (이 자리에 적었던 **
[scope.md] **verifier가 준 두 선택지 중 ①(실측값으로 교체 + 두 경로 추가)을 택했다.** ②(복제의 범위를 좁혀 정의)는 **증거가 가장 강한 자리에서 주장을 축소**하게 된다 — 이
[scope.md] **C-7.3의 판정 근거를 전부 재현 명령으로 교체**했고, **나머지 5건도 파일 전체 기준으로 재점검**했다. 넷은 성립하고 **하나(R-COL-03의 "정확히 30")는 철회**했
[scope.md] - **L-R2-2**: "`19.5%`가 이 라운드에 `관찰`에서 `사용자 영향`으로 옮겨졌다"가 **사실과 반대**였다. 텍스트는 **최초 커밋 `81a2ce3`부터 줄곧 `사용자 영
[scope.md] - **참고 2**: ledger의 깨진 들여쓰기 한 줄을 맞췄다.
[scope.md] **C-2.4 규칙대로** 커밋 직전에 세 스크립트를 재실행해 붙여넣기가 **최종 상태와 일치**함을 확인했다. 그 재실행에서 **자체 검사기가 이번 라운드 수정의 §0.3 위반을 잡았다
[scope.md] - **세 곳을 고쳤다** — `checklist.md` A2 · `commands.md` C-2.1 bullet · **C-2.4의 "현재는 99다"**(verifier가 든 둘 외에
[scope.md] - **C-2.4의 적용 대상을 「블록」에서 「블록 + 그 블록을 인용하는 산문」으로 넓혔다.**
[scope.md] - **인스턴스를 넘어서**: 두 곳은 아예 **수를 옮겨 적지 않고 블록을 가리키기만** 하게 바꿨다. **옮겨 적지 않으면 낡을 수 없다** — 형태 6의 "셈이 아니라 재현 명령으로
[scope.md] - **L-1**: `22~27`을 걷어내고 **셈 정의를 밝힌 실행 가능한 명령 둘**로 교체했다 — 리터럴 전체 **30**, 반환 카테고리명 7종을 뺀 **마커 24**(고유값도 2
[scope.md] - **L-2**: `R-COL-02` 셀의 생략 경로를 전체 경로로 고쳐 **복사 실행이 되게** 했고, `R-COL-06` 셀의 파일명 인용과 **범위 오기**(`:255-330` →
[scope.md] ### 0C 선행 조사 X-1 — ledger에서 함께 정정했다
[scope.md] **ledger가 그 프레이밍을 쓰고 있었고 네 자리를 고쳤다** — `R-BASIS-01`의 관찰 ("추정가격(ex-VAT)")과 사용자 영향("VAT·사정률만큼"), `R-BASIS-
[scope.md] **형태 5이고 H-R3와 같은 부류**이며, **그 절이 스스로 적은 사각지대 ③(정본이 블록에 없는 파생값)의 실물**이다. `checklist.md` A2·C-2.1 bullet과
[scope.md] **문서에서 그대로 떼어 실행해 35가 나오는 것을 확인**했다. 처음엔 인라인 코드로 넣었더니 **이스케이프된 백틱 때문에 복사 실행이 안 돼** fenced 블록으로 옮겼다 — 재현
[scope.md] - **L-1**: X-1 정정이 단 `bid_summary.py:70-77`이 **블록 경계보다 한 줄 늦다.** `Field(` 호출은 **`:69`에서 시작해 `:77`에서 닫힌다*
[scope.md] - **L-2**: **`prosecheck` 자신의 결함을 고쳤다.** `CHK` 두 패턴이 같은 문자열에 함께 걸려 한 줄을 **두 번 계상**했다. **한계로 기록하지 않고 고친 이
[scope.md] `citecheck`가 파일 길이를 `len(split("\n"))`으로 세어 **trailing newline 때문에 1 컸다.** `splitlines()`로 **산문에만** 적었고
[scope.md] 라운드 3의 M5 정정(**"두 축의 혼동"은 계열 6 한 칸뿐, 3·4·8은 단순 오계수**)이 `checklist.md`와 이 파일의 완료 요약에 **전파되지 않았다**(형태 5).
[scope.md] ### F-0 (blocker) — 고치지 않고 고쳤다고 적었다
[scope.md] **처방**: **본문 동기화를 resync에 넣고**(`resync.sh`) **인라인 7종을 실물과 전수 대조** 했다 — 어긋난 것은 **`citecheck` 하나뿐**이고 나머지는
[scope.md] `R-COL-02`와 **같은 처방**을 썼다 — **결정 무관(판정 순서는 임계값이 무엇이든 성립)**과 **조건부(경계 값 1.15의 마진 0.05는 재유도 대상이라 구체 수치를 고정
[scope.md] **축이 두 겹 다 못 본 이유**: 1차는 그 두 줄이 `OPEN-DEC-07`을 적지 않아서, 2차는 그 OPEN의 `결정 필요 사항`이 **전부 숫자**("기준 금액 신뢰 비율 1.
[scope.md] **전칭 둘을 현재 상태로 고쳤다** — C-8.2의 "추가 발견 0건"과 checklist A4의 "활성 OPEN을 선점하지 않았다". **둘 다 참이 아니었다.**
[scope.md] - **F-3**: 여유 검사에 **스크립트 본문이 없었다**(형태 4). `slack.py`로 인라인했다.
[checklist.md] | 축의 경계 | **C-8.3** — `citecheck`가 파일 길이를 **1 크게** 세고 있었다(trailing newline). **앞 라운드는 산문만 고치고 인라인 본문을 안
[commands.md] **필터를 길이 7 이상의 16진수로 바꿨고**(존재 여부는 `git`이 판정한다) 재실행 결과가 C-2.1이다. **커버리지 22/26 → 26/26**이며 그중 legacy 25종은
[commands.md] ledger가 R-QUAL-07에서 `classification/eligibility.py::assess_license` · `license_eligibility.py::assess_li
[commands.md] C-7.3의 판정 근거를 **전부 재현 명령으로 교체**했고, 그 결과 **추가 발견은 0이 아니라 1건**(R-PROV-01)이었다.
[commands.md] **"0건"은 축의 범위 안에서의 0이지 evidence 전체에 낡은 수가 없다는 뜻이 아니다.** 실제로 **사각지대 ③에 같은 부류의 실물이 살아 있었다** — C-5.1의 "현재 2
[commands.md] - **정본이 블록에 없는 수** — 산문이 계산해 적은 파생값은 대조 대상이 없다. **C-5.1의 "현재 28종"이 그 실물이었다**(verifier M-1) — 이 라운드에 재현 명
[commands.md] **축 자신의 결함 하나를 고쳤다**(verifier L-2): `CHK`의 두 패턴이 **같은 문자열에 함께 걸려** `checklist.md`의 한 줄을 **두 번 계상**했다. 자리
[commands.md] - secret 스캔 매치는 **전부 자기참조**다 — `scope.md`의 A6 문장 · `checklist.md`의 A6 행 · 위 스캔 명령 자신 · 이 설명이 `secret`이라는
[commands.md] **`93ecf9e`가 그 두 줄을 지웠다.** legacy가 오류로 판정해 제거한 값을 이 문서가 되살린 셈이다. **그 정정 사실을 R-ASYNC-02의 근거에 넣었다** — 관계가
[commands.md] 이전 판의 전칭("H1·H2 외에 인용 범위에 없는 수치는 없다")이 **자기 재현 명령의 범위를 넘었다.** 정규식이 뒤에 오는 `\w`를 전부 배제해 **한글 단위가 붙은 수를 통째로
[commands.md] > **정정 (verifier H-R2)** — 이전 판의 `R-PROV-01` 행은 **"근거에 다섯 경로가 열거돼 > 있다"**로 닫았다. **그건 legacy를 센 것이 아니라 le
[commands.md] | **R-PROV-01** | ~~"5곳에 복제"~~ → **앱 코드 6곳 · 테스트 포함 7곳** | `git grep -n -E '66(\.2)?%' ed4b06c -- '*.py'
[commands.md] | R-COL-03 | "30여 개" | **세는 대상 정의에 달렸다.** `F=app/services/koneps/live_failure.py`로 두고 — 리터럴 전체 `git -C b
[commands.md] - **`19.5%`의 이력을 잘못 적었던 것도 고쳤다**(verifier L-R2-2) — 이전 판은 "이 라운드에 `관찰`에서 `사용자 영향`으로 **옮겨져**"라고 적었으나 **텍스
[commands.md] - **`사용자 영향` 필드**(verifier L-R2-1) — 필드를 `관찰`로 한정하면서 **`사용자 영향`도 함께 빠졌고 그 사실이 기록되지 않았다.** 그 필드에도 **legac
[commands.md] **0A2가 `OPS-09`에 쓴 기법을 그대로 따랐다** — 결정과 무관하게 성립하는 것과 정책에 달린 것을 **갈랐다**:
[commands.md] `citecheck`가 파일 길이를 `len(stdout.split("\n"))`으로 계산해 **trailing newline 때문에 실제보다 1 컸다.** `splitlines()`로
[commands.md] > **정정 (verifier F-0, blocker) — 앞 라운드는 고치지 않고 고쳤다고 적었다.** > `splitlines()` 치환이 **산문에만** 들어갔고 **인라인된 스크립
[commands.md] **그 틈을 실제로 쓴 인용이 있었는지 재 봤다** — 인용 끝과 파일 끝의 여유를 전수로 계산한다. **스크립트 본문**(형태 4 — verifier F-3이 지적해 인라인했다):
[commands.md] `checklist.md`와 `scope.md`가 선행 조사 표 불일치의 원인을 **"전부 두 축의 혼동"**으로 요약했는데, `commands.md` C-5.2는 라운드 3에 그것을 *
[commands.md] **축을 늘리지 않았다.** 계산할 정본이 없으면 도구를 만들 수 없고, 이 부류의 처방은 이미 표에 있다 — **형태 5(정정을 인용 지점에 전파하지 않기)**다. 판정을 좁혔으면 **
[commands.md] ### C-8.6 형태 6 재발 — 산문을 고치고 대상을 안 고쳤다 (verifier F-0, blocker)
[commands.md] **§10.1 형태 6의 재발이며 새 형태가 아니다.** 그 형태의 규칙은 **"'고쳤다'는 진술도 전칭이다 — 적기 전에 그 지점을 열어 확인한다"**이고, **내가 앞 라운드에 세운
[commands.md] **이번 모양은 "산문을 고치고 대상을 안 고쳤다"**다. 앞선 재발들이 *대상을 고치고 인용 지점을 놓친* 형태(형태 5 계열)였다면, 이번은 **반대 방향**이다 — 세 파일이 "고쳤
[commands.md] **처방**: **본문 동기화를 resync에 넣었다.** 그리고 인라인된 스크립트 **6종을 실물과 전수 대조**해 어긋난 것이 `citecheck` 하나뿐임을 확인했다 — 나머지 다섯
```

**지목 8건 중 셋이 실물이고 다섯이 오탐이다.** 전부 사람이 판정한다.

| # | 지목 | 판정 | 근거 |
| --- | --- | --- | --- |
| 1 | `scope.md → §0.1` / `동반 OPEN` | **실물 (F-4)** | §0.1 필드 표에 그 행이 없었다 |
| 2 | `scope.md → R-BASIS-01` / `OPEN-REG-05` | **실물 (F-2)** | 귀속이 `OPEN-QUAL-10` 그대로였다 |
| 3 | `checklist.md → R-PROV-02` / `동반 OPEN` | **실물 (A4)** | `R-PROV-02`에 그 필드가 없었다 |
| 4 | `scope.md → R-BASIS-01` / `OPEN-DEC-10` | 오탐 | 주장이 **부정**이다("예규 구간 차등으로 **다른 축**") — 없는 것이 정답 |
| 5 | `checklist.md → §10` / `동반 OPEN` | 오탐 | §10 인계는 5필드 항목이 아니라 그 필드를 갖지 않는다 |
| 6 | `checklist.md → R-COL-02` / `OPEN-DEC-07` | 오탐 | 교차 짝짓기. `OPEN-DEC-07`은 `R-PROV-02` 몫이다 |
| 7 | `checklist.md → R-PROV-02` / `OPEN-OPS-01` | 오탐 | 교차 짝짓기(반대 방향) |
| 8 | `commands.md → §0.3` / `근거` | 오탐 | 주장은 **검사기**의 `- **근거**:` 처리에 관한 것이고 §0.3은 인용 규약 절이다 |

### C-9.5 수정 뒤 재실행 — 실물 셋이 사라졌다

`WT`를 주면 **작업 트리**를 본다. 커밋 전에 돌아야 의미가 있는 축이라 그 모드를 뒀다.
깨끗한 트리에서 `ec115a7 WT`와 `ec115a7 HEAD`는 **판정이 같다** — `diff`로 확인했고
차이는 **첫 줄의 ref 표기 한 줄뿐**이다.

```
$ python3 claimcheck.py ec115a7 WT
정본: git diff ec115a7  -- docs/discovery/regression-ledger.md  (추가 줄 1402)
산출물 절 81종 · 필드 어휘 15종 · evidence 3파일

=== 지목 — 주장이 가리키는 대상이 그 위치에 없다 (전부 사람이 판정한다) (11건) ===
[scope.md → R-BASIS-01] 대상 `OPEN-DEC-10` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: 등록된 소유자가 없음을 확인하고(`OPEN-DEC-10`은 예규 구간 차등으로 다른 축) **A7대로 `OPEN-REG-05`로 등록**했다.
[scope.md → §10.1] 대상 `동반 OPEN` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: | **A4** | "둘 다 `동반 OPEN`을 달았다" | `R-PROV-02`에 그 필드가 없다 | `R-PROV-02`에 **실제로 달고**, §10.1 인계는 5필드 항목이 아니라는 구분을 문면에 남겼다 |
[scope.md → §10.1] 대상 `관찰` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: `R-BASIS-04`의 같은 잔재 두 자리(`검증 방법`·`관찰`)도 **값 조건**(`낙찰하한율 × 기초금액 > 추정가격`)으로 바꿨다 — **값 조건으로 고르면 그 OPEN에 의존하지 않는다.**
[scope.md → R-BASIS-04] 대상 `동반 OPEN` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: `검증 방법`은 **결정 무관**(basis 태그가 다른 값 쌍, **어느 쪽이 크다는 전제 없음**)과 **조건부**(과세/비과세 경계 쌍은 그 OPEN이 닫힌 뒤)로 나누고 **`동반 OPEN`**을 달았다.
[scope.md → R-QUAL-06] 대상 `OPEN-REG-05` 가 그 위치에 없다
    주장: **둘 다 고쳤다** — `DIR`을 넓히고(**관측된 뒤의 확장이며 지목을 늘리는 방향**), 그 자리에 **legacy commit 본문의 인용임을 문면에 드러내고** 과세 의미·차이 크기가 `OPEN-REG-05` 소유임을 명시했다.
[checklist.md → §0.1] 대상 `OPEN-OPS-01` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`(`OPEN-DEC-07`, verifier F-1). **두 항목 다 결정 무관/조건부를 가르고 `동반 OPEN`을 달았다**(ledger §0.1이 그 필드를 **선택 필드**로 규약
[checklist.md → §0.1] 대상 `OPEN-DEC-07` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`(`OPEN-DEC-07`, verifier F-1). **두 항목 다 결정 무관/조건부를 가르고 `동반 OPEN`을 달았다**(ledger §0.1이 그 필드를 **선택 필드**로 규약
[checklist.md → §10.1] 대상 `OPEN-OPS-01` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`(`OPEN-DEC-07`, verifier F-1). **두 항목 다 결정 무관/조건부를 가르고 `동반 OPEN`을 달았다**(ledger §0.1이 그 필드를 **선택 필드**로 규약
[checklist.md → §10.1] 대상 `동반 OPEN` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`(`OPEN-DEC-07`, verifier F-1). **두 항목 다 결정 무관/조건부를 가르고 `동반 OPEN`을 달았다**(ledger §0.1이 그 필드를 **선택 필드**로 규약
[checklist.md → R-COL-02] 대상 `OPEN-DEC-07` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`(`OPEN-DEC-07`, verifier F-1). **두 항목 다 결정 무관/조건부를 가르고 `동반 OPEN`을 달았다**(ledger §0.1이 그 필드를 **선택 필드**로 규약
[checklist.md → R-PROV-02] 대상 `OPEN-OPS-01` 가 그 위치에 없다   ※ 부정형 어휘 포함
    주장: **계열 A로 두 건이 적출돼 조건부화했다** — `R-COL-02`(`OPEN-OPS-01`, Codex 1차)와 `R-PROV-02`(`OPEN-DEC-07`, verifier F-1). **두 항목 다 결정 무관/조건부를 가르고 `동반 OPEN`을 달았다**(ledger §0.1이 그 필드를 **선택 필드**로 규약

=== 참고 — 대상은 있으나 이 range 가 넣은 것이 아니다 (0건) ===

=== 위치 특정 불가 — 축이 닫지 않는다. 사람이 읽는다 (119건: scope.md 67 · checklist.md 2 · commands.md 50) ===
[scope.md] | **5. 정정을 인용 지점에 전파하지 않기** | 수치를 바꿨으면 인용 지점을 전수 확인. **자기 편집이 만든 오프셋도 대상이다** |
[scope.md] | **6. 셈으로 전칭을 주장하기** | 전칭은 셈이 아니라 **재현 명령**으로 쓴다. `뿐`·`전부`처럼 **수를 쓰지 않는 전칭도 포함**한다. **"고쳤다"는 진술도 전칭이다 —
[scope.md] 계열별로 나눈 이유는 **각 커밋에서 문서가 자체 정합**하기 위해서다 — 진행 중인 커밋은 "계열 N~8은 후속 커밋"을 문서 말미에 명시했고, 마지막 계열 커밋이 그 문구를 걷어냈다.
[scope.md] **없다.** 선행 조사가 정정한 C-1·C-2(백테스트 문서 출처 · `floor_applicability` 경로·행)는 **0A2가 이미 산출물에 반영해 고쳤고**, 0B가 `ed4b
[scope.md] **둘 다 선행 조사 노트의 문장이 축어로 이월된 것**이고, 계약 A2가 "상류 노트에서 옮겨 온 인용도 그 파일을 직접 열어 확인한다"고 요구한 바로 그 지점이다. **둘 다 lega
[scope.md] | **H1** | R-ASYNC-02가 `필요 951/시`를 부등식에 넣었다 | `inference_config.py:77-89`의 필요량은 **3,800~5,600건/시**다. `95
[scope.md] | **H2** | R-FLOOR-08이 "필터가 사정률 1 근방을 버린다"고 적었다 | **반대다.** 밴드 `[0.90, 1.10]` **안만** 담고 버리는 것은 **1에서 먼 꼬리
[scope.md] **H1에서 `93ecf9e`의 정정 사실을 근거에 넣었다** — 관계가 주석에만 있으면 그 산술이 틀려도 아무것도 깨지지 않고 사람이 읽을 때까지 남는다는 **이 항목의 논거를 강화**
[scope.md] `commands.md`의 출력 블록에서 **실행되지 않은 줄을 걷어냈다**(형태 3) — 손으로 고른 발췌 블록을 통째로 제거하고 **전문으로 교체**했다. **기계 축이 전수가 아니었
[scope.md] **자기참조 줄 번호 검사가 H2 수정으로 들어간 legacy 행 범위 연속 표기를 오탐으로 지목**했다. 검사기의 lookback이 직전 한 줄만 봐서 여러 줄에 걸친 `근거` 항목을
[scope.md] 라운드 1이 H1을 고치며 `93ecf9e`의 정정 사실을 근거에 넣은 것은 옳았으나 **그 정정의 *내용*을 커밋 본문과 다르게 적었다.** `git show 93ecf9e`로 본문과
[scope.md] **관찰도 함께 정확히 했다** — `3,800~5,600건/시`는 운영 필요량이 아니라 **0.5% drift 임계를 상한으로 만들려 할 때** 필요한 양이다. 주석이 **"보장되는 것
[scope.md] **규칙으로 남겼다**(`commands.md` C-2.4): **출력은 그 커밋의 최종 상태에서 다시 뜬다.** "이 커밋 기준"이라는 선언만으로는 부족하다. **이번 커밋은 그 규칙대
[scope.md] ### M-N2 — 축의 전칭을 실제 커버리지에 맞췄다
[scope.md] `numsrc`의 정규식이 뒤에 오는 `\w`를 전부 배제해 **한글 단위가 붙은 수를 통째로 놓쳤다**(`78건`·`5,822건`·`1,046행`·`1.15배`). **좁히는 대신 패턴
[scope.md] - **bare 연속 범위는 어느 기계 축도 행 범위를 검사하지 않는다** — 사람이 읽은 것으로만 확인됐다. **축을 늘릴지는 근거가 관측된 뒤에 판단한다.** (이 자리에 적었던 **
[scope.md] **verifier가 준 두 선택지 중 ①(실측값으로 교체 + 두 경로 추가)을 택했다.** ②(복제의 범위를 좁혀 정의)는 **증거가 가장 강한 자리에서 주장을 축소**하게 된다 — 이
[scope.md] **C-7.3의 판정 근거를 전부 재현 명령으로 교체**했고, **나머지 5건도 파일 전체 기준으로 재점검**했다. 넷은 성립하고 **하나(R-COL-03의 "정확히 30")는 철회**했
[scope.md] - **L-R2-2**: "`19.5%`가 이 라운드에 `관찰`에서 `사용자 영향`으로 옮겨졌다"가 **사실과 반대**였다. 텍스트는 **최초 커밋 `81a2ce3`부터 줄곧 `사용자 영
[scope.md] - **참고 2**: ledger의 깨진 들여쓰기 한 줄을 맞췄다.
[scope.md] **C-2.4 규칙대로** 커밋 직전에 세 스크립트를 재실행해 붙여넣기가 **최종 상태와 일치**함을 확인했다. 그 재실행에서 **자체 검사기가 이번 라운드 수정의 §0.3 위반을 잡았다
[scope.md] - **세 곳을 고쳤다** — `checklist.md` A2 · `commands.md` C-2.1 bullet · **C-2.4의 "현재는 99다"**(verifier가 든 둘 외에
[scope.md] - **C-2.4의 적용 대상을 「블록」에서 「블록 + 그 블록을 인용하는 산문」으로 넓혔다.**
[scope.md] - **인스턴스를 넘어서**: 두 곳은 아예 **수를 옮겨 적지 않고 블록을 가리키기만** 하게 바꿨다. **옮겨 적지 않으면 낡을 수 없다** — 형태 6의 "셈이 아니라 재현 명령으로
[scope.md] - **L-1**: `22~27`을 걷어내고 **셈 정의를 밝힌 실행 가능한 명령 둘**로 교체했다 — 리터럴 전체 **30**, 반환 카테고리명 7종을 뺀 **마커 24**(고유값도 2
[scope.md] - **L-2**: `R-COL-02` 셀의 생략 경로를 전체 경로로 고쳐 **복사 실행이 되게** 했고, `R-COL-06` 셀의 파일명 인용과 **범위 오기**(`:255-330` →
[scope.md] ### 0C 선행 조사 X-1 — ledger에서 함께 정정했다
[scope.md] **ledger가 그 프레이밍을 쓰고 있었고 네 자리를 고쳤다** — `R-BASIS-01`의 관찰 ("추정가격(ex-VAT)")과 사용자 영향("VAT·사정률만큼"), `R-BASIS-
[scope.md] **형태 5이고 H-R3와 같은 부류**이며, **그 절이 스스로 적은 사각지대 ③(정본이 블록에 없는 파생값)의 실물**이다. `checklist.md` A2·C-2.1 bullet과
[scope.md] **문서에서 그대로 떼어 실행해 35가 나오는 것을 확인**했다. 처음엔 인라인 코드로 넣었더니 **이스케이프된 백틱 때문에 복사 실행이 안 돼** fenced 블록으로 옮겼다 — 재현
[scope.md] - **L-1**: X-1 정정이 단 `bid_summary.py:70-77`이 **블록 경계보다 한 줄 늦다.** `Field(` 호출은 **`:69`에서 시작해 `:77`에서 닫힌다*
[scope.md] - **L-2**: **`prosecheck` 자신의 결함을 고쳤다.** `CHK` 두 패턴이 같은 문자열에 함께 걸려 한 줄을 **두 번 계상**했다. **한계로 기록하지 않고 고친 이
[scope.md] `citecheck`가 파일 길이를 `len(split("\n"))`으로 세어 **trailing newline 때문에 1 컸다.** `splitlines()`로 **산문에만** 적었고
[scope.md] 라운드 3의 M5 정정(**"두 축의 혼동"은 계열 6 한 칸뿐, 3·4·8은 단순 오계수**)이 `checklist.md`와 이 파일의 완료 요약에 **전파되지 않았다**(형태 5).
[scope.md] ### F-0 (blocker) — 고치지 않고 고쳤다고 적었다
[scope.md] **처방**: **본문 동기화를 resync에 넣고**(`resync.sh`) **인라인 7종을 실물과 전수 대조** 했다 — 어긋난 것은 **`citecheck` 하나뿐**이고 나머지는
[scope.md] `R-COL-02`와 **같은 처방**을 썼다 — **결정 무관(판정 순서는 임계값이 무엇이든 성립)**과 **조건부(경계 값 1.15의 마진 0.05는 재유도 대상이라 구체 수치를 고정
[scope.md] **축이 두 겹 다 못 본 이유**: 1차는 그 두 줄이 `OPEN-DEC-07`을 적지 않아서, 2차는 그 OPEN의 `결정 필요 사항`이 **전부 숫자**("기준 금액 신뢰 비율 1.
[scope.md] **전칭 둘을 현재 상태로 고쳤다** — C-8.2의 "추가 발견 0건"과 checklist A4의 "활성 OPEN을 선점하지 않았다". **둘 다 참이 아니었다.**
[scope.md] - **F-3**: 여유 검사에 **스크립트 본문이 없었다**(형태 4). `slack.py`로 인라인했다.
[scope.md] - `head_sha`를 갱신했다 — 이 라운드는 커밋이 넷이라 **`65a8f90`으로 한 번, 복원 커밋 `6d37634`으로 다시** 옮겼다(frontmatter가 최종값이다). *
[scope.md] **축을 만들며 축 자신의 결함 셋을 고쳤다** — 마크다운 강조가 한국어 동사를 가르는 것 (**F-2가 그것 때문에 안 잡혔다**), 부정형을 강등으로 쓴 것(**A4가 그렇게 숨었다
[scope.md] **인라인 스크립트 셈이 `scope.md`의 "7종"과 `commands.md`·`checklist.md`의 "6종"으로 어긋나 있었다.** **7이 맞다** — F-3이 같은 라운드에
[scope.md] **인라인한 직후 같은 구멍이 도구 자신에게 열려 있는 것을 찾았다** — 루프가 `.py`만 덮어 **`resync.sh` 자신의 본문이 낡았고** 대조가 그것을 잡았다. **자기 본문
[scope.md] **이력을 고쳐 쓰지 않는다.** `65a8f90`은 그대로 두고 **다음 커밋에서 복원·수정하고 사실을 기록한다** — 이 slice가 verdict와 정정에 대해 지켜 온 규율(원본을
[scope.md] 재검증 `not-ready`이나 **리뷰를 막는 성격이 아니다.** **앞 라운드의 "고쳤다고 적고 안 고침" 부류가 하나도 없다** — 세 건 실물 수정, ledger 자기모순 해소,
[scope.md] **실측으로 바꾸지 않고 수를 지웠다** — 열거는 C-9.6을, 수는 C-9.5 출력 블록을 가리킨다. **이번 라운드의 모든 수정에 같은 기준을 적용했다**: 고칠 자리마다 **블록을
[scope.md] ### M-C2 — 어느 축도 보지 않는 자리가 있었다. 그 자리를 축 안으로 넣었다
[scope.md] **`mutcheck.py`를 만들어 실행 키를 갖는 블록으로 바꿨다.** L-1이 만들어 둔 유도 루프가 **손대지 않아도 그것을 집었다**(`실행 10건 · 스크립트 9종`) — 배열
[scope.md] - **L-C4**: `head_sha` 서술이 한 절 안에서 frontmatter와 어긋났다 — 이 라운드가 커밋을 넷 냈고 값을 두 번 옮겼기 때문이다. 그 사실을 적었다.
[scope.md] **승인된 지침서를 직접 열었다** — `v2-지침서.md:222-223`(§4.5, `:215-223`)이 side effect를 port 뒤에 두고 **재시도를 멱등성 key와 함께
[scope.md] **Codex의 판정이 맞다.** A3의 판정 기준은 **머리표가 아니라 "무엇이 실제로 막히는가"**다 — 검사가 통과해도 막으려는 사건이 그대로 일어나면 형태만 갖춘 것이다. `a3c
[scope.md] **세 자리를 맞췄다** — C-5.1의 오탐 판정 bullet · 같은 절의 한계 서술 · `checklist.md` A6 셀에 **그 칸이 `d205d31` 시점에 근거와 어긋나 있었
[scope.md] **F-1·F-4는 형태 5**(정정·확장을 그것에 기대던 자리에 전파하지 않기)이고, **F-2는 형태 4**에 인접하며 처방은 도구 쪽에 넣었다. **F-3은 medium #1과 같은
[scope.md] **`citecheck`의 새 v2 경로 절이 행 범위 없는 인용에도 파일 길이를 찍었는데, 그 목록에 이 evidence 자신이 들어 있다.** 출력을 쓰면 자기 길이가 바뀌어 **동기
[scope.md] **한글 수사 축을 더했다** — 정본은 **출력 블록이 스스로 찍는 마지막 `N건` 헤더**, 대상은 **블록 직후 12줄의 산문**이다. **마지막 헤더로 잡은 것이 핵심**이다: 선
[scope.md] 1. **`DIR`에 `부풀`이 없었다** — `낮은`·`높은`만 더한 확장의 한계 실물이다. **한 낱말씩 메우면 다음 낱말에 또 뚫리므로** 이번엔 **증감 어휘를 부류로** 넣었다.
[scope.md] 2. **더 무거운 것 — tie가 조사 때문에 성립하지 않았다.** 3차는 항목이 **활성 OPEN과 엮여 있을 때만** 보는데, 특징어가 **조사가 붙은 채**(`기초금액과`·`추정가
[scope.md] 세 자리를 **"legacy 서술이므로 그대로 뒀다"**로 묶은 정책이 **이후 라운드에 셋 다 바뀌었다.** **원문을 지우지 않고 정정 주석**을 달았다. **"legacy 서술이니
[scope.md] **셋 다 형태 5**다. **F-b만 다른 점**은 **문면이 이미 있었고 실행이 안 됐다**는 것이라, **문면을 더 쓰는 대신 축을 고쳤다** — 어간 대조가 그 처방의 **실행본*
[scope.md] | **F-1** | `R-BASIS-03` 판정 행이 **`C-11.3`을 가리키는데 그 절은 `R-BASIS-06`만 다룬다.** 포인터를 따라가면 **근거가 그 자리에 없다**(형태
[scope.md] | **F-2** | F-a가 고친 문단 **두 줄 아래**가 `895d329`와 한 글자도 다르지 않게 남았다 — 표가 **둘에서 넷으로** 늘었는데 지시 대상이 조용히 바뀌었고, **
[scope.md] | **F-3** | 축의 **오차 방향을 반대로** 적었다 — *"전부 오탐 쪽으로 틀린다"*인데 **미탐 갈래가 둘 섞였고**, 미탐에는 *"사람이 판정한다"*가 성립하지 않는다 |
[scope.md] | **I-1** | **`ledger:135-138`의 legacy 인용이 원문과 공백 둘이 다르다** — 원문은 `capture 가`·`clamp 로`(조사 앞 공백), ledger는
[scope.md] - **산출물 변화**: **없다.** ledger **1,402줄 불변**, 스크립트 무변경, 집계 무변경. 이번 라운드는 **evidence 문면 셋**만 고쳤다.
[scope.md] **운영자가 이 라운드의 성격을 바꿨다.** 이 slice는 그동안 **틀린 서술의 인스턴스를 고쳐** 왔고 매번 새 인스턴스가 났다. 이번엔 **정확한 값으로 교체하지 않고 값 자체를
[scope.md] - `git diff --check` **0**. 스캔 매치는 **전부 자기참조**(C-6) — Codex 3차 verdict JSON이 등재돼 매치가 하나 늘었고 그 사실을 C-9.8
[checklist.md] | 축의 경계 | **C-8.3** — `citecheck`가 파일 길이를 **1 크게** 세고 있었다(trailing newline). **앞 라운드는 산문만 고치고 인라인 본문을 안
[checklist.md] **`R-ASYNC-06`을 지침서 요구에 맞춰 고쳤다** — `v2-지침서.md:222-223`(§4.5)이 side effect를 port 뒤에 두고 **재시도를 멱등성 key와 함께
[commands.md] **필터를 길이 7 이상의 16진수로 바꿨고**(존재 여부는 `git`이 판정한다) 재실행 결과가 C-2.1이다. **커버리지 22/26 → 26/26**이며 그중 legacy 25종은
[commands.md] ledger가 R-QUAL-07에서 `classification/eligibility.py::assess_license` · `license_eligibility.py::assess_li
[commands.md] C-7.3의 판정 근거를 **전부 재현 명령으로 교체**했고, 그 결과 **추가 발견은 0이 아니라 1건**(R-PROV-01)이었다.
[commands.md] **"0건"은 축의 범위 안에서의 0이지 evidence 전체에 낡은 수가 없다는 뜻이 아니다.** 실제로 **사각지대 ③에 같은 부류의 실물이 살아 있었다** — C-5.1의 "현재 2
[commands.md] - **정본이 블록에 없는 수** — 산문이 계산해 적은 파생값은 대조 대상이 없다. **C-5.1의 "현재 28종"이 그 실물이었다**(verifier M-1) — 이 라운드에 재현 명
[commands.md] **축 자신의 결함 하나를 고쳤다**(verifier L-2): `CHK`의 두 패턴이 **같은 문자열에 함께 걸려** `checklist.md`의 한 줄을 **두 번 계상**했다. 자리
[commands.md] - **자기참조 줄 번호 검사의 예외 판정을 고쳤다**(라운드 1) — `- **근거**:` 한 항목이 여러 줄에 걸치면 legacy 행 범위 연속 표기(`:162`)를 놓쳤다. **항목
[commands.md] - **한글 파일명이 두 축의 사각지대였다**(verifier F-1). `d205d31`이 넣은 `` `v2-지침서.md:215-223` `` 인용에 대해 이 검사가 뒤따르는 `` `:
[commands.md] - **`citecheck`도 같은 문자 클래스였다** — 그래서 **`v2-지침서.md:215-223`은 경로 존재도 행 범위도 어느 축이 검사하지 않았다.** 같이 넓혔고, **leg
[commands.md] - secret 스캔 매치는 **전부 자기참조**다 — `scope.md`의 A6 문장 · `checklist.md`의 A6 행 · 위 스캔 명령 자신 · 이 설명이 `secret`이라는
[commands.md] **`93ecf9e`가 그 두 줄을 지웠다.** legacy가 오류로 판정해 제거한 값을 이 문서가 되살린 셈이다. **그 정정 사실을 R-ASYNC-02의 근거에 넣었다** — 관계가
[commands.md] 이전 판의 전칭("H1·H2 외에 인용 범위에 없는 수치는 없다")이 **자기 재현 명령의 범위를 넘었다.** 정규식이 뒤에 오는 `\w`를 전부 배제해 **한글 단위가 붙은 수를 통째로
[commands.md] > **정정 (verifier H-R2)** — 이전 판의 `R-PROV-01` 행은 **"근거에 다섯 경로가 열거돼 > 있다"**로 닫았다. **그건 legacy를 센 것이 아니라 le
[commands.md] | **R-PROV-01** | ~~"5곳에 복제"~~ → **앱 코드 6곳 · 테스트 포함 7곳** | `git grep -n -E '66(\.2)?%' ed4b06c -- '*.py'
[commands.md] | R-COL-03 | "30여 개" | **세는 대상 정의에 달렸다.** `F=app/services/koneps/live_failure.py`로 두고 — 리터럴 전체 `git -C b
[commands.md] - **`19.5%`의 이력을 잘못 적었던 것도 고쳤다**(verifier L-R2-2) — 이전 판은 "이 라운드에 `관찰`에서 `사용자 영향`으로 **옮겨져**"라고 적었으나 **텍스
[commands.md] - **`사용자 영향` 필드**(verifier L-R2-1) — 필드를 `관찰`로 한정하면서 **`사용자 영향`도 함께 빠졌고 그 사실이 기록되지 않았다.** 그 필드에도 **legac
[commands.md] **0A2가 `OPS-09`에 쓴 기법을 그대로 따랐다** — 결정과 무관하게 성립하는 것과 정책에 달린 것을 **갈랐다**:
[commands.md] `citecheck`가 파일 길이를 `len(stdout.split("\n"))`으로 계산해 **trailing newline 때문에 실제보다 1 컸다.** `splitlines()`로
[commands.md] > **정정 (verifier F-0, blocker) — 앞 라운드는 고치지 않고 고쳤다고 적었다.** > `splitlines()` 치환이 **산문에만** 들어갔고 **인라인된 스크립
[commands.md] > **정정 (verifier M-C2)** — 이 자리는 원래 **손으로 옮겨 적은 출력**이었고 그 안의 ledger > 행 번호가 **두 라운드 연속 낡았다**(편집으로 밀릴 때마다
[commands.md] **그 틈을 실제로 쓴 인용이 있었는지 재 봤다** — 인용 끝과 파일 끝의 여유를 전수로 계산한다. **스크립트 본문**(형태 4 — verifier F-3이 지적해 인라인했다):
[commands.md] `checklist.md`와 `scope.md`가 선행 조사 표 불일치의 원인을 **"전부 두 축의 혼동"**으로 요약했는데, `commands.md` C-5.2는 라운드 3에 그것을 *
[commands.md] **축을 늘리지 않았다.** 계산할 정본이 없으면 도구를 만들 수 없고, 이 부류의 처방은 이미 표에 있다 — **형태 5(정정을 인용 지점에 전파하지 않기)**다. 판정을 좁혔으면 **
[commands.md] ### C-8.6 형태 6 재발 — 산문을 고치고 대상을 안 고쳤다 (verifier F-0, blocker)
[commands.md] **§10.1 형태 6의 재발이며 새 형태가 아니다.** 그 형태의 규칙은 **"'고쳤다'는 진술도 전칭이다 — 적기 전에 그 지점을 열어 확인한다"**이고, **내가 앞 라운드에 세운
[commands.md] **이번 모양은 "산문을 고치고 대상을 안 고쳤다"**다. 앞선 재발들이 *대상을 고치고 인용 지점을 놓친* 형태(형태 5 계열)였다면, 이번은 **반대 방향**이다 — 세 파일이 "고쳤
[commands.md] **처방**: **본문 동기화를 resync에 넣었다.** 그리고 인라인된 스크립트를 실물과 **전수 대조**해 어긋난 것이 `citecheck` 하나뿐임을 확인했다 — 나머지는 바이트
[commands.md] | **주장** | evidence 3파일에서 **완료 동사**(`고쳤`·`등재했`·`달았`·`교체했`·`추가했`·`제거했`·`걷어냈`·`넣었`·`갈랐`·`등록했`·`인라인했`·`옮겼`·
[commands.md] | **주장의 단위** | **문장이 아니라 블록**(bullet·문단). F-2가 그 이유다 — 대상(`R-BASIS-01`)이 앞 문장에 있고 완료 동사(`등록했다`)가 뒤 문장에 있
[commands.md] **축을 만들며 축 자신의 결함 셋을 고쳤다.** ① 마크다운 강조가 한국어 동사를 가른다 (`등록**했다`)라 정규식 전에 `*`·`` ` ``·`>`를 걷어낸다 — **F-2가 그것
[commands.md] **셋 다 "고쳤다"이므로 대상은 인라인 본문이고, 그 본문은 C-9.8의 `resync.sh`가 매 실행에 실물과 대조한다**(`불일치 0`). **이 축의 주장은 이 축이 아니라 그
[commands.md] - **대상 토큰의 어휘가 id와 산출물 필드명으로 한정된다** — "들여쓰기를 맞췄다"처럼 **대상이 이름을 갖지 않는 주장**은 짝지을 것이 없다.
[commands.md] - **"바뀌었는가"는 range 전체 기준이다** — 어느 라운드가 바꿨는지는 가르지 않는다.
[commands.md] - **펜스 블록은 보지 않는다** — 스크립트 본문과 명령 출력은 주장이 아니라고 정의했다. 그 자리는 C-9.8의 `resync.sh`가 닫되 **실행 키를 가진 블록만** 닫는다.
[commands.md] **위치 특정 불가 목록은 전수를 읽었다**(verifier L-C6). **결론**: 산출물 편집을 주장하면서 대상이 산출물에 없는 것은 **위 하나뿐**이고, 나머지는 세 부류다 —
[commands.md] `resync.sh` 본문(형태 4 — verifier L-1이 지적해 인라인했다):
[commands.md] **§10.1 형태 6의 네 번째 재발이며 새 형태가 아니다.** 규칙은 **"'고쳤다'는 진술도 전칭이다 — 적기 전에 그 지점을 열어 확인한다"**이고 **내가 앞 라운드에 세운 것*
[commands.md] **처리** — 확정 가능한 것과 미결을 갈랐다.
[commands.md] **지목은 위 블록이 찍는다 — 셈을 여기 옮겨 적지 않는다**(형태 6). **전부 사람이 판정했고 아래가 그 판정**이다. `R-BASIS-03`·`R-BASIS-06`은 **실물**
[commands.md] **처리**: `V2 예방 제약`을 **계약 + 테스트**로 바꾸고 **논리 작업을 식별하는 안정적인 멱등성 key**(대상 식별자 + 작업 종류 + 처리 대상 시점/버전)와 **그 ke
[commands.md] **판단: ③ 둘 다 — 정규식을 넓히고 세 자리를 맞췄다.**
[commands.md] > **이 확장이 곧바로 자기 결함을 냈다.** v2 경로 절이 **행 범위 없는 인용에도 파일 길이를 > 찍었는데**, 그 목록에 **이 evidence 자신이 들어 있다** — 출력을
[commands.md] - **F-2는 형태 4**(재현 불가능한 참조)에 인접하다 — 블록이 비어 근거가 그 자리에 없다. **처방은 도구 쪽이고 넣었다.**
[commands.md] **전수 훑기**: 지목 **여섯**, 그중 **실물은 F-a 자신 하나**이고 다섯은 오탐이다 — `실측 열`(낱말 뜻), `둘 다 고쳤다`·`하나만 겹쳐도`(블록이 아니라 다른 것을
[commands.md] 1. **`DIR` 어휘에 `부풀`이 없었다.** F-3에서 `낮은`·`높은`만 더한 확장의 한계 실물이다. **한 낱말씩 메우면 다음 낱말에 또 뚫린다** — 그래서 이번엔 **증감 어
[commands.md] `scope.md`가 세 자리를 **"legacy 서술이므로 그대로 뒀다"**로 묶었는데 **이후 라운드에 셋 다 바뀌었다**(`1.15` → Codex 1차, `~10% 낮은` → F-
[commands.md] `citecheck` 부재 5의 서술도 **`fixtures/manifest.yaml`을 덮지 않았다**(0C 소관 예정 경로라 이 저장소 문서도 `summary.py`도 아니다). 고쳤
[commands.md] - **F-b는 형태 5의 처방을 좁게 실행한 것**이고, C-11.3이 이미 **적용 범위를 「같은 계열 전체」로** 못박았다. **문면은 있었고 실행이 안 됐다** — 그래서 이번엔
[commands.md] - **이 slice에서 재발이 끊긴 처방은 하나뿐이다** — **수를 지우고 블록을 가리키기.** F-a의 인스턴스도 그렇게 고쳤다.
```

**실물 셋이 사라졌다.** 남은 지목은 **오탐 부류 셋**뿐이며 **셈을 여기 옮겨 적지
않는다** — 정본은 바로 위 블록이다(형태 6).

- **부정형**(C-9.4 표 4) — 주장이 "…는 다른 축이다"라고 **부재를 말한다.**
- **교차 짝짓기**(표 6·7) — `checklist.md` A4를 정확하게 고치면서 그 블록이 담는 위치가
  넷(`§0.1`·`§10.1`·`R-COL-02`·`R-PROV-02`)으로 늘어 **오탐이 그만큼 늘었다.**
  `scope.md`의 M-1 서술도 `§10.1`과 `동반 OPEN`을 한 블록에 담아 같은 부류를 하나 더 낸다.
- **주장이 문면으로 스스로 밝히는 것**(표 5) — "§10.1 인계는 5필드 항목이 아니라
  `동반 OPEN` 필드를 갖지 않는다". **축은 그 문장을 읽지 못한다.**

### C-9.6 이 축이 **못 보는 것**

- **교차 짝짓기** — 한 블록이 위치 여럿·토큰 여럿을 담으면 **교차곱으로 짝짓는다.**
  가장 가까운 위치에만 붙이는 쪽이 조용하지만 **놓치는 쪽으로 틀린다** —
  이 축은 놓치지 않는 쪽을 골랐고 **오탐은 사람이 건다.**
- **부정형** — 부정이 문장 전체에 걸리는지 그 토큰에 걸리는지 기계가 가르지 못한다.
  **표시만 하고 지목은 남긴다.**
- **위치를 특정할 수 없는 주장** — 절 번호도 항목 id도 없는 것이 **대다수**다(실행 결과의
  마지막 절). **축이 닫지 않는다. 사람이 읽는다.**
- **대상 토큰의 어휘가 id와 산출물 필드명으로 한정된다** — "들여쓰기를 맞췄다"처럼
  **대상이 이름을 갖지 않는 주장**은 짝지을 것이 없다.
- **산출물 하나만 본다** — `docs/discovery/regression-ledger.md`가 대상이고
  evidence 파일 자신에 대한 편집 주장은 위치 색인이 없다.
- **"바뀌었는가"는 range 전체 기준이다** — 어느 라운드가 바꿨는지는 가르지 않는다.
- **펜스 블록은 보지 않는다** — 스크립트 본문과 명령 출력은 주장이 아니라고 정의했다.
  그 자리는 C-9.8의 `resync.sh`가 닫되 **실행 키를 가진 블록만** 닫는다.
  **키 없는 붙여넣기 블록은 어느 축도 보지 않으며**, C-8.3의 변이 검사가 그 반례로
  두 라운드 살아 있었다(verifier M-C2). 그 자리는 **실행 키를 갖게 바꿨고**, 남은
  키 없는 **스크립트 본문 블록**은 C-2.2의 **발췌 한 개**뿐이다 — `resync.sh`가 매 실행에
  그 수를 찍는다. **명령 출력 블록 중 키가 없는 것**은 C-10.3의 `sed` 두 줄이며, 그 자리는
  **블록 안에 재현 명령이 함께 적혀 있고** `v2-지침서.md`는 이 slice가 편집하지 않는다.

### C-9.6a 이 절이 한 번 통째로 사라졌다 — 동기화 루프의 빈 블록 경계 (자기 발견)

**C-9.5 마무리·C-9.6·C-9.7과 C-9.8 머리가 지워진 채 커밋됐다**(`65a8f90`).
출력 동기화가 블록 끝을 `s.index` 로 잡았는데 **처음 쓴 WT 블록이 비어 있어** 그 탐색이
**다음 블록의 여는 펜스**까지 건너뛰었다 — 사이의 절들과 **WT 블록 자신의 닫는 펜스**가
함께 사라졌다. **그 커밋 메시지는 C-9.7·C-9.8을 적는데 파일에는 없었다 — 이 라운드의
주제와 글자 그대로 같은 실패다.**

**네 가지를 했다.** ① 블록 끝을 **비탐욕 정규식**으로 그 블록의 닫는 펜스에서 잡는다 —
빈 블록도 안전하다. ② **닫는 펜스를 되살리고** 지워진 절을 복원했다. ③ **안전 장치** —
동기화 전후의 **절 머리 수**를 세어 줄어들면 `assert`로 즉시 멈춘다(출력의 `[안전]` 줄).
④ 그 안전 장치로 **복원을 검증**했다: 펜스를 되살리기 전에는 복원한 절이 **블록 안에**
들어가 다음 동기화가 다시 지웠고, `assert`가 그것을 잡았다(`43 → 39`).

**찾은 것은 인라인 본문 대조가 아니라 절 머리 확인이다** — 본문·출력 블록은 전부 `일치`를
냈다. **동기화가 옳다는 것과 파일이 온전하다는 것은 다른 주장이고, 전자만 검사했다.**

### C-9.7 사람 판독 버킷이 찾은 것 — 인라인 스크립트 셈의 불일치

위치 특정 불가 목록을 읽다가 **`scope.md`의 "인라인 7종"과 `commands.md` C-8.6·
`checklist.md`의 "6종"이 어긋난다**는 것을 찾았다. **7이 맞다** — F-3이 같은 라운드에
`slack.py`를 인라인해 일곱 번째가 됐고, "6종"은 그 직전 상태다(형태 5).

**셈을 고쳐 적지 않고 세는 도구가 세게 했다.** 아래 `resync.sh`가 매 실행에서
**전체 블록 수 · 실행 목록이 차지한 수 · 발췌 수 · 불일치 수**를 찍는다.

**위치 특정 불가 목록은 전수를 읽었다**(verifier L-C6). **결론**: 산출물 편집을 주장하면서
대상이 산출물에 없는 것은 **위 하나뿐**이고, 나머지는 세 부류다 — ① `§10.1` 형태 표와
계약을 **인용**하는 문장(주장이 아니다), ② **evidence 자신·스크립트·`_workspace` 노트**를
대상으로 하는 수정(이 축의 위치 색인 밖이며 스크립트 쪽은 `resync.sh`의 본문 대조가 닫는다),
③ **대상이 이름을 갖지 않는 수정**("들여쓰기를 맞췄다" 부류). **전수 표는 만들지 않는다** —
`openstance`의 사각지대 목록과 달리 이 목록은 **판정이 필요한 미결이 아니라 축의 적용
범위 밖**이고,
그 사실은 항목마다가 아니라 **부류로** 닫힌다.

### C-9.8 L-1 — resync의 대상 목록이 **손으로 유지되는 배열이라** 새 스크립트를 놓쳤다

verifier가 든 셋 전부 같은 뿌리다: `slack`이 본문·출력 **어느 동기화 목록에도 없었고**,
출력 동기화가 `statusdiff`도 덮지 않았으며, `resync.sh` 자체가 evidence에 없었다.
**F-0을 만든 것과 같은 형태의 구멍이 새 스크립트에 그대로 열려 있었다.**

**처방은 배열을 고치는 것이 아니라 배열을 없애는 것이다** — 두 루프의 대상 목록을
**evidence가 스스로 선언하는 `$ python3 <name>.py <args>` 출력 키에서 유도**한다.
새 스크립트를 evidence에 넣으면 **자동으로 루프에 든다.** 실행 결과:

```
$ bash resync.sh
evidence 가 선언한 실행 14건 · 스크립트 10종: a3check · citecheck · claimcheck · ledgercheck · mutcheck · numsrc · openstance · prosecheck · slack · statusdiff
  [본문] 전체 블록 11 · 실행 목록이 차지한 것 10 · 발췌(실행 키 없음) 1 · 불일치 0
  [출력] openstance: 같은 키 2곳 — 전부 갱신
  [출력] openstance: 같은 키 2곳 — 전부 갱신
  [불변] ledger 1402줄 · secret 13
  [안전] 절 머리 61 → 61
```

**이 블록은 실행 키(`$ python3 <name>.py`)가 없어 `resync`가 스스로 덮지 않는다** — 자기
출력이라 손으로 옮긴다. `절 머리`는 이 라운드가 절을 하나 더해 **61**이 됐고, 스캔 매치는
Codex 3차 verdict JSON이 evidence에 등재돼 **13**이 됐다(매치는 여전히 **전부 자기참조**,
C-6 참조). **두 수 다 이 커밋의 최종 상태에서 다시 뜬 것**이다.
(**이 문단은 스캔 어휘를 쓰지 않는다** — 쓰면 그 자신이 매치가 돼 방금 적은 수를 낡게 만든다.)

출력의 `발췌(실행 키 없음) 1`은 C-2.2의 필터 조각이다 — 실행 키를 갖지 않는 **발췌**이고
전체 스크립트가 아니다. 루프가 그것을 **셈에서 갈라 찍는다.**

**인라인한 직후 같은 구멍이 도구 자신에게 열려 있는 것을 찾았다** — 루프가 `.py`만 덮어
**`resync.sh` 자신의 인라인 본문이 낡았다.** 대조가 `X`를 내서 알았다. **1b) 블록을 더해
자기 본문도 동기화**하게 했다. 도구를 만든 자리에서 그 도구가 자기를 빠뜨리는 것이
이 slice에서 **세 번째**다(F-0의 `citecheck` · L-1의 `slack` · 이번의 `resync` 자신).

`resync.sh` 본문(형태 4 — verifier L-1이 지적해 인라인했다):

````bash
#!/bin/bash
# 확장 C-2.4 규칙의 실행본 — evidence 의 **스크립트 본문**·**출력**·**불변 수치**를
# 최종 상태에 맞춘다. F-0 은 이 루프가 **본문을 갱신하지 않아서** 났고, L-1 은 대상
# 목록이 **손으로 유지되는 배열이라 새 스크립트를 놓쳐서** 났다.
#
# L-1 의 처방: **두 루프의 대상 목록을 evidence 에서 유도한다.**
#   정본 = commands.md 안의 `$ python3 <name>.py <args>` 출력 키.
#   손으로 고칠 배열이 없으므로 **새 스크립트를 추가하면 자동으로 루프에 든다.**
set -e
cd /Users/harris/Development/private/bid-vector-v2
python3 - <<'PY'
import pathlib, subprocess, re, shlex
SP = "/private/tmp/claude-503/-Users-harris-Development-private-bid-vector-v2/2071e10c-f36a-4720-b677-b29c3a80734f/scratchpad"
P  = pathlib.Path("reports/evidence/m0/0b/commands.md")

def keys():
    """evidence 가 스스로 선언하는 실행 목록. 여러 줄 `\\` 이음을 붙인다."""
    s, out = P.read_text(encoding="utf-8"), []
    for m in re.finditer(r'^\$ python3 ((?:[^\n]*\\\n)*[^\n]*)$', s, re.M):
        cmd = m.group(1).replace("\\\n", " ")
        name = cmd.split()[0]
        if name.endswith(".py"):
            out.append((m.group(0)[len("$ python3 "):], name[:-3], shlex.split(cmd)[1:]))
    return out

RUNS = keys()
HEADS0 = len(re.findall(r'(?m)^#{2,4} ', P.read_text(encoding="utf-8")))
names = sorted({n for _, n, _ in RUNS})
print(f"evidence 가 선언한 실행 {len(RUNS)}건 · 스크립트 {len(names)}종: " + " · ".join(names))

# 1) 스크립트 **본문** 동기화 (F-0). 자리는 실물의 첫 줄로 찾는다.
claimed, mism = set(), []
for _, name, _ in RUNS:
    real = pathlib.Path(f"{SP}/{name}.py").read_text(encoding="utf-8").rstrip("\n")
    s = P.read_text(encoding="utf-8")
    head = real.split("\n")[0]
    tgt = [m for m in re.finditer(r'```python\n(.*?)\n```', s, re.S)
           if m.group(1).split("\n")[0] == head]
    if len(tgt) != 1:
        print(f"  [본문] {name}: 자리 특정 실패({len(tgt)})"); continue
    m = tgt[0]; claimed.add(m.start())
    if m.group(1).rstrip("\n") == real:
        continue
    mism.append(name)
    P.write_text(s[:m.start(1)] + real + s[m.end(1):], encoding="utf-8")
    print(f"  [본문] {name}: 갱신")
s = P.read_text(encoding="utf-8")
allb = [m.start() for m in re.finditer(r'```python\n(.*?)\n```', s, re.S)]
print(f"  [본문] 전체 블록 {len(allb)} · 실행 목록이 차지한 것 {len(claimed)} · "
      f"발췌(실행 키 없음) {len(allb)-len(claimed)} · 불일치 {len(mism)}"
      + (": " + ", ".join(mism) if mism else ""))

# 1b) **자기 본문**도 동기화한다. 이 루프가 `.py` 만 덮던 동안 `resync.sh` 자신의
#     인라인 본문이 낡아 있었다 — F-0 과 같은 구멍이 도구 자신에게 열려 있었다.
me = pathlib.Path(f"{SP}/resync.sh").read_text(encoding="utf-8").rstrip("\n")
s = P.read_text(encoding="utf-8")
tgt = [m for m in re.finditer(r'````bash\n(.*?)\n````', s, re.S)
       if m.group(1).split("\n")[0] == me.split("\n")[0]]
if len(tgt) != 1:
    print(f"  [본문] resync.sh: 자리 특정 실패({len(tgt)})")
elif tgt[0].group(1).rstrip("\n") != me:
    P.write_text(s[:tgt[0].start(1)] + me + s[tgt[0].end(1):], encoding="utf-8")
    print("  [본문] resync.sh: 갱신")

# 2) 출력 동기화 — 같은 목록에서 돈다. 손으로 빠뜨릴 자리가 없다.
for label, name, args in RUNS:
    out = subprocess.run(["python3", f"{SP}/{name}.py"] + args,
                         capture_output=True, text=True).stdout.rstrip("\n")
    s = P.read_text(encoding="utf-8")
    # **빈 블록을 인덱스로 자르면 다음 블록의 여는 펜스까지 먹는다.** 그렇게 evidence
    # 세 절이 조용히 지워졌다. 비탐욕 정규식으로 **그 블록의 닫는 펜스**를 잡는다.
    pat = re.compile(r'(```\n\$ python3 ' + re.escape(label) + r'\n)(.*?)(```\n)', re.S)
    ms = list(pat.finditer(s))
    if not ms:
        print(f"  [출력] {name}: 키 미발견"); continue
    # **같은 실행 키가 여러 곳에 있으면 전부 갱신한다.** 첫 매치만 채우던 동안
    # 둘째 블록이 **영구히 빈 채로** 남았다(verifier F-2). 뒤에서부터 치환한다.
    if len(ms) > 1: print(f"  [출력] {name}: 같은 키 {len(ms)}곳 — 전부 갱신")
    if any(m.group(2).rstrip("\n") != out for m in ms):
        print(f"  [출력] {name}: 갱신")
    for m in reversed(ms):
        s = s[:m.start(2)] + out + "\n" + s[m.end(2):]
    P.write_text(s, encoding="utf-8")

# 3) 불변 수치
wc  = subprocess.run(["wc","-l","docs/discovery/regression-ledger.md"],capture_output=True,text=True).stdout.split()[0]
sec = subprocess.run("grep -rniE '(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))' docs/discovery/regression-ledger.md reports/evidence/m0/0b/ | wc -l",shell=True,capture_output=True,text=True).stdout.strip()
s = P.read_text(encoding="utf-8")
s = re.sub(r"(\$ wc -l docs/discovery/regression-ledger\.md\n)\d+", r"\g<1>"+wc, s, count=1)
s = re.sub(r"(reports/evidence/m0/0b/ \| wc -l\n)\d+", r"\g<1>"+sec, s, count=1)
P.write_text(s, encoding="utf-8")
print(f"  [불변] ledger {wc}줄 · secret {sec}")

# 4) 안전 장치 — 동기화가 **절을 지우지 않았는지**. 빈 블록 경계 버그로 실제로 세 절이
#    조용히 지워졌다. 줄어들면 즉시 실패한다.
h1 = len(re.findall(r'(?m)^#{2,4} ', P.read_text(encoding="utf-8")))
print(f"  [안전] 절 머리 {HEADS0} → {h1}" + ("" if h1 >= HEADS0 else "  ← 절이 사라졌다!"))
assert h1 >= HEADS0, f"동기화가 절을 지웠다: {HEADS0} → {h1}"
PY
````

### C-9.9 M-1 — §10 인계 자리는 계열 A 축의 **구조적 사각지대**다 (미기록이었다)

F-1이 조건부화한 자리는 **둘**(`R-PROV-02`의 `검증 방법` · **§10.1 fixture-curator 인계**)
인데 **계열 A 축(C-8.2)이 지목한 것은 앞의 하나뿐**이다. 축이 `^### [RP]-` 블록의
`V2 예방 제약`·`검증 방법` 필드만 훑고 **§10의 `- **경계값 corpus**:`는 스캔 대상이
아니기 때문**이다. C-8.2의 사각지대 절은 **"특징어 2개 미만인 활성 OPEN"**만 적었고
**필드 범위 사각지대는 적지 않았다.**

**내가 스스로 "하류로 나가는 자리라 이쪽이 더 중요하다"고 적은 자리가 축 밖에 있다.**
**축을 넓히지 않고 사각지대로 기록한다** — 근거가 한 번 관측됐고(F-1) 그 한 건은
사람이 읽어 닫았다. **§10 인계를 스캔에 넣을지는 이 부류가 다시 관측된 뒤에 판단한다.**

`R-PROV-02`에 단 `동반 OPEN`이 **§10.1을 함께 가리키게** 적은 것이 지금의 완화책이다 —
항목 쪽은 축이 보므로 **축이 보는 자리에서 사각지대를 가리킨다.**

### C-9.10 형태 6 재발 — 이번 모양은 "산문이 앞서 가고 대상이 따라오지 않았다"

**§10.1 형태 6의 네 번째 재발이며 새 형태가 아니다.** 규칙은
**"'고쳤다'는 진술도 전칭이다 — 적기 전에 그 지점을 열어 확인한다"**이고
**내가 앞 라운드에 세운 것**이다. C-8.6이 같은 진단을 적었는데 **그 진단을 적은 라운드가
같은 실패를 세 번 더 냈다.**

**문장으로 적은 규칙은 지켜지지 않고, 실행되는 절차로 만든 규칙만 남는다.** 이 slice가
축을 여섯 번 만들며 매번 배운 것이 그것이다. C-9는 그 규칙의 **실행본**이다.


---

## C-10. Codex 2차 대응 (2026-08-27) — 미결에 기댄 사실 주장과 실질 없는 제약

판정 `request_changes` — blocker 0 · **high 0** · medium 2. verdict는
`codex-review-20260827T085200Z.json`(등재, append-only). **직전 high(계열 A)와 medium 2건은
해소**됐다.

### C-10.1 medium #1 — 정량을 철회하면서 **방향 주장을 남겼다**

`R-BASIS-01`이 관찰에서 **"저장된 추정가격의 과세 처리가 이력상 일관되지 않다"**를 적고
차이의 크기를 `OPEN-REG-05`로 넘기면서, **사용자 영향은 "경계 공고가 한 방향으로 일관되게
누락 또는 포함된다"고 확정**했다. **방향을 말하려면 두 금액의 대소 관계가 정해져 있어야
하는데 그것이 바로 그 미결이다.**

**이건 X-1 정정이 절반만 된 것이다.** 원래 프레이밍은 *"차이가 VAT만큼이라 체계적이므로
한 방향으로 일관되게"*였고, 라운드 4가 **정량 부분("VAT만큼")만 철회**하면서 **그 위에
얹혀 있던 방향 주장은 그대로 뒀다.** 근거가 사라진 결론이 남은 것이다.

**처리** — 확정 가능한 것과 미결을 갈랐다.

| 자리 | 전 | 후 |
| --- | --- | --- |
| `사용자 영향` | "차이가 **체계적**" · "**한 방향으로 일관되게**" | "차이가 **basis 정의에서 나온다**" · "**같은 입력에 대해 필터 결과가 갈린다**". **방향·크기·행별 일관성은 `OPEN-REG-05` 소유**라고 명시 |
| `검증 방법` | "**과세/비과세 경계 쌍**을 fixture로" | **결정 무관**(basis 태그가 다른 값 쌍 — **어느 쪽이 크다는 전제 없음**) / **조건부**(과세 경계 쌍은 그 OPEN이 닫힌 뒤) |
| — | 없음 | **`동반 OPEN`** 필드 추가 — `OPEN-REG-05` 소유를 문면에 남긴다 |
| `R-BASIS-04` `검증 방법` | "**기초금액 > 추정가격인 공고**" | "**`낙찰하한율 × 기초금액 > 추정가격`인 공고**. **선택 기준은 값 조건 자체이지 과세 여부가 아니다**" |
| `R-BASIS-04` `관찰` | "…를 넘는 **과세 공고**에서" | 같은 값 조건으로. legacy가 그렇게 부른다는 것은 **귀속해서** 적는다 |
| §10.1 인계 | 없음 | ⚠ **하류가 과세 경계로 corpus를 굳히지 말 것** — F-1의 `OPEN-DEC-07`과 같은 처리 |

**`OPEN-REG-05` 등록 문면에는 잔재가 없다** — 질문이 *"두 금액의 차이가 무엇으로
이루어지는가"*로 중립적이고 방향을 전제하지 않는다.

### C-10.2 `openstance`가 왜 못 봤는가 — **사각지대 둘. 축의 결함이 아니다**

**둘 다 구조적이고 실측으로 확인했다.**

1. **`OPEN-REG`가 2차의 대상이 아예 아니었다.** 2차의 특징어 사전(`topics`)은
   **`capability-map.md` §12 행에서만** 만들어지는데 `OPEN-REG-*`는 **ledger §9에 산다.**
   `active` 집합에는 들어가 1차(id를 직접 언급하는 자리)에는 걸리지만, **id를 적지 않은
   선점을 보는 2차는 `OPEN-REG`를 원리적으로 매칭할 수 없었다.** 게다가 사각지대 목록이
   `not o.startswith("OPEN-REG")`로 **그 사실조차 보고하지 않았다.**
2. **필드 범위 밖이다.** 축의 `DECIDE`는 `V2 예방 제약`·`검증 방법` 둘뿐이다. 계열 A를
   **"미결을 V2 요구로 확정하는가"**로 정의했기 때문인데, medium #1은 **`사용자 영향`의
   사실 주장**이 미결에 기댄 것이라 **다른 필드**였다.

**보강 셋을 넣었다.** ① `OPEN-REG`의 특징어를 **ledger §9 행에서** 만든다. ② 사각지대
목록에서 `OPEN-REG` 제외를 걷어냈다. ③ **3차 패스**를 더했다 — **사실 주장 필드
(`관찰`·`사용자 영향`)의 방향·대소 주장**을 찍고, 그 항목과 엮인 활성 OPEN을 함께 보인다.
**관측된 형태를 그대로 겨눈 것**이다: 방향을 말하는 문장은 대소 관계를 전제하고, 그
전제가 미결이면 선점이다.

**유효성 실증 — Codex가 본 트리에서 지목한다.**

```
$ python3 openstance.py 4f4fd7f
활성 OPEN 정본: capability-map §12 45건 + OPEN-REG 5건

=== 결정 필드가 활성 OPEN을 언급하는 자리: 5건 ===
  [R-PROV-02] 검증 방법 → OPEN-DEC-07  조건부 표시 있음  :356
      - **조건부 — `OPEN-DEC-07` 결정에 따라 확정.** legacy의 임계 **1.15**(부가세 1.10 +
  [R-COL-02] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :812
      - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** **분류 불가(`unknown`)를
  [R-COL-02] 검증 방법 → OPEN-OPS-01  조건부 표시 있음  :821
      - **조건부 — `OPEN-OPS-01` 결정에 따라 확정.** (b) fail-safe면 "`unknown`이 재시도
  [R-COL-03] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :842
      ※ 재시도 정책 자체(`unknown → retryable` 여부)는 **활성 `OPEN-OPS-01`이 소유**하며
  [R-ASYNC-01] V2 예방 제약 → OPEN-OPS-10  조건부 표시 있음  :957
      ※ DB 기반 큐의 backlog 관측·가시성 timeout 계약은 **활성 `OPEN-OPS-10`이 소유**하며

=== id 없이 주제가 겹치는 후보: 2건 (오탐 다수 — 사람 판정) ===
  [R-BASIS-01] ~ OPEN-QUAL-10  공통어: ['basis', '운영자']
  [R-COL-01] ~ OPEN-ML-05  공통어: ['policy', 'versioned']

=== 2차가 매칭할 수 없는 활성 OPEN: 8건 (특징어 2개 미만) ===
  OPEN-COL-03: 특징어 ['업무구분'] | 질문 업무구분 코드 전체 체계(현재 4개만 매핑)
  OPEN-COL-05: 특징어 ['limit'] | 질문 외부 API rate limit의 실제 수치
  OPEN-NOTI-06: 특징어 ['초기값'] | 질문 알림 임계 4종의 초기값
  OPEN-NUM-02: 특징어 ['unknown'] | 질문 자격 관련 수치 5종(92% unknown 포함)
  OPEN-OPS-03: 특징어 [] | 질문 큐 깊이 검출 SLO (**임계 미결**)
  OPEN-REG-04: 특징어 ['측정일'] | 질문 **"66% 오염"의 측정 방법·모수·측정일**
  OPEN-SET-01: 특징어 ['페이퍼'] | 질문 페이퍼 정산 근접 임계 3종의 도출 근거
  OPEN-SET-05: 특징어 ['재공고'] | 질문 재공고(차수 다수) 대사 대상 선택 규칙
  → 이 항목들은 **사람이 읽어 판정**한다. 기록은 evidence C-8.2.

=== 3차: 사실 주장 필드의 방향·대소 주장: 8건 ===
  [R-BASIS-01] 사용자 영향 :84  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-NUM-01', 'OPEN-QUAL-10', 'OPEN-REG-05']
      경계 근처 공고가 **한 방향으로 일관되게** 누락되거나 포함된다. 운영자에게는 "그 공고가
  [R-BASIS-03] 관찰 :124  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-05']
      `paper_bidding_backtest`)에서 과세 공고의 capture가 `rate × 1.1 → 1.0` clamp로 부풀었다.
  [R-BASIS-04] 관찰 :142  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-05']
      기초금액-relative `price_range`를 자르면서 **하한까지 깎았다**(`price_lower = min(price_lower, budget_ca
  [R-BASIS-04] 관찰 :143  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-05']
      낙찰하한 × 기초금액이 추정가격을 넘는 과세 공고에서 추천가가 **법정 하한 아래로** 내려간다.
  [R-BASIS-06] 관찰 :181  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-ML-02', 'OPEN-QUAL-10']
      되고, 과세 공고에서는 그대로 ~10% 낮은 투찰가가 나온다. commit이 "이 저장소 실투찰 실격
  [R-RATE-01] 관찰 :224  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-03']
      - **관찰**: "값이 임계치보다 크면 백분율로 보고 `/100`" 규칙과 그 임계치가 **7곳에 독립
  [R-QUAL-06] 관찰 :738  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-ML-02']
      기반으로 교체하고 다중 그룹에서는 **누락이 가장 적은 그룹**을 대표로 쓴 것이다.
  [R-ML-04] 관찰 :1199  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-SET-06']
      **"측정할 수 없는 편향은 없다고 주장할 수 없으므로 회피한다"** — 성숙도 임계 미만 구간
  → 방향을 말하려면 대소 관계가 정해져 있어야 한다. **사람이 읽어 판정**한다.
```

**첫 줄이 medium #1이다**(`R-BASIS-01` `사용자 영향` `:84` ★, 엮인 활성 OPEN에
`OPEN-REG-05`). Codex가 적은 자리는 `:83`이고 같은 항목·같은 필드다.

**수정 뒤 실행:**

```
$ python3 openstance.py
활성 OPEN 정본: capability-map §12 45건 + OPEN-REG 5건

=== 결정 필드가 활성 OPEN을 언급하는 자리: 7건 ===
  [R-BASIS-01] 검증 방법 → OPEN-REG-05  조건부 표시 있음  :100
      - **조건부 — `OPEN-REG-05` 결정에 따라 확정.** **과세/비과세로 정의된 경계 쌍은 그 OPEN이
  [R-BASIS-04] 검증 방법 → OPEN-REG-05  조건부 표시 있음  :175
      공시하며, 과세 의미의 확정은 활성 `OPEN-REG-05`가 소유한다. **값 조건으로 고르면 그
  [R-PROV-02] 검증 방법 → OPEN-DEC-07  조건부 표시 있음  :380
      - **조건부 — `OPEN-DEC-07` 결정에 따라 확정.** legacy의 임계 **1.15**(부가세 1.10 +
  [R-COL-02] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :836
      - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** **분류 불가(`unknown`)를
  [R-COL-02] 검증 방법 → OPEN-OPS-01  조건부 표시 있음  :845
      - **조건부 — `OPEN-OPS-01` 결정에 따라 확정.** (b) fail-safe면 "`unknown`이 재시도
  [R-COL-03] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :866
      ※ 재시도 정책 자체(`unknown → retryable` 여부)는 **활성 `OPEN-OPS-01`이 소유**하며
  [R-ASYNC-01] V2 예방 제약 → OPEN-OPS-10  조건부 표시 있음  :981
      ※ DB 기반 큐의 backlog 관측·가시성 timeout 계약은 **활성 `OPEN-OPS-10`이 소유**하며

=== id 없이 주제가 겹치는 후보: 2건 (오탐 다수 — 사람 판정) ===
  [R-BASIS-01] ~ OPEN-QUAL-10  공통어: ['basis', '운영자']
  [R-COL-01] ~ OPEN-ML-05  공통어: ['policy', 'versioned']

=== 2차가 매칭할 수 없는 활성 OPEN: 8건 (특징어 2개 미만) ===
  OPEN-COL-03: 특징어 ['업무구분'] | 질문 업무구분 코드 전체 체계(현재 4개만 매핑)
  OPEN-COL-05: 특징어 ['limit'] | 질문 외부 API rate limit의 실제 수치
  OPEN-NOTI-06: 특징어 ['초기값'] | 질문 알림 임계 4종의 초기값
  OPEN-NUM-02: 특징어 ['unknown'] | 질문 자격 관련 수치 5종(92% unknown 포함)
  OPEN-OPS-03: 특징어 [] | 질문 큐 깊이 검출 SLO (**임계 미결**)
  OPEN-REG-04: 특징어 ['측정일'] | 질문 **"66% 오염"의 측정 방법·모수·측정일**
  OPEN-SET-01: 특징어 ['페이퍼'] | 질문 페이퍼 정산 근접 임계 3종의 도출 근거
  OPEN-SET-05: 특징어 ['재공고'] | 질문 재공고(차수 다수) 대사 대상 선택 규칙
  → 이 항목들은 **사람이 읽어 판정**한다. 기록은 evidence C-8.2.

=== 3차: 사실 주장 필드의 방향·대소 주장: 6건 ===
  [R-BASIS-03] 관찰 :136  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-QUAL-10', 'OPEN-REG-05']
      rate x 1.1 -> 1.0 clamp로 부풀어, 같은 공고가 어느 경로로 평가됐는지에 따라
  [R-BASIS-04] 관찰 :159  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-05']
      기초금액-relative `price_range`를 자르면서 **하한까지 깎았다**(`price_lower = min(price_lower, budget_ca
  [R-BASIS-06] 관찰 :203  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-ML-02', 'OPEN-QUAL-10', 'OPEN-REG-05']
      ~10% 낮은 투찰가가 나온다 — 이 저장소 실투찰 실격 이력과 같은 계열의 실패다."*
  [R-RATE-01] 관찰 :248  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-REG-03']
      - **관찰**: "값이 임계치보다 크면 백분율로 보고 `/100`" 규칙과 그 임계치가 **7곳에 독립
  [R-QUAL-06] 관찰 :762  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-ML-02']
      기반으로 교체하고 다중 그룹에서는 **누락이 가장 적은 그룹**을 대표로 쓴 것이다.
  [R-ML-04] 관찰 :1234  ★ 전제 확인 필요  엮인 활성 OPEN: ['OPEN-SET-06']
      **"측정할 수 없는 편향은 없다고 주장할 수 없으므로 회피한다"** — 성숙도 임계 미만 구간
  → 방향을 말하려면 대소 관계가 정해져 있어야 한다. **사람이 읽어 판정**한다.
```

**지목은 위 블록이 찍는다 — 셈을 여기 옮겨 적지 않는다**(형태 6). **전부 사람이 판정했고
아래가 그 판정**이다. `R-BASIS-03`·`R-BASIS-06`은 **실물**이라 고쳤고 나머지는 오탐이다.

| 지목 | 판정 | 근거 |
| --- | --- | --- |
| `R-BASIS-03` `관찰` ~ `OPEN-REG-05` | **실물 (verifier F-b)** | 처리는 **C-12.2**에 있다 — commit 본문 인용임을 드러내고 과세 의미를 `OPEN-REG-05`로 넘겼다. (이 칸은 한 라운드 동안 **C-11.3**을 가리켰다. 그 절은 `R-BASIS-06`만 다루므로 **포인터를 따라가면 근거가 없었다** — verifier F-1) |
| `R-BASIS-06` `관찰` ~ `OPEN-REG-05` | **실물 (verifier F-3)** | 처리는 **C-11.3**에 있다. 고친 뒤에도 찍히는 이유(축이 줄 단위라 인용부호 안의 어휘를 본다)도 그 절에 있다 |
| `R-BASIS-04` `관찰` ~ `OPEN-REG-05` | 오탐 | *"하한까지 깎았다"*는 `min` 연산의 **기계적 결과**다. 방향을 **코드가 정하지 과세 여부가 정하지 않는다.** 같은 항목의 값 조건 서술은 C-10.1에서 이미 고쳤다 |
| `R-RATE-01` `관찰` ~ `OPEN-REG-03` | 오탐 | *"값이 임계치보다 크면 백분율로 보고 `/100`"*은 **legacy 코드 규칙의 인용**이다. 이 문서가 대소 관계를 주장하는 것이 아니라 **legacy가 그렇게 분기한다는 관찰**이며, `OPEN-REG-03`과는 어휘만 겹친다 |
| `R-QUAL-06` `관찰` ~ `OPEN-ML-02` | 오탐 | *"누락이 가장 적은 그룹을 대표로 쓴 것"*은 **legacy 알고리즘의 서술**이다. 미결의 크기에 기대지 않는다 |
| `R-ML-04` `관찰` ~ `OPEN-SET-06` | 오탐 | *"성숙도 임계 **미만** 구간"*은 legacy가 **자기 원칙으로 적은 문장의 인용**이다. `미만`이 대소 어휘라 걸렸을 뿐 이 문서의 확정 서술이 아니다 |

**축이 못 보는 것(3차)**: **어휘 목록이 정본이라** 방향을 다른 말로 쓰면 놓친다.
**엮인 OPEN 판정은 주제 겹침**이라 오탐이 섞인다 — **위 표의 오탐 행들이 그것**이고
**셈은 옮겨 적지 않는다**(형태 6). **그리고 legacy 인용·서술과 이 문서의 주장을 기계가
가르지 못한다** — **위 표에서 근거가 *"legacy 인용"* 또는 *"legacy 서술"*인 행들이 정확히
그 경계**다.

> **정정 (verifier F-2)** — 이 자리는 한 라운드 동안 **"위 둘이 그렇다" · "위 두 오탐이
> 정확히 그 경계다"**로 남아 있었다. 표가 **둘에서 넷으로** 늘었는데 **지시 대상이 조용히
> 바뀌었고**, 둘째 문장은 **거짓이 됐다**(legacy 근거를 든 행이 둘이 아니라 셋이다).
> **F-a와 같은 형태 5이고, F-a가 두 줄 위를 고치면서 남긴 것**이다.
> **한글 수사 축이 못 잡은 이유는 사각지대 둘이 겹쳤기 때문**이다 — ① **창 12줄 밖**이고
> ② **조사 목록에 `이`가 없어** `둘이`·`두 오탐이`가 걸리지 않는다.
> **축을 넓히지 않았다**: 창을 넓히면 **지목이 늘고 늘어난 대부분이 오탐이다.**
> **관측된 뒤에 넓힌다**는 이 slice의 규약대로 **한계로만 적는다**(C-12.1).
> **방향 주장의 근거는 셈이 아니라 재현 명령**이다 — C-12.1의 「창을 넓히면 어떻게
> 되는가」에 3-변형 명령이 있다. (이 자리는 한 라운드 동안 `12줄 5건 → 25줄 15건`이라는
> **재현되지 않는 실측**을 근거로 달고 있었다 — Codex 3차 medium #2. **수를 지웠다.**)

### C-10.3 medium #2 — 타입 표지가 **안전성 자체를 대신**했다

`R-ASYNC-06`이 반복 실행의 안전을 **"idempotent임이 타입으로 선언된 작업"**으로만 보장하고
검증도 **선언 유무의 컴파일 검사**에 그쳤다. **표지는 비멱등한 write를 멱등하게 만들지
않는다.**

**승인된 지침서를 직접 열어 확인했다** — `v2-지침서.md` **§4.5 workflow와 side effect**
(`:215-223`, 다음 절 `## 5. 기술 표준`이 `:225`):

```
$ sed -n '222,223p' v2-지침서.md
- 외부 호출, DB write, event publish, notification은 port 뒤에 둔다.
- 재시도는 멱등성 key와 함께 설계하고, 알림 중복과 out-of-order event를 테스트한다.
```

**두 줄 다 걸린다** — 이 항목의 side effect가 정확히 그 넷이고, 반복 실행이 곧 재시도다.
(위 블록은 **실행 키가 없어 `resync`가 덮지 않는다.** 대신 **재현 명령을 블록 안에 함께
적었고**, `v2-지침서.md`는 out_of_scope라 이 slice가 편집하지 않는다 — M-C2가 지적한
부류이나 낡을 원인이 없는 자리다.)

**처리**: `V2 예방 제약`을 **계약 + 테스트**로 바꾸고 **논리 작업을 식별하는 안정적인
멱등성 key**(대상 식별자 + 작업 종류 + 처리 대상 시점/버전)와 **그 key의 적용 경계**(어느
side effect가 그 아래에서 중복 제거되는지)를 **port 계약에 선언**하게 했다.
`검증 방법`에는 **실제 scheduler/worker wiring으로 ① 중복 전달 ② 순서 뒤바꿈**을 주고
**DB write · event · notification이 중복되지 않는지**를 넣었다. **컴파일 검사는 key의
존재만 보증하고 중복 부재는 그 테스트가 보증한다**고 역할을 갈라 적었다.

### C-10.4 A3 전수 훑기 — 같은 형태가 더 있는가

**Codex의 판정이 맞다.** A3는 *"타입·계약·테스트 중 최소 하나의 구조적 형태"*를 요구하는데,
**선언 유무의 컴파일 검사는 타입 축의 형태를 갖췄으나 실질이 비어 있다** — 검사가 통과해도
**막으려는 사건(중복 side effect)은 그대로 일어날 수 있기** 때문이다. **형태는 "무엇을
적었는가"가 아니라 "무엇이 실제로 막히는가"로 판정해야 한다.**

**60건 + 예방책 1건을 전수로 훑었다.** 스크립트 본문(형태 4: 인라인):

```python
# A3 훑기 — `V2 예방 제약`이 **선언만으로** 끝나는가.
# Codex 2차 medium #2 가 R-ASYNC-06 에서 그 형태를 지적했다("타입 표지는 비멱등 작업을
# 멱등하게 만들지 않는다"). 같은 형태가 다른 항목에도 있는지 전수로 훑는다.
# 기계가 판정할 수 없는 것: 그 선언이 **실질을 담고 있는가**. 지목만 하고 사람이 읽는다.
import re, sys
LGP = "docs/discovery/regression-ledger.md"
def load(a):
    """인자가 git ref 면 그 커밋의 ledger 를 본다 — 수정 전 트리에서 유효성을 실증한다."""
    if not a: return open(LGP, encoding="utf-8").read()
    if re.fullmatch(r'[0-9a-f]{7,40}', a):
        import subprocess
        return subprocess.run(["git","show",f"{a}:{LGP}"],capture_output=True,text=True,check=True).stdout
    return open(a, encoding="utf-8").read()
lines = load(sys.argv[1] if len(sys.argv) > 1 else None).split("\n")
starts = [i for i, l in enumerate(lines) if re.match(r'^### [RP]-', l)]
FIELDS = ("V2 예방 제약", "검증 방법")
# 선언·표지 어휘 — 이것만 있으면 "적어 두었다"에 그친다
DECL = re.compile(r'선언|표시|명시|기록|붙인|남긴|표지')
# 작용 어휘 — 무언가가 실제로 막히거나 깨지거나 걸러진다
ACT = re.compile(r'컴파일 오류|타입 오류|컴파일 단계에서 막|거부|실패|막는다|막힌다'
                 r'|들어갈 수 없|올 수 없|될 수 없|불가능|차단|빨간불|깨진다|깨져'
                 r'|중복되지 않|중복 제거|재생성|되돌|탈락|걸러|통과할 수 없|오류가 된다'
                 r'|고정한다|고정$|테스트로 고정|property로 고정')
# 행위 테스트 어휘 — 선언 유무가 아니라 **동작**을 확인한다
BEH = re.compile(r'실행|재현|재전달|중복|순서|뒤바꿔|주입|재생|시나리오|경계값|fixture'
                 r'|corpus|property|왕복|round|골든|golden')
rows = []
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    eid = re.match(r'^### ([RP]-[A-Z]+-\d+)', lines[st]).group(1)
    buf, field = {f: [] for f in FIELDS}, None
    for k in range(st, e):
        l = lines[k]
        m = re.match(r'^- \*\*([^*]+)\*\*', l)
        if m: field = m.group(1).split("(")[0].split(" —")[0].strip()
        if field in FIELDS: buf[field].append(l)
    txt = {f: "\n".join(v) for f, v in buf.items()}
    both = txt["V2 예방 제약"] + "\n" + txt["검증 방법"]
    rows.append((eid, bool(DECL.search(both)), bool(ACT.search(both)), bool(BEH.search(txt["검증 방법"]))))
flag = [r for r in rows if r[1] and not r[2]]
weak = [r for r in rows if r[1] and r[2] and not r[3]]
print(f"항목 {len(rows)}건 · 선언 어휘 있음 {sum(r[1] for r in rows)}건")
print(f"\n=== ① 선언 어휘가 있는데 **작용 어휘가 없다** (선언만으로 끝날 후보): {len(flag)}건 ===")
for eid, *_ in flag: print(f"  {eid}")
print(f"\n=== ② 작용은 있으나 **검증이 동작을 보지 않는다**(선언 유무 검사에 그칠 후보): {len(weak)}건 ===")
for eid, *_ in weak: print(f"  {eid}")
print("\n  → 두 목록 다 **사람이 읽어 판정**한다. 기계는 어휘만 본다.")

```

**Codex가 본 트리(`4f4fd7f`)에서 `R-ASYNC-06`이 ②에 있다** — 축이 관측된 형태를 본다:

```
$ python3 a3check.py 4f4fd7f
항목 61건 · 선언 어휘 있음 19건

=== ① 선언 어휘가 있는데 **작용 어휘가 없다** (선언만으로 끝날 후보): 4건 ===
  R-BASIS-07
  R-FLOOR-01
  R-FLOOR-07
  R-ML-08

=== ② 작용은 있으나 **검증이 동작을 보지 않는다**(선언 유무 검사에 그칠 후보): 11건 ===
  R-BASIS-02
  R-BASIS-06
  R-RATE-03
  R-RATE-05
  R-FLOOR-04
  R-FLOOR-05
  R-FLOOR-06
  R-COL-02
  R-ASYNC-06
  R-ML-01
  R-ML-02

  → 두 목록 다 **사람이 읽어 판정**한다. 기계는 어휘만 본다.
```

**수정 뒤 — `R-ASYNC-06`이 ②에서 빠졌다:**

```
$ python3 a3check.py
항목 61건 · 선언 어휘 있음 19건

=== ① 선언 어휘가 있는데 **작용 어휘가 없다** (선언만으로 끝날 후보): 4건 ===
  R-BASIS-07
  R-FLOOR-01
  R-FLOOR-07
  R-ML-08

=== ② 작용은 있으나 **검증이 동작을 보지 않는다**(선언 유무 검사에 그칠 후보): 10건 ===
  R-BASIS-02
  R-BASIS-06
  R-RATE-03
  R-RATE-05
  R-FLOOR-04
  R-FLOOR-05
  R-FLOOR-06
  R-COL-02
  R-ML-01
  R-ML-02

  → 두 목록 다 **사람이 읽어 판정**한다. 기계는 어휘만 본다.
```

**남은 14건을 전부 열어 판정했다. 추가 발견 0건.**

| 목록 | 항목 | 판정 근거 |
| --- | --- | --- |
| ① | `R-BASIS-07` | architecture test가 **경로가 없음**을 고정하고, 저장 payload를 **다시 렌더링해 현재 계산과 일치**하는지 본다. 동작 검사다 |
| ① | `R-FLOOR-01` | 판정이 **생략되고 사유가 남는지** + 기본값 경로가 **타입상 존재하지 않음** |
| ① | `R-FLOOR-07` | 판정 결과에 policy version이 **실리는지** + 임계 변경이 **코드 변경 없이 가능한지** |
| ① | `R-ML-08` | **구현을 제거해도 계약이 바뀌지 않음** + 계약 변경이 **버전 변경을 요구**하는지 |
| ② | `R-BASIS-02` · `R-RATE-05` · `R-FLOOR-06` · `R-ML-01` | 전부 **거부**가 검증 대상이다(미표기 요청 · 로드 시점 구성 · 밴드 밖 override · 승격) |
| ② | `R-BASIS-06` · `R-FLOOR-05` · `R-ML-02` | 응답에 사실이 **실리는지** · 대입이 **타입 오류**인지 · 미선언 필드가 **컴파일 오류**인지 |
| ② | `R-RATE-03` · `R-FLOOR-04` | **경로가 존재하지 않음** · 소비자가 늘면 **기존 테스트가 깨지는지** |
| ② | `R-COL-02` | **가장 가까운 이웃이나 다르다.** 여기서 "선언의 존재"를 고정하는 것은 **값이 활성 `OPEN-OPS-01` 소유라 고정할 수 없기 때문**이고, 동시에 **재시도가 일어나면 그 사실과 횟수가 관측되는지**를 동작으로 고정한다. `R-ASYNC-06`은 **선언이 안전성 자체를 대신**했다 — 그 차이다 |

**①의 넷은 전부 오탐이다** — 축의 작용 어휘 목록에 **"…이 없음을 고정" · "존재하지 않음"**
부류가 빠져 있다. **목록을 늘려 ①을 비우지 않았다** — 도구를 답에 맞춰 깎으면 그 도구가
다음 라운드에 아무것도 잡지 못한다(라운드 3의 H-R2가 그 부류였다). **한계로 적고 판정을
남긴다.**

**이 축이 못 보는 것**: **어휘만 본다.** 제약이 구조적인지는 **그 문장이 가리키는 기제가
실재하는지**에 달렸고 그것은 M1 이후 구현이 판정한다. 이 축은 **"적힌 것이 동작을
말하는가"**까지만 본다.

### C-10.5 형태 기록 — 새 형태가 아니다

- **medium #1은 §10.1 형태 5**(정정을 인용 지점에 전파하지 않기)**의 변형**이다. 보통은
  *수를 고치고 인용 지점을 놓치는* 모양인데, 이번은 **근거를 철회하고 그 근거 위에 서 있던
  결론을 남긴 것**이다. **전파해야 할 것이 수가 아니라 논증의 의존 관계**였다.
  **새 형태를 만들지 않는다** — 뿌리가 같고, 처방도 같다(**철회할 때 그것에 기대던 문장을
  전수로 확인한다**).
- **medium #2는 A3 acceptance의 실질 문제**이지 §10.1의 실패 형태가 아니다. 근거의 지위도,
  미결의 선점도 아니고 **제약이 요구를 충족하는가**의 문제다. **형태 표를 건드리지 않는다.**


---

## C-11. Codex 2차 대응 재검증 (verifier F-1 · F-2 · F-3 · F-4)

**medium 2건의 수정 자체는 통과**했고 남은 넷은 **evidence 정합**이다. 넷 다 뿌리가
**어휘·문자 클래스의 좁음**이거나 **도구가 자기 자리를 다 덮지 않은 것**이다.

### C-11.1 F-1 — 한글 파일명이 두 축의 사각지대였다. **정규식을 넓히는 쪽을 골랐다**

`d205d31`이 넣은 `` `v2-지침서.md:215-223` `` 인용에 대해:

| 축 | 무슨 일이 났나 |
| --- | --- |
| `ledgercheck` | 예외 판정 정규식이 그 인용을 **경로로 보지 못해** 뒤따르는 `` `:222` ``·`` `:223` ``을 **자기참조로 오탐**했다. `checklist.md` A6가 그 절을 근거로 **"0"**을 주장해 **인용한 근거가 주장을 반증**하는 상태가 됐다 |
| `citecheck` | `PATH`가 같은 제약이라 그 인용을 **아예 보지 않았다** — **경로 존재도 행 범위도 어느 축이 검사하지 않았다** |

**판단: ③ 둘 다 — 정규식을 넓히고 세 자리를 맞췄다.**

**"도구를 답에 맞춰 깎는 것 아닌가"에 대한 답: 방향이 반대다.**

| | A3의 작용 어휘 (지난 라운드, **넓히지 않음**) | 여기의 경로 문자 클래스 (**넓힘**) |
| --- | --- | --- |
| 넓히면 | **지목이 줄어든다**(①이 비워진다) | **검사가 늘어난다**(안 보던 인용을 본다) |
| 무엇이 근거인가 | 판정을 바꾸고 싶은 마음 | **정본 정의의 결함** — `v2-지침서.md`는 **실제로 경로다** |
| 관측 | 오탐만 관측 | **오탐 + 미검사 인용**이 함께 관측 |

**결정적인 것은 두 번째 줄이다.** A3의 `ACT` 목록은 **어디까지가 "작용"인가**를 정하는
**판정 기준**이라 넓히는 순간 답이 달라진다. 여기 문자 클래스는 **"무엇이 파일 경로인가"**를
정하는 것이고 **한글 파일명이 경로가 아니라는 판정은 그냥 틀렸다.** 틀린 인식을 고치는 것은
기준을 무르게 하는 것과 다르다. 그리고 라운드 3의 H-R2가 경고한 것은 **축이 지목한 것을
축을 무디게 해서 닫는 것**인데, 여기서는 **축이 더 많이 보게 된다.**

**`citecheck`에 v2 경로 처리를 더했다.** legacy 트리에 없는 것이 **정상인** 이 저장소 문서를
`부재`로 몰지 않고, **commit 축이 v2 SHA를 가르는 것과 같은 방식으로** 갈라 **v2 HEAD에서
행 범위를 검사**한다. C-2.1 출력의 `v2 저장소 파일` 절이 그것이다 — **`v2-지침서.md:215-223`이
이제 실제로 검사된다.** 넓히기 전 이 인용의 유일한 검증은 C-10.3의 **키 없는 `sed` 두 줄**
이었다.

**맞춘 세 자리**: C-5.1에 오탐 판정 bullet · 같은 절의 한계 서술("지금은 그런 자기참조가
없고" → 검사가 `없음`을 내지만 그것을 증명하지는 않는다) · `checklist.md` A6 셀에
**그 칸이 `d205d31` 시점에 근거와 어긋나 있었다는 사실**. **수를 새로 옮겨 적지 않았다.**

**남는 한계**: 이 확장은 **한글**을 넣은 것이지 임의의 유니코드가 아니다. 다른 문자셋의
파일명은 여전히 같은 구멍이다 — **관측되면 그때 넓힌다.**

> **이 확장이 곧바로 자기 결함을 냈다.** v2 경로 절이 **행 범위 없는 인용에도 파일 길이를
> 찍었는데**, 그 목록에 **이 evidence 자신이 들어 있다** — 출력을 쓰면 자기 길이가 바뀌어
> **동기화가 고정점에 닿지 못했다**(커밋 직후 resync가 트리를 다시 더럽혔다).
> **행 범위가 없으면 길이를 찍지 않게** 고쳤다. 검사할 범위가 없는 자리에 수를 찍은 것이고,
> **필요 없는 수를 출력에 넣으면 그것이 곧 낡을 수**라는 이 slice의 반복 교훈과 같다.

### C-11.2 F-2 — 같은 실행 키가 두 곳인데 **첫 곳만 채웠다**

C-10.2의 「수정 뒤 실행」 블록이 **비어 있었다.** 출력 동기화가 `pat.search`로 **첫 매치
하나만** 갱신하는데 실행 키 `openstance.py`(인자 없음)가 **두 곳**에 있어 **둘째가 영구히
빈 채로** 남았다.

**"손으로 빠뜨릴 자리가 없다"(C-9.8)가 출력 블록에는 성립하지 않았다** — 유도 루프가 키를
**집합처럼** 다뤘기 때문이다. **`finditer`로 바꿔 같은 키의 모든 블록을 갱신**하고, **같은
키가 여러 곳이면 그 사실을 출력에 찍게** 했다(`같은 키 N곳 — 전부 갱신`). 뒤에서부터
치환해 오프셋이 밀리지 않게 했다.

**본문 셈(`전체 블록 · 차지한 것 · 발췌`)은 `python` 본문 블록만 센다** — 출력 블록의
빈 상태는 그 셈이 보지 못한다. **이제는 중복 키 줄이 그 자리를 대신 알린다.**

### C-11.3 F-3 — 훑기가 놓친 이유는 **둘 다**다

`R-BASIS-06`의 *"과세 공고에서는 그대로 ~10% 낮은 투찰가가 나온다"*는 medium #1이
`R-BASIS-01`에서 철회시킨 프레이밍과 **동형**인데 귀속도 유보도 없었다.

1. **3차 패스가 못 봤다.** `DIR` 어휘에 **`낮은`이 없었다** — 축이 스스로 적어 둔 한계
   (*"방향을 다른 말로 쓰면 놓친다"*)의 **실물**이다.
2. **손 훑기의 범위가 좁았다.** 나는 **`R-BASIS-01`·`R-BASIS-04`·`OPEN-REG-05` 등록 문면**
   셋만 열었다. **같은 계열 전체를 훑지 않았다** — C-10.5가 세운 처방(*"철회할 때 그것에
   기대던 문장을 전수로 확인한다"*)을 **내가 세워 놓고 좁게 적용했다.**

**둘 다 고쳤다.** `DIR`에 `낮은`·`높은` 부류를 더했고(**관측된 뒤의 확장이며 지목을 늘리는
방향**이다 — C-11.1과 같은 기준), 그 자리에 **legacy commit 본문의 인용임을 문면에 드러내고**
과세 의미·차이 크기가 `OPEN-REG-05` 소유임을 `R-BASIS-01`과 같은 처리로 적었다.

**넓힌 축이 새로 찍은 것은 `R-QUAL-06`의 "누락이 가장 적은 그룹"**인데 **legacy 알고리즘의
서술**이고 미결의 크기에 기대지 않는다 — **오탐**이다. `R-BASIS-06`은 고친 뒤에도 찍힌다:
**축이 줄 단위라 인용부호 안의 `낮은`을 보고, 유보(※)는 다음 줄에 있다.** **한계로 적는다** —
줄 단위 판정을 항목 단위로 바꾸면 유보 한 줄이 항목 전체를 가린다(그것이 F-2 라운드에
`claimcheck`에서 실제로 났다).

### C-11.4 F-4 — 제외를 걷어내니 사각지대가 하나 늘었다

이번 라운드의 보강 ②(`OPEN-REG` 제외 제거)가 **`OPEN-REG-04`를 사각지대 목록에 새로
드러냈는데** 아래 판정 표는 그대로였다. **판정 행을 더하고 절 머리·본문에서 셈을 걷어냈다** —
**그 목록은 축의 출력이 찍는다**(형태 6). **판정 자체는 바뀌지 않는다**: ledger §10.3이
`OPEN-NUM-01`과 같은 쟁점임을 적고 **승격 금지**를 유지하며 §10.4가 출처 포인터의 진전이
**해소 근거가 아님**을 명시한다 — **선점 없음.**

### C-11.5 형태 기록 — 넷 다 새 형태가 아니다

- **F-1·F-4는 §10.1 형태 5**(정정·확장을 그것에 기대던 자리에 전파하지 않기)다. 도구를
  바꾸면 **그 도구의 출력을 인용하는 자리**가 전부 대상이 되는데, `d205d31`은 인용을 넣고
  `checklist.md` A6를, 보강 ②는 사각지대 목록을 갱신하지 않았다.
- **F-2는 형태 4**(재현 불가능한 참조)에 인접하다 — 블록이 비어 근거가 그 자리에 없다.
  **처방은 도구 쪽이고 넣었다.**
- **F-3은 medium #1과 같은 부류**이며 **새로 관측된 것은 "내가 세운 전수 처방을 좁게
  적용했다"는 사실**이다. 형태를 늘리지 않고 **처방의 적용 범위를 문면에 못박는다**:
  **철회의 전수 확인 범위는 「같은 계열 전체」이지 「지적된 항목」이 아니다.**

---

## C-12. F-1~F-4 재검증 대응 (verifier F-a · F-b · F-c)

**셋 다 형태 5의 실물**이고 **둘은 이번 라운드가 낸 것**이다. 뿌리가 같다 —
**도구를 넓히면 그 출력을 인용하는 자리가 전부 대상이 된다.**

### C-12.1 F-a — 형태 6이 "수를 쓰지 않는 전칭"을 이미 금지하는데 축이 그걸 안 봤다

`openstance` 3차의 판정 문단이 **"남은 둘은 오탐이며"**라고 적는데 **바로 위 자기 출력
블록이 넷을 찍었다.** `DIR` 확장(F-3의 처방)이 **자기 판정 문단을 갱신하지 않은 것**이고,
넷 중 둘은 그 자리에 판정이 없어 **판독자에게 미판정 지목으로 남았다.**

**`prosecheck`가 못 잡았다** — 정본 대조가 **아라비아 숫자만** 본다. 그런데
`scope.md` 형태 6은 **"`뿐`·`전부`처럼 수를 쓰지 않는 전칭도 포함한다"**고 **이미 적고
있다.** **규칙은 있는데 축이 그 절반을 보지 않았다.**

**한글 수사 축을 더했다**(`prosecheck` 2차).

| | |
| --- | --- |
| **정본** | 출력 블록이 **스스로 헤더에 찍는 `N건`** — 그중 **마지막 헤더** |
| **대상** | 그 블록 **직후 12줄**의 산문 (블록을 논평하는 자리) |
| **어휘** | `하나`·`한`·`둘`·`두`·`셋`·`세`·`넷`·`네`·`다섯`…`열`, 뒤에 `건/개/곳/자리/항목/은/는/다/뿐/만`이 붙는 것만 |

**마지막 헤더를 정본으로 잡은 것이 핵심이다.** 처음엔 블록이 선언한 값을 **전부** 허용했는데,
`openstance` 출력처럼 한 블록이 절을 넷 찍으면 **어떤 수사든 그중 하나와 맞아 통과한다** —
**F-a가 정확히 그렇게 새어 나갔다**(2차가 `2건`이라 `둘`이 통과했다).

**전수 훑기**: 지목 **여섯**, 그중 **실물은 F-a 자신 하나**이고 다섯은 오탐이다 —
`실측 열`(낱말 뜻), `둘 다 고쳤다`·`하나만 겹쳐도`(블록이 아니라 다른 것을 가리킨다),
`2차가 남긴 후보 둘`(**앞 절**을 가리키는데 정본은 마지막 절이다). **인스턴스는 수를 지우고
블록을 가리키게 바꿨고**, 넷 전부에 판정을 넣고 **C-11.3 상호 참조**를 달았다.

**이 축이 못 보는 것 — 오차 방향이 갈린다.**

| 갈래 | 방향 |
| --- | --- |
| **낱말 뜻**(`열`이 열 개인지 세로줄인지) | **오탐(과지목)** — 지목이 남고 사람이 판정한다 |
| **가리키는 대상**(같은 블록의 앞 절이나 아예 다른 것) | **오탐(과지목)** — 같음 |
| **창 12줄 밖의 논평** | **미탐** — **아무것도 찍히지 않는다** |
| **정본이 `N건` 헤더로 찍히지 않는 블록** | **미탐** — 같음 |
| **조사 목록의 좁음**(`이`가 없어 `둘이`가 안 걸린다) | **미탐** — 같음 |

**앞 둘은 오탐 쪽, 뒤 셋은 미탐 쪽**이다. **미탐 갈래에는 "사람이 판정한다"는 후행절이
성립하지 않는다** — 지목 자체가 남지 않기 때문이다. **F-2가 그 반례의 실물**이고
**두 미탐 기제(창 밖 · 조사 목록)가 겹쳐 일어났다.**

> **정정 (verifier F-3)** — 이 자리는 한 라운드 동안 **"전부 오탐 쪽으로 틀린다"**로
> 적혀 있었다. 이 slice가 F-1 라운드에 **"지목이 줄어드는가 / 검사가 늘어나는가"**를
> 판정 기준으로 성문화해 놓고 **자기 축의 오차 방향을 그 어휘로 잘못 적은 것**이다.
> **축은 넓히지 않는다** — 창을 넓히면 **지목이 늘고 늘어난 대부분이 오탐**이며
> (아래 「창을 넓히면 어떻게 되는가」), 관측된 확장 근거가 없다.

#### 창을 넓히면 어떻게 되는가 — **수를 적지 않고 재현 명령으로 답한다**

창 폭은 이 축의 유일한 조절 손잡이다. 아래를 돌리면 **폭을 키울수록 지목이 늘고, 늘어난
쪽의 대부분이 오탐**이라는 것이 그 자리에서 확인된다.

**결과 수는 이 문서에 적지 않는다.** 한 라운드 동안 이 자리에 적혀 있던 수는 evidence가
더 작았을 때의 측정이었고, 이후 라운드들이 산문을 고치면서 **셋 다 움직였다**
(Codex 3차 medium #2). **문서가 자라고 편집되는 한 이 수는 계속 움직인다** — 정확한 값으로
바꿔 적어도 다음 편집에 다시 낡는다. 그래서 **수를 지우고 명령을 남긴다**(형태 6의
"전칭은 셈이 아니라 재현 명령으로"를 정량 주장에 확장한 것이며, 이 slice에서 재발이 끊긴
유일한 처방이다).

````bash
# **줄 번호를 쓰지 않는다.** scope.md 「0B에 고유한 것」이 자기참조 줄 번호를 금지하고,
# 실제로 이 파일은 라운드마다 편집돼 범위가 밀린다. 그래서 인라인된 prosecheck 본문을
# **위치가 아니라 내용**(`WIN = 12` 선언)으로 집는다 — resync 가 스크립트 본문 자리를
# 첫 줄로 특정하는 것과 같은 방식이다.
for W in 12 25 40; do
  printf '창 %s줄 → ' "$W"
  python3 - "$W" <<'PY' | python3 - | tail -1
import re, sys
t = open("reports/evidence/m0/0b/commands.md", encoding="utf-8").read()
b = [x for x in re.findall(r"```python\n(.*?)\n```", t, re.S)
     if re.search(r"^WIN = 12\b", x, re.M)]
assert len(b) == 1, f"prosecheck 본문 자리 특정 실패: {len(b)}"
print(re.sub(r"^WIN = 12\b", "WIN = " + sys.argv[1], b[0], count=1, flags=re.M))
PY
done
````

**출력 블록을 두지 않는 것이 의도다.** 이 자리에 출력을 붙이면 그 순간부터 다시 낡기
시작하고, `resync`는 **실행 키(`$ python3 <name>.py`)를 가진 블록만** 갱신하므로 이 3-변형은
루프가 덮지 못한다. **읽는 사람이 돌려서 본다.**

### C-12.2 F-b — 훑기가 또 놓쳤다. **축의 두 곳이 원인이고 하나가 더 무겁다**

`R-BASIS-03` 관찰이 *"과세 공고의 capture가 `rate × 1.1 → 1.0` clamp로 부풀었다"*를
**귀속 없이** 적었다 — `R-BASIS-06`과 **같은 부류이고 같은 계열이며 세 항목 이내**다.
**이번 라운드가 문면에 못박은 처방**(*"철회의 전수 확인 범위는 「같은 계열 전체」"*)이
**실행되지 않은 자리**다.

**원인 둘 — 순서가 중요하다.**

1. **`DIR` 어휘에 `부풀`이 없었다.** F-3에서 `낮은`·`높은`만 더한 확장의 한계 실물이다.
   **한 낱말씩 메우면 다음 낱말에 또 뚫린다** — 그래서 이번엔 **증감 어휘를 부류로**
   넣었다(`부풀|줄어|늘어|커지|작아|과대|과소|올라가|내려가|깎|치솟`).
2. **더 무거운 것 — 엮인 OPEN 판정이 조사 때문에 성립하지 않았다.** 3차는 그 항목이
   **활성 OPEN과 엮여 있을 때만** 본다. 그런데 특징어는 `결정 필요 사항`에서
   **조사가 붙은 채** 뽑힌다(`기초금액과`·`추정가격의`). 항목 본문은 `기초금액`·`추정가격`
   이라 **토큰이 하나도 안 맞고 tie가 성립하지 않아 항목 전체가 스캔에서 빠진다.**
   **한글 특징어는 어간(끝 한 글자를 뗀 형태)이 블록에 있으면 겹친 것으로** 보게 했다.

**①만 고쳤으면 여전히 못 잡는다** — 실행으로 확인했다. `부풀`을 넣은 뒤에도 `R-BASIS-03`은
지목되지 않았고, **어간 대조를 넣고서야 잡혔다.** `R-BASIS-01`이 F-3 라운드에 잡힌 것은
그 항목이 **`OPEN-REG-05`를 문면에 직접 적었기 때문**이고, **이름을 적지 않은 항목은
tie가 원리적으로 안 됐다.**

**처방은 `R-BASIS-06`과 같다** — `git -C bid-vector log -1 --format=%B 0755695`로 **커밋
본문을 직접 열어** 인용임을 문면에 드러내고, 과세 의미를 `OPEN-REG-05`로 넘겼다.
**이 항목의 논지(경로마다 분모가 달랐다)는 그 미결과 무관하게 성립한다**는 것도 적었다 —
유보가 논지를 약화시키지 않게.

**넓힌 축이 새로 찍은 `R-BASIS-04`의 "하한까지 깎았다"는 오탐**이다: 방향을 **`min` 연산이
정하지 과세 여부가 정하지 않는다.**

### C-12.3 F-c — 옛 정책 문장이 사실과 어긋났다

`scope.md`가 세 자리를 **"legacy 서술이므로 그대로 뒀다"**로 묶었는데 **이후 라운드에 셋 다
바뀌었다**(`1.15` → Codex 1차, `~10% 낮은` → F-3, `rate × 1.1` → F-b). **원문을 지우지 않고
정정 주석**을 달았다. **"legacy 서술이니 그대로"로는 부족하다**는 것이 이 라운드들의 결론이다 —
**귀속 없이 적으면 이 문서의 확정 서술로 읽힌다.**

`citecheck` 부재 5의 서술도 **`fixtures/manifest.yaml`을 덮지 않았다**(0C 소관 예정 경로라
이 저장소 문서도 `summary.py`도 아니다). 고쳤다. 참고로 든 오타와 파일 끝 리터럴도 정리했다.

### C-12.4 형태 기록 — 셋 다 형태 5다. **새 형태를 만들지 않는다**

- **F-a·F-c는 형태 5 그대로** — 정정·확장을 **그것에 기대던 자리**에 전파하지 않았다.
- **F-b는 형태 5의 처방을 좁게 실행한 것**이고, C-11.3이 이미 **적용 범위를 「같은 계열
  전체」로** 못박았다. **문면은 있었고 실행이 안 됐다** — 그래서 이번엔 **문면을 더 쓰는
  대신 축을 고쳤다.** 어간 대조가 그 처방의 **실행본**이다.
- **이 slice에서 재발이 끊긴 처방은 하나뿐이다** — **수를 지우고 블록을 가리키기.**
  F-a의 인스턴스도 그렇게 고쳤다.
