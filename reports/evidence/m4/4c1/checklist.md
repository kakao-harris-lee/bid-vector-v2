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
| 저장소 복원 경로(`restore`) → 경계로 처리 | `EventEnvelope.companion.restore(...)`는 `public`, `newEnvelope`에 위임하되 이름·KDoc이 「persistence 어댑터 전용, 신규 발행과 같은 호출부에서 섞지 않는다」를 못박음 | `EventEnvelopeTest`의 `restore` test가 형태만 확인(4C-2가 실 어댑터에서 이 함수를 쓴다 — 이 slice는 배선하지 않는다, out_of_scope) |
| `OutboxPort.mark*` 인자가 `OutboxEntryState`면 → 닫는다 | `markDelivered`/`markFailed`/`markIsolated`는 `OutboxEntryState`가 아니라 `OutboxTransition`의 대응 하위 타입(`ToDelivered`/`ToFailed`/`ToIsolated`, 각 `internal constructor`)만 받는다(`OutboxPort.kt`·`OutboxEntryState.kt`) | 임시 clone에서 `OutboxTransition.ToDelivered(...)` 직접 생성자 호출 → `Cannot access '<init>': it is internal`(commands.md) |
| `OutboxEntryState` sealed 값 읽기 → 연다 | `data object`/`when` 소비 자유(`OutboxTransitionTableTest`) | 전이표 test가 값을 직접 비교·소비 |
| `InboxPort` 위조해도 자기 소비만 손해 → 연다 | `InboxPort`는 `public interface`, `decideInbox`도 `public` 순수 함수 | `InboxDedupPropertyTest`가 fake 없이 순수 함수만으로 property 실행 |
| `OutboxEventSink`(4A `EventSink` 구현) 발행은 요구되는 것 → 연다 | `class OutboxEventSink : EventSink`, `public`(`OutboxEventSink.kt`) | `OutboxEventSinkTest` |
| 전이 함수·봉투 factory → `internal`(4A 결론 계승) | `transitionOutbox`·`newEnvelope`·`forStrategyUpdated` 전부 `internal fun`(4A `beginSession`/`apply` 관례) | 임시 clone에서 세 함수 전부 직접 호출 시도 → `Cannot access '...': it is internal in file`(commands.md, 세 형태 전부 거부 확인) |

**4A와의 차이**: 4A는 이 표가 없어 「위조」(H-1/H-2)만 먼저 닫고 「획득」(H-3)을 두 라운드
뒤에야 발견했다. 4C-1은 착수 전 설계 검토에서 표를 먼저 만들어 커널 함수(`transitionOutbox`·
`newEnvelope`·`forStrategyUpdated`)를 **처음부터 `internal`**로 시작했다 — 사후 라운드로
발견되지 않았다.

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

### 3. scope.md 밖 필수 파급 — `app/src/test/.../StrategyEditExecutors.kt`

**scope.md `in_scope`에 이 경로가 없다.** `EventSink.publish` 시그니처 확장(승인된 좁은
예외)의 기계적 결과로 `app` 모듈의 corpus 실행자 fake(`RecordingEventSink`)가 컴파일되지
않아(`:app:gateExecutionGate` 최초 실행 exit 1) 시그니처만 맞췄다 — corpus 산출 로직은
무변경(commands.md). **팀장에게 별도 보고 — scope.md 갱신 여부는 팀장 판단**(4A의
좁은 예외가 이미 「호출부 한 줄」을 명시적으로 승인한 전례가 있으나, 이 파일은 그 목록
밖이라 스스로 범위를 넓히지 않고 사실만 등재한다).

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

`data-dictionary.md` — §2.2.5에 +18줄 삽입, `data-dictionary.md:<줄번호>` 형태로 그
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
3. **`transitionOutbox`의 유일한 정당한 호출부(배달 오케스트레이션 use case)가 아직
   없다** — 4A의 `EditStrategyWorkflow`에 대응하는 outbox 쪽 use case는 4C-2 소관이다.
   지금은 `workflow` 테스트 코드만 이 함수를 직접 부른다(같은 모듈이라 `internal`
   경계 안).
4. **`EventIdFactory`의 실 기제(UUIDv7 등)는 미정** — port만 있다(D-M4-4 (a), 4C-2 어댑터
   가 고른다).
5. **`OutboxEntryId`·`AggregateId` 등은 문자열 값 타입** — 형식(UUID vs 다른 스킴) 강제는
   이 slice가 걸지 않는다(생성 기제가 아직 없어 형식을 안다고 주장할 근거가 없다).
6. **`app/src/test/.../StrategyEditExecutors.kt`가 scope.md `in_scope` 밖에서 편집됐다**
   (§3 참고) — scope.md 갱신 필요 여부는 팀장 판단 대기.
7. **재시도 계층 부재는 설계 의도**이지 미완이 아니다(계약 ⑤) — `Isolated`에서 나가는
   전이가 타입에 없다. 4C-2가 새 상태·전이를 추가하려면 이 sealed 어휘 자체를 갱신해야
   한다(sealed라 컴파일 층이 소진 `when`을 강제 — 회귀 방지).

## rollback

정본은 `rollback.md`.
