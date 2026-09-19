package bidvector.adapters.evaluation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * `MlGateRegistrationTest`·`EventGateRegistrationTest` 관례(D-6F2-10, verifier r1 HIGH-2
 * 수정) — `adapters/evaluation` 패키지의 **모든** `*Test` class 가
 * `gate.tests.adapters`(`config/quality/gate-tests.properties`)에 등재돼 있는지, 소스
 * 디렉터리 스캔과 properties 파싱을 직접 대조해 잰다. 등재가 빠지면 그 class가 아무리
 * 회귀를 잘 막아도 게이트 밖이라 삭제·비활성화돼도 `check`가 초록이다(verifier r1 HIGH-2
 * 실측 — `EvaluationAdapterDependencyTest.kt`를 파일째 지워도 `check`가 BUILD SUCCESSFUL).
 * 자기 자신(`EvaluationGateRegistrationTest`)도 대상이다.
 */
class EvaluationGateRegistrationTest {
    @Test
    fun `adapters evaluation 패키지의 모든 Test class 는 gate-tests properties 에 등재된다`() {
        val discovered = discoverAdaptersEvaluationTestClasses()
        val registered = registeredAdaptersEvaluationTests()

        (discovered - registered) shouldBe emptySet()
    }

    /** 양성 대조 — 등재에서 하나가 빠지면 차집합 술어가 그 차이를 그대로 낸다. */
    @Test
    fun `등재 집합에 없는 이름이 discovered 에 있으면 차집합이 비지 않는다 — 양성 대조`() {
        val discovered = setOf("bidvector.adapters.evaluation.FakeUnregisteredTest")
        val registered = setOf("bidvector.adapters.evaluation.SomeOtherTest")

        (discovered - registered) shouldBe discovered
    }
}

private fun discoverAdaptersEvaluationTestClasses(): Set<String> {
    val sourceRoot = File("src/test/kotlin/bidvector/adapters/evaluation")
    check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

    return sourceRoot
        .listFiles { file -> file.isFile && file.name.endsWith("Test.kt") }
        .orEmpty()
        .map { file -> "bidvector.adapters.evaluation.${file.nameWithoutExtension}" }
        .toSet()
}

private fun registeredAdaptersEvaluationTests(): Set<String> {
    val propertiesFile = File("../config/quality/gate-tests.properties")
    check(propertiesFile.isFile) { "정책 파일을 찾지 못했다: ${propertiesFile.absolutePath}" }

    val policy =
        propertiesFile.reader(Charsets.UTF_8).use { reader ->
            Properties().apply { load(reader) }
        }
    val required = policy.getProperty("gate.tests.adapters").orEmpty()

    return required
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { fqcn -> fqcn.startsWith("bidvector.adapters.evaluation.") }
        .toSet()
}
