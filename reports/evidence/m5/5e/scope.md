# Slice 계약 — M5 / 5E-1 · gRPC serving(서버 골격·readiness·metadata·training job service·bounded concurrency·graceful shutdown·wheel 빌드 훅) — **착수 계약 2026-09-16(사용자 지시 「5E 착수」 · 운영자 확인 대기: D-5E-1·2·3·4·6)**

> **지위**: 2026-09-16 착수판(세션 모델 단독, Fable 5.1). 조사 노트는 5C 와 공유(`_workspace/m5-5e/01_scout_training.md` §0·§5-5 artifact provider fork-safe 캐시·`02_scout_evaluation.md` §2-4 승격 경로) + 이 계약의 「착수 조사」 절(M2 계약 넷·5A~5D-2 공개 표면·2F 진행 상태 실측). **Phase 2.5 설계 검토 대상**(readiness fail-closed·job 상태 기계·bounded concurrency·경계 층): `_workspace/m5-5e/03_design-review.md`.
> **5E 를 둘로 가른다(D-5E-0)** — **5E-1** 서버 골격 + `GetModelMetadata`·`TrainingJobService`·`EmbeddingService`(NOT_READY) + 운영 성질(deadline·cancel·status·shutdown·concurrency·wheel) (이 계약) · **5E-2** `CalculateOptimalBid` wire 매핑(분포 엔진 `serve_bid_rates` → v1+2F wire: `release_kind=DERIVED`·`interval_source=POSTERIOR_PREDICTIVE`·Diagnostics 넷) — **2F(M2 additive, 병행 레인 `m2-2f/2026-09-15`, 미병합)가 `main` 에 든 뒤** 별도 계약. 이유는 D-5E-3.
> 레인: worktree `bid-vector-v2-m5e`, 브랜치 `m5-5e/2026-09-16`, base `d78e162`(PR #15 머지 = origin/main, 5A~5D-2·5C-1·5C-2 실물). 병행 레인 **2F**(`bid-vector-v2-m4e`) — 소스 겹침: `ml-engine/tests/test_prediction_contract.py`(2F in_scope) 하나, 5E-1 은 무편집.

```yaml
milestone: m5
slice: 5e1-grpc-serving-frame
base_sha: d78e16239afe103d524a61234fb1696e81d5f499   # PR #15 머지 커밋 = origin/main
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/serving/__init__.py             # 공개 표면 재수출
  - ml-engine/src/ml_engine/serving/policy.py               # ServingPolicy(frozen) + load_serving_policy(path) -> ServingPolicy | PolicyRejected — max_workers·max_concurrent_rpcs·shutdown_grace_seconds·job_workers·embedding_text_max_chars·dataset_uri_schemes(D-5E-6) · SHIPPED_SERVING_POLICY_VERSION="serving-v1"
  - ml-engine/src/ml_engine/serving/readiness.py            # Readiness 상태 기계(LOADING → READY | NOT_READY, 종료 시 NOT_READY 선행) · ReadinessGate(preload 결과 셋: inference 정책·evaluation 정책·training 정책 로드 결과 — 하나라도 PolicyRejected 면 NOT_READY, 사유 보존)
  - ml-engine/src/ml_engine/serving/prediction.py           # BidPredictionServicer — GetModelMetadata(readiness·supported_feature_schema_versions=5B SUPPORTED_FEATURE_SCHEMAS·promoted 는 5E-2 까지 미설정) · CalculateOptimalBid 는 **UNIMPLEMENTED**(D-5E-3 (a), 5E-2 가 채움) · envelope 검증(request_id·correlation_id 비어 있지 않음, feature_schema_version 지원, selector 소진)
  - ml-engine/src/ml_engine/serving/embedding.py            # EmbeddingServicer — GetEmbeddingMetadata(readiness NOT_READY·dimension 0·supported_text_kinds 빈) · EmbedText → ApplicationFailure(MODEL_NOT_READY, retryable=false, detail_code=EMBEDDING_MODEL_ABSENT) (D-5E-2) · text 검증(빈/공백·상한 → INVALID_REQUEST — 실물 없어도 계약 ④ 는 지킨다)
  - ml-engine/src/ml_engine/serving/status.py               # ADR 0010 D-3 표의 Python 쪽: 결과 타입 → oneof(Success/Unmeasurable/ApplicationFailure) 조립 규칙 · 예기치 않은 예외는 삼키지 않고 gRPC INTERNAL(context.abort) — 넓은 except 없음(구체 예외만)
  - ml-engine/src/ml_engine/serving/grpc.py                 # 유일 grpc import 진입점(5A ③ — import-linter ignore_imports 한 줄, OPEN-5A-SERVING-GRPC-EXCEPTION) · build_server(policy, servicers) -> grpc.Server(ThreadPoolExecutor(max_workers), maximum_concurrent_rpcs) · deadline 확인 헬퍼(context.is_active() 를 긴 계산 앞에서 — D-2) · graceful shutdown(SIGTERM → readiness NOT_READY → server.stop(grace) → 대기)
  - ml-engine/src/ml_engine/training/jobs/__init__.py       # 공개 표면
  - ml-engine/src/ml_engine/training/jobs/state.py          # JobState 전이표(2C ④: ACCEPTED→RUNNING→SUCCEEDED|FAILED, ACCEPTED|RUNNING→CANCELLED, 종료 상태 Cancel 은 멱등 no-op) — 표에 없는 전이는 TransitionRejected 결과 타입 · JobRecord(frozen 스냅샷: job_id·state·accepted_at·started_at·finished_at·artifact_ref·evaluation_ref·failure) · 조합 불변식(SUCCEEDED ⟹ artifact∧evaluation, FAILED ⟹ failure, 그 밖은 셋 미설정)을 생성자에서 강제
  - ml-engine/src/ml_engine/training/jobs/store.py          # InMemoryJobStore(프로세스 로컬 dict, 잠금) · idempotency_key → job_id(같은 키+같은 dataset = 같은 job, 같은 키+다른 dataset = IDEMPOTENCY_CONFLICT) · job_id 는 uuid4(2C D-2C-3)
  - ml-engine/src/ml_engine/training/jobs/runner.py         # JobRunner(ThreadPoolExecutor(max_workers=policy.job_workers), fork 없음 — ADR 0010 §6) · run(job_id, pipeline: TrainingPipeline) · 취소 존중: pipeline 이 단계 경계마다 cancel_token 을 확인(계산 중단 = 자원 해제, D-2D-7) · 예외는 JobFailure(TRAINING_ERROR|EVALUATION_ERROR, detail_code) 로(구체 결과 타입 매핑, 넓은 except 없음)
  - ml-engine/src/ml_engine/training/jobs/pipeline.py       # TrainingPipeline Protocol(dataset_ref, cancel_token) -> PipelineOutcome(artifact_bytes·report_bytes·refs) | PipelineFailed(JobFailureCode, detail_code) — **실물 조립은 app 층**(D-5E-1) · 5C-1 TrainingRejected·DatasetRejected·DatasetUnreadable → JobFailureCode 매핑 표(5C-1 D-5C-11·5C-2 D-5C2-12 승계) 를 이 모듈이 소유
  - ml-engine/src/ml_engine/training/jobs/servicer.py       # TrainingJobServicer — StartTraining(envelope·idempotency_key 비어 있지 않음·training_spec_version → resolve_training_spec 미지 = UNSUPPORTED_TRAINING_SPEC·dataset.uri scheme ∈ policy·manifest_checksum 형식 sha256 hex 64) → ACCEPTED 즉시 반환 · GetTrainingJob(미지 = JOB_NOT_FOUND) · CancelTrainingJob(전이표)
  - ml-engine/src/ml_engine/app/__init__.py                 # **조립 근(composition root, 새 최상위 패키지 — layers 계약 밖, D-5E-1)** — serving·training.jobs·inference·adapters 를 여기서만 잇는다
  - ml-engine/src/ml_engine/app/pipeline.py                 # build_training_pipeline(spec, policies, trainer, code_version) -> TrainingPipeline: read_dataset_files(5C-1) → load_dataset → train_award_rate_gbm → write_artifact → settlements → build_weekly_maturity(5D K7) → run_holdout(5C-2) → report canonical bytes → refs(file:// 산출 디렉터리, D-5E-4·D-5E-5)
  - ml-engine/src/ml_engine/app/server.py                   # `python -m ml_engine.app.server` — 환경 6(5A 표 #24~#26·#30 + port·policy 경로·artifact 출력 디렉터리)을 env 로 읽는 설정 객체(D-5E-7) · preload(정책 셋) → readiness → 세 servicer 등록 → serve · SIGTERM 핸들러
  - ml-engine/src/ml_engine/training/dataset.py             # 5C-1 파일 — DatasetManifestV1 에 `settlements_checksum` 필수 필드 추가 + rows 와 같은 검증(D-5E-4, hunk 격리) 
  - ml-engine/src/ml_engine/adapters/dataset_files.py       # 5C-1 파일 — `settlements.jsonl` 세 번째 파일 읽기(D-5E-4, hunk 격리)
  - ml-engine/src/ml_engine/adapters/artifact_files.py      # 신규 — write_artifact_files(dir, artifact_bytes, report_bytes) -> ArtifactRefs(file:// uri 둘 + checksum) · 쓰기 전 디렉터리 존재·비어 있음 검증(덮어쓰기 금지)
  - ml-engine/src/ml_engine/adapters/__init__.py           # `write_artifact_files`·`ArtifactRefs` 재수출 한 줄. 계약 갱신 (3)
  - ml-engine/policy/serving-v1.yaml                        # 정책 값 실물(D-5E-6, OPEN-5E-POLICY-VALUES)
  - ml-engine/pyproject.toml                                # (a) import-linter forbidden 계약(serving/inference 는 grpc 금지)에 `ignore_imports = ["ml_engine.serving.grpc -> grpc", "ml_engine.training.jobs.servicer -> grpc"]` (b) `[project.scripts]` 또는 `-m` 진입 문서 (c) wheel 빌드 훅 설정(D-5E-8 — 생성 stub 을 wheel 의 **별도 top-level 패키지 `bidvector/`** 로, 소스 트리·`ml_engine` 패키지 안엔 두지 않음) (d) serving extras 에 `grpcio` 는 이미 base 의존 — 변경 0 확인 · dev extras 에 `grpc-stubs`(D-5E-9) · `[tool.ruff]`·래칫·layers 무편집
  - ml-engine/uv.lock                                      # D-5E-9 `grpc-stubs`·(c) `grpcio-tools` build-system 갱신분(D-5E-9 산문에만 있던 것을 목록에). 계약 갱신 (3)
  - ml-engine/tools/build_hook.py                           # D-5E-8 — setuptools `build_py` 서브클래스: `generate_contracts.generate(<build_lib>)` 로 `<build_lib>/bidvector/ml/v1/*_pb2*.py` 생성(5A `tools/generate_contracts.py` 재사용) · `contracts/__init__.py` 무편집(설치본에선 절대 import 가 sys.path 조작 없이 성립 — S-11 실측)
  - ml-engine/setup.py                                     # D-5E-8 배선 — `cmdclass={"build_py": ContractsBuildPy}` 만(PEP 517 훅은 pyproject 만으로 cmdclass 를 못 건다). 계약 갱신 2026-09-16 (2): 구현 실측으로 추가
  - ml-engine/tests/serving/**                              # RED 먼저 — in-process grpc(grpc_testing 또는 insecure 포트 0) 로 세 servicer test · readiness 규칙표 · 전이표 property · idempotency · deadline/cancel(자원 해제 카운터) · concurrency 상한(동시 N+1 번째 RESOURCE_EXHAUSTED) · shutdown 순서 · status 매핑 전수 · 2C 조합 불변식
  - ml-engine/tests/training/test_jobs_*.py
  - ml-engine/tests/app/**                                  # pipeline 통합(fake trainer + 실 LightGBM 1건: dataset 디렉터리 → artifact·report 파일 → refs checksum 재계산) · server 부팅·종료
  - ml-engine/tests/gates/test_serving_purity.py            # 기존 test 무편집 + `ml_engine.app` 은 대상 아님을 명시하는 test 1(app 이 training 을 끌어와도 serving 패키지 자체는 안 끌어옴)
  - ml-engine/tests/gates/test_wheel_reexport.py            # S-11 — `uv build` → 임시 venv 설치 → `import ml_engine.contracts` + servicer import 성립(OPEN-5A-WHEEL-BUILD-HOOK 종결 증거)
  - .github/workflows/ci.yml                                # ml-engine job 에 S-11 step 추가(Python job hunk 만, Kotlin job 무편집)
  - config/quality/leak-pattern-baseline.txt              # 내용 해시 키 항목 추가만 — **진짜 식별자**(클래스 이름 등)의 오탐에 한정, evidence 가 스캔 어휘를 인용해 생긴 자기매치를 baseline 으로 덮지 않는다(하네스 규칙 2026-09-16). 계약 갱신 (2)
  - milestone-5.md                                          # 5E 절 착수 문단 + 5E-1/5E-2 분할
  - reports/evidence/m5/5e/**
out_of_scope:
  - **`CalculateOptimalBid` 의 wire 매핑**(5E-2) — 분포 엔진 `serve_bid_rates` 결과를 v1+2F wire 로: `ModelRelease{release_kind=DERIVED, release_id="distribution/<policy version>", artifact_checksum="sha256:"+sha256(정규화 정책 YAML), feature_schema_version=echo, code_version, dataset_id=""}`(2F ④) · `interval_source=POSTERIOR_PREDICTIVE`(2F ③) · Diagnostics 넷(2F ②) · `GetModelMetadata.promoted` 설정 · GBM 경로(`predict_bid_rates` + artifact 승격 registry — 5D-2 운영자 결정 (b) 「분포 단독」이라 서빙 경로 아님, 알려진 제한)
  - 임베딩 모델 실물(sentence-transformers 384 + 해시 fallback 은 legacy 실물일 뿐 — D-5E-2, `OPEN-5E-EMBEDDING-MODEL` → 후속 slice 5F 또는 6C 결정)
  - job 영속화(프로세스 재시작 시 job 소실 — M6 6B persistence, `OPEN-5E-JOB-PERSISTENCE`) · 다중 인스턴스 job 공유 · object storage(`s3://`, 6C — `OPEN-2C-DATASET-URI-SCHEME` 유지, 5E-1 은 `file://` 만)
  - 인증·권한(M6 6A) · TLS(6C) · 컨테이너·헬스 엔드포인트(6C — readiness 는 gRPC metadata 응답으로만) · 관측(로그·메트릭 파이프라인, 6E — 5E-1 은 stdlib logging 으로 request_id·correlation_id·job_id 만)
  - `contracts/**` 편집(2F 레인) · `ml-engine/tests/test_prediction_contract.py`(2F in_scope) · 5B/5D/5D-2 파일 · 5C-1 파일 중 dataset.py·dataset_files.py 외 · 5C-2 파일 · `ruff select`·래칫·layers 편집 · 5A `policy-values.md`
  - 승격(promotion) 실행 — evaluation report 는 참조만 반환(2C ⑧, ML-07 운영자 결정)
acceptance_commands:
  # 정본 = CI `ml-engine` job 전건 + 이 slice 가 더하는 S-11. evidence 편집 라운드는 Kotlin `check`(S-10) 도.
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                 # S-1
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c \"import $m\" || exit 1; done && uv sync --frozen --all-extras)"   # S-1b — serving extras 만으로 `ml_engine.serving` import 성립도 추가 단언(S-1c)
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && uv run --no-sync python -c \"import ml_engine.serving, ml_engine.serving.grpc\" && uv sync --frozen --all-extras)"   # S-1c
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3 — override 추가 0(grpc 타입 stub 은 grpc-stubs 를 dev 에 — D-5E-9)
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4 — ignore_imports 둘 외 계약 무편집, serving 은 training/adapters/inference 외 금지 유지, app 은 layers 밖
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                              # S-5
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
  - "./gradlew --no-daemon check"                                                                      # S-10 — evidence 편집 라운드 한정
  - "(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)"   # S-11 — wheel 설치본에서 `ml_engine.contracts` 재수출 + servicer import(OPEN-5A-WHEEL-BUILD-HOOK)
  - "./tools/contract-crosslang-smoke.sh"                                                             # S-12 — 2D 교차 언어 socket 스모크가 **실 servicer 셋**(fake 아님)에 대해 여전히 성립(착수·리뷰 요청 시 로컬 1회, D-2D-3 (a))
rollback: |
    **정본 `reports/evidence/m5/5e/rollback.md`**(구현 레인). `serving/*.py`(신규 5)·`training/jobs/**`·`app/**`·`adapters/artifact_files.py`·`policy/serving-v1.yaml`·`tools/build_hook.py`·`tests/{serving,app}/**`·`tests/training/test_jobs_*.py`·`tests/gates/test_wheel_reexport.py` 삭제 +
    공유 파일 hunk 격리: `pyproject.toml`(5A·5C-1·5D 줄 잔존)·`training/dataset.py`·`adapters/dataset_files.py`(5C-1 줄 잔존)·`tests/gates/test_serving_purity.py`·`.github/workflows/ci.yml`(Kotlin job 무접촉)·`serving/__init__.py`(5A 경계 한 줄 복원). scope.md 는 착수 경계 단일 역적용 + 하네스 절 재등재(evidence-pack 2026-09-16). `milestone-5.md` 문서 레인 제외.
    임시 worktree 실측 → in_scope diff 0 → S-1~S-7·S-9 초록(`main` test 수 직접 계수) + gradle check.
```

작성: 2026-09-16 착수판 — 세션 모델 단독. 근거: `milestone-5.md` 5E 여섯(생성 servicer·preload/readiness·validation·deadline/cancel/status·release/checksum/schema 응답·graceful shutdown·bounded concurrency)·완료 조건(「serving 패키지에 DB driver·ORM 없음」·「checksum 불일치 시 readiness fail-closed」·「M2 consumer/provider contract 통과」) · `v2-지침서.md` §3.2·§3.3·§5 · ADR 0010 D-2·D-3·D-4·D-6·D-8·§6(fork 금지·취소 존중) · 2A~2E 계약 + 2C 전이표·조합 불변식·D-2C-1~6 · 2D D-2D-3·D-2D-6·D-2D-7 · 4D-1 ②③④(client 가 기대하는 것) · 5A ③ `serving.grpc` 예외·`OPEN-5A-WHEEL-BUILD-HOOK`·환경 6 · 5C-1 D-5C-8·D-5C-11 · 5C-2 D-5C2-2(성숙도 입력)·D-5C2-12 · 5D-2 `serve_bid_rates`·`OPEN-5D2-RELEASE-FOR-DISTRIBUTION`·`OPEN-5D2-INTERVAL-SOURCE-WIRE` · 2F scope(병행) ②③④.

---

## 착수 조사(세션 모델 실측, 2026-09-16)

- **M2 wire 넷**(`contracts/proto/bidvector/ml/v1/*.proto`, origin/main 판): `BidPredictionService{CalculateOptimalBid, GetModelMetadata}`·`EmbeddingService{EmbedText, GetEmbeddingMetadata}`·`TrainingJobService{Start, Get, Cancel}`. `ModelRelease` 5필드(전부 비공백 요구 — 4D-1 `ReleaseShapeValidation`), `IntervalSource` 2값, `Diagnostics` 2필드, `Readiness` 3값, `FailureCode` 7값, `JobFailureCode` 6값.
- **2F(병행 레인, `m2-2f/2026-09-15` @ `0753061`, 미병합)** 가 그 wire 에 `release_kind`(DERIVED — 분포 엔진용)·`INTERVAL_SOURCE_POSTERIOR_PREDICTIVE`·Diagnostics 넷·표본 축 둘을 더한다. 5D-2 의 분포 엔진 결과는 **그 넷 없이는 v1 wire 로 정직하게 표현할 수 없다**(`dataset_id` 공백은 v1 Kotlin 이 계약 위반으로 거부, `interval_source` 는 값이 없음) → D-5E-3.
- **5D-2 `serve_bid_rates(request_pb2, policy) -> KernelResult`** 가 이미 wire 요청을 받는 유일 진입점(`ENGINE="DISTRIBUTION"`, 운영자 결정 2026-09-13 (b) 분포 단독). GBM 경로(`predict_bid_rates` + `load_artifact`)는 서빙 경로가 아니다.
- **layers**: `serving > inference > features`, `training > evaluation > features`; forbidden: serving/inference → training·adapters·grpc(진입점 예외 예정), features/training/evaluation/registry → inference·serving. **training job 은 5C-1(training)·5C-2(training/evaluation)·K7(inference)·adapters 를 한 자리에서 이어야 하는데 어느 층도 그 넷을 다 import 할 수 없다** → 조립 근 `ml_engine.app`(D-5E-1).
- **5C-2 `run_holdout` 은 `WeekMaturity` 입력을 요구**(D-5C2-2)하고 5C-1 dataset 은 `rows.jsonl` 만 — 정산 관측(`opened_at, settled`)이 없다 → D-5E-4.
- **임베딩**: M5 에 임베딩 slice 가 없다. 2E 가 「실 servicer·모델 선택은 M5」로 미뤘고 legacy 실물은 sentence-transformers 384 + 해시 fallback → D-5E-2.
- **5A 인수**: `OPEN-5A-SERVING-GRPC-EXCEPTION`(ignore_imports 한 줄) · `OPEN-5A-WHEEL-BUILD-HOOK`(wheel 설치본에서 `ml_engine.contracts` 재수출 불성립) · 환경 6(`PREFERRED_PREDICTOR`·`ENABLE_EXPERIMENTAL`·`ENSEMBLE_MODEL_PATH`·`GBM_MODEL_PATH` 등 — 5D-2 분포 단독 결정으로 predictor 선택 축은 소멸, 경로 둘은 5E-2/GBM 경로 밖).
- **4D-1 client 가 기대하는 것**: deadline 필수·취소 전파(D-2) · transport 재시도 allow-list(`UNAVAILABLE`·`DEADLINE_EXCEEDED`·`RESOURCE_EXHAUSTED`) · `MODEL_NOT_READY` 재시도 뒤 지속 → `Unavailable` · `latest_promoted` 는 `GetModelMetadata.promoted` 와 대조(D-4D-4) → 5E-1 이 `promoted` 미설정 + `NOT_READY` 를 내면 client 는 `Unavailable(ModelNotReady)` — 정직한 미가용.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **서버 골격**(`serving/grpc.py`·`app/server.py`) — `grpc.server(ThreadPoolExecutor(max_workers=policy.max_workers), maximum_concurrent_rpcs=policy.max_concurrent_rpcs)`(bounded concurrency — N+1 번째 동시 요청은 gRPC `RESOURCE_EXHAUSTED`, 4D-1 이 백오프 재시도) · 세 servicer 를 **한 서버**에 등록(ADR 0010 D-8) · 포트·정책 경로·출력 디렉터리는 env 설정 객체(D-5E-7) · `python -m ml_engine.app.server` | 5E 「graceful shutdown 과 bounded concurrency」 · ADR 0010 D-8 |
| ② | **preload + readiness**(`serving/readiness.py`) — 시동 시 정책 셋(inference-v1·training-v1·evaluation-v1·serving-v1) 로드; 하나라도 `PolicyRejected` 면 `NOT_READY(사유)` 로 **서버는 뜨되 판정 RPC 는 `MODEL_NOT_READY`** — 조용히 기본값으로 뜨지 않는다. 상태 기계 `LOADING → READY | NOT_READY`, 종료 시 `NOT_READY` 가 `server.stop` 보다 **먼저**. 5E-1 에서는 `CalculateOptimalBid` 가 UNIMPLEMENTED 라 `GetModelMetadata.readiness` 는 정책 로드 성공이어도 **`NOT_READY`**(promoted 미설정 — 5E-2 가 READY 를 연다, D-5E-3) | 5E 「model preload 와 readiness」 · 완료 조건 「checksum 불일치 시 readiness fail-closed」(artifact 없는 분포 엔진에서는 정책 checksum 이 그 자리) |
| ③ | **`GetModelMetadata`** — `readiness`·`supported_feature_schema_versions`(5B `SUPPORTED_FEATURE_SCHEMAS` 키)·`promoted` 미설정(5E-2). envelope 검증(request_id·correlation_id 비어 있지 않음). 로그에 request_id·correlation_id | 2B ⑥ · 4D-1 D-4D-4 |
| ④ | **`CalculateOptimalBid` = gRPC `UNIMPLEMENTED`**(생성 servicer 기본 거동 그대로 — 덮어쓰지 않는다) — D-5E-3 (a). 4D-1 client 는 transport status 로 `Unavailable` 처리(재시도 대상 아님) | D-5E-3 |
| ⑤ | **`EmbeddingService`**(`serving/embedding.py`) — `GetEmbeddingMetadata{readiness=NOT_READY, dimension=0, supported_text_kinds=[], promoted 미설정}` · `EmbedText`: envelope·`TextKind`·`text`(빈/공백·상한 `policy.embedding_text_max_chars`) 검증은 계약 ④ 대로 `INVALID_REQUEST`, 검증 통과 요청은 `ApplicationFailure(MODEL_NOT_READY, retryable=false, detail_code="EMBEDDING_MODEL_ABSENT")` — 해시 fallback 없음 | D-5E-2 · 2E ④ · §5 「조용한 fallback 금지」 |
| ⑥ | **`TrainingJobService`**(`training/jobs/*`) — `StartTraining`: 검증(envelope·`idempotency_key` 비어 있지 않음(상한은 정책)·`training_spec_version` → `resolve_training_spec` 미지 = `ApplicationFailure(UNSUPPORTED_TRAINING_SPEC, retryable=false)`·`dataset.uri` scheme ∈ `policy.dataset_uri_schemes`(`file` 만)·`manifest_checksum` 64 hex — 위반 = `INVALID_REQUEST`) → 멱등(같은 키+같은 dataset = 같은 `job_id`, 같은 키+다른 dataset = `IDEMPOTENCY_CONFLICT`) → `ACCEPTED` 즉시 반환(학습을 기다리지 않음, 2C ⑨) · `GetTrainingJob`(미지 = `JOB_NOT_FOUND`) · `CancelTrainingJob`(전이표, 종료 상태 멱등 no-op) · 조합 불변식(2C `TrainingJob` 주석)은 `JobRecord` 생성자가 강제 · 시각 `accepted_at ≤ started_at ≤ finished_at`(UTC) | 2C ①~⑨·D-2C-1~6 · ADR 0010 D-4·D-8 |
| ⑦ | **job 실행기**(`training/jobs/runner.py` + `app/pipeline.py`) — `ThreadPoolExecutor(max_workers=policy.job_workers)`, **fork 없음**(ADR 0010 §6) · 파이프라인: `read_dataset_files` → `load_dataset`(checksum 불일치 = `FAILED(DATASET_CHECKSUM_MISMATCH)`, 읽기 실패 = `DATASET_UNREADABLE`) → `train_award_rate_gbm`(`TrainingRejected` → `FAILED(TRAINING_ERROR, detail_code=reason)`) → `write_artifact` → settlements → `build_weekly_maturity`(5D K7, `maturity.window_days` 는 inference 정책) → `run_holdout`(`HoldoutRejected` → `EVALUATION_ERROR`) → `write_artifact_files`(artifact bytes + report canonical bytes → `file://` refs, checksum = 각 bytes sha256) · **취소 존중**: 단계 경계(dataset 로드 뒤·학습 뒤·창마다는 5C-2 내부라 창 단위는 `OPEN-5E-CANCEL-GRANULARITY`)에서 cancel_token 확인 → `CANCELLED` + `failure` 미설정(전이표) · 성공 = `SUCCEEDED{artifact: ArtifactReference{uri, release(5C-1 `ArtifactBytes` 의 release_id·code_version·dataset_id + feature_schema_version, `artifact_checksum=ArtifactBytes.sha256`), manifest_schema_version="artifact-manifest-v1"}, evaluation: EvaluationReportReference{uri, checksum, report_schema_version="evaluation-report-v1"}}` | 2C ②⑦⑧ · 5C-1 ⑧⑨ · 5C-2 ⑧ · D-2D-7 |
| ⑧ | **dataset 에 정산 관측 추가**(D-5E-4) — 디렉터리에 `settlements.jsonl`(`{opened_at, settled}` 행) 세 번째 파일, `DatasetManifestV1.settlements_checksum` 필수(기본값 없음 — 5B `require_declared` 관례) · 5C-1 `load_dataset` 확장(rows 와 같은 fail-closed) · `adapters/dataset_files.py` 세 파일 읽기 — 5C-1 파일 둘 편집(hunk 격리) · 5C-1 기존 test 의 manifest fixture 에 필드 추가 | 5C-2 D-5C2-2 · SET-06(계산은 ml-engine) |
| ⑨ | **wheel 빌드 훅**(D-5E-8, `OPEN-5A-WHEEL-BUILD-HOOK` 종결) — 빌드 시 `tools/generate_contracts.generate()` 로 stub 을 wheel 의 `ml_engine/contracts/_generated/` 에 넣되 소스 트리엔 두지 않음(D-5A-0 (a) 형태) · `contracts/__init__.py` 재수출이 소스 트리(`.contracts-generated/`)와 설치본 둘 다에서 성립하는지 S-11 이 임시 venv 로 실측 · CI 에 S-11 step | 5A checklist `OPEN-5A-WHEEL-BUILD-HOOK` · 완료 조건 「serving image/package 에 DB driver·ORM 없음」(S-1b 승계) |
| ⑩ | **status 매핑·예외 규율**(`serving/status.py`) — ADR 0010 D-3: 검증 실패·미준비·미지 job 은 **status OK + ApplicationFailure**, 도메인 결과는 Success/Unmeasurable(5E-2), 취소된 요청은 계산 없이 반환(D-2 `context.is_active()`), 예기치 않은 예외는 **삼키지 않는다**(`BLE`) — 구체 예외만 매핑하고 나머지는 grpc 가 `UNKNOWN`/`INTERNAL` 로(전파). 자유 문자열 사유 없음(`detail_code` 는 닫힌 어휘) | ADR 0010 D-3 · 2A ⑤ · D-4D-6 |
| ⑪ | **정책 값**(`policy/serving-v1.yaml`, D-5E-6) — `max_workers`·`max_concurrent_rpcs`·`shutdown_grace_seconds`·`job_workers`·`idempotency_key_max_chars`·`embedding_text_max_chars`·`dataset_uri_schemes.0=file`. 전부 **legacy 근거 없음**(legacy 는 FastAPI+Celery) — 보수적 초기값 + 측정 의무(ADR 0010 D-1) 층 표기 | ADR 0006 D-7 · 2D D-2D-6(`embedding_text_max_chars` 는 Kotlin `contract-policy.properties` 값과 경계 쌍 testdata 로 동일성 증명) |
| ⑫ | **교차 언어 스모크 유지**(S-12) — `tests/crosslang_smoke_server.py`(2D fake) 옆에 **실 servicer 셋으로 뜨는 스모크 서버 진입**을 `app/server.py` 로 대체할 수 있게 `--smoke` 옵션 없이 같은 부팅 경로 사용; 스크립트가 fake 를 계속 쓰는지 실 서버로 바꾸는지는 D-5E-10 | 2D D-2D-3 · 완료 조건 「M2 consumer/provider contract 통과」 |

**만들지 않는 것**: `CalculateOptimalBid` 매핑·`promoted`(5E-2) · 임베딩 모델·해시 fallback · job 영속화 · fork · 자유 문자열 사유 · 인증·TLS·컨테이너 · 승격 실행 · `except Exception` · `dict[str, Any]` · `contracts` 편집 · GBM 서빙 경로.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5E-0** | 5E 를 5E-1(골격·job·embedding NOT_READY·운영 성질)/5E-2(`CalculateOptimalBid` 매핑, 2F 뒤)로 가른다 | D-5E-3 의 2F 의존 · 4B/4C/4D/5C 분할 선례 | **운영자 확인**(대안: 2F 병합까지 5E 전체 대기 — 2F 는 진행 중이나 종결 시점 미정) |
| **D-5E-1** | **조립 근 `ml_engine.app`(새 최상위 패키지, layers 계약 밖)** — serving(servicer)·training.jobs(상태 기계·runner)·inference(K7)·adapters(파일)·5C-1/5C-2 커널을 **여기서만** 잇는다. `serving` 은 여전히 training·adapters·inference-외 를 모른다(5A forbidden·purity 게이트 무편집 통과). `TrainingJobServicer` 는 `training/jobs/servicer.py`(training 층 — grpc import 는 ignore_imports 로 허용) — `serving` 패키지에 두면 forbidden(training) 위반 | layers·forbidden 실측(착수 조사) · ADR 0010 D-8 「같은 gRPC 서버」 | **운영자 확인**(추천 위 · 대안 (b): job service 를 별 프로세스·별 포트 — D-8 문면 위반, Kotlin 4D 배선 둘) |
| **D-5E-2** | **임베딩은 정직한 미가용** — `EmbedText` → `ApplicationFailure(MODEL_NOT_READY, retryable=false, EMBEDDING_MODEL_ABSENT)`, metadata `NOT_READY`·dimension 0. 해시 fallback 은 「조용한 fallback」이라 이식하지 않는다. 모델 선택·이식은 `OPEN-5E-EMBEDDING-MODEL`(후속 5F 또는 6C 결정 — sentence-transformers 384 는 serving 의존·이미지 크기 축이 다르다) | 2E scope 「모델 선택은 M5」 · §5 fallback 금지 · 4D-2 client 는 `MODEL_NOT_READY` 를 `Unavailable` 로 | **운영자 확인**(대안: legacy sentence-transformers 이식을 5E-1 에 — 의존 추가·모델 파일 배포 축 신설) |
| **D-5E-3** | **`CalculateOptimalBid` 는 5E-1 에서 UNIMPLEMENTED**(생성 servicer 기본) — 분포 엔진 결과는 2F 없이는 v1 wire 로 정직하게 표현 불가(`dataset_id=""` 거부·`interval_source` 값 없음·Diagnostics 넷 없음). 가짜 `ApplicationFailure` 로 감싸지 않는다(계약 어휘 오용). 5E-2 가 2F 병합 뒤 매핑 + `promoted` + `READY` | 착수 조사 · 2F ②③④ · `OPEN-5D2-RELEASE-FOR-DISTRIBUTION`·`OPEN-5D2-INTERVAL-SOURCE-WIRE` | **운영자 확인**(대안 (b): 2F 브랜치 위에 5E 를 세움 — 검증 전 브랜치 병합 금지 규율 위반 · (c) GBM 경로를 임시 서빙 — 5D-2 (b) 결정과 충돌) |
| **D-5E-4** | dataset 디렉터리에 **`settlements.jsonl`** 세 번째 파일 + `DatasetManifestV1.settlements_checksum` **필수**(5C-1 파일 둘 편집, hunk 격리). 없으면 `load_dataset` 거부 → `FAILED(DATASET_UNREADABLE, SETTLEMENTS_ABSENT)`. 대안 (b) 성숙도를 wire 로 받기(계약 편집·2F 영역) (c) 라벨로 대체(정산≠라벨, 거짓) | 5C-2 D-5C2-2 · 2C ① dataset 은 immutable 참조(내용은 5C 소유) | **운영자 확인**(추천 위) |
| **D-5E-5** | artifact·report 는 **파일 storage 에 쓴다**(`adapters/artifact_files.py`, `file://<out_dir>/<job_id>/{artifact.json, report.json}`), 덮어쓰기 금지(디렉터리 비어 있음 검증). `ArtifactReference.uri`·`EvaluationReportReference.uri` = 그 경로, checksum = bytes sha256. object storage 는 6C | 2C ⑦⑧ · ADR 0010 D-8 | 계약 고정 |
| **D-5E-6** | 정책 값(`serving-v1.yaml`) — 아래 policy-values.md 표. 전부 **보수적 초기값 + 측정 의무**(legacy 근거 없음 — legacy 는 다른 런타임) | ADR 0010 D-1 · ADR 0006 D-7 | **값 승인 대기** |
| **D-5E-7** | 환경 값은 env 로만(포트·정책 파일 경로 넷·artifact 출력 디렉터리·bind host) — 설정 객체 하나(`ServerConfig.from_env`), 기본값은 **없다**(빈 env = 부팅 거부, 조용한 기본값 없음). 5A 환경 6 중 predictor 선택 셋은 5D-2 (b) 로 소멸, 경로 둘은 5E-2/GBM 밖 — policy-values.md 에 처분 표 | 5A 표 「환경 6 은 serving/training 진입점의 설정 객체(5E)」 | 계약 고정 |
| **D-5E-8** | wheel 빌드 훅 — 착수 조사 실측: `contracts/__init__.py` 는 `parents[3]/.contracts-generated` 를 `sys.path` 에 얹고 **절대 import `from bidvector.ml.v1 import …`** 를 한다(설치본에선 그 경로가 없어 재수출 불성립 — `OPEN-5A-WHEEL-BUILD-HOOK` 의 실체). 처방은 5A 파일 편집이 아니라 **wheel 에 `bidvector/` 를 별도 top-level 패키지로 싣는 것**: setuptools 빌드 단계(`tools/build_hook.py`, `build_py` 서브클래스)에서 `generate_contracts.generate(<build_lib>)` 로 `<build_lib>/bidvector/ml/v1/*_pb2*.py` 를 생성 → 설치본에선 `sys.path` 조작 없이 절대 import 가 그대로 성립, 소스 트리에선 기존 `.contracts-generated/` 경로 유지. `ml_engine.contracts._generated` 류 패키지 안 경로는 만들지 않는다(5A `test_generated_not_tracked` 구조 유지). `contracts/__init__.py` **무편집**. S-11 이 임시 venv 설치본에서 `import ml_engine.contracts`·`import bidvector.ml.v1.prediction_pb2_grpc` 성립을 실측 | `OPEN-5A-WHEEL-BUILD-HOOK` · 5A D-5A-0 (b) 구현 노트 | 계약 고정 |
| **D-5E-9** | mypy strict 를 위해 dev extras 에 `grpc-stubs`(또는 `types-grpcio`) 추가 — `[[tool.mypy.overrides]]` 로 `grpc` 를 `ignore_missing_imports` 하지 않는다(5A 규율: override 는 사유·해소 필수, 여기선 stub 이 있으므로 예외 불필요). `uv.lock` 갱신 in_scope | 5A D-5A-2 | 계약 고정 |
| **D-5E-10** | 2D 스모크 스크립트(`tools/contract-crosslang-smoke.sh`)는 5E-1 에서 **무편집**(fake 서버 유지 — Kotlin 쪽 test 가 fake 의 고정 응답에 의존). 실 서버 socket 스모크는 5E-1 이 별도 S-12b 로 로컬 1회(세 서비스 등록·`GetModelMetadata` 응답·`StartTraining` ACCEPTED) | D-2D-3 (a) | 계약 고정 |
| **D-5E-11** | 5C-1/5C-2 거부 사유 → `JobFailureCode` 매핑 표를 `training/jobs/pipeline.py` 가 **코드로** 소유(5C-1 D-5C-11·5C-2 D-5C2-12 의 checklist 표를 실물로) — 기존 6값에 없는 사유는 `TRAINING_ERROR`/`EVALUATION_ERROR` + `detail_code`(닫힌 StrEnum). proto 무편집, 값 추가 후보는 `OPEN-2C-FAILURE-CODES` 유지 | 2C D-2C-4 | 계약 고정 |

---

## 위협 모델 — 5E-1 고유 경계

**방어한다**: (a) 정책 없이 뜨기 — preload 실패 = `NOT_READY(사유)`, 기본값 없음 (b) 준비 안 된 판정 — `MODEL_NOT_READY`/UNIMPLEMENTED, 가짜 Success 0 (c) 무한 동시성 — `maximum_concurrent_rpcs` + `RESOURCE_EXHAUSTED` (d) 취소된 요청에 계산 — `is_active()`·cancel_token (e) 종료 중 요청 수락 — readiness NOT_READY 선행 + `stop(grace)` (f) job 전이표 밖 전이·조합 불변식 위반 — 타입(`JobRecord` 생성자)·결과 타입 (g) 멱등 위반 — 키 대조 (h) serving 이 DB/HTTP/training 을 끌어옴 — 5A forbidden·purity 게이트 무편집 (i) fork — runner 는 스레드 (j) 자유 문자열 사유 — 닫힌 enum (k) 임베딩 해시 fallback — 부재 (l) artifact 덮어쓰기 — 비어 있음 검증 (m) 예외 삼킴 — `BLE`, 구체 예외만.
**방어하지 않는다**: 인증·권한(6A) · TLS·네트워크 정책(6C) · job 영속·재시작 복구(6B) · 학습 결과의 옳음(5C) · 임베딩 실물 · 창 단위 취소 정밀도(5C-2 내부, `OPEN-5E-CANCEL-GRANULARITY`) · 다중 인스턴스 job 중복 · 성숙도 관측의 옳음(dataset 생성 측).

**우회 후보(≥5)**: (1) 정책 YAML 을 지우고 뜨기 → `NOT_READY`, `StartTraining` 도 `MODEL_NOT_READY`(정책 없이 job 못 돎) (2) `max_concurrent_rpcs+1` 동시 → 마지막 `RESOURCE_EXHAUSTED`(test 실측) (3) 취소 뒤에도 학습 계속 → cancel_token 이 단계 경계에서 확인, 카운터 test(D-2D-7) (4) 같은 `idempotency_key` 다른 dataset → `IDEMPOTENCY_CONFLICT` (5) `SUCCEEDED` 인데 evaluation 없음 → `JobRecord` 생성 불가 (6) `CANCELLED` 에 artifact → 생성 불가 (7) `settlements.jsonl` 없이 → `DATASET_UNREADABLE(SETTLEMENTS_ABSENT)`, evaluation 을 건너뛴 `SUCCEEDED` 없음 (8) `file://` 외 scheme → `INVALID_REQUEST`(시작 전) (9) 출력 디렉터리에 기존 파일 → `EVALUATION_ERROR`? 아니다 — 쓰기 전 검증 실패는 `TRAINING_ERROR(OUTPUT_DIR_NOT_EMPTY)` 로 **job 실패**(부분 산출물 없음) (10) `serving` 에 `ml_engine.training` 지연 import → purity 게이트 (11) `app` 에 DB import → `app` 은 forbidden 대상이 아니다 — **`app` 도 sqlalchemy·psycopg·requests·httpx·celery forbidden 에 넣는다**(pyproject (a) 에 포함) (12) SIGTERM 중 새 `StartTraining` → `NOT_READY` 뒤라 `MODEL_NOT_READY`.

---

## (2b) 값 획득 축 (Python)

| 표면 | 판정 |
| --- | --- |
| `ServingPolicy`·`load_serving_policy` | 연다 — 로더만 생성(컨벤션) |
| `ReadinessGate`·`Readiness` 상태 | 연다 — 전이 함수만(`mark_ready`·`mark_not_ready(reason)`·`begin_shutdown`), 상태 직접 대입 없음(frozen 스냅샷 반환) |
| `BidPredictionServicer`·`EmbeddingServicer`·`TrainingJobServicer` | 연다 — 생성자 인자는 readiness gate·store·runner·policy(주입 자리 = 조립 근 전용; 고정 항목 「무엇을 주입하는가」: fake store/runner 로 test — 결과가 쓴 값을 나르는가? servicer 응답은 store 스냅샷을 그대로 나르므로 **store 위조 = 응답 위조**, 경계 밖(조립 근 신뢰), 등재) |
| `JobRecord`·`transition(record, event) -> JobRecord \| TransitionRejected` | 연다 — 유일 전이 진입점, 생성자 불변식 |
| `InMemoryJobStore` | 연다 — `start_or_reuse(key, dataset) -> Started \| Reused \| Conflict` |
| `JobRunner`·`TrainingPipeline` Protocol | 연다 — 파이프라인 주입(조립 근) |
| `build_training_pipeline`·`ServerConfig.from_env`·`main` | 연다 — 조립 근, 밖에서 부를 이유 없음 |
| `write_artifact_files` | 연다 — `file://` 만, 비어 있는 디렉터리만 |
| 「경계로 처리」: store 위조·조립 근 | 실측 목록에 — servicer 가 store 값을 검증 없이 나르는지(그래야 함, 검증은 `JobRecord` 생성자) |

---

## 하네스 레인 변경(리뷰 요청 시점마다 갱신 — `git log --oneline d78e162..HEAD -- CLAUDE.md .claude/`)

없음(2026-09-16 verifier r1 요청 시점). 등재되는 커밋은 slice 산출물이 아니며 in_scope 밖, 운영자 승인 하에 같은 range 에 있다.

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-5E-POLICY-VALUES` | D-5E-6 표 — 착수 시 승인(보수적 초기값, 측정 의무) |
| `OPEN-5E-EMBEDDING-MODEL` | D-5E-2 — 모델 선택·이식 slice(5F)/이미지(6C) 결정 |
| `OPEN-5E-JOB-PERSISTENCE` | job 영속·재시작 복구 — 6B |
| `OPEN-5E-CANCEL-GRANULARITY` | 5C-2 창 루프 안 취소 확인 — 5C-2 파일 편집이라 후속 |
| `OPEN-5A-SERVING-GRPC-EXCEPTION` | pyproject (a) 로 **종결** |
| `OPEN-5A-WHEEL-BUILD-HOOK` | ⑨ + S-11 로 **종결**(조건부 D-5E-8) |
| `OPEN-5D2-RELEASE-FOR-DISTRIBUTION`·`OPEN-5D2-INTERVAL-SOURCE-WIRE`·`OPEN-5D-DIAGNOSTICS-WIRE` | 2F(wire) + 5E-2(매핑) — 5E-1 무변경 |
| `OPEN-2C-DATASET-URI-SCHEME` | `file` 만(정책 값) — 6C |
| `OPEN-2C-FAILURE-CODES` | D-5E-11 매핑 표에서 후보 열거(값 추가는 2F 류 additive) |
| `OPEN-5C2-SERVING-PATH-PARITY`·`OPEN-5C2-UNLEARNED-GUARD` | 5E-2(매핑 뒤 통합 test) |
| `OPEN-5C-REJECT-ACCOUNTING` | `TrainingRejected` 의 detail 을 `detail_code` 로 나름 — 사유별 분해는 여전히 없음, 유지 |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 착수 | 초판 — 5E 분할(D-5E-0), 결정 11 | 사용자 「5E 착수」 · 착수 조사 |
| 2026-09-16 (2) verifier r1 요청 전 | in_scope 에 `ml-engine/setup.py`·`config/quality/leak-pattern-baseline.txt` 추가 · 「하네스 레인 변경」 절 신설(없음) | 구현 실측 — setuptools 훅 배선에 `setup.py` 필요(pyproject 만으로 cmdclass 불가), `CancelToken` 식별자 오탐 baseline 등재. 절 누락은 evidence-pack 규격 위반(팀장 레인 초판 누락) |
| 2026-09-16 (3) verifier r1 뒤 | in_scope 에 `ml-engine/uv.lock`·`ml-engine/src/ml_engine/adapters/__init__.py` 추가 | verifier r1 L-1 — 둘 다 변경됐는데 목록 밖(전자는 D-5E-9 산문에만). 수정 라운드 1 과 병행, 산출물 무접촉 |
