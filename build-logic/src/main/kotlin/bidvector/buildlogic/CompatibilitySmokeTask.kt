package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * `v2-지침서.md` §5 가 1A 에 지운 실측 — `OPEN-OPS-07` 라이브러리 조사는 Boot **3.x** 전제로
 * 수행됐고, 채택 판정이 딛은 그 전제를 4.x 로 올리는 것이 이 task 다.
 *
 * 재는 것은 **해석**이고, 컴파일과 클래스 로드는 같은 configuration 을 쓰는 스모크 테스트가
 * 잰다. runtime 사용(스케줄 실행·migration·컨테이너 기동)은 범위 밖이다 — scope.md D-7.
 *
 * Boot BOM 이 어느 버전을 관리하는지가 `ADR 0004` §6 의 미확인 항목이므로 기대 버전을
 * 박지 않고 **해석된 버전을 리포트가 낸다**.
 */
abstract class CompatibilitySmokeTask : DefaultTask() {
    @get:Input
    abstract val expectedModules: SetProperty<String>

    @get:Input
    abstract val graphs: ListProperty<ResolvedComponentResult>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun smoke() {
        val resolved = resolveDependencies(graphs.get()).externalModules
        val expected = expectedModules.get().toSortedSet()
        val missing = expected.filterNot(resolved::containsKey)

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            expected.joinToString(separator = "\n", postfix = "\n") { module ->
                "$module\t${resolved[module] ?: "UNRESOLVED"}"
            },
        )
        expected.forEach { module -> logger.lifecycle("{} → {}", module, resolved[module] ?: "UNRESOLVED") }

        if (missing.isNotEmpty()) {
            throw GradleException("Boot BOM 아래에서 해석되지 않은 모듈: ${missing.joinToString()}")
        }
    }
}
