# checklist.md — M4/4A 리뷰 요청 조건 점검

> **수정 라운드 1(2026-09-08) 반영.** verifier 1차가 산출물층 high 2건(H-1·H-2)으로 not-ready
> 판정했다(`_workspace/m4-4a/03_verifier_report.md`). 이 문서는 그 리포트가 지적한 거짓 근거
> 문면(§「이 slice 고유 확인」 우회 (3) 행)을 정정하고, 함께 지시된 N-1·M-1·M-2·M-3 도 반영한다.

- [x] 구현 diff 가 커밋되어 base/head 고정 — base `9948c6e4056bbf71fa6683aa67d30c2a49fc6eae`,
      head `022629b`(수정 라운드 1의 마지막 코드 커밋 — 이 evidence 커밋 자신은 head 밖).
      `git status --porcelain -- <in_scope 경로 34개>` 결과 없음(commands.md 「clean-tree 게이트」,
      양성 대조 포함).
- [x] scope.md 의 acceptance_commands 전부 exit 0 — S-0~S-6 재실행 결과는 commands.md 「수정
      라운드 1」 절에 기록(S-0 은 임시 clone 재확인).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`clean check`) 이 전 모듈의
      test·ktlint·detekt·moduleDependencyGate·sizeGate·cpdCheck·gateExecutionGate·
      qualityBaseline·contractGate 를 포함해 통과. **verifier L-1(정보성)** — S-6(`:app:gateExecutionGate`)
      은 `gate.tests.app` 만 재고 `gate.tests.workflow` 는 재지 않는다. 그 래칫을 강제하는 것은
      `:workflow:gateExecutionGate`(S-1 `check` 안에 포함, 매 acceptance 재실행마다 함께 돈다)다 —
      verifier 가 `--tests` 로 일부 class 를 뺀 뒤 `:workflow:gateExecutionGate` 만 단독 실행해도
      exit 1(미실행 class 이름 지목)로 실효를 확인했다. scope.md 의 S-6 문면은 정정하지 않는다 —
      S-1 이 이미 `:workflow:gateExecutionGate` 를 포함해 돌리므로 누락이 아니라 서술의 정밀도
      문제였다.
- [x] 변경된 fixture 와 정책 version 의 근거 기록 — golden-manifest.json(5 case, sha256 실측
      대조, 수정 라운드 1 에서 fixture 무변경) · fixtures/manifest.yaml 각 case 의
      `source.kind: operator-decision`(M1/1E decision 30 재확인) · `EDIT_SESSION_POLICY`/
      `STRATEGY_POLICY` 는 값 미확정 placeholder 로 소스 주석에 「운영자 승인 대상」을 명시.
- [x] 알려진 제한과 rollback 방법이 기록됨 — milestone-4.md 4A 구현 기록 문단(수정 라운드 1
      반영) · rollback.md(임시 clone 실측 5단계 재실행 — 목록 32개로 갱신, exit 0·D28/M6·
      diff 빈 값·compile·test).
- [x] secret 스캔 통과 — commands.md 「secret 스캔」, 자기참조 오탐을 판독해 실제 매치 0건.
      Telegram id·사업자 정보 없음(육안 확인).

## 이 slice 고유 확인 — H-1/H-2 정정

**1차 checklist 의 우회 (3) 행이 거짓이었다.** 「저장 인자 `OperatorStrategy` 를 내는 유일한
자리가 `TransitionOutcome.Applied`」는 `validate()`·`StrategyValidation.Valid.strategy` 가
둘 다 public 이라(1E D-10) 성립하지 않는다 — verifier 가 `app` test 에 `StrategyRepository`
를 구현하고 `validate()` 결과를 직접 `save()` 에 넘기는 클래스를 심어 `:app:compileTestKotlin`
exit 0 으로 실증했다(1차 checklist 자신의 test 넷도 같은 패턴 — `currentStrategy()` helper).

**수정 — `AppliedStrategy` 통로 타입.** `StrategyRepository.save` 의 인자를 `OperatorStrategy`
에서 `AppliedStrategy`(`@ConsistentCopyVisibility` + `internal constructor`)로 바꿨다. 이
타입은 `workflow` 모듈 밖에서 생성할 수 없다 — `validate()` 가 아무리 public 이어도 그 결과를
`AppliedStrategy` 로 감싸는 것 자체가 다른 모듈에서는 컴파일되지 않는다.

**실측 (a) — 우회가 이제 컴파일되지 않는다.** 임시 clone 에 verifier 가 심었던 것과 같은 클래스
(`RogueStrategyWriter` — `StrategyRepository` 구현 + `validate()` 결과를 `save()` 에 전달)를
심고 `:app:compileTestKotlin` 실행 → 다음 진단으로 실패:
```
e: .../BypassProbe.kt:54:15 Cannot access 'constructor(strategy: OperatorStrategy): AppliedStrategy':
   it is internal in 'bidvector.workflow.strategy.AppliedStrategy'.
```

**실측 (b) — 양성 대조, 정상 배선은 그대로 선다.** 같은 clone 에서 probe 파일을 지우고
`:app:compileTestKotlin :workflow:test :app:test --tests '*Conformance*'` 실행 →
`BUILD SUCCESSFUL`(34 actionable tasks). `EditStrategyWorkflow` 경유 경로는 영향 없음.

**`OPEN-4A-WRITE-PATH-GATE` — 종결.** scope.md 가 요구한 실측(ArchUnit/의존 게이트로 표현
가능한지)은 verifier 가 반증 실측(별도 ArchUnit 규칙이 HEAD 에서 통과·우회 클래스 심으면
FAILED)까지 냈다 — 「게이트로 표현 불가」는 거짓이었다. 다만 통로 타입이 **컴파일 층**에서
이미 닫아 그 게이트 자체가 불필요해졌으므로 도입하지 않는다. 「4A port 밖에서 자체
persistence 경로를 새로 만드는 것」(예: `workflow` 를 거치지 않고 새 모듈이 DB 에 직접 쓰는
것)은 이 slice 위협 모델 경계 밖이고 3D/4C 의 write 게이트 소관이다(위협 모델 경계 (0) 문단).

**갱신된 위협 모델 방어 (a)~(g) 대응표**(Phase 2.5 설계 검토 노트가 지목한 우회 경로 대응):

| 우회 | 방어 위치 | 게이트/test |
| --- | --- | --- |
| (1) `EditSession.copy`로 상태 직조 | `@ConsistentCopyVisibility` + `internal constructor` | 컴파일(모듈 밖 `copy` 불가) |
| (2) `Confirm`에 revision 없이 stale 확인 | `seenRevision: StrategyRevision` 필수 필드 | 컴파일 |
| (3) 어댑터가 `StrategyRepository.save` 직접 호출 | `AppliedStrategy`(`internal constructor`) — `workflow` 밖에서 생성 자체가 컴파일되지 않는다 | 컴파일(실측 (a)(b) 위) |
| (4) timeout 을 0/음수/무한으로 | `EditSessionPolicyData.init` — `require(양수)` + 상한 24h | `EditSessionPolicyDataTest`(전용 test, 변이 실측으로 회귀 보호 확인) |
| (5) 같은 commandId 로 다른 command | `duplicateOutcome` — fingerprint(전체 command 값) 불일치 시 `IdempotencyConflict` | `EditSessionIdempotencyPropertyTest`(판정 순서 판별 case 포함) |
| (6) `System` actor 로 `Confirm` | 전이표에 `System` 행 없음, `actorRejection` 이 즉시 거부 | `EditSessionActorAndTimeoutTest`(D-4A-5 test) — verifier 표적 4 실측: 4 command 전부 거부 |
| (7) 전이 함수가 Telegram 타입을 FQN 으로 참조 | allow-list 술어(`disallowedQualifiedReferences`) | `EditSessionImportBoundaryTest`(양성 대조 포함) — verifier 표적 3 실측: telegram 주석 + `procurement.NoticeId` 컴파일 코드 둘 다 잡힘 |
| (8, 신설) `begin()` 이 기존 비종단 세션을 가드 없이 덮어씀(verifier N-1) | `EditStrategyWorkflow.begin` 이 `sessions.load` 로 확인 후 `BeginOutcome.Rejected(SessionAlreadyActive)` | `EditStrategyWorkflowTest`(거부·허용 양쪽) |

- [x] **fixture 다섯의 case id 와 `review.approved_by_user: false` 사실** — `strategy-edit-001~005`,
      golden-manifest.json 에 전건 `review_approved_by_user: false`. 운영자 승인은 Phase 6.

## M-1·M-2·M-3 — 산출물층 미달이 아닌 medium 반영

- **M-1(판정 순서 판별 case 부재)**: `EditSessionIdempotencyPropertyTest` 에 accepted 로
  `lastCommand` 를 채운 뒤 만료 시각 이후 재전달하는 case 추가. 변이 실측(만료/중복 두 줄
  순서 교환) — 새 case 1건만 FAILED, 원상 복구 확인(commands.md).
- **M-2(정책 불변식 회귀 보호 0)**: `EditSessionPolicyDataTest` 신설(0·음수·상한 초과·상한
  경계 넷). 변이 실측(두 `require` 를 `require(true)` 로) — 관련 3건 FAILED, 원상 복구 확인.
- **M-3(저장 순서로 편집 영구 소실 가능)**: `Applied` 경로를
  `strategies.save → events.publish → sessions.save` 순으로 교정. 두 실패 주입 test로
  (a) 전략 저장 실패 → 세션 미전진 → 재전달이 정상 재시도(Applied, revision 2) (b) 발행
  실패(전략 저장은 성공) → 세션 미전진 → 재전달이 `StaleRevision` 거부 를 관측.

## 알려진 제한(수정 라운드 1 갱신)

- 이벤트 발행·세션 전진의 원자성 부재 — 저장 성공 뒤 발행 실패는 다음 재전달이
  `StaleRevision` 으로 정직하게 거부되나(M-3 수정으로 이중 적용은 막힘), 그 사이 세션이
  전진하지 않는 잔여 창은 남는다(4C 트랜잭션 outbox 가 닫는다)
- 편집 세션 영속의 실 구현 부재(port + fake 까지, 4C/3D) — `EditSessionRepository` 의
  `sessionVersion` 낙관적 동시성은 fake 가 단순 map 이라 충돌을 재지 않는다(경계 안 사실,
  verifier L-4)
- 만료 트리거(sweep) 배선 부재 — `expireIfDue`/`expire()` 는 순수 함수로 존재, 스케줄러 없음
- `System` actor 확인 경로 미구현(D-4A-5, STR-15 `후속`) — 네 command 전부 거부됨을 실측
- Telegram 어댑터 없음(`OPEN-STR-12` 활성 유지)
- S-3b 술어(`disallowedQualifiedReferences`)는 소문자 세그먼트 둘 미만인 단일 세그먼트
  패키지 참조(예: `telegram.Bot`)를 잡지 못한다(verifier L-2) — 실무 좌표(`org.telegram.…`)는
  잡히므로 실효는 크지 않다고 판단해 이번 라운드에서는 강화하지 않는다
- `OPEN-4A-WRITE-PATH-GATE`는 **종결**(위 「이 slice 고유 확인」 참고) — 게이트가 아니라
  `AppliedStrategy` 통로 타입의 컴파일 층 폐쇄로 닫혔다
