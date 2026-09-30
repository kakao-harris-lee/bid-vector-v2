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
- `app/src/test/**`(출하 조립 E2E 의 기동 셋·옛 형식 거부 단언)
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

(착수 뒤 리뷰 요청 시점마다 등재)

## OPEN 수령·신설 (예상)

| OPEN | 처분 |
|---|---|
| `OPEN-6G-RUN-STATE-HEAL-ORDER` | **이 slice 가 닫는다**(D-1·D-2) |
| `OPEN-6G-LIST-AXIS-WALK-SELECTION` | **이 slice 가 닫는다**(D-3) |
| `OPEN-6G-LEGACY-AXIS-LINE` | **이 slice 가 닫는다**(D-4) |
| `OPEN-6G-REVIEW-FOLLOWUPS` 의 code-review r5-t M-3(복구 쓰기 비원자) · L-1 · L-2 | **이 slice 가 닫는다**(D-2·D-4) — 나머지 항목은 6G-2c 그대로 |
| (신설) `OPEN-6G2D-EMPTY-AXIS-REASON` | 「정착했으나 0 행」의 정확한 사유 어휘 — 스키마 v6 후보, 백테스트 판정 보고에 계수 공시(D-5) |
