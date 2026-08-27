# Slice 계약 — M0 / 0B regression ledger

```yaml
milestone: m0
slice: 0b-regression-ledger
base_sha: ec115a7   # 0A2 Codex approve verdict 등재 직후
head_sha: TBD       # 산출물 커밋 후 후속 커밋으로 기입 (0A2와 같은 방식)
in_scope:
  - docs/discovery/regression-ledger.md
  - reports/evidence/m0/0b/
out_of_scope:
  - docs/discovery/capability-map.md   # 0A2에서 Codex approve로 확정. 읽기 전용 참조
  - 다른 M0 산출물: data-dictionary(0C), ADR(0D), fixtures/manifest.yaml
  - Kotlin/Spring/Python 애플리케이션 코드 (M0 금지 사항)
  - .claude/ 하네스
  - 운영 DB query, 실제 외부 API 호출
  - bid-vector/ symlink 아래 기존 저장소 수정 (읽기 전용)
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A7을 checklist.md로 대조"
rollback: "N/A — 문서 산출물은 git revert로 복구"
```

작성: 2026-08-27, v2-slice-pipeline Phase 1

## 입력

- **선행 조사**: `_workspace/m0-0b/01_scout_regression_preflight.md` — 8계열 **60건**
  (`legacy에서 수정됨` 29 · `잔존` 28 · `판정 불가` 3) + 예방책 1건(P-ML-01)
- **§13 인계**: `capability-map.md` §13이 두 항목을 5필드로 채워 뒀다 — 예산 basis 불일치
  (**세 경로**, `legacy-defect`) · 지방계약 하한의 선언·실행 불일치
- **결정 기록**: `reports/evidence/m0/0a2/decisions.md` — 운영자 결정 30건. 여러 회귀의
  `V2 예방 제약`이 이미 결정으로 확정돼 있다
- **기준 문서**: `milestone-0.md` §"Slice 0B", `v2-지침서.md`, `data-extract.md`

## acceptance 항목

- **A1.** `milestone-0.md`가 요구하는 **8계열 전부**에 사례가 있고, 각 항목이
  `관찰` / `사용자 영향` / `V2 예방 제약` / `검증 방법` / `근거 파일·commit` 5필드를 갖는다.
- **A2. 모든 legacy 인용이 `ed4b06c`에서 실제로 확인된다.** 경로·행 범위·commit 해시가
  실측과 일치한다. 상류 노트에서 옮겨 온 인용도 **그 파일을 직접 열어** 확인한다.
  **확인 사실을 evidence에 기록한다** — 방법(전수 절차 vs 항목별 기록)은 이 slice가 정한다.
- **A3.** `V2 예방 제약`이 **타입·계약·테스트 중 최소 하나의 구조적 형태**다.
  "주의한다" 같은 규율 서술은 예방책으로 계상하지 않는다.
- **A4.** `V2 예방 제약`이 **legacy 동작의 복제가 아니다.** legacy가 그렇게 한다는 사실은
  관찰이며, 제약은 승인된 도메인 명세·운영자 결정·지침서 규율에서 나온다. 결정으로 이미
  확정된 항목은 `0a2/decisions.md`를 인용한다.
- **A5.** 근거가 얇은 항목을 억지로 채우지 않는다. **`판정 불가`로 명시하거나 `OPEN`으로
  등록한다.** 사용자 영향이 관측되지 않은 구조 결함은 그렇게 적는다.
- **A6.** 불변: 인용 형식 위반 0, 중복 id 0, secret 스캔 통과, `git diff --check` 0.
- **A7.** 0B가 새로 발견한 미결은 **`regression-ledger.md` 자체 `OPEN` 절에 등록**한다
  (아래 「OPEN 등록 위치」 참조).

## 0A2에서 가져오는 규율 — 계약에 박는다

0A2는 Codex 리뷰 6라운드 + verifier 재검증 다수를 거쳐 `approve`로 닫혔고, 그 과정에서
여섯 가지 실패 형태가 `reports/evidence/m0/0a2/checklist.md` §10.1에 정리됐다.
**0B는 그 표를 처음부터 적용한다.**

| 형태 | 0B에서의 의미 |
| --- | --- |
| **1. 확인하지 않고 기재** | 수치 주장은 실행·측정한 것만. 측정일 없는 legacy 수치는 인용하지 않는다 |
| **2. 확인하지 않고 승격** (legacy를 열지 않고 인용) | **0B의 핵심 위험이다.** 60건이 전부 파일:라인·commit 근거를 단다. 상류 노트에서 옮기는 인용도 직접 연다. **행 범위는 블록 경계까지 뜬다** |
| **3. 실행하지 않고 붙여넣기** | 명령 출력은 기록 시점 HEAD 실행분. 접은 표기는 그렇다고 선언. **서로 다른 실행의 출력을 섞지 않는다** |
| **4. 재현 불가능한 참조** | 스크립트는 evidence에 본문 인라인 |
| **5. 정정을 인용 지점에 전파하지 않기** | 수치를 바꿨으면 인용 지점을 전수 확인. **자기 편집이 만든 오프셋도 대상이다** |
| **6. 셈으로 전칭을 주장하기** | 전칭은 셈이 아니라 **재현 명령**으로 쓴다. `뿐`·`전부`처럼 **수를 쓰지 않는 전칭도 포함**한다. **"고쳤다"는 진술도 전칭이다 — 적기 전에 그 지점을 열어 확인한다** |

**추가로 0B에 고유한 것:**

- **줄 번호 자기참조를 만들지 마라.** 0A2가 그것으로 두 라운드를 태웠다. 대상이 이름으로
  유일하게 지시되면 이름만 쓴다. legacy 파일의 행 범위는 예외다(그 파일은 이 저장소가
  편집하지 않는다).
- **Codex 레인의 구조적 사각지대**: 리뷰어 worktree에 `bid-vector`가 없어 legacy 인용을
  원리적으로 재현하지 못한다. 0A2 매 라운드 residual risk에 같은 문장이 적혔고 실제로
  인용 오류 4건이 0B 선행 조사에서야 발견됐다. **0B는 그 검증을 verifier 레인이 진다.**
- **필요가 관측되기 전에 도구를 늘리지 마라.** 0A2가 만든 축 셋은 전부 실제 적발된 결함이
  먼저 있었다. 스윕은 결함 부재를 증명하지 않는다.

## OPEN 등록 위치

`capability-map.md` §12는 **out_of_scope**(0A2 approve로 확정)다. 따라서 0B가 발견한
미결은 **`regression-ledger.md`의 자체 `OPEN` 절**에 등록한다.

- id 접두는 `OPEN-REG-NN`을 쓴다 — `capability-map.md` §12의 id 공간과 충돌하지 않는다.
- **중앙 registry(§12)와의 통합은 별도 slice**다. 0B는 통합하지 않고, 통합이 필요하다는
  사실만 인계에 적는다.
- 0B가 **기존 활성 OPEN 45건 중 하나를 해소할 근거를 찾으면 해소하지 말고 보고**한다 —
  해소는 `capability-map.md` 편집이라 범위 밖이다.

## 선행 조사가 스스로 밝힌 약한 곳

| 계열 | 무엇이 얇은가 | 처리 |
| --- | --- | --- |
| **2. rate scale** | 5건 중 사용자 피해 관측은 1건뿐. 나머지는 코드가 스스로 공시한 구조 결함이라 "누가 무엇을 잘못 봤는가"가 비어 있다 | 사용자 영향을 **`판정 불가`로 명시.** fixture-curator에 경계값 corpus(0.5·1.5·2.0 전후) 요구 |
| **6. KONEPS rate limit** | 코드 경로는 확정적이나 발생 빈도 근거 0건 | **`OPEN` 등록.** 운영 로그 확인 가능 여부를 운영자에게 묻는다 |
| **3. "66% 오염"** | 5곳에 복제됐으나 측정 방법·모수·측정일 없음. 선행 조사가 출처 포인터를 찾았다 | **인용 금지 유지.** 포인터만 기록하고 사실로 승격하지 않는다 |
| **5. `Uncertain` 분포** | legacy에 측정되지 않음 | 추가 조사 불필요. `OPEN-QUAL-06` 결정을 인용만 한다 |

## 주의

- **`capability-map.md`가 틀렸음이 드러나면 고치지 말고 보고**한다 — 별도 slice 계약이 필요하다.
- 예방책 1건(P-ML-01, training-serving skew 방지 구조)은 **회귀가 아니라 이미 있는 장치**다.
  ledger에 넣되 그렇게 표시하고 회귀 수에 계상하지 않는다.
- **`_workspace/`는 gitignore 대상**이다. 판정의 근거가 되는 것은 evidence로 커밋한다.
