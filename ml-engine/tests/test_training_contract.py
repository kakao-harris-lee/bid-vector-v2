"""M2/2C — `TrainingJobService` provider 계약 test.

Python 쪽 fake servicer(생성 `TrainingJobServiceServicer`의 최소 구현, **실제 상태
기계**를 갖는다 — job 저장소 dict, idempotency 키 대조)가 계약 규칙(전이표·idempotency·
조합 불변식·fail-closed)을 지키는지 확인한다. servicer 메서드를 **직접 호출**한다
(scope.md 「구현 순서」 5의 두 선택지 중 하나, 2B `test_prediction_contract.py`와 같은
관례) — 실제 socket 배선·폴링 주기·취소 존중은 M4·5C 몫이다(ADR 0010 §6, scope.md
「만들지 않는 것」).

`ml-engine/tests/conftest.py`(2A)는 `common.proto`·`error.proto`만 생성한다. 이 slice의
`scope.md` in_scope는 이 파일 하나만 지정하고 `conftest.py`(`tests/**`가 아니다)는 범위
밖이다 — 2B와 같은 관례로 독립 module-scope fixture를 두어 `features.proto`·
`prediction.proto`(`ArtifactReference.release`가 `ModelRelease`를 재사용한다)·
`training.proto`(+ `grpc_python_out`)까지 생성한다. `common_pb2`·`error_pb2` 생성 로직
중복은 2B `checklist.md` 알려진 제한 4와 같은 갈래(5A 인계).
"""

from __future__ import annotations

import re
import sys
import tempfile
from collections.abc import Iterator
from pathlib import Path

import grpc_tools
import pytest
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
# `training.proto`가 well-known type `google/protobuf/timestamp.proto`를 쓴다(D-2C-5) —
# BSR 의존은 없지만(2B `google.type.Date` 회피와 같은 축) grpc_tools 는 buf 와 달리
# well-known type 을 자동으로 찾지 못한다. grpc_tools 배포에 번들된 `_proto`(protobuf 표준
# well-known type 사본)를 별도 `--proto_path`로 준다 — Kotlin `ml-contract`의 protobuf-java
# 런타임 jar 번들과 같은 축(로컬 완결, 네트워크 없음).
_GRPC_TOOLS_WELL_KNOWN_TYPES_ROOT = Path(grpc_tools.__file__).resolve().parent / "_proto"
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
_TESTDATA_ROOT = _REPO_ROOT / "contracts" / "testdata" / "training"
_PROTO_FILES = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
    "bidvector/ml/v1/features.proto",
    "bidvector/ml/v1/prediction.proto",
    "bidvector/ml/v1/training.proto",
)

_SHA256_HEX_PATTERN = re.compile(r"^[0-9a-f]{64}$")
_KNOWN_TRAINING_SPEC_VERSIONS = frozenset({"training-spec-2026.3"})


def _read(name: str) -> bytes:
    return (_TESTDATA_ROOT / name).read_bytes()


# ---- 생성 stub — 이 module 전용(conftest.py 를 건드리지 않는다) ----


@pytest.fixture(scope="module")
def generated_stub_path() -> Iterator[Path]:
    """`grpc_tools.protoc`로 `contracts/proto`를 임시 디렉터리에 생성한다(`--python_out` +
    `--grpc_python_out`). 생성물은 module 종료 시 삭제된다 — VCS 에도 리포지터리 안에도
    남지 않는다(D-M2-3 (a)와 같은 관례)."""
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-2c-") as tmp:
        out_dir = Path(tmp)
        args = [
            "grpc_tools.protoc",
            f"--proto_path={_PROTO_ROOT}",
            f"--proto_path={_GRPC_TOOLS_WELL_KNOWN_TYPES_ROOT}",
            f"--python_out={out_dir}",
            f"--grpc_python_out={out_dir}",
            *(str(_PROTO_ROOT / proto_file) for proto_file in _PROTO_FILES),
        ]
        exit_code = protoc.main(args)
        if exit_code != 0:
            raise RuntimeError(f"grpc_tools.protoc 생성 실패(exit={exit_code}) — args={args}")

        sys.path.insert(0, str(out_dir))
        try:
            yield out_dir
        finally:
            sys.path.remove(str(out_dir))


@pytest.fixture(scope="module")
def error_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import error_pb2 as module

    return module


@pytest.fixture(scope="module")
def training_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import training_pb2 as module

    return module


@pytest.fixture(scope="module")
def training_pb2_grpc(generated_stub_path: Path):
    from bidvector.ml.v1 import training_pb2_grpc as module

    return module


# ---- fake servicer — 실제 상태 기계(scope.md 「구현 순서」 4, Kotlin FakeTrainingJobServicer
# 와 대칭) ----


@pytest.fixture()
def fixture_servicer(training_pb2_grpc, training_pb2, error_pb2):
    class _FixtureServicer(training_pb2_grpc.TrainingJobServiceServicer):
        def __init__(self) -> None:
            self._job_id_by_idempotency_key: dict[str, str] = {}
            self._jobs_by_job_id: dict[str, object] = {}
            self._dataset_id_by_job_id: dict[str, str] = {}
            self._sequence = 0

        def job_count(self) -> int:
            return len(self._jobs_by_job_id)

        def advance_to_running(self, job_id: str) -> None:
            """test 전용 — RUNNING 전이를 흉내 내 cancel 이 두 비종료 상태 모두에서
            동작함을 검증한다(Kotlin `advanceToRunning`과 대칭)."""
            job = self._jobs_by_job_id[job_id]
            job.state = training_pb2.JOB_STATE_RUNNING
            job.started_at.GetCurrentTime()

        def StartTraining(self, request, context):  # noqa: N802 — grpc 생성 시그니처
            if request.training_spec_version not in _KNOWN_TRAINING_SPEC_VERSIONS:
                return self._start_failure(error_pb2.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC)
            existing_job_id = self._job_id_by_idempotency_key.get(request.idempotency_key)
            if existing_job_id is None:
                return self._accept_new_job(request)
            if self._dataset_id_by_job_id[existing_job_id] != request.dataset.dataset_id:
                return self._start_failure(error_pb2.FAILURE_CODE_IDEMPOTENCY_CONFLICT)
            existing_job = self._jobs_by_job_id[existing_job_id]
            response = training_pb2.StartTrainingResponse()
            response.handle.job_id = existing_job_id
            response.handle.state = existing_job.state
            return response

        def GetTrainingJob(self, request, context):  # noqa: N802
            job = self._jobs_by_job_id.get(request.job_id)
            if job is None:
                response = training_pb2.GetTrainingJobResponse()
                response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
                response.failure.retryable = False
                return response
            response = training_pb2.GetTrainingJobResponse()
            response.job.CopyFrom(job)
            return response

        def CancelTrainingJob(self, request, context):  # noqa: N802
            job = self._jobs_by_job_id.get(request.job_id)
            if job is None:
                response = training_pb2.CancelTrainingJobResponse()
                response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
                response.failure.retryable = False
                return response
            if job.state in (training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_RUNNING):
                job.state = training_pb2.JOB_STATE_CANCELLED
                job.finished_at.GetCurrentTime()
            response = training_pb2.CancelTrainingJobResponse()
            response.job.CopyFrom(job)
            return response

        def _accept_new_job(self, request):
            job_id = f"fake-job-{self._sequence}"
            self._sequence += 1
            job = training_pb2.TrainingJob()
            job.job_id = job_id
            job.state = training_pb2.JOB_STATE_ACCEPTED
            job.accepted_at.GetCurrentTime()
            self._jobs_by_job_id[job_id] = job
            self._dataset_id_by_job_id[job_id] = request.dataset.dataset_id
            self._job_id_by_idempotency_key[request.idempotency_key] = job_id
            response = training_pb2.StartTrainingResponse()
            response.handle.job_id = job_id
            response.handle.state = training_pb2.JOB_STATE_ACCEPTED
            return response

        def _start_failure(self, code):
            response = training_pb2.StartTrainingResponse()
            response.failure.code = code
            response.failure.retryable = False
            return response

    return _FixtureServicer()


# ---- fake servicer 상태 기계 — 직접 호출(scope.md ⑨, 위협 모델 (g)) ----


def test_start_training_returns_accepted_immediately(fixture_servicer, training_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    response = fixture_servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "handle"
    assert response.handle.state == training_pb2.JOB_STATE_ACCEPTED


def test_same_idempotency_key_twice_returns_same_job_id(fixture_servicer, training_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    first = fixture_servicer.StartTraining(request, context=None)
    second = fixture_servicer.StartTraining(request, context=None)
    assert first.handle.job_id == second.handle.job_id
    assert fixture_servicer.job_count() == 1


def test_same_idempotency_key_different_dataset_is_idempotency_conflict(fixture_servicer, training_pb2, error_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    fixture_servicer.StartTraining(request, context=None)

    different_dataset = training_pb2.StartTrainingRequest()
    different_dataset.CopyFrom(request)
    different_dataset.dataset.dataset_id = "dataset-other-2026-09"

    response = fixture_servicer.StartTraining(different_dataset, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_IDEMPOTENCY_CONFLICT
    assert fixture_servicer.job_count() == 1


def test_unknown_training_spec_version_rejects_start_training(fixture_servicer, training_pb2, error_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    request.training_spec_version = "unknown-spec-v9"

    response = fixture_servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC


def test_get_training_job_returns_started_job_as_accepted(fixture_servicer, training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    started = fixture_servicer.StartTraining(start_request, context=None)

    get_request = training_pb2.GetTrainingJobRequest()
    get_request.envelope.CopyFrom(start_request.envelope)
    get_request.job_id = started.handle.job_id

    response = fixture_servicer.GetTrainingJob(get_request, context=None)
    assert response.WhichOneof("result") == "job"
    assert response.job.state == training_pb2.JOB_STATE_ACCEPTED


def test_get_training_job_rejects_unknown_job_id(fixture_servicer, training_pb2, error_pb2):
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "req-2c-py-01"
    request.envelope.correlation_id = "corr-2c-py-01"
    request.job_id = "unknown-job-id"

    response = fixture_servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_JOB_NOT_FOUND


def test_cancel_training_job_transitions_accepted_to_cancelled(fixture_servicer, training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    started = fixture_servicer.StartTraining(start_request, context=None)

    cancel_request = training_pb2.CancelTrainingJobRequest()
    cancel_request.envelope.CopyFrom(start_request.envelope)
    cancel_request.job_id = started.handle.job_id

    response = fixture_servicer.CancelTrainingJob(cancel_request, context=None)
    assert response.WhichOneof("result") == "job"
    assert response.job.state == training_pb2.JOB_STATE_CANCELLED


def test_cancel_training_job_transitions_running_to_cancelled(fixture_servicer, training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    started = fixture_servicer.StartTraining(start_request, context=None)
    fixture_servicer.advance_to_running(started.handle.job_id)

    cancel_request = training_pb2.CancelTrainingJobRequest()
    cancel_request.envelope.CopyFrom(start_request.envelope)
    cancel_request.job_id = started.handle.job_id

    response = fixture_servicer.CancelTrainingJob(cancel_request, context=None)
    assert response.job.state == training_pb2.JOB_STATE_CANCELLED


def test_cancel_training_job_is_idempotent_no_op_in_terminal_state(fixture_servicer, training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    started = fixture_servicer.StartTraining(start_request, context=None)

    cancel_request = training_pb2.CancelTrainingJobRequest()
    cancel_request.envelope.CopyFrom(start_request.envelope)
    cancel_request.job_id = started.handle.job_id

    first = fixture_servicer.CancelTrainingJob(cancel_request, context=None)
    second = fixture_servicer.CancelTrainingJob(cancel_request, context=None)
    assert first.job.state == training_pb2.JOB_STATE_CANCELLED
    assert second.WhichOneof("result") == "job"
    assert second.job.state == training_pb2.JOB_STATE_CANCELLED


def test_cancel_training_job_rejects_unknown_job_id(fixture_servicer, training_pb2, error_pb2):
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = "req-2c-py-02"
    request.envelope.correlation_id = "corr-2c-py-02"
    request.job_id = "unknown-job-id"

    response = fixture_servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_JOB_NOT_FOUND


# ---- 전이표에 있는 쌍만 허용 — 표 밖 전이는 provider 가 거부(순수 함수, scope.md ④) ----


def test_allowed_transitions_are_accepted(training_pb2):
    assert _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_RUNNING)
    assert _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_SUCCEEDED)
    assert _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_FAILED)
    assert _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_CANCELLED)
    assert _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_CANCELLED)


def test_reverse_transition_running_to_accepted_is_rejected(training_pb2):
    assert not _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_ACCEPTED)


def test_transitions_leaving_terminal_states_are_rejected(training_pb2):
    assert not _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_SUCCEEDED, training_pb2.JOB_STATE_RUNNING)
    assert not _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_FAILED, training_pb2.JOB_STATE_CANCELLED)
    assert not _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_CANCELLED, training_pb2.JOB_STATE_RUNNING)


def test_accepted_to_succeeded_or_failed_direct_transition_is_rejected(training_pb2):
    assert not _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_SUCCEEDED)
    assert not _is_allowed_transition(training_pb2, training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_FAILED)


# ---- SUCCEEDED/FAILED 조합 불변식 — CANCELLED 에 artifact 첨부 거부(우회 후보 3) ----


def test_succeeded_testdata_has_artifact_and_evaluation(training_pb2):
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    assert _is_valid_job_combination(training_pb2, response.job)


def test_succeeded_without_artifact_violates_invariant(training_pb2):
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    response.job.ClearField("artifact")
    assert not _is_valid_job_combination(training_pb2, response.job)


def test_succeeded_without_evaluation_violates_invariant(training_pb2):
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    response.job.ClearField("evaluation")
    assert not _is_valid_job_combination(training_pb2, response.job)


def test_failed_testdata_has_failure(training_pb2):
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_failed.binpb"))
    assert _is_valid_job_combination(training_pb2, response.job)


def test_failed_without_failure_violates_invariant(training_pb2):
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_failed.binpb"))
    response.job.ClearField("failure")
    assert not _is_valid_job_combination(training_pb2, response.job)


def test_cancelled_with_artifact_attached_violates_invariant(training_pb2):
    cancelled = training_pb2.GetTrainingJobResponse()
    cancelled.ParseFromString(_read("get_training_job_response_cancelled.binpb"))
    succeeded = training_pb2.GetTrainingJobResponse()
    succeeded.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    cancelled.job.artifact.CopyFrom(succeeded.job.artifact)
    assert not _is_valid_job_combination(training_pb2, cancelled.job)


def test_cancelled_and_running_testdata_have_no_artifact_evaluation_failure(training_pb2):
    cancelled = training_pb2.GetTrainingJobResponse()
    cancelled.ParseFromString(_read("get_training_job_response_cancelled.binpb"))
    running = training_pb2.GetTrainingJobResponse()
    running.ParseFromString(_read("get_training_job_response_running.binpb"))
    assert _is_valid_job_combination(training_pb2, cancelled.job)
    assert _is_valid_job_combination(training_pb2, running.job)


# ---- timestamp 순서(D-2C-5) — accepted_at ≤ started_at ≤ finished_at ----


def test_testdata_timestamp_order_is_valid(training_pb2):
    for name in (
        "get_training_job_response_succeeded.binpb",
        "get_training_job_response_failed.binpb",
        "get_training_job_response_cancelled.binpb",
        "get_training_job_response_running.binpb",
    ):
        response = training_pb2.GetTrainingJobResponse()
        response.ParseFromString(_read(name))
        assert _is_valid_timestamp_order(response.job)


def test_finished_at_before_accepted_at_violates_invariant(training_pb2):
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    response.job.finished_at.seconds = response.job.accepted_at.seconds - 3600
    response.job.finished_at.nanos = response.job.accepted_at.nanos
    assert not _is_valid_timestamp_order(response.job)


# ---- dataset_id 일치(위협 모델 (f)) ----


def test_artifact_release_dataset_id_matches_request_dataset_id(training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    assert _artifact_matches_requested_dataset(response.job.artifact, start_request.dataset.dataset_id)


def test_artifact_release_dataset_id_mismatch_violates_invariant(training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    response.job.artifact.release.dataset_id = "other-dataset-id"
    assert not _artifact_matches_requested_dataset(response.job.artifact, start_request.dataset.dataset_id)


# ---- UNSPECIFIED·정의 밖 정수 거부(fail-closed, proto3 open enum) ----


def test_job_state_unspecified_is_rejected(training_pb2):
    assert not _is_acceptable_job_state(training_pb2, training_pb2.JOB_STATE_UNSPECIFIED)


def test_job_state_undefined_integer_is_rejected(training_pb2):
    handle = training_pb2.TrainingJobHandle()
    handle.state = 99  # proto3 open enum — 파싱은 통과하지만 정의 밖 값이다.
    assert not _is_acceptable_job_state(training_pb2, handle.state)


def test_job_failure_code_unspecified_is_rejected(training_pb2):
    assert not _is_acceptable_job_failure_code(training_pb2, training_pb2.JOB_FAILURE_CODE_UNSPECIFIED)


def test_job_failure_code_undefined_integer_is_rejected(training_pb2):
    failure = training_pb2.JobFailure()
    failure.code = 88
    assert not _is_acceptable_job_failure_code(training_pb2, failure.code)


# ---- checksum 정규형(sha256 hex, 소문자 64자) ----


def test_testdata_checksums_are_normalized_sha256_hex(training_pb2):
    start_request = training_pb2.StartTrainingRequest()
    start_request.ParseFromString(_read("start_training_request.binpb"))
    assert _is_valid_sha256_hex(start_request.dataset.manifest_checksum)

    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(_read("get_training_job_response_succeeded.binpb"))
    assert _is_valid_sha256_hex(response.job.artifact.release.artifact_checksum)
    assert _is_valid_sha256_hex(response.job.evaluation.checksum)


def test_checksum_wrong_length_or_not_hex_is_rejected():
    assert not _is_valid_sha256_hex("deadbeef")
    assert not _is_valid_sha256_hex("g" * 64)
    assert not _is_valid_sha256_hex("A" * 64)  # 대문자 hex 도 정규형 밖(소문자 고정)


# ---- StartTrainingRequest 필수값(idempotency_key 비어있음 거부, 우회 후보 1) ----


def test_testdata_start_training_request_is_valid(training_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    assert _is_valid_start_training_request(request)


def test_empty_idempotency_key_is_rejected(training_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    request.idempotency_key = ""
    assert not _is_valid_start_training_request(request)


def test_empty_training_spec_version_is_rejected(training_pb2):
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(_read("start_training_request.binpb"))
    request.training_spec_version = ""
    assert not _is_valid_start_training_request(request)


# ---- round-trip(testdata 바이트 ↔ 생성 타입 — Kotlin 쪽과 대칭) ----


def test_start_training_request_round_trips(training_pb2):
    original = _read("start_training_request.binpb")
    request = training_pb2.StartTrainingRequest()
    request.ParseFromString(original)
    assert request.SerializeToString(deterministic=True) == original


def test_start_training_response_accepted_round_trips(training_pb2):
    original = _read("start_training_response_accepted.binpb")
    response = training_pb2.StartTrainingResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_training_job_response_running_round_trips(training_pb2):
    original = _read("get_training_job_response_running.binpb")
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_training_job_response_succeeded_round_trips(training_pb2):
    original = _read("get_training_job_response_succeeded.binpb")
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_training_job_response_failed_round_trips(training_pb2):
    original = _read("get_training_job_response_failed.binpb")
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_training_job_response_cancelled_round_trips(training_pb2):
    original = _read("get_training_job_response_cancelled.binpb")
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_start_training_response_idempotency_conflict_round_trips(training_pb2):
    original = _read("start_training_response_failure_idempotency_conflict.binpb")
    response = training_pb2.StartTrainingResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_training_job_response_job_not_found_round_trips(training_pb2):
    original = _read("get_training_job_response_failure_job_not_found.binpb")
    response = training_pb2.GetTrainingJobResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


# ---- 계약이 요구하는 거부 규칙 — 순수 함수(Kotlin `TrainingContractTest`와 대칭). 실제
# Python validation 구현은 5C 몫이고, 여기서는 test 가 그 규칙을 문서화·고정한다
# (scope.md 「구현 순서」 4). ----


def _allowed_transitions(training_pb2) -> set[tuple[int, int]]:
    return {
        (training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_RUNNING),
        (training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_SUCCEEDED),
        (training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_FAILED),
        (training_pb2.JOB_STATE_ACCEPTED, training_pb2.JOB_STATE_CANCELLED),
        (training_pb2.JOB_STATE_RUNNING, training_pb2.JOB_STATE_CANCELLED),
    }


def _is_allowed_transition(training_pb2, from_state: int, to_state: int) -> bool:
    return (from_state, to_state) in _allowed_transitions(training_pb2)


def _is_valid_job_combination(training_pb2, job) -> bool:
    if job.state == training_pb2.JOB_STATE_SUCCEEDED:
        return job.HasField("artifact") and job.HasField("evaluation") and not job.HasField("failure")
    if job.state == training_pb2.JOB_STATE_FAILED:
        return job.HasField("failure") and not job.HasField("artifact") and not job.HasField("evaluation")
    if job.state in (
        training_pb2.JOB_STATE_ACCEPTED,
        training_pb2.JOB_STATE_RUNNING,
        training_pb2.JOB_STATE_CANCELLED,
    ):
        return not job.HasField("artifact") and not job.HasField("evaluation") and not job.HasField("failure")
    return False  # UNSPECIFIED·정의 밖 정수


def _compare_timestamps(left, right) -> int:
    left_key = (left.seconds, left.nanos)
    right_key = (right.seconds, right.nanos)
    if left_key < right_key:
        return -1
    if left_key > right_key:
        return 1
    return 0


def _is_valid_timestamp_order(job) -> bool:
    accepted_before_started = (
        not job.HasField("started_at") or _compare_timestamps(job.accepted_at, job.started_at) <= 0
    )
    accepted_before_finished = (
        not job.HasField("finished_at") or _compare_timestamps(job.accepted_at, job.finished_at) <= 0
    )
    started_before_finished = (
        not job.HasField("finished_at")
        or not job.HasField("started_at")
        or _compare_timestamps(job.started_at, job.finished_at) <= 0
    )
    return accepted_before_started and accepted_before_finished and started_before_finished


def _artifact_matches_requested_dataset(artifact, requested_dataset_id: str) -> bool:
    return artifact.release.dataset_id == requested_dataset_id


def _is_acceptable_job_state(training_pb2, value: int) -> bool:
    known_values = {number for _, number in training_pb2.JobState.items()}
    return value != training_pb2.JOB_STATE_UNSPECIFIED and value in known_values


def _is_acceptable_job_failure_code(training_pb2, value: int) -> bool:
    known_values = {number for _, number in training_pb2.JobFailureCode.items()}
    return value != training_pb2.JOB_FAILURE_CODE_UNSPECIFIED and value in known_values


def _is_valid_sha256_hex(value: str) -> bool:
    return bool(_SHA256_HEX_PATTERN.match(value))


def _is_valid_start_training_request(request) -> bool:
    return (
        bool(request.idempotency_key.strip())
        and bool(request.training_spec_version.strip())
        and bool(request.dataset.uri.strip())
        and bool(request.dataset.dataset_id.strip())
        and _is_valid_sha256_hex(request.dataset.manifest_checksum)
    )
