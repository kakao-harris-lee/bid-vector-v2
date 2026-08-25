# M0 / 0A — 실행 명령과 종료 코드

문서 slice이므로 `acceptance_commands`는 `N/A`이고 완료 조건 대조는 `checklist.md`가
담당한다(evidence-pack §"문서 slice(M0)의 evidence"). 이 파일은 **리뷰 라운드 메타데이터와
각 라운드의 검증 명령·결과**를 시간순으로 append한다.

---

## 2026-08-22 — Codex 독립 리뷰 라운드 1 (수신)

리뷰 실행은 codex-reviewer 레인이 수행했다. spec-writer는 산출물만 읽고 수정한다
(리뷰 JSON은 append-only이며 수정하지 않는다).

| 항목 | 값 |
| --- | --- |
| 산출물 | `reports/evidence/m0/0a/codex-review-20260822T061532Z.json` |
| CLI | codex-cli 0.148.0 |
| 모델 | gpt-5.6-sol (`model_reasoning_effort=high`) |
| reviewed_base | `3dc7d26333e9f3699c3fd1149651fe54500b6f27` |
| reviewed_head | `ff754c224beb55273dca30d75d0603dd6280e27e` |
| verdict | `request_changes` |
| findings | blocker 0 / high 2 / medium 2 |

finding 요약 (전문은 JSON):

| # | severity | 위치 | 요지 |
| --- | --- | --- | --- |
| 1 | high | capability-map.md:570 외 3곳 | OPEN이 미해결인 동작을 acceptance가 확정 — QUAL-03 / STR-08 / ML-03 / NOTI-04 |
| 2 | high | capability-map.md:1641 | SET-06 시간축 침묵 fallback — provenance 소실, 사유 없는 분모 제외 |
| 3 | medium | capability-map.md:487 | STR-13 이중 분류 + checklist 검증기가 이를 놓침 |
| 4 | medium | capability-map.md:842 | ML-07 acceptance 1건이 절차 규율이라 관측 불가 |

residual_risks 3건도 JSON에 기록돼 있다(원본 저장소·scout 노트 부재로 인용 원본 대조
불가, OPEN 잔여, 문서 slice라 실행 검증 불가).

참고 입력: `_workspace/m0-0a/02_verifier_report.md` (verifier 판정 `ready-for-review`,
low 6건). 이번 라운드에서 F-1·F-2·F-3·F-4·F-6을 함께 처리했다. F-5(디렉터리 없는 파일명
154건의 다중 해석 가능성)는 이번 범위에서 처리하지 않았다 — 인용 표기 규약 변경이라
문서 전반 재작성이 필요하고, verifier가 표본 검증에서 오해석 0건·미해석 0건을 확인했다.

---

## 2026-08-22 — 수정 라운드 1 검증

### C1. 분류 검증 (finding 3 대응 — 검증기 자체를 고쳤다)

기존 검증기는 분류 줄의 **첫 backtick만** 잘라 비교해 두 번째 분류 어휘를 놓쳤다.
분류 줄의 **모든 backtick 토큰**을 추출해 (a) 분류 어휘가 정확히 하나이고 (b) 그것이 첫
토큰인지 검사하도록 고쳤다.

```
$ python3 - <<'PY'
import re
from collections import Counter
txt=open('docs/discovery/capability-map.md',encoding='utf-8').read()
blocks=re.split(r'\n(?=### )', txt)
per={}; dual=[]; missing=[]
for b in blocks:
    m=re.match(r'### ([A-Z]+)-(\d+) · ', b)
    if not m: continue
    line=re.search(r'^- \*\*분류\*\*: *(.+)$', b, re.M)
    if not line: continue
    ticks=re.findall(r'`([^`]+)`', line.group(1))
    labels=[t for t in ticks if t in ('V2 필수','후속','폐기','근거 부족')]
    if len(labels)!=1 or ticks[0]!=labels[0]: dual.append((m.group(0).strip(), line.group(1)))
    per.setdefault(m.group(1),Counter())[labels[0]]+=1
    if labels[0]=='V2 필수' and ('Acceptance scenario' not in b or '사용자 가치' not in b):
        missing.append(m.group(0))
...
PY
| COL | 8 | 0 | 1 | 1 | 10 |
| STR | 9 | 4 | 0 | 2 | 15 |
| QUAL | 6 | 3 | 1 | 2 | 12 |
| ML | 7 | 2 | 0 | 1 | 10 |
| DEC | 9 | 1 | 0 | 0 | 10 |
| NOTI | 8 | 2 | 0 | 1 | 11 |
| SET | 3 | 1 | 0 | 2 | 6 |
| OPS | 13 | 1 | 4 | 2 | 20 |
TOTAL 필수/후속/폐기/근거부족 = 63 14 6 11 = 94
형식 위반: none
V2 필수 missing: none
```

- exit: 0
- **실패 이력(검증이 실제로 작동했다는 증거)**: 고친 검증기를 처음 돌렸을 때 형식 위반이
  **5건** 나왔다 — STR-13(Codex가 지목) 외에 QUAL-07 / DEC-09 / NOTI-06 / OPS-09가 부가
  설명 안에 다른 분류 어휘를 백틱으로 쓰고 있었다. STR-13은 실제 이중 분류라 capability를
  둘로 분리했고(STR-13 / STR-15), 나머지 4건은 분류가 하나이고 부가 설명이 다른 주체를
  가리키는 경우라 설명을 `분류 근거` / `경계` 줄로 옮겨 분류 줄을 순수하게 만들었다.
  형식 규칙 자체를 `capability-map.md` §0.3에 명문화했다.
- 집계 변화: capability 93 → 94, `근거 부족` 10 → 11 (STR-15 신설). §10 표 갱신 완료.

### C2. OPEN / acceptance 모순 전수 확인 (finding 1 대응)

Codex가 4곳을 지목했다. 같은 패턴이 더 있는지 **OPEN을 언급하면서 acceptance를 가진 블록
전수**(24개)를 추출해 육안 대조했다.

```
$ python3 - <<'PY'   # OPEN 언급 + acceptance 보유 블록의 acceptance 본문 출력
... (블록별 OPEN ID와 acceptance 항목 전체 출력)
PY
→ 24 블록 출력, 육안 대조 결과 추가 위반 2건
```

- exit: 0
- 추가 발견 2건: **DEC-02**(`OPEN-DEC-03` 계약 역전 채택을 전제한 "상한 초과 권한" 시나리오),
  **OPS-03**(`OPEN-NOTI-02` at-most-once를 전제한 격리 시나리오). Codex 지목 4곳과 함께
  조건부로 재서술했다.
- 위반 아님으로 판정한 사례(참고): COL-08·STR-03·QUAL-02·QUAL-04·DEC-03·DEC-04·DEC-06·
  DEC-08·DEC-10·NOTI-01·NOTI-05·NOTI-08·NOTI-10·SET-01·OPS-04·OPS-09·OPS-12 —
  acceptance가 OPEN의 쟁점이 아닌 축을 검증한다. STR-11은 이미 "채택 시 요구되는 관찰
  가능 동작"으로 조건부 표기돼 있었다.

### C3. secret 스캔 — 규격 전체 패턴 (verifier F-6 대응)

이전 라운드는 `BEGIN (RSA|EC|OPENSSH)` 대안을 뺀 부분집합 패턴으로 돌렸다. evidence-pack
규격의 전체 패턴으로 재실행했다.

```
$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ reports/evidence/m0/0a/
docs/discovery/capability-map.md:144:  관찰된 실패 문구는 HTTP 429 "API token quota exceeded"이고, 대응책이 4겹으로
docs/discovery/capability-map.md:1524:- **분류 근거**: webhook secret 검사가 **조건부**다 — 채널이 설정돼 있고 secret이 설정된
docs/discovery/capability-map.md:1531:  - webhook secret 미설정 상태의 미인증 호출이 **거부된다**. ← legacy는 통과시킨다.
reports/evidence/m0/0a/codex-review-20260822T061532Z.json:46: (Codex가 실행한 스캔 명령 문자열)
reports/evidence/m0/0a/checklist.md:157,162-165,174: (이전 라운드 스캔 결과 인용과 육안 확인 항목명)
```

- exit: 0
- 판정: **통과.** `capability-map.md` 3건은 전부 일반 명사다(외부 API 오류 문구 인용 1건,
  webhook 인증 메커니즘 설명 2건 — 설정 키 이름조차 없다). `BEGIN (RSA|EC|OPENSSH)`
  추가분 매치는 0건이다. evidence 파일 매치는 **스캔 명령 문자열과 이전 결과 인용**이며
  새로운 비밀값이 아니다.
- 육안 확인(패턴으로 못 잡는 것): Telegram chat id·bot token, 사업자 정보·상호·사업자번호,
  수신자 이메일, 개별 공고번호, 개별 금액 실값, credential·호스트명 — 전부 없음.
  verifier가 독립적으로 같은 결론에 도달했다(리포트 §2.8, PII 스캔 0건).

### C4. 문서 규모·인용 밀도 재측정 (verifier F-1 대응)

```
$ wc -l docs/discovery/capability-map.md
2518

$ grep -c '분류 근거' docs/discovery/capability-map.md
98

$ grep -oE 'OPEN-(COL|STR|QUAL|ML|DEC|NOTI|SET|OPS|NUM)-[0-9]+' docs/discovery/capability-map.md | sort -u | wc -l
68
```

- exit: 0
- `grep -c '분류 근거'`는 **행 수**이지 capability 수가 아니다(변형 라벨 `분류 근거 —`,
  `분류 근거(…)`가 일부 블록에 복수 존재). 이전 checklist가 이 값을 93으로 기록한 것은
  오류였다(verifier F-1). "94 블록 전부에 분류 근거가 있다"는 실질 주장은 C1의 블록
  파싱으로 검증한다.
- OPEN ID 유니크 68 = 활성 65 + 결번 3(`OPEN-OPS-06` 해소, `OPEN-NOTI-03`·`OPEN-SET-07`
  통합).

### C5. clean tree / scope 확인

```
$ git status --porcelain
(출력 없음)
```

- exit: 0. in_scope 밖 변경 없음. `_workspace/`, `bid-vector/`는 미변경.

---

## 2026-08-22 — Codex 재리뷰 라운드 2 (수신)

| 항목 | 값 |
| --- | --- |
| 산출물 | `reports/evidence/m0/0a/codex-review-20260822T065525Z.json` |
| CLI | codex-cli 0.148.0 |
| 모델 | gpt-5.6-sol (`model_reasoning_effort=high`) |
| reviewed_base | `3dc7d26333e9f3699c3fd1149651fe54500b6f27` |
| reviewed_head | `707b683b6c9f597be00f32fed43ad03976e1c590` |
| verdict | `request_changes` |
| findings | blocker 0 / high 4 / medium 1 |

라운드 1의 finding 4건은 해소로 확인됐고, 이번 4건은 **신규**다.

| # | severity | 위치 | 요지 |
| --- | --- | --- | --- |
| 1 | high | capability-map.md:2085 | OPS-09 acceptance가 `OPEN-OPS-01`의 정책 질문을 확정 — 라운드 1 #1 계열 잔존 |
| 2 | high | capability-map.md:1956 | OPS-04가 측정 불가를 초록(정상)으로 변환 |
| 3 | high | capability-map.md:1462 | NOTI-04 사용자 가치와 acceptance 불일치 |
| 4 | high | capability-map.md:2168 | OPS-13 acceptance가 크기 축만 검증 |
| 5 | medium | `.claude/skills/codex-review-gate/references/codex-output.strict.schema.json` | strict/정본 스키마의 `line` 계약 불일치 — **하네스 소관, spec-writer 범위 밖** |

---

## 2026-08-22 — 수정 라운드 2 검증

### D1. OPEN / acceptance 모순 전수 재확인 (finding 1 대응 + 신규 OPEN 반영)

라운드 1 이후 `OPEN-SET-10`·`OPEN-NOTI-08`이 신설됐으므로 전수 스윕을 다시 돌렸다.
OPEN을 언급하면서 acceptance를 가진 블록 26개의 조건부 표기 유무를 대조했다.

```
$ python3 - <<'PY'   # 블록별 OPEN ID와 조건부 묶음 유무
...
PY
COL-08 / STR-03 / STR-11 / STR-15 / QUAL-02 / QUAL-04 / DEC-03 / DEC-04 / DEC-06 /
DEC-08 / DEC-10 / NOTI-01 / NOTI-05 / NOTI-08 / NOTI-10 / SET-01 / SET-06 / OPS-04 /
OPS-12  → 조건부 없음 (acceptance가 해당 OPEN의 쟁점 축을 건드리지 않음)
STR-08 / QUAL-03 / ML-03 / DEC-02 / NOTI-04 / OPS-03 / OPS-09 → 조건부 보유
```

- exit: 0
- 신규 OPEN 2건 재확인 결과: **SET-06**(`OPEN-SET-10`)의 acceptance는 "승인된 대체 출처"
  라는 중립 표현을 써서 대체 채택 여부 어느 쪽으로 결정돼도 성립한다(미채택이면 대체 행이
  0건이고 나머지 항목은 그대로 유지). **STR-15**(`OPEN-STR-04`)는 `근거 부족`이며
  `채택 시 요구되는 관찰 가능 동작` 제목을 쓰므로 이미 조건부 형식이다.
- 추가 위반 0건. finding 1의 OPS-09만 조건부로 전환했다.

### D2. 분류·acceptance·ID 불변 재검증

```
$ python3 - <<'PY'   # 분류 백틱 전수 검사 + 중복 ID + OPEN 참조/등록 대조
...
PY
분류: {'V2 필수': 63, '근거 부족': 11, '폐기': 6, '후속': 14} = 94
형식 위반: none | V2 필수 결측: none
중복 ID: none
본문 참조 - 표 등록: ['OPEN-NOTI-03', 'OPEN-OPS-06', 'OPEN-SET-07']
```

- exit: 0
- 표에 없는 참조 3건은 §12.1에 결번 사유가 기록된 통합·해소 항목이다(신규 누락 아님).
- capability 94건, 분류 집계 불변. 이번 라운드는 capability를 추가·분리하지 않았다.

### D3. OPEN 집계

```
$ python3 - <<'PY'   # G1~G6 표 행 수와 선언 대조
G1 7 / G2 10 / G3 16 / G4 17 / G5 9 / G6 7
table total 66 | **총 66건** (G1 7 / G2 10 / G3 16 / G4 17 / G5 9 / G6 7)
```

- exit: 0. `OPEN-NOTI-08` 신설로 65 → 66 (G3 15 → 16).

### D4. 규모·secret 스캔

```
$ wc -l docs/discovery/capability-map.md
2591

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3
```

- exit: 0. 매치 3건은 라운드 0부터 동일한 일반 명사(외부 API 오류 문구 인용 1건, webhook
  인증 메커니즘 설명 2건)이며 새 매치가 없다.

### D5. clean tree

```
$ git status --porcelain
(출력 없음)
```

- exit: 0.

## 2026-08-26 — 수정 라운드 3 검증 (verifier `not-ready` M-1 + low 3건)

입력: `_workspace/m0-0a/04_verifier_report_round2.md` §3 (M-1, L-1, L-2, L-3).
Codex 리뷰 요청 없이 verifier finding에 대응한 라운드다.

### E1. 전수 스윕 — **재정의된 정의**로 재실행 (L-2 대응)

**구 정의(라운드 1·2)**: `분류` 줄을 가지면서 `- **Acceptance scenario**` bullet을 가진
블록. → (a) 분류 줄이 없는 **설계 입력 절**과 (b) **비-bullet** acceptance 제목이 구조적
사각지대였고, M-1이 정확히 그 교집합(OPS-00)에서 나왔다.

**신 정의(라운드 3부터)**: **acceptance 절을 가진 모든 `###` 블록**. 분류 줄 보유 여부와
bullet 형식을 묻지 않는다. 대상 제목은 §0.5가 규약으로 고정한 3형식 전부다 —
`- **Acceptance scenario**`(범위 괄호 변형 포함), `- **채택 시 요구되는 관찰 가능 동작**`,
비-bullet `**Acceptance scenario**`.

```
$ python3 - <<'PY'   # 제목 형식 열거 + 누락 탐색
ACC = r'^\s*-?\s*\*\*(Acceptance scenario|채택 시 요구되는 관찰 가능 동작)'
PY
- **Acceptance scenario**                (정확 일치)   : 63
- **Acceptance scenario (승계 계약 부분)**              : 1   (NOTI-08)
- **채택 시 요구되는 관찰 가능 동작**                     : 2   (STR-11, STR-15)
**Acceptance scenario**  (비-bullet, 설계 입력 절)      : 1   (OPS-00, :1908)
그 밖의 acceptance 유사 제목                            : 0

$ python3 - <<'PY'   # 블록 스윕 (신 정의)
PY
전체 ### 블록 수: 122
분류 줄 보유 블록(= capability) 수: 94
acceptance 절 보유 블록 수 (신 정의): 67
  그중 분류 줄 없음   (구 정의 사각지대 a): ['OPS-00 · 참조 사례: 큐 폭주 회귀 :1874']
  그중 비-bullet 제목 (구 정의 사각지대 b): ['OPS-00 · 참조 사례: 큐 폭주 회귀 :1874']

$ python3 - <<'PY'   # acceptance 절의 OPEN 인용과 조건부 표기 대조
PY
acceptance 절에서 OPEN을 인용하는 블록: 7
    STR-08 ['OPEN-STR-08']                 조건부 O
    QUAL-03 ['OPEN-QUAL-01']               조건부 O
    ML-03  ['OPEN-ML-03']                  조건부 O
    DEC-02 ['OPEN-DEC-03']                 조건부 O
    NOTI-04 ['OPEN-NOTI-01','OPEN-NOTI-08'] 조건부 O
    OPS-03 ['OPEN-NOTI-02']                조건부 O
    OPS-09 ['OPEN-OPS-01']                 조건부 O
   조건부 표시 없이 OPEN 인용: []
조건부 묶음 수: 8 | 무조건 acceptance 항목 수: 225
```

- exit: 0
- 스윕 대상이 **63 → 67 블록**으로 늘었다(+OPS-00, +STR-11, +STR-15, +NOTI-08). 새로
  편입된 4블록 중 M-1 계열이 남아 있던 것은 **OPS-00 하나뿐**이며 이번 라운드에 고쳤다.
  나머지 3블록은 위반 없음.
- 조건부 보유 7 capability가 §0.5의 목록과 정확히 일치한다(NOTI-04가 OPEN 2건이라 묶음은
  8개). 조건부 표시 없이 OPEN을 인용하는 블록 0건.
- "무조건 항목 225"는 라운드 2 verifier의 203과 다르다. **집계 변화가 아니라 정의 변화**다
  — 대상 블록이 4개 늘었고, 줄바꿈된 bullet을 하나의 항목으로 접합하도록 추출기를 고쳤다.

### E2. 어휘 중첩 스캔 — OPEN을 인용하지 **않으면서** 쟁점을 확정하는 유형 (L-2 대응)

E1의 블록 스윕은 acceptance가 OPEN id를 **명시할 때만** 걸린다. id를 쓰지 않으면서 그
OPEN의 쟁점을 확정하는 유형은 §12 활성 OPEN 66건의 결정문과 무조건 acceptance 225항목의
토큰 교집합으로 잡는다(불용어 제거, 2자 이상).

```
$ python3 - <<'PY'
PY
§12 활성 OPEN 행 수: 66 | 무조건 acceptance 항목 수: 225
중첩 4개 이상 쌍: 4
  (7) OPEN-SET-10 ↔ SET-06  "시간축 provenance — … 어느 시각 종류에서 왔는지(개찰 시각 /
      승인된 대체 출처)와 적용된 정책 version이 함께 남는다"
  (5) OPEN-SET-10 ↔ SET-06  "결측의 명시적 처리 — … `Unmeasurable(사유)`로 분류되며 …"
  (4) OPEN-OPS-01 ↔ OPS-13  "프로덕션 엔진에서만 유효한 코드 경로는 프로덕션 엔진 테스트
      없이 통과하지 못한다"
  (4) OPEN-ML-01  ↔ OPS-13  "`domain <- application <- adapters/app` 의존 방향을 뒤집는
      변이 …"
```

- exit: 0
- 4쌍 전부 **무해**로 판정했다.
  - SET-06 2건: `OPEN-SET-10`은 마감 시각 대체를 **승인된 정책으로 채택할지**를 묻는다.
    두 acceptance는 "승인된 대체 출처"라는 중립 표현을 써서 (a) 채택이면 그 행에
    provenance가 남고, (b) 미채택이면 대체 행이 0건이고 결측 행이 `Unmeasurable`로
    분류될 뿐 항목 자체는 그대로 성립한다. 어느 결정에도 깨지지 않으므로 무조건이 맞다.
  - OPS-13 ↔ `OPEN-OPS-01`: OPEN의 쟁점은 `Unclassified` 실패의 **재시도 정책**이고,
    적출된 항목은 아키텍처 게이트다. 축이 겹치지 않는다(공유 토큰은 "코드 경로").
  - OPS-13 ↔ `OPEN-ML-01`: OPEN의 쟁점은 ML 수학 커널을 **어느 런타임에 둘지**의 경계이고,
    적출된 항목은 Kotlin 모듈 내부의 의존 방향이다. 커널의 소속을 확정하지 않는다.
- 중첩 3개 쌍까지 낮추면 8쌍이 나오며 추가 4쌍(OPEN-NOTI-08↔NOTI-04, OPEN-NOTI-02↔NOTI-10,
  OPEN-DEC-04↔SET-01, OPEN-DEC-04↔DEC-05)도 검토했다. NOTI-04는 이미 조건부를 가진
  capability이고 적출된 항목은 "판정할 수 있으면 통지에 담긴다"는 결정 무관 항목이다.
  나머지 3건은 어휘만 겹친다.

### E3. 정책 기본값 단정 스캔

```
$ python3 - <<'PY'   # 무조건 acceptance에서 기본값|기본 ON|기본 OFF|항상
PY
COL-06 | 수집 1회 결과에서 `received = normalized + duplicate + dropped` 항등식이 항상
         성립하고, 위반 시 산출이 실패한다.
ML-08  | 새 metric을 추가할 때 가드 누락이 기본값이 되는 구조가 아니다(제외는 한 곳의
         데이터 변환으로).
```

- exit: 0. 라운드 2와 동일한 2건이며 둘 다 OPEN과 무관하다(수집 회계 항등식 / 구조 제약).

### E4. M-1 계열 잔존 재확인 (측정 불가 → 성공 변환)

```
$ grep -n '초록' docs/discovery/capability-map.md      # head_sha(fb832dd) 기준
1915:  경우**는 정상과 구별되는 **측정 불가(중립)**로 기록된다. 측정 불가를 성공(초록)으로
1995:  있음"이 아니다**. (2) legacy는 측정 불가를 **초록(정상)으로 표시하고 사유를 덧붙인다**:
1999:- **legacy 형태 처리 — 측정 불가의 초록 변환**: legacy의 문제의식(측정 실패를 빨강으로
2003:  ML-02 미학습 가드, ML-07 `not_evaluable`, SET-06 시간축 측정 불가). V2는 **초록이냐
2013:  - **측정 불가가 성공(초록) 표시로 렌더링되지 않는다** — 표시 계층에서 정상과 다른
2231:  못한다 — OPS-00의 모든 결함은 **래칫 초록 상태에서** 발생했다. property / contract /
2305:  않으므로 이 KPI들은 전부 초록으로 남는다** — OPS-00이 정확히 이 상태였다.

$ python3 - <<'PY'   # 무조건 acceptance 항목 중 초록/정상 변환 어휘
PY
OPS-00 | … 정상과 구별되는 **측정 불가(중립)**로 기록된다. 측정 불가를 성공(초록)으로
         변환하지 않는다 — … OPS-04가 소유 …
OPS-04 | **측정 불가가 성공(초록) 표시로 렌더링되지 않는다** …
```

- exit: 0
- acceptance에 남은 `초록` 2건은 **금지 방향**이다. 1995·1999·2003은 legacy 관찰과 그
  형태의 배격 서술이고, 2231·2305는 래칫 KPI 문맥(측정 불가와 무관)이다. V2 요구로 읽히는
  "측정 불가 → 초록" 형태는 0건이다.
- **행 번호 기준(verifier N-1 정정)**: 위 출력은 `head_sha`(`fb832dd`)에서 재실행한 값이다.
  최초 기록은 중간 커밋 `f29fa53` 시점이었고, `fb832dd`가 그 앞에 §0.5 +11줄·NOTI-04 +4줄을
  넣어 전부 **+15**만큼 밀렸다. 결론(V2 요구로 읽히는 형태 0건)은 두 시점에서 동일하다.
  이 evidence의 행 번호는 **`head_sha` 기준으로 통일**한다.

### E5. 분류·acceptance·ID·OPEN 불변 재검증

```
$ python3 - <<'PY'   # 분류 백틱 전수 + V2 필수 결측 + 중복 ID + OPEN 참조/등록 대조
PY
분류: {'V2 필수': 63, '근거 부족': 11, '폐기': 6, '후속': 14} = 94
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 ID: none
§12 등록 OPEN: 66 | 본문 참조 - 표 등록: ['OPEN-NOTI-03', 'OPEN-OPS-06', 'OPEN-SET-07']
총 OPEN id(등록+결번): 69

$ python3 - <<'PY'   # OPEN id 집합을 0b48eaa와 대조
PY
0b48eaa 등록 OPEN: 66 | worktree: 66
소멸: 없음 | 신규: 없음

$ grep -n '총 66건' docs/discovery/capability-map.md
2473:**총 66건** (G1 7 / G2 10 / G3 16 / G4 17 / G5 9 / G6 7). …
```

- exit: 0
- **집계 전부 불변.** capability 94, 63/14/6/11, 형식 위반 0, `V2 필수` 결측 0, 중복 ID 0,
  활성 OPEN 66(헤더 선언과 일치), 결번 3건(§12.1 기록된 통합·해소 항목).
- **OPEN 소멸 0건** — 이번 라운드는 OPEN을 해소하지도 신설하지도 않았다.

### E6. 규모·secret 스캔·clean tree

```
$ wc -l docs/discovery/capability-map.md
2608

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m0/0a/ | cut -d: -f1 | sort | uniq -c
   9 reports/evidence/m0/0a/checklist.md
   1 reports/evidence/m0/0a/codex-review-20260822T061532Z.json
   1 reports/evidence/m0/0a/codex-review-20260822T065525Z.json
  12 reports/evidence/m0/0a/commands.md
  (합계 23)

$ git diff --stat 0b48eaa -- docs/discovery/capability-map.md
 docs/discovery/capability-map.md | 25 ++++++++++++++++----
 1 file changed, 21 insertions(+), 4 deletions(-)

$ git status --porcelain -- docs/discovery/capability-map.md reports/evidence/m0/0a/
(출력 없음 — 커밋 후)
```

- exit: 0
- 2,591 → **2,608줄**. hunk별 순증 +17 — §0.5 +11(L-3 규약 문단), NOTI-04 +4(L-1 인용),
  OPS-00 +2(M-1), §12 행 ±0(행 내 교체).
- `docs/discovery/` 매치 **3건**은 라운드 0부터 동일한 일반 명사(외부 API 오류 문구 인용
  1건, webhook 인증 메커니즘 설명 2건)이며 신규 0건.
- evidence 디렉터리 매치 **23건은 전부 자기참조**다 — 스캔 명령 문자열 자체, 그 명령이
  뱉은 3줄을 인용한 기록, secret 스캔 절 제목, Codex 리뷰 JSON의 `commands_run`. 실제
  자격증명은 0건이다(라운드 0 C3에서 확인한 것과 같은 성격이며, 이번 라운드가 늘린 것은
  E6 자신의 명령 문자열 3줄뿐).
- Telegram id·사업자 정보는 패턴으로 잡히지 않으므로 육안 재확인했다. 이번 라운드 추가분
  (capability-map 4개 hunk, checklist §9, commands E1~E7)에 실데이터·식별자가 없다.

### E7. legacy 원본 인용 대조 (L-1)

verifier 권고의 라인 번호를 그대로 옮기지 않고 원본에서 직접 확인했다.

```
$ cd bid-vector && git show ed4b06c:app/services/award_notifications.py | grep -n "_recover_outcomes"
68:        recovered = self._recover_outcomes(
201:    def _recover_outcomes(

$ git show ed4b06c:app/services/award_notifications.py | sed -n '201,221p'
201:    def _recover_outcomes(
...
210:        """Backfill award_outcome for already-notified bids without re-notifying.
...
216:        public-award gate + :meth:`_record_outcomes`, but never sends a message
217:        nor stamps ``award_notified_at`` (재통지 금지). Returns the count recorded.
...
221:        """
```

- exit: 0
- `def`는 `:201`, **docstring은 `:210-221`**, "재통지 금지" 문장은 `:216-217`이다. verifier
  권고문의 `:201-217`은 `def` 줄부터 센 범위여서 문서에는 docstring 범위 `:210-221`을
  기입하고 해당 문장 위치 `:216-217`을 병기했다.
- 조사는 전부 `git show ed4b06c:`로 수행했다(legacy working tree가 dirty하며 읽기 전용).
