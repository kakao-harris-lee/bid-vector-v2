package bidvector.adapters.contract

import com.google.protobuf.Timestamp
import contract.bidvector.ml.v1.ArtifactReference
import contract.bidvector.ml.v1.JobFailureCode
import contract.bidvector.ml.v1.JobState
import contract.bidvector.ml.v1.StartTrainingRequest
import contract.bidvector.ml.v1.TrainingJob
import java.nio.file.Files

/**
 * `TrainingJobService` 계약이 요구하는 거부 규칙(전이표·조합 불변식·timestamp
 * 순서·dataset_id 일치·fail-closed enum·checksum 정규형)을 순수 함수로 문서화·고정한다
 * (`TrainingContractTest`·`TrainingTransitionTableTest`·`test_training_contract.py` 에서
 * 대칭으로 검증, `ContractFractionRules.kt`와 같은 관례 — `TrainingContractTest.kt`의
 * 500줄 한도를 넘기지 않도록 분리했다, v2-지침서.md §5). 실제 Kotlin validation 구현은
 * 아직 없다.
 *
 * 아래 전이표는 **데이터**이고 `internal` 이다 — `TrainingTransitionTableTest` 가 5x5 전 쌍
 * 진리표를 이 데이터와 대조하기 때문이다(M6/6B-4 vr M-2 P1b: `isAllowedTransition` 에 절을
 * 하나 더해(`|| from == JOB_STATE_FAILED`) 데이터와 어긋나게 만드는 변이는 **쌍을 열거한**
 * test 로는 잡히지 않았다. 전 쌍 대조는 자리마다 다른 답이 나오면 바로 붉어진다). 데이터
 * 자체가 Python `_TRANSITIONS` 와 같은지는 교차 등식 게이트가 센다.
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

/**
 * ADR 0010 D-8 의 **렌더링 전이표 블록**을 읽어 간선 집합으로 돌려준다(M6/6B-4 vr M-1).
 *
 * 그 블록은 Python 의 실행되는 표(`training.jobs.state._TRANSITIONS`)에서 렌더링한 바이트와
 * 등식이고(`ml-engine/tests/gates/test_job_transition_table_parity.py`), 이 함수가 그것을
 * 읽으므로 Kotlin 기대값이 **Python 에 전이적으로** 묶인다 — 새 데이터 파일 없이.
 *
 * 앞 판은 `TrainingContractTest` 가 자기 허용표와 자기 술어만 대조했다. 그래서 허용표
 * **데이터**를 바꾸는 변이(한 행을 `//` 로 막고 별칭으로 다른 행을 더하기 · 블록 뒤에
 * `) - setOf(…) + setOf(…)` 산술 · `forNumber`·`valueOf` 경유)가 Gradle 축까지 초록으로
 * 지나갔다(verifier r2 K1~K5 실측).
 *
 * 경로는 이미 있는 시스템 속성에서 **파생**한다(`contracts/testdata` → 저장소 뿌리) —
 * 새 속성을 넣으려면 `adapters/build.gradle.kts` 를 고쳐야 하고 그 파일은 이 slice
 * in_scope 밖이다. 알려진 제한: 이 task 의 Gradle 입력에 ADR 이 등재되지 않으므로 ADR 만
 * 고친 증분 빌드에서는 재실행되지 않을 수 있다(acceptance 는 `--rerun-tasks` 이고, ADR 쪽
 * 편집은 Python 게이트가 항상 다시 센다).
 */
internal fun renderedTransitionTableFromAdr(): Set<Pair<JobState, JobState>> {
    val repoRoot = contractTestdataRoot().parent.parent
    val adr = repoRoot.resolve("docs/adr/0010-ml-call-budget-and-training-transport.md")
    val lines = Files.readAllLines(adr)
    val begin = lines.indexOfFirst { it.contains("transition-table-begin") }
    val end = lines.indexOfFirst { it.contains("transition-table-end") }
    require(begin >= 0 && end > begin) { "ADR 에서 전이표 블록 마커를 찾지 못했다: $adr" }
    val rowPattern = Regex("^- `([A-Z]+) \u2192 ([A-Z]+)`$")
    return lines.subList(begin + 1, end).map { line ->
        val match = rowPattern.matchEntire(line.trim())
        require(match != null) { "렌더링 블록의 줄 모양이 아니다: $line" }
        val (left, right) = match.destructured
        JobState.valueOf("JOB_STATE_$left") to JobState.valueOf("JOB_STATE_$right")
    }.toSet()
}

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
