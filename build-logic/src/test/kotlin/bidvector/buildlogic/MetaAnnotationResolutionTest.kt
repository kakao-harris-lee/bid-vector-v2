package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * vr r1 H-2 — 메타 애노테이션 해소를 **실제 class 파일로** 잰다.
 *
 * `GateRegistrationCensusTest` 는 해소기를 람다로 바꿔 끼워 등식 자체를 잰다. 그 자리에서는 「무엇이
 * 해소되는가」를 잴 수 없다 — 조회 자리가 가짜이기 때문이다. 여기서는 이 모듈이 **실제로 컴파일한**
 * test 출력을 조회 자리로 주고, 그 안에만 있는 합성 애노테이션([GateProbeTest])이 발견 어휘에 닿는지,
 * 그리고 그것만 단 클래스([MetaAnnotatedProbeTest])가 모집단에 드는지 본다.
 */
class MetaAnnotationResolutionTest {
    @Test
    fun `모듈 test 출력에만 있는 합성 애노테이션이 발견 어휘로 풀린다`() {
        val meta = ClasspathMetaAnnotations(listOf(testClassesRoot()))

        assertTrue(TEST_ANNOTATION in meta(PROBE_ANNOTATION), "${meta(PROBE_ANNOTATION)}")
    }

    @Test
    fun `합성 애노테이션만 단 test 클래스가 모집단에 든다`() {
        val census = censusOfTestOutput()

        assertTrue(PROBE_CLASS in census.population, "${census.population.size}")
    }

    /**
     * 조회 자리에서 모듈 test 출력을 빼면 그 클래스가 **조용히** 모집단에서 사라진다 — H-2 가 잡은
     * 방향이다. 이 대조가 없으면 위 단언이 다른 이유로도 참일 수 있다.
     */
    @Test
    fun `조회 자리에 모듈 test 출력이 없으면 그 클래스가 모집단에서 빠진다 — 음성 대조`() {
        val census = censusOfTestOutput(metaRoots = emptyList())

        assertTrue(PROBE_CLASS !in census.population, "${census.population}")
    }

    /** 이 모듈의 test 출력에는 발견 어휘를 직접 단 클래스도 많다 — 모집단이 공허하지 않다. */
    @Test
    fun `모집단이 비어 있지 않다`() {
        assertTrue(censusOfTestOutput().population.size > 1, "${censusOfTestOutput().population.size}")
    }

    private fun censusOfTestOutput(metaRoots: List<File> = listOf(testClassesRoot())): GateRegistrationCensus {
        val lookup = ClasspathMetaAnnotations(metaRoots)
        return GateRegistration.census(
            testClassFactsIn(listOf(testClassesRoot())),
            VOCABULARY,
            excludePatterns = emptySet(),
            metaAnnotations = lookup,
            superFacts = lookup::factsOf,
        )
    }

    private fun testClassesRoot(): File =
        File(
            MetaAnnotatedProbeTest::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).also {
            assertEquals(true, it.isDirectory, "컴파일된 test 출력을 찾지 못했다: $it")
        }

    private companion object {
        const val TEST_ANNOTATION = "org.junit.jupiter.api.Test"
        const val PROBE_ANNOTATION = "bidvector.buildlogic.GateProbeTest"
        const val PROBE_CLASS = "bidvector.buildlogic.MetaAnnotatedProbeTest"

        val VOCABULARY =
            TestDiscoveryVocabulary(
                annotations =
                    setOf(
                        TEST_ANNOTATION,
                        "org.junit.jupiter.api.TestFactory",
                        "org.junit.jupiter.api.TestTemplate",
                    ),
                conditionPackages = setOf("org.junit.jupiter.api.condition"),
            )
    }
}
