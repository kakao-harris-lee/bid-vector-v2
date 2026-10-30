# M7/7E-3a — 알림함 + 화면 E 건별 결과 + 여정 ⑤ (착수 계약, 2026-10-31, 팀장)

- **근거**: 운영자 지시 2026-10-30(「7E-2b 끝나면 7E-3 도 이어서 착수」). 7E 분할의 갱신 — **7E-3 을 둘로 나눈다**:
  **7E-3a = 알림함 · 화면 E 의 건별 결과(기존 API) · 여정 ⑤**(지금), **7E-3b = 화면 E 의 내 투찰 대사·낙찰률 집계 · 활용사례 캡처·서비스
  설명**(M7/7C⑤-b 머지 뒤). 사유: 화면 E 의 「내 투찰가 · 차이 · 투찰 대비 낙찰률」을 읽을 API 가 main 에 없다(팀장 실측 2026-10-31,
  `BidCase` 에 투찰 금액 칸 0 · 집계 operation 0) — 그 API 는 M7/7C⑤-b(`reports/evidence/m7/7c5b/scope.md`)가 병렬로 세운다. 화면이 목록을
  세어 집계를 지어내지 않는다. 캡처는 화면 다섯이 다 선 뒤에 한 번 찍는다(7E-3b).
- **base**: main **`003ec91d`**(PR #107 7E-2b 머지) · worktree `bid-vector-v2-m7-7e3a` · 브랜치 `m7-7e3a/2026-10-31`. rollback 복원 기준 `003ec91d`.
- **입력 정본**: 7E-1 계약(D-7E-1~10 — 하네스·시간·상태 표시·§6.5·개인정보·CI 규율 그대로 구속) · 7E-2·7E-2b 계약 · 요구표 「E. 결과 확인」·
  「알림함」 · 7C③ 계약(알림함 표·트리거) · 7C⑤-a 계약(`readOpeningResult` 아홉 갈래 · 잠정/확정 분리 · 다른 업체 상호 0) ·
  `docs/discovery/ux-journey-research.md` §1 ⑤ · §6.
- **레인**: 구현 하나(`kotlin-implementer` — 하네스가 app.jar·Gradle 을 다룬다). 판정 `verifier`(opus) + `code-reviewer`(sonnet) 병렬. Codex 대상 아님.
- **병렬 레인**: M7/7C⑤-b 가 OpenAPI·생성 타입을 바꾼다 — 이 slice 는 OpenAPI 를 **읽기만** 하고, 나중 머지 쪽이 재생성한다(손 병합 금지).

## 화면이 쓰는 operation (base `003ec91d` OpenAPI 에서 — 이 밖을 부르지 않는다)

- 알림함: `GET /api/notifications` · `POST /api/notifications/read` · `POST /api/notifications/read-notice`.
- 화면 E 건별: `GET /api/bid-cases`(결과 단계 상태 — `AWAITING_OPENING` · `OPENED_PROVISIONAL_FIRST` · `OPENED_OUT_OF_RANK` ·
  `UNDER_QUALIFICATION_REVIEW` · `WON` · `LOST` · `FAILED_OR_REBID` — 와 다음 확인 시점) · `GET /api/opening-results`(공고별 개찰 결과) ·
  결과 기록 전이(`/opening` · `/qualification-review` · `/outcome` · `/outcome-correction` 중 화면 E 의 「다음 행동 하나」에 필요한 것 — 착수 보고에서
  D 화면과 겹침을 대조해 확정).
- 승인 목록(`approvedOperations.ts`) 증분은 착수 보고에서 확정. 부족한 API 는 만들지 않고 보고한다.

## 팀장 결정 (추천안 — 착수 보고 실측이 반박하면 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7E3a-1 알림함** | 받은편지함 하나: 항목 목록 · 읽음/안읽음 · 공고(건)별 묶음 · 클릭 → 그 공고의 워크시트(C). 읽음 처리는 서버 응답 뒤에만 표시(낙관 갱신 금지 — 7E-2b 와 같은 원칙). 채널 선택·복제는 빈 자리(R-4). 셸의 안읽음 배지가 있으면 그 수는 서버 값에서만. |
| **D-7E3a-2 화면 E 건별** | 결과 단계 건마다 「아직 모름 / 잠정 1위 / 심사 중 / 확정(낙찰·패찰·유찰)」과 다음 확인 시점을 **서버 상태에서만** 그린다. 개찰 결과는 `readOpeningResult` 의 `state` 아홉 갈래를 접지 않고 표시하고, **잠정과 확정을 다른 칸**으로(잠정 1위 금액이 확정 낙찰가 자리에 서지 않는다). 다른 업체 상호는 응답에 없다 — 화면도 지어내지 않는다. |
| **D-7E3a-3 E 의 빈 자리** | 내 투찰가 · 차이 · 고른 안 · 투찰 대비 낙찰률 · 자동 대사는 **7E-3b 몫** — 이 slice 에서는 빈 자리 + 「준비 중」. 역산(SET-03) · 추천 오차(SET-05)는 영구 빈 자리(근거 부족·후속). |
| **D-7E3a-4 여정 ⑤** | 7E-1 하네스 재사용(실 app.jar · fake KONEPS · 시각 조작 0). 「제출함」 기록된 건 → 개찰 대기 → (fake KONEPS 가 낼 수 있으면) 개찰 결과 관측 → 결과 기록 → 화면 E 에 상태 · 다음 확인 시점 · 개찰 결과가 DOM ↔ 같은 실행 API 응답으로 대조. 알림함에 그 건의 항목이 서면(트리거가 하네스에서 발화하면) 클릭 → 워크시트까지. **트리거가 하네스에서 발화하지 않으면** 그 사실을 착수 보고에 적고 알림함은 vitest 로 잠근다(우회 fixture 로 알림을 심지 않는다 — 심을 operation 이 계약에 있으면 그것만). 순서 독립: 다른 여정의 출발 상태를 깨지 않고 끝에서 되돌림까지 단언, 되돌릴 수 없는 값은 보고. |
| **D-7E3a-5 7C⑤-b 와의 순서** | 7C⑤-b 가 먼저 머지되면 이 slice 는 main 병합으로 받는다(OpenAPI·생성 타입은 재생성). 7C⑤-b 의 새 operation 을 이 slice 에서 부르지 않는다(7E-3b). |

## 설계 검토 (Phase 2.5 — 레인이 착수 보고에 채운다, 구현 전)

- (0) 방어: 클라이언트가 결과 단계·잠정/확정·읽음 상태를 추론 · 잠정을 확정으로 표시 · 「데이터 없음/결과 없음/개찰 전」을 한 얼굴로 접음 ·
  낙관 읽음 처리 · 개인정보(D-7E-6). 방어 안 함: 서버 판정·수집의 옳고 그름.
- (1) 열거인가 구성인가(아홉 갈래·상태 14 는 생성 타입에서 파생, 소진 `switch`) · (2) 우회 ≥5 · (2b) 새 public 표면 전수 · (3) 과잉·미달.

## in_scope

- `web/**`(화면 E · 알림함 · 공통 컴포넌트 확장 · `routes.tsx` · vitest · e2e 여정 ⑤ · 하네스 · 게이트 스크립트) · `.github/workflows/ci.yml` 의 `web`
  job(여정 추가 시) · `tools/one-command-check.sh`(축어 일치) · e2e fixture(출처·해시) · `reports/evidence/m7/7e3a/**` · 요구표 E·알림함 행 판정 문면.

## out_scope

- Kotlin `src/main` · OpenAPI 편집 · 7C⑤-b 의 operation 사용 · 캡처·서비스 설명(7E-3b) · 채널 발송 · 7F.

## 순서

1. **Phase 1 착수 보고(구현 전)**: 알림 트리거가 하네스(fake KONEPS)에서 발화하는지 실측 · 결과 단계 전이를 하네스에서 어디까지 만들 수 있는지 실측 ·
   화면별 operation 확정 · 설계 검토 · 여정 ⑤ 계획(되돌림 가능성 포함).
2. 화면 test·e2e 먼저(RED) → 구현 → 변이 → acceptance → rollback → evidence → 동결 보고.
3. 규율: 편집 → 커밋 → 측정 · 동결 직전 주장 ↔ `git show HEAD:<파일>` 대조 · 하네스는 남의 컨테이너에 닿지 않는다(7E-1 락 규율) · 음성 단언은 양성 표지를
   먼저 기다린 뒤(7E-2b r1 교훈) · 여정의 기다림은 「내가 부른 응답」을 집는다(7E-2b r3 교훈).

## acceptance

- `ci.yml` `web` job 명령 그대로(여정 포함) + Kotlin `check` job + `qualityBaseline`.

## rollback

- in_scope 경로 한정 `git restore --source=003ec91d --staged --worktree --` + 새 파일 삭제, 목록 기계 산출. 공유 파일은 병합된 main 고정 SHA 에서.
  임시 clone ①~⑥, `실측 HEAD` 기록.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-31 착수(팀장) · 운영자 지시 2026-10-30. 7E-3 분할(3a/3b)은 팀장 결정 — 7C⑤-b 선행이 사유.
