# rollback.md — M4 / 4C-2

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬,
2026-09-04 개정). 하네스 경로(`CLAUDE.md`·`.claude/**`) 변경은 **0건**(`git log --oneline
ff210c187bb7885da7639de0434d815e59e7f32a..HEAD -- CLAUDE.md .claude/` 결과 없음, scope.md
「하네스 레인 변경」 절과 일치).

## r1 갱신 — 레인 혼입으로 공유 파일이 실재하게 됐다

verifier r1 수정 진행 중 4B-3 레인이 이 slice의 진행분(`a3822c4`)을 자기 브랜치에
병합했고, 그 병합 커밋(`8b50461`)으로 `m4/2026-09-08`·`main`이 함께 이동했다(이력을
되쓰지 않고 사실로 선언, milestone-4.md 참고). **base_sha는 계약값 `ff210c1` 그대로**다
— range가 아니라 in_scope 경로의 변경이 slice의 커밋 집합이다(2026-09-04 규율).

그 결과 `git diff --name-status ff210c187bb7885da7639de0434d815e59e7f32a..HEAD`에
**4B-3의 파일도 함께 잡힌다.** 아래 목록은 그중 **4C-2 몫만** 추린 것이다.

## 목록 산출(기계적)

```
git diff --name-status ff210c187bb7885da7639de0434d815e59e7f32a..HEAD
```

원본 결과: **A 37 · M 17**. 그중 **4B-3 전용(이 slice 몫 아님, 건드리지 않는다) A 6 ·
M 8**을 뺀다:

- A 6: `adapters/src/main/kotlin/bidvector/adapters/ml/UnavailableMlAnalysis.kt` ·
  `adapters/src/test/kotlin/bidvector/adapters/ml/UnavailableMlAnalysisTest.kt` ·
  `reports/evidence/m4/4b3/{checklist,commands,rollback,scope}.md`
- M 8: `decision/src/main/kotlin/bidvector/decision/{LadderInput,VerdictLadder}.kt` ·
  `decision/src/test/kotlin/bidvector/decision/VerdictLadderTest.kt` ·
  `workflow/src/main/kotlin/bidvector/workflow/evaluation/{EvaluateCandidatesUseCase,Ports}.kt` ·
  `workflow/src/test/kotlin/bidvector/workflow/evaluation/{EvaluateCandidatesUseCaseIsolationTest,
  EvaluateCandidatesUseCaseTest,EvaluationTestFixtures}.kt`

남는 **4C-2 몫: A 31 · M 9**. M 9 중 `reports/evidence/m4/4c2/scope.md` 1건은 되돌릴
대상이 아니다(아래 「제외」) — 그러므로 실제 restore 대상은 **A 31 · M 8**이고, 그 M 8
중 둘(`config/quality/gate-tests.properties`·`milestone-4.md`)이 **진짜 공유 파일**이라
커밋 해시 hunk 격리가 필요하다(아래).

## 제외 — `reports/evidence/m4/4c2/scope.md`

이 파일의 M 편집 둘 다(`85a3835` 계약 갱신, `97af8bb`의 L-4 실측 갱신) **되돌릴 대상이
아니다** — 계약·장부 문서지 wiring이 아니다. evidence 디렉토리 자체(`commands.md`·
`rollback.md`·`differential.json`·`golden-manifest.json`)도 같은 이유로 대상이 아니다.

## A(신규 31) — 전체 삭제

```
git restore --source=ff210c187bb7885da7639de0434d815e59e7f32a --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/event/ActorCodec.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/EventSql.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/JdbcEventIdFactory.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/JdbcInboxPort.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/JdbcOutboxPort.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/ConnectionSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/TransactionBoundary.kt \
  adapters/src/main/resources/db/migration/V6__outbox_inbox.sql \
  adapters/src/test/kotlin/bidvector/adapters/event/ActorCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/JdbcEventIdFactoryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/JdbcInboxPortTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/JdbcOutboxPortTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxClaimConcurrencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxTransitionSqlTest.kt \
  adapters/src/test/resources/compile-fixtures/negative-1-event-envelope-ctor.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-2-transition-outbox.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-3-outbox-transition-to-delivered-ctor.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-4-outbox-entry-restore.kt.txt \
  adapters/src/test/resources/compile-fixtures/positive-claimed-outbox-row-public.kt.txt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/OutboxTransactionAtomicityTest.kt \
  reports/evidence/m4/4c2/commands.md \
  reports/evidence/m4/4c2/differential.json \
  reports/evidence/m4/4c2/golden-manifest.json \
  reports/evidence/m4/4c2/rollback.md
```

## M(6, 이 range 안에서 4C-2 단독) — 전체 복원

```
git restore --source=ff210c187bb7885da7639de0434d815e59e7f32a --staged --worktree -- \
  adapters/build.gradle.kts \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcRawObservationStore.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  docs/discovery/capability-map.md
```

(`git log --oneline ff210c1..HEAD -- <각 경로>`로 각각 확인 — 4B-3 커밋이 이 여섯 중
어느 것도 손대지 않는다.)

## M(2, 진짜 공유) — 커밋 해시 hunk 격리

`config/quality/gate-tests.properties`(4B-3 `eb1a1fc`가 `ml.UnavailableMlAnalysisTest`
등재 7줄을 더함)·`milestone-4.md`(4B-3 `db03c0b`가 4B-3 종결 문단을 더함)는 4C-2도
같은 range에서 만졌다. **자기 커밋 해시로 hunk를 격리해 역적용**한다 — 최신 것부터
역순으로:

```
# gate-tests.properties — 4C-2 커밋: 70cbcdb(r1 등재) 다음 28733bc(원 등재)
git diff 70cbcdb~1..70cbcdb -- config/quality/gate-tests.properties | git apply -R --3way
git diff 28733bc~1..28733bc -- config/quality/gate-tests.properties | git apply -R --3way

# milestone-4.md — 4C-2 커밋: 97af8bb(r1 문단) 다음 abf6b06(원 문단)
git diff 97af8bb~1..97af8bb -- milestone-4.md | git apply -R
git diff abf6b06~1..abf6b06 -- milestone-4.md | git apply -R
```

**함정(실측, r1) — `gate-tests.properties`의 두 번째 역적용(`28733bc`)이 자동으로
안 붙는다.** 4C-2(`M4/4C-2` 주석 블록)와 4B-3(`M4/4B-3` 주석 블록)가 파일의 **같은
삽입 지점**(`gate.tests.adapters=\` 바로 위)에 인접해 붙어 있어, `70cbcdb` 역적용 뒤
`28733bc`의 패치 컨텍스트가 그 경계를 정확히 못 찾고 conflict marker(`<<<<<<< ours` /
`=======` / `>>>>>>> theirs`)를 남긴다(`--3way`도 자동 해소를 못 한다). **수동으로
해소한다** — conflict block 안에서 `# M4/4B-3 —` 문단은 남기고 `# M4/4C-2 —` 문단만
지운 뒤 marker 세 줄을 제거하고 `git add`. 나머지(event.\* 아홉 줄·
`CleanMigrationColumnTest,`·`OutboxTransactionAtomicityTest,` 목록 항목)는 두 명령
모두 conflict 없이 지워진다 — 이 지점 하나만 손으로 본다. `milestone-4.md`는 두 문단이
파일의 다른 절(4B 절 끝 vs Slice 4C 절)에 있어 conflict 없이 깨끗하게 역적용된다.

## 마이그레이션

**되돌리지 않고 V7로 무력화한다**(append-only, 3D 관례) — 위 A 목록의
`V6__outbox_inbox.sql` 삭제는 파일만 지우는 것이라 이미 적용된 DB에는 영향이 없다. 운영
DB에 V6가 이미 적용된 뒤 이 slice를 끄려면 별도 `V7__drop_outbox_inbox.sql`(또는 GRANT만
회수)을 새로 얹는다 — 이 rollback은 **코드 트리** 복원만 다룬다.

`git checkout <base> -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec 오류로
exit 1, 1A/4A/4C-1 선례).

## 확인 지점 — 임시 clone에서 실제로 실행(dry-run 아님, r1에서 재실측)

1. 임시 clone(`git clone .`, HEAD `f19d2eb`)에서 A 31 restore, M 6 restore 모두 **exit 0**.
2. `git status --porcelain` — **39건**(A 31 삭제 대상 `D` + M 6 `M` + M 2(공유) `M`).
3. M 6(단독) 각각 `git diff <base> -- <경로>` — 빈 diff.
4. `config/quality/gate-tests.properties` hunk 역적용 — `70cbcdb` exit 0, `28733bc`
   conflict(위 함정) → 수동 해소 → `git add`. 확인: `bidvector.adapters.event.*`·
   `CleanMigrationColumnTest`·`OutboxTransactionAtomicityTest` 등재 **사라짐**(grep 0건),
   `bidvector.adapters.ml.UnavailableMlAnalysisTest`·`# M4/4B-3 —` 문단 **남음**(grep로
   확인).
5. `milestone-4.md` hunk 역적용 — `97af8bb`·`abf6b06` 둘 다 exit 0(conflict 없음).
   확인: 「4C-2 구현」·「4C-2 verifier」 문단 **사라짐**, 「4B-3 종결 2026-09-10」 문단
   **남음**.
6. 되돌린 트리에서 **모듈별 compile** — `:adapters:compileKotlin
   :adapters:compileTestKotlin` exit 0.
7. 되돌린 트리의 **test** — `:adapters:test` exit 0(V6 마이그레이션·outbox/inbox test
   자체가 없으므로 3D 기존 test만 남아 통과).

## 되돌린 뒤 남는 것

`TransactionBoundary`·`ConnectionSource`·`bidvector.adapters.event` 패키지 전체·
V6 스키마·outbox/inbox 관련 test가 전부 사라지고, `JdbcRawObservationStore`는 4C-2 이전
(순수 `DataSource` 생성자 하나)로 복귀한다. `OPEN-4C1-TX-CONTRACT-UNVERIFIED`는 다시
**활성**으로 돌아간다(capability-map.md의 이 slice 처리 문단도 되돌아가므로). 4B-3의
산출물(`UnavailableMlAnalysis`·`LadderInput.mlUnavailableReason`·milestone-4.md 4B-3
종결 문단·gate-tests.properties의 `ml.UnavailableMlAnalysisTest` 등재)은 **그대로
남는다** — 이번 시정(공유 파일 hunk 격리)의 목적이다. 3D의 다른 persistence
test·repository는 무영향(이 slice가 손대지 않았다).
