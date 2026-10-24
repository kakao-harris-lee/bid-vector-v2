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
