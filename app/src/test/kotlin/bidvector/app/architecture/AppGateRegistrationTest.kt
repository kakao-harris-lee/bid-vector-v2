package bidvector.app.architecture

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * D-6A2b-38(L-r3-6)·46(N-r4-6) — `app` 모듈의 게이트 등재 완전성. `workflow`·`adapters` 에는
 * 있고 `app` 에만 없던 자리다. 그 부재에서 H-r2-1(게이트 test 넷이 `gateExecutionGate` 밖)이
 * 났고, 그때는 아홉을 **손으로** 넣어 닫았을 뿐이라 다음 게이트가 또 빠질 수 있었다.
 *
 * **대상은 `bidvector.app` test 전수다.** 앞 판은 대상을 「`architecture` 패키지 ∪ 이름이
 * `GateTest` 로 끝나는 파일」로 **열거**했고, 그 술어가 오늘 찾는 것은 일곱뿐이며 둘째 갈래는
 * 하나도 찾지 못하는 사문이었다(code-review r4 N-r4-6 실측). H-r2-1 의 그 넷이 난 자리
 * (`app/http`·`app/management`)를 새 meta-gate 가 보지 못했다 — 같은 병을 다시 앓는 형태다.
 * `WorkflowGateRegistrationTest` 가 `workflow` 패키지에 하는 것과 같은 **전수·양방향**으로 둔다.
 *
 * 양방향인 이유: 누락은 「게이트가 안 돌아도 조용하다」이고, 잉여(소스에 없는 이름의 등재)는
 * 장부와 소스가 어긋난 채로 남는 것이다. `GateExecutionGateTask` 가 잉여를 「실행되지 않았다」로
 * 잡기는 하지만, 그 판정은 **이름이 처음부터 없었던 경우**와 구별되지 않는다.
 */
class AppGateRegistrationTest {
    @Test
    fun `app 패키지의 모든 Test class 는 gate-tests properties 에 등재된다 — 양방향`() {
        val discovered = discoverAppTestClasses()
        val registered = registeredAppTests()

        (discovered - registered) shouldBe emptySet()
        (registered - discovered) shouldBe emptySet()
    }

    /**
     * 양성 대조(누락) — 고정 문자열 집합 산술이 아니라 **읽는 함수의 결과**에서 한 이름을 빼서
     * 잰다. 앞 판은 리터럴 둘의 차집합이라 술어가 무엇을 읽든 늘 참이었다(N-r4-6).
     */
    @Test
    fun `등재 집합에서 한 이름을 빼면 그 이름이 차집합에 나온다 — 양성 대조(누락)`() {
        val discovered = discoverAppTestClasses()
        val dropped = discovered.first()

        (discovered - (registeredAppTests() - dropped)) shouldBe setOf(dropped)
    }

    /**
     * 양성 대조(잉여) — 같은 방식으로, 소스에 없는 이름을 등재에 더하면 반대 차집합이 그것을 낸다.
     */
    @Test
    fun `등재 집합에 없는 이름을 더하면 반대 차집합에 나온다 — 양성 대조(잉여)`() {
        val ghost = "bidvector.app.GhostTest"

        ((registeredAppTests() + ghost) - discoverAppTestClasses()) shouldBe setOf(ghost)
    }
}

private val CLASSPATH_ROOT = File("src/test/kotlin")

private fun discoverAppTestClasses(): Set<String> {
    val sourceRoot = File(CLASSPATH_ROOT, "bidvector/app")
    check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

    return sourceRoot
        .walkTopDown()
        .filter { file -> file.isFile && file.name.endsWith("Test.kt") }
        .map(::fqcnOf)
        .toSet()
}

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
