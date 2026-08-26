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
