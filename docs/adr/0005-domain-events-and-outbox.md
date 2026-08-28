# ADR 0005 — domain event · outbox · 알림 전달 방식

- **상태**: 제안됨 (M0 slice 0D) — 사용자 승인과 Codex `approve` 대기
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **6** (domain event / outbox / notification 방식)
- **legacy 기준 commit**: `ed4b06c`
- **이 ADR이 닫는 것**: **`OPEN-OPS-07`** — OPS-21 후보 중 outbox/스케줄러 · advisory lock ·
  resilience · 관측 네 행. 아키텍처 규칙 강제 행은 **ADR 0007**이 닫는다.
- **관련 ADR**: 0004(데이터베이스 — 이 메커니즘의 기반) · 0003(ML 호출 축 — **다른 축**) · 0006

---

## 1. 맥락

### 1.1 이 ADR이 다루는 축

`reports/evidence/m0/0a2/decisions.md` `OPEN-OPS-05` 절이 두 축을 갈랐다(ADR 0003 §1.1에
같은 인용이 있다). **이 ADR은 내부 workflow 큐 축**(주기 수집, 알림 발송, 재시도,
outbox)을 소유한다. Kotlin ↔ ml-engine 호출 경로는 ADR 0003이 소유한다.

### 1.2 이미 확정된 운영자 결정

**`OPEN-OPS-05` — 결정 완료** (운영자 2026-08-26):

> **결정**: 메시지 브로커(RabbitMQ 등)를 V2에 두지 않는다. 스케줄·outbox·재시도는
> DB 기반으로 구현한다. 측정된 필요가 나타나기 전에는 브로커를 도입하지 않는다.

같은 절: 운영 형태는 **1인**이며 실시간 경보를 전제하지 않는다.

**`OPEN-NOTI-02` — 결정 완료** (운영자 2026-08-26): **at-most-once(놓침을 감수)**.
`capability-map.md` OPS-03 acceptance가 그 귀결을 적는다 — *"`running` 상태에서 워커를
죽이면 그 채널의 행은 **재전송되지 않고 격리된다.** 모호한 배달을 재시도해 중복을 만들지
않는다"*, 그리고 *"놓친 배달이 앱 알림함에서 조회된다."*

**상호작용 모델 pull** (운영자 2026-08-26, `capability-map.md` §0.7): 알림은 강제 처리
대상이 아니라 제안이고 **앱 알림함(NOTI-10)이 기록이자 회수 경로**다.

### 1.3 legacy가 남긴 것 — 정책은 가치가 있고 구현은 아니다

`capability-map.md` OPS-21의 권고:

> legacy가 직접 구현한 outbox의 가치는 코드가 아니라 **at-most-once 모호성 격리 정책**에
> 있고, 그 정책은 어떤 라이브러리 위에도 얹을 수 있다. 반대로 v2-지침서 §7에 따라 측정된
> 필요 없이 무거운 도구를 추가하지 않는다.

`regression-ledger.md` §7이 이 축의 회귀 8건을 등재했다(`R-ASYNC-01`~`08`) — 백로그 적체가
무신호로 쌓인 것, "투입 ≤ 소진" 부등식이 config 주석에만 있던 것, lease 경합에서 진 쪽이
즉시 포기해 한 카테고리가 굶은 것, dialect 분기가 무조건 성공을 반환한 것, 주기 태스크
메시지에 수명이 없어 백로그가 정지 시간에 비례해 자란 것, 선택 순서가 `id ASC`라 노후도
상한 보장이 기아가 된 것, **관측이 전부 "생산된 행" 기반이라 소비자가 막히면 KPI가 전부
초록이던 것.**

---

## 2. 결정

### D-1. 이벤트 전달의 기반은 애플리케이션 데이터베이스다

§1.2의 운영자 결정을 그대로 채택한다. 브로커를 두지 않고 스케줄·outbox·재시도를
PostgreSQL(ADR 0004) 위에 둔다. **이 ADR은 그 결정을 재확정하지 않고 인용한다.**

### D-2. 도메인 write와 부작용 등록은 같은 트랜잭션에서 커밋된다

`capability-map.md` OPS-03의 **결정 무관(무조건)** acceptance 첫 항목이다 —
*"도메인 write와 outbox 행이 같은 트랜잭션에서 커밋되고, write 롤백 시 outbox 행도 없다."*
**이것이 outbox의 정의이며 라이브러리 선택의 1차 기준이다**(D-5).

### D-3. 전달 의미는 부작용마다 선언한다

- `capability-map.md` OPS-03: *"부작용마다 재시도 안전성(외부 dedupe 가능 여부)이 선언돼
  있고, **선언 없는 부작용은 outbox에 등록되지 않는다**."*
- **채널 배달은 at-most-once**다(§1.2). 모호한 배달을 재시도해 중복을 만들지 않고 격리한다.
- 동일 dedupe key의 동시 삽입 2건은 1개 행으로 수렴하고, 최대 시도를 소진한 행은 종단
  상태로 가 sweep을 무한 점유하지 않는다(같은 acceptance).

### D-4. 채널 억제는 기록 억제가 아니다

`capability-map.md` NOTI-01: *"웹 알림은 **항상** 만들고 채널 push는 게이트를 통과할 때만
보낸다"*, *"억제는 채널 억제이지 기록 억제가 아니다."* §1.2의 pull 모델에서 **앱 알림함이
회수 경로**이므로, 채널 배달 실패가 알림 자체의 소실이 되지 않는다.

### D-5. 스케줄러와 트랜잭션 스테이징은 **db-scheduler**를 채택한다

- **결정적 근거**: db-scheduler README가 **트랜잭션 스테이징을 OSS 1급 예제로 문서화**한다 —
  *"TransactionallyStagedJob — Example of transactionally staging a job, i.e. making sure
  the background job runs **iff** the transaction commits (along with other
  db-modifications)."* 이것이 D-2가 요구하는 성질 그 자체다.
- 범위가 제약과 일치한다 — *"A relational database and a **single `scheduled_tasks`
  table**"*(README *Requirements*). 브로커를 전제하지 않는다.
- Apache-2.0, 유료 에디션 경계 없음. Boot 3용·4용 스타터를 분리 제공하므로
  `OPEN-ADR-01`(Boot 세대)의 미결정과 분리된다.
- 설정 표면이 `application.properties`로 외부화된다 — `v2-지침서.md` §5의 *"환경·운영 값은
  설정 파일로 외부화"*와 방향이 같다.
- **확인하지 않은 것**: Kotlin 버전 호환의 **공식 서술이 존재하지 않는다**(Java
  라이브러리이며 README·릴리스 노트·저장소에 Kotlin 축이 없다). *"호환된다"가 아니라
  **"그 축의 서술이 없다"**가 확인된 전부다. 해소는 M1 slice 1A의 스모크 빌드다.
- **채택의 알려진 공백**: 대시보드·UI가 없고 README에 Micrometer 언급이 없다. 관측은
  D-7의 직접 작성 경로로 간다.

### D-6. 재시도·백오프·서킷브레이커·rate limiter는 **Resilience4j**를 채택한다

- 한 라이브러리가 이 축 전부를 덮고 Apache-2.0이다. `resilience4j-spring-boot3` 모듈이
  있고 2.4.0에 Boot 4 지원이 들어가 `OPEN-ADR-01`과 분리된다.
- 정책을 `application.yml`로 외부화하는 것이 기본 사용법이다(`v2-지침서.md` §5).
- 대응 capability: OPS-13(재시도·서킷브레이커), OPS-08 (a)(c)·COL-03(rate limiter).
- **채택과 함께 기록해야 할 위험**:
  - 릴리스 간격이 후보 중 가장 길다(2.3.0 → 2.4.0이 약 14개월). 보안 수정 대기가 길 수 있다.
  - **AOP 애스펙트 순서가 문서화된 실패 모드다** — 2.4.0에 *"Clarify Aspect Order defaults
    for Spring Boot 3 to prevent **metric inflation**"* 문서 수정이 들어갔다. 잘못 배치하면
    **지표가 부풀려지므로 D-7의 관측 신뢰성과 직접 얽힌다.**
  - released 2.4.0의 Java 베이스라인은 17이나 master는 21로 올라가 있다. **다음 릴리스의
    베이스라인은 확인 불가**이며 M1의 JDK 선택과 얽힌다.

### D-7. 관측은 **Micrometer**이고, DB backlog 깊이 게이지는 직접 작성한다

- Micrometer는 **도입 여부가 결정이 아니다** — Spring Boot Actuator의 지표 계층이며
  Boot BOM이 버전을 관리한다. 실제 결정은 *"무엇을 계측할지"*다.
- `capability-map.md` OPS-21이 브로커 없음 결정의 파급으로 이미 문구를 고쳤다 —
  *"Micrometer(+ **DB backlog 깊이 게이지는 직접 작성**)"*. **브로커 큐 깊이 바인더는
  존재 이유가 사라졌고**, DB 기반 큐 깊이를 재는 기성 바인더는 **이번 조사에서 확인하지
  못했다.**
- **버전 사실(기록)**: Boot 3.5.x BOM은 Micrometer **1.15.12**를 관리하고
  **1.15.x의 OSS 지원은 2026-06에 종료**됐다(micrometer.io/support). Boot 4.1.x BOM은
  1.17.1(OSS 2027-07까지)을 관리한다. **이 사실은 `OPEN-ADR-01`(Boot 세대)의 입력이다.**

> **이 ADR이 정하지 않는 것 — `OPEN-OPS-10` 선점 금지.**
> 활성 `OPEN-OPS-10`이 묻는 네 가지는 **여기서 답하지 않는다**: ① 깊이를 무엇으로 재는가
> ② 그 읽기가 부작용 없는 격리 수준은 무엇인가 ③ 소비자 사망 후 작업 재가시화 시간은
> 무엇으로 구현되는가 ④ 배포 전 토폴로지 게이트(OPS-06)를 DB 큐 위에서 재정의할 것인가
> 폐기할 것인가. **라이브러리를 고르는 것은 그 계약을 정하는 것이 아니다** — `OPEN-OPS-10`
> 자신이 후보로 OPS-21의 DB 기반 라이브러리를 지목한다. 임계·SLO는 활성 `OPEN-OPS-03`·
> `OPEN-OPS-04`가 따로 들고 있으며 이 ADR은 값을 쓰지 않는다.

### D-8. 관측은 세 축이며 산출물 축만으로 건강을 판정하지 않는다

`capability-map.md` **OPS-17**(분류 `폐기` — 구조적 맹점)의 V2 대체를 채택한다 —
*"관측 축을 **산출물 + 배압(큐·백로그 깊이) + 지연** 세 축으로 분리하고 산출물 축만으로
건강을 판정하지 않는다. **'산출물의 부재'가 관측 가능해야 한다.**"*
근거는 `R-ASYNC-08`이다 — 소비자가 막히면 볼 행 자체가 생기지 않아 KPI가 전부 초록이었다.

### D-9. 도메인은 전달 메커니즘을 모른다

`v2-지침서.md` §4.5: *"외부 호출, DB write, event publish, notification은 port 뒤에 둔다."*
db-scheduler·Resilience4j·Micrometer 타입은 `adapters`/`app` 모듈에만 있고 도메인에
들어가지 않는다. 강제 수단은 **ADR 0006**(의존 방향)과 **ADR 0007**(architecture test)이
소유한다.

---

## 3. 대안 — `OPEN-OPS-07` 후보의 채택/불채택

**판정 기준은 "최신 버전"이 아니라 M1에서 고정할 Kotlin 2.x + Spring Boot 3.x 조합과의
호환**이다(`v2-지침서.md` §5, `decisions.md` `OPEN-OPS-07`). 조사 원본은
`_workspace/m0-open-decisions/ops07-library-survey.md`(2026-08-26)이며, **그 노트를 옮겨
적지 않고 판정을 떠받치는 사실만 출처와 함께 인용한다.**

### 3.1 transactional outbox / 스케줄러 (OPS-03 · OPS-00)

| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **db-scheduler** | **채택** | D-5 |
| **JobRunr** | **불채택** | ① **Spring `@Transactional` 연동(transaction plugin)이 Pro 전용**이다(jobrunr.io Pro 문서). **부작용 등록이 도메인 트랜잭션과 함께 커밋되는 것이 outbox의 핵심**(D-2)인데 그것이 OSS 범위 밖이다. ② Rate Limiters·Mutexes도 Pro 전용이라 OPS-08(a)(c)·OPS-01(lease) 축을 OSS로 덮지 못한다. ③ **후보 9종 중 유일하게 Apache-2.0이 아니다** — LGPL v3 또는 상용의 다중 라이선스(`License.md`). ④ 대시보드·다중 서버 오케스트레이션은 **1인 운영·브로커 없음** 맥락에서 측정된 필요가 없다(`v2-지침서.md` §7). **기술 호환 자체는 확인됐다** — Kotlin 2.4를 버전을 특정해 공식 지원한다고 선언한 **유일한 후보**이고(8.8.0 릴리스 노트) OSS core에 PostgreSQL SQL StorageProvider가 있다. **불채택 근거는 호환이 아니라 OSS 경계와 라이선스다** |
| **Spring Modulith 이벤트 외부화** (`spring-modulith-events-jdbc`) | **불채택 (현 시점)** · 재검토 예약 | ① **프레임워크 결합도가 후보 중 가장 높다.** `v2-지침서.md` §5는 *"domain은 프레임워크 독립적인 port 인터페이스에 의존하고, Spring wiring은 `app`/`adapters`에만 둔다"*고 규정하는데, Modulith의 모듈 모델은 Spring 애플리케이션 구조를 전제한다. ② **Modulith의 "모듈"은 기본적으로 패키지 단위 개념**인 반면 ADR 0006은 **Gradle 멀티모듈 경계**를 쓴다. 빌드 모듈로 이미 물리 분리된 구조에서 Modulith 모듈 검증이 무엇을 더 막아 주는지는 **측정된 필요로 확인해야 한다**(§7). ③ 1인 운영에서 모듈 문서 생성·관측 기능의 필요가 측정되지 않았다. **기술 호환과 유지보수는 최상위권이다** — Boot 3.x(1.4.x)·4.0(2.0.x)·4.1(2.1.x) **세 라인을 병행 유지**하며 2026-08-26에 셋 다 패치했고, `-jdbc`가 브로커 없는 event publication registry 경로를 공식 제공한다. **재검토 조건**: D-2를 직접 구현하는 비용이 실제로 드러나거나, 모듈 경계 검증에서 ArchUnit이 표현하지 못하는 규칙이 필요해질 때 |

### 3.2 advisory lock 추상화 (OPS-01)

| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **ShedLock** | **불채택 (현 시점)** | **db-scheduler가 heartbeat 기반 단일 실행을 자체 보장**하므로 기능이 상당 부분 겹친다. ShedLock은 스케줄러가 아니라 `@Scheduled` 위에 락만 얹는 도구다. 겹치는 도구를 둘 다 두면 `v2-지침서.md` §5의 *"같은 규칙·변환·판정이 두 곳에 존재하면 한 곳은 회귀 지점이다"*에 해당한다. **호환·유지보수는 후보 중 최상위권이다** — 7.x가 **Boot 4.x·3.5·3.4를 함께 테스트**하고(공식 호환 매트릭스, 최소 JVM 17) 7.7.0에서 Micrometer 지표가 추가됐으며 커밋이 상시 돈다. **재검토 조건**: db-scheduler가 덮지 못하는 `@Scheduled` 기반 경로나 스케줄 외 상호배제가 필요해질 때. 그때는 **AOP 프록시 무효화가 조용한 실패 모드**이므로 `LockAssert` 테스트를 함께 둔다 |
| **Spring Integration JDBC lock registry** | **판정 보류 — 조사되지 않음** | OPS-21 표의 후보인데 `OPEN-OPS-07` 조사 노트의 대상 9종에 **없다.** 버전·유지보수·호환 어느 것도 확인되지 않았다. **불채택이 아니라 미조사다** — 위 두 후보로 이 축이 덮이면 조사할 이유가 생기지 않고, 덮이지 않으면 조사가 선행돼야 한다 |

### 3.3 재시도 · backoff · circuit breaker · rate limiter (OPS-13 · OPS-08(a)(c) · COL-03)

| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **Resilience4j** | **채택** | D-6. 이 행에 다른 후보가 없다 — OPS-21 표가 단일 후보를 적었고 조사도 대안을 세우지 않았다. **대안 없음을 확인한 것이 아니라 대안을 조사하지 않았다** |

### 3.4 관측 (OPS-04 · OPS-17)

| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **Micrometer** | **채택 (선택의 여지가 사실상 없다)** | D-7. Boot Actuator의 지표 계층이며 BOM이 버전을 관리한다 |
| **브로커 큐 깊이 바인더** | **대상 아님** | 브로커가 없어졌다(§1.2). OPS-21이 그 문구를 이미 *"DB backlog 깊이 게이지는 직접 작성"*으로 교체했다 |

### 3.5 아키텍처 규칙 강제 (OPS-13)

**ADR 0007**이 소유한다 — ArchUnit · Konsist · Detekt의 판정은 그 문서에 있다.

---

## 4. 결과

- **db-scheduler의 `scheduled_tasks` 테이블이 스키마에 들어온다.** migration은 ADR 0004
  D-2의 규칙을 따른다 — 라이브러리가 만드는 테이블도 versioned SQL로 선언한다.
- **outbox의 전달 의미 선언이 도메인 계약이 된다**(D-3). 선언 없는 부작용을 등록하지
  못하게 하는 것은 타입의 일이며, `v2-지침서.md` §5의 *"불법 상태를 타입으로 표현
  불가능하게 만든다"*에 해당한다.
- **관측 지표를 직접 만들어야 한다**(D-7) — Micrometer가 OPS-04를 자동으로 해결하지 않는다.
  그 게이지가 무엇을 재는지는 `OPEN-OPS-10`이 닫힌 뒤에 정해진다.
- **Resilience4j의 AOP 애스펙트 순서가 CI에서 확인돼야 한다**(D-6) — 잘못 배치하면 지표가
  부풀려지고, 부풀려진 지표는 D-8의 배압 축을 거짓 초록으로 만든다.
- `milestone-4.md`가 이 축의 마일스톤이며 *"deadline, cancellation, circuit breaker,
  bounded retry"*를 slice로 갖는다.

---

## 5. 이 ADR이 등록하는 `OPEN`

**없다.** 이 축의 미결은 이미 활성 registry에 있다 — `OPEN-OPS-10`(DB 큐 계약과 OPS-06
재정의/폐기), `OPEN-OPS-03`(큐 깊이 SLO 임계), `OPEN-OPS-04`(legacy 임계값 재유도),
`OPEN-OPS-01`·`OPEN-REG-01`(라이브 크롤 rate-limit 오분류의 실제 영향).
**이 ADR은 그중 어느 것도 해소하지 않는다.**

§3.2의 **Spring Integration JDBC lock registry 미조사**는 새 `OPEN`으로 등록하지 않는다 —
그것은 결정이 필요한 쟁점이 아니라 **아직 필요가 생기지 않은 조사**다. 필요가 생기면
그 slice가 조사한다.

---

## 6. 확인하지 않은 것

- **네 라이브러리(db-scheduler · ShedLock · ArchUnit · Spring Modulith)의 Kotlin 버전
  호환 공식 서술은 존재하지 않는다.** 넷 다 Java 라이브러리이고 공식 문서·README·호환표
  어디에도 Kotlin 버전 축이 없다. **"없음"을 확인했을 뿐 "호환됨"을 확인한 것이 아니다.**
  해소는 M1 slice 1A에서 실제 고정 버전으로 스모크 빌드.
- **DB 기반 큐 깊이용 Micrometer 기성 바인더를 확인하지 못했다.** Micrometer 번들 바인더
  목록 전체를 조회하지 않았고, db-scheduler README에는 Micrometer 언급이 없다.
- **Resilience4j 2.4.0 이후의 Java 베이스라인을 확인하지 못했다.**
- **Spring Modulith 1.4.x(Boot 3.x 라인)의 유지보수 종료 시점을 확인하지 못했다.**
  1.4.13이 2026-08-26에 나온 사실은 확인했으나 언제까지 패치하는지에 대한 공식 서술을
  찾지 못했다.
- **Spring Modulith 공식 레퍼런스의 호환 매트릭스는 최신 상태가 아니다**(최고 행이
  "2.0 snapshot"). 이 ADR의 버전 대응은 **릴리스 노트**를 인용했고 문서 매트릭스를
  인용하지 않았다.
- **Spring Integration JDBC lock registry를 조사하지 않았다**(§3.2).
- **알림 채널의 구체 선택(Telegram/email/앱)을 이 ADR이 정하지 않는다** —
  `capability-map.md` NOTI 축과 `milestone-4.md`가 소유한다. 여기서 정한 것은 **전달
  메커니즘과 그 의미**다.
