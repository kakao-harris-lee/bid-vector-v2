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

## 2026-08-26 — Codex 3차 리뷰 (수신) — **같은 range에서 effort에 따라 판정이 갈림**

같은 base/head(`3dc7d26...6c6b3a2`), 같은 CLI(`codex-cli 0.149.0`), 같은 모델
(`gpt-5.6-sol`)로 두 번 실행됐고 **`model_reasoning_effort`만 다르다.**

| 파일 | effort | verdict | finding |
| --- | --- | --- | --- |
| `codex-review-20260825T235415Z.json` | **high** | **`request_changes`** | high 1 / blocker 0 |
| `codex-review-20260825T235414Z.json` | medium | `approve` | 0 |

- **정본은 high 실행(`…235415Z`)이다.** 채택 근거: (1) 1·2차 리뷰가 **같은 high 레인**에서
  나왔으므로 라운드 간 판정을 비교하려면 레인이 같아야 한다, (2) 두 판정이 갈릴 때 finding을
  **낸 쪽이 검증 가능**하다 — high가 지목한 STR-08 `:451-452`는 실제로 존재하는 모순이고
  아래 F1·F2에서 독립 재현했다. `approve`는 부재의 주장이라 반증만 가능하다,
  (3) 유리한 판정을 고르면 심판 레인의 의미가 사라진다.
- **두 파일 모두 append-only로 커밋한다.** 판정이 갈린 사실 자체가 리뷰 재현성 이슈이며
  은폐 대상이 아니다. 하네스는 이 발견으로 `codex-review-gate`에 `effort=high`를 고정했다
  (`CLAUDE.md` 변경 이력 2026-08-26, 이 slice의 in_scope 밖).
- 정본 finding 1건(high): STR-08의 무조건 acceptance(`:451-452`)가 직전 run이 실패·취소인
  경우를 제외하지 않아 `OPEN-STR-08`에서 "재통지하지 않음"을 이미 선택하고, `legacy 형태
  처리`(`:447-449`)의 확정된 입력 경계도 같은 선택을 강제한다.
- `required_fix`의 두 선택지 중 **첫 번째(제외)를 사용자가 택했다.** `OPEN-STR-08`은
  해소하지 않는다.
- 정본의 residual risk 4건 중 첫 번째(리뷰어 worktree에 `bid-vector`·scout 노트 부재)는
  1·2차와 동일한 구조적 한계다.

## 2026-08-26 — 수정 라운드 4 검증

### F1. 전수 스윕 신규 축 — **조건부 보유 블록의 무조건 항목이 같은 OPEN의 쟁점을 확정하는가**

**기존 축의 한계**: 라운드 3까지의 E1은 "acceptance가 OPEN을 인용하는데 조건부 표시가
없는 블록"을 찾았다. STR-08은 **조건부 표시를 이미 갖고 있었기 때문에** 그 축에서 통과했다.
같은 블록의 **무조건 항목**이 그 조건부가 미정으로 둔 쟁점을 확정하는 형태는 어느 축에도
걸리지 않았다 — 이것이 이 계열이 4라운드 연속 재발한 구조적 원인이다.

**신규 축 F1 정의**: 조건부 묶음을 가진 블록마다, 그 묶음 본문 + 인용된 OPEN의 §12
결정문을 합쳐 **쟁점 어휘**를 만들고, **같은 블록의 무조건 항목**과 토큰 교집합을 잰다.
중첩 3개 이상을 전부 사람이 읽고 결정 무관성을 판정한다.

```
$ python3 - <<'PY'   # F1
PY
조건부 보유 블록: 7
중첩 3개 이상 (무조건 항목 × 같은 블록의 쟁점 어휘): 7
  7 | STR-08 OPEN-STR-08 | 중복 억제 판정이 무엇을 입력으로 삼는지가 관측 가능하다(… run 상태를
      함께 보는지). 그 입력 목록이 **선언된 정책 값**으로 조회 가능하다 …
  6 | OPS-09 OPEN-OPS-01 | 각 버킷의 재시도 가능성이 **선언된 정책 값**으로 조회 가능하다 …
  5 | STR-08 OPEN-STR-08 | **직전 run이 정상 완료된 경우**, 같은 공고가 연속 두 run에 걸쳐
      후보면 두 번째 run은 알림을 만들지 않는다. 직전 run이 실패·취소인 경우는 … 범위 밖 …
  5 | OPS-09 OPEN-OPS-01 | 분류 불가는 `Unclassified`라는 **별도 값**으로 관측된다 …
  4 | ML-03  OPEN-ML-03  | 응답이 산출을 "가격 적합도"로 명시하고 낙찰 확률로 표기하지 않는다.
  3 | NOTI-04 OPEN-NOTI-08 | 통지 시점에 낙찰/패찰을 판정할 수 있으면 그 판정이 통지에 포함된다.
  3 | NOTI-04 OPEN-NOTI-01 | 발송 결과가 성공·억제·실패 중 무엇이었는지가 통지 기록에서 구분 가능
```

- exit: 0 (수정 **후** 재실행 결과)
- > **⚠ 라운드 5 정정 (verifier H-1) — 아래 취소선 주장은 철회한다.**
  > 여기에는 원래 "**수정 전** 같은 축에서 STR-08의 무조건 항목이 중첩 **5**로 적출됐고,
  > 신규 축이 정본 finding을 **독립 재현**했다"고 적혀 있었다. **그 측정은 실제로 실행된
  > 적이 없다.** F1 스크립트는 STR-08 수정 커밋(`0b0c8aa`) **이후에** 작성·실행됐고,
  > 수정 전 문서에는 한 번도 돌리지 않았다. 기록된 5는 수정 **후** 값을 수정 **전**
  > 항목에 귀속시킨 것이다. `evidence-pack`의 "기억으로 재구성한 증적은 증적이 아니다"에
  > 저촉된다. 라운드 5에서 실측으로 교체했다 — 아래 G1 참조. 실측 결과 **F1은 이 사례를
  > 적출하지 못한다.**
- 7건 전부 수정 후 판정: **결정 무관**.
  - STR-08 2건: 전제를 "직전 run이 정상 완료된 경우"로 좁혔고, 입력 관측 항목은 세 후보
    (payload / 통지 이력 / run 상태)를 **모두 열거**한 뒤 목록의 **선언 여부**만 요구한다.
    (a)·(b) 어느 결정에도 성립한다.
  - OPS-09 2건: 항목 자체가 "정책의 **내용**이 아니라 **선언 여부**를 검증하므로 결정과
    무관하게 성립한다"를 본문에 명시한다. `Unclassified`를 별도 값으로 관측하는 것은
    재시도 가능성 결정과 축이 다르다.
  - ML-03: "응답이 산출을 가격 적합도로 명시한다"는 타입 분리·문서 규율 어느 쪽에서도
    요구된다. `OPEN-ML-03`은 **강제 메커니즘**의 선택이지 라벨 표기의 선택이 아니다.
  - NOTI-04 2건: 라운드 3 verifier가 이미 검토한 항목이다. 통지 시점 판정 포함은
    `OPEN-NOTI-08`(사후 재통지)과 축이 다르고, 발송 결과 구분은 `OPEN-NOTI-01`의 두
    선택지가 **모두 요구**한다(임시·대체 시나리오 양쪽이 결과 구분을 전제한다).

### F2. 신규 축 확장 — **확정 서술 절(acceptance 밖)이 같은 OPEN의 쟁점을 확정하는가**

Codex finding은 무조건 acceptance뿐 아니라 `legacy 형태 처리` 줄도 같은 선택을 강제한다고
지적했다. acceptance만 보는 축으로는 절반만 잡힌다. 그래서 조건부 보유 블록의 **확정 서술
절**(`legacy 형태 처리` / `V2 제약` / `결정 전에 확정되는 제약` / `경계` / `legacy 한계`)
중 **같은 절에서 그 OPEN을 언급하면서 단정 술어를 쓰는** 경우를 적출한다.

```
$ python3 - <<'PY'   # F2
PY
적출: 3 건
  [STR-08] OPEN-STR-08 — legacy 형태 처리
  [ML-03]  OPEN-ML-03  — legacy 형태 처리
  [OPS-09] OPEN-OPS-01 — V2 제약
```

- exit: 0 (수정 **후** 재실행 결과)
- > **⚠ 라운드 6 정정 (verifier L-5) — 위 "적출 3건"은 과소 계수다.**
  > 이 F2 구현에는 **위치 버그**가 있었다 — `body[:acc_i[0]]`, 즉 acceptance 절 **앞**만
  > 훑어서 그 **뒤에 오는 절을 통째로 놓쳤다**(라운드 5 G3에서 발견·수정). 버그만 고치고
  > 화이트리스트는 그대로 둔 채 같은 문서(`23b9c2a`)에 재실행하면 적출은 **4건**이다 —
  > `OPS-03`(`경계`: "알림 채널의 전달 의미 선택은 NOTI-05가 소유한다", `OPEN-NOTI-02`)이
  > 추가된다. **위반 판정은 바뀌지 않는다**(OPS-03 `경계`는 소유를 넘기는 서술로 위반
  > 아님) — 바뀌는 것은 적출 수와, 이 축이 무엇을 볼 수 있었는지다.
  > 같은 라운드의 F1만 정정 주석을 받고 이 절이 무주석이면 **정정이 선택적으로 보인다.**
  > 그래서 같은 형식으로 단다.
  >
  > **verifier 자기 정정 반영**: 라운드 4 검증에서 verifier의 F2 재구현이 낸 +1건
  > (OPS-03 `경계`)을 당시 "접합 규칙 차이"로 기록했으나, 실제 원인은 **이 위치
  > 버그**였고 **verifier의 재구현이 맞았다**. 그 정정을 여기 함께 남긴다.
  >
  > 덧붙여 이 버그는 라운드 5의 M-1(NOTI-02 `경계`)이 F2에 안 잡힌 이유이기도 하지만
  > **유일한 이유는 아니다** — 버그를 고친 F2'도 M-1을 못 본다(`checklist.md` §11.3
  > 한계 2의 라운드 6 정정 참조).
- **수정 전 판정**: 3건 중 **2건이 위반**이었다.
  - **STR-08** — Codex가 지목한 `:447-449` "중복 억제는 공고별 통지 이력(NOTI-02)에 근거해야
    한다". (b) 재발송을 택하면 억제 입력에 run 상태가 들어가야 하므로 이 단정이 (b)를
    배제한다. → 이번 라운드에 수정.
  - **ML-03 (신규 발견)** — "가격 적합도와 낙찰 확률을 서로 대입 불가능한 **별개 타입으로
    만든다**(`OPEN-ML-03`은 이를 타입으로 할지 문서 규율로 할지의 결정)". **한 문장 안에서**
    주절이 타입 분리를 확정하고 괄호가 그것을 미정이라고 말한다. STR-08과 정확히 같은
    형태이며 **F2가 없었으면 잡히지 않았다.** → 이번 라운드에 함께 수정.
  - **OPS-09** — 위반 아님. 같은 절이 "이는 **작성자 판단이지 확정이 아니다**"를 명시하고
    조건부로 이관했다(수정 라운드 1.5 `df78c77`에서 처리된 항목).
- 수정 후 3건 모두 "결정과 무관하게 확정되는 부분"과 "여기서 확정하지 않는다 → `OPEN-*`"을
  절 안에서 분리해 적는다.

### F3. 기존 축 재실행 (라운드 3 E1·E2·E3·E4)

```
$ python3 - <<'PY'   # 블록 스윕 + 어휘 중첩 + 기본값 단정 + 측정 불가 계열
PY
acceptance 절 보유 블록: 67
조건부 묶음 수: 8 | 무조건 acceptance 항목 수: 225
§12 활성 OPEN 행 수: 66
[A] 어휘 중첩 4개 이상 쌍: 4   (SET-06×2, OPS-13×2 — 라운드 3과 동일, 전부 무해)
[B] 정책 기본값 단정: COL-06, ML-08 (라운드 3과 동일, OPEN 무관)
[C] 측정 불가/초록 계열: OPS-00, OPS-04 (둘 다 금지 방향)
acceptance 절에서 OPEN 인용 7블록 전부 조건부 표기 보유, 조건부 없이 인용 0건
```

- exit: 0. 기존 4축 전부 라운드 3과 동일한 결과이며 신규 위반 0건.
- 무조건 항목 수 225 불변 — STR-08·ML-03 수정은 기존 항목의 **전제를 좁혔을 뿐** 항목을
  추가·삭제하지 않았다.

### F4. 분류·acceptance·ID·OPEN 불변 재검증

```
$ python3 - <<'PY'
PY
분류: {'V2 필수': 63, '근거 부족': 11, '폐기': 6, '후속': 14} = 94
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 ID: none
§12 등록 OPEN: 66 | 본문 참조 - 표 등록: ['OPEN-NOTI-03', 'OPEN-OPS-06', 'OPEN-SET-07']
총 OPEN id(등록+결번): 69

$ python3 - <<'PY'   # OPEN id 집합을 6c6b3a2와 대조
PY
6c6b3a2: 66 | worktree: 66 | 소멸: 없음 | 신규: 없음
```

- exit: 0. **전부 불변.** `OPEN-STR-08`·`OPEN-ML-03` 둘 다 유지했다 — 사용자가
  required_fix의 첫 선택지(제외)를 택했으므로 해소하지 않는다.

### F5. 규모·secret 스캔·clean tree

```
$ wc -l docs/discovery/capability-map.md
2621

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3

$ git status --porcelain -- docs/discovery/capability-map.md reports/evidence/m0/0a/
(출력 없음 — 커밋 후)
```

- exit: 0. 2,608 → **2,621줄**. hunk별 순증 +13 — STR-08 **+11**(17→28행), ML-03 **+2**
  (9→11행). secret 매치 3건 불변(일반 명사). 이번 라운드가 추가한 Codex JSON 2건에 대한
  같은 패턴 스캔은 **0건**이며, 내용은 verdict·finding·legacy 파일 경로·행 번호뿐이다.


## 2026-08-26 — 수정 라운드 5 검증 (verifier `not-ready` H-1 · M-1 · M-2 · L-1 · L-2)

### G1. **H-1 정정 — F1의 "수정 전 적출" 주장 실측 (처리 방식 (a))**

**무엇이 잘못됐나.** 라운드 4의 F1 절에 "수정 전 STR-08 무조건 항목이 중첩 **5**로
적출됐고 신규 축이 Codex 정본 finding을 **독립 재현**했다"고 적었다. **그 측정은 실행된
적이 없다.** 작업 순서를 보면 명백하다 — STR-08 수정 커밋 `0b0c8aa`를 **먼저** 만들고,
그 **뒤에** F1 스크립트를 작성해 수정된 문서에만 돌렸다. 수정 전 문서(`6c6b3a2`)에는 한
번도 실행하지 않았다. 기록된 5는 수정 **후** 값을 수정 **전** 항목에 귀속시킨 것이다.
`evidence-pack`의 "기억으로 재구성한 증적은 증적이 아니다"에 정면으로 저촉된다.

**정정 방식은 (a) — 실제로 재실행했다.** 라운드 4에서 실행한 것과 **같은 스크립트**를
수정 전 문서에 돌렸다.

```
$ git show 6c6b3a2:docs/discovery/capability-map.md > /tmp/pre.md
$ python3 f1.py /tmp/pre.md          # 수정 전
조건부 보유 블록: 7
중첩 3개 이상: 5건 — OPS-09(6), OPS-09(5), ML-03(4), NOTI-04(3), NOTI-04(3)
   ** STR-08은 한 건도 적출되지 않는다 **

$ python3 f1.py /tmp/pre.md          # 임계를 0으로 낮춰 STR-08만 강제 출력
   2 | STR-08 OPEN-STR-08 | 중복 억제 판정이 무엇을 입력으로 삼는지가 관측 가능하다(…)
       겹침: ['중복', '직전']
   2 | STR-08 OPEN-STR-08 | 같은 공고가 연속 두 run에 걸쳐 후보면 두 번째 run은 알림을
                            만들지 않는다.
       겹침: ['공고가', '않는다']

$ python3 f1.py docs/discovery/capability-map.md   # 수정 후 같은 두 항목
   7 | STR-08 | … run 상태를 함께 보는지 …
   5 | STR-08 | **직전 run이 정상 완료된 경우**, … 실패·취소인 경우는 … 범위 밖 …
```

- exit: 0
- **실측: 수정 전 2 / 2. 임계 3 미만이라 F1은 이 사례를 적출하지 못한다.** verifier가
  어절 토큰화로 재구현해 얻은 값(2 / 2)과 **정확히 일치**한다.
- 기록됐던 **5는 수정 후 두 번째 항목의 값**이다. 귀속 오류가 맞다.
- **따라서 "F1이 Codex 정본 finding을 독립 재현했다"를 철회한다.** 라운드 4의 F1 절과
  `checklist.md` §10.2 표에 정정 주석을 달았고, 이 주장에 의존하던 §10.1 채택 근거 (2)도
  철회해 근거 (1)·(3)만으로 재작성했다(정본이 high라는 **결론은 유지**된다).

**더 중요한 사실 — 이 축들의 점수는 수정에 의해 올라간다.** 2 → 5, 2 → 7이다. 결함이 있는
문장보다 **고쳐진 문장의 점수가 높다.** 이유는 구조적이다: 올바른 수정은 OPEN id를 인용하고
"실패·취소인 경우는 범위 밖"처럼 **쟁점 어휘를 명시적으로 끌어오기 때문**이다. 즉 이 축은
"쟁점을 **언급하는**" 항목을 고르지 "쟁점을 **선점하는**" 항목을 고르지 않는다.
**결함 탐지기가 아니라 사람이 읽을 후보를 좁히는 triage 필터다.** 실제 판정은 전부 사람이
한다. G4의 한계 서술을 함께 볼 것.

### G2. **L-2 — 스윕 스크립트 본문 기입**

라운드 3~4 evidence의 `python3 - <<'PY'` heredoc 본문이 전부 비어 있어 축이 evidence만으로
재현 불가였다. H-1이 정확히 그래서 생겼다(임계값 기반 축은 본문 없이 재현 불가). 아래가
**실제로 실행한 스크립트 전문**이다. 두 파일 모두 인자로 문서 경로를 받으므로
`git show <rev>:docs/discovery/capability-map.md > /tmp/x.md` 후 임의 리비전에 재실행할 수
있다.

**`f1.py` — F1 축 (라운드 4·5 공통)**

```python
import re
import sys
p=sys.argv[1] if len(sys.argv)>1 else "docs/discovery/capability-map.md"
lines=open(p,encoding="utf-8").read().split("\n")
starts=[i for i,l in enumerate(lines) if l.startswith("### ")]
blocks=[]
for n,s in enumerate(starts):
    e=starts[n+1] if n+1<len(starts) else len(lines)
    blocks.append((lines[s][4:].strip(), s+1, lines[s:e]))
ACC=re.compile(r'^\s*-?\s*\*\*(Acceptance scenario|채택 시 요구되는 관찰 가능 동작)')
COND=re.compile(r'조건부')

def acc_span(body):
    for i,l in enumerate(body):
        if ACC.match(l):
            ind=len(l)-len(l.lstrip()); out=[l]
            for j in range(i+1,len(body)):
                m=body[j]
                if not m.strip(): out.append(m); continue
                cur=len(m)-len(m.lstrip())
                if m.lstrip().startswith("- **") and cur<=ind and not ACC.match(m): break
                if m.startswith("**") and not ACC.match(m): break
                out.append(m)
            return out
    return None

def items(span):
    out,cur,curind=[],None,0
    for l in span[1:]:
        if not l.strip(): continue
        ind=len(l)-len(l.lstrip())
        if l.lstrip().startswith("- "):
            if cur is not None: out.append((curind,cur))
            cur,curind=l.strip()[2:],ind
        elif cur is not None and ind>curind: cur+=" "+l.strip()
    if cur is not None: out.append((curind,cur))
    return out

txt="\n".join(lines)
opens={}
for m in re.finditer(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|(.*)$', txt, re.M):
    opens[m.group(1)]=m.group(2)

STOP=set("있다 없다 된다 한다 그 이 되는 하는 것이 것을 수 안 더 두 세 각 같은 다른 모든 전부 위 아래 때 및 또는 그리고 판정 결과 값 상태 기록 경우 대상 기준 이상 이하 사실 여부 하나 사용 표시 확인 결정 조건부 확정 미정 시나리오 임시 대체 결정에 따라 무관 무조건 legacy OPEN".split())
def toks(s):
    s=re.sub(r'[`*\[\]()|]',' ',s)
    return {w for w in re.findall(r'[가-힣A-Za-z_][가-힣A-Za-z0-9_]{1,}',s) if len(w)>=2 and w not in STOP}

print("=== F1. 조건부 보유 블록의 무조건 항목이 같은 OPEN의 쟁점을 확정하는가 ===")
n_blocks=0; hits=[]
for t,ln,body in blocks:
    span=acc_span(body)
    if not span: continue
    its=items(span)
    # 조건부 묶음과 그 하위 항목을 분리
    bundles=[]; uncond=[]
    in_cond=False; cond_ind=None; buf=None
    for ind,it in its:
        is_bundle = COND.search(it) and ("결정에 따라 확정" in it or it.startswith("조건부"))
        if is_bundle:
            if buf: bundles.append(buf)
            buf=it; in_cond=True; cond_ind=ind; continue
        if in_cond and ind<=cond_ind:
            in_cond=False
            if buf: bundles.append(buf); buf=None
        if in_cond: buf+=" "+it; continue
        if "결정 무관" in it and len(it)<30: continue
        uncond.append(it)
    if buf: bundles.append(buf)
    if not bundles: continue
    n_blocks+=1
    cap=t.split(" ·")[0]
    for b in bundles:
        oids=sorted(set(re.findall(r'OPEN-[A-Z]+-\d+',b)))
        # 쟁점 어휘 = 조건부 묶음 본문 + 해당 OPEN의 §12 결정문
        axis=toks(b) | set().union(*[toks(opens.get(o,"")) for o in oids]) if oids else toks(b)
        for u in uncond:
            ov=axis & toks(u)
            if len(ov)>=3:
                hits.append((len(ov), cap, ",".join(oids), u[:110], sorted(ov)[:8]))
hits.sort(reverse=True)
print("조건부 보유 블록:", n_blocks)
print("중첩 3개 이상 (무조건 항목 × 같은 블록의 쟁점 어휘):", len(hits))
for h in hits: print("  ", h[0], "|", h[1], h[2], "|", h[3], "|", h[4])
```

**`sweep_r5.py` — F2'(확장 화이트리스트) + X(교차 블록) 축 (라운드 5)**

```python
import re, sys
p = sys.argv[1] if len(sys.argv) > 1 else "docs/discovery/capability-map.md"
lines = open(p, encoding="utf-8").read().split("\n")
starts = [i for i, l in enumerate(lines) if l.startswith("### ")]
blocks = []
for n, s in enumerate(starts):
    e = starts[n+1] if n+1 < len(starts) else len(lines)
    blocks.append((lines[s][4:].strip(), s+1, lines[s:e]))

ACC = re.compile(r'^\s*-?\s*\*\*(Acceptance scenario|채택 시 요구되는 관찰 가능 동작)')
COND = re.compile(r'조건부')
# F2 확장: 사용자 가치·분류 근거 추가 (verifier M-2 대응)
NARR = re.compile(r'^\s*- \*\*(legacy 형태 처리|V2 제약|결정 전에 확정되는 제약|경계|legacy 한계|사용자 가치|분류 근거|재사용 후보|모집단 제약|분리 사유)')
ASSERT = re.compile(r'(만든다|한다\b|않는다|둔다|쓴다|채택한다|채택하지 않는다|해야 한다|로 한다|분리한다|고정한다|사용한다|금지한다|대체한다|포함한다)')


def strip_acc(body):
    """acceptance 절의 span만 제거하고 블록의 나머지 전부를 돌려준다.
    (구 구현은 acceptance '앞'만 봐서 그 뒤의 `경계` 같은 절을 통째로 놓쳤다)"""
    out=[]; skip=False; ind0=None
    for l in body:
        if ACC.match(l):
            skip=True; ind0=len(l)-len(l.lstrip()); continue
        if skip:
            if not l.strip(): continue
            cur=len(l)-len(l.lstrip())
            if l.lstrip().startswith("- **") and cur<=ind0: skip=False
            elif l.startswith("**"): skip=False
            else: continue
        out.append(l)
    return out

def top_items(body, matcher):
    out, cur, curind, grab = [], None, 0, False
    for l in body:
        if not l.strip(): continue
        ind = len(l) - len(l.lstrip())
        if l.lstrip().startswith("- **") and ind == 0:
            if grab and cur: out.append(cur)
            grab = bool(matcher.match(l)); cur = l.strip()[2:] if grab else None; curind = ind
        elif grab and cur is not None and ind > curind:
            cur += " " + l.strip()
    if grab and cur: out.append(cur)
    return out

txt = "\n".join(lines)
opens = {}
for m in re.finditer(r'^\|\s*(OPEN-[A-Z]+-\d+)\s*\|(.*)$', txt, re.M):
    opens[m.group(1)] = m.group(2)

STOP = set("있다 없다 된다 한다 그 이 되는 하는 것이 것을 수 안 더 두 세 각 같은 다른 모든 전부 위 아래 때 및 또는 그리고 판정 결과 값 상태 기록 경우 대상 기준 이상 이하 사실 여부 하나 사용 표시 확인 결정 조건부 확정 미정 시나리오 임시 대체 무관 무조건 legacy OPEN".split())
def toks(s):
    s = re.sub(r'[`*\[\]()|]', ' ', s)
    return {w for w in re.findall(r'[가-힣A-Za-z_][가-힣A-Za-z0-9_]{1,}', s) if len(w) >= 2 and w not in STOP}

mode = sys.argv[2] if len(sys.argv) > 2 else "all"

if mode in ("all", "f2"):
    print("=== F2' (확장 화이트리스트: +사용자 가치 +분류 근거 +재사용 후보 +모집단 제약 +분리 사유) ===")
    n = 0
    for t, ln, body in blocks:
        acc_i = [i for i, l in enumerate(body) if ACC.match(l)]
        if not acc_i: continue
        accblock = "\n".join(body[acc_i[0]:])
        if not COND.search(accblock): continue
        oids = sorted(set(re.findall(r'OPEN-[A-Z]+-\d+', accblock)))
        cap = t.split(" ·")[0]
        for narr in top_items(strip_acc(body), NARR):
            mention = [o for o in oids if o in narr]
            if mention and ASSERT.search(narr):
                n += 1
                print(f"\n  [{cap}] {mention}")
                print("    ", narr[:340])
    print("\n  적출:", n, "건")

if mode in ("all", "x"):
    # 교차 블록 축 (verifier §2.4 a+c): 모든 확정 서술 절 x 모든 활성 OPEN 결정문.
    # 소유 블록·OPEN id 인용 여부를 묻지 않는다.
    print("\n=== X. 교차 블록 축 — 모든 확정 서술 절 × 모든 활성 OPEN 결정문 (임계 5) ===")
    narrs = []
    for t, ln, body in blocks:
        cap = t.split(" ·")[0]
        for narr in top_items(strip_acc(body), NARR):
            if ASSERT.search(narr):
                narrs.append((cap, narr))
    print("  확정 서술 절 수:", len(narrs), "| 활성 OPEN:", len(opens))
    hits = []
    for oid, od in opens.items():
        ot = toks(od)
        for cap, narr in narrs:
            ov = ot & toks(narr)
            if len(ov) >= 5:
                own = oid in narr
                hits.append((len(ov), oid, cap, own, narr[:150], sorted(ov)[:10]))
    hits.sort(reverse=True)
    print("  중첩 5 이상 적출:", len(hits))
    for h in hits:
        print(f"   {h[0]} | {h[1]} | {h[2]} | id인용={h[3]} | {h[4]}")
        print(f"       겹침: {h[5]}")
```

- 라운드 3의 E1·E2·E3·E4 스크립트는 `sweep_r5.py`의 `top_items`/`toks`/`opens` 추출부와
  같은 구조이며, E1의 블록·acceptance 추출은 위 `f1.py`의 `acc_span`/`items`와 동일하다.
  라운드 3 절에 소급 기입하는 대신 여기서 한 벌로 제공한다.

### G3. 축 보강 — F2' (절 화이트리스트 확장) 과 X (교차 블록)

verifier §2.4가 지적한 세 사각지대에 대응한다. (a) 교차 블록, (b) 절 화이트리스트 밖,
(c) OPEN id 미언급.

**F2' 변경 2가지.** ① 화이트리스트에 `사용자 가치`·`분류 근거`(및 `재사용 후보`·
`모집단 제약`·`분리 사유`)를 추가했다 — M-2가 정확히 `사용자 가치`에 있었고, 라운드 2
Codex high #3도 같은 절이었다. ② **구조적 버그를 고쳤다**: 구 구현은 `body[:acc_i[0]]`,
즉 acceptance 절 **앞**만 훑어서 **그 뒤에 오는 절을 통째로 놓쳤다.** NOTI-02의 `경계`가
바로 거기 있어 라운드 4 F2는 M-1을 **볼 수조차 없었다.** 이제 acceptance span만 제외하고
블록 전체를 본다(`strip_acc`).

**X (신규, 교차 블록)**: **모든** 확정 서술 절 × **모든** 활성 OPEN 결정문. 소유 블록인지,
그 절이 OPEN id를 인용하는지 **묻지 않는다** — (a)와 (c)를 함께 덮는다.

```
$ python3 sweep_r5.py docs/discovery/capability-map.md f2
  [STR-08] ['OPEN-STR-08']  **사용자 가치**: **직전 run이 정상 완료된 흐름에서** … (M-2 수정 후)
  [STR-08] ['OPEN-STR-08']  **legacy 형태 처리 — 관찰과, 결정과 무관하게 확정되는 부분** …
  [ML-03]  ['OPEN-ML-03']   **legacy 형태 처리 — 결정과 무관하게 확정되는 부분** …
  [OPS-03] ['OPEN-NOTI-02'] **경계**: 알림 채널의 전달 의미 선택은 NOTI-05가 소유한다 …
  [OPS-09] ['OPEN-OPS-01']  **V2 제약** … 이는 작성자 판단이지 확정이 아니다 …
  적출: 5 건

$ python3 sweep_r5.py docs/discovery/capability-map.md x
확정 서술 절 수: 170 | 활성 OPEN: 66   (전수 모집단 11,220쌍)
중첩 5 이상 적출: 15
  14 OPEN-OPS-01×OPS-09(V2 제약)      11 OPEN-SET-10×SET-06(시간축 대체)
  10 OPEN-NOTI-08×NOTI-04(분류 근거)   8 OPEN-QUAL-01×QUAL-03   8 OPEN-ML-02×ML-09
   7 OPEN-QUAL-07×QUAL-04              7 OPEN-QUAL-04×QUAL-10   7 OPEN-OPS-01×OPS-09
   6 OPEN-STR-08×STR-08                6 OPEN-NOTI-08×SET-01    5 OPEN-SET-10×SET-06
   5 OPEN-QUAL-05×QUAL-10              5 OPEN-OPS-02×OPS-09     5 OPEN-OPS-01×OPS-04
   5 OPEN-DEC-04×DEC-10
```

- exit: 0
- **추가 위반 0건.** 적출 20건(F2' 5 + X 15)을 전수로 읽었다.
  - F2' 5건: STR-08 2건·ML-03 1건은 이번·지난 라운드에 고친 것이고 전부 "결정과 무관하게
    확정되는 부분"과 "여기서 확정하지 않는다 → `OPEN-*`"을 절 안에서 분리한다.
    OPS-03 `경계`는 소유를 NOTI-05로 넘기는 서술이고, OPS-09 `V2 제약`은 "작성자 판단이지
    확정이 아니다"를 명시한다.
  - X 15건: **전부 legacy 관찰 서술(`분류 근거`)이거나 이미 조건부화된 절이다.** 개별
    판정 — OPS-09·OPS-04의 `V2 제약`/`legacy 형태 처리`는 조건부 이관이 명시돼 있고,
    SET-06 `시간축 대체`는 "V2는 대체를 **승인된 정책으로만** 허용한다"는 메타 제약이라
    `OPEN-SET-10`의 (a)·(b) 어느 쪽에도 성립한다((b)면 승인이 없어 대체가 없다).
    QUAL-04는 어휘 테이블의 **저장 형태**를 폐기할 뿐 `OPEN-QUAL-07`이 묻는 **세그먼트
    범위**를 정하지 않는다. QUAL-10 `결정 전에 확정되는 제약` 2건은 §12 행 자신이
    "넣는다면 … 선행한다"로 같은 조건부를 적고 있다. NOTI-04·SET-01·QUAL-03·ML-09·
    DEC-10은 파일:라인이 붙은 legacy 관찰이다. OPS-02×OPS-09는 어휘 잡음.

### G4. **축의 한계 — 이 축들은 결함 부재를 증명하지 않는다**

**이 절은 evidence의 필수 부분이다.** 라운드 3의 오진은 "형식 규약 준수(조건부 마커의
존재)"를 내용 정합의 **대리 지표**로 쓴 데서 나왔다. F1·F2는 그 배제를 되돌렸지만
**같은 오류를 한 단계 아래에서 반복했다** — 이번엔 "절 이름"과 "같은 블록"이 대리 지표였고,
그래서 M-1(다른 블록)과 M-2(목록 밖 절)가 보이지 않았다. 라운드 5의 F2'·X도 **여전히
대리 지표 기반이다.** 구체적으로:

1. **점수가 결함과 역상관한다.** G1에서 실측했다 — 수정 전 2, 수정 후 5·7. 올바른 수정이
   OPEN id와 쟁점 어휘를 끌어오기 때문이다. **높은 점수는 위험 신호가 아니라 "이 절이 그
   쟁점을 다룬다"는 신호일 뿐이다.**
2. **X는 M-1을 적출하지 못한다.** 버그를 고친 뒤에도 NOTI-02 `경계` × `OPEN-STR-08`의
   실측 점수는 **2**(수정 후 3)다. 임계를 2로 내리면 적출이 **437건**(모집단 11,220의 3.9%)
   이 되어 사람이 읽을 수 있는 양을 벗어난다. **verifier는 축이 아니라 읽어서 M-1을 찾았고,
   축은 읽을 곳을 좁히는 보조였다.**

   **기준 리비전(라운드 6 추가, verifier L-7)**: **수정 전 = `23b9c2a`**(M-1·M-2 수정 직전.
   `0b0c8aa`도 4행 전부 같은 값이다), **수정 후 = `0581e97`**(M-1·M-2 수정 커밋). 아래
   4행은 G2의 `sweep_r5.py`로 두 리비전에서 전부 재현된다. 라운드 5에는 이 기준이
   적혀 있지 않았다 — **H-1이 정확히 이 계열(기준 미표기로 인한 귀속 오류)이었으므로
   명시한다.**

   | 임계 | 수정 전 적출 (`23b9c2a`) | 수정 후 적출 (`0581e97`) | M-1 포함? |
   | --- | --- | --- | --- |
   | 5 | 16 | 15 | 아니오 |
   | 4 | 37 | 37 | 아니오 |
   | 3 | 109 | 110 | 아니오 |
   | 2 | 437 | 440 | 예(순위 최하위, 잡음과 구별 불가) |

   verifier의 재구현은 `23b9c2a`에서 임계 5·4를 **15·36**으로 냈다(각 1 차이). 토큰화·
   접합 규칙의 재구현 차이이며, G2 스크립트로는 16·37이다. **논지는 어느 기준·어느
   구현에서도 동일하다** — 임계를 M-1이 걸릴 때까지 낮추면 적출이 400건대가 되어 사람이
   읽을 수 있는 양을 벗어난다.
3. **새 사각지대가 남는다.** 어휘가 겹치지 않게 같은 쟁점을 확정하는 서술(동의어·환언),
   확정 서술 절이 아닌 곳(표 셀, §0.x 규약, acceptance 하위 항목의 괄호), 그리고 여러
   절에 나뉘어 합쳐질 때만 확정이 되는 형태는 어느 축도 보지 못한다.

**따라서 이 축들을 "계열 A의 부재 증명"으로 읽지 마라.** 적출 0건은 **"임계 위에서 사람이
읽을 후보가 없었다"**는 뜻이지 **"위반이 없다"**는 뜻이 아니다. 계열 A의 실질 방어는
(i) 사람이 OPEN 66건 각각에 대해 "이 결정의 두 선택지가 문서 전체에서 모두 성립하는가"를
읽는 것과 (ii) Codex 독립 리뷰뿐이며, 축은 그 읽기의 **순서를 정하는 도구**다.

### G5. 분류·acceptance·ID·OPEN 불변 재검증

```
$ python3 invariants.py
분류: {'V2 필수': 63, '근거 부족': 11, '폐기': 6, '후속': 14} = 94
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 ID: none
§12 등록 OPEN: 66 | 본문 참조 - 표 등록: ['OPEN-NOTI-03', 'OPEN-OPS-06', 'OPEN-SET-07']
총 OPEN id(등록+결번): 69

$ python3 - <<'PY'   # OPEN id 집합을 744fbfd와 대조
PY
744fbfd: 66 | worktree: 66 | 소멸: 없음 | 신규: 없음
```

- exit: 0. **전부 불변.** `OPEN-STR-08`·`OPEN-ML-03` 유지.
- M-2 수정으로 STR-08 `사용자 가치`가 바뀌었으나 `V2 필수` 결측 검사는 여전히 0이다
  (절이 사라진 것이 아니라 한정어가 붙었다).

### G6. 규모·secret 스캔·clean tree

```
$ wc -l docs/discovery/capability-map.md
2624

$ git diff --stat 744fbfd -- docs/discovery/capability-map.md
 1 file changed, 6 insertions(+), 3 deletions(-)      # hunk: :437 +1, :1443 +2

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3

$ git status --porcelain -- docs/discovery/capability-map.md reports/evidence/m0/0a/
(출력 없음 — 커밋 후)
```

- exit: 0. 2,621 → **2,624줄**(STR-08 `사용자 가치` +1, NOTI-02 `경계` +2). secret 매치
  3건 불변(일반 명사).

### G7. L-1 — 리뷰 range의 in_scope 밖 커밋 재확인

```
$ git log --oneline 3dc7d26..HEAD -- .claude/ CLAUDE.md
d7b1c10 chore(harness): codex 리뷰 호출에 model_reasoning_effort=high 고정
0c7eeff chore(harness): 정본 verdict 스키마의 line을 null 허용으로 — strict 변형과 계약 일치
1f8e57c chore(harness): codex --output-schema용 strict 변형 스키마 추가

$ git show d7b1c10 --stat
 .claude/skills/codex-review-gate/SKILL.md | 9 +++++++++
 CLAUDE.md                                 | 1 +

$ git diff --name-only 3dc7d26 HEAD | grep -v '^docs/discovery/\|^reports/evidence/'
.claude/skills/codex-review-gate/SKILL.md
.claude/skills/codex-review-gate/references/codex-output.strict.schema.json
.claude/skills/codex-review-gate/references/codex-verdict.schema.json
CLAUDE.md
```

- exit: 0. 무관 커밋은 **3건**(라운드 3·4 절의 "2건"은 `d7b1c10` 이전 기준이라 낡았다),
  파일은 **4개**로 불변이다. `scope.md` 라운드 5 절에서 갱신했다. 셋 다 하네스 소관이며
  **이 slice가 만든 커밋이 아니다.**

## 2026-08-26 — 수정 라운드 6 검증 (verifier `not-ready` M-3 + low 6건, 최종 수정 라운드)

### H1. M-3 — 축이 원리적으로 볼 수 없는 위치의 실물 잔존

NOTI-05 `사용자 가치`(`:1530-1531`)가 "워커가 죽은 시점의 알림은 사람이 챙기지 않으면
오지 않는다"로 무조건 서술해 `OPEN-NOTI-02`의 (b) at-least-once를 배제했다. 같은 블록
`분류` 줄이 그 OPEN을 스스로 지목하고(`:1532`), OPS-03(`:1997-2001`) 조건부 묶음이 (b)를
살아 있게 유지한다.

```
$ sed -n '1528,1533p' docs/discovery/capability-map.md   # 수정 후
### NOTI-05 · 배달 outbox (커밋 경계를 넘는 전달)

- **사용자 가치**: 롤백된 판단에 대한 알림이 나가지 않는다. 반면 워커가 죽은 시점의
  알림은, **at-most-once를 유지하면** 사람이 챙기지 않으면 오지 않는다(전달 의미는
  `OPEN-NOTI-02`).
- **분류**: `V2 필수` (전달 의미 정책은 `OPEN-NOTI-02`)
```

- exit: 0. 어법은 라운드 5의 M-2 수정(STR-08 `사용자 가치`)과 **대칭**이다 —
  한정어 + 서술 + (미정 축은 `OPEN-*`). `OPEN-NOTI-02`는 해소하지 않았다.

**축이 왜 못 봤는지 — 실측**. 지시대로 **새 축을 만들지 않았다.** 대신 이 사례를
`checklist.md` §11.3 한계 3("어휘가 겹치지 않게 같은 쟁점을 확정하는 서술")의 **실물
사례**로 기록한다.

```
$ python3 sweep_r5.py <rev> x     # NOTI-05 사용자 가치 × OPEN-NOTI-02 만 강제 출력
수정 전(a72e247):  중첩 0  | 겹침: []
수정 후(HEAD):     중첩 3  | 겹침: ['NOTI', 'at', 'most']
```

- **중첩 0이다.** §12 행은 "중복 알림 / 놓친 알림"이라 쓰고 절은 "사람이 챙기지 않으면
  오지 않는다"라고 쓴다 — **완전한 환언**이라 토큰 기반 축이 **원리적으로** 볼 수 없다.
- F2'도 못 본다: NOTI-05의 acceptance에 조건부 묶음이 없어 `COND` 게이트에서 탈락한다.
- **또다시 "수정이 점수를 올린다"**: 0 → 3. G1(STR-08 2→5·7)·G4에 이은 **세 번째 독립
  확인**이며, 이 축들이 결함 탐지기가 아니라는 근거가 하나 더 늘었다.
- **verifier가 M-3을 찾은 방법은 축이 아니라 읽기다** — §12에서 소유 capability가 명시된
  활성 OPEN 전부를 뽑아 그 블록의 `사용자 가치`·`경계`·`V2 제약`을 직접 읽었다(나머지
  17건 전수 판독 결과 M-3 외 추가 없음). M-1·M-2를 찾은 것과 같은 방법이다.

### H2. 기존 축 재실행 — 새 적출 0건

지시대로 축을 늘리지 않고 F1·F2'·X를 불변 확인용으로만 재실행했다.

```
$ python3 f1.py docs/discovery/capability-map.md
조건부 보유 블록: 7 | 중첩 3개 이상: 7건

$ python3 sweep_r5.py docs/discovery/capability-map.md f2
  [STR-08]×2 · [ML-03] · [OPS-03] · [OPS-09]      적출: 5 건

$ python3 sweep_r5.py docs/discovery/capability-map.md x
확정 서술 절 수: 170 | 활성 OPEN: 66 | 중첩 5 이상 적출: 15

$ python3 - <<'PY'   # 라운드 3 계열 4축 (블록 스윕 · 어휘 중첩 · 기본값 단정 · 측정 불가)
PY
acceptance 절 보유 블록: 67 | 조건부 묶음 8 | 무조건 항목 225 | 활성 OPEN 66
[A] 어휘 중첩 4개 이상: 4쌍 (SET-06×2, OPS-13×2)
```

- exit: 0. **F1 7 / F2' 5 / X 15 / [A] 4쌍 — 라운드 5와 전부 동일. 새 적출 0건.**
  M-3 수정은 기존 항목에 한정어를 붙였을 뿐 항목을 추가·삭제하지 않았다.

### H3. 분류·acceptance·ID·OPEN 불변 재검증

```
$ python3 invariants.py
분류: {'V2 필수': 63, '근거 부족': 11, '폐기': 6, '후속': 14} = 94
형식 위반: none
V2 필수 사용자 가치/acceptance 결측: none
중복 ID: none
§12 등록 OPEN: 66 | 본문 참조 - 표 등록: ['OPEN-NOTI-03', 'OPEN-OPS-06', 'OPEN-SET-07']
총 OPEN id(등록+결번): 69

$ python3 - <<'PY'   # OPEN id 집합을 a72e247과 대조
PY
a72e247: 66 | worktree: 66 | 소멸: 없음 | 신규: 없음
```

- exit: 0. **전부 불변.** `OPEN-NOTI-02`·`OPEN-STR-08`·`OPEN-ML-03` 전부 유지.

### H4. 규모·secret 스캔·clean tree

```
$ wc -l docs/discovery/capability-map.md
2625

$ git diff --stat a72e247 -- docs/discovery/capability-map.md
 1 file changed, 2 insertions(+), 1 deletion(-)

$ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ | wc -l
3

$ git status --porcelain -- docs/discovery/capability-map.md reports/evidence/m0/0a/
(출력 없음 — 커밋 후)
```

- exit: 0. 2,624 → **2,625줄**(NOTI-05 `사용자 가치` +1). secret 매치 3건 불변(일반 명사).

### H5. evidence 정정 L-4~L-9

| # | 처리 | 위치 |
| --- | --- | --- |
| **L-4** | **고치지 않고 미이행을 명시**했다(verifier 권고). 라운드 3 E1~E4와 라운드 4 F2 스크립트는 미기입이며, 그 사실과 사유(라운드 3 절 소급 편집이 감사 추적에 더 나쁘다)를 §11 표 L-2 행에 적었다 | `checklist.md` §11 표 |
| **L-5** | 라운드 4 F2 절에 F1과 **같은 형식의 `⚠ 정정` 블록** 추가. "적출 3건"은 위치 버그로 인한 과소 계수이고 버그만 고치면 **4건**(+OPS-03 `경계`)임을 실측으로 확인해 기입. **verifier 자기 정정**(라운드 4 §2.2의 "접합 규칙 차이"가 아니라 이 위치 버그였고 verifier 재구현이 맞았다)도 함께 반영 | `commands.md` F2 절 |
| **L-6** | §11.3 한계 2에 "**F2'도 M-1을 못 본다**"를 덧붙였다. 실측: F2'를 `23b9c2a`에 돌리면 적출 4건(STR-08·ML-03·OPS-03·OPS-09)이고 NOTI-02는 없다 — `COND` 게이트(acceptance에 조건부 묶음이 있을 것)에서 탈락하기 때문. 원인을 위치 버그 하나로 귀속한 라운드 5 서술이 불완전했다 | `checklist.md` §11.3 |
| **L-7** | G4 임계 표에 **기준 리비전**을 명시했다 — 수정 전 `23b9c2a`(`0b0c8aa`도 동일), 수정 후 `0581e97`. 두 리비전에서 4행 전부 재현된다. verifier 재구현이 `23b9c2a`에서 15·36을 낸 것은 구현 차이이며 논지는 불변임을 병기했다. **H-1이 정확히 이 계열(기준 미표기로 인한 귀속 오류)이었으므로 명시한다** | `commands.md` G4 |
| **L-8** | §2의 라운드 1 "전수 확인" 주장에 주석을 달았다 — 그 확인은 **`acceptance` 절에 한정**되며 부재의 증거가 아니다. 이후 라운드의 잔존(라운드 2·3 Codex finding, 라운드 4 M-1·M-2, 라운드 5 M-3)이 **전부 acceptance 밖의 절**에 있었음을 적고 §11.3으로 연결했다 | `checklist.md` §2 |
| **L-9** | 존재하지 않는 `§10.3` 참조 2곳을 **`§11.1`**로 교체. §11.1이 부르던 채택 근거 옛 번호 "(1)·(3)"을 재작성된 §10.1의 **(1)·(2)**에 맞췄다 | `checklist.md` §10.1·§10.2·§11.1 |

```
$ grep -n '§10\.3' reports/evidence/m0/0a/checklist.md
491:| **L-9** | low | 죽은 `§10.3` 참조 2곳 → `§11.1`. …
```

- exit: 0. 남은 매치 **1건은 죽은 참조가 아니라 위 §12 표 L-9 행의 처리 서술**이다
  (`§10.3` → `§11.1`로 고쳤다고 적은 문장 자체). 실제 **참조**로 쓰이던 2곳(§10.1 정정
  블록, §10.2 표 F1 행)은 `§11.1`로 교체됐고 죽은 참조는 0이다.
  초안에는 이 명령의 출력을 `0`으로 적었으나 실행해 보니 1이었고, **H-1과 같은 계열의
  미검증 기재가 되지 않도록 실제 출력으로 교체했다.**
