# M5/5E-1 — checklist.md

## M5 완료 조건 담당 근거

| 완료 조건(milestone-5.md) | 5E-1 근거 |
| --- | --- |
| 생성 servicer(3 개) 등록 | `serving/grpc.py::build_server` — `BidPredictionService`·`EmbeddingService`·`TrainingJobService` 한 서버(ADR 0010 D-8), `tests/serving/test_grpc.py::test_build_server_registers_three_services_and_serves` 실측(실 socket) |
| preload/readiness | `serving/readiness.py::ReadinessGate.from_preload` — 정책 넷 preload 결과를 `PreloadOutcome` 으로 정규화해 받는다(하나라도 실패 → `NOT_READY`). `app/server.py::_preload`가 조립. `tests/serving/test_readiness.py` |
| serving 패키지에 DB driver·ORM 없음 | pyproject.toml forbidden 계약(무편집 부분) + `tests/gates/test_serving_purity.py`(실행 시점 재확인, `ml_engine.app` 비대상 명시 test 추가) |
| checksum 불일치 시 readiness fail-closed | dataset checksum 불일치는 job 안에서 `FAILED`(2C 문면, D-5C-8 승계) — 정책 자체의 로드 실패는 `ReadinessGate`가 `NOT_READY`로(§ 위) |
| validation·Numpy conversion | envelope·필드 검증은 `serving/status.py`(`ValidationDetailCode`)·`training/jobs/servicer.py`(독립 소유, layer 경계). Numpy 변환은 5D-2 `serve_bid_rates` 내부(5E-2 대상, 5E-1 무관) |
| deadline/cancel/status 매핑 | deadline: 5E-1 은 배선하지 않는다 — 호출자가 없던 `is_deadline_active`는 제거했다(M-8 수정, 5E-2 가 실 계산을 필요로 하면 재도입). cancel: `CancelToken` + job 단계 경계 넷(D-2D-7, `tests/training/test_jobs_runner.py`). status: `serving/status.py::fill_application_failure`(ADR 0010 D-3) |
| release/checksum/schema 응답 | `GetModelMetadata.promoted` 미설정 + `supported_feature_schema_versions`(5B `SUPPORTED_FEATURE_SCHEMAS`) — 판정(`CalculateOptimalBid`)은 5E-2 |
| graceful shutdown | `serving/grpc.py::shutdown` — `gate.begin_shutdown()` → `server.stop(grace)`, `tests/serving/test_grpc.py::test_shutdown_calls_begin_shutdown_before_stop` |
| bounded concurrency | `grpc.server(ThreadPoolExecutor(max_workers), maximum_concurrent_rpcs=…)`, `tests/serving/test_grpc.py::test_maximum_concurrent_rpcs_exhausted_returns_resource_exhausted`(실 socket, N+1 번째 `RESOURCE_EXHAUSTED` 실측) |
| M2 consumer/provider contract 통과 | S-12(`./tools/contract-crosslang-smoke.sh`, fake 서버 무편집) + S-12b(실 서버 socket 스모크, commands.md) |

## D-5E-0~11 이행

| ID | 이행 |
| --- | --- |
| D-5E-0 | 5E 를 5E-1/5E-2 로 분할(이 slice = 5E-1) |
| D-5E-1 | `ml_engine.app`(조립 근) — `app/pipeline.py`(training/adapters/inference 를 잇는다)·`app/server.py`(그 넷 + serving 을 잇는다). `pyproject.toml`에 `app 은 DB·HTTP·업무 모듈을 모른다` 계약 신설 |
| D-5E-2 | `serving/embedding.py::EmbeddingServicer.EmbedText` — 검증 통과 요청은 `ApplicationFailure(MODEL_NOT_READY, EMBEDDING_MODEL_ABSENT)`. 해시 fallback 없음(`tests/serving/test_embedding.py`) |
| D-5E-3 | `serving/prediction.py::BidPredictionServicer` — `CalculateOptimalBid` 오버라이드 없음(생성 base 기본 `UNIMPLEMENTED` 유지, `tests/serving/test_prediction.py::test_calculate_optimal_bid_is_unimplemented_by_default`). `GetModelMetadata.readiness` 는 gate 상태와 무관하게 항상 `NOT_READY` |
| D-5E-4 | `training/dataset.py`(`settlements_checksum` 선택적 필드 + `RawSettlementRow` + `_load_settlements`)·`adapters/dataset_files.py`(`settlements.jsonl` 선택적 읽기) — 둘 다 기존 5C-1 test 무변경 통과(hunk 격리, 기본값으로 하위 호환). **강제는 `app/pipeline.py::_ConcreteTrainingPipeline._load`**가 진다(`settlements_bytes is None` → `PipelineFailed(DATASET_UNREADABLE, SETTLEMENTS_ABSENT)`) |
| D-5E-5 | `adapters/artifact_files.py::write_artifact_files` — `job_dir.mkdir(exist_ok=False)`(덮어쓰기 금지), `file://` uri 둘 |
| D-5E-6 | `serving/policy.py`+`policy/serving-v1.yaml`(값 승인 대기 유지 — 구현은 완료, 값 자체는 팀장·운영자 승인 대상) |
| D-5E-7 | `app/server.py::ServerConfig.from_env` — 환경 7 개 전부 기본값 없음(`tests/app/test_server.py`, 10 test — verifier r1 L-3, 이전 판은 `code_version`(`ML_ENGINE_CODE_VERSION`)을 빠뜨리고 6 개로 셌다) |
| D-5E-8 | `tools/build_hook.py::ContractsBuildPy` + `setup.py`(cmdclass 배선, `pyproject.toml` `[build-system] requires` 에 `grpcio-tools` 추가 — 계약 (c) 범위) — `tests/gates/test_wheel_reexport.py`(S-11, 저장소 밖 venv) |
| D-5E-9 | `pyproject.toml` dev extras 에 `grpc-stubs==1.53.0.6` — `[[tool.mypy.overrides]]` 추가 0 |
| D-5E-10 | `tools/contract-crosslang-smoke.sh`·`tests/crosslang_smoke_server.py` 무편집(S-12 그대로 통과) |
| D-5E-11 | `training/jobs/pipeline.py::map_dataset_unreadable`·`map_dataset_rejected`·`map_training_rejected`·`map_holdout_rejected` — 아래 매핑 표. `tests/training/test_jobs_pipeline.py`가 각 enum 의 **전 값**을 순회해 매핑을 확인(전수) |

## 거부 사유 → `JobFailureCode` 매핑 표(D-5E-11 코드 실물)

| 출처 enum | 값 | → `JobFailureCode` | `detail_code`(`DetailCode`) |
| --- | --- | --- | --- |
| `DatasetUnreadableReason`(adapters) | `UNSUPPORTED_SCHEME`·`NOT_FOUND`·`NOT_A_DIRECTORY` | `DATASET_UNREADABLE` | 값 그대로 |
| `DatasetRejectionReason`(training.dataset) | `CHECKSUM_MISMATCH`·`ROWS_CHECKSUM_MISMATCH`·`SETTLEMENTS_CHECKSUM_MISMATCH` | `DATASET_CHECKSUM_MISMATCH` | 값 그대로 |
| " | `UNREADABLE`·`SCHEMA_UNSUPPORTED`·`ID_MISMATCH` | `DATASET_UNREADABLE` | 값 그대로 |
| `TrainingRejectionReason`(training.train) | 4값 전부 | `TRAINING_ERROR` | 값 그대로 |
| `HoldoutRejectionReason`(training.holdout) | 3값 전부 | `EVALUATION_ERROR` | 값 그대로 |
| (파이프라인 자체 실패) | settlements 파일 부재 | `DATASET_UNREADABLE` | `SETTLEMENTS_ABSENT` |
| " | `write_artifact` 구조 실패(`NameMismatch`/`CanonicalizationRejected`) | `TRAINING_ERROR` | `ARTIFACT_WRITE_REJECTED` |
| " | report 정규화 실패 | `EVALUATION_ERROR` | `REPORT_CANONICALIZATION_REJECTED` |
| " | 출력 디렉터리 이미 존재 | `TRAINING_ERROR` | `OUTPUT_DIR_NOT_EMPTY` |
| " | runner 콜백 예외(분류되지 않은 크래시) | `TRAINING_ERROR` | `RUNNER_CRASHED` |

## (2b) 값 획득 축 — 실측

scope.md 표 그대로 승인. 실측 대상 「경계로 처리」 행(servicer 가 store 값을 검증 없이
나르는지):

- `training/jobs/servicer.py::_to_proto_job`은 `JobRecord`(store 스냅샷)의 필드를 proto
  메시지로 **그대로** 옮긴다 — 별도 검증 없음. `JobRecord.__post_init__`(조합 불변식)이
  이미 강제했으므로 servicer 단에서 재검증은 없다(의도된 「경계로 처리」).
- 위조 store 실측: `tests/serving/test_grpc.py::_BlockingStore`가 `InMemoryJobStore`가
  아닌 임의 객체를 `TrainingJobServicer(store=...)`에 주입해도 servicer 생성자가 타입을
  강제하지 않음을 실측했다(구조적 타이핑, `store` 프로토콜은 `.get`·`.start_or_reuse`·
  `.replace` 세 메서드만 — 조립 근 밖에서 부를 이유가 없어 이 경계는 신뢰 영역으로 남긴다,
  scope.md (2b) 표와 동일 판단).
- `training/jobs/pipeline.py::TrainingPipeline` Protocol 도 같은 성질 — `app/pipeline.py`
  가 만든 실물 외의 구현이 주입돼도 결과 타입(`PipelineOutcome`/`PipelineFailed`/
  `PipelineCancelled`) 셋 밖의 값을 반환하면 `runner.py::_on_done`이 무엇을 하는지는
  타입 시스템(`Future[PipelineOutcome | PipelineFailed | PipelineCancelled]`)이 이미
  막는다 — 네 번째 갈래를 받아들이는 코드 경로 자체가 없다(mypy `warn_unreachable`이
  방증, `runner.py` 구현 노트 참고).
- 「`object` 커널을 세야 할 때 무엇을 주입하는가」— `TrainingJobServicer`·
  `BidPredictionServicer`·`EmbeddingServicer` 생성자 인자는 전부 `app/server.py`(조립
  근)가 주입한다. `pipeline_factory: Callable[[TrainingSpec], TrainingPipeline]`도
  마찬가지 — 조립 근 밖에서 이 자리에 꽂을 경로가 없다(생성자는 `training.jobs.servicer`
  모듈 밖으로 공개되지만, 호출자는 `app.server` 하나뿐).
- 이번 라운드(fix round 1)가 새로 만든 이름 중 `app/server.py::_graceful_shutdown_sequence`와
  `TrainingJobServicer._validate_get_or_cancel`은 밑줄 접두 비공개라 (2b) 표 대상이
  아니다(모듈 안에서만 호출, `__all__` 미등재). 공개 표면인 것만 아래 표에 추가했다
  (`InMemoryJobStore.apply_transition`·`JobRunner.cancel_all`).

## 새 public 표면과 그것이 밖에 허락하는 것

| 표면 | 허락하는 것 |
| --- | --- |
| `ml_engine.serving.build_server`·`Servicers` | 이미 만들어진 servicer 인스턴스 셋으로 grpc 서버를 만든다 — servicer 실물 생성 권한은 안 준다 |
| `ml_engine.serving.ReadinessGate`(`from_preload`·`mark_not_ready`·`begin_shutdown`) | 상태 전이만(직접 대입 없음) — 호출자가 임의 `ReadinessSnapshot`을 주입할 순 없다. `mark_ready()`는 verifier r1 M-5 로 제거했다(생성자 없이 인자 없이 READY 로 만드는 경로 — production 호출자 0, READY 도달은 `from_preload` 하나뿐) |
| `ml_engine.training.jobs.InMemoryJobStore`·`JobRunner` | job 저장·실행 — 둘 다 `app.server`만 구성한다. 외부에서 `store.replace()`로 임의 `JobRecord`를 넣을 수 있지만 `JobRecord` 생성자 불변식이 여전히 막는다(조합·시각 불변식) |
| `ml_engine.training.jobs.transition` | 유일 전이 진입점 — 표 밖 전이는 여전히 `TransitionRejected` |
| `InMemoryJobStore.apply_transition`(이번 라운드 신설, H-2) | 읽기·`transition()` 계산·쓰기를 한 잠금 아래 원자적으로 — 새 권한 아님(`transition`이 이미 public, 종전엔 호출자가 get+transition+replace 세 호출로 직접 합성해 경합이 났다). 표 밖 전이를 열지 않는다(`transition()`과 같은 결과 타입) |
| `JobRunner.cancel_all`(이번 라운드 신설, M-3) | 인자 없이 그 시점 진행 중인 job 전부의 취소 토큰을 세운다 — 종전엔 `TrainingJobServicer.CancelTrainingJob`으로 job 하나씩만 취소할 수 있었으므로 이건 새 권한(일괄 취소)이다. 호출자는 `app.server`의 SIGTERM 경로 하나뿐(조립 근 밖에서 부를 이유 없음, 다른 행과 같은 신뢰 판단) |
| `ml_engine.adapters.write_artifact_files` | `file://`만, 존재하는 디렉터리에는 못 쓴다(덮어쓰기 불가) |
| `ml_engine.app.pipeline_factory`·`build_training_pipeline` | training/evaluation 정책·trainer·code_version·출력 디렉터리를 고정한 뒤 `TrainingSpec → TrainingPipeline`만 남긴다 — dataset 내용에 대한 권한은 여전히 `load_dataset`(5C-1)이 검증 |
| `ml_engine.app.server.ServerConfig.from_env`·`run`·`main` | 환경 7 개를 읽어 서버를 하나 만든다 — 조립 근 밖에서 부를 이유 없음(진입점) |

## 알려진 제한

1. **판정(`CalculateOptimalBid`) 미가용** — 5E-2 가 2F 병합 뒤 채운다(D-5E-3).
2. **임베딩 모델 부재** — `OPEN-5E-EMBEDDING-MODEL`(5F/6C).
3. **job 비영속** — 프로세스 재시작 시 job 소실(`OPEN-5E-JOB-PERSISTENCE`, M6 6B).
4. **창 단위 취소 정밀도** — `run_holdout` 내부 창 루프는 취소를 확인하지 않는다
   (`OPEN-5E-CANCEL-GRANULARITY`, 5C-2 파일 편집 필요라 후속).
5. **`requested_release_id` 거부** — 설계 검토 우회 (14), 값이 오면 항상
   `INVALID_REQUEST(REQUESTED_RELEASE_ID_UNSUPPORTED)`(파생 release_id 와 대조할 방법이
   없다 — 학습 전이므로).
6. **다중 인스턴스 job 공유 없음** — `InMemoryJobStore`는 프로세스 로컬.
7. **`embedding_text_max_chars` 두 파일** — `ml-engine/policy/serving-v1.yaml`과 Kotlin
   `config/quality/contract-policy.properties`에 같은 값이 두 자리로 존재(값 동일성은
   `tests/serving/test_policy_kotlin_parity.py`가 지키지만, 값 갱신 시 둘 다 고쳐야 한다).
8. **wheel 빌드 격리 의존** — `[build-system] requires`에 `grpcio-tools`를 추가해야
   `setup.py`의 `cmdclass` 등록이 성립한다(실측, D-5E-8 구현 노트) — `uv build`가 항상
   격리 환경을 새로 만드므로 이 의존이 없으면 빌드 자체가 실패한다.
9. **S-12b 는 커밋되는 자동 test 가 아니다** — 로컬 1회 스모크(commands.md 기록)이고,
   출하 `inference-v1.yaml`이 의도적으로 미완성(D-5D2 결정)이라 그대로는 `StartTraining
   ACCEPTED` 경로를 못 보인다 — 임시 완성 사본으로 배선만 확인했다(정책 값 결정 아님).
10. **job 제출 큐가 무한이다**(verifier r1 L-6) — `JobRunner`의 `ThreadPoolExecutor`는
    기본 큐로, `job_workers`를 넘는 제출이 쌓이기만 하고 2C 어휘의
    `WORKER_RESOURCE_EXHAUSTED`는 proto 매핑만 있고 생산 경로가 없다. 상한을 두려면
    큐 정책(거부·대기·백프레셔) 설계가 먼저 필요해 이번 라운드에서 만들지 않았다
    (`OPEN-5E-JOB-QUEUE-BOUND`, 5E-2 또는 6B).
11. **`add_done_callback` 순서 경계 사례**(verifier r1 L-7) — `executor.submit` 뒤에
    콜백을 등록하므로, 파이프라인이 그 사이 이미 끝나 있으면 `_on_done`이 (스레드
    풀이 아니라) 호출 스레드에서 동기 실행된다(실측). 결과 값은 같지만(`apply_transition`
    이 H-2 로 원자적이라 어느 스레드에서 불려도 안전) 「즉시 반환」 문서 서술의
    경계 사례로 등재만 한다 — 동작 자체는 안전하다.
12. **빈 `settlements.jsonl`은 정직한 미평가로 초록이 된다**(verifier r1 L-8) —
    0바이트 파일 + 일치하는 checksum 이면 job 은 `SUCCEEDED`이고 report 는
    `promotion={"kind":"not_evaluable","reason":"NO_EVALUABLE_WINDOW"}`다. 거짓
    초록이 아니다 — D-5E-4 가 강제하는 것은 파일의 **존재**이지 관측이 **있다**는
    사실이 아니다. 관측 0건에서 승격을 거부하는 판단은 이미 report 계약(`kind`)이
    표현하므로 이 자체를 결함으로 보지 않는다.
13. **fix round 1 처분 요약**(verifier r1 L-9, code-reviewer 판정도 함께) — verifier
    `not-ready`(HIGH 3·MEDIUM 8·LOW 9)와 code-reviewer 「머지 불가(HIGH 2)」를 모두
    이 라운드에서 처분했다: H-1·H-2·H-3·M-1·M-2·M-3·M-4·M-5·M-6·M-7·M-8/L-5는 코드
    또는 evidence 수정으로 닫았고(각 finding 별 독립 커밋, RED→GREEN), L-1(scope.md)
    은 팀장 소관이라 이 라운드가 건드리지 않았다. L-2(수치 서술)는 이 라운드 마지막
    커밋 뒤 S-1~S-12 재실행으로 자연히 갱신했다(수치는 그 실행 결과가 정본). L-3(환경
    개수)은 이 항목 위 D-5E-7 행에서 정정했다. L-4(rollback.md)는 이 라운드 끝에
    `git diff --name-status d78e162..HEAD`로 전면 재생성했다. L-6·L-7·L-8은 위
    10~12항으로 등재했다. code-reviewer 의 두 HIGH는 verifier H-1·H-2와 같은 자리를
    가리켜(중복 지목) 위 두 항목의 수정으로 함께 닫혔다.

## 열린 항목(이 라운드가 새로 낸 것)

- `OPEN-5E-YAML-LOADER-INFERENCE` — **부팅 경로는 fix round 2 로 닫혔다.** 원인은
  H-1과 같은 결함 계열(`yaml.YAMLError`를 `PolicyRejected`로 감싸지 않음)이
  `inference/policy.py`에도 있던 것이었고, verifier r2 가 실측한 잔존 증상(`app.
  server.run()`이 문법 깨진 inference YAML 에서 처리되지 않은 예외로 죽는다)은
  `app/server.py::_load_inference_policy_safe`(호출부, in_scope)가 `yaml.YAMLError`
  를 잡아 `InferencePolicyRejected`로 정규화해 없앴다 — `inference/policy.py`
  자체는 여전히 무방비다(팀장 지시로 범위 밖). 남은 것은 **로더 자체 정정**뿐이다
  — training/evaluation/serving 세 곳은 이미 같은 버그가 반복돼(PR#13 HIGH-2·
  evaluation·5E-1 fix round 1 serving) 왔으므로, `inference`까지 넷을 공유 로더로
  통합할지 이 파일만 개별 수정할지는 다음 라운드 또는 5E-2 착수 계약에서 결정한다.
- `OPEN-5E-JOB-QUEUE-BOUND` — 위 10항.

## 계약과 어긋나 판단이 필요했던 자리

- **pyproject.toml `ignore_imports`를 두 계약에 나눠 추가**했다 — scope.md 원문은
  "serving/inference 는 grpc 금지" 계약 하나에 두 줄을 다 넣으라고 읽히지만,
  `training.jobs.servicer`는 그 계약의 source_modules 가 아니라서 그대로 넣으면
  import-linter 가 "No matches for ignored import"로 설정 오류를 낸다(실측). 대신
  "features/training/evaluation/registry" 계약에 `grpc`를 forbidden_modules 로 추가하고
  그 계약에 `training.jobs.servicer -> grpc` 예외를 달았다 — 의도(두 grpc 진입점만 예외)는
  그대로 지키고 계약 배치만 정정했다.
- **`[build-system] requires`에 `grpcio-tools` 추가** — scope.md (c)는 "wheel 빌드 훅
  설정"만 말했지만, `setup.py`가 `tools/build_hook.py`를 import 하려면 그 자체가 이미
  `grpc_tools`를 요구해(연쇄 import) PEP 517 격리 빌드 환경에 없으면 `get_requires_for_
  build_wheel` 단계에서부터 실패한다(실측). `dev` extras 의 `grpcio-tools==1.83.1`과 같은
  버전으로 고정했다.
- **`ml_engine.app.server.py`가 `ConfigRejected`가 아니라 `ConfigError`로 명명** —
  `ruff` N818(예외 이름은 `Error` 접미사)에 걸려 이름을 바꿨다(스코프 문서의 서술적
  이름과 다름, 기능은 동일).
