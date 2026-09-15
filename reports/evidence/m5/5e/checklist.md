# M5/5E-1 — checklist.md

## M5 완료 조건 담당 근거

| 완료 조건(milestone-5.md) | 5E-1 근거 |
| --- | --- |
| 생성 servicer(3 개) 등록 | `serving/grpc.py::build_server` — `BidPredictionService`·`EmbeddingService`·`TrainingJobService` 한 서버(ADR 0010 D-8), `tests/serving/test_grpc.py::test_build_server_registers_three_services_and_serves` 실측(실 socket) |
| preload/readiness | `serving/readiness.py::ReadinessGate.from_preload` — 정책 넷 preload 결과를 `PreloadOutcome` 으로 정규화해 받는다(하나라도 실패 → `NOT_READY`). `app/server.py::_preload`가 조립. `tests/serving/test_readiness.py` |
| serving 패키지에 DB driver·ORM 없음 | pyproject.toml forbidden 계약(무편집 부분) + `tests/gates/test_serving_purity.py`(실행 시점 재확인, `ml_engine.app` 비대상 명시 test 추가) |
| checksum 불일치 시 readiness fail-closed | dataset checksum 불일치는 job 안에서 `FAILED`(2C 문면, D-5C-8 승계) — 정책 자체의 로드 실패는 `ReadinessGate`가 `NOT_READY`로(§ 위) |
| validation·Numpy conversion | envelope·필드 검증은 `serving/status.py`(`ValidationDetailCode`)·`training/jobs/servicer.py`(독립 소유, layer 경계). Numpy 변환은 5D-2 `serve_bid_rates` 내부(5E-2 대상, 5E-1 무관) |
| deadline/cancel/status 매핑 | deadline: `serving/grpc.py::is_deadline_active`(D-2). cancel: `CancelToken` + job 단계 경계 넷(D-2D-7, `tests/training/test_jobs_runner.py`). status: `serving/status.py::fill_application_failure`(ADR 0010 D-3) |
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
| D-5E-7 | `app/server.py::ServerConfig.from_env` — 환경 6 개 전부 기본값 없음(`tests/app/test_server.py`, 10 test) |
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

## 새 public 표면과 그것이 밖에 허락하는 것

| 표면 | 허락하는 것 |
| --- | --- |
| `ml_engine.serving.build_server`·`Servicers` | 이미 만들어진 servicer 인스턴스 셋으로 grpc 서버를 만든다 — servicer 실물 생성 권한은 안 준다 |
| `ml_engine.serving.ReadinessGate`(`mark_ready`·`mark_not_ready`·`begin_shutdown`) | 상태 전이만(직접 대입 없음) — 호출자가 임의 `ReadinessSnapshot`을 주입할 순 없다 |
| `ml_engine.training.jobs.InMemoryJobStore`·`JobRunner` | job 저장·실행 — 둘 다 `app.server`만 구성한다. 외부에서 `store.replace()`로 임의 `JobRecord`를 넣을 수 있지만 `JobRecord` 생성자 불변식이 여전히 막는다(조합·시각 불변식) |
| `ml_engine.training.jobs.transition` | 유일 전이 진입점 — 표 밖 전이는 여전히 `TransitionRejected` |
| `ml_engine.adapters.write_artifact_files` | `file://`만, 존재하는 디렉터리에는 못 쓴다(덮어쓰기 불가) |
| `ml_engine.app.pipeline_factory`·`build_training_pipeline` | training/evaluation 정책·trainer·code_version·출력 디렉터리를 고정한 뒤 `TrainingSpec → TrainingPipeline`만 남긴다 — dataset 내용에 대한 권한은 여전히 `load_dataset`(5C-1)이 검증 |
| `ml_engine.app.server.ServerConfig.from_env`·`run`·`main` | 환경 6 개를 읽어 서버를 하나 만든다 — 조립 근 밖에서 부를 이유 없음(진입점) |

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
