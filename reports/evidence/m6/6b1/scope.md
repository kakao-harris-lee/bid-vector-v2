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
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt  # 계약 갱신 (2): `sessions.load` 호출부가 스냅숏 → 복원을 지난다(로직 무변경). 계약 갱신 (9)·D-6B1-10: `process`의 `sessions.save` 호출을 `outcome.session !== session`으로 조건화(전이·판정 로직 `Transition.kt` 는 무변경)
  - workflow/src/test/kotlin/bidvector/workflow/strategy/**              # 계약 갱신 (2): `EditSessionRepository` fake 넷의 `load` 반환 타입만(단언·시나리오 무편집). 계약 갱신 (11): MEDIUM-8 — `EditSessionTransitionTableTest`에 저장 판별자(`outcome.session !== session ⟹ sessionVersion +1`) 불변식 test 신설(`Transition.kt` 는 무편집)
  - adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcEditSessionRepository.kt      # 계약 갱신 (4)·D-6B1-9: **패키지는 `adapters.strategy`**(신설) — `persistence` 는 workflow 참조가 게이트로 금지돼 있다
  - adapters/src/main/kotlin/bidvector/adapters/strategy/EditSessionRow.kt                 # 행 → 스냅숏 매핑(D-6B1-7). 도메인 복원은 workflow `internal`
  - adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt  # 계약 갱신 (4): 신설 패키지 전용 의존 게이트(허용 = `workflow.strategy`·`strategy`·`sharedkernel`·`adapters.persistence` 의 트랜잭션 경계). `adapters.event` 의 전례와 같은 형태. 계약 갱신 (9): MEDIUM-5 수정 — 술어를 점 표기 좌표까지 보게, `javap` 를 `java.home` 기준으로 해석(code-reviewer 지적)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                         # 세션 SQL 문자열 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcEditSessionRepositoryTest.kt   # 왕복·낙관적 충돌(0행 → 실패)·상태 전이 보존·만료 시각 왕복. 계약 갱신 (10): 공용 fixture 를 `EditSessionWorkflowTestSupport`로 추출(500줄 한도, 설계 변경 아님)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/EditSessionWorkflowTestSupport.kt   # 계약 갱신 (10, 신설) — `JdbcEditSessionRepositoryTest`·`JdbcEditSessionSaveGuardTest` 공용 fixture(500줄 한도로 기계적 추출, `EditSessionRestore.kt`·`CleanMigration*Test` 넷과 같은 전례)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcEditSessionSaveGuardTest.kt   # 계약 갱신 (9)·(10, 신설) — D-6B1-10 재현·회귀 보호: 실 저장소+실 workflow 로 거부·중복재전달 다섯 경로(예외 없이 문서화된 결과로 반환·버전 불변) + 명령 중 만료 fold(정당한 저장) 여섯
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt          # 계약 갱신 (3)·D-6B1-8: **기존 여덟 축 계열에 `edit_session` 을 「추가만」으로 등재** — 축1 테이블·축5 UNIQUE·축5b PK·축6 FK·축9 GRANT 유효권한
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt    # 축2·3·4 — 컬럼 존재·타입·NOT NULL
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt   # 축7 — 트리거(신설 표는 트리거 0 임을 **등재**해야 한다, 지금은 안 붉지만 누락 상태)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt     # 축8 — CHECK 개수 등재
  - app/src/test/kotlin/bidvector/app/conformance/**        # 계약 갱신 (6): `EditSessionRepository.load` 반환 타입 변경(D-6B1-7)의 기계적 귀결 — conformance fake 의 시그니처만
  - adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt   # 계약 갱신 (6): 신설 표를 test 간 정리 목록에 추가(기계적)
  - config/quality/gate-tests.properties                    # 계약 갱신 (6): 신설 test 를 양방향 등재 게이트에 등재(그 게이트가 요구한다 — 우회가 아니라 이행). 계약 갱신 (10): `StrategyAdapterDependencyTest`(D-6B1-9, 형제 다섯 `*AdapterDependencyTest`는 이미 등재돼 있었다) 등재 누락을 verifier r3 MEDIUM-5 수정 라운드에서 발견해 보충 — 추가만(6F-1 도 이 공유 파일을 만진다)
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
| **D-6B1-10** (수정 라운드 2, 계약 갱신 (9), 팀장 결정) | `EditStrategyWorkflow.process`는 **`outcome.session`이 [session](호출 시작 시 로드한 인스턴스)과 같은 인스턴스일 때 저장하지 않는다**(`begin`/`expire`가 이미 쓰는 `folded !== existing`/`expired !== session` 관용구와 같은 판별자). 이 편집은 D-6B1-7 이 그은 `workflow/**` 범위(포트 반환·스냅숏·복원·호출부·fake)를 **넘는다** — **그 범위를 그렇게 좁게 그은 것이 팀장의 오류였다.** 「언제 쓰는가」는 전이 로직이 아니라 영속 조율이고 이 slice 의 주제다 | verifier r3 HIGH-3 — `JdbcEditSessionRepository`는 낙관적 동시성 전제조건(저장소 버전 = 새 값 − 1)을 요구하는데 `process`가 결과 갈래와 무관하게 언제나 저장해, 버전이 안 오른 `Rejected` 전부와 중복 재전달의 `Accepted`가 항상 0행 → `EditSessionConflictException`으로 터졌다(재현 다섯: `R2 1`·`2b`·`3b`·`3c`·`4b`). 어댑터 쪽 흡수(전제조건 완화)는 거부한다 — 저장소 정의가 옳고 호출부가 틀렸다. 두 test 축(`EditStrategyWorkflowTest`·conformance fake)이 전제조건 없는 맵이라 이 결함을 못 봤다는 것도 verifier r3 실측 — 회귀 보호는 실 저장소 + 실 workflow 로 다섯 경로를 잠근다(`JdbcEditSessionRepositoryTest`) |
| **D-6B1-6** | `EditSession` 의 복원은 **가시성을 넓히지 않는다** — M4 가 `internal constructor` 로 위조를 닫았다. 복원 진입점의 형태는 설계 검토에서 정하고(같은 모듈 내 팩토리 · 전용 통로 타입 중) 새 public 표면 0 을 실측한다. **실제 강도(verifier r2 LOW-5)**: 이 경계는 Kotlin 소스 경계다 — 컴파일러 + `sourceLanguageGate`(Java 우회 차단, `check`에서 실측) 두 겹이고, `friendPaths` 설정 0건이 셋째 겹이다. **리플렉션은 이 세 겹 밖이고 막는 장치가 없다**(`setAccessible` 없이도 임의 `EditSession` 생성 가능, verifier r2 실측) — 이 한계는 6B-1 고유가 아니라 `OperatorStrategy`(1E)·`AppliedStrategy`(M4)도 공유하는 저장소 공통 성질이고, `EditSession` 주 생성자는 이 slice 이전에도 이미 바이트코드 public 이었다(한계 증분 0) | M4/4C-1 의 high 가 정확히 「public 복원 진입점이 아무 모듈에나 위조를 허락한다」였다. 같은 자리를 다시 열지 않는다 |

## 위협 모델 — 6B-1 고유 경계

**방어한다**: (a) 빈 DB 에서 마이그레이션 전건이 서고 스키마가 기대치와 같다(카탈로그 실측) (b) 세션의 **잃어버린 갱신**이 조용히 성립하지 않는다(전제조건 0행 → 실패) (c) **`adapters`·`app`의 Kotlin 소스로 도달 가능한 새 도메인 위조 진입점을 만들지 않는다**(새 public 표면 0, Kotlin 소스 경계 — verifier r2 LOW-5 시정: 리플렉션은 이 경계 밖이고 이 slice 도 다른 slice(1E·M4)도 막지 않는다) (d) 마이그레이션이 append-only·provenance 가드(V2·V3)를 무력화하지 않는다(기존 게이트 test 무편집 통과).
**방어하지 않는다(추가, verifier r2 LOW-5)**: 리플렉션으로 `internal constructor`를 우회하는 위조 — Kotlin 컴파일러·`sourceLanguageGate`·`friendPaths` 부재 세 겹은 **소스 경계**만 지키고 JVM 접근 제어는 아무것도 지키지 않는다. `EditSession`의 한계 증분은 0(이 slice 이전에도 바이트코드 public).
**방어하지 않는다**: 백업·복원·마이그레이션 되돌림 절차(6B-2) · 데이터 보존·파기·마스킹(6B-3) · ML job 영속·큐(6B-4) · 인덱스·제약 추가(D-6B1-5) · 다중 writer 하의 재시도·거부 정책(`OPEN-6B1-SAVE-OUTCOME`) · 운영 DB 접근(승인 대상).

**우회 후보**: (1) clean DB test 가 **자기 컨테이너에만** 참이고 마이그레이션 순서·의존을 안 재면 V8 이 V2 트리거를 깨도 초록 → 기대치 대조에 **트리거·제약 목록**을 넣고, V8 적용 전후로 기존 append-only test 가 그대로 초록인지 확인. (2) 스키마 기대치가 **생성된 스키마에서 자동 추출**되면 무엇을 확인하는지 없어진다(자기 증명) → 기대치는 **손으로 선언**하고 test 는 그것과 카탈로그를 대조(차이가 있으면 붉게). (3) 낙관적 전제조건이 `UPDATE` 에는 있고 **UPSERT 경로에는 없으면** 우회 → 세션 write 경로가 하나임을 grep 으로 실측하고 두 경로가 생기면 둘 다. (4) 충돌 실패가 예외인데 호출부가 **삼키면** 조용한 덮어쓰기와 같아진다 → 예외가 workflow 를 통과해 밖으로 나오는 것을 test 로 고정(삼키는 자리 0 을 실측). (5) `session_version` 을 DB 기본값·트리거가 정하면 D-6B1-3 이 무너진다 → 트리거 부재를 카탈로그로 확인. (6) 복원 경로가 `internal` 을 우회(리플렉션·같은 패키지 선언)하면 (2b) 가 열린다 → 새 public 시그니처 증분 0 을 AST 로.

## (2b) 값 획득 축

**verifier r1 MEDIUM-4 시정** — 초판 표 세 행 중 하나(`schema-baseline.properties`)는 D-6B1-8
이 만들지 않기로 결정한 파일이라 제거한다. 계약 갱신 (2)·(5) 가 실제로 새로 내놓은 public
표면 일곱(`EditSessionSnapshot`·`EditableFieldSnapshot`·`MoneySnapshot`·
`StrategyDraftSnapshot`·`EditCommandSnapshot`·`fun EditSession.toSnapshot()`·
`EditSessionConflictException`)이 한 행도 없었다 — 아래에 채운다.

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `JdbcEditSessionRepository`(public class) | 세션 읽기·쓰기 | **경계로 처리** — 배선 주체가 port 로 이미 갖는 권한과 동치. 생성자는 `DataSource` 주입뿐이고 도메인 값을 만들지 않음을 `javap` 로 실측(공개 멤버는 `load`·`save` 뿐, port 시그니처와 정확히 같다) |
| `EditSessionRow` 의 도메인 복원 | (없음 — `EditSession` 인스턴스를 만들지 않는다) | 닫는다 — D-6B1-6. `javap` 실측(`bidvector.adapters.strategy.EditSessionRow`)으로 공개 멤버가 전부 `EditSessionSnapshot`·`String` 만 반환함을 확인, `EditSession` 반환 0건 |
| `EditSessionSnapshot`(public data class, public 생성자) | 원시 필드로 `EditSessionRepository.load()` 반환값을 구성 | **연다(제한적) — verifier r1 이 읽어서 지적, r2 가 실행으로 확인(순수 Kotlin, 리플렉션·Java·가시성 우회 없음).** port(`EditSessionRepository`)가 public 이라 `adapters` 에서 그 port 를 구현해 위조 스냅숏을 돌려주면 `EditStrategyWorkflow.expire()`가 그것을 받아들여 `Applied(revision=999999)`(`lastCommand=ProvideValue`·`sessionVersion=0`, 전이표가 만들 수 없는 조합)를 정당한 도메인 값으로 만들었다. `restoreEditSession`은 필드별 어휘(state kind·command kind·provenance 등, HIGH-2 로 강화됨)는 거부하지만 **필드 간 정합**은 검사하지 않는다 — `EditSession`에 `init` 불변식이 없다. **HIGH-2 와 같은 결함 계열**(복원이 받아들여선 안 되는 값을 받아들인다)이지만 등급은 MEDIUM — 발동에 세션 저장소로 배선되는 권한이 필요하고 그 권한은 이 표가 이미 「경계로 처리」로 판정한 것과 같다. D-6B1-7 **이전에는** `internal constructor`가 이 주입 자체를 원천 차단했다 — **이것이 D-6B1-7 이 만든 유일한 실제 증분**이다. **오늘의 실제 노출**: 실 구현은 `JdbcEditSessionRepository` 하나뿐이고 `EditStrategyWorkflow`는 6A 이전이라 아무 곳에도 배선되지 않아 실제 주입 지점이 없다. **닫는 문 후보 둘**(둘 다 `workflow/**` 를 D-6B1-7 범위(스냅숏 배관) 밖으로 넓히는 편집이라 코딩하지 않고 계약 결정으로 올린다): ① `Transition.kt`에 (상태, `lastCommand`) 정합 조합을 조회하는 `internal` 함수를 신설해 `dispatch()`와 `restoreEditSession` 양쪽이 그 함수를 참조하게 한다(재사용, 복제 아님) ② `EditSessionTypes.kt`의 `EditSession`에 `init` 불변식을 더해 **모든** 생성 경로(`beginSession`·`accept`·`onConfirm`·리플렉션 제외)에서 정합을 강제한다(방어 심층 — `Transition.kt` 자체 버그도 같이 잡지만 무엇을 "정합"으로 볼지는 여전히 어딘가에 선언해야 한다). **`OPEN-6B1-CROSS-FIELD-CONSISTENCY`로 등재**한다(아래) |
| `EditableFieldSnapshot`·`MoneySnapshot`·`StrategyDraftSnapshot`·`EditCommandSnapshot`(public data class 넷) | `EditSessionSnapshot`의 nested 원시 필드 구성 | **경계로 처리** — 전부 원시 스칼라·리스트만 담고 그 자체로는 아무 권한도 열지 않는다. 위 `EditSessionSnapshot` 행의 잔여 위험에 얹힐 뿐 개별 위험은 없다 |
| `fun EditSession.toSnapshot()`(public 확장 함수) | `EditSession` → `EditSessionSnapshot` | 닫는다(안전한 방향) — 이미 정당하게 보유한 `EditSession`에서 값을 꺼낼 뿐이라 새 권한을 만들지 않는다 |
| `EditSessionConflictException`(public class) | 충돌 정보(`sessionId`·`expectedVersion`) 전달 | **경계로 처리** — 순수 정보 전달용 예외, 생성 자체가 아무것도 우회하지 않는다 |

## 하네스 레인 변경

`git log --oneline c4d09cc..HEAD -- CLAUDE.md .claude/ docs/harness/` — 없음(착수 시점).

## OPEN — 수령·신설

| 식별자 | 처분 |
| --- | --- |
| M4/4B 알려진 제한 ②(세션 영속 실 구현·`sessionVersion` 미검증) | 이 slice ③ 으로 **종결 예정** |
| `OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND` | **6B-4**(D-6B1-2) — 큐 정책 결정 선행 |
| `OPEN-6B1-SAVE-OUTCOME`(신설) | 충돌을 결과 타입으로 나를지 — 두 번째 writer(API·스케줄러)가 생길 때 workflow 도메인 결정과 함께 |
| `OPEN-6B1-INDEX-GAPS`(신설) | ② 가 등재할 인덱스 공백 — 소비 질의를 가진 slice 가 근거와 함께 추가(D-6B1-5) |
| `OPEN-6B1-CROSS-FIELD-CONSISTENCY`(신설, verifier r1 MEDIUM-4 지적 → r2 실행으로 확인) | `restoreEditSession`이 (상태, `lastCommand`) 필드 간 정합을 검사하지 않는다 — **순수 Kotlin, 리플렉션·Java·가시성 우회 없이 실행 재현됨**(`Applied(999999)`+`lastCommand=ProvideValue`+`sessionVersion=0`). 닫는 문 후보 둘(둘 다 `workflow/**`를 D-6B1-7 범위(스냅숏 배관) 밖으로 넓혀 이 slice 에서 코딩하지 않는다): ① `Transition.kt`에 정합 조회 함수 신설(재사용) ② `EditSession`에 `init` 불변식 추가(방어 심층). 오늘은 `EditSessionRepository` 실 구현·배선이 `JdbcEditSessionRepository` 하나뿐이고 `EditStrategyWorkflow`가 6A 이전이라 배선 안 돼 실제 주입 지점이 없다 — 6A 배선 slice 또는 `Transition.kt` 재설계 slice에서 팀장 계약 결정으로 처분 |
| `OPEN-6B1-REGISTRATION-GATE-GENERALIZATION`(신설, verifier r4 MEDIUM-7 지적) | `gate-tests.properties` 양방향 등재를 자동으로 채우는 장치가 셋뿐이고 **전부 자기 디렉터리를 하드코딩**한다 — `adapters.strategy`(신설 패키지)는 그런 장치가 없어 `StrategyAdapterDependencyTest`가 「의존 게이트 + 등재 게이트」 관례의 절반(의존 게이트)만 갖춘 채 남았고, 그래서 이번 라운드에 등재 누락이 생겼다(승인 전 일괄, gate-tests.properties 계약 갱신 (10)에서 수동 보충). **세 번째 하드코딩 복제를 만들지 않는다** — 디렉터리를 입력으로 받는 일반화가 맞고 그건 이 slice 밖 별도 slice 감이다. 새 패키지가 생길 때마다 같은 누락이 재발할 구조적 자리이므로, 등재 게이트 생성기(또는 검증기)를 디렉터리 인자화하는 slice에서 처분 |

## 리뷰 레인

**마이그레이션 파일이 생기므로 `migration-reviewer` 가 추가로 붙는다**(전역 규약 §3). Codex 심판은 되돌리기 어려운 경로(DB 마이그레이션)라 **대상이 되지만 유료 외부 호출**이므로 운영자에게 범위·비용을 제시하고 승인받은 뒤에만 건다 — 리뷰 요청 시점에 묻는다.

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-17 승인 전 일괄(12) | **LOW-7 등재**(`checklist.md` 알려진 제한 7 — MEDIUM-5 가 넓힌 정규식이 산문 문자열 상수도 걸린다, 코드 수정 없음) · **`OPEN-6B1-REGISTRATION-GATE-GENERALIZATION` 신설**(MEDIUM-7 — 등재 게이트 자동화 장치 셋이 전부 디렉터리 하드코딩이라 신설 패키지엔 없다, 복제 대신 일반화 방향으로 별도 slice) | verifier r4 LOW-7·MEDIUM-7 — 둘 다 코드 수정 없이 장부층 등재만 |
| 2026-09-17 승인 전 일괄(11) | **MEDIUM-8 시정** — `EditSessionTransitionTableTest`에 저장 판별자 불변식 test 신설(`outcome.session !== session`이면 반드시 `sessionVersion`이 입력보다 정확히 1 크다). `RejectionReason` 여섯(`SessionAlreadyActive` 제외 — `begin()` 전용) + `Accepted`·`Applied` 전 갈래를 돈다. 판별자(`!==`)는 바꾸지 않는다(`begin()`/`expire()`와 일관, code-reviewer가 현 전이표에서 구조적으로 옳음을 확인) — 등가 자체를 test 로 잠근다. `Transition.kt`(전이·판정 로직)는 무편집 | verifier r4 MEDIUM-8 — 「버전이 올랐다」와 「인스턴스가 다르다」의 등가는 현재 전이표에서만 참이고 코드에 안 잠겨 있었다. `StaleRevision` 거부 갈래에 "새 인스턴스인데 버전 불변"을 심어도 `:workflow:test`·어댑터 test 전건이 초록이었다(신설 여섯이 덮는 거부 사유는 셋뿐) |
| 2026-09-17 수정 라운드 2(10) | 수정 라운드가 만든 새 파일 둘을 in_scope 에 등재 — `EditSessionWorkflowTestSupport.kt`(공용 fixture, 500줄 한도로 기계적 추출)·`JdbcEditSessionSaveGuardTest.kt`(D-6B1-10 회귀 보호 여섯 test). `StrategyAdapterDependencyTest`의 `config/quality/gate-tests.properties` 등재 누락(D-6B1-9, 형제 다섯은 이미 등재)을 발견해 보충(추가만) | 하네스 2026-09-10 「수정 라운드가 만드는 새 파일은 in_scope 와 대조해 계약을 갱신한다」. 팀장 재개 지시 항목 2 |
| 2026-09-17 수정 라운드 2(9) | **D-6B1-10 신설** — `EditStrategyWorkflow.process`가 `outcome.session !== session`일 때만 저장하도록 조건화. `workflow/**`를 D-6B1-7 범위(스냅숏 배관) 밖으로 넓힌 것을 **팀장 오류로 사실 등재**(그 범위를 좁게 그은 것 자체가 오류였다) — in_scope 의 `EditStrategyWorkflow.kt`·`JdbcEditSessionRepositoryTest.kt` 서술 갱신. `Transition.kt`(전이·판정 로직)는 무편집 | verifier r3 HIGH-3 — 정당한 거부·멱등 재전달의 저장이 낙관적 동시성 전제조건에 걸려 예외로 터졌다(재현 다섯: `R2 1`·`2b`·`3b`·`3c`·`4b`). 어댑터 쪽 흡수는 거부(저장소 정의가 옳다) |
| 2026-09-17 수정 라운드 1(8) | D-6B1-6·위협 모델 (c)·(2b) `EditSessionSnapshot` 행·`OPEN-6B1-CROSS-FIELD-CONSISTENCY`를 verifier r2 실행 증거로 갱신 — 필드 간 정합 공백이 **이론이 아니라 실행 재현**(순수 Kotlin, `Applied(999999)`+`ProvideValue`+`v0`)임을 반영, 닫는 문 후보 둘(`Transition.kt` 정합 조회 함수 신설 / `EditSession` init 불변식) 명시 후 코딩하지 않고 계약 결정으로 올림. `internal` 위조 차단의 실제 강도(Kotlin 컴파일러+`sourceLanguageGate`+friendPaths 부재 세 겹, 리플렉션은 경계 밖·무방비, 이 slice 고유 한계 아님)를 D-6B1-6·위협 모델에 등재 | verifier r2 LOW-5(문면이 "위조 불가"로 읽히나 실제는 "Kotlin 소스 경계") · MEDIUM-4 승격(읽기 지적 → 실행 확인) |
| 2026-09-17 수정 라운드 1(7) | (2b) 표를 일곱 행으로 갱신(`EditSessionSnapshot`·nested 넷·`toSnapshot()`·`EditSessionConflictException`) + 판정되지 않았던 실질(`EditSessionSnapshot`이 여는 필드 간 정합 공백) 판정·문서화 + `schema-baseline.properties` 낡은 행 제거 + `OPEN-6B1-CROSS-FIELD-CONSISTENCY` 신설(닫으려면 `Transition.kt` 변경 필요 — 이 slice 범위 밖으로 판단, 코딩하지 않음) | verifier r1 MEDIUM-4 — 계약이 그 뒤 다섯 번 갱신되는 동안 (2b) 표를 갱신하지 않았다(하네스 2026-09-10 「수정 라운드마다 갱신」 위반) |
| 2026-09-17 완료 보고 뒤(6) | in_scope 에 기계적 귀결 셋 등재 — conformance fake 시그니처(D-6B1-7 의 귀결) · 신설 표의 test 간 정리 목록 · 신설 test 의 양방향 등재. **알려진 제한이 아니라 in_scope** 다(slice 의 커밋 집합 = in_scope 경로의 변경, 하네스 2026-09-04) | 구현 레인 완료 보고의 「scope 밖 기계적 편집 3건」. 셋 다 in_scope 변경이 **강제한** 편집이고 우회가 아니다 |
| 2026-09-17 구현 중(5) | in_scope 에 `workflow/.../EditSessionRestore.kt` 등재(detekt 파일당 함수 상한으로 스냅숏 파일에서 기계적 분리) · **S-31 acceptance 명령 정정** — D-6B1-8 이 삭제한 파일을 계속 가리키고 있었다(**팀장 오류** — 갱신 (3)(4)가 in_scope·결정·이력만 고치고 acceptance 절에 전파되지 않았다. 「낡는 좌표」의 계약 내부 판) | 구현 레인 보고(새 파일 1·낡은 좌표 1) |
| 2026-09-17 구현 중(4) | **D-6B1-9 신설** — 세션 어댑터 패키지를 `adapters.persistence` → **`adapters.strategy`**(신설) + 전용 의존 게이트 test 추가, in_scope 경로 교체 | 구현 레인 정지·보고: `persistence` 패키지는 workflow 참조가 기존 게이트로 금지돼 있고, 같은 필요로 `adapters.event` 가 이미 갈라져 있다(그 KDoc 이 allow-list 확대를 거부). **6F-1 의 전략 저장소도 같은 벽을 만나므로 같은 패키지를 쓴다**(6F-1 계약 갱신 (2)) |
| 2026-09-17 구현 중(3) | **D-6B1-8 신설** — clean DB 축을 기존 여덟 축 계열 확장으로(새 게이트·속성 파일 삭제), in_scope 교체(신규 둘 → 기존 넷), ① 문면 정정 · **계약 초판의 전제 오류를 사실로 선언**(공유 컨테이너가 이미 빈 DB 전건 적용을 충족한다) | 구현 레인 정지·보고: 「바퀴 재발명 금지」(CLAUDE.md)에 걸렸고 기존 계열이 더 엄격하다. 선택지 셋 중 1(기존 확장)을 채택 — 2(중복 유지)는 평행 메커니즘 둘, 3(기존 넷을 속성 파일 방식으로 흡수)은 과거 slice 넷의 산출물 재작성이라 범위 초과 |
| 2026-09-17 구현 전(2) | **D-6B1-7 신설** — `load` 반환만 원시 스냅숏으로 좁게 재개방(`save` 불변), in_scope 에 workflow 네 자리 추가(Ports·스냅숏 타입·호출부·test fake 반환 타입) · out_of_scope 문구를 `save` 한정으로 정정 · 충돌 ②(트리거) **차단 아님**으로 확정(기존 트리거 25 개가 세 표군에만 붙어 있고 신설 표는 대상 아님 — 실측), 충돌 ③ 은 구현 순서 3 에서 확정 · 인덱스 감사 완료(표 11·인덱스 14·제약 93·트리거 25, **소비 질의 없는 FK 인덱스 공백 4** → `OPEN-6B1-INDEX-GAPS` 등재, 추가 없음) | 구현 레인 정지·보고(앞 레인이 네트워크 오류로 죽으며 유실한 「충돌 셋」의 내용). 선례가 가리키는 해법이 계약이 막아 둔 항목과 **이름만 같았다** |
| 2026-09-17 착수 | 초판 — D-6B1-1~6 | 6C 병합 뒤 기록된 착수 순서(6C → 6B) · M6 입력 재고 §3 · M4/4B 인계 |
