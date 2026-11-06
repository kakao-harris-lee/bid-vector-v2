package bidvector.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **래칫의 판정 근거가 task 지문 안에 있는지**를 재는 유일한 실행 증거.
 *
 * 래칫이 존재하는 이유가 「test 가 약해지는 것」인데, 판정 근거(`mutations.xml`)가 선언 입력
 * 밖에 있으면 바로 그 경우에 게이트가 UP-TO-DATE 로 건너뛰고 exit 0 이 된다 — main 산출물과
 * 정책은 그대로이고 리포트만 나빠지기 때문이다. 앞 판에서 실제로 재현됐다(리포트 15/73 ·
 * `UP-TO-DATE` · BUILD SUCCESSFUL).
 *
 * 그래서 이 test 는 **리포트만 바꾼다** — 정책도 class 산출물도 손대지 않는다. 그것이
 * 양성 대조 일곱이 지나지 않았던 경로다(그 일곱은 전부 선언된 입력을 바꿨다).
 *
 * fixture 는 리포트를 **한 줄로** 배선한다(`mutationXmlReport` 하나). 지문을 별도 property 로
 * 두었던 판에서는 이 fixture 가 그 둘을 스스로 묶어, convention 에서 지문 배선 한 줄이 사라져도
 * 세 test 가 초록이었다 — 재는 자리가 프로덕션 배선과 달랐다. 지금은 선언이 하나라 fixture 의
 * 배선 모양이 프로덕션과 같고, 그 선언에서 지문 annotation 을 떼면 아래 둘이 바로 RED 다.
 *
 * pitest 를 띄우지 않는다. 재는 것은 변이 분석이 아니라 **Gradle 의 up-to-date 판정**이고,
 * 그 판정에 필요한 것은 리포트 파일 하나와 class 파일의 **이름**뿐이다(게이트는 class 내용을
 * 읽지 않는다 — 위치가 이름이다).
 */
class MutationReportGateUpToDateTest {
    @TempDir
    lateinit var projectDir: File

    private fun mutationsXml(detected: Int): String =
        (0 until TOTAL_MUTATIONS)
            .joinToString(separator = "\n", prefix = "<mutations>\n", postfix = "\n</mutations>\n") { index ->
                "<mutation detected='${index < detected}' status='${if (index < detected) "KILLED" else "SURVIVED"}'>" +
                    "<mutatedClass>a.B</mutatedClass></mutation>"
            }

    /**
     * `withPluginClasspath()` 는 **plugin 해석 경로**만 채운다 — fixture 의 build 스크립트가 이
     * 모듈의 task 클래스를 **이름으로 부르려면** 그 스크립트 자신의 컴파일 classpath 에 있어야
     * 한다. 그 경로는 `pluginUnderTestMetadata` 가 test runtime 자원으로 적어 둔 것을 읽어 쓴다
     * (경로를 손으로 적지 않는다 — 적으면 그 자리가 빌드 배선과 어긋난다).
     */
    private fun pluginClasspath(): List<File> =
        javaClass.classLoader
            .getResourceAsStream("plugin-under-test-metadata.properties")
            .let { requireNotNull(it) { "plugin-under-test-metadata.properties 가 test classpath 에 없다" } }
            .use { stream ->
                java.util
                    .Properties()
                    .apply { load(stream) }
                    .getProperty("implementation-classpath")
                    .split(File.pathSeparator)
                    .map(::File)
            }

    private fun writeFixture() {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        File(projectDir, "policy.properties").writeText(
            """
            policy.version=1
            mutation.modules=fixture
            threads=1
            output.formats=XML
            ratchet.fixture.score.min-percent=50
            ratchet.fixture.mutations.min=$TOTAL_MUTATIONS
            ratchet.fixture.classes.min=1
            """.trimIndent() + "\n",
        )
        // class 파일은 **이름만** 쓰인다 — 내용을 읽지 않으므로 빈 파일로 둔다.
        File(projectDir, "classes/a").apply { mkdirs() }
        File(projectDir, "classes/a/B.class").writeText("")
        val classpath = pluginClasspath().joinToString(", ") { "\"${it.invariantSeparatorsPath}\"" }
        File(projectDir, "build.gradle.kts").writeText(
            """
            buildscript { dependencies { classpath(files($classpath)) } }

            tasks.register<bidvector.buildlogic.MutationReportGateTask>("mutationReportGate") {
                moduleName.set("fixture")
                policyFile.set(layout.projectDirectory.file("policy.properties"))
                mutationXmlReport.set(layout.projectDirectory.file("mutations.xml"))
                classDirectories.from(layout.projectDirectory.dir("classes"))
                report.set(layout.buildDirectory.file("violations.txt"))
            }
            """.trimIndent() + "\n",
        )
    }

    private fun run(expectFailure: Boolean) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(":mutationReportGate", "--stacktrace")
            .let { if (expectFailure) it.buildAndFail() else it.build() }

    @Test
    fun `리포트만 나빠져도 게이트가 다시 돌아 실패한다 — UP-TO-DATE 로 건너뛰지 않는다`() {
        writeFixture()
        val reportFile = File(projectDir, "mutations.xml")

        reportFile.writeText(mutationsXml(detected = TOTAL_MUTATIONS))
        assertEquals(
            TaskOutcome.SUCCESS,
            run(expectFailure = false).task(":mutationReportGate")?.outcome,
            "첫 실행은 초록이어야 한다",
        )

        // **리포트만** 바꾼다 — 정책·class 산출물은 그대로다(test 가 약해진 상황의 모형).
        reportFile.writeText(mutationsXml(detected = 0))
        val second = run(expectFailure = true)

        assertEquals(
            TaskOutcome.FAILED,
            second.task(":mutationReportGate")?.outcome,
            "리포트가 나빠졌는데 게이트가 다시 돌지 않았다:\n${second.output}",
        )
        assertTrue(
            second.output.contains("mutation 점수"),
            "실패 사유가 점수 축이 아니다:\n${second.output}",
        )
    }

    @Test
    fun `리포트가 그대로면 두 번째 실행은 UP-TO-DATE 다 — 지문이 상수로 무효화되지 않는다`() {
        // 음성 대조. `upToDateWhen { false }` 처럼 **항상** 다시 도는 처방과 「리포트를 지문에
        // 넣는」 처방을 가른다 — 앞의 것으로는 이 단언이 깨진다. 래칫은 매 빌드 재실행이 아니라
        // 「근거가 바뀌면 재실행」이어야 한다.
        writeFixture()
        File(projectDir, "mutations.xml").writeText(mutationsXml(detected = TOTAL_MUTATIONS))

        run(expectFailure = false)
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            run(expectFailure = false).task(":mutationReportGate")?.outcome,
            "입력이 그대로인데 다시 돌았다",
        )
    }

    @Test
    fun `리포트를 지워도 게이트가 다시 돌아 부재를 사유로 실패한다`() {
        // 설계 검토 우회 6(「pitest 없이 낡은 리포트로 통과」)의 초록 상태 판. 부재가 지문의
        // 변화로 잡혀야 하고, 「존재하지 않는다」 가지는 살아 있어야 한다 — 그래서 리포트를
        // 존재 검증이 붙는 단일 `@InputFile` 로 선언하지 않는다.
        writeFixture()
        val reportFile = File(projectDir, "mutations.xml")
        reportFile.writeText(mutationsXml(detected = TOTAL_MUTATIONS))

        run(expectFailure = false)
        reportFile.delete()
        val second = run(expectFailure = true)

        assertEquals(TaskOutcome.FAILED, second.task(":mutationReportGate")?.outcome, second.output)
        assertTrue(
            second.output.contains("존재하지 않는다"),
            "부재가 그 사유로 잡히지 않았다:\n${second.output}",
        )
    }

    private companion object {
        /** fixture 의 변이 수. 래칫 `mutations.min` 과 같은 값이라 그 축은 늘 만족한다 — 재는 것은 점수 축이다. */
        const val TOTAL_MUTATIONS = 4
    }
}
