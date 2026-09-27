package bidvector.app.collection

import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.CollectionTerminationTestConfiguration
import bidvector.app.wiring.RecordingCollectionTermination
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/**
 * M6/6G D-6G-27 E2E — **적재도 출하 경로로 한다.** 수집 갈래 둘(공고 목록 · 개찰 축)을 가짜
 * transport 로 돌려 DB 를 채우고, 그 위에서 추출을 돌린다. 표에 직접 INSERT 하지 않는다 —
 * verifier 가 지적한 대로 직접 INSERT 는 「수집이 실제로 채우는가」를 가려 개찰일 결함을 숨겼다.
 *
 * 잠그는 것: ① 같은 입력이면 바이트 동일 ② golden 과 바이트 동일(레인 간 왕복) ③ 상호·공고번호
 * 원문 부재 ④ 추첨번호가 실제로 찬다 ⑤ 공고일 ≠ 개찰일 ⑥ 기초금액 두 칸 분리.
 */
class SnapshotExtractionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "snapshot-e2e-test-fixture-credential"
        private const val BIDDER_NAME = "SYN-상호-드러나면안됨"
        /**
         * 업무마다 여섯 — 마지막 하나(`MISSING_SAMPLE_INDEX`)가 업무마다 다른 사유로 행이 되지
         * 못한다(공사는 상세 결측, 용역은 canonical 결측). 표본 12 · 행 10 · 결측 계수 각 1.
         */
        private const val NOTICES_PER_SLOT = 6

        /** 층 = 업무 × 공고 주 — 이 E2E 는 하루치 두 업무라 층이 둘이다. */
        private const val DIVISIONS = 2
        private val NOTICE_DAY: LocalDate = LocalDate.of(2026, 6, 3)

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_snapshot_e2e_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var mock: MockOpeningKonepsHttp

        /** 적재는 **한 번만** — 원문 표가 append-only 라 반복 적재가 행을 늘린다. */
        @BeforeAll
        @JvmStatic
        fun collect() {
            // **고정 표식**이다 — golden 이 공고 키 해시를 담으므로 실행마다 번호가 달라지면
            // golden 이 설 수 없다. 이 test 는 자기 컨테이너에서 한 번만 적재하므로 충돌이 없다.
            mock =
                MockOpeningKonepsHttp(
                    noticesPerSlot = NOTICES_PER_SLOT,
                    bidderName = BIDDER_NAME,
                    nonce = "GOLDEN",
                )
            bootOnce(
                mapOf(
                    "bidvector.collection.mode" to "once",
                    "bidvector.collection.from" to NOTICE_DAY.toString(),
                    "bidvector.collection.to" to NOTICE_DAY.toString(),
                    "bidvector.collection.categories" to "construction,service",
                ),
            )
            bootOnce(
                mapOf(
                    "bidvector.opening-collection.mode" to "once",
                    "bidvector.opening-collection.from" to NOTICE_DAY.toString(),
                    "bidvector.opening-collection.to" to NOTICE_DAY.toString(),
                    "bidvector.opening-collection.categories" to "construction,service",
                    "bidvector.opening-collection.sampling-seed" to "6g-extract-seed",
                    // 층 둘(공사·용역, 같은 주) 전수 — 후보 12 를 다 뽑는다.
                    "bidvector.opening-collection.sample-size" to "12",
                    "bidvector.opening-collection.calls-per-day" to "10000",
                    "bidvector.opening-collection.calls-total" to "10000",
                    "bidvector.opening-collection.budget-since" to Instant.now().toString(),
                    "bidvector.opening-collection.sample-list-file" to SAMPLE_LIST_FILE.toString(),
                ),
            )
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            mock.close()
            postgres.stop()
        }

        private fun bootOnce(extra: Map<String, String>): List<Int> {
            val context =
                SpringApplicationBuilder(
                    BidVectorApplication::class.java,
                    CollectionTerminationTestConfiguration::class.java,
                ).properties(
                    PRODUCTION_DISPATCH_PROPERTIES +
                        mapOf(
                            "server.port" to "0",
                            "spring.profiles.active" to "collection-e2e",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to "1000",
                            "bidvector.koneps.service-key" to "SNAPSHOT-E2E-KEY",
                            "bidvector.koneps.base-url" to mock.baseUrl,
                            "bidvector.koneps.opening.scsbid-base-url" to mock.baseUrl,
                        ) + extra,
                ).run()
            return try {
                context.getBean(RecordingCollectionTermination::class.java).exitCodes.toList()
            } finally {
                context.close()
            }
        }

        /** 수집이 확정한 표본 목록 파일 — 추출도 **같은 파일**을 읽는다(D-6G-39). */
        val SAMPLE_LIST_FILE: Path = Files.createTempDirectory("6g-sample-list").resolve("sample-list.tsv")

        fun extractTo(outputDir: Path): List<Int> =
            bootOnce(
                mapOf(
                    "bidvector.snapshot-extract.mode" to "once",
                    // **관측 창**이다 — 적재는 지금 돌았으므로 오늘을 덮어야 한다(개찰일 창이 아니다).
                    "bidvector.snapshot-extract.from" to LocalDate.now().minusDays(1).toString(),
                    "bidvector.snapshot-extract.to" to LocalDate.now().plusDays(1).toString(),
                    "bidvector.snapshot-extract.output-dir" to outputDir.toString(),
                    "bidvector.snapshot-extract.snapshot-id" to "snap-e2e",
                    "bidvector.snapshot-extract.sample-list-file" to SAMPLE_LIST_FILE.toString(),
                ),
            )
    }

    private fun extractedRows(): String {
        val dir = Files.createTempDirectory("snapshot-e2e")
        extractTo(dir)
        return Files.readString(dir.resolve("rows.jsonl"))
    }

    @Test
    fun `수집 갈래가 채운 DB 에서 추출한다 — 같은 입력이면 바이트가 같다`() {
        val first = extractedRows()
        val second = extractedRows()

        first shouldBe second
        first.trimEnd('\n').lines().size shouldBeGreaterThan 0
    }

    @Test
    fun `상호도 공고번호 원문도 스냅숏에 없다`() {
        val rows = extractedRows()

        rows shouldNotContain BIDDER_NAME
        rows shouldNotContain "OPEN-E2E"
    }

    /**
     * D-6G-38 — 추첨번호는 **예비가격 상세의 `drwtYn=Y` 행 순번**이지 투찰자가 고른 번호가 아니다.
     * mock 이 둘을 겹치지 않게 두므로(뽑힌 `3,7,11,14` ↔ 선택 `1,2,4,5,6,8`), 출처가 틀리면 이 단언이
     * 곧바로 붉어진다. r1 은 둘을 같게 맞춰 둬서 틀린 출처가 초록으로 지나갔다.
     */
    @Test
    fun `추첨번호는 뽑힌 번호다 — 투찰자 선택이 아니다`() {
        val rows = extractedRows()

        rows shouldContain "\"drawn_serial_numbers\":[3,7,11,14]"
        rows shouldNotContain "\"drawn_serial_numbers\":null"
        // 선택 합집합(1,2,4,5,6,8)이 그대로 실리는 모양이 아니다.
        rows shouldNotContain "\"drawn_serial_numbers\":[1,2,4,5,6,8]"
    }

    @Test
    fun `참가업체수가 투찰 행 수와 같다`() {
        val rows = extractedRows()

        rows.trimEnd('\n').lines().forEach { line ->
            line shouldContain "\"participant_count\":3"
        }
    }

    @Test
    fun `공고일이 개찰일과 다르다 — 창 포함 판정이 개찰일로 돌지 않는다`() {
        val rows = extractedRows()

        rows shouldContain "\"noticed_on\":\"2026-06-03\""
        rows shouldContain "\"opened_on\":\"2026-06-17\""
    }

    @Test
    fun `기초금액 두 칸이 갈린다 — 출처가 다르면 값도 다르다`() {
        val rows = extractedRows()

        // 기초금액 조회 출처(마감 전 공개)와 예비가격 상세 출처가 서로 다른 값이다.
        rows shouldContain "\"base_amount\":1234567890"
        rows shouldContain "\"opening_base_amount\":1239999999"
    }

    /**
     * 표본인데 행이 되지 못한 공고가 **실제로 있다**(D-6G-42). 둘 다 0 이면 생산 쪽이 이 칸을 상수로
     * 적어도, 행 집합을 표본 목록과 같게 맞춰도 왕복이 아무것도 잡지 못한다 — 지난 라운드 추첨번호
     * 상수 `null` 과 같은 갈래다. 두 사유를 수집 경로로 만들어(응답이 비거나 공고 목록이 내지 않아)
     * 항등식이 **0 이 아닌 값으로** 닫히게 한다.
     */
    @Test
    fun `표본은 행의 진부분집합이고 두 결측 사유가 각각 하나다`() {
        val work = Files.createTempDirectory("snapshot-counts")
        extractTo(work)

        val manifest = Files.readString(work.resolve("manifest.json"))
        val sampleLines = Files.readString(work.resolve("sample-list.tsv")).trimEnd('\n').lines()
        val rows = Files.readString(work.resolve("rows.jsonl")).trimEnd('\n').lines()

        sampleLines shouldHaveSize NOTICES_PER_SLOT * DIVISIONS
        rows shouldHaveSize sampleLines.size - 2
        manifest shouldContain "\"sample_size\":${sampleLines.size}"
        manifest shouldContain "\"sampled_without_detail\":1"
        manifest shouldContain "\"sampled_without_notice\":1"
        // 행의 키는 표본의 **진부분집합**이다 — 목록에서 빼는 식으로 맞추면 이 단언이 붉어진다.
        val sampleKeys = sampleLines.map { it.substringBefore('\t') }.toSet()
        val rowKeys = rows.map { it.substringAfter("\"notice_key_hash\":\"").substringBefore('"') }.toSet()
        sampleKeys.containsAll(rowKeys) shouldBe true
        (sampleKeys - rowKeys) shouldHaveSize 2
    }

    /**
     * D-6G-37 — golden 은 **Python 레인의 fixture 자리**에 둔다(저장소 루트의 `fixtures` 는
     * data-extract 의 corpus 자리라 실험 fixture 를 섞지 않는다). v4 부터 파일이 셋이다: 표본 목록도
     * golden 에 들어가야 판독이 `sample_list_sha256` 대조를 돌릴 수 있다.
     */
    @Test
    fun `추출 바이트가 golden 과 같다 — 레인 간 왕복의 고정점`() {
        val goldenDir = Path.of("..", "ml-engine", "tests", "evaluation", "fixtures", "m6-6g-golden")
        val work = Files.createTempDirectory("snapshot-golden")

        extractTo(work)
        val produced = SNAPSHOT_FILES.associateWith { Files.readString(work.resolve(it)) }

        if (writeGoldenRequested()) {
            Files.createDirectories(goldenDir)
            produced.forEach { (name, bytes) -> Files.writeString(goldenDir.resolve(name), bytes) }
        }
        // **비교만 한다.** 없으면 스스로 써서 초록이 되는 test 는 무엇도 잠그지 않는다.
        SNAPSHOT_FILES.forEach { name ->
            Files.exists(goldenDir.resolve(name)) shouldBe true
            produced.getValue(name) shouldBe Files.readString(goldenDir.resolve(name))
        }
    }
}

private val SNAPSHOT_FILES = listOf("rows.jsonl", "manifest.json", "sample-list.tsv")

/**
 * golden 갱신은 **사람이 있는 실행에서만** 허용한다(D-6G-44). CI 에서 이 플래그가 켜지면 golden 이
 * 그 실행의 산출물로 덮여 왕복 대조가 자기 자신과의 대조가 된다 — 초록이지만 아무것도 잠그지 않는다.
 * 그래서 CI 에서는 **무시하지 않고 실패**한다: 조용히 무시하면 누가 왜 켰는지 아무도 모른 채 지나간다.
 */
private fun writeGoldenRequested(): Boolean {
    val requested = System.getenv("BIDVECTOR_WRITE_GOLDEN") == "1"
    require(!(requested && System.getenv("CI") != null)) {
        "golden 갱신 플래그는 CI 에서 쓸 수 없다 — golden 은 사람이 보고 갱신한다"
    }
    return requested
}
