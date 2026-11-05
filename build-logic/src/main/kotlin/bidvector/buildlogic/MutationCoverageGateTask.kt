package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * **pitest 가 걸리지 않은 대상 모듈을 잡는다.**
 *
 * mutation 래칫은 모듈의 convention plugin 안에 있으므로, 모듈이 그 plugin 적용을 빼면 래칫
 * task 자체가 사라지고 아무것도 실패하지 않는다 — 게이트의 부재는 조용하다. 정책이 든 대상
 * 집합과 실제로 등록된 집합을 **양방향**으로 댄다(`conventionCoverageGate` 와 같은 자리·사유).
 *
 * 「대상이 순수 도메인 모듈인가」는 이 게이트가 아니라 convention plugin 자신이 적용 시점에
 * 막는다(`layer.domain` 대조) — 구성 단계에서 끊는 쪽이 리포트를 읽는 쪽보다 세다.
 */
abstract class MutationCoverageGateTask : DefaultTask() {
    /**
     * 대상 집합의 정본. **구성 단계가 아니라 실행 시점에 읽는다** — 구성 단계의 직접 파일
     * 읽기는 configuration cache 가 입력으로 추적하지 않아, `mutation.modules` 를 줄인 편집이
     * 낡은 cache 와 함께 조용히 통과할 수 있다. 이 축은 「어느 모듈이 게이트를 받는가」를 정하는
     * 자리라 그 침묵을 받을 수 없다.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:Input
    abstract val registeredModules: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val expected = MutationPolicy.load(policyFile.get().asFile).modules.toSet()
        val registered = registeredModules.get()
        val violations = mutationModuleCoverageViolations(expected, registered)

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("expected=${expected.sorted()}", "registered=${registered.sorted()}") + violations)
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                "mutation 대상 집합이 어긋났다(ADR 0007 OPEN-ADR-07):\n" +
                    violations.joinToString(separator = "\n") { "  $it" },
            )
        }
    }
}
