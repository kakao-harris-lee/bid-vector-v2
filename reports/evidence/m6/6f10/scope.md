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
outbox 를 쓰지 않는다 ⑤ 억제 환경에서 claim·발송 0 ⑥ 구 payload 행 해독 유지 ⑦ **임대가 살아 있는** relay 의 in-flight `CLAIMED` 는 격리되지 않는다 — 임대 확인은 고아 격리·claim·행마다 발송
**전** 모두에서(D-6F10-27 ② · D-6F10-31 ②); 임대를 잃은 relay 는 더 격리·claim·발송하지 않고 `LeaseLost` 로 멈춘다. 남는 창은 「확인과 다음
동작 사이」·TCP 반개방(알려진 제한, 중복 발송은 0).
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
  - workflow/src/main/kotlin/bidvector/workflow/notification/**    # relay use case 본체(D-6F10-18 ② — workflow.event 는 순환 게이트로 불가) · DispatchNotification 자체는 기본 무변경
  - workflow/src/test/kotlin/bidvector/workflow/notification/**
  - adapters/src/main/kotlin/bidvector/adapters/event/**           # JdbcOutboxPort 계수 계약·kind claim · codec 필드 확장 · lease 어댑터(advisory lock)
  - adapters/src/test/kotlin/bidvector/adapters/event/**           # OutboxClaimConcurrencyTest 재작성 포함
  - adapters/src/main/kotlin/bidvector/adapters/relay/**           # relay 조립 + 자리지킴 셋(Unavailable*) — 새 패키지 + 자기 의존 test(D-6F10-18 ①, r3 추가)
  - adapters/src/test/kotlin/bidvector/adapters/relay/**
  - adapters/src/test/resources/compile-fixtures/**                  # 폐쇄 probe negative fixture(R1-M-2, r7 추가 — 새 파일 ↔ in_scope 대조)
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/**      # 커밋 경로 조립(선례 StrategyEditTransaction) — 의존 test 허용 루트에 adapters.event 한 줄(D-6F10-18 ③)
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/**
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**     # ConnectionSource/TransactionBoundary 가 필요할 때만 — 기본 무변경
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**
  - adapters/src/test/kotlin/bidvector/adapters/e2e/**             # 6D-1 test relay → production relay 교체(사본 제거)
  - app/src/main/kotlin/bidvector/app/wiring/**                    # relay · 평가 커밋 러너 배선(opt-in mode)
  - app/src/main/kotlin/bidvector/app/relay/**                     # 러너 본체가 거기 살면(수집 선례 app/collection)
  - app/src/main/kotlin/bidvector/app/evaluation/**                # 같은 이유
  - app/src/test/kotlin/bidvector/app/**
  - app/src/test/kotlin/bidvector/archfixture/violating/**         # 새 ArchUnit 규칙의 음성 대조 fixture(D-6F10-23, r5 추가 — 6F-9 선례와 같은 자리)
  - config/quality/gate-tests.properties                           # 공유 — 추가만
  - config/quality/architecture-policy.properties                  # 공유 — D-6A3-3 집합 등식 명시 갱신 · 키 단위 등재
  - config/quality/member-effects*.properties                      # 3층 등재가 필요하면(6G-2g 게이트)
  - docs/adr/0005-domain-events-and-outbox.md                      # 고아 격리·SkipDuplicate 처분·OPEN-OPS-10 ③ 등재(세션 모델) — A-1 (b) 면 D-3 개정
  - docs/discovery/data-dictionary.md                              # payload v2 · 상태 의미
  - reports/evidence/m6/6f10/**
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
`./tools/one-command-check.sh`(S-20). production `src/main`·app 배선 변경이므로 **container job 로컬 재생**(S-21a·S-21b·S-22b·**S-22c**·S-23·S-23b·
**S-23c**·**S-24**·S-25 — r12 에서 셋 추가, R3-L-5; ml-serving S-21·S-22a 는 입력 무변경이면 생략 가능, 사유 기록 —
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

## 계약 갱신 r1 — 운영자 결정 수령 (2026-10-06)

| 결정 | 내용 | 근거 |
|---|---|---|
| **D-6F10-9** | **운영자 결정 A-1~A-6 = 추천대로 전부 (a)**: A-1 고아 `CLAIMED` 는 **ISOLATED**(ADR 0005 at-most-once·전이표 그대로, PENDING 복귀 없음) · A-2 **새 열 없이** lease 기반 고아 판정(마이그레이션 0, `claimed_at` 관측은 `OPEN-6F10-CLAIM-OBSERVABILITY`) · A-3 `bidvector.relay.mode=once` 일회 러너(db-scheduler 는 `OPEN-6F10-SCHEDULER`) · A-4 `bidvector.evaluation.mode=once` 일회 러너, HTTP 는 dry-run 유지 · A-5 relay use case·조립·러너까지 production, **억제 환경에서는 claim 0**, sender 자리는 호출되면 던지는 자리지킴 · A-6 **Codex 안 탐**(verifier opus + code-reviewer sonnet) | 운영자 2026-10-06 |
| **D-6F10-10** | **6D-2 문면 정정의 자리.** 6D scope 제안 표의 6D-2 행(「reclaim → 정확히 한 번 발송」)은 **닫힌 evidence 라 고치지 않는다**(D-6D-19 선례: 닫힌 evidence 문면 무변경). 정정의 정본은 이 문서(수취 표·D-6F10-9)와 `milestone-6.md` 6F-10 착수 문단이고, 6D-2 착수 계약이 그 문면을 받는다 — 「claim 중 크래시 → 재기동 → 고아 **격리** → 발송 0 또는 1, **중복 0**(놓침 감수)」. in_scope 에서 `reports/evidence/m6/6d/scope.md` 를 뺀다 | A-1 (a) 귀결 |
| **D-6F10-11** | **lease 의 자리와 성질(A-2 a 의 전제)** — ADR 0005 D-10 대로 `workflow` 가 소유하는 port, `adapters` 가 구현. 요구 ②(홀더 사망 시 **즉시** 해제)를 만족하는 구현 후보 둘 — PostgreSQL **세션 advisory lock**(DB 와 같은 장애 영역, 연결이 끊기면 해제) 대 run-state **파일 잠금** 선례(`RunStateLock`, 프로세스 범위) — 중 **설계 검토가 고른다**. 고아 판정 술어는 「lease 를 새로 잡은 relay 가 **첫 claim 전에** 보는 `CLAIMED` 전부」이고, lease 를 얻지 못한 relay 는 claim·격리 **0** + 관측 결과(ADR D-10 ③). A-2 (b) 의 V18 행은 in_scope 주석으로만 남긴다(채택 안 함) | ADR 0005 D-10 |

## 계약 갱신 r2 — 설계 검토 결과 (2026-10-06, `_workspace/m6-6f10/01_design-review.md`)

| 결정 | 내용 | 근거 |
|---|---|---|
| **D-6F10-12** | **outbox 상태 어휘 해석표(V6 어휘 불변, 뜻을 셋으로)**: `DELIVERED` = 채널에 전달됨 **또는** 같은 멱등 키가 이미 전달됨(`SkipDuplicate` — 이 entry 의 의무가 이미 이행됨) · `FAILED` = 이 entry 로는 전달이 **일어나지 않았고 일어나지 않을 것**(`Rejected` · route 수준 `Suppressed`: 채널 비활성·route 없음; 재시도 없음) · `ISOLATED` = 전달 여부 **모호**(`Unknown` · 고아 `CLAIMED`). 환경 수준 억제(`environmentModes[env] != Live`)는 **claim 자체를 하지 않는다**(행 PENDING 보존). ADR 0005 addendum 으로 등재(세션 모델, 공유 파일 별도 커밋). 6D-1 relay 실측(SkipDuplicate 행이 CLAIMED 좌초)이 이 표가 없을 때의 결과다 | 설계 검토 (1)-A·(3) |
| **D-6F10-13** | **claim 은 payload 종류로 좁힌다.** `OutboxPort.claim(limit, kind)`·`claimedEntries(kind)` — relay 는 자기 종류(`NotificationRequested`)만 집고 그 종류의 고아만 격리한다. `StrategyUpdated`(전략 편집이 실제로 쓰는 행, 소비자 없음)는 **건드리지 않는다** — test: relay 전후 `StrategyUpdated` 행 상태 분포 불변. `kind` 는 `workflow` 값, `payload_type` 문자열 매핑은 adapters(codec). lease 키도 kind 단위. **신설 `OPEN-6F10-STRATEGY-EVENT-CONSUMER`** | 설계 검토 (1)-A, 우회 12 |
| **D-6F10-14** | **lease = PostgreSQL 세션 advisory lock**(run 동안 쥔 전용 연결, 프로세스 사망 → 연결 종료 → 해제; 알려진 제한: TCP 반개방은 서버 keepalive 까지 지연). 파일 잠금 불채택 — 고아 술어의 범위가 DB 라 다른 호스트의 relay 와 자물쇠 범위가 어긋난다. port `ConsumerLeasePort.withLease(kind, body)` → `Held(result) | Busy`, **토큰 타입 없음**(body 클로저 — 4A 식 통로 토큰의 adapters 생성 모순 회피). Busy 는 결과 값 + 러너 종료 코드(ADR D-10 ③) | 설계 검토 (1)-B |
| **D-6F10-15** | **D-6A3-17 게이트 술어·허용 키 무변경.** outbox 를 만지는 조립은 **adapters**(선례 `StrategyEditTransaction`), app 은 조립 결과만 든다 → ②·③ 그대로 초록. 게이트의 뜻은 「app 이 outbox 에 못 닿는다」→「adapters 조립을 통해서만 닿는다」로 바뀌므로 dry-run 보장은 ① 기존 E2E 전후 등식 + ② **새 ArchUnit 규칙**: dry-run 클래스 집합(`EvaluationDryRunFactory`·`EvaluationDryRunRun`·`EvaluationDryRunController`)이 커밋 조립 타입(새 키 `app.evaluation.commit-types`)을 참조하지 않는다(클래스 단위). `app.port-call.allowed-pairs`·`app.adapter.member-call-pairs` 는 러너 호출 쌍 **추가만**. adapters 쪽 의존 test 가 조립 자리를 막으면 허용 목록을 넓히지 않고 보고(0단계) | 설계 검토 (1)-D |
| **D-6F10-16** | **D-6F10-5 정정 — payload 는 제자리 형식 변경, v2 토큰·구 디코더 없음.** 실측: `OutboxNotificationRequestPort` 를 참조하는 main 코드는 자기 파일뿐이고 app 게이트 ③ 이 그 참조를 금지한 채 초록 → 어느 DB 에도 `NotificationRequested` 행이 영속된 적이 없다(test 는 Testcontainers 일회성). 구행 없는 구 디코더는 죽은 코드. `payload_type` 토큰 유지, 필드 수 상수 갱신, 왕복 + 새 형식 골든 test. 「production 행 0 의 근거」를 evidence 에 적는다 | 설계 검토 (1)-E·(3) |
| **D-6F10-17** | **러너 둘(app, opt-in `mode=once`)**: relay 러너는 `RuntimeEnvironment` 설정 필수(기본값 없음), sender 는 `UnavailableNotificationSender`(호출 시 예외), **Production 환경 설정이면 기동 거부**(실 sender 부재 = 설정 오류; `OPEN-STR-12` 가 푼다), owner 는 `OperatorProfilePort.current()`(없으면 기동 실패), channel 은 설정. 평가 커밋 러너는 `runBlocking { evaluate() }`(컨트롤러 선례), `Reached.notificationOutcome`(새 필드, BidNow 아니면 null)로 `Failed` 를 값으로 받아 수 > 0 이면 비-0 종료. 종료 코드는 사유 토큰과 한 쌍(수집 선례). 컨테이너 스모크에 「relay 꺼짐」 단언은 더하지 않고(ci.yml 밖) app test 로 「mode 없음 → 러너 빈 0」을 잠근다 — 알려진 제한 | 설계 검토 (1)-D·F·(3) |

## 계약 갱신 r3 — 구현 레인 0단계 보고 수령 (2026-10-06, `_workspace/m6-6f10/02_impl_stage0.md`)

레인 실측이 설계 검토의 전제 셋을 뒤집었다 — **A** relay 를 `workflow.event` 에 두면 `notification → event` 기존 간선과 순환(`packagesMustBeFreeOfCycles`, 하위 패키지 단위; D-6F7-1 이 기록한 함정) · **B** `ProfileFacts` 에 `OperatorId` 가 없어 `OperatorProfilePort.current()` 로 owner 를 얻을 수 없다 · **C** `PolicyVersion` 은 필드 둘(`effectiveFrom`·`source`)이라 새 칸은 3, 필드 수 17 → **20**. 레인 추천을 전부 채택한다.

| 결정 | 내용 | 근거 |
|---|---|---|
| **D-6F10-18** | **레인 결정 1~10 처분(전부 추천안)**: ① in_scope 에 `adapters/src/{main,test}/kotlin/bidvector/adapters/relay/**` 추가(새 패키지 + 바이트코드 형태 `RelayAdapterDependencyTest`, 허용 루트 `{workflow.event, workflow.notification, workflow.strategy, sharedkernel, adapters.event, adapters.persistence, adapters.relay}`) ② relay use case 는 **`workflow.notification`**(간선 0; `ConsumerLeasePort`·`LeaseAttempt`·`OutboxConsumerKind` 는 `workflow.event`; lease 어댑터는 `adapters.event` — 게이트 변경 0, `DataSource` 직접 수취) ③ 평가 커밋 조립은 `adapters.evaluation` + `EvaluationAdapterDependencyTest` 허용 루트에 **`bidvector.adapters.event` 한 줄**(선례 D-6A2b-3 과 같은 형태·사유, KDoc 에 근거; 클래스별 메서드 참조 허용 목록은 무변경) — D-6F10-15 「보고」 조건 충족으로 승인 ④ 러너 둘은 `app.assembly.tier3-collection-classes` 에 등재하고 키 주석에 「러너 레인 일반」을 적는다(키 신설 0; 이름 어긋남은 알려진 제한) ⑤ `Reached` 새 필드는 non-null sealed `NotificationDisposition { NotApplicable, Requested(outcome) }`(D-6F10-17 의 nullable 문면 정정 — §5 「불법 상태를 타입으로」) ⑥ `RouteDirectory`·`ContentRenderer`·`NotificationSender` **셋 모두** `Unavailable*` 자리지킴(`adapters.relay`), 실제 도달을 막는 층은 Production 기동 거부 + 억제 환경 claim 0 ⑦ owner 는 설정 `bidvector.relay.owner`(기본값 없음, 공백이면 기동 실패; `routesFor(owner)` 입력과 같은 축) · channel 은 `bidvector.relay.channel` · 환경은 `bidvector.relay.environment`(relaxed enum, 기본값 없음) ⑧ 종료 코드 enum 은 레인별 신설(`RelayExitCode` COMPLETE 0·FAILED 1·INCOMPLETE 2·LEASE_BUSY 3·ENV_SUPPRESSED 4 / `EvaluationCommitExitCode` COMPLETE 0·FAILED 1·INCOMPLETE 2), 사유 토큰 = `enum.name` 한 쌍 ⑨ 알려진 제한 등재 승인 — Production 이 아닌 배선에서 relay 는 늘 `ENV_SUPPRESSED`(실 claim 경로는 test 가 `Production` 주입으로만 돈다) · `claimedEntries` 무인덱스(`OPEN-6F10-CLAIM-INDEX`) · `payloadTypeOf` 두 오버로드 표류 가능(등식 test) · 커밋 러너 배포도 `bidvector.evaluation.candidate-cap` 필수 ⑩ 컴파일 확인 1회 허용(빈 조립 골격 + 의존 test 셋; 호스트 규율 선행) | 레인 0단계 §1·§6·§9 |
| **D-6F10-19** | **payload 세부(레인 §3)**: 새 칸 셋은 `bidNowReasons` 뒤·evidence 앞(인덱스 2·3·4 — 파생 상수 사슬 보존) · typed 필드(`ladderPolicyVersion: PolicyVersion`·`strategyRevision: StrategyRevision`, boundary·허용 루트 안 실측) · `NotificationRequest internal constructor` 에 두 인자 **기본값 없이** 추가 · `reach()` 7번째 인자 `strategyRevision`, 호출자 둘은 `analyzeAndJudge` 의 `strategy.revision` · 사다리 `PolicyVersion` 리터럴을 `LadderPolicySlot.kt` 의 `internal val EVALUATION_LADDER_POLICY_VERSION` 한 자리로 올려 `Resolution.Resolved` 와 payload 가 **같은 인스턴스**를 쓴다 · `OutboxPayloadCodecTest` 에 새 형식 wire 골든 신설(기존은 왕복만) | 레인 0단계 §3 |
| **D-6F10-20** | **프로퍼티**: `bidvector.evaluation.mode=once`(`EvaluationProperties` 무편집 — `@ConditionalOnProperty` 가 Environment 를 직접 읽는 수집 선례) · `bidvector.relay.mode=once`. 커밋 러너의 후보 상한은 기존 `candidate-cap` 그대로(둘째 상한 금지). Production 기동 거부는 `RelayWiring` 의 `@Bean` 생성 시점 `require`(러너가 돌기 전 실패 → claim 0) | 레인 0단계 §6 |
| **D-6F10-21** | **(2b) 「경계로 처리」 넷의 실측 방법 채택**(레인 §7-2): `EventInternalClosureCompileTest` 에 `ToIsolated` 직조 probe 1건 · `OutboxPort.claim` 을 `app.port-call.allowed-pairs` 에 등재 + workflow 안 호출자 == relay 단독을 상수 풀로 · `PostgresAdvisoryLockLeaseTest` 두 실 연결 Busy(Testcontainers) · 조립 생성자 호출자 집합 == 러너 wiring(`app.adapter.member-call-pairs` + 양성 대조 fixture). 변이 10(람다 tee)은 게이트 **초록**을 먼저 기록한 뒤 E2E RED 를 기록한다(분담의 측정) | 레인 0단계 §7 |
| **D-6F10-22** | **⑩ 컴파일 실측 수령 — 허용 목록 판정 전부 예측대로**(의존 게이트 다섯 exit 0; `EvaluationAdapterDependencyTest` 한 줄 외 기존 게이트 무변경). 실측이 더 뒤집은 둘 수용: ① `ConsumerLeasePort`·`ConsumerTransactionPort`(T1/T2 를 `workflow` 가 소유하는 트랜잭션 port — 새 public 표면, (2b) 표에 행 추가 요구) 는 메서드 자신의 타입 매개변수 때문에 `fun interface` 불가 → 평범한 `interface`(`ConnectionSource` 와 같은 사유) ② 평가 커밋 러너에 production `CapacityPort` 가 없다(유일한 구현 `RequestCapacityPort` 는 HTTP 요청 본문의 `currentActiveBids`) → 설정 키 `bidvector.evaluation.current-active-bids`(기본값 없음, 미설정 기동 실패)로 받는다 — 결정 ③(2026-09-18)의 「현재 활성 수는 호출자가 싣는다」를 일회 러너에서는 **호출자 = 설정을 주는 운영자**로 읽는다; 「0 고정」은 D-6A3 가 기각한 것이라 쓰지 않는다. **신설 `OPEN-6F10-CAPACITY-SOURCE`**(활성 투찰 수의 자동 출처 — 개찰·낙찰 데이터가 생기면) | 레인 ⑩ 보고 |
| **D-6F10-24** | **구현 완료 보고 수령·레인 동결 (2026-10-07, `_workspace/m6-6f10/03_impl_report.md`)** — 산출물 커밋 여섯(`ee149fcc` workflow · `eb76ac10` adapters · `de05f731` app · `608baa82` 공유 장부 · `d857909c`·`cf59db87` test), **마지막 산출물 `cf59db87` · 판정 SHA `f455d1bf`**(evidence 커밋 = HEAD). `check` exit 0 을 두 HEAD 에서(workflow 415 · adapters 132 · app 57 클래스, 실패 0). 변이 1~14 전부 RED(변이 9 는 래치 반환값 단언 하나가 잡음 · 변이 10 은 세 모양 전부 새 ArchUnit 규칙이 잡아 사각 미발견). (2b) 15종 전수. rollback ①~⑥ 실측 HEAD `cf59db87`. **레인 처분 수용**: 전이 예외 → `FAILED`(exit 1, 재던짐), `INCOMPLETE`(exit 2) 는 미지 payload > 0 — D-6F10-18 ⑧ 문면 정정(수집 선례의 「실행 실패/끝났지만 미완」 구분). 레인 모델 Opus 5(정의는 sonnet — 스폰 inherit, 다음 레인부터 명시). **미완(레인 선언)**: acceptance 나머지 셋(`qualityBaseline` · S-20 · 컨테이너 job 로컬 재생)은 verifier 가 판정 SHA 에서 돌린다 | 레인 보고 |
| **D-6F10-25** | **verifier 표적(판정 SHA `f455d1bf`, 산출물 `cf59db87`)**: ① acceptance 나머지 셋 실행(호스트 규율, 컨테이너 job 은 호스트 전체 1개) ② 변이 재생 — 1(T1/T2) · 2(lease 없는 격리, 두 연결) · 4(inbox 순서) · 5(`Failed` 버림) · 7(억제 환경 claim) · 9(SKIP LOCKED) · 12(kind 필터) 최소, 그리고 **레인이 재지 않은 모양**(변이 10 의 `invokedynamic` 람다 tee 를 **E2E 거동**으로; 더하기 변이 음성 대조) ③ payload 필드 고정 게이트 허용 집합 변경의 민감성 판단(정책 식별자·정수 revision) ④ (2b) 「경계로 처리」 행의 실측(D-6F10-21 넷 + `ConsumerTransactionPort` 「새 권한 0」 논증) ⑤ 어휘 해석표 D-6F10-12 가 코드와 일치하는가(`SkipDuplicate`→DELIVERED · route `Suppressed`→FAILED · `Unknown`→ISOLATED · 환경 억제 → claim 0 — 각각 DB 상태로) ⑥ 고아 격리가 **살아 있는 relay 의 in-flight 를 격리하지 않는가** ⑦ 새 파일 ↔ in_scope 대조 · rollback 유효성(`git diff --name-only cf59db87..f455d1bf -- <되돌림 경로>` 빈 출력). code-reviewer(sonnet) 병렬 — 빌드 금지(정적 + 레인 test 결과 읽기), 산출물 커밋 여섯의 diff | D-6F10-24 |
| **D-6F10-26** | **판정 r1 수령 @`f455d1bf`** — verifier **not-ready**(`04_verifier_r1.md`: **R1-H-1** production 커밋 조립 `EvaluationCommitRun` 을 어떤 test 도 돌리지 않는다 — 알림 port 를 Recording 으로 바꿔치워도 240 클래스 초록, D-6F10-4 가 요구한 「변한 표는 outbox 하나」 test 부재 · R1-M-1 임대 연결만 끊기면 둘째 relay 가 살아 있는 첫째의 in-flight 를 격리(중복 발송 0, 위협 ⑦·제한 11 문면과 어긋남) · R1-M-2 compile-fixture 둘 in_scope 밖 · R1-M-3 고아 조회 kind 필터 미잠금 · R1-M-4 커밋 배선이 전략을 두 번 읽음 · low 5). acceptance 나머지 셋 **전부 exit 0**(qualityBaseline · S-20 · 컨테이너 재생 S-21a~S-25, 앱 로그에 relay·커밋 start 줄 0) · `check --rerun-tasks` exit 0(실제 실행) · 변이 일곱 + 더하기 음성 대조 RED · rollback 유효. code-reviewer **새 high 0 · medium 6 · low 9**(`05_code_review_r1.md`: G-1 Production 거부가 enum 이름을 봄 · G-2 data-dictionary §2.2.5 구 문면 정본 충돌 · G-3 새 규칙 두 집합이 열거 · L-1 production relay 조립 거동 미측정 · L-2 `tallyOf`·러너 `run()` 미측정 · L-3 전량 실패 run exit 0). **재작업 1/5.** 호스트 조율: 고아 daemon(PID 3403262, k-6f10 잔존) 팀장이 그 PID 만 종료 · kis 세션 이미지 빌드에 슬롯 양보 1회 | 판정 레인 둘 |
| **D-6F10-27** | **수정 라운드 1 처분 — 산출물(레인, finding 군별 별도 커밋)**: ① **R1-H-1 + cr L-1·L-2·L-4**: production 조립 둘을 **실 DB 로 돌리는 test** — (a) `EvaluationCommitRun`: 커밋 run 뒤 **변한 표 == {outbox}**(표별 행 수 전후 등식) + BidNow 가 outbox 행을 남김(typed 되읽기) + **outbox 쓰기 실패 주입**(CHECK 위반을 만드는 payload 또는 권한 회수) → `Requested(Failed)` → `tallyOf` → 러너 `run()` 직접 돌려 종료 코드 비-0 (b) `NotificationRelayRun`: 조립을 그대로 돌려 relay 한 바퀴(경계 인스턴스 분리 변이 RED) (c) `FAILED`(1)를 만드는 경로 — 러너가 예외를 잡아 사유 토큰 + `terminate(1)`(죽은 열거 값 해소), 예외 주입 test · 회귀: 알림 port Recording 바꿔치기 RED ② **R1-M-1**: 행마다 발송 **전** 임대 생존 확인(임대 연결 `isValid`/`SELECT 1`), 죽었으면 남은 행 중단 + `RelayReport.LeaseLost`(종료 코드 FAILED) · 위협 ⑦ 을 「**임대가 살아 있는** relay 의 in-flight」로, 제한 11 문면 정정 · probe V6b 를 상설 DB test 로 ③ **R1-M-3**: `CLAIMED StrategyUpdated` 행 심고 relay 전후 불변(변이 12b RED) ④ **R1-M-4 / cr L-11**: 전략 한 번 읽기 — dry-run 선례 `PinnedStrategyRepository` 로 고정, KDoc 사실화 ⑤ **cr G-1**: Production 거부 술어를 `policy.environmentModes[env] == Live` 로(정책표 변이 test) ⑥ **cr G-3 + R1-L-1**: 새 규칙 선택자를 중첩 클래스까지(`fullName == n || startsWith("$n$")`), 두 집합에 **모집단 등식**(app 안 `EvaluationDryRun*`/`EvaluationCommit*` 이름 집합 == 키 집합) 추가, KDoc 사각 문장을 실측(중첩 클래스 선택자)으로 ⑦ **cr L-3 → 계약 정정(D-6F10-18 ⑧)**: `INCOMPLETE`(2) = 미지 payload > 0 **또는** 「claim > 0 이고 delivered == 0 이고 failed+isolated > 0」(아무것도 전달하지 못한 run) ⑧ **R1-L-3**: probe V5a~d(SkipDuplicate·Rejected·Unknown·route Suppressed → DB 상태) 상설 test ⑨ **R1-L-4**: 크래시 주입을 순번이 아니라 「발송 직후」에 ⑩ low 일괄 한 커밋: R1-L-2 KDoc `FAILED` · cr L-5 상수 재계산 등식 삭제 · L-6 unlock 예외 `addSuppressed` · L-7 추출 층 양성 대조 · L-8 `Diagnosed` 골든 · L-9 등재 정렬 · L-10 리스트 모양 · L-12 KDoc 사실화(⑦ 뒤 INCOMPLETE 의 도달 가능 뜻) · R1-L-5 배치 좌초 알려진 제한 등재. **팀장**: R1-M-2 in_scope 에 `adapters/src/test/resources/compile-fixtures/**`(이 r7) · **cr G-2** data-dictionary §2.2.5 를 ADR §7.1 해석표로 정정(공유 파일 별도 커밋). **①②③⑤⑥⑦⑨ 는 술어·거동 변경 → verifier r2 는 모든 커밋 표적** + cr r2 | verifier · code-reviewer |
| **D-6F10-28** | **수정 라운드 1 수령·동결 (2026-10-07)** — 산출물 커밋 열하나(`84bbb915` R1-M-1 임대 생존·LeaseLost → `106180fa` cr G-1 → `cc868486` cr L-3·L-4 → `8143158c` R1-M-4 → `2ff3b4b4` → `b6523054` 공유 장부 → `5afac29d` **R1-H-1** 커밋 조립 실 DB → `d3de268b` R1-M-1·M-3·L-3·L-4 DB test → `1559246b` cr G-3 → `6b73c288` low 일괄 → `63a15fda` M-4 단언 교정), **마지막 산출물 `63a15fda` · 판정 SHA `eac96249`**(evidence 둘 = HEAD). `check` exit 0(산출물 트리). R1-H-1 닫힘(변이 C 가 두 단언 RED: 변한 표 `{outbox}`→`{}` · 종료 코드 2→0). cr G-1 비대칭 실측(단독 변이 초록 — 정책표 `Staging→Live` 변이로 가름: 새 술어 RED · 앞 판 초록). 변이 M4 가 처음 초록이어 단언을 `evaluate()` 까지로 교정(`63a15fda`). **레인 처분 넷 수용**: ① cr L-6 `addSuppressed` 대신 해제 예외 삼킴(`Throwable` 포획을 detekt 가 막음 — KDoc 에 잃는 것·지키는 것) ② cr G-3 모집단 등식은 **금지 대상**만(「dry-run 조립」에 기계적 정의 없음 — 선택자는 보증 문면 한정) ③ 러너 정제 예외 타입 둘 삭제 ④ 러너 결과 보조 타입 두지 않음(app 최상위 타입 증가가 셋을 붉힘). 교훈: Spring 으로 러너를 돌리면 `exitProcess` 가 test JVM 을 죽여 Gradle 이 「초록 + skipped」로 보고 → 조립을 직접 세움(`RecordedExitCodes` KDoc). (2b) +3(`LeaseGuard`·`RelayReport.LeaseLost`·test 기록 대역) −2. 면제 수 46→44. rollback 재실측 HEAD `63a15fda`(D36/M28, 되돌리지 않은 일곱 HEAD 그대로, check 0, 유효성 통과). **비밀값 스캔 매치 2** = 새 E2E 의 test 컨테이너 자격 리터럴(저장소 다른 app E2E 열 곳과 같은 형태) — verifier 가 사실 확인. 알려진 제한 열넷(11 정정 · 12~14). **미완**: acceptance 나머지 셋은 `f455d1bf` 실측뿐 — production 이 바뀌었으므로 **verifier r2 재실측** | 레인 보고 「수정 라운드 1」 |
| **D-6F10-29** | **verifier r2 표적(판정 SHA `eac96249`, 산출물 `63a15fda`) — 모든 라운드 1 커밋**: ① acceptance 넷 재실측(`check --rerun-tasks` · `qualityBaseline` · S-20 · 컨테이너 재생) ② R1-H-1 종결 실측 — 변이 C(커밋 조립 port 바꿔치기) RED 재생 + 실 DB test 의 세 단언(변한 표 == {outbox} · payload 에 정책 버전·전략 revision · 쓰기 실패 주입 → 비-0)이 각각 독립으로 RED 를 내는가(하나를 끄면 다른 둘이 잡는가 아니라 **각 단언이 자기 변이를 잡는가**) ③ R1-M-1 임대 생존 확인 — probe V6b 재생(둘째 relay 격리 0 또는 첫째가 `LeaseLost` 로 멈춤), 제한 11 정정 문면 ↔ 실측, 남는 창 둘(발송 중 끊김·TCP 반개방) 문면 대조 ④ cr G-1 정책표 변이(`Staging→Live`) RED · 앞 판 술어 초록 재생 ⑤ cr G-3 모집단 등식(금지 대상에 새 `EvaluationCommit*` 클래스 추가 → RED) + 선택자 중첩 클래스(변이 10b 재생) ⑥ INCOMPLETE 확장 조건의 경계(claim>0·delivered==0·failed+isolated>0 각 경계값) ⑦ `FAILED` 경로(러너 예외 → 1) · `LeaseLost` 매핑 ⑧ 비밀값 매치 2 가 test 컨테이너 리터럴인지(값·자리) ⑨ 장부: 새 파일 ↔ in_scope · clean-tree · rollback 유효성(`63a15fda..eac96249` 되돌림 대상 변화 0) · 크기 게이트 · 좌표. **cr r2(sonnet, 빌드 금지)**: 라운드 1 diff `f455d1bf..63a15fda` — 처분 넷의 타당성 · 삼킨 해제 예외의 범위 · 새 public 표면 셋 · 러너 직접 조립 test 의 공허 여부 | D-6F10-28 |
| **D-6F10-30** | **판정 r2 수령 @`eac96249`** — verifier **not-ready**(`06_verifier_r2.md`: **R2-H-1** payload 두 값(`ladderPolicyVersion`·`strategyRevision`)의 **투영**(`NotificationRequest → NotificationRequestedPayload`)을 틀린 값으로 바꿔도 242 클래스 초록 — 커밋 E2E 가 payload 를 noticeId 부분 문자열로만 보고 typed 되읽기가 없다(D-27 ①(a)·D-29 ② 셋째 단언 부재, checklist 「값 두 축 ①」 문면이 측정된 것처럼 적혀 있다) · **R2-M-1** 임대 재확인이 고아 격리·claim **앞**에 없다 — 획득 직후 임대를 잃은 relay 가 정당한 둘째의 in-flight 를 격리하고 claim 0 이면 guard 를 묻지 않아 exit 0(probe V6c; = cr R-1) · low 6(R2-L-1 relay 러너 성공 경로 `terminate` 미측정 · L-2 커밋 러너 예외 경로 미측정(= cr R-6) · L-3 커밋 E2E 순서 의존(= cr R-9) · L-4 옛 전제 KDoc 셋 · L-5 rollback.md 제목 SHA/수 · L-6 scope 위협 ⑦ 문면(팀장 — 이 r9 에서 정정)). r1 finding 열 전부 닫힘. acceptance 넷 전부 exit 0(`check --rerun-tasks` 359 실행 · qualityBaseline · S-20 Python 1420 · 컨테이너 S-21a~S-25, relay/commit start 줄 0). rollback @`63a15fda` ⓪~⑥ 0, 유효성 빈 출력. 비밀값 매치 2 = Testcontainers 일회성 자격 리터럴(다른 app test 11 파일과 같은 형태 — 사실 확정). code-reviewer r2 **새 high 0 · medium 3 · low 10**(`07_code_review_r2.md`: R-1 = R2-M-1 · R-2 cr G-1 거동 변화에 상설 잠금 없음(앞 판 술어로 되돌려도 초록) · R-9 = R2-L-3 · low: R-3 INCOMPLETE 두 갈래 미분리 · R-4 모집단 등식이 `$` 이름을 버림 · R-5 = R2-L-1 · R-6 = R2-L-2 · R-7 ADR §7.1 ISOLATED 유입에 미지 payload 없음 · R-8 제한 13·KDoc 문면이 술어보다 좁음 · R-10 등재 정렬 재발 · R-11 E2E KDoc 둘 · R-12 `PinnedStrategyRepository` KDoc 「dry-run 전용」 · R-13 test 잔가지 넷; (2b) 누락 `ConsumerLeasePort.withLease` 시그니처 변경). r1 finding 15 중 13 종결, 둘은 처분대로 반(G-3 선택자 등재 유지 · L-2 러너 비대칭). **재작업 2/5** | 판정 레인 둘 |
| **D-6F10-31** | **수정 라운드 2 처분 — 산출물(레인, 군별 별도 커밋)**: ① **R2-H-1**: 커밋 E2E 에서 outbox 행을 `OutboxPayloadCodec.decode` 로 **typed 되읽어** `ladderPolicyVersion == EVALUATION_LADDER_POLICY_VERSION`·`strategyRevision == 그 run 이 읽은 전략의 revision` 등식 + 저장된 `payload_type` 대조(D-6F10-8 ①) · **거동 축**(D-6F10-8 ②): 전략 revision 을 올린 두 번째 run 의 payload 가 달라짐 단언 · 회귀: `toOutboxPayload` 두 값 바꿔치기 RED · checklist 「값 두 축」 문면을 측정된 것만으로 ② **R2-M-1 / cr R-1**: `LeaseGuard` 확인을 **임대 획득 직후·고아 격리 전·claim 전·행마다 발송 전** 전부에 — 어느 지점에서든 잃으면 `LeaseLost`(exit FAILED) 로 멈추고 격리·claim·발송 0 · claim 0 경로도 guard 결과를 본다 · KDoc 셋(R2-L-4: `PostgresAdvisoryLockLease`·`RelayOutboxNotifications`·`OutboxPort.claimedEntries`) 「살아 있는 홀더…」·「전부 죽은 홀더의 것」 문장 정정 · probe V6c 상설 DB test(획득 직후 상실 → 격리 0 · LeaseLost · exit 1) ③ **cr R-2**: G-1 의 Live 판정을 **순수 함수**(환경·정책표 → 거부 여부)로 뽑아 `RelayWiring` 이 그것을 부르고, test 가 `Staging→Live` 변이 표로 그 함수를 직접 쳐 RED(배선 변경·정책 bean 주입 없이 상설 잠금) ④ **R2-L-3 / cr R-9**: 커밋 E2E 순서 독립 — 저장소 규율대로 각 test 가 `PersistenceTestSupport` 의 표 비우기를 지난다(`NOT VALID` 가 아니라 비우기) ⑤ **R2-L-1·L-2 / cr R-5·R-6**: relay 러너 성공 경로(실 DB + `Staging` → `terminate` 기록 `[4]`) · 커밋 러너 예외 경로(던지는 port 또는 연결 불가 DataSource → `[1]` + 실패 줄) ⑥ **cr R-3** INCOMPLETE 두 갈래를 가르는 표본 둘(미지 payload 만 · 전달 0 만) ⑦ **low 일괄 한 커밋**: cr R-4 모집단 등식에 `$` 이름 포함 · R-8 제한 13·KDoc 문면을 술어와 같게 · R-10 등재 정렬 · R-11 E2E KDoc 둘 · R-12 Pinned KDoc 「dry-run 전용」 제거 · R-13 test 잔가지 넷 · (2b) 표에 `withLease` 시그니처 변경 행 추가 · R2-L-5 rollback.md 제목 SHA/수 → **rollback ⓪~⑥ 재실측(마지막 산출물 커밋에서)**. **팀장(이 r9)**: R2-L-6 위협 ⑦ 문면 · cr R-7 ADR §7.1 ISOLATED 유입에 미지 payload(심층 방어) 추가(별도 커밋). **①②③④⑤ 술어·거동 변경 → verifier r3 모든 커밋 표적** + cr r3 | verifier · code-reviewer |
| **D-6F10-32** | **수정 라운드 2 수령·동결 (2026-10-07)** — 산출물 커밋 열셋(`f1620ab4` 임대 재확인 넷 → `304d21a7` 거부 판정 순수 함수 → `8645819a` payload 투영 등식·거동·E2E 순서 → `9690e5b1` 러너 두 경로 → `bcb6c1f1` INCOMPLETE 두 갈래 → `fd0b529a` low 일괄 → `e60b0849`·`fe5670e9` 공유 장부 → `47af146e` → `e47955c3` 500줄 분리 → `64a4a201` 점검표 → `c037c45e` E2E 순서 고정), **마지막 산출물 `c037c45e` · 판정 SHA `5b7f26b7`**. `check` exit 0(workflow 50/422 · adapters 134/885 · app 61/550, 실패 0). 처분 ①~⑦ 전부 반영, in_scope 밖 0. **레인 판단 둘 수용**: (가) typed 되읽기를 app E2E 한 자리에 둘 수 없음(`OutboxPayloadCodec` 은 adapters `internal`, adapters test 는 `NotificationRequest`·`Verdict.BidNow` internal 생성자로 요청을 못 만듦 — 컴파일 거부 실측) → 가시성 완화 없이 **축 셋으로 가름**: 타입 등식은 workflow(투영) · 형식은 adapters 골든 · DB 열·거동은 app — 어느 변이가 어디서 붉는지 점검표 (나) 방어를 더한 것만으로는 측정이 안 됨 — cr R-4 접기는 중첩 전용 참조자 입력과, ④ 표 비우기는 `@Order` 불리한 순서와 함께여야 RED(r1 G1 계열). 새 변이 아홉 전부 RED(초록 둘은 의도한 음성 대조). 게이트가 네 번 잡음(ReturnCount · ktlint 둘 · sizeGate 500 · 등재 둘). rollback 실측 HEAD `c037c45e`(69 · D40/M29 · 되돌린 트리 check 0 · 유효성 빈 출력; ④ `--rerun-tasks` 사유 기록). 제한 15~17. 미완: acceptance 나머지 셋 → verifier r3. 재작업 **2/5 유지** | 레인 보고 「수정 라운드 2」 |
| **D-6F10-33** | **verifier r3 표적(판정 SHA `5b7f26b7`, 산출물 `c037c45e`) — 라운드 2 커밋 열셋 전부**: ① acceptance 넷 재실측 ② **R2-H-1 종결** — `toOutboxPayload` 두 값 바꿔치기가 **어느 층에서** RED 인지(workflow 투영 등식), 상수 1 변이(revision 표본이 비기본값인가), app E2E 의 DB 열 등식이 codec 없이 무엇을 재는가(문자열 부분 매치로 퇴행하지 않았는가), 거동 축(revision 변경 → payload 변화) ③ **R2-M-1 종결** — probe V6c(획득 직후 상실) 재생: 격리 0 · `LeaseLost` · exit 1; 네 지점 각각 하나씩 빼는 변이가 상설 test 에서 RED 인가 ④ cr R-2 — 거부 판정 순수 함수의 정책표 변이 RED + `RelayWiring` 이 **그 함수를** 부르는가(함수만 고치고 배선이 옛 술어를 쓰는 변이) ⑤ R2-L-3 — `@Order` 불리한 순서에서 비우기 제거 변이 RED ⑥ 러너 두 경로(`[4]`·`[1]`) 변이 ⑦ INCOMPLETE 두 갈래 표본 각각 ⑧ 장부: 새 파일 ↔ in_scope · clean-tree · rollback 유효성(`c037c45e..5b7f26b7`) + 절차 재실행 · 제한 열일곱 ↔ 승인 문서 · 좌표 · 크기 게이트. **cr r3(sonnet, 빌드 금지)**: diff `63a15fda..c037c45e` — r2 finding 종결 대조 · 레인 판단 둘 타당성 · `RelayBootDecision` 순수 함수와 배선의 결합 · 임대 재확인 네 지점의 창 · 500줄 분리의 결합도 · 새 public 표면 | D-6F10-32 |
| **D-6F10-34** | **사실 선언 — 동결 뒤 커밋 하나.** 레인이 동결 보고 뒤 `a37e5d59`(evidence 둘: `checklist.md`·`commands.md` — R2-H-1 표본 비기본값 전제의 양방향 측정: 상수 1 변이 단독 RED · 표본을 1 로 바꾸면 초록)를 올렸다. 원인은 팀장의 R2-H-1 보강 한 줄이 레인의 동결 보고와 엇갈려 뒤에 처리된 것. 산출물 경로는 `c037c45e` 이후 무변경(`git diff --name-only c037c45e..a37e5d59` = evidence 만). 처분: 산출물 판정 SHA 는 `c037c45e`/`5b7f26b7` 그대로, verifier r3 는 evidence 를 `a37e5d59` 에서 읽고 rollback 유효성을 `c037c45e..a37e5d59` 로 잰다. 레인에 「동결 뒤 추가 지시는 커밋하지 말고 묻는다」 재지시. 이력 되쓰기 없음 | 「레인 동결 + 판정 대상 SHA 고정」 |
| **D-6F10-35** | **판정 r3 수령 @`5b7f26b7`(산출물 `c037c45e`)** — verifier **ready-for-review**(`08_verifier_r3.md`: 새 finding 6 — **R3-M-1** `RelayWiring` 만 옛 `environment != Production` 술어로 되돌려도 app 550 test 초록(배선이 `relayBootDecision` 을 부르는지 아무것도 재지 않음; cr R-2 의 함수 쪽만 닫힘) · R3-L-1 「억제 판정 전 임대 확인」 순서 미잠금 · R3-L-2~4 장부(rollback.md 「셋」·「다섯」, 제한 11 「남는 창은 둘」 ↔ 제한 15, 점검표 값 축 ③ 「그 행을」 과장) · R3-L-5 acceptance 목록에 S-22c·S-23c·S-24 누락(팀장 — 이 r12). acceptance 넷 exit 0(컨테이너 재생은 S-21a~S-25 전부, S-23c·S-24 첫 재생 포함) · rollback ⓪~⑥ @`c037c45e` 0 · 유효성 `..91a54627` 빈 출력 · 표적 ② 투영 변이는 workflow 에서, 정책 source·revision 상수 변이는 app E2E 에서 RED · V6c probe = `LeaseLost`·격리 0·exit 1). code-reviewer r3 **high 0 · medium 1 · low 6**(`09_code_review_r3.md`: **T-1** 임대 재확인이 **고아 격리 루프 안**에 없다 — 격리 도중 임대를 잃으면 남은 고아를 임대 없이 전부 태움(제한 15·위협 ⑦ 「확인 직후 한 질의」가 그 루프에 거짓) · T-2 네 지점 중 첫 지점이 변이로 안 갈림(전 test 가 Live 환경 — 억제 환경 fake 하나로 닫힘; = R3-L-1) · T-3 `RelayWiringTest` KDoc 이 없는 test 를 가리킴 · T-4 접기가 대상 쪽엔 없음 · T-5 등재 정렬 세 번째 재발 · T-6 라운드 2 (2b) 절 부재 · T-7 점검표 제한 표를 끊는 빈 줄 여섯). r2 finding 17 중 16 종결. **재작업 2/5 유지** — 산출물 blocker/high 0 이므로 라운드가 아니라 **승인 전 일괄** | 판정 레인 둘 |
| **D-6F10-36** | **승인 전 일괄 처분 — 산출물 한 커밋(레인)**: ① **cr T-1** `isolateOrphans(guard)` — 고아마다 격리 전 임대 확인, 잃으면 `LeaseLost`(호출부 `leaseLostBefore(orphansIsolated)` 분기 재사용) · 회귀: 격리 도중 임대 종료 DB test(격리가 멈춤·남은 CLAIMED 보존) ② **R3-M-1** 배선이 `relayBootDecision` 을 **부르는지** 잠금 — 거동으로: 정책표를 주입 가능하게 하지 않고도 가능한 길(예: `RelayWiring` 의 거부 분기가 함수 결과를 그대로 쓰는지 바이트코드 참조 단언 + 환경 `Production` 기동 거부 test 가 함수를 지나는 구조) 을 레인이 고르되, **배선만 옛 술어로 되돌리는 변이가 RED** 여야 한다 ③ **R3-L-1 / cr T-2** 억제 환경(`Staging`) fake 한 건으로 「임대 확인 → 억제 판정」 순서와 첫 지점 잠금(분기 뒤바꾸기 변이 RED) ④ cr T-4 접기를 대상 쪽에도 · T-5 등재 정렬 · T-3 KDoc. **evidence 한 커밋(레인)**: R3-L-2 rollback.md 「셋」→넷·「다섯」 정정 · R3-L-3 제한 11 「남는 창은 둘」을 제한 15 와 합치 · R3-L-4 점검표 값 축 ③ 문면 · T-6 라운드 2 (2b) 절(`RelayBootDecision`·`RelayBootDecisionKt`) · T-7 표 빈 줄 제거 · **rollback ⓪~⑥ 재실측(산출물 커밋에서, 실측 HEAD 갱신)**. **팀장(이 r12)**: R3-L-5 acceptance 에 S-22c·S-23c·S-24. **①②③ 은 술어·거동 변경 → verifier 표적 재검증 1회(그 커밋만: T-1 격리 도중 상실 · 배선 옛 술어 변이 · 분기 뒤바꾸기 변이 · 전건 `check`)**, 통과 시 종결 | 「장부층·low 는 승인 전 일괄」·「술어 변경은 표적 재검증」 |
| **D-6F10-37** | **승인 전 일괄 수령·동결 (2026-10-07)** — 산출물 **`2bc08209`**(cr T-1 고아마다 임대 확인 → 반환 `OrphanIsolation(태운 수, leaseHeld)` · R3-M-1 배선이 판정 함수를 지나는지 **구조**로(단언 둘: 함수 facade 의존 있음 + 열거 상수 접근 없음 — 정책표 주입은 기동 거부의 입력을 호출자가 고르게 하므로 불채택) · R3-L-1/cr T-2 억제 환경 fake · T-4 대상 쪽 접기 · T-5 사전순 · T-3 KDoc) · evidence **`dad5b392`**(= 판정 SHA). `check` exit 0 두 HEAD. 변이 셋 RED(T1 2/5 · B3 1/1 · P1s 4/4 — B3·P1s 는 verifier r3 가 초록을 실측한 그 변이). rollback 실측 HEAD `2bc08209`(69 · D40/M29 · check 0 · 유효성 빈 출력). **고치지 않은 것 둘 수용**: cr T-4 는 변이로 재지 않음(오늘 커밋 조립에 중첩 타입이 없어 사각 도달 불가, 모양 통일만) · `gate-tests.properties` 의 slice 밖 정렬 어긋남 여덟은 「추가만」 한정으로 남김 — 근본 처방(게이트에 「블록 안 사전순」 단언, `build-logic/`)은 **하네스 slice 후보 `OPEN-HARNESS-GATE-TESTS-ORDER`**. 새 파일 0. 재작업 **2/5** | 레인 보고 「승인 전 일괄」 |
| **D-6F10-38** | **verifier 표적 재검증(판정 SHA `dad5b392`, 산출물 `2bc08209` 한 커밋)**: ① 변이 셋 재생(T1 고아마다 확인 제거 · B3 배선만 옛 술어 · P1s 확인·억제 순서 교환 — 각각 어느 test 가 RED) + 구조 단언 둘의 공허 여부(함수를 부르되 결과를 버리는 변이) ② `check --rerun-tasks` · `qualityBaseline` · S-20 · 컨테이너 재생(production workflow 변경이라 전부; 호스트 조건 미달이면 컨테이너는 사유와 함께 보류 보고) ③ 장부: rollback 유효성 `2bc08209..dad5b392` · R3-L-2~4·T-6·T-7 종결 · 제한 표 복구 · clean-tree. 통과 시 **종결** — 종결 문단(팀장) → push → PR → `/code-review` → 머지는 사용자 결정 | D-6F10-37 |
| **D-6F10-39** | **표적 재검증 수령 @`dad5b392` — verifier `ready-for-review`**(`10_verifier_targeted.md`: acceptance 넷 exit 0(컨테이너 S-21a~S-25, S-23c·S-24 포함) · rollback 유효(`2bc08209..dad5b392` 빈 출력, ⓪~⑥ 0) · 변이 T1·B3·P1s 전부 RED · r3·cr r3 finding 전부 종결(R3-M-1 은 문자 그대로의 되돌림에 대해). 새 finding 셋: **RT-M-1** 배선 구조 단언이 바이트코드 모양 하나(`GETSTATIC`)에 걸려 `when`·`.name` 비교 모양은 통과 · RT-L-1 `OrphanIsolation.leaseHeld` 미측정(동치 변이) · RT-L-2 rollback.md 「파일 넷·커밋 넷」 셈 오류). **종결 판정**: 산출물 blocker/high 0, medium 은 등재 — RT-M-1 은 **알려진 제한 18 + `OPEN-6F10-RELAY-BOOT-WIRING-LOCK`**(거동 잠금은 정책표 주입이 필요하고 그것은 기동 거부의 입력을 호출자에게 여는 자리; `OPEN-STR-12` 가 relay 배선을 다시 짤 때 닫는다) · RT-L-1 제한 19 · RT-L-2 문장을 「수는 산문에 적지 않는다」로(팀장 evidence 커밋). 새 일괄 라운드는 돌리지 않는다(라운드 셋·일괄 하나·표적 재검증 하나 뒤의 medium 하나는 반복이 아니라 등재가 맞다). **재작업 2/5 에서 종결.** 다음: 종결 문단(milestone) → push → PR → `/code-review` → 조치 → 머지는 **사용자 결정** | 표적 재검증 |
| **D-6F10-40** | **PR #63 `/code-review` 수령(finding 8) 과 조치 처분** — ① **일회 러너 상호 배제 없음**(relay·평가 커밋 `mode=once` 둘을 한 프로세스에 켜면 먼저 끝난 러너의 `terminate` 가 JVM 을 끝내 다른 러너는 조용히 안 돈다; 수집 레인과 섞어도 같음) → **app 배선에 기동 guard**: `ApplicationRunner` 빈이 둘 이상이면 기동 실패(수집 레인 코드 무변경, 세는 쪽만) + test(둘 켜면 RED) ② **`completedExitCodeOf` 가 `orphansIsolated` 를 보지 않음**(고아 50 격리 = 알림 50 영구 손실인데 exit 0) → `orphansIsolated > 0` 도 `INCOMPLETE`(2) — D-6F10-18 ⑧·27 ⑦ 정정, `RelayExitCodeTest` 의 `orphansIsolated=1 → 0` 핀 반전 ③ **`stillHolding` 이 `SELECT 1`(연결 생존)만 재고 잠금 자체를 보지 않음**(transaction-pooler 뒤에서는 연결이 살아도 backend 가 바뀌어 둘이 Held) → 잠금 인식 probe(`pg_locks` 에서 자기 `pg_backend_pid()` 의 advisory objid 존재) + **알려진 제한 20**: statement/transaction 모드 pooler 는 지원하지 않는다(지금 배포는 직접 연결) ④ **`settleRow` 중간 예외 시 누적 disposition 소실**(12 전달·7 좌초가 로그에 없음) → 부분 집계를 실어 재던짐(`LeaseLost` 의 `partial` 과 같은 모양), 러너가 실패 줄에 집계 기록 + test ⑤ `EvaluationCommitWiring.evaluationCommitRun` 의 미사용 `evaluationProperties` 파라미터 제거 ⑥ `causeCodeOf` 사본 넷 — **이 slice 의 둘만** 하나로(app.relay 또는 wiring 공용 `internal`), 수집 쪽 둘은 in_scope 밖(`app/collection/**`) → **`OPEN-6F10-CAUSE-CODE-DEDUP`**(장부 쌍도 하나 줄어듦) ⑦ `relayHoldingLease` 의 중복 `stillHeld()`(획득 직후 두 번 연속) 하나 제거 — 지점 수 문면(「다섯」) 사실화 ⑧ test `RelayDbSupport.stateOf` → `outboxStateOf` 재사용, SQL 사본 제거. **①②③④⑦ 술어·거동 변경 → verifier 표적 재검증 1회(조치 커밋만)**. 산출물 한 커밋 + evidence 한 커밋(제한 20 · OPEN · rollback 재실측) | `/code-review 63` |
| **D-6F10-41** | **PR #63 조치 라운드 수령·동결 (2026-10-07)** — 산출물 **`0dae7a36`**(finding 1~8: 러너 상호 배제 `OneShotRunnerGuard`(`BeanFactoryPostProcessor` 로 빈 **정의**를 셈 — 인스턴스로 세면 생성 효과가 실패 전에 일어남) · 고아 격리도 INCOMPLETE · 잠금 인식 probe · 부분 집계 재던짐 · 미사용 파라미터 · `causeCodeOf` 둘 합침 · 중복 확인 제거 · test SQL 사본 제거) · 공유 장부 **`3db918ab`** · evidence **`eb06749e`**(= 판정 SHA). `check` exit 0 세 HEAD(workflow 51/425 · adapters 134/887 · app 62/560). 변이 다섯 중 넷 RED, **F3(잠금 probe → `SELECT 1` 되돌리기)은 초록 — 도달 불가 사각**(직접 연결에서는 두 probe 가 같은 답; pooler 미지원이 제한 20) — 수용. **base 전진 사실**: main 병합으로 merge-base 가 `b137c670` → **`2865c9b8`** — 계약 머리 「base = `git merge-base HEAD origin/main`(고정 SHA 아님)」 정의대로, rollback 복원 목록·셈·공유 파일 커밋 열을 새 base 로 재산출(73 · D44/M29 · 되돌린 트리 check 0 · 유효성 빈 출력). 새 파일 넷 전부 in_scope 안. 게이트가 라운드 중 여섯 번 잡음. 재작업 2/5 유지 | 레인 보고 「PR #63 조치」 |
| **D-6F10-42** | **표적 재검증(판정 SHA `eb06749e`, 산출물 `0dae7a36`·장부 `3db918ab`)** — vr-6f10-t 재사용: ① 변이 F1(guard 셈)·F2(고아 갈래)·F4(집계 미적재)·F5(중복 확인 되돌리기) RED 재생 + F3 초록의 사유 확인(도달 불가) + guard 가 **수집 레인 + relay** 조합도 막는지(빈 정의 둘) ② acceptance 넷(`check --rerun-tasks` · `qualityBaseline` · S-20 · 컨테이너 재생) ③ 장부: rollback 유효성 `0dae7a36..eb06749e` · 새 base `2865c9b8` 기준 복원 목록 재실행 ⓪~⑥ · 새 파일 ↔ in_scope · clean-tree · 제한 20·OPEN 절. 통과 시 조치 코멘트 → CI → 머지 결정 요청 | D-6F10-41 |
| **D-6F10-43** | **표적 재검증 2 수령 @`eb06749e` — verifier `ready-for-review`**(`11_verifier_targeted2.md`: acceptance 넷 exit 0 · rollback @`3db918ab` ⓪~⑥ 0 · finding 1·2·5·6·7·8 닫힘 · F1·F2·F4b·F5 RED · guard 가 relay + 실 수집 배선 셋 각각의 조합을 기동 거부). 새 finding: **RT2-M-1** 잠금 인식 probe 가 상설 suite 에서 미측정(F3 `SELECT 1` 되돌림·F3b 자기 backend 조건 제거 모두 초록 — 「도달 불가」는 production 직접 연결에만 참; verifier 가 직접 SQL 로 의미를 확인하고 약 40줄 pooler 흉내 `DataSource` 프록시 test 가 둘을 RED 로 만듦) · RT2-L-1 러너가 부분 집계를 실패 줄로 넘기는 이음 미측정(F4a 초록 — R3-M-1 과 같은 「함수는 RED, 호출 자리는 초록」 모양) · RT2-L-2 production 컨텍스트가 guard 를 싣는지 미측정(`@Configuration` 제거 F6 초록) · RT2-L-3 장부(제한 18·19 번호 중복 · wiring-lock OPEN 이 신설 OPEN 표에 없음 · 제한 19 「셋」/「넷」 · milestone 종결 문단 「다섯 지점」). **D-42 정정**: rollback 유효성 시작점은 `0dae7a36` 이 아니라 **장부 커밋 `3db918ab`**(장부 커밋이 공유 config 둘을 바꾸므로) — rollback.md 가 이미 그렇게 쓴다 | 표적 재검증 2 |
| **D-6F10-44** | **마무리 일괄(test·장부 전용 — production 무변경, 컨테이너 재생 불필요)** — 레인 **test 한 커밋**: ① RT2-M-1 pooler 흉내 `DataSource` 프록시 test(잠금 질의는 연결 A, 생존 probe 는 연결 B → 새 probe 는 상실로 판정, `SELECT 1`·자기 backend 조건 제거 변이 둘 RED) ② RT2-L-1 `NotificationRelayRunner` 실패 분기가 `relayFailureLine(causeCodeOf, partialOf)` 를 잇는지(이음 제거 변이 RED) ③ RT2-L-2 production 컨텍스트 부팅 test 에 guard 빈 존재 단언(`@Configuration` 제거 변이 RED). **evidence 한 커밋**: 제한 18·19 번호 중복 해소(RT 라운드 18·19 → 그대로, 이번 둘을 20·21 로 — 제한 20 pooler 는 21 로 밀림 등 재번호 뒤 OPEN 인용 갱신) · `OPEN-6F10-RELAY-BOOT-WIRING-LOCK` 을 신설 OPEN 표에 · 사본 수 문면 통일 · commands.md 변이 셋 · **rollback ⓪~⑥ 재실측**(새 test 파일이 복원 목록에 들어가므로, 실측 HEAD 갱신). **팀장**: milestone 종결 문단 「다섯 지점」 → 「네 지점」(finding 7). 검증: vr-6f10-t **표적 재검증 3 — `check --rerun-tasks` + 변이 셋 + 장부만**(production 무변경이라 qualityBaseline·S-20·컨테이너는 CI 가 본다). 통과 → 조치 코멘트 → 머지 결정 요청 | D-6F10-43 |
| **D-6F10-45** | **마무리 일괄 수령·동결 (2026-10-07)** — test **`623fb423`**(pooler 흉내 probe `PoolerLeaseProbeTest` · 러너 이음은 커밋 E2E 에 붙임(`NotificationRelayRun` 이 `open` 아님 + 디코드 가능한 행은 production 경로로만 — R3-L-4 「커밋 run 이 쓴 행을 relay 로 한 바퀴」도 닫힘) · guard 빈 존재 단언을 `ProductionAssemblyAuthAuditTest` 에) · 장부 **`d949da9d`** · evidence **`ee756cc2`**(= 판정 SHA). **production 소스 diff 0**(`5ae419fb..HEAD`). `check` exit 0 세 HEAD(workflow 51/425 · adapters 135/889 · app 62/562). 변이 넷 전부 RED(F3·F3b·F4a·F6 — 앞 라운드 초록이던 넷). 제한 재번호(이번 셋 20·21·22, pooler 는 22 — 「pooler test 가 pooler 지원을 뜻하지 않는다」 유지) · OPEN 표 보완. rollback 실측 HEAD `d949da9d`(76 · D46/M30 · check 0) + 「목록 ⊇ main 소스 변경」 확인을 ③c 로 상설화. 새 파일 둘 in_scope 안 | 레인 보고 「마무리 일괄」 |
| **D-6F10-46** | **표적 재검증 3(판정 SHA `ee756cc2`, test `623fb423` · 장부 `d949da9d`)** — vr-6f10-t: ① `check --rerun-tasks` ② 변이 F3·F3b·F4a·F6 RED 재생 + 새 test 셋의 공허 여부(pooler 프록시가 실제로 질의를 두 연결로 가르는가 · 커밋 E2E 의 러너 이음 단언이 실패 줄의 **부분 집계 값**을 보는가 · guard 빈 단언이 production 컨텍스트에서 도는가) ③ 장부: rollback 유효성 `d949da9d..ee756cc2` 빈 출력 · ⓪~⑥ 재실행 · 제한 번호·OPEN 표 · clean-tree · 새 파일 ↔ in_scope. production 무변경이라 qualityBaseline·S-20·컨테이너는 CI 로 갈음(D-44). 통과 → 조치 코멘트 → **머지 결정 요청** | D-6F10-45 |
| **D-6F10-47** | **표적 재검증 3 수령 @`ee756cc2` — verifier `ready-for-review`**(`12_verifier_targeted3.md`: `check --rerun-tasks` exit 0 · production diff 0 · 변이 F3·F3b·F4a·F6 각각 새 test 하나가 RED · 새 test 셋 공허 아님(프록시가 질의를 두 연결로 가름 · E2E 가 claimed=1·delivered=0 값 단언 · guard 단언이 부팅된 컨텍스트에서) · RT2 finding 전부 종결 · rollback ⓪~③c @`d949da9d` 일치, 유효성 빈 출력). **공백**: rollback ④~⑥ 미실행 — swap free 1.85GB 로 시작 규칙(2GB) 아래 10분 지속; 되돌린 코드 트리는 r2 와 동일하나 milestone·evidence 다섯이 달라 트리 동일성 갈음 불가(evidence 가 `leakPatternGate` 입력). 새 finding **RT3-L-1**(장부): 재번호 뒤 낡은 KDoc 번호 둘 — `PostgresAdvisoryLockLease.kt` 「제한 20」·`PoolerLeaseProbeTest` 「21」 → 22(앞은 production 주석). **운영자 결정(2026-10-07)**: swap free 2GB 아래라도 available ≥ 6GB · swap free ≥ 1GB 면 Gradle 하나씩 허용(1GB 아래면 즉시 중단·보고). 처분: 레인 **소 커밋**(KDoc 번호 둘, 주석만) + evidence(알려진 제한 두 줄 — 프록시 `InvocationTargetException` 미해제로 probe 예외 분기 미측정 · guard 메시지 러너 이름 미단언 · rollback ⓪~③c 재산출) → verifier **표적 4**(새 HEAD 에서 `check --rerun-tasks` + rollback ⓪~⑥ + KDoc 번호 대조) → 종결 | 표적 재검증 3 · 운영자 |
| **D-6F10-48** | **소 커밋 수령·동결** — 산출물 **`d43128a9`**(KDoc 둘 20·21 → 22, 주석만 3줄) · evidence **`ac50f39d`**(제한 23·24 — 프록시 `InvocationTargetException` 미해제·guard 메시지 러너 이름 미단언 · rollback ⓪~③c 재산출 @`d43128a9`(76 · D46/M30 · ③c 0) · 호스트 문턱 변경 기록). `check` exit 0 두 번(swap 2.05GB 유지). 역방향 파급: `ManagementSurface*` 의 「checklist 제한 18」 인용은 다른 slice 몫(in_scope 밖·무관 커밋) — 사실만. 교훈: 점검표 번호를 코드가 인용하는 동안 재번호와 코드 전수 대조는 짝. **표적 4**(vr-6f10-t): `check --rerun-tasks` · rollback ⓪~⑥ @`d43128a9` · KDoc 22 대조 · 유효성 `d43128a9..ac50f39d` → 통과 시 조치 코멘트·머지 결정 요청 | 레인 보고 「소 커밋」 |
| **D-6F10-23** | **새 파일 ↔ in_scope 대조(구현 중 레인 요청)**: `app/src/test/kotlin/bidvector/archfixture/violating/app/RogueDryRunCommitReferencer.kt` — D-6F10-15 새 ArchUnit 규칙의 음성 대조 fixture. 위반 fixture 는 이 저장소에서 `bidvector.archfixture.violating` 한 자리에 모이고(`ArchitectureGateCatchesViolationsTest` 가 그 루트를 import), 커밋 조립 타입을 일부러 참조하므로 다른 패키지면 다른 게이트의 판정 대상이 된다 → in_scope 에 `app/src/test/kotlin/bidvector/archfixture/violating/**` 추가(6F-9 in_scope 와 같은 줄) | 레인 요청 · 「수정 라운드가 만드는 새 파일은 in_scope 와 대조」 |
