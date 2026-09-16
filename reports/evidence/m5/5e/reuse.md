# M5/5E-1 — reuse.md

**이식 없음.** legacy bid-vector 저장소(FastAPI+Celery)에는 gRPC 서버가 없다 —
`ml_engine.serving.*`·`ml_engine.training.jobs.*`·`ml_engine.app.*`는 전부 신규 작성이다
(설계 검토 (4)).

재사용한 것은 legacy 가 아니라 **이 저장소 자신의 기존 산출물**이다(포인터 표 대상 아님 —
`tools/reuse_provenance_check.py`는 `Reuse: <path>@<commit>` docstring 첫 줄이 있는 파일만
대조하고, 5E-1 신규 파일 중 그 형태를 쓴 파일은 0개다. 아래는 기록용 설명이지 표 항목이
아니다):

- `tools/generate_contracts.py::generate`(5A) — `tools/build_hook.py`가 그대로 호출한다.
- `registry/policy.py::load_policy`(5A) — `serving/policy.py::load_serving_policy`가 그
  위에 값 불변식을 얹는다(5C-1/5D 정책 로더와 같은 패턴).
- `tests/crosslang_smoke_server.py`(2D)의 서버 부팅 관례(`grpc.server(ThreadPoolExecutor)`)
  — `serving/grpc.py::build_server`가 같은 형태를 쓴다(값은 정책에서, 하드코딩 아님).
- `tests/test_training_contract.py`(2C)의 fake servicer 관례 — `training/jobs/servicer.py`
  실물 구현이 같은 검증 순서·전이표 대조 규약을 따른다.
- 5C-1·5C-2·5D 공개 표면(`load_dataset`·`train_award_rate_gbm`·`write_artifact`·
  `run_holdout`·`build_weekly_maturity`) — `ml_engine.app.pipeline`이 그대로 호출한다(수정
  없음, 다만 D-5E-4 로 `training/dataset.py`·`adapters/dataset_files.py` 두 파일에 hunk
  격리 확장을 더했다 — 기존 test 는 무변경 통과).

## 재작성 판정 근거(설계 검토 (4))

새로 작성한 이유는 재사용 대상이 legacy 에 아예 없기 때문이다(a 형 — 대체할 원본 부재).
b 형(기존 코드 결함)에 해당하는 재작성은 5E-1 에 없다.
