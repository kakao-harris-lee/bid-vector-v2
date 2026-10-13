# M6/6B-3 — 데이터 수명·마스킹 (보존 90일 파기 · 원문 PII 마스킹) (착수 계약, 2026-10-13, 팀장)

- base: `e2232a75`(PR #68·#69 머지 뒤 `main`) · 브랜치 `m6-6b3/2026-10-13` · worktree `bid-vector-v2-m6-6b3`
- 정본: 이 파일. 결정 ID `D-6B3-N`. 착수 조사 `_workspace/m6-6b3/00_scout.md` · 설계 검토 `_workspace/m6-6b3/01_design-review.md`(팀장, Phase 2.5 — 구현 전 필수).
- 성격: **되돌리기 어려운 경로 둘**(데이터 파기 · DB 마이그레이션 V18) + **게이트 술어 변경**(스키마 등재 test). Codex 독립 심판 **1회 승인**(운영자 2026-10-13, 설계 검토·verifier 통과 뒤 의미 있는 단위 한 번 · request_changes 최대 2라운드).
- 레인: 구현 `kotlin-implementer`(sonnet) 하나. 판정 `verifier`(opus) + `code-reviewer`(sonnet) 병렬 + `migration-reviewer` + `privacy-gate`(로컬 정의 없음 — 호출 시 역할 지시) → Codex(`codex-reviewer`).
- 사용자 「M6 잔여 6B-3·6B-4 착수」(2026-10-13).

## 왜 이 slice 인가

운영자 결정 2026-09-26 ② 「보존 기간 90일(공고·평가 이력·audit)」이 내려졌지만 V2 에 **저장된 행을 지우거나 마스킹하는 production 코드가 0** 이다. 6F-6·6F-5-a·6F-8·6G-2c·6A-2b·6A-1·6F-7 이 보존·파기를 이 slice 로 넘겼다. 원문 `raw_observation.payload` 에는 담당자 이름·전화·이메일 키가 관측마다 쌓인다.

## 받는 OPEN

`OPEN-6F6-PROFILE-RETENTION` · `OPEN-6F5A-RETENTION` · `OPEN-6F8-RAW-PII-RETENTION` · `OPEN-6B3-RAW-OBSERVATION-RETENTION` · `OPEN-6A2B-ABANDONED-SESSIONS` · 6F-7 privacy-gate 권고(outbox) · D-6A1-7(api audit).

## 운영자 결정 (2026-10-13)

| ID | 결정 |
|---|---|
| A-1 | **파기 실행 주체 = 소유자 비풀링 연결**(`migrateAsOwner` 선례). 런타임 풀(`SET ROLE bidvector_app`)의 권한 경계는 **무변경** — 앱 역할에 DELETE·UPDATE 를 새로 주지 않는다 |
| A-2 | **기준 시각 = 적재 시각 하나로 통일**(표별 `inserted_at`·`created_at`·`recorded_at`·`occurred_at` 중 DB 가 적재 때 찍는 열) |
| A-3 | **`raw_observation`·`notice_audit` 는 파기하지 않고 PII 마스킹만** — 6G 백테스트 표본 보존. V3 append-only 트리거를 **마스킹 UPDATE 하나만** 허용하도록 V18 이 좁게 개정, DELETE 거부는 유지 |
| A-4 | **`outbox` 도 90일 파기 대상**(결정 문면의 「평가 이력」 = 판정의 유일한 영속 흔적). 단 미발송·임대 중 행 제외. **`edit_session` 의 버려진 비종단 행 포함**. `inbox`·싱글턴 둘(`operator_profile`·`operator_strategy`)·`operator_strategy_revision`·`collection_run`·`provenance_authority`·`flyway_schema_history` 는 시간 파기 **제외** |
| A-5 | Codex 독립 심판 1회(위 머리 참조) |

## 산출물

| ID | 무엇 | 공허함을 막는 술어 |
|---|---|---|
| **P-1** | **V18 마이그레이션** — ① `raw_observation`·`notice_audit` 에 `pii_masked_at TIMESTAMPTZ NULL` ② append-only 트리거 개정: DELETE 는 여전히 거부, UPDATE 는 **「`pii_masked_at` 이 NULL→값 한 번 · 마스킹 열(`payload`/`previous_row`) 말고 다른 열 불변 · 실행 역할이 앱 역할이 아님」** 일 때만 허용 ③ 그 밖 표는 GRANT 무변경 | 트리거 test: 앱 역할 UPDATE 거부 · 소유자라도 둘째 마스킹(값→값)·되돌림(값→NULL)·다른 열 변경 거부 · DELETE 거부 유지. `CleanMigration*Test` 등재 갱신(컬럼·트리거·가드 인자) |
| **P-2** | **표 분류 레지스트리**(파기 / 마스킹 / 제외 + 기준 시각 열 + 파기 조건) — `CleanMigrationTest` 의 표 집합과 **집합 등식**(새 표가 생기면 분류 없이는 RED) | 등식 test + 변이(표 하나 분류 삭제 → RED, 미등재 표 추가 → RED) |
| **P-3** | **보존 러너**(`mode=once`, 여섯째 러너) — 소유자 비풀링 연결 · 고정 `Clock` 주입 · 기준 시각 `now - 90일` · FK 순서(자식 → 부모) · 표별 일괄 크기 상한 · 표별 계수(파기 N · 마스킹 N · 건너뜀 N) 종료 보고 · 종료 코드·cause code 규약 | 경계 ±1초 생존/소멸 + 통제군(경계 안쪽 행 전부 생존) · outbox 미발송·임대 행 생존 · 제외 표 행 수 불변 · 러너 둘 동시 기동 시 하나만(임대 또는 가드) |
| **P-4** | **마스킹 함수** — `payload`/`previous_row` 의 담당자 키(`ntceInsttOfcl*`·`dminsttOfcl*` 등 실측 키 전수)를 allow-list 반전으로 치환(개찰 축 `maskOpengCorpInfo` 관례 재사용), 형태가 다르면 **값 전체 폐기(fail-closed)** · 마스킹 실패 계수 | 키 하나라도 남으면 RED · 형태 불일치 fail-closed · 이미 마스킹된 행 건너뜀 |
| **P-5** | **보존 정책 값**(90일 · 일괄 크기) — 정책 데이터 한 자리(버전 포함), 러너가 읽는 값이 wire(실제 DELETE 술어)에 닿는지 test | 값을 89/91 로 바꾸면 경계 test 가 RED(값→거동) |
| **P-6** | **runbook**(보존 절차 · cron 줄 · 멈춤 조건 · 운영 DB 실행은 별도 승인) + checklist · OPEN 처분 | — |

## 위협 모델 경계 (설계 검토 (0) 요약 — 정본은 설계 검토 노트)

**방어하는 것**: 90일이 지난 행이 실제로 사라지거나(파기 대상) PII 가 사라진다(마스킹 대상) · 90일 안쪽 행과 제외 표는 건드리지 않는다 · 앱 런타임(풀)은 여전히 아무것도 지우거나 고칠 수 없다 · 마스킹은 한 방향이며 되돌릴 수 없다 · 새 표가 분류 없이 들어올 수 없다. **방어하지 않는 것**: 소유자 자격을 가진 주체의 임의 SQL(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`) · 백업 산출물 속 과거 행(6B-2 백업의 보존은 M7) · 수집 시점 마스킹(원문은 90일 동안 PII 를 가진다 — 결정 문면 그대로) · 운영 DB 에서의 실행(별도 승인).

## in_scope / out_scope

- in: `adapters/src/main/resources/db/migration/V18__*.sql` · `adapters/src/main/kotlin/**`(보존 저장소 어댑터·마스킹) · `adapters/src/test/**` · `workflow/src/main/kotlin/**`·`workflow/src/test/**`(보존 use case·정책 값 — 필요 시) · `app/src/main/kotlin/**`(러너·배선) · `app/src/test/**` · `config/quality/gate-tests.properties` · `config/quality/*policy*.properties`(필요 시, 계약 갱신으로 확정) · `docs/runbook/**`(보존 절) · `reports/evidence/m6/6b3/**` · `milestone-6.md`(팀장 문단).
- out: 앱 역할 GRANT 확대 · 수집 시점 마스킹 · `inbox`·싱글턴·개정 이력·수집 원장 파기 · 운영/dev DB(`bid-vector-v2-dev` 55432) 실행 · pg_cron·파티셔닝 · 백업 보존 · `docker/**`·`ci.yml`(필요해지면 멈추고 계약 갱신).

## acceptance

CI `check` job 그대로: `./gradlew --no-daemon check` → `./gradlew --no-daemon qualityBaseline`. `container` job 은 compose 를 안 만지면 생략 사유 등재(필요하면 계약 갱신). `ml-engine` job 은 무변경이라 생략. 호스트 규율(빌드 전 pgrep·free·ps 별도 호출 · 직렬). Testcontainers 만 — dev DB(55432)에 닿지 않음을 단언.

## rollback

in_scope 경로 한정 `git restore --source=e2232a75 --staged --worktree --` + 공유 파일은 병합된 main SHA 에서 복원(PR #69 규칙). **V18 이 적용된 DB 의 되돌림은 forward-fix**(새 V 로 트리거 원복 · 열 DROP) — 파기된 행·마스킹된 값은 백업 복원으로만 돌아온다(6B-2 runbook). 임시 clone 에서 ①~⑥.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
