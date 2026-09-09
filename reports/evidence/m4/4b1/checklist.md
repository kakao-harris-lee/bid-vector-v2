# checklist.md — M4/4B-1 리뷰 준비도

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      결과 없음(commands.md 「clean-tree 게이트」, 양성 대조 포함).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·
      detekt·cpd·sizeGate·domainApiTypeGate·domainSourceReferenceGate·moduleDependencyGate·
      gateExecutionGate·qualityBaseline 포함).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — golden-manifest.json(신설 8건) ·
      `VerdictLadderPolicyData`는 legacy-behavior 값을 test 정책에만 쓴다(아래 §3).
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] secret 스캔 통과 — commands.md 기록.

## 사용자 승인 — 2026-09-09

**slice 4B-1 종결 승인.** 승인 범위 셋:

1. **slice 종결** — 이 slice의 산출물(`Verdict` 게이트 사다리 커널·`SkipReason` 전수·
   force-bid 출처 노출·override 검증·corpus 실행자·gate 등재)을 최종 형태로 승인한다.
   base `13cf0f63da18e13f0e2befd519710a8635742004` · head
   `efd2d60dd9e4ebf354a871fac11121aa91fa45bc`(재검증 r2 장부층 M-1 재실측 커밋).
2. **`verdict` corpus 12건 case 승인** — `verdict-001`~`012` 전건. `fixtures/manifest.yaml`의
   `review.approved_by_user`를 `true`로, `review.claude_commit`을 실제 커밋(001~004는
   M0 원본 저작 커밋 `9e1223a`, 005~012는 이 slice의 커널 신설 커밋 `381eaeb`)으로
   채웠다. 승인 범위는 **`verdict` 도메인 한정**이고(manifest `review.approval_scope`
   필드에 명시), 다른 도메인의 case는 이 결정의 대상이 아니다.
3. **다음 slice는 4B-2**(조합 use case) — 계약은 팀장이 별도로 쓴다. 이 레인은
   `reports/evidence/m4/4b2/**`·`.claude/`·`CLAUDE.md`를 건드리지 않는다.

**승인의 근거 — verifier r2 `ready-for-review`.** 산출물층 blocker/high 0건
(`_workspace/m4-4b1/04_verifier_report_r2.md`). r1의 H-1(신설 여덟의 `verified_paths`가
미승인 reason 이름을 잠그고 `verdict-001`과의 분류 모순)은 `verified_paths` 좁히기와
`verdict-001~004` 재구성-후-재승격(운영자 결정 선택지 (a), 되돌림 경로의 두 번째 사용)
으로 재작업 1/5에서 닫혔다. 재검증 r2가 지목한 M-1(`verdict-003`이 자기 `verifies`를
재지 못하는 문면 정직화)·L-1·L-2·하네스 레인 누락 둘은 이번 장부층 일괄로 반영했다.
도메인·계약층에 미해결 finding은 없다.

**재작업 카운터: 1/5**(상한 5, 여유 4) — H-1·M-1/L-1~L-3/B-1~B-5 반영 라운드 하나만
카운트된다(재검증 r2와 장부층 일괄 둘은 라운드를 추가하지 않는다, 2026-09-02 low/장부층
문턱).

## 이 slice 고유 확인

### 1. 값 획득 축 판정의 실측(설계 검토 (2)) — 4A 와 결론이 다르다

설계 검토는 「`decision`은 도메인 모듈이라 `app` conformance 실행자가 커널을 직접
부르는 것이 1C·1D 관례라 4A 처럼 `internal`로 내릴 수 없다」고 판단했다 — 이 slice가
실측으로 확인한 것:

| 공개 표면 | 손에 넣은 주체가 할 수 있는 것 | 처분 | 실측 근거 |
| --- | --- | --- | --- |
| `VerdictLadder.judge`(public) | 값을 넣고 판정을 받는다 | **연다** | `VerdictExecutorsTest`(corpus 82 tests, `app` 모듈이 실제로 이 함수를 직접 호출) — 판정을 받는 것 자체가 부작용 0(순수 함수)임을 정상 배선이 증명 |
| `FloorOverrideValidation.validate`(public) | override rate 검증 결과를 받는다 | **연다** | 같은 근거, `verdict-012` |
| `Verdict.BidNow`/`Review`/`Skip` 생성자(`internal`) | 사다리를 지나지 않고 판정을 지어낸다 | **닫는다** | 임시 clone 컴파일 거부 실측(아래 §2) |
| `SkipReason`·`BidNowReason`·`ReviewReason`·`MlUnavailableReason` 하위 타입 생성자(data class 는 `internal`, data object 는 대상 아님) | 우회의 존재·ML 가용성을 거짓으로 주장하는 reason 을 지어낸다 | **닫는다** | 같은 실측(아래 §2) |
| `Verdict`·reason 읽기 프로퍼티 | 결과 소비 | **연다** | `VerdictExecutors.kt`가 필드를 직접 읽어 projection 을 만든다(위조가 아니라 읽기) |
| `VerdictLadderPolicyData`·`LadderInput`·`UnitScore`·`PlausibilityBand`(전부 public 생성자) | 사다리 입력을 구성한다 | **연다**(생성 불변식만 건다) | 입력은 호출부가 통제하는 값이라 통로 보호 대상이 아니다 — [Verdict]와 다른 축(4A `EditSessionPolicyData`도 같은 판단) |
| `FloorOverrideOutcome.Rejected`(public 생성자) — `Accepted`만 `internal` | 실제로는 밴드 **안**인 rate 를 "밖이라 거부됐다"고 위조할 수 있다(verifier L-2 실측: `app`에서 `Rejected(rate=0.50, band=[0,1])` 생성이 컴파일된다) | **연다(의도적 비대칭)** — 위조 방향이 **보수적**이다(override 를 폐기시킬 뿐 통과시키지 않는다). 위험한 방향(「밴드 밖인데 통과했다」주장)만 `Accepted`를 닫아 막는다(1D `Measurement.Unmeasurable` 관례와 같은 논리) | 임시 clone에서 `FloorOverrideOutcome.Rejected(BigDecimal("0.5"), band)` 직접 생성 → 컴파일 성공(위조 가능, 방향 안전 확인) |

**「`Verdict`를 얻으면 무엇을 할 수 있는가」** — 판정 결과는 읽기 값이다. 이 결과를 들고
저장·발행·알림 등 어떤 부작용 API 도 이 slice 에는 없다(조합 use case 는 4B-2, out_of_scope).
그래서 4A(획득이 저장 권한을 낳음)와 달리 **획득 자체가 위협이 아니고, 위협은 위조뿐**
이라는 설계 검토의 판단이 실측으로 확인된다.

### 2. 값 위조 축 실측 — 임시 clone 컴파일 거부(커밋 뒤 별도 절 참고)

`commands.md`의 「값 획득 축 실측」 절에 다섯 형태(Verdict 세 하위 타입 직접 생성자 +
BidNowReason/ReviewReason 하위 타입 직접 생성자) 전부의 거부 원문을 기록한다.

### 3. 조사의 여섯 실패 형태를 어떻게 뒤집었는가

| # | legacy 실패 형태(조사) | V2 구성형(이 slice) | 코드 근거 |
| --- | --- | --- | --- |
| 1 | 두 `skip`이 대입이 아니라 부작위(N-3) | 모든 분기가 결과를 명시 반환, 가변 변수 없음 | `VerdictLadder.judge`의 guard 함수 체인(`?:`), 각 함수가 `Verdict?`를 명시 반환 |
| 2 | 사유가 문장이고 구분 값이 코드에 없음(N-1·N-2) | `SkipReason`/`BidNowReason`/`ReviewReason` sealed + 값 payload, 문자열 사유 필드 없음 | `SkipReason.kt`·`BidNowReason.kt`·`ReviewReason.kt` |
| 3 | force-bid 우회와 정상 승격이 같은 문장(N-4) | `BidNowReason`을 `PriorityAboveBidNowThreshold`·`ForceBidOverride` 둘로 분리 | `VerdictLadderTest`의 「force-bid 우회」 case, `verdict-008` |
| 4 | ML 부재가 fail-open(N-9·N-10) | 점수 nullable + 부재 시 `Review(MlUnavailable)`, `BidNow`로 가는 경로가 타입에 없음 | `VerdictLadder.judge`의 `priorityScore?.value ?: return Review(...)`·`forceBidOutcome`의 null 가드, `VerdictLadderPropertyTest` negative property |
| 5 | 임계 다섯이 두 출처, 결과에 안 실림(N-5) | 정책 데이터 한 자리(`VerdictLadderPolicyData`) + **`BidNow`·`Review`** 의 각 reason 이 쓴 임계·실제 값을 함께 나름(verifier §3 실측 — 조작한 정책도 결과에서 드러난다). **정정(verifier L-1)** — `Skip`(`SkipReason.CapacityHold`/`LowPriority`)은 이 재현성을 갖지 않는다. 두 값 모두 값·임계 payload가 없어 「어떤 priority 가 어떤 임계에 걸렸는지」를 결과만으로 재현할 수 없다 — §3.6이 이를 요구하지 않아 계약 위반은 아니지만, DEC-12 A10의 피해가 남는 유일한 자리다 | `BidNowReason.PriorityAboveBidNowThreshold(priority, threshold)`·`ReviewReason.PriorityInReviewBand(...)` 등(`Skip`은 `SkipReason` 값만, payload 없음) |
| 6 | `review > bidNow`를 판정 시점에 조용히 수리(N-6) | 생성 불변식으로 그 조합 자체가 구성 불가 | `VerdictLadderPolicyData.init`의 `require(reviewThreshold <= bidNowThreshold)`, `VerdictLadderPolicyDataTest` |

부가로 **R-3**(legacy `probability_score`에 상한이 없었다)도 `UnitScore`의 `[0,1]` 생성
불변식으로 닫았다(`UnitScoreTest`).

### 3a. H-1 시정(수정 라운드 1) — golden 은 승인된 어휘까지만 잠근다, `verdict-001~004` 승격

**verifier H-1(2026-09-09 추가 표적)**: 신설 여덟(`verdict-005~012`)의 `verified_paths`가
`$.reasons`·`$.reasonCode`까지 잠가 **미승인 어휘·수**(reason 타입 이름 다섯 —
`PriorityAboveBidNowThreshold`·`ForceBidOverride`·`PriorityInReviewBand`·
`ScoreNotProvided`·`OverrideOutsidePlausibilityBand`, 승인 문서·결정 기록·이 slice
계약 어디에도 매치 0 — 와 그 payload 안의 임계 수)를 검증 대상으로 굳혔다. 동시에
005·006·011이 잠근 `CapacityHold`/`LowPriority`는 `verdict-001`이 **똑같이 잠근 채**
`insufficient-evidence`로 남아 같은 manifest 안에서 두 분류가 서로를 부정했다.

**운영자 결정 2026-09-09(팀장 전달) — 시정, 이번 라운드에서 실제로 반영한 것**:
1. `verdict-005`·`006`·`011`의 `verified_paths`는 `$.verdict`·`$.skipReason`만 유지
   (§3.6 + 이 결정이 정한 승인 어휘) — **변경 없음**.
2. `verdict-007`~`010`의 `verified_paths`에서 `$.reasons`를 뺐다 — `$.verdict`만 남는다.
3. `verdict-012`의 `verified_paths`에서 `$.reasonCode`·`$.silentlyDropped`·
   `$.silentlyAccepted`·`$.observableInResult`를 뺐다(뒤 셋은 verifier L-3 — 두 분기
   모두 상수라 실패할 수 없는 필드였다) — `$.overrideOutcome`만 남는다.
4. 여덟 전부의 `not_covered`에 reason 이름·임계 수·(010의) ML 부재 두 번째 형태·(012의)
   override 수용 경로가 **`:decision:test`(단위 test 층) 소관**임을 명시했다 — corpus가
   값을 잠그지 않을 뿐 커버리지를 잃지 않는다.
   `expected_*.json` 파일은 편집하지 않았다(내용은 그대로, `verified_paths`가 좁아져
   대조 대상만 줄었다 — `sha256`·`expected_sha256` 무변경).

**`verdict-001~004` 승격 — 시도했고, 실행으로 확인된 구조적 충돌 때문에 이번 라운드
에서는 반영하지 않았다(팀장에게 별도 보고, 임의로 선택하지 않았다).** 지시대로 넷을
`authoritative`+`source.kind: operator-decision`으로 올리고 `verified_paths`를 (1)의
잣대(001·002=`[$.verdict,$.skipReason]`, 003=`[$.verdict]`, 004=`[$.overrideOutcome]`)
에 맞춰 실제로 편집한 뒤 `./gradlew --no-daemon :app:test --tests '*Conformance*'`를
돌려봤다 — **86 tests, 5 failed**(`verdict-001~004` 「dispatch 표에 없는 case」 넷 +
완전성 test 하나). 원인: `TARGET_DOMAINS`에 `"verdict"`가 있어 `authoritative`로
바뀐 넷이 즉시 `targetCases()`에 잡히는데, 그 넷의 **입력 파일 형태**(`{"gateOutcome":
...}` · `{"baseVerdict": ..., "forceBid": {...}}` · `{"operatorFloorRateOverride":
...}`)가 신설 실행자가 읽는 `{"policy": ..., "input": ...}`/`{"override": ...,
"band": ...}` 형태와 달라 **manifest 문면만으로는 dispatch를 채울 수 없다** — 새
실행자·fixture 재구성(코드) 없이는 근본적으로 못 닫는다. **「코드는 건드리지 않는다」
와 「manifest 문면과 evidence 뿐」이 이 라운드의 반복·강조된 제약이라 그 제약을
우선했다** — 넷의 `classification`은 이번 라운드에서 **손대지 않고 되돌렸다**
(`insufficient-evidence` 그대로, 시도 흔적은 커밋하지 않았다). `:app:test --tests
'*Conformance*'`가 그린으로 82(74 기존 + 8 신설)를 유지함을 재확인했다(아래
commands.md). **남는 것**: `verdict-005`·`006`·`011`이 `CapacityHold`/`LowPriority`를
`authoritative`로 잠그는데 `verdict-001`·`002`는 같은 리터럴을 여전히
`insufficient-evidence`로 잠근 비대칭이 남는다 — H-1이 지목한 모순의 절반(reason 이름
다섯의 무근거 문제)은 이번 라운드로 닫혔지만, 이 비대칭은 **다음 라운드나 팀장의 별도
지시**(코드 승인 또는 다른 해소)로 넘긴다(알려진 제한 10번).

### 3b. `verdict-001~004` 승격(수정 라운드 1 후속, 운영자 결정 2026-09-09 선택지 (a))

위 §3a가 보고한 dispatch 충돌을 팀장에게 전달하자 **운영자가 (a)를 채택**했다 — 입력을
번역 슬 없이 이 slice 커널의 입력 계약으로 **재구성**하고 승격한다. 근거: 넷은 커널이
존재하기 전(M0, 2026-08-30)에 저작돼 `insufficient-evidence`로 한 번도 실행된 적이
없었다 — 입력 형태는 그 시점의 추정이었을 뿐 계약이 아니었다.

**지켜야 할 선(팀장 지시)과 준수 확인**:
- 각 case가 단언하는 규칙(`verifies`)은 불변 — 001·002는 「두 보류가 문장이 아니라
  `SkipReason`으로 구분된다」, 003은 force-bid 축, 004는 override 거부 축. 문면을
  고치지 않았다(diff 확인).
- `verified_paths`는 (1) 기준대로: 001·002 = `[$.verdict, $.skipReason]` · 003 =
  `[$.verdict]` · 004 = `[$.overrideOutcome]`.
- `approved_by_user`는 열둘 전부 `false` 유지(case 승인은 Phase 6).

**입력 재구성(의미 불변을 무엇으로 확인했는가)**:
- `verdict-001`(CapacityHold): `currentActiveBids=3 >= maxActiveBids=3` ∧
  `priority(0.6) < capacityHoldPriorityThreshold(0.8)` → `VerdictLadder.judge`의
  `capacityHoldOutcome` 분기(1순위)로 확정 `Skip(CapacityHold)`. 기대값은 이전과 같은
  리터럴(`verdict: Skip`, `skipReason: CapacityHold`)이다 — **규칙과 결과가 동일**.
- `verdict-002`(LowPriority): `currentActiveBids(1) < maxActiveBids(10)`(capacity-hold
  회피) ∧ `priority(0.2) < bidNowThreshold`(정상 승격 회피) ∧ `probability`·`matched`를
  **non-null**(0.1/0.1, force-bid 임계 미달)로 채워 `forceBidOutcome`이 `Review
  (MlUnavailable)`로 새지 않고 `null`을 반환하게 함(judge의 `?:` 체인이 `forceBidOutcome`
  까지 평가하려면 이 값들이 결측이면 안 된다 — `VerdictLadder.kt` 실측) ∧ `priority(0.2) <
  reviewThreshold(0.45)` → 최종 `Skip(LowPriority)`. 기대값 동일.
- `verdict-003`(force-bid): `priority(0.2) < bidNowThreshold`(정상 승격이었다면
  승격되지 않았을 조건) ∧ `probability(0.9)`·`matched(0.85)`가 각 임계(0.8/0.7) 이상 →
  `forceBidOutcome`이 `BidNow(ForceBidOverride(...))`로 확정. **`verifies`가 단언하는
  것(force-bid 우회가 유지되고 결과에 노출된다)이 그대로 성립** — 다만 기대값의
  세부 필드(`override.source`·`supersededReason` 등, M0 시점 서술형)는 커널이 내지
  않는 필드라 `verified_paths`에서 걷었다(003은 `$.verdict`만).
- `verdict-004`(override 거부): `override.rate=0.125`(원본의 "12.5%"를 fraction으로
  정규화, 값 자체는 바뀌지 않았다) ∧ `band=[0.25, 0.99]`(원본 그대로) →
  `FloorOverrideValidation.validate`가 `Rejected`. 기대값 동일(`overrideOutcome:
  Rejected`).
- 넷 다 실제로 `./gradlew --no-daemon :app:test --tests '*Conformance*'`(commands.md)를
  실행해 **기대값과 실제 커널 출력이 일치함을 실측**했다 — 손으로 기대값을 맞춘 게
  아니라 커널을 실행해서 확인했다.

**게이트가 일한 사례로 남긴다**: 「`authoritative`인데 dispatch 표에 없는 case가 없다」는
완전성 test가 없었다면 넷은 승격된 채 **조용히 실행되지 않았을 것**이다(입력 형태가
안 맞아 executor가 그 case를 못 찾았을 것) — 그 assertion이 이 재구성 작업을 강제했다.

**결과**: `verdict-001~004` 전부 `classification: authoritative`·`source.kind:
operator-decision`, `VERDICT_EXECUTORS`(`VerdictExecutors.kt`)에 dispatch 등록,
`./gradlew --no-daemon :app:test --tests '*Conformance*'` → **86 tests, 0 failed**
(기존 74 + 신설 8 + 재승격 4). §3a가 남긴 「005/006/011 대 001/002 분류 비대칭」은
**이제 해소됐다** — 열둘 전부 `authoritative`다. 알려진 제한 7·10을 갱신했다(아래).

### 4. `OPEN-DIC-03` 종결 근거와 신설 `OPEN`

`data-dictionary.md` §3.6·§13.2를 D-M4-6 (a)·조사 §3.1 실측으로 채우고
`capability-map.md`의 `OPEN-DIC-03` 행을 `~~OPEN-DIC-03~~`으로 닫았다(운영자 결정
2026-09-09). **신설 `OPEN-4B1-OFF-LADDER-DROPS`**(`capability-map.md` §14) — 사다리 밖
드롭 열셋(조사 §3.2 D-1~D-13) 중 무엇이 `SkipReason`류이고 무엇이 `Verdict` 밖 결과
타입인지는 4B-2(조합 use case, 「무엇을 판정 대상으로 삼는가」)가 정한다 — 이 slice가
멋대로 좁히지 않았다.

### 5. 역방향 파급 grep

`data-dictionary.md` — §3.6·§13.2 편집으로 **정정(verifier B-3)**: 「+13줄」이 아니라
**+14줄·-4줄(순증 +10줄)**이다(재산출: `git diff --numstat
13cf0f63da18e13f0e2befd519710a8635742004..a498fdc -- docs/discovery/data-dictionary.md`
→ `14  4`). 아래 좌표를 가리키는 참조 1건(`reports/evidence/m0/0c/commands.md:223` →
`data-dictionary.md:1551,1554`)이 영향권이나 **4B-1 in_scope 밖의 닫힌 slice
evidence**이고, 이미 `reports/evidence/m1/1c/checklist.md:148`이 「닫힌 slice evidence의
좌표가 낡는다」는 사실 자체를 선언해 두었다 — 새로 만드는 낡음이 아니라 그 원칙이 이미
포함하는 사례로 알려진 제한에만 등재한다(commands.md 상세).

`capability-map.md` — **정정(verifier B-1)**: 이전 판은 「`OPEN-DIC-03` 편집(+1줄)보다
위쪽 좌표만 참조가 있어 무관」이라 결론지었으나 **틀렸다**. 이 편집은 행 하나(3360)가
행 둘로 늘어난 **net +1**줄이고, `reports/evidence/harness/test-discovery-guard/commands.md:100`
이 가리키는 **`capability-map.md:3457`은 3360보다 아래라 영향권**이다. 재산출:
`git show 13cf0f63da18e13f0e2befd519710a8635742004:docs/discovery/capability-map.md | sed
-n '3457p'` → `OPEN-2A-CANONICAL-FORM` 행. 현재 HEAD의 `sed -n '3457p'` → `OPEN-STR-12` 행
(그 `OPEN-2A-CANONICAL-FORM` 행은 `3458p`로 밀려났다). **그 참조는 4B-1 in_scope 밖의
닫힌 harness 레인 evidence**라 고치지 않고 알려진 제한에 등재한다(아래 10번).

## 알려진 제한 (이 slice 종료 시점)

1. **사다리 밖 드롭 열셋의 어휘 분류**는 4B-2 소관(`OPEN-4B1-OFF-LADDER-DROPS`, 신설).
2. **임계 다섯의 운영 값**은 legacy-behavior 로만 test 정책에 쓰였다 —
   `OPEN-4B1-LADDER-THRESHOLDS`(운영자 승인 대상).
3. **용량이 네 번 세는 것의 합쳐진 효과**(조사 §7.1)와 **하한 override 가 판정을
   얼마나 바꾸는가**(조사 §6.3)는 이 slice 가 재측정하지 않았다(`OPEN-4B1-05`·
   `OPEN-4B1-06`, 조사 §10) — 재측정이 필요하면 별도 slice.
4. **`review_required`(ML regime 신호, 조사 §7.3)를 V2 어느 층이 만드는가**는 미정
   (`OPEN-4B1-07`) — 이 slice의 `MlUnavailableReason`은 「점수가 없다」만 다루고
   「점수는 있지만 ML 이 review 를 권고한다」는 다루지 않는다.
5. **`LicenseVerdict.Ineligible`과 `Verdict`의 관계**(참조 vs 복제, `OPEN-4B1-02`)는
   미정 — 4B-1은 그 축을 건드리지 않는다(out_of_scope).
6. **`AnalysisBudgetExhausted`·`SimilarityProjectionNotReady`가 `Verdict` 안인지
   밖인지**(`OPEN-4B1-03`) — 이 slice의 `Verdict`에는 그 갈래가 없다(사다리 밖).
7. ~~`verdict-001~004`는 `insufficient-evidence`로 남았다~~ — **운영자 결정
   2026-09-09 선택지 (a)(§3b)로 넷 다 `authoritative`로 승격했다.** 입력을 M0 시점
   추정 형태에서 이 slice 커널의 입력 계약으로 재구성했고, `verifies`가 단언하는
   규칙과 기대값의 의미는 불변임을 실행으로 확인했다(§3b). 005/006/011 대 001/002
   분류 비대칭은 **해소됐다** — 열둘 전부 `authoritative`다.
8. **조합 use case(4B-2)가 아직 없다** — `VerdictLadder.judge`를 실제 파이프라인에
   배선하는 코드는 이 slice 밖이다.
9. **닫힌 slice/하네스 레인 evidence의 낡은 좌표 2건**(§5 참고, 알려진 제한으로만
   등재, 수정하지 않는다) — `m0/0c/commands.md:223`(data-dictionary.md 좌표)·
   `test-discovery-guard/commands.md:100`(capability-map.md:3457 좌표, verifier B-1).
10. ~~[블로커, 미해결] verdict-001~004 승격이 dispatch 완전성 test를 깬다~~ —
    **해소됐다(§3b).** 1차 시도(`authoritative`+`verified_paths`만 편집, 입력은 그대로
    둠)는 실측으로 **86 tests, 5 failed**를 냈다(`TARGET_DOMAINS`의 `"verdict"`가
    즉시 `targetCases()`에 넷을 잡지만 M0 시점 입력 형태 — `{"gateOutcome": ...}` ·
    `{"baseVerdict": ..., "forceBid": {...}}` · `{"operatorFloorRateOverride": ...}`
    — 가 신설 실행자 계약과 달라 dispatch가 안 됨). 운영자가 선택지 (a)(입력을 커널
    계약으로 재구성)를 채택해 `fixtures/input`·`fixtures/expected/verdict-00{1,2,3,4}
    .json`을 다시 쓰고 `VerdictExecutors.kt`의 `VERDICT_EXECUTORS`에 등록한 뒤
    재실행 — **86 tests, 0 failed**(§3b). 완전성 test가 이 재구성을 강제한 게이트로
    일한 사례다.
11. **B-2(장부층, 등재만)** — `base(13cf0f6)..head` range에 4C-1 evidence 커밋 둘
    (`52c94e6`·`3752b49`, `reports/evidence/m4/4c1/**` 4파일 M)과 4B-1 계약 커밋 둘
    (`4e07682`·`e2bfb99`)이 섞여 있으나 「하네스 레인 변경」 절은 `ea79355` 하나만
    적는다 — 4C-1 scope.md의 「범위 밖 range 잡음(선언)」 선례와 같은 형태로 처리한다
    (rollback이 이미 그 커밋들의 경로를 대상에서 제외해 실해는 없다).
12. **B-4(장부층, 문면 정정)** — commands.md가 **이 slice가 만든 파일**의 좌표
    (`VerdictExecutors.kt:77`)를 인용했다 — 낡는 좌표 규격(2026-09-02)이 금지하는
    형태다(다른 slice의 닫힌 evidence를 가리키는 파급 목록 좌표와는 다르다). 절 제목·
    인용문으로 정정한다(commands.md 참고).

## rollback

정본은 `rollback.md`.
