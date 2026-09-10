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
- r1 재실측(commands.md 자체 변경 뒤) — 매치 3건, 전부 이 파일의 「secret 스캔」 절이
  검사 패턴 문자열 자신을 인용한 것(2026-09-02 판독 규칙 — developer 구간 매치 아님,
  실제 비밀값 없음).

## verifier r1(2026-09-10) — not-ready(high 둘) → 시정

전제: HEAD `a3822c4`(라운드 착수 시점). 보고서 `_workspace/m4-4c2/03_verifier_report.md`.

### H-2 — ConnectionSource 를 sealed 로 닫는다(옵션 1)

- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`(sealed 전환 뒤)
- exit: 0 — `TransactionBoundary`·`DataSourceConnectionSource`(이름 있는 클래스, 익명
  object 아님) 둘 다 같은 패키지라 sealed 규약을 그대로 만족.
- 양성 대조(비파괴 probe, `app/src/test/.../verifier4c2/RogueConnectionSourceProbe.kt`
  심고 실행 뒤 파일·디렉터리 삭제):
- cmd: `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1 — 진단 원문: `Extending sealed classes or interfaces from a different module
  is prohibited.` / `A class can only extend a sealed class or interface declared in
  the same package.`
- cmd: probe 제거 뒤 재실행 `./gradlew --no-daemon :app:compileTestKotlin` — exit: 0(복구 확인)

### H-1 — outbox·inbox GRANT 회귀 게이트(mutation 실측)

- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.persistence.CleanMigrationTest"`(정상 GRANT)
- exit: 0
- V6 GRANT 두 줄을 `GRANT ALL PRIVILEGES ON {outbox,inbox} TO bidvector_app;`로 임시 변경
- cmd: 재실행 — exit: 1 — 둘 다 실패, `unexpected: [DELETE, TRUNCATE, REFERENCES, TRIGGER]`
  (outbox)·`[UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER]`(inbox)
- V6 원복(`cp` 백업본으로) 뒤 `git diff --stat` 빈 diff 확인

### M-1 — outbox_state_check 본문 정확 대조(mutation 실측)

- cmd: `./gradlew --no-daemon :adapters:test --tests "...CleanMigrationCheckTest"`(정상 상태)
- exit: 0
- V6의 `state IN (...)`에 `'RETRYING'` 추가
- cmd: 재실행 — exit: 1 — `expected:<...ISOLATED'::text]))> but was:<...ISOLATED'::text,
  'RETRYING'::text]))>`
- V6 원복 뒤 빈 diff 확인

### M-2 — event 내부 폐쇄 컴파일 실패 probe(신설)

- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.event.EventInternalClosureCompileTest"`
- exit: 0 — 4 tests, 0 failed. 네 access point(`EventEnvelope` 생성자·`transitionOutbox`·
  `OutboxTransition.ToDelivered` 생성자·`OutboxEntry.restore`) 전부 `cannot access`
  포함 진단으로 실패, 양성 대조(`ClaimedOutboxRow`)는 정상 컴파일.

### L-1~L-6 — 문면 정정·장부(라운드를 막지 않음, 같은 라운드에서 처리)

L-1(`TransactionBoundary` owner-thread `check` 도달 불가 서술 정정) · L-2(`OutboxPayloadCodec`
소진 `when`) · L-3(`data-dictionary.md` §2.2.5 가 `OPEN-4C1-TX-CONTRACT-UNVERIFIED` 를
계속 활성으로 말함 — **in_scope 밖이라 고치지 않는다.** 인계: capability-map.md는 이
slice가 닫힘으로 갱신했으나 data-dictionary.md 는 그 반대말을 그대로 담고 있다 — 문서
정합화는 4C-2 밖의 후속(다음 4C 관련 slice 또는 장부 통합 slice)이 진다) · L-4(scope.md
「하네스 레인 변경」 절을 실측값 0건으로 갱신) · L-5(rollback.md 를 이번 라운드에서
전면 재산출 — 「제외」·commit-hash hunk 격리 절 신설로 흡수) · L-6(`claim` KDoc — 커밋
시점을 정하는 것은 port 가 아니라 호출부라는 정정).

## 레인 혼입(2026-09-10, r1 수정 도중 실측)

r1 수정 중 HEAD가 `a3822c4`에서 `8b50461`(4B-3 레인이 `a3822c4`를 자기 브랜치에 병합한
커밋)로 이동했다. 팀장에게 즉시 보고하고 확인받은 뒤 그 위에서 계속했다(milestone-4.md
「레인 혼입」 문단, rollback.md 「r1 갱신」 절 — 이력은 되쓰지 않는다).

- cmd: `git log --oneline ff210c187bb7885da7639de0434d815e59e7f32a..8b50461`
- 결과: 4C-2 커밋 일곱(`508d5de`~`a3822c4`) + 4B-3 커밋 여섯(`5556d81`~`db03c0b`) + 병합
  커밋(`8b50461`) — 다른 slice 커밋 섞임 없음(4B-3만).
- 파일 무결성: `config/quality/gate-tests.properties`의 `gate.tests.adapters`에 4C-2의
  `event.*` 아홉 항목과 4B-3의 `ml.UnavailableMlAnalysisTest`가 충돌 표식 없이 병존
  (병합 커밋이 이미 해소, grep 확인).

## r1 이후 acceptance 전건 재실행(HEAD `f19d2eb`)

- S-1: `./gradlew --no-daemon --no-build-cache clean check` — exit 1(1차, ktlint
  `CleanMigrationCheckTest.kt:89` `Unexpected indentation` — multiline string 연속줄이
  `shouldBe` 인자 그대로라 16칸, 기대 12칸) → 기대값을 지역 변수로 분리(커밋 `f19d2eb`)
  → 재실행 exit 0(345 actionable tasks).
- S-0: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08
  --single-branch . "$d/repo" && (cd "$d/repo" && ./gradlew --no-daemon --no-build-cache
  clean check)` — 1차 exit 1(같은 ktlint 미커밋 상태에서 실측, `f19d2eb` 커밋 전) →
  `f19d2eb` 커밋 뒤 재실행 exit 0(354 actionable tasks, 전부 executed).
- S-2: `./gradlew --no-daemon :adapters:test :adapters:moduleDependencyGate
  :adapters:sizeGate :adapters:cpdCheck` — exit 0.
- S-4(별도 호출): `./gradlew --no-daemon :app:test` — exit 0.
- S-5: `./gradlew --no-daemon qualityBaseline` — exit 0.
- S-6(별도 호출): `./gradlew --no-daemon :app:gateExecutionGate` — exit 0.

## rollback 재실측(r1, 임시 clone, dry-run 아님)

- cmd: `git clone --quiet . <tmp>/repo` → checkout `m4/2026-09-08`(HEAD `f19d2eb`) →
  rollback.md의 A(31) restore — exit 0.
- cmd: M(6, 단독) restore — exit 0.
- cmd: `config/quality/gate-tests.properties` hunk 역적용 `70cbcdb` — exit 0(conflict
  없음), `28733bc` — **conflict**(양쪽이 같은 삽입 지점에 인접, `--3way`도 자동 해소
  못함) → 수동 해소(`# M4/4C-2 —` 문단만 제거, `# M4/4B-3 —` 문단 보존) → `git add`.
- cmd: `milestone-4.md` hunk 역적용 `97af8bb`·`abf6b06` — 둘 다 exit 0(conflict 없음,
  두 slice 문단이 파일의 다른 절에 있다).
- 확인: `git status --porcelain` 39건(A 31 `D` + M 6 `M` + M 2 `M`). 4C-2 흔적
  (`bidvector.adapters.event.*`·`CleanMigrationColumnTest`·`OutboxTransactionAtomicityTest`·
  「4C-2 구현」/「4C-2 verifier」 문단) grep 0건. 4B-3 흔적(`UnavailableMlAnalysisTest`·
  `# M4/4B-3 —`·「4B-3 종결」 문단) grep 그대로 존재.
- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`(되돌린
  트리) — exit 0.
- cmd: `./gradlew --no-daemon :adapters:test`(되돌린 트리) — exit 0.

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
- `ConnectionSource`가 `sealed`로 교차 모듈 우회는 닫혔으나, `adapters` 모듈 **안**에서
  `DataSourceConnectionSource`를 손으로 조립해 `JdbcOutboxPort`에 넘기는 것은 타입
  검사로 막히지 않는다(verifier r1 H-2 갱신) — `DataSource` 보유자 권한 이하라 새
  권한은 아니다(같은 모듈은 이미 raw SQL로 outbox에 직접 쓸 수 있다).
- `data-dictionary.md` §2.2.5가 `OPEN-4C1-TX-CONTRACT-UNVERIFIED`를 여전히 활성으로
  말한다(verifier r1 L-3) — capability-map.md는 이 slice가 닫힘으로 갱신했지만
  data-dictionary.md는 in_scope 밖이라 고치지 않았다. 후속 slice 인계.
