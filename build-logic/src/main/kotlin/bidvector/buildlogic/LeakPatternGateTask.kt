package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * PR #5 게이트 시정(privacy-gate) — `config/quality/leak-patterns.txt`(M4/4E 신설)가 어느
 * gradle task 에도 배선되지 않아 slice 마다 손으로 돌리는 관행이었다. 이 task 가 `check` 에
 * 걸리는 실물이다.
 *
 * **스캔 대상은 `reports/evidence/`다**(소스가 아니다) — 이 패턴 파일을 만든 4E 의 실제
 * 관행(scope.md S-3c, 모든 slice 의 `commands.md` 가 손으로 반복해 온 「evidence 자기 스캔」)
 * 을 그대로 승격한 것이다. 소스 트리(Kotlin) 전수를 대상으로 하지 않는 이유는 실측 때문 —
 * `maxTokensPerCall`·`KtTokens.PRIVATE_KEYWORD`·`Authorization: Bearer`(정당한 헤더 생성
 * 코드, `LlmClient.kt`)·`ServiceKeyTest`/`ExtractionResponseNotLoggedTest`(유출 **없음**을
 * 증명하는 test double) 처럼 도메인 어휘·정당한 구현·유출 방어 test 자체가 이 어휘 축과
 * 광범위하게 겹쳐, 소스 전수 스캔은 이 정책 파일을 무의미한 소음으로 만든다(실측:
 * 저장소 전체 Kotlin source 스캔 시 자기매치 60건 이상, 전부 정당).
 *
 * **자기매치 함정 — 두 겹**: (1) 이 정책 파일 자신을 스캔 대상에 넣으면 패턴 어휘가 자신과
 * 매치된다 — `leak-patterns.txt` 는 `config/` 아래라 스캔 대상(`reports/evidence/`) 밖이라
 * 구조적으로 해당 없음. (2) evidence 문서 자신이 이 스캔 **명령**을 인용하거나(모든 slice
 * `commands.md` 의 반복 관행) 과거 라운드의 판독 서술(`fixtures.md`·codex 리뷰 JSON)에
 * 어휘가 등장한다 — `scope.md` 는 관례상 계약 문서라 빼고(4E 관례 승계), 나머지는
 * **baseline 파일**(`config/quality/leak-pattern-baseline.txt`)로 접는다. 이 baseline 은
 * 이 게이트가 배선되기 **이전에** 이미 승인·종결된 slice 들의 기존 매치를 문서화한
 * 것이다(`DomainSourceReferenceGateTask`의 허용 목록과 같은 관례 — 몰라서 통과가 아니라
 * 알고 받아들인 것). 게이트는 baseline **밖의 새 매치만** 실패시킨다 — 회귀는 막되
 * 이미 승인된 evidence 를 소급 편집(codex 리뷰 JSON은 append-only)하지 않는다.
 */
abstract class LeakPatternGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val patternsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baselineFile: RegularFileProperty

    /** 스캔 루트(`reports/evidence`) — 트리 전체가 입력이다(단일 파일이 아니라 디렉터리). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scanRoot: ConfigurableFileCollection

    /** 파일 이름으로 제외한다 — 4E 관례(`scope.md` 는 계약 문서, 육안 리뷰 대상). */
    @get:Input
    abstract val excludedFileNames: SetProperty<String>

    /** 보고서의 경로 표기·baseline 대조 키를 저장소 루트 상대경로로 통일하기 위해서만 쓴다. */
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val patterns = parseLeakPatterns(patternsFile.get().asFile.readLines())
        if (patterns.isEmpty()) {
            throw GradleException("leak-patterns.txt 에 패턴이 하나도 없다 — 게이트가 공허하게 통과한다")
        }

        val baseline = parseLeakBaseline(baselineFile.get().asFile.readLines())
        // D-LBC-2 — 옛 형식(경로:줄번호) 항목은 조용히 stale 로 죽지 않고 명시적으로 게이트를 실패시킨다.
        // throw 는 아래 한 곳으로 모은다(ThrowsCount 래칫 — leakGateViolation 과 우선순위만 다르다).
        val legacyFormatViolation = leakBaselineLegacyFormatViolation(baseline)

        val excluded = excludedFileNames.get()
        val root = repoRoot.get().asFile

        val files =
            scanRoot.files
                .filter(File::isDirectory)
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.name !in excluded } }
                .sortedBy(File::getPath)

        val matches =
            files.flatMap { file ->
                val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                leakMatchesInFile(relative, file.readLines(), patterns)
            }

        // 위반 메시지와 보고서가 이 그룹핑을 각자 다시 계산하면 어긋날 수 있어 여기서 한 번만
        // 묶어 두 함수에 그대로 넘긴다.
        val coordinatesByKey = matches.groupBy { leakBaselineKey(it.path, it.content) }
        val newKeys = newLeakBaselineKeys(coordinatesByKey.keys, baseline)
        val staleBaseline = staleLeakBaselineEntries(coordinatesByKey.keys, baseline)

        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(
                leakGateReportText(
                    patterns.size,
                    baseline.size,
                    matches.size,
                    coordinatesByKey,
                    newKeys,
                    staleBaseline,
                ),
            )

        (legacyFormatViolation ?: leakGateViolation(newKeys, coordinatesByKey))?.let { throw GradleException(it) }
    }
}
