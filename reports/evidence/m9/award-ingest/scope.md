# M9 / award-ingest — 낙찰자 상호 수집 (OPEN-SET01-AWARD-NAME-INGEST)

- base: main `79f3e5c4` · 브랜치 `feature/m9-award-ingest` · worktree `bid-vector-v2-m9-award-ingest`
- 근거: `docs/discovery/capability-map.md` 의 `OPEN-SET01-AWARD-NAME-INGEST` 행(결정 (a) 2026-10-22) · `reports/evidence/m6/g1set01/checklist.md` 처분표 P-23 · M6 종결 §2 · M7 종결 §4 · `reports/evidence/m7/7c5p/scope.md`(「원문이 들어오면 승격은 계약 행만 보고 코드 변경 0 으로 따라온다」).
- **사용자 결정 2026-11-05**: 착수 **승인**(새 조달청 호출 포함). 이 slice 는 코드·mock 검증까지이고 **실 수집 실행은 별도 확인**(상한은 아래 D-4 로 제안해 보고).

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | 낙찰정보서비스 `as/ScsbidInfoService` 의 낙찰 목록(`getScsbidListSttus{Cnstwk,Servc}` — 공사·용역; 물품·외자는 수집 범위 밖) 을 **새 일회 러너** `bidvector.award-collection.mode=once` 로 수집한다. 원문만 적재(`source_endpoint = OPENING_AWARD_LIST`), canonical 칸 채우기는 기존 승격 러너(`bidvector.opening-promotion`) 몫 — **`procurement/**` 변경 0** 이 「계약 행만으로 따라온다」의 증명이다. |
| D-2 | 새 파일로 분리(`AwardCollectionProperties` · `AwardCollectionWiring` · `AwardCollectionRunner` 등) — `OpeningCollection*` 를 고치지 않는다(조달청 02 전환 레인과 충돌 0). 어댑터는 `KonepsOpeningResultSource` 의 `AWARD_LIST` 모드에 production 팩토리 한 함수만 더한다. `OneShotRunnerGuard` 상호 배제에 새 러너 등재. |
| D-3 | 설정 기본값 없음(기존 수집 러너 관례): 기간 창·호출 상한(`calls-per-day`·`calls-total`)·키 env 는 미설정이면 기동 실패. 예산 소진은 `budgetLimit=DAILY|TOTAL` + exit 2 로 멈춘다(기존 꼴). |
| D-4 | **제안 상한**(실 실행 확인 때 사용자에게 보고): 오퍼레이션 둘 × 쿼터 일 1,000 의 한참 아래 — `calls-per-day 200` · `calls-total 3,000`(1년 창 추정 ≈1,460 호출). runbook 에 적는다. |
| D-5 | 개인정보: 사업자등록번호·대표자명 등은 원문 `payload`/`payload_fields` 에 남지 않는다(기존 `KonepsOpeningResultSource` 거동을 새 배선에서 재측정). 낙찰자 상호(`AWARD_COMPANY_NAME`)는 업체 식별 개념이라 보존 창(1년 개정 — 별 레인) 뒤 마스킹된다 — 수집→승격을 창 안에 끝내라는 운영 문장을 runbook 에. |
| D-6 | SET-01 대사가 `WINNER_NAME_NOT_PUBLISHED` 를 벗어나는 것을 실 DB(Testcontainers) 왕복으로 보인다. 화면(내 입찰 결과의 낙찰자 상호)이 값을 보이는지는 기존 조회가 읽으므로 한 줄 확인. |

## in_scope

- 새 `app/src/main/kotlin/bidvector/app/collection/Award*.kt` · `app/src/main/kotlin/bidvector/app/wiring/Award*.kt` · `app/src/main/kotlin/bidvector/app/wiring/OneShotRunnerGuard.kt`(등재) 와 그 test
- `adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSource.kt`(팩토리 한 함수) 와 `adapters/src/test/kotlin/bidvector/adapters/koneps/**`
- 필요 시 `workflow/src/main/kotlin/bidvector/workflow/collection/**` 새 use case(기존 수정은 멈추고 보고)
- `app/src/test/kotlin/bidvector/app/**` E2E · `config/quality/**` 게이트 등재
- 새 runbook `docs/runbook/m9-award-collection.md` · `docs/discovery/capability-map.md`(OPEN 행 종결/갱신) · `reports/evidence/m9/award-ingest/**`

## out_scope

`procurement/**` · 마이그레이션 · `OpeningCollection*` · 실 API 호출 · 운영 반영 · 보존 기간 값.

## acceptance

- CI Kotlin `check` job 명령 그대로 exit 0.
- mock E2E: 낙찰 목록 스윕 → `OPENING_AWARD_LIST` 원문 적재 → 승격 run → 같은 `opening_result` 행의 낙찰자 상호·낙찰금액·낙찰률 채워짐 · 개인정보 키 부재 · 예산 상한 정지(exit 2) · 다른 일회 러너와 동시 기동 거부 · SET-01 대사 실 DB 왕복.
- `git diff --stat 79f3e5c4..HEAD -- procurement` 빈 출력.
- 변이: 원문 적재 endpoint 를 다른 토큰으로 → RED · 개인정보 제거 경로 우회 → RED.

## rollback

in_scope 경로 한정 `git restore --source=79f3e5c4 …` + 새 파일 삭제; 목록 기계 산출(`rollback.md`). 러너는 `mode` 를 안 주면 꺼짐.

## 하네스 레인 변경

없음.
