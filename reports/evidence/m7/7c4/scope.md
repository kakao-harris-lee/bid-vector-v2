# M7/7C④ — 설정 API: 운영자 프로필 읽기·쓰기 + 시작하기 체크리스트 (착수 계약, 2026-10-22, 팀장)

- **근거**: `milestone-7.md` 7C ④(「설정(`operator_profile` + 전략 편집 세션 재사용)」). 운영자 지시 2026-10-22 「7C④ 도 레인 열어서
  병렬로 진행」. 7C① 과 같은 종류의 `milestone-7.md` 선행 조건(M6 완료 조건 성립) **운영자 예외**다.
- **base**: main `fc15975c` · worktree `bid-vector-v2-m7-7c4` · 브랜치 `m7-7c4/2026-10-22`.
  **2026-10-24 재고정(R-8)**: 착수 base 는 `bee7c36f` 였고 그 뒤 main 이 PR #81~#87 로 앞섰다. 레인 브랜치에 `fc15975c` 를 병합하고
  (충돌 0) base 와 rollback 복원 기준을 그 SHA 로 다시 고정했다. 앞 base 의 rollback 실측은 무효다 — 새 base 에서 다시 잰다.
  마이그레이션 번호는 **V22 유효**(병합된 main 최대 V20, 7B 가 V21 보유).

## 하네스 레인 변경

**없음.** 이 slice 는 `CLAUDE.md` 와 `.claude/` 에 커밋이 0 이다(`git log --oneline fc15975c..HEAD -- CLAUDE.md .claude/` 빈 출력).
- **병렬 레인**(전부 push 0): 7B(`bid-vector-v2-m7-7b`, **V21 보유**) · g1c03 · g1dec05 · g1set01. 무거운 빌드는 기존 flock 직렬에 줄을 선다.
- **입력 정본**:
  - `docs/discovery/m7-screen-data-requirements.md` — 화면 A(시작하기) 행 27~37, 「설정」 절(:115~), 담당 칸이 `7C ④` 인 행 전부.
  - `adapters/.../db/migration/V12__operator_profile.sql` · `adapters/.../profile/*` · `workflow/.../evaluation/ProfilePorts.kt`(싱글턴 프로필 · 면허 세 상태).
  - `openapi/bidvector-operator-api.yaml` 의 `/api/strategy` · `/api/strategy/edit-sessions*`(전략 편집 세션 — **재사용**, 새로 만들지 않는다).
  - 7B 판정 문서 `bid-vector-v2-m7-7b/reports/evidence/m7/7b/checklist.md` §9(R-2 남은 절반 = 별 slice 권고) · 7B 의 인증 축 결정
    (계정 범위 경로는 세션만, `AccountScopedRoutes`).
  - 추적표·capability map 의 QUAL-01 · QUAL-08 · QUAL-11 · SET-01 · STR-01 · STR-03 · STR-06 행.

## 산출물

1. **프로필 읽기·쓰기 API**: 화면 A·설정이 쓰는 필드 — 업종 · 면허(세 상태 보존: 미설정 / 미신고 / 신고(빈 목록 포함)) · 지역 · 상호 ·
   실적·시평액 · 나라장터 준비(등록 여부·등록분야·인증서 만료일). OpenAPI 수작성 단일 출처에 더하고 생성 타입을 재생성한다(W-5 바이트 등식).
2. **시작하기 체크리스트 진행 상태**: 항목별 완료 판정은 **서버가** 한다(클라이언트 추론 금지 — 요구표 :37). 항목 정의는 선언적 데이터로 둔다.
3. **전략 설정**은 기존 `readStrategy` + 편집 세션 여섯을 그대로 쓴다. 이 slice 는 화면이 그것으로 충분한지 대조해 **부족분만** 보고한다
   (STR-06 은 「신설 필요」 — 범위에 넣을지 Phase 1 에서 판정·보고).
4. **판정 표시 정직성**: QUAL-08(지역) · QUAL-11(실적·시평액)은 **입력만** 선다. 판정 자체는 미구현이므로 응답·추적표에서 「아직 판정하지
   않음」으로 드러나야 하고, 입력이 생겼다고 자격 판정이 그 값을 쓰는 것처럼 보이면 안 된다(M7 완료 조건 G2 문면). test 로 잠근다.
5. **SET-01 상호**: 7C⑤(결과 조회)의 선행이다. g1set01 레인의 이름 정규화 커널(법인 표기 토큰)과 **저장 형태가 맞물리는지** 확인한다 —
   저장은 운영자가 입력한 원문, 정규화는 대사 시점(이중 정규화 금지). g1set01 은 아직 머지 전이므로 그 코드를 끌어오지 않는다.
6. 추적표 · capability map · 요구표의 담당 칸 갱신(판정은 실측대로 — 입력만 선 행은 ⓒ 유지).

## Phase 1 — 설계 먼저, 코드·마이그레이션 0 (팀장 승인 뒤 Phase 2)

checklist.md §0 에 쓰고 팀장에게 보고한 뒤 **답을 기다린다**. 판정해야 할 것:

- **(P1) 저장 위치**: 새 필드(상호 · 실적·시평액 · 나라장터 준비 · 체크리스트 상태)를
  (a) V12 `operator_profile` 싱글턴(`CHECK (id = 1)`)에 열로 더하는가,
  (b) R-2 를 따라 `account_id BIGINT NOT NULL REFERENCES account(id)` 를 가진 새 표로 세우는가.
  7B §9 는 기존 싱글턴의 계정 단위 전환을 별 slice 로 권했다. 그 판단과 이 slice 가 **새 사용자 소유 데이터**를 만드는 사실이 충돌하는지,
  어느 쪽이 그 별 slice 를 덜 어렵게 만드는지 근거를 적는다.
- **(P2) 인증 축**: 프로필 endpoint 가 7B 의 `AccountScopedRoutes`(세션 전용) 쪽인가, 기존 운영자 자격 헤더 경로도 받는가.
  (P1) 과 일관돼야 한다. 기존 기계 전수 test(`ProductionHttpSurfaceTest` · `OpenApiContractTest`)는 7B 에서 바뀌고 있다 —
  **main 기준으로 설계하고, 7B 머지 뒤 rebase 에서 맞춘다**고 적는다.
- **(P3) 마이그레이션 번호**: 7B 가 V21 을 쥐고 있다. 이 slice 의 마이그레이션은 **잠정 V22** 로 쓰고, PR 시점 main 최대보다 크게 맞춘다
  (D-6A1-12). 7B 보다 먼저 머지되는 일은 없다고 가정한다(팀장이 순서를 관리한다).
- **(P4) 개인정보·민감 사업정보**: 상호 · 실적액 · 인증서 만료일은 운영자 사업 정보다. **사업자번호 · 대표자명 · 인증서 원문 슬롯을 만들지
  않는다.** 로그·audit 본문 노출 0, evidence 에 실값 0. 보존 분류(`RetentionTableRegistry`) 등재 여부를 판정한다.
- **(P5) 쓰기 경로 형태**: 프로필 쓰기를 전략처럼 편집 세션(초안→확인)으로 하는가, 단건 PUT + CSRF 로 하는가. 근거와 함께.
- **(P6) 범위 밖으로 보낼 것**: 판정 로직(QUAL-08/11) · 정규화 커널 · 화면(7E) · R-2 기존 싱글턴 전환.

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 — 인증 없는 쓰기 · CSRF 없는 상태 변경 · 다른 계정 데이터 접근(구조로) · 면허 세 상태 납작화(Uncertain 이
  「0개 보유」로 바뀜) · 입력만 선 축이 판정된 것처럼 보이는 표시 · 민감 사업정보의 로그·audit 누출. 방어하지 않음 — 운영자 자기 신고값의
  진위(나라장터 대조는 범위 밖).
- **(1) 구성**: 모든 새 경로는 7D 기본 거부 필터 아래(새 무인증 경로 0). 질의는 바인딩 매개변수만. 체크리스트 항목·판정 대기 표시는
  선언적 표.
- **(2) 과잉 금지**: 화면·판정 로직·다수 계정 기능을 만들지 않는다.

## test

- 프로필 왕복(세 상태 보존 — 실 DB) · 부분 갱신이 다른 필드를 지우지 않음 · 입력 검증 경계(빈 값 · 상한 · 날짜 형식).
- 체크리스트 서버 판정 전수(항목 × 완료/미완료).
- 「아직 판정하지 않음」 표시 잠금(QUAL-08 · QUAL-11).
- 인증 경계(401 · CSRF 실패 401)가 기존 기계 전수 test 에 자동 포함되는지. (정정 2026-10-22, D-7C4-1 Q6: 처음 문면은
  「CSRF 403」이었다. 실제 동작은 사유를 나누지 않는 401 이다 — `RequestAuthentication.kt:46-47`,
  `BrowserSessionAuthenticationTest.kt:117-122`.)
- 마이그레이션이 있으면 `CleanMigration*Test` 관례(CHECK · GRANT · 컬럼 · 인덱스) 등재.
- OpenAPI 계약 · 생성 타입 바이트 등식.
- 각 핵심 성질은 변이에서 RED 여야 한다(변이 표를 evidence 에 남긴다).

## 검증·동결

- 무거운 빌드 전 확인 셋(pgrep · free · ps)을 **별도 호출로 먼저**, 기존 flock 으로 직렬. available < 6GB 또는 swap free < 2GB 면 시작하지 않고
  팀장에게 보고. 00:30~03:00 KST 는 6G 수집 창 — 무거운 명령 금지.
- 완료: `check` exit 0 · 변이 표 · rollback.md(명령으로 목록 도출) · `leakPatternGate` executed 확인 → 판정 SHA 보고 후 동결. push 0.
- 검토: verifier(opus) · code-reviewer(sonnet) 병렬. 마이그레이션이 있으면 verifier 표적에 migration-reviewer · privacy-gate 관점을 넣는다.
  Codex 심판은 운영자가 범위·비용을 승인하지 않았으므로 타지 않는다.

## D-7C4-3 (2026-10-24, 팀장) — 2a 검토 r1 처분

verifier r1 `ready-for-review`(medium 1 · low 3) · code-review r1 `approve`(medium 6 · low 14), 둘 다 판정 SHA `8afb94d0`
기준. 동결을 풀고 **승인 전 일괄**로 처리한다(재작업 0/5 유지). finding 별 커밋이고, 게이트 술어를 바꾸는 ① 은 표적
재검증 대상이다.

| 지적 | 처분 |
|---|---|
| F1 · CR M-4 (medium, 게이트 술어) | **구조로 닫는다** — verifier 가 실측한 우회 셋(X1 V12 port 구현이 새 입력을 접음 · X2 의존 역전 · X3 점 표기)을 전부 RED 로. 전·후 표를 evidence 에 남긴다 |
| CR M-1 (쓰기 소유 경계 주장) | KDoc 을 기구대로 좁히고 알려진 제한에 등재. 구조 해소(RLS · 세션 범위 역할)는 2b/별 slice |
| CR M-2 (바인딩 인덱스 상수) | `bindDetails` 가 다음 자리를 내게 바꾼다(손으로 적은 7 제거) |
| CR M-3 · F4 (앱/DB 경계 어긋남) | V22 CHECK 를 좁힌다(공백뿐인 상호 · 중복 등록분야) + 실 DB 경계 test. V22 는 어느 상주 DB 에도 적용 0 · 미머지라 파일을 고친다 |
| CR M-5 (어휘 중복) | 게이트를 세우지 않는다 — 주장을 「이름 · 순서 동일」로 좁히고, **뒤에 머지되는 PR 이 같은 커밋 집합에서** 리터럴 제거 + 참조 전환(알려진 제한) |
| CR M-6 (V20 → V22 공백) | 머지 전제를 아래에 명시 |
| F2 · F3 · CR low 14 | 장부 일괄 커밋. 처리하지 않는 것은 아래 「low 처분」에 사유를 적는다 |

### in_scope 경로 (L-6 — 기계로 대조할 수 있게)

in_scope = **`rollback.md` 의 기계 산출 복원 목록** ∪ `reports/evidence/m7/7c4/`. 그 등식이 「수정 라운드가 만든 새 파일 ↔
in_scope 대조」의 술어다(목록은 라운드마다 재산출한다). 묶음은 다섯이다.

- `workflow/src/{main,test}/kotlin/bidvector/workflow/profile/**` — 값 타입 · port · 입력 정책 · 체크리스트 판정 · 판정 대기 축과 그 test.
- `adapters/src/main/kotlin/bidvector/adapters/profile/{BusinessProfileSql,JdbcBusinessProfileStore}.kt` + `adapters/src/main/resources/db/migration/V22__*.sql` + `adapters/.../retention/RetentionTableRegistry.kt`.
- `adapters/src/test/kotlin/bidvector/adapters/{profile,persistence,retention}/…` — 신설 test 둘 · 열 명세 둘 · `CleanMigration*` 다섯 · 어댑터 의존 게이트 test · 보존 fixture.
- `config/quality/gate-tests.properties` — 신설 test class 등재.
- 장부: `reports/evidence/m6/6e1/acceptance-trace.md`(QUAL-11 행) · `docs/discovery/capability-map.md`(QUAL-11 절, **D-7C4-3 에서 추가** — 산출물 6 의 「capability map 갱신」) · `reports/evidence/m7/7c4/**`.

### 머지 전제 (CR M-6)

production Flyway 는 기본값이다(`outOfOrder` 미설정 = false, `validateOnMigrate` true). 그래서 **7B(V21)가 이 slice(V22)보다
먼저 머지돼야 한다** — 순서가 뒤집히면 V22 가 적용된 DB 에서 뒤에 온 V21 이 out-of-order 로 거부된다. 상주 DB 적용은 0 이고
(`CleanMigration*Test` 는 버릴 컨테이너에만 적용) 번호는 PR 직전에 D-6A1-12 대로 팀장이 다시 잰다.

### 알려진 제한 추가 (D-7C4-3)

1. **쓰기의 소유 경계는 호출자 전제다**(CR M-1). 읽기 · 삭제는 `account_id = ?` 필터가 좁히지만, upsert 둘에서 `account_id`
   는 **실리는 값 / `ON CONFLICT` 대상**이다. 틀린 `AccountId` 를 넘기면 그 계정의 행을 덮어쓰고 열 단위 GRANT 는 막지
   못한다 — 권한 층이 막는 것은 **있는 행을 다른 계정으로 옮기는 일**(`account_id` 의 UPDATE)뿐이다. 2b 는 controller 가
   세션 계정만 넘긴다는 것을 자기 test 로 져야 한다(구조 해소는 RLS · 세션 범위 역할 — 별 판단).
2. **판정 대기 축 어휘가 두 레인에 중복이다**(CR M-5). 7C② 의 `workflow.evaluation` 쪽과 **이름 · 순서만** 같다(모양은
   다르다 — 이쪽만 `capability` 속성). 등식을 잠그는 게이트가 없고 **수동**이다: 뒤에 머지되는 PR 이 같은 커밋 집합에서
   리터럴을 지우고 상대를 참조한다. 방향은 `profile` → `evaluation` 뿐이다(반대는 격리 단언이 붉다).
3. **`ProfileInputPolicy.CAPACITY_ROWS_MAX` 는 아직 집행되지 않는다**(CR L-1). 2a 에 use case 가 없고 표에 행 수 CHECK 도
   없다. 2b 가 집행을 세우고, 검사-후-삽입이 경합하는 것도 그때 판단한다.
4. **상호의 빈 값 술어는 DB 가 앱보다 넓다**(유니코드 공백 변두리) — **그 차이는 앱 경로로 닿지 않는다**(팀장 수용
   2026-10-24). 쓰기는 앱 경로로만 들어오고 앱이 `isNotBlank` 로 더 좁게 거른다. 직접 SQL 로만 생기는 틈이며, 그렇게 저장된
   값은 읽기에서 크게 실패한다(조용히 접히지 않는다).

### low 처분 (고치지 않는 것과 사유)

- **L-8**(중복 등록분야) · **L-7**(연도 상한 DB 미측정)은 ④ 와 같은 커밋에서 **닫는다**(처분: 수용).
- **L-11**(`PersistenceTestSupport` 의 CASCADE 주석)은 **그 파일이 in_scope 밖**이다 — 2b 가 그 파일을 만질 때 또는 별
  slice. 신설 표 둘이 `account` CASCADE 로 비워지는 사실 자체는 맞다(verifier 확인).
- **L-9**(`updated_at` 이 port 에 닿지 않음) · **L-10**(test 안 양성 대조가 자명) · **L-13**(탈퇴 파기가 상속할 FK 제약)은
  **사실 등재만** 한다 — L-9 는 2b 표적, L-10 의 진짜 음성 대조는 변이 표에 있고, L-13 은 `browser_session` 전례와 같다.
- **L-12**(앱 역할 `DataSource` 가 두 인자 `getConnection` 을 위임)는 **닫는다** — 조용히 관리자 권한을 재는 자리다.
- **OQ-1 · OQ-2**(낡은 class 파일 · task 순서)는 실패 방향이 **거짓 빨강**이라 차단이 아니다. 찬 `check --rerun-tasks` 가
  그 여지를 줄이는 것으로 둔다.

### 2b 표적 (D-7C4-3 ⑧ 포함)

1. **DB 예외 문면이 밖으로 나가는가** — pgjdbc 기본값(`logServerErrorDetail` 미설정)에서 CHECK 위반 예외에 `Failing row
   contains (…)` 로 행 값이 실린다. 그 문면이 로그 · `ErrorBody` 로 나가지 않는지 2b 가 잰다(기존 표 전부의 공통 성질).
2. controller 가 세션 계정만 넘기는 것(위 제한 1) · `CAPACITY_ROWS_MAX` 집행(제한 3) · `updated_at` 전제조건(L-9).
3. 격리 술어의 모집단 변화 — 2b 는 `app` 에서 `workflow.profile` 을 정당하게 참조한다. 그때 ① 의 술어가 무엇을 재는지
   다시 적는다.

## D-7C4-4 (2026-10-25, 팀장) — 2a 검토 r2 처분 (승인 전 일괄 2)

verifier r2 `ready-for-review`(@`efc856e5` · medium 1 · low 3) · code-review r2 `approve`(medium 4 · low 7).
출발점은 `d55624ef`(4bd7cd59 의 어휘 전수 test 포함). 재작업 0/5 유지. 무거운 작업은 03:00 이후.

| 지적 | 처분 |
|---|---|
| F5 · CR R2-3 (격리 규칙) | **축을 바꾼다** — 우회마다 술어를 더하는 방식을 멈추고 `adapters` **모듈 전체**에 규칙 둘(표 이름 · 값 타입 좌표, 허용은 `SourceFile` 속성), 그리고 **람다(indy) 축**을 더한다. N1 · N4 · N5 는 닫지 않고 제한·2b 표적으로 적는다 |
| CR R2-1 · F6 (`license_name`) | 상호와 같은 POSIX 술어로 좁히고 공백 경계 test 를 더한다(RED 먼저). PK 일부라 목록 전체가 막힌다는 사실을 test KDoc 에 적는다 |
| F7 · N8 (어휘 전수) | `4bd7cd59` 의 전수 test 가 `array_positions` 오타 · 삭제 변이를 잡는지 실측하고 전·후 표에 적는다 |
| CR R2-2 (KDoc) | 「바이트 동일」 → 「이름 · 순서 동일」, 매달린 「위 (2)」 → 단언 이름 |
| CR R2-4 (역방향 파급) | **1:1 줄 치환으로 고친다**(형제 장부 전례) — 줄을 더하지 않으므로 다른 slice 의 좌표가 밀지 않는다. 다른 slice 의 evidence 는 만지지 않는다 |
| CR R2-L5 (task 산수) | 두 목록을 다시 `comm` 하고 수를 분해해 적는다 |
| F8 · 나머지 CR low | 장부 일괄. 고치지 않는 것은 아래에 처분을 적는다 |

### in_scope 추가 (D-7C4-4)

- `adapters/src/test/kotlin/bidvector/adapters/profile/BusinessProfileIsolationGateTest.kt` — 신설(모듈 전체 규칙).
  `config/quality/gate-tests.properties` 에 등재한다.
- **요구표**(`docs/discovery/m7-screen-data-requirements.md`)는 **변경 0** 이다(CR R2-L7) — 담당 칸이 이미 `7C ④` 라
  고칠 것이 없다. 산출물 6 의 세 장부 중 둘(추적표 · capability map)만 이 slice 가 편집한다.

### 알려진 제한 추가 (D-7C4-4)

5. **격리 게이트가 재는 것은 「직접 참조 · 직접 구현 · 람다 구현」뿐이다**(r2 F5 · CR R2-3). 닫지 않은 세 길:
   **이행 값 흐름**(허용 파일의 `String` 반환 helper 를 거친다 — N1) · **제3 패키지에 선언된 인터페이스**(선언 자리를
   한 칸 옮긴다 — N4) · **실행 시 조립한 이름**(상수 풀에 없다 — N5). 2a 에는 production 배선이 0 이고, **진짜 방어는
   2b 배선의 포트 경계**다. 우회마다 술어를 더하는 일은 r2 에서 멈췄다.
6. **빈 값 CHECK 의 `[:space:]` 멤버십은 DB locale(`lc_ctype`)에 달려 있다**(CR R2-L3). `C` locale 상주 DB 에서는
   ASCII 공백만 공백이라 전각 공백(U+3000) 같은 값이 통과한다. test 는 컨테이너 기본 locale 하나를 잰다 — 「두 집합의
   차이는 유니코드 공백의 변두리」에 **locale 차이도 든다**. 쓰기가 앱 경로로만 들어오므로 그 차이는 앱 경로에 닿지 않는다.

### low 처분 (D-7C4-4)

- **R2-L1 · R2-L2 는 닫힌다** — 파일 단위 이름 허용을 **지웠다**. 모듈 전체 규칙이 `SourceFile` 속성으로 자리를 정하므로
  `BusinessProfileSql` 의 값 타입 허용(필요 없던 넓힘)도, 허용 이름을 흉내낸 class(`…Kt` 이름)도 성립하지 않는다.
- **R2-L4 는 사유만 적는다** — 중복 CHECK 가 다차원 배열(`{{GOODS}}`)을 거부하는 것은 CHECK 위반이 아니라
  `array_positions` 가 1차원만 받기 때문이다. 결과는 맞고 **부수 이득**이다(앞 판은 `<@` 가 통과시켜 읽기에서
  `ClassCastException` 이 됐다). 탐침으로 사유를 실측해 evidence 에 적는다.
- **R2-L6 — r1 L-2 처분 등재**: `UnjudgedQualificationAxis.capability` 는 production 소비자가 0 이고 **test 전용 필드**다.
  지우지 않는다 — capability map 행 ID 와의 대응을 그 자리에 적어 두는 값이다. **머지 해소가 7C ② 열거(속성 없음)를
  단일 출처로 삼으면 이 필드는 사라진다** — 그때 지운다(알려진 제한 2 와 같은 자리).
- **R2-L7** 은 위 in_scope 한 줄로 닫는다.
- **OQ-R2-1**(indy 가 class 파일을 정말 안 내는가)은 이번 변이로 실측한다. **OQ-R2-2**(`array_positions` volatility)는
  이미 닫혀 있다 — 그 CHECK 가 PG 16.4 에서 CREATE TABLE 로 섰고 전용 RED→GREEN 짝이 있다.

### 2b 표적 추가 (D-7C4-4)

4. **포트 경계가 진짜 방어다** — 2b 가 배선을 세울 때 「자격 판정 use case 는 V22 port 를 받지 않는다」를 **조립 자리**
   (wiring)에서 잠근다. 위 제한 5 의 세 길(N1 · N4 · N5)은 그 경계가 닫는다. 격리 게이트의 모집단도 그때 다시 적는다
   (`app` 이 이 패키지를 정당하게 참조하기 시작한다).
