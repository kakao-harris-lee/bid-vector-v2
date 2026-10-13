package bidvector.adapters.contract

import com.google.protobuf.Timestamp
import contract.bidvector.ml.v1.ArtifactReference
import contract.bidvector.ml.v1.JobFailureCode
import contract.bidvector.ml.v1.JobState
import contract.bidvector.ml.v1.StartTrainingRequest
import contract.bidvector.ml.v1.TrainingJob

/**
 * `TrainingJobService` 계약이 요구하는 거부 규칙(전이표·조합 불변식·timestamp
 * 순서·dataset_id 일치·fail-closed enum·checksum 정규형)을 순수 함수로 문서화·고정한다
 * (`TrainingContractTest`·`test_training_contract.py` 양쪽에서 대칭으로 검증,
 * `ContractFractionRules.kt`와 같은 관례 — `TrainingContractTest.kt`의 500줄 한도를
 * 넘기지 않도록 분리했다, v2-지침서.md §5). 실제 Kotlin validation 구현은 아직 없다.
 */
/**
 * 전이표 **데이터**. `internal` 인 이유는 `TrainingContractTest` 가 5×5 전 쌍 진리표를 이
 * 데이터와 대조하기 때문이다(M6/6B-4 vr M-2 P1b — `isAllowedTransition` 에 절을 하나
 * 더해(`|| from == JOB_STATE_FAILED`) 데이터와 어긋나게 만드는 변이는 **쌍을 열거한**
 * test 로는 잡히지 않았다. 전 쌍 대조는 자리마다 다른 답이 나오면 바로 붉어진다).
 * 데이터 자체가 Python `_TRANSITIONS` 와 같은지는 교차 등식 게이트가 센다.
 */
internal val allowedJobStateTransitions =
    setOf(
        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_RUNNING,
        JobState.JOB_STATE_RUNNING to JobState.JOB_STATE_SUCCEEDED,
        JobState.JOB_STATE_RUNNING to JobState.JOB_STATE_FAILED,
        // M6/6B-4 D-6B4-1 — 대기 큐 상한 초과로 가장 오래된 대기 job 을 폐기하는 간선
        // (`JOB_FAILURE_CODE_WORKER_RESOURCE_EXHAUSTED`, ADR 0010 D-8 개정).
        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_FAILED,
        JobState.JOB_STATE_ACCEPTED to JobState.JOB_STATE_CANCELLED,
        JobState.JOB_STATE_RUNNING to JobState.JOB_STATE_CANCELLED,
    )

/** `ADR 0010` D-8 전이표에 있는 쌍만 허용 — 표 밖 전이(역전이·직행·종료 상태 이탈)는 거부. */
internal fun isAllowedTransition(
    from: JobState,
    target: JobState,
): Boolean = (from to target) in allowedJobStateTransitions

/**
 * `SUCCEEDED` ⟹ `artifact`·`evaluation` 둘 다 설정, `failure` 미설정. `FAILED` ⟹ `failure`
 * 설정, `artifact`·`evaluation` 미설정. 그 밖의 상태는 셋 다 미설정이어야 한다(`CANCELLED`에
 * `artifact` 첨부 거부, 우회 후보 (3)). `UNSPECIFIED`·정의 밖 정수는 거부.
 */
internal fun isValidJobCombination(job: TrainingJob): Boolean =
    when (job.state) {
        JobState.JOB_STATE_SUCCEEDED -> {
            job.hasArtifact() && job.hasEvaluation() && !job.hasFailure()
        }

        JobState.JOB_STATE_FAILED -> {
            job.hasFailure() && !job.hasArtifact() && !job.hasEvaluation()
        }

        JobState.JOB_STATE_ACCEPTED, JobState.JOB_STATE_RUNNING, JobState.JOB_STATE_CANCELLED -> {
            !job.hasArtifact() && !job.hasEvaluation() && !job.hasFailure()
        }

        JobState.JOB_STATE_UNSPECIFIED, JobState.UNRECOGNIZED -> {
            false
        }
    }

private fun compareTimestamps(
    left: Timestamp,
    right: Timestamp,
): Int {
    val bySeconds = left.seconds.compareTo(right.seconds)
    return if (bySeconds != 0) bySeconds else left.nanos.compareTo(right.nanos)
}

/** D-2C-5 — `accepted_at ≤ started_at ≤ finished_at`(설정된 성분끼리만 비교, UTC). */
internal fun isValidTimestampOrder(job: TrainingJob): Boolean {
    val acceptedBeforeStarted = !job.hasStartedAt() || compareTimestamps(job.acceptedAt, job.startedAt) <= 0
    val acceptedBeforeFinished = !job.hasFinishedAt() || compareTimestamps(job.acceptedAt, job.finishedAt) <= 0
    val startedBeforeFinished =
        !job.hasFinishedAt() || !job.hasStartedAt() || compareTimestamps(job.startedAt, job.finishedAt) <= 0
    return acceptedBeforeStarted && acceptedBeforeFinished && startedBeforeFinished
}

/** 위협 모델 (f) — 응답 `artifact.release.dataset_id`는 요청 `dataset.dataset_id`와 같아야 한다. */
internal fun artifactMatchesRequestedDataset(
    artifact: ArtifactReference,
    requestedDatasetId: String,
): Boolean = artifact.release.datasetId == requestedDatasetId

internal fun isAcceptableJobState(state: JobState): Boolean =
    state != JobState.JOB_STATE_UNSPECIFIED && state != JobState.UNRECOGNIZED

internal fun isAcceptableJobFailureCode(code: JobFailureCode): Boolean =
    code != JobFailureCode.JOB_FAILURE_CODE_UNSPECIFIED && code != JobFailureCode.UNRECOGNIZED

private val sha256HexPattern = Regex("^[0-9a-f]{64}$")

/** `manifest_checksum`·`artifact_checksum`·evaluation `checksum` 은 소문자 64자 hex 정규형. */
internal fun isValidSha256Hex(value: String): Boolean = sha256HexPattern.matches(value)

/** 우회 후보 (1) — `idempotency_key`·`training_spec_version`은 비어 있으면 거부. */
internal fun isValidStartTrainingRequest(request: StartTrainingRequest): Boolean =
    request.idempotencyKey.isNotBlank() &&
        request.trainingSpecVersion.isNotBlank() &&
        request.dataset.uri.isNotBlank() &&
        request.dataset.datasetId.isNotBlank() &&
        isValidSha256Hex(request.dataset.manifestChecksum)
