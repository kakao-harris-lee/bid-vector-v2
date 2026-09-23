# M6/6F-7 — commands.md

실측 HEAD(마지막 산출물 커밋): `e7e7a764`(D-6F7-7 — sink 를 `workflow.evaluation`
으로 이동한 뒤의 최종 상태)

## acceptance_commands (scope.md) — `contractGate` 환경 결함 해소 뒤 전건 재실측

**1라운드(구현 완료 시점)** — `contractGate`가 이 머신의 `~/.internal-bin/git`
래퍼(존재하지 않는 `/opt/homebrew/bin/git`을 가리킴 — Intel Homebrew 머신에
Apple Silicon 경로)로 실패했다. `git stash`로 이 slice의 변경을 전부 치운
상태에서도 동일하게 재현돼(`git stash && ./gradlew --no-daemon contractGate`
→ exit 1, 같은 git 래퍼 오류) **이 slice의 diff와 무관한 사전 존재 환경 결함**임을
확정했다. 팀장이 `~/.internal-bin/git`의 `exec` 경로를 `/usr/local/bin/git`으로
고쳤다(`~/.internal-bin/git --version` → `git version 2.38.0` 확인).

**2라운드(해소 뒤 재실측)** — 아래.

### 2026-09-23T10:33Z
- cmd: `./gradlew --no-daemon check`
- exit: **1**(첫 시도) → **아키텍처 순환 발견**(아래 절) → 수정 뒤 **exit 0**(최종)
- 핵심 결과: `contractGate`가 처음으로 `:app:test`까지 도달시켰고, 거기서
  `ArchitectureGateTest`(이전 라운드에는 `contractGate` 실패로 한 번도 실행된
  적이 없던 게이트)가 `workflow.event`를 지나는 순환 다섯 건을 냈다. 원인·수정은
  「아키텍처 순환 발견과 수정(D-6F7-7)」 절. 수정 뒤 재실행 **exit 0**.

### 2026-09-23T10:4xZ
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0

### 2026-09-23T10:4xZ
- cmd: `./tools/one-command-check.sh`
- exit: **1**(Python 설치 단계) — Kotlin 두 step(`check`·`qualityBaseline`)은
  **exit 0**로 끝까지 돎(이전 라운드는 `check`에서 막혀 여기 도달 못 함).
  Python 첫 step(`uv sync`, S-1)이 `pypi.org` 접속 시간초과로 실패
  (`Failed to fetch: https://pypi.org/simple/grpcio-tools/ ... operation
  timed out`) — 프록시(`HTTPS_PROXY=http://ai-proxy.onkakao.net:3128`,
  `pypi.org`는 `NO_PROXY`에 없어 프록시를 타야 정상)를 거쳐도 해당 목적지에
  도달하지 못하는 **외부 네트워크 문제**다. 이 slice는 `ml-engine`/Python을
  전혀 건드리지 않는다 — 무관.

## 아키텍처 순환 발견과 수정(D-6F7-7, 팀장 계약 갱신)

`contractGate` 해소 뒤 처음 `:app:test`에 도달하자 `ArchitectureGateTest`(app
모듈, ArchUnit 기반 패키지 순환 금지 — **이전 라운드는 한 번도 실행된 적이
없었다**, `contractGate` 실패가 먼저 죽어 도달 못 함)가 순환 다섯 건을 냈다.
공통 원인: `OutboxNotificationRequestPort`(당시 `workflow.event`)가
`NotificationRequest`·`PredictionEvidence`(`workflow.evaluation`)와 그
`diagnostics`/`release`(`workflow.prediction`)를 직접 참조해 **새 간선**
`workflow.event -> workflow.evaluation`·`workflow.event -> workflow.prediction`
을 만들었고, 이미 있던 반대 방향 간선(`workflow.evaluation -> workflow.event`
via `CorrelationId`, `workflow.evaluation -> workflow.prediction` via
`PredictionEvidence.Diagnosed` 필드, `workflow.prediction -> workflow.event`
via `BidPredictionRequest.correlationId`)과 합쳐져 순환이 닫혔다(파생 순환
셋은 `workflow.embedding`을 경유). 팀장이 `scope.md`에 **D-6F7-7**로
계약을 갱신했다 — sink 클래스만 `workflow.evaluation`으로 옮기고
(그 패키지는 이미 두 간선을 가져 새 간선이 없다), payload 타입
(`NotificationRequestedPayload`·`NotificationEvidencePayload`)은
`workflow.event`에 그대로 둔다(D-6F7-5의 codec 허용 루트 제약은 payload에만
해당). 구현 레인이 같은 진단·같은 수정으로 독립적으로 도달했다.

### 2026-09-23T10:4xZ
- cmd: (sink+test 파일을 `workflow.event`→`workflow.evaluation`로 이동, import
  정리, `gate-tests.properties` 등재 경로 갱신)
- 핵심 결과: `workflow:compileKotlin`·`workflow:compileTestKotlin` exit 0,
  `workflow:test`·`adapters:test`·`app:test` 전부 exit 0(순환 소멸 확인).

### 순환 재발 변이 실측(팀장 요청 — 버릴 clone)
- cmd: 버릴 clone에서 sink를 `workflow.event`로 되돌리고(패키지 선언·import
  원복, numstat으로 적용 확인) `./gradlew --no-daemon :app:test --tests
  "bidvector.app.architecture.ArchitectureGateTest"`
- exit: **1** — 정확히 같은 순환(`workflow.event -> workflow.prediction ->
  workflow.event` 등)이 재현됨. 검증 뒤 clone 삭제.

## 이 slice가 닿는 개별 gate 전건 — check의 나머지 전부

### 2026-09-23T10:4xZ
- cmd: `./gradlew --no-daemon :workflow:detekt :adapters:detekt :adapters:jarContentGate :adapters:sizeGate :workflow:sizeGate :workflow:runKtlintCheckOverMainSourceSet :workflow:runKtlintCheckOverTestSourceSet :adapters:runKtlintCheckOverMainSourceSet :adapters:runKtlintCheckOverTestSourceSet`
- exit: 0
- 핵심 결과: detekt(TooManyFunctions·MagicNumber·MaxLineLength) · jarContentGate
  (합성 클래스 SourceFile) · sizeGate(함수 50줄) · ktlint 전부 통과.

### 2026-09-23T10:4xZ
- cmd: `./gradlew --no-daemon :workflow:test :adapters:test :app:test`
- exit: 0
- 핵심 결과: `workflow`·`adapters`·`app`(아키텍처 게이트 포함) 전 test 통과
  (`OutboxNotificationRequestPortTest` 12건, `OutboxPayloadCodecTest` 10건,
  `EventInternalClosureCompileTest` 7건 포함 — 전건 0 failed).

### 2026-09-23T10:4xZ
- cmd: `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.WorkflowGateRegistrationTest" :adapters:test --tests "bidvector.adapters.event.EventGateRegistrationTest"`
- exit: 0
- 핵심 결과: `gate-tests.properties` 등재 완전성(양방향) 통과 — sink test의
  등재 경로도 `workflow.evaluation.OutboxNotificationRequestPortTest`로 갱신.

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

## 수정 라운드 1 — D-6F7-12(codec 구분자 이스케이프) TDD·변이 실측

RED → GREEN → 변이(버릴 clone) 순서. `excludedSamples` Map 직렬화가 구분자 둘
(`;`·`=`)을 쓰면서 키에서 `;`만 보호하던 결함(`code-reviewer` MEDIUM ·
`verifier` LOW-1)을 고쳤다 — `excludedSamplesField`가 키를 `MAP_KV_SEPARATOR`로
먼저 감싸고(안쪽 계층) 그 결과 전체를 `MAP_ENTRY_SEPARATOR`로 다시 감싼다
(바깥 계층, `FIELD_SEPARATOR`/`LIST_SEPARATOR`와 같은 중첩).

### 2026-09-23T02:00Z — RED
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.event.OutboxPayloadCodecTest"`
- exit: **1**
- 핵심 결과: 신설 test(`excludedSamples 키에 구분자 문자가 있어도 왕복된다 —
  D-6F7-12 이스케이프 실측`, 키 `"A=B;C"`)가
  `IllegalStateException: excludedSamples 항목 형식이 아니다: A=B;C=4`로 실패
  (`decodeExcludedSamples`의 `check(kv.size == 2)`, 실제 3개).

### 2026-09-23T02:03Z — GREEN(수정 뒤)
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.event.OutboxPayloadCodecTest"`
- exit: 0(12 test 전부 통과)

### 2026-09-23T02:05Z — 회귀 없음
- cmd: `./gradlew --no-daemon :adapters:test :workflow:test`
- exit: 0

### 변이 실측(버릴 clone, `git clone --no-hardlinks`, numstat 확인)
- 대상: `excludedSamplesField`를 수정 전 형태(키를 `MAP_ENTRY_SEPARATOR`로만
  감싸고 전체 join은 raw)로 되돌림
- numstat: `1  2`(수정 diff의 정확한 역)
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.event.OutboxPayloadCodecTest"`
- exit: **1** — 신설 test가 **수정 전과 동일한 예외**로 FAILED
  (`excludedSamples 항목 형식이 아니다: A=B;C=4`, 같은 스택). 원복 확인 후
  clone 삭제.

## aggregateVersion 실측(D-6F7-4)

- `grep -rn "aggregateVersion" workflow/src/main/kotlin adapters/src/main/kotlin` —
  일치 결과는 `EventEnvelope`·`ClaimedOutboxRow`·`OutboxEntry.restore`의 필드 왕복과
  `JdbcOutboxPort`의 컬럼 bind/read뿐이다. `EventSql.SELECT_PENDING_FOR_UPDATE_SKIP_LOCKED`
  는 `inserted_at`으로만 정렬하고 `WHERE` 절 어디에도 `aggregate_version`이 없다 —
  claim 순서·낙관적 잠금 어디에도 쓰이지 않는다는 것을 소스 전수로 확인. `0` 고정이
  실측과 일치한다(추가 조사 불필요, KDoc·`OutboxNotificationRequestPort.kt`에 근거 기재).
