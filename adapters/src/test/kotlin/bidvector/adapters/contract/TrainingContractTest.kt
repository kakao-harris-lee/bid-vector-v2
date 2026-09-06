package bidvector.adapters.contract

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Message
import contract.bidvector.ml.v1.CancelTrainingJobRequest
import contract.bidvector.ml.v1.CancelTrainingJobResponse
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.GetTrainingJobRequest
import contract.bidvector.ml.v1.GetTrainingJobResponse
import contract.bidvector.ml.v1.JobFailure
import contract.bidvector.ml.v1.JobFailureCode
import contract.bidvector.ml.v1.JobState
import contract.bidvector.ml.v1.StartTrainingRequest
import contract.bidvector.ml.v1.StartTrainingResponse
import contract.bidvector.ml.v1.TrainingJobHandle
import contract.bidvector.ml.v1.TrainingJobServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * M2/2C — `TrainingJobService` 계약 consumer test. Kotlin in-process fake servicer
 * (`FakeTrainingJobServicer.kt`, 같은 패키지)가 **실제 상태 기계**(job 저장소 map,
 * idempotency 키 대조)를 갖고 canonical 바이트(`contracts/testdata/training/`)를 답으로
 * 낸다. 실제 socket 배선·폴링·취소 존중은 M4·5C 몫이다(scope.md 「만들지 않는 것」).
 * **모든 test는 블록 본문(`{ }`)이다** — `= runBlocking { ... shouldBe }`(식 본문)로 쓰면
 * kotest `shouldBe`가 수신자를 반환해 함수의 추론 반환 타입이 `Unit`이 아니게 되고 JUnit
 * Jupiter가 test로 discover하지 않는다(2B `PredictionContractTest`의 실측 버그,
 * `OPEN-2B-TEST-DISCOVERY-GUARD`). 블록 본문은 항상 `Unit`이라 이 함정이 없다.
 *
 * **거부 규칙(전이표·조합 불변식·timestamp 순서·dataset_id 일치·fail-closed enum·checksum
 * 정규형)은 `TrainingContractRules.kt`(같은 패키지)의 순수 함수다** — 2A
 * `ContractFractionRules.kt`와 같은 관례(파일 분리는 v2-지침서.md §5 500줄 한도).
 * 실제 Kotlin validation 구현은 M4 몫이고, 여기서는 test가 그 불변식을 문서화·고정한다.
 */
class TrainingContractTest {
    private val testdataRoot: Path =
        Path
            .of(
                System.getProperty("bidvector.contracts.testdata")
                    ?: error("시스템 속성 'bidvector.contracts.testdata' 가 없다 — 빌드가 넘긴다"),
            ).resolve("training")

    private fun bytes(name: String): ByteArray = Files.readAllBytes(testdataRoot.resolve(name))

    private fun canonicalBytes(message: Message): ByteArray {
        val buffer = ByteArrayOutputStream()
        val coded = CodedOutputStream.newInstance(buffer)
        coded.useDeterministicSerialization()
        message.writeTo(coded)
        coded.flush()
        return buffer.toByteArray()
    }

    private val requestBytes = bytes("start_training_request.binpb")
    private val acceptedBytes = bytes("start_training_response_accepted.binpb")
    private val runningBytes = bytes("get_training_job_response_running.binpb")
    private val succeededBytes = bytes("get_training_job_response_succeeded.binpb")
    private val failedBytes = bytes("get_training_job_response_failed.binpb")
    private val cancelledBytes = bytes("get_training_job_response_cancelled.binpb")
    private val idempotencyConflictBytes = bytes("start_training_response_failure_idempotency_conflict.binpb")
    private val jobNotFoundBytes = bytes("get_training_job_response_failure_job_not_found.binpb")

    private var server: Server? = null
    private var channel: ManagedChannel? = null

    @AfterEach
    fun tearDown() {
        channel?.shutdownNow()
        server?.shutdownNow()
    }

    /** 실제 상태 기계를 가진 fake servicer 위에 in-process stub 을 배선한다(scope.md ⑨). */
    private fun stub(): Pair<TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub, FakeTrainingJobServicer> {
        val serverName = "bidvector-2c-fake-${System.nanoTime()}"
        val servicer = FakeTrainingJobServicer()
        server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(servicer)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return TrainingJobServiceGrpcKt.TrainingJobServiceCoroutineStub(channel!!) to servicer
    }

    // ---- fake servicer 상태 기계 — 실제 RPC(scope.md ⑨, 위협 모델 (g)) ----

    @Test
    fun `StartTraining 은 즉시 ACCEPTED 를 낸다`() {
        runBlocking {
            val (stub, _) = stub()
            val response = stub.startTraining(StartTrainingRequest.parseFrom(requestBytes))
            response.resultCase shouldBe StartTrainingResponse.ResultCase.HANDLE
            response.handle.state shouldBe JobState.JOB_STATE_ACCEPTED
        }
    }

    @Test
    fun `같은 idempotency_key 두 번은 같은 job_id 를 낸다(변이 1 대조)`() {
        runBlocking {
            val (stub, servicer) = stub()
            val request = StartTrainingRequest.parseFrom(requestBytes)
            val first = stub.startTraining(request)
            val second = stub.startTraining(request)
            first.handle.jobId shouldBe second.handle.jobId
            servicer.jobCount() shouldBe 1
        }
    }

    @Test
    fun `같은 idempotency_key 에 다른 dataset 이면 IDEMPOTENCY_CONFLICT 다(변이 4)`() {
        runBlocking {
            val (stub, servicer) = stub()
            val request = StartTrainingRequest.parseFrom(requestBytes)
            stub.startTraining(request)
            val differentDataset =
                request
                    .toBuilder()
                    .also { it.datasetBuilder.datasetId = "dataset-other-2026-09" }
                    .build()
            val response = stub.startTraining(differentDataset)
            response.resultCase shouldBe StartTrainingResponse.ResultCase.FAILURE
            response.failure.code shouldBe FailureCode.FAILURE_CODE_IDEMPOTENCY_CONFLICT
            servicer.jobCount() shouldBe 1
        }
    }

    @Test
    fun `미지 training_spec_version 은 StartTraining 을 거부한다(UNSUPPORTED_TRAINING_SPEC)`() {
        runBlocking {
            val (stub, _) = stub()
            val request =
                StartTrainingRequest
                    .parseFrom(requestBytes)
                    .toBuilder()
                    .setTrainingSpecVersion("unknown-spec-v9")
                    .build()
            val response = stub.startTraining(request)
            response.resultCase shouldBe StartTrainingResponse.ResultCase.FAILURE
            response.failure.code shouldBe FailureCode.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC
        }
    }

    @Test
    fun `GetTrainingJob 은 시작한 job 을 ACCEPTED 로 조회한다`() {
        runBlocking {
            val (stub, _) = stub()
            val envelope = StartTrainingRequest.parseFrom(requestBytes).envelope
            val started = stub.startTraining(StartTrainingRequest.parseFrom(requestBytes))
            val response =
                stub.getTrainingJob(
                    GetTrainingJobRequest
                        .newBuilder()
                        .setEnvelope(envelope)
                        .setJobId(started.handle.jobId)
                        .build(),
                )
            response.resultCase shouldBe GetTrainingJobResponse.ResultCase.JOB
            response.job.state shouldBe JobState.JOB_STATE_ACCEPTED
        }
    }

    @Test
    fun `GetTrainingJob 은 미지 job_id 를 JOB_NOT_FOUND 로 거부한다`() {
        runBlocking {
            val (stub, _) = stub()
            val envelope = StartTrainingRequest.parseFrom(requestBytes).envelope
            val response =
                stub.getTrainingJob(
                    GetTrainingJobRequest
                        .newBuilder()
                        .setEnvelope(envelope)
                        .setJobId("unknown-job-id")
                        .build(),
                )
            response.resultCase shouldBe GetTrainingJobResponse.ResultCase.FAILURE
            response.failure.code shouldBe FailureCode.FAILURE_CODE_JOB_NOT_FOUND
        }
    }

    @Test
    fun `CancelTrainingJob 은 ACCEPTED 에서 CANCELLED 로 전이한다`() {
        runBlocking {
            val (stub, _) = stub()
            val envelope = StartTrainingRequest.parseFrom(requestBytes).envelope
            val started = stub.startTraining(StartTrainingRequest.parseFrom(requestBytes))
            val response =
                stub.cancelTrainingJob(
                    CancelTrainingJobRequest
                        .newBuilder()
                        .setEnvelope(envelope)
                        .setJobId(started.handle.jobId)
                        .build(),
                )
            response.resultCase shouldBe CancelTrainingJobResponse.ResultCase.JOB
            response.job.state shouldBe JobState.JOB_STATE_CANCELLED
        }
    }

    @Test
    fun `CancelTrainingJob 은 RUNNING 에서도 CANCELLED 로 전이한다`() {
        runBlocking {
            val (stub, servicer) = stub()
            val envelope = StartTrainingRequest.parseFrom(requestBytes).envelope
            val started = stub.startTraining(StartTrainingRequest.parseFrom(requestBytes))
            servicer.advanceToRunning(started.handle.jobId)
            val response =
                stub.cancelTrainingJob(
                    CancelTrainingJobRequest
                        .newBuilder()
                        .setEnvelope(envelope)
                        .setJobId(started.handle.jobId)
                        .build(),
                )
            response.job.state shouldBe JobState.JOB_STATE_CANCELLED
        }
    }

    @Test
    fun `CancelTrainingJob 은 종료 상태에서 멱등 no-op 이다(scope md 항목 5)`() {
        runBlocking {
            val (stub, _) = stub()
            val envelope = StartTrainingRequest.parseFrom(requestBytes).envelope
            val started = stub.startTraining(StartTrainingRequest.parseFrom(requestBytes))
            val cancelRequest =
                CancelTrainingJobRequest
                    .newBuilder()
                    .setEnvelope(envelope)
                    .setJobId(started.handle.jobId)
                    .build()
            val first = stub.cancelTrainingJob(cancelRequest)
            val second = stub.cancelTrainingJob(cancelRequest)
            first.job.state shouldBe JobState.JOB_STATE_CANCELLED
            second.resultCase shouldBe CancelTrainingJobResponse.ResultCase.JOB
            second.job.state shouldBe JobState.JOB_STATE_CANCELLED
        }
    }

    @Test
    fun `CancelTrainingJob 은 미지 job_id 를 JOB_NOT_FOUND 로 거부한다`() {
        runBlocking {
            val (stub, _) = stub()
            val envelope = StartTrainingRequest.parseFrom(requestBytes).envelope
            val response =
                stub.cancelTrainingJob(
                    CancelTrainingJobRequest
                        .newBuilder()
                        .setEnvelope(envelope)
                        .setJobId("unknown-job-id")
                        .build(),
                )
            response.resultCase shouldBe CancelTrainingJobResponse.ResultCase.FAILURE
            response.failure.code shouldBe FailureCode.FAILURE_CODE_JOB_NOT_FOUND
        }
    }

    // ---- 전이표에 있는 쌍만 허용 — 표 밖 전이는 consumer 가 거부(순수 함수, scope.md ④) ----

    @Test
    fun `전이표에 있는 쌍은 전부 허용된다`() {
        isAllowedTransition(JobState.JOB_STATE_ACCEPTED, JobState.JOB_STATE_RUNNING) shouldBe true
        isAllowedTransition(JobState.JOB_STATE_RUNNING, JobState.JOB_STATE_SUCCEEDED) shouldBe true
        isAllowedTransition(JobState.JOB_STATE_RUNNING, JobState.JOB_STATE_FAILED) shouldBe true
        isAllowedTransition(JobState.JOB_STATE_ACCEPTED, JobState.JOB_STATE_CANCELLED) shouldBe true
        isAllowedTransition(JobState.JOB_STATE_RUNNING, JobState.JOB_STATE_CANCELLED) shouldBe true
    }

    @Test
    fun `역전이 RUNNING 에서 ACCEPTED 는 거부된다(변이 2)`() {
        isAllowedTransition(JobState.JOB_STATE_RUNNING, JobState.JOB_STATE_ACCEPTED) shouldBe false
    }

    @Test
    fun `종료 상태에서 나가는 전이는 전부 거부된다`() {
        isAllowedTransition(JobState.JOB_STATE_SUCCEEDED, JobState.JOB_STATE_RUNNING) shouldBe false
        isAllowedTransition(JobState.JOB_STATE_FAILED, JobState.JOB_STATE_CANCELLED) shouldBe false
        isAllowedTransition(JobState.JOB_STATE_CANCELLED, JobState.JOB_STATE_RUNNING) shouldBe false
    }

    @Test
    fun `ACCEPTED 에서 SUCCEEDED 나 FAILED 로 직행하는 전이는 거부된다`() {
        isAllowedTransition(JobState.JOB_STATE_ACCEPTED, JobState.JOB_STATE_SUCCEEDED) shouldBe false
        isAllowedTransition(JobState.JOB_STATE_ACCEPTED, JobState.JOB_STATE_FAILED) shouldBe false
    }

    // ---- SUCCEEDED/FAILED 조합 불변식 — CANCELLED 에 artifact 첨부 거부(우회 후보 3) ----

    @Test
    fun `testdata 의 SUCCEEDED 는 artifact 와 evaluation 을 둘 다 갖는다`() {
        val job = GetTrainingJobResponse.parseFrom(succeededBytes).job
        isValidJobCombination(job) shouldBe true
    }

    @Test
    fun `SUCCEEDED 인데 artifact 가 없으면 계약 불변식 위반이다(변이 3)`() {
        val response = GetTrainingJobResponse.parseFrom(succeededBytes)
        val mutated = response.toBuilder().also { it.jobBuilder.clearArtifact() }.build()
        isValidJobCombination(mutated.job) shouldBe false
    }

    @Test
    fun `SUCCEEDED 인데 evaluation 이 없으면 계약 불변식 위반이다`() {
        val response = GetTrainingJobResponse.parseFrom(succeededBytes)
        val mutated = response.toBuilder().also { it.jobBuilder.clearEvaluation() }.build()
        isValidJobCombination(mutated.job) shouldBe false
    }

    @Test
    fun `testdata 의 FAILED 는 failure 를 갖는다`() {
        val job = GetTrainingJobResponse.parseFrom(failedBytes).job
        isValidJobCombination(job) shouldBe true
    }

    @Test
    fun `FAILED 인데 failure 가 없으면 계약 불변식 위반이다`() {
        val response = GetTrainingJobResponse.parseFrom(failedBytes)
        val mutated = response.toBuilder().also { it.jobBuilder.clearFailure() }.build()
        isValidJobCombination(mutated.job) shouldBe false
    }

    @Test
    fun `CANCELLED 에 artifact 가 첨부되면 계약 불변식 위반이다(우회 후보 3)`() {
        val cancelled = GetTrainingJobResponse.parseFrom(cancelledBytes)
        val succeededArtifact = GetTrainingJobResponse.parseFrom(succeededBytes).job.artifact
        val mutated = cancelled.toBuilder().also { it.jobBuilder.setArtifact(succeededArtifact) }.build()
        isValidJobCombination(mutated.job) shouldBe false
    }

    @Test
    fun `testdata 의 CANCELLED RUNNING 은 artifact evaluation failure 가 전부 없다`() {
        isValidJobCombination(GetTrainingJobResponse.parseFrom(cancelledBytes).job) shouldBe true
        isValidJobCombination(GetTrainingJobResponse.parseFrom(runningBytes).job) shouldBe true
    }

    // ---- timestamp 순서(D-2C-5) — accepted_at ≤ started_at ≤ finished_at ----

    @Test
    fun `testdata 의 timestamp 순서가 맞다`() {
        isValidTimestampOrder(GetTrainingJobResponse.parseFrom(succeededBytes).job) shouldBe true
        isValidTimestampOrder(GetTrainingJobResponse.parseFrom(failedBytes).job) shouldBe true
        isValidTimestampOrder(GetTrainingJobResponse.parseFrom(cancelledBytes).job) shouldBe true
        isValidTimestampOrder(GetTrainingJobResponse.parseFrom(runningBytes).job) shouldBe true
    }

    @Test
    fun `finished_at 이 accepted_at 보다 앞서면 계약 불변식 위반이다(timestamp 역순, 변이 6)`() {
        val response = GetTrainingJobResponse.parseFrom(succeededBytes)
        val acceptedAt = response.job.acceptedAt
        val reversedFinishedAt = acceptedAt.toBuilder().setSeconds(acceptedAt.seconds - 3600).build()
        val mutated = response.toBuilder().also { it.jobBuilder.finishedAt = reversedFinishedAt }.build()
        isValidTimestampOrder(mutated.job) shouldBe false
    }

    // ---- dataset_id 일치(위협 모델 (f)) ----

    @Test
    fun `artifact release dataset_id 는 요청 dataset_id 와 같아야 한다`() {
        val requestDatasetId = StartTrainingRequest.parseFrom(requestBytes).dataset.datasetId
        val artifact = GetTrainingJobResponse.parseFrom(succeededBytes).job.artifact
        artifactMatchesRequestedDataset(artifact, requestDatasetId) shouldBe true
    }

    @Test
    fun `artifact release dataset_id 가 다르면 계약 불변식 위반이다(변이 5)`() {
        val requestDatasetId = StartTrainingRequest.parseFrom(requestBytes).dataset.datasetId
        val artifact = GetTrainingJobResponse.parseFrom(succeededBytes).job.artifact
        val mismatched =
            artifact
                .toBuilder()
                .also { it.releaseBuilder.datasetId = "other-dataset-id" }
                .build()
        artifactMatchesRequestedDataset(mismatched, requestDatasetId) shouldBe false
    }

    // ---- UNSPECIFIED·정의 밖 정수 거부(fail-closed) ----

    @Test
    fun `JobState UNSPECIFIED 는 거부된다`() {
        isAcceptableJobState(JobState.JOB_STATE_UNSPECIFIED) shouldBe false
    }

    @Test
    fun `JobState 정의 밖 정수는 거부된다`() {
        val handle = TrainingJobHandle.newBuilder().setStateValue(99).build()
        handle.state shouldBe JobState.UNRECOGNIZED
        isAcceptableJobState(handle.state) shouldBe false
    }

    @Test
    fun `JobFailureCode UNSPECIFIED 는 거부된다`() {
        isAcceptableJobFailureCode(JobFailureCode.JOB_FAILURE_CODE_UNSPECIFIED) shouldBe false
    }

    @Test
    fun `JobFailureCode 정의 밖 정수는 거부된다`() {
        val failure = JobFailure.newBuilder().setCodeValue(88).build()
        failure.code shouldBe JobFailureCode.UNRECOGNIZED
        isAcceptableJobFailureCode(failure.code) shouldBe false
    }

    // ---- checksum 정규형(sha256 hex, 소문자 64자) ----

    @Test
    fun `testdata 의 checksum 셋은 sha256 hex 정규형이다`() {
        val request = StartTrainingRequest.parseFrom(requestBytes)
        isValidSha256Hex(request.dataset.manifestChecksum) shouldBe true
        val job = GetTrainingJobResponse.parseFrom(succeededBytes).job
        isValidSha256Hex(job.artifact.release.artifactChecksum) shouldBe true
        isValidSha256Hex(job.evaluation.checksum) shouldBe true
    }

    @Test
    fun `길이가 64가 아니거나 hex 가 아닌 checksum 은 거부된다`() {
        isValidSha256Hex("deadbeef") shouldBe false
        isValidSha256Hex("g".repeat(64)) shouldBe false
        isValidSha256Hex("A".repeat(64)) shouldBe false // 대문자 hex 도 정규형 밖(소문자 고정)
    }

    // ---- StartTrainingRequest 필수값(idempotency_key 비어있음 거부, 우회 후보 1) ----

    @Test
    fun `testdata 의 StartTrainingRequest 는 유효하다`() {
        isValidStartTrainingRequest(StartTrainingRequest.parseFrom(requestBytes)) shouldBe true
    }

    @Test
    fun `idempotency_key 가 빈 문자열이면 거부된다(우회 후보 1)`() {
        val request =
            StartTrainingRequest
                .parseFrom(requestBytes)
                .toBuilder()
                .setIdempotencyKey("")
                .build()
        isValidStartTrainingRequest(request) shouldBe false
    }

    @Test
    fun `training_spec_version 이 빈 문자열이면 거부된다`() {
        val request =
            StartTrainingRequest
                .parseFrom(requestBytes)
                .toBuilder()
                .setTrainingSpecVersion("")
                .build()
        isValidStartTrainingRequest(request) shouldBe false
    }

    // ---- round-trip(testdata 바이트 ↔ 생성 타입) ----

    @Test
    fun `StartTrainingRequest 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(StartTrainingRequest.parseFrom(requestBytes)).toList() shouldBe requestBytes.toList()
    }

    @Test
    fun `StartTrainingResponse ACCEPTED 핸들은 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(StartTrainingResponse.parseFrom(acceptedBytes)).toList() shouldBe acceptedBytes.toList()
    }

    @Test
    fun `GetTrainingJobResponse RUNNING 은 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetTrainingJobResponse.parseFrom(runningBytes)).toList() shouldBe runningBytes.toList()
    }

    @Test
    fun `GetTrainingJobResponse SUCCEEDED 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetTrainingJobResponse.parseFrom(succeededBytes)).toList() shouldBe succeededBytes.toList()
    }

    @Test
    fun `GetTrainingJobResponse FAILED 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetTrainingJobResponse.parseFrom(failedBytes)).toList() shouldBe failedBytes.toList()
    }

    @Test
    fun `GetTrainingJobResponse CANCELLED 는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetTrainingJobResponse.parseFrom(cancelledBytes)).toList() shouldBe cancelledBytes.toList()
    }

    @Test
    fun `StartTrainingResponse IDEMPOTENCY_CONFLICT 실패는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(StartTrainingResponse.parseFrom(idempotencyConflictBytes)).toList() shouldBe
            idempotencyConflictBytes.toList()
    }

    @Test
    fun `GetTrainingJobResponse JOB_NOT_FOUND 실패는 canonicalization 후 원본과 바이트가 같다`() {
        canonicalBytes(GetTrainingJobResponse.parseFrom(jobNotFoundBytes)).toList() shouldBe jobNotFoundBytes.toList()
    }
}
