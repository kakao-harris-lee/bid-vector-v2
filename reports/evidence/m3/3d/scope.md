# Slice 계약 — M3 / 3D · persistence adapter — **착수 2026-09-07**

> **지위**: M3 착수 직후(2026-09-07) 세션 모델이 단독으로 쓴 **계약 초안**(문서 레인, 브리프 2). 구현·gradle·의존성·migration 편집 없음.
> 착수는 **3A 승인 → 3B 뒤**(`prep/m3-prep.md` §5 순서: 3A → 3B → 3D → 3C)이며, 그때 `base_sha` 재고정(40자), 아래 D-3D-1~3 답 수령,
> `milestone-3.md` 「Slice 3D」 착수 문단. **Phase 2.5 설계 검토 대상** — 수용 기준이 「파생값이 공식값을 조용히 덮는 mutation 이 실패하는가」
> (스키마 precedence)·「KONEPS 장애가 transaction 을 반쯤 commit 하지 않는가」라 구성상 닫힘을 구현 전에 검토한다.

```yaml
milestone: m3
slice: 3d-persistence-adapter
base_sha: 01ecbba69e21e4b85ee8303416fe06a77b919fd6   # 착수 2026-09-07 재고정 = 3B(공고 축) 최종 head. 초안 시점 앵커 1f3c4ff
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/resources/db/migration/**                 # Flyway `V1__*.sql` 부터(ADR 0004 D-2·D-4) — raw / canonical / audit / collection_run 스키마, 점유 가드 DB 측 실물
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**  # 3A fact 의 저장 port 구현(`NoticeRepository`·`OpeningResultRepository`·`RawObservationStore`·`CollectionRunStore`)·write 규칙·fold·항목 단위 트랜잭션
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**  # Testcontainers PostgreSQL 통합 test — 멱등 upsert·재수집 versioning·점유 가드 mutation 실패·항목 원자성·clean DB migration 재현
  - adapters/build.gradle.kts                                   # Flyway core · PostgreSQL JDBC driver · (D-3D-1) DB 접근 라이브러리 · Testcontainers(test) — 2A·3B 가 넣은 줄과 병합
  - procurement/src/main/kotlin/bidvector/procurement/*Repository.kt, procurement/src/test/kotlin/**   # **조건 충족(착수 시 확인 — 3A 는 repository port 를 두지 않았다)**: 도메인 소유 저장 port 파일 신설(ADR 0005 D-10.1) + 그 test. 그 밖의 procurement 편집 금지(3B 가 확장한 `Accounting.kt` 포함)
  - gradle/libs.versions.toml                                   # Flyway·PostgreSQL JDBC·Testcontainers 카탈로그 좌표(3A F-15·3B 판단 5 전례로 착수 시 등재)
  - config/quality/gate-tests.properties                        # 조건부 — `gate.tests.adapters` 에 3D test 추가(병합)
  - milestone-3.md                                              # 「Slice 3D」 착수 문단
  - reports/evidence/m3/3d/**
out_of_scope:
  - outbox·스케줄·lease 테이블과 어휘                             # M4 4C 소유(`OPEN-OPS-10`) — 3D 는 raw/canonical/audit/collection_run 만. 4C 가 같은 트랜잭션 경계를 쓴다
  - legacy 데이터 이관·Alembic 이력                               # ADR 0004 D-4 — V1 부터. 이관은 별도 판단(`OPEN-DEC-08` 경계)
  - pgvector 등 확장                                              # ADR 0004 D-6 — 요구 capability 분류에 종속
  - 판정 결과(`Qualification`·`BidDecision`)·정산(`TenderOutcome`) 저장   # M4·M6
  - ORM 의 스키마 생성(`ddl-auto`)                                 # ADR 0004 D-2 금지
  - H2 등 대체 엔진·dialect 분기                                   # ADR 0004 D-1 — Docker 부재는 붉게
  - koneps OpenAPI(3B)·extraction(3C)·ML client(4D) 패키지
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'"                            # S-2 — Testcontainers PostgreSQL 위 통합 test 전부(Docker 부재 = 실패, skip 아님)
  - "./gradlew :adapters:test --tests '*PrecedenceMutationTest*'"                                    # S-3 — 파생 provenance write 가 권위 자리를 덮는 시도가 **DB 층에서** 거부됨(Kotlin 규칙을 우회한 직접 SQL 도 실패)
  - "./gradlew :adapters:test --tests '*ItemAtomicityTest*'"                                         # S-4 — 배치 중 한 항목 실패 시 그 항목만 롤백, 나머지 커밋, 회계 행 1건
  - "./gradlew :adapters:test --tests '*CleanMigrationTest*'"                                        # S-5 — 빈 DB 에서 `V1__` 부터 migrate 한 스키마 = test 가 기대하는 스키마(6B 「clean 재현」 선행)
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-6
  - "./gradlew qualityBaseline"                                                                        # S-7
rollback: |
    **정본은 `reports/evidence/m3/3d/rollback.md`**(착수 시). `adapters/.../persistence/**`·`db/migration/**` 과 build 의존 줄을 걷으면 3B 상태.
    운영 DB 는 이 slice 가 만들지 않는다(test 컨테이너뿐) — 되돌릴 데이터가 없다.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-3.md` 3D·완료 조건(「retry 후에도 동일 공고의 canonical effect 하나」·「derived fact 가 authoritative fact 를 덮는
mutation 이 실패」·「KONEPS 장애가 transaction 을 반쯤 commit 하지 않음」) · `ADR 0004` D-1~D-6 · `ADR 0005`(트랜잭션 경계는 4C 가 공유) · `data-dictionary.md` §2.1(금액 축을 두
aggregate 에 나누지 않음)·§2.2.3(`observationKey`·`deliveryKey`)·§5.1 규율 1(점유 가드)·§5.2 · 3A ⑥(`isAuthoritative` 데이터)·⑦(회계) · D-M3-7 (a) · 조사 (c).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 01ecbba..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-07) **없음**.

**착수 2026-09-07 — 운영자 결정**: D-3D-1 (a) · D-3D-2 (a) · D-M3-7 (a)(M3 착수 시). **세션 모델 계약 고정 D-3D-6**(OPEN 후보 `OPEN-3D-SCHEMA-SNAPSHOT` 의
결정): S-5 의 기대 스키마는 **information_schema·pg_catalog 질의 단언**(테이블·컬럼·타입·NOT NULL·UNIQUE·FK·트리거·CHECK 목록을 test 가 열거)으로 고정한다 —
pg_dump 텍스트 골든은 바이너리 버전에 묶이고 리뷰 sandbox 에서 재현이 어렵다. 6B 가 같은 단언을 재사용한다. **착수 시 계약 정정**: 3B 가 `CollectionAccounting` 을
확장했으므로(truncation 사유·quota·backoff) `collection_run` 스키마는 그 필드까지 담는다(①·⑥). 3A 의 `Notice`·`OpeningResult`·`QualificationText` fact 셋이
저장 단위이며 §2.1 aggregate 조립은 3D 가 **테이블 셋을 `notice_number`+`notice_round` 키로 묶는 것**까지(`OPEN-3A-AGGREGATE` 참조).

**병렬 레인 경계**: 3D 구현 레인은 in_scope 경로만. `adapters/.../koneps/**`(3B 종결)·`fixtures/**`·`docs/**`·`build-logic/**` 은 편집 금지.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **Flyway `V1__` 부터, migration 이 스키마의 단일 출처** — 네 영역: `raw_observation`(append-only: `observation_key` UNIQUE, source, `payload` JSONB 원문 그대로, `observed_at`, `release_sha`) · `notice`/`opening_result`/`qualification_text`(canonical, PK = `notice_number`+`notice_round` **문자열**) · `notice_audit`(canonical 갱신마다 이전 값·provenance·사유·`observation_key`) · `collection_run`(3A 회계 컬럼 그대로 + 항등식 CHECK). ORM 은 스키마를 만들지 않는다 | 3D 「Flyway migration」 · ADR 0004 D-2·D-5 · §2.1 「한 공고의 금액 축을 두 aggregate 에 나누지 않는다」(legacy `projects`/`historical_data` 분리를 복제하지 않음) |
| ② | **raw 와 canonical 의 분리** — 어댑터는 **먼저** raw 행을 append 하고(실패해도 남는다), 그 `observation_key` 를 canonical fold 의 입력으로 쓴다. raw 는 갱신·삭제 경로가 없다(권한·트리거) | 3D 「raw observation 과 canonical fact 분리」·「원본 보존」 · 조사 c-1 「raw payload 테이블이 없다 — 커밋과 함께 사라진다」 |
| ③ | **멱등 upsert / versioning** — 같은 공고(`NoticeId`) 재수집은 canonical 행 하나에 **`revision` 증가 + audit 행**으로 접힌다. fold 는 필드별이고 순서 술어는 `observed_at`(§2.2.3, `OPEN-DIC-09` 의 잠정 ⓐ — 같은 키 재관측은 같은 값이어야 하며 다르면 audit 에 conflict 로 남김). 「retry 후 canonical effect 하나」는 `observation_key` UNIQUE + ON CONFLICT 로 | 3D 「같은 공고 재수집의 멱등 upsert/versioning」 · 완료 조건 · 조사 c-2 「versioning 이 없다 — 제자리 갱신」 |
| ④ | **점유 가드 = DB constraint + write 규칙 양쪽, 전 금액 축** — canonical 금액 컬럼마다 `*_provenance` 컬럼이 짝이고, `provenance_authority(provenance) → boolean` **테이블**(3A `isAuthoritative` 데이터의 DB 사본, migration 으로 seed)을 `BEFORE UPDATE` 트리거가 읽어 「권위 아님 → 권위 있음 자리」 write 를 **거부**한다. Kotlin write 규칙은 같은 데이터를 읽어 미리 거른다(둘이 같은 표를 읽는지 test). 존재 가드(비었으면 지우지 않음)도 같은 트리거 | 3D 「파생값이 공식값을 조용히 덮지 못하는 precedence rule」 · §5.1 규율 1 · ADR 0004 D-5 「파생 write 가 원본 자리를 덮는 경로가 스키마에 존재하지 않는다」 · 조사 c-3 「점유 가드는 `budget_estimate` 한 축에만」 |
| ⑤ | **항목 단위 원자성 · 배치 회계 분리** — 항목 하나 = 트랜잭션 하나(raw append → canonical fold → audit), 실패한 항목은 그 트랜잭션만 롤백하고 회계의 `dropped`+`dropReasons` 에 실린다. 배치 회계 행은 마지막에 별도 트랜잭션. **KONEPS 장애(3B 가 `truncated`/실패 배치를 넘김)는 이미 커밋된 항목을 되돌리지 않고, 진행 중 항목만 롤백** | 완료 조건 「KONEPS 장애가 transaction 을 반쯤 commit 하지 않음」 · 조사 c-4 「배치 전체 한 번 commit, per-item savepoint 없음 — 반대 위험(전부 잃음)」 |
| ⑥ | **감사 보존** — 원본(②)·판정(canonical 의 provenance 컬럼)·오류(`collection_run.drop_reasons` JSONB + 항목별 `rejected_write` 행: 거부된 mutation 의 시도값·사유) 셋이 전부 테이블 | 3D 「원본, 판정, 오류를 감사 가능하게 보존」 · 조사 c-5 표 |
| ⑦ | **PostgreSQL 전용, 조용한 no-op 금지** — advisory lock·JSONB·트리거는 PostgreSQL 의미론. 엔진이 다르면 migration 이 실패한다(dialect 분기 없음). Testcontainers 로 실측, Docker 부재는 **붉게** | ADR 0004 D-1 · 조사 c-2 「`dialect != postgresql` → return」 실물 · D-M3-7 (a) |
| ⑧ | **clean 재현** — 빈 컨테이너에서 `V1__` 부터 migrate 한 결과가 test 기대 스키마와 같음(S-5). 6B 의 「clean PostgreSQL 에서 Flyway 전체 재현」 선행 | ADR 0004 D-2 · `milestone-6.md` 6B |

**만들지 않는 것**: outbox·스케줄·lease(4C·M4) · legacy 스키마 호환·이관 · `ddl-auto` · dialect 분기 · pgvector · 판정·정산 테이블 · `0.0`=미상 관례(§1.3 — NULL 이 부재) ·
전체 카테고리 로드 + fuzzy 매칭(조사 c-2 ④ — 식별자 정규화는 3A 값 객체가 소유, 매칭은 `NoticeId` 정확 일치만).

---

## 운영자 결정 필요 — 착수 전(D-3D-1·2) · 계약 고정(D-3D-3~5)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3D-1** ✅ (a) 승인 2026-09-07 | **DB 접근 라이브러리** | (a) **JDBC 직접 + 작은 mapper 함수**(신규 의존 0 — 드라이버·Flyway만) (b) jOOQ(코드 생성 — migration 뒤 생성물 관리) (c) Exposed/Hibernate(ORM — ADR 0004 D-2 「ORM 이 스키마를 만들지 않는다」 아래에서만) | **(a)** 1차 — 테이블 넷·쿼리 형태가 단순(키 정확 일치·append·fold)하고 §7(측정된 필요) 을 넘을 근거가 없다. 쿼리가 늘어 유지비가 커지면 (b) 로 승격(결정 갱신) | 착수 전 |
| **D-3D-2** ✅ (a) 승인 2026-09-07 | **점유 가드의 DB 측 실물** | (a) **`BEFORE UPDATE` 트리거 + `provenance_authority` 표**(전 금액 축, 일반화된 한 함수) (b) 컬럼별 CHECK(행 간 비교 불가라 「이전 값」을 못 본다 — 부적합) (c) Kotlin 규칙만(DB 측 없음 — 직접 SQL 우회 열림) | **(a)** — 완료 조건이 「mutation 이 실패」이고 리뷰 관점이 「DB constraint/test 에 반영」이다. (c) 는 M6 운영 접근·백필 스크립트가 우회한다(`R-PROV-04` 실물) | 착수 전 |
| **D-3D-3** | canonical 키는 `(notice_number TEXT, notice_round TEXT)` — 정수 컬럼 금지(§12.2 「정수 변환 금지」, D-3A-0). 3A `NoticeRound` 의 정규화 규칙을 DB 가 재선언하지 않는다(CHECK 는 형식 `^[0-9]{3}$` 만) | — | 계약 고정 |
| **D-3D-4** | 트랜잭션 경계는 **항목 단위**(⑤). 배치 단위·페이지 단위 commit 은 두지 않는다. 4C outbox 가 같은 항목 트랜잭션에 이벤트를 넣는 자리를 남긴다(ADR 0005 D-2 — 3D 는 자리만, 어휘는 4C) | — | 계약 고정 |
| **D-3D-5** | 부재는 NULL — `0`·빈 문자열을 「미상」으로 쓰지 않는다(§1.3). 금액 컬럼은 `NUMERIC(…,0)` 정수 원, 율은 `NUMERIC` fraction + `scale` 재선언 없음(값의 단위는 3A 계약이 정하고 DB 는 저장만) | — | 계약 고정 |

---

## 위협 모델 — 3D 고유 경계

**방어한다**: (a) 파생·권위 없는 write 가 권위 자리를 덮음(④ DB 트리거 + Kotlin 규칙, S-3 는 **직접 SQL** 로도 실패함을 실측) (b) raw 의 갱신·삭제(② 권한·트리거) (c) 재수집 중복 효과(③ `observation_key` UNIQUE) (d) 배치 통째 소실·반쯤 commit(⑤ 항목 트랜잭션, S-4) (e) 엔진 대체의 조용한 no-op(⑦) (f) ORM 스키마 표류(① migration 단일 출처, S-5) (g) 차수의 정수 컬럼(D-3D-3).
**방어하지 않는다**: 어댑터가 옮긴 원문의 정직성(3B) · `isAuthoritative` **데이터 내용**(3A 정책·승인) · DB superuser 가 트리거를 끄는 것(운영 권한 경계 — M6 6C) · outbox 전달 의미(4C) · 백필·이관 스크립트의 옳음(별도 승인 — 단 ④ 가 그 스크립트의 권위 없는 write 도 거부한다) · 운영 성능(인덱스·파티션은 관측 뒤).

**우회 후보(≥5)**: (1) 백필 스크립트가 직접 `UPDATE notice SET base_amount=…` → 트리거가 provenance 컬럼 없는 갱신을 거부(금액과 provenance 는 **함께**만 갱신 가능) (2) provenance 를 `Published` 로 위조해 파생값 write → Kotlin 쪽 `ResolvedBaseAmount.Direct` 가 `Published` 만 받음(3A ⑥) + audit 에 `observation_key` 가 남아 원문 대조 가능(사후) — **완전 방어 아님, 경계 밖(권한)** (3) raw 를 건너뛰고 canonical 만 write → canonical write 함수가 `observation_key`(raw FK) 를 요구, FK NOT NULL (4) `revision` 을 되감아 옛 값을 덮음 → `revision` 은 트리거가 증가시키고 입력으로 받지 않음 (5) 배치를 한 트랜잭션으로 묶어 성능 개선 → S-4 가 「한 항목 실패 시 나머지 커밋」을 요구 (6) H2 로 test 를 돌려 초록 → migration 이 PostgreSQL 전용 문법(JSONB·트리거)이라 H2 에서 실패 (7) `ddl-auto=update` 로 스키마 보정 → ORM 미채택(D-3D-1) + S-5 가 migration 만으로 재현.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m3-prep/01_scout_collection.md` (c)(h))

- **raw payload 테이블 없음, `eligibility_raw` 부분집합만** → ② 는 legacy 선례 없이 ADR 0004 D-5 가 세운다.
- **advisory lock 이 PostgreSQL 아니면 조용히 no-op** → ⑦. **versioning 없음, 제자리 갱신** → ③.
- **점유 가드 강도가 축마다 다르다**(`budget_estimate` 점유 · `base_amount` 양수 · `award_floor_rate`/`eligibility_raw` 존재 · `reserve_prices` 유지) → ④ 는 전 금액 축에 같은 가드, 강도는 3A `isAuthoritative` 데이터 하나로.
- **배치 전체 한 번 commit** (`persist_crawl_results`), 백필은 `commit_every=25` — 경로마다 규율이 다름 → ⑤ 항목 단위 하나로.
- **오류 보존이 `error_message` 한 줄**, 추정가격 출처 컬럼 없음 → ⑥.
- **「66% 오염」 원문은 주석뿐**(측정 방법·모수·측정일 없음) → 승격 금지 유지(`OPEN-NUM-01`·`OPEN-REG-04`). 3D 는 §7 재측정이 가능한 형태(provenance 컬럼)를 남길 뿐.
- `OPEN-ADR-12`/`-13`(lease·db-scheduler 기본값)은 3D 를 막지 않음(M4). `OPEN-OPS-10`(DB 큐 어휘)은 4C.

---

## OPEN — 수령·신설

| OPEN | 3D 처리 |
| --- | --- |
| `OPEN-OPS-10` | outbox 어휘는 4C — 3D 는 항목 트랜잭션에 자리만 남긴다 |
| `OPEN-DIC-09` | fold 순서 술어 — 3D 는 잠정 ⓐ(`observed_at` 단독, 동률은 conflict 로 audit)로 구현하고 결정이 오면 fold 함수 하나만 바뀐다. 활성 유지 |
| `OPEN-DIC-06` | canonical write 가 `Undeclared` 를 거부하는가 — 3D 는 **거부하지 않고 권위 없음으로 저장**(§5.1 둘째 규율 — 빈 자리만 채움). 결정이 오면 트리거 표 한 행 |
| `OPEN-DEC-08`·ADR 0004 D-4 | legacy 행 이관 없음 — 이관 시 provenance 미상 격리는 그 결정의 몫 |
| ~~신설 후보 `OPEN-3D-SCHEMA-SNAPSHOT`~~ | 착수 시 세션 모델 계약 고정 **D-3D-6**(정보 스키마 질의 단언)으로 닫힘 — OPEN 등재 없음 |
