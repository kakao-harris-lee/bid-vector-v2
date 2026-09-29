package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * D-6A2b-38·46·48·50 — `app` 모듈의 게이트 등재 완전성. `workflow`·`adapters` 에는
 * 있고 `app` 에만 없던 자리다. 그 부재에서 게이트 test 넷이 `gateExecutionGate` 밖에 났다.
 *
 * **발견은 컴파일된 test 클래스 전수다**(D-6A2b-50). 파일 이름(`*Test.kt`)으로 찾으면
 * 그 술어를 세 형태가 지나간다(실측): 파일 이름과 다른 클래스 이름 · `*Tests` 접미 · 발견 루트
 * 밖 경로. **JUnit 이 실제로 실행하는 것**은 파일이 아니라 **`@Test` 를 가진 클래스**이므로
 * 그것을 모집단으로 둔다 — 이름 규약에 기대지 않는다.
 *
 * 양방향이다. 누락은 「게이트가 안 돌아도 조용하다」이고, 잉여는 장부와 소스가 어긋난 채 남는
 * 것이다(`GateExecutionGateTask` 의 「실행되지 않았다」는 이름이 처음부터 없던 경우와 구별되지
 * 않는다).
 */
class AppGateRegistrationTest {
    @Test
    fun `app 의 모든 test 클래스는 gate-tests properties 에 등재된다 — 양방향`() {
        val discovered = discoverAppTestClasses()
        val registered = registeredAppTests()

        (discovered - registered) shouldBe emptySet()
        (registered - discovered) shouldBe emptySet()
    }

    /**
     * 양성 대조(누락) — 고정 문자열 집합 산술이 아니라 **읽는 함수의 결과**에서 한 이름을 빼서
     * 잰다. 리터럴 둘의 차집합으로 재면 술어가 무엇을 읽든 늘 참이 된다.
     */
    @Test
    fun `등재 집합에서 한 이름을 빼면 그 이름이 차집합에 나온다 — 양성 대조(누락)`() {
        val discovered = discoverAppTestClasses()
        val dropped = discovered.first()

        (discovered - (registeredAppTests() - dropped)) shouldBe setOf(dropped)
    }

    /** 양성 대조(잉여) — 소스에 없는 이름을 등재에 더하면 반대 차집합이 그것을 낸다. */
    @Test
    fun `등재 집합에 없는 이름을 더하면 반대 차집합에 나온다 — 양성 대조(잉여)`() {
        val ghost = "bidvector.app.GhostTest"

        ((registeredAppTests() + ghost) - discoverAppTestClasses()) shouldBe setOf(ghost)
    }

    /** 모집단이 비어 있지 않다 — 컴파일 출력 경로가 바뀌면 조용히 공집합이 되지 않는다. */
    @Test
    fun `발견 모집단이 비어 있지 않다`() {
        (discoverAppTestClasses().size >= MINIMUM_DISCOVERED) shouldBe true
    }
}

/** 오늘 실측한 test 클래스 수보다 크게 줄면 모집단을 잃은 것이다. */
private const val MINIMUM_DISCOVERED = 30

private const val TEST_ANNOTATION = "org.junit.jupiter.api.Test"

private val TEST_CLASSES_ROOT = File("build/classes/kotlin/test")

private fun discoverAppTestClasses(): Set<String> {
    check(TEST_CLASSES_ROOT.isDirectory) { "컴파일된 test 클래스를 찾지 못했다: ${TEST_CLASSES_ROOT.absolutePath}" }
    val compiled: JavaClasses = ClassFileImporter().importPath(TEST_CLASSES_ROOT.toPath())

    return compiled
        .filter(::hasTestMethod)
        .map(JavaClass::getName)
        .toSet()
}

/** JUnit 이 실행하는 클래스 — 이름이 아니라 `@Test` 가 정한다. */
private fun hasTestMethod(type: JavaClass): Boolean =
    type.methods.any { method -> method.annotations.any { it.rawType.name == TEST_ANNOTATION } }

private fun registeredAppTests(): Set<String> {
    val propertiesFile = File(System.getProperty(GATE_TESTS_PROPERTY) ?: error("게이트 등재 파일 경로가 주어지지 않았다"))
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

/** 읽는 좌표와 Gradle 이 선언한 입력 좌표를 하나로 묶는다. */
private const val GATE_TESTS_PROPERTY = "bidvector.gate.tests"
