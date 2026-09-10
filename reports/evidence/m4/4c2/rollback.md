# rollback.md — M4 / 4C-2

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬,
2026-09-04 개정). 하네스 경로(`CLAUDE.md`·`.claude/**`) 변경은 **0건**(`git log --oneline
ff210c187bb7885da7639de0434d815e59e7f32a..HEAD -- CLAUDE.md .claude/` 결과 없음, scope.md
「하네스 레인 변경」 절과 일치).

## 목록 산출(기계적) — `git diff --name-status <base>..HEAD`

```
git diff --name-status ff210c187bb7885da7639de0434d815e59e7f32a..HEAD
```

**A(신규) 21 · M(변경) 7**(`reports/evidence/m4/4c2/scope.md`의 M 1건은 목록에서 뺐다 —
아래 「제외」 참고). 라운드가 늘어 파일이 늘면 이 절차를 다시 돌린다.

## 공유 파일 겹침 점검 — 이번 range에서는 겹치지 않는다

```
git log --oneline ff210c187bb7885da7639de0434d815e59e7f32a..HEAD
```

결과: 이 slice의 커밋 여섯(`508d5de`·`426d137`·`270e310`·`b920b7e`·`28733bc`·`abf6b06`) +
착수 전 계약 갱신 커밋 `85a3835`(`reports/evidence/m4/4c2/scope.md` 단독 편집, 아래 참고)
뿐이다 — **다른 slice의 커밋이 이 range에 없다.** 그러므로 `config/quality/gate-tests
.properties`·`docs/discovery/capability-map.md`·`milestone-4.md`(공유 파일)도 이 range
안에서는 **4C-2만 손댔다** — 파일 전체 `git restore --source=<base>`로 되돌려도 다른
slice의 줄을 지우지 않는다(M3/M4 선례가 요구하는 「커밋 해시 hunk 격리」가 이 range에는
불필요 — 겹침 자체가 없다).

**단, 이 문서 작성 이후 다른 lane이 같은 브랜치에 커밋했다면 이 전제가 깨진다** — 되돌리기
직전에 위 `git log` 명령을 다시 돌려 range에 낯선 커밋이 없는지 재확인한다. 있으면 공유
파일 셋은 자기 커밋 해시로 hunk를 격리해 되돌린다(`git diff <sha>~1..<sha> -- <파일> |
git apply -R`, evidence-pack 스킬 「공유 파일은 줄 단위로 끝나지 않는다」).

## 제외 — `reports/evidence/m4/4c2/scope.md`

이 파일은 `git diff --name-status` 결과에 M으로 잡히지만 **되돌릴 대상이 아니다**. 그
편집(30줄 추가)은 착수 **전** 커밋 `85a3835`(Phase 2.5 설계 검토 귀결 — D-4C2-1~3 계약
갱신 + base 재고정)이 낸 것으로, 이 slice의 구현이 아니라 **승인된 계약 자체**다. base_sha
(`ff210c1`)가 `85a3835`보다 앞이라 `git diff --name-status`에는 잡히지만, 「이번 slice의
신규 wiring을 비활성화」의 대상이 아니다 — 계약 문서를 되돌리면 D-4C2-1~3 결정 자체가
사라진다. evidence 디렉토리 자체(`commands.md`·`rollback.md`·`differential.json`·
`golden-manifest.json`)도 같은 이유로 대상이 아니다(기록이지 wiring이 아니다).

## 되돌리는 명령

**A(신규 21) — 전체 삭제:**

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
  adapters/src/test/kotlin/bidvector/adapters/event/EventTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/JdbcEventIdFactoryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/JdbcInboxPortTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/JdbcOutboxPortTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxClaimConcurrencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxTransitionSqlTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/OutboxTransactionAtomicityTest.kt
```

**M(변경 7) — 전체 복원(이 range 안에서는 4C-2 단독 편집, 위 「공유 파일 겹침 점검」 참고):**

```
git restore --source=ff210c187bb7885da7639de0434d815e59e7f32a --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcRawObservationStore.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  milestone-4.md
```

**마이그레이션은 되돌리지 않고 V7로 무력화한다**(append-only, 3D 관례) — 위 A 목록의
`V6__outbox_inbox.sql` 삭제는 파일만 지우는 것이라 이미 적용된 DB에는 영향이 없다. 운영
DB에 V6가 이미 적용된 뒤 이 slice를 끄려면 별도 `V7__drop_outbox_inbox.sql`(또는 GRANT만
회수)을 새로 얹는다 — 이 rollback은 **코드 트리** 복원만 다룬다.

`git checkout <base> -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec 오류로
exit 1, 1A/4A/4C-1 선례).

## 확인 지점 — 임시 clone에서 실제로 실행(dry-run 아님)

commands.md에 실행 결과를 한 줄씩 남긴다.

1. 임시 clone(`git clone .` 또는 `git worktree add --detach` — `cp -r`로 연결 worktree를
   복제하지 않는다, 4D-1 실측)에서 위 restore 명령 둘이 **exit 0**.
2. `git status --porcelain`이 위 A 21 삭제 + M 7 변경 취소와 일치(A 항목이 전부 사라지고
   M 항목의 `git diff -- <경로>`가 빈 diff).
3. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:adapters:compileKotlin
   :adapters:compileTestKotlin`.
4. 되돌린 트리의 **test**가 초록 — `:adapters:test`(V6 마이그레이션도 없으므로
   outbox·inbox 관련 test 자체가 없다 — 3D 기존 test만 남아 그대로 통과해야 한다).

## 되돌린 뒤 남는 것

`TransactionBoundary`·`ConnectionSource`·`bidvector.adapters.event` 패키지 전체·
V6 스키마·outbox/inbox 관련 test가 전부 사라지고, `JdbcRawObservationStore`는 4C-2 이전
(순수 `DataSource` 생성자 하나)로 복귀한다. `OPEN-4C1-TX-CONTRACT-UNVERIFIED`는 다시
**활성**으로 돌아간다(capability-map.md의 이 slice 처리 문단도 되돌아가므로). 3D의 다른
persistence test·repository는 무영향(이 slice가 손대지 않았다).
