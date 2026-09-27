package bidvector.app.collection

import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.CollectionTerminationTestConfiguration
import bidvector.app.wiring.RecordingCollectionTermination
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import javax.sql.DataSource

/**
 * M6/6G D-6G-2 E2E — 적재된 dev DB 를 **출하 조립**으로 읽어 스냅숏 두 바이트를 저장소 밖에 쓴다.
 * 잠그는 것: ① 같은 입력이면 같은 바이트 ② 상호가 어디에도 없다 ③ 기초금액 두 칸이 갈린다
 * (마감 뒤 공개된 기초금액은 투찰 시점 칸에 오르지 않는다) ④ manifest 가 rows 의 해시를 싣는다.
 */
class SnapshotExtractionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "snapshot-e2e-test-fixture-credential"
        private const val BIDDER_NAME = "SYN-상호-드러나면안됨"
        private const val NOTICE_NUMBER = "SNAP-E2E-0001"
        private const val LATE_NOTICE_NUMBER = "SNAP-E2E-0002"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_snapshot_e2e_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private val dataSource: DataSource =
            org.postgresql.ds.PGSimpleDataSource().apply {
                setUrl(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
            }

        @AfterAll
        @JvmStatic
        fun stop() {
            postgres.stop()
        }
    }

    private val from = LocalDate.of(2026, 6, 1)
    private val to = LocalDate.of(2026, 6, 30)

    private fun runExtraction(outputDir: Path): List<Int> {
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
                        "bidvector.snapshot-extract.mode" to "once",
                        "bidvector.snapshot-extract.from" to from.toString(),
                        "bidvector.snapshot-extract.to" to to.toString(),
                        "bidvector.snapshot-extract.output-dir" to outputDir.toString(),
                        "bidvector.snapshot-extract.snapshot-id" to "snap-e2e",
                        "bidvector.snapshot-extract.sample-list-sha256" to "feedfacecafe",
                    ),
            ).run()
        return try {
            context.getBean(RecordingCollectionTermination::class.java).exitCodes.toList()
        } finally {
            context.close()
        }
    }

    private fun seed() {
        SnapshotSeed(dataSource).load(
            noticeNumber = NOTICE_NUMBER,
            lateNoticeNumber = LATE_NOTICE_NUMBER,
            bidderName = BIDDER_NAME,
        )
    }

    @Test
    fun `적재 → 추출 → 같은 입력이면 바이트가 같고 상호는 어디에도 없다`() {
        seed()
        val firstDir = Files.createTempDirectory("snapshot-e2e-1")
        val secondDir = Files.createTempDirectory("snapshot-e2e-2")

        runExtraction(firstDir) shouldContainExactly listOf(0)
        runExtraction(secondDir) shouldContainExactly listOf(0)

        val firstRows = Files.readString(firstDir.resolve("rows.jsonl"))
        val secondRows = Files.readString(secondDir.resolve("rows.jsonl"))
        firstRows shouldBe secondRows
        firstRows shouldNotContain BIDDER_NAME
        // 공고번호 원문도 싣지 않는다 — 해시만 간다.
        firstRows shouldNotContain NOTICE_NUMBER
        Files.readString(firstDir.resolve("manifest.json")) shouldBe
            Files.readString(secondDir.resolve("manifest.json"))
    }

    @Test
    fun `기초금액 두 칸이 갈린다 — 마감 뒤 공개된 값은 투찰 시점 칸에 오르지 않는다`() {
        seed()
        val dir = Files.createTempDirectory("snapshot-e2e-3")

        runExtraction(dir)

        val rows = Files.readString(dir.resolve("rows.jsonl")).trimEnd('\n').lines()
        // 마감 **전** 공개된 공고: 투찰 시점 칸에 값이 있다.
        val onTime = rows.single { it.contains("\"base_amount\":1234567890") }
        onTime shouldContain "\"opening_base_amount\":1239999999"
        // 마감 **뒤** 공개된 공고: 투찰 시점 칸은 null 이고 개찰 출처만 남는다.
        val late = rows.single { it.contains("\"base_amount\":null") }
        late shouldContain "\"opening_base_amount\":1239999999"
        late shouldContain "\"base_amount_disclosed_at\":"
    }

    @Test
    fun `manifest 가 rows 바이트의 해시와 표본 목록 해시를 싣는다`() {
        seed()
        val dir = Files.createTempDirectory("snapshot-e2e-4")

        runExtraction(dir)

        val manifest = Files.readString(dir.resolve("manifest.json"))
        manifest shouldContain "\"schema_version\":\"snapshot-v2\""
        manifest shouldContain "\"sample_list_sha256\":\"feedfacecafe\""
        manifest shouldContain "\"snapshot_id\":\"snap-e2e\""
    }
}
