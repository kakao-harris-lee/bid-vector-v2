# checklist.md — M4/4A 리뷰 요청 조건 점검

> **수정 라운드 1(2026-09-08) 반영.** verifier 1차가 산출물층 high 2건(H-1·H-2)으로 not-ready
> 판정했다(`_workspace/m4-4a/03_verifier_report.md`).
>
> **수정 라운드 2(2026-09-09) 반영.** verifier 표적 재검증이 산출물층 high 1건(H-3)으로
> not-ready 판정했다(`_workspace/m4-4a/04_verifier_report_r2.md`) — 라운드 1 의 「우회 (3)
> 구조적 폐쇄」 결론이 반증됐다(`AppliedStrategy` 는 위조를 막았지만 **획득**은 막지
> 못했다). 이 문서는 그 새 근거로 §「이 slice 고유 확인」을 다시 쓰고, 함께 지시된
> M-4·M-5·L-5·L-6·L-7·B-5 도 반영한다.
>
> **장부층 일괄(2026-09-09) 반영.** verifier r3 판정은 **ready-for-review**(산출물층
> blocker/high 0건) — 새 라운드가 아니라 운영자 채택(2026-09-02, 장부층·산출물 low 는
> 등재 후 한 커밋 일괄)에 따라 L-8·L-9·L-10·B-6·B-7 을 이 한 커밋으로 처리한다
> (`_workspace/m4-4a/05_verifier_report_r3.md`).

- [x] 구현 diff 가 커밋되어 base/head 고정 — base `9948c6e4056bbf71fa6683aa67d30c2a49fc6eae`,
      head = 이 장부층 일괄 커밋 자신(`L-8` 코드 변경을 포함하므로 evidence 전용 커밋이
      아니다 — SHA 는 `git log -1 --oneline` 로 확인).
      `git status --porcelain -- <in_scope 경로 34개>` 결과 없음(commands.md 「clean-tree 게이트」,
      양성 대조 포함).
- [x] scope.md 의 acceptance_commands 전부 exit 0 — `:workflow:test`·`:app:test --tests
      '*Conformance*'`·`qualityBaseline`·`clean check`(L-8 로 코드가 바뀌므로 1회) 는
      commands.md 「장부층 일괄」 절에 기록. **S-0 전건 재실행은 이번엔 생략** — 코드 변경이
      가시성 한정자 하나(`internal val applied`)이고 verifier r3 가 head `2fbfba8`(이 커밋의
      부모)에서 S-0~S-6 전건을 이미 재현했다(팀장 지시, 운영자 채택 2026-09-02 차단 문턱과
      정합 — low/장부층은 전건 재검증을 요구하지 않는다).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 가 전 모듈의
      test·ktlint·detekt·moduleDependencyGate·sizeGate·cpdCheck·gateExecutionGate·
      qualityBaseline·contractGate·**sourceLanguageGate**(L-10, 아래)를 포함해 통과.
- [x] 변경된 fixture 와 정책 version 의 근거 기록 — golden-manifest.json(5 case, sha256 실측
      대조) · **fixture input/expected 는 라운드 1·2·장부층 일괄 전부에서 무변경**(`git log --
      fixtures/{input,expected}/strategy-edit-*.json` 전 파일이 최초 등재 커밋 `6101dd2`
      하나뿐) · `EDIT_SESSION_POLICY`/`STRATEGY_POLICY` 는 값 미확정 placeholder.
- [x] 알려진 제한과 rollback 방법이 기록됨 — milestone-4.md 4A 구현 기록 문단(이 일괄 반영) ·
      rollback.md(목록 34개 불변 — 이 커밋도 신규 파일이 없다).
- [x] secret 스캔 통과 — commands.md, 자기참조 오탐(commands.md·checklist.md 자신의 절
      제목 문구) 판독 후 실제 매치 0건.

## 이 slice 고유 확인 — H-3 정정(우회 (3) 재폐쇄) + L-8/L-10 반영

**라운드 1 의 결론이 반증됐다(라운드 2).** 「`AppliedStrategy`(internal constructor)가
`workflow` 밖 생성을 막아 우회 (3)이 닫혔다」는 생성(위조)만 막았지 **획득**을 막지 못했다 —
`beginSession`·`apply` 가 public top-level 함수였기 때문에, `workflow` 밖에서 그 둘을 직접
몰아 정당한 `AppliedStrategy`를 얻고 `EditStrategyWorkflow`를 거치지 않고 저장할 수 있었다
(H-3, verifier 실측: `revision=778` 저장 + 발행 **0**). 같은 뿌리로 `beginSession` 결과를
세션 port 에 직접 넘기면 활성 세션도 덮어썼다(M-4). **수정 — 커널 자체를 `internal` 로.**
`beginSession`·`apply`·`expireIfDue` 셋을 `internal`로 내려 `workflow` 밖에서는 `EditSession`
도 `TransitionOutcome`도 **얻는 경로 자체가 없게** 했다.

**실측 (a)(b)(c)**(라운드 2, clone 재현):
```
e: .../H3Probe.kt:25:36 Cannot access 'fun apply(...)': it is internal in file.
e: .../H3Probe.kt:26:36 Cannot access 'fun beginSession(...)': it is internal in file.
e: .../M4Probe.kt:10:36 Cannot access 'fun beginSession(...)': it is internal in file.
```
양성 대조: probe 제거 후 `:app:compileTestKotlin :workflow:test :app:test --tests
'*Conformance*'` → `BUILD SUCCESSFUL`(34 tasks).

**L-8(verifier r3) — 통로 토큰이 use case 의 공개 반환값으로 샌다.** `CommandResult.
Processed(outcome).applied`가 public 이라 `workflow` 밖에서 `AppliedStrategy` 값을 손에
넣어 **나중에 낡은 값으로 재저장**(되감기)할 수 있었다(위조된 새 값은 아니다 — `copy`·
생성자 모두 internal). **수정**: `TransitionOutcome.Applied.applied` 를 `internal val` 로
내렸다. 실측: `:workflow:test`·`:app:compileTestKotlin` **exit 0**(모듈 안 코드·corpus
무영향 — 이 값을 읽는 자리가 전부 `workflow` 모듈 안에 있었다). 이로써 이 값을 use case
반환값에서 꺼내 재사용하는 경로도 닫혔다.

**L-10(verifier r3, 정보성) — 폐쇄는 2층이다.** Kotlin `internal` 은 소스 언어(Kotlin
컴파일러) 층의 보장이지 바이트코드 층의 보장이 아니다 — `.java` 소스가 이 트리에 놓이면
`javac` 는 `internal` 을 모르므로 `new AppliedStrategy(...)` 가 컴파일된다(1층 우회).
그것을 막는 것은 **`sourceLanguageGate`**(build-logic, `check` 안에 등록 — 이 저장소는
`workflow` 를 포함해 전 모듈이 Kotlin 전용이라 `.java` 소스 자체를 거부한다, 2층). 즉
우회 (3)의 폐쇄는 **Kotlin 가시성(1층) + `sourceLanguageGate`(2층)** 둘이 함께 서는 것이지
`internal` 만으로 서는 것이 아니다.

**`OPEN-4A-WRITE-PATH-GATE` — 종결.** **닫힌 것**: (i) 이 slice 의 두 port를 우회해 커널을
직접 몰아 정당한 통로 값을 만드는 경로(H-3/M-4, 커널 `internal`) (ii) use case 가 이미
반환한 통로 값을 나중에 재사용하는 경로(L-8, `Applied.applied` `internal`) — 둘 다 Kotlin
가시성(1층) + `sourceLanguageGate`(2층, L-10)가 함께 막는다. **경계 밖(닫지 않는 것)**:
「4A port 를 아예 거치지 않고 다른 모듈이 자체 persistence(별도 JDBC 커넥션으로 전략
테이블에 직접 INSERT 등)를 새로 만드는 것」 — `workflow` 의 port 추상화 자체를 우회하는
것이라 4A 코드 어떤 폐쇄로도 막을 수 없고 3D/4C 의 write 게이트(실 스키마·권한 계층) 책임
이다(scope.md 위협 모델 경계 (0)). 리플렉션 경로(`setAccessible`)는 남지만 이 위협 모델이
막으려는 것(실수로 생기는 새 write 경로)이 아니다(verifier r3 판단, 채택).

**갱신된 위협 모델 방어 대응표**(Phase 2.5 설계 검토 노트가 지목한 우회 경로 + 라운드가
새로 연 항목):

| 우회 | 방어 위치 | 게이트/test |
| --- | --- | --- |
| (1) `EditSession.copy`로 상태 직조 | `@ConsistentCopyVisibility` + `internal constructor` | 컴파일(모듈 밖 `copy` 불가) |
| (2) `Confirm`에 revision 없이 stale 확인 | `seenRevision: StrategyRevision` 필수 필드 | 컴파일 |
| (3) 어댑터가 `StrategyRepository.save` 직접 호출 | `AppliedStrategy`(위조 방지) **+** `beginSession`·`apply`(`internal`, 획득 방지) **+** `sourceLanguageGate`(2층, L-10) | 컴파일 2층(실측 (a)(b)(c) 위) |
| (4) timeout 을 0/음수/무한으로 | `EditSessionPolicyData.init` — `require(양수)` + 상한 24h | `EditSessionPolicyDataTest`(변이 실측) |
| (5) 같은 commandId 로 다른 command | `duplicateOutcome` — fingerprint 불일치 시 `IdempotencyConflict` | `EditSessionIdempotencyPropertyTest` |
| (6) `System` actor 로 `Confirm` | 전이표에 `System` 행 없음 | `EditSessionActorAndTimeoutTest` |
| (7) 전이 함수가 Telegram 타입을 FQN 으로 참조 | allow-list 술어(소문자 단일 세그먼트까지 확장, L-7) | `EditSessionImportBoundaryTest`(양성/음성 대조) |
| (8) `begin()` 이 기존 비종단 세션을 가드 없이 덮어씀(N-1) | `sessions.load` 확인 후 `Rejected(SessionAlreadyActive)` | `EditStrategyWorkflowTest` |
| (9) `begin()` 커널을 `workflow` 밖에서 직접 호출(M-4) | `beginSession` `internal` | 컴파일 |
| (10) 만료된 세션이 fold 안 돼 `begin()` 을 무기한 막음(M-5) | 가드 전에 `expireIfDue` 적용 | `EditStrategyWorkflowTest`(재현 test) |
| (11, 신설) use case 반환값의 통로 토큰을 나중에 재사용(L-8) | `TransitionOutcome.Applied.applied` `internal` | 컴파일 |

- [x] **fixture 다섯의 case id 와 `review.approved_by_user: false` 사실** — `strategy-edit-001~005`,
      golden-manifest.json 에 전건 `review_approved_by_user: false`. 운영자 승인은 Phase 6.
      **입력/기대값은 이 일괄 커밋에서도 무변경.**

## M-1~M-5 + L-5~L-10 + B-5~B-7 — 산출물층 미달이 아닌 medium/low 반영

- **M-1**(판정 순서 판별 case 부재): 변이 실측 — 새 case 1건만 FAILED.
- **M-2**(정책 불변식 회귀 보호 0): `EditSessionPolicyDataTest` 신설, 변이 실측 3건 FAILED.
- **M-3**(저장 순서로 편집 영구 소실 가능): `strategies→events→sessions` 순 교정.
- **M-4**(세션 축 옆문): H-3 와 같은 커널 `internal` 화로 함께 닫힘.
- **M-5**(이번 라운드가 새로 연 것): `begin()` 가드 전에 `expireIfDue` 적용으로 교정.
- **L-5**(만료 fold 의 세션 저장이 `Rejected` 경로에서도 영속되는지 회귀 보호 0): 독립 test 신설
  — 이 test 는 `process()`(`provideValue`/`confirm` 등 command 경로)의 저장을 잰다. **L-9(아래)
  와 다른 자리다** — L-9 는 `begin()` 자체의 중간 저장을 가리킨다.
- **L-6**(`TransitionOutcome.Applied` 가 모듈 밖에서 생성·`copy` 가능): `@ConsistentCopyVisibility`
  + `internal constructor`.
- **L-7**(S-3b 사각이 서술보다 넓다): 술어를 소문자 세그먼트 하나 이상으로 넓힘(아래 B-6).
- **L-8**(위 「이 slice 고유 확인」 참고): `Applied.applied` `internal`.
- **L-9**(`begin()` 의 `sessions.save(folded)` 는 같은 id 경로에서 곧바로 덮이는 죽은 쓰기) —
  코드는 유지하고(종단 판정 자체가 `folded` 를 봐야 하고, id 가 아니라 세션 인스턴스마다
  이력을 남기는 실 저장소에서는 이 저장이 감사 기록이 된다) `EditStrategyWorkflow.begin`
  의 KDoc 을 「fold 가 영속된다」로 읽히지 않게 정정 — 「이 id-upsert port 계약 위에서는
  곧바로 덮인다」를 명시했다.
- **L-10**(위 참고): 폐쇄가 2층(Kotlin 가시성 + `sourceLanguageGate`)임을 명시.
- **B-5**: commands.md 「전체 33건」을 35건으로 정정(라운드 1 에서 처리).
- **B-6**(L-7 의 「46건」이 재현되지 않는다) — **직접 재산출**: 정규식
  `\b([A-Za-z][A-Za-z0-9_]*)\.[A-Z][A-Za-z0-9_]*\b`(대문자 단일 세그먼트 루트까지 허용하는
  최소 강화형)를 `workflow/src/main/kotlin/bidvector/workflow/strategy/*.kt` 에 적용한 결과
  **총 매치 97건 · 매치를 담은 줄 85건 · 그중 주석 아닌 줄 81건 · 고유 매치 문자열 42종**
  (명령·재현 절차는 `EditSessionImportBoundaryTest.kt` 의 `QUALIFIED_REFERENCE` KDoc과
  commands.md에 기록). 어느 셈으로도 원래 적었던 「46」이 아니다 — 오산이었다. **판단
  (강화하지 않는다)은 그대로 유효** — 어느 셈이든 두 자리 수 오탐이라 강화의 대가가 크다.
- **B-7**: 이 문서의 「닫힌 것/경계 밖」 서술(위 「이 slice 고유 확인」)에 L-8(반환값 경유
  재사용)과 L-10(2층 폐쇄)을 반영했다.

## 알려진 제한(장부층 일괄 갱신)

- 이벤트 발행·세션 전진의 원자성 부재 — 저장 성공 뒤 발행 실패는 다음 재전달이
  `StaleRevision` 으로 정직하게 거부되나(M-3), 그 사이 세션이 전진하지 않는 잔여 창은
  남는다(4C 트랜잭션 outbox 가 닫는다)
- 편집 세션 영속의 실 구현 부재(port + fake 까지, 4C/3D) — `sessionVersion` 낙관적 동시성은
  fake 가 단순 map 이라 충돌을 재지 않는다(경계 안 사실)
- 만료 트리거(sweep) 배선 부재 — `expireIfDue`/`expire()` 는 순수 함수로 존재, 스케줄러 없음
  (M-5 로 `begin()` 자체는 이 부재와 안전하게 공존)
- `System` actor 확인 경로 미구현(D-4A-5, STR-15 `후속`)
- Telegram 어댑터 없음(`OPEN-STR-12` 활성 유지)
- S-3b 술어는 대문자로 시작하는 단일 세그먼트 루트(`Telegram.Bot`)를 여전히 잡지 못한다 —
  강화하면 이 패키지 자신의 정당한 sealed 하위 타입 접근(재산출 총 매치 97건·고유 42종,
  위 B-6)까지 오탐한다
- 우회 (3) 폐쇄는 Kotlin 가시성(`internal`) + `sourceLanguageGate` 의 **2층**이다(L-10) —
  1층만으로는 `.java` 소스를 통한 우회가 가능했다(verifier 실측)
- `OPEN-4A-WRITE-PATH-GATE`는 **종결** — 4A port 우회 경로(직접 호출 + 반환값 재사용)는
  커널·통로 필드 `internal` 화로 닫혔고, port 자체를 거치지 않는 자체 persistence 는
  명시적으로 경계 밖(3D/4C)이다
