# M0 / 0A3 — 검증 명령과 실행 출력

**range**: `48151b9...HEAD`. **아래 출력은 전부 HEAD `03d7240`에서 실제로 실행한 것**이며
접거나 손으로 압축한 블록은 없다(§10.1 **형태 3**). 셈은 이 절의 출력이 내고 **산문으로
옮겨 적지 않는다**(**형태 6**).

**0B의 축을 새로 만들지 않았다.** 이 slice에 필요한 것만 두었다 — legacy 인용 확인(C-1),
범위·불변(C-2·C-4), X-1 프레이밍 스윕(C-3), 형태 7 등재 확인(C-5). 스윕 스크립트를 새로
만들지 않은 이유는 **결함이 아직 한 번도 이 slice에서 관측되지 않았기 때문**이다
(0B가 세운 순서 — *"필요가 관측되기 전에 도구를 늘리지 마라"*). 유일한 스크립트
`inv0a3.py`는 새 도구가 아니라 **0A2 수치 축의 「정본 산출」 절을 그대로 떼어낸 것**이고
본문을 C-4에 인라인했다(**형태 4**).

---

## C-1. legacy 인용 재확인 — 직접 열었다 (§10.1 형태 2)

**X-1의 근거 두 자리를 `git show ed4b06c:`로 직접 열었다.** 상류 노트(0C 선행 조사)의
인용을 그대로 옮기지 않았다. **행 범위는 블록 경계까지 뜬다** — `bid_summary.py`의
`Field(` 호출은 **`:69`에서 시작해 `:77`에서 닫힌다**(그 앞뒤 한 줄을 함께 떠서
`:68`이 이전 필드의 닫는 괄호, `:78`이 다음 필드임을 확인했다). 0B가 라운드 5 `L-1`에서
`:70-77`로 한 줄 늦게 적었다가 고친 그 자리다.

```
### 실행 시점 HEAD = 03d7240

$ cd bid-vector && git show ed4b06c:app/domain/money.py | sed -n "20,31p"
class Basis(str, Enum):
    """금액이 어떤 기준(basis)으로 표현됐는지 — 교차 대입 금지의 단일 어휘.

    값은 저장소에서 이미 쓰는 문자열 리터럴(price_predictions·HistoricalData 등)과
    맞춘다. ``str`` 혼합 Enum이라 직렬화/비교 시 값 문자열을 그대로 쓸 수 있다.
    """

    PLANNED_PRICE = "planned_price"      # 예정가: 발주처가 산정한 예정가격(사정률 적용 후)
    BASE_AMOUNT = "base_amount"          # 기초금액(=사업금액): 투찰율의 곱셈 base (#162)
    WINNING_AMOUNT = "winning_amount"    # 낙찰가: 실제 낙찰된 투찰 금액
    BUDGET_ESTIMATE = "budget_estimate"  # 추정가격: 부가세 포함 추정 총액(법정 하한 구간 기준)


$ cd bid-vector && git show ed4b06c:app/schemas/bid_summary.py | sed -n "68,79p"
    )
    bid_base_to_estimate_ratio: Optional[float] = Field(
        default=None,
        description=(
            "기초금액 ÷ 추정가격(관측값). 1.0 이면 두 금액이 같고 1.1 부근은 전형적으로 "
            "부가세 관계가 관측되는 값이나, 이 비율만으로 과세 여부를 단정할 수는 없다"
            "(저장된 추정가격의 부가세 포함 여부가 이력상 일관되지 않다). 추정가격이 "
            "0 이면 null."
        ),
    )
    bid_base_note: str = Field(default=BID_BASE_NOTE)
    demand_agency: Optional[str] = None
```

**판정**:

- `money.py:30` — `BUDGET_ESTIMATE = "budget_estimate"  # 추정가격: **부가세 포함** 추정
  총액(법정 하한 구간 기준)`. **ex-VAT 단정과 정반대다.**
- `bid_summary.py:69-77` — `bid_base_to_estimate_ratio`의 `Field(` 한 덩어리가
  *"1.1 부근은 전형적으로 부가세 관계가 관측되는 값이나, 이 비율만으로 과세 여부를
  단정할 수는 없다(**저장된 추정가격의 부가세 포함 여부가 이력상 일관되지 않다**)"*라고
  적는다. **legacy가 스스로 공시한다** — 따라서 차이의 크기도 방향도 legacy 기록으로
  판정되지 않는다.

**두 인용이 서로를 강화한다**: 하나는 선언이 ex-VAT가 아님을, 다른 하나는 **그 선언조차
저장된 값에 일관되게 적용되지 않았음**을 보인다.

> **조달청 문서와의 관계 — 이것이 이 축이 열려 있는 이유다.** `capability-map.md` §3이
> 인용하는 조달청 문서는 `추정가격`을 *"부가가치세 및 조달 수수료를 **제외한** 금액"*으로
> 정의한다. **legacy 컬럼 주석은 그 반대**이고 legacy 자신이 저장 값의 일관성 결여를
> 공시한다. **어느 쪽이 맞다고 이 slice가 정하지 않는다** — 그 판정이 활성
> `OPEN-REG-05`의 내용이다. 이 slice가 지우는 것은 **정량 서술**이지 조달청 인용이 아니다.

---

## C-2. 범위 — in_scope 준수와 `capability-map.md` 변경의 위치

```
### 실행 시점 HEAD = 03d7240

--- C-2.1 ---
$ git diff --name-only 48151b9...HEAD
docs/discovery/capability-map.md
reports/evidence/m0/0a2/checklist.md
reports/evidence/m0/0a2/decisions.md

--- C-2.2 ---
$ git diff --numstat 48151b9...HEAD -- docs/discovery/capability-map.md
1	1	docs/discovery/capability-map.md

--- C-2.3 ---
$ git diff -U0 48151b9...HEAD -- docs/discovery/capability-map.md | grep -E '^@@'
@@ -3171 +3171 @@ milestone-0.md 완료 조건은 "`OPEN` 결정이 0개이거나 사용자가 명
$ grep -n '^## 1[23]\. ' docs/discovery/capability-map.md
2910:## 12. OPEN 결정 목록
3166:## 13. 하류 인계

--- C-2.4 ---
$ git diff --check 48151b9...HEAD ; echo "exit=$?"
exit=0

--- C-2.5 ---
$ git diff 48151b9...HEAD -- docs/discovery/capability-map.md | grep -cE '^\+.*\| OPEN-[A-Z]+-[0-9]+ \|'
0
```

**판정**:

- `git diff --name-only`가 in_scope 세 파일만 낸다(`reports/evidence/m0/0a3/`는 이 커밋에
  들어간다).
- `capability-map.md`는 **한 줄 교체**이고 hunk가 **`3171` 하나**다. §13이 `3166`에서
  시작하고 §12가 `2910`에서 시작하므로 **그 한 줄은 §13 안**이다 — **A4의 「X-1 외 서술
  무변경」이 구조로 성립한다.** §10 집계표·§12 registry는 diff에 없다.
- `git diff --check` **exit 0**.
- **registry에 추가된 `OPEN` 행 0** — 새 `OPEN`을 만들지 않았다(A3).

---

## C-3. X-1 프레이밍 스윕 — 정정이 어디까지 갔고 어디서 멈췄나

```
### 실행 시점 HEAD = 03d7240

--- C-3.1 X-1 프레이밍 스윕 (in_scope 두 파일) ---
$ grep -nE 'ex-VAT|VAT·사정률만큼' docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md | cut -c1-140
reports/evidence/m0/0a2/decisions.md:40:| 「OPEN-STR-01」 절 안의 2026-08-27 정정 블록(**slice 0A3 · X-1**) | **slice 산물** — legacy **사실 서술 정정**(`bud
reports/evidence/m0/0a2/decisions.md:59:| `144` · `147` | 「OPEN-STR-01」 | **정정** — **X-1**: `budget_estimate`의 ex-VAT 단정과 차이의 크기·방향 서술(취소선 +
reports/evidence/m0/0a2/decisions.md:144:    `strategy.min|max_budget_estimate`. `Project.budget_estimate`는 ~~추정가격(ex-VAT)이다~~
reports/evidence/m0/0a2/decisions.md:147:    ~~기초금액과 추정가격은 VAT·사정률만큼 체계적으로 다르므로 임의 오차가 아니라
reports/evidence/m0/0a2/decisions.md:152:    > - **`budget_estimate`는 ex-VAT가 아니다.** legacy는 그 반대로 선언한다 —
reports/evidence/m0/0a2/decisions.md:155:    > - **차이가 "VAT·사정률만큼"이라고 말할 근거가 없다.** legacy 자신이 저장된 값의
reports/evidence/m0/0a2/decisions.md:173:    >   "fix(m0-0b): budget_estimate의 ex-VAT 단정을 제거 (0C 선행 조사 X-1)"에서 정정했고,
docs/discovery/capability-map.md:1373:- **분류 근거**: 투찰율이 곱해지는 base는 추정가격(ex-VAT)이 아니라 기초금액/사업금액
docs/discovery/capability-map.md:3171:| 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값을 `Project.budget_estimate`(**

--- C-3.2 취소선 안인가 (원본 보존 확인) ---
$ sed -n '141,149p' reports/evidence/m0/0a2/decisions.md
  - legacy는 운영자 값을 **추정가격**과 비교한다:
    `app/services/opportunity_monitoring/filters.py`
    `project_budget = float(project.budget_estimate or 0.0)` ↔
    `strategy.min|max_budget_estimate`. `Project.budget_estimate`는 ~~추정가격(ex-VAT)이다~~
    (strategy 노트 OPEN-1).
  - 운영자 의도가 기초금액이므로 **legacy는 운영자가 지정하지 않은 basis로 필터한다.**
    ~~기초금액과 추정가격은 VAT·사정률만큼 체계적으로 다르므로 임의 오차가 아니라
    경계 근처 공고를 **한 방향으로 일관되게** 누락/포함시킨다.~~
    > **2026-08-27 정정 (slice 0A3 — 0C 선행 조사 X-1) — legacy 사실 서술을 실측으로 교체.**

--- C-3.3 OPEN-REG-05 귀속 ---
$ grep -c 'OPEN-REG-05' docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md
reports/evidence/m0/0a2/decisions.md:3
docs/discovery/capability-map.md:1
$ grep -n 'OPEN-QUAL-10.*소유자가 아니' docs/discovery/regression-ledger.md docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md | cut -c1-120
docs/discovery/capability-map.md:3171:| 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값을 `Project
docs/discovery/regression-ledger.md:82:    `OPEN-QUAL-10`은 **시공능력평가금액 축**이라 이 질문의 소유자가 아니다.
docs/discovery/regression-ledger.md:1345:| **OPEN-REG-05** | **기초금액과 추정가격의 과세 처리 — 두 금액의 차이가 무엇으로 이루어지는가** | legacy는 `bu

--- C-3.4 §13 밖에 남은 X-1 프레이밍 (O-2, 고치지 않음) ---
$ grep -nE '추정가격.{0,12}(ex-VAT|부가세 별도)' docs/discovery/capability-map.md
1373:- **분류 근거**: 투찰율이 곱해지는 base는 추정가격(ex-VAT)이 아니라 기초금액/사업금액
1929:  - 기초금액(사업금액)과 추정가격(부가세 별도)을 **다른 행**으로 싣는다. 한 라벨로 묶지
$ for L in 1373 1929; do awk -v L=$L 'NR<=L && /^## /{h=$0} NR==L{print L" → "h}' docs/discovery/capability-map.md; done
1373 → ## 5. 축 5 — 투찰 판단과 근거 (DEC)
1929 → ## 6. 축 6 — 알림 / 보고서 (NOTI)

--- C-3.5 decisions.md 전수 표 ↔ 훅 1:1 ---
$ git diff -U0 6a4e49b -- reports/evidence/m0/0a2/decisions.md | grep -E '^@@'
@@ -4 +4 @@
@@ -6,3 +6,12 @@
@@ -12,2 +21,4 @@
@@ -15,0 +27,50 @@
@@ -83 +144 @@
@@ -86,2 +147,30 @@
@@ -91,0 +181,9 @@
@@ -174 +272 @@
@@ -189 +287 @@
@@ -191 +289,15 @@
@@ -265,2 +377,9 @@
@@ -295,0 +415,113 @@
$ sed -n '55,64p' reports/evidence/m0/0a2/decisions.md | cut -c1-46
| 훅(신규 줄) | 위치 | 성격 |
| --- | --- | --- |
| `4` · `6` · `21` | **파일 머리** | slice가 쓴 메타(원
| `27` | **「이 파일의 구성 — provenance」 신설** | slic
| `144` · `147` | 「OPEN-STR-01」 | **정정** — **X
| `181` | 「OPEN-STR-01」 | **정정** — `R-07` 식별자(
| `272` | 「M1 차단 묶음」의 `OPEN-QUAL-08` 표 행 | **포
| `287` · `289` | 「OPEN-DEC-09」 | **정정** — leg
| `377` | 「상호작용 모델」 절의 하류 파급 | **철회** — 21,321
| `415` | **말미 새 절 전부** | slice 절 추가(2026-08-2
```

**판정**:

- **C-3.1** — in_scope 두 파일에 남은 매치는 전부 ① **취소선 안**(`:144`·`:147`,
  원본 보존) ② **정정 블록이 무엇을 고쳤는지 설명하는 산문**(`:152`·`:155`·`:173`)
  ③ **provenance 표가 이 정정을 등재한 행**(`:40`·`:59`) ④ **§13 정정문이 원문을
  인용한 자리**(`:3171`)다. **살아 있는 단정으로 남은 것은 `capability-map.md:1373`
  하나이고 그것은 §13 밖이다**(C-3.4 · `scope.md` **O-2**).
- **C-3.2** — 원본 두 문장이 **지워지지 않고 취소선으로 보존**됐고 바로 아래 정정 인용
  블록이 붙었다. `decisions.md`의 provenance 원칙(*"원본 문장은 지우지 않는다"*)을
  지킨다(**A2**).
- **C-3.3** — `OPEN-REG-05` 귀속이 두 파일에 적혔고, `OPEN-QUAL-10`이 소유자가 아니라는
  서술이 **ledger와 §13 양쪽에** 있다. **두 문서가 같은 말을 한다** — 0B verifier F-2가
  적출한 자기모순(*"한 자리에서 소유한다, 다른 자리에서 소유자가 아니다"*)이 재발하지
  않는다.
- **C-3.4** — **§5 DEC-01과 §6 NOTI 보고서 계약에 살아 있는 X-1 프레이밍이 남아 있다.**
  팀 리드 지시의 *"두 파일에 남아 있다"*가 **완결 주장으로는 참이 아니다.**
  **고치지 않았다** — in_scope가 *"§13 X-1 서술 한정"*이라 건드리면 C-2가 낸 구조적
  불변이 깨진다. `scope.md` **O-2**로 등재했다.
- **C-3.5** — `git diff -U0 6a4e49b`의 훅 열두 개와 전수 표의 행이 **1:1로 대응**한다
  (표는 인접 훅을 한 행으로 묶는 자리가 둘 있다 — `4`·`6`·`21`과 `287`·`289`, 그리고
  이 slice가 더한 `144`·`147`). **A2 성립.**

> **이 slice의 편집이 뒤따르는 훅을 밀었다.** `144`·`147`이 새로 들어오면서 `181`(R-07)
> 이하가 전부 이동했고 표의 값을 그에 맞췄다. **이것이 인스턴스 고치기라는 것을 안다** —
> 다음 편집에 다시 낡는다. 표의 설계(훅 번호 ↔ 행)를 바꾸는 것은 이 slice의 위임 범위
> 밖이라 **번호는 유지하고 「정본은 명령의 출력」임을 표 아래에 명시**했으며,
> **셈(*"네 자리뿐"*)만 지웠다**(0B verifier `N-1` 처방). `scope.md` **O-3**.

---

## C-4. A4 불변 — 정본을 산출물에서 직접 계산한다

**스크립트 본문(형태 4 — 이름으로만 부르지 않는다).** 산출 정의는 0A2 `commands.md`
R9-1 수치 축 스윕의 「정본 산출」 절과 **같다** — 새 정의를 만들지 않았다.

```python
# 0A3 A4 불변 — 정본을 capability-map.md 에서 직접 계산한다.
# 산출 로직은 0A2 commands.md R9-1 수치 축 스윕의 「정본 산출」 절과 같은 정의다.
import re, collections
CM = "docs/discovery/capability-map.md"
lines = open(CM, encoding="utf-8").read().split("\n")
txt = "\n".join(lines)

starts = [i for i, l in enumerate(lines) if l.startswith("### ")]
CLS = re.compile(r'^\s*- \*\*분류\*\*:\s*\*?\*?`([^`]+)`')
cnt = collections.Counter()
for n, st in enumerate(starts):
    e = starts[n + 1] if n + 1 < len(starts) else len(lines)
    m = [x for x in (CLS.match(l) for l in lines[st:e]) if x]
    if not m:
        continue
    cnt[m[0].group(1)] += 1
ORDER = ["V2 필수", "후속", "폐기", "근거 부족"]
CLS4 = tuple(cnt[k] for k in ORDER)
CAP = sum(cnt.values())
reg = sorted(set(re.findall(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|', txt, re.M)))

print("capability 총수      :", CAP)
print("분류 4종 (필수/후속/폐기/근거부족):", "/".join(str(x) for x in CLS4))
print("활성 OPEN 총수       :", len(reg))
print("capability-map 줄 수 :", len(lines) - (1 if lines and lines[-1] == "" else 0))
```

**base와 HEAD 양쪽에서 돌렸다** — 값을 비교하는 것이 불변의 증명이지 한쪽 값만 적는 것은
아니다.

```
### 실행 시점 HEAD = 03d7240

--- C-4.1 HEAD ---
$ python3 inv0a3.py     # 본문은 이 절에 인라인
capability 총수      : 95
분류 4종 (필수/후속/폐기/근거부족): 61/17/6/11
활성 OPEN 총수       : 45
capability-map 줄 수 : 3184

--- C-4.2 base 48151b9 (같은 스크립트) ---
$ TMPD=$(mktemp -d); git archive 48151b9 docs/discovery/capability-map.md | tar -x -C "$TMPD"; (cd "$TMPD" && python3 inv0a3.py)
capability 총수      : 95
분류 4종 (필수/후속/폐기/근거부족): 61/17/6/11
활성 OPEN 총수       : 45
capability-map 줄 수 : 3184

--- C-5.1 §10.1 형태 표 행 ---
$ grep -nE '^\| \*{0,2}[0-9]' reports/evidence/m0/0a2/checklist.md | sed -n '1,8p' | cut -c1-62 | sed 's/[[:space:]]*$//'
276:| 1 | **확인하지 않고 기재** | 0A H-1 — F1의 "수정 전 중첩 5" 재현 주장을 실행
277:| 2 | **확인하지 않고 승격** — 넓게는 **legacy를 열지 않고 legacy를 인용하기**
278:| 3 | **실행하지 않고 붙여넣기** | 0A2 라운드 2 M-2r — 명령 출력이 내용은 정확하나
279:| 3′ | **재실행 범위를 전칭으로 적기** (3번의 반대 형태) | 0A2 라운드 3 M-4r —
280:| 4 | **재실행 불가능한 참조** | 0A2 라운드 4 Codex residual risk — ev
281:| 5 | **정정을 인용 지점에 전파하지 않기** | 0A2 라운드 5 Codex medium #6 —
287:| **6** | **셈으로 전칭을 주장하기** | 0A2 라운드 7 완결 — `decisions.md`
308:| **7** | **미결을 확정으로 쓰기** — 활성 `OPEN`이 소유한 쟁점을 산출물이 **확정 서

--- C-5.2 형태 7 절 ---
$ grep -n '형태 7 — 미결을 확정으로 쓰기\|왜 새 형태인가\|등재가 0A3로' reports/evidence/m0/0a2/checklist.md | cut -c1-70 | sed 's/[[:space:]]*$//'
289:**왜 새 형태인가 — 기존 형태로는 못 막는다.** 지금까지 두 번은 새 형태를 만들지 않는
303:**형태 7 — 미결을 확정으로 쓰기** (2026-08-27 신설, slice **0A3**이 등재. 근거를 세운 것
310:**왜 새 형태인가 — 기존 여섯을 다 지켜도 난다.** 근거는 `reports/evidence/m0/0b/comman
330:**등재가 0A3로 미뤄진 이유**: §10.1은 이 파일에 있고 **0B의 out_of_scope**였다.

--- C-5.3 형태 7이 인용하는 0B evidence 자리 (직접 열어 확인) ---
$ grep -n '^### C-8.1 \|^### C-8.2 \|^### C-8.5 \|^### C-9.9 ' reports/evidence/m0/0b/commands.md
1056:### C-8.1 high — 계열 A. 계약이 건 여섯 형태에 없던 축이다
1073:### C-8.2 계열 A 전수 훑기 — 축을 하나 더 만들었다
1502:### C-8.5 §10.1 판단 — 계열 A는 **일곱 번째 형태로 추가할 만하다**(단 그 파일은 범위 밖)
2205:### C-9.9 M-1 — §10 인계 자리는 계열 A 축의 **구조적 사각지대**다 (미기록이었다)
$ grep -n '^### F-1 (high)\|^- \*\*F-2\*\*: `R-BASIS-01`' reports/evidence/m0/0b/scope.md | cut -c1-100 | sed 's/[[:space:]]*$//'
611:### F-1 (high) — 계열 A 실물이 하나 더. 축이 구조적으로 못 봤다
635:- **F-2**: `R-BASIS-01`이 기초금액↔추정가격의 **차이 크기**를 `OPEN-QUAL-10` 소유로

--- C-5.4 0B가 §10.1 편집을 미룬 문장 (C-8.5 말미) ---
$ sed -n '1519,1521p' reports/evidence/m0/0b/commands.md
이 slice는 **판단만 기록하고 그 파일을 편집하지 않는다** — 표를 고치려면 별도 slice 계약이
필요하다. **0B는 그 사이 계열 A 축(C-8.2)을 자기 evidence에 두고 돌린다.**
```

**판정**:

- **C-4.1 ↔ C-4.2** — base `48151b9`와 HEAD의 네 값이 **모두 같다.** capability 총수 ·
  분류 4종 · 활성 OPEN 총수 · 줄 수 전부 불변이다(**A4**). 줄 수까지 같은 것은 §13
  변경이 **한 줄 교체**였기 때문이며 C-2.2의 `1 1`이 그것을 낸다.
- **C-5.1** — §10.1 형태 표에 **`7` 행이 들어갔고** 기존 행(`1`~`6`, `3′`)은 그대로다.
  `399`~`401`은 형태 1·2·3의 **결론/근거/처리 3열 표**로 다른 표다.
- **C-5.2** — 형태 7 절과 그 근거 절(*"왜 새 형태인가 — 기존 여섯을 다 지켜도 난다"*),
  등재가 0A3로 미뤄진 이유가 자리에 있다. **절 머리에 표의 마지막 번호를 박지 않았다**
  (0B `N-1`).
- **C-5.3 · C-5.4** — 형태 7이 인용하는 0B evidence 자리가 **전부 실재하고 직접 열어
  확인했다**: `C-8.1`(Codex 1차 high · `R-COL-02`) · `C-8.2`(계열 A 축 = `openstance`) ·
  `C-8.5`(일곱 번째 형태 판단과 **"별도 slice 계약이 필요하다"**는 문장) ·
  `C-9.9`(축의 구조적 사각지대) · `scope.md`의 `F-1`(`OPEN-DEC-07`) ·
  `F-2`(`R-BASIS-01` 귀속). **A5 성립.**

---

## C-6. 이 slice가 만들지 않은 것

| 안 만든 것 | 이유 |
| --- | --- |
| 새 스윕 축·스크립트 | 이 slice에서 그 부류의 결함이 **관측되지 않았다.** 0B가 세운 순서(결함이 먼저, 도구가 나중)를 지킨다 |
| 새 `OPEN` | 소유자가 **이미 등록돼 있다**(`OPEN-REG-05`). C-2.5가 신설 0을 낸다 |
| `OPEN` 해소 | 활성 45건 전부 out_of_scope. C-4가 총수 불변을 낸다 |
| `capability-map.md` §13 밖 편집 | in_scope 선언이 §13 한정. 발견한 것은 `scope.md` O-1·O-2로 등재 |
| 0B evidence 편집 | 0B는 Codex `approve`로 닫혔다. **인용만 하고 읽기 전용으로 다뤘다** |
