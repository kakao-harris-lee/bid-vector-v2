package bidvector.workflow

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * 운영자 결정 2026-09-11 (a) — `workflow` 패키지 전체의 게이트 등재 완전성. `adapters`의
 * `MlGateRegistrationTest`·`EventGateRegistrationTest`는 패키지 하나(`listFiles`, 재귀
 * 아님)만 훑고 단방향(누락만)만 잰다. `workflow`는 하위 패키지 다섯
 * (`evaluation`·`event`·`notification`·`prediction`·`strategy`)에 걸쳐 있어 재귀
 * (`walkTopDown`)가 필요하고, 이 test는 **양방향**(등재 누락 + 등재됐지만 소스에 없는
 * 잉여)을 함께 잰다 — 잉여 등재는 지운 test class 를 정책 파일이 계속 산 것처럼 세는
 * 장부층 불일치라 `gateExecutionGate`가 그 이름을 찾지 못해도 `check`가 조용히 통과할
 * 수 있다(그 이름의 test 가 처음부터 없었으므로 "0건 실행"과 "등재 자체가 오기"를
 * 구별 못한다).
 */
class WorkflowGateRegistrationTest {
    @Test
    fun `workflow 패키지의 모든 Test class 는 gate-tests properties 에 등재된다 — 양방향`() {
        val discovered = discoverWorkflowTestClasses()
        val registered = registeredWorkflowTests()

        (discovered - registered) shouldBe emptySet()
        (registered - discovered) shouldBe emptySet()
    }

    /** 양성 대조 — 등재에서 하나가 빠지면(누락) 차집합이 그 차이를 그대로 낸다. */
    @Test
    fun `등재 집합에 없는 이름이 discovered 에 있으면 차집합이 비지 않는다 — 양성 대조(누락)`() {
        val discovered = setOf("bidvector.workflow.evaluation.FakeUnregisteredTest")
        val registered = setOf("bidvector.workflow.evaluation.SomeOtherTest")

        (discovered - registered) shouldBe discovered
    }

    /** 양성 대조 — 소스에 없는 이름이 등재에만 있으면(잉여) 반대 차집합이 그 차이를 낸다. */
    @Test
    fun `discovered 에 없는 이름이 등재 집합에 있으면 반대 차집합이 비지 않는다 — 양성 대조(잉여)`() {
        val discovered = setOf("bidvector.workflow.evaluation.RealTest")
        val registered = setOf("bidvector.workflow.evaluation.RealTest", "bidvector.workflow.evaluation.DeletedTest")

        (registered - discovered) shouldBe setOf("bidvector.workflow.evaluation.DeletedTest")
    }
}

private val CLASSPATH_ROOT = File("src/test/kotlin")

private fun discoverWorkflowTestClasses(): Set<String> {
    val sourceRoot = File(CLASSPATH_ROOT, "bidvector/workflow")
    check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

    return sourceRoot
        .walkTopDown()
        .filter { file -> file.isFile && file.name.endsWith("Test.kt") }
        .map(::fqcnOf)
        .toSet()
}

/** 파일의 패키지 경로(디렉터리 구조)에서 FQCN을 만든다 — 파일 안 `package` 선언을 다시 읽지 않는다(경로가 이미 계약이다). */
private fun fqcnOf(file: File): String {
    val packagePath =
        file.parentFile
            .relativeTo(CLASSPATH_ROOT)
            .path
            .replace(File.separatorChar, '.')
    return "$packagePath.${file.nameWithoutExtension}"
}

private fun registeredWorkflowTests(): Set<String> {
    val propertiesFile = File("../config/quality/gate-tests.properties")
    check(propertiesFile.isFile) { "정책 파일을 찾지 못했다: ${propertiesFile.absolutePath}" }

    val policy =
        propertiesFile.reader(Charsets.UTF_8).use { reader ->
            Properties().apply { load(reader) }
        }
    val required = policy.getProperty("gate.tests.workflow").orEmpty()

    return required
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { fqcn -> fqcn.startsWith("bidvector.workflow.") }
        .toSet()
}
