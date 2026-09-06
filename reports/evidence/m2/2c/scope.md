# Slice 계약 — M2 / 2C · 비동기 training 계약 (`TrainingJobService`) — **초안, 구현 전**

> **지위**: M1/1E 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> 착수는 2A(그리고 2B — 병행 가능) 승인 뒤 운영자 지시로 하며, 그때 `base_sha` 를 재고정하고 `milestone-2.md` 착수 문단을 쓴다.
> `ADR 0010`(D-8 transport 포함)은 **2A 착수 전건으로 이미 승인돼 있다** — 2C 가 그 상태를 바꾸지 않는다.

```yaml
milestone: m2
slice: 2c-training-job-contract
base_sha: c9022d9989c4b2a09cf8b9ff94795176dc5dc00c   # 초안 작성 시점 HEAD — **2A 승인 뒤 착수 시 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - contracts/proto/bidvector/ml/v1/training.proto    # TrainingJobService { StartTraining, GetTrainingJob, CancelTrainingJob } + 상태 enum·전이표(주석)·참조 메시지
  - contracts/testdata/training/**
  - adapters/src/test/kotlin/**                        # consumer test — 상태 전이·idempotency·cancel 의미를 fake servicer 로
  - ml-engine/tests/test_training_contract.py         # provider 쪽 계약 test(전이표 밖 전이 거부·미지 상태 없음)
  - milestone-2.md                                    # 「Slice 2C」 착수 문단, **착수 시**
  - reports/evidence/m2/2c/**
out_of_scope:
  - 학습 실행·아티팩트 생성·evaluation report 의 내용   # M5 5C — 2C 는 참조(uri + checksum)의 형태만
  - dataset 의 생성·저장(object storage adapter)        # M5 5C·ml-engine adapters/ — 2C 는 immutable reference 의 형태만
  - Kotlin 쪽 폴링 스케줄(주기·재시도)                 # ADR 0005 DB 스케줄러 + M4. 2C 는 「Kotlin 이 조회한다」는 방향만(ADR 0003 D-5)
  - 모델 승격(promotion)·롤아웃                         # ML-07·ML-08 — M5. 학습 성공 ≠ 승격
  - broker contract · 서버 스트리밍 push                # ADR 0010 D-8 불채택
  - ml-contract/**, ml-engine/src/ml_engine/contracts/**   # 생성물은 VCS 밖(2A D-2A-0 (c)) — 변경이 나올 수 없는 경로라 in_scope 아님
  - Celery 이름·raw_status 노출                          # H-12 직접 위반 — 계약은 자기 상태 어휘만
  - shared-kernel/**, 도메인 모듈, fixtures/**
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "(cd contracts && buf lint && buf build)"                                                          # S-2
  - "./gradlew :adapters:test --tests '*Training*Contract*'"                                          # S-3 — 생성은 included build 가 adapters 의 test 의존으로 수행
  - "(cd ml-engine && python -m pytest tests/test_training_contract.py tests/test_contract_roundtrip.py -q)"   # S-4
  - "./gradlew qualityBaseline"                                                                        # S-5
rollback: |
    **정본은 `reports/evidence/m2/2c/rollback.md`**(착수 시 작성). training.proto 와 생성물·test 를 걷으면 2A/2B 상태.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 근거: `milestone-2.md` 「Slice 2C」 · `v2-지침서.md` §3.3(「장시간 training은 동기 RPC로 붙잡아
두지 않는다」·「Kotlin이 Python 내부 task 이름이나 Celery wire format을 알지 못한다」)·§5 · `ADR 0003` D-4·D-5 · `ADR 0005`(브로커 없음) ·
`ADR 0010` 초안 D-4·D-8 · `data-dictionary.md` §2.2(전이표 형태) · `capability-map.md` ML-05·ML-07·ML-08 · 조사 노트 01 (d)·(h) H-12.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — **착수 시 재고정한 base 로 다시 낸다.** 초안 시점은 해당 없음.

---

## 이 slice 가 하는 일

`milestone-2.md` 「Slice 2C」 여섯 항목을 **unary RPC 셋 + 상태 전이표 + 참조 메시지 둘**로 낸다. legacy 대응물은 아홉 항목 중 둘뿐
(artifact manifest·evaluation report reference)이라 **나머지는 신규 설계**다(조사 (d)).

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`StartTraining`** — `TrainingRequest { RequestEnvelope envelope, string idempotency_key, DatasetReference dataset, string training_spec_version, optional string requested_release_id }` → `TrainingJobHandle { string job_id, JobState state }`. **같은 `idempotency_key` 는 같은 `job_id`** 를 돌려준다(새 job 을 만들지 않음 — ADR 0010 D-4). `dataset` 없이 「지금 DB 를 읽어라」는 요청 형태가 없다(legacy 는 실행 시점 DB 읽기 — 조사 (d)) | 2C 「immutable dataset reference와 manifest checksum」·「training request id와 idempotency key」 · §3.2 serving 무 DB |
| ② | **`DatasetReference { string uri, string manifest_checksum(sha256 hex), string dataset_id }`** — uri 는 ml-engine `adapters/` 가 읽는 object/file storage 경로, DB 참조 아님. checksum 불일치는 `FAILED(DATASET_CHECKSUM_MISMATCH)` 로 **job 안에서** 실패(요청 거부가 아니라 — 검증은 읽을 때 일어난다) | 2C 「immutable dataset reference」 · `v2-지침서.md` §5 「dataset id, checksum」 |
| ③ | **`GetTrainingJob(job_id)`** → `TrainingJob { job_id, JobState state, Timestamp accepted_at, optional started_at, optional finished_at, optional ArtifactReference artifact, optional EvaluationReportReference evaluation, optional JobFailure failure }`. `SUCCEEDED` 면 `artifact`·`evaluation` 필수, `FAILED` 면 `failure` 필수 — provider test 가 조합 불변식을 검사 | 2C 「accepted/running/succeeded/failed 상태」·「artifact manifest와 evaluation report reference」 |
| ④ | **`JobState` 와 전이표** — `ACCEPTED → RUNNING → SUCCEEDED \| FAILED`, `ACCEPTED \| RUNNING → CANCELLED`. 표에 없는 쌍은 provider 가 만들지 않고 consumer 가 거부한다(관측 가능한 거부). **미지 상태 없음** — `UNSPECIFIED` 와 정의 밖 정수 거부(2A ⑥). legacy 는 미지 Celery 상태를 `queued` 로 접었다(조사 (d)) | `data-dictionary.md` §2.2 「표에 없는 쌍은 거부이며 거부가 관측 가능해야 한다」 · 2C 「cancel/retry 권한과 상태 전이」 |
| ⑤ | **`CancelTrainingJob(job_id)`** → `TrainingJob`. `ACCEPTED`·`RUNNING` 에서만 `CANCELLED` 로, 종료 상태에서는 **멱등 no-op**(현재 상태 반환, 오류 아님). 취소 권한은 계약 밖(M6 인증) — 계약은 「취소가 있다」와 전이만 | 2C 「cancel/retry 권한과 상태 전이」 |
| ⑥ | **retry 는 새 job** — `FAILED` job 을 되살리는 RPC 는 없다. 재시도는 새 `idempotency_key` 의 `StartTraining`(같은 `dataset` 참조 재사용). 계약이 「어떤 실패가 재시도 가치가 있는가」를 `JobFailure.retryable` 로 싣는다(ADR 0010 D-3 의 application failure 와 같은 형태) | 2C 「cancel/retry」 · ADR 0010 D-4 |
| ⑦ | **`ArtifactReference { string uri, ModelRelease release }`** — checksum 은 `release.artifact_checksum` **한 자리**(같은 사실 두 자리 금지). `release` 는 2B 의 다섯 성분(`release_id`·`artifact_checksum`·`feature_schema_version`·`code_version`·`dataset_id`)이고 `dataset_id` 는 요청의 것과 같아야 한다(consumer test). **manifest 의 내용**(재현성 파라미터·metric·`sample_scope`·서명)은 M5 5C 소유 — 계약은 참조 + `manifest_schema_version` 만 | `v2-지침서.md` §5 · ML-05 「재현성 파라미터가 manifest 에 기록」(내용은 5C) · ML-08 서명(5C) |
| ⑧ | **`EvaluationReportReference { string uri, string checksum, string report_schema_version }`** — 승격 판정(ML-07)은 Kotlin 도 ml-engine 도 아닌 **운영자 결정**이고 이 참조는 그 입력이다. 학습 성공이 승격이 아니다 | ML-07 「사전 선언 판정식」 · 5C 「promotion은 측정 결과를 만들 뿐 자동 운영 배포하지 않음」 |
| ⑨ | **transport** — 세 RPC 전부 unary, 같은 gRPC 서버(`bidvector.ml.v1`), Kotlin 이 폴링. deadline 은 각 unary 호출에만 걸리고 job 수명과 무관. Celery·큐·task 이름은 wire 에 없다(H-12) | `ADR 0010` D-8 · §3.3 |

**만들지 않는 것**: 학습 실행·아티팩트 내용·평가 내용·승격·롤아웃·dataset 생성·폴링 스케줄·인증·스트리밍.

---

## 운영자 결정 필요 — 착수 전(D-2C-1~2) · 계약 고정(D-2C-3~6)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-2C-1** | **`idempotency_key` 의 발급 주체와 의미.** | (a) **Kotlin 이 발급(업무 의도 단위 — 「이 dataset 으로 이 spec 의 학습 한 번」), ml-engine 은 저장·대조만** (b) ml-engine 이 `dataset_id + training_spec_version` 해시로 유도 | **(a)** — (b) 는 「같은 데이터로 두 번 학습」(seed 실험 등)을 계약이 금지하게 되고, 의도 단위는 업무 쪽이 안다. ml-engine 은 키를 해석하지 않는다(불투명 문자열, 길이 상한) | 착수 전 |
| **D-2C-2** | **`training_spec_version` 이 가리키는 것.** 학습 파라미터(seed·num_threads·boosting rounds·holdout 규칙)는 어디에 사는가 | (a) **ml-engine 안의 versioned training spec(5C 소유), 요청은 version 문자열만** (b) 요청 본문에 파라미터를 실음 | **(a)** — (b) 는 Kotlin 이 LightGBM 파라미터를 알게 된다(§3.2 경계). legacy 의 재현성 파라미터는 코드 상수였고 manifest 에 없었다(조사 (d)) — 5C 가 spec 을 versioned 로 두고 manifest 에 싣는다. 미지 version 은 job 실패가 아니라 **`StartTraining` 거부** — `ApplicationFailure(UNSUPPORTED_TRAINING_SPEC, retryable=false)`(2A ⑤ 가 소유하는 `FailureCode` 값 — 시작 전 알 수 있는 것은 시작 전에) | 착수 전 |
| **D-2C-3** | `job_id` 는 ml-engine 발급 불투명 문자열(UUID 권장, 계약은 형식을 정하지 않음). Kotlin 은 저장·조회 키로만 | — | 계약 고정 |
| **D-2C-4** | `JobFailure { JobFailureCode code, bool retryable, string detail_code }` — **`JobFailureCode` 는 2A 의 `FailureCode`(application 어휘)와 다른 enum** 이고 이름을 겹치지 않는다(같은 패키지에서 같은 이름은 protoc 이 거부하고, 층이 다르다 — application 실패는 RPC 응답, job 실패는 job 의 종료 사유). 최소 집합 `DATASET_UNREADABLE`·`DATASET_CHECKSUM_MISMATCH`·`TRAINING_ERROR`·`EVALUATION_ERROR`·`CANCELLED_BY_REQUEST`·`WORKER_RESOURCE_EXHAUSTED`(transport status `RESOURCE_EXHAUSTED` 와 이름을 겹치지 않는다 — ADR 0010 D-3 「두 층에 같은 실패를 두지 않는다」) 으로 열고 5C 가 값을 더한다(호환 추가). 2C 가 RPC 응답에서 쓰는 application 값(`IDEMPOTENCY_CONFLICT`·`JOB_NOT_FOUND`·`UNSUPPORTED_TRAINING_SPEC`)은 **2A ⑤ 에 등재돼 있다**. 자유 문자열 사유 없음 | — | 계약 고정 |
| **D-2C-5** | 시각은 `google.protobuf.Timestamp`(UTC). KST 해석은 Kotlin. `accepted_at ≤ started_at ≤ finished_at` 불변식은 consumer test | — | 계약 고정 |
| **D-2C-6** | job 목록 조회(`ListTrainingJobs`)는 **두지 않는다** — Kotlin 이 자기가 시작한 job 만 안다(ADR 0003 D-5 방향). 필요가 측정되면 호환 추가 | — | 계약 고정 |

---

## 위협 모델 — 2C 고유 경계

**방어한다**: (a) 중복 학습(`idempotency_key` — 같은 키 두 번 = job 하나, provider test) (b) 미지 상태의 조용한 접힘(`UNSPECIFIED`·정의 밖 거부, 전이표 밖 전이 거부) (c) `SUCCEEDED` 인데 artifact 없음 / `FAILED` 인데 failure 없음(조합 불변식 test) (d) dataset 이 요청 시점과 다른 것으로 바뀜(immutable reference + checksum — 불일치는 `FAILED`) (e) Celery·task 이름의 wire 유입(필드 부재 + lint) (f) 응답 artifact 의 `dataset_id` 가 요청과 다름(consumer test) (g) 동기 RPC 로 학습을 붙잡음(모든 RPC 가 unary 이고 `StartTraining` 이 즉시 `ACCEPTED` 를 돌려준다는 provider test).
**방어하지 않는다**: ml-engine 이 실제로 취소를 존중하는가(5C — 계약은 상태만) · 폴링 주기·재시도(M4/ADR 0005) · dataset 의 내용 무결성(checksum 은 바이트 동일성까지) · 인증·권한(M6) · manifest 서명 검증(ML-08, 5C) · fork-불안전(ADR 0010 §6 — 5A 프로세스 모델).

**우회 후보(≥5)**: (1) `idempotency_key` 를 빈 문자열로 → 필수 필드 validation (2) `RUNNING → ACCEPTED` 역전이 → 전이표 test (3) `CANCELLED` 에 artifact 첨부 → 조합 불변식 (4) `dataset.uri` 에 DB 연결 문자열 → 계약은 못 막는다(uri 는 불투명) — 5A 의 serving/training import boundary(§3.2) 가 잡는다, 알려진 제한 (5) `JobFailure.detail_code` 에 Celery 원문 → lint 는 못 잡는다 — 리뷰 항목 (6) `GetTrainingJob` 을 스트리밍으로 바꿈 → breaking gate (7) 같은 `idempotency_key` 로 다른 `dataset` → `ApplicationFailure(IDEMPOTENCY_CONFLICT, retryable=false)`(2A 소유 값) — provider test.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (조사 노트 01 (d))

- 9항목 중 legacy 대응물은 **artifact manifest**(성숙 — `manifest_schema_version`·`git_sha`·`promotion_gate`·HMAC 서명)와 **evaluation report reference**(`report_integrity.sha256`) 둘. → ⑦·⑧ 은 참조 형태를 legacy manifest 가 이미 갖는 것에 맞춘다(내용은 5C).
- immutable dataset reference·idempotency key·cancel(엔드포인트 없음, `REVOKED` 읽기만)·retry(task 재시도 미설정) **부재** → ①·②·⑤·⑥ 신규.
- 상태 5값(`queued`/`running`/`completed`/`failed`/`cancelled`)이고 미지 Celery 상태가 `queued` 로 접힘 → ④.
- 재현성 파라미터(`seed=20260812`·`num_threads=4`·`BOOSTING_ROUNDS=400`·`deterministic`)는 코드 상수, manifest 미기록 → D-2C-2 (a).
- `sample_scope`(기본값 없는 필수 코퍼스 선언)가 dataset reference 에 가장 가까운 legacy 자리 → `dataset_id`·manifest 의 5C 설계 입력.
- `raw_status`·`task_name` 노출(H-12) → ⑨.
- `OPEN-ADR-11` 근거 없음 → 학습 RPC 의 deadline 도 정책 데이터(ADR 0010 D-1).

---

## OPEN — 수령·신설

| OPEN | 2C 처리 |
| --- | --- |
| `OPEN-ML-05`(정책 값 33개 분류) | 5C 소유. 2C 는 `training_spec_version` 참조로만 만난다 |
| `OPEN-ML-07`/ML-08 승격·서명 | 계약 밖 — `EvaluationReportReference` 가 입력일 뿐 |
| `OPEN-ADR-11` | ADR 0010 — 2C 의 unary 호출에도 같은 규칙 |
| 신설 후보 `OPEN-2C-FAILURE-CODES` | `FailureCode` 최소 집합의 5C 확장 — 호환 추가 규칙(제공자 먼저 배포) |
| 신설 후보 `OPEN-2C-DATASET-URI-SCHEME` | `DatasetReference.uri` 의 허용 scheme(file/s3/…) — ml-engine adapters(5C)·배치(M6 6C) |
