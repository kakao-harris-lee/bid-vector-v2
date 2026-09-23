# M6/6F-7 — commands.md

실측 HEAD(마지막 산출물 커밋): `b016fd30`(BidNowReason·MlUnavailableReason
toString 축어 잠금 test 포함 — 아래 전건 test·gate 재실측에 들어 있다)

## acceptance_commands (scope.md)

### 2026-09-23T00:xx:xxZ
- cmd: `./gradlew --no-daemon check`
- exit: **1** — `:contractGate`만 실패, 그 밖은 전부 통과
- 핵심 결과: 실패 원인은 `~/.internal-bin/git`(이 머신의 git 래퍼)가 존재하지 않는
  `/opt/homebrew/bin/git`을 가리켜 `buf breaking`의 내부 `git clone`이 exit 126로
  죽는 것 — **이 slice의 diff와 무관한 환경 결함**(아래 「알려진 제한」 참고). 같은
  실패가 `git stash`로 이 slice의 미커밋 변경을 전부 치운 상태에서도 동일하게
  재현되어(실측, 아래) 사전 존재 확인됨.

### 2026-09-23T00:xx:xxZ
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: `contractGate`를 타지 않는 task라 그대로 통과.

### 2026-09-23T00:xx:xxZ
- cmd: `./tools/one-command-check.sh`
- exit: 1
- 핵심 결과: 첫 step(`./gradlew --no-daemon check`)에서 위와 같은 이유로 중단
  (`set -euo pipefail`). Python(ml-engine) 단계는 도달하지 못함 — 이 slice가
  Python을 건드리지 않으므로 무관.

## contractGate 환경 결함 — 사전 존재 확인

### 2026-09-23T00:xx:xxZ
- cmd: `git stash && ./gradlew --no-daemon contractGate` (이 slice의 미커밋 변경을 모두 치운 상태)
- exit: 1
- 핵심 결과: 동일한 git 래퍼 오류로 실패 — **이 slice의 diff가 원인이 아님**을 확정.
  `git stash pop`으로 복원.

## 이 slice가 닿는 개별 gate 전건 — check의 나머지 전부

### 2026-09-23T00:xx:xxZ
- cmd: `./gradlew --no-daemon :workflow:detekt :adapters:detekt :adapters:jarContentGate :adapters:sizeGate :workflow:sizeGate :workflow:runKtlintCheckOverMainSourceSet :workflow:runKtlintCheckOverTestSourceSet :adapters:runKtlintCheckOverMainSourceSet :adapters:runKtlintCheckOverTestSourceSet`
- exit: 0
- 핵심 결과: detekt(TooManyFunctions·MagicNumber·MaxLineLength) · jarContentGate
  (합성 클래스 SourceFile) · sizeGate(함수 50줄) · ktlint 전부 통과.

### 2026-09-23T00:xx:xxZ
- cmd: `./gradlew --no-daemon :workflow:test :adapters:test`
- exit: 0
- 핵심 결과: `workflow`·`adapters` 전 test 통과(신설 `OutboxNotificationRequestPortTest`
  12건, 확장된 `OutboxPayloadCodecTest` 10건, 확장된 `EventInternalClosureCompileTest`
  7건 포함 — 전건 0 failed).

### 2026-09-23T00:xx:xxZ
- cmd: `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.WorkflowGateRegistrationTest" :adapters:test --tests "bidvector.adapters.event.EventGateRegistrationTest"`
- exit: 0
- 핵심 결과: `gate-tests.properties` 등재 완전성(양방향) 통과.

## 비밀값 스캔

### 2026-09-23T01:16:47Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 8개 개별 인자> reports/evidence/m6/6f7/`
- exit: 1 (매치 없음 = 통과)
- 핵심 결과: in_scope 산출물·evidence 디렉터리 모두 매치 없음.

## 우회 변이 실측(팀장 요청 — 버릴 clone, `git clone --no-hardlinks`)

전부 `/tmp` 버릴 clone에서 실행, 변이 적용은 `git diff --numstat`로 먼저 확인한 뒤
test를 돌렸다. 결과는 모두 RED(기대대로), 검증 뒤 clone 삭제.

| # | 우회 | 심은 변이 | numstat | RED 확인 |
| --- | --- | --- | --- | --- |
| 3 | 쓰기 실패 삼킴 | `OutboxNotificationRequestPort.request`의 `catch` 블록이 내는 `Failed`를 `Requested`로 치환 | `1  1` | `OutboxNotificationRequestPortTest`의 "SQLException 으로 실패하면 Failed" 1건 FAILED(`expected:<Failed> but was:<Requested>`) |
| 6 | payload가 판정을 복원 못함 | `OutboxPayloadCodec`의 `EVIDENCE_TRAILING_SLOT_INDEX`를 `EVIDENCE_FIELD_COUNT - 1`에서 `- 2`로(off-by-one) | `1  1` | `OutboxPayloadCodecTest`의 Diagnosed·NotPredicted·이스케이프 왕복 3건 FAILED(`excludedSamples`/`reason` 유실) |
| 7 | codec이 다른 payload 타입을 잘못 해석 | `decode`의 `NOTIFICATION_REQUESTED_TYPE` 분기를 `decodeStrategyUpdated` 호출로 치환 | `1  1` | 4건 FAILED — fail-closed `IllegalStateException`("StrategyUpdated payload 형식이 아니다") 또는 값 불일치 |
| 1 | 판정 없이 알림 요청(타입 폐쇄 회귀) | `workflow/.../evaluation/Ports.kt`의 `NotificationRequest`에서 `internal` 제거 + 이제 불필요해진 `@ConsistentCopyVisibility` 제거(그대로 두면 `-Werror`로 컴파일 자체가 막혀 폐쇄 회귀와 구분이 안 됨) | `1  2` | `EventInternalClosureCompileTest`의 신설 케이스 7 FAILED(`expected:<COMPILATION_ERROR> but was:<OK>`) — 폐쇄가 사라지면 이 test가 그것을 놓치지 않는다는 것을 확인 |
| 4 | payload 직렬화 형식이 조용히 바뀜(2라운드, 팀장 지적) | `MlUnavailableReason.TransportFailed`에 `override fun toString() = "TRANSPORT_FAILED_V2"` 추가(이름은 안 바꿈 — 더 현실적인 "형식만 조용히 바뀜" 시나리오) | `2  1` | `MlUnavailableReason 전 case` test 1건 FAILED(`expected:<TransportFailed> but was:<TRANSPORT_FAILED_V2>`) |
| 4 | 〃 | `BidNowReason.ForceBidOverride`에 같은 형태로 `"FORCE_BID_OVERRIDE_V2"` override 추가 | `3  1` | 2건 FAILED(`payload 는 ForceBidOverride 사유도 나른다` · `BidNowReason 두 case 의 직렬화가 축어로 고정된다`) |

우회 2(같은 판정 이중 요청)는 D-6F7-6 결정대로 **닫지 않는다** — 변이 실측 대상이
아니다(닫을 구조가 없다). 대신 `OutboxNotificationRequestPortTest`의
"idempotencyKey 는 noticeId 로 고정된다" test가 **이중 요청이 실제로 outbox에 둘 다
들어간다**(`registered shouldHaveSize 2`)는 사실을 양성으로 고정한다 — 「막았다」고
잘못 적히는 것을 그 test 자체가 막는다.

우회 5(배달 주장 문면)는 mutation 대상이 아니다(코드가 아니라 문면의 축) — KDoc·
커밋 메시지 육안 확인으로 처분(checklist.md).

## 경계 test 범위 실측(팀장 질문 — test 소스도 보는가)

`EventBoundaryTest`(`workflow/src/test/kotlin/bidvector/workflow/event/EventBoundaryTest.kt`)
의 `sourceRoot`는 `File("src/main/kotlin/bidvector/workflow/event")`다(소스 확인) —
**test 소스는 스캔 대상이 아니다.** 실측: `OutboxNotificationRequestPortTest.kt`가
`bidvector.decision.BidNowReason`·`MlUnavailableReason`·`Verdict`·`VerdictLadder`
·`VerdictLadderPolicyData`를 이미 자유롭게 import하고, `:workflow:test`가 매번
통과한다(위 표) — main 소스에서만 막힌다는 것이 실행으로 확인된다.

## aggregateVersion 실측(D-6F7-4)

- `grep -rn "aggregateVersion" workflow/src/main/kotlin adapters/src/main/kotlin` —
  일치 결과는 `EventEnvelope`·`ClaimedOutboxRow`·`OutboxEntry.restore`의 필드 왕복과
  `JdbcOutboxPort`의 컬럼 bind/read뿐이다. `EventSql.SELECT_PENDING_FOR_UPDATE_SKIP_LOCKED`
  는 `inserted_at`으로만 정렬하고 `WHERE` 절 어디에도 `aggregate_version`이 없다 —
  claim 순서·낙관적 잠금 어디에도 쓰이지 않는다는 것을 소스 전수로 확인. `0` 고정이
  실측과 일치한다(추가 조사 불필요, KDoc·`OutboxNotificationRequestPort.kt`에 근거 기재).
