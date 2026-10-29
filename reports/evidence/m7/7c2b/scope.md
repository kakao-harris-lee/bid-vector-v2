# M7/7C②-b — 투찰가 요청 응답의 게시 하한율 표시에 DEC-10 게이트를 건다 (착수 계약, 2026-10-29, 팀장)

- **근거**: 운영자 지시 2026-10-29(「공고 상세 화면 문제도 해결해」). 문제는 M6/G1-DEC10(PR #99) r6 에서 실측됐다 —
  7C② 의 `floorRateGroundsStateOf` 가 `origin` 만 보고 개연 밴드를 보지 않아, 개연 불가 게시 하한율(예 `1.00000`)이
  `floorRateGroundsState = USABLE` 이고 `publishedFloorRateFraction` 에 **숫자까지** 실려 화면 근거 칸에 나간다. 가격 경로는
  게이트 타입으로 막혀 있지만 **표시는 출하 경로**다. DEC-10 계약 D-DEC10-7/D-DEC10-13/D-DEC10-16 이 이 일을 7C② 후속으로 인계했다.
- **base**: main **`78d97c59`**(PR #99 DEC-10 머지 커밋, 2026-10-29 고정) · worktree `bid-vector-v2-m7-7c2b` · 브랜치 `m7-7c2b/2026-10-29`.
  rollback 복원 기준도 `78d97c59` 다(전제: 그 SHA 에 이 slice 없음 — 첫 산출물 커밋에서 단언).
- **정본**: 이 파일. 결정 ID `D-7C2B-N`. 게이트 의미의 정본은 `decision.PublishedFloorTrust`(`trustPublishedFloorRate`)와 DEC-10 계약
  (`reports/evidence/m6/g1dec10/scope.md`), 표시 어휘의 정본은 `openapi/bidvector-operator-api.yaml`.
- **레인**: 구현 하나(`kotlin-implementer`). 판정은 `verifier`(opus) + `code-reviewer`(sonnet) 병렬. Codex 대상 아님(인증·암호화·
  마이그레이션·파기 경로 아님).
- **성격**: 표시 어휘 slice — 게이트를 **새로 만들지 않는다.** 이미 있는 게이트 함수 하나를 표시 조립이 부르게 하고, 그 결과를
  응답 어휘로 옮긴다.

## 결정 (팀장 — 추천안으로 진행)

| ID | 결정 |
| --- | --- |
| **D-7C2B-1** | **표시 상태는 게이트 판정에서 짓는다.** `floorRateFieldsOf` 는 `trustPublishedFloorRate(notice.floorRate, <평가가 쓰는 같은 floor-trust 정책>)` 의 결과 하나로 상태와 근거 값을 함께 낸다: `Trusted` → `USABLE` + 그 값 · `Absent` → `ABSENT` · `Implausible` → **새 상태 `IMPLAUSIBLE`** · `OriginNotJudged` → `ORIGIN_NOT_PUBLISHED`(기존 어휘 그대로). 상태와 값을 두 함수가 따로 판정하는 지금 구조(`floorRateGroundsStateOf` · `usableFloorRateOf`)를 없애 둘이 갈라질 자리를 닫는다. 정책은 **평가 경로와 같은 인스턴스**를 쓴다(표시만 다른 밴드를 보는 일이 없게) — 정책 값을 코드에 다시 적지 않는다. |
| **D-7C2B-2** | **`IMPLAUSIBLE` 이면 근거 칸 `publishedFloorRateFraction` 은 `null` 이다** — 기존 `ORIGIN_NOT_PUBLISHED` 의 관례(「근거로 설 수 없는 값은 근거 칸에 싣지 않고 상태가 말한다」, OpenAPI 설명문)를 그대로 따른다. 원값을 다른 칸으로 새로 내지 않는다(응답 필드 추가 없음). `publishedFloorRateOrigin` · `publishedFloorRateNoticeRound` 는 지금처럼 출처 사실로 남는다. |
| **D-7C2B-3** | **어휘는 OpenAPI 가 정본이고 생성 타입이 따라간다**: `floorRateGroundsState` enum 에 `IMPLAUSIBLE` 추가 + 설명문(개연 밴드 밖이라 근거로 쓰지 않는다 · 값을 고치지 않는다), `web/src/api/generated/operator-api.ts` 재생성(`npm run api:types`) — `api:types:check` 바이트 등식. 어휘 test(`OpenApiBidPriceVocabularyTest` 류)가 Kotlin enum ↔ OpenAPI enum 등식을 잰다. web 화면 코드는 오늘 이 필드를 소비하지 않는다(7E 미착수) — 소비 코드를 새로 만들지 않는다. |
| **D-7C2B-4** | **DEC-10 census 갱신은 기계적 귀결이다**: 표시 조립이 원값 대신 게이트 결과를 읽으면 `FloorTrustGateTest` ⓑ·ⓑ′ 의 허용 키 일부가 **낡은 허용**으로 붉어진다(설계대로). `tools/floor-trust-allowlist.py` 로 키 단위 diff 를 내고, 빠지는 키·들어오는 키를 자리별로 `commands.md` 에 적는다(DEC-10 제한 ⑫ 규율 ①). 가격 경로 셋의 진입 타입은 건드리지 않는다. |
| **D-7C2B-5** | **머지 순서**: 진행 중인 M7/7C④-2b 가 같은 OpenAPI 파일·생성 타입을 바꾼다 — **7C④-2b 가 먼저 머지되고**, 이 slice 는 그 뒤 main 을 병합해 생성 타입을 다시 만든다(손 병합 금지, 재생성 + 바이트 등식). 병합 때 OpenAPI 는 키 계수·중복 키 0 으로 검증한다. |

## in_scope

- `app/src/main/kotlin/bidvector/app/http/BidPriceRequestResponse.kt`(`floorRateFieldsOf`) · `BidPriceFactLabels.kt`(`floorRateGroundsStateOf` · `usableFloorRateOf` 정리) · `BidPriceResponseStates.kt`(`FloorRateGroundsState.IMPLAUSIBLE`).
- 정책 주입에 필요한 app wiring(`app/src/main/kotlin/bidvector/app/wiring/**` 중 투찰가 요청 조립 자리) — 평가 경로의 floor-trust 정책을 표시 조립에 넘기는 데 필요한 만큼.
- `openapi/bidvector-operator-api.yaml` 의 `floorRateGroundsState` enum·설명문 · `web/src/api/generated/operator-api.ts`(재생성).
- test: `app/src/test/kotlin/bidvector/app/http/**` 의 투찰가 요청 응답·어휘 test, DEC-10 census(`FloorTrustGateTest` 와 그 허용 목록 `config/quality/architecture-policy.properties` 의 floor-trust 키), 새 test 클래스는 `config/quality/gate-tests.properties` 등재.
- `docs/discovery/capability-map.md` 의 DEC-10 · 7C② 관련 OPEN/인계 문면 정리(인계 종결 표시) — 한 절 안에서.
- `reports/evidence/m7/7c2b/**`.

## out_scope

- 게이트 의미·밴드 값 변경(DEC-10 소관) · 가격 경로 진입 타입 · 새 응답 필드 · web 화면 소비 코드(7E) · `OPEN-DEC10-CRITICAL-RATE-CALLERS` · DB/마이그레이션.

## 순서

1. **test 먼저(RED)**: 게시 하한율 `1.00000` → 상태 `IMPLAUSIBLE` · 근거 칸 `null` · 출처/차수는 그대로 / 밴드 경계 `0.30`·`0.995` → `USABLE` + 값 / `0.29999`·`0.99501` → `IMPLAUSIBLE` / 부재 → `ABSENT` / 법정표 출처 → `ORIGIN_NOT_PUBLISHED`. 응답 JSON 수준(HTTP test)에서 적어도 하나. RED 를 실측해 기록.
2. 구현 → 어휘(OpenAPI · 생성 타입) → census 갱신.
3. 변이: ⓐ 표시가 다시 `origin` 만 보게 되돌림 ⓑ `IMPLAUSIBLE` 에서 값을 싣게 함 ⓒ 표시가 평가와 다른 밴드(리터럴)를 보게 함 — 각각 RED. 통제군 `--rerun`.
4. acceptance → rollback → evidence → 동결 보고.

## acceptance

- **CI job 명령 그대로**(`.github/workflows/ci.yml`): Kotlin `check` job + `qualityBaseline` · **`web` job 전체**(생성 타입·OpenAPI 가 바뀌므로). `ml-engine` 입력은 바뀌지 않는다 — 바뀌면 그 job 도. `container` 는 PR CI 가 정본.

## rollback

- in_scope 경로 한정 `git restore --source=78d97c59 --staged --worktree -- <경로들>`, 목록은 `git diff --name-status 78d97c59..HEAD` 기계 산출. 임시 clone 에서 ①~⑥ 실측, `실측 HEAD` 기록. 7C④-2b 병합 뒤면 복원 기준은 그 병합된 main 고정 SHA 로 재산출.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-29 착수(팀장).
