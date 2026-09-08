# checklist.md — M4/4A 리뷰 요청 조건 점검

> **수정 라운드 1(2026-09-08) 반영.** verifier 1차가 산출물층 high 2건(H-1·H-2)으로 not-ready
> 판정했다(`_workspace/m4-4a/03_verifier_report.md`).
>
> **수정 라운드 2(2026-09-09) 반영.** verifier 표적 재검증이 산출물층 high 1건(H-3)으로
> not-ready 판정했다(`_workspace/m4-4a/04_verifier_report_r2.md`) — 라운드 1 의 「우회 (3)
> 구조적 폐쇄」 결론이 반증됐다(`AppliedStrategy` 는 위조를 막았지만 **획득**은 막지
> 못했다). 이 문서는 그 새 근거로 §「이 slice 고유 확인」을 다시 쓰고, 함께 지시된
> M-4·M-5·L-5·L-6·L-7·B-5 도 반영한다.

- [x] 구현 diff 가 커밋되어 base/head 고정 — base `9948c6e4056bbf71fa6683aa67d30c2a49fc6eae`,
      head `2bda85f`(수정 라운드 2의 마지막 코드 커밋 — 이 evidence 커밋 자신은 head 밖).
      `git status --porcelain -- <in_scope 경로 34개>` 결과 없음(commands.md 「clean-tree 게이트」,
      양성 대조 포함).
- [x] scope.md 의 acceptance_commands 전부 exit 0 — S-0~S-6 재실행 결과는 commands.md 「수정
      라운드 2」 절에 기록(S-0 은 임시 clone 재확인).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`clean check`) 이 전 모듈의
      test·ktlint·detekt·moduleDependencyGate·sizeGate·cpdCheck·gateExecutionGate·
      qualityBaseline·contractGate 를 포함해 통과. **verifier L-1(정보성, 라운드 1)** — S-6은
      `gate.tests.app` 만 재고 `gate.tests.workflow` 는 S-1 의 `check` 안 `:workflow:gateExecutionGate`
      가 진다(그 래칫의 실효는 라운드 1 에서 실측 확인).
- [x] 변경된 fixture 와 정책 version 의 근거 기록 — golden-manifest.json(5 case, sha256 실측
      대조) · fixtures/manifest.yaml 각 case 의 `source.kind: operator-decision`(M1/1E decision 30
      재확인) · **fixture input/expected 는 수정 라운드 1·2 전부에서 무변경**(`git log --
      fixtures/{input,expected}/strategy-edit-*.json` 전 파일이 최초 등재 커밋 `6101dd2` 하나뿐,
      round 2 의 corpus 실행자 재작성은 관측 방식만 바꿨다) · `EDIT_SESSION_POLICY`/
      `STRATEGY_POLICY` 는 값 미확정 placeholder 로 「운영자 승인 대상」을 명시.
- [x] 알려진 제한과 rollback 방법이 기록됨 — milestone-4.md 4A 구현 기록 문단(수정 라운드 2
      반영) · rollback.md(임시 clone 실측 5단계 재실행, 목록 34개 불변 — head 만 갱신).
- [x] secret 스캔 통과 — commands.md 「secret 스캔」, 자기참조 오탐(commands.md·checklist.md
      자신의 절 제목 문구)을 판독해 실제 매치 0건. Telegram id·사업자 정보 없음(육안 확인).

## 이 slice 고유 확인 — H-3 정정(우회 (3) 재폐쇄)

**라운드 1 의 결론이 반증됐다.** 「`AppliedStrategy`(internal constructor)가 `workflow` 밖
생성을 막아 우회 (3)이 닫혔다」는 **생성(위조)만 막았지 획득을 막지 못했다** — `beginSession`·
`apply` 가 public top-level 함수였기 때문에, `workflow` 밖에서 그 둘을 직접 몰아 호출부가
고른 `current`·`draft`로 **정당한** `AppliedStrategy`를 얻고, `EditStrategyWorkflow`를 거치지
않고 `StrategyRepository.save`에 직접 넘길 수 있었다(verifier 실측: `revision=778`·
`bidNowThreshold=0.99` 저장 + `StrategyUpdated` 발행 **0** — STR-07 이 `폐기`로 못 박은
「저장은 하는데 이벤트를 안 낳는 write 경로」 그 자체 재현). 같은 뿌리로 `beginSession` 결과를
세션 port 에 직접 넘기면 활성 세션도 덮어썼다(M-4).

**수정 — 커널 자체를 `internal` 로.** `beginSession`·`apply`·`expireIfDue` 셋을 `internal`로
내렸다. `EditSession`도 `TransitionOutcome`도 `workflow` 밖에서는 **얻는 경로 자체가 없다** —
`AppliedStrategy`(H-1/H-2, 위조 방지)와 이 커널 폐쇄(H-3, 획득 방지)가 함께 서야 우회 (3)이
닫힌다. app 의 corpus 실행자는 이제 `EditStrategyWorkflow` + fake port 넷을 조립해
`begin`→`provideValue`→`confirm`을 실제로 모는 형태로 재작성했다(fixture 입력/기대값 무변경).

**실측 (a) — 커널 직몰이 우회가 이제 컴파일 거부된다.**
```
e: .../H3Probe.kt:25:36 Cannot access 'fun apply(...)': it is internal in file.
e: .../H3Probe.kt:26:36 Cannot access 'fun beginSession(...)': it is internal in file.
```

**실측 (b) — M-4(세션 축 옆문)도 같은 폐쇄로 거부된다.**
```
e: .../M4Probe.kt:10:36 Cannot access 'fun beginSession(...)': it is internal in file.
```

**실측 (c) — 양성 대조, 정상 배선과 corpus 다섯은 그대로 선다.** 같은 clone 에서 probe 를
지우고 `:app:compileTestKotlin :workflow:test :app:test --tests '*Conformance*'` →
`BUILD SUCCESSFUL`(34 actionable tasks).

**`OPEN-4A-WRITE-PATH-GATE` — 종결(새 근거로 재작성).** **닫힌 것**: 「이 slice 의 두 port
(`StrategyRepository`·`EditSessionRepository`)를 우회해 커널을 직접 몰아 쓰는 경로」— 커널
`internal` 화로 컴파일 층에서 폐쇄, verifier 의 ArchUnit 반증 실측(그런 게이트가 표현
가능하다)은 유효하지만 컴파일 폐쇄가 강도상 더 세므로 별도 게이트를 도입하지 않는다.
**경계 밖(닫지 않는 것)**: 「4A port 를 아예 거치지 않고 다른 모듈이 자체 persistence
(예: 별도 JDBC 커넥션으로 전략 테이블에 직접 INSERT)를 새로 만드는 것」 — 이것은 `workflow`
의 port 추상화 자체를 우회하는 것이라 4A 코드 어떤 폐쇄로도 막을 수 없고, 3D/4C 의 write
게이트(실 스키마·권한 계층)가 갖는 책임이다(scope.md 위협 모델 경계 (0)).

**갱신된 위협 모델 방어 (a)~(g) 대응표**(Phase 2.5 설계 검토 노트가 지목한 우회 경로 대응):

| 우회 | 방어 위치 | 게이트/test |
| --- | --- | --- |
| (1) `EditSession.copy`로 상태 직조 | `@ConsistentCopyVisibility` + `internal constructor` | 컴파일(모듈 밖 `copy` 불가) |
| (2) `Confirm`에 revision 없이 stale 확인 | `seenRevision: StrategyRevision` 필수 필드 | 컴파일 |
| (3) 어댑터가 `StrategyRepository.save` 직접 호출 | `AppliedStrategy`(위조 방지) **+** `beginSession`·`apply`(`internal`, 획득 방지) — 둘이 함께 폐쇄 | 컴파일(실측 (a)(b)(c) 위) |
| (4) timeout 을 0/음수/무한으로 | `EditSessionPolicyData.init` — `require(양수)` + 상한 24h | `EditSessionPolicyDataTest`(전용 test, 변이 실측) |
| (5) 같은 commandId 로 다른 command | `duplicateOutcome` — fingerprint(전체 command 값) 불일치 시 `IdempotencyConflict` | `EditSessionIdempotencyPropertyTest`(판정 순서 판별 case 포함) |
| (6) `System` actor 로 `Confirm` | 전이표에 `System` 행 없음, `actorRejection` 이 즉시 거부 | `EditSessionActorAndTimeoutTest`(D-4A-5 test) |
| (7) 전이 함수가 Telegram 타입을 FQN 으로 참조 | allow-list 술어(`disallowedQualifiedReferences`, L-7 로 소문자 단일 세그먼트까지 확장) | `EditSessionImportBoundaryTest`(양성/음성 대조 포함) |
| (8) `begin()` 이 기존 비종단 세션을 가드 없이 덮어씀(N-1) | `sessions.load` 확인 후 `BeginOutcome.Rejected(SessionAlreadyActive)` | `EditStrategyWorkflowTest` |
| (9, 신설) `begin()` 커널이 `workflow` 밖에서 세션을 직접 생성·저장(M-4) | `beginSession` `internal` — 호출 자체가 컴파일되지 않음 | 컴파일(실측 (b) 위) |
| (10, 신설) 만료된 세션이 fold 안 돼 `begin()` 을 무기한 막음(M-5) | `begin()` 가드 전에 `expireIfDue` 적용 | `EditStrategyWorkflowTest`(재현 test) |

- [x] **fixture 다섯의 case id 와 `review.approved_by_user: false` 사실** — `strategy-edit-001~005`,
      golden-manifest.json 에 전건 `review_approved_by_user: false`. 운영자 승인은 Phase 6.
      **입력/기대값은 수정 라운드 2 에서도 무변경**(위 체크리스트 항목 참고).

## M-1·M-2·M-3(라운드 1) + M-5·L-5·L-6·L-7(라운드 2) — 산출물층 미달이 아닌 medium/low 반영

- **M-1(판정 순서 판별 case 부재)**: 변이 실측(만료/중복 두 줄 순서 교환) — 새 case 1건만 FAILED.
- **M-2(정책 불변식 회귀 보호 0)**: `EditSessionPolicyDataTest` 신설. 변이 실측 — 관련 3건 FAILED.
- **M-3(저장 순서로 편집 영구 소실 가능)**: `strategies→events→sessions` 순으로 교정, failure-injection test 2건.
- **M-5(이번 라운드가 새로 연 것)**: `begin()` 가드가 저장된 state 만 봐서 시각상 만료됐지만
  fold 되지 않은 세션을 비종단으로 읽어 새 편집을 막을 수 있었다(sweep 부재와 겹치면 무기한).
  가드 전에 `expireIfDue` 적용 + 접힌 세션 저장으로 교정, 재현 test 추가.
- **L-5(만료 fold 의 세션 저장이 `Rejected` 경로에서도 영속되는지 회귀 보호 0)**: 독립 test 신설.
- **L-6(`TransitionOutcome.Applied` 가 모듈 밖에서 생성·`copy` 가능)**: `@ConsistentCopyVisibility`
  + `internal constructor` 로 다른 통로 타입과 관례 정렬.
- **L-7(S-3b 사각이 서술보다 넓다)**: 술어를 소문자 세그먼트 하나 이상으로 넓혀
  `telegram.Bot`·`telegram_api.Bot` 을 포착(양성 대조 추가). 대문자 단일 세그먼트 루트
  (`Telegram.Bot`)는 이 패키지 자신의 정당한 sealed 하위 타입 접근(`EditSessionState.Applied`
  등, 실측 46건)까지 오탐하므로 강화하지 않고 음성 test 로 그 사각을 고정했다.

## 알려진 제한(수정 라운드 2 갱신)

- 이벤트 발행·세션 전진의 원자성 부재 — 저장 성공 뒤 발행 실패는 다음 재전달이
  `StaleRevision` 으로 정직하게 거부되나(M-3 수정으로 이중 적용은 막힘), 그 사이 세션이
  전진하지 않는 잔여 창은 남는다(4C 트랜잭션 outbox 가 닫는다)
- 편집 세션 영속의 실 구현 부재(port + fake 까지, 4C/3D) — `sessionVersion` 낙관적 동시성은
  fake 가 단순 map 이라 충돌을 재지 않는다(경계 안 사실, verifier L-4)
- 만료 트리거(sweep) 배선 부재 — `expireIfDue`/`expire()` 는 순수 함수로 존재, 스케줄러 없음
  (M-5 로 `begin()` 자체는 이 부재와 안전하게 공존하도록 고쳤다 — sweep 이 없어도 `begin()`
  이 스스로 fold 한다)
- `System` actor 확인 경로 미구현(D-4A-5, STR-15 `후속`)
- Telegram 어댑터 없음(`OPEN-STR-12` 활성 유지)
- S-3b 술어는 대문자로 시작하는 단일 세그먼트 루트(`Telegram.Bot`)를 여전히 잡지 못한다
  (L-7 로 소문자 단일 세그먼트는 이미 넓혔다) — 강화하면 이 패키지 자신의 정당한 sealed
  하위 타입 접근(실측 46건)까지 오탐한다
- `OPEN-4A-WRITE-PATH-GATE`는 **종결**(위 「이 slice 고유 확인」 참고, 라운드 2 근거로 재작성) —
  4A port 우회 경로는 커널 `internal` 화로 컴파일 층에서 닫혔고, port 자체를 거치지 않는
  자체 persistence 는 명시적으로 경계 밖(3D/4C)이다
- 「4A port 밖에서 자체 persistence 경로를 새로 만드는 것」은 4A 코드로 막을 수 없다 —
  3D/4C 의 write 게이트(스키마·권한 계층) 소관으로 명시 인계
