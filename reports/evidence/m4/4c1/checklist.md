# checklist.md — M4/4C-1 리뷰 준비도

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      결과 없음(commands.md 「clean-tree 게이트」, 양성 대조 포함).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·
      detekt·cpd·sizeGate·moduleDependencyGate·gateExecutionGate·qualityBaseline 포함).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — 이 slice는 fixture 신설이 없다
      (golden-manifest.json N/A). 정책 데이터 신설도 없다(outbox timeout/재시도 예산은
      4C-1이 만들지 않는다, 계약 ⑤).
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] secret 스캔 통과 — 실 매치 0(자기참조 1건은 이 문장 자신이 패턴에 걸린 것,
      commands.md 「secret 스캔」 절 참고).

## 이 slice 고유 확인

### 1. 값 획득 축 표(설계 검토 (2))가 코드에서 어떻게 섰는가 — 행별 대응

| 설계 검토 (2) 행 | 코드 대응 | 실측 근거 |
| --- | --- | --- |
| `EventEnvelope` 생성자 위조 → 닫는다 | `EventEnvelope`가 `@ConsistentCopyVisibility` + `internal constructor`(`EventEnvelope.kt`) | 임시 clone에서 `EventEnvelope(...)` 직접 호출 → `Cannot access '<init>': it is internal`(commands.md 「값 획득 축 실측」) |
| `EventEnvelope` 읽기 프로퍼티 → 연다 | 9개 필드 전부 `val`, `public`(생성자만 `internal`) | `EventEnvelopeTest`가 정상 생성 경로로 필드를 읽어 단언 — 읽기 자체는 막지 않음을 실측 |
| 저장소 복원 경로(`restore`) → **닫는다**(수정 라운드 1, verifier H-1) | ~~`public`~~ **`internal`로 정정.** 원래 판단(「경계로 처리」)이 실은 틀렸다 — persistence 어댑터에만 참인 경계 논증을 `public`(아무 모듈)에 적용했다. `OutboxPort.claim`이 이제 `EventEnvelope`가 아니라 `ClaimedOutboxRow`(원시 행)만 돌려주고, `EventEnvelope.restore`·`OutboxEntry.restore` 모두 `internal` — 어댑터는 `EventEnvelope`를 **만들지 않는다**(정정, L-5 — `register`는 어댑터 자신의 메서드라 `EventEnvelope`를 인자로 **받는다**, 「전혀 다루지 않는다」는 과잉 서술이었다) | 임시 clone에서 `EventEnvelope.restore(...)` 직접 호출 → `Cannot access 'fun <P> restore(...)': it is internal in file`(H-1 재작업 실측, commands.md) |
| `OutboxEntry` 생성자 → **닫는다**(수정 라운드 1, verifier L-3 — H-1과 같은 뿌리) | `OutboxEntry`도 `@ConsistentCopyVisibility` + `internal constructor`, 유일한 생성 경로는 `OutboxEntry.restore(row)`(`internal`) | 임시 clone에서 `OutboxEntry(id, envelope, state)` 직접 생성자 호출 → `Cannot access '<init>': it is internal`(commands.md) |
| `ClaimedOutboxRow`(신설, H-1 시정) 생성자 → **연다** | `data class` 공개 생성자 — 어댑터(4C-2)가 DB 컬럼을 그대로 옮겨 담는 DTO. 위조해도 소비할 방법이 없다(`EventEnvelope`로 가는 유일한 경로가 `internal`) | `OutboxEntryTest`가 정상 생성 경로로 값을 채워 `OutboxEntry.restore`에 넘긴다 |
| `OutboxPort.mark*` 인자가 `OutboxEntryState`면 → 닫는다 | `markDelivered`/`markFailed`/`markIsolated`는 `OutboxEntryState`가 아니라 `OutboxTransition`의 대응 하위 타입(`ToDelivered`/`ToFailed`/`ToIsolated`, 각 `internal constructor`)만 받는다(`OutboxPort.kt`·`OutboxEntryState.kt`) | 임시 clone에서 `OutboxTransition.ToDelivered(...)` 직접 생성자 호출 → `Cannot access '<init>': it is internal`(commands.md) |
| `OutboxEntryState` sealed 값 읽기 → 연다 | `data object`/`when` 소비 자유(`OutboxTransitionTableTest`) | 전이표 test가 값을 직접 비교·소비 |
| `InboxPort` 위조해도 자기 소비만 손해 → 연다 | `InboxPort`는 `public interface`, `decideInbox`도 `public` 순수 함수 | `InboxDedupPropertyTest`가 fake 없이 순수 함수만으로 property 실행 |
| `OutboxEventSink`(4A `EventSink` 구현) 발행은 요구되는 것 → 연다 | `class OutboxEventSink : EventSink`, `public`(`OutboxEventSink.kt`) | `OutboxEventSinkTest` |
| 전이 함수·봉투 factory → `internal`(4A 결론 계승) | `transitionOutbox`·`newEnvelope`·`forStrategyUpdated` 전부 `internal fun`(4A `beginSession`/`apply` 관례) | 임시 clone에서 세 함수 전부 직접 호출 시도 → `Cannot access '...': it is internal in file`(commands.md, 세 형태 전부 거부 확인) |

**4A와의 차이**: 4A는 이 표가 없어 「위조」(H-1/H-2)만 먼저 닫고 「획득」(H-3)을 두 라운드
뒤에야 발견했다. 4C-1은 착수 전 설계 검토에서 표를 먼저 만들어 커널 함수(`transitionOutbox`·
`newEnvelope`·`forStrategyUpdated`)를 **처음부터 `internal`**로 시작했다.

**그러나 표의 「닫는다」가 아니라 「경계로 처리」로 분류한 행 하나(`restore`)가 수정
라운드 1에서 결함으로 드러났다**(verifier H-1) — 최초 값 획득 축 실측(5형태)이 「닫는다」
행만 재고 「경계로 처리」 행을 재지 않아 아무도 그 행의 조건(「신규 발행과 절대 섞지
않는다」)이 실제로 지켜지는지 확인하지 않았다. 실물은 조건을 어겼다 — `restore`가
`newEnvelope`(신규 발행 경로)를 그대로 위임하는 **공개 래퍼**였다. 이번 라운드에서
`restore`를 `internal`로 내리고 `OutboxPort.claim`의 반환형을 `EventEnvelope`가 아니라
`ClaimedOutboxRow`(원시 행)로 바꿔 어댑터가 `EventEnvelope`를 전혀 다루지 않게
구조를 바꿨다(단순히 가시성만 내리면 4C-2에서 어댑터가 그 함수를 다시 열어야 해 같은
구멍이 되돌아왔다) — **다음 slice의 값 획득 축 실측 목록에는 「경계로 처리」로 분류한
행도 반드시 포함한다**(verifier의 권고, 이 slice가 채택).

### 2. 4A 회귀 없음 실측 (좁은 예외 — `EventSink.publish(event, actor)`)

- `EditStrategyWorkflowTest`의 기존 12개 test(4A 산출물, 무변경) + 신규 회귀 단언
  (`events.publishedActors shouldBe listOf(Actor.Operator(OPERATOR))`) 포함 전건 초록
  (commands.md `:workflow:test`).
- `EditStrategyWorkflow.kt`의 변경은 호출부 한 줄(`events.publish(outcome.event,
  outcome.session.actor)`)뿐 — 전이 커널·상태 기계·통로 타입·정책은 무변경(diff 확인,
  `git diff -- workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt`
  가 그 한 줄 hunk뿐임을 리뷰 시 대조 가능).
- `:app:test --tests '*Conformance*'` 74 tests(기존과 동일 개수) — `strategy-edit-*` corpus
  다섯은 actor를 투영하지 않으므로 산출 값 무변경(golden-manifest.json N/A 사유 참고).

### 3. scope.md 밖 필수 파급 — `app/src/test/.../StrategyEditExecutors.kt`(**정정 — 이미 in_scope 편입**)

`EventSink.publish` 시그니처 확장(승인된 좁은 예외)의 기계적 결과로 `app` 모듈의 corpus
실행자 fake(`RecordingEventSink`)가 컴파일되지 않아(`:app:gateExecutionGate` 최초 실행
exit 1) 시그니처만 맞췄다 — corpus 산출 로직은 무변경(commands.md). **구현 당시엔
scope.md `in_scope`에 이 경로가 없어 팀장에게 별도 보고했다.** 팀장이 그 신고를 옳다고
확인하고 `3752b49`(「계약 갱신 — EventSink 확장의 기계적 파급 1파일 in_scope 추가」)로
scope.md `in_scope`에 이 파일을 편입했다 — **이 항목은 verifier r1 이 닫혔다고 판정한
넷 중 하나**라 이 checklist 문면만 그 사실을 반영해 정정한다(재작업 대상 아님).

### 4. `OPEN-OPS-10` 종결 근거와 잔여

`data-dictionary.md` §2.2.5를 D-M4-5 (a) 값으로 채우고 `capability-map.md` §14.2의
`OPEN-OPS-10` 행을 `~~OPEN-OPS-10~~`으로 닫았다(운영자 결정 2026-09-09, 어휘·전이표
축 한정). **잔여 셋**(닫지 않은 것, capability-map.md 새 행 본문에 그대로 등재):
① backlog 관측(깊이 측정)은 `OPEN-OPS-03`·`OPEN-OPS-04`가 이미 별도 소유 ② claim
가시성 timeout·재가시화 시간은 실 저장(4C-2)이 있어야 정해진다
(`OPEN-4C1-TX-CONTRACT-UNVERIFIED`, scope.md) ③ `OPS-06`(§10) 자신의 재정의·폐기
판정 행은 이 편집이 건드리지 않았다(§14.2 한정 편집, scope.md in_scope 준수) — §10은
별도 후속이 판정한다.

### 5. 역방향 파급 grep

`data-dictionary.md` — §2.2.5에 **+19줄·-4줄(순증 +15줄)** 삽입(재산출: `git diff --numstat
4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..3752b49 -- docs/discovery/data-dictionary.md` →
`19 4` — verifier B-6 정정, 이전 판의 「+18줄」은 hunk 신규 줄 수만 세고 삭제분을 빼지
않은 오산이었다), `data-dictionary.md:<줄번호>` 형태로 그
아래를 가리키는 참조 4건 발견(960·980·1551·1554) — **전부 4C-1 in_scope 밖의 닫힌 slice
evidence**(`m1/1b/scope.md`·`m1/1e/scope.md`·`m1/1c/checklist.md`·`m0/0c/commands.md`)이고,
그중 `m1/1c/checklist.md:148` 자신이 이미 그 좌표들이 낡았음을 선언하고 있다 — 4C-1이
새로 만드는 낡음이 아니라 **기존에 알려진 제한을 인수**한다(commands.md 상세).

`capability-map.md` — 1줄→1줄 교체(줄 수 불변, `git diff --stat` `2 +-`)라 아래 좌표가
밀리지 않는다. 참조 1건(`test-discovery-guard/commands.md:100`)은 다른 줄(3457)을
가리켜 무관.

## 알려진 제한 (이 slice 종료 시점)

1. **DB↔outbox 원자성·crash-after-commit·claim 경합**은 실 저장이 있어야 잰다 — 4C-2
   (`OPEN-4C1-TX-CONTRACT-UNVERIFIED`, scope.md 「위협 모델」).
2. **`OutboxPort`·`InboxPort` 구현은 test fake만** — 4C-2가 persistence 어댑터를 짓는다.
3. **`transitionOutbox`·`OutboxEntry.restore` 둘 다 유일한 정당한 호출부(배달
   오케스트레이션 use case)가 아직 없다** — 4A의 `EditStrategyWorkflow`에 대응하는
   outbox 쪽 use case는 4C-2 소관이다. 지금은 `workflow` 테스트 코드만 이 함수들을
   직접 부른다(같은 모듈이라 `internal` 경계 안). H-1 시정으로 `OutboxEntry.restore`도
   같은 처지가 됐다 — 4C-2가 그 use case를 지을 때 이 함수를 호출하고, 어댑터는
   여전히 그 함수를 보지 않는다(어댑터는 `ClaimedOutboxRow`만 다룬다).
4. **`EventIdFactory`의 실 기제(UUIDv7 등)는 미정** — port만 있다(D-M4-4 (a), 4C-2 어댑터
   가 고른다).
5. **`OutboxEntryId`·`AggregateId` 등은 문자열 값 타입** — 형식(UUID vs 다른 스킴) 강제는
   이 slice가 걸지 않는다(생성 기제가 아직 없어 형식을 안다고 주장할 근거가 없다).
6. ~~`app/src/test/.../StrategyEditExecutors.kt`가 scope.md `in_scope` 밖에서 편집됐다`~~
   — **닫힘**. 팀장이 `3752b49`로 scope.md `in_scope`에 편입해 계약을 갱신했다(§3 참고).
7. **재시도 계층 부재는 설계 의도**이지 미완이 아니다(계약 ⑤) — `Isolated`에서 나가는
   전이가 타입에 없다. 4C-2가 새 상태·전이를 추가하려면 이 sealed 어휘 자체를 갱신해야
   한다(sealed라 컴파일 층이 소진 `when`을 강제 — 회귀 방지).
8. **NOTI-05 의 등록 측 dedup 은 이 slice가 지지 않는다**(verifier L-2, 신설) — 「같은
   이벤트를 두 번 적재해도 outbox 행은 1개」는 저장소(4C-2 persistence 어댑터)의 성질이다.
   4C-1이 세우는 전제(같은 revision → 같은 `idempotencyKey`, `OutboxEventSinkTest`)는
   그 등록 측 dedup 이 실 구현되기 위한 **입력**만 준비한다 — 실제 「행 1개로 수렴」은
   4C-2가 구현·검증한다.
9. **`milestone-4.md`가 in_scope·rollback 목록에 있으나 이 slice의 어느 range 에서도
   실제로 편집되지 않았다**(verifier B-5) — 4A는 종결 문단을 남겼으나 4C-1은 아직 구현
   기록을 milestone 문서에 남기지 않았다. slice 종결(사용자 승인) 시점에 팀장이 closure
   문단을 붙일 자리로 남겨 둔다 — 지금 붙이면 verifier 재검증이 끝나기 전에 「종결」로
   읽힐 수 있어 자리표시자를 만들지 않는다.
10. **`OutboxEventSink` 는 public 이고, 자기 `OutboxPort`·`EventIdFactory`·`Clock` 을
    조립해 밖에서 새로 지을 수 있다(verifier r2 신규 표적)** — 임시 clone에서 `app`
    모듈(다른 모듈)에 self-supplied `OutboxPort`·`EventIdFactory`·`Clock` fake 를 심고
    `OutboxEventSink(fake, fake, fake)`를 직접 생성했더니 **컴파일 성공**
    (`:app:compileTestKotlin` exit 0, probe 삭제 후 `git status --porcelain` 재확인 —
    clean). **결함이 아니다** — sink 는 불변식을 강제한다(`actor` non-null·
    `idempotencyKey` 는 revision 파생·`correlationId == eventId`)라 이 경로로 만들 수
    있는 것은 **위조가 아니라 well-formed 이벤트 발행**이고, 설계 검토가 이미 「연다 —
    발행은 요구되는 것」으로 판정한 자리다(scope.md 위협 모델 (2b) — sink 자체가 이
    slice 의 유일한 [EventSink] 실구현이자 진입점). 다만 **어느 코드든 outbox 에 쓸 수
    있다는 사실**은 4C-2 가 배달 오케스트레이션 use case 를 지을 때 알고 시작해야 하는
    잔여다 — 그 use case 가 유일한 정당한 호출부가 되도록 강제하는 장치(예: DI 배선을
    단일 지점으로)는 이 slice 범위 밖이고 4C-2 가 판단한다.

## rollback

정본은 `rollback.md`.
