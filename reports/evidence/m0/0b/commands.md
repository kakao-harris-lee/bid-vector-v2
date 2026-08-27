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
PATH = re.compile(r'`([A-Za-z0-9_./-]+\.(?:py|md|toml|cfg|yaml|yml|json|txt))'
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

bad_path, bad_range, ok = [], [], 0
for (p, rng), where in cites.items():
    if p not in tree:
        # 저장소 밖 파일(이 저장소 자신의 문서 등)은 제외 후보로 표시
        bad_path.append((p, rng, where)); continue
    # `split("\n")` 은 trailing newline 때문에 실제 줄 수보다 **1 크다**
    # (`"one\n".split("\n")` → 2). 그만큼 이 축이 마지막 줄 +1 까지 통과시켰다.
    n = len(git("show", f"{REF}:{p}").stdout.splitlines())
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
경로 인용 114종 · commit 인용 26종

--- 경로: 존재 104 / 부재 10 ---
  [부재] reports/evidence/m0/0b/scope.md:None  ← [(None, 3)]
  [부재] milestone-0.md:None  ← [(None, 5), ('R-PROV-01', 322)]
  [부재] summary.py:None  ← [(None, 33)]
  [부재] reports/evidence/m0/0a2/decisions.md:None  ← [(None, 45)]
  [부재] reports/evidence/m0/0b/commands.md:None  ← [(None, 59)]
  [부재] 0a2/decisions.md:None  ← [('R-BASIS-01', 88), ('R-RATE-03', 259), ('R-RATE-05', 305)]
  [부재] fixtures/manifest.yaml:None  ← [('R-PROV-07', 439)]
  [부재] commands.md:None  ← [('R-FLOOR-06', 584), ('R-QUAL-07', 745)]
  [부재] data-extract.md:None  ← [('R-COL-06', 879), ('R-ML-07', 1243)]
  [부재] capability-map.md:None  ← [('R-ML-09', 1289), ('R-ML-09', 1325), ('R-ML-09', 1336)]
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
  것은 **legacy 트리에 있을 수 없는 것**들이다 — 이 저장소 자신의 문서와, §0.3이 모호성의
  **예시로 언급**하는 `summary.py`.
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
```

```
$ python3 prosecheck.py
=== 정본 (commands.md 출력 블록에서 추출) ===
  경로 존재: 104
  경로 부재: 10
  행 범위 내: 104
  행 범위 초과: 0
  legacy commit: 25
  legacy commit 존재: 25
  경로 인용: 114
  commit 인용: 26

=== 산문 대조 ===
  불일치 0건 (이력 서술 포함 — 사람 판정 대상)
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
        if any(re.search(r'`[A-Za-z0-9_./-]+\.(py|md):[\d,\-]+`', c) for c in ctx):
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
  이 문서 자신을 줄 번호로 가리키면 놓친다.** 지금은 그런 자기참조가 없고(§0.3이 금지),
  **없다는 것을 이 검사가 증명하지는 않는다.**
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
6
$ git diff --check ec115a7...HEAD | wc -l
0
$ git status --porcelain -- docs/discovery/regression-ledger.md reports/evidence/m0/0b/ | wc -l
0
$ wc -l docs/discovery/regression-ledger.md
1346 docs/discovery/regression-ledger.md
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
import re, collections
CM = "docs/discovery/capability-map.md"
LG = "docs/discovery/regression-ledger.md"

cm = open(CM, encoding="utf-8").read()
active = set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|', cm, re.M))
lg = open(LG, encoding="utf-8").read()
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
STOP = set("결정 필요 사항 여부 것인가 무엇 어떤 할 를 을 이 가 의 와 과 에 로 는 은 수 그 이번 V2 legacy".split())
topics = {}
for oid, q in rows:
    terms = {t for t in re.findall(r'[A-Za-z_][A-Za-z0-9_]{4,}|[가-힣]{3,}', q)
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
print(f"\n=== id 없이 주제가 겹치는 후보: {len(cands)}건 (오탐 다수 — 사람 판정) ===")
for (eid, oid), terms in sorted(cands.items()):
    print(f"  [{eid}] ~ {oid}  공통어: {sorted(terms)[:5]}")
```

```
$ python3 openstance.py
활성 OPEN 정본: capability-map §12 45건 + OPEN-REG 4건

=== 결정 필드가 활성 OPEN을 언급하는 자리: 4건 ===
  [R-COL-02] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :801
      - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** **분류 불가(`unknown`)를
  [R-COL-02] 검증 방법 → OPEN-OPS-01  조건부 표시 있음  :810
      - **조건부 — `OPEN-OPS-01` 결정에 따라 확정.** (b) fail-safe면 "`unknown`이 재시도
  [R-COL-03] V2 예방 제약 → OPEN-OPS-01  조건부 표시 있음  :831
      ※ 재시도 정책 자체(`unknown → retryable` 여부)는 **활성 `OPEN-OPS-01`이 소유**하며
  [R-ASYNC-01] V2 예방 제약 → OPEN-OPS-10  조건부 표시 있음  :946
      ※ DB 기반 큐의 backlog 관측·가시성 timeout 계약은 **활성 `OPEN-OPS-10`이 소유**하며

=== id 없이 주제가 겹치는 후보: 2건 (오탐 다수 — 사람 판정) ===
  [R-BASIS-01] ~ OPEN-QUAL-10  공통어: ['basis', '운영자']
  [R-COL-01] ~ OPEN-ML-05  공통어: ['policy', 'versioned']
```

**판정 — 추가 발견 0건.** 2차가 남긴 후보 둘은 **어휘만 겹치는 오탐**이다:

| 후보 | 판정 |
| --- | --- |
| `R-BASIS-01` ~ `OPEN-QUAL-10` | **오탐 — 다른 금액 축.** `OPEN-QUAL-10`은 **시공능력평가금액**(QUAL 축)의 unit·과세이고, R-BASIS-01의 제약은 **예산 필터 basis**로 운영자 결정 `OPEN-STR-01`(확정)에 선다. 라운드 4에 두 금액의 **차이 크기**를 `OPEN-QUAL-10` 소유로 넘긴 것은 **참조이지 선점이 아니다** |
| `R-COL-01` ~ `OPEN-ML-05` | **오탐 — 다른 정책 영역.** `OPEN-ML-05`는 **predictor 정책 값 33개**의 versioned policy 경계이고, R-COL-01은 **KONEPS `resultCode`**의 versioned policy로 운영자 결정 `OPEN-COL-02`(확정)에 선다. `versioned`·`policy` 두 낱말만 겹친다 |

1차가 지목한 `R-COL-03`·`R-ASYNC-01`은 **이미 "활성 OPEN이 소유한다"고 적고 있어** 선점이
아니다(축이 그렇게 표시한다).

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
> **본문 동기화를 그 루프에 넣었고**(`resync.sh`), **인라인된 여섯 스크립트를 실물과
> 전수 대조**했다 — **어긋난 것은 `citecheck` 하나뿐**이고 나머지 다섯
> (`ledgercheck`·`numsrc`·`prosecheck`·`statusdiff`·`openstance`)은 **바이트 동일**이었다.
>
> **변이 검사로 고침을 실증했다** — `eligibility.py:53-97`을 `:53-98`(파일 끝 +1)로 바꾸면
> **현행 본문은 `초과 1`로 적출**하고 **옛 본문은 `초과 0`으로 통과**시킨다:
>
> ```
> 현행(splitlines): 행 범위: 파일 길이 내 103 / 초과 1
>   [초과] app/services/classification/eligibility.py:53-98 (파일 97줄)  ← [('R-QUAL-07', 754)]
> 옛 버전(split)  : 행 범위: 파일 길이 내 104 / 초과 0
> ```

**그 틈을 실제로 쓴 인용이 있었는지 재 봤다** — 인용 끝과 파일 끝의 여유를 전수로 계산:

```
$ python3 - <<'PY'   # 여유 = 파일 줄 수 − 인용 범위 끝
행 범위 끝과 파일 끝의 여유(작을수록 경계에 가깝다) — 하위 6건:
  여유     0  app/services/classification/eligibility.py:53-97  (파일 97줄)
  여유     0  app/services/classification/text.py:108-117  (파일 117줄)
  여유     0  app/services/license_eligibility.py:447-511  (파일 511줄)
  여유     2  app/services/allocation_core.py:133-179  (파일 181줄)
  여유     4  app/core/constants.py:445  (파일 449줄)
  여유     4  app/core/inference_config.py:114-124  (파일 128줄)

음수(=초과) 건수: 0
여유 0(=마지막 줄까지 인용) 건수: 3
PY
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
