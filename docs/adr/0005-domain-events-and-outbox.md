# ADR 0005 — domain event · outbox · 알림 전달 방식

- **상태**: 제안됨 (M0 slice 0D) — 사용자 승인과 Codex `approve` 대기
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **6** (domain event / outbox / notification 방식)
- **legacy 기준 commit**: `ed4b06c`
- **`OPEN-OPS-07`과의 관계**: 이 ADR이 OPS-21 후보 중 **outbox/스케줄러 · advisory lock ·
  resilience · 관측** 네 행의 **조사 결과를 기입**한다(그 항목의 종료 조건). **아키텍처
  규칙 강제 행은 ADR 0007**이 기입한다. **단 advisory lock 행은 판정이 끝나지 않았다** —
  후보 하나가 조사 범위 밖이었고 어느 후보도 OPS-01의 요구 셋을 덮는다고 확인되지
  않았다(§3.2 · §5 `OPEN-ADR-12`). **「후보 전건 판정 완료」를 이 ADR은 주장하지 않는다.**
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
- 대응 capability: **OPS-08 (a)(c)** · **COL-03**. `capability-map.md` OPS-21 표의
  「재시도·backoff·circuit breaker·rate limiter」 행이 그 둘을 대응 항목으로 적는다.
  **`OPS-13`은 이 행이 아니라 「아키텍처 규칙 강제」 행의 것**이며 그 항목의 정의는
  **「설계 래칫(비대화 방지)」**다 — §3.5와 ADR 0007이 소유한다.
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

### D-10. OPS-01의 lease는 **`workflow` 계층의 port**이고 스케줄러가 아니다

`capability-map.md` **OPS-01**(`V2 필수`)이 요구하는 것은 셋이다.

| # | OPS-01 요구 | 원문 |
| --- | --- | --- |
| **①** | lease가 **도메인 use case 경계**에 있다 | *"lease가 소비자마다 다른 레이어에 있는 형태는 채택하지 않는다 — 수집은 태스크 레이어, 모니터는 서비스 레이어라 **in-process 실행 경로에서 수집만 lease를 우회**한다(OPS-14). lease는 **도메인 use case 경계**에 둔다"* |
| **②** | 홀더 프로세스를 강제 종료하면 **즉시 해제** | acceptance: *"홀더 프로세스를 강제 종료하면 lease가 **즉시 해제**된다."* legacy는 **TTL이 없는 세션 advisory lock**으로 그 성질을 얻었다 — *"홀더가 죽으면 커넥션이 끊기며 즉시 해제되므로 … **죽은 홀더가 스케줄을 영구 점유할 수 없다**"* |
| **③** | 억제가 **감사 가능한 결과**를 남기고 사유가 **reason code(enum) + detail**로 분리된다 | acceptance: *"lease를 잡지 못한 실행이 조용히 사라지지 않고 관찰 가능한 결과를 남긴다"*, *"억제 사유 값이 재시도 횟수와 무관하게 동일하다"* |

**결정**: 이 셋은 **스케줄러 라이브러리가 주는 성질이 아니다.**

- **①·③은 애플리케이션 설계 사항이다.** lease를 **use case 진입 경계**에 두는 것과
  억제를 **reason code + detail로 기록**하는 것은 어느 라이브러리도 대신하지 않는다.
  따라서 **lease는 `workflow` 모듈의 port**이고, 도메인은 그 port만 본다(ADR 0006 D-3).
- **②는 어댑터의 성질이며 후보마다 다르다.** **이 ADR은 그 어댑터를 고르지 않는다** —
  §5 `OPEN-ADR-12`.

**따라서 D-5의 db-scheduler 채택은 OPS-01을 덮지 않는다.** db-scheduler가 덮는 것은
**예약 작업의 단일 실행**이고, OPS-01이 요구하는 것은 **use case 경계의 lease**다.
**두 축을 같은 결정으로 접지 않는다.**

### D-11. 재시도의 소유를 부작용 유형이 정한다

**D-3이 전달 의미를 부작용마다 선언하게 했으므로, 재시도도 그 선언을 따른다.**
채택한 도구가 둘(db-scheduler · Resilience4j)이므로 **어느 계층이 무엇을 재시도하는지**를
정하지 않으면 두 재시도가 곱해진다.

| 부작용 유형 | 재시도 | 소유 |
| --- | --- | --- |
| **채널 배달**(at-most-once, `OPEN-NOTI-02`) | **하지 않는다.** `running`에서 워커가 죽은 행은 **재실행하지 않고 격리**한다(OPS-03 acceptance) | 스케줄러도 Resilience4j도 이 작업에 재시도를 걸지 않는다 |
| **외부 호출**(KONEPS 등, 재시도 안전이 선언된 것) | **한 곳에서만** — **Resilience4j**의 bounded retry | 호출 지점. 스케줄러는 그 작업을 재실행하지 않는다 |
| **멱등 작업**(dedupe key가 있고 재시도 안전이 선언된 것) | 스케줄러의 재시도 예산 **하나만** | 스케줄러 |

**규칙**: **한 부작용에 재시도 계층은 하나다.** 두 계층이 같은 부작용을 재시도하면
예산이 곱해지고, `regression-ledger.md` §7의 백로그 폭주 계열이 그 형태다.

**채택한 도구의 기본값이 이 결정과 충돌할 수 있다.** Codex 리뷰(2026-08-28)가 db-scheduler
공식 문서를 근거로 지목했다 — *"one-time 실패를 5분 뒤 재시도하고 dead execution을
`ReviveDeadExecution`으로 즉시 재예약한다"*. **이 slice는 그 기본값을 공식 문서로
확인하지 않았다**(§6). 확인 여부와 무관하게 **결정은 위 표이며**, 구현은
**부작용 유형마다 `onFailure`/`onDeadExecution`을 명시적으로 지정**해야 한다 —
**기본값에 맡기지 않는다.** 실제 기본값의 확인과 그 지정은 **M4가 한다**(§5 `OPEN-ADR-13`).


## 3. 대안 — `OPEN-OPS-07` 후보의 판정

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

**이 행은 판정이 끝나지 않았다.** D-10이 OPS-01의 요구 셋을 세웠고 **어느 후보도 셋을
덮는다고 확인되지 않았다.** 아래 표는 **무엇이 확인됐고 무엇이 확인되지 않았는지**를
적으며, 채택은 **`OPEN-ADR-12`**가 닫힐 때 정해진다.

| OPS-01 요구 | db-scheduler | ShedLock | Spring Integration JDBC lock registry | PostgreSQL 세션 advisory lock |
| --- | --- | --- | --- | --- |
| **① use case 경계** | **아니다** — 예약 작업 실행 단위다 | **아니다로 읽힌다** — `@Scheduled` 위에 락을 얹는다(조사 노트) | **미조사** | **경계는 호출자가 정한다** — 기제 자체는 경계를 강제하지 않는다 |
| **② 홀더 종료 시 즉시 해제** | **미확인** — 설정 표면에 `heartbeat-interval`·`missed-heartbeats-limit`가 있다(조사 노트). **heartbeat 종속으로 읽히나 공식 문서로 확인하지 않았다** | **미확인** — 조사 노트에 해제 기제 서술이 없다 | **미조사** | **legacy가 이 성질로 얻었다** — TTL 없는 세션 락이라 커넥션이 끊기면 해제(OPS-01 분류 근거). **V2에서의 운용은 미조사** |
| **③ 억제의 감사 기록 · reason code + detail** | **아니다** — 라이브러리 관심사가 아니다 | **아니다** | **미조사** | **아니다** |

**③은 어느 후보도 주지 않는다** — D-10대로 **애플리케이션이 소유한다.**

| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **ShedLock** | **판정 보류 — OPS-01 요구에 대한 확인 미완** | **앞 라운드는 「db-scheduler와 기능 중복」을 사유로 불채택했으나 그것은 OPS-01을 덮지 못하는 판단이었다**(Codex 1차 high). db-scheduler가 덮는 것은 **예약 작업의 단일 실행**이고 OPS-01이 요구하는 것은 **use case 경계의 lease**다(D-10). ShedLock에 대해 **확인된 것**: 조사 노트가 *"**스케줄러가 아니다.** 스케줄 실행 자체는 Spring `@Scheduled` 등이 하고 ShedLock은 그 위에 락만 얹는다"*고 적는다 → **경계가 `@Scheduled` 메서드이지 use case 진입점이 아니다**(요구 ① 불충족으로 읽힌다). 호환·유지보수는 최상위권이다(7.x가 Boot 4.x·3.5·3.4를 함께 테스트, 최소 JVM 17, 7.7.0에 Micrometer 지표). **확인되지 않은 것**: 락 해제 기제(요구 ②) — 조사 노트에 서술이 없다. **AOP 프록시 무효화가 조용한 실패 모드**라는 것은 확인됐고, 채택한다면 `LockAssert` 테스트를 함께 둔다 |
| **Spring Integration JDBC lock registry** | **판정 보류 — 조사되지 않음** | OPS-21 표의 후보인데 `OPEN-OPS-07` 조사 노트의 대상 9종에 **없다**(그 노트에 문자열 매치 0). 버전·유지보수·호환 어느 것도 확인되지 않았다. **불채택이 아니라 미조사다.** **앞 라운드는 *"위 두 후보로 이 축이 덮이면 조사할 이유가 생기지 않고, 덮이지 않으면 조사가 선행돼야 한다"*고 적었으나 그 앞머리의 전제가 틀렸다** — D-10이 보인 대로 **두 후보 어느 것도 OPS-01의 세 요구를 덮는다고 확인되지 않았다.** 따라서 **조사가 선행돼야 한다**(§5 `OPEN-ADR-12`) |
| **PostgreSQL 세션 advisory lock 직접 사용** | **후보로 등재 — 미조사** | **legacy가 요구 ②를 이 기제로 얻었다** — *"TTL이 없는 것이 **의도된 설계**"*이고 *"홀더가 죽으면 커넥션이 끊기며 즉시 해제"*(OPS-01 분류 근거). ADR 0004 D-1이 **PostgreSQL 단일 엔진**을 확정했으므로 이 기제가 가용하다. **OPS-21 표에 없는 후보이며 이 ADR이 등재만 한다** — 전용 커넥션 운용 비용과 Spring 트랜잭션 경계와의 관계를 **확인하지 않았다**(§5 `OPEN-ADR-12`) |

### 3.3 재시도 · backoff · circuit breaker · rate limiter (OPS-08 (a)(c) · COL-03)

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

**2건.** 앞 라운드는 *"없다"*고 적으면서 §3.2의 미조사를 *"아직 필요가 생기지 않은 조사"*로
넘겼는데, **그 전제가 틀렸다** — D-10이 보인 대로 **OPS-01(`V2 필수`)을 덮는 수단이
정해지지 않았고, 그것은 필요가 이미 생긴 자리다**(Codex 1차 high).

### `OPEN-ADR-12` · OPS-01의 lease 어댑터

- **결정 필요 사항**: D-10의 요구 **②**(홀더 강제 종료 시 즉시 해제)를 무엇이 만족하는가.
- **선택지**: (a) **PostgreSQL 세션 advisory lock 직접 사용** — legacy가 이 성질로 얻었고
  ADR 0004 D-1의 단일 엔진 결정과 정합하나 전용 커넥션 운용 비용이 미조사다
  (b) **Spring Integration JDBC lock registry** — OPS-21 후보인데 **조사 자체가 없다**
  (c) **ShedLock** — 경계가 `@Scheduled`라 요구 ①과 어긋나 보이고 해제 기제가 미확인
  (d) 위를 어댑터로 감싸 **`workflow` port 뒤에 두고** 요구 ①·③은 애플리케이션이 소유.
  **(d)는 (a)~(c)와 배타가 아니다** — D-10이 이미 port를 확정했고 남은 것은 그 어댑터다.
- **선행 조사**: (b)의 **버전·유지보수·Kotlin 2.x + Spring Boot 3.x 호환**이
  `OPEN-OPS-07` 조사 범위 밖이었다. **판정하려면 그 조사가 선행한다.**
- **판정 방법**: OPS-01 acceptance *"홀더 프로세스를 강제 종료하면 lease가 즉시
  해제된다"*를 **Testcontainers PostgreSQL 통합 테스트**로 후보별로 재는 것
  (ADR 0004 D-1 · ADR 0007 D-1).
- **소유**: M1(port) · M4(어댑터 선택과 그 테스트).

### `OPEN-ADR-13` · db-scheduler 실패 처리 기본값

- **결정 필요 사항**: 부작용 유형별 `onFailure`/`onDeadExecution`을 무엇으로 지정하는가.
- **왜 미결인가**: **결정(D-11의 표)은 확정**이나 **채택 도구의 실제 기본값을 이 slice가
  공식 문서로 확인하지 않았다.** Codex 리뷰(2026-08-28)가 *"one-time 실패를 5분 뒤 재시도,
  dead execution을 `ReviveDeadExecution`으로 즉시 재예약"*이라고 지목했고, 그것이 사실이면
  **기본값이 D-3의 at-most-once와 정면으로 충돌한다.**
- **종료 조건**: 기본값을 공식 문서로 확인하고, **채널 배달 작업에 대해 재시도·재예약이
  일어나지 않도록 명시 지정**한 뒤 그것을 테스트로 고정하는 것.
- **소유**: M4. **M1이 버전을 고정할 때 함께 확인하면 더 이르다.**

### 이 ADR이 해소하지 않는 활성 `OPEN`

`OPEN-OPS-10`(DB 큐 계약과 OPS-06 재정의/폐기) · `OPEN-OPS-03`(큐 깊이 SLO 임계) ·
`OPEN-OPS-04`(legacy 임계값 재유도) · `OPEN-OPS-01`·`OPEN-REG-01`(라이브 크롤 rate-limit
오분류의 실제 영향). **어느 것도 해소하지 않는다.**

### 이 ADR이 선점하지 않는 활성 `OPEN` — 인접 축

**언급하지 않은 활성 `OPEN` 중 이 ADR의 서술과 인접한 것**을 적는다. 관계만 적고
**해소하지 않는다.**

| 활성 `OPEN` | 이 ADR이 하지 않는 것 |
| --- | --- |
| **`OPEN-OPS-02`** (스케줄 기본값 정책 — (a) 코드 선언 + 환경별 override (b) legacy 관례 유지) | D-5가 db-scheduler의 설정 표면이 **외부화된다는 성질**만 적는다. **기본값을 무엇으로 둘지, 스케줄이 기본 OFF인지는 정하지 않는다** |
| **`OPEN-OPS-08`** (realtime 이벤트 fanout이 V2 범위인가) | D-1이 정하는 것은 **내부 workflow 큐의 기반**이다. **realtime fanout의 범위 편입 여부는 다루지 않는다** |

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
- **Spring Integration JDBC lock registry를 조사하지 않았다**(§3.2) — `OPEN-ADR-12`.
- **db-scheduler·ShedLock의 락 해제 기제를 공식 문서로 확인하지 않았다**(§3.2 요구 ②).
  db-scheduler에 대해 확인된 것은 **설정 표면에 `heartbeat-interval`·`missed-heartbeats-limit`가
  있다**는 것까지이고, 그것이 「즉시 해제」를 배제하는지는 **읽은 것이 아니라 추론**이다.
- **db-scheduler의 실패·dead execution 기본값을 확인하지 않았다**(D-11) — `OPEN-ADR-13`.
  이 ADR이 인용한 값은 **Codex 리뷰가 지목한 것**이며 이 slice가 원문을 열지 않았다.
- **PostgreSQL 세션 advisory lock의 V2 운용 비용을 조사하지 않았다** — 전용 커넥션,
  커넥션 풀과의 관계, Spring 트랜잭션 경계와의 상호작용.
- **알림 채널의 구체 선택(Telegram/email/앱)을 이 ADR이 정하지 않는다** —
  `capability-map.md` NOTI 축과 `milestone-4.md`가 소유한다. 여기서 정한 것은 **전달
  메커니즘과 그 의미**다.
