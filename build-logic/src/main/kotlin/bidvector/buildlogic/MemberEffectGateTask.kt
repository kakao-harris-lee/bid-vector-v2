package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * 허용 클래스 **안**의 효과 멤버를 도출하고, 그 후보가 전부 분류돼 있는지 잰다.
 *
 * 세 가지를 한꺼번에 막는다. ① 도출 결과가 커밋본과 다르면 실패 — JDK 나 허용 목록이 바뀌면
 * 조용히 지나가지 않는다. ② 분류되지 않은 후보가 있으면 실패 — 「생각해 내지 못한 멤버」가
 * 열리지 않는다. ③ 후보에 없는 분류가 있으면 실패 — 낡은 금지가 남아 있으면 그것도 거짓말이다.
 *
 * 도출은 **이 태스크를 돌리는 JVM 의 JDK 바이트코드**를 읽으므로 툴체인과 같은 판이어야 한다.
 * 다르면 게이트가 다른 JDK 의 사실을 재게 되므로 먼저 막는다.
 */
abstract class MemberEffectGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    /** 커밋된 도출 결과. 재생성본과 축어로 대조한다. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val derivedFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val classificationFile: RegularFileProperty

    @get:Input
    abstract val expectedJdk: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val runningJdk = System.getProperty("java.specification.version")
        if (runningJdk != expectedJdk.get()) {
            throw GradleException(
                "효과 도출은 툴체인과 같은 JDK 에서 돌아야 한다 — 이 JVM 은 $runningJdk, " +
                    "툴체인은 ${expectedJdk.get()}(gradle.properties `bidvector.jvmToolchain`). " +
                    "`org.gradle.java.home` 을 맞추거나 같은 JDK 로 Gradle 을 띄워라.",
            )
        }
        val policy = readPolicy(policyFile.get().asFile)
        val effects =
            deriveMemberEffects(
                policy.requireList("class.allowed.api") + policy.requireList("class.allowed.runtime"),
                EffectSurface(
                    policy.requireList("effect.surface.packages"),
                    policy.requireList("effect.surface.classes").toSet(),
                ),
            )
        val rendered = renderMemberEffects(effects, runningJdk)
        val freshFile = report.get().asFile.apply { parentFile.mkdirs() }
        freshFile.writeText(rendered)
        verifyDerivation(rendered, freshFile.path)
        verifyClassification(effects.map { it.key }.toSet())
    }

    private fun verifyDerivation(
        rendered: String,
        freshPath: String,
    ) {
        val committed = derivedFile.get().asFile
        if (rendered != committed.readText()) {
            throw GradleException(
                "도출 결과가 커밋본과 다르다 — JDK 나 허용 목록이 바뀌었다.\n" +
                    "  커밋본: ${committed.path}\n  재생성: $freshPath\n" +
                    "재생성본을 커밋본에 덮고, 새 후보를 ${classificationFile.get().asFile.name} 에 분류하라.",
            )
        }
    }

    private fun verifyClassification(candidates: Set<String>) {
        val classification = MemberEffectClassification.parse(readPolicy(classificationFile.get().asFile))
        val mismatches = classification.mismatches(candidates)
        if (mismatches.isNotEmpty()) {
            throw GradleException(
                mismatches.joinToString(
                    prefix = "효과 후보의 분류가 맞지 않다 (${classificationFile.get().asFile.path}):\n  ",
                    separator = "\n  ",
                ),
            )
        }
        logger.lifecycle(
            "효과 멤버 분류: forbidden ${classification.forbidden.size} · reviewed ${classification.reviewed.size}",
        )
    }
}
