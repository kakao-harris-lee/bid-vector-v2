# M0 / 0A2 — 검증 명령과 출력

규격: `.claude/skills/evidence-pack/SKILL.md`. 문서 slice이므로 acceptance command 대신
`checklist.md`가 A1~A7을 대조하고, 이 파일은 그 대조에 쓴 명령과 **실제 출력**을 담는다.

- base: `6af7019` (0A 동결 head)
- 대상: `docs/discovery/capability-map.md`, `reports/evidence/m0/0a/commands.md`(H-2 append 한정)

---

## 2026-08-26 — A2 검증 (미결 OPEN 임의 해소 0건)

**A2가 이 slice의 실패 조건**이므로 수치가 아니라 **id 집합**을 대조한다.

```
$ python3 - <<'PY'
import re, subprocess
def reg(rev):
    t = subprocess.run(["git","show",f"{rev}:docs/discovery/capability-map.md"],
                       capture_output=True, text=True).stdout if rev \
        else open("docs/discovery/capability-map.md", encoding="utf-8").read()
    return set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|', t, re.M))
before, after = reg("6af7019"), reg(None)
decided = set(""" ... 결정 기록 29개 id ... """.split())
gone, new = before - after, after - before
print(f"base(6af7019) {len(before)} -> head {len(after)}")
print(f"소멸 {len(gone)}건 / 신규 {len(new)}건")
print("결정 기록에 없는데 소멸(=임의 해소):", sorted(gone - decided) or "0건")
print("결정 기록에 있는데 잔존:", sorted(decided - gone) or "0건")
print("신규 id:", sorted(new) or "없음")
print("OPEN-OPS-03 잔존:", "OPEN-OPS-03" in after)
PY
base(6af7019) 66 -> head 37
소멸 29건 / 신규 0건
결정 기록에 없는데 소멸(=임의 해소): 0건
결정 기록에 있는데 잔존: 0건
신규 id: 없음
OPEN-OPS-03 잔존: True
```

- exit: 0
- **소멸 29 / 신규 0 / 임의 해소 0.** 소멸 집합이 결정 기록 29건과 **정확히 일치**한다 —
  양방향으로 확인했다(기록에 없는데 소멸 0건, 기록에 있는데 잔존 0건).
- **`OPEN-OPS-03`이 잔존한다.** 입력만 부분 확정이므로 해소하지 않았다.
- `decided` 집합은 `decisions.md`에서 옮긴 29개 id다. 축약한 부분은 그 파일과
  `capability-map.md` §12.2 표가 정본이다.

### 그룹별 내역

```
$ python3 - <<'PY'   # G1~G6 표에서 제거된 행
PY
G1: 7 -> 3  (해소 4)  QUAL-01, QUAL-02, COL-02, DEC-09
G2: 10 -> 6 (해소 4)  DEC-01, DEC-02, DEC-07, STR-05
G3: 16 -> 11 (해소 5) STR-07, QUAL-03, QUAL-08, STR-11, ML-04
G4: 17 -> 7 (해소 10) NOTI-02, STR-03, STR-06, STR-08, DEC-03, DEC-04, DEC-05, DEC-06, SET-02, DEC-08
G5: 9 -> 7  (해소 2)  NUM-04, QUAL-06
G6: 7 -> 3  (해소 4)  ML-01, OPS-05, OPS-07, STR-01
합계: 66 -> 37
```

- exit: 0. §12 머리말의 `(G1 3 / G2 6 / G3 11 / G4 7 / G5 7 / G6 3)`과 일치한다.

---

## 2026-08-26 — A6 불변 + A4 집계 재검증

**이번 라운드는 집계가 의도적으로 바뀐다.** "불변"과 "의도된 변경"을 구분해 적는다.

```
$ python3 inv2.py
분류: {'V2 필수': 62, '근거 부족': 10, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 37
```

**불변(A6)** — 변하지 않아야 하고 변하지 않은 것:

| 항목 | 값 | 판정 |
| --- | --- | --- |
| 분류 줄 형식 위반 | **0** | 불변 |
| 중복 capability id | **0** | 불변 |
| `V2 필수` 전건의 사용자 가치·acceptance | **결측 0** | 불변 (단 QUAL-11은 아래 참조) |
| secret 스캔 (`docs/discovery/`) | 매치 **3** | 불변 — 라운드 0부터 동일한 일반 명사 |

**의도된 변경(A4)** — 결정 기록에 근거가 있는 것:

| 항목 | 이전 | 현재 | 사유 |
| --- | --- | --- | --- |
| capability 수 | 94 | **95** | **STR-16 신규**(`V2 필수`) — pull 모델의 주 경로(§0.7) |
| `V2 필수` | 63 | **62** | +STR-16, +QUAL-11, −NOTI-02/05/06 |
| `후속` | 14 | **17** | NOTI-02·NOTI-05·NOTI-06 재분류 |
| `폐기` | 6 | **6** | 불변 |
| `근거 부족` | 11 | **10** | −QUAL-11(`OPEN-QUAL-08` 분할 확정) |
| 활성 OPEN | 66 | **37** | 결정 29건 해소 |

- exit: 0
- **QUAL-11 주의**: 이전에는 `근거 부족`이라 acceptance 절이 **없었다**(verifier 라운드 5가
  §5.4에서 그 사실을 기록했다). `V2 필수`로 올라가면서 A6가 acceptance를 요구하므로
  **이번에 6항목을 신설**했다. 결정(자격/적합도 분할)에서 직접 유도되는 항목만 적었고,
  "요건 미달이 즉시 실격인지 감점인지"는 근거가 없어 **미결로 남겼다**.
- **STR-11은 분류를 바꾸지 않았다.** `OPEN-STR-11`이 해소됐으나 닫힌 것은 `OPEN-STR-07`
  의존 축이고, 이 블록의 `근거 부족` 근거인 "운영자 실사용 여부"에 대한 판단이 결정
  기록에 없다. 기록에 없는 것을 결정하지 않는다.

---

## 2026-08-26 — 조건부 묶음 전수 확인 (§0.5 목록 정합)

조건부의 근거였던 OPEN이 닫혔으면 조건부를 유지할 이유가 없다. 전수로 확인했다.

```
$ grep -n '조건부 — `OPEN-' docs/discovery/capability-map.md
70:- **조건부 — `OPEN-*` 결정에 따라 확정** — 결정 전 **임시 시나리오**임을 명시하고,   ← §0.5 규약 정의
978:  - **조건부 — `OPEN-ML-03` 결정에 따라 확정.** …
1663:  - **조건부 — `OPEN-NOTI-08` 결정에 따라 확정.** …
1670:  - **조건부 — `OPEN-NOTI-01` 결정에 따라 확정.** …
2331:  - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** …
```

- exit: 0
- 실제 조건부 묶음 **4개 / capability 3개**(ML-03, NOTI-04 ×2, OPS-09). `:70`은 §0.5의
  규약 정의문이지 묶음이 아니다.
- **§0.5 목록과 일치한다** — 7개에서 3개로 줄었고, 사라진 4개(QUAL-03 / STR-08 / OPS-03 /
  DEC-02)는 전부 근거 OPEN이 §12.2에서 해소됐다.
- 잔존 3개의 근거 OPEN(`OPEN-ML-03`, `OPEN-NOTI-01`, `OPEN-NOTI-08`, `OPEN-OPS-01`)은
  **전부 §12 활성 목록에 있다** — 조건부가 근거 없이 남아 있지 않다.

---

## 2026-08-26 — 스윕 축 재실행 (0A 라운드 5·6의 F1 · F2' · X)

0A가 만든 축을 그대로 재실행한다. **모집단이 바뀌었으므로(활성 OPEN 66 → 37, 조건부 보유
블록 7 → 3) 수치 비교는 의미가 없다.** 새 위반이 나오는지만 본다.

```
$ python3 f1.py docs/discovery/capability-map.md
조건부 보유 블록: 3
중첩 3개 이상 (무조건 항목 × 같은 블록의 쟁점 어휘): 5

$ python3 sweep_r5.py docs/discovery/capability-map.md f2
  [ML-03] ['OPEN-ML-03']
  [OPS-09] ['OPEN-OPS-01']
  적출: 2 건

$ python3 sweep_r5.py docs/discovery/capability-map.md x
확정 서술 절 수: 174 | 활성 OPEN: 37 | 중첩 5 이상 적출: 14
```

- exit: 0. **새 위반 0건.** 적출 21건(F1 5 + F2' 2 + X 14)을 전수로 읽었고, 전부
  ① 잔존 조건부가 자기 OPEN을 정상 인용하거나 ② 파일:라인이 붙은 legacy 관찰이다.
- **축의 한계는 0A 라운드 5·6이 기록한 그대로다**(`reports/evidence/m0/0a/checklist.md`
  §11.3, §12.1). 이 축들은 **대리 지표 기반 triage 필터**이며 계열 A의 부재를 증명하지
  않는다. 적출 0건은 "임계 위에서 읽을 후보가 없었다"는 뜻이지 "위반이 없다"는 뜻이
  아니다. **0A2가 이 한계를 뒤집지 않는다.**
- 다만 이 slice는 계열 A의 **뿌리**를 줄였다 — 활성 OPEN이 66 → 37이므로 "미해결 쟁점을
  확정 서술이 선점할" 표면 자체가 44% 줄었다. 축이 아니라 **결정**이 그 일을 했다.

---

## 2026-08-26 — 규모·secret·clean tree

```
$ git diff --stat 6af7019 -- docs/discovery/capability-map.md
 1 file changed, 376 insertions(+), 129 deletions(-)

$ wc -l docs/discovery/capability-map.md
2872

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3

$ git status --porcelain -- docs/discovery/capability-map.md reports/evidence/m0/0a2/ reports/evidence/m0/0a/commands.md
(출력 없음 — 커밋 후)
```

```
$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m0/0a2/
scope.md:49:  acceptance scenario 존재, secret 스캔 통과.
checklist.md:108:| secret 스캔 | `docs/discovery/` 매치 **3**(…) |
commands.md:86:| secret 스캔 (`docs/discovery/`) | 매치 **3** | 불변 — … |
commands.md:163:## 2026-08-26 — 규모·secret·clean tree
commands.md:172:$ grep -rniE "(api[_-]?key|secret|…)" docs/discovery/ | wc -l
commands.md:179:- exit: 0. … secret 매치 3건은 …
  (합계 6)
```

- exit: 0. 2,625 → **2,872줄**(+247).
- `docs/discovery/` 매치 **3건**은 라운드 0부터 동일한 일반 명사(외부 API 오류 문구 인용
  1건, webhook 인증 메커니즘 설명 2건)이며 신규 0건이다.
- **evidence 디렉터리 매치 6건은 전부 자기참조다** — 스캔 명령 문자열 자체, 그 결과를
  인용한 기록, 절 제목, A6 항목명. 실제 자격증명은 0건이다(0A와 같은 성격).
  **초안에 이 스캔을 돌리지 않고 넘어갈 뻔했다** — 0A의 H-1·H-2가 정확히 "실행하지 않고
  기재"한 계열이었으므로 실행하고 실제 출력을 붙인다.
- `decisions.md`에는 운영자 진술과 legacy 파일 경로·commit만 있고 자격증명·식별자가
  없다(패턴으로 잡히지 않으므로 육안 확인). Telegram id·사업자 정보 없음.

---

## 2026-08-26 — 수정 라운드 2 검증 (verifier H-1·H-2·M-1~M-5·L-1·L-2)

verifier 판정 `not-ready`. **A2는 통과**(독립 재현됨)이고 A1·A4·A5·A6·A7도 충족이었다.
발견 9건 전부를 처리했다.

### R2-1. H-1 · H-2 · M-1 — legacy 실측으로 정정

**세 건 다 부재 주장 또는 귀속 오류였다. 완화 없이 실측으로 교체했다.**

```
$ cd bid-vector

# H-1 — STR-16의 "legacy에 대응 구현이 없다" 반증
$ git show ed4b06c:app/api/projects.py | grep -n 'q:\|agency:\|budget_min\|budget_max\|ilike\|X-Total-Count'
63:    q: Optional[str] = Query(                       # 제목/공고번호 부분 일치(LIKE)
67:    agency: Optional[str] = Query(                  # 발주/수요 기관명 부분 일치
71:    budget_min: Optional[float] = Query(
74:        description="budget_estimate >= 값으로 필터",
76:    budget_max: Optional[float] = Query(
79:        description="budget_estimate <= 값으로 필터",
86:    count is exposed via the `X-Total-Count` response header so the frontend
100:            or_(Project.title.ilike(like), Project.notice_number.ilike(like))
107:                Project.issuing_agency.ilike(agency_like),
108:                Project.demand_agency.ilike(agency_like),
113:        query = query.filter(Project.budget_estimate >= budget_min)
116:        query = query.filter(Project.budget_estimate <= budget_max)

$ git show ed4b06c:app/api/routes.py | grep -n 'projects.router'
38:router.include_router(projects.router, prefix="/projects", tags=["Projects"])

# H-2 — 21,321건 귀속
$ git grep -n "21,321\|21321" ed4b06c
app/core/inference_config.py:84,96,105,114
app/services/task_queue_depth.py:6      "the similarity projection backfill pinned the one
                                         inference worker for four hours and
                                         bid_vector_ml_inference accumulated 21,321 messages"
app/tasks/pipeline_schedules.py:30
tests/test_pipeline_sweep_expiry.py:4
tests/test_similarity_backfill_overlap_guard.py:5
tests/test_task_queue_depth.py:3,129
$ git log -1 --format='%h %s' 19f2c94
19f2c94 fix(similarity): 백필 폭주 수습 — 큐 21,321건 적체의 코드 원인 제거 (P1~P4) (#368)

# M-1 — legacy on-demand 경로
$ git show ed4b06c:app/api/predictions.py | sed -n '20,26p'
@router.post("/price", response_model=PricePredictionResponse)
def predict_project_price(request, db, workflow): return workflow.predict_project_price(db, request)
$ git show ed4b06c:app/api/routes.py | grep -n 'predictions.router'
40:router.include_router(predictions.router, prefix="/predictions", tags=["AI Predictions"])
$ git show ed4b06c:app/services/prediction_workflow.py | grep -n 'build_bid_target_menu'
18:from app.ai.bid_target import build_bid_target_menu
109:            menu = build_bid_target_menu(
```

- exit: 0
- **H-1**: `list_projects`가 STR-16의 검색 절반을 이미 구현한다. 부재 주장 2곳을 실제
  형태 관찰로 교체하고, 조사가 놓친 이유(파이프라인 축으로 훑었고 이 엔드포인트가 어느
  stage에도 속하지 않는다)를 적었다. `V2 필수` 분류와 STR 축 배정은 유지했다.
- **H-1 파생 — 닫혔던 조사 표면을 열었다.** 검색 API의 금액 필터가
  `Project.budget_estimate`(추정가격)와 비교한다. 이번에 `legacy-defect`로 판정한
  `filters.py`와 **같은 결함**이다. §13 0B 인계 행을 **세 경로**로 다시 썼다 —
  capture(R-07 `0755695`, **수정됨**) / 감시(`filters.py`, 미수정) /
  검색(`projects.py:112-116`, 미수정). STR-16 acceptance에도 basis 고정 항목을 넣었다.
- **H-2**: 21,321건은 **유사공고 임베딩 백필** 적체다. "그 뿌리가 사라진다"는 완화를
  철회했다. `OPEN-OPS-03`·`OPEN-OPS-04`의 근거 기반은 완화 이전 상태 그대로다.
- **M-1**: legacy에 on-demand 경로가 이미 있다. 바뀌는 것은 **배치 선산출의 제거**이므로
  "의도적 재설계" 프레이밍을 그 범위로 좁혔다.
- **경위**: H-2와 M-1은 팀 리드 분석 단계의 사실 오류가 `decisions-log.md`를 거쳐 0A2에서
  **정본 산출물로 승격**된 것이다. 승격 전에 legacy 원본을 대조하지 않은 것이 원인이며,
  `decisions.md`에 경위를 절로 남기고 원본 문장은 취소선으로 보존했다(감사 추적).

### R2-2. M-3 — OPEN registry 밖으로 이탈한 미결 2건 신설

```
$ python3 - <<'PY'   # base 대비 id 집합
base 66 -> head 39 | 소멸 29 | 신규 ['OPEN-QUAL-09', 'OPEN-STR-12']
```

- exit: 0
- **소멸은 여전히 29건이고 임의 해소 0건이다.** 신규 2건은 **정당한 신설**이며 임의
  해소의 반대 방향이다.

| 신규 id | 사유 |
| --- | --- |
| `OPEN-QUAL-09` | `OPEN-QUAL-08` 분할 확정이 "시공능력평가액은 자격 축"까지만 정하고 **요건 미달의 처리**를 정하지 않았다. 결정 원본이 이 하위 질문을 넘긴 `OPEN-QUAL-03`이 **같은 라운드에 해소돼 수신처가 사라졌다** |
| `OPEN-STR-12` | `OPEN-STR-11`이 `OPEN-STR-07` **의존 축**에서 해소됐으나 STR-11의 `근거 부족` 근거인 **운영자 실사용 여부**는 미결이다. `근거 부족`↔OPEN 짝 구조(§0.4 규칙 3·4)를 복원한다 |

**왜 이것이 중요한가**: `milestone-0.md` 완료 조건이 **OPEN 수를 게이트로 쓴다.** 미결이
registry 밖에 있으면 활성 수가 실제 미결을 과소 계상하고, 게이트가 잘못된 수로 판정된다.
계열 A(미해결을 확정 서술이 선점)의 **거울상**이다 — 확정 서술이 미결을 덮는 대신 **결정이
미결의 수신처를 지운** 형태다.

### R2-3. M-2 · M-4 · M-5 · L-1 · L-2

| # | 처리 |
| --- | --- |
| **M-2** | QUAL-11 분류 상향의 **축어 인용을 `decisions.md`로 옮겼다**(원본은 gitignore 대상 `g1-g3.md`에만 있었다). A7의 취지가 정확히 이것이다 — 인용이 커밋 밖을 가리키면 리뷰어가 근거를 못 찾는다. 부수로 "§0.4 **규칙 4**" 귀속을 뺐다 — base 블록은 규칙 번호를 인용하지 않았고 §9.1 상충 표에 올라 있으므로 성격은 규칙 3 계열이다 |
| **M-4** | DEC-02의 `잠정` 항목을 "결정 무관(무조건)" 묶음에서 빼내 **`잠정 — OPEN-DEC-03 재결정 대상`** 별도 묶음으로 분리했다. §0.5 머리말에 **네 번째 형식**으로 규약화했다 — "결정 무관"은 정의상 어떤 결정에도 성립하는 확정 요구라 "재결정 시 다시 열린다"고 스스로 적는 항목이 들어갈 수 없고, 조건부도 아니다(조건부는 **아직 결정되지 않은** 쟁점의 임시 시나리오다) |
| **M-5** | 0A `commands.md`의 **틀린 원문 지점에 정정 절로 가는 포인터를 append**했다. 재작성이 아니므로 scope 제약과 충돌하지 않는다 |
| **L-1** | 절 번호 순서 정정 — §0.7이 §0.6 앞에 삽입돼 있었다. 블록을 §0.6 뒤로 옮겼다 |
| **L-2** | `OPEN-OPS-03` registry 행을 갱신했다(**해소하지 않았다**) — "실시간 경보 필요 여부는 운영 형태에 달렸다"는 입력이 **1인 운영으로 확정**됐고 §12.2 인접 표가 그 사실을 적는다. 남은 것이 **구체 임계**임을 행에 명시했다 |

### R2-4. 불변 재확인

```
$ python3 inv2.py
분류: {'V2 필수': 62, '근거 부족': 10, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 39

$ grep -n '^### 0\.[567] ' docs/discovery/capability-map.md
63:### 0.5 OPEN과 acceptance scenario의 관계
98:### 0.6 축과 ID 접두사
111:### 0.7 상호작용 모델 — **pull** (운영자 결정 2026-08-26)

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3
```

- exit: 0
- **capability 95 · 분류 62/17/6/10 불변** — 라운드 2는 분류를 하나도 바꾸지 않았다.
- **활성 OPEN 37 → 39**(M-3 신설 2건). 이것이 라운드 2의 유일한 의도된 집계 변경이다.
- 절 번호 순서 정상. secret 매치 3건 불변.
