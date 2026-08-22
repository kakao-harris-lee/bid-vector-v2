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
