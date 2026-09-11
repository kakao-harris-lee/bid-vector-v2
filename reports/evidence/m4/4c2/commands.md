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

## verifier r2(2026-09-10) — ready-for-review(산출물 high 0) → M-3·M-4·L-7·L-11 시정

전제: HEAD `f3f034f`(라운드 착수 시점, base `ff210c1` 그대로). 보고서
`_workspace/m4-4c2/04_verifier_report_r2.md`. r1의 high 둘(H-1·H-2)은 verifier가 실측으로
닫힘 확인 — 이 라운드는 medium 둘·low 다섯만 처리한다.

### H-2 양성 대조 재확인(verifier가 이미 함, 자체 재실측은 생략) — sealed가 옆으로 옮기지
않았음을 verifier가 `PROBE-2a`~`PROBE-2c`로 확인(경계를 여는 것 자체는 sealed 이전에도
가능했던 성질, 새 우회 아님).

### M-3 — GRANT 게이트를 유효 권한 술어로 교체(mutation 실측)

- cmd: `./gradlew --no-daemon :adapters:test --tests "…CleanMigrationTest"`(정상 GRANT,
  `has_table_privilege` 술어로 재작성 뒤)
- exit: 0
- V6 GRANT 두 줄을 `GRANT ALL PRIVILEGES ON {outbox,inbox} TO bidvector_app;`로 변경
- cmd: 재실행 — exit: 1 — 둘 다 실패(delete=true 등 예상 필드 불일치)
- V6 원복(`cp` 백업) 뒤 빈 diff 확인
- V6 끝에 `GRANT DELETE ON outbox TO PUBLIC;` 한 줄 추가(verifier가 지적한 PUBLIC 경유
  미탐 축)
- cmd: 재실행 — exit: 1 — `outbox`: `delete: expected:<false> but was:<true>`(PUBLIC
  경유가 유효 권한에 정확히 반영됨)
- V6 원복 뒤 빈 diff 확인

### M-4 — 봉투 획득 경로 여섯 전부 컴파일 probe(mutation 실측)

- cmd: `./gradlew --no-daemon :adapters:test --tests
  "bidvector.adapters.event.EventInternalClosureCompileTest"`(fixture 5·6 추가 뒤)
- exit: 0 — 6 tests, 0 failed
- `workflow`의 `internal fun forStrategyUpdated(` → `fun forStrategyUpdated(`(한 단어)
- cmd: 재실행 — exit: 1 — `forStrategyUpdated 는 workflow 밖에서 internal 이다` 단독 실패
- 원복(`cp` 백업) 뒤 빈 diff 확인
- `internal fun <P> newEnvelope(` → `fun <P> newEnvelope(`(한 단어)
- cmd: 재실행 — exit: 1 — `newEnvelope 는 workflow 밖에서 internal 이다` 단독 실패
- 원복 뒤 빈 diff 확인

### L-7·L-11 — scope.md in_scope 추가, ConnectionSource KDoc 정정(mutation 불필요, 문면)

### (2b) 라운드별 새 public 표면 — r1·r2 둘 다 기록(verifier r2 L-9 시정 — 이전엔 evidence에 없었다)

**r1 라운드(H-1·H-2·M-1·M-2·L-1~L-6)**:

| 변경 | public 표면 | 판정 |
| --- | --- | --- |
| `ConnectionSource` → `sealed` | 기존 타입이 **좁아짐** | 축소 — 새 권한 없음 |
| `EventInternalClosureCompileTest` + fixture 5 | test 소스. 헬퍼 전부 `private` | 없음 |
| `CleanMigrationTest`·`CleanMigrationCheckTest` 새 test 넷 | test method + `private` 헬퍼 | 없음 |
| `TransactionBoundary`·`JdbcOutboxPort`·`OutboxPayloadCodec` | KDoc·본문만(소진 `when`) | 없음 |

→ **신설 0 · 축소 1**.

**r2 라운드(M-3·M-4·L-7·L-11, 이번 라운드)**:

| 변경 | public 표면 | 판정 |
| --- | --- | --- |
| `CleanMigrationTest` — `has_table_privilege` 재작성 | `private TablePrivileges`·`private effectivePrivileges` | 없음 |
| `EventInternalClosureCompileTest` + fixture 5·6 | test 소스만 | 없음 |
| `ConnectionSource` KDoc 정정 | 없음(문면만) | 없음 |
| `scope.md` in_scope 추가 | 코드 아님 | 없음 |

→ **신설 0 · 축소 0**. 계수 목적 주입 없음(관측 지점은 여전히 DB SQL과 컴파일러 종료 코드).

## 레인 혼입 — 두 번째 발생(2026-09-10, r2 시정 도중 실측)

r2(M-3·M-4·L-7·L-11) 커밋 완료 직후 HEAD가 `4ec52db`에서 `20f7ad0`(M2/2E 레인이
`4ec52db`를 자기 브랜치에 병합한 커밋, clean merge)으로 이동했다. 팀장에게 즉시
보고하고 확인받은 뒤 그 위에서 계속했다(milestone-4.md·rollback.md 「레인 혼입」
문단 — 이력은 되쓰지 않는다).

- cmd: `git log --oneline ff210c187bb7885da7639de0434d815e59e7f32a..20f7ad0 -- CLAUDE.md .claude/`
- 결과: `f3f034f` 1건뿐(재확인, L-4 절차 재실행) — 이 라운드도 하네스 레인 변경 없음.
- 파일 무결성: `gate-tests.properties`의 `gate.tests.adapters` 키 1개(중복 없음), 4C-2·
  4B-3·2E 세 레인의 등재 전부 공존(grep 확인).

## r2 최종 acceptance 전건 재실행(HEAD `20f7ad0` — 레인 혼입 뒤 최종 head, S-0 우연 일치 아님)

- S-0: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08
  --single-branch . "$d/repo" && (cd "$d/repo" && git rev-parse HEAD && ./gradlew
  --no-daemon --no-build-cache clean check)` — clone HEAD 확인 `20f7ad0` 일치, exit 0
  (354 actionable tasks, 전부 executed).
- S-1: `./gradlew --no-daemon --no-build-cache clean check`(로컬, HEAD `20f7ad0`) —
  exit 0(345 actionable tasks).
- S-2·S-3: `./gradlew --no-daemon :adapters:test :adapters:moduleDependencyGate
  :adapters:sizeGate :adapters:cpdCheck` — exit 0.
- S-4(별도 호출): `./gradlew --no-daemon :app:test` — exit 0.
- S-5: `./gradlew --no-daemon qualityBaseline` — exit 0.
- S-6(별도 호출): `./gradlew --no-daemon :app:gateExecutionGate` — exit 0.

**verifier r3 L-12 시정 — 위 표의 기준 head를 명시한다.** 위 S-0~S-6은 `20f7ad0`에서
돈 것이다(docs(m4-4c2) 커밋 `673cbd5`가 이 표 자체를 포함해 `milestone-4.md`·
`commands.md`·`rollback.md` 셋만 델타로 더했다 — 코드·게이트는 무변경). **행운을
근거로 쓰지 않는다** — 현재 HEAD `673cbd5`에서 내가 직접 재실행해 별도로 확인했다:
`./gradlew --no-daemon --no-build-cache clean check`(S-1) exit 0(345 actionable
tasks) · `./gradlew --no-daemon :app:test`(S-4, 별도 호출) exit 0 ·
`./gradlew --no-daemon :app:gateExecutionGate`(S-6, 별도 호출) exit 0. verifier r3도
독립적으로 `673cbd5`에서 S-1을 돌려 exit 0을 확인했다(`_workspace/m4-4c2
/05_verifier_report_r3.md` §acceptance).

## rollback 재실측(r2, 임시 clone, dry-run 아님) — 공유 파일 판정을 파일마다 재계산

`git log --oneline <base>..HEAD -- <파일>`을 in_scope 파일마다 다시 돌려 4C-2·4B-3·2E
세 레인의 귀속을 갈랐다(rollback.md 본문 참고). A 33·M 5(단독) restore 각각 exit 0.
`gate-tests.properties` hunk 역적용 — `70cbcdb` exit 0(clean), `28733bc` **conflict**
(r1과 같은 함정 재현: 4C-2·4B-3 주석 블록이 같은 삽입 지점에 인접) → 수동 해소(「M4/4B-3
문단 보존, M4/4C-2 문단 제거」) → `git add`. `capability-map.md`·`milestone-4.md` hunk
역적용 — 전부 exit 0(conflict 없음, 4C-2·2E/4B-3의 편집 위치가 물리적으로 분리).
`git status --porcelain` 41건(D 33 + M 5 + M 3) 일치. 「내 줄 사라짐」·「남의 줄 남음」
(이번엔 4B-3·2E 둘 다) 전부 grep으로 실측. 되돌린 트리 `:adapters:compileKotlin
:adapters:compileTestKotlin` exit 0, `:adapters:test :app:test` exit 0(4B-3·2E
산출물이 여전히 남아 있는 트리이므로 app도 함께 확인).

## secret 스캔(r2 재실측)

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4c2/`
- 매치 4건, 전부 이 파일의 「secret 스캔」 절 자기 인용(2026-09-02 판독 규칙, 실제
  비밀값 없음).

## 알려진 제한

- 3D의 다른 네 repository(`JdbcNoticeRepository`·`JdbcOpeningResultRepository`·
  `JdbcQualificationTextRepository`·`JdbcCollectionRunStore`)는 `ConnectionSource`
  참여에 개조되지 않았다 — 지금 outbox와 한 트랜잭션에 묶을 수 있는 도메인 write는
  `raw_observation` append 하나뿐이다(D-4C2-1 갈래 b).
- 3D의 기존 두 권한 test(`provenance_authority`·`notice_audit`, `CleanMigrationTest`)는
  M-3와 같은 PUBLIC 경유 공백을 여전히 갖는다(`information_schema.role_table_grants`
  기반) — 이 slice의 in_scope 밖이라 손대지 않았다. 인계.
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

## verifier r3(2026-09-10) — ready-for-review(산출물 high 0) → M-5·L-12 시정(마지막 라운드)

전제: HEAD `673cbd5`(라운드 착수 시점, base `ff210c1` 그대로). 보고서
`_workspace/m4-4c2/05_verifier_report_r3.md`. M-3·M-4는 verifier가 mutation 넷·여섯으로
직접 재실측해 닫힘 확인(양방향 모두 — 초과·부족 둘 다 잡힘, fixture 여섯이 각자 자기
축만 낙제). 이 라운드는 M-5·L-12만 처리한다. **이 수정 커밋은
`reports/evidence/m4/4c2/**`만 바꾼다** — `milestone-4.md`·`gate-tests.properties`·
`capability-map.md`·`scope.md`는 한 줄도 건드리지 않았다(아래 뿌리 참고, 자기 재생산
방지).

### M-5 — rollback.md의 milestone-4.md hunk 목록에 `673cbd5`가 빠져 있었다

verifier가 문서 그대로 실행해 재현: `97af8bb` 역적용이 conflict(문서는 「conflict
없음」이라 틀렸다), 이어 `abf6b06` 역적용이 「인덱스에 없습니다」로 시퀀스 자체가
끊겼다(1A 16차와 같은 「명령이 실패」 계열). 뿌리 — `673cbd5`(rollback.md 자신을
담은 evidence 커밋)가 `milestone-4.md`의 레인 혼입 문단도 함께 만졌는데, 그 사실이
목록에 반영되지 않았다. r2 때(`8f8e1e1`)는 그 커밋이 `milestone-4.md`를 안 건드려
둘짜리 목록이 우연히 맞았을 뿐이다.

시정: rollback.md의 milestone-4.md hunk 목록에 `673cbd5`를 최신 자리로 추가(셋:
`673cbd5`→`97af8bb`→`abf6b06`) + 항구 규칙 명시(rollback.md를 갱신하는 커밋이 공유
파일도 함께 바꾸면 그 커밋 해시를 그 공유 파일의 hunk 목록 최신 자리에 반드시
추가한다).

- cmd: 임시 clone(HEAD `673cbd5` 이상, 시정된 rollback.md 반영 뒤)에서 A(33)·M(5) restore
  — 둘 다 exit 0.
- cmd: `config/quality/gate-tests.properties` hunk 역적용 `70cbcdb`(exit 0, clean) →
  `28733bc`(conflict, 문서의 수동 해소 절차) → `git add`.
- cmd: `docs/discovery/capability-map.md` hunk 역적용 `abf6b06` — exit 0(conflict 없음).
- cmd: `milestone-4.md` hunk 역적용 **셋, 최신 순** `673cbd5` → `97af8bb` → `abf6b06`
  — **전부 exit 0**(conflict 없음, M-5 시정 확인).
- 확인: `git status --porcelain` 41건(D 33 + M 8) — rollback.md 수치와 일치. 「내 줄
  사라짐」(`4C-2 구현`·`4C-2 verifier` grep 0) · 「남의 줄 남음」(4B-3 착수/종결 문단
  grep 2, capability-map.md의 2E `OPEN-2E-*` 행 grep 2) 둘 다 확인.
- cmd: 되돌린 트리 `./gradlew --no-daemon :adapters:compileKotlin
  :adapters:compileTestKotlin` — exit 0.
- cmd: 되돌린 트리 `./gradlew --no-daemon :adapters:test :app:test` — exit 0.

### L-12 — acceptance 기준 head 명시(위 「r2 최종 acceptance」 절에 반영)
