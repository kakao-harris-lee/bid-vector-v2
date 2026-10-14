# M7/7A — 스택·재사용 판정 + ADR 0011 + 화면 데이터 요구표 + `web/` 골격 (착수 계약, 2026-10-14, 팀장)

- 정본: 이 파일. 결정 ID `D-7A-N`. 입력: `milestone-7.md` 「구현 대상」 7A 행 · `docs/adr/0008-frontend-disposition.md` D-4(개정 2026-10-03) · `docs/discovery/ux-journey-research.md` §5~§6 · `openapi/bidvector-operator-api.yaml` · `docs/discovery/capability-map.md` · `reports/evidence/m6/6e1/acceptance-trace.md`.
- base `d930d54c`(main, PR #74 뒤) · worktree `bid-vector-v2-m7-7a` · 브랜치 `m7-7a/2026-10-14`.

## 운영자 결정 (2026-10-14) — 이 slice 를 여는 근거

- **(다) 7A 예외 착수.** `milestone-7.md` 「선행 조건」은 M6 완료 조건 성립 뒤 착수다. 7A 만 그 예외로 지금 연다 — 7A 는 운영 DB·외부 호출·production 서버 코드를 만들지 않는 문서 + 프런트 골격 slice 다. 7B 이후는 여전히 M6 완료 조건(아래 (나)로 조정된 것) 뒤.
- **(나) M6 완료 조건 3 의 범위를 7A 데이터 요구표로 조정한다.** 조건 3(「M0 필수 capability E2E 전체 통과」)은 6E-1 추적표 기준 61 중 15 성립·46 미구현이다. 7A 의 데이터 요구표가 「화면 여정(M7)이 실제로 소비하는 capability 집합」을 기계적으로 정하고, 팀장이 그 집합을 근거로 **조건 3 의 판정 대상을 둘로 가르는 조정안**(M6 안에서 닫을 것 / M7·후속으로 넘길 것)을 작성해 운영자 결정에 올린다. **조정 자체는 이 slice 가 하지 않는다** — 조정안 문서까지가 산출이고, 결정은 운영자가 내린다(6G 를 완료 조건에서 분리한 2026-10-03 결정과 같은 형식).

## 산출물

| ID | 산출 | 저자 | 수용 |
|---|---|---|---|
| **P-1** | `docs/discovery/m7-screen-data-requirements.md` — 화면 다섯(A~E) + 알림함의 **필드 단위** 데이터 요구표. 행마다: 화면·필드·의미·출처 capability ID · V2 OpenAPI 대응(operationId·필드) 또는 부재 · 판정 ⓐ 덮음 / ⓑ V2 누락 capability(7B·7C 가 만든다) / ⓒ legacy 에만 있던 요구(버린다) · 담당 slice(7B/7C/7D/7E) | 세션 모델(단독) | 화면 다섯·알림함 전 필드가 판정 셋 중 하나 · 판정 ⓑ 는 담당 slice 가 비지 않음 · 출처 capability ID 는 capability map 에 실재(기계 대조) |
| **P-2** | 같은 문서의 「M7 소비 capability 집합」 절 — P-1 에서 기계 산출한 capability ID 집합과, 그 각각의 6E-1 추적표 분류(ⓐ/ⓑ/ⓒ) | 세션 모델 | 집합은 P-1 표에서 산출(손 목록 금지) · 추적표 분류는 추적표 원문 인용 |
| **P-3** | `reports/evidence/m7/7a/m6-condition3-adjustment-proposal.md` — 조건 3 조정안: P-2 집합 ∩ 미구현 → M6 에서 닫을 것, 나머지 → M7·후속, OPEN 에스컬레이션 17 의 각 처분 제안. **운영자 결정 대상**(결정 전에는 효력 없음) | 세션 모델 | 61 전건이 두 갈래 중 하나 · 갈래마다 근거 · OPEN 17 전건 처분 제안 |
| **P-4** | `docs/adr/0011-web-frontend-stack.md` — R-1(a) 스택 등재: React 19 · Vite · TypeScript · react-router · TanStack Query · Tailwind · vitest · Playwright, `web/`. 타입은 V2 OpenAPI 에서만 생성(D-4 ③ 게이트 = 어댑터 없음). legacy 는 컴포넌트·스타일·e2e 시나리오 수준 참고만 | 세션 모델 | ADR 0008 D-4 개정 블록과 상호 참조 · 어댑터 금지가 게이트로 서는 자리(P-5) 명시 |
| **P-5** | `web/` 골격 — Vite + React + TS strict, react-router, TanStack Query, Tailwind, vitest(단위), Playwright(설치·빈 스모크 하나), **OpenAPI → TS 타입 생성 + check 모드**(생성물이 문서와 어긋나면 RED), lint(eslint)·typecheck·test·build 를 **CI job 하나(`web`)** 로. 화면 구현 없음(빈 셸 + 라우트 자리 다섯) | 구현 레인 | `web` job 명령 그대로 로컬 exit 0 · 생성 타입 check 모드 음성 대조(OpenAPI 한 줄 바꾸면 RED) · legacy 경로·타입 import 0(게이트) |
| **P-6** | `tools/one-command-check.sh` 에 `web` job 명령 추가(완료 조건 1 의 one-command 가 새 job 을 포함) | 구현 레인 | S-20 exit 0 |

## 위협 모델 경계 (설계 검토 (0) 요약)

**방어하는 것**: 화면 타입이 V2 OpenAPI 밖에서 오지 않는다(legacy 타입·경로 매핑 어댑터 0 — D-4 ③) · 생성 타입과 OpenAPI 문서의 불일치가 CI 에서 RED · 데이터 요구표의 판정이 capability map·OpenAPI 와 기계 대조된다. **방어하지 않는 것**: 화면 동작(7E) · 인증(7D) · 화면이 부를 새 endpoint(7B·7C) · 운영 반입(7F) · 의존 패키지 공급망 일반(6E-2b 취약점 스캔 범위 밖이면 등재만).

## in_scope / out_scope

- in: `docs/discovery/m7-screen-data-requirements.md` · `docs/adr/0011-*.md` · `docs/adr/0008-frontend-disposition.md`(D-4 개정 블록에 7A 판정 결과 포인터만) · `web/**` · `.github/workflows/ci.yml`(`web` job 추가만) · `tools/one-command-check.sh`(web 단계 추가만) · `.gitignore`(web 산출물) · `milestone-7.md`(7A 착수·종결 문단, 운영자 결정 등재) · `milestone-6.md`(운영자 결정 (나)의 등재 문단만 — 조정 효력은 결정 뒤) · `reports/evidence/m7/7a/**`.
- out: Kotlin·Python production 코드 · 마이그레이션 · OpenAPI 문서의 endpoint 추가(7C) · capability map 의 `BID-01` 정식 등재(7B) · 화면 구현(7E) · 인증(7D) · docker/compose(7F) · 외부 호출 · legacy 저장소 수정.

## acceptance

CI job 명령 그대로: 새 `web` job 전 step · 기존 `check` job(`check` → `qualityBaseline` → S-20 — S-20 이 web 단계를 포함하게 되므로) · `ml-engine`·`container` job 은 해당 입력 무변경이면 생략 사유를 기계 확인으로 등재. 호스트 규율(빌드 전 pgrep·free·ps 별도 호출 · 직렬).

## rollback

in_scope 경로 한정 `git restore --source=<병합된 main SHA 고정> --staged --worktree --`(머지 전) · 머지 뒤는 PR revert. 새 파일(`web/**` · 문서 셋)은 삭제로 되돌린다. 운영 영향 0(런타임 코드 무변경).

## 검토 레인

verifier(opus) · code-reviewer(sonnet, 병렬) — `web` 골격과 CI job. 문서 P-1~P-4 는 세션 모델이 단독으로 쓰고(운영자 지시 2026-09-04), verifier 는 P-1 의 기계 대조(필드·capability ID·OpenAPI 대응)를 표적으로 받는다. Codex 없음(되돌리기 어려운 경로 아님).

## 계약 갱신 이력

**D-7A-1 (r1, 2026-10-15, 팀장 — 구현 수령 · 동결).** 문서 셋(세션 모델 단독): 요구표 `80812682` · ADR 0011 `bbd76aba` · 조건 3 조정안 `4ec0fee4`(운영자 결정 Q-1~Q-3 대기). `web` 골격(구현 레인): 산출물 판정 SHA **`dd33d4bb`** · evidence `e13041cc`. 설계 검토 정정 둘 수용 — ① **U-3(`src` 안 파일 심볼릭 링크)은 tsc 가 링크 경로로 프로그램에 넣어 TS6059 가 나지 않는다**(실측 — 설계 검토의 기대가 틀렸고 구멍이 열려 있었다) → 새 step W-4 `scripts/check-src-tree.mjs`(컴파일러 API 로 유효 tsconfig 의 `rootDir`·`include`·`allowJs`·`paths` 단언 + `src` 안 링크 0 + 프로그램 파일 realpath 가 `src` 안)가 닫는다 ② U-8 1차 측정 오염(probe 주석이 `@ts-nocheck` 지시어로 읽힘) → 정정 커밋 `dd33d4bb`. `@ts-ignore`·`@ts-nocheck` 는 TS6059 를 억제하므로 `ban-ts-comment` 가 진다(U-4). TypeScript 5.9.3 고정(typescript-eslint·openapi-typescript peer 교집합). 레인 acceptance: `web` job W-1~W-9 전부 exit 0 · `tools/one-command-check.sh` exit 0 · evidence HEAD 에서 check exit 0. 검토: verifier(opus) · code-reviewer(sonnet) 병렬.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
