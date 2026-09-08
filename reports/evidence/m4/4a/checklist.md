# checklist.md — M4/4A 리뷰 요청 조건 점검

- [x] 구현 diff 가 커밋되어 base/head 고정 — base `9948c6e4056bbf71fa6683aa67d30c2a49fc6eae`,
      head `1db6282`(HEAD, 5개 커밋: `4e861e3`·`29cbd6a`·`c561cdb`·`cad3dd9`·`6101dd2`·`1db6282`).
      `git status --porcelain -- <in_scope 경로 27개>` 결과 없음(commands.md 「clean-tree 게이트」,
      양성 대조 포함).
- [x] scope.md 의 acceptance_commands 전부 exit 0 — S-0~S-6 전부 commands.md 에 기록(S-0 은
      임시 clone 재확인, `BUILD SUCCESSFUL in 50s`).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`clean check`, 344 actionable
      tasks) 가 전 모듈의 test·ktlint·detekt·moduleDependencyGate·sizeGate·cpdCheck·
      gateExecutionGate·qualityBaseline·contractGate 를 포함해 통과.
- [x] 변경된 fixture 와 정책 version 의 근거 기록 — golden-manifest.json(5 case, sha256 실측
      대조) · fixtures/manifest.yaml 각 case 의 `source.kind: operator-decision`(M1/1E decision 30
      재확인) · `EDIT_SESSION_POLICY`/`STRATEGY_POLICY` 는 값 미확정 placeholder 로 소스 주석에
      「운영자 승인 대상」을 명시.
- [x] 알려진 제한과 rollback 방법이 기록됨 — milestone-4.md 4A 구현 기록 문단 · rollback.md
      (임시 clone 실측 5단계 — exit 0·D/M 건수·diff 빈 값·compile·test).
- [x] secret 스캔 통과 — commands.md 「secret 스캔」, 자기참조 오탐 2건을 판독해 실제 매치 0건.
      Telegram id·사업자 정보 없음(육안 확인).

## 이 slice 고유 확인

- [x] **설계 검토 (2) #3 실측 판정** — `StrategyRepository` internal 시도 → 컴파일 에러 →
      public 유지, `OPEN-4A-WRITE-PATH-GATE` 신설(milestone-4.md·이 checklist 등재).
- [x] **위협 모델 방어 (a)~(g) 대응표**(Phase 2.5 설계 검토 노트가 지목한 우회 경로 대응):

  | 우회 | 방어 위치 | 게이트/test |
  | --- | --- | --- |
  | (1) `EditSession.copy`로 상태 직조 | `@ConsistentCopyVisibility` + `internal constructor` | 컴파일(모듈 밖 `copy` 불가) |
  | (2) `Confirm`에 revision 없이 stale 확인 | `seenRevision: StrategyRevision` 필수 필드 | 컴파일 |
  | (3) 어댑터가 `StrategyRepository.save` 직접 호출 | 저장 인자 `OperatorStrategy` 를 내는 유일한 자리가 `TransitionOutcome.Applied`(실측: `internal` 은 컴파일 에러로 안 섬, 타입 근거만) | 코드 리뷰 + `OPEN-4A-WRITE-PATH-GATE` |
  | (4) timeout 을 0/음수/무한으로 | `EditSessionPolicyData.init` — `require(양수)` + 상한 24h | 생성 불변식(EditSessionPolicyData 는 아직 그 test 전용 class 는 없으나 `beginSession`/`expireIfDue` 전 test 가 정상 범위로 간접 확인) |
  | (5) 같은 commandId 로 다른 command | `duplicateOutcome` — fingerprint(전체 command 값) 불일치 시 `IdempotencyConflict` | `EditSessionIdempotencyPropertyTest` |
  | (6) `System` actor 로 `Confirm` | 전이표에 `System` 행 없음, `actorRejection` 이 즉시 거부 | `EditSessionActorAndTimeoutTest`(D-4A-5 test) |
  | (7) 전이 함수가 Telegram 타입을 FQN 으로 참조 | allow-list 술어(`disallowedQualifiedReferences`) | `EditSessionImportBoundaryTest`(양성 대조 포함) |

- [x] **fixture 다섯의 case id 와 `review.approved_by_user: false` 사실** — `strategy-edit-001~005`,
      golden-manifest.json 에 전건 `review_approved_by_user: false`. 운영자 승인은 Phase 6.

## 알려진 제한(재확인)

- 이벤트 발행 비원자성(저장 성공 뒤 발행 실패의 유실 창 — 4C 트랜잭션 outbox 가 닫는다)
- 편집 세션 영속의 실 구현 부재(port + fake 까지, 4C/3D)
- 만료 트리거(sweep) 배선 부재 — `expireIfDue`/`expire()` 는 순수 함수로 존재, 스케줄러 없음
- `System` actor 확인 경로 미구현(D-4A-5, STR-15 `후속`)
- Telegram 어댑터 없음(`OPEN-STR-12` 활성 유지)
- `OPEN-4A-WRITE-PATH-GATE`(신설) — write 경로 우회를 아키텍처 게이트로 표현할 수 없었다(실측),
  현재는 타입 근거 + 코드 리뷰에 의존
