package bidvector.app.architecture

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * D-6A2b-38(L-r3-6) — `app` 모듈의 게이트 등재 완전성. `workflow`·`adapters` 에는 있고
 * `app` 에만 없던 자리다. 그 부재에서 H-r2-1(게이트 test 넷이 `gateExecutionGate` 밖)이
 * 났고, 그때는 아홉을 **손으로** 넣어 닫았을 뿐이라 다음 게이트가 또 빠질 수 있었다.
 *
 * 대상은 **게이트 자리**로 좁힌다 — `architecture` 패키지 전체와, 어디에 있든
 * 이름이 게이트라고 말하는 `*GateTest`. `app` 의 test 전수는 과하다(대부분은 게이트가
 * 아니라 기능 test 이고, `gateExecutionGate` 가 지키려는 것은 게이트 쪽이다).
 *
 * 단방향(누락만)이다 — 잉여 등재는 `workflow` 의 양방향 test 가 이미 잡는 형태이고,
 * 여기서는 등재 목록에 `app` 의 비게이트 test 가 함께 오르는 것을 막지 않는다.
 */
class AppGateRegistrationTest {
    @Test
    fun `app 의 게이트 test 는 모두 gate-tests properties 에 등재된다 — 누락 단방향`() {
        (discoverAppGateTests() - registeredAppTests()) shouldBe emptySet()
    }

    /** 양성 대조 — 등재에서 하나가 빠지면 차집합이 그 이름을 그대로 낸다. */
    @Test
    fun `등재되지 않은 게이트 이름이 있으면 차집합이 비지 않는다 — 양성 대조`() {
        val discovered = setOf("bidvector.app.architecture.FakeUnregisteredGateTest")

        (discovered - setOf("bidvector.app.architecture.OtherGateTest")) shouldBe discovered
    }

    /** 대상 술어가 공허하지 않다 — 오늘 실제로 게이트 자리를 찾아낸다. */
    @Test
    fun `대상 집합이 비어 있지 않다`() {
        (discoverAppGateTests().size >= MINIMUM_DISCOVERED) shouldBe true
    }
}

/** 오늘 실측한 게이트 자리 수보다 작아지면 술어가 대상을 잃은 것이다. */
private const val MINIMUM_DISCOVERED = 5

private val CLASSPATH_ROOT = File("src/test/kotlin")

private fun discoverAppGateTests(): Set<String> {
    val sourceRoot = File(CLASSPATH_ROOT, "bidvector/app")
    check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

    return sourceRoot
        .walkTopDown()
        .filter { file -> file.isFile && file.name.endsWith("Test.kt") && isGateSite(file) }
        .map(::fqcnOf)
        .toSet()
}

/** 게이트 자리 — `architecture` 패키지 안이거나, 이름이 `GateTest` 로 끝난다. */
private fun isGateSite(file: File): Boolean =
    file.parentFile.name == "architecture" || file.name.endsWith("GateTest.kt")

/** 경로가 곧 패키지다 — 파일 안 `package` 선언을 다시 읽지 않는다. */
private fun fqcnOf(file: File): String {
    val packagePath =
        file.parentFile
            .relativeTo(CLASSPATH_ROOT)
            .path
            .replace(File.separatorChar, '.')
    return "$packagePath.${file.nameWithoutExtension}"
}

private fun registeredAppTests(): Set<String> {
    val propertiesFile = File("../config/quality/gate-tests.properties")
    check(propertiesFile.isFile) { "정책 파일을 찾지 못했다: ${propertiesFile.absolutePath}" }

    val policy =
        propertiesFile.reader(Charsets.UTF_8).use { reader ->
            Properties().apply { load(reader) }
        }

    return policy
        .getProperty("gate.tests.app")
        .orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
}
