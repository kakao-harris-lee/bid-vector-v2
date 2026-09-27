package bidvector.app.collection

import bidvector.adapters.snapshot.JdbcSnapshotSource
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
class SnapshotExtractionRunner(
    private val source: JdbcSnapshotSource,
    private val properties: SnapshotExtractionProperties,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val extraction = source.extract(properties.from, properties.to)
        val rows = SnapshotWriter.renderRows(extraction.rows)
        val manifest =
            SnapshotWriter.renderManifest(
                snapshotId = properties.snapshotId,
                rowsBytes = rows,
                rowCount = extraction.rows.size,
                periodStart = properties.from,
                periodEnd = properties.to,
                sampleListSha256 = properties.sampleListSha256,
            )
        val directory = Files.createDirectories(Path.of(properties.outputDir))
        Files.writeString(directory.resolve("rows.jsonl"), rows)
        Files.writeString(directory.resolve("manifest.json"), manifest)
        log.write(
            "snapshot-extract finished rows=${extraction.rows.size} " +
                "skippedWithoutNotice=${extraction.skippedWithoutNotice} bytes=${rows.length}",
        )
        termination.terminate(CollectionExitCode.COMPLETE.value)
    }
}
