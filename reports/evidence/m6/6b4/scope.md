# M6/6B-4 — ML training job 영속 · 대기 큐 상한 (착수 계약, 2026-10-13, 팀장)

- base: `e2232a75` · 브랜치 `m6-6b4/2026-10-13` · worktree `bid-vector-v2-m6-6b4`
- 정본: 이 파일. 결정 ID `D-6B4-N`. 착수 조사 `_workspace/m6-6b4/00_scout.md` · 설계 검토 `_workspace/m6-6b4/01_design-review.md`(팀장, Phase 2.5 — 구현 전 필수).
- 성격: **계약 파일 변경**(`contracts/**` 전이표 주석 + ADR 0010 D-8 + Kotlin 계약 규칙) + 게이트·계약형. Codex 계약 심판 **1회 승인**(운영자 2026-10-13, 설계 검토·verifier 통과 뒤). 마이그레이션·PII 0 → `migration-reviewer`·`privacy-gate` 불요, `contract-keeper` 관점은 verifier 표적.
- 레인: 구현 `ml-implementer`(sonnet) — Python + `docker/**` + Kotlin 계약 규칙 한 줄·계약 test. ADR·proto 주석 문면은 팀장이 계약 커밋으로. 판정 `verifier`(opus) + `code-reviewer`(sonnet) 병렬 → Codex.
- 사용자 「M6 잔여 6B-3·6B-4 착수」(2026-10-13).

## 받는 OPEN

`OPEN-5E-JOB-PERSISTENCE`(프로세스 재시작 시 job 소실) · `OPEN-5E-JOB-QUEUE-BOUND`(제출 큐 무한 · `WORKER_RESOURCE_EXHAUSTED` 생산 경로 0).

## 운영자 결정

| ID | 결정 |
|---|---|
| 2026-09-26 ③ | **큐 상한 초과 시 가장 오래된 대기 job 부터 폐기**(최신성 우선) |
| B-1 (10-13) | **계약 네 자리 개정** — `ACCEPTED→FAILED` 간선(`WORKER_RESOURCE_EXHAUSTED`)을 ADR 0010 D-8 · `training.proto` `JobState` 주석 · Python `state._TRANSITIONS` · Kotlin `TrainingContractRules` 에 추가 + Codex 계약 심판 |
| B-2 (10-13) | **대기 상한 4**(`job_workers: 1`) — `serving-v1.yaml` 정책 키, `policy-values.md` §1 에 **잠정** 등재(`OPEN-OPS-03` 결정 시 재검토) |
| B-3 (10-13) | **파일 저장소 + compose 볼륨** — 프로세스 재시작·컨테이너 재생성 둘 다 닫는다. `docker/**` in_scope. 다중 인스턴스 공유는 범위 밖 |

## 산출물

| ID | 무엇 | 공허함을 막는 술어 |
|---|---|---|
| **Q-1** | **계약 개정** — 네 자리 전이표에 `ACCEPTED→FAILED` 하나만 추가(네 자리 **같은 데이터** — 교차 언어 등식 test) · `DetailCode` 에 대기 폐기·재기동 고아 처분 값 | 네 자리 간선 집합 등식(Python ↔ Kotlin ↔ proto 주석 기계 추출) · 간선 제거 변이 → RED |
| **Q-2** | **명시 대기 큐** — START 전이를 워커가 실제로 집는 순간으로 이동(ACCEPTED = 대기, RUNNING = 실행 중). 제출 시 대기 수가 상한을 넘으면 `accepted_at`(동률 `job_id`) 가장 오래된 대기 job 을 `FAILED(WORKER_RESOURCE_EXHAUSTED)` 로 | 대기 5번째 제출 → 1번째가 FAILED · 새 job 은 ACCEPTED · RUNNING job 은 폐기 대상 아님 · 상한 제거(바꿔치우기) 변이 → RED · 래치로 경합 실측 |
| **Q-3** | **상한 정책 값** — `serving-v1.yaml` 키 + 로더 `_SCALAR_KEYS`·`ServingPolicy` 필드·불변식(≥1) + `policy-values.md` §1 | 값 4 → 3 바꾸면 폐기 시점 test 가 RED(값→거동) · 키 누락·0 → 로더 fail-closed |
| **Q-4** | **파일 저널 저장소** — 영속은 `apply_transition` **안에서**(한 잠금) · 원자 교체(tmp → `fsync` → `os.replace` → 디렉터리 `fsync`) · canonical JSON · 판독 실패는 닫힌 `reason` 결과 타입 | 재기동(새 store 인스턴스 로드) 등가 test: 종단 job 보존 · 멱등 키 재사용 보존 · 쓰기 도중 크래시 주입 뒤 판독 가능 |
| **Q-5** | **재기동 처분** — RUNNING 고아 → `FAILED`(재개 아님, 기존 간선) · ACCEPTED 고아 → **대기 큐에 `accepted_at` 순서로 재적재**(실행된 적 없음 — 상한 적용) · 처분 계수 로그 | 재기동 수렴 test(크래시 지점 주입 · 재재기동 처분 0 · 전부 종단 또는 대기) |
| **Q-6** | **compose 볼륨** — ml-serving 에 저널 디렉터리 볼륨 · Dockerfile 디렉터리·소유자 · 이미지 위생 재실측 | `container` job(S-21~S-25) · 컨테이너 재생성 뒤 저널 생존 실측(로컬) |
| **Q-7** | runbook(저널 위치·상한·처분·볼륨 백업은 M7) · checklist · OPEN 처분 | — |

## 위협 모델 경계 (요약 — 정본은 설계 검토 노트)

**방어**: 대기 job 은 상한을 넘지 않는다 · 넘칠 때 버려지는 것은 **가장 오래된 대기** job 이고 그 사실이 job 상태(`FAILED` + 코드)로 남는다 · RUNNING 은 폐기되지 않는다 · 프로세스·컨테이너 재기동 뒤 job 기록이 남고 고아는 정해진 처분을 받는다 · 쓰기 도중 크래시가 판독 불가 저널을 남기지 않는다(남으면 판정 불가로 기동 실패, 조용한 초기화 금지). **비방어**: 다중 인스턴스 공유 · 볼륨 자체의 백업·손상(M7) · 저널 파일을 직접 편집하는 주체 · 실 소비자(출하 Kotlin 소비자 0 — 범위는 계약 안의 운영 안전성).

## in_scope / out_scope

- in: `ml-engine/src/ml_engine/training/jobs/**` · `ml-engine/src/ml_engine/serving/policy*`·`ml-engine/policy/serving-v1.yaml` · `ml-engine/src/ml_engine/app/server.py`(조립) · `ml-engine/tests/**` · `contracts/proto/bidvector/ml/v1/training.proto`(**주석만**) · `docs/adr/0010-*.md`(D-8 개정 블록) · Kotlin `TrainingContractRules` 와 그 계약 test · `docker/compose.yaml`·`docker/ml-serving.Dockerfile`(볼륨·디렉터리) · `config/quality/image-hygiene-policy.properties`(재실측 주석만) · `reports/evidence/m5/5e/policy-values.md`(§1 행 · §2 아티팩트 루트 행 — D-6B4-2) · `docs/runbook/**` · `reports/evidence/m6/6b4/**` · `milestone-6.md`(팀장 문단).
- out: proto 메시지·필드 변경(주석 외) · `ListTrainingJobs` RPC · Kotlin production 소비자 · 외부 큐·브로커 · 다중 인스턴스 · metric(로그 + 상태로만, metric 은 OPEN) · `OPEN-5E-CANCEL-GRANULARITY`.

## acceptance

CI 그대로: `ml-engine` job 전건(S-1~S-11) · `check` job(`./gradlew --no-daemon check` → `qualityBaseline` — Kotlin 계약 test 편집) · `container` job(S-21~S-25, `docker/**` 편집). 빌드는 호스트 전체 직렬(6B-3·6E-2c 레인과 겹치지 않게, 시작 전 pgrep·free·ps 별도 호출).

## rollback

in_scope 경로 한정 `git restore --source=e2232a75 --staged --worktree --` · 공유 파일(runbook·`milestone-6.md`·ADR·policy-values)은 병합된 main SHA 에서 복원(PR #69 규칙). 저널 형식은 새 파일이라 되돌림 뒤 남은 저널은 무시된다(인메모리로 복귀) — 그 사실을 rollback.md 에 적는다. 임시 clone 에서 ①~⑥.

## 계약 갱신 이력

**D-6B4-2 (r1, 2026-10-14, 팀장 — 구현 레인 착수 수령).** ① **저널 경로 = `<ML_ENGINE_ARTIFACT_OUT_DIR>/_journal/`, 볼륨은 `/app/artifacts` 전체**(B안). 새 env 0 → in_scope 밖 두 자리(`tools/image-hygiene-check.sh` PROBE_ENV · `RealServerIntegrationTest`) 무편집, 그리고 「재기동 뒤 job 기록은 남는데 `artifact.uri` 파일은 없다」 비정합이 함께 닫힌다(산출물과 기록이 한 볼륨). `ML_ENGINE_ARTIFACT_OUT_DIR` 의 뜻이 「job 산출물 + 저널 루트」로 넓어지므로 in_scope 에 `reports/evidence/m5/5e/policy-values.md` §2 해당 행을 더한다. ② `InMemoryJobStore` → `JobStore`(저널 **필수 인자** — 비영속 조립 갈래를 구성상 없앤다). ③ ACCEPTED 고아 재적재를 위해 저널 항목에 제출 payload(dataset uri · manifest_checksum · training_spec_version)를 함께 적는다(proto 변경 0). 정책 preload 가 NOT_READY 면 재적재하지 않고 ACCEPTED 로 두되, **READY 가 되는 시점에 재적재**(영구 정체 금지)하고 그동안도 상한 계수에 든다 — 알려진 제한 등재.

**D-6B4-3 (r2, 2026-10-14, 팀장 — 구현 완료 수령 · 동결).** ① D-6B4-2 ③ 의 「READY 가 되는 시점에 재적재」는 **「다음 기동이 집는다」**로 확정한다 — `ReadinessGate` 에는 NOT_READY → READY 프로세스 내 전이가 없다(M5 가 `mark_ready` 를 M-5 로 지웠고 「정책은 재로드하지 않는다 — 재시작만 preload 를 다시 돈다」). 재기동이 유일한 READY 경로이므로 정체의 상한은 재기동이다. 그 사실을 공개 표면 집합 등식 + 인자 없는 공개 메서드 전수 호출 test 로 잠갔다(`18069139` — 기존 이름 하나 금지 test 는 `retry_preload()` 변이에 초록이었다). 대기 job 은 그동안도 상한 계수에 든다. ② 산출물 판정 SHA `18069139` · evidence 끝 `a89f4a08`. acceptance: `ml-engine` 전건 exit 0(1,517 passed) · `check` exit 0 @a89f4a08 · `container` 16 step exit 0 @54bbef81(이미지 입력 무변경 근거로 재사용) · 변이 11 RED · 컨테이너 재생성 뒤 저널 생존 + 통제군. 새 OPEN 넷(`OPEN-6B4-CANCEL-REASON-UNREACHABLE` · `-STORE-UNAVAILABLE-CODE` · `-JOURNAL-RETENTION` · `-QUEUE-METRIC`). 검토: verifier(opus) · code-reviewer(sonnet) 병렬 → Codex 계약 심판 1회.

**D-6B4-4 (r3, 2026-10-14, 팀장 — 검토 r1 수령 · 수정 라운드 1 수령 · 동결, 재작업 1/5).** 검토 r1: verifier not-ready(H-1 값 운반 · H-2 조립 경계 이름 술어 · H-3 폐기 로그 0) · code-review high 1(H-1 응답 `RUNNING` 경합). 팀장 결정 둘: ① cr H-1 은 **순서 복원**(저장소 읽기를 대기열 투입 앞으로 — proto `StartTrainingResponse` 문면 무변경, test 는 `== ACCEPTED` 결정적) ② vr M-3 은 **문면을 구현에 맞춘다** — `ACCEPTED → FAILED` 사유 셋(상한 폐기 · 재기동 시 모르는 spec · START 커밋 전 크래시)을 proto 주석·ADR D-8 에 열거. 레인 결과: `retryable` 은 코드의 함수(필드 아님) · 재기동 처분 비재시도 · 교체 뒤 디렉터리 fsync 실패 = `durable=False` 커밋 · 새 파일 `training/jobs/journal_file.py`(in_scope `training/jobs/**` 안) · `OPEN-5E-JOB-QUEUE-BOUND` 는 **부분 닫힘**(상태 상한 닫힘, 실행기 대기열 항목 남음). 산출물 판정 SHA **`e118ced7`** · evidence `78228929`. 레인 acceptance: ml-engine job 전 step exit 0(1563 passed) · check `--rerun-tasks` 0 · qualityBaseline 0 · container job 16/16(S-24 포함). 미처리 low 셋(cr L-6·L-7·L-8)은 알려진 제한 등재. 검토 r2: verifier(opus) · code-reviewer(sonnet) 병렬 → Codex 1회.

**D-6B4-5 (r4, 2026-10-14, 팀장 — 검토 r2 수령 · 수정 라운드 2 수령 · 동결, 재작업 2/5).** 검토 r2: verifier not-ready(H-1 상대 import·재바인딩 · H-2 쓰기 자리 이름 열거) · code-review APPROVE(medium 4). 팀장 결정: 조립 경계는 grimp import 그래프(재바인딩·`type()`·private 접근은 명시 제한) · 둘째 writer 는 **정적 닫힘 주장 철회 → 세션 전역 런타임 감사 훅**(실행된 in-process 경로만 닫음, subprocess·미실행 경로는 제한; 판정은 세션 맨 뒤 test 의 실패로 — `pytest_sessionfinish` 는 exit 0 이라 쓰지 않음) · 전이표는 `_TRANSITIONS` 렌더링 바이트 등식 + Kotlin 은 ADR 블록 파싱(새 데이터 파일 없음; ADR 은 Gradle 입력이 아니어서 증분 빌드는 재실행 안 할 수 있음 — `--rerun-tasks` acceptance 와 Python 게이트가 합성을 닫음) · `retryable` 사본 불일치는 파생값 승리+경고 · `ACCEPTED→FAILED` 사유는 런타임 닫힌 집합 · proto `StartTrainingResponse` 문면은 새 job / 멱등 재사용 두 경로로(주석만). 산출물 판정 SHA **`86ee999a`** · evidence `72f55ee2`. 레인 acceptance: ml-engine 전 step 0(1598 passed, 감사 훅으로 `tests/app` +25% 시간) · check `--rerun-tasks` 0 · qualityBaseline 0 · container 16/16 · rollback 트리 = base 트리. 레인 자기신고: `git checkout -- .` 로 미커밋 작업 소실 1회(재작업, evidence 등재). 검토 r3: verifier(opus) · code-reviewer(sonnet) 병렬.

**D-6B4-6 (r5, 2026-10-14, 팀장 — 검토 r3 수령 · 승인 전 일괄 수령 · PR 단계).** verifier r3 ready-for-review(high 0) · code-review r3 APPROVE. 승인 전 일괄: 게이트 빈틈은 술어 변경 없이 제한 등재(`OPEN-6B4-AUDIT-HOOK-HARDENING` · `OPEN-6B4-TRANSITION-PROSE-GATE` · `OPEN-6B4-ASSEMBLY-BOUNDARY-HARDENING`) · 변이 test 의 훅 누출만 술어 기계 변경(`3fb9010e`, 표적 재검증). 표적 재검증 r3b 가 r3 부터 있던 test 경합(`shutdown(wait=False)` 뒤 같은 저널 디렉터리 둘째 store — 전체 pytest 3/3 실패)을 잡음 → 경합만 `d3a0a28c` · 장부 `cf4902bf`(재작업 3/5) → r3c ready-for-review(전체 pytest 2회 exit 0 · 게이트 파일 단독 15/15). **최종 산출물 SHA `cf4902bf`** · evidence `86967b4e`. `0716e89e` 이후 변경은 `ml-engine/tests/**`·evidence 뿐이라 r3 의 check · qualityBaseline · container 16/16 유효. ADR 0010 편집이 「D-8 개정 블록」 문면보다 넓다(원 D-8 항목의 산문 전이 사슬을 렌더링 블록으로 옮김 — 블록 밖 금지 게이트 때문) — 계약 심판에 D-8 절 전체 검토를 요청한다. 다음: PR → Codex 1회(proto 주석 · ADR 0010 D-8 — 계약 파일) → `/code-review` → CI → 머지(사용자 사전 승인).

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
