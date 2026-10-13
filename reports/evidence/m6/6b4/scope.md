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

- in: `ml-engine/src/ml_engine/training/jobs/**` · `ml-engine/src/ml_engine/serving/policy*`·`ml-engine/policy/serving-v1.yaml` · `ml-engine/src/ml_engine/app/server.py`(조립) · `ml-engine/tests/**` · `contracts/proto/bidvector/ml/v1/training.proto`(**주석만**) · `docs/adr/0010-*.md`(D-8 개정 블록) · Kotlin `TrainingContractRules` 와 그 계약 test · `docker/compose.yaml`·`docker/ml-serving.Dockerfile`(볼륨·디렉터리) · `config/quality/image-hygiene-policy.properties`(재실측 주석만) · `reports/evidence/m5/5e/policy-values.md`(§1 행) · `docs/runbook/**` · `reports/evidence/m6/6b4/**` · `milestone-6.md`(팀장 문단).
- out: proto 메시지·필드 변경(주석 외) · `ListTrainingJobs` RPC · Kotlin production 소비자 · 외부 큐·브로커 · 다중 인스턴스 · metric(로그 + 상태로만, metric 은 OPEN) · `OPEN-5E-CANCEL-GRANULARITY`.

## acceptance

CI 그대로: `ml-engine` job 전건(S-1~S-11) · `check` job(`./gradlew --no-daemon check` → `qualityBaseline` — Kotlin 계약 test 편집) · `container` job(S-21~S-25, `docker/**` 편집). 빌드는 호스트 전체 직렬(6B-3·6E-2c 레인과 겹치지 않게, 시작 전 pgrep·free·ps 별도 호출).

## rollback

in_scope 경로 한정 `git restore --source=e2232a75 --staged --worktree --` · 공유 파일(runbook·`milestone-6.md`·ADR·policy-values)은 병합된 main SHA 에서 복원(PR #69 규칙). 저널 형식은 새 파일이라 되돌림 뒤 남은 저널은 무시된다(인메모리로 복귀) — 그 사실을 rollback.md 에 적는다. 임시 clone 에서 ①~⑥.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
