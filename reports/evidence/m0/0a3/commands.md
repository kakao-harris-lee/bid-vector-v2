# M0 / 0A3 — 검증 명령과 실행 출력

**range**: `48151b9...<head>`. **출력 블록은 각자 자기 실행 시점 HEAD를 첫 줄에 선언한다** —
한 값을 머리에 박으면 수정 라운드마다 낡는다(0B `N-1`). 접거나 손으로 압축한 블록은 없다
(§10.1 **형태 3**). **뒤 커밋에 낡지 않는 형태로 짠 블록**은 legacy 고정 commit
읽기(C-1) · `capability-map.md`로 경로 한정(C-2) · 이 라운드 이후 바뀌지 않는 파일의
grep(C-3·C-5) · 산출물에서 정본을 계산하는 스크립트(C-3.4·C-4)다.
**`C-7`은 그 부류가 아니다** — **검사 대상이 라운드마다 바뀌므로 그 블록은 편집이 생기면
낡고, 그때 다시 뜬다.** **「기록된 블록이 전부 낡지 않는다」고 적지 않는다**
(verifier `W-2`). **range 전체를 보는 검사는
출력을 적지 않고 명령만 남긴다**(C-2의 첫 표). 셈은 이 절의 출력이 내고 **산문으로
옮겨 적지 않는다**(**형태 6**).

### 이 패키지가 쓰는 스크립트 — 각각 어디에 있고 왜 있는지

**전부 본문을 이 파일에 인라인해 두고, 실행할 때 그 본문을 marker로 뽑아 stdin 으로
흘려 넣는다** — **커밋되지 않은 스크립트 파일에 의존하지 않는다**(**형태 4**).

**수를 산문에 옮겨 적지 않는다.** 이 표가 몇 줄인지는 **아래 명령이 낸다** — 표는
**그 출력과 1:1로 대조되는 것**이고, 스크립트가 늘면 **행을 더한다.**
**앞서 이 자리에 「둘」이라고 셈을 박아 두었다가 셋이 되면서 거짓이 됐다**(Codex 2차
`M-3`에서 한 번, Codex 4차 `B`에서 다시). **셈을 지우고 대상이 내게 한다.**

```
grep -n '^```python' reports/evidence/m0/0a3/commands.md
```

| 스크립트 | 어디 | 무엇을 하나 | 왜 있나 (도입 근거) |
| --- | --- | --- | --- |
| **불변 계산기** | **C-4** | `capability-map.md`에서 capability 총수·분류 4종·활성 OPEN 총수·줄 수를 **직접 계산**한다 | **새 도구가 아니다** — 0A2 수치 축의 「정본 산출」 절을 그대로 떼어낸 것이고 **A4의 불변을 base와 HEAD 양쪽에서 재는 데** 쓴다 |
| **X-1 프레이밍 후보 스윕** | **C-3.4** | `capability-map.md` 전체에서 X-1 프레이밍 후보를 **블록 단위**로 찍는다(어휘 5종) | **결함이 관측된 뒤에 넓혔다** — verifier **`P-1`**이 *"살아 있는 단정이 하나"*를 반증했고, 그 전 축이 **어휘 하나·줄 단위**라 `부가세 별도`와 **여러 줄에 걸친 자리**를 못 봤다. 0B가 세운 순서(**결함이 먼저, 도구가 나중**)를 지킨 것이다 |
| **포인터 대조** | **C-7 (b)** | `scope.md`·`checklist.md`가 이 파일의 절을 부르는 이름이 **실재하는지**, **번호가 절을 넘겨준 뒤에 낡지 않았는지**(`git blame`) 본다 | **결함이 관측된 뒤에 넓혔다** — verifier **`W-1`**이 개명으로 끊긴 포인터를 냈고, **`Y-1`**이 그때 만든 좁은 검사가 **그 자리를 구조적으로 못 본다**는 것을 지적했다. **넓힌 뒤 두 커밋에서 능력을 실행으로 쟀다**(C-7 (b)) |

**0B의 축을 그대로 가져오지 않았다.** 이 slice에 필요한 것만 두었다 — legacy 인용
확인(C-1), 범위·불변(C-2·C-4), X-1 프레이밍 스윕(C-3), 형태 7 등재 확인(C-5).

---

## C-1. legacy 인용 재확인 — 직접 열었다 (§10.1 형태 2)

**X-1의 근거 두 자리를 `git show ed4b06c:`로 직접 열었다.** 상류 노트(0C 선행 조사)의
인용을 그대로 옮기지 않았다. **행 범위는 블록 경계까지 뜬다** — `bid_summary.py`의
`Field(` 호출은 **`:69`에서 시작해 `:77`에서 닫힌다**(그 앞뒤 한 줄을 함께 떠서
`:68`이 이전 필드의 닫는 괄호, `:78`이 다음 필드임을 확인했다). 0B가 라운드 5 `L-1`에서
`:70-77`로 한 줄 늦게 적었다가 고친 그 자리다.

```
### 실행 시점 HEAD = 74132ea
### 이 블록의 출력은 저장소 HEAD와 무관하다 — legacy 저장소의 고정 commit 을 읽는다.

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

### 목록을 옮겨 적지 않는 것 두 가지 — 명령만 남긴다

**range 전체를 보는 검사는 evidence 커밋이 자기 파일을 range에 더하므로, 커밋 전에 뜬
출력은 언제나 불완전하다.** 앞 라운드가 그 자리에서 걸렸다(Codex 1차 medium) — 기록된
목록이 세 경로였고 리뷰 head에서는 여섯이었다. **인스턴스를 고치면 다음 커밋에 다시
낡는다.** 그래서 **목록도 셈도 옮겨 적지 않고 명령만 남긴다** — 0B가 같은 부류에 네 번
걸린 뒤 세운 처방이다.

| 확인할 것 | 명령 | 판정 문면 |
| --- | --- | --- |
| in_scope 밖 경로가 없다 | `git diff --name-only 48151b9...<head>` | **목록 없이** — 나오는 경로가 전부 `scope.md`의 `in_scope` 아래에 있는가 |
| 공백 오류가 없다 | `git diff --check 48151b9...<head>` | 출력이 없다 |

**결과를 여기 적지 않는다.** 두 명령은 **리뷰 head에서 리뷰어가 직접 돌린다** — 그것이
정본이고, 이 파일이 옮겨 적은 값은 정본이 될 수 없다.

### 경로를 한정해 안정한 검사 — 출력을 기록한다

```
### 실행 시점 HEAD = 74132ea
### 아래 셋은 전부 `-- docs/discovery/capability-map.md` 로 경로를 한정한다 —
### 그래서 이 파일을 건드리지 않는 뒤 커밋이 붙어도 출력이 바뀌지 않는다.

--- C-2.1 ---
$ git diff --numstat 48151b9...HEAD -- docs/discovery/capability-map.md
1	1	docs/discovery/capability-map.md

--- C-2.2 ---
$ git diff -U0 48151b9...HEAD -- docs/discovery/capability-map.md | grep -E '^@@'
@@ -3171 +3171 @@ milestone-0.md 완료 조건은 "`OPEN` 결정이 0개이거나 사용자가 명
$ grep -n '^## 1[23]\. ' docs/discovery/capability-map.md
2910:## 12. OPEN 결정 목록
3166:## 13. 하류 인계
$ grep -n '^| 0B — 예산 basis 불일치' docs/discovery/capability-map.md | cut -c1-46
3171:| 0B — 예산 basis 불일치 (`legacy-defect`) — *

--- C-2.3 ---
$ git diff 48151b9...HEAD -- docs/discovery/capability-map.md | grep -cE '^\+.*\| OPEN-[A-Z]+-[0-9]+ \|'
0
```

**판정**:

- **변경 위치가 §13 안이다** — 위 hunk 지목이 §13 시작 줄보다 뒤이고 §12 시작 줄보다도
  뒤다. **§10 집계표·§12 registry는 diff에 없고**, §13 안에서도 **「0B — 예산 basis
  불일치」 인계 행 하나**가 대상이다(그 행은 마크다운 표의 한 줄이라 hunk가 그 줄을
  지목한다). **A4의 「그 인계 행 밖 무변경」이 구조로 성립한다.**
  **셈을 여기 옮겨 적지 않는다** — 지목도 위치도 위 블록이 낸다.
- **registry에 추가된 `OPEN` 행 0** — 새 `OPEN`을 만들지 않았다(A3).
- **이 셋은 다른 파일을 바꾸는 커밋에 낡지 않는다** — 경로가
  `-- docs/discovery/capability-map.md`로 **한정돼 있기 때문**이다. **근거는 경로 한정
  하나이고 커밋 관계가 아니다** — 이 블록은 자기가 실린 커밋에 대해 주장하지 않는다.
  이 파일이 다시 바뀌면 **그 라운드가 이 블록을 다시 뜬다.**

---

## C-3. X-1 프레이밍 — 두 축이 보는 범위

- **C-3.1**은 **`decisions.md` 전체**와 **`capability-map.md` §13**을 본다.
- **C-3.4**는 **`capability-map.md` 전체**를 본다(out_of_scope · `scope.md` **O-2**).

**두 범위는 포개져 있다** — **§13 ⊂ `capability-map.md` 전체**이므로 **§13은 두 축이 모두
출력하고 각자 판정한다**(C-3.1의 분류 **④**와 C-3.4 판정 표의 **§13 행**이 그 자리다).
**오늘 두 판정이 어긋나지 않지만 그것이 구성으로 보장되지는 않는다.**

**이 라운드가 한 것은 C-3.1의 범위를 A1 쪽으로 좁힌 것**이다 — 앞서 C-3.1이
`capability-map.md` **전체**를 grep해 **§5의 out_of_scope 매치가 그 출력에 섞였고**,
그것이 Codex 2차 `M-2`였다. **지금은 섞이지 않는다.**

**§6·§7은 이 축의 발견이 아니다.** C-3.1의 패턴(`ex-VAT|VAT·사정률만큼`)에 **걸리지
않는다** — `부가세 별도`·`과세 공고`는 다른 어휘다. **그 두 자리는 C-3.4의 5어휘 블록
스윕이 냈고 그것은 `P-1` 라운드의 발견**이다. **두 축의 발견을 합치지 않는다.**

```
### 실행 시점 HEAD = a264d6f

--- C-3.1 X-1 단정 스윕 — decisions.md 전체 + capability-map.md §13 ---
### A1 의 범위(OPEN-STR-01 절 + §13)를 포함하는 상위집합이다 —
### decisions.md 는 절로 자르지 않는다. §13 은 절 머리로 잘라낸다(내용으로 집는다).
$ grep -nE 'ex-VAT|VAT·사정률만큼' reports/evidence/m0/0a2/decisions.md | cut -c1-125 | sed 's/[[:space:]]*$//'
40:| 「OPEN-STR-01」 절 안의 2026-08-27 정정 블록(**slice 0A3 · X-1**) | **slice 산물** — legacy **사실 서술 정정**(`budget_estimate`의 ex-VAT
59:| `144` · `147` | 「OPEN-STR-01」 | **정정** — **X-1**: `budget_estimate`의 ex-VAT 단정과 차이의 크기·방향 서술(취소선 + 인용 블록, 원본 문장 보존). sli
144:    `strategy.min|max_budget_estimate`. `Project.budget_estimate`는 ~~추정가격(ex-VAT)이다~~
147:    ~~기초금액과 추정가격은 VAT·사정률만큼 체계적으로 다르므로 임의 오차가 아니라
152:    > - ~~**`budget_estimate`는 ex-VAT가 아니다.** legacy는 그 반대로 선언한다 —~~
157:    >   ex-VAT라고도 VAT 포함이라고도 단정하지 않으며**, 그 판정은 활성 **`OPEN-REG-05`**가
158:    >   소유한다. 원본에 대해 남는 것은 **「ex-VAT」라는 단정에 근거가 없다**는 것까지다.
168:    > - **차이가 "VAT·사정률만큼"이라고 말할 근거가 없다.** legacy 자신이 저장된 값의
186:    >   "fix(m0-0b): budget_estimate의 ex-VAT 단정을 제거 (0C 선행 조사 X-1)"에서 정정했고,
$ grep -n '^### OPEN-STR-01\|^### OPEN-OPS-03' reports/evidence/m0/0a2/decisions.md | cut -c1-58 | sed 's/[[:space:]]*$//'   # 절 경계 — 위 매치 일부가 이 밖이다
135:### OPEN-STR-01 · 운영자 예산 필터가 비교하는 금액의 basis — **결정: (c
211:### OPEN-OPS-03 · 큐 깊이 SLO — **부분 확정**
$ awk '/^## 13\. /,0' docs/discovery/capability-map.md | grep -E 'ex-VAT|VAT·사정률만큼' | cut -c1-125 | sed 's/[[:space:]]*$//'
| 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값을 `Project.budget_estimate`(**추정가격**)와 비교하는 형태가 legac
$ grep -n '^## 1[23]\. ' docs/discovery/capability-map.md   # §13 이 파일 끝까지임을 보인다
2910:## 12. OPEN 결정 목록
3166:## 13. 하류 인계

--- C-3.2 원본 보존 확인 (줄 번호가 아니라 내용으로 집는다) ---
$ awk '/^  - legacy는 운영자 값을 \*\*추정가격\*\*과 비교한다:/,/^    >   > \*\*2차 정정 /' reports/evidence/m0/0a2/decisions.md
  - legacy는 운영자 값을 **추정가격**과 비교한다:
    `app/services/opportunity_monitoring/filters.py`
    `project_budget = float(project.budget_estimate or 0.0)` ↔
    `strategy.min|max_budget_estimate`. `Project.budget_estimate`는 ~~추정가격(ex-VAT)이다~~
    (strategy 노트 OPEN-1).
  - 운영자 의도가 기초금액이므로 **legacy는 운영자가 지정하지 않은 basis로 필터한다.**
    ~~기초금액과 추정가격은 VAT·사정률만큼 체계적으로 다르므로 임의 오차가 아니라
    경계 근처 공고를 **한 방향으로 일관되게** 누락/포함시킨다.~~
    > **2026-08-27 정정 (slice 0A3 — 0C 선행 조사 X-1) — legacy 사실 서술을 실측으로 교체.**
    > 위 취소선 두 곳은 **slice가 고친 legacy 사실 서술**이며 원본 문장은 감사 추적을 위해
    > 남긴다. 확인은 `git show ed4b06c:`로 두 파일을 직접 열어서 했다.
    > - ~~**`budget_estimate`는 ex-VAT가 아니다.** legacy는 그 반대로 선언한다 —~~
    >   `app/domain/money.py:30`의 `Basis` enum 주석이
    >   `BUDGET_ESTIMATE = "budget_estimate"  # 추정가격: 부가세 포함 추정 총액(법정 하한 구간 기준)`이다.
    >   **legacy가 그렇게 「선언」한다는 것이 이 인용이 지지하는 전부다** — 저장된 값의 실제
    >   과세 처리가 그 선언과 같은지는 **다음 항목이 반대로 적는다.** 따라서 **이 문서는
    >   ex-VAT라고도 VAT 포함이라고도 단정하지 않으며**, 그 판정은 활성 **`OPEN-REG-05`**가
    >   소유한다. 원본에 대해 남는 것은 **「ex-VAT」라는 단정에 근거가 없다**는 것까지다.
    >   > **2차 정정 (Codex 1차 high · 2026-08-27) — 이 slice가 만든 반대 방향 단정을 좁혔다.**

--- C-3.3 OPEN-REG-05 귀속 ---
### 여러 파일을 인자로 주는 grep 의 파일 간 출력 순서는 구현·환경에 따라 다르다.
### 앞 라운드의 기록은 그 순서에 의존했다. sort 로 순서를 고정한다.
$ grep -c 'OPEN-REG-05' docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md | sort
docs/discovery/capability-map.md:1
reports/evidence/m0/0a2/decisions.md:4
$ grep -n 'OPEN-QUAL-10.*소유자가 아니' docs/discovery/regression-ledger.md docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md | sort | cut -c1-110 | sed 's/[[:space:]]*$//'
docs/discovery/capability-map.md:3171:| 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값
docs/discovery/regression-ledger.md:1345:| **OPEN-REG-05** | **기초금액과 추정가격의 과세 처리 — 두 금액의 차이가 무엇으로 이루어지는가** | l
docs/discovery/regression-ledger.md:82:    `OPEN-QUAL-10`은 **시공능력평가금액 축**이라 이 질문의 소유자가 아니다.

--- C-3.4 X-1 프레이밍 후보 스윕 (capability-map.md 전체 · O-2, out_of_scope) ---
$ BLK4=$(python3 - <<'EOF'
import re
F = chr(96) * 3                     # 리터럴 백틱 세 개를 이 파일에 넣지 않는다
s = open("reports/evidence/m0/0a3/commands.md", encoding="utf-8").read()
b = [m.group(1) for m in re.finditer(F + r"python\n(.*?)" + F, s, re.S)
     if "X-1 프레이밍 후보 스윕" in m.group(1)]
assert len(b) == 1, "marker 블록이 유일하지 않다: %d" % len(b)
print(b[0], end="")
EOF
)
$ printf %s "$BLK4" | python3 -
[ 3.] :978  - **분류**: `V2 필수` — **분할 확정** (운영자 결정 2026-08-26), `OPEN-QUAL-08` 해소. **시공능력평가액은 자격 축**(공고가 게시하는 요건), **
[ 5.] :1373  - **분류 근거**: 투찰율이 곱해지는 base는 추정가격(ex-VAT)이 아니라 기초금액/사업금액 (과세 공고면 VAT 포함)이며, 해석 결과가 금액만이 아니라 **출처 라벨과 비교
[ 6.] :1929  - 기초금액(사업금액)과 추정가격(부가세 별도)을 **다른 행**으로 싣는다. 한 라벨로 묶지 않는다.
[ 6.] :1931  - 투찰률은 **낙찰하한율과 basis가 같은 기초금액 기준 율만** 싣는다. 추정가격 기준 율을 하한 옆에 두면 **과세 공고에서 하한 여유가 부풀어 보인다**(관찰된 회귀).
[ 6.] :1938  - 과세 공고 보고서에서 기초금액과 추정가격이 서로 다른 행으로 표시된다.
[ 7.] :2187  | F-4 | 율 결측 시 폴백 분모를 추정가격으로 잡으면 과세 공고에서 약 10% 어긋난다 | 금액에 basis를 타입으로 붙인다(DEC-01) |
[12.] :2998  | OPEN-QUAL-10 | **시공능력평가금액 비교에 쓰는 두 금액의 unit·basis·과세 처리·기준 시점** — 공고 게시 요건(`cnstrtnAbltyEvlAmtList`)과
[12.] :3088  | `OPEN-QUAL-10` (신설) | 라운드 1이 시공능력평가금액 요건과 운영자 보유액의 **직접 비교를 확정**했는데 그 두 금액의 **unit·basis·과세 처리·기준 시점**
[13.] :3171  | 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값을 `Project.budget_estimate`(**추정

--- C-3.5 decisions.md 전수 표 ↔ 훅 1:1 ---
$ git diff -U0 6a4e49b -- reports/evidence/m0/0a2/decisions.md | grep -E '^@@'
@@ -4 +4 @@
@@ -6,3 +6,12 @@
@@ -12,2 +21,4 @@
@@ -15,0 +27,50 @@
@@ -83 +144 @@
@@ -86,2 +147,43 @@
@@ -91,0 +194,9 @@
@@ -174 +285 @@
@@ -189 +300 @@
@@ -191 +302,15 @@
@@ -265,2 +390,9 @@
@@ -295,0 +428,113 @@
$ awk '/^\| 훅\(신규 줄\)/,/^$/' reports/evidence/m0/0a2/decisions.md | cut -c1-44 | sed 's/[[:space:]]*$//'
| 훅(신규 줄) | 위치 | 성격 |
| --- | --- | --- |
| `4` · `6` · `21` | **파일 머리** | slice가 쓴 메타
| `27` | **「이 파일의 구성 — provenance」 신설** | sl
| `144` · `147` | 「OPEN-STR-01」 | **정정** — *
| `194` | 「OPEN-STR-01」 | **정정** — `R-07` 식별
| `285` | 「M1 차단 묶음」의 `OPEN-QUAL-08` 표 행 | *
| `300` · `302` | 「OPEN-DEC-09」 | **정정** — l
| `390` | 「상호작용 모델」 절의 하류 파급 | **철회** — 21,3
| `428` | **말미 새 절 전부** | slice 절 추가(2026-08
```

**판정**:

- **C-3.1 — 검사 범위가 A1의 범위를 포함하는 상위집합이다.** A1이 주장하는 것은
  **`decisions.md`의 `OPEN-STR-01` 절**과 **`capability-map.md` §13**에서 X-1 단정이
  제거됐다는 것이다. **이 명령은 그보다 넓다** — `capability-map.md`는 **§13만** 잘라
  보지만(`awk`가 절 머리로 자른다. **줄 번호가 아니라 내용으로 집는다**),
  **`decisions.md`는 절로 자르지 않고 파일 전체를 본다.**
  **자기 출력이 그것을 보인다** — provenance 절의 매치가 `OPEN-STR-01` 절 **밖**이고,
  **분류 ③이 바로 그 절 밖 매치를 받는다.**
  **방향은 보수적이다** — 상위집합이라 **A1이 요구하는 것을 놓치지 않는다.**
  **`capability-map.md` 쪽 매치는 §13 안의 것뿐이다** — §5의 out_of_scope 매치가
  더는 섞이지 않는다(**그것이 Codex 2차 `M-2`였다**). §6·§7은 **이 패턴에 걸리지 않아**
  애초에 이 축의 대상이 아니며 **C-3.4가 낸다.**
- **판정** — **그 범위의 매치는 전부** ① **취소선 안**(원본 보존) ② **정정 블록이 무엇을
  고쳤는지 설명하는 산문** ③ **provenance 표가 이 정정을 등재한 행** ④ **§13 정정문이
  원문을 인용한 자리** 중 하나이며 **그 넷 중 어느 것도 살아 있는 단정이 아니다.**
  **A1이 요구하는 것은 여기까지다.**
- **이 축이 하지 않는 일** — **문서 전체를 보지 않는다**(§13 밖은 범위 밖이다) **그리고
  어휘도 이 패턴 둘로 한정된다**(`부가세 별도` 같은 자리를 잡지 않는다).
  **문서 전체의 정본은 C-3.4**이며 거기 **살아 있는 X-1 프레이밍이 남아 있다**
  (out_of_scope · `scope.md` **O-2**). **자리도 수도 여기 옮겨 적지 않는다** — C-3.4의
  출력과 그 아래 판정 표가 낸다. **§13은 이 축과 C-3.1이 모두 판정한다** — 두 범위가
  포개져 있기 때문이고, **여기서는 「제외」**(정정문이 원문을 인용한 자리)**로 판정한다.**
- **C-3.2** — 원본 두 문장이 **지워지지 않고 취소선으로 보존**됐고 바로 아래 정정 인용
  블록이 붙었다. `decisions.md`의 provenance 원칙(*"원본 문장은 지우지 않는다"*)을
  지킨다(**A2**).
- **C-3.3** — **출력 순서를 `sort`로 고정했다.** 앞 라운드의 기록은 **여러 파일을 인자로
  주는 `grep`의 파일 간 출력 순서에 의존했고**, 그 순서는 **구현·환경에 따라 다르다** —
  **기록을 뜬 환경에서는 재현됐으나 리뷰어 환경에서는 아니었다**(Codex 3차 low).
  **`sort`가 고정하는 것은 그 의존 하나**다. 매치 내용과 아래 판정은 바뀌지 않는다.
  **어느 구현이 어떤 순서를 내는지는 이 evidence가 주장하지 않는다** — 측정한 것은
  *"두 환경에서 순서가 달랐다"*까지다.
  > **한계** — 이 블록이 **어디서 돌려도 같은 출력을 낸다고 보장하지 않는다.**
  > 뒤따르는 `cut -c`가 **문자/바이트 중 무엇을 세는지 구현에 따라 다를 수 있고**,
  > 출력에 한글이 섞이므로 **잘리는 자리가 달라질 수 있다.** `cut` 자리를 손보면 그
  > 의존도 없앨 수 있으나 **이 라운드는 그 교환을 하지 않는다** — 판정은 잘린 자리에
  > 기대지 않는다.
- **C-3.3(판정)** — `OPEN-REG-05` 귀속이 두 파일에 적혔고, `OPEN-QUAL-10`이 소유자가 아니라는
  서술이 **ledger와 §13 양쪽에** 있다. **두 문서가 같은 말을 한다** — 0B verifier F-2가
  적출한 자기모순(*"한 자리에서 소유한다, 다른 자리에서 소유자가 아니다"*)이 재발하지
  않는다.
- **C-3.4** — **이 축이 `capability-map.md` 전체에 대한 정본이다.** 어휘를 가리지 않고
  (`ex-VAT` · `부가가치세` · `부가세` · `VAT` · `과세`) **줄이 아니라 블록**으로 본다 —
  한 문장이 여러 줄에 걸치면 줄 grep 이 놓치기 때문이다(0B 가 "창 밖" 미탐으로 두 번 태운
  자리다). **후보와 자리는 위 출력이 내고 여기 옮겨 적지 않는다.**
  **판정은 기계가 못 한다** — 후보 전건을 사람이 읽고 아래 표에 근거와 함께 남긴다
  (0B C-1 의 두 축 구분).

- **판정 축 — 출처 층을 먼저 가른다.** `data-extract.md` §1이 세 층을 정의한다:
  **`authoritative`**(공식 규정/API 문서 또는 사용자가 승인한 업무 규칙) ·
  **`observed`**(운영 데이터·로그에서 관찰한 실제 형태) ·
  **`legacy-behavior`**(기존 Python이 낸 결과, 정답 지위 없음).
  **X-1이 무너뜨린 것은 저장값 층이다** — legacy `Project.budget_estimate`가
  `money.py`의 **선언**(부가세 포함)과 **저장 관측**(*"부가세 포함 여부가 이력상 일관되지
  않다"*) 사이에서 갈린다는 것. **조달청 문서의 `추정가격` = 부가세 제외는 `authoritative`
  이고 그것을 정확히 인용한 자리는 X-1 프레이밍이 아니다**(C-1의 마지막 주석이 같은
  구분을 적는다).
  **층은 「그 값이 어디서 왔는가」로 정한다 — 문구가 아니다.** `data-extract.md` §1이
  **`observed`를 「기존 운영 데이터/로그에서 비식별화해 관찰한 실제 형태」**로,
  **`legacy-behavior`를 「기존 Python이 낸 결과」**로 가른다. `capability-map.md` §0도
  **기존 Python의 출력·docstring 수치를 `legacy-behavior`**라고 명시한다.
  **그래서 *"(관찰된 회귀)"* 같은 문구는 층 판정의 근거가 되지 않는다.**
  **그래서 판정 질문은 둘이다**: ① **어느 층의 서술인가** ②
  **저장값의 과세 처리를 근거 없이 단정하거나 두 금액의 차이를 정량화·방향화하는가.**
  **`O-2`에 남는 것은 ②가 참인 항목뿐이다.**

| 후보(절) | 출처 층 | ② 저장값의 과세 처리를 단정·정량화·방향화하는가 | `O-2` |
| --- | --- | --- | --- |
| §3 QUAL | **`authoritative`** — 조달청 문서 정의의 귀속 인용 | **아니다** | **빠진다** |
| §5 DEC(분류 근거) | **`authoritative` + legacy 선언** — `추정가격(ex-VAT)`은 **조달청 정의 그대로**이고, `기초금액/사업금액(과세 공고면 VAT 포함)`은 **legacy가 같게 선언한다**(`bid_summary.py:57` *"공고 투찰 기준금액(기초금액/사업금액, 과세 공고는 부가세 포함)"*) | **아니다** — **`Project.budget_estimate`의 저장값을 말하지 않는다.** 어느 금액에 투찰율을 곱하는가의 서술이다 | **빠진다** |
| §6 NOTI(금액 라벨) | **`authoritative`** — 두 금액을 **공식 정의대로 라벨해 다른 행에 싣는 표시 계약**(`bid_report_rows.py` 인용) | **아니다** — 라벨과 행 분리 요구다 | **빠진다** |
| §6 NOTI(투찰률) | **`legacy-behavior`** — 값의 출처가 **legacy 자신의 서술**이다. *"(관찰된 회귀)"*는 **legacy 저장소가 자기 회귀를 그렇게 부른 것**이고 **운영 데이터·로그 관측이 아니다** | **그렇다 — 방향을 말한다**(*"과세 공고에서 하한 여유가 **부풀어 보인다**"*). 그러려면 두 금액의 **대소 관계**가 정해져야 하고 그것은 `OPEN-REG-05` 소유인데 **그 귀속이 없다** | **남는다** |
| §6 NOTI(acceptance) | 표시 요구 | **아니다** — *"서로 다른 **행으로 표시된다**"*는 **표시**에 대한 것이고 차이의 크기·방향을 말하지 않는다 | **빠진다** |
| §7 SET(SET-09 F-4) | **`legacy-behavior`** — **0B ledger `R-BASIS-06`이 같은 `~10%`를 「legacy의 서술」로 명시 판정**한다(그 값은 **legacy commit 본문의 인용**이다). 표 제목의 「관찰된」은 **legacy 저장소가 자기 실패를 부른 말**이지 층이 아니다 | **그렇다 — 크기를 정량화한다**(*"과세 공고에서 **약 10%** 어긋난다"*). **0B ledger `R-BASIS-06`이 같은 주장에 ※를 달아 `OPEN-REG-05` 소유로 돌린 그 문장**인데 **여기엔 그 귀속이 없다** | **남는다** |
| §12(두 행) | **`authoritative`** — `OPEN-QUAL-10` registry의 조달청 문서 귀속 인용 | **아니다** | **빠진다** |
| §13 | 이 slice의 **정정문이 원문을 인용**한 자리 | **아니다** — 그 행은 고쳐졌다 | **빠진다** |

- **판정 근거의 출처** — 두 「남는다」의 판정 기준은 **0B가 세운 것**이다. 0B verifier
  **`F-c`**가 *"**「legacy 서술이니 그대로」로는 부족하다** — 귀속 없이 적으면 이 문서의
  확정 서술로 읽힌다"*로 **판정**했다. **유보를 실제로 단 것은 다른 라운드다** —
  같은 `F-c` 블록이 그렇게 적는다: **`R-BASIS-06`(`~10% 낮은 투찰가`)은 `F-3`이,
  `R-BASIS-03`(`rate × 1.1`)은 `F-b`가** 각각 **인용 귀속 + `OPEN-REG-05` 유보**를 달았다
  (`reports/evidence/m0/0b/scope.md`의 라운드 4 이력 안 `F-c` 정정 블록).
  **기준은 legacy 서술로 귀속하는 것만으로는 부족하고 그 `OPEN`을 지목해야 한다**는 것이며,
  두 항목 다 그 지목이 없다.

- **`O-2` 판정 요약** — **남는 것은 `legacy-behavior` 층에서 두 금액의 차이를
  정량화·방향화하면서 `OPEN-REG-05`를 지목하지 않은 항목**이고
  **§6 NOTI(투찰률)와 §7 SET(SET-09 F-4)**이 그것이다.
  **`legacy-behavior`는 「기존 Python이 낸 결과」로 정답 지위가 없다** — 그래서 **그
  값으로 V2 계약·acceptance를 세우려면 `OPEN-REG-05`의 지목이 더 필요하다.**
  **`authoritative` 층의 인용과 표시 계약은 `O-2`가 아니다** — 조달청 정의를 정확히 인용한
  것이고 X-1이 무너뜨린 저장값 층을 말하지 않는다.
  **자리는 위 출력이 낸다** — 산문에 옮겨 적지 않는다.
  **전부 out_of_scope다** — in_scope 가 승인한 것은 **§13 인계 행의 `사용자 영향`과
  `검증 방법` 두 자리뿐**이고 여기 남은 자리는 **§13 밖**이다. 건드리면 C-2가 낸 구조적
  불변이 깨진다. `scope.md` **O-2**로 등재했고 **0C 데이터 사전이 정본**이 된다.

> **이 재판정으로 `O-2`가 줄었다 — 0C 인계 내용이 달라진다.** 앞서 이 표는 **문서의
> 「자기 목소리」인지만 보고 출처 층을 가르지 않아** `authoritative` 인용(§5 · §6 금액
> 라벨)과 **표시 요구**(§6 acceptance)까지 `O-2`에 넣었다(Codex 3차 medium).
> **줄어든 자리는 오탐이 아니라 「층이 다른 참인 서술」**이다 — **0C는 그것들을 고칠
> 대상이 아니라 그대로 쓸 수 있는 인용으로 받는다.** **「전부 오탐이었다」가 아니다**:
> 두 자리는 그대로 `O-2`에 남는다.

- **C-3.5** — 위 두 출력이 **1:1로 대응**한다 — `git diff -U0 6a4e49b`가 낸 훅 하나하나가
  전수 표의 행으로 설명된다. **표에는 인접 훅을 한 행으로 묶는 자리가 있다** — 파일 머리 ·
  `OPEN-DEC-09` · 이 slice가 더한 X-1 정정. **어느 훅인지는 위 awk 출력이 낸다.**
  **번호를 여기 옮겨 적지 않는다.** **A2 성립.**

> **이 slice의 편집이 뒤따르는 훅을 밀었다.** X-1 정정 훅이 새로 들어오면서 그 아래
> 훅이 전부 이동했고 표를 그에 맞췄다. **이것이 인스턴스 고치기라는 것을 안다** —
> 다음 편집에 다시 낡는다. 표의 설계(훅 번호 ↔ 행)를 바꾸는 것은 이 slice의 위임 범위
> 밖이라 **번호는 유지하고 「정본은 명령의 출력」임을 표 아래에 명시**했으며,
> **셈(*"네 자리뿐"*)만 지웠다**(0B verifier `N-1` 처방). `scope.md` **O-3**.

---

### C-3.4의 스윕 본문 (형태 4 — 이름으로만 부르지 않는다)

**이 블록을 뽑아 실행하는 명령은 C-3.4 절의 첫 명령이고, 위 출력 블록에 그대로 있다.**
**C-4.1도 같은 방식을 쓴다** — 다른 것은 **marker와 그것을 담는 변수뿐**이다.
줄 번호가 아니라 **marker로 블록을 집고** `assert`로 유일성을 확인한 뒤 stdin 으로 흘려
넣는다. **커밋되지 않은 스크립트 파일에 의존하지 않는다.**

```python
# X-1 프레이밍 후보 스윕 — capability-map.md 전체 (0A3 C-3.4).
#
# 기계가 하는 일: 후보를 좁혀 「절 · 시작 줄 · 블록 본문」으로 찍는다.
#   - 어휘 하나에 기대지 않는다(ex-VAT · 부가가치세 · 부가세 · VAT · 과세).
#   - 줄이 아니라 **블록**을 본다 — 한 문장이 여러 줄에 걸치면 줄 grep 은 놓친다
#     (0B 가 "창 밖" 미탐으로 두 번 태운 자리다).
# 기계가 못 하는 일: "이 블록이 이 문서의 단정인가, 귀속된 인용인가"의 판정.
#   → 후보 전건을 사람이 읽고 아래 판정 표에 근거와 함께 남긴다
#     (0B C-1 의 두 축 구분: 정본이 있는 축은 기계, 없는 축은 사람).
import re

CM = "docs/discovery/capability-map.md"
lines = open(CM, encoding="utf-8").read().split("\n")

AMT = re.compile(r'추정가격|budget_estimate')
TAX = re.compile(r'ex-VAT|부가가치세|부가세|VAT|과세')
NEW = re.compile(r'^(#{1,6} |\s*[-*] |\s*\d+\. |\|)')      # 새 블록이 시작되는 형태

blocks, cur, start, sec, sec_at = [], [], 1, "", ""
for i, l in enumerate(lines, 1):
    if l.startswith("## "):
        sec = l[3:].strip()
    if NEW.match(l) or not l.strip():
        if cur:
            blocks.append((sec_at, start, cur))
        cur, start, sec_at = ([l], i, sec) if l.strip() else ([], i, sec)
    else:
        if not cur:
            start, sec_at = i, sec
        cur.append(l)
if cur:
    blocks.append((sec_at, start, cur))

for sec_at, start, body in blocks:
    t = " ".join(x.strip() for x in body)
    if AMT.search(t) and TAX.search(t):
        print(f"[{sec_at.split(' ')[0]:>3}] :{start}  {t[:104].rstrip()}")
```

---

## C-4. A4 불변 — 정본을 산출물에서 직접 계산한다 (**C-5** 형태 7 확인을 같은 실행에 포함)

**아래 출력 블록은 한 번의 실행이라 `C-4.x`와 `C-5.x`가 같은 블록에 들어 있다** —
서로 다른 실행의 출력을 섞지 않기 위해서다(§10.1 **형태 3**). 그래서 `C-5`에는 별도
절 머리가 없다.

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

**위 블록을 파일에서 뽑아 base와 HEAD 양쪽에서 실제로 실행했다** — 값을 비교하는 것이
불변의 증명이지 한쪽 값만 적는 것은 아니다. **블록은 줄 번호가 아니라 marker로 집고**
`assert`로 유일성을 확인한다(줄 번호를 쓰면 이 파일이 편집될 때마다 낡는다).

```
### 실행 시점 HEAD = 74132ea

--- C-4.1 인라인 본문을 파일에서 뽑는다 (줄 번호가 아니라 marker로 집는다) ---
$ BLK=$(python3 - <<'EOF'
import re
F = chr(96) * 3                     # 리터럴 백틱 세 개를 이 파일에 넣지 않는다
s = open("reports/evidence/m0/0a3/commands.md", encoding="utf-8").read()
b = [m.group(1) for m in re.finditer(F + r"python\n(.*?)" + F, s, re.S)
     if "0A3 A4 불변" in m.group(1)]
assert len(b) == 1, "marker 블록이 유일하지 않다: %d" % len(b)
print(b[0], end="")
EOF
)
$ printf %s "$BLK" | wc -l
      24

--- C-4.2 HEAD 에서 실행 ---
$ printf %s "$BLK" | python3 -
capability 총수      : 95
분류 4종 (필수/후속/폐기/근거부족): 61/17/6/11
활성 OPEN 총수       : 45
capability-map 줄 수 : 3184

--- C-4.3 base 48151b9 에서 같은 본문을 실행 ---
$ TMPD=$(mktemp -d); git archive 48151b9 docs/discovery/capability-map.md | tar -x -C "$TMPD"
$ printf %s "$BLK" | (cd "$TMPD" && python3 -); rm -rf "$TMPD"
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

- **C-4.1** — **스크립트 파일에 의존하지 않는다.** 아래 인라인 본문을 **marker로 집어**
  (줄 번호가 아니다 — 이 파일이 편집되면 줄 번호는 낡는다) `assert`로 유일성을 확인한 뒤
  stdin으로 흘려 넣는다. **커밋되지 않은 `inv0a3.py`에 의존하던 앞 라운드의 명령은
  그대로 돌리면 exit 2였다**(Codex 1차 medium · 형태 4). 수치는 맞았고 **틀린 것은
  명령이었다.**
- **C-4.2 ↔ C-4.3** — base `48151b9`와 HEAD에서 **같은 본문을 실제로 실행**했고 네 값이
  **모두 같다**(**A4**). 줄 수까지 같은 것은 §13 변경이 **표의 한 줄 안에서 일어났기**
  때문이며 **그 규모는 C-2.1이 낸다** — 여기 옮겨 적지 않는다.
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

## C-6. F-4 — ledger 정본을 직접 열어 그 조건부 서술을 따랐다

**`capability-map.md` §13의 `검증 방법`을 새로 쓰지 않았다.** 0B ledger `R-BASIS-01`이
authoritative이고(0B Codex `approve`로 확정) **그 문면을 옮겼다.** 아래가 그 정본이다 —
**줄 번호가 아니라 항목 이름으로 집는다.**

```
### 실행 시점 HEAD = 74132ea
### ledger 는 0B Codex approve 로 확정된 authoritative 정본이며 읽기만 한다.

$ awk '/^### R-BASIS-01 /,/^- \*\*근거\*\*/' docs/discovery/regression-ledger.md | awk '/^- \*\*검증 방법\*\*/,/^- \*\*동반 OPEN\*\*/'
- **검증 방법**
  - **결정 무관(무조건)**: **basis 태그가 다른 값 쌍**을 fixture로 고정한다 — **어느 쪽이
    크다는 전제를 넣지 않고**, 같은 운영자 값에 대해 **필터 결과가 basis에 따라 갈리는지**만
    고정한다. **같은 쌍이 감시 경로와 검색 경로(R-BASIS-02)에서 같은 답을 내는지도 함께
    고정한다** — 두 경로가 다른 basis를 쓰면 "제안에는 뜨는데 검색에는 안 나온다"가 된다.
  - **조건부 — `OPEN-REG-05` 결정에 따라 확정.** **과세/비과세로 정의된 경계 쌍은 그 OPEN이
    닫힌 뒤에 만든다.** 지금 만들면 **legacy의 미결 과세 의미로 corpus 경계가 굳는다** —
    저장된 추정가격의 과세 처리가 legacy 안에서 일관되지 않기 때문이다.
- **동반 OPEN**: **`OPEN-REG-05`**(활성)가 두 금액의 **과세 처리**, 따라서 **차이의 크기와

$ git diff --stat 48151b9...HEAD -- docs/discovery/regression-ledger.md ; echo "(빈 출력 = 무변경)"
(빈 출력 = 무변경)
$ wc -l < docs/discovery/regression-ledger.md
    1402
```

**판정**:

- **§13이 이제 ledger와 같은 두 갈래를 갖는다** — **결정 무관(무조건)** = basis 태그가
  다른 값 쌍, 어느 쪽이 크다는 전제 없이 + 감시·검색 두 경로의 일치.
  **조건부(`OPEN-REG-05` 결정에 따라 확정)** = 과세/비과세로 정의된 경계 쌍은 그 `OPEN`이
  닫힌 뒤에. 사유(*"legacy의 미결 과세 의미로 corpus 경계가 굳는다"*)까지 같다.
- **ledger는 읽기만 했다** — `git diff --stat`이 무변경을 내고 줄 수도 그대로다.
  **0B는 Codex `approve`로 닫혔고 이 slice의 out_of_scope다.**
- **`검증 방법` 외의 필드는 건드리지 않았다** — C-2.2의 hunk 지목이 대상이 그 인계 행
  하나임을 내고, 그 행 안에서 무엇이 바뀌었는지는 `git diff 48151b9...HEAD --
  docs/discovery/capability-map.md`가 낸다. **셈을 여기 옮겨 적지 않는다.**

---

## C-7. 「적은 대로 했는지」를 실행으로 확인한다 (verifier `V-1` · `W-1` · `Y-1`)

**`V-1`은 evidence가 *"라운드별 이력 절에 표시를 달았다"*고 적고 **그 행위를 하지 않은
것**이고, **`W-1`은 절을 개명하고 그 이름을 부르던 자리를 안 고친 것**이다. **둘 다 실행
한 줄로 잡혔다.** **그래서 적기 전에 실행으로 확인한다.**

**(a) 마커로 집는다** — 이 절의 서술이 **자기 검사에 걸리지 않게** 마커 문자열을 셸 변수로
쪼개 기록한다. 그러지 않으면 **이 절을 쓰는 것만으로 결과가 늘어난다.**

> **이 블록은 고정점이 아니다.** 쪼갠 것은 **자기참조 하나**이고, **검사 대상은 라운드마다
> 바뀐다** — 표시를 더 달거나 이 디렉터리를 편집하면 **출력이 달라진다.**
> **그때 다시 뜬다.** 이 절은 *"지금 이 상태에서 적은 대로 했는가"*를 보이는 것이지
> **어느 상태에서도 같은 출력을 낸다는 주장이 아니다**(verifier `W-2`).

**(b) 포인터를 두 축으로 본다.** 앞 라운드의 검사는 **`commands.md` 접두가 붙은 인용만**
봤고 **`W-1`의 자리는 전부 접두가 없어 구조적으로 못 봤다**(verifier `Y-1`).
**넓혔다** — 접두를 가리지 않고, **번호가 절을 넘겨준 뒤에 낡은 인용**까지 본다.
**본문은 아래에 인라인**한다(형태 4).

```python
# 포인터 대조 — scope.md·checklist.md 가 commands.md 의 절을 부르는 이름을 검사한다.
#
# 앞 라운드의 검사는 `commands.md` 접두가 붙은 인용만 봤다. W-1 의 자리는 전부 접두가
# 없어 구조적으로 못 봤다(verifier Y-1). 두 축으로 넓힌다.
#
#   (1) 존재   — 부르는 이름이 실재하는 절 머리인가. **접두 유무를 가리지 않는다.**
#   (2) 번호이동 — 그 번호가 쓰던 제목이 지금은 **다른 번호**의 제목인가(= 절이 번호를
#                옮겼다). 옮겼다면 그 이동 **전에 쓰인 줄**은 옛 절을 뜻하는 채로 남아
#                있다. **W-1 이 정확히 그 형태다.**
#
# 제목만 다듬은 것(번호는 그대로, 내용도 그대로)은 지목하지 않는다 — 그것으로 지목하면
# 이 파일의 정상 인용 다수가 걸려 축이 무의미해진다. 좁힌 근거는 evidence 에 적었다.
#
# 기계가 못 하는 것: "이 문장이 뜻한 절이 어느 것인가". 축은 지목하고 판정은 사람이 한다.
import re, subprocess, sys

CM  = "reports/evidence/m0/0a3/commands.md"
SRC = ["reports/evidence/m0/0a3/scope.md", "reports/evidence/m0/0a3/checklist.md"]
EXEMPT = {"C-5"}   # 절 머리가 없다 — C-4 의 출력 블록에 같은 실행으로 들어 있고 C-4 제목이 그렇게 적는다
rev = sys.argv[1] if len(sys.argv) > 1 else "HEAD"

def sh(*a): return subprocess.run(a, capture_output=True, text=True).stdout
HEAD_RE = re.compile(r'^## (C-\d)\. (.*)$', re.M)
now = dict(HEAD_RE.findall(sh("git","show",f"{rev}:{CM}")))
now_title_owner = {t: n for n, t in now.items()}

# 번호별 제목 이력 + 각 변경 커밋
shas = sh("git","log","--format=%H","--reverse",rev,"--",CM).split()
seen, moved_at = {}, {}
for sha in shas:
    for n, t in HEAD_RE.findall(sh("git","show",f"{sha}:{CM}")):
        t = t.strip()
        if seen.get(n) != t:
            prev = seen.get(n)
            seen[n] = t
            # 이전 제목이 지금 다른 번호의 것이면 = 그 번호가 절을 넘겨줬다
            if prev and now_title_owner.get(prev) not in (None, n):
                moved_at[n] = sha
def when(s): return int(sh("git","show","-s","--format=%ct",s).strip() or 0)

MENTION = re.compile(r'(?<![0-9A-Za-z-])(C-\d)(?![0-9.\-])')
bad = []
for path in SRC:
    cur, line_sha, lines = None, [], []
    for ln in sh("git","blame","--line-porcelain",rev,"--",path).split("\n"):
        if re.match(r'^[0-9a-f]{40} ', ln): cur = ln.split()[0]
        elif ln.startswith("\t"): line_sha.append(cur); lines.append(ln[1:])
    for i, (sha, line) in enumerate(zip(line_sha, lines), 1):
        for m in sorted(set(MENTION.findall(line))):
            if m in EXEMPT: continue
            if m not in now:
                bad.append(("존재", path, i, m, "그런 절이 없다"))
            elif m in moved_at and when(moved_at[m]) > when(sha):
                bad.append(("번호이동", path, i, m,
                            f"그 줄({sha[:7]})은 {m} 이 절을 넘겨준 커밋({moved_at[m][:7]})보다 앞선다"))
for k, p, i, m, w in bad: print(f"[{k}] {p}:{i}  {m} — {w}")
print(f"-- 지목 {len(bad)}건 · 번호를 넘겨준 절: {' '.join(sorted(moved_at)) or '없음'}")
```

**두 커밋에서 돌려 능력을 측정했다** — **넓혔다고 적기 전에 재는 것이 이번 라운드의 요구**다.

> **이 블록은 선언된 SHA의 트리에서 떴다.** 커밋 안의 블록은 **자기 커밋의 트리에서 뜰 수
> 없으므로**(`head_sha`와 같은 뿌리), **직전 커밋을 체크아웃한 worktree에서 실행**했다.
> **선언된 SHA에서 재현된다** — **이후 편집이 들어오면 다시 떠야 한다**(verifier `F-2`).

```
### 실행 시점 HEAD = 7bcf2e7
### 이 블록은 그 커밋의 트리에서 떴다 — 선언한 SHA 에서 재현된다.
### 마커는 셸 변수로 쪼갠다 · 파일 간 순서는 sort 로 고정한다.

## (a) 표시가 이력 절 안에 실제로 들어갔는가 (V-1)
$ MARK='재판정으로 이 절의'; grep -rn "⚠ 수정 라운드 10의 출처 층 $MARK" reports/evidence/m0/0a3/ | sort | cut -c1-64 | sed 's/[[:space:]]*$//'
reports/evidence/m0/0a3/checklist.md:421:> **⚠ 수정 라운드 10의 출처 층 재
reports/evidence/m0/0a3/scope.md:450:> **⚠ 수정 라운드 10의 출처 층 재판정으로
reports/evidence/m0/0a3/scope.md:534:> **⚠ 수정 라운드 10의 출처 층 재판정으로
$ MARK2='재판정으로 이 귀속은'; grep -rn "⚠ 수정 라운드 10의 출처 층 $MARK2" reports/evidence/m0/0a3/ | sort | cut -c1-64 | sed 's/[[:space:]]*$//'
reports/evidence/m0/0a3/checklist.md:365:| **H-1** (high) | C-3.
$ grep -n '^## 갱신 이력 — 수정 라운드 [45] ' reports/evidence/m0/0a3/scope.md | cut -c1-56 | sed 's/[[:space:]]*$//'
443:## 갱신 이력 — 수정 라운드 4 (verifier `H-1`~`H-4`)
514:## 갱신 이력 — 수정 라운드 5 (verifier `P-1` · 게이트 안 1건)
$ grep -n '^## 1[12]\. 수정 라운드' reports/evidence/m0/0a3/checklist.md | cut -c1-56 | sed 's/[[:space:]]*$//'
358:## 11. 수정 라운드 4 — verifier `H-1`~`H-4`
397:## 12. 수정 라운드 5 — verifier `P-1` (게이트 안 1건)

## (b) 포인터가 실재하고 낡지 않았는가 (W-1 · Y-1)
$ BLKP=$(python3 - <<'EOF'
import re
F = chr(96) * 3
s = open("reports/evidence/m0/0a3/commands.md", encoding="utf-8").read()
b = [m.group(1) for m in re.finditer(F + r"python\n(.*?)" + F, s, re.S)
     if "포인터 대조 — scope.md" in m.group(1)]
assert len(b) == 1, "marker 블록이 유일하지 않다: %d" % len(b)
print(b[0], end="")
EOF
)

### (b-1) W-1 이 살아 있던 커밋에서 — 넓힌 검사가 그 자리를 실제로 잡는가
$ printf %s "$BLKP" | python3 - 5d2ca03
[번호이동] reports/evidence/m0/0a3/scope.md:662  C-7 — 그 줄(2f8b65a)은 C-7 이 절을 넘겨준 커밋(5d2ca03)보다 앞선다
[번호이동] reports/evidence/m0/0a3/scope.md:690  C-7 — 그 줄(2f8b65a)은 C-7 이 절을 넘겨준 커밋(5d2ca03)보다 앞선다
[번호이동] reports/evidence/m0/0a3/scope.md:695  C-7 — 그 줄(2f8b65a)은 C-7 이 절을 넘겨준 커밋(5d2ca03)보다 앞선다
[번호이동] reports/evidence/m0/0a3/checklist.md:526  C-7 — 그 줄(2f8b65a)은 C-7 이 절을 넘겨준 커밋(5d2ca03)보다 앞선다
-- 지목 4건 · 번호를 넘겨준 절: C-6 C-7

### (b-2) 선언된 HEAD 7bcf2e7 에서
$ printf %s "$BLKP" | python3 - 7bcf2e7
-- 지목 0건 · 번호를 넘겨준 절: C-6 C-7
```

**판정 (a)** — **네 자리 전부 라운드별 이력 절 안에 있다.** 앞 두 grep 이 낸 줄 번호가
뒤 두 grep 이 낸 절 머리보다 **뒤**다: `scope.md`는 **라운드 4 이력**과 **라운드 5 이력**,
`checklist.md`는 **§11(라운드 4)**과 **§12(라운드 5)**. **이력 본문은 고쳐 쓰지 않았고
표시만 달았다.** **앞 라운드들이 달아 둔 다른 표시**는 **다른 마커를 써서** 여기 걸리지 않는다.

**판정 (b)** — **측정한 것을 적는다.**

- **`5d2ca03`(= `W-1`이 살아 있던 커밋)에서 그 자리를 잡는다.** 지목이 `scope.md`의
  세 줄과 `checklist.md`의 한 줄이고 **그 넷이 `W-1`이 지적한 자리 전부**다
  (`checklist.md` 쪽 한 줄에 인용이 **둘** 있었다). **다른 지목은 없다.**
- **선언된 HEAD(`7bcf2e7`)에서 지목 0.**
- **앞 축은 이것을 못 잡았다** — 접두가 붙은 인용만 봤기 때문이고, **`W-1`의 자리는
  전부 접두가 없다.**

**이 축이 좁혀 둔 것과 못 보는 것**:

| 좁힌 것 / 못 보는 것 | 왜 |
| --- | --- |
| **제목만 다듬은 번호는 지목하지 않는다** | 번호가 그대로이고 절도 그대로면 인용은 여전히 옳다. **지목 대상으로 넣으면 정상 인용 다수가 걸린다** — 실제로 그 형태로 먼저 짜서 돌려 보고 좁혔다(그때 HEAD 지목이 0이 아니었다) |
| **`C-5`는 면제다** | **절 머리가 없다** — C-4 의 출력 블록에 같은 실행으로 들어 있고 **C-4 제목이 그렇게 적는다.** 면제를 스크립트에 명시했다 |
| **줄이 옮겨지기만 해도 blame 이 갱신된다** | 내용을 안 고치고 위치만 바뀐 줄은 **새 커밋으로 찍혀 낡음 판정을 빠져나간다** |
| **코드 블록·인용 안의 이름도 같이 본다** | 오탐이 될 수 있다. **오탐이 는다는 이유로 좁히지 않았다** — 지금은 0건이다 |
| **다른 파일의 절 이름** | 0B 의 `C-8.2`·`C-9.9` 같은 것은 **점이 붙어** 이 패턴에 걸리지 않는다 |

**「전부 막는다」고 적지 않는다** — 위 표가 못 보는 것이고, **이 축이 막는 것은 측정된
한 형태(번호 이동 뒤 낡은 인용)**다.

---

## C-8. 이 slice가 만들지 않은 것

| 안 만든 것 | 이유 |
| --- | --- |
| **근거 없이 늘린 축·스크립트** | **결함이 관측되기 전에는 도구를 만들지 않았다**(0B가 세운 순서 — 결함이 먼저, 도구가 나중). **이 패키지의 스크립트는 전부 그 순서를 지켰다** — 불변 계산기는 **0A2 수치 축에서 떼어온 것**, X-1 스윕은 **`P-1`이 앞 축의 미탐을 반증한 뒤**, 포인터 대조는 **`W-1`이 나고 `Y-1`이 좁은 검사의 사각지대를 지적한 뒤**에 넓혔다. **목록도 셈도 여기 적지 않는다** — 목적과 도입 근거는 **이 파일 머리의 표**에 있고 **그 표는 `grep -n '^\`\`\`python'` 출력과 대조된다** |
| 새 `OPEN` | 소유자가 **이미 등록돼 있다**(`OPEN-REG-05`). C-2.3이 신설 0을 낸다 |
| `OPEN` 해소 | 활성 45건 전부 out_of_scope. C-4가 총수 불변을 낸다 |
| `capability-map.md` §13 밖 편집 | in_scope 가 승인한 것은 **§13 인계 행의 두 자리**(`사용자 영향`·`검증 방법`)뿐이다. 발견한 것은 `scope.md` **O-2**로 등재했고 **0C가 정본**이 된다 |
| 0B evidence 편집 | 0B는 Codex `approve`로 닫혔다. **인용만 하고 읽기 전용으로 다뤘다** |

---

## C-9. 이 라운드의 편집이 무엇을 낡게 했는지 훑는다 (Codex 4차 `B`·`C`)

**`B`와 `C`가 같은 것을 말한다 — 앞 라운드의 참인 기록이 뒤 라운드에 거짓이 된다.**
`B`는 대상이 늘어서, `C`는 지시 문구를 기록에 그대로 옮겨 범위가 사라져서.
**그래서 고친 뒤 그 수정이 무효화한 자리가 없는지 훑는다.**

> **이 블록은 선언된 SHA의 트리에서 떴다.** 커밋 안의 블록은 **자기 커밋의 트리에서 뜰 수
> 없으므로**(`head_sha`와 같은 뿌리) **직전 커밋을 체크아웃한 worktree에서 실행**했다.
> **선언된 SHA에서 재현된다** — **이후 편집이 들어오면 다시 떠야 한다**(verifier `F-2`).
> **명령에 축약·자리표시자를 쓰지 않는다** — 앞 라운드가 `git show <sha>:…/$f.md`로 적어
> **그대로 돌면 실패했다**(Codex 5차 `G-1`). **전체 경로로 적는다.**

```
### 실행 시점 HEAD = b940074
### 이 블록은 그 커밋의 트리에서 떴다 — 선언한 SHA 에서 재현된다.
### (b) 는 commands.md 를 대상에서 뺀다 — 이 절의 출력이 자기 검사에 걸리기 때문이다.
### 파일 간 출력 순서는 구현에 따라 다르므로 sort 로 고정한다(R-2 와 같은 부류).

## (a) 인벤토리 표 ↔ 실물 python 블록
$ grep -c '^```python' reports/evidence/m0/0a3/commands.md
3
$ awk '/^### 이 패키지가 쓰는 스크립트/,/^## /' reports/evidence/m0/0a3/commands.md | grep -c '^| \*\*'
3

## (b) 산문에 살아 있는 셈이 남았는가 (scope.md · checklist.md)
$ grep -nE '스크립트가 (둘|셋|하나)|스크립트 — (둘|셋)|블록이 (둘|셋)' reports/evidence/m0/0a3/scope.md reports/evidence/m0/0a3/checklist.md | sort | cut -c1-88
reports/evidence/m0/0a3/checklist.md:1017:| **지운 문면** | 머리글의 *"이 패키지가 쓰는 스크립트 — **둘**이고"
reports/evidence/m0/0a3/scope.md:1223:늘면서 `python` 블록이 셋이 됐는데 인벤토리는 **둘**이라 적고 있었다.

## (c) 라운드 15 실측 — 「~하지 않았다」류가 이것과 맞아야 한다
$ git diff --numstat 32f7a63..6b99420 -- reports/evidence/m0/0a3/
69	1	reports/evidence/m0/0a3/checklist.md
5	33	reports/evidence/m0/0a3/commands.md
57	2	reports/evidence/m0/0a3/scope.md
$ for f in scope checklist commands; do a=$(git show 32f7a63:reports/evidence/m0/0a3/$f.md | grep -c '^#\{2,4\} '); b=$(git show 6b99420:reports/evidence/m0/0a3/$f.md | grep -c '^#\{2,4\} '); echo "  $f: $a → $b"; done
  scope: 94 → 100
  checklist: 64 → 69
  commands: 31 → 30

## (d) 라운드 16~18 이 산출물을 건드렸는가
$ git diff --stat 6b99420..7bcf2e7 -- docs/discovery/capability-map.md reports/evidence/m0/0a2/
(빈 출력 = 무변경)
```

**판정**:

- **(a)** **인벤토리 표의 행 수와 실물 `python` 블록 수가 같다.** 표는 **셈을 적지 않고
  이 대조로 유지**된다 — 스크립트가 늘면 **행을 더하고 이 명령이 그것을 낸다.**
- **(b)** **살아 있는 셈은 없다.** 남은 매치는 둘 다 **이번 라운드가 그 셈을 고쳤다고
  적은 기록**이다 — `checklist.md` §24의 **「지운 문면」 인용**과 `scope.md` 라운드 17
  이력의 **경위 서술**. **기록이지 주장이 아니다.**
  **`commands.md`를 대상에서 뺐다** — 이 절의 **출력이 그 패턴에 걸려** 넣어 두면
  **쓸 때마다 매치가 늘어난다.** C-7이 마커를 셸 변수로 쪼갠 것과 같은 이유다.
- **(c)** 라운드 15는 **`commands.md`에서 지우고 `scope.md`·`checklist.md`에는 더했다.**
  그래서 **「새 서술을 쓰지 않았다」를 라운드 전체에 걸면 거짓**이고, 주장을
  **`commands.md`의 교체 대상 구간**으로 한정했다(Codex 4차 `C`).
  **절 머리도 두 파일은 늘고 `commands.md`만 줄었다** — 줄어든 것은 **중복이 빠진 것**이다.
- **(d)** **이 라운드는 산출물을 건드리지 않았다.**
