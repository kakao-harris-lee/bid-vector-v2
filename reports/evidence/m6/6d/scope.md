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

- 6D-1: ~~`app/src/test/kotlin/bidvector/app/e2e/**`~~ → **`adapters/src/test/kotlin/bidvector/adapters/e2e/**`(신설, r3 D-6D-5)** · 필요한 test fixture(`adapters/src/test/**`·`workflow/src/test/**` 의 fake 확장) · `config/quality/gate-tests.properties`(등재 추가만) · `reports/evidence/m6/6d/**` · `milestone-6.md`(착수·종결 문단만). **out_scope**: production 코드 전부(`*/src/main/**`) · 마이그레이션 · ci.yml.

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

## 계약 갱신 r3 (2026-10-05, 팀장 — E2E 의 자리 이동 · D-6D-4 보정)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-5** | **E2E 는 `adapters/src/test/kotlin/bidvector/adapters/e2e/**` 에 둔다**(in_scope 문면 정정 — `adapters/src/test/**` 는 이미 in_scope). 레인 실측(팀장 대조 — `app/build.gradle.kts` test classpath 에 `grpc-inprocess`·`grpc-kotlin-stub`·`ml-contract` stub **0**, `adapters` 는 셋 다 있음): `app` test 에서는 fake ML gRPC 서버를 지을 수 없어 **ML timeout·unknown field·unsupported schema·EXACT rollback 네 축**(deadline·`ReleaseCheck`·`ReleaseShapeValidation` 이 gateway 안)이 통째로 불가능. `adapters` test 에는 `MlTestFixtures`(in-process gRPC)·`PersistenceTestSupport`(Testcontainers + TRUNCATE + production Flyway)·`MockKonepsServer`·`ContractTestdataSupport`(`contracts/testdata` 로더)가 이미 있어 **바퀴 재발명 금지**와 일치. 의존 게이트 여섯은 `src/main` 만 스캔(레인 실측). 비용: `FakeNotificationSender`·`FixedClock`·`SequentialCorrelationIdFactory` 는 `workflow` test 라 모듈 경계로 재사용 불가 → 같은 형태를 adapters test 에 둔다(선례 `BidNowFakeMlAnalysisTestConfiguration`). 등재는 `gate.tests.adapters`. build 파일 무편집(교차 test 의존 B 불채택 유지) | 레인 보고 |
| **D-6D-4 보정** | adapters test 에서는 `EventSql`(adapters `internal`)이 보이므로 relay 가 **production SQL 상수 `EventSql.MARK_DELIVERED` 그대로** DELIVERED 종단 전이까지 간다(선례 `OutboxTransitionSqlTest`, 사본 아님). 단언은 outbox 행 **`DELIVERED`** + sender 기록 + inbox 행. 남는 알려진 제한은 「`OutboxPort.markDelivered` 포트 메서드 자체는 호출되지 않는다 — `OPEN-4C2-MARK-UNEXERCISED`, 6F-10 수취」 한 줄. 전이표 거부는 `OutboxTransitionTableTest`·`OutboxTransitionSqlTest` 가 이미 잠금 | D-6D-5 의 귀결 |

## 계약 갱신 r4 (2026-10-05, 팀장 — 구현 완료 수령 · 동결 · 판정 SHA)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-6** | **구현 완료 수령·동결.** 산출물 `428551f3`(= rollback 실측 HEAD) · evidence `d23c1e55` = **판정 SHA**. 팀장 대조: 변경 15 파일 전부 in_scope(e2e 아홉 · `gate-tests.properties` · milestone(팀장) · evidence 넷) · production·build 파일·`.github`·`docker` diff **0** · in_scope porcelain 빈 출력 · 참조형 누출 스캔 evidence 0 · 크기 259 ≤ 1,384 · 등재 넷(`gate.tests.adapters`, 제외 0) · `git diff --name-only 428551f3..d23c1e55 -- <e2e 디렉터리 + gate-tests.properties + milestone-6.md>` 빈 출력 · 남은 Gradle daemon 은 TestKit 잔여(PWD contract-gate determinism, 레인 빌드 창) — 팀장이 그 PID 만 멈춤. 「미처리 지시 없음」 | 레인 보고 |
| **D-6D-7** | **레인이 지시와 다르게 택한 셋의 처분.** ① E2E 소스셋 — r3 D-6D-5 로 이미 결정. ② **전략 승격 임계 "0.50"** — 측정 priority 0.72~0.76 에서 0 은 항진명제라는 사유는 맞다; 다만 리터럴이면 fixture 가 바뀔 때 조용히 어긋나므로 **verifier 표적**: 임계가 fixture/정책값에서 도출되는가, 아니면 상수인가 — 상수라면 「측정값과 0 사이」임을 단언하는 음성 대조(임계 0 → 사다리 단언이 공허해짐을 보이는 변이)가 있는가. ③ **DB conflict 축의 GREEN 변이 하나를 사실로 등재** — 「래치 대기만 제거」가 잡히지 않음(순서가 뒤집혀도 둘째가 집을 행이 없음은 같다). 레인이 같은 축에 잡히는 변이 둘(경합 행 제거·첫 워커 claim 상한 0)을 세운 것은 채택; **verifier 표적**: 그 축이 「동시성」을 재는지 「순차 2회」로도 같은 답인지 — 동시성이 구조로 강제되지 않으면 알려진 제한으로 문면을 바꾼다(「경합 행의 배타 claim」으로) | 팀장 판단 |
| **D-6D-8** | **판정 레인 표적**(verifier opus + code-reviewer sonnet 병렬, 판정 SHA `d23c1e55`, 산출물 `428551f3`): (a) 설계 검토 (2) 우회 일곱 각각의 변이 실측(항상 성공 fake · inbox 미경유 · 리터럴 골든 · 같은 참조 비교 · `check` 밖 태그(등재 등식이 잡는가) · use case 를 fake 로 대체(production 클래스 단언이 있는가) · 순차 실행 충돌 없음) (b) 단언이 DB 상태·sender 기록인가, 로그·문자열 단언 0 (c) timeout 지연이 `OpportunityPolicyData` 예산에서 도출되는가(리터럴 0) (d) 골든이 `contracts/testdata` 에서 로드되고 부재 시 RED (e) 재현 등식의 제외 필드 규칙이 코드인가, 음성 대조(release 교체) RED (f) D-6D-7 ②③ (g) `OutboxPort.markDelivered` 미호출·정책/전략 버전 부재·gateway 수준 rollback 세 제한이 승인 문서 경계 안인가 (h) production diff 0 · `internal` 완화 0 · 등재 등식 양방향(제외 0) (i) rollback 술어·`comm` 양방향·되돌린 트리 (j) 전건 `check`(Testcontainers E2E 가 기본 `check` 안에서 실제로 돌았는가 — 12 test 실행 수 대조) · evidence 크기·좌표 0·축어 0. **빌드 규율**: 사전 확인 셋 별도 호출 · flock · `--no-daemon` · 끝나면 자기 daemon PID 만 · 00:30~01:00 KST 시작 금지 | 설계 검토 · 레인 보고 |

## 계약 갱신 r5 (2026-10-05, 팀장 — 판정 r1 수령 · 수정 라운드 1/5)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-9** | **판정 r1 수령 @`d23c1e55`**: verifier r1 **not-ready**(`02_verifier_r1.md` — F-1 high 우회 6 열림: 출처 단언이 use case 에 실제 꽂힌 객체가 아니라 따로 지은 목록을 검사해 전략·후보 소스 배선을 대역으로 바꿔도 12 GREEN · F-2 high 우회 7 열림: DB conflict 가 완전 순차에서도, production `SKIP LOCKED` 제거에서도 GREEN — 동시성을 재지 않음; verifier 시제품(첫 워커가 행을 잡고 둘째가 claim 한 뒤 첫째 롤백)은 두 변이 모두 RED · F-3 medium 임계 "0.50" 리터럴, 0 으로 두면 12 GREEN · F-4 medium timeout 이 test ceiling 200ms 에서 발화, 정책 예산 1시간으로 둬도 GREEN · F-5 low payload 부분 일치 · F-6 low 장부 과대) · code-reviewer r1 **새 high 있음**(`03_code_review_r1.md` — **G-1 ≡ F-2** · G-2 ≡ F-1(medium) · G-3 ≡ F-5 payload 부분 일치 · G-4 ≡ F-3 · G-5 ≡ F-4 + checklist 「정책은 전부 출하 정본」 오기 · G-6 rollback 목록이 공유 파일 `milestone-6.md` 누락 · L-1~L-5 low). 우회 1~5·등재 등식 둘·production diff 0·internal 완화 0·rollback 술어·`comm`·골든 로드·제외 규칙 코드는 통과. `check` 는 `adapters:test` 가 FROM-CACHE 라 `--rerun` 으로 12 실행 확인. 재작업 **1/5** | 판정 레인 |
| **D-6D-10** | **수정 라운드 처분 — 산출물(각각 별도 커밋)**: ① **F-1/G-2** 출처 단언을 **use case 에 실제 꽂힌 인스턴스**로(생성자 캡처 또는 반사로 필드 읽기 — production `internal` 완화 없이 가능한 방법만; 안 되면 멈추고 보고) · 회귀: 전략/후보 소스 배선을 대역으로 교체 → RED ② **F-2/G-1** DB conflict 를 verifier 시제품 모양으로 — 첫 워커가 행을 잡은 채 둘째가 claim → 둘째는 그 행을 못 집음(SKIP LOCKED) → 첫째 롤백 → 둘째 재 claim 성공; `CountDownLatch.await` 반환값 단언 · 회귀: 완전 순차 변이 RED · production `SKIP LOCKED` 제거 변이(clone) RED ③ **F-3/G-4** 임계를 두 방향으로 잠금 — 임계 아래 후보 하나를 넣어 「승격 안 됨」도 단언(임계 0 → RED); 리터럴은 유지하되 `OPEN-4B1-LADDER-THRESHOLDS` 인용 주석 ④ **F-4/G-5** timeout 축의 시한을 **출하 정책 예산**에서 도출(`OpportunityPolicyData` 예측 예산 + ε; test ceiling 200ms 제거) · 회귀: 예산을 1시간으로 바꾸면 RED(지연이 예산을 넘지 않게 되므로 timeout 경로 미발화) ⑤ **F-5/G-3** payload 단언을 `OutboxPayloadCodec.decode` 로 되살려 typed 필드 등식 ⑥ **low 일괄 한 커밋**: L-1 중복 공고 상태 단언 순서 무관 · L-2 `executor.shutdown()` finally · L-3 relay 의 모르는 payload 는 `error(...)` · L-4 `MlFakeServer.close()` 종료 대기 · L-5(보고서 문면대로). **①②④ 는 술어 변경 → 표적 재검증.** 기존 `OutboxClaimConcurrencyTest` 의 같은 구멍(verifier 참고)은 **이 slice 밖** — `OPEN-6D1-CLAIM-CONCURRENCY-TEST` 로 6F-10 수취(outbox 를 만지는 slice) | verifier · code-reviewer |
| **D-6D-11** | **수정 라운드 처분 — evidence(산출물 뒤 한 커밋)**: F-6 commands.md 변이 표·checklist 우회 6·7 행을 ①② 뒤 사실로 · G-5 checklist 「정책은 전부 출하 정본」을 사실로(ML 호출·KONEPS HTTP 정책은 test 가 고른 값인지 명시) · G-6 rollback.md 복원 목록에 공유 파일 `milestone-6.md`(hunk 2단계) 등재 + 실측 HEAD 를 마지막 산출물 커밋으로 재실측(⓪~⑥, 버릴 clone) · 알려진 제한에 임계 리터럴·`OPEN-6D1-CLAIM-CONCURRENCY-TEST` 추가 · 회귀 실측 행 한 줄씩. 보고 항목은 D-6D-8 보고 규율 그대로 + 「미처리 지시 없음」 뒤 동결 | evidence-pack |

## 계약 갱신 r6 (2026-10-05, 팀장 — 수정 라운드 1 수령 · 동결 · 판정 r2)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-12** | **수정 라운드 1 수령·동결.** 산출물 일곱 `09ebb17b`(①) · `6d165ce6`(②) · `efef53e4`(③, 새 test 클래스 `PipelineLadderBoundaryE2ETest` 등재 → `gate.tests.adapters` 다섯) · `669d3abe`(④) · `249a2f31`(⑤) · `554c8888`(⑥) · `aa99e987`(detekt `MatchingDeclarationName`·`ReturnCount` 시정 — 술어 무변경; **= rollback 실측 HEAD**) · evidence `f186ff5b` = **판정 SHA**. 팀장 대조: 변경 15 파일 전부 in_scope(e2e 10 · 등재 · evidence 4) · production·build·`.github`·`docker`·migration diff 0 · evidence 누출 0 · 크기 302 ≤ 1,722 · `git diff --name-only aa99e987..f186ff5b -- <e2e + 등재 + milestone>` 빈 출력 · 남은 daemon 은 TestKit 잔여(PWD contract-gate determinism, 레인 빌드 창 14:30) — 팀장이 그 PID 만 멈춤. 레인이 F-3 측정에서 판별력 없는 변이(임계 0 → 전략 불변식 위반으로 RED)를 버리고 두 임계 함께 0 인 유효 전략 변이로 다시 잰 것은 **「변이는 바꿔치기」 규율에 맞다** — 채택 | 레인 보고 |
| **D-6D-13** | **판정 r2 표적**(verifier 표적 재검증 — 술어 변경 ①②④ severity 무관; code-reviewer r2): (a) ① 살아 있는 use case·dispatcher 필드 그래프 순회가 전략/후보 소스 대역 교체를 잡는가(RED) · 포트 경계 여섯 양방향 등식 · 리플렉션이 읽기 전용이고 test 소스셋 안인가 (b) ② 「쥔 동안 claim」 — 완전 순차 변이 RED · 버릴 clone 에서 production `SKIP LOCKED` 제거 RED(대기 반환값 단언) · 래치 시한에 매달리지 않는가 (c) ④ 출하 예산 고정점 단언 + gateway 상한 = 예산×2 도출 — 예산 1시간 변이가 서버 호출 **전에** RED · 시한이 정말 `OpportunityPolicyData` 에서 오는가 (d) ③ 두 임계 함께 0 인 유효 전략 → 「둘 다 승격」 RED · 공고 번호별 임베딩 응답이 직교 벡터를 한 후보에만 주는가 (e) ⑤ typed 등식 · 멱등 키 전체 등식 (f) 등재 다섯 == 컴파일 `@Test` 클래스(새 클래스 포함, 제외 0) (g) rollback 실측 HEAD `aa99e987` — 복원 목록 D 12·M 2 `comm` 양방향, `milestone-6.md` hunk + 등재 파일 hunk **두 단계** 최신부터 역적용 conflict 0, 트리 base 동일 (h) 전건 `check` 는 레인이 `--rerun` 으로 13 test 실측 — verifier 는 `:adapters:test --rerun` 1회로 13 대조(전건 `check` 생략 가능, 적는다) (i) 알려진 제한(임계 리터럴·`OPEN-6D1-CLAIM-CONCURRENCY-TEST`·전략 INSERT 사본 둘·정책 출처 둘로 가름)이 경계 안인가 · F-6 장부 사실화 | 규율 「게이트 술어 변경은 표적 재검증」 |

## 계약 갱신 r7 (2026-10-05, 팀장 — 판정 r2 수령 · 승인 전 일괄 라운드)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D-14** | **판정 r2 수령 @`f186ff5b`**: verifier r2 **ready-for-review**(`04_verifier_r2.md` — F-1~F-5 변이 RED 로 닫힘(SAM 람다 대역도 RED), F-6 사실화; **새 medium R2-M-1**: 협력자 그래프가 `bidvector.` 패키지 접두로만 수집해 `outoftree.fake` 패키지의 감시 대상 대역이 13/13 GREEN, 정적 게이트 넷도 초록 — 우회 6 의 잔여; low 장부 셋 R2-L-1 checklist 우회 6 문면 「닿는 객체 전수」→「`bidvector.*` 객체 전수」 · R2-L-2 기존 claim test 의 「완전 순차에서도 초록」은 미측정(SKIP LOCKED 제거만 측정) · R2-L-3 `OPEN-6D1-CLAIM-CONCURRENCY-TEST` 가 milestone 6F-10 항목에 없음) · code-reviewer r2 **새 high 없음**(`05_code_review_r2.md` — G-1~G-6·L-1~L-5 닫힘, detekt 시정 술어 무변경 diff 대조; 새 low 여섯 R-1 정책 출처 다섯→여섯(KONEPS 수집 정책) · R-2 순회의 신호 없는 건너뜀 둘(`runCatching` 광범위·깊이 상한) · R-3 「읽기 전용」 문면과 `Iterable` 소비 · R-4 저장된 `payload_type` 미독 · R-5 acceptance 표에 CI 문면 명령 행 부재 · R-6 포트 경계 단언이 미래 production 구현에 먼저 붉어지는 점 제한 등재). 레인 자기 신고 둘(시한 기반 「막힘」 판정 · SAM 람다 `CodeSource` 의존)은 verifier 가 정상 run <1s·SAM 대역 RED 로 받았고 실패 방향은 거짓 RED — 알려진 제한 등재. 재작업 **1/5 유지** — 산출물 blocker/high 0 이므로 **승인 전 일괄** | 판정 레인 |
| **D-6D-15** | **일괄 라운드 처분(산출물 한 커밋 → evidence 한 커밋)**: ① **R2-M-1** 그래프 수집·하강을 패키지 접두가 아니라 **`ClassOrigin`(CodeSource)으로** — 어느 패키지든 production 출력이 아닌 객체는 경계 여섯 중 하나여야 하고 그 외는 RED; 회귀: `outoftree.fake` 대역 → RED(verifier 변이 재생) ② R-2 순회 건너뜀에 신호(건너뛴 이유·깊이 상한 도달을 결과에 실어 단언 가능하게; 상한 도달은 RED) ③ R-3 문면 「읽기 전용」을 사실로(`field.get` 만·`Iterable` 소비는 production 컬렉션 자체) ④ R-4 `payload_type` 도 decode 전에 대조. evidence: R2-L-1 우회 6 문면 → 「닿는 모든 객체(출처 기준)」(① 뒤) · R2-L-2 기존 claim test 서술을 측정한 것만으로 · R-1 정책 출처 여섯 · R-5 acceptance 표에 CI `check` job 명령 행 문면 그대로 · R-6·자기 신고 둘(시한 기반 막힘 판정 · SAM 람다 CodeSource) 알려진 제한 · rollback 실측 HEAD 를 ① 커밋으로 재실측(⓪~⑥, 호스트 규율). R2-L-3 은 팀장 — milestone 6F-10 항목에 `OPEN-6D1-CLAIM-CONCURRENCY-TEST` 수취를 종결 문단에 적는다. **① 은 술어 변경 → verifier 가벼운 표적 재검증 1회**(outoftree 대역 RED · 정상 13 GREEN · SAM 람다 여전히 RED) 뒤 종결 | 「장부층·low 는 승인 전 일괄」 · 「술어 변경은 표적 재검증」 |

## 하네스 레인 변경

`git log --oneline fd4629fe..HEAD -- CLAUDE.md .claude/` → **없음**(r4 시점). 팀장 레인 커밋은 `reports/evidence/m6/6d/scope.md`(초안 `2202184d` · r1 `8a918423` · r2 `c777f7c8` · r3 `a4f4d7b2` · r4 `d81f4761` · r5 `ae453c1d` · r6 `b826702b` · r7 이 커밋)와 `milestone-6.md`(`b42900d5` 착수) — `git log -- <파일>` 산출.
