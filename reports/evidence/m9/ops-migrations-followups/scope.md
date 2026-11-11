# M9 / ops-migrations-followups — PR #141 비차단 지적의 후속 slice

- milestone: M9
- slice: ops-migrations-followups
- base_sha: `6e6f953cf0e3d1ca36c1ccac896c1e57297bbecf` (main, PR #138 머지)
- head_sha: 라운드 1 의 리뷰 대상은 `cd1afcd8`(PR #145 · verifier ready-for-review · code-reviewer
  approve). **이 자리에 라운드마다 값을 새로 적지 않는다** — 그 시점의 정본은 PR 의 `headRefOid` 와
  판정 코멘트의 SHA 이고, 여기 적으면 라운드마다 같은 줄만 고치는 커밋이 하나씩 붙는다
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
`git log --oneline 6e6f953c..HEAD -- CLAUDE.md .claude/` — **없음**(0 줄). 라운드 1 판정 SHA
`cd1afcd8` 와 수정 라운드 1 둘 다에서 재산출해 같았다. **이 브랜치에는 하네스 레인 커밋이 하나도
없다** — slice 산출물이 아닌 커밋이 range 에 섞이지 않았다는 뜻이고, 라운드가 더 붙어도 그 명령이
정본이다.

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

## 계약 갱신 2026-11-11 — cr M-2 의 범위 (구현 레인)

cr M-2 는 두 자리(낙찰 runbook §1·§3 · 6E runbook 의 `bidvector.release` 행)를 지목했고 팀장 지시는
「**실제 코드가 요구하는 키 집합과 대조해** 사실로 맞춰라」였다. 대조해 보니 **같은 결함이 그 행
하나가 아니었다**:

- 낙찰 runbook **§4**(승격 블록)도 릴리스 식별자 없이 적혀 있어 그대로 돌리면 같은 이유로 기동
  실패다 — 같이 고쳤다(그 블록을 쓰는 사람에게 §3 의 수정만으로는 아무 도움이 안 된다).
- 6E runbook 의 **키 census 셋이 코드보다 밀려 있었다.** 낙찰 runbook §1 이 「늘 필요한 나머지의
  정본」으로 그 자리를 가리키게 했으므로, 밀린 census 를 그대로 두면 **포인터가 거짓을 가리킨다**.
  기계로 다시 뽑아 채웠다 — §1.2 prefix 10 → **17**(릴리스·접속 쿠키·알림 채널·낙찰 둘·승격·계정) ·
  §1.3 러너 스위치 여섯 → **아홉 + 어휘가 다른 하나** · §1.4 leaf 표에 같은 prefix 들.
  **`bidvector.release` 자신이 §1.2 「항상 필수」 표에 없었다** — 그 한 줄은 지적의 안쪽이다.

문서만 만지고 코드·게이트 거동은 건드리지 않았다. 이 census 를 지키는 술어는 여전히 없으며 받는
자리는 `OPEN-M9-REQUIRED-KEY-ENTRYPOINT-CENSUS` 다(capability map 에 이미 있는 항목 — 새로 세우지
않았다).

## 계약 갱신 2026-11-11 — 수정 라운드 1 에서 바로잡은 전제 하나

**cr r11 M-2 의 처방 사유 일부가 코드와 달랐다.** 그 지적은 §4 승격 블록의 「§3 과 같은 값」 문면을
들어 「베낀 값이 canonical `opening_result` 행에 영구히 남는다」를 사유로 들었다. **지적 자체는
맞지만 그 사유는 성립하지 않는다**:

- V27 은 릴리스 칸을 **표 둘**(수집 회계·outbox)에만 더했고 canonical 결과 표에는 그 칸이 **없다**.
- 승격 배선은 릴리스 식별자 빈을 **주입받지 않는다** — 승격 run 은 그 값을 어디에도 쓰지 않는다.

그래서 문면은 고치되(값은 **그 호출을 실행하는 jar** 의 빌드 SHA 다) 사유는 사실로 바꿨다 — §4 에는
「오늘은 기록되지 않지만 그 무해함은 우연이므로 규칙이 그것에 기대지 않는다」, write-once 오염이
**실제로 무는 자리인 §3**(원문 행 + 수집 회계 행, 그 칸은 한 번만 적힌다)에는 조각 사이 값 베끼기
금지를 적었다. 리뷰 지적을 확인 없이 문면으로 옮기면 **문서가 거짓을 주장**하게 되는 자리였다.

**cr r11 M-1 은 양쪽을 다 고쳤다** — §1.5 를 코드와 대조해 채우고(네 행) 포인터도 그 절 자신의
「거의 전부」에 맞춰 좁혔다. 둘 중 하나만 했다면 포인터와 표가 다시 갈린다.

**cr r11 L-7 · L-6 · v r11 L-1 은 코드를 바꾸지 않았다** — L-7 은 어긋나면 탐침이 실패해 소견으로
나오는 fail-closed 이고, L-6·L-1 은 후보 격자가 열거라는 **구조적 한계**여서 문면으로만 닫힌다.

## 알려진 제한

① **리허설과 출하 Flyway 설정의 차이 하나는 남긴다.** 출하 경로는 migration 연결에 잠금 상한을
   `initSql` 로 깔지만 리허설 호출자는 그것을 주지 않는다. 그 값은 **동시 잠금 대기의 상한**이고
   리허설은 자기 컨테이너 안에서 배타적으로 도므로 깎을 대기가 없다 — 거동 차이 0 이다. 셸 스크립트의
   그 인자를 고치는 것은 CI `container` job 에서만 실측되는 변경이라 이 slice 에서 **문면을 사실로
   맞추는 쪽**을 골랐다(거동 무변경). 그 축을 재는 test 는 이미 있다
   (`MigrationLockBoundTest`·`MigrationLockTimeoutTest`). **OPEN id 를 새로 세우지 않았다** —
   등재 자리는 capability map 이고 그 파일은 이 slice 의 in_scope 밖이다. 뒤에 올릴 후보 이름은
   `OPEN-M9-REHEARSAL-FLYWAY-CONFIG-PARITY` 이며, 지금 그 이름을 가리키는 등재 행은 **없다**
   (커밋 `4af8c96b` 의 메시지가 「등재」로 적은 것은 이 절을 가리킨 말이다).

② **다른 slice 의 evidence 가 쥔 좌표 하나가 밀렸다**(역방향 파급). `tools/db-rehearsal.sh` 머리말에
   열두 줄이 들어가 그 뒤 줄 번호가 그만큼 내려갔고, **닫힌 slice 의 evidence 한 줄**이 그 아래를
   `파일:줄` 로 가리킨다 — `reports/evidence/m7/7c1b/checklist.md` 의 「리허설 도구의 `truncate`」 행
   (가리키던 `truncate` 문은 그 수만큼 아래로 옮겨갔고, 옛 번호 자리는 이제 빈 줄이다). **고치지
   않는다**: 닫힌 slice 의 evidence 는 되쓰지 않고, 그 파일은 이 slice 의 in_scope 밖이다. 같은 축을
   가리키는 다른 한 줄(`m7/7f2/checklist.md` 의 머리말 범위)은 **밀리지 않았다** — 삽입 지점이 그
   범위 끝보다 아래임을 양쪽 트리에서 재서 확인했다. 이 종류가 반복되는 이유와 처방(좌표 대신
   인용문·절 제목)은 evidence-pack 규격이 든다.

③ **EXCLUDE·트리거 `WHEN` 은 오늘 스키마에 0 이다.** 고정점 test 의 모집단을 넓힌 것은 「새 종류가
   들어오는 순간 함께 잰다」는 구조적 폐쇄이고, **오늘 그 종류를 재는 것은 상설 양성 대조**(심은
   접힐 식)다. 실 스키마 쪽 계수는 0 이며 그 사실 자체가 `CleanMigrationCheckTest` 의 표별 셈 소관이다.
