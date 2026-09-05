package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
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
    /**
     * `cpdCheck`의 XML 리포트 경로. **`@InputFile`이 아니라 `@Internal`이다** — verifier r1
     * L-3: `@InputFile`은 task 실행 전에 Gradle 자체가 파일 존재를 검증해 "Input file does
     * not exist"로 죽으므로 [cpdReportPresenceViolation]의 「존재하지 않는다」 사유가 프로덕션
     * 배선에서는 닿지 못하는 죽은 가지였다(`-x cpdCheck`로 재현). 순서 보장은 `@InputFile`의
     * 암묵적 의존이 아니라 배선부의 명시적 `dependsOn(cpdCheck)`가 이미 진다.
     */
    @get:Internal
    abstract val cpdXmlReport: RegularFileProperty

    @TaskAction
    fun gate() {
        cpdReportPresenceViolation(cpdXmlReport.get().asFile)?.let { throw GradleException(it) }
    }
}
