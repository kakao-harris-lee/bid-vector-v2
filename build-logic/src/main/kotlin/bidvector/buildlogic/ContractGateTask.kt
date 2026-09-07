package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * M2/2D — `.proto` 계약 drift·생성물 수동 편집·게이트 밖 소스를 잡는 게이트 하나
 * (scope.md 「이 slice 가 하는 일」 ①②③). 판정 로직은 전부 `ContractGateChecks.kt`의 순수
 * 함수다 — 이 task 는 외부 프로세스(`buf`·`gradlew`·`git`)를 실행하고 그 결과를 넘기기만
 * 한다. **`Internal`로 표시한다** — 외부 프로세스·git 작업 트리 상태를 Gradle 입력
 * 지문으로 정직하게 표현할 수 없어 항상 재실행한다(배선부의 `outputs.upToDateWhen { false }`).
 */
abstract class ContractGateTask : DefaultTask() {
    @get:Internal
    abstract val policyFile: RegularFileProperty

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:Internal
    abstract val rootGradlew: RegularFileProperty

    @get:Internal
    abstract val catalogProtobufRuntimeVersion: Property<String>

    @get:Internal
    abstract val catalogGrpcKotlinVersion: Property<String>

    // verifier r1 F-13 — 생성물의 Java/gRPC 절반을 만드는 플러그인 버전도 대조한다.
    @get:Internal
    abstract val catalogGrpcJavaVersion: Property<String>

    @get:Internal
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = ContractPolicy.load(policyFile.get().asFile)
        val root = repoRoot.get().asFile
        val mlContractDir = File(root, "ml-contract")

        val violations =
            checkToolVersions(policy, root) +
                checkBufLint(root) +
                checkBufBreaking(policy, root) +
                checkGenerationDeterminism(root, mlContractDir) +
                checkCleanWorkingTree(root) +
                checkNoIncludedBuildSource(mlContractDir)

        writeReport(violations)
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(prefix = "contractGate 위반:\n  ", separator = "\n  "))
        }
    }

    // (a) 도구 버전 assertion — buf 런타임 실측 + 카탈로그 대조(두 정책 표면의 drift).
    private fun checkToolVersions(
        policy: ContractPolicy,
        root: File,
    ): List<String> {
        val bufVersion = run(root, "buf", "--version").output
        val protobufCatalog = catalogProtobufRuntimeVersion.get()
        val grpcKotlinCatalog = catalogGrpcKotlinVersion.get()
        val grpcJavaCatalog = catalogGrpcJavaVersion.get()
        return listOfNotNull(
            toolVersionViolation("buf", bufVersion, policy.bufVersion),
            toolVersionViolation("protobuf-runtime(카탈로그)", protobufCatalog, policy.protocVersion),
            toolVersionViolation("grpc-kotlin(카탈로그)", grpcKotlinCatalog, policy.protocGenGrpcKotlinVersion),
            toolVersionViolation("protoc-gen-grpc-java(카탈로그)", grpcJavaCatalog, policy.protocGenGrpcJavaVersion),
            approvedTagViolation(policy.approvedTag, policy.approvedTagPattern),
        )
    }

    // (b) buf lint
    private fun checkBufLint(root: File): List<String> {
        val lint = run(root, "buf", "lint", "contracts")
        return listOfNotNull(bufProcessViolation("buf lint", lint.exitCode, lint.output))
    }

    // (c) buf breaking — against 승인 태그(D-2D-1 (a)).
    private fun checkBufBreaking(
        policy: ContractPolicy,
        root: File,
    ): List<String> {
        val against = ".git#tag=${policy.approvedTag},subdir=contracts"
        val breaking = run(root, "buf", "breaking", "contracts", "--against", against)
        return listOfNotNull(bufProcessViolation("buf breaking", breaking.exitCode, breaking.output))
    }

    /**
     * (d) generateProto 결정성 — **격리된 임시 사본**에서 두 번 clean+generate 하고 산출물
     * 해시를 비교한다. 실측(2026-09-07) — 살아있는 `ml-contract/build`를 직접
     * `clean generateProto`하면, 같은 `check` 실행 안에서 병렬로 도는 다른 모듈의
     * `compileTestKotlin`(같은 디렉터리를 composite 치환으로 참조)이 그 사이 지워진 산출물을
     * 읽어 "Unresolved reference"로 무너진다 — 공유 작업 트리에서 다른 세션이 동시에 gradle
     * 을 돌릴 가능성까지 포함해, 이 게이트는 자신의 결정성 실측이 어떤 동시 실행도 건드리지
     * 않는 별도 디렉터리에서만 일어나게 한다. `contracts/proto`(상대경로 srcDir)와
     * `gradle/libs.versions.toml`(상대경로 카탈로그)의 상대 참조를 그대로 두기 위해 두 형제
     * 디렉터리를 함께 복사한다.
     */
    private fun checkGenerationDeterminism(
        root: File,
        mlContractDir: File,
    ): List<String> {
        val gradlew = rootGradlew.get().asFile.absolutePath
        val isolated = Files.createTempDirectory("bidvector-contract-gate-determinism")
        try {
            copyDirectory(root.resolve("gradle").toPath(), isolated.resolve("gradle"))
            copyDirectory(root.resolve("contracts/proto").toPath(), isolated.resolve("contracts/proto"))
            val isolatedMlContract = isolated.resolve("ml-contract").toFile().apply { mkdirs() }
            for (buildFile in listOf("build.gradle.kts", "gradle.properties", "settings.gradle.kts")) {
                File(mlContractDir, buildFile).copyTo(File(isolatedMlContract, buildFile))
            }
            val generatedDir = File(isolatedMlContract, "build/generated/sources/proto/main")

            val first = generateProtoOnce(isolated.toFile(), gradlew, isolatedMlContract, "1회차")
            val firstHashes = hashDirectoryContents(generatedDir)
            val second = generateProtoOnce(isolated.toFile(), gradlew, isolatedMlContract, "2회차")
            val secondHashes = hashDirectoryContents(generatedDir)
            return first + second + listOfNotNull(generationDeterminismViolation(firstHashes, secondHashes))
        } finally {
            isolated.toFile().deleteRecursively()
        }
    }

    /**
     * verifier r1 F-1(high) — 이 중첩 호출에 `--no-build-cache`가 없으면 격리 사본의
     * `ml-contract/gradle.properties`(`org.gradle.caching=true`)가 공유 build cache 를 켠
     * 채로 두 회차 모두 **같은 캐시 엔트리에서 복원**된다(`generateProto FROM-CACHE` 두 번,
     * 실측). 그러면 비교되는 두 해시 집합이 같은 복원본이라 결정성 검사가 구조적으로
     * 실패할 수 없다 — `--no-build-cache`로 매 회차가 실제 protoc 실행이게 한다.
     */
    private fun generateProtoOnce(
        workingDir: File,
        gradlew: String,
        mlContractDir: File,
        label: String,
    ): List<String> {
        val result =
            run(
                workingDir,
                gradlew,
                "--offline",
                "--no-build-cache",
                "--project-dir",
                mlContractDir.path,
                "clean",
                "generateProto",
            )
        return listOfNotNull(bufProcessViolation("generateProto($label)", result.exitCode, result.output))
    }

    private fun copyDirectory(
        source: Path,
        target: Path,
    ) {
        if (!Files.isDirectory(source)) return
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path))
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    // (e) 작업 트리 청결 — 생성·게이트 실행이 `ml-contract`·`contracts`를 더럽히지 않는다.
    private fun checkCleanWorkingTree(root: File): List<String> {
        val paths = listOf("ml-contract", "contracts")
        val status = run(root, "git", "status", "--porcelain", "--", *paths.toTypedArray())
        return listOfNotNull(uncleanWorkingTreeViolation(paths, status.output))
    }

    // (f) 무소스 단언 — `ml-contract/src` 부재 + 최상위 파일이 빌드 파일 셋뿐(2A S-5 게이트화).
    private fun checkNoIncludedBuildSource(mlContractDir: File): List<String> {
        val topLevelFiles =
            mlContractDir
                .listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                ?.toSet()
                .orEmpty()
        val expected = setOf("build.gradle.kts", "gradle.properties", "settings.gradle.kts")
        val violation =
            includedBuildSourcePresenceViolation(
                srcDirectoryExists = File(mlContractDir, "src").exists(),
                topLevelFileNames = topLevelFiles,
                expectedTopLevelFiles = expected,
            )
        return listOfNotNull(violation)
    }

    private fun writeReport(violations: List<String>) {
        val lines = listOf("contractGate", "violations=${violations.size}") + violations
        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private class ProcessOutput(
        val exitCode: Int,
        val output: String,
    )

    private fun run(
        workingDir: File,
        vararg command: String,
    ): ProcessOutput {
        val buffer = ByteArrayOutputStream()
        val process =
            ProcessBuilder(*command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
        process.inputStream.copyTo(buffer)
        val exitCode = process.waitFor()
        return ProcessOutput(exitCode, buffer.toString(Charsets.UTF_8))
    }
}
