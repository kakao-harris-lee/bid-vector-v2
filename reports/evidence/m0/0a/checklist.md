# M0 / 0A — 완료 조건 대조 checklist

- 작성일: 2026-08-22
- 산출물: `docs/discovery/capability-map.md` (2,403줄)
- 기준: `reports/evidence/m0/0a/scope.md`의 `acceptance_commands` 4항목
  (= `milestone-0.md` 완료 조건 중 0A 담당 항목)
- 규격: `.claude/skills/evidence-pack/SKILL.md` §"문서 slice(M0)의 evidence"

## 1. acceptance 항목별 대조

### A1. V2 필수 capability마다 사용자 가치와 acceptance scenario가 존재

- 판정: **충족**
- 충족 근거: `docs/discovery/capability-map.md` — `V2 필수` 63건 각각에
  `**사용자 가치**` 한 줄과 `**Acceptance scenario**` 절이 있다.
- 검증 방법 (재현 가능). capability 블록별로 분류·사용자 가치·acceptance 유무를 대조한다.

  ```
  $ python3 - <<'PY'
  import re
  txt=open('docs/discovery/capability-map.md',encoding='utf-8').read()
  blocks=re.split(r'\n### ', txt)
  missing=[];v=a=0;tot=0
  for b in blocks:
      m=re.match(r'([A-Z]+-\d+) · ', b)
      if not m: continue
      cls=re.search(r'^- \*\*분류\*\*: *`([^`]+)`', b, re.M)
      if not cls: continue
      tot+=1
      if '사용자 가치' in b: v+=1
      if 'Acceptance scenario' in b: a+=1
      if cls.group(1).startswith('V2 필수') and ('Acceptance scenario' not in b or '사용자 가치' not in b):
          missing.append(m.group(1))
  print('capabilities:',tot,'| 사용자 가치:',v,'| acceptance:',a)
  print('V2 필수 missing:',missing or 'none')
  PY
  capabilities: 93 | 사용자 가치: 84 | acceptance: 64
  V2 필수 missing: none
  ```

  - 실패 이력: 최초 실행에서 `OPS-13`이 `no-acceptance`로 검출됐다. acceptance scenario
    5개를 추가한 뒤 재실행해 `none`이 됐다. 검증이 실제로 작동했다는 증거로 남긴다.
  - `사용자 가치` 84건은 `V2 필수` 63건 + `후속`·`근거 부족` 대다수를 포함한다. 미기재
    9건은 전부 `폐기`이거나 사용자 가치가 없다고 판정한 항목이며, 완료 조건은 `V2 필수`만
    요구한다.
  - `acceptance` 64건 = `V2 필수` 63건 + `STR-11`(채택 시 요구되는 관찰 가능 동작).

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
  - §10 집계 주석: "93이라는 수는 사용자 가치 단위의 분해 결과이지 V2 구현 단위 수도,
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
- 충족 근거: capability 93건 전부에 `**분류**:` 줄이 있고 값이 4개 어휘 중 하나다.

  | 분류 | 건수 |
  | --- | ---: |
  | V2 필수 | 63 |
  | 후속 | 14 |
  | 근거 부족 | 10 |
  | 폐기 | 6 |
  | **계** | **93** |

- 검증 명령과 결과 (2026-08-22 실행)

  ```
  $ grep '^- \*\*분류\*\*:' docs/discovery/capability-map.md \
      | sed -E 's/^- \*\*분류\*\*: *//' | sed -E 's/^`([^`]*)`.*/\1/' \
      | sed -E 's/ \(.*//' | sort | uniq -c | sort -rn
    63 V2 필수
    14 후속
    10 근거 부족
     6 폐기
  ```

  `capability-map.md` §10의 축별 표 합계와 일치한다.
- 설계 입력 절(ML-11, DEC-11~13, SET-07~10, OPS-00, OPS-21)은 capability가 아니므로 분류
  대상에서 제외했고, 그 사실을 §10에 명시했다.

### A4. 모든 분류에 근거 파일/commit 인용이 존재

- 판정: **충족**
- 충족 근거: 93건 전부에 `**분류 근거**:` 줄이 있고, legacy 파일:라인 또는 commit
  해시를 인용한다. legacy-scout 노트의 인용을 그대로 보존했다.
- 인용 밀도(2026-08-22 측정)

  ```
  $ grep -c '분류 근거' docs/discovery/capability-map.md          # 93
  $ grep -coE 'commit `[0-9a-f]{7}`' docs/discovery/capability-map.md   # 커밋 인용 다수
  ```

- 기준 SHA 표기: 문서 헤더에 `ed4b06c`(대다수 노트)와 `ddee938`(scout-collection, koneps
  경로 최신)를 병기하고 "인용 검증 시 각 항목의 출처 노트를 함께 본다"를 명시했다.
- 근거가 없거나 상충하는 항목은 인용을 지어내지 않고 `근거 부족` + OPEN으로 처리했다
  (§9.1 상충 근거 표 6건).

## 2. OPEN 결정 잔여 수

- **잔여 `OPEN` 64건** (`capability-map.md` §12).
  - G1 도메인 의미론(외부 공식 정의 확인 필요): 7
  - G2 정책 값·임계 근거: 9
  - G3 V2 범위 결정: 15
  - G4 legacy 동작이 의도인가 결함인가: 17
  - G5 미검증 수치 재측정: 9
  - G6 외부 확인 / ADR 연계: 7
- 이 slice에서 통합·해소된 항목 3건(`OPEN-OPS-06` 해소, `OPEN-NOTI-03`·`OPEN-SET-07` 통합)은
  §12.1에 결번 사유와 함께 기록했다.
- 검증: `grep -oE 'OPEN-(COL|STR|QUAL|ML|DEC|NOTI|SET|OPS|NUM)-[0-9]+' … | sort -u | wc -l`
  → 67 (활성 64 + 결번 3).
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
| `commands.md` | N/A → 이 `checklist.md`로 대체 | 문서 slice는 acceptance command 대신 완료 조건 대조를 기록한다(evidence-pack 규격) |
| `differential.json` | N/A | Python/V2 실행 결과 비교가 없는 문서 slice다. 이 slice는 legacy 동작을 **관찰로만** 기록하며 V2 산출물이 없어 비교 대상이 성립하지 않는다 |
| `golden-manifest.json` | N/A | fixture를 사용하지 않는다. fixture 후보 인계만 했다(§11.4) |
| `rollback.md` | N/A — 문서 산출물은 `git revert`로 복구 | 신규 wiring이 없다 |
| `codex-review-*.json` | pending | codex-reviewer 에이전트만 작성한다 |

## 5. secret 스캔

- 명령 (2026-08-22 실행)

  ```
  $ grep -rniE "(api[_-]?key|secret|token|password|Bearer )" docs/discovery/ reports/evidence/m0/0a/
  docs/discovery/capability-map.md:122:  관찰된 실패 문구는 HTTP 429 "API token quota exceeded"이고, 대응책이 4겹으로
  docs/discovery/capability-map.md:1440:- **분류 근거**: webhook secret 검사가 **조건부**다 — 채널이 설정돼 있고 secret이 설정된
  docs/discovery/capability-map.md:1447:  - webhook secret 미설정 상태의 미인증 호출이 **거부된다**. ← legacy는 통과시킨다.
  ```

- 재실행 시 이 `checklist.md` 자신도 매치된다(위 출력을 인용하고 있기 때문). 그 매치는
  `docs/discovery/capability-map.md`의 3건을 복사한 것이며 새로운 매치가 아니다.
- 판정: **통과.** 3건 모두 비밀값이 아니라 **일반 명사**다.
  - 122행: 외부 API가 반환하는 오류 문구를 관찰 사실로 인용(값 아님).
  - 1440·1447행: webhook 인증 메커니즘 설명. 설정 키 이름조차 옮기지 않았고 값은 없다.
- 육안 확인 (패턴 스캔으로 잡히지 않는 항목)
  - **Telegram chat id / bot token**: 없음. 채널을 "대화형 채널"·"Telegram"으로만 지칭하고
    식별자 값을 옮기지 않았다. 상류 `01_scout_notification.md`도 같은 정책을 명시했다.
  - **사업자 정보 / 상호 / 사업자번호**: 없음. SET-01은 "상호 정규화"를 규칙으로만
    기술하고 실제 상호를 옮기지 않았다. legacy 회귀 테스트에 등장하는 상호 예시는 인용하지
    않았다.
  - **수신자 이메일**: 없음.
  - **공고번호**: 개별 공고번호를 옮기지 않았다(상류 노트에는 일부 존재하나 이 문서에는
    인용하지 않았다).
  - **금액 실값**: 개별 공고의 금액을 옮기지 않았다. 인용한 수치는 집계·비율·건수뿐이며
    전부 측정일을 병기했다(§11).
  - **credential / URL / 호스트명**: 없음.

## 6. 커밋

- in_scope 파일만 커밋한다: `docs/discovery/capability-map.md`,
  `reports/evidence/m0/0a/`.
- scope 밖(`_workspace/`, 다른 discovery 문서, ADR)은 커밋하지 않는다.
- 커밋 SHA는 `scope.md`의 `head_sha`에 기입하고 같은 커밋에 포함한다(amend).
