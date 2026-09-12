package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(listOf("a.md:2"), matches.map { it.coordinate })
    }

    @Test
    fun `패턴 어휘가 없는 줄은 매치하지 않는다`() {
        val matches = leakMatchesInFile("a.md", listOf("nothing sensitive", "plain text"), patterns)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `패턴은 대소문자를 가리지 않는다`() {
        val matches = leakMatchesInFile("a.md", listOf("SECRET in caps"), patterns)
        assertEquals(listOf("a.md:1"), matches.map { it.coordinate })
    }

    // ---- (d) 좌표(file:line) 판독 정확성 ----

    @Test
    fun `줄 번호는 1부터 시작한다`() {
        val matches = leakMatchesInFile("x/y.md", listOf("secret"), patterns)
        assertEquals(listOf("x/y.md:1"), matches.map { it.coordinate })
    }

    @Test
    fun `여러 줄 중 매치한 줄만 정확한 번호로 잡힌다`() {
        val matches =
            leakMatchesInFile(
                "x/y.md",
                listOf("first line", "second has secret", "third", "fourth has token here"),
                patterns,
            )
        assertEquals(listOf("x/y.md:2", "x/y.md:4"), matches.map { it.coordinate })
    }

    @Test
    fun `상대경로는 그대로 좌표 접두사로 실린다`() {
        val matches = leakMatchesInFile("nested/dir/file.md", listOf("token"), patterns)
        assertEquals(listOf("nested/dir/file.md:1"), matches.map { it.coordinate })
    }

    // ---- 정규화 (D-LBC-1: trim + 내부 공백 접기까지, 소문자화·구두점 제거는 하지 않는다) ----

    @Test
    fun `정규화는 앞뒤 공백을 없앤다`() {
        assertEquals("has secret", normalizeLeakLineContent("  has secret  "))
    }

    @Test
    fun `정규화는 내부 공백을 하나로 접는다`() {
        assertEquals("has a secret", normalizeLeakLineContent("has  a   secret"))
        assertEquals("has a secret", normalizeLeakLineContent("has\ta\tsecret"))
    }

    @Test
    fun `정규화는 소문자화하지 않는다`() {
        assertEquals("Has Secret", normalizeLeakLineContent("Has Secret"))
    }

    @Test
    fun `정규화는 구두점을 제거하지 않는다`() {
        assertEquals("has a secret!!", normalizeLeakLineContent("has a secret!!"))
    }

    // ---- 키 산출 (D-LBC-1: 경로 + 정규화 줄 내용 해시) ----

    private fun LeakMatch.key(): String = leakBaselineKey(path, content)

    @Test
    fun `내부 공백만 다른 두 줄은 같은 키로 접힌다`() {
        val m1 = leakMatchesInFile("a.md", listOf("has  a   secret"), patterns).single()
        val m2 = leakMatchesInFile("a.md", listOf("has a secret"), patterns).single()
        assertEquals(m1.key(), m2.key())
    }

    @Test
    fun `같은 파일에서 위쪽에 줄이 삽입돼도 승인된 매치의 키는 그대로다`() {
        // S-3 재현 — 줄 번호(좌표)는 밀리지만 키는 줄 번호를 담지 않는다.
        val before = leakMatchesInFile("a.md", listOf("first line", "second has secret"), patterns).single()
        val after =
            leakMatchesInFile(
                "a.md",
                listOf("inserted new line", "first line", "second has secret"),
                patterns,
            ).single()
        assertEquals("a.md:2", before.coordinate)
        assertEquals("a.md:3", after.coordinate)
        assertEquals(before.key(), after.key())
    }

    @Test
    fun `같은 글자라도 다른 파일이면 다른 키가 된다`() {
        // S-4 재현 — 경로가 키에서 빠지면 한 파일의 승인이 모든 파일로 샌다.
        val inA = leakMatchesInFile("a.md", listOf("has a secret value"), patterns).single()
        val inB = leakMatchesInFile("b.md", listOf("has a secret value"), patterns).single()
        assertFalse(inA.key() == inB.key())

        val baseline = setOf(inA.key())
        val newKeys = newLeakBaselineKeys(setOf(inA.key(), inB.key()), baseline)
        assertEquals(listOf(inB.key()), newKeys)
    }

    @Test
    fun `같은 자리 글자가 바뀌면 키도 바뀐다`() {
        // S-6 재현 — 승인은 그 문장에 대한 판단이라 문장이 바뀌면 다시 본다.
        val before = leakMatchesInFile("a.md", listOf("has a secret value"), patterns).single()
        val after = leakMatchesInFile("a.md", listOf("has a secret VALUE2"), patterns).single()
        assertFalse(before.key() == after.key())
    }

    // ---- (b) baseline diff 의 방향 — 키 기준 ----

    @Test
    fun `baseline 에 있는 키는 새 키로 잡히지 않는다`() {
        val matchKeys = setOf("a.md#1", "b.md#2")
        val baseline = setOf("a.md#1", "b.md#2")
        assertTrue(newLeakBaselineKeys(matchKeys, baseline).isEmpty())
    }

    @Test
    fun `baseline 밖의 새 키만 실패 대상으로 남는다`() {
        val matchKeys = setOf("a.md#1", "b.md#2")
        val baseline = setOf("a.md#1")
        assertEquals(listOf("b.md#2"), newLeakBaselineKeys(matchKeys, baseline))
    }

    @Test
    fun `baseline 에만 있고 지금 매치에는 없는 키는 새 키가 아니다`() {
        val matchKeys = setOf("a.md#1")
        val baseline = setOf("a.md#1", "removed.md#9")
        assertTrue(newLeakBaselineKeys(matchKeys, baseline).isEmpty())
    }

    // ---- (c) stale baseline — 현재 동작 고정(실패시키지 않는다) ----

    @Test
    fun `baseline 에 있었는데 지금은 안 잡히는 키는 stale 로 잡힌다`() {
        val matchKeys = setOf("a.md#1")
        val baseline = setOf("a.md#1", "removed.md#9")
        assertEquals(listOf("removed.md#9"), staleLeakBaselineEntries(matchKeys, baseline))
    }

    // ---- 옛 형식(경로:줄번호) 검출 — D-LBC-2 ----

    @Test
    fun `옛 형식 경로 콜론 숫자 항목은 legacy 로 잡힌다`() {
        assertTrue(isLegacyLeakBaselineKey("a.md:12"))
    }

    @Test
    fun `새 형식 키는 legacy 로 오판되지 않는다`() {
        assertFalse(isLegacyLeakBaselineKey("a.md#${"0".repeat(64)}"))
    }

    @Test
    fun `혼합 baseline 에서 옛 형식 항목만 위반 사유에 잡힌다`() {
        val baseline = setOf("a.md:12", "b.md#${"a".repeat(64)}", "c.md:3")
        val violation = leakBaselineLegacyFormatViolation(baseline)
        assertNotNull(violation)
        assertTrue(violation.contains("a.md:12"))
        assertTrue(violation.contains("c.md:3"))
        assertFalse(violation.contains("b.md#${"a".repeat(64)}"))
    }

    @Test
    fun `옛 형식 항목이 없으면 legacy 위반이 없다`() {
        assertNull(leakBaselineLegacyFormatViolation(setOf("b.md#${"a".repeat(64)}")))
    }

    @Test
    fun `옛 형식 항목이 있으면 사유를 담은 legacy 위반을 낸다`() {
        val violation = leakBaselineLegacyFormatViolation(setOf("a.md:12"))
        assertNotNull(violation)
        assertTrue(violation.contains("a.md:12"))
    }

    // ---- 위반 메시지 — 저장 가능한 키와 현재 좌표를 함께 낸다(D-LBC-3) ----

    @Test
    fun `새 키가 없으면 위반이 없다`() {
        assertNull(leakGateViolation(emptyList(), emptyList()))
    }

    @Test
    fun `새 키가 있으면 키와 현재 좌표를 함께 낸다`() {
        val match = leakMatchesInFile("a.md", listOf("has a secret"), patterns).single()
        val violation = leakGateViolation(listOf(match.key()), listOf(match))
        assertNotNull(violation)
        assertTrue(violation.contains(match.key()))
        assertTrue(violation.contains("a.md:1"))
    }

    // ---- 보고서 — 매치 줄 수와 접힌 키 수를 따로 싣는다(D-LBC-3) ----

    @Test
    fun `같은 파일 안에서 같은 글자가 두 줄에 나타나면 매치는 둘 키는 하나로 보고된다`() {
        val matches = leakMatchesInFile("a.md", listOf("has a secret", "has a secret"), patterns)
        assertEquals(2, matches.size)
        val report =
            leakGateReportText(
                patternCount = patterns.size,
                baselineCount = 0,
                matches = matches,
                newKeys = matches.map { it.key() }.distinct(),
                staleBaseline = emptyList(),
            )
        assertTrue(report.contains("matches=2"))
        assertTrue(report.contains("keys=1"))
    }

    @Test
    fun `보고서의 new 줄은 키와 좌표를 함께 싣는다`() {
        val match = leakMatchesInFile("a.md", listOf("has a secret"), patterns).single()
        val report =
            leakGateReportText(
                patternCount = patterns.size,
                baselineCount = 0,
                matches = listOf(match),
                newKeys = listOf(match.key()),
                staleBaseline = emptyList(),
            )
        assertTrue(report.contains("new: ${match.key()}"))
        assertTrue(report.contains("a.md:1"))
    }

    @Test
    fun `보고서의 stale 줄은 저장된 키 그대로 싣는다`() {
        val report =
            leakGateReportText(
                patternCount = patterns.size,
                baselineCount = 1,
                matches = emptyList(),
                newKeys = emptyList(),
                staleBaseline = listOf("removed.md#${"9".repeat(64)}"),
            )
        assertTrue(report.contains("stale: removed.md#${"9".repeat(64)}"))
    }
}
