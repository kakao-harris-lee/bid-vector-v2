package bidvector.app.collection

import bidvector.adapters.snapshot.JdbcSnapshotSource
import bidvector.adapters.snapshot.SnapshotExtraction
import bidvector.adapters.snapshot.SnapshotWriter
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * 스냅숏 추출의 입력(M6/6G D-6G-2) — `bidvector.snapshot-extract.mode=once` 일 때만 바인딩된다.
 * [outputDir] 는 **저장소 밖** 경로여야 한다(data-extract §7 · ADR 0010 D-8) — 실험 입력은 커밋되지
 * 않는다. 기본값을 두지 않는다: 어디에 쓰는지를 실행자가 매번 정한다.
 */
@ConfigurationProperties(prefix = "bidvector.snapshot-extract")
data class SnapshotExtractionProperties(
    /**
     * **관측 창**이다(개찰일 창이 아니다) — 이 갈래가 적재한 원문을 `observed_at` 으로 자른다.
     * manifest 가 싣는 기간은 이것이 아니라 행들의 **개찰일** 범위다(스키마 §2) — 둘을 같은 값으로
     * 두면 manifest 가 거짓을 말한다.
     */
    val from: LocalDate,
    val to: LocalDate,
    val outputDir: String,
    val snapshotId: String,
    val sampleListSha256: String,
)

/**
 * 스냅숏 추출 러너(D-6G-2) — dev DB 를 읽어 `rows.jsonl`·`manifest.json` 두 바이트를 저장소 **밖**에
 * 쓴다. 읽기만 한다(DB write 없음). 로그에는 계수와 경로만 남긴다 — 공고 식별자도 상호도 싣지 않는다.
 */
@Suppress("TooGenericExceptionCaught")
class SnapshotExtractionRunner(
    private val source: JdbcSnapshotSource,
    private val properties: SnapshotExtractionProperties,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        try {
            extract()
        } catch (failure: Exception) {
            // 형제 러너와 같은 형태(D-6F8-4) — 원 예외를 잇지 않는다. 저장소 예외 메시지에 행 값이,
            // 파일 예외 메시지에 **절대 경로**가 실릴 수 있어 그 채널을 만들지 않는다.
            val causeCode = openingCauseCodeOf(failure)
            log.write(failureLine(causeCode))
            throw CollectionRunFailedException(causeCode)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun extract() {
        val extraction = source.extract(properties.from, properties.to)
        val rows = SnapshotWriter.renderRows(extraction.rows)
        val period = openingPeriod(extraction, properties.from to properties.to)
        val manifest =
            SnapshotWriter.renderManifest(
                snapshotId = properties.snapshotId,
                rowsBytes = rows,
                rowCount = extraction.rows.size,
                periodStart = period.first,
                periodEnd = period.second,
                sampleListSha256 = properties.sampleListSha256,
            )
        val directory = Files.createDirectories(requireOutsideRepository(Path.of(properties.outputDir)))
        Files.writeString(directory.resolve("rows.jsonl"), rows)
        Files.writeString(directory.resolve("manifest.json"), manifest)
        log.write(
            "snapshot-extract finished rows=${extraction.rows.size} " +
                "skippedWithoutNotice=${extraction.skippedWithoutNotice} " +
                "frameOnly=${extraction.frameOnlyNotices} bytes=${rows.length}",
        )
        termination.terminate(CollectionExitCode.COMPLETE.value)
    }
}

/**
 * manifest 의 기간 — 행들의 **개찰일** 범위다(스키마 §2 「창을 자르는 축」). 개찰일이 있는 행이
 * 하나도 없으면 관측 창으로 물러선다(그 스냅숏은 어차피 전 행이 `OPENING_DATE_ABSENT` 다).
 */
private fun openingPeriod(
    extraction: SnapshotExtraction,
    fallback: Pair<LocalDate, LocalDate>,
): Pair<LocalDate, LocalDate> {
    val days = extraction.rows.mapNotNull { it.outcome.openedOn }
    return if (days.isEmpty()) fallback else days.min() to days.max()
}

/**
 * 실험 입력은 **저장소 밖**이다(data-extract §7 · ADR 0010 D-8) — 커밋되지 않아야 한다. 출력 경로가
 * 저장소 루트 아래면 거부한다(privacy INFO-2). 「커밋하지 마라」를 규율이 아니라 기동 실패로 둔다.
 */
private fun requireOutsideRepository(target: Path): Path {
    val absolute = target.toAbsolutePath().normalize()
    val repositoryRoot = Path.of("").toAbsolutePath().normalize()
    require(!absolute.startsWith(repositoryRoot)) {
        "스냅숏 출력 경로는 저장소 밖이어야 한다 — 실험 입력은 커밋되지 않는다"
    }
    return absolute
}
