# M7/7E-1 — 여정 e2e 하네스 + 공통 셸·상태 표시 + 화면 B·C (착수 계약, 2026-10-29, 팀장)

- **근거**: 운영자 결정 2026-10-29(「7E 지금 착수」). `milestone-7.md` 「구현 대상」 7E 행 · 완료 조건 첫 줄(화면 다섯 + 알림함이 V2 API 만
  부르고 Playwright e2e 가 여정 ①~⑤ 를 fake KONEPS 로 닫는다) · G2 13 · 화면 C 3안 「아직 산출 안 함」(운영자 결정 2026-10-24).
  초안은 2026-10-25 팀장 초안(재부팅으로 scratchpad 소실, 세션 기록에서 복원)이고 이 파일이 정본이다.
- **base**: main **`78d97c59`**(2026-10-29 고정) · worktree `bid-vector-v2-m7-7e1` · 브랜치 `m7-7e1/2026-10-29`. rollback 복원 기준도
  `78d97c59`(전제: 그 SHA 에 이 slice 없음 — 첫 산출물 커밋에서 단언).
- **분할(D-7E-0)**: 7E 는 한 slice 로 크다 → **7E-1**(이 계약: 하네스 · 공통 셸·디자인 토큰·상태 표시 매핑 · 화면 B·C — 여정 ②③) →
  **7E-2**(A 시작하기 · D 내 입찰함 · 설정 — 여정 ①④, A 는 7C④-2b 머지 뒤) → **7E-3**(E 결과 확인 · 알림함 · 여정 ⑤ · 캡처 ·
  서비스 설명 · ①~⑤ 전체 묶음). 각 slice 가 여정 e2e 의 자기 단계를 닫는다. 7E-2·3 계약은 앞 slice 머지 뒤 팀장이 쓴다.
- **입력 정본**: `docs/discovery/ux-journey-research.md` §6(상태 기계 · 화면 · 용어 · §6.5 관행) · `docs/discovery/m7-screen-data-requirements.md`
  (필드 ↔ 계약 표 — 화면 B·C 행) · `docs/adr/0011-web-frontend-stack.md`(스택 · D-2 타입·호출은 V2 OpenAPI 에서만 · D-3 어댑터 금지 게이트 ·
  D-5 CI) · `openapi/bidvector-operator-api.yaml`(읽기만).
- **레인**: 구현 하나(`kotlin-implementer` — 프런트 작업이지만 하네스가 app.jar·Gradle·Testcontainers 를 다룬다). 판정 `verifier`(opus) +
  `code-reviewer`(sonnet) 병렬. Codex 대상 아님.

## 화면 B·C 가 쓰는 operation (base `78d97c59` 의 OpenAPI 에서 옮김 — 이 밖을 부르지 않는다)

- 공통: `/api/session`(7D 로그인 상태·CSRF).
- B 오늘의 공고: `/api/notices`(검색·목록) · `/api/suggestions`(제안 목록) · `/api/bid-cases`(담기 = 입찰 건 생성).
- C 공고 워크시트: `/api/bid-price-requests`(투찰가 요청 — 「아직 산출 안 함」 상태·근거) · `/api/bid-cases/{caseId}` · `/timeline` ·
  `/draft` · `/record`(「제출함」) · `/skip` · `/defer` · `/resume`.
- 부족한 API 가 보이면 **7E 에서 만들지 않는다**(Kotlin `src/main` out) — 해당 7C slice 의 후속으로 보고한다.

## 팀장 결정 (추천안으로 진행 — 레인 Phase 1 실측이 반박하면 착수 보고에서 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7E-1 e2e 하네스** | **로컬 하네스**: Playwright `webServer`(또는 그 앞의 하네스 스크립트)가 ① Postgres(Testcontainers 또는 compose 의 postgres 서비스, 버릴 DB) ② 출하 `app.jar` 를 e2e 설정으로(`bidvector.koneps.base-url=http://127.0.0.1:<port>` — 기존 `requireSafeKonepsBaseUri` 가 loopback http 를 허용하는지 실측) ③ **fake KONEPS**(loopback HTTP 별 프로세스 — 응답은 `data-extract.md` 절차의 authoritative fixture 에서, 출처·SHA-256 을 가진다) ④ `vite preview` + `/api` 프록시를 띄운다. **production 코드에 fake·e2e 분기 0** — app 은 설정 값만 받는다. 공고는 **수집 경로로** 들어온다(DB 에 직접 넣지 않는다 — 수집 → 저장 → API → 화면이 한 사슬이어야 한다). 수집을 트리거하는 출하 경로를 레인이 실측해 착수 보고에 적는다. 기각: 7F-local compose 위(base-url 검사 변경 필요) · API 목(MSW 등, 완료 조건 「V2 API 만」 위반). |
| **D-7E-2 시간** | 출하 `Clock` 은 `SystemClock` 하나다. e2e 는 **시각을 조작하지 않고** fixture 를 실행 시각 상대로 만든다(마감 = now+2d 등). production 에 시각 조작 훅을 넣지 않는다. |
| **D-7E-3 여정 ②③ 정의** | ② 로그인 → 오늘의 공고에서 검색 → 카드 → 담기 ③ 워크시트에서 투찰가 요청 → 「아직 산출 안 함」 상태와 근거 표시 → 투찰 예정 초안 → 나라장터 딥링크·입력값 카드 → 「제출함」 기록 → 타임라인에 그 사건. 각 단계는 **화면이 보여 주는 값을 같은 실행의 API 응답과 대조**한다(화면이 값을 지어내지 않음). |
| **D-7E-4 상태 표시** | 「아직 …」·「없음」 표시는 **API 상태 값에서만** 나오고 클라이언트가 추론하지 않는다. 화면 B·C 가 소비하는 상태 enum 은 **전수 ↔ 표시 매핑**을 exhaustive 하게 둔다(새 enum 값이 생기면 `tsc` 가 멈춘다 — `satisfies Record<Enum, …>` 꼴) + vitest 로 표 등식. 화면 C 의 G2 13 행(DEC-03 「법정 하한율 미해석」 · QUAL-02 · QUAL-08 · QUAL-11 「아직 판정하지 않음」 · COL-04 「요건 없음 vs 수집 실패」 · **DEC-05 · DEC-02 「아직 산출 안 함」 — 근거 없는 숫자 0**)을 화면 test 로 잠근다. 게시 하한율 상태(`floorRateGroundsState`)는 진행 중인 M7/7C②-b 가 `IMPLAUSIBLE` 을 더한다 — 7C②-b 가 먼저 머지되면 그 main 을 병합해 매핑에 넣고, 나중이면 7C②-b 가 매핑 한 줄을 더한다(나중 머지 쪽이 맞춘다; exhaustive 매핑이 그 누락을 컴파일로 잡는다). |
| **D-7E-5 §6.5 관행(이 slice 몫)** | 모바일 우선 반응형 하나 · 다음 행동 하나(primary 버튼은 그 상태의 다음 전이) · 점진적 공개 · 되돌리기 가능한 즉시 반응(토스트 + 실행 취소 — API 가 되돌림 전이를 줄 때만) · 빈/로딩/오류 세 얼굴 · 시스템 테마 · 키보드 접근 · 2층 라벨 · 값이 없으면 빈 자리(가짜 기본값 금지). 디자인 토큰은 Tailwind 테마 한 자리. 범위 밖 기능은 「준비 중」 빈 자리뿐(탭·버튼을 만들지 않는다). |
| **D-7E-6 개인정보** | 화면·콘솔·e2e trace 에 사업자번호·토큰·세션 쿠키 평문 0. trace 는 실패 시에만. e2e 산출물에 누출 스캔(참조형 `grep -rniE -f config/quality/leak-patterns.txt`). |
| **D-7E-7 CI** | 여정 e2e 를 `ci.yml` `web` job 에 넣는다(app.jar 가 필요하면 job 간 산출물 전달 또는 같은 job 에서 bootJar — 레인이 비용과 함께 착수 보고에 제안). `tools/one-command-check.sh` 는 ci.yml 과 축어 일치를 유지한다. job 시간 상한을 넘기지 않는다. |

## 설계 검토 (Phase 2.5 — 레인이 착수 보고에 채운다, 구현 전)

- (0) 위협 모델: 방어 — 화면이 V2 API 밖(legacy · 하드코딩 · 목·정적 JSON)에서 값을 얻는 것 · 근거 없는 숫자 · 상태를 클라이언트가 추론 ·
  CSRF·세션 우회(7D 경계 재사용) · 개인정보 노출. 방어하지 않음 — 시각 디자인 품질 자체(운영자 리뷰 몫).
- (1) 열거인가 구성인가 — 「V2 API 만」은 ADR 0011 D-3 게이트(src-tree · boundary canary)가 구조로 진다. 새 호출이 생성 클라이언트 밖이면 붉어야 한다.
- (2) 우회 ≥5: `fetch` 직접 호출 · 생성 타입 우회 `any` 캐스팅 · 정적 JSON import · 상태 enum 의 기본 분기 · e2e 가 화면 대신 API 만 검증 · 캡처·trace 에 실데이터.
- (3) 과잉·미달: 범위 밖 기능을 「준비 중」 이상으로 만들면 과잉 · B·C 의 G2 13 행 중 하나라도 test 없이 두면 미달.

## in_scope

- `web/**`(화면 B·C · 공통 셸·토큰·상태 매핑 · vitest · e2e · 하네스 스크립트 · `web/scripts` 게이트 등재) · e2e fixture(출처·해시) ·
  `.github/workflows/ci.yml` 의 `web` job(여정 e2e) · `tools/one-command-check.sh`(축어 일치) · e2e 용 app 설정 파일(production 프로필 밖,
  test/e2e 자리) · `reports/evidence/m7/7e1/**` · 추적표의 B·C 관련 G2 13 행 판정 문면.

## out_scope

- Kotlin `src/main`(API 추가·변경) · OpenAPI 편집 · 마이그레이션 · 화면 A·D·E·알림함·설정(7E-2·3) · 캡처·서비스 설명(7E-3) · 채널 발송 · 7F.

## 순서

1. **Phase 1 착수 보고(구현 전, 팀장에게)**: 하네스 실측(수집 트리거 경로 · loopback base-url 허용 · fixture 출처) · 설계 검토 (0)~(3) · CI 비용 제안.
2. 화면 test·e2e 먼저(RED) → 구현 → 변이(아래) → acceptance → rollback → evidence → 동결 보고.
3. 변이 최소: 화면이 값을 리터럴로 내게 함 · exhaustive 매핑에 기본 분기 · 「아직 산출 안 함」 자리에 숫자 · 생성 클라이언트 밖 `fetch` — 각각 RED.

## acceptance

- `ci.yml` 의 `web` job 명령 그대로(여정 e2e 포함 판) + Kotlin `check` job + `qualityBaseline`. `container` 는 web 이미지 입력이 바뀌면.

## rollback

- in_scope 경로 한정 `git restore --source=78d97c59 --staged --worktree --` + 새 파일 삭제, 목록은 `git diff --name-status 78d97c59..HEAD`
  기계 산출. 공유 파일(ci.yml · one-command)은 병합된 main 고정 SHA 에서. 임시 clone ①~⑥ 실측, `실측 HEAD` 기록.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-29 착수(팀장) · 운영자 승인 2026-10-29(분할 7E-1/2/3 포함).
