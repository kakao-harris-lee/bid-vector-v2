# M7/7C①b — 점검표

## 0. Phase 1 설계 메모 (코드·마이그레이션·Gradle 0, 팀장 승인 대기)

base `bee7c36f`. 이 절의 좌표는 둘로 나뉜다. Phase 2 에서 **편집하지 않을** 파일은 `file:line` 으로 적었다.
**편집할 수 있는** 파일(평가 use case · 계약 문서 · 오류 매핑 · 게이트 계약 · 추적표 · capability map)은 낡지 않도록
「base `bee7c36f` 기준 줄」로 고정해 적었다. 다른 레인 브랜치의 근거는 그 브랜치 SHA 를 함께 적었다.

### 0.1 결론 요약

| 물음 | 판정 | 마이그레이션 |
|---|---|---|
| P1 계산 시점 | **조회 시 동기 계산.** 감시·면허 두 값싼 게이트에서 멈추고 ML·사다리·용량은 부르지 않는다. 영속 스냅샷은 만들지 않는다 | **없음** |
| P2 인증 축 | 기존 읽기 operation 기본값(자격 헤더 **또는** 세션 쿠키, CSRF 없음). 계정 전용으로 두지 않는다 | — |
| P3 카드 형태 | `$ref`/`allOf` 는 평탄 게이트(D-6A1-38)가 막는다. **평탄 스키마 + 「NoticeSummary 속성 정의 등식」 test** 로 같은 형태를 잠근다. 코드 매핑은 `NoticeSummaryResponse.from` 하나를 재사용한다 | — |
| P4 STR-07 편집 경로 | 오늘 코드의 쓰기 경로는 **하나**(편집 세션 확정)다. 닫는 방식은 **소비자 쪽 비교**다. 계산에 쓴 전략 **값**을 계산 뒤 현재 값과 비교한다. 다르면 결과를 버리고 한 번 재계산하며, 또 다르면 409 로 끝낸다. 쓰기 쪽의 규율에 기대지 않는다 | — |
| P5 범위 밖 | ML-01 · 7C③ 알림 · STR-08 이력/diff · 7E 화면 · 지역/실적 자격 판정 · dry-run/커밋의 예산 결합 수정(아래 발견 F-1, 팀장 판단) | — |

### 0.2 먼저 보고할 발견 — V2 에 STR-06 결함이 이미 있다 (F-1)

- `CandidateLimit` 의 KDoc 은 「분석 예산과 결합하지 않는다(STR-06 「폐기」)」라고 적는다(`strategy/src/main/kotlin/bidvector/strategy/ActionThresholds.kt:27`).
- 그런데 dry-run 조립은 `analysisBudget = strategy.candidateLimit?.value` 다(`app/src/main/kotlin/bidvector/app/wiring/EvaluationDryRunFactory.kt:87`). 커밋 조립도 같다(`app/src/main/kotlin/bidvector/app/wiring/EvaluationCommitWiring.kt:110`). 6A-3 이 의도해서 정한 값이다(`reports/evidence/m6/6a3f3/scope.md:100-101`, D-6A3-8).
- 게다가 예산은 **값싼 필터 앞에서** 원시 후보 순번으로 깎인다. `evaluateOne` 의 첫 갈래가 `analysisBudgetDrop(index, …)` 이고, `index` 는 `openCandidates()` 목록의 순번이다(`EvaluateCandidatesUseCase.kt`, base `bee7c36f` :118-136·:143). 그래서 후보 상한이 10 이면 마감이 가장 가까운 공고 10 건만 감시 게이트를 거친다. 그 뒤에 있는 감시 일치 공고는 평가조차 되지 않는다.
- 이것은 legacy 가 고친 굶김(OPS-08 (c), `docs/discovery/capability-map.md:2512-2515` — 「값싼 필터는 여전히 전체를 스캔」)이 V2 에서 다시 생긴 것이다. STR-06 의 「표시 상한과 계산 예산의 결합」이 ×12 clamp 없이 1:1 로 들어와 있다.
- **이 slice 의 처분(제안)**: 새 operation 은 이 결합을 만들지 않는다(0.3). dry-run·커밋의 결합은 **고치지 않고 보고만** 한다. 커밋 경로는 outbox 에 쓰는 실행 경로라서, 예산 출처를 바꾸면 알림 요청 집합이 바뀐다. 그래서 따로 판단해야 한다. 이 상태로는 추적표 STR-06 을 「성립」으로 올리지 않는다. 판정은 「제안 목록 절반 성립 · dry-run/커밋 결합 잔존」으로 적는다. **팀장 판단 필요(Q1).**

### 0.3 P1 — 계산 시점: 조회 시 동기 계산

**무엇을 계산하는가.** 제안 목록은 「감시 통과 × 자격 통과 × 마감 임박」이다(`docs/discovery/m7-screen-data-requirements.md:45`). 평가 use case 의 앞쪽 세 게이트만 쓴다.

1. 생애주기(입찰 가능 + 마감 전)
2. 감시 게이트(`WatchVerdict.Passed` 만 통과)
3. 면허 게이트(`Ineligible` 이 아닌 것. `Eligible`·`Uncertain` 통과 — U-5)

임계치·ML·사다리·용량·알림은 부르지 않는다. 사다리는 오늘 쓸 정보를 내지 않는다. ML 이 `UnavailableMlAnalysis` 고정이라(`app/src/main/kotlin/bidvector/app/wiring/EvaluationWiring.kt:70-71`) 점수가 없고, 점수가 없으면 사다리는 모든 후보에 `Review(mlUnavailable)` 를 낸다(`decision/src/main/kotlin/bidvector/decision/VerdictLadder.kt:36-38`). 용량은 요청자가 `currentActiveBids` 를 실어야 하는데, GET 목록에는 그 값이 없다.

**비용 구조.** 측정한 값이 아니라 코드에서 센 질의 수다.

| 단계 | 질의 | 근거 |
|---|---|---|
| 전략 | 1 | `JdbcStrategyRepository.load` (`adapters/.../strategy/JdbcStrategyRepository.kt:66-74`) |
| 후보 스캔 | 1 (≤ `cap+1` 행, 상한 초과는 `CandidateCapExceededException`) | `adapters/.../evaluation/JdbcCandidateSource.kt:73-86` |
| 감시 게이트 | 0 (메모리 안 순수 판정) | `adapters/.../evaluation/NoticeWatchSubjectPort.kt:3-11,25` (I/O import 없음) |
| 면허 게이트 | **감시 통과 건마다 2** (요건 1 + 프로필 1) | `adapters/.../qualification/StoredRequirementLicenseGate.kt` `verdictFor` — `store.find` + `operatorProfilePort.current()` |
| 계산 뒤 비교(0.6) | 1~2 | 제안 |

- 최악은 `2 + 2·cap` 질의다. CI 의 `cap` 은 1000 이다(`.github/workflows/ci.yml:356`). 운영 값은 `BIDVECTOR_APP_CANDIDATE_CAP` 환경변수이고(`docker/compose.yaml:91`) 이 메모는 그 값을 읽지 않았다.
- **제안**: 요청 스코프 조립에서 프로필을 **한 번만** 읽어 고정한다(`PinnedStrategyRepository` 와 같은 형태). 그러면 질의 수는 `3 + n_watch_passed` 로 줄고, 0.6 의 입력 고정도 함께 해결된다.
- **실측은 아직 없다.** Phase 1 은 Gradle·DB 를 돌리지 않는다. Phase 2 의 증거는 둘이다. ① 7C① 의 기록 프록시 관례로 GET 한 번의 문장 수를 단언한다. ② Testcontainers 에서 `cap` 건 시드로 걸린 시간을 한 번 재서 evidence 에 적는다. 그 값이 크면 그때 스냅샷 안으로 되돌아온다.

> **정정 (수정 라운드 1, 2026-10-23, D-7C1B-3 — 위 문면은 Phase 1 원문 그대로 둔다).**
> - 위 표의 「최악 `2 + 2·cap`」은 **틀렸다.** 표가 계산 뒤 비교(마지막 행 「1~2」)를 합계에 넣지 않았고, 입력 읽기에서 프로필 1 을 빠뜨렸고, 요건 **헤더가 있는** 후보의 행 질의 1 을 빠뜨렸다.
> - 출하 코드 기준의 식은 **`5 + 2n + h`** 다. 입력 읽기 2(전략 `SELECT_STRATEGY` · 프로필 `SELECT_PROFILE`) + 후보 스캔 1(`SELECT_OPEN_CANDIDATES`) + 감시 통과 후보마다 2(`StoredRequirementLicenseGate.verdictFor` 의 `JdbcRequirementStore.find` 가 헤더 `SELECT_REQUIREMENT_STATUS` 1, `operatorProfilePort.current()` 1) + 그 가운데 헤더가 있는 후보마다 1(`find` 가 헤더가 있을 때만 `SELECT_REQUIREMENT_ROWS`) + 계산 뒤 비교 2(전략·프로필). 감시를 통과하지 못한 후보는 0 이다(면허 게이트까지 가지 않는다). 재계산이 걸리면 그 식이 한 번 더 붙는다.
> - 헤더가 없으면 `5 + 2n`, 있으면 후보당 +1 이다. 두 식을 헤더 유무를 섞은 fixture 로 함께 단언한다 — `SuggestionProductionE2ETest` 「GET 한 번의 읽기 문장 수는 5 + 2n + h 다」(표별 계수까지 등식, §2 M10 · M11).
> - 「프로필을 한 번만 읽어 고정」 제안은 하지 않았다(§6-5). 그래서 `3 + n` 으로 줄지 않았다.

**영속 스냅샷을 고르지 않는 이유.**

1. 계산이 ML 을 부르지 않아서, 비용을 미리 치를 이유가 없다(위 표).
2. 스냅샷을 만들면 표(**V23 잠정** — 7B V21 · 7C④ 잠정 V22 다음), 재계산 실행자, 무효화 구독이 필요하다. 무효화 구독은 `StrategyUpdated` outbox 소비자이거나 커밋 후 훅이다. V2 에는 앱 안 주기 실행기가 없고 일회 러너뿐이다(`OneShotRunnerGuard`). 그래서 이 slice 가 실행 기반까지 새로 세워야 한다.
3. 스냅샷은 **프로필**(면허)이 바뀔 때도 무효화돼야 한다. 7C④ 가 프로필 쓰기 경로를 연다. 그러면 무효화 지점이 둘로 늘어난다. legacy 가 무효화 호출을 빠뜨린 것과 같은 축이다(`docs/discovery/capability-map.md:546-552`). 동기 계산은 입력을 매 조회 새로 읽어서 이 축이 생기지 않는다.

**동기 계산에서 STR-07 「stale 표시」가 뜻하게 되는 것.** 서버 쪽에 낡을 스냅샷이 없다. 응답은 「그 응답을 낸 순간의 전략 판」으로 계산됐음을 0.6 의 비교로 보장한다. 그래서 서버는 **stale 결과를 내지 않는다**. 늘 `false` 인 `stale` 칸은 두지 않는다. 대신 응답 헤더에 계산 기준을 싣는다.

- `X-Strategy-Revision`: 계산에 쓴 전략 개정
- `X-Computed-At`: 주입 `Clock` 시각

화면(7E)이 저장 뒤 다시 조회하거나, `readStrategy` 개정과 비교해서 클라이언트 캐시가 낡았는지 판단한다. **acceptance 의 정직한 판정 방식**(팀장 확인 Q2): capability map 의 첫 시나리오(「스냅샷이 stale 로 표시되고 재계산이 디스패치된다」)는 형태를 옮겨 판정한다. 「스냅샷 없음 — 조회가 곧 재계산이다. 저장 직후 조회의 `X-Strategy-Revision` 이 새 개정이고, 결과가 새 전략을 반영한다」를 모든 쓰기 경로에서 test 로 잰다. 둘째 시나리오(「계산 중 저장 → 채택하지 않는다」)는 문면 그대로 잰다(0.6).

### 0.4 P2 — 인증 축: 기존 읽기 기본값, 비계정 operation

- 이 operation 이 읽는 전략은 V9 싱글턴이다(`adapters/src/main/resources/db/migration/V9__operator_strategy.sql`, `id SMALLINT PRIMARY KEY CHECK (id = 1)`). 프로필도 V12 싱글턴이다. 계정 범위 데이터가 아니다.
- 그래서 `readStrategy`·`searchNotices` 와 같은 **문서 기본 `security`**(자격 헤더 **또는** 세션 쿠키, 읽기라서 CSRF 없음)를 쓴다. 그 기본값은 `openapi/bidvector-operator-api.yaml`(base `bee7c36f` :19-29)에 있다.
- 7B 는 계정 전용 operation 을 `browserSession` **만** 적도록 하고, `AccountScopedRoutes.ROOTS` 와 집합 정확 일치로 잰다(7B 브랜치 `fc115d8b`, `reports/evidence/m7/7b/checklist.md:620-630`). 이 operation 은 **비계정**이라 그 집합에 넣지 않는다. 7B 가 먼저 병합돼도 「비계정 op 는 자격 헤더 대안을 남긴다」 단언과 맞는다.
- 새 경로는 기본 거부 필터의 와일드카드 등록 아래에서 태어난다. 7C① 이 변이로 확인한 구성이고(`reports/evidence/m7/7c1/checklist.md` 우회 ①), 기존 기계 전수 test(「등록된 모든 endpoint 가 자격증명 없이는 401」)가 자동으로 센다. 무인증 허용 목록은 바꾸지 않는다.
- R-2 의 남은 절반(싱글턴 → 계정, `milestone-7.md:92-97`)은 가져오지 않는다. 전략이 계정 단위가 되면 이 operation 은 그때 계정 전용으로 옮긴다(알려진 제한에 적는다).

### 0.5 P3 — 카드 형태 재사용

- **OpenAPI `$ref` 는 쓸 수 없다.** `OpenApiContractTest` 의 평탄 술어는 문서 **전체**의 속성 정의에서 `$ref`·`allOf`·`oneOf`·`anyOf`·`additionalProperties`·object 를 기본으로 거부한다(D-6A1-38, `app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt:83-98,469-482`). 7C① 은 그래서 본문을 **최상위 배열**(`items: $ref NoticeSummary`)로 두고 총수·상태를 헤더로 보냈다(`reports/evidence/m7/7c1/checklist.md` 1 절 「빈 결과의 두 뜻」 행).
- **제안 형태**:
  - 응답 본문은 최상위 배열 `items: $ref SuggestedNotice` 다.
  - `SuggestedNotice` 는 평탄 스키마다. `NoticeSummary` 의 12 속성을 **같은 정의로** 갖고, 자격 칸(0.8)을 더한다.
  - 헤더는 `X-Strategy-Revision` · `X-Computed-At` · `X-Total-Count`(게이트 통과 총수, 표시 상한 전) · `X-Result-State` 다.
  - 객체 봉투(`{revision, items:[…]}`)는 `items` 가 object 배열이라 평탄 게이트에 걸린다. 그래서 7C① 과 같은 헤더 관례를 따른다.
- **「같은 형태」는 test 로 잠근다.** 계약 test 에 「`SuggestedNotice.properties` 를 `NoticeSummary` 키로 제한한 것이 `NoticeSummary.properties` 와 **정의까지 같다**」를 둔다. 다르면 RED 다. 문서에 12 속성이 두 번 적히는 중복은 이 등식이 묶는다. 게이트 술어 자체(평탄 허용 목록)는 넓히지 않는다.
- **코드 매핑은 중복 없이 간다.** Kotlin DTO 는 `NoticeSummaryResponse.from(notice)`(7C① 이 만든 하나의 매핑) 결과를 `@JsonUnwrapped` 로 품고 자격 칸만 더한다. **미확인**: Jackson 3 + Kotlin 모듈에서 직렬화 쪽 `@JsonUnwrapped` 가 기대대로 평탄하게 나오는지는 Phase 2 첫 test 에서 확인한다. 안 되면 대안을 팀장에게 보고한다. 대안은 12 칸을 복사하지 않고 `from` 을 감싸는 작은 직렬화기다.
- 생성 타입(W-5)은 새 스키마 하나와 operation 하나만 늘고, 바이트 등식 재생성으로 맞춘다.

### 0.6 P4 — STR-07 「모든 편집 경로」

**오늘 코드의 전략 쓰기 경로 전수.**

| # | 경로 | 근거 |
|---|---|---|
| W1 | `operator_strategy` 쓰기 SQL(`UPSERT_STRATEGY`·`INSERT_STRATEGY_REVISION`)을 실행하는 곳은 `JdbcStrategyRepository.save` 하나다 | `adapters/.../strategy/JdbcStrategyRepository.kt:88-100`; `grep UPSERT_STRATEGY` main 소스 참조는 그 파일과 `Sql.kt` 정의뿐 |
| W2 | `StrategyRepository.save` 를 부르는 main 코드는 `EditStrategyWorkflow.process` 하나다 | `workflow/.../strategy/EditStrategyWorkflow.kt:156-160`; `PinnedStrategyRepository.save` 는 `error()` 다(`adapters/.../evaluation/PinnedStrategyRepository.kt:18-30`) |
| W3 | `save` 의 인자 `AppliedStrategy` 는 `internal constructor` 이고, 만드는 자리는 `onConfirm` 하나다. 그 자리는 개정을 +1 하고, 개정이 어긋나면 `StaleRevision` 으로 거부한다 | `workflow/.../strategy/Transition.kt:170-197`, `TransitionGuards.kt:114-118`, `Ports.kt:23-30` |
| W4 | `EditStrategyWorkflow` 를 조립하는 production 자리는 `JdbcStrategyEditTransaction` 하나다. HTTP 편집 세션 여섯 operation 중 상태를 적용하는 것은 `confirm` 뿐이다 | `adapters/.../strategy/StrategyEditTransaction.kt:50-61`, `Transition.kt:104-126` |
| — | Telegram 편집(STR-11/12)·온보딩 확정 반영·실험 자동 갱신(STR-15)은 V2 에 **없다**. 7C④ 도 전략 쪽에 아무것도 더하지 않는다고 적었다 | `docs/discovery/capability-map.md:3067`(OPEN-STR-12), STR-15 `근거 부족`(:690-710 부근); 7C④ 브랜치 `b6d6137b` `reports/evidence/m7/7c4/checklist.md:200-204` |
| 코드 밖 | 마이그레이션 DDL(예: V16 열 추가) · 리허설 도구의 `truncate`(`tools/db-rehearsal.sh:286`) · 수동 SQL | 쓰기 경로가 아니다. 다만 아래 방식은 이것들도 덮는다 |

**구조로 닫는 방식: 소비자 쪽 값 비교.** 쓰기 쪽 이벤트 구독은 고르지 않는다.

1. 요청 하나가 전략을 **한 번** 읽어 고정한다(`PinnedStrategyRepository` 관례). 프로필도 한 번 읽어 고정한다(0.3 제안).
2. 고정한 입력으로 게이트를 돌린다.
3. 끝나면 현재 전략(과 프로필)을 다시 읽어 **값 등식**(`OperatorStrategy` data class `==`)으로 비교한다.
4. 같으면 채택한다. 다르면 결과를 버리고 새 값으로 **한 번** 재계산한다. 또 다르면 409(새 코드, 예: `STRATEGY_CHANGED_DURING_COMPUTATION`)로 끝낸다. 지어낸 결과도, 낡은 결과도 내지 않는다.

왜 이것이 「모든 편집 경로」를 구조로 닫는가.

- 판정이 **쓰기 쪽이 무엇을 했는가**(무효화 호출 · 이벤트 발행 · 개정 +1)를 보지 않는다. **읽힌 값이 바뀌었는가**만 본다.
- 그래서 내일 Telegram·온보딩·실험 경로가 생겨도, 개정을 올리지 않는 수동 SQL 이 끼어도 같은 비교에 걸린다.
- 개정 번호만 비교하면 W3 의 규율(+1)에 기댄다. 값 등식은 그 규율에도 기대지 않는다. 개정은 헤더로 실어 「어느 판인가」를 알리는 데만 쓴다.

**test 계획(전 편집 경로 전수):**

- ⓐ HTTP 확정 경로로 저장한 뒤 GET 한다 → 헤더 개정이 새 값이고 목록이 새 감시 조건을 반영한다.
- ⓑ 워크플로를 거치지 않는 직접 SQL 쓰기 뒤 GET 한다 → 새 값을 반영한다(「미래의 다른 writer」의 대역).
- ⓒ 계산 중 변경: 첫 `load` 는 r, 비교 `load` 는 r+1 을 내는 가짜 reader 를 쓴다 → 첫 결과를 버리고 재계산한 결과의 개정이 r+1 인지 본다. 두 번 연속 바뀌면 409 이다.
- ⓓ 실 DB 경쟁: 계산 사이에 확정을 끼운다(가능하면).
- 변이: 비교를 항진식으로 바꾸면 ⓒ 가 RED 여야 한다. 비교를 개정 번호로만 바꾸면 ⓑ 의 「개정 미변경 값 변경」 꼴이 RED 여야 한다.

**알려진 한계.** 비교와 응답 전송 사이(마이크로초)에 커밋된 저장은 그 응답에 반영되지 않는다. 헤더 개정이 그것을 드러낸다. 화면은 저장한 뒤 다시 조회한다.

### 0.7 STR-06 — 표시 상한과 계산 예산의 독립 (새 operation 안)

- **표시 상한**은 전략 `candidateLimit` 이다. 게이트를 통과한 전체를 마감 임박 순으로 세운 **뒤에** 앞에서부터 자른다. 미설정(`null`)이면 자르지 않는다. 기본값을 지어내지 않고, 스캔 상한이 바깥 한계다. `X-Total-Count` 는 자르기 전 총수다.
- **계산 예산**은 운영 설정 `bidvector.evaluation.candidate-cap` 이다. 기본값이 없고 미설정이면 기동에 실패한다(`app/.../wiring/EvaluationWiring.kt:99-107`). 스캔 행 수와 게이트 호출 수를 정한다. 표시 상한은 후보 원천·게이트에 **전달되지 않는다**.
- 이 operation 은 ML 을 부르지 않는다. 그래서 「분석 예산」은 이 operation 에 존재하지 않는다. 계산 예산 = 스캔 상한이다.
- test: 전략이 `candidateLimit` 만 다르게(1 vs 5) 주어져도 후보 원천의 스캔 인자와 면허 게이트 호출 수가 **같고**, 본문 길이만 다르다. 변이: 표시 상한을 스캔 상한 자리에 넘기면 RED 다.
- 정렬은 후보 원천의 결정적 순서를 그대로 쓴다(`deadline_at ASC, notice_number, notice_round` — `adapters/.../persistence/Sql.kt:56-63`, D-6F2-5).

### 0.8 QUAL-02 의 한 조각 — 면허 축의 실제 판정만 싣는다

- 면허 게이트는 이미 구조화된 사유를 낸다. `Eligible(satisfiedGroups)` · `Ineligible(missingByGroup)` · `Uncertain(reason ∈ 다섯)` 이다(`qualification/src/main/kotlin/bidvector/qualification/LicenseVerdict.kt`). 지어낼 필요가 없다.
- 제안 목록은 「자격 통과」만 담는다. 그래서 카드에 실리는 값은 다음 둘이고, 이유 열거 값은 `UncertainReason` 의 이름을 그대로 쓴다.
  - `licenseVerdict ∈ {ELIGIBLE, UNCERTAIN}`
  - `licenseUncertainReason`(UNCERTAIN 일 때만 비어 있지 않음. 예: 프로필 미선언 → `OPERATOR_LICENSES_NOT_DECLARED`)
- 7C④ 병합 전에는 프로필이 대개 비어 있어서 전부 `UNCERTAIN` 으로 나온다. 정직한 값이다.
- **싣지 않는 것.**
  - 「❌ + 무엇이 없어서」(`missingByGroup`)는 `Ineligible` 이 이 목록에서 빠지므로 나타나지 않는다.
  - 지역(QUAL-08)·실적(QUAL-11)은 판정 자체가 없다.
  - 그래서 계약 문서에 「자격은 **면허 축만** 판정한다」를 적고, 화면이 이것을 「자격 통과」 전체로 표시하지 않게 한다.
- `OPEN-6A3-EVALUATION-DETAIL`(dry-run 후보별 사유, `reports/evidence/m6/6a3f3/scope.md:87`)은 닫지 않는다. 이 조각은 그 OPEN 의 면허 축 일부를 **다른 operation** 에서 보여 줄 뿐이다.
- **팀장 판단(Q3)**: 이 조각을 넣을지, 아니면 자격 칸 없이 「사유 없음 — 미구현」만 선언할지 결정해 주십시오. 넣는 쪽을 권한다.

### 0.9 빈 결과의 뜻 (`X-Result-State`, 제안 어휘)

| 값 | 뜻 |
|---|---|
| `MATCHED` | 게이트 통과가 1 건 이상 |
| `NO_OPEN_NOTICES` | 입찰 가능한 열린 공고가 0 건 |
| `WATCH_NOT_CONFIGURED` | 감시 조건 미설정. `WatchVerdict.NoGate` 라 전 후보가 감시 게이트에서 빠진다. legacy 가 이 상태에서 스캔을 하지 않던 것과 같은 결과다 |
| `NO_MATCH` | 열린 공고는 있으나 통과 0 건 |

7C① 의 `NOTHING_COLLECTED` 와 이름이 다른 이유가 있다. 이 목록의 원천은 「수집된 전체」가 아니라 「열린 후보」라서 뜻이 다르기 때문이다. 같은 이름을 다른 뜻으로 쓰지 않는다.

### 0.10 P5 — 범위 밖 (만들지 않는다)

- ML-01: ML 포트를 부르지 않는다. 판정·점수 칸이 없다.
- 7C③: 알림을 만들지 않는다. outbox 를 건드리지 않는다.
- STR-08: 감시 실행 이력·신규/지속/이탈 diff 를 만들지 않는다. 영속이 없다.
- 7E: 화면을 만들지 않는다.
- 쪽 넘김: 이 목록은 표시 상한으로 짧게 자른다.
- 지역·실적 자격 판정, 참가신청 마감 열(7C① 알려진 제한 1).
- R-2 계정화.
- F-1 의 dry-run/커밋 예산 결합 수정(Q1).

### 0.11 Phase 2 에 들어올 경로 (계약 in_scope 갱신 예정)

- **workflow**: 제안 use case 하나. 면허·감시·생애주기 세 게이트는 `EvaluateCandidatesUseCase` 의 private 함수를 **모듈 internal 공용 함수로 추출**해 두 use case 가 함께 쓴다. 복제하지 않는다. 추출은 거동이 그대로여야 하고, 기존 평가 test 전건이 그 증거다. **평가 핵심 파일을 만지므로 팀장 확인(Q4).**
- **app.wiring**: 조회기 하나 + 요청 스코프 조립. 프로필 고정 래퍼가 들어간다.
- **app.http**: 컨트롤러 하나 + DTO.
- 오류 매핑에 409 코드 하나.
- `openapi/bidvector-operator-api.yaml` 과 생성 타입.
- 게이트 계약 두 파일(`architecture-policy.properties` 의 HTTP 표면·포트 호출 쌍 등, `gate-tests.properties`). 7C① 5 절과 같은 이유로 필요하다.
- 추적표 STR-06 · STR-07 · QUAL-02 행, capability map.
- **adapters 새 파일 0 · 마이그레이션 0 · 새 설정 키 0**(스캔 상한은 기존 키).

### 0.12 팀장에게 필요한 것

- **Q1** F-1(dry-run·커밋의 `candidateLimit → analysisBudget` 결합과 「값싼 필터 앞 예산」 굶김)을 어떻게 처리할지 정해 주십시오. 셋 중 하나입니다.
  - (a) 이 slice 는 보고만 하고 별 slice 로 분리한다(**권장**: 커밋 경로는 outbox 쓰기 경로라 영향 판단이 따로 필요하다)
  - (b) 이 slice 에 포함한다
  - (c) 의도된 결합으로 보고 capability map 쪽 문면을 고친다
- **Q2** STR-07 첫 시나리오를 0.3 의 「옮긴 형태」로 판정하는 것을 승인해 주십시오.
- **Q3** QUAL-02 면허 축 조각을 포함할지 정해 주십시오.
- **Q4** 평가 use case 의 게이트 함수 추출(거동이 같은 리팩터)을 승인해 주십시오. 거절하면 대안은 제안 use case 가 `EvaluateCandidatesUseCase` 를 쓰지 않고 같은 도메인 함수(`isBiddable`·`WatchRules.evaluate`·`LicenseGatePort`)를 직접 엮는 것입니다. 이 경우 「드롭 순서」가 두 벌이 됩니다.
- 경로 이름은 `GET /api/suggestions`(operationId `listSuggestions`)로 잠정합니다. 바꿀 이름이 있으면 알려 주십시오.

---

## 1. 팀장 결정 D-7C1B-1 의 반영 (Phase 2)

| 결정 | 반영 |
|---|---|
| P1 동기 계산 · 마이그레이션 0 | `ListSuggestionsUseCase` 가 조회마다 값싼 게이트 셋만 돈다. 새 마이그레이션·새 표·새 설정 키 0 |
| P2 기존 읽기 기본 security | `listSuggestions` 는 `security` 를 적지 않는다(문서 기본값). 기본 거부 필터 아래에서 태어나고 `SuggestionEndpointTest` 가 401 을 직접 잰다 |
| P3 평탄 카드 + 12 속성 등식 + 신선도 헤더 | `SuggestedNotice` = `NoticeSummary` 12 속성(같은 정의) + 면허 축 셋. 등식은 `OpenApiSuggestionContractTest`. 헤더 넷(개정·계산 시각·총수·결과 상태) |
| P4 소비자 쪽 값 비교(1회 재계산 뒤 409) | **전략만 고정해 게이트에 넘긴다** → 게이트 → 전략·프로필을 다시 읽어 시도 첫머리에 읽은 값과 값 등식. 두 번 연속 다르면 409 `STRATEGY_CHANGED_DURING_COMPUTATION`. **프로필은 게이트에 고정되지 않는다** — 면허 게이트(`StoredRequirementLicenseGate`)가 후보마다 `operatorProfilePort.current()` 를 직접 읽는다. 시도 첫머리의 프로필 읽기는 비교의 기준값으로만 쓴다(§6-4 · §6-5, 수정 라운드 1 cr M-1 정정 — 앞 판 「전략·프로필을 고정」은 사실과 달랐다) |
| Q1 (a) F-1 은 별 slice | 이 slice 는 dry-run·커밋 조립을 건드리지 않았다. 제안 목록은 상한을 게이트 **뒤**에만 적용한다(아래 2 절 M1). F-1 의 현 거동은 특성화 test 가 고정한다 |
| Q2 STR-07 은 의도 둘을 재는 test 로 판정 | capability map 문면은 무편집. 추적표 STR-07 에 「스냅샷 없는 설계」를 적고 ⓐ 로 판정했다(근거 test 는 그 행). **이 판정은 검토 대상으로 표시한다** — 표의 규칙은 「의심되면 ⓒ」이고, ⓐ 는 Q2 승인에 기댄다. **수정 라운드 1(D-7C1B-2, cr H-1)**: 같은 표의 STR-03·STR-16 기준(「production 경로에서 재야 ⓐ」)에 맞춰 둘째 의도(계산 중 저장 불채택)도 출하 조립 + 실 DB 에서 잰다(E2E ③, §10). 그래서 ⓐ 를 유지하고, 남는 한계를 추적표 행에 옮겨 적었다 |
| Q3 면허 실값만 · 지역·실적은 판정 없음 | `licenseVerdict`·`licenseUncertainReason` 은 `LicenseVerdict` 값을 옮길 뿐이다. `unjudgedAxes` 는 7C④ 와 같은 어휘(`QUAL_08_REGION`·`QUAL_11_CONSTRUCTION_CAPACITY`)이고, 선언 표는 `SUGGESTION_UNJUDGED_AXES` 하나다 |
| Q4 특성화 먼저 · 추출은 별 refactor 커밋 | 특성화 test 커밋이 원래 코드 위에서 초록(4/4)인 것을 확인한 뒤, 추출 커밋을 따로 넣었다. 추출 뒤 workflow 전 test 486 초록 |
| `@JsonUnwrapped` 확인 | **쓰지 않았다(보고 항목).** 그 애노테이션은 `com.fasterxml.jackson.annotation` 패키지라 app 의 바깥 참조 허용 목록(모듈별 정확 패키지 등식)을 넓혀야 한다. 대신 **인터페이스 위임**(`NoticeCard by summary`)으로 같은 결과를 냈다. 직렬화가 평탄하다는 것은 응답 키 집합 등식(`SuggestionEndpointTest`·`OpenApiSuggestionContractTest`)이 잰다. `@JsonUnwrapped` 자체의 Jackson 3 거동은 재지 않았다 |

## 2. 변이 표 (각각 바꿔치우기 → 표적 재실행 → 사본 복원)

numstat 의 삭제 줄이 1 이상이어야 바꿔치우기다. M5·M8 은 줄을 지우기만 했다(더하기 0). 복원 뒤 `git status` 는 빈 출력이었다.

| # | 변이 | 막는 것 | RED 로 잰 test |
|---|---|---|---|
| M1 | 표시 상한을 게이트 **앞** 후보 목록에 적용(F-1 결합 이식) | STR-06 독립 · 굶김 | `ListSuggestionsUseCaseTest` STR-06 둘 |
| M2 | 계산 뒤 비교를 항진식으로(`false &&`) | STR-07 계산 중 변경 불채택 | `ListSuggestionsUseCaseTest` STR-07 넷 전부 |
| M3 | 비교를 전략 개정 번호만으로 | 쓰기 쪽 규율(개정 +1)에 기대지 않음 · 프로필 비교 | 「개정이 같아도 값이 바뀌면」 · 「프로필이 바뀌어도」 |
| M4 | 표시 상한을 적용하지 않음 | 상한은 전략 값 | use case STR-06 · `SuggestionEndpointTest` 표시 상한 |
| M5 | `WATCH_NOT_CONFIGURED` 갈래 삭제 | 빈 결과의 뜻 | use case 빈 결과의 뜻 |
| M6 | 재시도를 두 번으로 | 1회 재계산 뒤 409 | use case 409 |
| M7 | 미결 사유를 늘 `null` 로 | 면허 축 값을 싣는다(지어내지도 버리지도 않는다) | 계약 어휘 등식 · endpoint 카드 |
| M8 | 계약의 `SuggestedNotice.deadlineAt` 에서 `format` 삭제 | 같은 카드 형태 | 계약 「같은 정의」 등식 |
| M9 | use case 가 첫 읽은 입력을 캐시(`by lazy`) | 저장 뒤 옛 결과가 안 보임 · 모든 편집 경로 | `SuggestionProductionE2ETest` STR-07 ① · ② (출하 조립 + 실 DB) |

**수정 라운드 1 변이 일곱 + 통제군**(rebase 뒤 HEAD `1e46c616` — 코드 트리는 마지막 산출물 커밋 `19b34b6c` 와 같다). 필터는
제안 test 네 클래스 전수(`ListSuggestionsUseCaseTest` · `SuggestionProductionE2ETest` · `SuggestionEndpointTest` ·
`OpenApiSuggestionContractTest`)로 모든 변이에 같고, test task 마다 `--rerun` 이다. 통제군(변이 없음)은 31 건 실행 · exit 0 ·
실패 0 이다. 일곱 모두 numstat `1 1`(바꿔치우기) · exit 1 · 컴파일 오류 0 · 복원 뒤 `git status` 빈 출력이다. 같은 일곱을
rebase 전(base `a61ddff1`)에도 돌렸고 RED 집합이 같았다.

| # | 변이 | 막는 것 | RED 로 잰 test(실패 수) |
|---|---|---|---|
| M2′ | 계산 뒤 비교를 항진식으로(`false &&`) | 계산 중 변경 불채택(출하 조립 포함) | use case STR-07 넷 · E2E ③ 둘 · 문장 수 · 마감 순서(8) |
| M3′ | 비교를 전략 개정 번호만으로 | 개정을 올리지 않는 계산 중 변경 · 프로필 비교 | use case 「개정이 같아도」·「프로필이 바뀌면」 · **E2E ③ 둘**(4) — §9 의 14 행이 말한 「출하 조립에서도 잰다」의 실측 |
| M10 | use case 가 후보마다 프로필을 한 번 더 읽음 | 질의 예산 `5 + 2n + h` | E2E 문장 수(1) |
| M11 | 요건 행 질의를 헤더 유무와 무관하게 실행 | 질의 예산의 `h` 항 | E2E 문장 수(1) |
| M12 | 후보 질의 `ORDER BY deadline_at DESC` | 마감 임박 순(원천) | E2E 마감 순서(1) |
| M13 | use case 가 통과 목록을 뒤집음 | 마감 임박 순(use case) · 상한이 임박 쪽을 남김 | use case 셋 · endpoint 표시 상한 · E2E 마감 순서(5) |
| M14 | 비교는 하되 버리지 않음(`return null` → `Unit`) | cr M-4 「비교만 하고 채택」 | use case STR-07 넷(프로필 결과 오라클 포함) · E2E ③ 둘(6) |

## 3. (2b) 값 획득 축 — 새 public 표면 전수

| 새 표면 | 밖에 허락하는 것 | 허락하지 않는 것(구조) |
|---|---|---|
| `GET /api/suggestions` | 인증된 운영자의 제안 목록 읽기 | 쓰기 없음. 질의 매개변수를 읽지 않는다(계약 밖 이름·틀린 이스케이프도 200 — endpoint test). 미선언 메서드 405 · 406 은 기존 전수 test 가 덮는다 |
| `ListSuggestionsUseCase`(workflow) | `list()` 하나 | 쓰기 포트를 받지 않는다(`StrategyReader`·`OperatorProfilePort`·후보 원천·감시·면허). HTTP 층은 이 타입을 직접 받지 못한다(`app.http.denied-types`) — 조회기 경유만 |
| `Suggestion`·`SuggestionList`(workflow) | 결과 값 | 생성자가 `internal` 이다. `Suggestion` 은 `Ineligible` 을 거부한다 |
| `SuggestionState`·`UnjudgedQualificationAxis`·`SUGGESTION_UNJUDGED_AXES` | 어휘 | 값만 있다 |
| `StrategyChangedDuringComputationException` | 409 로 옮겨지는 실패 | 메시지는 응답에 실리지 않는다(매핑표의 고정 문구) |
| `screenCandidate`·`CandidateScreening`(workflow, **internal**) | 모듈 안 공용 게이트 셋 | 모듈 밖으로 나가지 않는다 |
| `SuggestionQuery`(app.wiring) | use case 위임 하나 | ② 층 허용 목록 등재 |
| `SuggestionWiring`(app.wiring) | 기존 빈으로 use case 조립 | 새 어댑터·새 설정 키 0 |
| `NoticeCard`(app.http) | 카드 속성 열둘의 읽기 | 행위 없음. `NoticeSummaryResponse` 가 구현한다(속성 `override` 만, 매핑 무변경) |
| `SuggestedNoticeResponse`(app.http) | 응답 직렬화 | 생성자 `private`, 만드는 길은 `from(Suggestion)` 하나. data class 가 아니다(자동 `equals` 없음) |
| `ErrorCode.STRATEGY_CHANGED_DURING_COMPUTATION` | 409 코드 | 계약 `ErrorBody.code` 열거에 등재 |

## 4. 게이트 계약 — 데이터만 움직였다 (술어 무편집)

| 파일 | 무엇을 | 왜 |
|---|---|---|
| `config/quality/architecture-policy.properties` | HTTP 허용 패키지(`workflow.suggestion`) · HTTP 허용 클래스·② 층(`SuggestionQuery`) · ① 층(`SuggestionWiring`) · ② 층 workflow 타입(use case·결과) · 주입 표면(`SuggestionQuery`·use case·`NoticeSummaryResponse`) · HTTP 거부 타입(use case) · API 표면 handler(`SuggestionController`) | 전부 집합 **등식**이라 새 타입이 목록에 없으면 RED. 거부 타입은 넓힌 것을 다시 판다 |
| `config/quality/gate-tests.properties` | test 다섯 등재(workflow 2 · app 3) | 등재 게이트가 「모집단 ∖ 제외 == 등재」 |
| app 의 의존 게이트 test | 면제 수 62 → 64(근거 문단 동반). **rebase 뒤 65 → 67** — main 의 7C⑤-0(#84)이 62 → 65 로 셋을 먼저 넣어 그 위에 둘을 얹었다(충돌 해소, 두 레인 문단 모두 보존) | ① 층 하나 + ② 층 하나 |
| `config/quality/architecture-policy.properties` `app.injection.allowed-types` += `NoticeSummaryResponse` | **오분류로 넓혔다(수정 라운드 1, cr L-1 — 앞 판은 위 첫 행 괄호 안에 묶어 「데이터만 움직였다」로 적었다).** `SuggestedNoticeResponse` 의 `private constructor(summary: NoticeSummaryResponse, …)` 매개변수를 INJECTION_SURFACE 규칙(`AppHttpDependencyRules.kt` 의 생성자 매개변수 타입 = 주입점)이 주입으로 셌다. 실제로는 **응답 DTO 합성**이고 빈 주입이 아니다 | 넓힌 것은 능력 포트가 아니라 응답 DTO 다 — 게이트의 목적(컨트롤러·② 층이 포트를 직접 쥐지 않는다)은 그대로다. 대가: 앞으로 그 층의 아무 클래스가 생성자로 `NoticeSummaryResponse` 를 받아도 게이트가 말하지 않는다. 코드로 피하려면 `Notice` 를 받게 바꿔야 하는데, 그러면 `Notice` 를 같은 목록에 넣어야 해서 이득이 없다 |

첫 전건 `check` 가 잡은 것 넷은 이렇게 고쳤다.
- detekt `ComplexInterface`: 근거를 적고 억제했다.
- `Intrinsics.areEqual`: 미결 사유의 `when` 을 `is` 로 바꿔 **허용 목록을 늘리지 않았다**.
- 주입 표면 · 면제 수: 위 표.

## 5. 쓰기·마이그레이션 0 확인

- 새 SQL 0. 새 어댑터 파일 0(`adapters/` 변경 없음).
- `adapters/src/main/resources/db/migration/` 무변경.
- 평가 use case 의 변경은 refactor 하나다. 거동 불변이고, 특성화 test 와 기존 평가 test 전건이 증거다.

## 6. 알려진 제한

1. **F-1 이 남아 있다** — dry-run·커밋의 `analysisBudget = candidateLimit` 결합과 「값싼 필터 앞 절단」. 별 slice 다. STR-06 은 ⓒ 이다.
2. **비교와 응답 전송 사이에 커밋된 저장**은 그 응답에 반영되지 않는다. `X-Strategy-Revision` 이 그것을 드러내고, 화면은 저장 뒤 다시 조회한다.
3. **계산 중 변경은 전략 쪽만 출하 조립 + 실 DB 에서 잰다(수정 라운드 1 로 바뀜).** 앞 판은 「실 DB 재현 없음」이었다. 지금은 `SuggestionE2EProbes` 가 출하 조립의 `strategyRepository` 빈을 감싸 그 GET 의 N 번째 `load()` 직후 실 SQL 로 전략을 바꾸고, 결과가 둘째 시도의 값인지 · 연속이면 409 인지를 잰다(§10). 남는 것: (a) **프로필**의 계산 중 변경은 여전히 use case test 의 가짜 포트만 잰다. (b) 끼어드는 자리는 `load()` 반환 직후로 결정적으로 고정했다 — 다른 스레드의 커밋이 임의 시점에 끼는 경쟁 자체를 재현한 것은 아니다.
4. **A-B-A 변경은 보지 못한다** — 계산 중에 바뀌었다가 같은 값으로 돌아오면 비교가 같다고 본다. 그 사이 면허 게이트가 중간 값을 읽었을 수 있다. 특히 **프로필은 게이트에 고정되지 않으므로**(§1 P4) 프로필이 A → B → A 로 움직이면 **한 목록 안에 프로필 A 로 판정된 카드와 B 로 판정된 카드가 섞일 수 있고**, 끝의 비교는 A == A 라 그 목록을 채택한다(cr M-1).
5. **면허 게이트는 후보마다 2 질의**(요건 헤더·프로필, 헤더가 있으면 +1)다. 프로필 고정 래퍼로 줄이는 제안(§0.3)은 하지 않았다 — 어댑터·wiring 하나가 늘고(「adapters 새 파일 0」 약속), 4 의 A-B-A 를 프로필 쪽에서 닫는 것도 그 래퍼 몫이다. **질의 예산은 이제 단언한다**: GET 한 번의 읽기 문장 수 `5 + 2n + h` 를 표별로 등식 단언한다(§0.3 정정 · §2 M10 · M11). 소요 시간은 여전히 관측값이다: 출하 조립 + 실 PostgreSQL 에서 통과 후보 200 건 조회 한 번이 184 ms(Phase 2 관측 한 번, 단언 아님).
6. **스캔 상한 초과는 목록 전체를 409 로 막는다** — 조용히 자르지 않는 대가다. 운영 값 `BIDVECTOR_APP_CANDIDATE_CAP` 는 **저장소에 기본값이 없다**: `docker/compose.yaml:91` 이 `${BIDVECTOR_APP_CANDIDATE_CAP:?…}` 라 미설정이면 기동을 거부한다. 저장소에 있는 값은 CI 의 1000(`.github/workflows/ci.yml:356`)과 runbook 예시 10(`docs/runbook/m6-6g-real-collection.md:38`)뿐이다. `.env` 는 읽지 않았다. cap 1000 이면 최악 GET 한 번이 `5 + 2·1000 + h` 문장(재계산 시 두 배)이고 그 소요는 재지 않았다.
7. **`unjudgedAxes` 선언 표가 7C④ 병합 뒤 둘이 된다** — 이름만 맞췄다. 병합 시 하나로 합친다.
8. **`Ineligible` 은 제안에서 빠지므로** 「❌ + 무엇이 없어서」는 이 목록에 나타나지 않는다. `OPEN-6A3-EVALUATION-DETAIL` 그대로다.
9. **전략이 계정 단위가 되면**(R-2 남은 절반) 이 operation 은 계정 전용 축으로 옮겨야 한다.
10. **참가신청 마감·쪽 넘김은 없다**(7C① 제한 1 과 같은 뿌리 · 짧은 목록 전제).
11. **컨테이너 스모크가 `/api/suggestions` 를 부르지 않는다** — 7C① 제한 11 과 같다.
12. **`X-Result-State` 의 `NO_MATCH` 는 빈 이유가 자격 때문인지 조건 때문인지 가르지 않는다**(cr L-3) — 열린 공고가 전부 면허 미달(`Ineligible`)로 떨어져도 감시 탈락과 같은 `NO_MATCH` 다. 어휘 넷은 승인된 설계(§0.9)라 이 slice 에서 바꾸지 않는다. **7E 와 함께 다룰 후속 후보**다.
13. **`X-Strategy-Revision` 비교는 개정을 올리는 쓰기 경로에만 유효하다**(cr L-5) — 서버 계산은 값 비교라 개정을 올리지 않는 경로(마이그레이션 DDL · 리허설 도구 · 수동 SQL, §0.6 「코드 밖」 행)도 덮지만, 계약이 화면에 권하는 「저장 뒤 `readStrategy` 개정과 비교」는 그 경로를 덮지 못한다. 클라이언트 캐시 무효화는 7E 소관이다.
14. **비교 범위는 이 operation 이 읽지 않는 필드까지 넓다(의도된 보수)** — 값 등식이 `OperatorStrategy` 전체(임계치·`maxActiveBids` 등)와 프로필 전체를 본다. 그 필드만 바뀐 편집이 계산 중에 끼면 결과가 같아도 버리고 1회 재계산한다(연속이면 409). 근거: 「무엇을 읽었는가」를 추적해 범위를 좁히면 게이트가 새 필드를 읽기 시작할 때 비교가 따라오지 않는 구멍이 생긴다. 넓게 비교하면 대가가 재계산 1회뿐이다. 프로필까지 비교하는 것은 면허 게이트가 프로필을 읽기 때문이다 — 전략만 비교하면 계산 중 프로필이 바뀐 결과를 채택한다.
15. **「감시 미설정」 술어가 두 자리다**(cr L-4) — 드롭 쪽 `WatchVerdict.NoGate`(`CandidateScreening`)와 상태 쪽 `watchRules.isEmpty()`(`ListSuggestionsUseCase.stateOf`). 오늘은 `WatchRules.evaluate` 첫 줄이 같은 조건이라 같다. 후속 후보: 상태를 드롭 사유 목록에서 도출해 술어를 하나로 둔다.

## 7. 추적표

`reports/evidence/m6/6e1/acceptance-trace.md` 를 고쳤다.
- STR-06: ⓒ 유지 + F-1 홀더.
- STR-07: ⓒ → **ⓐ**(Q2). 집계 16 → 17. 수정 라운드 1 에서 근거 test 에 E2E ③(출하 조립 + 실 DB, 계산 중 변경)을 더하고 남는 한계(A-B-A · 프로필 축 · 결정적 끼움 · 클라이언트 개정 비교)를 행에 적었다.
- QUAL-02: ⓒ 유지 + 부분 측정 추가.

capability map 은 **무편집**이다(Q2 「acceptance 문면은 고치지 말 것」).

## 8. 하네스 레인 변경

`git log --oneline bee7c36f..HEAD -- CLAUDE.md .claude/` → **없음**.

## 9. 약속 ↔ 출하 대조 (Phase 1 §0 설계 vs 출하 코드)

분류: **약속대로** · **선언된 이탈**(evidence 가 이탈과 이유를 적었다) · **미선언 이탈**(적지 않았다) · **약속 자체가 틀림**.
Phase 2 동결(`38af04b7`) 때 미선언이던 것은 그 사실을 남기고, 이 라운드의 처분을 함께 적는다.

| # | Phase 1 약속(§) | 출하 | 분류 |
|---|---|---|---|
| 1 | 조회 시 동기 계산, 게이트 셋(생애주기·감시·면허)만, ML·사다리·용량 미호출(§0.3) | `ListSuggestionsUseCase` 가 `screenCandidate` 만 부른다. ML·사다리·용량 포트를 받지 않는다 | 약속대로 |
| 2 | 마이그레이션 0 · adapters 새 파일 0 · 새 설정 키 0(§0.11) | 그대로(§5) | 약속대로 |
| 3 | 비용 「최악 `2 + 2·cap`」(§0.3 표) | 출하 식은 `5 + 2n + h` | **약속 자체가 틀림** — §0.3 아래 정정, test 로 단언 |
| 4 | 비용 증거 ① GET 한 번의 문장 수 단언(§0.3) | Phase 2 에는 없었다(`println` 시간뿐) | Phase 2 **미선언 이탈**(cr M-2) → 이 라운드에 이행: `SuggestionProductionE2ETest` 문장 수 test(식은 3 의 정정값) |
| 5 | 비용 증거 ② Testcontainers 시간 1회(§0.3) | 184 ms 관측 한 번 | 약속대로(관측값 — 단언 아님) |
| 6 | 프로필을 요청당 한 번 읽어 고정(§0.3 제안 · §0.6 1단계 · §0.11 「프로필 고정 래퍼가 들어간다」) | 넣지 않았다. 면허 게이트가 후보마다 프로필을 읽는다 | Phase 2 §6-5 는 비용 항목으로만 「하지 않았다」고 적었고 §1 P4 는 「전략·프로필을 고정」이라 적었다 — **입력 고정 쪽은 미선언 이탈**(cr M-1). 이 라운드: §1 P4 정정 + §6-4 에 섞인 카드 위험 명시 → **선언된 이탈** |
| 7 | 문서 기본 `security`, 계정 전용 아님(§0.4) | `listSuggestions` 는 `security` 를 적지 않는다 | 약속대로 |
| 8 | 평탄 `SuggestedNotice` + 「NoticeSummary 12 속성 정의 등식」 test(§0.5) | `OpenApiSuggestionContractTest` | 약속대로 |
| 9 | Kotlin 매핑은 `@JsonUnwrapped` 로 `NoticeSummaryResponse.from` 재사용(§0.5) | 인터페이스 위임(`NoticeCard by summary`) | 선언된 이탈(§1 — 패키지 허용 목록을 넓혀야 해서) |
| 10 | 신선도 헤더 넷(§0.5) | 넷 그대로, 계약 test 가 응답 헤더와 문서 선언을 대조 | 약속대로 |
| 11 | 소비자 쪽 값 비교 · 1회 재계산 · 그다음 409(§0.6) | `list() = attempt() ?: attempt() ?: throw` · `ComputationInputs` data class 등식 | 약속대로 |
| 12 | test ⓐ 확정 뒤 GET · ⓑ 직접 SQL 뒤 GET · ⓒ 가짜 reader 로 계산 중 변경(§0.6) | E2E ① · E2E ② · use case test 넷 | 약속대로 |
| 13 | test ⓓ 실 DB 경쟁 「가능하면」(§0.6) | Phase 2: 없음(§6-3 선언). 이 라운드: 출하 조립의 `strategyRepository` 빈을 감싸 `load()` 직후 실 SQL 로 바꾸는 E2E ③ 둘 | 선언된 이탈 — 끼우는 시점을 결정적으로 고정했다. 임의 시점의 스레드 경쟁은 아니다(§6-3) |
| 14 | 변이 「비교를 개정 번호로만 바꾸면 **ⓑ** 가 RED」(§0.6) | ⓑ(E2E ②)는 연속된 두 GET 이라 계산 중 비교를 지나지 않는다 — 그 변이에 초록이다 | **약속 자체가 틀림** — 그 변이를 잡는 것은 개정을 올리지 않고 **계산 중** 값을 바꾸는 자리다. Phase 2 는 use case test 로 잡았고(M3), 이 라운드에 출하 조립 E2E ③ 에서도 잰다(§2 M3′) |
| 15 | 표시 상한은 게이트 뒤, 미설정이면 자르지 않음, `X-Total-Count` 는 자르기 전 총수(§0.7) | 그대로 | 약속대로 |
| 16 | STR-06 test 「표시 상한만 다르면 **후보 원천의 스캔 인자**와 면허 게이트 호출 수가 같다」 · 변이 「표시 상한을 스캔 상한 자리에 넘기면 RED」(§0.7) | `openCandidates()` 에는 인자가 없다(스캔 상한은 어댑터 생성자 값). test 는 감시·면허 게이트 호출 **목록**의 등식을 잰다. 변이는 「표시 상한을 게이트 앞 후보 목록에 적용」(M1) | **약속 자체가 틀림**(넘길 인자 자리가 없다 — 표시 상한이 원천에 닿을 통로 자체가 없다). 잰 것은 그 대체이고 §2 에 적었다 |
| 17 | 정렬은 원천의 결정적 순서(§0.7), scope test 절 「정렬」 | 코드는 그대로. Phase 2 에는 마감이 다른 공고로 순서를 재는 test 가 없었다 | Phase 2 **미선언 이탈**(cr M-3) → 이 라운드 E2E 「마감 임박 순 · 상한이 마감 임박 쪽을 남긴다」 + 변이 둘(§2 M12 · M13) |
| 18 | 면허 축 실제 값만(`ELIGIBLE`/`UNCERTAIN` + 미결 사유)(§0.8) | 그대로 + `unjudgedAxes`(Q3 결정) | 약속대로(결정 반영 추가분 포함). 계약의 nullable+enum 은 이 라운드에 `null` 을 enum 에 더했다(cr L-2, C-4 관례) |
| 19 | 빈 결과 어휘 넷(§0.9) | 그대로 | 약속대로 — 감시·면허 탈락을 가르지 않는 한계는 §6-12 |
| 20 | 409 코드 하나(§0.11) | `STRATEGY_CHANGED_DURING_COMPUTATION` | 약속대로 |
| 21 | 비교~전송 사이 커밋은 반영되지 않음(§0.6 한계) | §6-2 | 약속대로 |
| 22 | F-1 은 보고만(§0.2, Q1 (a)) | dry-run·커밋 조립 무변경 + 특성화 test 가 결함인 채 고정 | 약속대로 |
| 23 | 게이트 함수 추출, 거동 불변(§0.11, Q4) | 특성화 커밋 → 추출 커밋 | 약속대로 |

## 10. 수정 라운드 1 (D-7C1B-2 · D-7C1B-3) — 발견별 처분

검토: code-reviewer `cr-7c1b` REQUEST_CHANGES(코드 차단급 0). verifier `v-7c1b` 는 빌드 락 대기 중 보고 없이 끝났다 — 팀장 결정으로 기다리지 않고 이 라운드를 돌렸다. 이 라운드 앞에 `origin/main` `a61ddff1`(PR #81, 컨테이너 스캔 면제 2줄) 위로 rebase 했다(충돌 0).
**둘째 rebase**(2026-10-23 22:41 KST, 팀장 지시): `origin/main` `56bb7474`(#82 · #83 · #84) 위로. 충돌 둘을 합집합으로 풀었다 —
(a) app 의존 게이트 test 의 면제 수: main 65 + 이 slice 2 = **67**, 근거 문단 둘 다 보존. (b) 추적표 집계 문단: main 의 G1-C03
문단을 보존하고 ⓒ 수는 이 slice 의 42 를 쓰되, 그 문단이 인용한 16 · 43 이 「그 라운드 시점 값」임을 한 줄 덧붙였다. 설정 둘
(`architecture-policy.properties` · `gate-tests.properties`)은 자동 병합이고 main 대비 더하기만이다(지운 줄은 목록 끝 쉼표 이음뿐).
rebase 전 SHA ↔ 뒤 SHA 는 `commands.md` 의 대응표에 있다. **main 은 그 뒤 다시 움직였다** — PR 직전 rebase 는 검토 뒤(팀장 지시).

| 발견 | 처분 |
|---|---|
| H-1 STR-07 ⓐ 의 둘째 의도가 출하 경로에서 재지지 않음 | **잰다 → ⓐ 유지.** `SuggestionProductionE2ETest` STR-07 ③ 둘. 데코레이터는 아래 「관측 장치」. 추적표 행에 남는 한계(A-B-A · 클라이언트 개정 비교 · 프로필 축은 가짜 포트만 · 결정적 끼움)를 적었다 |
| M-1 §1 P4 「프로필 고정」 문면 | (a) 정정. §1 P4 · §6-4(섞인 카드) |
| M-2 문장 수 단언 미이행·미선언 | 이행. 식은 `5 + 2n + h` 로 정정(§0.3 아래) · 헤더 유무를 섞어 두 식 모두 단언 |
| M-3 정렬 test 없음 | E2E D+1 · D+10 · D+30(섞어 넣음) 순서 + 표시 상한 2 가 D+1 · D+10 을 남김 · 총수 3. 변이 M12 · M13 |
| M-4 프로필 test 오라클이 호출 횟수 | 가짜 게이트가 프로필을 보고 판정(미선언 → 미달, 선언 → 충족), 첫 판정 직후 프로필이 바뀐다. 결과 `ids()` · 판정 값 · 게이트 호출 목록으로 단언. 변이 M14 |
| L-1 주입 허용 목록에 응답 DTO | §4 별 행 |
| L-2 nullable + enum 에 null 없음 | enum 에 `null`(C-4 관례, 같은 문서 `EditSession.field` 선례가 있다 — cr 의 「선례 없음」은 사실과 다르다). 계약 test 어휘 등식은 「구현이 낸 값 전수(ELIGIBLE 의 `null` 포함) == enum」. 생성 타입 재생성 · W-5 |
| L-3 `NO_MATCH` 합침 | §6-12(7E 와 함께 다룰 후속 후보) |
| L-4 술어 두 벌 | §6-15(후속 후보) |
| L-5 클라이언트 개정 비교 | §6-13 |
| 열린 질문: 운영 cap | §6-6(저장소 값만, `.env` 미열람) |
| 열린 질문: 비교 과잉 범위 | §6-14 |

**관측 장치**(`app/src/test/kotlin/bidvector/app/http/SuggestionE2EProbes.kt`, test 소스 전용). `productionApplication()` 에 초기화자 하나를 덧붙여 `BeanPostProcessor` 를 등록한다. 조립 함수·배선 클래스는 그대로다.

| 감싸는 빈 | 정의 자리 · 타입 | 그 빈을 받는 소비자 | 무장 구간 · 무장 중 거동 | 무장하지 않을 때 |
|---|---|---|---|---|
| `strategyRepository` | `PersistenceWiring.strategyRepository` · `StrategyRepository`(구현 `JdbcStrategyRepository`) | 이 빈을 참조하는 `app.wiring` 파일 전부 — `StrategyEditWiring` · `StrategyEditExecutor` · `EvaluationWiring` · `EvaluationDryRunFactory` · `EvaluationCommitWiring` · `SuggestionWiring`(`grep` 실측). `StrategyRepository`/`StrategyReader` 를 내는 `@Bean` 은 이것 하나다(dry-run·커밋의 `PinnedStrategyRepository` 는 빈이 아니라 요청 안 지역 객체) | STR-07 ③ test 의 GET 한 번. 무장 뒤 N 번째 `load()` 가 **돌아온 직후** 소유자 연결로 `UPDATE operator_strategy SET required_keyword_terms = …`(개정 그대로)를 돌린다. 그 `load()` 의 반환값은 바뀌기 전 값이다. 해제가 무장 구간의 `load()` 횟수를 낸다(4 단언 — 감싼 빈이 제안 경로의 그 reader 라는 증거) | 위임만 |
| `dataSource` | `PersistenceWiring.dataSource` · `DataSource`(구현 `HikariDataSource`, 컨텍스트의 유일한 `DataSource`) | 모든 JDBC 어댑터 | 문장 수 test 의 GET 한 번. 내준 연결의 `prepareStatement`·`prepareCall` 과 `createStatement` 문의 `execute*`·`addBatch` SQL 을 적는다. 셈은 `SELECT … FROM <표>` 만, 표별로(감사 INSERT 등 쓰기는 세지 않는다) | 위임만(종료 시 `close` 도 풀에 위임) |
