# M6/6F-10 — outbox relay/dispatcher · CLAIMED 고아 처분 · 평가→outbox 커밋 경로 · payload 버전 (2026-10-06, 착수 계약 초안)

6D-1 이 test 소스셋에 **모양으로** 보여 준 relay 를 production 으로 낸다. 6D 분할(D-6D-1 C-2 (a))이 이 slice 에 넘긴 production 틈은
넷 — ⓐ outbox **relay/dispatcher**(claim → inbox 판정 → `DispatchNotification` → 종단 전이) ⓑ **CLAIMED 고아 처분**(워커가 죽어 `CLAIMED`
에 남은 행) ⓒ **평가→outbox 커밋 경로**(`OPEN-6A3-EVALUATION-COMMIT` — dry-run 밖에 없는 평가 진입점에 `OutboxNotificationRequestPort` 를
꽂는 자리, D-6F7-11 `Failed` 처분) ⓓ **payload 에 정책 버전·전략 revision**(6D-1 C-4 → 6D-2 재현 등식의 입력). 발송 채널은 그대로
`OPEN-STR-12` 다 — 이 slice 에 production sender 는 없다.

- base: 이 브랜치가 분기해 나온 **현재** `main` — `git merge-base HEAD origin/main`(고정 SHA 아님, D-6F5-30). 착수 실측값 `b137c670`
  (PR #62 6D-1 머지 커밋). 라운드마다 재산출.
- 선행: 4C-1·4C-2(outbox 전이표·V6·`JdbcOutboxPort`) · 6F-7(`OutboxNotificationRequestPort`) · 6A-3+6F-3(평가 조립·dry-run) · 6D-1(test relay
  모양·협력자 출처 단언) — 전부 `main`.
- worktree `bid-vector-v2-m6-6f10`, 브랜치 `m6-6f10/2026-10-06`. evidence `reports/evidence/m6/6f10/`, 결정 ID `D-6F10-n`, 운영자 결정 `A-n`.
- 조사 노트 `_workspace/m6-6f10/00_scout.md`(읽기 전용 scout, gitignored — 핵심 주장은 팀장이 저장소에서 재대조했다, 아래 「착수 조사가
  확정한 것」이 정본).
- **설계 검토 필수**(6D 표 문면) — 운영자 결정 A-1~A-6 뒤, 구현 전, 세션 모델이 직접 `_workspace/m6-6f10/01_design-review.md` 에
  (0) 경계 → (1) 구조로 닫는가 → (2) 우회 ≥5 → (2b) 값 획득 축 → (3) 과잉·미달.

## 수취하는 OPEN·인계

| 항목 | 출처 | 이 slice 의 처분 |
|---|---|---|
| `OPEN-6A3-EVALUATION-COMMIT` | 6A-3+6F-3 결정 2 | **닫는다** — 커밋 경로 진입점(A-4) + 6F-7 인계 둘(D-6F7-11 원자성 · 하위 소비자 `Failed` 로깅) |
| `OPEN-4C2-MARK-UNEXERCISED` | 4C-2 | **닫는다** — `OutboxPort.markDelivered`·`markFailed`·`markIsolated` 가 production relay 에서 port 수준으로 호출된다. **통로를 열어 닫지 않는다**(4C-2 명시 결정: `OutboxTransition.To*` 생성자·`transitionOutbox` 의 `workflow` `internal` 유지 → relay use case 는 `workflow` 에 산다) |
| `OPEN-6D1-CLAIM-CONCURRENCY-TEST` | 6D-1 | **닫는다** — `OutboxClaimConcurrencyTest` 를 6D-1 모양(쥔 동안 claim → 롤백 → 재claim, 래치 반환값 단언)으로 고쳐 production `SKIP LOCKED` 제거 변이 RED 실측 |
| 6D-1 checklist 「relay 가 inbox 를 dispatch 보다 먼저 기록한다 → 발송 실패 시 CLAIMED 좌초·키 소진」 | 6D-1 (PR #62 U) | **닫는다** — D-6F10-3 순서 |
| 6D-1 checklist 「재현 등식에 정책 버전·전략 revision 이 없다」 | 6D-1 C-4 | ⓓ 가 payload 를 넓힌다; 등식에 넣는 것은 **6D-2** |
| 6D-1 checklist 「DB conflict 막힘 판정이 시한 기반」 | 6D-1 | 같은 축을 production 으로 옮기는 자리 — 구조 단언(쥔 동안 못 집음)을 주 단언으로 |
| 6D 표 6D-2 행 「claim 중 크래시 → 재기동 → reclaim → **정확히 한 번 발송**」 | 6D scope 제안 표 | **문면 충돌** — ADR 0005 at-most-once 와 맞지 않는다. A-1 결정으로 정정(아래) |
| `OPEN-STR-12` | M4 | 변경 없음 — 실 발송·렌더링·라우팅은 그 뒤. 이 slice 의 sender 경계는 A-5 |
| ADR 0005 `OPEN-OPS-10` ③ 「소비자 사망 후 작업 재가시화 시간」 | ADR 0005 | A-1 (a) 면 **재가시화 없음**(격리)으로 답이 정해진다 — ADR 에 등재 |

## 착수 조사가 확정한 것 (저장소 실측, 2026-10-06)

- **전이표에 `CLAIMED → PENDING` 간선이 없다.** `transitionOutbox` 가 받는 쌍은 Pending+Claim · Claimed+Deliver · Claimed+Fail · Claimed+Isolate 넷뿐이고
  나머지는 `Rejected`. `OutboxEntryState.Isolated` KDoc 문면: 「`Claimed`에서 워커가 죽어 격리됐다(D-M4-5 (a), at-most-once·`OPEN-NOTI-02`)」.
  ADR 0005 §1.2·D-3·재시도 표: **채널 배달은 at-most-once, `running` 에서 워커가 죽은 행은 재실행하지 않고 격리한다.** 그러므로 6D 표의
  「reclaim(stale sweep)」은 **PENDING 복귀가 아니라 ISOLATED 전이**로 읽어야 승인 문서와 맞다.
- **V6 에 `claimed_at`·lease·시도 횟수·마지막 오류 열이 없다.** `idempotency_key` 에 UNIQUE 없음(D-6F7-3·6 — 같은 공고가 run 마다 행 하나씩,
  「두 번 아니다」를 주장하지 않는다). GRANT 는 outbox SELECT/INSERT/UPDATE · inbox SELECT/INSERT, **DELETE 없음**. 종단 셋(DELIVERED·FAILED·
  ISOLATED)은 단방향 — 잘못 격리한 행은 되돌릴 API 가 없다(이 slice 의 되돌리기 어려운 축).
- **`JdbcOutboxPort` 는 트랜잭션을 열지 않는다**(`ConnectionSource` 만). `claim` 은 같은 연결에서 SELECT … FOR UPDATE SKIP LOCKED + MARK_CLAIMED;
  **커밋은 호출자 몫**이라 at-most-once 의 성립 여부는 relay 의 트랜잭션 모양이 정한다. `mark*` 셋은 **`executeUpdate()` 계수를 버린다** — `WHERE
  state = 'CLAIMED'` 거부가 조용한 no-op 다(6D-1 test relay 는 `check(== 1)` 로 막았다).
- **평가 쪽**: `evaluate()` 의 `reach()` 가 `BidNow` 에만 `notifications.request(...)` 를 부르고 **반환 `Outcome` 을 버린다.**
  `OutboxNotificationRequestPort` 는 `SQLException → Failed`(D-6F7-11 이 지적한 갈림). 오늘 평가에는 **도메인 write 가 없다**(판정 기록 표 없음,
  D-6F7-2) — outbox 행이 판정의 **유일한 영속 흔적**이다. `evaluate()` 는 `suspend` 이고 `TransactionBoundary.inTransaction` 은 ThreadLocal·소유
  스레드 검사 → **run 전체를 한 트랜잭션으로 감는 설계는 서지 않는다**(요청 하나 = 트랜잭션 하나, 선례 `OwnTransactionConnectionSource`).
- **진입점**: 평가 진입점은 HTTP dry-run 하나(`EvaluationDryRunFactory` 가 `RecordingNotificationRequestPort` 고정). `config/quality/
  architecture-policy.properties` 의 `app.notification.allowed-impls`(== {`RecordingNotificationRequestPort`})·`app.forbidden.outbox-types`
  (`OutboxPort`·`JdbcOutboxPort`·`OutboxNotificationRequestPort`·`OutboxEventSink`)가 app 의 outbox 참조를 **금지**한다 — D-6A3-3 「dry-run 은 타입으로
  닫는다」의 게이트. 선례: `StrategyEditTransaction` 이 **adapters 안에서** `OutboxEventSink(JdbcOutboxPort(transactions))` 를 조립하고 app 은
  `TransactionBoundary` 빈만 든다.
- **발송 자리**: `DispatchNotification(RouteDirectory, ContentRenderer, NotificationSender, NotificationDeliveryPolicyData, RuntimeEnvironment)`,
  결과 `Suppressed(plan) | Attempted(Delivered | Rejected | Unknown)`. `Unknown` 은 재시도 없음(D-4E-2), 「격리 판단은 다른 slice 소유」. production
  구현은 **넷 모두 없다**(`OPEN-STR-12`), `RuntimeEnvironment` 미배선.
- **payload**: `NotificationRequestedPayload` 에 정책 버전·전략 revision 없음. `strategy.revision` 은 `evaluate()` 에 있으나 `reach()` 로 안 넘어가고,
  사다리 `PolicyVersion` 은 `reach()` 안 리터럴(`"m4-4b2-legacy-behavior-2026-09-09"`). `OutboxPayloadCodec.decode` 는 **필드 수 고정 `check`** →
  필드 추가는 구행 해독을 깨뜨린다 → **새 `payload_type` 토큰**이 필요하고 구 토큰 디코더는 유지(`OPEN-6F7-REASON-CODE-STABILITY`: 구행 재작성
  없음). `StrategyUpdated` 가 `PolicyVersion` 을 인코딩하는 선례.
- **실행 모델**: `@Scheduled`·`EnableScheduling` 0. db-scheduler 16.12.0 은 app classpath(ADR 0005 D-5 채택)에 있으나 **미사용**. 일회 러너 선례:
  `ApplicationRunner` + `@ConditionalOnProperty(prefix="bidvector.<x>", name=["mode"], havingValue="once")` + run-state **파일 잠금**(`RunStateLock`,
  프로세스 범위) + `CollectionTerminationWiring`(종료 코드). compose 는 `*.mode` 를 설정하지 않는다(D-6A2a-5 opt-in).
- **lease**: ADR 0005 D-10 — lease 는 `workflow` 가 소유하는 port, `adapters` 가 구현, 홀더 사망 시 **즉시 해제**(TTL 아님, 세션 advisory lock
  성질). 오늘 그 port 는 없다(수집은 파일 잠금으로 같은 성질을 얻었다).
- `OutboxClaimConcurrencyTest` 구멍(6D-1 verifier 실측): 두 `await(5s)` 반환값 미단언 → `SKIP LOCKED` 제거에서 B 가 A 의 행 잠금에 막혀 5s 뒤
  빈손으로 돌아와도 초록.
- 게이트: `gate-tests.properties` 는 모듈별 **모집단 == 등재**(D-6G2g-10) → 새 test 클래스 전부 등재. `EventAdapterDependencyTest` 허용 루트에
  `workflow.evaluation` 없음(relay 조립이 adapters.event 에서 평가 payload 를 만지면 닿는 자리 — 설계 검토 (1)).

## 운영자 결정 대기 — A-1 ~ A-6 (선택지 + 추천)

- **A-1 CLAIMED 고아 처분** — (a) **ISOLATED 로 격리**(ADR 0005 at-most-once·전이표 그대로; 6D 표 6D-2 행 문면을 「claim 중 크래시 → 재기동 → 고아
  **격리** → 발송 0 또는 1, **중복 0**(놓침 감수)」로 정정) · (b) PENDING 복귀(재발송) — ADR 0005 D-3 개정 + 전이 간선 신설 + D-M4-5 (a) 어휘
  변경, 중복 발송 가능성 수용. **추천 (a)**.
- **A-2 고아 판정 기제** — (a) **새 열 없이**: relay 는 **lease**(ADR D-10 port, 구현은 PostgreSQL 세션 advisory lock 또는 파일 잠금 선례 — 설계 검토가
  고른다) 아래에서만 claim 을 커밋하고, lease 를 **새로** 잡은 relay 가 보는 `CLAIMED` 전부 = 죽은 홀더의 것 → 격리. 마이그레이션 0. 관측용
  `claimed_at` 은 `OPEN-6F10-CLAIM-OBSERVABILITY` 로 · (b) **V18** `claimed_at`(+`claimed_by`) + TTL sweep — 마이그레이션(migration-reviewer, 되돌리기
  어려운 경로 판단 축) + `OPEN-OPS-10` 재가시화 시간 값 결정 필요. **추천 (a)**.
- **A-3 relay 실행 모델** — (a) **`bidvector.relay.mode=once` 일회 러너**(수집 선례: opt-in · 종료 코드 · cron 이 돌림) · (b) db-scheduler 상시 작업
  (ADR D-5 채택 도구 첫 사용 — 도구 기본 재시도를 끄는 설정 검증이 같이 든다) · (c) 앱 내 상주 루프 직접 구현. **추천 (a)**; db-scheduler 도입은
  `OPEN-6F10-SCHEDULER` 로 보이게만.
- **A-4 평가 커밋 진입점** — (a) **`bidvector.evaluation.mode=once` 일회 러너**(전략 전체 평가 → outbox 커밋; HTTP 는 dry-run 그대로) · (b) `POST
  /api/evaluation-runs` 커밋 endpoint(인증 쓰기 경로 — Codex 판단 축이 하나 늘어난다) · (c) 둘 다. **추천 (a)**.
- **A-5 sender 경계(`OPEN-STR-12` 전)** — (a) **relay use case·조립·`mode=relay` 러너까지 production** 으로 내되 발송은 **환경 억제로 닫는다**: 배선된
  `RuntimeEnvironment`·정책이 발송 불가 환경이면 relay 는 **claim 자체를 하지 않는다**(행은 PENDING 보존 — ADR D-4 「억제는 기록 억제가 아니다」);
  `NotificationSender` 자리는 호출되면 던지는 자리지킴(`UnavailableMlAnalysis` 선례) · (b) use case + 조립까지만, 러너·sender 배선 없음(6D-2 는 test
  에서 production relay + fake sender) · (c) 자리지킴 sender 가 늘 `Unknown` → 행을 격리로 태움 — **불채택 권고**. **추천 (a)**.
- **A-6 Codex 심판** — (a) **안 탐**: verifier(opus) + code-reviewer(sonnet) 병렬(+ A-2 (b) 면 migration-reviewer). 근거: 마이그레이션 0(A-2 a) · 인증
  변경 0(A-4 a) · 데이터 파기 0(격리는 단방향 전이지만 행·payload 보존) · (b) 탐(outbox 종단 전이가 영구 좌초를 만들 수 있다는 데이터 경로 근거,
  비용 승인). **추천 (a)** — 단방향 전이의 위험은 설계 검토 (2) 우회 1·2 + 변이 실측으로 닫는다.

## 계약 고정 결정 (팀장 — 운영자 결정이 필요하지 않은 것)

**D-6F10-1 — 이름·범위.** slice ID `6F-10`, 산출물 넷 ⓐ~ⓓ(머리). 6D-2 로 넘기는 것: restart 수렴 E2E(production relay + fake sender) · redelivery
(같은 entry 두 번 claim → inbox 가 두 번째를 거부) · 재현 등식에 정책·전략 버전 포함.

**D-6F10-2 — relay 는 `workflow` 에 산다, `internal` 완화 0.** 종단 전이는 `OutboxPort.markDelivered`·`markFailed`·`markIsolated` 를 **port 로** 지난다
(사본 SQL 0). 6D-1 의 test relay(`PipelineAssembly.relay()` + raw `EventSql.MARK_DELIVERED`)는 production relay 로 **교체**(사본 제거). `mark*` 의 **갱신
계수 0 은 조용한 no-op 가 아니다** — 포트 계약(반환값 또는 예외)으로 올리고 「전이표 거부」와 「행이 이미 다른 상태」를 test 로 가른다.

**D-6F10-3 — 트랜잭션 모양은 at-most-once 의 귀결이다.** **T1**: claim(→ `CLAIMED`) **커밋** → 발송 → **T2**: 종단 전이 + inbox 기록(같은 트랜잭션).
inbox 는 **발송 뒤**, `Delivered` 일 때만 기록한다(6D-1 인계 「선기록 → 좌초·키 소진」을 닫는 순서). 결과 매핑(기본안, 설계 검토가 확정):
`Delivered → DELIVERED + inbox` · `Rejected → FAILED` · `Unknown → ISOLATED`(모호, 재시도 없음 D-4E-2 — 격리 판단 소유자가 이 slice 다) ·
`Suppressed → claim 하지 않는다`(A-5 a; 억제 환경에서는 relay 가 PENDING 을 건드리지 않는다) · **`SkipDuplicate`**(inbox 에 같은 키가 이미 있음 —
D-6F7-6 의 run 간 N 행)의 종단은 **설계 검토가 정한다**(후보: `DELIVERED`(그 키의 알림은 전달됐다) 대 `ISOLATED`; **어휘 확장은 하지 않는다** —
V6 CHECK 변경 = 마이그레이션 = 범위 밖). T1/T2 사이의 크래시가 남기는 `CLAIMED` 가 A-1 의 고아다.

**D-6F10-4 — 평가 커밋 경로.** `SQLException → Failed` 매핑은 유지하되 **호출부가 `Failed` 를 버리지 않는다** — run 결과에 실패 계수·공고 ID 를 싣고
러너 종료 코드는 비-0(수집 선례). 요청 하나 = 트랜잭션 하나(run 전체 원자성은 **주장하지 않는다** — `suspend`·ThreadLocal 실측). D-6F7-11 의
「도메인 write 만 커밋되고 outbox 행이 없다」 경우는 오늘 평가에 outbox 밖 write 가 없어 **성립하지 않음을 test 로 사실화**한다(판정 기록 표가
생기는 slice 가 다시 받는다 — `OPEN-6F10-EVALUATION-DOMAIN-WRITE` 로 보이게). dry-run endpoint 는 그대로 **outbox 전후 등식** 유지. D-6A3-3 의 집합
등식 게이트(app 이 참조하는 `NotificationRequestPort` 구현 == {`Recording…`})는 커밋 경로 구현을 더해 **명시 갱신**하고(술어 변경 → 표적 재검증),
**커밋 경로 구현이 dry-run 조립에서 참조 불가**임을 같은 층(바이트코드)에서 잠근다. `app.forbidden.outbox-types` 는 선례대로 **adapters 조립**으로
피하는 것이 기본(app 은 조립 결과만 든다); 키를 바꿔야 하면 바꾸는 이유를 (1) 에 적는다.

**D-6F10-5 — payload v2.** 새 `payload_type` 토큰(예 `NotificationRequested.v2`)으로 사다리 `PolicyVersion`(reach() 리터럴을 **값으로 나른다**)과
`StrategyRevision` 을 싣는다. 구 토큰 디코더 유지 + **구행 골든 해독 test**(6F-7 시점 행 문자열) + 왕복 test. `strategy.revision` 을 `reach()` 로
넘기는 시그니처 변경은 `workflow` 내부. 새 쓰기는 전부 v2 — 구 토큰으로 **쓰는** 경로는 남기지 않는다(codec 등록 쪽은 v2 만).

**D-6F10-6 — `OutboxClaimConcurrencyTest` 재작성.** 6D-1 D-6D-10 ② 모양(첫 워커가 행을 잡은 채 둘째가 claim → 둘째는 그 행을 못 집음 → 첫째 롤백 →
둘째 재claim 성공, `CountDownLatch.await` 반환값 단언, 시한 집음 5s/쥠 30s/합류 60s). 변이: production `SKIP LOCKED` 제거 RED · 완전 순차 RED.

**D-6F10-7 — 게이트·공유 파일.** 새 test 는 `gate-tests.properties` 등재(추가만). `architecture-policy.properties` 변경은 **키 단위로** 이 문서에
등재. 공유 파일 커밋은 hunk 격리 가능하게 **slice 산출물 커밋과 분리**. 레인 커밋 전 게이트 결과는 **종료 코드로**(`grep FAILED && …` 사슬 금지,
6D-1 교훈). 변이 전 커밋, 변이 적용은 `git diff --numstat` 로 먼저 확인(6F-4 교훈).

**D-6F10-8 — 값 두 축(Phase 1 규율).** ⓓ 는 값 slice 축을 갖는다 — ① **wire**: v2 payload 의 정책 버전·전략 revision 이 **outbox 행에 실린다**(DB 에서
되읽어 typed 등식) ② **거동**: 전략 revision 을 올리면 같은 입력의 payload 가 달라진다(6D-2 재현 등식의 음성 대조가 될 값).

## 위협 모델 — 6F-10 고유 경계 (0)

**방어하는 것**: ① **중복 발송 0**(at-most-once) — 모호한 행(`Unknown`·고아 `CLAIMED`)은 재시도 없이 격리 ② **조용한 상태 손실 0** — 전이 거부·
발송 실패·outbox 쓰기 실패가 계수·종료 코드로 보인다 ③ 종단 전이는 port·전이표를 지난다(사본 SQL 0, `internal` 완화 0) ④ dry-run 은 여전히
outbox 를 쓰지 않는다 ⑤ 억제 환경에서 claim·발송 0 ⑥ 구 payload 행 해독 유지 ⑦ 살아 있는 relay 의 in-flight `CLAIMED` 는 격리되지 않는다
(lease 아래에서만 claim·격리).
**방어하지 않는 것(경계 밖)**: 실 발송 채널·렌더링·라우팅(`OPEN-STR-12`) · **놓침**(at-most-once 가 감수하는 것) · outbox 보존·삭제(DELETE grant 없음,
운영 소관) · `idempotency_key` UNIQUE(D-6F7-3·6) · relay 수평 확장(단일 인스턴스 lease 전제) · 판정 기록 표(D-6F7-2) · 저장된 구행의 재작성.

### 우회 후보 — 설계 검토 (2) 의 입력 (≥5, 레인이 변이로 실측)

1. relay 가 claim → 발송 → 종단을 **한 트랜잭션**에 두어 크래시 롤백이 행을 PENDING 으로 되돌려 **재발송**. ← T1/T2 분리: 발송 뒤·종단 전 크래시 주입
   → 행이 `CLAIMED` 에 남음(PENDING 아님) 단언; 한 트랜잭션 변이 RED.
2. 고아 격리가 **살아 있는** relay 의 in-flight `CLAIMED` 를 격리(두 인스턴스·lease 없이 sweep). ← lease 미획득 relay 는 claim·격리 0 + 관측 결과를
   남김(ADR D-10 ③); 두 프로세스 변이에서 in-flight 행 격리 0 단언.
3. `mark*` 갱신 계수 0 무시 → 전이 실패가 사라짐. ← 계수 계약 + 「이미 다른 상태인 행」 변이 RED.
4. inbox **선기록** → 발송 실패 시 키 소진·행 좌초. ← `Rejected` 경로에서 inbox 행 0 단언; 선기록 변이 RED.
5. 호출부가 `Failed` 를 버려 판정 흔적 0 인 채 run 이 성공 종료. ← outbox 쓰기 실패 주입(제약 위반) → 결과 계수·종료 코드 비-0 단언.
6. 커밋 경로 구현이 dry-run 조립으로 흘러듦. ← dry-run 전후 outbox 등식 유지 + 바이트코드 집합 등식(dry-run 조립 참조 집합) 변이 RED.
7. 억제 환경에서 claim 뒤 `Suppressed` → 어휘 없는 종단 또는 `CLAIMED` 좌초. ← 억제 환경 relay 전후 등식(outbox 상태 분포 불변) test.
8. payload 필드 추가가 구행 해독을 깨뜨림 / 구 토큰 디코더 삭제. ← 구행 골든 해독 test + 디코더 제거 변이 RED.
9. (6D-1 교훈) 동시성 test 의 공허한 초록 — 래치 반환값 미단언. ← D-6F10-6.
10. (6G-2g 교훈) 바이트코드 비가시 간선 — `invokedynamic` 람다 본문·bridge 안의 참조는 집합 등식 게이트가 못 본다. ← 설계 검토 (2) 기본 항목.
11. 러너가 run-state·lease 를 **얻지 못했을 때** 조용히 exit 0. ← 억제 사유 + 비-0(또는 명시 코드) 단언(ADR D-10 ③).

### (2b) 값 획득 축 — 새 public 표면 전수 (설계 검토가 채우고, 구현 레인이 라운드마다 갱신)

| 새 public 표면(후보) | 손에 넣은 주체가 할 수 있는 것 | 판정 |
|---|---|---|
| relay use case 생성자(포트 주입 자리) | 자기 `OutboxPort`·`InboxPort`·sender 를 꽂아 전이를 몰 수 있다 — 배선 주체가 port 로 이미 가진 권한과 동치인가 | 설계 검토 |
| relay 결과 타입(계수·격리 목록) | 읽기만 — 조작의 자기서술성(결과가 쓴 값을 나르는가) | 설계 검토 |
| 고아 격리 함수/명령 | **임의 `CLAIMED` 를 격리**할 수 있는가(lease 없이 호출 가능하면 우회 2) | 설계 검토 — `internal` 또는 lease 토큰 요구 |
| lease port·`Held` 토큰 | 토큰을 밖에서 **만들** 수 있는가(위조) · **얻을** 수 있는가(획득) | 설계 검토 — 4A 통로 타입 선례 |
| 평가 커밋 결과 타입 | 읽기만 | 설계 검토 |
| `NotificationRequestedPayload` v2 필드 | 생성은 `workflow` 내부 투영만인가 | 설계 검토 |

## in_scope / out_of_scope

```yaml
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/event/**           # relay use case · 종단 전이 호출 · 고아 격리 · lease port · payload v2 타입
  - workflow/src/test/kotlin/bidvector/workflow/event/**
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/**      # 커밋 경로 · Failed 처분 · reach() 에 전략 revision
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/**
  - workflow/src/main/kotlin/bidvector/workflow/notification/**    # 결과→전이 매핑이 거기 닿을 때만 — DispatchNotification 자체는 기본 무변경
  - workflow/src/test/kotlin/bidvector/workflow/notification/**
  - adapters/src/main/kotlin/bidvector/adapters/event/**           # JdbcOutboxPort 계수 계약 · codec v2 · lease 어댑터 · relay 조립
  - adapters/src/test/kotlin/bidvector/adapters/event/**           # OutboxClaimConcurrencyTest 재작성 포함
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/**      # 커밋 경로 조립(선례 StrategyEditTransaction) · 자리지킴 sender 가 거기 살면
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/**
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**     # ConnectionSource/TransactionBoundary 가 필요할 때만 — 기본 무변경
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**
  - adapters/src/test/kotlin/bidvector/adapters/e2e/**             # 6D-1 test relay → production relay 교체(사본 제거)
  - app/src/main/kotlin/bidvector/app/wiring/**                    # relay · 평가 커밋 러너 배선(opt-in mode)
  - app/src/main/kotlin/bidvector/app/relay/**                     # 러너 본체가 거기 살면(수집 선례 app/collection)
  - app/src/main/kotlin/bidvector/app/evaluation/**                # 같은 이유
  - app/src/test/kotlin/bidvector/app/**
  - config/quality/gate-tests.properties                           # 공유 — 추가만
  - config/quality/architecture-policy.properties                  # 공유 — D-6A3-3 집합 등식 명시 갱신 · 키 단위 등재
  - config/quality/member-effects*.properties                      # 3층 등재가 필요하면(6G-2g 게이트)
  - docs/adr/0005-domain-events-and-outbox.md                      # 고아 격리·SkipDuplicate 처분·OPEN-OPS-10 ③ 등재(세션 모델) — A-1 (b) 면 D-3 개정
  - docs/discovery/data-dictionary.md                              # payload v2 · 상태 의미
  - reports/evidence/m6/6f10/**
  - reports/evidence/m6/6d/scope.md                                # 6D-2 행 문면 정정만(A-1 뒤) — 닫힌 evidence 는 그 외 무변경
  - milestone-6.md                                                 # 착수·종결 문단(팀장) — 공유
  # A-2 (b) 채택 시에만 더한다:
  # - adapters/src/main/resources/db/migration/V18__outbox_claim_lease.sql
out_of_scope:
  - 실 발송 채널·렌더링·라우팅 — production sender/RouteDirectory/ContentRenderer 0   # OPEN-STR-12
  - outbox 상태 어휘 변경(V6 CHECK) · idempotency_key UNIQUE · DELETE grant · 구행 재작성
  - db-scheduler 도입                                               # OPEN-6F10-SCHEDULER (A-3)
  - 판정 기록 표(D-6F7-2) · 전략·후보·사다리 로직 변경
  - 6D-2 — restart 수렴·redelivery E2E · 재현 등식 버전 포함
  - docker/compose.yaml                                            # *.mode 미설정 유지(D-6A2a-5) — 바꾸면 범위 밖
  - ml-engine/** · contracts/** · bid-vector(레거시, read-only)
```

## acceptance

CI job 명령 그대로(버릴 worktree/clone, `--rerun-tasks` 한 번): `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` ·
`./tools/one-command-check.sh`(S-20). production `src/main`·app 배선 변경이므로 **container job 로컬 재생**(S-21a·S-21b·S-22b·S-23·S-23b·S-25 —
컨테이너 스모크의 「수집 꺼짐」에 relay·evaluation mode 꺼짐 단언을 더할지는 설계 검토 (3)). 호스트 규율: 무거운 빌드는 호스트 전체 1개(pgrep ·
`free -m` · RSS 상위 — 별도 호출로 먼저).

변이(구현 레인 실측 → verifier 표적): 우회 1~9·11 각각 RED + 「더하기」 아닌 「바꿔치기」 변이인지 음성 대조(6G-2c 교훈). 값 두 축(D-6F10-8).
verifier 표적: T1/T2 분리(크래시 주입) · lease 없는 격리 · 계수 무시 · inbox 순서 · `Failed` 버림 · dry-run 등식 · 구행 해독 · (2b) 「경계로 처리」
행 실측.

## rollback (개요 — 레인이 `rollback.md` 에 실측과 함께 쓴다)

- **운영 비활성화**: `bidvector.relay.mode`·`bidvector.evaluation.mode` 미설정 = 러너 0(compose 기본) — 코드 되돌림 없이 꺼진다.
- **코드**: in_scope 경로 한정 `git restore --source=<base> --staged --worktree -- <개별 인자>`; 공유 파일(`gate-tests.properties`·
  `architecture-policy.properties`·`milestone-6.md`·ADR 0005·data-dictionary·6D scope)은 `git log --format=%h <base>..<실측 HEAD> -- <파일>` 산출로
  **뒤 커밋부터** hunk 역적용, 수동 해소 절차 명기. 확인은 「내 줄 사라짐」·「남의 줄 남음」 둘 다.
- **DB**: A-2 (a) 면 마이그레이션 0. 이미 `ISOLATED` 로 옮긴 행은 **되돌리지 않는다**(단방향 — 사실로 선언). A-2 (b) 면 미적용 DB 는 파일 삭제,
  적용 DB 는 새 V 의 `DROP COLUMN`.
- **payload v2 행**: 되돌린 트리의 codec 은 v2 토큰을 모른다(fail-closed `error`) — 되돌림 뒤 relay 는 v2 행에서 멈춘다. 그 행들의 처분(격리 또는
  코드 재적용)을 rollback.md 에 **미리** 적는다 — 이것이 이 slice 의 되돌리기 어려운 자리다.
- 실측 ⓪~⑥ 임시 clone, `실측 HEAD` 두 앵커(종결 커밋 · 마지막 산출물 커밋), 트리 동일성 갈음은 evidence 가 `leakPatternGate` 입력이라 불가 → 전부 실측.

## 검토 레인

- 설계 검토: 세션 모델 직접(`_workspace/m6-6f10/01_design-review.md`), A-1~A-6 뒤 · 구현 전.
- 구현: `kotlin-implementer`(sonnet) 1명, 순차. 0단계 보고(선택지·추천) → 팀장 계약 갱신 → 구현.
- 판정: `verifier`(opus) + `code-reviewer`(sonnet 명시) 병렬, 검증 전 **레인 동결 + 판정 SHA 고정**. A-2 (b) 면 `migration-reviewer` 추가.
  Codex: A-6.
- 재작업 카운터 **0/5**.

## 하네스 레인 변경 (상시 절)

없음 — 리뷰 요청 시점마다 `git log --format=%h <base>..HEAD -- CLAUDE.md .claude/` 산출로 갱신.
