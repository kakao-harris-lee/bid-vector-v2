# M6/6B-1 — clean DB 재현 · 제약·인덱스 실측 · 낙관적 동시성(세션 영속) (2026-09-17)

```yaml
milestone: M6
slice: 6b1-schema-and-optimistic-concurrency
base_sha: c4d09cc   # PR #31(6C) 머지 커밋 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - adapters/src/main/resources/db/migration/V8__edit_session.sql     # 세션 영속 표 신설 — id PK · state · expires_at · session_version · actor · last_command · 감사 열. 낙관적 동시성은 session_version 전제조건으로(트리거가 값을 정하지 않는다 — V2 의 revision 과 다른 축, D-6B1-3)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcEditSessionRepository.kt   # `EditSessionRepository` 실 구현(M4/4B 알려진 제한 ② 「세션 영속 실 구현 부재」 인계)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/EditSessionRow.kt              # 행 ↔ 도메인 매핑. `EditSession` 은 `internal constructor`(M4 가 위조를 닫았다)이므로 복원 진입점의 가시성을 넓히지 않는 방법을 설계 검토에서 정한다
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                         # 세션 SQL 문자열 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcEditSessionRepositoryTest.kt   # 왕복·낙관적 충돌(0행 → 실패)·상태 전이 보존·만료 시각 왕복
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanDatabaseReproductionTest.kt    # 완료 조건 1·6B ① — 빈 컨테이너에 V1~V8 전건 적용 뒤 스키마 불변식 실측(표·제약·인덱스·트리거 목록을 카탈로그에서 읽어 정책과 대조)
  - config/quality/schema-baseline.properties                          # 위 test 가 대조하는 스키마 기대치(표·PK·UNIQUE·인덱스·트리거 목록). 매직값을 test 에 박지 않는다(v2-지침서 §5)
  - reports/evidence/m6/6b1/**
  - milestone-6.md                                                     # 6B 분할·6B-1 착수 문단(팀장 커밋)
out_of_scope:
  - backup/restore·migration rollback 정책과 rehearsal   # **6B-2** — 완료 조건 7 의 자리. 운영 절차·덤프·복원 리허설은 스키마 축과 독립이다(D-6B1-1)
  - raw/canonical/audit/outbox 데이터 수명·마스킹          # **6B-3** — 보존·파기는 privacy-gate 대상 축이고 승인된 보존 기간이 아직 없다(D-6B1-1)
  - ML job 영속·큐 상한(`OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND`) # **6B-4** — Python·파일 계열이고 저장소가 Postgres 가 아니다. 큐 정책(거부·대기·백프레셔) 결정이 선행한다(D-6B1-2)
  - `EditSessionRepository` port 시그니처 변경             # 충돌을 결과 타입으로 나르려면 workflow(M4 종결 산출물)와 그 호출부·fake 전부가 바뀐다 — D-6B1-4 로 이 slice 는 **실패를 큰 소리로** 내고, 결과 타입화는 두 번째 writer 가 생길 때(`OPEN-6B1-SAVE-OUTCOME`)
  - 인덱스 신설·제약 강화 자체                            # 이 slice 는 **실측과 공백 등재**까지(카탈로그 대조). 실제 추가는 소비 질의가 있는 slice 가 근거와 함께(D-6B1-5)
  - app 배선·HTTP 진입점                                  # 6A
acceptance_commands:
  - "./gradlew --no-daemon check"                                                   # S-10 — 전건(evidence 커밋마다 그 HEAD 에서, 마지막 HEAD 결과는 보고·PR 코멘트가 정본)
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcEditSessionRepositoryTest*' --rerun-tasks"      # S-30 — 세션 왕복·낙관적 충돌(캐시 우회)
  - "./gradlew --no-daemon :adapters:test --tests '*CleanDatabaseReproductionTest*' --rerun-tasks"      # S-31 — 빈 컨테이너 V1~V8 전건 + 스키마 대조
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

① **clean DB 전체 재현을 게이트로** — 지금은 Testcontainers 가 test 마다 Flyway 를 태워 **간접** 재현만 한다(그 test 들의 목적은 도메인 왕복이다). 빈 컨테이너에 V1~V8 을 전건 적용한 뒤 **카탈로그를 읽어** 표·PK·UNIQUE·인덱스·트리거 목록을 기대치와 대조한다. 기대치는 `config/quality/schema-baseline.properties`(값의 자리를 test 밖으로).
② **제약·인덱스 실측과 공백 등재** — 현재 인덱스 셋(감사·수집 회계·outbox 순서)과 제약을 카탈로그에서 뽑아 evidence 에 표로 남기고, **소비 질의가 있는데 인덱스가 없는 자리**를 공백으로 등재한다. 추가 자체는 근거를 가진 slice 가 한다(D-6B1-5).
③ **낙관적 동시성 실물** — `EditSessionRepository` 의 실 구현을 세우고 `session_version` 을 **전제조건**으로 쓴다(`UPDATE … WHERE session_version = :expected`, 0행이면 실패). M4 가 port+fake 까지만 하고 남긴 자리이며, 지금 저장소에는 `revision`(V2 트리거가 정하는 값)만 있고 **잃어버린 갱신을 막는 전제조건이 어디에도 없다**.

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6B1-1** | 6B 를 6B-1(스키마·동시성)·6B-2(백업·복원·마이그레이션 되돌림)·6B-3(데이터 수명·마스킹)으로 가른다 | 축이 다르고(코드 vs 운영 절차 vs 보존 정책) 6B-3 은 **승인된 보존 기간이 없어** 결정 선행이다. 6C 의 3 라운드가 혼합 slice 의 비용을 실측했다 |
| **D-6B1-2** | ML job 영속·큐 상한(M5 이월 둘)은 **6B-4** — 이 slice 밖 | Python·파일 계열이고 Postgres 가 아니다. 큐 정책(거부·대기·백프레셔)이 먼저 결정돼야 하며 `WORKER_RESOURCE_EXHAUSTED` 는 계약에 있으나 생산 경로가 없다 |
| **D-6B1-3** | 세션 표의 `session_version` 은 **애플리케이션이 싣고 DB 가 전제조건으로 검사**한다 — V2 의 `revision`(트리거가 정하는 값)과 다른 축이다 | 낙관적 동시성은 「내가 읽은 값이 아직 그 값인가」를 묻는 것이고, 트리거가 값을 정하면 그 질문이 사라진다. 두 메커니즘을 한 표에 섞지 않는다 |
| **D-6B1-4** | 충돌은 port 시그니처를 바꾸지 않고 **큰 소리로 실패**한다(구체 예외) — 결과 타입화는 `OPEN-6B1-SAVE-OUTCOME` | `save(session): Unit` 에 충돌 통로가 없다. 결과 타입으로 나르려면 M4 종결 산출물(workflow)과 호출부·fake 가 전부 바뀌고 「충돌 시 무엇을 하는가」는 workflow 도메인 결정이다. **조용한 덮어쓰기만은 허용하지 않는다** — 그것이 이 축의 요점이다 |
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
| 2026-09-17 착수 | 초판 — D-6B1-1~6 | 6C 병합 뒤 기록된 착수 순서(6C → 6B) · M6 입력 재고 §3 · M4/4B 인계 |
