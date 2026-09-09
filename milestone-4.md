# 마일스톤 4 — Workflow, event, 상태 제어와 ML gateway

## 목표

수집된 canonical fact, 전략, 자격, ML 후보, 알림 요청을 하나의 거대한 service 함수가 아닌
명시적 use case와 상태 전이로 조합한다. 모든 외부 효과는 port 뒤에 두고 fake로 검증한다.

## 선행 조건

- M1~M3 승인
- 상태 전이와 event/outbox ADR 승인

## 구현 대상

### Slice 4A — Strategy edit state machine

- `WaitingForValue -> WaitingForConfirmation -> Applied/Cancelled/Expired`
- 허용 command와 invalid transition
- actor/operator scope
- timeout과 중복 command
- 상태와 event의 version

Telegram DTO는 adapter에만 존재하고 domain state가 Telegram library를 import하지 않는다.

**M4 착수·4A 착수 2026-09-08** — 선행 조건 충족(M1~M3 승인 — M3 잔여 0, `milestone-3.md` 「M3 완료 2026-09-08」 · 상태 전이·event/outbox ADR 0004·0005 는
M0 승인). 준비 정본 `reports/evidence/m4/prep/m4-prep.md`(slice 지도·D-M4-1~8 추천안), 4A 계약 `reports/evidence/m4/4a/scope.md`(base `9948c6e`).
**레인 격리** — 다른 세션이 `main` 에서 M3 후속을 진행 중이라 M4 는 별도 worktree + 브랜치 `m4/2026-09-08` 에서 산다(`main` 병합은 종결 뒤 사용자 승인 사항).
착수 전 운영자 결정: **시작 slice 4A** · **D-M4-1 (a)** 채널 독립 use case 만 세우고 Telegram 어댑터는 `후속`(`OPEN-STR-12` 활성 유지 — 상태 기계는
웹 편집 6A 에도 쓰이므로 채택과 독립) · **D-4A-2 (a)** 상태 기계를 `workflow`(application) 안에 순수 Kotlin 으로(도메인 게이트가 `isDomain=false` 라
걸리지 않으므로 채널 타입 경계는 소스 스캔 test 가 진다) · **D-4A-1 (a)** `strategy-edit-*` corpus 신설(`authored-from-approved-spec`).
Phase 2.5 설계 검토는 세션 모델이 직접 했다(`_workspace/m4-4a/02_design-review.md`) — 전이표 밖 거부·이중 적용·채널 타입 유입을 각각 소진 `when`·
상태 종단성·allow-list 소스 스캔의 **구성형**으로 닫고, `EventSink` port 는 STR-07 이 `폐기`로 못 박은 「호출자 규율」 재현을 막기 위해 남긴다
(발행 비원자성은 4C 가 닫을 **알려진 제한**으로 선언). 과잉으로 뺀 셋: 처리한 command id 전체 집합 · 별도 `Effect` 목록 타입 · 만료 sweep use case.

**4A 구현 2026-09-08(구현 레인, 사용자 승인 대기)** — `workflow/src/main/kotlin/bidvector/workflow/strategy/**`(전이표·`EditSession`·`EditCommand`·
`TransitionOutcome`·port 넷·`EditStrategyWorkflow` use case) 신설. acceptance S-0~S-6 전부 exit 0(evidence
`reports/evidence/m4/4a/commands.md`). fixture 다섯(`strategy-edit-001~005`, `authored-from-approved-spec`,
`review.approved_by_user: false` — 운영자 승인은 Phase 6).

**verifier 1차 not-ready(2026-09-08) → 수정 라운드 1 반영.** 1차 리뷰가 산출물층 high 2건(H-1·H-2)을 냈다 —
「`TransitionOutcome.Applied` 만 저장 인자를 낸다」는 타입 근거가 **거짓**이었다(`validate()`·
`StrategyValidation.Valid.strategy` 가 public 이라 `OperatorStrategy` 는 어디서든 얻을 수 있고, verifier 가
`app` test 에 `StrategyRepository` 를 구현해 `validate()` 결과를 직접 `save()` 에 넘기는 클래스로 그 우회를
컴파일까지 실증했다). **수정**: `StrategyRepository.save` 의 인자를 `OperatorStrategy` 에서
`AppliedStrategy`(`internal constructor`, `workflow` 모듈 밖에서 생성 불가)로 바꿔 「값을 안다」와 「저장을
실행할 수 있다」를 구조적으로 분리했다(M3/3B-2 `MaskedKonepsItem` 과 같은 갈래). 재현 클론에서 verifier 의
우회 클래스를 그대로 심어 `Cannot access 'constructor(...): AppliedStrategy': it is internal` 로 컴파일이
거부됨을, 그리고 정상 배선은 그대로 컴파일·통과함을 확인했다. **`OPEN-4A-WRITE-PATH-GATE` 는 이 수정으로
종결한다** — 「게이트로 표현 불가」는 verifier 의 ArchUnit 반증 실측으로도 틀렸지만, 통로 타입이 컴파일
층에서 이미 닫아 별도 게이트가 불필요해졌다(4A port 밖에서 자체 persistence 경로를 새로 만드는 것은 이
slice 위협 모델 경계 밖, 3D/4C write 게이트 소관).

**수정 라운드 1 이 함께 닫은 것**: N-1(`begin()` 이 기존 비종단 세션을 가드 없이 덮어써 `apply()` 밖에서
상태가 바뀔 수 있었다 — `BeginOutcome`(Started/Rejected) 신설로 관측 가능한 거부로 전환) · M-1(판정 순서
「만료→중복」의 판별 test 부재 — 변이 실측으로 회귀 보호 확인) · M-2(정책 생성 불변식 `require` 둘의
회귀 보호 부재 — 전용 test 신설) · M-3(전략 저장 실패 시 세션이 먼저 굳어 편집이 조용히 영구 소실될 수
있었다 — 저장 순서를 `strategies→events→sessions` 로 교정).

**verifier 표적 재검증(r2, 2026-09-09) → 수정 라운드 2 반영.** r1 의 「우회 (3) 구조적 폐쇄」
결론이 반증됐다 — `AppliedStrategy` 는 위조를 막았지만 **획득**을 막지 못했다. `beginSession`·
`apply` 가 public top-level 함수라 `workflow` 밖에서 직접 몰아 호출부가 고른 값으로 정당한
`AppliedStrategy` 를 얻고 `EditStrategyWorkflow` 를 거치지 않고 저장할 수 있었다(H-3, 실측:
revision=778 저장 + 발행 0 — STR-07 이 `폐기`로 못 박은 legacy 결함 재현). 같은 뿌리로
`beginSession` 결과를 세션 port 에 직접 넘기면 활성 세션도 덮어썼다(M-4). **수정**: `beginSession`·
`apply`·`expireIfDue` 를 `internal` 로 내려 `workflow` 밖에서 호출 자체가 컴파일되지 않게
했다 — `EditSession`·`TransitionOutcome` 을 얻는 유일한 경로가 `EditStrategyWorkflow` 가
된다. app 의 corpus 실행자를 `EditStrategyWorkflow` + fake port 경유로 재작성(fixture
input/expected 무변경). 재현 clone 에서 verifier 의 두 probe(H-3 커널 직몰이·M-4 세션
덮어쓰기)가 이제 컴파일 거부됨을, 정상 배선·corpus 다섯은 그대로 통과함을 확인했다.

**함께 닫은 것**: M-5(이번 수정이 새로 연 것 — `begin()` 가드가 저장된 state 만 봐서 시각상
만료됐지만 fold 되지 않은 세션이 비종단으로 읽혀 같은 `EditSessionId` 의 새 편집을 무기한
막을 수 있었다. 가드 전에 `expireIfDue` 를 적용하도록 수정) · L-5(만료 fold 의 세션 저장이
`Rejected` 경로에서도 영속되는지 재는 test 신설) · L-6(`TransitionOutcome.Applied` 에
`@ConsistentCopyVisibility` + `internal constructor` — 다른 통로 타입과 관례 정렬) · L-7(S-3b
술어를 소문자 세그먼트 하나 이상으로 넓혀 `telegram.Bot`·`telegram_api.Bot` 형태를 포착).

**장부층 일괄(2026-09-09) — verifier r3 `ready-for-review`.** 산출물층 blocker/high 0건.
새 라운드 없이 low 셋 + 장부층 low 둘을 한 커밋으로 처리: L-8(`TransitionOutcome.Applied.
applied`가 use case 반환값으로 새 나가 나중에 낡은 값으로 재저장(되감기)될 수 있었다 —
`internal val`로 내려 닫음, `:workflow:test`·`:app:compileTestKotlin` 무영향 실측) · L-9
(`begin()`의 중간 `sessions.save(folded)`가 같은 id 경로에서 곧바로 덮이는 죽은 쓰기 —
코드는 유지, 「fold 가 영속된다」로 읽히던 KDoc을 정정) · L-10(우회 (3) 폐쇄는 Kotlin
가시성 1층이 아니라 **그 위에 `sourceLanguageGate`(`.java` 소스 자체를 거부)가 겹치는
2층**임을 명시) · B-6(S-3b 강화 시 오탐 규모를 「46건」이라 적었던 것이 재현되지 않아
직접 재산출 — 대문자 단일 세그먼트 허용형 정규식으로 총 매치 97건·고유 문자열 42종,
판단 자체는 불변) · B-7(「닫힌 것/경계 밖」 서술에 L-8·L-10 반영).

알려진 제한(갱신): 이벤트 발행·세션 전진의 원자성 부재(저장 성공 뒤 발행 실패는 다음 재전달이
`StaleRevision` 으로 정직하게 거부되나, 세션이 그 사이 전진하지 않는 잔여 창은 남는다 — 4C 트랜잭션
outbox 가 닫는다) · 세션 영속 실 구현 부재(4C/3D) · 만료 트리거(sweep) 배선 부재 · `System` actor 확인
경로 미구현(D-4A-5) · Telegram 어댑터 없음(`OPEN-STR-12` 활성) · **S-3b 술어는 대문자로 시작하는
단일 세그먼트 루트(`Telegram.Bot`)를 여전히 잡지 못한다** — 잡으려면 `EditSessionState.Applied`
같은 이 패키지 자신의 정당한 sealed 하위 타입 접근(재산출 총 매치 97건·고유 42종, 위 참고)까지
오탐해 강화하지 않기로 했다(소문자 세그먼트 판별은 L-7 로 이미 넓혔다) · 우회 (3) 폐쇄는 Kotlin
가시성 + `sourceLanguageGate` 의 2층이다(L-10, `.java` 소스 경로는 1층만으론 안 막힘).

**4A 종결 2026-09-09(사용자 승인) · fixture 다섯 승인 · D-4A-3 timeout=15분 확정** — verifier r3
`ready-for-review`(산출물층 blocker/high 0) 위에서 승인(evidence `reports/evidence/m4/4a/
checklist.md` 「사용자 승인」 절, 최종 산출물 커밋은 장부층 일괄 `d567adc` — 이 승인 반영
자체는 뒤따르는 문서·manifest 전용 커밋이다, SHA 는 `git log`로 확인). **재작업 2/5.** 산출:
전이 커널(`apply`·`beginSession`·`expireIfDue`, 전부 `internal`) · `EditStrategyWorkflow`
use case(begin/provideValue/confirm/requestEdit/cancel/expire) · port 넷(`StrategyRepository`·
`EditSessionRepository`·`Clock`·`EventSink`) · 통로 타입 둘(`AppliedStrategy`·
`TransitionOutcome.Applied`, 둘 다 `internal constructor`) · corpus `strategy-edit-001~005`
(`EditStrategyWorkflow` + fake port 경유로 실행) · `gate.tests.workflow`(6 class, 37 test).
`strategy-edit-*` fixture 다섯의 `review.approved_by_user` 를 `true` 로 올렸다(manifest·
golden-manifest.json 반영, 입력/기대값은 착수 이후 전 라운드 무변경). D-4A-3 timeout 값은
착수 시 placeholder(구조만)였다가 이 승인으로 **15분**이 정책값으로 확정됐다(정본
`reports/evidence/m4/4a/policy-values.md` §1, 3A `KONEPS_COLLECTION_POLICY` 관례).

**검증이 드러낸 것 — 셋 다 정적 판독으로는 나오지 않았다.** ① 「저장 인자를 내는 유일한 자리가
`TransitionOutcome.Applied`」라는 착수 시 타입 근거가 **거짓**이었다(`validate()`가 public 이라
그 결과 `OperatorStrategy`는 어느 모듈에서든 얻을 수 있다 — r1 verifier가 `app` test에 그 우회를
컴파일까지 실증). ② 그 자리를 메운 통로 타입(`AppliedStrategy`, H-1/H-2)은 **위조**만 막고
**획득**은 막지 못했다 — `beginSession`·`apply`가 public이라 커널을 직접 몰아 이벤트 발행 없이
저장을 실행하는 STR-07 결함 형태가 그대로 재현됐다(H-3, r2 verifier 실측: revision=778 저장 +
발행 0). 커널 자체를 `internal`로 내려서야 실질적으로 닫혔다. ③ H-3/M-4를 닫은 N-1 계열 수정
(`begin()` 가드)이 **그 자신의 새 결함**(M-5 — 만료된 세션이 fold 되지 않으면 같은 id의 새 편집을
무기한 막을 수 있었다)을 열었고, 표적 재검증이 그것을 잡았다.

**반복이 발산이 아니라 수렴이었다는 판정(verifier r3 §5).** 세 라운드가 같은 계열(「use case가
막지만 타입이 열어 둔다」)이었으나 우회에 필요한 권한은 라운드마다 좁혀졌다 — r1: *아무
모듈이나* `validate()` 결과로 저장 인자를 만들 수 있었음 → r2: *커널 두 함수를 부를 수 있는
모듈*로 좁혀짐 → r3 이후 남은 잔여(L-8, 반환값 재사용)는 *use case를 정상 호출해 결과를 받은
주체가 그 값을 재사용하는 것*뿐이고, 이는 정의상 port 구현자가 이미 가진 권한과 같아져 더 좁힐
실익이 없다(그 이상은 4A가 저장소 구현까지 소유해야 하는 4C/3D의 몫). r3가 지목한 재발 방지책 —
**「이 slice가 새로 public으로 내놓는 타입·함수·반환값을 열거하고 각각이 그 값을 쥔 자에게 무엇을
허락하는지 적는다」**(Phase 2.5의 「값 위조 축」에 빠져 있던 「값 획득 축」) — 는 팀장이 하네스에
직접 반영한다.

**worktree 격리의 실측 효과.** `git log --oneline 9948c6e4056bbf71fa6683aa67d30c2a49fc6eae..HEAD
-- CLAUDE.md .claude/` 는 착수부터 검증·종결 승인(`d567adc`)까지 **0건**이었다(`reports/evidence/
m4/4a/scope.md` 「하네스 레인 변경」 절 실측) — 다른 세션이 `main`에서 진행한 M3 후속과 공유
working tree 없이 별도 브랜치·worktree(`m4/2026-09-08`)에 살아, 2026-09-02 스테이징 규율이
다루던 레인 혼입이 slice 진행 기간 내내 **구조적으로 발생하지 않았다**(비교: M1/1A는 같은
문제를 사후 감사로 발견). **종결 승인 이후(2026-09-09) 같은 브랜치에 하네스 커밋 1건**
(`harness(v2-slice-pipeline): Phase 2.5 에 (2b) 값 획득 축 신설` — 이 slice의 r2·r3 실측이
근거)이 붙었다 — slice 산출물이 아니고 검증·승인이 끝난 뒤라 판정 대상이 아니며, scope.md
「하네스 레인 변경」 절에 선언돼 있다. `main`과 아직 병합 전이라 그 개선은 병합 시점에 전파된다.

**알려진 제한 일곱(종결 시점)**: ①이벤트 발행·세션 전진의 원자성 부재(저장 성공 뒤 발행 실패는
`StaleRevision`으로 정직하게 거부되나 그 사이 세션 미전진 잔여 창은 남음 — 4C 트랜잭션 outbox
소관) ②세션 영속 실 구현 부재 + `sessionVersion` 낙관적 동시성 미검증(port+fake까지가 이 slice
경계, 4C/3D) ③만료 트리거(sweep) 배선 부재(`expireIfDue`/`expire()`는 순수 함수로 존재, 스케줄러
없음 — 단 `begin()` 자체는 M-5로 sweep 없이도 안전) ④`System` actor 확인 경로 미구현(D-4A-5,
STR-15 `후속`) ⑤Telegram 어댑터 없음(`OPEN-STR-12` 활성 유지) ⑥S-3b 술어가 대문자 단일 세그먼트
루트(`Telegram.Bot`)는 못 잡음(강화 시 이 패키지 자신의 sealed 하위 타입 접근 다수를 오탐) ⑦4A
port를 거치지 않는 자체 persistence는 4A 코드로 막을 수 없음(3D/4C 명시 인계). **`OPEN-4A-
WRITE-PATH-GATE`는 종결** — 폐쇄는 Kotlin 가시성(1층) + `sourceLanguageGate`(2층)이고, 그 밖은
위 ⑦로 3D/4C에 넘긴다.

**다음 slice는 4C**(계약은 팀장이 별도 작성).

### Slice 4B — application use case

- notice 수집 완료
- qualification 평가
- low-cost strategy filter
- 필요 시 ML inference 요청
- decision 후보 조립
- state 저장과 domain event 기록

transaction 경계와 실패 시 상태를 명시한다. catch-all exception으로 성공처럼 계속하지
않는다.

**4B를 4B-1/4B-2로 나눈 이유(착수 2026-09-09)** — 위 「decision 후보 조립」은 **조립할
대상이 없었다**: `decision` 모듈에 투찰 판정 `Verdict` 축이 실재하지 않았다(실측:
provenance·floor shortfall 커널 둘뿐). `data-dictionary.md` §3.6이 어휘를 승인했으나
§13.2가 구현을 M1에 인계했고 `milestone-1.md` 1A~1E에 게이트 사다리 slice가 없었다 —
`capability-map.md`가 이 어긋남을 이미 **「갈림」**으로 등재해 둔 자리였다. 운영자가
**4B-1(도메인 커널) / 4B-2(조합 use case)** 분할로 해소했다 — 도메인 커널과
application 조합을 같은 slice에 섞지 않는다.

**4B-1 구현(2026-09-09)** — 조사(`_workspace/m4-4b1/01_scout_verdict_ladder.md`, 608줄)
가 legacy `allocation_core.py`에서 특정한 여섯 실패 형태를 각각 뒤집었다:

1. **부작위 skip**(legacy의 두 `skip`이 대입이 아니라 초기값 유지 부작위) →
   **모든 분기가 결과를 명시 반환**(`VerdictLadder.judge`의 guard 함수 체인, 소진 `?:`).
2. **문장 사유**(두 보류가 같은 `"skip"`이고 한국어 문장으로만 구분) →
   **`SkipReason` sealed + 값**(`CapacityHold`·`LowPriority`, 문자열 아님).
3. **force-bid와 정상 승격이 같은 문장** → **`BidNowReason` 타입 분리**
   (`PriorityAboveBidNowThreshold` vs `ForceBidOverride`, 둘 다 `internal constructor`) —
   우회의 존재를 감춘 것이 결함이었지 우회 자체가 아니다(`OPEN-STR-03`).
4. **ML 부재가 fail-open**(점수 없음이 어떤 판정으로도 새지 않고 그냥 진행) →
   **부재를 구분하는 타입**(`ReviewReason.MlUnavailable`) — `Verdict.judge`의 타입
   서명 자체에 「부재에서 `BidNow`로 가는 경로」가 없다(D-M4-6 (a)).
5. **임계 두 출처**(legacy는 영속 전략 2 + settings 3에 흩어짐, DEC-12 A10) →
   **정책 데이터 한 자리**(`VerdictLadderPolicyData`)로 모으고 각 reason이 쓴 임계·
   실제 값을 결과에 싣는다(재현 가능성).
6. **침묵 수리**(legacy는 `review_threshold > bid_now_threshold`가 깨지면 판정 시점에
   조용히 `review_threshold = bid_now_threshold`로 고쳐 저장값과 판정값이 갈렸다) →
   **생성 불변식**(`VerdictLadderPolicyData` 생성자가 `reviewThreshold <= bidNowThreshold`
   를 강제 — 그 조합 자체를 구성 불가로 만들어 침묵 수리 자리를 없앤다).

**`OPEN-DIC-03` 종결과 신설 `OPEN-4B1-OFF-LADDER-DROPS`** — 조사가 사다리 안에서 확정적
으로 갈리는 사유가 정확히 둘(`CapacityHold`·`LowPriority`)임을 전수해 `data-dictionary.md`
§3.6·§13.2의 `OPEN-DIC-03`을 종결했다. 사다리 **밖**에서 공고가 조용히 사라지는 지점
열셋(조사 §3.2)은 이 어휘가 아니다 — 신설 `OPEN-4B1-OFF-LADDER-DROPS`(`capability-map.md`
§14)로 등재하고 **4B-2**(무엇을 판정 대상으로 삼는가)에 배정했다.

**corpus 12건 승격·승인과 되돌림 경로의 두 번째 사용** — `verdict-005`~`012`(신설
authored-from-approved-spec)와 `verdict-001`~`004`(M0/2026-08-30에 커널 이전 저작돼
`insufficient-evidence`로 강등, 한 번도 실행된 적 없음)를 전부 `authoritative`로
승격했다. `verdict-001`~`004`는 운영자 결정(선택지 (a))으로 **입력을 이 slice 커널의
입력 계약으로 재구성**했다 — dispatch 완전성 test가 형태 불일치를 86 tests/5 failed로
잡은 뒤 재구성해 86/0으로 닫았다. `koneps-collection` 27건에 이은 **되돌림 경로의 두
번째 사용**이다. 열둘 전부 사용자 승인(`approved_by_user: true`, `reports/evidence/
m4/4b1/checklist.md` 「사용자 승인」 절) — case 승인은 slice 종결 승인과 별개 항목으로
받았다.

**4B-1 종결 2026-09-09(사용자 승인)** — verifier r2 `ready-for-review`(산출물층
blocker/high 0) 위에서 승인(evidence `reports/evidence/m4/4b1/checklist.md` 「사용자
승인」 절, 최종 산출물 커밋 `efd2d60dd9e4ebf354a871fac11121aa91fa45bc`). **재작업 1/5.**
값 획득 축(설계 검토 (2)) 실측: `Verdict`·`BidNowReason`·`ReviewReason` 생성자와
`SkipReason` 하위 타입 전부 `internal`이고 사다리를 지나지 않고 만들면 다른 모듈(`app`)
에서 컴파일이 거부됨을 확인했다 — `VerdictLadder.judge`·`FloorOverrideValidation.validate`
자체는 `decision`이 도메인 모듈이라 `app` conformance 실행자가 직접 부르는 것이 1C·1D
관례이므로 public이 옳다(판정을 받는 것 자체는 권한이 아니다).

**알려진 제한(종결 시점)**: 임계 다섯의 운영 값은 legacy-behavior로만 test 정책에
쓰였다(`OPEN-4B1-LADDER-THRESHOLDS`, 운영자 승인 대상) · 용량이 네 번 세는 것의 합쳐진
효과와 하한 override가 판정을 얼마나 바꾸는가는 이 slice가 재측정하지 않았다
(`OPEN-4B1-05`·`OPEN-4B1-06`) · `review_required`(ML regime 신호)를 V2 어느 층이
만드는가 미정(`OPEN-4B1-07`) · `LicenseVerdict.Ineligible`과 `Verdict`의 관계 미정
(`OPEN-4B1-02`, out of scope) · `AnalysisBudgetExhausted`·`SimilarityProjectionNotReady`
가 `Verdict` 안인지 밖인지 미정(`OPEN-4B1-03`) · 조합 use case(4B-2)가 아직 없다 —
`VerdictLadder.judge`를 실제 파이프라인에 배선하는 코드는 이 slice 밖이다.

**다음 slice는 4B-2**(조합 use case, 계약은 팀장이 별도 작성).

**4B-2 구현 2026-09-09(구현 레인, 검증 대기)** — 조사(`_workspace/m4-4b2/01_scout_composition.md`,
786줄)가 실측한 legacy 실패 형태 여덟을 각각 뒤집는 조합 use case
(`workflow/src/main/kotlin/bidvector/workflow/evaluation/**`) 신설:
`EvaluateCandidatesUseCase`(판정은 공고당 한 번·전략/용량은 진입에서 한 번·후보 단위
격리·catch-all 없음) · 판정 결과 어휘 `CandidateEvaluation`(`Reached`/`NotReached`,
둘 다 값) · 탈락 사유 `EvaluationDropReason`(기존 축 `WatchVerdict`(1E)·`LicenseVerdict`
(1C)·`NoticeStatus`(3A)를 그대로 싣고 어느 축도 소유하지 않은 넷만 최소 신설) · port
일곱(`CandidateSourcePort`·`WatchSubjectPort`·`LicenseGatePort`·`MlAnalysisPort`·
`CapacityPort`·`NotificationRequestPort`·`CorrelationIdFactory`). acceptance S-0~S-6
전부 exit 0(evidence `reports/evidence/m4/4b2/commands.md`).

**`OPEN-4B1-OFF-LADDER-DROPS` 종결과 `OPEN-4B1-03`의 답** — 사다리 밖 드롭 열셋(D-1~D-13)
의 어휘 소유를 전부 정했다(`docs/discovery/capability-map.md`). 그 과정에서 4B-1이
남긴 「`AnalysisBudgetExhausted`·`SimilarityProjectionNotReady`가 `Verdict` 안인지
밖인지 미정」(`OPEN-4B1-03`, 위 4B-1 알려진 제한)의 답도 함께 나왔다 — **밖이다.** 둘
다 사다리에 이르기 **전** 단계의 `EvaluationDropReason`이고 `Verdict`(4B-1이 이미
종결한 sealed)에는 없다 — `Verdict`를 다시 여는 것이 아니라 그 앞에 새 단계를 두는
형태로 닫힌다.

**값 획득 축 실측(설계 검토 (2))** — 「닫는다」(판정 결과·알림 요청 생성자, `app`
모듈 위조 probe 셋 전부 거부) · 「연다」(use case 클래스 자체·`correlationId` 값 타입,
컴파일 성공) · 「경계로 처리」(port 인터페이스, 배선 주체가 이미 그 값을 받기만 하고
스스로 지어낼 수 없음을 확인) 셋 다 실측했다(`reports/evidence/m4/4b2/checklist.md`).

**조사 신설 `OPEN-4B2-*` 여섯 중 다섯을 `capability-map.md`에 등재**했다(운영 관측·실
저장이 있어야 답할 수 있는 것 넷 + 도메인 명세 판단이 필요한 것 하나). 나머지 하나
(`OPEN-4B2-3`, 같은 run 안 용량 선점 순서)는 이 slice의 설계(용량 스냅샷을 run당 한
번만 읽어 모든 후보가 공유)가 구조적으로 닫아 등재하지 않았다.

### Slice 4C — event/outbox

- `StrategyUpdated`, `NoticeQualified`, `PredictionRequested`, `DecisionPrepared`,
  `NotificationRequested`
- event id, aggregate version, idempotency/correlation/causation id
- DB state와 outbox의 atomic commit
- consumer inbox/dedup
- duplicate, out-of-order, crash-after-commit test

Spring in-process event는 로컬 관찰용으로 쓸 수 있지만, 신뢰성 있는 외부 side effect의
유일한 보장으로 사용하지 않는다.

**4C를 4C-1/4C-2로 나눈 이유(착수 2026-09-09)** — 다른 세션이 `main`에서 M3 후속을
진행 중이었고 그 브랜치가 이미 `db/migration/`에 V4·V5를 만지고 있었다(`git log
--name-only` 실측). 이 브랜치(`m4/2026-09-08`)의 base에는 V4·V5가 없어, 4C가 outbox
테이블을 만들면 **Flyway 버전 번호가 정면 충돌**한다. 그래서 **4C-1은 `workflow`
모듈 한정**(충돌 0)으로 이벤트 봉투 어휘·outbox port·dedup을 세우고, **4C-2**
(Flyway·persistence 어댑터·DB↔outbox 원자성·claim 경합)는 `main` 병합 뒤로 미뤘다
(`reports/evidence/m4/4c1/scope.md`).

**4C-1 구현·검증(2026-09-09)** — `workflow/src/main/kotlin/bidvector/workflow/event/**`
(`EventEnvelope<P>`·`OutboxEntryState` sealed+전이표·`OutboxPort`/`InboxPort`/
`EventIdFactory` port·dedup 순수 판단·`OutboxEventSink`, 4A `EventSink`의 첫 실구현)
신설. acceptance S-0~S-6 전부 exit 0.

**검증이 드러낸 것 — 통로 타입 하나로는 안 닫혔다.** verifier r1의 H-1: 초기 판은
`EventEnvelope.restore`가 `public`이라 **저장소 복원 진입점 자체가 위조 재료를 내주는
자리**였다 — 위조는 막았어도(H-2에서 이미 닫힘) **획득**이 열려 있어, 어느 모듈에서든
`actor = null`인 봉투를 `restore`로 지어 `register`에 넘기면 배달 스트림에 주입됐다
(4A H-3와 같은 계열 — 통로 타입의 획득 축이 위조 축과 별개로 닫혀야 한다는 교훈의
두 번째 사례). **가시성 한 단어(`internal fun restore`)로는 부족했다** — 4C-2의 실
persistence 어댑터가 DB 행을 봉투로 되살려야 하는 순간 그 함수를 다시 열어야 했고
같은 구멍이 되돌아왔을 것이다. 그래서 `OutboxPort.claim()`의 **반환 타입 자체를
바꿨다** — `EventEnvelope`가 아니라 `ClaimedOutboxRow`(완성 전 원시 필드)만 돌려주고,
어댑터는 `EventEnvelope`를 만들지 않는다. 원시 행 → 완성 봉투 복원은 `workflow` 안의
`internal` 매핑(`OutboxEntry.restore`)이 지고, 그 함수는 4C-2에서도 계속 `internal`일
수 있다(호출부가 미래의 배달 오케스트레이션 use case이지 어댑터가 아니기 때문이다) —
**4C-2에서 되열 필요가 없다.**

**`OPEN-OPS-10` 종결과 잔여 셋** — D-M4-5 (a)(`Pending → Claimed → Delivered | Failed
(final) | Isolated`)를 `data-dictionary.md` §2.2.5에 등재해 `OPEN-OPS-10`을 종결했다
(`capability-map.md` §14.2 갱신). 잔여 셋: `OPEN-ADR-13`(db-scheduler 실패 기본값,
4C-1에는 스케줄러가 없다) · `OPEN-ADR-12`(lease 어댑터, 4C-2/후속) ·
`OPEN-4C1-TX-CONTRACT-UNVERIFIED`(등록이 도메인 write와 같은 트랜잭션이라는 계약은
4C-1에서 문면 선언일 뿐 강제되지 않는다 — 4C-2가 실 저장으로 닫을 때까지 **활성**).

**알려진 제한(종결 시점)**: DB↔outbox 원자성·crash-after-commit·claim 경합은 실 저장이
있어야 잰다(4C-2, `OPEN-4C1-TX-CONTRACT-UNVERIFIED`) · `OutboxPort`·`InboxPort` 구현은
test fake만(4C-2가 persistence 어댑터를 짓는다) · `transitionOutbox`·`OutboxEntry.restore`
둘 다 유일한 정당한 호출부(배달 오케스트레이션 use case)가 아직 없다 · `EventIdFactory`의
실 기제(UUIDv7 등) 미정 · **`OutboxEventSink`는 public이라 자기 `OutboxPort`·
`EventIdFactory`·`Clock`을 조립해 밖에서 새로 지을 수 있다**(verifier r2 L-4) — 결함이
아니라 sink의 불변식(`actor` non-null·`idempotencyKey`는 revision 파생·
`correlationId == eventId`)이 지키는 well-formed 발행이지만, 4C-2가 배달 오케스트레이션
use case를 지을 때 「어느 코드든 outbox에 쓸 수 있다」는 사실을 알고 시작해야 한다 ·
NOTI-05의 등록 측 dedup은 4C-1이 지지 않는다(입력만 준비, 실 구현·검증은 4C-2).

**4C-1 종결 2026-09-09(사용자 승인)** — verifier r2 `ready-for-review`(산출물층
blocker/high 0) 위에서 승인(evidence `reports/evidence/m4/4c1/checklist.md` 「사용자
승인」 절, 최종 산출물 커밋 `c54e0249814dccd5d6dcdd9f4826390ae615fa74`). **재작업 1/5.**
공유 파일(`gate-tests.properties`·`data-dictionary.md`·`capability-map.md`)을 4B-1과
함께 만졌고, **줄 단위 rollback이 서로를 지우지 않음을 이 종결 문단 작성 시점에 임시
clone에서 직접 재실측**했다 — 4C-1 몫만 걷으면(4B-1은 그대로) conformance **86**·
`decision:test` **62**·`workflow:test` **39**(event 테스트 열여덟 사라지고 4A의
strategy 서른아홉만 남음)로, 4B-1 몫만 걷으면(이전 라운드에서 이미 반복 실측) conformance
**74**·`decision:test` **38**(1D만)·`workflow:test`는 **57**(4B-1은 `workflow`를 만지지
않아 무영향)로 각각 정확히 복귀한다 — 두 slice의 되돌림이 서로의 몫을 지우지 않는다.

**다음 slice는 4B-2**(조합 use case, 계약은 팀장이 별도 작성). 4C-2(Flyway·persistence
어댑터·DB↔outbox 원자성)는 `main` 병합 뒤 별도 slice.

### Slice 4D — ML gateway

- M2 generated coroutine gRPC client
- deadline, cancellation, circuit breaker, bounded retry
- domain input → contract DTO mapping
- ML response → candidate mapping
- release/checksum/schema provenance 보존
- ML unavailable을 `review/unavailable`로 처리하는 fail-safe 정책

### Slice 4E — notification adapter contract

- delivery request와 rendered content 분리
- dry-run/fake sender
- masking과 owner isolation
- 동일 idempotency key의 단일 delivery effect

## 완료 조건

- 상태 전이 property test와 invalid transition test 통과
- use case test가 DB/network 없이 fake port로 실행
- duplicate/redelivery/out-of-order event에서 상태가 수렴
- ML timeout 시 thread/connection이 고갈되지 않고 업무 결과가 fail-safe
- dry-run에서 실제 Telegram/email 호출 0
- trace/correlation id가 수집→판정→ML→알림 요청까지 유지
- 한 application 함수에 수집·DB·ML·알림 구현이 함께 들어가지 않음

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- transaction/outbox 사이에 유실 창이 있는지
- invalid state/event가 조용히 허용되는지
- retry와 idempotency가 함께 설계됐는지
- ML 장애가 위험한 추천으로 fail-open하는지
- Telegram/framework 타입이 domain을 오염시키는지
- 테스트가 실제 side-effect adapter를 차단하는지

## 범위 밖

- 실제 broker/Telegram/email
- Python ML 계산 구현
- public API/UI
