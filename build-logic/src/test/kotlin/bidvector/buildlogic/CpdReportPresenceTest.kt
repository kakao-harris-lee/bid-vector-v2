package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-4 — 관찰이든 실패 모드든 "실행 안 함"으로 조용히 퇴화하지 않게 리포트 산출 자체를
 * 단언하는 순수 함수. 실제 CPD 실행 없이 파일 존재·크기·「잴 소스가 있었는가」만으로
 * 판정한다.
 */
class CpdReportPresenceTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `소스가 있고 리포트가 존재하고 비어 있지 않으면 위반이 없다`() {
        val report = File(dir, "cpd.xml").apply { writeText("<pmd-cpd/>") }
        assertNull(cpdReportPresenceViolation(report, hasExpectedSource = true))
    }

    @Test
    fun `소스가 있는데 리포트가 없으면 위반이다`() {
        val missing = File(dir, "missing.xml")
        val violation = cpdReportPresenceViolation(missing, hasExpectedSource = true)
        assertTrue(violation != null && violation.contains("존재하지 않는다"), "$violation")
    }

    @Test
    fun `소스가 있는데 리포트가 있어도 비어 있으면 위반이다`() {
        val empty = File(dir, "empty.xml").apply { writeText("") }
        val violation = cpdReportPresenceViolation(empty, hasExpectedSource = true)
        assertTrue(violation != null && violation.contains("비어"), "$violation")
    }

    @Test
    fun `소스가 원래 없으면 리포트가 없어도 위반이 아니다`() {
        val missing = File(dir, "missing.xml")
        assertNull(cpdReportPresenceViolation(missing, hasExpectedSource = false))
    }

    @Test
    fun `소스가 원래 없으면 리포트가 비어 있어도 위반이 아니다`() {
        val empty = File(dir, "empty.xml").apply { writeText("") }
        assertNull(cpdReportPresenceViolation(empty, hasExpectedSource = false))
    }
}
