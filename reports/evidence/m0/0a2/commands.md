# M0 / 0A2 — 검증 명령과 출력

규격: `.claude/skills/evidence-pack/SKILL.md`. 문서 slice이므로 acceptance command 대신
`checklist.md`가 A1~A7을 대조하고, 이 파일은 그 대조에 쓴 명령과 **실제 출력**을 담는다.

- base: `6af7019` (0A 동결 head)
- 대상: `docs/discovery/capability-map.md`, `reports/evidence/m0/0a/commands.md`(H-2 append 한정)

---

## 2026-08-26 — A2 검증 (미결 OPEN 임의 해소 0건)

**(라운드 1 시점 기준 — 활성 OPEN 37 · `capability-map.md` 2,872줄. 그 뒤 라운드 2가 OPEN 2건을 신설했고 라운드 2~4가 문서를 늘렸으므로 아래 출력은 현재 HEAD에서 재실행해도 그대로 나오지 않는다. 재실행으로 덮지 않고 그 시점의 기록으로 보존한다 — 현재 값은 이 파일의 라운드 2·3 절과 `checklist.md` §11.2에 있다.)**

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

**(라운드 1 시점 기준 — 활성 OPEN 37 · `capability-map.md` 2,872줄. 그 뒤 라운드 2가 OPEN 2건을 신설했고 라운드 2~4가 문서를 늘렸으므로 아래 출력은 현재 HEAD에서 재실행해도 그대로 나오지 않는다. 재실행으로 덮지 않고 그 시점의 기록으로 보존한다 — 현재 값은 이 파일의 라운드 2·3 절과 `checklist.md` §11.2에 있다.)**

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

**(라운드 1 시점 기준 — 활성 OPEN 37 · `capability-map.md` 2,872줄. 그 뒤 라운드 2가 OPEN 2건을 신설했고 라운드 2~4가 문서를 늘렸으므로 아래 출력은 현재 HEAD에서 재실행해도 그대로 나오지 않는다. 재실행으로 덮지 않고 그 시점의 기록으로 보존한다 — 현재 값은 이 파일의 라운드 2·3 절과 `checklist.md` §11.2에 있다.)**

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

**(라운드 1 시점 기준 — 활성 OPEN 37 · `capability-map.md` 2,872줄. 그 뒤 라운드 2가 OPEN 2건을 신설했고 라운드 2~4가 문서를 늘렸으므로 아래 출력은 현재 HEAD에서 재실행해도 그대로 나오지 않는다. 재실행으로 덮지 않고 그 시점의 기록으로 보존한다 — 현재 값은 이 파일의 라운드 2·3 절과 `checklist.md` §11.2에 있다.)**

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
  확정 서술이 선점할" 표면 자체가 **44%** 줄었다. 축이 아니라 **결정**이 그 일을 했다.
  (**라운드 4 정정, verifier L-5r**: 라운드 3이 `44%`만 `41%`로 고치고 앞의 `37`을 두어
  자기모순이 됐다. 이 절은 **라운드 1 시점 기록**이므로 그 시점 값 `66 → 37 = 44%`로
  되돌린다. **현재 값은 `66 → 43 = 35%`**이며 `checklist.md` §9가 그렇게 적는다.)

---

## 2026-08-26 — 규모·secret·clean tree

**(라운드 1 시점 기준 — 활성 OPEN 37 · `capability-map.md` 2,872줄. 그 뒤 라운드 2가 OPEN 2건을 신설했고 라운드 2~4가 문서를 늘렸으므로 아래 출력은 현재 HEAD에서 재실행해도 그대로 나오지 않는다. 재실행으로 덮지 않고 그 시점의 기록으로 보존한다 — 현재 값은 이 파일의 라운드 2·3 절과 `checklist.md` §11.2에 있다.)**

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

> **라운드 3 재실행 (verifier M-2r)**: 아래 블록은 **라운드 3 시점 HEAD에서 실제로
> 재실행한 출력**이다. 라운드 2 기록은 내용은 정확했으나 `$` 프롬프트 아래 붙은 것이
> 실행 출력이 아니라 **손으로 압축한 요약**이었다. 접어서 보여주는 곳은 그렇다고
> 아래에 명시한다.

```
$ cd bid-vector

# H-1 — STR-16의 "legacy에 대응 구현이 없다" 반증
$ git show ed4b06c:app/api/projects.py | grep -nE "q: |agency: |budget_min|budget_max|ilike|X-Total-Count"
63:    q: Optional[str] = Query(
67:    agency: Optional[str] = Query(
71:    budget_min: Optional[float] = Query(
76:    budget_max: Optional[float] = Query(
86:    count is exposed via the `X-Total-Count` response header so the frontend
100:            or_(Project.title.ilike(like), Project.notice_number.ilike(like))
107:                Project.issuing_agency.ilike(agency_like),
108:                Project.demand_agency.ilike(agency_like),
112:    if budget_min is not None:
113:        query = query.filter(Project.budget_estimate >= budget_min)
115:    if budget_max is not None:
116:        query = query.filter(Project.budget_estimate <= budget_max)
119:    response.headers["X-Total-Count"] = str(total)

$ git show ed4b06c:app/api/routes.py | grep -n "projects.router\|predictions.router"
38:router.include_router(projects.router, prefix="/projects", tags=["Projects"])
40:router.include_router(predictions.router, prefix="/predictions", tags=["AI Predictions"])

# H-2 — 21,321건 귀속
$ git grep -n "21,321\|21321" ed4b06c
ed4b06c:app/core/inference_config.py:84:    # 소진을 넘으면 큐가 무한히 자란다(실제로 21,321건). 60초 유지 시 투입이 6,000건/시로
ed4b06c:app/core/inference_config.py:96:    # 21,321건이 며칠 쌓이는 동안 아무 신호도 없었다(2026-08-13). 기존 점검은 전부
ed4b06c:app/core/inference_config.py:105:    #   사고 실측(21,321건) 대비 2.3% 지점이므로 같은 사고가 재발하면 훨씬 얕은 깊이에서
ed4b06c:app/core/inference_config.py:114:    # 주기 sweep 태스크 메시지의 수명 = 주기 × 이 배수. 21,321건은 소비자가 막힌
ed4b06c:app/services/task_queue_depth.py:6:for four hours and ``bid_vector_ml_inference`` accumulated 21,321 messages. The
ed4b06c:app/tasks/pipeline_schedules.py:30:    proportion to its downtime — that is literally how 21,321 messages reached
ed4b06c:tests/test_pipeline_sweep_expiry.py:4:동안 beat 는 계속 밀어 넣었고 ``bid_vector_ml_inference`` 에 21,321건이 쌓였다.
ed4b06c:tests/test_similarity_backfill_overlap_guard.py:5:inference worker for four hours and 21,321 events piled up behind them.
ed4b06c:tests/test_task_queue_depth.py:3:21,321 messages accumulated over days behind a blocked consumer and no check
ed4b06c:tests/test_task_queue_depth.py:129:    """21,321 — the depth the 2026-08-13 runaway reached with nothing reporting it."""

$ git log -1 --format="%h %s" 19f2c94
19f2c94 fix(similarity): 백필 폭주 수습 — 큐 21,321건 적체의 코드 원인 제거 (P1~P4) (#368)

# M-1 — legacy on-demand 경로
$ git show ed4b06c:app/api/predictions.py | sed -n "20,26p"
@router.post("/price", response_model=PricePredictionResponse)
def predict_project_price(
    request: PricePredictionRequest,
    db: Session = Depends(get_db),
    workflow: PredictionWorkflowService = Depends(get_prediction_workflow),
):
    return workflow.predict_project_price(db, request)

$ git show ed4b06c:app/services/prediction_workflow.py | grep -n "build_bid_target_menu"
18:from app.ai.bid_target import build_bid_target_menu
109:            menu = build_bid_target_menu(
```

- exit: 0. 위 6개 명령 전부 **라운드 3 HEAD에서 실행한 그대로**이며 접거나 압축한 곳이
  없다. `task_queue_depth.py:6`의 문장이 grep 한 줄로 잘려 나오므로 문맥을 덧붙인다 —
  전문은 "On 2026-08-13 the **similarity projection backfill** pinned the one inference
  worker for four hours and ``bid_vector_ml_inference`` accumulated 21,321 messages."
  (`git show ed4b06c:app/services/task_queue_depth.py | sed -n '1,12p'`로 확인 가능).
- **H-1**: `list_projects`가 STR-16의 검색 절반을 이미 구현한다. 부재 주장 2곳을 실제
  형태 관찰로 교체하고, 조사가 놓친 이유(파이프라인 축으로 훑었고 이 엔드포인트가 어느
  stage에도 속하지 않는다)를 적었다. `V2 필수` 분류와 STR 축 배정은 유지했다.
- **H-1 파생 — 닫혔던 조사 표면을 열었다.** 검색 API의 금액 필터가 `:113`·`:116`에서
  `Project.budget_estimate`(추정가격)와 비교한다. 이번에 `legacy-defect`로 판정한
  `filters.py`와 **같은 결함**이다. §13 0B 인계 행을 **세 경로**로 다시 썼다.
  STR-16 acceptance에도 basis 고정 항목을 넣었다.
- **H-2**: 10곳 전부 같은 2026-08-13 사고를 가리키며, 원인은 **유사공고 임베딩 백필**이다.
  "그 뿌리가 사라진다"는 완화를 철회했다(라운드 3에서 문장 후반부까지 철회 범위 확대 —
  R2-5 참조). `OPEN-OPS-03`·`OPEN-OPS-04`의 근거 기반은 완화 이전 상태 그대로다.
- **M-1**: legacy에 on-demand 경로가 이미 있다. 바뀌는 것은 **배치 선산출의 제거**이므로
  "의도적 재설계" 프레이밍을 그 범위로 좁혔다.
- **경위**: H-2와 M-1은 팀 리드 분석 단계의 사실 오류가 `decisions-log.md`를 거쳐 0A2에서
  **정본 산출물로 승격**된 것이다. `decisions.md`에 경위를 절로 남기고 원본 문장은
  취소선으로 보존했다(감사 추적).

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

### R2-4. 불변 재확인 — **라운드 3 HEAD에서 재실행**

> **라운드 3 정정 (verifier M-2r)**: 라운드 2 기록의 절 번호 grep 출력이 `63 / 98 / 111`
> 이었는데 **range 내 어느 커밋에서도 그 값이 나오지 않는다.** 실행하지 않고 기재한
> 것이다. 아래는 라운드 3 HEAD에서 실제로 실행한 출력이다.

```
$ grep -n "^### 0\.[567] " docs/discovery/capability-map.md
63:### 0.5 OPEN과 acceptance scenario의 관계
107:### 0.6 축과 ID 접두사
120:### 0.7 상호작용 모델 — **pull** (운영자 결정 2026-08-26)

$ python3 inv2.py
분류: {'V2 필수': 62, '근거 부족': 10, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 39
본문 참조 - 활성 등록: 32 건 (해소·결번 포함)

$ grep -c "^| OPEN-" docs/discovery/capability-map.md
39

$ grep -n "조건부 — .OPEN-\|잠정 — .OPEN-" docs/discovery/capability-map.md
72:- **조건부 — `OPEN-*` 결정에 따라 확정** — 결정 전 **임시 시나리오**임을 명시하고,
1045:  - **조건부 — `OPEN-ML-03` 결정에 따라 확정.** 결정 전 임시 시나리오: 가격 적합도 값이
1341:  - **잠정 — `OPEN-DEC-03` 재결정 대상.** 아래 항목은 확정이 아니라 **잠정**이므로 위
1731:  - **조건부 — `OPEN-NOTI-08` 결정에 따라 확정.** 판정이 통지 이후에 확정되는 전이에서
1738:  - **조건부 — `OPEN-NOTI-01` 결정에 따라 확정.** 미전달을 통지 완료로 스탬프하는 legacy
2399:  - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** `Unclassified`를 재시도

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3

$ wc -l docs/discovery/capability-map.md
    2956 docs/discovery/capability-map.md
```

- exit: 0
- **capability 95 · 분류 62/17/6/10 불변** — 라운드 2·3 모두 분류를 바꾸지 않았다.
- **활성 OPEN 39.** `^| OPEN-` 행 수와 `inv2.py`의 §12 등록 수가 **양쪽 39로 일치**한다.
- 절 번호 순서 정상(0.5 → 0.6 → 0.7). secret 매치 3건 불변.
- 묶음: 조건부 3 + **잠정 1**. `:72`는 §0.5의 규약 정의문이지 묶음이 아니다.
- `capability-map.md` 2,951 → **2,956줄**(라운드 3 +5).

### R2-5. 라운드 3 — H-2r · L-4r 정정

```
$ sed -n "263,269p" reports/evidence/m0/0a2/decisions.md   # H-2r 수정 전
- ~~**큐 폭주 문제의 뿌리가 사라진다.** …배치 산출 작업이 쌓인 것이다.~~
  → **철회. 원본의 사실 오류다**…
  `OPEN-OPS-03`(큐 깊이 SLO)의 모수가 근본적으로 달라진다.      ← 철회 표시 **밖**

$ cd bid-vector && git grep -rn "R-07" ed4b06c
(출력 없음 — 매치 0)

$ git log -1 --format="%h %s" 0755695
0755695 fix(basis): budget_estimate capture 축 basis 정합 3종 세트 (#354)

$ git log -1 --format="%h %s" 4645ce4
4645ce4 fix(predictor): 투찰가를 사업금액(기초금액) 기준으로 산정 (#162)

$ git log --oneline --all --grep="BaseAmount" | head -3
0755695 fix(basis): budget_estimate capture 축 basis 정합 3종 세트 (#354)
2db542c fix(allocation): 투찰 결정 capture 분모를 기초금액으로 정렬
c4ec93b refactor(domain): money 타입(BaseAmount)을 bid_base 경계 시그니처에 강제 — mypy strict 아일랜드 승격 (#262)

$ git show 0755695 | grep -c "resolve_notice_bid_base"
12
```

- exit: 0
- **H-2r**: 라운드 2가 앞문장만 취소선에 넣고 **뒷문장을 철회 표시 밖에 남겼다.** 그 결과
  정정문이 오히려 완화의 결론을 재확인하는 형태가 됐고, 같은 파일의 정정 절
  (`OPEN-OPS-03`·`OPEN-OPS-04`의 근거 기반은 완화 이전 그대로)과 `capability-map.md`
  §0.7이 **정반대를 적고 있었다.** 철회 범위를 문장 전체로 넓혔다.
- **L-4r**: `R-07`은 legacy 저장소에 **매치 0**이며 scout 노트 `01_scout_strategy.md`의
  내부 번호다. `#162`와 `0755695`는 **같은 계열의 서로 다른 두 건**이다 —
  `0755695`=**#354**(capture 축, `resolve_notice_bid_base` 12회),
  `4645ce4`=**#162**(투찰가를 기초금액 기준으로, `bid_base.py` 신설),
  `c4ec93b`=**#262**(`BaseAmount` 뉴타입 강제). §13과 §12.2를 검증 가능한 형태로 고쳤다.
  **"같은 계열"이라는 판단 자체는 성립하므로 유지했다** — 입증된 오류는 "R-07이 저장소
  식별자"와 "#162 = 0755695" 두 전제뿐이며, 없는 주장을 만들지 않았다.

---

## 2026-08-26 — 수정 라운드 4 검증 (verifier M-3r·M-4r·L-5r·L-6r·L-7r)

라운드 3 재검증 판정 `not-ready`. **지시받은 7건은 전부 실질 처리됐고 초점이었던 M-2r는
통과**였다(`$` 명령 17건 전수 재현에서 어긋난 것 0건, 계열 A 신규 0건, 불변 8종 재현).
남은 5건은 **전부 라운드 3이 새로 쓴 문장**에서 나왔다.

### R4-1. 불변 재확인 — **라운드 4 HEAD에서 실행**

```
$ python3 inv2.py
분류: {'V2 필수': 62, '근거 부족': 10, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 39
본문 참조 - 활성 등록: 32 건 (해소·결번 포함)

$ grep -c "^| OPEN-" docs/discovery/capability-map.md
39

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3

$ wc -l docs/discovery/capability-map.md
    2958 docs/discovery/capability-map.md
```

- exit: 0. **capability 95 · 62/17/6/10 · 활성 OPEN 39 · 형식 위반 0 · 중복 id 0 ·
  `V2 필수` 결측 0 · secret 3 — 전부 불변.** 라운드 4는 집계를 바꾸지 않았다.
- `capability-map.md` 2,956 → **2,958줄**(L-6r +2). L-7r는 한 셀 안 교체라 줄 수 불변.
- **이 블록의 범위**: 위 4개 명령은 라운드 4 HEAD 실행분이다. 이 파일의 **라운드 1 절
  5개는 재실행하지 않았고** 각 절 제목에 그 사실과 기준 시점을 적었다(M-4r).

### R4-2. L-7r — `0755695`가 실제로 건드린 범위

```
$ cd bid-vector && git show --stat --format='' 0755695 | grep -E "monitoring|filters"
 app/services/opportunity_monitoring/base.py        |   5 +-
 app/services/opportunity_monitoring/candidates.py  |  17 +-

$ git show --stat --format='' 0755695 | grep -c "opportunity_monitoring/filters.py"
0

$ git show ed4b06c:app/services/opportunity_monitoring/filters.py | sed -n '62,67p'
        project_budget = float(project.budget_estimate or 0.0)
        min_budget = float(strategy.min_budget_estimate or 0.0)
        max_budget = float(strategy.max_budget_estimate or 0.0)
        if min_budget > 0 and project_budget < min_budget:
            return StrategyFilterResult(matched=False, reasons=[])
        if max_budget > 0 and project_budget > max_budget:
```

- exit: 0
- **`0755695`는 감시 모듈에 손을 댔다**(`base.py` · `candidates.py`). 건드리지 않은 것은
  **`filters.py`**이며 그 안의 예산 basis 비교 지점이 위 출력이다.
- 따라서 "감시·검색 경로는 세 commit에서 빠졌다"는 **과했다.** 빠진 것은 정확히
  **전략 예산 필터(`filters.py:62-70`)와 검색 경로(`projects.py:112-116`) 두 지점**이다.

> **범위 정정 (2026-08-27, 0B 선행 조사 C-3)** — 위 실행은 `sed -n '62,67p'`로 **블록을
> 6줄만 떴다.** 비교 블록은 실제로 **`:62-70`**이다. 위 출력은 그 시점 실행 그대로
> 두고(형태 3), 재실측을 아래 「C-3 재실측」에 적었다. **어느 지점인가라는 실질 주장은
> 바뀌지 않는다** — 범위 끝이 3줄 늘었다.
  §13 인계 행의 `근거` 문장을 그 범위로 좁혔다. **실질 주장(두 지점이 미수정)은 참이며
  바꾸지 않았다.**

### R4-3. 서술 정정 4건 — 새 주장 없음

| # | 무엇을 좁혔나 |
| --- | --- |
| **M-3r** | §10.1의 "**세 번 다** 결론 자체는 옳았다"가 **2번 형태(H-2·M-1·L-4r)에 대해 거짓**이었다. 그 셋은 결론이 반증돼 **철회**됐고 "재실행 후 교체"가 아니었다. 표를 `결론 자체 / 근거 / 처리` 3열로 나눠 **1·3번(결론 옳음 → 교체)과 2번(결론 틀림 → 철회)**을 분리했다. `scope.md`의 같은 문장도 고쳤다 |
| **M-4r** | `scope.md`의 "**모든 명령을** HEAD에서 실제로 실행하고"를 **"라운드 2·3 절의 명령"**으로 좁혔다. 라운드 1 절 12건은 재실행하지 않았고 그 시점 기준이므로 HEAD에서 재현되지 않는다. `commands.md` 라운드 1 절 **5개 제목에 기준 시점을 선언**했다 |
| **L-5r** | 라운드 1 절의 "66 → 37 … **41%**" 자기모순. 이 절은 **라운드 1 시점 기록**이므로 `44%`로 되돌렸다(66→37 기준). 현재 값 `66 → 43 = 35%`는 `checklist.md` §9가 적는다 |
| **L-6r** | "잔여 39건은 G2·G4·G5에 몰려 있으며 … 나머지 2건"이 계상되지 않았다(G2+G4+G5=20, 나머지 19). **분포 전체(G3 13 · G4 7 · G5 7 · G2 6 · G1 3 · G6 3)를 적고 G3가 단일 최대 그룹임을 밝혔다** |

- **이번 라운드는 새 주장을 만들지 않았다.** 전부 기존 서술의 범위를 좁히거나 수치를
  맞춘 것이며, 좁힌 자리마다 정정 경위를 남겼다.
- **M-4r가 §10.1 3번의 반대 형태다** — M-2r가 "출력을 손으로 썼다"면 이것은
  **"재실행 범위를 손으로 넓혔다"**이다. 같은 표에 이 사실을 기록했다.

---

## 2026-08-26 — 수정 라운드 5 검증 (Codex 4차 `request_changes`)

Codex 4차 판정 `request_changes`(blocker 0 / high 4 / medium 2). verdict 원문은
`codex-review-20260826T102642Z.json`. **6건 중 4건이 "결정의 파급이 문서 곳곳에 덜 반영된"
유형**이었다.

### R5-0. **`inv2.py` 본문 — Codex residual risk 대응**

Codex가 residual risk로 적었다:

> clean worktree에서 evidence가 기록한 `python3 inv2.py`는 **파일 부재로 exit 2**였다.
> 동일 불변식은 독립 parser로 재현했지만 evidence 명령 자체는 재실행할 수 없다.

**정확한 지적이다.** `inv2.py`는 커밋되지 않은 스크래치 스크립트였고, 이 파일이 그것을
이름으로만 불러 **리뷰어가 재실행할 수 없었다.** `checklist.md` §10.1이 세는 계열의
사촌이다 — 실행은 했지만 **남이 재실행할 수 없다.** 0A 라운드 5가 F1·F2'·X 스크립트에
한 것과 같은 방식으로 본문을 인라인한다.

```python
import re, collections, sys
p=sys.argv[1] if len(sys.argv)>1 else "docs/discovery/capability-map.md"
lines=open(p,encoding="utf-8").read().split("\n")
starts=[i for i,l in enumerate(lines) if l.startswith("### ")]
CLS=re.compile(r'^\s*- \*\*분류\*\*:\s*\*?\*?`([^`]+)`')
ACC=re.compile(r'^\s*-?\s*\*\*(Acceptance scenario|채택 시 요구되는 관찰 가능 동작)')
VAL=re.compile(r'^\s*- \*\*사용자 가치')
VALID={"V2 필수","후속","폐기","근거 부족"}
cnt=collections.Counter(); bad=[]; miss=[]; ids=[]
for n,st in enumerate(starts):
    e=starts[n+1] if n+1<len(starts) else len(lines)
    body=lines[st:e]; t=lines[st][4:].strip()
    m=[CLS.match(l) for l in body]; m=[x for x in m if x]
    if not m: continue
    cid=t.split(" ")[0]; ids.append(cid); v=m[0].group(1)
    if v not in VALID: bad.append((cid,v))
    cnt[v]+=1
    if v=="V2 필수" and (not any(VAL.match(l) for l in body) or not any(ACC.match(l) for l in body)):
        miss.append(cid)
print("분류:",dict(cnt),"=",sum(cnt.values()))
print("형식 위반:",bad or "none")
print("V2 필수 사용자 가치/acceptance 결측:",miss or "none")
dup=[k for k,v in collections.Counter(ids).items() if v>1]
print("중복 id:",dup or "none")
txt="\n".join(lines)
reg=set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|',txt,re.M))
ref=set(re.findall(r'OPEN-[A-Z]+-\d+',txt))
print("§12 활성 OPEN:",len(reg))
print("본문 참조 - 활성 등록:",len(sorted(ref-reg)),"건 (해소·결번 포함)")
```

- 위 스크립트를 임의 경로에 저장하고 `python3 <경로> [문서경로]`로 실행하면 이 파일의
  모든 `$ python3 inv2.py` 블록을 재현할 수 있다. 인자를 생략하면
  `docs/discovery/capability-map.md`를 읽는다.
- **주의**: 라운드 1 절의 출력은 그 시점 문서 기준이므로 현재 HEAD에서 재실행하면 다른
  값이 나온다(각 절 제목의 기준 시점 선언 참조). 재현 가능한 것은 **스크립트이지 값이
  아니다** — 값은 리비전에 종속된다.

### R5-1. 불변 재확인 — **라운드 6 HEAD에서 실행**

```
$ python3 inv2.py
분류: {'V2 필수': 62, '근거 부족': 10, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 43
본문 참조 - 활성 등록: 31 건 (해소·결번 포함)

$ grep -c "^| OPEN-" docs/discovery/capability-map.md
43

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3

$ wc -l docs/discovery/capability-map.md
    3078 docs/discovery/capability-map.md

$ grep -n "조건부 — .OPEN-|잠정 — .OPEN-" docs/discovery/capability-map.md
72:- **조건부 — `OPEN-*` 결정에 따라 확정** …            ← §0.5 규약 정의(묶음 아님)
847:  - **조건부 — `OPEN-QUAL-11` 결정에 따라 확정.** …
1013:  - **조건부 — `OPEN-QUAL-10` 결정에 따라 확정.** …
1018:  - **조건부 — `OPEN-QUAL-09` 결정에 따라 확정.** …
1108:  - **조건부 — `OPEN-ML-03` 결정에 따라 확정.** …
1404:  - **잠정 — `OPEN-DEC-03` 재결정 대상.** …
1794:  - **조건부 — `OPEN-NOTI-08` 결정에 따라 확정.** …
1801:  - **조건부 — `OPEN-NOTI-01` 결정에 따라 확정.** …
2483:  - **조건부 — `OPEN-OPS-01`의 정책 질문 결정에 따라 확정.** …
```

- exit: 0. **이 블록의 5개 명령은 라운드 6 HEAD 실행분이다** — 라운드 5 기록의 `wc -l`과
  grep 줄 번호가 라운드 6 편집으로 밀려(verifier F-5) 재실행해 교체했다. 라운드 1 절
  5개는 재실행하지 않았고 각 제목에 기준 시점을 선언했다(라운드 4 M-4r).
- **capability 95 · 분류 62/17/6/10 불변** — 라운드 5는 capability를 추가·재분류하지 않았다.
- **활성 OPEN 39 → 43.** 신설 3(`OPEN-QUAL-10` · `OPEN-QUAL-11` · `OPEN-OPS-10`) +
  복원 1(`OPEN-OPS-07`). `^| OPEN-` 행 수와 `inv2.py` 등록 수가 **양쪽 43으로 일치**한다.
- 조건부 묶음 **7개 / capability 5개** + 잠정 1. §0.5 목록(5개)과 일치한다.
  grep 9행 중 `:72`는 §0.5 **규약 정의문**이고 `:1404`는 **잠정** 묶음이므로 조건부에서
  제외한다 — 라운드 2의 같은 확인도 정의문을 제외해 셌다(verifier F-6).
- secret 매치 3건 불변. `capability-map.md` 2,958 → 3,064(라운드 5) → **3,078줄**(라운드 6 전파 +14).

---

## 2026-08-27 — 수정 라운드 7 검증 (Codex 5차 `request_changes` + 전수 재판정)

Codex 5차 판정 `request_changes`(blocker 0 / high 3 / medium 2). verdict 원문은
`codex-review-20260826T113911Z.json`. **4차 finding 6건은 residual에 해소로 명시**됐다.

### R7-0. 전수 재판정 — 인벤토리와 기준

Codex high #1(`OPS-06`)과 #2(`OPEN-DEC-03`)가 **같은 부류**다: **활성 OPEN에 종속된 것을
확정으로 계상한다.** 인스턴스가 아니라 부류를 쳤다.

**1단계 인벤토리 — 규모**

```
$ python3 inventory.py    # 활성 OPEN × 종속 지점  (재판정 **착수 시점** — 복원·신설 전)
활성 OPEN: 43건
본문(§12 이전) 참조 없는 활성 OPEN (= anchorless): 5
  ['OPEN-COL-01','OPEN-COL-05','OPEN-NUM-03','OPEN-QUAL-05','OPEN-SET-08']
활성 OPEN을 인용하는 블록: 37 (capability 30 + 설계 입력 절 7)
  그중 조건부 보유: QUAL-03 · QUAL-11 · ML-03 · NOTI-04 · OPS-09 (5)

$ python3 - <<'PY'   # V2 필수 블록의 acceptance에서 큐/브로커/워커 어휘 검색
STR-16 · QUAL-05 · OPS-02 · OPS-03 · OPS-04 · OPS-06 · OPS-12 · OPS-13
PY
```

- **기계적 스윕과 읽기를 병행**했다. 스윕은 후보를 좁혔고 판정은 읽기로 했다.
- 어휘 스윕 8건 중 **OPS-06만 실제 위반**이다 — OPS-02·OPS-04의 broker 어휘는 라운드 5의
  **정정 블록 안**(전제를 제거했다는 서술)이고 acceptance 항목 자체는 구현 중립이다.
  OPS-03의 outbox·워커는 DB 기반에서도 성립하고, OPS-12의 "큐"는 스케줄 파라미터 이름이며
  STR-16·QUAL-05·OPS-13은 축이 다르다.

**2단계 재판정 — 하나의 기준**

> 종료 조건이 충족되지 않았으면 활성 registry에 있어야 한다. 그에 종속된 서술은 조건부여야
> 하고, 그 쟁점에 종속된 capability 분류·acceptance는 확정될 수 없다.

`잠정`·`실행 대기`·`이월` 라벨을 전수로 훑었다.

```
$ grep -n '잠정\|실행 대기\|이월\|종료 조건' docs/discovery/capability-map.md
```

| 항목 | 라벨 | 종료 조건 | 재판정 |
| --- | --- | --- | --- |
| `OPEN-OPS-07` | 실행 대기 | ADR 대안 절 기입 | **미충족 → 이미 라운드 5에 복원** |
| `OPEN-DEC-03` | 잠정 | 예규 확인 후 재결정 | **미충족 → 복원**(Codex #2) |
| `OPEN-DEC-07` | 실행 대기 | 마진 값 산출 | **미충족 → 복원**(전수 재판정의 추가 발견) |
| `OPEN-DEC-08` "후속 확인" | — | 없음 — 결정 (b)는 그 자체로 완결이고 (a)는 근거가 생기면 **좁히는 선택지** | 유지 |
| `OPEN-ML-01` "잔여: 0C 확인" · `OPEN-STR-07` "하류 주의" · `OPEN-QUAL-06` "사유 enum" | — | 없음 — 결정 완결, 하류 작업 지시 | 유지 |
| `OPS-06` 이월(§13) | 이월 | DB 큐 설계 | **분류·acceptance가 확정돼 있었다 → 조건부화**(Codex #1) |

- **추가 발견은 `OPEN-DEC-07` 1건**이다. `OPEN-OPS-07`과 **같은 라벨**인데 복원되지 않아
  기준이 일관되지 않았다.
- **유지 판정 4건의 근거**: 그 항목들의 "잔여"는 **종료 조건이 아니라 하류 작업 지시**다 —
  결정 자체는 완결이고 미충족 조건이 없다.

### R7-1. 불변 재확인 — **라운드 7 HEAD에서 실행**

```
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 12, '폐기': 6, '후속': 16} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 46
본문 참조 - 활성 등록: 29 건 (해소·결번 포함)

$ grep -c "^| OPEN-" docs/discovery/capability-map.md
46

$ grep -n "조건부 — .OPEN-|잠정 — .OPEN-" docs/discovery/capability-map.md
72:   ← §0.5 규약 정의(묶음 아님)
847:  QUAL-03(`OPEN-QUAL-11`)
1013: QUAL-11(`OPEN-QUAL-10`)
1018: QUAL-11(`OPEN-QUAL-09`)
1108: ML-03(`OPEN-ML-03`)
1404: DEC-02(`OPEN-DEC-03`)   ← 잠정 묶음
1803: NOTI-04(`OPEN-NOTI-08`)
1810: NOTI-04(`OPEN-NOTI-01`)
2411: OPS-06(`OPEN-OPS-10`)   ← 라운드 7 신규
2504: OPS-09(`OPEN-OPS-01`)

$ wc -l docs/discovery/capability-map.md
    3133 docs/discovery/capability-map.md

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3

$ git diff --check 6af7019 -- docs reports | wc -l
       0
```

- exit: 0. **이 블록의 6개 명령은 라운드 7 HEAD 실행분이다.** 라운드 1 절 5개는
  재실행하지 않았고 각 제목에 기준 시점을 선언했다(라운드 4 M-4r).
- **capability 95 불변**, 분류 **62/17/6/10 → 61/16/6/12**(OPS-06·NOTI-02 재분류).
- **활성 OPEN 43 → 46** — 복원 2(`OPEN-DEC-03` · `OPEN-DEC-07`) + 신설 1(`OPEN-NOTI-09`).
  `^| OPEN-` 행 수와 `inv2.py` 등록 수가 **양쪽 46으로 일치**한다.
- 조건부 묶음 **8개 / capability 6개**(§0.5 목록과 일치) + 잠정 1. `:72`는 규약 정의문이다.
- **`git diff --check` 0** — Codex residual이 보고한 Markdown 제목 6곳의 trailing
  whitespace를 제거했다(제목 뒤 두 칸 → 빈 줄로 문단 분리).

---

## 2026-08-27 — 라운드 7 완결: 활성 OPEN 46건 전수 인용 스윕 (verifier F7-1~F7-9)

라운드 7 재검증에서 **복원한 항목의 인용 지점을 안 고친 것**이 차단 사유였다
(F7-1 · F7-2). **라운드 6 F-2와 글자 그대로 같은 형태이며 세 번째 재발**이다 —
`checklist.md` §10.1 **형태 5**를 자기 복원 항목에 적용하지 않았다.

### R7C-1. 절차 — 형태 5를 문장이 아니라 실행으로

문장으로 둔 규칙이 세 번을 못 막았으므로 **스크립트로 만들었다.** 아래를 임의 경로에
저장하고 저장소 루트에서 `python3 <경로>`로 실행하면 재현된다.

```python
"""활성 OPEN 46건 전수 인용 스윕.
각 id의 모든 매치를 훑어 '해소' 계열 어휘와 같은 줄에 있는 것을 적출한다.
라운드별 이력 절의 그 시점 값은 제외 대상이 아니라 '판정 대상'이며,
살아 있는 현재 상태 주장인지는 사람이 읽고 정한다."""
import re, subprocess, sys, glob
FILES=["docs/discovery/capability-map.md"]+sorted(glob.glob("reports/evidence/m0/0a2/*.md"))
cm=open(FILES[0],encoding="utf-8").read()
active=sorted(set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|', cm, re.M)))
RESOLVED=re.compile(r'해소|해소됐|결번|~~')
total=0; flagged=[]
for oid in active:
    for f in FILES:
        for i,l in enumerate(open(f,encoding="utf-8").read().split("\n"),1):
            if oid not in l: continue
            total+=1
            if RESOLVED.search(l):
                flagged.append((oid,f,i,l.strip()[:150]))
print(f"활성 OPEN: {len(active)}건 | 전체 매치: {total}건")
print(f"'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: {len(flagged)}건 (판정 대상)")
for oid,f,i,l in flagged:
    print(f"\n[{oid}] {f}:{i}\n   {l}")
```

**판정 기준**: 매치가 그 항목의 **현재 상태(활성/해소)와 정합하는가**. 라운드별 이력 절의
그 시점 값은 **정확한 기록이므로 제외 대상이 아니라 판정 대상**이고, 판정 축은
**"살아 있는 현재 상태 주장인가"**다.

### R7C-2. 실행 결과

```
$ git rev-parse HEAD
cb81a17...                      # 아래 수치의 재현 지점
$ python3 sweep46.py
활성 OPEN: 46건 | 전체 매치: 379건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
```

- **52건 전수를 읽고 판정했다. 불일치 0건.**
- **수치는 커밋을 명시해 고정한다.** 스윕이 `reports/evidence/m0/0a2/*.md`도 훑으므로
  **이 기록과 `scope.md` 이력 자체가 매치에 포함되고, 그것들을 쓸 때마다 수가 늘어난다.**
  위 수치의 재현 지점은 **`cb81a17`**이며(`git stash` 후 해당 커밋에서 재실행),
  **그 뒤 커밋에서도 379/52로 같다** — 실제로 `cb81a17`과 `304de90` 양쪽에서 재실행해
  같은 값을 확인했다(뒤따른 커밋들이 새 OPEN id 인용을 추가하지 않았다). 중간 측정은
  363/47 → 378/51 → **379/52**였고, 늘어난 16/5건은 전부 이 절 · §10.1 절차 본문 ·
  `scope.md` 완결 이력이다. **활성 OPEN 46건과 불일치 0건은 그 사이 불변이다.**
- 내역: `DEC-03`·`DEC-07` 13건(전부 활성 복원 서술 또는 라운드 이력) · `OPS-03` 8건
  (전부 "해소하지 않았다") · `OPS-07` 11건(복원·활성·이력) · `STR-12` 5건(§9.1이
  "미해소"로 적음) · `QUAL-09` 3건(신설 사유) · `NUM-03` 1건(`SET-07`이 여기로 통합된
  것이지 `NUM-03`이 해소된 것이 아니다) · 나머지 6건은 처리 기록.

### R7C-3. 절차 유효성 — 수정 **전** 상태에서 F7-1·F7-2가 잡히는가

```
$ git show HEAD:docs/discovery/capability-map.md > /tmp/pre.md   # 수정 전
$ python3 - <<'EOS'
import re
pre=open("/tmp/pre.md",encoding="utf-8").read().split("\n")
# 복원 2건은 그 시점에도 활성 registry에 있었다 — 같은 판정 규칙으로 인용 지점을 훑는다.
for oid in ("OPEN-DEC-03","OPEN-DEC-07"):
    for i,l in enumerate(pre,1):
        if oid in l and re.search(r'해소|결번|~~', l) and '복원' not in l:
            print(f"[{oid}] :{i}  {l.strip()[:70]} …")
EOS
[OPEN-DEC-03] :3017  | ~~OPEN-DEC-03~~ | **운영자 결정 2026-08-26로 해소** — …
[OPEN-DEC-07] :1410  신뢰 비율 1.15의 마진 0.05는 V2 코퍼스에서 **재유도**한다(`OPEN-DEC-07` 해소,
[OPEN-DEC-07] :3016  | ~~OPEN-DEC-07~~ | **운영자 결정 2026-08-26로 해소** — …
```

- exit: 0. **F7-1(§12.1 결번 표 2행)과 F7-2(DEC-02 본문 1행)가 정확히 적출된다.**
  절차가 이 부류를 잡는다는 것이 실증됐다.
- **F7-1·F7-2 외 추가 불일치는 0건**이다.

### R7C-4. 불변 재확인 — 라운드 7 완결 HEAD

```
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 12, '폐기': 6, '후속': 16} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 46

$ grep -c "^| OPEN-" docs/discovery/capability-map.md
46

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3

$ git diff --check 6af7019 HEAD | wc -l
       0

$ wc -l docs/discovery/capability-map.md
    3144 docs/discovery/capability-map.md
```

- exit: 0. **집계는 하나도 바뀌지 않았다** — 이 완결은 상태 정합과 절차 기록만 했다.
- 조건부 묶음 **9개 / capability 7개**(F7-4로 NOTI-02 추가) + 잠정 1. §0.5 목록과 일치한다.
- `capability-map.md` 3,133 → **3,144줄**.

---

## 2026-08-27 — 운영자 결정 반영: `OPEN-NOTI-09` 확정

**결정**: NOTI-02(알림 피로도 게이트)를 **`후속`으로 확정**(`축소` 아님). 운영자,
2026-08-27. 원본 기록은 `decisions.md`의 「2026-08-27」 절.

### R8-1. 활성 OPEN 전수 인용 스윕 — 대상이 46에서 **45**로 바뀐 상태

`checklist.md` §10.1 형태 5의 절차대로, **OPEN을 해소한 라운드이므로 그 라운드 안에서**
돌렸다. 스크립트는 R7C-1과 동일하다(활성 목록을 `capability-map.md`에서 매번 다시 읽으므로
목록 변경에 자동으로 따라간다).

```
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 365건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
```

- **52건 전수를 읽고 판정했다. 불일치 0건.**
- **재현 지점은 이 절을 담은 커밋**이다 — 스윕이 evidence 자신을 훑으므로 기록을 쓰면
  수가 늘어난다. 뒤따르는 `scope.md` 커밋은 **`head_sha` 한 줄만** 바꾸므로 이 수치는
  그 커밋에서도 같다.
- 판정 대상 52건의 분포: `OPS-07` 14 · `DEC-07` 10 · `DEC-03` 7 · `OPS-03` 8 ·
  `STR-12` 5 · `QUAL-09` 4 · `OPS-10` 1 · `OPS-01` 1 · `NUM-03` 1 · `NOTI-08` 1.
- 활성 45건 = 46건에서 `OPEN-NOTI-09` 1건이 빠진 것이다. 나머지 44건의 인용 지점은
  이 결정과 무관하므로 판정 결과가 라운드 7 완결과 같다.

### R8-2. 역방향 패스 — **해소한 id는 활성 목록에 없어 스윕이 잡지 못한다**

**이 결정 반영에서 발견한 절차의 구멍이다.** `sweep46.py`는 `capability-map.md` §12의
**활성 registry 행에서 id를 뽑는다.** `OPEN-NOTI-09`를 해소하면 그 id가 목록에서 빠지므로,
**"아직 `OPEN-NOTI-09` 종속"이라고 남은 낡은 인용을 스윕이 영원히 못 본다.** 활성 항목에서
찾는 방향(활성인데 "해소"로 적힌 것)과 **정반대 방향의 불일치**다.

그래서 해소한 id와 그 capability를 직접 훑었다.

```
$ grep -c 'OPEN-NOTI-09' <capability-map.md + evidence 5개 파일>
   수정 전 `304de90` 합계 16 → 반영 후 `3ed76e9` 합계 41
$ grep -c 'NOTI-02'      <같은 파일들>
   수정 전 `304de90` 합계 45 → 반영 후 `3ed76e9` 합계 64
```

**수정 전 HEAD의 16곳·45줄을 전수로 읽어 판정**했고, 그 판정에 따라 **현재 상태 주장만**
고쳤다(반영 후 수치는 **`3ed76e9` 기준 고정**이다 — 이 파일 자신이 스캔 대상이라 뒤
라운드가 쓰면 늘어난다). 늘어난 25곳·19줄은 **전부 이번 반영이 쓴 기록**이다 — `capability-map.md`
§0.5 보완 문단 · NOTI-02 블록 · §10 되돌림 표 · §12 머리 · §12.1 두 곳 · §12.2 행,
`checklist.md` §4 · §9 · §10.1 보완 · §14.3 · §14.5, 이 절, `decisions.md` 새 절,
`scope.md` 이 라운드 이력.

전수 판정 결과 **불일치 0건**. 분류는 이렇다.

| 부류 | 판정 |
| --- | --- |
| 현재 상태 주장(`capability-map.md` §0.5 · NOTI-02 블록 · §10 · §12.1 · §12.2) | **해소·`후속` 확정과 정합**하도록 이번에 전부 고쳤다 |
| 라운드별 이력 서술(라운드 1 재분류 표 · 라운드 7 재분류 표 · Codex 5차 처리 표 · `scope.md` 라운드 7 이력) | **그 시점 값으로 정합.** 라운드 7 표는 바로 뒤에 2026-08-27 되돌림 블록이 붙어 현재 상태를 오도하지 않는다 |
| 실행 기록(`commands.md` R7 절의 `43 → 46`) | **그 시점 실행 출력**이므로 갱신 대상이 아니다 |

이 역방향 패스를 **§10.1 형태 5 절차의 보완**으로 규칙에 넣었다 — 새 형태가 아니라
같은 절차의 누락된 반쪽이다.

### R8-3. 불변 재실행

```
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3
$ git diff --check 6af7019...HEAD | wc -l
       0
$ wc -l docs/discovery/capability-map.md
    3171 docs/discovery/capability-map.md
```

- capability **95 불변**. 분류 **61/16/6/12 → 61/17/6/11**. 활성 OPEN **46 → 45**.
- 조건부 묶음 **9 → 8개 / capability 7 → 6개** + 잠정 1(불변).
- M0 차단 8건 중 7건 해소 · M1 차단 17건 중 15건 해소 — **불변**.

---

## 2026-08-27 — 라운드 7 완결 2차: **수치 축 스윕** (verifier V-1~V-10)

**진단이 핵심이다.** §10.1 형태 5의 원래 정의는 "정정을 인용 지점에 전파하지 않기"이고
라운드 6 F-1이 바로 "활성 OPEN 43 **수치** 미전파"였다. 라운드 7 완결이 그 규칙을
절차로 만들면서 **OPEN id grep으로 좁혔고**, 그 결과 **id 축 불일치 0인 상태에서 수치 축
5건**(V-1~V-5)이 남았다. 좁힌 것을 되돌린다 — 새 형태가 아니라 원래 정의의 복원이다.

### R9-1. 스크립트 본문 (형태 4 — 인라인)

> **라운드 7 완결 3차 갱신**: verifier F2-1로 **§12.2 결정 완료 행 수 · §12.1 결번 표
> 행 수**를 대조 목록에 추가했다(`DEC_ROWS` · `VOID_ROWS`와 대응 `CHK` 항목 2개).
> 아래 본문은 **갱신 후**이며 R10이 그 실행 결과다. R9-2의 173/103은 갱신 **전** 축의
> 값으로, 그 시점 기록으로 남긴다.

**id 축 스윕(R7C-1)과 설계가 다르다.** id 축은 문자열을 grep해 사람이 판정하지만, 수치
축은 **정본을 산출물에서 직접 계산한 뒤 인용 지점을 패턴으로 추출해 자동 대조**한다.
그래서 수치 축은 사람 판정 없이도 불일치를 지목한다 — 판정이 필요한 것은 "그 불일치가
살아 있는 현재 값 주장인가, 라운드별 이력의 그 시점 값인가" 하나뿐이다.

```python
# 수치 축 스윕 — §10.1 형태 5의 수치 arm.
# 1) 정본을 산출물에서 직접 계산하고 2) 문서·evidence의 수치 인용을 패턴으로 추출해
# 정본과 자동 대조한다. MISMATCH는 "이력의 그 시점 값"일 수 있으므로 사람이 판정한다.
import re, collections, glob, sys, os
CM="docs/discovery/capability-map.md"
FILES=[CM]+sorted(glob.glob("reports/evidence/m0/0a2/*.md"))
lines=open(CM,encoding="utf-8").read().split("\n"); txt="\n".join(lines)

# ---------- 정본 산출 ----------
starts=[i for i,l in enumerate(lines) if l.startswith("### ")]
CLS=re.compile(r'^\s*- \*\*분류\*\*:\s*\*?\*?`([^`]+)`')
cnt=collections.Counter(); axis=collections.defaultdict(collections.Counter); ids=[]
for n,st in enumerate(starts):
    e=starts[n+1] if n+1<len(starts) else len(lines)
    body=lines[st:e]; t=lines[st][4:].strip()
    m=[CLS.match(l) for l in body]; m=[x for x in m if x]
    if not m: continue
    cid=t.split(" ")[0]; ids.append(cid); v=m[0].group(1)
    cnt[v]+=1; axis[cid.split("-")[0]][v]+=1
ORDER=["V2 필수","후속","폐기","근거 부족"]
CLS4=tuple(cnt[k] for k in ORDER); CAP=sum(cnt.values())
reg=sorted(set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|',txt,re.M)))
OPENTOT=len(reg)
# 그룹별: '### G<n>.' 헤딩 아래의 registry 행
grp=collections.Counter(); cur=None
for l in lines:
    g=re.match(r'^### (G\d)\.',l)
    if g: cur=g.group(1)
    elif re.match(r'^\|\s*OPEN-[A-Z]+-\d+\s*\|',l) and cur: grp[cur]+=1
COND=len(re.findall(r'^\s+- \*\*조건부 — `OPEN-',txt,re.M))
PROV=len(re.findall(r'^\s+- \*\*잠정 — `OPEN-',txt,re.M))
CONDCAP=len(set(re.findall(r'^### ([A-Z]+-\d+)',txt,re.M)) & set())  # 아래에서 별도 산출
condcaps=set()
for n,st in enumerate(starts):
    e=starts[n+1] if n+1<len(starts) else len(lines)
    if any(re.match(r'^\s+- \*\*조건부 — `OPEN-',l) for l in lines[st:e]):
        condcaps.add(lines[st][4:].strip().split(" ")[0])
CONDCAP=len(condcaps)
LINES=len(lines)-(1 if lines and lines[-1]=="" else 0)

# §12.2 결정 완료 행 수 · §12.1 결번 표 행 수 — 산출물이 스스로 선언하는 집계다.
def _blk(start_pat, end_pats):
    i=next(k for k,l in enumerate(lines) if re.match(start_pat,l))
    j=next(k for k in range(i+1,len(lines)) if any(re.match(pp,lines[k]) for pp in end_pats))
    return [l for l in lines[i:j]
            if l.startswith("| ") and not re.match(r'^\|\s*-{2,}',l) and not l.startswith("| ID |")]
DEC_ROWS=len(_blk(r'^### 12\.2 ', [r'^\*\*해소되지 않은', r'^### ', r'^## ']))
VOID_ROWS=len(_blk(r'^### 12\.1 ', [r'^\*\*0A2 라운드 2 신설']))

GT={
 "분류4종": CLS4, "capability 총수": CAP, "활성 OPEN 총수": OPENTOT,
 "G 분포": {k:grp[k] for k in sorted(grp)},
 "축별 분포": {a:tuple(axis[a][k] for k in ORDER) for a in sorted(axis)},
 "조건부 묶음": COND, "조건부 capability": CONDCAP, "잠정 묶음": PROV,
 "capability-map 줄 수": LINES,
 "§12.2 결정 완료 행 수": DEC_ROWS, "§12.1 결번 표 행 수": VOID_ROWS,
}
print("=== 정본 (산출물에서 직접 계산) ===")
for k,v in GT.items(): print(f"  {k}: {v}")
print("  차단 집계: 문서 선언값 — 기계 산출 불가(§12.2 라벨이 없다). 인용 지점 간 일치만 본다")
print()

# ---------- 인용 지점 추출·대조 ----------
# 각 항목: (이름, 줄 필터, 값 정규식, 추출 함수, 정본)
# 줄 필터 = 그 수치가 "이 문서의 집계"를 가리킬 때만 참이 되는 문맥 조건.
# 필터 없이 숫자만 긁으면 legacy 줄 수·건수 같은 무관한 수치가 섞인다.
def has(*ws): return lambda l: any(w in l for w in ws)
ALWAYS=lambda l: True
CHK=[
 ("분류4종", has("분류","집계","/ 6 /","/6/"),
   re.compile(r'(\d{1,3})\s*/\s*(\d{1,3})\s*/\s*(\d{1,3})\s*/\s*(\d{1,3})'),
   lambda m: tuple(int(x) for x in m.groups()), CLS4),
 ("분류4종(§10 표 계 행)", ALWAYS,
   re.compile(r'^\|\s*\*\*계\*\*\s*\|\s*\*\*(\d+)\*\*\s*\|\s*\*\*(\d+)\*\*\s*\|\s*\*\*(\d+)\*\*\s*\|\s*\*\*(\d+)\*\*'),
   lambda m: tuple(int(x) for x in m.groups()), CLS4),
 ("§10 축별 행", ALWAYS,
   re.compile(r'^\|\s*(COL|STR|QUAL|ML|DEC|NOTI|SET|OPS)\s[^|]*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|'),
   None, None),
 ("활성 OPEN 총수", has("활성","잔여"),
   re.compile(r'활성\s*`?OPEN`?\s*(?:결정\s*)?\*{0,2}(\d{1,3})\*{0,2}\s*건'),
   lambda m: int(m.group(1)), OPENTOT),
 ("활성 OPEN 총수(잔여 N건)", has("활성 OPEN","잔여 활성","OPEN**"),
   re.compile(r'잔여\s*\*{0,2}(\d{1,3})\s*건\*{0,2}'),
   lambda m: int(m.group(1)), OPENTOT),
 ("G3 분포", has("G4","분포"),
   re.compile(r'G3\s*\*{0,2}(\d{1,2})\*{0,2}'), lambda m: int(m.group(1)), grp["G3"]),
 ("capability 총수", has("capability","분류"),
   re.compile(r'capability\s*(?:수\s*)?\*{0,2}(\d{2,3})\*{0,2}\s*(?:불변|개다|개|다)'),
   lambda m: int(m.group(1)), CAP),
 ("조건부 capability 수", ALWAYS,
   re.compile(r'조건부[^\n]{0,30}?capability\s*(?:는)?\s*\*{0,2}(\d{1,2})\*{0,2}\s*개'),
   lambda m: int(m.group(1)), CONDCAP),
 ("조건부 묶음 수", ALWAYS,
   re.compile(r'조건부 묶음\s*\*{0,2}(\d{1,2})\*{0,2}\s*개'),
   lambda m: int(m.group(1)), COND),
 ("capability-map 줄 수", has("capability-map","산출물"),
   re.compile(r'\*{0,2}([\d,]{3,6})\s*줄\*{0,2}'),
   lambda m: int(m.group(1).replace(",","")), LINES),
 ("M0 차단", ALWAYS,
   re.compile(r'M0[^\n]{0,4}차단\s*\*{0,2}(\d+)\*{0,2}건 중\s*\*{0,2}(\d+)\*{0,2}'),
   lambda m: (int(m.group(1)),int(m.group(2))), (8,7)),
 ("M1 차단", ALWAYS,
   re.compile(r'M1[^\n]{0,4}차단(?:은)?\s*\*{0,2}(\d+)\*{0,2}건 중\s*\*{0,2}(\d+)\*{0,2}'),
   lambda m: (int(m.group(1)),int(m.group(2))), (17,15)),
]
AXORD=["COL","STR","QUAL","ML","DEC","NOTI","SET","OPS"]

CHK += [
 ("§12.2 행 수", has("§12.2","12.2","결정 완료"),
   re.compile(r'(?<![.\d])\*{0,2}(\d{1,3})\s*행\*{0,2}'), lambda m: int(m.group(1)), DEC_ROWS),
 ("§12.1 결번 행 수", has("§12.1","12.1","결번"),
   re.compile(r'(?<![.\d])\*{0,2}(\d{1,3})\s*행\*{0,2}'), lambda m: int(m.group(1)), VOID_ROWS),
]

# 파생 수치: 문서가 계산해 인용하는 값도 정본에서 다시 계산한다.
import subprocess
def gcount(pat):
    n=0
    for f in FILES:
        n+=sum(1 for l in open(f,encoding="utf-8") if pat in l)
    return n
N09=gcount("OPEN-NOTI-09"); N02=gcount("NOTI-02")
REDUCE=round((66-OPENTOT)/66*100)
GT["역방향 OPEN-NOTI-09 총수"]=N09
GT["역방향 NOTI-02 총수"]=N02
GT["활성 OPEN 감축률(66 기준)"]=f"{REDUCE}%"

CHK += [
 ("시계열 마지막 값", has("활성 OPEN","활성 `OPEN`","OPEN 수","잔여"),
   re.compile(r'(?:\d{1,3}\s*→\s*\*{0,2}){1,6}\*{0,2}(\d{1,3})\*{0,2}'),
   lambda m: int(m.group(1)), OPENTOT),
 ("감축률", has("줄여","줄였","감축"),
   re.compile(r'(\d{1,3})\s*%'), lambda m: f"{m.group(1)}%", f"{REDUCE}%"),
 ("역방향 패스 합계", has("합계"),
   re.compile(r'합계\s*(\d{1,3})'), None, None),
]
tot=0; bad=[]
for f in FILES:
    for i,l in enumerate(open(f,encoding="utf-8").read().split("\n"),1):
        for name,filt,rx,ex,exp in CHK:
            if not filt(l): continue
            for m in rx.finditer(l):
                tot+=1
                if name=="역방향 패스 합계":
                    v=int(m.group(1))
                    if v not in (N09,N02,16,45):   # 16·45는 수정 전 HEAD 실측(고정 이력)
                        bad.append((name,f,i,v,f"{N09}(N09) 또는 {N02}(N02)",l.strip()[:160]))
                    continue
                if name=="§10 축별 행":
                    a=m.group(1); got=tuple(int(x) for x in m.groups()[1:])
                    e=GT["축별 분포"].get(a)
                    if e and got!=e: bad.append((name,f,i,got,e,l.strip()[:150]))
                    continue
                got=ex(m)
                if got!=exp: bad.append((name,f,i,got,exp,l.strip()[:160]))
print(f"=== 대조 ===\n추출된 수치 인용: {tot}건 | 정본 불일치: {len(bad)}건 (이력 서술 포함 — 판정 대상)\n")
for n,f,i,g,e,l in bad:
    print(f"[{n}] {f}:{i}  추출={g} 정본={e}\n   {l}\n")
```

### R9-2. 실행 결과

```
$ python3 numsweep.py
=== 정본 (산출물에서 직접 계산) ===
  분류4종: (61, 17, 6, 11)
  capability 총수: 95
  활성 OPEN 총수: 45
  G 분포: {'G1': 3, 'G2': 7, 'G3': 15, 'G4': 8, 'G5': 7, 'G6': 5}
  축별 분포: {'COL': (8, 0, 1, 1), 'DEC': (9, 1, 0, 0), 'ML': (7, 2, 0, 1),
             'NOTI': (5, 5, 0, 1), 'OPS': (12, 1, 4, 3), 'QUAL': (7, 3, 1, 1),
             'SET': (3, 1, 0, 2), 'STR': (10, 4, 0, 2)}
  조건부 묶음: 8 / 조건부 capability: 6 / 잠정 묶음: 1
  capability-map 줄 수: 3171
  차단 집계: 문서 선언값 — 기계 산출 불가(§12.2 라벨이 없다). 인용 지점 간 일치만 본다

=== 대조 ===
추출된 수치 인용: 173건 | 정본 불일치: 103건 (이력 서술 포함 — 판정 대상)
```

- **재현 지점은 이 절을 담은 커밋**이다. 스윕이 evidence 자신을 훑으므로 기록을 쓰면
  추출 건수가 늘어난다. 뒤따르는 `scope.md` 커밋은 **`head_sha` 한 줄만** 바꾸므로
  그 커밋에서도 같은 값이 나온다(실측으로 확인했다).
- **정본은 산출물에서 직접 계산한다.** §10 축별 표를 읽어 대조하는 것이 아니라
  capability 블록의 `- **분류**:` 줄을 세서 표를 검증한다 — 그래서 V-1이 잡혔다.

### R9-3. 판정 — 살아 있는 현재 값 주장 vs 이력의 그 시점 값

103건을 전수로 읽었다. **살아 있는 현재 값 주장의 불일치는 아래가 전부이며 모두
고쳤다.** 나머지는 라운드별 이력 절의 그 시점 값(정확한 기록)과 무관한 수치의 오탐이다.

| # | 지점 | 추출 → 정본 | 처리 |
| --- | --- | --- | --- |
| **V-1** | `capability-map.md:2777` §10 NOTI 행 | `(5,4,0,2)` → `(5,5,0,1)` | 고침(`098bf03`) |
| **V-1** | `capability-map.md:2780` §10 계 행 | `(61,16,6,12)` → `(61,17,6,11)` | 고침(`098bf03`) |
| **V-2** | `checklist.md` A1 §12 집계 일치 행 | 46 / G3 16 → 45 / G3 15 | 고침 + 시계열에 `→ 45(2026-08-27)` |
| **V-3** | `checklist.md` §9 제한 4 | `66 → 46`, 30% → `66 → 45`, **32%** | 고침(감축률도 정본에서 재계산) |
| **V-5** | `commands.md` R8-2 `NOTI-02` 합계 | 63 → **64**, 파생 "18줄" → **19줄** | 고침 + 측정 커밋(`3ed76e9`) 명시 |
| **V-7** | `checklist.md:7` 산출물 줄 수 | 3,144 → **3,171** | 고침. §14.3 후속 줄에도 3,171 추가 |
| **V-9** | `checklist.md` §10.1 활성 OPEN 시계열 | `라운드 7 후 46`에서 끝남 | `2026-08-27 결정 후 45` 추가 |
| **추가 발견** | `checklist.md` §4 「§10 재계산」 | `62/17/6/10 = 95`가 **2026-08-27 블록 뒤에 놓여 현재 값처럼 읽혔다** | 「라운드 1 시점」 한정을 달고 현재 값 `61/17/6/11` 병기 |
| **추가 발견** | `checklist.md` A1 본문 인용 일치 행 | 조건부 시계열이 `→ 8(라운드 7)`에서 끝남 | `→ 9(라운드 7 완결) → 8(2026-08-27)` 추가 |

**이 표 자신이 수정 전 값을 인용하므로 스윕이 이 절의 행들도 지목한다** — 위 실행이
이 절을 담은 커밋에서 나왔으므로 그 매치들은 **이미 위 건수 안에 있다**(`R9-3` 표 안의
`30%` · `(62,17,6,10)` 등). 기록의 성질상 정상이며 산출물의 상태와 무관하다 —
다음 라운드가 다시 판정하지 않도록 여기 적는다.

**추가 발견 2건은 verifier 목록에 없던 것**이며 수치 축 스윕이 잡았다. 첫 번째가 특히
이 축의 값을 보여준다 — 문장은 참(라운드 1 시점 값)인데 **위치가 바뀌어** 현재 값 주장이
된 경우이고, id 축으로는 영원히 안 보인다.

**오탐 3건**(스윕의 한계로 기록한다 — 다음 라운드가 다시 판정하지 않도록):

| 지점 | 왜 오탐인가 |
| --- | --- |
| `capability-map.md:998` "잔여 2건" | QUAL-11 블록의 **미결 항목 2건**이지 활성 OPEN 총수가 아니다 |
| `capability-map.md:1260` "합계 2,166줄" | legacy `app/domain/` 줄 수다 |
| `commands.md:200` "(합계 6)" | 라운드 1 절 번호 grep 출력의 행 수다 |

### R9-4. id 축 스윕 재실행 (R7C-1 스크립트)

```
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 367건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
```

- **불일치 0건.** 이번 라운드는 OPEN을 신설·복원·해소하지 않았으므로 R8-1의 판정이
  그대로 유효하고, 재실행은 그 사실의 확인이다.

### R9-5. 불변 재실행

```
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
       3
$ git diff --check 6af7019...HEAD | wc -l
       0
$ wc -l docs/discovery/capability-map.md
    3171 docs/discovery/capability-map.md
```

- capability **95** · 분류 **61/17/6/11** · 활성 OPEN **45** — 전부 불변.
  V-1은 표를 실측에 맞춘 것이지 분류를 바꾼 것이 아니다.

---

## 2026-08-27 — 라운드 7 완결 3차: **수치 축 커버리지 확장** (verifier F2-1~F2-3)

**인스턴스가 아니라 축이 문제였다.** F2-1(`checklist.md` A1 「결정 근거」 행이 §12.2를
"29행"으로 적음)을 복원된 수치 축이 못 잡은 이유는 **§12.2/§12.1 행 수가 대조 목록에
없었기 때문**이다. 산출물이 자기 절에서 "30행 중 27건"으로 세 번 선언하는 집계인데
축의 커버리지 밖이었다.

### R10-1. 축 확장

`DEC_ROWS`(§12.2 결정 완료 행 수) · `VOID_ROWS`(§12.1 결번 표 행 수)를 정본 산출에
추가하고 대응 `CHK` 항목 2개를 넣었다. 본문은 R9-1(갱신 후).

```
$ python3 numsweep.py   # 정본 발췌
  §12.2 결정 완료 행 수: 30
  §12.1 결번 표 행 수: 35
```

- **35 = 0A에서 온 5행 + 2026-08-26 결정 29행 + 2026-08-27 `OPEN-NOTI-09` 1행.**
- §10.1 형태 5 본문에 두 집계를 넣고, 개별 항목을 계속 덧붙이는 대신 **커버리지 기준**을
  한 줄로 남겼다 — **"산출물이 스스로 선언하는 집계는 전부 대조 대상"**이며 위 열거는
  목록이 아니라 확인된 사례다.

### R10-2. 넓힌 축으로 재실행

```
$ python3 numsweep.py
추출된 수치 인용: 197건 | 정본 불일치: 124건 (이력 서술 포함 — 판정 대상)
```

- **재현 지점은 이 절을 담은 커밋**이다. 뒤따르는 `scope.md` 커밋은 이력 절을 더하므로
  **그 커밋에서는 199/125**다(실측) — R9-2와 달리 이번엔 두 커밋의 값이 같지 않다.
- R9-2의 **173/103은 확장 전 축의 값**이며 그 시점 기록으로 남는다.

### R10-3. 판정 — F2-1 3지점과 넓힌 축의 적출

**표 제목의 범위를 좁힌다**(verifier F3-1) — 아래 4행 중 **축이 기계로 지목한 것은
`checklist.md:20`·`:21`과 `scope.md:103`**이고, **`:173`은 축이 지목하지 않았다.**
수정 전 그 줄은 "이 파일의 **29건**이…"이고 `§12` arm은 `N행`을 요구하므로 어휘가
어긋난다(`8540239`에서 `:173`에 대한 매치는 전 항목 통틀어 0건). `:173`은 **F2-1이
부수 지점으로 지목한 것을 사람이 판정해 고친 것**이다. 그 행의 「추출 → 정본」 열이
수치쌍이 아니라 `29건 / 한 날짜`로 적혀 있어 형식으로는 이미 구분됐으나, 제목이
범위를 넓게 말했다.

| 지점 | 추출 → 정본 | 판정 |
| --- | --- | --- |
| `checklist.md:20` A1 「결정 근거」 | 29 → **30** | **F2-1. 살아 있는 현재 값 주장 — 고쳤다.** 「30행 각각에 3열 — 라운드 1에 29행(2026-08-26), 2026-08-27 결정으로 1행 추가」 |
| `checklist.md:21` A1 「결번 사유」 | 29 → **35** | **F2-1 부수. 고쳤다** — 「29행 추가 + 2026-08-27 1행, 결번 표는 이제 **35행**(0A 5 + 29 + 1)」 |
| `checklist.md:173` §7 A7 | 29건 / 한 날짜 | **F2-1 부수. 고쳤다** — **30건** · 두 날짜. `decisions.md` 머리가 두 날짜를 덮는다고 적으므로(V-6 수정) 대조도 두 날짜를 센다. **V-6이 만든 비대칭이다** |
| `scope.md:103` | 29 → 30 / 29 → 35 | **이력.** `e06e62a`(라운드 1) 커밋 내용 기록이며 그 시점 참 |

**넓힌 축의 추가 발견은 없다** — F2-1이 지목한 3지점 외에 살아 있는 현재 값 주장의
불일치가 나오지 않았다.

**§12 두 arm의 오탐**(한계로 기록):

| 지점 | 왜 오탐인가 |
| --- | --- |
| `checklist.md:20`·`:21`의 성분값(1 · 5 · 27 · 29 · 35) | 고친 행 **안의 구성 요소**다. arm은 한 줄의 모든 `N행`을 뽑으므로 머리값과 성분값을 구분하지 못한다. 머리값(30 · 35)은 일치한다 |
| `checklist.md:249` "§12 G3 +2행" · `commands.md:782` "§12.1 결번 표 2행" | 행 **수**가 아니라 적출된 행의 개수다 |
| `commands.md`의 R9-1 스크립트 본문 | evidence가 스크립트를 인라인하므로(형태 4) 스캔 대상에 자기 소스가 들어간다. 절 번호 오탐은 `(?<![.\d])` 전방부정으로 제거했다 |

### R10-4. F2-2 · F2-3

- **F2-2** R9-3 자기참조 경고의 시제 — "다음 실행부터는"을 **"이미 위 건수 안에 있다"**로.
  기록된 실행이 그 절을 담은 커밋에서 나왔으므로 6건은 이미 포함돼 있었다.
- **F2-3** `inv2.py` 출력이 5줄만 실려 있었다(스크립트는 6줄). **R8-3·R9-5 두 곳에
  `본문 참조 - 활성 등록: 30 건 (해소·결번 포함)`을 복원**했다. 접은 표기가 아니라
  누락이었으므로 선언이 아니라 복원이 맞다. 값은 각 블록의 커밋(`3ed76e9` · `c58d8f8`)
  에서 실행해 **둘 다 30**임을 확인하고 넣었다.

### R10-5. id 축 스윕 · 불변

```
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 367건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ wc -l docs/discovery/capability-map.md
    3171 docs/discovery/capability-map.md
```

- id 축 **불일치 0**. 이번 라운드는 **산출물을 바꾸지 않았다** — `capability-map.md`는
  `098bf03` 이후 그대로이고 이번 수정은 전부 evidence다.

---

## 2026-08-27 — Codex 송부 전 legacy 인용 정정 (0B 선행 조사 C-1~C-4 + verifier F3-1)

**이건 저장소 안 정합 검증이 못 잡는 부류다.** Codex worktree에 `bid-vector`가 없어 매
라운드 residual risk에 "저장소 밖 파일·commit 주장은 재현하지 않았다"가 적혔고, 여덟
라운드가 문서 내부 정합만 훑었다. **전부 `git show ed4b06c:`로 직접 열어 재확인**하고
고쳤다 — 상류 조사의 실측을 그대로 믿지 않았다.

### R11-1. C-1 — 출처가 문서가 아니라 스크립트다 (가장 무겁다)

```
$ git show ed4b06c:scripts/backtest_latest_award_holdouts.py | grep -n '0.9~2.7%p'
1203:            "Known limit: shallow undercuts (0.9~2.7%p, 지방계약/공공기관/수의견적 등 "
$ git show ed4b06c:scripts/backtest_latest_award_holdouts.py | sed -n '1203,1204p'
            "Known limit: shallow undercuts (0.9~2.7%p, 지방계약/공공기관/수의견적 등 "
            "다른 하한 체계 가능성) outside those gates are NOT resolved and still surface as below_legal_floor — the data alone cannot tell which tier applied.",

$ git show ed4b06c:docs/operations/latest-award-holdout-backtest.md | grep -n '0\.9~2\.7\|0\.9~1\.7'
240:낮게 읽힌다(관측된 0.9~1.7%p 하회와 정합).
```

- **`0.9~2.7%p`는 백테스트 문서에 없다.** 리포트 caveat **문자열**로
  `scripts/backtest_latest_award_holdouts.py:1203-1204`에 있다.
- 문서 `:277`의 같은 한계 서술("**남는 한계(정직 표기):** 위 두 게이트 밖의 얕은
  하회…")에는 **숫자가 없다.**
- 문서 `:240`의 `0.9~1.7%p`는 **보고 낙찰률 basis 독립성** 축의 수치다. 다른 축이므로
  섞지 않는다.
- 고친 곳 3: `capability-map.md` DEC-09 블록 · §13 인계 행, `decisions.md` `OPEN-DEC-09` 절.
- **이 오류는 팀 리드 분석 단계 서술의 승격**이다 — §10.1 **형태 2의 다섯 번째 사례**로
  기록했다.

### R11-2. C-2 — 경로와 행

```
$ git ls-tree -r --name-only ed4b06c | grep floor_applicability
app/ai/floor_applicability.py
tests/test_floor_applicability.py
$ git show ed4b06c:app/ai/floor_applicability.py | sed -n '126,127p'
# 보수적으로 **명백한 것만** 넣는다. 조직 종류 어미(공사/공단/청/시/군)는 국가·지자체
# 기관을 뜻하므로 여기 없고 기본값 ``applicable`` 로 남는다.
```

- 경로는 `app/ai/floor_applicability.py`다(`app/ai/predictors/` 아래가 아니다 — 같은
  디렉터리에 있는 것은 `legal_floor_spec.py`이고 그 인용은 맞다).
- 지자체가 기본값 `applicable`에 남는다는 근거 줄은 **`:126-127`**이다.
- 고친 곳 4: `capability-map.md` DEC-09 · §12.2 DEC-09 행 · §13 인계 행, `decisions.md`.
- **함께 재확인한 인접 인용 2건은 맞다** — `tests/test_floor_applicability.py:70`이
  `("울산광역시 울주군", FLOOR_APPLICABLE)`, `app/ai/predictors/legal_floor_spec.py:30-31`이
  "지방계약(지자체)은 별도 율 체계로 / 2025-07-01 기시행됐고 이번 스코프에서 제외한다".

### R11-3. C-3 재실측 — 블록 끝이 3줄 더 있다

```
$ git show ed4b06c:app/services/opportunity_monitoring/filters.py | sed -n '62,70p'
        project_budget = float(project.budget_estimate or 0.0)
        min_budget = float(strategy.min_budget_estimate or 0.0)
        max_budget = float(strategy.max_budget_estimate or 0.0)
        if min_budget > 0 and project_budget < min_budget:
            return StrategyFilterResult(matched=False, reasons=[])
        if max_budget > 0 and project_budget > max_budget:
            return StrategyFilterResult(matched=False, reasons=[])
        if min_budget > 0 or max_budget > 0:
            reasons.append("예산 범위 일치")
```

- 비교 블록은 **`:62-70`** — `:65-66`·`:67-68`이 두 guard, `:69-70`이 사유 append다.
- 라운드 2의 실행(`sed -n '62,67p'`)은 **6줄만 떴다.** 그 출력은 실행 그대로 두고(형태 3)
  범위 정정 주석을 그 자리에 달았다. **어느 지점인가라는 실질 주장은 바뀌지 않는다.**
- 고친 곳 3: `capability-map.md` §13 인계 행, `commands.md` 라운드 2 절, `checklist.md` L-7r.

### R11-4. C-4 — in_scope에 없다

```
$ git ls-tree -r --name-only ed4b06c | grep collection_jobs
app/tasks/collection_jobs.py
$ grep -rn 'services/collection_jobs' docs/discovery/capability-map.md reports/evidence/m0/0a2/
(매치 없음)
```

- 실제 경로는 `app/tasks/collection_jobs.py`이나 **`app/services/collection_jobs.py`
  인용은 in_scope 산출물·evidence에 들어와 있지 않다.** 0A 노트(`_workspace/`)는 이 slice
  범위 밖이므로 **손대지 않았다.**
- **재실행 주의**: 위 grep은 `reports/evidence/m0/0a2/`를 훑으므로 **이 절이 커밋된 뒤로는
  이 기록 자신이 매치된다**(R11-4 본문 2줄과 `scope.md` C-4 행). "매치 없음"은 **정정 전
  상태**의 결과다. 산출물·evidence에 실제 인용이 없음을 다시 보려면 이 절과 `scope.md`
  「Codex 송부 전」 절을 제외하고 읽으면 된다 — 스윕 기록들과 같은 자기참조다.

### R11-5. F3-1 · §10.1 형태 판단

- **F3-1**: R10-3 표 제목이 축의 적출 범위를 넓게 말했다 → 제목을 좁히고, `:173`이
  **축이 아니라 사람 판정으로 고쳐진 것**임을 표 위에 적었다.
- **§10.1 형태 판단**: **새 형태를 만들지 않고 형태 2를 넓혔다.** 근거 셋은 `checklist.md`
  §10.1 「형태 2의 판단」에 적었다 — ① 이 표는 원인으로 형태를 나누는데 C-1~C-3의 원인은
  하나("legacy를 열지 않고 legacy를 인용했다")이고 형태 2의 규칙 문장이 이미 그것을
  요구한다(빠진 것은 규칙이 아니라 **적용 범위**), ② 한 원인을 두 형태로 쪼개면 각 형태가
  자기 절반만 방어한다 — **형태 5가 절차화에서 수치 축을 잃고 네 번째 재발을 낸 전례**,
  ③ Codex 레인의 사각지대는 **레인의 속성**이지 실패 형태가 아니다.
- **절차화는 하지 않았다** — legacy 인용은 정본을 계산할 수 있는 집계가 아니라 사람이
  파일을 열어야 하는 종류다. 근거 없이 스크립트를 만들면 형태 5가 밟은 길을 되풀이한다.
  **0B가 legacy를 본격적으로 훑으므로 그 라운드가 절차의 필요를 판단할 자리**다.

### R11-6. 넓힌 수치 축 · id 축 · 불변 재실행

```
$ python3 numsweep.py          # 아래 R11-7이 현재 head 값으로 갱신한다
추출된 수치 인용: 200건 | 정본 불일치: 128건 (이력 서술 포함 — 판정 대상)
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 367건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ wc -l docs/discovery/capability-map.md
    3176 docs/discovery/capability-map.md
```

- **이번 정정은 분류·OPEN 집계를 바꾸지 않는다** — 경로·행 범위·출처 종류만 고쳤다.
  **바뀐 수치는 산출물 줄 수 하나**(3,171 → **3,176**)이며 C-1의 출처 설명이 5줄
  늘어난 결과다. **그 파급을 넓힌 수치 축이 스스로 적출했다** — `checklist.md:7`(head
  기준 줄 수)·`:582`(§14.3 후속)를 지목해 함께 고쳤다. `scope.md:509`는 라운드 7 완결
  3차 블록 안의 그 시점 값이라 유지했다.
- 그 외 살아 있는 현재 값 주장의 불일치 **신규 0건**.
- id 축 **불일치 0**(OPEN을 신설·복원·해소하지 않았다).
- **기준 SHA**: 위 `200 / 128`은 **`1fc5968`에서만 성립**한다(Codex 6차 medium #2).
  뒤따른 `2adadb8`·`520f928`·`ab69012`는 evidence를 늘려 **203**이 된다 — 스윕이 자기
  기록을 훑기 때문이다. **현재 head 값은 아래 R11-7**에 있다.

### R11-7. 수치 축 재실행 — 현재 head 기준 (Codex 6차 medium #2)

**R11-6의 `200 / 128`은 `1fc5968`에서만 성립한다.** 그 뒤 커밋들이 evidence를 늘렸고
스윕이 자기 기록을 훑으므로 값이 올라갔다. **최신 절이 최신 값을 말하도록** 갱신하되,
이 절 자신도 같은 성질이므로 **재현 지점을 함께 못박는다**(R9-2·R10-2와 같은 방식).

```
$ python3 numsweep.py
추출된 수치 인용: 204건 | 정본 불일치: 128건 (이력 서술 포함 — 판정 대상)
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 367건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ wc -l docs/discovery/capability-map.md
    3176 docs/discovery/capability-map.md
```

| 커밋 | 수치 축 추출 / 불일치 | 왜 다른가 |
| --- | --- | --- |
| `1fc5968` | 200 / 128 | R11-1~R11-6을 담은 커밋. R11-6이 기록한 값 |
| `2adadb8` · `520f928` · `ab69012` | 203 / 128 | `scope.md` 이력 · R11-4 주의 · head_sha가 인용을 3건 더했다 |
| **이 절을 담은 커밋** | **204 / 128** | 이 절과 medium #1 처리가 더한 만큼 |

- **재현 지점은 이 절을 담은 커밋**이다. 뒤따르는 `scope.md` 커밋은 이력 절을 더하므로
  값이 다시 달라진다 — 그 값도 그 절에 적는다.
- **살아 있는 현재 값 주장의 신규 불일치 0건.** 이번 라운드는 분류·OPEN·줄 수를 바꾸지
  않았다(`capability-map.md`는 `1fc5968` 이후 무변경, **3,176줄**).
- **새 오탐 1종**(한계로 기록): `commands.md:1315` "§12.2 DEC-09 **행**"에서 `§12` arm이
  OPEN id 조각 `09`를 행 수로 읽는다. `checklist.md:249`("G3 +2행")와 같은 부류 —
  **행 수가 아닌 곳의 숫자 + `행`**이다. 실제 불일치를 가리지 않는다.
- id 축 **불일치 0**.

---

## 2026-08-27 — Codex 7차 대응: 원본 절 변경 전수 대조 (medium 1건)

Codex 4차 리뷰 판정 `request_changes` — **blocker 0 / high 0 / medium 1 / low 0**.
직전 2건은 둘 다 해소 판정됐고 독립 집계도 capability **95** · 분류 **61/17/6/11** ·
활성 OPEN **45** · G 분포 **3/7/15/8/7/5**를 재현했다. verdict는
`codex-review-20260827T021632Z.json`(이번 커밋에 등재, append-only).

### R12-1. 전수 대조 실행

`decisions.md` provenance가 "원본 절 안의 slice 정정은 **3건뿐**"이라 선언했는데 Codex가
최초 커밋 blob과 head를 통째로 diff해 **선언되지 않은 4번째**(`OPEN-QUAL-08` 비고)를
찾았다. 셈으로 전칭을 주장한 것이 뿌리이므로 **셈을 버리고 재현 목록으로 바꿨다.**

```
$ git diff -U0 6a4e49b -- reports/evidence/m0/0a2/decisions.md | grep -E '^@@'
@@ -4 +4 @@
@@ -6,3 +6,12 @@
@@ -12,2 +21,4 @@
@@ -15,0 +27,41 @@
@@ -91,0 +144,9 @@
@@ -174 +235 @@
@@ -189 +250 @@
@@ -191 +252,15 @@
@@ -265,2 +340,9 @@
@@ -295,0 +378,113 @@
```

**훅 10개를 전수로 절에 대응시켰다**(위 수치는 이 절을 담은 커밋 기준 — `decisions.md`를
고치면 뒤 훅 번호가 밀린다. 표는 `decisions.md` 안에 두어 그 파일과 함께 갱신된다).

| 훅(신규) | 절 | 성격 |
| --- | --- | --- |
| `4` · `6` · `21` | 파일 머리 | slice 메타(**원본 절 아님**) |
| `27` | 「이 파일의 구성 — provenance」 | slice 절 신설 |
| `144` | 「OPEN-STR-01」 | **정정** `R-07` 식별자 |
| `235` | 「M1 차단 묶음」 `OPEN-QUAL-08` 행 | **포인터 추가 — 미선언이었다** |
| `250` · `252` | 「OPEN-DEC-09」 | **정정** C-1·C-2 |
| `340` | 「상호작용 모델」 하류 파급 | **철회** 21,321건 |
| `378` | 말미 새 절 전부 | slice 절 신설 |

- **원본 절 안에 들어간 것은 네 자리뿐이다** — `144` · `235` · `250`/`252` · `340`.
  성격은 **정정 2 · 철회 1 · 포인터 1**.
- **Codex가 찾은 `OPEN-QUAL-08` 외에 미선언 변경은 없다.**

### R12-2. `OPEN-QUAL-08` 성격 판정 — 의도적이며, 되돌리지 않는다

```
$ git log --oneline -S'capability QUAL-11의 분류 상향 근거는 아래 절에 축어로 옮겼다' \
    -- reports/evidence/m0/0a2/decisions.md
4c29cce docs(m0-0a2): OPEN 신설 2건, 잠정 묶음 분리, 인용 이관, 절 순서·registry 정정 …
```

- **의도적이다.** 라운드 2가 「`OPEN-QUAL-08` 승인 권고의 축어 인용」 절을 만들면서
  **같은 커밋에서** 원본 표 행에 그 절로 보내는 포인터를 달았다. 커밋 제목의 "인용 이관"이
  그 작업이다. 편집 중 섞인 것이 아니다.
- **사실을 바꾸지 않는다.** 결정 내용·근거 어느 것도 손대지 않고 **한 구절을 덧붙였을
  뿐**이며, 가리키는 대상은 같은 파일 안의 slice 절이다.
- **따라서 되돌리지 않고 드러냈다** — 되돌리면 원본 표 독자가 축어 인용이 어디로 갔는지
  알 길이 없어져 A7(리뷰어가 인용을 따라갈 수 있게 한다)이 도로 깨진다. 그 자리에
  `*(slice 추가 — 라운드 2 4c29cce)*`를 달고 전수 표·A7 목록에 등재했다.
- 성격이 다른 셋(정정·철회)과 구분해 **「포인터 추가」**로 적었다 — 취소선은 쓰지 않았다.
  지울 원본 문장이 없기 때문이다.

### R12-3. §10.1 형태 판단 — **형태 6을 신설했다**

지금까지 두 번은 새 형태를 만들지 않는 쪽이 옳았다(둘 다 형태 2로 흡수). **이번은 다르다.**

| 후보 | 왜 아닌가 |
| --- | --- |
| 형태 1 (확인하지 않고 기재) | 규칙이 "실행한 것만 적는다"인데, **`git diff`를 돌려 맞는 수를 적었어도 다음 편집에서 다시 틀린다.** 규칙과 실패 모드가 맞지 않는다 |
| 형태 5 (정정을 인용 지점에 전파하지 않기) | 수치 축 커버리지 기준이 "예외 N건"을 덮으므로 **사후 적출은 된다.** 그러나 형태 5는 *정정한 뒤* 전파하는 규칙이고 이 실패는 **애초에 전파가 필요한 형태로 쓴 것**이다 — 두 형태는 **예방(6)과 적출(5)로 짝**을 이룬다 |

**형태 6 「셈으로 전칭을 주장하기」** — 규칙은 **"전칭 주장은 셈이 아니라 재현 명령으로
쓴다"**다. `checklist.md` §10.1에 표 행과 근거를 적었다. 이번 처리가 그 첫 적용이다.

### R12-4. 스윕·불변 재실행

```
$ python3 numsweep.py
추출된 수치 인용: 209건 | 정본 불일치: 128건 (이력 서술 포함 — 판정 대상)
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 369건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ wc -l docs/discovery/capability-map.md
    3176 docs/discovery/capability-map.md
```

- **기준 SHA는 이 절을 담은 커밋**이다. 뒤따르는 `scope.md` 커밋은 이력 절을 더하므로
  값이 달라지며 그 값은 그 절에 적는다(medium #2가 지적한 형태를 되풀이하지 않는다).
- **산출물 `capability-map.md`는 건드리지 않았다** — `1fc5968` 이후 무변경, **3,176줄**.
- 살아 있는 현재 값 주장의 신규 불일치 **0건**. id 축 **불일치 0**.

---

## 2026-08-27 — Codex 8차 대응: **형태 6 전수 적용** (medium 3건)

Codex 5차 리뷰 판정 `request_changes` — **blocker 0 / high 0 / medium 3**. 직전 1건은
해소 판정됐고(provenance 훅 10개 일치·`OPEN-QUAL-08` 표시 확인, 기준 `3ec50fb`에서
numsweep 209/128 · OPEN sweep 45/369/52 · 3,176행 재현). verdict는
`codex-review-20260827T024238Z.json`(등재, append-only).

**#1·#2는 형태 6의 인스턴스다.** 직전 라운드가 형태 6을 신설하면서 `decisions.md`
provenance **한 곳에만** 적용하고 evidence 전수를 돌리지 않았다 — **축을 만들면 그 축으로
전수를 훑는다**는, 이 slice가 형태 5로 네 번 배운 것을 되풀이했다. §10.1 형태 6에
**재발 1회**로 기록했다.

### R13-1. 형태 6 스윕 — 정의와 스크립트 (형태 4 — 인라인)

**수치 축(`numsweep`)과 대상이 다르다.** 수치 축은 **집계**(활성 OPEN 수, 분류 4종 …)를
보고, 이것은 **열거**를 본다 — `N항목`·`N건`·`N종`처럼 **목록을 동반하는 셈**이 실제 열거
대상 수와 맞는가다. 정본은 산출물·저장소에서 직접 계산한다.

**정본을 만들 수 없으면 주장하지 않는다** — acceptance 블록에 `결정 무관`/`조건부`/`잠정`
묶음이 있으면 2-space bullet 셈이 **항목 수가 아니라 묶음 수**이므로 그 capability는
대조 대상에서 뺀다(현재 평면 블록 60개만 정본이 있다).

```python
# 형태 6 스윕 — "셈으로 전칭을 주장하기"의 전수 적용.
# 수치 축(numsweep)은 *집계*를 본다. 이것은 *열거*를 본다 — "N항목/N건/N종…"이
# 목록·열거를 동반하는 자리에서, 그 N이 실제 열거 대상 수와 맞는지 본다.
# 정본은 산출물·저장소에서 직접 계산한다.
import re, glob, subprocess, collections
CM = "docs/discovery/capability-map.md"
EV = sorted(glob.glob("reports/evidence/m0/0a2/*.md"))
cm = open(CM, encoding="utf-8").read().split("\n")

# ---------- 정본 ----------
# 1) capability별 acceptance 항목 수
ACC_HEAD = re.compile(r'^\s*-?\s*\*\*(Acceptance scenario|채택 시 요구되는 관찰 가능 동작)')
acc = {}
starts = [i for i, l in enumerate(cm) if l.startswith("### ")]
for n, st in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(cm)
    cid = cm[st][4:].strip().split(" ")[0]
    body = cm[st:e]
    idx = [k for k, l in enumerate(body) if ACC_HEAD.match(l)]
    if not idx: continue
    k = idx[0] + 1; cnt = 0; grouped = False
    while k < len(body):
        l = body[k]
        if re.match(r'^- ', l): break          # 다음 최상위 bullet에서 종료
        if re.search(r'\*\*(결정 무관|조건부 —|잠정 —)', l): grouped = True
        if re.match(r'^  - ', l): cnt += 1     # acceptance 항목은 2-space bullet
        k += 1
    # 묶음(결정 무관 / 조건부 / 잠정)이 있는 블록은 2-space 셈이 '묶음 수'라
    # 항목 수의 정본이 되지 못한다. 정본을 만들 수 없으면 주장하지 않는다.
    acc[cid] = None if grouped else cnt
# 2) verdict JSON
vj = subprocess.run(["git","ls-files","reports/evidence/m0/0a2/codex-review-*.json"],
                    capture_output=True, text=True).stdout.split()
# 3) decisions.md provenance 훅
hunks = [l for l in subprocess.run(
    ["git","diff","-U0","6a4e49b","--","reports/evidence/m0/0a2/decisions.md"],
    capture_output=True, text=True).stdout.split("\n") if l.startswith("@@")]
# 4) decisions.md 최상위 절 수
dsec = [l for l in open("reports/evidence/m0/0a2/decisions.md",encoding="utf-8") if l.startswith("## ")]
# 5) evidence 파일 수
GT = {"verdict JSON": len(vj), "provenance 훅": len(hunks),
      "decisions.md ## 절": len(dsec), "evidence .md 파일": len(EV)}
print("=== 정본 (저장물에서 직접 계산) ===")
for k, v in GT.items(): print(f"  {k}: {v}")
print(f"  acceptance 항목 수: 평면 블록 {sum(1 for v in acc.values() if v is not None)}개 산출 (예: STR-16={acc.get('STR-16')})")
print()

# ---------- 대조 ----------
CHK = [
 # acceptance 항목 수: 줄에 등장하는 capability id의 정본과 대조(일반화)
 ("acceptance 항목 수", None, None, None, None),
 ("verdict JSON 건수", lambda l: "codex-review-*.json" in l or ("codex-review" in l and "존재" in l),
  re.compile(r'\*{0,2}(\d{1,2})\s*건'), lambda m: int(m.group(1)), len(vj)),
 ("provenance 훅 수", lambda l: "훅" in l and "provenance" not in l.lower(),
  re.compile(r'훅\s*\*{0,2}(\d{1,2})\s*개'), lambda m: int(m.group(1)), len(hunks)),
]
bad = []; tot = 0
# capability id. `OPEN-NOTI-02` 같은 OPEN id의 꼬리를 capability로 읽지 않는다.
CAPID = re.compile(r'(?<!OPEN-)\b([A-Z]{2,4}-\d{2})\b')
ITEM  = re.compile(r'\*{0,2}(\d{1,2})\s*항목')
for f in EV:
    cur = None                                # 절 안에서 마지막으로 언급된 capability
    for i, l in enumerate(open(f, encoding="utf-8").read().split("\n"), 1):
        if l.startswith("## "): cur = None     # 절이 바뀌면 초기화
        found = [c for c in CAPID.findall(l) if c in acc]
        if found: cur = found[-1]
        # acceptance 항목 수 — 줄이 capability id를 명시할 때만
        for m in ITEM.finditer(l):
            ids = [c for c in CAPID.findall(l) if c in acc]
            via = "줄 직접"
            if not ids and cur:              # 줄에 없으면 절 안의 직전 capability
                ids = [cur]; via = "절 캐리오버 — 오귀속 가능"

            if len(ids) == 1 and acc.get(ids[0]) is not None:
                tot += 1
                if int(m.group(1)) != acc[ids[0]]:
                    bad.append(("acceptance 항목 수(%s, %s)" % (ids[0], via), f, i,
                                int(m.group(1)), acc[ids[0]], l.strip()[:150]))
        for name, filt, rx, ex, exp in CHK:
            if exp is None or filt is None or not filt(l): continue
            for m in rx.finditer(l):
                tot += 1
                if ex(m) != exp: bad.append((name, f, i, ex(m), exp, l.strip()[:150]))
print(f"=== 계산 가능한 열거 주장 대조 ===\n추출 {tot}건 | 불일치 {len(bad)}건\n")
for n, f, i, g, e, l in bad:
    print(f"[{n}] {f}:{i}  추출={g} 정본={e}\n   {l}\n")

# ---------- 광역 후보 스캔 ----------
UNIT = re.compile(r'(?<![.\d])(\d{1,3})\s*(항목|종|곳|건|개|행|줄)')
ENUM = ("—", "·", ":", "다음", "아래", "열거", "전부", "뿐")
cand = collections.Counter(); rows = []
for f in EV:
    for i, l in enumerate(open(f, encoding="utf-8").read().split("\n"), 1):
        if not any(t in l for t in ENUM): continue
        for m in UNIT.finditer(l):
            cand[m.group(2)] += 1
            rows.append((f, i, m.group(0), l.strip()[:120]))
tot_c = sum(cand.values())
assert tot_c == sum(dict(cand).values())   # 합과 총계는 같은 실행에서 나온다
print(f"=== 광역 후보(열거 동반 셈) : {tot_c}건 ===")
print("  단위별:", dict(cand), "| 합:", sum(dict(cand).values()))
print("  항목·종·곳 소계:", sum(cand[u] for u in ("항목","종","곳")))
```

### R13-2. 실행 결과

```
$ python3 enumsweep.py
=== 정본 (저장물에서 직접 계산) ===
  verdict JSON: 5
  provenance 훅: 10
  decisions.md ## 절: 10
  evidence .md 파일: 4
  acceptance 항목 수: 평면 블록 60개 산출 (예: STR-16=7)

=== 계산 가능한 열거 주장 대조 ===
추출 17건 | 불일치 8건

=== 광역 후보(열거 동반 셈) : 474건 ===
  단위별: {'줄': 35, '행': 33, '종': 19, '건': 319, '항목': 13, '곳': 19, '개': 36} | 합: 474
  항목·종·곳 소계: 51
```

> **정정 (verifier F-2·F-3, 2026-08-27)** — 여기 있던 `448` / 단위별 합 `439`는
> **두 실행의 출력을 섞어 붙인 것**이다. 단위별 dict와 총계는 같은 실행에서 나오므로
> 원래 어긋날 수 없다 — 총계는 나중 실행, dict는 앞선 실행에서 복사했다(**형태 3**).
> 위는 **기준 커밋 한 번의 실행**으로 교체했고, 스크립트가 `합`과 `소계`를 함께 찍어
> 다시 섞이지 않게 했다(합·총계 일치를 `assert`로 고정).
>
> **광역 후보 수는 기록 자신을 포함한다** — 이 절에 수를 쓰면 그 수가 다시 후보가 된다.
> 그래서 **값이 변하지 않을 때까지 재실행·교체를 반복해 고정점에서 멈췄다**(수를 같은
> 자릿수로 바꾸면 후보 수가 변하지 않으므로 한 번의 반복으로 수렴한다). 이 절과
> R13-7의 값은 **이 절을 담은 커밋에서 그대로 재현된다.**

**광역 후보 수는 이 절을 담은 커밋 기준**이다 — 스윕이 evidence 자신을 훑으므로 기록을
쓰면 늘어난다(형태 6의 규칙대로 기준을 못박는다). **계산 가능한 대조의 13/5는 정본과의
대조라 그 성질이 없다.**

**두 겹으로 돌렸다.** ① **계산 가능한 열거**는 정본과 자동 대조하고, ② **광역 후보**는
`N항목|N건|N종|N개|N행|N곳|N줄`이 열거 어휘(`—`·`·`·`아래`·`전부`·`뿐` 등)를 동반하는
자리를 전부 뽑아 사람이 판정했다.

### R13-3. 판정 — 살아 있는 열거 주장의 불일치

| 지점 | 추출 → 정본 | 처리 |
| --- | --- | --- |
| **#1** `checklist.md:70` A3 증명 | STR-16 acceptance **6항목** → **7** | 빠진 것은 **금액 범위 검색의 basis 일치**(`OPEN-STR-01` 결정 · `OPEN-QUAL-10` 축). 7항목으로 고치고 **재현 명령을 셀에 넣었다** |
| **#2** `checklist.md:213` 규격 산출물 | `codex-review-*.json` **2건** → **5** | **셈을 지우고** `git ls-files 'reports/evidence/m0/0a2/codex-review-*.json'`로 바꿨다(형태 6 규칙). 목록은 참고로 병기 |

**추가 발견은 없다.** 광역 후보 중 `항목`·`종`·`곳` 단위 **51건**(기준 커밋 실측,
위 `소계`)**을 전수로 읽었고**, #1 외에
살아 있는 열거 주장의 불일치가 없었다. 나머지는 라운드별 이력의 그 시점 값(`불변 8종`·
`부재 주장 2곳`·`Markdown 제목 6곳` 등)이거나 도메인 수치(`운영 라이브러리 6종`)다.
`건`·`개`·`행`·`줄` 단위는 대부분 라운드별 finding 수·집계이며 **집계 축은 `numsweep`이
이미 덮는다**(§12.1/§12.2 행 수, 조건부 개수, 줄 수).

**판정 보류 1건 — 정본을 만들지 않았다**: `commands.md:106`의 QUAL-11 "이번에 **6항목**을
신설했다"는 **라운드 1 시점 기록**이며, 그 뒤 라운드 5가 그 블록을 조건부 묶음으로
쪼갰다. 지금 구조에서는 "항목 수"의 정본이 성립하지 않으므로(위 규칙) **대조 대상에서
빼고 이력으로 둔다.** 셈을 지어내 맞추지 않는다.

**오탐 5건**(한계로 기록) — **전부 이 라운드의 기록 자신**이 적발된 틀린 값을 인용해서다:
`checklist.md:287` 형태 6 표 행(`3건뿐`·`2건`·`6항목`)과 위 R13-3 표 두 행(`6항목`·`2건`).
기록의 성질상 정상이며 `R9-3`·`R10-3`에 적은 자기참조와 같은 부류다.

**스윕 자체의 결함 1건을 부분적으로 줄였다**: `OPEN-NOTI-02` 같은 **OPEN id의 꼬리를
capability `NOTI-02`로 읽는** 문제에 `(?<!OPEN-)` 전방부정을 넣었다.

> **정정 (verifier F-5, 2026-08-27) — "막았다"는 완결 주장을 철회한다.** 전방부정은
> **같은 줄의 직접 매치만** 막고, **절 안의 직전 capability를 물려주는 `cur` 캐리오버는
> 못 막는다.** HEAD에서 같은 오탐이 다시 난다(`scope.md`의 "acceptance 3항목"은 NOTI-05
> = 3으로 정합인데 `NOTI-02`(=5)로 오귀속된다).
>
> **캐리오버를 없애지 않기로 했다** — 그 캐리오버가 **#1을 찾아낸 수단**이다
> (`checklist.md:70`은 줄에 capability id가 없고 절 안 직전 언급으로만 STR-16에
> 묶인다). 없애면 적출력을 잃고, 더 좁히려면 근거 없는 휴리스틱이 필요하다.
> **정본을 만들 수 없으면 주장하지 않는다**는 이 스윕의 규칙을 도구 자신에도 적용한다.
>
> **대신 오귀속이 눈에 보이게 했다** — 출력이 해석 경로를 함께 찍는다:
> `acceptance 항목 수(STR-16, 줄 직접)` vs `acceptance 항목 수(NOTI-02, 절 캐리오버 —
> 오귀속 가능)`. **한계는 남고, 라벨이 그것을 감추지 않는다.**

### R13-4. #3 산출물 정합화 — NOTI-05 블록 전문 판독

블록 전문을 읽었다. 미갱신 서술은 「분류 근거(정책 미확정)」이었다.

> **정정 (verifier F-1, 2026-08-27)** — 여기 있던 "**한 bullet뿐**"은 **거짓이었다.**
> 같은 블록 `분류` bullet이 **NOTI-05에 존재하지 않는 `V2 제약`을 가리키고 있었다**
> (실물은 **OPS-03의 `V2 제약`** `:2345`). 미갱신이 아니라 `e06e62a`(운영자 결정 반영)의
> 재분류가 **신설한 잘못된 포인터**다. 아래 표의 `분류` 행도 "정합"이 아니었다.
> **「수를 안 쓰는 전칭」도 전칭이다** — 형태 6에 이 사례를 남겼다.

| bullet | 판독 |
| --- | --- |
| 사용자 가치 | **정합** — 이미 "at-most-once 확정 (운영자 결정 2026-08-26), `OPEN-NOTI-02` 해소"라 적고 회수 경로가 앱 알림함임을 밝힌다(라운드 5 M-3 처리분) |
| 분류 `후속` | **거짓 포인터** — 근거(전달 의미 확정 + pull 확정)는 정합하나 "아래 `V2 제약`"이 **NOTI-05에 없는 bullet**을 가리켰다(verifier F-1). **OPS-03의 `V2 제약`**(`:2345`)으로 명시했고 `경계` bullet("outbox 메커니즘 일반은 OPS-03이 소유")과 정합한다 |
| 분류 근거 | **정합** — legacy 설계 관찰(커밋 경계·`running` 격리)이며 V2 요구 주장이 아니다 |
| **분류 근거(정책 미확정)** | **모순** — "사용자와 합의된 것인지 근거가 없다"가 확정 결정과 정면 충돌. **교체했다** |
| Acceptance 3항목 | **정합** — outbox 메커니즘 시나리오이며 조건부 표시가 필요 없다(`OPEN-NOTI-02`는 닫혔다) |
| 경계 | **정합** — 소유 범위 서술 |

교체 내용: legacy가 택한 at-most-once를 **V2도 채택**하며, legacy는 채널 멱등 키가 없어
**선택의 여지가 없었던 것으로 보이나** V2에서는 운영자가 그 절충(놓침 감수)을 **명시적으로
승인**했다는 구분을 남겼다. **라운드 5 M-3과의 관계**를 인용 블록 한 줄로 적었다 —
그때는 활성 `OPEN-NOTI-02`의 한 분기를 선점해 **라운드 5가 `at-most-once를 유지하면`이라는
한정어를 달았고**(`d140f92` — 조건을 **건** 것이지 걷어낸 것이 아니다), 지금은 그 OPEN이
닫혔으므로 확정 서술이 옳다. **`NOTI-02`가 밟은 경로(조건화 → 운영자 결정으로 확정 복귀)와 같은
형태**다.

### R13-5. 스윕·불변 재실행

```
$ python3 numsweep.py
추출된 수치 인용: 213건 | 정본 불일치: 134건 (이력 서술 포함 — 판정 대상)
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 371건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ wc -l docs/discovery/capability-map.md
    3182 docs/discovery/capability-map.md
```

- **기준 SHA는 이 절을 담은 커밋**이다(뒤따르는 `scope.md` 커밋은 값이 다르며 그 절에 적는다).
- **줄 수 전파**: NOTI-05 정합화로 3,176 → **3,182**. 수치 축이 6지점을 지목했고
  살아 있는 head 주장인 `checklist.md:7`을 고쳤다(**나머지 5곳은 각 라운드의 그 시점 값**
  — "`1fc5968` 이후 무변경" 같은 커밋 고정 서술이라 유지). §14.3 후속 줄에 3,182를 더했다.
- 분류·활성 OPEN 집계 **불변**. id 축 **불일치 0**.

### R13-6. Codex 8차 완결 — verifier F-1~F-5 처리 (2026-08-27)

재검증 `not-ready`(medium 2 · low 3). **#1·#2 해소, #3 부분 해소** — 지목된 bullet은
정확히 교체됐으나 같은 블록에 F-1이 남았다.

| # | 처리 |
| --- | --- |
| **F-1** | NOTI-05 `분류`가 **NOTI-05에 없는 `V2 제약`**을 가리켰다 → **OPS-03의 `V2 제약`**(`:2345`)으로 명시. 미갱신이 아니라 `e06e62a`의 재분류가 **신설한 잘못된 포인터**다. R13-4의 "한 bullet뿐"·`분류` 행 "정합"·`scope.md`의 같은 전칭을 실제 상태로 고쳤다 |
| **F-2** | 광역 후보 총계와 단위별 합이 어긋났다 → **원인은 두 실행의 출력을 섞어 붙인 것**(형태 3). 기준 커밋 한 번의 실행으로 교체하고 스크립트에 합·총계 `assert`를 넣었다 |
| **F-3** | "항목·종·곳 35건"이 다른 커밋 값 → 기준 커밋 실측 **51건**으로 교체(스크립트가 소계를 함께 찍는다) |
| **F-4** | "조건을 걷어냈다" → **"라운드 5가 `at-most-once를 유지하면` 한정어를 달았다(`d140f92`)"** — 조치를 명시했다. 세 서술 중 나머지는 건드리지 않았다 |
| **F-5** | "막았다"를 **철회**했다 — 판단과 근거는 위 R13-3 |

- **형태 6에 재발 2회를 기록**했다 — "**수를 쓰지 않아도 `뿐`·`전부`는 전칭이다.**
  셈만이 아니라 **열거를 닫는 어휘 전부**가 재현 명령이나 전수 근거를 요구한다."
  F-1이 그 축이 잡은 사례다. **새 형태는 만들지 않았다.**
- **줄 수**: F-1·F-4 정정으로 3,182 → **3,184**. `checklist.md:7`(head 주장)과 §14.3
  후속 줄에 전파했다.

#### 범위 밖 판단 — 열거를 동반하는 `건`·`개`의 사각지대

verifier §9 지적이 맞다. `numsweep`은 **정본이 정의된 지표만** 보고, `enumsweep`의 계산
가능한 대조도 acceptance 항목·verdict JSON·provenance 훅에 한정된다. **열거를 동반하는
`건`·`개`는 어느 축도 자동으로 보지 않는다.**

**이 slice에서 축을 넓히지 않고 0B로 넘긴다.** 근거 셋:

1. **정본의 출처가 이 slice 밖이다.** 남은 `건`·`개`의 대다수는 **라운드별 finding 수**
   (`verifier 발견 9건`, `Codex 4차 finding 6건` 등)이고, 그 정본은 verifier 리포트와
   verdict JSON이다. verdict JSON은 in_scope지만 **verifier 리포트는 `_workspace/`**라
   이 slice가 정본으로 삼을 수 없다.
2. **그것들은 시점 기록이다.** finding 수는 그 라운드에 확정돼 이후 바뀌지 않는 값이라
   형태 6이 겨냥하는 **"편집이 늘 때마다 틀려지는 셈"**과 성격이 다르다. 잘못 세었다면
   형태 1(확인하지 않고 기재)이지 형태 6이 아니다.
3. **근거 없이 축을 만들지 않는다.** 이 slice는 축을 세 번 만들었고 세 번 다 **실제
   적발된 결함이 먼저 있었다.** 지금은 이 부류의 실제 오류가 하나도 적출되지 않았다 —
   필요가 관측되기 전에 도구를 늘리면 형태 5가 밟은 길(범위를 못 잡은 절차)을 되풀이한다.

**0B가 판단할 자리**인 이유: 0B는 regression ledger에서 legacy 회귀 사례를 **건수로**
세고 그 정본이 저장소 안(commit·테스트)에 있다. 그때는 정본이 성립하므로 축을 넓힐
근거가 생긴다. **여기 사각지대가 있다는 사실 자체는 이 절이 기록으로 남긴다.**

### R13-7. 완결 재실행 (기준: 이 절을 담은 커밋)

```
$ python3 numsweep.py
추출된 수치 인용: 215건 | 정본 불일치: 134건 (이력 서술 포함 — 판정 대상)
$ python3 enumsweep.py
추출 17건 | 불일치 8건
=== 광역 후보(열거 동반 셈) : 474건 ===
  항목·종·곳 소계: 51
$ python3 sweep46.py
활성 OPEN: 45건 | 전체 매치: 372건
'해소/결번/취소선' 어휘와 같은 줄에 있는 매치: 52건 (판정 대상)
$ python3 inv2.py
분류: {'V2 필수': 61, '근거 부족': 11, '폐기': 6, '후속': 17} = 95
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 id: none
§12 활성 OPEN: 45
본문 참조 - 활성 등록: 30 건 (해소·결번 포함)
$ wc -l docs/discovery/capability-map.md
    3184 docs/discovery/capability-map.md
```

- **집계 불변**: capability **95** · 분류 **61/17/6/11** · 활성 OPEN **45**.
- `enumsweep` 불일치는 **전부 이 라운드 기록 자신의 자기참조**이며, 그중 하나는
  **`절 캐리오버 — 오귀속 가능`으로 라벨된 도구의 잔존 한계**다(F-5).
