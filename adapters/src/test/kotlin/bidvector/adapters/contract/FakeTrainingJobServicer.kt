package bidvector.adapters.contract

import com.google.protobuf.Timestamp
import contract.bidvector.ml.v1.ApplicationFailure
import contract.bidvector.ml.v1.CancelTrainingJobRequest
import contract.bidvector.ml.v1.CancelTrainingJobResponse
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.GetTrainingJobRequest
import contract.bidvector.ml.v1.GetTrainingJobResponse
import contract.bidvector.ml.v1.JobState
import contract.bidvector.ml.v1.StartTrainingRequest
import contract.bidvector.ml.v1.StartTrainingResponse
import contract.bidvector.ml.v1.TrainingJob
import contract.bidvector.ml.v1.TrainingJobHandle
import contract.bidvector.ml.v1.TrainingJobServiceGrpcKt
import java.time.Instant

/**
 * M2/2C — in-process fake servicer. **실제 상태 기계**를 갖는다(scope.md 「구현 순서」 3,
 * `TrainingContractTest.kt`에서 분리 — v2-지침서.md §5 500줄 한도). job 저장소
 * (`recordsByJobId`)와 idempotency 대조(`jobIdByIdempotencyKey`)로 같은 키 두 번은 같은
 * `job_id`를, 다른 dataset 이면 `IDEMPOTENCY_CONFLICT`를 낸다(ADR 0010 D-4). 취소는
 * `ACCEPTED`·`RUNNING`에서만 `CANCELLED`로 전이하고 종료 상태에서는 멱등 no-op이다
 * (scope.md ⑤). 실제 학습 실행·취소 존중은 흉내 내지 않는다 — 이 fake 는 계약의 상태
 * 어휘만 지킨다.
 */
internal class FakeTrainingJobServicer : TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineImplBase() {
    // D-2C-2 (a) — ml-engine 이 아는 versioned training spec 집합(5C 소유, 이 fake 에서는
    // testdata 가 쓰는 하나만 안다).
    private val knownTrainingSpecVersions = setOf("training-spec-2026.3")
    private val jobIdByIdempotencyKey = mutableMapOf<String, String>()
    private val recordsByJobId = mutableMapOf<String, TrainingJob>()
    private val datasetIdByJobId = mutableMapOf<String, String>()
    private var sequence = 0

    fun jobCount(): Int = recordsByJobId.size

    /** test 전용 — RUNNING 전이를 흉내 내 cancel 이 두 비종료 상태 모두에서 동작함을 검증한다. */
    fun advanceToRunning(jobId: String) {
        val current = recordsByJobId.getValue(jobId)
        val running =
            current
                .toBuilder()
                .setState(JobState.JOB_STATE_RUNNING)
                .setStartedAt(nowTimestamp())
                .build()
        recordsByJobId[jobId] = running
    }

    override suspend fun startTraining(request: StartTrainingRequest): StartTrainingResponse {
        if (request.trainingSpecVersion !in knownTrainingSpecVersions) {
            return failureResponse(FailureCode.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC)
        }
        val existingJobId = jobIdByIdempotencyKey[request.idempotencyKey]
        return if (existingJobId == null) {
            acceptNewJob(request)
        } else if (datasetIdByJobId.getValue(existingJobId) != request.dataset.datasetId) {
            failureResponse(FailureCode.FAILURE_CODE_IDEMPOTENCY_CONFLICT)
        } else {
            val existingJob = recordsByJobId.getValue(existingJobId)
            StartTrainingResponse
                .newBuilder()
                .setHandle(TrainingJobHandle.newBuilder().setJobId(existingJobId).setState(existingJob.state))
                .build()
        }
    }

    override suspend fun getTrainingJob(request: GetTrainingJobRequest): GetTrainingJobResponse {
        val job =
            recordsByJobId[request.jobId]
                ?: return GetTrainingJobResponse
                    .newBuilder()
                    .setFailure(applicationFailure(FailureCode.FAILURE_CODE_JOB_NOT_FOUND))
                    .build()
        return GetTrainingJobResponse.newBuilder().setJob(job).build()
    }

    override suspend fun cancelTrainingJob(request: CancelTrainingJobRequest): CancelTrainingJobResponse {
        val current =
            recordsByJobId[request.jobId]
                ?: return CancelTrainingJobResponse
                    .newBuilder()
                    .setFailure(applicationFailure(FailureCode.FAILURE_CODE_JOB_NOT_FOUND))
                    .build()
        val next =
            if (current.state == JobState.JOB_STATE_ACCEPTED || current.state == JobState.JOB_STATE_RUNNING) {
                current
                    .toBuilder()
                    .setState(JobState.JOB_STATE_CANCELLED)
                    .setFinishedAt(nowTimestamp())
                    .build()
            } else {
                current // 종료 상태 — 멱등 no-op(scope.md ⑤), 새 전이가 아니다.
            }
        recordsByJobId[request.jobId] = next
        return CancelTrainingJobResponse.newBuilder().setJob(next).build()
    }

    private fun acceptNewJob(request: StartTrainingRequest): StartTrainingResponse {
        val jobId = "fake-job-${sequence++}"
        val job =
            TrainingJob
                .newBuilder()
                .setJobId(jobId)
                .setState(JobState.JOB_STATE_ACCEPTED)
                .setAcceptedAt(nowTimestamp())
                .build()
        recordsByJobId[jobId] = job
        datasetIdByJobId[jobId] = request.dataset.datasetId
        jobIdByIdempotencyKey[request.idempotencyKey] = jobId
        return StartTrainingResponse
            .newBuilder()
            .setHandle(TrainingJobHandle.newBuilder().setJobId(jobId).setState(JobState.JOB_STATE_ACCEPTED))
            .build()
    }

    private fun failureResponse(code: FailureCode): StartTrainingResponse =
        StartTrainingResponse.newBuilder().setFailure(applicationFailure(code)).build()

    private fun applicationFailure(code: FailureCode): ApplicationFailure =
        ApplicationFailure
            .newBuilder()
            .setCode(code)
            .setRetryable(false)
            .build()

    private fun nowTimestamp(): Timestamp {
        val instant = Instant.now()
        return Timestamp
            .newBuilder()
            .setSeconds(instant.epochSecond)
            .setNanos(instant.nano)
            .build()
    }
}
