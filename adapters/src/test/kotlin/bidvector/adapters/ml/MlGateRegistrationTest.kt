package bidvector.adapters.ml

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * verifier r3 H-2(medium) — `gateExecutionGate`(build-logic)는 **등재된** test class 가
 * 실제로 도는지 잰다(반대 방향은 못 잰다). 이 test 는 반대축이다 — `adapters/ml` 패키지의
 * **모든** `*Test` class 가 `gate.tests.adapters`(`config/quality/gate-tests.properties`)에
 * 등재돼 있는지, 소스 디렉터리 스캔과 properties 파싱을 직접 대조해 잰다. 등재가 빠지면
 * 그 class 가 아무리 회귀를 잘 막아도 게이트 밖이라 삭제·비활성화돼도 `check`가 초록이다
 * (r3 실측: 신설 class 의 `@Test` 여섯을 전부 `@Disabled` 로 만들어도 `gateExecutionGate`
 * 가 초록이었다). 자기 자신(`MlGateRegistrationTest`)도 대상이라 이 test 스스로 등재
 * 여부를 보증한다 — evidence 자기검사가 아니라 산출물 게이트의 완전성 test 다.
 */
class MlGateRegistrationTest {
    @Test
    fun `adapters ml 패키지의 모든 Test class 는 gate-tests properties 에 등재된다`() {
        val discovered = discoverAdaptersMlTestClasses()
        val registered = registeredAdaptersMlTests()

        (discovered - registered) shouldBe emptySet()
    }

    /** 양성 대조 — 등재에서 하나가 빠지면 차집합 술어가 그 차이를 그대로 낸다. */
    @Test
    fun `등재 집합에 없는 이름이 discovered 에 있으면 차집합이 비지 않는다 — 양성 대조`() {
        val discovered = setOf("bidvector.adapters.ml.FakeUnregisteredTest")
        val registered = setOf("bidvector.adapters.ml.SomeOtherTest")

        (discovered - registered) shouldBe discovered
    }
}

private fun discoverAdaptersMlTestClasses(): Set<String> {
    val sourceRoot = File("src/test/kotlin/bidvector/adapters/ml")
    check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

    return sourceRoot
        .listFiles { file -> file.isFile && file.name.endsWith("Test.kt") }
        .orEmpty()
        .map { file -> "bidvector.adapters.ml.${file.nameWithoutExtension}" }
        .toSet()
}

private fun registeredAdaptersMlTests(): Set<String> {
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
        .filter { fqcn -> fqcn.startsWith("bidvector.adapters.ml.") }
        .toSet()
}
