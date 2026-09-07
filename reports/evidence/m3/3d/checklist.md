# M3/3D — checklist.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · head `6a1fe681ec82956fc8da514ddbce42d98957da1b`.
정본 순서: `scope.md` → `_workspace/m3-3d/01_design-review.md`(Phase 2.5) → 이 문서.

## 완료 조건 대응표 — scope.md 「이 slice 가 하는 일」

| # | 요구 | 구현 | test |
| --- | --- | --- | --- |
| ① | Flyway가 스키마 단일 출처, 4영역 | `V1__schema.sql`(raw_observation·notice/opening_result/qualification_text·notice_audit/rejected_write·collection_run) | `CleanMigrationTest`(정보 스키마 대조) |
| ② | raw/canonical 분리, 원본 보존 | `JdbcRawObservationStore.append`가 canonical fold보다 먼저(별도 트랜잭션) raw 행을 만든다 | `RawAppendOnlyTest` 6건 |
| ③ | 멱등 upsert/versioning | `ObservationKey`(procurement, RawNoticeObservation 공개 표면만으로 결정적 유도) + `observation_key` PK(`ON CONFLICT DO NOTHING`) + notice `revision` 트리거 증가 | `NoticeVersioningTest` |
| ④ | 점유 가드 = DB constraint + write 규칙 | `guard_authoritative_slot()` 트리거(V2, `provenance_authority` 조회) + Kotlin `mergeNoticeRow`(`bidvector.procurement.mayOverwrite` 재사용) | `PrecedenceMutationTest` 6건 |
| ⑤ | 항목 단위 원자성·배치 회계 분리 | `JdbcNoticeRepository.persist`가 connection 하나 = 트랜잭션 하나(raw는 이미 별도 커밋됨). `JdbcCollectionRunStore.record`는 완전히 별도 호출/트랜잭션 | `ItemAtomicityTest` 3건 |
| ⑥ | 감사 보존 | 원본=raw_observation, 판정=canonical의 `*_provenance` 컬럼, 오류=`collection_run.drop_reasons`+`rejected_write`(거부된 시도값+DB 원문 메시지) | `NoticeVersioningTest`(audit 행), `PrecedenceMutationTest`(트리거 거부 재현) |
| ⑦ | PostgreSQL 전용, 조용한 no-op 금지 | JSONB·PL/pgSQL 트리거·`to_jsonb` — 다른 엔진에서 파싱 자체가 실패한다(dialect 분기 없음) | S-0~S-5 전건 실제 PostgreSQL 16.4 컨테이너 위에서 실행 |
| ⑧ | clean 재현 | `CleanMigrationTest` — information_schema·pg_catalog 질의를 코드 안 기대 목록과 대조(D-3D-6) | 위와 동일 |

## 판단이 갈린 지점(evidence 필수 절)

1. **notice만 revision+audit+정밀 provenance 가드를 받는다.** opening_result·qualification_text는
   provenance 축이 아예 없는 「최신 관측 우선」(`observed_at`) upsert만 — 설계 검토가
   floor_rate에 대해 이미 정한 「존재 가드만, 정밀 가드 없음」 원칙을 두 테이블 전체로
   확장한 결정이다. 근거: `OpeningResult.derivedBaseAmount`는 항상 `Provenance
   .DerivedFromOpening`(비권위 고정)이라 notice의 권위 계층 모델이 애초에 적용되지 않고,
   `QualificationText`는 금액/provenance 개념 자체가 없다. `ON CONFLICT ...
   WHERE observed_at >= ... RETURNING (xmax = 0)` 한 SQL 문으로 insert/update/no-op을
   전부 판별해 Kotlin 쪽 SELECT+merge 왕복을 없앴다(코드 간결성 + 왕복 1회 감소).
2. **`ObservationKey`·`PersistOutcome`·`RejectionReason`을 procurement에 신설할 때
   `RawObservationStore.kt` 한 파일에 담았다.** scope.md의 in_scope 글롭이
   `*Repository.kt`인데 `RawObservationStore`·`CollectionRunStore`는 이름이 그 글롭과
   안 맞는다 — design review의 「구현 지침」이 다섯 port 이름을 전부 명시했으므로 그
   문면을 정본으로 삼고, 값 객체 둘(`ObservationKey`·`PersistOutcome`)은 그것들을
   생산/소비하는 `RawObservationStore.kt`에 동거시켜 새 파일 수를 최소화했다.
3. **raw_observation.payload는 계약 등재 필드만 담는다(전체 원문 바이트 캡처가 아니다).**
   `RawNoticeObservation`이 계약 없는 값 열람을 막아(3A 위협 모델 우회 (1)(10)) 어댑터가
   가진 유일한 값 접근 경로는 `presenceOf(계약)`뿐이다 — `ObservationPayloadCodec`은
   `KONEPS_COLLECTION_POLICY`의 field contract registry를 순회해 등재분만 직렬화한다.
   완전한 원문 캡처는 `RawObservation.kt`(procurement, 편집 금지 대상)에 전체 열람 API를
   더해야 하는데 그것은 이 slice 범위 밖이다. `unknownFields` 축(3A/3B 회계)이 미등재
   필드 존재 자체는 계속 관측한다.
4. **`releaseSha`(raw_observation 컬럼)는 구성 근이 주입하는 문자열이고, 이 slice는 그
   값의 실제 출처(git SHA 등)를 정하지 않는다.** 빈 문자열만 거부한다(`JdbcRawObservationStore
   .init`). 운영 배선(실제 SHA를 어디서 읽어 넘기는지)은 M6 6C 소관 — evidence에서 test는
   고정 문자열 `"test-release"`를 쓴다.
5. **`Notice.reconstructNotice`(`find()` 경로)는 상태 재구성을 위해 `Notice.collected()`
   +`applyEvent()` 이벤트 체인을 쓴다** — `Notice`의 유일한 공개 생성 경로가 이 둘뿐이고
   (`@ConsistentCopyVisibility`+`internal constructor`, 3A 결정) `internal` 생성자를
   procurement 밖에서 우회할 수 없기 때문이다. `NoticeStatus`마다 `Open`에서부터의 고정
   이벤트 경로(`EVENT_PATH_TO_STATUS`)를 두어 재구성한다 — procurement의 전이표
   (`Canonicalize.kt`)와 같은 데이터를 다른 방향(상태→경로)으로 든 것이라 두 표가
   어긋나면 재구성이 예외를 던진다(조용한 오답이 아니다).

## 위협 모델 대응표 — scope.md 「방어한다」

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 파생/비권위 write가 권위 자리를 덮음 | `guard_authoritative_slot()` 트리거(직접 SQL도 지난다) | `PrecedenceMutationTest`(애플리케이션 역할·superuser 둘 다) |
| (b) raw의 갱신·삭제 | `reject_mutation()` 트리거 + GRANT 미부여(권한) | `RawAppendOnlyTest` 6건 |
| (c) 재수집 중복 효과 | `observation_key` UNIQUE + `ON CONFLICT DO NOTHING` | `NoticeVersioningTest`(재시도 raw 행 1개, canonical Unchanged) |
| (d) 배치 통째 소실·반쯤 commit | 항목 하나 = 트랜잭션 하나, 실패해도 이전 항목 보존 | `ItemAtomicityTest` |
| (e) 엔진 대체의 조용한 no-op | JSONB·PL/pgSQL — 타 엔진에서 migration 자체가 실패(dialect 분기 없음) | 코드 검사(PostgreSQL 전용 문법이 migration 전체에 있음), 실행은 PostgreSQL 16.4 컨테이너로만 |
| (f) ORM 스키마 표류 | migration만 스키마를 만든다(Kotlin 쪽 ORM 미사용, JDBC 직접) | `CleanMigrationTest` |
| (g) 차수의 정수 컬럼 | `notice_round TEXT CHECK (~ '^[0-9]{3}$')` — 정수 변환 없음 | V1 migration CHECK, D-3D-3 |

## 우회 후보 실측(scope 7 + 추가 3)

| # | 우회 시도 | 방어 | 실측 |
| --- | --- | --- | --- |
| 1 | 백필 스크립트가 직접 `UPDATE notice SET base_amount_won=…` | 금액+provenance CHECK(둘 다 null이거나 둘 다 값) | `PrecedenceMutationTest`(provenance 없이 금액만 갱신 → CHECK 위반) |
| 2 | provenance를 `PUBLISHED`로 위조 | (경계 밖, 권한 문제 — Kotlin `Direct.of`는 `Provenance.Published` 타입만 받아 도메인 값 조립 단계에서 이미 막힘) | 코드 검사(`ResolvedBaseAmount.Direct` 생성자 서명) |
| 3 | raw를 건너뛰고 canonical만 write | `notice.observation_key REFERENCES raw_observation` FK NOT NULL | `ItemAtomicityTest`(존재하지 않는 observation_key로 persist 시도 → 예외) |
| 4 | `revision`을 되감아 옛 값을 덮음 | `notice_revision_bump()` 트리거가 입력을 무시하고 `OLD.revision+1`로 고정 | `NoticeVersioningTest`(revision 2로 정확히 증가) |
| 5 | 배치를 한 트랜잭션으로 묶어 성능 개선 | API에 그 경로가 없다(`persist` 자체가 트랜잭션 경계, `Connection`을 밖에서 주입 못 함) | `ItemAtomicityTest`(항목별 독립 커밋 관측) |
| 6 | H2로 test를 돌려 초록 | migration이 JSONB·PL/pgSQL 트리거를 씀 — H2 채택 안 함, Testcontainers PostgreSQL 실물만 사용 | 코드 검사(dialect 분기 없음) |
| 7 | `ddl-auto=update`로 스키마 보정 | ORM 미채택(D-3D-1 (a)), Kotlin은 JDBC 직접만 | 코드 검사 |
| 8 | 애플리케이션 역할로 `provenance_authority` UPDATE | `GRANT SELECT`만(V2), UPDATE/INSERT/DELETE 미부여(V3 REVOKE로 재확정) | `CleanMigrationTest`(권한 목록 SELECT 하나뿐 실측) |
| 9 | TRUNCATE/COPY로 트리거 우회 | TRUNCATE 권한 미부여(REVOKE), COPY는 INSERT 경로라 canonical BEFORE INSERT 가드도 동반 | `RawAppendOnlyTest`(TRUNCATE 시도 거부) |
| 10 | audit를 지워 이력 소거 | `notice_audit` append-only 트리거 + 권한 미부여 | `RawAppendOnlyTest`(DELETE·UPDATE 둘 다 거부) |

## 알려진 제한

- **Docker 부재 시 실제 실패 재현을 이번 라운드에서 완결하지 못했다** — `commands.md`
  「Docker 부재 시 붉음」절 참고. 코드 구조(assumeTrue 미사용, `@Testcontainers
  (disabledWithoutDocker = false)`, `start()` 직접 호출)는 붉어짐을 보장하나, 이 공유
  개발 머신에서 살아 있는 Docker Desktop을 정지시키지 않고는 라이브 재현이 안 됐다.
- **raw_observation.payload는 계약 등재 필드만 담는다**(판단 3, 위).
- **`ObservationKey`는 32비트 `hashCode()`에 기반해 이론적 충돌 가능성이 있다**
  (`RawObservationStore.kt` KDoc) — 완전한 배제는 `RawObservation.kt`에 전체 원문 열람
  API를 더해야 하고 그것은 이 slice 범위 밖이다.
- **`OPEN-DIC-09`(fold 순서 술어)는 여전히 열려 있다** — 3D는 잠정 ⓐ(`observed_at` 단독)로
  구현했다. 같은 `observedAt`에 다른 값이 재관측되는 "conflict" 케이스를 별도 감사
  카테고리로 구분하지 않는다(마지막 유효 write가 그대로 적용) — 결정이 오면 fold 함수
  (opening_result·qualification_text의 SQL `WHERE` 절, notice의 Kotlin merge)만 바뀐다.
- **`OPEN-DIC-06`은 이 slice 계약대로 「거부하지 않고 권위 없음으로 저장」을 구현했다** —
  `NoticeVersioningTest`가 `Provenance.Undeclared`를 실은 `AllocatedBudget`이 빈 자리를
  채우는 것을 실측했다(base_amount 축은 `ResolvedBaseAmount`의 sealed 폐쇄 때문에
  Undeclared를 애초에 나를 수 없어 이 test 대상이 아니다).
- **`bidvector_app` 역할은 NOLOGIN**이라 실제 운영 배선(연결 문자열에서 `SET ROLE`을
  언제 거는지)은 M6 6C가 정한다 — 3D는 test에서만 그 전환을 실증한다.

## 재작업 누계: 0회, 상한 5(v2-slice-pipeline).
