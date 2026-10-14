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
| **P-1** | **V18 마이그레이션**(D-6B3-1 로 `raw_observation` 한 표) — ① `raw_observation`·~~`notice_audit`~~ 에 `pii_masked_at TIMESTAMPTZ NULL` ② append-only 트리거 개정: DELETE 는 여전히 거부, UPDATE 는 **「`pii_masked_at` 이 NULL→값 한 번 · 마스킹 열(`payload`/`previous_row`) 말고 다른 열 불변 · 실행 역할이 앱 역할이 아님」** 일 때만 허용 ③ 그 밖 표는 GRANT 무변경 | 트리거 test: 앱 역할 UPDATE 거부 · 소유자라도 둘째 마스킹(값→값)·되돌림(값→NULL)·다른 열 변경 거부 · DELETE 거부 유지. `CleanMigration*Test` 등재 갱신(컬럼·트리거·가드 인자) |
| **P-2** | **표 분류 레지스트리**(파기 / 마스킹 / 제외 + 기준 시각 열 + 파기 조건) — `CleanMigrationTest` 의 표 집합과 **집합 등식**(새 표가 생기면 분류 없이는 RED) | 등식 test + 변이(표 하나 분류 삭제 → RED, 미등재 표 추가 → RED) |
| **P-3** | **보존 러너**(`mode=once`, 여섯째 러너) — 소유자 비풀링 연결 · 고정 `Clock` 주입 · 기준 시각 `now - 90일` · FK 순서(자식 → 부모) · 표별 일괄 크기 상한 · 표별 계수(파기 N · 마스킹 N · 건너뜀 N) 종료 보고 · 종료 코드·cause code 규약 | 경계 ±1초 생존/소멸 + 통제군(경계 안쪽 행 전부 생존) · outbox 미발송·임대 행 생존 · 제외 표 행 수 불변 · 러너 둘 동시 기동 시 하나만(임대 또는 가드) |
| **P-4** | **마스킹 함수** — `payload`/`previous_row` 의 담당자 키(`ntceInsttOfcl*`·`dminsttOfcl*` 등 실측 키 전수)를 allow-list 반전으로 치환(개찰 축 `maskOpengCorpInfo` 관례 재사용), 형태가 다르면 **값 전체 폐기(fail-closed)** · 마스킹 실패 계수 | 키 하나라도 남으면 RED · 형태 불일치 fail-closed · 이미 마스킹된 행 건너뜀 |
| **P-5** | **보존 정책 값**(90일 · 일괄 크기) — 정책 데이터 한 자리(버전 포함), 러너가 읽는 값이 wire(실제 DELETE 술어)에 닿는지 test | 값을 89/91 로 바꾸면 경계 test 가 RED(값→거동) |
| **P-6** | **runbook**(보존 절차 · cron 줄 · 멈춤 조건 · 운영 DB 실행은 별도 승인) + checklist · OPEN 처분 | — |

## 위협 모델 경계 (설계 검토 (0) 요약 — 정본은 설계 검토 노트)

**방어하는 것**: 90일이 지난 행이 실제로 사라지거나(파기 대상) PII 가 사라진다(마스킹 대상) · 90일 안쪽 행과 제외 표는 건드리지 않는다 · 앱 런타임(풀)은 여전히 아무것도 지우거나 고칠 수 없다 · 마스킹은 한 방향이며 되돌릴 수 없다 · 새 표가 분류 없이 들어올 수 없다. **방어하지 않는 것**: 소유자 자격을 가진 주체의 임의 SQL(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`) · 백업 산출물 속 과거 행(6B-2 백업의 보존은 M7) · 수집 시점 마스킹(원문은 90일 동안 PII 를 가진다 — 결정 문면 그대로) · 운영 DB 에서의 실행(별도 승인).

## in_scope / out_scope

- in: `adapters/src/main/resources/db/migration/V18__*.sql` · `adapters/src/main/resources/db/migration/V19__*.sql`(D-6B3-3 ⑥ — 인덱스 `CONCURRENTLY` 분리) · `adapters/src/main/kotlin/**`(보존 저장소 어댑터·마스킹) · `adapters/src/test/**` · `workflow/src/main/kotlin/**`·`workflow/src/test/**`(보존 use case·정책 값 — 필요 시) · `app/src/main/kotlin/**`(러너·배선) · `app/src/test/**` · `config/quality/gate-tests.properties` · `config/quality/architecture-policy.properties`(D-6B3-2 확정) · `docs/runbook/**`(보존 절) · `reports/evidence/m6/6b3/**` · `milestone-6.md`(팀장 문단).
- out: 앱 역할 GRANT 확대 · 수집 시점 마스킹 · `inbox`·싱글턴·개정 이력·수집 원장 파기 · 운영/dev DB(`bid-vector-v2-dev` 55432) 실행 · pg_cron·파티셔닝 · 백업 보존 · `docker/**`·`ci.yml`(필요해지면 멈추고 계약 갱신).

## acceptance

CI `check` job 그대로: `./gradlew --no-daemon check` → `./gradlew --no-daemon qualityBaseline`. `container` job 은 compose 를 안 만지면 생략 사유 등재(필요하면 계약 갱신). `ml-engine` job 은 무변경이라 생략. 호스트 규율(빌드 전 pgrep·free·ps 별도 호출 · 직렬). Testcontainers 만 — dev DB(55432)에 닿지 않음을 단언.

## rollback

in_scope 경로 한정 `git restore --source=e2232a75 --staged --worktree --` + 공유 파일은 병합된 main SHA 에서 복원(PR #69 규칙). **V18 이 적용된 DB 의 되돌림은 forward-fix**(새 V 로 트리거 원복 · 열 DROP) — 파기된 행·마스킹된 값은 백업 복원으로만 돌아온다(6B-2 runbook). 임시 clone 에서 ①~⑥.

## 계약 갱신 이력

**D-6B3-1 (r1, 2026-10-13, 팀장 — 구현 레인 preflight 수령).** ① **V18 의 트리거 완화는 `raw_observation` 한 표만**(`pii_masked_at` 도 그 표에만). 실측: `notice_audit.previous_row = to_jsonb(OLD notice)` 이고 `notice` 열에는 담당자 열이 없으며 담당자 키는 계약 레지스트리에 등재되지 않는다(`FieldContractTest` 기존 단언) — 담당자 PII 가 `notice_audit` 로 들어오는 경로가 구조적으로 없다. 완화하면 얻는 것 0 · append-only 계약의 구멍 하나. 대신 「`notice_audit` 마스킹 대상 0」을 **술어로 잠근다**: `previous_row` 키 집합 ⊆ `notice` 열 집합(DB 카탈로그 실측) · `notice` 열 이름에 담당자 꼴 없음 · 레지스트리 단언 참조. A-3 의 취지(PII 제거 · 표본 보존)는 그대로다. ② **`edit_session` 은 상태 무관 `created_at < cutoff`**(버려진 비종단 행은 그 부분집합 — A-4 포함, 세션 수명은 분·시간 단위이고 감사는 `operator_strategy_revision` 이 진다). ③ outbox 는 종단(`DELIVERED`·`ISOLATED`)만 · 기준 시각 매핑은 적재 시각 축(레인 preflight 표). ④ 분류 레지스트리 ↔ DB 카탈로그 집합 등식(`CleanMigrationTest` 축1 로 전이적으로 스키마 정본에 묶임) · FK 순서는 pg 카탈로그 런타임 위상 정렬(순환이면 실패) · `payload` 마스킹 allow-list = 계약 레지스트리 rawNames — **단 그 allow-list 가 `payload` 를 읽는 모든 소비자(스냅숏 추출·재정규화·6G 백테스트 경로)의 읽기 키 집합을 포함함을 기계 수집으로 실측**(마스킹이 PII 아닌 필드를 지워 표본을 깨지 않게).

**D-6B3-2 (r2, 2026-10-14, 팀장 — 구현 완료 수령 · 동결).** 산출물 판정 SHA `575674d2` · evidence 끝 `811925fb`. `config/quality/architecture-policy.properties` 를 in_scope 로 **확정**(tier·러너 참조자·로깅 사용자 등재 — 없으면 `ArchitectureGateTest` RED). acceptance(레인): check exit 0 · test 2,911 · qualityBaseline `--rerun-tasks` exit 0. 변이 17 중 15 KILLED · 2 SURVIVED(마스킹 건너뜀 이중 가드 — 쌍 변이는 RED, 알려진 제한 4). 면제 래칫 46→51. 새 OPEN 셋(`OPEN-6B3-OUTBOX-FAILED-RETENTION` · `-BLOAT-AFTER-FIRST-PURGE` · `-PARTIAL-COUNTS-ON-FAILURE`). TDD 순서 이탈(workflow 층) 자기신고. 검토 레인: verifier(opus) · code-reviewer(sonnet) · migration-reviewer · privacy-gate 병렬 → Codex 1회.

**D-6B3-3 (r3, 2026-10-14, 팀장 — 검토 r1 수령 · 운영자 결정 둘 · 수정 라운드 1, 재작업 1/5).** 검토 넷: verifier r1 not-ready(F-1 운반 미잠금 · F-2 `notice_audit` 술어 미구현) · code-review r1 high 1 · migration-review request_changes(H-1) · privacy fail(F-1 high). 판정 SHA `575674d2`(그 뒤 `1dfd0094` test · `5c622296` fixture 는 레인이 판정 뒤 커밋 — 동결 위반으로 등재, 수정 라운드에 흡수). ① **업체명은 마스킹 대상이다(운영자 결정 2026-10-14, privacy F-1 조치 1)** — `BID_COMPANY_NAME`·`AWARD_COMPANY_NAME` 개념의 rawName 을 남김 집합에서 개념 축으로 뺀다; `payload_fields` 도 마스킹 열로 둔다(V18 관문 ④의 「마스킹 열」 = `payload`·`payload_fields` 둘, 그 밖 열 불변 유지 — V18 은 미배포라 V18 자체를 고친다). 먼저 6G 백테스트·스냅숏 추출이 그 키를 읽는지 기계 수집으로 실측하고, 읽으면 「90일 안쪽만 읽는다」를 보이거나 멈추고 보고. D-6B3-1 ④ 의 소비자 포함 술어는 「업체명 제외 남김 집합」 기준으로 다시 잰다. `OPEN-6B3-RAW-OBSERVATION-RETENTION` 처분은 「마스킹으로 닫힘(업체명 포함)」. ② **CASCADE 자식에도 기한 가드(운영자 결정 2026-10-14, vr F-3)** — 기한 안쪽 자식이 있으면 부모를 남긴다(비-CASCADE 자식과 같은 성질), 헤더 120일 전 생성·오늘 재저장 경로를 test 로. ③ **D-6B3-1 ③ 정정 — outbox 파기는 `DELIVERED` 만**(code-review H-1: `ISOLATED` 는 미발송, A-4 문면이 제외; 팀장이 r1 에서 잘못 받음). ④ D-6B3-1 ① 술어 셋을 test 로 구현(vr F-2 · privacy F-2). ⑤ 트리거·권한은 구조로: 관문 ② 는 역할 이름이 아니라 실행 역할 = 표 소유자(`pg_class.relowner`) allow-list(vr F-5 · mig L-1) · `pii_masked_at` 은 INSERT 에서 NULL 강제(vr F-4) · 비객체 `payload_fields` 처분(vr F-9). ⑥ **V18 인덱스는 V19 로 분리, `CREATE INDEX CONCURRENTLY` + 그 마이그레이션만 `executeInTransaction=false`**(mig H-1) — 파기 cutoff 지원 인덱스(mig M-2)도 V19, 인덱스 장부 등재(mig L-2), forward-fix 문면에 함수 정리·표지 소실(mig M-1). ⑦ 정책 값 타입 벽: 출하 배선이 읽는 값이 러너에 닿는가(vr F-1 운반 — V-1 RED) · `cutoffFrom`·정책 생성은 최소 보존 이상만(vr F-6 · cr M-2). ⑧ 마스킹 판독 깊이 = 수집 상한(cr M-1) · `notice_audit` 보존 축 사유 등재(cr M-3) · runbook 자격 값을 argv 로 넘기지 않는다(privacy F-3) · 첫 실행 전 세어 보기 절차(privacy F-5). low·장부층은 같은 라운드 일괄.

**D-6B3-4 (r4, 2026-10-14, 팀장 — 수정 라운드 1 수령 · 동결).** 산출물 판정 SHA **`b252fa0c`** · evidence 끝 `fac7a0a6`. 이탈 둘 수용: ① **D-6B3-3 ⑥ 의 `CONCURRENTLY` 는 실측으로 기각** — `executeInTransaction=false` 에서 Flyway 메타데이터 연결이 `idle in transaction` 으로 남아 `CONCURRENTLY` 가 영구 대기(Testcontainers 14분 무진행). `postgresql.transactional.lock=false` 는 Flyway 호출 자리 넷에 다 걸어야 하고 하나만 빠져도 실패가 아니라 **멈춤**이라 택하지 않는다. 대신 V18 = 메타데이터만(밀리초) · V19 = 인덱스(`SHARE` — 읽기 통과, 쓰기 차단 창 남음) 분리, 적용 전 크기 측정 절차(runbook) · `OPEN-6B3-INDEX-WINDOW`. 형제 `.sql.conf` 없음. ② cr L-2(표지만 찍는 UPDATE)는 막지 않고 비방어로 측정 등재 — 막으면 대체값이 원문과 같을 때 실행이 영구 실패. 6G 소비자 실측(① 전제): 저장 행에서 업체명을 읽는 소비자 0(스냅숏 추출 개념 35 중 업체명 0 · ml-engine 참조 0 · `prcbdrNm` 은 수집 시점 HTTP 응답 판별자로만). 레인 acceptance: check `--rerun-tasks` exit 0(2,948 test · fail 0 · skipped 4) · 변이 16 KILLED 0 SURVIVED(V-1 은 컴파일에서 죽음). 새 파일 다섯 모두 in_scope. 검토 r2: verifier(opus) · code-reviewer · migration · privacy 병렬 → Codex 1회.

**D-6B3-5 (r5, 2026-10-14, 팀장 — 검토 r2 수령 · 수정 라운드 2, 재작업 2/5).** 검토 r2: verifier not-ready(R2-F1 high) · privacy fail(F-6 high = 같은 결함) · code-review APPROVE(medium 2) · migration approve(medium 2). 팀장 결정(운영자 결정 「업체명 마스킹」의 범위 안 — 새 운영자 결정 없음): ① **업체 식별 개념 집합을 한 자리에 선언**하고 `OPENING_COMPANY_INFO`(`opengCorpInfo`)를 포함, 마스킹 집합 == 그 집합 · **`FieldConcept` 전수 ↔ PII 분류(업체 식별 / 비PII) 분할 등식**으로 새 개념이 분류 없이 들어올 수 없게(privacy F-7) — 키 전체를 지운다. ② **남길 키는 행이 아니라 계약 레지스트리에서** — 수집 계약 전 개정(`KONEPS_COLLECTION_POLICY.entries`)의 합집합에서 업체 식별 개념을 뺀 rawName 집합(D-6B3-1 ④ 문면 복귀 · vr R2-F2 · cr M-2), `referenceDate` 의존 제거. ③ **`inserted_at` 은 DB 시계가 소유** — 앱 역할이 미래 시각으로 마스킹 대상에서 빠질 수 없게(vr R2-F2), 수집 경로가 그 열을 쓰는지 먼저 실측. ④ `maskAll` 은 진척 0 이면 끝나고 `skipped > 0` 은 `INCOMPLETE`(cr M-1) · ⑤ V18 첫 문장 `SET LOCAL lock_timeout`, runbook 적용 창은 V19 한 트랜잭션 = 열 표 동시 쓰기 차단 · 적용 전 비객체 `payload_fields`·긴 트랜잭션 점검(mig M-1 · vr R2-F4) · `convalidated` 단언(mig M-2) · ⑥ low·장부층 일괄(vr R2-F3·F5~F8 · cr L-1~L-8 · mig L-1~L-3 · privacy F-8·F-9).

**D-6B3-6 (r6, 2026-10-14, 팀장 — 수정 라운드 2 수령 · 동결).** 산출물 판정 SHA **`bc18d033`** · evidence `b28dc012`. RED 먼저 커밋(`2d9996e3`, 실패 14 · 공허 대조 초록). ⑤ `lock_timeout` 은 문자열 술어가 아니라 **거동 test**(별 컨테이너에서 경합 잠금 → V18 이 `55P03` 으로 빨리 실패 · 재시도 성공; 변이 RED 41.9s). `inserted_at` 은 비소유자 INSERT 를 DB 시계로 덮어씀(수집 경로가 그 열을 안 씀 — 실측). 남길 키 = 수집 계약 전 개정 합집합(저장소 생성자가 날짜를 받지 않음 — 오늘은 개정 하나라 관측 차이 없음, 구조로 닫음). 새 파일 일곱 모두 in_scope. 레인 acceptance: check `--rerun-tasks` exit 0(2,973 test · fail 0 · skipped 4) · qualityBaseline 0 · 변이 13 중 12 KILLED + 대조 1 · rollback 48 경로 재실측. 재작업 2/5. 표적 재검증: verifier · privacy(r2 fail 축) · migration(V18 변경) · code-review(r2 medium 둘) 병렬.

**D-6B3-7 (r7, 2026-10-14, 팀장 — 검토 r3 · 승인 전 일괄 · r3b 수령 · PR 단계).** 검토 r3: verifier ready-for-review · privacy pass-with-notes · migration approve · code-review APPROVE. 승인 전 일괄의 술어 변경 둘 — (A) `a6559df0` 투영 열 값도 스칼라만(원문과 대칭) · (B) `dd059a3a` V19 `SET LOCAL lock_timeout` + 거동 test(`MigrationLockTimeoutTest`) — 표적 r3b: verifier ready-for-review · migration approve(실 Flyway · 149만 행). 장부: `OPEN-6B3-FREE-TEXT-PII-AXIS`(자유 텍스트 개념의 비PII 분류는 가정 — **운영 첫 실행 전 실표본 판독**) · 값 축(허용 키의 스칼라 값 속 상호)은 비방어 등재. r3b 문면: V19 주석만 `057e6f95`(비주석 줄 0 — 팀장 확인) · runbook·evidence `f92d4b55`. **최종 산출물 SHA `f92d4b55`** · evidence `5d0f9a75`(rollback 재실측 @`f92d4b55` — r3b 가 되돌림 대상 둘을 움직여 재측정). evidence 사고 1건(라운드 2 편집이 commands.md 라운드 1 표를 지움 → `fac7a0a6` 에서 복원, 사실 등재). 재작업 2/5. 다음: PR → Codex 1회(V18·V19 마이그레이션 · 파기·마스킹 — 되돌리기 어려운 경로, 운영자 승인 A-5) → `/code-review` → CI → 머지(사용자 사전 승인).

**D-6B3-8 (r8, 2026-10-14, 팀장 — Codex approve · `/code-review` 조치 · main 병합 둘 · 머지 단계).** Codex 1회 **approve · 지적 0**(codex-cli 0.162.1 · gpt-5.5 · effort high, 판정 `codex-review-20261014T082123Z.json`). `/code-review`(PR #73) 80+ 0건이나 실측 항목 조치: P7 FK 간선 동일성 = `pg_constraint.oid` `12e38a19` · P6 부분 계수를 실패 줄에(원인 SQLSTATE 보존, `OPEN-6B3-PARTIAL-COUNTS-ON-FAILURE` 닫힘) `0124b874` · 문면 `47e49ac6`(권한 사실 · 적용 DB rollback 갈래 · 운영 runbook outbox) · r3c 문면 `0cf252c6`(풀 역할 실행 = 계획 단계 거부 0행 · 마스킹 부분 계수 하한). 표적 r3c ready-for-review @`9b4625c7`. 등재: `OPEN-6B3-PURGE-LOCK-TIMEOUT` · 자식 0 헤더 파기(알려진 제한). main 병합 둘 — `8251113c`(ae9b2f52) · `3f4512e9`(4f2b45b3, 6B-4 머지 뒤) 충돌 0 · 6B-4 경로 ∩ 복원 목록 0 · 복원 원천 고정 `4f2b45b3`. **판정 SHA `3f4512e9`** · evidence `1d6620a7` — check `--rerun-tasks` · qualityBaseline · S-20 · container job 전부 exit 0. 재작업 2/5. 하네스 레인 변경: `git log e2232a75..HEAD -- CLAUDE.md .claude/` 중 이 slice 커밋 없음(병합으로 들어온 PR #72 커밋만).

## 하네스 레인 변경 (상시)

- 없음 — 이 slice 커밋은 `CLAUDE.md`·`.claude/` 를 만지지 않았다(2026-10-14 리뷰 요청 시점 재측정; 병합으로 들어온 main 의 하네스 커밋 PR #72 는 이 slice 의 것이 아니다).
