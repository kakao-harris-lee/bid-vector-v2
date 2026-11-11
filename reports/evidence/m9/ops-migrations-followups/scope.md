# M9 / ops-migrations-followups — PR #141 비차단 지적의 후속 slice

- milestone: M9
- slice: ops-migrations-followups
- base_sha: `6e6f953cf0e3d1ca36c1ccac896c1e57297bbecf` (main, PR #138 머지)
- head_sha: <리뷰 요청 시점에 기입>
- 유래: PR #141 「팀장 처분」 코멘트의 **「후속 PR 로 넘기는 것」 전부**. 그 PR 은 승인 상한 너머 마지막
  라운드를 썼으므로 비차단 지적(cr M-2·M-4·L-1~L-5 · v M-1·M-2)을 라운드가 아니라 이 slice 로 분리했다
  (워크플로 5(b)). 원 slice 의 evidence 는 `reports/evidence/m9/ops-migrations/`.

## 대상 — 지적 ↔ 처방

| 지적 | 처방 |
| --- | --- |
| **cr M-2**(운영 반영 전 필수) | 낙찰 수집 runbook 의 「기본값이 없는 값」 열거와 실행 블록에 릴리스 식별자 키를 더한다. 6E runbook 의 `bidvector.release` 행이 「앞 판의 키 **둘**을 대체」로 적혀 있는데 award 갈래의 옛 키까지 셋이다 — **코드가 요구하는 키 집합과 대조해** 사실로 맞춘다 |
| **v M-2** | `OutboxTransitionSqlTest` 의 쌍 집합 등식을 **제약 본문의 정규식 토큰**이 아니라 **전수 UPDATE 시도 + savepoint 되돌림**으로 받아들여진 집합을 세는 거동 test 로 바꿔치운다. 구판이 놓친 결함(토큰을 늘리지 않는 확장)을 새 판이 잡는지 변이로 실측 |
| **v M-1 · cr L-5** | 고정점 test 의 모집단을 복원 지문이 비교하는 집합과 맞춘다 — CHECK(`contype='c'`) 외에 EXCLUDE(`'x'`)의 WHERE, 트리거 정의(`WHEN` 포함)까지. 종류마다 **접힐 식을 심어 잡히는지**를 상설 양성 대조로 둔다(오늘 스키마에 그 둘이 0 이라 「넓혔지만 아무것도 재지 않는다」가 되는 자리를 막는다) |
| **cr M-4** | 원 slice 라운드 10 의 계약 갱신 사실(병합 넷 · V26 재편집 사유 · 면제 KDoc 재작성)을 이 slice evidence 에 기록 — 아래 「원 slice 라운드 10 계약 갱신」 절 |
| **cr L-1** | 면제 KDoc 재작성이 지운 레인 `7C⑤-b` 의 면제 근거를 근거 목록에 복원(게이트 거동·상수 값 불변) |
| **cr L-2** | `tools/db-rehearsal.sh` 머리말과 그 안의 Flyway 호출자 주석이 가리키는 출하 함수 이름·「전부 기본값」이 낡았다 — 사실로 맞추고 **남는 차이 하나를 명시** |
| **cr L-3** | 고정점 test 의 탐침을 savepoint 로 감싼다 — 첫 예외가 트랜잭션을 실패 상태로 만들어 뒤 제약의 진단을 잃는 자리 |
| **cr L-4** | 고정점 test 의 실패 목록에 **기대·관측 렌더링**을 함께 싣는다 |

## in_scope
- `docs/runbook/m9-award-collection.md` · `docs/runbook/m6-6e-operations.md`
- `adapters/src/test/kotlin/bidvector/adapters/event/OutboxTransitionSqlTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CheckConstraintRenderingFixpointTest.kt`
- `app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt` (KDoc 근거 목록만)
- `tools/db-rehearsal.sh` (머리말·호출자 주석 문면만)
- `reports/evidence/m9/ops-migrations-followups/**`

## out_of_scope
- **Flyway 마이그레이션 파일(`V*.sql`) 무편집** — 팀장 지시. 적용된 DB 의 체크섬이 움직이고, V26 은 그
  자신이 `CheckConstraintRenderingFixpointTest` 를 이름으로 가리키므로 test 클래스 이름도 바꾸지 않는다.
- production Kotlin 코드와 거동 — 이 slice 는 test·문서·셸 주석만 만진다.
- `EXPECTED_EXEMPT_COUNT` 값과 면제 목록(`config/quality/architecture-policy.properties`) — cr L-1 은
  근거 문면만이고 게이트 거동은 불변이다.
- `tools/db-rehearsal.sh` 의 **거동**(Flyway 설정 인자) — 아래 「알려진 제한」 ①.
- 새 test 클래스 0 → `config/quality/gate-tests.properties` 편집 0(기존 클래스에 메서드만 더한다).

## acceptance
CI `.github/workflows/ci.yml` 의 Kotlin job 명령 그대로:
- `./gradlew --no-daemon check`
- `./gradlew --no-daemon qualityBaseline`

## rollback
`rollback.md` — in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 공유 파일은
base(= 병합된 main 커밋 `6e6f953c`)에서 복원한다. 전제: 그 SHA 에 이 slice 의 커밋이 없다(base 가 곧
착수 전 main 이다).

## 하네스 레인 변경
`git log --oneline 6e6f953c..HEAD -- CLAUDE.md .claude/` — **없음**. (리뷰 요청 시점에 재산출한다.)

## 원 slice 라운드 10 계약 갱신 (cr M-4)

PR #141 의 라운드 10 은 `scope.md` 에 등재되지 않은 채 아래 넷을 했다. 이 절이 그 기록이다 —
원 slice 의 scope.md 는 되쓰지 않는다(이력을 되쓰지 않는 규율).

1. **병합 넷.** 그 브랜치가 만든 main 병합은 넷이다 — `56bd33ea`(paper-bid V25) ·
   `3101035f`(CVE · STR12 telegram · pitest) · `70406318`(award-ingest) · `07140729`(ml-lifecycle,
   **마지막**). 넷을 등재한 자리는 `rollback.md` 뿐이고 **scope.md 는 라운드 10 을 한 줄도 적지
   않았다** — 그 어긋남이 cr M-4 다. 되돌림 전제인 「이 브랜치가 마지막으로 합친 main」은 넷째이고,
   원 slice `rollback.md` 의 복원 원천 SHA 가 그 값이다. 병합 수가 중요한 이유는 둘 — 복원 원천이
   라운드 도중 두 번 옮겨 **복원 목록을 재산출**해야 했고(그 갈래 수가 98 → 93 으로 줄었다),
   「남의 변경 보존」 계수가 넷이 들여온 축 전부를 세야 했다.
2. **V26 재편집.** 사용자 2026-11-07 처방은 「SQL 무편집」이었다. 라운드 10 이 그 처방에서 이탈해
   V26 의 CHECK 본문을 **행 값 비교에서 평평한 OR 사슬로** 바꿨고, 사유는 새 CI `container` job 의
   복원 등식 실패다(행 값 비교는 자기 렌더링의 고정점이 아니다 — 파서가 왼쪽 깊은 OR 로 저장하고
   덤프 텍스트를 다시 읽을 때 평평하게 접는다). 그 이탈이 허용된 근거는 「적용된 DB 가 아직 없다」
   (V26·V27 은 origin/main 에 없어 체크섬 재편집이 깨는 환경이 없다)이고, 재검증 범위는 그 편집이
   닿는 축 전부 — CHECK 의 진리표 보존 · 전이 SQL · 복원 등식 · 고정점 test 신설이었다.
3. **면제 KDoc 재작성.** `AppHttpDependencyGateTest` 가 병합 합집합으로 519 줄이 되어 파일 500 줄
   한도를 넘겼고, 「병합 재계산 N째」 이력 문단 여덟(그중 한 쌍은 앞 병합이 접어 만든 중복)을 걷어
   452 줄로 줄였다. 게이트 거동과 면제 집합은 불변이다. **그 재작성이 레인 `7C⑤-b` 의 근거 한 줄을
   함께 잃은 것**이 cr L-1 이고 이 slice 가 복원한다.
4. **진리표 수의 정본.** `4cda0103` 커밋 메시지의 「63 조합 · 수용 21/21」은 고치기 전 하네스의 수다.
   정본은 원 slice `commands.md` 의 수(`state NOT NULL` 모집단)이며, verifier 독립 진리표(NULL·어휘
   밖 포함)도 같은 답을 냈다. 이력은 되쓰지 않는다 — PR #141 의 팀장 처분 코멘트가 그 선언 자리다.

## 알려진 제한

① **리허설과 출하 Flyway 설정의 차이 하나는 남긴다.** 출하 경로는 migration 연결에 잠금 상한을
   `initSql` 로 깔지만 리허설 호출자는 그것을 주지 않는다. 그 값은 **동시 잠금 대기의 상한**이고
   리허설은 자기 컨테이너 안에서 배타적으로 도므로 깎을 대기가 없다 — 거동 차이 0 이다. 셸 스크립트의
   그 인자를 고치는 것은 CI `container` job 에서만 실측되는 변경이라 이 slice 에서 **문면을 사실로
   맞추는 쪽**을 골랐다(거동 무변경). 받는 자리: `OPEN-M9-REHEARSAL-FLYWAY-CONFIG-PARITY`.

② **EXCLUDE·트리거 `WHEN` 은 오늘 스키마에 0 이다.** 고정점 test 의 모집단을 넓힌 것은 「새 종류가
   들어오는 순간 함께 잰다」는 구조적 폐쇄이고, **오늘 그 종류를 재는 것은 상설 양성 대조**(심은
   접힐 식)다. 실 스키마 쪽 계수는 0 이며 그 사실 자체가 `CleanMigrationCheckTest` 의 표별 셈 소관이다.
