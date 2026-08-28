# ADR 0006 — Gradle module 경계와 의존 방향

- **상태**: 제안됨 (M0 slice 0D) — 사용자 승인과 Codex `approve` 대기
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **2** (Gradle module과 의존 방향)
- **관련 ADR**: 0001(두 runtime) · 0002(`shared-kernel`이 소유하는 타입) · 0005(전달
  메커니즘의 배치) · 0007(경계를 실제로 막는 수단)

---

## 1. 맥락

`v2-지침서.md` §3.1이 모듈 목록과 의존 방향을 이미 규정했다.

> Kotlin은 하나의 Gradle 멀티모듈 애플리케이션으로 시작한다. 업무별 배포 서비스로 미리
> 쪼개지 않는다. …
> 의존 방향은 `domain <- application <- adapters/app`이다. domain은 Spring, JPA, JSON,
> HTTP, broker를 import하지 않는다. 다른 업무 모듈의 repository/entity를 직접 참조하지
> 않는다.

**이 ADR이 더하는 것**은 ① 그 목록을 층에 대응시키고 ② 업무 모듈 사이의 교차를 누가
조합하는지 정하고 ③ 새로 생기는 산출물(생성 stub, 정책 데이터, ml-engine)이 어디에
속하는지 정하는 것이다.

`v2-지침서.md` §2가 이 경계를 요구하는 관찰을 적었다 — *"수집·DB·ML·알림이 한 workflow에
결합 → 순수 도메인, application, adapter, ML 경계를 분리"*.
`regression-ledger.md` `R-QUAL-07`(같은 면허를 판정하는 경로가 둘이고 같은 공고에 다른
답을 낼 수 있다)과 `R-RATE-01`(같은 규칙이 7곳에 독립 구현됐고 한 곳만 임계가 어긋나
회귀 엔진이 됐다)이 **경계가 없을 때 무엇이 생기는지**의 실물이다.

---

## 2. 결정

### D-1. 하나의 Gradle 멀티모듈 프로젝트, 하나의 배포 단위

Kotlin 쪽은 **단일 애플리케이션**이다. 업무별 배포 서비스로 나누지 않는다
(ADR 0001 A-3에서 기각). 빌드는 **Gradle Kotlin DSL과 version catalog**를 쓴다
(`v2-지침서.md` §5).

### D-2. 모듈과 층의 대응

`v2-지침서.md` §3.1의 목록을 그대로 채택하고 층을 명시한다.

| 층 | 모듈 | 책임 |
| --- | --- | --- |
| **domain** | `shared-kernel` | Money, Rate, Basis, 식별자, 시간 타입만 (ADR 0002 D-9) |
| **domain** | `procurement` | KONEPS 공고·개찰 canonical facts와 수집 port |
| **domain** | `qualification` | 면허·지역·실적 자격 정책 |
| **domain** | `strategy` | 운영자 감시 조건과 전략 |
| **domain** | `bidding` | 투찰 계획과 상태 |
| **domain** | `decision` | 법정 하한, 추천 후보 평가, reason code |
| **domain** | `settlement` | 개찰 결과 대사와 정산 상태 |
| **application** | `workflow` | use case, transaction, event/outbox 조합 |
| **adapters** | `adapters` | HTTP, DB, KONEPS, notification, ML client |
| **app** | `app` | Spring Boot wiring과 public API |

`v2-지침서.md` §3.1의 `adapters` 설명에는 `broker`가 포함돼 있으나 **브로커를 두지 않기로
확정됐다**(운영자 결정 `OPEN-OPS-05`, 2026-08-26). 위 표에서 그 항목을 뺐고, 그 자리를
채우는 것은 ADR 0005의 DB 기반 스케줄·outbox이며 그 어댑터도 `adapters`에 있다.

### D-3. 의존 방향은 단방향이고 빌드가 그것을 선언한다

`domain <- application <- adapters/app`. **의존 선언은 Gradle 빌드 스크립트가 1차
강제 수단**이다 — domain 모듈의 빌드 파일에 Spring·JPA·gRPC 의존이 없으면 그 타입은
컴파일 단계에서 보이지 않는다.

- domain은 **Spring, JPA, JSON, HTTP를 import하지 않는다**(`v2-지침서.md` §3.1).
- domain은 프레임워크 독립적인 **port 인터페이스**에만 의존하고, Spring wiring은
  `app`/`adapters`에만 둔다(§5).
- `object` 싱글턴·전역 상태·service locator로 의존을 숨기지 않는다(§5). 협력 객체는
  **생성자 주입**으로 받는다.

### D-4. 업무 모듈은 서로를 직접 참조하지 않는다

- **다른 업무 모듈의 repository/entity를 직접 참조하지 않는다**(`v2-지침서.md` §3.1).
- 업무 모듈 사이의 교차는 **`workflow`가 조합**한다. 두 도메인이 함께 답해야 하는 질문은
  application 층의 use case이지 도메인 간 호출이 아니다.
- 업무 모듈이 공유해도 되는 유일한 domain 모듈은 **`shared-kernel`**이다.
- 모듈 간 재사용은 **public 계약으로만** 한다(§5).

**왜 이것이 회귀 방어인가**: `capability-map.md` §9(축 간 경계 정리)가 각 겹침에 대해
**소유 축**을 정했다 — 예: B-04(자격 게이트)는 QUAL-02가 판정을 소유하고 STR-01은 순서
계약만 가진다. 모듈 경계가 그 소유를 물리적으로 강제하지 못하면 `R-QUAL-07`처럼 같은
판정이 두 경로에 생긴다.

### D-5. 새 모듈은 기본이 아니라 예외다

`v2-지침서.md` §5: *"새 기능은 기존 모듈의 책임에 맞는지 먼저 판단한다."*
D-2의 목록에 없는 모듈을 추가하려면 **어느 책임이 기존 모듈에 맞지 않는지**를 slice
문서에 적는다. 줄 수를 줄이기 위한 분할은 금지된다(§5 「크기와 결합도」 — *"줄 수만 맞추기
위한 파일 분할을 금지한다"*).

### D-6. 생성 stub은 도메인에 노출되지 않는다

ADR 0003의 `.proto` 생성 코드와 ML client는 **`adapters`가 소유**한다. 도메인은 gRPC
타입도 Protobuf 타입도 보지 못한다 — 도메인이 받는 것은 port 인터페이스와 도메인 타입뿐이다.
**생성 stub을 별도 빌드 모듈로 뺄지는 M2의 구현 결정**이며, 이 ADR이 요구하는 것은
*"도메인에서 보이지 않는다"* 하나다.

### D-7. 정책 데이터는 모듈의 자산이지 코드가 아니다

`v2-지침서.md` §5가 도메인 정책 값(임계, 하한, 표본 수, 반올림 규칙)을 **versioned policy
데이터**로 두라고 규정한다. 그 데이터는 **판정을 소유한 domain 모듈**에 속하고, 로딩
경로는 port 뒤에 있다. **저장 형태와 effective date 모델은 0C 데이터 사전이 소유한다.**

### D-8. ml-engine은 Gradle 빌드에 들어가지 않는다

Python `ml-engine`은 `pyproject.toml` 기반의 별도 패키지다(`v2-지침서.md` §5).
Gradle 멀티모듈은 Kotlin 애플리케이션만 담는다. 두 runtime을 잇는 것은 **계약**이지
빌드 의존이 아니다(ADR 0003).

---

## 3. 대안

| # | 대안 | 판정 | 사유 |
| --- | --- | --- | --- |
| **A-1** | **단일 모듈** — 패키지로만 층·업무를 나눈다 | **불채택** | 의존 방향을 **빌드가 강제하지 못한다.** 규칙이 리뷰어의 주의력에 남으면 `v2-지침서.md` §5의 *"회귀 방어를 사람의 주의력에 맡기지 않는다"*를 어긴다. legacy가 이 형태였고 §2가 인용한 결합(수집·DB·ML·알림이 한 workflow에)이 그 결과다 |
| **A-2** | **층별 3모듈만** (`domain` / `application` / `adapters`) | **불채택** | 층 경계는 서지만 **업무 경계가 사라진다.** 모든 도메인이 한 모듈에 있으면 업무 모듈 간 직접 참조를 막을 수단이 없고(D-4), `capability-map.md` §9가 정한 소유 축이 빌드에 반영되지 않는다. `R-QUAL-07`·`R-RATE-01`이 그 상태에서 나온 회귀다 |
| **A-3** | **업무별 배포 서비스(MSA)** | **불채택** | ADR 0001 A-3. `v2-지침서.md` §3 — *"과도한 MSA가 아닌 두 개의 명확한 runtime으로 시작한다."* 운영 1인 |
| **A-4** | **Spring Modulith의 패키지 모듈 모델** | **불채택** | ADR 0005 §3.1이 같은 판정을 적었다 — Modulith의 "모듈"은 **패키지 단위 개념**이라 Gradle 빌드 모듈과 개념이 이중화되고, 프레임워크 결합도가 §5의 domain 독립 규율과 마찰한다. **빌드 모듈로 이미 물리 분리된 구조에서 무엇을 더 막아 주는지 측정된 필요가 없다** |
| **A-5** | **Maven 멀티모듈** | **불채택** | `v2-지침서.md` §5가 *"Gradle Kotlin DSL과 version catalog를 사용한다"*로 이미 규정했다. **Maven과 비교 측정하지 않았다** — 기각 근거는 승인된 지침이다 |
| **A-6** | **`shared-kernel`에 공용 유틸을 모은다** | **불채택** | §3.1이 그 모듈의 내용을 *"Money, Rate, Basis, 식별자, 시간 타입만"*으로 한정했다. 공용 유틸 모듈은 **모든 모듈이 의존하는 자석**이 되어 경계를 무력화한다. 공통화가 결합을 만들면 **중복 대신 경계를 재검토한다**(§5) |

---

## 4. 결과

- **`./gradlew check`가 경계 위반을 잡는다** — `milestone-1.md`의 완료 조건이
  *"금지 import와 순환 의존을 일부러 넣은 test fixture가 **실제로 실패**"*를 요구한다.
  게이트가 있다는 주장이 아니라 **게이트가 잡는다는 증거**가 기준이다.
- **강제 수단은 두 겹이다.** ① 빌드 의존 선언(컴파일 단계) ② architecture test(**ADR 0007**).
  ①이 1차이고 ②는 ①이 표현하지 못하는 규칙(모듈 내부의 층, 순환, 명명)을 맡는다.
- **ArchUnit은 바이트코드를 읽는다.** Kotlin `internal`·확장 함수·top-level 함수처럼
  바이트코드에서 형태가 바뀌는 요소의 규칙 표현은 **이번 조사에서 확인하지 않았다.**
  그래서 이 ADR은 **빌드 의존 선언을 1차 강제로 둔다** — 도구가 표현하지 못해도 컴파일이
  막는다.
- **모듈이 늘면 빌드 시간과 순환 의존 위험이 는다.** D-5가 그 증가를 예외로 둔 이유다.
- `milestone-1.md`가 이 구조를 처음 세우는 마일스톤이며, 그 범위 밖 목록에
  *"Spring controller, DB schema, Flyway"*가 있다 — **M1의 멀티모듈은 domain 층부터 선다.**

---

## 5. 이 ADR이 등록하는 `OPEN`

**없다.** 모듈 목록과 의존 방향은 `v2-지침서.md` §3.1이 승인된 문서로 이미 규정했고,
이 ADR은 그것을 층에 대응시키고 교차 조합의 소유자를 정했을 뿐이다.

---

## 6. 확인하지 않은 것

- **모듈별 fan-in/fan-out, public API 수, 순환 의존을 측정하지 않았다.** `v2-지침서.md`
  §5가 요구하는 결합도 축이며 **M1 래칫 구현이 소유한다**(ADR 0007).
- **Kotlin 고유 형태(`internal`, 확장 함수, top-level 함수)에 대한 ArchUnit의 규칙 표현
  가능 범위를 확인하지 않았다**(§4).
- **모듈 안의 층 배치(패키지 규약)를 정하지 않았다** — M1 slice 1A의 구현 결정이다.
  이 ADR이 정한 것은 **모듈 사이**의 방향이다.
