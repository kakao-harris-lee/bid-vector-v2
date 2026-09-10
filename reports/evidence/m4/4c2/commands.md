# commands.md — M4/4C-2

명령과 종료 코드만 남긴다(출력 전문 금지, evidence-pack 스킬 규격). 감사자는 명령을 다시 돌린다.

## RED → 구현 → 컴파일 확인

- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`
- exit: 0(main·test 소스 전부 컴파일 — `bidvector.adapters.event` 신규 패키지,
  `ConnectionSource`/`TransactionBoundary`, 개조된 `JdbcRawObservationStore` 포함)

## S-2 — `:adapters:test`(초기 라운드, 3건 실패 → 수정 → 그린)

- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.event.*" --tests "bidvector.adapters.persistence.*"`
- exit: 1(초기) — `OutboxClaimConcurrencyTest` 둘이 `OutboxPayloadCodec.decode`에서
  `알 수 없는 outbox payload_type: irrelevant`로 실패(claim이 payload를 항상 디코드하는데
  claim 경합 test의 기본 placeholder가 유효하지 않은 값이었다).
- 수정: `EventTestSupport.insertPendingOutboxRow`의 기본 payload_type/payload를 유효한
  `StrategyUpdated` 인코딩으로 교체(payload 자체가 관심사가 아닌 test도 디코드가
  성공해야 한다).
- cmd: 재실행, exit: 0(131 tests, 0 failed)

## S-2 — `:adapters:test`(전체 스위트)

- cmd: `./gradlew --no-daemon :adapters:test`
- exit: 0

## S-3 — 모듈 게이트

- cmd: `./gradlew --no-daemon :adapters:moduleDependencyGate :adapters:sizeGate :adapters:cpdCheck`
- exit: 1(초기) — `sizeGate`: `CleanMigrationTest.kt` 511줄(500 한도 초과, outbox·inbox
  컬럼 스펙 추가분).
- 수정: 축2·3·4(컬럼)를 `CleanMigrationColumnTest.kt`로 분리(3D 축7·8 분리 전례,
  `CleanMigrationTriggerTest`/`CleanMigrationCheckTest`와 같은 처분). `gate-tests
  .properties`에 신규 class 등재.
- cmd: 재실행, exit: 0

## S-4 — `:app:test`(별도 호출)

- cmd: `./gradlew --no-daemon :app:test`
- exit: 0

## S-5 — `qualityBaseline`

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0

## S-6 — `:app:gateExecutionGate`(별도 호출)

- cmd: `./gradlew --no-daemon :app:gateExecutionGate`
- exit: 0

## S-1 — `./gradlew --no-build-cache clean check`(전체, 초기 라운드 2건 실패 → 수정 → 그린)

- cmd: `./gradlew --no-daemon --no-build-cache clean check`
- exit: 1(1차) — ktlint `JdbcOutboxPortTest.kt` 인자 줄바꿈·최대 줄길이.
- exit: 1(2차, ktlint 수정 뒤) — detekt 셋: ① `OutboxPayloadCodec.kt` `MagicNumber`
  (`fields.size == 3`) ② `TransactionBoundary.kt` `TooGenericExceptionCaught`
  (`catch (failure: Exception)`) ③ ktlint `JdbcOutboxPortTest.kt` `MaxLineLength`
  (동일 인자 줄).
- 수정: ① `STRATEGY_UPDATED_FIELD_COUNT` 명명 상수로 대체 ② `TransactionBoundary
  .inTransaction`을 `catch` 없는 형태로 재작성 — `committed` 플래그 + `finally`에서
  커밋 안 됐을 때만 롤백(v2-지침서.md §5 「business control flow에 exception을 쓰지
  않는다」, `ResilientPredictionCall.kt` 관례) ③ 인자를 여러 줄로.
- exit: 1(3차) — ktlint `ActorCodec.kt` when-분기 중괄호 일관성(한 분기만 멀티라인+중괄호).
- 수정: 네 분기 전부 중괄호로 통일 + 분기 사이 빈 줄.
- cmd: 재실행, exit: 0(344 actionable tasks — build-logic·9개 domain 모듈·adapters·app
  전부 `check` 통과, kover 검증 포함)

## S-0 — 임시 clone에서 `clean check`

- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch . "$d/repo" && (cd "$d/repo" && ./gradlew --no-daemon --no-build-cache clean check)`
- exit: 0(353 actionable tasks, 전부 executed — 캐시 없는 완전 처녀 clone)

## rollback 실측(임시 clone, dry-run 아님)

- cmd: `git clone --quiet . <tmp>/repo` → checkout HEAD(`abf6b06c`) → rollback.md의 A
  restore 명령
- exit: 0
- cmd: rollback.md의 M restore 명령
- exit: 0
- 확인: `git status --porcelain` — A 21건 `D`(삭제 대상), M 7건 `M`, 합계 28건(목록과 일치).
  `git diff <base> -- <M 7개 경로>` — 빈 diff(7개 전부).
- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`(되돌린
  트리) — exit: 0
- cmd: `./gradlew --no-daemon :adapters:test`(되돌린 트리) — exit: 0

## secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4c2/`
- exit: 1(매치 없음 = 통과)
- cmd: `git diff ff210c187bb7885da7639de0434d815e59e7f32a..HEAD | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음 = 통과)

## 알려진 제한

- 3D의 다른 네 repository(`JdbcNoticeRepository`·`JdbcOpeningResultRepository`·
  `JdbcQualificationTextRepository`·`JdbcCollectionRunStore`)는 `ConnectionSource`
  참여에 개조되지 않았다 — 지금 outbox와 한 트랜잭션에 묶을 수 있는 도메인 write는
  `raw_observation` append 하나뿐이다(D-4C2-1 갈래 b).
- `OutboxPort.markDelivered`/`markFailed`/`markIsolated`는 production 호출부가
  없다(`OPEN-4C2-MARK-UNEXERCISED`, 배달 오케스트레이션 인계) — 전이 SQL의 효과·거부는
  DB 층에서(`OutboxTransitionSqlTest`) 증명했지 port 메서드 자체를 실행한 test는 아니다.
- claim 커밋 뒤 죽은 워커의 `Claimed` 잔존 회수(sweep)는 이 slice 범위 밖(설계 검토 (3)
  미달 위험 2, 별도 후속).
- crash-after-commit의 「재기동」은 프로세스 재기동이 아니라 새 `TransactionBoundary`
  인스턴스(새 커넥션)로 대역했다(설계 검토가 승인한 정직한 대역).
