package bidvector.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **래칫의 판정 근거가 어디서 와서 지문에 들어가는지**를 재는 유일한 실행 증거 — 축이 둘이다.
 * 「도구가 실제로 쓴 자리에서 오는가」(마지막 test)와 「그 내용이 바뀌면 다시 도는가」(앞의 셋).
 * 둘 중 하나가 깨지면 게이트는 **낡은 근거로 조용히 통과**한다.
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

    /**
     * `excludePitest` 는 **pitest 자신의 속성에서 파생한** 배선을 쓰는 fixture 에만 쓴다 — 그
     * 배선은 pitest task 를 게이트의 생산자로 묶으므로(그것이 처방의 요지다) 빼지 않으면 PIT 가
     * 실제로 뜬다. 여기서 재는 것은 분석이 아니라 **게이트가 어느 파일을 읽는가**다.
     */
    private fun run(
        expectFailure: Boolean,
        excludePitest: Boolean = false,
    ) = GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(
            buildList {
                add(":mutationReportGate")
                if (excludePitest) addAll(listOf("-x", "pitest"))
                add("--stacktrace")
            },
        ).let { if (expectFailure) it.buildAndFail() else it.build() }

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

    /**
     * **pitest 가 리포트를 쓰는 자리를 옮긴** fixture. 실제 plugin 을 적용해 `reportDir` 을
     * 옮기고, 옛 자리에는 **좋은 낡은 리포트**를 남긴다 — 로컬 증분 빌드의 모양이다.
     *
     * 게이트의 배선은 **프로덕션과 같은 한 줄**(`pitestMutationXmlReport()`)이다. 그 자리를
     * 고정 경로로 바꿔치우면 게이트는 옛 자리를 읽고 아래 둘이 RED 가 된다.
     */
    private fun writeMovedReportDirFixture(timestampedReportsOverride: String = "") {
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
        File(projectDir, "classes/a").apply { mkdirs() }
        File(projectDir, "classes/a/B.class").writeText("")
        val classpath = pluginClasspath().joinToString(", ") { "\"${it.invariantSeparatorsPath}\"" }
        File(projectDir, "build.gradle.kts").writeText(
            """
            import bidvector.buildlogic.pitestMutationXmlReport

            buildscript { dependencies { classpath(files($classpath)) } }

            apply(plugin = "java")
            apply(plugin = "info.solidsoft.pitest")

            // 도구가 쓰는 자리를 옮긴다. 옛 자리('$STALE_REPORT_DIR')의 리포트는 그대로 남는다.
            configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
                reportDir.set(layout.projectDirectory.dir("$MOVED_REPORT_DIR"))
            }

            $timestampedReportsOverride

            tasks.register<bidvector.buildlogic.MutationReportGateTask>("mutationReportGate") {
                moduleName.set("fixture")
                policyFile.set(layout.projectDirectory.file("policy.properties"))
                mutationXmlReport.set(pitestMutationXmlReport())
                classDirectories.from(layout.projectDirectory.dir("classes"))
                report.set(layout.buildDirectory.file("violations.txt"))
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeReport(
        dir: String,
        detected: Int,
    ) = File(projectDir, "$dir/mutations.xml")
        .apply { parentFile.mkdirs() }
        .writeText(mutationsXml(detected))

    @Test
    fun `pitest 가 리포트를 쓰는 자리를 옮기면 게이트도 그 자리를 읽는다 — 낡은 리포트로 통과하지 않는다`() {
        // 지역 값 하나가 「도구가 쓰는 자리」와 「게이트가 읽는 자리」를 묶고 있던 판에서는, 한쪽만
        // 옮기고 옛 리포트를 남기면 로컬 증분 빌드가 **좋은 낡은 수**로 통과했다(verifier B5).
        writeMovedReportDirFixture()
        writeReport(STALE_REPORT_DIR, detected = TOTAL_MUTATIONS)
        writeReport(MOVED_REPORT_DIR, detected = 0)

        val result = run(expectFailure = true, excludePitest = true)

        assertEquals(TaskOutcome.FAILED, result.task(":mutationReportGate")?.outcome, result.output)
        assertTrue(
            result.output.contains("mutation 점수"),
            "옮긴 자리의 리포트를 읽지 않았다(부재나 다른 축으로 실패했다):\n${result.output}",
        )
    }

    @Test
    fun `옮긴 자리의 리포트가 좋으면 게이트는 통과한다 — 자리를 따라간다는 양방향 대조`() {
        // 위 test 의 통제군. 「옮긴 자리를 읽는다」가 실패 방향으로만 성립하면 그것은 자리를
        // 따라가는 것이 아니라 그냥 붉은 것이다.
        writeMovedReportDirFixture()
        writeReport(STALE_REPORT_DIR, detected = 0)
        writeReport(MOVED_REPORT_DIR, detected = TOTAL_MUTATIONS)

        assertEquals(
            TaskOutcome.SUCCESS,
            run(expectFailure = false, excludePitest = true).task(":mutationReportGate")?.outcome,
            "옮긴 자리의 좋은 리포트로 통과하지 않았다",
        )
    }

    @Test
    fun `타임스탬프 리포트를 켜면 구성 단계에서 거부한다 — extension 축`() {
        // 자리의 둘째 축(v r4 L-2). `timestampedReports=true` 면 pitest 가 시각 이름의 하위
        // 디렉터리에 리포트를 쓰는데 Gradle 은 출력 디렉터리의 낡은 파일을 지우지 않는다 —
        // 게이트는 좋은 낡은 리포트를 읽고 로컬에서 초록이 된다(verifier 재현). 그 자리는 파생으로
        // 따라갈 수 없으므로 **켜는 것 자체를 거부**한다.
        writeMovedReportDirFixture(timestampedReportsOverride = TIMESTAMPED_VIA_EXTENSION)
        writeReport(MOVED_REPORT_DIR, detected = TOTAL_MUTATIONS)

        val result = run(expectFailure = true, excludePitest = true)

        assertTrue(
            result.output.contains("timestampedReports"),
            "거부 사유가 그 플래그를 가리키지 않는다:\n${result.output}",
        )
        assertEquals(null, result.task(":mutationReportGate")?.outcome, "게이트가 돌아버렸다")
    }

    @Test
    fun `타임스탬프 리포트를 task 쪽에서 켜도 거부한다 — 읽는 값이 task 의 최종값이다`() {
        // extension 이 아니라 task 속성을 직접 켜는 축. 리포트를 쓰는 것은 task 이므로 게이트가
        // 보는 값도 task 의 것이어야 한다.
        writeMovedReportDirFixture(timestampedReportsOverride = TIMESTAMPED_VIA_TASK)
        writeReport(MOVED_REPORT_DIR, detected = TOTAL_MUTATIONS)

        val result = run(expectFailure = true, excludePitest = true)

        assertTrue(
            result.output.contains("timestampedReports"),
            "거부 사유가 그 플래그를 가리키지 않는다:\n${result.output}",
        )
        assertEquals(null, result.task(":mutationReportGate")?.outcome, "게이트가 돌아버렸다")
    }

    private companion object {
        /** fixture 의 변이 수. 래칫 `mutations.min` 과 같은 값이라 그 축은 늘 만족한다 — 재는 것은 점수 축이다. */
        const val TOTAL_MUTATIONS = 4

        /** pitest 가 **옮겨 간** 자리 — 게이트가 읽어야 하는 쪽. */
        const val MOVED_REPORT_DIR = "pitest-moved"

        /** 옛 자리 — 좋은 수를 담은 낡은 리포트가 남아 있는 쪽. */
        const val STALE_REPORT_DIR = "pitest-stale"

        /** 자리의 둘째 축을 켜는 fixture 한 줄 — extension 축. */
        const val TIMESTAMPED_VIA_EXTENSION =
            "configure<info.solidsoft.gradle.pitest.PitestPluginExtension> { timestampedReports.set(true) }"

        /** 같은 축, task 쪽. 리포트를 쓰는 것이 task 이므로 이쪽이 최종값이다. */
        const val TIMESTAMPED_VIA_TASK =
            "tasks.named<info.solidsoft.gradle.pitest.PitestTask>(\"pitest\") { timestampedReports.set(true) }"
    }
}
