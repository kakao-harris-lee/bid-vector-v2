package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-4 — 관찰 모드(`ignoreFailures=true`)가 "실행 안 함"으로 조용히 퇴화하지 않게 리포트
 * 산출 자체를 단언하는 순수 함수. 실제 CPD 실행 없이 파일 존재·크기만으로 판정한다.
 */
class CpdReportPresenceTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `리포트가 존재하고 비어 있지 않으면 위반이 없다`() {
        val report = File(dir, "cpd.xml").apply { writeText("<pmd-cpd/>") }
        assertNull(cpdReportPresenceViolation(report))
    }

    @Test
    fun `리포트가 없으면 위반이다`() {
        val missing = File(dir, "missing.xml")
        val violation = cpdReportPresenceViolation(missing)
        assertTrue(violation != null && violation.contains("존재하지 않는다"), "$violation")
    }

    @Test
    fun `리포트가 있어도 비어 있으면 위반이다`() {
        val empty = File(dir, "empty.xml").apply { writeText("") }
        val violation = cpdReportPresenceViolation(empty)
        assertTrue(violation != null && violation.contains("비어"), "$violation")
    }
}
