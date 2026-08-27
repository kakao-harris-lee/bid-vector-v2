# M0 / 0A3 — 검증 명령과 실행 출력

**range**: `48151b9...<head>`. **출력 블록은 각자 자기 실행 시점 HEAD를 첫 줄에 선언한다** —
한 값을 머리에 박으면 수정 라운드마다 낡는다(0B `N-1`). 접거나 손으로 압축한 블록은 없다
(§10.1 **형태 3**). **기록된 블록은 전부 뒤 커밋에 낡지 않는 형태**다 — legacy 고정 commit
읽기(C-1) · `capability-map.md`로 경로 한정(C-2) · 이 라운드 이후 바뀌지 않는 파일의
grep(C-3·C-5) · 산출물에서 정본을 계산하는 스크립트(C-4). **range 전체를 보는 검사는
출력을 적지 않고 명령만 남긴다**(C-2의 첫 표). 셈은 이 절의 출력이 내고 **산문으로
옮겨 적지 않는다**(**형태 6**).

**0B의 축을 새로 만들지 않았다.** 이 slice에 필요한 것만 두었다 — legacy 인용 확인(C-1),
범위·불변(C-2·C-4), X-1 프레이밍 스윕(C-3), 형태 7 등재 확인(C-5). 스윕 스크립트를 새로
만들지 않은 이유는 **결함이 아직 한 번도 이 slice에서 관측되지 않았기 때문**이다
(0B가 세운 순서 — *"필요가 관측되기 전에 도구를 늘리지 마라"*). 유일한 스크립트는 새 도구가
아니라 **0A2 수치 축의 「정본 산출」 절을 그대로 떼어낸 것**이고 **본문을 C-4에 인라인한 뒤
그 본문을 파일에서 뽑아 실행**한다 — **커밋되지 않은 스크립트 파일에 의존하지 않는다**
(**형태 4**).

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

## C-3. X-1 프레이밍 스윕 — 정정이 어디까지 갔고 어디서 멈췄나

```
### 실행 시점 HEAD = 491684f

--- C-3.1 in_scope 두 파일의 X-1 단정 스윕 (패턴 한정) ---
$ grep -nE 'ex-VAT|VAT·사정률만큼' docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md | cut -c1-130 | sed 's/[[:space:]]*$//'
reports/evidence/m0/0a2/decisions.md:40:| 「OPEN-STR-01」 절 안의 2026-08-27 정정 블록(**slice 0A3 · X-1**) | **slice 산물** — legacy **사실 서술
reports/evidence/m0/0a2/decisions.md:59:| `144` · `147` | 「OPEN-STR-01」 | **정정** — **X-1**: `budget_estimate`의 ex-VAT 단정과 차이의 크기·방
reports/evidence/m0/0a2/decisions.md:144:    `strategy.min|max_budget_estimate`. `Project.budget_estimate`는 ~~추정가격(ex-VAT)이다~~
reports/evidence/m0/0a2/decisions.md:147:    ~~기초금액과 추정가격은 VAT·사정률만큼 체계적으로 다르므로 임의 오차가 아니라
reports/evidence/m0/0a2/decisions.md:152:    > - ~~**`budget_estimate`는 ex-VAT가 아니다.** legacy는 그 반대로 선언한다 —~~
reports/evidence/m0/0a2/decisions.md:157:    >   ex-VAT라고도 VAT 포함이라고도 단정하지 않으며**, 그 판정은 활성 **`OPEN-REG-05`**가
reports/evidence/m0/0a2/decisions.md:158:    >   소유한다. 원본에 대해 남는 것은 **「ex-VAT」라는 단정에 근거가 없다**는 것까지다.
reports/evidence/m0/0a2/decisions.md:168:    > - **차이가 "VAT·사정률만큼"이라고 말할 근거가 없다.** legacy 자신이 저장된 값의
reports/evidence/m0/0a2/decisions.md:186:    >   "fix(m0-0b): budget_estimate의 ex-VAT 단정을 제거 (0C 선행 조사 X-1)"에서 정정했고,
docs/discovery/capability-map.md:1373:- **분류 근거**: 투찰율이 곱해지는 base는 추정가격(ex-VAT)이 아니라 기초금액/사업금액
docs/discovery/capability-map.md:3171:| 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값을 `Project.budget_es

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
$ grep -c 'OPEN-REG-05' docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md
reports/evidence/m0/0a2/decisions.md:4
docs/discovery/capability-map.md:1
$ grep -n 'OPEN-QUAL-10.*소유자가 아니' docs/discovery/regression-ledger.md docs/discovery/capability-map.md reports/evidence/m0/0a2/decisions.md | cut -c1-110 | sed 's/[[:space:]]*$//'
docs/discovery/capability-map.md:3171:| 0B — 예산 basis 불일치 (`legacy-defect`) — **세 경로** | **관찰**: 운영자가 지정한 예산 값
docs/discovery/regression-ledger.md:82:    `OPEN-QUAL-10`은 **시공능력평가금액 축**이라 이 질문의 소유자가 아니다.
docs/discovery/regression-ledger.md:1345:| **OPEN-REG-05** | **기초금액과 추정가격의 과세 처리 — 두 금액의 차이가 무엇으로 이루어지는가** | l

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

- **C-3.1** — **이 출력의 매치에 한정한 판정이다**(패턴은 `ex-VAT|VAT·사정률만큼`이고
  in_scope 두 파일만 본다 — **문서 전체에 대한 주장이 아니다**). **그 매치는 전부**
  ① **취소선 안**(원본 보존) ② **정정 블록이 무엇을 고쳤는지 설명하는 산문**
  ③ **provenance 표가 이 정정을 등재한 행** ④ **§13 정정문이 원문을 인용한 자리** 중
  하나이며 **그 넷 중 어느 것도 살아 있는 단정이 아니다.**
- **C-3.1의 사각지대** — **이 패턴은 X-1 프레이밍을 다 잡지 못한다.** `부가세 별도`처럼
  **어휘가 다른 자리**를 놓치고 **`capability-map.md` 전체를 보지도 않는다.**
  **문서 전체의 정본은 C-3.4**이며, 거기 **살아 있는 X-1 프레이밍이 남아 있다**
  (out_of_scope · `scope.md` **O-2**). **자리도 수도 여기 옮겨 적지 않는다** — C-3.4의
  출력과 그 아래 판정 표가 낸다.
- **C-3.2** — 원본 두 문장이 **지워지지 않고 취소선으로 보존**됐고 바로 아래 정정 인용
  블록이 붙었다. `decisions.md`의 provenance 원칙(*"원본 문장은 지우지 않는다"*)을
  지킨다(**A2**).
- **C-3.3** — `OPEN-REG-05` 귀속이 두 파일에 적혔고, `OPEN-QUAL-10`이 소유자가 아니라는
  서술이 **ledger와 §13 양쪽에** 있다. **두 문서가 같은 말을 한다** — 0B verifier F-2가
  적출한 자기모순(*"한 자리에서 소유한다, 다른 자리에서 소유자가 아니다"*)이 재발하지
  않는다.
- **C-3.4** — **이 축이 `capability-map.md` 전체에 대한 정본이다.** 어휘를 가리지 않고
  (`ex-VAT` · `부가가치세` · `부가세` · `VAT` · `과세`) **줄이 아니라 블록**으로 본다 —
  한 문장이 여러 줄에 걸치면 줄 grep 이 놓치기 때문이다(0B 가 "창 밖" 미탐으로 두 번 태운
  자리다). **후보와 자리는 위 출력이 내고 여기 옮겨 적지 않는다.**
  **판정은 기계가 못 한다** — *"이 블록이 이 문서의 단정인가, 귀속된 인용인가"*는 정본을
  계산할 수 없다. **후보 전건을 사람이 읽고 아래 표에 근거와 함께 남긴다**(0B C-1 의 두 축
  구분). **판정 축**: *"`capability-map.md`가 **자기 목소리로** ① `추정가격`을 부가세
  제외로 **단정**하거나 ② 두 금액의 차이를 **과세로 설명하거나 크기를 정량화**하는가."*

| 후보(절) | 판정 | 근거 |
| --- | --- | --- |
| §3 QUAL | **제외** | `추정가격` 언급이 **조달청 문서 정의의 귀속 인용**이다(*"같은 문서가 다른 금액 필드에는 단위·과세를 명시하므로(예: …)"*). 이 문서의 단정이 아니라 **다른 필드의 무주석이 규약 누락임을 보이는 근거**다 |
| §5 DEC | **포함 — 부가세 제외 단정** | *"투찰율이 곱해지는 base는 추정가격(**ex-VAT**)이 아니라 기초금액/사업금액(**과세 공고면 VAT 포함**)"* — 자기 목소리다 |
| §6 NOTI(승계 계약, 금액 라벨) | **포함 — 부가세 제외 단정** | *"기초금액(사업금액)과 추정가격(**부가세 별도**)"* |
| §6 NOTI(승계 계약, 투찰률) | **포함 — 과세로 차이의 방향을 설명** | *"추정가격 기준 율을 하한 옆에 두면 **과세 공고에서 하한 여유가 부풀어 보인다**"* — 방향까지 말한다 |
| §6 NOTI(acceptance) | **포함 — 과세를 전제** | *"**과세 공고** 보고서에서 기초금액과 추정가격이 서로 다른 행으로 표시된다"* — 무엇이 두 금액을 가르는지를 과세로 잡는다 |
| §7 SET(SET-09 F-4) | **포함 — 과세 + 정량** | *"폴백 분모를 추정가격으로 잡으면 **과세 공고에서 약 10% 어긋난다**"*. **0B ledger `R-BASIS-06`이 같은 주장에 ※를 달아 `OPEN-REG-05` 소유로 돌린 그 문장**인데 여기엔 그 귀속이 없다 |
| §12(두 자리) | **제외** | `OPEN-QUAL-10` registry 행의 **조달청 문서 귀속 인용** |
| §13 | **제외** | **이 slice의 정정문이 원문을 인용**한 자리다. 그 행은 고쳐졌다 |

- **C-3.4 판정 요약** — **살아 있는 X-1 프레이밍은 §5 · §6 · §7에 걸쳐 남아 있다.**
  **부가세 제외 단정**과 **과세로 차이를 설명·정량화하는 서술** 두 갈래이며,
  **자리와 수는 위 출력과 판정 표가 내고 산문에 옮겨 적지 않는다.**
  **전부 out_of_scope다** — in_scope가 *"§13 X-1 서술 한정"*이고 건드리면 C-2가 낸 구조적
  불변이 깨진다. `scope.md` **O-2**로 등재했고 **0C 데이터 사전이 정본**이 된다.
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

## C-7. 이 slice가 만들지 않은 것

| 안 만든 것 | 이유 |
| --- | --- |
| 새 스윕 축·스크립트 | 이 slice에서 그 부류의 결함이 **관측되지 않았다.** 0B가 세운 순서(결함이 먼저, 도구가 나중)를 지킨다 |
| 새 `OPEN` | 소유자가 **이미 등록돼 있다**(`OPEN-REG-05`). C-2.3이 신설 0을 낸다 |
| `OPEN` 해소 | 활성 45건 전부 out_of_scope. C-4가 총수 불변을 낸다 |
| `capability-map.md` §13 밖 편집 | in_scope 선언이 §13 한정. 발견한 것은 `scope.md` **O-2**로 등재했고 **0C가 정본**이 된다 |
| 0B evidence 편집 | 0B는 Codex `approve`로 닫혔다. **인용만 하고 읽기 전용으로 다뤘다** |
