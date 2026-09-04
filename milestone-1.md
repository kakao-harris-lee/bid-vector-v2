# 마일스톤 1 — Kotlin 골격과 순수 도메인 커널

## 목표

M0에서 승인된 명세를 새 Kotlin 프로젝트의 타입·모듈·테스트로 구현한다. Spring, DB, HTTP,
broker 없이도 핵심 판정이 실행되는 순수 도메인부터 만든다.

## 선행 조건

- M0 사용자 승인 (M0 는 Codex `approve` 도 갖춰 닫혔다 — 2026-09-04 이후 코드 slice 는 Codex 불요, `milestone-0.md` 「Codex 독립 리뷰」 개정 참조)
- 금액/rate/basis ADR 승인
- 검증 fixture 중 `authoritative` case 준비

## 구현 순서

### Slice 1A — 프로젝트와 CI 골격

- Gradle Kotlin DSL, wrapper, version catalog
- `shared-kernel`, `procurement`, `qualification`, `strategy`, `decision`,
  `settlement`, `workflow`, `adapters`, `app`
- module dependency/순환 의존 architecture test
- formatting, lint, unit test, coverage, size/complexity ratchet
- domain 모듈의 Spring/JPA/JSON/HTTP import 금지 test

빈 모듈을 많이 만드는 것이 목적이 아니다. M0 필수 capability에 없는 모듈은 만들지 않는다.

**`bidding`을 위 목록에서 뺐다 — 운영자 결정 2026-09-02.** 「투찰 계획과 상태」를 소유하는
capability가 `capability-map.md`에 없어 바로 위 문장이 그대로 적용된다. 소유 capability가
서면 추가하며 추적은 **`OPEN-ADR-14`**다. 근거 전문은 **`docs/adr/0006` D-2.1**이 갖는다 —
여기서 되풀이하지 않는다.

### Slice 1B — Money/Rate/Basis

- 원 단위 금액과 계산용 decimal의 명시적 분리
- `BaseAmount`, `EstimatedAmount`, `YegaAmount`, `BidAmount`
- `Rate(unit=fraction)`, `Basis`, `VatTreatment`, `Provenance`
- versioned `RoundingPolicy`
- percent/fraction, basis 교차 대입, overflow, unknown provenance의 실패 계약

property test로 변환 왕복, 반올림 경계, 잘못된 단위 거부를 검증한다.

### Slice 1B-c — corpus 계약 정렬

**운영자 결정 2026-09-04(decision 15) 로 신설.** 1B 완료 조건의 「승인된 authoritative
corpus 전체 통과」는 1B slice 의 종결 조건에서 제외하고, 이 slice 가 마일스톤 수준에서
채운다 — 착수는 1B approve 뒤 별도 계약 고정. 담당 레인은 fixture-curator + spec-writer다.

**근거**: 1B 는 타입·계약 형태를 완성했으나(`reports/evidence/m1/1b/checklist.md` 「M1
완료 조건 대조」) `money-basis` 6건·`rate-unit` 5건이 술어 미비로 `insufficient-evidence`
강등돼 authoritative case 가 0건으로 남았다(`reports/evidence/m1/1b/fixtures.md`). 이
간극은 타입 구현이 아니라 fixture 계약 술어·재추출의 문제라 corpus 전용 slice 로 분리하는
것이 1B 를 계속 붙드는 것보다 낫다는 것이 운영자 판단이다.

**in_scope 후보 셋**(계약 확정은 이 slice 착수 시점의 별도 slice 계약이 정본):

1. `fixtures/tools/manifest_contract.py` — 술어 어휘 동결 해제. `holds()`와 스윕 변이체
   생성 규칙을 실제 1B 계약 어휘(`Fact.Known`/`Absent`·`ReasonCode`)에 맞춰 확장한다.
2. `fixtures/manifest.yaml`·`fixtures/expected/**` — `rate-unit` 5건 재추출. 기대값
   토큰(`Accepted`/`Rejected` 등)을 1B 계약 어휘로 정렬한다(기대값 파일이 바뀌므로
   되돌림이 아니라 재추출이다) — case 별 운영자 승인이 필요하다.
3. money-basis 6건 — 명시 승인 또는 폐기/재정의 판단. `money-basis-001`·`-004`는 basis
   혼합이 컴파일 차단이라 거부 객체 자체가 없고(BLOCK-4), `money-basis-003`는 검색 경로가
   1B 계약에 없다(BLOCK-5) — 술어가 갖춰져도 이 셋은 별도 판단이 필요하다.

관련 OPEN: `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS`(`reports/evidence/m1/1b/scope.md`, 담당을
이 slice 로 정정), `capability-map.md` §14.2.

**착수 2026-09-04 · 운영자 결정 2026-09-05(decision 18~22)**: 술어 어휘 동결 해제(셋 전부),
결과 토큰 어휘 = 1B 계약 어휘, BLOCK-3 은 case 별 승인, 11 case 처분 표 전체 승인, 소비
테스트는 shared-kernel `testFixtures` projection + `app/src/test` runner. 계약 정본은
`reports/evidence/m1/1b-c/scope.md`. 담당 레인은 fixture-curator + kotlin-implementer
(spec-writer 는 2026-09-04 지시로 세션 모델이 대신한다).

### Slice 1A-b — 래칫·게이트 확장 (하네스)

**운영자 결정 2026-09-05 로 신설.** 1B 종료 시점에 함께 결정한 `OPEN-ADR-06`·`OPEN-ADR-16`
과 1B-c 조사가 드러낸 게이트 사각의 **배선**을 한 slice 에 모은다 — 도메인 코드가 아니라
`build-logic/**`·`config/quality/**` 를 만지는 하네스 slice 라 1A 의 승계다. 착수는 1B-c
종결 뒤 별도 계약 고정.

- `sizeGate` 에 **타입 멤버 수 상한 30**(Kotlin PSI 소스 기준) + 상속 깊이·구현 인터페이스 수
  **래칫**(shared-kernel baseline 2·1 대비 증가 금지) — `OPEN-ADR-06` (a), `docs/adr/0007` §5.
- **PMD CPD** 배선(`de.aaschmid.cpd`, `language = kotlin`, `toolVersion` 으로 엔진 독립,
  `minimumTokenCount` 50, **관찰 모드** — 리포트만, 실패 모드 전환은 1C 종료 시 결정).
  acceptance 에 **Gradle 9.6.1 스모크** — 실패 시 (b) `qualityBaseline` 자체 판정으로 후퇴 —
  `OPEN-ADR-16` (a).
- 1차 게이트(`moduleDependencyGate`)의 declared 판정에 **`testFixtures*` 의존 버킷** 포함 —
  `OPEN-1BC-TESTFIXTURES-GATE`(1B-c 조사 §8 실측 사각).

완료 조건은 M1 공통 조건 + 위 셋의 위반 fixture 가 실제로 걸림(관찰 모드인 CPD 는 리포트
산출로 대신). 게이트 위협 모델(「완료 조건」 절)은 그대로 적용된다.

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

### 게이트 위협 모델 (운영자 결정 2026-09-03)

위 완료 조건의 게이트가 **무엇을 막고 무엇을 막지 않는지**를 적는다. 지금까지 이것이 적혀
있지 않아 리뷰 라운드마다 새 우회 경로가 하나씩 나왔고, 그 무한은 설계 결함이 아니라
**모델을 적지 않은 결과**였다.

**방어한다** — 모듈·도메인 **소스**와 **의존 선언**을 통해 들어오는 경계·크기·언어·형식
위반. 여기에는 모듈 build script 의 **평범한 선언**(의존 추가, source dir 등록, 표준 task
설정)이 포함된다.

**방어하지 않는다** — 게이트의 **정의·배선 자체**를 바꾸는 편집. `build-logic/**` ·
`config/quality/*.properties` · 루트 `build.gradle.kts` 수정, `tasks.named(...) { enabled = false }`,
`check` 의존 제거, `-x` 로 게이트 제외, 그리고 outputs 를 선언하지 않고 산출물 디렉터리에
직접 쓰는 task. **이 경계는 선택이 아니라 강제다** — 게이트가 같은 저장소의 빌드 스크립트이므로
그것을 고칠 수 있는 저자를 상정하면 게이트는 정의상 무력하다.

**도구 설정 파일도 여기 든다**(운영자 결정 2026-09-03) — `.editorconfig`(경로별
`ktlint = disabled` 포함) · `config/quality/**`(detekt 의 `excludes`·`ignoreAnnotated`,
크기·경계·효과 정책) · kover 필터 · convention script 의 게이트 배선. **이 파일들은 게이트가
「무엇을 거르는가」를 정의하므로 그 편집은 게이트 정의를 고치는 것과 같은 성질이다** —
한 줄로 도구를 특정 트리에서 끌 수 있다는 점에서 `enabled = false` 와 다르지 않다.

`.editorconfig` 에 이미 `[**/build/generated-sources/**] ktlint = disabled` 가 있다. **그것도
게이트 정의의 일부이며** 여기서 그렇게 인정한다 — 다만 그 경로는 `build/` 아래라 소스 관례
밖이고, 레이아웃을 관례로 고정하는 것과 어긋나지 않는다.

**게이트를 실행하는 task 의 실행 집합을 줄이는 설정도 여기 든다**(운영자 결정 2026-09-03,
Codex 12차 high) — `Test.filter`의 `excludeTestsMatching`·`excludePatterns`, `--tests` 선택,
게이트 test 를 도는 task 의 `onlyIf` 가 그것이다. 게이트를 test 로 표현하면 그 test 를 실행
집합에서 빼는 것이 게이트를 끄는 것과 같다는, 위 `enabled = false` 항목의 논증이 그대로
적용된다. **그럼에도 `gateExecutionGate`(policy `config/quality/gate-tests.properties`)가
`Test.filter` 계열을 구성상 잡는다** — 경계는 그 단언 자체를 끄는 편집에만 적용된다.

**이것은 요구 축소가 아니라 미기재 사항의 명시다.** 위 완료 조건의 **fixture 항목**이 요구하는
대상은 **소스 수준 위반**이지 빌드 배선 사보타주가 아니다. `v2-지침서.md` §5 「회귀의 구조적
방지」(*"회귀 방어를 사람의 주의력에"* 맡기지 않는다)와 `ADR 0007` §1.2 는 둘 다 **회귀(drift)**
를 말하며, 어느 문면도 적대적 저자를 상정하지 않는다. 근거 전문과 우회 시도 아홉의 실측은
**`ADR 0007` §1.1.2**가 든다.

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- 타입이 unit/basis 혼입을 실제로 막는지
- test가 구현을 재진술할 뿐인 tautology가 아닌지
- first-match 순서와 경계값이 승인 명세와 일치하는지
- 과도한 pattern/abstraction이 새 결합을 만들지 않는지
- architecture gate를 우회할 수 없는지

각 slice 별 verifier `ready-for-review` 와 사용자 승인을 받고, 1A~1E 전체가 승인되어야 M2로 진행한다.

## 범위 밖

- Spring controller, DB schema, Flyway
- KONEPS/LLM/gRPC 실제 호출
- Python ML 코드
- 기존 Python과 byte-for-byte 동등성
