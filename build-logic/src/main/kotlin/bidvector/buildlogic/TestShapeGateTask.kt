package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * test-메서드(정책 목록)가 JUnit Jupiter 가 조용히 discover 하지 않는 형태(식 본문·비-Unit 명시
 * 반환·suspend·private)이면 실패한다 — `OPEN-2B-TEST-DISCOVERY-GUARD`(2B 에서 25/27 가짜
 * 초록 실측). 판정은 [TestShapes.extractAll] 이 하고 이 task 는 배선·report 만
 * (`DomainApiTypeGateTask` 관례).
 *
 * **빈 스캔은 실패가 아니다** — 설계 검토 `_workspace/harness-test-shape/02_design-review.md`
 * A-5. `settlement`·`workflow` 처럼 test 디렉터리가 없는 모듈은 「있는 test 의 형태」를 잴
 * 대상이 없을 뿐이다(`DomainApiTypeGateTask`·`typeShapeGate` 의 「빈 스캔 = 실패」와 다른
 * 이유 — 그쪽은 「스캔 대상이 비면 게이트가 아무것도 안 잰 것」이고 여기는 「test 가 없는
 * 모듈」이 정당 상태다). 「test 가 있는가·돌았는가」는 `gateExecutionGate` 가 잰다.
 */
abstract class TestShapeGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = TestShapePolicy.load(readPolicy(policyFile.get().asFile))
        val files = sources.asFileTree.files.sortedBy(File::getPath)
        val violations =
            TestShapes.extractAll(files, policy).sortedWith(compareBy({ it.fileName }, { it.line }))

        // verifier r1 F-7 — `scanned=`(스캔 대상 파일 총수, 확장자 무관 — `sources` 는
        // `sourceSetLayoutGate` 가 인정하는 test source set 트리 전체다)과 `parsed 없음` 을
        // 구분한다. `TestShapes.extractAll` 이 Kotlin 이 아닌 파일을 걸러 파싱하므로(`.md`
        // 등은 0건 기여), 이 값을 "파싱한 Kotlin 파일 수"로 읽으면 어긋난다.
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (
                listOf("scanned=${files.size}", "violations=${violations.size}") +
                    violations.map { "${it.fileName}:${it.line} ${it.functionName} — ${it.reason}" }
            ).joinToString("\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "JUnit 이 조용히 discover 하지 않는 test 형태가 있다(OPEN-2B-TEST-DISCOVERY-GUARD):\n  ",
                    separator = "\n  ",
                ) { "${File(it.fileName).name}:${it.line} ${it.functionName} — ${it.reason}" },
            )
        }
    }
}
