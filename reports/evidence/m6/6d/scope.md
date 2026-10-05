# M6/6D — E2E 와 장애 주입 (계약 초안, 2026-10-05, 팀장)

- base: `fd4629fe`(PR #61 머지 = `main`) · 브랜치 `m6-6d/2026-10-05` · worktree `bid-vector-v2-m6-6d`
- 정본: 이 파일. 결정 ID `D-6D-N`. 운영자 결정은 「운영자 승인」 절. 착수 조사 `_workspace/m6-6d/00_scout.md`(읽기 전용, 미커밋).
- 성격: **test 조립 slice**(milestone 6D 다섯 축: ① mock KONEPS → canonical → qualification → ML → decision → fake notification ② 중복 공고·ML timeout·broker redelivery·DB conflict·malformed contract ③ restart 뒤 outbox/inbox 수렴 ④ model rollback·incompatible schema 거부 ⑤ 동일 input/policy/model version 재현). 완료 조건 3(필수 capability E2E)·4(dry-run acceptance)·6(버전으로 재현)의 자리.

## 착수 실측(조사 레인 요지 — 전문은 `00_scout.md`)

| 축 | 오늘 저장소 | 뜻 |
|---|---|---|
| 합성 파이프라인 | **없음** — 러너 셋은 일회성, 평가는 HTTP dry-run 뿐(`RecordingNotificationRequestPort`, outbox 미기록), ML 은 `UnavailableMlAnalysis` 고정(운영자 결정 ④ 2026-09-27: 6G 백테스트 선행) | ① 은 **test-scope 조립**으로만 가능(MockKonepsServer → CollectNoticesUseCase → 요건 시딩 → OpportunityAnalysis+gRPC gateway(in-process fake) → EvaluateCandidatesUseCase → OutboxNotificationRequestPort → relay → DispatchNotification+FakeNotificationSender) |
| outbox/inbox | V6 · claim `FOR UPDATE SKIP LOCKED` PENDING→CLAIMED · **CLAIMED 복귀(lease/reclaim) 없음** · **relay/dispatcher 없음** · 브로커 없음 · idempotency_key UNIQUE 없음(D-6F7-6) | ③ 「restart 뒤 수렴」과 ② 「broker redelivery」는 **새 production 코드**(lease·reclaim·relay) 없이는 실측 불가; 「broker」= outbox 재청구 + inbox 중복 제거로 정의해야 함 |
| ML | gateway·Resilient·Retry 존재, 타임아웃은 자리값(`OPEN-M2-DEADLINE-VALUES`), 릴리스 검사 EXACT/LATEST_PROMOTED, Python 이 `FEATURE_SCHEMA_VERSION_UNSUPPORTED` 거부(골든 있음) · **rollback/demote 기제 없음** | ④ 「incompatible schema 거부」는 실측 가능; 「model rollback」은 기제가 없어 **정의가 먼저**(EXACT 선택자로 옛 release 지정 = rollback 인가) |
| 재현성 | payload 에 releaseId·artifactChecksum·featureSchemaVersion·codeVersion·datasetId; **정책 버전 하드코딩 · 전략 revision·정책 버전은 payload 에 없음** · 판정 기록 표 없음(`OPEN-6F3-BID-RECORD`) | ⑤ 는 outbox payload 등식으로만; 정책·전략 버전 축은 payload 확장(production) 없이는 못 잠금 |
| fake 자산 | KONEPS mock 둘 + 16 fixture · ML in-process gRPC fake · FakeNotificationSender · FixedClock·SequentialCorrelationIdFactory · FakeLlmServer | ①②⑤ 의 재료는 있다 |
| 관련 OPEN | `OPEN-6A3-EVALUATION-COMMIT`(평가→outbox 커밋 경로 없음) · `OPEN-STR-12`(발송 채널) · `OPEN-6F3-BID-RECORD` · `OPEN-ML-ANALYSIS-WIRING`(보류) | 6D 가 닫는 것이 아니라 **경계로 삼거나 production slice 로 가를 것** |

## 제안 — 6D 를 둘로 가르고 production 틈을 별도 slice 로 (운영자 결정 대기)

| slice | 내용 | 전제 |
|---|---|---|
| **6D-1 (지금)** | test-scope 조립 E2E: mock KONEPS → 수집 → canonical → 요건·면허 gate → **in-process fake ML**(OpportunityAnalysis + gRPC gateway ↔ 가짜 서버) → 평가 → outbox 기록 → test relay → DispatchNotification → FakeNotificationSender. 장애 주입: 중복 공고(inbox 중복 제거) · ML timeout(가짜 서버 지연 → 예산 초과 → `MlUnavailable` 경로) · DB conflict(동시 쓰기) · malformed contract(unknown field·unsupported schema 골든). 재현: 같은 입력·같은 release 로 두 번 돌려 outbox payload 집합 등식(정책·전략 버전은 **알려진 제한**). production diff 0 | 없음 |
| **6F-10 (production, 설계 검토 필수)** | outbox **relay/dispatcher** + CLAIMED **lease·reclaim**(stale sweep) + 평가→outbox **커밋 경로**(`OPEN-6A3-EVALUATION-COMMIT`, D-6F7-11 원자성) + payload 에 **정책 버전·전략 revision**. 발송 채널은 그대로 `OPEN-STR-12`(sender 는 fake 경계) | 6D-1 이 relay 의 test 소비자 모양을 먼저 보여 줌 |
| **6D-2 (6F-10 뒤)** | restart 뒤 outbox/inbox 수렴(claim 중 크래시 → 재기동 → reclaim → 정확히 한 번 발송) · redelivery(같은 entry 두 번 claim → inbox 가 두 번째를 거부) · 재현 등식에 정책·전략 버전 포함 | 6F-10 |

## 운영자 승인 (2026-10-05 결정 완료 — 「추천대로 진행해」: C-1 (a) · C-2 (a) · C-3 (a) · C-4 (a) · C-5 (a); 정본은 D-6D-1)

- **C-1 ML 축** — (a) **6D-1 은 in-process fake ML 로 ML timeout·schema 거부를 실측**하고 prod 는 자리지킴 유지(결정 ④ 존중) · (b) ML 축 전부를 ML gRPC 배선 결정 뒤로 미룸 · (c) 지금 prod ML 배선(결정 ④ 번복). **추천 (a)**.
- **C-2 outbox 수렴·redelivery** — (a) **production 틈을 6F-10 으로 가르고 6D 를 6D-1/6D-2 로 분할**(위 표) · (b) 6D 가 production 코드(relay·reclaim)를 함께 낸다(test slice 가 production 을 바꿈 — 설계 검토·Codex 심판 범위 판단이 섞임) · (c) 6D 는 실측만 하고 결함을 OPEN 으로 남김(완료 조건 3 미충족 고착). **추천 (a)**.
- **C-3 model rollback 정의** — (a) **「rollback = 릴리스 선택자 EXACT 로 직전 release 지정」**으로 정의하고 6D-1 이 그 경로 + schema 거부를 실측(demote 기제 신설 없음) · (b) registry 에 demote/rollback 기제 신설(ML 레인, production). **추천 (a)** — 6E runbook 의 「model rollback」 절차도 이 정의로 쓴다.
- **C-4 재현 등식의 범위** — (a) **6D-1 은 payload 의 release 다섯 필드 + 판정 결과로 등식**, 정책·전략 버전은 알려진 제한 → 6F-10 이 payload 를 넓히면 6D-2 가 포함 · (b) payload 확장을 6D-1 에 넣음(production). **추천 (a)**.
- **C-5 Codex 심판** — (a) 6D-1 안 탐(test 코드만) · 6F-10 은 outbox 상태 전이(데이터 경로)라 **되돌리기 어려운 경로인지 그때 판단** · (b) 6F-10 Codex 탐(비용 승인). **추천 (a)**.

## in_scope (r1 확정 — 이 slice = **6D-1**)

- 6D-1: `app/src/test/kotlin/bidvector/app/e2e/**`(신설) · 필요한 test fixture(`adapters/src/test/**`·`workflow/src/test/**` 의 fake 확장) · `config/quality/gate-tests.properties`(등재 추가만) · `reports/evidence/m6/6d/**` · `milestone-6.md`(착수·종결 문단만). **out_scope**: production 코드 전부(`*/src/main/**`) · 마이그레이션 · ci.yml.

## acceptance (초안)

CI `check` job 명령 그대로(`./gradlew --no-daemon check` · `qualityBaseline`) — E2E 는 Testcontainers 라 기본 `check` 안에서 돈다(선례 `*E2ETest`). 각 장애 주입 축에 변이 ≥1(주입 제거 → 단언 RED). 재현 등식 음성 대조(release 를 바꾸면 RED).

## rollback (초안)

in_scope 경로 한정 `git restore --source=<base>`; 공유 파일(`gate-tests.properties`·`milestone-6.md`)은 커밋 해시 hunk — 목록은 실측 HEAD 에서 `git log` 로.

## 계약 갱신 r1 (2026-10-05, 팀장 — 운영자 결정 · 설계 검토)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-1** | **운영자 결정 C-1~C-5 = 추천대로**: C-1 (a) 6D-1 은 in-process fake ML 로 timeout·schema 거부 실측, prod 자리지킴 유지 · C-2 (a) **이 slice 는 6D-1**; production 틈(outbox relay·CLAIMED lease/reclaim·평가→outbox 커밋 경로·payload 정책/전략 버전)은 **6F-10** 으로, restart 수렴·redelivery 는 **6D-2** 로 가른다 · C-3 (a) model rollback = 릴리스 선택자 EXACT 로 직전 release 지정(6E runbook 도 같은 정의) · C-4 (a) 재현 등식 = payload release 다섯 필드 + 판정 결과, 정책·전략 버전은 알려진 제한 · C-5 (a) Codex 안 탐 | 운영자 2026-10-05 |
| **D-6D-2** | **설계 검토(팀장, `_workspace/m6-6d/01_design-review.md`) 요지**: (0) 경계 — production 조립·relay·reclaim·커밋 경로·실 발송·정책/전략 버전 재현·LLM 체인은 밖 · (1) test 가 산출물이므로 술어는 「공허한 초록 불가」 — 단언은 DB 상태(canonical·outbox·inbox 행)와 FakeNotificationSender 기록으로, 로그·문자열 아님; timeout 지연은 정책값에서 도출; malformed/schema 골든은 `contracts/testdata` 에서 로드; 재현은 별도 run 의 직렬화 payload 집합 등식 + 음성 대조 · (2) 우회 일곱(항상 성공 fake · inbox 미경유 · 리터럴 골든 · 같은 참조 비교 · `check` 밖 태그 · use case 를 fake 로 대체 · 순차 실행 충돌 없음) 각각의 닫는 술어 · (2b) 새 production public 표면 0 — production `internal` 완화가 필요하면 **멈추고 보고** · (3) 과잉: production 급 relay·실 ML 컨테이너; 미달: 로그로 재기 | 설계 검토 |
| **D-6D-3** | **production 불변 규칙**: `*/src/main/**`·`adapters/src/main/resources/db/migration/**`·`.github/**`·`docker/**` diff 0. fake 는 포트 경계(KONEPS HTTP·gRPC 서버·NotificationSender·Clock·IdFactory)에만; use case·adapter 는 production 클래스. test relay 는 test 소스셋의 소비자 모양(6F-10 의 입력 — 「relay 가 claim → DispatchNotification → sender → DELIVERED 전이」를 test 코드로 보여 주되 production 에 두지 않음) | D-6D-2 (2b) |

## 계약 갱신 r2 (2026-10-05, 팀장 — 레인 착수 실측의 충돌 처분)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-4** | **DELIVERED 전이는 6D-1 밖(= 레인 선택지 D, 명시 결정).** 레인 실측: `OutboxTransition.ToDelivered` 생성자·`transitionOutbox` 는 `workflow` `internal`, `EventSql` 은 `adapters` `internal object`, 저장소에 교차 test 의존 0 — `app` test 에서 DELIVERED 를 치려면 D-6D-3(production diff 0) 또는 (2b)(internal 완화 금지)를 깨야 한다. 그 폐쇄는 **승인된 결정**(`OPEN-4C2-MARK-UNEXERCISED`: 통로를 열어 해결하지 않는다 — legacy C-4 를 재현하는 문; 배달 오케스트레이션 slice 로 인계)이고 그 slice 가 **6F-10** 이다. 6D-1 의 relay 는 production 클래스만으로 claim → `DispatchNotification` → `FakeNotificationSender` → inbox 기록까지 잇고, 단언은 outbox 행 **`CLAIMED`**(PENDING 아님) + sender 기록 + inbox 행. DELIVERED 종단 전이는 **알려진 제한**으로 등재하고 `OPEN-4C2-MARK-UNEXERCISED` 를 6F-10 수취로 인용. 선택지 B(교차 test 의존 신설 — build 파일 in_scope 밖, 등재 모집단 영향)·C(E2E 를 adapters test 로 — in_scope 문면 위반·fixture 복제) 불채택 | 레인 보고 · 4C-2 결정 |

## 하네스 레인 변경

(착수 뒤 리뷰 요청 시점마다 `git log -- <파일>` 산출로 등재)
