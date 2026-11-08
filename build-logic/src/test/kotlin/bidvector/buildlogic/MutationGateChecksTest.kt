package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * mutation 래칫의 판정 전부 — Gradle·pitest 없이 값으로 선다.
 *
 * 세 축을 함께 재는 이유는 축 하나만으로는 「대상을 줄여 점수를 유지하는」 경로가 열리기
 * 때문이다(설계 검토 (2) 2·3·4). 점수만 보면 `targetClasses` 를 좁히는 것이 통과이고, 변이
 * 수만 보면 테스트를 지우는 것이 통과다.
 */
class MutationGateChecksTest {
    @TempDir
    lateinit var dir: File

    private val ratchet = MutationRatchet(minScorePercent = 77, minMutations = 100, minMutatedClasses = 18)

    private fun reportFile(
        total: Int,
        detected: Int,
        classes: Int,
    ): File {
        val rows =
            (0 until total).map { index ->
                val isDetected = index < detected
                val owner = "bidvector.m.C${index % classes}"
                "<mutation detected='$isDetected' status='${if (isDetected) "KILLED" else "SURVIVED"}'>" +
                    "<mutatedClass>$owner</mutatedClass></mutation>"
            }
        return File(dir, "mutations-$total-$detected-$classes.xml").apply {
            writeText(rows.joinToString("\n", "<mutations>\n", "\n</mutations>\n"))
        }
    }

    private fun compiled(classes: Int): Set<String> = (0 until classes).map { "bidvector.m.C$it" }.toSet()

    @Test
    fun `세 축을 모두 만족하면 위반이 없다`() {
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 109, detected = 84, classes = 18),
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `점수가 임계 밑이면 위반이다`() {
        // 108/140 = 77.1% 는 통과, 107/140 = 76.4% 는 실패 — 정수 내림이 아니라 정확한
        // 정수 비교(detected*100 >= min*total)로 판정하므로 1점 미만의 하락도 잡는다.
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 140, detected = 107, classes = 18),
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertTrue(violations.any { it.contains("점수") }, "$violations")
    }

    @Test
    fun `점수 임계 바로 위는 통과한다`() {
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 140, detected = 108, classes = 18),
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `변이 수가 임계 밑이면 위반이다 — 대상을 좁히는 우회를 잡는다`() {
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 99, detected = 99, classes = 18),
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertTrue(violations.any { it.contains("변이 수") }, "$violations")
    }

    @Test
    fun `변이 클래스 수가 임계 밑이면 위반이다 — 클래스를 빼는 우회를 잡는다`() {
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 109, detected = 109, classes = 17),
                ratchet = ratchet,
                compiledClasses = compiled(17),
            )
        assertTrue(violations.any { it.contains("클래스 수") }, "$violations")
    }

    @Test
    fun `리포트가 그 모듈 산출물 밖의 클래스를 담으면 위반이다`() {
        // 남의 모듈 class 디렉터리를 `mutableCodePaths` 로 끌어와 쉬운 변이로 점수를 올리는 경로.
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 109, detected = 84, classes = 18),
                ratchet = ratchet,
                compiledClasses = compiled(18) - "bidvector.m.C17",
            )
        assertTrue(violations.any { it.contains("산출물 밖") }, "$violations")
    }

    @Test
    fun `리포트가 없으면 위반이다`() {
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = File(dir, "absent.xml"),
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertTrue(violations.any { it.contains("존재하지 않는다") }, "$violations")
    }

    @Test
    fun `리포트가 비어 있으면 위반이다`() {
        val empty = File(dir, "empty.xml").apply { writeText("") }
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = empty,
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertTrue(violations.any { it.contains("비어") }, "$violations")
    }

    @Test
    fun `컴파일된 main 산출물이 비어 있으면 위반이다`() {
        // 대상 모듈의 main 출력이 없는 상태는 「잴 것이 없다」가 아니라 배선이 깨진 것이다 —
        // 그 상태를 통과로 두면 산출물을 치우는 것이 게이트를 끄는 길이 된다.
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = reportFile(total = 109, detected = 84, classes = 18),
                ratchet = ratchet,
                compiledClasses = emptySet(),
            )
        assertTrue(violations.any { it.contains("산출물") }, "$violations")
    }

    @Test
    fun `변이가 0 이면 점수를 100 으로 접지 않고 위반으로 낸다`() {
        // `failWhenNoMutations=false` + 대상 0 의 경로. 0/0 을 「전부 탐지」로 읽으면 통과한다.
        val violations =
            mutationGateViolations(
                moduleName = "qualification",
                report = File(dir, "none.xml").apply { writeText("<mutations/>") },
                ratchet = ratchet,
                compiledClasses = compiled(18),
            )
        assertTrue(violations.any { it.contains("변이 수") }, "$violations")
    }

    @Test
    fun `정책 모듈 집합과 등록 집합이 같고 전부 도메인이면 위반이 없다`() {
        assertEquals(
            emptyList(),
            mutationModuleCoverageViolations(
                expected = setOf("qualification"),
                registered = setOf("qualification"),
                domainModules = DOMAIN,
            ),
        )
    }

    @Test
    fun `정책에 있는 모듈이 convention 을 적용하지 않으면 위반이다`() {
        val violations =
            mutationModuleCoverageViolations(
                expected = setOf("qualification", "settlement"),
                registered = setOf("qualification"),
                domainModules = DOMAIN,
            )
        assertTrue(violations.any { it.contains("적용하지 않았다") && it.contains("settlement") }, "$violations")
    }

    @Test
    fun `정책 밖 모듈이 convention 을 적용하면 위반이다`() {
        val violations =
            mutationModuleCoverageViolations(
                expected = setOf("qualification"),
                registered = setOf("qualification", "app"),
                domainModules = DOMAIN,
            )
        assertTrue(violations.any { it.contains("정책 밖 모듈") && it.contains("app") }, "$violations")
    }

    @Test
    fun `정책이 도메인 밖 모듈을 대상으로 들면 한 걸음에 그 사유로 위반이다`() {
        // 이 축이 없으면 「convention 을 적용하지 않았다」로 붉고, 그 문면을 따라 적용하면 구성
        // 단계에서 다른 예외가 난다 — 두 걸음이라 읽는 사람이 엉뚱한 고침을 한다.
        //
        // **줄이 하나여야 한다.** 도메인 밖 모듈은 convention 을 적용할 수 없으므로(구성 단계
        // 가드가 던진다) 「적용하지 않았다」 축에 함께 세우면 같은 오기가 두 줄로 나오고, 먼저
        // 읽히는 줄이 엉뚱한 고침(적용해 보기)을 유도한다 — 첫 실측에서 실제로 그렇게 나왔다.
        val violations =
            mutationModuleCoverageViolations(
                expected = setOf("qualification", "workflow"),
                registered = setOf("qualification"),
                domainModules = DOMAIN,
            )
        assertEquals(1, violations.size, "줄이 하나가 아니다: $violations")
        assertTrue(violations.single().contains("순수 도메인 밖"), "$violations")
        assertTrue(violations.single().contains("workflow"), "$violations")
    }

    private companion object {
        val DOMAIN = setOf("shared-kernel", "qualification", "settlement", "strategy")
    }
}
