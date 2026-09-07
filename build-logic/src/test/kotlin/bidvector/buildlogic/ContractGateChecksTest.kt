package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractGateChecksTest {
    // ---- (a) 도구 버전 assertion ----

    @Test
    fun `버전이 같으면 위반이 없다`() {
        assertNull(toolVersionViolation("buf", "1.72.0\n", "1.72.0"))
    }

    @Test
    fun `버전이 다르면 위반이다`() {
        assertNotNull(toolVersionViolation("buf", "1.71.0", "1.72.0"))
    }

    // ---- (b)(c) buf 프로세스 결과 ----

    @Test
    fun `exit 0 은 위반이 없다`() {
        assertNull(bufProcessViolation("buf lint", 0, ""))
    }

    @Test
    fun `exit 0 이 아니면 위반이고 원문을 싣는다`() {
        val violation = bufProcessViolation("buf breaking", 100, "필드가 삭제됐다")
        assertNotNull(violation)
        assertTrue(violation.contains("필드가 삭제됐다"))
    }

    // ---- (d) generateProto 결정성 ----

    @Test
    fun `같은 파일 집합·같은 내용이면 위반이 없다`() {
        val map = mapOf("a/Foo.kt" to "hash1", "b/Bar.java" to "hash2")
        assertNull(generationDeterminismViolation(map, map.toMap()))
    }

    @Test
    fun `파일 집합이 다르면 위반이다`() {
        val first = mapOf("a/Foo.kt" to "hash1")
        val second = mapOf("a/Foo.kt" to "hash1", "b/Bar.kt" to "hash2")
        assertNotNull(generationDeterminismViolation(first, second))
    }

    @Test
    fun `같은 파일의 해시가 다르면 위반이다`() {
        val first = mapOf("a/Foo.kt" to "hash1")
        val second = mapOf("a/Foo.kt" to "hash2")
        assertNotNull(generationDeterminismViolation(first, second))
    }

    @Test
    fun `디렉터리 해시는 파일 내용이 같으면 두 번 같은 값을 낸다`() {
        val dir = Files.createTempDirectory("contract-gate-hash")
        dir.resolve("nested").createDirectory()
        dir.resolve("nested/x.txt").writeText("hello")
        val first = hashDirectoryContents(dir.toFile())
        val second = hashDirectoryContents(dir.toFile())
        assertNull(generationDeterminismViolation(first, second))
        assertEquals(setOf("nested" + java.io.File.separator + "x.txt"), first.keys)
    }

    @Test
    fun `디렉터리 해시는 내용이 다르면 다른 값을 낸다`() {
        val dirA = Files.createTempDirectory("contract-gate-hash-a")
        val dirB = Files.createTempDirectory("contract-gate-hash-b")
        dirA.resolve("x.bin").writeBytes(byteArrayOf(1, 2, 3))
        dirB.resolve("x.bin").writeBytes(byteArrayOf(1, 2, 4))
        assertNotNull(
            generationDeterminismViolation(hashDirectoryContents(dirA.toFile()), hashDirectoryContents(dirB.toFile())),
        )
    }

    // ---- (e) 작업 트리 청결 ----

    @Test
    fun `porcelain 출력이 비어있으면 위반이 없다`() {
        assertNull(uncleanWorkingTreeViolation(listOf("ml-contract"), ""))
        assertNull(uncleanWorkingTreeViolation(listOf("ml-contract"), "   \n"))
    }

    @Test
    fun `porcelain 출력이 있으면 위반이다`() {
        assertNotNull(uncleanWorkingTreeViolation(listOf("ml-contract"), " M ml-contract/build.gradle.kts"))
    }

    // ---- (f) 무소스 단언 ----

    private val expectedFiles = setOf("build.gradle.kts", "gradle.properties", "settings.gradle.kts")

    @Test
    fun `src 가 없고 최상위가 빌드 파일 셋뿐이면 위반이 없다`() {
        assertNull(
            includedBuildSourcePresenceViolation(
                srcDirectoryExists = false,
                topLevelFileNames = expectedFiles,
                expectedTopLevelFiles = expectedFiles,
            ),
        )
    }

    @Test
    fun `src 디렉터리가 있으면 위반이다`() {
        assertNotNull(
            includedBuildSourcePresenceViolation(
                srcDirectoryExists = true,
                topLevelFileNames = expectedFiles,
                expectedTopLevelFiles = expectedFiles,
            ),
        )
    }

    @Test
    fun `빌드 파일 셋 밖의 파일이 있으면 위반이다(우회 후보 8)`() {
        assertNotNull(
            includedBuildSourcePresenceViolation(
                srcDirectoryExists = false,
                topLevelFileNames = expectedFiles + "Sneaky.kt",
                expectedTopLevelFiles = expectedFiles,
            ),
        )
    }
}
