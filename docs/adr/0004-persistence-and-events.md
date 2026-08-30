# ADR 0004 — 데이터베이스와 migration 도구

- **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
  (`reports/evidence/m0/0d/codex-review-20260829T222217Z.json`, `reviewed_base` `998dc217` …
  `reviewed_head` `df056259`, 2026-08-29, `model_reasoning_effort=high`). 갱신 시점은
  **2026-08-30 · M0 종료 slice 0E**다 — 0D 종료 뒤에도 이 줄이 *"제안됨 … 대기"*로 남아
  **문면이 사실을 따라가지 않았다.**
  **`milestone-0.md` 완료 조건의 「사용자 명시 승인」을 받았다** — **운영자, 2026-08-31**.
  물음 *"「M0 승인」이 ADR 0001~0009 채택까지 덮는가"*에 **「덮는다 — ADR 채택 포함」**.
  **이 승인은 Codex `approve`가 만든 것이 아니다** — 위 0D `approve`와 별개로 운영자에게
  직접 물어 받았다. 기록의 **정본**은 `milestone-0.md` 「승인에 드는 것」의 ADR 행이며,
  같은 승인의 다른 대상(활성 `OPEN` 이월)은 `docs/discovery/capability-map.md` §14다.
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **5** (DB 및 migration 도구)
- **legacy 기준 commit**: `ed4b06c`
- **관련 ADR**: **0005**(domain event · outbox · notification) · 0001(두 runtime) · 0007(검증)

> **파일 이름과 내용에 대하여.** 이 파일 이름은 `milestone-0.md` 「산출물」이 지정한
> `docs/adr/0004-persistence-and-events.md`이고 "events"를 포함한다. 그러나 **같은 문서의
> 결정 목록은 5(DB 및 migration 도구)와 6(domain event / outbox / notification 방식)을
> 별개 결정으로 센다.** 지정된 이름을 바꾸지 않으면서 최소 9건을 만족하기 위해,
> **이 ADR은 결정 5를 담고 결정 6은 ADR 0005가 담는다.** 그 대응표는
> `reports/evidence/m0/0d/scope.md`에 있다.

---

## 1. 맥락

### 1.1 승인된 문서가 이미 엔진과 도구를 지목한다

| 문서 | 문면 |
| --- | --- |
| `milestone-3.md` Slice 3D | *"**Flyway** migration과 **Testcontainers PostgreSQL** test"* |
| `milestone-6.md` Slice 6B | *"clean **PostgreSQL**에서 **Flyway** 전체 재현"*, *"backup/restore와 migration rollback 정책"* |
| `milestone-6.md` Slice 6C | *"Kotlin app, Python ML serving, **PostgreSQL**, broker가 필요한 경우에만 구성"* |
| `milestone-1.md` 범위 밖 | *"Spring controller, DB schema, **Flyway**"* |
| `v2-지침서.md` §5 | *"JUnit 5 + Kotest/AssertJ, property test, **Testcontainers**를 사용한다"* |

**이 ADR이 새로 고르는 것이 아니라, 흩어져 있는 그 선택을 하나의 결정으로 고정하고
무엇을 금지하는지를 적는다.**

### 1.2 legacy의 형태

`app/core/config.py:10`이 `DEFAULT_DATABASE_URL`을 `POSTGRES_DRIVERNAME` 기반으로 만들고,
`app/core/config.py:40-42`의 `DATABASE_URL` 기본값이 `postgresql+psycopg` 드라이버를
지목한다. **두 줄의 원문은 이 문서에 옮기지 않는다** — 자격증명 형태의 문자열이 들어 있고
`agent-workflow.md` §6이 그런 문자열을 생성물에 남기지 않도록 규정한다. 확인하는 명령은
`reports/evidence/m0/0d/commands.md` **C-5.4**에 있다.

legacy도 PostgreSQL이고 migration은 **Alembic**이다(`alembic.ini`, `alembic/`).
`requirements/runtime.txt`에 `sqlalchemy` · `psycopg[binary]` · `pgvector` · `sqlmodel` ·
`alembic`이 있다. (재현: `commands.md` **C-5.4**)

### 1.3 다중 엔진 지원이 만든 회귀

`regression-ledger.md` §7이 **같은 형태의 결함을 두 건** 등재했다.

- **`R-ASYNC-04`** — 단일비행 lease가 PostgreSQL이 아니면 **무조건 성공을 반환한다.**
- **`R-ASYNC-05`** — outbox claim의 `FOR UPDATE SKIP LOCKED`도 같은 dialect 분기를 갖는다.

`capability-map.md` **OPS-16**(프로덕션 DB 전용 semantics의 다른 엔진 무음 no-op)이 같은
축을 capability로 들고 있고 V2 제약을 이미 적었다:

> 동시성·격리 semantics는 프로덕션 DB에서 검증한다. 프로덕션 엔진에서만 유효한 코드 경로는
> 프로덕션 엔진 테스트 없이 머지 불가(CI 게이트). **dialect 분기의 fallback이 "항상
> 성공"이면 안 된다** — 지원하지 않는 엔진은 실패해야 한다.

같은 절이 그 함정의 전례를 적는다 — *"엔티티 DISTINCT가 프로덕션 json 컬럼에서만 죽었고
SQLite 스위트는 잡지 못해 **라이브에서야 드러났다**"*(SET-09 F-2).

---

## 2. 결정

### D-1. 데이터베이스는 PostgreSQL 하나다

- 개발·테스트·운영이 **같은 엔진**이다. 테스트는 **Testcontainers PostgreSQL**을 쓴다
  (`milestone-3.md` 3D, `v2-지침서.md` §5).
- **대체 엔진을 위한 dialect 분기를 만들지 않는다.** 엔진이 PostgreSQL이 아니면 코드는
  **실패해야 하고**, 조용히 성공을 반환할 수 없다(§1.3의 `R-ASYNC-04`·`R-ASYNC-05`·OPS-16).
- 근거: ① §1.1의 승인된 문서 ② §1.3의 회귀 실물 ③ **ADR 0005의 DB 기반 스케줄·outbox·
  lock이 PostgreSQL의 동시성 의미론(`FOR UPDATE SKIP LOCKED`, advisory lock)에 의존**한다.
  엔진 선택은 그 축과 분리되지 않는다.

### D-2. migration 도구는 Flyway이고, migration이 스키마의 단일 출처다

- **versioned SQL migration**이 스키마를 정의한다. `milestone-6.md` 6B가
  *"clean PostgreSQL에서 Flyway 전체 재현"*을 완료 조건으로 둔다 — **빈 DB에서 migration만
  돌려 현재 스키마가 나와야 한다.**
- **ORM이 스키마를 생성하지 않는다.** Hibernate `ddl-auto`로 스키마를 만들거나 갱신하는
  경로를 두지 않는다. 엔티티 매핑과 스키마가 어긋나면 그것은 실패로 드러나야 하며
  자동 정정 대상이 아니다.
- **rollback 정책을 갖는다** — `milestone-6.md` 6B가 *"migration rollback 정책"*을 요구한다.
  구체 절차는 그 slice가 소유한다.

### D-3. 스키마의 소유자는 Kotlin 애플리케이션 하나다

- **ml-engine은 migration을 만들지 않는다.** `v2-지침서.md` §3.2가 serving 프로세스에
  SQLAlchemy·DB driver를 금지했고, training은 `adapters/`(training 전용 dataset/object
  storage adapter)를 통해서만 데이터에 닿는다.
- 두 runtime이 같은 스키마에 write하면 **소유가 둘이 되고 migration 순서가 계약이 된다.**
  그것을 만들지 않는다.

> **이 ADR이 정하지 않는 것**: training이 데이터셋을 **어떤 형태로** 받는지(운영 DB
> 읽기 전용 접근인지, 내보낸 데이터셋 파일인지)는 정하지 않는다. `milestone-5.md`와
> M2 계약이 소유한다. 여기서 정하는 것은 **스키마 소유자가 하나**라는 것뿐이다.

### D-4. legacy 스키마와의 자동 호환을 전제하지 않는다

`v2-지침서.md` §1의 "가져오지 않는 것"에 *"기존 DB schema와 public API의 자동 호환성"*이
있다. 따라서:

- **Alembic 이력을 승계하지 않는다.** V2의 migration은 `V1`부터 시작한다.
- legacy 데이터의 이관은 별도 판단이며, 이관하는 값의 provenance는 ADR 0002 D-7의 제약을
  받는다. **운영자 결정 `OPEN-DEC-08`이 이미 한 경계를 그었다** — *"legacy `clean` 라벨을
  V2 corpus에 승계하지 않는다. write provenance를 갖고 새로 수집·판정한 행만 clean으로
  인정하고, legacy에서 가져오는 행은 provenance 미상으로 격리한다"*
  (`reports/evidence/m0/0a2/decisions.md`).

### D-5. 원본과 파생을 스키마가 구분한다

`milestone-3.md` 3D가 요구한다 — *"raw observation과 canonical fact 분리"*, *"파생값이
공식값을 조용히 덮지 못하는 precedence rule"*, *"원본, 판정, 오류를 감사 가능하게 보존"*.
`milestone-6.md` 6B가 *"raw/canonical/audit/outbox 데이터의 수명과 masking"*을 더한다.

이 ADR은 그 요구를 **스키마 결정으로 확정**한다 — raw · canonical · audit는 서로 다른
자리를 갖고, 파생 write가 원본 자리를 덮는 경로가 스키마에 존재하지 않는다.
`R-PROV-04`(수집 write가 매 주기 백필의 재태깅을 되돌렸다)가 그 실물이다.

### D-6. 확장(extension) 채택은 여기서 정하지 않는다

legacy는 `pgvector`를 쓴다(§1.2). V2가 그것을 쓰는지는 **그것을 요구하는 capability의
분류에 종속**되며, 이 ADR은 정하지 않는다. 확장을 추가하면 `milestone-6.md` 6C의
컨테이너 구성과 6B의 clean 재현에 영향한다.

---

## 3. 대안

| # | 대안 | 판정 | 사유 |
| --- | --- | --- | --- |
| **A-1** | **다른 RDBMS** (MySQL 등) | **불채택** | §1.1의 승인된 문서가 PostgreSQL을 명시한다. 더해서 ADR 0005의 DB 기반 스케줄·outbox·lock이 PostgreSQL 의미론에 의존한다. **다른 엔진을 비교 측정하지 않았다** — 기각 근거는 승인된 문서와 하류 의존이지 벤치마크가 아니다 |
| **A-2** | **다중 엔진 지원** (운영 PostgreSQL + 테스트 SQLite) | **불채택** | §1.3이 그 형태의 실물 결과다 — lease가 무조건 성공을 반환하고, `FOR UPDATE SKIP LOCKED`가 같은 분기를 갖고, json 컬럼 결함이 **라이브에서야 드러났다.** 테스트가 빨라지는 대가로 **테스트가 프로덕션 경로를 통과하지 않게 된다** — `agent-workflow.md` §4가 그것을 `request_changes` 사유로 열거한다 |
| **A-3** | **Liquibase** | **불채택** | `milestone-3.md` 3D와 `milestone-6.md` 6B가 Flyway를 명시하며 그 문서는 승인된 기준이다. **Liquibase와의 비교 측정을 이번에 하지 않았다** — 두 도구의 버전·호환 조사는 `OPEN-OPS-07`의 대상 9종에 없다(§5) |
| **A-4** | **Alembic 유지** (Python 도구가 Kotlin 스키마를 소유) | **불채택** | ① `v2-지침서.md` §1의 두 갈래 전략에서 **service 레이어는 Kotlin**이고 스키마는 그 레이어의 것이다. ② Alembic은 SQLAlchemy 모델에 결합돼 있고, V2 Kotlin에는 그 모델이 없다. ③ D-3의 소유자 단일화와 충돌한다 |
| **A-5** | **JPA/Hibernate `ddl-auto`로 스키마 생성** | **불채택 (D-2)** | migration 이력이 없어지고 `milestone-6.md` 6B의 *"clean PostgreSQL에서 전체 재현"*과 rollback 정책을 만족할 수 없다. 엔티티와 스키마의 불일치가 **조용한 자동 정정**으로 사라진다 |
| **A-6** | **수작업 SQL 스크립트** (도구 없음) | **불채택** | 적용 여부·순서·이력이 사람의 기억에 남는다. `v2-지침서.md` §5의 *"회귀 방어를 사람의 주의력에 맡기지 않는다"*와 정면으로 충돌한다 |

---

## 4. 결과

- **CI가 프로덕션 엔진을 요구한다.** 동시성·격리에 의존하는 코드 경로는 Testcontainers
  PostgreSQL 테스트 없이 머지될 수 없다(OPS-16의 V2 제약). 테스트 시간이 늘어나는 것이
  이 결정의 비용이다.
- **`milestone-1.md`는 DB를 건드리지 않는다** — 그 마일스톤의 범위 밖 목록에
  *"Spring controller, DB schema, Flyway"*가 있다. 스키마는 `milestone-3.md` 3D에서
  처음 생기고 `milestone-6.md` 6B에서 완결된다.
- **ADR 0005의 스케줄·outbox·lock이 이 결정 위에 선다.** 브로커가 없으므로
  (`OPEN-OPS-05`, 운영자 결정 2026-08-26) 그 메커니즘의 기반은 이 데이터베이스다.
- **백업/복구와 수명·마스킹 정책**은 `milestone-6.md` 6B가 소유한다. 이 ADR은 그 요구가
  존재한다는 사실만 고정한다.

---

## 5. 이 ADR이 등록하는 `OPEN`

**없다.**

**왜 migration 도구가 `OPEN`이 아닌가**: `OPEN-OPS-07`의 조사 대상 9종에 Flyway도
Liquibase도 없다 — 그 조사는 OPS-21 표의 후보(outbox/스케줄러 · lock · resilience ·
아키텍처 규칙 · 관측)만 다뤘다. 그러나 **migration 도구는 근거가 없는 자리가 아니다** —
`milestone-3.md`와 `milestone-6.md`가 Flyway를 명시적으로 지정했고 그 문서는 승인된
기준이다. 근거가 없어서 미결인 것과, 근거가 다른 문서에 있는 것은 다르다.

**대신 §6에 확인하지 않은 것으로 남긴다** — Flyway의 Kotlin 2.x + Spring Boot 3.x 조합
호환은 이번에 조사되지 않았고, `OPEN-ADR-01`(Boot 세대)과 함께 **M1 slice 1A의 스모크
빌드**에서 확인해야 한다. 이는 조사 노트가 U-1에 대해 지정한 것과 같은 해소 방법이다.

---

## 6. 확인하지 않은 것

- **Flyway·Liquibase의 버전·유지보수·호환을 조사하지 않았다.** `OPEN-OPS-07` 조사의
  대상이 아니었다. 채택 근거는 승인된 마일스톤 문서이며 버전 호환 사실이 아니다.
  M1에서 실제 고정 버전으로 확인한다.
- **Spring Boot BOM이 Flyway 버전을 관리하는지 확인하지 않았다.** 관리한다면
  `OPEN-ADR-01`(Boot 세대)의 결정이 Flyway 버전에도 파급된다.
- **PostgreSQL 버전을 고정하지 않았다.** `milestone-6.md` 6C가 *"dependency/image
  version 고정과 SBOM/vulnerability check"*를 요구하므로 그 slice가 소유한다.
- **legacy 데이터 이관 범위를 정하지 않았다.** D-4가 정한 것은 *"자동 호환을 전제하지
  않는다"*와 `OPEN-DEC-08`의 격리 규칙까지다.
- **`pgvector` 등 확장의 필요를 판정하지 않았다**(D-6).
