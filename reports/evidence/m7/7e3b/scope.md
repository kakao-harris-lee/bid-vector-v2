# M7/7E-3b — 화면 E 의 내 투찰 대사·낙찰률 집계 + 활용사례 캡처·서비스 설명 (착수 계약, 2026-10-31, 팀장)

- **근거**: 운영자 지시 2026-10-30(「7E-2b 끝나면 7E-3 도 이어서 착수」) · 7E-3 분할(7E-3a 계약 「근거」): **7E-3b = 화면 E 의 내 투찰
  대사·낙찰률 집계 · 활용사례 캡처·서비스 설명**. 선행 `readMyBidResults`(M7/7C⑤-b, PR #109)가 main 에 들어왔다. `milestone-7.md` 7E 행 「활용사례용
  화면 캡처·서비스 설명 초안도 여기서 낸다」 · 완료 조건 「활용사례용 화면 캡처·서비스 설명이 준비돼 있다」.
- **base**: main **`ac806a51`** · worktree `bid-vector-v2-m7-7e3b` · 브랜치 `m7-7e3b/2026-10-31`. rollback 복원 기준 `ac806a51`.
- **입력 정본**: 7E-1·7E-2·7E-2b·7E-3a 계약(하네스·시간·상태 표시·§6.5·개인정보·여정 게이트 규율 그대로) · 7C⑤-b 계약(D-7C5B-1~11 — 판정 불가 사유
  열 · 집계 두 축 · 분모 0 사유 · 「고른 안」 언제나 부재) · 요구표 「E. 결과 확인」 · `docs/discovery/ux-journey-research.md` §6 · `milestone-7.md`
  「M7 종결 뒤 — 공공데이터포털 운영계정」.
- **레인**: 구현 하나(`kotlin-implementer` — 하네스가 app.jar·Gradle 을 다룬다). 판정 `verifier`(opus, 캡처의 개인정보 관점 포함) + `code-reviewer`.
  Codex 대상 아님.

## 화면이 쓰는 operation

- `GET /api/bid-results`(`readMyBidResults`) 하나를 승인 목록에 더한다. 그 밖은 7E-3a 까지의 목록 그대로. 부족한 API 는 만들지 않고 보고한다.

## 팀장 결정 (추천안 — 착수 보고 실측이 반박하면 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7E3b-1 건별 칸** | 화면 E 카드에 내 투찰 금액(다섯 칸이 함께일 때만 숫자 — 7E-3a `MoneyReading` 규율) · 고른 안(언제나 「메뉴 아님」 — 서버 `submittedAmountOrigin` 값 그대로) · 차이(`gapState` 가 값이 아닐 때는 그 상태를 답으로) · 대사 판정(`reconciledOutcome` · `reconciliationBlocker` 열 사유 각자의 문면) · 운영자 기록과 대사의 일치(`outcomeAgreement`). 7E-3a 의 「준비 중」 빈 자리를 이 칸들로 바꾼다. 카드와 건의 대응은 `caseId` 로(공고 쌍이 아니라). |
| **D-7E3b-2 집계** | 두 축(운영자 기록 · 대사)을 **나란히**, 각자 분자·분모·표본 수·제외 수(유찰/재입찰 · 미확정)와 함께. 분모 0 이면 비율 자리 없이 서버 상태(`recordedState`·`reconciledState`)와 사유(`reconciliationBlockers`)를 답으로. 화면은 **계산 0** — 비율·수 모두 서버 값. 오늘 대사 축은 구조적으로 분모 0(`WINNER_NAME_NOT_PUBLISHED`) — 그것이 「부재」로 정직하게 보이는 것이 기대 거동. |
| **D-7E3b-3 어휘** | 판정 불가 사유 열 · 대사 결과 · 일치 · 차이 상태 · 집계 상태를 생성 타입에서 파생한 `satisfies Record<Enum, …>` 표로, 폴백 분기 0. 칸 전수 표(7E-3a CR M-3 교훈 — 계약이 준 칸을 버리지 않는다). |
| **D-7E3b-4 여정** | 여정 ⑤(`results.spec.ts`)를 넓힌다 — 결과까지 민 건이 화면 E 에서 내 투찰 금액 · 운영자 기록 축 집계(분모 ≥1) · 대사 축 「부재」+사유를 같은 실행의 `readMyBidResults` 응답과 대조. 여정 게이트(건너뜀·flaky·재시도·등록부)는 그대로 지난다. 새 spec 을 만들면 등록부에. |
| **D-7E3b-5 캡처** | 활용사례 캡처는 **하네스(fake KONEPS · 합성 fixture)** 에서 Playwright 로 찍는다 — 화면 다섯 + 알림함, 데스크톱·모바일 폭 각 하나. 실 데이터·실 계정 0, 화면에 사업자번호·토큰·비밀 문구 0(D-7E-6 — 캡처 PNG 를 텍스트로 검사할 수 없으니 **캡처 직전 DOM** 에 대해 같은 단언을 건다). 캡처 스크립트는 CI 여정 레인과 분리된 명령(수동 실행, 산출물은 저장소의 정해진 디렉터리에 커밋 — 크기 상한을 착수 보고에서 정한다). |
| **D-7E3b-6 서비스 설명 초안** | 공공데이터포털 활용사례 등록용 서비스 설명 초안 문서 하나(`docs/` 아래, 경로는 착수 보고에서). 무엇을 하는가 · 어떤 공공 API 를 어디에 쓰는가 · 화면 흐름(캡처 참조) · **하지 않는 것**(자동 제출 금지 · 채널 발송 없음 · 알림 트리거 미배선 · 대사 미가동의 사유) — 아직 안 되는 것을 된다고 쓰지 않는다. 포털 데이터셋 번호·트래픽 값은 저장소 밖 근거라 빈 자리(R-6). |

## 설계 검토 (Phase 2.5 — 레인이 착수 보고에 채운다, 구현 전)

- (0) 방어: 화면이 집계·차이·판정을 계산 · 분모 0 을 0% 로 · 판정 불가를 패찰로 · 운영자 기록과 대사를 한 값으로 접음 · 「고른 안」 빈칸 · 캡처에
  개인정보 · 서비스 설명의 과장. 방어 안 함: 서버 판정의 옳고 그름(7C⑤-b).
- (1) 열거인가 구성인가 · (2) 우회 ≥5 · (2b) 새 public 표면 전수 · (3) 과잉·미달.

## in_scope

- `web/**`(화면 E 확장 · 표시 표 · vitest · 여정 ⑤ 확장 · 캡처 스크립트 · 여정 등록부) · 캡처 산출물 디렉터리 · 서비스 설명 초안 문서 ·
  `reports/evidence/m7/7e3b/**` · 요구표 E 행 판정 문면.

## out_scope

- Kotlin `src/main` · OpenAPI 편집 · 알림 트리거 배선 · 낙찰자 상호 수집 · 포털 신청 행위 · 채널 발송 · 7F.

## acceptance

- `ci.yml` `web` job 명령 그대로(여정 포함) + Kotlin `check` + `qualityBaseline`.

## rollback

- in_scope 경로 한정 `git restore --source=ac806a51 --staged --worktree --` + 새 파일 삭제, 목록 기계 산출, 임시 clone ①~⑥, `실측 HEAD` 기록.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-31 착수(팀장) · 운영자 지시 2026-10-30.
- 2026-10-31 **병렬 레인 경계(팀장)**: M7/7C③-b(알림 트리거 배선, worktree `bid-vector-v2-m7-7c3b`)가 `web/e2e/journey/results.spec.ts` 의
  **알림함 단언 블록**(빈 얼굴 + 배지 0 → 같은 실행 응답 대조)과 `web/src/display/worksheetDisplay.ts`·그 test 를 소유한다(D-7C3B-10). 이 slice 는
  그 블록·파일을 만지지 않는다 — 같은 spec 의 화면 E 카드·집계 쪽만. 나중 머지 쪽이 main 을 병합한다(손 병합 금지, 생성 TS 는 재생성).
- 7C⑤-b 머지(PR #109)로 생성 TS 의 결과 어휘가 union 이다 — 집계 상태 칸 이름은 `recordedRateState`·`reconciledRateState`(D-7E3b-2 의
  `recordedState`·`reconciledState` 표기를 이것으로 읽는다), 사유 배열 `reconciliationBlockers[]` 도 enum 이다.
- 2026-10-31 **D-7E3b-7(팀장, 착수 보고 수령 — D-7E3b-1·2 정정)**: ⒜ 하네스의 대사 축 사유는 `NOT_OPENED_YET`(개찰 축 미수집)이고
  `WINNER_NAME_NOT_PUBLISHED` 는 운영 환경 예상일 뿐이다 — 화면은 특정 사유를 전제하지 않고 `reconciliationBlockers[]` **전수**를 그리며, 여정 ⑤ 는
  「부재 + 사유 ≥1」까지, 사유 열 전수는 vitest. ⒝ 내 투찰 금액은 **넷 칸 non-null**(basis 없음) — 세 갈래 `MoneyReading` 은 확정 낙찰가 다섯 칸에만 적용.
  ⒞ 카드 ⊆ `cases[]` 는 전이표의 성질이라 대응은 `caseId` 로, 행을 못 찾으면 사유 한 줄(Q5). ⒟ 집계 절에 **표본 정의 문면**(「카드 수 ≠ 표본」)을 칸으로.
- 2026-10-31 **D-7E3b-8(팀장 — 비율 표기 Q1)**: 서버 비율 문자열을 **그대로** + 분자/분모 병기(「낙찰률 0.5 — 낙찰 1 / 분모 2」), 퍼센트 환산 없음(화면 계산 0).
- 2026-10-31 **D-7E3b-9(팀장 — 개찰 축 두 출처 Q2)**: 카드의 개찰 절(`readOpeningResult`)은 그대로, `readMyBidResults` 의 `openingState`·`confirmedAward*` 는
  대사 절 안 「판정이 본 입력」으로 따로 그린다 — 어긋나면 둘 다 보인다(접지 않는다, 전용 경고 UI 없음).
- 2026-10-31 **D-7E3b-10(팀장 — 캡처 Q3·Q4)**: 화면 여섯(A·B·C·D·E·알림함) × 폭 둘 = 12 PNG, **설정 화면 제외**(화면 다섯 밖 + 프로필 PII 표면).
  스크립트 `web/e2e/capture/usecase-captures.mjs`(여정 `testDir` 밖 · `npm run capture:usecase` 수동 · CI 미연결), 캡처 **직전** DOM 단언이 하나라도 걸리면
  파일을 쓰지 않고 멈춘다. 산출물 `docs/usecase/captures/` + `manifest.json`(파일별 SHA-256 · 캡처 시각 · HEAD), 상한 PNG 300KB · 합 2MB(스크립트가 재고 실패).
  in_scope 에 `docs/usecase/**` 를 더한다(저장소 첫 이미지 바이너리).
- 2026-10-31 **D-7E3b-11(팀장 — 서비스 설명)**: `docs/usecase/portal-service-description.md`, 착수 보고 §5 목차, 「하지 않는 것」 각 줄은 OPEN 홀더 id·결정 id 를
  인용한다(홀더가 닫히면 그 slice 가 문서를 고친다). 알림 트리거 줄은 7C③-b 머지 순서에 따라 나중 머지 쪽이 맞춘다.
