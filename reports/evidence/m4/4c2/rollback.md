# rollback.md — M4 / 4C-2

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬,
2026-09-04 개정). **base_sha는 계약값 `ff210c187bb7885da7639de0434d815e59e7f32a`
그대로다** — slice의 커밋 집합은 range가 아니라 in_scope 경로의 변경이다
(2026-09-04 규율).

## 레인 혼입 — 이력을 되쓰지 않고 사실로 선언한다(2026-09-02 규율, 두 번째 발생)

r1 수정 중 4B-3 레인이 `a3822c4`를 자기 브랜치에 병합해 `m4/2026-09-08`·`main`이
`8b50461`로 이동했다(milestone-4.md 참고). **r2 시정 뒤 M2/2E 레인이 `4ec52db`를 자기
브랜치에 병합했고 그 커밋 `20f7ad0`으로 브랜치가 다시 이동했다**(clean merge, conflict
없음). 둘 다 되돌리지 않는다 — 세 worktree와 다른 세션이 그 커밋들을 참조 중이라 공유
이력을 되쓰는 비용이 더 크다. 하네스에 「진행 중 slice의 브랜치는 검증(ready-for-review)
뒤에만 병합한다」가 `f3f034f`로 성문화됐다(재발 방지).

## 목록 산출(기계적) 및 공유 판정 — **파일마다 다시** 계산했다(레인이 셋이 됐다)

```
git diff --name-status ff210c187bb7885da7639de0434d815e59e7f32a..HEAD   # 원본: A 58 · M 25
```

**절차 ①을 in_scope 파일마다 다시 돌렸다**: `git log --oneline <base>..HEAD -- <파일>`로
그 파일을 만진 모든 커밋을 나열하고 커밋 메시지의 slice 태그(`(m4-4c2)`/`(m4-4b3)`/
`(m2-2e)`/`harness:`)로 귀속을 갈랐다. 결과:

- **4C-2 전용(A 33 · M 5)** — 아래 표에서 전체 삭제/복원.
- **4C-2가 손대지 않은 4B-3 전용(A 6 · M 8)**·**2E 전용(A 19 · M 5)** — 건드리지 않는다.
- **진짜 공유(M 3)** — `config/quality/gate-tests.properties`(4C-2·4B-3·2E 셋 다) ·
  `docs/discovery/capability-map.md`(4C-2·2E) · `milestone-4.md`(4C-2·4B-3) — 커밋 해시
  hunk 격리.
- **제외(M 1)** — `reports/evidence/m4/4c2/scope.md`(아래).
- **하네스(M 3)** — `.claude/skills/**`·`CLAUDE.md`(`f3f034f`) — in_scope 밖, 되돌리지
  않는다.

33+5+3+1+(6+8)+(19+5)+3 = 83 = 원본 합계와 일치.

## 제외 — `reports/evidence/m4/4c2/scope.md`

이 파일의 M 편집 전부(`85a3835` 계약 갱신, `97af8bb`의 L-4, `4ec52db`의 L-7)는 **되돌릴
대상이 아니다** — 계약·장부 문서지 wiring이 아니다. **정정(L-8 시정)**: 이전 판은
「evidence 디렉토리 자체(`commands.md`·`rollback.md`·`differential.json`·
`golden-manifest.json`)도 같은 이유로 대상이 아니다」라고 적었으나 아래 A 목록에는 그
넷이 실제로 들어 있었다 — 문면과 명령이 어긋났다(verifier r2 L-8). **정정**: 그 넷은
**이 slice의 존재를 기록하는 자기 evidence**라 코드·wiring과 함께 되돌리는 것이 맞다
(4C-1 rollback.md 전례와 같다) — `scope.md` 하나만 「계약 문서」로 예외다.

## A(신규 33) — 전체 삭제

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
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/OutboxTransactionAtomicityTest.kt \
  adapters/src/test/resources/compile-fixtures/negative-1-event-envelope-ctor.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-2-transition-outbox.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-3-outbox-transition-to-delivered-ctor.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-4-outbox-entry-restore.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-5-new-envelope.kt.txt \
  adapters/src/test/resources/compile-fixtures/negative-6-for-strategy-updated.kt.txt \
  adapters/src/test/resources/compile-fixtures/positive-claimed-outbox-row-public.kt.txt \
  reports/evidence/m4/4c2/commands.md \
  reports/evidence/m4/4c2/differential.json \
  reports/evidence/m4/4c2/golden-manifest.json \
  reports/evidence/m4/4c2/rollback.md
```

## M(5, 이 range 안에서 4C-2 단독) — 전체 복원

```
git restore --source=ff210c187bb7885da7639de0434d815e59e7f32a --staged --worktree -- \
  adapters/build.gradle.kts \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcRawObservationStore.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt
```

## M(3, 진짜 공유) — 커밋 해시 hunk 격리

**`config/quality/gate-tests.properties`**(4C-2·4B-3·2E 셋 다 만진다) — 4C-2 커밋만
최신 것부터 역순으로:

```
git diff 70cbcdb~1..70cbcdb -- config/quality/gate-tests.properties | git apply -R --3way
git diff 28733bc~1..28733bc -- config/quality/gate-tests.properties | git apply -R --3way
```

**함정(r1·r2 둘 다 재현, 실측) — 두 번째 역적용이 자동으로 안 붙는다.** 4C-2와 4B-3의
주석 블록(`# M4/4C-2 —`·`# M4/4B-3 —`)이 파일의 **같은 삽입 지점**(`gate.tests.adapters=\`
바로 위)에 인접해 있어, `28733bc` 역적용이 conflict marker(`<<<<<<< ours` /
`=======` / `>>>>>>> theirs`)를 낸다(`--3way`도 자동 해소 못함). **수동 해소** — conflict
block 안에서 `# M4/4B-3 —` 문단은 남기고 `# M4/4C-2 —` 문단만 지운 뒤 marker 세 줄
제거, `git add`. 나머지(event.\* 아홉 줄·`CleanMigrationColumnTest,`·
`OutboxTransactionAtomicityTest,`)는 두 명령 모두 conflict 없이 지워진다.

확인(둘 다 실측) — 「내 줄 사라짐」: `bidvector.adapters.event.*`·
`CleanMigrationColumnTest`·`OutboxTransactionAtomicityTest`·`# M4/4C-2 —` grep **0건**.
「남의 줄 남음」: `bidvector.adapters.ml.UnavailableMlAnalysisTest`·`# M4/4B-3 —`·
`bidvector.adapters.contract.EmbeddingContractTest`·`EmbeddingTestdataCanonicalTest`·
`# M2/2E —` grep **전부 존재**.

**`docs/discovery/capability-map.md`**(4C-2·2E) — 4C-2 커밋 하나뿐:

```
git diff abf6b06~1..abf6b06 -- docs/discovery/capability-map.md | git apply -R --3way
```

conflict 없음(내 편집은 `OPEN-OPS-10` 행 한 줄 치환, 2E는 `OPEN-4D-LADDER-SCORE-SOURCE`
행과 신규 두 행 — 물리적으로 다른 행이라 겹치지 않는다). 확인: 「4C-2 처리」 문구
grep 0건, 2E가 넣은 `OPEN-2E-TEXT-SYNTHESIS`·`OPEN-2E-TEXT-MAX` 행 grep 존재.

**`milestone-4.md`**(4C-2·4B-3) — 4C-2 커밋 **다섯**, 최신 것부터:

```
git diff 3af06ad~1..3af06ad -- milestone-4.md | git apply -R --3way
git diff ef25a11~1..ef25a11 -- milestone-4.md | git apply -R --3way
git diff 673cbd5~1..673cbd5 -- milestone-4.md | git apply -R --3way
git diff 97af8bb~1..97af8bb -- milestone-4.md | git apply -R --3way
git diff abf6b06~1..abf6b06 -- milestone-4.md | git apply -R --3way
```

**verifier r3 M-5 시정 — `673cbd5`를 목록 최신 자리에 추가했다.** 이전 판(둘만 등재)은
`673cbd5`(이 rollback.md 자신을 담은 evidence 커밋)가 `milestone-4.md`의 레인 혼입
문단도 함께 만졌다는 사실을 놓쳤다 — 그 결과 문서대로 실행하면 `97af8bb` 역적용이
**conflict**(문서는 「conflict 없음」이라 적어 틀렸다)로 걸리고, 이어지는 `abf6b06`
역적용은 「인덱스에 없습니다」로 **시퀀스 자체가 끊겼다**(verifier r3 실측, 1A 16차와
같은 「명령이 실패」 계열). 셋을 최신 순으로 두면 셋 다 exit 0·marker 0으로 깨끗하다
(verifier r3가 실행 확인).

**뿌리** — 「이 rollback.md를 쓰는 evidence 커밋이 공유 파일도 함께 만졌다」는 사실을
그 커밋 자신의 hunk 목록에 반영하는 절차가 없었다. r2 때(`8f8e1e1`)는 그 커밋이
`milestone-4.md`를 안 건드려 둘짜리 목록이 우연히 맞았을 뿐이다(verifier r3 진단).

**종결 등재도 같은 함정을 지난다 — 그래서 두 커밋으로 나눴다.** 종결 승인 문단은
`milestone-4.md`(공유 파일)를 만지므로 이 목록에 들어가야 하는데, 자기 해시는 커밋 전에
알 수 없다. 그래서 ① 종결 문단 + `checklist.md`를 한 커밋(`ef25a11`)으로 올리고 ② 이
목록에 그 해시를 더하는 커밋은 **`reports/evidence/m4/4c2/**`만** 만진다. ②가 공유 파일을
건드리면 목록이 또 자기만큼 낡는다(M-5의 자기 재생산).
**항구 규칙**: rollback.md를 갱신하는 커밋을 만들 때, 그 커밋이 공유 파일도 함께
바꾸면 **그 커밋 해시를 그 공유 파일의 hunk 목록 최신 자리에 반드시 추가**한다 — 다음
라운드의 evidence 커밋이 공유 파일을 다시 건드리지 않는 한(이번 M-5 시정 커밋은
`reports/evidence/m4/4c2/**`만 바꾼다, 공유 파일 무접촉) 이 목록은 최종적이다.

셋 다 conflict 없음(문서 커밋의 레인 혼입 문장 추가는 4B-3 문단과 물리적으로 분리된
자리). 확인: 「4C-2 구현」·「4C-2 verifier」 문단 grep 0건, 「4B-3 종결 2026-09-10」
문단 grep 존재.

## 마이그레이션

**되돌리지 않고 V7로 무력화한다**(append-only, 3D 관례) — 위 A 목록의
`V6__outbox_inbox.sql` 삭제는 파일만 지우는 것이라 이미 적용된 DB에는 영향이 없다.

`git checkout <base> -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec 오류로
exit 1, 1A/4A/4C-1 선례).

## 확인 지점 — 임시 clone에서 실제로 실행(dry-run 아님, r3 시정 뒤 재실측)

1. 임시 clone(`git clone .`, HEAD `673cbd5` 이상)에서 A(33)·M(5, 단독) restore — 둘 다 exit 0.
2. `config/quality/gate-tests.properties` hunk 역적용 — `70cbcdb` exit 0(clean),
   `28733bc` conflict → 위 수동 해소 → `git add`.
3. `docs/discovery/capability-map.md` hunk 역적용 — exit 0(conflict 없음).
4. `milestone-4.md` hunk 역적용 **다섯(`3af06ad`→`ef25a11`→`673cbd5`→`97af8bb`→`abf6b06`, 최신 순)** — 전부
   exit 0(conflict 없음, M-5 시정).
5. `git status --porcelain` — **41건**(D 33 + M 5 + M 3).
6. 되돌린 트리 **모듈별 compile** — `:adapters:compileKotlin :adapters:compileTestKotlin`
   exit 0.
7. 되돌린 트리 **test** — `:adapters:test :app:test`(이번엔 4B-3·2E 산출물이 여전히
   남아 있으므로 app 도 함께 확인) exit 0.

## 되돌린 뒤 남는 것

`TransactionBoundary`·`ConnectionSource`·`bidvector.adapters.event` 패키지 전체·
V6 스키마·outbox/inbox 관련 test가 전부 사라지고, `JdbcRawObservationStore`는 4C-2 이전
(순수 `DataSource` 생성자 하나)로 복귀한다. `OPEN-4C1-TX-CONTRACT-UNVERIFIED`는 다시
**활성**으로 돌아간다. 4B-3의 산출물(`UnavailableMlAnalysis`·`LadderInput
.mlUnavailableReason`·milestone-4.md 4B-3 종결 문단·gate-tests.properties의
`ml.UnavailableMlAnalysisTest` 등재)과 2E의 산출물(`EmbeddingContractTest`·
`embedding.proto`·capability-map.md의 `OPEN-2E-*` 행·milestone-2.md 2E 절)은 **그대로
남는다** — 이번 시정(공유 파일 hunk 격리 재계산)의 목적이다. 3D의 다른 persistence
test·repository는 무영향(이 slice가 손대지 않았다).
