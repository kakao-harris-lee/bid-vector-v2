# M6/6G-2d — 추출·실행 상태의 데이터 정확성 셋 + 실수집 강건성 넷 (계약, 착수 2026-09-30)

> **지위: 착수(2026-09-30).** base `c357e437`(PR #50 6G 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2d`, 브랜치 `m6-6g2d/2026-09-30`. 초안 이력: `docs/m6-6g2-contracts` 브랜치.
> **초안 시점 문면:** 6G 표적 재검증(판정 SHA `ceb9990f`)이 D-6G-76 「이 수정이 새 high 를 만들어도 멈춘다」로 끝나며 남긴 데이터 정확성
> 결함 셋을 닫는 slice 다. 운영자 결정 2026-09-30: **6G 머지 뒤 첫 후속 slice — 6G-2a 보다 먼저.** 착수 때 base 와 「착수 실측」을 채운다.
> 수령하는 OPEN: **`OPEN-6G-RUN-STATE-HEAL-ORDER`** · **`OPEN-6G-LIST-AXIS-WALK-SELECTION`** · **`OPEN-6G-LEGACY-AXIS-LINE`**(6G 계약 D-6G-78) + PR #50 `/code-review` 넷(6G D-6G-82 → D-6G2d-8).
> **실 KONEPS 수집은 이 slice 가 머지되기 전에는 시작하지 않는다**(6G D-6G-77).

- base: **`c357e437`**.
- 레인: `kotlin-implementer` 하나. Python 변경 없음 — 스냅숏 스키마 칸·golden 바이트 불변(`ml-engine/**` diff 0 이 기대값).

## 왜 이 slice 인가

6G 의 표적 수정(D-6G-68~74)은 r5 H-1(끊긴 걷기의 행이 완료 행으로 실림)을 닫았다 — 출하 조립 E2E 에서 재현되지 않는다. 그러나 같은
수정이 셋을 만들었고, 셋 다 실수집의 **값 또는 회계**에 닿는다(D-6G-65 데이터 정확성).

1. **복구 순서(D-6G-70 미이행).** `RunStateDirectory` 는 `LedgerDigest` 를 `init` 의 `healTornTail()` 보다 **먼저** 짓는다. 찢어진 끝 줄을
   고친 기동 A 는 복구 **전** 바이트의 해시를 `state.json` 에 굳히고, 기동 B 부터 「앞부분이 장부와 다르다」로 영구 거부된다(verifier
   r5-t probe C2: 기동 2·3 REJECT). 벗어나는 길은 디렉터리를 비우는 것뿐이고 그러면 **승인 호출 상한이 0 에서 다시 센다** — 실제로 나간
   호출 수와 상한이 어긋나는 바로 그 항목이다. 복구 쓰기 자체도 제자리 truncate+rewrite 라 복구 도중 두 번째 크래시가 원장을 줄인다
   (code-review r5-t M-3).
2. **목록 축의 걷기 선별 소실.** AXIS 결말 줄이 없는 축(`NOTICE_LIST` · `OPENING_RESULT_LIST`)은 `collectWalkRow` 가 아무 선별 없이
   전부 모으고 조립이 `firstOrNull()` 을 `ORDER BY inserted_at` 위에서 취한다 → **가장 먼저 적재된 관측**이 쓰인다. 02142854 판의
   `collectLatestWalk` 는 이 축도 가장 늦은 걷기를 골랐다(D-6G-58 r4-d). 개발 DB 에는 6F-8·6F-9 실수집이 남긴 공고 목록 원문(2026-08-25~
   2026-09-24, `raw_observation` 67,917 행)이 있고 그 행들은 6G 가 계약에 더한 `bidNtceDt`·`sucsfbidMthdCd/Nm` 을 **싣지 않는다**.
   6G 수집 범위는 그 한 달을 덮으므로 표본 공고의 공고일·낙찰방법이 null 이 되고 Python 이 공고일 결측으로 **통째로** 뺀다 — 날짜로
   몰린 비랜덤 제외이고 사유도 틀린다(verifier r5-t probe W6b: `ceb9990f` 는 `noticedOn=null`, `02142854` 는 `2026-06-03`).
3. **옛 형식 AXIS 줄.** `walk` 칸이 생기기 전에 쓰인 `AXIS`/`SUCCEEDED` 줄은 `AxisConclusion(settled=true, walk=null)` 이 되어 그 축의
   행은 전부 버리면서 축은 **완료**로 센다 — 축이 통째로 빠진 완료 행이 나오고 `incomplete_axis` 는 0 이다(probe W7). `state.json` 에
   형식 version 이 없어 옛 디렉터리가 그대로 기동한다. 실수집이 아직 없으므로 오늘 발화하지 않지만, 그 전제가 코드에도 evidence 에도
   없다(code-review r5-t H-2). 같은 뿌리: 「빈 응답으로 정착」과 「걷기를 모름」이 같은 값이라 결측 사유가 틀린 칸으로 간다(M-2) ·
   `CollectionAttempt.walk` 의 기본값 `null` 이 AXIS 줄의 빠뜨림을 조용히 허용한다(L-1) · `lineOf` 주석이 사실과 다르다(L-2).

## 실수집·백테스트와의 관계

- **실수집은 이 slice 머지 뒤에만.** 지금은 실행 상태 디렉터리가 하나도 없다 — 실행 상태 **형식**을 바꾸는 값싼 유일한 때다. 6G-2c 의
  「실행 상태 형식에 닿는 항목은 실수집·추출 뒤에만 머지」는 이 slice 뒤의 실수집 시작을 기준으로 읽는다.
- 이 slice 가 바꾸는 실수집 값은 기대한 방향뿐이다(목록 축이 **최신** 관측을 쓴다). 6G evidence 는 되쓰지 않고 이 slice evidence 에서
  정정을 선언한다(6G-2a D-6G2a-8 과 같은 규율).

## 착수 실측 (착수 시 채운다)

| 항목 | 값 |
|---|---|
| base SHA | `c357e437` |
| Kotlin `check` test 수 | 2,513 · skipped 4 · failures 0(verifier r5-t, `ceb9990f` — main 은 그 위 문서 커밋뿐) |
| probe C2 기동 세 번(찢어진 끝 줄 → A·B·C) | A ACCEPT · B·C REJECT(verifier r5-t) — 레인이 어댑터 test 로 승격하며 base 에서 RED 를 먼저 확인 |
| probe W6b(옛 공고 목록 행 뒤 6G 행) | `noticedOn=null · method=null`(verifier r5-t) |
| probe W7(walk 없는 SUCCEEDED 줄) | 행 0 · 축 완료(verifier r5-t) |
| `SourceBatch(` 생성 자리(production) | 2 — 둘 다 `KonepsPageUriBuilder`(빈 배치 하나 · 쪽 배치 하나). 팀장 grep; 빈 배치가 walk 를 싣는지는 레인이 실측해 D-4 ⓓ 를 정한다 |
| `CollectionAttempt(` 생성 자리(production) | 3 — `FileAttemptLedger`(읽기 복원) · `KonepsCallGate`(HTTP 줄) · `CollectOpeningResultsUseCase`(AXIS 줄). 팀장 grep |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| verifier r5-t probe `R6RunStateProbeTest` · `R6ListAxisProbeTest` · `R6WalkProbeTest`(실 Postgres) | **어댑터 test 로 승격** | 재현 절차가 리포트에 있다. scratchpad 는 정전으로 사라졌으므로 `verifier-r5-targeted.md` 의 기술로 다시 쓴다 |
| `RunStateDirectory.recordState()` 의 staged + `ATOMIC_MOVE` | **채택** | 복구 쓰기를 같은 형태로 — 이미 있는 원자 쓰기 하나를 두 자리가 쓴다 |
| 02142854 판 `collectLatestWalk` 의 「가장 늦은 `observed_at` 만」 갈래 | **되살림(결말 줄 없는 축에만)** | 벽시계 짐작을 없앤 것은 결말 줄이 **있는** 축의 이야기다. 결말 줄이 없는 축에는 원장이 줄 답이 없다 |
| `FileAttemptLedger.lineOf` 가 `walk` 키를 언제나 싣는 사실(code-review r5-t L-2) | **채택** | `containsKey("walk")` 가 옛 줄과 새 줄을 실제로 가른다 — 옛 줄 거부가 구현 가능하다 |
| 목록 축에도 AXIS 결말 줄을 남기기 | **기각** | 목록 갈래는 범위 재실행이 멱등인 설계이고 공고 단위 축이 아니다 — 원장 결말이 뜻을 갖지 않는다 |
| 스냅숏 사유 어휘 확장(`AXIS_EMPTY` 등) | **이 slice 밖** | 스키마 bump + Python + golden 재생성이다. 운영자 결정 A-2 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2d-1** | **누적 해시는 복구 뒤에 짓는다.** `LedgerDigest` 를 `init` 안에서 `healTornTail()` **다음에** 만들거나, 복구가 파일을 바꾸면 다시 짓는다. test 는 **기동 셋**을 연다: 찢어진 끝 줄 → 기동 A 수락·`spend` +1 → append → 기동 B 수락 → 기동 C 수락, `state.json` 의 해시가 디스크 바이트와 같다. 변이: 순서를 되돌림 → RED | 한 번만 여는 test 가 이 결함을 못 봤다 |
| **D-6G2d-2** | **복구 쓰기는 staged + `ATOMIC_MOVE`.** `recordState()` 와 같은 형태. staged 이름을 장부 대상 집합 등식의 제외 목록에 등재(지금은 `STAGED_STATE_NAME` 하나). test: 복구 도중 크래시를 흉내 내어 staged 파일이 남은 디렉터리 → 다음 기동 수락 · 원장 줄 수 불변 · 「모르는 파일」 거부 없음. 변이: 제자리 쓰기로 → RED | 복구가 도는 순간은 방금 죽은 기계 위다 |
| **D-6G2d-3** | **결말 줄이 없는 축은 가장 늦은 걷기의 행만.** `conclusion == null` 갈래에서 (공고, 축)마다 `observed_at` 최대 집합만 남긴다(한 걷기의 모든 쪽이 같은 값 — D-6G-58 r4-d 복원). 순서 기준은 **`observed_at`** 이지 `inserted_at` 이 아니다. 어댑터 test: 같은 공고의 목록 관측 둘(값이 다름) → 뒤 값 · **6F-8 형식 행(6G 칸 없음) 뒤 6G 행** → `noticed_on`·`successful_bid_method_code` 가 채워진다 · `observed_at` 이 늦고 `inserted_at` 이 이른 행(backfill)을 심어도 `observed_at` 이 이긴다. 변이: `firstOrNull()` 로 되돌림 → RED · 기준을 `inserted_at` 으로 → RED | 실수집 표본에서 실제로 발화하는 결함이다 |
| **D-6G2d-4** | **옛 형식은 fail-closed.** ⓐ `state.json` 에 `format_version` 을 두고, 없거나 다르면 기동 거부(닫힌 사유 코드, `ALREADY_RUNNING` 과 구별). ⓑ `walk` 키가 없는(값이 `null` 인) AXIS 줄은 **형태 위반**으로 읽기 거부 — `AxisConclusion.walk` 는 non-null. ⓒ `CollectionAttempt.walk` 의 기본값을 없애고 `init` 이 양방향을 요구한다(AXIS 줄은 walk 필수 · 그 밖은 금지). ⓓ `SourceBatch.observedAt` 의 기본값은 착수 실측(채우지 않는 포트 구현 수)으로 정한다 — 0 이면 없앤다. 변이: walk 없는 AXIS 줄이 수락됨 → RED · version 없는 `state.json` 이 기동됨 → RED | 「옛 디렉터리는 없다」는 전제를 코드가 스스로 지킨다 — 실수집 전이라 옛 형식을 관용할 이유가 없다 |
| **D-6G2d-5** | **「정착했으나 0 행」의 사유 귀속은 등재로 닫는다(스키마 무변경).** (a) 모든 상세 축이 빈 응답 → 지금 거동대로 `sampled_without_detail` 에 계수, (b) 일부만 → 행이 조립되고 Python 행 단위 제외(`BIDDER_AMOUNT_ABSENT`)로 떨어진다 — 둘 다 **잘린 값이 채점에 들어가지 않는다**(6G verifier r5-t E2E). 이 slice 는 두 경우를 알려진 제한으로 적고 `OPEN-6G2D-EMPTY-AXIS-REASON` 을 신설한다. 백테스트 판정 보고는 그 두 계수를 함께 공시한다 | 정확한 사유 어휘는 `schema_version` 인상이라 Python 레인·golden 재생성이 따라온다 — 운영자 결정 A-2 |
| **D-6G2d-6** | **게이트 술어 확장 0 · 새 public 표면 0 · `ml-engine/**` diff 0 · golden 바이트 불변.** `config/quality/**` 변경은 새 타입 **등재**만 허용된다. 어긋나면 멈추고 보고 | 데이터 정확성 slice 가 게이트 범위를 넓히면 범위가 샌다 |
| **D-6G2d-8** | **PR #50 `/code-review` 가 더한 넷(6G D-6G-82) — 실수집 전에 닫는다.** ⓐ 원문 `bidNtceNo` 가 빈 행: `NoticeNumber.of` 를 무방비로 부르지 않고 **행 단위 이름 있는 제외**(계수 공시)로 — 추출과 `JdbcCollectedAxisStore` 둘 다. 변이: 빈 번호 행을 심으면 추출이 던짐 → RED ⓑ raw append 와 AXIS 결말 사이의 크래시: 그 축의 PENDING/HTTP 줄이 있으면 「원장 시대 · 미정착」으로 읽어 **재호출 대상**으로(원장 이전 원문과 구별). 변이: 결말 없는 원장 시대 축이 「수집됨」으로 → RED ⓒ 결정적 실패(INPUT_ERROR · NOT_RETRYABLE · 구조 실패 · MAX_PAGES)는 **실패 확정으로 정착**하고 사유별 계수, 일시 실패(5xx·타임아웃·상한 거부·SHORT_WALK)만 재호출 — 재호출 상한(예: 3회)은 정책 값으로 두고 넘으면 확정. 변이: 결정적 실패가 둘째 기동에서 재호출됨 → RED ⓓ `jsonAmount` 는 소수 금액을 **행 단위 제외**(이름 있는 사유)로 — 추출 전체를 멈추지 않는다. 변이: 소수 금액 한 행 → 추출 전체 실패 → RED. ⓐⓓ 의 새 사유는 스키마 칸이 아니라 manifest 제외 계수 어휘의 추가라면 A-2 와 함께 결정 | 넷 다 실데이터에서만 드러나고 실수집 값·회계·강건성에 닿는다(verifier 정적 판독) |
| **D-6G2d-7** | **6G evidence 정정 선언.** 6G `commands.md` 의 D-6G-70 행 · D-6G-68 「원장 이전 원문은 D-6G-58 그대로」 행이 어느 전제 위의 서술이었는지 이 slice evidence 에 한 문단 | 닫힌 slice 의 evidence 를 되쓰지 않는다 |

## 계약 갱신 r0-b (2026-09-30, 팀장 — Kotlin 레인 완료 보고 수령: 이탈 넷 · 새 public 표면 여덟 · OPEN 둘)

레인 HEAD `2d2a5548`(산출물 `21d979e9`), acceptance `check` job 넷 exit 0 · test XML 2,527 · rollback D1/M33 실측. 아래는 검증 전 팀장 처분이고, 각 항목은 verifier 표적이다.

| ID | 결정 |
|---|---|
| **D-6G2d-9** | **in_scope 에 넷을 더한다(레인 이탈 1 수용)**: `adapters/.../koneps/KonepsPageUriBuilder.kt` · `adapters/.../koneps/KonepsCallGate.kt`(D-4 ⓒⓓ 기본값 제거의 기계적 귀결, 각 한 자리) · `adapters/.../persistence/JdbcCollectedAxisStore.kt`(D-8 ⓐ 결정문이 이름으로 든 자리 — 착수 계약의 경로 누락, 6A-3·6G 교훈 재발) · `app/src/main/.../collection/SnapshotExtractionRunner.kt`(D-8 ⓐ 계수 로그 한 칸). **전송 거동 무변경**이 조건이다 — verifier 는 koneps 둘의 diff 가 생성자 인자 전달 외 아무것도 아님을 확인한다 |
| **D-6G2d-10** | **구조 붕괴(StructureFailure)는 일시 실패로 둔다(레인 이탈 2 수용).** 이 저장소에서 HTTP 5xx 가 봉투 없이 그 사유로 오므로, 확정으로 두면 일시적 5xx 한 번이 축을 영구히 버린다. 결정적 실패 = INPUT_ERROR · NOT_RETRYABLE · MAX_PAGES 셋. 영구적인 구조 붕괴는 **재호출 상한(D-11)에서 확정**된다 — verifier 는 「매번 구조 붕괴인 축이 상한 뒤 확정되고 더 호출되지 않는다」를 mock 요청 수로 잰다 |
| **D-6G2d-11** | **재호출 상한은 `DetailFetchGates.axisRetryLimit`(기본값 없음)에 둔다(레인 이탈 3 수용)** — 수집 use case 는 `KonepsCollectionPolicyData` 의 멤버를 읽을 수 없고 구조 게이트가 그것을 막았다(게이트 무확장). 배선이 넣는 값 **3 은 잠정**이다 — 운영자 승인 표(6G A-1~5)에 없다 → `OPEN-6G2D-AXIS-RETRY-LIMIT`, 이 slice 종결 보고에서 운영자 결정 항목(A-3)으로 올린다. verifier 는 상한 값이 배선 한 자리에서만 오고 리터럴이 use case 에 없음을 확인한다 |
| **D-6G2d-12** | **`architecture-policy.properties` 의 변경은 `AxisConclusion` 등재 해제 한 줄이다(레인 이탈 4 수용)** — 허용 집합 == 관측 집합 등식에서 use case 가 더는 그 타입을 쓰지 않으므로 좁히는 방향이다. 술어 무변경 — verifier 확인 |
| **D-6G2d-13** | **새 public 표면 여덟은 D-6G2d-6 「0」의 이탈이며 (2b) 값 획득 축으로 verifier 가 전수한다.** 좁히는 여섯(`SourceBatch.observedAt` 비널 · `CollectionAttempt.walk` 기본값 제거+양방향 · `AxisConclusion` 비널 걷기 · `AttemptHistory.settledAxes` 제거/`axisResumptions` 신설 · `DetailFetchGates.axisRetryLimit` · `NoticeNumber.ofOrNull`)과 새 값을 나르는 둘(`AttemptOutcome.FinalFailure` · `SnapshotExtraction.unusableRawRows`) 각각에 「밖에서 무엇을 할 수 있는가」. 특히 `FinalFailure` 를 밖에서 지어 원장에 쓰면 축이 영구 확정되는가(누가 쓸 수 있는가) · `ofOrNull` 이 정규화 우회를 여는가 |
| **D-6G2d-14** | **`container` job 도 acceptance 다.** 레인은 돌리지 않았다(실행 상태 E2E 가 `check` 안이라는 이유) — verifier 가 판정 SHA 에서 한 번 돌린다(자기 compose 프로젝트, 다른 프로젝트 불간섭) |

**OPEN 신설**: `OPEN-6G2D-EMPTY-AXIS-REASON`(계약 예고) · `OPEN-6G2D-AXIS-RETRY-LIMIT`(잠정값 3, 운영자 결정 A-3). **판정 SHA 는 이 갱신 커밋**이다(레인 동결 유지, 코드 0).

## 계약 갱신 r1 (2026-09-30, 팀장 — verifier r1 not-ready H-1·M-1·L · code-reviewer r1 H-1·H-2·M-1~3·L-1~4 수령)

판정 SHA `2a1bb88b`. 통과: 기동 셋·복구 도중 크래시·옛 디렉터리 거부(MISSING) · W6b 최신 걷기 · 빈 번호 `unusableRawRows` · 결정적 실패 1회 · 변이 13 RED · (2b) 여덟 표면 새 권한 없음 · acceptance 넷 exit 0(test XML 2,535) · container job 전 단계 exit 0 · rollback ①~⑥. 막는 것 하나, 고칠 것 셋. **재작업 1/5.**

| ID | 결정 |
|---|---|
| **D-6G2d-15** | **(vr r1 H-1 · cr r1 H-2 — 데이터 정확성) 소수 금액은 집계 전체를 null 로.** `jsonAmount` 가 여섯 금액 칸 중 `a_value.total`·`reserve_prices[i]` 에서 `null` 을 내면 스키마 §2.2(`total:int` · `int[15] | null`)를 어겨 Python `_a_value`·`_numbers` 가 `INVALID_VALUE` 로 **스냅숏 전체**를 거부한다. 처방: 소수부가 있는 값이 집계에 들어가면 **그 집계 자체를 null**(`a_value: null` · `reserve_prices: null`)로 내어 기존 행 단위 제외로 떨어뜨린다. `parseAmount` 에서 거르지 않는다(`aValueTotalOf` 의 `mapNotNull` 이 A 를 조용히 줄인다). 여섯 칸 전부에 **스키마 계약 test**(렌더 결과가 §2.2 형태 집합 안) + 소수 금액 발생 수를 러너 로그에 공시(manifest 어휘 추가 없음, A-2). 변이: 집계 안 원소만 null → RED · `parseAmount` 절삭 → RED |
| **D-6G2d-16** | **(cr r1 H-1 · vr r1 M-1 — 데이터 정확성) 관문 거부는 상한에 세지 않는다.** `SelfThrottled`·`BudgetExhausted`·`QuotaExhausted` 는 HTTP 가 나가기 전에 접히는 거부다 — `Failed` 가 아니라 **넷째 결말 어휘 `Refused`**(또는 상한 셈에서 구조적으로 제외되는 형태)로 원장에 싣고 `doneWith` 는 **실제로 나간 호출의 일시 실패**만 센다. 계약 ⓒ 「상한 거부는 재호출」의 이행이다. 변이: 거부를 셈 → RED(verifier 판: 오늘치 소진 상태로 세 번 기동 뒤 예산 있는 네 번째 기동에서 그 축이 **호출된다**, mock 요청 수) |
| **D-6G2d-17** | **(cr r1 M-1) 결정적 실패는 정확히 셋** — INPUT_ERROR · NOT_RETRYABLE · MAX_PAGES. `RepeatedPage`·`Unclassified` 는 **일시**로 두고 상한이 확정한다(미지 사유 한 번에 영구 확정하지 않는다). 집합은 test 가 등식으로 잠근다. 변이: `Unclassified` 를 확정으로 → RED |
| **D-6G2d-18** | **일괄(값싼 것)**: cr M-2 `unusableRawRows` 의 KDoc·러너 로그가 세 원인(빈 번호 · 형태 어긴 차수 · 열거 밖 엔드포인트)을 정확히 말하게(이름 유지) · cr M-3 형식 거부가 러너 종료 코드·로그에 닫힌 어휘로 나오게(디렉터리 열기를 bean 생성이 아니라 러너의 `catch` 안으로 — 불가하면 이탈로 사유 기록) · vr L `format_version` 은 **정수만**(문자열·선행 0 거부, `1.0` 은 MISMATCHED) · `CollectionAttemptLedger.kt` 낡은 KDoc 넷 · 중복 하한 `require` 는 한 자리로(도메인 질의 쪽 유지, 사유 한 줄) · cr L test 대역의 `FinalFailure("STRUCTURE_FAILURE")` 조합 제거 · evidence 의 `private`/`internal` 표기 정정 |
| **D-6G2d-19** | **재호출 상한의 셈 창을 등재한다(레인 자기 보고).** `doneWith` 는 실행 상태 디렉터리 **생애 전체**에 걸쳐 그 (공고, 축)의 일시 실패를 누적한다 — 하루치도 실행치도 아니다. 알려진 제한 (e) 에 이 문장을 넣고 `OPEN-6G2D-AXIS-RETRY-LIMIT` 의 운영자 결정 A-3 은 **값과 창**(디렉터리 생애 / 일 단위)을 함께 묻는다. 이 라운드에서 창은 바꾸지 않되 **(r1 추가, vr r1 M-2)** 셈은 그 (공고, 축)의 **마지막 정착 결말(성공·빈 응답) 뒤**의 일시 실패만 — 정착 앞의 실패는 세지 않는다(`doneWith` 가 `at` 순서를 읽는다). 등식 test 하나. 옛 PENDING 줄이 올바른 version 아래 수락되어 +1 소비·재호출로 흐르는 것은 보수 방향이라 등재만 |
| **D-6G2d-20** | **재검증은 표적**: D-15 여섯 칸 스키마 계약 + Python 판독 규칙 대조 · D-16 mock 요청 수 판 · D-17 등식 · D-18 각 항목 · acceptance(`check` job 넷 + container) · rollback 재실측 · 새 public 표면(수정이 만든 것) 갱신. code-reviewer 는 수정 diff 를 본다 |

## 계약 갱신 r1-b (2026-09-30, 팀장 — 수정 라운드 1 보고 수령: HEAD `8448f9f2` · 이탈 넷 · 새 알려진 제한 둘)

| ID | 결정 |
|---|---|
| **D-6G2d-21** | **(레인 보고 — D-15 와 같은 계열, 데이터 정확성) `a_value` 는 전부 아니면 무.** ⓐ A 구성 항목은 있는데 `open_at`(공개일시)이 없는 원문 → 스키마(`open_at` 비널)를 어겨 Python 이 스냅숏 전체를 거부한다 → **`a_value: null`** 로 낸다. ⓑ `aValueTotalOf` 의 `mapNotNull` 은 결측 항목을 빼고 합산해 **A 를 조용히 줄인다**(6G 부채) → 구성 항목 하나라도 결측이면 **`a_value: null`**(전부 아니면 무). 둘 다 기존 행 단위 제외(A 부재)로 떨어진다 — 사유 어휘 추가 없음(A-2). 스키마 계약 test 에 두 경우 추가, 발생 수를 러너 로그 계수(`fractionalAmounts` 와 별도 칸 또는 합산 사유 명시). 변이: 항목 하나 결측에 `total` 이 나옴 → RED · `open_at` 결측에 `a_value` 가 나옴 → RED |
| **D-6G2d-22** | **이탈 수용 둘**: ① D-18 「형식 거부의 러너 종료 코드」는 하지 않는다 — 디렉터리 열기가 네 빈의 의존이고 「여는 자리 = 잠금 자리」(D-6G-57)라 러너 catch 로 옮기면 배선·잠금·seed 순서를 다시 짜야 한다. 거부 사유가 기동 실패 출력에서 grep 되는 닫힌 토큰을 갖는 것으로 충분 → `OPEN-6G-REVIEW-FOLLOWUPS` 에 등재 ② D-19 의 셈은 `at` 정렬이 아니라 **원장의 덧붙인 순서**로 읽는다 — 시계 역행 실행이 있으면 `at` 정렬이 추출 쪽 「마지막 줄이 이긴다」와 어긋난다. 정착 창 성질은 그대로 |
| **D-6G2d-23** | **형식 version 2** — AXIS 결말 어휘에 `Refused` 가 더해져 version 1 원장의 `FAILED:BUDGET_EXHAUSTED_*` 줄을 새 코드가 읽으면 고친 결함이 재현되므로 올린 것이 맞다. 실수집 전이라 비용 0. 종결 문단에 「실행 상태 형식 version 2 부터」를 적는다 |
| **D-6G2d-24** | **r2 는 표적 재검증**: D-15·21 스키마 계약(여섯 금액 칸 + `a_value` 전부/무) · D-16 [1,1,1,→호출] 판 · D-17 등식 · D-19 정착 창(Failed·Failed·Succeeded·Failed → 재호출) · version 2 거부/수락 · D-18 항목 · 새 public 표면(`Refused` · `fractionalAmounts` · D-21 계수) (2b) · acceptance(`check` 넷 + container) · rollback. code-reviewer 는 `2a1bb88b..판정 SHA` diff |

**OPEN 표 갱신(레인 보고 4)**: 신설 `OPEN-6G2D-EMPTY-AXIS-REASON` · `OPEN-6G2D-AXIS-RETRY-LIMIT`(값 3 · 창 = 디렉터리 생애, 마지막 정착 뒤 누적 — 운영자 A-3) · `OPEN-6G-REVIEW-FOLLOWUPS` 추가(형식 거부 종료 코드 · `unusableRawRows` 세 원인 분리 계수).

## 계약 갱신 r1-c (2026-09-30, 팀장 — 레인 보고 「D-21 ⓑ 는 골든 바이트를 바꾼다」 수령 · 운영자 결정 「지금 6G-2d 에 포함」)

| ID | 결정 |
|---|---|
| **D-6G2d-25** | **D-21 ⓑ 를 이 slice 에서 닫는다 — 골든 재생성 포함(운영자 결정 2026-09-30).** 골든의 `a_value.total` 260,853,707 은 출하 조립 E2E 의 A 축 대역이 여섯 구성 항목 중 하나(`npnInsrprm`)만 싣고 품질관리비 술어 `Y` 인데 금액이 없는 **반쪽 원문**이 만든 과소 합산이다 — 골든이 결함을 굳히고 있었다. 처방: ⓐ 대역의 A 축 원문을 **여섯 항목 + 공개일시 온전히** 채운다(합성값, 식별자 없음) ⓑ `a_value` 는 항목 하나라도 결측이면 null(전부 아니면 무), 발생 수는 `incompleteAValues` 계수 ⓒ Kotlin writer 로 골든을 **재생성**하고 manifest 의 해시·행 수·`sample_scope_divisions` 를 함께 갱신(12 = 9+1+1+1 항등식 유지, 스키마 칸·`schema_version` 무변경 — A-2 유지) ⓓ **Python 왕복**: CI `ml-engine` job 명령 그대로(`.github/workflows/ci.yml`)를 레인이 한 번 돌려 골든 판독 test 가 초록임을 확인(`ml-engine/src/**` 무변경) ⓔ 골든 바이트 변화는 evidence 에 「무엇이 바뀌었나」(행별 `a_value` before/after)로 공시. in_scope 에 `ml-engine/tests/evaluation/fixtures/m6-6g-golden/**` 를 더한다(골든 파일만 — Python 소스·다른 fixture 금지). 변이: 항목 결측에 `total` 이 나옴 → RED(M-a-undersum). 골든 재생성이 세 행의 `a_value` 를 null 로 만들면 그것은 대역이 아직 반쪽이라는 뜻이다 — 대역을 채운 뒤에는 세 행 모두 `a_value` 가 **있어야** 한다 |

in_scope 추가: `ml-engine/tests/evaluation/fixtures/m6-6g-golden/**`(D-25, 골든 파일만). out_scope 유지: `ml-engine/src/**` · `ml-engine/tests/**` 의 그 밖 · `snapshot-schema.md` 계약 칸.

## 계약 갱신 r1-d (2026-09-30, 팀장 — D-21 ⓐⓑ 완료 HEAD `5adcaa0c` 수령 · D-25 이행 방식 확정)

| ID | 결정 |
|---|---|
| **D-6G2d-26** | **D-25 는 골든 불변으로 이행된 것으로 확정한다.** 레인이 대역의 A 행을 **일곱 항목 온전히** 채우되 합을 골든의 `total` 260,853,707 과 같게 두었다 — `a_value` 에서 원문으로부터 파생되는 값은 `total` 뿐이라 골든 바이트가 그대로다. 골든은 합성 왕복 fixture 이고 그 값의 의미는 「writer 와 reader 가 같은 바이트에 합의한다」이지 실측 금액이 아니므로, 「반쪽 원문이 굳힌 과소 합산」은 대역이 온전해진 순간 사라졌다(이제 일곱 항목의 참 합). 따라서 D-25 ⓒ(재생성)·ⓓ(Python 왕복)·ⓔ(before/after 공시)는 **불필요**하고 in_scope 의 골든 디렉터리 추가는 **철회**한다(`ml-engine/**` diff 0 유지). 골든 재생성 시도는 auto mode 권한 분류기(「Modify Shared Resources」)가 막았고 레인은 우회하지 않았다 — 사실로 등재, 종결 보고에 적는다 |
| **D-6G2d-27** | **판정 SHA 는 이 갱신 커밋.** r2 표적(D-24)에 D-21 ⓑ 의 대역 온전성(일곱 항목·술어와 금액 정합)과 `incompleteAValues` 계수 (2b) 를 더한다 |

in_scope: r1-c 의 `ml-engine/tests/evaluation/fixtures/m6-6g-golden/**` 추가를 철회한다.

## 계약 갱신 r2 (2026-09-30, 팀장 — verifier r2 not-ready H-1 · code-reviewer r2 H-1·H-2·M-1~3·L-1~5 수령)

판정 SHA `38311917`. 통과: D-15/21 여섯 칸·A 두 경우 Python 거부 0 · D-16 [1,1,1,1](쿼터·스로틀·총 상한 동일) · D-17 · D-19 정착 창 · D-23 version 2(옛 디렉터리 MISMATCHED, 바이트 불변) · D-25/26 · 변이 12 RED · acceptance 넷 exit 0(test 2,552) · container 전 단계 exit 0 · rollback ①~⑥. 막는 것 하나. **재작업 2/5.**

| ID | 결정 |
|---|---|
| **D-6G2d-28** | **(vr r2 H-1 · cr r2 H-1 · 레인 자기 보고 — 데이터 정확성) 품질관리비 술어가 `Y`/`N` 밖이면 A 묶음을 비운다.** `aValuePartsOf` 는 술어가 `true` 일 때만 항목을 넣어, 술어 부재·빈 문자열·제3값이면 「합산 대상 아님」으로 접혀 A 가 그 금액만큼 작게 실린다(실측 6,000,000 vs 6,500,000, `incompleteAValues` 0). 「모름」은 「대상 아님」이 아니라 **「A 를 낼 수 없음」**이다(fail-safe, 필드 계약의 「세 번째 값이 오면 조용히 false 가 되는 자리를 만들지 않는다」와 같은 방향) → `a_value: null` + `incompleteAValues` 계수. test 는 네 칸(술어 부재·빈·제3값 × 금액 유·무) + `N` 유지. 변이: null 술어를 false 로 → RED. A 안의 다른 `*Yn` 술어가 있으면 같은 규칙 |
| **D-6G2d-29** | **(cr r2 H-2 기각) 쿼터 소진은 `Refused` 가 맞다.** `Refused` 의 뜻은 「HTTP 가 안 나갔다」가 아니라 **「그 축에 귀속되지 않는 거부」**다 — 예산·스로틀은 호출 없음, 쿼터(429·resultCode 22)는 계정 단위 사고이고 6G D-6G-11 대로 실행이 멈춘다. verifier 실측: 쿼터·스로틀·총 상한이 같은 거동, 다음 기동에서 재호출. `AttemptOutcome.Refused` KDoc 과 commands.md 문면을 이 뜻으로 고친다(코드 무변경) |
| **D-6G2d-30** | **일괄(low)**: D-17 등식을 손 목록이 아니라 **sealed 계층에서 도출한 집합**과 맞댄다(새 원인이 등식에 걸리게) · D-22 ② 덧붙인 순서 규칙의 전용 test 하나 · 러너 로그 줄의 두 계수 단언 · 이탈 13 문면을 D-26 대로(멈춘 이유 = 권한 분류기, A-2 아님) · 표면 표의 `init` 하한 문장 제거 · rollback.md 명령 범위를 마지막 산출물 커밋으로 · `axisResumptions` 안내 메시지의 한계 뜻(「상한 n = 확정 전 허용하는 일시 실패 수」) 정정(cr M-1) · evidence 표면 절 제목·행 수 정합(cr M-3) |
| **D-6G2d-31** | **r3 는 표적**: D-28 네 칸 + 변이 · D-30 각 항목 · acceptance(`check` 넷; container 는 코드 변경이 조립 층에 없으면 verifier 판단으로 생략 가능) · rollback 재실측 · 새 public 표면 갱신. code-reviewer 는 `38311917..판정 SHA` |

**사실 선언**: 동결 뒤 레인 evidence 커밋 `af5c23cb`(commands.md 골든 상태·Python 왕복 1,209 passed)가 판정 SHA 위에 올라갔다 — 코드 0, 판정 유효. 검증 중 레인이 `check` 를 한 번 돌려 verifier 가 기다렸다(동결 통지와 엇갈림, 세 번째 사례).

## 계약 갱신 r2-b (2026-09-30, 팀장 — 수정 라운드 2 보고 수령: HEAD `5020646a` · 이탈 둘)

| ID | 결정 |
|---|---|
| **D-6G2d-32** | **이탈 수용 둘**: ① D-30 의 sealed 도출 등식 test 는 도메인 모듈이 아니라 **app 의 구조 게이트 test 자리**에 둔다 — 도메인 test classpath 에 kotlin-reflect 가 없고(`-Werror`) 의존 추가는 in_scope 밖이며 도메인 test 의 능력을 넓힌다. 값 단위 거동 test 는 도메인에 남는다 ② `config/quality/gate-tests.properties` 의 한 줄은 새 게이트 test 의 **양방향 등재**다(술어 확장 아님) — verifier 가 등재임을 확인 |
| **D-6G2d-33** | **r3 판정 SHA 는 이 갱신 커밋.** 표적: D-28 여섯 판 + 변이 · D-29 문면 · D-30 각 항목(sealed 등식이 새 subclass 를 실제로 잡는가 — 정적) · 등재 한 줄 · acceptance `check` 넷 · rollback(공유 파일 hunk 격리 셋) · 새 public 표면 0 확인. container job 은 이 라운드가 조립 층을 바꾸지 않았으므로 생략 가능(verifier 판단) |

## 계약 갱신 r3 (2026-10-01, 팀장 — verifier r3 **ready-for-review**(R-1 rollback 문서 · L 2) · code-reviewer r3 high 1(= R-1) · medium 3 · low 3 수령)

판정 SHA `f2998be0`. 코드·게이트·계약 high 0 — r1·r2 의 차단 항목(소수 금액 필수 칸 · 관문 거부 셈 · 셈 창 · 술어 미지) 전부 닫힘. acceptance 넷 exit 0(test 2,558) · Python 1,209 · 변이 RED · 새 public 표면 0. **승인 전 일괄 하나**(6A-2b D-6A2b-54 선례) 뒤 종결. 재작업 계수 불변(2/5).

| ID | 결정 |
|---|---|
| **D-6G2d-34** | **승인 전 일괄(코드 거동 변경 0, 술어 확장 0)**: ① **rollback.md** — 복원 경로에 `app/src/test/kotlin/bidvector/app/architecture/**`(새 게이트 test) 추가, 유효성 술어·③ 대조 경로에 `config/quality/gate-tests.properties` 추가, 「새 파일 다섯」→ 여섯, 문서에 적힌 명령 **그대로** ①~⑥ 재실측(`실측 HEAD` 갱신; D 6 · ③ 빈 것 · ④ compile 0 이 문서의 명령에서 나와야 한다) ② `axisResumptions` 안내 문면: 「상한 N = N 번째 일시 실패에서 확정(N=1 이면 첫 실패에 확정)」 ③ evidence 표면 절 제목·D-6G2d-6 상태 행·이탈 2 의 「여덟」→ 실제 수 ④ 로그 계수 test 가 **값**을 잠그게(fixture 계수를 서로 다른 0 아닌 값으로, 두 계수 바꿔치기 → RED) · 분기 키 자기교집합 단언 제거 · 술어 `N` + 금액 있음 판 하나 ⑤ cr r3 M-2: sealed 도출을 **잎까지 재귀**(중첩 sealed 층이 대표 하나로 접히지 않게) — 술어 확장이 아니라 도출 범위 정정, 변이(중첩 층의 넷째 확정 원인) → RED |
| **D-6G2d-35** | **종결 절차**: 일괄 커밋 뒤 verifier **표적 확인**(문서 명령 그대로 rollback ①~⑥ · 게이트 test 초록 · 로그 test 변이 · sealed 재귀 변이 · evidence 정직성) → 팀장 종결 문단(`milestone-6.md`, 「실행 상태 형식 version 2 부터」·A-3 대기) → rollback 2단계 등재 → push · PR · 판정 코멘트(r1·r2·r3·표적 확인) · `/code-review` · 처분 · CI 초록이면 머지(사용자 사전 승인 2026-09-30) |

## 계약 갱신 r4 (2026-10-01, 팀장 — PR #51 `/code-review` 8건 수령)

| ID | 결정 |
|---|---|
| **D-6G2d-36** | **리뷰 대응 일괄(코드, 소수정 여섯 — 술어 확장 0, 형식 version 불변)**: ② **PENDING/HTTP 줄만 남은 라운드도 상한에 센다** — raw 적재와 AXIS 결말 사이에서 던진 축(ⓑ 창)은 지금 `Failed` 줄이 없어 매 실행 상한 없이 재호출된다(구조적 적재 실패면 승인 호출을 매번 태운다). `doneWith` 는 마지막 정착 뒤 「결말 없이 끝난 시도 라운드」(HTTP 줄 있음·결말 없음)를 일시 실패와 같이 센다. test: 적재가 던지는 축 세 실행 → 네 번째 호출 0. 변이(세지 않음) → RED ③ `healTornTail` staged 쓰기에 `SYNC`(또는 `force(true)`) + 디렉터리 fsync — rename 이 바이트보다 먼저 굳는 창 제거 ④ `axisRetryLimit >= 1` 검증을 `DetailFetchGates.init` 으로(형제 필드와 같은 자리, HTTP 전) — `axisResumptions` 의 `require` 는 제거(D-30 의 「도메인 질의 쪽 유지」를 뒤집는다: 정책 구성 시점이 더 이르다) ⑥ `jsonAmount` 의 소수 → `null` 은 **tally 를 거치지 않으면 fail-loud**(`check`) — 조용한 결측 금지, 계수는 tally 한 자리 ⑦ `conclusionOf` 의 도달 불가 `requireNotNull`·`AXIS_WALK_REQUIRED` 제거, fail-closed 자리는 `CollectionAttempt.init` 이라고 KDoc 이 가리키게 ⑧ `SnapshotExtraction(...)` 호출을 **명명 인자**로(일곱 `Int` 위치 인자 금지) — 계수 둘 바꾸기 변이가 컴파일·test 어디선가 RED 가 되게(값 test 가 wiring 까지 덮도록 조립 test 하나) |
| **D-6G2d-37** | **OPEN 등재 둘(운영자 가시)**: ① `FinalFailure(MAX_PAGES)` 는 정책 값 `maxPages`(50×100) 에 의존하는 확정이다 — 5,000 초과 참가 축이 `incomplete_axis` 로 영구 제외되고 `maxPages` 를 올려도 재호출되지 않는다. 지금은 계수가 정직하므로 유지하되 `OPEN-6G2D-MAX-PAGES-FINAL` 로 등재, **A-3 과 함께** 운영자에게(선택지: 유지 / 일시로 바꿔 상한에 맡김 / `maxPages` 상향) ⑤ 정착한 축을 다시 걸 경로가 오늘 없지만, 생기면 `doneWith`(정착 뒤 창)와 `axisConclusions`(마지막 줄)가 같은 원장을 다르게 읽는다 → `OPEN-6G-REVIEW-FOLLOWUPS` 에 「두 판독기가 한 술어를 공유」 |
| **D-6G2d-38** | **표적 확인**: ② 판(적재 예외 세 실행 → 네 번째 0) + 변이 · ③ 옵션 확인 · ④ 정책 구성 시점 실패 · ⑥ 변이(tally 우회 → 던짐) · ⑧ 계수 맞바꾸기 변이 RED · acceptance 강한 집합 · rollback 재실측(`실측 HEAD` 갱신, 종결 문단 커밋 `f714e3f5` 유지) · 새 public 표면. code-reviewer 정적 리뷰는 `/code-review` 재실행으로 갈음 |

## 계약 갱신 r4-b (2026-10-01, 팀장 — 리뷰 대응 일괄 보고 수령: HEAD `ef730c57` · 이탈 다섯)

| ID | 결정 |
|---|---|
| **D-6G2d-39** | **이탈 수용 다섯**: ① 상한이 세는 단위는 실행이 아니라 **호출**이다 — 원장에 실행 경계가 없어(형식 불변) 마지막 정착 뒤 「일시 실패 결말 + 결말 없는 꼬리의 HTTP 줄」을 센다. 여러 쪽을 걷는 축은 상한에 더 빨리 닿는다(보수, `incomplete_axis` 공시). **`OPEN-6G2D-AXIS-RETRY-LIMIT` 의 A-3 문면**을 「N = 마지막 정착 뒤 일시 실패 결말 수 + 결말 없는 꼬리 호출 수」로 고치고, 실행 단위 셈은 원장 형식이 실행을 말할 때(별 slice)로 ② 내구 교체를 장부 쓰기에도 적용(같은 창·같은 세 걸음, 한 함수) ③ public 거동 변경 둘(조립 밖 소수 금액은 던짐 · 상한 0 정책 구성 거부) — 계약 D-36 이 요구한 것 ④ workflow harness 의 대역 포트가 관문 두 줄을 남김(측정 표면) ⑤ 새 파일 셋·게이트 등재 세 번째 줄(크기 게이트 대응, 재는 것으로 분할) |
| **D-6G2d-40** | **표적 확인 SHA 는 이 갱신 커밋.** verifier: D-38 항목 + ①의 셈 단위 실측(두 쪽 걷기가 적재 예외로 끝난 축은 몇 번째 기동에서 멈추는가) · 등재 hunk 여섯 순서 · acceptance 강한 집합 · rollback 문서 명령 그대로 |

## 계약 갱신 r5 (2026-10-01, 팀장 — PR #51 `/code-review` 재실행 6건 수령: 직전 일괄이 만든 셋 + 셋)

| ID | 결정 |
|---|---|
| **D-6G2d-41** | **(cr ①③ — 회계·내구) 원장이 장부보다 먼저 굳는다.** 직전 일괄은 `recordState()` 에 SYNC+fsync 를 걸고 원장 append(`FileAttemptLedger.append`)·표본 파일은 그대로 두어 **장부가 원장보다 앞서 굳는** 역전을 만들었다 — 정전 뒤 「줄 수가 장부보다 적다」 영구 거부(이 slice 가 닫으려던 부류, 이 세션의 정전이 그 예). 처방: 내구 순서를 **원장 → 장부**로 고정 — `recordState()` 전에 원장 채널 `force(true)`(표본 파일도 쓴 뒤 `force`), 그 다음 장부 durable 교체. 비용은 append 마다 fsync 셋 — E2E 에서 append 당 시간을 한 줄 로그·evidence 에 실측하고 `OPEN-6G2D-FSYNC-BATCHING`(결말 단위 묶기 후보) 등재. 변이: 원장 force 제거 → RED(순서 test: 장부 쓰기 전에 원장 force 가 호출됨을 대역 채널로 단언) |
| **D-6G2d-42** | **(cr ②⑥ — 데이터 정확성) 크래시 라운드는 라운드 하나로 센다.** 결말 없는 꼬리를 HTTP 줄 수로 세면 여러 쪽 축이 크래시 한 번에 상한을 다 써 **참가자 수와 결측이 상관**한다(비랜덤 제외). 그리고 꼬리 셈이 `Refused` 줄에서 끊겨 거부 뒤의 크래시가 사라진다. 처방: **재개 시 앞 라운드를 닫는다** — 결말 없는 꼬리가 있는 축을 다시 걷기 전에 use case 가 `AXIS Failed(INTERRUPTED)` 결말 줄을 남긴다(걷기 식별자는 꼬리 HTTP 줄이 나르게 하거나 형식 version 3 로 라운드 표지를 둔다 — 실수집 디렉터리가 없어 bump 비용 0; 레인이 택하고 사유 기록). 그러면 상한은 **일시 실패 결말 수만** 세고(D-16·19 그대로), `Refused` 는 정착도 실패도 아니므로 셈에 영향 없음. test: 두 쪽 축이 적재 예외로 세 번 끊김 → 네 번째 기동 0 호출(한 쪽 축과 같은 기동에서 멈춤) · 크래시·크래시·Refused·기동 → 셈 2. 변이: 앞 라운드를 안 닫음 → RED |
| **D-6G2d-43** | **(cr ④) 투찰자 소수 금액은 `bidder_rows` 전체를 비운다** — 한 명만 null 이면 정렬(널 마지막)이 바뀌어 순위가 밀리고 Python 이 「수의계약」 사유로 뺀다. 집계 전부/무(D-15) 와 같게: 소수 금액 투찰자가 하나라도 있으면 `bidder_rows` 를 비우고(기존 행 단위 제외로) `fractionalAmounts` 계수. 변이: 한 명만 null → RED |
| **D-6G2d-44** | **(cr ⑤) Busy 경로도 형식을 검사한다** — 잠금을 못 잡아도 `requireCurrentFormat`(읽기 전용)은 돌려 옛 디렉터리는 `RunStateFormatRefused` 로, 손상은 손상으로 갈린다. `LockedOutAttemptLedger.read` 가 옛 AXIS 줄에서 generic 예외를 내지 않게. test: v1 디렉터리를 다른 프로세스가 잠근 채 열기 → 형식 거부 |
| **D-6G2d-45** | **표적 확인(verifier)**: D-41 순서 test·변이 + append 당 비용 실측 · D-42 두 판(두 쪽 축 · Refused 사이) + 변이 · D-43 변이 · D-44 · 형식 version(2 유지 또는 3) 거부/수락 · acceptance 강한 집합 · rollback 문서 명령 그대로 · 새 public 표면. 그 뒤 `/code-review` 재실행 |

## 계약 갱신 r5-b (2026-10-01, 팀장 — 재리뷰 대응 보고 수령: HEAD `a5581341` · 이탈 셋)

| ID | 결정 |
|---|---|
| **D-6G2d-46** | **이탈 수용 셋**: ① D-42 의 끊긴 라운드 걷기 식별자는 형식 v3 가 아니라 **그 라운드의 마지막 호출 시각** — 그 라운드의 원문은 결말이 `Failed(INTERRUPTED)` 라 어차피 쓰이지 않으므로(그 공고는 `incomplete_axis`) 값이 0 인 칸을 위해 version 을 올리지 않는다; 「형식 version 2 부터」 유지 ② D-44 가 기동 실패 사유 토큰 `RUN_STATE_FORMAT_LEGACY_LINE` 을 더했다 — 장부가 아니라 **줄**에서 드러나는 옛 형식이라 기존 둘로 말할 수 없다. A-2 의 「사유 어휘」는 스냅숏 스키마의 결측 사유이고 이것은 기동 출력이다 ③ D-41 이 장부 판독을 제 타입으로 갈랐다(함수 수 한도) — 그 과정의 초기화 순서 함정(D-1 계열)은 전건 `check` 가 잡았다(표적 test 는 초록이었다 — 「부분 게이트는 안 돌린 것과 같다」 다섯 번째 실측) |
| **D-6G2d-47** | **표적 확인 SHA 는 이 갱신 커밋.** verifier: D-41 순서 test·변이·append 당 6,795 µs 재실측 · D-42 두 판·변이(두 쪽 축이 한 쪽 축과 같은 기동에서 멈춤 · 크래시·크래시·Refused → 2) · D-43 변이 · D-44 v1 디렉터리 잠긴 채 열기 → 형식 거부 · 형식 version 2 유지 확인 · acceptance 강한 집합 · rollback 문서 명령 그대로(hunk 일곱) · 새 public 표면 |

## 계약 갱신 r6 (2026-10-01, 팀장 — PR #51 `/code-review` 3차 9건 수령)

| ID | 결정 |
|---|---|
| **D-6G2d-48** | **소수정 여섯(코드 거동 최소, 술어 확장 0, 형식 version 2 유지)**: ① 러너의 `openingCauseCodeOf` 가 `RunStateFormatRefusedException` 의 사유 토큰(MISSING/MISMATCHED/LEGACY_LINE)을 로그·종료 사유로 내게(클래스명으로 떨어지지 않게 — 예외를 app 이 식별할 수 있는 최소 표면, 사유 코드 문자열만) ② **PENDING 만 남은 꼬리**(의도 줄 뒤·HTTP 줄 전 크래시)도 끊긴 라운드다 — `interruptedRounds` 가 닫고(walk = PENDING 의 `at`) 상한에 하나로 센다; 예산은 이미 그 PENDING 을 나간 호출로 세므로 두 장부의 가정이 같아진다. test: 매 실행 PENDING 뒤 크래시 세 번 → 네 번째 0 호출. 변이 → RED ③ 디렉터리 fsync 실패(DrvFs·9p 등 미지원 마운트)는 **한 번 경고하고 계속**(파일 데이터 fsync 는 유지) — 예외로 append 전체를 막지 않는다; runbook 에 「실행 상태 디렉터리는 ext4(WSL 내부)」 권고 ④ `incompleteAValues` 는 **A 가 적용되는 공고**(`bid_price_formula_a_applicable = Y`)에서만 센다 — 미적용 공고의 빈 A 행은 계수 없이 null ⑤ `FileChannelAppend.append` 는 `while (buffer.hasRemaining()) channel.write(buffer)` ⑥ `requireReadableFormat` 호출을 `heldOrRelease` 가드 안으로(잠금 쥔 채 던지지 않게) + 낡은 KDoc(`readFacts`) 정정 |
| **D-6G2d-49** | **등재 셋**: cr ④ 「끊긴 라운드를 `INTERRUPTED` 줄로 닫는 대신 `doneWith` 가 열린 꼬리를 하나로 세라」는 설계 이견 — 계약 D-42 는 닫는 쪽을 택했다(원장이 사실을 말하고 셈이 원장만 읽게). INTERRUPTED 줄의 `walk` 가 관측 시각이 아니라 마지막 호출 시각임은 `AxisConclusion.walk` KDoc 에 예외로 적고, 고정 시계 test 에서 그 값이 관측 시각과 겹칠 수 있음(오늘은 `Failed` 라 무해)을 알려진 제한으로 · cr ⑥ 장부 갱신을 AXIS 결말·표본 확정 때만(원장 앞섬은 재동기가 처리) → `OPEN-6G2D-FSYNC-BATCHING` 의 구체안으로 · cr ⑦ 원장 이중 파싱 → `OPEN-6G-REVIEW-FOLLOWUPS` |
| **D-6G2d-50** | **표적 확인(verifier)**: ①~⑥ 각 test·변이(②③⑤ 필수) · acceptance 강한 집합 · rollback 문서 명령 그대로 · 새 public 표면(①의 사유 코드 표면). 그 뒤 `/code-review` 4차 — **새 high 가 없으면 나머지는 등재로 닫고 머지한다**(수렴 규칙: 라운드마다 새 low·medium 이 나오는 것은 리뷰의 성질이지 slice 의 미완이 아니다) |

## 계약 갱신 r6-b (2026-10-01, 팀장 — 3차 리뷰 대응 보고 수령: HEAD `eb602adc` · 이탈 넷)

| ID | 결정 |
|---|---|
| **D-6G2d-51** | **이탈 수용 넷 + in_scope 한 파일**: ① 디렉터리 fsync 관용은 `forceDirectory` 안이 아니라 부르는 자리에(대역 주입 test 가 잠그려면) ② 경고는 표준 오류 한 줄(어댑터에 로그 포트 없음, 닫힌 토큰, 프로세스에 한 번) ③ 새 public 표면 하나 `runStateFormatCauseCode`(사유 코드 문자열만, 예외 타입은 비공개 유지) ④ **in_scope 에 `app/src/main/kotlin/bidvector/app/collection/OpeningCollectionLines.kt` 추가** — D-48 ① 이 그 자리를 지목했는데 목록이 추출 러너 한 파일만 들었다(팀장 누락). 그 누락이 rollback ④⑤⑥ 의 구멍이었고 레인이 실측으로 잡아 복원 목록에 더했다 |
| **D-6G2d-52** | **표적 확인 SHA 는 이 갱신 커밋.** verifier: D-48 ①~⑥ test·변이(②③⑤ 필수) · 러너 로그에 형식 토큰 · acceptance 강한 집합 · rollback 문서 명령 그대로(D14/M39, hunk 아홉) · 새 public 표면. 그 뒤 `/code-review` 4차 — D-50 수렴 규칙 적용 |

## 계약 갱신 r7 (2026-10-01, 팀장 — PR #51 `/code-review` 4차 9건 수령 · D-50 수렴 규칙 발동)

새 high 없음 → 코드 변경 0, 전부 등재. **실수집 전** 표지 셋은 6G-2c(리뷰 후속 일괄)가 실수집 시작 전에 닫는다 — 실수집 시작 조건에 더한다(A-3 · 운영계정 키 · 6G-2c 의 이 셋).

| ID | 결정 |
|---|---|
| **D-6G2d-53** | **`OPEN-6G-REVIEW-FOLLOWUPS` 추가(실수집 전 ★)**: ★② 꼬리에 **찢어진 조각만** 남은 라운드(첫 PENDING 쓰기 도중 크래시)는 `read()` 가 조각을 걸러 열린 라운드로 안 보이고 예산은 그 조각을 호출 하나로 센다 — 같은 축의 반복 크래시가 상한을 안 올린다(예산 상한으로는 묶임). 처방: 꼬리의 torn 표식도 열린 라운드로 ★③ `incompleteAValues` 가 기초금액 축의 `formulaAApplies` 에 묶여 그 축이 비면 A 행의 결손을 세지 않는다(값은 맞고 계수만 과소) — A 적용 여부는 A 축 행 자체에서 ★⑦ `healTornTail` 이 `verifyIntegrity`(directory_id·모르는 파일) 앞에 돌아 거부될 디렉터리를 먼저 고쳐 쓴다 · `LedgerDigest` 읽기(비UTF-8 등)가 가드 밖이라 던지면 잠금이 남는다 — 순서 교환 + 가드 안으로. 그 밖: ① MISSING/MISMATCHED 토큰은 bean 생성 시점이라 러너 매핑에 안 닿는다(D-22 ① 수용 사실, KDoc 「감싸는 자리가 없다」 정정 필요) ⑥ `AxisConclusion.settled` 죽은 코드·KDoc 오도 ⑧ Held 경로에서 `state.json` 세 번 파싱 ⑨ `fromLedgeredWalk` 가 실패 결말 축의 원문도 파싱·보유(메모리) |
| **D-6G2d-54** | **`OPEN-6G2D-FSYNC-BATCHING` 추가**: ④ `recordState()` 가 append 마다 표본 두 파일을 다시 읽고 해시한다(확정 콜백에서만 갱신하면 됨) ⑤ 원장을 collect 마다 두 번 전체 파싱 + 줄마다 JSON 두 번 — 묶기 설계와 같은 자리에서 |
| **D-6G2d-55** | **머지.** 사용자 사전 승인(2026-09-30 「리뷰 이상 없으면 머지」) + D-50. 실수집 시작 조건: A-3 결정 · `OPEN-6G2D-MAX-PAGES-FINAL` 처분 · 운영계정 키 · D-53 ★ 셋 닫힘(6G-2c) |

## 위협 모델 — 6G-2d 고유 경계 (Phase 2.5 (0))

**지키는 것**: ① 실행 상태 **회계** — 정직한 크래시 한 번 뒤에 재기동이 되고 상한이 되감기지 않는다 ② 추출 값이 (공고, 축)마다 **하나의
걷기**에서 오고, 결말 줄이 없는 축은 **가장 늦은** 걷기다 ③ 옛 형식 실행 상태는 **기동되지 않는다**(fail-closed).
**경계 밖**: 게이트 하드닝(6G-2a·6G-2b) · 두 프로세스 잠금 실측·harness 고정 시계 충돌(6G-2c) · 사유 어휘 확장(A-2) · 반사실.

### (1) 열거인가 구성인가
결말 줄이 없는 축의 집합을 이름으로 들지 않는다 — `conclusion == null` 갈래가 구성으로 덮는다. 옛 형식 판별도 키 존재·version 값이지
축 이름이 아니다.

### (2) 우회 — 다섯 이상 (착수 시 실측으로 갱신)
1. 복구 뒤 `LedgerDigest` 재생성을 빠뜨린다. ← 기동 셋 test(D-1).
2. staged 파일 이름을 장부 집합 제외에 넣지 않아 복구 도중 크래시가 「모르는 파일」 거부로 이어진다. ← 크래시 흉내 test(D-2).
3. 목록 축 선별을 `inserted_at` 으로 한다(재걷기는 보통 뒤에 적재되므로 같은 답처럼 보인다). ← backfill 행 test(D-3).
4. `format_version` 을 읽되 경고만 남기고 기동한다. ← 기동 거부 단언 변이 RED(D-4 ⓐ).
5. 파서가 walk 없는 AXIS 줄을 `null` 로 관용한다. ← 읽기 거부 변이 RED(D-4 ⓑ).
6. AXIS 기록 자리가 `observedAt` 이 없는 배치에서 walk 없이 줄을 쓴다. ← `init` 의 양방향 `require`(D-4 ⓒ) — 그 자리는 실패해야지 조용히 지나면 안 된다.

### (2b) 값 획득 축
새 public 표면 0 이 기대값이다. `format_version` 상수·staged 이름은 `internal`. 바깥 코드가 걷기 식별자로 엉뚱한 행을 고르게 할 수 있는가 —
6G r5-t §8 의 답(식별자는 (공고, 축) 안에서만 대조)이 그대로 성립해야 하고, D-3 이 더하는 것은 결말 줄 **없는** 축의 선별뿐이다.

### (3) 과잉·미달
- 과잉 아님: 셋 다 실수집의 값 또는 회계에 닿는다. 형식 version 은 지금이 유일하게 싼 때다.
- 미달 경계: 사유 어휘(D-5)와 복구 표식 줄이 매일 오늘치를 1 씩 줄이는 성질(6G code-review r5-t L-7 — 보수적 방향)은 이 slice 밖.

## 운영자 승인 필요 (착수 전)

> **2026-09-30 운영자: A-1 「6G 머지 직후, 6G-2a 앞」 · A-2 「스키마 올리지 않음 — 등재·공시」 확정.** 같은 날 DEC-03 은 **현행 유지**(판별 slice 없음, 6G D-6G-21 그대로: 제외 ⑪ 판정 불가 계수 + 민감도 두 판) — 이 slice 와 실수집에 지자체 판별 요구가 더해지지 않는다.

- **A-1 착수 시점**: 6G 머지 직후, 6G-2a 앞(2026-09-30 결정 반영). 실수집은 이 slice 머지 뒤 — 실수집 준비(dev DB 재기동·설정·runbook)는 병행 가능.
- **A-2 D-6G2d-5 의 사유 어휘**: (추천) 이 slice 에서 스키마를 올리지 않고 등재·공시 / 함께 올림(Python 레인 추가, golden 재생성, 스키마 v6).

## in_scope

- `adapters/src/main/kotlin/bidvector/adapters/snapshot/**` · `adapters/src/test/**`(실행 상태 디렉터리·추출·조립)
- `procurement/src/main/kotlin/bidvector/procurement/**`(`CollectionAttemptLedger` 의 AXIS 줄 계약) · `procurement/src/test/**`
- `workflow/src/main/kotlin/bidvector/workflow/collection/**` · `workflow/src/test/kotlin/bidvector/workflow/collection/**`(AXIS 기록 자리 — D-4 ⓒⓓ 가 닿을 때만)
- `app/src/test/**`(출하 조립 E2E 의 기동 셋·옛 형식 거부 단언) · **(r0-b D-6G2d-9)** `app/src/main/kotlin/bidvector/app/collection/SnapshotExtractionRunner.kt` · **(r6-b D-6G2d-51)** `app/src/main/kotlin/bidvector/app/collection/OpeningCollectionLines.kt` · `adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsPageUriBuilder.kt` · `adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsCallGate.kt` · `adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcCollectedAxisStore.kt`
- `config/quality/**`(새 타입 **등재**만)
- `reports/evidence/m6/6g2d/**` · `milestone-6.md`(착수·종결 문단만)

**out_scope**: `ml-engine/**` · `reports/evidence/m6/6g/snapshot-schema.md` 의 계약 칸 · golden `m6-6g-golden/` · 게이트 술어 파일 ·
`adapters/.../koneps/**`(transport) · `reports/evidence/m6/6g/**` · `app/src/main/**/http/**`.

## acceptance

CI `check` job 명령 그대로(`.github/workflows/ci.yml`) + `container` job(실행 상태 디렉터리 E2E 가 그 job 에 있으면 같은 조건으로).
`commands.md` 에 변이표(D-1~D-4 의 RED/GREEN)와 착수 실측 표의 갱신값. clean-tree 게이트·누출 스캔은 evidence-pack 규격대로.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 공유 파일 `milestone-6.md`·`config/quality/*.properties` 는 커밋 해시
hunk 격리(착수·종결 두 문단 커밋을 `git log` 로 기계 산출). 버릴 clone 에서 ①~⑥ 실측, `실측 HEAD` 는 마지막 산출물 커밋.

## 리뷰 레인

`verifier`(기동 셋 · 복구 도중 크래시 · backfill 순서 · 옛 형식 fail-closed 변이 · 새 public 표면 0 확인) + `code-reviewer`(sonnet, `model: sonnet` 명시).
Codex 없음 — 되돌리기 어려운 경로가 아니다. privacy-gate 는 실행 상태 파일에 새 칸이 생길 때 그 칸의 어휘만(식별자 표면 무변경).

## 하네스 레인 변경

**없음** — `git log --oneline c357e437..HEAD -- CLAUDE.md .claude/` 가 빈 출력이다(구현 레인 실측,
리뷰 요청 시점). 이 range 의 커밋은 착수 문단 둘과 slice 산출물·evidence 뿐이다.

## OPEN 수령·신설 (예상)

| OPEN | 처분 |
|---|---|
| `OPEN-6G-RUN-STATE-HEAL-ORDER` | **이 slice 가 닫는다**(D-1·D-2) |
| `OPEN-6G-LIST-AXIS-WALK-SELECTION` | **이 slice 가 닫는다**(D-3) |
| `OPEN-6G-LEGACY-AXIS-LINE` | **이 slice 가 닫는다**(D-4) |
| `OPEN-6G-REVIEW-FOLLOWUPS` 의 code-review r5-t M-3(복구 쓰기 비원자) · L-1 · L-2 | **이 slice 가 닫는다**(D-2·D-4) — 나머지 항목은 6G-2c 그대로 |
| (신설) `OPEN-6G2D-EMPTY-AXIS-REASON` | 「정착했으나 0 행」의 정확한 사유 어휘 — 스키마 v6 후보, 백테스트 판정 보고에 계수 공시(D-5) |
| (신설) `OPEN-6G2D-AXIS-RETRY-LIMIT` | 재호출 상한 값 3 은 잠정 · 창 = 실행 상태 디렉터리 생애 · **단위(D-39 ①)**: 상한 N 은 (공고, 축)마다 마지막 정착 결말(성공·빈 응답·확정 실패) 뒤의 **일시 실패 결말 수**다 — 끊긴 라운드(결말 없이 끝난 걷기)는 재개 시 `Failed(INTERRUPTED)` 결말 하나로 닫혀 **쪽 수와 무관하게 하나**로 세고, 관문 거부(`Refused`)·의도 줄·HTTP 줄은 세지 않는다. 합이 N 에 닿으면 다음 기동부터 그 축을 부르지 않는다(N=3: 한 쪽 축도 두 쪽 축도 4번째 기동부터 안 부름 — vr 실측) (D-42 뒤 정정) — 운영자 결정 A-3(값·창·단위) |
| `OPEN-6G-REVIEW-FOLLOWUPS` | 추가: 형식 거부의 러너 종료 코드(D-22 ①) · `unusableRawRows` 세 원인 분리 계수(cr r1 M-2) |
