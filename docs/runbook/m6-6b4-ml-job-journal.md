# M6/6B-4 운영 runbook — ML training job 저널 · 대기 큐 상한 · 재기동 처분 (초안 2026-10-13)

대상은 **ml-serving 의 training job 하나**다(`TrainingJobService`). Kotlin 쪽 outbox/inbox·임대는
다른 축이고(`m6-6e-operations.md` §3), 이 문서는 그것을 말하지 않는다.

**이 문서가 약속하지 않는 것**은 §5 에 모아 둔다 — 특히 **볼륨 자체의 백업·손상 복구는 M7** 이다.

## 1. 저널이 사는 자리

| 축 | 값 |
| --- | --- |
| 파일 | `<ML_ENGINE_ARTIFACT_OUT_DIR>/_journal/jobs.json` 하나. 디렉터리 이름이 `_` 로 시작해 job 디렉터리(`uuid4`)와 충돌할 수 없다 |
| 컨테이너 경로 | `/app/artifacts/_journal/jobs.json`(`docker/compose.yaml` 의 `ML_ENGINE_ARTIFACT_OUT_DIR`) |
| 영속 | `docker/compose.yaml` 의 named volume `bidvector-ml-jobs` 가 `/app/artifacts` 를 든다 — **프로세스 재시작과 컨테이너 재생성 둘 다** 저널과 산출물이 남는다 |
| 형식 | canonical JSON 한 덩이(`schema_version: job-journal-v1` + `jobs[]`). 추가 기록이 아니라 **전체 교체**다 |
| 쓰기 | tmp → `fsync` → `os.replace` → 디렉터리 `fsync`. 어느 단계에서 끊겨도 남는 것은 **이전 상태 또는 새 상태**뿐이다 |
| 쓰는 시점 | 기동 1회(처분 반영 + 쓰기 가능 확인) · job 적재 · **상태를 바꾸는** 전이. 종단 상태의 멱등 `CancelTrainingJob` no-op 은 바뀌는 것이 없어 **쓰지 않는다**(cr L-5). 전이의 저널 쓰기는 저장소 잠금 **안**이다 |
| 메모리 커밋 시점 | **원자 교체(`os.replace`)가 성공한 뒤**. 교체 전에 끊기면 메모리도 바뀌지 않는다(요청 거부). 교체 **뒤** 디렉터리 `fsync` 가 실패하면 교체는 이미 일어났으므로 메모리를 커밋하고 「내구성 미확인」을 **오류 로그**로 남긴다(기동 1회 쓰기와 전이·적재 모두) — 되돌리면 디스크와 메모리가 갈라져 「거절했다고 답한 job 이 재기동 뒤 실행」된다 |
| 내구성 미확인 뒤 전원이 끊기면 | **전이와 적재에서 결과가 다르다**(vr L-2). 기존 job 의 전이면 디스크에 **이전 상태**가 남아 재기동 처분이 덮는다. 방금 적재한 **새 job** 은 이전 상태에 없으므로 처분이 아니라 **소실**이다 — `ACCEPTED` 를 응답했는데 재기동 뒤 `GetTrainingJob` 이 `JOB_NOT_FOUND` 이고, 같은 멱등 키 재제출은 새 job 을 만든다. 오류 로그가 그 창을 알려 주는 유일한 채널이다 |

**저널만 영속시키지 않는 이유**: `SUCCEEDED` job 의 `artifact.uri` 는 `<ML_ENGINE_ARTIFACT_OUT_DIR>/<job_id>/`
의 파일을 가리킨다. job 기록만 살리고 산출물을 휘발시키면 **없는 파일을 가리키는 기록**이 남는다. 그래서
볼륨은 산출물 루트에 걸고 저널을 그 안에 둔다(D-6B4-2).

## 2. 대기 큐 상한과 폐기

- 「대기」는 플래그가 아니라 **상태**다: `ACCEPTED` = 워커가 아직 집지 않음, `RUNNING` = 실제로 실행 중.
  대기 수는 저장소의 `ACCEPTED` 개수이고 어디에도 캐시하지 않는다.
- 상한은 정책 값 `pending_queue_max`(`ml-engine/policy/serving-v1.yaml`, **잠정 4**). 값의 승인 지위와
  근거는 `reports/evidence/m5/5e/policy-values.md` §1 — 승인된 큐 깊이 임계가 없어(`OPEN-OPS-03` 미결)
  `job_workers` 의 네 배로 둔 **보수적 초기값**이다. 0·음수·키 누락은 로더가 거부한다(기동 거부).
- 상한을 넘으면 **가장 오래된 대기 job**(`accepted_at`, 동률 `job_id`) 하나가
  `FAILED(WORKER_RESOURCE_EXHAUSTED, PENDING_QUEUE_OVERFLOW)` 로 끝난다(최신성 우선, 운영자 결정 2026-09-26 ③).
  새로 들어온 job 은 대기로 남는다.
- **`RUNNING`·종단 job 은 폐기 대상이 아니다.** 멱등 재사용(`idempotency_key` 재요청)은 적재가 아니므로
  폐기도 일으키지 않는다.

### 폐기를 **어떻게 아는가** — 오늘 전부

| 채널 | 있는가 |
| --- | --- |
| `GetTrainingJob(job_id)` | **있다.** `state=FAILED` · `failure.code=JOB_FAILURE_CODE_WORKER_RESOURCE_EXHAUSTED` · `failure.detail_code=PENDING_QUEUE_OVERFLOW` · `failure.retryable=true`(용량 고갈만 참 — `retryable` 은 코드의 함수다, cr M-5). job_id 를 쥔 쪽만 볼 수 있다 |
| 서버 로그 | **있다.** 적재가 폐기를 일으키면 폐기된 job 마다 `WARNING` 한 줄 — `job_id` · `accepted_at` · 사유(`PENDING_QUEUE_OVERFLOW`). 폐기 **시점**에 남는다(워커가 뒤늦게 그 job 에 닿아 내는 `INFO` 와 다르다 — 그쪽은 시점도 사유도 아니고 재기동이 먼저 오면 아예 없다) |
| job 목록 조회 | 없다 — `ListTrainingJobs` RPC 가 없다(범위 밖) |
| metric·경보 | 없다 — `OPEN-6B4-QUEUE-METRIC` |

## 3. 재기동 처분 — **재개가 아니다**

기동 때 저널을 읽고 아래 표대로 처분한 뒤 그 결과를 저널에 다시 쓴다. 그래서 **재재기동은 아무것도
더 처분하지 않는다**(수렴 고정점).

쓰기는 **한 번이 아니라 두 단계**다(cr L-2): 저장소 복원(`load_job_store`)이 RUNNING 고아 처분과 상한 초과
폐기를 **한 번에** 쓰고, 그 뒤 대기 재적재(`_resume_pending_jobs`)가 「지금 코드가 모르는 spec」을 만나면
그 처분을 **별도 쓰기**로 남긴다. 기동 로그 두 줄(복원 계수 셋 · 재적재 대상·재적재 수)이 그 두 단계다.

| 저널 속 상태 | 처분 |
| --- | --- |
| `RUNNING` | `FAILED(TRAINING_ERROR, RUNNER_LOST_ON_RESTART)`(`retryable=false`). **재실행하지 않는다** — 실행기와 취소 토큰이 사라졌고, 재실행은 중복 학습·산출물 덮어쓰기를 부른다 |
| `ACCEPTED` | 대기 큐로 **재적재**(실행된 적이 없다). `accepted_at` 순서로 다시 집어넣고 **상한을 다시 적용**한다 — 저널이 상한보다 많은 대기를 들고 있으면 최고참부터 폐기한다 |
| `ACCEPTED` 인데 그 `training_spec_version` 을 지금 코드가 모른다 | `FAILED(TRAINING_ERROR, TRAINING_SPEC_UNSUPPORTED_ON_RESTART)`(`retryable=false` — 같은 spec 재제출은 `UNSUPPORTED_TRAINING_SPEC` 로 거부된다) — 영원히 대기로 남기지 않는다. **단 READY 기동에서만** 일어난다(이 처분은 재적재 단계의 일이고, NOT_READY 기동은 재적재를 하지 않는다 — 아래 문단) |
| 종단(`SUCCEEDED`·`FAILED`·`CANCELLED`) | 그대로 보존. 멱등 키도 보존되므로 같은 키 재요청은 **새 job 을 만들지 않는다** |

기동 로그가 처분 계수 셋(`RUNNING 고아 처분` · `상한 초과 폐기` · `대기 재적재 대상`)을 낸다 — incident
때 먼저 보는 줄이다.

**NOT_READY 로 뜬 서버는 대기 job 을 집지 않는다.** 정책 preload 가 하나라도 깨지면 새 제출을
`MODEL_NOT_READY` 로 거부하는 것과 같은 규율로 재적재도 하지 않는다. 대기 job 은 `ACCEPTED` 로 남고
**정책을 고쳐 다시 기동**해야 집힌다(이 서버는 뒤늦게 READY 가 되지 않는다).

## 4. 기동을 **거부하는** 저널 상태 — 값을 지어내지 않는다

| 상태 | 결과 |
| --- | --- |
| 파일이 없다(첫 기동) | 정상. 빈 저장소로 시작하고 **그 자리에 저널을 한 번 쓴다**(쓰기 가능 확인) |
| 디렉터리에 쓸 수 없다(볼륨 소유자·읽기 전용) | **기동 거부**(`ConfigError`). 조용히 비영속으로 뜨지 않는다 |
| 파일이 깨졌다 / 잘렸다 / JSON 이 아니다 | **기동 거부** — 「없음」과 「깨짐」을 구분한다. 깨진 저널을 빈 저장소로 읽는 경로가 없다 |
| `schema_version` 이 지원 밖 | **기동 거부** — 조용히 fallback 하지 않는다 |
| 2C 조합·시각 불변식 위반(손으로 고친 파일 등) | **기동 거부** — 판독이 `JobRecord` 생성자를 지나므로 같은 규칙이 선다 |
| `failure.retryable` 사본이 지금 사상과 다르다(사상을 고친 뒤의 **구판 저널**) | **기동한다.** `retryable` 은 `code` 의 파생값이고 저널의 값은 사본이다 — 파생값이 이기고 경고 한 줄(`retryable 사본이 … 어긋난다`)이 남는다. 「손으로 고친 파일」 행과 **다르다**: 되돌릴 수 있는 불일치라 기록을 버리지 않는다(cr M-2) |
| 저널 경로가 디렉터리다 | **기동 거부** |
| 이전 크래시가 남긴 `jobs.json.tmp` | 무해. 판독 대상이 아니고 다음 쓰기가 덮는다 |
| 기동은 됐는데 **그 뒤** 쓰기가 실패한다(볼륨이 중간에 읽기 전용이 됨 등) | 기동 거부가 아니다. 제출·취소는 `MODEL_NOT_READY` + `detail_code=JOB_STORE_UNAVAILABLE`(retryable)로 거부되고 상태는 바뀌지 않는다. **START 전이의 쓰기가 실패한 job** 은 그 프로세스 안에서 다시 집히지 않고 `ACCEPTED` 로 남아 상한 계수를 먹는다(로그 있음) — 결국 다른 제출의 상한 폐기로 치워지거나 다음 기동이 재적재한다(cr L-3) |

운영자가 깨진 저널을 만났을 때: **파일을 손으로 고치지 않는다.** 이 축은 비방어다(§5). job 기록을 버려도
되는 상황이라면 그 파일을 **치우고**(백업해 두고) 다시 기동한다 — 그러면 첫 기동으로 처리된다. 치우기 전에
`GetTrainingJob` 으로 쥐고 있던 job_id 들의 마지막 상태를 받아 둘 수 있다면 받아 둔다.

## 5. 알려진 제한 — 이 문서가 약속하지 않는 것

1. **볼륨 자체의 백업·손상 복구는 M7.** 이 slice 는 볼륨이 **살아 있는 동안**의 영속만 든다. 볼륨을 지우면
   (`docker compose down -v`, CI S-25 가 그렇게 한다) job 기록도 산출물도 함께 사라진다.
2. **다중 인스턴스 공유는 범위 밖.** 저널은 한 프로세스가 잠금 아래 쓰는 파일이다. 같은 볼륨에 두 ml-serving
   을 올리면 서로의 쓰기를 덮는다 — 그 구성을 지원하지 않는다(5E 제한 6 유지).
3. **저널을 직접 편집하는 주체는 비방어.** 판독이 불변식을 다시 세지만, 그것은 「조용히 받지 않는다」일 뿐
   편집을 막는 장치가 아니다.
4. **종단 job 이 영구 누적된다.** 전체 교체 형식이라 파일이 단조 증가한다. 보존 기간·정리 절차는 이 slice
   밖이다 — `OPEN-6B4-JOURNAL-RETENTION`.
4b. **상한이 닫는 것은 「대기 상태의 job 수」이고 실행기 대기열 항목 수가 아니다**(vr L-1). 제출이 상한을 넘으면
   job 은 `FAILED` 가 되지만 `JobRunner` 의 `ThreadPoolExecutor` 작업 큐에는 그 job 의 항목(파이프라인 객체
   포함)이 워커가 집을 때까지 남는다 — 실측: 워커 하나를 묶고 200건 제출 시 저장소 `ACCEPTED` 4 · 작업 큐
   199 · 저널 106 KB. 집힌 뒤에는 대기가 아니므로 파이프라인을 돌리지 않는다(계산 자원은 안전하다). 메모리
   상한이 필요하면 큐 자체의 상한이 따로 필요하다 — 이 slice 밖.
5. **폐기·처분의 metric 이 없다**(`OPEN-6B4-QUEUE-METRIC`). 오늘 관측은 job 상태와 서버 로그 둘뿐이고,
   job_id 를 쥔 쪽만 상태를 물을 수 있다.
6. **저장소를 쓸 수 없을 때의 전용 실패 코드가 없다.** `StartTraining`·`CancelTrainingJob` 은 그 경우
   `MODEL_NOT_READY` + `detail_code=JOB_STORE_UNAVAILABLE`(retryable)로 답한다 — 2A `FailureCode` 여덟에
   그 뜻이 없고 이 slice 는 proto 를 바꾸지 않았다(`OPEN-6B4-STORE-UNAVAILABLE-CODE`).
7. **취소 사유 `CANCELLED_BY_REQUEST` 는 여전히 생산되지 않는다.** 2C 조합 불변식이 `CANCELLED` 에
   `failure` 설정을 금지하기 때문이다(`OPEN-6B4-CANCEL-REASON-UNREACHABLE`) — 이 slice 가 고치지 않았다.
8. **출하 소비자가 0 이다.** `StartTraining` 을 부르는 production Kotlin 코드가 없다. 이 문서가 서술하는
   것은 계약 안의 운영 안전성이고, 오늘 이 큐를 채울 수 있는 것은 직접 gRPC 호출뿐이다.
