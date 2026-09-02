package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * 함수 크기 임계를 정책 파일에서 detekt 설정으로 옮긴다. 같은 수를 `detekt.yml` 에도 적으면
 * 두 자리가 어긋날 수 있으므로 생성으로 잇는다 — 정책 파일이 유일한 자리다.
 *
 * detekt 2.0 은 1.x 의 `threshold` 키를 읽지 않는다(`allowedLines`). 키 이름이 틀리면
 * **조용히 무시**되므로 이 overlay 는 2.0 키만 쓴다.
 */
abstract class DetektThresholdOverlayTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:OutputFile
    abstract val overlayFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val policy = readPolicy(policyFile.get().asFile)
        overlayFile.get().asFile.apply { parentFile.mkdirs() }.writeText(
            """
            complexity:
              LongMethod:
                active: true
                allowedLines: ${policy.requireInt("limit.function.lines")}

            """.trimIndent(),
        )
    }
}
