package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * D-4 — CPD 관찰(`ignoreFailures=true`)이든 실패 모드든 위반 여부와 무관하게 리포트 **산출
 * 자체**는 `check`가 강제한다. 관찰이 "실행 안 함"으로 조용히 퇴화하는 것(task 삭제·
 * `onlyIf false`·`--tests`/`-x` 배제와 같은 계열의 침묵)을 막는 자리다.
 *
 * **`hasExpectedSource`가 거짓이면 리포트 부재가 위반이 아니다** — main/test 축 분리 뒤
 * 실측: `adapters` 모듈은 test source set 이 비어 있어(`.kt` 0개)
 * `cpdCheckObserved` 가 `NO-SOURCE` 로 건너뛰고 리포트를 내지 않는다. 이것은 정책 회피가
 * 아니라 그 축에 잴 것이 없다는 사실이다 — 소스가 실제로 있는데 리포트가 없는 경우만
 * (`-x`·`onlyIf`로 조용히 뺀 경우 포함) 위반으로 잡는다.
 */
internal fun cpdReportPresenceViolation(
    report: File,
    hasExpectedSource: Boolean,
    newestSourceModifiedAt: Long = 0L,
): String? =
    when {
        !hasExpectedSource -> {
            null
        }

        !report.exists() -> {
            "PMD CPD 리포트가 존재하지 않는다(D-4, OPEN-ADR-16 (a)): ${report.path}"
        }

        report.length() == 0L -> {
            "PMD CPD 리포트가 비어 있다(D-4, OPEN-ADR-16 (a)): ${report.path}"
        }

        report.lastModified() < newestSourceModifiedAt -> {
            "PMD CPD 리포트가 잴 소스보다 낡았다 — 낡은 리포트가 '실행 안 함'을 가린다" +
                "(OPEN-CPD-GATE-RENAME-BYPASS): ${report.path}"
        }

        else -> {
            null
        }
    }

abstract class CpdReportPresenceGateTask : DefaultTask() {
    /**
     * `cpdCheck`(또는 `cpdCheckObserved`)의 XML 리포트 경로. **`@InputFile`이 아니라
     * `@Internal`이다** — `@InputFile`은 task 실행 전에 Gradle 자체가 파일
     * 존재를 검증해 "Input file does not exist"로 죽으므로 [cpdReportPresenceViolation]의
     * 「존재하지 않는다」 사유가 프로덕션 배선에서는 닿지 못하는 죽은 가지였다(`-x cpdCheck`로
     * 재현). 순서 보장은 `@InputFile`의 암묵적 의존이 아니라 배선부의 명시적
     * `dependsOn(cpdCheck)`가 이미 진다.
     *
     * **이 task 는 `@OutputFile` 이 없어 매번 실행된다** — 그래서 이 `@Internal` 선택이
     * UP-TO-DATE 거짓 초록을 만들지 않는다(리뷰 LOW-3). Gradle 은 입력의 mtime 을 정규화하므로
     * mtime 술어를 선언 입력으로 표현할 길이 없고, **출력을 하나 더하는 순간** 이 task 가
     * up-to-date 판정 대상이 되어 그 술어가 조용히 죽는다. 출력을 더하려는 다음 사람은 먼저 이
     * 문단을 읽어야 한다.
     */
    @get:Internal
    abstract val cpdXmlReport: RegularFileProperty

    /**
     * 해당 CPD task 에 실제로 넘긴 것과 **같은** source 집합(`setSource(...)`에 넘긴
     * source set 들). 디스크에서 다시 읽으므로 `-x`·`onlyIf`로 task 실행을 조용히 빼도 이
     * 값은 실제 파일 유무를 그대로 반영한다 — 「소스가 있는데 리포트가 없다」와 「잴 소스가
     * 원래 없다」를 가르는 유일한 근거다.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:IgnoreEmptyDirectories
    abstract val expectedSource: ConfigurableFileCollection

    /**
     * **낡은 리포트 축**(`OPEN-CPD-GATE-RENAME-BYPASS` 둘째 사각). 앞 판은 파일 존재·크기만 보아
     * `check -x cpdCheck` 가 직전 실행이 남긴 XML 로 조용히 통과했다(정책 파일 「알려진 한계」가 실측을
     * 적어 두었다).
     *
     * 술어는 **리포트가 잴 소스보다 새로운가**다. 소스를 고친 뒤 cpd 를 빼면 리포트가 그보다 낡아
     * 붉는다. task 의 `didWork` 를 직접 보는 축은 쓰지 않았다 — configuration-cache 아래에서 다른
     * task 의 상태를 실행 시점에 읽으려면 그 task 참조를 들고 있어야 한다.
     *
     * 알려진 한계: 리포트를 `touch` 하면 이 축은 통과한다(내용 무변).
     */
    @TaskAction
    fun gate() {
        val newestSource = expectedSource.files.filter(File::isFile).maxOfOrNull(File::lastModified) ?: 0L
        cpdReportPresenceViolation(cpdXmlReport.get().asFile, !expectedSource.isEmpty, newestSource)
            ?.let { throw GradleException(it) }
    }
}
