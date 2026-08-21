# V2 검증 데이터와 회귀 corpus 구축 지침

## 1. 목적

기존 Python 출력을 복제하기 위한 golden을 만들지 않는다. V2 corpus는 새 도메인 명세가
맞는지를 검증하기 위한 **근거가 있는 예제와 반례**다.

각 case는 다음 세 층 중 하나로 분류한다.

1. `authoritative`: 공식 규정/API 문서 또는 사용자가 승인한 업무 규칙
2. `observed`: 기존 운영 데이터/로그에서 비식별화해 관찰한 실제 형태
3. `legacy-behavior`: 기존 Python이 낸 결과. 참고만 가능하며 정답 지위 없음

`legacy-behavior`를 `authoritative`로 승격하려면 별도의 수작업 판정과 승인 근거가 필요하다.

## 2. 기존 저장소에서 우선 조사할 자료

기존 [`bid-vector`](./bid-vector)의 다음 파일은 사례 후보를 찾는 출발점이다.

- KONEPS 수집 형태: `tests/test_koneps_collection_characterization.py`,
  `tests/goldens/koneps/`
- 금액 basis: `tests/test_basis_conversion.py`, `tests/test_base_amount_basis.py`,
  `tests/test_bid_decision_basis_alignment.py`
- 법정 하한: `tests/test_floor_shortfall.py`, `tests/test_published_floor_rate.py`,
  `tests/test_budget_cap_basis.py`
- 면허 자격: `tests/test_license_eligibility.py`, `tests/test_license_matching.py`,
  `tests/test_license_gate_wiring.py`
- predictor 사례: `tests/test_predictor_output_equivalence.py`,
  `tests/goldens/predictor/`
- 정산 payload: `tests/test_paper_bidding_run_payload_characterization.py`,
  `tests/goldens/paper_bidding/`

이 파일의 기대값을 그대로 복사하지 않는다. 입력 형태, 누락 필드, 경계 조건, 과거 회귀를
추출하고 기대값은 V2 명세로 다시 판정한다.

## 3. case manifest

모든 fixture는 manifest entry를 가진다.

```yaml
id: floor-shortfall-001
domain: floor-shortfall
classification: authoritative
source:
  kind: approved-rule
  reference: ADR-004
legacy_reference:
  repo_sha: ed4b06c
  files:
    - tests/test_floor_shortfall.py
input_file: fixtures/floor-shortfall-001.json
expected_file: expected/floor-shortfall-001.json
normalization:
  rate_unit: fraction
  money_unit: KRW
  timezone: Asia/Seoul
privacy:
  contains_personal_data: false
sha256: <fixture-content-hash>
review:
  claude_commit: <sha>
  codex_verdict: approve
  approved_by_user: true
```

필수 조건:

- 출처가 없는 fixture 금지
- 입력과 기대 결과의 version 고정
- 정규화 전 원본과 정규화 후 fixture 구분
- hash로 사후 변경 탐지
- 기대값 변경 시 case를 덮어쓰지 말고 변경 이유와 policy version 기록

## 4. 도메인별 최소 corpus

### 면허 자격

- 그룹 내 모든 면허 만족
- 그룹 일부만 만족
- 여러 그룹 중 하나만 만족
- 포괄 코드/별칭 충돌
- 만료·지역·협회 조건
- 정보 누락으로 `Uncertain`
- 같은 코드를 percent/공백/문장 위치 차이로 표현한 입력

### 기초금액 provenance

- 정수 원화 후보
- 예정가 역산형 소수
- VAT 파생형
- 추정가격과 모순되는 비율
- 값은 정수지만 출처가 없어 `Unknown`이어야 하는 경우
- NaN/Infinity/0/음수/overflow
- 두 rule에 동시에 걸려 rule order가 결과를 바꾸는 경우

### 낙찰하한 미달

- 임계 바로 아래/같음/바로 위
- percent와 fraction 혼입 거부
- 최소 표본 `N-1`, `N`, `N+1`
- 표본 범위 경계
- 모든 표본 무효
- `Unmeasurable`가 0%로 표시되지 않는 경우

### KONEPS 수집

- 정상 OpenAPI payload
- optional field 누락과 명시적 null
- 숫자 문자열, 천 단위 구분자, 잘못된 인코딩
- HTML fallback이 필요한 사례
- 같은 공고의 중복/수정 이벤트
- rate limit, timeout, partial page, out-of-order 결과
- 공식 field와 파생 field의 provenance 충돌

### ML

- feature schema의 정상/누락/미지원 version
- training/serving 동일 feature transform
- cold start와 최소 표본
- artifact checksum 불일치
- LightGBM 오류와 KDE singular input
- deterministic seed에서 재현 가능한 결과
- 모델 점수와 Kotlin 업무 판정이 분리되는 사례

## 5. 추출 절차

1. 기존 코드/테스트에서 회귀 또는 입력 형태를 찾는다.
2. secret, 사용자/회사 식별자, Telegram id, 원문 개인정보를 제거한다.
3. notice number 등 공개 식별자를 유지할 필요가 없으면 안정적인 synthetic id로 치환한다.
4. 원본 단위와 provenance를 기록한 뒤 canonical fixture로 정규화한다.
5. 기존 Python을 실행해 `legacy-observation`을 별도 저장할 수 있다.
6. 공식 근거 또는 승인된 도메인 규칙으로 V2 기대값을 수작업 작성한다.
7. Claude가 test를 만들고 Codex가 source, privacy, expected reasoning을 독립 검토한다.
8. SHA-256을 manifest에 기록한다.

실제 운영 DB read가 필요하면 query, 컬럼, row limit, masking 계획을 먼저 사용자에게
제시하고 승인을 받는다. 이 문서만으로 DB 접근 권한이 생기지 않는다.

## 6. differential 결과 판정

Python과 V2를 같은 입력으로 실행했을 때:

| 결과 | 처리 |
| --- | --- |
| 둘 다 명세와 일치 | case 통과 |
| Python만 불일치 | `legacy-defect`로 기록, V2 유지 |
| V2만 불일치 | V2 test/구현 수정 |
| 둘 다 불일치 | 명세에 맞게 기대값과 V2 수정 |
| 명세가 모호 | `insufficient-evidence`, 완료 gate 실패 |
| 의도적 재설계 | ADR와 새 policy version이 있을 때만 허용 |

Python 출력 diff 0은 V2 합격 조건이 아니며, diff 발생 자체도 실패가 아니다. 판정 근거가
없는 diff만 실패다.

## 7. 금지 사항

- `KONEPS_*_GOLDEN_REGEN=1` 같은 재생성 명령으로 V2 기대값 자동 승인
- 기존 DB row를 그대로 커밋
- 가상의 fixture를 “실운영 샘플”이라고 표기
- percent/fraction, 원/천원, VAT/basis를 값 크기로 자동 추측
- 개인정보 masking 전 로그/리뷰 산출물 기록
- 한 case의 기대값을 여러 의미에 재사용
