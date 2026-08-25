# M0 / 0A — 완료 조건 대조 checklist

- 작성: 라운드 0 → 수정 라운드 1 → **수정 라운드 2(2026-08-22, Codex 재리뷰 `request_changes` 대응)**
- 산출물: `docs/discovery/capability-map.md` (2,591줄)
- 함께 볼 것: `commands.md`(리뷰 메타데이터·라운드별 검증 명령),
  `codex-review-20260822T061532Z.json`·`codex-review-20260822T065525Z.json`(finding 원문)
- 기준: `reports/evidence/m0/0a/scope.md`의 `acceptance_commands` 4항목
  (= `milestone-0.md` 완료 조건 중 0A 담당 항목)
- 규격: `.claude/skills/evidence-pack/SKILL.md` §"문서 slice(M0)의 evidence"

## 1. acceptance 항목별 대조

### A1. V2 필수 capability마다 사용자 가치와 acceptance scenario가 존재

- 판정: **충족**
- 충족 근거: `docs/discovery/capability-map.md` — `V2 필수` 63건 각각에
  `**사용자 가치**` 한 줄과 `**Acceptance scenario**` 절이 있다.
- 검증 방법 (재현 가능). capability 블록별로 분류·사용자 가치·acceptance 유무를 대조한다.
  분류 추출은 A3의 엄격 검증기와 같은 방식(백틱 토큰 전수)을 쓴다.

  ```
  $ python3 - <<'PY'
  import re
  txt=open('docs/discovery/capability-map.md',encoding='utf-8').read()
  blocks=re.split(r'\n(?=### )', txt)
  v=a=tot=0; nov=[]; missing=[]
  for b in blocks:
      m=re.match(r'### ([A-Z]+-\d+) · ', b)
      if not m: continue
      line=re.search(r'^- \*\*분류\*\*: *(.+)$', b, re.M)
      if not line: continue
      tot+=1
      lab=[t for t in re.findall(r'`([^`]+)`',line.group(1))
           if t in ('V2 필수','후속','폐기','근거 부족')][0]
      if '사용자 가치' in b: v+=1
      else: nov.append(m.group(1))
      if 'Acceptance scenario' in b: a+=1
      if lab=='V2 필수' and ('Acceptance scenario' not in b or '사용자 가치' not in b):
          missing.append(m.group(1))
  print('capabilities:',tot,'| 사용자 가치:',v,'| acceptance:',a)
  print('V2 필수 missing:',missing or 'none')
  print('no-value:',nov)
  PY
  capabilities: 94 | 사용자 가치: 85 | acceptance: 64
  V2 필수 missing: none
  no-value: ['QUAL-12', 'NOTI-11', 'OPS-14', 'OPS-15', 'OPS-16', 'OPS-17', 'OPS-18', 'OPS-19', 'OPS-20']
  ```

  - **실패 이력 1(라운드 0)**: 최초 실행에서 `OPS-13`이 acceptance 누락으로 검출됐다.
    scenario 5개를 추가한 뒤 재실행해 `none`이 됐다.
  - **실패 이력 2(라운드 1)**: 이전 라운드의 검증기는 분류를 첫 백틱만 잘라 읽었다.
    Codex finding 3이 지적한 이중 분류를 놓친 원인이며, A3에서 고쳤다. 위 A1 스크립트도
    같은 방식으로 교체했다.
  - `사용자 가치` 85건은 `V2 필수` 63건 전부를 포함한다. 미기재 9건은 위 `no-value`
    목록이며 전부 `폐기`(QUAL-12, OPS-14~17) 또는 `근거 부족`·`후속`으로, 사용자 가치가
    없거나 조사 범위 밖이라 판정한 항목이다. 완료 조건은 `V2 필수`만 요구한다.
  - `acceptance` 64건 = `V2 필수` 63건 + **NOTI-08**(`후속`이지만 "즉시 승계할 계약"인
    금액 라벨 basis 부분에 `- **Acceptance scenario (승계 계약 부분)**` 절을 둔다).
    이전 라운드가 이 64번째를 STR-11로 귀속시킨 것은 오류다(verifier F-2) — STR-11은
    `Acceptance scenario`라는 문자열 대신 `채택 시 요구되는 관찰 가능 동작`을 쓴다.

- 관찰: acceptance scenario는 `후속`·`근거 부족` 일부 항목에도 부여했다(채택 시 요구되는
  관찰 가능 동작). `폐기` 항목에는 부여하지 않고 대체 설계만 기록했다 — 완료 조건은
  `V2 필수`만 요구한다.
- 관찰 가능성: 모든 scenario를 "무엇이 관측되는가"(건수, 호출 횟수, 표시 값, 타입 구분,
  실패 여부)로 서술했다. 내부 구현 상태를 기대값으로 쓴 항목은 없다.

### A2. Kotlin service 범위에 Python 파일/endpoint를 그대로 옮기는 작업 항목이 없음

- 판정: **충족**
- 충족 근거
  - `capability-map.md` §0.2가 이를 문서 지위로 명시한다 — "파일:라인 인용은 관찰 사실의
    출처일 뿐 이관 대상 지정이 아니다", "기존 endpoint 수·함수 수를 V2 요구사항 수로
    간주하지 않는다".
  - §10 집계 주석: "94라는 수는 사용자 가치 단위의 분해 결과이지 V2 구현 단위 수도,
    기존 endpoint·함수 수도 아니다."
  - 모든 capability 제목이 파일명·클래스명·endpoint가 아니라 사용자 문장이다
    (예: "당일 등록된 신규 공고를 자동으로 받는다", "배우지 않은 공종에 답하지 않는다").
  - `V2 필수` 항목 중 legacy 구조를 버리는 것들은 `legacy 형태 처리` 줄로 무엇을 채택하지
    않는지 명시했다.
- 예외 명시: **ML 축만** 코드 이식이 전략이며(v2-지침서 §1·§3.2), 그 범위가 ML-11에
  모듈 단위로 한정돼 있다. ML-11.3이 잘라낼 결합을, ML-11.4가 이식 제외 대상을 명시한다.
  ML 축에서도 "재활용은 구현 전략이지 기대값 판정 근거가 아니다"를 §4 서두와 §11.4에
  이중으로 기록했다.

### A3. 각 capability가 V2 필수 / 후속 / 폐기 / 근거 부족 중 하나로 분류됨

- 판정: **충족**
- 충족 근거: capability 94건 전부에 `**분류**:` 줄이 있고, 그 줄이 4개 어휘 중 **정확히
  하나만** 백틱으로 포함한다.

  | 분류 | 건수 |
  | --- | ---: |
  | V2 필수 | 63 |
  | 후속 | 14 |
  | 근거 부족 | 11 |
  | 폐기 | 6 |
  | **계** | **94** |

- 검증 명령과 결과 (2026-08-22 재실행). **이전 라운드의 검증기는 첫 백틱만 잘라
  비교해 두 번째 분류 어휘를 놓쳤다**(Codex finding 3). 분류 줄의 모든 백틱 토큰을
  추출해 (a) 분류 어휘가 정확히 하나이고 (b) 그것이 첫 토큰인지 검사하도록 고쳤다.
  전체 스크립트와 축별 출력은 `commands.md` C1에 있다.

  ```
  TOTAL 필수/후속/폐기/근거부족 = 63 14 6 11 = 94
  형식 위반: none
  V2 필수 missing: none
  ```

  `capability-map.md` §10의 축별 표 합계와 일치한다.
- **실패 이력**: 고친 검증기의 첫 실행에서 형식 위반 5건이 검출됐다.
  - `STR-13` — 실제 이중 분류(`후속` + `근거 부족`)였다. 추천 산출(STR-13, `후속`)과
    무승인 자동 반영(STR-15, `근거 부족`)으로 **capability를 분리**했다. 두 항목은 사용자
    가치도 판정 근거도 다르다. 집계가 93 → 94, `근거 부족` 10 → 11로 바뀌었다.
  - `QUAL-07` / `DEC-09` / `NOTI-06` / `OPS-09` — 분류는 하나이고 부가 설명이 **다른
    주체**(다른 capability의 분류, legacy 구현의 처리)를 가리키는 경우였다. 설명을
    `분류 근거` / `경계` / `V2 대체` 줄로 옮겨 분류 줄을 순수하게 만들었다. QUAL-07은
    "reason code 표준화는 DEC-06 소관이며 그쪽에서 V2 필수"라는 정보를 `경계` 줄에
    보존했다.
  - 재발 방지: 분류 줄의 형식 규칙을 `capability-map.md` §0.3에 명문화했다.
- 설계 입력 절(ML-11, DEC-11~13, SET-07~10, OPS-00, OPS-21)은 capability가 아니므로 분류
  대상에서 제외했고, 그 사실을 §10에 명시했다.

### A4. 모든 분류에 근거 파일/commit 인용이 존재

- 판정: **충족**
- 충족 근거: 94건 전부에 `**분류 근거**:` 줄이 있고, legacy 파일:라인 또는 commit
  해시를 인용한다. legacy-scout 노트의 인용을 그대로 보존했다. 블록 단위 확인은 A1·A3의
  파싱 스크립트가 담당한다(분류 줄이 있는 블록 94개 전수).
- 인용 밀도(2026-08-22 재측정)

  ```
  $ grep -c '분류 근거' docs/discovery/capability-map.md
  98
  ```

  - **이전 라운드가 이 값을 93으로 기록한 것은 오류다**(verifier F-1). 이 명령은 **행
    수**를 세며 capability 수가 아니다 — 변형 라벨(`분류 근거 —`, `분류 근거(…)`)이 일부
    블록에 복수로 존재해 블록 수보다 크다. 실질 주장인 "94 블록 전부에 분류 근거가 있다"는
    행 grep이 아니라 블록 파싱으로 검증한다.
  - verifier가 표본 16건을 `git show ed4b06c:<file>`로 원본 대조해 **16/16 일치**,
    commit 인용 26건이 **26/26 실존**함을 독립 확인했다(`_workspace/m0-0a/02_verifier_report.md` §2.5).
- 기준 SHA 표기: 문서 헤더에 `ed4b06c`(대다수 노트)와 `ddee938`(scout-collection, koneps
  경로 최신)를 병기하고 "인용 검증 시 각 항목의 출처 노트를 함께 본다"를 명시했다.
- 근거가 없거나 상충하는 항목은 인용을 지어내지 않고 `근거 부족` + OPEN으로 처리했다
  (§9.1 상충 근거 표 6건).

## 2. OPEN 결정 잔여 수

- **잔여 `OPEN` 66건** (`capability-map.md` §12).
  - G1 도메인 의미론(외부 공식 정의 확인 필요): 7
  - G2 정책 값·임계 근거: 10
  - G3 V2 범위 결정: 16
  - G4 legacy 동작이 의도인가 결함인가: 17
  - G5 미검증 수치 재측정: 9
  - G6 외부 확인 / ADR 연계: 7
- 수정 라운드 1에서 **`OPEN-SET-10`**(64 → 65), 라운드 2에서 **`OPEN-NOTI-08`**(65 → 66)을
  신설했다. 둘 다 legacy 동작을 확정하는 대신 근거 부재를 사용자 결정으로 올린 것이다 —
  전자는 성숙도 시간축 대체 정책, 후자는 늦게 확정된 낙찰/패찰 판정의 재통지 여부다.
- 이 slice에서 통합·해소된 항목 3건(`OPEN-OPS-06` 해소, `OPEN-NOTI-03`·`OPEN-SET-07` 통합)은
  §12.1에 결번 사유와 함께 기록했다.
- 검증: `grep -oE 'OPEN-(COL|STR|QUAL|ML|DEC|NOTI|SET|OPS|NUM)-[0-9]+' … | sort -u | wc -l`
  → 69 (활성 66 + 결번 3).
- **acceptance가 OPEN을 암묵적으로 해소하지 않는지**를 수정 라운드 1에서 전수 확인했다
  (`commands.md` C2). Codex 지목 4곳 + 자체 발견 2곳을 조건부 시나리오로 재서술했고,
  규약을 `capability-map.md` §0.5에 명문화했다.
- **milestone-0.md 완료 조건은 "`OPEN` 0개이거나 사용자가 명시적으로 다음 단계 진행을
  승인"이다. 현재 0개가 아니므로 사용자 승인이 필요하다.** 우선 결정 권고 대상은 G3와
  G1이다(M1 이후 설계를 좌우).

## 3. 다른 slice 소관 (pending)

`milestone-0.md` 완료 조건 중 0A가 담당하지 않는 항목:

| 완료 조건 | 소관 | 상태 |
| --- | --- | --- |
| ML 재활용 대상이 모듈 단위로 식별되고 잘라낼 결합이 명시됨 | 0A 부분 + 0D ADR | **0A 부분 충족** — `capability-map.md` ML-11.1/11.3에 모듈 목록과 결합 지점 기록. ADR 확정은 0D |
| 모든 도메인 숫자의 unit/basis/provenance가 정의되거나 OPEN | 0C 데이터 사전 | pending. 0A는 DEC-11(Rate 밴드 인벤토리)과 SET-07("정산됨" 4정의)을 §13으로 인계 |
| 기존 회귀마다 V2 예방책이 최소 하나 | 0B regression ledger | pending. 0A는 회귀 사례와 예방 제약 후보를 §13으로 인계 |
| Python 결과가 정답이 아니라 참고임을 모든 관련 문서가 일관되게 명시 | 0A~0D 공통 | **0A 충족** — §0.2, §11, ML 축 서두, §11.4에 기록 |
| `fixtures/manifest.yaml` schema와 후보 목록 | fixture-curator | pending. 0A는 §11.4로 인계 |

## 4. 규격상 N/A 항목 (evidence-pack §"문서 slice")

| 항목 | 판정 | 사유 |
| --- | --- | --- |
| `commands.md` | **존재** | 라운드 0에서는 이 `checklist.md`로 대체했으나(evidence-pack 규격), 리뷰 라운드 메타데이터와 라운드별 검증 명령·결과를 기록할 곳이 필요해 수정 라운드 1에서 신설했다. acceptance command는 여전히 N/A다 |
| `differential.json` | N/A | Python/V2 실행 결과 비교가 없는 문서 slice다. 이 slice는 legacy 동작을 **관찰로만** 기록하며 V2 산출물이 없어 비교 대상이 성립하지 않는다 |
| `golden-manifest.json` | N/A | fixture를 사용하지 않는다. fixture 후보 인계만 했다(§11.4) |
| `rollback.md` | N/A — 문서 산출물은 `git revert`로 복구 | 신규 wiring이 없다 |
| `codex-review-*.json` | **존재** | `codex-review-20260822T061532Z.json` (라운드 1, `request_changes`). codex-reviewer 레인이 작성했고 spec-writer는 읽기만 한다(append-only) |

## 5. secret 스캔

- 명령 (2026-08-22 수정 라운드 1 재실행). 이전 라운드는 규격 패턴에서
  `BEGIN (RSA|EC|OPENSSH)` 대안을 뺀 부분집합으로 돌렸다(verifier F-6). **규격 전체
  패턴**으로 재실행했다.

  ```
  $ grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" docs/discovery/ reports/evidence/m0/0a/
  docs/discovery/capability-map.md:144:  관찰된 실패 문구는 HTTP 429 "API token quota exceeded"이고, 대응책이 4겹으로
  docs/discovery/capability-map.md:1524:- **분류 근거**: webhook secret 검사가 **조건부**다 — 채널이 설정돼 있고 secret이 설정된
  docs/discovery/capability-map.md:1531:  - webhook secret 미설정 상태의 미인증 호출이 **거부된다**. ← legacy는 통과시킨다.
  reports/evidence/m0/0a/codex-review-20260822T061532Z.json:46: (Codex가 실행한 스캔 명령 문자열)
  reports/evidence/m0/0a/checklist.md:  (이 절의 인용과 육안 확인 항목명)
  reports/evidence/m0/0a/commands.md:  (C3의 인용)
  ```

- `BEGIN (RSA|EC|OPENSSH)` 추가분 매치는 **0건**이다.
- evidence 파일 매치는 **스캔 명령 문자열과 이전 결과 인용**이며 새로운 비밀값이 아니다.
- 판정: **통과.** `capability-map.md` 3건 모두 비밀값이 아니라 **일반 명사**다.
  - 144행: 외부 API가 반환하는 오류 문구를 관찰 사실로 인용(값 아님).
  - 1524·1531행: webhook 인증 메커니즘 설명. 설정 키 이름조차 옮기지 않았고 값은 없다.
- 육안 확인 (패턴 스캔으로 잡히지 않는 항목)
  - **Telegram chat id / bot token**: 없음. 채널을 "대화형 채널"·"Telegram"으로만 지칭하고
    식별자 값을 옮기지 않았다. 상류 `01_scout_notification.md`도 같은 정책을 명시했다.
  - **사업자 정보 / 상호 / 사업자번호**: 없음. SET-01은 "상호 정규화"를 규칙으로만
    기술하고 실제 상호를 옮기지 않았다. legacy 회귀 테스트에 등장하는 상호 예시는 인용하지
    않았다.
  - **수신자 이메일**: 없음.
  - **공고번호**: 개별 공고번호를 옮기지 않았다(상류 노트에는 일부 존재하나 이 문서에는
    인용하지 않았다).
  - **금액 실값**: 개별 공고의 금액을 옮기지 않았다. 인용한 수치는 집계·비율·건수뿐이다.
  - **credential / URL / 호스트명**: 없음.
- 독립 확인: verifier가 같은 스캔과 별도 PII 스캔(email / URL / 사업자번호 / 9자리 이상
  숫자 id / 상호)을 돌려 **전부 0건**을 확인했다(`_workspace/m0-0a/02_verifier_report.md` §2.8).

## 6. 커밋

- in_scope 파일만 커밋한다: `docs/discovery/capability-map.md`,
  `reports/evidence/m0/0a/`.
- scope 밖(`_workspace/`, 다른 discovery 문서, ADR)은 커밋하지 않는다.
- 커밋 SHA는 `scope.md`의 `head_sha`에 기입한다. **커밋이 자기 자신의 SHA를 담을 수
  없으므로 amend가 아니라 후속 커밋으로 기입한다** — 라운드 0에서 실제로 그렇게 했고
  (`ff754c2`), 리뷰 range 해석 방법을 `scope.md` 갱신 이력에 적었다. 이전 라운드의
  checklist가 "amend"라고 적은 것은 갱신 전 서술이 남은 것이다(verifier F-3).
- 수정 라운드 1은 finding별로 커밋을 나눈다(`scope.md` 갱신 이력 참조).

## 7. 수정 라운드 1 — finding별 처리

Codex `request_changes` (blocker 0 / high 2 / medium 2). finding 원문은
`codex-review-20260822T061532Z.json`, 검증 명령·출력은 `commands.md`에 있다.

| # | severity | 처리 | 산출물 변화 |
| --- | --- | --- | --- |
| 1 | high | OPEN이 미해결인 동작을 확정하던 acceptance를 **조건부 시나리오**로 재서술. Codex 지목 4곳(QUAL-03 / STR-08 / ML-03 / NOTI-04) + 전수 grep으로 찾은 2곳(DEC-02 / OPS-03). OPEN은 하나도 해소하지 않았다 | §0.5 규약 신설, 조건부 항목 보유 capability 6개 목록 |
| 2 | high | SET-06의 시간축 침묵 fallback 제거. provenance 보존·결측의 명시적 산출·대체 영향 관측 3항목으로 acceptance 재서술 | `OPEN-SET-10` 신설(64 → 65) |
| 3 | medium | STR-13 이중 분류를 STR-13(`후속`) / STR-15(`근거 부족`)로 분리. **검증기를 백틱 토큰 전수 검사로 교체**해 같은 결함 4건을 추가 검출·수정 | capability 93 → 94, §0.3 형식 규칙 신설 |
| 4 | medium | ML-07의 절차 규율 1건을 acceptance에서 분리하고, 정책 산출물 계약·완화 불가·version 기록 3항목의 관측 가능한 시나리오로 교체 | 절차 규율은 `작업 규율(acceptance 아님)` 줄로 이동 |

verifier low 6건 중 함께 처리한 것:

| # | 처리 |
| --- | --- |
| F-1 | `grep -c '분류 근거'` 기록값 93 → 실제값 98로 정정하고, 그 명령이 행 수를 센다는 사실과 실질 검증은 블록 파싱이라는 점을 명시 |
| F-2 | acceptance 64번째의 귀속을 STR-11 → **NOTI-08**로 정정 |
| F-3 | 커밋 방식 서술을 amend → 후속 커밋으로 정정(라운드 0의 실제 동작) |
| F-4 | ML-04에 측정일 `2026-08-11` 보완(원본 `assessment_shrinkage.py:6`). §11.2를 "측정일이 확인된 항목"과 "`(측정일 미기재)`"로 분리하고 미기재 4건을 표시. 라벨만 "측정일 병기"이고 날짜가 없던 COL-07·STR-09도 정정 |
| F-6 | secret 스캔을 evidence-pack 규격 전체 패턴(`BEGIN (RSA\|EC\|OPENSSH)` 포함)으로 재실행·기록. 추가 매치 0건 |
| F-5 | **미처리.** 디렉터리 없는 파일명 154건의 다중 해석 가능성. 인용 표기 규약 변경이라 문서 전반 재작성이 필요하고, verifier 표본 검증에서 오해석 0건·미해석 0건이었다. 후속 라운드 또는 0B 작성 시 표기 규약을 함께 정하는 것을 권고한다 |

## 8. 수정 라운드 2 — finding별 처리

Codex 재리뷰 `request_changes` (blocker 0 / high 4 / medium 1). 라운드 1의 finding 4건은
해소로 확인됐고 이번 4건은 신규다. 원문은 `codex-review-20260822T065525Z.json`, 검증
명령·출력은 `commands.md` D1~D5에 있다.

| # | severity | 처리 | 산출물 변화 |
| --- | --- | --- | --- |
| 1 | high | OPS-09 acceptance를 §0.5 조건부 형식으로 전환. 무조건 5항목(구조화 신호 분류, `Unclassified`의 별도 값 관측, 재시도 가능성의 **선언 여부** 검증)과 조건부 1항목(`OPEN-OPS-01` 정책 질문)으로 분리. V2 제약의 단정도 제거 | §0.5 조건부 보유 목록 6 → 7 |
| 2 | high | OPS-04에서 측정 불가의 초록 변환을 제거. 정상 / 임계 초과 / **측정 불가(중립)** 3-상태로 분리하고, 표시 계층에서도 성공 색상으로 렌더링되지 않음을 acceptance로 고정 | `legacy 형태 처리` 신설, V2 제약에 상태→표시 매핑 추가 |
| 3 | high | NOTI-04의 사용자 가치를 실제 배달되는 것으로 재서술하고, 금액-선도착 경로의 **가치 공백을 명시**. 재통지 여부는 근거가 없어 `OPEN-NOTI-08`로 신설하고 acceptance를 조건부화 | OPEN 65 → 66, §0.5에 복수 조건부 묶음 표기 규칙 추가 |
| 4 | high | OPS-13 acceptance에 v2-지침서 §5의 크기 외 축 전부 추가 — fan-in/fan-out, public API 예산, 순환 의존, duplicate helper, 복잡도, `domain <- application <- adapters/app` 의존 방향. **크기를 한도 안에 유지한 채로도** 실패해야 함을 명시 | 크기 외 축의 근거(legacy mixin god-class 사례) 추가 |
| 5 | medium | **범위 밖.** `.claude/skills/` 하위 스키마 계약 문제로 하네스 소관이며 팀 리드가 별도 해소했다 | — |

라운드 2 불변 확인: capability 94건(추가·분리 없음), 분류 63/14/6/11, 분류 줄 형식 위반
0, `V2 필수` 사용자 가치·acceptance 결측 0, 중복 ID 0, secret 스캔 신규 매치 0.

전수 스윕(`commands.md` D1)에서 신규 OPEN 2건을 포함해 다시 대조한 결과 finding 1의
OPS-09 외에 추가 위반은 없었다. SET-06(`OPEN-SET-10`)은 "승인된 대체 출처"라는 중립
표현이라 어느 결정에도 성립하고, STR-15(`OPEN-STR-04`)는 이미 조건부 형식이다.

## 9. 수정 라운드 3 — finding별 처리

verifier 재검증 판정 `not-ready`(`_workspace/m0-0a/04_verifier_report_round2.md`). Codex
라운드 2 finding 4건은 verifier가 전부 충족으로 확인했고, `not-ready` 사유는 M-1 한
건이었다. 사용자가 M-1 + low 3건 전부 수정을 승인했다. 검증 명령·출력은 `commands.md`
E1~E7에 있다.

| # | severity | 처리 | 산출물 변화 |
| --- | --- | --- | --- |
| M-1 | medium | OPS-00 acceptance의 "깊이를 읽지 못한 경우는 초록 + 사유로 구분된다"를 "정상과 구별되는 **측정 불가(중립)**로 기록된다"로 교체하고, 3-상태 정의와 표시 계층 제약의 소유가 OPS-04임을 참조로 걸었다. 라운드 2에 Codex high #2로 고친 OPS-04, 그리고 OPS-21 B-13(측정 불가 어휘는 DEC-04 소유 · OPS-04 동형 적용)과 정렬 | `capability-map.md` 라운드2 1899-1900 → 라운드3 1914-1917 |
| L-1 | low | `OPEN-NOTI-08`의 근거 부재 서술을 원본에 맞게 정밀화. "재통지가 의도인지 근거를 찾지 못했다" → "**구현 의도**는 docstring에 '재통지 금지'로 명시돼 있으나, 그 선택이 **운영자 요구와 합의된 근거**는 없다". NOTI-04 본문과 §12 행 두 곳 모두. 인용은 `git show ed4b06c:app/services/award_notifications.py`로 직접 대조해 `:210-221`(해당 문장 `:216-217`)로 기입 — verifier 권고의 `:201-217`은 `def` 줄부터 세었고, docstring 자체의 범위는 210-221이다 | OPEN 판단 불변(`OPEN-NOTI-08` 유지) |
| L-2 | low | 전수 스윕 정의를 "분류 줄 보유 + `- **Acceptance scenario**` bullet" → "**acceptance 절을 가진 모든 블록**(분류 유무 무관, bullet 형식 무관)"으로 재정의하고 실제로 재실행. 정의·스크립트·출력을 `commands.md` E1에 기록. OPEN을 인용하지 않으면서 쟁점을 확정하는 유형에 쓴 어휘 중첩 스캔도 E2에 기록 | 스윕 대상 63 → **67 블록** (+OPS-00, +STR-11·STR-15, +NOTI-08 변형 제목) |
| L-3 | low | §0.5에 acceptance 절 제목 형식 3종을 규약으로 명시 — (1) `- **Acceptance scenario**`(범위 괄호 허용, NOTI-08), (2) `근거 부족` 항목의 `- **채택 시 요구되는 관찰 가능 동작**`(블록 전체가 채택 조건에 걸리므로 조건부 목록에 넣지 않음, STR-11·STR-15), (3) 설계 입력 절의 비-bullet 제목(OPS-00). 전수 스윕은 셋 모두를 대상으로 함을 명시 | §0.5에 문단 1개 추가 |

라운드 3 불변 확인(재실행): capability **94**건, 분류 **63/14/6/11**, 분류 줄 형식 위반
**0**, `V2 필수` 사용자 가치·acceptance 결측 **0**, 중복 ID **0**, §12 등록 OPEN **66**
(전체 id 69 = 활성 66 + 결번 3), secret 스캔 매치 **3**(라운드 0부터 동일한 일반 명사,
신규 0). `capability-map.md` 2,591 → **2,608줄**.

**OPEN id 집합 대조**: `0b48eaa`(66) → 라운드 3(66), **소멸 0 · 신규 0**. 이번 라운드는
OPEN을 해소하지도 신설하지도 않았다 — L-1은 근거 부재의 *서술*만 정밀화했고 OPEN 판단
자체는 그대로 유지했다.

**재정의한 스윕의 신규 발견**: 없음. 구조적으로 새로 편입된 4블록 중 M-1 계열이 남아
있던 것은 OPS-00 하나뿐이었고 이번 라운드에 고쳤다. 어휘 중첩 4개 이상 쌍 4건은 전부
검토 후 무해로 판정했다(`commands.md` E2).

**이월(이번 범위 밖, 미처리)**: 라운드 1 verifier L-3~L-6 중 §0.5 형식 규약(L-3)은 이번에
처리했고, 나머지 — QUAL-03의 항목 배치, SET-06 신규 근거의 파일:라인 부재, checklist A1의
실패 이력 누락 — 와 라운드 0의 F-5(디렉터리 없는 파일명 표기 규약)는 그대로 이월한다.
사유와 권고는 §7에 있다. 0B 또는 후속 라운드에서 처리한다.
