# commands — M1 / 1B-c

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절은 만들지 않는다 — 그 기록은 git log 와 리뷰 verdict 가 갖는다.

**C-0~C-4(Gradle acceptance)는 ④ 레인(소비 테스트)이 적는다.** 아래는 corpus 레인(①②③)의
몫인 **C-5~C-10** 이고, 각 커밋 뒤에 전건을 다시 돌렸다 — 아래 수치는 `ed87da6` 시점이다.

## C-5 — `python3 fixtures/tools/mutation_sweep_adversarial.py`

- exit: 0
- 핵심 결과: **강등 대상 0 · 잔존 authoritative 28.** 착수 시점 18 에서 1B 축 열이 돌아왔고
  (`rate-unit` 다섯 · `money-basis` 001·002·004·005·006) **1B 축 밖 18 은 집합이 그대로다**
  (base `a5ea955` 의 authoritative 집합과 대조: 추가 10 · 빠짐 0). `money-basis-003` 은 이월로
  `insufficient-evidence` 에 남는다.
- 함께 도는 것: **술어 self-check 22 건 통과**(`manifest_contract.self_check()`, corpus 를 읽지
  않는 격리 검사). 동결 상태의 `holds()` 에서 **15 건 실패(RED)** 를 먼저 확인하고 구현했다.

## C-6 — `python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml`

- exit: 0
- 핵심 결과: manifest reader 와 PyYAML 이 **63 case** 에서 일치. 새 술어 키(`is-present` 항목의
  `operand` 부재 포함)를 둘이 같게 읽는다.

## C-7 — `python3 fixtures/tools/mutation_sweep_targeted.py`

- exit: 0
- 핵심 결과: 통과 자리는 `license-006` **대조군(무변이)** 하나뿐 — 착수 시점과 같다. 앞선
  라운드가 실증한 세탁 변이체(B7 넷 · B8 표기 셋)가 **전부 caught 로 바뀌었다**(착수 시점에는
  그 case 가 강등돼 있어 아예 돌지 않았다).

## C-8 — `python3 fixtures/tools/manifest_prose_consistency.py`

- exit: 0
- 핵심 결과: 불일치 **case id 7 · (블록,case) 10**. 착수 시점 8·11 에서 **하나 줄었다** —
  `rate-unit-004` 가 `authoritative` 로 돌아와 그 자리가 낡은 덮개 주장이 아니게 됐다.
  **새로 생긴 불일치는 없다**(나머지 열 자리는 착수 시점과 같은 (case, 사유) 짝이고 줄 번호만
  밀렸다).

## C-9 — `python3 fixtures/tools/check_legacy_numbers.py`

- exit: 0
- 핵심 결과: legacy-number hits **0**. 재추출이 새로 실은 수는 `100`·`1`·`0.875`·`0.00875`
  뿐이고 넷 다 `FORBIDDEN` 밖이다.

## C-10 — 11 case 의 `input_file`·`expected_file` SHA-256 재계산 후 manifest 값과 대조

- exit: 0
- 핵심 결과: **63 case 126 해시 전수 대조, 불일치 0.** 값이 바뀐 자리는 기대값 9 · 입력 3 이고
  전부 해당 case 의 `change_history` 에 `previous_expected_sha256`/`previous_sha256` 을 실었다.

## 무해성 대조 (착수 시점 기준선)

기준선은 `_workspace/m1-1b-c/01_scout_preflight.md` §5 가 HEAD `edbeee5` 에서 잰 값이고, 이
레인이 **편집 직전에 다시 재서** 같은 값임을 확인한 뒤 그 위에서 작업했다.

| 도구 | 착수 시점 | ① 뒤 | ③ 뒤 |
| --- | --- | --- | --- |
| C-5 강등 / 잔존 | 0 / 18 | 0 / 18 | 0 / 28 |
| C-7 통과 자리 | `license-006` 대조군 | 같음 | 같음 |
| C-8 불일치 | 8 · 11 | 8 · 11 | 7 · 10 |
| C-9 hits | 0 | 0 | 0 |

**① 뒤 전건이 착수 시점과 같다는 것이 술어 확장의 무해성 증거다** — 어휘를 넓혔을 뿐 어느
case 의 계약도 바뀌지 않았고, `not-equals` 의 의미는 한 글자도 건드리지 않았다.
