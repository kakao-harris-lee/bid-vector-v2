# M6/6F-7 — 알림 요청 → outbox (2026-09-23)

```yaml
milestone: M6
slice: 6f7-notification-request-outbox
base_sha: 86093972   # main (6A-1 병합 뒤 + 배선 순서 문서)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/event/**        # 알림 payload 타입 + sink (봉투 생성이 workflow 에 internal 로 닫혀 있다, D-6F7-1)
  - adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt  # payload 분기 추가만
  - workflow/src/test/kotlin/bidvector/workflow/event/**        # sink·payload 왕복·우회 게이트
  - adapters/src/test/kotlin/bidvector/adapters/event/**        # codec 왕복
  - config/quality/gate-tests.properties                        # 신설 게이트 test 등재(추가만) — 공유 파일
  - reports/evidence/m6/6f7/**
  - milestone-6.md                                              # 6F-7 착수·결과 문단(팀장 커밋)
out_of_scope:
  - 실 발송·렌더링·채널 선택            # `OPEN-STR-12`. 이 slice 는 **요청을 outbox 에 넣는 자리까지**다
  - 마이그레이션                        # **불필요**(D-6F7-1) — outbox(V6)가 `payload_type`/`payload` 짝으로 payload 종류에 무관하다
  - `JdbcOutboxPort`·`EventSql`         # **무편집 재사용**. payload 무관 범용이다
  - outbox 의 `idempotency_key` UNIQUE  # 표에 없다 — **4C-1 의 기존 한계**이고 이 slice 계약 밖이다(D-6F7-3)
  - app DI 조립                         # `OPEN-6F-ASSEMBLY` — 포트 셋이 다 선 뒤 전용 slice
  - `EvaluateCandidatesUseCase` 배선     # 같은 이유
acceptance_commands:
  - "./gradlew --no-daemon check"                                              # S-10 — 전건(CI Kotlin job 그대로)
  - "./gradlew --no-daemon qualityBaseline"                                    # S-11
  - "./tools/one-command-check.sh"                                             # S-20 — Kotlin + Python 전건
rollback: |
  in_scope 경로 한정. **base 는 고정 SHA 가 아니라 정의**다 — 「이 브랜치가 분기해 나온 현재 main」
  = `$(git merge-base HEAD origin/main)`(6A-1 D-6A1-15 계승. 고정 SHA 는 main 이 전진하면 낡는다).
  목록·수치는 라운드마다 재산출하고 **정본은 `reports/evidence/m6/6f7/rollback.md`** 다.
  **마이그레이션 비대칭 없음** — 이 slice 는 스키마를 만들지 않는다.
```

작성: 2026-09-23, 세션 모델 단독. 근거: `milestone-6.md` 「M6 잔여 해소와 배선 — 실측 지도와 순서」(팀장,
2026-09-23) · 6F 표의 `6F-7` 행(「요청·의도 타입 연결, 발송 채널은 `OPEN-STR-12`」) · preflight 조사
`_workspace/m6-6f7/01_preflight.md`.

## 이 slice 가 여는 것

`EvaluateCandidatesUseCase` 의 포트 아홉 중 **셋이 비어 있고** 그중 하나가 `NotificationRequestPort` 다.
이 slice 가 그 production 구현을 낸다. **배선 순서의 1번**이고 차단이 없다(outbox 기반이 V6 로 이미 있다).

## 착수 조사가 바꾼 것 — 봉투가 모듈에 갇혀 있다

`EventEnvelope` 생성 경로(`newEnvelope`·`forStrategyUpdated`)가 **`workflow` 모듈에 `internal` 로 폐쇄**돼
있고 `EventInternalClosureCompileTest` 가 그것을 상시 확인한다. `adapters` 는 **별도 Gradle 모듈**이라 그
경로를 부를 수 없다. 그러므로 **봉투를 짓는 코드는 `workflow` 안에 있어야** 한다(선례 `OutboxEventSink`).

**따라오는 결과 둘** — ① **신설 어댑터 패키지가 불필요**하다(D-6F5-6 의 「패키지 신설 = 게이트 한 벌」이
발동하지 않는다) ② **마이그레이션이 불필요**하다.

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F7-1** | sink 와 payload 타입은 **`workflow` 모듈**에 둔다. `adapters` 쪽 변경은 **`OutboxPayloadCodec` 분기 하나**뿐이다. **신설 패키지 없음 · 마이그레이션 없음** | 위 조사. 봉투 생성이 `workflow` 에 `internal` 이고 컴파일 test 가 그것을 잠근다 — **모듈 경계가 이미 설계를 정해 놓았다.** outbox(V6)는 `payload_type`/`payload` 짝이라 payload 종류에 무관하다 |
| **D-6F7-2**(미결 A — **조사 추천과 반대로 간다**) | payload 는 **`PredictionEvidence` 를 싣는다.** 최소 사실(noticeId·correlationId·reason 코드)만 싣지 **않는다** | **팀장 실측: V2 에 판정 기록 표가 없다.** 표 18개를 전수했고 판정·투찰 기록에 해당하는 것이 **하나도 없다**(`OPEN-6F3-BID-RECORD` 가 그래서 열려 있다). 그러므로 **outbox 행이 그 판정의 유일한 영속 흔적**이고, evidence 를 빼면 **영구히 복원 불가**다 — 조사 레인도 그 위험을 스스로 적었다. evidence 는 **비-PII 수치**(조사 실측)라 싣는 비용이 낮다. 「요청을 낳는 자리까지」는 **범위**에 대한 말이지 **정보를 버리라**는 말이 아니다. **되돌릴 수 없는 손실 쪽으로 기울이지 않는다** |
| **D-6F7-3**(미결 B) | `idempotencyKey` 는 **`noticeId` 고정**(`OutboxEventSink` 의 「업무 사실 단위 키」 관례 계승). **그러나 「멱등하다」를 주장하지 않는다** — outbox 에 `idempotency_key` UNIQUE 가 **없어** 이 키는 **권고적**이다 | 조사 추천 (a). UNIQUE 부재는 **4C-1 의 기존 한계**이고 **이 slice 가 만든 것도, 고칠 계약도 아니다.** 알려진 제한에 그대로 적고 **전칭 문면을 쓰지 않는다** — 6A-1 이 다섯 번 겪은 「보장을 지지 않는데 졌다고 적혀 있다」를 되풀이하지 않는다 |
| **D-6F7-4** | `aggregateId` = `noticeId` 기반, `aggregateVersion` = **0**. 단 **구현 레인이 `aggregateVersion` 의 실제 용법을 실측**하고, 낙관적 잠금·순서에 쓰인다면 **그 용법에 맞춘 값**으로 바꾼 뒤 근거를 evidence 에 적는다 | 알림 요청에는 낙관적 버전 개념이 없다. **다만 「없다」를 단정하지 않는다** — 이 slice 가 배운 것이 「논증은 실측을 갈음하지 못한다」다. 봉투는 그 값을 싣고 `JdbcOutboxPort` 가 읽고 쓴다 |
| **D-6F7-5** | 새 payload 타입을 **`bidvector.workflow.event`** 에 둔다 | 조사 실측: `EventAdapterDependencyTest` 의 허용 루트가 다섯이고 **`workflow.evaluation` 이 없다.** payload 타입을 거기 두면 codec 이 그것을 참조하는 순간 **의존 게이트가 막는다.** `workflow.event` 는 허용 루트다 |
| **D-6F7-6** | 우회 2(같은 판정 이중 요청)를 **구조로 닫지 않는다.** 대신 **전칭 문면을 쓰지 않는 것**이 처방이다 — 이 slice 는 「요청이 outbox 에 든다」만 주장하고 **「두 번 들지 않는다」를 주장하지 않는다** | 닫을 수 없다: 표에 UNIQUE 가 없고 그것은 이 slice 계약 밖이다. **닫을 수 없는 것을 닫았다고 적는 것이 이 저장소가 반복해 겪은 결함**이다(6A-1 다섯 번). **사실대로 적는 것이 이 자리의 정답이다** |

## 위협 모델 — 6F-7 고유 경계

**방어한다**: (a) **판정 없이 알림을 요청할 수 없다** (b) 요청이 **배달을 주장하지 않는다** (c) outbox 쓰기
실패가 **조용히 삼켜지지 않는다**(`Failed` 로 나온다) (d) payload 에 **민감값이 실리지 않는다** (e) payload 가
**판정을 복원할 수 있다**(D-6F7-2).
**방어하지 않는다**: 실 발송·렌더링·채널(`OPEN-STR-12`) · **run 간 멱등**(D-6F7-6) · outbox 배달 보장·순서 ·
소비자 측 중복 제거.

**우회 후보와 처분**

| # | 우회 | 처분 |
| --- | --- | --- |
| 1 | **판정 없이 알림을 요청**한다 | **타입이 이미 막는다** — `NotificationRequest` 가 `internal` 생성자 + `@ConsistentCopyVisibility` 이고 `verdict: Verdict.BidNow`(그 자체도 `internal` 생성자)다. **어댑터가 새로 뚫을 자리가 있는지**를 test 로 잰다(밖에서 지어 보려는 시도가 **컴파일되지 않아야** 한다) |
| 2 | 같은 판정으로 **두 번 요청** | **닫지 않는다**(D-6F7-6) — 표에 UNIQUE 가 없다. **전칭 문면 금지**가 처분이고, 알려진 제한에 4C-1 유래로 등재 |
| 3 | outbox 쓰기 실패를 **삼킨다** | 실패를 `Failed` 로 매핑하고 **test 가 그것을 잰다**(쓰기를 강제 실패시키면 `Requested` 가 아니라 `Failed` 가 나와야 한다). 선례 `JdbcCompetitionSampleSource` 의 `SQLException` 처리 형태 |
| 4 | payload 에 **민감값**을 싣는다 | `NotificationRequest` 가 나르는 값은 코드·수치뿐(조사 실측). **참조형 누출 스캔** + **`data class` 합성 `toString()` 축**(6A-1 실측 — 언어 기능이 조용히 누출 표면을 만든다)을 (2b) 에서 실측한다 |
| 5 | 문면이 **배달을 주장**한다 | port KDoc 이 이미 절제돼 있다. **산출물 문면도 같은 절제**를 유지한다 — verifier 표적 |
| 6 | payload 가 **판정을 복원하지 못한다**(D-6F7-2 미달) | **왕복 test** — 요청을 넣고 outbox 에서 읽어 **같은 판정·근거가 나오는지** 잰다. 「썼다」가 아니라 「복원된다」를 잠근다 |
| 7 | codec 분기가 **다른 payload 타입을 잘못 해석**한다 | `payload_type` 별 왕복을 **전수**로 잰다(기존 타입 + 신설). 새 분기가 기존을 깨지 않는지 |

## (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| 알림 sink 클래스(`workflow`) | `NotificationRequest` 를 outbox 에 넣는다 | **경계로 처리** — 주입받는 것이 **전부 기존 port**(`OutboxPort`·`EventIdFactory`·`Clock`)라 **새 권한이 아니다**. **실측 항목**: 생성자 가시성, 그리고 이 sink 없이 밖이 할 수 있는 일과 같은가 |
| 새 payload 타입(`workflow.event`) | 알림 요청을 값으로 나른다 | **닫는다** — 효과의 경계는 payload 가 아니라 **register 호출**이다(`StrategyEvent` 선례). **실측 항목**: `toString()` 이 무엇을 흘리는가(`data class` 축), 생성자 가시성 |
| `OutboxPayloadCodec` 분기 | payload 직렬화 | **닫는다** — `internal object` 라 밖으로 안 샌다(조사 실측) |

**수정 라운드마다 이 표를 갱신한다.**

## 계약 갱신 (1) — D-6F7-5 가 패키지 순환을 만들었다 (2026-09-23, 팀장)

**팀장 실측: 전건 `check` 가 `:app:test` 의 아키텍처 게이트에서 실패한다.** `bidvector.**` 슬라이스 순환
금지 규칙 위반 **5건**이고, 사슬은 이것이다:

```
workflow.embedding → workflow.event → workflow.evaluation → workflow.embedding
```

- `embedding → event` : `EmbedTextRequest` 가 `CorrelationId`(=`workflow.event`)를 나른다 — **기존 변**
- `evaluation → embedding` : **기존 변**
- **`event → evaluation` : 이 slice 가 새로 만든 변** — sink 가 `workflow.event` 에 있는데
  `NotificationRequestPort`·`NotificationRequest` 는 `workflow.evaluation` 에 선언돼 있다

**이것은 D-6F7-5(팀장 결정)의 결함이다.** 나는 codec 의 **허용 루트**(`EventAdapterDependencyTest`)만 보고
「payload 를 `workflow.event` 에 두라」고 적었고, **그 위치가 만드는 의존 방향은 보지 않았다.** 위치를
고르면서 **그 위치가 무엇을 참조하게 되는지**를 함께 재지 않은 것이다 — 이 저장소가 반복해 겪은 형태다.

**그리고 이 결함은 깨진 git 래퍼에 가려져 있었다.** `contractGate` 가 먼저 죽어 빌드가 `:app:test` 에
**도달하지 못했고**, 그래서 아키텍처 게이트가 **아예 돌지 않았다.** 구현 레인이 「전 gate green」으로 본 것은
그 때문이다. **「안 돌린 게이트는 아무것도 막지 못한다」에 두 번째 날이 있다 — 안 돌린 게이트는 다른 게이트의
실패를 가린다.** 래퍼를 고치자마자 드러났다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F7-7**(D-6F7-5 정정 — **sink 와 payload 를 가른다**) | **sink(`OutboxNotificationRequestPort`)를 `workflow.evaluation` 으로 옮긴다.** **payload 타입은 `workflow.event` 에 그대로 둔다.** 그러면 방향이 `evaluation → event` 가 되고 이는 **이미 존재하는 변**이라 순환이 생기지 않는다 | **sink 는 자기가 구현하는 port 가 선언된 곳에 있는 것이 자연스럽다**(`NotificationRequestPort` 가 `workflow.evaluation` 에 있다). **payload 만 `workflow.event` 에 있으면 된다** — codec 이 이름으로 참조해야 하는 것은 payload 뿐이고, 그것이 D-6F7-5 의 **진짜 요구**였다. 봉투 생성이 `workflow` 모듈 `internal` 이라는 D-6F7-1 의 제약은 **모듈 단위**라 패키지를 옮겨도 그대로 만족된다. **대안을 기각한 이유**: ⓐ `CorrelationId` 를 `workflow.event` 밖으로 옮기는 것은 기존 변 둘을 건드리는 큰 변경이고 이 slice 범위가 아니다 ⓑ codec 허용 루트에 `workflow.evaluation` 을 더하는 것은 **「한 칸 넓히기」**이고 adapters 가 evaluation 에 닿게 만든다 — 이 저장소가 여러 번 막은 방향이다 |

**닫힘 판정**: 전건 `./gradlew --no-daemon check` **exit 0**(아키텍처 게이트 포함). 그리고 **순환이 다시
생기면 붉어지는지**를 변이로 재라 — sink 를 `workflow.event` 로 되돌리면 **RED** 여야 한다.

**acceptance 정정** — 앞선 라운드의 「`contractGate` 는 환경 결함이라 확인하지 않았다」는 **해소됐다**
(팀장이 `~/.internal-bin/git` 의 `exec` 경로를 `/usr/local/bin/git` 으로 고쳤다 — 이 머신의 git 은 Intel
homebrew 설치라 `/opt/homebrew` 에 없었다). **`:contractGate` 단독 실행 BUILD SUCCESSFUL 을 팀장이 실측했다.**
이제 전건이 온전히 돌고, 그래서 위 순환이 드러났다.

## 계약 갱신 (2) — 순환 해소와 acceptance 정정 (2026-09-23, 팀장)

D-6F7-7 이 적용됐다. sink 는 `workflow.evaluation`, payload 는 `workflow.event` 에 있고 **전건
`./gradlew --no-daemon check` 이 exit 0**(아키텍처 게이트 포함)임을 팀장이 실측했다.

**구현 레인이 독립으로 같은 순환을 찾아 같은 수정에 도달했다** — 재실측을 시작하자 `ArchitectureGateTest` 가
**처음으로 실행돼** 순환 다섯 건을 냈고, 진단이 팀장이 `scope.md` 에 적어 둔 것과 같았으며 처방도 같았다.
**두 레인의 수렴이라 D-6F7-7 이 단단하다.**

**그리고 구현 레인이 순환 그림을 팀장보다 정확히 봤다.** 팀장은 게이트 출력에서 사슬 하나
(`embedding → event → evaluation → embedding`)만 읽었는데, 실제로는 sink 가 `NotificationRequest`·
`PredictionEvidence`(`workflow.evaluation`)와 그 `diagnostics`·`release`(**`workflow.prediction`**)까지
직접 참조해 **반대 방향 간선이 둘**이었고, 기존 간선 셋(`evaluation→event`·`evaluation→prediction`·
`prediction→event`)과 합쳐 순환이 닫혔다. **처방은 같지만 그림은 그쪽이 맞다.**

**순환 재발 변이 실측** — 버릴 clone 에서 sink 를 `workflow.event` 로 되돌리니(패키지 선언·import 원복,
`numstat` 확인) `ArchitectureGateTest` 가 **같은 순환으로 RED**(`workflow.event → workflow.prediction →
workflow.event` 등). **한 번 닫은 자리가 다시 열리면 붉는다.**

### acceptance — 정정과 남은 하나

- **`./gradlew --no-daemon check` exit 0**(아키텍처 게이트 포함) · **`qualityBaseline` exit 0** — 팀장 실측.
- **`contractGate` 환경 결함은 해소됐다.** 팀장이 `~/.internal-bin/git` 의 `exec` 경로를 `/usr/local/bin/git`
  으로 고쳤다(이 머신의 git 은 **Intel homebrew**(`/usr/local/Cellar`) 설치라 `/opt/homebrew` 에 없었다).
  **앞선 라운드의 「환경 결함이라 확인하지 않았다」는 이제 「해소 뒤 전건 통과」다.**
- **`./tools/one-command-check.sh` 는 Kotlin 두 step 을 exit 0 로 끝까지 돌고 Python 첫 step(`uv sync`)에서
  exit 1** — `pypi.org` 도달 불가(**프록시를 거쳐도 목적지에 못 닿는다**). **git 래퍼 문제와는 다른 종류**로,
  로컬 설정이 아니라 **프록시 바깥의 실제 도달성** 문제다. **이 slice 는 `ml-engine`·Python 을 전혀 만지지
  않는다.** **우회하지 않는다** — Python 축은 **CI 에서 확인**한다(6A-1 이 같은 자리에서 같은 방식으로 닫았다).

### 이번 라운드가 남긴 것 — 「안 돌린 게이트」의 두 번째 날

`contractGate` 가 먼저 죽어 빌드가 `:app:test` 에 **도달하지 못했고**, 그래서 아키텍처 게이트가 **한 번도
돌지 않은 채** 「전 gate green」으로 보였다. **안 돌린 게이트는 아무것도 막지 못할 뿐 아니라 다른 게이트의
실패를 가린다.** 래퍼를 고치자마자 순환이 드러났다 — **게이트를 먼저 고치고 간 판단이 값을 했다.**

## 하네스 레인 변경 (상시 절)

- (착수 시점) 없음.

## 병행 레인

| 레인 | 상태 | 겹침 |
| --- | --- | --- |
| `m6-6a/2026-09-17`(6A-1) | **병합 완료** | 없음 |
| (없음) | — | 이 시점에 열린 다른 slice 레인이 없다 |

## OPEN — 수령·신설

| ID | 처분 |
| --- | --- |
| `OPEN-STR-12` | 변경 없음 — 발송 채널·렌더링은 그 뒤 |
| `OPEN-6F-ASSEMBLY` | 변경 없음 — 이 slice 는 포트 구현을 낼 뿐 꽂지 않는다 |
| `OPEN-6F7-REASON-CODE-STABILITY`(신설) | `BidNowReason`·`MlUnavailableReason`(`bidvector.decision`)이 **직렬화 가능한 안정적 code 속성을 갖지 않아** outbox 에 실리는 값이 **Kotlin 합성 `toString()`** 이다. 지금 못 고치는 이유: 경계 게이트가 main 소스에서 `bidvector.decision` 참조를 막고, 두 하위 타입이 `internal` 생성자다. **이 slice 의 축어 잠금은 「소리 나게」 할 뿐 형식 안정성을 주지 않는다** — 이미 영속된 옛 행을 고치지도 않는다. 받는 쪽 **도메인 레인** |
| **4C-1 의 outbox `idempotency_key` UNIQUE 부재** | 이 slice 가 **수령만** 한다(D-6F7-3·6) — 고치지 않고 알려진 제한에 등재 |

## 리뷰 레인

마이그레이션이 **없으므로** `migration-reviewer` 는 붙지 않는다. 공개 HTTP 계약이 **없으므로**
`contract-keeper` 도 아니다. payload 에 값이 실리므로 **`privacy-gate`** 는 붙인다(전역 규약 §3 「외부 전송」·
「로그」 축). `code-reviewer`(**`model: sonnet` 명시**)와 `verifier` 는 기본이다.

**Codex**: 되돌리기 어려운 경로(결제·인증/인가·암호화·마이그레이션·데이터 파기)에 **해당하지 않는다** —
대상이 아니므로 올리지 않는다.
