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
  **`filters.py`**이며 그 안의 `:62-67`이 바로 예산 basis 비교 지점이다.
- 따라서 "감시·검색 경로는 세 commit에서 빠졌다"는 **과했다.** 빠진 것은 정확히
  **전략 예산 필터(`filters.py:62-67`)와 검색 경로(`projects.py:112-116`) 두 지점**이다.
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
$ python3 - <<'PY'   # 같은 판정 규칙, '복원' 없는 줄만
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
