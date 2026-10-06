package bidvector.app.collection

import bidvector.adapters.snapshot.ConfirmedSampleList
import bidvector.adapters.snapshot.JdbcSnapshotSource
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.adapters.snapshot.SnapshotCounts
import bidvector.adapters.snapshot.SnapshotExtraction
import bidvector.adapters.snapshot.SnapshotWriter
import bidvector.adapters.snapshot.UnusableRawRowCause
import bidvector.adapters.snapshot.UnusableRawRows
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
    /**
     * 수집 갈래의 실행 상태 디렉터리(D-6G-39·45) — 저장소 밖. 해시를 설정으로 받지 않는다: 받으면
     * 실행자가 적어 넣은 문자열이 「결과를 보기 전에 확정됐다」의 증거 행세를 한다. 파일에서 읽는다.
     */
    val runStateDir: String,
)

/**
 * 스냅숏 추출 러너(D-6G-2) — dev DB 를 읽어 `rows.jsonl`·`manifest.json` 두 바이트를 저장소 **밖**에
 * 쓴다. **DB 는 읽기만** 한다(write 0). 실행 상태 디렉터리는 읽기 전용이 아니다(D-6G-71) — 잠금을
 * 잡고, 원장이 장부보다 앞서 있으면(크래시 흔적) `state.json` 을 재동기한다. 로그에는 계수와 경로만
 * 남긴다 — 공고 식별자도 상호도 싣지 않는다.
 */
@Suppress("TooGenericExceptionCaught")
class SnapshotExtractionRunner(
    private val source: JdbcSnapshotSource,
    private val runState: RunStateDirectory,
    private val properties: SnapshotExtractionProperties,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    /**
     * **추출도 잠금 안에서만 돈다**(D-6G-71). 잠그지 않으면 두 가지가 함께 깨진다: 수집이 도는 중에
     * 읽으면 표본·원장이 **검증되지 않은 채**로 오고(무결성 대조는 잠금을 든 자리에서만 돈다),
     * 그 순간의 원장은 절반만 쓰인 걷기를 가리킬 수 있다. 잠금을 못 잡으면 추출을 **거부**한다 —
     * 덜 읽은 스냅숏보다 없는 스냅숏이 낫다.
     */
    override fun run(args: ApplicationArguments) =
        underRunStateLock(runState, "snapshot-extract", log, termination) { extractOrFail() }

    private fun extractOrFail() {
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
        val sample = requireNotNull(runState.confirmedSampleList()) { "확정된 표본 목록이 없다 — 수집이 먼저다" }
        // 축 완료도, **어느 걷기의 행을 쓸지**도 시도 원장이 정한다(D-6G-58·68) — raw 존재도 그
        // 행의 시각도 아니다. `properties.from..to` 는 이제 추출 범위가 아니라 manifest 기간이
        // 되돌아갈 자리일 뿐이다(행에 개찰일이 하나도 없을 때).
        val conclusions = runState.attempts.read().axisConclusions()
        val extraction = source.extract(sample.list, conclusions)
        val rows = SnapshotWriter.renderRows(extraction.rows)
        val period = openingPeriod(extraction, properties.from..properties.to)
        val manifest =
            SnapshotWriter.renderManifest(
                snapshotId = properties.snapshotId,
                rowsBytes = rows,
                counts = countsOf(sample, extraction),
                period = period,
                sampleListSha256 = sample.sha256,
                sampleScopeDivisions =
                    sample.list.scope.divisions
                        .map { it.name }
                        .toSet(),
            )
        val directory = Files.createDirectories(requireOutsideRepository(Path.of(properties.outputDir)))
        Files.writeString(directory.resolve("rows.jsonl"), rows)
        Files.writeString(directory.resolve("manifest.json"), manifest)
        // 표본 목록은 **바이트 그대로** 곁에 둔다 — 판독이 manifest 해시를 실제 파일로 대조한다.
        Files.writeString(directory.resolve("sample-list.tsv"), sample.text)
        log.write(snapshotFinishedLine(sample.size, extraction, rows.length))
        termination.terminate(CollectionExitCode.COMPLETE.value)
    }
}

/**
 * 추출이 끝난 줄 — 계수들은 **이 줄이 유일한 공시 자리**다(manifest 어휘를 늘리지 않는다, 운영자 결정
 * A-2). 그래서 줄을 짓는 일을 값 함수로 둔다: 칸 하나가 빠지거나 **두 계수가 서로 바뀌어도** test 가
 * 붉어진다. 줄 안에 값을 박아 두면 그 배선은 어느 test 도 보지 못한다(vr r3 L-2 — 앞 판의 fixture 는
 * 세 계수가 모두 0 이라 리터럴 `0` 으로 바꾼 변이가 초록이었다).
 */
internal fun snapshotFinishedLine(
    sampleSize: Int,
    extraction: SnapshotExtraction,
    bytes: Int,
): String =
    "snapshot-extract finished rows=${extraction.rows.size} " +
        "sampleSize=$sampleSize " +
        "sampledWithoutDetail=${extraction.sampledWithoutDetail} " +
        "skippedWithoutNotice=${extraction.skippedWithoutNotice} " +
        "incompleteAxis=${extraction.incompleteAxis} " +
        "unusableRawRows=${extraction.unusableRawRows.total} " +
        unusableCauseFields(extraction.unusableRawRows) + " " +
        "fractionalAmounts=${extraction.fractionalAmounts} " +
        "incompleteAValues=${extraction.incompleteAValues} " +
        "outsideSample=${extraction.observedOutsideSample} bytes=$bytes"

/**
 * 키가 서지 않은 행의 **원인별 계수**(D-6G2c-20) — 합계 칸(`unusableRawRows`) 바로 뒤에 선다.
 * 칸 이름을 열거에서 돌려 짓는다: 새 원인이 생기면 [logFieldOf] 의 소진 `when` 이 컴파일로 그 자리를
 * 가리키고, 줄에는 자동으로 칸이 하나 더 선다 — 계수와 칸을 맞바꾸는 편집이 설 자리가 없다.
 */
private fun unusableCauseFields(rows: UnusableRawRows): String =
    UnusableRawRowCause.entries.joinToString(" ") { "${logFieldOf(it)}=${rows[it]}" }

/** 원인의 로그 칸 이름 — 소진 `when` 이라 새 원인을 더하면 컴파일이 여기를 가리킨다. */
private fun logFieldOf(cause: UnusableRawRowCause): String =
    when (cause) {
        UnusableRawRowCause.BLANK_NOTICE_NUMBER -> "blankNoticeNumber"
        UnusableRawRowCause.MALFORMED_ROUND -> "malformedRound"
        UnusableRawRowCause.UNKNOWN_ENDPOINT -> "unknownEndpoint"
    }

/** 계수 넷은 모두 **측정값**이다 — 항등식 자체는 구성상 참이라 [SnapshotCounts] 에서 표기로 선다. */
private fun countsOf(
    sample: ConfirmedSampleList,
    extraction: SnapshotExtraction,
): SnapshotCounts =
    SnapshotCounts(
        sampleSize = sample.size,
        rowCount = extraction.rows.size,
        sampledWithoutDetail = extraction.sampledWithoutDetail,
        sampledWithoutNotice = extraction.skippedWithoutNotice,
        incompleteAxis = extraction.incompleteAxis,
    )

/**
 * manifest 의 기간 — 행들의 **개찰일** 범위다(스키마 §2 「창을 자르는 축」). 개찰일이 있는 행이
 * 하나도 없으면 관측 창으로 물러선다(그 스냅숏은 어차피 전 행이 `OPENING_DATE_ABSENT` 다).
 */
private fun openingPeriod(
    extraction: SnapshotExtraction,
    fallback: ClosedRange<LocalDate>,
): ClosedRange<LocalDate> {
    val days = extraction.rows.mapNotNull { it.outcome.openedOn }
    return if (days.isEmpty()) fallback else days.min()..days.max()
}
