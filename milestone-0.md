# 마일스톤 0 — 요구사항 발굴과 아키텍처 동결

## 목표

기존 Python 코드를 옮기지 않고, V2에서 다시 구현할 기능·도메인 규칙·데이터 계약·실패
사례만 선별한다. 이 단계는 문서와 검증 fixture 설계 단계이며 애플리케이션 코드를 만들지
않는다.

## 선행 조건

- [`v2-지침서.md`](./v2-지침서.md)와 [`agent-workflow.md`](./agent-workflow.md) 승인
- 기존 [`bid-vector`](./bid-vector) 접근 가능
- 조사 기준 SHA 고정
- 독립 Codex diff review를 위해 이 디렉터리를 별도 Git 저장소로 초기화하고 문서 baseline
  커밋 고정(사용자 승인 후)

## Claude 작업

### Slice 0A — capability map

기존 UI/API/서비스/작업을 파일 목록이 아니라 사용자 capability 기준으로 분류한다.

- 공고 수집·정규화
- 회사/운영자 전략
- 면허·지역·실적 자격
- 추천 입력·ML 추론
- 투찰 판단과 근거
- 알림/보고서
- 개찰 대사·정산
- 운영·증적

각 capability를 `V2 필수`, `후속`, `폐기`, `근거 부족` 중 하나로 표시한다. 기존 endpoint
수나 함수 수를 V2 요구사항 수로 간주하지 않는다.

### Slice 0B — regression ledger

최소 다음 계열을 조사한다.

- 금액 basis: 기초금액/예정가/추정가격/낙찰가
- rate scale: fraction/percent
- VAT/provenance와 파생값 오염
- 법정 하한과 `Unmeasurable`
- 면허 group AND/OR, 별칭, 불확실 판정
- KONEPS 필드 누락·fallback·rate limit
- 비동기 중복·재시도·queue 폭주
- 모델 label/feature의 training-serving skew와 시간 누수

각 항목에는 `관찰`, `사용자 영향`, `V2 예방 제약`, `검증 방법`, `근거 파일/commit`을 쓴다.

### Slice 0C — 도메인 명세와 데이터 사전

- 용어, 단위, basis, nullable 의미
- aggregate와 상태 전이
- rule의 입력/출력/reason code
- 정책 version과 effective date
- canonical KONEPS fact와 derived fact 구분
- ML feature와 업무 판단의 경계

모호한 항목은 기존 Python 구현을 정답으로 채우지 말고 `OPEN`으로 남겨 사용자 결정을
요청한다.

### Slice 0D — ADR

최소 다음 결정을 기록한다.

1. Kotlin modular application + Python ML engine
2. Gradle module과 의존 방향
3. 금액/rate/basis 표현
4. gRPC/Protobuf 내부 계약
5. DB 및 migration 도구
6. domain event/outbox/notification 방식
7. 테스트 pyramid와 mutation 대상
8. React UI 재사용/재작성/후속 여부

## 산출물

```text
docs/discovery/capability-map.md
docs/discovery/regression-ledger.md
docs/discovery/data-dictionary.md
docs/discovery/legacy-reference-map.md
docs/adr/0001-target-architecture.md
docs/adr/0002-money-rate-basis.md
docs/adr/0003-contract-transport.md
docs/adr/0004-persistence-and-events.md
fixtures/manifest.yaml              # schema와 후보 목록만
```

## 완료 조건

- V2 필수 capability마다 사용자 가치와 acceptance scenario가 있다.
- Python 파일/endpoint를 그대로 옮기는 작업 항목이 없다.
- 모든 도메인 숫자의 unit/basis/provenance가 정의되거나 `OPEN`이다.
- 기존 회귀마다 V2의 타입·계약·테스트 중 최소 하나의 예방책이 있다.
- Python 결과가 정답이 아니라 참고임을 모든 관련 문서가 일관되게 명시한다.
- `OPEN` 결정이 0개이거나 사용자가 명시적으로 다음 단계 진행을 승인했다.

## Codex 독립 리뷰

Codex는 구현을 제안하는 대신 다음만 판정한다.

- capability가 기존 파일 구조를 요구사항으로 오인하지 않았는가
- 회귀 ledger의 근거가 실제 파일/commit과 맞는가
- 명세가 Python의 버그/fallback을 무비판적으로 채택하지 않았는가
- 아키텍처 경계가 모호하거나 실행 불가능한 부분이 없는가
- acceptance scenario가 관찰 가능한가

Codex `approve`와 사용자 승인이 있어야 M1로 진행한다.

## 금지 사항

- Kotlin/Spring/Python 서비스 코드 작성
- 운영 DB query 또는 외부 API 호출(별도 사용자 승인 제외)
- 기존 golden 출력의 V2 expected 자동 복사
- “향후 결정”으로 핵심 unit/basis/writer를 숨긴 채 완료 처리
