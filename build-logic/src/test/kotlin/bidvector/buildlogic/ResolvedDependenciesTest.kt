package bidvector.buildlogic

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * `resolveDependencies`의 분류 — composite 치환 의존(다른 build 의 project)이 같은 build 의
 * project 의존과 섞이지 않는지가 요점이다(M2/2A 리뷰 r3 ⓕ). `adapters`의
 * `testImplementation("bidvector:ml-contract")`가 해석되면 그 component 의 `id`는
 * `ProjectComponentIdentifier`이지만 **다른 build**(`ml-contract` included build)에 속한다 —
 * `id` 타입만 보고 전부 `projectPaths`로 접으면 이 좌표가 `ModuleDependencyPolicy`의 업무
 * 모듈 간 직접 참조 판정(허용 project 목록 밖)에 잘못 걸린다.
 *
 * Gradle 을 띄우지 않고 순수 함수를 손으로 만든 결과 그래프로 검증한다 — fake 는 이 파일에
 * 갇힌 최소 구현이다(재사용할 만큼 안정된 test 대역이 아니라 다른 게이트 test 의 공유
 * fixture 로 승격하지 않는다).
 */
class ResolvedDependenciesTest {
    @Test
    fun `같은 build 의 project 의존은 projectPaths 로 분류된다`() {
        val mainBuild = fakeBuild(":")
        val root = fakeProjectNode(mainBuild, ":adapters")
        val decision = fakeProjectNode(mainBuild, ":decision")
        root.dependsOn(decision)

        val result = resolveDependencies(listOf(root))

        assertEquals(setOf(":decision"), result.projectPaths)
        assertEquals(emptyMap(), result.externalModules)
    }

    @Test
    fun `다른 build 의 project(composite 치환)는 projectPaths 가 아니라 externalModules 로 분류된다`() {
        val mainBuild = fakeBuild(":")
        val mlContractBuild = fakeBuild(":ml-contract")
        val root = fakeProjectNode(mainBuild, ":adapters")
        val mlContract =
            fakeProjectNode(
                mlContractBuild,
                ":",
                moduleVersion = fakeModuleVersion("bidvector", "ml-contract", "unspecified"),
            )
        root.dependsOn(mlContract)

        val result = resolveDependencies(listOf(root))

        assertEquals(emptySet(), result.projectPaths, "다른 build 의 project 가 projectPaths 에 섞이면 안 된다")
        assertEquals(mapOf("bidvector:ml-contract" to "unspecified"), result.externalModules)
    }

    @Test
    fun `외부 모듈 의존은 externalModules 로 분류된다`() {
        val mainBuild = fakeBuild(":")
        val root = fakeProjectNode(mainBuild, ":adapters")
        val kotlinStdlib = fakeExternalNode("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.10")
        root.dependsOn(kotlinStdlib)

        val result = resolveDependencies(listOf(root))

        assertEquals(emptySet(), result.projectPaths)
        assertEquals(mapOf("org.jetbrains.kotlin:kotlin-stdlib" to "2.4.10"), result.externalModules)
    }

    @Test
    fun `root 자기 자신은 project 의존에 실리지 않는다`() {
        val mainBuild = fakeBuild(":")
        val root = fakeProjectNode(mainBuild, ":adapters")

        val result = resolveDependencies(listOf(root))

        assertEquals(emptySet(), result.projectPaths)
        assertEquals(emptyMap(), result.externalModules)
    }

    @Test
    fun `main 과 test 둘 다의 root 가 같은 build 라도 서로를 project 의존으로 만들지 않는다`() {
        val mainBuild = fakeBuild(":")
        val mainRoot = fakeProjectNode(mainBuild, ":adapters")
        val testRoot = fakeProjectNode(mainBuild, ":adapters")
        val workflow = fakeProjectNode(mainBuild, ":workflow")
        testRoot.dependsOn(workflow)

        val result = resolveDependencies(listOf(mainRoot, testRoot))

        assertEquals(setOf(":workflow"), result.projectPaths)
    }

    // ---- fakes — Gradle API 인터페이스의 최소 구현. 호출되지 않는 멤버는 미지원으로 던진다. ----

    private fun fakeBuild(path: String): BuildIdentifier =
        object : BuildIdentifier {
            override fun getBuildPath(): String = path
        }

    private fun fakeModuleVersion(
        group: String,
        name: String,
        version: String,
    ): ModuleVersionIdentifier =
        object : ModuleVersionIdentifier {
            override fun getVersion(): String = version

            override fun getGroup(): String = group

            override fun getName(): String = name

            override fun getModule(): ModuleIdentifier =
                object : ModuleIdentifier {
                    override fun getGroup(): String = group

                    override fun getName(): String = name
                }
        }

    private class FakeNode(
        private val nodeId: ComponentIdentifier,
        private val nodeModuleVersion: ModuleVersionIdentifier?,
    ) : ResolvedComponentResult {
        private val deps = mutableListOf<DependencyResult>()

        fun dependsOn(node: FakeNode) {
            deps +=
                object : ResolvedDependencyResult {
                    override fun getSelected(): ResolvedComponentResult = node

                    override fun getResolvedVariant(): ResolvedVariantResult = unsupported()

                    override fun getRequested(): ComponentSelector = unsupported()

                    override fun getFrom(): ResolvedComponentResult = this@FakeNode

                    override fun isConstraint(): Boolean = false
                }
        }

        override fun getId(): ComponentIdentifier = nodeId

        override fun getDependencies(): Set<DependencyResult> = deps.toSet()

        override fun getDependents(): Set<ResolvedDependencyResult> = emptySet()

        override fun getSelectionReason(): ComponentSelectionReason = unsupported()

        override fun getModuleVersion(): ModuleVersionIdentifier? = nodeModuleVersion

        override fun getVariants(): List<ResolvedVariantResult> = emptyList()

        override fun getDependenciesForVariant(variant: ResolvedVariantResult): List<DependencyResult> = emptyList()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("이 fake 에서 호출되지 않아야 한다")
    }

    private fun fakeProjectNode(
        build: BuildIdentifier,
        path: String,
        moduleVersion: ModuleVersionIdentifier? = null,
    ): FakeNode {
        val id =
            object : ProjectComponentIdentifier {
                override fun getBuild(): BuildIdentifier = build

                override fun getProjectPath(): String = path

                override fun getBuildTreePath(): String = path

                override fun getProjectName(): String = path.substringAfterLast(':').ifEmpty { "root" }

                override fun getDisplayName(): String = "project '$path'"
            }
        return FakeNode(id, moduleVersion)
    }

    private fun fakeExternalNode(
        group: String,
        name: String,
        version: String,
    ): FakeNode {
        val moduleVersion = fakeModuleVersion(group, name, version)
        val id =
            object : ComponentIdentifier {
                override fun getDisplayName(): String = "$group:$name:$version"
            }
        return FakeNode(id, moduleVersion)
    }
}
