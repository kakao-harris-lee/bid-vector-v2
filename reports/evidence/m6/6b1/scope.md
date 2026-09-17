# M6/6B-1 — clean DB 재현 · 제약·인덱스 실측 · 낙관적 동시성(세션 영속) (2026-09-17)

```yaml
milestone: M6
slice: 6b1-schema-and-optimistic-concurrency
base_sha: c4d09cc   # PR #31(6C) 머지 커밋 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - adapters/src/main/resources/db/migration/V8__edit_session.sql     # 세션 영속 표 신설 — id PK · state · expires_at · session_version · actor · last_command · 감사 열. 낙관적 동시성은 session_version 전제조건으로(트리거가 값을 정하지 않는다 — V2 의 revision 과 다른 축, D-6B1-3)
  - workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt        # 계약 갱신 (2)·D-6B1-7: `load` 반환을 **원시 스냅숏**으로(`save` 시그니처 무편집). M4/4C-1 의 `OutboxPort.claim() -> ClaimedOutboxRow` 패턴 재사용
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionSnapshot.kt   # 계약 갱신 (2): 원시 필드만 담는 공개 스냅숏 타입(도메인 불변식 없음) + 인코딩. 어댑터는 이것만 만든다
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionRestore.kt     # 계약 갱신 (5): **`internal` 복원 함수**(미지·불량 값 거부). detekt 파일당 함수 상한 때문에 스냅숏 파일에서 기계적으로 분리 — 설계 변경 아님(`CleanMigration*Test` 가 크기 게이트로 넷으로 갈린 것과 같은 종류)
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt  # 계약 갱신 (2): `sessions.load` 호출부가 스냅숏 → 복원을 지난다(로직 무변경)
  - workflow/src/test/kotlin/bidvector/workflow/strategy/**              # 계약 갱신 (2): `EditSessionRepository` fake 넷의 `load` 반환 타입만(단언·시나리오 무편집)
  - adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcEditSessionRepository.kt      # 계약 갱신 (4)·D-6B1-9: **패키지는 `adapters.strategy`**(신설) — `persistence` 는 workflow 참조가 게이트로 금지돼 있다
  - adapters/src/main/kotlin/bidvector/adapters/strategy/EditSessionRow.kt                 # 행 → 스냅숏 매핑(D-6B1-7). 도메인 복원은 workflow `internal`
  - adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt  # 계약 갱신 (4): 신설 패키지 전용 의존 게이트(허용 = `workflow.strategy`·`strategy`·`sharedkernel`·`adapters.persistence` 의 트랜잭션 경계). `adapters.event` 의 전례와 같은 형태
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                         # 세션 SQL 문자열 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcEditSessionRepositoryTest.kt   # 왕복·낙관적 충돌(0행 → 실패)·상태 전이 보존·만료 시각 왕복
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt          # 계약 갱신 (3)·D-6B1-8: **기존 여덟 축 계열에 `edit_session` 을 「추가만」으로 등재** — 축1 테이블·축5 UNIQUE·축5b PK·축6 FK·축9 GRANT 유효권한
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt    # 축2·3·4 — 컬럼 존재·타입·NOT NULL
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt   # 축7 — 트리거(신설 표는 트리거 0 임을 **등재**해야 한다, 지금은 안 붉지만 누락 상태)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt     # 축8 — CHECK 개수 등재
  - app/src/test/kotlin/bidvector/app/conformance/**        # 계약 갱신 (6): `EditSessionRepository.load` 반환 타입 변경(D-6B1-7)의 기계적 귀결 — conformance fake 의 시그니처만
  - adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt   # 계약 갱신 (6): 신설 표를 test 간 정리 목록에 추가(기계적)
  - config/quality/gate-tests.properties                    # 계약 갱신 (6): 신설 test 를 양방향 등재 게이트에 등재(그 게이트가 요구한다 — 우회가 아니라 이행)
  - reports/evidence/m6/6b1/**
  - milestone-6.md                                                     # 6B 분할·6B-1 착수 문단(팀장 커밋)
out_of_scope:
  - backup/restore·migration rollback 정책과 rehearsal   # **6B-2** — 완료 조건 7 의 자리. 운영 절차·덤프·복원 리허설은 스키마 축과 독립이다(D-6B1-1)
  - raw/canonical/audit/outbox 데이터 수명·마스킹          # **6B-3** — 보존·파기는 privacy-gate 대상 축이고 승인된 보존 기간이 아직 없다(D-6B1-1)
  - ML job 영속·큐 상한(`OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND`) # **6B-4** — Python·파일 계열이고 저장소가 Postgres 가 아니다. 큐 정책(거부·대기·백프레셔) 결정이 선행한다(D-6B1-2)
  - `EditSessionRepository.save` 시그니처 변경             # 충돌을 결과 타입으로 나르려면 workflow 호출부·fake 전부가 바뀐다 — D-6B1-4 로 이 slice 는 **실패를 큰 소리로** 내고, 결과 타입화는 두 번째 writer 가 생길 때(`OPEN-6B1-SAVE-OUTCOME`). **`load` 반환은 계약 갱신 (2)·D-6B1-7 로 좁게 재개방**했다(원시 스냅숏) — 그 문구의 취지는 저장 쪽 충돌 통로였고 읽기 축은 별 문제였다
  - 인덱스 신설·제약 강화 자체                            # 이 slice 는 **실측과 공백 등재**까지(카탈로그 대조). 실제 추가는 소비 질의가 있는 slice 가 근거와 함께(D-6B1-5)
  - app 배선·HTTP 진입점                                  # 6A
acceptance_commands:
  - "./gradlew --no-daemon check"                                                   # S-10 — 전건(evidence 커밋마다 그 HEAD 에서, 마지막 HEAD 결과는 보고·PR 코멘트가 정본)
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcEditSessionRepositoryTest*' --rerun-tasks"      # S-30 — 세션 왕복·낙관적 충돌(캐시 우회)
  - "./gradlew --no-daemon :adapters:test --tests '*CleanMigration*' --rerun-tasks"                     # S-31 — 빈 컨테이너 전건 적용 뒤 여덟 축 스키마 대조(계약 갱신 (5) 정정 — D-6B1-8 이 삭제한 파일을 가리키고 있었다, 팀장 오류)
  - "./tools/one-command-check.sh"                                                  # S-20 승계(6C) — 새 checkout 한 명령
rollback: |
  신규 파일 삭제 + 편집 둘(`Sql.kt`·`milestone-6.md`)을 base 로.
  `git restore --source=c4d09cc --staged --worktree -- <in_scope 경로 개별 인자>`, 신규는 삭제.
  **마이그레이션은 되돌림이 비대칭이다** — 이미 적용된 DB 에는 V8 이 남는다. 되돌린 코드는 그 표를 쓰지 않으므로
  기능은 base 와 같고, 실제 DB 에서 지우려면 `DROP TABLE edit_session`(사용자 승인 대상, 운영 DB 금지)이다.
  이 사실과 명령을 rollback.md 에 적고 **임시 clone ①~⑥ + 빈 컨테이너 재적용**으로 실측한다.
```

작성: 2026-09-17, 세션 모델 단독. 근거: `milestone-6.md` 「Slice 6B」(clean PostgreSQL Flyway 재현 · constraint/index/optimistic lock)·완료 조건 1 · M4/4B 알려진 제한 ②(「세션 영속 실 구현 부재 + `sessionVersion` 낙관적 동시성 미검증 — port+fake 까지가 그 slice 경계」) · M6 입력 재고 §3 · 6C 가 만든 `docker compose` postgres 자리.

## 6B 를 넷으로 가른다 (D-6B1-1·2)

milestone-6 의 6B 는 네 축을 한 bullet 목록에 담고 있는데 성질이 다르다 — ① 스키마·동시성(Kotlin·Postgres·Testcontainers) ② 운영 절차(백업·복원·마이그레이션 되돌림 리허설) ③ 데이터 수명·마스킹(보존·파기 정책, privacy-gate 축) ④ ML job 영속·큐(Python·파일·큐 정책 결정 선행). **6C 가 네 축을 한 slice 에 담아 재작업 3 라운드를 썼다** — 같은 실수를 하지 않는다. 이 slice 는 ① 만 한다.

## 이 slice 가 하는 일

① **clean DB 재현 게이트에 신설 표를 잇는다**(계약 갱신 (3)·D-6B1-8 — 초판의 「새 게이트 신설」은 재발명이었다) — 기존 여덟 축 계열(`CleanMigration*Test` 넷, 정본 D-3D-6)이 빈 컨테이너 전건 적용 뒤 테이블·UNIQUE·PK·FK·컬럼 타입·NOT NULL·트리거·CHECK·GRANT 유효권한을 이미 대조한다. V8 이 그 계열을 붉히므로(실측 5 failed) `edit_session` 을 「추가만」으로 등재하고, **지금은 붉지 않지만 등재가 빠진 축 7·8**(트리거 0·CHECK 개수)도 함께 채운다.
② **제약·인덱스 실측과 공백 등재** — 현재 인덱스 셋(감사·수집 회계·outbox 순서)과 제약을 카탈로그에서 뽑아 evidence 에 표로 남기고, **소비 질의가 있는데 인덱스가 없는 자리**를 공백으로 등재한다. 추가 자체는 근거를 가진 slice 가 한다(D-6B1-5).
③ **낙관적 동시성 실물** — `EditSessionRepository` 의 실 구현을 세우고 `session_version` 을 **전제조건**으로 쓴다(`UPDATE … WHERE session_version = :expected`, 0행이면 실패). M4 가 port+fake 까지만 하고 남긴 자리이며, 지금 저장소에는 `revision`(V2 트리거가 정하는 값)만 있고 **잃어버린 갱신을 막는 전제조건이 어디에도 없다**.

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6B1-1** | 6B 를 6B-1(스키마·동시성)·6B-2(백업·복원·마이그레이션 되돌림)·6B-3(데이터 수명·마스킹)으로 가른다 | 축이 다르고(코드 vs 운영 절차 vs 보존 정책) 6B-3 은 **승인된 보존 기간이 없어** 결정 선행이다. 6C 의 3 라운드가 혼합 slice 의 비용을 실측했다 |
| **D-6B1-2** | ML job 영속·큐 상한(M5 이월 둘)은 **6B-4** — 이 slice 밖 | Python·파일 계열이고 Postgres 가 아니다. 큐 정책(거부·대기·백프레셔)이 먼저 결정돼야 하며 `WORKER_RESOURCE_EXHAUSTED` 는 계약에 있으나 생산 경로가 없다 |
| **D-6B1-3** | 세션 표의 `session_version` 은 **애플리케이션이 싣고 DB 가 전제조건으로 검사**한다 — V2 의 `revision`(트리거가 정하는 값)과 다른 축이다 | 낙관적 동시성은 「내가 읽은 값이 아직 그 값인가」를 묻는 것이고, 트리거가 값을 정하면 그 질문이 사라진다. 두 메커니즘을 한 표에 섞지 않는다 |
| **D-6B1-4** | 충돌은 port 시그니처를 바꾸지 않고 **큰 소리로 실패**한다(구체 예외) — 결과 타입화는 `OPEN-6B1-SAVE-OUTCOME` | `save(session): Unit` 에 충돌 통로가 없다. 결과 타입으로 나르려면 M4 종결 산출물(workflow)과 호출부·fake 가 전부 바뀌고 「충돌 시 무엇을 하는가」는 workflow 도메인 결정이다. **조용한 덮어쓰기만은 허용하지 않는다** — 그것이 이 축의 요점이다 |
| **D-6B1-7** (계약 갱신 (2), 구현 레인 정지·보고) | `EditSessionRepository.load` 는 **원시 스냅숏**을 반환하고 `EditSession` 복원은 **workflow 안 `internal` 함수**가 진다 — 어댑터는 `EditSession` 을 만들지 않는다. `save` 시그니처는 불변(D-6B1-4). 스냅숏 타입은 **원시 필드만**(도메인 불변식 0)이고 복원은 **미지·불량 값을 거부**한다(지어내지 않는다) | 구현 레인 실측: `EditSession` 은 `internal constructor` + `@ConsistentCopyVisibility` 이고 저장소에 `friendPaths`·`associate` 설정이 0 이라 `adapters` 에서 생성자 호출이 **컴파일 불가**다. 통로 타입은 쓰기 주체만 가르고 **읽기·복원 문제를 풀지 않는다**. 같은 계열을 M4/4C-1 이 이미 겪었고(공개 복원 진입점이 위조 재료를 내준다 — verifier H-1) 해법이 **port 반환을 원시 행으로**(`OutboxPort.claim() -> ClaimedOutboxRow`, 복원은 workflow `internal`)였다. 대안 둘은 거부: 새 public 복원 팩토리는 D-6B1-6 이 막은 바로 그 자리를 다시 열고, 세션 축을 이 slice 에서 빼면 M4/4B 인계가 무기한 미결로 남는다 |
| **D-6B1-8** (계약 갱신 (3), 구현 레인 정지·보고) | clean DB 재현은 **기존 여덟 축 계열**(`CleanMigration*Test` 넷, 정본 D-3D-6)에 신설 표를 「추가만」으로 등재해 잇는다 — 새 게이트를 만들지 않고 계약 초판이 지정한 `CleanDatabaseReproductionTest`·`schema-baseline.properties` 는 **삭제**한다. 스키마 기대치는 **test 코드에 둔다**(외부 속성 파일로 빼지 않는다) | ① **재발명이었다**: 그 계열이 M3/3D→3E→3F→3G→M4/4C-2 를 거쳐 「추가만」으로 확장돼 왔고 **FK·CHECK 개수·컬럼별 타입·NOT NULL 까지 더 엄격**하다(내 초판 구상은 표·PK·인덱스·트리거 **이름 집합**만 봤다). ② **전제가 틀렸다**: `PersistenceTestSupport` 의 공유 컨테이너는 `init` 에서 Flyway 를 **한 번** 태우므로 그 뒤 다른 test 의 DML 은 스키마 카탈로그에 영향이 없고, 「빈 컨테이너 전건 적용」 요구를 **이미 충족**한다 — 계약 초판의 「간접 재현만」은 **팀장 오류**다. ③ 기대치를 외부 속성 파일로 빼면 6C 가 실측한 표면이 생긴다(`OPEN-6C-POLICY-GATE-STRUCTURAL` — **정책 값이 곧 게이트의 세기**이고 값을 느슨하게 바꾸면 조용히 약해진다). 스키마 기대치는 test 코드가 정본인 편이 강하다 |
| **D-6B1-9** (계약 갱신 (4), 구현 레인 정지·보고) | 세션 어댑터의 패키지는 **`bidvector.adapters.strategy`**(신설)이고 **전용 의존 게이트 test** 를 함께 둔다 — `adapters.persistence` 에 두지 않는다. `Sql.kt` 는 `persistence` 에 그대로 두고 `internal`(모듈 범위)로 참조한다 | 구현 레인 실측: `PersistenceAdapterDependencyTest`(M3/3D)가 `adapters.persistence` 의 domain 참조를 `procurement`·`shared-kernel` 로 좁히고 **`bidvector.workflow` 를 명시 금지**한다(계약이 지정한 경로대로 두니 workflow 좌표 15 참조로 붉어졌다). 같은 필요 때문에 **`adapters.event` 가 이미 별 패키지로 존재**하고 그 게이트 KDoc 이 「3D allow-list 를 넓히지 않는다 — 이 test 는 그 test 와 별개다」로 **선택지 3 을 미리 거부**해 뒀다. 선택지 2(event 패키지 합류)는 허용 목록이 이미 맞지만 「이벤트 전달」과 「세션 영속」이 한 패키지에 섞인다 |
| **D-6B1-5** | 인덱스·제약의 **추가**는 소비 질의를 가진 slice 가 근거와 함께. 이 slice 는 실측·공백 등재까지 | 근거 없는 인덱스는 쓰기 비용만 늘린다. 공백을 보이게 만드는 것이 이 slice 의 값이다 |
| **D-6B1-6** | `EditSession` 의 복원은 **가시성을 넓히지 않는다** — M4 가 `internal constructor` 로 위조를 닫았다. 복원 진입점의 형태는 설계 검토에서 정하고(같은 모듈 내 팩토리 · 전용 통로 타입 중) 새 public 표면 0 을 실측한다 | M4/4C-1 의 high 가 정확히 「public 복원 진입점이 아무 모듈에나 위조를 허락한다」였다. 같은 자리를 다시 열지 않는다 |

## 위협 모델 — 6B-1 고유 경계

**방어한다**: (a) 빈 DB 에서 마이그레이션 전건이 서고 스키마가 기대치와 같다(카탈로그 실측) (b) 세션의 **잃어버린 갱신**이 조용히 성립하지 않는다(전제조건 0행 → 실패) (c) 세션 복원이 도메인 위조 진입점을 새로 만들지 않는다(새 public 표면 0) (d) 마이그레이션이 append-only·provenance 가드(V2·V3)를 무력화하지 않는다(기존 게이트 test 무편집 통과).
**방어하지 않는다**: 백업·복원·마이그레이션 되돌림 절차(6B-2) · 데이터 보존·파기·마스킹(6B-3) · ML job 영속·큐(6B-4) · 인덱스·제약 추가(D-6B1-5) · 다중 writer 하의 재시도·거부 정책(`OPEN-6B1-SAVE-OUTCOME`) · 운영 DB 접근(승인 대상).

**우회 후보**: (1) clean DB test 가 **자기 컨테이너에만** 참이고 마이그레이션 순서·의존을 안 재면 V8 이 V2 트리거를 깨도 초록 → 기대치 대조에 **트리거·제약 목록**을 넣고, V8 적용 전후로 기존 append-only test 가 그대로 초록인지 확인. (2) 스키마 기대치가 **생성된 스키마에서 자동 추출**되면 무엇을 확인하는지 없어진다(자기 증명) → 기대치는 **손으로 선언**하고 test 는 그것과 카탈로그를 대조(차이가 있으면 붉게). (3) 낙관적 전제조건이 `UPDATE` 에는 있고 **UPSERT 경로에는 없으면** 우회 → 세션 write 경로가 하나임을 grep 으로 실측하고 두 경로가 생기면 둘 다. (4) 충돌 실패가 예외인데 호출부가 **삼키면** 조용한 덮어쓰기와 같아진다 → 예외가 workflow 를 통과해 밖으로 나오는 것을 test 로 고정(삼키는 자리 0 을 실측). (5) `session_version` 을 DB 기본값·트리거가 정하면 D-6B1-3 이 무너진다 → 트리거 부재를 카탈로그로 확인. (6) 복원 경로가 `internal` 을 우회(리플렉션·같은 패키지 선언)하면 (2b) 가 열린다 → 새 public 시그니처 증분 0 을 AST 로.

## (2b) 값 획득 축

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `JdbcEditSessionRepository`(public class) | 세션 읽기·쓰기 | **경계로 처리** — 배선 주체가 port 로 이미 갖는 권한과 동치. 생성자는 DataSource 주입뿐이고 도메인 값을 만들지 않음을 실측 |
| `EditSessionRow` 의 도메인 복원 | `EditSession` 인스턴스 생성 | 닫는다 — D-6B1-6, 가시성을 넓히지 않고 새 public 표면 0(AST 증분) |
| `config/quality/schema-baseline.properties` | 게이트 기대치 | **경계로 처리** — 값을 느슨하게 바꾸면 게이트가 약해진다. 6C 의 `OPEN-6C-POLICY-GATE-STRUCTURAL` 과 같은 계열이며, 이 slice 는 **기대치를 손으로 선언**하는 형태까지(자동 추출 금지, 우회 (2)) |

## 하네스 레인 변경

`git log --oneline c4d09cc..HEAD -- CLAUDE.md .claude/ docs/harness/` — 없음(착수 시점).

## OPEN — 수령·신설

| 식별자 | 처분 |
| --- | --- |
| M4/4B 알려진 제한 ②(세션 영속 실 구현·`sessionVersion` 미검증) | 이 slice ③ 으로 **종결 예정** |
| `OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND` | **6B-4**(D-6B1-2) — 큐 정책 결정 선행 |
| `OPEN-6B1-SAVE-OUTCOME`(신설) | 충돌을 결과 타입으로 나를지 — 두 번째 writer(API·스케줄러)가 생길 때 workflow 도메인 결정과 함께 |
| `OPEN-6B1-INDEX-GAPS`(신설) | ② 가 등재할 인덱스 공백 — 소비 질의를 가진 slice 가 근거와 함께 추가(D-6B1-5) |

## 리뷰 레인

**마이그레이션 파일이 생기므로 `migration-reviewer` 가 추가로 붙는다**(전역 규약 §3). Codex 심판은 되돌리기 어려운 경로(DB 마이그레이션)라 **대상이 되지만 유료 외부 호출**이므로 운영자에게 범위·비용을 제시하고 승인받은 뒤에만 건다 — 리뷰 요청 시점에 묻는다.

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-17 완료 보고 뒤(6) | in_scope 에 기계적 귀결 셋 등재 — conformance fake 시그니처(D-6B1-7 의 귀결) · 신설 표의 test 간 정리 목록 · 신설 test 의 양방향 등재. **알려진 제한이 아니라 in_scope** 다(slice 의 커밋 집합 = in_scope 경로의 변경, 하네스 2026-09-04) | 구현 레인 완료 보고의 「scope 밖 기계적 편집 3건」. 셋 다 in_scope 변경이 **강제한** 편집이고 우회가 아니다 |
| 2026-09-17 구현 중(5) | in_scope 에 `workflow/.../EditSessionRestore.kt` 등재(detekt 파일당 함수 상한으로 스냅숏 파일에서 기계적 분리) · **S-31 acceptance 명령 정정** — D-6B1-8 이 삭제한 파일을 계속 가리키고 있었다(**팀장 오류** — 갱신 (3)(4)가 in_scope·결정·이력만 고치고 acceptance 절에 전파되지 않았다. 「낡는 좌표」의 계약 내부 판) | 구현 레인 보고(새 파일 1·낡은 좌표 1) |
| 2026-09-17 구현 중(4) | **D-6B1-9 신설** — 세션 어댑터 패키지를 `adapters.persistence` → **`adapters.strategy`**(신설) + 전용 의존 게이트 test 추가, in_scope 경로 교체 | 구현 레인 정지·보고: `persistence` 패키지는 workflow 참조가 기존 게이트로 금지돼 있고, 같은 필요로 `adapters.event` 가 이미 갈라져 있다(그 KDoc 이 allow-list 확대를 거부). **6F-1 의 전략 저장소도 같은 벽을 만나므로 같은 패키지를 쓴다**(6F-1 계약 갱신 (2)) |
| 2026-09-17 구현 중(3) | **D-6B1-8 신설** — clean DB 축을 기존 여덟 축 계열 확장으로(새 게이트·속성 파일 삭제), in_scope 교체(신규 둘 → 기존 넷), ① 문면 정정 · **계약 초판의 전제 오류를 사실로 선언**(공유 컨테이너가 이미 빈 DB 전건 적용을 충족한다) | 구현 레인 정지·보고: 「바퀴 재발명 금지」(CLAUDE.md)에 걸렸고 기존 계열이 더 엄격하다. 선택지 셋 중 1(기존 확장)을 채택 — 2(중복 유지)는 평행 메커니즘 둘, 3(기존 넷을 속성 파일 방식으로 흡수)은 과거 slice 넷의 산출물 재작성이라 범위 초과 |
| 2026-09-17 구현 전(2) | **D-6B1-7 신설** — `load` 반환만 원시 스냅숏으로 좁게 재개방(`save` 불변), in_scope 에 workflow 네 자리 추가(Ports·스냅숏 타입·호출부·test fake 반환 타입) · out_of_scope 문구를 `save` 한정으로 정정 · 충돌 ②(트리거) **차단 아님**으로 확정(기존 트리거 25 개가 세 표군에만 붙어 있고 신설 표는 대상 아님 — 실측), 충돌 ③ 은 구현 순서 3 에서 확정 · 인덱스 감사 완료(표 11·인덱스 14·제약 93·트리거 25, **소비 질의 없는 FK 인덱스 공백 4** → `OPEN-6B1-INDEX-GAPS` 등재, 추가 없음) | 구현 레인 정지·보고(앞 레인이 네트워크 오류로 죽으며 유실한 「충돌 셋」의 내용). 선례가 가리키는 해법이 계약이 막아 둔 항목과 **이름만 같았다** |
| 2026-09-17 착수 | 초판 — D-6B1-1~6 | 6C 병합 뒤 기록된 착수 순서(6C → 6B) · M6 입력 재고 §3 · M4/4B 인계 |
