# M6/6F-1 — checklist.md

## 새 파일 ↔ in_scope 대조

`git diff --name-status c4d09cc..HEAD`(evidence 디렉터리 제외) 기계 산출:

| 상태 | 경로 | in_scope 대조 |
| --- | --- | --- |
| A | `adapters/src/main/resources/db/migration/V9__operator_strategy.sql` | scope.md 그대로 |
| A | `adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt` | scope.md는 원래 `adapters/persistence/` 경로였으나 **계약 갱신(2) D-6F1-7**로 패키지가 바뀌었다(아래 「scope 이탈과 정정」) |
| A | `adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt` | 위와 동일 |
| A | `adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt` | scope.md 그대로(패키지 무변경 — `PersistenceTestSupport` 상속 자리) |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt` | scope.md 그대로(「전략 SQL 추가만」) |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt` | **scope 밖 발견** — 아래 「scope 밖 필연적 companion」 |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt` | **scope 밖 필연적 companion** |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt` | 위와 동일 |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt` | 위와 동일 |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt` | 위와 동일 |
| M | `milestone-6.md` | 팀장 커밋(착수 계약 고정, scope.md 명시) |

`PersistenceAdapterDependencyTest.kt`는 커밋 히스토리에는 나타나지만(임시로 열었다가
계약 갱신 뒤 원복) base 대비 순 diff는 **없다** — `git diff c4d09cc..HEAD --
adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceAdapterDependencyTest.kt`
가 비어 있다(왕복 실측, `git status`가 아니라 base 대비 diff로 확인).

### 재산출 — base `8652893`(계약 갱신 (7), verifier r2 LOW-2 정정)

위 표는 rebase 이전 base(`c4d09cc`) 기준이라 **이제 낡았다** — 지우지 않고 아래에 새 base
기준 재산출을 덧붙인다. `git diff --name-status 8652893..HEAD -- . ':!reports/evidence'`
(2026-09-18, 이 라운드의 마지막 내용 커밋 뒤 재산출):

```
M  adapters/src/main/kotlin/bidvector/adapters/persistence/PersistenceJdbcSupport.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/ProvenanceCodec.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A  adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt
A  adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt
A  adapters/src/main/resources/db/migration/V9__operator_strategy.sql
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt
M  milestone-6.md
```

옛 표 대비 **둘이 달라졌다**:
- **`ProvenanceCodec.kt`(M) 가 새로 나타난다** — 이번 라운드(D-6F1-8)의 편집. scope.md
  in_scope 에는 이미 계약 갱신 (5)로 등재돼 있었으나 **이 표에는 verifier r2 LOW-2 전까지
  빠져 있었다** — 반영한다.
- **`StrategyAdapterDependencyTest.kt` 가 더 이상 나타나지 않는다** — 옛 표에도 없었지만
  이유가 다르다: 옛 base 시점엔 「추가했다 철회해 순 diff 0」(위 「scope 이탈과 정정」)였고,
  새 base(`8652893`)는 **6B-1 의 병합으로 그 파일을 이미 담고 있어** 이 slice 쪽 diff 에
  안 잡힌다(rebase 가 add/add 를 6B-1 판으로 채택 — scope.md 「병합 계획」 절 rebase 실측).
  `PersistenceAdapterDependencyTest.kt` 는 여전히 순 diff 0(왕복 확인 재실측, 변화 없음).

이 12 는 scope.md in_scope 열넷(신설 `ProvenanceCodec.kt` 포함) 전부에 걸린다.

## scope 밖 필연적 companion(구현 중 발견, 오케스트레이터 보고 대상)

**`PersistenceJdbcSupport.kt`·`CleanMigrationTest.kt`·`CleanMigrationColumnTest.kt`·
`CleanMigrationCheckTest.kt`·`PersistenceTestSupport.kt`는 scope.md의 in_scope 목록에
문자 그대로 없다.** 다음 이유로 편집이 구조적으로 불가피했다 — 이 다섯 파일을 건드리지
않고는 acceptance S-10(`check` 전건)이 성립할 수 없다(정의상 통과 불가능한 계약이
아니라, M3/3E·M4/4C-2가 새 표를 추가할 때마다 반복해 온 「스키마 스냅샷 래칫 예외
—추가만」 패턴의 다섯 번째 반복이다):

1. `CleanMigrationTest`(축1 테이블·축5b PK·축9 권한 행렬)·`CleanMigrationColumnTest`
   (축2·3·4 컬럼)·`CleanMigrationCheckTest`(축8 CHECK 개수)는 **exact-match**
   (`shouldContainExactlyInAnyOrder`/`shouldBe`) 단언이다 — V9가 새 표 둘을 만들면 이
   세 test는 그 표를 반영하지 않는 한 항상 실패한다(선택이 아니라 필연).
2. `PersistenceTestSupport`의 `truncateAllTables`가 새 표를 비우지 않으면 싱글턴 행이
   test 간에 새어 "전략 없음" test가 오염된 상태를 본다(M4/4C-2가 outbox·inbox에 쓴
   것과 같은 필연).
3. `PersistenceJdbcSupport.kt`의 `setTextArray`/`getTextList` 추가는 순수 추가(기존
   함수 무편집) — `TEXT[]` 바인딩이 필요한 두 표를 위해 `OpeningCompleteAxisCodec`의
   `createArrayOf("integer", ...)` 관용구를 확장한 것.

편집 전부 **추가만**(기존 값·assertion을 좁히거나 삭제하지 않음)이고, 실측(commands.md)
으로 각 test가 정상 통과함을 확인했다. 정본 scope.md는 이 다섯 파일을 명시하지 않으므로
오케스트레이터에게 **사후 등재 승인**을 요청한다(운영자 지시 2026-09-04 사후 승인 관례,
D-6F1-1이 이미 「스키마 스냅샷 래칫 예외」를 원칙으로 세워 뒀다).

## scope 이탈과 정정 — D-6F1-7(계약 갱신 (2))

구현 중 `PersistenceAdapterDependencyTest`(3D 원안)가 `persistence` 패키지의
`strategy`·`workflow` 참조를 전면 금지함을 발견했다(`JdbcStrategyRepository`가 그 둘을
모두 참조해야 하는 D-6F1-2 설계와 정면 충돌). 최초에는 그 게이트의 허용 목록을 넓히는
방향으로 커밋했으나(`76b3902`), 그 직후 오케스트레이터가 scope.md에 **계약 갱신(2)
D-6F1-7**을 커밋했다(`a3a90d6`) — 6B-1 레인(다른 worktree)이 세션 저장소에서 **같은
벽**을 먼저 만나 `adapters.strategy`라는 새 패키지 + 전용 의존 게이트로 처분한 것을
인계한다는 내용이었다. 그 지시에 맞춰:

- `76b3902`을 새 커밋(`91e386a`)으로 원상 복구했다(이력을 되쓰지 않는다 — git revert
  대신 원본 내용을 다시 커밋).
- `JdbcStrategyRepository.kt`·`StrategyRow.kt`를 `adapters.strategy` 패키지로 옮겼다
  (`bd00c5e`·`b7d2265`).
- 6B-1의 `StrategyAdapterDependencyTest.kt`(worktree `bid-vector-v2-m6b`, 읽기 전용
  참고)를 처음에는 바이트 단위로 동일하게 가져왔으나(`682e03e`), 팀장 조율 메시지가
  **소유권을 6B-1로 확정**해(그 레인이 이미 그 파일을 만들어 병합 대기 중) 그 커밋을
  `d9fda0a`로 철회했다 — 이 slice의 branch에는 이제 그 게이트 파일이 없다(아래
  「의존 게이트 병합 전 대조」).

이 왕복(패키지 이동·게이트 신설·게이트 철회) 전부가 커밋 이력에 그대로 남아 있다 —
되돌리거나 rebase하지 않았다(운영자 지시 「이력은 되쓰지 않는다」).

## 의존 게이트 병합 전 대조(팀장 조율 — 소유권 6B-1)

이 branch의 `check`는 `StrategyAdapterDependencyTest`를 **돌리지 않는다**(파일이
없다 — 병합 뒤 `main`에서 6B-1의 파일과 함께 처음 돈다, 알려진 제한). 병합 전에
버릴 임시 clone에서 6B-1 worktree(`bid-vector-v2-m6b`, 읽기 전용 참고)의 게이트
파일만 복사해 실측했다:

- `./gradlew --no-daemon :adapters:test --tests '*StrategyAdapterDependencyTest*' --rerun-tasks` → exit 0
- `./gradlew --no-daemon :adapters:check` → exit 0

6B-1의 허용 목록(`workflow.strategy`·`strategy`·`sharedkernel`·`adapters.persistence`)이
이 slice의 실제 import(`bidvector.workflow.strategy.{AppliedStrategy,StrategyRepository}`·
`bidvector.strategy.{OperatorStrategy,StrategyDraft,StrategyPolicyData,StrategyRevision,
StrategyValidation,StrategyViolation,validate,CategoryCode}`·`bidvector.sharedkernel.*`·
`bidvector.adapters.persistence.{Sql,ProvenanceCodec,getTextList,setNullableInt,
setTextArray}`)를 전부 덮는다 — **추가 좌표 요청 없음**.

**rebase 뒤 사실 갱신(verifier r2 LOW-2)**: 위 절은 병합 **전** 상태의 기록이라 지금은
낡았다 — 지우지 않고 사실을 덧붙인다. rebase(2026-09-18)로 6B-1 이 병합해 둔
`StrategyAdapterDependencyTest.kt` 가 **이제 이 branch 의 트리에 있고 `check` 가 실제로
돌린다**(「병합 전 대조 실측만 있었다」가 아니라 그 자리에서 D-6F1-8 결함을 실제로 잡았다
— scope.md D-6F1-8·완료 보고 참고). 아래 「알려진 제한」의 관련 두 항목도 같은 이유로
갱신한다.

## (2b) 값 획득 축 — 실측 대응표

| 표면 | scope.md 판정 | 실측 |
| --- | --- | --- |
| `JdbcStrategyRepository`(public class) | 경계로 처리 | 생성자는 `DataSource`·`Resolution.Resolved<StrategyPolicyData>`만 받는다(javap 확인, commands.md) — 도메인 값을 만들지 않는다 |
| `StrategyRow`(행 → 초안) | 닫는다 | `internal` — 소스 컴파일 차단 실측(변이 ⓐ, commands.md). 도메인 생성자 호출 0(grep, commands.md) |
| 검증 함수 재사용(`validate`) | 경계로 처리, 거부하지 않으면 HIGH | `정책 불일치` test가 실제 거부를 실측했다 — 허용 policy로 저장한 무효 후보값을 더 좁은 policy로 `load()`하면 `Invalid`가 나오고 그 위반이 그대로 예외로 전파된다(commands.md) |

## 알려진 제한

- **동시 편집 충돌 무방어**(scope.md 위협 모델 「방어하지 않는다」) — `save()`는 단일
  트랜잭션 upsert일 뿐 낙관적 잠금이 없다. 전략 쓰기 직렬화는 편집 세션(6B-1)의 책임.
- **개정 이력 보존·파기 무구현** — `operator_strategy_revision`은 append-only로 계속
  자란다. 삭제·보관 정책은 6B-3 소관(scope.md 위협 모델과 동일 경계).
- **app 조립 미배선** — `JdbcStrategyRepository`를 실제로 생성해 `EvaluateCandidatesUseCase`
  ·`EditStrategyWorkflow`에 주입하는 자리는 이 slice 밖(`OPEN-6F-ASSEMBLY`, scope.md 그대로).
- **`StrategyAdapterDependencyTest`를 이 branch의 `check`가 돌리지 않는다** — ~~병합 전
  시점의 서술~~. **verifier r2 LOW-2로 갱신(거짓이 됨)**: rebase(2026-09-18)로 이 파일이
  이제 트리에 있고 `check`가 실제로 돌린다 — 병합 전 대조(위 절)는 예측이었고, rebase
  뒤 그 예측대로 통과했을 뿐 아니라 **이 라운드의 D-6F1-8 의존 위반을 실제로 잡았다**
  (2/3 → 수정 후 3/3, scope.md D-6F1-8·완료 보고 참고). 남는 위험은 이 게이트 자신의
  허용 목록이 **앞으로** 좁혀질 가능성뿐이고, 그것은 이 slice가 통제할 수 없는 6B-1
  소유 파일의 몫이다.
- **마이그레이션 병합 순서** — 팀장 조율: 기본 순서는 6B-1(V8) → 6F-1(V9, 이 slice) →
  6A-1(V10)이다(Flyway `outOfOrder`가 이 저장소에 없어 기본값 `false` — 낮은 번호를
  나중에 적용하면 거부된다). ~~이 문서 작성 시점(base `c4d09cc`) 기준 `main`의 최신은
  V7이고 이 slice의 파일명은 V9다(V8은 6B-1 몫으로 미리 비워 둔 자리)~~ — **verifier r2
  LOW-2로 갱신(거짓이 됨)**: rebase 뒤 `main`(`8652893`)에 6B-1의 **V8이 이미 있다**.
  이 slice는 예정대로 **V9**를 유지했고(계약 갱신 그대로), Flyway가 V1~V9를 순서대로
  적용함을 빈 컨테이너로 실측했다(verifier r2 §2①). **6A-1(V10)과의 병합 시점에 `main`의
  최신 번호를 다시 확인해야 한다**는 원래 문장은 여전히 유효하다.
- **MEDIUM-1(verifier r2)** — `PersistenceJdbcSupport.kt`의 `getTextList`가 `TEXT[]`의
  NULL 원소를 조용히 버린다(`{서울, NULL, 경기}` → `[서울, 경기]`). 빈 문자열은
  `BlankTerm` 위반으로 크게 실패하는데 NULL은 통과해 비대칭이다. 이 slice의 writer는
  NULL 원소를 만들지 않아 시스템 내부 경로로는 도달 불가 — DB 직접 쓰기·복원·후속
  writer에서만 발생한다. 코드는 고치지 않는다(팀장 지시 — 등재만).

## MEDIUM-2 처분(verifier r2) — 이력 표 payload 컬럼 왕복 test 신설

`operator_strategy_revision`의 payload 컬럼 스물이 `revision` 말고는 아무 test 도
되읽지 않았다(verifier MUT-8: `INSERT_STRATEGY_REVISION`의 컬럼 두 자리를 바꿔도
`:adapters:test`가 전건 초록이었다). **이 라운드에서 닫는다** — 새 읽기 코드가
필요 없었다: `JdbcStrategyRepositoryTest`(`PersistenceTestSupport` 상속, 기존
`historyRevisions` 단언과 같은 자리)가 **이미 원시 JDBC로 이력 표를 직접 SELECT 하는
관례**를 갖고 있어(`SELECT revision FROM operator_strategy_revision ...`), 그 관례를
그대로 확장해 `min_budget_currency`·`min_budget_vat`·`candidate_limit`·
`bid_now_threshold` 네 컬럼을 revision=2 행에서 되읽어 두 번째 저장값과 대조하는
단언을 test ⓑ에 추가했다(`min_budget_currency`·`min_budget_vat`을 나란히 확인해
MUT-8이 심은 「두 컬럼 자리가 바뀌어도 통과」를 직접 겨눈다). **production 코드는
편집하지 않았다** — `JdbcStrategyRepositoryTest.kt`(test-only) 한 파일. 이 편집은
게이트 술어를 넓히는 test 추가라 표적 재검증 대상이다(팀장 지시, 아래 완료 보고의
전건·표적 재실측이 그 대상).

## OPEN 처분

scope.md가 신설한 OPEN 다섯(`OPEN-6A-EVALUATION-ADAPTERS`·`OPEN-6F-ASSEMBLY`·
`OPEN-6F-WATCH-TEXT-SOURCE`·`OPEN-6F-ACTIVE-BID-DEFINITION`·`OPEN-4B6-PROFILE-SOURCE`
·`OPEN-STR-12`)는 이 slice가 변경하지 않는다 — scope.md 표 그대로 유지.

## 재활용(reuse) — N/A

이 slice는 legacy Python bid-vector에 대응 기능이 없다(전략 편집·영속은 V2 신설
capability, `bid-vector` symlink에 상응 코드 없음). `ProvenanceCodec`·
`PersistenceJdbcSupport`·`OpeningCompleteAxisCodec`의 배열 바인딩 관용구를 재사용해
새 구현을 최소화했다(중복 금지 원칙 — legacy 재활용이 아니라 V2 내부 재사용).

## golden-manifest — N/A

fixture corpus를 소비하지 않는다 — 왕복 test는 합성 도메인 값(`StrategyDraft` 리터럴)만
쓴다. `fixtures/manifest.yaml` 갱신 없음.

## 정책 값 근거

정책 데이터는 기존 `bidvector.strategy.STRATEGY_POLICY`(1E, 형태만 확정된 자리표시자
`[0,1]`)를 test가 그대로 재사용한다 — 새 정책 값·새 키를 만들지 않았다(scope.md
out_of_scope 그대로).
