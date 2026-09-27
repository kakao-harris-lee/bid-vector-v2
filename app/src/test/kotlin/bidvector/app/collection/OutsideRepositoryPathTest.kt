package bidvector.app.collection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * 「저장소 밖」의 경계는 **저장소 루트**다(D-6G-43). cwd 를 기준으로 삼으면 경로를 하나도 바꾸지 않고
 * 작업 디렉터리만 바꿔 저장소 안을 「밖」으로 통과시킬 수 있다.
 */
class OutsideRepositoryPathTest {
    @TempDir
    lateinit var temp: Path

    private fun repositoryWithMarker(marker: String): Path {
        val root = Files.createDirectories(temp.resolve("repo"))
        val git = root.resolve(".git")
        if (marker == "dir") Files.createDirectories(git) else Files.writeString(git, "gitdir: …\n")
        Files.createDirectories(root.resolve("reports/evidence"))
        return root
    }

    @Test
    fun `저장소 루트는 git 표식을 가진 첫 조상이다 — 디렉터리든 파일이든`() {
        val root = repositoryWithMarker("dir")
        val deep = Files.createDirectories(root.resolve("reports/evidence/m6/6g"))

        repositoryRoot(from = deep) shouldBe root.toRealPath()
        repositoryRoot(from = root) shouldBe root.toRealPath()
    }

    @Test
    fun `linked worktree 의 git 은 파일이다 — 그것도 루트다`() {
        val root = repositoryWithMarker("file")

        repositoryRoot(from = root.resolve("reports")) shouldBe root.toRealPath()
    }

    @Test
    fun `하위 디렉터리에서 돌아도 저장소 안은 거부된다`() {
        val root = repositoryWithMarker("dir")
        val deep = root.resolve("reports/evidence")

        // cwd 를 기준으로 삼았다면 이 경로는 deep 의 밖이라 통과했을 것이다.
        shouldThrow<IllegalArgumentException> {
            requireOutsideRepository(root.resolve("fixtures/golden"), root = repositoryRoot(from = deep))
        }
    }

    @Test
    fun `저장소 밖은 통과한다 — 아직 없는 경로도`() {
        val root = repositoryWithMarker("dir")
        val outside = temp.resolve("snapshots/2026-09-28/rows")

        requireOutsideRepository(outside, root = repositoryRoot(from = root)) shouldBe
            temp.toRealPath().resolve("snapshots/2026-09-28/rows")
    }

    /** 이름만 대조하면 밖을 가리키는 이름이 안을 가리켜도 통과한다. */
    @Test
    fun `심링크를 따라간다`() {
        val root = repositoryWithMarker("dir")
        val link = temp.resolve("looks-outside")
        Files.createSymbolicLink(link, root.resolve("reports"))

        shouldThrow<IllegalArgumentException> {
            requireOutsideRepository(link.resolve("snapshot"), root = repositoryRoot(from = root))
        }
    }

    @Test
    fun `저장소가 아닌 곳에서는 경계가 없다`() {
        repositoryRoot(from = temp.resolve("nowhere")) shouldBe null
    }
}
