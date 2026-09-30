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
        when (marker) {
            "dir" -> Files.createDirectories(root.resolve(".git"))
            "gradle" -> Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"x\"\n")
            else -> Files.writeString(root.resolve(".git"), "gitdir: …\n")
        }
        Files.createDirectories(root.resolve("reports/evidence"))
        return root
    }

    @Test
    fun `저장소 루트는 git 표식을 가진 첫 조상이다 — 디렉터리든 파일이든`() {
        val root = repositoryWithMarker("dir")
        val deep = Files.createDirectories(root.resolve("reports/evidence/m6/6g"))

        repositoryRoot(deep) shouldBe root.toRealPath()
        repositoryRoot(root) shouldBe root.toRealPath()
    }

    @Test
    fun `linked worktree 의 git 은 파일이다 — 그것도 루트다`() {
        val root = repositoryWithMarker("file")

        repositoryRoot(root.resolve("reports")) shouldBe root.toRealPath()
    }

    /**
     * D-6G-51 — 루트를 **대상 경로에서** 찾는다. 이 test 는 `root` 인자를 주지 않는다: 기본 갈래가
     * 실제로 그 경로에서 루트를 찾는지 재는 것이 요점이고, 인자를 주면 기본 갈래를 지나지 않는다.
     */
    @Test
    fun `저장소 안은 거부된다 — 실행 위치와 무관하게`() {
        val root = repositoryWithMarker("dir")

        shouldThrow<IllegalArgumentException> { requireOutsideRepository(root.resolve("fixtures/golden")) }
        // 아직 없는 하위 경로도 같다 — 존재하는 조상까지 올라가 표식을 만난다.
        shouldThrow<IllegalArgumentException> { requireOutsideRepository(root.resolve("a/b/c/d")) }
    }

    @Test
    fun `저장소 밖은 통과한다 — 아직 없는 경로도`() {
        repositoryWithMarker("dir")
        val outside = temp.resolve("snapshots/2026-09-28/rows")

        requireOutsideRepository(outside) shouldBe temp.toRealPath().resolve("snapshots/2026-09-28/rows")
    }

    /** 이름만 대조하면 밖을 가리키는 이름이 안을 가리켜도 통과한다. */
    @Test
    fun `심링크를 따라간다`() {
        val root = repositoryWithMarker("dir")
        val link = temp.resolve("looks-outside")
        Files.createSymbolicLink(link, root.resolve("reports"))

        shouldThrow<IllegalArgumentException> { requireOutsideRepository(link.resolve("snapshot")) }
    }

    /** 계약(D-6G-43)이 적은 표식 — `.git` 이 없는 배치에서도 경계가 선다. */
    @Test
    fun `settings gradle kts 도 루트 표식이다`() {
        val root = repositoryWithMarker("gradle")

        repositoryRoot(root.resolve("reports/evidence")) shouldBe root.toRealPath()
    }

    /**
     * 대상이 어느 저장소에도 속하지 않으면 경계가 없다 — 그때만 통과다. 앞 판은 **cwd** 가 저장소
     * 밖이면 경계가 사라져 저장소 안을 가리키는 경로까지 통과했다(vr M-6). 이제 그 구멍이 없다.
     */
    @Test
    fun `어느 저장소에도 속하지 않는 경로는 경계가 없다`() {
        repositoryRoot(temp.resolve("nowhere")) shouldBe null
        requireOutsideRepository(temp.resolve("nowhere")) shouldBe temp.toRealPath().resolve("nowhere")
    }
}
