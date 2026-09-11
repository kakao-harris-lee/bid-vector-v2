package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeakPatternGateChecksTest {
    // ---- 정책 파일 파싱 ----

    @Test
    fun `빈 줄과 주석 줄은 패턴에서 걸러진다`() {
        val patterns = parseLeakPatterns(listOf("secret", "# comment", "", "   ", "token"))
        assertEquals(2, patterns.size)
    }

    // ---- (a) 패턴 매치·비매치 경계 ----

    private val patterns = parseLeakPatterns(listOf("secret", "token"))

    @Test
    fun `패턴 어휘를 담은 줄은 매치한다`() {
        val matches = leakMatchesInFile("a.md", listOf("no match here", "this has a secret value"), patterns)
        assertEquals(listOf("a.md:2"), matches)
    }

    @Test
    fun `패턴 어휘가 없는 줄은 매치하지 않는다`() {
        val matches = leakMatchesInFile("a.md", listOf("nothing sensitive", "plain text"), patterns)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `패턴은 대소문자를 가리지 않는다`() {
        val matches = leakMatchesInFile("a.md", listOf("SECRET in caps"), patterns)
        assertEquals(listOf("a.md:1"), matches)
    }

    // ---- (b) baseline diff 의 방향 — 이 test 의 핵심 ----

    @Test
    fun `baseline 에 있는 매치는 새 매치로 잡히지 않는다`() {
        val matches = setOf("a.md:1", "b.md:2")
        val baseline = setOf("a.md:1", "b.md:2")
        assertTrue(newLeakMatches(matches, baseline).isEmpty())
    }

    @Test
    fun `baseline 밖의 새 매치만 실패 대상으로 남는다`() {
        val matches = setOf("a.md:1", "b.md:2")
        val baseline = setOf("a.md:1")
        assertEquals(listOf("b.md:2"), newLeakMatches(matches, baseline))
    }

    @Test
    fun `baseline 에만 있고 지금 매치에는 없는 항목은 새 매치가 아니다`() {
        val matches = setOf("a.md:1")
        val baseline = setOf("a.md:1", "removed.md:9")
        assertTrue(newLeakMatches(matches, baseline).isEmpty())
    }

    // ---- (c) stale baseline — 현재 동작 고정(실패시키지 않는다) ----

    @Test
    fun `baseline 에 있었는데 지금은 안 잡히는 항목은 stale 로 잡힌다`() {
        val matches = setOf("a.md:1")
        val baseline = setOf("a.md:1", "removed.md:9")
        assertEquals(listOf("removed.md:9"), staleLeakBaselineEntries(matches, baseline))
    }

    @Test
    fun `stale 항목만 있을 때는 게이트 위반이 없다`() {
        val matches = setOf("a.md:1")
        val baseline = setOf("a.md:1", "removed.md:9")
        assertNull(leakGateViolation(newLeakMatches(matches, baseline)))
    }

    // ---- (d) 좌표(file:line) 판독 정확성 ----

    @Test
    fun `줄 번호는 1부터 시작한다`() {
        val matches = leakMatchesInFile("x/y.md", listOf("secret"), patterns)
        assertEquals(listOf("x/y.md:1"), matches)
    }

    @Test
    fun `여러 줄 중 매치한 줄만 정확한 번호로 잡힌다`() {
        val matches =
            leakMatchesInFile(
                "x/y.md",
                listOf("first line", "second has secret", "third", "fourth has token here"),
                patterns,
            )
        assertEquals(listOf("x/y.md:2", "x/y.md:4"), matches)
    }

    @Test
    fun `상대경로는 그대로 좌표 접두사로 실린다`() {
        val matches = leakMatchesInFile("nested/dir/file.md", listOf("token"), patterns)
        assertEquals(listOf("nested/dir/file.md:1"), matches)
    }

    // ---- 위반 메시지 ----

    @Test
    fun `새 매치가 없으면 위반이 없다`() {
        assertNull(leakGateViolation(emptyList()))
    }

    @Test
    fun `새 매치가 있으면 그 좌표를 담은 위반 메시지를 낸다`() {
        val violation = leakGateViolation(listOf("a.md:1", "b.md:2"))
        assertNotNull(violation)
        assertTrue(violation.contains("a.md:1"))
        assertTrue(violation.contains("b.md:2"))
    }
}
