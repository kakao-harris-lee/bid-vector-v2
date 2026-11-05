package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `OPEN-MIGRATION-ORDER-GATE` 의 **순수 코어**. task 배선과 분리해 인라인 파일 이름으로 잰다
 * (`GateRegistrationCensusTest`·`TestShapesTest` 관례).
 *
 * 마지막 두 test 는 **저장소의 실제 디렉터리와 실제 정책 파일**을 읽는다 — 등식이 오늘의 main 에서
 * 성립하는지는 인라인 입력으로 증명되지 않고, 그 둘이 갈리면 게이트가 재는 것과 test 가 재는 것이
 * 달라진다(`DomainSourceReferenceFixtureTest` 가 fixture 를 실제로 읽는 것과 같은 이유).
 */
class MigrationOrderTest {
    @Test
    fun `빈틈 없는 번호 집합은 위반이 없다`() {
        assertEquals(emptyList<String>(), violations("V1__a.sql", "V2__b.sql", "V3__c.sql"))
    }

    @Test
    fun `결번은 위반이다 — 그 자리로 나중에 들어오면 조용히 건너뛰어진다`() {
        val violations = violations("V1__a.sql", "V3__c.sql")

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("V2"), "$violations")
    }

    @Test
    fun `같은 번호가 둘이면 위반이다 — 병합 순서가 갈렸다`() {
        val violations = violations("V1__a.sql", "V2__b.sql", "V2__c.sql")

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("V2"), "$violations")
    }

    @Test
    fun `은퇴 번호의 결번은 위반이 아니다`() {
        assertEquals(emptyList<String>(), violations("V1__a.sql", "V3__c.sql", retired = setOf(2)))
    }

    @Test
    fun `은퇴 번호를 다시 쓰면 위반이다 — 영구히 비워 둔 자리다`() {
        val violations = violations("V1__a.sql", "V2__b.sql", "V3__c.sql", retired = setOf(2))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("V2"), "$violations")
    }

    @Test
    fun `대상이 없는 은퇴 항목은 위반이다 — 죽은 면제는 범위를 조용히 넓힌다`() {
        val violations = violations("V1__a.sql", "V2__b.sql", retired = setOf(99))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("V99"), "$violations")
    }

    @Test
    fun `관례 밖 이름은 판독 불가로 위반이다 — 번호를 읽을 수 없으면 순서도 없다`() {
        val violations = violations("V1__a.sql", "migrate-2.sql")

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("migrate-2.sql"), "$violations")
    }

    @Test
    fun `점 버전과 repeatable 은 이 저장소의 관례가 아니라 판독 불가다`() {
        assertEquals(null, MigrationOrder.versionOf("V1.1__a.sql"))
        assertEquals(null, MigrationOrder.versionOf("R__view.sql"))
        assertEquals(24, MigrationOrder.versionOf("V24__notification_inbox_failed_bid_detail.sql"))
    }

    @Test
    fun `저장소의 마이그레이션 디렉터리는 등식을 지킨다 — 오늘의 main`() {
        val fileNames = migrationFiles().map(File::getName)
        assertTrue(fileNames.isNotEmpty(), "디렉터리가 비었다 — 비-공허 단언")

        assertEquals(emptyList<String>(), MigrationOrder.violations(fileNames, retiredInPolicy()))
    }

    @Test
    fun `정책이 고정한 은퇴 번호는 실제로 결번이다 — 정책과 디스크가 갈리면 보인다`() {
        val present = migrationFiles().mapNotNull { MigrationOrder.versionOf(it.name) }.toSet()

        val retired = retiredInPolicy()
        assertTrue(retired.isNotEmpty(), "은퇴 목록이 비었다면 이 단언의 대상이 없다")
        assertEquals(emptySet(), retired intersect present, "은퇴 번호가 디스크에 있다")
    }

    private companion object {
        fun violations(
            vararg fileNames: String,
            retired: Set<Int> = emptySet(),
        ) = MigrationOrder.violations(fileNames.toList(), retired)

        fun retiredInPolicy(): Set<Int> =
            readPolicy(policyFile())
                .requireList("migration.retired-versions")
                .map(String::toInt)
                .toSet()

        fun migrationFiles(): List<File> {
            val policy = readPolicy(policyFile())
            val module = policy.requireValue("migration.module")
            val path = policy.requireValue("migration.resource-path")
            val directory = File(repoRoot(), "$module/$path")
            assertTrue(directory.isDirectory, directory.absolutePath)
            return directory.listFiles().orEmpty().filter(File::isFile)
        }

        /** 저장소 뿌리는 `architecturePolicy` 주입 경로에서 유도한다 — 작업 디렉터리에 기대지 않는다. */
        fun policyFile(): File = File(repoRoot(), "config/quality/migration-policy.properties")

        fun repoRoot(): File =
            File(System.getProperty("bidvector.architecture.policy"))
                .parentFile
                .parentFile
                .parentFile
    }
}
