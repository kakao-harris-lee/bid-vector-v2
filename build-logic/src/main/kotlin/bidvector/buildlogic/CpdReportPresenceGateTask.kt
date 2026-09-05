package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * D-4 — CPD 관찰 모드(`ignoreFailures=true`)는 위반을 실패시키지 않되, 리포트 **산출 자체**는
 * `check`가 강제한다. 관찰 모드가 "실행 안 함"으로 조용히 퇴화하는 것(task 삭제·`onlyIf false`·
 * `--tests` 배제와 같은 계열의 침묵)을 막는 자리다.
 */
internal fun cpdReportPresenceViolation(report: File): String? =
    when {
        !report.exists() -> "PMD CPD 리포트가 존재하지 않는다(D-4, OPEN-ADR-16 (a)): ${report.path}"
        report.length() == 0L -> "PMD CPD 리포트가 비어 있다(D-4, OPEN-ADR-16 (a)): ${report.path}"
        else -> null
    }

abstract class CpdReportPresenceGateTask : DefaultTask() {
    /** `cpdCheck`의 XML 리포트 — provider 로 연결되므로 이 task 가 `cpdCheck` 뒤에 자동으로 돈다. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cpdXmlReport: RegularFileProperty

    @TaskAction
    fun gate() {
        cpdReportPresenceViolation(cpdXmlReport.get().asFile)?.let { throw GradleException(it) }
    }
}
