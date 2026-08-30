# 마일스톤 1 — Kotlin 골격과 순수 도메인 커널

## 목표

M0에서 승인된 명세를 새 Kotlin 프로젝트의 타입·모듈·테스트로 구현한다. Spring, DB, HTTP,
broker 없이도 핵심 판정이 실행되는 순수 도메인부터 만든다.

## 선행 조건

- M0 Codex `approve`와 사용자 승인
- 금액/rate/basis ADR 승인
- 검증 fixture 중 `authoritative` case 준비

## 구현 순서

### Slice 1A — 프로젝트와 CI 골격

- Gradle Kotlin DSL, wrapper, version catalog
- `shared-kernel`, `procurement`, `qualification`, `strategy`, `bidding`, `decision`,
  `settlement`, `workflow`, `adapters`, `app`
- module dependency/순환 의존 architecture test
- formatting, lint, unit test, coverage, size/complexity ratchet
- domain 모듈의 Spring/JPA/JSON/HTTP import 금지 test

빈 모듈을 많이 만드는 것이 목적이 아니다. M0 필수 capability에 없는 모듈은 만들지 않는다.

### Slice 1B — Money/Rate/Basis

- 원 단위 금액과 계산용 decimal의 명시적 분리
- `BaseAmount`, `EstimatedAmount`, `YegaAmount`, `BidAmount`
- `Rate(unit=fraction)`, `Basis`, `VatTreatment`, `Provenance`
- versioned `RoundingPolicy`
- percent/fraction, basis 교차 대입, overflow, unknown provenance의 실패 계약

property test로 변환 왕복, 반올림 경계, 잘못된 단위 거부를 검증한다.

### Slice 1C — Qualification

- 단일 면허 조건
- 그룹 내 AND, 그룹 간 OR
- `Eligible`, `Ineligible(reasons)`, `Uncertain(reasons)`
- 별칭/포괄 코드/지역 조건을 versioned policy data로 분리
- **유효기간은 다루지 않는다** — `v2-지침서.md` §4.2(운영자 결정 2026-08-28 U-7, 문면
  집행 2026-08-29 Q2). **대신 자격 판정 결과에 「유효기간 미검증」이 드러나야 한다**

### Slice 1D — Provenance와 Floor Shortfall

- 기초금액 provenance first-match rule과 reason
- rule order의 명시적 테스트
- 임계 사정률과 과거 빈도 계산
- 최소 표본 미달의 `Unmeasurable`
- 빈도를 실제 확률로 표현하지 않는 output contract

### Slice 1E — Strategy와 상태

- 저비용 `OperatorStrategy.matches` 순수 predicate
- 전략 값 validation
- 필요한 최소 sealed state/event 타입

## 구현 규칙

- domain은 I/O가 없는 입력→출력 함수/객체다.
- mock framework 대신 fixture와 fake policy repository를 사용한다.
- `if`를 없애기 위한 불필요한 class hierarchy를 만들지 않는다.
- 같은 rule을 enum, validator, service에 중복 구현하지 않는다.
- magic number는 근거와 policy version을 가진다.
- 기존 Python 이름과 클래스 구조를 따라가지 않는다.

## 산출물

- build 가능한 Kotlin multi-module project
- domain source와 unit/property test
- architecture/size ratchet
- M1 case manifest와 test report
- `reports/evidence/m1/<slice>/...`

## 완료 조건

- `./gradlew check` 통과
- 금지 import와 순환 의존을 일부러 넣은 test fixture가 실제로 실패
- 승인된 authoritative corpus 전체 통과
- 중요 rule mutation이 생존하지 않음
- raw `Double` 금액/rate가 public domain API에 없음
- `Uncertain`/`Unmeasurable`가 성공 또는 0으로 합쳐지지 않음
- 신규 파일/함수 예산 위반 없음

## Codex 독립 리뷰

- 타입이 unit/basis 혼입을 실제로 막는지
- test가 구현을 재진술할 뿐인 tautology가 아닌지
- first-match 순서와 경계값이 승인 명세와 일치하는지
- 과도한 pattern/abstraction이 새 결합을 만들지 않는지
- architecture gate를 우회할 수 없는지

각 slice별 승인을 받고, 1A~1E 전체가 승인되어야 M2로 진행한다.

## 범위 밖

- Spring controller, DB schema, Flyway
- KONEPS/LLM/gRPC 실제 호출
- Python ML 코드
- 기존 Python과 byte-for-byte 동등성
